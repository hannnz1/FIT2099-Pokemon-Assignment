package game.agent.web;

import game.agent.combat.PokemonSpecies;
import game.agent.growth.*;
import game.agent.llm.Json;
import game.agent.persistence.*;
import game.items.balls.Pokeball;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyGrowthMigrationTest {
    static String legacy(WorldStore store,String owner,String species,int hp) {
        String id=UUID.randomUUID().toString();
        game.actors.pokemon.Pokemon p=PokemonSpecies.create(species);p.hurt(100-hp);
        assertEquals("ACCEPTED",new PokemonCollection(owner,store).receive(id,new Pokeball().capturePokemon(p)));
        return id;
    }
    static GrowthRoom open(WorldStore store,String owner){return new GrowthRoom(owner,GrowthCarryTest.offline(),Runnable::run,store,()->0);}
    static Map<String,Object> partner(GrowthRoom room){return Json.asObject(Json.asObject(room.snapshot().get("world")).get("partner"));}
    @Test void oldSchemaRemainsUntouchedUntilExplicitTransferAndThenRoundTrips(@TempDir Path dir) {
        WorldStore store=new FileWorldStore(dir);
        for(String species:Arrays.asList("TREECKO","MUDKIP"))for(int hp:new int[]{1,49,100}){
            String owner=species+hp,id=legacy(store,owner,species,hp);
            Map<String,Object> old=store.load("collection:"+owner);old.remove("training");old.remove("deployment");store.save("collection:"+owner,old);
            GrowthRoom room=open(store,owner);
            assertEquals(old,store.load("collection:"+owner));
            assertEquals(200,GrowthReturnTest.command(room,"RETURN_GROWTH",id).status);
            Map<String,Object> profile=partner(room);
            assertEquals(id,profile.get("captureId"));assertEquals(species,profile.get("species"));
            assertEquals(5,((Number)profile.get("level")).intValue());
            assertEquals(Math.max(1,hp*GrowthRules.stats(species,5)[0]/100),((Number)profile.get("hp")).intValue());
            assertEquals(GrowthRules.experienceAt(5),((Number)profile.get("experience")).intValue());
            assertFalse(Json.asObject(profile.get("pp")).isEmpty());
            assertTrue(new PokemonCollection(owner,store).snapshot().isEmpty());
            room=open(store,owner);assertEquals(profile,partner(room));
            for(int n=0;n<2;n++){
                assertEquals(200,GrowthReturnTest.command(room,"CARRY",null).status);
                assertEquals(1,new PokemonCollection(owner,store).snapshot().size());
                assertEquals(200,GrowthReturnTest.command(room,"RETURN_GROWTH",id).status);
                assertEquals(profile,partner(room));
            }
            assertEquals(1,Json.asArray(Json.asObject(room.snapshot().get("world")).get("team")).size());
        }
    }
    @Test void sourceCommitFailurePreservesOriginalLegacyActorAndSave(@TempDir Path dir){
        FileWorldStore base=new FileWorldStore(dir);String id=legacy(base,"source","MUDKIP",49);
        Map<String,Object> before=base.load("collection:source");boolean[] fail={true};
        WorldStore store=new WorldStore(){public Map<String,Object> load(String o){return base.load(o);}public void save(String o,Map<String,Object> v){if(fail[0]&&o.startsWith("collection:"))throw new StoreException(StoreException.Code.IO_ERROR);base.save(o,v);}public void close(){}};
        PokemonCollection collection=new PokemonCollection("source",store);
        assertEquals("STORAGE_UNAVAILABLE",collection.returnToGrowth(id));
        assertEquals(before,base.load("collection:source"));assertEquals(49,collection.ball(id).getPokemon().getHitPoints());
        assertFalse(collection.ball(id).getPokemon() instanceof GrowthPokemon);assertTrue(collection.trainingProfiles().isEmpty());
        fail[0]=false;collection.recover();assertEquals("ACCEPTED",collection.returnToGrowth(id));
        assertEquals(1,collection.trainingProfiles().size());
    }
    @Test void destinationFailureReconcilesMigratedProfileOnRestart(@TempDir Path dir){
        FileWorldStore base=new FileWorldStore(dir);String id=legacy(base,"dest","TREECKO",49);boolean[] fail={false};
        WorldStore store=new WorldStore(){public Map<String,Object> load(String o){return base.load(o);}public void save(String o,Map<String,Object> v){if(fail[0]&&o.startsWith("growth:"))throw new StoreException(StoreException.Code.IO_ERROR);base.save(o,v);}public void close(){}};
        GrowthRoom room=open(store,"dest");fail[0]=true;
        assertEquals(503,GrowthReturnTest.command(room,"RETURN_GROWTH",id).status);
        fail[0]=false;room=open(store,"dest");assertEquals(id,partner(room).get("captureId"));
        assertEquals(9,((Number)partner(room).get("hp")).intValue());
        assertTrue(new PokemonCollection("dest",store).snapshot().isEmpty());
        assertEquals(200,GrowthReturnTest.command(room,"CARRY",null).status);
        assertEquals(1,new PokemonCollection("dest",store).snapshot().size());
    }
    @Test void postgresLegacyMigrationSurvivesNewRoom()throws Exception{
        String url=System.getProperty("pokemon.test.jdbcUrl");Assumptions.assumeTrue(url!=null);Class.forName("org.postgresql.Driver");
        try(WorldStore store=new JdbcWorldStore(()->java.sql.DriverManager.getConnection(url,System.getProperty("pokemon.test.jdbcUser","pokemon_v1"),""))){
            String owner="legacy-pg-"+UUID.randomUUID(),id=legacy(store,owner,"MUDKIP",49);GrowthRoom room=open(store,owner);
            assertEquals(200,GrowthReturnTest.command(room,"RETURN_GROWTH",id).status);Map<String,Object> profile=partner(room);
            assertEquals(profile,partner(open(store,owner)));assertTrue(new PokemonCollection(owner,store).snapshot().isEmpty());
        }
    }
}
