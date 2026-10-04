package game.agent.tools;
import game.agent.action.ActionResult;import game.agent.llm.TaskIntent;import java.util.*;import java.util.function.*;
/** Reuses native tools while validating the current ordered stage at commit time. */
public final class MixedQuestTools {
 private MixedQuestTools(){}
 public static GameToolRegistry create(GameToolRegistry berry,GameToolRegistry field,Supplier<TaskIntent> current,Supplier<Map<String,String>> observation){
  GameToolRegistry mixed=new GameToolRegistry(r->null);
  mixed.register(new ToolDefinition("observe","Read current mixed stage, actual berries, wild targets and controlling actor. No world turn.",false,Collections.emptyMap()),r->ActionResult.of(ActionResult.Status.SUCCESS,"MIXED_OBSERVED",observation.get()));
  for(boolean wild:new boolean[]{false,true}){GameToolRegistry source=wild?field:berry;
   for(ToolDefinition d:source.definitions()){
    if("observe".equals(d.getName()))continue;String name=!wild&&"move_to".equals(d.getName())?"move_to_location":d.getName();
    mixed.register(new ToolDefinition(name,d.getDescription(),d.changesWorld(),d.getParameters()),r->{
     if(current.get().isField()!=wild)return ActionResult.rejected("STAGE_OPERATION_NOT_ALLOWED");
     return source.execute(new ToolRequest(r.getActionId(),d.getName(),r.getArguments()));
    });
   }
  }
  return mixed;
 }
}
