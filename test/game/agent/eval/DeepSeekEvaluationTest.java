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
    @Test void nativeEvaluationTraceKeepsActualConfiguredProviderIdentity(){
        for(String model:Arrays.asList("deepseek-flash","gpt-6-luna","baseline")){
            Map<String,Object> spec=Json.object("id",UUID.randomUUID().toString(),"scenario","information","seed",11,"repetition",1,"model",model,"starter","TREECKO","decisions",30,"wallMillis",10000,"price",Collections.emptyMap());
            EvaluationRun run=new EvaluationRun(spec);run.resume(EvaluationRun.baseline(),Runnable::run,1000);
            long started=System.currentTimeMillis();for(int tick=0;tick<100&&!run.terminal();tick++)run.tick(started+tick*10);
            assertEquals("COMPLETED",run.status());
            String expected=model.startsWith("deepseek-")?"deepseek":"baseline".equals(model)?"scripted":"openai";
            java.util.List<Object> trace=Json.asArray(run.report(true).get("trace"));assertFalse(trace.isEmpty());
            for(Object raw:trace){Map<String,Object> row=Json.asObject(raw);assertEquals(expected,row.get("provider"));assertEquals(model,row.get("model"));}
        }
    }
}
