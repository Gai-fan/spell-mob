package spellmob.config;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server side configuration.
 *
 * <p>Every accessor is safe to call before the config has been loaded: it then falls back to the
 * built-in default instead of throwing, so that mod construction and the first data pack reload
 * cannot blow up.</p>
 */
public final class SpellMobConfig {

    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.BooleanValue DEBUG_LOGGING;
    private static final ModConfigSpec.BooleanValue CAST_SOUNDS;
    private static final ModConfigSpec.DoubleValue COOLDOWN_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue MAX_RANGE;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> ENTITY_BLACKLIST;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_BLACKLIST;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> PROTECTED_TAGS;
    private static final ModConfigSpec.BooleanValue ALLOW_OPS_RENAME;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> EXTRA_RULES;
    private static final ModConfigSpec.BooleanValue PRESENTATION_ENABLED;
    private static final ModConfigSpec.IntValue PRESENTATION_DENSITY;
    private static final ModConfigSpec.DoubleValue PRESENTATION_DISTANCE;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> BOSS_TAGS;
    private static final ModConfigSpec.BooleanValue BOSS_EXTRAS;
    private static final ModConfigSpec.BooleanValue FRONT_SIGIL;
    private static final ModConfigSpec.IntValue SIGIL_DETAIL;
    private static final ModConfigSpec.DoubleValue SIGIL_SCALE;
    private static final ModConfigSpec.DoubleValue SIGIL_DISTANCE;
    private static final ModConfigSpec.BooleanValue INTERRUPT_FEEDBACK;
    private static final ModConfigSpec.BooleanValue INTERRUPT_CHANNELS_ON_DAMAGE;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> CHANNEL_IMMUNE_TAGS;

    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Master switch for the whole mod.",
                        "（整个模组的总开关）").push("general");
        ENABLED = builder.define("enabled", true);
        DEBUG_LOGGING = builder.comment("Log every cast, cancellation and goal sync. Noisy, for debugging only.",
                        "（记录每次施法、打断与目标同步，很吵，仅用于调试）")
                .define("debugLogging", false);
        CAST_SOUNDS = builder.comment("Play the casting sounds Iron's Spellbooks uses for its own casters.",
                        "（播放 Iron's Spellbooks 给自家施法生物用的施法音效）")
                .define("castSounds", true);
        builder.pop();

        builder.comment("Balance knobs applied on top of the datapack values.",
                        "（在数据包数值之上叠加的平衡参数）").push("balance");
        COOLDOWN_MULTIPLIER = builder.comment("Multiplier applied to every cooldown (0 = never on cooldown).",
                        "（所有冷却的倍率，0 表示永不冷却）")
                .defineInRange("cooldownMultiplier", 1.0D, 0.0D, 100.0D);
        MAX_RANGE = builder.comment("Upper bound for datapack 'range' values, in blocks.",
                        "（数据包里 range 的上限，单位：格）")
                .defineInRange("maxRange", 32.0D, 1.0D, 128.0D);
        INTERRUPT_CHANNELS_ON_DAMAGE = builder.comment(
                        "Whether taking damage interrupts a channeled (CONTINUOUS) spell.",
                        "Normal casts keep using the per spell 'interruptOnDamage' flag instead.",
                        "（受伤是否打断引导型法术；普通吟唱仍由每个法术的 interruptOnDamage 决定）")
                .define("interruptChannelsOnDamage", true);
        builder.pop();

        builder.comment("Filter out entities or dimensions.",
                        "（按生物或维度过滤）").push("filters");
        ENTITY_BLACKLIST = builder.comment("Entity ids that must never cast, e.g. minecraft:villager.",
                        "（永远不施法的生物 id，例如 minecraft:villager）")
                .defineList("entityBlacklist", List.<String>of(), value -> value instanceof String);
        DIMENSION_BLACKLIST = builder.comment("Dimension ids where casting is disabled, e.g. minecraft:the_nether.",
                        "（禁用施法的维度 id，例如 minecraft:the_nether）")
                .defineList("dimensionBlacklist", List.<String>of(), value -> value instanceof String);
        builder.pop();

        builder.comment("Keep the names of hand picked mobs from being changed by players.",
                        "（保护指定生物的名字不被玩家改动）").push("protection");
        PROTECTED_TAGS = builder.comment(
                        "Mobs carrying any of these scoreboard tags keep their current name:",
                        "a name tag cannot overwrite it. Only operators can rename them when allowOpsRename is true.",
                        "（带这些计分板标签的生物会保留当前名字，命名牌无法覆盖；allowOpsRename 为 true 时仅 OP 可以改名）")
                .defineList("protectedTags", List.<String>of(), value -> value instanceof String);
        ALLOW_OPS_RENAME = builder.comment("Let players with permission level 2 rename name-protected mobs anyway.",
                        "（允许权限等级 2 的玩家重命名受保护的生物）")
                .define("allowOpsRename", true);
        CHANNEL_IMMUNE_TAGS = builder.comment(
                        "Mobs carrying any of these scoreboard tags never lose a channeled spell to damage.",
                        "Tag immunity takes precedence over interruptChannelsOnDamage.",
                        "（带这些计分板标签的生物不会因受伤中断引导；标签免疫优先于 interruptChannelsOnDamage）")
                .defineList("channelImmuneTags", List.of("spellmob_boss"), value -> value instanceof String);
        builder.pop();

        builder.comment("Purely cosmetic feedback for mob casting. Every effect can be turned off.",
                        "（生物施法的纯表现效果，所有效果都可以关闭）").push("presentation");
        PRESENTATION_ENABLED = builder.comment("Draw the casting circle and show interrupt feedback.",
                        "（绘制施法法阵并显示中断反馈）")
                .define("enabled", true);
        PRESENTATION_DENSITY = builder.comment("Dots per circle ring: 0 = no circle, 1 = 8, 2 = 12, 3 = 16.",
                        "（法阵每圈的点数：0 不画，1 为 8 点，2 为 12 点，3 为 16 点）")
                .defineInRange("density", 1, 0, 3);
        PRESENTATION_DISTANCE = builder.comment("Only players within this many blocks receive the particles.",
                        "（只有这个距离内的玩家会收到粒子）")
                .defineInRange("distance", 24.0D, 4.0D, 64.0D);
        INTERRUPT_FEEDBACK = builder.comment("Smoke puff and extinguish sound when a cast is interrupted.",
                        "（施法被打断时冒烟并播放熄灭音效）")
                .define("interruptFeedback", true);
        BOSS_TAGS = builder.comment("Mobs carrying any of these scoreboard tags use the boss circle.",
                        "（带有任一这些计分板标签的生物使用更华丽的 Boss 法阵）")
                .defineList("bossTags", List.of("spellmob_boss"), value -> value instanceof String);
        BOSS_EXTRAS = builder.comment("Boss circles also get the floating rune halo, spark pillars and pulse rings.",
                        "（Boss 法阵额外加上悬浮符环、光柱与脉冲环）")
                .define("bossExtras", true);
        FRONT_SIGIL = builder.comment("Draw a vertical hexagram in front of the caster, in the school colour.",
                        "（在施法者正前方竖着画一个学派颜色的六芒星法阵）")
                .define("frontSigil", true);
        SIGIL_DETAIL = builder.comment("Dust points per emission: 1 = 26, 2 = 36, 3 = 52. Higher is smoother.",
                        "（六芒星每次发射的点数：1 为 26 点，2 为 36 点，3 为 52 点；越高越平滑）")
                .defineInRange("sigilDetail", 2, 1, 3);
        SIGIL_SCALE = builder.comment("Size multiplier of the vertical hexagram.",
                        "（竖立法阵的尺寸倍率）")
                .defineInRange("sigilScale", 1.0D, 0.4D, 2.0D);
        SIGIL_DISTANCE = builder.comment("Blocks in front of the caster's eyes where the hexagram floats.",
                        "（法阵悬浮在施法者眼前多少格处）")
                .defineInRange("sigilDistance", 1.15D, 0.4D, 3.0D);
        builder.pop();

        builder.comment("Species wide rules kept in this file instead of a data pack.",
                        "（写在本文件而不是数据包里的物种级规则）").push("species");
        EXTRA_RULES = builder.comment(
                        "Species wide casting rules defined here instead of in a data pack.",
                        "Each entry is one JSON object on a single line, e.g.",
                        "  {\"entity\":\"minecraft:cow\",\"goals\":[{\"spell\":\"irons_spellbooks:fireball\",\"level\":1}]}",
                        "The schema is the one of data/<namespace>/spell_casters/<entity>.json minus 'variants'.",
                        "When a data pack also defines the entity type, the data pack wins and this entry is ignored.",
                        "（每条是单行 JSON 对象；schema 与 data/<命名空间>/spell_casters/<实体>.json 相同但不含 variants；",
                        "  同一物种在数据包里也存在时以数据包为准，这条会被忽略）")
                .defineList("extraRules", List.<String>of(), value -> value instanceof String);
        builder.pop();

        SPEC = builder.build();
    }

    private SpellMobConfig() {
    }

    private static boolean ready() {
        return SPEC.isLoaded();
    }

    public static boolean enabled() {
        return !ready() || ENABLED.get();
    }

    public static boolean debugLogging() {
        return ready() && DEBUG_LOGGING.get();
    }

    public static boolean castSounds() {
        return !ready() || CAST_SOUNDS.get();
    }

    public static double cooldownMultiplier() {
        return ready() ? COOLDOWN_MULTIPLIER.get() : 1.0D;
    }

    public static double maxRange() {
        return ready() ? MAX_RANGE.get() : 32.0D;
    }

    public static boolean isEntityBlacklisted(ResourceLocation id) {
        return ready() && contains(ENTITY_BLACKLIST.get(), id);
    }

    public static boolean isDimensionBlacklisted(ResourceLocation id) {
        return ready() && contains(DIMENSION_BLACKLIST.get(), id);
    }

    /** @return the configured species rules, one single line JSON object per entry */
    public static List<String> extraRules() {
        if (!ready()) {
            return List.of();
        }
        List<String> entries = new ArrayList<>();
        for (String value : EXTRA_RULES.get()) {
            if (value != null && !value.isBlank()) {
                entries.add(value);
            }
        }
        return entries;
    }

    /** @return whether operators may rename name-protected mobs */
    public static boolean allowOpsRename() {
        return !ready() || ALLOW_OPS_RENAME.get();
    }

    /** @return whether the casting circle and interrupt feedback are drawn */
    public static boolean presentationEnabled() {
        return !ready() || PRESENTATION_ENABLED.get();
    }

    /** @return ring dot preset: 0 disables the circle, 1 to 3 select 8, 12 or 16 dots */
    public static int presentationDensity() {
        return ready() ? PRESENTATION_DENSITY.get() : 1;
    }

    /** @return radius in blocks inside which players receive casting particles */
    public static double presentationDistance() {
        return ready() ? PRESENTATION_DISTANCE.get() : 24.0D;
    }

    /** @return whether an interrupted cast shows a smoke puff and a sound */
    public static boolean interruptFeedback() {
        return !ready() || INTERRUPT_FEEDBACK.get();
    }

    /**
     * @param entity the casting entity
     * @return true when the entity carries one of the configured boss tags
     */
    public static boolean isBossCaster(Entity entity) {
        if (!ready() || entity == null) {
            return false;
        }
        List<? extends String> tags = BOSS_TAGS.get();
        if (tags.isEmpty()) {
            return false;
        }
        for (String tag : tags) {
            if (entity.getTags().contains(tag)) {
                return true;
            }
        }
        return false;
    }

    /** @return whether the boss circle adds the halo, spark pillars and pulse rings */
    public static boolean bossExtras() {
        return !ready() || BOSS_EXTRAS.get();
    }

    /** @return whether a vertical hexagram is drawn in front of the caster */
    public static boolean frontSigil() {
        return !ready() || FRONT_SIGIL.get();
    }

    /** @return hexagram detail preset: 1 to 3 select 26, 36 or 52 points per emission */
    public static int sigilDetail() {
        return ready() ? SIGIL_DETAIL.get() : 2;
    }

    /** @return size multiplier applied to the vertical hexagram */
    public static double sigilScale() {
        return ready() ? SIGIL_SCALE.get() : 1.0D;
    }

    /** @return blocks in front of the eyes where the hexagram floats */
    public static double sigilDistance() {
        return ready() ? SIGIL_DISTANCE.get() : 1.15D;
    }

    /**
     * @param entity the entity a name tag is being used on
     * @return true when the entity carries one of the configured protected tags
     */
    public static boolean isNameProtected(Entity entity) {
        if (!ready()) {
            return false;
        }
        for (String tag : PROTECTED_TAGS.get()) {
            if (tag != null && !tag.isEmpty() && entity.getTags().contains(tag)) {
                return true;
            }
        }
        return false;
    }

    /** @return whether damage interrupts a channeled (CONTINUOUS) spell */
    public static boolean interruptChannelsOnDamage() {
        return !ready() || INTERRUPT_CHANNELS_ON_DAMAGE.get();
    }

    /**
     * @param entity the channeling entity
     * @return true when the entity carries a tag that makes its channels uninterruptible
     */
    public static boolean isChannelImmune(Entity entity) {
        if (!ready()) {
            return false;
        }
        for (String tag : CHANNEL_IMMUNE_TAGS.get()) {
            if (tag != null && !tag.isEmpty() && entity.getTags().contains(tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(List<? extends String> values, ResourceLocation id) {
        String needle = id.toString();
        for (String value : values) {
            if (value.equals(needle)) {
                return true;
            }
        }
        return false;
    }
}
