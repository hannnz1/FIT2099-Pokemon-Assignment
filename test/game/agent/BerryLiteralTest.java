package game.agent;
import game.agent.tools.ToolParameter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BerryLiteralTest {
 @Test void providerSchemasConstrainTheExactItemIdentifier(){
  game.agent.runtime.AgentTask task=new game.agent.runtime.AgentTask("literal");task.start();game.agent.tools.GameToolRegistry tools=new game.agent.tools.GameToolRegistry(r->null);
  tools.register(new game.agent.tools.ToolDefinition("pickup","pick",true,java.util.Map.of("itemId",ToolParameter.literal("BERRY"),"quantity",ToolParameter.integer(1,20))),r->null);
  java.util.concurrent.atomic.AtomicReference<game.agent.runtime.AgentLoop.Context> capture=new java.util.concurrent.atomic.AtomicReference<>();
  new game.agent.runtime.AgentLoop(task,"pick",tools,Runnable::run,c->{capture.set(c);return null;},java.util.Collections::emptyMap,()->false,100,5,5,3).tick(0);
  new game.agent.llm.OpenAiGateway(new game.agent.llm.OpenAiConfig("test-key","test-model",1000),(m,k,b,t)->{assertTrue(b.contains("\"enum\":[\"BERRY\"]"));return new game.agent.llm.JsonTransport.Response(200,OpenAiGatewayTest.function("pickup","{\"itemId\":\"BERRY\",\"quantity\":1}"));}).decide(capture.get());
  assertThrows(game.agent.llm.ProviderException.class,()->new game.agent.llm.OpenAiGateway(new game.agent.llm.OpenAiConfig("test-key","test-model",1000),(m,k,b,t)->new game.agent.llm.JsonTransport.Response(200,OpenAiGatewayTest.function("pickup","{\"itemId\":\"Berry\",\"quantity\":1}"))).decide(capture.get()));
 }

 @Test void exactItemIdentifierRejectsDisplayNamesBeforeExecution(){ToolParameter p=ToolParameter.literal("BERRY");assertTrue(p.accepts("BERRY"));assertFalse(p.accepts("Berry"));assertFalse(p.accepts("树果"));assertEquals(java.util.List.of("BERRY"),p.getAllowedValues());assertTrue(ToolParameter.string().accepts("Berry"));}
}
