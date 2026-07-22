package game.time;

import edu.monash.fit2099.engine.displays.Display;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Coordinates the global five-turn day/night cycle and notifies registered
 * {@link TimePerception} objects using the Observer pattern.
 */
public final class TimePerceptionManager {

    public static final int TURNS_PER_PERIOD = 5;

    private static final TimePerceptionManager INSTANCE = new TimePerceptionManager();

    private final List<TimePerception> observers = new ArrayList<>();
    private int turn;
    private TimePeriod currentPeriod;

    private TimePerceptionManager() {
        reset();
    }

    public static TimePerceptionManager getInstance() {
        return INSTANCE;
    }

    /** Advances the clock and returns the period applying to the new turn. */
    public TimePeriod advanceTurn() {
        currentPeriod = periodAt(turn);
        turn++;
        return currentPeriod;
    }

    /** Compatibility entry point used by the console world. */
    public void manageTimePeriod(Display display) {
        Objects.requireNonNull(display, "display cannot be null");
        TimePeriod period = advanceTurn();
        display.println("It is " + (period == TimePeriod.DAY ? "Day-time" : "Night-time")
                + " (turn " + (turn - 1) + ")");
    }

    /** Applies the current period's effect once to every registered observer. */
    public void run() {
        List<TimePerception> snapshot = new ArrayList<>(observers);
        for (TimePerception observer : snapshot) {
            if (currentPeriod == TimePeriod.DAY) {
                observer.dayEffect();
            } else {
                observer.nightEffect();
            }
        }
    }

    public void append(TimePerception observer) {
        Objects.requireNonNull(observer, "observer cannot be null");
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void cleanUp(TimePerception observer) {
        observers.remove(observer);
    }

    public List<TimePerception> getTimePerceptionList() {
        return Collections.unmodifiableList(new ArrayList<>(observers));
    }

    public int getTurn() {
        return turn;
    }

    public TimePeriod getCurrentPeriod() {
        return currentPeriod;
    }

    private TimePeriod periodAt(int turnNumber) {
        int periodIndex = (turnNumber / TURNS_PER_PERIOD) % 2;
        return periodIndex == 0 ? TimePeriod.DAY : TimePeriod.NIGHT;
    }

    /** Restores a new-game state and removes stale observers. */
    public void reset() {
        observers.clear();
        turn = 0;
        currentPeriod = TimePeriod.DAY;
    }
}
