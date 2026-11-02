package spellmob.presentation;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions;
import io.redspace.ironsspellbooks.particle.SparkParticleOptions;
import io.redspace.ironsspellbooks.registries.ParticleRegistry;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import spellmob.capability.CancelReason;
import spellmob.config.SpellMobConfig;
import spellmob.util.Logs;

/**
 * Server side particle feedback for mob casting.
 *
 * <p>Everything here is cosmetic and best effort: a failing effect must never break a cast, so the
 * public methods swallow runtime exceptions and silently do nothing when the config turns them
 * off. Particles are sent with the player targeted overload of {@code ServerLevel#sendParticles},
 * so only players inside the configured radius receive packets.</p>
 *
 * <p>The circle is built from individual dust particles because vanilla has no ring particle: a
 * single {@code sendParticles} call spreads its particles randomly inside a box, which would look
 * like a fuzzy square rather than a circle. One emission therefore costs one call per ring dot,
 * which is why the density is configurable and the interval is a few ticks rather than every
 * tick.</p>
 */
public final class CastingPresentation {

    /** Ticks between two circle emissions; 3 ticks is smooth enough without flooding the client. */
    private static final int EMIT_INTERVAL_TICKS = 3;

    /** Boss circles are denser, so they emit a little less often than the plain dust ring. */
    private static final int BOSS_EMIT_INTERVAL_TICKS = 4;

    /** Ticks between two emissions of the vertical hexagram in front of the caster. */
    private static final int SIGIL_INTERVAL_TICKS = 6;

    /**
     * Fallback colour used when a spell has no school or the school has no usable colour.
     *
     * <p>Pure black is deliberately chosen because it is almost impossible for a real school: the
     * colour is derived from the school's display name style, and anything that dark would be
     * rejected by {@link #isUsable(Vector3f)} anyway. A black circle therefore means "the colour
     * could not be resolved" instead of quietly pretending to be a school colour.</p>
     */
    private static final float DEFAULT_RED = 0.0F;
    private static final float DEFAULT_GREEN = 0.0F;
    private static final float DEFAULT_BLUE = 0.0F;

    private CastingPresentation() {
    }

    /**
     * Emits the first circle when a cast starts.
     *
     * @param mob        the caster
     * @param spell      the spell being cast, used for the school colour
     * @param totalTicks the cast time in ticks; instant casts get no circle
     */
    public static void onCastStart(Mob mob, AbstractSpell spell, int totalTicks) {
        if (totalTicks <= 0 || !SpellMobConfig.presentationEnabled()) {
            return;
        }
        try {
            ServerLevel level = levelOf(mob);
            if (level == null) {
                return;
            }
            Vector3f color = colorOf(spell);
            if (SpellMobConfig.isBossCaster(mob)) {
                emitBossRing(level, mob, ringRadius(mob, 0.0F) * 1.35D, 0.0F, 14);
                if (SpellMobConfig.bossExtras()) {
                    emitHalo(level, mob, 0.0F);
                    emitBlastwave(level, mob, color, 0.6F);
                }
            } else {
                emitRing(level, mob, color, ringRadius(mob, 0.0F), 0.0F);
                emitAura(level, mob);
            }
            if (SpellMobConfig.sigil()) {
                emitFrontSigil(level, mob, color, 0.0F);
            }
        } catch (RuntimeException exception) {
            Logs.debug("Casting presentation failed on start: {}", exception.getMessage());
        }
    }

