package game.agent;

import game.agent.eval.EvaluationRun;
import game.agent.llm.*;
import game.agent.runtime.*;
import game.agent.tools.*;
import game.agent.action.ActionResult;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class LocalAiWorkflowRegressionTest {
    private Map<String,Object> spec(String scenario,long seed,String model){
        return Json.object("id","local-"+model+scenario+seed,"scenario",scenario,"seed",seed,"repetition",1,"model",model,"starter","TREECKO","decisions",100,"wallMillis",180000,"price",Collections.emptyMap());
    }
    @Test void fiveScenariosAcrossTwentySeedsPassBaselineAndDeepSeekProtocolStub(){
        for(int sample=0;sample<20;sample++)for(String scenario:EvaluationRun.SCENARIOS){
            long seed=11+sample*12;
            for(boolean wire:new boolean[]{false,true}){
                EvaluationRun run=new EvaluationRun(spec(scenario,seed,wire?"deepseek-protocol-stub":"baseline"));
                AgentLoop.DecisionProvider scripted=EvaluationRun.baseline();
                AtomicReference<AgentLoop.Context> current=new AtomicReference<>();
                OpenAiGateway gateway=DeepSeekGatewayTest.gateway((model,key,body,timeout)->{
                    Map<String,Object> request=Json.asObject(Json.read(body));
                    assertEquals("deepseek-flash",model);assertEquals("disabled",Json.asObject(request.get("thinking")).get("type"));
                    assertFalse(body.contains("test-key"));assertFalse(request.containsKey("input"));
                    ToolRequest choice=scripted.decide(current.get());
                    return new JsonTransport.Response(200,DeepSeekGatewayTest.action(choice.getName(),Json.write(choice.getArguments())));
                });
                AgentLoop.DecisionProvider provider=wire?new AgentLoop.DecisionProvider(){
                    public ToolRequest decide(AgentLoop.Context c){current.set(c);return gateway.decide(c);}
                    public Map<String,Number> getUsage(){return gateway.getUsage();}
                }:scripted;
                run.resume(provider,Runnable::run,1000);
                for(int i=1;i<1000&&!run.terminal();i++)run.tick(i);
                assertEquals("COMPLETED",run.status(),scenario+seed+" wire="+wire);
                Map<String,Object> report=run.report(true);
                assertTrue(((Number)Json.asObject(report.get("metrics")).get("worldTurns")).longValue()<60);
                if(wire)assertFalse(Json.write(report).contains("test-key"));
            }
        }
    }
    @Test void repeatingObservationPausesBeforeDecisionBudgetAndResumeStartsFreshWindow(){
        AgentTask task=new AgentTask("stalled");task.start();GameToolRegistry tools=new GameToolRegistry(q->null);
        tools.register(new ToolDefinition("observe","observe",false,Collections.emptyMap()),q->ActionResult.success("OBSERVED"));
        Map<String,String> s=new LinkedHashMap<>();s.put("carriedBerry","0");s.put("remainingBerry","3");s.put("deliveredBerry","0");s.put("x","1");s.put("y","1");s.put("visibleBerry","0");
        AgentLoop loop=new AgentLoop(task,"collect",tools,Runnable::run,c->new ToolRequest("observe","observe",Collections.emptyMap()),()->s,()->false,1000,100,10,8).deliveryPriority(()->null);
        ActionResult event=null;for(int i=1;i<20&&loop.getState()!=AgentTask.State.PAUSED;i++)event=loop.tick(i);
        assertEquals("NO_PROGRESS_REQUIRES_HELP",event.getCode());assertEquals(3L,loop.getMetrics().get("decisionCount"));
        loop.resume();assertEquals("DECISION_PENDING",loop.tick(21).getCode());
    }
    @Test void actualLedgerProgressResetsRepeatedSiteDetection(){
        AgentTask task=new AgentTask("progress");task.start();GameToolRegistry tools=new GameToolRegistry(q->null);
        Map<String,String> s=new LinkedHashMap<>();s.put("carriedBerry","0");s.put("remainingBerry","3");s.put("deliveredBerry","0");s.put("x","1");s.put("y","1");s.put("visibleBerry","0");
        tools.register(new ToolDefinition("observe","observe",false,Collections.emptyMap()),q->ActionResult.success("OBSERVED"));
        AgentLoop loop=new AgentLoop(task,"collect",tools,Runnable::run,c->new ToolRequest("observe","observe",Collections.emptyMap()),()->s,()->false,1000,100,10,8).deliveryPriority(()->null);
        for(int i=1;i<=6;i++)loop.tick(i);
        s.put("remainingBerry","2");s.put("deliveredBerry","1");
        assertEquals("DECISION_PENDING",loop.tick(7).getCode());assertEquals(AgentTask.State.RUNNING,loop.getState());
    }
    @Test void expiringObservationStopsWithoutInternalLoopError(){
        AgentTask task=new AgentTask("expiry");task.start();GameToolRegistry tools=new GameToolRegistry(q->null);
        AgentLoop loop=new AgentLoop(task,"collect",tools,Runnable::run,c->{fail("No model call after expiry");return null;},Collections::emptyMap,()->false,1000,10,10,8)
            .deliveryPriority(()->{task.fail();return null;});
        assertEquals("QUEST_INACTIVE",loop.tick(1).getCode());assertEquals(AgentTask.State.FAILED,loop.getState());
    }
    @Test void historicalCheckpointKeepsPolicyHistoryInsteadOfBeingRelabeledAsNewBenchmark(){
        EvaluationRun run=new EvaluationRun(spec("shared",11,"baseline"));
        assertEquals(Collections.singletonList(EvaluationRun.EXECUTION_POLICY_VERSION),run.report(false).get("executionPolicyVersions"));
        Map<String,Object> saved=run.checkpoint();Json.asObject(saved.get("spec")).remove("executionPolicyVersions");
        EvaluationRun legacy=EvaluationRun.restore(saved);
        assertEquals(Collections.singletonList("legacy-unversioned"),legacy.report(false).get("executionPolicyVersions"));
        legacy.resume(EvaluationRun.baseline(),Runnable::run,1000);
        assertEquals(Arrays.asList("legacy-unversioned",EvaluationRun.EXECUTION_POLICY_VERSION),legacy.report(false).get("executionPolicyVersions"));
    }
}
