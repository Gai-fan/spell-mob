package spellmob.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.ForgeConfigSpec;
import spellmob.util.Logs;

/**
 * Server side configuration.
 *
 * <p>Every accessor is safe to call before the config has been loaded: it then falls back to the
 * built-in default instead of throwing, so that mod construction and the first data pack reload
 * cannot blow up.</p>
 */
public final class SpellMobConfig {

    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING;
    private static final ForgeConfigSpec.DoubleValue COOLDOWN_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue MAX_RANGE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITY_BLACKLIST;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> DIMENSION_BLACKLIST;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> PROTECTED_TAGS;
    private static final ForgeConfigSpec.BooleanValue ALLOW_OPS_RENAME;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXTRA_RULES;
    private static final ForgeConfigSpec.BooleanValue PRESENTATION_ENABLED;
    private static final ForgeConfigSpec.IntValue PRESENTATION_POINTS;
    private static final ForgeConfigSpec.IntValue BOSS_POINTS;
    private static final ForgeConfigSpec.DoubleValue RING_SCALE;
    private static final ForgeConfigSpec.DoubleValue PRESENTATION_DISTANCE;
    private static final ForgeConfigSpec.BooleanValue INTERRUPT_FEEDBACK;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> BOSS_TAGS;
    private static final ForgeConfigSpec.BooleanValue BOSS_EXTRAS;
    private static final ForgeConfigSpec.BooleanValue BOSS_BAR;
    private static final ForgeConfigSpec.ConfigValue<String> BOSS_BAR_COLOR;
    private static final ForgeConfigSpec.DoubleValue BOSS_BAR_RANGE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> BOSS_BAR_TAGS;
    private static final ForgeConfigSpec.BooleanValue BOSS_BAR_CAST_NAME;
    private static final ForgeConfigSpec.BooleanValue INTERRUPT_CHANNELS_ON_DAMAGE;
    private static final ForgeConfigSpec.IntValue GLOBAL_COOLDOWN;
    private static final ForgeConfigSpec.BooleanValue ADAPTIVE_ENABLED;
    private static final ForgeConfigSpec.ConfigValue<String> ADAPTIVE_TAG;
    private static final ForgeConfigSpec.IntValue MAX_LEARNED_SPELLS;
    private static final ForgeConfigSpec.IntValue ADAPTIVE_REMEMBERED;
    private static final ForgeConfigSpec.IntValue LEARNED_COOLDOWN;
    private static final ForgeConfigSpec.IntValue LEARNED_PRIORITY;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ADAPTIVE_BLACKLIST;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> CHANNEL_IMMUNE_TAGS;

    public static final ForgeConfigSpec SPEC;

