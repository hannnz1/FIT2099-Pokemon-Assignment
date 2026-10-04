package game.agent.demo;

import edu.monash.fit2099.engine.actions.*;
import edu.monash.fit2099.engine.displays.Display;
import edu.monash.fit2099.engine.positions.*;
import game.actors.npc.*;
import game.agent.action.ActionResult;
import game.agent.quest.*;
import game.agent.runtime.*;
import game.agent.tools.*;
import game.environments.Dirt;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic Java integration scenario, explicitly not a Gemini demonstration.
 * It executes real engine turns and branches on observations/results. The trusted
 * harness simulates the player's approve/deny choice; the Agent cannot approve.
 */
public final class BerryQuestDemo {
    public static final class Outcome {
        private final BerryQuestSession.QuestStatus questStatus;
        private final AgentTask.State taskState;
        private final int coins,delivered,decisions;
        private final boolean resourceFailure,approval;
        private Outcome(BerryQuestSession session,AgentTask task,ProffesorOak professor,int decisions,boolean resourceFailure,boolean approval) {
            questStatus=session.getQuestStatus(); taskState=task.getState(); coins=session.getBalance();
            delivered=(int)professor.getInventory().stream().filter(Berry.class::isInstance).count();
            this.decisions=decisions; this.resourceFailure=resourceFailure; this.approval=approval;
        }
        public BerryQuestSession.QuestStatus getQuestStatus() { return questStatus; }
        public AgentTask.State getTaskState() { return taskState; }
        public int getCoins() { return coins; }
        public int getDelivered() { return delivered; }
        public int getDecisions() { return decisions; }
        public boolean hadResourceFailure() { return resourceFailure; }
        public boolean hadApproval() { return approval; }
        @Override public String toString() {
            return "quest="+questStatus+", task="+taskState+", delivered="+delivered+", coins="+coins+
                ", decisions="+decisions+", resourceFailure="+resourceFailure+", approval="+approval;
        }
    }
    private static final class ScriptedProvider implements AgentLoop.DecisionProvider {
        private boolean attemptedThree,requested;
        private int decisions;
        @Override public ToolRequest decide(AgentLoop.Context context) {
            decisions++; Map<String,String> state=context.getObservation();
            int carried=Integer.parseInt(state.get("carriedBerry"));
            if(carried>=3) return "true".equals(state.get("nearProfessor"))
                ? call("deliver","questId",state.get("questId"),"targetNpcId","professor") : move("laboratory");
            if(carried<2) {
                if(!"2".equals(state.get("x")) || !"1".equals(state.get("y"))) return move("orchard");
                if(!attemptedThree) { attemptedThree=true; return call("pickup","itemId","BERRY","quantity",3); }
                return call("pickup","itemId","BERRY","quantity",2);
            }
            String token=state.get("approvalToken");
            if("true".equals(state.get("nearMerchant"))) {
                if(token!=null) return call("purchase_item","itemId","BERRY","quantity",1,"approvalToken",token);
                if(!requested) {
                    requested=true;
                    return call("request_player_approval","actionType","PURCHASE_BERRY","itemId","BERRY","quantity",1,"reason","Missing the third berry");
                }
            }
            if(!requested) return move("market");
            if("6".equals(state.get("x")) && "2".equals(state.get("y"))) return call("pickup","itemId","BERRY","quantity",1);
            return move("alternative");
        }
        private ToolRequest move(String location) { return call("move_to","locationId",location); }
        private ToolRequest call(String name,Object... pairs) {
            Map<String,Object> args=new LinkedHashMap<>(); for(int i=0;i<pairs.length;i+=2) args.put((String)pairs[i],pairs[i+1]);
            return new ToolRequest("script-"+decisions,name,args);
        }
    }
    private BerryQuestDemo() { }
    public static Outcome run(boolean approve) {
        GameMap map=new GameMap(new FancyGroundFactory(new Dirt()),Arrays.asList(".........",".........","........."));
        new World(new Display()).addGameMap(map); AgentMudkip actor=new AgentMudkip();
        ProffesorOak professor=new ProffesorOak(); Shopkeeper merchant=new Shopkeeper();
        map.addActor(actor,map.at(0,1)); map.addActor(merchant,map.at(4,1)); map.addActor(professor,map.at(8,1));
        map.at(2,1).addItem(new Berry()); map.at(2,1).addItem(new Berry()); map.at(6,2).addItem(new Berry());
        AgentTask task=new AgentTask("berry-demo"); task.start(); AtomicInteger clock=new AtomicInteger();
        BerryQuestSession session=new BerryQuestSession("demo-player",task,actor,map,professor,merchant,
            new BerryQuestSession.Rules(3,60,10,1000),5,4,1,true,clock::get);
        GameToolRegistry tools=new GameToolRegistry(r->null); Map<String,Location> catalog=new LinkedHashMap<>();
        catalog.put("orchard",map.at(2,1)); catalog.put("market",map.at(3,1)); catalog.put("laboratory",map.at(7,1)); catalog.put("alternative",map.at(6,2));
        GameTools.register(tools,actor,map,catalog,p->true,()->task.getState()==AgentTask.State.RUNNING || task.getState()==AgentTask.State.REPLANNING);
        BerryQuestTools.register(tools,session); ScriptedProvider provider=new ScriptedProvider();
        AgentLoop loop=new AgentLoop(task,"Collect and deliver 3 berries; no spending without player approval",tools,
            Runnable::run,provider,session::observation,()->session.getQuestStatus()==BerryQuestSession.QuestStatus.COMPLETED,100,30,12,5);
        actor.attach(loop,map,()->clock.getAndIncrement()); boolean hadApproval=false;
        for(int i=0;i<100 && task.getState()!=AgentTask.State.COMPLETED && task.getState()!=AgentTask.State.FAILED;i++) {
            Action action=actor.playTurn(new ActionList(),null,map,new Display()); action.execute(actor,map);
            if(task.getState()==AgentTask.State.WAITING_APPROVAL) {
                hadApproval=true;
                if(session.advanceTurn()) throw new IllegalStateException("Approval did not freeze quest turns");
                String id=session.pendingApproval("demo-player").get("proposalId");
                session.resolveApproval("demo-player",id,approve);
            } else session.advanceTurn();
        }
        boolean resourceFailure=loop.getTrace().stream().anyMatch(r->"RESOURCE_NOT_AVAILABLE".equals(r.getCode()));
        Outcome result=new Outcome(session,task,professor,provider.decisions,resourceFailure,hadApproval);
        if(result.questStatus!=BerryQuestSession.QuestStatus.COMPLETED || result.delivered!=3 || !resourceFailure || !hadApproval)
            throw new IllegalStateException("Incomplete berry scenario: "+result);
        return result;
    }
    public static void main(String[] args) {
        System.out.println("Scripted Java quest integration (no LLM / HTTP / database)");
        System.out.println("Player approves: "+run(true));
        System.out.println("Player denies, Agent finds alternative: "+run(false));
        System.out.println("PASS: real berry pickup, insufficient resources, single-use purchase approval, denial/replanning, delivery and authoritative quest completion");
    }
}
