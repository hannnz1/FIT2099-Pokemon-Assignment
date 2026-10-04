package game.agent.combat;
import game.actors.pokemon.*;import edu.monash.fit2099.engine.actors.Actor;import game.time.TimePerceptionManager;
/** Explicit native portable species; never grants capture to a species lacking it. */
public final class PokemonSpecies {
 private PokemonSpecies(){}
 public static String of(Actor actor){if(actor instanceof game.agent.growth.GrowthPokemon)return ((game.agent.growth.GrowthPokemon)actor).species;if(actor instanceof Treecko)return "TREECKO";if(actor instanceof Mudkip)return "MUDKIP";if(actor instanceof Torchic)return "TORCHIC";throw new IllegalArgumentException("UNKNOWN_SPECIES");}
 public static boolean portable(Actor actor){return actor instanceof game.agent.growth.GrowthPokemon||actor instanceof Treecko||actor instanceof Mudkip;}
 public static Pokemon create(String species){Pokemon p;if("TREECKO".equals(species))p=new Treecko();else if("MUDKIP".equals(species))p=new Mudkip();else if("TORCHIC".equals(species))p=new Torchic();else throw new IllegalArgumentException("UNKNOWN_SPECIES");TimePerceptionManager.getInstance().cleanUp((game.time.TimePerception)p);return p;}
}
