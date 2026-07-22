package game.weapons;

import java.util.Objects;

/** Factory for fresh special-attack weapon instances. */
public final class BackupWeaponsManager {

    private static final BackupWeaponsManager INSTANCE = new BackupWeaponsManager();

    private BackupWeaponsManager() {
    }

    public static BackupWeaponsManager getInstance() {
        return INSTANCE;
    }

    public SpecialAttackWeapon createWeapon(SpecialAttackType type) {
        return Objects.requireNonNull(type, "special attack type cannot be null").createWeapon();
    }
}
