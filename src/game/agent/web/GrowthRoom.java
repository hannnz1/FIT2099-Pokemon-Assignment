package game.agent.web;

import game.agent.growth.*;
import game.agent.action.ActionResult;
import game.agent.llm.Json;
import game.agent.persistence.WorldStore;
import game.agent.runtime.*;
import game.agent.tools.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntSupplier;

/** One owner checkpoint includes the native world, rewards, AI budget and command receipts. */
public final class GrowthRoom implements WebRoom {
    private final String id=UUID.randomUUID().toString(),owner;
    private final ProviderSelection provider; private final Executor executor; private final WorldStore store;
    private final IntSupplier random;
    private FirstAdventure firstAdventure=new FirstAdventure();private boolean tutorialSkillUsed;private GrowthWorld world;private final PokemonCollection collection;
    private String taskId=UUID.randomUUID().toString(),phase="IDLE",error,storageFailureCode;
    private Map<String,Object> storageFailureDetails=Collections.emptyMap();
    private long lastSavedRevision;
    private long revision=1,lastTick=-1,activeMillis,lastSavedActive;
    private int targetLevel;
    private String targetIndividual,targetRegion;
    private AgentLoop loop;
    private boolean storageFailed,recovered;
    private final List<Map<String,Object>> trace=new ArrayList<>(),oldSteps=new ArrayList<>();
    private Map<String,Object> oldMetrics=Collections.emptyMap();
    private final Map<String,String> payloads=new LinkedHashMap<>();
    private final Map<String,AgentRoom.Reply> replies=new LinkedHashMap<>();
    public GrowthRoom(String owner,ProviderSelection provider,Executor executor,WorldStore store){this(owner,provider,executor,store,()->ThreadLocalRandom.current().nextInt());}
    GrowthRoom(String owner,ProviderSelection provider,Executor executor,WorldStore store,IntSupplier random){this(owner,provider,executor,store,random,new PokemonCollection(owner,store));}
    GrowthRoom(String owner,ProviderSelection provider,Executor executor,WorldStore store,PokemonCollection collection){this(owner,provider,executor,store,()->ThreadLocalRandom.current().nextInt(),collection);}
    private GrowthRoom(String owner,ProviderSelection provider,Executor executor,WorldStore store,IntSupplier random,PokemonCollection collection){
        this.collection=collection;
        this.owner="growth:"+Objects.requireNonNull(owner);this.provider=Objects.requireNonNull(provider);this.executor=Objects.requireNonNull(executor);this.store=store;this.random=random;world=new GrowthWorld(random);
        if(store!=null){Map<String,Object> saved=store.load(this.owner);if(saved!=null){restore(saved);reconcileTransfers();}else persist();}
    }
    public String getId(){return id;}
    private String status(){return storageFailed?"STORAGE_ERROR":loop==null?phase:loop.getState().name();}
    private boolean running(){return Arrays.asList("RUNNING","REPLANNING").contains(status());}
    private AgentRoom.Reply reply(int code,String request,String reason){return new AgentRoom.Reply(code,Json.object("requestId",request,"outcome",code==200?"ACCEPTED":"REJECTED","reasonCode",reason));}
    private static String identifier(Object o){if(!(o instanceof String)||!((String)o).matches("[a-zA-Z0-9_-]{1,128}"))throw new IllegalArgumentException();return (String)o;}
    public AgentRoom.Reply command(Map<String,Object> input,long now){return command(input,now,(String)null);}
    public AgentRoom.Reply command(Map<String,Object> input,long now,boolean other){return command(input,now,other?"OTHER_MODE_ACTIVE":null);}
    public AgentRoom.Reply command(Map<String,Object> input,long now,String block){String request="";
        try{
            if(!input.keySet().equals(new HashSet<>(Arrays.asList("requestId","taskId","expectedRevision","command","params"))))return reply(400,request,"INVALID_FIELDS");
            request=identifier(input.get("requestId"));String task=identifier(input.get("taskId")),kind=identifier(input.get("command"));long expected=number(input.get("expectedRevision"));Map<String,Object> params=Json.asObject(input.get("params"));
            boolean stop=Arrays.asList("PAUSE","CANCEL").contains(kind);
            if(storageFailed&&!Arrays.asList("RECOVER","CANCEL").contains(kind))return reply(503,request,"STORAGE_UNAVAILABLE");
            String payload=Json.write(input);if(payloads.containsKey(request))return payload.equals(payloads.get(request))?replies.get(request):reply(409,request,"REQUEST_ID_CONFLICT");
            if(!taskId.equals(task))return reply(409,request,"STALE_TASK");
            if(expected<1||expected>revision||!stop&&expected!=revision)return reply(409,request,"STALE_REVISION");
            if(block!=null&&!Arrays.asList("PAUSE","CANCEL","RECOVER").contains(kind))return reply(409,request,block);
            Set<String> fields=new HashSet<>();
            switch(kind){case "PRACTICE_SKILL":fields.add("moveId");break;case "STARTER":fields.add("species");break;case "RETURN_GROWTH":case "SELECT":fields.add("captureId");break;case "LEARN":fields.addAll(Arrays.asList("moveId","replaceId"));break;case "CLAIM":fields.add("milestoneId");break;case "AI_TRAIN":fields.add("targetLevel");if(params.containsKey("regionId"))fields.add("regionId");break;case "MANUAL":fields.addAll(Arrays.asList("action","arguments"));break;default:break;}
            if(!params.keySet().equals(fields))return reply(400,request,"INVALID_PARAMS");
            if(running()&&!stop)return reply(409,request,"AI_CONTROLS_COMPANION");
            Map<String,Object> storyBefore=world.view();ActionResult action=null;
            switch(kind){
                case "START_ADVENTURE":if(Arrays.asList("PAUSED","PROVIDER_UNAVAILABLE").contains(status()))return reply(409,request,"TRAINING_TASK_PENDING");action=world.startAdventure();if(action.getStatus()==ActionResult.Status.SUCCESS)clearTraining();break;
                case "PRACTICE_SELECT":case "STORY_CONTINUE":case "STORY_FINISH":case "STORY_SKIP":action=firstAdventure.action(kind,null,world);break;
                case "PRACTICE_SKILL":action=firstAdventure.action(kind,identifier(params.get("moveId")),world);break;
                case "STARTER":action=world.starter(identifier(params.get("species")));break;
                case "SELECT":action=world.select(identifier(params.get("captureId")));break;
                case "RETURN_GROWTH":
                    if(!"lab".equals(world.region()))return reply(409,request,"LAB_REQUIRED");
                    if(Json.asArray(world.view().get("team")).size()>=20)return reply(409,request,"TEAM_FULL");
                    String returning=identifier(params.get("captureId"));String returnCode=collection.returnToGrowth(returning);
                    if(!"ACCEPTED".equals(returnCode)){if("STORAGE_UNAVAILABLE".equals(returnCode)){storageFailed=true;error=returnCode;return reply(503,request,returnCode);}return reply(409,request,returnCode);}
                    for(Map<String,Object> profile:collection.trainingProfiles())if(returning.equals(profile.get("captureId")))world.acceptTraining(profile);
                    clearTraining();action=ActionResult.success("GROWTH_RETURNED");break;
                case "CARRY":
                    if(!"lab".equals(world.region()))return reply(409,request,"LAB_REQUIRED");
                    if(world.partner()==null)return reply(409,request,"CHOOSE_STARTER");
                    if(!world.partner().isConscious())return reply(409,request,"REST_REQUIRED");
                    String carried=world.partner().captureId;
                    String resultCode=collection.receive(carried,new game.items.balls.Pokeball().capturePokemon(world.partner()));
                    if(!"ACCEPTED".equals(resultCode)){if("STORAGE_UNAVAILABLE".equals(resultCode)){storageFailed=true;error=resultCode;return reply(503,request,resultCode);}return reply(409,request,resultCode);}
                    clearTraining();world.withdraw(carried);action=ActionResult.success("GROWTH_CARRIED");break;
                case "REST":if(world.partner()==null)return reply(409,request,"CHOOSE_STARTER");action=world.rest();break;
                case "EVOLVE":action=world.evolve();break;
                case "LEARN":action=world.learn(identifier(params.get("moveId")),params.get("replaceId")==null?null:identifier(params.get("replaceId")));break;
                case "CLAIM":action=world.claim(identifier(params.get("milestoneId")));break;
                case "NEXT_EXPEDITION":action=world.nextExpedition();break;
                case "MANUAL":String tool=identifier(params.get("action"));Map<String,Object> args=Json.asObject(params.get("arguments"));
                    if("move".equals(tool)){if(!args.keySet().equals(Collections.singleton("direction")))return reply(400,request,"INVALID_PARAMS");action=world.move(identifier(args.get("direction")));}
                    else if("capture".equals(tool)&&"forest".equals(world.region())&&firstAdventure.needsCapture()&&world.partner()!=null&&world.partner().captureId.equals(firstAdventure.starterId())){if(!args.keySet().equals(Collections.singleton("targetId")))return reply(400,request,"INVALID_PARAMS");action=world.firstAdventureCapture(identifier(args.get("targetId")),(String)firstAdventure.view().get("species"));}
                    else action=world.tools().execute(new ToolRequest(request,tool,args));break;
                case "AI_TRAIN":
                    if(provider.gateway==null)return reply(409,request,"PROVIDER_CONFIGURATION");
                    if(world.partner()==null)return reply(409,request,"CHOOSE_STARTER");
                    int level=Math.toIntExact(number(params.get("targetLevel")));if(level<=world.partner().level()||level>GrowthRules.LEVEL_CAP)return reply(400,request,"INVALID_TARGET_LEVEL");
                    if(params.containsKey("regionId")){String trainingRegion=identifier(params.get("regionId"));if(!GrowthMaps.wild(trainingRegion))return reply(400,request,"UNKNOWN_REGION");if(!world.regionUnlocked(trainingRegion))return reply(409,request,"REGION_LOCKED");world.chooseExpedition(trainingRegion);}
                    closeLoop();taskId=UUID.randomUUID().toString();oldSteps.clear();oldMetrics=Collections.emptyMap();activeMillis=0;targetLevel=level;targetIndividual=world.partner().captureId;targetRegion=world.expeditionRegion();error=null;startLoop();lastTick=now;break;
                case "PAUSE":if(!running())return reply(409,request,"NOT_RUNNING");loop.pause();break;
                case "RESUME":if(!Arrays.asList("PAUSED","PROVIDER_UNAVAILABLE").contains(status()))return reply(409,request,"NOT_PAUSED");if(provider.gateway==null)return reply(409,request,"PROVIDER_CONFIGURATION");if(activeMillis>=600000)return reply(409,request,"TASK_TIME_LIMIT");if(world.partner()==null||!targetIndividual.equals(world.partner().captureId))return reply(409,request,"TRAINING_PARTNER_CHANGED");world.chooseExpedition(targetRegion);if(loop==null)startLoop();else loop.resume();lastTick=now;break;
                case "CANCEL":closeLoop();phase="CANCELLED";if(storageFailed){revision++;return reply(200,request,"CANCELLED");}break;
                case "RECOVER":if(!storageFailed||store==null)return reply(409,request,"NO_STORAGE_RECOVERY");try{Map<String,Object> saved=store.load(owner);if(saved==null)return reply(503,request,"STORAGE_UNAVAILABLE");collection.recover();restore(saved);storageFailed=false;error=null;storageFailureCode=null;storageFailureDetails=Collections.emptyMap();reconcileTransfers();if(storageFailed)return reply(503,request,"STORAGE_UNAVAILABLE");}catch(RuntimeException failure){return reply(503,request,"STORAGE_UNAVAILABLE");}break;
                default:return reply(400,request,"UNKNOWN_COMMAND");
            }
            if(action!=null){firstAdventure.observe(world,storyBefore,action);append(action,now);revision++;if(action.getStatus()!=ActionResult.Status.SUCCESS&&action.getStatus()!=ActionResult.Status.IN_PROGRESS)return reply(409,request,action.getCode());}
            else revision++;
            AgentRoom.Reply result=reply(200,request,action==null?"ACCEPTED":action.getCode());
            if(payloads.size()>=64){String expired=payloads.keySet().iterator().next();payloads.remove(expired);replies.remove(expired);}payloads.put(request,payload);replies.put(request,result);
            if(!persist())return reply(503,request,"STORAGE_UNAVAILABLE");return result;
        }catch(IllegalArgumentException|ClassCastException|NullPointerException|ArithmeticException failure){return reply(400,request,"INVALID_COMMAND");}
    }
    private void clearTraining(){closeLoop();taskId=UUID.randomUUID().toString();phase="IDLE";targetLevel=0;targetIndividual=null;targetRegion=null;activeMillis=0;oldSteps.clear();oldMetrics=Collections.emptyMap();}
    private void reconcileTransfers(){boolean changed=false;for(Object row:game.agent.llm.Json.asArray(world.view().get("team"))){String captureId=(String)Json.asObject(row).get("captureId");if(collection.inCollection(captureId)){clearTraining();world.withdraw(captureId);changed=true;}}for(Map<String,Object> profile:collection.trainingProfiles()){boolean exists=false;for(Object row:Json.asArray(world.view().get("team")))if(profile.get("captureId").equals(Json.asObject(row).get("captureId")))exists=true;if(!exists){clearTraining();world.acceptTraining(profile);changed=true;}}if(changed){revision++;persist();}}
    private void startLoop(){
        AgentTask task=new AgentTask(taskId);task.start();
        loop=new AgentLoop(task,"Train selected individual "+targetIndividual+" to level "+targetLevel+" through native battles in the selected region "+targetRegion+". The allowed training destination is fixed for this task. Follow connected region gates to reach it. Do not capture or change the selected individual. When HP/PP is low, walk to the region recovery point using go_to_recovery, then rest when canRest is true; lab also allows rest. Stop when level reached.",world.trainingTools(targetRegion),executor,provider.gateway,world::observation,()->world.partner()!=null&&targetIndividual.equals(world.partner().captureId)&&world.partner().level()>=targetLevel&&world.region().equals(targetRegion),provider.timeoutMillis+1000L,200,100,8);
        loop.configureTrace(targetIndividual,provider.name,provider.model);if(!oldMetrics.isEmpty())loop.restoreProgress(oldMetrics);phase="RUNNING";
    }
    public void tick(long now){if(storageFailed||!running())return;String before=status();if(lastTick>=0)activeMillis=Math.min(600000,activeMillis+Math.max(0,now-lastTick));lastTick=now;
        if(activeMillis>=600000){closeLoop();phase="FAILED";error="TASK_TIME_LIMIT";revision++;persist();return;}
        long callsBefore=((Number)loop.getMetrics().get("decisionCount")).longValue();ActionResult result=loop.tick(now);if(!Arrays.asList("DECISION_PENDING","TASK_INACTIVE").contains(result.getCode())){append(result,now);revision++;persist();}else if(!before.equals(status())){revision++;persist();}else if(((Number)loop.getMetrics().get("decisionCount")).longValue()!=callsBefore||activeMillis-lastSavedActive>=1000){persist();}
    }
    private void append(ActionResult action,long now){if(action.getStatus()==ActionResult.Status.SUCCESS&&action.getData().containsKey("moveId"))tutorialSkillUsed=true;if(trace.size()>=64)trace.remove(0);trace.add(Json.object("code",action.getCode(),"status",action.getStatus().name(),"data",action.getData(),"time",now));}
    private List<Map<String,Object>> steps(){List<Map<String,Object>> rows=new ArrayList<>(oldSteps);if(loop!=null)rows.addAll(loop.getStepTrace());return rows.size()>64?new ArrayList<>(rows.subList(rows.size()-64,rows.size())):rows;}
    private Map<String,Object> metrics(){return loop==null?oldMetrics:loop.getMetrics();}
    public void recordTrainerVictory(Map<String,Object> duel){if(storageFailed||!"COMPLETED".equals(duel.get("status")))return;Map<String,Object> battle=Json.asObject(duel.get("world"));if(!"LEFT_WON".equals(battle.get("result")))return;for(Object row:Json.asArray(battle.get("ownTeam"))){String individual=(String)Json.asObject(row).get("captureId");if(firstAdventure.trainerVictory(individual,(String)duel.get("difficulty"))){revision++;persist();break;}}}
    public Map<String,Object> snapshot(){return Json.object("roomId",id,"taskId",taskId,"revision",revision,"status",status(),"mode","GROWTH","tutorialSkillUsed",tutorialSkillUsed,"firstAdventure",firstAdventure.view(),"world",world.view(),"collection",collection.snapshot(),"collectionAvailable",collection.available(),"trace",new ArrayList<>(trace),"stepTrace",steps(),"metrics",metrics(),"provider",provider.name,"model",provider.model,"aiAvailable",provider.gateway!=null,"manualAllowed",!running()&&!storageFailed,"errorCode",error,"storageFailureCode",storageFailureCode,"storageFailureDetails",new LinkedHashMap<>(storageFailureDetails),"targetLevel",targetLevel,"targetIndividual",targetIndividual,"targetRegion",targetRegion,"storage",store==null?"MEMORY":"PERSISTENT","recovered",recovered);}
    private boolean persist(){if(store==null)return true;Map<String,Object> checkpoint=null;try{
        List<Object> receipts=new ArrayList<>();for(String request:payloads.keySet())receipts.add(Json.object("id",request,"payload",payloads.get(request),"status",replies.get(request).status,"body",replies.get(request).body));
        checkpoint=Json.object("schemaVersion",1,"tutorialSkillUsed",tutorialSkillUsed,"firstAdventure",firstAdventure.view(),"world",world.export(),"taskId",taskId,"revision",revision,"phase",status(),"targetLevel",targetLevel,"targetIndividual",targetIndividual,"targetRegion",targetRegion,"activeMillis",activeMillis,"trace",new ArrayList<>(trace),"steps",steps(),"metrics",metrics(),"receipts",receipts);store.save(owner,checkpoint);lastSavedRevision=revision;lastSavedActive=activeMillis;return true;
    }catch(RuntimeException failure){if(running())loop.pause();storageFailed=true;error="STORAGE_UNAVAILABLE";storageFailureCode=failure instanceof game.agent.persistence.StoreException?((game.agent.persistence.StoreException)failure).getCode().name():"UNKNOWN";storageFailureDetails=saveFailureDetails(checkpoint);System.err.println("GROWTH_SAVE_FAILED "+Json.write(storageFailureDetails));revision++;return false;}}
    // Only bounded numeric metadata and finite error codes enter logs; never owner IDs, keys or payloads.
    private Map<String,Object> saveFailureDetails(Map<String,Object> checkpoint){
        long nodes=-1,bytes=-1;int receipts=-1;
        if(checkpoint!=null){nodes=countCheckpointNodes(checkpoint);receipts=Json.asArray(checkpoint.get("receipts")).size();try{bytes=Json.write(checkpoint).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;}catch(RuntimeException ignored){}}
        return Json.object("code",storageFailureCode,"revision",revision,"lastSavedRevision",lastSavedRevision,"checkpointNodes",nodes,"checkpointBytes",bytes,"receiptCount",receipts,"partnerLevel",world.partner()==null?0:world.partner().level());
    }
    private static long countCheckpointNodes(Object value){long count=1;if(value instanceof Map)for(Object child:((Map<?,?>)value).values())count+=1+countCheckpointNodes(child);else if(value instanceof List)for(Object child:(List<?>)value)count+=countCheckpointNodes(child);return count;}
    private static long number(Object n){if(!(n instanceof Number))throw new IllegalArgumentException();return new java.math.BigDecimal(n.toString()).longValueExact();}
    private void restore(Map<String,Object> saved){
        Set<String> fields=new HashSet<>(Arrays.asList("schemaVersion","world","taskId","revision","phase","targetLevel","targetIndividual","activeMillis","trace","steps","metrics","receipts"));if(saved.containsKey("firstAdventure"))fields.add("firstAdventure");if(saved.containsKey("tutorialSkillUsed"))fields.add("tutorialSkillUsed");if(saved.containsKey("targetRegion"))fields.add("targetRegion");if(!saved.keySet().equals(fields)||number(saved.get("schemaVersion"))!=1)throw new IllegalArgumentException("INVALID_GROWTH_CHECKPOINT");
        if(saved.containsKey("tutorialSkillUsed")&&!(saved.get("tutorialSkillUsed") instanceof Boolean))throw new IllegalArgumentException("INVALID_GROWTH_CHECKPOINT");
        firstAdventure=saved.containsKey("firstAdventure")?FirstAdventure.restore(Json.asObject(saved.get("firstAdventure"))):FirstAdventure.legacy();tutorialSkillUsed=Boolean.TRUE.equals(saved.get("tutorialSkillUsed"));
        GrowthWorld restored=GrowthWorld.restore(Json.asObject(saved.get("world")),random);String savedTask=identifier(saved.get("taskId")),savedPhase=identifier(saved.get("phase"));long rev=number(saved.get("revision")),active=number(saved.get("activeMillis"));int goal=(int)number(saved.get("targetLevel"));
        if(rev<1||active<0||active>600000||goal<0||goal>GrowthRules.LEVEL_CAP||!Arrays.asList("IDLE","RUNNING","REPLANNING","PAUSED","PROVIDER_UNAVAILABLE","COMPLETED","FAILED","CANCELLED").contains(savedPhase))throw new IllegalArgumentException("INVALID_GROWTH_CHECKPOINT");
        List<Object> logs=Json.asArray(saved.get("trace")),steps=Json.asArray(saved.get("steps")),receipts=Json.asArray(saved.get("receipts"));if(logs.size()>64||steps.size()>64||receipts.size()>1024)throw new IllegalArgumentException("INVALID_GROWTH_CHECKPOINT");
        String savedRegion=saved.containsKey("targetRegion")?(saved.get("targetRegion")==null?null:identifier(saved.get("targetRegion"))):(goal>0?restored.expeditionRegion():null);if(goal>0?!GrowthMaps.wild(savedRegion):savedRegion!=null)throw new IllegalArgumentException("INVALID_TRAINING_REGION");String individual=saved.get("targetIndividual")==null?null:identifier(saved.get("targetIndividual"));if(goal>0&&(restored.partner()==null||individual==null))throw new IllegalArgumentException("INVALID_GROWTH_CHECKPOINT");
        closeLoop();world=restored;taskId=savedTask;revision=rev+1;phase=Arrays.asList("RUNNING","REPLANNING","PROVIDER_UNAVAILABLE").contains(savedPhase)?"PAUSED":savedPhase;targetLevel=goal;targetIndividual=individual;targetRegion=savedRegion;activeMillis=active;lastSavedActive=active;lastSavedRevision=rev;lastTick=-1;
        trace.clear();for(Object row:logs)trace.add(Json.asObject(row));oldSteps.clear();for(Object row:steps)oldSteps.add(Json.asObject(row));oldMetrics=Json.asObject(saved.get("metrics"));payloads.clear();replies.clear();
        for(Object row:receipts){Map<String,Object> v=Json.asObject(row);String request=identifier(v.get("id"));if(payloads.put(request,(String)v.get("payload"))!=null)throw new IllegalArgumentException("DUPLICATE_RECEIPT");replies.put(request,new AgentRoom.Reply((int)number(v.get("status")),Json.asObject(v.get("body"))));}while(payloads.size()>64){String expired=payloads.keySet().iterator().next();payloads.remove(expired);replies.remove(expired);}recovered=true;
    }
    private void closeLoop(){if(loop!=null){String state=loop.getState().name();if(!Arrays.asList("COMPLETED","FAILED","CANCELLED").contains(state))loop.cancel();List<Map<String,Object>> combined=steps();oldSteps.clear();oldSteps.addAll(combined);oldMetrics=loop.getMetrics();loop=null;}}
    public void close(){closeLoop();}
}
