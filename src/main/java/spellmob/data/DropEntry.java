package spellmob.data;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;
import spellmob.util.JsonHelper;
import spellmob.util.Logs;

/**
 * One line of a {@code drops} list.
 *
 * <p>An entry is either a plain item ({@code item} plus {@code count} or {@code min}/{@code max})
 * or a reference to a vanilla loot table ({@code lootTable}), never both. The loot table form is
 * meant for pools that are painful to describe by hand (enchantments, random equipment, nested
 * tables); the item form covers the common "drop two emeralds" case.</p>
 *
 * @param item             the item id, or {@code null} for a loot table entry
 * @param lootTable        the loot table id, or {@code null} for an item entry
 * @param minCount         minimum stack size
 * @param maxCount         maximum stack size (inclusive)
 * @param chance           probability of this entry, 0..1
 * @param looting          whether the killer's looting level adds extra items
 * @param requirePlayerKill whether a player has to be the killer (guards against farms)
 */
public record DropEntry(@Nullable ResourceLocation item, @Nullable ResourceLocation lootTable,
                        int minCount, int maxCount, double chance, boolean looting,
                        boolean requirePlayerKill) {

    /** Ids that already produced a warning, so a broken file does not spam the log per death. */
    private static final Set<ResourceLocation> WARNED_IDS = new HashSet<>();

    public DropEntry {
        if (item == null && lootTable == null) {
            throw new IllegalArgumentException("a drop needs either 'item' or 'lootTable'");
        }
        if (item != null && lootTable != null) {
            throw new IllegalArgumentException("a drop cannot have both 'item' and 'lootTable'");
        }
        if (minCount < 1 || maxCount < minCount) {
            throw new IllegalArgumentException("drop counts must satisfy 1 <= min <= max");
        }
        if (!Double.isFinite(chance)) {
            chance = 1.0D;
        }
        chance = Math.max(0.0D, Math.min(1.0D, chance));
    }

    /**
     * Parses one {@code drops} array element.
     *
     * @param json  the element
     * @param where file path for error messages
     * @return the entry
     * @throws IllegalArgumentException when required fields are missing or malformed
     */
    public static DropEntry parse(JsonObject json, String where) {
        ResourceLocation item = null;
        String itemId = JsonHelper.optString(json, "item", null);
        if (itemId != null) {
            item = ResourceLocation.tryParse(itemId);
            if (item == null) {
                throw new IllegalArgumentException(where + ": invalid 'item' id '" + itemId + "'");
            }
        }

        ResourceLocation table = null;
        String tableId = JsonHelper.optString(json, "lootTable", null);
        if (tableId != null) {
            table = ResourceLocation.tryParse(tableId);
            if (table == null) {
                throw new IllegalArgumentException(where + ": invalid 'lootTable' id '" + tableId + "'");
            }
        }

        int min;
        int max;
        if (json.has("count")) {
            min = JsonHelper.optInt(json, "count", 1);
            max = min;
        } else {
            min = JsonHelper.optInt(json, "min", 1);
            max = JsonHelper.optInt(json, "max", min);
        }

        double chance = JsonHelper.optDouble(json, "chance", 1.0D);
        boolean looting = JsonHelper.optBoolean(json, "looting", false);
        boolean requirePlayerKill = JsonHelper.optBoolean(json, "requirePlayerKill", true);
        return new DropEntry(item, table, min, max, chance, looting, requirePlayerKill);
    }

    /**
     * Rolls this entry.
     *
     * @param random the mob's random source
     * @return true when the entry should drop
     */
    public boolean rolls(RandomSource random) {
        return chance >= 1.0D || random.nextDouble() < chance;
    }

    /**
     * Builds the stacks this entry contributes.
     *
     * @param mob          the dying mob
     * @param killer       the player that killed it, or {@code null}
     * @param lootingLevel the looting level of the kill
     * @return the stacks, possibly empty
     */
    public List<ItemStack> createStacks(Mob mob, @Nullable Player killer, int lootingLevel) {
        if (lootTable != null) {
            return createLootTableStacks(mob, killer, lootingLevel);
        }
        if (item == null) {
            return List.of();
        }
        Item resolved = BuiltInRegistries.ITEM.get(item);
        if (resolved == null) {
            if (WARNED_IDS.add(item)) {
                Logs.warn("Drop item {} is not registered, skipping", item);
            }
            return List.of();
        }

        RandomSource random = mob.getRandom();
        int count = minCount;
        if (maxCount > minCount) {
            count += random.nextInt(maxCount - minCount + 1);
        }
        if (looting && lootingLevel > 0) {
            count += random.nextInt(lootingLevel + 1);
        }

        int stackSize = Math.max(1, resolved.getDefaultMaxStackSize());
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = count;
        while (remaining > 0) {
            int part = Math.min(remaining, stackSize);
            stacks.add(new ItemStack(resolved, part));
            remaining -= part;
        }
        return stacks;
    }

    private List<ItemStack> createLootTableStacks(Mob mob, @Nullable Player killer, int lootingLevel) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return List.of();
        }
        LootTable table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, lootTable));
        if (table == LootTable.EMPTY) {
            if (WARNED_IDS.add(lootTable)) {
                Logs.warn("Drop loot table {} does not exist, skipping", lootTable);
            }
            return List.of();
        }
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, mob)
                .withParameter(LootContextParams.ORIGIN, mob.position())
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, killer)
                .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                .withLuck(lootingLevel)
                .create(LootContextParamSets.ENTITY);
        return table.getRandomItems(params);
    }
}
