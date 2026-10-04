package game.agent;

import edu.monash.fit2099.engine.displays.Display;
import edu.monash.fit2099.engine.items.Item;
import edu.monash.fit2099.engine.positions.*;
import game.actors.Player;
import game.actors.npc.*;
import game.agent.quest.*;
import game.agent.runtime.AgentTask;
import game.agent.tools.*;
import game.agent.action.ActionResult;
import game.environments.Dirt;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class BerryQuestTest {
    static class Fixture {
        final GameMap map=new GameMap(new FancyGroundFactory(new Dirt()),Arrays.asList(".......",".......","......."));
        final Player actor=new Player("Ash",'@',100);
        final ProffesorOak professor=new ProffesorOak(); final Shopkeeper merchant=new Shopkeeper();
        final AgentTask task=new AgentTask("task"); final AtomicLong clock=new AtomicLong();
        final BerryQuestSession session;
        final GameToolRegistry tools=new GameToolRegistry(r->null);
        Fixture() { this(10,30); }
        Fixture(int capacity,long deadline) {
            new World(new Display()).addGameMap(map); map.addActor(actor,map.at(0,1));
            map.addActor(professor,map.at(6,1)); map.addActor(merchant,map.at(4,1)); task.start();
            session=new BerryQuestSession("owner",task,actor,map,professor,merchant,
                new BerryQuestSession.Rules(3,deadline,capacity,1000),5,4,1,true,clock::get);
            BerryQuestTools.register(tools,session);
        }
        void ground(int quantity) { for(int i=0;i<quantity;i++) map.locationOf(actor).addItem(new Berry()); }
        void carried(int quantity) { for(int i=0;i<quantity;i++) actor.addItemToInventory(new Berry()); }
        void nearMerchant() { map.moveActor(actor,map.at(3,1)); }
        void nearProfessor() { map.moveActor(actor,map.at(5,1)); }
        ToolRequest request(String id,String name,Object... pairs) {
            Map<String,Object> args=new LinkedHashMap<>(); for(int i=0;i<pairs.length;i+=2) args.put((String)pairs[i],pairs[i+1]);
            return new ToolRequest(id,name,args);
        }
    }
    @Test void pickupMovesRealObjectsAndRetryDoesNotDuplicate() {
        Fixture f=new Fixture(); f.ground(2); Item first=f.map.locationOf(f.actor).getItems().get(0);
        ToolRequest pickup=f.request("pick","pickup","itemId","BERRY","quantity",2);
        assertEquals("PICKED_UP",f.tools.execute(pickup).getCode()); f.tools.execute(pickup);
        assertEquals(2,f.actor.getInventory().size()); assertTrue(f.actor.getInventory().contains(first));
        assertTrue(f.map.locationOf(f.actor).getItems().isEmpty()); assertEquals(BerryQuestSession.QuestStatus.ACTIVE,f.session.getQuestStatus());
    }
    @Test void insufficientResourceCapacityAndInvalidItemHaveNoPartialEffects() {
        Fixture f=new Fixture(3,30); f.carried(2); f.ground(2);
        assertEquals("INVENTORY_FULL",f.session.pickup("BERRY",2).getCode()); assertEquals(2,f.map.locationOf(f.actor).getItems().size());
        assertEquals("RESOURCE_NOT_AVAILABLE",f.session.pickup("BERRY",3).getCode());
        assertEquals("UNKNOWN_ITEM",f.session.pickup("SECRET",1).getCode());
        assertEquals("INVALID_QUANTITY",f.session.pickup("BERRY",0).getCode()); assertEquals(2,f.actor.getInventory().size());
    }
    @Test void staleOrNonportableItemCannotBePickedUp() {
        Fixture f=new Fixture(); f.ground(1); f.map.locationOf(f.actor).removeItem(f.map.locationOf(f.actor).getItems().get(0));
        assertEquals("RESOURCE_NOT_AVAILABLE",f.session.pickup("BERRY",1).getCode());
        Berry berry=new Berry(); berry.togglePortability(); f.map.locationOf(f.actor).addItem(berry);
        assertEquals("RESOURCE_NOT_AVAILABLE",f.session.pickup("BERRY",1).getCode());
    }
    @Test void deliveryRechecksIdentityDistanceAndCountThenCompletesOnlyOnce() {
        Fixture f=new Fixture(); f.carried(2);
        assertEquals("UNKNOWN_QUEST",f.session.deliver("wrong","professor").getCode());
        assertEquals("UNKNOWN_TARGET",f.session.deliver(f.session.getQuestId(),"merchant").getCode());
        assertEquals("OUT_OF_REACH",f.session.deliver(f.session.getQuestId(),"professor").getCode());
        f.nearProfessor(); assertEquals("INSUFFICIENT_BERRIES",f.session.deliver(f.session.getQuestId(),"professor").getCode());
        f.carried(2); f.actor.addItemToInventory(new Item("keep",'k',true){});
        ToolRequest deliver=f.request("deliver","deliver","questId",f.session.getQuestId(),"targetNpcId","professor");
        assertEquals("QUEST_COMPLETED",f.tools.execute(deliver).getCode()); f.tools.execute(deliver);
        assertEquals(BerryQuestSession.QuestStatus.COMPLETED,f.session.getQuestStatus());
        assertEquals(AgentTask.State.COMPLETED,f.task.getState()); assertEquals(3,f.professor.getInventory().size());
        assertEquals(2,f.actor.getInventory().size());
        assertEquals("QUEST_NOT_ACTIVE",f.session.deliver(f.session.getQuestId(),"professor").getCode());
    }
    @Test void deadlineAndCancelledTaskPreventWorldChanges() {
        Fixture f=new Fixture(10,1); f.ground(1); assertTrue(f.session.advanceTurn());
        assertEquals("QUEST_EXPIRED",f.session.pickup("BERRY",1).getCode()); assertTrue(f.actor.getInventory().isEmpty());
        assertEquals(AgentTask.State.FAILED,f.task.getState());
        Fixture cancelled=new Fixture(); cancelled.ground(1); cancelled.task.cancel();
        assertEquals("TASK_INACTIVE",cancelled.session.pickup("BERRY",1).getCode()); assertTrue(cancelled.actor.getInventory().isEmpty());
    }
    @Test void pausedMissingAndDeadActorsCannotMutateWorld() {
        Fixture f=new Fixture(); f.ground(1); f.task.pause();
        assertEquals("TASK_INACTIVE",f.session.pickup("BERRY",1).getCode()); f.task.resume(); f.map.removeActor(f.actor);
        assertEquals("ACTOR_UNAVAILABLE",f.session.pickup("BERRY",1).getCode());
        Fixture dead=new Fixture(); dead.ground(1); dead.actor.hurt(1000);
        assertEquals("ACTOR_UNAVAILABLE",dead.session.pickup("BERRY",1).getCode());
    }
    @Test void toolSchemaRejectsForgedParametersAndNegativeCounts() {
        Fixture f=new Fixture(); f.ground(1);
        assertEquals("INVALID_ARGUMENTS",f.tools.execute(f.request("bad","pickup","itemId","BERRY","quantity",-1)).getCode());
        assertEquals("INVALID_ARGUMENTS",f.tools.execute(f.request("forge","pickup","itemId","BERRY","quantity",1,"actorId","other")).getCode());
        assertTrue(f.actor.getInventory().isEmpty());
    }
    @Test void snapshotUsesActualInventoryAndDoesNotExposeDistantMerchantStock() {
        Fixture f=new Fixture(); f.carried(2); f.ground(1);
        Map<String,String> state=f.session.observation(); assertEquals("2",state.get("carriedBerry")); assertEquals("1",state.get("visibleBerry"));
        assertFalse(state.containsKey("merchantStock")); assertThrows(UnsupportedOperationException.class,()->state.put("carriedBerry","3"));
        f.nearMerchant(); assertEquals("4",f.session.observation().get("merchantStock"));
    }
}
