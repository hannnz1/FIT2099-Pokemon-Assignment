package game.agent.quest;
import java.util.*;
/** Engine-derived advice, not an executed action or substitute for a model decision. */
public final class BerryCoordinationAdvice {
 private BerryCoordinationAdvice(){}
 public static void enrich(Map<String,String> state,int orchardX,int orchardY){
  int carried=Integer.parseInt(state.get("carriedBerry")),remaining=Integer.parseInt(state.getOrDefault("remainingBerry",state.get("requiredBerry"))),visible=Integer.parseInt(state.get("visibleBerry"));boolean near="true".equals(state.get("nearProfessor"));
  String next=carried>0&&(near||carried>=remaining)?(near?"deliver":"move_to"):visible>0?"pickup":"move_to";
  state.put("suggestedAction",next);if("move_to".equals(next))state.put("suggestedLocation",carried>=remaining&&carried>0?"laboratory":Integer.parseInt(state.get("x"))==orchardX&&Integer.parseInt(state.get("y"))==orchardY?"alternative":"orchard");
  state.put("decisionHint","Use current carriedBerry, remainingBerry, visibleBerry and nearProfessor before historical NPC messages. When carriedBerry >= remainingBerry, go to laboratory and deliver; do not revisit resources to reach the original total of 3. At professor deliver any carried berries even if partial. Empty site feedback overrides all historical clues. suggestedAction/suggestedLocation use only current local observation; destination stock remains unknown. Choose the registered tool yourself; engine validates every action.");
 }
}
