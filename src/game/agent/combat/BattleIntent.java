package game.agent.combat;
import game.agent.llm.Json;
import java.util.*;
/** A confirmed goal is data; only engine state proves its completion. */
public final class BattleIntent {
    public final String goal,targetId;
    public final boolean noBattle;
    public BattleIntent(String goal,String targetId,boolean noBattle){
        if(!Arrays.asList("CAPTURE","DEFEAT").contains(goal)||!Arrays.asList("wild-treecko","wild-torchic").contains(targetId))throw new IllegalArgumentException("UNSUPPORTED_GOAL");
        if("CAPTURE".equals(goal)&&!"wild-treecko".equals(targetId))throw new IllegalArgumentException("NOT_CAPTURABLE");
        if("DEFEAT".equals(goal)&&noBattle)throw new IllegalArgumentException("CONFLICTING_CONSTRAINTS");
        this.goal=goal;this.targetId=targetId;this.noBattle=noBattle;
    }
    public Map<String,Object> encode(){return Json.object("goal",goal,"targetId",targetId,"noBattle",noBattle);}
    public static BattleIntent decode(Map<String,Object> value){
        if(!value.keySet().equals(new HashSet<>(Arrays.asList("goal","targetId","noBattle")))||!(value.get("goal") instanceof String)||!(value.get("targetId") instanceof String)||!(value.get("noBattle") instanceof Boolean))throw new IllegalArgumentException("INVALID_INTENT");
        return new BattleIntent((String)value.get("goal"),(String)value.get("targetId"),(Boolean)value.get("noBattle"));
    }
    public String instruction(){return goal+" "+targetId+". "+(noBattle?"NO_ACTIVE_BATTLE. ":"")+"Use actual observed rules. Approach using "+("wild-treecko".equals(targetId)?"treecko-approach":"torchic-approach")+", never the occupied target tile. Capturing is not defeating.";}
}
