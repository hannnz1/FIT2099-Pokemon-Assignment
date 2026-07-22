package game.items.balls;


import edu.monash.fit2099.engine.actors.Actor;
import game.actors.pokemon.Pokemon;
import game.conditions.Status;
import game.items.balls.Ball;

//  This portable item is illustrated as o (small o), will be used to catch a pokemon.
//  It can only contain one Pokemon at a time and will always expect to have a Pokemon inside.
//  Once the player captures a Pokemon, it will store the corresponding Pokemon instance in this Pokeball.
//  It can be summoned back to the world at one of the trainer's adjacent squares. There is an unlimited number of Pokeballs in this game,
//  Since there's unlimited Pokeballs, it means a Pokeball can be instantiated when the trainer is capturing it.
public class Pokeball extends Ball {

  private Actor pokemon;

  public Pokeball() {
    super("Pokeball", 'o', true);
    this.addCapability(Status.CAPTURE_POKEMON);
  }


  public Pokeball capturePokemon(Actor pokemon){
    if (pokemon == null) {
      throw new IllegalArgumentException("pokemon cannot be null");
    }
    this.pokemon = pokemon;
    return this;
  }

  public boolean containsPokemon() {
    return pokemon != null;
  }

  public Actor getPokemon() {
    if (pokemon == null) {
      throw new IllegalStateException("Pokeball does not contain a Pokemon");
    }
    return pokemon;
  }


}
