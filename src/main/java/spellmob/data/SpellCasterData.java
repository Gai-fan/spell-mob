package spellmob.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import spellmob.util.Logs;

/**
 * Everything one entity type knows how to cast.
 *
 * @param goals    periodic spells, evaluated by the goal selector
 * @param onHurt   cast when the mob is damaged
 * @param onTarget cast when the mob acquires a new attack target
 * @param onDeath  cast by the dying mob, forcefully and without a cast time
 * @param onKill   cast by the killer right after it killed a living entity
 */
public record SpellCasterData(List<SpellCastDefinition> goals, @Nullable SpellCastDefinition onHurt,
                              @Nullable SpellCastDefinition onTarget, @Nullable SpellCastDefinition onDeath,
                              @Nullable SpellCastDefinition onKill) {

    /** A configuration that casts nothing. */
    public static final SpellCasterData EMPTY = new SpellCasterData(List.of(), null, null, null, null);

    public SpellCasterData {
        goals = List.copyOf(goals);
    }

    /** @return true when this configuration would not add a single spell */
    public boolean isEmpty() {
        return goals.isEmpty() && onHurt == null && onTarget == null && onDeath == null && onKill == null;
    }

    /**
     * Appends a species default to this variant configuration.
     *
     * <p>Goals are concatenated (this variant first), while triggers fall back to the default only
     * when this variant does not define them.</p>
     *
     * @param fallback the species default, may be null
     * @return the combined configuration
     */
    public SpellCasterData mergeWith(@Nullable SpellCasterData fallback) {
        if (fallback == null || fallback.isEmpty()) {
            return this;
        }
        List<SpellCastDefinition> merged = new ArrayList<>(goals);
        merged.addAll(fallback.goals());
        return new SpellCasterData(merged,
                onHurt != null ? onHurt : fallback.onHurt(),
                onTarget != null ? onTarget : fallback.onTarget(),
                onDeath != null ? onDeath : fallback.onDeath(),
                onKill != null ? onKill : fallback.onKill());
    }

    /**
     * Parses one {@code spell_casters} file.
     *
     * <p>Individual entries that fail to parse are dropped with a warning so that one typo does not
     * invalidate the whole file.</p>
     *
     * @param json  the file contents
     * @param where file path, used for log messages
     * @return the parsed data, or {@code null} when the file does not look like a SpellMob
     * definition at all (other mods use the same folder name for their own formats)
     */
    public static @Nullable SpellCasterData parse(JsonObject json, String where) {
        if (!json.has("goals") && !json.has("onHurt") && !json.has("onTarget")
                && !json.has("onDeath") && !json.has("onKill")) {
            return null;
        }
        List<SpellCastDefinition> goals = new ArrayList<>();
        JsonElement goalsElement = json.get("goals");
        if (goalsElement != null && goalsElement.isJsonArray()) {
            JsonArray array = goalsElement.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                JsonElement element = array.get(index);
                String entryWhere = where + "/goals[" + index + "]";
                if (!element.isJsonObject()) {
                    Logs.warn("{}: entry is not an object, skipping", entryWhere);
                    continue;
                }
                try {
                    goals.add(SpellCastDefinition.parse(element.getAsJsonObject(), entryWhere));
                } catch (RuntimeException exception) {
                    Logs.warn("{}: {}", entryWhere, exception.getMessage());
                }
            }
        }

        SpellCastDefinition onHurt = parseOptional(json, "onHurt", where);
        SpellCastDefinition onTarget = parseOptional(json, "onTarget", where);
        SpellCastDefinition onDeath = parseOptional(json, "onDeath", where);
        SpellCastDefinition onKill = parseOptional(json, "onKill", where);
        return new SpellCasterData(goals, onHurt, onTarget, onDeath, onKill);
    }

    private static @Nullable SpellCastDefinition parseOptional(JsonObject json, String key, String where) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        String entryWhere = where + "/" + key;
        if (!element.isJsonObject()) {
            Logs.warn("{}: entry is not an object, skipping", entryWhere);
            return null;
        }
        try {
            return SpellCastDefinition.parse(element.getAsJsonObject(), entryWhere);
        } catch (RuntimeException exception) {
            Logs.warn("{}: {}", entryWhere, exception.getMessage());
            return null;
        }
    }
}
