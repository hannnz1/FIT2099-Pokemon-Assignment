package game.agent.runtime;

import edu.monash.fit2099.engine.actions.*;
import edu.monash.fit2099.engine.displays.Display;
import edu.monash.fit2099.engine.positions.GameMap;
import game.actors.pokemon.Mudkip;
import game.conditions.Element;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Opt-in Mudkip that uses only the Agent loop for turns, avoiding dual control.
 * Existing species capabilities, capture rules and day/night effects are retained.
 * Other species can return AgentTurnAction from their own playTurn implementation.
 */
public final class AgentMudkip extends Mudkip {
    private AgentLoop loop;
    private GameMap map;
    private LongSupplier clock;
    public void attach(AgentLoop loop,GameMap map,LongSupplier clock) {
        if(this.loop!=null) throw new IllegalStateException("Agent already attached");
        Objects.requireNonNull(loop); Objects.requireNonNull(map); Objects.requireNonNull(clock);
        if(!map.contains(this)) throw new IllegalArgumentException("Add actor to the map first");
        this.loop=loop; this.map=map; this.clock=clock;
    }
    @Override public Action playTurn(ActionList actions,Action lastAction,GameMap current,Display display) {
        if(loop==null || current!=map) return new DoNothingAction();
        toggleWeapon(current.locationOf(this).getGround().hasCapability(Element.WATER));
        return new AgentTurnAction(this,map,loop,clock);
    }
}
