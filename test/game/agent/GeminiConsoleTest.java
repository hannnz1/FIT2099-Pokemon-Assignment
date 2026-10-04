package game.agent;

import game.agent.demo.GeminiAgentConsole;
import game.agent.llm.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GeminiConsoleTest {
    @Test void stalledTaskParserStopsWithoutStartingWorld() throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        Map<String,String> env=new HashMap<>(); env.put("GEMINI_API_KEY","test-key"); env.put("GEMINI_TIMEOUT_MS","1");
        int result=GeminiAgentConsole.run(env,new ByteArrayInputStream("完成任务\ny\n".getBytes("UTF-8")),new PrintStream(output,true,"UTF-8"),(m,k,b,t)->{
            try { Thread.sleep(2000); } catch(InterruptedException error) { Thread.currentThread().interrupt(); }
            throw new ProviderException(ProviderException.Code.UNAVAILABLE);
        });
        assertEquals(3,result); assertTrue(output.toString("UTF-8").contains("PROVIDER_TIMEOUT"));
        assertFalse(output.toString("UTF-8").contains("任务完成"));
    }
    @Test void missingKeyDoesNotSendNetworkRequestOrPretendToRun() throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        int result=GeminiAgentConsole.run(Collections.emptyMap(),new ByteArrayInputStream(new byte[0]),new PrintStream(output,true,"UTF-8"),
            (m,k,b,t)->{fail("Unexpected network request");return null;});
        assertEquals(2,result); assertTrue(output.toString("UTF-8").contains("GEMINI_API_KEY"));
    }
    @Test void interpretedTaskRequiresPlayerConfirmationBeforeRunning() throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream(); AtomicInteger calls=new AtomicInteger();
        int result=GeminiAgentConsole.run(Collections.singletonMap("GEMINI_API_KEY","test-key"),
            new ByteArrayInputStream("帮我完成树果任务，不要花金币\nn\n".getBytes("UTF-8")),new PrintStream(output,true,"UTF-8"),(m,k,body,t)->{
                calls.incrementAndGet(); Map<String,Object> request=Json.asObject(Json.read(body));
                Map<String,Object> content=Json.asObject(Json.asArray(request.get("contents")).get(0));
                String prompt=(String)Json.asObject(Json.asArray(content.get("parts")).get(0)).get("text");
                String quest=(String)Json.asObject(Json.read(prompt)).get("trustedQuestId");
                return NaturalTaskParserTest.taskResponse(quest,"NO_SPENDING");
            });
        assertEquals(0,result); assertEquals(1,calls.get()); assertFalse(output.toString("UTF-8").contains("test-key"));
        assertTrue(output.toString("UTF-8").contains("未启动任务"));
    }
}
