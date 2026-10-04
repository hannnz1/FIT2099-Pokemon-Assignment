package game.agent.demo;
import game.agent.llm.*;
import java.io.*;
import java.util.*;
/** Isolated opt-in acceptance runner. No production saves, no scripted fallback. */
public final class DeepSeekAgentConsole {
    private DeepSeekAgentConsole(){}
    public static void main(String[] args){int result=run(System.getenv(),System.in,System.out,DeepSeekTransport.officialHttpTransport());if(result!=0)System.exit(result);}
    public static int run(Map<String,String> env,InputStream input,PrintStream output,JsonTransport transport){
        OpenAiConfig config;
        try{config=OpenAiConfig.fromDeepSeekEnvironment(env);}
        catch(ProviderException|IllegalArgumentException error){output.println("DeepSeek 配置不可用：请检查 DEEPSEEK_API_KEY、模型和超时。");return 2;}
        java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        JsonTransport bounded=(model,key,body,timeout)->{
            if(calls.incrementAndGet()>24)throw new IOException("ACCEPTANCE_CALL_LIMIT");
            return transport.send(model,key,body,timeout);
        };
        OpenAiGateway gateway=new OpenAiGateway(config,new DeepSeekTransport(bounded));
        int result=QuestAgentConsole.run(gateway,gateway,config.getTimeoutMillis(),"DeepSeek",input,output);
        output.println("验收 API 请求次数："+Math.min(calls.get(),24));return result;
    }
}
