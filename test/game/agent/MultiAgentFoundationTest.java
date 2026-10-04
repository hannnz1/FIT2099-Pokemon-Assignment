package game.agent;
import game.agent.multi.MultiAgentFoundation;
import game.agent.multi.MultiAgentFoundation.Role;
import game.agent.memory.NpcMemory;
import game.agent.llm.Json;
import game.agent.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MultiAgentFoundationTest {
    MultiAgentFoundation world(){return new MultiAgentFoundation("world-1",2);}
    void send(MultiAgentFoundation r){r.send("message-1",Role.TORCHIC,Role.PROFESSOR,"BERRY","Observed berry","QUEST_AREA",31,10,0,1,3);}
    @Test void schedulerIsFairAndStopsAtPerAgentBudget(){MultiAgentFoundation r=world();List<String> order=new ArrayList<>();
        for(int i=0;i<8;i++){Map<String,Object> lease=r.next(i,i);order.add((String)lease.get("agentId"));assertEquals("AUTHORIZED",r.finish((String)lease.get("leaseId"),"observe",i));}
        assertEquals(Arrays.asList("mudkip","torchic","professor","merchant","mudkip","torchic","professor","merchant"),order);assertNull(r.next(8,8));}
    @Test void oneLeaseCannotAuthorizeTwoActions(){MultiAgentFoundation r=world();Map<String,Object> lease=r.next(0,3);
        assertThrows(IllegalArgumentException.class,()->r.next(0,3));assertEquals("AUTHORIZED",r.finish((String)lease.get("leaseId"),"move",3));assertEquals("UNKNOWN_LEASE",r.finish((String)lease.get("leaseId"),"move",3));}
    @Test void worldRevisionInvalidatesLateResult(){MultiAgentFoundation r=world();Map<String,Object> lease=r.next(0,3);
        assertEquals("STALE_OBSERVATION",r.finish((String)lease.get("leaseId"),"move",4));assertEquals("UNKNOWN_LEASE",r.finish((String)lease.get("leaseId"),"move",4));}
    @Test void npcsCannotMutateInventory(){MultiAgentFoundation r=world();for(int i=0;i<2;i++){Map<String,Object> l=r.next(0,0);r.finish((String)l.get("leaseId"),"observe",0);}
        Map<String,Object> professor=r.next(0,0);assertEquals("professor",professor.get("agentId"));assertEquals("TOOL_FORBIDDEN",r.finish((String)professor.get("leaseId"),"pickup",0));assertFalse(MultiAgentFoundation.tools(Role.MERCHANT).contains("purchase"));}
    @Test void messagesAreScopedHistoricalClaimsWithProvenance(){MultiAgentFoundation r=world();send(r);
        assertTrue(r.inbox(Role.MERCHANT,1).isEmpty());Map<String,Object> message=r.inbox(Role.PROFESSOR,1).get(0);assertEquals(false,message.get("liveFact"));assertEquals(true,message.get("requiresObservation"));
        NpcMemory memory=r.recall(Role.PROFESSOR,"BERRY",1,1).get(0);assertEquals(NpcMemory.Source.NPC_MESSAGE,memory.getSource());assertEquals("torchic",memory.getSourceNpcId());assertEquals("world-1",memory.getWorldId());}
    @Test void deliveryCannotAppearBeforeItWasSent(){MultiAgentFoundation r=world();send(r);assertTrue(r.inbox(Role.PROFESSOR,0).isEmpty());assertTrue(r.recall(Role.PROFESSOR,"BERRY",0,64).isEmpty());}
    @Test void expiredMessageStaysHistorical(){MultiAgentFoundation r=world();send(r);assertEquals(false,r.inbox(Role.PROFESSOR,3).get(0).get("expired"));assertEquals(true,r.inbox(Role.PROFESSOR,4).get(0).get("expired"));}
    @Test void retryDoesNotDuplicateOrOverwriteMessage(){MultiAgentFoundation r=world();send(r);assertFalse(r.send("message-1",Role.TORCHIC,Role.PROFESSOR,"BERRY","Observed berry","QUEST_AREA",31,10,0,1,3));
        assertThrows(IllegalArgumentException.class,()->r.send("message-1",Role.TORCHIC,Role.PROFESSOR,"BERRY","Forged content","QUEST_AREA",31,10,0,1,3));assertEquals(1,r.inbox(Role.PROFESSOR,4).size());}
    @Test void futureObservationAndExcessiveTtlAreRejected(){MultiAgentFoundation r=world();assertThrows(IllegalArgumentException.class,()->r.send("a",Role.TORCHIC,Role.PROFESSOR,"BERRY","claim","QUEST_AREA",0,0,2,1,3));
        assertThrows(IllegalArgumentException.class,()->r.send("a",Role.TORCHIC,Role.PROFESSOR,"BERRY","claim","QUEST_AREA",0,0,0,0,11));assertTrue(r.inbox(Role.PROFESSOR,99).isEmpty());}
    @Test void checkpointAndInboxCannotMutateAuthoritativeMessages(){MultiAgentFoundation r=world();send(r);Map<String,Object> saved=r.checkpoint();Json.asObject(Json.asArray(saved.get("messages")).get(0)).put("content","changed");r.inbox(Role.PROFESSOR,1).get(0).put("content","changed");assertEquals("Observed berry",r.inbox(Role.PROFESSOR,1).get(0).get("content"));}
    @Test void restoreRetainsReceiptsAndBudgetsButDropsPendingLease(){MultiAgentFoundation r=world();send(r);Map<String,Object> lease=r.next(1,0);MultiAgentFoundation restored=MultiAgentFoundation.restore(r.checkpoint());
        assertEquals("UNKNOWN_LEASE",restored.finish((String)lease.get("leaseId"),"move",0));assertEquals("torchic",restored.next(1,0).get("agentId"));assertEquals(r.inbox(Role.PROFESSOR,4),restored.inbox(Role.PROFESSOR,4));}
    @Test void corruptBudgetAndDuplicateReceiptAreRejected(){MultiAgentFoundation r=world();send(r);Map<String,Object> saved=r.checkpoint();Json.asObject(Json.asArray(saved.get("agents")).get(0)).put("used",3);assertThrows(IllegalArgumentException.class,()->MultiAgentFoundation.restore(saved));
        Map<String,Object> dup=r.checkpoint();Json.asArray(dup.get("messages")).add(Json.asArray(dup.get("messages")).get(0));assertThrows(IllegalArgumentException.class,()->MultiAgentFoundation.restore(dup));}
    @Test void clockCannotRewind(){MultiAgentFoundation r=world();Map<String,Object> lease=r.next(5,5);r.finish((String)lease.get("leaseId"),"observe",5);assertThrows(IllegalArgumentException.class,()->r.next(4,6));}
    @Test void receiptsHaveBoundedCapacity(){MultiAgentFoundation r=world();for(int i=0;i<64;i++)r.send("m"+i,Role.TORCHIC,Role.PROFESSOR,"BERRY","claim","QUEST_AREA",0,0,0,0,1);
        assertThrows(IllegalArgumentException.class,()->r.send("overflow",Role.TORCHIC,Role.PROFESSOR,"BERRY","claim","QUEST_AREA",0,0,0,0,1));assertEquals(64,r.inbox(Role.PROFESSOR,1).size());}
    @Test void fileRoundTripPreservesClaimsAndBudgets(@TempDir Path dir){MultiAgentFoundation r=world();send(r);r.next(1,0);try(WorldStore store=new FileWorldStore(dir)){store.save("v3",r.checkpoint());assertEquals(Json.write(r.checkpoint()),Json.write(MultiAgentFoundation.restore(store.load("v3")).checkpoint()));}}
    @Test void postgresRoundTripPreservesClaimsAndBudgets()throws Exception{String url=System.getProperty("pokemon.test.jdbcUrl");org.junit.jupiter.api.Assumptions.assumeTrue(url!=null);Class.forName("org.postgresql.Driver");String owner="v3-"+UUID.randomUUID();MultiAgentFoundation r=world();send(r);r.next(1,0);
        try(WorldStore store=new JdbcWorldStore(()->java.sql.DriverManager.getConnection(url,System.getProperty("pokemon.test.jdbcUser","pokemon_v1"),""))){store.save(owner,r.checkpoint());assertEquals(Json.write(r.checkpoint()),Json.write(MultiAgentFoundation.restore(store.load(owner)).checkpoint()));}}
}
