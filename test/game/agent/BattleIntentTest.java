package game.agent;
import game.agent.combat.*;
import game.agent.llm.Json;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BattleIntentTest {
    @Test void observationAndCompletionNotificationsDoNotAdvanceWorld(){
        BattleTrainingScenario scene=new BattleTrainingScenario();for(String code:java.util.Arrays.asList("COMBAT_OBSERVED","GOAL_COMPLETED","OBSERVED","INVENTORY"))scene.advance(game.agent.action.ActionResult.success(code));assertEquals("0",scene.observation().get("turn"));
    }
    @Test void rejectsUncatchableConflictAndAdditionalFields(){
        assertThrows(IllegalArgumentException.class,()->BattleIntent.decode(Json.object("goal","CAPTURE","targetId","wild-torchic","noBattle",false)));
        assertThrows(IllegalArgumentException.class,()->BattleIntent.decode(Json.object("goal","DEFEAT","targetId","wild-treecko","noBattle",true)));
        assertThrows(IllegalArgumentException.class,()->BattleIntent.decode(Json.object("goal","CAPTURE","targetId","wild-treecko","noBattle",true,"success",true)));
    }
    @Test void capturedIsNotDefeatedAndGoalSurvivesRestore(){
        BattleTrainingScenario scene=new BattleTrainingScenario();scene.configureGoal(new BattleIntent("DEFEAT","wild-treecko",false));
        scene.manual("move","E");scene.manual("capture","wild-treecko");assertFalse(scene.isComplete());
        BattleTrainingScenario restored=BattleTrainingScenario.restore(scene.exportState());assertFalse(restored.isComplete());assertEquals("DEFEAT",restored.observation().get("goalType"));
    }
    @Test void actualDefeatCompletesOnlyRequestedTarget(){
        BattleTrainingScenario scene=new BattleTrainingScenario();scene.configureGoal(new BattleIntent("DEFEAT","wild-torchic",false));
        scene.manual("move","S");for(int i=0;i<5;i++)scene.manual("move","E");scene.manual("move","N");
        for(int i=0;i<100&&!scene.isComplete();i++)scene.manual("attack","wild-torchic");
        assertTrue(scene.isComplete());assertEquals(0,scene.getCapturedCount());assertTrue(BattleTrainingScenario.restore(scene.exportState()).isComplete());
    }
}
