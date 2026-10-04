package game.agent.runtime;

import edu.monash.fit2099.engine.actions.Action;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.positions.GameMap;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Bridges the original engine's turn execution to a nonblocking Agent loop. */
public final class AgentTurnAction extends Action {
    private final Actor owner;
    private final GameMap world;
    private final AgentLoop loop;
    private final LongSupplier clock;
    private String result;
    public AgentTurnAction(Actor owner,GameMap world,AgentLoop loop,LongSupplier clock) {
        this.owner=Objects.requireNonNull(owner); this.world=Objects.requireNonNull(world);
        this.loop=Objects.requireNonNull(loop); this.clock=Objects.requireNonNull(clock);
    }
    @Override public String execute(Actor actor,GameMap map) {
        if(actor!=owner || map!=world || !map.contains(owner) || !owner.isConscious()) return "REJECTED:ACTOR_UNAVAILABLE";
        if(result==null) result=loop.tick(clock.getAsLong()).toString();
        return result;
    }
    @Override public String menuDescription(Actor actor) { return actor+" follows the Agent task"; }
}
