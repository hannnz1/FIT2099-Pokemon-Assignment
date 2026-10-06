package game.agent.combat;
import game.agent.tools.*;
import game.agent.action.ActionResult;
import java.util.*;
import java.util.function.Supplier;
public final class CombatTools {
    private CombatTools(){ }
    public static void register(GameToolRegistry registry,CombatSession session,Supplier<Map<String,String>> observation){
        Map<String,ToolParameter> params=Collections.singletonMap("targetId",ToolParameter.string());
        registry.register(new ToolDefinition("attack","Attack an adjacent trusted wild Pokemon and receive its actual retaliation; forbidden under NO_ACTIVE_BATTLE",true,params),r->session.attack((String)r.getArguments().get("targetId")));
        registry.register(new ToolDefinition("capture","Delegate an adjacent capture to trainer inventory using original species rules; Torchic is not capturable. Does not attack or require low HP; ordinary balls are unlimited",true,params),r->session.capture((String)r.getArguments().get("targetId")));
        registry.register(new ToolDefinition("observe_combat","Read actual companion health, wild target positions and captured trainer balls",false,Collections.emptyMap()),r->ActionResult.of(ActionResult.Status.SUCCESS,"COMBAT_OBSERVED",observation.get()));
    }
}