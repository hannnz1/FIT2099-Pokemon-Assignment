package game.agent;

import game.agent.llm.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NaturalTaskParserTest {
    static GeminiTransport.Response taskResponse(String quest,String... constraints) {
        String intent=Json.write(Json.object("status","SUPPORTED","goal","COMPLETE_QUEST","questId",quest,
            "constraints",Arrays.asList(constraints),"unsupportedClauses",Collections.emptyList()));
        return new GeminiTransport.Response(200,GeminiGatewayTest.response(Json.write(Json.object("text",intent))));
    }
    @Test void naturalInputMapsOnlyToTrustedQuestAndKnownConstraints() {
        NaturalTaskParser parser=new NaturalTaskParser(GeminiGatewayTest.gateway((m,k,body,t)->{
            assertTrue(body.contains("responseJsonSchema")); assertTrue(body.contains("不要花金币"));
            return taskResponse("quest-1","NO_SPENDING","NO_ACTIVE_BATTLE");
        }));
        TaskIntent intent=parser.parse("帮我完成树果任务，不要花金币，也不要主动战斗","quest-1");
        assertEquals("quest-1",intent.getQuestId()); assertTrue(intent.has(TaskIntent.Constraint.NO_SPENDING));
        assertTrue(intent.has(TaskIntent.Constraint.NO_ACTIVE_BATTLE));
        assertThrows(UnsupportedOperationException.class,()->intent.getConstraints().clear());
    }
    @Test void unknownConstraintsAndOtherQuestAreRejected() {
        for(GeminiTransport.Response response:Arrays.asList(taskResponse("other","NO_SPENDING"),taskResponse("quest-1","ADMIN"),
            taskResponse("quest-1","AREA_RESTRICTED"),taskResponse("quest-1","NO_SPENDING","NO_SPENDING"))) {
            assertThrows(ProviderException.class,()->new NaturalTaskParser(GeminiGatewayTest.gateway((m,k,b,t)->response)).parse("任务","quest-1"));
        }
    }
    @Test void unsupportedClausesCannotSilentlyStartTasks() {
        String text=Json.write(Json.object("status","UNSUPPORTED","goal","COMPLETE_QUEST","questId","quest-1",
            "constraints",Collections.emptyList(),"unsupportedClauses",Collections.singletonList("只在某区域行动")));
        NaturalTaskParser parser=new NaturalTaskParser(GeminiGatewayTest.gateway((m,k,b,t)->new GeminiTransport.Response(200,
            GeminiGatewayTest.response(Json.write(Json.object("text",text))))));
        assertEquals(ProviderException.Code.UNSUPPORTED_TASK,assertThrows(ProviderException.class,()->parser.parse("只在区域行动","quest-1")).getCode());
        assertThrows(IllegalArgumentException.class,()->parser.parse(" ","quest-1"));
    }
    @Test void extraFieldsAndUnstructuredCompletionAreRejected() {
        NaturalTaskParser parser=new NaturalTaskParser(GeminiGatewayTest.gateway((m,k,b,t)->new GeminiTransport.Response(200,
            GeminiGatewayTest.response("{\"text\":\"Done, I have completed the quest\"}"))));
        assertEquals(ProviderException.Code.INVALID_RESPONSE,assertThrows(ProviderException.class,()->parser.parse("任务","quest-1")).getCode());
    }
}
