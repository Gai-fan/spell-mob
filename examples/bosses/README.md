# SpellMob Boss 数据包

六个数据驱动的 Boss，全部只依赖本模组 + Iron's Spells 'n Spellbooks，不新增实体、物品或方块。

## 安装

1. 把整个 `bosses/` 目录复制到 `saves/<存档>/datapacks/spellmob_bosses/`（目录里应能看到 `pack.mcmeta` 和 `data/`）；
2. 进入世界执行 `/reload`；
3. 日志出现 `[SpellMob] Loaded N spell caster definition(s)` 即生效（装了本包应为 10 条：4 条内置 + 6 条 Boss——本包的 `husk.json` 会覆盖内置的尸壳示例）；
4. 用下面的命令召唤，`/spellmob info @e[tag=frost,limit=1]` 可以确认配置是否命中。

## 召唤命令

```mcfunction
# 霜寂领主 / Frost Sovereign（流浪者，120 HP）
/summon minecraft:stray ~ ~ ~5 {CustomName:'{"text":"霜寂领主","color":"aqua"}',CustomNameVisible:1b,Tags:["spellmob_boss","frost"],PersistenceRequired:1b,Attributes:[{Name:"generic.max_health",Base:120.0}],Health:120.0f}

# 血誓祭司 / Blood Oath Hierophant（僵尸村民，100 HP）
/summon minecraft:zombie_villager ~ ~ ~5 {CustomName:'{"text":"血誓祭司","color":"dark_red"}',CustomNameVisible:1b,Tags:["spellmob_boss","blood"],PersistenceRequired:1b,Attributes:[{Name:"generic.max_health",Base:100.0}],Health:100.0f}

# 雷霆战帅 / Storm Warlord（劫掠兽，200 HP）
/summon minecraft:ravager ~ ~ ~8 {CustomName:'{"text":"雷霆战帅","color":"yellow"}',CustomNameVisible:1b,Tags:["spellmob_boss","storm"],PersistenceRequired:1b,Attributes:[{Name:"generic.max_health",Base:200.0}],Health:200.0f}

# 虚空织法者 / Void Weaver（凋灵骷髅，160 HP）
/summon minecraft:wither_skeleton ~ ~ ~5 {CustomName:'{"text":"虚空织法者","color":"light_purple"}',CustomNameVisible:1b,Tags:["spellmob_boss","void"],PersistenceRequired:1b,Attributes:[{Name:"generic.max_health",Base:160.0}],Health:160.0f}

# 虚空之眼 / Void Eye（幻翼，120 HP，建议在夜晚或室内打）
/summon minecraft:phantom ~ ~8 ~ {CustomName:'{"text":"虚空之眼","color":"dark_purple"}',CustomNameVisible:1b,Tags:["spellmob_boss","void"],PersistenceRequired:1b,Size:3,Attributes:[{Name:"generic.max_health",Base:120.0}],Health:120.0f}

# 沙海祭司 / Dune Hierophant（尸壳，140 HP）
/summon minecraft:husk ~ ~ ~5 {CustomName:'{"text":"沙海祭司","color":"gold"}',CustomNameVisible:1b,Tags:["spellmob_boss","dune"],PersistenceRequired:1b,Attributes:[{Name:"generic.max_health",Base:140.0}],Health:140.0f}
```

## 设计意图与反制

| Boss | 压力来源 | 反制 |
|---|---|---|
| 霜寂领主 | 冰环近身惩罚、冰墙远程压制、挨打瞬移 | 打断 `frostwave`；别在瞬移冷却好时硬追 |
| 血誓祭司 | 越打小怪越多，击杀还会再召唤 | 优先打断 `raise_dead`，先清小怪 |
| 雷霆战帅 | 雷暴持续落雷、冲击波震退、雷矛点名 | 必须打断雷暴；分散站位防连锁闪电 |
| 虚空织法者 | 黑洞拉扯、穿墙声波、触手致盲 | 看脚下法阵躲黑洞；别躲柱子后 |
| 虚空之眼 | 星陨与黑洞共享 `void_ritual` 冷却、穿墙声波、受伤闪现、击杀补刀 | 看脚下法阵躲黑洞；打断 100 tick 的星陨；别在它抬手时贴脸（会闪现） |
| 沙海祭司 | 火息引导、枯尸召唤、吸血射线、中段狂暴 | 打断 `fire_breath` 与 `ray_of_siphoning`；被 `root` 后先拉开再贴脸 |

## 机制覆盖

这两份新配置（`phantom.json`、`husk.json`）是刻意做成"机制演示"的，几乎用满了 `spell_casters` 的全部能力：

- 触发器：`goals` / `onHurt` / `onTarget` / `onDeath` / `onKill` 五个全用；
- 条件：`healthBelow` / `healthAbove` / `minTargetDistance` / `maxTargetDistance` 四个全用；
- 选择器：`name` / `tags` / `healthBelow` / `healthAbove`，另有 `nameRegex` 测试变体（名字形如 `虚影-12` 的幻翼会换成一套引导测试法术）；
- 位置模式：`RELATIVE` / `EYES_LOOK` / `FEET_LOOK` / `TARGET_ENTITY` / `RANDOM_IN_RANGE` 五种全用；
- 学派：九大学派每个至少两条（虚空之眼：末影 / 邪术 / 雷电 / 鲜血 / 幻术 / 冰霜；沙海祭司：火焰 / 自然 / 神圣 / 幻术 / 鲜血 / 冰霜）；
- 引导法术：虚空之眼 4 条（`dragon_breath`、`starfall`、`telekinesis`，以及只在 `phantom_elite` 测试变体里出现的 `electrocute`），沙海祭司 4 条（`fire_breath`、`cone_of_cold`、`ray_of_siphoning`、`poison_breath`）。

两个测试变体（`phantom_elite` / `dune_elite` 标签）是给调试用的：召唤后执行 `/tag <实体> add phantom_elite`（或 `dune_elite`），即可在 40% / 60% 血线看到另一套法术；不加标签则永远走 Boss 正体。

## 调整

- 整体难度：`config/spellmob-common.toml` 的 `[balance] cooldownMultiplier`（0.5 = 技能频率翻倍，2.0 = 减半）；
- 单个 Boss 的节奏：改对应 JSON 里的 `cooldown`、`level`，或调整 `healthBelow` 阶段线；
- 想让 Boss 更“纯法系”：流浪者默认带弓，可执行
  `/item replace entity @e[tag=frost,limit=1] weapon.mainhand with minecraft:air`
  清掉武器，它就只能靠法术输出；
- 名字与 tag 必须同时匹配。若在 `[protection] protectedTags` 里加了 `spellmob_boss`，玩家无法用命名牌覆盖 Boss 的名字。

## 注意

- Boss 是普通生物的加强版，会被原版机制影响（击退、跌落、白天燃烧、被船/矿车困住等）；
- 本包里的 `husk.json` 与内置示例同名，会覆盖它：装了本包后普通尸壳不再施法，只有被召唤的“沙海祭司”会；想让普通尸壳继续按内置示例施法，删掉本包的 `husk.json` 即可；
- 幻翼在白天会燃烧，建议夜晚或室内使用；
- 改了 tag 之后要 `/spellmob resync` 才会立即生效，改名字会自动重同步。
