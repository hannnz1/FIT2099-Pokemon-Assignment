package game.agent;
import game.agent.web.*;
import game.agent.llm.*;
import java.nio.file.Paths;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UnifiedEntryTest {
    private Map<String,Object> command(Map<String,Object> view,String kind,Map<String,Object> params){return Json.object("requestId",UUID.randomUUID().toString(),"taskId",view.get("taskId"),"expectedRevision",view.get("revision"),"command",kind,"params",params);}
    @Test void oneSessionKeepsTwoRoomsAndAliasesCannotCrossTheirCommands() throws Exception {
        try(AgentWebServer server=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),Paths.get("client/agent"))){
            server.start();AgentWebServerTest.Browser browser=new AgentWebServerTest.Browser(server.getPort());
            String quest=(String)browser.post("/api/quest/rooms",Collections.emptyMap()).get("roomId");String training=(String)browser.post("/api/training/rooms",Collections.emptyMap()).get("roomId");assertNotEquals(quest,training);
            String q="/api/quest/rooms/"+quest,t="/api/training/rooms/"+training;
            Map<String,Object> original=AgentWebServerTest.read(browser.connection(q+"/snapshot","GET")),battle=AgentWebServerTest.read(browser.connection(t+"/snapshot","GET"));
            browser.post(t+"/commands",command(battle,"MANUAL",Json.object("action","move","arguments",Json.object("direction","E"))));
            assertEquals("0",Json.asObject(AgentWebServerTest.read(browser.connection(q+"/snapshot","GET")).get("world")).get("turn"));
            assertEquals("1",Json.asObject(AgentWebServerTest.read(browser.connection(t+"/snapshot","GET")).get("world")).get("turn"));
            assertEquals(quest,browser.post("/api/agent/rooms",Collections.emptyMap()).get("roomId"));
            assertEquals(404,browser.connection("/api/training/rooms/"+quest+"/snapshot","GET").getResponseCode());
            assertEquals(403,browser.post(t+"/commands",Json.write(command(battle,"CANCEL",Collections.emptyMap())),browser.base,"wrong").getResponseCode());
            assertEquals(403,browser.post(t+"/commands",Json.write(command(battle,"CANCEL",Collections.emptyMap())),"https://attacker.example",browser.csrf).getResponseCode());
            for(String asset:Arrays.asList("/quest/","/training/","/quest/app.mjs","/training/app.mjs","/training/client.mjs","/quest/style.css","/training/style.css"))assertEquals(200,browser.connection(asset,"GET").getResponseCode(),asset);
        }
    }
    @Test void otherPlayerCannotReadEitherMode() throws Exception {
        try(AgentWebServer server=new AgentWebServer(0,ProviderSelection.fromEnvironment(Collections.emptyMap()),null)){
            server.start();AgentWebServerTest.Browser a=new AgentWebServerTest.Browser(server.getPort()),b=new AgentWebServerTest.Browser(server.getPort());
            String training=(String)a.post("/api/training/rooms",Collections.emptyMap()).get("roomId");assertEquals(404,b.connection("/api/training/rooms/"+training+"/snapshot","GET").getResponseCode());
        }
    }
    @Test void bothCheckpointsSurviveOnePortRestartUsingSameCookie() throws Exception {
        BattleRoomTest.Memory store=new BattleRoomTest.Memory();ProviderSelection provider=ProviderSelection.fromEnvironment(Collections.emptyMap());String cookie;int port;
        try(AgentWebServer server=new AgentWebServer(0,provider,null,false,store)){
            server.start();port=server.getPort();AgentWebServerTest.Browser browser=new AgentWebServerTest.Browser(port);cookie=browser.cookie;
            String quest=(String)browser.post("/api/quest/rooms",Collections.emptyMap()).get("roomId"),training=(String)browser.post("/api/training/rooms",Collections.emptyMap()).get("roomId");
            String q="/api/quest/rooms/"+quest,t="/api/training/rooms/"+training;
            browser.post("/api/quest/rooms",Collections.emptyMap());
            Map<String,Object> view=AgentWebServerTest.read(browser.connection(q+"/snapshot","GET"));browser.post(q+"/commands",command(view,"MANUAL",Json.object("action","wait","arguments",Collections.emptyMap())));
            browser.post("/api/training/rooms",Collections.emptyMap());
            view=AgentWebServerTest.read(browser.connection(t+"/snapshot","GET"));browser.post(t+"/commands",command(view,"MANUAL",Json.object("action","move","arguments",Json.object("direction","E"))));
            view=AgentWebServerTest.read(browser.connection(t+"/snapshot","GET"));browser.post(t+"/commands",command(view,"MANUAL",Json.object("action","capture","arguments",Json.object("targetId","wild-treecko"))));
        }
        try(AgentWebServer restarted=new AgentWebServer(port,provider,null,false,store)){
            restarted.start();AgentWebServerTest.Browser browser=new AgentWebServerTest.Browser(port);browser.cookie=cookie;
            browser.csrf=(String)AgentWebServerTest.read(browser.connection("/api/training/session","GET")).get("csrfToken");
            String quest=(String)browser.post("/api/quest/rooms",Collections.emptyMap()).get("roomId"),training=(String)browser.post("/api/training/rooms",Collections.emptyMap()).get("roomId");
            Map<String,Object> q=AgentWebServerTest.read(browser.connection("/api/quest/rooms/"+quest+"/snapshot","GET")),t=AgentWebServerTest.read(browser.connection("/api/training/rooms/"+training+"/snapshot","GET"));
            assertEquals("1",Json.asObject(q.get("world")).get("turn"));assertEquals("2",Json.asObject(t.get("world")).get("turn"));assertEquals(Arrays.asList("wild-treecko"),t.get("captured"));assertEquals(2,store.values.size());
        }
    }
    @Test void activeOtherModeBlocksNewMutationsButDoesNotChangeCachedReceipts(){
        BattleRoom room=new BattleRoom("owner",BattleRoomTest.provider(BattleRoomTest.captureProvider()),Runnable::run,null);
        Map<String,Object> move=command(room.snapshot(),"MANUAL",Json.object("action","move","arguments",Json.object("direction","E")));AgentRoom.Reply first=room.command(move,0,false);assertEquals(200,first.status);assertSame(first,room.command(move,1,true));
        Map<String,Object> newMove=command(room.snapshot(),"MANUAL",Json.object("action","move","arguments",Json.object("direction","S")));assertEquals("OTHER_MODE_ACTIVE",room.command(newMove,2,true).body.get("reasonCode"));assertEquals("1",Json.asObject(room.snapshot().get("world")).get("turn"));
        AgentRoom quest=new AgentRoom("owner",ProviderSelection.fromEnvironment(Collections.emptyMap()),Runnable::run);
        Map<String,Object> wait=command(quest.snapshot(),"MANUAL",Json.object("action","wait","arguments",Collections.emptyMap()));AgentRoom.Reply receipt=quest.command(wait,0,false);assertEquals(200,receipt.status);assertSame(receipt,quest.command(wait,1,true));
        assertEquals("OTHER_MODE_ACTIVE",quest.command(command(quest.snapshot(),"MANUAL",Json.object("action","wait","arguments",Collections.emptyMap())),2,true).body.get("reasonCode"));
    }
    @Test void atomicSwitchRejectsOldTabResumeAndReceiptReplayCannotReclaimControl() throws Exception {
        java.util.concurrent.CountDownLatch release=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch decisionStarted=new java.util.concurrent.CountDownLatch(1);
        JsonTransport transport=(m,k,b,t)->{
            if(b.contains("json_schema"))return new JsonTransport.Response(200,OpenAiGatewayTest.text(Json.write(Json.object("goal","CAPTURE","targetId","wild-treecko","noBattle",true))));
            decisionStarted.countDown();
            try{release.await(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.IOException();}
            return new JsonTransport.Response(200,OpenAiGatewayTest.function("move_to","{\"locationId\":\"treecko-approach\"}"));
        };
        try(AgentWebServer server=new AgentWebServer(0,BattleRoomTest.provider(transport),null)){
            server.start();AgentWebServerTest.Browser browser=new AgentWebServerTest.Browser(server.getPort());String qid=(String)browser.post("/api/quest/rooms",Collections.emptyMap()).get("roomId"),tid=(String)browser.post("/api/training/rooms",Collections.emptyMap()).get("roomId");
            String q="/api/quest/rooms/"+qid,t="/api/training/rooms/"+tid;
            Map<String,Object> view=AgentWebServerTest.read(browser.connection(t+"/snapshot","GET"));browser.post(t+"/commands",command(view,"PARSE",Json.object("text","捕捉木守宫")));
            long until=System.nanoTime()+3000000000L;do{view=AgentWebServerTest.read(browser.connection(t+"/snapshot","GET"));if("READY".equals(view.get("status")))break;Thread.sleep(20);}while(System.nanoTime()<until);assertEquals("READY",view.get("status"));
            browser.post(t+"/commands",command(view,"CONFIRM",Collections.emptyMap()));
            assertTrue(decisionStarted.await(3,java.util.concurrent.TimeUnit.SECONDS),"Model decision must be pending before switching");
            Map<String,Object> quest=AgentWebServerTest.read(browser.connection(q+"/snapshot","GET"));Map<String,Object> move=command(quest,"MANUAL",Json.object("action","wait","arguments",Collections.emptyMap()));
            java.net.HttpURLConnection denied=browser.post(q+"/commands",Json.write(move),browser.base,browser.csrf);assertEquals(409,denied.getResponseCode());assertEquals("MODE_NOT_SELECTED",AgentWebServerTest.read(denied).get("reasonCode"));
            view=AgentWebServerTest.read(browser.connection(t+"/snapshot","GET"));Map<String,Object> handoff=command(view,"SWITCH_MODE",Json.object("mode","quest"));browser.post(t+"/commands",handoff);
            view=AgentWebServerTest.read(browser.connection(t+"/snapshot","GET"));assertEquals("PAUSED",view.get("status"));
            java.net.HttpURLConnection oldResume=browser.post(t+"/commands",Json.write(command(view,"RESUME",Collections.emptyMap())),browser.base,browser.csrf);assertEquals(409,oldResume.getResponseCode());assertEquals("MODE_NOT_SELECTED",AgentWebServerTest.read(oldResume).get("reasonCode"));
            browser.post(q+"/commands",move);release.countDown();Thread.sleep(150);
            assertEquals("1",Json.asObject(AgentWebServerTest.read(browser.connection(q+"/snapshot","GET")).get("world")).get("turn"));assertEquals("0",Json.asObject(AgentWebServerTest.read(browser.connection(t+"/snapshot","GET")).get("world")).get("turn"));
            browser.post("/api/training/rooms",Collections.emptyMap());browser.post(t+"/commands",handoff);
            view=AgentWebServerTest.read(browser.connection(t+"/snapshot","GET"));browser.post(t+"/commands",command(view,"MANUAL",Json.object("action","move","arguments",Json.object("direction","E"))));
            assertEquals("1",Json.asObject(AgentWebServerTest.read(browser.connection(t+"/snapshot","GET")).get("world")).get("turn"));
            view=AgentWebServerTest.read(browser.connection(t+"/snapshot","GET"));browser.post(t+"/commands",command(view,"RESUME",Collections.emptyMap()));
            browser.post("/api/quest/rooms",Collections.emptyMap());view=AgentWebServerTest.read(browser.connection(t+"/snapshot","GET"));assertEquals("PAUSED",view.get("status"));
            java.net.HttpURLConnection backgroundMove=browser.post(t+"/commands",Json.write(command(view,"MANUAL",Json.object("action","move","arguments",Json.object("direction","S")))),browser.base,browser.csrf);assertEquals(409,backgroundMove.getResponseCode());assertEquals("MODE_NOT_SELECTED",AgentWebServerTest.read(backgroundMove).get("reasonCode"));
        }finally{release.countDown();}
    }
}
