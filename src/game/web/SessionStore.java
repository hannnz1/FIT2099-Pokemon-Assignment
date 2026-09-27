package game.web;

import game.runtime.*;
import game.web.dto.*;
import java.util.*;
import java.util.function.*;

/** Bounded in-memory single-instance store. The lock serializes lifecycle with execution. */
public final class SessionStore implements AutoCloseable {
    public static final long TTL=30*60*1000;
    private final Supplier<GameSession> factory;private final LongSupplier clock;
    private final int capacity,actionLimit,rate;
    private final Map<String,Entry> sessions=new HashMap<>();
    private final LinkedHashMap<String,String> expired=new LinkedHashMap<>();
    private static final class Entry {
        final GameSession game;long touched,window;int requests;
        final Map<String,Receipt> receipts=new HashMap<>();
        Entry(GameSession game,long now){this.game=game;touched=now;window=now;}
    }
    private static final class Receipt { final int expected,applied;final String action;Receipt(int expected,String action,int applied){this.expected=expected;this.action=action;this.applied=applied;} }
    public static final class Result {
        public String requestId,outcome="APPLIED";public int appliedRevision;public boolean replayed;public SnapshotDto snapshot;public List<EventDto> events=Collections.emptyList();
    }
    public SessionStore(Supplier<GameSession> factory){this(factory,System::currentTimeMillis,100,5000,5);}
    public SessionStore(Supplier<GameSession> factory,LongSupplier clock,int capacity,int actionLimit,int rate){this.factory=factory;this.clock=clock;this.capacity=capacity;this.actionLimit=actionLimit;this.rate=rate;}
    public synchronized void reap() {
        long now=clock.getAsLong();Iterator<Map.Entry<String,Entry>> it=sessions.entrySet().iterator();
        while(it.hasNext()){Map.Entry<String,Entry> e=it.next();if(now-e.getValue().touched>=TTL){expired.put(e.getValue().game.gameId,e.getKey());it.remove();}}
        while(expired.size()>capacity*2)expired.remove(expired.keySet().iterator().next());
    }
    public synchronized GameSession create(String owner) {
        reap();Entry old=sessions.get(owner);if(old!=null){old.touched=clock.getAsLong();return old.game;}
        if(sessions.size()>=capacity)throw new ApiException(503,"CAPACITY","演示服务器已满，请稍后再试");
        Entry e=new Entry(factory.get(),clock.getAsLong());sessions.put(owner,e);return e.game;
    }
    private Entry entry(String owner,String id) {
        reap();Entry e=sessions.get(owner);
        if(e==null || id!=null&&!e.game.gameId.equals(id)){
            if(id!=null&&owner.equals(expired.get(id)))throw new ApiException(410,"EXPIRED","本局已过期，请重新开始");
            throw new ApiException(404,"NOT_FOUND","本局不可继续，请重新开始");
        }
        e.touched=clock.getAsLong();return e;
    }
    public synchronized GameSession get(String owner,String id){return entry(owner,id).game;}
    public synchronized void delete(String owner,String id){Entry e=sessions.get(owner);if(e!=null&&e.game.gameId.equals(id)){e.game.end();sessions.remove(owner);}}
    public synchronized Result action(String owner,String id,String requestId,int expected,String actionId) {
        if(requestId==null||!requestId.matches("[A-Za-z0-9_-]{1,80}")||actionId==null||actionId.length()>100||expected<0)throw new ApiException(400,"MALFORMED","动作请求格式错误");
        Entry e=entry(owner,id);long now=clock.getAsLong();if(now-e.window>=1000){e.window=now;e.requests=0;}
        if(++e.requests>rate)throw new ApiException(429,"RATE_LIMIT","操作过快，请稍后重试");
        Receipt saved=e.receipts.get(requestId);Result result=new Result();result.requestId=requestId;
        if(saved!=null){if(saved.expected!=expected||!saved.action.equals(actionId))throw new ApiException(409,"REQUEST_COLLISION","同一请求编号不能更改动作");result.replayed=true;result.appliedRevision=saved.applied;result.snapshot=e.game.snapshot();return result;}
        if(e.game.turn()!=expected)throw new ApiException(409,"REVISION_CONFLICT","游戏已更新，请刷新状态");
        if(e.game.ended())throw new ApiException(422,"ENDED","本局已结束，请重新开始");
        if(e.game.turn()>=actionLimit)throw new ApiException(429,"ACTION_LIMIT","本局已达到动作上限，请重新开始");
        ActionCatalog.Entry selected=null;for(ActionCatalog.Entry a:ActionCatalog.entries(e.game))if(a.dto.id.equals(actionId)){selected=a;break;}
        if(selected==null)throw new ApiException(422,"INVALID_ACTION","该动作当前不可执行");
        try {result.events=e.game.execute(selected.action);}catch(RuntimeException ex){e.game.end();throw ex;}
        result.appliedRevision=e.game.turn();e.receipts.put(requestId,new Receipt(expected,actionId,result.appliedRevision));result.snapshot=e.game.snapshot();return result;
    }
    public synchronized int size(){reap();return sessions.size();}
    public synchronized void close(){sessions.clear();expired.clear();}
}
