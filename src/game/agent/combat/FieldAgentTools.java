package game.agent.combat;

import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.positions.*;
import game.agent.action.ActionResult;
import game.agent.llm.*;
import game.agent.navigation.NavigationService;
import game.agent.tools.*;
import java.util.*;
import java.util.function.*;

/** World-thread field tools, bound to the player's confirmed target and native rules. */
public final class FieldAgentTools {
 private FieldAgentTools(){}
 public static GameToolRegistry create(Actor actor,GameMap map,WildEncounter wild,TaskIntent intent,
     Predicate<Location> allowed,Supplier<String> guard,BooleanSupplier active,IntSupplier turnDamage,Supplier<Map<String,String>> observation){
  return create(actor,map,wild,intent,()->intent,allowed,guard,active,turnDamage,observation);
 }
 public static GameToolRegistry create(Actor actor,GameMap map,WildEncounter wild,TaskIntent plan,Supplier<TaskIntent> current,
     Predicate<Location> allowed,Supplier<String> guard,BooleanSupplier active,IntSupplier turnDamage,Supplier<Map<String,String>> observation){
  return create(()->actor,map,wild,plan,current,allowed,guard,active,turnDamage,observation);
 }
 public static GameToolRegistry create(Supplier<Actor> actors,GameMap map,WildEncounter wild,TaskIntent plan,Supplier<TaskIntent> current,
     Predicate<Location> allowed,Supplier<String> guard,BooleanSupplier active,IntSupplier turnDamage,Supplier<Map<String,String>> observation){
  GameToolRegistry registry=new GameToolRegistry(request->guard.get());
  registry.register(new ToolDefinition("observe","Read actual field target states and current actor position; no world turn.",false,Collections.emptyMap()),r->ActionResult.of(ActionResult.Status.SUCCESS,"FIELD_OBSERVED",observation.get()));
  Map<String,ToolParameter> params=Collections.singletonMap("targetId",ToolParameter.string());
  registry.register(new ToolDefinition("move_to","Move ONE legal exit toward a reachable adjacent tile of the confirmed wild target. Uses targetId, never its occupied tile. Continuations finish approach without extra model calls.",true,params),r->{
   Actor actor=actors.get();TaskIntent intent=current.get();String id=(String)r.getArguments().get("targetId");if(!intent.getTargetId().equals(id))return ActionResult.rejected("TARGET_NOT_IN_TASK");
   Map<String,Object> target=wild.state(id);if(target==null||!"WILD".equals(target.get("state")))return ActionResult.rejected("TARGET_UNAVAILABLE");
   Location goal=map.at(((Number)target.get("x")).intValue(),((Number)target.get("y")).intValue());
   if(!allowed.test(goal)||!allowed.test(map.locationOf(actor)))return ActionResult.rejected("AREA_RESTRICTED");
   NavigationService navigation=new NavigationService();Location destination=null;List<Location> shortest=Collections.emptyList();
   for(Exit exit:goal.getExits()){
    Location point=exit.getDestination();if(point==map.locationOf(actor))return ActionResult.success("TARGET_ADJACENT");
    if(!allowed.test(point)||!point.canActorEnter(actor))continue;
    List<Location> route=navigation.findRoute(actor,map,point,allowed);
    if(!route.isEmpty()&&(destination==null||route.size()<shortest.size())){destination=point;shortest=route;}
   }
   if(destination==null)return ActionResult.rejected("NO_PATH");
   return navigation.step(actor,map,destination,allowed,active);
  });
  for(String operation:Arrays.asList("capture","attack")){
   boolean supported=false;for(TaskIntent step:plan.getSteps())if(step.isField()&&operation.equals("CAPTURE".equals(step.getKind())?"capture":"attack"))supported=true;
   if(!supported)continue;
   registry.register(new ToolDefinition(operation,"Apply native "+operation+" ONLY to current stage target and corresponding goal. Future/previous targets are forbidden.",true,params),r->{
    Actor actor=actors.get();TaskIntent intent=current.get();String id=(String)r.getArguments().get("targetId");if(!intent.getTargetId().equals(id))return ActionResult.rejected("TARGET_NOT_IN_TASK");
    if(!operation.equals("CAPTURE".equals(intent.getKind())?"capture":"attack"))return ActionResult.rejected("STAGE_OPERATION_NOT_ALLOWED");
    return wild.action(actor,operation,id,intent.has(TaskIntent.Constraint.NO_ACTIVE_BATTLE),allowed,turnDamage.getAsInt());
   });
  }
  boolean defeat=false;for(TaskIntent step:plan.getSteps())if("DEFEAT".equals(step.getKind()))defeat=true;
  if(defeat){Map<String,ToolParameter> skill=new LinkedHashMap<>();skill.put("targetId",ToolParameter.string());skill.put("moveId",ToolParameter.string());registry.register(new ToolDefinition("use_skill","For a growth companion, use an equipped move with positive PP against ONLY the current DEFEAT target. Real effects, counterattack and XP. NO_ACTIVE_BATTLE forbids skills.",true,skill),r->{TaskIntent intent=current.get();String id=(String)r.getArguments().get("targetId");if(!intent.isField()||!Objects.equals(intent.getTargetId(),id))return ActionResult.rejected("TARGET_NOT_IN_TASK");if(!"DEFEAT".equals(intent.getKind()))return ActionResult.rejected("STAGE_OPERATION_NOT_ALLOWED");return wild.useSkill(actors.get(),id,(String)r.getArguments().get("moveId"),intent.has(TaskIntent.Constraint.NO_ACTIVE_BATTLE),allowed);});}
  return registry;
 }
}
