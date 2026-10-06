package game.agent.combat;
import edu.monash.fit2099.engine.actors.Actor;import edu.monash.fit2099.engine.positions.*;import game.actors.Player;import game.actors.pokemon.Pokemon;import game.items.balls.Pokeball;import game.agent.action.ActionResult;import game.agent.llm.Json;
import java.util.*;import java.util.function.Predicate;import java.util.function.IntSupplier;import game.agent.growth.*;
/** Native original-map targets and trainer inventory; ownership receipts stay in collection. */
public final class WildEncounter {
 private String battleTarget,battlePartner;private final IntSupplier random;
 private final GameMap map;private final Player trainer=new Player("Field trainer",'@',100);
 private final Map<String,Pokemon> targets=new LinkedHashMap<>();private final Map<String,String> ids=new LinkedHashMap<>();private final Set<String> transferred=new HashSet<>();
 private static final String[] NAMES={"field-treecko","field-mudkip","field-torchic"},SPECIES={"TREECKO","MUDKIP","TORCHIC"};
 private static final int[][] POINTS={{32,11},{34,10},{36,11}};
 public static Map<String,Object> spawnPoint(String id){for(int i=0;i<NAMES.length;i++)if(NAMES[i].equals(id))return Json.object("x",POINTS[i][0],"y",POINTS[i][1]);return null;}
 public Map<String,Object> state(String id){for(Object value:states()){Map<String,Object> row=Json.asObject(value);if(id.equals(row.get("id")))return row;}return null;}
 private WildEncounter(GameMap map){this(map,()->java.util.concurrent.ThreadLocalRandom.current().nextInt());}
 private WildEncounter(GameMap map,IntSupplier random){this.map=map;this.random=random;for(int i=0;i<NAMES.length;i++){targets.put(NAMES[i],PokemonSpecies.create(SPECIES[i]));ids.put(NAMES[i],UUID.randomUUID().toString());}}
 public static WildEncounter spawn(GameMap map){return spawn(map,()->java.util.concurrent.ThreadLocalRandom.current().nextInt());}
 public static WildEncounter spawn(GameMap map,IntSupplier random){WildEncounter w=new WildEncounter(map,random);int[][] points=POINTS;for(int i=0;i<points.length;i++)if(!map.at(points[i][0],points[i][1]).canActorEnter(w.targets.get(NAMES[i])))throw new IllegalArgumentException("NO_WILD_SPACE");for(int i=0;i<points.length;i++)map.addActor(w.targets.get(NAMES[i]),map.at(points[i][0],points[i][1]));return w;}
 public String captureId(String target){return ids.get(target);}
 public Set<String> targetIds(){return Collections.unmodifiableSet(targets.keySet());}
 public boolean isTransferred(String target){return transferred.contains(target);}
 public Pokeball ball(String target){Pokemon p=targets.get(target);for(edu.monash.fit2099.engine.items.Item item:trainer.getInventory())if(item instanceof Pokeball&&((Pokeball)item).containsPokemon()&&((Pokeball)item).getPokemon()==p)return (Pokeball)item;return null;}
 public void transfer(String target){if(transferred.contains(target))return;Pokeball ball=ball(target);if(ball==null)throw new IllegalStateException("NOT_CAPTURED");trainer.removeItemFromInventory(ball);transferred.add(target);}
 public ActionResult useSkill(Actor partner,String target,String moveId,boolean noBattle,Predicate<Location> allowed){
  if(noBattle)return ActionResult.rejected("NO_ACTIVE_BATTLE");
  if(!(partner instanceof GrowthPokemon))return ActionResult.rejected("GROWTH_PARTNER_REQUIRED");
  GrowthPokemon a=(GrowthPokemon)partner;String denied=growthGuard(partner,target,allowed);if(denied!=null)return ActionResult.rejected(denied);
  if(!a.pp.containsKey(moveId))return ActionResult.rejected("MOVE_NOT_EQUIPPED");if(a.pp.get(moveId)<=0)return ActionResult.rejected("NO_PP");
  Pokemon original=targets.get(target);GrowthPokemon d;
  if(original instanceof GrowthPokemon)d=(GrowthPokemon)original;
  else{d=new GrowthPokemon(ids.get(target),PokemonSpecies.of(original),5);d.hurt(d.maxHp()-Math.max(1,original.getHitPoints()*d.maxHp()/100));Location location=map.locationOf(original);map.removeActor(original);map.addActor(d,location);targets.put(target,d);}
  if(!target.equals(battleTarget)||!a.captureId.equals(battlePartner)){a.clearBattle();d.clearBattle();battleTarget=target;battlePartner=a.captureId;}
  int hp=a.getHitPoints(),targetHp=d.getHitPoints(),level=a.level();new GrowthCombat(random).turn(a,d,moveId);
  int retaliation=Math.max(0,hp-a.getHitPoints()),xp=0;
  if(!d.isConscious()){map.removeActor(d);xp=GrowthRules.reward(d.species,d.level());a.gainExperience(xp);a.awardEv(d.species);a.clearBattle();battleTarget=battlePartner=null;}
  return ActionResult.of(ActionResult.Status.SUCCESS,!d.isConscious()?"TARGET_DEFEATED":a.isConscious()?"SKILL_USED":"PARTNER_FAINTED",JsonStrings(target,moveId,targetHp-d.getHitPoints(),retaliation,xp,level,a));
 }
 private static Map<String,String> JsonStrings(String target,String move,int damage,int retaliation,int xp,int level,GrowthPokemon a){Map<String,String> data=new LinkedHashMap<>();data.put("targetId",target);data.put("moveId",move);data.put("damage",Integer.toString(Math.max(0,damage)));data.put("retaliationDamage",Integer.toString(retaliation));data.put("experienceGained",Integer.toString(xp));data.put("levelBefore",Integer.toString(level));data.put("levelAfter",Integer.toString(a.level()));data.put("actorHp",Integer.toString(a.getHitPoints()));return data;}
 private String growthGuard(Actor partner,String target,Predicate<Location> allowed){Pokemon p=targets.get(target);if(p==null)return "UNKNOWN_TARGET";if(transferred.contains(target)||ball(target)!=null||!map.contains(p)||!p.isConscious())return "TARGET_UNAVAILABLE";if(partner==null||!map.contains(partner)||!partner.isConscious())return "ACTOR_UNAVAILABLE";if(!allowed.test(map.locationOf(partner))||!allowed.test(map.locationOf(p)))return "AREA_RESTRICTED";for(Exit exit:map.locationOf(partner).getExits())if(exit.getDestination()==map.locationOf(p))return null;return "OUT_OF_REACH";}
 public ActionResult action(Actor partner,String action,String target,boolean noBattle,Predicate<Location> allowed,int turnDamage){
  Pokemon p=targets.get(target);if(p==null)return ActionResult.rejected("UNKNOWN_TARGET");if(transferred.contains(target)||ball(target)!=null||!map.contains(p))return ActionResult.rejected("TARGET_UNAVAILABLE");
  if("attack".equals(action)&&partner instanceof GrowthPokemon)return ActionResult.rejected("USE_SKILL_REQUIRED");
  if("capture".equals(action)&&"TORCHIC".equals(PokemonSpecies.of(p)))return ActionResult.rejected("NOT_CAPTURABLE");
  if("capture".equals(action)&&p instanceof GrowthPokemon){String denied=growthGuard(partner,target,allowed);if(denied!=null)return ActionResult.rejected(denied);new game.actions.CaptureAction(p).execute(trainer,map);if(partner instanceof GrowthPokemon)((GrowthPokemon)partner).clearBattle();((GrowthPokemon)p).clearBattle();battleTarget=battlePartner=null;return ActionResult.of(ActionResult.Status.SUCCESS,"CAPTURED",Collections.singletonMap("targetId",target));}
  CombatSession combat=new CombatSession(partner,trainer,map,targets,()->true,()->noBattle,allowed,()->{},()->true,()->turnDamage);
  ActionResult result="attack".equals(action)?combat.attack(target):"capture".equals(action)?combat.capture(target):ActionResult.rejected("UNKNOWN_MANUAL_ACTION");if(!map.contains(p)&&target.equals(battleTarget))battleTarget=battlePartner=null;return result;
 }
 public List<Object> states(){List<Object> rows=new ArrayList<>();for(String id:targets.keySet()){Pokemon p=targets.get(id);String state=transferred.contains(id)?"TRANSFERRED":ball(id)!=null?"CAPTURED":map.contains(p)?"WILD":"DEFEATED";Map<String,Object> row=Json.object("id",id,"captureId",ids.get(id),"species",PokemonSpecies.of(p),"hp",Math.max(0,p.getHitPoints()),"maxHp",p instanceof GrowthPokemon?((GrowthPokemon)p).maxHp():100,"state",state);if(p instanceof GrowthPokemon){row.put("growth",((GrowthPokemon)p).export());row.put("level",((GrowthPokemon)p).level());}if("WILD".equals(state)){Location pos=map.locationOf(p);row.put("x",pos.x());row.put("y",pos.y());}rows.add(row);}return rows;}
 public Map<String,Object> exportState(){return Json.object("schemaVersion",1,"targets",states(),"battleTarget",battleTarget,"battlePartner",battlePartner);}
 public static WildEncounter restore(GameMap map,Map<String,Object> saved){
  Set<String> saveFields=new HashSet<>(Arrays.asList("schemaVersion","targets"));if(saved.containsKey("battleTarget")){saveFields.add("battleTarget");saveFields.add("battlePartner");}
  if(!saved.keySet().equals(saveFields)||num(saved.get("schemaVersion"))!=1)throw new IllegalArgumentException("INVALID_WILD_CHECKPOINT");WildEncounter w=new WildEncounter(map);List<Object> rows=Json.asArray(saved.get("targets"));if(rows.size()!=3)throw new IllegalArgumentException("INVALID_WILD_CHECKPOINT");Set<String> seen=new HashSet<>(),captureIds=new HashSet<>();
  for(Object value:rows){Map<String,Object> row=Json.asObject(value);String id=(String)row.get("id"),capture=(String)row.get("captureId"),state=(String)row.get("state");Pokemon p=w.targets.get(id);Set<String> fields=new HashSet<>(Arrays.asList("id","captureId","species","hp","maxHp","state"));if("WILD".equals(state)){fields.add("x");fields.add("y");}if(p==null)throw new IllegalArgumentException("INVALID_WILD_CHECKPOINT");if(row.containsKey("growth")){fields.add("growth");fields.add("level");GrowthPokemon grown=GrowthPokemon.restore(Json.asObject(row.get("growth")));if(!grown.captureId.equals(capture)||!PokemonSpecies.of(p).equals(grown.species)||num(row.get("level"))!=grown.level())throw new IllegalArgumentException("INVALID_WILD_CHECKPOINT");p=grown;w.targets.put(id,p);}
   if(p==null||!seen.add(id)||!row.keySet().equals(fields)||!PokemonSpecies.of(p).equals(row.get("species"))||capture==null||!capture.matches("[a-f0-9-]{36}")||!captureIds.add(capture)||num(row.get("maxHp"))!=(p instanceof GrowthPokemon?((GrowthPokemon)p).maxHp():100))throw new IllegalArgumentException("INVALID_WILD_CHECKPOINT");int hp=num(row.get("hp"));if(hp<0||hp>(p instanceof GrowthPokemon?((GrowthPokemon)p).maxHp():100)||("DEFEATED".equals(state)?hp!=0:hp==0))throw new IllegalArgumentException("INVALID_WILD_CHECKPOINT");if(p instanceof GrowthPokemon){if(p.getHitPoints()!=hp)throw new IllegalArgumentException("INVALID_WILD_CHECKPOINT");}else p.hurt(100-hp);w.ids.put(id,capture);
   if("WILD".equals(state)){int x=num(row.get("x")),y=num(row.get("y"));if(!map.getXRange().contains(x)||!map.getYRange().contains(y)||!map.at(x,y).canActorEnter(p))throw new IllegalArgumentException("INVALID_WILD_CHECKPOINT");map.addActor(p,map.at(x,y));}
   else if("CAPTURED".equals(state)){if(!PokemonSpecies.portable(p))throw new IllegalArgumentException("INVALID_WILD_CHECKPOINT");w.trainer.addItemToInventory(new Pokeball().capturePokemon(p));}
   else if("TRANSFERRED".equals(state)){if(!PokemonSpecies.portable(p))throw new IllegalArgumentException("INVALID_WILD_CHECKPOINT");w.transferred.add(id);}
   else if(!"DEFEATED".equals(state))throw new IllegalArgumentException("INVALID_WILD_CHECKPOINT");
  }if(saved.get("battleTarget")!=null||saved.get("battlePartner")!=null){Object target=saved.get("battleTarget"),partner=saved.get("battlePartner");if(!(target instanceof String)||!(partner instanceof String)||!((String)partner).matches("[a-f0-9-]{36}")||!w.targets.containsKey(target)||!(w.targets.get(target) instanceof GrowthPokemon)||!"WILD".equals(w.state((String)target).get("state")))throw new IllegalArgumentException("INVALID_WILD_CHECKPOINT");w.battleTarget=(String)target;w.battlePartner=(String)partner;}return w;
 }
 private static int num(Object value){if(!(value instanceof Number))throw new IllegalArgumentException("INVALID_WILD_CHECKPOINT");return new java.math.BigDecimal(value.toString()).intValueExact();}
}

