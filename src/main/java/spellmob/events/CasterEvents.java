package spellmob.events;

import java.util.List;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import spellmob.SpellMob;
import spellmob.capability.CancelReason;
import spellmob.capability.ISpellCaster;
import spellmob.capability.SpellCasterCapability;
import spellmob.config.SpellMobConfig;
import spellmob.data.DropEntry;
import spellmob.data.DropRule;
import spellmob.data.SpellCastDefinition;
import spellmob.data.SpellCasterData;
import spellmob.data.SpellCasterRegistry;
import spellmob.data.SpellCasterRules;
import spellmob.goal.CastGoalSync;
import spellmob.util.TargetResolver;

/** All forge event hooks of the mod. */
@Mod.EventBusSubscriber(modid = SpellMob.MOD_ID)
public final class CasterEvents {

    private CasterEvents() {
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        SpellCasterCapability.onAttachCapabilities(event);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Mob mob) {
            CastGoalSync.sync(mob);
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Mob mob) {
            mob.getCapability(SpellCasterCapability.capability())
                    .ifPresent(caster -> caster.cancel(CancelReason.UNLOADED));
        }
    }

    /**
     * Keeps the name of protected mobs: a name tag may only fill in a missing name, never replace
     * an existing one, unless the user is an operator and renaming is explicitly allowed.
     *
     * <p>This only stops manual name tag use. Commands and other mods that bypass player
     * interaction are out of scope, which is why protected mobs should also use tag based
     * selectors instead of name based ones.</p>
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        if (!event.getItemStack().is(Items.NAME_TAG)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!target.hasCustomName() || !SpellMobConfig.isNameProtected(target)) {
            return;
        }
        if (SpellMobConfig.allowOpsRename() && player.hasPermissions(2)) {
            return;
        }
        event.setCanceled(true);
        player.displayClientMessage(Component.translatableWithFallback(
                "spellmob.message.name_locked", "This creature's name is locked."), true);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) {
            return;
        }
        mob.getCapability(SpellCasterCapability.capability()).ifPresent(ISpellCaster::tick);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // COMMON configs are loaded before the spell registry is guaranteed to be populated, so a
        // rule that references a spell may have been dropped on the first pass. Re-applying once the
        // server is up makes the config side authoritative without depending on load order.
        ConfigEvents.reapply();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            CastGoalSync.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity hurt = event.getEntity();
        if (hurt.level().isClientSide()) {
            return;
        }
        if (event.getAmount() > 0.0F) {
            interruptIfConfigured(hurt);
        }
        Entity attacker = event.getSource().getEntity();
        trigger(hurt, SpellCasterData::onHurt, attacker instanceof LivingEntity living ? living : null);
    }

    /**
     * Breaks a cast whose spell is configured to lose concentration when its caster is hit.
     *
     * <p>Cancelling happens before the {@code onHurt} trigger runs, so a spell that reacts to
     * damage is free to start a new cast in the same tick; that is how "hit me and I teleport
     * away" setups are written.</p>
     */
    private static void interruptIfConfigured(LivingEntity hurt) {
        if (!(hurt instanceof Mob mob)) {
            return;
        }
        mob.getCapability(SpellCasterCapability.capability()).ifPresent(caster -> {
            SpellCastDefinition casting = caster.castingDefinition();
            if (casting == null) {
                return;
            }
            AbstractSpell spell = casting.spell();
            if (spell != null && spell.getCastType() == CastType.CONTINUOUS) {
                // Channels follow the global switch plus the per mob immunity tag, so a boss can
                // keep channeling through damage while ordinary mobs are easy to interrupt.
                if (SpellMobConfig.interruptChannelsOnDamage() && !SpellMobConfig.isChannelImmune(mob)) {
                    caster.cancel(CancelReason.DAMAGED);
                }
                return;
            }
            if (casting.interruptOnDamage()) {
                caster.cancel(CancelReason.DAMAGED);
            }
        });
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || event.getNewTarget() == null) {
            return;
        }
        trigger(entity, SpellCasterData::onTarget, event.getNewTarget());
    }

    /**
     * Fires the two death based triggers: the dying mob's {@code onDeath} spell (forced and
     * instant, because it stops ticking) and the killer's {@code onKill} spell.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        LivingEntity killer = attacker instanceof LivingEntity living && living != victim ? living : null;

        if (victim instanceof Mob mob) {
            cast(mob, definition(mob, SpellCasterData::onDeath), killer, true);
        }
        if (killer instanceof Mob mob) {
            cast(mob, definition(mob, SpellCasterData::onKill), victim, false);
        }
    }

    /**
     * Rolls the {@code dropRules} of a dying caster.
     *
     * <p>Only mobs that resolve to a non empty casting configuration drop anything, so the file
     * cannot be used to give plain mobs loot; that is what vanilla loot tables are for. Rules are
     * evaluated in order and the first match wins, matching the {@code variants} semantics.</p>
     */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !(entity instanceof Mob mob)) {
            return;
        }
        SpellCasterRules rules = SpellCasterRegistry.get(mob.getType());
        if (rules == null) {
            return;
        }
        SpellCasterData data = rules.resolve(mob);
        if (data == null || data.isEmpty()) {
            return;
        }
        DropRule rule = rules.dropRule(mob);
        if (rule == null || mob.getRandom().nextDouble() >= rule.chance()) {
            return;
        }
        Player killer = event.getSource().getEntity() instanceof Player player ? player : null;
        for (DropEntry drop : rule.drops()) {
            if (drop.requirePlayerKill() && killer == null) {
                continue;
            }
            if (!drop.rolls(mob.getRandom())) {
                continue;
            }
            for (ItemStack stack : drop.createStacks(mob, killer, event.getLootingLevel())) {
                if (stack.isEmpty()) {
                    continue;
                }
                event.getDrops().add(new ItemEntity(mob.level(), mob.getX(), mob.getY() + 0.5D,
                        mob.getZ(), stack));
            }
        }
    }

    private static void trigger(LivingEntity entity, Function<SpellCasterData, @Nullable SpellCastDefinition> selector,
                                @Nullable LivingEntity preferred) {
        if (entity instanceof Mob mob) {
            cast(mob, definition(mob, selector), preferred, false);
        }
    }

    private static @Nullable SpellCastDefinition definition(
            Mob mob, Function<SpellCasterData, @Nullable SpellCastDefinition> selector) {
        SpellCasterRules rules = SpellCasterRegistry.get(mob.getType());
        SpellCasterData data = rules == null ? null : rules.resolve(mob);
        return data == null ? null : selector.apply(data);
    }

    private static void cast(Mob mob, @Nullable SpellCastDefinition definition,
                             @Nullable LivingEntity preferred, boolean forced) {
        if (definition == null) {
            return;
        }
        mob.getCapability(SpellCasterCapability.capability()).ifPresent(caster -> {
            if (!forced && !caster.isReady(definition)) {
                return;
            }
            TargetResolver.Resolved resolved = TargetResolver.resolve(mob, definition, preferred);
            if (resolved == null) {
                return;
            }
            if (!definition.conditions().test(mob, resolved.entity())) {
                return;
            }
            if (forced) {
                caster.startForced(definition, resolved.entity(), resolved.position());
            } else {
                caster.start(definition, resolved.entity(), resolved.position());
            }
        });
    }
}
