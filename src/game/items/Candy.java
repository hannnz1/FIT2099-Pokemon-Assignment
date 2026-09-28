package game.items;

import edu.monash.fit2099.engine.items.Item;


//  Candy is randomly placed by WorldPopulation, with at most two on the map. The trainer
//  can pick it up from the ground or drop it. This candy can be traded with the Shopkeeper % for objects,
//  such as Torchic (inside a Pokeball) or other types of Pokeballs. (see REQ6).
//  If you don't work on REQ6, your team only need to design how it will be dropped from a successful capture.
public class Candy extends Tradable {

  public Candy() {
    super("Candy", '*', true);
  }

  @Override public edu.monash.fit2099.engine.items.DropItemAction getDropAction(edu.monash.fit2099.engine.actors.Actor actor) {
    return new game.actions.DropCandyAction(this);
  }

}
