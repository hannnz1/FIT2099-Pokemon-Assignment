package game.agent.quest;

import game.agent.action.ActionResult;
import game.agent.runtime.AgentTask;
import java.util.*;
import java.util.function.LongSupplier;

/** One pending proposal and one single-use grant, bounded per task session.
 * Owner identity must come from a future authenticated server session, never
 * directly from a browser field. Token capabilities are not durable across restart.
 */
final class PurchaseApprovalService {
    private static final class Proposal {
        final String id=UUID.randomUUID().toString();
        final int quantity,totalCost;
        final long expiresAt,revision;
        Proposal(int quantity,int totalCost,long expiresAt,long revision) {
            this.quantity=quantity; this.totalCost=totalCost; this.expiresAt=expiresAt; this.revision=revision;
        }
        Map<String,String> view() {
            Map<String,String> data=new LinkedHashMap<>(); data.put("proposalId",id); data.put("actionType","PURCHASE_BERRY");
            data.put("itemId","BERRY"); data.put("quantity",Integer.toString(quantity)); data.put("totalCost",Integer.toString(totalCost));
            data.put("expiresAt",Long.toString(expiresAt));
            data.put("reason","Purchase "+quantity+" BERRY for "+totalCost+" coins");
            return Collections.unmodifiableMap(data);
        }
    }
    private final String owner;
    private final AgentTask task;
    private final LongSupplier clock;
    private final long ttl;
    private Proposal pending,approved;
    private String token;
    private String outcome="NONE";
    PurchaseApprovalService(String owner,AgentTask task,LongSupplier clock,long ttl) {
        this.owner=owner; this.task=task; this.clock=clock; this.ttl=ttl;
    }
    ActionResult request(int quantity,int cost) {
        refresh(); long expiresAt=Math.addExact(clock.getAsLong(),ttl);
        approved=null; token=null; task.waitForApproval(); outcome="WAITING";
        pending=new Proposal(quantity,cost,expiresAt,task.getRevision());
        return ActionResult.of(ActionResult.Status.IN_PROGRESS,"APPROVAL_REQUESTED",pending.view());
    }
    ActionResult respond(String requester,String proposalId,boolean allow) {
        if(!owner.equals(requester)) return ActionResult.rejected("UNAUTHORIZED");
        refresh();
        if(pending==null || !pending.id.equals(proposalId) || task.getState()!=AgentTask.State.WAITING_APPROVAL)
            return ActionResult.rejected("STALE_APPROVAL");
        Proposal proposal=pending; pending=null; task.resolveApproval(allow);
        if(!allow) {
            outcome="DENIED";
            return ActionResult.of(ActionResult.Status.SUCCESS,"APPROVAL_DENIED",Collections.singletonMap("proposalId",proposal.id));
        }
        // Approval has its own fresh TTL, even if the player responds at the end of the proposal TTL.
        approved=new Proposal(proposal.quantity,proposal.totalCost,Math.addExact(clock.getAsLong(),ttl),task.getRevision());
        token=UUID.randomUUID().toString();
        outcome="APPROVED";
        return ActionResult.of(ActionResult.Status.SUCCESS,"APPROVED",Collections.singletonMap("approvalToken",token));
    }
    Map<String,String> pendingView(String requester) {
        if(!owner.equals(requester)) return Collections.emptyMap();
        refresh(); return pending==null?Collections.emptyMap():pending.view();
    }
    String validate(String suppliedToken,int quantity,int cost) {
        refresh();
        if(approved==null || token==null || !token.equals(suppliedToken)) return "APPROVAL_REQUIRED";
        return approved.quantity==quantity && approved.totalCost==cost ? null : "APPROVAL_MISMATCH";
    }
    void consume() { token=null; approved=null; outcome="CONSUMED"; }
    String availableToken() { refresh(); return token; }
    String outcome() { refresh(); return outcome; }
    void refresh() {
        long now=clock.getAsLong(),revision=task.getRevision();
        if(pending!=null && (pending.revision!=revision || now>=pending.expiresAt || task.getState()!=AgentTask.State.WAITING_APPROVAL)) {
            if(pending.revision==revision && task.getState()==AgentTask.State.WAITING_APPROVAL) task.resolveApproval(false);
            outcome=now>=pending.expiresAt?"EXPIRED":"INVALIDATED";
            pending=null;
        }
        if(approved!=null && (approved.revision!=revision || now>=approved.expiresAt
                || (task.getState()!=AgentTask.State.RUNNING && task.getState()!=AgentTask.State.REPLANNING))) {
            outcome=now>=approved.expiresAt?"EXPIRED":"INVALIDATED";
            token=null; approved=null;
        }
    }
}
