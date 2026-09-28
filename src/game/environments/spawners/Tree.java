package game.environments.spawners;

import edu.monash.fit2099.engine.positions.Exit;
import edu.monash.fit2099.engine.positions.Ground;
import edu.monash.fit2099.engine.positions.Location;
import game.conditions.Element;
import game.actors.pokemon.Treecko;
import game.environments.Hay;
import game.environments.structures.StructuralGrounds;

import game.time.TimePerception;
import game.time.TimePerceptionManager;
import java.util.Random;

public class Tree extends SpawnerGrounds implements TimePerception {
    //    Tree has a 15% chance will spawn a Treecko (see REQ2)
    public static int percentSpawn = 15;

    private Location location;

    /**
     * Constructor.
     *
     */
    public Tree() {
        super('T');
        this.addCapability(Element.GRASS);
        // Time effects are scoped to active map tiles.
    }
    /**
     * go through one day and night turn, made some affection to special pokemons or
     * ground things
     * @param location: current game map, prepare the extension Tree area
     * @return: no return is needed
     */
//    3. Tree T  and Hay , 🌳
//    Tree is a spawning ground. Tree and Hay have Grass element. At every turn:
    @Override
    public void tick(Location location) {
        game.runtime.RandomSource rand = location.map().context().random;
        //    IF there is also at least one (1) GRASS element ground in its surrounding.
        for (Exit exit : location.getExits()) {
            if (exit.getDestination().getGround().hasCapability(Element.GRASS)){
                //    Tree has a 15% chance will spawn a Treecko (see REQ2)
                if(rand.nextInt(100) <= percentSpawn && game.runtime.WorldPopulation.trySpawn(location,new Treecko())){
                    break;
                }
            }

        }
        this.location = location;
    }

    //    When it is at day, at every turn:
    //    Candy spawning is owned by WorldPopulation, independently of trees.
    /**
     * made the day shift effect specially for Tree
     * ground things
     * @param: no parameter is needed
     * @return: no return is needed
     */
    @Override
    public void dayEffect() {
        // Candy is replenished once per world turn at random reachable locations.
    }

    //    When it is at night, at every turn:
    //    Trees T have 10% chance to expand (convert its surroundings to either all Trees or all Hays randomly).
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
        if (location != null && location.getGround() == this) {
            game.runtime.RandomSource rand = location.map().context().random;
            if (rand.nextInt(100) <= 10) {
                for (Exit exit : location.getExits()) {
                    if (!(exit.getDestination().getGround().hasCapability(Element.GRASS))
                        && !(exit.getDestination().getGround() instanceof StructuralGrounds)) {
                        exit.getDestination().setGround(createSameElementGround());
                    }
                }
            }
        }
    }

    //Method used for night effect to create same element ground. 90:10 Grass:Tree because don't need that many trees
    /**
     * method to create the same type environment to the ground
     *
     * @param: no parameter is needed
     * @return: return pokemon type or remain the situation
     */
    public Ground createSameElementGround(){
        game.runtime.RandomSource rand = location.map().context().random;
        if (rand.nextInt(100) <= 10){
            return new Tree();
        } else {
            return new Hay();
        }
    }

    @Override
    public void registerInstance() {
        TimePerception.super.registerInstance();
    }
}
