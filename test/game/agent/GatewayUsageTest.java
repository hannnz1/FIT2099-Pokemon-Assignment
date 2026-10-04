package game.agent;
import game.agent.llm.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GatewayUsageTest {
 @Test void openAiExtractsOnlyAvailableSafeUsageAndClearsEachResponse() throws Exception {
  Map<String,Object> response=Json.asObject(Json.read(OpenAiGatewayTest.function("pickup","{\"itemId\":\"BERRY\",\"quantity\":2}")));
  response.put("usage",Json.object("input_tokens",12,"output_tokens",4,"total_tokens",16,"secret","test-key"));
  AtomicReference<String> body=new AtomicReference<>(Json.write(response));
  OpenAiGateway gateway=OpenAiGatewayTest.gateway((m,k,b,t)->new JsonTransport.Response(200,body.get()));
  gateway.decide(GeminiGatewayTest.context());assertEquals(12L,gateway.getUsage().get("inputTokens"));assertEquals(4L,gateway.getUsage().get("outputTokens"));
  assertEquals(16L,gateway.getUsage().get("totalTokens"));assertEquals(3,gateway.getUsage().size());
  assertThrows(UnsupportedOperationException.class,()->gateway.getUsage().put("x",1));
  AtomicReference<Map<String,Number>> other=new AtomicReference<>();Thread thread=new Thread(()->other.set(gateway.getUsage()));thread.start();thread.join();assertTrue(other.get().isEmpty());
  body.set(OpenAiGatewayTest.function("pickup","{\"itemId\":\"BERRY\",\"quantity\":2}"));gateway.decide(GeminiGatewayTest.context());assertTrue(gateway.getUsage().isEmpty());
 }
 @Test void geminiNormalizesActualUsageAndIgnoresInvalidNumbers() {
  Map<String,Object> response=Json.asObject(Json.read(GeminiGatewayTest.function("pickup","{\"itemId\":\"BERRY\",\"quantity\":2}")));
  response.put("usageMetadata",Json.object("promptTokenCount",7,"candidatesTokenCount",3,"totalTokenCount",10,"thoughtsTokenCount",999));
  GeminiGateway gateway=GeminiGatewayTest.gateway((m,k,b,t)->new GeminiTransport.Response(200,Json.write(response)));
  gateway.decide(GeminiGatewayTest.context());assertEquals(7L,gateway.getUsage().get("inputTokens"));assertEquals(3L,gateway.getUsage().get("outputTokens"));assertEquals(10L,gateway.getUsage().get("totalTokens"));assertEquals(3,gateway.getUsage().size());
  response.put("usageMetadata",Json.object("promptTokenCount",-1,"candidatesTokenCount","private","totalTokenCount",1.5));
  gateway.decide(GeminiGatewayTest.context());assertTrue(gateway.getUsage().isEmpty());
 }
}
