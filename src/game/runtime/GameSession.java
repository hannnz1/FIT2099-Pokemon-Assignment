package game.runtime;

import edu.monash.fit2099.engine.actions.*;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.items.Item;
import edu.monash.fit2099.engine.displays.Display;
import edu.monash.fit2099.engine.positions.*;
import game.time.TimePerception;
import game.web.dto.*;
import java.util.*;

/** One synchronous, non-blocking turn API for both console and HTTP adapters. */
public final class GameSession extends World {
    public final String gameId=UUID.randomUUID().toString();
    private final GameMap map;
    private int turn;
    private boolean ended;
    private Action continuation;
    private final Deque<SnapshotDto.LogEntry> log=new ArrayDeque<>();
    private long logSequence;
    public String mapVersion="test";
    public int tileSize=32;
    public GameSession(GameMap map, Actor player,int x,int y) {
        super(new Display());this.map=map;addGameMap(map);addPlayer(player,map.at(x,y));context().id(player);
    }
    public GameMap map() { return map; }
    public Actor player() { return player; }
    public GameContext context() { return map.context(); }
    public int turn() { return turn; }
    public String period() { return periodAt(Math.max(1,turn)); }
    public static String periodAt(int t) { return ((t-1)/5)%2==0?"DAY":"NIGHT"; }
    public boolean ended() { return ended; }
    public void end() { ended=true; }
    public List<SnapshotDto.LogEntry> logs() { return new ArrayList<>(log); }
    public Action continuation() { return continuation; }
    public void waitTurn() { execute(new DoNothingAction()); }
    public List<Actor> activeActors() {
        List<Actor> result=new ArrayList<>(); for(Actor a:actorLocations) if(map.contains(a)) result.add(a);
        result.sort(Comparator.comparingLong(a->context().order(a)));return result;
    }
    public void appendLog(String text) {
        log.addLast(new SnapshotDto.LogEntry("l"+(++logSequence),turn+1,text));
        while(log.size()>200) log.removeFirst();
    }
    private void cleanup() {
        for(Actor a:activeActors()) if(!a.isConscious()) {
            Location location=map.locationOf(a);
            for(Item item:new ArrayList<>(a.getInventory())) { a.removeItemFromInventory(item); if(item.getDropAction(a)!=null) location.addItem(item); }
            map.removeActor(a);lastActionMap.remove(a);context().affection.forget(a);
            context().event("REMOVE",a,null,"失去战斗能力");
            if(a==player) ended=true;
        }
    }
    public synchronized List<EventDto> execute(Action action) {
        if(ended) throw new IllegalStateException("Game ended");
        if(continuation!=null && action!=continuation) throw new IllegalArgumentException("Continuation required");
        context().events.clear();
        SnapshotDto before=snapshot();
        try {
            cleanup(); List<Actor> initial=activeActors();
            if(!ended) { appendLog(action.execute(player,map)); continuation=action.getNextAction(); }
            cleanup();
            for(Actor a:initial) if(a!=player && map.contains(a) && a.isConscious()) {
                Action last=lastActionMap.get(a);
                Action next=last==null?null:last.getNextAction();
                if(next==null) next=a.playTurn(AvailableActionCollector.collect(a,map),last,map,display);
                if(next!=null) { appendLog(next.execute(a,map)); lastActionMap.put(a,next); }
                cleanup();
            }
            map.tick(); cleanup();
            List<Actor> actors=activeActors();
            List<Location> locations=new ArrayList<>(); List<Ground> grounds=new ArrayList<>();
            for(int y:map.getYRange()) for(int x:map.getXRange()) { Location l=map.at(x,y);locations.add(l);grounds.add(l.getGround()); }
            boolean day=periodAt(turn+1).equals("DAY");
            for(Actor a:actors) if(map.contains(a) && a.isConscious() && a instanceof TimePerception) { effect((TimePerception)a,day); cleanup(); }
            for(int i=0;i<grounds.size();i++) if(locations.get(i).getGround()==grounds.get(i) && grounds.get(i) instanceof TimePerception) effect((TimePerception)grounds.get(i),day);
            cleanup();turn++;
            SnapshotMapper.diff(before,snapshot(),context().events);
            return new ArrayList<>(context().events);
        } catch(RuntimeException error) { ended=true;throw error; }
    }
    private static void effect(TimePerception p,boolean day) { if(day)p.dayEffect();else p.nightEffect(); }
    public synchronized SnapshotDto snapshot() { return SnapshotMapper.map(this); }
}
