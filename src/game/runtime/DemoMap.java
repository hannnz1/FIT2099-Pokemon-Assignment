package game.runtime;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.util.*;
import game.actors.pokemon.*;
import game.actors.npc.*;

import edu.monash.fit2099.engine.actors.Actor;

public final class DemoMap {
    public static GameSession create() {
        try(InputStream in=DemoMap.class.getResourceAsStream("/demo-map.json")) {
            if(in==null)throw new IOException("Run node tools/export-map.mjs");
            JsonNode root=new ObjectMapper().readTree(in);List<String> rows=new ArrayList<>();for(JsonNode row:root.get("rows"))rows.add(row.asText());
            JsonNode spawn=null;for(JsonNode e:root.get("entities"))if(e.get("kind").asText().equals("PLAYER"))spawn=e;
            if(spawn==null)throw new IOException("Missing player");
            GameSession s=GameFactory.fromRows(rows.toArray(new String[0]),spawn.get("x").asInt(),spawn.get("y").asInt(),new Random()::nextInt);
            s.mapVersion=root.get("version").asText();s.tileSize=root.get("tileSize").asInt();
            for(JsonNode e:root.get("entities")) {String kind=e.get("kind").asText();int x=e.get("x").asInt(),y=e.get("y").asInt();Actor a=null;
                switch(kind){case "TREECKO":a=new Treecko();break;case "MUDKIP":a=new Mudkip();break;case "TORCHIC":a=new Torchic();break;case "PROFESSOR":a=new ProffesorOak();break;case "MERCHANT":a=new Shopkeeper();break;}
                if(a instanceof Pokemon)WorldPopulation.trySpawn(s.map().at(x,y),(Pokemon)a);
                else if(a!=null)s.map().addActor(a,s.map().at(x,y));
            }
            WorldPopulation.replenishCandy(s);
            return s;
        }catch(IOException e){throw new IllegalStateException("Cannot load demo map",e);}
    }
}
