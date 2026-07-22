package game.actors.pokemon;


import edu.monash.fit2099.engine.actions.Action;
import edu.monash.fit2099.engine.actions.ActionList;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.displays.Display;
import edu.monash.fit2099.engine.actions.DoNothingAction;
import edu.monash.fit2099.engine.items.Item;
import edu.monash.fit2099.engine.positions.GameMap;
import edu.monash.fit2099.engine.positions.Location;
import edu.monash.fit2099.engine.weapons.Weapon;
import game.actions.AttackAction;
import game.actions.CaptureAction;
import game.actions.ChestPoundingAction;
import game.actions.DancingAction;
import game.actions.SingingAction;
import game.behaviours.AttackBehaviour;
import game.conditions.Element;
import game.behaviours.Behaviour;
import game.behaviours.WanderBehaviour;

import game.conditions.FavoriteAffection;
import game.conditions.Status;
import game.time.TimePerception;
import game.weapons.IntrinsicScratchWeapon;
import game.weapons.BackupWeaponsManager;
import game.weapons.SpecialAttackType;
import game.weapons.SpecialAttackWeapon;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by:
 *
 * @author Riordan D. Alfredo
 * Modified by: Ian K. Felix
 */

//    Torchic c
//    It is a Fire type Pokemon.
//    It spawns from a Crater (See REQ1)
//    It's favorite action is "Singing" (See REQ4)
//    Intrinsic attack: A "scratch" attack that deals 10 HP damage with a 50% chance to hit (UPDATE (30/8): it was 55% chance to hit)
//    Special attack: If Torchic is standing on the Fire element ground, it will equip "Ember", a weapon that can deal 30 HP damage with a 65% chance to hit ( "sparks").
//    Currently, this Pokemon is uncatchable/ cannot be caught with a Pokeball/Greatball/Masterball  (See REQ4).
public class Torchic extends Pokemon implements TimePerception {
    private final Map<Integer, Behaviour> behaviours = new TreeMap<>();
    private final SpecialAttackWeapon specialWeapon;

    /**
     * Constructor.
     */
    public Torchic() {
        super("Torchic", 'c', 100);
        this.specialWeapon = BackupWeaponsManager.getInstance().createWeapon(SpecialAttackType.EMBER);
        this.addCapability(Element.FIRE);
        this.behaviours.put(10, new WanderBehaviour());
        this.registerInstance();
        this.behaviours.put(1, new AttackBehaviour());
        this.addCapability(FavoriteAffection.SINGING);
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
        if(otherActor.hasCapability(Status.TRAINER)) {
            actions.add(new SingingAction(this));
            actions.add(new ChestPoundingAction(this));
            actions.add(new DancingAction(this));
        }
        return actions;
    }

    /**
     * By using behaviour loops, it will decide what will be the next action automatically. Also checks to equip special weapons.
     *
     * @see Actor#playTurn(ActionList, Action, GameMap, Display)
     */
    @Override
    public Action playTurn(ActionList actions, Action lastAction, GameMap map, Display display) {
        Location here = map.locationOf(this);
        if(here.getGround().hasCapability(Element.FIRE)) {
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

    @Override
    public void toggleWeapon(boolean isEquipping) {
        setSpecialWeaponEquipped(specialWeapon, isEquipping);
    }

    @Override
    public Weapon getWeapon() {
        for(Item item : getInventory()){
            if(item.asWeapon() != null){
                return item.asWeapon();
            }
        }
        return new IntrinsicScratchWeapon();
    }

    //    When it is at day, at every turn:
    //    Torchic will be healed by 20 hit points.
    @Override
    public void dayEffect() {
        super.heal(20);
    }

    //    When it is at night, at every turn:
    //    Torchic will be hurt by 15 hit points.
    @Override
    public void nightEffect() {
        super.hurt(15);
    }

    @Override
    public void registerInstance() {
        TimePerception.super.registerInstance();
    }


}
