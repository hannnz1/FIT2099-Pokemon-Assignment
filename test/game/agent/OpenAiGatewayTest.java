package game.agent;

import game.agent.llm.*;
import game.agent.tools.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.net.SocketTimeoutException;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiGatewayTest {
    static OpenAiGateway gateway(JsonTransport transport) {
        return new OpenAiGateway(new OpenAiConfig("test-key","gpt-6.1-sol",1000),transport);
    }
    static String function(String name,String args) {
        return Json.write(Json.object("status","completed","output",Collections.singletonList(
            Json.object("type","function_call","status","completed","call_id","untrusted-id","name",name,"arguments",args))));
    }
    static String text(String text) {
        return Json.write(Json.object("status","completed","output",Collections.singletonList(Json.object("type","message","status","completed",
            "content",Collections.singletonList(Json.object("type","output_text","text",text))))));
    }
    static String task(String quest,String...constraints) {
        return text(Json.write(Json.object("status","SUPPORTED","goal","COMPLETE_QUEST","questId",quest,"constraints",Arrays.asList(constraints),"unsupportedClauses",Collections.emptyList())));
    }
    @Test void responsesRequestDeclaresStrictToolsAndReturnsOnlyValidatedCall() {
        OpenAiGateway gateway=gateway((model,key,body,timeout)->{
            Map<String,Object> request=Json.asObject(Json.read(body));
            assertEquals("gpt-6.1-sol",request.get("model")); assertEquals(false,request.get("store"));
            assertEquals(false,request.get("parallel_tool_calls")); assertEquals("required",request.get("tool_choice"));
            Map<String,Object> tool=Json.asObject(Json.asArray(request.get("tools")).get(0));
            assertEquals("function",tool.get("type")); assertEquals(true,tool.get("strict"));
            Map<String,Object> schema=Json.asObject(tool.get("parameters")); assertEquals(false,schema.get("additionalProperties"));
            assertEquals(Arrays.asList("quantity","itemId"),schema.get("required"));
            assertTrue(body.contains("visibleBerry")); assertFalse(body.contains("test-key"));
            return new JsonTransport.Response(200,function("pickup","{\"itemId\":\"BERRY\",\"quantity\":2}"));
        });
        ToolRequest call=gateway.decide(GeminiGatewayTest.context()); assertEquals("pickup",call.getName());
        assertFalse(call.getActionId().equals("untrusted-id"));
    }
    @Test void invalidUnknownMultipleAndTextOnlyOutputsAreRejected() {
        String valid=function("pickup","{\"itemId\":\"BERRY\",\"quantity\":2}");
        Map<String,Object> multiple=Json.asObject(Json.read(valid)); List<Object> calls=Json.asArray(multiple.get("output")); calls.add(calls.get(0));
        for(String body:Arrays.asList(function("shell","{}"),function("pickup","{\"itemId\":\"BERRY\",\"quantity\":0}"),
            function("pickup","{\"itemId\":\"BERRY\",\"quantity\":\"2\"}"),function("pickup","{\"itemId\":\"BERRY\",\"quantity\":2,\"owner\":\"other\"}"),
            text("Quest completed"),valid.replace("completed","incomplete"),Json.write(multiple),"not json")) {
            assertEquals(ProviderException.Code.INVALID_RESPONSE,assertThrows(ProviderException.class,
                ()->gateway((m,k,b,t)->new JsonTransport.Response(200,body)).decide(GeminiGatewayTest.context())).getCode());
        }
    }
    @Test void refusalIsNotAnAction() {
        String body=text("no").replace("output_text","refusal");
        assertEquals(ProviderException.Code.SAFETY_BLOCK,assertThrows(ProviderException.class,
            ()->gateway((m,k,b,t)->new JsonTransport.Response(200,body)).decide(GeminiGatewayTest.context())).getCode());
    }
    @Test void errorsAreSanitizedAndQuotaIsDistinctFromRateLimit() {
        for(int status:new int[]{400,401,403,429,500,302}) {
            ProviderException error=assertThrows(ProviderException.class,()->gateway((m,k,b,t)->new JsonTransport.Response(status,"test-key private details")).decide(GeminiGatewayTest.context()));
            assertFalse(error.toString().contains("test-key")); assertNull(error.getCause());
            assertEquals(status==429?ProviderException.Code.RATE_LIMIT:status==401||status==403?ProviderException.Code.AUTHENTICATION:
                status==400?ProviderException.Code.REQUEST_REJECTED:ProviderException.Code.UNAVAILABLE,error.getCode());
        }
        assertEquals(ProviderException.Code.QUOTA_EXHAUSTED,assertThrows(ProviderException.class,()->gateway((m,k,b,t)->
            new JsonTransport.Response(429,"{\"error\":{\"code\":\"insufficient_quota\"}}" )).decide(GeminiGatewayTest.context())).getCode());
        assertEquals(ProviderException.Code.TIMEOUT,assertThrows(ProviderException.class,()->gateway((m,k,b,t)->{throw new SocketTimeoutException("test-key");}).decide(GeminiGatewayTest.context())).getCode());
    }
    @Test void structuredInterpretationUsesTrustedQuestAndKnownConstraints() {
        TaskIntent intent=gateway((m,k,body,t)->{
            Map<String,Object> request=Json.asObject(Json.read(body));
            Map<String,Object> format=Json.asObject(Json.asObject(request.get("text")).get("format"));
            assertEquals("json_schema",format.get("type")); assertEquals(true,format.get("strict"));
            assertFalse(request.containsKey("tools")); assertFalse(body.contains("test-key"));
            return new JsonTransport.Response(200,task("quest-1","NO_SPENDING","NO_ACTIVE_BATTLE"));
        }).parse("完成任务，不要花金币，也不要主动战斗","quest-1");
        assertTrue(intent.has(TaskIntent.Constraint.NO_SPENDING)); assertTrue(intent.has(TaskIntent.Constraint.NO_ACTIVE_BATTLE));
    }
    @Test void taskParserRejectsMismatchedUnknownDuplicateAndUnsupportedClauses() {
        for(String body:Arrays.asList(task("other","NO_SPENDING"),task("quest-1","FREE_FORM_RULE"),
            task("quest-1","NO_SPENDING","NO_SPENDING"),text("I will do it"))) {
            assertEquals(ProviderException.Code.INVALID_RESPONSE,assertThrows(ProviderException.class,
                ()->gateway((m,k,b,t)->new JsonTransport.Response(200,body)).parse("完成任务","quest-1")).getCode());
        }
        String unsupported=task("quest-1").replace("SUPPORTED","UNSUPPORTED");
        assertEquals(ProviderException.Code.UNSUPPORTED_TASK,assertThrows(ProviderException.class,
            ()->gateway((m,k,b,t)->new JsonTransport.Response(200,unsupported)).parse("限制在森林","quest-1")).getCode());
    }
    @Test void configRejectsMissingKeyHeaderInjectionAndBadModel() {
        assertThrows(ProviderException.class,()->OpenAiConfig.fromEnvironment(Collections.emptyMap()));
        assertThrows(IllegalArgumentException.class,()->new OpenAiConfig("key\nheader","gpt-6.1-sol",1000));
        assertThrows(IllegalArgumentException.class,()->new OpenAiConfig("key","model?key=bad",1000));
        assertThrows(IllegalArgumentException.class,()->new OpenAiConfig("key","gpt-6.1-sol",0));
    }
}
