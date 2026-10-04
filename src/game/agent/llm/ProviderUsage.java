package game.agent.llm;
import java.util.*;
/** Numeric response counters only; never retains raw response data. */
final class ProviderUsage {
 static Map<String,Number> read(Object raw,String input,String output,String total) {
  Map<String,Number> counters=new LinkedHashMap<>();
  if(raw instanceof Map) {Map<?,?> data=(Map<?,?>)raw;
   add(counters,"inputTokens",data.get(input));add(counters,"outputTokens",data.get(output));add(counters,"totalTokens",data.get(total));
   if(data.get("input_tokens_details") instanceof Map){Map<?,?> details=(Map<?,?>)data.get("input_tokens_details");add(counters,"cachedInputTokens",details.get("cached_tokens"));add(counters,"cacheWriteTokens",details.get("cache_write_tokens"));}
   if(data.get("output_tokens_details") instanceof Map)add(counters,"reasoningOutputTokens",((Map<?,?>)data.get("output_tokens_details")).get("reasoning_tokens"));
  }
  return Collections.unmodifiableMap(counters);
 }
 private static void add(Map<String,Number> out,String name,Object value) {
  if(!(value instanceof Number))return;Number n=(Number)value;double d=n.doubleValue();long l=n.longValue();
  if(Double.isFinite(d)&&d>=0&&d<9223372036854775808.0&&d==l)out.put(name,l);
 }
}
