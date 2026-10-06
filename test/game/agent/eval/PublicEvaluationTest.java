package game.agent.eval;
import game.agent.llm.*;import java.util.*;import java.nio.file.*;import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;
class PublicEvaluationTest {
 @TempDir Path dir;
 @Test void publicModelsRequireExplicitOptInKeyAndDurableBudget(){
  Map<String,String> env=new HashMap<>();env.put("PUBLIC_ORIGIN","https://example.test");env.put("LLM_PROVIDER","deepseek");env.put("DEEPSEEK_API_KEY","test-key");
  assertEquals(Arrays.asList("baseline"),EvaluationProviders.environment(env).models());
  env.put("EVAL_PUBLIC_DEEPSEEK_ENABLED","true");assertEquals(Arrays.asList("baseline"),EvaluationProviders.environment(env).models());
  env.put("AI_BUDGET_FILE",dir.resolve("budget.json").toString());assertEquals(Arrays.asList("baseline","deepseek-flash"),EvaluationProviders.environment(env).models());
  env.remove("DEEPSEEK_API_KEY");assertEquals(Arrays.asList("baseline"),EvaluationProviders.environment(env).models());
 }
 @Test void paidLimitsConfirmationQuotaAndRestartCannotBeBypassed(){
  EvaluationProviders providers=new EvaluationProviders(Arrays.asList("baseline","deepseek-flash"),1000,m->EvaluationRun.baseline(),true);
  EvaluationPlatformTest.MemoryStore store=new EvaluationPlatformTest.MemoryStore();EvaluationService s=new EvaluationService("quota",store,providers,Runnable::run);
  Map<String,Object> p=EvaluationPlatformTest.params();p.put("models",Arrays.asList("deepseek-flash"));p.put("scenarios",Arrays.asList("information"));p.put("seeds",Arrays.asList(11));p.put("repetitions",1);p.put("decisions",30);p.put("wallMillis",180000);
  final EvaluationService initial=s;assertEquals("PAID_CONFIRMATION_REQUIRED",assertThrows(IllegalStateException.class,()->initial.command(EvaluationPlatformTest.command(initial,"CREATE",p))).getMessage());
  p.put("paidConfirmed",true);p.put("decisions",31);assertEquals("PUBLIC_EVAL_LIMIT",assertThrows(IllegalStateException.class,()->initial.command(EvaluationPlatformTest.command(initial,"CREATE",p))).getMessage());p.put("decisions",30);
  p.put("seeds",Arrays.asList(11,23,37,41));assertEquals("PUBLIC_EVAL_LIMIT",assertThrows(IllegalStateException.class,()->initial.command(EvaluationPlatformTest.command(initial,"CREATE",p))).getMessage());p.put("seeds",Arrays.asList(11,23,37));
  Map<String,Object> request=EvaluationPlatformTest.command(s,"CREATE",p);s.command(request);s.command(request);assertEquals(1,game.agent.llm.Json.asArray(s.snapshot().get("batches")).size());
  s.command(EvaluationPlatformTest.command(s,"CREATE",p));s.close();s=new EvaluationService("quota",store,providers,Runnable::run);final EvaluationService restarted=s;
  assertEquals("PUBLIC_EVAL_DAILY_LIMIT",assertThrows(IllegalStateException.class,()->restarted.command(EvaluationPlatformTest.command(restarted,"CREATE",p))).getMessage());
  p.put("models",Arrays.asList("baseline"));p.remove("paidConfirmed");s.command(EvaluationPlatformTest.command(s,"CREATE",p));assertEquals(3,game.agent.llm.Json.asArray(s.snapshot().get("batches")).size());
 }
}
