package spellmob.data;

import net.minecraft.world.phys.Vec3;

/**
 * Immutable description of what a spell should be aimed at.
 *
 * @param type             target type, never null
 * @param lineOfSight      whether a clear line of sight is required
 * @param positionMode     how the aim point is computed for {@link TargetType#POSITION}
 * @param relativeOffset   offset used by {@link PositionMode#RELATIVE}
 * @param sort             how several valid candidates are ranked, defaults to {@link TargetSort#NEAREST}
 * @param filters          extra restrictions a candidate has to pass, never null
 */
public record TargetSpec(TargetType type, boolean lineOfSight, PositionMode positionMode, Vec3 relativeOffset,
                         TargetSort sort, TargetFilters filters) {

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
        filters = filters == null ? TargetFilters.NONE : filters;
    }

    /** @return the default spec used when json omits {@code target} */
    public static TargetSpec attackTarget() {
        return new TargetSpec(TargetType.ATTACK_TARGET, true, PositionMode.RELATIVE, Vec3.ZERO,
                TargetSort.NEAREST, TargetFilters.NONE);
    }

    public boolean isPosition() {
        return type == TargetType.POSITION;
    }
}
