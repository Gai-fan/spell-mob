# SpellMob

**用 JSON 给任意生物配法术——不加内容、不用装客户端。**

SpellMob 是 *Iron's Spells 'n Spellbooks*（ISS）的附属模组，不添加任何方块、物品或实体：它通过数据包把 ISS 的法术绑定到原版或其它模组的生物身上。施法走 ISS 自己的 `CastSource.MOB` 管线，读条时长、施法音效和法术行为都与 ISS 本体一致。生物不消耗法力、也不受 ISS 自身冷却限制——冷却由数据包自己决定。

- 只装模组：世界不会有任何变化。
- 纯服务端：客户端不装也能进服；脚下的法阵是服务端发送的原版粒子。
- 五种触发器、15 种目标、个体变体、血量阶段、共享冷却组、特殊怪掉落。

## 环境要求

| | |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x |
| Iron's Spells 'n Spellbooks | `1.21.1-3.16.3` 或更新 |

SpellMob 本身没有其它依赖。如果使用 ISS 3.15+，请按 ISS 的要求装好它需要的前置（新版 NeoForge、GeckoLib、Curios、`irons_lib`）——那是 ISS 的要求，不是本模组的。

## 安装

**1. 装模组。** 把 `spellmob-<版本>.jar` 放进 `mods/`。

**2. 装数据包（可选）。** 放进世界的 `datapacks` 目录：

```
saves/<世界>/datapacks/我的法术/
├── pack.mcmeta
└── data/<实体命名空间>/spell_casters/<实体路径>.json
```

目录路径**必须镜像实体 id**，数据包自己叫什么名字无所谓：

| 生物 | 文件 |
|---|---|
| `minecraft:zombie` | `data/minecraft/spell_casters/zombie.json` |
| `twilightforest:lich` | `data/twilightforest/spell_casters/lich.json` |
| `minecraft:zombie_villager` | `data/minecraft/spell_casters/zombie_villager.json` |

**3. 重载。** 进游戏执行 `/reload`，日志里应出现：

```
[SpellMob] Loaded N spell caster definition(s)
```

## 快速开始

`data/minecraft/spell_casters/zombie.json`：

```json
{
  "goals": [
    {
      "spell": "irons_spellbooks:fireball",
      "level": 2,
      "cooldown": 200,
      "priority": 1,
      "range": 24,
      "castTime": 30,
      "target": "ATTACK_TARGET"
    }
  ]
}
```

所有僵尸现在每 200 tick 对当前攻击目标放一发二级火球，读条 30 tick。

> 未知的键会被静默忽略，所以键名拼错不会报错。用 `/spellmob validate` 和 `/spellmob info <生物>` 确认实际加载了什么。

## 施法字段

