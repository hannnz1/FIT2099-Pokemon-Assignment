package game.agent.eval;
import game.agent.llm.Json;import game.agent.runtime.AgentLoop;import game.agent.tools.ToolRequest;import java.nio.file.*;import java.util.*;
public class V5BaselineBenchmark {
 public static void main(String[] args)throws Exception{List<Map<String,Object>> rows=new ArrayList<>();for(int seed=1;seed<=30;seed++)for(String strategy:Arrays.asList("baseline","first-legal")){
  Map<String,Object> spec=Json.object("id",UUID.randomUUID().toString(),"scenario","battle","seed",seed,"model",strategy,"starter","MUDKIP","repetition",1,"decisions",100,"wallMillis",60000,"price",Collections.emptyMap());EvaluationRun run=new EvaluationRun(spec);
  AgentLoop.DecisionProvider provider=strategy.equals("baseline")?EvaluationRun.baseline():c->{Map<String,String> o=c.getObservation();if(Boolean.parseBoolean(o.get("forcedSwitch")))return new ToolRequest("choice","battle_switch",Json.object("slot",Json.asArray(Json.read(o.get("legalSwitches"))).get(0)));return new ToolRequest("choice","battle_move",Json.object("moveId",Json.asArray(Json.read(o.get("legalMoves"))).get(0)));};
  run.resume(provider,Runnable::run,1000);for(int n=1;n<1000&&!run.terminal();n++)run.tick(n*10);Map<String,Object> report=run.report(true);report.put("realAi",false);rows.add(report);if(!run.status().equals("COMPLETED"))System.out.println(strategy+" seed="+seed+" "+run.status());
 }
 Path out=Paths.get(args[0]);Files.createDirectories(out);try(java.io.BufferedWriter writer=Files.newBufferedWriter(out.resolve("cases.jsonl"),java.nio.charset.StandardCharsets.UTF_8)){for(Map<String,Object> row:rows){writer.write(Json.write(row));writer.newLine();}}Files.write(out.resolve("summary.json"),Json.write(Json.object("realAi",false,"cases",rows.size(),"comparison",EvaluationStatistics.compare(rows))).getBytes(java.nio.charset.StandardCharsets.UTF_8));System.out.println(Json.write(EvaluationStatistics.compare(rows)));
 }
}
