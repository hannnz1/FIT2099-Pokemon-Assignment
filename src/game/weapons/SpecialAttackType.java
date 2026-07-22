package game.weapons;

/** Type-safe definitions for Pokemon special attacks. */
public enum SpecialAttackType {
    EMBER("Ember", 'q', 30, "sparks", 65),
    WATER_BLAST("Water Blast", 'w', 25, "burbles", 85),
    BLADE_CUTTER("Blade Cutter", 'a', 20, "whips", 90);

    private final String name;
    private final char displayChar;
    private final int damage;
    private final String verb;
    private final int hitRate;

    SpecialAttackType(String name, char displayChar, int damage, String verb, int hitRate) {
        this.name = name;
        this.displayChar = displayChar;
        this.damage = damage;
        this.verb = verb;
        this.hitRate = hitRate;
    }

    SpecialAttackWeapon createWeapon() {
        return new SpecialAttackWeapon(name, displayChar, damage, verb, hitRate);
    }
}
