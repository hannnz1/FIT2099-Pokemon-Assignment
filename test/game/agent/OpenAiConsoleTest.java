package game.agent;

import game.agent.demo.*;
import game.agent.llm.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiConsoleTest {
    @Test void missingKeyStopsBeforeAnyRequest() throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        assertEquals(2,OpenAiAgentConsole.run(Collections.emptyMap(),new ByteArrayInputStream(new byte[0]),new PrintStream(output,true,"UTF-8"),
            (m,k,b,t)->{fail("Unexpected API request");return null;}));
        assertTrue(output.toString("UTF-8").contains("OPENAI_API_KEY"));
    }
    @Test void playerCanDeclineParsedInterpretationBeforeDecisionRequest() throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        int result=OpenAiAgentConsole.run(Collections.singletonMap("OPENAI_API_KEY","test-key"),
            new ByteArrayInputStream("完成任务，不要花金币\nn\n".getBytes("UTF-8")),new PrintStream(output,true,"UTF-8"),(m,k,body,t)->{
                Map<String,Object> request=Json.asObject(Json.read(body));assertTrue(request.containsKey("text"));
                String quest=(String)Json.asObject(Json.read((String)request.get("input"))).get("trustedQuestId");
                return new JsonTransport.Response(200,OpenAiGatewayTest.task(quest,"NO_SPENDING"));
            });
        assertEquals(0,result);assertTrue(output.toString("UTF-8").contains("未启动任务"));assertFalse(output.toString("UTF-8").contains("test-key"));
    }
    @Test void offlineOpenAiWireDrivesActualQuestToCompletionWithoutSpending() throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();Queue<String> calls=new ArrayDeque<>(Arrays.asList(
            OpenAiGatewayTest.function("move_to","{\"locationId\":\"orchard\"}"),
            OpenAiGatewayTest.function("pickup","{\"itemId\":\"BERRY\",\"quantity\":2}"),
            OpenAiGatewayTest.function("move_to","{\"locationId\":\"alternative\"}"),
            OpenAiGatewayTest.function("pickup","{\"itemId\":\"BERRY\",\"quantity\":1}")));
        String[] quest=new String[1];
        int result=OpenAiAgentConsole.run(Collections.singletonMap("OPENAI_API_KEY","test-key"),
            new ByteArrayInputStream("完成树果任务，不要花金币\ny\n".getBytes("UTF-8")),new PrintStream(output,true,"UTF-8"),(m,k,body,t)->{
                Map<String,Object> request=Json.asObject(Json.read(body));
                if(request.containsKey("text")) {
                    quest[0]=(String)Json.asObject(Json.read((String)request.get("input"))).get("trustedQuestId");
                    return new JsonTransport.Response(200,OpenAiGatewayTest.task(quest[0],"NO_SPENDING"));
                }
                String response=calls.isEmpty()?OpenAiGatewayTest.function("deliver",Json.write(Json.object("questId",quest[0],"targetNpcId","professor"))):calls.remove();
                return new JsonTransport.Response(200,response);
            });
        assertEquals(0,result);assertTrue(calls.isEmpty());assertTrue(output.toString("UTF-8").contains("实际交付 3 个树果；剩余金币 5"));
        assertFalse(output.toString("UTF-8").contains("test-key"));
    }
    @Test void quotaFailureDoesNotBecomeFakeSuccess() throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        int result=OpenAiAgentConsole.run(Collections.singletonMap("OPENAI_API_KEY","test-key"),
            new ByteArrayInputStream("完成任务\ny\n".getBytes("UTF-8")),new PrintStream(output,true,"UTF-8"),(m,k,body,t)->{
                Map<String,Object> request=Json.asObject(Json.read(body));
                if(request.containsKey("text"))return new JsonTransport.Response(200,OpenAiGatewayTest.task(
                    (String)Json.asObject(Json.read((String)request.get("input"))).get("trustedQuestId")));
                return new JsonTransport.Response(429,"{\"error\":{\"code\":\"insufficient_quota\"}}");
            });
        assertEquals(4,result);assertTrue(output.toString("UTF-8").contains("PROVIDER_QUOTA_EXHAUSTED"));
        assertFalse(output.toString("UTF-8").contains("任务完成"));
        assertTrue(output.toString("UTF-8").contains("世界回合 0"));
    }
    @Test void confirmationShowsStructuredAreaAndDeadlineRatherThanOldUnsupportedNotice() throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        int result=OpenAiAgentConsole.run(Collections.singletonMap("OPENAI_API_KEY","test-key"),new ByteArrayInputStream("完成树果任务，只在任务区域，第40回合前\nn\n".getBytes("UTF-8")),new PrintStream(output,true,"UTF-8"),(m,k,body,t)->{
            String quest=(String)Json.asObject(Json.read((String)Json.asObject(Json.read(body)).get("input"))).get("trustedQuestId");
            return new JsonTransport.Response(200,OpenAiGatewayTest.text(Json.write(Json.object("status","SUPPORTED","goal","COMPLETE_QUEST","questId",quest,"constraints",Arrays.asList("AREA_RESTRICTED","DEADLINE"),"areaId","QUEST_AREA","deadlineTurn",40,"unsupportedClauses",Collections.emptyList()))));
        });
        assertEquals(0,result);assertTrue(output.toString("UTF-8").contains("QUEST_AREA"));assertTrue(output.toString("UTF-8").contains("40"));
        assertFalse(output.toString("UTF-8").contains("不支持自定义区域或截止时间"));
    }
    @Test void liveConsoleUsesSameOriginalWorldAsV1Browser() throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();String[] position={null,null};
        OpenAiAgentConsole.run(Collections.singletonMap("OPENAI_API_KEY","test-key"),new ByteArrayInputStream("完成树果任务\ny\n".getBytes("UTF-8")),new PrintStream(output,true,"UTF-8"),(m,k,body,t)->{
            Map<String,Object> request=Json.asObject(Json.read(body));Map<String,Object> input=Json.asObject(Json.read((String)request.get("input")));
            if(request.containsKey("text"))return new JsonTransport.Response(200,OpenAiGatewayTest.task((String)input.get("trustedQuestId")));
            Map<String,Object> observation=Json.asObject(input.get("observation"));position[0]=(String)observation.get("x");position[1]=(String)observation.get("y");return new JsonTransport.Response(429,"{}");
        });assertEquals("29",position[0]);assertEquals("10",position[1]);
    }
    @Test void npcClueRemainsInProviderObservationAfterAnotherTool() throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();int[] steps={0};String[] clue={null};
        OpenAiAgentConsole.run(Collections.singletonMap("OPENAI_API_KEY","test-key"),new ByteArrayInputStream("完成树果任务\ny\n".getBytes("UTF-8")),new PrintStream(output,true,"UTF-8"),(m,k,body,t)->{
            Map<String,Object> request=Json.asObject(Json.read(body));Map<String,Object> input=Json.asObject(Json.read((String)request.get("input")));
            if(request.containsKey("text"))return new JsonTransport.Response(200,OpenAiGatewayTest.task((String)input.get("trustedQuestId")));
            if(steps[0]++==0)return new JsonTransport.Response(200,OpenAiGatewayTest.function("talk_to","{\"targetNpcId\":\"treecko\",\"message\":\"你在哪里看见过树果？\"}"));
            Map<String,Object> observation=Json.asObject(input.get("observation"));String history=(String)observation.get("npcDialogueHistory");
            assertNotNull(history);assertTrue(history.contains("treecko"));
            if(steps[0]==2){clue[0]=history;return new JsonTransport.Response(200,OpenAiGatewayTest.function("observe","{}"));}
            assertEquals(clue[0],history);return new JsonTransport.Response(429,"{}");
        });assertEquals(3,steps[0]);
    }
}
