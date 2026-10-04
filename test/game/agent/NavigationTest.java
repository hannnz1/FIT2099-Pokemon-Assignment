package game.agent;

import edu.monash.fit2099.engine.positions.*;
import edu.monash.fit2099.engine.displays.Display;
import game.actors.Player;
import game.environments.Dirt;
import game.environments.structures.Wall;
import game.agent.navigation.NavigationService;
import game.agent.tools.*;
import game.agent.llm.Json;
import game.agent.action.ActionResult;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NavigationTest {
    private GameMap map(String... rows) {
        GameMap map = new GameMap(new FancyGroundFactory(new Dirt(), new Wall()), Arrays.asList(rows));
        new World(new Display()).addGameMap(map); return map;
    }
    @Test void routeCanInitiallyMoveAwayFromTargetToGetAroundWall() {
        GameMap map = map(".....", ".###.", ".#...", ".###.", ".....");
        Player actor = new Player("Ash", '@', 100); map.addActor(actor, map.at(0,2));
        List<Location> route = new NavigationService().findRoute(actor, map, map.at(2,2), p -> true);
        assertFalse(route.isEmpty()); assertEquals(map.at(2,2), route.get(route.size()-1));
        // (0,2)->(0,1)->(1,0)->(2,0)->(3,0)->(4,1)->(3,2)->(2,2)
        assertEquals(7, route.size());
        assertThrows(UnsupportedOperationException.class, () -> route.clear());
    }
    @Test void unreachableRestrictedAndForeignDestinationsAreRejected() {
        GameMap map = map(".#.", ".#.", ".#.");
        Player actor = new Player("Ash", '@', 100); map.addActor(actor, map.at(0,1));
        NavigationService nav = new NavigationService();
        assertFalse(nav.findRoute(actor,map,map.at(2,1),p->true).size()>0);
        assertEquals("NO_PATH", nav.step(actor,map,map.at(2,1),p->true,()->true).getCode());
        assertEquals("AREA_RESTRICTED", nav.step(actor,map,map.at(0,2),p->p.y()!=2,()->true).getCode());
        assertEquals("INVALID_DESTINATION", nav.step(actor,map,map(".").at(0,0),p->true,()->true).getCode());
        assertEquals(map.at(0,1), map.locationOf(actor));
    }
    @Test void eachStepRechecksOccupancyAndCancellation() {
        GameMap map = map("..."); Player actor = new Player("Ash", '@', 100); map.addActor(actor,map.at(0,0));
        NavigationService nav = new NavigationService();
        assertEquals(ActionResult.Status.IN_PROGRESS, nav.step(actor,map,map.at(2,0),p->true,()->true).getStatus());
        assertEquals(map.at(1,0),map.locationOf(actor));
        Player blocker = new Player("Blocker", 'b',100); map.addActor(blocker,map.at(2,0));
        assertEquals("NO_PATH", nav.step(actor,map,map.at(2,0),p->true,()->true).getCode());
        map.removeActor(blocker);
        assertEquals("INTERRUPTED", nav.step(actor,map,map.at(2,0),p->true,()->false).getCode());
        assertEquals(map.at(1,0),map.locationOf(actor));
        assertEquals("ARRIVED", nav.step(actor,map,map.at(2,0),p->true,()->true).getCode());
    }
    @Test void registeredMoveToolChangesActualGameAndRetryDoesNotMoveTwice() {
        GameMap map = map("...."); Player actor = new Player("Ash", '@',100); map.addActor(actor,map.at(0,0));
        GameToolRegistry registry = new GameToolRegistry(r -> null);
        GameTools.register(registry, actor, map, Collections.singletonMap("orchard", map.at(3,0)), p->true, ()->true);
        ToolRequest first = new ToolRequest("step-1","move_to",Collections.singletonMap("locationId","orchard"));
        registry.execute(first); registry.execute(first);
        assertEquals(map.at(1,0),map.locationOf(actor));
        assertEquals("UNKNOWN_LOCATION",registry.execute(new ToolRequest("bad","move_to",Collections.singletonMap("locationId","secret"))).getCode());
        assertEquals("1",registry.execute(new ToolRequest("read","observe",Collections.emptyMap())).getData().get("x"));
    }
    @Test void occupiedBerryTileApproachesWaitsAndEntersOnlyAfterNativeOccupantLeaves(){
        GameMap m=map(".....");Player a=new Player("Agent",'a',100),b=new Player("NPC",'b',100);m.addActor(a,m.at(0,0));m.addActor(b,m.at(3,0));GameToolRegistry tools=new GameToolRegistry(q->null);GameTools.register(tools,a,m,Collections.singletonMap("orchard",m.at(3,0)),p->true,()->true);
        for(int i=0;i<2;i++)assertEquals(ActionResult.Status.IN_PROGRESS,tools.execute(new ToolRequest("approach"+i,"move_to",Json.object("locationId","orchard"))).getStatus());
        assertEquals(m.at(2,0),m.locationOf(a));assertEquals("RESOURCE_WAITED",tools.execute(new ToolRequest("wait","move_to",Json.object("locationId","orchard"))).getCode());assertEquals(m.at(3,0),m.locationOf(b));assertEquals(m.at(2,0),m.locationOf(a));
        m.moveActor(b,m.at(4,0));assertEquals("ARRIVED",tools.execute(new ToolRequest("enter","move_to",Json.object("locationId","orchard"))).getCode());assertEquals(m.at(3,0),m.locationOf(a));
    }
    @Test void occupiedResourceWaitingCannotBypassAreaOrCancellation(){GameMap m=map("...");Player a=new Player("Agent",'a',100),b=new Player("NPC",'b',100);m.addActor(a,m.at(0,0));m.addActor(b,m.at(1,0));GameToolRegistry denied=new GameToolRegistry(q->null);GameTools.register(denied,a,m,Collections.singletonMap("orchard",m.at(1,0)),p->p.x()==0,()->true);assertEquals("AREA_RESTRICTED",denied.execute(new ToolRequest("area","move_to",Json.object("locationId","orchard"))).getCode());GameToolRegistry cancelled=new GameToolRegistry(q->null);GameTools.register(cancelled,a,m,Collections.singletonMap("orchard",m.at(1,0)),p->true,()->false);assertEquals("TASK_INACTIVE",cancelled.execute(new ToolRequest("cancel","move_to",Json.object("locationId","orchard"))).getCode());}
}
