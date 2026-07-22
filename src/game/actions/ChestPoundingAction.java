package game.actions;

import edu.monash.fit2099.engine.actors.Actor;
import game.conditions.FavoriteAffection;

public final class ChestPoundingAction extends AffectionAction {
    public ChestPoundingAction(Actor target) {
        super(target, "Chest Pounding", FavoriteAffection.CHEST_POUNDING);
    }
}
