package game.agent.web;

import game.agent.action.ActionResult;
import game.agent.demo.GeminiQuestScenario;
import game.agent.llm.*;
import game.agent.runtime.*;
import game.agent.persistence.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

/** One isolated, in-memory real-engine scene. Access exclusively on the world thread. */
public final class AgentRoom implements WebRoom {
    public static final class Reply {
        public final int status;
        public final Map<String,Object> body;
        Reply(int status,Map<String,Object> body) { this.status=status;this.body=Collections.unmodifiableMap(body); }
    }
    private static final Set<String> FIELDS=new HashSet<>(Arrays.asList("requestId","taskId","expectedRevision","command","params"));
    private static final List<String> WORLD=Arrays.asList("questId","questStatus","taskState","requiredBerry","carriedBerry","turn",
        "deadlineTurn","coins","noSpending","approvalOutcome","x","y","visibleBerry","nearProfessor","nearMerchant","merchantStock","berryPrice");
    private static final class Cached { final String payload; final Reply reply; Cached(String p,Reply r) {payload=p;reply=r;} }
    private final String id=UUID.randomUUID().toString(),owner;
    private final ProviderSelection provider;
    private final Executor executor;
    private final Map<String,Cached> cache=new LinkedHashMap<>();
    private final Deque<Map<String,Object>> trace=new ArrayDeque<>();
    private GeminiQuestScenario scene;
    private AgentLoop loop;
    private TaskIntent intent;
    private CompletableFuture<TaskIntent> parsing;
    private String phase="IDLE",errorCode;
    private long revision=1,parseDeadline,lastTick=-1,activeMillis;
    private int parses,resumes;
    private final boolean original;
    private final WorldStore store;
    private final PokemonCollection collection;
    private boolean recovered,storageFailed;
    private String projectedDeployment;
    private game.agent.multi.NpcAgentRuntime npcAgents;
    private game.agent.multi.NpcAgentRuntime createNpcAgents(){game.agent.multi.NpcAgentRuntime runtime=new game.agent.multi.NpcAgentRuntime(scene.getSession().getQuestId(),provider.gateway==null?c->{throw new ProviderException(ProviderException.Code.CONFIGURATION);}:provider.gateway,executor,provider.timeoutMillis+1000L,scene::npcObservation,scene::npcAction,true);scene.setAgentMessages(()->runtime.inbox(game.agent.multi.MultiAgentFoundation.Role.MUDKIP,scene.getSession().getTurn()));return runtime;}
    private String combatPartner="mudkip",aiPartner="mudkip";
    private Map<String,Object> restoredSummoned;
    private List<Map<String,Object>> previousSteps=new ArrayList<>();
    private Map<String,Object> previousMetrics=Collections.emptyMap();
    public AgentRoom(String owner,ProviderSelection provider,Executor executor) {
        this(owner,provider,executor,false,null);
    }
    public AgentRoom(String owner,ProviderSelection provider,Executor executor,boolean original,WorldStore store) {
        this(owner,provider,executor,original,store,new PokemonCollection(owner,store));
    }
    AgentRoom(String owner,ProviderSelection provider,Executor executor,boolean original,WorldStore store,PokemonCollection collection) {
        this.collection=collection;
        this.owner=Objects.requireNonNull(owner);this.provider=Objects.requireNonNull(provider);
        this.executor=Objects.requireNonNull(executor);this.original=original;this.store=store;scene=new GeminiQuestScenario(owner,original);
        if(store!=null){Map<String,Object> checkpoint=store.load(owner);if(checkpoint!=null)restore(checkpoint);else persist();}
        synchronizeCollection();
    }
    public String getId() { return id; }
    public Reply command(Map<String,Object> input,long now) {
        return command(input,now,false);
    }
    public Reply command(Map<String,Object> input,long now,boolean otherModeActive) {
        return command(input,now,otherModeActive?"OTHER_MODE_ACTIVE":null);
    }
    public Reply command(Map<String,Object> input,long now,String controlBlock) {
        synchronizeCollection();
        String requestId="";
        try {
            if(!input.keySet().equals(FIELDS)) return reply(400,requestId,"INVALID_FIELDS");
            requestId=identifier(input.get("requestId"));String taskId=identifier(input.get("taskId"));
            String kind=identifier(input.get("command"));Map<String,Object> params=Json.asObject(input.get("params"));
            if(!(input.get("expectedRevision") instanceof Number)) throw new IllegalArgumentException();
            long expected=new BigDecimal(String.valueOf(input.get("expectedRevision"))).longValueExact();
            if(expected<0) throw new IllegalArgumentException();
            Set<String> required=("MANUAL".equals(kind)||"WILD_ACTION".equals(kind)||"PLAYER_ACTION".equals(kind))?new HashSet<>(Arrays.asList("action","arguments")):"PARSE".equals(kind)?Collections.singleton("text"):
                ("APPROVE".equals(kind)||"DENY".equals(kind))?Collections.singleton("proposalId"):"SUMMON".equals(kind)?new HashSet<>(Arrays.asList("captureId","direction")):"FOLLOW".equals(kind)?new HashSet<>(Arrays.asList("captureId","enabled")):"STORE_WILD".equals(kind)?Collections.singleton("targetId"):("RECALL".equals(kind)||"PARTNER".equals(kind)||"REST".equals(kind))?Collections.singleton("captureId"):"SWITCH_MODE".equals(kind)?Collections.singleton("mode"):Collections.emptySet();
            if(!params.keySet().equals(required) && !("PARSE".equals(kind)&&params.keySet().equals(new HashSet<>(Arrays.asList("text","captureId"))))) return reply(400,requestId,"INVALID_PARAMS");
            String payload=Json.write(input);Cached cached=cache.get(requestId);
            if(cached!=null) return cached.payload.equals(payload)?cached.reply:reply(409,requestId,"REQUEST_ID_CONFLICT");
            boolean stop="PAUSE".equals(kind)||"CANCEL".equals(kind)||"NPC_PAUSE".equals(kind)||"NPC_CANCEL".equals(kind);
            if(!scene.getTaskId().equals(taskId)) return reply(409,requestId,"STALE_TASK");
            if(expected>revision || (!stop && expected!=revision)) return reply(409,requestId,"STALE_REVISION");
            if(controlBlock!=null && Arrays.asList("NPC_START","NPC_RESUME","COOP_START","PLAYER_ACTION","PARSE","CONFIRM","RESUME","MANUAL","APPROVE","DENY","SUMMON","RECALL","FOLLOW","EXPLORE","WILD_ACTION","STORE_WILD","PARTNER","REST","RESET").contains(kind))return reply(409,requestId,controlBlock);
            // Keep a fixed history and reserve a separate stop path when a room exhausts it.
            if(cache.size()>=256 && !stop) return reply(429,requestId,"REQUEST_LIMIT");
            if(storageFailed && !"RECOVER".equals(kind) && !"CANCEL".equals(kind) && !"NPC_CANCEL".equals(kind) && !"NPC_PAUSE".equals(kind))return reply(503,requestId,"STORAGE_UNAVAILABLE");
            Reply result=execute(requestId,kind,params,now);
            if(result.status==200 && !storageFailed && !persist())result=reply(503,requestId,"STORAGE_UNAVAILABLE");
            if(cache.size()<256) cache.put(requestId,new Cached(payload,result));
            return result;
        } catch(IllegalArgumentException | ClassCastException | NullPointerException error) { return reply(400,requestId,"INVALID_COMMAND"); }
    }
    private Reply execute(String requestId,String kind,Map<String,Object> params,long now) {
        String state=status();
        switch(kind) {
            case "NPC_START":
                if(!provider.isAvailable())return reply(409,requestId,"PROVIDER_CONFIGURATION");
                if(!original||npcAgents!=null||!state.equals("IDLE")||scene.getSession().getTurn()!=0)return reply(409,requestId,"NEW_ROUND_REQUIRED");
                scene.enableNpcAgents();npcAgents=createNpcAgents();break;
            case "NPC_PAUSE":case "NPC_CANCEL":case "NPC_RESUME":
                if(npcAgents==null)return reply(409,requestId,"NPC_NOT_STARTED");
                if("NPC_RESUME".equals(kind)){if(!provider.isAvailable())return reply(409,requestId,"PROVIDER_CONFIGURATION");npcAgents.resume();}
                else if("NPC_CANCEL".equals(kind))npcAgents.cancel();else npcAgents.pause();break;
            case "COOP_START":
                if(!state.equals("IDLE"))return reply(409,requestId,"NEW_ROUND_REQUIRED");
                try{scene.enableCooperative();}catch(IllegalArgumentException invalid){return reply(409,requestId,invalid.getMessage());}break;
            case "PLAYER_ACTION": {
                if(state.equals("PARSING")||state.equals("READY"))return reply(409,requestId,"CONFIRM_BEFORE_PLAYER_ACTION");
                ActionResult result=scene.playerAction(identifier(params.get("action")),Json.asObject(params.get("arguments")));
                if(result.getStatus()!=ActionResult.Status.SUCCESS&&result.getStatus()!=ActionResult.Status.IN_PROGRESS)return reply(409,requestId,result.getCode());
                append(result,now);scene.advanceTurn();break;
            }
            case "EXPLORE": case "PARTNER": case "REST": case "STORE_WILD": case "WILD_ACTION": {
                if(Arrays.asList("PARSING","READY","RUNNING","REPLANNING","WAITING_APPROVAL").contains(state))return reply(409,requestId,"AI_CONTROLS_COMPANION");
                ActionResult action=null;
                if("EXPLORE".equals(kind))action=scene.explore();
                else if("PARTNER".equals(kind)){
                    String captureId=identifier(params.get("captureId"));Map<String,Object> out=collection.deployment();if(!"mudkip".equals(captureId)&&(out==null||!captureId.equals(out.get("captureId"))))return reply(409,requestId,"NOT_SUMMONED");combatPartner=captureId;action=ActionResult.success("PARTNER_SELECTED");
                }else if("REST".equals(kind)){
                    String captureId=identifier(params.get("captureId"));if(!scene.canRest()&&!("mudkip".equals(captureId)&&!scene.canAdvanceSafely()))return reply(409,requestId,"RESEARCH_AREA_REQUIRED");if("mudkip".equals(captureId))scene.restLeader();else{String result=collection.heal(captureId);if(!"ACCEPTED".equals(result)){if("STORAGE_UNAVAILABLE".equals(result)){freezeStorage();return reply(503,requestId,result);}return reply(409,requestId,result);}}action=ActionResult.success("POKEMON_RESTED");
                }else if("STORE_WILD".equals(kind)){
                    if(scene.wildEncounter()==null)return reply(409,requestId,"EXPLORATION_REQUIRED");String target=identifier(params.get("targetId")),captureId=scene.wildEncounter().captureId(target);if(captureId==null)return reply(409,requestId,"UNKNOWN_TARGET");
                    String result=collection.receive(captureId,scene.wildEncounter().ball(target));if(!"ACCEPTED".equals(result)){if("STORAGE_UNAVAILABLE".equals(result)){freezeStorage();return reply(503,requestId,result);}return reply(409,requestId,result);}scene.wildEncounter().transfer(target);action=ActionResult.success("WILD_STORED");
                }else{
                    if(intent!=null&&("SEQUENCE".equals(intent.getKind())||intent.isMixed())&&Arrays.asList("PAUSED","PROVIDER_UNAVAILABLE").contains(state)){
                        String operation=identifier(params.get("action"));Map<String,Object> args=Json.asObject(params.get("arguments"));TaskIntent current=scene.currentFieldStep();
                        if(Arrays.asList("attack","capture","use_skill").contains(operation)){
                            if(!current.isField())return reply(409,requestId,"STAGE_OPERATION_NOT_ALLOWED");
                            if(!current.getTargetId().equals(args.get("targetId")))return reply(409,requestId,"TARGET_NOT_IN_TASK");
                            if(!("CAPTURE".equals(current.getKind())?"capture".equals(operation):Arrays.asList("attack","use_skill").contains(operation)))return reply(409,requestId,"STAGE_OPERATION_NOT_ALLOWED");
                        }
                    }
                    action=scene.wildManual(!"mudkip".equals(combatPartner),identifier(params.get("action")),Json.asObject(params.get("arguments")));if(action.getStatus()==ActionResult.Status.SUCCESS||action.getStatus()==ActionResult.Status.IN_PROGRESS)scene.advanceTurn();
                }
                if(action.getStatus()!=ActionResult.Status.SUCCESS&&action.getStatus()!=ActionResult.Status.IN_PROGRESS)return reply(409,requestId,action.getCode());append(action,now);break;
            }
            case "FOLLOW": {
                if(Arrays.asList("PARSING","READY","RUNNING","REPLANNING","WAITING_APPROVAL").contains(state))return reply(409,requestId,"AI_CONTROLS_COMPANION");
                String captureId=identifier(params.get("captureId"));if(!(params.get("enabled") instanceof Boolean))return reply(400,requestId,"INVALID_PARAMS");
                Map<String,Object> out=collection.deployment();if(out==null||!captureId.equals(out.get("captureId")))return reply(409,requestId,"NOT_SUMMONED");
                scene.setFollowing((Boolean)params.get("enabled"));append(ActionResult.success((Boolean)params.get("enabled")?"FOLLOW_ENABLED":"FOLLOW_DISABLED"),now);break;
            }
            case "SUMMON": case "RECALL": {
                if(Arrays.asList("PARSING","READY","RUNNING","REPLANNING","WAITING_APPROVAL").contains(state))return reply(409,requestId,"AI_CONTROLS_COMPANION");
                String captureId=identifier(params.get("captureId")),result;
                if("SUMMON".equals(kind)){
                    String direction=identifier(params.get("direction"));if(!Arrays.asList("N","S","E","W").contains(direction))return reply(400,requestId,"INVALID_DIRECTION");
                    if(collection.ball(captureId)==null)return reply(409,requestId,"NOT_OWNED");
                    if(collection.deployment()!=null)return reply(409,requestId,"ALREADY_SUMMONED");
                    Map<String,Object> point=scene.summonPoint(collection.ball(captureId).getPokemon(),direction);if(point==null)return reply(409,requestId,"NO_SUMMON_SPACE");
                    result=collection.summon(captureId,point);
                }else result=collection.recall(captureId);
                if(!"ACCEPTED".equals(result)){if("STORAGE_UNAVAILABLE".equals(result)){freezeStorage();return reply(503,requestId,result);}return reply(409,requestId,result);}
                combatPartner="mudkip";synchronizeCollection();if(storageFailed)return reply(503,requestId,"STORAGE_UNAVAILABLE");
                append(ActionResult.success("SUMMON".equals(kind)?"POKEMON_SUMMONED":"POKEMON_RECALLED"),now);break;
            }
            case "SWITCH_MODE":
                if(npcAgents!=null)npcAgents.pause();
                if(!"training".equals(params.get("mode")))return reply(400,requestId,"INVALID_MODE");
                if(state.equals("PARSING")||state.equals("READY"))return reply(409,requestId,"CANCEL_BEFORE_SWITCH");
                if(loop!=null&&Arrays.asList("RUNNING","REPLANNING","WAITING_APPROVAL").contains(state))loop.pause();break;
            case "PARSE": {
                if(!provider.isAvailable()) return reply(409,requestId,"PROVIDER_CONFIGURATION");
                if(!(state.equals("IDLE")||state.equals("ERROR")) || loop!=null) return reply(409,requestId,"TASK_ACTIVE");
                if(parses>=3) return reply(429,requestId,"PARSE_LIMIT");
                Object text=params.get("text");if(!(text instanceof String) || ((String)text).trim().isEmpty() || ((String)text).length()>1000)
                    return reply(400,requestId,"INVALID_TEXT");
                String chosen=params.containsKey("captureId")?identifier(params.get("captureId")):"mudkip";
                if(!"mudkip".equals(chosen)&&collection.ball(chosen)==null)return reply(409,requestId,"NOT_OWNED");
                if(!"mudkip".equals(chosen)&&collection.deployment()!=null&&!chosen.equals(collection.deployment().get("captureId")))return reply(409,requestId,"OTHER_PARTNER_SUMMONED");
                if(scene.isCooperative()&&!"mudkip".equals(chosen))return reply(409,requestId,"OWNED_FIELD_ONLY");
                aiPartner=chosen;
                final String goal=(String)text,quest=scene.getSession().getQuestId();parses++;intent=null;errorCode=null;
                try { parsing=CompletableFuture.supplyAsync(()->provider.interpreter.parse(goal,quest),executor); }
                catch(RejectedExecutionException error) { phase="ERROR";errorCode="PROVIDER_UNAVAILABLE";revision++;return reply(503,requestId,errorCode); }
                phase="PARSING";parseDeadline=now+provider.timeoutMillis+1000L;break;
            }
            case "CONFIRM":
                if(!state.equals("READY") || intent==null) return reply(409,requestId,"NOT_READY");
                try{scene.validateIntent(intent);}catch(IllegalArgumentException invalid){return reply(409,requestId,invalid.getMessage());}
                if(!"mudkip".equals(aiPartner)){
                    if(!intent.isField()||scene.isCooperative())return reply(409,requestId,"OWNED_FIELD_ONLY");
                    if(collection.ball(aiPartner)==null)return reply(409,requestId,"NOT_OWNED");
                    Map<String,Object> point=scene.summonedState();Map<String,Object> out=collection.deployment();
                    if(out!=null&&!aiPartner.equals(out.get("captureId")))return reply(409,requestId,"OTHER_PARTNER_SUMMONED");
                    if(out==null){point=null;for(String direction:Arrays.asList("E","S","W","N")){Map<String,Object> candidate=scene.summonPoint(collection.ball(aiPartner).getPokemon(),direction);if(candidate==null)continue;try{scene.validateOwnedPartner(intent,collection.ball(aiPartner).getPokemon(),candidate);point=candidate;break;}catch(IllegalArgumentException invalid){}}
                        if(point==null)return reply(409,requestId,"NO_SUMMON_SPACE");
                    }
                    try{scene.validateOwnedPartner(intent,collection.ball(aiPartner).getPokemon(),point);}catch(IllegalArgumentException invalid){return reply(409,requestId,invalid.getMessage());}
                    if(out==null){String result=collection.summon(aiPartner,point);if(!"ACCEPTED".equals(result)){if("STORAGE_UNAVAILABLE".equals(result)){freezeStorage();return reply(503,requestId,result);}return reply(409,requestId,result);}synchronizeCollection();if(storageFailed)return reply(503,requestId,"STORAGE_UNAVAILABLE");append(ActionResult.success("AI_PARTNER_SUMMONED"),now);}
                    scene.selectAgentOwned(true);
                }else scene.selectAgentOwned(false);
                try{loop=scene.start(intent,provider.gateway,executor,provider.timeoutMillis*2L+5000);}catch(IllegalArgumentException invalid){return reply(409,requestId,invalid.getMessage());}
                if(intent.isField())combatPartner=intent.isMixed()&&!scene.currentFieldStep().isField()?"mudkip":aiPartner;loop.configureTrace(combatPartner,provider.name,provider.model);phase="ACTIVE";lastTick=now;break;
            case "PAUSE":
                if(loop==null || !(state.equals("RUNNING")||state.equals("REPLANNING")||state.equals("WAITING_APPROVAL"))) return reply(409,requestId,"NOT_RUNNING");
                loop.pause();break;
            case "RESUME":
                if(loop==null || !(state.equals("PAUSED")||state.equals("PROVIDER_UNAVAILABLE"))) return reply(409,requestId,"NOT_PAUSED");
                if(!"mudkip".equals(aiPartner)&&(scene.summonedState()==null||!aiPartner.equals(summonedSnapshot().get("captureId"))))return reply(409,requestId,"AI_PARTNER_NOT_SUMMONED");
                if(!scene.canAgentRun())return reply(409,requestId,"AREA_RESTRICTED");
                if(resumes>=3) return reply(429,requestId,"RESUME_LIMIT");if(!"mudkip".equals(aiPartner)){scene.selectAgentOwned(true);combatPartner=intent.isMixed()&&!scene.currentFieldStep().isField()?"mudkip":aiPartner;}resumes++;errorCode=null;loop.resume();lastTick=now;break;
            case "CANCEL":
                if(loop!=null && !(state.equals("COMPLETED")||state.equals("FAILED")||state.equals("CANCELLED"))) loop.cancel();
                if(parsing!=null) parsing.cancel(false);parsing=null;intent=null;
                if(loop==null) phase="CANCELLED";break;
            case "RESET":
                if(loop!=null && !(state.equals("COMPLETED")||state.equals("FAILED")||state.equals("CANCELLED"))) return reply(409,requestId,"CANCEL_BEFORE_RESET");
                if(state.equals("PARSING")||state.equals("READY")) return reply(409,requestId,"CANCEL_BEFORE_RESET");
                if(!"ACCEPTED".equals(collection.recallAll())){freezeStorage();return reply(503,requestId,"STORAGE_UNAVAILABLE");}scene.synchronizeSummoned(null,null);
                if(npcAgents!=null)npcAgents.cancel();npcAgents=null;scene=new GeminiQuestScenario(owner,original);combatPartner="mudkip";aiPartner="mudkip";loop=null;intent=null;phase="IDLE";errorCode=null;
                trace.clear();previousSteps.clear();previousMetrics=Collections.emptyMap();activeMillis=0;lastTick=-1;break;
            case "MANUAL": {
                if(state.equals("PARSING")||state.equals("READY"))return reply(409,requestId,"CANCEL_BEFORE_MANUAL");
                String action=identifier(params.get("action"));if(intent!=null&&intent.isMixed()&&scene.currentFieldStep().isField()&&Arrays.asList("pickup","deliver").contains(action))return reply(409,requestId,"STAGE_OPERATION_NOT_ALLOWED");Map<String,Object> arguments=Json.asObject(params.get("arguments"));
                ActionResult result=scene.manual(action,arguments);
                if(result.getStatus()!=ActionResult.Status.SUCCESS)return reply(409,requestId,result.getCode());
                append(result,now);scene.advanceTurn();lastTick=now;break;
            }
            case "RECOVER":
                if(store==null||!storageFailed)return reply(409,requestId,"NO_STORAGE_RECOVERY");
                try{collection.recover();Map<String,Object> checkpoint=store.load(owner);if(checkpoint==null)return reply(503,requestId,"STORAGE_UNAVAILABLE");restore(checkpoint);storageFailed=false;errorCode=null;synchronizeCollection();if(storageFailed)return reply(503,requestId,"STORAGE_UNAVAILABLE");}
                catch(RuntimeException failure){freezeStorage();return reply(503,requestId,"STORAGE_UNAVAILABLE");}break;
            case "APPROVE": case "DENY": {
                if(!state.equals("WAITING_APPROVAL")) return reply(409,requestId,"NOT_WAITING_APPROVAL");
                String proposal=identifier(params.get("proposalId"));
                ActionResult result=scene.getSession().resolveApproval(owner,proposal,kind.equals("APPROVE"));
                if(result.getStatus()!=ActionResult.Status.SUCCESS) return reply(409,requestId,result.getCode());
                append(result,now);lastTick=now;break;
            }
            default:return reply(400,requestId,"UNKNOWN_COMMAND");
        }
        revision++;return reply(200,requestId,"ACCEPTED");
    }
    public void tick(long now) {
        synchronizeCollection();
        if(storageFailed)return;
        long beforeRevision=revision;
        if(parsing!=null) {
            if(now>=parseDeadline) { parsing.cancel(false);parsing=null;phase="ERROR";errorCode="PROVIDER_TIMEOUT";revision++; }
            else if(parsing.isDone()) {
                try {
                    intent=parsing.join();
                    if(intent==null || !scene.getSession().getQuestId().equals(intent.getQuestId())) throw new ProviderException(ProviderException.Code.INVALID_RESPONSE);
                    phase="READY";
                } catch(CompletionException | ProviderException | CancellationException failure) {
                    Throwable cause=failure instanceof CompletionException?failure.getCause():failure;
                    errorCode="PROVIDER_"+(cause instanceof ProviderException?((ProviderException)cause).getCode().name():"UNAVAILABLE");phase="ERROR";intent=null;
                }
                parsing=null;revision++;
            }
        }
        if(loop==null){tickNpc(now);if(revision!=beforeRevision)persist();return;}
        String before=status();boolean runnable=before.equals("RUNNING")||before.equals("REPLANNING");
        if(runnable && !scene.canAdvanceSafely() && !(intent!=null&&intent.isField()&&scene.fieldComplete())){loop.pause();errorCode="LEADER_EXHAUSTED";revision++;lastTick=now;persist();return;}
        if(runnable) {
            if(lastTick>=0) activeMillis+=Math.max(0,now-lastTick);
            if(activeMillis>=120000) {loop.cancel();errorCode="TASK_TIME_LIMIT";revision++;lastTick=now;persist();return;}
            if(intent!=null&&intent.isMixed()){combatPartner=scene.currentFieldStep().isField()?aiPartner:"mudkip";loop.configureTrace(combatPartner,provider.name,provider.model);}
            ActionResult result=loop.tick(now);
            if(Arrays.asList("NAVIGATION_REQUIRES_HELP","NO_PROGRESS_REQUIRES_HELP").contains(result.getCode()))errorCode=result.getCode();
            if("PARTNER_EXHAUSTED".equals(result.getCode())){loop.pause();errorCode="PARTNER_EXHAUSTED";}
            if(!result.getCode().equals("DECISION_PENDING") && !result.getCode().equals("TASK_INACTIVE")) {
                append(result,now);
                if(intent!=null&&intent.isField())scene.settleFieldCompletion();
                if(intent!=null&&intent.isMixed())combatPartner=scene.currentFieldStep().isField()?aiPartner:"mudkip";
                if((result.getStatus()==ActionResult.Status.SUCCESS||result.getStatus()==ActionResult.Status.IN_PROGRESS)&&!(scene.isCooperative()&&Arrays.asList("OBSERVED","QUEST_OBSERVED","INVENTORY","DIALOGUE","NPC_DIALOGUE","GOAL_COMPLETED","APPROVAL_REQUESTED").contains(result.getCode()))&&!(intent!=null&&intent.isField()&&Arrays.asList("GOAL_COMPLETED","FIELD_OBSERVED","MIXED_OBSERVED","QUEST_OBSERVED","OBSERVED","INVENTORY","DIALOGUE","NPC_DIALOGUE","APPROVAL_REQUESTED","TARGET_ADJACENT").contains(result.getCode())))scene.advanceTurn();
                revision++;
            }
        }
        tickNpc(now);
        lastTick=now;
        if(!before.equals(status())) revision++;
        if(revision!=beforeRevision)persist();
    }
    private void tickNpc(long now){if(npcAgents==null)return;if(Arrays.asList("PARSING","READY","WAITING_APPROVAL","COMPLETED","FAILED").contains(status())){if("RUNNING".equals(npcAgents.view(scene.getSession().getTurn()).get("status"))){npcAgents.pause();revision++;}}else if(npcAgents.tick(now,scene.getSession().getTurn(),npcAgents.hasPending()?revision:revision+1))revision++;}
    private void append(ActionResult result,long now) {
        if(trace.size()==64) trace.removeFirst();
        Map<String,String> visible=new LinkedHashMap<>();for(String key:Arrays.asList("targetId","damage","targetHp","actorHp","retaliationDamage","moveId","experienceGained","levelBefore","levelAfter","x","y"))if(result.getData().containsKey(key))visible.put(key,result.getData().get(key));
        trace.addLast(Json.object("code",result.getCode(),"status",result.getStatus().name(),"data",visible,"time",now));
    }
    private String status() { return storageFailed?"STORAGE_ERROR":intent!=null&&intent.isField()&&scene.fieldTaskCompleted()?"COMPLETED":scene.getSession().getQuestStatus()==game.agent.quest.BerryQuestSession.QuestStatus.COMPLETED?"COMPLETED":scene.getSession().getQuestStatus()==game.agent.quest.BerryQuestSession.QuestStatus.EXPIRED?"FAILED":loop==null?phase:loop.getState().name(); }
    private String reportedErrorCode() {
        if("FAILED".equals(status()) && scene.getSession().getQuestStatus()==game.agent.quest.BerryQuestSession.QuestStatus.EXPIRED) return "QUEST_EXPIRED";
        if(errorCode!=null) return errorCode;
        if("FAILED".equals(status())) for(Iterator<Map<String,Object>> it=trace.descendingIterator();it.hasNext();) {Map<String,Object> row=it.next();if("FAILED".equals(row.get("status"))||"TIMED_OUT".equals(row.get("status"))) return String.valueOf(row.get("code"));}
        return null;
    }
    public Map<String,Object> snapshot() {
        synchronizeCollection();
        String before=status();Map<String,String> observation=scene.getSession().observation();
        Map<String,String> pending=scene.getSession().pendingApproval(owner);
        if(!before.equals(status())) revision++;
        Map<String,Object> world=new LinkedHashMap<>();for(String key:WORLD) if(observation.containsKey(key)) world.put(key,observation.get(key));
        List<String> constraints=new ArrayList<>();if(intent!=null) for(TaskIntent.Constraint c:intent.getConstraints()) constraints.add(c.name());
        return Json.object("roomId",id,"taskId",scene.getTaskId(),"revision",revision,"status",status(),"provider",provider.name,
            "resumeLimit",3,"resumeRemaining",Math.max(0,3-resumes),"model",provider.model,"aiAvailable",provider.isAvailable(),"errorCode",reportedErrorCode(),"goal",intent==null?null:intent.getGoal(),
            "aiPartner",aiPartner,"aiPartnerState","mudkip".equals(aiPartner)?scene.partnerState(false):collection.snapshot().stream().filter(p->aiPartner.equals(p.get("captureId"))).findFirst().orElse(null),"taskKind",intent==null?"COMPLETE_QUEST":intent.getKind(),"targetId",intent==null?null:scene.currentFieldStep()==null?(intent.isField()?intent.getSteps().get(0).getTargetId():null):scene.currentFieldStep().getTargetId(),"goalSteps",scene.goalProgress(intent),"constraints",constraints,"world",world,"delivered",scene.getDelivered(),"locations",scene.getKnownLocations(),
            "approval",pending.isEmpty()?null:new LinkedHashMap<>(pending),"trace",new ArrayList<>(trace),"dialogue",scene.getPublicDialogue(),"dialogues",scene.getPublicDialogues(),"npcs",scene.getNpcPositions(),
            "npcAgents",npcAgents==null?null:npcAgents.view(scene.getSession().getTurn()),"cooperative",scene.isCooperative(),"player",scene.playerView(),"sharedQuest",scene.sharedQuestView(),"playerAllowed",scene.isCooperative()&&!storageFailed&&scene.canAdvanceSafely()&&scene.getSession().getQuestStatus()==game.agent.quest.BerryQuestSession.QuestStatus.ACTIVE&&!Arrays.asList("PARSING","READY","WAITING_APPROVAL").contains(status()),"map",scene.getMapDescription(),"period",scene.getWorldPeriod(),"nightStarts",60,"manualAllowed",scene.getSession().getQuestStatus()==game.agent.quest.BerryQuestSession.QuestStatus.ACTIVE&&!Arrays.asList("RUNNING","REPLANNING","WAITING_APPROVAL","PARSING","READY","STORAGE_ERROR","COMPLETED").contains(status()),
            "recovered",recovered,"storage",store==null?"MEMORY":store instanceof JdbcWorldStore?"POSTGRESQL":"FILE","stepTrace",stepTrace(),"metrics",metrics(),"areaId",intent==null?null:intent.getAreaId(),"customDeadline",intent==null?null:intent.getDeadlineTurn(),"summoned",summonedSnapshot(),"wild",scene.wildStates(),"battleForbidden",scene.battleForbidden(),"combatPartner",combatPartner,"combatPartnerState",scene.partnerState(!"mudkip".equals(combatPartner)),"leaderState",scene.partnerState(false),"restAllowed",scene.canRest()||!scene.canAdvanceSafely(),"emergencyRest",!scene.canRest()&&!scene.canAdvanceSafely(),"wildActionAllowed",scene.wildEncounter()!=null&&!Arrays.asList("RUNNING","REPLANNING","WAITING_APPROVAL","PARSING","READY","STORAGE_ERROR","COMPLETED","FAILED").contains(status()),"collectionControlAllowed",!Arrays.asList("RUNNING","REPLANNING","WAITING_APPROVAL","PARSING","READY","STORAGE_ERROR").contains(status()));
    }
    private Map<String,Object> summonedSnapshot(){Map<String,Object> state=scene.summonedState();if(state!=null){Map<String,Object> out=collection.deployment();if(out!=null){state.put("captureId",out.get("captureId"));state.put("deploymentId",deploymentKey(out));}}return state;}
    private void freezeStorage(){if(npcAgents!=null)npcAgents.pause();if(loop!=null&&Arrays.asList("RUNNING","REPLANNING","WAITING_APPROVAL").contains(loop.getState().name()))loop.pause();if(!storageFailed)revision++;storageFailed=true;errorCode="STORAGE_UNAVAILABLE";}
    private static String deploymentKey(Map<String,Object> out){return (String)(out.containsKey("deploymentId")?out.get("deploymentId"):out.get("captureId"));}
    private void synchronizeCollection(){
        if(!collection.available()){freezeStorage();return;}
        try{
            Map<String,Object> out=collection.deployment();
            if(out==null){scene.synchronizeSummoned(null,null);projectedDeployment=null;restoredSummoned=null;combatPartner="mudkip";reconcileWild();return;}
            String key=deploymentKey(out);Map<String,Object> point=out;boolean follow=false;
            if(key.equals(projectedDeployment)&&scene.summonedState()!=null){point=scene.summonedState();follow=Boolean.TRUE.equals(point.get("following"));}
            else if(restoredSummoned!=null&&key.equals(restoredSummoned.get("deploymentId"))&&out.get("captureId").equals(restoredSummoned.get("captureId"))){
                Set<String> fields=new HashSet<>(Arrays.asList("captureId","deploymentId","x","y","following"));if(restoredSummoned.containsKey("hp"))fields.add("hp");if(restoredSummoned.containsKey("growth"))fields.add("growth");
                if(!restoredSummoned.keySet().equals(fields)||!(restoredSummoned.get("following") instanceof Boolean))throw new IllegalArgumentException("INVALID_SUMMONED_CHECKPOINT");
                point=restoredSummoned;follow=(Boolean)point.get("following");
            }
            scene.synchronizeSummoned(collection.ball((String)out.get("captureId")).getPokemon(),point);scene.setFollowing(follow);projectedDeployment=key;restoredSummoned=null;if(!combatPartner.equals(out.get("captureId")))combatPartner="mudkip";reconcileWild();
        }catch(RuntimeException error){freezeStorage();}
    }
    private void reconcileWild(){
        if(storageFailed||scene.wildEncounter()==null)return;
        boolean changed=false;for(String target:scene.wildEncounter().targetIds()){
            String captureId=scene.wildEncounter().captureId(target);boolean received=collection.received(captureId);
            if(scene.wildEncounter().isTransferred(target)&&!received){freezeStorage();return;}
            if(received&&!scene.wildEncounter().isTransferred(target)){scene.wildEncounter().transfer(target);changed=true;}
        }
        if(changed){revision++;persist();}
    }
    private Map<String,Object> summonedCheckpoint(){Map<String,Object> value=summonedSnapshot();if(value!=null){value.remove("species");value.remove("maxHp");}return value;}
    private List<Map<String,Object>> stepTrace(){List<Map<String,Object>> rows=new ArrayList<>(previousSteps);if(loop!=null)rows.addAll(loop.getStepTrace());return rows.size()>64?new ArrayList<>(rows.subList(rows.size()-64,rows.size())):rows;}
    private Map<String,Object> metrics(){return loop==null?previousMetrics:loop.getMetrics();}
    private Map<String,Object> checkpoint(){
        List<String> constraints=new ArrayList<>();if(intent!=null)for(TaskIntent.Constraint c:intent.getConstraints())constraints.add(c.name());
        return Json.object("schemaVersion",1,"npcAgents",npcAgents==null?null:npcAgents.checkpoint(),"aiPartner",aiPartner,"scene",scene.exportState(),"summoned",summonedCheckpoint(),"combatPartner",combatPartner,"taskState",status(),"intent",intent==null?null:Json.object("kind",intent.getKind(),"targetId",intent.getTargetId(),"steps",intent.stepDefinitions(),"constraints",constraints,"areaId",intent.getAreaId(),"deadlineTurn",intent.getDeadlineTurn()),
            "trace",new ArrayList<>(trace),"stepTrace",stepTrace(),"metrics",metrics(),"parses",parses,"resumes",resumes,"activeMillis",activeMillis,
            "pauseReason",Arrays.asList("NAVIGATION_REQUIRES_HELP","NO_PROGRESS_REQUIRES_HELP").contains(errorCode)?errorCode:null);
    }
    private boolean persist(){
        if(store==null)return true;
        try{store.save(owner,checkpoint());return true;}
        catch(RuntimeException failure){if(npcAgents!=null)npcAgents.pause();if(loop!=null && Arrays.asList("RUNNING","REPLANNING","WAITING_APPROVAL").contains(loop.getState().name()))loop.pause();storageFailed=true;errorCode="STORAGE_UNAVAILABLE";revision++;return false;}
    }
    private void restore(Map<String,Object> checkpoint){
        if(new BigDecimal(String.valueOf(checkpoint.get("schemaVersion"))).intValueExact()!=1)throw new IllegalArgumentException("INVALID_CHECKPOINT");
        scene.synchronizeSummoned(null,null);projectedDeployment=null;restoredSummoned=checkpoint.get("summoned")==null?null:Json.asObject(checkpoint.get("summoned"));scene=GeminiQuestScenario.restore(owner,Json.asObject(checkpoint.get("scene")));combatPartner="mudkip";
        if(checkpoint.get("combatPartner")!=null){String selected=identifier(checkpoint.get("combatPartner"));Map<String,Object> out=collection.deployment();if("mudkip".equals(selected)||out!=null&&selected.equals(out.get("captureId"))&&restoredSummoned!=null&&deploymentKey(out).equals(restoredSummoned.get("deploymentId")))combatPartner=selected;}
        aiPartner=checkpoint.containsKey("aiPartner")?identifier(checkpoint.get("aiPartner")):"mudkip";
        if(!"mudkip".equals(aiPartner)&&collection.ball(aiPartner)==null){if(collection.inTraining(aiPartner))aiPartner="mudkip";else throw new IllegalArgumentException("INVALID_AI_PARTNER");}
        scene.selectAgentOwned(!"mudkip".equals(aiPartner));
        loop=null;intent=null;parsing=null;
        Object savedIntent=checkpoint.get("intent");
        if(savedIntent!=null){Map<String,Object> value=Json.asObject(savedIntent);Set<TaskIntent.Constraint> constraints=EnumSet.noneOf(TaskIntent.Constraint.class);
            for(Object c:Json.asArray(value.get("constraints")))constraints.add(TaskIntent.Constraint.valueOf((String)c));
            Long deadline=value.get("deadlineTurn")==null?null:new BigDecimal(String.valueOf(value.get("deadlineTurn"))).longValueExact();
            if("SEQUENCE".equals(value.get("kind"))||"MIXED".equals(value.get("kind"))){List<Map<String,Object>> rows=new ArrayList<>();for(Object row:Json.asArray(value.get("steps")))rows.add(Json.asObject(row));intent="MIXED".equals(value.get("kind"))?TaskIntent.ofMixed(scene.getSession().getQuestId(),rows,constraints,(String)value.get("areaId"),deadline):TaskIntent.ofSequence(scene.getSession().getQuestId(),rows,constraints,(String)value.get("areaId"),deadline);}
            else intent=TaskIntent.ofGoal(scene.getSession().getQuestId(),value.containsKey("kind")?(String)value.get("kind"):"COMPLETE_QUEST",(String)value.get("targetId"),constraints,(String)value.get("areaId"),deadline);
            if("READY".equals(checkpoint.get("taskState"))){phase="READY";}else{
            loop=scene.restoreAgent(intent,provider.gateway==null?context->{throw new ProviderException(ProviderException.Code.CONFIGURATION);}:provider.gateway,executor,provider.timeoutMillis*2L+5000);
            loop.restoreProgress(Json.asObject(checkpoint.get("metrics")));
            loop.configureTrace(aiPartner,provider.name,provider.model);scene.restoreLifecycle((String)checkpoint.get("taskState"));phase="ACTIVE";
            }
        }else phase="IDLE";
        trace.clear();List<Object> oldTrace=Json.asArray(checkpoint.get("trace"));if(oldTrace.size()>64)throw new IllegalArgumentException("INVALID_CHECKPOINT");for(Object row:oldTrace)trace.add(Json.asObject(row));
        Object pauseReason=checkpoint.get("pauseReason");
        if(pauseReason!=null&&!Arrays.asList("NAVIGATION_REQUIRES_HELP","NO_PROGRESS_REQUIRES_HELP").contains(pauseReason))throw new IllegalArgumentException("INVALID_CHECKPOINT");
        errorCode="PAUSED".equals(status())?(String)pauseReason:null;
        if(errorCode==null&&"PAUSED".equals(status())&&!trace.isEmpty()){
            Object lastCode=trace.peekLast().get("code");if(Arrays.asList("NAVIGATION_REQUIRES_HELP","NO_PROGRESS_REQUIRES_HELP").contains(lastCode))errorCode=(String)lastCode;
        }
        previousSteps=new ArrayList<>();List<Object> savedSteps=Json.asArray(checkpoint.get("stepTrace"));if(savedSteps.size()>64)throw new IllegalArgumentException("INVALID_CHECKPOINT");for(Object row:savedSteps)previousSteps.add(Json.asObject(row));
        previousMetrics=Json.asObject(checkpoint.get("metrics"));parses=new BigDecimal(String.valueOf(checkpoint.get("parses"))).intValueExact();resumes=new BigDecimal(String.valueOf(checkpoint.get("resumes"))).intValueExact();
        activeMillis=new BigDecimal(String.valueOf(checkpoint.get("activeMillis"))).longValueExact();if(parses<0||parses>3||resumes<0||resumes>3||activeMillis<0||activeMillis>120000)throw new IllegalArgumentException("INVALID_CHECKPOINT");
        if(npcAgents!=null)npcAgents.cancel();npcAgents=null;if(checkpoint.get("npcAgents")!=null){if(!scene.getNpcPositions().containsKey("torchic"))throw new IllegalArgumentException("INVALID_NPC_CHECKPOINT");npcAgents=createNpcAgents();npcAgents.restore(Json.asObject(checkpoint.get("npcAgents")),scene.getSession().getQuestId());}
        recovered=true;lastTick=-1;cache.clear();revision++;
    }
    public void close() { if(npcAgents!=null)npcAgents.cancel();if(parsing!=null) parsing.cancel(false);if(loop!=null && !Arrays.asList("COMPLETED","FAILED","CANCELLED").contains(status())) loop.cancel(); }
    private static String identifier(Object value) {
        if(!(value instanceof String) || !((String)value).matches("[a-zA-Z0-9_-]{1,128}")) throw new IllegalArgumentException();
        return (String)value;
    }
    private static Reply reply(int status,String requestId,String reason) {
        return new Reply(status,Json.object("requestId",requestId,"outcome",status==200?"ACCEPTED":"REJECTED","reasonCode",reason));
    }
}
