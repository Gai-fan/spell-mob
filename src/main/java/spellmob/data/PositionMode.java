package spellmob.data;

import java.util.Locale;

/** How the aim point of a {@link TargetType#POSITION} spell is computed. */
public enum PositionMode {

    /** Caster position plus the configured relative offset. */
    RELATIVE,
    /** Range blocks along the caster's view direction, starting at eye height. */
    EYES_LOOK,
    /** Range blocks along the caster's view direction, starting at the feet. */
    FEET_LOOK,
    /** The position of the current target entity; skipped when there is no target. */
    TARGET_ENTITY,
    /** Random point within range blocks around the caster. */
    RANDOM_IN_RANGE;

    /**
     * Lenient lookup used by the deserializer.
     *
     * @param name the raw name from json, may be null
     * @return the matching mode, or null when the name is unknown
     */
    public static PositionMode byName(String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
