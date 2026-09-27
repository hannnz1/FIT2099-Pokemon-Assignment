package game.conditions;

import edu.monash.fit2099.engine.actors.Actor;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stores the affection relationship between the trainer and each Pokemon.
 *
 * <p>The manager is shared by the game so that affection survives across
 * actions and turns. Actor identity is deliberately used as the key because
 * two Pokemon of the same species still have independent affection values.</p>
 */
public final class AffectionManager {

    public static final int MIN_AFFECTION = -50;
    public static final int MAX_AFFECTION = 100;

    private static final AffectionManager INSTANCE = new AffectionManager();

    private final Map<Actor, Integer> affectionPoints = new IdentityHashMap<>();
    private Actor trainer;

    public AffectionManager() {
    }

    public static AffectionManager getInstance() {
        return INSTANCE;
    }

    public void registerTrainer(Actor trainer) {
        this.trainer = Objects.requireNonNull(trainer, "trainer cannot be null");
    }

    public Actor getTrainer() {
        return trainer;
    }

    public void registerPokemon(Actor pokemon) {
        Objects.requireNonNull(pokemon, "pokemon cannot be null");
        affectionPoints.putIfAbsent(pokemon, 0);
    }

    public boolean isRegistered(Actor pokemon) {
        return affectionPoints.containsKey(pokemon);
    }

    public int getAffectionPoint(Actor pokemon) {
        Objects.requireNonNull(pokemon, "pokemon cannot be null");
        Integer points = affectionPoints.get(pokemon);
        if (points == null) {
            throw new IllegalArgumentException("Pokemon is not registered: " + pokemon);
        }
        return points;
    }

    public String increaseAffection(Actor pokemon, int points) {
        validateModifier(points);
        return format(updateAffection(pokemon, points));
    }

    public String decreaseAffection(Actor pokemon, int points) {
        validateModifier(points);
        return format(updateAffection(pokemon, -points));
    }

    private int updateAffection(Actor pokemon, int delta) {
        registerPokemon(pokemon);
        int current = affectionPoints.get(pokemon);
        int updated = Math.max(MIN_AFFECTION, Math.min(MAX_AFFECTION, current + delta));
        affectionPoints.put(pokemon, updated);
        return updated;
    }

    private void validateModifier(int points) {
        if (points < 0) {
            throw new IllegalArgumentException("affection modifier must be non-negative");
        }
    }

    private String format(int points) {
        return "(" + points + "AP)";
    }

    /** Clears game-session state. Primarily useful when starting a new game or test. */
    public void forget(Actor actor) { affectionPoints.remove(actor); }
    public void retain(java.util.Set<Object> active) { affectionPoints.keySet().removeIf(a -> !active.contains(a)); }

    public void reset() {
        affectionPoints.clear();
        trainer = null;
    }
}
