# SpellMob

一个数据驱动的生物施法模组：用数据包给任意生物配置法术，让它们像术士一样吟唱、索敌、开火。

* Minecraft 1.21.1 / NeoForge 21.1.x
* 必须安装 [Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks) `1.21.1-3.16.3` 或更高
* 纯服务端逻辑，客户端无需安装（但联机时服务端必须装）

> 完整使用文档：[中文](docs/guide.zh.md) · [English](docs/guide.en.md)

## 特性

* **数据包驱动** —— 不注册任何新实体、方块或物品，只解析 `data/<namespace>/spell_casters/<entity_id>.json`
* **五种触发方式** —— 周期性施法（`goals`）、受伤时（`onHurt`）、锁定目标时（`onTarget`）、死亡时（`onDeath`）、击杀后（`onKill`）
* **15 种目标类型 + 5 种位置模式**，覆盖"打当前目标""打最近的亡灵""传送到随机位置"等常见需求
* **走 Iron's Spellbooks 自己的施法管线** —— 读条、施法音效与 `CastSource.MOB` 语义与 ISS 一致（冷却由数据包控制），而不是直接调 `onCast`
* **真正可用的优先级** —— JSON 里的 `priority` 会用于 `GoalSelector`，施法期间占用 `MOVE` + `LOOK`，不会再被原版近战 AI 抢掉
* **全局配置** —— `config/spellmob-common.toml`：总开关、冷却倍率、最大射程、实体/维度黑名单、名字保护、粒子表现、物种规则
* **个体定向** —— `variants` 按名字、正则或 scoreboard tag 挑出个别生物：同名物种里 A 与 B 可以放不同法术，其余个体完全不施法
* **物种规则也能写进配置** —— `species.extraRules` 一行一条 JSON，可直接用 Configured 之类的 GUI 编辑物种级规则；同一物种若已有数据包文件，则以数据包为准（配置项被忽略并打一行日志）
* **名字锁** —— 给生物打上 `protection.protectedTags` 里的 tag 后，普通玩家无法用命名牌覆盖它已有的名字（OP 可放行）
* **施法表现** —— 吟唱时脚下浮现学派配色的旋转法阵，环的大小随读条进度呼吸；Boss 施法者脚下会多一圈更大的同色外环，并在吟唱期间周期性地向外扩散脉冲尘环；被打断时冒烟并播放熄灭音效。纯服务端发包，客户端不需要安装本模组
* **阶段与节奏** —— `select.healthBelow` 让同一个生物半血后换一套法术；`interruptOnDamage` 让吟唱被伤害打断；`cooldownGroup` 让换阶段时共享冷却；`chance` 让法术按概率触发
* **特殊怪掉落** —— `dropRules` 按名字、正则、tag 或血量挑出个别生物，而且只有会施法的怪才掉；支持固定物品、数量区间、概率、抢夺加成与整张战利品表

## 快速开始

1. 安装 NeoForge 21.1.x 与 Iron's Spells 'n Spellbooks；
2. 把数据包放进世界的 `datapacks/` 目录（或模组的 `data/` 里），路径形如：

   ```
   data/<namespace>/spell_casters/<entity_id>.json
   ```

   例如 `data/minecraft/spell_casters/zombie.json` 对应 `minecraft:zombie`；
   模组生物则用它自己的命名空间，如 `data/irons_spellbooks/spell_casters/necromancer.json`。

3. 进游戏执行 `/reload`；
4. 生成对应生物验证。

安装后**默认附带一份双尸壳示例**（`data/minecraft/spell_casters/husk.json`，源码见 `examples/husk_twins_1_21_1/`）：带 `husk_boss` + `spellmob_boss` 标签的是 Boss 版（12 种法术、Boss 法阵、引导免打断），带 `husk_elite` 标签的是精英版（另外 12 种法术）；野生尸壳没有这些标签，行为保持不变。此外 jar 内还内置了五名**只能召唤**的 Boss（`data/twilightforest/spell_casters/{naga,lich,alpha_yeti}.json` 与 `data/minecraft/spell_casters/{drowned,phantom}.json`）：娜迦（自然系）、巫妖（13 种）、雪怪首领（冰系）、溺尸 jack（雷系）与幻翼（8 种）。它们都靠 `spellmob_boss` + 各自专属标签识别，野生同类完全不受影响；召唤命令见 `docs/guide.zh.md`。想要更多示例，从仓库的 `examples/` 里挑——`classic/`、`bosses/`、`channel_test/`、`kitchen_sink/`、`twilight_forest_1_21_1/`、`twilight_forest_bosses_1_21_1/`；`full-reference.json` 是字段最全的参考 JSON，不是数据包。

