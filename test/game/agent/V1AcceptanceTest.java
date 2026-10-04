package game.agent;
import game.agent.demo.V1AcceptanceDemo;
import game.agent.llm.Json;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class V1AcceptanceTest {
    @Test void explicitOfflineDemoCanRepeatTheWholeFailureDenialReplanAndCompletionFlow(){
        for(int i=0;i<2;i++){Map<String,Object> result=V1AcceptanceDemo.run();assertEquals("COMPLETED",result.get("status"));assertEquals(3,result.get("delivered"));assertEquals(5,result.get("coins"));
            Map<String,Object> metrics=Json.asObject(result.get("metrics"));assertEquals(1L,metrics.get("approvalCount"));assertTrue(((Number)metrics.get("replanCount")).longValue()>=2);assertFalse(Json.write(result).contains("approvalToken"));}
    }
}
