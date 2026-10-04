package game.agent.growth;
import game.agent.llm.Json;import edu.monash.fit2099.engine.positions.*;import edu.monash.fit2099.engine.displays.Display;import game.environments.Dirt;import game.environments.structures.Wall;import java.util.*;import java.io.*;import java.nio.charset.StandardCharsets;
/** Both Java movement and Phaser scenery consume this manifest derived from existing Tiled maps. */
public final class GrowthMaps {
 private static final Map<String,Object> MAPS=load();private GrowthMaps(){}
 private static Map<String,Object> load(){try(InputStream in=GrowthMaps.class.getResourceAsStream("/web/growth-maps.json")){if(in==null)throw new IOException("GROWTH_MAPS_MISSING");ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] chunk=new byte[4096];for(int n;(n=in.read(chunk))!=-1;)b.write(chunk,0,n);return Json.asObject(Json.asObject(Json.read(new String(b.toByteArray(),StandardCharsets.UTF_8))).get("maps"));}catch(IOException e){throw new IllegalStateException("GROWTH_MAPS_MISSING",e);}}
 public static Map<String,Object> definition(String id){if(!MAPS.containsKey(id))throw new IllegalArgumentException("UNKNOWN_REGION");return Json.asObject(MAPS.get(id));}
 public static List<String> regions(){return new ArrayList<>(MAPS.keySet());}
 public static boolean wild(String id){return MAPS.containsKey(id)&&!"lab".equals(id);}
 public static List<Object> exits(String id){return Json.asArray(definition(id).get("exits"));}
 /** Find the first gate on a shortest region route; the native navigator handles tiles. */
 public static Map<String,Object> nextExit(String from,String to){
  definition(from);definition(to);Queue<String> q=new ArrayDeque<>();Map<String,String> previous=new HashMap<>();q.add(from);previous.put(from,null);
  while(!q.isEmpty()){String current=q.remove();if(current.equals(to))break;for(Object row:exits(current)){String next=(String)Json.asObject(row).get("regionId");if(!previous.containsKey(next)){previous.put(next,current);q.add(next);}}}
  if(!previous.containsKey(to)||from.equals(to))throw new IllegalArgumentException("NO_REGION_ROUTE");String hop=to;while(!from.equals(previous.get(hop)))hop=previous.get(hop);
  for(Object row:exits(from))if(hop.equals(Json.asObject(row).get("regionId")))return Json.asObject(row);throw new IllegalArgumentException("NO_REGION_ROUTE");
 }
 public static int coordinate(String region,String point,String axis){return GrowthPokemon.num(Json.asObject(definition(region).get(point)).get(axis));}
 public static GameMap create(String id){Map<String,Object> d=definition(id);int width=GrowthPokemon.num(d.get("width")),height=GrowthPokemon.num(d.get("height"));List<Object> c=Json.asArray(d.get("collision"));if(width<1||height<1||c.size()!=width*height)throw new IllegalArgumentException("INVALID_MAP");List<String> rows=new ArrayList<>();for(int y=0;y<height;y++){StringBuilder row=new StringBuilder();for(int x=0;x<width;x++)row.append(GrowthPokemon.num(c.get(y*width+x))==0?'.':'#');rows.add(row.toString());}GameMap map=new GameMap(new FancyGroundFactory(new Dirt(),new Wall()),rows);new World(new Display()).addGameMap(map);return map;}
}
