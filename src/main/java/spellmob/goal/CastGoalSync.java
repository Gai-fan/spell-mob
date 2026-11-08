package spellmob.goal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import spellmob.capability.CancelReason;
import spellmob.capability.SpellCaster;
import spellmob.capability.SpellCasterAttachments;
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
        // No rules and no caster means this mob cannot carry any of our goals, so the list copy can
        // be skipped. A caster is kept: it may still hold goals left behind by a removed rule.
        if (rules == null && spellCaster == null) {
            return;
        }
        for (WrappedGoal wrapped : new ArrayList<>(mob.goalSelector.getAvailableGoals())) {
            if (wrapped.getGoal() instanceof CastSpellGoal goal && goal.mob() == mob) {
                mob.goalSelector.removeGoal(goal);
            }
        }
        int index = rules == null
                ? -1
                : (spellCaster == null ? rules.variantIndex(mob) : spellCaster.effectiveVariantIndex());
        SpellCasterData data = rules == null ? null : rules.resolve(index);
        // Learned spells live outside the data pack: they are rebuilt from the caster state on
        // every sync so a /reload or a variant change never wipes what a boss has learned. They
        // share the ordering with the data pack goals, so learnedPriority really applies; ties
        // keep the data pack spell first because the sort is stable.
        List<SpellCastDefinition> goals = new ArrayList<>();
        if (data != null) {
            goals.addAll(data.goals());
        }
        if (spellCaster != null) {
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
                rules == null ? "learned only" : rules.describeMatch(mob));
    }
}
