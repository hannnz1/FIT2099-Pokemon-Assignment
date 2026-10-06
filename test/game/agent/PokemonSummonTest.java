package game.agent;
import game.agent.web.*;
import game.agent.llm.*;
import game.agent.persistence.*;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class PokemonSummonTest {
    @TempDir Path directory;
    static String carried(AgentWebServerTest.Browser b)throws Exception{String t=PokemonCarryTest.room(b,"training");PokemonCarryTest.capture(b,t);PokemonCarryTest.send(b,t,"TRANSFER",Json.object("targetId","wild-treecko"));return (String)Json.asObject(PokemonCarryTest.collection(PokemonCarryTest.view(b,t)).get(0)).get("captureId");}
    static Map<String,Object> snapshot(AgentWebServerTest.Browser b,String q)throws Exception{return PokemonCarryTest.view(b,q);}
    static void send(AgentWebServerTest.Browser b,String q,String kind,Map<String,Object> params)throws Exception{PokemonCarryTest.send(b,q,kind,params);}
    static Map<String,Object> summon(String id){return Json.object("captureId",id,"direction","E");}
    static int rejected(AgentWebServerTest.Browser b,String q,String kind,Map<String,Object> params)throws Exception{return b.post(q+"/commands",Json.write(PokemonCarryTest.command(snapshot(b,q),kind,params)),b.base,b.csrf).getResponseCode();}
    @Test void summonedActorBlocksRealMovementAndRecallReturnsSamePokemonWithoutWorldTick()throws Exception{
        try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,new FileWorldStore(directory))){
            s.start();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(s.getPort());String id=carried(b),q=PokemonCarryTest.room(b,"quest");Map<String,Object> payload=PokemonCarryTest.command(snapshot(b,q),"SUMMON",summon(id));b.post(q+"/commands",payload);b.post(q+"/commands",payload);
            Map<String,Object> v=snapshot(b,q),deployed=Json.asObject(v.get("summoned"));assertEquals(id,deployed.get("captureId"));assertEquals("30",String.valueOf(deployed.get("x")));assertEquals("10",String.valueOf(deployed.get("y")));assertEquals("100",String.valueOf(deployed.get("hp")));assertEquals("DEPLOYED",Json.asObject(PokemonCarryTest.collection(v).get(0)).get("state"));
            assertEquals(409,rejected(b,q,"MANUAL",Json.object("action","move","arguments",Json.object("direction","E"))));assertEquals("0",Json.asObject(snapshot(b,q).get("world")).get("turn"));
            send(b,q,"RECALL",Json.object("captureId",id));assertNull(snapshot(b,q).get("summoned"));assertEquals("IN_BALL",Json.asObject(PokemonCarryTest.collection(snapshot(b,q)).get(0)).get("state"));
            send(b,q,"MANUAL",Json.object("action","move","arguments",Json.object("direction","E")));assertEquals("30",Json.asObject(snapshot(b,q).get("world")).get("x"));assertEquals("1",Json.asObject(snapshot(b,q).get("world")).get("turn"));
        }
    }
    @Test void deploymentSurvivesFileRestartAndQuestResetAutomaticallyRecalls()throws Exception{
        int port;String cookie,id;
        try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,new FileWorldStore(directory))){s.start();port=s.getPort();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(port);cookie=b.cookie;id=carried(b);String q=PokemonCarryTest.room(b,"quest");send(b,q,"SUMMON",summon(id));}
        try(AgentWebServer s=new AgentWebServer(port,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,new FileWorldStore(directory))){
            s.start();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(port);b.cookie=cookie;b.csrf=(String)AgentWebServerTest.read(b.connection("/api/quest/session","GET")).get("csrfToken");String q=PokemonCarryTest.room(b,"quest");assertEquals(id,Json.asObject(snapshot(b,q).get("summoned")).get("captureId"));
            send(b,q,"RESET",Collections.emptyMap());assertNull(snapshot(b,q).get("summoned"));assertEquals(1,PokemonCarryTest.collection(snapshot(b,q)).size());assertEquals("IN_BALL",Json.asObject(PokemonCarryTest.collection(snapshot(b,q)).get(0)).get("state"));
        }
    }
    @Test void unknownPokemonWrongDirectionAlreadySummonedAndUnselectedTabAreRejected()throws Exception{
        try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,null)){
            s.start();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(s.getPort());String id=carried(b),q=PokemonCarryTest.room(b,"quest");
            assertEquals(409,rejected(b,q,"SUMMON",summon(UUID.randomUUID().toString())));assertEquals(400,rejected(b,q,"SUMMON",Json.object("captureId",id,"direction","UP")));send(b,q,"SUMMON",summon(id));assertEquals(409,rejected(b,q,"SUMMON",summon(id)));
            PokemonCarryTest.room(b,"training");assertEquals(409,rejected(b,q,"RECALL",Json.object("captureId",id)));assertNotNull(snapshot(b,q).get("summoned"));
            AgentWebServerTest.Browser other=new AgentWebServerTest.Browser(s.getPort());String oq=PokemonCarryTest.room(other,"quest");assertEquals(409,rejected(other,oq,"SUMMON",summon(id)));assertNull(snapshot(other,oq).get("summoned"));
        }
    }
    @Test void occupiedNpcTileRejectsSummonWithoutRemovingBall()throws Exception{
        try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,null)){
            s.start();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(s.getPort());String id=carried(b),q=PokemonCarryTest.room(b,"quest");send(b,q,"MANUAL",Json.object("action","move","arguments",Json.object("direction","N")));
            assertEquals(409,rejected(b,q,"SUMMON",summon(id)));assertNull(snapshot(b,q).get("summoned"));assertEquals("IN_BALL",Json.asObject(PokemonCarryTest.collection(snapshot(b,q)).get(0)).get("state"));send(b,q,"SUMMON",Json.object("captureId",id,"direction","W"));assertNotNull(snapshot(b,q).get("summoned"));
        }
    }
    @Test void collectionFailureAndUnknownSummonOrRecallCommitRecoverActualSinglePlacement()throws Exception{
        for(String action:Arrays.asList("SUMMON","RECALL"))for(boolean committed:Arrays.asList(false,true)){
            PokemonCarryTest.FaultStore store=new PokemonCarryTest.FaultStore();
            try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,store)){
                s.start();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(s.getPort());String id=carried(b),q=PokemonCarryTest.room(b,"quest");if(action.equals("RECALL"))send(b,q,"SUMMON",summon(id));
                store.failPrefix="collection:";store.afterCommit=committed;assertEquals(503,rejected(b,q,action,action.equals("SUMMON")?summon(id):Json.object("captureId",id)));assertEquals("STORAGE_ERROR",snapshot(b,q).get("status"));assertEquals(503,rejected(b,q,"MANUAL",Json.object("action","move","arguments",Json.object("direction","N"))));
                store.failPrefix=null;send(b,q,"RECOVER",Collections.emptyMap());boolean out=action.equals("SUMMON")?committed:!committed;assertEquals(out,snapshot(b,q).get("summoned")!=null);assertEquals(1,PokemonCarryTest.collection(snapshot(b,q)).size());
                if(out){assertEquals(409,rejected(b,q,"MANUAL",Json.object("action","move","arguments",Json.object("direction","E"))));send(b,q,"RECALL",Json.object("captureId",id));}
                send(b,q,"MANUAL",Json.object("action","move","arguments",Json.object("direction","E")));assertEquals("30",Json.asObject(snapshot(b,q).get("world")).get("x"));
            }
        }
    }
    @Test void worldSaveFailureKeepsDeploymentAndRecoveryCannotReportSuccessUntilWriteWorks()throws Exception{
        PokemonCarryTest.FaultStore store=new PokemonCarryTest.FaultStore();
        try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,store)){
            s.start();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(s.getPort());String id=carried(b),q=PokemonCarryTest.room(b,"quest");store.failPrefix=store.values.keySet().stream().filter(k->!k.contains(":")).findFirst().get();
            assertEquals(503,rejected(b,q,"SUMMON",summon(id)));assertEquals("STORAGE_ERROR",snapshot(b,q).get("status"));assertEquals(503,rejected(b,q,"RECOVER",Collections.emptyMap()));assertEquals("STORAGE_ERROR",snapshot(b,q).get("status"));
            store.failPrefix=null;send(b,q,"RECOVER",Collections.emptyMap());assertNotNull(snapshot(b,q).get("summoned"));assertEquals(409,rejected(b,q,"MANUAL",Json.object("action","move","arguments",Json.object("direction","E"))));
            store.failPrefix=store.values.keySet().stream().filter(k->!k.contains(":")).findFirst().get();assertEquals(503,rejected(b,q,"RESET",Collections.emptyMap()));store.failPrefix=null;send(b,q,"RECOVER",Collections.emptyMap());assertNull(snapshot(b,q).get("summoned"));assertEquals("IN_BALL",Json.asObject(PokemonCarryTest.collection(snapshot(b,q)).get(0)).get("state"));
        }
    }
    @Test void trainingRecoveryCannotLeaveGhostActorAfterQuestRecall()throws Exception{
        PokemonCarryTest.FaultStore store=new PokemonCarryTest.FaultStore();
        try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,store)){
            s.start();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(s.getPort());String id=carried(b),q=PokemonCarryTest.room(b,"quest");send(b,q,"SUMMON",summon(id));String t=PokemonCarryTest.room(b,"training");store.failPrefix="battle:";
            assertEquals(503,rejected(b,t,"MANUAL",Json.object("action","move","arguments",Json.object("direction","E"))));store.failPrefix=null;send(b,t,"RECOVER",Collections.emptyMap());PokemonCarryTest.room(b,"quest");
            assertNotNull(snapshot(b,q).get("summoned"));send(b,q,"RECALL",Json.object("captureId",id));send(b,q,"MANUAL",Json.object("action","move","arguments",Json.object("direction","E")));assertEquals("30",Json.asObject(snapshot(b,q).get("world")).get("x"));assertEquals(1,PokemonCarryTest.collection(snapshot(b,q)).size());
        }
    }
    @Test void legacyInjuredCollectionSummonsWithActualHpAndOldFieldsRemainCompatible(){
        BattleRoomTest.Memory store=new BattleRoomTest.Memory();String id=UUID.randomUUID().toString();store.save("collection:injured",Json.object("schemaVersion",1,"kind","POKEMON_COLLECTION","pokemon",Arrays.asList(Json.object("captureId",id,"species","TREECKO","hp",85,"maxHp",100))));
        AgentRoom room=new AgentRoom("injured",ProviderSelection.fromEnvironment(Collections.emptyMap()),Runnable::run,true,store);assertEquals(200,room.command(PokemonCarryTest.command(room.snapshot(),"SUMMON",summon(id)),0).status);assertEquals("85",String.valueOf(Json.asObject(room.snapshot().get("summoned")).get("hp")));
        room=new AgentRoom("injured",ProviderSelection.fromEnvironment(Collections.emptyMap()),Runnable::run,true,store);assertEquals("85",String.valueOf(Json.asObject(room.snapshot().get("summoned")).get("hp")));
    }
    @Test void invalidRestoredPlacementFreezesWithoutDeletingCollection(){
        for(int[] point:new int[][]{{29,10},{100,10}}){
            BattleRoomTest.Memory store=new BattleRoomTest.Memory();String id=UUID.randomUUID().toString();
            store.save("collection:invalid",Json.object("schemaVersion",1,"kind","POKEMON_COLLECTION","pokemon",Arrays.asList(Json.object("captureId",id,"species","TREECKO","hp",85,"maxHp",100)),"deployment",Json.object("captureId",id,"x",point[0],"y",point[1])));
            AgentRoom room=new AgentRoom("invalid",ProviderSelection.fromEnvironment(Collections.emptyMap()),Runnable::run,true,store);
            assertEquals("STORAGE_ERROR",room.snapshot().get("status"));assertNull(room.snapshot().get("summoned"));
            assertEquals(503,room.command(AgentRoomTest.command(room,"RECALL",Json.object("captureId",id)),0).status);
            assertEquals(503,room.command(AgentRoomTest.command(room,"RECOVER",Collections.emptyMap()),1).status);
            assertEquals(1,Json.asArray(store.load("collection:invalid").get("pokemon")).size());
        }
    }
    @Test void aiLifecycleAndStaleRevisionCannotManageDeployment(){
        BattleRoomTest.Memory store=new BattleRoomTest.Memory();String id=UUID.randomUUID().toString();
        store.save("collection:guard",Json.object("schemaVersion",1,"kind","POKEMON_COLLECTION","pokemon",Arrays.asList(Json.object("captureId",id,"species","TREECKO","hp",100,"maxHp",100))));
        Queue<Runnable> jobs=new ArrayDeque<>();AgentRoom room=new AgentRoom("guard",AgentRoomTest.provider((m,k,b,t)->new JsonTransport.Response(200,AgentRoomTest.parsed(b))),jobs::add,true,store);
        Map<String,Object> stale=AgentRoomTest.command(room,"SUMMON",summon(id));
        assertEquals(200,room.command(AgentRoomTest.command(room,"PARSE",Json.object("text","完成任务")),0).status);
        assertEquals(409,room.command(stale,1).status);
        assertEquals(409,room.command(AgentRoomTest.command(room,"SUMMON",summon(id)),1).status);
        jobs.remove().run();room.tick(2);assertEquals("READY",room.snapshot().get("status"));
        assertEquals(409,room.command(AgentRoomTest.command(room,"SUMMON",summon(id)),2).status);
        assertEquals(200,room.command(AgentRoomTest.command(room,"CONFIRM",Collections.emptyMap()),2).status);
        assertEquals(409,room.command(AgentRoomTest.command(room,"SUMMON",summon(id)),3).status);assertNull(room.snapshot().get("summoned"));
    }
    @Test void realPostgresRestartRestoresSummonedActorAndRecallFreesTile()throws Exception{
        String jdbc=System.getProperty("pokemon.test.jdbcUrl");org.junit.jupiter.api.Assumptions.assumeTrue(jdbc!=null,"PostgreSQL opt-in");String user=System.getProperty("pokemon.test.jdbcUser","postgres"),password=System.getProperty("pokemon.test.jdbcPassword","");int port;String cookie,id;
        try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,new JdbcWorldStore(()->java.sql.DriverManager.getConnection(jdbc,user,password)))){s.start();port=s.getPort();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(port);cookie=b.cookie;id=carried(b);send(b,PokemonCarryTest.room(b,"quest"),"SUMMON",summon(id));}
        try(AgentWebServer s=new AgentWebServer(port,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,new JdbcWorldStore(()->java.sql.DriverManager.getConnection(jdbc,user,password)))){
            s.start();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(port);b.cookie=cookie;b.csrf=(String)AgentWebServerTest.read(b.connection("/api/quest/session","GET")).get("csrfToken");String q=PokemonCarryTest.room(b,"quest");assertEquals(id,Json.asObject(snapshot(b,q).get("summoned")).get("captureId"));send(b,q,"RECALL",Json.object("captureId",id));send(b,q,"MANUAL",Json.object("action","move","arguments",Json.object("direction","E")));assertEquals("30",Json.asObject(snapshot(b,q).get("world")).get("x"));
        }
    }
}

