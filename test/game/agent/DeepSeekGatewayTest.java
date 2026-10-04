package game.agent;

import game.agent.llm.*;
import game.agent.tools.ToolRequest;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeepSeekGatewayTest {
    static OpenAiGateway gateway(JsonTransport wire) {
        return new OpenAiGateway(new OpenAiConfig("test-key","deepseek-flash",1000),new DeepSeekTransport(wire));
    }
    static String reply(String finish,Map<String,Object> message) {
        return Json.write(Json.object("choices",Collections.singletonList(Json.object("finish_reason",finish,"message",message)),
            "usage",Json.object("prompt_tokens",100,"completion_tokens",20,"total_tokens",120,"prompt_cache_hit_tokens",30)));
    }
    static String action(String name,String args) {
        return reply("tool_calls",Json.object("role","assistant","content",null,"tool_calls",Collections.singletonList(
            Json.object("type","function","id","remote-id","function",Json.object("name",name,"arguments",args)))));
    }
    @Test void mapsTrustedToolsAndUsageWithoutSendingResponsesFields() {
        OpenAiGateway gateway=gateway((model,key,body,timeout)->{
            Map<String,Object> request=Json.asObject(Json.read(body));
            assertEquals("deepseek-flash",model);assertEquals("test-key",key);
            assertEquals("disabled",Json.asObject(request.get("thinking")).get("type"));
            assertEquals("required",request.get("tool_choice"));assertEquals(false,request.get("parallel_tool_calls"));
            assertFalse(request.containsKey("input"));assertFalse(request.containsKey("store"));assertFalse(body.contains("test-key"));
            Map<String,Object> function=Json.asObject(Json.asObject(Json.asArray(request.get("tools")).get(0)).get("function"));
            assertEquals("pickup",function.get("name"));assertFalse(function.containsKey("strict"));
            assertTrue(body.contains("visibleBerry"));
            return new JsonTransport.Response(200,action("pickup","{\"itemId\":\"BERRY\",\"quantity\":2}"));
        });
        ToolRequest call=gateway.decide(GeminiGatewayTest.context());assertEquals("pickup",call.getName());
        assertNotEquals("remote-id",call.getActionId());assertEquals(120L,gateway.getUsage().get("totalTokens"));
        assertEquals(30L,gateway.getUsage().get("cachedInputTokens"));
    }
    @Test void jsonTaskInterpretationStillValidatesTrustedQuestAndConstraints() {
        TaskIntent task=gateway((m,k,body,t)->{
            Map<String,Object> request=Json.asObject(Json.read(body));
            assertEquals("json_object",Json.asObject(request.get("response_format")).get("type"));
            assertFalse(request.containsKey("tools"));assertTrue(body.contains("JSON"));
            return new JsonTransport.Response(200,reply("stop",Json.object("content",Json.write(Json.object(
                "status","SUPPORTED","goal","COMPLETE_QUEST","questId","quest-1","constraints",Arrays.asList("NO_SPENDING"),
                "unsupportedClauses",Collections.emptyList())))));
        }).parse("收集树果不要花钱","quest-1");
        assertTrue(task.has(TaskIntent.Constraint.NO_SPENDING));
        String wrong=reply("stop",Json.object("content",Json.write(Json.object("status","SUPPORTED","goal","COMPLETE_QUEST",
            "questId","other","constraints",Collections.emptyList(),"unsupportedClauses",Collections.emptyList()))));
        assertEquals(ProviderException.Code.INVALID_RESPONSE,assertThrows(ProviderException.class,
            ()->gateway((m,k,b,t)->new JsonTransport.Response(200,wrong)).parse("任务","quest-1")).getCode());
    }
    @Test void rejectsUnknownToolsInvalidArgumentsTruncationAndMultipleChoices() {
        String valid=action("pickup","{\"itemId\":\"BERRY\",\"quantity\":2}");
        Map<String,Object> multiple=Json.asObject(Json.read(valid));List<Object> choices=Json.asArray(multiple.get("choices"));choices.add(choices.get(0));
        Map<String,Object> multiCalls=Json.asObject(Json.read(valid));List<Object> calls=Json.asArray(Json.asObject(Json.asObject(Json.asArray(multiCalls.get("choices")).get(0)).get("message")).get("tool_calls"));calls.add(calls.get(0));
        for(String body:Arrays.asList(action("shell","{}"),action("pickup","{\"quantity\":0}"),valid.replace("tool_calls\",\"message","length\",\"message"),
            Json.write(multiple),Json.write(multiCalls),reply("stop",Json.object("content","done")),"not json")) {
            assertEquals(ProviderException.Code.INVALID_RESPONSE,assertThrows(ProviderException.class,
                ()->gateway((m,k,b,t)->new JsonTransport.Response(200,body)).decide(GeminiGatewayTest.context())).getCode());
        }
    }
    @Test void balanceAndAuthenticationErrorsNeverLeakBodiesOrKeys() {
        for(int status:new int[]{402,401,403,429,500}) {
            ProviderException error=assertThrows(ProviderException.class,()->gateway((m,k,b,t)->new JsonTransport.Response(status,"test-key secret")).decide(GeminiGatewayTest.context()));
            assertEquals(status==402?ProviderException.Code.QUOTA_EXHAUSTED:status==429?ProviderException.Code.RATE_LIMIT:
                status==500?ProviderException.Code.UNAVAILABLE:ProviderException.Code.AUTHENTICATION,error.getCode());
            assertFalse(error.toString().contains("test-key"));
        }
    }
    @Test void arenaCaptureCompletesOnlyAfterPlayerConfirmation() {
        OpenAiGateway gateway=gateway((m,k,b,t)->{
            Map<String,Object> request=Json.asObject(Json.read(b));
            if(request.containsKey("response_format"))return new JsonTransport.Response(200,reply("stop",Json.object("content",
                Json.write(Json.object("goal","CAPTURE","targetId","wild-treecko","noBattle",true)))));
            Map<String,Object> user=Json.asObject(Json.asArray(request.get("messages")).get(1));
            Map<String,Object> observation=Json.asObject(Json.asObject(Json.read((String)user.get("content"))).get("observation"));
            return new JsonTransport.Response(200,"0".equals(observation.get("x"))?
                action("move_to","{\"locationId\":\"treecko-approach\"}"):action("capture","{\"targetId\":\"wild-treecko\"}"));
        });
        game.agent.web.BattleRoom room=new game.agent.web.BattleRoom("owner",new game.agent.web.ProviderSelection(
            "deepseek","deepseek-flash",1000,gateway,gateway),Runnable::run,null);
        BattleRoomTest.accepted(room,"PARSE",Json.object("text","捕捉木守宫不要战斗"));room.tick(1);
        assertEquals("READY",room.snapshot().get("status"));assertEquals("0",Json.asObject(room.snapshot().get("world")).get("turn"));
        BattleRoomTest.accepted(room,"CONFIRM",Collections.emptyMap());for(int i=2;i<30;i++)room.tick(i);
        assertEquals("COMPLETED",room.snapshot().get("status"));assertEquals(Arrays.asList("wild-treecko"),room.snapshot().get("captured"));
        assertFalse(Json.write(room.snapshot()).contains("test-key"));
    }
    @Test void realGrowthCombatCanLevelUpThroughDeepSeekWire() throws Exception {
        game.agent.runtime.AgentLoop.DecisionProvider baseline=game.agent.eval.GrowthBatchEvaluation.scripted();
        java.util.concurrent.atomic.AtomicReference<game.agent.runtime.AgentLoop.Context> current=new java.util.concurrent.atomic.AtomicReference<>();
        OpenAiGateway gateway=gateway((m,k,b,t)->{
            Map<String,Object> user=Json.asObject(Json.asArray(Json.asObject(Json.read(b)).get("messages")).get(1));
            assertEquals(current.get().getGoal(),Json.asObject(Json.read((String)user.get("content"))).get("goal"));
            ToolRequest call=baseline.decide(current.get());return new JsonTransport.Response(200,action(call.getName(),Json.write(call.getArguments())));
        });
        Map<String,Object> run=game.agent.eval.GrowthBatchEvaluation.run(11,"MUDKIP",c->{current.set(c);return gateway.decide(c);},
            "scripted-deepseek-wire","deepseek-flash",1000,10000);
        assertEquals("COMPLETED",run.get("terminal"));assertTrue(Boolean.TRUE.equals(run.get("completed")));
    }
}
