package game.agent;
import game.agent.trace.StepTrace;
import game.agent.runtime.AgentTask;
import game.agent.action.ActionResult;
import game.agent.tools.ToolRequest;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TraceApprovalTransitionTest {
    @Test void approvalRequestAndExternalDenialCountThenSuccessfulReplanIsRecorded(){
        StepTrace trace=new StepTrace();trace.observe(AgentTask.State.RUNNING);
        trace.record("task","op",new ToolRequest("request","request_player_approval",Collections.emptyMap()),"VALID",ActionResult.success("APPROVAL_REQUESTED"),AgentTask.State.RUNNING,AgentTask.State.WAITING_APPROVAL,1,Collections.emptyMap());
        assertEquals(1L,trace.metrics(AgentTask.State.WAITING_APPROVAL).get("approvalCount"));
        trace.observe(AgentTask.State.REPLANNING);assertEquals(1L,trace.metrics(AgentTask.State.REPLANNING).get("replanCount"));
        trace.record("task","op",new ToolRequest("move","move_to",Collections.emptyMap()),"VALID",ActionResult.success("ARRIVED"),AgentTask.State.REPLANNING,AgentTask.State.RUNNING,1,Collections.emptyMap());
        assertEquals(1L,trace.metrics(AgentTask.State.RUNNING).get("replanSuccessCount"));
    }
}
