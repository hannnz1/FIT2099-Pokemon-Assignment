package game.actors.pokemon;

import edu.monash.fit2099.engine.actions.Action;
import edu.monash.fit2099.engine.actions.ActionList;
import edu.monash.fit2099.engine.actions.DoNothingAction;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.displays.Display;
import edu.monash.fit2099.engine.positions.GameMap;
import edu.monash.fit2099.engine.positions.Location;
import edu.monash.fit2099.engine.weapons.IntrinsicWeapon;
import game.actions.CaptureAction;
import game.actions.ChestPoundingAction;
import game.actions.DancingAction;
import game.actions.SingingAction;
import game.behaviours.AttackBehaviour;
import game.conditions.Element;
import game.actions.AttackAction;
import game.behaviours.Behaviour;
import game.behaviours.WanderBehaviour;
import game.conditions.FavoriteAffection;
import game.conditions.Status;
import game.time.TimePerception;
import game.weapons.BackupWeaponsManager;
import game.weapons.SpecialAttackType;
import game.weapons.SpecialAttackWeapon;
import java.util.Map;
import java.util.TreeMap;

//    Treecko b
//    It is a Grass type Pokemon.
//    It spawns from a Tree (See REQ1)
//    It's favorite action is "Dancing" (See REQ4)
//    Intrinsic attack:  "tackle" attack that deals 10 HP damage with a 50% chance to hit
//    Special attack: If Treecko is standing on the Grass element ground, it will equip "Blade Cutter", a weapon that can deal 20 HP damage with a 90% chance to hit ("whips").

public class Treecko extends Pokemon implements TimePerception {
  private final Map<Integer, Behaviour> behaviours = new TreeMap<>();
  private final SpecialAttackWeapon specialWeapon;

  /**
   * Constructor.
   */
  public Treecko() {
    super("Treecko", 'b', 100);
    this.specialWeapon = BackupWeaponsManager.getInstance().createWeapon(SpecialAttackType.BLADE_CUTTER);
    this.addCapability(Element.GRASS);
    this.behaviours.put(10, new WanderBehaviour());
    this.registerInstance();
    this.behaviours.put(1, new AttackBehaviour());
    this.addCapability(FavoriteAffection.DANCING);
  }

  /**
   * @param otherActor the Actor that might perform an action.
   * @param direction  String representing the direction of the other Actor
   * @param map        current GameMap
   * @return list of actions
   */
  @Override
  public ActionList allowableActions(Actor otherActor, String direction, GameMap map) {
    ActionList actions = new ActionList();
    if(otherActor.hasCapability(Status.IS_POKEMON)) {
      actions.add(new AttackAction(this, direction));
    }
    if(otherActor.hasCapability(Status.TRAINER_POKEBALL)){
      actions.add(new CaptureAction(this));
    }
    if(otherActor.hasCapability(Status.TRAINER)) {
      actions.add(new SingingAction(this));
      actions.add(new ChestPoundingAction(this));
      actions.add(new DancingAction(this));
    }
    return actions;
  }

  /**
   * By using behaviour loops, it will decide what will be the next action automatically.
   *
   * @see Actor#playTurn(ActionList, Action, GameMap, Display)
   */
  @Override
  public Action playTurn(ActionList actions, Action lastAction, GameMap map, Display display) {
    Location here = map.locationOf(this);
    if(here.getGround().hasCapability(Element.GRASS)) {
      this.toggleWeapon(true);}
    else {
      this.toggleWeapon(false);
    }
    for (Behaviour behaviour : behaviours.values()) {
      Action action = behaviour.getAction(this, map);
      if (action != null)
        return action;
    }
    return new DoNothingAction();
  }
  //    It's favorite action is "Dancing" (See REQ4)


  //    Intrinsic attack:  "tackle" attack that deals 10 HP damage with a 50% chance to hit
  @Override
  protected IntrinsicWeapon getIntrinsicWeapon() {
    return new IntrinsicWeapon(10, "tackle");  }

  //    Special attack: If Treecko is standing on the Grass element ground, it will equip "Blade Cutter", a weapon that can deal 20 HP damage with a 90% chance to hit ("whips").



  @Override
  public void toggleWeapon(boolean isEquipping) {
    setSpecialWeaponEquipped(specialWeapon, isEquipping);
  }

  //    When it is at day, at every turn:
  //    Treecko will be hurt by 5 hit points.
  @Override
  public void dayEffect() {
    super.hurt(5);
  }

  //    When it is at night, at every turn:
  //    Treecko will be healed by 5 points.
  @Override
  public void nightEffect() {
    super.heal(5);
  }

  @Override
  public void registerInstance() {
    TimePerception.super.registerInstance();
  }

//    default void registerInstance(){
//        TimePerceptionManager.getInstance().append(this);
//    }

}
