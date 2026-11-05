package spellmob.util;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import spellmob.data.PlacementSpec;
import spellmob.data.SpellCastDefinition;
import spellmob.data.TargetSort;
import spellmob.data.TargetSpec;
import spellmob.data.TargetType;

/**
 * Turns a {@link TargetSpec} into an actual entity or aim point.
 *
 * <p>All methods are safe to call from the server thread only.</p>
 */
public final class TargetResolver {

    /**
     * Distance used by {@link spellmob.data.PositionMode#BEHIND_TARGET} when the json does not
     * configure one through {@code relativeOffset.x}.
     */
    private static final double DEFAULT_BEHIND_DISTANCE = 2.0D;

    /** How many random points to try before giving up on a placement check. */
    private static final int PLACEMENT_ATTEMPTS = 8;

    /**
     * Result of a target lookup. Exactly one of the two fields is set, except for {@link TargetType#POSITION}
     * spells where only {@code position} is set.
     *
     * @param entity   the entity to aim at, may be null
     * @param position the position to aim at, may be null
     */
    public record Resolved(@Nullable LivingEntity entity, @Nullable Vec3 position) {
    }

    /**
     * Resolves the target of a cast.
     *
     * @param mob        the caster
     * @param definition the cast to resolve
     * @param preferred  an already known target (for example the attacker in an onHurt trigger)
     * @return the resolved target, or null when nothing suitable exists
     */
    public static @Nullable Resolved resolve(Mob mob, SpellCastDefinition definition,
                                             @Nullable LivingEntity preferred) {
        TargetSpec spec = definition.target();
        if (spec.type() == TargetType.SELF) {
            return new Resolved(mob, null);
        }
        if (spec.isPosition()) {
            Vec3 position = positionFor(mob, definition, preferred);
            return position == null ? null : new Resolved(null, position);
        }
        // A trigger may hand us something that just died (onKill). The spell still deserves a place
        // to land, so aim at the corpse instead of failing the whole lookup.
        if (preferred != null && !preferred.isAlive()) {
            return new Resolved(null, preferred.position());
        }
        if (preferred != null && isValid(mob, definition, preferred)) {
            return new Resolved(preferred, null);
        }

        LivingEntity target = switch (spec.type()) {
            case ATTACK_TARGET, HOSTILE_TARGET -> mob.getTarget();
            case LAST_DAMAGE_SOURCE -> mob.getLastHurtByMob();
            case THREATENING_ENTITY -> nearest(mob, definition, candidate -> isThreatening(mob, candidate));
            case NEAREST_HOSTILE -> nearest(mob, definition, candidate -> isHostile(mob, candidate));
            case NEAREST_UNDEAD -> nearest(mob, definition,
                    candidate -> candidate.getMobType() == MobType.UNDEAD);
            case NEAREST_ARTHROPOD -> nearest(mob, definition,
                    candidate -> candidate.getMobType() == MobType.ARTHROPOD);
            case NEAREST_ANIMAL -> nearest(mob, definition, candidate -> candidate instanceof Animal);
            case NEAREST_MONSTER -> nearest(mob, definition, candidate -> candidate instanceof Enemy);
            case NEAREST_PLAYER -> nearest(mob, definition, candidate -> isTargetablePlayer(candidate));
            case OWNER -> mob instanceof TamableAnimal tamable ? tamable.getOwner() : null;
            case NEAREST_ALLY -> nearest(mob, definition, candidate -> isAlly(mob, candidate));
            case SELF, POSITION -> null;
            case RANDOM_HOSTILE -> randomHostile(mob, definition);
        };

        if (target == null || !isValid(mob, definition, target)) {
            return null;
        }
        return new Resolved(target, null);
    }

    private static boolean isTargetablePlayer(LivingEntity candidate) {
        return candidate instanceof Player player && !player.isSpectator();
    }

    private static boolean isHostile(Mob mob, LivingEntity candidate) {
        if (candidate == mob || !candidate.isAlive() || candidate.isRemoved() || candidate.isSpectator()) {
            return false;
        }
        if (candidate instanceof Player player) {
            return !player.isSpectator();
        }
        if (mob.isAlliedTo(candidate) || candidate.getType() == mob.getType()) {
            return false;
        }
        return candidate instanceof Enemy || (candidate instanceof Mob other && other.getTarget() == mob);
    }

    private static boolean isThreatening(Mob mob, LivingEntity candidate) {
        return candidate != mob
                && (candidate == mob.getLastHurtByMob()
                        || (candidate instanceof Mob other && other.getTarget() == mob));
    }

    private static boolean isAlly(Mob mob, LivingEntity candidate) {
        if (candidate == mob || !candidate.isAlive() || candidate instanceof Player) {
            return false;
        }
        return candidate.getType() == mob.getType() || mob.isAlliedTo(candidate);
    }

