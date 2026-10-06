package game.agent;

import game.agent.demo.GeminiQuestScenario;
import game.agent.llm.*;
import game.agent.runtime.*;
import game.agent.quest.*;
import game.agent.tools.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GeminiScenarioTest {
    @Test void structuredNaturalTaskAndGeminiToolWireCompleteActualQuestOffline() {
        GeminiQuestScenario scene=new GeminiQuestScenario(); Queue<String> replies=new ArrayDeque<>();
        replies.add(GeminiGatewayTest.function("move_to","{\"locationId\":\"orchard\"}"));
        replies.add(GeminiGatewayTest.function("pickup","{\"itemId\":\"BERRY\",\"quantity\":2}"));
        replies.add(GeminiGatewayTest.function("move_to","{\"locationId\":\"alternative\"}"));
        replies.add(GeminiGatewayTest.function("pickup","{\"itemId\":\"BERRY\",\"quantity\":1}"));
        GeminiGateway gateway=GeminiGatewayTest.gateway((m,k,body,t)->body.contains("responseJsonSchema")
            ?NaturalTaskParserTest.taskResponse(scene.getSession().getQuestId(),"NO_SPENDING"):new GeminiTransport.Response(200,replies.remove()));
        TaskIntent intent=new NaturalTaskParser(gateway).parse("帮我完成树果任务，不要花金币",scene.getSession().getQuestId());
        AgentLoop loop=scene.start(intent,gateway,Runnable::run);
        for(int tick=0;tick<100 && loop.getState()!=AgentTask.State.COMPLETED;tick++) {
            loop.tick(tick); scene.getSession().advanceTurn();
        }
        assertEquals(BerryQuestSession.QuestStatus.COMPLETED,scene.getSession().getQuestStatus());
        assertEquals(AgentTask.State.COMPLETED,loop.getState()); assertEquals(5,scene.getSession().getBalance()); assertEquals(3,scene.getDelivered());
        assertTrue(replies.isEmpty()); assertThrows(IllegalStateException.class,()->scene.start(intent,gateway,Runnable::run));
        assertTrue(loop.getStepTrace().stream().anyMatch(row->"ENGINE_DELIVERY_PRIORITY".equals(row.get("executionSource"))&&"deliver".equals(row.get("toolName"))));
    }
}
