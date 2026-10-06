package game.agent;

import game.agent.quest.BerryCoordinationAdvice;
import game.agent.runtime.*;
import game.agent.tools.*;
import game.agent.action.ActionResult;
import game.agent.llm.Json;
import game.agent.eval.EvaluationRun;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryPriorityTest {
    private Map<String,String> state(int carried,int remaining,boolean near,boolean shared) {
        Map<String,String> s=new LinkedHashMap<>();s.put("questId","q");s.put("questStatus","ACTIVE");
        s.put("requiredBerry","3");s.put("remainingBerry",String.valueOf(remaining));s.put("carriedBerry",String.valueOf(carried));
        s.put("nearProfessor",String.valueOf(near));s.put("sharedQuest",String.valueOf(shared));s.put("visibleBerry","0");s.put("x","0");s.put("y","0");return s;
    }
    @Test void localPickupUsesOnlyVisibleStockAndOutstandingNeed(){Map<String,String> s=state(1,2,false,true);s.put("visibleBerry","3");ToolRequest q=BerryCoordinationAdvice.localPickupRecovery(s);assertNotNull(q);assertEquals("pickup",q.getName());assertEquals("BERRY",q.getArguments().get("itemId"));assertEquals(1,q.getArguments().get("quantity"));assertNull(BerryCoordinationAdvice.deliveryAction(s));s.put("sharedQuest","false");assertNotNull(BerryCoordinationAdvice.localPickupRecovery(s));s.put("visibleBerry","0");assertNull(BerryCoordinationAdvice.localPickupRecovery(s));}
    @Test void stalledSharedObservationRecoversWithoutPreemptingNormalDecision() {
        Map<String,String> s=state(0,2,false,false);s.put("visibleBerry","2");AtomicInteger calls=new AtomicInteger(),picks=new AtomicInteger();
        AgentTask task=new AgentTask("stalled");task.start();GameToolRegistry tools=new GameToolRegistry(q->null);
        tools.register(new ToolDefinition("observe_quest","observe",false,Collections.emptyMap()),q->ActionResult.success("OBSERVED"));
        Map<String,ToolParameter> params=new LinkedHashMap<>();params.put("itemId",ToolParameter.string());params.put("quantity",ToolParameter.integer(1,20));
        tools.register(new ToolDefinition("pickup","pick",true,params),q->{picks.incrementAndGet();s.put("carriedBerry","2");s.put("visibleBerry","0");return ActionResult.success("PICKED_UP");});
        AgentLoop loop=new AgentLoop(task,"deliver",tools,Runnable::run,c->{calls.incrementAndGet();return new ToolRequest("obs","observe_quest",Collections.emptyMap());},()->s,()->false,1000,10,10,3).deliveryPriority(()->BerryCoordinationAdvice.deliveryAction(s));
        loop.tick(1);assertEquals(1,calls.get());assertEquals(0,picks.get());
        for(int i=2;i<=7;i++)loop.tick(i);
        assertEquals(3,calls.get());assertEquals(1,picks.get());assertEquals("ENGINE_LOCAL_PICKUP_PRIORITY",loop.getStepTrace().get(3).get("executionSource"));
    }
    @Test void sharedInventoryCoversRemainingWithoutRecollectingOriginalTotal() {
        Map<String,String> s=state(2,2,false,true);BerryCoordinationAdvice.enrich(s,1,1);
        assertEquals("0",s.get("neededPickupBerry"));assertEquals("true",s.get("deliveryReady"));
        assertEquals("laboratory",BerryCoordinationAdvice.deliveryAction(s).getArguments().get("locationId"));
        s.put("nearProfessor","true");assertEquals("deliver",BerryCoordinationAdvice.deliveryAction(s).getName());
    }
    @Test void partialDeliveryOnlyAllowedForSharedQuestAndNoActionAfterCompletion() {
        assertNull(BerryCoordinationAdvice.deliveryAction(state(1,3,true,false)));
        assertEquals("deliver",BerryCoordinationAdvice.deliveryAction(state(1,3,true,true)).getName());
        Map<String,String> s=state(3,0,true,true);assertNull(BerryCoordinationAdvice.deliveryAction(s));
        s.put("questStatus","EXPIRED");assertNull(BerryCoordinationAdvice.deliveryAction(s));
    }
    @Test void repeatPathFailurePausesWithoutBurningProviderCallsAndCanResume() {
        AgentTask task=new AgentTask("blocked");task.start();AtomicInteger calls=new AtomicInteger();
        GameToolRegistry tools=new GameToolRegistry(q->null);
        tools.register(new ToolDefinition("move_to","move",true,Collections.singletonMap("locationId",ToolParameter.string())),q->ActionResult.rejected("NO_PATH"));
        AgentLoop loop=new AgentLoop(task,"deliver",tools,Runnable::run,c->{calls.incrementAndGet();return null;},Collections::emptyMap,()->false,1000,10,10,8)
                .deliveryPriority(()->new ToolRequest("priority","move_to",Json.object("locationId","laboratory")));
        loop.tick(1);loop.tick(2);assertEquals("NAVIGATION_REQUIRES_HELP",loop.tick(3).getCode());
        assertEquals(AgentTask.State.PAUSED,loop.getState());assertEquals(0,calls.get());
        loop.tick(4);assertEquals(3L,loop.getMetrics().get("navigationFailureCount"));
        loop.resume();assertEquals("NO_PATH",loop.tick(5).getCode());assertEquals(AgentTask.State.REPLANNING,loop.getState());
    }
    @Test void pendingModelChoiceRechecksCurrentInventoryBeforeCommit() {
        AgentTask task=new AgentTask("race");task.start();Map<String,String> s=state(0,2,true,true);
        List<Runnable> queue=new ArrayList<>();AtomicInteger picked=new AtomicInteger(),delivered=new AtomicInteger();
        GameToolRegistry tools=new GameToolRegistry(q->null);
        tools.register(new ToolDefinition("pickup","pick",true,Collections.emptyMap()),q->{picked.incrementAndGet();return ActionResult.success("PICKED_UP");});
        Map<String,ToolParameter> params=new LinkedHashMap<>();params.put("questId",ToolParameter.string());params.put("targetNpcId",ToolParameter.string());
        tools.register(new ToolDefinition("deliver","deliver",true,params),q->{delivered.incrementAndGet();return ActionResult.success("DELIVERED");});
        AgentLoop loop=new AgentLoop(task,"deliver",tools,queue::add,c->new ToolRequest("model","pickup",Collections.emptyMap()),()->s,()->false,1000,10,10,8)
                .deliveryPriority(()->BerryCoordinationAdvice.deliveryAction(s));
        loop.tick(1);s.put("carriedBerry","2");queue.remove(0).run();loop.tick(2);
        assertEquals(0,picked.get());assertEquals(1,delivered.get());
        assertEquals("ENGINE_DELIVERY_PRIORITY",loop.getStepTrace().get(0).get("executionSource"));
        loop.pause();loop.tick(3);assertEquals(1,delivered.get());
    }
    @Test void trustedPriorityCannotRetryForeverWhenAuthoritativePolicyRejectsIt(){
        AgentTask task=new AgentTask("restricted");task.start();AtomicInteger mutations=new AtomicInteger();
        GameToolRegistry tools=new GameToolRegistry(q->"AREA_RESTRICTED");
        tools.register(new ToolDefinition("move_to","move",true,Collections.singletonMap("locationId",ToolParameter.string())),q->{mutations.incrementAndGet();return ActionResult.success("MOVED");});
        AgentLoop loop=new AgentLoop(task,"deliver",tools,Runnable::run,c->{fail("No model request");return null;},Collections::emptyMap,()->false,1000,10,10,2)
            .deliveryPriority(()->new ToolRequest("priority","move_to",Json.object("locationId","laboratory")));
        assertEquals("AREA_RESTRICTED",loop.tick(1).getCode());assertEquals("AREA_RESTRICTED",loop.tick(2).getCode());
        assertEquals("LOOP_LIMIT",loop.tick(3).getCode());assertEquals(AgentTask.State.FAILED,loop.getState());assertEquals(0,mutations.get());
    }
    @Test void sharedAndCompetitionFinishEvenWhenModelNeverChoosesDelivery() {
        for(String scenario:Arrays.asList("shared","competition"))for(long seed:new long[]{11,23,35}){
            Map<String,Object> spec=Json.object("id","guard-"+scenario+seed,"scenario",scenario,"seed",seed,"repetition",1,"model","baseline","starter","TREECKO","decisions",100,"wallMillis",180000,"price",Collections.emptyMap());
            EvaluationRun run=new EvaluationRun(spec);
            run.resume(c->{Map<String,String> s=c.getObservation();int visible=Integer.parseInt(s.get("visibleBerry"));
                if(visible>0)return new ToolRequest("pick","pickup",Json.object("itemId","BERRY","quantity",visible));
                return new ToolRequest("wander","move_to",Json.object("locationId", "31".equals(s.get("x"))&&"10".equals(s.get("y"))?"alternative":"orchard"));
            },Runnable::run,1000);
            for(int i=1;i<500&&"RUNNING".equals(run.status());i++)run.tick(i);
            assertEquals("COMPLETED",run.status(),scenario+seed+Json.write(run.report(false)));
        }
    }
    @Test void sharedVisibleStockStallsRecoverInsideThirtyDecisionBudget() {
        for(String scenario:Arrays.asList("shared","competition"))for(long seed:new long[]{11,23,37}){
            Map<String,Object> spec=Json.object("id","guard-"+scenario+seed,"scenario",scenario,"seed",seed,"repetition",1,"model","baseline","starter","TREECKO","decisions",30,"wallMillis",180000,"price",Collections.emptyMap());
            EvaluationRun run=new EvaluationRun(spec);
            run.resume(c->{Map<String,String> s=c.getObservation();int visible=Integer.parseInt(s.get("visibleBerry"));
                if(visible>0)return new ToolRequest("observe","observe_quest",Collections.emptyMap());
                return new ToolRequest("wander","move_to",Json.object("locationId", "31".equals(s.get("x"))&&"10".equals(s.get("y"))?"alternative":"orchard"));
            },Runnable::run,1000);
            for(int i=1;i<500&&"RUNNING".equals(run.status());i++)run.tick(i);
            assertEquals("COMPLETED",run.status(),scenario+seed+Json.write(run.report(false)));
        }
    }
}
