package game.actors.pokemon;

import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.items.Item;
import game.conditions.Status;

import java.util.Objects;

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

  public abstract void toggleWeapon(boolean isEquipping);

  /** Equips or removes one Pokemon-owned special weapon without duplicates. */
  protected final void setSpecialWeaponEquipped(Item specialWeapon, boolean isEquipping) {
    Objects.requireNonNull(specialWeapon, "special weapon cannot be null");
    boolean equipped = getInventory().contains(specialWeapon);
    if (isEquipping && !equipped) {
      addItemToInventory(specialWeapon);
    } else if (!isEquipping && equipped) {
      removeItemFromInventory(specialWeapon);
    }
  }
}