### Boss 数据包（`examples/bosses/`）

仓库里的 `examples/bosses/` 是一份独立数据包，内置六个“名字 + tag”双重限定的 Boss，野生同类生物不受影响：

| 文件 | Boss | 基底 | 机制 |
|---|---|---|---|
| `stray.json` | 霜寂领主 | 流浪者 | 三阶段冰系；挨打瞬移；死亡冰环 |
| `zombie_villager.json` | 血誓祭司 | 僵尸村民 | 召唤亡灵滚雪球；击杀/死亡都召唤 |
| `ravager.json` | 雷霆战帅 | 劫掠兽 | 雷暴控场 + 冲击波反击；阵亡雷暴 |
| `wither_skeleton.json` | 虚空织法者 | 凋灵骷髅 | 黑洞拉扯 + 穿墙声波；阵亡黑洞 |
| `phantom.json` | 虚空之眼 | 幻翼 | 五阶段、九学派轮转 + 引导法术；挨打瞬移 |
| `husk.json` | 沙海祭司 | 尸壳 | 四阶段；五类触发器全用上 + 掉落规则 |

安装：把 `examples/bosses/` 整个复制到 `saves/<存档>/datapacks/spellmob_bosses/`（目录里应有 `pack.mcmeta` 与 `data/`），进游戏 `/reload`，日志出现 `Loaded N spell caster definition(s)` 即生效。

召唤示例：

```mcfunction
/summon minecraft:stray ~ ~ ~5 {CustomName:'{"text":"霜寂领主","color":"aqua"}',CustomNameVisible:1b,Tags:["spellmob_boss","frost"],PersistenceRequired:1b,Attributes:[{Name:"generic.max_health",Base:120.0}],Health:120.0f}
```

五个 Boss 的完整召唤命令见 `examples/bosses/README.md`。

> 名字与 tag 必须同时匹配：只改名、或只加 tag 都不会触发 Boss 配置。若在配置里把 `spellmob_boss` 加进 `[protection] protectedTags`，玩家就无法用命名牌覆盖 Boss 的名字。

## JSON 结构

```json
{
  "_comment": "注释字段会被忽略",
  "goals": [
    {
      "spell": "irons_spellbooks:fireball",
      "level": 2,
      "cooldown": 200,
      "priority": 1,
      "range": 20.0,
      "castTime": 30,
      "checkInterval": 10,
      "target": "ATTACK_TARGET",
      "conditions": { "healthBelow": 0.5 }
    }
  ],
  "onHurt": { "spell": "irons_spellbooks:heal", "level": 1, "cooldown": 400, "target": "SELF" },
  "onTarget": { "spell": "irons_spellbooks:slow", "level": 1, "cooldown": 150, "range": 25.0 },
  "onDeath": {
    "spell": "irons_spellbooks:ice_spikes",
    "level": 2,
    "range": 4,
    "target": { "type": "POSITION", "positionMode": "RANDOM_IN_RANGE", "lineOfSight": false }
  },
  "onKill": { "spell": "irons_spellbooks:heal", "level": 1, "cooldown": 200, "target": "SELF" }
}
```

* `goals`：周期性施法，数组，按顺序尝试；每条独立冷却；
* `onHurt`：被攻击时触发，单对象，可选；
* `onTarget`：锁定新目标时触发，单对象，可选；
* `onDeath`：自己死亡时触发，单对象，可选；施法者已经死亡，所以**强制瞬发**（忽略 `castTime`），也不检查冷却/黑名单；
* `onKill`：自己击杀生物后触发，单对象，可选；击杀目标已死，默认瞄准尸体的位置；
* 五者与 `variants` 都没有时，说明这个文件不是 SpellMob 的格式，会被静默忽略（别的模组也用 `spell_casters` 这个目录名）。

### 个体变体（variants）

同一物种可以让个别生物施不同的法术，甚至完全不施法。`variants` 是一个数组，按顺序匹配，**第一个命中的生效**：

