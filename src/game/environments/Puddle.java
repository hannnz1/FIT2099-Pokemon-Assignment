package game.environments;

import edu.monash.fit2099.engine.positions.Exit;
import edu.monash.fit2099.engine.positions.Ground;
import edu.monash.fit2099.engine.positions.Location;
import game.conditions.Element;
import game.environments.structures.StructuralGrounds;
import game.time.TimePerception;
import game.time.TimePerceptionManager;
import java.util.Random;

public class Puddle extends Ground implements TimePerception {
    /**
     * Constructor.
     *
     */
    private Location location;
    public Puddle() {
        super('~');
        this.addCapability(Element.WATER);
        // Time effects are scoped to active map tiles.
    }

    @Override
    public void tick(Location location) {
        this.location = location;
        //super.tick(location);
        //nightEffect(location);
    }

    //    When it is at day, at every turn:
    //    Puddle ~ have a 10% chance of being destroyed (converted to a Dirt .).
    /**
     * made the day shift effect specially for Tree
     * ground things
     * @param: no parameter is needed
     * @return: no return is needed
     */
    @Override
    public void dayEffect() {
        if (location != null && location.getGround() == this) {
            game.runtime.RandomSource rand = location.map().context().random;
            if (rand.nextInt(100) == 10 && !location.containsAnActor()) {
                location.setGround(new Dirt());
            }
        }
    }

    //    When it is at night, at every turn:
    //    Puddle ~ has 10% chance to expand (convert its surrounding grounds to Puddle~).
    //    The expanding grounds won't convert the Floors, Walls, and grounds with similar elements
    //    (e.g., the expansion of puddles won't convert Waterfall to puddles).
    /**
     * made the night shift effect specially for Tree
     * ground things
     * @param: no parameter is needed
     * @return: no return is needed
     */
    @Override
    public void nightEffect() {
//        if (location == !null) {
//            TimePerceptionManager.getInstance().cleanUp(this);
//        }
        if (location != null && location.getGround() == this) {
            game.runtime.RandomSource rand = location.map().context().random;
            if (rand.nextInt(100) == 10) {
                for (Exit exit : location.getExits()) {
                    if (!(exit.getDestination().getGround().hasCapability(Element.WATER))
                        && !(exit.getDestination().getGround() instanceof StructuralGrounds)) {
                        exit.getDestination().setGround(new Puddle());
                    }
                }
            }
        }
    }

    @Override
    public void registerInstance() {
        TimePerception.super.registerInstance();
    }
}
