package spellmob.events;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import spellmob.SpellMob;
import spellmob.config.SpellMobConfig;
import spellmob.data.ConfigRuleLoader;
import spellmob.data.SpellCasterRegistry;
import spellmob.goal.CastGoalSync;

/**
 * Re-applies the config based species rules whenever the config is loaded or reloaded.
 *
 * <p>Data pack rules are handled by {@link spellmob.data.SpellCasterReloadListener}; this class only
 * keeps the config side in sync and asks for the already loaded mobs to be re-evaluated.</p>
 */
@Mod.EventBusSubscriber(modid = SpellMob.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ConfigEvents {

    private ConfigEvents() {
    }

    @SubscribeEvent
    public static void onConfigLoading(ModConfigEvent.Loading event) {
        apply(event);
    }

    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        apply(event);
    }

    private static void apply(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SpellMobConfig.SPEC) {
            return;
        }
        reapply();
    }

    /**
     * Rebuilds the config side rules from the current config values and asks loaded mobs to rebuild
     * their goals. Safe to call more than once.
     */
    public static void reapply() {
        try {
            SpellCasterRegistry.replaceConfig(ConfigRuleLoader.parse(SpellMobConfig.extraRules()));
            CastGoalSync.requestResync();
        } catch (RuntimeException exception) {
            SpellMob.LOGGER.error("Failed to apply config spell caster rules", exception);
        }
    }
}
