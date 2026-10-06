package game.agent.persistence;
import java.util.Map;
/** Owner-scoped repository of trusted, inert world checkpoints. */
public interface WorldStore extends AutoCloseable {
    void save(String owner, Map<String,Object> checkpoint);
    Map<String,Object> load(String owner);
    /** Delete only the exact owner-scoped checkpoint; legacy repositories may defer cleanup. */
    default boolean delete(String owner) { return false; }
    @Override void close();
}
