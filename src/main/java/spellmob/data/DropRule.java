package spellmob.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Mob;
import spellmob.util.JsonHelper;
import spellmob.util.Logs;

/**
 * One entry of a file's {@code dropRules} array.
 *
 * <p>Rules are evaluated in order and the first match wins, exactly like {@code variants}. They are
 * deliberately independent of the casting variants: a boss that switches to a lower health variant
 * keeps dropping the same loot, because the drop rules only look at the mob itself.</p>
 *
 * @param selector decides which mobs the rule applies to
 * @param chance   probability applied to the whole rule, 0..1
 * @param drops    the entries rolled when the rule fires
 */
public record DropRule(VariantSelector selector, double chance, List<DropEntry> drops) {

    public DropRule {
        drops = List.copyOf(drops);
        if (!Double.isFinite(chance)) {
            chance = 1.0D;
        }
        chance = Math.max(0.0D, Math.min(1.0D, chance));
    }

    /**
     * Parses one {@code dropRules} array element.
     *
     * @param json  the element
     * @param where file path for error messages
     * @return the rule
     * @throws IllegalArgumentException when the rule is malformed or has no usable drops
     */
    public static DropRule parse(JsonObject json, String where) {
        JsonElement selectElement = json.get("select");
        if (selectElement == null || !selectElement.isJsonObject()) {
            throw new IllegalArgumentException(where + ": missing 'select' object");
        }
        VariantSelector selector = VariantSelector.parse(selectElement.getAsJsonObject(), where + "/select");
        double chance = JsonHelper.optDouble(json, "chance", 1.0D);

        JsonElement dropsElement = json.get("drops");
        if (dropsElement == null || !dropsElement.isJsonArray()) {
            throw new IllegalArgumentException(where + ": missing 'drops' array");
        }
        JsonArray array = dropsElement.getAsJsonArray();
        List<DropEntry> drops = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            JsonElement element = array.get(index);
            String entryWhere = where + "/drops[" + index + "]";
            if (element == null || !element.isJsonObject()) {
                Logs.warn("{}: entry is not an object, skipping", entryWhere);
                continue;
            }
            try {
                drops.add(DropEntry.parse(element.getAsJsonObject(), entryWhere));
            } catch (RuntimeException exception) {
                Logs.warn("{}: {}", entryWhere, exception.getMessage());
            }
        }
        if (drops.isEmpty()) {
            throw new IllegalArgumentException(where + ": 'drops' has no usable entries");
        }
        return new DropRule(selector, chance, drops);
    }

    /**
     * Tests one mob against the rule.
     *
     * @param mob the dead mob
     * @return true when the rule applies
     */
    public boolean matches(Mob mob) {
        return selector.matches(mob);
    }
}