```json
{
  "goals": [ { "spell": "irons_spellbooks:heal", "target": "SELF" } ],
  "variants": [
    {
      "select": { "name": "A" },
      "override": true,
      "goals": [
        { "spell": "irons_spellbooks:fireball", "level": 3, "range": 24, "castTime": 20 }
      ]
    }
  ]
}
```

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `select` | object | 必填 | 匹配条件，见下 |
| `override` | bool | `true` | `true` 只用变体自己的配置；`false` 表示把变体配置叠加在物种默认配置上（`goals` 拼接、`onHurt`/`onTarget` 变体优先） |
| `goals`/`onHurt`/`onTarget`/`onDeath`/`onKill` | | 无 | 与顶层同名字段完全一样；全都不写表示"这个变体什么都不放" |

`select` 支持五种条件，同时书写时要求**全部满足**：

| 字段 | 说明 |
|---|---|
| `name` | 自定义名精确匹配（需要命名牌） |
| `nameRegex` | 自定义名正则全匹配，如 `"^守卫-\\d+$"` |
| `tags` | scoreboard tag 列表，要求生物拥有全部 tag |
| `healthBelow` | 当前生命比例**低于**该值（`0`–`1`）时命中，用于血量阶段 |
| `healthAbove` | 当前生命比例**高于**该值（`0`–`1`）时命中 |

没有被任何变体命中的生物使用顶层默认配置；顶层也可以完全省略这五个字段，此时这些生物不施法。

### 阶段（用血量切换变体）

`select` 里的 `healthBelow` / `healthAbove` 可以按当前生命比例挑变体。变体按数组顺序匹配，所以把"低血量"那条放在前面，物种默认配置就自然成为最后一个阶段：

```json
{
  "variants": [
    {
      "select": { "name": "A", "healthBelow": 0.4 },
      "goals": [ { "spell": "irons_spellbooks:ice_spikes", "castTime": 20, "interruptOnDamage": true, "cooldownGroup": "a_phase" } ]
    },
    {
      "select": { "name": "A" },
      "goals": [ { "spell": "irons_spellbooks:fireball", "castTime": 20, "cooldownGroup": "a_phase" } ]
    }
  ]
}
```

* 血量变化每 tick 用一次 float 比较检测；跨过阈值后重建该生物的 Goal，如果此时正在吟唱会等这次吟唱结束再切换，不会打断自己；
* `cooldownGroup` 让两个阶段的法术共用一份冷却，避免"半血瞬间无缝换招"；
* 两个阈值同时写时要求 `healthBelow` 大于 `healthAbove`，否则加载阶段就会报错。

改名会在一秒内自动生效（每 tick 比较自定义名）；用 `/tag` 加标签、或直接改完数据包后，执行 `/spellmob resync` 立即重建。

### 掉落（dropRules）

只有**会施法**的生物才会结算 `dropRules`（当前解析出的施法配置非空），所以普通同类不会被顺手塞上掉落。规则按数组顺序匹配、**第一条命中的生效**，和 `variants` 一致；但两者互相独立：Boss 切阶段换法术时，掉落规则不受影响。

```json
"dropRules": [
  {
    "select": { "tags": ["spellmob_boss", "dune"] },
    "drops": [
      { "item": "minecraft:gold_block", "count": 1 },
      { "item": "minecraft:emerald", "min": 2, "max": 4, "looting": true },
      { "item": "minecraft:enchanted_golden_apple", "chance": 0.15 }
    ]
  },
  {
    "select": { "tags": ["spellmob_boss"] },
    "chance": 0.2,
    "drops": [ { "lootTable": "minecraft:chests/desert_pyramid" } ]
  }
]
```

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `select` | object | 必填 | 与变体相同的选择器：`name` / `nameRegex` / `tags` / `healthBelow` / `healthAbove` |
| `chance` | double | `1.0` | **整条规则**掷一次骰，没中就整条不掉 |
| `drops` | array | 必填 | 条目列表，至少一条可用 |
| `drops[].item` | string | 二选一 | 物品 id |
| `drops[].lootTable` | string | 二选一 | 原版战利品表 id，适合附魔、随机装备这类复杂池 |
| `drops[].count` | int | `1` | 固定数量，与 `min`/`max` 二选一 |
| `drops[].min` / `max` | int | `1` | 数量区间（含两端） |
| `drops[].chance` | double | `1.0` | 这一条独立掷一次骰 |
| `drops[].looting` | bool | `false` | 吃抢夺等级，每级多 0~1 个 |
| `drops[].requirePlayerKill` | bool | `true` | 只算玩家击杀，防刷怪塔；用 `/kill` 测试不会掉，用玩家攻击或 `/damage` |

