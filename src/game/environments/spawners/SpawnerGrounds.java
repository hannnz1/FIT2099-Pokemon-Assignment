package game.environments.spawners;

import edu.monash.fit2099.engine.positions.Ground;

public abstract class SpawnerGrounds extends Ground {

  /**
   * Constructor.
   *
   * @param displayChar character to display for this type of terrain
   */
  public SpawnerGrounds(char displayChar) {
    super(displayChar);
  }



}
