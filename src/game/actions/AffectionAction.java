package game.actions;

import edu.monash.fit2099.engine.actions.Action;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.positions.GameMap;
import game.conditions.AffectionManager;

import java.util.Objects;

/** Shared workflow for trainer interactions that modify Pokemon affection. */
public abstract class AffectionAction extends Action {
    public Actor getTarget() { return target; }


    private static final int FAVOURITE_BONUS = 10;
    private static final int NON_FAVOURITE_PENALTY = 20;

    private final Actor target;
    private final String interactionName;
    private final Enum<?> favouriteCapability;

    protected AffectionAction(Actor target, String interactionName, Enum<?> favouriteCapability) {
        this.target = Objects.requireNonNull(target, "target cannot be null");
        this.interactionName = Objects.requireNonNull(interactionName, "interaction name cannot be null");
        this.favouriteCapability = Objects.requireNonNull(favouriteCapability, "favourite capability cannot be null");
    }

    @Override
    public final String execute(Actor actor, GameMap map) {
        AffectionManager manager = map == null ? AffectionManager.getInstance() : map.context().affection;
        manager.registerTrainer(actor);
        manager.registerPokemon(target);

        boolean favourite = target.hasCapability(favouriteCapability);
        int points = favourite ? FAVOURITE_BONUS : NON_FAVOURITE_PENALTY;
        if (favourite) {
            manager.increaseAffection(target, points);
        } else {
            manager.decreaseAffection(target, points);
        }

        return target + (favourite ? " likes it! +" : " dislikes it! -")
                + points + " affection points. " + manager.getAffectionPoint(target) + "AP";
    }

    @Override
    public final String menuDescription(Actor actor) {
        AffectionManager manager = AffectionManager.getInstance();
        int points = manager.isRegistered(target) ? manager.getAffectionPoint(target) : 0;
        return actor + " tries " + interactionName + " with " + target + " (" + points + "AP)";
    }
}
