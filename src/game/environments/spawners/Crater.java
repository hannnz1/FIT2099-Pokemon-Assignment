package game.environments.spawners;

import edu.monash.fit2099.engine.positions.Location;
import game.conditions.Element;
import game.actors.pokemon.Torchic;
import java.util.Random;

public class Crater extends SpawnerGrounds {
  //  Crater has a 10% chance of spawning a Torchic (see REQ2).
  public static int percentSpawn = 10;

  public Crater() {
    super('C');
    this.addCapability(Element.FIRE);
  }

  //  Crater c (capital C) is a spawning ground. Crater and Lava have Fire element. At every turn:
  /**
   * go through one day and night turn, made some affection to special pokemons or
   * ground things
   * @param location: current game map, prepare the extension Tree area
   * @return: no return is needed
   */
  @Override
  public void tick(Location location) {
    game.runtime.RandomSource rand = location.map().context().random;
    //  Crater has a 10% chance of spawning a Torchic (see REQ2).
    if(rand.nextInt(100) <= percentSpawn && !location.containsAnActor()){
      location.addActor(new Torchic());
    }
  }

}
