package game.agent;

import game.agent.memory.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MemoryTest {
    private NpcMemory event(String id,String world,String area,int x,long turn) {
        return new NpcMemory(id,world,"treecko","BERRY_SEEN","BERRY","Saw berries",area,x,0,turn,NpcMemory.Source.SELF_OBSERVATION,null);
    }
    @Test void perceptionFiltersWorldAreaDistanceAndFutureEvents() {
        MemoryService service = new MemoryService();
        List<NpcMemory> events = Arrays.asList(event("ok","w","orchard",1,2),event("far","w","orchard",9,2),event("other","other","orchard",1,2),event("area","w","lab",1,2),event("future","w","orchard",1,5));
        assertEquals(1,service.perceive("w","treecko","orchard",0,0,3,2,5,events));
        assertEquals("ok",service.recall("w","treecko","BERRY",3,5).get(0).getEventId());
        assertTrue(service.recall("w","mudkip","BERRY",3,5).isEmpty());
        assertTrue(service.recall("other","treecko","BERRY",3,5).isEmpty());
    }
    @Test void duplicateEventsAreIgnoredButNewOccurrenceIsRemembered() {
        MemoryService service = new MemoryService();
        NpcMemory first=event("e1","w","orchard",0,1);
        service.record(first); service.record(first); service.record(event("e2","w","orchard",0,2));
        assertEquals(2,service.recall("w","treecko","BERRY",10,10).size());
        assertEquals("e2",service.recall("w","treecko","BERRY",10,1).get(0).getEventId());
        assertEquals("e1",service.recall("w","treecko","BERRY",1,10).get(0).getEventId());
        assertThrows(UnsupportedOperationException.class, () -> service.recall("w","treecko","BERRY",10,10).clear());
    }
    @Test void perceptionDoesNotCopyOtherNpcsMemoryAndUsesNearestEvents() {
        MemoryService service = new MemoryService();
        NpcMemory other = new NpcMemory("foreign","w","mudkip","SEEN","BERRY","Private","orchard",0,0,1,NpcMemory.Source.SELF_OBSERVATION,null);
        service.perceive("w","treecko","orchard",0,0,2,5,1,Arrays.asList(event("far","w","orchard",3,1),event("near","w","orchard",1,1),other));
        assertEquals("near",service.recall("w","treecko","BERRY",2,10).get(0).getEventId());
        assertEquals(1,service.recall("w","treecko","BERRY",2,10).size());
    }
    @Test void relayedInformationRetainsSourceAndDoesNotBecomeObservation() {
        MemoryService service = new MemoryService();
        NpcMemory relayed = new NpcMemory("told","w","mudkip","BERRY_HINT","BERRY","Treecko saw berries","orchard",1,0,3,NpcMemory.Source.NPC_MESSAGE,"treecko");
        service.record(relayed);
        assertEquals(NpcMemory.Source.NPC_MESSAGE,service.recall("w","mudkip","BERRY",3,1).get(0).getSource());
        assertEquals("treecko",service.recall("w","mudkip","BERRY",3,1).get(0).getSourceNpcId());
        assertTrue(service.recall("w","treecko","BERRY",3,1).isEmpty());
    }
}