    private static boolean isValid(Mob mob, SpellCastDefinition definition, LivingEntity target) {
        if (target == mob || !target.isAlive() || target.isRemoved() || target.isSpectator()) {
            return false;
        }
        double range = definition.effectiveRange();
        if (mob.distanceToSqr(target) > range * range) {
            return false;
        }
        if (definition.target().lineOfSight() && !mob.getSensing().hasLineOfSight(target)) {
            return false;
        }
        return definition.target().filters().test(mob, target);
    }

    private static @Nullable LivingEntity nearest(Mob mob, SpellCastDefinition definition,
                                                  Predicate<LivingEntity> predicate) {
        double range = definition.effectiveRange();
        List<LivingEntity> candidates = mob.level().getEntitiesOfClass(LivingEntity.class,
                mob.getBoundingBox().inflate(range),
                candidate -> predicate.test(candidate) && isValid(mob, definition, candidate));
        if (candidates.isEmpty()) {
            return null;
        }
        TargetSort sort = definition.target().sort();
        if (sort == TargetSort.RANDOM) {
            return candidates.get(mob.getRandom().nextInt(candidates.size()));
        }
        Comparator<LivingEntity> byDistance = Comparator.comparingDouble(candidate -> mob.distanceToSqr(candidate));
        Comparator<LivingEntity> comparator = switch (sort) {
            case FARTHEST -> byDistance.reversed();
            case LOWEST_HEALTH -> Comparator.<LivingEntity>comparingDouble(LivingEntity::getHealth)
                    .thenComparing(byDistance);
            case HIGHEST_HEALTH -> Comparator.<LivingEntity>comparingDouble(LivingEntity::getHealth).reversed()
                    .thenComparing(byDistance);
            default -> byDistance;
        };
        return candidates.stream().min(comparator).orElse(null);
    }

    private static @Nullable LivingEntity randomHostile(Mob mob, SpellCastDefinition definition) {
        double range = definition.effectiveRange();
        List<LivingEntity> candidates = mob.level().getEntitiesOfClass(LivingEntity.class,
                mob.getBoundingBox().inflate(range),
                candidate -> isHostile(mob, candidate) && isValid(mob, definition, candidate));
        if (candidates.isEmpty()) {
            return null;
        }
        RandomSource random = mob.getRandom();
        return candidates.get(random.nextInt(candidates.size()));
    }

    private static @Nullable Vec3 positionFor(Mob mob, SpellCastDefinition definition,
                                              @Nullable LivingEntity preferred) {
        TargetSpec spec = definition.target();
        double range = definition.effectiveRange();
        PlacementSpec placement = spec.placement();
        ServerLevel level = mob.level() instanceof ServerLevel serverLevel ? serverLevel : null;
        return switch (spec.positionMode()) {
            case RELATIVE -> mob.position().add(spec.relativeOffset());
            case EYES_LOOK -> mob.getEyePosition().add(mob.getLookAngle().scale(range));
            case FEET_LOOK -> mob.position().add(mob.getLookAngle().scale(range));
            case TARGET_ENTITY -> {
                LivingEntity target = preferred != null && preferred.isAlive() ? preferred : mob.getTarget();
                yield target != null ? target.position() : null;
            }
            case BEHIND_TARGET -> behindTarget(mob, preferred, spec, level, placement);
            case RANDOM_IN_RANGE -> randomPointInRange(mob, range, level, placement);
        };
    }

    /**
     * Computes the point behind the target entity.
     *
     * <p>Falls back to the target's own position when the computed point is rejected by the
     * placement checks, so a caster never loses its aim entirely.</p>
     */
    private static @Nullable Vec3 behindTarget(Mob mob, @Nullable LivingEntity preferred, TargetSpec spec,
                                               @Nullable ServerLevel level, PlacementSpec placement) {
        LivingEntity target = preferred != null && preferred.isAlive() ? preferred : mob.getTarget();
        if (target == null) {
            return null;
        }
        double distance = Math.abs(spec.relativeOffset().x);
        if (distance < 0.5D) {
            distance = DEFAULT_BEHIND_DISTANCE;
        }
        double yaw = Math.toRadians(target.getYRot());
        Vec3 direction = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 point = target.position().subtract(direction.scale(distance))
                .add(0.0D, spec.relativeOffset().y, 0.0D);
        if (level != null && !placement.isSafe(level, point)) {
            return target.position();
        }
        return point;
    }

    /**
     * Picks a random point inside {@code range}, retrying when the placement checks reject it.
     *
     * @return the point, or null when every attempt landed in an avoided fluid
     */
    private static @Nullable Vec3 randomPointInRange(Mob mob, double range, @Nullable ServerLevel level,
                                                     PlacementSpec placement) {
        RandomSource random = mob.getRandom();
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(random.nextDouble()) * range;
            Vec3 point = mob.position().add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
            if (level == null || placement.isSafe(level, point)) {
                return point;
            }
        }
        return null;
    }
}
