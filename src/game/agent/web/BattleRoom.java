package game.agent.web;
import game.agent.action.ActionResult;
import game.agent.combat.*;
import game.agent.llm.*;
import game.agent.persistence.*;
import game.agent.runtime.*;
import java.util.*;
import java.util.concurrent.*;
import java.math.BigDecimal;
/** Serialized by the shared HTTP world executor. Model workers receive only immutable data. */
public final class BattleRoom implements WebRoom {
    private final String id=UUID.randomUUID().toString(),owner;
    private final ProviderSelection provider;
    private final Executor executor;
    private final WorldStore store;
    private final PokemonCollection collection;
    private BattleTrainingScenario scene=new BattleTrainingScenario();
    private BattleIntent intent;
    private AgentLoop loop;
    private CompletableFuture<BattleIntent> parsing;
    private String phase="IDLE",error;
    private long revision=1,parseDeadline,lastTick=-1,activeMillis;
    private int parses,resumes;
    private int tutorialMoves;private boolean tutorialSelected,tutorialAttacked,tutorialRested,tutorialSkipped;
    private boolean storageFailed,recovered,confirmed,hasCheckpoint;
    private final List<Map<String,Object>> trace=new ArrayList<>(),previousSteps=new ArrayList<>();
    private Map<String,Object> previousMetrics=Collections.emptyMap();
    private final Map<String,String> payloads=new LinkedHashMap<>();
    private final Map<String,AgentRoom.Reply> replies=new LinkedHashMap<>();
    public BattleRoom(String owner,ProviderSelection provider,Executor executor,WorldStore store){
        this(owner,provider,executor,store,new PokemonCollection(owner,store));
    }
    BattleRoom(String owner,ProviderSelection provider,Executor executor,WorldStore store,PokemonCollection collection){
        this.collection=collection;
        this.owner="battle:"+Objects.requireNonNull(owner);this.provider=Objects.requireNonNull(provider);this.executor=Objects.requireNonNull(executor);this.store=store;
        if(store!=null){Map<String,Object> saved=store.load(this.owner);if(saved!=null)restore(saved);else persist();}
        reconcileTransfers();
    }
    public String getId(){return id;}
    private boolean available(){return provider.gateway instanceof OpenAiGateway;}
    private String status(){return storageFailed?"STORAGE_ERROR":loop==null?phase:loop.getState().name();}
    private AgentRoom.Reply reply(int status,String request,String reason){return new AgentRoom.Reply(status,Json.object("requestId",request,"outcome",status==200?"ACCEPTED":"REJECTED","reasonCode",reason));}
    private static String identifier(Object value){if(!(value instanceof String)||!((String)value).matches("[a-zA-Z0-9_-]{1,128}"))throw new IllegalArgumentException();return (String)value;}
    public AgentRoom.Reply command(Map<String,Object> input,long now){return command(input,now,false);}
    public AgentRoom.Reply command(Map<String,Object> input,long now,boolean otherModeActive){return command(input,now,otherModeActive?"OTHER_MODE_ACTIVE":null);}
    public AgentRoom.Reply command(Map<String,Object> input,long now,String controlBlock){String request="";
        try{
            if(!input.keySet().equals(new HashSet<>(Arrays.asList("requestId","taskId","expectedRevision","command","params"))))return reply(400,request,"INVALID_FIELDS");
            request=identifier(input.get("requestId"));String task=identifier(input.get("taskId")),kind=identifier(input.get("command"));Map<String,Object> params=Json.asObject(input.get("params"));
            if(!(input.get("expectedRevision") instanceof Number))throw new IllegalArgumentException();long expected=new BigDecimal(input.get("expectedRevision").toString()).longValueExact();
            Set<String> fields="TUTORIAL".equals(kind)?Collections.singleton("action"):"PARSE".equals(kind)?Collections.singleton("text"):"MANUAL".equals(kind)?new HashSet<>(Arrays.asList("action","arguments")):"TRANSFER".equals(kind)?Collections.singleton("targetId"):"SWITCH_MODE".equals(kind)?Collections.singleton("mode"):Collections.emptySet();
            if(!params.keySet().equals(fields))return reply(400,request,"INVALID_PARAMS");
            String payload=Json.write(input);if(payloads.containsKey(request))return payloads.get(request).equals(payload)?replies.get(request):reply(409,request,"REQUEST_ID_CONFLICT");
            if(!scene.getTaskId().equals(task))return reply(409,request,"STALE_TASK");
            boolean stop="PAUSE".equals(kind)||"CANCEL".equals(kind);
            if(expected<0||expected>revision||(!stop&&expected!=revision))return reply(409,request,"STALE_REVISION");
            if(controlBlock!=null&&Arrays.asList("PARSE","CONFIRM","RESUME","MANUAL","TRANSFER","REST","RESET").contains(kind))return reply(409,request,controlBlock);
            if(payloads.size()>=256&&!"CANCEL".equals(kind))return reply(429,request,"REQUEST_LIMIT");
            if(storageFailed&&!"RECOVER".equals(kind)&&!"CANCEL".equals(kind))return reply(503,request,"STORAGE_UNAVAILABLE");
            AgentRoom.Reply result=execute(request,kind,params,now);
            if(result.status==200&&!storageFailed&&!persist())result=reply(503,request,"STORAGE_UNAVAILABLE");
            if(payloads.size()<256){payloads.put(request,payload);replies.put(request,result);}return result;
        }catch(IllegalArgumentException|ClassCastException|NullPointerException|ArithmeticException failure){return reply(400,request,"INVALID_COMMAND");}
    }
    private AgentRoom.Reply execute(String request,String kind,Map<String,Object> params,long now){String state=status();
        switch(kind){
            case "TUTORIAL":
                String tutorialAction=identifier(params.get("action"));
                if("SELECT".equals(tutorialAction))tutorialSelected=true;
                else if("SKIP".equals(tutorialAction))tutorialSkipped=true;
                else if("REPLAY".equals(tutorialAction)){tutorialSkipped=false;tutorialMoves=0;tutorialSelected=false;tutorialAttacked=false;tutorialRested=false;}
                else return reply(400,request,"INVALID_PARAMS");break;
            case "TRANSFER":
                if(!"wild-treecko".equals(params.get("targetId")))return reply(400,request,"UNKNOWN_TARGET");
                if(Arrays.asList("PARSING","READY","RUNNING","REPLANNING").contains(state))return reply(409,request,"AI_CONTROLS_COMPANION");
                if(!scene.isTransferred()&&scene.capturedBall()==null)return reply(409,request,"NOT_CAPTURED");
                String received=collection.receive(scene.getArenaId(),scene.capturedBall());
                if(!"ACCEPTED".equals(received)){if("STORAGE_UNAVAILABLE".equals(received)){storageFailed=true;error=received;revision++;return reply(503,request,received);}return reply(409,request,received);}
                if(!scene.isTransferred())scene.transferCaptured();break;
            case "SWITCH_MODE":
                if(!"quest".equals(params.get("mode")))return reply(400,request,"INVALID_MODE");
                if(Arrays.asList("PARSING","READY").contains(state))return reply(409,request,"CANCEL_BEFORE_SWITCH");
                if(loop!=null&&Arrays.asList("RUNNING","REPLANNING").contains(state))loop.pause();break;
            case "PARSE":
                if(!available())return reply(409,request,"PROVIDER_CONFIGURATION");
                if(loop!=null||!Arrays.asList("IDLE","ERROR").contains(state))return reply(409,request,"TASK_ACTIVE");
                if(parses>=3)return reply(429,request,"PARSE_LIMIT");
                Object text=params.get("text");if(!(text instanceof String)||((String)text).trim().isEmpty()||((String)text).length()>1000)return reply(400,request,"INVALID_TEXT");
                intent=null;error=null;parses++;
                try{parsing=CompletableFuture.supplyAsync(()->((OpenAiGateway)provider.gateway).parseBattle((String)text),executor);}
                catch(RejectedExecutionException failure){phase="ERROR";error="PROVIDER_UNAVAILABLE";revision++;return reply(503,request,error);}
                phase="PARSING";parseDeadline=now+provider.timeoutMillis+1000L;break;
            case "CONFIRM":
                if(!"READY".equals(state)||intent==null)return reply(409,request,"NOT_READY");
                if("0".equals(scene.observation().get("actorHp")))return reply(409,request,"ACTOR_UNAVAILABLE");
                scene.configureGoal(intent);loop=scene.start(provider.gateway,executor,intent.noBattle);loop.configureTrace("mudkip",provider.name,provider.model);confirmed=true;phase="ACTIVE";lastTick=now;break;
            case "PAUSE":
                if(loop==null||!Arrays.asList("RUNNING","REPLANNING").contains(state))return reply(409,request,"NOT_RUNNING");loop.pause();break;
            case "RESUME":
                if(loop==null||!Arrays.asList("PAUSED","PROVIDER_UNAVAILABLE").contains(state))return reply(409,request,"NOT_PAUSED");
                if(resumes>=3)return reply(429,request,"RESUME_LIMIT");resumes++;loop.resume();lastTick=now;break;
            case "CANCEL":
                close();if(loop==null&&!Arrays.asList("COMPLETED","FAILED","CANCELLED").contains(state))phase="CANCELLED";intent=confirmed?intent:null;break;
            case "REST":
                if(Arrays.asList("PARSING","READY","RUNNING","REPLANNING").contains(state))return reply(409,request,"AI_CONTROLS_COMPANION");
                scene.rest();tutorialRested=true;append(ActionResult.success("TRAINING_RESTED"),now);break;
            case "RESET":
                if(!Arrays.asList("IDLE","ERROR","COMPLETED","FAILED","CANCELLED").contains(state))return reply(409,request,"CANCEL_BEFORE_RESET");
                if(scene.capturedBall()!=null)return reply(409,request,"COLLECT_BEFORE_RESET");
                close();scene=new BattleTrainingScenario();loop=null;intent=null;confirmed=false;phase="IDLE";error=null;trace.clear();previousSteps.clear();previousMetrics=Collections.emptyMap();parses=0;resumes=0;activeMillis=0;lastTick=-1;break;
            case "MANUAL":
                if(Arrays.asList("PARSING","READY","RUNNING","REPLANNING").contains(state))return reply(409,request,"AI_CONTROLS_COMPANION");
                String action=identifier(params.get("action"));Map<String,Object> args=Json.asObject(params.get("arguments"));String field="move".equals(action)?"direction":"targetId";
                if(!Arrays.asList("move","approach","attack","capture").contains(action)||!args.keySet().equals(Collections.singleton(field)))return reply(400,request,"INVALID_PARAMS");
                ActionResult result=scene.manual(action,identifier(args.get(field)));append(result,now);
                if(result.getStatus()==ActionResult.Status.SUCCESS||result.getStatus()==ActionResult.Status.IN_PROGRESS){
                    if("move".equals(action))tutorialMoves=Math.min(2,tutorialMoves+1);
                    if("attack".equals(action)){tutorialAttacked=true;tutorialRested=false;}
                }
                if(result.getStatus()!=ActionResult.Status.SUCCESS&&result.getStatus()!=ActionResult.Status.IN_PROGRESS){revision++;return reply(409,request,result.getCode());}break;
            case "RECOVER":
                if(!storageFailed||store==null)return reply(409,request,"NO_STORAGE_RECOVERY");
                try{collection.recover();Map<String,Object> saved=store.load(owner);if(saved==null){if(hasCheckpoint)return reply(503,request,"STORAGE_UNAVAILABLE");close();scene=new BattleTrainingScenario();loop=null;intent=null;confirmed=false;phase="IDLE";}else restore(saved);storageFailed=false;error=null;reconcileTransfers();}
                catch(RuntimeException failure){storageFailed=true;error="STORAGE_UNAVAILABLE";revision++;return reply(503,request,"STORAGE_UNAVAILABLE");}
                if(storageFailed)return reply(503,request,"STORAGE_UNAVAILABLE");break;
            default:return reply(400,request,"UNKNOWN_COMMAND");
        }
        revision++;return reply(200,request,"ACCEPTED");
    }
    public void tick(long now){if(storageFailed)return;long before=revision;
        if(parsing!=null){
            if(now>=parseDeadline){parsing.cancel(false);parsing=null;phase="ERROR";error="PROVIDER_TIMEOUT";revision++;}
            else if(parsing.isDone()){try{intent=Objects.requireNonNull(parsing.join());phase="READY";}
                catch(RuntimeException failure){Throwable cause=failure instanceof CompletionException?failure.getCause():failure;error="PROVIDER_"+(cause instanceof ProviderException?((ProviderException)cause).getCode().name():"INVALID_RESPONSE");phase="ERROR";intent=null;}
                parsing=null;revision++;}}
        if(loop!=null){String state=status();if(Arrays.asList("RUNNING","REPLANNING").contains(state)){
            if(lastTick>=0)activeMillis=Math.min(120000,activeMillis+Math.max(0,now-lastTick));
            if(activeMillis>=120000){loop.cancel();error="TASK_TIME_LIMIT";revision++;}
            else{ActionResult result=loop.tick(now);if(!Arrays.asList("DECISION_PENDING","TASK_INACTIVE").contains(result.getCode())){scene.advance(result);append(result,now);revision++;}if(!state.equals(status()))revision++;}}
            lastTick=now;}
        if(before!=revision)persist();
    }
    private void append(ActionResult result,long now){if(trace.size()==64)trace.remove(0);trace.add(Json.object("code",result.getCode(),"status",result.getStatus().name(),"data",result.getData(),"time",now));}
    private List<Map<String,Object>> steps(){List<Map<String,Object>> rows=new ArrayList<>(previousSteps);if(loop!=null)rows.addAll(loop.getStepTrace());return rows.size()>64?new ArrayList<>(rows.subList(rows.size()-64,rows.size())):rows;}
    private Map<String,Object> metrics(){return loop==null?previousMetrics:loop.getMetrics();}
    void reconcileTransfers(){if(!collection.available())return;if(collection.received(scene.getArenaId())&&!scene.isTransferred()){scene.transferCaptured();revision++;persist();}if(scene.isTransferred()&&!collection.received(scene.getArenaId()))throw new IllegalStateException("COLLECTION_OWNERSHIP_MISSING");}
    private Map<String,Object> tutorial(){return Json.object("moves",tutorialMoves,"selected",tutorialSelected,"attacked",tutorialAttacked,"rested",tutorialRested,"skipped",tutorialSkipped);}
    public Map<String,Object> snapshot(){Map<String,String> observation=scene.observation();return Json.object("roomId",id,"taskId",scene.getTaskId(),"revision",revision,"status",status(),"mode","BATTLE_TRAINING","tutorial",tutorial(),
        "provider",provider.name,"model",provider.model,"aiAvailable",available(),"errorCode",error,"intent",intent==null?null:intent.encode(),"world",observation,
        "targets",Json.read(observation.get("targets")),"captured",Json.read(observation.get("captured")),"goalComplete",scene.isComplete(),"trace",new ArrayList<>(trace),"stepTrace",steps(),"metrics",metrics(),
        "manualAllowed",!Arrays.asList("RUNNING","REPLANNING","PARSING","READY","STORAGE_ERROR").contains(status()),"recovered",recovered,"storage",store==null?"MEMORY":"PERSISTENT");}
    private boolean persist(){if(store==null)return true;try{store.save(owner,Json.object("schemaVersion",1,"tutorial",tutorial(),"scene",scene.exportState(),"intent",intent==null?null:intent.encode(),"confirmed",confirmed,"roomState",status(),"trace",new ArrayList<>(trace),"steps",steps(),"metrics",metrics(),"parses",parses,"resumes",resumes,"activeMillis",activeMillis));hasCheckpoint=true;return true;}
        catch(RuntimeException failure){if(loop!=null&&Arrays.asList("RUNNING","REPLANNING").contains(loop.getState().name()))loop.pause();storageFailed=true;error="STORAGE_UNAVAILABLE";revision++;return false;}}
    private static long number(Object value){if(!(value instanceof Number))throw new IllegalArgumentException("INVALID_CHECKPOINT");return new BigDecimal(value.toString()).longValueExact();}
    private void restore(Map<String,Object> saved){
        if(number(saved.get("schemaVersion"))!=1||!(saved.get("confirmed") instanceof Boolean)||!(saved.get("roomState") instanceof String))throw new IllegalArgumentException("INVALID_CHECKPOINT");
        if(saved.containsKey("tutorial")){
            Map<String,Object> t=Json.asObject(saved.get("tutorial"));long moves=number(t.get("moves"));
            if(moves<0||moves>2||!t.keySet().equals(new HashSet<>(Arrays.asList("moves","selected","attacked","rested","skipped"))))throw new IllegalArgumentException("INVALID_CHECKPOINT");
            for(String key:Arrays.asList("selected","attacked","rested","skipped"))if(!(t.get(key) instanceof Boolean))throw new IllegalArgumentException("INVALID_CHECKPOINT");
            tutorialMoves=(int)moves;tutorialSelected=(Boolean)t.get("selected");tutorialAttacked=(Boolean)t.get("attacked");tutorialRested=(Boolean)t.get("rested");tutorialSkipped=(Boolean)t.get("skipped");
        }
        String savedStatus=(String)saved.get("roomState");if(!Arrays.asList("IDLE","PARSING","READY","ERROR","RUNNING","REPLANNING","PAUSED","PROVIDER_UNAVAILABLE","COMPLETED","FAILED","CANCELLED").contains(savedStatus))throw new IllegalArgumentException("INVALID_CHECKPOINT");
        BattleTrainingScenario restored=BattleTrainingScenario.restore(Json.asObject(saved.get("scene")));
        BattleIntent restoredIntent=saved.get("intent")==null?null:BattleIntent.decode(Json.asObject(saved.get("intent")));
        List<Object> oldTrace=Json.asArray(saved.get("trace")),oldSteps=Json.asArray(saved.get("steps"));if(oldTrace.size()>64||oldSteps.size()>64)throw new IllegalArgumentException("INVALID_CHECKPOINT");
        long p=number(saved.get("parses")),r=number(saved.get("resumes")),a=number(saved.get("activeMillis"));if(p<0||p>3||r<0||r>3||a<0||a>120000)throw new IllegalArgumentException("INVALID_CHECKPOINT");
        Map<String,Object> savedMetrics=Json.asObject(saved.get("metrics"));
        if((Boolean)saved.get("confirmed")&&restoredIntent==null)throw new IllegalArgumentException("INVALID_CHECKPOINT");
        close();scene=restored;confirmed=(Boolean)saved.get("confirmed");intent=confirmed?restoredIntent:null;loop=null;phase=Arrays.asList("COMPLETED","FAILED","CANCELLED").contains(savedStatus)?savedStatus:scene.isComplete()?"COMPLETED":"IDLE";
        if(intent!=null){
            if(!intent.goal.equals(scene.observation().get("goalType"))||!intent.targetId.equals(scene.observation().get("goalTarget")))throw new IllegalArgumentException("INVALID_CHECKPOINT");
            String state=scene.observation().get("taskState");
            if(!Arrays.asList("COMPLETED","FAILED","CANCELLED").contains(phase)&&"PAUSED".equals(state)){loop=scene.start(provider.gateway==null?c->{throw new ProviderException(ProviderException.Code.CONFIGURATION);}:provider.gateway,executor,intent.noBattle);loop.pause();loop.restoreProgress(savedMetrics);loop.configureTrace("mudkip",provider.name,provider.model);}
            else if(!Arrays.asList("FAILED","CANCELLED").contains(phase))phase=state;
        }
        trace.clear();previousSteps.clear();for(Object row:oldTrace)trace.add(Json.asObject(row));for(Object row:oldSteps)previousSteps.add(Json.asObject(row));previousMetrics=savedMetrics;
        parses=(int)p;resumes=(int)r;activeMillis=a;lastTick=-1;parsing=null;recovered=true;hasCheckpoint=true;payloads.clear();replies.clear();revision++;
    }
    public void close(){if(parsing!=null)parsing.cancel(false);parsing=null;if(loop!=null&&!Arrays.asList("COMPLETED","FAILED","CANCELLED").contains(loop.getState().name()))loop.cancel();}
}
