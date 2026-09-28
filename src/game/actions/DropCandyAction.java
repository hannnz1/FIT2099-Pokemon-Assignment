package game.actions;

import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.items.DropItemAction;
import edu.monash.fit2099.engine.positions.GameMap;
import game.items.Candy;
import game.runtime.WorldPopulation;

/** Rechecks capacity before removing the player's item, including stale callers. */
public final class DropCandyAction extends DropItemAction {
    public DropCandyAction(Candy item) {super(item);}
    @Override public String execute(Actor actor,GameMap map) {
        if(!actor.getInventory().contains(getItem()))return "背包中已没有这颗 Candy。";
        if(!WorldPopulation.canPlaceCandy(map.locationOf(actor)))return "地图最多放置 2 颗 Candy，且不能堆叠；糖果仍在背包中。";
        return super.execute(actor,map);
    }
}
