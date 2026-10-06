package game.agent.quest;
import java.util.*;
/** Engine-derived advice, not an executed action or substitute for a model decision. */
public final class BerryCoordinationAdvice {
 private BerryCoordinationAdvice(){}
 public static void enrich(Map<String,String> state,int orchardX,int orchardY){
  int carried=Integer.parseInt(state.get("carriedBerry")),remaining=Integer.parseInt(state.getOrDefault("remainingBerry",state.get("requiredBerry"))),visible=Integer.parseInt(state.get("visibleBerry"));boolean near="true".equals(state.get("nearProfessor"));
  state.put("neededPickupBerry",Integer.toString(Math.max(0,remaining-carried)));
  state.put("deliveryReady",Boolean.toString(carried>0&&carried>=remaining));
  String next=carried>0&&(near||carried>=remaining)?(near?"deliver":"move_to"):visible>0?"pickup":"move_to";
  state.put("suggestedAction",next);if("move_to".equals(next))state.put("suggestedLocation",carried>=remaining&&carried>0?"laboratory":Integer.parseInt(state.get("x"))==orchardX&&Integer.parseInt(state.get("y"))==orchardY?"alternative":"orchard");
  state.put("decisionHint","For pickup and purchase tools itemId must be exactly BERRY (uppercase), never Berry or a translated display name. Use current carriedBerry, remainingBerry, visibleBerry and nearProfessor before historical NPC messages. When carriedBerry >= remainingBerry, go to laboratory and deliver; do not revisit resources to reach the original total of 3. At professor deliver any carried berries even if partial. Empty site feedback overrides all historical clues. suggestedAction/suggestedLocation use only current local observation; destination stock remains unknown. Choose the registered tool yourself; engine validates every action.");
 }
 /** Recovery only after a berry task stalls; current local stock remains authoritative. */
 public static game.agent.tools.ToolRequest localPickupRecovery(Map<String,String> state){
  if(!"ACTIVE".equals(state.get("questStatus")))return null;
  int needed=Math.max(0,Integer.parseInt(state.getOrDefault("remainingBerry",state.get("requiredBerry")))-Integer.parseInt(state.get("carriedBerry")));
  int visible=Integer.parseInt(state.getOrDefault("visibleBerry","0"));
  return needed>0&&visible>0?new game.agent.tools.ToolRequest("local-pickup-recovery","pickup",game.agent.llm.Json.object("itemId","BERRY","quantity",Math.min(20,Math.min(needed,visible)))):null;
 }
 /** Trusted delivery priority. Solo quests cannot deliver partial inventories. */
 public static game.agent.tools.ToolRequest deliveryAction(Map<String,String> state){
  if(!"ACTIVE".equals(state.get("questStatus")))return null;
  int carried=Integer.parseInt(state.get("carriedBerry")),remaining=Integer.parseInt(state.getOrDefault("remainingBerry",state.get("requiredBerry")));
  boolean near="true".equals(state.get("nearProfessor")),shared="true".equals(state.get("sharedQuest"));
  if(remaining<=0||carried<=0)return null;
  if(near&&(shared||carried>=remaining))return new game.agent.tools.ToolRequest("delivery-priority","deliver",game.agent.llm.Json.object("questId",state.get("questId"),"targetNpcId","professor"));
  return carried>=remaining?new game.agent.tools.ToolRequest("delivery-priority","move_to",game.agent.llm.Json.object("locationId","laboratory")):null;
 }
}
