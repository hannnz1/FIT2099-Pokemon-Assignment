package game.agent;

import game.agent.llm.*;
import game.agent.runtime.*;
import game.agent.tools.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class GeminiEmptyArgsTest {
    @Test void zeroParameterFunctionMayOmitArgsButStillUsesWhitelist() {
        AgentTask task=new AgentTask("t"); task.start(); GameToolRegistry tools=new GameToolRegistry(r->null);
        tools.register(new ToolDefinition("observe","look",false,Collections.emptyMap()),r->null);
        AtomicReference<AgentLoop.Context> context=new AtomicReference<>();
        new AgentLoop(task,"goal",tools,Runnable::run,c->{context.set(c);return null;},Collections::emptyMap,()->false,100,5,5,3).tick(0);
        GeminiGateway gateway=GeminiGatewayTest.gateway((m,k,b,t)->new GeminiTransport.Response(200,
            GeminiGatewayTest.response("{\"functionCall\":{\"name\":\"observe\"}}")));
        assertTrue(gateway.decide(context.get()).getArguments().isEmpty());
    }
}
