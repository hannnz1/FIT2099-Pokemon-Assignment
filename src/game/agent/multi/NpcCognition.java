package game.agent.multi;
import game.agent.llm.Json;
import game.agent.multi.MultiAgentFoundation.Role;
import java.util.*;

/** Model-authored interpretations are claims, never an authoritative engine observation. */
public final class NpcCognition {
 private final EnumMap<Role,Map<String,Object>> states=new EnumMap<>(Role.class);
 public static String period(long turn){if(turn<0)throw new IllegalArgumentException("INVALID_TURN");return turn%60<20?"MORNING":turn%60<40?"AFTERNOON":"EVENING";}
 public boolean needsPlan(Role role,String signature,long turn){Map<String,Object> s=states.get(role);return s==null||!signature.equals(s.get("signature"))||!period(turn).equals(s.get("period"))||turn/60!=((Number)s.get("day")).longValue();}
 public void accept(Role role,Map<String,Object> args,String signature,long turn,NpcKnowledge memory){
  String evidence=text(args.get("evidenceId"),128);if(!memory.ownsEvidence(role,evidence))throw new IllegalArgumentException("UNOWNED_EVIDENCE");
  String next=text(args.get("nextAction"),32);if(!Arrays.asList("SEND_FACT","RELAY_MESSAGE","OBSERVE","MOVE").contains(next)||("MOVE".equals(next)&&role!=Role.TORCHIC))throw new IllegalArgumentException("INVALID_PLAN_ACTION");
  Map<String,Object> reflection=Json.object("kind","MODEL_GENERATED","liveFact",false,"evidenceIds",Arrays.asList(evidence),"summary",text(args.get("reflection"),600));
  Map<String,Object> plan=Json.object("kind","MODEL_GENERATED","liveFact",false,"morning",text(args.get("morning"),300),"afternoon",text(args.get("afternoon"),300),"evening",text(args.get("evening"),300),"next",next,"approvalRequiredForPurchase",true);
  states.put(role,Json.object("signature",signature,"day",turn/60,"period",period(turn),"createdTurn",turn,"reflection",reflection,"plan",plan));
 }
 public Map<String,Object> view(Role role,long turn){Map<String,Object> s=states.get(role);if(s==null)return Json.object("needsPlanning",true);Map<String,Object> out=copy(s);out.put("phase",period(turn));out.put("phaseExpired",!period(turn).equals(s.get("period"))||turn/60!=((Number)s.get("day")).longValue());return out;}
 public Map<String,Object> checkpoint(){Map<String,Object> out=new LinkedHashMap<>();for(Map.Entry<Role,Map<String,Object>> e:states.entrySet())out.put(MultiAgentFoundation.agentId(e.getKey()),e.getValue());return copy(out);}
 public static NpcCognition restore(Map<String,Object> saved,NpcKnowledge memory){NpcCognition c=new NpcCognition();for(Map.Entry<String,Object> e:saved.entrySet()){Role role=Role.valueOf(e.getKey().toUpperCase(Locale.ROOT));Map<String,Object> s=Json.asObject(e.getValue()),ref=Json.asObject(s.get("reflection")),plan=Json.asObject(s.get("plan"));long turn=new java.math.BigDecimal(String.valueOf(s.get("createdTurn"))).longValueExact();if(turn<0||!period(turn).equals(s.get("period"))||new java.math.BigDecimal(String.valueOf(s.get("day"))).longValueExact()!=turn/60||!"MODEL_GENERATED".equals(ref.get("kind"))||!"MODEL_GENERATED".equals(plan.get("kind"))||!Boolean.FALSE.equals(ref.get("liveFact"))||!Boolean.FALSE.equals(plan.get("liveFact")))throw new IllegalArgumentException("INVALID_COGNITION");List<Object> ids=Json.asArray(ref.get("evidenceIds"));if(ids.size()!=1)throw new IllegalArgumentException("INVALID_EVIDENCE");
   // Evidence may have been compacted/evicted after the plan was authored; preserve the historical claim.
   text(ids.get(0),128);text(s.get("signature"),100000);text(ref.get("summary"),600);for(String p:Arrays.asList("morning","afternoon","evening"))text(plan.get(p),300);String next=text(plan.get("next"),32);if(!Arrays.asList("SEND_FACT","RELAY_MESSAGE","OBSERVE","MOVE").contains(next)||("MOVE".equals(next)&&role!=Role.TORCHIC))throw new IllegalArgumentException("INVALID_PLAN_ACTION");c.states.put(role,copy(s));}return c;}
 private static String text(Object value,int max){if(!(value instanceof String)||((String)value).trim().isEmpty()||((String)value).length()>max)throw new IllegalArgumentException("INVALID_COGNITION_TEXT");return(String)value;}
 private static Map<String,Object> copy(Map<String,Object> value){return Json.asObject(Json.read(Json.write(value)));}
}
