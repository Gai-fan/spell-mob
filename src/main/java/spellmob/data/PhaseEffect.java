package spellmob.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;
import org.jetbrains.annotations.Nullable;
import spellmob.util.JsonHelper;

/**
 * The one shot "entering this phase" show of a variant.
 *
 * <p>Written as {@code onPhase} on the variant that is entered, so a two phase boss puts one on the
 * second variant. Every field is optional: an empty object means "replay the goal rebuild, do
 * nothing visible".</p>
 *
 * @param title             big screen title, {@code %s} is replaced with the caster name
 * @param subtitle          subtitle under the title
 * @param sound             sound played once when the phase starts
 * @param shake             camera shake, {@code null} means none
 * @param burst             ground particle burst, {@code null} means none
 * @param knockback         blocks nearby players are pushed away, {@code 0} disables it
 * @param invulnerableTicks ticks the caster cannot be hurt, {@code 0} disables it
 * @param freezeTicks       ticks the caster stands still; when negative it follows
 *                          {@link #invulnerableTicks}
 * @param summon            reinforcements spawned around the caster, {@code null} means none
 * @param bossBarColor      new boss bar colour, {@code null} keeps the current one
 * @param bossBarName       new boss bar name, {@code null} keeps the current one
 * @param weather           weather the level is switched to; only applied when the server allows it
 * @param time              time of day the level is switched to; same gate as {@link #weather}
 * @param phaseTicks        length of the whole show in ticks
 * @param replay            {@code true} allows the same phase to play again after the boss heals
 */
