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
 * @param variants  individual rules, evaluated in order, first match wins
 * @param dropRules species wide loot rules; the matched variant may add its own, and every rule
 *                  whose selector matches the dying mob is rolled (they no longer replace each
 *                  other, so two rules can share a selector)
 * @param source    where the rules come from, shown by {@code /spellmob info}
 */
public record SpellCasterRules(SpellCasterData fallback, List<SpellCasterVariant> variants,
                               List<DropRule> dropRules, String source) {

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
                || json.has("onDeath") || json.has("onKill") || json.has("dropRules");
        if (!hasDefault && variantsElement == null) {
            return null;
        }

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
        return new SpellCasterRules(fallback, variants, dropRules, SOURCE_DATA_PACK);
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
            try {
                dropRules.add(DropRule.parse(entry.getAsJsonObject(), entryWhere));
            } catch (RuntimeException exception) {
                Logs.warn("{}: {}", entryWhere, exception.getMessage());
            }
        }
        return dropRules;
    }

    private static SpellCasterVariant parseVariant(JsonObject json, String where) {
        JsonElement selectElement = json.get("select");
        if (selectElement == null || !selectElement.isJsonObject()) {
            throw new IllegalArgumentException(where + ": missing 'select' object");
        }
        VariantSelector selector = VariantSelector.parse(selectElement.getAsJsonObject(), where + "/select");
        boolean override = JsonHelper.optBoolean(json, "override", true);
        SpellCasterData data = SpellCasterData.parse(json, where);
        List<DropRule> dropRules = parseDropRules(json.get("dropRules"), where + "/dropRules");
        return new SpellCasterVariant(selector, override, data == null ? SpellCasterData.EMPTY : data,
                dropRules);
    }

    /**
     * Resolves the configuration that applies to one mob.
     *
     * @param mob the mob
     * @return the effective configuration, never null but possibly
     * {@link SpellCasterData#isEmpty() empty}
     */
    public SpellCasterData resolve(Mob mob) {
        int index = variantIndex(mob);
        if (index < 0) {
            return fallback;
        }
        SpellCasterVariant variant = variants.get(index);
        return variant.override() ? variant.data() : variant.data().mergeWith(fallback);
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
     * Collects every drop rule that matches the mob.
     *
     * <p>The matched variant's own rules come first, then the species wide {@code dropRules}. All
     * of them are returned, so two rules may share a selector (for example "always drop gold" plus
     * "20% chance of a loot table") instead of the second silently never firing.</p>
     *
     * @param mob the dead mob
     * @return the matching rules in evaluation order, possibly empty
     */
    public List<DropRule> dropRulesFor(Mob mob) {
        List<DropRule> matched = new ArrayList<>();
        int index = variantIndex(mob);
        if (index >= 0) {
            for (DropRule rule : variants.get(index).dropRules()) {
                if (rule.matches(mob)) {
                    matched.add(rule);
                }
            }
        }
        for (DropRule rule : dropRules) {
            if (rule.matches(mob)) {
                matched.add(rule);
            }
        }
        return matched;
    }

    /**
     * Describes which variant a mob matched, for {@code /spellmob info} and debug logs.
     *
     * @param mob the mob
     * @return a short description
     */
    public String describeMatch(Mob mob) {
        int index = variantIndex(mob);
        if (index >= 0) {
            SpellCasterVariant variant = variants.get(index);
            return "variant #" + index + " (" + variant.selector().describe()
                    + ", override=" + variant.override() + ")";
        }
        return variants.isEmpty() ? "species default" : "species default (no variant matched)";
    }
}
