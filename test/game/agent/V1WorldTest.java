package game.agent;

import game.agent.demo.GeminiQuestScenario;
import game.agent.action.ActionResult;
import game.agent.llm.Json;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class V1WorldTest {
    @Test void originalWorldUsesOriginalTerrainAndManualRulesWithoutProvider() {
        GeminiQuestScenario scene=new GeminiQuestScenario("manual-player",true);
        assertEquals(59,scene.getMapDescription().get("width"));
        assertEquals("DAY",scene.getWorldPeriod());
        assertEquals("RESOURCE_NOT_AVAILABLE",scene.manual("pickup",Json.object("quantity",1)).getCode());
        ActionResult result=scene.manual("move",Json.object("direction","E"));
        assertEquals(ActionResult.Status.SUCCESS,result.getStatus());
        scene.advanceTurn();assertEquals(1,scene.getSession().getTurn());
        assertEquals(1,scene.getWorldTicks());
    }
    @Test void manualCannotControlRunningTaskAndPauseAllowsTakeover() {
        GeminiQuestScenario scene=new GeminiQuestScenario("manual-player");
        game.agent.llm.TaskIntent intent=new game.agent.llm.OpenAiGateway(new game.agent.llm.OpenAiConfig("test-key","gpt-6-luna",1000),
            (m,k,b,t)->new game.agent.llm.JsonTransport.Response(200,AgentRoomTest.parsed(b))).parse("完成任务",scene.getSession().getQuestId());
        game.agent.runtime.AgentLoop loop=scene.start(intent,c->new game.agent.tools.ToolRequest("choice","wait",Collections.emptyMap()),Runnable::run);
        assertEquals("AI_CONTROLS_COMPANION",scene.manual("move",Json.object("direction","E")).getCode());
        loop.pause();assertEquals(ActionResult.Status.SUCCESS,scene.manual("move",Json.object("direction","E")).getStatus());
        loop.cancel();assertEquals(ActionResult.Status.SUCCESS,scene.manual("wait",Collections.emptyMap()).getStatus());
    }
    @Test void actualActionsReachNightAndExpiredQuestCannotBeDelivered(){
        GeminiQuestScenario scene=new GeminiQuestScenario("manual-player",true);
        for(int i=0;i<60;i++){assertEquals(ActionResult.Status.SUCCESS,scene.manual("wait",Collections.emptyMap()).getStatus());scene.advanceTurn();}
        assertEquals("NIGHT",scene.getWorldPeriod());assertEquals(60,scene.getWorldTicks());
        assertEquals("QUEST_EXPIRED",scene.manual("wait",Collections.emptyMap()).getCode());
        assertEquals(game.agent.quest.BerryQuestSession.QuestStatus.EXPIRED,scene.getSession().getQuestStatus());
    }
    @Test void constructingAndRestoringV1TerrainDoesNotRegisterLegacyClockObservers(){
        List<game.time.TimePerception> before=game.time.TimePerceptionManager.getInstance().getTimePerceptionList();
        GeminiQuestScenario scene=new GeminiQuestScenario("owner",true);
        assertEquals(before,game.time.TimePerceptionManager.getInstance().getTimePerceptionList());
        GeminiQuestScenario.restore("owner",scene.exportState());
        assertEquals(before,game.time.TimePerceptionManager.getInstance().getTimePerceptionList());
    }
    @Test void questNpcWandersOnlyInItsOutdoorAreaAndCannotOccupyTheOnlyLaboratoryEntrance(){
        edu.monash.fit2099.engine.positions.GameMap map=new edu.monash.fit2099.engine.positions.GameMap(new edu.monash.fit2099.engine.positions.FancyGroundFactory(new game.environments.Dirt()),Arrays.asList("...","..."));
        new edu.monash.fit2099.engine.positions.World(new edu.monash.fit2099.engine.displays.Display()).addGameMap(map);
        game.actors.pokemon.Treecko npc=new game.actors.pokemon.Treecko();map.addActor(npc,map.at(1,1));
        game.behaviours.WanderBehaviour wander=new game.behaviours.WanderBehaviour(p->p.y()==1);
        for(int i=0;i<100;i++){edu.monash.fit2099.engine.actions.Action action=wander.getAction(npc,map);if(action!=null)action.execute(npc,map);assertEquals(1,map.locationOf(npc).y());}
        npc.unregisterInstance();
    }
}
