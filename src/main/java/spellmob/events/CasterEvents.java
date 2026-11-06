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
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import spellmob.SpellMob;
import spellmob.capability.CancelReason;
import spellmob.capability.ISpellCaster;
import spellmob.capability.SpellCasterAttachments;
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
@EventBusSubscriber(modid = SpellMob.MOD_ID)
public final class CasterEvents {

    private CasterEvents() {
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Mob mob) {
            // A mob that enters the level starts with vanilla goals only, so a type without rules
            // has none of ours to clean up: skip the goal list copy and the variant match entirely.
            if (SpellCasterRegistry.get(mob.getType()) != null) {
                CastGoalSync.sync(mob);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Mob mob) {
            ISpellCaster caster = SpellCasterAttachments.peek(mob);
            if (caster != null) {
                caster.cancel(CancelReason.UNLOADED);
                // The bar is a player facing object: leaving it behind keeps a ghost bar on the
                // screen of everyone who was standing next to the mob.
                caster.removeBossBar();
            }
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
    public static void onLivingTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) {
            return;
        }
        // Types without rules can never cast, so they never get a caster: this is what keeps a
        // full world from paying for the attachment bookkeeping on every mob, every tick.
        if (SpellCasterRegistry.get(mob.getType()) == null) {
            return;
        }
        ISpellCaster caster = SpellCasterAttachments.get(mob);
        if (caster != null) {
            caster.tick();
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // COMMON configs are loaded before the spell registry is guaranteed to be populated, so a
        // rule that references a spell may have been dropped on the first pass. Re-applying once the
        // server is up makes the config side authoritative without depending on load order.
        ConfigEvents.reapply();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        CastGoalSync.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingIncomingDamageEvent event) {
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
        ISpellCaster caster = SpellCasterAttachments.peek(mob);
        if (caster == null) {
            return;
        }
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
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || event.getNewAboutToBeSetTarget() == null) {
            return;
        }
        trigger(entity, SpellCasterData::onTarget, event.getNewAboutToBeSetTarget());
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
     * <p>Every rule that matches the mob is rolled: the matched variant's own rules plus the
     * species wide ones. A definition that only carries {@code dropRules} still drops loot even
     * though it does not cast anything, which is useful for plain "loot variant" mobs.</p>
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
        List<DropRule> dropRules = rules.dropRulesFor(mob);
        if (dropRules.isEmpty()) {
            return;
        }
        Player killer = event.getSource().getEntity() instanceof Player player ? player : null;
        for (DropRule rule : dropRules) {
            if (mob.getRandom().nextDouble() >= rule.chance()) {
                continue;
            }
            for (DropEntry drop : rule.drops()) {
                if (drop.requirePlayerKill() && killer == null) {
                    continue;
                }
                if (!drop.rolls(mob.getRandom())) {
                    continue;
                }
                for (ItemStack stack : drop.createStacks(mob, killer, lootingLevel(killer))) {
                    if (stack.isEmpty()) {
                        continue;
                    }
                    event.getDrops().add(new ItemEntity(mob.level(), mob.getX(), mob.getY() + 0.5D,
                            mob.getZ(), stack));
                }
            }
        }
    }


    /**
     * Looting level of the killing player, used for the bonus rolls of drop entries.
     *
     * <p>NeoForge no longer exposes the looting level on the drops event, so it is read from the
     * killer's enchantments directly. A missing enchantment registry entry is treated as level 0.</p>
     */
    private static int lootingLevel(@Nullable Player killer) {
        if (killer == null) {
            return 0;
        }
        try {
            return EnchantmentHelper.getEnchantmentLevel(
                    killer.level().registryAccess().holderOrThrow(Enchantments.LOOTING), killer);
        } catch (RuntimeException exception) {
            return 0;
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
        // Stale goals can outlive a datapack reload, so re-check that this type still has rules.
        if (SpellCasterRegistry.get(mob.getType()) == null) {
            return;
        }
        ISpellCaster existing = SpellCasterAttachments.peek(mob);
        // No attachment means no cooldown has ever been recorded, so the readiness check can be
        // skipped; start() validates the rest and the attachment is created only after the target
        // and the conditions have passed.
        if (!forced && existing != null && !existing.isReady(definition)) {
            return;
        }
        TargetResolver.Resolved resolved = TargetResolver.resolve(mob, definition, preferred);
        if (resolved == null) {
            return;
        }
        if (!definition.conditions().test(mob, resolved.entity())) {
            return;
        }
        ISpellCaster caster = SpellCasterAttachments.get(mob);
        if (caster == null) {
            return;
        }
        if (forced) {
            caster.startForced(definition, resolved.entity(), resolved.position());
        } else {
            caster.start(definition, resolved.entity(), resolved.position());
        }
    }
}
