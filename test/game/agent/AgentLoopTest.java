package game.agent;

import game.agent.action.ActionResult;
import game.agent.runtime.*;
import game.agent.tools.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AgentLoopTest {
    private GameToolRegistry tools(AtomicInteger steps) {
        GameToolRegistry tools = new GameToolRegistry(r -> null);
        tools.register(new ToolDefinition("move_to","test movement",true,Collections.emptyMap()),r ->
            steps.incrementAndGet() < 3 ? ActionResult.of(ActionResult.Status.IN_PROGRESS,"MOVED",Collections.emptyMap()) : ActionResult.success("ARRIVED"));
        return tools;
    }
    private AgentLoop loop(AgentTask task, GameToolRegistry tools, Executor executor, AtomicInteger decisions) {
        task.start();
        return new AgentLoop(task,"reach destination",tools,executor,context -> {
            decisions.incrementAndGet();
            return new ToolRequest("model-request","move_to",Collections.emptyMap());
        },Collections::emptyMap,()->false,100,5,10,3);
    }
    @Test void continuationMovesOncePerTickWithoutMoreDecisions() {
        AtomicInteger steps=new AtomicInteger(), decisions=new AtomicInteger(); AgentTask task=new AgentTask("t");
        AgentLoop loop=loop(task,tools(steps),Runnable::run,decisions);
        loop.tick(0); assertEquals(0,steps.get());
        loop.tick(1); assertEquals(1,steps.get());
        loop.tick(2); assertEquals(2,steps.get());
        loop.tick(3); assertEquals(3,steps.get()); assertEquals(1,decisions.get());
        assertEquals(AgentTask.State.RUNNING,task.getState());
        assertEquals(3,loop.getTrace().size());
    }
    @Test void pauseInterruptsContinuationAndResumeReplans() {
        AtomicInteger steps=new AtomicInteger(), decisions=new AtomicInteger(); AgentTask task=new AgentTask("t");
        AgentLoop loop=loop(task,tools(steps),Runnable::run,decisions);
        loop.tick(0); loop.tick(1); loop.pause(); loop.tick(2); assertEquals(1,steps.get());
        loop.resume(); loop.tick(3); loop.tick(4); assertEquals(2,decisions.get()); assertEquals(2,steps.get());
    }
    @Test void cancelledAndExpiredProviderCannotMutateWorld() {
        List<Runnable> queue=new ArrayList<>(); AtomicInteger steps=new AtomicInteger(),decisions=new AtomicInteger();
        AgentTask task=new AgentTask("t"); AgentLoop loop=loop(task,tools(steps),queue::add,decisions);
        loop.tick(0); loop.cancel(); queue.remove(0).run(); loop.tick(1);
        assertEquals(0,steps.get()); assertEquals(AgentTask.State.CANCELLED,task.getState());
        AgentTask timeout=new AgentTask("timeout"); AgentLoop second=loop(timeout,tools(steps),queue::add,decisions);
        second.tick(0); second.tick(100); queue.remove(0).run(); second.tick(101);
        assertEquals(0,steps.get()); assertEquals(AgentTask.State.PROVIDER_UNAVAILABLE,timeout.getState());
    }
    @Test void authoritativeCompletionPreventsFurtherTools() {
        AgentTask task=new AgentTask("t"); task.start(); AtomicInteger steps=new AtomicInteger();
        AgentLoop loop=new AgentLoop(task,"goal",tools(steps),Runnable::run,c->null,Collections::emptyMap,()->true,100,5,10,3);
        loop.tick(0); assertEquals(AgentTask.State.COMPLETED,task.getState()); assertEquals(0,steps.get());
    }
    @Test void brokenProviderAndDecisionBudgetStopLoops() {
        AgentTask task=new AgentTask("t"); task.start(); AtomicInteger steps=new AtomicInteger();
        AgentLoop loop=new AgentLoop(task,"goal",tools(steps),Runnable::run,c->null,Collections::emptyMap,()->false,100,1,10,3);
        loop.tick(0); loop.tick(1); assertEquals(AgentTask.State.PROVIDER_UNAVAILABLE,task.getState());
        AgentTask bounded=new AgentTask("b"); AgentLoop b=loop(bounded,tools(new AtomicInteger()),Runnable::run,new AtomicInteger());
        for(int i=0;i<40;i++) b.tick(i);
        assertEquals(AgentTask.State.FAILED,bounded.getState()); assertTrue(b.getTrace().size()<=64);
    }
    @Test void externalPauseResumeInvalidatesRetainedAction() {
        AgentTask task=new AgentTask("t"); AtomicInteger steps=new AtomicInteger(),decisions=new AtomicInteger();
        AgentLoop loop=loop(task,tools(steps),Runnable::run,decisions);
        loop.tick(0); loop.tick(1); task.pause(); task.resume();
        loop.tick(2); assertEquals(1,steps.get()); assertEquals(2,decisions.get());
        loop.tick(3); assertEquals(2,steps.get());
    }
    @Test void longActionsCannotRunBeyondStepBudget() {
        AtomicInteger steps=new AtomicInteger(); AgentTask task=new AgentTask("t"); task.start();
        GameToolRegistry tools=new GameToolRegistry(r->null);
        tools.register(new ToolDefinition("endless","test",true,Collections.emptyMap()),r->{
            steps.incrementAndGet(); return ActionResult.of(ActionResult.Status.IN_PROGRESS,"MOVED",Collections.emptyMap());
        });
        AgentLoop loop=new AgentLoop(task,"goal",tools,Runnable::run,c->new ToolRequest("choice","endless",Collections.emptyMap()),
            Collections::emptyMap,()->false,100,1,2,3);
        loop.tick(0); loop.tick(1); loop.tick(2);
        assertEquals("ACTION_STEP_LIMIT",loop.tick(3).getCode()); assertEquals(2,steps.get());
        loop.tick(4); assertEquals(AgentTask.State.FAILED,task.getState()); assertEquals(2,steps.get());
    }
    @Test void executorRejectionAndRepeatedInvalidDecisionsStopSafely() {
        AgentTask task=new AgentTask("reject"); AtomicInteger steps=new AtomicInteger();
        AgentLoop rejected=loop(task,tools(steps),r->{throw new RejectedExecutionException();},new AtomicInteger());
        assertEquals("PROVIDER_UNAVAILABLE",rejected.tick(0).getCode()); assertEquals(0,steps.get());
        AgentTask invalid=new AgentTask("invalid"); invalid.start(); AtomicInteger calls=new AtomicInteger();
        AgentLoop loop=new AgentLoop(invalid,"goal",tools(steps),Runnable::run,c->{
            calls.incrementAndGet(); return new ToolRequest("choice","not_allowed",Collections.emptyMap());
        },Collections::emptyMap,()->false,100,20,10,3);
        for(int i=0;i<10;i++) loop.tick(i);
        assertEquals(3,calls.get()); assertEquals(0,steps.get()); assertEquals(AgentTask.State.FAILED,invalid.getState());
    }
}
