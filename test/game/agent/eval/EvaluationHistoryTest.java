package game.agent.eval;
import game.agent.llm.Json;
import game.agent.persistence.*;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class EvaluationHistoryTest {
 @TempDir Path dir;
 Map<String,Object> params(){return Json.object("models",Arrays.asList("baseline"),"scenarios",Arrays.asList("information"),"seeds",Arrays.asList(11L),"repetitions",1,"starter","TREECKO","decisions",30,"wallMillis",180000,"prices",Collections.emptyMap());}
 Map<String,Object> cmd(EvaluationService s,String name,String id){return EvaluationPlatformTest.command(s,name,"CREATE".equals(name)?params():Json.object("batchId",id));}
 String create(EvaluationService s){s.command(cmd(s,"CREATE",null));List<Object> rows=Json.asArray(s.snapshot().get("batches"));return (String)Json.asObject(rows.get(rows.size()-1)).get("id");}
 void finish(EvaluationService s){for(int n=1;n<200;n++)s.tick(n);}
 @Test void archiveReleasesNinthSlotAndRetainsReplayAcrossRestart(){try(FileWorldStore store=new FileWorldStore(dir)){
  EvaluationService s=new EvaluationService("alice",store,EvaluationPlatformTest.providers(),Runnable::run);String first=create(s);for(int i=1;i<8;i++)create(s);finish(s);
  assertEquals("BATCH_LIMIT",assertThrows(IllegalStateException.class,()->create(s)).getMessage());
  Map<String,Object> archive=cmd(s,"ARCHIVE",first);s.command(archive);Object revision=s.snapshot().get("revision");s.command(archive);assertEquals(revision,s.snapshot().get("revision"));
  assertEquals(true,s.report(first).get("archived"));assertNotNull(create(s));s.close();
  EvaluationService restored=new EvaluationService("alice",store,EvaluationPlatformTest.providers(),Runnable::run);assertEquals(9,Json.asArray(restored.snapshot().get("batches")).size());assertEquals(true,restored.report(first).get("archived"));
  Map<String,Object> row=Json.asObject(Json.asArray(restored.report(first).get("cases")).get(0));assertFalse(Json.asArray(restored.replay(first,(String)row.get("id")).get("trace")).isEmpty());
 }}
 @Test void deleteRequiresTerminalOwnerAndPurgesCheckpointWithoutClearingQuota(){try(FileWorldStore store=new FileWorldStore(dir)){
  EvaluationProviders paid=new EvaluationProviders(Arrays.asList("baseline","deepseek-flash"),1000,m->EvaluationRun.baseline(),true);
  EvaluationService s=new EvaluationService("alice",store,paid,Runnable::run);Map<String,Object> p=params();p.put("models",Arrays.asList("deepseek-flash"));p.put("paidConfirmed",true);s.command(EvaluationPlatformTest.command(s,"CREATE",p));String id=(String)Json.asObject(Json.asArray(s.snapshot().get("batches")).get(0)).get("id");
  assertEquals("BATCH_NOT_TERMINAL",assertThrows(IllegalStateException.class,()->s.command(cmd(s,"DELETE",id))).getMessage());
  EvaluationService other=new EvaluationService("bob",store,paid,Runnable::run);assertThrows(NoSuchElementException.class,()->other.command(cmd(other,"DELETE",id)));
  finish(s);Map<String,Object> row=Json.asObject(Json.asArray(s.report(id).get("cases")).get(0));Map<String,Object> deletion=cmd(s,"DELETE",id);s.command(deletion);s.command(deletion);assertTrue(Json.asArray(s.snapshot().get("batches")).isEmpty());assertEquals(5L,((Number)s.snapshot().get("publicPaidRemaining")).longValue());
  assertNull(store.load("evaluation:alice:"+row.get("id")));s.close();EvaluationService restored=new EvaluationService("alice",store,paid,Runnable::run);assertTrue(Json.asArray(restored.snapshot().get("batches")).isEmpty());assertEquals(5L,((Number)restored.snapshot().get("publicPaidRemaining")).longValue());
 }}
 @Test void deletingUnknownUsageRetainsSettlementButNeverRevivesHistory(){try(FileWorldStore store=new FileWorldStore(dir)){
  EvaluationService s=new EvaluationService("alice",store,EvaluationPlatformTest.providers(),Runnable::run);String id=create(s);finish(s);
  Map<String,Object> row=Json.asObject(Json.asArray(s.report(id).get("cases")).get(0));String key="evaluation:alice:"+row.get("id")+":usage";
  Map<String,Object> journal=store.load(key);List<Object> calls=Json.asArray(journal.get("calls"));journal.put("issued",calls.size()+1);store.save(key,journal);
  s.command(cmd(s,"DELETE",id));assertNotNull(store.load(key));assertNull(store.load("evaluation:alice:"+row.get("id")));assertTrue(Json.asArray(s.snapshot().get("batches")).isEmpty());
  calls.add(Json.object("callIndex",calls.size()+1,"usage",Json.object("inputTokens",1,"outputTokens",1)));store.save(key,journal);s.tick(1);assertNull(store.load(key));assertTrue(Json.asArray(s.snapshot().get("batches")).isEmpty());
 }}
 @Test void archivedRetentionIsBoundedAndLargeIndexStoresOnlyReferences(){String previous=System.getProperty("pokemon.eval.archiveMaximum");System.setProperty("pokemon.eval.archiveMaximum","1");try(FileWorldStore store=new FileWorldStore(dir)){
  EvaluationService s=new EvaluationService("alice",store,EvaluationPlatformTest.providers(),Runnable::run);String first=create(s);finish(s);s.command(cmd(s,"ARCHIVE",first));String second=create(s);finish(s);s.command(cmd(s,"ARCHIVE",second));assertEquals(1,Json.asArray(s.snapshot().get("batches")).size());assertThrows(NoSuchElementException.class,()->s.report(first));assertEquals(true,s.report(second).get("archived"));
  Map<String,Object> index=store.load("evaluation:alice");Map<String,Object> stored=Json.asObject(Json.asArray(Json.read((String)index.get("batchesJson"))).get(0));Map<String,Object> ref=Json.asObject(Json.asArray(stored.get("cases")).get(0));assertFalse(ref.containsKey("metrics"));assertFalse(stored.containsKey("comparison"));
 }finally{if(previous==null)System.clearProperty("pokemon.eval.archiveMaximum");else System.setProperty("pokemon.eval.archiveMaximum",previous);}}

 @Test void providerStopReasonSurvivesCheckpointWithoutRawProviderMessage(){
  Map<String,Object> spec=Json.object("id",UUID.randomUUID().toString(),"scenario","information","model","deepseek-flash","seed",11,"starter","TREECKO","repetition",1,"decisions",30,"wallMillis",180000,"price",Collections.emptyMap());
  EvaluationRun run=new EvaluationRun(spec);run.resume(c->{throw new game.agent.llm.ProviderException(game.agent.llm.ProviderException.Code.BUDGET_EXHAUSTED);},Runnable::run,1000);for(int n=0;n<10;n++)run.tick(n+1);
  assertEquals("PROVIDER_BUDGET_EXHAUSTED",run.report(false).get("errorCode"));assertEquals("PROVIDER_BUDGET_EXHAUSTED",EvaluationRun.restore(run.checkpoint()).report(false).get("errorCode"));
 }

 @Test void returnedPaidCallWithUnknownUsageKeepsFeeJournalAfterDeletion(){try(FileWorldStore store=new FileWorldStore(dir)){EvaluationService service=new EvaluationService("alice",store,EvaluationPlatformTest.providers(),Runnable::run);String batch=create(service);finish(service);String run=(String)Json.asObject(Json.asArray(service.report(batch).get("cases")).get(0)).get("id");String key="evaluation:alice:"+run;store.save(key+":usage",Json.object("billable",true,"issued",1,"calls",Arrays.asList(Json.object("callIndex",1,"success",false,"latencyMs",1,"usage",Collections.emptyMap()))));service.command(cmd(service,"DELETE",batch));assertNull(store.load(key));assertNotNull(store.load(key+":usage"));new EvaluationService("alice",store,EvaluationPlatformTest.providers(),Runnable::run).tick(2000);assertNotNull(store.load(key+":usage"));}}

 @Test void legacyUnknownPaidJournalWithoutMarkerIsNeverPurged(){try(FileWorldStore store=new FileWorldStore(dir)){EvaluationService service=new EvaluationService("alice",store,EvaluationPlatformTest.providers(),Runnable::run);String batch=create(service);finish(service);String run=(String)Json.asObject(Json.asArray(service.report(batch).get("cases")).get(0)).get("id"),key="evaluation:alice:"+run;store.save(key+":usage",Json.object("issued",1,"calls",Arrays.asList(Json.object("callIndex",1,"success",false,"latencyMs",1,"usage",Collections.emptyMap()))));service.command(cmd(service,"DELETE",batch));assertNotNull(store.load(key+":usage"));}}
}
