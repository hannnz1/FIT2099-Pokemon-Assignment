package game.agent;
import game.agent.quest.BerryCoordinationAdvice;import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class BerryCoordinationAdviceTest {
 Map<String,String> state(int carried,int remaining,boolean near){Map<String,String> s=new LinkedHashMap<>();s.put("carriedBerry",""+carried);s.put("remainingBerry",""+remaining);s.put("requiredBerry","3");s.put("visibleBerry","0");s.put("nearProfessor",""+near);s.put("x","31");s.put("y","10");return s;}
 @Test void sharedRemainderOverridesOriginalQuantityAndHistoricalClaim(){Map<String,String> s=state(2,2,false);s.put("npcAgentMessages","historical berry quantity 2");BerryCoordinationAdvice.enrich(s,31,10);assertEquals("move_to",s.get("suggestedAction"));assertEquals("laboratory",s.get("suggestedLocation"));assertEquals("2",s.get("remainingBerry"));}
 @Test void adjacentPartialDeliveryIsRecommended(){Map<String,String> s=state(1,3,true);BerryCoordinationAdvice.enrich(s,31,10);assertEquals("deliver",s.get("suggestedAction"));assertFalse(s.containsKey("suggestedLocation"));}
 @Test void emptyCurrentSiteRecommendsAnotherSearchWithoutInventingStock(){Map<String,String> s=state(1,3,false);BerryCoordinationAdvice.enrich(s,31,10);assertEquals("alternative",s.get("suggestedLocation"));assertFalse(s.containsKey("alternativeStock"));}
}
