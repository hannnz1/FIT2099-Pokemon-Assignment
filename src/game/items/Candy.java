package game.items;

import edu.monash.fit2099.engine.items.Item;


//  Candy * is an item that will drop whenever a Pokemon is successfully captured (see REQ4). The trainer
//  can pick it up from the ground or drop it. This candy can be traded with the Shopkeeper % for objects,
//  such as Torchic (inside a Pokeball) or other types of Pokeballs. (see REQ6).
//  If you don't work on REQ6, your team only need to design how it will be dropped from a successful capture.
public class Candy extends Tradable {

  public Candy() {
    super("Candy", '*', true);
  }

}
