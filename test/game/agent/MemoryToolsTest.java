package game.agent;

import game.agent.memory.*;
import game.agent.tools.*;
import game.agent.action.ActionResult;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MemoryToolsTest {
    @Test void modelCanRecallOnlyBoundNpcAndPastEventsWithProvenance() {
        MemoryService memory=new MemoryService();
        memory.record(new NpcMemory("own","w","a","SEEN","berry","past claim","area",1,2,3,NpcMemory.Source.NPC_MESSAGE,"b"));
        memory.record(new NpcMemory("other","w","b","SEEN","berry","private","area",1,2,3,NpcMemory.Source.SELF_OBSERVATION,null));
        memory.record(new NpcMemory("future","w","a","SEEN","berry","future","area",1,2,5,NpcMemory.Source.SELF_OBSERVATION,null));
        GameToolRegistry tools=new GameToolRegistry(r->null);
        MemoryTools.register(tools,memory,"w","a",()->3L);
        Map<String,Object> args=new LinkedHashMap<>(); args.put("subject","berry"); args.put("limit",5);
        ActionResult result=tools.execute(new ToolRequest("recall","recall_memory",args));
        assertEquals("1",result.getData().get("count"));
        assertEquals("past claim",result.getData().get("0.content"));
        assertEquals("NPC_MESSAGE",result.getData().get("0.source"));
        assertEquals("b",result.getData().get("0.sourceNpcId"));
        args.put("npcId","b"); assertEquals("INVALID_ARGUMENTS",tools.execute(new ToolRequest("steal","recall_memory",args)).getCode());
        assertEquals("UNKNOWN_TOOL",tools.execute(new ToolRequest("forge","record_memory",Collections.emptyMap())).getCode());
    }
}
