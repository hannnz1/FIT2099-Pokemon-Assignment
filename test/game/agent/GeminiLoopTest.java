package game.agent;

import game.agent.llm.*;
import game.agent.runtime.*;
import game.agent.tools.*;
import game.agent.action.ActionResult;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GeminiLoopTest {
    @Test void quotaFailureStopsTaskAndDoesNotChangeGame() {
        BerryQuestTest.Fixture f=new BerryQuestTest.Fixture(); f.ground(1);
        GeminiGateway gateway=GeminiGatewayTest.gateway((m,k,b,t)->new GeminiTransport.Response(429,"test-key"));
        AgentLoop loop=new AgentLoop(f.task,"collect",f.tools,Runnable::run,gateway,f.session::observation,()->false,100,5,10,3);
        loop.tick(0); assertEquals("PROVIDER_RATE_LIMIT",loop.tick(1).getCode());
        assertEquals(AgentTask.State.PROVIDER_UNAVAILABLE,f.task.getState()); assertTrue(f.actor.getInventory().isEmpty());
        assertEquals(1,f.map.locationOf(f.actor).getItems().size()); assertEquals(5,f.session.getBalance());
    }
    @Test void validatedGeminiWireCallsExecuteThroughRealRulesOnlyOnWorldTick() {
        BerryQuestTest.Fixture f=new BerryQuestTest.Fixture(); f.ground(1);
        GeminiGateway gateway=GeminiGatewayTest.gateway((m,k,b,t)->new GeminiTransport.Response(200,
            GeminiGatewayTest.function("pickup","{\"itemId\":\"BERRY\",\"quantity\":1}")));
        AgentLoop loop=new AgentLoop(f.task,"collect",f.tools,Runnable::run,gateway,f.session::observation,()->false,100,5,10,3);
        loop.tick(0); assertTrue(f.actor.getInventory().isEmpty());
        assertEquals("PICKED_UP",loop.tick(1).getCode()); assertEquals(1,f.actor.getInventory().size());
        assertEquals(AgentTask.State.RUNNING,f.task.getState());
    }
}
