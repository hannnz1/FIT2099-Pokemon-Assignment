package game.actions;

import edu.monash.fit2099.engine.actions.Action;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.items.Item;
import edu.monash.fit2099.engine.positions.GameMap;
import game.items.Candy;
import game.items.TradeOffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Exchanges a fixed number of Candy items for a product from the Shopkeeper. */
public final class TradeAction extends Action {

    private final Actor shopkeeper;
    private final String direction;
    private final TradeOffer offer;

    public TradeAction(Actor shopkeeper, String direction, TradeOffer offer) {
        this.shopkeeper = Objects.requireNonNull(shopkeeper, "shopkeeper cannot be null");
        this.direction = Objects.requireNonNull(direction, "direction cannot be null");
        this.offer = Objects.requireNonNull(offer, "offer cannot be null");
    }

    @Override
    public String execute(Actor actor, GameMap map) {
        List<Item> candies = findCandies(actor);
        if (candies.size() < offer.getCandyCost()) {
            return actor + " needs " + offer.getCandyCost() + " candies to trade for " + offer
                    + " (currently has " + candies.size() + ").";
        }

        for (int index = 0; index < offer.getCandyCost(); index++) {
            actor.removeItemFromInventory(candies.get(index));
        }
        Item product = offer.createProduct();
        actor.addItemToInventory(product);
        return actor + " trades " + offer.getCandyCost() + " candies with " + shopkeeper
                + " for " + offer + ".";
    }

    private List<Item> findCandies(Actor actor) {
        List<Item> candies = new ArrayList<>();
        for (Item item : actor.getInventory()) {
            if (item instanceof Candy) {
                candies.add(item);
            }
        }
        return candies;
    }

    @Override
    public String menuDescription(Actor actor) {
        return actor + " trades " + offer.getCandyCost() + " candies for " + offer
                + " with " + shopkeeper + " at " + direction;
    }
}
