package spellmob.util;

import spellmob.SpellMob;
import spellmob.config.SpellMobConfig;

/** Small logging facade so that debug output is controlled by the server config. */
public final class Logs {

    private Logs() {
    }

    public static void debug(String message, Object... args) {
        if (SpellMobConfig.debugLogging()) {
            SpellMob.LOGGER.info("[SpellMob] " + message, args);
        }
    }

    public static void info(String message, Object... args) {
        SpellMob.LOGGER.info("[SpellMob] " + message, args);
    }

    public static void warn(String message, Object... args) {
        SpellMob.LOGGER.warn("[SpellMob] " + message, args);
    }

    public static void error(String message, Throwable throwable) {
        SpellMob.LOGGER.error("[SpellMob] " + message, throwable);
    }
}
