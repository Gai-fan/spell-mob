package spellmob.capability;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;
import spellmob.config.SpellMobConfig;
import spellmob.data.SpellCastDefinition;
import spellmob.data.SpellCasterRegistry;
import spellmob.data.SpellCasterRules;
import spellmob.goal.CastGoalSync;
import spellmob.presentation.CastingPresentation;
import spellmob.util.Logs;

/**
 * Default {@link ISpellCaster} implementation.
 *
 * <p>The cast pipeline mirrors what Iron's Spellbooks does for its own casting mobs:
 * {@code MagicData#initiateCast} -> {@code onServerPreCast} -> {@code onServerCastTick} every tick
 * -> {@code onCast} -> {@code onServerCastComplete}. Going through that pipeline (instead of calling
 * {@code onCast} directly) keeps animations, sounds, sounds-based spell state and per-spell
 * bookkeeping consistent.</p>
 */
public final class SpellCaster implements ISpellCaster {

    /**
     * How long a caster waits after a failed attempt before trying again.
     *
     * <p>Without this backoff a spell whose {@code checkPreCastConditions} keeps failing would be
     * retried on every single tick, each retry doing a fresh target scan.</p>
     */
    private static final int FAILED_CAST_BACKOFF_TICKS = 20;

    /**
     * Ticks between two {@code onCast} calls of a channeled ({@code CONTINUOUS}) spell.
     *
     * <p>Iron's Spellbooks re-runs {@code onCast} every ten ticks for its own channeling mobs, so
     * using the same cadence keeps damage and particles in sync with what players see when they
     * channel the same spell themselves.</p>
     */
    private static final int CHANNEL_REPEAT_INTERVAL = 10;

    private final Mob mob;
    private final ResourceLocation entityId;
    @Nullable
    private MagicData magicData;
    private final Map<String, Integer> cooldowns = new HashMap<>();

    @Nullable
    private ActiveCast active;

    /** Tick before which {@link #isReady(SpellCastDefinition)} refuses to start another attempt. */
    private int retryAtTick;

    /** Custom name this caster was last synced with; name tags replace the component instance. */
    @Nullable
    private Component lastName;

    /** Health value the variant rules were last evaluated against. */
    private float lastHealth;

    /** Tamed state the variant rules were last evaluated against; taming unlocks variants. */
    private boolean lastTamed;

    /** Variant index the current goals were built for, -1 meaning the species default. */
    private int lastVariantIndex;

    /** False until the first tick has read health, which cannot happen during construction. */
    private boolean variantInitialized;

    /** True when the health moved into another variant while a cast was in flight. */
    private boolean variantResyncPending;

    /**
     * True while a cast is being completed or cancelled.
     *
     * <p>The completion callbacks run arbitrary spell code, which may damage the caster and fire
     * {@code onHurt}. Without this guard such a callback could start a nested cast that the outer
     * {@code resetCastingState()} would then immediately wipe.</p>
     */
    private boolean resolving;

    SpellCaster(Mob mob) {
        this.mob = mob;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        this.entityId = id == null ? ResourceLocation.fromNamespaceAndPath("minecraft", "unknown") : id;
        this.lastName = mob.getCustomName();
        // Forge gathers capabilities from Entity#<init>, before LivingEntity has built its
        // AttributeMap. Reading health at that point throws a NullPointerException, so the variant
        // is initialised lazily on the first tick (see syncGoalsIfHealthChanged).
        this.lastHealth = -1.0F;
        this.lastVariantIndex = -1;
    }