* 规则和变体一样按顺序**第一条命中生效**，所以具体的（名字）写前面、通用的（tag）写后面；
* 掉落与阶段解耦：Boss 掉到半血切到第二阶段变体后，仍旧按同一条掉落规则结算；
* 数量超过单组上限会自动拆成多组；
* `lootTable` 以 `LootContextParamSets.ENTITY` 生成，带上了击杀者与抢夺等级。

### 施法对象字段

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `spell` | string | 必填 | 法术 id，如 `irons_spellbooks:fireball` |
| `level` | int | `1` | 法术等级，1–10，超过法术上限会自动降到上限 |
| `cooldown` | int | `-1` | 冷却（tick）。`-1` 表示使用 ISS 该法术的默认冷却（秒 × 20）；实际生效值最小 1 tick |
| `priority` | int | `1` | Goal 优先级，0–9，**数字越小越优先** |
| `range` | double | `16` | 施法距离（格），用于索敌与打断判定 |
| `castTime` | int | `-1` | 吟唱时间（tick）。`-1` 表示使用 ISS 计算值（`getEffectiveCastTime`） |
| `checkInterval` | int | `10` | `canUse()` 的检查间隔（tick），降低高频索敌开销 |
| `target` | string/object | `ATTACK_TARGET` | 目标选择，见下 |
| `conditions` | object | 无 | 额外条件，见下 |
| `interruptOnDamage` | bool | `false` | 为 `true` 时，吟唱期间受到任意伤害即打断（不返还冷却） |
| `cooldownGroup` | string | 无 | 共享冷却组名（`a-z0-9_.-`，1–32 字符）；同组法术共用一个冷却 |
| `chance` | double | `1.0` | 每次尝试真正施放的概率（`0`–`1`）；未掷中时按 `checkInterval` 再试 |
| `repeatOnCast` | bool | `true` | 仅引导型（`CONTINUOUS`）法术：是否每 10 tick 重跑一次 `onCast`。个别"每次结算都会重新生成实体"的法术可设为 `false` |

### target

字符串简写等价于 `{ "type": "<类型>", "lineOfSight": true }`。

对象形式：

```json
{
  "type": "POSITION",
  "lineOfSight": false,
  "positionMode": "RANDOM_IN_RANGE",
  "relativeOffset": [0, 1, -2],
  "maxHealthPercent": 0.5,
  "sort": "LOWEST_HEALTH"
}
```

| 字段 | 说明 |
|---|---|
| `type` | 目标类型，见下表 |
| `lineOfSight` | 是否需要视线，默认 `true` |
| `positionMode` | 仅 `POSITION` 使用，见下表 |
| `relativeOffset` | 仅 `RELATIVE` 使用，相对施法者的偏移（格） |
| `filters` | 目标过滤，字段见下；顶层的 `maxHealthPercent` 仍然可用，等价于 `filters.maxHealthPercent` |
| `sort` | 多个候选时如何排序：`NEAREST`（默认）、`FARTHEST`、`LOWEST_HEALTH`、`HIGHEST_HEALTH`、`RANDOM`；只影响"多选一"的类型（`NEAREST_*`、`THREATENING_ENTITY`），平局按距离 |
#### 目标过滤（filters）

```json
"target": {
  "type": "NEAREST_HOSTILE",
  "filters": {
    "entityTypes": ["minecraft:skeleton", "minecraft:stray"],
    "entityTags": ["minecraft:raiders"],
    "excludeTags": ["spellmob_immune"],
    "excludeCasterTeam": true,
    "minHealthPercent": 0.3,
    "maxHealthPercent": 0.9,
    "requireNoShield": true
  }
}
```

| 字段 | 默认 | 说明 |
|---|---|---|
| `entityTypes` | 空 | 只接受这些实体 id |
| `entityTags` | 空 | 只接受带其中任意一个实体类型标签的目标 |
| `excludeTags` | 空 | 带这些计分板标签的目标直接排除 |
| `excludeCasterTeam` | `false` | 排除与施法者同队的目标 |
| `ignoreCreative` | `true` | 忽略创造/旁观玩家 |
| `minHealthPercent` / `maxHealthPercent` | `0.0` / `1.0` | 只接受生命比例落在这个区间的目标 |
| `requireNoShield` | `false` | 只接受没有格挡的目标 |