    /**
     * Emits the progress circle. The radius breathes with the cast progress and the ring rotates
     * continuously, so players can tell how far along a cast is without opening any UI.
     *
     * @param mob        the caster
     * @param spell      the spell being cast, used for the school colour
     * @param ticks      ticks elapsed since the cast started
     * @param totalTicks total cast time in ticks
     */
    public static void onCastTick(Mob mob, AbstractSpell spell, int ticks, int totalTicks) {
        if (!SpellMobConfig.presentationEnabled()) {
            return;
        }
        boolean boss = SpellMobConfig.isBossCaster(mob);
        boolean ringTick = ticks % EMIT_INTERVAL_TICKS == 0;
        boolean bossTick = boss && ticks % BOSS_EMIT_INTERVAL_TICKS == 0;
        if (!ringTick && !bossTick) {
            return;
        }
        try {
            ServerLevel level = levelOf(mob);
            if (level == null) {
                return;
            }
            float progress = totalTicks <= 0 ? 1.0F : Math.min(1.0F, (float) ticks / (float) totalTicks);
            Vector3f color = colorOf(spell);
            double radius = ringRadius(mob, (float) Math.sin(Math.PI * progress));
            if (boss) {
                if (ringTick) {
                    emitBossRing(level, mob, radius * 1.35D, ticks * 0.22F, 12);
                }
                if (bossTick && SpellMobConfig.bossExtras()) {
                    emitHalo(level, mob, ticks * 0.12F);
                    emitPillars(level, mob, color, radius);
                    if (ticks % 20 == 0) {
                        emitBlastwave(level, mob, color, 0.35F + progress * 0.9F);
                    }
                }
            } else {
                emitRing(level, mob, color, radius, ticks * 0.35F);
                emitAura(level, mob);
                if (progress >= 0.7F) {
                    emitSpark(level, mob, radius);
                }
            }
            if (SpellMobConfig.sigil() && ticks % SIGIL_INTERVAL_TICKS == 0) {
                emitFrontSigil(level, mob, color, ticks * 0.05F);
            }
        } catch (RuntimeException exception) {
            Logs.debug("Casting presentation failed while ticking: {}", exception.getMessage());
        }
    }

    /**
     * Closes a boss cast with a blast and a wide smoke ring at the caster.
     *
     * @param mob        the caster
     * @param spell      the spell that finished, used for the school colour
     * @param totalTicks the cast time in ticks; instant casts are ignored
     */
    public static void onCastFinish(Mob mob, AbstractSpell spell, int totalTicks) {
        if (totalTicks <= 0 || !SpellMobConfig.presentationEnabled() || !SpellMobConfig.isBossCaster(mob)
                || !SpellMobConfig.bossExtras()) {
            return;
        }
        try {
            ServerLevel level = levelOf(mob);
            if (level == null) {
                return;
            }
            Vector3f color = colorOf(spell);
            emitBlastwave(level, mob, color, 1.8F);
            emitBossRing(level, mob, ringRadius(mob, 0.0F) * 1.9D, 0.0F, 20);
        } catch (RuntimeException exception) {
            Logs.debug("Casting presentation failed on finish: {}", exception.getMessage());
        }
    }

