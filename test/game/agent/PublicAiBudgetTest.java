package game.agent;
import game.agent.llm.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class PublicAiBudgetTest {
 @TempDir Path dir;
 final AtomicLong now=new AtomicLong(1791072000000L);
 String request(){return "{\"max_tokens\":32,\"messages\":[]}";}
 JsonTransport ok=(m,k,j,t)->new JsonTransport.Response(200,"{\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2}}");
 PublicAiBudget budget(long day,long month,int minute,int concurrent){return new PublicAiBudget(dir.resolve("budget.json"),day,month,minute,concurrent,now::get);}
 @Test void rateLimitRejectsBeforeHttpAndPersistsAcrossRestart() throws Exception {
  AtomicInteger calls=new AtomicInteger();JsonTransport wire=(m,k,j,t)->{calls.incrementAndGet();return ok.send(m,k,j,t);};
  budget(50000,100000,1,1).wrap(wire).send("deepseek-flash","secret",request(),1000);
  assertEquals(ProviderException.Code.RATE_LIMIT,assertThrows(ProviderException.class,()->budget(50000,100000,1,1).wrap(wire).send("deepseek-flash","secret",request(),1000)).getCode());assertEquals(1,calls.get());
  now.addAndGet(60000);budget(50000,100000,1,1).wrap(wire).send("deepseek-flash","secret",request(),1000);assertEquals(2,calls.get());
  String saved=new String(Files.readAllBytes(dir.resolve("budget.json")),"UTF-8");assertFalse(saved.contains("secret"));assertFalse(saved.contains("messages"));
 }
 @Test void dailyBudgetReservedOnFailureAndCannotResetByRestart() throws Exception {
  PublicAiBudget b=budget(1800,10000,12,2);assertThrows(java.io.IOException.class,()->b.wrap((m,k,j,t)->{throw new java.io.IOException();}).send("deepseek-flash","key",request(),1000));
  assertEquals(ProviderException.Code.BUDGET_EXHAUSTED,assertThrows(ProviderException.class,()->budget(1800,10000,12,2).wrap(ok).send("deepseek-flash","key",request(),1000)).getCode());
  assertEquals(1L,((Number)b.snapshot().get("failedCalls")).longValue());
 }
 @Test void monthlyBudgetSurvivesDayRollover() throws Exception {
  PublicAiBudget b=budget(1800,1800,12,2);b.wrap((m,k,j,t)->new JsonTransport.Response(200,"{}")).send("deepseek-flash","key",request(),1000);now.addAndGet(86400000);
  assertEquals(ProviderException.Code.BUDGET_EXHAUSTED,assertThrows(ProviderException.class,()->b.wrap(ok).send("deepseek-flash","key",request(),1000)).getCode());
 }
 @Test void newDayRestoresDailyAllowanceAndAggregatesActualUsage() throws Exception {
  PublicAiBudget b=budget(1800,10000,12,2);b.wrap(ok).send("deepseek-flash","key",request(),1000);now.addAndGet(86400000);b.wrap(ok).send("deepseek-flash","key",request(),1000);
  assertEquals(20L,((Number)b.snapshot().get("actualInputTokens")).longValue());assertEquals(2L,((Number)b.snapshot().get("calls")).longValue());
 }
 @Test void concurrentCallsHaveOneSharedGateAndReleaseOnCompletion() throws Exception {
  PublicAiBudget b=budget(50000,100000,12,1);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);ExecutorService pool=Executors.newSingleThreadExecutor();
  try{Future<?> pending=pool.submit(()->{try{b.wrap((m,k,j,t)->{entered.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return ok.send(m,k,j,t);}).send("deepseek-flash","key",request(),1000);}catch(Exception e){throw new RuntimeException(e);}});
   assertTrue(entered.await(3,TimeUnit.SECONDS));assertEquals(ProviderException.Code.RATE_LIMIT,assertThrows(ProviderException.class,()->b.wrap(ok).send("deepseek-flash","key",request(),1000)).getCode());release.countDown();pending.get(3,TimeUnit.SECONDS);b.wrap(ok).send("deepseek-flash","key",request(),1000);
  }finally{release.countDown();pool.shutdownNow();}
 }
 @Test void corruptLedgerFailsClosed() throws Exception {Files.write(dir.resolve("budget.json"),"{}".getBytes("UTF-8"));assertThrows(ProviderException.class,()->budget(50000,100000,12,2));}
 @Test void publicDeploymentRequiresBudgetAndEvaluationCannotSpend() {
  Map<String,String> env=new HashMap<>();env.put("PUBLIC_ORIGIN","https://example.com");env.put("LLM_PROVIDER","deepseek");env.put("DEEPSEEK_API_KEY","test-key");
  assertFalse(game.agent.web.ProviderSelection.fromEnvironment(env).isAvailable());assertEquals(Collections.singletonList("baseline"),game.agent.eval.EvaluationProviders.environment(env).models());
 }
 @Test void rejectsExpensiveModelOrUnboundedRequest() {
  PublicAiBudget b=budget(50000,100000,12,2);assertThrows(ProviderException.class,()->b.wrap(ok).send("deepseek-v4-pro","key",request(),1000));assertThrows(ProviderException.class,()->b.wrap(ok).send("deepseek-flash","key","{\"max_tokens\":100000}",1000));assertEquals(0L,((Number)b.snapshot().get("calls")).longValue());
 }
 @Test void knownUsageSettlesWithoutGivingBackUnknownCosts() throws Exception {
  PublicAiBudget b=budget(1800,10000,12,2);b.wrap(ok).send("deepseek-flash","key",request(),1000);assertEquals(12L,((Number)b.snapshot().get("dayReserved")).longValue());
  b.wrap((m,k,j,t)->new JsonTransport.Response(200,"{}")).send("deepseek-flash","key",request(),1000);assertTrue(((Number)b.snapshot().get("dayReserved")).longValue()>1000);
 }
 @Test void persistenceFailureNeverDispatchesHttp() throws Exception {
  PublicAiBudget b=budget(50000,100000,12,2);Files.createDirectory(dir.resolve("budget.json"));AtomicInteger sent=new AtomicInteger();JsonTransport t=b.wrap((m,k,j,d)->{sent.incrementAndGet();return ok.send(m,k,j,d);});
  assertThrows(ProviderException.class,()->t.send("deepseek-flash","key",request(),1000));assertThrows(ProviderException.class,()->t.send("deepseek-flash","key",request(),1000));assertEquals(0,sent.get());
 }
 @Test void finishingAnOlderDayNeverRefundsTheNewDay() throws Exception {
  PublicAiBudget b=budget(10000,50000,12,2);b.wrap((m,k,j,t)->{now.addAndGet(86400000);b.wrap(ok).send(m,k,j,t);return ok.send(m,k,j,t);}).send("deepseek-flash","key",request(),1000);
  assertEquals(12L,((Number)b.snapshot().get("dayReserved")).longValue());assertEquals(24L,((Number)b.snapshot().get("monthReserved")).longValue());
 }
}
