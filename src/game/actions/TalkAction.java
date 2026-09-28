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
public Actor getTarget() { return target; }


    private final String direction;
    private final Actor target;

    public TalkAction(Actor target, String direction) {
        this.target = Objects.requireNonNull(target, "target cannot be null");
        this.direction = Objects.requireNonNull(direction, "direction cannot be null");
    }

    @Override
    public String execute(Actor actor, GameMap map) {
        String text=dialogue(actor);
        if(map!=null)map.context().event("DIALOGUE",actor,target,text);
        return text;
    }

    private String dialogue(Actor actor) {
        if (target instanceof ProffesorOak) {
            return hasAllElements(actor)
                    ? "大木博士：恭喜你，" + actor + "！你已经集齐草、水、火三种属性的精灵！"
                    : "大木博士：我想看看草、水、火三种属性的精灵。去收集它们吧，" + actor + "！";
        }
        if (target instanceof Shopkeeper) {
            return "商人：带糖果来，可以兑换高级球、大师球，或装有火稚鸡的精灵球。";
        }
        return target + "暂时没有想说的话。";
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
