package game.runtime;
import edu.monash.fit2099.engine.actions.*;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.items.Item;
import edu.monash.fit2099.engine.positions.*;

/** Shared engine action collection, independent of console input. */
public final class AvailableActionCollector {
    public static ActionList collect(Actor actor, GameMap map) {
        ActionList actions=new ActionList();
        if (!map.contains(actor) || !actor.isConscious()) return actions;
        Location here=map.locationOf(actor);
        for(Item item:actor.getInventory()) { actions.add(item.getAllowableActions()); actions.add(item.getDropAction(actor)); }
        actions.add(here.getGround().allowableActions(actor,here,""));
        for(Exit exit:here.getExits()) {
            Location dest=exit.getDestination();
            if(dest.containsAnActor()) actions.add(dest.getActor().allowableActions(actor,exit.getName(),map));
            else actions.add(dest.getGround().allowableActions(actor,dest,exit.getName()));
            actions.add(dest.getMoveAction(actor,exit.getName(),exit.getHotKey()));
        }
        for(Item item:here.getItems()) { actions.add(item.getAllowableActions()); actions.add(item.getPickUpAction(actor)); }
        actions.add(new DoNothingAction()); return actions;
    }
}
