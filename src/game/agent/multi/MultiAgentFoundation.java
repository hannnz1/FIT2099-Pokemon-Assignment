package game.agent.multi;

import game.agent.llm.Json;
import game.agent.memory.*;
import java.math.BigDecimal;
import java.util.*;

/** V3 foundation only. Trusted server API; messages are historical claims, not live facts.
 * This class selects bounded work but never executes a game action or calls a model.
 * The caller must validate and commit any authorized tool on its world thread.
 */
public final class MultiAgentFoundation {
    public enum Role { MUDKIP, TORCHIC, PROFESSOR, MERCHANT }
    private static final int LIMIT=64;
    private final String worldId;
    private final int budget;
    private final EnumMap<Role,Integer> used=new EnumMap<>(Role.class);
    private final LinkedHashMap<String,Map<String,Object>> messages=new LinkedHashMap<>();
    private final MemoryService memory=new MemoryService();
    private int cursor;
    private long lastTurn;
    private Map<String,Object> active;

    public MultiAgentFoundation(String worldId,int budget) {
        this.worldId=id(worldId);if(budget<1||budget>32)throw invalid("INVALID_BUDGET");this.budget=budget;
        for(Role role:Role.values())used.put(role,0);
    }
    public static String agentId(Role role){return Objects.requireNonNull(role).name().toLowerCase(Locale.ROOT);}
    /** Minimal initial permissions. NPCs cannot mutate inventory or control another actor. */
    public static Set<String> tools(Role role) {
        Objects.requireNonNull(role);
        List<String> list=role==Role.MUDKIP?Arrays.asList("observe","send_message","relay_message","reflect_plan","move"):
            role==Role.TORCHIC?Arrays.asList("observe","send_message","relay_message","reflect_plan","move"):
            Arrays.asList("observe","send_message","relay_message","reflect_plan");
        return Collections.unmodifiableSet(new LinkedHashSet<>(list));
    }
    /** One in-flight lease per world; budget charged at dispatch, including failed calls. */
    public synchronized int remaining(Role role){return budget-used.get(Objects.requireNonNull(role));}
    public synchronized Map<String,Object> next(long worldTurn,long worldRevision) {return next(worldTurn,worldRevision,EnumSet.allOf(Role.class));}
    /** Only eligible roles consume dispatch budget; the shared round-robin cursor remains fair. */
    public synchronized Map<String,Object> next(long worldTurn,long worldRevision,Set<Role> eligible) {
        Objects.requireNonNull(eligible);if(eligible.contains(null))throw invalid("UNKNOWN_AGENT");
        if(worldRevision<0)throw invalid("INVALID_REVISION");
        if(active!=null)throw invalid("LEASE_ACTIVE");
        turn(worldTurn);
        for(int i=0;i<Role.values().length;i++){
            Role role=Role.values()[cursor];cursor=(cursor+1)%Role.values().length;
            if(!eligible.contains(role)||used.get(role)>=budget)continue;
            used.put(role,used.get(role)+1);
            active=Json.object("leaseId",UUID.randomUUID().toString(),"agentId",agentId(role),
                "turn",worldTurn,"revision",worldRevision);
            return copy(active);
        }
        return null;
    }
    /** Consumes lease before returning. A stale, repeated or forbidden result cannot be applied. */
    public synchronized String finish(String leaseId,String tool,long currentRevision) {
        if(active==null||!active.get("leaseId").equals(leaseId))return "UNKNOWN_LEASE";
        Map<String,Object> lease=active;active=null;
        if(currentRevision!=number(lease.get("revision")))return "STALE_OBSERVATION";
        if(!tools(role((String)lease.get("agentId"))).contains(tool))return "TOOL_FORBIDDEN";
        return "AUTHORIZED";
    }
    /** Called with authenticated sender and engine-confirmed observation, never raw model memory writes. */
    public synchronized boolean send(String messageId,Role sender,Role recipient,String subject,String content,
                                      String area,int x,int y,long observedTurn,long sentTurn,long expiresTurn) {
        id(messageId);Objects.requireNonNull(sender);Objects.requireNonNull(recipient);
        if(sender==recipient)throw invalid("SELF_MESSAGE");
        text(subject,128);text(content,1000);id(area);
        if(observedTurn<0||sentTurn<observedTurn||expiresTurn<sentTurn||expiresTurn-sentTurn>10)
            throw invalid("INVALID_MESSAGE_TIME");
        Map<String,Object> row=Json.object("id",messageId,"sender",agentId(sender),"recipient",agentId(recipient),
            "subject",subject,"content",content,"area",area,"x",x,"y",y,"observedTurn",observedTurn,
            "sentTurn",sentTurn,"expiresTurn",expiresTurn);
        Map<String,Object> old=messages.get(messageId);
        if(old!=null){if(!old.equals(row))throw invalid("MESSAGE_ID_REUSED");return false;}
        if(messages.size()>=LIMIT)throw invalid("MESSAGE_LIMIT");
        turn(sentTurn);
        messages.put(messageId,row);
        memory.record(new NpcMemory(messageId,worldId,agentId(recipient),"AGENT_MESSAGE",subject,content,
            area,x,y,observedTurn,NpcMemory.Source.NPC_MESSAGE,agentId(sender)));
        return true;
    }
    /** Relay an existing recipient-visible receipt without rewriting content or extending freshness. */
    public synchronized boolean relay(String id,Role sender,Role recipient,String sourceId,long turn){
        Map<String,Object> source=messages.get(sourceId);if(source==null||!agentId(sender).equals(source.get("recipient")))throw invalid("MESSAGE_NOT_RECEIVED");
        if(turn<number(source.get("sentTurn"))||turn>number(source.get("expiresTurn")))throw invalid("MESSAGE_EXPIRED");
        List<Object> route=source.containsKey("route")?new ArrayList<>(Json.asArray(source.get("route"))):new ArrayList<>(Arrays.asList(source.get("sender"),source.get("recipient")));
        if(route.size()>=4||route.contains(agentId(recipient)))throw invalid("MESSAGE_LOOP");route.add(agentId(recipient));
        String root=source.containsKey("rootMessageId")?(String)source.get("rootMessageId"):sourceId;
        for(Map<String,Object> old:messages.values())if(root.equals(old.get("rootMessageId"))&&agentId(recipient).equals(old.get("recipient")))return false;
        boolean added=send(id,sender,recipient,(String)source.get("subject"),(String)source.get("content"),(String)source.get("area"),exactInt(source.get("x")),exactInt(source.get("y")),number(source.get("observedTurn")),turn,number(source.get("expiresTurn")));
        if(added){Map<String,Object> row=messages.get(id);row.put("sourceMessageId",sourceId);row.put("rootMessageId",root);row.put("route",route);}return added;
    }
    /** Recipient-scoped views. Expired claims stay in history and require fresh engine observation. */
    public synchronized List<Map<String,Object>> inbox(Role recipient,long asOfTurn) {
        Objects.requireNonNull(recipient);if(asOfTurn<0)throw invalid("INVALID_TURN");
        List<Map<String,Object>> rows=new ArrayList<>();
        for(Map<String,Object> row:messages.values())if(agentId(recipient).equals(row.get("recipient"))&&number(row.get("sentTurn"))<=asOfTurn){
            Map<String,Object> view=copy(row);view.put("source","NPC_MESSAGE");view.put("liveFact",false);
            view.put("requiresObservation",true);view.put("expired",asOfTurn>number(row.get("expiresTurn")));rows.add(view);
        }
        return rows;
    }
    public synchronized List<NpcMemory> recall(Role recipient,String subject,long asOfTurn,int limit) {
        Objects.requireNonNull(recipient);if(limit<0)throw invalid("INVALID_LIMIT");
        Set<String> visible=new HashSet<>();for(Map<String,Object> row:inbox(recipient,asOfTurn))visible.add((String)row.get("id"));
        List<NpcMemory> result=new ArrayList<>();
        for(NpcMemory event:memory.recall(worldId,agentId(recipient),subject,asOfTurn,LIMIT))
            if(visible.contains(event.getEventId())&&result.size()<limit)result.add(event);
        return Collections.unmodifiableList(result);
    }
    /** Checkpoint contains receipts and consumed budgets. In-flight leases are never replayed on restore. */
    public synchronized Map<String,Object> checkpoint() {
        List<Map<String,Object>> agents=new ArrayList<>();
        for(Role role:Role.values())agents.add(Json.object("agentId",agentId(role),"used",used.get(role)));
        return copy(Json.object("schemaVersion",1,"worldId",worldId,"budget",budget,"cursor",cursor,"lastTurn",lastTurn,
            "agents",agents,"messages",new ArrayList<>(messages.values())));
    }
    public static MultiAgentFoundation restore(Map<String,Object> saved) {
        if(number(saved.get("schemaVersion"))!=1)throw invalid("INVALID_CHECKPOINT");
        MultiAgentFoundation runtime=new MultiAgentFoundation((String)saved.get("worldId"),exactInt(saved.get("budget")));
        int cursor=exactInt(saved.get("cursor"));long last=number(saved.get("lastTurn"));
        if(cursor<0||cursor>=Role.values().length||last<0)throw invalid("INVALID_CHECKPOINT");
        Set<Role> seen=new HashSet<>();
        for(Object item:Json.asArray(saved.get("agents"))){Map<String,Object> row=Json.asObject(item);Role role=role((String)row.get("agentId"));
            int used=exactInt(row.get("used"));if(!seen.add(role)||used<0||used>runtime.budget)throw invalid("INVALID_CHECKPOINT");runtime.used.put(role,used);}
        if(seen.size()!=Role.values().length)throw invalid("INVALID_CHECKPOINT");
        List<Object> messages=Json.asArray(saved.get("messages"));if(messages.size()>LIMIT)throw invalid("MESSAGE_LIMIT");
        for(Object item:messages){Map<String,Object> row=Json.asObject(item);
            if(row.containsKey("sourceMessageId")){if(!runtime.relay((String)row.get("id"),role((String)row.get("sender")),role((String)row.get("recipient")),(String)row.get("sourceMessageId"),number(row.get("sentTurn")))||!copy(runtime.messages.get(row.get("id"))).equals(copy(row)))throw invalid("INVALID_RELAY_CHECKPOINT");continue;}
            if(!runtime.send((String)row.get("id"),role((String)row.get("sender")),role((String)row.get("recipient")),
                (String)row.get("subject"),(String)row.get("content"),(String)row.get("area"),exactInt(row.get("x")),exactInt(row.get("y")),
                number(row.get("observedTurn")),number(row.get("sentTurn")),number(row.get("expiresTurn"))))throw invalid("INVALID_CHECKPOINT");
        }
        if(last<runtime.lastTurn)throw invalid("INVALID_CHECKPOINT");runtime.lastTurn=last;runtime.cursor=cursor;return runtime;
    }
    private void turn(long turn){if(turn<lastTurn)throw invalid("TURN_REWIND");lastTurn=turn;}
    private static Role role(String value){for(Role r:Role.values())if(agentId(r).equals(value))return r;throw invalid("UNKNOWN_AGENT");}
    private static String id(String value){if(value==null||!value.matches("[a-zA-Z0-9_-]{1,128}"))throw invalid("INVALID_ID");return value;}
    private static void text(String value,int limit){if(value==null||value.trim().isEmpty()||value.length()>limit)throw invalid("INVALID_TEXT");}
    private static long number(Object value){try{return new BigDecimal(String.valueOf(value)).longValueExact();}catch(RuntimeException e){throw invalid("INVALID_NUMBER");}}
    private static int exactInt(Object value){long n=number(value);if(n<Integer.MIN_VALUE||n>Integer.MAX_VALUE)throw invalid("INVALID_NUMBER");return(int)n;}
    private static Map<String,Object> copy(Map<String,Object> value){return Json.asObject(Json.read(Json.write(value)));}
    private static IllegalArgumentException invalid(String code){return new IllegalArgumentException(code);}
}
