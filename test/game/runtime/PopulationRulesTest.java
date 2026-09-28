package game.runtime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.positions.*;
import game.actors.npc.*;
import game.actors.pokemon.*;
import game.actions.*;
import game.behaviours.AttackBehaviour;
import game.environments.*;
import game.environments.spawners.*;
import game.items.Candy;
import java.util.*;

class PopulationRulesTest {
    GameSession game(RandomSource r) {
        return GameFactory.fromRows(new String[]{"#########","#.......#","#.......#","#.......#","#.......#","#.......#","#.......#","#.......#","#########"},4,4,r);
    }
    long count(GameSession s,String kind) {return s.snapshot().actors.stream().filter(a->a.kind.equals(kind)).count();}
    long candies(GameSession s) {return s.snapshot().groundItems.stream().filter(i->i.item.kind.equals("CANDY")).count();}
    Set<String> candyPositions(GameSession s) {Set<String> p=new HashSet<>();s.snapshot().groundItems.stream().filter(i->i.item.kind.equals("CANDY")).forEach(i->p.add(i.x+","+i.y));return p;}
    void spawners(GameSession s,int species) {
        for(int y=1;y<8;y++)for(int x=1;x<8;x++)s.map().at(x,y).setGround(species==0?new Hay():species==1?new Puddle():new Dirt());
        for(int x=1;x<8;x++)s.map().at(x,1).setGround(species==0?new Tree():species==1?new Waterfall():new Crater());
    }
    @Test void allSpawnerTypesShareAPerSpeciesCapOfThree() {
        String[] kinds={"TREECKO","MUDKIP","TORCHIC"};
        for(int species=0;species<3;species++){
            GameSession s=game(b->0);spawners(s,species);
            s.map().tick();assertEquals(3,count(s,kinds[species]),kinds[species]);
            s.map().tick();assertEquals(3,count(s,kinds[species]));
            GameSession independent=game(b->0);spawners(independent,species);independent.map().tick();assertEquals(3,count(independent,kinds[species]));
        }
    }
    @Test void initialAndCapturedPokemonAreCountedCorrectly() {
        GameSession s=game(b->0);spawners(s,0);
        Treecko initial=new Treecko();s.map().addActor(initial,s.map().at(1,3));s.map().tick();assertEquals(3,count(s,"TREECKO"));
        new CaptureAction(initial).execute(s.player(),s.map());s.map().tick();assertEquals(3,count(s,"TREECKO"));
        assertEquals(1,s.player().getInventory().size());
        Actor removed=s.activeActors().stream().filter(a->a instanceof Treecko).findFirst().get();s.map().removeActor(removed);
        s.map().tick();assertEquals(3,count(s,"TREECKO"));
    }
    @Test void demoStartsWithOnlyTwoRandomCandiesAndNoFixedPile() {
        GameSession s=DemoMap.create();assertEquals(2,candies(s));assertEquals(2,candyPositions(s).size());
        assertTrue(s.map().locationOf(s.player()).getItems().isEmpty());assertTrue(s.player().getInventory().isEmpty());
    }
    @Test void randomCandySpawningUsesReachableUnoccupiedDistinctTilesAndRefills() {
        GameSession low=game(b->0),high=game(b->b-1);low.waitTurn();high.waitTurn();
        assertEquals(2,candies(low));assertEquals(2,candies(high));assertNotEquals(candyPositions(low),candyPositions(high));
        for(game.web.dto.SnapshotDto.GroundItem item:low.snapshot().groundItems){
            Location l=low.map().at(item.x,item.y);assertFalse(l.containsAnActor());assertTrue(l.getGround().canActorEnter(low.player()));
        }
        game.web.dto.SnapshotDto.GroundItem first=low.snapshot().groundItems.get(0);
        Location location=low.map().at(first.x,first.y);location.removeItem(location.getItems().get(0));
        low.waitTurn();assertEquals(2,candies(low));assertEquals(2,candyPositions(low).size());
        for(int i=0;i<20;i++){low.waitTurn();assertEquals(2,candies(low));}
    }
    @Test void inaccessibleGroundAndFullMapCannotTrapCandyGeneration() {
        GameSession s=GameFactory.fromRows(new String[]{"#######","#.#...#","###...#","#######"},1,1,b->0);
        s.waitTurn();assertEquals(0,candies(s));
    }
    @Test void treesCannotAddCandyBeyondTheGlobalLimit() {
        GameSession s=game(b->0);s.waitTurn();
        for(int x=1;x<8;x++){Tree t=new Tree();Location l=s.map().at(x,1);l.setGround(t);t.tick(l);t.dayEffect();}
        assertEquals(2,candies(s));
    }
    @Test void droppingCandyAtCapacityKeepsInventoryAndDoesNotOfferAnIllegalAction() {
        GameSession s=game(b->0);s.waitTurn();Candy candy=new Candy();s.player().addItemToInventory(candy);
        assertFalse(ActionCatalog.entries(s).stream().anyMatch(e->e.dto.kind.equals("DROP")));
        candy.getDropAction(s.player()).execute(s.player(),s.map());
        assertTrue(s.player().getInventory().contains(candy));assertEquals(2,candies(s));
    }
    @Test void friendlyNpcsOnlyOfferTheirOwnRoleAndWildPokemonCannotAttackThem() {
        for(NPC npc:Arrays.asList(new ProffesorOak(),new Shopkeeper())){
            GameSession s=game(b->0);s.map().addActor(npc,s.map().at(4,3));
            java.util.List<ActionCatalog.Entry> actions=new ArrayList<>();for(ActionCatalog.Entry e:ActionCatalog.entries(s))if(s.context().id(npc).equals(e.dto.targetId))actions.add(e);
            assertEquals(npc instanceof ProffesorOak?1:3,actions.size());assertTrue(actions.stream().allMatch(e->e.dto.kind.equals(npc instanceof ProffesorOak?"TALK":"TRADE")));
            Treecko wild=new Treecko();s.map().addActor(wild,s.map().at(4,2));assertNull(new AttackBehaviour().getAction(wild,s.map()));
        }
    }
    @Test void evolvingDemoStaysWithinAllLimitsForTwoHundredTurns() {
        GameSession s=DemoMap.create();
        for(int turn=0;turn<200;turn++){
            s.waitTurn();
            for(String kind:Arrays.asList("TREECKO","MUDKIP","TORCHIC"))assertTrue(count(s,kind)<=3,kind+" turn "+turn);
            assertTrue(candies(s)<=2);assertEquals(candies(s),candyPositions(s).size());
        }
    }
    @Test void allTradeProductsRemainAvailableAfterCollectingCandy() {
        GameSession s=game(b->0);Shopkeeper merchant=new Shopkeeper();s.map().addActor(merchant,s.map().at(4,3));
        for(int i=0;i<19;i++)s.player().addItemToInventory(new Candy());
        for(game.items.TradeOffer offer:game.items.TradeOffer.values())s.execute(new TradeAction(merchant,"North",offer));
        assertEquals(3,s.player().getInventory().size());
        assertTrue(s.snapshot().inventory.stream().anyMatch(i->i.kind.equals("GREAT_BALL")));
        assertTrue(s.snapshot().inventory.stream().anyMatch(i->i.kind.equals("MASTER_BALL")));
        assertTrue(s.snapshot().inventory.stream().anyMatch(i->i.containedPokemon!=null&&i.containedPokemon.kind.equals("TORCHIC")));
        assertEquals(2,candies(s));
    }
}

