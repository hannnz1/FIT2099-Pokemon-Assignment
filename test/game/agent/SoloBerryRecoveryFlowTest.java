package game.agent;
import game.agent.web.*;
import game.agent.llm.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SoloBerryRecoveryFlowTest {
 @Test void wrongItemThenRepeatedObservationStillCollectsAndDeliversWithoutResume(){
  AtomicBoolean wrong=new AtomicBoolean(false);
  ProviderSelection provider=AgentRoomTest.provider((m,k,b,t)->{
   if(b.contains("json_schema"))return new JsonTransport.Response(200,AgentRoomTest.parsed(b));
   Map<String,Object> input=Json.asObject(Json.read((String)Json.asObject(Json.read(b)).get("input")));
   Map<String,Object> s=Json.asObject(input.get("observation"));
   String name,args;
   if(Integer.parseInt(String.valueOf(s.get("visibleBerry")))>0){
    if(!wrong.getAndSet(true)){name="pickup";args="{\"itemId\":\"Berry\",\"quantity\":2}";}
    else{name="observe_quest";args="{}";}
   }else{name="move_to";args=Json.write(Json.object("locationId",Integer.parseInt(String.valueOf(s.get("carriedBerry")))>0?"alternative":"orchard"));}
   return new JsonTransport.Response(200,OpenAiGatewayTest.function(name,args));
  });
  AgentRoom room=new AgentRoom("solo-recovery-flow",provider,Runnable::run);
  try{AgentRoomTest.start(room);for(int i=2;i<150&&!Set.of("COMPLETED","FAILED","PAUSED","PROVIDER_UNAVAILABLE").contains(room.snapshot().get("status"));i++)room.tick(i);
   Map<String,Object> v=room.snapshot();assertEquals("COMPLETED",v.get("status"),Json.write(v.get("trace")));assertEquals(3,((Number)v.get("delivered")).intValue());
   assertTrue(wrong.get());assertEquals(3,v.get("resumeRemaining"));
   assertTrue(Json.write(v.get("stepTrace")).contains("ENGINE_LOCAL_PICKUP_PRIORITY"));
  }finally{room.close();}
 }
 @Test void repeatedInvalidLiteralsStopAtExistingFailureLimitWithoutWorldWrites(){
  game.agent.runtime.AgentTask task=new game.agent.runtime.AgentTask("bounded");task.start();java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger(),writes=new java.util.concurrent.atomic.AtomicInteger();
  game.agent.tools.GameToolRegistry tools=new game.agent.tools.GameToolRegistry(r->null);
  tools.register(new game.agent.tools.ToolDefinition("pickup","pick",true,Collections.emptyMap()),r->{writes.incrementAndGet();return game.agent.action.ActionResult.success("PICKED_UP");});
  game.agent.runtime.AgentLoop loop=new game.agent.runtime.AgentLoop(task,"pick",tools,Runnable::run,c->{calls.incrementAndGet();throw new ProviderException(ProviderException.Code.INVALID_TOOL_ARGUMENTS);},Collections::emptyMap,()->false,100,20,10,3);
  for(int n=0;n<10;n++)loop.tick(n);
  assertEquals(3,calls.get());assertEquals(0,writes.get());assertEquals(game.agent.runtime.AgentTask.State.FAILED,task.getState());
 }
}