过滤在候选实体解析出来之后、起手之前判定；`POSITION` 类目标不看过滤。

### 目标类型（15 种）

| 类型 | 含义 |
|---|---|
| `ATTACK_TARGET` | 当前攻击目标 |
| `HOSTILE_TARGET` | 同 `ATTACK_TARGET`（兼容 1.x 数据包） |
| `LAST_DAMAGE_SOURCE` | 最后伤害自己的实体 |
| `THREATENING_ENTITY` | 最近一个把自己当作攻击目标的生物 |
| `NEAREST_HOSTILE` | 最近的敌对目标：玩家（排除创造/旁观）或怪物，且非同队、非同类 |
| `NEAREST_UNDEAD` | 最近的亡灵（`MobType.UNDEAD`，含僵尸/骷髅/幻翼等） |
| `NEAREST_ARTHROPOD` | 最近的节肢动物（`MobType.ARTHROPOD`，含蜘蛛/蠹虫等） |
| `NEAREST_ANIMAL` | 最近的动物（`Animal`） |
| `NEAREST_MONSTER` | 最近的怪物（`Enemy`） |
| `NEAREST_PLAYER` | 最近的玩家（排除创造/旁观） |
| `OWNER` | 驯服生物的主人 |
| `NEAREST_ALLY` | 最近的同类型或同队生物 |
| `SELF` | 施法者自己 |
| `RANDOM_HOSTILE` | 范围内随机一个敌对目标 |
| `POSITION` | 不锁定实体，瞄准一个坐标 |

### 位置模式（5 种，仅 `POSITION`）

| 模式 | 含义 |
|---|---|
| `RELATIVE` | 施法者位置 + `relativeOffset` |
| `EYES_LOOK` | 沿视线方向，取 `range` 处 |
| `FEET_LOOK` | 沿脚部朝向，取 `range` 处 |
| `TARGET_ENTITY` | 当前目标脚下（没有目标时本次施法不会开始） |
| `RANDOM_IN_RANGE` | 以施法者为中心、`range` 为半径的圆内随机一点 |

### conditions

| 字段 | 默认 | 说明 |
|---|---|---|
| `healthBelow` | `1.0` | 自身生命比例**低于**该值才触发 |
| `healthAbove` | `0.0` | 自身生命比例**高于**该值才触发 |
| `minTargetDistance` | `0` | 目标与自己的最小距离（格） |
| `maxTargetDistance` | `∞` | 目标与自己的最大距离（格） |

也支持按来源分组嵌套，扁平写法仍然有效（同名键以扁平为准）：

```json
"conditions": {
  "self":   { "healthBelow": 0.5, "healthAbove": 0.1 },
  "target": { "minTargetDistance": 4.0, "maxTargetDistance": 16.0 }
}
```

## 施法流程

1. `CastSpellGoal.canUse()` 解析目标并检查冷却（带 `checkInterval` 缓存）；冷却通过后按 `chance` 掷骰；
2. 施法者转向目标（`POSITION` 则转向坐标）；
3. `spell.checkPreCastConditions()`，失败则放弃、不进冷却，并在 20 tick 内不再重试；
4. `MagicData.initiateCast(..., CastSource.MOB, MAINHAND)` 进入吟唱，播放起手音效；
5. 每 tick 调 `onServerCastTick()`；期间占用 `MOVE` + `LOOK`，生物会停下并持续面向目标；
6. 引导型法术（ISS 的 `CastType.CONTINUOUS`，例如冰锥、电刑、虹吸射线）额外每 10 tick 重跑一次 `onCast()`，与玩家按住施法的节奏一致；个别"每次结算都会重新生成实体"的法术可以设 `repeatOnCast: false`；
7. 完成时调 `onCast()`（引导型若最后一拍已经跑过则跳过）+ `onServerCastComplete()`，播放结束音效，清空吟唱状态；引导型的冷却从这一刻才开始计。

打断条件（任一满足即取消，不返还冷却）：施法者死亡/卸载、目标死亡或离开 `range + 4`、需要视线但丢失视线、`spell.shouldAIStopCasting()` 返回 true、更高优先级的 Goal 抢占、法术配了 `interruptOnDamage` 时受到任意伤害；引导型法术（`CONTINUOUS`）则统一看 `balance.interruptChannelsOnDamage`，带 `protection.channelImmuneTags` 标签的生物不会被伤害打断。

