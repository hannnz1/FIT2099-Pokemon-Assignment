package game.agent;
import game.agent.web.*;
import game.agent.llm.*;
import game.agent.persistence.*;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class PokemonCarryTest {
    @TempDir Path directory;
    static Map<String,Object> command(Map<String,Object> view,String kind,Map<String,Object> params){return Json.object("requestId",UUID.randomUUID().toString(),"taskId",view.get("taskId"),"expectedRevision",view.get("revision"),"command",kind,"params",params);}
    static Map<String,Object> view(AgentWebServerTest.Browser b,String room)throws Exception{return AgentWebServerTest.read(b.connection(room+"/snapshot","GET"));}
    static String room(AgentWebServerTest.Browser b,String mode)throws Exception{return "/api/"+mode+"/rooms/"+b.post("/api/"+mode+"/rooms",Collections.emptyMap()).get("roomId");}
    static void send(AgentWebServerTest.Browser b,String room,String kind,Map<String,Object> params)throws Exception{b.post(room+"/commands",command(view(b,room),kind,params));}
    static void capture(AgentWebServerTest.Browser b,String room)throws Exception{
        send(b,room,"MANUAL",Json.object("action","move","arguments",Json.object("direction","E")));
        send(b,room,"MANUAL",Json.object("action","capture","arguments",Json.object("targetId","wild-treecko")));
    }
    static List<Object> collection(Map<String,Object> view){return Json.asArray(view.get("collection"));}
    @Test void actualCaptureMovesOnceAndBothSceneResetsPreserveCollection()throws Exception{
        try(AgentWebServer server=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,new FileWorldStore(directory))){
            server.start();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(server.getPort());String t=room(b,"training");capture(b,t);
            Map<String,Object> input=command(view(b,t),"TRANSFER",Json.object("targetId","wild-treecko"));
            b.post(t+"/commands",input);b.post(t+"/commands",input);
            Map<String,Object> trained=view(b,t);assertEquals(Collections.emptyList(),trained.get("captured"));assertTrue((Boolean)trained.get("goalComplete"));assertEquals("2",Json.asObject(trained.get("world")).get("turn"));
            assertEquals("TRANSFERRED",Json.asObject(Json.asArray(trained.get("targets")).get(0)).get("state"));
            assertEquals(1,collection(trained).size());assertEquals("100",String.valueOf(Json.asObject(collection(trained).get(0)).get("hp")));
            send(b,t,"RESET",Collections.emptyMap());capture(b,t);send(b,t,"TRANSFER",Json.object("targetId","wild-treecko"));assertEquals(2,collection(view(b,t)).size());
            String q=room(b,"quest");assertEquals(2,collection(view(b,q)).size());assertEquals("0",Json.asObject(view(b,q).get("world")).get("turn"));assertEquals("5",Json.asObject(view(b,q).get("world")).get("coins"));
            send(b,q,"RESET",Collections.emptyMap());assertEquals(2,collection(view(b,q)).size());
        }
    }
    @Test void sameCookieFileRestartKeepsOwnershipAndSourceCompletion()throws Exception{
        int port;String cookie;
        try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,new FileWorldStore(directory))){
            s.start();port=s.getPort();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(port);cookie=b.cookie;String t=room(b,"training");capture(b,t);send(b,t,"TRANSFER",Json.object("targetId","wild-treecko"));
        }
        try(AgentWebServer s=new AgentWebServer(port,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,new FileWorldStore(directory))){
            s.start();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(port);b.cookie=cookie;b.csrf=(String)AgentWebServerTest.read(b.connection("/api/quest/session","GET")).get("csrfToken");
            String q=room(b,"quest"),t=room(b,"training");assertEquals(1,collection(view(b,q)).size());assertEquals(Collections.emptyList(),view(b,t).get("captured"));assertTrue((Boolean)view(b,t).get("goalComplete"));
            send(b,t,"TRANSFER",Json.object("targetId","wild-treecko"));assertEquals(1,collection(view(b,q)).size());
            AgentWebServerTest.Browser other=new AgentWebServerTest.Browser(port);assertEquals(0,collection(view(other,room(other,"quest"))).size());
        }
    }
    @Test void missingCaptureWrongTargetAndDeselectedTabCannotTransfer()throws Exception{
        try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null)){
            s.start();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(s.getPort());String t=room(b,"training");
            java.net.HttpURLConnection bad=b.post(t+"/commands",Json.write(command(view(b,t),"TRANSFER",Json.object("targetId","wild-treecko"))),b.base,b.csrf);assertEquals(409,bad.getResponseCode());assertEquals("NOT_CAPTURED",AgentWebServerTest.read(bad).get("reasonCode"));
            bad=b.post(t+"/commands",Json.write(command(view(b,t),"TRANSFER",Json.object("targetId","wild-torchic"))),b.base,b.csrf);assertEquals(400,bad.getResponseCode());assertEquals("UNKNOWN_TARGET",AgentWebServerTest.read(bad).get("reasonCode"));
            bad=b.post(t+"/commands",Json.write(command(view(b,t),"TRANSFER",Json.object("targetId","wild-treecko","hp",100))),b.base,b.csrf);assertEquals(400,bad.getResponseCode());assertEquals("INVALID_PARAMS",AgentWebServerTest.read(bad).get("reasonCode"));
            capture(b,t);String q=room(b,"quest");bad=b.post(t+"/commands",Json.write(command(view(b,t),"TRANSFER",Json.object("targetId","wild-treecko"))),b.base,b.csrf);assertEquals(409,bad.getResponseCode());assertEquals("MODE_NOT_SELECTED",AgentWebServerTest.read(bad).get("reasonCode"));assertEquals(0,collection(view(b,q)).size());
        }
    }
    static final class FaultStore extends BattleRoomTest.Memory {
        String failPrefix;boolean afterCommit;
        @Override public void save(String owner,Map<String,Object> value){if(failPrefix!=null&&owner.startsWith(failPrefix)){if(afterCommit)super.save(owner,value);throw new IllegalStateException("injected write result");}super.save(owner,value);}
    }
    private void failedTransferRecovers(boolean collectionFailure,boolean ambiguous)throws Exception{
        FaultStore store=new FaultStore();int port;String cookie;
        try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,store)){
            s.start();port=s.getPort();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(port);cookie=b.cookie;String t=room(b,"training");capture(b,t);
            store.failPrefix=collectionFailure?"collection:":"battle:";store.afterCommit=ambiguous;
            java.net.HttpURLConnection result=b.post(t+"/commands",Json.write(command(view(b,t),"TRANSFER",Json.object("targetId","wild-treecko"))),b.base,b.csrf);assertEquals(503,result.getResponseCode());assertEquals("STORAGE_UNAVAILABLE",AgentWebServerTest.read(result).get("reasonCode"));
            assertEquals("STORAGE_ERROR",view(b,t).get("status"));assertEquals(collectionFailure?1:0,Json.asArray(view(b,t).get("captured")).size());
            store.failPrefix=null;
            send(b,t,"RECOVER",Collections.emptyMap());
            if(collectionFailure&&!ambiguous){assertEquals(1,Json.asArray(view(b,t).get("captured")).size());send(b,t,"TRANSFER",Json.object("targetId","wild-treecko"));}
            assertEquals(0,Json.asArray(view(b,t).get("captured")).size());assertEquals(1,collection(view(b,t)).size());
        }
        try(AgentWebServer s=new AgentWebServer(port,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,store)){
            s.start();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(port);b.cookie=cookie;b.csrf=(String)AgentWebServerTest.read(b.connection("/api/training/session","GET")).get("csrfToken");String t=room(b,"training");
            assertEquals(0,Json.asArray(view(b,t).get("captured")).size());assertEquals(1,collection(view(b,t)).size());assertTrue((Boolean)view(b,t).get("goalComplete"));
        }
    }
    @Test void collectionFailureNeverConsumesSourceAndRetrySucceeds()throws Exception{failedTransferRecovers(true,false);}
    @Test void committedCollectionWithUnknownResultRecoversExactlyOneOwner()throws Exception{failedTransferRecovers(true,true);}
    @Test void sourceWriteFailureAfterCollectionCommitRecoversExactlyOneOwner()throws Exception{failedTransferRecovers(false,false);}
    @Test void recoveryMustNotAcknowledgeSuccessWhileSourceReconciliationWriteStillFails()throws Exception{
        FaultStore store=new FaultStore();
        try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,store)){
            s.start();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(s.getPort());String t=room(b,"training");capture(b,t);store.failPrefix="battle:";
            java.net.HttpURLConnection result=b.post(t+"/commands",Json.write(command(view(b,t),"TRANSFER",Json.object("targetId","wild-treecko"))),b.base,b.csrf);assertEquals(503,result.getResponseCode());
            result=b.post(t+"/commands",Json.write(command(view(b,t),"RECOVER",Collections.emptyMap())),b.base,b.csrf);assertEquals(503,result.getResponseCode());assertEquals("STORAGE_ERROR",view(b,t).get("status"));assertEquals(1,collection(view(b,t)).size());
            store.failPrefix=null;send(b,t,"RECOVER",Collections.emptyMap());assertEquals(0,Json.asArray(view(b,t).get("captured")).size());assertEquals(1,collection(view(b,t)).size());
        }
    }
    @Test void fullCollectionLeavesTwentyFirstCaptureInTrainingBackpack(){
        BattleRoomTest.Memory store=new BattleRoomTest.Memory();BattleRoom room=new BattleRoom("capacity",ProviderSelection.fromEnvironment(Collections.emptyMap()),Runnable::run,store);
        for(int i=0;i<20;i++){BattleRoomTest.accepted(room,"MANUAL",BattleRoomTest.manual("move","direction","E"));BattleRoomTest.accepted(room,"MANUAL",BattleRoomTest.manual("capture","targetId","wild-treecko"));BattleRoomTest.accepted(room,"TRANSFER",Json.object("targetId","wild-treecko"));BattleRoomTest.accepted(room,"RESET",Collections.emptyMap());}
        BattleRoomTest.accepted(room,"MANUAL",BattleRoomTest.manual("move","direction","E"));BattleRoomTest.accepted(room,"MANUAL",BattleRoomTest.manual("capture","targetId","wild-treecko"));
        AgentRoom.Reply result=room.command(BattleRoomTest.command(room,"TRANSFER",Json.object("targetId","wild-treecko")),0);assertEquals(409,result.status);assertEquals("COLLECTION_FULL",result.body.get("reasonCode"));assertEquals(Arrays.asList("wild-treecko"),room.snapshot().get("captured"));assertEquals(20,Json.asArray(store.load("collection:capacity").get("pokemon")).size());
    }
    @Test void directRestartReconcilesOldSourceWithoutNeedingPreviousRecovery()throws Exception{
        for(String prefix:Arrays.asList("collection:","battle:")){
            FaultStore store=new FaultStore();int port;String cookie;
            try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,store)){
                s.start();port=s.getPort();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(port);cookie=b.cookie;String t=room(b,"training");capture(b,t);store.failPrefix=prefix;store.afterCommit=prefix.equals("collection:");
                java.net.HttpURLConnection result=b.post(t+"/commands",Json.write(command(view(b,t),"TRANSFER",Json.object("targetId","wild-treecko"))),b.base,b.csrf);assertEquals(503,result.getResponseCode());
            }
            store.failPrefix=null;
            try(AgentWebServer s=new AgentWebServer(port,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,store)){
                s.start();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(port);b.cookie=cookie;b.csrf=(String)AgentWebServerTest.read(b.connection("/api/training/session","GET")).get("csrfToken");String t=room(b,"training");
                assertEquals(0,Json.asArray(view(b,t).get("captured")).size());assertEquals(1,collection(view(b,t)).size());assertTrue((Boolean)view(b,t).get("goalComplete"));send(b,t,"TRANSFER",Json.object("targetId","wild-treecko"));assertEquals(1,collection(view(b,t)).size());
            }
        }
    }
    @Test void missingOwnershipCheckpointCannotUnfreezeTransferredSourceDuringRecovery()throws Exception{
        FaultStore store=new FaultStore();
        try(AgentWebServer s=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null,true,store)){
            s.start();AgentWebServerTest.Browser b=new AgentWebServerTest.Browser(s.getPort());String t=room(b,"training");capture(b,t);send(b,t,"TRANSFER",Json.object("targetId","wild-treecko"));store.failPrefix="battle:";
            java.net.HttpURLConnection result=b.post(t+"/commands",Json.write(command(view(b,t),"MANUAL",Json.object("action","move","arguments",Json.object("direction","S")))),b.base,b.csrf);assertEquals(503,result.getResponseCode());
            store.failPrefix=null;store.values.keySet().removeIf(key->key.startsWith("collection:"));
            result=b.post(t+"/commands",Json.write(command(view(b,t),"RECOVER",Collections.emptyMap())),b.base,b.csrf);assertEquals(503,result.getResponseCode());assertEquals("STORAGE_ERROR",view(b,t).get("status"));
        }
    }
    @Test void legacyCaptureHasStableIdentityAndTransfersActualInjuredPokemon(){
        game.agent.combat.BattleTrainingScenario scene=new game.agent.combat.BattleTrainingScenario();scene.manual("move","E");scene.manual("capture","wild-treecko");
        Map<String,Object> legacy=scene.exportState();legacy.remove("arenaId");legacy.remove("transferred");Json.asObject(Json.asArray(legacy.get("targets")).get(0)).put("hp",85);
        game.agent.combat.BattleTrainingScenario a=game.agent.combat.BattleTrainingScenario.restore(legacy),b=game.agent.combat.BattleTrainingScenario.restore(legacy);assertEquals(a.getArenaId(),b.getArenaId());assertEquals(85,a.capturedBall().getPokemon().getHitPoints());
        BattleRoomTest.Memory store=new BattleRoomTest.Memory();ProviderSelection p=ProviderSelection.fromEnvironment(Collections.emptyMap());new BattleRoom("legacy",p,Runnable::run,store);store.load("battle:legacy").put("scene",legacy);
        BattleRoom room=new BattleRoom("legacy",p,Runnable::run,store);BattleRoomTest.accepted(room,"TRANSFER",Json.object("targetId","wild-treecko"));
        assertEquals("85",String.valueOf(Json.asObject(Json.asArray(store.load("collection:legacy").get("pokemon")).get(0)).get("hp")));
        room=new BattleRoom("legacy",p,Runnable::run,store);BattleRoomTest.accepted(room,"TRANSFER",Json.object("targetId","wild-treecko"));assertEquals(1,Json.asArray(store.load("collection:legacy").get("pokemon")).size());
    }
    @Test void realPostgresRestartRetainsCollectionAndSourceOwnership()throws Exception{
        String jdbc=System.getProperty("pokemon.test.jdbcUrl");org.junit.jupiter.api.Assumptions.assumeTrue(jdbc!=null,"PostgreSQL opt-in");
        String user=System.getProperty("pokemon.test.jdbcUser","postgres"),password=System.getProperty("pokemon.test.jdbcPassword","");
        int port;String cookie;ProviderSelection provider=ProviderSelection.fromEnvironment(Collections.emptyMap());
        try(AgentWebServer s=new AgentWebServer(0,provider,null,true,new JdbcWorldStore(()->java.sql.DriverManager.getConnection(jdbc,user,password)))){
            s.start();port=s.getPort();AgentWebServerTest.Browser browser=new AgentWebServerTest.Browser(port);cookie=browser.cookie;String t=room(browser,"training");capture(browser,t);send(browser,t,"TRANSFER",Json.object("targetId","wild-treecko"));
        }
        try(AgentWebServer s=new AgentWebServer(port,provider,null,true,new JdbcWorldStore(()->java.sql.DriverManager.getConnection(jdbc,user,password)))){
            s.start();AgentWebServerTest.Browser browser=new AgentWebServerTest.Browser(port);browser.cookie=cookie;browser.csrf=(String)AgentWebServerTest.read(browser.connection("/api/training/session","GET")).get("csrfToken");String t=room(browser,"training");
            assertEquals(Collections.emptyList(),view(browser,t).get("captured"));assertEquals(1,collection(view(browser,t)).size());String q=room(browser,"quest");assertEquals(1,collection(view(browser,q)).size());
        }
    }
    @Test void parsingConfirmationAndRunningAiCannotTransferAndStaleRevisionCannotMoveAssets(){
        BattleRoom room=new BattleRoom("controls",BattleRoomTest.provider(BattleRoomTest.captureProvider()),Runnable::run,null);
        Map<String,Object> stale=BattleRoomTest.command(room,"TRANSFER",Json.object("targetId","wild-treecko"));
        BattleRoomTest.accepted(room,"PARSE",Json.object("text","捕捉木守宫"));
        assertEquals("STALE_REVISION",room.command(stale,0).body.get("reasonCode"));
        for(String phase:Arrays.asList("PARSING","READY","RUNNING")){
            assertEquals(phase,room.snapshot().get("status"));assertEquals("AI_CONTROLS_COMPANION",room.command(BattleRoomTest.command(room,"TRANSFER",Json.object("targetId","wild-treecko")),0).body.get("reasonCode"));
            if("PARSING".equals(phase))room.tick(1);else if("READY".equals(phase))BattleRoomTest.accepted(room,"CONFIRM",Collections.emptyMap());
        }
        assertEquals("0",Json.asObject(room.snapshot().get("world")).get("turn"));room.close();
    }
}
