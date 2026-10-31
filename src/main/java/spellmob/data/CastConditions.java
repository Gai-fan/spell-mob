package spellmob.data;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Extra gates a cast has to pass before it may start.
 *
 * <p>{@code healthBelow} / {@code healthAbove} look at the caster, while the distance bounds look at
 * the chosen target.</p>
 *
 * @param healthBelow        only cast while the caster is at or below this health fraction (1 = always)
 * @param healthAbove        only cast while the caster is at or above this health fraction (0 = always)
 * @param minTargetDistance  minimum distance to the target, in blocks
 * @param maxTargetDistance  maximum distance to the target, in blocks
 */
public record CastConditions(double healthBelow, double healthAbove, double minTargetDistance,
                             double maxTargetDistance) {

    public static final CastConditions NONE = new CastConditions(1.0D, 0.0D, 0.0D, Double.MAX_VALUE);

    public CastConditions {
        healthBelow = Math.max(0.0D, Math.min(1.0D, healthBelow));
        healthAbove = Math.max(0.0D, Math.min(1.0D, healthAbove));
        minTargetDistance = Math.max(0.0D, minTargetDistance);
        maxTargetDistance = Math.max(minTargetDistance, maxTargetDistance);
    }

    /**
     * Tests the conditions against the current situation.
     *
     * @param caster the mob that wants to cast
     * @param target the resolved target, may be null for position spells
     * @return true when the cast may start
     */
    public boolean test(Mob caster, LivingEntity target) {
        float maxHealth = caster.getMaxHealth();
        float fraction = maxHealth <= 0.0F ? 0.0F : caster.getHealth() / maxHealth;
        if (fraction > healthBelow || fraction < healthAbove) {
            return false;
        }
        if (target != null) {
            double distance = Math.sqrt(caster.distanceToSqr(target));
            return distance >= minTargetDistance && distance <= maxTargetDistance;
        }
        return true;
    }
}