`onHurt` / `onTarget` / `onKill` 与 `goals` 共用同一个施法状态机：如果生物已经在吟唱，事件触发会被忽略（不会打断当前施法）。

`onDeath` 是唯一会绕过就绪检查的触发：施法者已经死亡、下一 tick 不再 tick，所以它总是瞬发，不检查冷却与概率；但它仍然检查 `conditions` 与实体/维度黑名单。如果法术本身要求活着的施法者，效果由 Iron's Spellbooks 决定。

## 表现（粒子）

施法反馈全部由服务端发包，客户端不需要安装本模组：

* 吟唱开始时脚下出现一圈尘粒子法阵，颜色取自法术所属学派的 `SchoolType#getTargetingColor()`（读不到颜色时用纯黑兜底；纯黑只可能来自兜底，正常学派色不会黑到被判为不可用）；
* 每 3 tick 刷新一次，环的半径随进度先扩后收、并持续旋转，末尾还会冒出上升的光点 —— 不看 UI 也能判断读条到哪了；
* Boss 施法者脚下额外画一圈更大的同色外环，并在吟唱期间每 20 tick 向外扩散一圈脉冲尘环；外环点数由 `presentation.bossPoints` 控制，与普通圆环的 `points` 分开配置。
* 法阵大小按生物碰撞箱宽度缩放，大体型生物不会"踩在圈外"；
* 被打断时（目标丢失、施法者死亡、法术回调抛异常）冒一团烟并播放熄灭音效；因卸载、重载或改名而中止的施法不会触发，避免在区块边界来回走时刷屏。

代价是每个环点一次发包：原版没有"环形"粒子，一次 `sendParticles` 只会在方框里随机撒点，看起来是一团方雾而不是圆。想省带宽可以把 `presentation.points` 调成 0 关掉法阵，或把 `presentation.distance` 调小。

## 配置

`config/spellmob-common.toml`（全局配置，所有存档共用一份；服务器在服务端根目录的 `config/`）：

| 键 | 默认 | 说明 |
|---|---|---|
| `general.enabled` | `true` | 模组总开关 |
| `general.debugLogging` | `false` | 调试日志 |
| `balance.cooldownMultiplier` | `1.0` | 全局冷却倍率 |
| `balance.maxRange` | `32.0` | 最大施法距离上限，JSON 的 `range` 会被截断 |
| `balance.interruptChannelsOnDamage` | `true` | 引导型法术是否被伤害打断（普通吟唱仍由每条法术的 `interruptOnDamage` 控制） |
| `filters.entityBlacklist` | `[]` | 不参与施法的实体 id 列表 |
| `filters.dimensionBlacklist` | `[]` | 不生效的维度 id 列表 |
| `protection.protectedTags` | `[]` | 名字受保护的 scoreboard tag：普通玩家不能覆盖这些生物已有的名字 |
| `protection.allowOpsRename` | `true` | 是否允许权限等级 ≥2 的玩家改名受保护生物 |
| `protection.channelImmuneTags` | `["spellmob_boss"]` | 带这些标签的生物，引导不会因受伤中断 |
| `presentation.enabled` | `true` | 是否显示法阵与打断反馈 |
| `presentation.points` | `24` | 普通圆环的点数，0 关闭；范围 0–128，小于 6 基本看不见，常用 8–48 |
| `presentation.bossPoints` | `32` | Boss 圆环的点数，0 关闭 Boss 圆环；范围 0–128 |
| `presentation.ringScale` | `1.0` | 法阵半径倍率，范围 0.25–4.0 |
| `presentation.distance` | `24.0` | 只向这个距离内的玩家发粒子 |
| `presentation.interruptFeedback` | `true` | 被打断时是否冒烟 + 熄灭音效 |
| `presentation.bossTags` | `["spellmob_boss"]` | 带这些标签的施法者使用 Boss 特效：更大的脚下外环 + 脉冲环 |
| `presentation.bossExtras` | `true` | Boss 特效是否加上周期性脉冲环 |
| `species.extraRules` | `[]` | 写在配置里的物种级规则，一行一条 JSON |

### 物种规则写进配置（extraRules）

不想给每个物种单独放一个 JSON 文件时，可以把物种级规则直接写进 `species.extraRules`，一行一条：

