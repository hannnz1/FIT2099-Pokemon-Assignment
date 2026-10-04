package game.agent.tools;

import game.agent.action.ActionResult;
import game.agent.memory.NpcDialogueService;
import java.util.*;
import java.util.function.BooleanSupplier;

public final class DialogueTools {
    private DialogueTools(){}
    public static void register(GameToolRegistry tools,NpcDialogueService dialogue,BooleanSupplier active) {
        Map<String,ToolParameter> args=new LinkedHashMap<>();args.put("targetNpcId",ToolParameter.string());args.put("message",ToolParameter.string());
        tools.register(new ToolDefinition("talk_to","Ask an adjacent treecko, merchant or professor. Treecko gives historical sightings only; verify live availability with observe before pickup.",false,args),
            r->dialogue.talk((String)r.getArguments().get("targetNpcId"),(String)r.getArguments().get("message")));
        tools.register(new ToolDefinition("wait","Wait one allowed scene action turn; cannot advance time during pause or purchase approval",true,Collections.emptyMap()),
            r->active.getAsBoolean()?ActionResult.success("WAITED"):ActionResult.rejected("TASK_INACTIVE"));
    }
}
