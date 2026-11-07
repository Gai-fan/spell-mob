package spellmob.events;

import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Monster;
import spellmob.config.SpellMobConfig;

/**
 * Small amount of pet AI glue: pets defend the player who owns them and, when asked to, look for
 * their own fight.
 *
 * <p>Vanilla already makes a wolf fight back once it is hit; this only covers the case where the
 * owner is attacked and the pet is standing right next to the fight.</p>
 */
public final class PetBehavior {

    private static final int AGGRESSIVE_INTERVAL = 20;

    private PetBehavior() {
    }

    public static void onOwnerHurt(ServerPlayer owner, LivingEntity attacker) {
        if (!SpellMobConfig.defendOwner() || attacker == null || attacker == owner
                || !attacker.isAlive()) {
            return;
        }
        double range = SpellMobConfig.petEngageRange();
        List<TamableAnimal> pets = owner.level().getEntitiesOfClass(TamableAnimal.class,
                owner.getBoundingBox().inflate(range),
                pet -> pet.isAlive() && pet.isTame() && pet.isOwnedBy(owner)
                        && !pet.isOrderedToSit());
        for (TamableAnimal pet : pets) {
            pet.setTarget(attacker);
            pet.setLastHurtByMob(attacker);
        }
    }

    public static void tickAggressive(MinecraftServer server, long gameTime) {
        if (!SpellMobConfig.petsAggressive() || gameTime % AGGRESSIVE_INTERVAL != 0L) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                double range = SpellMobConfig.petEngageRange();
                List<TamableAnimal> pets = level.getEntitiesOfClass(TamableAnimal.class,
                        player.getBoundingBox().inflate(range),
                        pet -> pet.isAlive() && pet.isTame() && pet.isOwnedBy(player)
                                && !pet.isOrderedToSit());
                for (TamableAnimal pet : pets) {
                    if (pet.getTarget() != null && pet.getTarget().isAlive()) {
                        continue;
                    }
                    Monster nearest = level.getEntitiesOfClass(Monster.class,
                                    pet.getBoundingBox().inflate(SpellMobConfig.petCastRange()),
                                    monster -> monster.isAlive())
                            .stream().findFirst().orElse(null);
                    if (nearest != null) {
                        pet.setTarget(nearest);
                    }
                }
            }
        }
    }

    /** True when the mob is a tamed pet whose owner should be considered for mana sharing. */
    public static boolean isOwnedPet(Mob mob) {
        return mob instanceof TamableAnimal pet && pet.isTame();
    }
}
