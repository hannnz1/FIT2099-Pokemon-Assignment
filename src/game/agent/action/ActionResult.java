package game.agent.action;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable tool outcome; success of a tool never implies completion of a quest. */
public final class ActionResult {
    public enum Status { SUCCESS, IN_PROGRESS, REJECTED, INTERRUPTED, TIMED_OUT, FAILED }
    private final Status status;
    private final String code;
    private final Map<String, String> data;

    private ActionResult(Status status, String code, Map<String, String> data) {
        this.status = Objects.requireNonNull(status);
        this.code = Objects.requireNonNull(code);
        this.data = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(data)));
    }
    public static ActionResult of(Status status, String code, Map<String, String> data) {
        return new ActionResult(status, code, data);
    }
    public static ActionResult success(String code) { return of(Status.SUCCESS, code, Collections.emptyMap()); }
    public static ActionResult rejected(String code) { return of(Status.REJECTED, code, Collections.emptyMap()); }
    public static ActionResult failed(String code) { return of(Status.FAILED, code, Collections.emptyMap()); }
    public Status getStatus() { return status; }
    public String getCode() { return code; }
    public Map<String, String> getData() { return data; }
    @Override public String toString() { return status + ":" + code + " " + data; }
}
