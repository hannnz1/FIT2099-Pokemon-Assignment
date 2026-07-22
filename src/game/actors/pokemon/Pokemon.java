package game.actors.pokemon;

import edu.monash.fit2099.engine.actors.Actor;
import game.actions.SingingAction;
import game.conditions.Status;

public abstract class Pokemon extends Actor {

  /**
   * Constructor.
   *
   * @param name        the name of the Actor
   * @param displayChar the character that will represent the Actor in the display
   * @param hitPoints   the Actor's starting hit points
   */
  public Pokemon(String name, char displayChar, int hitPoints) {
    super(name, displayChar, hitPoints);
    this.addCapability(Status.IS_POKEMON);

  }

  @Override
  public String toString() {
    return super.toString() + this.printHp() + "(Ap: " + this.getWeapon().damage() + ")";
  }

  @Override
  public void creatIntrinsicWeapon(){}

  public void toggleWeapon(boolean isEquipping) {}
}


