package spellmob.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;
import spellmob.capability.CancelReason;
import spellmob.capability.SpellCaster;
import spellmob.capability.SpellCasterAttachments;
import spellmob.config.SpellMobConfig;
import spellmob.data.SpellCastDefinition;

/**
 * Goal of a tamed pet that learned scroll spells and uses {@code pets.spellSelection = smart}.
 *
 * <p>A pet owns one of these goals instead of one goal per learned spell. Every check interval the
 * goal asks its caster which learned spell fits the current target ({@link
 * SpellCaster#pickLearnedSpell(LivingEntity)}) and starts that one, which is what lets a pet switch
 * between melee ranged spells as the player or the target moves without rebuilding its goal
 * selector.</p>
 */
public final class PetSpellGoal extends Goal {

    private static final int CHECK_INTERVAL = 10;

    private final Mob mob;

    private int nextCheckTick;
    private boolean started;
    private @Nullable SpellCastDefinition definition;
    private @Nullable LivingEntity plannedTarget;

    /**
     * @param mob the pet
     */
    public PetSpellGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** @return the mob this goal belongs to */
    public Mob mob() {
        return this.mob;
    }

    @Override
    public boolean canUse() {
        if (this.mob.level().isClientSide()) {
            return false;
        }
        if (this.mob.tickCount < this.nextCheckTick) {
            return false;
        }
        this.nextCheckTick = this.mob.tickCount + CHECK_INTERVAL;
        return this.evaluate();
    }

    private boolean evaluate() {
        this.definition = null;
        this.plannedTarget = null;
        if (!(this.mob instanceof TamableAnimal tame) || !tame.isTame() || tame.isOrderedToSit()) {
            return false;
        }
        SpellCaster caster = caster();
        if (caster == null || caster.isCasting()) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive() || target.isRemoved()) {
            return false;
        }
        double range = SpellMobConfig.petCastRange();
        if (this.mob.distanceToSqr(target) > range * range) {
            return false;
        }
        SpellCastDefinition picked = caster.pickLearnedSpell(target);
        if (picked == null) {
            return false;
        }
        this.definition = picked;
        this.plannedTarget = target;
        return true;
    }

    @Override
    public void start() {
        SpellCaster caster = caster();
        if (caster == null || this.definition == null) {
            this.started = false;
            return;
        }
        this.started = caster.start(this.definition, this.plannedTarget, null);
    }

    @Override
    public boolean canContinueToUse() {
        SpellCaster caster = caster();
        return this.started && caster != null && this.definition != null
                && caster.isCasting(this.definition);
    }

    @Override
    public boolean isInterruptable() {
        return !this.started;
    }

    @Override
    public void stop() {
        SpellCaster caster = caster();
        if (this.started && caster != null && this.definition != null
                && caster.isCasting(this.definition)) {
            caster.cancel(CancelReason.GOAL_STOPPED);
        }
        this.started = false;
        this.definition = null;
        this.plannedTarget = null;
        this.nextCheckTick = this.mob.tickCount + CHECK_INTERVAL;
    }

    private @Nullable SpellCaster caster() {
        return SpellCasterAttachments.peek(this.mob) instanceof SpellCaster spellCaster
                ? spellCaster
                : null;
    }
}
