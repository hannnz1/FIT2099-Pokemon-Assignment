package game.agent.llm;

import java.util.*;

/** Environment-only live credentials; no files, command-line keys or secret logging. */
public final class GeminiConfig {
    private final String key,model;
    private final int timeoutMillis;
    public GeminiConfig(String key,String model,int timeoutMillis) {
        if(key==null || key.isEmpty()) throw new ProviderException(ProviderException.Code.CONFIGURATION);
        if(key.length()>512 || !key.matches("[!-~]+") || model==null || !model.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}")
                || timeoutMillis<=0 || timeoutMillis>60000) throw new IllegalArgumentException("INVALID_PROVIDER_CONFIG");
        this.key=key; this.model=model; this.timeoutMillis=timeoutMillis;
    }
    public static GeminiConfig fromEnvironment(Map<String,String> env) {
        try { return new GeminiConfig(env.get("GEMINI_API_KEY"),env.getOrDefault("GEMINI_MODEL","gemini-3.8-flash"),
            Integer.parseInt(env.getOrDefault("GEMINI_TIMEOUT_MS","15000"))); }
        catch(IllegalArgumentException error) { throw new ProviderException(ProviderException.Code.CONFIGURATION); }
    }
    String key() { return key; }
    public String getModel() { return model; }
    public int getTimeoutMillis() { return timeoutMillis; }
}
