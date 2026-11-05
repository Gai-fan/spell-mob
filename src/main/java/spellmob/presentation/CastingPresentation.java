package spellmob.presentation;

import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Map;
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
 * which is why the point count is configurable and the interval is a few ticks rather than every
 * tick.</p>
 */
public final class CastingPresentation {

    /** Ticks between two circle emissions; 3 ticks is smooth enough without flooding the client. */
    private static final int EMIT_INTERVAL_TICKS = 3;

    /** Boss circles are denser, so they emit a little less often than the plain dust ring. */
    private static final int BOSS_EMIT_INTERVAL_TICKS = 4;

    /** Ticks of one outward pulse cycle inside the vertical sigil. */
    private static final int SIGIL_PULSE_PERIOD = 8;

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
                emitBossRing(level, mob, color, ringRadius(mob, 0.0F) * 1.35D, 0.0F,
                        Math.max(4, SpellMobConfig.bossPoints()));
                if (SpellMobConfig.bossExtras()) {
                    emitVerticalSigil(level, mob, spell, color, 0.0D);
                    emitBlastwave(level, mob, color, 0.6F);
                }
            } else {
                emitRing(level, mob, color, ringRadius(mob, 0.0F), 0.0F);
                emitAura(level, mob);
            }
        } catch (Throwable exception) {
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
                    emitBossRing(level, mob, color, radius * 1.35D, ticks * 0.22F,
                            Math.max(3, SpellMobConfig.bossPoints() * 3 / 4));
                }
                if (bossTick && SpellMobConfig.bossExtras()) {
                    emitVerticalSigil(level, mob, spell, color, ticks);
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
        } catch (Throwable exception) {
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
            emitBossRing(level, mob, color, ringRadius(mob, 0.0F) * 1.9D, 0.0F,
                    Math.max(4, SpellMobConfig.bossPoints() * 5 / 4));
            emitSigilBurst(level, mob, spell, color, 1.0D);
        } catch (Throwable exception) {
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
                emitBossSmokeRing(level, mob, ringRadius(mob, 0.0F) * 1.5D, 0.0F, 12);
            }
            level.playSound(null, x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.7F, 0.7F);
        } catch (Throwable exception) {
            Logs.debug("Casting presentation failed on cancel: {}", exception.getMessage());
        }
    }

    private static void emitRing(ServerLevel level, Mob mob, Vector3f color, double radius, float phase) {
        int points = points();
        if (points <= 0) {
            return;
        }
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(color), 1.6F);
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
    private static void emitBossRing(ServerLevel level, Mob mob, Vector3f color, double radius, float phase, int points) {
        if (points <= 0) {
            return;
        }
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(color), 1.2F);
        double centerX = mob.getX();
        double centerY = mob.getY() + 0.05D;
        double centerZ = mob.getZ();
        for (int index = 0; index < points; index++) {
            double angle = phase + Math.PI * 2.0D * index / points;
            send(level, dust, centerX + Math.cos(angle) * radius, centerY, centerZ + Math.sin(angle) * radius);
        }
    }

    private static void emitBossSmokeRing(ServerLevel level, Mob mob, double radius, float phase, int points) {
        if (points <= 0) {
            return;
        }
        ParticleOptions smoke = ParticleTypes.SMOKE;
        double centerX = mob.getX();
        double centerY = mob.getY() + 0.05D;
        double centerZ = mob.getZ();
        for (int index = 0; index < points; index++) {
            double angle = phase + Math.PI * 2.0D * index / points;
            send(level, smoke, centerX + Math.cos(angle) * radius, centerY, centerZ + Math.sin(angle) * radius);
        }
    }

    /**
     * Draws one expanding dust ring at the caster's feet. This is the boss pulse: the same kind of
     * ring as the normal casting circle, only brighter and farther out.
     *
     * @param level  the server level
     * @param mob    the caster
     * @param color  the school colour
     * @param radius ring radius in blocks
     * @param points number of dust dots
     */
    /**
     * Draws the vertical sigil in front of a boss caster: one static school coloured ring plus two
     * outward pulses that keep expanding while the cast lasts.
     *
     * <p>The plane comes from the caster's look direction so the ring always faces its target, and
     * the center is the spell origin (eye height, slightly in front of the head) where projectiles
     * actually spawn, so the spell looks like it flies out of the ring.</p>
     *
     * @param mob     the caster
     * @param spell   the spell being cast, used to pick the school particle
     * @param colour  the resolved school colour
     * @param elapsed ticks since the cast started, drives the pulse animation
     */
    private static void emitVerticalSigil(ServerLevel level, Mob mob, AbstractSpell spell, Vector3f colour, double elapsed) {
        SigilFrame frame = sigilFrame(mob);
        if (frame == null) {
            return;
        }
        ParticleOptions ring = sigilRingParticle(spell, colour);
        ParticleOptions pulse = sigilPulseParticle(spell, colour);
        int points = 36;
        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2.0D * index / points;
            send(level, ring, frame.point(Math.cos(angle), Math.sin(angle), frame.radius()));
        }
        for (int wave = 0; wave < 2; wave++) {
            double phase = (((elapsed + wave * (SIGIL_PULSE_PERIOD / 2.0D)) % SIGIL_PULSE_PERIOD) + SIGIL_PULSE_PERIOD)
                    % SIGIL_PULSE_PERIOD / SIGIL_PULSE_PERIOD;
            double radius = frame.radius() * (0.45D + 0.50D * phase);
            int wavePoints = Math.max(8, (int) Math.round(points * (0.85D - 0.45D * phase)));
            for (int index = 0; index < wavePoints; index++) {
                double angle = Math.PI * 2.0D * index / wavePoints;
                send(level, pulse, frame.point(Math.cos(angle), Math.sin(angle), radius));
            }
        }
    }

    /** Expands the sigil outward once when a boss cast finishes. */
    private static void emitSigilBurst(ServerLevel level, Mob mob, AbstractSpell spell, Vector3f colour, double scale) {
        SigilFrame frame = sigilFrame(mob);
        if (frame == null) {
            return;
        }
        ParticleOptions ring = sigilRingParticle(spell, colour);
        ParticleOptions pulse = sigilPulseParticle(spell, colour);
        int points = 40;
        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2.0D * index / points;
            send(level, ring, frame.point(Math.cos(angle), Math.sin(angle), frame.radius() * scale));
        }
        int wavePoints = 24;
        for (int index = 0; index < wavePoints; index++) {
            double angle = Math.PI * 2.0D * index / wavePoints;
            send(level, pulse, frame.point(Math.cos(angle), Math.sin(angle), frame.radius() * scale * 0.90D));
        }
    }

    /**
     * Builds the vertical plane of the sigil from the caster's look direction.
     *
     * @return the frame, or null when the look vector is degenerate
     */
    private static @Nullable SigilFrame sigilFrame(Mob mob) {
        Vec3 forward = mob.getLookAngle();
        if (forward.lengthSqr() < 1.0E-6D) {
            return null;
        }
        forward = forward.normalize();
        Vec3 right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-6D) {
            right = new Vec3(1.0D, 0.0D, 0.0D).cross(forward);
        }
        if (right.lengthSqr() < 1.0E-6D) {
            return null;
        }
        right = right.normalize();
        Vec3 up = right.cross(forward).normalize();
        double radius = 0.75D;
        Vec3 center = mob.getEyePosition().add(forward.scale(0.45D + radius * 0.15D));
        return new SigilFrame(center, right, up, radius);
    }

    /** Ring particle for the school, or a school coloured dust fallback for unknown schools. */
    private static ParticleOptions sigilRingParticle(AbstractSpell spell, Vector3f colour) {
        String path = schoolPath(spell);
        return switch (path) {
            case "fire" -> ParticleTypes.FLAME;
            case "ice" -> ParticleTypes.SNOWFLAKE;
            case "lightning" -> ParticleTypes.ELECTRIC_SPARK;
            case "holy" -> ParticleTypes.END_ROD;
            case "ender" -> ParticleTypes.PORTAL;
            case "evocation" -> ParticleTypes.WITCH;
            case "nature" -> ParticleTypes.HAPPY_VILLAGER;
            case "eldritch" -> ParticleTypes.SCULK_SOUL;
            default -> new DustParticleOptions(new Vector3f(colour), 1.4F);
        };
    }

    /** Pulse particle for the school, or a school coloured dust fallback for unknown schools. */
    private static ParticleOptions sigilPulseParticle(AbstractSpell spell, Vector3f colour) {
        String path = schoolPath(spell);
        return switch (path) {
            case "fire" -> ParticleTypes.LAVA;
            case "ice" -> ParticleTypes.CLOUD;
            case "lightning" -> ParticleTypes.END_ROD;
            case "holy" -> ParticleTypes.TOTEM_OF_UNDYING;
            case "ender" -> ParticleTypes.REVERSE_PORTAL;
            case "evocation" -> ParticleTypes.ENCHANT;
            case "nature" -> ParticleTypes.COMPOSTER;
            case "eldritch" -> ParticleTypes.WARPED_SPORE;
            default -> new DustParticleOptions(new Vector3f(colour), 2.0F);
        };
    }

    private static String schoolPath(AbstractSpell spell) {
        SchoolType school = schoolOf(spell);
        ResourceLocation id = school == null ? null : school.getId();
        return id == null ? "" : id.getPath();
    }

    /** Precomputed plane of the vertical sigil: center plus two orthonormal axes. */
    private record SigilFrame(Vec3 center, Vec3 right, Vec3 up, double radius) {

        Vec3 point(double cos, double sin, double radius) {
            return center.add(right.scale(cos * radius)).add(up.scale(sin * radius));
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

    private static void send(ServerLevel level, ParticleOptions particle, Vec3 position) {
        send(level, particle, position.x, position.y, position.z);
    }

    private static double ringRadius(Mob mob, float pulse) {
        double base = 0.5D + mob.getBbWidth() * 0.85D;
        return base * SpellMobConfig.ringScale() * (0.8D + 0.45D * pulse);
    }

    private static int points() {
        return Math.max(0, Math.min(128, SpellMobConfig.presentationPoints()));
    }

    private static double limitSqr() {
        double limit = SpellMobConfig.presentationDistance();
        return limit * limit;
    }

    /**
     * School colours as ISS registers them, used when the school itself can be resolved but its
     * targeting colour cannot be read. The values mirror {@code SchoolRegistry} so a fallback still
     * looks like the school instead of turning black.
     */
    private static final Map<String, Vector3f> KNOWN_SCHOOL_COLOURS = Map.ofEntries(
            Map.entry("irons_spellbooks:fire", rgb(0xFFAA00)),
            Map.entry("irons_spellbooks:ice", rgb(13695487)),
            Map.entry("irons_spellbooks:lightning", rgb(0x55FFFF)),
            Map.entry("irons_spellbooks:holy", rgb(16775380)),
            Map.entry("irons_spellbooks:ender", rgb(0xFF55FF)),
            Map.entry("irons_spellbooks:blood", rgb(0xAA0000)),
            Map.entry("irons_spellbooks:evocation", rgb(0xFFFFFF)),
            Map.entry("irons_spellbooks:nature", rgb(0x55FF55)),
            Map.entry("irons_spellbooks:eldritch", rgb(1016732)));

    private static Vector3f rgb(int value) {
        return new Vector3f(((value >> 16) & 0xFF) / 255.0F, ((value >> 8) & 0xFF) / 255.0F, (value & 0xFF) / 255.0F);
    }

    private static Vector3f colorOf(AbstractSpell spell) {
        SchoolType school = schoolOf(spell);
        Vector3f colour = targetingColourOf(school);
        if (isUsable(colour)) {
            return new Vector3f(colour);
        }
        if (school != null) {
            Vector3f known = KNOWN_SCHOOL_COLOURS.get(school.getId().toString());
            if (known != null) {
                return new Vector3f(known);
            }
        }
        return new Vector3f(DEFAULT_RED, DEFAULT_GREEN, DEFAULT_BLUE);
    }

    /**
     * Resolves the school of a spell.
     *
     * <p>ISS 3.16 moved the school into the per spell configuration. When that configuration is
     * absent - which is the case on the 1.20.1 build that ships without spell config files -
     * {@code getSchoolType()} yields null. The spell's own default config still carries the school
     * resource, so it is consulted before giving up.</p>
     */
    @Nullable
    private static SchoolType schoolOf(AbstractSpell spell) {
        try {
            SchoolType school = spell.getSchoolType();
            if (school != null) {
                return school;
            }
        } catch (Throwable exception) {
            Logs.debug("Could not read the school of {}: {}", spell.getSpellId(), exception.getMessage());
        }
        try {
            ResourceLocation id = spell.getDefaultConfig().schoolResource;
            if (id != null) {
                return SchoolRegistry.getSchool(id);
            }
        } catch (Throwable exception) {
            Logs.debug("Could not read the default school of {}: {}", spell.getSpellId(), exception.getMessage());
        }
        return null;
    }

    @Nullable
    private static Vector3f targetingColourOf(@Nullable SchoolType school) {
        if (school == null) {
            return null;
        }
        try {
            return school.getTargetingColor();
        } catch (Throwable exception) {
            Logs.debug("Could not read the targeting colour of {}: {}", school.getId(), exception.getMessage());
            return null;
        }
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
