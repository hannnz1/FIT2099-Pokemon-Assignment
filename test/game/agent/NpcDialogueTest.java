package game.agent;

import game.agent.action.*;
import game.agent.demo.*;
import game.agent.llm.*;
import game.agent.runtime.*;
import game.agent.tools.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class NpcDialogueTest {
    static ToolRequest talk(String target) {return new ToolRequest(UUID.randomUUID().toString(),"talk_to",Json.object("targetNpcId",target,"message","哪里有树果？"));}
    @Test void treeckoHistoryIsAnActualOldObservationAndDoesNotChangeInventory() {
        GeminiQuestScenario scene=new GeminiQuestScenario();AtomicReference<AgentLoop.Context> context=new AtomicReference<>();
        LlmGateway gateway=c->{context.set(c);return talk("treecko");};
        TaskIntent intent=OpenAiGatewayTest.gateway((m,k,b,t)->new JsonTransport.Response(200,OpenAiGatewayTest.task(scene.getSession().getQuestId()))).parse("完成树果任务",scene.getSession().getQuestId());
        AgentLoop loop=scene.start(intent,gateway,Runnable::run);loop.tick(0);ActionResult reply=loop.tick(1);
        assertEquals("NPC_DIALOGUE",reply.getCode());assertEquals("treecko",reply.getData().get("speakerId"));
        List<Object> memories=Json.asArray(Json.read(reply.getData().get("memories")));assertEquals(1,memories.size());
        Map<String,Object> clue=Json.asObject(memories.get(0));assertEquals("3",clue.get("quantity").toString());
        assertEquals("SELF_OBSERVATION",clue.get("source"));assertEquals("0",clue.get("occurredAt").toString());
        assertEquals("0",scene.getSession().observation().get("carriedBerry"));assertEquals(0,scene.getDelivered());
        assertTrue(context.get().getObservation().containsKey("nearTreecko"));
    }
    @Test void outOfReachUnknownNpcAndPausedTasksCannotTalk() {
        GeminiQuestScenario scene=new GeminiQuestScenario();Queue<ToolRequest> choices=new ArrayDeque<>(Arrays.asList(talk("merchant"),talk("other-room-npc"),talk("treecko")));
        TaskIntent intent=OpenAiGatewayTest.gateway((m,k,b,t)->new JsonTransport.Response(200,OpenAiGatewayTest.task(scene.getSession().getQuestId()))).parse("完成任务",scene.getSession().getQuestId());
        AgentLoop loop=scene.start(intent,c->choices.remove(),Runnable::run);
        loop.tick(0);assertEquals("OUT_OF_REACH",loop.tick(1).getCode());loop.tick(2);assertEquals("UNKNOWN_NPC",loop.tick(3).getCode());
        loop.tick(4);loop.pause();assertEquals("TASK_INACTIVE",loop.tick(5).getCode());assertEquals("0",scene.getSession().observation().get("carriedBerry"));
    }
    @Test void actualQuestCanUseStaleClueFailPickupDenyPurchaseAndDeliverAlternative() {
        GeminiQuestScenario scene=new GeminiQuestScenario("owner");
        Queue<ToolRequest> choices=new ArrayDeque<>(Arrays.asList(talk("treecko"),move("orchard"),pick(3),pick(2),move("market"),talk("merchant"),
            tool("request_player_approval",Json.object("itemId","BERRY","quantity",1,"actionType","PURCHASE_BERRY","reason","one needed")),
            move("alternative"),pick(1),move("laboratory"),talk("professor"),tool("deliver",Json.object("questId",scene.getSession().getQuestId(),"targetNpcId","professor"))));
        TaskIntent intent=OpenAiGatewayTest.gateway((m,k,b,t)->new JsonTransport.Response(200,OpenAiGatewayTest.task(scene.getSession().getQuestId(),"NO_SPENDING"))).parse("完成任务，不花金币",scene.getSession().getQuestId());
        AgentLoop loop=scene.start(intent,c->choices.remove(),Runnable::run);boolean denied=false,failed=false;
        for(int t=0;t<100 && loop.getState()!=AgentTask.State.COMPLETED;t++) {
            ActionResult r=loop.tick(t);if(r.getCode().equals("RESOURCE_NOT_AVAILABLE")) {failed=true;assertEquals(AgentTask.State.REPLANNING,loop.getState());}
            if(!r.getCode().equals("DECISION_PENDING")&&!r.getCode().equals("TASK_INACTIVE"))scene.advanceTurn();
            if(loop.getState()==AgentTask.State.WAITING_APPROVAL) {
                assertTrue(failed);String proposal=scene.getSession().pendingApproval("owner").get("proposalId");
                assertEquals(ActionResult.Status.SUCCESS,scene.getSession().resolveApproval("owner",proposal,false).getStatus());denied=true;
            }
        }
        assertTrue(failed);assertTrue(denied);assertEquals(AgentTask.State.COMPLETED,loop.getState());assertEquals(3,scene.getDelivered());assertEquals(5,scene.getSession().getBalance());
        assertFalse(Json.write(scene.getPublicDialogue()).contains("approvalToken"));
    }
    static ToolRequest tool(String name,Map<String,Object> args){return new ToolRequest(UUID.randomUUID().toString(),name,args);}
    static ToolRequest move(String location){return tool("move_to",Json.object("locationId",location));}
    static ToolRequest pick(int n){return tool("pickup",Json.object("itemId","BERRY","quantity",n));}
}
