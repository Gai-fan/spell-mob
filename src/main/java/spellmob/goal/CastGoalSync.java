package spellmob.goal;

import java.util.ArrayList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
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
        // No rules and no caster means this mob cannot carry any of our goals, so the list copy can
        // be skipped. A caster is kept: it may still hold goals left behind by a removed rule.
        if (rules == null && SpellCasterAttachments.peek(mob) == null) {
            return;
        }
        for (WrappedGoal wrapped : new ArrayList<>(mob.goalSelector.getAvailableGoals())) {
            if (wrapped.getGoal() instanceof CastSpellGoal goal && goal.mob() == mob) {
                mob.goalSelector.removeGoal(goal);
            }
        }
        SpellCasterData data = rules == null ? null : rules.resolve(mob);
        if (data == null || data.isEmpty()) {
            Logs.debug("{} has no casting spells", mob);
            return;
        }
        for (SpellCastDefinition definition : data.goals()) {
            mob.goalSelector.addGoal(definition.priority(), new CastSpellGoal(mob, definition));
        }
        Logs.debug("{} now knows {} casting goal(s) [{}]", mob, data.goals().size(), rules.describeMatch(mob));
    }
}
