package spellmob;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;
import spellmob.capability.SpellCasterAttachments;
import spellmob.config.SpellMobConfig;
import spellmob.data.SpellCasterReloadListener;

/**
 * Entry point of the mod.
 *
 * <p>SpellMob is a pure server-side, data-driven addon for Iron's Spells 'n Spellbooks: it reads
 * {@code data/<namespace>/spell_casters/<entity>.json} from data packs and gives the matching mobs
 * a set of castable spells. There are no blocks, items, entities or client-side assets.</p>
 */
@Mod(SpellMob.MOD_ID)
public final class SpellMob {

    public static final String MOD_ID = "spellmob";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SpellMob(IEventBus modEventBus, ModContainer modContainer) {
        SpellCasterAttachments.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, SpellMobConfig.SPEC, "spellmob-common.toml");
        NeoForge.EVENT_BUS.addListener(SpellMob::onAddReloadListener);
        LOGGER.info("SpellMob is loading: data packs under data/<namespace>/spell_casters are parsed on /reload");
    }

    private static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(SpellCasterReloadListener.INSTANCE);
    }
}
