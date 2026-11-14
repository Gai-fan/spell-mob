package spellmob.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;
import spellmob.util.JsonHelper;
import spellmob.util.Logs;

/**
 * The rules of one entity type: a species wide fallback plus optional per-individual variants.
 *
 * <p>{@code data/<namespace>/spell_casters/zombie.json} is read as the fallback for every zombie,
 * and its {@code variants} array lets a named or tagged zombie cast something else (or nothing at
 * all).</p>
 *
 * @param fallback  the configuration every mob of the type gets when no variant matches
 * @param variants  individual rules, evaluated in order, first match wins; a {@code sticky} variant
 *                  stays active after its first match even when the mob heals back up
 * @param dropRules species wide loot rules; the matched variant may add its own, and every rule
 *                  whose selector matches the dying mob is rolled (they no longer replace each
 *                  other, so two rules can share a selector)
 * @param bossBar   species wide boss bar override; {@code null} means "follow the presentation
 *                  config", {@code true}/{@code false} force the bar on or off for these mobs
 * @param disableAttackGoals {@code true} removes the vanilla melee and ranged attack goals from
 *                  these mobs, so only their spells deal damage; the removed goals are put back
 *                  when a later variant or a data pack reload turns the flag off
 * @param source    where the rules come from, shown by {@code /spellmob info}
 */
