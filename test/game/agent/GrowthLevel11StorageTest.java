package game.agent.web;

import game.agent.llm.Json;
import game.agent.persistence.*;
import game.agent.growth.GrowthRules;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrowthLevel11StorageTest {
    static class Store implements WorldStore {
        final Map<String,Map<String,Object>> values=new HashMap<>();boolean fail;
        public void save(String key,Map<String,Object> value){if(fail)throw new StoreException(StoreException.Code.IO_ERROR);values.put(key,Json.asObject(Json.read(Json.write(value))));}
        public Map<String,Object> load(String key){return values.containsKey(key)?Json.asObject(Json.read(Json.write(values.get(key)))):null;}
        public void close(){}
    }
    private AgentRoom.Reply command(GrowthRoom room,String name,Map<String,Object> args){return room.command(Json.object("requestId",UUID.randomUUID().toString(),"taskId",room.snapshot().get("taskId"),"expectedRevision",room.snapshot().get("revision"),"command",name,"params",args),10);}
    @Test void levelElevenSaveFailureFreezesAndRecoveryRetainsCommittedLevelThenSavesAgain(){
        Store store=new Store();ProviderSelection offline=new ProviderSelection("disabled","unavailable",1000,null,null);
        GrowthRoom first=new GrowthRoom("level11",offline,Runnable::run,store,()->0);
        assertEquals(200,command(first,"STARTER",Json.object("species","MUDKIP")).status);
        Map<String,Object> saved=store.values.get("growth:level11"),world=Json.asObject(saved.get("world"));
        Json.asObject(Json.asArray(world.get("team")).get(0)).put("experience",GrowthRules.experienceAt(11));
        GrowthRoom room=new GrowthRoom("level11",offline,Runnable::run,store,()->0);
        assertEquals(11,Json.asObject(Json.asObject(room.snapshot().get("world")).get("partner")).get("level"));
        store.fail=true;assertEquals(503,command(room,"REST",Collections.emptyMap()).status);
        assertEquals("STORAGE_ERROR",room.snapshot().get("status"));assertEquals("IO_ERROR",room.snapshot().get("storageFailureCode"));
        Map<String,Object> diagnostic=Json.asObject(room.snapshot().get("storageFailureDetails"));
        assertEquals("IO_ERROR",diagnostic.get("code"));assertEquals(11,diagnostic.get("partnerLevel"));
        assertTrue(((Number)diagnostic.get("checkpointNodes")).longValue()>0);
        assertTrue(((Number)diagnostic.get("checkpointBytes")).longValue()>0);
        assertEquals(new HashSet<>(Arrays.asList("code","revision","lastSavedRevision","checkpointNodes","checkpointBytes","receiptCount","partnerLevel")),diagnostic.keySet());
        diagnostic.put("code","CHANGED");assertEquals("IO_ERROR",Json.asObject(room.snapshot().get("storageFailureDetails")).get("code"));
        assertEquals(503,command(room,"MOVE",Json.object("direction","N")).status);
        store.fail=false;assertEquals(200,command(room,"RECOVER",Collections.emptyMap()).status);
        assertNull(room.snapshot().get("storageFailureCode"));assertEquals(200,command(room,"REST",Collections.emptyMap()).status);
        assertTrue(Json.asObject(room.snapshot().get("storageFailureDetails")).isEmpty());
        GrowthRoom restored=new GrowthRoom("level11",offline,Runnable::run,store,()->0);
        Map<String,Object> partner=Json.asObject(Json.asObject(restored.snapshot().get("world")).get("partner"));
        assertEquals(11,partner.get("level"));assertEquals(partner.get("maxHp"),partner.get("hp"));
    }
}
