package game.agent;

import game.agent.llm.*;
import game.agent.runtime.*;
import game.agent.tools.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.net.SocketTimeoutException;
import static org.junit.jupiter.api.Assertions.*;

class GeminiGatewayTest {
    static AgentLoop.Context context() {
        AgentTask task=new AgentTask("t"); task.start(); GameToolRegistry tools=new GameToolRegistry(r->null);
        Map<String,ToolParameter> args=new LinkedHashMap<>(); args.put("quantity",ToolParameter.integer(1,20)); args.put("itemId",ToolParameter.string());
        tools.register(new ToolDefinition("pickup","Pick real berries",true,args),r->null);
        AtomicReference<AgentLoop.Context> capture=new AtomicReference<>();
        AgentLoop loop=new AgentLoop(task,"Collect berries without spending",tools,Runnable::run,c->{capture.set(c);return null;},
            ()->Collections.singletonMap("visibleBerry","2"),()->false,100,5,5,3);
        loop.tick(0); return capture.get();
    }
    static String response(String part) {
        return "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":["+part+"]}}]}";
    }
    static String function(String name,String args) {
        return response("{\"functionCall\":{\"name\":\""+name+"\",\"args\":"+args+"}}");
    }
    static GeminiGateway gateway(GeminiTransport transport) {
        return new GeminiGateway(new GeminiConfig("test-key","gemini-3.8-flash",1000),transport);
    }
    @Test void realWireRequestContainsSchemasAndNoKeyInBody() {
        AtomicReference<String> request=new AtomicReference<>();
        GeminiGateway gateway=gateway((model,key,body,timeout)->{
            assertEquals("test-key",key); assertEquals("gemini-3.8-flash",model); request.set(body);
            return new GeminiTransport.Response(200,function("pickup","{\"itemId\":\"BERRY\",\"quantity\":2}"));
        });
        ToolRequest call=gateway.decide(context()); assertEquals("pickup",call.getName());
        assertFalse(request.get().contains("test-key")); assertTrue(request.get().contains("functionDeclarations"));
        assertTrue(request.get().contains("additionalProperties")); assertTrue(request.get().contains("visibleBerry"));
        assertTrue(request.get().contains("minimum")); assertTrue(request.get().contains("ANY"));
    }
    @Test void unknownMalformedOrMultipleCallsNeverBecomeActions() {
        List<String> bad=Arrays.asList(function("shell","{}"),function("pickup","{\"itemId\":\"BERRY\",\"quantity\":0}"),
            function("pickup","{\"itemId\":\"BERRY\",\"quantity\":\"2\"}"),
            function("pickup","{\"itemId\":\"BERRY\",\"quantity\":2,\"owner\":\"other\"}"),
            response("{\"text\":\"I completed the quest\"}"),response("{\"functionCall\":{\"name\":\"pickup\",\"args\":{}}},{\"functionCall\":{\"name\":\"pickup\",\"args\":{}}}"),"not json");
        for(String body:bad) assertEquals(ProviderException.Code.INVALID_RESPONSE,
            assertThrows(ProviderException.class,()->gateway((m,k,b,t)->new GeminiTransport.Response(200,body)).decide(context())).getCode());
    }
    @Test void errorsAreClassifiedWithoutEchoingProviderBodyOrKey() {
        for(int status:new int[]{400,401,403,429,500}) {
            ProviderException error=assertThrows(ProviderException.class,()->gateway((m,k,b,t)->new GeminiTransport.Response(status,"test-key private details")).decide(context()));
            assertFalse(error.toString().contains("test-key")); assertNull(error.getCause());
            assertEquals(status==429?ProviderException.Code.RATE_LIMIT:status==401||status==403?ProviderException.Code.AUTHENTICATION:
                status==400?ProviderException.Code.REQUEST_REJECTED:ProviderException.Code.UNAVAILABLE,error.getCode());
        }
        assertEquals(ProviderException.Code.TIMEOUT,assertThrows(ProviderException.class,()->gateway((m,k,b,t)->{throw new SocketTimeoutException("test-key");}).decide(context())).getCode());
    }
    @Test void blockedAndTruncatedResponsesAreNotExecuted() {
        String valid=function("pickup","{\"itemId\":\"BERRY\",\"quantity\":2}");
        assertEquals(ProviderException.Code.INVALID_RESPONSE,assertThrows(ProviderException.class,()->gateway((m,k,b,t)->
            new GeminiTransport.Response(200,valid.replace("STOP","MAX_TOKENS"))).decide(context())).getCode());
        assertEquals(ProviderException.Code.SAFETY_BLOCK,assertThrows(ProviderException.class,()->gateway((m,k,b,t)->
            new GeminiTransport.Response(200,"{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}")).decide(context())).getCode());
    }
    @Test void configRejectsMissingCredentialsAndPathInjection() {
        assertThrows(ProviderException.class,()->GeminiConfig.fromEnvironment(Collections.emptyMap()));
        assertThrows(IllegalArgumentException.class,()->new GeminiConfig("key","model?key=leak",1000));
        assertThrows(IllegalArgumentException.class,()->new GeminiConfig("key\nheader","gemini-3.8-flash",1000));
    }
}
