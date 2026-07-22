package game.environments;

import edu.monash.fit2099.engine.positions.Ground;
import game.conditions.Element;

public class Hay extends Ground {

  /**
   * Constructor.
   *
   *
   */
  public Hay() {
    super(',');
    this.addCapability(Element.GRASS);
  }

}
