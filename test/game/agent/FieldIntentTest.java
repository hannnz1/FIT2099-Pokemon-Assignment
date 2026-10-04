package game.agent;
import game.agent.llm.*;import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class FieldIntentTest {
 static String result(String goal,String target,String...cs){return OpenAiGatewayTest.text(Json.write(Json.object("status","SUPPORTED","goal",goal,"questId","quest","targetId",target,"constraints",Arrays.asList(cs),"areaId",null,"deadlineTurn",null,"unsupportedClauses",Collections.emptyList())));}
 @Test void typedFieldGoalsPreserveConstraintsAndNeverPretendToBeBerryQuests(){
  TaskIntent t=OpenAiGatewayTest.gateway((m,k,b,time)->new JsonTransport.Response(200,result("CAPTURE","field-mudkip","NO_SPENDING","NO_ACTIVE_BATTLE"))).parse("捕捉水跃鱼，不花金币也不战斗","quest");
  assertEquals("CAPTURE",t.getKind());assertEquals("field-mudkip",t.getTargetId());assertTrue(t.isField());assertTrue(t.has(TaskIntent.Constraint.NO_ACTIVE_BATTLE));assertTrue(t.getGoal().contains("field-mudkip"));
 }
 @Test void fieldSchemaListsOnlyTrustedGoalsAndTargets(){OpenAiGatewayTest.gateway((m,k,b,time)->{assertTrue(b.contains("field-treecko"));assertTrue(b.contains("field-torchic"));assertTrue(b.contains("DEFEAT"));return new JsonTransport.Response(200,result("DEFEAT","field-torchic"));}).parse("击败火稚鸡","quest");}
 @Test void conflictingAndUncapturableTargetsCannotBecomeValidIntents(){for(String body:Arrays.asList(result("CAPTURE","field-torchic"),result("DEFEAT","field-treecko","NO_ACTIVE_BATTLE"),result("CAPTURE","npc-treecko"),result("COMPLETE_QUEST","field-treecko"))){assertThrows(ProviderException.class,()->OpenAiGatewayTest.gateway((m,k,b,time)->new JsonTransport.Response(200,body)).parse("请求","quest"));}}
 @Test void legacyBerryResultsAndFactoryRemainCompatible(){TaskIntent t=OpenAiGatewayTest.gateway((m,k,b,time)->new JsonTransport.Response(200,OpenAiGatewayTest.task("quest","NO_SPENDING"))).parse("树果任务","quest");assertEquals("COMPLETE_QUEST",t.getKind());assertNull(t.getTargetId());assertFalse(t.isField());}
}
