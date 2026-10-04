package game.agent.web;
import game.agent.llm.Json;import game.agent.runtime.AgentLoop;import game.agent.tools.ToolRequest;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class GrowthContinuityTest {
 private AgentRoom.Reply send(GrowthRoom room,String command,Map<String,Object> params){Map<String,Object> s=room.snapshot();return room.command(Json.object("requestId",UUID.randomUUID().toString(),"taskId",s.get("taskId"),"expectedRevision",s.get("revision"),"command",command,"params",params),10);}
 private ToolRequest next(AgentLoop.Context c){Map<String,String> o=c.getObservation();String phase=o.get("trainingPhase");String name;Map<String,Object> args=Collections.emptyMap();
  if("REST".equals(phase))name="rest";else if("RETURN_TO_REST".equals(phase))name="go_to_recovery";else if("PREPARE_NEW_EXPEDITION".equals(phase))name="next_expedition";else if("RETURN_FOR_NEW_EXPEDITION".equals(phase)){name="go_to_region";args=Json.object("regionId","lab");}else if(!o.get("region").equals(o.get("expeditionRegion"))){name="go_to_region";args=Json.object("regionId",o.get("expeditionRegion"));}else{Map<String,Object> e=Json.asObject(Json.asArray(Json.read(o.get("encounters"))).get(0));name=Boolean.TRUE.equals(e.get("targetAdjacent"))?"use_skill":"move_to";args=Json.object("targetId",e.get("id"));if("use_skill".equals(name))args.put("moveId",e.get("recommendedMoveId"));}return new ToolRequest("test-choice",name,args);
 }
 @Test void oneTrainingCommandCrossesThreeMapsAndBattlesWithoutAnyPlayerClick(){
  Set<String> observedRegions=new LinkedHashSet<>(),goals=new HashSet<>();ProviderSelection p=new ProviderSelection("test","continuity",1000,c->{observedRegions.add(c.getObservation().get("region"));goals.add(c.getGoal());return next(c);},null);
  GrowthRoomTest.Store store=new GrowthRoomTest.Store();GrowthRoom room=new GrowthRoom("continuous-route",p,Runnable::run,store,()->0);
  assertEquals(200,send(room,"STARTER",Json.object("species","TREECKO")).status);String individual=(String)Json.asObject(Json.asObject(room.snapshot().get("world")).get("partner")).get("captureId");
  assertEquals(200,send(room,"AI_TRAIN",Json.object("regionId","mountain","targetLevel",6)).status);String task=(String)room.snapshot().get("taskId");Set<String> visited=new LinkedHashSet<>();
  for(int n=11;n<1000&&!"COMPLETED".equals(room.snapshot().get("status"));n++){room.tick(n);Map<String,Object> s=room.snapshot(),w=Json.asObject(s.get("world"));visited.add((String)w.get("region"));assertEquals(task,s.get("taskId"));assertEquals(6,s.get("targetLevel"));assertEquals("mountain",s.get("targetRegion"));assertEquals(individual,s.get("targetIndividual"));assertTrue(Arrays.asList("RUNNING","REPLANNING","COMPLETED").contains(s.get("status")),Json.write(s.get("trace")));}
  assertEquals("COMPLETED",room.snapshot().get("status"));assertTrue(visited.containsAll(Arrays.asList("forest","river","mountain")));assertEquals(1,goals.size());assertTrue(goals.iterator().next().contains("mountain"));assertTrue(Json.asArray(room.snapshot().get("stepTrace")).stream().map(Json::asObject).anyMatch(s->"use_skill".equals(s.get("toolName"))));assertEquals(task,store.load("growth:continuous-route").get("taskId"));room.close();
 }

 @Test void mudkipLongAiTrainingCanSavePastLevelEleven(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory){
  final String[] failure={null};final int[] nodes={0};
  try(game.agent.persistence.WorldStore disk=new game.agent.persistence.FileWorldStore(directory)){
   game.agent.persistence.WorldStore tracked=new game.agent.persistence.WorldStore(){public Map<String,Object> load(String owner){return disk.load(owner);}public void close(){}public void save(String owner,Map<String,Object> state){nodes[0]=countNodes(state);try{disk.save(owner,state);}catch(game.agent.persistence.StoreException e){failure[0]=e.getCode().name();throw e;}}};
   ProviderSelection provider=new ProviderSelection("test","long-training",1000,this::next,null);GrowthRoom room=new GrowthRoom("mudkip-long",provider,Runnable::run,tracked,()->0);
   assertEquals(200,send(room,"STARTER",Json.object("species","MUDKIP")).status);assertEquals(200,send(room,"AI_TRAIN",Json.object("targetLevel",12,"regionId","forest")).status);
   for(int n=11;n<4000&&!"COMPLETED".equals(room.snapshot().get("status"));n++){room.tick(n);assertNotEquals("STORAGE_ERROR",room.snapshot().get("status"),"storage="+failure[0]+" nodes="+nodes[0]+" level="+Json.asObject(Json.asObject(room.snapshot().get("world")).get("partner")).get("level"));if(Arrays.asList("FAILED","PROVIDER_UNAVAILABLE").contains(room.snapshot().get("status")))fail(Json.write(room.snapshot().get("trace")));}
   assertEquals("COMPLETED",room.snapshot().get("status"));GrowthRoom reopened=new GrowthRoom("mudkip-long",provider,Runnable::run,tracked,()->0);assertEquals("COMPLETED",reopened.snapshot().get("status"));assertTrue(((Number)Json.asObject(Json.asObject(reopened.snapshot().get("world")).get("partner")).get("level")).intValue()>=12);room.close();reopened.close();
  }
 }
 private static int countNodes(Object value){int n=1;if(value instanceof Map)for(Object key:((Map<?,?>)value).keySet())n+=1+countNodes(((Map<?,?>)value).get(key));else if(value instanceof List)for(Object item:(List<?>)value)n+=countNodes(item);return n;}

 @Test void longTrainingWithCollectedPartnersDoesNotExceedCheckpointBudget(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory){
  try(game.agent.persistence.WorldStore disk=new game.agent.persistence.FileWorldStore(directory)){
   GrowthRoom room=new GrowthRoom("mudkip-team",new ProviderSelection("test","long-training",1000,this::next,null),Runnable::run,disk,()->0);
   assertEquals(200,send(room,"STARTER",Json.object("species","MUDKIP")).status);
   for(int wave=0;wave<7;wave++){
    travel(room,"forest");
    for(Object raw:new ArrayList<>(Json.asArray(Json.asObject(room.snapshot().get("world")).get("encounters")))){
     if(Json.asArray(Json.asObject(room.snapshot().get("world")).get("team")).size()>=20)break;
     String target=(String)Json.asObject(raw).get("id");for(int step=0;step<80;step++){AgentRoom.Reply a=manual(room,"move_to",Json.object("targetId",target));assertEquals(200,a.status);if("ARRIVED".equals(a.body.get("reasonCode"))||"TARGET_ADJACENT".equals(a.body.get("reasonCode")))break;}
     assertEquals(200,manual(room,"capture",Json.object("targetId",target)).status);
    }
    if(wave<6){travel(room,"lab");assertEquals(200,send(room,"NEXT_EXPEDITION",Collections.emptyMap()).status);}
   }
   assertEquals(20,Json.asArray(Json.asObject(room.snapshot().get("world")).get("team")).size());assertEquals(200,send(room,"AI_TRAIN",Json.object("targetLevel",12,"regionId","forest")).status);
   for(int n=11;n<4000&&!"COMPLETED".equals(room.snapshot().get("status"));n++){room.tick(n);assertNotEquals("STORAGE_ERROR",room.snapshot().get("status"),"valid long-play checkpoint exceeded budget");if(Arrays.asList("FAILED","PROVIDER_UNAVAILABLE").contains(room.snapshot().get("status")))fail(Json.write(room.snapshot().get("trace")));}
   assertEquals("COMPLETED",room.snapshot().get("status"));room.close();
  }
 }
 private AgentRoom.Reply manual(GrowthRoom room,String action,Map<String,Object> args){return send(room,"MANUAL",Json.object("action",action,"arguments",args));}
 private void travel(GrowthRoom room,String id){for(int n=0;n<160&&!id.equals(Json.asObject(room.snapshot().get("world")).get("region"));n++)assertEquals(200,manual(room,"go_to_region",Json.object("regionId",id)).status);assertEquals(id,Json.asObject(room.snapshot().get("world")).get("region"));}
}
