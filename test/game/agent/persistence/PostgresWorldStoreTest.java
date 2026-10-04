package game.agent.persistence;

import game.agent.llm.Json;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/** Opt-in integration test. Supply only an isolated disposable PostgreSQL URL. */
class PostgresWorldStoreTest {
    @Test void realPostgresRoundTripIsolationReopenAndRollback() throws Exception {
        String url=System.getProperty("pokemon.test.jdbcUrl");
        assumeTrue(url!=null,"No isolated PostgreSQL URL supplied");
        String user=System.getProperty("pokemon.test.jdbcUser","pokemon_v1");
        Class.forName("org.postgresql.Driver");
        JdbcWorldStore.ConnectionFactory connections=()->DriverManager.getConnection(url,user,"");
        String owner="integration-"+UUID.randomUUID();
        try(WorldStore store=new JdbcWorldStore(connections)) {
            store.save(owner,Json.object("worldState",Json.object("turn",7),"memories",Arrays.asList("saved-memory"),"trace",Arrays.asList("saved-step")));
            assertNull(store.load(owner+"-other"));
        }
        try(WorldStore store=new JdbcWorldStore(connections)) {
            String restored=Json.write(store.load(owner));
            assertTrue(restored.contains("saved-memory")); assertTrue(restored.contains("saved-step"));
        }
        boolean[] failCommit={false};
        JdbcWorldStore.ConnectionFactory failing=()-> {
            Connection real=connections.open();
            return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)-> {
                if(m.getName().equals("commit") && failCommit[0]) throw new SQLException("injected commit failure");
                try { return m.invoke(real,a); } catch(InvocationTargetException e) { throw e.getCause(); }
            });
        };
        try(WorldStore store=new JdbcWorldStore(failing)) {
            failCommit[0]=true;
            assertEquals(StoreException.Code.DATABASE_ERROR,assertThrows(StoreException.class,()->store.save(owner,Json.object("memories",Arrays.asList("bad-memory"),"trace",Arrays.asList("bad-step")))).getCode());
        }
        try(WorldStore store=new JdbcWorldStore(connections)) {
            String restored=Json.write(store.load(owner));
            assertTrue(restored.contains("saved-memory")); assertTrue(restored.contains("saved-step")); assertFalse(restored.contains("bad-"));
        }
    }
}