    @Override
    public void tick() {
        if (mob.level().isClientSide() || mob.isRemoved()) {
            return;
        }
        syncGoalsIfRenamed();
        syncGoalsIfVariantChanged();
        if (!SpellMobConfig.enabled()) {
            cancel(CancelReason.DISABLED);
            return;
        }
        tickCooldowns();

        ActiveCast cast = this.active;
        if (cast == null) {
            return;
        }
        if (!mob.isAlive() || mob.isDeadOrDying()) {
            cancel(CancelReason.CASTER_DIED);
            return;
        }
        cast.ticks++;
        // Iron's Spellbooks advances this clock itself for its own casting mobs. Spells that read
        // MagicData#getCastDurationRemaining (starfall schedules its meteors from it) otherwise see a
        // frozen value and never fire.
        magicData().handleCastDuration();
        // Re-aim before the completion branch below. onCast resolves on this very tick, and spells
        // such as lightning_lance launch a straight projectile along getLookAngle(); if the caster
        // moved since the previous tick the shot would be a tick stale and fly wide at long range.
        try {
            aimAt(cast);
        } catch (Throwable throwable) {
            Logs.error("Spell " + cast.spell.getSpellId() + " threw while aiming", throwable);
            cancel(CancelReason.INTERNAL_ERROR);
            return;
        }
        int remaining = cast.totalTicks - cast.ticks;
        if (remaining <= 0) {
            complete(cast);
            return;
        }
        if (shouldAbort(cast)) {
            cancel(CancelReason.TARGET_LOST);
            return;
        }
        CastingPresentation.onCastTick(mob, cast.spell, cast.ticks, cast.totalTicks);
        try {
            cast.spell.onServerCastTick(level(), cast.level, mob, magicData());
            repeatChannelOnCast(cast, remaining);
        } catch (Throwable throwable) {
            Logs.error("Spell " + cast.spell.getSpellId() + " threw while ticking its cast", throwable);
            cancel(CancelReason.INTERNAL_ERROR);
        }
    }

    /**
     * Returns the magic state of this caster, creating it on first use.
     *
     * <p>Iron's Spellbooks cannot cast without a synced state: {@code initiateCast} writes the
     * casting marker into it and throws when it is missing, which is why Iron's own casting mobs
     * build one in their constructor. Here the object is created only when this mob actually starts
     * a cast, so configured but idle mobs do not carry it around.</p>
     *
     * @return the magic state of this caster
     */
    private MagicData magicData() {
        if (this.magicData == null) {
            MagicData data = new MagicData(true);
            data.setSyncedData(new SyncedSpellData(this.mob));
            this.magicData = data;
        }
        return this.magicData;
    }

