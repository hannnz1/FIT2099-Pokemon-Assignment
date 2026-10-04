package game.agent.tools;

import game.agent.action.ActionResult;
import java.util.*;
import java.util.function.Function;

/**
 * Java adaptation of Mindcraft's command registry and parameter checking.
 * Copyright (c) 2024 Kolby Nottingham. MIT; see third_party/mindcraft/LICENSE.
 * Changes: native structured calls, strict schemas, policy checks and bounded
 * in-memory idempotency. One registry per actor/session, used on the world thread.
 * Handlers must validate before mutating; this class does not roll back exceptions.
 */
public final class GameToolRegistry {
    private static final int MAX_RECEIPTS = 10000;
    private final Map<String, ToolDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, Function<ToolRequest, ActionResult>> handlers = new HashMap<>();
    private final Map<String, ToolRequest> requests = new HashMap<>();
    private final Map<String, ActionResult> receipts = new HashMap<>();
    private final Function<ToolRequest, String> policy;
    public GameToolRegistry(Function<ToolRequest, String> policy) { this.policy = Objects.requireNonNull(policy); }
    public synchronized void register(ToolDefinition definition, Function<ToolRequest, ActionResult> handler) {
        Objects.requireNonNull(definition); Objects.requireNonNull(handler);
        if (definitions.containsKey(definition.getName())) throw new IllegalArgumentException("Duplicate tool");
        definitions.put(definition.getName(), definition); handlers.put(definition.getName(), handler);
    }
    public synchronized List<ToolDefinition> definitions() {
        return Collections.unmodifiableList(new ArrayList<>(definitions.values()));
    }
    public synchronized ActionResult execute(ToolRequest request) {
        Objects.requireNonNull(request);
        ToolRequest previous = requests.get(request.getActionId());
        if (previous != null) {
            return previous.samePayload(request) ? receipts.get(request.getActionId()) : ActionResult.rejected("ACTION_ID_CONFLICT");
        }
        ToolDefinition definition = definitions.get(request.getName());
        if (definition == null) return ActionResult.rejected("UNKNOWN_TOOL");
        if (!definition.accepts(request.getArguments())) return ActionResult.rejected("INVALID_ARGUMENTS");
        if (receipts.size() >= MAX_RECEIPTS) return ActionResult.rejected("SESSION_ACTION_LIMIT");
        ActionResult result;
        try {
            String denied = policy.apply(request);
            result = denied == null ? Objects.requireNonNull(handlers.get(request.getName()).apply(request)) : ActionResult.rejected(denied);
        } catch (RuntimeException error) {
            // Do not leak provider keys, engine internals or a stack trace to an LLM.
            result = ActionResult.failed("TOOL_ERROR");
        }
        requests.put(request.getActionId(), request);
        receipts.put(request.getActionId(), result);
        return result;
    }
}
