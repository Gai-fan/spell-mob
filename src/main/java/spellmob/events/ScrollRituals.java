package spellmob.events;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.item.Scroll;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import spellmob.capability.SpellCaster;
import spellmob.capability.SpellCasterCapability;
import spellmob.config.SpellMobConfig;
import spellmob.goal.CastGoalSync;
import spellmob.presentation.CastingPresentation;
import spellmob.presentation.RitualCircle;

/**
 * Turns a dropped spell scroll into a short learning ritual for a nearby owned pet.
 *
 * <p>A tossed scroll only starts something when it lands next to a tamed mob that carries the
 * learner tag. The ritual runs for a few seconds, draws a school coloured circle under the pet and
 * consumes the scroll when it completes. A blacklist hit or a cooldown only shows feedback and
 * leaves the scroll on the ground.</p>
 */
public final class ScrollRituals {

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Ritual> RITUALS = new HashMap<>();
    private static final double PICKUP_RADIUS = 2.5D;
    private static final double KEEP_DISTANCE = 3.5D;
    private static final int PENDING_TIMEOUT = 200;
    private static final int REJECT_TICKS = 40;

    private ScrollRituals() {
    }

    public static void onItemToss(ItemTossEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        ItemEntity item = event.getEntity();
        ItemStack stack = item.getItem();
        if (!(stack.getItem() instanceof Scroll) || !ISpellContainer.isSpellContainer(stack)) {
            return;
        }
        ResourceLocation spellId = scrollSpellId(stack);
        if (spellId == null) {
            return;
        }
        PENDING.put(item.getUUID(), new Pending(item.getUUID(), item.level().dimension(),
                event.getPlayer().getUUID(), item.level().getGameTime(), spellId,
                scrollSpellLevel(stack)));
    }

    public static void tick(MinecraftServer server) {
        if (!SpellMobConfig.scrollLearningEnabled()) {
            return;
        }
        Iterator<Map.Entry<UUID, Pending>> pending = PENDING.entrySet().iterator();
        while (pending.hasNext()) {
            Map.Entry<UUID, Pending> entry = pending.next();
            Pending data = entry.getValue();
            ServerLevel level = server.getLevel(data.dimension());
            if (level == null || !(level.getEntity(data.itemId()) instanceof ItemEntity item)) {
                pending.remove();
                continue;
            }
            long now = level.getGameTime();
            if (now - data.startTick() > PENDING_TIMEOUT || item.getItem().isEmpty()) {
                pending.remove();
                continue;
            }
            if (!item.onGround() && item.getAge() < 5) {
                continue;
            }
            TamableAnimal pet = findPet(level, item, data.thrower());
            if (pet == null) {
                continue;
            }
            pending.remove();
            begin(level, pet, item, data, now);
        }
        tickRituals(server);
    }

    public static void clear() {
        PENDING.clear();
        RITUALS.clear();
    }

