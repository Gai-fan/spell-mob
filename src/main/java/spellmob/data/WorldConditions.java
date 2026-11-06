package spellmob.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;

import java.util.Set;

/**
 * Gates that look at the world the caster lives in: time of day, weather, moon phase, dimension,
 * difficulty and the age of the world.
 *
 * <p>Every field is optional; {@code null} means "do not care". The time of day is read from the
 * world clock, so it never matches in dimensions without a sky (the Nether and the End), where day
 * and night have no meaning; use {@code dimension} to target those.</p>
 *
 * @param time       accepted times of day, or null for any
 * @param weather    accepted weather, or null for any
 * @param moonPhase  accepted moon phases (0 = full moon), or null for any
 * @param dimensions accepted dimension ids, or null for any; modded ids such as
 *                   {@code twilightforest:twilight_forest} work
 * @param difficulty accepted difficulties, or null for any
 * @param minDay     minimum world age in days, 0 for any
 */
public record WorldConditions(Set<TimeOfDay> time, Set<Weather> weather, Set<Integer> moonPhase,
                              Set<ResourceLocation> dimensions, Set<Difficulty> difficulty, int minDay) {

    /** No world gate at all. */
    public static final WorldConditions NONE = new WorldConditions(null, null, null, null, null, 0);

    public WorldConditions {
        minDay = Math.max(0, minDay);
        time = time == null ? null : Set.copyOf(time);
        weather = weather == null ? null : Set.copyOf(weather);
        moonPhase = moonPhase == null ? null : Set.copyOf(moonPhase);
        dimensions = dimensions == null ? null : Set.copyOf(dimensions);
        difficulty = difficulty == null ? null : Set.copyOf(difficulty);
    }

    /**
     * Tests every configured gate against the world.
     *
     * @param level the level the caster is in
     * @return true when all gates pass
     */
    public boolean test(ServerLevel level) {
        if (time != null) {
            if (!level.dimensionType().hasSkyLight()) {
                return false;
            }
            if (!time.contains(TimeOfDay.of(level.getDayTime()))) {
                return false;
            }
        }
        if (weather != null && !weather.contains(Weather.of(level))) {
            return false;
        }
        if (moonPhase != null && !moonPhase.contains(moonPhase(level))) {
            return false;
        }
        if (dimensions != null && !dimensions.contains(level.dimension().location())) {
            return false;
        }
        if (difficulty != null && !difficulty.contains(level.getLevelData().getDifficulty())) {
            return false;
        }
        return minDay <= 0 || level.getDayTime() / 24000L >= minDay;
    }

    private static int moonPhase(ServerLevel level) {
        return (int) ((level.getDayTime() / 24000L) % 8L);
    }

    /** Coarse buckets of the world clock. */
    public enum TimeOfDay {
        /** 23000-23999: the sun is about to come up. */
        DAWN,
        /** 0-11999: sunrise until noon until the sun starts going down. */
        DAY,
        /** 12000-12999: the sun sets, monsters start to spawn. */
        DUSK,
        /** 13000-22999: the monsters are out. */
        NIGHT;

        public static TimeOfDay of(long dayTime) {
            long time = Math.floorMod(dayTime, 24000L);
            if (time < 12000L) {
                return DAY;
            }
            if (time < 13000L) {
                return DUSK;
            }
            return time < 23000L ? NIGHT : DAWN;
        }
    }

    /** The three states the vanilla weather can be in. */
    public enum Weather {
        CLEAR, RAIN, THUNDER;

        public static Weather of(ServerLevel level) {
            if (level.isThundering()) {
                return THUNDER;
            }
            return level.isRaining() ? RAIN : CLEAR;
        }
    }
}
