package game.actors.npc;


//the shopkeeper who you will do trading with
//1. Shopkeeper
//    The shopkeeper (%) is another actor, and she stands in the middle of the map (inside a house made of Floor and Walls).
//    The candy * is an item that can be picked up and stored in the inventory straightaway. This item can be dropped at anytime and anywhere in the world. A buyer (i.e., the player) can use these candies to get the following items from the shopkeeper (by trading it):
//    3 candies = 1 Greatball
//    6 candies = 1 Masterball
//    10 candies = 1 Torchic inside a Pokeball (Affection Point: 0)

import edu.monash.fit2099.engine.actions.Action;
import edu.monash.fit2099.engine.actions.ActionList;
import edu.monash.fit2099.engine.actions.DoNothingAction;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.displays.Display;
import edu.monash.fit2099.engine.positions.GameMap;


import game.actions.TradeAction;
import game.items.TradeOffer;

public class Shopkeeper extends NPC {
  public Shopkeeper() {
    super("Shopkeeper", '%', 500000);
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
    for (TradeOffer offer : TradeOffer.values()) {
      actions.add(new TradeAction(this, direction, offer));
    }
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
    return new DoNothingAction();
  }
}
