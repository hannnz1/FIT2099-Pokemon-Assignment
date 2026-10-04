package game.agent.llm;

import edu.monash.fit2099.engine.positions.Location;
import java.util.*;

/** Server-owned immutable tile and absolute-time policy. Every route step must call allows. */
public final class ConstraintPolicy {
    private final Set<Location> allowed;
    private final long deadline;
    public ConstraintPolicy(TaskIntent intent,Map<String,Set<Location>> areas,long questDeadline) {
        Objects.requireNonNull(intent); Objects.requireNonNull(areas);
        if(questDeadline<=0) throw new IllegalArgumentException("INVALID_QUEST_DEADLINE");
        Long requested=intent.getDeadlineTurn();
        if(requested!=null && requested>questDeadline) throw new IllegalArgumentException("DEADLINE_AFTER_NIGHT");
        deadline=requested==null?questDeadline:requested;
        if(intent.has(TaskIntent.Constraint.AREA_RESTRICTED)) {
            Set<Location> tiles=areas.get(intent.getAreaId());
            if(tiles==null || tiles.isEmpty() || tiles.contains(null)) throw new IllegalArgumentException("UNKNOWN_AREA");
            allowed=Collections.unmodifiableSet(new HashSet<>(tiles));
        } else allowed=null;
    }
    public boolean allows(Location location) { return location!=null && (allowed==null || allowed.contains(location)); }
    public long deadlineTurn() { return deadline; }
}
