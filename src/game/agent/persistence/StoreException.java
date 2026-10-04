package game.agent.persistence;
/** Finite public storage error; underlying database/path details are never exposed. */
public final class StoreException extends RuntimeException {
    public enum Code { INVALID_OWNER, INVALID_CHECKPOINT, TOO_LARGE, CORRUPT, IO_ERROR, DATABASE_ERROR, CLOSED }
    private final Code code;
    public StoreException(Code code) { super(code.name()); this.code=code; }
    public Code getCode() { return code; }
}
