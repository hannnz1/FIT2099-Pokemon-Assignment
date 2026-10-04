package game.agent.multi;
import game.agent.llm.Json;
import game.agent.multi.MultiAgentFoundation.Role;
import java.util.*;

/** Bounded, engine-grounded observation history. Summaries are rules, not model-generated facts. */
public final class NpcKnowledge {
    private final EnumMap<Role,List<Map<String,Object>>> history=new EnumMap<>(Role.class);
    private final EnumMap<Role,LinkedHashMap<String,Map<String,Object>>> archive=new EnumMap<>(Role.class);
    public boolean observe(Role role,Map<String,String> observation){
        long turn=Long.parseLong(observation.get("turn"));if(turn<0)throw new IllegalArgumentException("INVALID_TURN");
        String facts=observation.get("facts");Json.asArray(Json.read(facts));
        List<Map<String,Object>> rows=history.computeIfAbsent(role,r->new ArrayList<>());
        Map<String,Object> previous=rows.isEmpty()?null:rows.get(rows.size()-1);
        if(previous!=null&&turn<lastSeen(previous))throw new IllegalArgumentException("TURN_REWIND");
        if(previous!=null&&facts.equals(previous.get("facts"))&&Objects.equals(observation.get("position"),previous.get("position"))){previous.put("lastSeenTurn",turn);return false;}
        Map<String,Object> row=Json.object("id",UUID.randomUUID().toString(),"turn",turn,"lastSeenTurn",turn,"facts",facts,"position",observation.get("position"),"source","SELF_OBSERVATION");
        rows.add(row);if(rows.size()>16)compact(role,rows.remove(0));return true;
    }
    public Map<String,Object> view(Role role){
        List<Map<String,Object>> rows=history.getOrDefault(role,Collections.emptyList());
        List<String> evidence=new ArrayList<>();for(int i=Math.max(0,rows.size()-2);i<rows.size();i++)evidence.add((String)rows.get(i).get("id"));
        String summary=rows.isEmpty()?"尚无自身观察":rows.size()==1?"已有一次自身观察；消息仍需现场核实":"自身观察发生变化；旧信息不能直接作为当前事实";
        return copy(Json.object("agentId",MultiAgentFoundation.agentId(role),"observations",rows,"retrieved",recall(role,"",6),"longTerm",new ArrayList<>(archive.getOrDefault(role,new LinkedHashMap<>()).values()),"compressed",archive.getOrDefault(role,new LinkedHashMap<>()).size(),"reflection",Json.object("kind","RULE_BASED","evidenceIds",evidence,"summary",summary),
            "plan",Json.object("kind","RULE_BASED","next","OBSERVE_THEN_SHARE_CHANGED_FACTS","approvalRequiredForPurchase",true)));
    }
    public Map<String,Object> checkpoint(){Map<String,Object> out=new LinkedHashMap<>();for(Role role:Role.values())out.put(MultiAgentFoundation.agentId(role),history.getOrDefault(role,Collections.emptyList()));Map<String,Object> compacted=new LinkedHashMap<>();for(Role role:Role.values())compacted.put(MultiAgentFoundation.agentId(role),new ArrayList<>(archive.getOrDefault(role,new LinkedHashMap<>()).values()));out.put("_archive",compacted);return copy(out);}
    public static NpcKnowledge restore(Map<String,Object> saved){NpcKnowledge k=new NpcKnowledge();Set<String> seen=new HashSet<>();
        for(Role role:Role.values()){String agent=MultiAgentFoundation.agentId(role);List<Object> rows=Json.asArray(saved.get(agent));if(rows.size()>16)throw new IllegalArgumentException("MEMORY_LIMIT");List<Map<String,Object>> restored=new ArrayList<>();long prior=-1;
            for(Object item:rows){Map<String,Object> row=Json.asObject(item);long turn=new java.math.BigDecimal(String.valueOf(row.get("turn"))).longValueExact();
                long last=lastSeen(row);if(last<turn)throw new IllegalArgumentException("INVALID_MEMORY_TIME");
                if(turn<0||turn<prior||!"SELF_OBSERVATION".equals(row.get("source"))||!(row.get("id") instanceof String)||!seen.add((String)row.get("id")))throw new IllegalArgumentException("INVALID_MEMORY");
                Json.asArray(Json.read((String)row.get("facts")));if(row.get("position")!=null)Json.asObject(Json.read((String)row.get("position")));prior=last;restored.add(copy(row));}
            k.history.put(role,restored);
        }
        if(saved.containsKey("_archive"))for(Role role:Role.values()){
            List<Object> entries=Json.asArray(Json.asObject(saved.get("_archive")).get(MultiAgentFoundation.agentId(role)));if(entries.size()>32)throw new IllegalArgumentException("ARCHIVE_LIMIT");
            LinkedHashMap<String,Map<String,Object>> table=new LinkedHashMap<>();for(Object item:entries){Map<String,Object> row=Json.asObject(item);if(!"SELF_OBSERVATION".equals(row.get("source"))||!(row.get("id") instanceof String)||!(row.get("key") instanceof String)||table.containsKey(row.get("key")))throw new IllegalArgumentException("INVALID_ARCHIVE");
                long first=new java.math.BigDecimal(String.valueOf(row.get("firstTurn"))).longValueExact(),last=new java.math.BigDecimal(String.valueOf(row.get("lastTurn"))).longValueExact(),count=new java.math.BigDecimal(String.valueOf(row.get("count"))).longValueExact();if(first<0||last<first||count<1)throw new IllegalArgumentException("INVALID_ARCHIVE");Json.asObject(row.get("fact"));table.put((String)row.get("key"),copy(row));}k.archive.put(role,table);
        }return k;
    }
    private void compact(Role role,Map<String,Object> snapshot){
        LinkedHashMap<String,Map<String,Object>> table=archive.computeIfAbsent(role,r->new LinkedHashMap<>());
        for(Object item:Json.asArray(Json.read((String)snapshot.get("facts")))){Map<String,Object> fact=Json.asObject(item);String key=String.valueOf(fact.get("id"));Map<String,Object> previous=table.remove(key);
            table.put(key,Json.object("id",snapshot.get("id"),"key",key,"fact",fact,"source","SELF_OBSERVATION","firstTurn",previous==null?snapshot.get("turn"):previous.get("firstTurn"),"lastTurn",lastSeen(snapshot),"count",previous==null?1:((Number)previous.get("count")).longValue()+1));if(table.size()>32)table.remove(table.keySet().iterator().next());}
    }
    /** Deterministic subject/text retrieval; no embedding service or invented facts. */
    public List<Map<String,Object>> recall(Role role,String query,int limit){if(limit<0||limit>16)throw new IllegalArgumentException("INVALID_LIMIT");String q=query.toLowerCase(Locale.ROOT);List<Map<String,Object>> all=new ArrayList<>(archive.getOrDefault(role,new LinkedHashMap<>()).values());all.addAll(history.getOrDefault(role,Collections.emptyList()));Collections.reverse(all);List<Map<String,Object>> out=new ArrayList<>();for(Map<String,Object> row:all)if(Json.write(row).toLowerCase(Locale.ROOT).contains(q)&&out.size()<limit)out.add(copy(row));return out;}
    public boolean ownsEvidence(Role role,String id){for(Map<String,Object> row:history.getOrDefault(role,Collections.emptyList()))if(id.equals(row.get("id")))return true;for(Map<String,Object> row:archive.getOrDefault(role,new LinkedHashMap<>()).values())if(id.equals(row.get("id")))return true;return false;}
    private static long lastSeen(Map<String,Object> row){return new java.math.BigDecimal(String.valueOf(row.containsKey("lastSeenTurn")?row.get("lastSeenTurn"):row.get("turn"))).longValueExact();}
    private static Map<String,Object> copy(Map<String,Object> v){return Json.asObject(Json.read(Json.write(v)));}
}
