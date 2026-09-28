package game.runtime;
import java.util.*;
import game.conditions.AffectionManager;
import game.web.dto.EventDto;

/** Owned by exactly one session; no static mutable gameplay state. */
public final class GameContext {
    public final AffectionManager affection = new AffectionManager();
    public final RandomSource random;
    private final IdentityHashMap<Object,String> ids = new IdentityHashMap<>();
    private long sequence;
    public final List<EventDto> events = new ArrayList<>();
    public GameContext() { this(new Random()::nextInt); }
    public GameContext(RandomSource random) { this.random = Objects.requireNonNull(random); }
    public String id(Object value) { return ids.computeIfAbsent(value, k -> "e" + (++sequence)); }
    public long order(Object value) { return Long.parseLong(id(value).substring(1)); }
    public void forget(Object value) { ids.remove(value); }
    public int retainedCount() { return ids.size(); }
    public void retain(Set<Object> active) { ids.keySet().removeIf(o -> !active.contains(o)); }
    public void event(String kind, Object actor, Object target, String text) {
        events.add(new EventDto(kind, actor == null ? null : id(actor), target == null ? null : id(target), GameText.chinese(text)));
    }
}
