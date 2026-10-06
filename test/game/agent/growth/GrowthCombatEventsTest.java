package game.agent.growth;
import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class GrowthCombatEventsTest {
 GrowthPokemon mon(String species){return new GrowthPokemon(UUID.randomUUID().toString(),species,10);}
 @Test void missAndResidualAreExplicitAndDoNotChangePpOrRandomSequence(){
  GrowthPokemon a=mon("TREECKO"),b=mon("MUDKIP");a.accuracyStage=-6;GrowthCombat c=new GrowthCombat(()->99);int hp=b.getHitPoints(),pp=a.pp.get("POUND");
  c.chosenMove(a,b,"POUND");assertEquals("MISSED",c.lastOutcome());assertEquals(hp,b.getHitPoints());assertEquals(pp-1,a.pp.get("POUND"));
  assertEquals(a.captureId,c.events().get(0).get("actorId"));assertEquals(b.captureId,c.events().get(0).get("targetId"));
  a.burned=true;c.residual(a,b);assertEquals("RESIDUAL",c.events().get(1).get("type"));assertEquals("BURN",c.events().get(1).get("status"));
 }
 @Test void statusPreventsMoveWithoutSpendingPp(){GrowthPokemon a=mon("TREECKO"),b=mon("MUDKIP");a.status="SLEEP";a.sleepTurns=2;int pp=a.pp.get("POUND");GrowthCombat c=new GrowthCombat(()->0);c.chosenMove(a,b,"POUND");assertEquals("STATUS_PREVENTED",c.lastOutcome());assertEquals(pp,a.pp.get("POUND"));assertEquals("STATUS_PREVENTED",c.events().get(0).get("outcome"));}
}
