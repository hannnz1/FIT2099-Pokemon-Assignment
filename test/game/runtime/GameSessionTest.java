package game.runtime;

import game.actors.pokemon.Treecko;
import game.actors.pokemon.Mudkip;
import game.items.balls.Pokeball;
import game.actions.CaptureAction;
import game.actions.DancingAction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameSessionTest {
    private GameSession empty() { return GameFactory.fromRows(new String[]{"#####", "#...#", "#...#", "#...#", "#####"}, 2, 2, bound -> bound - 1); }
    @Test void readsDoNotTickAndEveryStepAdvancesOnce() {
        GameSession s = empty();
        assertEquals(0, s.turn()); s.snapshot(); s.snapshot(); assertEquals(0, s.turn());
        for (int i=1;i<=11;i++) { s.waitTurn(); assertEquals(i,s.turn()); assertEquals(i<=5 || i==11 ? "DAY":"NIGHT",s.period()); }
    }
    @Test void capturedPokemonKeepsIdentityAndStopsTakingTimeDamage() {
        GameSession s=empty(); Treecko p=new Treecko(); s.map().addActor(p, s.map().at(1,2));
        String id=s.context().id(p); s.execute(new CaptureAction(p));
        Pokeball ball=(Pokeball)s.player().getInventory().get(0);
        assertSame(p,ball.getPokemon()); assertEquals(id,s.context().id(ball.getPokemon()));
        for(int i=0;i<12;i++) s.waitTurn(); assertEquals(100,p.getHitPoints());
    }
    @Test void deadPokemonDropsItemsAndDoesNotActOrHeal() {
        GameSession s=empty(); Mudkip p=new Mudkip(); p.hurt(999); s.map().addActor(p,s.map().at(1,1));
        p.addItemToInventory(new game.items.Candy()); s.waitTurn();
        assertFalse(s.map().contains(p)); assertEquals(0,p.getHitPoints()); assertEquals(1,s.map().at(1,1).getItems().size());
    }
    @Test void affectionAndClockAreIsolated() {
        GameSession a=empty(), b=empty(); Treecko p=new Treecko(); a.map().addActor(p,a.map().at(1,2));
        a.execute(new DancingAction(p)); assertEquals(10,a.context().affection.getAffectionPoint(p));
        assertFalse(b.context().affection.isRegistered(p)); assertEquals(0,b.turn());
    }
    @Test void hpClampsAtBothBounds() { Treecko p=new Treecko(); p.hurt(999); assertEquals(0,p.getHitPoints()); p.heal(999); assertEquals(100,p.getHitPoints()); }
}
