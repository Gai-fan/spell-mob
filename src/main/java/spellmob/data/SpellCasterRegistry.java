package spellmob.data;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;
import spellmob.util.Logs;

/**
 * Holds the spell caster rules currently in effect.
 *
 * <p>There are two sources: data packs (which may also define per-individual variants) and the
 * {@code filters.extraRules} config entries (species wide only). A data pack entry for an entity
 * type always wins; the config entry for the same type is ignored and reported once, so that a
 * server owner cannot silently override what the pack author shipped.</p>
 */
public final class SpellCasterRegistry {

    private static volatile Map<ResourceLocation, SpellCasterRules> byEntityType = Map.of();
    private static volatile Map<ResourceLocation, SpellCasterRules> byConfig = Map.of();
    private static final Set<ResourceLocation> shadowed = ConcurrentHashMap.newKeySet();

    private SpellCasterRegistry() {
    }

    /**
     * Replaces the active data pack rules. Called from the data pack reload thread.
     *
     * @param parsed the freshly parsed rules, keyed by entity type id
     */
    public static void replace(Map<ResourceLocation, SpellCasterRules> parsed) {
        byEntityType = Map.copyOf(parsed);
        logShadowed();
    }

    /**
     * Replaces the active config rules. Config rules are species wide, so they never carry variants.
     *
     * @param parsed the freshly parsed config rules, keyed by entity type id
     */
    public static void replaceConfig(Map<ResourceLocation, SpellCasterData> parsed) {
        Map<ResourceLocation, SpellCasterRules> wrapped = new LinkedHashMap<>();
        parsed.forEach((id, data) ->
                wrapped.put(id, new SpellCasterRules(data, List.of(), List.of(), null, false,
                        SpellCasterRules.SOURCE_CONFIG)));
        byConfig = Map.copyOf(wrapped);
        logShadowed();
        if (!byConfig.isEmpty()) {
            Logs.info("Loaded {} config spell caster rule(s)", byConfig.size());
        }
    }

    /**
     * Looks up the rules of an entity type id.
     *
     * @param id the entity type id, may be null
     * @return the rules, or null when the entity type is not configured
     */
    public static @Nullable SpellCasterRules get(@Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        SpellCasterRules fromDataPack = byEntityType.get(id);
        return fromDataPack != null ? fromDataPack : byConfig.get(id);
    }

    /**
     * Looks up the rules of an entity type.
     *
     * @param type the entity type
     * @return the rules, or null when the entity type is not configured
     */
    public static @Nullable SpellCasterRules get(EntityType<?> type) {
        return get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    /**
     * Looks up the data pack boss bar override of a mob.
     *
     * @param mob the mob
     * @return the override ({@code true}/{@code false}), or {@code null} when the mob has no rule
     * or the rule leaves the choice to the presentation config
     */
    public static @Nullable Boolean bossBarOverride(Mob mob) {
        SpellCasterRules rules = get(mob.getType());
        return rules == null ? null : rules.bossBar(mob);
    }

    /**
     * @param mob          the mob
     * @param variantIndex the resolved (sticky aware) variant index
     * @return the data pack override for the boss bar, or {@code null} to follow the config
     */
    public static @Nullable Boolean bossBarOverride(Mob mob, int variantIndex) {
        SpellCasterRules rules = get(mob.getType());
        return rules == null ? null : rules.bossBar(variantIndex);
    }

    /** @return how many entity types are configured by either source */
    public static int size() {
        return ids().size();
    }

    /** @return entity type ids whose config rule is ignored because a data pack defines them first */
    public static List<ResourceLocation> shadowed() {
        return shadowed.stream().sorted().toList();
    }

    /** @return the configured entity type ids, sorted and de-duplicated for stable command output */
    public static List<ResourceLocation> ids() {
        Set<ResourceLocation> all = new HashSet<>(byEntityType.keySet());
        all.addAll(byConfig.keySet());
        return all.stream().sorted().toList();
    }

    /** Reports every config rule currently shadowed by a data pack entry, once per transition. */
    private static void logShadowed() {
        Map<ResourceLocation, SpellCasterRules> fromDataPack = byEntityType;
        Set<ResourceLocation> overlap = new LinkedHashSet<>();
        for (ResourceLocation id : byConfig.keySet()) {
            if (fromDataPack.containsKey(id)) {
                overlap.add(id);
            }
        }
        shadowed.retainAll(overlap);
        for (ResourceLocation id : overlap) {
            if (shadowed.add(id)) {
                Logs.info("Config rule for {} ignored: the data pack already defines it", id);
            }
        }
    }
}
