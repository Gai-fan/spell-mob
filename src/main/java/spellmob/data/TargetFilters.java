package spellmob.data;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Optional filters applied to a candidate target before a cast may aim at it.
 *
 * <p>All filters are ANDed: a candidate has to pass every enabled check. Empty lists mean "no
 * restriction". The entity type tags are any-of (a candidate matching at least one listed tag
 * passes); scoreboard tags in {@code excludeTags} reject on the first match.</p>
 *
 * @param entityTypes       entity type ids the target has to belong to, any-of, empty means any
 * @param entityTags        entity type tags the target has to match, any-of, empty means any
 * @param excludeTags       scoreboard tags that disqualify a target, any-of
 * @param excludeCasterTeam reject candidates that are allied to the caster's team
 * @param ignoreCreative    reject creative and spectator players
 * @param minHealthPercent  reject targets below this fraction of their max health (0..1)
 * @param maxHealthPercent  reject targets above this fraction of their max health (0..1)
 * @param requireNoShield   reject targets that are currently blocking
 */
public record TargetFilters(List<ResourceLocation> entityTypes, List<ResourceLocation> entityTags,
                            List<String> excludeTags, boolean excludeCasterTeam, boolean ignoreCreative,
                            double minHealthPercent, double maxHealthPercent, boolean requireNoShield) {

    /** Filters that accept every candidate except creative and spectator players. */
    public static final TargetFilters NONE =
            new TargetFilters(List.of(), List.of(), List.of(), false, true, 0.0D, 1.0D, false);

    private static final Map<ResourceLocation, TagKey<EntityType<?>>> TAG_CACHE = new ConcurrentHashMap<>();

    public TargetFilters {
        entityTypes = entityTypes == null ? List.of() : List.copyOf(entityTypes);
        entityTags = entityTags == null ? List.of() : List.copyOf(entityTags);
        excludeTags = excludeTags == null ? List.of() : List.copyOf(excludeTags);
        minHealthPercent = clamp01(minHealthPercent);
        maxHealthPercent = clamp01(maxHealthPercent);
        if (maxHealthPercent < minHealthPercent) {
            maxHealthPercent = minHealthPercent;
        }
    }

    /** @return true when nothing beyond the defaults is configured */
    public boolean isEmpty() {
        return entityTypes.isEmpty() && entityTags.isEmpty() && excludeTags.isEmpty() && !excludeCasterTeam
                && !requireNoShield && minHealthPercent <= 0.0D && maxHealthPercent >= 1.0D;
    }

    /**
     * Tests a candidate target.
     *
     * @param caster    the mob that wants to cast
     * @param candidate the living entity being considered
     * @return true when the candidate passes every enabled filter
     */
    public boolean test(Mob caster, LivingEntity candidate) {
        if (!entityTypes.isEmpty() && !entityTypes.contains(EntityType.getKey(candidate.getType()))) {
            return false;
        }
        if (!entityTags.isEmpty() && !matchesAnyTypeTag(candidate, entityTags)) {
            return false;
        }
        for (String tag : excludeTags) {
            if (candidate.getTags().contains(tag)) {
                return false;
            }
        }
        if (excludeCasterTeam) {
            var team = caster.getTeam();
            if (team != null && candidate.isAlliedTo(team)) {
                return false;
            }
        }
        if (ignoreCreative && candidate instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        float maxHealth = candidate.getMaxHealth();
        double fraction = maxHealth <= 0.0F ? 0.0D : candidate.getHealth() / (double) maxHealth;
        if (fraction < minHealthPercent || fraction > maxHealthPercent) {
            return false;
        }
        return !requireNoShield || !candidate.isBlocking();
    }

    private static boolean matchesAnyTypeTag(LivingEntity candidate, List<ResourceLocation> tags) {
        for (ResourceLocation id : tags) {
            TagKey<EntityType<?>> key = TAG_CACHE.computeIfAbsent(id,
                    value -> TagKey.create(Registries.ENTITY_TYPE, value));
            if (candidate.getType().is(key)) {
                return true;
            }
        }
        return false;
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return value > 0.0D ? 1.0D : 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    /**
     * Builds filters, keeping every list non-null.
     *
     * @param entityTypes      entity type ids, may be null
     * @param entityTags       entity type tags, may be null
     * @param excludeTags      scoreboard tags, may be null
     * @param excludeCasterTeam whether team allies are rejected
     * @param ignoreCreative   whether creative players are rejected
     * @param minHealthPercent lower health bound
     * @param maxHealthPercent upper health bound
     * @param requireNoShield  whether blocking targets are rejected
     * @return the filters
     */
    public static TargetFilters of(@Nullable List<ResourceLocation> entityTypes,
                                   @Nullable List<ResourceLocation> entityTags,
                                   @Nullable List<String> excludeTags, boolean excludeCasterTeam,
                                   boolean ignoreCreative, double minHealthPercent, double maxHealthPercent,
                                   boolean requireNoShield) {
        return new TargetFilters(entityTypes, entityTags, excludeTags, excludeCasterTeam, ignoreCreative,
                minHealthPercent, maxHealthPercent, requireNoShield);
    }
}
