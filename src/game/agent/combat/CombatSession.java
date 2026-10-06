package game.agent.combat;

import edu.monash.fit2099.engine.actions.Action;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.positions.*;
import game.actions.AttackAction;
import game.actions.CaptureAction;
import game.actors.pokemon.Pokemon;
import game.agent.action.ActionResult;
import game.items.balls.Pokeball;
import game.conditions.Element;
import java.util.*;
import java.util.function.*;

/** World-thread adapter. Captures belong to the trainer; the companion delegates
 * within actual adjacency. Original species allowableActions decides capturability.
 * Ordinary Pokeballs are unlimited in this engine; no HP/affection rule is invented. */
public final class CombatSession {
    private final Actor actor,trainer;
    private final GameMap map;
    private final Map<String,Pokemon> targets;
    private final BooleanSupplier active,noBattle,preserveCompanion;
    private final Predicate<Location> allowed;
    private final IntSupplier turnDamage;
    private final Runnable defeated;
    public CombatSession(Actor actor,Actor trainer,GameMap map,Map<String,Pokemon> targets,
                         BooleanSupplier active,BooleanSupplier noBattle,Predicate<Location> allowed,Runnable defeated){
        this(actor,trainer,map,targets,active,noBattle,allowed,defeated,()->false);
    }
    public CombatSession(Actor actor,Actor trainer,GameMap map,Map<String,Pokemon> targets,BooleanSupplier active,BooleanSupplier noBattle,Predicate<Location> allowed,Runnable defeated,BooleanSupplier preserveCompanion){
        this(actor,trainer,map,targets,active,noBattle,allowed,defeated,preserveCompanion,()->0);
    }
    public CombatSession(Actor actor,Actor trainer,GameMap map,Map<String,Pokemon> targets,BooleanSupplier active,BooleanSupplier noBattle,Predicate<Location> allowed,Runnable defeated,BooleanSupplier preserveCompanion,IntSupplier turnDamage){
        this.turnDamage=Objects.requireNonNull(turnDamage);
        this.preserveCompanion=Objects.requireNonNull(preserveCompanion);
        this.actor=Objects.requireNonNull(actor);this.trainer=Objects.requireNonNull(trainer);this.map=Objects.requireNonNull(map);
        this.targets=Collections.unmodifiableMap(new LinkedHashMap<>(targets));this.active=Objects.requireNonNull(active);
        this.noBattle=Objects.requireNonNull(noBattle);this.allowed=Objects.requireNonNull(allowed);this.defeated=Objects.requireNonNull(defeated);
    }
    public ActionResult attack(String id){
        String denied=guard(id);if(denied!=null)return ActionResult.rejected(denied);
        if(noBattle.getAsBoolean())return ActionResult.rejected("NO_ACTIVE_BATTLE");
        Pokemon target=targets.get(id);prepareWeapon(actor,target);prepareWeapon(target,actor);
        if(preserveCompanion.getAsBoolean()&&actor.getHitPoints()<=target.getWeapon().damage()+turnDamage.getAsInt())return ActionResult.rejected("PARTNER_EXHAUSTED");
        Action attack=action(target,actor,AttackAction.class);if(attack==null)return ActionResult.rejected("ATTACK_NOT_ALLOWED");
        int before=target.getHitPoints(),actorBefore=actor.getHitPoints();attack.execute(actor,map);
        int damage=before-target.getHitPoints();
        if(map.contains(target)&&target.isConscious()&&map.contains(actor)&&actor.isConscious()){
            Action counter=action(actor,target,AttackAction.class);if(counter!=null)counter.execute(target,map);
        }
        if(!map.contains(actor)||!actor.isConscious())defeated.run();
        Map<String,String> data=new LinkedHashMap<>();data.put("targetId",id);data.put("damage",Integer.toString(damage));
        data.put("targetHp",Integer.toString(target.getHitPoints()));data.put("actorHp",Integer.toString(actor.getHitPoints()));
        data.put("retaliationDamage",Integer.toString(actorBefore-actor.getHitPoints()));
        return ActionResult.of(ActionResult.Status.SUCCESS,!map.contains(actor)?"COMPANION_DEFEATED":!map.contains(target)?"TARGET_DEFEATED":damage==0?"ATTACK_MISSED":"ATTACKED",data);
    }
    public ActionResult capture(String id){
        String denied=guard(id);if(denied!=null)return ActionResult.rejected(denied);
        Pokemon target=targets.get(id);Action capture=action(target,trainer,CaptureAction.class);
        if(capture==null)return ActionResult.rejected("NOT_CAPTURABLE");
        capture.execute(trainer,map);
        return ActionResult.of(ActionResult.Status.SUCCESS,"CAPTURED",Collections.singletonMap("targetId",id));
    }
    public boolean captured(String id){
        Pokemon target=targets.get(id);if(target==null)return false;
        return trainer.getInventory().stream().anyMatch(item->item instanceof Pokeball && ((Pokeball)item).containsPokemon() && ((Pokeball)item).getPokemon()==target);
    }
    public List<String> capturedIds(){List<String> ids=new ArrayList<>();for(String id:targets.keySet())if(captured(id))ids.add(id);return Collections.unmodifiableList(ids);}
    private String guard(String id){
        if(!active.getAsBoolean())return "TASK_INACTIVE";
        if(!map.contains(actor)||!actor.isConscious())return "ACTOR_UNAVAILABLE";
        Pokemon target=targets.get(id);if(target==null)return "UNKNOWN_TARGET";
        if(!map.contains(target)||!target.isConscious())return "TARGET_UNAVAILABLE";
        if(!allowed.test(map.locationOf(actor))||!allowed.test(map.locationOf(target)))return "AREA_RESTRICTED";
        for(Exit exit:map.locationOf(actor).getExits())if(exit.getDestination()==map.locationOf(target))return null;
        return "OUT_OF_REACH";
    }
    private Action action(Actor target,Actor performer,Class<? extends Action> type){
        // Converted field targets remain attackable by legacy Pokemon companions.
        if(type==AttackAction.class&&target instanceof game.agent.growth.GrowthPokemon&&performer.hasCapability(game.conditions.Status.IS_POKEMON))return new AttackAction(target,"adjacent");
        for(Action action:target.allowableActions(performer,"adjacent",map))if(type.isInstance(action))return action;
        return null;
    }
    private void prepareWeapon(Actor performer,Actor opponent){
        if(performer instanceof Pokemon){boolean equip=false;
            for(Element element:performer.findCapabilitiesByType(Element.class))if(map.locationOf(performer).getGround().hasCapability(element))equip=true;
            if(performer instanceof game.actors.pokemon.Mudkip && opponent.hasCapability(Element.FIRE))equip=true;
            ((Pokemon)performer).toggleWeapon(equip);
        }
    }
}