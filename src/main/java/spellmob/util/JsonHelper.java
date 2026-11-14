package spellmob.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** Defensive helpers for reading the data pack format. */
public final class JsonHelper {

    private JsonHelper() {
    }

    public static String requireString(JsonObject object, String key, String where) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(where + ": missing required string '" + key + "'");
        }
        return element.getAsString();
    }

    public static @Nullable String optString(JsonObject object, String key, @Nullable String fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("'" + key + "' must be a string");
        }
        return element.getAsString();
    }

    public static int optInt(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("'" + key + "' must be an integer");
        }
    }

    public static double optDouble(JsonObject object, String key, double fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsDouble();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("'" + key + "' must be a number");
        }
    }

    public static boolean optBoolean(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsBoolean();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("'" + key + "' must be a boolean");
        }
    }

    /**
     * Reads an optional boolean without inventing a default.
     *
     * @param object  the object
     * @param key     the key
     * @return the value, or {@code null} when the key is absent (three-state flags such as
     * {@code bossBar}, where "absent" means "use the config default")
     */
    public static @Nullable Boolean optBooleanOrNull(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.getAsBoolean();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("'" + key + "' must be a boolean");
        }
    }

    /**
     * Logs keys the parser does not understand.
     *
     * <p>The format stays forgiving on purpose: one typo should not throw away a whole boss. It
     * must not be silent either, otherwise {@code "cooldownTick": 100} looks like a mod bug
     * instead of a spelling mistake. Keys starting with {@code _} are treated as comments.</p>
     *
     * @param object  the object to inspect
     * @param where   path prefix used in the log line
     * @param allowed the keys the parser actually reads
     */
    public static void warnUnknown(JsonObject object, String where, String... allowed) {
        Set<String> known = new LinkedHashSet<>(java.util.List.of(allowed));
        for (String key : object.keySet()) {
            if (key.startsWith("_") || known.contains(key)) {
                continue;
            }
            Logs.warn("{}: unknown key '{}' was ignored", where, key);
        }
    }
}
