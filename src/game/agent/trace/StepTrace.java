package game.agent.trace;

import game.agent.action.ActionResult;
import game.agent.runtime.AgentTask;
import game.agent.tools.ToolRequest;
import java.util.*;

/** Bounded public audit records. No observations, provider text or approval material. */
public final class StepTrace {
 private final Deque<Map<String,Object>> rows=new ArrayDeque<>();
 private String agent="agent",provider="unknown",model="unknown",goal="unknown";
 private long sequence,providerCalls,latencySamples;
 private double providerLatencyTotal;
 public void configureGoal(String goal) {this.goal=label(goal);}
 public void providerStarted() {providerCalls++;}
 public void providerCompleted(double latency) {if(Double.isFinite(latency)&&latency>=0){providerLatencyTotal+=latency;latencySamples++;}}
 private long total,invalid,replans,replanSuccess,approvalCount,approvalNanos,approvalStarted;
 private boolean replanning;
 private AgentTask.State observedState;
 public void configure(String agent,String provider,String model) {this.agent=label(agent);this.provider=label(provider);this.model=label(model);}
 public void observe(AgentTask.State state) {
  if(state==AgentTask.State.WAITING_APPROVAL && observedState!=state){approvalCount++;approvalStarted=System.nanoTime();}
  if(state!=AgentTask.State.WAITING_APPROVAL && approvalStarted!=0) {approvalNanos+=Math.max(0,System.nanoTime()-approvalStarted);approvalStarted=0;}
  if(state==AgentTask.State.REPLANNING && observedState!=state){replans++;replanning=true;}
  observedState=state;
 }
 public void record(String taskId,String operationId,ToolRequest request,String validation,ActionResult result,AgentTask.State before,AgentTask.State after,double latency,Map<String,Number> usage) {
  record(taskId,operationId,request,validation,result,before,after,latency,usage,request==null?"SCHEDULER":"MODEL_OR_CONTINUATION");
 }
 public void record(String taskId,String operationId,ToolRequest request,String validation,ActionResult result,AgentTask.State before,AgentTask.State after,double latency,Map<String,Number> usage,String executionSource) {
  observe(before);
  Map<String,Object> row=new LinkedHashMap<>();row.put("traceId",UUID.randomUUID().toString());row.put("stepNumber",++sequence);row.put("goal",goal);row.put("taskId",label(taskId));row.put("agentId",agent);
  row.put("actionId",request==null?null:label(request.getActionId()));row.put("operationId",operationId==null?null:label(operationId));
  row.put("executionSource",executionSource);
  row.put("toolName",request==null?null:label(request.getName()));row.put("arguments",request==null?Collections.emptyMap():sanitize(request.getArguments(),0));
  row.put("validationResult",validation);row.put("actionResult",label(result.getCode()));row.put("actionStatus",result.getStatus().name());row.put("resultData",sanitize(result.getData(),0));
  row.put("taskStateBefore",before.name());row.put("taskStateAfter",after.name());row.put("latencyMs",Math.max(0,latency));
  row.put("provider",provider);row.put("model",model);row.put("timestamp",System.currentTimeMillis());
  Map<String,Object> tokens=new LinkedHashMap<>();
  if(usage!=null) for(String key:Arrays.asList("inputTokens","outputTokens","totalTokens","promptTokens","completionTokens")) {
   Number value=usage.get(key);if(value!=null && value.longValue()>=0)tokens.put(key,value.longValue());
  }
  row.put("usage",Collections.unmodifiableMap(tokens));
  if(rows.size()==64)rows.removeFirst();rows.addLast(Collections.unmodifiableMap(row));
  if(request!=null) {
   total++;if(result.getStatus()==ActionResult.Status.REJECTED)invalid++;
   if(replanning && result.getStatus()!=ActionResult.Status.REJECTED && result.getStatus()!=ActionResult.Status.FAILED && result.getStatus()!=ActionResult.Status.TIMED_OUT) {replanSuccess++;replanning=false;}
  }
  observe(after);
 }
 public List<Map<String,Object>> rows() {return Collections.unmodifiableList(new ArrayList<>(rows));}
 public Map<String,Object> metrics(AgentTask.State state) {
  observe(state);Map<String,Object> m=new LinkedHashMap<>();m.put("totalSteps",total);m.put("invalidCalls",invalid);m.put("invalidCallRate",total==0?0.0:(double)invalid/total);
  m.put("totalProviderCalls",providerCalls);m.put("llmLatencySamples",latencySamples);m.put("averageLlmLatencyMs",latencySamples==0?0.0:providerLatencyTotal/latencySamples);
  m.put("totalLlmLatencyMs",providerLatencyTotal);m.put("traceSequence",sequence);m.put("replanPending",replanning);
  m.put("taskCount",1L);m.put("completedTasks",state==AgentTask.State.COMPLETED?1L:0L);m.put("taskCompletionRate",state==AgentTask.State.COMPLETED?1.0:0.0);
  m.put("averageSteps",(double)total);m.put("replanCount",replans);m.put("replanSuccessCount",replanSuccess);m.put("replanSuccessRate",replans==0?0.0:(double)replanSuccess/replans);
  m.put("approvalCount",approvalCount);m.put("totalApprovalLatencyMs",approvalNanos/1000000.0);m.put("averageApprovalLatencyMs",approvalCount==0?0.0:approvalNanos/1000000.0/approvalCount);return Collections.unmodifiableMap(m);
 }
 public void restoreMetrics(Map<String,Object> m) {
  if(!rows.isEmpty()||total!=0||providerCalls!=0)throw new IllegalStateException("Trace already started");
  total=count(m,"totalSteps");invalid=count(m,"invalidCalls");replans=count(m,"replanCount");replanSuccess=count(m,"replanSuccessCount");
  providerCalls=count(m,"totalProviderCalls");latencySamples=count(m,"llmLatencySamples");sequence=count(m,"traceSequence");approvalCount=count(m,"approvalCount");
  if(invalid>total||replanSuccess>replans||latencySamples>providerCalls)throw new IllegalArgumentException("Invalid trace counters");
  providerLatencyTotal=duration(m,"totalLlmLatencyMs");approvalNanos=(long)(duration(m,"totalApprovalLatencyMs")*1000000);
  replanning=Boolean.TRUE.equals(m.get("replanPending"));approvalStarted=0;observedState=replanning?AgentTask.State.REPLANNING:null;
 }
 public static long count(Map<String,Object> m,String key) {
  Object raw=m.get(key);if(raw==null)return 0;if(!(raw instanceof Number))throw new IllegalArgumentException("Invalid counter");
  double d=((Number)raw).doubleValue();long n=((Number)raw).longValue();if(!Double.isFinite(d)||d!=n||n<0||n>1000000000L)throw new IllegalArgumentException("Invalid counter");return n;
 }
 private static double duration(Map<String,Object> m,String key) {
  Object raw=m.get(key);if(raw==null)return 0;if(!(raw instanceof Number))throw new IllegalArgumentException("Invalid duration");
  double d=((Number)raw).doubleValue();if(!Double.isFinite(d)||d<0||d>1000000000000.0)throw new IllegalArgumentException("Invalid duration");return d;
 }
 private static String label(String s) {if(s==null)return "unknown";return safe(s.substring(0,Math.min(160,s.length())));}
 private static String safe(String s) {
  String low=s.toLowerCase(Locale.ROOT);if(low.contains("sk-")||low.contains("bearer ")||low.contains("aiza")||low.contains("approvaltoken")||low.contains("api_key")||low.contains("apikey"))return "[redacted]";return s;
 }
 private static Object sanitize(Object source,int depth) {
  if(depth>3)return "[bounded]";
  if(source instanceof Map) {Map<String,Object> out=new LinkedHashMap<>();int n=0;
   for(Object raw:((Map<?,?>)source).keySet()) {if(n++>=32)break;if(!(raw instanceof String))continue;String key=(String)raw;String low=key.toLowerCase(Locale.ROOT).replace("_","").replace("-","");
    if(low.contains("token")||low.contains("secret")||low.contains("key")||low.contains("authorization")||low.contains("reasoning")||low.contains("prompt")||low.contains("context"))continue;
    out.put(label(key),sanitize(((Map<?,?>)source).get(raw),depth+1));
   }return Collections.unmodifiableMap(out);
  }
  if(source instanceof List) {List<Object> out=new ArrayList<>();for(Object item:(List<?>)source){if(out.size()==32)break;out.add(sanitize(item,depth+1));}return Collections.unmodifiableList(out);}
  if(source instanceof String)return label((String)source);
  if(source==null||source instanceof Boolean||source instanceof Integer||source instanceof Long)return source;
  if(source instanceof Number){double n=((Number)source).doubleValue();return Double.isFinite(n)?n:null;}
  return "[unsupported]";
 }
}
