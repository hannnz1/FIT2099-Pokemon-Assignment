package game.actions;

import edu.monash.fit2099.engine.actions.Action;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.positions.GameMap;
import game.actors.npc.ProffesorOak;
import game.actors.npc.Shopkeeper;
import game.conditions.Element;
import game.items.balls.Pokeball;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Produces contextual NPC dialogue without coupling the NPC to console I/O. */
public final class TalkAction extends Action {

    private final String direction;
    private final Actor target;

    public TalkAction(Actor target, String direction) {
        this.target = Objects.requireNonNull(target, "target cannot be null");
        this.direction = Objects.requireNonNull(direction, "direction cannot be null");
    }

    @Override
    public String execute(Actor actor, GameMap map) {
        if (target instanceof ProffesorOak) {
            return hasAllElements(actor)
                    ? "Professor Oak: Congratulations " + actor + "! Here is a Pokedex for you."
                    : "Professor Oak: I want to see 3 different Pokemon, " + actor + "! Gotta catch 'em all!";
        }
        if (target instanceof Shopkeeper) {
            return "Shopkeeper: Bring me Candy to trade for GreatBalls, MasterBalls, or a Torchic.";
        }
        return target + " has nothing to say.";
    }

    private boolean hasAllElements(Actor actor) {
        Set<Element> capturedElements = EnumSet.noneOf(Element.class);
        actor.getInventory().stream()
                .filter(Pokeball.class::isInstance)
                .map(Pokeball.class::cast)
                .filter(Pokeball::containsPokemon)
                .map(Pokeball::getPokemon)
                .forEach(pokemon -> capturedElements.addAll(pokemon.findCapabilitiesByType(Element.class)));
        return capturedElements.contains(Element.FIRE)
                && capturedElements.contains(Element.WATER)
                && capturedElements.contains(Element.GRASS);
    }

    @Override
    public String menuDescription(Actor actor) {
        return actor + " talks to " + target + " at " + direction;
    }
}
