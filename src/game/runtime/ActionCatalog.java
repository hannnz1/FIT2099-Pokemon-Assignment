package game.runtime;
import edu.monash.fit2099.engine.actions.*;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.items.*;
import game.actions.*;
import game.web.dto.SnapshotDto.ActionDto;
import java.util.*;

/** Ephemeral capabilities: IDs include revision and are regenerated from legal Java actions. */
public final class ActionCatalog {
    public static final class Entry { public final Action action;public final ActionDto dto;Entry(Action a,ActionDto d){action=a;dto=d;} }
    public static List<Entry> entries(GameSession s) {
        List<Entry> result=new ArrayList<>();if(s.ended())return result;
        if(s.continuation()!=null) { ActionDto d=new ActionDto();d.id=s.turn()+":continue";d.kind="CONTINUE";d.label="继续当前动作";result.add(new Entry(s.continuation(),d));return result; }
        int index=0;
        for(Action a:AvailableActionCollector.collect(s.player(),s.map())) {
            ActionDto d=new ActionDto();d.id=s.turn()+":"+(index++);Actor target=null;Object object=null;
            if(a instanceof MoveActorAction) {d.kind="MOVE";d.direction=((MoveActorAction)a).getDirection();d.label="移动 "+d.direction;}
            else if(a instanceof DoNothingAction) {d.kind="WAIT";d.label="等待一回合";}
            else if(a instanceof CaptureAction) {d.kind="CAPTURE";d.label="捕捉";target=((CaptureAction)a).getTarget();}
            else if(a instanceof AffectionAction) { target=((AffectionAction)a).getTarget();d.kind=a instanceof SingingAction?"SING":a instanceof DancingAction?"DANCE":"CHEST_POUND";d.label=d.kind.equals("SING")?"唱歌":d.kind.equals("DANCE")?"跳舞":"拍胸脯"; }
            else if(a instanceof TalkAction) {d.kind="TALK";d.label="交谈";target=((TalkAction)a).getTarget();}
            else if(a instanceof TradeAction) {TradeAction t=(TradeAction)a;target=t.getTarget();d.kind="TRADE";d.label="兑换 "+t.getOffer()+" · "+t.getOffer().getCandyCost()+" 糖果";}
            else if(a instanceof AttackAction) {d.kind="ATTACK";d.label="攻击";target=((AttackAction)a).getTarget();}
            else if(a instanceof PickUpItemAction) {d.kind="PICK_UP";object=((PickUpItemAction)a).getItem();d.label="拾取 "+object;}
            else if(a instanceof DropItemAction) {d.kind="DROP";object=((DropItemAction)a).getItem();d.label="丢下 "+object;}
            else throw new IllegalStateException("Unmapped action "+a.getClass());
            if(target!=null) {d.targetId=s.context().id(target);d.label+=" · "+target.getName();}
            else if(object!=null) d.targetId=s.context().id(object);
            result.add(new Entry(a,d));
        }
        return result;
    }
}
