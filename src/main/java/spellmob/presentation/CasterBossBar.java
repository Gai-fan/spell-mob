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
import org.jetbrains.annotations.Nullable;
import spellmob.capability.ISpellCaster;
import spellmob.capability.SpellCasterAttachments;
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
    private int variantIndex = -1;
    private BossEvent.BossBarColor colour;
    private String nameText = "";
    /** Colour forced by the running phase show, {@code null} to follow the config. */
    private BossEvent.BossBarColor phaseColour;
    /** Name forced by the running phase show, {@code null} to use the mob name. */
    private String phaseName;

    public CasterBossBar(Mob mob) {
        this.mob = mob;
    }

    /**
     * Overrides the colour and name while a boss phase show is running.
     *
     * @param phaseColour the colour to force, {@code null} keeps the configured one
     * @param phaseName   the name to force, {@code null} keeps the mob name
     */
    public void setPhaseAppearance(@Nullable BossEvent.BossBarColor phaseColour, @Nullable String phaseName) {
        this.phaseColour = phaseColour;
        this.phaseName = phaseName == null || phaseName.isBlank() ? null : phaseName;
    }

    /** Drops the phase overrides, for example when a boss is reset after the fight. */
    public void clearPhaseAppearance() {
        this.phaseColour = null;
        this.phaseName = null;
    }

    private BossEvent.BossBarColor effectiveColour() {
        return this.phaseColour != null ? this.phaseColour : SpellMobConfig.bossBarColor();
    }

    /** Refreshes the bar, or hides it when the mob no longer qualifies. */
    public void tick(int variantIndex) {
        this.variantIndex = variantIndex;
        if (!(mob.level() instanceof ServerLevel level) || !shouldShow()) {
            hide();
            return;
        }
        if (bar == null) {
            Component initialName = displayName();
            ServerBossEvent created = new ServerBossEvent(initialName, effectiveColour(),
                    BossEvent.BossBarOverlay.PROGRESS);
            created.setProgress(progress());
            bar = created;
            colour = effectiveColour();
            nameText = initialName.getString();
            refreshPlayers(level);
        }
        ticks++;
        if (ticks % UPDATE_INTERVAL == 0) {
            BossEvent.BossBarColor configured = effectiveColour();
            if (configured != colour) {
                colour = configured;
                bar.setColor(configured);
            }
            Component currentName = displayName();
            if (!currentName.getString().equals(nameText)) {
                nameText = currentName.getString();
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

    /**
     * @return the name shown on the bar, optionally followed by the spell being cast
     */
    private Component displayName() {
        if (this.phaseName != null) {
            return Component.literal(this.phaseName.replace("%s", mob.getDisplayName().getString()));
        }
        Component base = mob.getDisplayName();
        if (!SpellMobConfig.bossBarCastName()) {
            return base;
        }
        AbstractSpell spell = null;
        ISpellCaster caster = SpellCasterAttachments.peek(mob);
        if (caster != null) {
            spell = caster.castingSpell();
        }
        if (spell == null) {
            return base;
        }
        return Component.empty()
                .append(base)
                .append(" ")
                .append(Component.translatable("spellmob.boss_bar.casting", spell.getDisplayName(null)));
    }

    private boolean shouldShow() {
        if (!SpellMobConfig.enabled() || !mob.isAlive() || mob.isRemoved()) {
            return false;
        }
        // A data pack may force the bar on or off per variant, which wins over the tag based
        // config switch. That is what makes "with bar" and "without bar" test setups possible.
        Boolean override = SpellCasterRegistry.bossBarOverride(mob, variantIndex);
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
