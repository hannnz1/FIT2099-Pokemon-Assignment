package game.agent;
import game.agent.combat.*;
import game.agent.tools.*;
import game.agent.runtime.*;
import game.agent.llm.Json;
import game.agent.action.ActionResult;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatTrainingTest {
    private ToolRequest call(String name,String target){return new ToolRequest(UUID.randomUUID().toString(),name,Json.object("targetId",target));}
    @Test void captureMovesActualPokemonIntoTrainerBallAndCompletesGoal(){
        BattleTrainingScenario scene=new BattleTrainingScenario();
        assertEquals("OUT_OF_REACH",scene.manual("capture","wild-treecko").getCode());
        assertEquals("ARRIVED",scene.manual("move","E").getCode());
        assertEquals("CAPTURED",scene.manual("capture","wild-treecko").getCode());
        assertTrue(scene.isComplete());assertEquals(1,scene.getCapturedCount());
        assertEquals("TARGET_UNAVAILABLE",scene.manual("capture","wild-treecko").getCode());
    }
    @Test void noBattleConstraintPreventsDamageButAllowsPeacefulCapture(){
        BattleTrainingScenario scene=new BattleTrainingScenario();scene.manual("move","E");
        scene.start(c->call("attack","wild-treecko"),Runnable::run,true);
        int before=Integer.parseInt(scene.observation().get("actorHp"));
        assertEquals("NO_ACTIVE_BATTLE",scene.execute(call("attack","wild-treecko")).getCode());
        assertEquals(before,Integer.parseInt(scene.observation().get("actorHp")));
        assertEquals("CAPTURED",scene.execute(call("capture","wild-treecko")).getCode());
        assertEquals("100",Json.asObject(Json.asArray(Json.read(scene.observation().get("targets"))).get(0)).get("hp").toString());
    }
    @Test void attackUsesActualHealthAndRetaliationAndIsIdempotent(){
        edu.monash.fit2099.engine.positions.GameMap map=new edu.monash.fit2099.engine.positions.GameMap(new edu.monash.fit2099.engine.positions.FancyGroundFactory(new game.environments.Dirt()),Arrays.asList("..."));
        new edu.monash.fit2099.engine.positions.World(new edu.monash.fit2099.engine.displays.Display()).addGameMap(map);
        ReliablePokemon actor=new ReliablePokemon(70),target=new ReliablePokemon(10);map.addActor(actor,map.at(0,0));map.addActor(target,map.at(1,0));
        CombatSession combat=new CombatSession(actor,new game.actors.Player("trainer",'@',100),map,Collections.singletonMap("wild",target),()->true,()->false,p->true,()->{});
        GameToolRegistry tools=new GameToolRegistry(r->null);CombatTools.register(tools,combat,Collections::emptyMap);
        ToolRequest request=call("attack","wild");ActionResult result=tools.execute(request);
        assertEquals("ATTACKED",result.getCode());assertEquals(30,target.getHitPoints());assertEquals(90,actor.getHitPoints());
        assertEquals("70",result.getData().get("damage"));assertEquals("10",result.getData().get("retaliationDamage"));
        assertSame(result,tools.execute(request));assertEquals(30,target.getHitPoints());assertEquals(90,actor.getHitPoints());
        assertEquals("TARGET_DEFEATED",tools.execute(call("attack","wild")).getCode());assertFalse(map.contains(target));
        assertEquals("TARGET_UNAVAILABLE",tools.execute(call("capture","wild")).getCode());
    }
    @Test void areaPolicyChecksBothActorsBeforeDamage(){
        edu.monash.fit2099.engine.positions.GameMap map=new edu.monash.fit2099.engine.positions.GameMap(new edu.monash.fit2099.engine.positions.FancyGroundFactory(new game.environments.Dirt()),Arrays.asList("..."));
        new edu.monash.fit2099.engine.positions.World(new edu.monash.fit2099.engine.displays.Display()).addGameMap(map);
        ReliablePokemon actor=new ReliablePokemon(70),target=new ReliablePokemon(10);map.addActor(actor,map.at(0,0));map.addActor(target,map.at(1,0));
        CombatSession combat=new CombatSession(actor,new game.actors.Player("trainer",'@',100),map,Collections.singletonMap("wild",target),()->true,()->false,p->p.x()==0,()->{});
        assertEquals("AREA_RESTRICTED",combat.attack("wild").getCode());assertEquals("AREA_RESTRICTED",combat.capture("wild").getCode());assertEquals(100,target.getHitPoints());assertEquals(100,actor.getHitPoints());
    }
    private static class ReliablePokemon extends game.actors.pokemon.Pokemon {
        private final int damage;
        ReliablePokemon(int damage){super("test pokemon",'p',100);this.damage=damage;}
        public void toggleWeapon(boolean equip){}
        public edu.monash.fit2099.engine.weapons.Weapon getWeapon(){return new edu.monash.fit2099.engine.weapons.Weapon(){public int damage(){return damage;}public int chanceToHit(){return 100;}public String verb(){return "hits";}};}
        public edu.monash.fit2099.engine.actions.Action playTurn(edu.monash.fit2099.engine.actions.ActionList a,edu.monash.fit2099.engine.actions.Action previous,edu.monash.fit2099.engine.positions.GameMap m,edu.monash.fit2099.engine.displays.Display d){return new edu.monash.fit2099.engine.actions.DoNothingAction();}
        public edu.monash.fit2099.engine.actions.ActionList allowableActions(edu.monash.fit2099.engine.actors.Actor other,String direction,edu.monash.fit2099.engine.positions.GameMap m){return new edu.monash.fit2099.engine.actions.ActionList(new game.actions.AttackAction(this,direction));}
    }
    @Test void invalidTargetRangeAndUncatchableSpeciesDoNotMutateWorld(){
        BattleTrainingScenario scene=new BattleTrainingScenario();
        assertEquals("UNKNOWN_TARGET",scene.manual("attack","professor").getCode());
        assertEquals("OUT_OF_REACH",scene.manual("attack","wild-treecko").getCode());
        scene.manual("move","S");for(int i=0;i<5;i++)scene.manual("move","E");scene.manual("move","N");
        assertEquals("NOT_CAPTURABLE",scene.manual("capture","wild-torchic").getCode());assertEquals(0,scene.getCapturedCount());
    }
    @Test void pausedControlRejectsDirectToolsButAllowsManualAndRestoreKeepsOwnedBall(){
        BattleTrainingScenario scene=new BattleTrainingScenario();scene.manual("move","E");
        AgentLoop loop=scene.start(c->call("capture","wild-treecko"),Runnable::run,false);
        assertEquals("AI_CONTROLS_COMPANION",scene.manual("capture","wild-treecko").getCode());loop.pause();
        assertEquals("TASK_INACTIVE",scene.execute(call("capture","wild-treecko")).getCode());
        assertEquals("CAPTURED",scene.manual("capture","wild-treecko").getCode());
        BattleTrainingScenario restored=BattleTrainingScenario.restore(Json.asObject(Json.read(Json.write(scene.exportState()))));
        assertTrue(restored.isComplete());assertEquals(1,restored.getCapturedCount());assertNotEquals(scene.getTaskId(),restored.getTaskId());
    }
    @Test void restoresUnfinishedTaskPausedAndRejectsInconsistentCheckpoint(){
        BattleTrainingScenario scene=new BattleTrainingScenario();scene.manual("move","E");scene.start(c->call("capture","wild-treecko"),Runnable::run,false);
        BattleTrainingScenario restored=BattleTrainingScenario.restore(scene.exportState());assertEquals("PAUSED",restored.observation().get("taskState"));
        assertEquals("TASK_INACTIVE",restored.execute(call("attack","wild-treecko")).getCode());
        Map<String,Object> bad=Json.asObject(Json.read(Json.write(scene.exportState())));bad.put("captured",Arrays.asList("wild-treecko"));
        assertThrows(IllegalArgumentException.class,()->BattleTrainingScenario.restore(bad));
    }
    @Test void fixedOfflineProviderRunsThroughRealAgentLoopAndTrace(){
        BattleTrainingScenario scene=new BattleTrainingScenario();
        Queue<ToolRequest> choices=new ArrayDeque<>(Arrays.asList(new ToolRequest("move","move_to",Json.object("locationId","treecko-approach")),call("capture","wild-treecko")));
        AgentLoop loop=scene.start(c->choices.remove(),Runnable::run,true);
        for(int i=0;i<20&&loop.getState()!=AgentTask.State.COMPLETED;i++)loop.tick(i);
        assertEquals(AgentTask.State.COMPLETED,loop.getState());assertTrue(scene.isComplete());assertEquals(1,scene.getCapturedCount());assertFalse(loop.getStepTrace().isEmpty());
    }
    @Test void capturedTargetTileCanBecomeCompanionTileAndRestore(){
        BattleTrainingScenario scene=new BattleTrainingScenario();scene.manual("move","E");scene.manual("capture","wild-treecko");scene.manual("move","E");
        BattleTrainingScenario restored=BattleTrainingScenario.restore(scene.exportState());
        assertEquals("2",restored.observation().get("x"));assertEquals(1,restored.getCapturedCount());
    }
    @Test void overkillDamageExportsRestorableDefeatedHealth() throws Exception {
        BattleTrainingScenario scene=new BattleTrainingScenario();
        java.lang.reflect.Field targetField=BattleTrainingScenario.class.getDeclaredField("targets");targetField.setAccessible(true);
        java.util.Map<?,?> targets=(java.util.Map<?,?>)targetField.get(scene);
        edu.monash.fit2099.engine.actors.Actor target=(edu.monash.fit2099.engine.actors.Actor)targets.get("wild-treecko");target.hurt(125);
        java.lang.reflect.Field mapField=BattleTrainingScenario.class.getDeclaredField("map");mapField.setAccessible(true);
        ((edu.monash.fit2099.engine.positions.GameMap)mapField.get(scene)).removeActor(target);
        BattleTrainingScenario restored=BattleTrainingScenario.restore(scene.exportState());
        assertEquals("0",Json.asObject(Json.asArray(Json.read(restored.observation().get("targets"))).get(0)).get("hp").toString());
        assertFalse(restored.isComplete());
    }
    @Test void enablingNoBattleWhenResumingRecoveredTaskCannotBeIgnored(){
        BattleTrainingScenario scene=new BattleTrainingScenario();scene.manual("move","E");scene.start(c->call("capture","wild-treecko"),Runnable::run,false);
        BattleTrainingScenario restored=BattleTrainingScenario.restore(scene.exportState());restored.start(c->call("attack","wild-treecko"),Runnable::run,true);
        assertEquals("NO_ACTIVE_BATTLE",restored.execute(call("attack","wild-treecko")).getCode());assertEquals("true",restored.observation().get("noActiveBattle"));
    }
    @Test void cancelledEpisodeRestoresPausedAndCanStartNewExplicitAttempt(){
        BattleTrainingScenario scene=new BattleTrainingScenario();AgentLoop loop=scene.start(c->call("capture","wild-treecko"),Runnable::run,true);loop.cancel();
        BattleTrainingScenario restored=BattleTrainingScenario.restore(scene.exportState());assertEquals("PAUSED",restored.observation().get("taskState"));
        restored.start(c->call("capture","wild-treecko"),Runnable::run,false);assertEquals("true",restored.observation().get("noActiveBattle"));
        assertEquals("RUNNING",restored.observation().get("taskState"));
    }
}
