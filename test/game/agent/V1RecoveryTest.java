package game.agent;
import game.agent.demo.GeminiQuestScenario;
import game.agent.web.*;
import game.agent.persistence.*;
import game.agent.llm.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class V1RecoveryTest {
    @TempDir Path directory;
    @Test void checkpointRoundtripKeepsRealResourcesAndFreshTaskIdentity(){
        GeminiQuestScenario original=new GeminiQuestScenario("owner");
        original.manual("move",Json.object("direction","E"));original.advanceTurn();
        original.manual("move",Json.object("direction","E"));original.advanceTurn();
        assertEquals("PICKED_UP",original.manual("pickup",Json.object("quantity",2)).getCode());original.advanceTurn();
        Map<String,Object> saved=original.exportState();
        GeminiQuestScenario restored=GeminiQuestScenario.restore("owner",Json.asObject(Json.read(Json.write(saved))));
        assertNotEquals(original.getTaskId(),restored.getTaskId());assertEquals("2",restored.getSession().observation().get("carriedBerry"));
        assertEquals("0",restored.getSession().observation().get("visibleBerry"));assertEquals(3,restored.getSession().getTurn());
        assertFalse(Json.write(saved).contains("approvalToken"));
    }
    @Test void restoredActiveTaskRequiresExplicitResumeAndRejectsOldCommands(){
        FileWorldStore store=new FileWorldStore(directory);
        ProviderSelection provider=AgentRoomTest.provider((m,k,b,t)->new JsonTransport.Response(200,b.contains("json_schema")?AgentRoomTest.parsed(b):OpenAiGatewayTest.function("wait","{}")));
        AgentRoom room=new AgentRoom("owner",provider,Runnable::run,false,store);
        AgentRoomTest.start(room);Map<String,Object> stale=AgentRoomTest.command(room,"CANCEL",Collections.emptyMap());
        room.tick(2);room.tick(3);
        AgentRoom restored=new AgentRoom("owner",provider,Runnable::run,false,store);
        assertEquals("PAUSED",restored.snapshot().get("status"));
        assertEquals(409,restored.command(stale,4).status);
        assertEquals(Json.asObject(room.snapshot().get("world")).get("turn"),Json.asObject(restored.snapshot().get("world")).get("turn"));
        assertNull(restored.snapshot().get("approval"));assertTrue((Boolean)restored.snapshot().get("recovered"));
    }
    @Test void storageFailureFreezesFurtherActionsUntilReloadOfDurableState(){
        FileWorldStore files=new FileWorldStore(directory);boolean[] fail={false};
        WorldStore store=new WorldStore(){public void save(String owner,Map<String,Object> cp){if(fail[0])throw new IllegalStateException("failure");files.save(owner,cp);}public Map<String,Object> load(String owner){return files.load(owner);}public void close(){files.close();}};
        AgentRoom room=new AgentRoom("owner",ProviderSelection.fromEnvironment(Collections.emptyMap()),Runnable::run,false,store);
        Map<String,Object> move=AgentRoomTest.command(room,"MANUAL",Json.object("action","move","arguments",Json.object("direction","E")));
        fail[0]=true;assertEquals(503,room.command(move,1).status);assertEquals("STORAGE_ERROR",room.snapshot().get("status"));
        assertEquals(503,room.command(AgentRoomTest.command(room,"MANUAL",Json.object("action","wait","arguments",Collections.emptyMap())),2).status);
        fail[0]=false;assertEquals(200,room.command(AgentRoomTest.command(room,"CANCEL",Collections.emptyMap()),3).status);
        assertEquals(200,room.command(AgentRoomTest.command(room,"RECOVER",Collections.emptyMap()),4).status);
        assertEquals("0",Json.asObject(room.snapshot().get("world")).get("x"));assertEquals("0",Json.asObject(room.snapshot().get("world")).get("turn"));
    }
    @Test void taskBudgetAndStatisticsSurviveCheckpointRecovery(){
        FileWorldStore store=new FileWorldStore(directory);
        ProviderSelection provider=AgentRoomTest.provider((m,k,b,t)->new JsonTransport.Response(200,b.contains("json_schema")?AgentRoomTest.parsed(b):OpenAiGatewayTest.function("wait","{}")));
        AgentRoom room=new AgentRoom("owner",provider,Runnable::run,false,store);AgentRoomTest.start(room);room.tick(2);room.tick(3);
        Map<String,Object> metrics=Json.asObject(room.snapshot().get("metrics"));AgentRoom recovered=new AgentRoom("owner",provider,Runnable::run,false,store);
        assertEquals(((Number)metrics.get("totalSteps")).longValue(),((Number)Json.asObject(recovered.snapshot().get("metrics")).get("totalSteps")).longValue());
        assertEquals(((Number)metrics.get("decisionCount")).longValue(),((Number)Json.asObject(recovered.snapshot().get("metrics")).get("decisionCount")).longValue());
    }
    @Test void providerUnavailableDoesNotAdvanceWorldAndManualGameplayRemainsAvailable(){
        ProviderSelection provider=AgentRoomTest.provider((m,k,b,t)->new JsonTransport.Response(b.contains("json_schema")?200:429,b.contains("json_schema")?AgentRoomTest.parsed(b):"{}"));
        AgentRoom room=new AgentRoom("owner",provider,Runnable::run,false,null);AgentRoomTest.start(room);room.tick(2);room.tick(3);
        assertEquals("PROVIDER_UNAVAILABLE",room.snapshot().get("status"));assertEquals("0",Json.asObject(room.snapshot().get("world")).get("turn"));
        assertEquals(200,room.command(AgentRoomTest.command(room,"MANUAL",Json.object("action","wait","arguments",Collections.emptyMap())),4).status);
        assertEquals("1",Json.asObject(room.snapshot().get("world")).get("turn"));
    }
    @Test void manualTakeoverOutsideRestrictedAreaRequiresReturnBeforeAgentResume(){
        ProviderSelection provider=new ProviderSelection("offline","area-fixture",1000,
            c->new game.agent.tools.ToolRequest("choice","wait",Collections.emptyMap()),
            (text,quest)->TaskIntent.of(quest,EnumSet.of(TaskIntent.Constraint.AREA_RESTRICTED),"ORCHARD_AREA",null));
        FileWorldStore store=new FileWorldStore(directory);
        AgentRoom room=new AgentRoom("owner",provider,Runnable::run,false,store);AgentRoomTest.start(room);
        assertEquals(200,room.command(AgentRoomTest.command(room,"PAUSE",Collections.emptyMap()),2).status);
        for(int i=0;i<3;i++)assertEquals(200,room.command(AgentRoomTest.command(room,"MANUAL",Json.object("action","move","arguments",Json.object("direction","E"))),3+i).status);
        room=new AgentRoom("owner",provider,Runnable::run,false,store);assertEquals("PAUSED",room.snapshot().get("status"));
        assertEquals(409,room.command(AgentRoomTest.command(room,"RESUME",Collections.emptyMap()),6).status);
        assertEquals("PAUSED",room.snapshot().get("status"));
        assertEquals(200,room.command(AgentRoomTest.command(room,"MANUAL",Json.object("action","move","arguments",Json.object("direction","W"))),7).status);
        assertEquals(200,room.command(AgentRoomTest.command(room,"RESUME",Collections.emptyMap()),8).status);
    }
    @Test void restartDiscardsPendingApprovalAndRejectsItsReplay(){
        Queue<String> calls=new ArrayDeque<>(Arrays.asList(OpenAiGatewayTest.function("move_to","{\"locationId\":\"market\"}"),OpenAiGatewayTest.function("request_player_approval","{\"itemId\":\"BERRY\",\"quantity\":1,\"actionType\":\"PURCHASE_BERRY\",\"reason\":\"missing berry\"}")));
        ProviderSelection provider=AgentRoomTest.provider((m,k,b,t)->new JsonTransport.Response(200,b.contains("json_schema")?AgentRoomTest.parsed(b):calls.remove()));
        FileWorldStore store=new FileWorldStore(directory);AgentRoom room=new AgentRoom("owner",provider,Runnable::run,false,store);AgentRoomTest.start(room);
        for(int i=2;i<15 && !"WAITING_APPROVAL".equals(room.snapshot().get("status"));i++)room.tick(i);
        assertEquals("WAITING_APPROVAL",room.snapshot().get("status"));
        Object proposal=Json.asObject(room.snapshot().get("approval")).get("proposalId");
        Map<String,Object> oldCommand=AgentRoomTest.command(room,"APPROVE",Json.object("proposalId",proposal));
        AgentRoom restored=new AgentRoom("owner",provider,Runnable::run,false,store);
        assertEquals("PAUSED",restored.snapshot().get("status"));assertNull(restored.snapshot().get("approval"));
        assertEquals(409,restored.command(oldCommand,20).status);
        assertEquals(409,restored.command(AgentRoomTest.command(restored,"APPROVE",Json.object("proposalId",proposal)),21).status);
        assertEquals("5",Json.asObject(restored.snapshot().get("world")).get("coins"));
    }
}
