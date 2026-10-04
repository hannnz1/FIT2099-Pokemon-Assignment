package game.agent.demo;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.displays.Display;
import edu.monash.fit2099.engine.positions.*;
import game.actors.npc.*;
import game.agent.quest.*;
import game.agent.combat.*;
import game.agent.runtime.*;
import game.agent.llm.*;
import game.agent.tools.*;
import game.agent.memory.*;
import game.actors.pokemon.Treecko;
import game.environments.Dirt;
import game.behaviours.WanderBehaviour;
import game.time.*;
import game.agent.action.ActionResult;
import game.agent.navigation.NavigationService;
import edu.monash.fit2099.engine.actions.Action;
import java.util.*;
import java.util.concurrent.Executor;

/** Opt-in, isolated real game scene shared by live and offline Gemini entry points.
 * Only immutable per-companion observations enter the provider. Other actors and
 * map/day-night ticks are not automatically advanced by this console scene.
 */
public final class GeminiQuestScenario {
    private final AgentTask task;
    private final AgentMudkip actor=new AgentMudkip();
    private final ProffesorOak professor=new ProffesorOak();
    private final BerryQuestSession session;
    private final NpcDialogueService dialogue;
    private final GameToolRegistry tools=new GameToolRegistry(r->canAdvanceSafely()?null:"LEADER_EXHAUSTED");
    private final Map<String,Location> catalog=new LinkedHashMap<>();
    private final GameMap map;
    private final Treecko treecko;
    private final MemoryService memories=new MemoryService();
    private final WorldClock clock=new WorldClock(60);
    private final boolean original;
    private long worldTicks;
    private java.util.function.IntSupplier evaluationRandom;private int evaluationDecisionLimit=100;
    public void evaluationBudget(int limit){if(limit<1||limit>100)throw new IllegalArgumentException("INVALID_BUDGET");evaluationDecisionLimit=limit;}
    private edu.monash.fit2099.engine.actors.Actor summoned;
    private boolean following,battleForbidden,agentOwned;
    private WildEncounter wild;
    private TaskIntent fieldIntent;
    private SharedBerryQuest cooperative;
    private Actor v3Torchic;
    private java.util.function.Supplier<List<Map<String,Object>>> agentMessages=Collections::emptyList;
    public void configureEvaluation(java.util.function.IntSupplier random,String questId){evaluationRandom=Objects.requireNonNull(random);if(session.getTurn()==0&&task.getState()==AgentTask.State.CREATED){Map<String,Object> state=session.exportState();state.put("questId",questId);session.restoreState(state);}}
    public void setAgentMessages(java.util.function.Supplier<List<Map<String,Object>>> supplier){agentMessages=supplier;}
    public void enableNpcAgents(){if(!original)throw new IllegalArgumentException("ORIGINAL_MAP_REQUIRED");if(v3Torchic!=null)return;
        v3Torchic=new game.actors.pokemon.Torchic();TimePerceptionManager.getInstance().cleanUp((TimePerception)v3Torchic);
        if(!map.at(33,10).canActorEnter(v3Torchic)){v3Torchic=null;throw new IllegalArgumentException("NO_NPC_SPACE");}map.addActor(v3Torchic,map.at(33,10));}
    public Map<String,String> npcObservation(game.agent.multi.MultiAgentFoundation.Role role){
        String id=game.agent.multi.MultiAgentFoundation.agentId(role);Map<String,Object> point=(Map<String,Object>)getNpcPositions().get(id);
        if(point==null)throw new IllegalArgumentException("NPC_UNAVAILABLE");int x=((Number)point.get("x")).intValue(),y=((Number)point.get("y")).intValue();List<Object> facts=new ArrayList<>();
        if(role==game.agent.multi.MultiAgentFoundation.Role.TORCHIC)for(int xx:map.getXRange())for(int yy:map.getYRange())if(Math.max(Math.abs(xx-x),Math.abs(yy-y))<=3){long n=map.at(xx,yy).getItems().stream().filter(Berry.class::isInstance).count();if(n>0)facts.add(Json.object("id","BERRY_"+xx+"_"+yy,"subject","BERRY","x",xx,"y",yy,"quantity",n));}
        if(role==game.agent.multi.MultiAgentFoundation.Role.PROFESSOR)facts.add(Json.object("id","QUEST","subject","QUEST","x",x,"y",y,"required",3,"delivered",getDelivered()));
        if(role==game.agent.multi.MultiAgentFoundation.Role.MERCHANT)facts.add(Json.object("id","OFFER","subject","OFFER","x",x,"y",y,"stock",session.getMerchantStock()));
        Map<String,String> out=new LinkedHashMap<>();out.put("agentId",id);out.put("turn",Long.toString(session.getTurn()));out.put("position",Json.write(point));out.put("facts",Json.write(facts));return out;
    }
    public ActionResult npcAction(game.agent.multi.MultiAgentFoundation.Role role,ToolRequest request){
        if("observe".equals(request.getName()))return ActionResult.success("NPC_OBSERVED");
        if(role!=game.agent.multi.MultiAgentFoundation.Role.TORCHIC||v3Torchic==null||!"move".equals(request.getName()))return ActionResult.rejected("TOOL_FORBIDDEN");
        if(!canAdvanceSafely()||session.getQuestStatus()!=BerryQuestSession.QuestStatus.ACTIVE)return ActionResult.rejected("WORLD_INACTIVE");
        Map<String,String> dirs=new HashMap<>();dirs.put("N","North");dirs.put("S","South");dirs.put("E","East");dirs.put("W","West");
        for(Exit exit:map.locationOf(v3Torchic).getExits())if(exit.getName().equals(dirs.get(request.getArguments().get("direction")))){Location dest=exit.getDestination();if(dest.x()<27||dest.x()>36||dest.y()<5||dest.y()>11||!areaAllowed(dest))return ActionResult.rejected("AREA_RESTRICTED");
            ActionResult result=new NavigationService().step(v3Torchic,map,dest,p->areaAllowed(p),()->true);if(result.getStatus()==ActionResult.Status.SUCCESS||result.getStatus()==ActionResult.Status.IN_PROGRESS)advanceTurn();return result;}return ActionResult.rejected("INVALID_DIRECTION");
    }
    public boolean isCooperative(){return cooperative!=null;}
    public void enableCooperative(){if(!original)throw new IllegalArgumentException("ORIGINAL_MAP_REQUIRED");if(cooperative!=null)return;if(session.getTurn()!=0||getDelivered()!=0||task.getState()!=AgentTask.State.CREATED)throw new IllegalArgumentException("NEW_ROUND_REQUIRED");cooperative=new SharedBerryQuest(actor,map,session,map.at(29,11));}
    public Map<String,Object> playerView(){return cooperative==null?null:cooperative.playerView();}
    public Map<String,Object> sharedQuestView(){return cooperative==null?null:cooperative.view();}
    public ActionResult playerAction(String action,Map<String,Object> args){if(cooperative==null)return ActionResult.rejected("COOP_REQUIRED");if(!canAdvanceSafely())return ActionResult.rejected("LEADER_EXHAUSTED");return cooperative.action(action,args,p->areaAllowed(p));}
    public List<Object> wildStates(){return wild==null?Collections.emptyList():wild.states();}
    private boolean constraintsActive(){return !EnumSet.of(AgentTask.State.CREATED,AgentTask.State.CANCELLED,AgentTask.State.COMPLETED,AgentTask.State.FAILED).contains(task.getState());}
    private boolean areaAllowed(Location p){return !constraintsActive()||allowed.test(p);}
    public boolean battleForbidden(){return battleForbidden&&constraintsActive();}
    private int nextTurnDamage(){return 0;}
    public boolean canAdvanceSafely(){return actor.getHitPoints()>nextTurnDamage();}
    public WildEncounter wildEncounter(){return wild;}
    public ActionResult explore(){if(!original)return ActionResult.rejected("ORIGINAL_MAP_REQUIRED");if(wild!=null)return ActionResult.success("EXPLORATION_READY");try{wild=WildEncounter.spawn(map);return ActionResult.success("EXPLORATION_READY");}catch(IllegalArgumentException failure){return ActionResult.rejected("NO_WILD_SPACE");}}
    public boolean canRest(){return "true".equals(session.observation().get("nearProfessor"));}
    public void restLeader(){actor.heal(1000);}
    public Map<String,Object> partnerState(boolean owned){if(owned){Map<String,Object> state=summonedState();if(state!=null&&summoned instanceof game.agent.growth.GrowthPokemon){Map<String,Object> growth=((game.agent.growth.GrowthPokemon)summoned).view();for(String key:Arrays.asList("level","experience","moves","burned"))state.put(key,growth.get(key));}return state;}Location p=map.locationOf(actor);return Json.object("species","MUDKIP","hp",actor.getHitPoints(),"maxHp",original?1000:100,"x",p.x(),"y",p.y());}
    public ActionResult wildManual(boolean owned,String action,Map<String,Object> args){
        return session.manualAction(()->{
            if(wild==null)return ActionResult.rejected("EXPLORATION_REQUIRED");
            if(!canAdvanceSafely())return ActionResult.rejected("LEADER_EXHAUSTED");
            edu.monash.fit2099.engine.actors.Actor partner=owned?summoned:actor;if(partner==null||!map.contains(partner)||!partner.isConscious())return ActionResult.rejected("ACTOR_UNAVAILABLE");
            if("move".equals(action)&&args.keySet().equals(Collections.singleton("direction"))){
                Map<String,String> names=new HashMap<>();names.put("N","North");names.put("S","South");names.put("E","East");names.put("W","West");
                for(Exit exit:map.locationOf(partner).getExits())if(exit.getName().equals(names.get(args.get("direction")))){
                    if(!exit.getDestination().canActorEnter(partner))return ActionResult.rejected("PATH_BLOCKED");
                    ActionResult result=new NavigationService().step(partner,map,exit.getDestination(),p->areaAllowed(p),()->true);if(owned&&(result.getStatus()==ActionResult.Status.SUCCESS||result.getStatus()==ActionResult.Status.IN_PROGRESS))following=false;return result;
                }return ActionResult.rejected("INVALID_DIRECTION");
            }
            if("use_skill".equals(action)&&args.keySet().equals(new HashSet<>(Arrays.asList("targetId","moveId")))&&args.get("targetId") instanceof String&&args.get("moveId") instanceof String)return wild.useSkill(partner,(String)args.get("targetId"),(String)args.get("moveId"),battleForbidden(),p->areaAllowed(p));
            if(("attack".equals(action)||"capture".equals(action))&&args.keySet().equals(Collections.singleton("targetId"))&&args.get("targetId") instanceof String)return wild.action(partner,action,(String)args.get("targetId"),battleForbidden(),p->areaAllowed(p),owned?0:nextTurnDamage());
            return ActionResult.rejected("INVALID_PARAMS");
        });
    }
    public void setFollowing(boolean enabled){if(enabled&&summoned==null)throw new IllegalStateException("NOT_SUMMONED");following=enabled;}
    private void followOneStep(){
        if(!following||summoned==null||!map.contains(summoned)||!summoned.isConscious()||!actor.isConscious())return;
        Location current=map.locationOf(summoned),leader=map.locationOf(actor);
        for(Exit exit:leader.getExits())if(exit.getDestination()==current)return;
        NavigationService navigation=new NavigationService();List<Location> best=Collections.emptyList();Location destination=null;
        for(Exit exit:leader.getExits()){
            Location candidate=exit.getDestination();if(!areaAllowed(candidate)||!candidate.canActorEnter(summoned))continue;
            List<Location> route=navigation.findRoute(summoned,map,candidate,p->areaAllowed(p));
            if(!route.isEmpty()&&(destination==null||route.size()<best.size())){destination=candidate;best=route;}
        }
        if(destination!=null)navigation.step(summoned,map,destination,p->areaAllowed(p),()->following);
    }
    public Map<String,Object> summonPoint(edu.monash.fit2099.engine.actors.Actor pokemon,String direction){
        Map<String,String> names=new HashMap<>();names.put("N","North");names.put("S","South");names.put("E","East");names.put("W","West");
        for(Exit exit:map.locationOf(actor).getExits())if(exit.getName().equals(names.get(direction))&&exit.getDestination().canActorEnter(pokemon))return Json.object("x",exit.getDestination().x(),"y",exit.getDestination().y());return null;
    }
    public void synchronizeSummoned(edu.monash.fit2099.engine.actors.Actor pokemon,Map<String,Object> point){
        if(pokemon==null){if(summoned!=null&&map.contains(summoned))map.removeActor(summoned);summoned=null;following=false;return;}
        Location destination=checkedLocation(point);
        boolean already=summoned!=null&&map.contains(summoned)&&map.locationOf(summoned)==destination;
        if(!destination.getGround().canActorEnter(pokemon)||destination.containsAnActor()&&!already)throw new IllegalArgumentException("SUMMON_POSITION_BLOCKED");
        if(point.containsKey("growth")){if(!(pokemon instanceof game.agent.growth.GrowthPokemon))throw new IllegalArgumentException("INVALID_SUMMONED_GROWTH");Map<String,Object> profile=Json.asObject(point.get("growth"));if(!Objects.equals(profile.get("hp"),point.get("hp"))&&integer(profile.get("hp"))!=integer(point.get("hp")))throw new IllegalArgumentException("INVALID_SUMMONED_HP");((game.agent.growth.GrowthPokemon)pokemon).applyCheckpoint(profile);}
        if(point.containsKey("hp")){int hp=integer(point.get("hp"));if(hp<(pokemon instanceof game.agent.growth.GrowthPokemon?0:1)||hp>(pokemon instanceof game.agent.growth.GrowthPokemon?((game.agent.growth.GrowthPokemon)pokemon).maxHp():100))throw new IllegalArgumentException("INVALID_SUMMONED_HP");if(pokemon.getHitPoints()>hp)pokemon.hurt(pokemon.getHitPoints()-hp);else pokemon.heal(hp-pokemon.getHitPoints());}
        if(summoned==pokemon&&already)return;
        if(summoned!=null&&map.contains(summoned))map.removeActor(summoned);map.addActor(pokemon,destination);summoned=pokemon;
    }
    public Map<String,Object> summonedState(){if(summoned==null||!map.contains(summoned))return null;Location p=map.locationOf(summoned);Map<String,Object> state=Json.object("x",p.x(),"y",p.y(),"hp",summoned.getHitPoints(),"maxHp",summoned instanceof game.agent.growth.GrowthPokemon?((game.agent.growth.GrowthPokemon)summoned).maxHp():100,"following",following,"species",PokemonSpecies.of(summoned));if(summoned instanceof game.agent.growth.GrowthPokemon)state.put("growth",((game.agent.growth.GrowthPokemon)summoned).export());return state;}
    private java.util.function.Predicate<Location> allowed=p->true;
    public GeminiQuestScenario() {
        this("console-player");
    }
    public GeminiQuestScenario(String ownerId) {
        this(ownerId,false);
    }
    public GeminiQuestScenario(String ownerId,boolean original) {this(ownerId,original,UUID.randomUUID().toString());}
    public GeminiQuestScenario(String ownerId,boolean original,String taskId) {
        this.task=new AgentTask(taskId);this.original=original;map=V1WorldFactory.create(original);
        new World(new Display()).addGameMap(map); Shopkeeper merchant=new Shopkeeper();
        int ax=original?29:0,ay=original?10:1,ox=original?31:2,oy=original?10:1;
        map.addActor(actor,map.at(ax,ay)); map.addActor(merchant,map.at(original?30:4,original?5:1)); map.addActor(professor,map.at(original?28:8,original?5:1));
        map.at(ox,oy).addItem(new Berry()); map.at(ox,oy).addItem(new Berry());
        Berry relocated=new Berry();map.at(ox,oy).addItem(relocated);
        treecko=new Treecko();map.addActor(treecko,map.at(original?30:1,original?9:0));
        TimePerceptionManager.getInstance().cleanUp(actor);TimePerceptionManager.getInstance().cleanUp(treecko);
        for(int x:map.getXRange())for(int y:map.getYRange())if(map.at(x,y).getGround() instanceof TimePerception)
            TimePerceptionManager.getInstance().cleanUp((TimePerception)map.at(x,y).getGround());
        // Quest companions have training health for a 60-action day; legacy species stats are unchanged.
        if(original){actor.resetMaxHp(1000);treecko.resetMaxHp(1000);}
        session=new BerryQuestSession(ownerId,task,actor,map,professor,merchant,
            new BerryQuestSession.Rules(3,60,10,300000),5,4,1,true,()->System.nanoTime()/1000000L);
        catalog.put("orchard",map.at(ox,oy)); catalog.put("market",map.at(original?30:3,original?6:1)); catalog.put("laboratory",map.at(original?28:7,original?6:1)); catalog.put("alternative",map.at(original?35:6,original?8:2));
        catalog.put("treecko",map.at(ax,ay));
        dialogue=new NpcDialogueService(actor,map,task,session,memories,task.getTaskId(),catalog);
        dialogue.bind("treecko",treecko,NpcDialogueService.Role.MEMORY);dialogue.bind("merchant",merchant,NpcDialogueService.Role.MERCHANT);
        dialogue.bind("professor",professor,NpcDialogueService.Role.PROFESSOR);
        // Real opening observation precedes a trusted scene change; it becomes a stale clue.
        dialogue.perceive(0);map.at(ox,oy).removeItem(relocated);catalog.get("alternative").addItem(relocated);
        Map<String,Actor> approachTargets=new LinkedHashMap<>();approachTargets.put("laboratory",professor);approachTargets.put("market",merchant);
        GameTools.register(tools,actor,map,catalog,p->areaAllowed(p),()->task.getState()==AgentTask.State.RUNNING || task.getState()==AgentTask.State.REPLANNING,approachTargets);
        BerryQuestTools.register(tools,session);
        DialogueTools.register(tools,dialogue,()->task.getState()==AgentTask.State.RUNNING||task.getState()==AgentTask.State.REPLANNING);
    }
    public BerryQuestSession getSession() { return session; }
    public String getTaskId() { return task.getTaskId(); }
    public void advanceTurn() {
        if(!session.advanceTurn())return;
        worldTicks++;clock.synchronize(session.getTurn());if(evaluationRandom==null)map.tick();
        if(original) {
            if(evaluationRandom==null)for(int x:map.getXRange())for(int y:map.getYRange())if(map.at(x,y).getGround() instanceof TimePerception) {
                TimePerception terrain=(TimePerception)map.at(x,y).getGround();if(clock.period().equals("DAY"))terrain.dayEffect();else terrain.nightEffect();
            }
            if(session.getTurn()%4==0 && treecko.isConscious()){
                Action wander=(evaluationRandom==null?new WanderBehaviour(p->p.x()>=29&&p.x()<=33&&p.y()>=8&&p.y()<=11):new WanderBehaviour(p->p.x()>=29&&p.x()<=33&&p.y()>=8&&p.y()<=11,evaluationRandom)).getAction(treecko,map);if(wander!=null)wander.execute(treecko,map);
            }
        }
        followOneStep();dialogue.perceive(session.getTurn());
    }
    public String getWorldPeriod(){return clock.period();}
    public long getWorldTicks(){return worldTicks;}
    public boolean isOriginal(){return original;}
    public void selectAgentOwned(boolean owned){agentOwned=owned;if(owned)following=false;}
    private boolean ownedStage(){return agentOwned&&(fieldIntent==null||!fieldIntent.isMixed()||currentFieldStep().isField());}
    private Actor agentActor(){return ownedStage()?summoned:actor;}
    public void validateOwnedPartner(TaskIntent intent,Actor pokemon,Map<String,Object> point){
        if(!intent.isField()||cooperative!=null)throw new IllegalArgumentException("OWNED_FIELD_ONLY");
        if(pokemon==null||!pokemon.isConscious())throw new IllegalArgumentException("PARTNER_UNAVAILABLE");
        ConstraintPolicy policy=new ConstraintPolicy(intent,getAreas(),clock.nightStarts());
        if(point==null||!policy.allows(checkedLocation(point)))throw new IllegalArgumentException("AREA_RESTRICTED");
    }
    public boolean canAgentRun(){Actor controlled=agentActor();return allowed.test(map.locationOf(actor))&&controlled!=null&&map.contains(controlled)&&controlled.isConscious()&&allowed.test(map.locationOf(controlled));}
    public Map<String,Set<Location>> getAreas(){
        Map<String,Set<Location>> areas=new LinkedHashMap<>();Set<Location> quest=new HashSet<>(),orchard=new HashSet<>();
        for(int x:map.getXRange())for(int y:map.getYRange()){
            Location p=map.at(x,y);if(!original||(x>=27&&x<=36&&y>=5&&y<=11))quest.add(p);
            if(original?(x>=29&&x<=33&&y>=8&&y<=11):(x<=2))orchard.add(p);
        }areas.put("QUEST_AREA",quest);areas.put("ORCHARD_AREA",orchard);return areas;
    }
    public Map<String,Object> exportState(){
        List<Object> berries=new ArrayList<>();for(int x:map.getXRange())for(int y:map.getYRange()){
            long count=map.at(x,y).getItems().stream().filter(Berry.class::isInstance).count();if(count>0)berries.add(Json.object("x",x,"y",y,"count",count));
        }
        Location p=map.locationOf(actor),t=map.locationOf(treecko);
        return Json.object("schemaVersion",1,"original",original,"map",getMapDescription(),"session",session.exportState(),"berries",berries,
            "actor",Json.object("x",p.x(),"y",p.y(),"hp",health(actor)),"treecko",Json.object("x",t.x(),"y",t.y(),"hp",health(treecko)),
            "stagedBerry",session.isStagedCompletion(),"carried",actor.getInventory().stream().filter(Berry.class::isInstance).count(),"delivered",getDelivered(),"memories",dialogue.exportState(),"worldTicks",worldTicks,"wild",wild==null?null:wild.exportState(),"battleForbidden",battleForbidden,"cooperative",cooperative==null?null:cooperative.exportState(),"v3Torchic",v3Torchic==null?null:Json.object("x",map.locationOf(v3Torchic).x(),"y",map.locationOf(v3Torchic).y()));
    }
    public static GeminiQuestScenario restore(String owner,Map<String,Object> state){
        if(integer(state.get("schemaVersion"))!=1||!(state.get("original") instanceof Boolean))throw new IllegalArgumentException("INVALID_CHECKPOINT");
        GeminiQuestScenario scene=new GeminiQuestScenario(owner,(Boolean)state.get("original"));
        Map<String,Object> description=Json.asObject(state.get("map"));List<Object> terrain=Json.asArray(description.get("terrain"));
        int width=integer(description.get("width")),height=integer(description.get("height"));
        if(width!=integer(scene.getMapDescription().get("width"))||height!=integer(scene.getMapDescription().get("height"))||terrain.size()!=height)throw new IllegalArgumentException("INVALID_CHECKPOINT");
        FancyGroundFactory factory=V1WorldFactory.groundFactory();
        for(int y=0;y<height;y++){String row=(String)terrain.get(y);if(row.length()!=width||!row.matches("[.,~^#_]+"))throw new IllegalArgumentException("INVALID_CHECKPOINT");
            for(int x=0;x<width;x++){scene.map.at(x,y).setGround(factory.newGround(row.charAt(x)));scene.map.at(x,y).getItems().stream().filter(Berry.class::isInstance).collect(java.util.stream.Collectors.toList()).forEach(scene.map.at(x,y)::removeItem);}
        }
        List<Object> berries=Json.asArray(state.get("berries"));if(berries.size()>20)throw new IllegalArgumentException("INVALID_CHECKPOINT");
        Set<Location> seen=new HashSet<>();int total=0;for(Object value:berries){Map<String,Object> point=Json.asObject(value);Location p=scene.checkedLocation(point);int count=integer(point.get("count"));
            if(!seen.add(p)||count<1||(total+=count)>20)throw new IllegalArgumentException("INVALID_CHECKPOINT");for(int i=0;i<count;i++)p.addItem(new Berry());
        }
        Map<String,Object> actorState=Json.asObject(state.get("actor")),treeState=Json.asObject(state.get("treecko"));Location a=scene.checkedLocation(actorState),t=scene.checkedLocation(treeState);
        scene.map.removeActor(scene.actor);scene.map.removeActor(scene.treecko);
        if(a==t||!a.canActorEnter(scene.actor)||!t.canActorEnter(scene.treecko))throw new IllegalArgumentException("INVALID_CHECKPOINT");
        scene.map.addActor(scene.actor,a);scene.map.addActor(scene.treecko,t);
        int max=scene.original?1000:100,ah=integer(actorState.get("hp")),th=integer(treeState.get("hp"));if(ah<0||ah>max||th<0||th>max)throw new IllegalArgumentException("INVALID_CHECKPOINT");
        scene.actor.resetMaxHp(max);scene.actor.hurt(max-ah);scene.treecko.resetMaxHp(max);scene.treecko.hurt(max-th);
        int carried=integer(state.get("carried")),delivered=integer(state.get("delivered"));if(carried<0||carried>10||delivered<0||delivered>3)throw new IllegalArgumentException("INVALID_CHECKPOINT");
        for(int i=0;i<carried;i++)scene.actor.addItemToInventory(new Berry());for(int i=0;i<delivered;i++)scene.professor.addItemToInventory(new Berry());
        scene.session.restoreState(Json.asObject(state.get("session")));
        if(state.containsKey("stagedBerry")&&!(state.get("stagedBerry") instanceof Boolean))throw new IllegalArgumentException("INVALID_CHECKPOINT");
        if(Boolean.TRUE.equals(state.get("stagedBerry"))){if(state.get("wild")==null||state.get("cooperative")!=null||scene.session.getQuestStatus()==BerryQuestSession.QuestStatus.COMPLETED)throw new IllegalArgumentException("INVALID_CHECKPOINT");scene.session.configureStagedCompletion();}
        else if((scene.session.getQuestStatus()==BerryQuestSession.QuestStatus.COMPLETED)!=(delivered==3))throw new IllegalArgumentException("INVALID_CHECKPOINT");
        scene.clock.synchronize(scene.session.getTurn());scene.worldTicks=new java.math.BigDecimal(String.valueOf(state.get("worldTicks"))).longValueExact();
        if(scene.worldTicks<0||scene.worldTicks>scene.session.getTurn())throw new IllegalArgumentException("INVALID_CHECKPOINT");
        scene.dialogue.restoreState(Json.asObject(state.get("memories")));
        for(int x:scene.map.getXRange())for(int y:scene.map.getYRange())if(scene.map.at(x,y).getGround() instanceof TimePerception)TimePerceptionManager.getInstance().cleanUp((TimePerception)scene.map.at(x,y).getGround());
        if(state.containsKey("battleForbidden")){if(!(state.get("battleForbidden") instanceof Boolean))throw new IllegalArgumentException("INVALID_CHECKPOINT");scene.battleForbidden=(Boolean)state.get("battleForbidden");}
        if(state.get("wild")!=null){if(!scene.original)throw new IllegalArgumentException("INVALID_WILD_CHECKPOINT");scene.wild=WildEncounter.restore(scene.map,Json.asObject(state.get("wild")));}
        if(state.get("cooperative")!=null){
            Location free=null;for(Exit exit:scene.map.locationOf(scene.actor).getExits())if(exit.getDestination().canActorEnter(new game.actors.Player("玩家",'@',100))){free=exit.getDestination();break;}
            if(free==null)throw new IllegalArgumentException("INVALID_SHARED_CHECKPOINT");
            scene.cooperative=new SharedBerryQuest(scene.actor,scene.map,scene.session,free);scene.cooperative.restoreState(Json.asObject(state.get("cooperative")));
        }
        if(state.get("v3Torchic")!=null){if(!scene.original)throw new IllegalArgumentException("INVALID_NPC_CHECKPOINT");scene.v3Torchic=new game.actors.pokemon.Torchic();TimePerceptionManager.getInstance().cleanUp((TimePerception)scene.v3Torchic);Location dest=scene.checkedLocation(Json.asObject(state.get("v3Torchic")));if(dest.x()<27||dest.x()>36||dest.y()<5||dest.y()>11||!dest.canActorEnter(scene.v3Torchic))throw new IllegalArgumentException("INVALID_NPC_CHECKPOINT");scene.map.addActor(scene.v3Torchic,dest);}
        return scene;
    }
    private Location checkedLocation(Map<String,Object> point){int x=integer(point.get("x")),y=integer(point.get("y"));if(!map.getXRange().contains(x)||!map.getYRange().contains(y))throw new IllegalArgumentException("INVALID_CHECKPOINT");return map.at(x,y);}
    private static int integer(Object value){return new java.math.BigDecimal(String.valueOf(value)).intValueExact();}
    private static int health(edu.monash.fit2099.engine.actors.Actor actor){return actor.getHitPoints();}
    public void validateIntent(TaskIntent intent){
        if(cooperative!=null&&intent.isField())throw new IllegalArgumentException("SHARED_BERRY_ONLY");
        ConstraintPolicy policy=new ConstraintPolicy(intent,getAreas(),clock.nightStarts());
        if(cooperative!=null&&!policy.allows(map.at((Integer)playerView().get("x"),(Integer)playerView().get("y"))))throw new IllegalArgumentException("AREA_RESTRICTED");
        if(policy.deadlineTurn()<=session.getTurn())throw new IllegalArgumentException("INVALID_DEADLINE");
        if(!policy.allows(map.locationOf(actor)))throw new IllegalArgumentException("AREA_RESTRICTED");
        if(intent.isField()){
            if(!original)throw new IllegalArgumentException("ORIGINAL_MAP_REQUIRED");
            for(TaskIntent step:intent.getSteps()){if(!step.isField())continue;
            Map<String,Object> target=wild==null?WildEncounter.spawnPoint(step.getTargetId()):wild.state(step.getTargetId());
            if(target==null||wild!=null&&!"WILD".equals(target.get("state")))throw new IllegalArgumentException("TARGET_UNAVAILABLE");
            Location point=map.at(((Number)target.get("x")).intValue(),((Number)target.get("y")).intValue());
            if(!policy.allows(point))throw new IllegalArgumentException("AREA_RESTRICTED");
            }
        }
    }
    private boolean stepComplete(TaskIntent step){
        if(!step.isField())return getDelivered()==3;
        if(wild==null)return false;Map<String,Object> target=wild.state(step.getTargetId());if(target==null)return false;
        return "CAPTURE".equals(step.getKind())?Arrays.asList("CAPTURED","TRANSFERRED").contains(target.get("state")):"DEFEATED".equals(target.get("state"));
    }
    public TaskIntent currentFieldStep(){
        if(fieldIntent==null)return null;for(TaskIntent step:fieldIntent.getSteps())if(!stepComplete(step))return step;
        return fieldIntent.getSteps().get(fieldIntent.getSteps().size()-1);
    }
    public List<Map<String,Object>> goalProgress(TaskIntent plan){
        List<Map<String,Object>> rows=new ArrayList<>();if(plan==null||!plan.isField())return rows;
        boolean current=true;for(TaskIntent step:plan.getSteps()){boolean done=stepComplete(step);rows.add(Json.object("kind",step.getKind(),"targetId",step.getTargetId(),"completed",done,"status",done?"COMPLETED":current?"CURRENT":"PENDING"));if(!done)current=false;}
        return rows;
    }
    public boolean fieldComplete(){
        if(fieldIntent==null||wild==null)return false;for(TaskIntent step:fieldIntent.getSteps())if(!stepComplete(step))return false;return true;
    }
    public void settleFieldCompletion(){
        if(fieldComplete()&&(task.getState()==AgentTask.State.RUNNING||task.getState()==AgentTask.State.REPLANNING))task.markCompleted();
    }
    public boolean fieldTaskCompleted(){return fieldIntent!=null&&fieldComplete()&&task.getState()==AgentTask.State.COMPLETED;}
    public void restoreLifecycle(String saved){
        if(task.getState()!=AgentTask.State.RUNNING)throw new IllegalStateException("RESTORE_AFTER_START");
        if(fieldIntent!=null&&fieldComplete()&&"COMPLETED".equals(saved)){task.markCompleted();return;}
        if(session.getQuestStatus()==BerryQuestSession.QuestStatus.COMPLETED){task.markCompleted();return;}
        if(session.getQuestStatus()==BerryQuestSession.QuestStatus.EXPIRED){if(task.getState()!=AgentTask.State.FAILED)task.fail();return;}
        if("CANCELLED".equals(saved))task.cancel();else if("FAILED".equals(saved))task.fail();else task.pause();
    }
    public Map<String,Object> getMapDescription(){
        List<String> rows=new ArrayList<>();int width=0;
        for(int y:map.getYRange()){StringBuilder row=new StringBuilder();for(int x:map.getXRange()){row.append(map.at(x,y).getGround().getDisplayChar());width=Math.max(width,x+1);}rows.add(row.toString());}
        return Json.object("width",width,"height",rows.size(),"terrain",rows,"mode",original?"ORIGINAL_V1":"COMPACT");
    }
    public ActionResult manual(String action,Map<String,Object> params){
        return session.manualAction(()->{
            if(!canAdvanceSafely())return ActionResult.rejected("LEADER_EXHAUSTED");
            if("move".equals(action) && params.keySet().equals(Collections.singleton("direction"))){
                String direction=String.valueOf(params.get("direction"));
                Map<String,String> names=new HashMap<>();names.put("N","North");names.put("S","South");names.put("E","East");names.put("W","West");
                for(Exit exit:map.locationOf(actor).getExits())if(exit.getName().equals(names.get(direction)))
                    return new NavigationService().step(actor,map,exit.getDestination(),p->true,()->true);
                return ActionResult.rejected("INVALID_DIRECTION");
            }
            if("pickup".equals(action)&&params.keySet().equals(Collections.singleton("quantity"))){
                try{return session.pickup("BERRY",new java.math.BigDecimal(String.valueOf(params.get("quantity"))).intValueExact());}
                catch(RuntimeException error){return ActionResult.rejected("INVALID_QUANTITY");}
            }
            if(!params.isEmpty())return ActionResult.rejected("INVALID_PARAMS");
            if("deliver".equals(action))return session.deliver(session.getQuestId(),"professor");
            if("wait".equals(action))return ActionResult.success("WAITED");
            return ActionResult.rejected("UNKNOWN_MANUAL_ACTION");
        });
    }
    public Map<String,String> getPublicDialogue() {return dialogue.publicDialogue();}
    public List<Map<String,String>> getPublicDialogues(){return dialogue.publicDialogues();}
    public Map<String,Object> getNpcPositions() {Map<String,Object> positions=new LinkedHashMap<>(dialogue.publicPositions());if(v3Torchic!=null){Location p=map.locationOf(v3Torchic);positions.put("torchic",Json.object("x",p.x(),"y",p.y()));}return positions;}
    public Map<String,Object> getKnownLocations() {
        Map<String,Object> result=new LinkedHashMap<>();
        for(Map.Entry<String,Location> entry:catalog.entrySet()) result.put(entry.getKey(),
            Json.object("x",entry.getValue().x(),"y",entry.getValue().y()));
        return result;
    }
    public int getDelivered() { return (int)professor.getInventory().stream().filter(Berry.class::isInstance).count(); }
    public AgentLoop start(TaskIntent intent,LlmGateway gateway,Executor modelExecutor) {
        return start(intent,gateway,modelExecutor,35000);
    }
    public AgentLoop start(TaskIntent intent,LlmGateway gateway,Executor modelExecutor,long operationTimeoutMillis) {
        return startInternal(intent,gateway,modelExecutor,operationTimeoutMillis,false);
    }
    /** Restore builds a suspended loop; it never starts a decision before lifecycle restoration. */
    public AgentLoop restoreAgent(TaskIntent intent,LlmGateway gateway,Executor modelExecutor,long operationTimeoutMillis){
        return startInternal(intent,gateway,modelExecutor,operationTimeoutMillis,true);
    }
    private AgentLoop startInternal(TaskIntent intent,LlmGateway gateway,Executor modelExecutor,long operationTimeoutMillis,boolean restoring){
        if(task.getState()!=AgentTask.State.CREATED) throw new IllegalStateException("Task already started");
        if(!session.getQuestId().equals(intent.getQuestId())) throw new IllegalArgumentException("UNKNOWN_QUEST");
        if(!restoring)validateIntent(intent);
        if(intent.isField()){
            if(restoring&&wild==null)throw new IllegalArgumentException("INVALID_FIELD_CHECKPOINT");
            if(!restoring&&wild==null){ActionResult opened=explore();if(opened.getStatus()!=ActionResult.Status.SUCCESS)throw new IllegalArgumentException(opened.getCode());}
        }
        battleForbidden=intent.has(TaskIntent.Constraint.NO_ACTIVE_BATTLE);ConstraintPolicy policy=new ConstraintPolicy(intent,getAreas(),clock.nightStarts());
        allowed=policy::allows;
        if(!restoring&&!canAgentRun())throw new IllegalArgumentException("AREA_RESTRICTED");
        session.configureDeadline(policy.deadlineTurn());
        session.configureNoSpending(intent.has(TaskIntent.Constraint.NO_SPENDING));if(intent.isMixed())session.configureStagedCompletion(); task.start();
        if(intent.isField()){
            fieldIntent=intent;if(intent.isMixed()&&session.getQuestStatus()==BerryQuestSession.QuestStatus.COMPLETED)throw new IllegalArgumentException("INVALID_MIXED_CHECKPOINT");
            java.util.function.BooleanSupplier active=()->task.getState()==AgentTask.State.RUNNING||task.getState()==AgentTask.State.REPLANNING;
            java.util.function.Supplier<Map<String,String>> observation=()->{
                Map<String,String> state=new LinkedHashMap<>(session.observation());Map<String,Object> partner=partnerState(ownedStage());if(partner==null)throw new IllegalStateException("PARTNER_UNAVAILABLE");state.put("x",String.valueOf(partner.get("x")));state.put("y",String.valueOf(partner.get("y")));state.put("controlledSpecies",String.valueOf(partner.get("species")));state.put("controlledRole",ownedStage()?"OWNED":"LEADER");
                TaskIntent current=currentFieldStep();state.put("goalSteps",Json.write(goalProgress(intent)));
                state.put("actorHp",String.valueOf(partner.get("hp")));state.put("targets",Json.write(wild.states()));state.put("goalType",current.getKind());state.put("goalTarget",current.getTargetId());
                state.put("allowedAreaId",intent.getAreaId()==null?"ALL":intent.getAreaId());state.put("noActiveBattle",Boolean.toString(intent.has(TaskIntent.Constraint.NO_ACTIVE_BATTLE)));
                Map<String,Object> target=current.isField()?wild.state(current.getTargetId()):null;boolean adjacent=false;
                if(target!=null&&"WILD".equals(target.get("state"))){Location point=map.at(((Number)target.get("x")).intValue(),((Number)target.get("y")).intValue());for(Exit exit:map.locationOf(agentActor()).getExits())if(exit.getDestination()==point)adjacent=true;}
                state.put("targetAdjacent",Boolean.toString(adjacent));boolean growth=agentActor() instanceof game.agent.growth.GrowthPokemon;state.put("suggestedAction",adjacent?("CAPTURE".equals(current.getKind())?"capture":growth?"use_skill":"attack"):"move_to");
                if(growth){game.agent.growth.GrowthPokemon trained=(game.agent.growth.GrowthPokemon)agentActor();state.put("growthPartner",Json.write(trained.view()));state.put("skillRules","For DEFEAT use use_skill(targetId,moveId), never attack. Choose an equipped damaging move with pp>0 from growthPartner.moves. Skill turns retaliate, apply status and consume PP. Rest is player-only; do not invent rest tools. Original wild targets convert to Lv.5 retaining HP ratio on first valid skill. Capture still only Treecko/Mudkip, no XP for capture.");}
                state.put("decisionHint","You already have the current authoritative observation at EVERY decision. Avoid repeated observe on unchanged state. When targetAdjacent=true, use the goal operation on goalTarget next. Native capture is not active battle; NO_ACTIVE_BATTLE permits capture.");
                state.put("captureRules","Capture is not active battle. Original rules: Treecko/Mudkip capturable directly without weakening; Torchic cannot be captured. Infinite ordinary balls. No collection transfer tools. move_to uses targetId and approaches an unoccupied adjacent tile.");
                if(intent.isMixed()){
                    Map<String,Object> locations=new LinkedHashMap<>();for(Map.Entry<String,Location> e:catalog.entrySet())locations.put(e.getKey(),Json.object("x",e.getValue().x(),"y",e.getValue().y()));
                    state.put("knownLocations",Json.write(locations));state.put("availableNpcIds","treecko,merchant,professor");state.put("nearTreecko",Boolean.toString(dialogue.near("treecko")));state.put("npcDialogueHistory",Json.write(dialogue.publicDialogues()));
                    state.put("berryDelivered",Integer.toString(getDelivered()));state.put("mixedRules","Only execute current goalType. COMPLETE_QUEST: leader Mudkip collects and delivers exactly 3 berries; use move_to_location(locationId: orchard,alternative,laboratory,market), pickup, deliver. Orchard initially has 2, alternative 1; stock must be observed, clues can be stale. CAPTURE/DEFEAT: selected companion uses move_to(targetId), capture/attack. Never deliver or pick up during wild stage, never attack/capture during berry stage. Stage changes from actual state; berry delivery is NOT overall completion. Same absolute deadline and constraints apply to every stage.");
                    if(!current.isField()){state.put("suggestedAction",Integer.parseInt(state.get("carriedBerry"))>=3?("true".equals(state.get("nearProfessor"))?"deliver":"move_to_location"):Integer.parseInt(state.get("visibleBerry"))>0?"pickup":"move_to_location");state.put("decisionHint","Berry stage: use move_to_location for resource or laboratory landmarks; collect 3 then deliver questId to professor. Do not use field move_to here. Observe actual stock and search alternative when orchard is insufficient.");}
                }
                return Collections.unmodifiableMap(state);
            };
            GameToolRegistry fieldTools=FieldAgentTools.create(this::agentActor,map,wild,intent,this::currentFieldStep,allowed,()->session.getQuestStatus()!=BerryQuestSession.QuestStatus.ACTIVE?"QUEST_EXPIRED":!active.getAsBoolean()?"TASK_INACTIVE":agentActor()==null||!map.contains(agentActor())||!agentActor().isConscious()?"PARTNER_UNAVAILABLE":!canAdvanceSafely()?"LEADER_EXHAUSTED":null,active,()->ownedStage()?0:nextTurnDamage(),observation);
            if(intent.isMixed())fieldTools=MixedQuestTools.create(tools,fieldTools,this::currentFieldStep,observation);
            return new AgentLoop(task,intent.getGoal(),fieldTools,modelExecutor,gateway,observation,this::fieldComplete,operationTimeoutMillis,intent.isMixed()?50:40,40,5);
        }
        // Berry tasks keep their original whitelist: no attack/battle tool exists.
        return new AgentLoop(task,intent.getGoal(),tools,modelExecutor,gateway,()->{
            Map<String,String> state=new LinkedHashMap<>(session.observation());
            state.put("nearTreecko",Boolean.toString(dialogue.near("treecko")));
            state.put("availableNpcIds", "treecko,merchant,professor");
            state.put("npcDialogueHistory",Json.write(dialogue.publicDialogues()));
            state.put("npcDialogueHint","If you need a resource clue, ask nearby treecko with talk_to. Remembered quantities are historical, not live availability.");
            Map<String,Object> locations=new LinkedHashMap<>();
            for(Map.Entry<String,Location> entry:catalog.entrySet()) locations.put(entry.getKey(),Json.object("x",entry.getValue().x(),"y",entry.getValue().y()));
            if(cooperative!=null){state.put("sharedQuestState",Json.write(sharedQuestView()));state.put("playerState",Json.write(playerView()));state.put("cooperationHint","PLAYER and AGENT have separate inventories and can collect/deliver concurrently. Deliver any carried berries to professor when adjacent; remainingBerry is the shared amount left, not always 3. Never wait for player promises. Actual partial deliveries reduce remainingBerry. Player may take observed resources before your commit: reread actual feedback and replan, never invent or duplicate berries.");}
            BerryCoordinationAdvice.enrich(state,catalog.get("orchard").x(),catalog.get("orchard").y());
            state.put("npcAgentMessages",Json.write(agentMessages.get()));state.put("resourceSearchSites","orchard,alternative");
            state.put("navigationHint","orchard and alternative are known resource-search landmarks; their current stock is unknown until visited. market approaches the merchant, laboratory approaches a currently reachable free neighbor of the professor even when a player occupies the nominal landmark. If the current resource site is empty and carriedBerry is insufficient, search a different known resource site; repeatedly arriving at this same empty tile or asking the same historical clue will not create berries.");
            state.put("knownLocations",Json.write(locations)); state.put("constraints",intent.getConstraints().toString());
            state.put("summonedPokemon",Json.write(summonedState()));state.put("worldPeriod",clock.period());state.put("nightStarts","60");state.put("allowedAreaId",intent.getAreaId()==null?"ALL":intent.getAreaId());
            return state;
        },()->session.getQuestStatus()==BerryQuestSession.QuestStatus.COMPLETED,operationTimeoutMillis,evaluationRandom==null?30:evaluationDecisionLimit,evaluationRandom==null?12:100,evaluationRandom==null?5:8);
    }
}
