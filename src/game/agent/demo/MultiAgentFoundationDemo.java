package game.agent.demo;

import game.agent.multi.MultiAgentFoundation;
import game.agent.multi.MultiAgentFoundation.Role;
import game.agent.persistence.*;
import game.agent.llm.Json;
import java.nio.file.Paths;
import java.util.*;

/** Runnable V3 infrastructure smoke demo. No model calls and no main-map NPC control. */
public final class MultiAgentFoundationDemo {
    public static void main(String[] args){
        if(args.length!=1)throw new IllegalArgumentException("Usage: MultiAgentFoundationDemo <isolated-save-directory>");
        MultiAgentFoundation runtime=new MultiAgentFoundation("v3-foundation-demo",2);
        List<String> order=new ArrayList<>();
        for(int i=0;i<4;i++){
            Map<String,Object> lease=runtime.next(0,0);order.add((String)lease.get("agentId"));
            if(!"AUTHORIZED".equals(runtime.finish((String)lease.get("leaseId"),"observe",0)))throw new IllegalStateException();
        }
        runtime.send("torchic-professor-1",Role.TORCHIC,Role.PROFESSOR,"BERRY",
            "Prototype observation: a berry was present at turn 0.","QUEST_AREA",31,10,0,1,3);
        Map<String,Object> checkpoint=runtime.checkpoint();
        try(WorldStore store=new FileWorldStore(Paths.get(args[0]))){
            store.save("v3-foundation-demo",checkpoint);
            runtime=MultiAgentFoundation.restore(store.load("v3-foundation-demo"));
        }
        if(!Json.write(checkpoint).equals(Json.write(runtime.checkpoint())))throw new IllegalStateException("RESTORE_MISMATCH");
        System.out.println(Json.write(Json.object("status","PASSED","milestone","V3_STARTED_FOUNDATION_ONLY",
            "realAi",false,"mainMapNpcControl",false,"dispatchOrder",order,"professorInbox",runtime.inbox(Role.PROFESSOR,4),
            "merchantInbox",runtime.inbox(Role.MERCHANT,4),"checkpointRestored",true)));
    }
}
