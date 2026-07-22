package game.environments;

import edu.monash.fit2099.engine.positions.Exit;
import edu.monash.fit2099.engine.positions.Ground;
import edu.monash.fit2099.engine.positions.Location;
import game.actors.pokemon.Torchic;
import game.conditions.Element;
import game.environments.structures.StructuralGrounds;
import game.time.TimePerception;
import game.time.TimePerceptionManager;
import java.util.Random;

/**
 * Created by:
 * @author Riordan D. Alfredo
 * Modified by:
 *
 */
public class Lava extends Ground implements TimePerception {
    Random rand = new Random();
    private Location location;

    /**
     * Constructor.
     */
    public Lava() {
        super('^');
        this.addCapability(Element.FIRE);
        this.registerInstance();
    }

    @Override
    public void tick(Location location) {
        this.location = location;
    }


    //    When it is at day, at every turn:
    //    Lava ground ^ has 10% to expand (convert its surrounding grounds to Lava). It looks like a flood of lava.
    //    The expanding grounds won't convert the Floors, Walls, and grounds with similar elements
    //    (e.g., the expansion of puddles won't convert Waterfall to puddles).
    /**
     * made the day shift effect specially for Tree
     * ground things
     * @param: no parameter is needed
     * @return: no return is needed
     */
    @Override
    public void dayEffect() {
        if (location != null) {
            Random rand = new Random();
            if (rand.nextInt(100) == 10) {
                for (Exit exit : location.getExits()) {
                    if (!(exit.getDestination().getGround().hasCapability(Element.FIRE))
                        && !(exit.getDestination().getGround() instanceof StructuralGrounds)) {
                        exit.getDestination().setGround(new Lava());
                    }
                }
            }
        }
    }

    //    When it is at night, at every turn:
    //    Lava ^ has a 10% chance of being destroyed (converted to a Dirt .) as long as there's no actor on it.
    /**
     * made the night shift effect specially for Tree
     * ground things
     * @param: no parameter is needed
     * @return: no return is needed
     */
    @Override
    public void nightEffect() {
        if (location != null) {
            Random rand = new Random();
            if (rand.nextInt(100) == 10 && !location.containsAnActor()) {
                location.setGround(new Dirt());
            }
        }
    }


    @Override
    public void registerInstance() {
        TimePerception.super.registerInstance();
    }

//    @Override
//    public void registerInstance() {
//        TimePerception.super.registerInstance();
//    }

//    default void registerInstance(){
//        TimePerceptionManager.getInstance().append(this);
//    }
}

