# SpellMob

**让任何生物成为 Iron's Spells 'n Spellbooks 施法者。**

纯服务端 · 数据包驱动 · 不新增方块、物品或实体

![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1%20%7C%201.21.1-3C8527?style=flat-square)
![Forge](https://img.shields.io/badge/Forge-47.2%2B-E04E14?style=flat-square)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1%2B-FF9F00?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)

> **English:** SpellMob is a server-side, data-driven spellcasting framework for Iron's Spells 'n Spellbooks. Any mob can cast configured spells — no client mod required. See the [English guide](docs/guide.en.md).

SpellMob 是 [Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks)（下称 ISS）的附加模组。它不重新实现法术，而是把 ISS 自己的施法管线接到任意生物身上：你在数据包里写下“这个生物会什么法术、什么时候放、打谁”，它就会像 ISS 自带的施法怪一样读条、索敌、引导和开火。

**你不需要写 Java，也不需要安装客户端模组。** 一个 JSON 文件就能让僵尸变成火法师、让幻翼变成雷暴 Boss，或者只让某一只被命名的 Boss 施法。

---

## 目录

- [特性](#特性)
- [版本与前置](#版本与前置)
- [安装](#安装)
- [快速开始](#快速开始)
- [数据包格式](#数据包格式)
- [触发器](#触发器)
- [目标与条件](#目标与条件)
- [变体与阶段](#变体与阶段)
- [掉落规则](#掉落规则)
- [内置内容](#内置内容)
- [配置](#配置)
- [命令](#命令)
- [施法表现](#施法表现)
- [常见问题](#常见问题)
- [从源码构建](#从源码构建)
- [贡献与许可](#贡献与许可)

## 特性

- **纯数据包驱动**：不注册新实体、方块或物品，只解析 `data/<namespace>/spell_casters/<entity_id>.json`。
- **五种触发方式**：周期性施法（`goals`）、受伤时（`onHurt`）、锁定目标时（`onTarget`）、死亡时（`onDeath`）、击杀后（`onKill`）。
- **完整的目标系统**：15 个目标类型、6 种瞄准模式（含 `BEHIND_TARGET` 与落点检查）、视线判定、距离限制、目标排序，以及实体 id / 实体标签 / 计分板标签 / 队伍 / 血量 / 格挡等过滤器。
- **阶段与个体定向**：`variants` 可以按名字、正则、tag、血量、是否驯服挑出个别生物；同一物种的不同个体可以放不同法术，甚至完全不施法。
- **可用的施法节奏**：`cooldown`、`cooldownGroup`、`chance`、`checkInterval`、`castTime`、`interruptOnDamage`、`repeatOnCast`，以及真正生效的 Goal `priority`。
- **特殊怪掉落**：`dropRules` 支持固定物品、数量区间、概率、抢夺加成、玩家击杀保护和整张战利品表。
- **服务端表现**：学派配色的旋转法阵、读条呼吸、Boss 外环与冲击波、打断烟雾；全部由服务端发包，客户端不需要 SpellMob。
- **全局配置**：总开关、冷却倍率、最大射程、实体/维度黑名单、名字锁、引导免疫、粒子强度，以及可直接写进配置的物种规则。
- **兼容任意生物**：原版和模组生物都可以用；模组生物使用它自己的命名空间。
- **不重复造法术**：法术、音效、粒子与施法判定都来自 ISS；SpellMob 只负责“谁、何时、对谁施法”。

## 版本与前置

SpellMob 同时维护 Forge 1.20.1 与 NeoForge 1.21.1 两个发行版，数据格式一致；差异只在加载器和少量原版 API。

| 发行版 | Minecraft | 加载器 | Iron's Spellbooks | 维护分支 |
|---|---|---|---|---|
| Forge 版 | 1.20.1 | Forge 47.2+ | `1.20.1-3.4.0.9` 或更高 | `master` |
| NeoForge 版 | 1.21.1 | NeoForge 21.1+ | `1.21.1-3.16.3` 或更高 | `neoforge-1.21.1` |

- **SpellMob 本身是纯服务端逻辑。** 专用服务器只需要在服务端安装 SpellMob；客户端安装 ISS 即可，不需要安装 SpellMob。
- **ISS 必须同时安装在客户端和服务端。** 法术、音效和粒子资源都来自 ISS；只装 SpellMob 不会凭空多出法术。
- **Java 版本**：Forge 1.20.1 使用 Java 17；NeoForge 1.21.1 使用 Java 21。
- 上表是源码维护分支；实际可下载的版本以 Releases 页面为准，版本号可在 `gradle.properties` 的 `mod_version` 查看。

## 安装

1. 安装对应版本的 Minecraft、Forge/NeoForge 与 Iron's Spells 'n Spellbooks。
2. 把 SpellMob 的 jar 放进 `mods/` 目录。
3. 启动一次游戏或服务器，生成 `config/spellmob-common.toml`。
4. 准备数据包（见下一节），或直接使用 jar 内置的召唤 Boss。
5. 进入世界执行 `/reload`，让数据包生效。

> 单人游戏需要把 SpellMob 放进客户端 `mods/`（它运行在内置服务器上）。多人服务器只需要服务端安装 SpellMob，客户端不需要；但客户端仍然需要 ISS。

## 快速开始

下面做一个最小数据包：只让带有 `spellmob_demo` 标签的僵尸放火球，野生僵尸保持原样。

### 1. 数据包目录

```
my_spellmob_pack/
├── pack.mcmeta
└── data/
    └── minecraft/
        └── spell_casters/
            └── zombie.json
```

`pack.mcmeta`（1.20.1 使用 `pack_format: 15`；1.21.1 使用对应版本值，当前示例为 `48`）：

```json
{
  "pack": {
    "description": "My SpellMob pack",
    "pack_format": 15
  }
}
```

> 文件路径就是实体 id：`data/minecraft/spell_casters/zombie.json` 对应 `minecraft:zombie`；模组生物使用它自己的命名空间，例如 `data/irons_spellbooks/spell_casters/necromancer.json`。

### 2. 规则文件

```json
{
  "_comment": "只有带 spellmob_demo 标签的僵尸会施法",
  "variants": [
    {
      "select": {
        "tags": ["spellmob_demo"]
      },
      "override": true,
      "goals": [
        {
          "spell": "irons_spellbooks:fireball",
          "level": 1,
          "cooldown": 100,
          "priority": 1,
          "range": 16.0,
          "castTime": 20,
          "target": "ATTACK_TARGET"
        }
      ]
    }
  ]
}
```

这个文件没有顶层默认配置，所以没有 `spellmob_demo` 标签的僵尸什么都不会放；它们仍使用原版 AI。

### 3. 生效与召唤

把数据包放进世界的 `datapacks/` 目录，然后：

```mcfunction
/reload
/summon minecraft:zombie ~ ~ ~5 {PersistenceRequired:1b,Tags:["spellmob_demo"]}
```

也可以先召唤再打标签：

```mcfunction
/tag @e[type=minecraft:zombie,sort=nearest,limit=1] add spellmob_demo
```

日志出现 `[SpellMob] Loaded 1 spell caster definition(s)` 即加载成功。用下面的命令检查这只僵尸最终拿到了什么配置：

```mcfunction
/spellmob info @e[type=minecraft:zombie,sort=nearest,limit=1]
```

> **改完数据包记得两步走：** `/reload` 只负责把 JSON 重新读进内存，**已经加载在世界里的生物不会自动换配置**；再执行一次 `/spellmob resync`（或让它们重新进入加载范围）才会应用新的 goal。打 tag、改名同理——改名和驯服会自动重同步，打 tag 不会。

## 数据包格式

一个 `spell_casters` 文件可以包含以下任意组合；如果**一个都没有**，文件会被忽略（避免误读其他模组放在同名目录里的文件）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `goals` | array | 周期性施法，每条是一个施法对象；按 Goal 优先级竞争 |
| `onHurt` | object | 受到伤害时触发 |
| `onTarget` | object | 获得新攻击目标时触发 |
| `onDeath` | object | 死亡时触发，强制瞬发 |
| `onKill` | object | 击杀生物后触发，走正常就绪检查 |
| `variants` | array | 个体/阶段规则，按顺序匹配，第一个命中的生效 |
| `dropRules` | array | 掉落规则；写在根节点对整族生效，写进 variant 只在该变体命中时生效 |

以 `_` 开头的键可以当注释使用；未知键会被忽略。

### 施法对象字段

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `spell` | string | 必填 | ISS 法术 id，例如 `irons_spellbooks:fireball`；必须已注册且未被 ISS 配置禁用 |
| `level` | int | `1` | 法术等级，1–10；超过该法术最大等级会被截到上限 |
| `cooldown` | int | `-1` | 冷却（tick）。`-1` 使用 ISS 的默认冷却（秒 × 20）；实际最小 1 tick |
| `priority` | int | `1` | Goal 优先级，0–9，数字越小越优先；**近战怪必须保持 1**，否则会被原版近战 Goal 抢走（见下） |
| `range` | double | `16.0` | 施法/索敌距离（格）；最终会被 `balance.maxRange`（默认 32）截断 |
| `castTime` | int | `-1` | 吟唱时间（tick）。`-1` 使用 ISS 计算值；`0` 表示瞬发 |
| `checkInterval` | int | `10` | 周期性 Goal 重新检查的间隔（tick），范围 1–200 |
| `target` | string/object | `ATTACK_TARGET` | 目标选择，见下 |
| `conditions` | object | 无 | 额外条件，见下 |
| `interruptOnDamage` | bool | `false` | 吟唱期间受到伤害是否打断（引导法术使用全局开关 + 标签免疫） |
| `cooldownGroup` | string | 无 | 共享冷却组，1–32 字符，只允许 `a-z0-9_.-`；同组法术共用冷却 |
| `chance` | double | `1.0` | 每次尝试真正施放的概率（0–1）；未命中时等下一次检查 |
| `repeatOnCast` | bool | `true` | 仅 `CONTINUOUS` 引导法术：是否每 10 tick 重跑一次 `onCast`；对于“每次结算都会重新生成实体”的法术建议设为 `false` |

#### 优先级与原版 AI 的抢占

施法 Goal 占用 `MOVE + LOOK`，按 `priority` 注册进原版 `goalSelector`；原版规则是“数字更小的 Goal 可以抢占正在运行的 Goal”，所以这个字段决定施法能不能压过近战/远程 AI：

| 生物 | 原版攻击 Goal | 优先级 |
|---|---|---|
| 僵尸、尸壳 | `ZombieAttackGoal` | 2 |
| 骷髅、流浪者 | `RangedBowAttackGoal` | 4 |
| 烈焰人 | `BlazeAttackGoal` | 4 |

- `1`（默认）能抢占上表所有攻击 Goal：有法术就停下施法，冷却/条件不满足时让位给近战，表现为“停下施法 → 继续追”。
- `2` 及以上会与僵尸近战同档；近战 Goal 是生物自己先注册的，同档先注册者胜，**结果就是只追人、永远不放法术**。
- `0` 一般不需要，只有当生物自带优先级 0–1 的移动 Goal（如 `PanicGoal`）时才有区别。
- 目标超出 `range` 同样会让位给近战；想远程开打就把 `range` 调大（上限 `balance.maxRange`，默认 32）。
- 近战 Goal 属于原版生物，本模组只能抢占、不能删除；目前没有“禁用近战”的开关。

> `level` 会在解析时按 ISS 给出的法术上限进行夹取；`range` 会被全局 `maxRange` 限制；`cooldown` 的最小有效值是 1 tick。

## 触发器

### `goals`：周期性施法

每条 `goals` 都会变成一个独立的 Goal。Goal 每 `checkInterval` tick 检查一次：先确认自身就绪（冷却、黑名单、总开关），再解析目标、检查 `conditions`、掷 `chance`，最后进入 ISS 的施法管线。

- `priority` 会直接用于 `GoalSelector`；施法期间占用 `MOVE` + `LOOK`，普通近战 Goal 不会把吟唱抢掉。
- 同一条法术可以写多次，用不同等级、目标或冷却组形成轮换。
- 多条 `goals` 同时可用时，由 Goal 优先级决定谁先开始；同一优先级下先加入的优先。

### `onHurt`：受伤时

受到伤害时触发，优先以攻击者为目标。它会走正常的就绪检查：如果生物正在吟唱、处于冷却、被黑名单禁用，或没有合法目标，就不会触发。

### `onTarget`：获得新目标时

生物设置新的攻击目标时触发，优先使用这个新目标。适合做“锁定瞬间减速/标记/瞬移”之类的反应。

### `onDeath`：死亡时

强制、瞬发的死亡法术：

- 不检查冷却、`chance` 和“是否活着”；
- 仍然检查 `enabled`、实体/维度黑名单和 `conditions`；
- 如果死亡时正在施法，会先用 `CASTER_DIED` 收尾当前施法，再释放死亡法术；
- 不读 `castTime`，避免死亡实体不再 tick 导致法术永远放不出来。

### `onKill`：击杀后

击杀一个生物后触发，走正常冷却和条件检查。被击杀目标已经死亡，实体目标会退化为瞄准尸体位置；`SELF` 类法术不受影响。

> `onHurt`、`onTarget`、`onKill` 与 `goals` 共用一个施法状态机：如果生物已经在吟唱，事件触发通常会被忽略，不会打断当前施法。例外是受伤打断：如果当前吟唱配置了 `interruptOnDamage`（或引导法术被 `balance.interruptChannelsOnDamage` 打断），会先取消当前施法，再允许 `onHurt` 起一个新法术。

## 目标与条件

### `target` 的两种写法

字符串简写：

```json
"target": "ATTACK_TARGET"
```

等价于 `{ "type": "ATTACK_TARGET", "lineOfSight": true }`。

对象写法：

```json
"target": {
  "type": "POSITION",
  "lineOfSight": false,
  "positionMode": "RANDOM_IN_RANGE",
  "relativeOffset": [0, 1, -2],
  "sort": "LOWEST_HEALTH",
  "filters": {
    "minHealthPercent": 0.3,
    "maxHealthPercent": 0.9
  }
}
```

| 字段 | 默认 | 说明 |
|---|---|---|
| `type` | `ATTACK_TARGET` | 目标类型，见下表 |
| `lineOfSight` | `true` | 实体目标是否需要视线；`POSITION` 忽略此项 |
| `positionMode` | `RELATIVE` | 仅 `POSITION` 使用，见“瞄准模式” |
| `relativeOffset` | `[0,0,0]` | 仅 `POSITION`；`RELATIVE` 用三个分量，`BEHIND_TARGET` 用 `x`（距离）与 `y`（垂直偏移） |
| `placement` | 空 | 仅 `POSITION` 的落点检查：`avoidFluid` / `avoidLava` |
| `sort` | `NEAREST` | 多候选时的排序：`NEAREST` / `FARTHEST` / `LOWEST_HEALTH` / `HIGHEST_HEALTH` / `RANDOM` |
| `filters` | 空 | 额外目标过滤，见下 |
| `minHealthPercent` / `maxHealthPercent` | 无 | 顶层写法，等价于 `filters` 里的同名字段 |

### 目标类型（15 种）

| 类型 | 含义 |
|---|---|
| `ATTACK_TARGET` | 当前攻击目标 |
| `HOSTILE_TARGET` | `ATTACK_TARGET` 的别名（兼容旧数据包） |
| `LAST_DAMAGE_SOURCE` | 最后一个伤害自己的实体 |
| `THREATENING_ENTITY` | 攻击过自己、或正以自己为目标的实体 |
| `NEAREST_HOSTILE` | 最近的敌对目标（玩家或怪物，排除同队/同类） |
| `NEAREST_UNDEAD` | 最近的亡灵生物 |
| `NEAREST_ARTHROPOD` | 最近的节肢生物 |
| `NEAREST_ANIMAL` | 最近的动物 |
| `NEAREST_MONSTER` | 最近的怪物（实现 `Enemy` 的生物） |
| `NEAREST_PLAYER` | 最近的玩家；默认排除创造/旁观 |
| `OWNER` | 驯服生物的主人 |
| `NEAREST_ALLY` | 最近的同类型或同队生物（不含玩家） |
| `SELF` | 施法者自己 |
| `RANDOM_HOSTILE` | 范围内随机一个敌对目标 |
| `POSITION` | 不锁定实体，瞄准一个坐标（见“瞄准模式”） |

> ⚠️ **`SELF` 表示“目标就是自己”。** “自己”永远找得到，所以只要冷却好了、法术可用，它就会放，跟有没有敌人完全无关。
> 想改成“只在战斗中才召唤/治疗”，把那一行的 `target` 换成 `ATTACK_TARGET`：

```json
"goals": [
  { "spell": "irons_spellbooks:heal", "cooldown": 400, "target": "SELF" },
  { "spell": "irons_spellbooks:summon_vex", "cooldown": 400, "target": "ATTACK_TARGET" }
]
```

### 目标过滤（`filters`）

所有过滤器按 AND 生效；列表为空表示不限制。

| 字段 | 默认 | 说明 |
|---|---|---|
| `entityTypes` | 空 | 只接受这些实体 id（任意一个匹配即可） |
| `entityTags` | 空 | 只接受带其中任意一个实体类型标签的目标 |
| `excludeTags` | 空 | 带这些计分板标签的目标直接排除 |
| `excludeCasterTeam` | `false` | 排除与施法者同队的目标 |
| `ignoreCreative` | `true` | 是否忽略创造模式玩家；旁观者始终排除 |
| `minHealthPercent` / `maxHealthPercent` | `0.0` / `1.0` | 只接受生命比例落在这个区间的目标 |
| `requireNoShield` | `false` | 只接受没有举盾格挡的目标 |

过滤发生在候选实体解析之后、真正起手之前；`POSITION` 类目标不看过滤器。

### 瞄准模式（仅 `POSITION`）

> **`POSITION` 只决定“朝哪看”，不改变法术的出生点。** ISS 的施法接口没有坐标参数，投射物一律从施法者身上生成、沿当前视线飞出；
> 这里算出来的点只会喂给 `lookAt`。所以纯投射物用 `POSITION` 只能改朝向（打不穿墙），
> 传送 / 岩浆炸弹 / 黑洞这类靠视线找落点的法术才真正受影响，自施法完全无效。
> 想追着目标打，请用 `ATTACK_TARGET` / `NEAREST_HOSTILE`——`POSITION + TARGET_ENTITY` 会丢掉视线检测与 `filters`。

| 模式 | 含义 |
|---|---|
| `RELATIVE` | 施法者脚下 + `relativeOffset` |
| `EYES_LOOK` | 眼睛位置 + 视线方向 × `range` |
| `FEET_LOOK` | 脚下位置 + 视线方向 × `range` |
| `TARGET_ENTITY` | 当前目标脚下；没有目标时本次施法不会开始 |
| `BEHIND_TARGET` | 目标背后：`relativeOffset.x` 是距离（默认 2 格），`relativeOffset.y` 是垂直偏移；适合传送类法术落到玩家身后 |
| `RANDOM_IN_RANGE` | 以施法者为中心、`range` 为半径的圆内随机一点（同一高度） |

`RANDOM_IN_RANGE` 与 `BEHIND_TARGET` 还可以加落点检查，避免瞄进水里或岩浆：

```json
"target": {
  "type": "POSITION",
  "positionMode": "RANDOM_IN_RANGE",
  "placement": { "avoidFluid": true, "avoidLava": true }
}
```

`avoidFluid` 拒绝任何液体，`avoidLava` 只拒绝岩浆；随机点多试 8 次，全失败就放弃这次施法，`BEHIND_TARGET` 则退回目标自身位置。

### `conditions`：额外条件

| 字段 | 默认 | 说明 |
|---|---|---|
| `healthBelow` | `1.0` | 施法者生命比例不高于该值才施放（≤） |
| `healthAbove` | `0.0` | 施法者生命比例不低于该值才施放（≥） |
| `minTargetDistance` | `0` | 与目标的距离下限（格） |
| `maxTargetDistance` | `∞` | 与目标的距离上限（格） |

也支持按来源分组；扁平写法仍然有效，同名键以扁平写法为准：

```json
"conditions": {
  "self": {
    "healthBelow": 0.5,
    "healthAbove": 0.1
  },
  "target": {
    "minTargetDistance": 4.0,
    "maxTargetDistance": 16.0
  }
}
```

#### 世界与施法者条件

`conditions.world` 看世界状态，`conditions.self` 看施法者自身状态。两组都可选，`onDeath` 这类强制施法也要过这两组判定。

| 字段 | 取值 | 说明 |
|---|---|---|
| `world.time` | `day` / `night` / `dawn` / `dusk`，或数组 | 世界时间；无天空的维度（下界、末地）永远不满足 |
| `world.weather` | `clear` / `rain` / `thunder`，或数组 | 天气 |
| `world.moonPhase` | `0`–`7`，或数组 | 月相，`0` 为满月，每天加 1 |
| `world.dimension` | 维度 id 或数组 | 支持模组维度，如 `twilightforest:twilight_forest` |
| `world.difficulty` | `peaceful` / `easy` / `normal` / `hard` | 世界难度 |
| `world.minDay` | 整数 | 世界天数下限 |
| `self.onFire` / `self.inWater` / `self.inLava` / `self.frozen` | `true` / `false` | `true` 表示必须处于该状态，`false` 表示必须不处于 |

```json
"conditions": {
  "world": {
    "time": ["night", "dusk"],
    "weather": ["thunder"],
    "dimension": ["minecraft:overworld", "twilightforest:twilight_forest"]
  },
  "self": { "onFire": false, "inWater": false }
}
```

## 变体与阶段

`variants` 让同一物种的个别生物拥有不同配置。数组按顺序匹配，**第一个命中的生效**：

```json
{
  "variants": [
    {
      "select": { "tags": ["husk_boss", "spellmob_boss"] },
      "override": true,
      "goals": [
        { "spell": "irons_spellbooks:fireball", "level": 2, "range": 24.0 }
      ]
    },
    {
      "select": { "name": "普通尸壳卫兵" },
      "override": false,
      "goals": [
        { "spell": "irons_spellbooks:haste", "level": 1, "target": "SELF" }
      ]
    }
  ]
}
```

| 字段 | 默认 | 说明 |
|---|---|---|
| `select` | 必填 | 匹配条件，至少配置一项 |
| `override` | `true` | `true` 只用变体自己的配置；`false` 与顶层默认配置合并：`goals` 拼接（变体在前），触发器只在变体未定义时回落到顶层 |
| `sticky` | `false` | `true` 时该变体一旦命中就锁定：血量被奶回阈值以上也不退回（阶段只往前走）；写在 `select` 里也认 |
| `bossBar` | 无 | `true`/`false` 强制这个变体显示或不显示 Boss 血条，优先于 `presentation.bossBar` 与 `bossBarTags`；不写则沿用配置。根节点也可写，作用于整族 |
| `goals` / `onHurt` / `onTarget` / `onDeath` / `onKill` | 无 | 与顶层同名字段一致；全都不写表示“这个变体什么都不放” |

`select` 支持以下条件，同时存在时要求全部满足：

| 字段 | 说明 |
|---|---|
| `name` | 自定义名的精确匹配（需要命名牌） |
| `nameRegex` | 自定义名的正则全匹配，例如 `"^守卫-\\d+$"` |
| `tags` | 计分板标签列表；生物必须拥有全部标签 |
| `healthBelow` | 当前生命比例**严格低于**该值（0–1） |
| `healthAbove` | 当前生命比例**严格高于**该值（0–1） |
| `tamed` | 是否被驯服；驯服状态变化会自动重同步 |

- 没有被任何变体命中的生物使用顶层默认配置；顶层也没有配置时，这只生物不施法。
- 改名、血量变化和驯服状态变化会自动重建 Goal；直接改 tag 或改数据包后，执行 `/spellmob resync` 立即生效。
- 用血量做阶段时，建议把“低血量阶段”放在前面，顶层默认配置自然成为最后一个阶段。
- 阶段切换时可以配合 `cooldownGroup` 让新旧阶段的法术共享冷却，避免“半血瞬间无缝换招”。
- 血量阶段默认双向（被治疗就退回上一阶段）。希望“进二阶段就回不去”时给变体加 `"sticky": true`；锁定状态写进存档，改名会重置。

## 掉落规则

`dropRules` 给会施法的生物追加掉落，也可以只写掉落、不写任何施法规则（“战利品变体”）。

规则可以写在**文件根节点**（对该实体的所有生物生效），也可以写在 **`variants` 的某个变体里**（只在该变体命中时生效，死亡时命中哪个变体就用哪个变体的规则）。两处、以及同一处的多条规则，**所有 `select` 命中的都会结算**，不再“第一条命中就忽略后面的”。

```json
{
  "dropRules": [
    {
      "select": { "tags": ["spellmob_boss", "dune"] },
      "chance": 1.0,
      "drops": [
        { "item": "minecraft:gold_block", "count": 1 },
        { "item": "minecraft:emerald", "min": 2, "max": 4, "looting": true },
        { "item": "minecraft:enchanted_golden_apple", "chance": 0.15 }
      ]
    },
    {
      "select": { "tags": ["spellmob_boss"] },
      "chance": 0.2,
      "drops": [
        { "lootTable": "minecraft:chests/desert_pyramid" }
      ]
    }
  ]
}
```

| 字段 | 默认 | 说明 |
|---|---|---|
| `select` | 必填 | 与 `variants.select` 相同的选择器 |
| `chance` | `1.0` | 整条规则掷一次骰，未命中则整条不掉 |
| `drops` | 必填 | 至少一条可用条目 |
| `drops[].item` | 二选一 | 物品 id |
| `drops[].lootTable` | 二选一 | 战利品表 id，适合附魔、随机装备等复杂池 |
| `drops[].count` | `1` | 固定数量，与 `min`/`max` 二选一 |
| `drops[].min` / `max` | `1` | 数量区间（含两端）；**字段名不是 `minCount`/`maxCount`** |
| `drops[].chance` | `1.0` | 该条目独立掷一次骰 |
| `drops[].looting` | `false` | 物品条目按抢夺等级追加 0~N 个；战利品表由原版掉落上下文处理 |
| `drops[].requirePlayerKill` | `true` | 只算玩家击杀；`/kill` 等方式不会掉落，适合防刷怪塔 |

注意事项：

- 多条规则可以共用同一个 `select`：它们会各自掷骰、掉落叠加，不再互相覆盖。
- 只解析出**空施法配置**也可以掉落：文件里只写 `dropRules`、不写 `goals` 是合法的，只是这只怪不施法。
- 掉落规则在生物死亡时匹配。此时生命比例为 0，因此**不建议在 `dropRules.select` 里使用 `healthBelow`/`healthAbove`**；掉落筛选请优先使用 `name`、`nameRegex`、`tags` 或 `tamed`。
- 数量字段是 `count` 或 `min` / `max`（**不是 `minCount`/`maxCount`**）。
- `lootTable` 条目使用 `LootContextParamSets.ENTITY`，会带上击杀者和抢夺上下文。
- 数量超过单组上限时会自动拆成多组。

## 内置内容

jar 内置了六份示例文件（共七套配置），全部采用“标签限定”的方式：只有带有对应标签的生物才会施法，野生同类完全不受影响。

| 实体 | 需要的标签 | 说明 |
|---|---|---|
| `minecraft:husk` | `husk_boss` + `spellmob_boss` | 沙海 Boss（12 种法术，Boss 表现，引导免打断） |
| `minecraft:husk` | `husk_elite` | 沙海精英（另一套 12 种法术） |
| `minecraft:drowned` | `spellmob_boss` + `spellmob_jack` | 雷系溺尸 Boss |
| `minecraft:phantom` | `spellmob_boss` + `spellmob_phantom` | 雷系幻翼 Boss |
| `twilightforest:naga` | `spellmob_boss` + `spellmob_naga` | 自然系娜迦（需要 Twilight Forest） |
| `twilightforest:lich` | `spellmob_boss` + `spellmob_lich` | 巫妖（需要 Twilight Forest） |
| `twilightforest:alpha_yeti` | `spellmob_boss` + `spellmob_yeti` | 冰系雪怪首领（需要 Twilight Forest） |

召唤示例：

```mcfunction
/summon minecraft:husk ~ ~ ~5 {CustomName:'"沙海祭司"',CustomNameVisible:1b,PersistenceRequired:1b,Tags:["husk_boss","spellmob_boss"]}
```

内置定义会随模组 jar 加载；如果数据包里存在同一路径的文件，以数据包为准（数据包优先级更高）。Twilight Forest 的数据在未安装该模组时不会引用到任何实体，保持惰性。

仓库的 `examples/` 还包含更多可直接安装的示例包：经典生物、Boss、引导法术测试、`kitchen_sink`、过滤器演示、驯服狼，以及 1.20.1/1.21.1 各自的双尸壳示例。`examples/full-reference.json` 是字段最全的参考文件，不是数据包。安装示例包时，把包含 `pack.mcmeta` 的整个目录放进 `datapacks/` 即可。

## 配置

配置文件：`config/spellmob-common.toml`（COMMON 配置，所有存档共用一份；服务器在服务端根目录的 `config/`）。

| 键 | 默认 | 说明 |
|---|---|---|
| `general.enabled` | `true` | 施法总开关 |
| `general.debugLogging` | `false` | 打印施法、打断与 Goal 同步的调试日志 |
| `balance.cooldownMultiplier` | `1.0` | 全局冷却倍率；`0` 会压到最短 1 tick |
| `balance.maxRange` | `32.0` | `range` 的硬上限（1–128） |
| `balance.interruptChannelsOnDamage` | `true` | 引导法术是否被伤害打断（普通吟唱仍由每条法术的 `interruptOnDamage` 控制） |
| `filters.entityBlacklist` | `[]` | 永不施法的实体 id |
| `filters.dimensionBlacklist` | `[]` | 禁用施法的维度 id |
| `protection.protectedTags` | `[]` | 名字受保护的计分板标签；普通玩家不能用命名牌覆盖已有名字 |
| `protection.allowOpsRename` | `true` | 是否允许权限等级 ≥2 的玩家改名受保护生物 |
| `protection.channelImmuneTags` | `["spellmob_boss"]` | 带这些标签的生物，引导不会因受伤中断 |
| `presentation.enabled` | `true` | 是否显示法阵与打断反馈 |
| `presentation.points` | `24` | 普通圆环点数，0 关闭；范围 0–128 |
| `presentation.bossPoints` | `32` | Boss 圆环点数，0 关闭；范围 0–128 |
| `presentation.ringScale` | `1.0` | 法阵半径倍率（0.25–4.0） |
| `presentation.distance` | `24.0` | 只向该距离内的玩家发送粒子（4–64） |
| `presentation.interruptFeedback` | `true` | 被打断时是否冒烟并播放熄灭音效 |
| `presentation.bossTags` | `["spellmob_boss"]` | 带这些标签的施法者使用 Boss 表现 |
| `presentation.bossExtras` | `true` | Boss 表现是否附加额外效果（周期性/收尾冲击波等） |
| `presentation.bossBar` | `false` | 是否给施法者显示原版 Boss 血条；与 `enabled` 独立，可只开血条不开法阵 |
| `presentation.bossBarColor` | `white` | 血条颜色：`pink`/`blue`/`red`/`green`/`yellow`/`purple`/`white` |
| `presentation.bossBarRange` | `48` | 该距离内的玩家能看到血条（8–128） |
| `presentation.bossBarTags` | `["spellmob_boss"]` | 带这些标签的生物显示血条；留空表示所有配置了规则的生物 |
| `species.extraRules` | `[]` | 直接写在配置里的物种级规则，一行一条 JSON |

> 修改配置后重启游戏/服务器（或使用会触发 Forge 配置重载的工具）。数据包修改后执行 `/reload`。

### 把物种规则写进配置（`extraRules`）

不想为每个物种单独放 JSON 文件时，可以在 `species.extraRules` 里一行写一条：

```toml
[species]
extraRules = [
  '{"entity":"minecraft:cow","goals":[{"spell":"irons_spellbooks:fireball","level":1,"cooldown":200,"range":16,"target":"ATTACK_TARGET"}]}',
  '{"entity":"minecraft:pig","onHurt":{"spell":"irons_spellbooks:heal","level":1,"cooldown":400,"target":"SELF"}}'
]
```

- `entity` 必填，其余字段与数据包里的施法对象一致；配置里**不支持 `variants`**，个体定向仍然只走数据包。
- 同一物种同时存在数据包文件和配置条目时，**数据包优先**，配置条目会被忽略并在日志里说明。
- 单条解析失败只丢弃该条，不影响其他条目。

### 名字锁

把 `protection.protectedTags` 里的标签打到生物身上，它的名字就会被保护：

```mcfunction
/tag @e[type=minecraft:zombie,limit=1,sort=nearest] add sm_boss
```

之后普通玩家对它使用命名牌不会覆盖已有名字，命名牌也不会被消耗；`protection.allowOpsRename = true`（默认）时权限等级 ≥2 的玩家仍然可以改名。没有名字的生物仍可被命名一次——“保护”的是覆盖行为。

名字锁只拦截玩家手动使用命名牌的路径；命令、NBT 编辑器和绕过玩家交互的自动化模组不受限制。真正决定“谁能施法”的始终是 `select` 条件，名字锁只负责保护显示名。

## 命令

所有命令需要权限等级 2（管理员）。

| 命令 | 说明 |
|---|---|
| `/spellmob list` | 列出已加载的实体类型、变体数量与规则来源 |
| `/spellmob info <目标>` | 显示该生物命中的变体、最终施法配置、剩余冷却与当前吟唱 |
| `/spellmob resync [目标]` | 重建目标（或执行者自己）的施法 Goal；改 tag 后使用 |
| `/spellmob validate` | 用当前 ISS 法术表复查已加载配置，列出缺失/被禁用的法术与被数据包遮蔽的配置项 |

> 加载阶段被丢弃的条目（例如拼错法术 id）只会出现在日志里，`/spellmob validate` 检查的是已经成功加载的定义。排错时两者一起看。

## 施法表现

所有表现都由服务端发包，并且只发送给 `presentation.distance` 内的玩家。

- **普通施法者**：脚下出现学派配色的旋转法阵，半径随读条进度呼吸；每 3 tick 刷新一次，并在读条后段冒出上升光点。法术颜色取自 ISS 的学派 `getTargetingColor()`。
- **Boss 施法者**：脚下额外一圈更大的同色外环，并周期性释放冲击波；读条结束时有更大的收尾冲击波。带 `presentation.bossTags`（默认 `spellmob_boss`）的生物自动获得 Boss 表现。
- **打断反馈**：目标丢失、施法者死亡或法术回调抛异常时，冒烟并播放熄灭音效；因卸载、数据包重载或改名导致的静默取消不会触发。
- **可关闭**：`presentation.enabled = false` 关闭全部粒子与打断反馈；`points = 0` 关闭普通圆环；`bossPoints = 0` 关闭 Boss 圆环；`bossExtras = false` 关闭 Boss 的额外冲击波效果。

性能提示：法阵由一个个独立的粒子点组成，每个点一次发包。Boss 数量多或玩家人数多时，可以降低 `points` / `bossPoints` / `distance`，或适当增大 `checkInterval`。关闭法阵不会影响施法逻辑。

## 常见问题

**为什么野生生物不施法？**
内置示例全部使用标签限定（例如 `spellmob_boss`）。给生物打上对应标签，或使用 `variants.select` 按名字/tag 匹配，才会命中配置。也可以用 `/spellmob info` 查看它命中了哪个变体。

**改了数据包但没生效？**
确认路径是 `data/<namespace>/spell_casters/<entity_id>.json`、`pack.mcmeta` 的 `pack_format` 与游戏版本匹配，然后执行 `/reload`，**再 `/spellmob resync`**（只 `/reload` 不会替换已加载生物的 goal）。用 `/spellmob list` 确认定义已加载；加载失败的具体原因看日志里的 `[SpellMob]` 行。

**法术等级为什么没生效？**
`level` 会被 Iron's Spellbooks 的 `getMaxLevel()` 夹取——ISS 侧没给这个法术配置更高等级时，写 `level: 5` 也会按 1 级放。调等级前先在 ISS 的配置/法术书里确认它支持到几级。

**两个数据包写了同一个实体，谁生效？**
后加载的、优先级更高的数据包覆盖前面的；同一个包内一个实体只能有一个 `spell_casters/<id>.json`。配置里的 `[species] extraRules` 优先级最低，被数据包覆盖时日志会说明，`/spellmob info` 也会显示来源。

**冷却写多少合适？**
单位是 **tick**（20 tick = 1 秒）。`cooldown: 100` 是 5 秒；`cooldown: 0` 会被压到最短 1 tick，等于无限连发，慎用。想让几个法术共享冷却就设同一个 `cooldownGroup`。

**为什么某个法术不放？**
先跑 `/spellmob validate`。常见原因是法术 id 拼错、ISS 版本不包含该法术、该法术在 ISS 配置里被禁用，或者 `level`/`range`/`conditions` 不满足。加载阶段被丢弃的条目只会出现在日志里。

**为什么掉落不触发？**
确认 `dropRules` 写在根节点或命中的 variant 里；`select` 在死亡时能匹配（血量比例为 0，不建议用血量条件）；如果用了默认的 `requirePlayerKill`，需要用玩家击杀而不是 `/kill`。

**客户端需要安装 SpellMob 吗？**
不需要。SpellMob 的逻辑和粒子发包都在服务端；客户端需要安装 Iron's Spellbooks，因为法术、音效和粒子资源来自 ISS。单人游戏需要把 SpellMob 放进客户端 `mods/`，因为它同时是内置服务端。

**支持 Fabric 吗？**
目前只提供 Forge 1.20.1 与 NeoForge 1.21.1 版本。

**会给模组生物用吗？**
可以。把文件放到对应模组的命名空间下，例如 `data/irons_spellbooks/spell_casters/necromancer.json` 对应 `irons_spellbooks:necromancer`，然后按该生物的实体 id 配置即可。

**Boss 用自定义标签可以吗？**
可以，但记得同时修改 `presentation.bossTags`（Boss 表现）和 `protection.channelImmuneTags`（引导免打断），否则只有其中一个生效。

**服务器装了，客户端没装，能进服吗？**
SpellMob 不注册网络通道，也不要求客户端安装；客户端只要满足 ISS 的依赖即可。不过客户端必须安装 ISS，否则法术资源缺失。

## 从源码构建

前置：JDK 17（1.20.1 Forge）或 JDK 21（1.21.1 NeoForge）。

```bash
# 1.20.1 / Forge
JAVA_HOME=/path/to/jdk-17 ./gradlew build

# 1.21.1 / NeoForge
JAVA_HOME=/path/to/jdk-21 ./gradlew build
```

产物：`build/libs/spellmob-<version>.jar`。

- 首次构建需要联网，从 `https://code.redspace.io/releases` 解析 Iron's Spellbooks 依赖。
- 开发运行配置：`./gradlew runClient`、`./gradlew runServer`、`./gradlew runGameTestServer`（以对应分支的 `build.gradle` 为准）。
- 源码结构：
  - `src/main/java/spellmob/capability`：每生物的施法状态、冷却与当前施法；
  - `src/main/java/spellmob/data`：JSON 解析、变体、目标、掉落与注册表；
  - `src/main/java/spellmob/events`：Forge/NeoForge 事件与命令；
  - `src/main/java/spellmob/goal`：周期施法 Goal 与 Goal 同步；
  - `src/main/java/spellmob/presentation`：服务端粒子表现；
  - `src/main/resources/data/...`：内置示例定义；
  - `examples/`：可直接安装的数据包示例；
  - `docs/`：中英文详细指南。

## 贡献与许可

- **问题反馈**：请在仓库的 Issues 页面提交，并附上游戏版本、加载器版本、ISS 版本、`/spellmob validate` 输出和相关日志。数据包问题请附最小复现包。
- **Pull Request**：保持数据驱动和纯服务端设计；新增字段请同步更新 README、`docs/` 与示例，并尽量补充解析器测试。
- **许可**：MIT，详见 [LICENSE](LICENSE)。
- **作者**：GAIFAN404。
- **致谢**：Iron's Spellbooks（Redspace）提供法术与施法管线；Minecraft Forge / NeoForge 提供加载器；Twilight Forest 提供内置示例的实体兼容目标。

> SpellMob 不是 Iron's Spellbooks 的官方附属模组。遇到法术本身的问题，请先确认在 ISS 正常使用时也存在，再反馈到对应项目。
