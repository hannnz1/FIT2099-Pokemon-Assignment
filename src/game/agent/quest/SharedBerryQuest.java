package game.agent.quest;

import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.items.Item;
import edu.monash.fit2099.engine.positions.*;
import game.actors.Player;
import game.agent.action.ActionResult;
import game.agent.llm.Json;
import game.agent.navigation.NavigationService;
import java.util.*;
import java.util.function.Predicate;

/** World-thread shared quest. Real Items and separate Actors, never client credit. */
public final class SharedBerryQuest {
 private final Player player=new Player("玩家",'@',100);
 private final Actor agent;private final GameMap map;private final BerryQuestSession session;
 private final List<Map<String,Object>> ledger=new ArrayList<>();
 public SharedBerryQuest(Actor agent,GameMap map,BerryQuestSession session,Location spawn){
  this.agent=agent;this.map=map;this.session=session;
  if(!spawn.canActorEnter(player))throw new IllegalArgumentException("PLAYER_SPAWN_BLOCKED");
  map.addActor(player,spawn);session.enableShared(this::record);
 }
 private static int integer(Object v){if(!(v instanceof Number))throw new IllegalArgumentException("INVALID_SHARED_CHECKPOINT");return new java.math.BigDecimal(v.toString()).intValueExact();}
 private void record(Map<String,Object> event){
  if(ledger.size()>=512)throw new IllegalStateException("CONTRIBUTION_LIMIT");
  Map<String,Object> row=new LinkedHashMap<>(event);row.put("sequence",ledger.size()+1);ledger.add(row);
 }
 private void credit(String action,int quantity,Location source){record(Json.object("actor","PLAYER","action",action,"quantity",quantity,"turn",session.getTurn(),"x",source.x(),"y",source.y()));}
 private static int count(Actor actor){return (int)actor.getInventory().stream().filter(Berry.class::isInstance).count();}
 public Map<String,Object> playerView(){Location p=map.locationOf(player);return Json.object("x",p.x(),"y",p.y(),"hp",player.getHitPoints(),"maxHp",100,"carriedBerry",count(player),"nearProfessor",session.nearProfessor(player),"resources",resources());}
 private List<Map<String,Object>> resources(){
  List<Map<String,Object>> rows=new ArrayList<>();Location here=map.locationOf(player);Set<Location> points=new LinkedHashSet<>();points.add(here);for(Exit exit:here.getExits())points.add(exit.getDestination());
  for(Location p:points){int n=(int)p.getItems().stream().filter(Berry.class::isInstance).count();if(n>0)rows.add(Json.object("x",p.x(),"y",p.y(),"quantity",n));}return rows;
 }
 public Map<String,Object> contributions(){Map<String,Object> totals=new LinkedHashMap<>();for(String who:Arrays.asList("PLAYER","AGENT")){int picked=0,purchased=0,delivered=0;for(Map<String,Object> row:ledger)if(who.equals(row.get("actor"))){int n=integer(row.get("quantity"));if("PICKUP".equals(row.get("action")))picked+=n;else if("PURCHASE".equals(row.get("action")))purchased+=n;else delivered+=n;}totals.put(who,Json.object("picked",picked,"purchased",purchased,"delivered",delivered));}return totals;}
 private List<Map<String,Object>> events(){List<Map<String,Object>> rows=new ArrayList<>();for(Map<String,Object> row:ledger)rows.add(new LinkedHashMap<>(row));return rows;}
 public Map<String,Object> view(){return Json.object("questId",session.getQuestId(),"status",session.getQuestStatus().name(),"required",session.requiredCount(),"delivered",session.deliveredCount(),"remaining",Math.max(0,session.requiredCount()-session.deliveredCount()),"contributions",contributions(),"ledger",events());}
 public ActionResult action(String name,Map<String,Object> args,Predicate<Location> allowed){
  if(session.getQuestStatus()!=BerryQuestSession.QuestStatus.ACTIVE)return ActionResult.rejected("QUEST_NOT_ACTIVE");
  if(session.approvalWaiting())return ActionResult.rejected("APPROVAL_FREEZE");
  Location here=map.locationOf(player);if(!allowed.test(here))return ActionResult.rejected("AREA_RESTRICTED");
  if("move".equals(name)&&args.keySet().equals(Collections.singleton("direction"))){
   Map<String,String> names=new HashMap<>();names.put("N","North");names.put("E","East");names.put("S","South");names.put("W","West");
   for(Exit exit:here.getExits())if(exit.getName().equals(names.get(args.get("direction"))))return new NavigationService().step(player,map,exit.getDestination(),allowed,()->true);
   return ActionResult.rejected("INVALID_DIRECTION");
  }
  if("pickup".equals(name)&&args.keySet().equals(new HashSet<>(Arrays.asList("x","y","quantity")))){
   int x,y,n;try{x=integer(args.get("x"));y=integer(args.get("y"));n=integer(args.get("quantity"));}catch(RuntimeException invalid){return ActionResult.rejected("INVALID_PARAMS");}
   if(n<1||n>20)return ActionResult.rejected("INVALID_QUANTITY");
   if(!map.getXRange().contains(x)||!map.getYRange().contains(y))return ActionResult.rejected("OUT_OF_REACH");
   Location source=map.at(x,y);boolean near=source==here;for(Exit exit:here.getExits())if(exit.getDestination()==source)near=true;
   if(!near)return ActionResult.rejected("OUT_OF_REACH");if(!allowed.test(source))return ActionResult.rejected("AREA_RESTRICTED");
   List<Item> actual=new ArrayList<>();Set<Item> seen=Collections.newSetFromMap(new IdentityHashMap<Item,Boolean>());for(Item item:source.getItems())if(item instanceof Berry&&seen.add(item))actual.add(item);
   if(actual.size()<n)return ActionResult.rejected("RESOURCE_NOT_AVAILABLE");if(player.getInventory().size()+n>10)return ActionResult.rejected("INVENTORY_FULL");
   for(int i=0;i<n;i++){Item item=actual.get(i);source.removeItem(item);player.addItemToInventory(item);}credit("PICKUP",n,source);return ActionResult.success("PLAYER_PICKED_UP");
  }
  if("deliver".equals(name)&&args.isEmpty())return session.sharedPlayerDeliver(player,event->{Map<String,Object> row=new LinkedHashMap<>(event);row.put("actor","PLAYER");record(row);});
  if("wait".equals(name)&&args.isEmpty())return ActionResult.success("PLAYER_WAITED");
  return ActionResult.rejected("INVALID_PARAMS");
 }
 public Map<String,Object> exportState(){Location p=map.locationOf(player);return Json.object("schemaVersion",1,"player",Json.object("x",p.x(),"y",p.y(),"hp",player.getHitPoints(),"carried",count(player)),"ledger",events());}
 public void restoreState(Map<String,Object> saved){
  if(!saved.keySet().equals(new HashSet<>(Arrays.asList("schemaVersion","player","ledger")))||integer(saved.get("schemaVersion"))!=1)throw new IllegalArgumentException("INVALID_SHARED_CHECKPOINT");
  Map<String,Object> data=Json.asObject(saved.get("player"));if(!data.keySet().equals(new HashSet<>(Arrays.asList("x","y","hp","carried"))))throw new IllegalArgumentException("INVALID_SHARED_CHECKPOINT");
  int x=integer(data.get("x")),y=integer(data.get("y")),hp=integer(data.get("hp")),carried=integer(data.get("carried"));
  if(!map.getXRange().contains(x)||!map.getYRange().contains(y)||hp<1||hp>100||carried<0||carried>10)throw new IllegalArgumentException("INVALID_SHARED_CHECKPOINT");
  map.removeActor(player);Location p=map.at(x,y);if(!p.canActorEnter(player))throw new IllegalArgumentException("INVALID_SHARED_CHECKPOINT");map.addActor(player,p);player.hurt(100-hp);for(int i=0;i<carried;i++)player.addItemToInventory(new Berry());
  List<Object> rows=Json.asArray(saved.get("ledger"));if(rows.size()>512)throw new IllegalArgumentException("INVALID_SHARED_CHECKPOINT");
  int lastTurn=0;for(Object value:rows){Map<String,Object> row=Json.asObject(value);if(!row.keySet().equals(new HashSet<>(Arrays.asList("sequence","actor","action","quantity","turn","x","y")))||integer(row.get("sequence"))!=ledger.size()+1||!Arrays.asList("PLAYER","AGENT").contains(row.get("actor"))||!Arrays.asList("PICKUP","PURCHASE","DELIVER").contains(row.get("action"))||"PLAYER".equals(row.get("actor"))&&"PURCHASE".equals(row.get("action")))throw new IllegalArgumentException("INVALID_SHARED_CHECKPOINT");
   int n=integer(row.get("quantity")),turn=integer(row.get("turn")),sx=integer(row.get("x")),sy=integer(row.get("y"));if(n<1||n>20||turn<lastTurn||turn>session.getTurn()||!map.getXRange().contains(sx)||!map.getYRange().contains(sy))throw new IllegalArgumentException("INVALID_SHARED_CHECKPOINT");
   lastTurn=turn;ledger.add(new LinkedHashMap<>(row));Map<String,Object> totals=contributions();for(String who:Arrays.asList("PLAYER","AGENT")){Map<String,Object> t=Json.asObject(totals.get(who));if(integer(t.get("delivered"))>integer(t.get("picked"))+integer(t.get("purchased")))throw new IllegalArgumentException("INVALID_SHARED_CHECKPOINT");}
  }
  Map<String,Object> totals=contributions();int total=0;for(String who:Arrays.asList("PLAYER","AGENT")){Map<String,Object> t=Json.asObject(totals.get(who));int delivered=integer(t.get("delivered"));total+=delivered;if(integer(t.get("picked"))+integer(t.get("purchased"))-delivered!=count("PLAYER".equals(who)?player:agent))throw new IllegalArgumentException("INVALID_SHARED_CHECKPOINT");}
  if(total!=session.deliveredCount()||total>session.requiredCount())throw new IllegalArgumentException("INVALID_SHARED_CHECKPOINT");
 }
}
