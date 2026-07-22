package game.items;

import edu.monash.fit2099.engine.items.Item;
import game.actors.pokemon.Torchic;
import game.items.balls.GreatBall;
import game.items.balls.MasterBall;
import game.items.balls.Pokeball;

import java.util.function.Supplier;

/** Products offered by the Shopkeeper in exchange for Candy. */
public enum TradeOffer {
    GREAT_BALL("GreatBall", 3, GreatBall::new),
    MASTER_BALL("MasterBall", 6, MasterBall::new),
    TORCHIC("Torchic in a Pokeball", 10,
            () -> new Pokeball().capturePokemon(new Torchic()));

    private final String description;
    private final int candyCost;
    private final Supplier<Item> productFactory;

    TradeOffer(String description, int candyCost, Supplier<Item> productFactory) {
        this.description = description;
        this.candyCost = candyCost;
        this.productFactory = productFactory;
    }

    public int getCandyCost() {
        return candyCost;
    }

    public Item createProduct() {
        return productFactory.get();
    }

    @Override
    public String toString() {
        return description;
    }
}
