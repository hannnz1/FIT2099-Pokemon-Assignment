package game.runtime;

import edu.monash.fit2099.engine.positions.Location;

/** Shared four-direction movement rule for actors and reachable item placement. */
public final class GridMovement {
    private GridMovement() {}
    public static boolean isCardinal(Location from, Location to) {
        return Math.abs(from.x()-to.x())+Math.abs(from.y()-to.y())==1;
    }
}
