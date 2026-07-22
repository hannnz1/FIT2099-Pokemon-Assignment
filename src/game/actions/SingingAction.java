package game.actions;

import edu.monash.fit2099.engine.actors.Actor;
import game.conditions.FavoriteAffection;

public final class SingingAction extends AffectionAction {
    public SingingAction(Actor target) {
        super(target, "Singing", FavoriteAffection.SINGING);
    }
}
