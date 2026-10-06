package game.agent.tools;

import game.agent.quest.BerryQuestSession;
import game.agent.action.ActionResult;
import java.util.*;

/** One registry must be bound to the same actor/task as the quest session. */
public final class BerryQuestTools {
    private BerryQuestTools() { }
    public static void register(GameToolRegistry tools,BerryQuestSession session) {
        Objects.requireNonNull(session);
        Map<String,ToolParameter> item=new LinkedHashMap<>(); item.put("itemId",ToolParameter.literal("BERRY")); item.put("quantity",ToolParameter.integer(1,20));
        tools.register(new ToolDefinition("pickup","Pick up BERRY units on the companion's current tile",true,item),
            r->session.pickup((String)r.getArguments().get("itemId"),integer(r,"quantity")));
        Map<String,ToolParameter> delivery=new LinkedHashMap<>(); delivery.put("questId",ToolParameter.string()); delivery.put("targetNpcId",ToolParameter.string());
        tools.register(new ToolDefinition("deliver","Deliver carried berries to adjacent professor; cooperative quests accept partial contribution toward shared remainingBerry, legacy solo quests require all 3; authoritative validation",true,delivery),
            r->session.deliver((String)r.getArguments().get("questId"),(String)r.getArguments().get("targetNpcId")));
        tools.register(new ToolDefinition("observe_quest","Read this session's quest and visible local state",false,Collections.emptyMap()),
            r->ActionResult.of(ActionResult.Status.SUCCESS,"QUEST_OBSERVED",session.observation()));
        Map<String,ToolParameter> purchase=new LinkedHashMap<>(item); purchase.put("approvalToken",ToolParameter.string());
        tools.register(new ToolDefinition("purchase_item","Buy BERRY from adjacent merchant using exact single-use approval; NONE has no authorization",true,purchase),
            r->session.purchase((String)r.getArguments().get("itemId"),integer(r,"quantity"),(String)r.getArguments().get("approvalToken")));
        Map<String,ToolParameter> approval=new LinkedHashMap<>(item); approval.put("actionType",ToolParameter.string()); approval.put("reason",ToolParameter.string());
        tools.register(new ToolDefinition("request_player_approval","Propose a BERRY purchase; server computes actual cost; player approval is separate",false,approval),r->{
            if(!"PURCHASE_BERRY".equals(r.getArguments().get("actionType"))) return ActionResult.rejected("UNKNOWN_ACTION");
            return session.requestPurchaseApproval((String)r.getArguments().get("itemId"),integer(r,"quantity"));
        });
    }
    private static int integer(ToolRequest request,String name) {
        return new java.math.BigDecimal(request.getArguments().get(name).toString()).intValueExact();
    }
}
