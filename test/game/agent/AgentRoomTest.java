package game.agent;

import game.agent.web.*;
import game.agent.llm.*;
import game.agent.tools.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentRoomTest {
    static ProviderSelection provider(JsonTransport transport) {
        OpenAiGateway gateway=new OpenAiGateway(new OpenAiConfig("test-key","gpt-6-luna",1000),transport);
        return new ProviderSelection("openai","gpt-6-luna",1000,gateway,gateway);
    }
    static String parsed(String body) {
        String quest=(String)Json.asObject(Json.read((String)Json.asObject(Json.read(body)).get("input"))).get("trustedQuestId");
        return OpenAiGatewayTest.task(quest,"NO_SPENDING");
    }
    static Map<String,Object> command(AgentRoom room,String kind,Map<String,Object> params) {
        Map<String,Object> view=room.snapshot();
        return Json.object("requestId",UUID.randomUUID().toString(),"taskId",view.get("taskId"),"expectedRevision",view.get("revision"),"command",kind,"params",params);
    }
    static void start(AgentRoom room) {
        assertEquals(200,room.command(command(room,"PARSE",Json.object("text","完成树果任务，不要花金币")),0).status);
        room.tick(1); assertEquals("READY",room.snapshot().get("status"));
        assertEquals(200,room.command(command(room,"CONFIRM",Collections.emptyMap()),1).status);
    }
    @Test void interpretationRequiresConfirmationAndSnapshotsNeverExposeCredentials() {
        AgentRoom room=new AgentRoom("owner",provider((m,k,b,t)->new JsonTransport.Response(200,parsed(b))),Runnable::run);
        Map<String,Object> parse=command(room,"PARSE",Json.object("text","完成任务"));
        AgentRoom.Reply first=room.command(parse,0);assertEquals(200,first.status);room.tick(1);
        assertEquals("READY",room.snapshot().get("status"));
        assertEquals("0",Json.asObject(room.snapshot().get("world")).get("carriedBerry"));
        assertEquals(first.body,room.command(parse,1).body);
        parse.put("params",Json.object("text","另一个请求"));assertEquals(409,room.command(parse,1).status);
        assertFalse(Json.write(room.snapshot()).contains("test-key"));assertFalse(Json.write(room.snapshot()).contains("approvalToken"));
    }
    @Test void pauseAndCancelInvalidateLateModelResultsAndResetChangesTaskIdentity() {
        Queue<Runnable> jobs=new ArrayDeque<>();
        AgentRoom room=new AgentRoom("owner",provider((m,k,b,t)->new JsonTransport.Response(200,b.contains("json_schema")?parsed(b):
            OpenAiGatewayTest.function("move_to","{\"locationId\":\"orchard\"}"))),jobs::add);
        room.command(command(room,"PARSE",Json.object("text","完成任务")),0);jobs.remove().run();room.tick(1);
        room.command(command(room,"CONFIRM",Collections.emptyMap()),1);room.tick(2);
        Map<String,Object> cancel=command(room,"CANCEL",Collections.emptyMap());
        room.command(command(room,"PAUSE",Collections.emptyMap()),3);jobs.remove().run();room.tick(4);
        assertEquals("PAUSED",room.snapshot().get("status"));assertEquals("0",Json.asObject(room.snapshot().get("world")).get("x"));
        assertEquals(200,room.command(cancel,4).status);String old=(String)room.snapshot().get("taskId");
        room.command(command(room,"RESET",Collections.emptyMap()),5);
        assertNotEquals(old,room.snapshot().get("taskId"));assertEquals(200,room.command(cancel,6).status);
        assertEquals(409,room.command(Json.object("requestId","old-task-new-command","taskId",old,
            "expectedRevision",room.snapshot().get("revision"),"command","CANCEL","params",Collections.emptyMap()),6).status);
    }
    @Test void cancelledAndTimedOutParserCannotStartAnOldTask() {
        Queue<Runnable> jobs=new ArrayDeque<>();AgentRoom room=new AgentRoom("owner",provider((m,k,b,t)->new JsonTransport.Response(200,parsed(b))),jobs::add);
        room.command(command(room,"PARSE",Json.object("text","完成任务")),0);room.command(command(room,"CANCEL",Collections.emptyMap()),1);
        jobs.remove().run();room.tick(2);assertEquals("CANCELLED",room.snapshot().get("status"));
        room.command(command(room,"RESET",Collections.emptyMap()),3);
        room.command(command(room,"PARSE",Json.object("text","完成任务")),4);room.tick(3000);jobs.remove().run();room.tick(3001);
        assertEquals("ERROR",room.snapshot().get("status"));assertEquals("PROVIDER_TIMEOUT",room.snapshot().get("errorCode"));
        assertEquals(409,room.command(command(room,"CONFIRM",Collections.emptyMap()),3002).status);
    }
    @Test void approvalUsesAuthenticatedOwnerAndOnlyServerTokenCanPurchase() {
        Queue<String> calls=new ArrayDeque<>(Arrays.asList(
            OpenAiGatewayTest.function("move_to","{\"locationId\":\"market\"}"),
            OpenAiGatewayTest.function("request_player_approval","{\"itemId\":\"BERRY\",\"quantity\":1,\"actionType\":\"PURCHASE_BERRY\",\"reason\":\"wrong price\"}")));
        AgentRoom room=new AgentRoom("cookie-owner",provider((m,k,b,t)->{
            if(b.contains("json_schema"))return new JsonTransport.Response(200,parsed(b));
            if(!calls.isEmpty())return new JsonTransport.Response(200,calls.remove());
            Map<String,Object> obs=Json.asObject(Json.asObject(Json.read((String)Json.asObject(Json.read(b)).get("input"))).get("observation"));
            return new JsonTransport.Response(200,OpenAiGatewayTest.function("purchase_item",Json.write(Json.object("itemId","BERRY","quantity",1,"approvalToken",obs.get("approvalToken")))));
        }),Runnable::run);
        start(room);for(int i=2;i<15 && !"WAITING_APPROVAL".equals(room.snapshot().get("status"));i++)room.tick(i);
        Map<String,Object> view=room.snapshot();assertEquals("WAITING_APPROVAL",view.get("status"));
        Map<String,Object> approval=Json.asObject(view.get("approval"));assertEquals("1",approval.get("totalCost"));
        long turn=Long.parseLong((String)Json.asObject(view.get("world")).get("turn"));room.tick(100);
        assertEquals(Long.toString(turn),Json.asObject(room.snapshot().get("world")).get("turn"));
        AgentRoom.Reply approved=room.command(command(room,"APPROVE",Json.object("proposalId",approval.get("proposalId"))),101);
        assertEquals(200,approved.status);assertFalse(Json.write(approved.body).contains("approvalToken"));
        room.tick(102);room.tick(103);assertEquals("4",Json.asObject(room.snapshot().get("world")).get("coins"));
        assertEquals("1",Json.asObject(room.snapshot().get("world")).get("carriedBerry"));assertFalse(Json.write(room.snapshot()).contains("approvalToken"));
    }
    @Test void staleRevisionsUnknownFieldsAndUnavailableProviderCannotStartTask() {
        AgentRoom room=new AgentRoom("owner",ProviderSelection.fromEnvironment(Collections.emptyMap()),Runnable::run);
        assertEquals(409,room.command(command(room,"PARSE",Json.object("text","完成任务")),0).status);
        Map<String,Object> bad=command(room,"CANCEL",Collections.emptyMap());bad.put("ownerId","other");assertEquals(400,room.command(bad,0).status);
        bad=command(room,"RESET",Collections.emptyMap());bad.put("expectedRevision",0);assertEquals(409,room.command(bad,0).status);
    }
    @Test void inputLimitsAreCheckedBeforeConsumingAModelRequest() {
        Queue<Runnable> jobs=new ArrayDeque<>();AgentRoom room=new AgentRoom("owner",provider((m,k,b,t)->new JsonTransport.Response(200,parsed(b))),jobs::add);
        String tooLong=String.join("",Collections.nCopies(1001,"字"));
        assertEquals(400,room.command(command(room,"PARSE",Json.object("text",tooLong)),0).status);assertTrue(jobs.isEmpty());
        Map<String,Object> bad=command(room,"PARSE",Json.object("text","完成任务"));bad.put("expectedRevision","1");
        assertEquals(400,room.command(bad,0).status);assertTrue(jobs.isEmpty());
    }
    @Test void expiredFailureDisablesWorldMovesAndNewRoundRecovers() {
        AgentRoom room=new AgentRoom("expired-owner",provider((m,k,b,t)->new JsonTransport.Response(200,parsed(b))),Runnable::run);
        int deadline=Integer.parseInt(String.valueOf(Json.asObject(room.snapshot().get("world")).get("deadlineTurn")));
        for(int i=0;i<deadline;i++) assertEquals(200,room.command(command(room,"MANUAL",Json.object("action","wait","arguments",Collections.emptyMap())),i).status);
        assertEquals("FAILED",room.snapshot().get("status"));
        assertEquals("QUEST_EXPIRED",room.snapshot().get("errorCode"));
        assertEquals(false,room.snapshot().get("manualAllowed"),"expired world cannot accept movement");
        String task=String.valueOf(room.snapshot().get("taskId"));
        assertEquals(200,room.command(command(room,"RESET",Collections.emptyMap()),deadline+1).status);
        assertEquals("IDLE",room.snapshot().get("status"));assertNotEquals(task,room.snapshot().get("taskId"));
        assertEquals(true,room.snapshot().get("manualAllowed"));
        start(room);
    }
}
