package game.agent.runtime;

import game.agent.action.ActionResult;
import game.agent.llm.ProviderException;
import java.util.*;
import java.util.function.Supplier;

/**
 * Java adaptation of AI Town's in-progress operation token and stale-result guard.
 * Copyright (c) 2023 a16z-infra. MIT; see third_party/ai-town/LICENSE.
 * Changes: explicit task states, generation invalidation and synchronized commits.
 * All external world mutations must still use the same world executor.
 */
public final class AgentTask {
    public enum State { CREATED, RUNNING, REPLANNING, WAITING_APPROVAL, PAUSED, COMPLETED, FAILED, CANCELLED, PROVIDER_UNAVAILABLE }
    public static final class Operation {
        private final long generation;
        private final long deadline;
        private final String id = UUID.randomUUID().toString();
        private Operation(long generation, long deadline) { this.generation = generation; this.deadline = deadline; }
        public String getId() { return id; }
    }
    private final String taskId;
    private State state = State.CREATED;
    private long generation;
    private Operation pending;
    public AgentTask(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) throw new IllegalArgumentException("Missing task ID");
        this.taskId = taskId;
    }
    public String getTaskId() { return taskId; }
    public synchronized State getState() { return state; }
    /** Lifecycle revision lets retained actions detect pause/resume outside the loop. */
    public synchronized long getRevision() { return generation; }
    public synchronized void start() { require(State.CREATED); state = State.RUNNING; }
    public synchronized Operation beginOperation(long now, long timeoutMillis) {
        if (!runnable() || pending != null) throw new IllegalStateException("Task cannot start another operation");
        if (timeoutMillis <= 0) throw new IllegalArgumentException("Timeout must be positive");
        pending = new Operation(generation, Math.addExact(now, timeoutMillis));
        return pending;
    }
    public synchronized ActionResult commit(Operation operation, long now, Supplier<ActionResult> action) {
        if (!current(operation)) return ActionResult.rejected("STALE_OPERATION");
        if (expire(now)) return ActionResult.of(ActionResult.Status.TIMED_OUT, "OPERATION_TIMEOUT", Collections.emptyMap());
        pending = null; // a duplicate completion can no longer execute the supplier
        ActionResult result;
        try { result = Objects.requireNonNull(action.get()); }
        catch (RuntimeException e) { result = ActionResult.failed("ACTION_ERROR"); }
        // A trusted tool may have requested approval or completed the quest.
        // Preserve that explicit transition instead of unconditionally resuming.
        if (generation == operation.generation && runnable()) {
            if (result.getStatus() == ActionResult.Status.REJECTED || result.getStatus() == ActionResult.Status.FAILED) state = State.REPLANNING;
            else state = State.RUNNING;
        }
        return result;
    }
    /** Call on the world scheduler even when a provider future never returns. */
    public synchronized boolean expire(long now) {
        if (pending == null || now < pending.deadline) return false;
        invalidate(); state = State.PROVIDER_UNAVAILABLE; return true;
    }
    public synchronized ActionResult providerFailed(Operation operation) {
        return providerFailed(operation,null);
    }
    /** Provider-neutral, enum-only reason classification; no raw error messages. */
    public synchronized ActionResult providerFailed(Operation operation,ProviderException.Code code) {
        if (!current(operation)) return ActionResult.rejected("STALE_OPERATION");
        invalidate(); state = State.PROVIDER_UNAVAILABLE;
        return ActionResult.failed(code==null?"PROVIDER_UNAVAILABLE":"PROVIDER_"+code.name());
    }
    public synchronized void pause() { requireActive(); invalidate(); state = State.PAUSED; }
    public synchronized void resume() {
        if (state != State.PAUSED && state != State.PROVIDER_UNAVAILABLE) throw new IllegalStateException("Task cannot resume");
        invalidate(); state = State.RUNNING;
    }
    public synchronized void cancel() {
        if (state == State.CANCELLED) return;
        requireActive(); invalidate(); state = State.CANCELLED;
    }
    public synchronized void waitForApproval() {
        if (!runnable()) throw new IllegalStateException("Cannot request approval");
        invalidate(); state = State.WAITING_APPROVAL;
    }
    /** Lifecycle only. An approval-token service must separately authorize a purchase. */
    public synchronized void resolveApproval(boolean approved) {
        require(State.WAITING_APPROVAL); state = approved ? State.RUNNING : State.REPLANNING;
    }
    /** Only an authoritative quest service may call this, never model text. */
    public synchronized void markCompleted() { requireActive(); invalidate(); state = State.COMPLETED; }
    public synchronized void fail() { requireActive(); invalidate(); state = State.FAILED; }
    private boolean runnable() { return state == State.RUNNING || state == State.REPLANNING; }
    private boolean current(Operation op) { return op != null && pending == op && op.generation == generation && runnable(); }
    private void invalidate() { pending = null; generation++; }
    private void require(State expected) { if (state != expected) throw new IllegalStateException("Expected " + expected); }
    private void requireActive() {
        if (state == State.CREATED || state == State.COMPLETED || state == State.CANCELLED || state == State.FAILED) throw new IllegalStateException("Task is not active");
    }
}
