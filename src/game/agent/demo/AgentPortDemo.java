package game.agent.demo;

import edu.monash.fit2099.engine.displays.Display;
import edu.monash.fit2099.engine.actions.*;
import edu.monash.fit2099.engine.items.Item;
import edu.monash.fit2099.engine.positions.*;
import game.actors.pokemon.Treecko;
import game.environments.Dirt;
import game.environments.structures.Wall;
import game.agent.action.ActionResult;
import game.agent.memory.*;
import game.agent.runtime.*;
import game.agent.tools.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.*;

/** Executable deterministic module example. No LLM, API key or full berry quest. */
public final class AgentPortDemo {
    private AgentPortDemo() { }
    public static void main(String[] args) throws Exception {
        GameMap map = new GameMap(new FancyGroundFactory(new Dirt(),new Wall()),
                Arrays.asList(".....", ".###.", ".#...", ".###.", "....."));
        new World(new Display()).addGameMap(map);
        AgentMudkip mudkip = new AgentMudkip(); Treecko treecko = new Treecko();
        map.addActor(mudkip,map.at(0,2)); map.addActor(treecko,map.at(4,2));
        Location orchard = map.at(2,2);
        Item berry = new Item("Berry",'b',true) { };
        orchard.addItem(berry);
        MemoryService memory = new MemoryService();
        // The trusted engine produces the event from a real item, not from model text.
        if (orchard.getItems().contains(berry)) {
            NpcMemory seen = new NpcMemory("berry-spawn-1","demo","treecko","ITEM_SEEN","BERRY",
                    "Saw a berry in the orchard","chapter1",orchard.x(),orchard.y(),0,NpcMemory.Source.SELF_OBSERVATION,null);
            Location observer = map.locationOf(treecko);
            memory.perceive("demo","treecko","chapter1",observer.x(),observer.y(),0,2,5,Collections.singletonList(seen));
        }
        System.out.println("Scripted Java module demo (no LLM)");
        System.out.println("Treecko memories: " + memory.recall("demo","treecko","BERRY",0,5).size());
        System.out.println("Mudkip private memories: " + memory.recall("demo","mudkip","BERRY",0,5).size());
        Path saveDirectory=Files.createTempDirectory("pokemon-agent-memory-");
        Path save=saveDirectory.resolve("memory.bin");
        try {
            MemoryStore.save(save,memory);
            MemoryService loaded=MemoryStore.load(save);
            if(loaded.recall("demo","treecko","BERRY",0,5).size()!=1 || !loaded.recall("demo","mudkip","BERRY",0,5).isEmpty())
                throw new IllegalStateException("Memory round trip lost isolation");
            System.out.println("Memory save/load: PASS");
        } finally { Files.deleteIfExists(save); Files.deleteIfExists(saveDirectory); }
        orchard.removeItem(berry); // a real world change makes the historical observation stale
        AgentTask task = new AgentTask("demo-task"); task.start();
        GameToolRegistry registry = new GameToolRegistry(request -> null);
        GameTools.register(registry,mudkip,map,Collections.singletonMap("orchard",orchard),p->true,
                () -> task.getState() == AgentTask.State.RUNNING || task.getState() == AgentTask.State.REPLANNING);
        MemoryTools.register(registry,memory,"demo","mudkip",()->0L);
        AtomicInteger decisions=new AtomicInteger(),clock=new AtomicInteger();
        AgentLoop loop=new AgentLoop(task,"Reach orchard",registry,Runnable::run,context->{
            decisions.incrementAndGet();
            return new ToolRequest("script-choice","move_to",Collections.singletonMap("locationId","orchard"));
        },()->Collections.singletonMap("location",map.locationOf(mudkip).toString()),
            ()->map.locationOf(mudkip)==orchard,100,5,12,3);
        mudkip.attach(loop,map,()->clock.getAndIncrement());
        for(int turn=0;turn<20 && task.getState()!=AgentTask.State.COMPLETED;turn++) {
            Action action=mudkip.playTurn(new ActionList(),null,map,new Display());
            System.out.println("Turn "+turn+": "+action.execute(mudkip,map));
        }
        if(task.getState()!=AgentTask.State.COMPLETED || map.locationOf(mudkip)!=orchard || decisions.get()!=1)
            throw new IllegalStateException("Engine loop did not finish in one decision");
        System.out.println("Automatic loop: completed reach-orchard goal with "+decisions.get()+" decision");
        ActionResult observation=registry.execute(new ToolRequest("observe-arrival","observe",Collections.emptyMap()));
        System.out.println("Current orchard: " + observation);
        if (!observation.getData().get("items").isEmpty()) throw new IllegalStateException("Stale memory changed world");
        Queue<Runnable> modelJobs=new ArrayDeque<>(),worldJobs=new ArrayDeque<>();
        AgentRunner runner=new AgentRunner(modelJobs::add,worldJobs::add,()->30L);
        AgentTask cancelled=new AgentTask("cancel-demo"); cancelled.start();
        CompletableFuture<ActionResult> late=runner.step(cancelled,()->new ToolRequest("late","observe",Collections.emptyMap()),registry,100);
        cancelled.cancel(); modelJobs.remove().run(); worldJobs.remove().run();
        System.out.println("Cancelled task's late decision: " + late.join());
        if (!"STALE_OPERATION".equals(late.join().getCode())) throw new IllegalStateException("Late decision accepted");
        System.out.println("PASS: engine turns, bounded automatic loop, navigation, memory save/load, isolation, stale observation and cancellation");
    }
}
