package game.actions;

import edu.monash.fit2099.engine.actions.Action;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.positions.GameMap;
import game.actors.pokemon.Mudkip;
import game.actors.pokemon.Pokemon;
import game.conditions.AffectionManager;

public class ChestPoundingAction extends Action {

    public Actor targetActor;
    public String result;
    public int pokeAP;

    public ChestPoundingAction(Actor targetActor){
        this.targetActor = targetActor;
}

    // method to let player(ash) execute the specific action to a pokemon actor and raise
    // the corresponding AP between player and actor.
    // first using if loop to check the player's location, if ash can touched the pokemon
    // then using another if loop to check is the targetActor equal the actor pokemon that
    // has the favorite action => yes raise 10 ap otherwise lose 20 ap(notice the ap cannot be negative)
    /**
     * execute the affection action to pokemons
     *
     * @param actor: who will execute the affection action
     *        map: the location of the player
     * @return result: a string short description of the result
     */
    @Override
    public String execute(Actor actor, GameMap map) {
        if(map.isAnActorAt(map.locationOf(actor)) == true){
            if(targetActor instanceof Mudkip){
                // raise the AP
                AffectionManager pokeAM = new AffectionManager();
                pokeAM.registerTrainer(actor);
                pokeAM.registerPokemon(targetActor);
                pokeAM.increaseAffection(targetActor, 10);
                pokeAP = pokeAM.getAffectionPoint(targetActor);
                result = targetActor.toString() + " likes it! +10 affection points.";
            }else{
                AffectionManager pokeAM = new AffectionManager();
                pokeAM.registerTrainer(actor);
                pokeAM.registerPokemon(targetActor);
                pokeAM.decreaseAffection(targetActor, 20);
                pokeAP = pokeAM.getAffectionPoint(targetActor);
                result = targetActor.toString() + " dislikes it! -20 affection points.";
            }
        }
        return result;
    }
    /**
     * print a description string before execute the action
     *
     * @param actor: who will execute the affection action
     * @return str: a string short description
     */
    @Override
    public String menuDescription(Actor actor) {
        String str = "Ash tries Chest Pounding with " + targetActor +"(" + pokeAP + "AP)";
        return str;
    }
}
