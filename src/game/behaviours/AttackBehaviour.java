package game.behaviours;

import edu.monash.fit2099.engine.actions.Action;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.positions.Exit;
import edu.monash.fit2099.engine.positions.GameMap;
import edu.monash.fit2099.engine.positions.Location;
import game.actions.AttackAction;
import game.actors.pokemon.Pokemon;
import game.actors.pokemon.Treecko;
import game.conditions.Element;
import game.conditions.ElementsHelper;
import game.actors.pokemon.Torchic;
import game.conditions.Status;

/**
 * Created by:
 * @author Riordan D. Alfredo
 * Modified by:
 *
 */
public class AttackBehaviour extends Action implements Behaviour {
    /**
     *  HINT: develop a logic to check surrounding, check elements, and return an action to attack that opponent.
     */

    Actor target;

    @Override
    public Action getAction(Actor actor, GameMap map) {
        Location here = map.locationOf(actor);
        for (Exit exit : here.getExits()) {
            if (exit.getDestination().containsAnActor()) {
                target = exit.getDestination().getActor();
                Location there = map.locationOf(target);
                if (!ElementsHelper.hasAnySimilarElements(actor,
                    target.findCapabilitiesByType(Element.class)) && !target.hasCapability(
                    Status.IMMUNE)) {
                    return new AttackAction(target, "there"); // behaviour will stop here.
                }
            }
        }
        return null; // go to next behaviour
    }

    //if (exit.getDestination().getGround().hasCapability(Element.GRASS)){)
    //if (exit.getDestination().getActor().capabilitiesList().stream().filter(actor.)){}
//        if (actor.hasCapability(Element.FIRE)){
//            for (Exit exit : here.getExits()) {
//                if (exit.getDestination().containsAnActor()){
//                    target = exit.getDestination().getActor();
//                    if(target.hasCapability(Element.FIRE) || target.hasCapability(Element.WATER)){
//
//                    }
//                }
//        } else if (actor.hasCapability(Element.WATER)){
//
//        } else {
//
//        }



    @Override
    public String execute(Actor actor, GameMap map) {
        return actor + " performs " + actor.getWeapon().verb() + " at " + target + "."+ System.lineSeparator()+
            "It deals " + actor.getWeapon().damage() + " damage.";
    }

    @Override
    public String menuDescription(Actor actor) {
        return "";
    }
}


