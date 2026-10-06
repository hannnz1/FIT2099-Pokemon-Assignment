package game.agent;

import game.agent.web.*;
import game.agent.persistence.*;
import game.agent.llm.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentPausePersistenceTest {
    static class Store implements WorldStore {
        final Map<String,Map<String,Object>> values=new HashMap<>();
        public void save(String key,Map<String,Object> value){values.put(key,Json.asObject(Json.read(Json.write(value))));}
        public Map<String,Object> load(String key){return values.containsKey(key)?Json.asObject(Json.read(Json.write(values.get(key)))):null;}
        public void close(){}
    }
    @Test void automaticPauseReasonAndInventorySurviveRestartThenResumeWithoutReplay(){
        Store store=new Store();ProviderSelection provider=AgentRoomTest.provider((m,k,b,t)->new JsonTransport.Response(200,
            b.contains("json_schema")?AgentRoomTest.parsed(b):OpenAiGatewayTest.function("observe","{}")));
        AgentRoom room=new AgentRoom("paused",provider,Runnable::run,false,store);AgentRoomTest.start(room);
        for(int i=2;i<25&&!"PAUSED".equals(room.snapshot().get("status"));i++)room.tick(i);
        Map<String,Object> before=room.snapshot();assertEquals("PAUSED",before.get("status"));assertEquals("NO_PROGRESS_REQUIRES_HELP",before.get("errorCode"));
        String world=Json.write(before.get("world"));
        AgentRoom restored=new AgentRoom("paused",provider,Runnable::run,false,store);
        assertEquals("PAUSED",restored.snapshot().get("status"));assertEquals("NO_PROGRESS_REQUIRES_HELP",restored.snapshot().get("errorCode"));assertEquals(world,Json.write(restored.snapshot().get("world")));
        assertEquals(200,restored.command(AgentRoomTest.command(restored,"RESUME",Collections.emptyMap()),30).status);
        assertNull(restored.snapshot().get("errorCode"));restored.tick(31);assertEquals("RUNNING",restored.snapshot().get("status"));
        restored.close();room.close();
    }
    @Test void oldCheckpointWithoutPauseReasonCanRecoverFromActualTrace(){
        Store store=new Store();ProviderSelection provider=AgentRoomTest.provider((m,k,b,t)->new JsonTransport.Response(200,
            b.contains("json_schema")?AgentRoomTest.parsed(b):OpenAiGatewayTest.function("observe","{}")));
        AgentRoom room=new AgentRoom("legacy-pause",provider,Runnable::run,false,store);AgentRoomTest.start(room);
        for(int i=2;i<25&&!"PAUSED".equals(room.snapshot().get("status"));i++)room.tick(i);
        store.values.get("legacy-pause").remove("pauseReason");
        AgentRoom restored=new AgentRoom("legacy-pause",provider,Runnable::run,false,store);
        assertEquals("NO_PROGRESS_REQUIRES_HELP",restored.snapshot().get("errorCode"));restored.close();room.close();
    }
    @Test void exhaustedResumeAllowanceSurvivesRestartAndIsVisible() {
        Store store=new Store();ProviderSelection provider=AgentRoomTest.provider((m,k,b,t)->new JsonTransport.Response(200,b.contains("json_schema")?AgentRoomTest.parsed(b):OpenAiGatewayTest.function("observe","{}")));
        AgentRoom room=new AgentRoom("resume-limit",provider,Runnable::run,false,store);AgentRoomTest.start(room);
        for(int i=2;i<25&&!"PAUSED".equals(room.snapshot().get("status"));i++)room.tick(i);
        for(int n=0;n<3;n++){assertEquals(200,room.command(AgentRoomTest.command(room,"RESUME",Collections.emptyMap()),30+n*2).status);assertEquals(200,room.command(AgentRoomTest.command(room,"PAUSE",Collections.emptyMap()),31+n*2).status);}
        AgentRoom restored=new AgentRoom("resume-limit",provider,Runnable::run,false,store);
        assertEquals(0,restored.snapshot().get("resumeRemaining"));assertEquals(3,restored.snapshot().get("resumeLimit"));
        assertEquals(429,restored.command(AgentRoomTest.command(restored,"RESUME",Collections.emptyMap()),40).status);
        restored.close();room.close();
    }
}