    /**
     * Shows a smoke puff and an extinguish sound when a cast is interrupted by gameplay.
     *
     * <p>Only {@link CancelReason#visible()} reasons trigger this: a cast that ends because the mob
     * was unloaded or the data pack reloaded should not produce any effect.</p>
     *
     * @param mob    the caster
     * @param reason why the cast stopped
     */
    public static void onCastCancelled(Mob mob, CancelReason reason) {
        if (!reason.visible() || !SpellMobConfig.presentationEnabled() || !SpellMobConfig.interruptFeedback()) {
            return;
        }
        try {
            ServerLevel level = levelOf(mob);
            if (level == null) {
                return;
            }
            double x = mob.getX();
            double y = mob.getY() + mob.getBbHeight() * 0.5D;
            double z = mob.getZ();
            double limitSqr = limitSqr();
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(x, y, z) <= limitSqr) {
                    level.sendParticles(player, ParticleTypes.LARGE_SMOKE, false, x, y, z, 6, 0.25D, 0.2D, 0.25D, 0.01D);
                }
            }
            if (SpellMobConfig.isBossCaster(mob)) {
                emitBossRing(level, mob, ringRadius(mob, 0.0F) * 1.5D, 0.0F, 12);
            }
            level.playSound(null, x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.7F, 0.7F);
        } catch (RuntimeException exception) {
            Logs.debug("Casting presentation failed on cancel: {}", exception.getMessage());
        }
    }

    private static void emitRing(ServerLevel level, Mob mob, Vector3f color, double radius, float phase) {
        int points = points() * 2;
        if (points <= 0) {
            return;
        }
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(color), 0.6F);
        double centerX = mob.getX();
        double centerY = mob.getY() + 0.06D;
        double centerZ = mob.getZ();
        for (int index = 0; index < points; index++) {
            double angle = phase + Math.PI * 2.0D * index / points;
            send(level, dust, centerX + Math.cos(angle) * radius, centerY, centerZ + Math.sin(angle) * radius);
        }
    }

    private static void emitAura(ServerLevel level, Mob mob) {
        double x = mob.getX();
        double y = mob.getY() + mob.getBbHeight() * 0.75D;
        double z = mob.getZ();
        double limitSqr = limitSqr();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= limitSqr) {
                level.sendParticles(player, ParticleTypes.ENCHANT, false, x, y, z, 4, 0.45D, 0.55D, 0.45D, 0.02D);
            }
        }
    }

    private static void emitSpark(ServerLevel level, Mob mob, double radius) {
        double angle = mob.getRandom().nextDouble() * Math.PI * 2.0D;
        send(level, ParticleTypes.END_ROD,
                mob.getX() + Math.cos(angle) * radius,
                mob.getY() + 0.15D,
                mob.getZ() + Math.sin(angle) * radius);
    }

    /**
     * Boss ring built from Iron's Spellbooks ring smoke, which is wider and softer than dust.
     *
     * @param level  the server level
     * @param mob    the caster
     * @param radius ring radius in blocks
     * @param phase  rotation offset in radians
     * @param points number of smoke puffs on the ring
     */
    private static void emitBossRing(ServerLevel level, Mob mob, double radius, float phase, int points) {
        if (points <= 0) {
            return;
        }
        ParticleOptions smoke = ParticleRegistry.RING_SMOKE_PARTICLE.get();
        double centerX = mob.getX();
        double centerY = mob.getY() + 0.05D;
        double centerZ = mob.getZ();
        for (int index = 0; index < points; index++) {
            double angle = phase + Math.PI * 2.0D * index / points;
            send(level, smoke, centerX + Math.cos(angle) * radius, centerY, centerZ + Math.sin(angle) * radius);
        }
    }

    /**
     * Slow counter rotating rune halo floating above the caster.
     *
     * @param level the server level
     * @param mob   the caster
     * @param phase rotation offset in radians
     */
    private static void emitHalo(ServerLevel level, Mob mob, float phase) {
        ParticleOptions frame = ParticleRegistry.PORTAL_FRAME_PARTICLE.get();
        double radius = Math.max(0.9D, mob.getBbWidth() * 1.1D);
        double x = mob.getX();
        double y = mob.getY() + mob.getBbHeight() + 0.35D + 0.12D * Math.sin(phase * 0.7D);
        double z = mob.getZ();
        for (int index = 0; index < 8; index++) {
            double angle = -phase + Math.PI * 2.0D * index / 8.0D;
            send(level, frame, x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius);
        }
    }

    /**
     * Five short spark pillars around the circle, tinted with the school colour.
     *
     * @param level  the server level
     * @param mob    the caster
     * @param color  the school colour
     * @param radius the circle radius
     */
    private static void emitPillars(ServerLevel level, Mob mob, Vector3f color, double radius) {
        SparkParticleOptions spark = new SparkParticleOptions(new Vector3f(color));
        double x = mob.getX();
        double y = mob.getY();
        double z = mob.getZ();
        for (int index = 0; index < 5; index++) {
            double angle = Math.PI * 2.0D * index / 5.0D;
            double px = x + Math.cos(angle) * radius * 1.05D;
            double pz = z + Math.sin(angle) * radius * 1.05D;
            for (int step = 0; step < 3; step++) {
                send(level, spark, px, y + 0.15D + step * 0.35D, pz);
            }
        }
    }

    /**
     * Expanding shockwave ring, used for the periodic pulse and the finishing blast.
     *
     * @param level the server level
     * @param mob   the caster
     * @param color the school colour
     * @param scale the visual scale of the blast
     */
    private static void emitBlastwave(ServerLevel level, Mob mob, Vector3f color, float scale) {
        send(level, new BlastwaveParticleOptions(new Vector3f(color), scale),
                mob.getX(), mob.getY() + 0.1D, mob.getZ());
    }

    /**
     * Vertical hexagram floating in front of the caster, facing the direction it is looking.
     *
     * <p>The figure is drawn in the plane spanned by the caster's look direction, so it stays
     * readable from the side instead of collapsing into a line. Edges use the vanilla
     * {@code entity_effect} particle tinted with the school colour, and the six vertices get a
     * bright {@code end_rod} dot so the shape reads clearly at night.</p>
     *
     * @param level the server level
     * @param mob   the caster
     * @param color the school colour
     * @param phase rotation offset in radians
     */
    private static void emitFrontSigil(ServerLevel level, Mob mob, Vector3f color, float phase) {
        Vec3 forward = mob.getLookAngle();
        if (forward.lengthSqr() < 1.0E-6D) {
            return;
        }
        forward = forward.normalize();
        Vec3 right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-6D) {
            right = new Vec3(1.0D, 0.0D, 0.0D).cross(forward);
        }
        if (right.lengthSqr() < 1.0E-6D) {
            return;
        }
        right = right.normalize();
        Vec3 up = right.cross(forward).normalize();
        double radius = Math.max(0.85D, Math.min(1.6D, 0.9D + mob.getBbWidth() * 0.35D))
                * SpellMobConfig.sigilScale();
        Vec3 center = new Vec3(mob.getX(), mob.getY() + mob.getBbHeight() * 0.55D + 0.4D, mob.getZ())
                .add(forward.scale(mob.getBbWidth() * 0.5D + 1.35D));
        int detail = SpellMobConfig.sigilDetail();
        int edgePoints = detail <= 1 ? 5 : detail == 2 ? 8 : 11;
        int rgb = (toByte(color.x()) << 16) | (toByte(color.y()) << 8) | toByte(color.z());
        ParticleOptions line = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, rgb);
        for (int triangle = 0; triangle < 2; triangle++) {
            double offset = phase + triangle * Math.PI / 3.0D;
            for (int edge = 0; edge < 3; edge++) {
                double from = offset + Math.PI * 2.0D * edge / 3.0D;
                double to = from + Math.PI * 2.0D / 3.0D;
                Vec3 start = center.add(right.scale(Math.cos(from) * radius)).add(up.scale(Math.sin(from) * radius));
                Vec3 end = center.add(right.scale(Math.cos(to) * radius)).add(up.scale(Math.sin(to) * radius));
                for (int step = 0; step < edgePoints; step++) {
                    Vec3 point = start.lerp(end, step / (double) (edgePoints - 1));
                    send(level, line, point.x, point.y, point.z);
                }
            }
        }
        for (int vertex = 0; vertex < 6; vertex++) {
            double angle = phase + Math.PI * 2.0D * vertex / 6.0D;
            Vec3 point = center.add(right.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
            send(level, ParticleTypes.END_ROD, point.x, point.y, point.z);
        }
    }

    private static int toByte(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
    }

    /**
     * Sends a single stationary particle to every player close enough to see it.
     *
     * <p>{@code count == 0} makes the client spawn exactly one particle at the given position with
     * the given velocity; the zero velocity keeps the dot on the ring instead of letting it fly
     * away.</p>
     */
    private static void send(ServerLevel level, ParticleOptions particle, double x, double y, double z) {
        double limitSqr = limitSqr();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= limitSqr) {
                level.sendParticles(player, particle, false, x, y, z, 0, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static double ringRadius(Mob mob, float pulse) {
        double base = 0.5D + mob.getBbWidth() * 0.85D;
        return base * (0.8D + 0.45D * pulse);
    }

    private static int points() {
        return switch (SpellMobConfig.presentationDensity()) {
            case 1 -> 8;
            case 2 -> 12;
            case 3 -> 16;
            default -> 0;
        };
    }

    private static double limitSqr() {
        double limit = SpellMobConfig.presentationDistance();
        return limit * limit;
    }

    private static Vector3f colorOf(AbstractSpell spell) {
        try {
            SchoolType school = spell.getSchoolType();
            if (school != null) {
                Vector3f color = school.getTargetingColor();
                if (isUsable(color)) {
                    return new Vector3f(color);
                }
            }
        } catch (RuntimeException exception) {
            Logs.debug("Could not read the school colour of {}: {}", spell.getSpellId(), exception.getMessage());
        }
        return new Vector3f(DEFAULT_RED, DEFAULT_GREEN, DEFAULT_BLUE);
    }

    private static boolean isUsable(@Nullable Vector3f color) {
        if (color == null) {
            return false;
        }
        return Float.isFinite(color.x) && Float.isFinite(color.y) && Float.isFinite(color.z)
                && color.x + color.y + color.z > 0.01F;
    }

    @Nullable
    private static ServerLevel levelOf(Mob mob) {
        return mob.level() instanceof ServerLevel serverLevel ? serverLevel : null;
    }
}
