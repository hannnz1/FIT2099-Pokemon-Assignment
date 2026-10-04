package game.agent.demo;

import game.agent.action.ActionResult;
import game.agent.llm.*;
import game.agent.runtime.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Shared real quest runner. Provider selection does not change game or approval rules. */
final class QuestAgentConsole {
    private QuestAgentConsole() { }
    static int run(LlmGateway gateway,TaskInterpreter interpreter,int timeoutMillis,String provider,InputStream input,PrintStream output) {
        GeminiQuestScenario scene=new GeminiQuestScenario("console-player",true);Scanner scanner=new Scanner(input,"UTF-8");
        output.println(provider+" 树果任务：交付 3 个树果。请输入任务，例如：帮我完成树果任务，不要花金币。");
        if(!scanner.hasNextLine())return 0;
        TaskIntent intent;
        try {intent=parseWithDeadline(interpreter,scanner.nextLine(),scene.getSession().getQuestId(),timeoutMillis);}
        catch(ProviderException error){output.println("无法解析任务："+error.getMessage()+"。未启动任务。");return 3;}
        catch(IllegalArgumentException error){output.println("输入不受支持。未启动任务。");return 3;}
        output.println("解析目标：完成当前树果任务；约束："+intent.getConstraints());
        output.println("允许区域："+(intent.getAreaId()==null?"ALL":intent.getAreaId())+"；截止回合："+(intent.getDeadlineTurn()==null?60:intent.getDeadlineTurn())+"。确认这个理解并启动？[y/N]");
        if(!scanner.hasNextLine() || !"y".equalsIgnoreCase(scanner.nextLine().trim())) {output.println("未启动任务。");return 0;}
        ExecutorService executor=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),job->{
            Thread thread=new Thread(job,"quest-model-decision");thread.setDaemon(true);return thread;
        },new ThreadPoolExecutor.AbortPolicy());
        AgentLoop loop=scene.start(intent,gateway,executor,2L*timeoutMillis+5000);long started=System.nanoTime()/1000000L;
        try {
            while(System.nanoTime()/1000000L-started<120000L) {
                ActionResult result=loop.tick(System.nanoTime()/1000000L);
                if(!"DECISION_PENDING".equals(result.getCode()) && !"TASK_INACTIVE".equals(result.getCode()))
                    output.println(result.getStatus()+":"+result.getCode()); // no raw state, approval token or provider body
                if(loop.getState()==AgentTask.State.WAITING_APPROVAL) {
                    Map<String,String> pending=scene.getSession().pendingApproval("console-player");
                    if(!pending.isEmpty()) {
                        output.println("玩家审批："+pending.get("reason")+"。允许？[y=允许 / n=拒绝 / cancel=取消]");
                        if(!scanner.hasNextLine()){loop.cancel();break;}
                        String choice=scanner.nextLine().trim();
                        if("cancel".equalsIgnoreCase(choice)){loop.cancel();break;}
                        ActionResult response=scene.getSession().resolveApproval("console-player",pending.get("proposalId"),"y".equalsIgnoreCase(choice));
                        output.println(response.getCode());
                    }
                } else if(!"DECISION_PENDING".equals(result.getCode()) && !"TASK_INACTIVE".equals(result.getCode())
                    && (result.getStatus()==ActionResult.Status.SUCCESS || result.getStatus()==ActionResult.Status.IN_PROGRESS))scene.advanceTurn();
                if(loop.getState()==AgentTask.State.COMPLETED) {
                    output.println("任务完成：实际交付 "+scene.getDelivered()+" 个树果；剩余金币 "+scene.getSession().getBalance());return 0;
                }
                if(loop.getState()==AgentTask.State.PROVIDER_UNAVAILABLE || loop.getState()==AgentTask.State.FAILED || loop.getState()==AgentTask.State.CANCELLED)break;
                Thread.sleep(50);
            }
            if(loop.getState()==AgentTask.State.RUNNING || loop.getState()==AgentTask.State.REPLANNING)loop.cancel();
            output.println("任务已停止："+loop.getState()+"，世界回合 "+scene.getSession().getTurn()+"，未伪造完成。");return 4;
        } catch(InterruptedException error){Thread.currentThread().interrupt();loop.cancel();return 130;}
        finally {executor.shutdownNow();}
    }
    private static TaskIntent parseWithDeadline(TaskInterpreter interpreter,String text,String questId,int timeoutMillis) {
        ExecutorService parser=Executors.newSingleThreadExecutor(job->{Thread thread=new Thread(job,"quest-task-parser");thread.setDaemon(true);return thread;});
        Future<TaskIntent> future=parser.submit(()->interpreter.parse(text,questId));
        try {return future.get((long)timeoutMillis+1000,TimeUnit.MILLISECONDS);}
        catch(TimeoutException error){throw new ProviderException(ProviderException.Code.TIMEOUT);}
        catch(InterruptedException error){Thread.currentThread().interrupt();throw new ProviderException(ProviderException.Code.UNAVAILABLE);}
        catch(ExecutionException error) {
            if(error.getCause() instanceof ProviderException)throw (ProviderException)error.getCause();
            if(error.getCause() instanceof IllegalArgumentException)throw (IllegalArgumentException)error.getCause();
            throw new ProviderException(ProviderException.Code.UNAVAILABLE);
        } finally {future.cancel(true);parser.shutdownNow();}
    }
}
