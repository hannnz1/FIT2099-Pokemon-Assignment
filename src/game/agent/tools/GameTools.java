package game.agent.tools;

import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.items.Item;
import edu.monash.fit2099.engine.positions.*;
import game.agent.action.ActionResult;
import game.agent.navigation.NavigationService;
import java.util.*;
import java.util.function.*;

/** Minimal real-engine adapters. Bind one actor, map and authorized location catalog. */
public final class GameTools {
    private GameTools() { }
    public static void register(GameToolRegistry registry, Actor actor, GameMap map,
                                Map<String,Location> locations, Predicate<Location> allowed, BooleanSupplier active) {
        register(registry,actor,map,locations,allowed,active,Collections.emptyMap());
    }
    public static void register(GameToolRegistry registry, Actor actor, GameMap map,
                                Map<String,Location> locations, Predicate<Location> allowed, BooleanSupplier active,
                                Map<String,Actor> approachTargets) {
        Map<String,Actor> targets=Collections.unmodifiableMap(new LinkedHashMap<>(approachTargets));
        Objects.requireNonNull(actor); Objects.requireNonNull(map); Objects.requireNonNull(allowed); Objects.requireNonNull(active);
        Map<String,Location> catalog = Collections.unmodifiableMap(new LinkedHashMap<>(locations));
        NavigationService navigation = new NavigationService();
        registry.register(new ToolDefinition("move_to", "Move one world turn toward a known location; repeat with a new actionId while IN_PROGRESS",true,
                Collections.singletonMap("locationId",ToolParameter.string())), request -> {
            Location destination = catalog.get((String) request.getArguments().get("locationId"));
            if (destination == null) return ActionResult.rejected("UNKNOWN_LOCATION");
            Actor npc=targets.get((String)request.getArguments().get("locationId"));
            if(npc==null && Arrays.asList("orchard","alternative").contains(request.getArguments().get("locationId")) && destination.containsAnActor() && destination.getActor()!=actor){
                if(!active.getAsBoolean())return ActionResult.rejected("TASK_INACTIVE");
                if(!allowed.test(destination)||!allowed.test(map.locationOf(actor)))return ActionResult.rejected("AREA_RESTRICTED");
                // A roaming native NPC may occupy a resource tile. Approach, then spend real
                // world turns waiting for it to leave; never move the NPC or collect remotely.
                Location here=map.locationOf(actor);for(Exit exit:here.getExits())if(exit.getDestination()==destination)return ActionResult.of(ActionResult.Status.IN_PROGRESS,"RESOURCE_WAITED",Collections.emptyMap());
                Location best=null;int length=Integer.MAX_VALUE;for(Exit exit:destination.getExits()){Location point=exit.getDestination();List<Location> route=navigation.findRoute(actor,map,point,allowed);if(!route.isEmpty()&&route.size()<length){best=point;length=route.size();}}
                if(best==null)return ActionResult.rejected("NO_PATH");ActionResult result=navigation.step(actor,map,best,allowed,active);
                return result.getStatus()==ActionResult.Status.SUCCESS?ActionResult.of(ActionResult.Status.IN_PROGRESS,"RESOURCE_APPROACHED",result.getData()):result;
            }
            if(npc==null || map.locationOf(actor).equals(destination) || !navigation.findRoute(actor,map,destination,allowed).isEmpty())return navigation.step(actor,map,destination,allowed,active);
            return navigation.approach(actor,map,npc,allowed,active);
        });
        registry.register(new ToolDefinition("observe","Observe the actor's current tile only",false,Collections.emptyMap()),request -> {
            if (!map.contains(actor)) return ActionResult.rejected("ACTOR_NOT_ON_MAP");
            Location here = map.locationOf(actor);
            Map<String,String> data = new LinkedHashMap<>();
            data.put("x",Integer.toString(here.x())); data.put("y",Integer.toString(here.y()));
            data.put("ground",here.getGround().getClass().getSimpleName());
            data.put("items",summarize(here.getItems()));
            return ActionResult.of(ActionResult.Status.SUCCESS,"OBSERVED",data);
        });
        registry.register(new ToolDefinition("inspect_inventory","Inspect this actor's own inventory",false,Collections.emptyMap()),request ->
                ActionResult.of(ActionResult.Status.SUCCESS,"INVENTORY",Collections.singletonMap("items",summarize(actor.getInventory()))));
    }
    private static String summarize(List<Item> items) {
        StringJoiner joiner = new StringJoiner(", ");
        for (Item item : items) joiner.add(item.toString());
        return joiner.toString();
    }
}
