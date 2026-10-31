package spellmob.data;

import java.util.Locale;

/**
 * How a spell decides what to aim at.
 *
 * <p>The constant names are part of the data pack format and must not be renamed.</p>
 */
public enum TargetType {

    /** The current attack target of the mob. */
    ATTACK_TARGET,
    /** Alias kept for data packs written for version 1. */
    HOSTILE_TARGET,
    /** Whoever last damaged the mob. */
    LAST_DAMAGE_SOURCE,
    /** Nearest mob that is currently hunting the caster. */
    THREATENING_ENTITY,
    /** Nearest players or monsters that are not allied with the caster. */
    NEAREST_HOSTILE,
    /** Nearest undead creature. */
    NEAREST_UNDEAD,
    /** Nearest arthropod. */
    NEAREST_ARTHROPOD,
    /** Nearest animal. */
    NEAREST_ANIMAL,
    /** Nearest monster. */
    NEAREST_MONSTER,
    /** Nearest player, ignoring creative and spectator players. */
    NEAREST_PLAYER,
    /** The owner of a tamed mob. */
    OWNER,
    /** Nearest mob of the same type or team. */
    NEAREST_ALLY,
    /** The caster itself. */
    SELF,
    /** Random hostile creature around the caster. */
    RANDOM_HOSTILE,
    /** No entity at all: the spell is aimed at a position instead. */
    POSITION;

    /**
     * Lenient lookup used by the deserializer.
     *
     * @param name the raw name from json, may be null
     * @return the matching type, or null when the name is unknown
     */
    public static TargetType byName(String name) {
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