    private void tickCooldowns() {
        if (cooldowns.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<String, Integer>> iterator = cooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                iterator.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    /**
     * Rebuilds the casting goals when the mob gets, loses or changes its name tag.
     *
     * <p>Variants select individuals by name, so a plain zombie becomes a caster the moment it is
     * renamed with a name tag. Name tags replace the component instance, which makes a reference
     * comparison enough to detect a change without allocating anything per tick.</p>
     */
    private void syncGoalsIfRenamed() {
        Component current = mob.getCustomName();
        if (current != this.lastName) {
            this.lastName = current;
            CastGoalSync.sync(mob);
            this.lastVariantIndex = currentVariantIndex();
        }
    }

    /**
     * Rebuilds the casting goals when the mob moves into another variant.
     *
     * <p>Re-running the selectors for every loaded mob every tick would cost far more than the
     * feature is worth, so only the cheap triggers are compared: the health value (one float) and
     * the tamed flag (one entity data bit). Taming is checked here so a wolf unlocks its tamed
     * variants the moment it is tamed, without a reload. The rebuild is deferred while a cast is in
     * flight because {@code CastGoalSync#sync} removes the goals (and therefore cancels the cast);
     * a phase change is not worth losing a cast over.</p>
     */
    private void syncGoalsIfVariantChanged() {
        if (!this.variantInitialized) {
            this.variantInitialized = true;
            this.lastHealth = mob.getHealth();
            this.lastTamed = isTamed();
            this.lastVariantIndex = currentVariantIndex();
            return;
        }
        float health = mob.getHealth();
        boolean tamed = isTamed();
        if (health != this.lastHealth || tamed != this.lastTamed) {
            this.lastHealth = health;
            this.lastTamed = tamed;
            int index = currentVariantIndex();
            if (index != this.lastVariantIndex) {
                this.lastVariantIndex = index;
                this.variantResyncPending = true;
            }
        }
        if (this.variantResyncPending && this.active == null) {
            this.variantResyncPending = false;
            CastGoalSync.sync(mob);
        }
    }

    private boolean isTamed() {
        return mob instanceof TamableAnimal tamable && tamable.isTame();
    }

    private int currentVariantIndex() {
        SpellCasterRules rules = SpellCasterRegistry.get(mob.getType());
        return rules == null ? -1 : rules.variantIndex(mob);
    }

    private void aimAt(ActiveCast cast) {
        Vec3 aim = cast.position;
        if (aim == null) {
            LivingEntity target = cast.target;
            aim = target != null && target.isAlive() ? target.getEyePosition() : null;
        }
        if (aim == null) {
            return;
        }
        mob.getLookControl().setLookAt(aim.x, aim.y, aim.z, 30.0F, 30.0F);
        // Rays such as ray_of_siphoning are cast from getLookAngle(), which for a living entity reads
        // the *head* rotation; a LookControl that turns 30 degrees per tick does not guarantee the
        // head already points at the target when the first tick of the cast resolves. Snapping the
        // body and head here costs nothing and makes the first hit test aim where it should.
        mob.lookAt(EntityAnchorArgument.Anchor.EYES, aim);
        mob.setYHeadRot(mob.getYRot());
        mob.yBodyRot = mob.getYRot();
    }

    private boolean shouldAbort(ActiveCast cast) {
        LivingEntity target = cast.target;
        if (target == null || target == mob) {
            return false;
        }
        if (!target.isAlive() || target.isRemoved()) {
            return true;
        }
        double limit = cast.definition.effectiveRange() + 4.0D;
        if (mob.distanceToSqr(target) > limit * limit) {
            return true;
        }
        if (cast.definition.target().lineOfSight() && !mob.getSensing().hasLineOfSight(target)) {
            return true;
        }
        try {
            return cast.spell.shouldAIStopCasting(cast.level, mob, target);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public boolean isCasting() {
        return active != null;
    }

    @Override
    public boolean isCasting(SpellCastDefinition definition) {
        return active != null && active.definition.equals(definition);
    }

    @Override
    public @Nullable SpellCastDefinition castingDefinition() {
        ActiveCast cast = this.active;
        return cast == null ? null : cast.definition;
    }

    @Override
    public int castTicksRemaining() {
        ActiveCast cast = this.active;
        return cast == null ? 0 : Math.max(0, cast.totalTicks - cast.ticks);
    }

    @Override
    public boolean isReady(SpellCastDefinition definition) {
        if (resolving || active != null || !SpellMobConfig.enabled() || mob.tickCount < retryAtTick) {
            return false;
        }
        if (!mob.isAlive() || mob.isDeadOrDying()) {
            return false;
        }
        ServerLevel level = level();
        if (level == null) {
            return false;
        }
        if (SpellMobConfig.isEntityBlacklisted(entityId)
                || SpellMobConfig.isDimensionBlacklisted(level.dimension().location())) {
            return false;
        }
        if (cooldownTicks(definition) > 0) {
            return false;
        }
        AbstractSpell spell = definition.spell();
        return spell != null && spell.isEnabled();
    }

    @Override
    public boolean start(SpellCastDefinition definition, @Nullable LivingEntity target, @Nullable Vec3 position) {
        return start(definition, target, position, false, false);
    }

    @Override
    public boolean startForced(SpellCastDefinition definition, @Nullable LivingEntity target, @Nullable Vec3 position) {
        return start(definition, target, position, true, true);
    }

    private boolean start(SpellCastDefinition definition, @Nullable LivingEntity target, @Nullable Vec3 position,
                          boolean instant, boolean forced) {
        if (forced) {
            // The caster is dying, so the normal readiness checks (alive, cooldown, blacklists) do
            // not apply. Still refuse to interrupt an existing cast or start while disabled.
            if (resolving || active != null || !SpellMobConfig.enabled()) {
                return false;
            }
        } else if (!isReady(definition)) {
            return false;
        }
        if (!forced && definition.chance() < 1.0D && mob.getRandom().nextDouble() >= definition.chance()) {
            // The roll happens before any expensive check, so a "chance" spell that skips its turn
            // costs a single random number instead of a target scan.
            return false;
        }
        ServerLevel level = level();
        AbstractSpell spell = definition.spell();
        if (level == null || spell == null) {
            this.retryAtTick = mob.tickCount + FAILED_CAST_BACKOFF_TICKS;
            return false;
        }

        // ISS resolves a spell's maximum level through its config manager, and a spell can be
        // missing from that map while the data pack loads (before ISS fills it). Clamping here,
        // at cast time, keeps the configured level intact and still never exceeds what ISS allows.
        // ISS 1.21.1 reads a spell's max level from its config manager, which defaults to 1 when
        // no per-spell entry exists. That default would flatten every configured level to 1, so the
        // cap is only applied when the config actually declares a higher maximum.
        int configuredMax = spell.getMaxLevel();
        int spellLevel = configuredMax > 1 ? Math.min(definition.level(), configuredMax) : definition.level();

        Vec3 aim = position != null ? position : target != null && target != mob ? target.getEyePosition() : null;
        if (aim != null) {
            mob.lookAt(EntityAnchorArgument.Anchor.EYES, aim);
            mob.getLookControl().setLookAt(aim.x, aim.y, aim.z, 30.0F, 30.0F);
        }

        try {
            if (!spell.checkPreCastConditions(level, spellLevel, mob, magicData())) {
                Logs.debug("{} failed pre-cast conditions for {}", mob, definition.spellId());
                this.retryAtTick = mob.tickCount + FAILED_CAST_BACKOFF_TICKS;
                return false;
            }
        } catch (Throwable throwable) {
            Logs.error("Spell " + definition.spellId() + " threw in checkPreCastConditions", throwable);
            this.retryAtTick = mob.tickCount + FAILED_CAST_BACKOFF_TICKS;
            return false;
        }

        int castTime = instant ? 0
                : definition.castTimeTicks() >= 0 ? definition.castTimeTicks()
                : Math.max(0, spell.getEffectiveCastTime(spellLevel, mob));
        int cooldownTicks = definition.cooldownTicks() >= 0 ? definition.cooldownTicks()
                : Math.max(1, spell.getSpellCooldown() * 20);
        // A channeled spell keeps its cooldown until the channel ends (see applyCooldown): charging
        // it up front would let the same caster start a second channel the tick the first one ends.
        boolean channel = castTime > 0 && spell.getCastType() == CastType.CONTINUOUS;
        // Never store a zero tick cooldown: the goal would immediately re-cast on the next tick.
        int scaledCooldown = (int) Math.max(1L, Math.round(cooldownTicks * SpellMobConfig.cooldownMultiplier()));
        String key = cooldownKey(definition);
        if (!channel) {
            cooldowns.put(key, scaledCooldown);
        }

        try {
            magicData().initiateCast(spell, spellLevel, castTime, CastSource.MOB,
                    SpellSelectionManager.MAINHAND);
            spell.onServerPreCast(level, spellLevel, mob, magicData());
        } catch (Throwable throwable) {
            Logs.error("Spell " + definition.spellId() + " threw while starting its cast", throwable);
            if (this.magicData != null) {
                this.magicData.resetCastingState();
            }
            if (!channel) {
                cooldowns.remove(key);
            }
            this.retryAtTick = mob.tickCount + FAILED_CAST_BACKOFF_TICKS;
            return false;
        }

        if (SpellMobConfig.castSounds()) {
            try {
                spell.playSound(spell.getCastStartSound(), mob);
            } catch (RuntimeException exception) {
                Logs.debug("Could not play the start sound of {}: {}", definition.spellId(), exception.getMessage());
            }
        }

        this.retryAtTick = 0;
        ActiveCast cast = new ActiveCast(definition, spell, spellLevel, castTime, target, position,
                channel, scaledCooldown, key);
        this.active = cast;
        Logs.debug("{} starts casting {} (level {}, {} ticks)", mob, definition.spellId(), spellLevel, castTime);
        CastingPresentation.onCastStart(mob, spell, castTime);
        if (castTime <= 0) {
            complete(cast);
        }
        return true;
    }

    private void complete(ActiveCast cast) {
        this.active = null;
        this.resolving = true;
        ServerLevel level = level();
        try {
            if (level != null && (!cast.channel || !cast.repeatedOnCast)) {
                // A channel already resolved its final tick through repeatChannelOnCast.
                cast.spell.onCast(level, cast.level, mob, CastSource.MOB, magicData());
            }
        } catch (Throwable throwable) {
            Logs.error("Spell " + cast.spell.getSpellId() + " threw in onCast", throwable);
        }
        try {
            if (level != null) {
                cast.spell.onServerCastComplete(level, cast.level, mob, magicData(), false);
            }
        } catch (Throwable throwable) {
            Logs.error("Spell " + cast.spell.getSpellId() + " threw in onServerCastComplete", throwable);
        } finally {
            magicData().resetCastingState();
            this.resolving = false;
        }
        if (cast.totalTicks > 0) {
            CastingPresentation.onCastFinish(mob, cast.spell, cast.totalTicks);
        }
        if (SpellMobConfig.castSounds()) {
            try {
                cast.spell.playSound(cast.spell.getCastFinishSound(), mob);
            } catch (RuntimeException exception) {
                Logs.debug("Could not play the finish sound of {}: {}", cast.spell.getSpellId(), exception.getMessage());
            }
        }
        applyCooldown(cast);
    }

    /**
     * Re-runs {@code onCast} on the cadence Iron's Spellbooks uses for channeled spells.
     *
     * <p>Without it a {@code CONTINUOUS} spell would resolve only once, at the end of the channel,
     * which turns e.g. a ten second beam into a single damage tick.</p>
     */
    private void repeatChannelOnCast(ActiveCast cast, int remaining) {
        if (!cast.channel || !cast.definition.repeatOnCast()) {
            return;
        }
        if ((remaining + 1) % CHANNEL_REPEAT_INTERVAL != 0) {
            return;
        }
        cast.repeatedOnCast = true;
        cast.spell.onCast(level(), cast.level, mob, CastSource.MOB, magicData());
    }

    /**
     * Applies the cooldown of a channeled cast once its channel is over.
     *
     * <p>Normal casts store their cooldown when they start; a channel defers it to this method so
     * the cooldown only begins counting down after the channel ended, not while it is running.</p>
     */
    private void applyCooldown(ActiveCast cast) {
        if (cast.channel && cast.cooldownTicks > 0) {
            cooldowns.put(cast.cooldownKey, cast.cooldownTicks);
        }
    }

    @Override
    public void cancel(CancelReason reason) {
        ActiveCast cast = this.active;
        if (cast == null) {
            return;
        }
        this.active = null;
        this.resolving = true;
        ServerLevel level = level();
        if (level != null && cast.ticks > 0) {
            try {
                cast.spell.onServerCastComplete(level, cast.level, mob, magicData(), true);
            } catch (Throwable throwable) {
                Logs.error("Spell " + cast.spell.getSpellId() + " threw while being cancelled", throwable);
            }
        }
        if (this.magicData != null) {
            this.magicData.resetCastingState();
        }
        this.resolving = false;
        applyCooldown(cast);
        Logs.debug("{} stops casting {} ({})", mob, cast.spell.getSpellId(), reason.message());
        CastingPresentation.onCastCancelled(mob, reason);
    }

    @Override
    public int cooldownTicks(SpellCastDefinition definition) {
        return cooldowns.getOrDefault(cooldownKey(definition), 0);
    }

    /**
     * Builds the cooldown bucket of a definition.
     *
     * <p>Spells with a {@code cooldownGroup} share one bucket, which is what makes a phase change
     * after a long cast time out instead of firing immediately next to the spell it replaced.</p>
     */
    private static String cooldownKey(SpellCastDefinition definition) {
        String group = definition.cooldownGroup();
        return group == null ? definition.spellId().toString() : "group:" + group;
    }

    @Override
    public void clear() {
        cooldowns.clear();
        if (this.magicData != null) {
            this.magicData.resetCastingState();
        }
        this.active = null;
        this.retryAtTick = 0;
        this.resolving = false;
        this.lastHealth = -1.0F;
        this.lastVariantIndex = -1;
        this.variantInitialized = false;
        this.variantResyncPending = false;
    }

    private @Nullable ServerLevel level() {
        return mob.level() instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (!cooldowns.isEmpty()) {
            CompoundTag stored = new CompoundTag();
            cooldowns.forEach((key, ticks) -> stored.putInt(key, ticks));
            tag.put("cooldowns", stored);
        }
        return tag;
    }

    void load(CompoundTag tag) {
        cooldowns.clear();
        CompoundTag stored = tag.getCompound("cooldowns");
        for (String key : stored.getAllKeys()) {
            int ticks = stored.getInt(key);
            if (!key.isEmpty() && ticks > 0) {
                cooldowns.put(key, ticks);
            }
        }
    }

    /** One in-flight cast. */
    private static final class ActiveCast {

        private final SpellCastDefinition definition;
        private final AbstractSpell spell;
        private final int level;
        private final int totalTicks;
        private final @Nullable LivingEntity target;
        private final @Nullable Vec3 position;
        private final boolean channel;
        private final int cooldownTicks;
        private final String cooldownKey;
        private int ticks;
        private boolean repeatedOnCast;

        private ActiveCast(SpellCastDefinition definition, AbstractSpell spell, int level, int totalTicks,
                           @Nullable LivingEntity target, @Nullable Vec3 position,
                           boolean channel, int cooldownTicks, String cooldownKey) {
            this.definition = definition;
            this.spell = spell;
            this.level = level;
            this.totalTicks = totalTicks;
            this.target = target;
            this.position = position;
            this.channel = channel;
            this.cooldownTicks = cooldownTicks;
            this.cooldownKey = cooldownKey;
        }
    }
}
