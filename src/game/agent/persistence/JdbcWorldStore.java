package game.agent.persistence;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Map;
/** PostgreSQL repository. One row atomically contains world, memory and trace state. */
public final class JdbcWorldStore implements WorldStore {
    @FunctionalInterface public interface ConnectionFactory { Connection open() throws SQLException; }
    private static final String CREATE="CREATE TABLE IF NOT EXISTS pokemon_agent_world_checkpoints ("
            +"owner_hash VARCHAR(64) PRIMARY KEY, document TEXT NOT NULL CHECK (octet_length(document) <= 1048576), "
            +"updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)";
    private static final String SAVE="INSERT INTO pokemon_agent_world_checkpoints(owner_hash,document) VALUES (?,?) "
            +"ON CONFLICT(owner_hash) DO UPDATE SET document=EXCLUDED.document,updated_at=CURRENT_TIMESTAMP";
    private static final String LOAD="SELECT document FROM pokemon_agent_world_checkpoints WHERE owner_hash=?";
    private final ConnectionFactory factory;
    private boolean closed;
    public JdbcWorldStore(ConnectionFactory factory) {
        if(factory==null) throw new StoreException(StoreException.Code.DATABASE_ERROR);
        this.factory=factory;
        try(Connection connection=factory.open()) {
            connection.setAutoCommit(false);
            try(Statement statement=connection.createStatement()) {
                statement.setQueryTimeout(5); statement.execute(CREATE); connection.commit();
            } catch(SQLException | RuntimeException e) { rollback(connection); throw e; }
        } catch(SQLException | RuntimeException e) { throw new StoreException(StoreException.Code.DATABASE_ERROR); }
    }
    public synchronized void save(String owner,Map<String,Object> checkpoint) {
        ensureOpen(); String key=CheckpointDocument.ownerKey(owner);
        String document=new String(CheckpointDocument.encode(key,checkpoint),StandardCharsets.UTF_8);
        try(Connection connection=factory.open()) {
            connection.setAutoCommit(false);
            try(PreparedStatement statement=connection.prepareStatement(SAVE)) {
                statement.setQueryTimeout(5); statement.setString(1,key); statement.setString(2,document);
                if(statement.executeUpdate()!=1) throw new SQLException(); connection.commit();
            } catch(SQLException | RuntimeException e) { rollback(connection); throw e; }
        } catch(SQLException | RuntimeException e) { throw new StoreException(StoreException.Code.DATABASE_ERROR); }
    }
    public synchronized Map<String,Object> load(String owner) {
        ensureOpen(); String key=CheckpointDocument.ownerKey(owner);
        try(Connection connection=factory.open(); PreparedStatement statement=connection.prepareStatement(LOAD)) {
            statement.setQueryTimeout(5); statement.setString(1,key);
            try(ResultSet result=statement.executeQuery()) {
                if(!result.next()) return null;
                try(Reader reader=result.getCharacterStream(1)) {
                    if(reader==null) throw new StoreException(StoreException.Code.CORRUPT);
                    StringBuilder document=new StringBuilder(); char[] buffer=new char[4096]; int count;
                    while((count=reader.read(buffer))!=-1) {
                        if(document.length()+count>CheckpointDocument.MAX_BYTES) throw new StoreException(StoreException.Code.TOO_LARGE);
                        document.append(buffer,0,count);
                    }
                    return CheckpointDocument.decode(key,document.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch(StoreException e) { throw e; }
        catch(SQLException | IOException | RuntimeException e) { throw new StoreException(StoreException.Code.DATABASE_ERROR); }
    }
    public synchronized void close() { closed=true; }
    private void ensureOpen() { if(closed) throw new StoreException(StoreException.Code.CLOSED); }
    private static void rollback(Connection connection) { try { connection.rollback(); } catch(SQLException ignored) {} }
}
