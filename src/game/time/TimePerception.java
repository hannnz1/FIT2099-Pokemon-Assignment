package game.time;

import edu.monash.fit2099.engine.positions.Location;

/**
 * Created by:
 * @author Riordan D. Alfredo
 * Modified by:
 *
 */

//Each ground, actor, and some items may have a time perception of the world.
// They may experience two different kinds of time periods Day and Night.
// A day will last for 5 turns and so the night. They will repeat alternately and indefinitely (see pictures below).

//R5.1 Day-time
//    When it is at day, at every turn:
//    Torchic will be healed by 20 hit points.
//    When it is at day, at every turn:
//    Mudkip will be hurt by 15 hit points.
//    When it is at day, at every turn:
//    Treecko will be hurt by 5 hit points.
//    When it is at day, at every turn:
//    Lava ground ^ has 10% to expand (convert its surrounding grounds to Lava). It looks like a flood of lava.
//    When it is at day, at every turn:
//    Puddle ~ have a 10% chance of being destroyed (converted to a Dirt .).
//    When it is at day, at every turn:
//    Trees T have a 5% chance of dropping a Candy.

//R5.2 Night-time
//    When it is at night, at every turn:
//    Mudkip will be healed by 10 hit points.
//    When it is at night, at every turn:
//    Treecko will be healed by 5 points.
//    When it is at night, at every turn:
//    Torchic will be hurt by 15 hit points.
//    When it is at night, at every turn:
//    Lava ^ has a 10% chance of being destroyed (converted to a Dirt .) as long as there's no actor on it.
//    When it is at night, at every turn:
//    Puddle ~ has 10% chance to expand (convert its surrounding grounds to Puddle~).
//    When it is at night, at every turn:
//    Trees T have 10% chance to expand (convert its surroundings to either all Trees or all Hays randomly).

//    The expanding grounds won't convert the Floors, Walls, and grounds with similar elements (e.g., the expansion of puddles won't convert Waterfall to puddles).
public interface TimePerception {
    /**
     * TODO: override this method, and execute this method inside the relevant manager.
     */
    void dayEffect();
    //new implementation
    //void dayEffect(Location location);

    /**
     * TODO: override this method, and execute this method inside the relevant manager.
     */
    void nightEffect();
    //new implementation
    //void nightEffect(Location location);

    /**
     * a default interface method that register current instance to the Singleton manager.
     * It allows corresponding class uses to be affected by global reset.
     * TODO: Use this method at the constructor of the concrete class that implements it (`this` instance).
     *       For example:
     *       Simple(){
     *          // other stuff for constructors.
     *          this.registerInstance()  // add this instance to the relevant manager.
     *       }
     */
    default void registerInstance(){
        if (TimePerceptionManager.getInstance() != null) {
            TimePerceptionManager.getInstance().append(this);
        }
    }
}


