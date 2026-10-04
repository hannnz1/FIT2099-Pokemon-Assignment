package game.agent.combat;
import edu.monash.fit2099.engine.positions.*;
import edu.monash.fit2099.engine.actors.Actor;
import game.actors.Player;
import game.actors.pokemon.*;
import game.agent.runtime.*;
import game.agent.tools.*;
import game.agent.action.ActionResult;
import game.agent.navigation.NavigationService;
import game.agent.llm.Json;
import game.environments.Dirt;
import game.items.balls.Pokeball;
import game.time.*;
import java.util.*;
import java.util.concurrent.Executor;

/** Isolated opt-in arena; no global clock or legacy NPC state. */
public final class BattleTrainingScenario {
    private final AgentTask task=new AgentTask(UUID.randomUUID().toString());
    private final AgentMudkip actor=new AgentMudkip();
    private final Player trainer=new Player("Training trainer",'@',100);
    private final GameMap map=new GameMap(new FancyGroundFactory(new Dirt()),Arrays.asList(".........",".........","........."));
    private final Map<String,Pokemon> targets=new LinkedHashMap<>();
    private final GameToolRegistry tools;
    private final CombatSession combat;
    private boolean manual,noBattle;
    private long turn;
    private BattleIntent goal=new BattleIntent("CAPTURE","wild-treecko",false);
    private String arenaId=UUID.randomUUID().toString();
    private boolean transferred;
    public String getArenaId(){return arenaId;}
    public boolean isTransferred(){return transferred;}
    public Pokeball capturedBall(){for(edu.monash.fit2099.engine.items.Item item:trainer.getInventory())if(item instanceof Pokeball&&((Pokeball)item).containsPokemon()&&((Pokeball)item).getPokemon()==targets.get("wild-treecko"))return (Pokeball)item;return null;}
    public boolean transferCaptured(){if(transferred)return false;Pokeball ball=capturedBall();if(ball==null)throw new IllegalStateException("NOT_CAPTURED");trainer.removeItemFromInventory(ball);transferred=true;return true;}
    public void configureGoal(BattleIntent goal){if(active())throw new IllegalStateException("TASK_ACTIVE");this.goal=Objects.requireNonNull(goal);this.noBattle=goal.noBattle;}
    public BattleTrainingScenario(){
        new World(new edu.monash.fit2099.engine.displays.Display()).addGameMap(map);
        actor.resetMaxHp(1000);map.addActor(actor,map.at(0,1));
        targets.put("wild-treecko",new Treecko());targets.put("wild-torchic",new Torchic());
        map.addActor(targets.get("wild-treecko"),map.at(2,1));map.addActor(targets.get("wild-torchic"),map.at(6,1));
        TimePerceptionManager.getInstance().cleanUp(actor);for(Pokemon p:targets.values())TimePerceptionManager.getInstance().cleanUp((TimePerception)p);
        combat=new CombatSession(actor,trainer,map,targets,()->manual||active(),()->!manual&&noBattle,p->true,()->{if(active())task.fail();});
        tools=new GameToolRegistry(r->active()?null:"TASK_INACTIVE");
        Map<String,Location> locations=new LinkedHashMap<>();locations.put("treecko-approach",map.at(1,1));locations.put("torchic-approach",map.at(5,1));
        GameTools.register(tools,actor,map,locations,p->true,this::active);CombatTools.register(tools,combat,this::observation);
    }
    private boolean active(){return task.getState()==AgentTask.State.RUNNING||task.getState()==AgentTask.State.REPLANNING;}
    public String getTaskId(){return task.getTaskId();}
    public boolean isComplete(){return "CAPTURE".equals(goal.goal)?combat.captured(goal.targetId)||transferred&&"wild-treecko".equals(goal.targetId):!map.contains(targets.get(goal.targetId))&&!combat.captured(goal.targetId)&&!targets.get(goal.targetId).isConscious();}
    public int getCapturedCount(){return combat.capturedIds().size();}
    public ActionResult execute(ToolRequest request){return tools.execute(request);}
    public ActionResult manual(String action,String value){
        if(active()||task.getState()==AgentTask.State.WAITING_APPROVAL)return ActionResult.rejected("AI_CONTROLS_COMPANION");
        if(!map.contains(actor)||!actor.isConscious())return ActionResult.rejected("ACTOR_UNAVAILABLE");
        manual=true;try{ActionResult result;
            if("attack".equals(action))result=combat.attack(value);
            else if("capture".equals(action))result=combat.capture(value);
            else if("move".equals(action)){
                Map<String,String> names=new HashMap<>();names.put("N","North");names.put("S","South");names.put("E","East");names.put("W","West");
                result=ActionResult.rejected("INVALID_DIRECTION");for(Exit exit:map.locationOf(actor).getExits())if(exit.getName().equals(names.get(value))){result=new NavigationService().step(actor,map,exit.getDestination(),p->true,()->true);break;}
            }else result=ActionResult.rejected("UNKNOWN_MANUAL_ACTION");
            advance(result);return result;
        }finally{manual=false;}
    }
    public void advance(ActionResult result){if(!Arrays.asList("GOAL_COMPLETED","COMBAT_OBSERVED","OBSERVED","INVENTORY").contains(result.getCode())&&(result.getStatus()==ActionResult.Status.SUCCESS||result.getStatus()==ActionResult.Status.IN_PROGRESS)){turn++;map.tick();}}
    public AgentLoop start(AgentLoop.DecisionProvider provider,Executor executor,boolean noBattle){
        if(task.getState()!=AgentTask.State.CREATED && task.getState()!=AgentTask.State.PAUSED)throw new IllegalStateException("TASK_ACTIVE");
        if(!map.contains(actor)||!actor.isConscious())throw new IllegalStateException("ACTOR_UNAVAILABLE");
        this.noBattle=this.noBattle||noBattle;
        if(task.getState()==AgentTask.State.CREATED)task.start();else task.resume();
        AgentLoop loop=new AgentLoop(task,goal.instruction()+(this.noBattle?" NO_ACTIVE_BATTLE.":""),tools,executor,provider,this::observation,this::isComplete,35000,30,30,5);
        loop.configureTrace("mudkip","training","unspecified");return loop;
    }
    public Map<String,String> observation(){
        Map<String,String> data=new LinkedHashMap<>();data.put("actorHp",Integer.toString(actor.getHitPoints()));data.put("taskState",task.getState().name());data.put("turn",Long.toString(turn));
        if(map.contains(actor)){Location p=map.locationOf(actor);data.put("x",Integer.toString(p.x()));data.put("y",Integer.toString(p.y()));}
        data.put("noActiveBattle",Boolean.toString(noBattle));data.put("targets",Json.write(targetStates()));data.put("captured",Json.write(combat.capturedIds()));
        data.put("goalType",goal.goal);data.put("goalTarget",goal.targetId);
        data.put("knownLocations","treecko-approach=(1,1),torchic-approach=(5,1)");data.put("captureRules","Original engine: Treecko capturable without attack; Torchic cannot be captured; trainer owns unlimited ordinary Pokeballs. Capture requires adjacency.");return Collections.unmodifiableMap(data);
    }
    private List<Object> targetStates(){List<Object> rows=new ArrayList<>();for(Map.Entry<String,Pokemon> entry:targets.entrySet()){
        Pokemon p=entry.getValue();Map<String,Object> row=Json.object("id",entry.getKey(),"hp",Math.max(0,p.getHitPoints()),"state",transferred&&"wild-treecko".equals(entry.getKey())?"TRANSFERRED":combat.captured(entry.getKey())?"CAPTURED":map.contains(p)?"WILD":"DEFEATED");
        if(map.contains(p)){Location location=map.locationOf(p);row.put("x",location.x());row.put("y",location.y());}rows.add(row);
    }return rows;}
    public Map<String,Object> exportState(){Map<String,Object> position=Json.object("hp",Math.max(0,actor.getHitPoints()));if(map.contains(actor)){Location p=map.locationOf(actor);position.put("x",p.x());position.put("y",p.y());}
        return Json.object("schemaVersion",1,"kind","BATTLE_TRAINING","actor",position,"targets",targetStates(),"captured",combat.capturedIds(),"turn",turn,"noBattle",noBattle,"taskState",task.getState().name(),"goal",goal.encode(),"arenaId",arenaId,"transferred",transferred);}
    public static BattleTrainingScenario restore(Map<String,Object> saved){
        Set<String> fields=new HashSet<>(Arrays.asList("schemaVersion","kind","actor","targets","captured","turn","noBattle","taskState"));if(saved.containsKey("goal"))fields.add("goal");
        if(saved.containsKey("arenaId")||saved.containsKey("transferred")){fields.add("arenaId");fields.add("transferred");}
        if(!saved.keySet().equals(fields)||number(saved.get("schemaVersion"))!=1||!"BATTLE_TRAINING".equals(saved.get("kind"))||!(saved.get("noBattle") instanceof Boolean))throw new IllegalArgumentException("INVALID_CHECKPOINT");
        BattleTrainingScenario scene=new BattleTrainingScenario();scene.noBattle=(Boolean)saved.get("noBattle");scene.turn=number(saved.get("turn"));if(scene.turn<0||scene.turn>100000)throw new IllegalArgumentException("INVALID_CHECKPOINT");
        if(saved.containsKey("arenaId")){Object arena=saved.get("arenaId");if(!(arena instanceof String)||!((String)arena).matches("[a-f0-9-]{36}")||!(saved.get("transferred") instanceof Boolean))throw new IllegalArgumentException("INVALID_CHECKPOINT");scene.arenaId=(String)arena;scene.transferred=(Boolean)saved.get("transferred");}
        else scene.arenaId=UUID.nameUUIDFromBytes(("legacy-training:"+Json.write(saved)).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        if(saved.containsKey("goal"))scene.goal=BattleIntent.decode(Json.asObject(saved.get("goal")));
        if("DEFEAT".equals(scene.goal.goal)&&scene.noBattle)throw new IllegalArgumentException("INVALID_CHECKPOINT");
        for(Pokemon p:scene.targets.values())scene.map.removeActor(p);
        Map<String,Object> actor=Json.asObject(saved.get("actor"));int hp=bounded(actor.get("hp"),0,1000);scene.map.removeActor(scene.actor);
        if(hp>0){if(actor.size()!=3)throw new IllegalArgumentException("INVALID_CHECKPOINT");scene.map.addActor(scene.actor,scene.position(actor));}else if(actor.size()!=1)throw new IllegalArgumentException("INVALID_CHECKPOINT");scene.actor.hurt(1000-hp);
        Set<String> captured=new HashSet<>();for(Object id:Json.asArray(saved.get("captured")))if(!(id instanceof String)||!scene.targets.containsKey(id)||!captured.add((String)id)||!"wild-treecko".equals(id))throw new IllegalArgumentException("INVALID_CHECKPOINT");
        List<Object> rows=Json.asArray(saved.get("targets"));if(rows.size()!=2)throw new IllegalArgumentException("INVALID_CHECKPOINT");Set<String> seen=new HashSet<>();
        for(Object value:rows){Map<String,Object> row=Json.asObject(value);String id=(String)row.get("id");Pokemon p=scene.targets.get(id);if(p==null||!seen.add(id))throw new IllegalArgumentException("INVALID_CHECKPOINT");
            int health=bounded(row.get("hp"),0,100);String state=(String)row.get("state");p.hurt(100-health);
            if("WILD".equals(state)){if(health==0||captured.contains(id)||row.size()!=5)throw new IllegalArgumentException("INVALID_CHECKPOINT");Location location=scene.position(row);if(!location.canActorEnter(p))throw new IllegalArgumentException("INVALID_CHECKPOINT");scene.map.addActor(p,location);}
            else if("CAPTURED".equals(state)){if(health==0||!captured.contains(id)||row.size()!=3)throw new IllegalArgumentException("INVALID_CHECKPOINT");scene.trainer.addItemToInventory(new Pokeball().capturePokemon(p));}
            else if("TRANSFERRED".equals(state)){if(!scene.transferred||!"wild-treecko".equals(id)||health==0||captured.contains(id)||row.size()!=3)throw new IllegalArgumentException("INVALID_CHECKPOINT");}
            else if(!"DEFEATED".equals(state)||health>0||captured.contains(id)||row.size()!=3)throw new IllegalArgumentException("INVALID_CHECKPOINT");
        }
        AgentTask.State lifecycle=AgentTask.State.valueOf((String)saved.get("taskState"));
        if(scene.transferred&&rows.stream().noneMatch(value->"TRANSFERRED".equals(Json.asObject(value).get("state"))))throw new IllegalArgumentException("INVALID_CHECKPOINT");
        if(lifecycle==AgentTask.State.COMPLETED&&!scene.isComplete())throw new IllegalArgumentException("INVALID_CHECKPOINT");
        scene.task.start();if(scene.isComplete())scene.task.markCompleted();else if(hp==0)scene.task.fail();else scene.task.pause();
        return scene;
    }
    private Location position(Map<String,Object> point){return map.at(bounded(point.get("x"),0,8),bounded(point.get("y"),0,2));}
    private static long number(Object value){if(!(value instanceof Number))throw new IllegalArgumentException("INVALID_CHECKPOINT");try{return new java.math.BigDecimal(value.toString()).longValueExact();}catch(RuntimeException e){throw new IllegalArgumentException("INVALID_CHECKPOINT");}}
    private static int bounded(Object value,int min,int max){long n=number(value);if(n<min||n>max)throw new IllegalArgumentException("INVALID_CHECKPOINT");return (int)n;}
}
