package game.time;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimePerceptionManagerTest {

    private final TimePerceptionManager manager = TimePerceptionManager.getInstance();

    @BeforeEach
    void setUp() {
        manager.reset();
    }

    @Test
    void alternatesAfterEveryFiveTurns() {
        for (int turn = 0; turn < 5; turn++) {
            assertEquals(TimePeriod.DAY, manager.advanceTurn());
        }
        for (int turn = 0; turn < 5; turn++) {
            assertEquals(TimePeriod.NIGHT, manager.advanceTurn());
        }
        assertEquals(TimePeriod.DAY, manager.advanceTurn());
    }

    @Test
    void notifiesObserversForTheCurrentPeriod() {
        CountingObserver observer = new CountingObserver();
        manager.append(observer);

        manager.advanceTurn();
        manager.run();
        assertEquals(1, observer.dayEffects);
        assertEquals(0, observer.nightEffects);

        for (int turn = 1; turn <= 5; turn++) {
            manager.advanceTurn();
        }
        manager.run();
        assertEquals(1, observer.nightEffects);
    }

    @Test
    void ignoresDuplicateRegistrationsAndSupportsCleanup() {
        CountingObserver observer = new CountingObserver();
        manager.append(observer);
        manager.append(observer);
        assertEquals(1, manager.getTimePerceptionList().size());

        manager.cleanUp(observer);
        assertEquals(0, manager.getTimePerceptionList().size());
    }

    private static final class CountingObserver implements TimePerception {
        private int dayEffects;
        private int nightEffects;

        @Override
        public void dayEffect() {
            dayEffects++;
        }

        @Override
        public void nightEffect() {
            nightEffects++;
        }
    }
}
