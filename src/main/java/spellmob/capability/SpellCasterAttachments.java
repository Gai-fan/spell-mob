package spellmob.capability;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;
import spellmob.SpellMob;

/**
 * Registers and resolves the per mob {@link SpellCaster} data attachment.
 *
 * <p>NeoForge replaced the old capability system with data attachments, which is a much better fit
 * here: the attachment is created lazily the first time it is requested, so nothing is allocated for
 * the mobs that never cast, and it is serialized with the entity automatically.</p>
 */
public final class SpellCasterAttachments {

    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SpellMob.MOD_ID);

    private static final DeferredHolder<AttachmentType<?>, AttachmentType<SpellCaster>> SPELL_CASTER =
            TYPES.register("caster", () -> AttachmentType
                    .<SpellCaster>builder(holder -> new SpellCaster((Mob) holder))
                    .serialize(new Serializer())
                    .build());

    private SpellCasterAttachments() {
    }

    /**
     * Registers the attachment type on the mod event bus.
     *
     * @param modEventBus the mod event bus
     */
    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }

    /**
     * Returns the caster state of a mob, creating it if needed.
     *
     * <p>Client side mobs get {@code null}: the mod is server authoritative and never needs the
     * cooldown bookkeeping on the client.</p>
     *
     * @param mob the mob
     * @return the caster state, or {@code null} on the client
     */
    public static @Nullable ISpellCaster get(Mob mob) {
        if (mob.level() == null || mob.level().isClientSide()) {
            return null;
        }
        return mob.getData(SPELL_CASTER.get());
    }

    /**
     * Returns the caster state of a mob if it already exists, without creating it.
     *
     * <p>Use this on paths that only read state (unloading, interruption, readiness checks): a hurt
     * event on a mob that never cast anything must not allocate the attachment.</p>
     *
     * @param mob the mob
     * @return the existing caster state, or {@code null}
     */
    public static @Nullable ISpellCaster peek(Mob mob) {
        if (mob.level() == null || mob.level().isClientSide()) {
            return null;
        }
        return mob.getExistingDataOrNull(SPELL_CASTER.get());
    }

    /** Serializes the cooldown map with the entity. */
    private static final class Serializer implements IAttachmentSerializer<CompoundTag, SpellCaster> {

        @Override
        public SpellCaster read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
            SpellCaster caster = new SpellCaster((Mob) holder);
            caster.load(tag);
            return caster;
        }

        @Override
        public CompoundTag write(SpellCaster caster, HolderLookup.Provider provider) {
            return caster.save();
        }
    }
}
