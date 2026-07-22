package game.actors.npc;


import edu.monash.fit2099.engine.actions.Action;
import edu.monash.fit2099.engine.actions.ActionList;
import edu.monash.fit2099.engine.actions.DoNothingAction;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.displays.Display;
import edu.monash.fit2099.engine.positions.GameMap;
import game.actions.AttackAction;
import game.actions.TalkAction;
import game.behaviours.Behaviour;
import java.util.HashMap;
import java.util.Map;

//Professor Oak, who you can visit after you have captured one of each pokemon.
//2. Professor Oak👩‍⚕️
//    Professor Oak ($) is another actor, and he stands in the middle of the map(next to the shopkeeper) (inside a house made of Floor and Walls).
//    Once you are done collecting all 3 different pokemon, you can interact with Professor Oak, who will then give you a Pokedex.
//    You don’t have to implement this as an item, it should just print out a text message.
//    So Professor Oak only has 2 possible lines he will say to you:
//    If you have all 3 different types(Elements) of pokemon: “Congratulations Ash! Here is a Pokedex for you.”
//    If you do not have all 3: “I want to see 3 different pokemon Ash! Gotta catch em all!”
//    This should signify the end of assignment 1. Hint: Your system should have some way of keeping track of pokemon caught by the player to make this task simple.
public class ProffesorOak extends NPC {
  //FIXME: Change it to a sorted map (is it TreeMap? HashMap? LinkedHashMap?)
  private final Map<Integer, Behaviour> behaviours = new HashMap<>(); // priority, behaviour


  public ProffesorOak() {
    super("Professor Oak", '$', 500000);
  }

  /**
   * @param otherActor the Actor that might perform an action.
   * @param direction  String representing the direction of the other Actor
   * @param map        current GameMap
   * @return list of actions
   */
  @Override
  public ActionList allowableActions(Actor otherActor, String direction, GameMap map) {
    ActionList actions = new ActionList();
    actions.add(new AttackAction(this, direction));
    actions.add(new TalkAction(this, direction));
    return actions;
  }

  @Override
  public void creatIntrinsicWeapon() {

  }

  /**
   * By using behaviour loops, it will decide what will be the next action automatically.
   *
   * @see Actor#playTurn(ActionList, Action, GameMap, Display)
   */
  @Override
  public Action playTurn(ActionList actions, Action lastAction, GameMap map, Display display) {
    for (Behaviour behaviour : behaviours.values()) {
      Action action = behaviour.getAction(this, map);
      if (action != null)
        return action;
    }
    return new DoNothingAction();
  }
}
