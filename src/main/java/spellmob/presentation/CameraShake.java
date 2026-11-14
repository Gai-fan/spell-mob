package spellmob.presentation;

import io.redspace.ironsspellbooks.api.util.CameraShakeData;
import io.redspace.ironsspellbooks.api.util.CameraShakeManager;
import net.minecraft.world.entity.Mob;

/**
 * Thin wrapper around the Iron's Spellbooks camera shake API.
 *
 * <p>The API lives in {@code api.util} and has not changed between the 1.20.1 and 1.21.1 builds of
 * Iron's Spellbooks that this mod targets, but it is only semi public. Everything is wrapped in a
 * {@code Throwable} catch so a future rename degrades to "no shake" instead of breaking a phase.</p>
 */
public final class CameraShake {

    private CameraShake() {
    }

    /**
     * Shakes the camera of every player around a mob.
     *
     * @param mob      the caster the shake is anchored to
     * @param radius   players within this distance are shaken
     * @param duration ticks the shake lasts
     */
    public static void shake(Mob mob, float radius, int duration) {
        if (duration <= 0 || radius <= 0.0F) {
            return;
        }
        try {
            CameraShakeManager.addCameraShake(new CameraShakeData(duration, mob.position(), radius));
        } catch (Throwable throwable) {
            // Iron's Spellbooks changed its shake API: the phase still runs, it is just silent.
        }
    }
}
