package spellmob.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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

    public static int optInt(JsonObject object, String key, int fallback) {        JsonElement element = object.get(key);
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
}
