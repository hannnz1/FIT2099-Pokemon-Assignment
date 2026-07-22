package game.weapons;

import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.items.Item;
import game.actions.AttackAction;

import java.util.ArrayList;

/**
 * Created by:
 * @author Riordan D. Alfredo
 * Modified by:
 *
 * TODO: Use this class to store Pokemon's weapons (special attack) permanently.
 * If a Pokemon needs to use a weapon, put it into that Pokemon's inventory.
 * @see Actor#getWeapon() method.
 * @see AttackAction uses getWeapon() in the execute() method.
 */
public class BackupWeaponsManager {

    public static BackupWeaponsManager backupWeaponsManager = null;
    private static ArrayList<SpecialAttackWeapon> weapons;
    private BackupWeaponsManager(){}

    public static BackupWeaponsManager getInstance(){
        if (backupWeaponsManager == null) {
            weapons = new ArrayList<>();
            backupWeaponsManager = new BackupWeaponsManager();
            SpecialAttackWeapon ember =  new SpecialAttackWeapon("Ember",   'q',    30,  "sparks", 65);
            weapons.add(ember);
            SpecialAttackWeapon waterBlast =  new SpecialAttackWeapon("Water Blast", 'w',  25,  "burbles", 85);
            weapons.add(waterBlast);
            SpecialAttackWeapon bladeCutter =  new SpecialAttackWeapon("Blade Cutter", 'a', 20, "whips", 90);
            weapons.add(bladeCutter);
        }
        return backupWeaponsManager;
    }

    public Item getSpecialAttackWeapon(int index){
        return weapons.get(index);
    }










}
