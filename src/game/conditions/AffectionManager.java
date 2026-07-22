package game.conditions;

import edu.monash.fit2099.engine.actors.Actor;

import game.actors.pokemon.Mudkip;
import game.actors.pokemon.Pokemon;
import game.actors.pokemon.Torchic;
import game.actors.pokemon.Treecko;
import game.environments.spawners.Tree;

import java.util.HashMap;
import java.util.Map;

/**
 * Affection Manager
 * <p>
 * Created by:
 *
 * @author Riordan D. Alfredo
 * Modified by: Ian K. Felix
 */


//All pokemon have different affection for the player. Depending on how the Player treats them, it can increase or decrease affection points. Each Pokemon will start from 0 affection points (AP) to a maximum of 100 AP. It differs from hit points as we use them for a battle/fight between Pokemons. Here are the level points:
//    -50 and below: It dislikes you, and the relationship cannot be fixed. It can only be captured by a Masterball.
//    0: Neutral.
//    20: It is curious about you and is willing to be captured by a Greatball.
//    60: It likes you and is willing to be captured with Pokeball (note: not all pokemon are capturable/catchable). However, it won't follow the trainer/player yet.
//    75: It will follow the trainer/player around.
//    100: The maximum affection level.
//    A Masterball can capture a pokemon(except Torchic) at any affection level and raises the affection level to 100.
public class AffectionManager {

    /**
     * Singleton instance (the one and only for a whole game).
     */
    private static AffectionManager instance;
    /**
     * HINT: is it just for a Torchic?
     */
    private final Map<Actor, Integer> affectionPoints;


    /**
     * We assume there's only one trainer in this manager.
     * Think about how will you extend it.
     */
    private Actor trainer;

    /**
     * private singleton constructor
     */
    public AffectionManager() {
        this.affectionPoints = new HashMap<>();
    }

    /**
     * Access single instance publicly
     *
     * @return this instance
     */
    public static AffectionManager getInstance() {
        if (instance == null) {
            instance = new AffectionManager();
        }
        return instance;
    }

    /**
     * Add a trainer to this class's attribute. Assume there's only one trainer at a time.
     *
     * @param trainer the actor instance
     */
    public void registerTrainer(Actor trainer) {
        this.trainer = trainer;
    }

    /**
     * Add Pokemon to the collection. By default, it has 0 affection point. Ideally, you'll register all instantiated Pokemon
     *
     * @param pokemon
     */
    public void registerPokemon(Actor pokemon) {
        if(affectionPoints.containsKey(pokemon) == false) {
            affectionPoints.put(pokemon, 0);
        }
    }

    /**
     * Get the affection point by using the pokemon instance as the key.
     *
     * @param pokemon Pokemon instance
     * @return integer of affection point.
     */
    public int getAffectionPoint(Actor pokemon) {
        return affectionPoints.get(pokemon);
    }

    /**
     * Useful method to search a pokemon by using Actor instance.
     *
     * @param actor general actor instance
     * @return the Pokemon instance.
     */
    private Actor findPokemon(Actor actor) {
        for (Actor pokemon : affectionPoints.keySet()) {
            if (pokemon.equals(actor)) {
                return pokemon;
            }
        }
        return null;
    }

    /**
     * Increase the affection. Work on both cases when there's a Pokemon,
     * or when it doesn't exist in the collection.
     *
     * @param actor Actor instance, but we expect a Pokemon here.
     * @param point positive affection modifier
     * @return custom message to be printed by Display instance later.
     */
    public String increaseAffection(Actor actor, int point) {
        int newAP = affectionPoints.get(actor) + point;
        return "("+ newAP +"AP)" ;
    }

    /**
     * Decrease the affection level of the . Work on both cases when it is
     *
     * @param actor Actor instance, but we expect a Pokemon here.
     * @param point positive affection modifier (to be subtracted later)
     * @return custom message to be printed by Display instance later.
     */
    public String decreaseAffection(Actor actor, int point) {
        int newAP = affectionPoints.get(actor) - point;
        if(newAP < 0){newAP = 0;}
        return "("+ newAP +"AP)" ;
    }

}
