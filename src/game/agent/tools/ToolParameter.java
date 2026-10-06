package game.agent.tools;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Strict scalar schema. No string-to-number coercion or silent truncation. */
public final class ToolParameter {
    public enum Type { STRING, INTEGER, BOOLEAN }
    private final Type type;
    private java.util.List<String> allowedValues=java.util.Collections.emptyList();
    private final int minimum;
    private final int maximum;
    private ToolParameter(Type type, int minimum, int maximum) {
        this.type = type; this.minimum = minimum; this.maximum = maximum;
    }
    public static ToolParameter string() { return new ToolParameter(Type.STRING, 0, 0); }
    public static ToolParameter literal(String value) { if(value==null||value.trim().isEmpty())throw new IllegalArgumentException("Empty literal");ToolParameter p=string();p.allowedValues=java.util.Collections.singletonList(value);return p; }
    public java.util.List<String> getAllowedValues(){return allowedValues;}
    public static ToolParameter bool() { return new ToolParameter(Type.BOOLEAN, 0, 0); }
    public static ToolParameter integer(int minimum, int maximum) {
        if (minimum > maximum) throw new IllegalArgumentException("Invalid numeric interval");
        return new ToolParameter(Type.INTEGER, minimum, maximum);
    }
    public Type getType() { return type; }
    public int getMinimum() { return minimum; }
    public int getMaximum() { return maximum; }
    public boolean accepts(Object value) {
        if (type == Type.STRING) return value instanceof String && !((String) value).trim().isEmpty() && (allowedValues.isEmpty()||allowedValues.contains(value));
        if (type == Type.BOOLEAN) return value instanceof Boolean;
        // Only immutable JSON numeric representations; never retain a mutable Number.
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double || value instanceof BigDecimal || value instanceof BigInteger)) return false;
        try {
            int number = new BigDecimal(value.toString()).intValueExact();
            return number >= minimum && number <= maximum;
        } catch (NumberFormatException | ArithmeticException invalid) {
            return false;
        }
    }
}
