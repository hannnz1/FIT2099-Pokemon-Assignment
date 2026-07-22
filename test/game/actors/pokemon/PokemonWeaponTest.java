package game.actors.pokemon;

import edu.monash.fit2099.engine.items.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class PokemonWeaponTest {

    @Test
    void equippingIsIdempotentAndUnequippingRemovesTheWeapon() {
        Mudkip mudkip = new Mudkip();

        mudkip.toggleWeapon(true);
        mudkip.toggleWeapon(true);
        assertEquals(1, mudkip.getInventory().size());
        assertEquals(25, mudkip.getWeapon().damage());

        mudkip.toggleWeapon(false);
        assertEquals(0, mudkip.getInventory().size());
        assertEquals(10, mudkip.getWeapon().damage());
    }

    @Test
    void eachPokemonOwnsADifferentWeaponInstance() {
        Mudkip first = new Mudkip();
        Mudkip second = new Mudkip();
        first.toggleWeapon(true);
        second.toggleWeapon(true);

        Item firstWeapon = first.getInventory().get(0);
        Item secondWeapon = second.getInventory().get(0);
        assertNotSame(firstWeapon, secondWeapon);
    }
}
