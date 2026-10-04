package game.agent.combat;

import game.agent.runtime.*;
import game.agent.tools.*;
import game.agent.llm.*;
import game.agent.action.ActionResult;
import game.agent.persistence.*;
import java.util.*;
import java.util.concurrent.*;

/** Separate opt-in playable arena. --demo is explicitly offline; --live uses
 * the existing OpenAI gateway and never falls back to scripted decisions. */
public final class BattleTrainingConsole {
    private BattleTrainingConsole(){ }
    public static void main(String[] args){
        Set<String> flags=new HashSet<>(Arrays.asList(args));
        if(flags.size()!=args.length||!Arrays.asList("--demo","--live","--no-battle","--new").containsAll(flags)||flags.contains("--demo")&&flags.contains("--live")){
            System.out.println("Usage: BattleTrainingConsole [--demo | --live] [--no-battle] [--new]");return;
        }
        Map<String,String> env=new HashMap<>(System.getenv());env.putIfAbsent("AGENT_SAVE_DIR","data/battle-training");
        boolean demo=flags.contains("--demo"),live=flags.contains("--live");String owner=demo?"battle-training-offline-demo":"battle-training-local-player";
        ExecutorService workers=Executors.newSingleThreadExecutor(r->{Thread thread=new Thread(r,"battle-model");thread.setDaemon(true);return thread;});
        try(WorldStore store=WorldStoreFactory.fromEnvironment(env)){
            Map<String,Object> checkpoint=demo||flags.contains("--new")||store==null?null:store.load(owner);
            BattleTrainingScenario scene=checkpoint==null?new BattleTrainingScenario():BattleTrainingScenario.restore(checkpoint);
            System.out.println("Java battle training / original engine rules / trainer owns captured balls");
            if(demo){
                System.out.println("OFFLINE: scripted provider decisions; actual engine capture; no API calls.");
                Queue<ToolRequest> choices=new ArrayDeque<>(Arrays.asList(new ToolRequest("demo-move","move_to",Json.object("locationId","treecko-approach")),new ToolRequest("demo-capture","capture",Json.object("targetId","wild-treecko"))));
                AgentLoop loop=scene.start(c->choices.remove(),Runnable::run,true);loop.configureTrace("mudkip","offline-scripted","battle-fixture");run(scene,loop,store,owner);
                if(!scene.isComplete()||loop.getState()!=AgentTask.State.COMPLETED)throw new IllegalStateException("OFFLINE_DEMO_FAILED");
                if(store!=null&&!Json.write(scene.exportState()).equals(Json.write(store.load(owner))))throw new IllegalStateException("CHECKPOINT_MISMATCH");
                System.out.println("OFFLINE_DEMO_COMPLETED captured="+scene.getCapturedCount()+" metrics="+Json.write(loop.getMetrics()));return;
            }
            if(live){
                if(scene.isComplete()){System.out.println("Saved capture goal already complete. Use manual mode to inspect it.");return;}
                OpenAiConfig config=OpenAiConfig.fromEnvironment(env);
                AgentLoop loop=scene.start(new OpenAiGateway(config,new OpenAiHttpTransport()),workers,flags.contains("--no-battle"));loop.configureTrace("mudkip","openai",config.getModel());
                run(scene,loop,store,owner);System.out.println("LIVE_RESULT "+loop.getState()+" captured="+scene.getCapturedCount());
                if(loop.getState()!=AgentTask.State.COMPLETED)System.out.println("AI stopped without inventing completion. Restart manual mode to take over saved state.");return;
            }
            System.out.println("Commands: status | move N/S/E/W | attack wild-treecko/wild-torchic | capture wild-treecko/wild-torchic | quit");
            System.out.println("Capture rule: Treecko is directly capturable; Torchic is not. No low-HP or affection threshold exists in current legacy capture action.");
            try(Scanner input=new Scanner(System.in,"UTF-8")){
                show(scene);while(input.hasNextLine()){
                    String line=input.nextLine().trim();if("quit".equals(line))break;if("status".equals(line)){show(scene);continue;}
                    String[] command=line.split("\\s+");if(command.length!=2){System.out.println("INVALID_COMMAND");continue;}
                    ActionResult result=scene.manual(command[0],command[1]);System.out.println(result);
                    if(result.getStatus()==ActionResult.Status.SUCCESS)save(store,owner,scene);show(scene);
                }
            }
        }catch(ProviderException failure){System.out.println("PROVIDER_"+failure.getCode()+"; manual mode remains available.");}
        catch(StoreException failure){System.out.println("STORAGE_"+failure.getCode()+"; stopped, reload durable state before further action.");}
        catch(IllegalArgumentException failure){System.out.println("INVALID_CONFIGURATION_OR_CHECKPOINT; stopped without changing the saved file.");}
        catch(IllegalStateException failure){System.out.println("TASK_CANNOT_START; use manual mode or --new for a new training episode.");}
        finally{workers.shutdownNow();}
    }
    private static void run(BattleTrainingScenario scene,AgentLoop loop,WorldStore store,String owner){
        long start=System.nanoTime();int ticks=0;save(store,owner,scene);
        while((loop.getState()==AgentTask.State.RUNNING||loop.getState()==AgentTask.State.REPLANNING)&&ticks++<6000){
            long now=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);if(now>=120000){loop.cancel();break;}
            ActionResult result=loop.tick(now);
            if(!"DECISION_PENDING".equals(result.getCode())){scene.advance(result);System.out.println(result);save(store,owner,scene);}
            else try{Thread.sleep(20);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();loop.cancel();break;}
        }
        if(loop.getState()==AgentTask.State.RUNNING||loop.getState()==AgentTask.State.REPLANNING)loop.cancel();
        save(store,owner,scene);show(scene);
    }
    private static void save(WorldStore store,String owner,BattleTrainingScenario scene){if(store!=null)store.save(owner,scene.exportState());}
    private static void show(BattleTrainingScenario scene){System.out.println(Json.write(scene.observation()));}
}
