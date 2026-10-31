package spellmob.data;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import spellmob.goal.CastGoalSync;
import spellmob.util.Logs;

/**
 * Loads {@code data/<namespace>/spell_casters/<entity type>.json}.
 *
 * <p>The file path is the entity type id, so {@code data/minecraft/spell_casters/zombie.json}
 * configures {@code minecraft:zombie}.</p>
 */
public final class SpellCasterReloadListener extends SimpleJsonResourceReloadListener {

    private static final String DIRECTORY = "spell_casters";

    public static final SpellCasterReloadListener INSTANCE = new SpellCasterReloadListener();

    private SpellCasterReloadListener() {
        // The Gson instance is built here on purpose. A static field declared next to INSTANCE
        // would still be null when INSTANCE is created, because static initialisers run in
        // declaration order: super(null, ...) makes SimpleJsonResourceReloadListener crash with a
        // NullPointerException inside GsonHelper as soon as a spell_casters file exists.
        super(new GsonBuilder().setLenient().create(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, SpellCasterRules> parsed = new LinkedHashMap<>();
        objects.forEach((id, element) -> {
            if (!element.isJsonObject()) {
                Logs.warn("{}: file is not a json object, skipping", id);
                return;
            }
            try {
                SpellCasterRules rules = SpellCasterRules.parse(element.getAsJsonObject(), id.toString());
                if (rules != null) {
                    parsed.put(id, rules);
                }
            } catch (RuntimeException exception) {
                Logs.warn("Dropping spell caster {}: {}", id, exception.getMessage());
            }
        });

        SpellCasterRegistry.replace(parsed);
        CastGoalSync.requestResync();
        Logs.info("Loaded {} spell caster definition(s)", parsed.size());
    }
}
