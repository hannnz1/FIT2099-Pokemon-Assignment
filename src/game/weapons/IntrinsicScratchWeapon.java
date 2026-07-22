package game.weapons;

import edu.monash.fit2099.engine.weapons.Weapon;

public final class IntrinsicScratchWeapon implements Weapon {

  //    Intrinsic attack: A "scratch" attack that deals 10 HP damage with a 50% chance to hit (UPDATE (30/8): it was 55% chance to hit)
  private final int damage;
  private final String verb;
  private final int hitRate;
  /**
   * create the intrinsic attack especially for scratch since only one poekmon has different
   * value for intrinsic attack
   * @param: no parameter is needed
   */
  public IntrinsicScratchWeapon() {
    this.damage = 10;
    this.verb = "scratch";
    this.hitRate = 55;
  }

  @Override
  public int damage() {
    return damage;
  }

  @Override
  public String verb() {
    return verb;
  }

  @Override
  public int chanceToHit() {
    return hitRate;
  }
}
