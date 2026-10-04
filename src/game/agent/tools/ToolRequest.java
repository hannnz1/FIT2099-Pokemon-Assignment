package game.agent.tools;

import java.util.*;

/** An action ID identifies a single execution; retries must reuse the same payload. */
public final class ToolRequest {
    private final String actionId;
    private final String name;
    private final Map<String, Object> arguments;
    public ToolRequest(String actionId, String name, Map<String, ?> arguments) {
        if (actionId == null || actionId.trim().isEmpty()) throw new IllegalArgumentException("Missing actionId");
        this.actionId = actionId;
        this.name = Objects.requireNonNull(name);
        this.arguments = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(arguments)));
    }
    public String getActionId() { return actionId; }
    public String getName() { return name; }
    public Map<String, Object> getArguments() { return arguments; }
    boolean samePayload(ToolRequest other) { return name.equals(other.name) && arguments.equals(other.arguments); }
}
