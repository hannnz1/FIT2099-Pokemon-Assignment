package game.actions;

import edu.monash.fit2099.engine.actions.Action;
import edu.monash.fit2099.engine.actions.ActionList;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.displays.Display;
import edu.monash.fit2099.engine.positions.GameMap;
import game.conditions.AffectionManager;
import game.conditions.FavoriteAffection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AffectionActionTest {

    private final AffectionManager manager = AffectionManager.getInstance();
    private Actor trainer;
    private Actor pokemon;

    @BeforeEach
    void setUp() {
        manager.reset();
        trainer = new TestActor("Ash");
        pokemon = new TestActor("Mudkip");
        pokemon.addCapability(FavoriteAffection.CHEST_POUNDING);
    }

    @Test
    void favouriteInteractionAddsTenPoints() {
        new ChestPoundingAction(pokemon).execute(trainer, null);
        assertEquals(10, manager.getAffectionPoint(pokemon));
    }

    @Test
    void nonFavouriteInteractionRemovesTwentyPoints() {
        new SingingAction(pokemon).execute(trainer, null);
        assertEquals(-20, manager.getAffectionPoint(pokemon));
    }

    @Test
    void repeatedActionsUseTheSameRelationshipState() {
        new ChestPoundingAction(pokemon).execute(trainer, null);
        new ChestPoundingAction(pokemon).execute(trainer, null);
        assertEquals(20, manager.getAffectionPoint(pokemon));
    }

    private static final class TestActor extends Actor {
        private TestActor(String name) {
            super(name, 'a', 100);
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
