package game.weapons;

import edu.monash.fit2099.engine.weapons.Weapon;
import edu.monash.fit2099.engine.weapons.WeaponItem;

public class SpecialAttackWeapon extends WeaponItem {

    /**
     * a model to create the weapons for the pokemon to do the special attack
     * since weaponsitem is abstract class we can not store weapons as a arraylist in backupweaponsMnager
     * @param: name: the string of the name of the weapon
     *         damage: damage of the weapon
     *         hitrate: hitrate of the weapon
     *         verb: the words to describe the attack
     */
    public SpecialAttackWeapon(String name, char displayChar, int damage, String verb, int hitRate){
        super(name, displayChar, damage, verb, hitRate);
    }

}
