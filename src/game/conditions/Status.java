package game.conditions;

/**
 * Use this enum class to give `buff` or `debuff`.
 * It is also useful to give a `state` to abilities or actions that can be attached-detached.
 */
public enum Status {
    IMMUNE, // an enum to identify that an object is immune to any attack.
    HOSTILE, // use this status to be considered hostile towards enemy (e.g., to be attacked by enemy)
    TRAINER, // New status for trainer
    CATCHABLE,
    CAPTURE_POKEMON,
    TRAINER_POKEBALL,
    IS_POKEMON
}
