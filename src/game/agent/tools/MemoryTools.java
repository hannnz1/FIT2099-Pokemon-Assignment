package game.agent.tools;

import game.agent.action.ActionResult;
import game.agent.memory.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Read-only, per-NPC adapter. Historical claims retain time and provenance. */
public final class MemoryTools {
    private MemoryTools() { }
    public static void register(GameToolRegistry registry,MemoryService memory,String worldId,String npcId,LongSupplier turn) {
        Objects.requireNonNull(memory); Objects.requireNonNull(worldId); Objects.requireNonNull(npcId); Objects.requireNonNull(turn);
        Map<String,ToolParameter> params=new LinkedHashMap<>();
        params.put("subject",ToolParameter.string()); params.put("limit",ToolParameter.integer(1,20));
        registry.register(new ToolDefinition("recall_memory","Recall this NPC's historical claims, not current world facts",false,params),request -> {
            List<NpcMemory> events=memory.recall(worldId,npcId,(String)request.getArguments().get("subject"),turn.getAsLong(),
                new java.math.BigDecimal(request.getArguments().get("limit").toString()).intValueExact());
            Map<String,String> data=new LinkedHashMap<>(); data.put("count",Integer.toString(events.size()));
            for(int i=0;i<events.size();i++) {
                NpcMemory m=events.get(i); String prefix=i+".";
                data.put(prefix+"eventId",m.getEventId()); data.put(prefix+"content",m.getContent());
                data.put(prefix+"occurredAt",Long.toString(m.getOccurredAt())); data.put(prefix+"source",m.getSource().name());
                data.put(prefix+"areaId",m.getAreaId()); data.put(prefix+"x",Integer.toString(m.getX())); data.put(prefix+"y",Integer.toString(m.getY()));
                if(m.getSourceNpcId()!=null) data.put(prefix+"sourceNpcId",m.getSourceNpcId());
            }
            return ActionResult.of(ActionResult.Status.SUCCESS,"HISTORICAL_MEMORIES",data);
        });
    }
}
