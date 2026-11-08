package spellmob.capability;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;
import spellmob.config.SpellMobConfig;
import spellmob.data.CastConditions;
import spellmob.data.SpellCastDefinition;
import spellmob.data.SpellCasterRegistry;
import spellmob.data.SpellCasterRules;
import spellmob.data.TargetSpec;
import spellmob.goal.CastGoalSync;
import spellmob.presentation.CasterBossBar;
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

    /** Tag carried by mobs that learned at least one spell, so they keep ticking without rules. */
    public static final String CASTER_TAG = "spellmob_caster";

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

    /**
     * Remaining ticks of the optional global cooldown, which is shared by every spell of this
     * caster. It is kept apart from {@link #cooldowns} so a data pack can never collide with it by
     * naming a cooldown group the same way.
     */
    private int globalCooldown;

    /** Player UUID -> the spells that player cast while fighting this boss. */
    private final Map<UUID, List<ResourceLocation>> encounterSpells = new HashMap<>();

    /** Players this boss is currently fighting. */
    private final Set<UUID> engagedPlayers = new HashSet<>();

    /** Players that died while fighting this boss and have not come back yet. */
    private final Set<UUID> defeatedPlayers = new HashSet<>();

    /** Spells learned from defeated players or from scrolls, mapped to the level they are cast at. */
    private final Map<ResourceLocation, Integer> learnedSpells = new LinkedHashMap<>();

    /** Game time before which this pet refuses a second scroll. */
    private long scrollCooldownEnd;

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

    /**
     * Deepest sticky variant this caster has reached, -1 when none.
     *
     * <p>Without it a boss that gets healed snaps back to its first phase and swaps its whole spell
     * list mid fight. Only variants marked {@code sticky} are remembered; the lock is dropped on a
     * rename because that changes which individual the selectors are looking at.</p>
     */
    private int lockedVariant;
    /** Ticks this boss has spent without any player nearby, see {@link #tickEscapeReset()}. */
    private int escapeTicks;

    /** False until the first tick has read health, which cannot happen during construction. */
    private boolean variantInitialized;

    /** True when the health moved into another variant while a cast was in flight. */
    private boolean variantResyncPending;

    /** Optional boss bar, only allocated once the config asks for one. */
    @Nullable
    private CasterBossBar bossBar;

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
        tickBossBar();
        if (!SpellMobConfig.enabled()) {
            cancel(CancelReason.DISABLED);
            return;
        }
        tickCooldowns();
        tickEscapeReset();

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
     * a cast, so configured but idle mobs do not carry it around, and nothing entity dependent runs
     * during {@code Entity#<init>}.</p>
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

    /**
     * Heals a boss back to full and drops its phase lock after the fight was abandoned, so a player
     * who died or ran away finds the same clean fight instead of a half dead boss stuck in a later
     * phase. Learned spells are intentionally kept: learning is meant to pile up across fights.
     */
    private void tickEscapeReset() {
        if (!SpellMobConfig.bossResetOnEscape() || !SpellMobConfig.isBossCaster(this.mob)
                || !(this.mob.level() instanceof ServerLevel level)) {
            this.escapeTicks = 0;
            return;
        }
        if (level.getNearestPlayer(this.mob, SpellMobConfig.bossEscapeRadius()) != null) {
            this.escapeTicks = 0;
            return;
        }
        if (++this.escapeTicks < SpellMobConfig.bossEscapeGrace()) {
            return;
        }
        this.escapeTicks = 0;
        if (this.mob.getHealth() >= this.mob.getMaxHealth() && this.lockedVariant < 0
                && this.mob.getTarget() == null) {
            return;
        }
        this.mob.setHealth(this.mob.getMaxHealth());
        this.lockedVariant = -1;
        this.mob.setTarget(null);
        this.mob.getNavigation().stop();
        cancel(CancelReason.TARGET_LOST);
        CastGoalSync.sync(this.mob);
        Logs.debug("{} reset after the fight was abandoned", this.mob);
    }

    private void tickCooldowns() {
        if (this.globalCooldown > 0) {
            this.globalCooldown--;
        }
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
            // The selectors are looking at a different individual now, so an old phase lock would
            // keep a renamed cow in a boss phase it never reached.
            this.lockedVariant = -1;
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
        if (rules == null) {
            this.lockedVariant = -1;
            return -1;
        }
        if (this.lockedVariant >= 0
                && (this.lockedVariant >= rules.variantCount() || !rules.isSticky(this.lockedVariant))) {
            // The data pack changed (variant removed or no longer sticky): forget the lock.
            this.lockedVariant = -1;
        }
        int raw = rules.variantIndex(mob);
        if (raw >= 0 && rules.isSticky(raw) && raw > this.lockedVariant) {
            this.lockedVariant = raw;
        }
        // Health came back above the threshold, but a phase is one way once entered.
        return rules.effectiveIndex(mob, this.lockedVariant);
    }

    /**
     * @return the variant index that currently applies, honouring the sticky lock
     */
    public int effectiveVariantIndex() {
        return currentVariantIndex();
    }

    /**
     * Runs the optional boss bar.
     *
     * <p>The bar object is only allocated once {@code presentation.bossBar} is on, so a server that
     * never enables it pays nothing but one boolean read per tick.</p>
     */
    private void tickBossBar() {
        if (this.bossBar == null) {
            if (!SpellMobConfig.bossBarEnabled()) {
                return;
            }
            this.bossBar = new CasterBossBar(this.mob);
        }
        this.bossBar.tick(currentVariantIndex());
    }

    @Override
    public void removeBossBar() {
        if (this.bossBar != null) {
            this.bossBar.remove();
            this.bossBar = null;
        }
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

    /**
     * @return the spell currently being cast, or {@code null}
     *
     * <p>Used by the boss bar to append "casting &lt;spell&gt;" to the mob name. Returned as the
     * spell object rather than its id so the caller can use the game's own translated name.</p>
     */
    public @Nullable AbstractSpell castingSpell() {
        ActiveCast cast = this.active;
        return cast == null ? null : cast.spell;
    }

    /** @return whether the caster is inside the global cooldown window */
    public boolean onGlobalCooldown() {
        return this.globalCooldown > 0;
    }

    /**
     * @return true when this boss may learn spells from players that lose to it: the config switch
     *     is on, the mob carries the adaptive tag and it has data pack rules
     */
    public boolean isAdaptive() {
        if (!SpellMobConfig.adaptiveEnabled()) {
            return false;
        }
        SpellCasterRules rules = SpellCasterRegistry.get(mob.getType());
        if (rules == null) {
            return false;
        }
        return mob.getTags().contains(SpellMobConfig.adaptiveTag()) || rules.adaptive(mob);
    }

    /**
     * Marks a player as fighting this boss. A player that had already died to the boss and comes
     * back triggers the learning step here.
     *
     * @param player the player that attacked or was attacked
     */
    public void engage(Player player) {
        if (!isAdaptive()) {
            return;
        }
        UUID id = player.getUUID();
        if (engagedPlayers.add(id) && defeatedPlayers.remove(id) && learnFrom(id)) {
            CastGoalSync.sync(mob);
        }
    }

    /**
     * Records a spell a player cast while fighting this boss.
     *
     * @param player  the casting player
     * @param spellId the Iron's Spellbooks spell id
     */
    public void recordPlayerSpell(Player player, ResourceLocation spellId) {
        if (!isAdaptive()) {
            return;
        }
        UUID id = player.getUUID();
        if (!engagedPlayers.contains(id) && !defeatedPlayers.contains(id)) {
            return;
        }
        List<ResourceLocation> spells = encounterSpells.computeIfAbsent(id, key -> new ArrayList<>());
        if (spells.contains(spellId) || spells.size() >= SpellMobConfig.adaptiveRememberedSpells()) {
            return;
        }
        spells.add(spellId);
    }

    /**
     * Freezes the spells of a player that just died to this boss; the next fight unlocks one of
     * them.
     *
     * @param player the dying player
     */
    public void markPlayerDefeated(Player player) {
        UUID id = player.getUUID();
        if (engagedPlayers.remove(id)) {
            defeatedPlayers.add(id);
        }
    }

    /**
     * @return the learned spells as cast definitions, at the spell's maximum level
     */
    public List<SpellCastDefinition> learnedDefinitions() {
        if (learnedSpells.isEmpty()) {
            return List.of();
        }
        boolean pet = mob instanceof TamableAnimal tame && tame.isTame();
        int priority = pet ? SpellMobConfig.petCastPriority(mob.getType()) : SpellMobConfig.learnedPriority();
        double range = pet ? SpellMobConfig.petCastRange() : 24.0D;
        List<SpellCastDefinition> definitions = new ArrayList<>(learnedSpells.size());
        for (Map.Entry<ResourceLocation, Integer> entry : learnedSpells.entrySet()) {
            ResourceLocation spellId = entry.getKey();
            AbstractSpell spell = SpellRegistry.getSpell(spellId);
            if (spell == null || !spell.isEnabled()) {
                continue;
            }
            int configuredCooldown = SpellMobConfig.learnedCooldownTicks();
            int cooldown = configuredCooldown >= 0 ? configuredCooldown
                    : SpellMobConfig.learnedSpellCooldown(spell.getSpellCooldown());
            definitions.add(new SpellCastDefinition(spellId, Math.max(1, entry.getValue()),
                    cooldown, priority, range,
                    TargetSpec.attackTarget(), -1, 10, CastConditions.NONE, false, null, 1.0D, true,
                    "learned/" + spellId));
        }
        return definitions;
    }

    /**
     * Teaches this caster a spell from a scroll.
     *
     * @param spellId the spell on the scroll
     * @param level   the level written on the scroll
     * @return true when the spell is new or replaced a weaker copy
     */
    public boolean learnScrollSpell(ResourceLocation spellId, int level) {
        AbstractSpell spell = SpellRegistry.getSpell(spellId);
        int max = spell == null ? 1 : Math.max(1, spell.getMaxLevel());
        int clamped = Math.max(1, Math.min(level, max));
        int current = learnedSpells.getOrDefault(spellId, 0);
        if (clamped <= current) {
            return false;
        }
        learnedSpells.put(spellId, clamped);
        mob.addTag(CASTER_TAG);
        Logs.debug("{} learned {} level {} from a scroll", mob, spellId, clamped);
        return true;
    }

    /**
     * @return the level a learned spell is cast at, or 0 when it is unknown
     */
    public int learnedLevel(ResourceLocation spellId) {
        return learnedSpells.getOrDefault(spellId, 0);
    }

    public int learnedCount() {
        return learnedSpells.size();
    }

    public long scrollCooldownRemaining(long now) {
        return Math.max(0L, this.scrollCooldownEnd - now);
    }

    public void startScrollCooldown(long now, int ticks) {
        this.scrollCooldownEnd = now + Math.max(0, ticks);
    }

    /** Clears the scroll learning cooldown, used by {@code /spellmob resetcooldown}. */
    public void clearScrollCooldown() {
        this.scrollCooldownEnd = 0L;
    }

    @Nullable
    private TamableAnimal tamedPet() {
        return mob instanceof TamableAnimal tame && tame.isTame() ? tame : null;
    }

    private float petManaCost(AbstractSpell spell, int level) {
        double percent = SpellMobConfig.petManaCostPercent() / 100.0D;
        return (float) Math.max(0.0D,
                spell.getManaCost(level) * SpellMobConfig.petManaCostMultiplier() * percent);
    }

    /**
     * @return true when the pet may pay for this cast; mobs without an online owner cast for free
     */
    private boolean petCanPayMana(AbstractSpell spell, int level) {
        if (!SpellMobConfig.shareOwnerMana()) {
            return true;
        }
        TamableAnimal pet = tamedPet();
        if (pet == null) {
            return true;
        }
        LivingEntity owner = pet.getOwner();
        if (owner == null) {
            // The owner is offline or in another dimension; do not silently disable the pet.
            return true;
        }
        return MagicData.getPlayerMagicData(owner).getMana() >= petManaCost(spell, level);
    }

    private void spendPetMana(AbstractSpell spell, int level) {
        if (!SpellMobConfig.shareOwnerMana()) {
            return;
        }
        TamableAnimal pet = tamedPet();
        if (pet == null) {
            return;
        }
        LivingEntity owner = pet.getOwner();
        if (owner == null) {
            return;
        }
        float cost = petManaCost(spell, level);
        if (cost <= 0.0F) {
            return;
        }
        MagicData data = MagicData.getPlayerMagicData(owner);
        data.setMana(Math.max(0.0F, data.getMana() - cost));
    }

    /**
     * Copies one spell of a defeated player into the learned list.
     *
     * @param playerId the player that lost the previous fight
     * @return true when the boss learned something, so its goals must be rebuilt
     */
    private boolean learnFrom(UUID playerId) {
        if (learnedSpells.size() >= SpellMobConfig.maxLearnedSpells()) {
            return false;
        }
        List<ResourceLocation> cast = encounterSpells.get(playerId);
        if (cast == null) {
            return false;
        }
        List<ResourceLocation> candidates = new ArrayList<>();
        for (ResourceLocation spellId : cast) {
            if (learnedSpells.containsKey(spellId) || SpellMobConfig.isAdaptiveBlacklisted(spellId)) {
                continue;
            }
            AbstractSpell spell = SpellRegistry.getSpell(spellId);
            if (spell == null || !spell.isEnabled()) {
                continue;
            }
            candidates.add(spellId);
        }
        if (candidates.isEmpty()) {
            return false;
        }
        ResourceLocation picked = candidates.get(mob.getRandom().nextInt(candidates.size()));
        AbstractSpell pickedSpell = SpellRegistry.getSpell(picked);
        learnedSpells.put(picked, pickedSpell == null ? 1 : pickedSpell.getMaxLevel());
        Logs.debug("{} learned {} from {}", mob, picked, playerId);
        return true;
    }

    @Override
    public boolean isReady(SpellCastDefinition definition) {
        if (resolving || active != null || !SpellMobConfig.enabled() || mob.tickCount < retryAtTick) {
            return false;
        }
        if (!mob.isAlive() || mob.isDeadOrDying()) {
            return false;
        }
        // A sitting pet stays put; casting would either yank it out of the sit or waste a spell.
        if (mob instanceof TamableAnimal tame && tame.isOrderedToSit()) {
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
            // The caster is dying, so the alive and cooldown checks do not apply. The blacklist and
            // the master switch still do, and a channel already in flight must not swallow the
            // death spell: it is closed here and the death spell then starts normally.
            if (resolving || !SpellMobConfig.enabled()) {
                return false;
            }
            ServerLevel forcedLevel = level();
            if (forcedLevel == null
                    || SpellMobConfig.isEntityBlacklisted(entityId)
                    || SpellMobConfig.isDimensionBlacklisted(forcedLevel.dimension().location())) {
                return false;
            }
            if (active != null) {
                cancel(CancelReason.CASTER_DIED);
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

        Vec3 aim = position != null ? position : target != null && target != mob ? target.getEyePosition() : null;
        if (aim != null) {
            mob.lookAt(EntityAnchorArgument.Anchor.EYES, aim);
            mob.getLookControl().setLookAt(aim.x, aim.y, aim.z, 30.0F, 30.0F);
        }

        try {
            if (!spell.checkPreCastConditions(level, definition.level(), mob, magicData())) {
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
                : Math.max(0, spell.getEffectiveCastTime(definition.level(), mob));
        int cooldownTicks = definition.cooldownTicks() >= 0 ? definition.cooldownTicks()
                : Math.max(1, spell.getSpellCooldown() * 20);
        if (!forced && !petCanPayMana(spell, definition.level())) {
            // A pet without enough of its owner's mana simply waits; it must not burn the cooldown.
            this.retryAtTick = mob.tickCount + FAILED_CAST_BACKOFF_TICKS;
            return false;
        }

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
            magicData().initiateCast(spell, definition.level(), castTime, CastSource.MOB,
                    SpellSelectionManager.MAINHAND);
            spell.onServerPreCast(level, definition.level(), mob, magicData());
            spendPetMana(spell, definition.level());
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

        this.retryAtTick = 0;
        ActiveCast cast = new ActiveCast(definition, spell, castTime, target, position,
                channel, scaledCooldown, key);
        this.active = cast;
        // The global cooldown starts with any successful cast, channels included: the caster is
        // busy anyway, and this keeps "one spell per window" true for channel-heavy packs too.
        int global = SpellMobConfig.globalCooldownTicks();
        if (global > 0) {
            this.globalCooldown = Math.max(this.globalCooldown, global);
        }
        Logs.debug("{} starts casting {} (level {}, {} ticks)", mob, definition.spellId(), definition.level(), castTime);
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
        return Math.max(cooldowns.getOrDefault(cooldownKey(definition), 0), this.globalCooldown);
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

    private @Nullable ServerLevel level() {
        return mob.level() instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (this.lockedVariant >= 0) {
            // Persisted so a boss does not fall back a phase when its chunk reloads mid fight.
            tag.putInt("lockedVariant", this.lockedVariant);
        }
        if (!cooldowns.isEmpty()) {
            CompoundTag stored = new CompoundTag();
            cooldowns.forEach((key, ticks) -> stored.putInt(key, ticks));
            tag.put("cooldowns", stored);
        }
        if (this.globalCooldown > 0) {
            tag.putInt("globalCooldown", this.globalCooldown);
        }
        if (this.scrollCooldownEnd > 0L) {
            tag.putLong("scrollCooldownEnd", this.scrollCooldownEnd);
        }
        saveAdaptive(tag);
        return tag;
    }

    void load(CompoundTag tag) {
        this.lockedVariant = tag.contains("lockedVariant") ? tag.getInt("lockedVariant") : -1;
        this.globalCooldown = tag.contains("globalCooldown") ? Math.max(0, tag.getInt("globalCooldown")) : 0;
        this.scrollCooldownEnd = tag.contains("scrollCooldownEnd") ? Math.max(0L, tag.getLong("scrollCooldownEnd")) : 0L;
        loadAdaptive(tag);
        cooldowns.clear();
        CompoundTag stored = tag.getCompound("cooldowns");
        for (String key : stored.getAllKeys()) {
            int ticks = stored.getInt(key);
            if (!key.isEmpty() && ticks > 0) {
                cooldowns.put(key, ticks);
            }
        }
    }

    private void saveAdaptive(CompoundTag tag) {
        if (learnedSpells.isEmpty() && engagedPlayers.isEmpty() && defeatedPlayers.isEmpty()
                && encounterSpells.isEmpty()) {
            return;
        }
        CompoundTag state = new CompoundTag();
        state.put("learned", learnedTags());
        state.put("engaged", uuids(engagedPlayers));
        state.put("defeated", uuids(defeatedPlayers));
        CompoundTag encounters = new CompoundTag();
        encounterSpells.forEach((uuid, spells) -> encounters.put(uuid.toString(), strings(spells)));
        state.put("encounters", encounters);
        tag.put("adaptive", state);
    }

    private void loadAdaptive(CompoundTag tag) {
        learnedSpells.clear();
        engagedPlayers.clear();
        defeatedPlayers.clear();
        encounterSpells.clear();
        if (!tag.contains("adaptive")) {
            return;
        }
        CompoundTag state = tag.getCompound("adaptive");
        readLearned(state.getList("learned", 10));
        engagedPlayers.addAll(uuids(state.getList("engaged", 8)));
        defeatedPlayers.addAll(uuids(state.getList("defeated", 8)));
        CompoundTag encounters = state.getCompound("encounters");
        for (String key : encounters.getAllKeys()) {
            try {
                encounterSpells.put(UUID.fromString(key), ids(encounters.getList(key, 8)));
            } catch (IllegalArgumentException ignored) {
                // A corrupt key is simply dropped; it cannot match a player anyway.
            }
        }
    }

    private static ListTag strings(List<ResourceLocation> ids) {
        ListTag list = new ListTag();
        for (ResourceLocation id : ids) {
            list.add(StringTag.valueOf(id.toString()));
        }
        return list;
    }

    private ListTag learnedTags() {
        ListTag list = new ListTag();
        learnedSpells.forEach((id, level) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id.toString());
            entry.putInt("level", level);
            list.add(entry);
        });
        return list;
    }

    private void readLearned(ListTag list) {
        for (int i = 0; i < list.size(); i++) {
            if (list.getElementType() == 10) {
                CompoundTag entry = list.getCompound(i);
                ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
                if (id != null) {
                    AbstractSpell spell = SpellRegistry.getSpell(id);
                    int max = spell == null ? 1 : Math.max(1, spell.getMaxLevel());
                    learnedSpells.put(id, Math.max(1, Math.min(entry.getInt("level"), max)));
                }
                continue;
            }
            // Older worlds stored plain spell ids; those are cast at the spell's maximum level.
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) {
                AbstractSpell spell = SpellRegistry.getSpell(id);
                learnedSpells.put(id, spell == null ? 1 : Math.max(1, spell.getMaxLevel()));
            }
        }
    }

    private static ListTag uuids(Set<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) {
            list.add(StringTag.valueOf(id.toString()));
        }
        return list;
    }

    private static List<ResourceLocation> ids(ListTag list) {
        List<ResourceLocation> ids = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static Set<UUID> uuids(ListTag list) {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            try {
                ids.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed entries rather than failing the whole tag.
            }
        }
        return ids;
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

        private ActiveCast(SpellCastDefinition definition, AbstractSpell spell, int totalTicks,
                           @Nullable LivingEntity target, @Nullable Vec3 position,
                           boolean channel, int cooldownTicks, String cooldownKey) {
            this.definition = definition;
            this.spell = spell;
            this.level = definition.level();
            this.totalTicks = totalTicks;
            this.target = target;
            this.position = position;
            this.channel = channel;
            this.cooldownTicks = cooldownTicks;
            this.cooldownKey = cooldownKey;
        }
    }
}
