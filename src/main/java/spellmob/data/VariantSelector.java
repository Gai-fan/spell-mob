package spellmob.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;
import spellmob.util.JsonHelper;

/**
 * Decides whether one mob belongs to a variant.
 *
 * <p>A selector is a conjunction: every criterion that is present has to match. Criteria only ever
 * look at the mob itself, so the same selector works for any entity type.</p>
 *
 * <ul>
 *     <li>{@code name} - exact custom name (a mob without a name never matches)</li>
 *     <li>{@code nameRegex} - regular expression matched against the custom name, full match</li>
 *     <li>{@code tags} - scoreboard tags, all of them must be present</li>
 *     <li>{@code healthBelow} - current health below this fraction of the maximum health</li>
 *     <li>{@code healthAbove} - current health above this fraction of the maximum health</li>
 * </ul>
 *
 * <p>The health criteria turn variants into phases: put the low health variant first and it takes
 * over when the mob drops below the threshold, with the species default acting as the last phase.</p>
 */
public record VariantSelector(@Nullable String name, @Nullable Pattern namePattern, List<String> tags,
                              @Nullable Double healthBelow, @Nullable Double healthAbove) {

    public VariantSelector {
        tags = List.copyOf(tags);
    }

    /**
     * Parses a {@code select} object.
     *
     * @param json  the object
     * @param where file path for error messages
     * @return the selector
     * @throws IllegalArgumentException when no criterion is configured or a value is malformed
     */
    public static VariantSelector parse(JsonObject json, String where) {
        String name = JsonHelper.optString(json, "name", null);
        String regex = JsonHelper.optString(json, "nameRegex", null);

        Pattern pattern = null;
        if (regex != null) {
            try {
                pattern = Pattern.compile(regex);
            } catch (PatternSyntaxException exception) {
                throw new IllegalArgumentException(where + ": invalid 'nameRegex': " + exception.getDescription());
            }
        }

        List<String> tags = new ArrayList<>();
        JsonElement tagsElement = json.get("tags");
        if (tagsElement != null && !tagsElement.isJsonNull()) {
            if (!tagsElement.isJsonArray()) {
                throw new IllegalArgumentException(where + ": 'tags' must be an array of strings");
            }
            for (JsonElement element : tagsElement.getAsJsonArray()) {
                if (element == null || element.isJsonNull() || !element.isJsonPrimitive()
                        || !element.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException(where + ": 'tags' must be an array of strings");
                }
                tags.add(element.getAsString());
            }
        }

        Double healthBelow = optFraction(json, "healthBelow", where);
        Double healthAbove = optFraction(json, "healthAbove", where);
        if (healthBelow != null && healthAbove != null && healthBelow <= healthAbove) {
            throw new IllegalArgumentException(
                    where + ": 'healthBelow' must be greater than 'healthAbove', otherwise no health matches");
        }

        if (name == null && pattern == null && tags.isEmpty() && healthBelow == null && healthAbove == null) {
            throw new IllegalArgumentException(where
                    + ": needs at least one of 'name', 'nameRegex', 'tags', 'healthBelow' or 'healthAbove'");
        }
        return new VariantSelector(name, pattern, tags, healthBelow, healthAbove);
    }

    private static @Nullable Double optFraction(JsonObject json, String key, String where) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(where + ": '" + key + "' must be a number between 0 and 1");
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(where + ": '" + key + "' must be a finite number");
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    /**
     * Tests one mob against every configured criterion.
     *
     * @param mob the candidate
     * @return true when all criteria match
     */
    public boolean matches(Mob mob) {
        if (name != null || namePattern != null) {
            if (!mob.hasCustomName()) {
                return false;
            }
            String customName = mob.getName().getString();
            if (name != null && !name.equals(customName)) {
                return false;
            }
            if (namePattern != null && !namePattern.matcher(customName).matches()) {
                return false;
            }
        }
        if (!tags.isEmpty() && !mob.getTags().containsAll(tags)) {
            return false;
        }
        if (healthBelow != null || healthAbove != null) {
            double fraction = healthFraction(mob);
            if (healthBelow != null && fraction >= healthBelow) {
                return false;
            }
            if (healthAbove != null && fraction <= healthAbove) {
                return false;
            }
        }
        return true;
    }

    private static double healthFraction(Mob mob) {
        // Capabilities are gathered inside Entity#<init>, so a selector can be evaluated on a mob
        // whose AttributeMap does not exist yet. Treat that as full health instead of crashing.
        if (mob.getAttributes() == null) {
            return 1.0D;
        }
        float maxHealth = mob.getMaxHealth();
        if (maxHealth <= 0.0F) {
            return 0.0D;
        }
        return mob.getHealth() / (double) maxHealth;
    }

    /** @return a short human readable form, used by {@code /spellmob info} and logs */
    public String describe() {
        List<String> parts = new ArrayList<>(5);
        if (name != null) {
            parts.add("name=" + name);
        }
        if (namePattern != null) {
            parts.add("nameRegex=" + namePattern.pattern());
        }
        if (!tags.isEmpty()) {
            parts.add("tags=[" + String.join(", ", tags) + "]");
        }
        if (healthBelow != null) {
            parts.add(String.format(Locale.ROOT, "health<%.2f", healthBelow));
        }
        if (healthAbove != null) {
            parts.add(String.format(Locale.ROOT, "health>%.2f", healthAbove));
        }
        return parts.isEmpty() ? "any mob" : String.join(" & ", parts);
    }
}
