package game.actions;

import java.util.Random;

import edu.monash.fit2099.engine.actions.Action;
import edu.monash.fit2099.engine.actions.ActionList;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.positions.GameMap;
import edu.monash.fit2099.engine.items.Item;
import edu.monash.fit2099.engine.weapons.Weapon;

/**
 * An Action to attack another Actor.
 * Created by:
 *
 * @author Riordan D. Alfredo
 * Modified by:
 */
public class AttackAction extends Action {
public Actor getTarget() { return target; }


    /**
     * The Actor that is to be attacked
     */
    protected Actor target;

    /**
     * The direction of incoming attack.
     */
    protected String direction;

    /**
     * Random number generator
     */
    protected Random rand = new Random();

    /**
     * Constructor.
     *
     * @param target the Actor to attack
     */
    public AttackAction(Actor target, String direction) {
        this.target = target;
        this.direction = direction;
    }

    @Override
    public String execute(Actor actor, GameMap map) {

        if(target instanceof game.actors.npc.NPC)return "友好 NPC 不能被攻击。";

        Weapon weapon = actor.getWeapon();

        if (!(map.context().random.nextInt(100) <= weapon.chanceToHit())) {
            map.context().event("MISS", actor, target, "攻击落空");
            return actor + "对" + target + "的攻击落空了。";
        }

        int damage = weapon.damage();
        String result = actor + "攻击了" + target + "，造成 " + damage + " 点伤害。";
        target.hurt(damage);
        map.context().event("ATTACK", actor, target, "命中：" + damage);
        if (!target.isConscious()) {
            ActionList dropActions = new ActionList();
            // drop all items
            for (Item item : target.getInventory())
                dropActions.add(item.getDropAction(actor));
            for (Action drop : dropActions)
                drop.execute(target, map);
            // remove actor
            map.removeActor(target);
            result += System.lineSeparator() + target + "失去了战斗能力。";
        }

        return result;
    }

    @Override
    public String menuDescription(Actor actor) {
        return actor + " attacks " + target + " at " + direction;
    }
}
