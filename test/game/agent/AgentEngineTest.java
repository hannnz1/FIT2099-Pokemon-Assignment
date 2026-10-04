package game.agent;

import edu.monash.fit2099.engine.actions.*;
import edu.monash.fit2099.engine.displays.Display;
import edu.monash.fit2099.engine.positions.*;
import game.agent.runtime.*;
import game.agent.tools.*;
import game.environments.Dirt;
import game.conditions.Element;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AgentEngineTest {
    @Test void agentRetainsMudkipWaterWeaponRules() {
        GameMap map=new GameMap(new FancyGroundFactory(new Dirt()),Arrays.asList(".."));
        new World(new Display()).addGameMap(map); AgentMudkip actor=new AgentMudkip(); map.addActor(actor,map.at(0,0));
        AgentTask task=new AgentTask("weapon"); task.start();
        AgentLoop loop=new AgentLoop(task,"wait",new GameToolRegistry(r->null),Runnable::run,c->null,
            Collections::emptyMap,()->false,100,5,10,3);
        actor.attach(loop,map,()->0); map.at(0,0).getGround().addCapability(Element.WATER);
        actor.playTurn(new ActionList(),null,map,new Display()); assertEquals(25,actor.getWeapon().damage());
        map.moveActor(actor,map.at(1,0)); actor.playTurn(new ActionList(),null,map,new Display());
        assertEquals(10,actor.getWeapon().damage());
    }
    @Test void engineTurnUsesAgentInsteadOfWanderAndDuplicateExecuteIsHarmless() {
        GameMap map=new GameMap(new FancyGroundFactory(new Dirt()),Arrays.asList("...."));
        new World(new Display()).addGameMap(map); AgentMudkip actor=new AgentMudkip(); map.addActor(actor,map.at(0,0));
        AgentTask task=new AgentTask("engine"); task.start(); GameToolRegistry registry=new GameToolRegistry(r->null);
        GameTools.register(registry,actor,map,Collections.singletonMap("target",map.at(3,0)),p->true,()->true);
        AgentLoop loop=new AgentLoop(task,"reach target",registry,Runnable::run,
            c->new ToolRequest("chosen","move_to",Collections.singletonMap("locationId","target")),
            Collections::emptyMap,()->map.locationOf(actor)==map.at(3,0),100,5,10,3);
        AtomicInteger clock=new AtomicInteger(); actor.attach(loop,map,()->clock.getAndIncrement());
        Action first=actor.playTurn(new ActionList(),null,map,new Display()); first.execute(actor,map);
        assertEquals(map.at(0,0),map.locationOf(actor));
        Action second=actor.playTurn(new ActionList(),first,map,new Display()); second.execute(actor,map); second.execute(actor,map);
        assertEquals(map.at(1,0),map.locationOf(actor));
        for(int i=0;i<3;i++) actor.playTurn(new ActionList(),second,map,new Display()).execute(actor,map);
        assertEquals(map.at(3,0),map.locationOf(actor)); assertEquals(AgentTask.State.COMPLETED,task.getState());
        assertThrows(IllegalStateException.class,()->actor.attach(loop,map,()->0));
    }
}
