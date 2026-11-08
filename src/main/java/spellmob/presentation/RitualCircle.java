package spellmob.presentation;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Scroll learning circle drawn on the ground under a pet.
 *
 * <p>The look mirrors the teleportation crystal charge effect: a dust ring, a still hexagram made
 * of two triangles, and enchant runes that rise out of the circle while the ritual runs.</p>
 */
public final class RitualCircle {

    private static final int RING_SEGMENTS = 32;
    private static final int STAR_POINTS = 6;
    private static final int POINTS_PER_EDGE = 6;
    private static final double RING_RADIUS = 1.25D;
    private static final double STAR_RADIUS = 0.95D;

    private RitualCircle() {
    }

    /**
     * Draws one frame of the circle.
     *
     * @param level    server level the pet lives in
     * @param pet      the pet the circle belongs to
     * @param colour   school colour, black when the ritual is a rejection
     * @param progress 0 at the start, 1 when the ritual completes
     * @param rejected true for the short black "cannot learn" circle
     * @param upgraded true when the scroll upgrades a spell the pet already knows, which adds a
     *                 rising spiral on top of the regular circle
     */
    public static void tick(ServerLevel level, LivingEntity pet, Vector3f colour, float progress,
                            boolean rejected, boolean upgraded) {
        Vec3 centre = pet.position().add(0.0D, 0.05D, 0.0D);
        float pulse = 0.85F + 0.15F * Mth.sin(progress * (float) Math.PI * 6.0F);
        float scale = 0.9F + progress * 0.3F;
        DustParticleOptions dust = new DustParticleOptions(colour, scale * pulse);

        for (int i = 0; i < RING_SEGMENTS; i++) {
            double angle = Math.PI * 2.0D * i / RING_SEGMENTS;
            CastingPresentation.send(level, new Vec3(
                    centre.x + Math.cos(angle) * RING_RADIUS,
                    centre.y,
                    centre.z + Math.sin(angle) * RING_RADIUS), dust);
        }

        drawStar(level, centre, dust);
        if (!rejected && level.getGameTime() % 6L == 0L) {
            drawRune(level, centre, colour);
        }
        if (!rejected && upgraded) {
            drawSpiral(level, centre, dust, progress);
        }
    }

    /**
     * A helix of particles climbing out of the circle; only drawn when the scroll upgrades a spell
     * the pet already knows, so the player gets a visual confirmation that this is not a new spell.
     */
    private static void drawSpiral(ServerLevel level, Vec3 centre, DustParticleOptions dust,
                                   float progress) {
        int points = 12;
        double spin = progress * Math.PI * 6.0D;
        for (int i = 0; i < points; i++) {
            double t = i / (double) (points - 1);
            double angle = spin + t * Math.PI * 3.0D;
            double radius = 0.30D + 0.85D * t;
            double y = 0.10D + 1.45D * t;
            double x = centre.x + Math.cos(angle) * radius;
            double z = centre.z + Math.sin(angle) * radius;
            Vec3 point = new Vec3(x, centre.y + y, z);
            CastingPresentation.send(level, point, dust);
            if (i % 3 == 0) {
                CastingPresentation.send(level, point, ParticleTypes.ENCHANT,
                        0.0D, 0.05D, 0.0D, 0.02D);
            }
        }
    }

    private static void drawStar(ServerLevel level, Vec3 centre, DustParticleOptions dust) {
        Vector3f[] points = new Vector3f[STAR_POINTS];
        for (int i = 0; i < STAR_POINTS; i++) {
            double angle = Math.PI * i / 3.0D;
            points[i] = new Vector3f((float) (Math.sin(angle) * STAR_RADIUS), 0.0F,
                    (float) (Math.cos(angle) * STAR_RADIUS));
        }
        for (int i = 0; i <= POINTS_PER_EDGE; i++) {
            float t = (float) i / POINTS_PER_EDGE;
            line(level, centre, points[0], points[2], t, dust);
            line(level, centre, points[2], points[4], t, dust);
            line(level, centre, points[4], points[0], t, dust);
            line(level, centre, points[1], points[3], t, dust);
            line(level, centre, points[3], points[5], t, dust);
            line(level, centre, points[5], points[1], t, dust);
        }
    }

    private static void line(ServerLevel level, Vec3 centre, Vector3f from, Vector3f to, float t,
                             DustParticleOptions dust) {
        float x = Mth.lerp(t, from.x, to.x);
        float z = Mth.lerp(t, from.z, to.z);
        CastingPresentation.send(level, new Vec3(centre.x + x, centre.y, centre.z + z), dust);
    }

    /** Enchant runes drift up from random spots inside the circle, like the crystal effect. */
    private static void drawRune(ServerLevel level, Vec3 centre, Vector3f colour) {
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double radius = Math.sqrt(level.random.nextDouble()) * RING_RADIUS;
        Vec3 rune = new Vec3(centre.x + Math.cos(angle) * radius, centre.y + 0.2D,
                centre.z + Math.sin(angle) * radius);
        CastingPresentation.send(level, rune, ParticleTypes.ENCHANT, 0.0D, 1.0D, 0.0D, 0.04D);
        // A single coloured spark ties the runes back to the school colour.
        CastingPresentation.send(level,
                new Vec3(rune.x, centre.y + 0.35D, rune.z), new DustParticleOptions(colour, 0.7F));
    }
}
