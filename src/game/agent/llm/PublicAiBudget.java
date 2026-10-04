package game.agent.llm;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Shared pre-dispatch reservations. No prompts, responses or credentials are stored.
 * Validated usage settles successful reservations; unknown calls retain their reservation. Single JVM deployment. */
public final class PublicAiBudget {
    private static final Map<String,PublicAiBudget> SHARED=new HashMap<>();
    private final Path file; private final LongSupplier clock;
    private final long daily,monthly; private final int perMinute,concurrency;
    private Map<String,Object> state; private int active; private boolean storageFailed;
    public PublicAiBudget(Path file,long daily,long monthly,int perMinute,int concurrency,LongSupplier clock) {
        if(daily<1||monthly<daily||perMinute<1||perMinute>120||concurrency<1||concurrency>8)throw new IllegalArgumentException("INVALID_AI_LIMITS");
        this.file=file;this.daily=daily;this.monthly=monthly;this.perMinute=perMinute;this.concurrency=concurrency;this.clock=clock;
        try {state=Files.exists(file)?Json.asObject(Json.read(new String(Files.readAllBytes(file),StandardCharsets.UTF_8))):fresh(); validate();}
        catch(Exception e){throw new ProviderException(ProviderException.Code.CONFIGURATION);}
    }
    public static synchronized JsonTransport protect(JsonTransport transport,Map<String,String> env) {
        String path=env.get("AI_BUDGET_FILE");
        if(path==null){if(env.containsKey("PUBLIC_ORIGIN"))throw new ProviderException(ProviderException.Code.CONFIGURATION);return transport;}
        Path file=Paths.get(path).toAbsolutePath().normalize();
        PublicAiBudget budget=SHARED.get(file.toString());
        if(budget==null){budget=new PublicAiBudget(file,Long.parseLong(env.getOrDefault("AI_DAILY_TOKENS","200000")),Long.parseLong(env.getOrDefault("AI_MONTHLY_TOKENS","2000000")),Integer.parseInt(env.getOrDefault("AI_REQUESTS_PER_MINUTE","12")),Integer.parseInt(env.getOrDefault("AI_CONCURRENCY","2")),System::currentTimeMillis);SHARED.put(file.toString(),budget);}
        return budget.wrap(transport);
    }
    private Map<String,Object> fresh(){return Json.object("version",1,"day","","month","","dayReserved",0L,"monthReserved",0L,"minute",-1L,"minuteCalls",0L,"calls",0L,"successfulCalls",0L,"failedCalls",0L,"actualInputTokens",0L,"actualOutputTokens",0L,"cachedInputTokens",0L);}
    private long n(String key){Object value=state.get(key);if(!(value instanceof Number)||((Number)value).longValue()<0)throw new IllegalArgumentException();return ((Number)value).longValue();}
    private void validate(){if(!Integer.valueOf(1).equals(((Number)state.get("version")).intValue())||!(state.get("day") instanceof String)||!(state.get("month") instanceof String))throw new IllegalArgumentException();for(String key:Arrays.asList("dayReserved","monthReserved","minuteCalls","calls","successfulCalls","failedCalls","actualInputTokens","actualOutputTokens","cachedInputTokens"))n(key);if(!(state.get("minute") instanceof Number))throw new IllegalArgumentException();}
    private void add(String key,long amount){state.put(key,Math.addExact(n(key),amount));}
    private void save() {
        try {Files.createDirectories(file.getParent());Path temp=Files.createTempFile(file.getParent(),"ai-budget-",".tmp");
            try {byte[] data=Json.write(state).getBytes(StandardCharsets.UTF_8);try(java.nio.channels.FileChannel c=java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)){java.nio.ByteBuffer bytes=java.nio.ByteBuffer.wrap(data);while(bytes.hasRemaining())c.write(bytes);c.force(true);}Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            finally{Files.deleteIfExists(temp);}
        }catch(IOException e){storageFailed=true;throw new ProviderException(ProviderException.Code.UNAVAILABLE);}
    }
    private synchronized Map<String,Object> reserve(String model,String json) {
        if(storageFailed)throw new ProviderException(ProviderException.Code.UNAVAILABLE);
        if(!"deepseek-flash".equals(model))throw new ProviderException(ProviderException.Code.REQUEST_REJECTED);
        int bytes=json.getBytes(StandardCharsets.UTF_8).length;
        Map<String,Object> payload=Json.asObject(Json.read(json));Object output=payload.get("max_tokens");
        if(bytes>32768||!(output instanceof Number)||((Number)output).longValue()<1||((Number)output).longValue()>2048)throw new ProviderException(ProviderException.Code.REQUEST_REJECTED);
        // UTF-8 bytes * 2 plus completion limit and framing allowance; pessimistic token reservation.
        long tokens=bytes*2L+((Number)output).longValue()+1024;
        long now=clock.getAsLong(),minute=now/60000;
        LocalDate date=Instant.ofEpochMilli(now).atZone(ZoneId.of("Australia/Sydney")).toLocalDate();
        String day=date.toString(),month=day.substring(0,7);
        if(!month.equals(state.get("month"))){state.put("month",month);state.put("monthReserved",0L);}
        if(!day.equals(state.get("day"))){state.put("day",day);state.put("dayReserved",0L);}
        if(minute!=((Number)state.get("minute")).longValue()){state.put("minute",minute);state.put("minuteCalls",0L);}
        if(active>=concurrency||n("minuteCalls")>=perMinute)throw new ProviderException(ProviderException.Code.RATE_LIMIT);
        if(tokens>daily-n("dayReserved")||tokens>monthly-n("monthReserved"))throw new ProviderException(ProviderException.Code.BUDGET_EXHAUSTED);
        add("dayReserved",tokens);add("monthReserved",tokens);add("minuteCalls",1);add("calls",1);
        save();active++;return Json.object("day",day,"month",month,"tokens",tokens);
    }
    private synchronized void finish(JsonTransport.Response response,Map<String,Object> lease) {
        active--;
        if(response!=null&&response.status==200){add("successfulCalls",1);
            try{Map<String,Object> usage=Json.asObject(Json.asObject(Json.read(response.body)).get("usage"));
                long input=exact(usage.get("prompt_tokens")),output=exact(usage.get("completion_tokens")),reserved=((Number)lease.get("tokens")).longValue();
                if(output>2048||input+output>reserved)throw new IllegalArgumentException();
                add("actualInputTokens",input);add("actualOutputTokens",output);add("cachedInputTokens",Math.min(input,nonnegative(usage.get("prompt_cache_hit_tokens"))));
                long release=reserved-input-output;
                if(lease.get("day").equals(state.get("day")))state.put("dayReserved",n("dayReserved")-release);
                if(lease.get("month").equals(state.get("month")))state.put("monthReserved",n("monthReserved")-release);
            }catch(RuntimeException ignored){}
        }else add("failedCalls",1);
        save();
    }
    private static long exact(Object value){if(!(value instanceof Number)||((Number)value).longValue()<0||((Number)value).doubleValue()!=((Number)value).longValue())throw new IllegalArgumentException();return ((Number)value).longValue();}
    private static long nonnegative(Object value){return value instanceof Number?Math.max(0,((Number)value).longValue()):0;}
    public JsonTransport wrap(JsonTransport transport){return (model,key,json,timeout)->{Map<String,Object> lease=reserve(model,json);JsonTransport.Response response=null;try{response=transport.send(model,key,json,timeout);return response;}finally{finish(response,lease);}};}
    public synchronized Map<String,Object> snapshot(){return new LinkedHashMap<>(state);}
}
