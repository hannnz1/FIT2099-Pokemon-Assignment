package game.environments.structures;

import edu.monash.fit2099.engine.positions.Ground;

public abstract class StructuralGrounds extends Ground {

  /**
   * Constructor.
   *
   * @param displayChar character to display for this type of terrain
   */
  public StructuralGrounds(char displayChar) {
    super(displayChar);
  }
}
