package game.agent.growth;
import game.agent.demo.ExpansionBalanceDemo;import game.agent.llm.Json;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class GrowthBalanceTest {
 @Test void representativeNativeMatchupsTerminateAndEachNewBaseCanWin(){Map<String,Object> report=ExpansionBalanceDemo.run();assertEquals(1152,report.get("cases"));assertEquals(0,report.get("unresolved"));for(Object row:Json.asArray(report.get("summary"))){Map<String,Object> s=Json.asObject(row);assertTrue(((Number)s.get("wins")).intValue()>0,(String)s.get("species"));assertTrue(((Number)s.get("losses")).intValue()>0,(String)s.get("species"));}}
 @Test void sameSeedNumericBattleSamplesRepeat(){assertEquals(Json.write(ExpansionBalanceDemo.run()),Json.write(ExpansionBalanceDemo.run()));}
}
