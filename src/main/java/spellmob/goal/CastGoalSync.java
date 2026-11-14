package spellmob.goal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import spellmob.capability.CancelReason;
import spellmob.capability.SpellCaster;
import spellmob.capability.SpellCasterAttachments;
import spellmob.config.SpellMobConfig;
import spellmob.data.SpellCastDefinition;
import spellmob.data.SpellCasterData;
import spellmob.data.SpellCasterRegistry;
import spellmob.data.SpellCasterRules;
import spellmob.util.Logs;

/**
 * Keeps the goal selector in sync with the loaded data packs.
 *
 * <p>Every mob owns one {@link CastSpellGoal} per configured spell. When data packs change, the old
 * goals are removed through {@code GoalSelector#removeGoal} (which stops them and releases their
 * flags) and rebuilt from the new definitions.</p>
 */
public final class CastGoalSync {

    private static volatile boolean resyncRequested;

    /**
     * Mobs whose vanilla attack goals this class removed. The caster owns the actual goal
     * instances, but a data pack reload can delete the rules before the caster is looked up; the
     * marker lets {@link #sync(Mob)} reach the restore path instead of returning early. Weak keys
     * keep unloaded mobs collectable.
     */
    private static final Set<Mob> ATTACK_GOALS_REMOVED =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private CastGoalSync() {
    }

    /** Marks the loaded world for a resync on the next server tick. */
    public static void requestResync() {
        resyncRequested = true;
    }

    /**
     * Rebuilds the casting goals of every loaded mob. Called once at the end of the server tick that
     * followed a data pack reload.
     *
     * @param server the running server
     */
    public static void tick(MinecraftServer server) {
        if (!resyncRequested) {
            return;
        }
        resyncRequested = false;
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                if (entity instanceof Mob mob) {
                    sync(mob);
                    count++;
                }
            }
        }
        Logs.debug("Resynced casting goals for {} entities", count);
    }

    /**
     * Removes and re-adds the casting goals of a single mob.
     *
     * @param mob the mob to sync
     */
    public static void sync(Mob mob) {
        if (mob.level().isClientSide()) {
            return;
        }
        SpellCasterRules rules = SpellCasterRegistry.get(mob.getType());
        SpellCaster spellCaster = SpellCasterAttachments.peek(mob) instanceof SpellCaster caster ? caster : null;
        // Pets that learned a scroll have no data pack rules, but their learned goals still have to
        // be rebuilt, hence the tag check. Mobs without rules and without the tag never own a
        // caster, so returning early keeps them allocation free -- unless this class already removed
        // their vanilla attack goals and a reload deleted the rule that asked for it.
        if (rules == null && !mob.getTags().contains(SpellCaster.CASTER_TAG)
                && !ATTACK_GOALS_REMOVED.contains(mob)) {
            return;
        }
        int index = rules == null
                ? -1
                : (spellCaster == null ? rules.variantIndex(mob) : spellCaster.effectiveVariantIndex());
        // The flag can change with the variant, so every sync either removes the vanilla attack
        // goals or puts back the ones a previous sync removed. NeoForge attachments are lazy: a
        // mob whose rules ask for the flag has no caster yet on the first sync, so create it here
        // before removing anything (the removed goals are stored on that caster).
        if (rules != null && rules.disableAttackGoals(index)) {
            if (spellCaster == null) {
                spellCaster = SpellCasterAttachments.get(mob) instanceof SpellCaster caster ? caster : null;
            }
            if (spellCaster != null && removeVanillaAttackGoals(mob, spellCaster)) {
                ATTACK_GOALS_REMOVED.add(mob);
            }
        } else if (spellCaster != null) {
            ATTACK_GOALS_REMOVED.remove(mob);
            spellCaster.restoreAttackGoals();
        }
        for (WrappedGoal wrapped : new ArrayList<>(mob.goalSelector.getAvailableGoals())) {
            if (wrapped.getGoal() instanceof CastSpellGoal goal && goal.mob() == mob) {
                mob.goalSelector.removeGoal(goal);
            }
            if (wrapped.getGoal() instanceof PetSpellGoal goal && goal.mob() == mob) {
                mob.goalSelector.removeGoal(goal);
            }
        }
        SpellCasterData data = rules == null ? null : rules.resolve(index);
        // Learned spells live outside the data pack: they are rebuilt from the caster state on
        // every sync so a /reload or a variant change never wipes what a boss has learned. They
        // share the ordering with the data pack goals, so learnedPriority really applies; ties
        // keep the data pack spell first because the sort is stable.
        List<SpellCastDefinition> goals = new ArrayList<>();
        if (data != null) {
            goals.addAll(data.goals());
        }
        // A pet on "smart" spell selection owns a single PetSpellGoal that picks its spell from
        // the learned list on every activation; one goal per spell would freeze the choice.
        boolean smartPet = spellCaster != null && spellCaster.usesSmartPetSelection();
        if (spellCaster != null && !smartPet) {
            goals.addAll(spellCaster.learnedDefinitions());
        }
        // Vanilla runs the first goal whose canUse() returns true in insertion order; priority
        // only decides whether a running goal may be replaced. Sorting by priority keeps the
        // documented "lower first" order for our own goals, stable for ties.
        goals.sort(Comparator.comparingInt(SpellCastDefinition::priority));
        for (SpellCastDefinition definition : goals) {
            mob.goalSelector.addGoal(definition.priority(), new CastSpellGoal(mob, definition));
        }
        int added = goals.size();
        if (smartPet) {
            mob.goalSelector.addGoal(SpellMobConfig.petCastPriority(mob.getType()), new PetSpellGoal(mob));
            added++;
        }
        if (added == 0) {
            // A reload removed the rules, or the resolved variant no longer carries any spell: an
            // event triggered cast would stay frozen forever, because nothing ticks a mob without
            // rules, and its old boss bar would stay on screen.
            if (spellCaster != null) {
                spellCaster.cancel(CancelReason.TARGET_LOST);
                spellCaster.removeBossBar();
            }
            Logs.debug("{} has no casting spells", mob);
            return;
        }
        Logs.debug("{} now knows {} casting goal(s) [{}]", mob, added,
                rules == null ? "learned only" : rules.describeMatch(index));
    }

    /**
     * Removes the vanilla melee and ranged attack goals of one mob, remembering them for a later
     * restore. Target goals are deliberately left alone: a boss that cannot damage the player may
     * still need to notice and chase them for its spells to find a target.
     *
     * @param mob    the mob whose goals are trimmed
     * @param caster the caster that stores the removed goals
     * @return true when at least one goal was removed by this call
     */
    private static boolean removeVanillaAttackGoals(Mob mob, SpellCaster caster) {
        boolean removed = false;
        for (WrappedGoal wrapped : new ArrayList<>(mob.goalSelector.getAvailableGoals())) {
            Goal goal = wrapped.getGoal();
            if (isVanillaAttackGoal(goal)) {
                mob.goalSelector.removeGoal(goal);
                caster.rememberRemovedAttackGoal(wrapped.getPriority(), goal);
                removed = true;
            }
        }
        return removed;
    }

    private static boolean isVanillaAttackGoal(Goal goal) {
        // instanceof also catches the many vanilla subclasses, e.g. ZombieAttackGoal extends
        // MeleeAttackGoal and Drowned.DrownedAttackGoal extends ZombieAttackGoal.
        return goal instanceof MeleeAttackGoal
                || goal instanceof RangedAttackGoal
                || goal instanceof RangedBowAttackGoal<?>
                || goal instanceof RangedCrossbowAttackGoal<?>;
    }
}