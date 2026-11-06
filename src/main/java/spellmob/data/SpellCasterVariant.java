package spellmob.data;

import java.util.List;

/**
 * One individual rule inside a {@code spell_casters} file.
 *
 * <p>Variants are checked in file order and the first matching one wins.</p>
 *
 * @param selector  which mobs this variant applies to
 * @param override  {@code true} replaces the species default, {@code false} appends to it
 * @param sticky    {@code true} keeps this variant after it matched once, even when the mob heals
 *                  back above the health threshold; meant for boss phases
 * @param data      the spells of this variant, may be {@link SpellCasterData#EMPTY} to silence the
 *                  mob when {@code override} is true
 * @param dropRules loot rules that only apply while this variant matches; every rule whose
 *                  selector matches the dying mob is rolled, in file order
 */
public record SpellCasterVariant(VariantSelector selector, boolean override, boolean sticky,
                                 SpellCasterData data, List<DropRule> dropRules) {

    public SpellCasterVariant {
        dropRules = List.copyOf(dropRules);
    }
}
