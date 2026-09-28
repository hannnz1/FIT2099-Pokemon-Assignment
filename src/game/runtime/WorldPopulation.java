package game.runtime;

import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.positions.*;
import game.actors.npc.NPC;
import game.actors.pokemon.Pokemon;
import game.environments.*;
import game.environments.structures.Floor;
import game.items.Candy;
import java.util.*;

/** Counts the current map, never inventories or other sessions. Called inside a turn. */
public final class WorldPopulation {
    public static final int MAX_PER_SPECIES=3;
    public static final int MAX_CANDY=2;
    private WorldPopulation() {}

    public static boolean trySpawn(Location location,Pokemon pokemon) {
        if(!location.canActorEnter(pokemon))return false;
        int count=0;GameMap map=location.map();
        for(int y:map.getYRange())for(int x:map.getXRange()) {
            Location cell=map.at(x,y);
            if(cell.containsAnActor()&&cell.getActor().getClass()==pokemon.getClass()&&++count>=MAX_PER_SPECIES)return false;
        }
        location.addActor(pokemon);return true;
    }

    public static int candyCount(GameMap map) {
        int count=0;
        for(int y:map.getYRange())for(int x:map.getXRange())
            for(edu.monash.fit2099.engine.items.Item item:map.at(x,y).getItems())if(item instanceof Candy)count++;
        return count;
    }

    public static boolean canPlaceCandy(Location location) {
        return candyCount(location.map())<MAX_CANDY&&location.getItems().stream().noneMatch(item->item instanceof Candy);
    }

    public static boolean tryPlaceCandy(Location location,Candy candy) {
        if(!canPlaceCandy(location))return false;
        location.addItem(candy);return true;
    }

    /** Uniform sampling without replacement from reachable, safe, empty cells. */
    public static void replenishCandy(GameSession session) {
        GameMap map=session.map();Actor player=session.player();
        int missing=MAX_CANDY-candyCount(map);
        if(missing<=0||!map.contains(player)||!player.isConscious())return;
        List<Location> candidates=new ArrayList<>();Set<Location> visited=new HashSet<>();
        Deque<Location> queue=new ArrayDeque<>();Location start=map.locationOf(player);queue.add(start);visited.add(start);
        while(!queue.isEmpty()) {
            Location cell=queue.removeFirst();Ground ground=cell.getGround();
            if(!cell.containsAnActor()&&cell.getItems().isEmpty()&&(ground instanceof Dirt||ground instanceof Hay||ground instanceof Floor))candidates.add(cell);
            for(Exit exit:cell.getExits()) {
                Location next=exit.getDestination();
                if(GridMovement.isCardinal(cell,next)&&next.getGround().canActorEnter(player)&&!(next.containsAnActor()&&next.getActor() instanceof NPC)&&visited.add(next))queue.addLast(next);
            }
        }
        while(missing>0&&!candidates.isEmpty()) {
            Location cell=candidates.remove(session.context().random.nextInt(candidates.size()));
            if(tryPlaceCandy(cell,new Candy()))missing--;
        }
    }
}
