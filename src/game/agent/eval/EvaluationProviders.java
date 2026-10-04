package game.agent.eval;
import game.agent.llm.*;import game.agent.runtime.AgentLoop;import java.util.*;
/** Finite server allowlist; credentials never enter metadata, checkpoints or browser input. */
public final class EvaluationProviders {
 public interface Factory {AgentLoop.DecisionProvider create(String model);}
 private final List<String> models;private final Factory factory;private final int timeout;
 public EvaluationProviders(List<String> models,int timeout,Factory factory){if(models==null||models.isEmpty()||models.size()>4||new HashSet<>(models).size()!=models.size()||timeout<1||timeout>61000)throw new IllegalArgumentException("INVALID_PROVIDERS");for(String model:models)if(!model.matches("[a-zA-Z0-9_.:-]{1,128}"))throw new IllegalArgumentException("INVALID_MODEL");this.models=Collections.unmodifiableList(new ArrayList<>(models));this.timeout=timeout;this.factory=Objects.requireNonNull(factory);}
 public List<String> models(){return models;}public int timeout(){return timeout;}
 public AgentLoop.DecisionProvider create(String model){if(!models.contains(model))throw new IllegalArgumentException("MODEL_NOT_CONFIGURED");return factory.create(model);}
 public static EvaluationProviders environment(){return environment(System.getenv());}
 public static EvaluationProviders environment(Map<String,String> env){
  if("disabled".equals(env.get("LLM_PROVIDER")))return new EvaluationProviders(Collections.singletonList("baseline"),1000,m->EvaluationRun.baseline());
  if(env.containsKey("PUBLIC_ORIGIN"))return new EvaluationProviders(Collections.singletonList("baseline"),1000,m->EvaluationRun.baseline());
  boolean deepSeek="deepseek".equals(env.get("LLM_PROVIDER"));
  String modelVariable=deepSeek?"DEEPSEEK_MODEL":"OPENAI_MODEL";
  String primary=env.getOrDefault(modelVariable,deepSeek?"deepseek-flash":"gpt-6-luna");
  List<String> names=new ArrayList<>(Arrays.asList(env.getOrDefault("EVAL_MODELS",primary).split(",")));
  if(!names.contains("baseline") && names.size()<4)names.add("baseline");
  int timeout=16000;
  try{timeout=Math.min(61000,Math.max(1001,Integer.parseInt(env.getOrDefault(deepSeek?"DEEPSEEK_TIMEOUT_MS":"OPENAI_TIMEOUT_MS","15000"))+1000));}
  catch(NumberFormatException ignored){}
  return new EvaluationProviders(names,timeout,model->{
   if("baseline".equals(model))return EvaluationRun.baseline();
   Map<String,String> config=new HashMap<>(env);config.put(modelVariable,model);
   return deepSeek?new OpenAiGateway(OpenAiConfig.fromDeepSeekEnvironment(config),new DeepSeekTransport()):
    new OpenAiGateway(OpenAiConfig.fromEnvironment(config),new OpenAiHttpTransport());
  });
 }
}