public record SpellCasterRules(SpellCasterData fallback, List<SpellCasterVariant> variants,
                               List<DropRule> dropRules, @Nullable Boolean bossBar,
                               boolean disableAttackGoals, String source) {

    /** Source label used for rules read from {@code data/.../spell_casters}. */
    public static final String SOURCE_DATA_PACK = "data pack";
    /** Source label used for rules read from the {@code species.extraRules} config list. */
    public static final String SOURCE_CONFIG = "config";

    public SpellCasterRules {
        variants = List.copyOf(variants);
        dropRules = List.copyOf(dropRules);
        if (source == null) {
            source = SOURCE_DATA_PACK;
        }
    }

    /**
     * Parses one {@code spell_casters} file.
     *
     * @param json  the file contents
     * @param where file path, used for log messages
     * @return the rules, or {@code null} when the file does not look like a SpellMob definition at
     * all (other mods use the same folder name for their own formats)
     */
    public static @Nullable SpellCasterRules parse(JsonObject json, String where) {
        JsonElement variantsElement = json.get("variants");
        boolean hasDefault = json.has("goals") || json.has("onHurt") || json.has("onTarget")
                || json.has("onDeath") || json.has("onKill") || json.has("dropRules")
                || json.has("disableAttackGoals");
        if (!hasDefault && variantsElement == null) {
            return null;
        }
        JsonHelper.warnUnknown(json, where, "variants", "goals", "onHurt", "onTarget",
                "onDeath", "onKill", "dropRules", "bossBar", "disableAttackGoals", "$schema");

        SpellCasterData fallback = SpellCasterData.parse(json, where);
        if (fallback == null) {
            fallback = SpellCasterData.EMPTY;
        }

        List<SpellCasterVariant> variants = new ArrayList<>();
        if (variantsElement != null && !variantsElement.isJsonNull()) {
            if (!variantsElement.isJsonArray()) {
                throw new IllegalArgumentException(where + ": 'variants' must be an array");
            }
            JsonArray array = variantsElement.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                JsonElement element = array.get(index);
                String entryWhere = where + "/variants[" + index + "]";
                if (element == null || !element.isJsonObject()) {
                    Logs.warn("{}: entry is not an object, skipping", entryWhere);
                    continue;
                }
                try {
                    variants.add(parseVariant(element.getAsJsonObject(), entryWhere));
                } catch (RuntimeException exception) {
                    Logs.warn("{}: {}", entryWhere, exception.getMessage());
                }
            }
        }
        List<DropRule> dropRules = parseDropRules(json.get("dropRules"), where + "/dropRules");
        Boolean bossBar = JsonHelper.optBooleanOrNull(json, "bossBar");
        boolean disableAttackGoals = JsonHelper.optBoolean(json, "disableAttackGoals", false);
        return new SpellCasterRules(fallback, variants, dropRules, bossBar, disableAttackGoals,
                SOURCE_DATA_PACK);
    }

    /**
     * Parses a {@code dropRules} array.
     *
     * @param element the array element, may be {@code null} when the key is absent
     * @param where   path prefix for log messages
     * @return the parsed rules, empty when the key is absent
     */
    private static List<DropRule> parseDropRules(JsonElement element, String where) {
        List<DropRule> dropRules = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return dropRules;
        }
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException(where + ": must be an array");
        }
        JsonArray dropArray = element.getAsJsonArray();
        for (int index = 0; index < dropArray.size(); index++) {
            JsonElement entry = dropArray.get(index);
            String entryWhere = where + "[" + index + "]";
            if (entry == null || !entry.isJsonObject()) {
                Logs.warn("{}: entry is not an object, skipping", entryWhere);
                continue;
            }
            JsonObject ruleJson = entry.getAsJsonObject();
            try {
                dropRules.add(DropRule.parse(ruleJson, entryWhere));
                warnHealthSelector(ruleJson, entryWhere);
            } catch (RuntimeException exception) {
                Logs.warn("{}: {}", entryWhere, exception.getMessage());
            }
        }
        return dropRules;
    }

    /**
     * Warns about health conditions inside a drop rule's {@code select}.
     *
     * <p>Drops are rolled from {@code LivingDropsEvent}, that is once the mob is already dead and its
     * health fraction is 0. A {@code healthBelow} threshold therefore always matches and a
     * {@code healthAbove} threshold never does; phase specific loot belongs in
     * {@code variants[i].dropRules}, which the death event already resolves.
     *
     * @param json  the drop rule object
     * @param where path prefix for log messages
     */
    private static void warnHealthSelector(JsonObject json, String where) {
        JsonElement select = json.get("select");
        if (select == null || !select.isJsonObject()) {
            return;
        }
        JsonObject selector = select.getAsJsonObject();
        if (selector.has("healthBelow") || selector.has("healthAbove")) {
            Logs.warn("{}: health conditions in 'select' decide nothing - drops are rolled on death, "
                    + "where the health fraction is 0 (use variants[i].dropRules for phase loot)", where);
        }
    }

    private static SpellCasterVariant parseVariant(JsonObject json, String where) {
        JsonElement selectElement = json.get("select");
        if (selectElement == null || !selectElement.isJsonObject()) {
            throw new IllegalArgumentException(where + ": missing 'select' object");
        }
        JsonObject select = selectElement.getAsJsonObject();
        JsonHelper.warnUnknown(json, where, "select", "override", "sticky", "bossBar", "learner",
                "adaptive", "disableAttackGoals", "onPhase", "goals", "onHurt", "onTarget",
                "onDeath", "onKill", "dropRules");
        VariantSelector selector = VariantSelector.parse(select, where + "/select");
        boolean override = JsonHelper.optBoolean(json, "override", true);
        // 'sticky' belongs to the variant, but accepting it inside 'select' too costs nothing and
        // spares file authors a confusing "nothing happened" when they put it one level too deep.
        boolean sticky = JsonHelper.optBoolean(json, "sticky", JsonHelper.optBoolean(select, "sticky", false));
        // Same deal for 'bossBar': per variant control beats the config default, and "absent" must
        // stay distinguishable from "false", so the Boolean stays nullable.
        Boolean bossBar = JsonHelper.optBooleanOrNull(json, "bossBar");
        if (bossBar == null) {
            bossBar = JsonHelper.optBooleanOrNull(select, "bossBar");
        }
        // 'learner' follows the same "variant level, or inside select" rule so file authors do not
        // have to remember which of the two blocks it belongs to.
        boolean learner = JsonHelper.optBoolean(json, "learner", JsonHelper.optBoolean(select, "learner", false));
        // 'adaptive' mirrors 'learner': either level works, and it saves boss authors from having to
        // tag every summon or spawn egg by hand.
        boolean adaptive = JsonHelper.optBoolean(json, "adaptive", JsonHelper.optBoolean(select, "adaptive", false));
        // Three states again: absent follows the species default, true removes the vanilla attack
        // goals, false puts back the ones this caster removed earlier.
        Boolean disableAttackGoals = JsonHelper.optBooleanOrNull(json, "disableAttackGoals");
        if (disableAttackGoals == null) {
            disableAttackGoals = JsonHelper.optBooleanOrNull(select, "disableAttackGoals");
        }
        PhaseEffect phase = parsePhase(json.get("onPhase"), where + "/onPhase");
        SpellCasterData data = SpellCasterData.parse(json, where);
        List<DropRule> dropRules = parseDropRules(json.get("dropRules"), where + "/dropRules");
        return new SpellCasterVariant(selector, override, sticky, bossBar, learner, adaptive,
                disableAttackGoals, phase, data == null ? SpellCasterData.EMPTY : data, dropRules);
    }

    /**
     * Parses the optional {@code onPhase} object of one variant.
     *
     * @param element the element, may be {@code null} when the key is absent
     * @param where   path prefix for log messages
     * @return the parsed show, or {@code null} when the key is absent
     */
    private static @Nullable PhaseEffect parsePhase(@Nullable JsonElement element, String where) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(where + ": must be an object");
        }
        return PhaseEffect.parse(element.getAsJsonObject(), where);
    }

    /**
     * @param mob the mob
     * @return the phase show of the variant that currently matches this mob, if any
     */
    public @Nullable PhaseEffect phase(Mob mob) {
        return phase(variantIndex(mob));
    }

    /**
     * @param index a resolved variant index, or -1 for the species default
     * @return the phase show of that variant, or {@code null}
     */
    public @Nullable PhaseEffect phase(int index) {
        return index >= 0 && index < variants.size() ? variants.get(index).phase() : null;
    }

    /**
     * @param index a variant index
     * @return the number of variants in this file
     */
    public int variantCount() {
        return variants.size();
    }

    /**
     * @param index a variant index
     * @return true when that variant keeps its phase after the mob heals back
     */
    public boolean isSticky(int index) {
        return index >= 0 && index < variants.size() && variants.get(index).sticky();
    }

    /**
     * Resolves the data pack boss bar override for one mob.
     *
     * @param mob the mob
     * @return the matched variant's override when it has one, otherwise the species wide override,
     * otherwise {@code null} meaning "follow the presentation config"
     */
    public @Nullable Boolean bossBar(Mob mob) {
        return bossBar(variantIndex(mob));
    }

    /**
     * @param index a resolved variant index, or -1 for the species default
     * @return the matched variant's override when it has one, otherwise the species wide override,
     * otherwise {@code null} meaning "follow the presentation config"
     */
    public @Nullable Boolean bossBar(int index) {
        if (index >= 0) {
            Boolean override = variants.get(index).bossBar();
            if (override != null) {
                return override;
            }
        }
        return bossBar;
    }

    /**
     * Resolves the vanilla attack goal override for one mob.
     *
     * @param mob the mob
     * @return true when the matched variant, or the species default, disables vanilla attacks
     */
    public boolean disableAttackGoals(Mob mob) {
        return disableAttackGoals(variantIndex(mob));
    }

    /**
     * @param index a resolved variant index, or -1 for the species default
     * @return the matched variant's override when it has one, otherwise the species wide flag
     */
    public boolean disableAttackGoals(int index) {
        if (index >= 0) {
            Boolean override = variants.get(index).disableAttackGoals();
            if (override != null) {
                return override;
            }
        }
        return disableAttackGoals;
    }

    /**
     * @param mob the mob
     * @return true when the variant that currently matches this mob allows learning scroll spells
     */
    public boolean learner(Mob mob) {
        return learner(variantIndex(mob));
    }

    /**
     * @param index a resolved variant index, or -1 for the species default
     * @return true when that variant allows learning scroll spells
     */
    public boolean learner(int index) {
        return index >= 0 && variants.get(index).learner();
    }

    /**
     * @param mob the mob
     * @return true when the variant that currently matches this mob lets it learn player spells
     */
    public boolean adaptive(Mob mob) {
        return adaptive(variantIndex(mob));
    }

    /**
     * @param index a resolved variant index, or -1 for the species default
     * @return true when that variant lets the mob learn player spells
     */
    public boolean adaptive(int index) {
        return index >= 0 && variants.get(index).adaptive();
    }

    /**
     * @return true when at least one variant of this species allows learning scroll spells
     */
    public boolean hasLearnerVariants() {
        for (SpellCasterVariant variant : variants) {
            if (variant.learner()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true when at least one variant of this species can lock its phase
     */
    public boolean hasStickyVariants() {
        for (SpellCasterVariant variant : variants) {
            if (variant.sticky()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the configuration that applies to one mob.
     *
     * @param mob the mob
     * @return the effective configuration, never null but possibly
     * {@link SpellCasterData#isEmpty() empty}
     */
    public SpellCasterData resolve(Mob mob) {
        return resolve(variantIndex(mob));
    }

    /**
     * Resolves the configuration of one variant index.
     *
     * <p>Every rebuild path (goal sync, boss bar, learner flag) has to go through this overload
     * with {@link #effectiveIndex(Mob, int)} so a sticky phase survives chunk reloads and health
     * regeneration.</p>
     *
     * @param index a variant index, or -1 for the species default
     * @return the effective configuration, never null
     */
    public SpellCasterData resolve(int index) {
        if (index < 0) {
            return fallback;
        }
        SpellCasterVariant variant = variants.get(index);
        return variant.override() ? variant.data() : variant.data().mergeWith(fallback);
    }

    /**
     * Resolves the variant index while honouring a sticky lock.
     *
     * <p>{@link #variantIndex(Mob)} is a raw selector match: it happily falls back to an earlier
     * phase once the mob heals above the threshold. A sticky phase must survive that, so rebuilds
     * have to use the lock stored on the caster.</p>
     *
     * @param mob    the mob
     * @param locked the locked variant index, or -1 when nothing is locked
     * @return the index to use, -1 when the species default applies
     */
    public int effectiveIndex(Mob mob, int locked) {
        int matched = variantIndex(mob);
        if (locked >= 0) {
            if (locked >= variants.size() || !variants.get(locked).sticky()) {
                return matched;
            }
            if (matched < 0) {
                return locked;
            }
            // Variants are listed deepest phase first, so the lowest matching index is the furthest
            // the mob has ever been; the lock may only move towards it.
            return Math.min(matched, locked);
        }
        return matched;
    }

    /**
     * Finds the first variant that matches the mob.
     *
     * <p>Comparing indices instead of resolved data lets callers detect "the mob switched to another
     * phase" without re-comparing the rule objects; {@code mergeWith} allocates a new instance on
     * every call, so object identity would not work for non-overriding variants.</p>
     *
     * @param mob the mob
     * @return the index of the first matching variant, or -1 when the species default applies
     */
    public int variantIndex(Mob mob) {
        for (int index = 0; index < variants.size(); index++) {
            if (variants.get(index).selector().matches(mob)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Collects the drop rules of one effective variant index.
     *
     * <p>The matched variant's own rules come first, then the species wide {@code dropRules}. All
     * of them are returned, so two rules may share a selector (for example "always drop gold" plus
     * "20% chance of a loot table") instead of the second silently never firing.</p>
     *
     * <p>A rule inside a variant needs no {@code select}: the variant already matched the mob, and
     * drops are only rolled once it dies. Species wide rules keep their selector.</p>
     *
     * <p>Callers must pass the effective variant index rather than re-matching: the raw selector
     * match can fall back to an earlier variant once the mob healed above the threshold, which
     * would roll the wrong loot table for a boss that is still locked in its second phase.</p>
     *
     * @param mob   the dead mob
     * @param index the effective variant index
     * @return the applicable rules in evaluation order, possibly empty
     */
    public List<DropRule> dropRulesFor(Mob mob, int index) {
        List<DropRule> matched = new ArrayList<>();
        if (index >= 0) {
            // The variant already matched and drops are only rolled on death, so a second
            // selector on every rule would just be a chance to disagree with its own variant.
            matched.addAll(variants.get(index).dropRules());
        }
        for (DropRule rule : dropRules) {
            if (rule.matches(mob)) {
                matched.add(rule);
            }
        }
        return matched;
    }

    /**
     * Describes one effective variant index, for {@code /spellmob info} and debug logs.
     *
     * @param index the effective variant index, or -1 for the species default
     * @return a short description
     */
    public String describeMatch(int index) {
        if (index >= 0) {
            SpellCasterVariant variant = variants.get(index);
            return "variant #" + index + " (" + variant.selector().describe()
                    + ", override=" + variant.override() + ")";
        }
        return variants.isEmpty() ? "species default" : "species default (no variant matched)";
    }
}
