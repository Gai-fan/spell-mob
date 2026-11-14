package spellmob.capability;

/**
 * Why an in-flight cast stopped before it completed.
 *
 * <p>The reason is written to the debug log, and only the {@link #visible()} ones are shown to
 * players. That distinction matters: a cast that disappears because the mob was unloaded or the
 * data pack reloaded must not puff smoke, or walking back and forth over a chunk border would
 * spam particles.</p>
 */
public enum CancelReason {

    /** The mod was turned off in the server config. */
    DISABLED("disabled in config", false),

    /** The caster left the level (chunk unload, teleport, dimension change). */
    UNLOADED("entity unloaded", false),

    /** The casting goal was removed, for example by a data pack reload or a name change. */
    GOAL_STOPPED("goal stopped", false),

    /** A boss phase started and the show takes over, no interrupt feedback. */
    PHASE_CHANGE("phase change", false),

    /** The target died, moved out of range or left the line of sight. */
    TARGET_LOST("target lost", true),

    /** The caster died while casting. */
    CASTER_DIED("caster died", true),

    /** The caster was damaged and the spell is configured to break on hit. */
    DAMAGED("interrupted by damage", true),

    /** A spell callback threw an exception. */
    INTERNAL_ERROR("cast tick failed", true);

    private final String message;
    private final boolean visible;

    CancelReason(String message, boolean visible) {
        this.message = message;
        this.visible = visible;
    }

    /**
     * @return short reason used for debug logging
     */
    public String message() {
        return message;
    }

    /**
     * @return true when players should get feedback (smoke and a sound) for this cancellation
     */
    public boolean visible() {
        return visible;
    }
}
