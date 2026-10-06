package game.agent.web;
import game.agent.llm.Json;import game.agent.persistence.*;import game.agent.tools.ToolRequest;import java.util.*;import java.util.concurrent.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class GrowthRoomTest {
 static class Store implements WorldStore{Map<String,Map<String,Object>> values=new HashMap<>();boolean fail,commitThenFail;public void save(String owner,Map<String,Object> checkpoint){if(!fail||commitThenFail)values.put(owner,Json.asObject(Json.read(Json.write(checkpoint))));if(fail)throw new IllegalStateException();}public Map<String,Object> load(String owner){return values.get(owner);}public void close(){}}
 private static ProviderSelection offline(){return new ProviderSelection("openai","unavailable",1000,null,null);}
 private static Map<String,Object> input(GrowthRoom r,String kind,Map<String,Object> params){Map<String,Object> s=r.snapshot();return Json.object("requestId",UUID.randomUUID().toString(),"taskId",s.get("taskId"),"expectedRevision",s.get("revision"),"command",kind,"params",params);}
 private static AgentRoom.Reply command(GrowthRoom r,String kind,Map<String,Object> params){return r.command(input(r,kind,params),10);}
 @Test void persistentReceiptPreventsDuplicateRewardAcrossRestart(){Store store=new Store();GrowthRoom r=new GrowthRoom("owner",offline(),Runnable::run,store,()->0);Map<String,Object> choose=input(r,"STARTER",Json.object("species","MUDKIP"));assertEquals(200,r.command(choose,10).status);GrowthRoom restored=new GrowthRoom("owner",offline(),Runnable::run,store,()->0);assertEquals(200,restored.command(choose,10).status);assertEquals(1,Json.asArray(Json.asObject(restored.snapshot().get("world")).get("team")).size());Map<String,Object> conflict=new LinkedHashMap<>(choose);conflict.put("params",Json.object("species","TORCHIC"));assertEquals("REQUEST_ID_CONFLICT",restored.command(conflict,10).body.get("reasonCode"));GrowthRoom other=new GrowthRoom("other",offline(),Runnable::run,store);assertNull(Json.asObject(other.snapshot().get("world")).get("partner"));}
 @Test void failedSaveFreezesAndRecoveryLoadsOnlyCommittedWorld(){Store store=new Store();GrowthRoom r=new GrowthRoom("owner",offline(),Runnable::run,store,()->0);store.fail=true;assertEquals(503,command(r,"STARTER",Json.object("species","MUDKIP")).status);assertEquals("STORAGE_ERROR",r.snapshot().get("status"));assertEquals(503,command(r,"REST",Collections.emptyMap()).status);assertEquals(200,command(r,"CANCEL",Collections.emptyMap()).status);store.fail=false;assertEquals(200,command(r,"RECOVER",Collections.emptyMap()).status);assertNull(Json.asObject(r.snapshot().get("world")).get("partner"));}
 @Test void uncertainCommitRecoveryRetainsReceiptAndChosenIndividual(){Store store=new Store();GrowthRoom r=new GrowthRoom("owner",offline(),Runnable::run,store,()->0);store.fail=store.commitThenFail=true;Map<String,Object> choose=input(r,"STARTER",Json.object("species","MUDKIP"));assertEquals(503,r.command(choose,10).status);store.fail=false;assertEquals(200,command(r,"RECOVER",Collections.emptyMap()).status);assertEquals(200,r.command(choose,10).status);assertNotNull(Json.asObject(r.snapshot().get("world")).get("partner"));}
 @Test void authorityAndStrictParametersRejectStaleOrUnselectedCommands(){GrowthRoom r=new GrowthRoom("owner",offline(),Runnable::run,null);Map<String,Object> choose=input(r,"STARTER",Json.object("species","MUDKIP"));assertEquals("MODE_NOT_SELECTED",r.command(choose,10,"MODE_NOT_SELECTED").body.get("reasonCode"));assertEquals(200,r.command(choose,10).status);assertEquals(400,command(r,"REST",Json.object("unexpected",true)).status);Map<String,Object> stale=input(r,"REST",Collections.emptyMap());stale.put("expectedRevision",1);assertEquals("STALE_REVISION",r.command(stale,10).body.get("reasonCode"));assertEquals("PROVIDER_CONFIGURATION",command(r,"AI_TRAIN",Json.object("targetLevel",6)).body.get("reasonCode"));}
 @Test void aiPendingPauseAndRestartCannotCommitLateDecisions(){List<Runnable> jobs=new ArrayList<>();ProviderSelection ai=new ProviderSelection("openai","test",1000,c->new ToolRequest("provider","go_to_region",Json.object("regionId","forest")),null);Store store=new Store();GrowthRoom r=new GrowthRoom("owner",ai,jobs::add,store,()->0);command(r,"STARTER",Json.object("species","MUDKIP"));assertEquals(200,command(r,"AI_TRAIN",Json.object("targetLevel",6)).status);r.tick(11);assertEquals(1,jobs.size());assertEquals(200,command(r,"PAUSE",Collections.emptyMap()).status);jobs.get(0).run();r.tick(15);assertEquals("lab",Json.asObject(r.snapshot().get("world")).get("region"));GrowthRoom restored=new GrowthRoom("owner",ai,jobs::add,store,()->0);assertEquals("PAUSED",restored.snapshot().get("status"));assertEquals(200,command(restored,"RESUME",Collections.emptyMap()).status);restored.tick(30);assertEquals("RUNNING",restored.snapshot().get("status"));}
 @Test void boundedReceiptsDoNotPermanentlyLockProgressAndExpiredReplayCannotMutate(){GrowthRoom r=new GrowthRoom("owner",offline(),Runnable::run,null,()->0);Map<String,Object> first=input(r,"STARTER",Json.object("species","MUDKIP"));assertEquals(200,r.command(first,10).status);Map<String,Object> last=null;for(int i=0;i<1030;i++){last=input(r,"REST",Collections.emptyMap());assertEquals(200,r.command(last,10).status,"REST "+i);}assertEquals(200,r.command(last,10).status);assertEquals("STALE_REVISION",r.command(first,10).body.get("reasonCode"));assertEquals(1,Json.asArray(Json.asObject(r.snapshot().get("world")).get("team")).size());}
 @Test void pendingDispatchAndElapsedBudgetArePersistedBeforeProviderReturns(){List<Runnable> jobs=new ArrayList<>();Store store=new Store();ProviderSelection ai=new ProviderSelection("openai","test",15000,c->new ToolRequest("provider","rest",Collections.emptyMap()),null);GrowthRoom r=new GrowthRoom("owner",ai,jobs::add,store,()->0);command(r,"STARTER",Json.object("species","MUDKIP"));command(r,"AI_TRAIN",Json.object("targetLevel",6));r.tick(11);Map<String,Object> saved=store.load("growth:owner");assertEquals(1,((Number)Json.asObject(saved.get("metrics")).get("decisionCount")).intValue());r.tick(1511);assertTrue(((Number)store.load("growth:owner").get("activeMillis")).longValue()>=1500);GrowthRoom restored=new GrowthRoom("owner",ai,jobs::add,store,()->0);assertEquals("PAUSED",restored.snapshot().get("status"));assertEquals(1,((Number)Json.asObject(restored.snapshot().get("metrics")).get("decisionCount")).intValue());}
 @Test void delayedStopFromOldTrainingCannotStopNewTraining(){ProviderSelection ai=new ProviderSelection("openai","test",1000,c->new ToolRequest("provider","rest",Collections.emptyMap()),null);GrowthRoom r=new GrowthRoom("owner",ai,Runnable::run,null,()->0);command(r,"STARTER",Json.object("species","MUDKIP"));command(r,"AI_TRAIN",Json.object("targetLevel",6));Map<String,Object> delayed=input(r,"CANCEL",Collections.emptyMap());command(r,"CANCEL",Collections.emptyMap());command(r,"AI_TRAIN",Json.object("targetLevel",7));assertEquals("STALE_TASK",r.command(delayed,10).body.get("reasonCode"));assertEquals("RUNNING",r.snapshot().get("status"));}
 @Test void realPostgresRestoresGrowthRoom()throws Exception{String url=System.getProperty("pokemon.test.jdbcUrl");org.junit.jupiter.api.Assumptions.assumeTrue(url!=null);Class.forName("org.postgresql.Driver");JdbcWorldStore.ConnectionFactory connection=()->java.sql.DriverManager.getConnection(url,System.getProperty("pokemon.test.jdbcUser","pokemon_v1"),"");String owner=UUID.randomUUID().toString();try(WorldStore store=new JdbcWorldStore(connection)){GrowthRoom r=new GrowthRoom(owner,offline(),Runnable::run,store,()->0);assertEquals(200,command(r,"STARTER",Json.object("species","TREECKO")).status);}try(WorldStore store=new JdbcWorldStore(connection)){GrowthRoom r=new GrowthRoom(owner,offline(),Runnable::run,store,()->0);assertEquals("TREECKO",Json.asObject(Json.asObject(r.snapshot().get("world")).get("partner")).get("species"));assertEquals(true,r.snapshot().get("recovered"));}}

 @Test void higherTrainingGoalsPersistAcrossPauseAndReloadButRejectAboveCap(){
  Store store=new Store();ProviderSelection ai=new ProviderSelection("openai","test",1000,c->new ToolRequest("provider","rest",Collections.emptyMap()),null);GrowthRoom r=new GrowthRoom("high",ai,Runnable::run,store,()->0);command(r,"STARTER",Json.object("species","TORCHIC"));
  assertEquals("INVALID_TARGET_LEVEL",command(r,"AI_TRAIN",Json.object("targetLevel",41)).body.get("reasonCode"));assertEquals(200,command(r,"AI_TRAIN",Json.object("targetLevel",36)).status);command(r,"PAUSE",Collections.emptyMap());GrowthRoom restored=new GrowthRoom("high",ai,Runnable::run,store,()->0);assertEquals(36,restored.snapshot().get("targetLevel"));assertEquals("PAUSED",restored.snapshot().get("status"));assertEquals(200,command(restored,"RESUME",Collections.emptyMap()).status);
 }

 @Test void realFileCheckpointStaysWithinBudgetDuringLongPlayAndLegacyReceiptLoad(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory){
  try(WorldStore store=new FileWorldStore(directory)){
   GrowthRoom r=new GrowthRoom("long-file",offline(),Runnable::run,store,()->0);Map<String,Object> first=input(r,"STARTER",Json.object("species","MUDKIP"));assertEquals(200,r.command(first,10).status);
   for(int n=0;n<620;n++)assertEquals(200,command(r,"REST",Collections.emptyMap()).status,"actual file save "+n);
   assertEquals(64,Json.asArray(store.load("growth:long-file").get("receipts")).size());GrowthRoom restored=new GrowthRoom("long-file",offline(),Runnable::run,store,()->0);assertEquals("STALE_REVISION",restored.command(first,10).body.get("reasonCode"));assertEquals(200,command(restored,"REST",Collections.emptyMap()).status);
  }
  Store legacy=new Store();GrowthRoom source=new GrowthRoom("legacy",offline(),Runnable::run,legacy,()->0);command(source,"STARTER",Json.object("species","MUDKIP"));Map<String,Object> saved=legacy.load("growth:legacy");List<Object> receipts=Json.asArray(saved.get("receipts"));Map<String,Object> example=Json.asObject(receipts.get(0));for(int n=0;n<130;n++){Map<String,Object> copy=new LinkedHashMap<>(example);copy.put("id","legacy-"+n);receipts.add(copy);}GrowthRoom restored=new GrowthRoom("legacy",offline(),Runnable::run,legacy,()->0);assertEquals(200,command(restored,"REST",Collections.emptyMap()).status);assertEquals(64,Json.asArray(legacy.load("growth:legacy").get("receipts")).size());
 }

 @Test void skillTutorialCompletionSurvivesTraceEvictionAndIgnoresNonSkillDefeat(){
  Store store=new Store();GrowthRoom room=new GrowthRoom("skill-guide",offline(),Runnable::run,store,()->0);
  command(room,"STARTER",Json.object("species","TREECKO"));
  Map<String,Object> saved=store.load("growth:skill-guide");saved.remove("tutorialSkillUsed");
  saved.put("trace",new ArrayList<>(Arrays.asList(Json.object("code","GROWTH_DEFEATED","status","SUCCESS","data",Collections.emptyMap(),"time",1))));
  room=new GrowthRoom("skill-guide",offline(),Runnable::run,store,()->0);assertEquals(false,room.snapshot().get("tutorialSkillUsed"));
  for(int i=0;i<40&&!"forest".equals(Json.asObject(room.snapshot().get("world")).get("region"));i++)assertEquals(200,command(room,"MANUAL",Json.object("action","go_to_region","arguments",Json.object("regionId","forest"))).status);
  Map<String,Object> world=Json.asObject(room.snapshot().get("world"));String target=(String)Json.asObject(Json.asArray(world.get("encounters")).get(0)).get("id");
  for(int i=0;i<40;i++){Map<String,Object> row=Json.asObject(Json.asArray(Json.asObject(room.snapshot().get("world")).get("encounters")).get(0));if(Boolean.TRUE.equals(row.get("targetAdjacent")))break;assertEquals(200,command(room,"MANUAL",Json.object("action","move_to","arguments",Json.object("targetId",target))).status);}
  Map<String,Object> partner=Json.asObject(Json.asObject(room.snapshot().get("world")).get("partner"));String move=(String)Json.asObject(Json.asArray(partner.get("moves")).get(0)).get("id");
  assertEquals(200,command(room,"MANUAL",Json.object("action","use_skill","arguments",Json.object("targetId",target,"moveId",move))).status);
  assertEquals(true,room.snapshot().get("tutorialSkillUsed"));
  for(int i=0;i<70;i++)command(room,"MANUAL",Json.object("action","move","arguments",Json.object("direction",i%2==0?"N":"S")));
  GrowthRoom restored=new GrowthRoom("skill-guide",offline(),Runnable::run,store,()->0);assertEquals(true,restored.snapshot().get("tutorialSkillUsed"));
 }

 @Test void firstAdventureKeepsAllThreeStartersPracticeSafeAndRewardExactlyOnce(){
  for(String species:Arrays.asList("TREECKO","MUDKIP","TORCHIC")){
   Store store=new Store();String owner="story-"+species;GrowthRoom r=new GrowthRoom(owner,offline(),Runnable::run,store,()->0);
   command(r,"STARTER",Json.object("species",species));Map<String,Object> original=Json.asObject(Json.asObject(r.snapshot().get("world")).get("partner"));
   assertEquals("MOVE",Json.asObject(r.snapshot().get("firstAdventure")).get("stage"));
   assertEquals(409,command(r,"PRACTICE_SKILL",Json.object("moveId","POUND")).status);
   command(r,"MANUAL",Json.object("action","move","arguments",Json.object("direction","E")));command(r,"MANUAL",Json.object("action","move","arguments",Json.object("direction","W")));
   assertEquals(200,command(r,"PRACTICE_SELECT",Collections.emptyMap()).status);
   String move=(String)Json.asObject(Json.asArray(original.get("moves")).get(0)).get("id");
   assertEquals(200,command(r,"PRACTICE_SKILL",Json.object("moveId",move)).status);
   assertEquals(Json.write(original),Json.write(Json.asObject(Json.asObject(r.snapshot().get("world")).get("partner"))));
   r=new GrowthRoom(owner,offline(),Runnable::run,store,()->0);assertEquals("FOREST",Json.asObject(r.snapshot().get("firstAdventure")).get("stage"));
   for(int i=0;i<80&&!"forest".equals(Json.asObject(r.snapshot().get("world")).get("region"));i++)assertEquals(200,command(r,"MANUAL",Json.object("action","go_to_region","arguments",Json.object("regionId","forest"))).status);
   Map<String,Object> w=Json.asObject(r.snapshot().get("world"));Map<String,Object> target=Json.asArray(w.get("encounters")).stream().map(Json::asObject).filter(e->!species.equals(e.get("species"))).findFirst().get();String id=(String)target.get("id");
   for(int i=0;i<80;i++){w=Json.asObject(r.snapshot().get("world"));Map<String,Object> row=Json.asArray(w.get("encounters")).stream().map(Json::asObject).filter(e->id.equals(e.get("id"))).findFirst().get();if(Boolean.TRUE.equals(row.get("targetAdjacent")))break;assertEquals(200,command(r,"MANUAL",Json.object("action","move_to","arguments",Json.object("targetId",id))).status);}
   assertEquals(200,command(r,"MANUAL",Json.object("action","capture","arguments",Json.object("targetId",id))).status);
   assertEquals("CAMP",Json.asObject(r.snapshot().get("firstAdventure")).get("stage"));
   if(species.equals("TREECKO")){
    Json.asObject(store.load("growth:"+owner).get("firstAdventure")).put("stage","FOREST");
    r=new GrowthRoom(owner,offline(),Runnable::run,store,()->0);command(r,"MANUAL",Json.object("action","observe","arguments",Collections.emptyMap()));
    assertEquals("CAMP",Json.asObject(r.snapshot().get("firstAdventure")).get("stage"),"previous forest capture must be acknowledged");
   }

   if(species.equals("MUDKIP")){
    for(int i=0;i<80&&!"lab".equals(Json.asObject(r.snapshot().get("world")).get("region"));i++)command(r,"MANUAL",Json.object("action","go_to_region","arguments",Json.object("regionId","lab")));
    Map<String,Object> other=Json.asArray(Json.asObject(r.snapshot().get("world")).get("team")).stream().map(Json::asObject).filter(row->!original.get("captureId").equals(row.get("captureId"))).findFirst().get();
    assertEquals(200,command(r,"SELECT",Json.object("captureId",other.get("captureId"))).status);assertEquals("CAMP",Json.asObject(r.snapshot().get("firstAdventure")).get("stage"));
    assertEquals(200,command(r,"SELECT",Json.object("captureId",original.get("captureId"))).status);
    for(int i=0;i<80&&!"forest".equals(Json.asObject(r.snapshot().get("world")).get("region"));i++)command(r,"MANUAL",Json.object("action","go_to_region","arguments",Json.object("regionId","forest")));
   }

   for(int i=0;i<80&&"CAMP".equals(Json.asObject(r.snapshot().get("firstAdventure")).get("stage"));i++)assertEquals(200,command(r,"MANUAL",Json.object("action","go_to_recovery","arguments",Collections.emptyMap())).status);
   assertEquals("GROWTH",Json.asObject(r.snapshot().get("firstAdventure")).get("stage"));
   Map<String,Object> p1=Json.asObject(Json.asObject(r.snapshot().get("world")).get("partner"));assertEquals(original.get("captureId"),p1.get("captureId"));assertTrue(((Number)p1.get("level")).intValue()>5);assertEquals(p1.get("hp"),p1.get("maxHp"));
   int xp=((Number)p1.get("experience")).intValue();r=new GrowthRoom(owner,offline(),Runnable::run,store,()->0);command(r,"REST",Collections.emptyMap());assertEquals(xp,Json.asObject(Json.asObject(r.snapshot().get("world")).get("partner")).get("experience"));
   assertEquals(200,command(r,"STORY_CONTINUE",Collections.emptyMap()).status);
   for(int i=0;i<80&&!"lab".equals(Json.asObject(r.snapshot().get("world")).get("region"));i++)assertEquals(200,command(r,"MANUAL",Json.object("action","go_to_region","arguments",Json.object("regionId","lab"))).status);
   assertEquals(200,command(r,"STORY_FINISH",Collections.emptyMap()).status);assertEquals("TRAINER",Json.asObject(r.snapshot().get("firstAdventure")).get("stage"));assertEquals(409,command(r,"STORY_FINISH",Collections.emptyMap()).status);
  }
 }
 @Test void legacyStoryMissingDoesNotEnrollExistingPlayer(){Store store=new Store();GrowthRoom r=new GrowthRoom("legacy-story",offline(),Runnable::run,store,()->0);command(r,"STARTER",Json.object("species","MUDKIP"));store.load("growth:legacy-story").remove("firstAdventure");r=new GrowthRoom("legacy-story",offline(),Runnable::run,store,()->0);assertEquals("SKIPPED",Json.asObject(r.snapshot().get("firstAdventure")).get("stage"));assertEquals(200,command(r,"REST",Collections.emptyMap()).status);}

 @Test void tutorialReplacementNeverSpawnsOnThePlayersTile(){
  game.agent.growth.GrowthWorld w=new game.agent.growth.GrowthWorld(()->0);w.starter("TREECKO");Map<String,Object> saved=w.export();Map<String,Object> occupied=null;
  for(Object row:Json.asArray(saved.get("encounters"))){Map<String,Object> e=Json.asObject(row);if(!"TREECKO".equals(Json.asObject(e.get("pokemon")).get("species"))){e.put("state","DEFEATED");Json.asObject(e.get("pokemon")).put("hp",0);if(occupied==null)occupied=e;}}
  saved.put("region","forest");saved.put("x",occupied.get("x"));saved.put("y",occupied.get("y"));game.agent.growth.GrowthWorld restored=game.agent.growth.GrowthWorld.restore(saved,()->0);
  assertDoesNotThrow(()->restored.ensureFirstAdventureTargets("TREECKO"));Map<String,Object> view=restored.view();assertEquals(3,Json.asArray(view.get("encounters")).size());assertTrue(Json.asArray(view.get("encounters")).stream().map(Json::asObject).anyMatch(e->"WILD".equals(e.get("state"))&&!"TREECKO".equals(e.get("species"))));
 }
 @Test void firstFieldBattleThenCampPrecedesCaptureAndSurvivesRestart(){
  Store store=new Store();String owner="battle-then-camp";GrowthRoom r=new GrowthRoom(owner,offline(),Runnable::run,store,()->0);
  command(r,"STARTER",Json.object("species","TREECKO"));Object identity=Json.asObject(Json.asObject(r.snapshot().get("world")).get("partner")).get("captureId");
  command(r,"MANUAL",Json.object("action","move","arguments",Json.object("direction","E")));command(r,"MANUAL",Json.object("action","move","arguments",Json.object("direction","W")));
  command(r,"PRACTICE_SELECT",Collections.emptyMap());command(r,"PRACTICE_SKILL",Json.object("moveId","POUND"));
  for(int n=0;n<80&&!"forest".equals(Json.asObject(r.snapshot().get("world")).get("region"));n++)command(r,"MANUAL",Json.object("action","go_to_region","arguments",Json.object("regionId","forest")));
  assertEquals("BATTLE",Json.asObject(r.snapshot().get("firstAdventure")).get("stage"));
  String id=(String)Json.asObject(Json.asArray(Json.asObject(r.snapshot().get("world")).get("encounters")).get(0)).get("id");
  for(int n=0;n<80;n++){Map<String,Object> target=Json.asArray(Json.asObject(r.snapshot().get("world")).get("encounters")).stream().map(Json::asObject).filter(e->id.equals(e.get("id"))).findFirst().get();if(Boolean.TRUE.equals(target.get("targetAdjacent")))break;command(r,"MANUAL",Json.object("action","move_to","arguments",Json.object("targetId",id)));}
  assertEquals(200,command(r,"MANUAL",Json.object("action","use_skill","arguments",Json.object("targetId",id,"moveId","POUND"))).status);
  assertEquals("REST_FIRST",Json.asObject(r.snapshot().get("firstAdventure")).get("stage"));r=new GrowthRoom(owner,offline(),Runnable::run,store,()->0);
  assertEquals("REST_FIRST",Json.asObject(r.snapshot().get("firstAdventure")).get("stage"));
  for(int n=0;n<80&&!"CAPTURE".equals(Json.asObject(r.snapshot().get("firstAdventure")).get("stage"));n++)command(r,"MANUAL",Json.object("action","go_to_recovery","arguments",Collections.emptyMap()));
  assertEquals("CAPTURE",Json.asObject(r.snapshot().get("firstAdventure")).get("stage"));Map<String,Object> p=Json.asObject(Json.asObject(r.snapshot().get("world")).get("partner"));assertEquals(identity,p.get("captureId"));assertEquals(p.get("maxHp"),p.get("hp"));
 }

}
