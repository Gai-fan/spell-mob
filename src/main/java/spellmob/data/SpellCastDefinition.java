package spellmob.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import spellmob.config.SpellMobConfig;
import spellmob.util.JsonHelper;

/**
 * One fully parsed cast instruction: which spell, at which level, aimed at what.
 *
 * <p>Instances are immutable and shared by every mob that uses the same data pack entry.</p>
 *
 * @param spellId           id of the Iron's Spellbooks spell
 * @param level             spell level, clamped to the spell's maximum
 * @param cooldownTicks     cooldown in ticks, negative means "use the spell's own cooldown"
 * @param priority          goal priority, clamped to 0..9
 * @param range             maximum distance to the target, in blocks
 * @param target            what to aim at
 * @param castTimeTicks     cast duration in ticks, negative means "use the spell's own cast time"
 * @param checkInterval     how often the goal re-evaluates its availability, in ticks
 * @param conditions        extra gates for the cast
 * @param interruptOnDamage whether taking damage cancels this cast
 * @param cooldownGroup     optional shared cooldown group; spells in one group share a cooldown
 * @param chance            probability (0..1) of actually attempting the cast
 * @param repeatOnCast      whether a channeled (CONTINUOUS) spell re-runs its onCast every 10 ticks
 * @param where             human readable origin of this entry, used in log messages
 */
