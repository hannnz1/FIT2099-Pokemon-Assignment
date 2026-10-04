package game.agent.demo;

import game.agent.llm.*;
import game.agent.tools.*;
import game.agent.runtime.*;
import game.agent.action.ActionResult;
import game.agent.persistence.*;
import java.util.*;

/** Explicit deterministic OFFLINE acceptance fixture. Never a live-provider fallback. */
public final class V1AcceptanceDemo {
    private V1AcceptanceDemo(){ }
    private static ToolRequest call(String name,Map<String,Object> args){return new ToolRequest(UUID.randomUUID().toString(),name,args);}
    public static Map<String,Object> run(){
        GeminiQuestScenario scene=new GeminiQuestScenario("offline-test-player");
        List<ToolRequest> script=Arrays.asList(
            call("talk_to",Json.object("targetNpcId","treecko","message","你之前在哪里看见树果？")),
            call("move_to",Json.object("locationId","orchard")),
            call("pickup",Json.object("itemId","BERRY","quantity",3)),
            call("pickup",Json.object("itemId","BERRY","quantity",2)),
            call("move_to",Json.object("locationId","market")),
            call("talk_to",Json.object("targetNpcId","merchant","message","树果的库存与价格是多少？")),
            call("request_player_approval",Json.object("itemId","BERRY","quantity",1,"actionType","PURCHASE_BERRY","reason","需要购买最后一个树果")),
            call("move_to",Json.object("locationId","alternative")),
            call("pickup",Json.object("itemId","BERRY","quantity",1)),
            call("move_to",Json.object("locationId","laboratory")),
            call("talk_to",Json.object("targetNpcId","professor","message","请确认交付要求")),
            call("deliver",Json.object("questId",scene.getSession().getQuestId(),"targetNpcId","professor")));
        Iterator<ToolRequest> choices=script.iterator();
        TaskIntent intent=TaskIntent.of(scene.getSession().getQuestId(),EnumSet.of(TaskIntent.Constraint.NO_SPENDING,TaskIntent.Constraint.NO_ACTIVE_BATTLE),null,null);
        AgentLoop loop=scene.start(intent,context->{if(!choices.hasNext())throw new IllegalStateException("OFFLINE_FIXTURE_EXHAUSTED");return choices.next();},Runnable::run);
        loop.configureTrace("mudkip","offline-scripted","acceptance-fixture");List<String> events=new ArrayList<>();
        for(long now=0;now<200 && loop.getState()!=AgentTask.State.COMPLETED && loop.getState()!=AgentTask.State.FAILED;now++){
            if(loop.getState()==AgentTask.State.WAITING_APPROVAL){Map<String,String> proposal=scene.getSession().pendingApproval("offline-test-player");
                events.add(scene.getSession().resolveApproval("offline-test-player",proposal.get("proposalId"),false).getCode());continue;}
            ActionResult result=loop.tick(now);if(result.getCode().equals("DECISION_PENDING"))continue;events.add(result.getCode());
            if(result.getStatus()==ActionResult.Status.SUCCESS||result.getStatus()==ActionResult.Status.IN_PROGRESS)scene.advanceTurn();
        }
        if(loop.getState()!=AgentTask.State.COMPLETED || scene.getDelivered()!=3 || scene.getSession().getBalance()!=5
            || !events.contains("RESOURCE_NOT_AVAILABLE")||!events.contains("APPROVAL_DENIED"))throw new IllegalStateException("OFFLINE_ACCEPTANCE_FAILED");
        return Json.object("provider","offline-scripted","note","固定模型响应与模拟玩家拒绝；真实 Java 规则，不是在线 AI 结果", "status",loop.getState().name(),
            "delivered",scene.getDelivered(),"coins",scene.getSession().getBalance(),"events",events,"metrics",loop.getMetrics(),"trace",loop.getStepTrace(),"scene",scene.exportState());
    }
    public static void main(String[] args){
        System.out.println("OFFLINE ACCEPTANCE: scripted decisions / simulated player DENY / real engine rules / no AI calls");
        Map<String,Object> result=run();try(WorldStore store=WorldStoreFactory.fromEnvironment(System.getenv())){
            if(store!=null){String owner="offline-acceptance-"+UUID.randomUUID();store.save(owner,result);if(!Json.write(result).equals(Json.write(store.load(owner))))throw new IllegalStateException("CHECKPOINT_MISMATCH");}
        }
        System.out.println(Json.write(result));
    }
}