    /** Set after the first invalid colour so the log does not repeat every frame. */
    private static boolean WARNED_BOSS_BAR_COLOR;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Master switch for the whole mod.",
                        "（整个模组的总开关）").push("general");
        ENABLED = builder.define("enabled", true);
        DEBUG_LOGGING = builder.comment("Log every cast, cancellation and goal sync. Noisy, for debugging only.",
                        "（记录每次施法、打断与目标同步，很吵，仅用于调试）")
                .define("debugLogging", false);
        builder.pop();

        builder.comment("Balance knobs applied on top of the datapack values.",
                        "（在数据包数值之上叠加的平衡参数）").push("balance");
        COOLDOWN_MULTIPLIER = builder.comment("Multiplier applied to every cooldown (0 = no extra length, still at least one tick).",
                        "（所有冷却的倍率，0 表示不额外延长，仍至少 1 tick）")
                .defineInRange("cooldownMultiplier", 1.0D, 0.0D, 100.0D);
        MAX_RANGE = builder.comment("Upper bound for datapack 'range' values, in blocks.",
                        "（数据包里 range 的上限，单位：格）")
                .defineInRange("maxRange", 32.0D, 1.0D, 128.0D);
        INTERRUPT_CHANNELS_ON_DAMAGE = builder.comment(
                        "Whether taking damage interrupts a channeled (CONTINUOUS) spell.",
                        "Normal casts keep using the per spell 'interruptOnDamage' flag instead.",
                        "（受伤是否打断引导型法术；普通吟唱仍由每个法术的 interruptOnDamage 决定）")
                .define("interruptChannelsOnDamage", true);
        GLOBAL_COOLDOWN = builder.comment(
                        "Global cooldown in ticks shared by every spell of one caster (0 = off).",
                        "After any cast starts, no other spell of that caster can start for this long,",
                        "which stops a mob from dumping its whole spell list in a few seconds.",
                        "（全局冷却，单位 tick，0 表示关闭；一次施法后该施法者的所有法术都要等这么久，防止一轮爆发）")
                .defineInRange("globalCooldown", 0, 0, 400);
        builder.pop();

        builder.comment("Bosses that learn the spells of players that lose to them.",
                        "（会学习败者法术的 Boss）").push("adaptive");
        ADAPTIVE_ENABLED = builder.comment("Master switch for adaptive bosses.",
                        "Bosses also need the scoreboard tag from 'adaptiveTag'.",
                        "（总开关；Boss 还需要带上 adaptiveTag 指定的计分板标签）")
                .define("enabled", true);
        ADAPTIVE_TAG = builder.comment("Scoreboard tag that marks a boss as able to learn.",
                        "（标记“会学习”的计分板标签）")
                .define("adaptiveTag", "spellmob_adaptive");
        MAX_LEARNED_SPELLS = builder.comment("How many spells one boss may learn in total.",
                        "（一个 Boss 最多能学会几个法术；0 表示不学）")
                .defineInRange("maxLearnedSpells", 1, 0, 8);
        ADAPTIVE_REMEMBERED = builder.comment("How many spells of one player are remembered per fight.",
                        "（每场战斗最多记住一个玩家的几个法术）")
                .defineInRange("rememberedSpells", 6, 1, 16);
        LEARNED_COOLDOWN = builder.comment("Cooldown of a learned spell, in ticks.",
                        "（学会的法术的冷却，单位 tick；-1 表示跟随该法术自己的冷却）")
                .defineInRange("learnedCooldown", -1, -1, 1200);
        LEARNED_PRIORITY = builder.comment("Goal priority of a learned spell (lower runs first).",
                        "（学会的法术的 Goal 优先级，越小越先执行）")
                .defineInRange("learnedPriority", 1, 0, 9);
        ADAPTIVE_BLACKLIST = builder.comment("Spells a boss is never allowed to learn, e.g. escapes.",
                        "（永不被学会的法术 id，例如传送类）")
                .defineList("spellBlacklist",
                        List.of("irons_spellbooks:teleport", "irons_spellbooks:blood_step",
                                "irons_spellbooks:evasion"),
                        value -> value instanceof String);
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
        PRESENTATION_POINTS = builder.comment("Particles per casting circle ring, 0 disables the circle (0 - 128).",
                        "Values below 6 are barely visible, 8 - 48 is the useful range.",
                        "（法阵每圈的点数，0 表示不画法阵，范围 0 - 128；小于 6 基本看不见，常用 8 - 48）")
                .defineInRange("points", 24, 0, 128);
        BOSS_POINTS = builder.comment("Particles per boss ring, 0 disables the boss ring (0 - 128).",
                        "Boss casters also pulse extra dust rings outward while they channel.",
                        "（Boss 圆环每圈的点数，0 表示不画 Boss 圆环，范围 0 - 128；Boss 吟唱时还会向外扩散尘环脉冲）")
                .defineInRange("bossPoints", 32, 0, 128);
        RING_SCALE = builder.comment("Multiplier applied to the casting circle radius.",
                        "（施法法阵半径的倍率）")
                .defineInRange("ringScale", 1.0D, 0.25D, 4.0D);
        PRESENTATION_DISTANCE = builder.comment("Only players within this many blocks receive the particles.",
                        "（只有这个距离内的玩家会收到粒子）")
                .defineInRange("distance", 24.0D, 4.0D, 64.0D);
        INTERRUPT_FEEDBACK = builder.comment("Smoke puff and extinguish sound when a cast is interrupted.",
                        "（施法被打断时冒烟并播放熄灭音效）")
                .define("interruptFeedback", true);
        BOSS_TAGS = builder.comment("Mobs carrying any of these scoreboard tags use the boss effects.",
                        "Boss casters get a second, larger circle under their feet.",
                        "（带有任一这些计分板标签的生物使用 Boss 特效；Boss 施法者脚下会多一圈更大的外环并周期性脉冲）")
                .defineList("bossTags", List.of("spellmob_boss"), value -> value instanceof String);
        BOSS_EXTRAS = builder.comment("Boss casters also pulse extra dust rings outward every 10 ticks.",
                        "（Boss 施法者还会每 10 tick 向外扩散一圈脉冲尘环）")
                .define("bossExtras", true);
        BOSS_BAR = builder.comment("Show a boss bar above casters that carry one of 'bossBarTags'.",
                        "Independent from 'enabled', so the particles can stay off while the bar is on.",
                        "（为带有 bossBarTags 标签的施法者显示 Boss 血条；与 enabled 相互独立，可以只开血条不开法阵）")
                .define("bossBar", false);
        BOSS_BAR_COLOR = builder.comment("Boss bar colour: pink, blue, red, green, yellow, purple or white.",
                        "（Boss 血条颜色：pink / blue / red / green / yellow / purple / white）")
                .define("bossBarColor", "white");
        BOSS_BAR_RANGE = builder.comment("Players within this many blocks see the boss bar.",
                        "（这个距离内的玩家能看到 Boss 血条）")
                .defineInRange("bossBarRange", 48.0D, 8.0D, 128.0D);
        BOSS_BAR_TAGS = builder.comment("Mobs carrying any of these scoreboard tags get a boss bar.",
                        "Empty means every mob with casting rules gets one.",
                        "（带有任一这些计分板标签的生物显示 Boss 血条；留空表示所有配置了施法规则的生物都显示）")
                .defineList("bossBarTags", List.of("spellmob_boss"), value -> value instanceof String);
        BOSS_BAR_CAST_NAME = builder.comment(
                        "Append 'casting <spell>' to the boss bar name while the mob is casting.",
                        "Turn this off to keep the plain mob name, for example when the bar is only a health display.",
                        "（施法时在血条名字后面显示“正在施放 xxx”；关掉后只显示生物名字）")
                .define("bossBarCastName", true);
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

    public static double cooldownMultiplier() {
        return ready() ? COOLDOWN_MULTIPLIER.get() : 1.0D;
    }

    public static double maxRange() {
        return ready() ? MAX_RANGE.get() : 32.0D;
    }

    /** @return the global cooldown in ticks shared by every spell of one caster, 0 when disabled */
    public static int globalCooldownTicks() {
        return ready() ? Math.max(0, GLOBAL_COOLDOWN.get()) : 0;
    }

    /** @return whether bosses may learn spells from players that lose to them */
    public static boolean adaptiveEnabled() {
        return ready() && ADAPTIVE_ENABLED.get();
    }

    /** @return the scoreboard tag that marks a boss as able to learn */
    public static String adaptiveTag() {
        return ready() ? ADAPTIVE_TAG.get() : "spellmob_adaptive";
    }

    /** @return how many spells one boss may learn in total */
    public static int maxLearnedSpells() {
        return ready() ? MAX_LEARNED_SPELLS.get() : 1;
    }

    /** @return how many spells of one player are remembered per fight */
    public static int adaptiveRememberedSpells() {
        return ready() ? ADAPTIVE_REMEMBERED.get() : 6;
    }

    /** @return cooldown of a learned spell, in ticks */
    public static int learnedCooldownTicks() {
        return ready() ? LEARNED_COOLDOWN.get() : -1;
    }

    /** @return goal priority of a learned spell */
    public static int learnedPriority() {
        return ready() ? LEARNED_PRIORITY.get() : 1;
    }

    /**
     * @param spellId the spell a boss would learn
     * @return true when the blacklist forbids learning that spell
     */
    public static boolean isAdaptiveBlacklisted(ResourceLocation spellId) {
        if (!ready()) {
            return false;
        }
        String name = spellId.toString();
        for (String entry : ADAPTIVE_BLACKLIST.get()) {
            if (entry != null && entry.equals(name)) {
                return true;
            }
        }
        return false;
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

    /** @return dust dots per casting circle ring, 0 disables the circle */
    public static int presentationPoints() {
        return ready() ? PRESENTATION_POINTS.get() : 24;
    }

    /** @return dust dots per boss ring, 0 disables the boss ring */
    public static int bossPoints() {
        return ready() ? BOSS_POINTS.get() : 32;
    }

    /** @return multiplier applied to the casting circle radius */
    public static double ringScale() {
        return ready() ? RING_SCALE.get() : 1.0D;
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

    /** @return whether the boss circle adds the periodic pulse rings and the finishing blast */
    public static boolean bossExtras() {
        return !ready() || BOSS_EXTRAS.get();
    }

    /** @return whether casters carrying {@link #bossBarTags()} show a boss bar */
    public static boolean bossBarEnabled() {
        return ready() && BOSS_BAR.get();
    }

    /** @return radius in blocks inside which players see the boss bar */
    public static double bossBarRange() {
        return ready() ? BOSS_BAR_RANGE.get() : 48.0D;
    }

    /** @return whether the boss bar name shows the spell the mob is currently casting */
    public static boolean bossBarCastName() {
        return !ready() || BOSS_BAR_CAST_NAME.get();
    }

    /**
     * @param entity the casting entity
     * @return true when the entity should get a boss bar (an empty tag list matches every caster)
     */
    public static boolean isBossBarCaster(Entity entity) {
        if (!ready() || entity == null) {
            return false;
        }
        List<? extends String> tags = BOSS_BAR_TAGS.get();
        if (tags.isEmpty()) {
            return true;
        }
        for (String tag : tags) {
            if (tag != null && entity.getTags().contains(tag)) {
                return true;
            }
        }
        return false;
    }

    /** @return the configured boss bar colour, falling back to white when the value is unknown */
    public static BossEvent.BossBarColor bossBarColor() {
        String raw = ready() ? BOSS_BAR_COLOR.get() : "white";
        if (raw == null) {
            return BossEvent.BossBarColor.WHITE;
        }
        try {
            return BossEvent.BossBarColor.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            if (!WARNED_BOSS_BAR_COLOR) {
                WARNED_BOSS_BAR_COLOR = true;
                Logs.warn("Unknown presentation.bossBarColor '{}', using white", raw);
            }
            return BossEvent.BossBarColor.WHITE;
        }
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
