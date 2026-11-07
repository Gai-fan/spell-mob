package spellmob.presentation;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Mob;
import spellmob.capability.SpellCaster;
import spellmob.capability.SpellCasterCapability;
import spellmob.config.SpellMobConfig;
import spellmob.data.SpellCasterRegistry;

/**
 * The optional boss bar of one casting mob.
 *
 * <p>Bars are built lazily: a caster without the {@code bossBar} config keeps no bar object at all.
 * While the bar is visible the name and progress are refreshed every {@value #UPDATE_INTERVAL} ticks
 * and the recipient list every {@value #PLAYER_INTERVAL} ticks, which is far cheaper than touching
 * the network every tick and still looks continuous.</p>
 */
public final class CasterBossBar {

    private static final int UPDATE_INTERVAL = 5;
    private static final int PLAYER_INTERVAL = 20;

    private final Mob mob;
    private ServerBossEvent bar;
    private int ticks;
    private BossEvent.BossBarColor colour;
    private String nameText = "";

    public CasterBossBar(Mob mob) {
        this.mob = mob;
    }

    /** Refreshes the bar, or hides it when the mob no longer qualifies. */
    public void tick() {
        if (!(mob.level() instanceof ServerLevel level) || !shouldShow()) {
            hide();
            return;
        }
        if (bar == null) {
            Component created = displayName();
            ServerBossEvent event = new ServerBossEvent(created, SpellMobConfig.bossBarColor(),
                    BossEvent.BossBarOverlay.PROGRESS);
            event.setProgress(progress());
            bar = event;
            colour = SpellMobConfig.bossBarColor();
            nameText = created.getString();
            refreshPlayers(level);
        }
        ticks++;
        if (ticks % UPDATE_INTERVAL == 0) {
            BossEvent.BossBarColor configured = SpellMobConfig.bossBarColor();
            if (configured != colour) {
                colour = configured;
                bar.setColor(configured);
            }
            Component currentName = displayName();
            String currentText = currentName.getString();
            if (!currentText.equals(nameText)) {
                nameText = currentText;
                bar.setName(currentName);
            }
            bar.setProgress(progress());
        }
        if (ticks % PLAYER_INTERVAL == 0) {
            refreshPlayers(level);
        }
    }

    /** Removes the bar from every player, for example when the mob unloads. */
    public void remove() {
        hide();
    }

    private boolean shouldShow() {
        if (!SpellMobConfig.enabled() || !mob.isAlive() || mob.isRemoved()) {
            return false;
        }
        // A data pack may force the bar on or off per variant, which wins over the tag based
        // config switch. That is what makes "with bar" and "without bar" test setups possible.
        Boolean override = SpellCasterRegistry.bossBarOverride(mob);
        if (override != null) {
            return override;
        }
        return SpellMobConfig.bossBarEnabled() && SpellMobConfig.isBossBarCaster(mob);
    }

    private void hide() {
        if (bar != null) {
            bar.setVisible(false);
            bar.removeAllPlayers();
            bar = null;
            ticks = 0;
        }
    }

    /**
     * Builds the bar title: the plain mob name, or "name casting &lt;spell&gt;" while the caster is
     * busy. The suffix can be switched off with {@code presentation.bossBarCastName}.
     */
    private Component displayName() {
        Component base = mob.getDisplayName();
        if (!SpellMobConfig.bossBarCastName()) {
            return base;
        }
        AbstractSpell spell = mob.getCapability(SpellCasterCapability.capability())
                .resolve()
                .map(spellmob.capability.ISpellCaster::castingSpell)
                .orElse(null);
        if (spell == null) {
            return base;
        }
        return Component.empty()
                .append(base)
                .append(Component.literal(" "))
                .append(Component.translatable("spellmob.boss_bar.casting", spell.getDisplayName(null)));
    }

    private float progress() {
        float max = mob.getMaxHealth();
        if (max <= 0.0F) {
            return 0.0F;
        }
        float fraction = mob.getHealth() / max;
        return Math.max(0.0F, Math.min(1.0F, fraction));
    }

    private void refreshPlayers(ServerLevel level) {
        double range = SpellMobConfig.bossBarRange();
        double rangeSq = range * range;
        Set<UUID> nearby = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            if (player.distanceToSqr(mob) <= rangeSq) {
                nearby.add(player.getUUID());
            }
        }
        for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
            if (!nearby.contains(player.getUUID())) {
                bar.removePlayer(player);
            }
        }
        for (ServerPlayer player : level.players()) {
            if (nearby.contains(player.getUUID())) {
                bar.addPlayer(player);
            }
        }
        bar.setVisible(!bar.getPlayers().isEmpty());
    }
}
