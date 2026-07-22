package game.actors.pokemon;

import static game.weapons.BackupWeaponsManager.backupWeaponsManager;

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
import java.util.HashMap;
import java.util.Map;


//    Mudkip s
//    It is a Water type Pokemon.
//    It spawns from a Waterfall (See REQ1)
//    It's favorite action is "Chest Pounding" (See REQ4)
//    Intrinsic attack: "tackle" attack that deals 10 HP damage with a 50% chance to hit
//    Special attack: If Mudkip is standing on the Water element ground or the enemy has a Fire element, it will equip "Water Blast", a weapon that can deal 25 HP damage with an 80% chance to hit ( "burbles").
public class Mudkip extends Pokemon implements TimePerception {
  //FIXME: Change it to a sorted map (is it TreeMap? HashMap? LinkedHashMap?)
  private final Map<Integer, Behaviour> behaviours = new HashMap<>(); // priority, behaviour

  /**
   * Constructor.
   */
  public Mudkip() {
    super("Mudkip", 's', 100);
    // HINT: add more relevant behaviours here
    this.addCapability(Element.WATER);
    this.behaviours.put(10, new WanderBehaviour());
    this.registerInstance();
    this.behaviours.put(1, new AttackBehaviour()); // not sure for now: priority, behaviour
    this.addCapability(FavoriteAffection.CHEST_POUNDING);
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
    if(here.getGround().hasCapability(Element.WATER)) {
      this.toggleWeapon(true);}
    else {
      this.toggleWeapon(false);
    }
    //    for (Exit exit : here.getExits()) {
//      if (exit.getDestination().containsAnActor()) {
//        if (exit.getDestination().getActor().findCapabilitiesByType(Element.class).stream().filter(Element.FIRE).) {
//          this.toggleWeapon(true);
//          break;
//        }    else {
//          this.toggleWeapon(false);
//        }
    for (Behaviour behaviour : behaviours.values()) {
      Action action = behaviour.getAction(this, map);
      if (action != null)
        return action;
    }
    return new DoNothingAction();
  }

  //    It's favorite action is "Chest Pounding" (See REQ4)
  //    Intrinsic attack: "tackle" attack that deals 10 HP damage with a 50% chance to hit
  @Override
  protected IntrinsicWeapon getIntrinsicWeapon() {
    return new IntrinsicWeapon(10, "tackle");  }
  //    Special attack: If Mudkip is standing on the Water element ground or the enemy has a Fire element, it will equip "Water Blast", a weapon that can deal 25 HP damage with an 80% chance to hit ( "burbles").

  /**
   * @param isEquipping FIXME: develop a logic to toggle weapon (put a selected weapon to the inventory - used!);
   */
  public void toggleWeapon(boolean isEquipping) {
    if (isEquipping) {
      this.addItemToInventory(backupWeaponsManager.getSpecialAttackWeapon(1));
    } else {
      this.removeItemFromInventory(backupWeaponsManager.getSpecialAttackWeapon(1));
    }
  }

  //    When it is at day, at every turn:
  //    Mudkip will be hurt by 15 hit points.
  @Override
  public void dayEffect() {
    super.hurt(15);
  }

  //    When it is at night, at every turn:
  //    Mudkip will be healed by 10 hit points.
  @Override
  public void nightEffect() {
    super.heal(10);
  }

  @Override
  public void registerInstance() {
    TimePerception.super.registerInstance();
  }

//    default void registerInstance(){
//        TimePerceptionManager.getInstance().append(this);
//    }
}

