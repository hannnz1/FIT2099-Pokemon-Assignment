package game.agent.quest;

import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.items.Item;
import edu.monash.fit2099.engine.positions.*;
import game.agent.action.ActionResult;
import game.agent.runtime.AgentTask;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Authoritative opt-in quest rules. All calls and all map access use the world
 * thread. One session owns one companion/task/quest; balances and stock are demo
 * session state, separate from the legacy Candy economy. No database transaction
 * or multi-session economy is implied by this in-memory implementation.
 */
public final class BerryQuestSession {
    public enum QuestStatus { ACTIVE, COMPLETED, EXPIRED }
    public static final class Rules {
        final int requiredCount,capacity;
        final long deadlineTurn,approvalTtlMillis;
        public Rules(int requiredCount,long deadlineTurn,int capacity,long approvalTtlMillis) {
            if(requiredCount<=0 || deadlineTurn<=0 || capacity<requiredCount || approvalTtlMillis<=0)
                throw new IllegalArgumentException("Invalid quest rules");
            this.requiredCount=requiredCount; this.deadlineTurn=deadlineTurn;
            this.capacity=capacity; this.approvalTtlMillis=approvalTtlMillis;
        }
    }
    private String questId=UUID.randomUUID().toString();
    private final AgentTask task;
    private final Actor actor,professor,merchant;
    private final GameMap map;
    private final Rules rules;
    private boolean noSpending;
    private boolean manualControl;
    private java.util.function.Consumer<Map<String,Object>> sharedLedger;
    private boolean sharedFinalTurn;
    private long deadlineTurn;
    private final PurchaseApprovalService approvals;
    private QuestStatus status=QuestStatus.ACTIVE;
    private long turn;
    private int balance,stock,price;

