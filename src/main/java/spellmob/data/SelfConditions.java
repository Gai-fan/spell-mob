package spellmob.data;

import net.minecraft.world.entity.Entity;

/**
 * Gates that look at the caster itself, independent of the world and the target.
 *
 * <p>Each flag is tri-state: {@code null} means "do not care", {@code true} means the caster must be
 * in that state and {@code false} means it must not be.</p>
 *
 * @param onFire  whether the caster has to be burning
 * @param inWater whether the caster has to be in water
 * @param inLava  whether the caster has to be in lava
 * @param frozen  whether the caster has to be frozen (powder snow)
 */
public record SelfConditions(Boolean onFire, Boolean inWater, Boolean inLava, Boolean frozen) {

    /** No self gate at all. */
    public static final SelfConditions NONE = new SelfConditions(null, null, null, null);

    /**
     * Tests every configured flag against the caster.
     *
     * @param caster the mob that wants to cast
     * @return true when all flags pass
     */
    public boolean test(Entity caster) {
        if (onFire != null && caster.isOnFire() != onFire) {
            return false;
        }
        if (inWater != null && caster.isInWater() != inWater) {
            return false;
        }
        if (inLava != null && caster.isInLava() != inLava) {
            return false;
        }
        return frozen == null || (caster.getTicksFrozen() > 0) == frozen;
    }
}
