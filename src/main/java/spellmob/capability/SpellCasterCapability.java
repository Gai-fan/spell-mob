package spellmob.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;
import spellmob.SpellMob;

/**
 * Declaration, serialization and attachment of {@link ISpellCaster}.
 *
 * <p>The capability is attached to mobs on the logical server only; client side entities never need
 * it because the mod does not render anything.</p>
 */
public final class SpellCasterCapability {

    private SpellCasterCapability() {
    }

    /**
     * Returns the capability instance.
     *
     * <p>Resolved lazily because {@link net.minecraftforge.common.capabilities.CapabilityManager#get}
     * must not run before the mod event bus registered the capability.</p>
     *
     * @return the registered capability
     */
    public static Capability<ISpellCaster> capability() {
        return Holder.CAPABILITY;
    }

    /**
     * Creates the capability instance. Called from {@code RegisterCapabilitiesEvent}.
     *
     * @param event the forge registration event
     */
    public static void register(RegisterCapabilitiesEvent event) {
        event.register(ISpellCaster.class);
    }

    /**
     * Attaches the capability to server side mobs.
     *
     * @param event the forge attachment event
     */
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof Mob mob) || mob.level() == null || mob.level().isClientSide()) {
            return;
        }
        Provider provider = new Provider(mob);
        event.addCapability(new ResourceLocation(SpellMob.MOD_ID, "caster"), provider);
        event.addListener(provider::invalidate);
    }

    /** Capability provider holding one {@link SpellCaster} per mob. */
    public static final class Provider implements ICapabilitySerializable<CompoundTag> {

        private final SpellCaster caster;
        private final LazyOptional<ISpellCaster> optional;

        private Provider(Mob mob) {
            this.caster = new SpellCaster(mob);
            this.optional = LazyOptional.of(() -> this.caster);
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
            if (capability != null && capability == capability()) {
                return this.optional.cast();
            }
            return LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return this.caster.save();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            this.caster.load(tag);
        }

        private void invalidate() {
            this.optional.invalidate();
        }
    }

    /** Lazily resolved capability holder. */
    private static final class Holder {

        private static final Capability<ISpellCaster> CAPABILITY =
                CapabilityManager.get(new CapabilityToken<>() {
                });

        private Holder() {
        }
    }
}
