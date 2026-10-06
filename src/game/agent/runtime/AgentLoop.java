package game.agent.runtime;

import game.agent.action.ActionResult;
import game.agent.tools.*;
import game.agent.llm.ProviderException;
import game.agent.trace.StepTrace;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/**
 * Java adaptation of Mindcraft self_prompter.js and action_manager.js.
 * Copyright (c) 2024 Kolby Nottingham. MIT; see third_party/mindcraft/LICENSE.
 * Changes: bounded decisions/actions, cooperative per-turn continuation, no generated
 * code or process killing. Call tick and lifecycle methods on the world thread.
 * Providers receive immutable snapshots; they must never access the mutable world.
 */
public final class AgentLoop {
    public interface DecisionProvider {
        ToolRequest decide(Context context);
        default Map<String,Number> getUsage() { return Collections.emptyMap(); }
    }
    public static final class Context {
        private final String goal;
        private final Map<String,String> observation;
        private final List<ToolDefinition> tools;
        private final ActionResult previous;
        private Context(String goal,Map<String,String> observation,List<ToolDefinition> tools,ActionResult previous) {
            this.goal=goal; this.observation=Collections.unmodifiableMap(new LinkedHashMap<>(observation));
            this.tools=tools; this.previous=previous;
        }
        public String getGoal() { return goal; }
        public Map<String,String> getObservation() { return observation; }
        public List<ToolDefinition> getTools() { return tools; }
        public ActionResult getPreviousResult() { return previous; }
        public static Context snapshot(String goal,Map<String,String> observation,List<ToolDefinition> tools,ActionResult previous) {
            return new Context(goal,observation,Collections.unmodifiableList(new ArrayList<>(tools)),previous);
        }
    }
    private static final class Decision {
        final ToolRequest request; final RuntimeException error; final Map<String,Number> usage; final double latency;
        Decision(ToolRequest request,RuntimeException error,Map<String,Number> usage,double latency) {
            this.request=request;this.error=error;this.usage=usage;this.latency=latency;
        }
    }
    private final AgentTask task;
    private final String goal;
    private final GameToolRegistry tools;
    private final Executor executor;
    private final DecisionProvider provider;
    private final Supplier<Map<String,String>> observation;
    private final BooleanSupplier completed;
    private final long timeout;
    private final int decisionLimit, stepLimit, failureLimit;
    private final String session=UUID.randomUUID().toString();
    private final Deque<ActionResult> trace=new ArrayDeque<>();
    private CompletableFuture<Decision> pending;
    private AgentTask.Operation operation;
    private ToolRequest continuation;
    private int decisions,steps,failures,sequence;
    private long revision;
    private ActionResult last;
    private final StepTrace structured=new StepTrace();
    private long providerStarted;
    private double providerLatency;
    private Map<String,Number> providerUsage=Collections.emptyMap();
    private AgentTask.State eventBefore;
    private Supplier<ToolRequest> deliveryPriority;
    private int navigationFailures;
    private long deliveryPriorityActions;
    private String progressKey;
    private final Map<String,Integer> repeatedSites=new LinkedHashMap<>();

    /** Opt-in trusted berry delivery guard; all actions still pass normal tool validation. */
    public AgentLoop deliveryPriority(Supplier<ToolRequest> priority) {
        if(decisions!=0||pending!=null)throw new IllegalStateException("Loop already started");
        deliveryPriority=Objects.requireNonNull(priority);return this;
    }

