package game.agent;
import game.agent.web.*;
import game.agent.llm.*;
import game.agent.persistence.*;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class PokemonFollowTest {
    @TempDir Path directory;
    static Map<String,Object> out(Map<String,Object> view){return Json.asObject(view.get("summoned"));}
    static Map<String,Object> toggle(String id,boolean enabled){return Json.object("captureId",id,"enabled",enabled);}
    static void move(AgentWebServerTest.Browser b,String q,String direction)throws Exception{PokemonSummonTest.send(b,q,"MANUAL",Json.object("action","move","arguments",Json.object("direction",direction)));}
    static int coord(Map<String,Object> p,String name){return Integer.parseInt(String.valueOf(p.get(name)));}
    @Test void followIsOptInMovesOneRealExitOnWorldTurnAndStopsWithoutExtraTurn()throws Exception{
        try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,null)){
            s.start();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(s.getPort());String id=PokemonSummonTest.carried(b),q=PokemonCarryTest.room(b,"quest");PokemonSummonTest.send(b,q,"SUMMON",PokemonSummonTest.summon(id));
            assertEquals(false,out(PokemonSummonTest.snapshot(b,q)).get("following"));
            Map<String,Object> payload=PokemonCarryTest.command(PokemonSummonTest.snapshot(b,q),"FOLLOW",toggle(id,true));assertEquals(200,b.post(q+"/commands",Json.write(payload),b.base,b.csrf).getResponseCode());
            b.post(q+"/commands",payload);Map<String,Object> before=PokemonSummonTest.snapshot(b,q);assertEquals(true,out(before).get("following"));assertEquals("0",Json.asObject(before.get("world")).get("turn"));
            move(b,q,"W");Map<String,Object> v=PokemonSummonTest.snapshot(b,q),p=out(v),world=Json.asObject(v.get("world"));assertEquals("1",world.get("turn"));assertEquals("5",world.get("coins"));
            assertTrue(Math.max(Math.abs(coord(p,"x")-30),Math.abs(coord(p,"y")-10))==1);assertTrue(Math.max(Math.abs(coord(p,"x")-coord(world,"x")),Math.abs(coord(p,"y")-coord(world,"y")))<=1);assertFalse(coord(p,"x")==coord(world,"x")&&coord(p,"y")==coord(world,"y"));
            assertEquals(coord(p,"x"),coord(Json.asObject(PokemonCarryTest.collection(v).get(0)),"x"));
            PokemonSummonTest.send(b,q,"FOLLOW",toggle(id,false));move(b,q,"W");assertEquals(p.get("x"),out(PokemonSummonTest.snapshot(b,q)).get("x"));assertEquals(p.get("y"),out(PokemonSummonTest.snapshot(b,q)).get("y"));assertEquals("2",Json.asObject(PokemonSummonTest.snapshot(b,q).get("world")).get("turn"));
        }
    }

    @Test void fileRestartRestoresActualFollowPositionEvenWhenTrainingIsOpenedFirst()throws Exception{
        int port;String cookie,id;Map<String,Object> actual;
        try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,new FileWorldStore(directory))){s.start();port=s.getPort();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(port);cookie=b.cookie;id=PokemonSummonTest.carried(b);String q=PokemonCarryTest.room(b,"quest");PokemonSummonTest.send(b,q,"SUMMON",PokemonSummonTest.summon(id));PokemonSummonTest.send(b,q,"FOLLOW",toggle(id,true));move(b,q,"W");actual=out(PokemonSummonTest.snapshot(b,q));}
        try(AgentWebServer s=new AgentWebServer(port,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,new FileWorldStore(directory))){s.start();AgentWebServerTest.Browser b=restoredBrowser(port,cookie);String t=PokemonCarryTest.room(b,"training");Map<String,Object> row=Json.asObject(PokemonCarryTest.collection(PokemonSummonTest.snapshot(b,t)).get(0));assertEquals(actual.get("x"),row.get("x"));assertEquals(actual.get("y"),row.get("y"));String q=PokemonCarryTest.room(b,"quest");assertEquals(actual,out(PokemonSummonTest.snapshot(b,q)));assertEquals("1",Json.asObject(PokemonSummonTest.snapshot(b,q).get("world")).get("turn"));move(b,q,"W");assertNotEquals(actual,out(PokemonSummonTest.snapshot(b,q)));PokemonSummonTest.send(b,q,"RECALL",Json.object("captureId",id));assertNull(PokemonSummonTest.snapshot(b,q).get("summoned"));}
    }
    static AgentWebServerTest.Browser restoredBrowser(int port,String cookie)throws Exception{AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(port);b.cookie=cookie;b.csrf=(String)AgentWebServerTest.read(b.connection("/api/quest/session","GET")).get("csrfToken");return b;}
    @Test void unknownCommitRestoresFollowerAndLeaderFromSameAtomicWorldCheckpoint()throws Exception{
        for(boolean committed:Arrays.asList(false,true)){
            PokemonCarryTest.FaultStore store=new PokemonCarryTest.FaultStore();
            try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,store)){
                s.start();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(s.getPort());String id=PokemonSummonTest.carried(b),q=PokemonCarryTest.room(b,"quest");PokemonSummonTest.send(b,q,"SUMMON",PokemonSummonTest.summon(id));PokemonSummonTest.send(b,q,"FOLLOW",toggle(id,true));Map<String,Object> previous=out(PokemonSummonTest.snapshot(b,q));
                store.failPrefix=store.values.keySet().stream().filter(k->!k.contains(":")).findFirst().get();store.afterCommit=committed;
                assertEquals(503,PokemonSummonTest.rejected(b,q,"MANUAL",Json.object("action","move","arguments",Json.object("direction","W"))));assertEquals("STORAGE_ERROR",PokemonSummonTest.snapshot(b,q).get("status"));Map<String,Object> after=out(PokemonSummonTest.snapshot(b,q));assertNotEquals(previous,after);
                store.failPrefix=null;PokemonSummonTest.send(b,q,"RECOVER",Collections.emptyMap());Map<String,Object> restored=PokemonSummonTest.snapshot(b,q);assertEquals(committed?after:previous,out(restored));assertEquals(committed?"1":"0",Json.asObject(restored.get("world")).get("turn"));assertEquals(committed?"28":"29",Json.asObject(restored.get("world")).get("x"));assertEquals(true,out(restored).get("following"));
            }
        }
    }
    @Test void followToggleFailedSaveRevertsToDurableSwitchAndFreezeBlocksFurtherCommands()throws Exception{
        PokemonCarryTest.FaultStore store=new PokemonCarryTest.FaultStore();
        try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,store)){
            s.start();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(s.getPort());String id=PokemonSummonTest.carried(b),q=PokemonCarryTest.room(b,"quest");PokemonSummonTest.send(b,q,"SUMMON",PokemonSummonTest.summon(id));store.failPrefix=store.values.keySet().stream().filter(k->!k.contains(":")).findFirst().get();assertEquals(503,PokemonSummonTest.rejected(b,q,"FOLLOW",toggle(id,true)));assertEquals(503,PokemonSummonTest.rejected(b,q,"FOLLOW",toggle(id,false)));store.failPrefix=null;PokemonSummonTest.send(b,q,"RECOVER",Collections.emptyMap());assertEquals(false,out(PokemonSummonTest.snapshot(b,q)).get("following"));
        }
    }
    @Test void newSummonGenerationIgnoresStaleFollowPositionAfterWorldSaveFails()throws Exception{
        PokemonCarryTest.FaultStore store=new PokemonCarryTest.FaultStore();
        try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,store)){
            s.start();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(s.getPort());String id=PokemonSummonTest.carried(b),q=PokemonCarryTest.room(b,"quest");PokemonSummonTest.send(b,q,"SUMMON",PokemonSummonTest.summon(id));PokemonSummonTest.send(b,q,"FOLLOW",toggle(id,true));move(b,q,"W");Map<String,Object> old=out(PokemonSummonTest.snapshot(b,q));Map<String,Object> oldCheckpoint=Json.asObject(Json.read(Json.write(store.values.entrySet().stream().filter(e->!e.getKey().contains(":")).findFirst().get().getValue())));
            PokemonSummonTest.send(b,q,"RECALL",Json.object("captureId",id));String owner=store.values.keySet().stream().filter(k->!k.contains(":")).findFirst().get();store.save(owner,oldCheckpoint);store.failPrefix=owner;
            assertEquals(503,PokemonSummonTest.rejected(b,q,"SUMMON",Json.object("captureId",id,"direction","S")));store.failPrefix=null;PokemonSummonTest.send(b,q,"RECOVER",Collections.emptyMap());Map<String,Object> current=out(PokemonSummonTest.snapshot(b,q));assertNotEquals(old.get("deploymentId"),current.get("deploymentId"));assertEquals(28,coord(current,"x"));assertEquals(11,coord(current,"y"));assertEquals(false,current.get("following"));
        }
    }
    static BattleRoomTest.Memory legacy(String owner,String id){BattleRoomTest.Memory store=new BattleRoomTest.Memory();store.save("collection:"+owner,Json.object("schemaVersion",1,"kind","POKEMON_COLLECTION","pokemon",Arrays.asList(Json.object("captureId",id,"species","TREECKO","hp",85,"maxHp",100)),"deployment",Json.object("captureId",id,"x",30,"y",10)));return store;}
    @Test void legacyDeploymentWithoutGenerationRemainsStationaryAndCanStartFollowing(){String id=UUID.randomUUID().toString();BattleRoomTest.Memory store=legacy("legacy",id);AgentRoom room=new AgentRoom("legacy",ProviderSelection.fromEnvironment(Collections.emptyMap()),Runnable::run,true,store);assertEquals(false,out(room.snapshot()).get("following"));assertEquals(85,coord(out(room.snapshot()),"hp"));assertEquals(200,room.command(AgentRoomTest.command(room,"FOLLOW",toggle(id,true)),0).status);room=new AgentRoom("legacy",ProviderSelection.fromEnvironment(Collections.emptyMap()),Runnable::run,true,store);assertEquals(true,out(room.snapshot()).get("following"));}
    @Test void invalidMatchingFollowPositionFreezesWithoutDeletingOwnership(){String id=UUID.randomUUID().toString();BattleRoomTest.Memory store=legacy("corrupt",id);AgentRoom room=new AgentRoom("corrupt",ProviderSelection.fromEnvironment(Collections.emptyMap()),Runnable::run,true,store);room.command(AgentRoomTest.command(room,"FOLLOW",toggle(id,true)),0);Map<String,Object> saved=store.load("corrupt");Json.asObject(saved.get("summoned")).put("x",29);Json.asObject(saved.get("summoned")).put("y",10);room=new AgentRoom("corrupt",ProviderSelection.fromEnvironment(Collections.emptyMap()),Runnable::run,true,store);assertEquals("STORAGE_ERROR",room.snapshot().get("status"));assertEquals(503,room.command(AgentRoomTest.command(room,"RECOVER",Collections.emptyMap()),1).status);assertEquals(1,Json.asArray(store.load("collection:corrupt").get("pokemon")).size());}
    @Test void collectionReloadPreservesCurrentFollowPositionAndRecallHasNoGhost()throws Exception{
        PokemonCarryTest.FaultStore store=new PokemonCarryTest.FaultStore();try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,store)){
            s.start();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(s.getPort());String id=PokemonSummonTest.carried(b),q=PokemonCarryTest.room(b,"quest");PokemonSummonTest.send(b,q,"SUMMON",PokemonSummonTest.summon(id));PokemonSummonTest.send(b,q,"FOLLOW",toggle(id,true));move(b,q,"W");Map<String,Object> before=out(PokemonSummonTest.snapshot(b,q));String t=PokemonCarryTest.room(b,"training");store.failPrefix="battle:";assertEquals(503,PokemonSummonTest.rejected(b,t,"MANUAL",Json.object("action","move","arguments",Json.object("direction","E"))));store.failPrefix=null;PokemonSummonTest.send(b,t,"RECOVER",Collections.emptyMap());PokemonCarryTest.room(b,"quest");assertEquals(before,out(PokemonSummonTest.snapshot(b,q)));PokemonSummonTest.send(b,q,"RECALL",Json.object("captureId",id));move(b,q,"E");assertNull(PokemonSummonTest.snapshot(b,q).get("summoned"));
        }
    }
    @Test void followCommandChecksOwnershipTypesControlLeaseAndAiPhase()throws Exception{
        try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,null)){
            s.start();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(s.getPort());String id=PokemonSummonTest.carried(b),q=PokemonCarryTest.room(b,"quest");assertEquals(409,PokemonSummonTest.rejected(b,q,"FOLLOW",toggle(id,true)));PokemonSummonTest.send(b,q,"SUMMON",PokemonSummonTest.summon(id));assertEquals(409,PokemonSummonTest.rejected(b,q,"FOLLOW",toggle(UUID.randomUUID().toString(),true)));assertEquals(400,PokemonSummonTest.rejected(b,q,"FOLLOW",Json.object("captureId",id,"enabled","true")));PokemonCarryTest.room(b,"training");assertEquals(409,PokemonSummonTest.rejected(b,q,"FOLLOW",toggle(id,true)));
            AgentWebServerTest.Browser other=new AgentWebServerTest.Browser(s.getPort());assertEquals(409,PokemonSummonTest.rejected(other,PokemonCarryTest.room(other,"quest"),"FOLLOW",toggle(id,true)));
        }
        String id=UUID.randomUUID().toString();BattleRoomTest.Memory store=legacy("ai",id);Queue<Runnable> jobs=new ArrayDeque<>();AgentRoom room=new AgentRoom("ai",AgentRoomTest.provider((m,k,b,t)->new JsonTransport.Response(200,AgentRoomTest.parsed(b))),jobs::add,true,store);room.command(AgentRoomTest.command(room,"PARSE",Json.object("text","完成任务")),0);assertEquals(409,room.command(AgentRoomTest.command(room,"FOLLOW",toggle(id,true)),1).status);jobs.remove().run();room.tick(2);assertEquals(409,room.command(AgentRoomTest.command(room,"FOLLOW",toggle(id,true)),2).status);room.command(AgentRoomTest.command(room,"CONFIRM",Collections.emptyMap()),2);assertEquals(409,room.command(AgentRoomTest.command(room,"FOLLOW",toggle(id,true)),3).status);
    }
    @Test void sceneBlockedRouteAndAreaRestrictionCannotTeleportFollower(){
        game.agent.demo.GeminiQuestScenario initial=new game.agent.demo.GeminiQuestScenario("blocked");Map<String,Object> state=initial.exportState();Json.asObject(state.get("map")).put("terrain",Arrays.asList("...#.....","...#.....","...#....."));game.agent.demo.GeminiQuestScenario scene=game.agent.demo.GeminiQuestScenario.restore("blocked",state);scene.synchronizeSummoned(new game.actors.pokemon.Treecko(),Json.object("x",8,"y",2));scene.setFollowing(true);Map<String,Object> before=scene.summonedState();scene.advanceTurn();assertEquals(before,scene.summonedState());assertEquals(1,scene.getSession().getTurn());
        scene=new game.agent.demo.GeminiQuestScenario("area");scene.synchronizeSummoned(new game.actors.pokemon.Treecko(),Json.object("x",8,"y",2));scene.setFollowing(true);scene.start(TaskIntent.of(scene.getSession().getQuestId(),EnumSet.of(TaskIntent.Constraint.AREA_RESTRICTED),"ORCHARD_AREA",null),context->{throw new IllegalStateException("not called");},Runnable::run);before=scene.summonedState();scene.advanceTurn();assertEquals(before,scene.summonedState());
    }
    @Test void aiDecisionWaitDoesNotMoveFollowerAndExecutedActionUsesSameWorldTurn(){
        String id=UUID.randomUUID().toString();BattleRoomTest.Memory store=legacy("agent-follow",id);Queue<Runnable> jobs=new ArrayDeque<>();
        AgentRoom room=new AgentRoom("agent-follow",AgentRoomTest.provider((m,k,b,t)->new JsonTransport.Response(200,b.contains("json_schema")?AgentRoomTest.parsed(b):OpenAiGatewayTest.function("move_to","{\"locationId\":\"orchard\"}"))),jobs::add,true,store);
        assertEquals(200,room.command(AgentRoomTest.command(room,"FOLLOW",toggle(id,true)),0).status);
        room.command(AgentRoomTest.command(room,"PARSE",Json.object("text","完成任务")),0);jobs.remove().run();room.tick(1);room.command(AgentRoomTest.command(room,"CONFIRM",Collections.emptyMap()),1);
        Map<String,Object> before=out(room.snapshot());room.tick(2);room.tick(3);assertEquals(before,out(room.snapshot()));assertEquals("0",Json.asObject(room.snapshot().get("world")).get("turn"));
        jobs.remove().run();room.tick(4);Map<String,Object> v=room.snapshot(),after=out(v),leader=Json.asObject(v.get("world"));assertEquals("1",leader.get("turn"));assertTrue(Math.max(Math.abs(coord(after,"x")-coord(before,"x")),Math.abs(coord(after,"y")-coord(before,"y")))<=1);assertTrue(Math.max(Math.abs(coord(after,"x")-coord(leader,"x")),Math.abs(coord(after,"y")-coord(leader,"y")))<=1);assertEquals(85,coord(after,"hp"));
        room.command(AgentRoomTest.command(room,"PAUSE",Collections.emptyMap()),5);room.tick(6);assertEquals(after,out(room.snapshot()));
    }
    @Test void followerDetoursAroundActorsAndWallsUsingOneLegalExitPerTurn(){
        game.agent.demo.GeminiQuestScenario initial=new game.agent.demo.GeminiQuestScenario("detour");Map<String,Object> state=initial.exportState();Json.asObject(state.get("map")).put("terrain",Arrays.asList(".........","...#.....","...#....."));Json.asObject(state.get("treecko")).put("x",4);Json.asObject(state.get("treecko")).put("y",2);
        game.agent.demo.GeminiQuestScenario scene=game.agent.demo.GeminiQuestScenario.restore("detour",state);scene.synchronizeSummoned(new game.actors.pokemon.Treecko(),Json.object("x",8,"y",2));scene.setFollowing(true);boolean detoured=false;
        for(int turn=1;turn<=15;turn++){
            Map<String,Object> before=scene.summonedState();scene.advanceTurn();Map<String,Object> after=scene.summonedState();int x=coord(after,"x"),y=coord(after,"y");
            assertTrue(Math.max(Math.abs(x-coord(before,"x")),Math.abs(y-coord(before,"y")))<=1);assertFalse(x==0&&y==1);assertFalse(x==4&&(y==1||y==2));assertFalse(x==8&&y==1);assertFalse(x==3&&y>0);
            if(x==3&&y==0)detoured=true;if(Math.max(x,Math.abs(y-1))<=1)break;
        }
        Map<String,Object> finalPoint=scene.summonedState();assertTrue(detoured);assertTrue(Math.max(coord(finalPoint,"x"),Math.abs(coord(finalPoint,"y")-1))<=1);
    }
    @Test void realPostgresRestoresFollowPositionAndToggle()throws Exception{
        String jdbc=System.getProperty("pokemon.test.jdbcUrl");org.junit.jupiter.api.Assumptions.assumeTrue(jdbc!=null,"PostgreSQL opt-in");String user=System.getProperty("pokemon.test.jdbcUser","postgres"),password=System.getProperty("pokemon.test.jdbcPassword","");int port;String cookie;Map<String,Object> actual;
        try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,new JdbcWorldStore(()->java.sql.DriverManager.getConnection(jdbc,user,password)))){s.start();port=s.getPort();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(port);cookie=b.cookie;String id=PokemonSummonTest.carried(b),q=PokemonCarryTest.room(b,"quest");PokemonSummonTest.send(b,q,"SUMMON",PokemonSummonTest.summon(id));PokemonSummonTest.send(b,q,"FOLLOW",toggle(id,true));move(b,q,"W");actual=out(PokemonSummonTest.snapshot(b,q));}
        try(AgentWebServer s=new AgentWebServer(port,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,new JdbcWorldStore(()->java.sql.DriverManager.getConnection(jdbc,user,password)))){s.start();AgentWebServerTest.Browser b=restoredBrowser(port,cookie);String q=PokemonCarryTest.room(b,"quest");assertEquals(actual,out(PokemonSummonTest.snapshot(b,q)));assertEquals("1",Json.asObject(PokemonSummonTest.snapshot(b,q).get("world")).get("turn"));}
    }
}

