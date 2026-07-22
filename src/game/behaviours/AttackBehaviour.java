package game.behaviours;

import edu.monash.fit2099.engine.actions.Action;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.positions.Exit;
import edu.monash.fit2099.engine.positions.GameMap;
import edu.monash.fit2099.engine.positions.Location;
import game.actions.AttackAction;
import game.conditions.Element;
import game.conditions.ElementsHelper;
import game.conditions.Status;

/** Selects the first adjacent, non-immune actor with a different element. */
public final class AttackBehaviour implements Behaviour {

    @Override
    public Action getAction(Actor actor, GameMap map) {
        Location currentLocation = map.locationOf(actor);
        for (Exit exit : currentLocation.getExits()) {
            if (!exit.getDestination().containsAnActor()) {
                continue;
            }

            Actor target = exit.getDestination().getActor();
            boolean sharesElement = ElementsHelper.hasAnySimilarElements(
                    actor, target.findCapabilitiesByType(Element.class));
            if (!sharesElement && !target.hasCapability(Status.IMMUNE)) {
                return new AttackAction(target, exit.getName());
            }
        }
        return null;
    }
}
