package game.agent;

import game.agent.quest.*;
import game.agent.runtime.AgentTask;
import game.agent.tools.ToolRequest;
import game.agent.action.ActionResult;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BerryApprovalTest {
    private String approve(BerryQuestTest.Fixture f,int quantity) {
        ActionResult pending=f.session.requestPurchaseApproval("BERRY",quantity);
        assertEquals("APPROVAL_REQUESTED",pending.getCode());
        String id=pending.getData().get("proposalId");
        return f.session.resolveApproval("owner",id,true).getData().get("approvalToken");
    }
    @Test void spendingRequiresApprovalAndRequestDoesNotPurchase() {
        BerryQuestTest.Fixture f=new BerryQuestTest.Fixture(); f.nearMerchant();
        assertEquals("APPROVAL_REQUIRED",f.session.purchase("BERRY",1,"NONE").getCode());
        f.session.requestPurchaseApproval("BERRY",1);
        assertEquals(5,f.session.getBalance()); assertEquals(4,f.session.getMerchantStock()); assertTrue(f.actor.getInventory().isEmpty());
        assertEquals(AgentTask.State.WAITING_APPROVAL,f.task.getState());
        assertEquals("1",f.session.pendingApproval("owner").get("totalCost"));
        assertTrue(f.session.pendingApproval("other").isEmpty()); assertFalse(f.session.observation().containsKey("approvalToken"));
    }
    @Test void wrongOwnerAndProposalCannotApproveAndDenyReplans() {
        BerryQuestTest.Fixture f=new BerryQuestTest.Fixture(); f.nearMerchant();
        String id=f.session.requestPurchaseApproval("BERRY",1).getData().get("proposalId");
        assertEquals("UNAUTHORIZED",f.session.resolveApproval("other",id,true).getCode());
        assertEquals("STALE_APPROVAL",f.session.resolveApproval("owner","wrong",true).getCode());
        assertEquals(AgentTask.State.WAITING_APPROVAL,f.task.getState());
        assertEquals("APPROVAL_DENIED",f.session.resolveApproval("owner",id,false).getCode());
        assertEquals(AgentTask.State.REPLANNING,f.task.getState()); assertEquals(5,f.session.getBalance());
        assertEquals("APPROVAL_REQUIRED",f.session.purchase("BERRY",1,"NONE").getCode());
        assertEquals("STALE_APPROVAL",f.session.resolveApproval("owner",id,true).getCode());
    }
    @Test void exactApprovalPurchasesOnceAndRetryReturnsOriginalReceipt() {
        BerryQuestTest.Fixture f=new BerryQuestTest.Fixture(); f.nearMerchant(); String token=approve(f,2);
        assertNotNull(token); assertEquals(token,f.session.observation().get("approvalToken"));
        ToolRequest buy=f.request("buy","purchase_item","itemId","BERRY","quantity",2,"approvalToken",token);
        assertEquals("PURCHASED",f.tools.execute(buy).getCode()); f.tools.execute(buy);
        assertEquals(3,f.session.getBalance()); assertEquals(2,f.session.getMerchantStock()); assertEquals(2,f.actor.getInventory().size());
        assertEquals("APPROVAL_REQUIRED",f.session.purchase("BERRY",2,token).getCode());
        assertFalse(f.session.observation().containsKey("approvalToken"));
    }
    @Test void tokenCannotChangeQuantityPriceOrSession() {
        BerryQuestTest.Fixture f=new BerryQuestTest.Fixture(); f.nearMerchant(); String token=approve(f,1);
        assertEquals("APPROVAL_MISMATCH",f.session.purchase("BERRY",2,token).getCode());
        BerryQuestTest.Fixture other=new BerryQuestTest.Fixture(); other.nearMerchant();
        assertEquals("APPROVAL_REQUIRED",other.session.purchase("BERRY",1,token).getCode());
        f.session.updateMerchantOffer(4,2);
        assertEquals("APPROVAL_MISMATCH",f.session.purchase("BERRY",1,token).getCode());
        assertEquals(5,f.session.getBalance()); assertTrue(f.actor.getInventory().isEmpty());
    }
    @Test void approvalExpiryUsesWallClockWhileWorldTurnsAreFrozen() {
        BerryQuestTest.Fixture f=new BerryQuestTest.Fixture(); f.nearMerchant();
        String id=f.session.requestPurchaseApproval("BERRY",1).getData().get("proposalId");
        for(int i=0;i<10;i++) assertFalse(f.session.advanceTurn()); assertEquals(0,f.session.getTurn());
        f.clock.set(1000);
        assertEquals("STALE_APPROVAL",f.session.resolveApproval("owner",id,true).getCode());
        assertEquals(AgentTask.State.REPLANNING,f.task.getState()); assertTrue(f.session.advanceTurn());
        String token=approve(f,1); f.clock.set(2000);
        assertEquals("APPROVAL_REQUIRED",f.session.purchase("BERRY",1,token).getCode());
    }
    @Test void pauseCancelAndResumeInvalidateOldGrantsAndPendingApproval() {
        BerryQuestTest.Fixture f=new BerryQuestTest.Fixture(); f.nearMerchant(); String token=approve(f,1);
        f.task.pause(); f.task.resume();
        assertEquals("APPROVAL_REQUIRED",f.session.purchase("BERRY",1,token).getCode());
        String id=f.session.requestPurchaseApproval("BERRY",1).getData().get("proposalId"); f.task.cancel();
        assertEquals("STALE_APPROVAL",f.session.resolveApproval("owner",id,true).getCode());
        assertEquals("TASK_INACTIVE",f.session.purchase("BERRY",1,token).getCode()); assertEquals(5,f.session.getBalance());
    }
    @Test void purchaseRevalidatesStockCapacityDistanceFundsAndDeadline() {
        BerryQuestTest.Fixture f=new BerryQuestTest.Fixture(3,1); f.nearMerchant(); String token=approve(f,1);
        f.session.updateMerchantOffer(0,1); assertEquals("RESOURCE_NOT_AVAILABLE",f.session.purchase("BERRY",1,token).getCode());
        f.session.updateMerchantOffer(4,1); f.carried(3); assertEquals("INVENTORY_FULL",f.session.purchase("BERRY",1,token).getCode());
        f.actor.removeItemFromInventory(f.actor.getInventory().get(0)); f.map.moveActor(f.actor,f.map.at(0,1));
        assertEquals("OUT_OF_REACH",f.session.purchase("BERRY",1,token).getCode());
        f.nearMerchant(); f.session.advanceTurn(); assertEquals("QUEST_EXPIRED",f.session.purchase("BERRY",1,token).getCode());
        assertEquals(5,f.session.getBalance()); assertEquals(4,f.session.getMerchantStock());
        BerryQuestTest.Fixture poor=new BerryQuestTest.Fixture(); poor.nearMerchant(); poor.session.updateMerchantOffer(4,6);
        assertEquals("INSUFFICIENT_FUNDS",poor.session.requestPurchaseApproval("BERRY",1).getCode());
    }
    @Test void removedMerchantAndUnknownItemCannotCreateApproval() {
        BerryQuestTest.Fixture f=new BerryQuestTest.Fixture(); f.nearMerchant();
        assertEquals("UNKNOWN_ITEM",f.session.requestPurchaseApproval("SHELL",1).getCode());
        f.map.removeActor(f.merchant); assertEquals("OUT_OF_REACH",f.session.requestPurchaseApproval("BERRY",1).getCode());
        assertEquals(AgentTask.State.RUNNING,f.task.getState());
    }
    @Test void approvalToolValidatesActionAndDoesNotTrustModelReasonForPrice() {
        BerryQuestTest.Fixture f=new BerryQuestTest.Fixture(); f.nearMerchant();
        assertEquals("UNKNOWN_ACTION",f.tools.execute(f.request("bad","request_player_approval","actionType","ADMIN",
            "itemId","BERRY","quantity",1,"reason","free")).getCode());
        ActionResult result=f.tools.execute(f.request("request","request_player_approval","actionType","PURCHASE_BERRY",
            "itemId","BERRY","quantity",1,"reason","zero coins"));
        assertEquals("1",result.getData().get("totalCost")); assertEquals(AgentTask.State.WAITING_APPROVAL,f.task.getState());
    }
    @Test void playerResponseAndExpiryAreVisibleToNextAgentDecision() {
        BerryQuestTest.Fixture f=new BerryQuestTest.Fixture(); f.nearMerchant();
        String id=f.session.requestPurchaseApproval("BERRY",1).getData().get("proposalId");
        assertEquals("WAITING",f.session.observation().get("approvalOutcome"));
        f.session.resolveApproval("owner",id,false); assertEquals("DENIED",f.session.observation().get("approvalOutcome"));
        String token=approve(f,1); assertEquals("APPROVED",f.session.observation().get("approvalOutcome"));
        f.session.purchase("BERRY",1,token); assertEquals("CONSUMED",f.session.observation().get("approvalOutcome"));
        f.session.requestPurchaseApproval("BERRY",1); f.clock.set(1000);
        assertEquals("EXPIRED",f.session.observation().get("approvalOutcome"));
    }
}
