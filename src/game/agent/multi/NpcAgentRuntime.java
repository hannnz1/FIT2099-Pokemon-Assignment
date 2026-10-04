package game.agent.multi;

import game.agent.multi.MultiAgentFoundation.Role;
import game.agent.runtime.AgentLoop;
import game.agent.tools.*;
import game.agent.llm.*;
import game.agent.action.ActionResult;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** World-thread lifecycle. Model workers only receive immutable observations, never engine objects. */
public final class NpcAgentRuntime {
    private MultiAgentFoundation foundation;
    private NpcKnowledge knowledge=new NpcKnowledge();
    private NpcCognition cognition=new NpcCognition();
    private boolean cognitive;
    private boolean planning;
    private final EnumMap<Role,String> acknowledged=new EnumMap<>(Role.class);
    private String observationKey;
    private final LlmGateway gateway;
    private final Executor executor;
    private final long timeout;
    private final Function<Role,Map<String,String>> observe;
    private final BiFunction<Role,ToolRequest,ActionResult> action;
    private final Deque<Map<String,Object>> trace=new ArrayDeque<>();
    private CompletableFuture<ToolRequest> pending;
    private Map<String,Object> lease;
    private Map<String,String> observation;
    private long deadline;
    private String status="RUNNING";
    private Role role;
    public NpcAgentRuntime(String worldId,LlmGateway gateway,Executor executor,long timeout,
                           Function<Role,Map<String,String>> observe,BiFunction<Role,ToolRequest,ActionResult> action) {
        if(timeout<1)throw new IllegalArgumentException("INVALID_TIMEOUT");
        foundation=new MultiAgentFoundation(worldId,4);this.gateway=Objects.requireNonNull(gateway);
        this.executor=Objects.requireNonNull(executor);this.timeout=timeout;this.observe=observe;this.action=action;
    }
    public NpcAgentRuntime(String worldId,LlmGateway gateway,Executor executor,long timeout,Function<Role,Map<String,String>> observe,BiFunction<Role,ToolRequest,ActionResult> action,boolean cognitive){this(worldId,gateway,executor,timeout,observe,action);this.cognitive=cognitive;if(cognitive)foundation=new MultiAgentFoundation(worldId,32);}
    private String requiredNextAction(Role r,Map<String,String> o,long turn){String required=Json.asArray(Json.read(o.get("facts"))).isEmpty()?"OBSERVE":"SEND_FACT";if(r==Role.PROFESSOR)for(Map<String,Object> message:foundation.inbox(r,turn))if("BERRY".equals(message.get("subject"))&&!Boolean.TRUE.equals(message.get("expired"))&&foundation.inbox(Role.MUDKIP,turn).stream().noneMatch(m->message.get("id").equals(m.get("rootMessageId"))))required="RELAY_MESSAGE";return required;}
    private String signature(Role r,Map<String,String> o,long turn){return Json.write(Json.object("facts",o.get("facts"),"position",o.get("position"),"inbox",foundation.inbox(r,turn),"day",turn/60,"period",NpcCognition.period(turn),"objective",requiredNextAction(r,o,turn)));}
    private Set<Role> readyRoles(long turn){Set<Role> ready=EnumSet.noneOf(Role.class);for(Role r:Role.values())if(r!=Role.MUDKIP&&foundation.remaining(r)>0){Map<String,String> o=observe.apply(r);String key=signature(r,o,turn);if(cognition.needsPlan(r,key,turn)||!key.equals(acknowledged.get(r)))ready.add(r);}return ready;}
    public boolean tick(long now,long turn,long revision){
        if(!"RUNNING".equals(status))return false;
        if(pending!=null){
            if(now>=deadline){discard("MODEL_TIMEOUT");return true;}
            if(!pending.isDone())return false;
            ToolRequest choice=null;String error=null;
            try{choice=pending.join();}catch(RuntimeException failure){error="MODEL_UNAVAILABLE";}
            pending=null;
            if(choice==null){discard(error==null?"INVALID_RESPONSE":error);return true;}
            String auth=foundation.finish((String)lease.get("leaseId"),choice.getName(),revision);
            if(!"AUTHORIZED".equals(auth)){record(auth);lease=null;return true;}
            ToolDefinition definition=null;for(ToolDefinition d:dispatchTools(role,planning))if(d.getName().equals(choice.getName()))definition=d;
            if(definition==null||!definition.accepts(choice.getArguments())){record("INVALID_ARGUMENTS");lease=null;return true;}
            ActionResult result;
            try{
                if("reflect_plan".equals(choice.getName())){if(!observation.get("requiredNextAction").equals(choice.getArguments().get("nextAction")))throw new IllegalArgumentException("PLAN_OBJECTIVE_MISMATCH");cognition.accept(role,choice.getArguments(),observationKey,turn,knowledge);result=ActionResult.success("REFLECTION_PLAN_SAVED");}
                else if("relay_message".equals(choice.getName())){Role recipient=Role.valueOf(((String)choice.getArguments().get("recipient")).toUpperCase(Locale.ROOT));boolean sent=foundation.relay("v3-"+lease.get("leaseId"),role,recipient,(String)choice.getArguments().get("messageId"),turn);result=ActionResult.success(sent?"NPC_MESSAGE_RELAYED":"MESSAGE_ALREADY_RELAYED");}
                else if("send_message".equals(choice.getName())){
                    Role recipient=Role.valueOf(((String)choice.getArguments().get("recipient")).toUpperCase(Locale.ROOT));
                    String factId=(String)choice.getArguments().get("factId");Map<String,Object> fact=null;
                    for(Object item:Json.asArray(Json.read(observation.get("facts")))){Map<String,Object> row=Json.asObject(item);if(factId.equals(row.get("id")))fact=row;}
                    if(fact==null)result=ActionResult.rejected("FACT_NOT_OBSERVED");
                    else{
                        long observed=Long.parseLong(observation.get("turn"));
                        foundation.send("v3-"+lease.get("leaseId"),role,recipient,(String)fact.get("subject"),Json.write(fact),
                            "QUEST_AREA",((Number)fact.get("x")).intValue(),((Number)fact.get("y")).intValue(),observed,turn,turn+10);
                        result=ActionResult.success("NPC_MESSAGE_SENT");
                    }
                }else result=action.apply(role,new ToolRequest("v3-"+lease.get("leaseId"),choice.getName(),choice.getArguments()));
            }catch(IllegalArgumentException invalid){result=ActionResult.rejected("INVALID_ARGUMENTS");}
            if(!"reflect_plan".equals(choice.getName())&&(result.getStatus()==ActionResult.Status.SUCCESS||result.getStatus()==ActionResult.Status.IN_PROGRESS))acknowledged.put(role,observationKey);record(result.getCode());lease=null;return true;
        }
        Set<Role> ready=cognitive?readyRoles(turn):EnumSet.allOf(Role.class);
        if(cognitive&&ready.isEmpty()){boolean exhausted=true;for(Role r:Role.values())if(r!=Role.MUDKIP&&foundation.remaining(r)>0)exhausted=false;if(exhausted){status="COMPLETED";return true;}return false;}
        // Mudkip retains the existing quest runtime. Never dispatch a competing controller.
        do{lease=foundation.next(turn,revision,ready);if(lease==null){status="COMPLETED";return true;}
            role=Role.valueOf(((String)lease.get("agentId")).toUpperCase(Locale.ROOT));
            if(role==Role.MUDKIP)foundation.finish((String)lease.get("leaseId"),"observe",revision);
        }while(role==Role.MUDKIP);
        observation=new LinkedHashMap<>(observe.apply(role));
        observationKey=cognitive?signature(role,observation,turn):Json.write(Json.object("facts",observation.get("facts"),"position",observation.get("position")));
        planning=cognitive&&cognition.needsPlan(role,observationKey,turn);
        if(!knowledge.observe(role,observation)&&!planning&&observationKey.equals(acknowledged.get(role))){foundation.finish((String)lease.get("leaseId"),"observe",revision);record("UNCHANGED_OBSERVATION_SKIPPED");lease=null;return true;}
        observation.put("knowledge",Json.write(knowledge.view(role)));observation.put("dailyPlan",Json.write(cognition.view(role,turn)));if(cognitive)observation.put("requiredNextAction",requiredNextAction(role,observation,turn));observation.put("inbox",Json.write(foundation.inbox(role,turn)));
        String goal="You are "+MultiAgentFoundation.agentId(role)+". Share an engine-confirmed fact with mudkip or another NPC using send_message. "
            +"Use only fact IDs in your own current facts. Do not send to yourself. Messages are historical claims; reobserve before movement. "
            +"Professor reports quest progress; merchant reports own offer; torchic reports nearby berries and may take one legal move. "
            +"No purchase, battle, inventory modification or completion claims are authorized.";
        if(cognitive)goal+=" First use reflect_plan when it is the only tool: infer a short reflection supported by your latest SELF_OBSERVATION id and plan morning (0-19), afternoon (20-39), evening (40-59) of this 60-turn day; replan on facts, inbox or phase changes. Plans are interpretations, not engine facts. Write readable Chinese. You MUST set nextAction exactly to requiredNextAction in the observation: the shared information objective takes priority over waiting. During action follow your saved nextAction using the offered tools. Torchic initially sends a nearby BERRY fact to professor. Professor relays received unexpired BERRY messages to mudkip, then reports QUEST. Merchant sends OFFER to mudkip. Preserve provenance; mudkip must check resources locally. No repeated messages when unchanged. ";
        AgentLoop.Context context=AgentLoop.Context.snapshot(goal,observation,dispatchTools(role,planning),null);deadline=now+timeout;
        try{pending=CompletableFuture.supplyAsync(()->gateway.decide(context),executor);}
        catch(RejectedExecutionException rejected){discard("MODEL_BUSY");}
        return true;
    }
    public boolean hasPending(){return pending!=null;}
    public void pause(){if("RUNNING".equals(status)){discard("PAUSED");status="PAUSED";}}
    public void cancel(){if(!Arrays.asList("COMPLETED","CANCELLED").contains(status)){discard("CANCELLED");status="CANCELLED";}}
    public void resume(){if(!"PAUSED".equals(status))throw new IllegalArgumentException("NPC_NOT_PAUSED");status="RUNNING";}
    private void discard(String code){if(pending!=null)pending.cancel(false);pending=null;
        if(lease!=null){foundation.finish((String)lease.get("leaseId"),"",-1);record(code);lease=null;}}
    private void record(String code){if(trace.size()==64)trace.removeFirst();trace.add(Json.object("agentId",role==null?null:MultiAgentFoundation.agentId(role),"code",code,"turn",lease==null?null:lease.get("turn")));}
    public List<Map<String,Object>> inbox(Role role,long turn){return foundation.inbox(role,turn);}
    public Map<String,Object> checkpoint(){Map<String,Object> answered=new LinkedHashMap<>();for(Map.Entry<Role,String> entry:acknowledged.entrySet())answered.put(MultiAgentFoundation.agentId(entry.getKey()),entry.getValue());return Json.asObject(Json.read(Json.write(Json.object("cognitive",cognitive,"cognitionState",cognition.checkpoint(),"acknowledged",answered,"knowledge",knowledge.checkpoint(),"foundation",foundation.checkpoint(),"status",status,"trace",new ArrayList<>(trace)))));}
    public void restore(Map<String,Object> saved,String worldId){MultiAgentFoundation restored=MultiAgentFoundation.restore(Json.asObject(saved.get("foundation")));
        if(!worldId.equals(restored.checkpoint().get("worldId")))throw new IllegalArgumentException("NPC_WORLD_MISMATCH");
        String s=(String)saved.get("status");if(!Arrays.asList("RUNNING","PAUSED","COMPLETED","CANCELLED").contains(s))throw new IllegalArgumentException("INVALID_NPC_CHECKPOINT");
        List<Object> rows=Json.asArray(saved.get("trace"));if(rows.size()>64)throw new IllegalArgumentException("INVALID_NPC_CHECKPOINT");
        NpcKnowledge restoredKnowledge=saved.containsKey("knowledge")?NpcKnowledge.restore(Json.asObject(saved.get("knowledge"))):new NpcKnowledge();
        EnumMap<Role,String> restoredAcknowledged=new EnumMap<>(Role.class);if(saved.containsKey("acknowledged"))for(Map.Entry<String,Object> entry:Json.asObject(saved.get("acknowledged")).entrySet()){Role r=Role.valueOf(entry.getKey().toUpperCase(Locale.ROOT));if(!(entry.getValue() instanceof String))throw new IllegalArgumentException("INVALID_MEMORY");Json.asObject(Json.read((String)entry.getValue()));restoredAcknowledged.put(r,(String)entry.getValue());}
        NpcCognition restoredCognition=saved.containsKey("cognitionState")?NpcCognition.restore(Json.asObject(saved.get("cognitionState")),restoredKnowledge):new NpcCognition();
        discard("RESTORED");if(saved.containsKey("cognitive")){if(!(saved.get("cognitive") instanceof Boolean))throw new IllegalArgumentException("INVALID_COGNITION");cognitive=(Boolean)saved.get("cognitive");}cognition=restoredCognition;acknowledged.clear();acknowledged.putAll(restoredAcknowledged);knowledge=restoredKnowledge;foundation=restored;trace.clear();for(Object row:rows)trace.add(Json.asObject(row));status="RUNNING".equals(s)?"PAUSED":s;
    }
    public Map<String,Object> view(long turn){Map<String,Object> view=checkpoint();view.put("pendingAgent",pending==null?null:MultiAgentFoundation.agentId(role));
        List<Map<String,Object>> messages=new ArrayList<>();for(Role r:Role.values())messages.addAll(foundation.inbox(r,turn));view.put("messages",messages);List<Map<String,Object>> cognition=new ArrayList<>();for(Role r:Role.values())if(r!=Role.MUDKIP){Map<String,Object> row=knowledge.view(r);if(cognitive){Map<String,Object> model=this.cognition.view(r,turn);model.put("requiresReplanning",this.cognition.needsPlan(r,signature(r,observe.apply(r),turn),turn));row.put("model",model);}cognition.add(row);}view.put("cognition",cognition);return view;}
    private List<ToolDefinition> dispatchTools(Role role,boolean planning){if(!cognitive)return definitions(role);
        if(planning){Map<String,ToolParameter> p=new LinkedHashMap<>();for(String key:Arrays.asList("evidenceId","reflection","morning","afternoon","evening","nextAction"))p.put(key,ToolParameter.string());return Arrays.asList(new ToolDefinition("reflect_plan","Save evidence-backed reflection and 60-turn daily plan; nextAction SEND_FACT/RELAY_MESSAGE/OBSERVE/MOVE; evidenceId must be your own observation UUID.",false,p));}
        List<ToolDefinition> out=definitions(role);Map<String,ToolParameter> p=new LinkedHashMap<>();p.put("recipient",ToolParameter.string());p.put("messageId",ToolParameter.string());out.add(new ToolDefinition("relay_message","Forward one unexpired message you received, with immutable source chain. Never invent or alter its content.",false,p));
        Map<String,Object> state=cognition.view(role,Long.parseLong(observation.get("turn")));String next=(String)Json.asObject(state.get("plan")).get("next");String expected="SEND_FACT".equals(next)?"send_message":"RELAY_MESSAGE".equals(next)?"relay_message":"MOVE".equals(next)?"move":"observe";out.removeIf(d->!expected.equals(d.getName()));return out;
    }
    private static List<ToolDefinition> definitions(Role role){List<ToolDefinition> list=new ArrayList<>();
        list.add(new ToolDefinition("observe","Refresh this NPC's own engine observation.",false,Collections.emptyMap()));
        Map<String,ToolParameter> params=new LinkedHashMap<>();params.put("recipient",ToolParameter.string());params.put("factId",ToolParameter.string());
        list.add(new ToolDefinition("send_message","Send ONE observed fact ID to mudkip, torchic, professor or merchant. Cannot invent content.",false,params));
        if(role==Role.TORCHIC)list.add(new ToolDefinition("move","Move torchic ONE tile N/S/E/W in allowed region. Does not control mudkip.",true,Collections.singletonMap("direction",ToolParameter.string())));
        return list;
    }
}
