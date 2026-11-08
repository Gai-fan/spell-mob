package spellmob.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import spellmob.capability.CancelReason;
import spellmob.capability.ISpellCaster;
import spellmob.capability.SpellCasterAttachments;
import spellmob.data.SpellCastDefinition;
import spellmob.util.TargetResolver;

/**
 * Goal wrapper around one periodic spell.
 *
 * <p>The goal does not tick the cast itself: the {@link ISpellCaster} capability owns cast
 * progression, and this goal only decides when a cast may start and holds the {@code MOVE} and
 * {@code LOOK} flags while it runs. That split is what makes an instant cast, a long cast and an
 * event triggered cast share exactly one code path.</p>
 */
public final class CastSpellGoal extends Goal {

    private final Mob mob;
    private final SpellCastDefinition definition;

    private int nextCheckTick;
    private boolean lastResult;
    private boolean started;
    private @Nullable LivingEntity plannedTarget;
    private @Nullable Vec3 plannedPosition;

    /**
     * @param mob        the caster
     * @param definition the spell this goal is responsible for
     */
    public CastSpellGoal(Mob mob, SpellCastDefinition definition) {
        this.mob = mob;
        this.definition = definition;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** @return the mob this goal belongs to */
    public Mob mob() {
        return this.mob;
    }

    /** @return the spell this goal is responsible for */
    public SpellCastDefinition definition() {
        return this.definition;
    }

    @Override
    public boolean canUse() {
        if (this.mob.level().isClientSide()) {
            return false;
        }
        if (this.mob.tickCount < this.nextCheckTick) {
            return this.lastResult;
        }
        this.nextCheckTick = this.mob.tickCount + this.definition.checkInterval();
        this.lastResult = this.evaluate();
        return this.lastResult;
    }

    private boolean evaluate() {
        this.plannedTarget = null;
        this.plannedPosition = null;
        ISpellCaster caster = caster();
        if (caster == null || !caster.isReady(this.definition)) {
            return false;
        }
        TargetResolver.Resolved resolved = TargetResolver.resolve(this.mob, this.definition, null);
        if (resolved == null) {
            return false;
        }
        if (!this.definition.conditions().test(this.mob, resolved.entity())) {
            return false;
        }
        this.plannedTarget = resolved.entity();
        this.plannedPosition = resolved.position();
        return true;
    }

    @Override
    public void start() {
        ISpellCaster caster = caster();
        if (caster == null) {
            this.started = false;
            return;
        }
        if (!this.evaluate()) {
            this.started = false;
            return;
        }
        this.started = caster.start(this.definition, this.plannedTarget, this.plannedPosition);
    }

    @Override
    public boolean canContinueToUse() {
        ISpellCaster caster = caster();
        return this.started && caster != null && caster.isCasting(this.definition);
    }

    @Override
    public void tick() {
        // Cast progression happens in ISpellCaster#tick; the goal only keeps the flags.
    }

    /**
     * A running cast must not be cancelled by an unrelated vanilla goal.
     *
     * <p>Vanilla replaces a goal whenever a higher priority goal becomes usable; {@code stop()}
     * then cancels the cast, so a mob standing in water (float goal, priority 0) or panicking
     * could drop a 40 tick cast. Damage based interruption is handled by the caster settings
     * instead, which keeps "a boss should not lose its cast to pathfinding" and "a player can
     * interrupt by hitting it" separate.</p>
     *
     * @return false while this goal owns a cast, true otherwise
     */
    @Override
    public boolean isInterruptable() {
        return !this.started;
    }

    @Override
    public void stop() {
        ISpellCaster caster = caster();
        if (this.started && caster != null && caster.isCasting(this.definition)) {
            caster.cancel(CancelReason.GOAL_STOPPED);
        }
        this.started = false;
        this.plannedTarget = null;
        this.plannedPosition = null;
        this.lastResult = false;
        // Do not re-evaluate on the very next tick: when a spell's "chance" roll fails the goal is
        // stopped immediately, and resetting the counter to zero would roll once per tick instead
        // of once per check interval.
        this.nextCheckTick = this.mob.tickCount + this.definition.checkInterval();
    }

    private @Nullable ISpellCaster caster() {
        return SpellCasterAttachments.get(this.mob);
    }
}
