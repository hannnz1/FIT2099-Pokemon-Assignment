package game.agent.web;
import game.actors.Player;
import game.agent.growth.GrowthPokemon;
import game.actors.pokemon.Treecko;
import game.agent.combat.PokemonSpecies;
import game.items.balls.Pokeball;
import game.agent.llm.Json;
import game.agent.persistence.WorldStore;
import game.time.TimePerceptionManager;
import java.util.*;
import java.math.BigDecimal;
/** Single durable ownership authority, independent of resettable scene checkpoints. World-thread only. */
final class PokemonCollection {
    private final String owner;
    private final WorldStore store;
    private final Player trainer=new Player("Collection trainer",'@',100);
    private final Map<String,Pokeball> balls=new LinkedHashMap<>();
    private boolean failed;private final Map<String,Map<String,Object>> training=new LinkedHashMap<>();
    private Map<String,Object> deployment;
    PokemonCollection(String owner,WorldStore store){this.owner="collection:"+owner;this.store=store;recover();}
    boolean available(){return !failed;}
    boolean received(String captureId){return balls.containsKey(captureId)||training.containsKey(captureId);}
    boolean inCollection(String id){return balls.containsKey(id);}
    boolean inTraining(String id){return training.containsKey(id);}
    List<Map<String,Object>> trainingProfiles(){return new ArrayList<>(training.values());}
    Pokeball ball(String captureId){return balls.get(captureId);}
    Map<String,Object> deployment(){return deployment==null?null:new LinkedHashMap<>(deployment);}
    List<Map<String,Object>> snapshot(){List<Map<String,Object>> rows=new ArrayList<>();for(Map.Entry<String,Pokeball> e:balls.entrySet()){
        boolean out=deployment!=null&&e.getKey().equals(deployment.get("captureId"));
        if(trainer.getInventory().contains(e.getValue())==out)throw new IllegalStateException("COLLECTION_INVENTORY_MISMATCH");
        Map<String,Object> value=row(e.getKey(),e.getValue());value.put("state",out?"DEPLOYED":"IN_BALL");if(out){value.put("x",deployment.get("x"));value.put("y",deployment.get("y"));}rows.add(value);
    }return rows;}
    private List<Map<String,Object>> records(){List<Map<String,Object>> rows=new ArrayList<>();for(Map.Entry<String,Pokeball> e:balls.entrySet())rows.add(row(e.getKey(),e.getValue()));return rows;}
    private boolean commit(List<Map<String,Object>> rows,Map<String,Object> out){try{if(store!=null)store.save(owner,Json.object("schemaVersion",1,"kind","POKEMON_COLLECTION","pokemon",rows,"deployment",out,"training",new ArrayList<>(training.values())));return true;}catch(RuntimeException error){failed=true;return false;}}
    String summon(String captureId,Map<String,Object> point){
        if(failed)return "STORAGE_UNAVAILABLE";if(!balls.containsKey(captureId))return "NOT_OWNED";if(deployment!=null)return "ALREADY_SUMMONED";
        Map<String,Object> next=Json.object("captureId",captureId,"x",point.get("x"),"y",point.get("y"),"deploymentId",UUID.randomUUID().toString());
        if(!commit(records(),next))return "STORAGE_UNAVAILABLE";
        deployment=next;trainer.removeItemFromInventory(balls.get(captureId));return "ACCEPTED";
    }
    String recall(String captureId){
        if(failed)return "STORAGE_UNAVAILABLE";if(deployment==null||!captureId.equals(deployment.get("captureId")))return "NOT_SUMMONED";
        if(!commit(records(),null))return "STORAGE_UNAVAILABLE";
        trainer.addItemToInventory(balls.get(captureId));deployment=null;return "ACCEPTED";
    }
    String heal(String captureId){
        if(failed)return "STORAGE_UNAVAILABLE";if(!balls.containsKey(captureId))return "NOT_OWNED";
        if(deployment!=null&&captureId.equals(deployment.get("captureId")))return "RECALL_BEFORE_REST";
        List<Map<String,Object>> next=records();for(Map<String,Object> row:next)if(captureId.equals(row.get("captureId"))){row.put("hp",row.get("maxHp"));if(row.containsKey("growth")){GrowthPokemon p=GrowthPokemon.restore(Json.asObject(row.get("growth")));p.rest();row.put("growth",p.export());}}
        if(!commit(next,deployment))return "STORAGE_UNAVAILABLE";if(balls.get(captureId).getPokemon() instanceof GrowthPokemon)((GrowthPokemon)balls.get(captureId).getPokemon()).rest();else balls.get(captureId).getPokemon().heal(100);return "ACCEPTED";
    }
    String recallAll(){if(failed)return "STORAGE_UNAVAILABLE";return deployment==null?"ACCEPTED":recall((String)deployment.get("captureId"));}
    private static Map<String,Object> row(String id,Pokeball ball){if(ball.getPokemon() instanceof GrowthPokemon){GrowthPokemon p=(GrowthPokemon)ball.getPokemon();Map<String,Object> v=Json.object("captureId",id,"species",p.species,"hp",p.getHitPoints(),"maxHp",p.maxHp(),"growth",p.export());return v;}return Json.object("captureId",id,"species",PokemonSpecies.of(ball.getPokemon()),"hp",ball.getPokemon().getHitPoints(),"maxHp",100);}
    String receive(String captureId,Pokeball ball){
        if(failed)return "STORAGE_UNAVAILABLE";
        if(balls.containsKey(captureId))return "ACCEPTED";
        if(!training.containsKey(captureId)&&balls.size()+training.size()>=20)return "COLLECTION_FULL";
        if(ball!=null&&ball.getPokemon() instanceof GrowthPokemon&&!((GrowthPokemon)ball.getPokemon()).captureId.equals(captureId))return "NOT_CAPTURED";
        if(captureId==null||!captureId.matches("[a-f0-9-]{36}")||ball==null||!ball.containsPokemon()||!PokemonSpecies.portable(ball.getPokemon())||ball.getPokemon().getHitPoints()<1||ball.getPokemon().getHitPoints()>(ball.getPokemon() instanceof GrowthPokemon?((GrowthPokemon)ball.getPokemon()).maxHp():100))return "NOT_CAPTURED";
        List<Map<String,Object>> next=records();next.add(row(captureId,ball));
        // Publish memory ownership only after this one atomic file/DB commit succeeds.
        Map<String,Object> prior=training.remove(captureId);
        if(!commit(next,deployment)){if(prior!=null)training.put(captureId,prior);return "STORAGE_UNAVAILABLE";}
        balls.put(captureId,ball);trainer.addItemToInventory(ball);return "ACCEPTED";
    }
    String returnToGrowth(String id){
        if(failed)return "STORAGE_UNAVAILABLE";if(training.containsKey(id))return "ACCEPTED";
        Pokeball ball=balls.get(id);if(ball==null)return "NOT_OWNED";
        if(deployment!=null&&id.equals(deployment.get("captureId")))return "RECALL_BEFORE_TRAINING";
        GrowthPokemon profile;
        if(ball.getPokemon() instanceof GrowthPokemon)profile=(GrowthPokemon)ball.getPokemon();
        else {
            // Legacy saves have no level/EXP/PP. Convert only on an explicit lab transfer.
            profile=new GrowthPokemon(id,PokemonSpecies.of(ball.getPokemon()),5);
            int hp=Math.max(1,ball.getPokemon().getHitPoints()*profile.maxHp()/100);
            profile.hurt(profile.maxHp()-hp);
        }
        List<Map<String,Object>> next=records();next.removeIf(row->id.equals(row.get("captureId")));
        training.put(id,profile.export());
        if(!commit(next,deployment)){training.remove(id);return "STORAGE_UNAVAILABLE";}
        balls.remove(id);trainer.removeItemFromInventory(ball);return "ACCEPTED";
    }
    void recover(){
        Map<String,Object> saved=store==null?null:store.load(owner);
        Map<String,Pokeball> restored=new LinkedHashMap<>();
        Map<String,Object> restoredDeployment=null;Map<String,Map<String,Object>> restoredTraining=new LinkedHashMap<>();
        if(saved!=null){
            Set<String> fields=new HashSet<>(Arrays.asList("schemaVersion","kind","pokemon"));if(saved.containsKey("deployment"))fields.add("deployment");if(saved.containsKey("training"))fields.add("training");
            if(!saved.keySet().equals(fields)||number(saved.get("schemaVersion"))!=1||!"POKEMON_COLLECTION".equals(saved.get("kind")))throw new IllegalArgumentException("INVALID_COLLECTION");
            List<Object> rows=Json.asArray(saved.get("pokemon"));if(rows.size()>20)throw new IllegalArgumentException("INVALID_COLLECTION");
            for(Object value:rows){Map<String,Object> row=Json.asObject(value);Object id=row.get("captureId");long hp=number(row.get("hp"));
                if(row.containsKey("growth")){if(!row.keySet().equals(new HashSet<>(Arrays.asList("captureId","species","hp","maxHp","growth"))))throw new IllegalArgumentException("INVALID_COLLECTION");GrowthPokemon p=GrowthPokemon.restore(Json.asObject(row.get("growth")));if(!p.captureId.equals(id)||restored.containsKey(id)||!p.species.equals(row.get("species"))||p.getHitPoints()!=hp||p.maxHp()!=number(row.get("maxHp")))throw new IllegalArgumentException("INVALID_COLLECTION");restored.put((String)id,new Pokeball().capturePokemon(p));continue;}
                if(!row.keySet().equals(new HashSet<>(Arrays.asList("captureId","species","hp","maxHp")))||!(id instanceof String)||!((String)id).matches("[a-f0-9-]{36}")||restored.containsKey(id)||!Arrays.asList("TREECKO","MUDKIP").contains(row.get("species"))||hp<1||hp>100||number(row.get("maxHp"))!=100)throw new IllegalArgumentException("INVALID_COLLECTION");
                game.actors.pokemon.Pokemon pokemon=PokemonSpecies.create((String)row.get("species"));pokemon.hurt(100-(int)hp);restored.put((String)id,new Pokeball().capturePokemon(pokemon));
            }
            if(saved.containsKey("training"))for(Object item:Json.asArray(saved.get("training"))){GrowthPokemon p=GrowthPokemon.restore(Json.asObject(item));if(restored.containsKey(p.captureId)||restoredTraining.put(p.captureId,p.export())!=null||restored.size()+restoredTraining.size()>20)throw new IllegalArgumentException("INVALID_TRAINING_OWNERSHIP");}
            if(saved.get("deployment")!=null){Map<String,Object> out=Json.asObject(saved.get("deployment"));
                Set<String> keys=new HashSet<>(Arrays.asList("captureId","x","y"));if(out.containsKey("deploymentId")){keys.add("deploymentId");if(!(out.get("deploymentId") instanceof String)||!((String)out.get("deploymentId")).matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("INVALID_COLLECTION");}
                if(!out.keySet().equals(keys)||!restored.containsKey(out.get("captureId"))||number(out.get("x"))<0||number(out.get("x"))>100||number(out.get("y"))<0||number(out.get("y"))>100)throw new IllegalArgumentException("INVALID_COLLECTION");restoredDeployment=new LinkedHashMap<>(out);
            }
        }
        for(Pokeball ball:balls.values())trainer.removeItemFromInventory(ball);balls.clear();balls.putAll(restored);training.clear();training.putAll(restoredTraining);deployment=restoredDeployment;for(Map.Entry<String,Pokeball> e:balls.entrySet())if(deployment==null||!e.getKey().equals(deployment.get("captureId")))trainer.addItemToInventory(e.getValue());failed=false;
    }
    private static long number(Object value){if(!(value instanceof Number))throw new IllegalArgumentException("INVALID_COLLECTION");return new BigDecimal(value.toString()).longValueExact();}
}
