package spellmob.data;

import java.util.Locale;

/**
 * How the aim point of a {@link TargetType#POSITION} spell is computed.
 *
 * <p>Iron's Spellbooks casts everything from the caster itself: the cast API takes no position,
 * and projectiles spawn at the caster's eye and fly along {@code getLookAngle()}. The point
 * computed here is therefore only a look-at target. It decides which way a ray-shaped spell
 * travels (and where a look-based raycast such as teleport lands), but it cannot move the
 * origin of a spell.</p>
 */
public enum PositionMode {
    /** Caster position plus the configured relative offset. */
    RELATIVE,
    /** Range blocks along the caster's view direction, starting at eye height. */
    EYES_LOOK,
    /** Range blocks along the caster's view direction, starting at the feet. */
    FEET_LOOK,
    /** The position of the current target entity; skipped when there is no target. */
    TARGET_ENTITY,
    /**
     * A point behind the current target, on the far side from the direction it is facing.
     * {@code relativeOffset.x} is the distance in blocks (default {@code 2}) and
     * {@code relativeOffset.y} the vertical offset, so a teleport-style spell can land behind
     * the target instead of on top of it.
     */
    BEHIND_TARGET,
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
