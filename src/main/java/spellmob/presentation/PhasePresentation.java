package spellmob.presentation;

import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import spellmob.config.SpellMobConfig;
import spellmob.data.PhaseEffect;
import spellmob.util.Logs;

/**
 * Plays the visible part of a phase change: particles, camera shake, sound, titles, weather and
 * reinforcements.
 *
 * <p>Everything here is server side. Camera shake is delegated to Iron's Spellbooks, which sends the
 * shake to its own clients; every other effect uses vanilla packets and particles.</p>
 */
public final class PhasePresentation {

    /** Players within this distance see the title and subtitle. */
    private static final double TITLE_RADIUS = 32.0D;
    /** Ticks the ground burst takes to expand to its full radius. */
    public static final int BURST_TICKS = 10;

    private PhasePresentation() {
    }

    /**
     * Plays the instant part of the show.
     *
     * @param level  the level of the caster
     * @param mob    the caster
     * @param effect the show
     * @param colour the colour used by the particle burst
     */
    public static void start(ServerLevel level, Mob mob, PhaseEffect effect, Vector3f colour) {
        if (effect.shake() != null) {
            CameraShake.shake(mob, effect.shake().radius(), effect.shake().duration());
        }
        if (effect.sound() != null) {
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(effect.sound());
            if (sound != null) {
                level.playSound(null, mob.blockPosition(), sound, SoundSource.HOSTILE, 2.0F, 1.0F);
            }
        }
        if (effect.title() != null || effect.subtitle() != null) {
            sendTitles(level, mob, effect.title(), effect.subtitle());
        }
        if (SpellMobConfig.allowPhaseWeather()) {
            if (effect.weather() != null) {
                level.setWeatherParameters(effect.weather().clearTime(), effect.weather().weatherTime(),
                        effect.weather().raining(), effect.weather().thundering());
            }
            if (effect.time() != null) {
                level.setDayTime(effect.time().dayTime());
            }
        }
        burst(level, mob, colour, effect.burst(), 0.0D);
    }

    /**
     * Draws one frame of the expanding ground burst.
     *
     * @param level   the level
     * @param mob     the caster
     * @param colour  the colour of the burst
     * @param burst   the configured burst, may be {@code null}
     * @param progress {@code 0} to {@code 1}
     */
    public static void burst(ServerLevel level, Mob mob, Vector3f colour, @Nullable PhaseEffect.Burst burst,
                             double progress) {
        if (burst == null) {
            return;
        }
        double radius = burst.radius() * (0.25D + 0.75D * progress);
        int segments = Math.max(12, (int) Math.min(96.0D, radius * 24.0D));
        DustParticleOptions dust = new DustParticleOptions(colour(burst, colour), 1.6F);
        Vec3 base = mob.position();
        for (int i = 0; i < segments; i++) {
            double angle = (Math.PI * 2.0D * i) / segments;
            double x = base.x + Math.cos(angle) * radius;
            double z = base.z + Math.sin(angle) * radius;
            CastingPresentation.send(level, new Vec3(x, base.y + 0.1D, z), dust);
            if (i % 4 == 0) {
                CastingPresentation.send(level, new Vec3(x, base.y + 0.4D, z), ParticleTypes.ENCHANT);
            }
        }
    }

    /**
     * Spawns the reinforcements of a phase.
     *
     * @param level  the level
     * @param mob    the caster
     * @param summon the summon block
     */
    public static void summon(ServerLevel level, Mob mob, PhaseEffect.Summon summon) {
        // DefaultedRegistry#get hands back the default value (a pig) for unknown ids, so the
        // optional variant is the only way to tell "unknown entity" from "pig".
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(summon.entity()).orElse(null);
        if (type == null) {
            Logs.warn("onPhase summon: unknown entity {}", summon.entity());
            return;
        }
        for (int i = 0; i < summon.count(); i++) {
            double angle = (Math.PI * 2.0D * i) / summon.count();
            double x = mob.getX() + Math.cos(angle) * summon.radius();
            double z = mob.getZ() + Math.sin(angle) * summon.radius();
            Entity entity = type.create(level);
            if (entity == null) {
                continue;
            }
            entity.moveTo(x, mob.getY(), z, mob.getYRot(), 0.0F);
            for (String tag : summon.tags()) {
                entity.addTag(tag);
            }
            level.addFreshEntity(entity);
        }
    }

    /**
     * Pushes players away from the caster.
     *
     * @param level    the level
     * @param mob      the caster
     * @param strength distance in blocks the players are pushed
     */
    public static void knockback(ServerLevel level, Mob mob, double strength) {
        if (strength <= 0.0D) {
            return;
        }
        double range = 5.0D;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.distanceToSqr(mob) > range * range) {
                continue;
            }
            Vec3 away = player.position().subtract(mob.position());
            Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);
            if (horizontal.lengthSqr() < 1.0E-4D) {
                horizontal = new Vec3(1.0D, 0.0D, 0.0D);
            }
            horizontal = horizontal.normalize().scale(strength * 0.55D);
            player.push(horizontal.x, 0.35D, horizontal.z);
            player.hurtMarked = true;
        }
    }

    private static void sendTitles(ServerLevel level, Mob mob, @Nullable String title,
                                   @Nullable String subtitle) {
        String name = mob.getName().getString();
        Component titleText = title == null ? Component.empty()
                : Component.literal(title.replace("%s", name));
        Component subtitleText = subtitle == null ? Component.empty()
                : Component.literal(subtitle.replace("%s", name));
        double rangeSq = TITLE_RADIUS * TITLE_RADIUS;
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (player.distanceToSqr(mob) > rangeSq) {
                continue;
            }
            player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
            player.connection.send(new ClientboundSetTitleTextPacket(titleText));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitleText));
        }
    }

    /**
     * Resolves the colour of the particle burst.
     *
     * @param burst    the burst settings, may be {@code null}
     * @param fallback the colour used when the data pack did not pick one
     * @return the resolved colour
     */
    public static Vector3f colour(PhaseEffect.Burst burst, Vector3f fallback) {
        if (burst == null || burst.autoColour() || burst.colour() == null) {
            return fallback;
        }
        String value = burst.colour().trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        try {
            int rgb = Integer.parseInt(value, 16);
            return new Vector3f(((rgb >> 16) & 0xFF) / 255.0F,
                    ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    /**
     * @param entity the entity a title belongs to
     * @return the display name used in title placeholders
     */
    public static String nameOf(LivingEntity entity) {
        return entity.getName().getString();
    }
}
