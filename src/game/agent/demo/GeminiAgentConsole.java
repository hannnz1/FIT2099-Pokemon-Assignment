package game.agent.demo;

import game.agent.llm.*;
import java.io.*;
import java.util.*;

/** Existing Gemini entry retained; both providers use the same trusted quest runner. */
public final class GeminiAgentConsole {
    private GeminiAgentConsole() { }
    public static void main(String[] args) {
        int exit=run(System.getenv(),System.in,System.out,new GeminiHttpTransport());
        if(exit!=0)System.exit(exit);
    }
    public static int run(Map<String,String> env,InputStream input,PrintStream output,GeminiTransport transport) {
        GeminiConfig config;
        try {config=GeminiConfig.fromEnvironment(env);}
        catch(ProviderException error){output.println("未配置有效的 GEMINI_API_KEY。请在本机环境变量配置后重新打开终端。");return 2;}
        GeminiGateway gateway=new GeminiGateway(config,transport);
        return QuestAgentConsole.run(gateway,new NaturalTaskParser(gateway),config.getTimeoutMillis(),"Gemini",input,output);
    }
}
