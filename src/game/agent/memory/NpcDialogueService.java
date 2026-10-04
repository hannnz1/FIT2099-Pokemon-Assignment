package game.agent.memory;

import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.positions.*;
import game.agent.action.ActionResult;
import game.agent.llm.Json;
import game.agent.quest.*;
import game.agent.runtime.AgentTask;
import java.util.*;

/** Trusted world-thread adapter. Dialogue returns bounded historical claims, never a world mutation. */
public final class NpcDialogueService {
    public enum Role { MEMORY, PROFESSOR, MERCHANT }
    private static final class Npc {final Actor actor;final Role role;Npc(Actor a,Role r){actor=a;role=r;}}
    private final Actor companion;
    private final GameMap map;
    private final AgentTask task;
    private final BerryQuestSession quest;
    private final MemoryService memory;
    private final String worldId;
    private final Map<String,Location> landmarks;
    private final Map<String,Npc> npcs=new LinkedHashMap<>();
    private final Map<String,Integer> observed=new HashMap<>();
    private Map<String,String> last=Collections.emptyMap();
    private final Deque<Map<String,String>> dialogueHistory=new ArrayDeque<>();
    private int sequence;
    public NpcDialogueService(Actor companion,GameMap map,AgentTask task,BerryQuestSession quest,
                              MemoryService memory,String worldId,Map<String,Location> landmarks) {
        this.companion=Objects.requireNonNull(companion);this.map=Objects.requireNonNull(map);this.task=Objects.requireNonNull(task);
        this.quest=Objects.requireNonNull(quest);this.memory=Objects.requireNonNull(memory);this.worldId=Objects.requireNonNull(worldId);
        this.landmarks=new LinkedHashMap<>(landmarks);
    }
    public void bind(String id,Actor actor,Role role) {
        if(id==null || npcs.containsKey(id) || !map.contains(actor))throw new IllegalArgumentException("INVALID_NPC");
        npcs.put(id,new Npc(actor,Objects.requireNonNull(role)));
    }
    /** Real visible items on an open demo map; radius 2, at most 64 changed observations. */
    public void perceive(long turn) {
        if(turn<0)throw new IllegalArgumentException("INVALID_TURN");
        for(Map.Entry<String,Npc> entry:npcs.entrySet()) {
            Npc npc=entry.getValue();if(npc.role!=Role.MEMORY || !map.contains(npc.actor)||!npc.actor.isConscious())continue;
            Location here=map.locationOf(npc.actor);
            for(int x:map.getXRange())for(int y:map.getYRange()) {
                if(Math.hypot(x-here.x(),y-here.y())>2)continue;
                Location tile=map.at(x,y);int count=(int)tile.getItems().stream().filter(Berry.class::isInstance).count();
                String key=entry.getKey()+":"+x+":"+y;Integer previous=observed.put(key,count);
                if(sequence>=64 || (previous!=null && previous==count) || (count==0 && (previous==null||previous==0)))continue;
                String content=Json.write(Json.object("quantity",count,"locationId",locationId(tile)));
                memory.record(new NpcMemory("observation-"+(++sequence),worldId,entry.getKey(),"BERRY_SEEN","BERRY",content,
                    "berry-path",x,y,turn,NpcMemory.Source.SELF_OBSERVATION,null));
            }
        }
    }
    public boolean near(String id) {Npc npc=npcs.get(id);return npc!=null && reachable(npc.actor);}
    public ActionResult talk(String target,String message) {
        if(task.getState()!=AgentTask.State.RUNNING && task.getState()!=AgentTask.State.REPLANNING)return ActionResult.rejected("TASK_INACTIVE");
        if(message==null||message.trim().isEmpty()||message.length()>500)return ActionResult.rejected("INVALID_MESSAGE");
        Npc npc=npcs.get(target);if(npc==null)return ActionResult.rejected("UNKNOWN_NPC");
        if(!reachable(npc.actor))return ActionResult.rejected("OUT_OF_REACH");
        Map<String,String> data=new LinkedHashMap<>();data.put("speakerId",target);data.put("turn",Long.toString(quest.getTurn()));
        if(npc.role==Role.MEMORY) {
            List<Object> claims=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(NpcMemory event:memory.recall(worldId,target,"BERRY",quest.getTurn(),64)) {
                if(event.getSource()!=NpcMemory.Source.SELF_OBSERVATION || !seen.add(event.getX()+":"+event.getY()))continue;
                Map<String,Object> content=Json.asObject(Json.read(event.getContent()));
                claims.add(Json.object("locationId",content.get("locationId"),"x",event.getX(),"y",event.getY(),"quantity",content.get("quantity"),
                    "occurredAt",event.getOccurredAt(),"source",event.getSource().name()));if(claims.size()==3)break;
            }
            StringBuilder words=new StringBuilder("这些是我的历史观察，请到现场确认：");
            for(Object value:claims) {Map<String,Object> claim=Json.asObject(value);words.append("第").append(claim.get("occurredAt")).append("回合，在（")
                .append(claim.get("x")).append(",").append(claim.get("y")).append("）看见").append(claim.get("quantity")).append("个树果。");}
            data.put("message",claims.isEmpty()?"我没有观察到树果的记忆。":words.toString());data.put("historical","true");data.put("memories",Json.write(claims));
        } else {
            Map<String,String> facts=quest.observation();data.put("historical","false");
            if(npc.role==Role.PROFESSOR) {
                data.put("questId",facts.get("questId"));data.put("requiredBerry",facts.get("requiredBerry"));data.put("deadlineTurn",facts.get("deadlineTurn"));data.put("questStatus",facts.get("questStatus"));
                data.put("message","请在第"+facts.get("deadlineTurn")+"个行动回合之前把"+facts.get("requiredBerry")+"个树果交给我，完成条件由任务规则检查。");
            } else {
                data.put("merchantStock",facts.get("merchantStock"));data.put("berryPrice",facts.get("berryPrice"));
                data.put("message","现有"+facts.get("merchantStock")+"个树果，每个"+facts.get("berryPrice")+"金币。购买仍需要玩家批准。");
            }
        }
        last=Collections.unmodifiableMap(new LinkedHashMap<>(data));if(dialogueHistory.size()==8)dialogueHistory.removeFirst();dialogueHistory.addLast(last);
        return ActionResult.of(ActionResult.Status.SUCCESS,"NPC_DIALOGUE",data);
    }
    public Map<String,String> publicDialogue() {return last;}
    public List<Map<String,String>> publicDialogues(){return Collections.unmodifiableList(new ArrayList<>(dialogueHistory));}
    /** Trusted checkpoint adapter, never registered as a model tool. */
    public Map<String,Object> exportState(){
        List<Object> events=new ArrayList<>();for(NpcMemory e:memory.snapshot())if(e.getWorldId().equals(worldId))
            events.add(Json.object("id",e.getEventId(),"npc",e.getNpcId(),"content",e.getContent(),"x",e.getX(),"y",e.getY(),"turn",e.getOccurredAt()));
        return Json.object("events",events,"observed",new LinkedHashMap<>(observed),"sequence",sequence,"dialogues",new ArrayList<>(dialogueHistory));
    }
    public void restoreState(Map<String,Object> state){
        if(!state.keySet().equals(new HashSet<>(Arrays.asList("events","observed","sequence","dialogues"))))throw new IllegalArgumentException("INVALID_MEMORY_CHECKPOINT");
        List<Object> events=Json.asArray(state.get("events")),history=Json.asArray(state.get("dialogues"));
        int seq=new java.math.BigDecimal(String.valueOf(state.get("sequence"))).intValueExact();
        if(seq<0||seq>64||events.size()>64||history.size()>8)throw new IllegalArgumentException("INVALID_MEMORY_CHECKPOINT");
        List<NpcMemory> restored=new ArrayList<>();for(Object value:events){Map<String,Object> e=Json.asObject(value);
            String npc=(String)e.get("npc"),content=(String)e.get("content");if(!npcs.containsKey(npc)||content.length()>1000)throw new IllegalArgumentException("INVALID_MEMORY_CHECKPOINT");
            Map<String,Object> claim=Json.asObject(Json.read(content));int quantity=integer(claim.get("quantity"));if(quantity<0||quantity>20)throw new IllegalArgumentException("INVALID_MEMORY_CHECKPOINT");
            int x=integer(e.get("x")),y=integer(e.get("y"));if(!map.getXRange().contains(x)||!map.getYRange().contains(y))throw new IllegalArgumentException("INVALID_MEMORY_CHECKPOINT");
            long turn=new java.math.BigDecimal(String.valueOf(e.get("turn"))).longValueExact();if(turn>quest.getTurn())throw new IllegalArgumentException("INVALID_MEMORY_CHECKPOINT");
            restored.add(new NpcMemory((String)e.get("id"),worldId,npc,"BERRY_SEEN","BERRY",content,"berry-path",x,y,turn,NpcMemory.Source.SELF_OBSERVATION,null));
        }
        Map<String,Integer> counts=new HashMap<>();for(Map.Entry<String,Object> e:Json.asObject(state.get("observed")).entrySet()){
            if(!e.getKey().matches("(treecko|merchant|professor):[0-9]{1,3}:[0-9]{1,3}"))throw new IllegalArgumentException("INVALID_MEMORY_CHECKPOINT");
            int count=integer(e.getValue());if(count<0||count>20||counts.size()>1000)throw new IllegalArgumentException("INVALID_MEMORY_CHECKPOINT");counts.put(e.getKey(),count);
        }
        List<Map<String,String>> replies=new ArrayList<>();for(Object value:history){Map<String,String> reply=new LinkedHashMap<>();for(Map.Entry<String,Object> e:Json.asObject(value).entrySet()){
            if(!(e.getValue() instanceof String)||((String)e.getValue()).length()>4000||e.getKey().equals("approvalToken"))throw new IllegalArgumentException("INVALID_MEMORY_CHECKPOINT");reply.put(e.getKey(),(String)e.getValue());
        }if(!npcs.containsKey(reply.get("speakerId")))throw new IllegalArgumentException("INVALID_MEMORY_CHECKPOINT");replies.add(Collections.unmodifiableMap(reply));}
        memory.restore(restored);observed.clear();observed.putAll(counts);sequence=seq;dialogueHistory.clear();dialogueHistory.addAll(replies);last=replies.isEmpty()?Collections.emptyMap():replies.get(replies.size()-1);
    }
    private static int integer(Object value){return new java.math.BigDecimal(String.valueOf(value)).intValueExact();}
    public Map<String,Object> publicPositions() {
        Map<String,Object> result=new LinkedHashMap<>();for(Map.Entry<String,Npc> entry:npcs.entrySet())if(map.contains(entry.getValue().actor)) {
            Location p=map.locationOf(entry.getValue().actor);result.put(entry.getKey(),Json.object("x",p.x(),"y",p.y()));
        }return result;
    }
    private String locationId(Location tile) {for(Map.Entry<String,Location> e:landmarks.entrySet())if(e.getValue()==tile)return e.getKey();return "visible-tile";}
    private boolean reachable(Actor actor) {
        if(!map.contains(companion)||!map.contains(actor)||!companion.isConscious()||!actor.isConscious())return false;
        for(Exit exit:map.locationOf(companion).getExits())if(exit.getDestination()==map.locationOf(actor))return true;return false;
    }
}
