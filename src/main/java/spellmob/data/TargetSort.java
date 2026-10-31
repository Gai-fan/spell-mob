package spellmob.data;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Order in which several valid candidates of a {@code NEAREST_*} or hostile lookup are ranked.
 *
 * <p>Only the first candidate wins, so the order decides which mob a spell is aimed at when more
 * than one qualifies. Ties are always broken by distance so that the behaviour stays deterministic
 * for the same world state.</p>
 */
public enum TargetSort {

    /** Closest candidate, the default. */
    NEAREST,
    /** Farthest candidate still inside the spell range. */
    FARTHEST,
    /** Candidate with the lowest current health, useful for finishing blows. */
    LOWEST_HEALTH,
    /** Candidate with the highest current health. */
    HIGHEST_HEALTH,
    /** Uniformly random candidate. */
    RANDOM;

    /**
     * @param name the json value
     * @return the matching sort, or null when the name is unknown
     */
    public static @Nullable TargetSort byName(String name) {
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
