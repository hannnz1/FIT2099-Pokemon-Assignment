package game.agent;

import game.agent.action.ActionResult;
import game.agent.runtime.*;
import game.agent.tools.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AgentTaskTest {
    @Test void cancelRejectsLateResultWithoutChangingWorld() {
        AgentTask task = new AgentTask("t"); task.start();
        AgentTask.Operation op = task.beginOperation(10, 100);
        task.cancel();
        AtomicInteger coins = new AtomicInteger(10);
        assertEquals("STALE_OPERATION", task.commit(op, 20, () -> { coins.decrementAndGet(); return ActionResult.success("OK"); }).getCode());
        assertEquals(10, coins.get()); assertEquals(AgentTask.State.CANCELLED, task.getState());
        assertThrows(IllegalStateException.class, task::resume);
    }
    @Test void pauseResumeInvalidatesOldOperationAndCommitsNewOneOnlyOnce() {
        AgentTask task = new AgentTask("t"); task.start();
        AgentTask.Operation old = task.beginOperation(0, 100);
        task.pause(); task.resume();
        AgentTask.Operation fresh = task.beginOperation(1, 100);
        AtomicInteger moves = new AtomicInteger();
        assertEquals("STALE_OPERATION", task.commit(old, 2, () -> { moves.incrementAndGet(); return ActionResult.success("OK"); }).getCode());
        assertEquals("OK", task.commit(fresh, 2, () -> { moves.incrementAndGet(); return ActionResult.success("OK"); }).getCode());
        task.commit(fresh, 3, () -> { moves.incrementAndGet(); return ActionResult.success("OK"); });
        assertEquals(1, moves.get());
        assertEquals(AgentTask.State.RUNNING, task.getState()); // tool success is not quest completion
    }
    @Test void timeoutAtBoundaryDoesNotExecuteAndCanResume() {
        AgentTask task = new AgentTask("t"); task.start();
        AgentTask.Operation op = task.beginOperation(10, 5);
        assertEquals("OPERATION_TIMEOUT", task.commit(op, 15, () -> { fail("must not execute"); return null; }).getCode());
        assertEquals(AgentTask.State.PROVIDER_UNAVAILABLE, task.getState());
        task.resume(); assertEquals(AgentTask.State.RUNNING, task.getState());
    }
    @Test void approvalAndFailuresHaveExplicitTransitions() {
        AgentTask task = new AgentTask("t"); task.start();
        task.waitForApproval();
        assertThrows(IllegalStateException.class, () -> task.beginOperation(0, 10));
        task.resolveApproval(false);
        assertEquals(AgentTask.State.REPLANNING, task.getState());
        AgentTask.Operation op = task.beginOperation(0, 10);
        task.commit(op, 1, () -> ActionResult.rejected("RESOURCE_NOT_AVAILABLE"));
        assertEquals(AgentTask.State.REPLANNING, task.getState());
        task.markCompleted();
        assertThrows(IllegalStateException.class, task::pause);
    }
    @Test void asynchronousDecisionCannotCommitAfterCancel() {
        Queue<Runnable> modelJobs = new ArrayDeque<>();
        Queue<Runnable> worldJobs = new ArrayDeque<>();
        AgentRunner runner = new AgentRunner(modelJobs::add, worldJobs::add, () -> 1L);
        AgentTask task = new AgentTask("t"); task.start();
        AtomicInteger moves = new AtomicInteger();
        GameToolRegistry registry = new GameToolRegistry(r -> null);
        registry.register(new ToolDefinition("move", "Move", true, Collections.emptyMap()), r -> { moves.incrementAndGet(); return ActionResult.success("MOVED"); });
        CompletableFuture<ActionResult> result = runner.step(task, () -> new ToolRequest("a", "move", Collections.emptyMap()), registry, 100);
        assertFalse(result.isDone());
        modelJobs.remove().run(); task.cancel(); worldJobs.remove().run();
        assertEquals("STALE_OPERATION", result.join().getCode()); assertEquals(0, moves.get());
    }
    @Test void providerFailureBecomesRecoverableState() {
        AgentTask task = new AgentTask("t"); task.start();
        AgentRunner runner = new AgentRunner(Runnable::run, Runnable::run, () -> 1L);
        ActionResult result = runner.step(task, () -> { throw new IllegalStateException("private provider details"); }, new GameToolRegistry(r -> null), 100).join();
        assertEquals("PROVIDER_UNAVAILABLE", result.getCode());
        assertEquals(AgentTask.State.PROVIDER_UNAVAILABLE, task.getState());
    }
    @Test void toolRequestingApprovalDoesNotGetOverwrittenByCommit() {
        AgentTask task = new AgentTask("t"); task.start();
        AgentTask.Operation operation = task.beginOperation(0,100);
        task.commit(operation,1,()-> { task.waitForApproval(); return ActionResult.success("APPROVAL_REQUESTED"); });
        assertEquals(AgentTask.State.WAITING_APPROVAL,task.getState());
    }
    @Test void rejectedWorldExecutorReleasesPendingOperation() {
        AgentTask task = new AgentTask("t"); task.start();
        AgentRunner runner = new AgentRunner(Runnable::run, job -> { throw new RejectedExecutionException(); }, ()->1L);
        ActionResult result = runner.step(task, ()->new ToolRequest("x","observe",Collections.emptyMap()),new GameToolRegistry(r->null),100).join();
        assertEquals("PROVIDER_UNAVAILABLE",result.getCode());
        assertEquals(AgentTask.State.PROVIDER_UNAVAILABLE,task.getState());
    }
    @Test void schedulerCanExpireHungProviderAndRetry() {
        AgentTask task = new AgentTask("t"); task.start();
        task.beginOperation(10,5);
        assertFalse(task.expire(14)); assertTrue(task.expire(15));
        task.resume(); assertNotNull(task.beginOperation(16,10));
    }
}
