package game.agent.eval;
import game.agent.llm.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DeepSeekEvaluationTest {
    @Test void disabledWebsiteCannotIssueHiddenEvaluationApiCalls(){
        Map<String,String> env=new HashMap<>();env.put("LLM_PROVIDER","disabled");env.put("EVAL_MODELS","gpt-6-luna");
        assertEquals(Arrays.asList("baseline"),EvaluationProviders.environment(env).models());
    }
    @Test void deepSeekEvaluationUsesItsDedicatedKeyAndModel(){
        Map<String,String> env=new HashMap<>();env.put("LLM_PROVIDER","deepseek");env.put("DEEPSEEK_API_KEY","test-key");
        EvaluationProviders providers=EvaluationProviders.environment(env);
        assertEquals(Arrays.asList("deepseek-flash","baseline"),providers.models());
        assertNotNull(providers.create("deepseek-flash"));
        env.remove("DEEPSEEK_API_KEY");env.put("OPENAI_API_KEY","other-key");
        assertThrows(ProviderException.class,()->EvaluationProviders.environment(env).create("deepseek-flash"));
    }
}
