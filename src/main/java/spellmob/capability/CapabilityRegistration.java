package spellmob.capability;

import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spellmob.SpellMob;

/** Registers the mod capability on the mod event bus. */
@Mod.EventBusSubscriber(modid = SpellMob.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CapabilityRegistration {

    private CapabilityRegistration() {
    }

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        SpellCasterCapability.register(event);
    }
}
