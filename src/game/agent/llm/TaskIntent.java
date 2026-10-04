package game.agent.llm;

import java.util.*;

/** Validated interpretation, not an executable free-form rule or a quest definition. */
public final class TaskIntent {
    public enum Constraint { NO_SPENDING, NO_ACTIVE_BATTLE, AREA_RESTRICTED, DEADLINE }
    private final String questId;
    private final String kind,targetId;
    private final List<TaskIntent> steps;
    private final Set<Constraint> constraints;
    private final String areaId;
    private final Long deadlineTurn;
    TaskIntent(String questId,Set<Constraint> constraints) {
        this(questId,constraints,null,null);
    }
    private TaskIntent(String questId,Set<Constraint> constraints,String areaId,Long deadlineTurn) {
        this(questId,constraints,areaId,deadlineTurn,"COMPLETE_QUEST",null);
    }
    private TaskIntent(String questId,Set<Constraint> constraints,String areaId,Long deadlineTurn,String kind,String targetId) {
        this(questId,constraints,areaId,deadlineTurn,kind,targetId,null);
    }
    private TaskIntent(String questId,Set<Constraint> constraints,String areaId,Long deadlineTurn,String kind,String targetId,List<TaskIntent> steps) {
        this.kind=kind;this.targetId=targetId;this.questId=questId;this.steps=steps;
        this.constraints=Collections.unmodifiableSet(constraints.isEmpty()?EnumSet.noneOf(Constraint.class):EnumSet.copyOf(constraints));
        this.areaId=areaId; this.deadlineTurn=deadlineTurn;
    }
    /** Validated data factory for trusted checkpoint restoration and decoded intents. */
    public static TaskIntent of(String questId,Set<Constraint> constraints,String areaId,Long deadlineTurn) {
        if(questId==null || questId.trim().isEmpty() || constraints==null || constraints.contains(null)) throw new IllegalArgumentException("INVALID_TASK_INTENT");
        if(constraints.contains(Constraint.AREA_RESTRICTED)!=(areaId!=null) || constraints.contains(Constraint.DEADLINE)!=(deadlineTurn!=null)) throw new IllegalArgumentException("INVALID_CONSTRAINT_PARAMS");
        if(areaId!=null && !"QUEST_AREA".equals(areaId) && !"ORCHARD_AREA".equals(areaId)) throw new IllegalArgumentException("UNKNOWN_AREA");
        if(deadlineTurn!=null && (deadlineTurn<=0 || deadlineTurn>60)) throw new IllegalArgumentException("INVALID_DEADLINE");
        return new TaskIntent(questId,constraints,areaId,deadlineTurn);
    }
    public static TaskIntent ofGoal(String questId,String kind,String targetId,Set<Constraint> constraints,String areaId,Long deadlineTurn) {
        TaskIntent checked=of(questId,constraints,areaId,deadlineTurn);
        if("COMPLETE_QUEST".equals(kind)){if(targetId!=null)throw new IllegalArgumentException("INVALID_TASK_TARGET");return checked;}
        if(!Arrays.asList("CAPTURE","DEFEAT").contains(kind)||!Arrays.asList("field-treecko","field-mudkip","field-torchic").contains(targetId))throw new IllegalArgumentException("UNSUPPORTED_GOAL");
        if("CAPTURE".equals(kind)&&"field-torchic".equals(targetId))throw new IllegalArgumentException("NOT_CAPTURABLE");
        if("DEFEAT".equals(kind)&&constraints.contains(Constraint.NO_ACTIVE_BATTLE))throw new IllegalArgumentException("TASK_CONSTRAINT_CONFLICT");
        return new TaskIntent(questId,constraints,areaId,deadlineTurn,kind,targetId);
    }
    public static TaskIntent ofSequence(String questId,List<Map<String,Object>> rows,Set<Constraint> constraints,String areaId,Long deadlineTurn){
        return sequence(questId,rows,constraints,areaId,deadlineTurn,false);
    }
    public static TaskIntent ofMixed(String questId,List<Map<String,Object>> rows,Set<Constraint> constraints,String areaId,Long deadlineTurn){
        return sequence(questId,rows,constraints,areaId,deadlineTurn,true);
    }
    private static TaskIntent sequence(String questId,List<Map<String,Object>> rows,Set<Constraint> constraints,String areaId,Long deadlineTurn,boolean mixed){
        of(questId,constraints,areaId,deadlineTurn);
        if(rows==null||rows.size()<2||rows.size()>3)throw new IllegalArgumentException("INVALID_SEQUENCE");
        List<TaskIntent> goals=new ArrayList<>();Set<String> targets=new HashSet<>();int berries=0;
        for(Map<String,Object> row:rows){
            if(row==null||!row.keySet().equals(new HashSet<>(Arrays.asList("kind","targetId")))||!(row.get("kind") instanceof String)||row.get("targetId")!=null&&!(row.get("targetId") instanceof String))throw new IllegalArgumentException("INVALID_SEQUENCE");
            TaskIntent goal=ofGoal(questId,(String)row.get("kind"),(String)row.get("targetId"),constraints,areaId,deadlineTurn);
            if(!goal.isField()){if(!mixed||++berries>1)throw new IllegalArgumentException("INVALID_SEQUENCE");}
            else if(!targets.add(goal.getTargetId()))throw new IllegalArgumentException("INVALID_SEQUENCE");
            goals.add(goal);
        }
        if(mixed&&(berries!=1||targets.isEmpty()))throw new IllegalArgumentException("INVALID_MIXED_SEQUENCE");
        return new TaskIntent(questId,constraints,areaId,deadlineTurn,mixed?"MIXED":"SEQUENCE",null,Collections.unmodifiableList(goals));
    }
    public boolean isMixed(){return "MIXED".equals(kind);}
    public List<TaskIntent> getSteps(){return steps==null?Collections.singletonList(this):steps;}
    public List<Map<String,Object>> stepDefinitions(){List<Map<String,Object>> rows=new ArrayList<>();for(TaskIntent step:getSteps())rows.add(Json.object("kind",step.getKind(),"targetId",step.getTargetId()));return rows;}
    public String getKind(){return kind;}
    public String getTargetId(){return targetId;}
    public boolean isField(){return !"COMPLETE_QUEST".equals(kind);}
    public String getQuestId() { return questId; }
    public Set<Constraint> getConstraints() { return constraints; }
    public boolean has(Constraint constraint) { return constraints.contains(constraint); }
    public String getAreaId() { return areaId; }
    public Long getDeadlineTurn() { return deadlineTurn; }
    public String getGoal() { return kind+"("+(steps!=null?Json.write(stepDefinitions()):isField()?targetId:questId)+") constraints="+constraints+(areaId==null?"":" areaId="+areaId)+(deadlineTurn==null?"":" deadlineTurn="+deadlineTurn); }
}
