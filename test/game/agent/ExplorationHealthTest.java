package game.agent;
import game.agent.demo.*;import game.agent.llm.*;import game.agent.tools.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class ExplorationHealthTest{
 @Test void dayNightAdvanceWithoutFixedDamageOrHealing(){GeminiQuestScenario s=new GeminiQuestScenario("health",true);Map<String,Object>state=s.exportState();Json.asObject(state.get("actor")).put("hp",900);s=GeminiQuestScenario.restore("health",state);s.start(TaskIntent.of(s.getSession().getQuestId(),EnumSet.noneOf(TaskIntent.Constraint.class),null,null),c->new ToolRequest("obs","observe_quest",Collections.emptyMap()),Runnable::run);for(int i=0;i<65;i++)s.advanceTurn();assertEquals("NIGHT",s.getWorldPeriod());assertEquals(900,Json.asObject(s.exportState().get("actor")).get("hp"));assertEquals(1000,Json.asObject(s.exportState().get("treecko")).get("hp"));}
}
