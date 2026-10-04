package game.agent;

import game.agent.action.ActionResult;
import game.agent.tools.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {
    private ToolDefinition purchase() {
        Map<String, ToolParameter> schema = new LinkedHashMap<>();
        schema.put("itemId", ToolParameter.string());
        schema.put("quantity", ToolParameter.integer(1, 3));
        return new ToolDefinition("purchase", "Buy items", true, schema);
    }
    private ToolRequest request(String id, Object quantity) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("itemId", "berry"); args.put("quantity", quantity);
        return new ToolRequest(id, "purchase", args);
    }
    @Test void invalidArgumentsNeverChangeInventory() {
        AtomicInteger berries = new AtomicInteger();
        GameToolRegistry registry = new GameToolRegistry(r -> null);
        registry.register(purchase(), r -> { berries.addAndGet(((Number) r.getArguments().get("quantity")).intValue()); return ActionResult.success("PURCHASED"); });
        for (Object invalid : Arrays.asList(0, -1, 4, 1.5, "2", Double.NaN, Double.POSITIVE_INFINITY,
                new java.math.BigDecimal("1.00000000000000000001"), new AtomicInteger(1), null)) {
            assertEquals("INVALID_ARGUMENTS", registry.execute(request(UUID.randomUUID().toString(), invalid)).getCode());
        }
        Map<String,Object> extra = new HashMap<>(request("a", 1).getArguments()); extra.put("admin", true);
        assertEquals("INVALID_ARGUMENTS", registry.execute(new ToolRequest("extra", "purchase", extra)).getCode());
        assertEquals("INVALID_ARGUMENTS", registry.execute(new ToolRequest("missing", "purchase", Collections.emptyMap())).getCode());
        assertEquals(0, berries.get());
    }
    @Test void policyIsEnforcedAndDuplicateExecutionIsPrevented() {
        AtomicInteger coins = new AtomicInteger(10);
        GameToolRegistry denied = new GameToolRegistry(r -> "NO_SPENDING");
        denied.register(purchase(), r -> { coins.decrementAndGet(); return ActionResult.success("PURCHASED"); });
        assertEquals("NO_SPENDING", denied.execute(request("denied", 1)).getCode());
        assertEquals(10, coins.get());
        GameToolRegistry registry = new GameToolRegistry(r -> null);
        registry.register(purchase(), r -> { coins.decrementAndGet(); return ActionResult.success("PURCHASED"); });
        ActionResult first = registry.execute(request("one", 1));
        assertSame(first, registry.execute(request("one", 1)));
        assertEquals(9, coins.get());
        assertEquals("ACTION_ID_CONFLICT", registry.execute(request("one", 2)).getCode());
        assertEquals(9, coins.get());
    }
    @Test void whitelistAndDefinitionCannotBeChangedByCaller() {
        GameToolRegistry registry = new GameToolRegistry(r -> null);
        registry.register(purchase(), r -> ActionResult.success("OK"));
        assertEquals("UNKNOWN_TOOL", registry.execute(new ToolRequest("x", "shell", Collections.emptyMap())).getCode());
        assertThrows(IllegalArgumentException.class, () -> registry.register(purchase(), r -> ActionResult.success("OK")));
        assertThrows(UnsupportedOperationException.class, () -> registry.definitions().clear());
        assertThrows(UnsupportedOperationException.class, () -> purchase().getParameters().clear());
    }
    @Test void failedHandlerIsNotExecutedAgainOnRetry() {
        AtomicInteger count = new AtomicInteger();
        GameToolRegistry registry = new GameToolRegistry(r -> null);
        registry.register(purchase(), r -> { count.incrementAndGet(); throw new IllegalStateException("internal secret"); });
        assertEquals("TOOL_ERROR", registry.execute(request("x", 1)).getCode());
        registry.execute(request("x", 1));
        assertEquals(1, count.get());
    }
    @Test void resultDefensivelyCopiesData() {
        Map<String,String> data = new HashMap<>(); data.put("count", "2");
        ActionResult result = ActionResult.of(ActionResult.Status.SUCCESS, "OK", data);
        data.put("count", "99");
        assertEquals("2", result.getData().get("count"));
        assertThrows(UnsupportedOperationException.class, () -> result.getData().clear());
    }
}
