package spellmob.events;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spellmob.SpellMob;
import spellmob.capability.SpellCasterCapability;
import spellmob.data.SpellCastDefinition;
import spellmob.data.SpellCasterData;
import spellmob.data.SpellCasterRegistry;
import spellmob.data.SpellCasterRules;
import spellmob.data.SpellCasterVariant;
import spellmob.goal.CastGoalSync;

/**
 * Operator commands used to inspect and refresh the loaded definitions.
 *
 * <p>Renaming a mob or adding a scoreboard tag changes which variant matches, but the goal set is
 * only rebuilt when something asks for it. Renames are picked up automatically by
 * {@code SpellCaster#tick()}; tags and {@code /reload} driven edits can be applied with
 * {@code /spellmob resync}.</p>
 */
@Mod.EventBusSubscriber(modid = SpellMob.MOD_ID)
public final class SpellMobCommands {

    private SpellMobCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("spellmob")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("resync")
                        .executes(SpellMobCommands::resyncSelf)
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(context -> resync(context.getSource(),
                                        EntityArgument.getEntities(context, "targets")))))
                .then(Commands.literal("info")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(context -> info(context.getSource(),
                                        EntityArgument.getEntity(context, "target")))))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("validate")
                        .executes(context -> validate(context.getSource()))));
    }

    private static int resyncSelf(CommandContext<CommandSourceStack> context) {
        Entity entity = context.getSource().getEntity();
        if (!(entity instanceof Mob mob)) {
            context.getSource().sendFailure(Component.literal("Run this as a mob, or pass <targets>"));
            return 0;
        }
        return resync(context.getSource(), List.of(mob));
    }

    private static int resync(CommandSourceStack source, Collection<? extends Entity> targets) {
        int count = 0;
        for (Entity entity : targets) {
            if (entity instanceof Mob mob) {
                CastGoalSync.sync(mob);
                count++;
            }
        }
        int resynced = count;
        source.sendSuccess(() -> Component.literal("Resynced " + resynced + " spell caster(s)"), true);
        return count;
    }

    private static int info(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof Mob mob)) {
            source.sendFailure(Component.literal("Only mobs can be spell casters"));
            return 0;
        }
        String name = mob.getName().getString();
        SpellCasterRules rules = SpellCasterRegistry.get(mob.getType());
        if (rules == null) {
            source.sendSuccess(() -> Component.literal(name + " has no spell_casters data"), false);
            return 1;
        }
        SpellCasterData data = rules.resolve(mob);
        source.sendSuccess(() -> Component.literal(name + " -> " + rules.describeMatch(mob)
                + " [" + rules.source() + "]"), false);
        source.sendSuccess(() -> Component.literal("  goals=" + data.goals().size()
                + ", onHurt=" + (data.onHurt() != null)
                + ", onTarget=" + (data.onTarget() != null)
                + ", onDeath=" + (data.onDeath() != null)
                + ", onKill=" + (data.onKill() != null)), false);
        if (data.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  this mob casts nothing")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        for (SpellCastDefinition definition : data.goals()) {
            int remaining = mob.getCapability(SpellCasterCapability.capability())
                    .map(caster -> caster.cooldownTicks(definition))
                    .orElse(0);
            source.sendSuccess(() -> Component.literal("  - " + definition.spellId()
                    + " lv" + definition.level()
                    + " cd=" + remaining + "t"
                    + " prio=" + definition.priority()
                    + " range=" + String.format(Locale.ROOT, "%.1f", definition.effectiveRange())
                    + (definition.cooldownGroup() == null ? "" : " group=" + definition.cooldownGroup())
                    + (definition.chance() < 1.0D
                            ? " chance=" + String.format(Locale.ROOT, "%.2f", definition.chance()) : "")
                    + (definition.interruptOnDamage() ? " interruptOnDamage" : "")), false);
        }
        mob.getCapability(SpellCasterCapability.capability()).ifPresent(caster -> {
            SpellCastDefinition casting = caster.castingDefinition();
            if (casting != null) {
                source.sendSuccess(() -> Component.literal("  casting " + casting.spellId()
                        + " lv" + casting.level() + " (" + caster.castTicksRemaining() + "t left)")
                        .withStyle(ChatFormatting.AQUA), false);
            }
        });
        return 1;
    }

    private static int list(CommandSourceStack source) {
        List<ResourceLocation> ids = SpellCasterRegistry.ids();
        if (ids.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No spell_casters definitions loaded"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Loaded " + ids.size() + " spell caster definition(s):"), false);
        for (ResourceLocation id : ids) {
            SpellCasterRules rules = SpellCasterRegistry.get(id);
            int variants = rules == null ? 0 : rules.variants().size();
            String origin = rules == null ? "?" : rules.source();
            source.sendSuccess(() -> Component.literal("  " + id + " (" + variants + " variant(s), " + origin + ")"),
                    false);
        }
        return ids.size();
    }

    /**
     * Re-checks every loaded cast definition against the current Iron's Spellbooks spell registry.
     *
     * <p>Useful after updating the spell mod or toggling spells in its config: entries that were
     * valid when the data pack loaded can become unusable later.</p>
     */
    private static int validate(CommandSourceStack source) {
        List<SpellCastDefinition> definitions = new ArrayList<>();
        for (ResourceLocation id : SpellCasterRegistry.ids()) {
            SpellCasterRules rules = SpellCasterRegistry.get(id);
            if (rules == null) {
                continue;
            }
            collect(rules.fallback(), definitions);
            for (SpellCasterVariant variant : rules.variants()) {
                collect(variant.data(), definitions);
            }
        }

        int problems = 0;
        for (SpellCastDefinition definition : definitions) {
            AbstractSpell spell = definition.spell();
            boolean usable = spell != null
                    && definition.spellId().toString().equals(spell.getSpellId())
                    && spell.isEnabled();
            if (!usable) {
                problems++;
                String message = definition.where() + ": " + definition.spellId()
                        + " is missing or disabled in the Iron's Spellbooks config";
                source.sendSuccess(() -> Component.literal("  " + message).withStyle(ChatFormatting.RED), false);
            }
        }
        for (ResourceLocation id : SpellCasterRegistry.shadowed()) {
            source.sendSuccess(() -> Component.literal("  " + id
                    + ": config rule ignored, a data pack defines it").withStyle(ChatFormatting.YELLOW), false);
        }

        int checked = definitions.size();
        if (problems == 0) {
            source.sendSuccess(() -> Component.literal("All " + checked + " cast definition(s) are valid")
                    .withStyle(ChatFormatting.GREEN), false);
        } else {
            int found = problems;
            source.sendSuccess(() -> Component.literal(found + " problem(s) in " + checked + " cast definition(s)")
                    .withStyle(ChatFormatting.RED), false);
        }
        return problems;
    }

    private static void collect(SpellCasterData data, List<SpellCastDefinition> output) {
        output.addAll(data.goals());
        if (data.onHurt() != null) {
            output.add(data.onHurt());
        }
        if (data.onTarget() != null) {
            output.add(data.onTarget());
        }
        if (data.onDeath() != null) {
            output.add(data.onDeath());
        }
        if (data.onKill() != null) {
            output.add(data.onKill());
        }
    }
}