public record PhaseEffect(@Nullable String title, @Nullable String subtitle,
                          @Nullable ResourceLocation sound,
                          @Nullable Shake shake, @Nullable Burst burst,
                          double knockback, int invulnerableTicks, int freezeTicks,
                          @Nullable Summon summon,
                          @Nullable BossEvent.BossBarColor bossBarColor, @Nullable String bossBarName,
                          @Nullable Weather weather, @Nullable Time time,
                          int phaseTicks, boolean replay) {

    /** Default length of the invulnerable window, in ticks. */
    public static final int DEFAULT_INVULNERABLE_TICKS = 40;
    /** Default length of the whole show, in ticks. */
    public static final int DEFAULT_PHASE_TICKS = 40;
    /** Default shake length, in ticks. */
    public static final int DEFAULT_SHAKE_TICKS = 15;
    /** Default shake radius, in blocks. */
    public static final float DEFAULT_SHAKE_RADIUS = 16.0F;
    /** Default burst radius, in blocks. */
    public static final double DEFAULT_BURST_RADIUS = 4.0D;

    private static final Set<String> KEYS = Set.of("title", "subtitle", "sound", "shake", "burst",
            "knockback", "invulnerableTicks", "freezeTicks", "summon", "bossBarColor", "bossBarName",
            "weather", "time", "phaseTicks", "replay", "_comment");

    public PhaseEffect {
        knockback = Math.max(0.0D, Math.min(16.0D, knockback));
        invulnerableTicks = Math.max(0, Math.min(1200, invulnerableTicks));
        phaseTicks = Math.max(10, Math.min(1200, phaseTicks));
        if (freezeTicks < 0) {
            freezeTicks = invulnerableTicks;
        }
        freezeTicks = Math.max(0, Math.min(1200, freezeTicks));
    }

    /**
     * @return ticks the caster stands still, following the invulnerable window when not set
     */
    public int effectiveFreezeTicks() {
        return freezeTicks;
    }

    /**
     * Parses an {@code onPhase} object.
     *
     * @param json  the object
     * @param where path prefix for error messages
     * @return the parsed effect
     * @throws IllegalArgumentException when the object is malformed or carries unknown keys
     */
    public static PhaseEffect parse(JsonObject json, String where) {
        for (String key : json.keySet()) {
            if (!KEYS.contains(key)) {
                throw new IllegalArgumentException(where + ": unknown key '" + key + "'");
            }
        }
        String title = JsonHelper.optString(json, "title", null);
        String subtitle = JsonHelper.optString(json, "subtitle", null);
        String soundId = JsonHelper.optString(json, "sound", null);
        ResourceLocation sound = null;
        if (soundId != null) {
            sound = ResourceLocation.tryParse(soundId);
            if (sound == null) {
                throw new IllegalArgumentException(where + ": invalid sound id '" + soundId + "'");
            }
        }
        Shake shake = null;
        if (json.has("shake") && !json.get("shake").isJsonNull()) {
            JsonElement element = json.get("shake");
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException(where + "/shake: must be an object");
            }
            shake = Shake.parse(element.getAsJsonObject(), where + "/shake");
        }
        Burst burst = null;
        if (json.has("burst") && !json.get("burst").isJsonNull()) {
            JsonElement element = json.get("burst");
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException(where + "/burst: must be an object");
            }
            burst = Burst.parse(element.getAsJsonObject(), where + "/burst");
        }
        Summon summon = null;
        if (json.has("summon") && !json.get("summon").isJsonNull()) {
            JsonElement element = json.get("summon");
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException(where + "/summon: must be an object");
            }
            summon = Summon.parse(element.getAsJsonObject(), where + "/summon");
        }
        BossEvent.BossBarColor colour = parseColour(
                JsonHelper.optString(json, "bossBarColor", null), where);
        Weather weather = parseWeather(JsonHelper.optString(json, "weather", null), where);
        Time time = parseTime(JsonHelper.optString(json, "time", null), where);
        return new PhaseEffect(title, subtitle, sound, shake, burst,
                JsonHelper.optDouble(json, "knockback", 0.0D),
                JsonHelper.optInt(json, "invulnerableTicks", DEFAULT_INVULNERABLE_TICKS),
                JsonHelper.optInt(json, "freezeTicks", -1),
                summon, colour, JsonHelper.optString(json, "bossBarName", null),
                weather, time,
                JsonHelper.optInt(json, "phaseTicks", DEFAULT_PHASE_TICKS),
                JsonHelper.optBoolean(json, "replay", false));
    }

    private static @Nullable BossEvent.BossBarColor parseColour(@Nullable String value, String where) {
        if (value == null) {
            return null;
        }
        try {
            return BossEvent.BossBarColor.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(where + "/bossBarColor: unknown colour '" + value + "'");
        }
    }

    private static @Nullable Weather parseWeather(@Nullable String value, String where) {
        if (value == null) {
            return null;
        }
        for (Weather weather : Weather.values()) {
            if (weather.id().equalsIgnoreCase(value)) {
                return weather;
            }
        }
        throw new IllegalArgumentException(where + "/weather: unknown weather '" + value + "'");
    }

    private static @Nullable Time parseTime(@Nullable String value, String where) {
        if (value == null) {
            return null;
        }
        for (Time time : Time.values()) {
            if (time.id().equalsIgnoreCase(value)) {
                return time;
            }
        }
        throw new IllegalArgumentException(where + "/time: unknown time of day '" + value + "'");
    }

    /**
     * Camera shake settings.
     *
     * @param duration ticks the shake lasts
     * @param radius   players within this distance of the caster are shaken
     */
    public record Shake(int duration, float radius) {

        public Shake {
            duration = Math.max(1, Math.min(200, duration));
            radius = Math.max(1.0F, Math.min(64.0F, radius));
        }

        private static Shake parse(JsonObject json, String where) {
            for (String key : json.keySet()) {
                if (!key.equals("duration") && !key.equals("radius") && !key.equals("_comment")) {
                    throw new IllegalArgumentException(where + ": unknown key '" + key + "'");
                }
            }
            return new Shake(JsonHelper.optInt(json, "duration", DEFAULT_SHAKE_TICKS),
                    (float) JsonHelper.optDouble(json, "radius", DEFAULT_SHAKE_RADIUS));
        }
    }

    /**
     * Ground particle burst settings.
     *
     * @param radius how far the burst reaches, in blocks
     * @param colour {@code null} or {@code auto} paints it in the school colour of the first spell,
     *               otherwise a {@code #RRGGBB} value
     */
    public record Burst(double radius, @Nullable String colour) {

        public Burst {
            radius = Math.max(1.0D, Math.min(16.0D, radius));
        }

        /**
         * @return true when the burst should use the school colour instead of {@link #colour()}
         */
        public boolean autoColour() {
            return colour == null || colour.isBlank() || colour.equalsIgnoreCase("auto");
        }

        private static Burst parse(JsonObject json, String where) {
            for (String key : json.keySet()) {
                if (!key.equals("radius") && !key.equals("color") && !key.equals("colour")
                        && !key.equals("_comment")) {
                    throw new IllegalArgumentException(where + ": unknown key '" + key + "'");
                }
            }
            String colour = JsonHelper.optString(json, "color", JsonHelper.optString(json, "colour", null));
            return new Burst(JsonHelper.optDouble(json, "radius", DEFAULT_BURST_RADIUS), colour);
        }
    }

    /**
     * Reinforcements spawned when the phase starts.
     *
     * @param entity the entity type to spawn
     * @param count  how many
     * @param radius spawn radius around the caster, in blocks
     * @param tags   scoreboard tags added to the spawned mobs
     */
    public record Summon(ResourceLocation entity, int count, double radius, List<String> tags) {

        public Summon {
            count = Math.max(1, Math.min(16, count));
            radius = Math.max(1.0D, Math.min(16.0D, radius));
            tags = List.copyOf(tags);
        }

        private static Summon parse(JsonObject json, String where) {
            for (String key : json.keySet()) {
                if (!key.equals("entity") && !key.equals("count") && !key.equals("radius")
                        && !key.equals("tags") && !key.equals("_comment")) {
                    throw new IllegalArgumentException(where + ": unknown key '" + key + "'");
                }
            }
            String id = JsonHelper.requireString(json, "entity", where);
            ResourceLocation entity = ResourceLocation.tryParse(id);
            if (entity == null) {
                throw new IllegalArgumentException(where + ": invalid entity id '" + id + "'");
            }
            List<String> tags = new ArrayList<>();
            JsonElement tagsElement = json.get("tags");
            if (tagsElement != null && !tagsElement.isJsonNull()) {
                if (!tagsElement.isJsonArray()) {
                    throw new IllegalArgumentException(where + "/tags: must be an array");
                }
                JsonArray array = tagsElement.getAsJsonArray();
                for (JsonElement tag : array) {
                    if (!tag.isJsonPrimitive() || !tag.getAsJsonPrimitive().isString()) {
                        throw new IllegalArgumentException(where + "/tags: entries must be strings");
                    }
                    tags.add(tag.getAsString());
                }
            }
            return new Summon(entity, JsonHelper.optInt(json, "count", 2),
                    JsonHelper.optDouble(json, "radius", 3.0D), tags);
        }
    }

    /** Weather the level can be forced into during a phase. */
    public enum Weather {
        CLEAR("clear", 0, 0, false, false),
        RAIN("rain", 12000, 0, true, false),
        THUNDER("thunder", 12000, 12000, true, true);

        private final String id;
        private final int clearTime;
        private final int weatherTime;
        private final boolean raining;
        private final boolean thundering;

        Weather(String id, int clearTime, int weatherTime, boolean raining, boolean thundering) {
            this.id = id;
            this.clearTime = clearTime;
            this.weatherTime = weatherTime;
            this.raining = raining;
            this.thundering = thundering;
        }

        public String id() {
            return id;
        }

        public int clearTime() {
            return clearTime;
        }

        public int weatherTime() {
            return weatherTime;
        }

        public boolean raining() {
            return raining;
        }

        public boolean thundering() {
            return thundering;
        }
    }

    /** Time of day the level can be forced into during a phase. */
    public enum Time {
        DAY("day", 1000L),
        NOON("noon", 6000L),
        NIGHT("night", 13000L),
        MIDNIGHT("midnight", 18000L);

        private final String id;
        private final long dayTime;

        Time(String id, long dayTime) {
            this.id = id;
            this.dayTime = dayTime;
        }

        public String id() {
            return id;
        }

        public long dayTime() {
            return dayTime;
        }
    }
}