    private static void begin(ServerLevel level, TamableAnimal pet, ItemEntity item, Pending data,
                              long now) {
        SpellCaster caster = casterOf(pet);
        if (caster == null) {
            return;
        }
        AbstractSpell spell = SpellRegistry.getSpell(data.spellId());
        if (spell == null || spell == SpellRegistry.none()) {
            return;
        }
        ServerPlayer owner = ownerPlayer(pet);
        long remaining = caster.scrollCooldownRemaining(now);
        if (remaining > 0L) {
            notify(owner, Component.translatable("spellmob.message.scroll_cooldown",
                    remaining / 1200L, (remaining / 20L) % 60L).withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (SpellMobConfig.isScrollBlacklisted(data.spellId())) {
            startRitual(level, pet, item, data.spellId(), data.level(), now, true);
            notify(owner, Component.translatable("spellmob.message.scroll_blacklisted")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (caster.learnedCount() >= SpellMobConfig.scrollMaxSpells()) {
            notify(owner, Component.translatable("spellmob.message.scroll_max")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (caster.learnedLevel(data.spellId()) >= data.level()) {
            notify(owner, Component.translatable("spellmob.message.scroll_known",
                    spell.getDisplayName(null)).withStyle(ChatFormatting.GRAY));
            return;
        }
        startRitual(level, pet, item, data.spellId(), data.level(), now, false);
    }

    private static void startRitual(ServerLevel level, TamableAnimal pet, ItemEntity item,
                                    ResourceLocation spellId, int spellLevel, long now,
                                    boolean rejected) {
        int ticks = rejected ? REJECT_TICKS : SpellMobConfig.scrollRitualTicks();
        RITUALS.put(pet.getUUID(), new Ritual(pet.getUUID(), item.getUUID(), level.dimension(),
                spellId, spellLevel, now, now + ticks, rejected));
        level.playSound(null, pet.blockPosition(), rejected
                ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.ILLUSIONER_PREPARE_MIRROR,
                SoundSource.NEUTRAL, 0.7F, 1.0F);
    }

    private static void tickRituals(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Ritual>> iterator = RITUALS.entrySet().iterator();
        while (iterator.hasNext()) {
            Ritual ritual = iterator.next().getValue();
            ServerLevel level = server.getLevel(ritual.dimension());
            if (level == null || !(level.getEntity(ritual.petId()) instanceof TamableAnimal pet)
                    || !pet.isAlive()) {
                iterator.remove();
                continue;
            }
            if (!(level.getEntity(ritual.itemId()) instanceof ItemEntity item)
                    || item.getItem().isEmpty()) {
                iterator.remove();
                continue;
            }
            long now = level.getGameTime();
            if (item.position().distanceToSqr(pet.position()) > KEEP_DISTANCE * KEEP_DISTANCE) {
                iterator.remove();
                continue;
            }
            float duration = Math.max(1.0F, ritual.endTick() - ritual.startTick());
            float progress = Math.min(1.0F, (now - ritual.startTick()) / duration);
            Vector3f colour = ritual.rejected() ? new Vector3f(0.05F)
                    : CastingPresentation.schoolColour(SpellRegistry.getSpell(ritual.spellId()));
            RitualCircle.tick(level, pet, colour, progress, ritual.rejected());
            if (now < ritual.endTick()) {
                continue;
            }
            iterator.remove();
            complete(level, pet, item, ritual);
        }
    }

    private static void complete(ServerLevel level, TamableAnimal pet, ItemEntity item,
                                 Ritual ritual) {
        if (ritual.rejected()) {
            return;
        }
        SpellCaster caster = casterOf(pet);
        AbstractSpell spell = SpellRegistry.getSpell(ritual.spellId());
        if (caster == null || spell == null || spell == SpellRegistry.none()) {
            return;
        }
        boolean upgraded = caster.learnedLevel(ritual.spellId()) > 0;
        caster.learnScrollSpell(ritual.spellId(), ritual.level());
        caster.startScrollCooldown(level.getGameTime(), SpellMobConfig.scrollCooldownTicks());
        CastGoalSync.sync(pet);
        item.discard();
        level.playSound(null, pet.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL,
                0.8F, 1.2F);
        notify(ownerPlayer(pet), Component.translatable(upgraded
                        ? "spellmob.message.scroll_upgraded" : "spellmob.message.scroll_learned",
                spell.getDisplayName(null)).withStyle(ChatFormatting.GREEN));
    }

    @Nullable
    private static TamableAnimal findPet(ServerLevel level, ItemEntity item, UUID thrower) {
        ServerPlayer owner = player(level, thrower);
        if (SpellMobConfig.scrollRequireOwner() && owner == null) {
            return null;
        }
        AABB area = item.getBoundingBox().inflate(PICKUP_RADIUS);
        List<TamableAnimal> pets = new ArrayList<>(level.getEntitiesOfClass(TamableAnimal.class, area,
                pet -> pet.isAlive() && pet.isTame() && isLearner(pet)
                        && (!SpellMobConfig.scrollRequireOwner() || pet.isOwnedBy(owner))));
        TamableAnimal best = null;
        double bestDistance = Double.MAX_VALUE;
        for (TamableAnimal pet : pets) {
            double distance = pet.distanceToSqr(item);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pet;
            }
        }
        return best;
    }

    private static boolean isLearner(TamableAnimal pet) {
        return pet.getTags().contains(SpellMobConfig.scrollLearnerTag());
    }

    @Nullable
    private static ServerPlayer ownerPlayer(TamableAnimal pet) {
        return pet.getOwner() instanceof ServerPlayer player ? player : null;
    }

    @Nullable
    private static ServerPlayer player(ServerLevel level, UUID uuid) {
        return level.getServer().getPlayerList().getPlayer(uuid);
    }

    @Nullable
    private static SpellCaster casterOf(TamableAnimal pet) {
        return pet.getCapability(SpellCasterCapability.capability()).resolve()
                .filter(SpellCaster.class::isInstance).map(SpellCaster.class::cast).orElse(null);
    }

    private static void notify(@Nullable ServerPlayer player, Component message) {
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }

    @Nullable
    private static ResourceLocation scrollSpellId(ItemStack stack) {
        ISpellContainer container = ISpellContainer.get(stack);
        if (container == null || container.isEmpty()) {
            return null;
        }
        SpellData data = container.getSpellAtIndex(0);
        AbstractSpell spell = data.getSpell();
        return spell == null || spell == SpellRegistry.none() ? null
                : ResourceLocation.tryParse(spell.getSpellId().toString());
    }

    private static int scrollSpellLevel(ItemStack stack) {
        ISpellContainer container = ISpellContainer.get(stack);
        if (container == null || container.isEmpty()) {
            return 1;
        }
        return Math.max(1, container.getSpellAtIndex(0).getLevel());
    }

    private record Pending(UUID itemId, ResourceKey<Level> dimension, UUID thrower, long startTick,
                           ResourceLocation spellId, int level) {
    }

    private record Ritual(UUID petId, UUID itemId, ResourceKey<Level> dimension,
                          ResourceLocation spellId, int level, long startTick, long endTick,
                          boolean rejected) {
    }
}
