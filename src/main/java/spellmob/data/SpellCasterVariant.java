package spellmob.data;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * One individual rule inside a {@code spell_casters} file.
 *
 * <p>Variants are checked in file order and the first matching one wins.</p>
 *
 * @param selector  which mobs this variant applies to
 * @param override  {@code true} replaces the species default, {@code false} appends to it
 * @param sticky    {@code true} keeps this variant after it matched once, even when the mob heals
 *                  back above the health threshold; meant for boss phases
 * @param bossBar   forces the boss bar on ({@code true}) or off ({@code false}) for this variant;
 *                  {@code null} falls back to the config default
 * @param disableAttackGoals {@code true} removes the vanilla melee and ranged attack goals while
 *                  this variant applies; {@code null} falls back to the species default
 * @param learner   {@code true} lets the mob learn spells from thrown scrolls; equivalent to giving
 *                  it the {@code scrollLearning.learnerTag} tag, but stored in the data pack
 * @param adaptive  {@code true} lets a boss learn the spells of players that lose to it; equivalent
 *                  to giving it the {@code adaptive.adaptiveTag} tag, but stored in the data pack
 * @param phase     the one shot show played when this variant is entered, {@code null} for none
 * @param data      the spells of this variant, may be {@link SpellCasterData#EMPTY} to silence the
 *                  mob when {@code override} is true
 * @param dropRules loot rules that only apply while this variant matches; every rule whose
 *                  selector matches the dying mob is rolled, in file order
 */
public record SpellCasterVariant(VariantSelector selector, boolean override, boolean sticky,
                                 @Nullable Boolean bossBar, boolean learner, boolean adaptive,
                                 @Nullable Boolean disableAttackGoals, @Nullable PhaseEffect phase,
                                 SpellCasterData data, List<DropRule> dropRules) {

    public SpellCasterVariant {
        dropRules = List.copyOf(dropRules);
    }
}
