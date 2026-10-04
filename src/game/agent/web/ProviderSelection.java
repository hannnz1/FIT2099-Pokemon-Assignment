package game.agent.web;

import game.agent.llm.*;
import java.util.*;

/** Environment credentials remain private to gateways; public metadata has no keys. */
public final class ProviderSelection {
    final String name,model;
    final int timeoutMillis;
    final LlmGateway gateway;
    final TaskInterpreter interpreter;
    public ProviderSelection(String name,String model,int timeoutMillis,LlmGateway gateway,TaskInterpreter interpreter) {
        this.name=Objects.requireNonNull(name); this.model=Objects.requireNonNull(model);
        if(timeoutMillis<=0) throw new IllegalArgumentException("INVALID_TIMEOUT");
        this.timeoutMillis=timeoutMillis;this.gateway=gateway;this.interpreter=interpreter;
    }
    public boolean isAvailable() { return gateway!=null && interpreter!=null; }
    public static ProviderSelection fromEnvironment(Map<String,String> env) {
        String provider=env.getOrDefault("LLM_PROVIDER","openai");
        try {
            if("deepseek".equals(provider)) {
                OpenAiConfig config=OpenAiConfig.fromDeepSeekEnvironment(env);
                OpenAiGateway gateway=new OpenAiGateway(config,new DeepSeekTransport(PublicAiBudget.protect(DeepSeekTransport.officialHttpTransport(),env)));
                return new ProviderSelection(provider,config.getModel(),config.getTimeoutMillis(),gateway,gateway);
            }
            if("openai".equals(provider)) {
                OpenAiConfig config=OpenAiConfig.fromEnvironment(env);
                OpenAiGateway gateway=new OpenAiGateway(config,new OpenAiHttpTransport());
                return new ProviderSelection(provider,config.getModel(),config.getTimeoutMillis(),gateway,gateway);
            }
            if("gemini".equals(provider)) {
                GeminiConfig config=GeminiConfig.fromEnvironment(env);
                GeminiGateway gateway=new GeminiGateway(config,new GeminiHttpTransport());
                return new ProviderSelection(provider,config.getModel(),config.getTimeoutMillis(),gateway,new NaturalTaskParser(gateway));
            }
        } catch(ProviderException | IllegalArgumentException ignored) { /* finite public error only */ }
        return new ProviderSelection(Arrays.asList("gemini","deepseek","disabled").contains(provider)?provider:"openai", "unavailable",15000,null,null);
    }
}
