package game.agent.runtime;

import game.agent.action.ActionResult;
import game.agent.tools.*;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.*;

/** Model work runs separately; all result validation and tools run on the world executor. */
public final class AgentRunner {
    private final Executor modelExecutor;
    private final Executor worldExecutor;
    private final LongSupplier clock;
    public AgentRunner(Executor modelExecutor, Executor worldExecutor, LongSupplier clock) {
        this.modelExecutor = Objects.requireNonNull(modelExecutor);
        this.worldExecutor = Objects.requireNonNull(worldExecutor);
        this.clock = Objects.requireNonNull(clock);
    }
    /** Invoke on the world thread. The supplier must use an immutable observation snapshot. */
    public CompletableFuture<ActionResult> step(AgentTask task, Supplier<ToolRequest> decision, GameToolRegistry tools, long timeoutMillis) {
        Objects.requireNonNull(task); Objects.requireNonNull(decision); Objects.requireNonNull(tools);
        AgentTask.Operation operation = task.beginOperation(clock.getAsLong(), timeoutMillis);
        try {
            return CompletableFuture.supplyAsync(decision, modelExecutor).handleAsync((request, error) -> {
                if (error != null || request == null) return task.providerFailed(operation);
                return task.commit(operation, clock.getAsLong(), () -> tools.execute(request));
            }, worldExecutor).exceptionally(error -> task.providerFailed(operation));
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.completedFuture(task.providerFailed(operation));
        }
    }
}
