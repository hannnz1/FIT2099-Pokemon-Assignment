package game.agent.persistence;
import java.util.*;
import java.nio.file.Paths;
import java.sql.*;

/** Server-only configuration. Explicit database failures never silently fall back. */
public final class WorldStoreFactory {
    private WorldStoreFactory() { }
    public static WorldStore fromEnvironment(Map<String,String> env){
        String mode=env.getOrDefault("AGENT_STORAGE",env.containsKey("AGENT_DB_URL")?"postgres":"file");
        if("memory".equals(mode))return null;
        if("file".equals(mode))return new FileWorldStore(Paths.get(env.getOrDefault("AGENT_SAVE_DIR","data/agent-saves")));
        if(!"postgres".equals(mode))throw new IllegalArgumentException("INVALID_STORAGE_MODE");
        final String url=env.get("AGENT_DB_URL");if(url==null||!url.startsWith("jdbc:postgresql:"))throw new IllegalArgumentException("INVALID_DATABASE_CONFIGURATION");
        final Properties properties=new Properties();properties.setProperty("user",env.getOrDefault("AGENT_DB_USER","postgres"));
        properties.setProperty("password",env.getOrDefault("AGENT_DB_PASSWORD",""));properties.setProperty("connectTimeout","3");properties.setProperty("socketTimeout","5");
        try{Class.forName("org.postgresql.Driver");}catch(ClassNotFoundException failure){throw new IllegalArgumentException("POSTGRES_DRIVER_REQUIRED");}
        return new JdbcWorldStore(()->DriverManager.getConnection(url,properties));
    }
}
