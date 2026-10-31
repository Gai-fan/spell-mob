package spellmob.data;

import net.minecraft.world.phys.Vec3;

/**
 * Immutable description of what a spell should be aimed at.
 *
 * @param type             target type, never null
 * @param lineOfSight      whether a clear line of sight is required
 * @param positionMode     how the aim point is computed for {@link TargetType#POSITION}
 * @param relativeOffset   offset used by {@link PositionMode#RELATIVE}
 * @param maxHealthPercent only accept targets at or below this fraction of their max health (0..1)
 * @param sort             how several valid candidates are ranked, defaults to {@link TargetSort#NEAREST}
 */
public record TargetSpec(TargetType type, boolean lineOfSight, PositionMode positionMode, Vec3 relativeOffset,
                         double maxHealthPercent, TargetSort sort) {

    public TargetSpec {
        if (type == null) {
            throw new IllegalArgumentException("target type must not be null");
        }
        if (positionMode == null) {
            positionMode = PositionMode.RELATIVE;
        }
        if (relativeOffset == null) {
            relativeOffset = Vec3.ZERO;
        }
        if (sort == null) {
            sort = TargetSort.NEAREST;
        }
        maxHealthPercent = Math.max(0.0D, Math.min(1.0D, maxHealthPercent));
    }

    /** @return the default spec used when json omits {@code target} */
    public static TargetSpec attackTarget() {
        return new TargetSpec(TargetType.ATTACK_TARGET, true, PositionMode.RELATIVE, Vec3.ZERO, 1.0D,
                TargetSort.NEAREST);
    }

    public boolean isPosition() {
        return type == TargetType.POSITION;
    }
}
