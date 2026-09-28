package game.actions;

import edu.monash.fit2099.engine.items.Item;
import game.actors.Player;
import game.actors.npc.Shopkeeper;
import game.items.Candy;
import game.items.TradeOffer;
import game.items.balls.GreatBall;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeActionTest {

    @Test
    void successfulTradeConsumesCandyAndAddsProduct() {
        Player player = new Player("Ash", '@', 100);
        for (int i = 0; i < 4; i++) {
            player.addItemToInventory(new Candy());
        }

        TradeAction action = new TradeAction(new Shopkeeper(), "north", TradeOffer.GREAT_BALL);
        action.execute(player, null);

        assertEquals(1, countItems(player, Candy.class));
        assertEquals(1, countItems(player, GreatBall.class));
    }

    @Test
    void failedTradeLeavesInventoryUnchanged() {
        Player player = new Player("Ash", '@', 100);
        player.addItemToInventory(new Candy());

        TradeAction action = new TradeAction(new Shopkeeper(), "north", TradeOffer.GREAT_BALL);
        String result = action.execute(player, null);

        assertEquals(1, countItems(player, Candy.class));
        assertEquals(0, countItems(player, GreatBall.class));
        assertTrue(result.contains("需要 3 颗糖果"));
    }

    private long countItems(Player player, Class<? extends Item> type) {
        return player.getInventory().stream().filter(type::isInstance).count();
    }
}
