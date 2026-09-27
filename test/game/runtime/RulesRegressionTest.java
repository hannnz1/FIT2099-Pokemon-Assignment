package game.runtime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import edu.monash.fit2099.engine.actions.*;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.positions.*;
import game.actors.pokemon.*;
import game.environments.*;
import game.environments.spawners.*;
import game.actions.*;
import game.items.*;
import java.util.concurrent.atomic.AtomicInteger;

class RulesRegressionTest {
    GameSession s(RandomSource random) {return GameFactory.fromRows(new String[]{"#######","#.....#","#.....#","#.....#","#.....#","#.....#","#######"},3,3,random);}
    @Test void replacedGroundCannotActAgain() {
        GameSession s=s(b->10);Lava lava=new Lava();Location l=s.map().at(1,1);l.setGround(lava);lava.tick(l);l.setGround(new Dirt());lava.dayEffect();assertTrue(s.map().at(2,1).getGround() instanceof Dirt);
    }
    @Test void newbornActsNextRoundButGetsDayEffectImmediately() {
        GameSession s=s(b->0);s.map().at(1,1).setGround(new Crater());s.waitTurn();Actor born=s.map().at(1,1).getActor();assertNotNull(born);assertEquals(100,born.getHitPoints());s.waitTurn();assertTrue(s.map().contains(born));assertNotEquals(s.map().at(1,1),s.map().locationOf(born));
    }
    @Test void activePokemonReceivesExactlyOneTimeEffect() {
        GameSession s=s(b->b-1);Treecko p=new Treecko();s.map().addActor(p,s.map().at(1,1));s.waitTurn();assertEquals(95,p.getHitPoints());s.snapshot();assertEquals(95,p.getHitPoints());
    }
    @Test void spawnThresholdAndRepeatedWaterTrialsStayCompatible() {
        AtomicInteger calls=new AtomicInteger();GameSession s=s(b->{calls.incrementAndGet();return 21;});Location l=s.map().at(2,2);l.setGround(new Waterfall());s.map().at(1,1).setGround(new Puddle());s.map().at(2,1).setGround(new Puddle());s.map().at(3,1).setGround(new Puddle());l.getGround().tick(l);assertEquals(2,calls.get());assertFalse(l.containsAnActor());
        GameSession hit=s(b->20);Location h=hit.map().at(2,2);h.setGround(new Waterfall());hit.map().at(1,1).setGround(new Puddle());hit.map().at(2,1).setGround(new Puddle());h.getGround().tick(h);assertTrue(h.getActor() instanceof Mudkip);
    }
    @Test void noWildTorchicCaptureAndInsufficientTradeStillTicks() {
        GameSession s=s(b->b-1);s.map().addActor(new Torchic(),s.map().at(2,3));assertFalse(ActionCatalog.entries(s).stream().anyMatch(e->e.dto.kind.equals("CAPTURE")));
        s.execute(new TradeAction(new game.actors.npc.Shopkeeper(),"East",TradeOffer.TORCHIC));assertEquals(1,s.turn());assertTrue(s.player().getInventory().isEmpty());
    }
    @Test void requiredContinuationOnlyAppearsOnce() {
        GameSession s=s(b->b-1);AtomicInteger count=new AtomicInteger();Action next=new Action(){public String execute(Actor a,GameMap m){count.incrementAndGet();return "next";}public String menuDescription(Actor a){return "next";}};
        Action first=new Action(){public String execute(Actor a,GameMap m){return "first";}public String menuDescription(Actor a){return "first";}public Action getNextAction(){return next;}};
        s.execute(first);assertEquals("CONTINUE",ActionCatalog.entries(s).get(0).dto.kind);assertThrows(IllegalArgumentException.class,s::waitTurn);s.execute(ActionCatalog.entries(s).get(0).action);assertEquals(1,count.get());s.waitTurn();assertEquals(1,count.get());
    }
}