    public BerryQuestSession(String ownerId,AgentTask task,Actor actor,GameMap map,Actor professor,Actor merchant,
                             Rules rules,int balance,int stock,int price,boolean noSpending,LongSupplier approvalClock) {
        if(ownerId==null || ownerId.trim().isEmpty() || balance<0 || stock<0 || price<=0)
            throw new IllegalArgumentException("Invalid session configuration");
        this.task=Objects.requireNonNull(task); this.actor=Objects.requireNonNull(actor);
        this.map=Objects.requireNonNull(map); this.professor=Objects.requireNonNull(professor); this.merchant=Objects.requireNonNull(merchant);
        this.rules=Objects.requireNonNull(rules); this.balance=balance; this.stock=stock; this.price=price;
        this.deadlineTurn=rules.deadlineTurn;
        this.noSpending=noSpending; this.approvals=new PurchaseApprovalService(ownerId,task,Objects.requireNonNull(approvalClock),rules.approvalTtlMillis);
    }
    public void enableShared(java.util.function.Consumer<Map<String,Object>> ledger){if(task.getState()!=AgentTask.State.CREATED||sharedLedger!=null)throw new IllegalStateException("SHARED_SETUP_ONLY");sharedLedger=Objects.requireNonNull(ledger);}
    public int requiredCount(){return rules.requiredCount;}
    public int deliveredCount(){return berries(professor.getInventory(),false).size();}
    public boolean nearProfessor(Actor source){return reachable(source,professor);}
    public boolean approvalWaiting(){return task.getState()==AgentTask.State.WAITING_APPROVAL;}
    private Map<String,Object> contribution(Actor source,String action,int quantity){Location p=map.locationOf(source);return game.agent.llm.Json.object("actor","AGENT","action",action,"quantity",quantity,"turn",turn,"x",p.x(),"y",p.y());}
    public ActionResult sharedPlayerDeliver(Actor player,java.util.function.Consumer<Map<String,Object>> ledger){
        expireQuest();approvals.refresh();if(sharedLedger==null)return ActionResult.rejected("COOP_REQUIRED");if(status!=QuestStatus.ACTIVE)return ActionResult.rejected("QUEST_NOT_ACTIVE");if(approvalWaiting())return ActionResult.rejected("APPROVAL_FREEZE");
        if(!map.contains(player)||!player.isConscious())return ActionResult.rejected("ACTOR_UNAVAILABLE");return deliverFrom(player,ledger);
    }
    private boolean stagedCompletion;
    public boolean isStagedCompletion(){return stagedCompletion;}
    public void configureStagedCompletion(){if(task.getState()!=AgentTask.State.CREATED)throw new IllegalStateException("Task already started");stagedCompletion=true;}
    public String getQuestId() { return questId; }
    public QuestStatus getQuestStatus() { expireQuest(); return status; }
    public int getBalance() { return balance; }
    public int getMerchantStock() { return stock; }
    public long getTurn() { return turn; }
    public long getDeadlineTurn() { return deadlineTurn; }
    /** Trusted persistence adapter. Approval capabilities are deliberately absent. */
    public Map<String,Object> exportState(){
        return game.agent.llm.Json.object("questId",questId,"status",status.name(),"turn",turn,"balance",balance,"stock",stock,"price",price,"noSpending",noSpending,"deadlineTurn",deadlineTurn);
    }
    public void restoreState(Map<String,Object> state){
        if(task.getState()!=AgentTask.State.CREATED||turn!=0)throw new IllegalStateException("RESTORE_NEW_SESSION_ONLY");
        if(!state.keySet().equals(new HashSet<>(Arrays.asList("questId","status","turn","balance","stock","price","noSpending","deadlineTurn"))))throw new IllegalArgumentException("INVALID_CHECKPOINT");
        String id=(String)state.get("questId");if(id==null||!id.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("INVALID_CHECKPOINT");
        long restoredTurn=number(state.get("turn")),deadline=number(state.get("deadlineTurn"));long coins=number(state.get("balance")),restoredStock=number(state.get("stock")),restoredPrice=number(state.get("price"));
        QuestStatus restoredStatus=QuestStatus.valueOf((String)state.get("status"));
        if(restoredTurn<0||restoredTurn>rules.deadlineTurn||deadline<1||deadline>rules.deadlineTurn||coins<0||coins>1000000||restoredStock<0||restoredStock>1000||restoredPrice<1||restoredPrice>1000000||!(state.get("noSpending") instanceof Boolean))throw new IllegalArgumentException("INVALID_CHECKPOINT");
        if(restoredStatus==QuestStatus.ACTIVE && restoredTurn>=deadline || restoredStatus==QuestStatus.EXPIRED && restoredTurn<deadline)throw new IllegalArgumentException("INVALID_CHECKPOINT");
        questId=id;turn=restoredTurn;deadlineTurn=deadline;balance=(int)coins;stock=(int)restoredStock;price=(int)restoredPrice;status=restoredStatus;noSpending=(Boolean)state.get("noSpending");
    }
    private static long number(Object value){return new java.math.BigDecimal(String.valueOf(value)).longValueExact();}
    /** A task may tighten, never extend, the authoritative NIGHT deadline. */
    public void configureDeadline(long value) {
        if(task.getState()!=AgentTask.State.CREATED) throw new IllegalStateException("Task already started");
        if(value<=0 || value>rules.deadlineTurn) throw new IllegalArgumentException("INVALID_DEADLINE");
        deadlineTurn=value;
    }
    /** Trusted world-thread manual controller; callbacks never originate from model data. */
    public ActionResult manualAction(Supplier<ActionResult> action) {
        Objects.requireNonNull(action);
        AgentTask.State state=task.getState();
        if(state==AgentTask.State.RUNNING || state==AgentTask.State.REPLANNING || state==AgentTask.State.WAITING_APPROVAL) return ActionResult.rejected("AI_CONTROLS_COMPANION");
        if(manualControl) return ActionResult.rejected("MANUAL_ACTION_ACTIVE");
        manualControl=true;
        try {
            String denied=guard();
            return denied==null?Objects.requireNonNull(action.get()):ActionResult.rejected(denied);
        }
        finally { manualControl=false; }
    }
    /** Trusted setup only, after player confirms parsed intent and before start. */
    public void configureNoSpending(boolean value) {
        if(task.getState()!=AgentTask.State.CREATED) throw new IllegalStateException("Task already started");
        noSpending=value;
    }

    /** Owning world scheduler must call this BEFORE advancing its world/map clock.
     * Approval freezes this opt-in session. Original World is not modified.
     */
    public boolean advanceTurn() {
        approvals.refresh();
        if(status==QuestStatus.COMPLETED&&sharedFinalTurn){sharedFinalTurn=false;turn=Math.addExact(turn,1);return true;}
        expireQuest();
        if(status!=QuestStatus.ACTIVE || task.getState()==AgentTask.State.WAITING_APPROVAL) return false;
        turn=Math.addExact(turn,1); expireQuest(); return true;
    }
    public ActionResult pickup(String itemId,int quantity) {
        String denied=guard(); if(denied!=null) return ActionResult.rejected(denied);
        if(!"BERRY".equals(itemId)) return ActionResult.rejected("UNKNOWN_ITEM");
        if(quantity<=0 || quantity>20) return ActionResult.rejected("INVALID_QUANTITY");
        List<Item> available=berries(map.locationOf(actor).getItems(),true);
        if(available.size()<quantity) return ActionResult.rejected("RESOURCE_NOT_AVAILABLE");
        if(actor.getInventory().size()+quantity>rules.capacity) return ActionResult.rejected("INVENTORY_FULL");
        for(int i=0;i<quantity;i++) available.get(i).getPickUpAction(actor).execute(actor,map);
        if(sharedLedger!=null)sharedLedger.accept(contribution(actor,"PICKUP",quantity));
        return result("PICKED_UP");
    }
    public ActionResult deliver(String requestedQuestId,String targetNpcId) {
        String denied=guard(); if(denied!=null) return ActionResult.rejected(denied);
        if(!questId.equals(requestedQuestId)) return ActionResult.rejected("UNKNOWN_QUEST");
        if(!"professor".equals(targetNpcId)) return ActionResult.rejected("UNKNOWN_TARGET");
        return deliverFrom(actor,sharedLedger);
    }
    private ActionResult deliverFrom(Actor source,java.util.function.Consumer<Map<String,Object>> ledger){
        if(!reachable(source,professor)) return ActionResult.rejected("OUT_OF_REACH");
        List<Item> carried=berries(source.getInventory(),false);int quantity=sharedLedger==null?rules.requiredCount:Math.min(carried.size(),Math.max(0,rules.requiredCount-deliveredCount()));
        if(quantity<1||carried.size()<quantity)return ActionResult.rejected("INSUFFICIENT_BERRIES");
        for(int i=0;i<quantity;i++){Item berry=carried.get(i);source.removeItemFromInventory(berry);professor.addItemToInventory(berry);}
        if(ledger!=null)ledger.accept(contribution(source,"DELIVER",quantity));
        if(deliveredCount()<rules.requiredCount)return result("SHARED_DELIVERED");
        if(stagedCompletion)return result("BERRY_STAGE_COMPLETED");
        status=QuestStatus.COMPLETED;sharedFinalTurn=sharedLedger!=null;AgentTask.State state=task.getState();
        if(state==AgentTask.State.CREATED){task.start();task.markCompleted();}
        else if(state!=AgentTask.State.CANCELLED&&state!=AgentTask.State.FAILED&&state!=AgentTask.State.COMPLETED)task.markCompleted();
        return result("QUEST_COMPLETED");
    }
    public ActionResult requestPurchaseApproval(String itemId,int quantity) {
        String denied=guard(); if(denied!=null) return ActionResult.rejected(denied);
        denied=purchaseRules(itemId,quantity); if(denied!=null) return ActionResult.rejected(denied);
        return approvals.request(quantity,price*quantity);
    }
    /** Trusted player-control API: the server must supply the authenticated owner. */
    public ActionResult resolveApproval(String owner,String proposalId,boolean allow) {
        expireQuest(); return approvals.respond(owner,proposalId,allow);
    }
    public Map<String,String> pendingApproval(String owner) { expireQuest(); return approvals.pendingView(owner); }
    public ActionResult purchase(String itemId,int quantity,String approvalToken) {
        String denied=guard(); if(denied!=null) return ActionResult.rejected(denied);
        denied=purchaseRules(itemId,quantity); if(denied!=null) return ActionResult.rejected(denied);
        int cost=price*quantity;
        denied=approvals.validate(approvalToken,quantity,cost); if(denied!=null) return ActionResult.rejected(denied);
        List<Berry> products=new ArrayList<>(); for(int i=0;i<quantity;i++) products.add(new Berry());
        balance-=cost; stock-=quantity;
        for(Berry berry:products) actor.addItemToInventory(berry);
        approvals.consume(); if(sharedLedger!=null)sharedLedger.accept(contribution(actor,"PURCHASE",quantity));return result("PURCHASED");
    }
    /** Trusted merchant/world update, never registered as a model tool. */
    public void updateMerchantOffer(int stock,int price) {
        if(stock<0 || price<=0) throw new IllegalArgumentException("Invalid merchant offer");
        this.stock=stock; this.price=price;
    }
    private String purchaseRules(String itemId,int quantity) {
        if(!"BERRY".equals(itemId)) return "UNKNOWN_ITEM";
        if(quantity<=0 || quantity>20) return "INVALID_QUANTITY";
        if(!reachable(merchant)) return "OUT_OF_REACH";
        if(stock<quantity) return "RESOURCE_NOT_AVAILABLE";
        if(actor.getInventory().size()+quantity>rules.capacity) return "INVENTORY_FULL";
        long cost=(long)price*quantity;
        if(cost>Integer.MAX_VALUE) return "PRICE_OVERFLOW";
        if(balance<cost) return "INSUFFICIENT_FUNDS";
        return null;
    }
    /** Scope-safe provider observation. Distant shop inventory is not exposed. */
    public Map<String,String> observation() {
        expireQuest(); approvals.refresh(); Map<String,String> data=new LinkedHashMap<>();
        data.put("questId",questId); data.put("questStatus",status.name()); data.put("taskState",task.getState().name());
        data.put("requiredBerry",Integer.toString(rules.requiredCount)); data.put("carriedBerry",Integer.toString(berries(actor.getInventory(),false).size()));
        if(sharedLedger!=null){data.put("sharedQuest","true");data.put("deliveredBerry",Integer.toString(deliveredCount()));data.put("remainingBerry",Integer.toString(Math.max(0,rules.requiredCount-deliveredCount())));}
        data.put("turn",Long.toString(turn)); data.put("deadlineTurn",Long.toString(deadlineTurn));
        data.put("coins",Integer.toString(balance)); data.put("noSpending",Boolean.toString(noSpending));
        data.put("approvalOutcome",approvals.outcome());
        String token=approvals.availableToken(); if(token!=null) data.put("approvalToken",token);
        if(map.contains(actor)) {
            Location here=map.locationOf(actor); data.put("x",Integer.toString(here.x())); data.put("y",Integer.toString(here.y()));
            data.put("visibleBerry",Integer.toString(berries(here.getItems(),true).size()));
            data.put("nearProfessor",Boolean.toString(reachable(professor))); data.put("nearMerchant",Boolean.toString(reachable(merchant)));
            if(reachable(merchant)) { data.put("merchantStock",Integer.toString(stock)); data.put("berryPrice",Integer.toString(price)); }
        }
        return Collections.unmodifiableMap(data);
    }
    private ActionResult result(String code) { return ActionResult.of(ActionResult.Status.SUCCESS,code,observation()); }
    private String guard() {
        expireQuest(); approvals.refresh();
        if(status==QuestStatus.EXPIRED) return "QUEST_EXPIRED";
        if(status!=QuestStatus.ACTIVE) return "QUEST_NOT_ACTIVE";
        if(!manualControl && task.getState()!=AgentTask.State.RUNNING && task.getState()!=AgentTask.State.REPLANNING) return "TASK_INACTIVE";
        if(!map.contains(actor) || !actor.isConscious()) return "ACTOR_UNAVAILABLE";
        return null;
    }
    private void expireQuest() {
        if(status==QuestStatus.ACTIVE && turn>=deadlineTurn) {
            status=QuestStatus.EXPIRED;
            AgentTask.State state=task.getState();
            if(state!=AgentTask.State.CREATED && state!=AgentTask.State.COMPLETED && state!=AgentTask.State.CANCELLED && state!=AgentTask.State.FAILED) task.fail();
        }
    }
    private boolean reachable(Actor target){return reachable(actor,target);}
    private boolean reachable(Actor source,Actor target) {
        if(!map.contains(source) || !map.contains(target) || !target.isConscious()) return false;
        for(Exit exit:map.locationOf(source).getExits()) if(exit.getDestination()==map.locationOf(target)) return true;
        return false;
    }
    private List<Item> berries(List<Item> items,boolean requirePortable) {
        List<Item> result=new ArrayList<>(); Set<Item> seen=Collections.newSetFromMap(new IdentityHashMap<Item,Boolean>());
        for(Item item:items) if(item instanceof Berry && (!requirePortable || item.getPickUpAction(actor)!=null) && seen.add(item)) result.add(item);
        return result;
    }
}
