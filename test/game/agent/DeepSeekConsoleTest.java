package game.agent;
import game.agent.demo.DeepSeekAgentConsole;
import game.agent.llm.*;
import java.util.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DeepSeekConsoleTest {
    @Test void missingDedicatedKeyStopsBeforeAnyRequest() throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        assertEquals(2,DeepSeekAgentConsole.run(Collections.singletonMap("OPENAI_API_KEY","other-key"),
            new ByteArrayInputStream(new byte[0]),new PrintStream(output,true,"UTF-8"),(m,k,b,t)->{fail("Unexpected request");return null;}));
        assertFalse(output.toString("UTF-8").contains("other-key"));
    }
    @Test void deepSeekWireDrivesRealBerryQuestAndDeliveryWithoutSpending() throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        Queue<String> actions=new ArrayDeque<>(Arrays.asList(
            DeepSeekGatewayTest.action("move_to","{\"locationId\":\"orchard\"}"),
            DeepSeekGatewayTest.action("pickup","{\"itemId\":\"BERRY\",\"quantity\":2}"),
            DeepSeekGatewayTest.action("move_to","{\"locationId\":\"alternative\"}"),
            DeepSeekGatewayTest.action("pickup","{\"itemId\":\"BERRY\",\"quantity\":1}"),
            DeepSeekGatewayTest.action("move_to","{\"locationId\":\"laboratory\"}")));
        String[] quest=new String[1];
        int result=DeepSeekAgentConsole.run(Collections.singletonMap("DEEPSEEK_API_KEY","test-key"),
            new ByteArrayInputStream("完成树果任务，不要花金币\ny\n".getBytes("UTF-8")),new PrintStream(output,true,"UTF-8"),(m,k,body,t)->{
                Map<String,Object> request=Json.asObject(Json.read(body));
                if(request.containsKey("response_format")) {
                    Map<String,Object> user=Json.asObject(Json.asArray(request.get("messages")).get(1));
                    quest[0]=(String)Json.asObject(Json.read((String)user.get("content"))).get("trustedQuestId");
                    return new JsonTransport.Response(200,DeepSeekGatewayTest.reply("stop",Json.object("content",Json.write(Json.object(
                        "status","SUPPORTED","goal","COMPLETE_QUEST","questId",quest[0],"constraints",Arrays.asList("NO_SPENDING"),"unsupportedClauses",Collections.emptyList())))));
                }
                return new JsonTransport.Response(200,actions.isEmpty()?DeepSeekGatewayTest.action("deliver",Json.write(Json.object("questId",quest[0],"targetNpcId","professor"))):actions.remove());
            });
        assertEquals(0,result,output.toString("UTF-8"));assertTrue(actions.isEmpty());
        assertTrue(output.toString("UTF-8").contains("实际交付 3 个树果；剩余金币 5"));
        assertFalse(output.toString("UTF-8").contains("test-key"));
    }
}
