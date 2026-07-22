package game.conditions;

import edu.monash.fit2099.engine.actions.Action;
import edu.monash.fit2099.engine.actions.ActionList;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.displays.Display;
import edu.monash.fit2099.engine.positions.GameMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AffectionManagerTest {

    private final AffectionManager manager = AffectionManager.getInstance();
    private Actor pokemon;

    @BeforeEach
    void setUp() {
        manager.reset();
        pokemon = new TestActor("Pokemon");
        manager.registerPokemon(pokemon);
    }

    @Test
    void startsAtNeutralAffection() {
        assertEquals(0, manager.getAffectionPoint(pokemon));
    }

    @Test
    void persistsIncreasesAndDecreases() {
        manager.increaseAffection(pokemon, 30);
        manager.decreaseAffection(pokemon, 10);
        assertEquals(20, manager.getAffectionPoint(pokemon));
    }

    @Test
    void clampsAffectionToDomainLimits() {
        manager.increaseAffection(pokemon, 150);
        assertEquals(AffectionManager.MAX_AFFECTION, manager.getAffectionPoint(pokemon));

        manager.decreaseAffection(pokemon, 200);
        assertEquals(AffectionManager.MIN_AFFECTION, manager.getAffectionPoint(pokemon));
    }

    @Test
    void rejectsNegativeModifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> manager.increaseAffection(pokemon, -1));
    }

    @Test
    void rejectsReadingUnregisteredPokemon() {
        assertThrows(IllegalArgumentException.class,
                () -> manager.getAffectionPoint(new TestActor("Unknown")));
    }

    private static final class TestActor extends Actor {
        private TestActor(String name) {
            super(name, 'p', 100);
        }

        @Override
        public Action playTurn(ActionList actions, Action lastAction, GameMap map, Display display) {
            return null;
        }

        @Override
        public void creatIntrinsicWeapon() {
        }
    }
}
