package game.agent;

import game.agent.demo.BerryQuestDemo;
import game.agent.runtime.*;
import game.agent.quest.*;
import game.agent.tools.*;
import game.agent.action.ActionResult;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BerryQuestIntegrationTest {
    @Test void approvedPurchaseAndDeniedAlternativeBothCompleteRealQuest() {
        BerryQuestDemo.Outcome bought=BerryQuestDemo.run(true);
        assertEquals(BerryQuestSession.QuestStatus.COMPLETED,bought.getQuestStatus());
        assertEquals(AgentTask.State.COMPLETED,bought.getTaskState());
        assertEquals(4,bought.getCoins()); assertEquals(3,bought.getDelivered()); assertTrue(bought.hadResourceFailure());
        assertTrue(bought.getDecisions()>1); assertTrue(bought.hadApproval());
        BerryQuestDemo.Outcome found=BerryQuestDemo.run(false);
        assertEquals(BerryQuestSession.QuestStatus.COMPLETED,found.getQuestStatus());
        assertEquals(5,found.getCoins()); assertEquals(3,found.getDelivered()); assertTrue(found.hadResourceFailure());
        assertTrue(found.hadApproval());
    }
    @Test void approvalDuringToolCommitPausesLoopAndPlayerResponseResumesIt() {
        BerryQuestTest.Fixture f=new BerryQuestTest.Fixture(); f.nearMerchant();
        AgentLoop loop=new AgentLoop(f.task,"buy berry",f.tools,Runnable::run,c->{
            Map<String,Object> args=new LinkedHashMap<>(); args.put("itemId","BERRY"); args.put("quantity",1);
            String token=c.getObservation().get("approvalToken");
            if(token!=null) { args.put("approvalToken",token); return new ToolRequest("buy","purchase_item",args); }
            args.put("actionType","PURCHASE_BERRY"); args.put("reason","Need one berry");
            return new ToolRequest("request","request_player_approval",args);
        },f.session::observation,()->f.actor.getInventory().size()==1,100,10,5,3);
        loop.tick(0); assertEquals("APPROVAL_REQUESTED",loop.tick(1).getCode());
        assertEquals(AgentTask.State.WAITING_APPROVAL,f.task.getState()); loop.tick(2); assertFalse(f.session.advanceTurn());
        Map<String,String> pending=f.session.pendingApproval("owner"); f.session.resolveApproval("owner",pending.get("proposalId"),true);
        loop.tick(3); assertEquals("PURCHASED",loop.tick(4).getCode()); loop.tick(5);
        assertEquals(4,f.session.getBalance()); assertEquals(AgentTask.State.COMPLETED,f.task.getState());
    }
    @Test void resourceLossIsFeedbackAndCannotCompleteQuestFromTwoBerries() {
        BerryQuestTest.Fixture f=new BerryQuestTest.Fixture(); f.ground(2);
        ActionResult missing=f.tools.execute(f.request("missing","pickup","itemId","BERRY","quantity",3));
        assertEquals("RESOURCE_NOT_AVAILABLE",missing.getCode()); assertTrue(f.actor.getInventory().isEmpty());
        f.tools.execute(f.request("two","pickup","itemId","BERRY","quantity",2)); f.nearProfessor();
        assertEquals("INSUFFICIENT_BERRIES",f.tools.execute(f.request("deliver","deliver","questId",f.session.getQuestId(),"targetNpcId","professor")).getCode());
        assertEquals(BerryQuestSession.QuestStatus.ACTIVE,f.session.getQuestStatus()); assertTrue(f.professor.getInventory().isEmpty());
    }
}
