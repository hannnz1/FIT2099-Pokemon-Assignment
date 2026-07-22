package game.actions;

import edu.monash.fit2099.engine.actors.Actor;
import game.conditions.FavoriteAffection;

public final class DancingAction extends AffectionAction {
    public DancingAction(Actor target) {
        super(target, "Dancing", FavoriteAffection.DANCING);
    }
}
