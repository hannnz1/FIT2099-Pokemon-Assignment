package game.behaviours;

import java.util.ArrayList;
import java.util.Random;

import edu.monash.fit2099.engine.actions.Action;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.positions.Exit;
import edu.monash.fit2099.engine.positions.GameMap;
import edu.monash.fit2099.engine.positions.Location;

public class WanderBehaviour implements Behaviour {

	private final java.util.function.IntSupplier random;
    private final java.util.function.Predicate<Location> allowed;
    public WanderBehaviour(){this(location->true);}
    public WanderBehaviour(java.util.function.Predicate<Location> allowed){this(allowed,new Random()::nextInt);} public WanderBehaviour(java.util.function.Predicate<Location> allowed,java.util.function.IntSupplier random){this.allowed=java.util.Objects.requireNonNull(allowed);this.random=java.util.Objects.requireNonNull(random);}

	/**
	 * Returns a MoveAction to wander to a random location, if possible.
	 * If no movement is possible, returns null.
	 *
	 * @param actor the Actor enacting the behaviour
	 * @param map the map that actor is currently on
	 * @return an Action, or null if no MoveAction is possible
	 */
	@Override
	public Action getAction(Actor actor, GameMap map) {
		ArrayList<Action> actions = new ArrayList<>();

		for (Exit exit : map.locationOf(actor).getExits()) {
            Location destination = exit.getDestination();
            if (allowed.test(destination) && destination.canActorEnter(actor)) {
            	actions.add(exit.getDestination().getMoveAction(actor, "around", exit.getHotKey()));
            }
        }

		if (!actions.isEmpty()) {
			return actions.get(Math.floorMod(random.getAsInt(),actions.size()));
		}
		else {
			return null; // go to next behaviour
		}
	}
}
