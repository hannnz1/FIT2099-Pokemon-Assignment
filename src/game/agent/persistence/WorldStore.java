package game.agent.persistence;
import java.util.Map;
/** Owner-scoped repository of trusted, inert world checkpoints. */
public interface WorldStore extends AutoCloseable {
    void save(String owner, Map<String,Object> checkpoint);
    Map<String,Object> load(String owner);
    @Override void close();
}
