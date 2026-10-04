package game.agent;
import game.agent.action.ActionResult;
import game.agent.runtime.*;
import game.agent.tools.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StructuredTraceTest {
 private AgentLoop loop(AgentTask task,String name,Map<String,Object> args) {
  task.start();GameToolRegistry tools=new GameToolRegistry(r->null);
  tools.register(new ToolDefinition("wait","wait",false,Collections.emptyMap()),r->ActionResult.success("WAITED"));
  AgentLoop loop=new AgentLoop(task,"goal",tools,Runnable::run,c->new ToolRequest("untrusted",name,args),Collections::emptyMap,()->false,100,1000,10,1000);
  loop.configureTrace("player","openai","gpt-6-luna");return loop;
 }
 @Test void actualToolArgumentsAreImmutableAndRedacted() {
  Map<String,Object> args=new LinkedHashMap<>();args.put("approvalToken","do-not-save");args.put("x",2);
  AgentLoop loop=loop(new AgentTask("task"),"unknown",args);loop.tick(0);loop.tick(1);
  Map<String,Object> row=loop.getStepTrace().get(0);
  assertNotNull(row.get("traceId"));assertEquals(1L,row.get("stepNumber"));assertEquals("goal",row.get("goal"));
  assertEquals("unknown",row.get("toolName"));assertEquals("task",row.get("taskId"));assertEquals("UNKNOWN_TOOL",row.get("validationResult"));
  assertEquals("REPLANNING",row.get("taskStateAfter"));assertFalse(row.toString().contains("do-not-save"));assertFalse(row.toString().contains("approvalToken"));
  assertTrue(((Number)row.get("latencyMs")).doubleValue()>=0);assertTrue(((Number)row.get("timestamp")).longValue()>1000000);
  assertThrows(UnsupportedOperationException.class,()->row.put("x",3));assertThrows(UnsupportedOperationException.class,()->((Map)row.get("arguments")).put("x",3));
 }
 @Test void aggregatesRemainCumulativeBeyondRetention() {
  AgentLoop loop=loop(new AgentTask("task"),"wait",Collections.emptyMap());for(int i=0;i<140;i++)loop.tick(i);
  assertEquals(64,loop.getStepTrace().size());assertEquals(70L,loop.getMetrics().get("totalSteps"));assertEquals(0.0,loop.getMetrics().get("invalidCallRate"));
  assertEquals(70L,loop.getMetrics().get("totalProviderCalls"));assertNotNull(loop.getMetrics().get("averageLlmLatencyMs"));
 }
 @Test void providerAndStaleEventsDoNotInflateDenominator() {
  AgentTask task=new AgentTask("task");task.start();
  AgentLoop loop=new AgentLoop(task,"goal",new GameToolRegistry(r->null),Runnable::run,c->{throw new IllegalStateException("sk-secret");},Collections::emptyMap,()->false,100,5,5,5);
  loop.tick(0);loop.tick(1);assertEquals("PROVIDER_UNAVAILABLE",loop.getStepTrace().get(0).get("actionResult"));
  assertEquals(0L,loop.getMetrics().get("totalSteps"));assertFalse(loop.getStepTrace().toString().contains("sk-secret"));
  AgentLoop stale=loop(new AgentTask("stale"),"wait",Collections.emptyMap());stale.tick(0);stale.pause();
  assertEquals("STALE_OPERATION",stale.getStepTrace().get(0).get("actionResult"));assertEquals(0L,stale.getMetrics().get("totalSteps"));
 }
 @Test void completionApprovalAndAvailableUsageAreMeasured() {
  AgentTask task=new AgentTask("task");task.start();GameToolRegistry tools=new GameToolRegistry(r->null);
  tools.register(new ToolDefinition("approval","approval",false,Collections.emptyMap()),r->{task.waitForApproval();return ActionResult.success("APPROVAL_REQUIRED");});
  AgentLoop.DecisionProvider provider=new AgentLoop.DecisionProvider(){
   public ToolRequest decide(AgentLoop.Context c){return new ToolRequest("model-id","approval",Collections.emptyMap());}
   public Map<String,Number> getUsage(){Map<String,Number> m=new HashMap<>();m.put("inputTokens",12);m.put("secret",99);return m;}
  };
  AgentLoop loop=new AgentLoop(task,"goal",tools,Runnable::run,provider,Collections::emptyMap,()->false,100,10,10,10);
  loop.tick(0);loop.tick(1);assertEquals(12L,((Map)loop.getStepTrace().get(0).get("usage")).get("inputTokens"));
  assertFalse(((Map)loop.getStepTrace().get(0).get("usage")).containsKey("secret"));
  task.resolveApproval(true);loop.tick(2);assertEquals(1L,loop.getMetrics().get("approvalCount"));
  task.markCompleted();assertEquals(1.0,loop.getMetrics().get("taskCompletionRate"));assertEquals(1.0,loop.getMetrics().get("averageSteps"));
 }
 @Test void rejectionFollowedByValidCallMeasuresReplanSuccess() {
  AgentTask task=new AgentTask("task");task.start();GameToolRegistry tools=new GameToolRegistry(r->null);
  tools.register(new ToolDefinition("wait","wait",false,Collections.emptyMap()),r->ActionResult.success("WAITED"));
  java.util.concurrent.atomic.AtomicInteger choices=new java.util.concurrent.atomic.AtomicInteger();
  AgentLoop loop=new AgentLoop(task,"goal",tools,Runnable::run,c->new ToolRequest("model",choices.getAndIncrement()==0?"unknown":"wait",Collections.emptyMap()),Collections::emptyMap,()->false,100,10,10,10);
  for(int i=0;i<4;i++)loop.tick(i);
  assertEquals(2L,loop.getMetrics().get("totalSteps"));assertEquals(0.5,loop.getMetrics().get("invalidCallRate"));
  assertEquals(1L,loop.getMetrics().get("replanCount"));assertEquals(1L,loop.getMetrics().get("replanSuccessCount"));
 }
 @Test void restoredCountersContinueAndCannotResetDecisionBudget() {
  AgentLoop original=loop(new AgentTask("old"),"wait",Collections.emptyMap());original.tick(0);original.tick(1);
  AgentLoop restored=loop(new AgentTask("new"),"wait",Collections.emptyMap());restored.restoreProgress(original.getMetrics());restored.tick(2);restored.tick(3);
  assertEquals(2L,restored.getMetrics().get("totalSteps"));assertEquals(2L,restored.getMetrics().get("decisionCount"));assertEquals(2L,restored.getStepTrace().get(0).get("stepNumber"));
  Map<String,Object> exhausted=new HashMap<>(restored.getMetrics());exhausted.put("decisionCount",1000L);
  AgentLoop bounded=loop(new AgentTask("bounded"),"wait",Collections.emptyMap());bounded.restoreProgress(exhausted);
  assertEquals("LOOP_LIMIT",bounded.tick(4).getCode());assertEquals(2L,bounded.getMetrics().get("totalSteps"));
 }
 @Test void usageIsCapturedOnWorkerThreadAndContinuationsAreNotProviderCalls() throws Exception {
  AgentTask task=new AgentTask("task");task.start();GameToolRegistry tools=new GameToolRegistry(r->null);
  java.util.concurrent.atomic.AtomicInteger actions=new java.util.concurrent.atomic.AtomicInteger();
  tools.register(new ToolDefinition("walk","walk",true,Collections.emptyMap()),r->actions.incrementAndGet()<3?ActionResult.of(ActionResult.Status.IN_PROGRESS,"MOVED",Collections.emptyMap()):ActionResult.success("ARRIVED"));
  ThreadLocal<Map<String,Number>> local=ThreadLocal.withInitial(Collections::emptyMap);
  AgentLoop.DecisionProvider provider=new AgentLoop.DecisionProvider(){
   public ToolRequest decide(AgentLoop.Context c){local.set(Collections.singletonMap("inputTokens",20));return new ToolRequest("model","walk",Collections.emptyMap());}
   public Map<String,Number> getUsage(){return local.get();}
  };
  List<Runnable> queued=new ArrayList<>();AgentLoop loop=new AgentLoop(task,"goal",tools,queued::add,provider,Collections::emptyMap,()->false,100,10,10,10);
  loop.tick(0);Thread worker=new Thread(queued.remove(0));worker.start();worker.join();loop.tick(1);loop.tick(2);loop.tick(3);
  assertEquals(20L,((Map)loop.getStepTrace().get(0).get("usage")).get("inputTokens"));
  assertEquals(3L,loop.getMetrics().get("totalSteps"));assertEquals(1L,loop.getMetrics().get("totalProviderCalls"));assertEquals(1L,loop.getMetrics().get("llmLatencySamples"));
 }
}
