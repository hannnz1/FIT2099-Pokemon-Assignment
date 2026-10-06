package game.agent.growth;
import game.agent.action.ActionResult;import game.agent.llm.Json;import java.util.*;
/** Persisted one-partner introduction; practice operates on copies, never the owned Actor. */
public final class FirstAdventure {
 private static final List<String> STAGES=Arrays.asList("CHOOSE","MOVE","SELECT","PRACTICE","FOREST","BATTLE","REST_FIRST","CAPTURE","CAMP","GROWTH","RETURN","TRAINER","COMPLETE","SKIPPED");
 private boolean rookieWon,trainerWon;private String stage="CHOOSE",starterId,species;private int moves,practiceDamage;
 public static FirstAdventure legacy(){FirstAdventure s=new FirstAdventure();s.stage="SKIPPED";return s;}
 public boolean trainerVictory(String id,String difficulty){if(id==null||starterId!=null&&!starterId.equals(id)||!Arrays.asList("TRAINER","COMPLETE","SKIPPED").contains(stage))return false;if("rookie".equals(difficulty)){if(rookieWon)return false;rookieWon=true;if("TRAINER".equals(stage))stage="COMPLETE";return true;}if("standard".equals(difficulty)){if(trainerWon)return false;trainerWon=true;return true;}return false;}
 public boolean active(){return !Arrays.asList("COMPLETE","SKIPPED").contains(stage);}
 public boolean needsCapture(){return "CAPTURE".equals(stage);}
 public String starterId(){return starterId;}
 public Map<String,Object> view(){return Json.object("stage",stage,"starterId",starterId,"species",species,"moves",moves,"practiceDamage",practiceDamage,"rookieWon",rookieWon,"trainerWon",trainerWon);}
 public static FirstAdventure restore(Map<String,Object> v){
  Set<String> fields=new HashSet<>(Arrays.asList("stage","starterId","species","moves","practiceDamage"));if(v.containsKey("rookieWon"))fields.add("rookieWon");if(v.containsKey("trainerWon"))fields.add("trainerWon");if(!v.keySet().equals(fields))throw new IllegalArgumentException("INVALID_FIRST_ADVENTURE");
  FirstAdventure s=new FirstAdventure();for(String flag:Arrays.asList("rookieWon","trainerWon"))if(v.containsKey(flag)&&!(v.get(flag) instanceof Boolean))throw new IllegalArgumentException("INVALID_FIRST_ADVENTURE");s.rookieWon=Boolean.TRUE.equals(v.get("rookieWon"));s.trainerWon=Boolean.TRUE.equals(v.get("trainerWon"));s.stage=(String)v.get("stage");s.starterId=(String)v.get("starterId");s.species=(String)v.get("species");s.moves=GrowthPokemon.num(v.get("moves"));s.practiceDamage=GrowthPokemon.num(v.get("practiceDamage"));
  if(!STAGES.contains(s.stage)||s.moves<0||s.moves>2||s.practiceDamage<0||s.practiceDamage>10000||s.starterId!=null&&!s.starterId.matches("[a-f0-9-]{36}")||s.species!=null&&!Arrays.asList("TREECKO","MUDKIP","TORCHIC").contains(s.species)||(!Arrays.asList("CHOOSE","SKIPPED").contains(s.stage)&&(s.starterId==null||s.species==null)))throw new IllegalArgumentException("INVALID_FIRST_ADVENTURE");return s;
 }
 public ActionResult action(String kind,String moveId,GrowthWorld w){
  if("STORY_SKIP".equals(kind)){if(!active())return ActionResult.rejected("STORY_INACTIVE");stage="SKIPPED";return ActionResult.success("STORY_SKIPPED");}
  GrowthPokemon p=w.partner();if(p==null||!p.captureId.equals(starterId))return ActionResult.rejected("STORY_STARTER_REQUIRED");
  if("PRACTICE_SELECT".equals(kind)){if(!"SELECT".equals(stage)||!"lab".equals(w.region()))return ActionResult.rejected("PRACTICE_NOT_READY");stage="PRACTICE";return ActionResult.success("PRACTICE_SELECTED");}
  if("PRACTICE_SKILL".equals(kind)){
   if(!"PRACTICE".equals(stage)||!"lab".equals(w.region()))return ActionResult.rejected("PRACTICE_NOT_READY");
   if(!p.pp.containsKey(moveId)||p.pp.get(moveId)<=0||GrowthRules.move(moveId).power<=0)return ActionResult.rejected("PRACTICE_DAMAGE_MOVE_REQUIRED");
   GrowthPokemon copy=GrowthPokemon.restore(p.export()),dummy=new GrowthPokemon(UUID.randomUUID().toString(),"TREECKO",5);int hp=dummy.getHitPoints();new GrowthCombat(()->0).chosenMove(copy,dummy,moveId);practiceDamage=Math.max(0,hp-dummy.getHitPoints());stage="FOREST";
   return ActionResult.of(ActionResult.Status.SUCCESS,"PRACTICE_COMPLETED",Collections.singletonMap("damage",Integer.toString(practiceDamage)));
  }
  if("STORY_CONTINUE".equals(kind)&&"GROWTH".equals(stage)){stage="RETURN";return ActionResult.success("STORY_RETURN");}
  if("STORY_FINISH".equals(kind)&&"RETURN".equals(stage)&&"lab".equals(w.region())){stage="TRAINER";return ActionResult.success("STORY_TRAINER_READY");}
  return ActionResult.rejected("STORY_NOT_READY");
 }
 public void observe(GrowthWorld w,Map<String,Object> before,ActionResult a){
  if(!active()||a.getStatus()!=ActionResult.Status.SUCCESS&&a.getStatus()!=ActionResult.Status.IN_PROGRESS)return;
  GrowthPokemon p=w.partner();if("CHOOSE".equals(stage)){if("STARTER_CHOSEN".equals(a.getCode())&&p!=null){starterId=p.captureId;species=p.species;stage="MOVE";}else if(p!=null)stage="SKIPPED";return;}
  if(p==null||!p.captureId.equals(starterId))return;
  Map<String,Object> now=w.view();
  if("MOVE".equals(stage)&&(!Objects.equals(before.get("x"),now.get("x"))||!Objects.equals(before.get("y"),now.get("y"))||!Objects.equals(before.get("region"),now.get("region")))){moves=Math.min(2,moves+1);if(moves==2)stage="SELECT";}
  if("FOREST".equals(stage)&&"forest".equals(w.region()))stage=w.hasFirstAdventureForestFriend(starterId,species)?"CAMP":"BATTLE";
  if("BATTLE".equals(stage)){
   if(w.hasFirstAdventureForestFriend(starterId,species))stage="CAMP";
   else if(Arrays.asList("SKILL_USED","GROWTH_DEFEATED","PARTNER_FAINTED").contains(a.getCode())&&a.getData().containsKey("moveId"))stage="REST_FIRST";
  }
  if("REST_FIRST".equals(stage)&&"forest".equals(w.region())&&w.canRest()){w.rest();stage="CAPTURE";}

  if("CAPTURE".equals(stage)){
   boolean captured=w.hasFirstAdventureForestFriend(starterId,species);
   if(captured&&"forest".equals(w.region()))stage="CAMP";else w.ensureFirstAdventureTargets(species);
  }
  if("CAMP".equals(stage)&&"forest".equals(w.region())&&w.canRest()){w.rest();w.firstAdventureReward(starterId);stage="GROWTH";}
 }
}
