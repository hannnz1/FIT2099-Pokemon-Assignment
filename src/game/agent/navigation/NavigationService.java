package game.agent.navigation;

import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.actions.MoveActorAction;
import edu.monash.fit2099.engine.positions.*;
import game.agent.action.ActionResult;
import java.util.*;
import java.util.function.*;

/**
 * Java port of AI Town movement.ts route search / predecessor reconstruction.
 * Copyright (c) 2023 a16z-infra. MIT; see third_party/ai-town/LICENSE.
 * Uses actual engine exits with unit cost and zero heuristic (Dijkstra), since
 * eight-way movement and custom exits invalidate the upstream Manhattan heuristic.
 * No partial-path fallback. Must run on the authoritative world thread.
 */
public final class NavigationService {
    private static final class Candidate {
        final Location location;
        final Candidate previous;
        final int cost;
        final long order;
        Candidate(Location location, Candidate previous, int cost, long order) {
            this.location = location; this.previous = previous; this.cost = cost; this.order = order;
        }
    }
    /** Excludes origin. Empty means no route, or already at destination. */
    public List<Location> findRoute(Actor actor, GameMap map, Location destination, Predicate<Location> allowed) {
        Objects.requireNonNull(actor); Objects.requireNonNull(map); Objects.requireNonNull(allowed);
        if (destination == null || destination.map() != map || !map.contains(actor) || !allowed.test(destination)) return Collections.emptyList();
        Location origin = map.locationOf(actor);
        if (origin.equals(destination)) return Collections.emptyList();
        if (!destination.canActorEnter(actor)) return Collections.emptyList();
        PriorityQueue<Candidate> queue = new PriorityQueue<>(Comparator.comparingInt((Candidate c) -> c.cost).thenComparingLong(c -> c.order));
        Map<Location,Integer> best = new HashMap<>();
        queue.add(new Candidate(origin, null, 0, 0)); best.put(origin,0);
        long order = 1;
        while (!queue.isEmpty()) {
            Candidate current = queue.remove();
            if (current.cost != best.get(current.location)) continue;
            if (current.location.equals(destination)) {
                LinkedList<Location> result = new LinkedList<>();
                for (Candidate c = current; c.previous != null; c = c.previous) result.addFirst(c.location);
                return Collections.unmodifiableList(result);
            }
            for (Exit exit : current.location.getExits()) {
                Location next = exit.getDestination();
                if (next.map() != map || !allowed.test(next) || !next.canActorEnter(actor)) continue;
                int cost = current.cost + 1;
                if (best.containsKey(next) && best.get(next) <= cost) continue;
                best.put(next,cost); queue.add(new Candidate(next,current,cost,order++));
            }
        }
        return Collections.emptyList();
    }
    /** Approach a real NPC through its nearest currently reachable, authorized neighbor. */
    public ActionResult approach(Actor actor, GameMap map, Actor target, Predicate<Location> allowed, BooleanSupplier active) {
        if(!active.getAsBoolean())return interrupted();
        if(!map.contains(actor)||!map.contains(target))return ActionResult.rejected("ACTOR_NOT_ON_MAP");
        Location origin=map.locationOf(actor),best=null;int distance=Integer.MAX_VALUE;
        for(Exit exit:map.locationOf(target).getExits()){
            Location candidate=exit.getDestination();if(candidate.map()!=map||!allowed.test(candidate))continue;
            if(candidate.equals(origin))return step(actor,map,origin,allowed,active);
            List<Location> route=findRoute(actor,map,candidate,allowed);
            if(!route.isEmpty()&&route.size()<distance){best=candidate;distance=route.size();}
        }
        return best==null?ActionResult.rejected("NO_PATH"):step(actor,map,best,allowed,active);
    }
    /** Executes at most one legal exit; caller decides when to advance the world clock. */
    public ActionResult step(Actor actor, GameMap map, Location destination, Predicate<Location> allowed, BooleanSupplier active) {
        Objects.requireNonNull(active); Objects.requireNonNull(allowed);
        if (!active.getAsBoolean()) return interrupted();
        if (destination == null || destination.map() != map) return ActionResult.rejected("INVALID_DESTINATION");
        if (!map.contains(actor)) return ActionResult.rejected("ACTOR_NOT_ON_MAP");
        if (!allowed.test(destination)) return ActionResult.rejected("AREA_RESTRICTED");
        if (map.locationOf(actor).equals(destination)) return position(ActionResult.Status.SUCCESS,"ARRIVED",destination);
        List<Location> route = findRoute(actor,map,destination,allowed);
        if (route.isEmpty()) return ActionResult.rejected("NO_PATH");
        Location next = route.get(0);
        if (!active.getAsBoolean()) return interrupted();
        if (!allowed.test(next) || !next.canActorEnter(actor)) return ActionResult.rejected("PATH_BLOCKED");
        Exit step = null;
        for (Exit exit : map.locationOf(actor).getExits()) if (exit.getDestination().equals(next)) { step = exit; break; }
        if (step == null) return ActionResult.rejected("PATH_CHANGED");
        new MoveActorAction(next,step.getName()).execute(actor,map);
        boolean arrived = next.equals(destination);
        return position(arrived ? ActionResult.Status.SUCCESS : ActionResult.Status.IN_PROGRESS, arrived ? "ARRIVED" : "MOVED",next);
    }
    private ActionResult interrupted() { return ActionResult.of(ActionResult.Status.INTERRUPTED,"INTERRUPTED",Collections.emptyMap()); }
    private ActionResult position(ActionResult.Status status,String code,Location location) {
        Map<String,String> data = new LinkedHashMap<>(); data.put("x",Integer.toString(location.x())); data.put("y",Integer.toString(location.y()));
        return ActionResult.of(status,code,data);
    }
}
