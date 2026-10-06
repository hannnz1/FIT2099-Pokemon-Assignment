import game.agent.eval.*;
import game.agent.llm.*;
import game.agent.runtime.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
public class DeliveryLiveBenchmark {
 public static void main(String[] a)throws Exception{
  boolean live=a.length>0&&a[0].equals("live");String model=live?"gpt-6-luna":"baseline";
  Path out=Paths.get("outputs/delivery-live-20261005-"+model+".jsonl");Files.write(out,new byte[0]);
  EvaluationProviders providers=EvaluationProviders.environment();ExecutorService executor=Executors.newSingleThreadExecutor();final long[] used={0};
  try{int samples=live?3:20;
   for(int rep=0;rep<samples;rep++)for(String scene:EvaluationRun.SCENARIOS){
    long seed=11+rep*12;Map<String,Object> spec=Json.object("id","bench-"+model+"-"+scene+"-"+seed,"scenario",scene,"seed",seed,"repetition",rep+1,"model",model,"starter","TREECKO","decisions",100,"wallMillis",180000,"price",Collections.emptyMap());
    EvaluationRun run=new EvaluationRun(spec);AgentLoop.DecisionProvider raw=live?providers.create(model):EvaluationRun.baseline();
    AgentLoop.DecisionProvider guard=new AgentLoop.DecisionProvider(){public game.agent.tools.ToolRequest decide(AgentLoop.Context c){if(used[0]>=350000)throw new ProviderException(ProviderException.Code.BUDGET_EXHAUSTED);try{return raw.decide(c);}finally{Number n=raw.getUsage().get("totalTokens");if(n!=null)used[0]+=n.longValue();else used[0]=350000;}}public Map<String,Number> getUsage(){return raw.getUsage();}};
    run.resume(guard,live?executor:Runnable::run,16000);long start=System.nanoTime();
    while(!run.terminal()){run.tick(System.nanoTime()/1000000);if(live)Thread.sleep(5);}
    Map<String,Object> report=run.report(true);report.put("measuredWallMs",(System.nanoTime()-start)/1000000.0);report.put("benchmarkProvider",live?"openai":"scripted");
    Files.write(out,(Json.write(report)+"\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.APPEND);
    System.out.println(model+" "+scene+" seed="+seed+" "+run.status()+" "+Json.write(report.get("metrics")));
    if(live&&used[0]>=350000){System.out.println("BENCHMARK_TOKEN_CAP");return;}
   }
  }finally{executor.shutdownNow();}
 }
}
