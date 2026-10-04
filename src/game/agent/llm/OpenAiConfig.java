package game.agent.llm;

import java.util.*;

/** Credentials stay in server process memory and are never printed. */
public final class OpenAiConfig {
    private final String key,model;
    private final int timeoutMillis;
    public OpenAiConfig(String key,String model,int timeoutMillis) {
        if(key==null || key.isEmpty()) throw new ProviderException(ProviderException.Code.CONFIGURATION);
        if(key.length()>512 || !key.matches("[!-~]+") || model==null || !model.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}")
                || timeoutMillis<1 || timeoutMillis>60000) throw new IllegalArgumentException("INVALID_PROVIDER_CONFIG");
        this.key=key; this.model=model; this.timeoutMillis=timeoutMillis;
    }
    public static OpenAiConfig fromEnvironment(Map<String,String> env) {
        try { return new OpenAiConfig(env.get("OPENAI_API_KEY"),env.getOrDefault("OPENAI_MODEL","gpt-6-luna"),
            Integer.parseInt(env.getOrDefault("OPENAI_TIMEOUT_MS","15000"))); }
        catch(IllegalArgumentException error) { throw new ProviderException(ProviderException.Code.CONFIGURATION); }
    }
    /** Dedicated provider variables; never falls back to another project's key. */
    public static OpenAiConfig fromDeepSeekEnvironment(Map<String,String> env) {
        try {return new OpenAiConfig(env.get("DEEPSEEK_API_KEY"),env.getOrDefault("DEEPSEEK_MODEL","deepseek-flash"),
            Integer.parseInt(env.getOrDefault("DEEPSEEK_TIMEOUT_MS","15000")));}
        catch(IllegalArgumentException error){throw new ProviderException(ProviderException.Code.CONFIGURATION);}
    }
    String key() { return key; }
    public String getModel() { return model; }
    public int getTimeoutMillis() { return timeoutMillis; }
}
