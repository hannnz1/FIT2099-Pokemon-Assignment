package game.agent;
import game.agent.quest.*;
import game.agent.runtime.AgentTask;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class V1DeadlineTest {
    @Test void configuredAbsoluteDeadlineRejectsLateDeliveryWithoutChangingInventory() throws Exception {
        BerryQuestTest.Fixture f=new BerryQuestTest.Fixture(); AgentTask task=new AgentTask("deadline");
        BerryQuestSession session=new BerryQuestSession("owner",task,f.actor,f.map,f.professor,f.merchant,new BerryQuestSession.Rules(3,30,10,1000),5,4,1,true,f.clock::get);
        session.configureDeadline(2L); task.start(); f.carried(3); f.nearProfessor();
        assertTrue(session.advanceTurn()); assertTrue(session.advanceTurn());
        assertEquals("QUEST_EXPIRED",session.deliver(session.getQuestId(),"professor").getCode()); assertEquals(3,f.actor.getInventory().size()); assertTrue(f.professor.getInventory().isEmpty()); assertEquals("2",session.observation().get("deadlineTurn"));
    }
    @Test void deadlineSetupCannotExtendNightOrChangeRunningTask() throws Exception {
        BerryQuestTest.Fixture f=new BerryQuestTest.Fixture(); AgentTask task=new AgentTask("setup");
        BerryQuestSession session=new BerryQuestSession("owner",task,f.actor,f.map,f.professor,f.merchant,new BerryQuestSession.Rules(3,30,10,1000),5,4,1,true,f.clock::get);
        assertThrows(IllegalArgumentException.class,()->session.configureDeadline(31L)); assertThrows(IllegalArgumentException.class,()->session.configureDeadline(0L)); session.configureDeadline(3L); task.start(); assertThrows(IllegalStateException.class,()->session.configureDeadline(2L));
    }
    @Test void manualOwnershipWrapperDeniesAiAndAllowsPausedAuthoritativePickup() throws Exception {
        BerryQuestTest.Fixture f=new BerryQuestTest.Fixture(); f.ground(1);
        java.util.function.Supplier<game.agent.action.ActionResult> pickup=()->f.session.pickup("BERRY",1);
        assertEquals("AI_CONTROLS_COMPANION",f.session.manualAction(pickup).getCode());
        f.task.pause(); assertEquals("PICKED_UP",f.session.manualAction(pickup).getCode());
        assertEquals("TASK_INACTIVE",f.session.pickup("BERRY",1).getCode()); assertEquals(1,f.actor.getInventory().size());
    }
    @Test void expiredQuestBlocksEvenManualWaitCallback() {
        BerryQuestTest.Fixture f=new BerryQuestTest.Fixture(10,1); f.session.advanceTurn();
        java.util.concurrent.atomic.AtomicBoolean ran=new java.util.concurrent.atomic.AtomicBoolean();
        assertEquals("QUEST_EXPIRED",f.session.manualAction(()-> { ran.set(true); return game.agent.action.ActionResult.rejected("WAIT"); }).getCode());
        assertFalse(ran.get());
    }
}
