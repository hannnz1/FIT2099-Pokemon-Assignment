package game.agent.growth;

import game.agent.llm.Json;
import java.util.*;

/** Persistent Demo campaign and lifetime dex; no reward is tied to the current team size. */
public final class GrowthAdventure {
    boolean enabled;
    private final Set<String> seen=new LinkedHashSet<>(),caught=new LinkedHashSet<>(),cleared=new LinkedHashSet<>();
    public void see(String species){GrowthRules.species(species);seen.add(species);}
    public void own(String species){see(species);caught.add(species);}
    public void clear(String region){if(enabled&&GrowthMaps.wild(region))cleared.add(region);}
    public boolean unlocked(String region){return !enabled||Arrays.asList("lab","forest").contains(region)||"river".equals(region)&&cleared.contains("forest")||"mountain".equals(region)&&cleared.contains("river");}
    public boolean ready(String reward){return reward.startsWith("ADVENTURE_")?enabled&&cleared.contains(reward.substring(10).toLowerCase(Locale.ROOT)):"DEX_THREE".equals(reward)&&caught.size()>=3;}
    public static int reward(String id){return "ADVENTURE_FOREST".equals(id)?300:"ADVENTURE_RIVER".equals(id)?500:"ADVENTURE_MOUNTAIN".equals(id)?800:300;}
    public Map<String,Object> export(){return Json.object("enabled",enabled,"seen",new ArrayList<>(seen),"caught",new ArrayList<>(caught),"cleared",new ArrayList<>(cleared));}
    public Map<String,Object> view(Set<String> claims){Map<String,Object> v=export();v.put("completed",enabled&&cleared.containsAll(Arrays.asList("forest","river","mountain")));List<Object> quests=new ArrayList<>();for(String id:Arrays.asList("ADVENTURE_FOREST","ADVENTURE_RIVER","ADVENTURE_MOUNTAIN","DEX_THREE"))quests.add(Json.object("id",id,"ready",ready(id),"claimed",claims.contains(id),"reward",reward(id)));v.put("quests",quests);List<Object> dex=new ArrayList<>();for(String id:GrowthRules.speciesIds())dex.add(Json.object("species",id,"name",GrowthRules.species(id).name,"seen",seen.contains(id),"caught",caught.contains(id)));v.put("dex",dex);return v;}
    public static GrowthAdventure restore(Map<String,Object> saved){if(!saved.keySet().equals(new HashSet<>(Arrays.asList("enabled","seen","caught","cleared")))||!(saved.get("enabled") instanceof Boolean))throw new IllegalArgumentException("INVALID_ADVENTURE");GrowthAdventure a=new GrowthAdventure();a.enabled=(Boolean)saved.get("enabled");for(Object id:Json.asArray(saved.get("seen")))if(!(id instanceof String)||!GrowthRules.speciesIds().contains(id)||!a.seen.add((String)id))throw new IllegalArgumentException("INVALID_DEX");for(Object id:Json.asArray(saved.get("caught")))if(!(id instanceof String)||!a.seen.contains(id)||!a.caught.add((String)id))throw new IllegalArgumentException("INVALID_DEX");for(Object id:Json.asArray(saved.get("cleared")))if(!(id instanceof String)||!GrowthMaps.wild((String)id)||!a.cleared.add((String)id))throw new IllegalArgumentException("INVALID_ADVENTURE");if(!a.enabled&&!a.cleared.isEmpty()||a.cleared.contains("mountain")&&!a.cleared.contains("river")||a.cleared.contains("river")&&!a.cleared.contains("forest"))throw new IllegalArgumentException("INVALID_ADVENTURE_ORDER");return a;}
    /** Region/round/slot schedule; all six new bases occur across the first campaign route. */
    public static String encounter(String region,int wave,int slot){String[] pool="forest".equals(region)?new String[]{"POOCHYENA","ZIGZAGOON","SHROOMISH","TREECKO"}:"river".equals(region)?new String[]{"WINGULL","TAILLOW","MUDKIP","SHROOMISH"}:new String[]{"ELECTRIKE","TORCHIC","POOCHYENA","ZIGZAGOON"};return pool[Math.floorMod((wave-1)*3+slot,pool.length)];}
}
