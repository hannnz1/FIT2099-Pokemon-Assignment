package game.environments.spawners;

import edu.monash.fit2099.engine.positions.Exit;
import edu.monash.fit2099.engine.positions.Location;
import game.conditions.Element;
import game.actors.pokemon.Mudkip;
import java.util.Random;

public class Waterfall extends SpawnerGrounds {
  //  Waterfall has a 20% chance of spawning a Mudkip (see REQ2)
  public static int percentSpawn = 20;

  /**
   * Constructor.
   */
  public Waterfall() {
    super('W');
    this.addCapability(Element.WATER);
  }

  //  Waterfall is a spawning ground. Waterfall and Puddle have Water element. At every turn:
  /**
   * go through one day and night turn, made some affection to special pokemons or
   * ground things
   * @param location: current game map, prepare the extension Tree area
   * @return: no return is needed
   */
  @Override
  public void tick(Location location) {
    //  IF there are also at least two (2) WATER element grounds in its surrounding.
    int count = 0;
    for (Exit exit : location.getExits()) {
      if (exit.getDestination().getGround().hasCapability(Element.WATER)) {
        count += 1;
        //  Waterfall has a 20% chance of spawning a Mudkip (see REQ2)
        Random rand = new Random();
        if (count >= 2 && rand.nextInt(100) <= percentSpawn && !location.containsAnActor()) {
          location.addActor(new Mudkip());
          break;
        }
      }
    }
  }
}