`goals`、`onHurt`、`onTarget`、`onDeath`、`onKill` 以及变体里的每个施法对象，字段完全一致。

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `spell` | 字符串 | — | **必填**，ISS 法术 id。 |
| `level` | 整数 | `1` | 自动夹到法术的最大等级。 |
| `cooldown` | 整数（tick） | 法术自带 | 20 tick = 1 秒；乘上配置倍率后最少 1 tick。 |
| `priority` | 整数 0–9 | `1` | 数字越小越先执行，同时决定与原版 AI 的抢占：施法目标占用 MOVE + LOOK，`1` 会抢占近战（优先级 2）。想让近战优先就调大。 |
| `range` | 数值 | `16` | 格，会被配置里的 `maxRange`（默认 32）截断。 |
| `castTime` | 整数（tick） | ISS 的值 | `0` = 瞬发，`> 0` = 读条或引导；写了就覆盖 ISS 的减读条属性。 |
| `checkInterval` | 整数（tick） | `10` | 每隔多久重新判断一次能否施法。 |
| `chance` | 数值 0–1 | `1.0` | 起手前掷骰；没中不消耗冷却。 |
| `cooldownGroup` | 字符串 | — | `[a-z0-9_.-]{1,32}`；同组法术共用一个冷却，适合做阶段切换。 |
| `interruptOnDamage` | 布尔 | `false` | 只对普通读条生效；引导法术看全局开关。 |
| `repeatOnCast` | 布尔 | `true` | 只对引导（CONTINUOUS）法术生效：每 10 tick 重放一次 `onCast`，与 ISS 自己的施法怪一致。 |
| `target` | 字符串或对象 | `ATTACK_TARGET` | 见[目标](#目标)。 |
| `conditions` | 对象 | — | `healthBelow`、`healthAbove`、`minTargetDistance`、`maxTargetDistance`。 |

### 条件

条件在起手前判定，比较的是**施法者**的血量与目标的距离。

| 键 | 默认 | 含义 |
|---|---|---|
| `healthBelow` | `1.0` | 施法者血量比例低于此值才放。 |
| `healthAbove` | `0.0` | 施法者血量比例高于此值才放。 |
| `minTargetDistance` | `0` | 目标比这更近就不放。 |
| `maxTargetDistance` | 不限 | 目标比这更远就不放。 |

也可以按来源分组嵌套，扁平写法仍然有效（同名键以扁平为准）：

```json
"conditions": {
  "self":   { "healthBelow": 0.5 },
  "target": { "minTargetDistance": 4.0 }
}
```

位置类施法（没有实体目标）会跳过距离判定。

## 触发器

| 键 | 触发时机 | 说明 |
|---|---|---|
| `goals` | 周期性 | 数组，每项是一个独立的施法目标。 |
| `onHurt` | 受到伤害 | 攻击者作为优先目标。 |
| `onTarget` | 锁定新目标 | 新目标作为优先目标。 |
| `onDeath` | 死亡 | 立刻施放：跳过冷却与存活检查。 |
| `onKill` | 击杀 | 走正常冷却与条件判定。 |

```json
{
  "goals": [ { "spell": "irons_spellbooks:fireball", "target": "ATTACK_TARGET" } ],
  "onHurt":  { "spell": "irons_spellbooks:heal", "cooldown": 400, "target": "SELF" },
  "onDeath": { "spell": "irons_spellbooks:ice_spikes", "level": 1, "range": 8,
               "target": { "type": "POSITION", "positionMode": "RANDOM_IN_RANGE" } },
  "onKill":  { "spell": "irons_spellbooks:heal", "target": "SELF" }
}
```

同一时刻只会有一个事件触发的施法：正在施法时，后续触发会被忽略而不是排队。

## 目标

`target` 可以写成字符串简写（`"ATTACK_TARGET"`），也可以写成对象：

```json
{
  "type": "NEAREST_HOSTILE",
  "sort": "FARTHEST",
  "lineOfSight": true,
  "maxHealthPercent": 0.8
}
```

### 目标类型

| 类型 | 选中谁 |
|---|---|
| `SELF` | 施法者自己。 |
| `ATTACK_TARGET` | 当前攻击目标。 |
| `HOSTILE_TARGET` | `ATTACK_TARGET` 的别名，兼容旧数据包。 |
| `LAST_DAMAGE_SOURCE` | 最后一个伤害来源。 |
| `THREATENING_ENTITY` | 攻击过施法者、或正以施法者为目标的实体。 |
| `NEAREST_HOSTILE` | 最近的玩家或怪物，且不是盟友、也不是同类。 |
| `NEAREST_UNDEAD` | 最近的亡灵生物。 |
| `NEAREST_ARTHROPOD` | 最近的节肢生物。 |
| `NEAREST_ANIMAL` | 最近的动物。 |
| `NEAREST_MONSTER` | 最近的怪物（实现 `Enemy` 的生物）。 |
| `NEAREST_PLAYER` | 最近的生存/冒险模式玩家。 |
| `OWNER` | 驯服生物的主人。 |
| `NEAREST_ALLY` | 最近的同类或同队生物。 |
| `RANDOM_HOSTILE` | 范围内随机一个敌对目标。 |
| `POSITION` | 世界中的一个点，见位置模式。 |

### 目标对象字段

| 键 | 默认 | 说明 |
|---|---|---|
| `type` | `ATTACK_TARGET` | 上表之一。 |
| `sort` | `NEAREST` | `NEAREST`、`FARTHEST`、`LOWEST_HEALTH`、`HIGHEST_HEALTH`、`RANDOM`。 |
| `lineOfSight` | `true` | 实体目标必须可见（`POSITION` 忽略）。 |
| `maxHealthPercent` | `1.0` | 只接受血量比例不高于此值的目标；等价于 `filters.maxHealthPercent`。 |
| `positionMode` | `RELATIVE` | 仅 `POSITION`，见下。 |
| `relativeOffset` | `[0,0,0]` | 仅 `POSITION` + `RELATIVE`。 |
| `filters` | 空 | 目标过滤，见下。 |
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

### 位置模式（仅 `POSITION`）

| 模式 | 落点 |
|---|---|
| `RELATIVE` | 施法者位置 + `relativeOffset`。 |
| `EYES_LOOK` | 眼睛位置 + 视线方向 × `range`。 |
| `FEET_LOOK` | 脚下位置 + 视线方向 × `range`。 |
| `TARGET_ENTITY` | 目标所在位置；没有目标就不施放。 |
| `RANDOM_IN_RANGE` | `range` 格内的随机一点。 |

## 变体

`variants` 让同一个实体文件描述不同个体、以及血量变化后的另一套规则。变体只在数据包里可用，配置的 `[species] extraRules` 不支持。

```json
{
  "goals": [
    { "spell": "irons_spellbooks:magic_missile", "cooldown": 120, "target": "ATTACK_TARGET" }
  ],
  "variants": [
    { "select": { "name": "A", "tags": ["boss_a"] },
      "goals": [ { "spell": "irons_spellbooks:fireball", "cooldown": 100, "range": 24 } ] },

    { "select": { "nameRegex": "^B-[0-9]+$" },
      "goals": [ { "spell": "irons_spellbooks:lightning_lance", "cooldown": 140 } ] },

    { "select": { "name": "C" }, "override": false,
      "goals": [ { "spell": "irons_spellbooks:haste", "target": "SELF" } ] },

    { "select": { "name": "D" }, "override": true },

    { "select": { "healthBelow": 0.4 },
      "goals": [ { "spell": "irons_spellbooks:ice_spikes", "cooldown": 90, "range": 20 } ] }
  ]
}
```

`select` 条件（写了几个就要同时满足）：

| 键 | 匹配方式 |
|---|---|
| `name` | 自定义名完全相等。 |
| `nameRegex` | 对自定义名做整串正则匹配。 |
| `tags` | 包含列出的全部计分板标签。 |
| `healthBelow` | 血量比例严格低于此值。 |
| `healthAbove` | 血量比例严格高于此值。 |
| `tamed` | 是否已驯服（`true` / `false`，只对可驯服生物有意义）。 |

规则：

- 变体**从上往下匹配，第一条命中就生效**。具体条件放前面，宽泛的血量阶段放最后。
- `override` 默认 `true`：变体完全替换顶层默认；写成 `false` 则变体的法术**追加**在默认之后（变体的 goals 在前，默认的 goal 在后）。
- `override: true` 又不写任何法术 = 这个个体什么都不放。
- `name` 与 `nameRegex` 要求生物有自定义名。

**改名**会在一个 tick 内自动重同步目标；**驯服或取消驯服**同样会自动重同步；**改标签不会**——改完要执行 `/spellmob resync`。

## 掉落

`dropRules` 让真正会施法的生物额外掉落。只有当前解析出非空施法配置的生物才会结算，普通生物不受影响。

```json
"dropRules": [
  {
    "select": { "tags": ["spellmob_boss"] },
    "chance": 1.0,
    "drops": [
      { "item": "minecraft:emerald", "minCount": 2, "maxCount": 4, "looting": true },
      { "lootTable": "minecraft:chests/desert_pyramid", "chance": 0.2 }
    ]
  }
]
```

- 只使用**第一条命中的规则**。
- 规则级 `chance` 掷一次，条目级 `chance` 逐条掷。
- 每个条目二选一：`item` 或 `lootTable`。
- 固定数量用 `count`，区间用 `minCount` / `maxCount`。
- `requirePlayerKill` 默认 `true`，刷怪塔与挤压击杀不会掉落。

## 配置

配置文件在 `config/spellmob-common.toml`（COMMON 类型——整份安装共用一个文件，不是每个世界一份）。

| 分节 | 键 | 默认 | 说明 |
|---|---|---|---|
| `general` | `enabled` | `true` | 总开关。 |
| | `debugLogging` | `false` | 打开 `[SpellMob]` 详细日志。 |
| | `castSounds` | `true` | 播放 ISS 的施法开始/结束音效。 |
| `balance` | `cooldownMultiplier` | `1.0` | 缩放数据包里的所有冷却；`0` 会压到最短 1 tick。 |
| | `maxRange` | `32` | 数据包 `range` 的硬上限（最高 128）。 |
| | `interruptChannelsOnDamage` | `true` | 受伤是否打断引导法术。 |
| `filters` | `entityBlacklist` | 空 | 永不施法的实体 id。 |
| | `dimensionBlacklist` | 空 | 禁用施法的维度。 |
| `protection` | `protectedTags` | 空 | 带这些标签的生物不能被命名牌改名。 |
| | `allowOpsRename` | `true` | OP（权限 2）可以绕过名字锁。 |
| | `channelImmuneTags` | `["spellmob_boss"]` | 带这些标签的生物引导不被打断。 |
| `presentation` | `enabled` | `true` | 是否绘制法阵。 |
| | `points` | `24` | 普通圆环的点数，`0` = 不画法阵；范围 0–128，小于 6 基本看不见，常用 8–48。 |
| | `bossPoints` | `32` | Boss 圆环的点数，`0` = 不画 Boss 圆环；范围 0–128。 |
| | `ringScale` | `1.0` | 法阵半径倍率，范围 0.25–4.0。 |
| | `distance` | `24` | 粒子可见距离（4–64）。 |
| | `interruptFeedback` | `true` | 被打断时的烟与"熄灭"音效。 |
| | `bossTags` | `["spellmob_boss"]` | 带这些标签的施法者使用 Boss 特效：更大的脚下外环 + 脉冲环。 |
| | `bossExtras` | `true` | Boss 特效是否加上周期性脉冲环。 |
| `species` | `extraRules` | 空 | 整个物种的规则，一行一个 JSON 对象；schema 与数据包文件相同，但没有 `variants`。 |

同一个实体同时被数据包和 `[species] extraRules` 定义时，**数据包优先**，配置里的那条会被忽略（并打一行日志）。

## 命令

需要权限等级 2。

| 命令 | 作用 |
|---|---|
| `/spellmob list` | 列出所有已加载的实体类型及其来源（`data pack` / `config`）。 |
| `/spellmob info <生物>` | 显示该生物实际生效的配置，包括命中的变体与剩余冷却。 |
| `/spellmob resync [目标]` | 对指定生物重新生成施法目标（不给参数就是自己）；改完标签后执行。 |
| `/spellmob validate` | 检查所有已加载的法术 id，并列出被数据包遮蔽的配置条目。 |

## 兼容性说明

- **纯服务端。** 客户端不需要装模组；法阵是服务端发送的普通粒子。
- **数据包路径必须镜像实体 id。** `data/mypack/` 下的文件永远影响不到 `minecraft:zombie`。
- **未知 JSON 键会被忽略。** 字段名拼错不会报错——用 `validate` 和 `info` 抓。
- **ISS 版本。** 以 3.16.3 的 API 编译并验证；jar 声明 `[1.21.1-3.16.3,)`。

## 示例

仓库的 `examples/` 下有可直接使用的包：

| 目录 | 内容 |
|---|---|
| `examples/classic/` | 最开始的五个示例：僵尸 A/B、骷髅、狼、村民、尸壳。 |
| `examples/bosses/` | 六个「名字 + 标签」双门控 Boss（虚空之眼、沙海祭司……）。 |
| `examples/channel_test/` | 会引导全部 CONTINUOUS 法术的掠夺者与卫道士。 |
| `examples/full-reference.json` | 字段参考——所有键集中在一个文件里。 |
