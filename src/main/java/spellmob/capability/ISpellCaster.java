package spellmob.capability;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import spellmob.data.SpellCastDefinition;

/**
 * Per-mob casting state: cooldowns and the spell that is currently being cast.
 *
 * <p>Attached to {@link net.minecraft.world.entity.Mob} only, on the logical server.</p>
 */
public interface ISpellCaster {

    /** Advances cooldowns and the active cast by one tick. */
    void tick();

    /** @return true while a spell is being cast */
    boolean isCasting();

    /**
     * @param definition the definition to check
     * @return true when the active cast belongs to that exact definition
     */
    boolean isCasting(SpellCastDefinition definition);

    /**
     * @return the definition of the active cast, or null when nothing is being cast
     */
    @Nullable
    SpellCastDefinition castingDefinition();

    /**
     * @return ticks left until the active cast completes, 0 when nothing is being cast
     */
    int castTicksRemaining();

    /**
     * @param definition the definition to check
     * @return true when the caster may start this spell right now
     */
    boolean isReady(SpellCastDefinition definition);

    /**
     * Starts a cast. Instant spells complete inside this call.
     *
     * @param definition the spell to cast
     * @param target     the entity to aim at, may be null for position spells
     * @param position   the position to aim at, may be null for entity spells
     * @return true when the cast started
     */
    boolean start(SpellCastDefinition definition, @Nullable LivingEntity target, @Nullable Vec3 position);

    /**
     * Starts a cast even though the caster is not in a normal state; used by the {@code onDeath}
     * trigger.
     *
     * <p>Skips the usual ready checks (the caster is dying) and always casts instantly, because a
     * dead entity stops ticking and would never finish a cast time.</p>
     *
     * @param definition the spell to cast
     * @param target     the entity to aim at, may be null
     * @param position   the position to aim at, may be null
     * @return true when the cast started
     */
    boolean startForced(SpellCastDefinition definition, @Nullable LivingEntity target, @Nullable Vec3 position);

    /**
     * Cancels the active cast, if any.
     *
     * @param reason why the cast stopped
     */
    void cancel(CancelReason reason);

    /**
     * @param definition the cast definition
     * @return remaining cooldown in ticks for that definition, 0 when ready
     */
    int cooldownTicks(SpellCastDefinition definition);

    /** Clears cooldowns and the active cast. */
    void clear();
}
