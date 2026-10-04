package game.agent.persistence;

import game.agent.llm.Json;
import java.io.StringReader;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcWorldStoreTest {
    @Test void committedNestedDocumentSurvivesReopenAndIsOwnerScoped() {
        Database db=new Database();
        try(WorldStore store=new JdbcWorldStore(db::open)) {
            store.save("owner' OR '1'='1",Json.object("memories",Arrays.asList(Json.object("text","saved")),"trace",Arrays.asList("step")));
            assertNull(store.load("different"));
        }
        try(WorldStore store=new JdbcWorldStore(db::open)) {
            assertTrue(Json.write(store.load("owner' OR '1'='1")).contains("saved"));
        }
    }
    @Test void failedCommitRollsBackWholeWorldIncludingMemoriesAndTrace() {
        Database db=new Database();
        try(WorldStore store=new JdbcWorldStore(db::open)) {
            store.save("one",Json.object("world","old","memories",Arrays.asList("old-memory"),"trace",Arrays.asList("old-step")));
            db.failCommit=true;
            StoreException error=assertThrows(StoreException.class,()->store.save("one",Json.object("world","new","memories",Arrays.asList("new-memory"))));
            assertEquals(StoreException.Code.DATABASE_ERROR,error.getCode());
            assertFalse(error.toString().contains("database-password"));
            db.failCommit=false;
            String saved=Json.write(store.load("one"));
            assertTrue(saved.contains("old-memory")); assertTrue(saved.contains("old-step")); assertFalse(saved.contains("new"));
            assertEquals(1,db.rollbacks);
        }
    }
    @Test void statementFailureAndInvalidDocumentCannotOverwriteCheckpoint() {
        Database db=new Database();
        try(WorldStore store=new JdbcWorldStore(db::open)) {
            store.save("one",Json.object("state","before")); db.failStatement=true;
            assertThrows(StoreException.class,()->store.save("one",Json.object("state","after")));
            db.failStatement=false;
            assertEquals("before",store.load("one").get("state"));
            assertThrows(StoreException.class,()->store.save("one",Json.object("password","private")));
            assertEquals("before",store.load("one").get("state"));
        }
    }
    @Test void corruptedStoredDocumentFailsClosed() {
        Database db=new Database();
        try(WorldStore store=new JdbcWorldStore(db::open)) {
            store.save("one",Json.object("state","before"));
            String key=db.rows.keySet().iterator().next(); db.rows.put(key,"{}");
            assertEquals(StoreException.Code.CORRUPT,assertThrows(StoreException.class,()->store.load("one")).getCode());
        }
    }
    /** External JDBC boundary double: stores only parameterized statements and transaction commits. */
    static final class Database {
        final Map<String,String> rows=new HashMap<>(); boolean failCommit,failStatement; int rollbacks;
        Connection open() {
            Map<String,String> pending=new HashMap<>(); boolean[] auto={true};
            return proxy(Connection.class,(p,m,a)->{
                switch(m.getName()) {
                    case "setAutoCommit": auto[0]=(Boolean)a[0]; return null;
                    case "getAutoCommit": return auto[0];
                    case "createStatement": return proxy(Statement.class,(sp,sm,sa)-> {
                        if(sm.getName().equals("execute")) { assertTrue(((String)sa[0]).contains("pokemon_agent_world_checkpoints")); return false; }
                        return defaultValue(sm.getReturnType());
                    });
                    case "prepareStatement": {
                        String sql=(String)a[0]; assertTrue(sql.contains("pokemon_agent_world_checkpoints"));
                        assertTrue(sql.contains("?")); Map<Integer,String> args=new HashMap<>();
                        return proxy(PreparedStatement.class,(sp,sm,sa)-> {
                            switch(sm.getName()) {
                                case "setString": args.put((Integer)sa[0],(String)sa[1]); return null;
                                case "executeUpdate":
                                    if(failStatement) throw new SQLException("database-password");
                                    assertFalse(auto[0]); assertNotNull(args.get(1)); assertNotNull(args.get(2));
                                    pending.put(args.get(1),args.get(2)); return 1;
                                case "executeQuery": {
                                    String document=rows.get(args.get(1)); boolean[] first={true};
                                    return proxy(ResultSet.class,(rp,rm,ra)-> {
                                        if(rm.getName().equals("next")) { boolean present=first[0] && document!=null; first[0]=false; return present; }
                                        if(rm.getName().equals("getCharacterStream")) return new StringReader(document);
                                        if(rm.getName().equals("getString")) return document;
                                        return defaultValue(rm.getReturnType());
                                    });
                                }
                                default: return defaultValue(sm.getReturnType());
                            }
                        });
                    }
                    case "commit": if(failCommit) throw new SQLException("database-password"); rows.putAll(pending); pending.clear(); return null;
                    case "rollback": rollbacks++; pending.clear(); return null;
                    case "close": pending.clear(); return null;
                    default: return defaultValue(m.getReturnType());
                }
            });
        }
        static Object defaultValue(Class<?> type) {
            if(type==boolean.class) return false; if(type==int.class) return 0; if(type==long.class) return 0L; return null;
        }
        static <T> T proxy(Class<T> type,InvocationHandler handler) { return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler)); }
    }
}
