package game.time;

/** Observer for objects whose state changes between day and night. */
public interface TimePerception {

    void dayEffect();

    void nightEffect();

    default void registerInstance() {
        TimePerceptionManager.getInstance().append(this);
    }

    default void unregisterInstance() {
        TimePerceptionManager.getInstance().cleanUp(this);
    }
}
