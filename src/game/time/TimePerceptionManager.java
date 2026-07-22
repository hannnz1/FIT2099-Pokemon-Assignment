package game.time;

import edu.monash.fit2099.engine.displays.Display;
import edu.monash.fit2099.engine.positions.Location;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A global Singleton manager that gives time perception  on the affected instances.
 * TODO: you may modify (add or remove) methods in this class if you think they are not necessary.
 * HINT: refer to Bootcamp Week 5 about static factory method.
 *
 * Created by:
 * @author Riordan D. Alfredo
 * Modified by:
 *
 */
public class TimePerceptionManager {
    /**
     * A list of polymorph instances (any classes that implements TimePerception,
     * such as, a Charmander implements TimePerception, it will be stored in here)
     */
    private final List<TimePerception> timePerceptionList;

    private int turn;

    private TimePeriod shift; // DAY or NIGHT

    /**
     * A singleton instance
     */
    private static TimePerceptionManager instance = null;

    /**
     * Get the singleton instance of time perception manager
     *
     * @return TimePerceptionManager singleton instance
     *
     * FIXME: create a singleton instance.
     */
    public static TimePerceptionManager getInstance() {
        if (instance == null) {
            instance = new TimePerceptionManager();
        }
        return instance;
    }

    /**
     * Private constructor
     */
    private TimePerceptionManager() {
        timePerceptionList = new ArrayList<>();
        turn = 0;
    }

    public void manageTimePeriod(Display display) {
        // track turn when 5 change to night when 10 change to night
        if (turn % 10 == 0) {
            shift = TimePeriod.DAY;
        } else if (turn % 10 == 5) {
            shift = TimePeriod.NIGHT;
        }
        if (shift == TimePeriod.DAY) {
            display.println("It is a Day-time (turn " + turn + ")");
        } else if (shift == TimePeriod.NIGHT) {
            display.println(
                "It is a Night-time (turn " + turn + ")");
        }
        turn += 1;
    }


    /**
     * Traversing through all instances in the list and execute them
     * By doing this way, it will avoid using `instanceof` all over the place.
     *
     * FIXME: write a relevant logic (i.e., increment turns choose day or night) and call this method once at every turn.
     */
    //public void run() {
    //public void run(Location location) {
    public void run() {
        if (shift == TimePeriod.DAY) {
            for (TimePerception object : timePerceptionList) {
                object.dayEffect();
            }
        } else if (shift == TimePeriod.NIGHT) {
            for (TimePerception object : timePerceptionList) {
                object.nightEffect();
            }

//            for (Iterator<TimePerception> iterator = timePerceptionList.iterator(); iterator.hasNext();) {
//                TimePerception object = iterator.next();
//                object.nightEffect();
        }
    }


    /**
     * Add the TimePerception instance to the list
     * FIXME: add objInstance to the list.
     * @param objInstance any instance that implements TimePerception
     */
    public void append (TimePerception objInstance) {
        timePerceptionList.add(objInstance);
    }


    /**
     * Remove a TimePerception instance from the list
     *
     * FIXME: [OPTIONAL] run cleanUp once every turn if you don't want to
     *        have too many instances in the list (e.g., memory leak)
     * @param objInstance object instance
     */
    public void cleanUp(TimePerception objInstance) {
        timePerceptionList.remove(objInstance);
    }

    public List<TimePerception> getTimePerceptionList() {
        return Collections.unmodifiableList(timePerceptionList);
    }

}