    public AgentLoop(AgentTask task,String goal,GameToolRegistry tools,Executor executor,DecisionProvider provider,
                     Supplier<Map<String,String>> observation,BooleanSupplier completed,long timeout,
                     int decisionLimit,int stepLimit,int failureLimit) {
        this.task=Objects.requireNonNull(task); this.goal=Objects.requireNonNull(goal);
        this.tools=Objects.requireNonNull(tools); this.executor=Objects.requireNonNull(executor);
        this.provider=Objects.requireNonNull(provider); this.observation=Objects.requireNonNull(observation);
        this.completed=Objects.requireNonNull(completed);
        if (goal.trim().isEmpty() || timeout<=0 || decisionLimit<=0 || stepLimit<=0 || failureLimit<=0)
            throw new IllegalArgumentException("Invalid loop limits");
        this.timeout=timeout; this.decisionLimit=decisionLimit; this.stepLimit=stepLimit; this.failureLimit=failureLimit;
        revision=task.getRevision();
        structured.configureGoal(goal);
    }
    /** Does not await unfinished decisions; executes at most one tool per turn.
     * Production model executors must run asynchronously (inline executors block).
     */
    public ActionResult tick(long now) {
        eventBefore=task.getState(); structured.observe(eventBefore);
        if (revision!=task.getRevision()) { clearWork(); revision=task.getRevision(); }
        if (!runnable()) return ActionResult.rejected("TASK_INACTIVE");
        try {
            if (completed.getAsBoolean()) { clearWork(); task.markCompleted(); return record(ActionResult.success("GOAL_COMPLETED")); }
            if (task.expire(now)) {
                AgentTask.Operation expired=operation;
                clearWork(); revision=task.getRevision();
                return recordEvent(ActionResult.of(ActionResult.Status.TIMED_OUT,"OPERATION_TIMEOUT",Collections.emptyMap()),expired);
            }
            if (continuation!=null) {
                if (steps>=stepLimit) {
                    continuation=null; failures++;
                    return record(ActionResult.of(ActionResult.Status.TIMED_OUT,"ACTION_STEP_LIMIT",Collections.emptyMap()));
                }
                return execute(continuation,task.beginOperation(now,timeout),now);
            }
            if (pending!=null) {
                if (!pending.isDone()) return ActionResult.of(ActionResult.Status.IN_PROGRESS,"DECISION_PENDING",Collections.emptyMap());
                ToolRequest request;
                try {
                    Decision decision=pending.join();providerLatency=decision.latency;providerUsage=decision.usage;
                    structured.providerCompleted(decision.latency);
                    if(decision.error!=null) throw new CompletionException(decision.error);
                    request=decision.request;
                }
                catch (CompletionException error) {
                    pending=null;
                    Throwable cause=error.getCause();
                    if(cause instanceof ProviderException && ((ProviderException)cause).getCode()==ProviderException.Code.INVALID_TOOL_ARGUMENTS){
                        failures++;
                        return record(task.commit(operation,now,()->ActionResult.rejected("INVALID_TOOL_ARGUMENTS")));
                    }
                    return record(task.providerFailed(operation,cause instanceof ProviderException?((ProviderException)cause).getCode():null));
                }
                catch (CancellationException error) { request=null; }
                pending=null;
                if (request==null) return record(task.providerFailed(operation));
                steps=0;
                return execute(request,operation,now);
            }
            if(failures>=failureLimit){task.fail();return record(ActionResult.failed("LOOP_LIMIT"));}
            if(deliveryPriority!=null){
                ToolRequest priority=deliveryPriority.get();
                if(!runnable()){clearWork();return record(ActionResult.failed("QUEST_INACTIVE"));}
                if(priority!=null){steps=0;return execute(priority,task.beginOperation(now,timeout),now);}
            }
            if (decisions>=decisionLimit || failures>=failureLimit) {
                task.fail(); return record(ActionResult.failed("LOOP_LIMIT"));
            }
            Map<String,String> current=Objects.requireNonNull(observation.get());
            // Observation may expire the authoritative quest. Do not begin another operation.
            if(!runnable())return record(ActionResult.failed("QUEST_INACTIVE"));
            if(deliveryPriority!=null&&repeatedBerrySite(current)){
                ToolRequest recovery=game.agent.quest.BerryCoordinationAdvice.localPickupRecovery(current);
                if(recovery!=null){steps=0;deliveryPriorityActions++;return execute(recovery,task.beginOperation(now,timeout),now,true);}
                task.pause();return record(ActionResult.of(ActionResult.Status.INTERRUPTED,"NO_PROGRESS_REQUIRES_HELP",Collections.emptyMap()));
            }
            Context context=new Context(goal,current,tools.definitions(),last);
            operation=task.beginOperation(now,timeout); decisions++;
            providerStarted=System.nanoTime(); providerLatency=0; providerUsage=Collections.emptyMap();
            structured.providerStarted();
            try { pending=CompletableFuture.supplyAsync(()->decide(context),executor); }
            catch (RejectedExecutionException error) { return record(task.providerFailed(operation)); }
            return ActionResult.of(ActionResult.Status.IN_PROGRESS,"DECISION_PENDING",Collections.emptyMap());
        } catch (RuntimeException error) {
            clearWork(); if (runnable()) task.fail(); return record(ActionResult.failed("LOOP_ERROR"));
        }
    }
    /** Check model decision boundaries, never intermediate route steps. Inventory/ledger
     * progress resets the window; distant resource stock is not consulted. */
    private boolean repeatedBerrySite(Map<String,String> state){
        if(!state.containsKey("carriedBerry")||!state.containsKey("x")||!state.containsKey("y"))return false;
        // A cooperating player's observed movement/inventory can release resources or a
        // choke point. Do not mistake a changing cooperative world for a dead loop.
        String key=Arrays.asList(state.get("carriedBerry"),state.get("remainingBerry"),state.get("deliveredBerry"),state.get("coins"),state.get("playerState")).toString();
        if(!key.equals(progressKey)){progressKey=key;repeatedSites.clear();}
        String site=Arrays.asList(state.get("x"),state.get("y"),state.get("visibleBerry"),state.get("merchantStock"),state.get("approvalOutcome")).toString();
        if(repeatedSites.size()>=64&&!repeatedSites.containsKey(site))repeatedSites.remove(repeatedSites.keySet().iterator().next());
        int visits=repeatedSites.getOrDefault(site,0)+1;repeatedSites.put(site,visits);return visits>=4;
    }
    private ActionResult execute(ToolRequest choice,AgentTask.Operation op,long now) { return execute(choice,op,now,false); }
    private ActionResult execute(ToolRequest choice,AgentTask.Operation op,long now,boolean trustedDelivery) {
        if(deliveryPriority!=null){
            ToolRequest priority=deliveryPriority.get();
            // Preserve unknown/malformed tool rejection; never hide a protocol error.
            boolean valid=false;for(ToolDefinition d:tools.definitions())if(d.getName().equals(choice.getName())&&d.accepts(choice.getArguments()))valid=true;
            if(priority!=null&&valid){choice=priority;deliveryPriorityActions++;trustedDelivery=true;}
        }
        // IDs belong to the authoritative scheduler, never to untrusted model output.
        ToolRequest request=new ToolRequest(session+":"+(++sequence),choice.getName(),choice.getArguments());
        AgentTask.State before=task.getState();
        String validation="UNKNOWN_TOOL";
        for(ToolDefinition definition:tools.definitions()) if(definition.getName().equals(request.getName())) {
            validation=definition.accepts(request.getArguments())?"VALID":"INVALID_ARGUMENTS"; break;
        }
        ActionResult result=task.commit(op,now,()->tools.execute(request)); steps++;
        continuation=result.getStatus()==ActionResult.Status.IN_PROGRESS && runnable() ? request : null;
        if (result.getStatus()==ActionResult.Status.REJECTED || result.getStatus()==ActionResult.Status.FAILED
                || result.getStatus()==ActionResult.Status.TIMED_OUT) failures++;
        else failures=0;
        if("VALID".equals(validation) && result.getStatus()==ActionResult.Status.REJECTED) validation=result.getCode();
        structured.record(task.getTaskId(),op==null?null:op.getId(),request,validation,result,before,task.getState(),providerLatency,providerUsage,trustedDelivery?(request.getName().equals("pickup")?"ENGINE_LOCAL_PICKUP_PRIORITY":"ENGINE_DELIVERY_PRIORITY"):"MODEL_OR_CONTINUATION");
        providerLatency=0;providerUsage=Collections.emptyMap();
        if(deliveryPriority!=null){
            if(Arrays.asList("NO_PATH","PATH_BLOCKED","PATH_CHANGED","INTERACTION_WAITED","RESOURCE_WAITED").contains(result.getCode()))navigationFailures++;
            else if("move_to".equals(request.getName())&&(result.getStatus()==ActionResult.Status.SUCCESS||result.getStatus()==ActionResult.Status.IN_PROGRESS))navigationFailures=0;
            if(navigationFailures>=3){
                recordLegacy(result);task.pause();continuation=null;
                return record(ActionResult.of(ActionResult.Status.INTERRUPTED,"NAVIGATION_REQUIRES_HELP",Collections.singletonMap("reason","连续寻路失败，请检查通路或角色占位后继续委托。")));
            }
        }
        return recordLegacy(result);
    }
    private ActionResult record(ActionResult result) {
        return recordEvent(result,operation);
    }
    private ActionResult recordEvent(ActionResult result,AgentTask.Operation op) {
        structured.record(task.getTaskId(),op==null?null:op.getId(),null,"NOT_APPLICABLE",result,eventBefore==null?task.getState():eventBefore,task.getState(),pending==null?providerLatency:elapsed(),providerUsage);
        return recordLegacy(result);
    }
    private ActionResult recordLegacy(ActionResult result) {
        last=result; if(trace.size()==64) trace.removeFirst(); trace.addLast(result); return result;
    }
    private boolean runnable() { return task.getState()==AgentTask.State.RUNNING || task.getState()==AgentTask.State.REPLANNING; }
    private void clearWork() {
        if(pending!=null) {
            if(task.getState()!=AgentTask.State.PROVIDER_UNAVAILABLE && task.getState()!=AgentTask.State.COMPLETED)
                structured.record(task.getTaskId(),operation==null?null:operation.getId(),null,"STALE_OPERATION",ActionResult.rejected("STALE_OPERATION"),eventBefore==null?task.getState():eventBefore,task.getState(),elapsed(),Collections.emptyMap());
            pending.cancel(false);
        }
        pending=null; operation=null; continuation=null; steps=0;
    }
    private double elapsed() {return providerStarted==0?0:Math.max(0,System.nanoTime()-providerStarted)/1000000.0;}
    private Decision decide(Context context) {
        long start=System.nanoTime();ToolRequest request=null;RuntimeException error=null;Map<String,Number> usage=Collections.emptyMap();
        try {request=provider.decide(context);} catch(RuntimeException failure) {error=failure;}
        double latency=Math.max(0,System.nanoTime()-start)/1000000.0;
        try {usage=Collections.unmodifiableMap(new LinkedHashMap<>(provider.getUsage()));} catch(RuntimeException ignored) { }
        return new Decision(request,error,usage,latency);
    }
    public void configureTrace(String agentId,String provider,String model) {structured.configure(agentId,provider,model);}
    public List<Map<String,Object>> getStepTrace() {return structured.rows();}
    public Map<String,Object> getMetrics() {
        Map<String,Object> metrics=new LinkedHashMap<>(structured.metrics(task.getState()));
        metrics.put("decisionCount",(long)decisions);metrics.put("failureCount",(long)failures);
        metrics.put("navigationFailureCount",(long)navigationFailures);metrics.put("deliveryPriorityActions",deliveryPriorityActions);
        return Collections.unmodifiableMap(metrics);
    }
    /** Trusted checkpoint counters only; never restores actions, approvals or provider futures. */
    public void restoreProgress(Map<String,Object> metrics) {
        if(decisions!=0||pending!=null||!trace.isEmpty())throw new IllegalStateException("Loop already started");
        long restoredDecisions=StepTrace.count(metrics,"decisionCount"),restoredFailures=StepTrace.count(metrics,"failureCount");
        structured.restoreMetrics(metrics);decisions=(int)restoredDecisions;failures=(int)restoredFailures;
        deliveryPriorityActions=StepTrace.count(metrics,"deliveryPriorityActions");
    }
    public void pause() { task.pause(); clearWork(); revision=task.getRevision(); }
    public void resume() { task.resume(); clearWork(); navigationFailures=0;progressKey=null;repeatedSites.clear();revision=task.getRevision(); }
    public void cancel() { task.cancel(); clearWork(); revision=task.getRevision(); }
    public List<ActionResult> getTrace() { return Collections.unmodifiableList(new ArrayList<>(trace)); }
    public AgentTask.State getState() { return task.getState(); }
}
