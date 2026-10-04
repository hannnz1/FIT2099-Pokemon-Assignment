package game.agent.eval;
import game.agent.growth.GrowthWorld;import game.agent.llm.Json;import java.util.*;import java.nio.charset.StandardCharsets;import java.security.*;
/** Isolated resettable fixture. Equal seed + equal actions reproduce game state, not model text. */
public final class SeededGrowthFixture {
 private SeededGrowthFixture(){}
 public static GrowthWorld reset(long seed,String starter){Random random=new Random(seed);long[] index={0};GrowthWorld world=new GrowthWorld(random::nextInt,()->UUID.nameUUIDFromBytes(("growth-eval-v1:"+seed+":"+index[0]++).getBytes(StandardCharsets.UTF_8)).toString());if(!"ADVENTURE_STARTED".equals(world.startAdventure().getCode())||!"STARTER_CHOSEN".equals(world.starter(starter).getCode()))throw new IllegalArgumentException("INVALID_STARTER");return world;}
 public static String fingerprint(Map<String,Object> world){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(Json.write(world).getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
