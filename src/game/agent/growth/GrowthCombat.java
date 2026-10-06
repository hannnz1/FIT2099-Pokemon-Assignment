package game.agent.growth;
import java.util.*;import java.util.function.IntSupplier;
/** Shared world-thread skill effects for growth and original-map battles. */
public final class GrowthCombat {
 private final IntSupplier random;
 private final List<Map<String,Object>> events=new ArrayList<>();private String lastOutcome="RESOLVED";
 public List<Map<String,Object>> events(){return Collections.unmodifiableList(new ArrayList<>(events));}public String lastOutcome(){return lastOutcome;}
 private void prevented(GrowthPokemon a,GrowthPokemon d,String move){lastOutcome="STATUS_PREVENTED";events.add(game.agent.llm.Json.object("type","MOVE","actorId",a.captureId,"targetId",d.captureId,"moveId",move,"outcome",lastOutcome,"damage",0,"actorHpAfter",a.getHitPoints(),"targetHpAfter",d.getHitPoints()));}
 public GrowthCombat(IntSupplier random){this.random=Objects.requireNonNull(random);}
 /** One explicitly chosen duel move. No automatic counterattack or residual tick. */
 public void chosenMove(GrowthPokemon a,GrowthPokemon d,String id){
  if(!a.isConscious()||!d.isConscious())return;
  if(!"STRUGGLE".equals(id)&&(!a.pp.containsKey(id)||a.pp.get(id)<=0))throw new IllegalArgumentException("MOVE_UNAVAILABLE");
  if("STRUGGLE".equals(id)&&a.pp.values().stream().anyMatch(n->n>0))throw new IllegalArgumentException("STRUGGLE_NOT_ALLOWED");
  if(!ready(a)){prevented(a,d,id);return;}
  if(!"STRUGGLE".equals(id))a.pp.put(id,a.pp.get(id)-1);
  strike(a,d,"STRUGGLE".equals(id)?GrowthRules.STRUGGLE:GrowthRules.move(id));
 }
 public double effectiveSpeed(GrowthPokemon p){return speed(p);}
 public void counter(GrowthPokemon attacker,GrowthPokemon defender){if(!attacker.isConscious()||!defender.isConscious())return;if(!ready(attacker)){prevented(attacker,defender,"UNKNOWN");return;}String chosen=null;for(String id:attacker.pp.keySet())if(GrowthRules.move(id).power>0&&attacker.pp.get(id)>0)chosen=id;if(chosen==null)strike(attacker,defender,GrowthRules.STRUGGLE);else{attacker.pp.put(chosen,attacker.pp.get(chosen)-1);strike(attacker,defender,GrowthRules.move(chosen));}}
 public void residual(GrowthPokemon a,GrowthPokemon b){for(GrowthPokemon p:Arrays.asList(a,b))if((p.burned||"POISON".equals(p.status))&&p.isConscious()){int before=p.getHitPoints();p.hurt(Math.min(before,Math.max(1,p.maxHp()/8)));events.add(game.agent.llm.Json.object("type","RESIDUAL","actorId",p.captureId,"targetId",p.captureId,"status",p.burned?"BURN":"POISON","damage",before-p.getHitPoints(),"targetHpAfter",p.getHitPoints()));}}
 public void turn(GrowthPokemon a,GrowthPokemon d,String moveId){
  GrowthRules.Move move=GrowthRules.move(moveId);
  boolean first=move.priority>0||speed(a)>=speed(d);
  if(!first)counter(d,a);if(a.isConscious()&&d.isConscious()){if(ready(a)){a.pp.put(moveId,a.pp.get(moveId)-1);strike(a,d,move);}else prevented(a,d,moveId);}if(first&&d.isConscious())counter(d,a);residual(a,d);
 }
 public void strike(GrowthPokemon a,GrowthPokemon d,GrowthRules.Move m){
  int before=d.getHitPoints(),selfBefore=a.getHitPoints();lastOutcome="RESOLVED";try {
  if(!"CHAIN".equals(m.effect))a.chain=0;
  if("SPEED_UP_2".equals(m.effect)){a.speedStage=Math.min(6,a.speedStage+2);return;}
  if("BULK_UP".equals(m.effect)){a.attackStage=Math.min(6,a.attackStage+1);a.defenseStage=Math.min(6,a.defenseStage+1);return;}
  double accuracy=a.accuracyStage>=0?(3.0+a.accuracyStage)/3:3.0/(3-a.accuracyStage);
  if(Math.floorMod(random.getAsInt(),100)>=m.accuracy*accuracy){a.chain=0;lastOutcome="MISSED";return;}
  if(m.power==0){if(GrowthRules.typeMultiplier(m.type,GrowthRules.species(d.species).types)==0){lastOutcome="IMMUNE";return;}if("CONFUSION".equals(m.effect))d.confusionTurns=3;if(Arrays.asList("PARALYSIS","SLEEP").contains(m.effect))inflict(d,m.effect);if("DEF_DOWN".equals(m.effect))d.defenseStage=Math.max(-6,d.defenseStage-1);if("DEF_DOWN_2".equals(m.effect))d.defenseStage=Math.max(-6,d.defenseStage-2);if("ATK_DOWN".equals(m.effect))d.attackStage=Math.max(-6,d.attackStage-1);if("ACCURACY_DOWN".equals(m.effect)&&!"KEEN_EYE".equals(GrowthRules.ability(d.species)))d.accuracyStage=Math.max(-6,d.accuracyStage-1);return;}
  int power=m.power;if(GrowthRules.typeMultiplier(m.type,GrowthRules.species(d.species).types)==0)lastOutcome="IMMUNE";if("CHAIN".equals(m.effect)){power*=1<<a.chain;a.chain=Math.min(4,a.chain+1);}else a.chain=0;
  boolean critical=Math.floorMod(random.getAsInt(),m.effect.startsWith("CRITICAL")?8:16)==0;int totalDamage=0;
  for(int i=0;i<("DOUBLE".equals(m.effect)?2:1)&&d.isConscious()&&a.isConscious();i++){
   int damage=Math.min(d.getHitPoints(),GrowthRules.damage(a,d,m,random.getAsInt(),power,critical));totalDamage+=damage;d.hurt(damage);
   if("DRAIN".equals(m.effect)&&damage>0)a.heal(Math.max(1,damage/2));
   if("RECOIL".equals(m.effect)&&damage>0)a.hurt(Math.min(a.getHitPoints(),Math.max(1,damage/4)));
  }
  if(totalDamage>0&&d.isConscious()){
   if(m.effect.equals("POISON_30")&&Math.floorMod(random.getAsInt(),100)<30)inflict(d,"POISON");
   if(m.effect.startsWith("PARALYSIS_")&&Math.floorMod(random.getAsInt(),100)<(m.effect.endsWith("30")?30:10))inflict(d,"PARALYSIS");
   if(!GrowthRules.special(m.type)&&"STATIC".equals(GrowthRules.ability(d.species))&&Math.floorMod(random.getAsInt(),100)<30)inflict(a,"PARALYSIS");
   if("SPEED_DOWN".equals(m.effect))d.speedStage=Math.max(-6,d.speedStage-1);
   if(!"KEEN_EYE".equals(GrowthRules.ability(d.species))&&("ACCURACY_DOWN".equals(m.effect)||"ACCURACY_DOWN_30".equals(m.effect)&&Math.floorMod(random.getAsInt(),100)<30))d.accuracyStage=Math.max(-6,d.accuracyStage-1);
   if(Arrays.asList("BURN","CRITICAL_BURN").contains(m.effect)&&!Arrays.asList(GrowthRules.species(d.species).types).contains("FIRE")&&Math.floorMod(random.getAsInt(),100)<10)inflict(d,"BURN");
  }
  }finally{events.add(game.agent.llm.Json.object("type","MOVE","actorId",a.captureId,"targetId",d.captureId,"moveId",m.id,"outcome",lastOutcome,"damage",Math.max(0,before-d.getHitPoints()),"actorHpBefore",selfBefore,"actorHpAfter",a.getHitPoints(),"targetHpBefore",before,"targetHpAfter",d.getHitPoints()));}
 }
 private double speed(GrowthPokemon p){return p.stats()[5]*GrowthRules.stage(p.speedStage)*("PARALYSIS".equals(p.status)?.25:1);}
 private boolean ready(GrowthPokemon p){
  if("SLEEP".equals(p.status)){p.sleepTurns--;if(p.sleepTurns==0)p.status="NONE";return false;}
  if("PARALYSIS".equals(p.status)&&Math.floorMod(random.getAsInt(),4)==0)return false;
  if(p.confusionTurns>0){p.confusionTurns--;if(Math.floorMod(random.getAsInt(),2)==0){int[] st=p.stats();int damage=Math.max(1,((2*p.level()/5+2)*40*st[1]/st[2])/50+2);p.hurt(Math.min(p.getHitPoints(),damage));return false;}}
  return p.isConscious();
 }
 private void inflict(GrowthPokemon p,String status){
  if(p.burned||!"NONE".equals(p.status))return;List<String> types=Arrays.asList(GrowthRules.species(p.species).types);
  if("BURN".equals(status)){if(!types.contains("FIRE"))p.burned=true;return;}
  if("POISON".equals(status)&&(types.contains("POISON")||types.contains("STEEL")))return;
  p.status=status;if("SLEEP".equals(status))p.sleepTurns=2;
 }

}
