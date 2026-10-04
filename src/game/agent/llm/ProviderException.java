package game.agent.llm;

/** Only sanitized reason codes cross into task traces. Never retain HTTP bodies/keys. */
public final class ProviderException extends RuntimeException {
    public enum Code { CONFIGURATION, TIMEOUT, RATE_LIMIT, BUDGET_EXHAUSTED, QUOTA_EXHAUSTED, AUTHENTICATION, UNAVAILABLE, REQUEST_REJECTED, SAFETY_BLOCK, INVALID_RESPONSE, UNSUPPORTED_TASK }
    private final Code code;
    public ProviderException(Code code) { super("PROVIDER_"+code.name()); this.code=code; }
    public Code getCode() { return code; }
}
