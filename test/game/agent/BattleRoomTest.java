package game.agent;
import game.agent.web.*;
import game.agent.llm.*;
import game.agent.persistence.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BattleRoomTest {
    @Test void fractionalRevisionIsAClientErrorWithoutMutation(){
        BattleRoom room=new BattleRoom("owner",provider(captureProvider()),Runnable::run,null);Map<String,Object> input=command(room,"MANUAL",manual("move","direction","E"));input.put("expectedRevision",0.5);assertEquals(400,room.command(input,0).status);assertEquals("0",Json.asObject(room.snapshot().get("world")).get("turn"));
    }
    static ProviderSelection provider(JsonTransport transport){OpenAiGateway gateway=new OpenAiGateway(new OpenAiConfig("test-key","gpt-6-luna",1000),transport);return new ProviderSelection("openai","gpt-6-luna",1000,gateway,gateway);}
    static JsonTransport captureProvider(){return (m,k,b,t)->{
        if(b.contains("json_schema"))return new JsonTransport.Response(200,OpenAiGatewayTest.text(Json.write(Json.object("goal","CAPTURE","targetId","wild-treecko","noBattle",true))));
        Map<String,Object> obs=Json.asObject(Json.asObject(Json.read((String)Json.asObject(Json.read(b)).get("input"))).get("observation"));
        return new JsonTransport.Response(200,"0".equals(obs.get("x"))?OpenAiGatewayTest.function("move_to","{\"locationId\":\"treecko-approach\"}"):OpenAiGatewayTest.function("capture","{\"targetId\":\"wild-treecko\"}"));};}
    static Map<String,Object> command(BattleRoom room,String kind,Map<String,Object> params){Map<String,Object> view=room.snapshot();return Json.object("requestId",UUID.randomUUID().toString(),"taskId",view.get("taskId"),"expectedRevision",view.get("revision"),"command",kind,"params",params);}
    static void accepted(BattleRoom room,String kind,Map<String,Object> params){assertEquals(200,room.command(command(room,kind,params),0).status);}
    static Map<String,Object> manual(String action,String field,String value){return Json.object("action",action,"arguments",Json.object(field,value));}
    static class Memory implements WorldStore {Map<String,Map<String,Object>> values=new HashMap<>();boolean fail;public void save(String owner,Map<String,Object> value){if(fail)throw new IllegalStateException();values.put(owner,Json.asObject(Json.read(Json.write(value))));}public Map<String,Object> load(String owner){return values.get(owner);}public void close(){}}
    @Test void parseRequiresConfirmationAndActualCaptureCompletes(){
        BattleRoom room=new BattleRoom("owner",provider(captureProvider()),Runnable::run,null);
        accepted(room,"PARSE",Json.object("text","捕捉木守宫不要战斗"));room.tick(1);assertEquals("READY",room.snapshot().get("status"));assertEquals("0",Json.asObject(room.snapshot().get("world")).get("turn"));
        accepted(room,"CONFIRM",Collections.emptyMap());
        for(int i=2;i<30;i++)room.tick(i);
        assertEquals("COMPLETED",room.snapshot().get("status"));assertEquals(Arrays.asList("wild-treecko"),room.snapshot().get("captured"));assertFalse(Json.write(room.snapshot()).contains("test-key"));
        assertEquals("2",Json.asObject(room.snapshot().get("world")).get("turn"));
    }
    @Test void repeatedManualCaptureDoesNotConsumeAnotherTurnAndConflictingIdFails(){
        BattleRoom room=new BattleRoom("owner",provider(captureProvider()),Runnable::run,null);accepted(room,"MANUAL",manual("move","direction","E"));
        Map<String,Object> input=command(room,"MANUAL",manual("capture","targetId","wild-treecko"));AgentRoom.Reply first=room.command(input,1);assertEquals(200,first.status);assertSame(first,room.command(input,2));
        assertEquals("2",Json.asObject(room.snapshot().get("world")).get("turn"));input.put("params",manual("attack","targetId","wild-treecko"));assertEquals("REQUEST_ID_CONFLICT",room.command(input,3).body.get("reasonCode"));
    }
    @Test void cancelledDelayedParseCannotBecomeReady(){
        List<Runnable> jobs=new ArrayList<>();BattleRoom room=new BattleRoom("owner",provider(captureProvider()),jobs::add,null);
        accepted(room,"PARSE",Json.object("text","捕捉木守宫"));accepted(room,"CANCEL",Collections.emptyMap());jobs.get(0).run();room.tick(1);assertEquals("CANCELLED",room.snapshot().get("status"));assertNull(room.snapshot().get("intent"));
    }
    @Test void pausedLateDecisionCannotMoveAndManualControlWorks(){
        List<Runnable> jobs=new ArrayList<>();BattleRoom room=new BattleRoom("owner",provider(captureProvider()),jobs::add,null);
        accepted(room,"PARSE",Json.object("text","捕捉木守宫"));jobs.remove(0).run();room.tick(1);accepted(room,"CONFIRM",Collections.emptyMap());room.tick(2);
        accepted(room,"PAUSE",Collections.emptyMap());jobs.remove(0).run();room.tick(3);assertEquals("0",Json.asObject(room.snapshot().get("world")).get("turn"));accepted(room,"MANUAL",manual("move","direction","E"));assertEquals("1",Json.asObject(room.snapshot().get("world")).get("turn"));
    }
    @Test void restoredTaskIsPausedWithFreshIdAndFailureFreezesUntilRecovery(){
        Memory store=new Memory();BattleRoom room=new BattleRoom("owner",provider(captureProvider()),Runnable::run,store);
        accepted(room,"PARSE",Json.object("text","捕捉木守宫"));room.tick(1);accepted(room,"CONFIRM",Collections.emptyMap());room.tick(2);room.tick(3);
        Map<String,Object> old=command(room,"PAUSE",Collections.emptyMap());BattleRoom restored=new BattleRoom("owner",provider(captureProvider()),Runnable::run,store);
        assertEquals("PAUSED",restored.snapshot().get("status"));assertEquals("STALE_TASK",restored.command(old,4).body.get("reasonCode"));assertTrue(store.values.containsKey("battle:owner"));assertFalse(store.values.containsKey("owner"));
        store.fail=true;assertEquals(503,restored.command(command(restored,"MANUAL",manual("move","direction","S")),5).status);assertEquals("STORAGE_ERROR",restored.snapshot().get("status"));
        assertEquals(503,restored.command(command(restored,"MANUAL",manual("move","direction","S")),6).status);store.fail=false;accepted(restored,"RECOVER",Collections.emptyMap());assertEquals("PAUSED",restored.snapshot().get("status"));
    }
    @Test void realHttpBattleReusesSecurityAndRealEngine() throws Exception {
        try(AgentWebServer server=new AgentWebServer(0,provider(captureProvider()),Paths.get("client/agent"),false,null,true)){
            server.start();AgentWebServerTest.Browser browser=new AgentWebServerTest.Browser(server.getPort());String id=(String)browser.post("/api/agent/rooms",Collections.emptyMap()).get("roomId");
            assertEquals("BATTLE_TRAINING",browser.snapshot(id).get("mode"));assertEquals(403,browser.post("/api/agent/rooms","{}",browser.base,"wrong").getResponseCode());
            browser.cmd(id,browser.snapshot(id),"PARSE",Json.object("text","捕捉木守宫"));browser.cmd(id,AgentWebServerTest.await(browser,id,"READY"),"CONFIRM",Collections.emptyMap());
            Map<String,Object> done=AgentWebServerTest.await(browser,id,"COMPLETED");assertEquals(Arrays.asList("wild-treecko"),done.get("captured"));assertEquals(true,done.get("goalComplete"));
            assertEquals(200,browser.connection("/battle-view.mjs","GET").getResponseCode());assertEquals(200,browser.connection("/","GET").getResponseCode());
        }
    }
    @Test void parserRejectsUnsupportedAndContradictoryResponses(){
        for(Map<String,Object> value:Arrays.asList(Json.object("goal","DEFEAT","targetId","wild-torchic","noBattle",true),Json.object("goal","CAPTURE","targetId","wild-torchic","noBattle",false),Json.object("goal","UNSUPPORTED","targetId","UNSUPPORTED","noBattle",false)))
            assertEquals(ProviderException.Code.INVALID_RESPONSE,assertThrows(ProviderException.class,()->new OpenAiGateway(new OpenAiConfig("test-key","gpt-6-luna",1000),(m,k,b,t)->new JsonTransport.Response(200,OpenAiGatewayTest.text(Json.write(value)))).parseBattle("任务")).getCode());
    }
    @Test void quotaAndParseTimeoutNeverAdvanceWorld(){
        BattleRoom quota=new BattleRoom("owner",provider((m,k,b,t)->new JsonTransport.Response(429,"{\"error\":{\"code\":\"insufficient_quota\"}}")),Runnable::run,null);
        accepted(quota,"PARSE",Json.object("text","捕捉木守宫"));quota.tick(1);assertEquals("PROVIDER_QUOTA_EXHAUSTED",quota.snapshot().get("errorCode"));assertEquals("0",Json.asObject(quota.snapshot().get("world")).get("turn"));
        List<Runnable> jobs=new ArrayList<>();BattleRoom timeout=new BattleRoom("owner",provider(captureProvider()),jobs::add,null);accepted(timeout,"PARSE",Json.object("text","捕捉木守宫"));timeout.tick(2000);jobs.get(0).run();timeout.tick(2001);assertEquals("ERROR",timeout.snapshot().get("status"));assertEquals("0",Json.asObject(timeout.snapshot().get("world")).get("turn"));
    }
    @Test void cancelledAndCompletedGoalsKeepTerminalStatusAcrossMultipleRestarts(){
        Memory store=new Memory();BattleRoom room=new BattleRoom("cancel",provider(captureProvider()),Runnable::run,store);accepted(room,"PARSE",Json.object("text","捕捉木守宫"));room.tick(1);accepted(room,"CONFIRM",Collections.emptyMap());accepted(room,"CANCEL",Collections.emptyMap());
        BattleRoom cancelled=new BattleRoom("cancel",provider(captureProvider()),Runnable::run,store);assertEquals("CANCELLED",cancelled.snapshot().get("status"));assertEquals(409,cancelled.command(command(cancelled,"RESUME",Collections.emptyMap()),0).status);
        BattleRoom complete=new BattleRoom("done",provider(captureProvider()),Runnable::run,store);accepted(complete,"PARSE",Json.object("text","捕捉木守宫"));complete.tick(1);accepted(complete,"CONFIRM",Collections.emptyMap());for(int i=2;i<20;i++)complete.tick(i);
        BattleRoom restored=new BattleRoom("done",provider(captureProvider()),Runnable::run,store);assertEquals("COMPLETED",restored.snapshot().get("status"));accepted(restored,"MANUAL",manual("move","direction","S"));
        assertEquals("COMPLETED",new BattleRoom("done",provider(captureProvider()),Runnable::run,store).snapshot().get("status"));
    }
    @Test void failedFirstSaveCanRecoverWithoutPretendingThereWasAPreviousSave(){
        Memory store=new Memory();store.fail=true;BattleRoom room=new BattleRoom("new",provider(captureProvider()),Runnable::run,store);assertEquals("STORAGE_ERROR",room.snapshot().get("status"));store.fail=false;accepted(room,"RECOVER",Collections.emptyMap());assertEquals("IDLE",room.snapshot().get("status"));assertNotNull(store.load("battle:new"));
    }
    @Test void overshootingTimeoutCheckpointRestoresAsCancelled(){
        Memory store=new Memory();BattleRoom room=new BattleRoom("timeout",provider(captureProvider()),Runnable::run,store);accepted(room,"PARSE",Json.object("text","捕捉木守宫"));room.tick(1);accepted(room,"CONFIRM",Collections.emptyMap());room.tick(120001);
        assertEquals("CANCELLED",room.snapshot().get("status"));assertEquals("CANCELLED",new BattleRoom("timeout",provider(captureProvider()),Runnable::run,store).snapshot().get("status"));
    }

    @Test void approachIsOneAcceptedStepAndResetProtectsUncollectedCapture(){
        Memory store=new Memory();BattleRoom room=new BattleRoom("flow",provider(captureProvider()),Runnable::run,store);
        accepted(room,"MANUAL",manual("approach","targetId","wild-torchic"));
        assertEquals("1",Json.asObject(room.snapshot().get("world")).get("turn"));
        assertTrue(Integer.parseInt((String)Json.asObject(room.snapshot().get("world")).get("x"))<=1);
        accepted(room,"MANUAL",manual("capture","targetId","wild-treecko"));
        assertEquals("COLLECT_BEFORE_RESET",room.command(command(room,"RESET",Collections.emptyMap()),0).body.get("reasonCode"));
        accepted(room,"REST",Collections.emptyMap());
        assertEquals(Arrays.asList("wild-treecko"),room.snapshot().get("captured"));
        assertEquals("2",Json.asObject(room.snapshot().get("world")).get("turn"));
        accepted(room,"TRANSFER",Json.object("targetId","wild-treecko"));
        accepted(room,"RESET",Collections.emptyMap());
        BattleRoom restored=new BattleRoom("flow",provider(captureProvider()),Runnable::run,store);
        assertEquals("0",Json.asObject(restored.snapshot().get("world")).get("turn"));
        assertEquals(1,Json.asArray(store.values.get("collection:flow").get("pokemon")).size());
    }
    @Test void recoveryPreservesTargetsAfterDamageAndPersists(){
        Memory store=new Memory();BattleRoom room=new BattleRoom("rest",provider(captureProvider()),Runnable::run,store);
        accepted(room,"MANUAL",manual("approach","targetId","wild-treecko"));
        accepted(room,"MANUAL",manual("attack","targetId","wild-treecko"));
        Object targets=room.snapshot().get("targets"),turn=Json.asObject(room.snapshot().get("world")).get("turn");
        accepted(room,"REST",Collections.emptyMap());
        BattleRoom restored=new BattleRoom("rest",provider(captureProvider()),Runnable::run,store);
        assertEquals("1000",Json.asObject(restored.snapshot().get("world")).get("actorHp"));
        assertEquals(Json.write(targets),Json.write(restored.snapshot().get("targets")));
        assertEquals(turn,Json.asObject(restored.snapshot().get("world")).get("turn"));
    }

    @Test void tutorialProgressPersistsAndReplayDoesNotResetCapture(){
        Memory store=new Memory();BattleRoom room=new BattleRoom("tutorial",provider(captureProvider()),Runnable::run,store);
        accepted(room,"MANUAL",manual("move","direction","E"));
        accepted(room,"TUTORIAL",Json.object("action","SELECT"));
        accepted(room,"MANUAL",manual("capture","targetId","wild-treecko"));
        accepted(room,"TUTORIAL",Json.object("action","SKIP"));
        BattleRoom restored=new BattleRoom("tutorial",provider(captureProvider()),Runnable::run,store);
        Map<String,Object> guide=Json.asObject(restored.snapshot().get("tutorial"));
        assertEquals(true,guide.get("skipped"));assertEquals(1,((Number)guide.get("moves")).intValue());assertEquals(true,guide.get("selected"));
        accepted(restored,"TUTORIAL",Json.object("action","REPLAY"));
        assertEquals(false,Json.asObject(restored.snapshot().get("tutorial")).get("skipped"));
        assertEquals(Arrays.asList("wild-treecko"),restored.snapshot().get("captured"));
    }
}
