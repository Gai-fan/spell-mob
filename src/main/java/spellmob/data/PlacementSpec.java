package spellmob.data;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Safety checks applied to a computed {@link TargetType#POSITION} point.
 *
 * <p>Only used by the modes that can pick an arbitrary spot ({@link PositionMode#RANDOM_IN_RANGE}
 * and {@link PositionMode#BEHIND_TARGET}); the other modes aim at something that already exists.</p>
 *
 * @param avoidFluid reject points inside a liquid (water or lava)
 * @param avoidLava  reject points inside lava, but still allow water
 */
public record PlacementSpec(boolean avoidFluid, boolean avoidLava) {

    /** No checks at all — the default. */
    public static final PlacementSpec NONE = new PlacementSpec(false, false);

    /**
     * Tests a point against the configured checks.
     *
     * @param level    the level the point is in
     * @param position the point to test
     * @return true when the point passes every enabled check
     */
    public boolean isSafe(ServerLevel level, Vec3 position) {
        if (!avoidFluid && !avoidLava) {
            return true;
        }
        BlockState state = level.getBlockState(BlockPos.containing(position));
        if (state.getFluidState().isEmpty()) {
            return true;
        }
        if (avoidFluid) {
            return false;
        }
        return !state.getFluidState().is(FluidTags.LAVA);
    }
}
