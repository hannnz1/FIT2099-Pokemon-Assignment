package game.web;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import game.runtime.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
class SessionStoreTest {
    GameSession game(){return GameFactory.fromRows(new String[]{"#####","#...#","#...#","#...#","#####"},2,2,b->b-1);}
    String waitId(GameSession s){return s.snapshot().availableActions.stream().filter(a->a.kind.equals("WAIT")).findFirst().get().id;}
    @Test void retriesAreIdempotentBeforeRevisionValidation() {
        SessionStore store=new SessionStore(this::game,System::currentTimeMillis,100,5000,100);GameSession s=store.create("a");String id=waitId(s);
        assertFalse(store.action("a",s.gameId,"r1",0,id).replayed);store.action("a",s.gameId,"r2",1,waitId(s));
        SessionStore.Result replay=store.action("a",s.gameId,"r1",0,id);assertTrue(replay.replayed);assertEquals(1,replay.appliedRevision);assertEquals(2,replay.snapshot.revision);assertTrue(replay.events.isEmpty());
        assertEquals(409,assertThrows(ApiException.class,()->store.action("a",s.gameId,"r1",0,"other")).status);
    }
    @Test void ownershipAndExpiryAndCapacity() {
        AtomicLong clock=new AtomicLong();SessionStore store=new SessionStore(this::game,clock::get,1,2,5);GameSession a=store.create("a");assertSame(a,store.create("a"));
        assertEquals(404,assertThrows(ApiException.class,()->store.get("b",a.gameId)).status);assertEquals(503,assertThrows(ApiException.class,()->store.create("b")).status);
        clock.set(1800001);assertEquals(410,assertThrows(ApiException.class,()->store.get("a",a.gameId)).status);assertNotNull(store.create("b"));
    }
    @Test void limitsAndInvalidRequestsDoNotAdvance() {
        SessionStore store=new SessionStore(this::game,()->0L,2,1,5);GameSession s=store.create("a");
        assertEquals(422,assertThrows(ApiException.class,()->store.action("a",s.gameId,"bad",0,"nope")).status);assertEquals(0,s.turn());
        String originalWait=waitId(s);store.action("a",s.gameId,"ok",0,originalWait);assertEquals(429,assertThrows(ApiException.class,()->store.action("a",s.gameId,"cap",1,waitId(s))).status);
        assertTrue(store.action("a",s.gameId,"ok",0,originalWait).replayed);
    }
    @Test void concurrentSameRevisionAcceptsExactlyOne() throws Exception {
        SessionStore store=new SessionStore(this::game,System::currentTimeMillis,2,100,100);GameSession s=store.create("a");String id=waitId(s);ExecutorService pool=Executors.newFixedThreadPool(2);
        try {Callable<Integer> c=()->{try{store.action("a",s.gameId,java.util.UUID.randomUUID().toString(),0,id);return 200;}catch(ApiException e){return e.status;}};java.util.List<Future<Integer>> f=pool.invokeAll(java.util.Arrays.asList(c,c));assertEquals(609,f.get(0).get()+f.get(1).get());assertEquals(1,s.turn());}finally{pool.shutdownNow();}
    }
    @Test void perIdentityRateWindowResetsAndCloseReleasesGames(){AtomicLong time=new AtomicLong();SessionStore store=new SessionStore(this::game,time::get,10,100,5);GameSession s=store.create("a");for(int i=0;i<5;i++)store.action("a",s.gameId,"r"+i,i,waitId(s));assertEquals(429,assertThrows(ApiException.class,()->store.action("a",s.gameId,"r5",5,waitId(s))).status);time.set(1000);store.action("a",s.gameId,"r5",5,waitId(s));assertEquals(6,s.turn());store.close();assertEquals(0,store.size());}
}
