package game.agent.demo;

import game.agent.llm.*;
import java.io.*;
import java.util.*;

/** Opt-in OpenAI entry. Environment-only key, no scripted fallback on live failure. */
public final class OpenAiAgentConsole {
    private OpenAiAgentConsole() { }
    public static void main(String[] args) {
        int exit=run(System.getenv(),System.in,System.out,new OpenAiHttpTransport());
        if(exit!=0)System.exit(exit);
    }
    public static int run(Map<String,String> env,InputStream input,PrintStream output,JsonTransport transport) {
        OpenAiConfig config;
        try {config=OpenAiConfig.fromEnvironment(env);}
        catch(ProviderException error){output.println("未配置有效的 OPENAI_API_KEY。请在本机环境变量配置后重新打开终端。");return 2;}
        OpenAiGateway gateway=new OpenAiGateway(config,transport);
        return QuestAgentConsole.run(gateway,gateway,config.getTimeoutMillis(),"OpenAI ("+config.getModel()+")",input,output);
    }
}
