package game.agent;

import game.agent.web.ProviderSelection;
import game.agent.llm.Json;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeepSeekSelectionTest {
    @Test void dedicatedDeepSeekKeyEnablesExistingGameModes() {
        Map<String,String> env=new HashMap<>();
        env.put("LLM_PROVIDER","deepseek");env.put("DEEPSEEK_API_KEY","test-key");
        assertTrue(ProviderSelection.fromEnvironment(env).isAvailable());
    }
    @Test void neverFallsBackToAnOpenAiKey() {
        Map<String,String> env=new HashMap<>();
        env.put("LLM_PROVIDER","deepseek");env.put("OPENAI_API_KEY","other-project-key");
        assertFalse(ProviderSelection.fromEnvironment(env).isAvailable());
    }
}