```toml
[species]
extraRules = [
  '{"entity":"minecraft:cow","goals":[{"spell":"irons_spellbooks:fireball","level":1,"cooldown":200,"range":16,"target":"ATTACK_TARGET"}]}',
  '{"entity":"minecraft:pig","onHurt":{"spell":"irons_spellbooks:heal","level":1,"cooldown":400,"target":"SELF"}}'
]
```

* `entity` 必填，其余字段与数据包 JSON 完全一致（不支持 `variants`，个体定向仍然只走数据包）；
* 单条解析失败只会丢弃该条并打一行警告，不影响其它条目；
* **优先级**：同一物种只要数据包里有文件，就以数据包为准，配置里的同物种条目被忽略，并在日志里打一行 `Config rule for <id> ignored: the data pack already defines it`；
* 改完配置后重启游戏/服务器生效（NeoForge 不会随 `/reload` 重读配置）；若 GUI 或其它工具触发了配置热重载，本模组会自动重新应用并重建 Goal；
* 用 Configured 打开这份配置时，`extraRules` 就是一个字符串列表，可以逐条增删改。

### 名字锁

把 `protection.protectedTags` 里配的 tag 打到生物身上，它的名字就被保护了：

```text
/tag @e[type=zombie,limit=1,sort=nearest] add sm_boss
```

之后普通玩家对该生物使用命名牌不会生效（已有名字不会被覆盖，命名牌不消耗），并收到一条 actionbar 提示；`protection.allowOpsRename = true`（默认）时权限等级 ≥2 的玩家仍可改名。没有名字的生物仍可被命名一次，保护的是"覆盖"。

它只拦"玩家手动用命名牌"这一条路径；命令、NBT 编辑器，以及绕过玩家交互的自动化模组不受限制。所以真正决定"谁能施法"的始终是 `select` 里的 tag 条件，名字锁只负责保护显示名。

## 命令

| 命令 | 说明 |
|---|---|
| `/spellmob list` | 列出已加载的实体类型与变体数量 |
| `/spellmob info <目标>` | 显示该生物最终生效的配置、命中的变体、规则来源、剩余冷却与当前吟唱 |
| `/spellmob resync [目标]` | 重建目标（或执行者自己）的施法 Goal |
| `/spellmob validate` | 用当前 ISS 法术表复查所有已加载配置，列出缺失/被禁用的法术与被数据包遮蔽的配置项 |

需要权限等级 2（管理员）。

## 与 1.x 的差异

* 重写为单一施法状态机（capability 持有），删除原来"Goal + 延迟队列"两套并行逻辑；
* `priority` 真正生效，施法期间占用 `MOVE` + `LOOK`，不再被原版近战 AI 饿死；
* `POSITION` 目标不再把施法者自己当目标（修复 1.x 瞄准自己、传送方向恒定的 bug）；
* 冷却、施法状态、打断改为按生物实例持久化（NBT），重载/重进存档后仍正确；
* `/reload` 会重建已加载生物的 Goal，不再残留旧配置；
* 目标判定改用 `MobType` / `Animal` / `Enemy` / 队伍关系，不再用硬编码名字表；
* 新增 `onDeath` / `onKill` 触发器和 `sort` 目标排序（`NEAREST` / `FARTHEST` / 血量 / 随机）；
* 新增血量阶段（`select.healthBelow` / `healthAbove`）、概率施法（`chance`）、共享冷却组（`cooldownGroup`）与吟唱打断（`interruptOnDamage`）；
* 引导型（`CONTINUOUS`）法术按 ISS 的 10 tick 节奏重放 `onCast`，冷却在引导结束后才开始计；`repeatOnCast` 可对个别法术关掉重放；
* 新增施法表现：学派配色的旋转法阵随读条变化，被打断时有烟与音效（纯服务端，客户端无需安装）；
* 法术经 ISS 官方施法管线（`MagicData` + `onServerPreCast` + `onServerCastTick` + `onServerCastComplete`），不再直接调 `onCast(..., null, null)`。

## 调试

* 打开 `debugLogging` 后，`logs/latest.log` 会打印每条配置的解析结果、施法开始/完成/打断原因；
* 配置写错时会在加载阶段打印 `[SpellMob] <文件>: <原因>`，并只丢弃出错的单条法术，不会让整个文件失效；
* 修改数据包后执行 `/reload` 即可，已加载的生物会立即获得新配置。

## 构建

```bash
JAVA_HOME=/path/to/jdk-17 ./gradlew build
```

产物：`build/libs/spellmob-2.5.0.jar`。
