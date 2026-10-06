import game.agent.eval.*;
import game.agent.llm.*;
import game.agent.tools.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class DeliveryReplay {
 public static void main(String[] args)throws Exception {
  List<Object> results=new ArrayList<>();
  for(Object item:Json.asArray(Json.read(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8)))){
   Map<String,Object> fixture=Json.asObject(item);String scenario=(String)fixture.get("scenario");long seed=((Number)fixture.get("seed")).longValue();
   Map<String,Object> spec=Json.object("id","archived-decisions-"+scenario+seed,"scenario",scenario,"seed",seed,"repetition",1,"model","archived-decisions","starter","TREECKO","decisions",100,"wallMillis",180000,"price",Collections.emptyMap());
   List<Object> choices=Json.asArray(fixture.get("choices"));int[] index={0};EvaluationRun run=new EvaluationRun(spec);
   run.resume(c->{if(index[0]>=choices.size())throw new IllegalStateException("ARCHIVED_CHOICES_EXHAUSTED");Map<String,Object> choice=Json.asObject(choices.get(index[0]++));return new ToolRequest("archived",(String)choice.get("tool"),Json.asObject(choice.get("arguments")));},Runnable::run,1000);
   for(int i=1;i<1000&&!run.terminal();i++)run.tick(i);
   Map<String,Object> result=run.report(true);result.put("validationType","OFFLINE_ARCHIVED_DECISION_REPLAY_NO_API_CALLS");results.add(result);
   System.out.println(scenario+" seed="+seed+" status="+run.status()+" archivedChoicesConsumed="+index[0]+" "+Json.write(result.get("metrics")));
   if(!"COMPLETED".equals(run.status()))throw new IllegalStateException("Replay did not complete");
  }
  Files.write(Paths.get(args[1]),Json.write(results).getBytes(StandardCharsets.UTF_8));
 }
}