public record SpellCastDefinition(ResourceLocation spellId, int level, int cooldownTicks, int priority, double range,
                                  TargetSpec target, int castTimeTicks, int checkInterval, CastConditions conditions,
                                  boolean interruptOnDamage, @Nullable String cooldownGroup, double chance,
                                  boolean repeatOnCast, String where) {

    /** Shape of {@code cooldownGroup}: short, lowercase and safe to use as a cooldown map key. */
    private static final Pattern COOLDOWN_GROUP = Pattern.compile("[a-z0-9_.-]{1,32}");

    public SpellCastDefinition {
        level = Math.max(1, Math.min(10, level));
        priority = Math.max(0, Math.min(9, priority));
        checkInterval = Math.max(1, Math.min(200, checkInterval));
        range = Math.max(1.0D, range);
        chance = Math.max(0.0D, Math.min(1.0D, chance));
        if (cooldownGroup != null) {
            cooldownGroup = cooldownGroup.trim();
            if (cooldownGroup.isEmpty()) {
                cooldownGroup = null;
            }
        }
        if (target == null) {
            target = TargetSpec.attackTarget();
        }
        if (conditions == null) {
            conditions = CastConditions.NONE;
        }
    }

    /**
     * Resolves the spell from the Iron's Spellbooks registry.
     *
     * @return the spell, or null when it is not registered (for example another mod was removed)
     */
    public AbstractSpell spell() {
        return SpellRegistry.getSpell(spellId);
    }

    /** @return the range actually used for targeting, capped by the server config */
    public double effectiveRange() {
        return Math.min(range, SpellMobConfig.maxRange());
    }

    /**
     * Parses one cast object.
     *
     * @param json  the json object
     * @param where path of the object inside the data pack, used for error messages
     * @return the parsed definition
     * @throws IllegalArgumentException when the object is not usable
     */
    public static SpellCastDefinition parse(JsonObject json, String where) {
        String rawSpell = JsonHelper.requireString(json, "spell", where);
        ResourceLocation spellId = ResourceLocation.tryParse(rawSpell);
        if (spellId == null) {
            throw new IllegalArgumentException(where + ": invalid spell id '" + rawSpell + "'");
        }
        AbstractSpell spell = SpellRegistry.getSpell(spellId);
        if (spell == null || !spellId.toString().equals(spell.getSpellId())) {
            throw new IllegalArgumentException(where + ": unknown spell '" + rawSpell + "'");
        }
        if (!spell.isEnabled()) {
            throw new IllegalArgumentException(where + ": spell '" + rawSpell + "' is disabled in the Iron's Spellbooks config");
        }

        String rawGroup = JsonHelper.optString(json, "cooldownGroup", null);
        if (rawGroup != null) {
            rawGroup = rawGroup.trim();
            if (!COOLDOWN_GROUP.matcher(rawGroup).matches()) {
                throw new IllegalArgumentException(where + ": invalid 'cooldownGroup' '" + rawGroup
                        + "', expected 1-32 characters of a-z, 0-9, '_', '.' or '-'");
            }
        }

        int level = Math.max(1, Math.min(spell.getMaxLevel(), JsonHelper.optInt(json, "level", 1)));
        return new SpellCastDefinition(
                spellId,
                level,
                JsonHelper.optInt(json, "cooldown", -1),
                JsonHelper.optInt(json, "priority", 1),
                JsonHelper.optDouble(json, "range", 16.0D),
                parseTarget(json, where),
                JsonHelper.optInt(json, "castTime", -1),
                JsonHelper.optInt(json, "checkInterval", 10),
                parseConditions(json, where),
                JsonHelper.optBoolean(json, "interruptOnDamage", false),
                rawGroup,
                JsonHelper.optDouble(json, "chance", 1.0D),
                JsonHelper.optBoolean(json, "repeatOnCast", true),
                where);
    }

    private static TargetSpec parseTarget(JsonObject json, String where) {
        JsonElement element = json.get("target");
        if (element == null || element.isJsonNull()) {
            return TargetSpec.attackTarget();
        }
        if (element.isJsonPrimitive()) {
            TargetType type = requireType(element.getAsString(), where);
            return new TargetSpec(type, true, PositionMode.RELATIVE, Vec3.ZERO, 1.0D, TargetSort.NEAREST);
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(where + ": 'target' must be a string or an object");
        }

        JsonObject object = element.getAsJsonObject();
        TargetType type = object.has("type") ? requireType(object.get("type").getAsString(), where)
                : TargetType.ATTACK_TARGET;
        PositionMode mode = PositionMode.RELATIVE;
        if (object.has("positionMode")) {
            mode = PositionMode.byName(object.get("positionMode").getAsString());
            if (mode == null) {
                throw new IllegalArgumentException(where + ": unknown positionMode '" + object.get("positionMode").getAsString() + "'");
            }
        }
        TargetSort sort = TargetSort.NEAREST;
        if (object.has("sort")) {
            String rawSort = object.get("sort").getAsString();
            sort = TargetSort.byName(rawSort);
            if (sort == null) {
                throw new IllegalArgumentException(where + ": unknown sort '" + rawSort + "'");
            }
        }
        return new TargetSpec(
                type,
                JsonHelper.optBoolean(object, "lineOfSight", true),
                mode,
                parseOffset(object.get("relativeOffset"), where),
                JsonHelper.optDouble(object, "maxHealthPercent", 1.0D),
                sort);
    }

    private static Vec3 parseOffset(JsonElement element, String where) {
        if (element == null || element.isJsonNull()) {
            return Vec3.ZERO;
        }
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException(where + ": 'relativeOffset' must be an array of three numbers");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() < 3) {
            throw new IllegalArgumentException(where + ": 'relativeOffset' must contain three numbers");
        }
        return new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
    }

    private static CastConditions parseConditions(JsonObject json, String where) {
        JsonElement element = json.get("conditions");
        if (element == null || element.isJsonNull()) {
            return CastConditions.NONE;
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(where + ": 'conditions' must be an object");
        }
        JsonObject object = element.getAsJsonObject();
        return new CastConditions(
                JsonHelper.optDouble(object, "healthBelow", 1.0D),
                JsonHelper.optDouble(object, "healthAbove", 0.0D),
                JsonHelper.optDouble(object, "minTargetDistance", 0.0D),
                JsonHelper.optDouble(object, "maxTargetDistance", Double.MAX_VALUE));
    }

    private static TargetType requireType(String raw, String where) {
        TargetType type = TargetType.byName(raw);
        if (type == null) {
            throw new IllegalArgumentException(where + ": unknown target type '" + raw + "'");
        }
        return type;
    }
}
