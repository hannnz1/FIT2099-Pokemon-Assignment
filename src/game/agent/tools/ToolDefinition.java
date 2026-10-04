package game.agent.tools;

import java.util.*;

/** Provider-neutral metadata, suitable for conversion to function-call JSON schema. */
public final class ToolDefinition {
    private final String name;
    private final String description;
    private final boolean changesWorld;
    private final Map<String, ToolParameter> parameters;
    public ToolDefinition(String name, String description, boolean changesWorld, Map<String, ToolParameter> parameters) {
        if (name == null || !name.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("Invalid tool name");
        this.name = name;
        this.description = Objects.requireNonNull(description);
        this.changesWorld = changesWorld;
        LinkedHashMap<String, ToolParameter> copy = new LinkedHashMap<>(Objects.requireNonNull(parameters));
        copy.forEach((key, value) -> { Objects.requireNonNull(key); Objects.requireNonNull(value); });
        this.parameters = Collections.unmodifiableMap(copy);
    }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean changesWorld() { return changesWorld; }
    public Map<String, ToolParameter> getParameters() { return parameters; }
    public boolean accepts(Map<String, Object> args) {
        if (!args.keySet().equals(parameters.keySet())) return false;
        for (Map.Entry<String, ToolParameter> entry : parameters.entrySet()) {
            if (!entry.getValue().accepts(args.get(entry.getKey()))) return false;
        }
        return true;
    }
}
