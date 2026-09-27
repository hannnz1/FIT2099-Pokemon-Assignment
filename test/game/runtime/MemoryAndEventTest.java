package game.runtime;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
import game.actors.pokemon.Treecko;import game.actions.*;import game.actors.npc.*;import game.items.TradeOffer;
class MemoryAndEventTest {
    GameSession game(){return GameFactory.fromRows(new String[]{"#####","#...#","#...#","#...#","#####"},2,2,b->b-1);}
    @Test void removedActorsDoNotStayInSessionIdentityRegistry(){GameSession s=game();for(int i=0;i<100;i++){Treecko p=new Treecko();s.map().addActor(p,s.map().at(1,1));p.hurt(100);s.waitTurn();}assertEquals(1,s.context().retainedCount());}
    @Test void dialogueAndTradeHaveStructuredEvents(){GameSession s=game();assertTrue(s.execute(new TalkAction(new ProffesorOak(),"N")).stream().anyMatch(e->e.kind.equals("DIALOGUE")));assertTrue(s.execute(new TradeAction(new Shopkeeper(),"N",TradeOffer.GREAT_BALL)).stream().anyMatch(e->e.kind.equals("TRADE")));}
    @Test void eachRetirementHasOneRemoveEvent(){GameSession s=game();Treecko p=new Treecko();s.map().addActor(p,s.map().at(1,1));p.hurt(99);String id=s.context().id(p);assertEquals(1,s.execute(new edu.monash.fit2099.engine.actions.DoNothingAction()).stream().filter(e->e.kind.equals("REMOVE")&&id.equals(e.actorId)).count());}
}
