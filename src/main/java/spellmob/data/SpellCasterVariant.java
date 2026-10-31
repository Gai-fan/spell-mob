package spellmob.data;

/**
 * One individual rule inside a {@code spell_casters} file.
 *
 * <p>Variants are checked in file order and the first matching one wins.</p>
 *
 * @param selector which mobs this variant applies to
 * @param override {@code true} replaces the species default, {@code false} appends to it
 * @param data     the spells of this variant, may be {@link SpellCasterData#EMPTY} to silence the
 *                 mob when {@code override} is true
 */
public record SpellCasterVariant(VariantSelector selector, boolean override, SpellCasterData data) {
}
