package game.runtime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import game.actors.pokemon.Treecko;
import game.behaviours.WanderBehaviour;
import edu.monash.fit2099.engine.actions.Action;

class CardinalMovementTest {
    @Test void playerReceivesExactlyFourMovementDirections() {
        GameSession s=GameFactory.fromRows(new String[]{"#####","#...#","#...#","#...#","#####"},2,2,b->0);
        assertEquals(4,ActionCatalog.entries(s).stream().filter(e->e.dto.kind.equals("MOVE")).count());
        assertFalse(ActionCatalog.entries(s).stream().anyMatch(e->e.dto.direction!=null&&e.dto.direction.contains("-")));
    }
    @Test void wildPokemonAlsoMoveOnlyOneCardinalCell() {
        for(int choice=0;choice<8;choice++){
            final int roll=choice;
            GameSession s=GameFactory.fromRows(new String[]{"#######","#.....#","#.....#","#.....#","#.....#","#.....#","#######"},5,5,b->roll%b);
            Treecko p=new Treecko();s.map().addActor(p,s.map().at(3,3));Action a=new WanderBehaviour().getAction(p,s.map());a.execute(p,s.map());
            assertEquals(1,Math.abs(s.map().locationOf(p).x()-3)+Math.abs(s.map().locationOf(p).y()-3));
        }
    }
    @Test void candyCannotSpawnInARoomOnlyReachableDiagonally() {
        GameSession s=GameFactory.fromRows(new String[]{"#####","#.#.#","##..#","#####"},1,1,b->0);
        s.waitTurn();assertTrue(s.snapshot().groundItems.isEmpty());
    }
}
