package spellmob.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import spellmob.util.JsonHelper;
import spellmob.util.Logs;

/**
 * Parses the config based species rules ({@code filters.extraRules}).
 *
 * <p>Each entry is one JSON object on a single line, using the same schema as a data pack file
 * except that the entity type is given by an {@code entity} field instead of the file name. A bad
 * entry is dropped with a warning so that one typo cannot take the whole config down.</p>
 */
public final class ConfigRuleLoader {

    private ConfigRuleLoader() {
    }

    /**
     * Parses all configured entries.
     *
     * @param entries the raw config strings
     * @return the parsed rules, keyed by entity type id, in config order
     */
    public static Map<ResourceLocation, SpellCasterData> parse(List<String> entries) {
        Map<ResourceLocation, SpellCasterData> rules = new LinkedHashMap<>();
        for (int index = 0; index < entries.size(); index++) {
            String where = "config extraRules[" + index + "]";
            String entry = entries.get(index).trim();
            if (entry.isEmpty()) {
                continue;
            }
            try {
                JsonElement element = JsonParser.parseString(entry);
                if (!element.isJsonObject()) {
                    Logs.warn("{}: entry is not a JSON object, skipping", where);
                    continue;
                }
                JsonObject json = element.getAsJsonObject();
                String rawId = JsonHelper.requireString(json, "entity", where);
                ResourceLocation id = ResourceLocation.tryParse(rawId);
                if (id == null) {
                    Logs.warn("{}: invalid entity id '{}', skipping", where, rawId);
                    continue;
                }
                json.remove("entity");
                SpellCasterData data = SpellCasterData.parse(json, where);
                if (data == null) {
                    Logs.warn("{}: needs at least one of 'goals', 'onHurt', 'onTarget', 'onDeath' or 'onKill', skipping", where);
                    continue;
                }
                if (rules.putIfAbsent(id, data) != null) {
                    Logs.warn("{}: duplicate rule for {}, keeping the first one", where, id);
                }
            } catch (RuntimeException exception) {
                Logs.warn("{}: {}", where, exception.getMessage());
            }
        }
        return rules;
    }
}
