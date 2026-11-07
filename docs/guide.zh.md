# SpellMob

**用 JSON 给任意生物配法术——不加内容、不用装客户端。**

SpellMob 是 *Iron's Spells 'n Spellbooks*（ISS）的附属模组，不添加任何方块、物品或实体：它通过数据包把 ISS 的法术绑定到原版或其它模组的生物身上。施法走 ISS 自己的 `CastSource.MOB` 管线，读条时长、施法音效和法术行为都与 ISS 本体一致。生物不消耗法力、也不受 ISS 自身冷却限制——冷却由数据包自己决定。

- 只装模组：世界不会有任何变化。
- 纯服务端：客户端不装也能进服；脚下的法阵是服务端发送的原版粒子。
- 五种触发器、15 种目标、个体变体、血量阶段、共享冷却组、特殊怪掉落。

## 环境要求

| | |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.x |
| Iron's Spells 'n Spellbooks | `1.20.1-3.4.0.9` 或更新（已验证到 `3.16.3`） |

SpellMob 本身没有其它依赖。如果使用 ISS 3.15+，请按 ISS 的要求装好它需要的前置（新版 Forge、GeckoLib、Curios、`irons_lib`）——那是 ISS 的要求，不是本模组的。

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
| `priority` | 整数 0–9 | `1` | 数字越小越优先，同时决定能不能抢过原版 AI；见[优先级与原版 AI 的抢占](#优先级与原版-ai-的抢占)。 |
| `range` | 数值 | `16` | 格，会被配置里的 `maxRange`（默认 32）截断。 |
| `castTime` | 整数（tick） | ISS 的值 | `0` = 瞬发，`> 0` = 读条或引导；写了就覆盖 ISS 的减读条属性。 |
| `checkInterval` | 整数（tick） | `10` | 每隔多久重新判断一次能否施法。 |
| `chance` | 数值 0–1 | `1.0` | 起手前掷骰；没中不消耗冷却。 |
| `cooldownGroup` | 字符串 | — | `[a-z0-9_.-]{1,32}`；同组法术共用一个冷却，适合做阶段切换。 |
| `interruptOnDamage` | 布尔 | `false` | 只对普通读条生效；引导法术看全局开关。 |
| `repeatOnCast` | 布尔 | `true` | 只对引导（CONTINUOUS）法术生效：每 10 tick 重放一次 `onCast`，与 ISS 自己的施法怪一致。 |
| `target` | 字符串或对象 | `ATTACK_TARGET` | 见[目标](#目标)。 |
| `conditions` | 对象 | — | 血量、目标距离，以及 `world` / `self` 两组世界与施法者条件。 |

### 优先级与原版 AI 的抢占

施法 Goal 占用 `MOVE + LOOK`，按 `priority` 注册进原版 `goalSelector`。原版 `GoalSelector` 的规则是“数字更小的 Goal 可以抢占正在运行的 Goal”，所以这个字段不只是我们内部的排序，也决定施法能不能压过近战、远程 AI：

| 生物 | 原版攻击 Goal | 优先级 |
|---|---|---|
| 僵尸、尸壳 | `ZombieAttackGoal` | 2 |
| 骷髅、流浪者 | `RangedBowAttackGoal` | 4 |
| 烈焰人 | `BlazeAttackGoal` | 4 |

- `priority: 1`（默认）：有法术可放时抢占近战，施法期间不再追人；冷却中或条件不满足时 `canUse()` 失败，让位给近战，所以看起来是“停下施法 → 继续追”的循环，这是正常的。
- `priority: 2` 及以上：与僵尸近战同档。近战 Goal 由生物自己在 `registerGoals()` 里先注册，同档先注册者胜，结果是**永远轮不到施法，只会追人**。给近战生物写规则时保持 `1`。
- `priority: 0`：一般不需要；只有当这只生物自带优先级 0 或 1 的移动 Goal（部分生物的 `PanicGoal`、`FloatGoal` 在 0–1）时才用得上。
- 距离超过 `range` 时同样 `canUse()` 失败、让位给近战。想让施法者站远处就开打，把 `range` 调大（会被配置 `maxRange` 截断，默认 32）。

近战 Goal 是原版注册的，本模组只能抢占、不能删除。想让生物完全放弃近战，目前只能用 `teleport` 类法术或更大的 `range` 拉开距离。

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

### 世界与施法者条件

`conditions.world` 看世界状态，`conditions.self` 看施法者自身的状态（和 `healthBelow` 写在同一个对象里）。两组都可选，写了才判定；`onDeath` 这类强制施法同样要过这两组条件。

| 键 | 取值 | 含义 |
|---|---|---|
| `world.time` | `"day"` / `"night"` / `"dawn"` / `"dusk"`，或数组 | 世界时间。下界、末地这类没有天空的维度永远不满足时间条件，请改用 `world.dimension`。 |
| `world.weather` | `"clear"` / `"rain"` / `"thunder"`，或数组 | 天气。 |
| `world.moonPhase` | `0`–`7`，或数组 | 月相，`0` 是满月，每过一天加 1。 |
| `world.dimension` | 维度 id 或数组 | 支持任意维度，包括模组维度，例如 `"twilightforest:twilight_forest"`。 |
| `world.difficulty` | `"peaceful"` / `"easy"` / `"normal"` / `"hard"` | 世界难度。 |
| `world.minDay` | 整数 | 世界至少过了这么多天。 |
| `self.onFire` | `true` / `false` | 施法者是否必须着火。 |
| `self.inWater` | `true` / `false` | 是否必须在水里。 |
| `self.inLava` | `true` / `false` | 是否必须在岩浆里。 |
| `self.frozen` | `true` / `false` | 是否必须处于冰冻（细雪）状态。 |

`true` 表示“必须满足”，`false` 表示“必须不满足”，不写表示不关心。

```json
"conditions": {
  "world": {
    "time": ["night", "dusk"],
    "weather": ["thunder"],
    "dimension": ["minecraft:overworld", "twilightforest:twilight_forest"],
    "minDay": 3
  },
  "self": { "onFire": false, "inWater": false },
  "target": { "maxTargetDistance": 16 }
}
```

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
| `POSITION` | 世界中的一个点，见瞄准模式。 |

> ⚠️ **`SELF` 表示“目标就是自己”。** 而“自己”永远找得到，所以只要冷却好了、法术可用，它就会放，跟有没有敌人在场完全无关。
> 如果你想要“只在战斗中才召唤/治疗”，把那一行的 `target` 换成 `ATTACK_TARGET`：

```json
"goals": [
  { "spell": "irons_spellbooks:heal", "cooldown": 400, "target": "SELF" },
  { "spell": "irons_spellbooks:summon_vex", "cooldown": 400, "target": "ATTACK_TARGET" }
]
```

### 目标对象字段

| 键 | 默认 | 说明 |
|---|---|---|
| `type` | `ATTACK_TARGET` | 上表之一。 |
| `sort` | `NEAREST` | `NEAREST`、`FARTHEST`、`LOWEST_HEALTH`、`HIGHEST_HEALTH`、`RANDOM`。 |
| `lineOfSight` | `true` | 实体目标必须可见（`POSITION` 忽略）。 |
| `maxHealthPercent` | `1.0` | 只接受血量比例不高于此值的目标；等价于 `filters.maxHealthPercent`。 |
| `positionMode` | `RELATIVE` | 仅 `POSITION`，见下。 |
| `relativeOffset` | `[0,0,0]` | 仅 `POSITION`；`RELATIVE` 用全部三个分量，`BEHIND_TARGET` 用 `x`（距离）和 `y`（垂直偏移）。 |
| `placement` | 空 | 仅 `POSITION` 的落点安全检查，见下。 |
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


### 瞄准模式（仅 `POSITION`）

> **先弄明白一件事：`POSITION` 不是“落点”，它只决定朝哪看。**
>
> Iron's Spells 的施法接口没有坐标参数，投射物一律从施法者的眼睛/身体生成，再沿着它当时的视线飞出去。
> 所以 `POSITION` 解析出来的点会被喂给 `lookAt`，法术真正“出生”的位置不会变。
>
> - 纯投射物（冰锥、魔法飞弹、火球、各种射线）：只改朝向。只有在“没有实体目标也要打”的时候才有意义，**它不能打到墙后的目标**。
> - 靠视线找落点的法术（传送、岩浆炸弹、黑洞、冰块、传送门）：真正受影响，因为射线起点和方向变了。
> - 自施法 / 增益 / 召唤：完全无效。
>
> 想要“追着目标打”就用 `ATTACK_TARGET` / `NEAREST_HOSTILE` 这类实体目标，不要用 `POSITION + TARGET_ENTITY`——后者会丢掉视线检测和 `filters`。

| 模式 | 瞄准哪儿 |
|---|---|
| `RELATIVE` | 施法者位置 + `relativeOffset`。 |
| `EYES_LOOK` | 眼睛位置 + 视线方向 × `range`。 |
| `FEET_LOOK` | 脚下位置 + 视线方向 × `range`（和 `EYES_LOOK` 的射线起点不同）。 |
| `TARGET_ENTITY` | 目标所在位置；没有目标就不施放。 |
| `BEHIND_TARGET` | 目标背后：`relativeOffset.x` 是距离（默认 `2` 格），`relativeOffset.y` 是垂直偏移。用来让传送类法术落到玩家身后，而不是脚下。 |
| `RANDOM_IN_RANGE` | `range` 格内的随机一点。 |

#### 落点安全检查（placement）

`RANDOM_IN_RANGE` 和 `BEHIND_TARGET` 可以再加一层安全检查，避免把瞄准点丢进水里或岩浆里：

```json
"target": {
  "type": "POSITION",
  "positionMode": "RANDOM_IN_RANGE",
  "placement": { "avoidFluid": true, "avoidLava": true }
}
```

| 字段 | 默认 | 说明 |
|---|---|---|
| `avoidFluid` | `false` | 拒绝落在任何液体里的点（水、岩浆都算）。 |
| `avoidLava` | `false` | 只拒绝岩浆，水仍然可以。 |

`RANDOM_IN_RANGE` 最多重试 8 次，全都落在被禁止的液体里就放弃这次施法；`BEHIND_TARGET` 不安全时退回到目标自身的位置。

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

### 阶段锁定 `sticky`

血量阶段默认是双向的：Boss 被治疗回到阈值以上，就退回上一阶段，整套技能组跟着换回去。给变体加上 `"sticky": true` 之后，它**一旦命中就锁定**，血量回升也不会退回；继续掉到更深的阶段仍然允许，而锁定只会往前走。

```json
{
  "variants": [
    { "select": { "healthBelow": 0.5 }, "sticky": true,
      "goals": [ { "spell": "irons_spellbooks:wither_skull", "cooldown": 100 } ] },

    { "select": { "healthBelow": 0.2 }, "sticky": true,
      "goals": [ { "spell": "irons_spellbooks:black_hole", "cooldown": 200 } ] }
  ]
}
```

- `sticky` 默认 `false`，即原来“血量回升就退回”的行为。
- 只对带 `sticky` 的变体生效；没写 `sticky` 的变体照旧双向匹配。
- 改名会重置锁定（改名等于换了个体）；锁定状态写进存档，区块卸载再加载不会掉阶段。
- 写在 `select` 里也认：`"select": { "healthBelow": 0.5, "sticky": true }`，效果相同。

### 卷轴学习 `learner`

变体写 `"learner": true` 之后，这只生物就能学卷轴上的法术，效果等同于给它打 `scrollLearning.learnerTag` 标签，但规则写在数据包里：

```json
{ "select": { "name": "Bryan", "tamed": true }, "learner": true,
  "goals": [ { "spell": "irons_spellbooks:fang_strike", "cooldown": 80 } ] }
```

- 只有**已驯服**的生物能学卷轴，`learner` 本身不检查驯服，所以通常和 `select.tamed` 一起用。
- 写在 `select` 里也认：`"select": { "name": "Bryan", "tamed": true, "learner": true }`。
- jar 内置的 `wolf.json` 就是一个例子：野生的 Bryan 只会唤魔尖牙，被驯服后保留尖牙并获得学习能力。

## 掉落

`dropRules` 给会施法的生物追加掉落，也可以用来做“只掉东西、不施法”的战利品变体。

`dropRules` 可以写两个地方：

- **文件根节点**：对所有解析到这份定义文件的生物生效。
- **`variants` 的某个变体里**：只在该变体命中时生效；生物死亡时命中哪个变体，就用哪个变体的规则。

两处、以及同一处的多条规则，**所有 `select` 命中的规则都会结算**，不再“第一条命中后忽略后面的”。所以可以把“必掉的金块”和“20% 概率的战利品表”拆成两条规则，也可以让两条规则共用同一个 `select`。

> ⚠️ 注意
>
> - 规则不再互相覆盖：多条规则同时命中时掉落会叠加。写兜底规则时，把条件都写进 `select`，不要依赖“前面的命中就跳过后面”。
> - 只写 `dropRules`、不写 `goals` 是合法的：生物不施法，但照常掉东西。
> - 多个变体共用同一批掉落时，直接写在根节点更省事。

```json
"dropRules": [
  {
    "select": { "tags": ["spellmob_boss"] },
    "chance": 1.0,
    "drops": [
      { "item": "minecraft:gold_block", "min": 3, "max": 6, "looting": true }
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
```

写在变体里时，结构与根节点完全相同：

```json
"variants": [
  {
    "select": { "tags": ["spellmob_boss"] },
    "goals": [ ... ],
    "dropRules": [
      { "select": { "tags": ["spellmob_boss"] }, "chance": 1.0, "drops": [ ... ] }
    ]
  }
]
```

- 规则级 `chance` 每条规则各掷一次，条目级 `chance` 逐条掷。
- 每个条目二选一：`item` 或 `lootTable`。
- 固定数量用 `count`，区间用 `min` / `max`（不是 `minCount`/`maxCount`）。
- `requirePlayerKill` 默认 `true`，刷怪塔与挤压击杀不会掉落。

## Boss 血条

把 `presentation.bossBar` 设为 `true` 后，带 `bossBarTags` 标签（默认只有 `spellmob_boss`）的施法者会获得一条原版 Boss 血条：

- 名字取生物显示名，进度是当前血量比例，颜色由 `bossBarColor` 决定。
- 只发给 `bossBarRange` 格以内、非旁观模式的玩家；玩家进出范围会自动增减。
- 与 `presentation.enabled` 独立：只想关法阵、留血条，就把 `enabled` 设 `false`、`bossBar` 设 `true`。
- 生物死亡或区块卸载时血条自动收起；改配置项即时生效，不用重启。
- 数据包可以逐个体覆盖：在 `spell_casters` 文件的根节点或某个变体里写 `"bossBar": true` 或 `"bossBar": false`，优先级高于 `bossBarTags` 与 `presentation.bossBar`；不写就沿用配置。同一物种的两个个体因此可以一个显示、一个不显示（jar 内置的 `zombie.json` 就是这样演示的）。

## 会学习的 Boss

这是给“第二次挑战”准备的机制：Boss 打败过某个玩家后，下次再遇到这个玩家时，会从对方上次用过的法术里随机学会一个，并且总以该法术的最高等级释放。

- 门控：`adaptive.enabled` 打开，且这只 Boss 带 `adaptive.adaptiveTag` 指定的计分板标签（默认 `spellmob_adaptive`）；用 `/tag @e[type=minecraft:zombie,limit=1] add spellmob_adaptive` 就能让它开始学。
- 记录：Boss 与玩家交手（任一方打到对方）时算“交战”；玩家交战中释放的每一个法术都会被记下，每个玩家最多 `rememberedSpells` 条。
- 学会：玩家被它打死（或死在它 48 格内）时，这次交战记为“失败”；下次再交手，只要该玩家上次记下的法术里还有没学过的、也没在 `spellBlacklist` 里的，Boss 就随机学一个。
- 上限：一个 Boss 最多 `maxLearnedSpells` 个（默认 1，填 0 = 完全不学）；学到的法术按 `learnedCooldown` 参与正常施法，`-1` 表示跟随该法术自己的默认冷却，填 ≥0 则用固定 tick；冷却同样受 `balance.cooldownMultiplier` 缩放。等级固定 `getMaxLevel()`。
- 默认黑名单是 `irons_spellbooks:teleport`、`blood_step`、`evasion`，防止 Boss 学会逃跑类法术。
- 状态存在生物 NBT 里（`adaptive` 子标签），杀死 Boss、换维度都还在；`/reload` 不会抹掉，因为每次同步都会从施法者状态重建这些 Goal。

## 宠物学习卷轴

已驯服的生物可以把法术卷轴“教”给它：把卷轴丢到它脚边，地上会亮起该学派的法阵，几秒后卷轴被消耗，它学会这个法术，并把它加入自己的施法列表。

- 前提：生物已驯服，且带 `scrollLearning.learnerTag` 指定的标签（默认 `spellmob_learner`）或在它的变体里写了 `"learner": true`；卷轴必须是主人自己丢的（`requireOwner`）。
- 判定：卷轴落地后 2.5 格内最近的合格宠物会开始仪式；黑名单里的法术会画一圈黑法阵并提示，卷轴留在地上不消耗。
- 冷却与上限：每学完一本进入 `scrollLearning.cooldown`（默认 30 分钟）冷却；一只宠物最多学 `maxSpells` 个（默认 5，0 = 关闭学习）。服务器管理员可以用 `/spellmob resetcooldown <目标>` 立刻清掉学习冷却。
- 等级：卷轴写的是几级就学几级；再学同名法术时，只有更高等级的卷轴才会覆盖旧等级，此时法阵上会多出一圈螺旋上升的粒子（普通学习没有）。
- 施法：学会的法术使用宠物专用参数——`pets.castPriority`（默认 4，可用 `pets.castPriorityOverrides` 按生物类型单独覆盖）、`pets.castRange`（默认 16 格），冷却按 `clamp(法术默认冷却 × spellCooldownMultiplier, cooldownMin, cooldownMax)` 计算（默认 2 倍、60–600 tick），并同样受 `balance.cooldownMultiplier` 缩放。
- 法力：`pets.shareOwnerMana` 打开时（默认），宠物施法扣的是主人的法力，实际消耗 = 法术基础消耗 × `pets.manaCostMultiplier` × `pets.manaCostPercent` / 100；主人不在线时不检查法力、也不扣。
- 其它：宠物坐下时仍能学卷轴，但坐下时不施法；`scrollLearning.enabled` 关掉后所有投掷卷轴都不再触发仪式。
- 学会的状态存在生物 NBT 里（`adaptive.learned`），还带 `spellmob_caster` 标签，所以即使这个物种没有数据包规则，它也会继续 tick 冷却、继续施法。

## 配置

配置文件在 `config/spellmob-common.toml`（COMMON 类型——整份安装共用一个文件，不是每个世界一份）。

它就是一个普通的 Forge `ModConfigSpec`，所以 **Configured** 这类“游戏内编辑配置”的模组可以直接读到它、也能在游戏里改：

- 开关、冷却倍率、黑名单、法阵这些**普通数值**改完立即生效，不用重启（我们是每次用到时现读，不缓存）。
- 但 `[species]` 里的规则文本（`extraRules`、`spawnEgg` 等）是启动时解析成规则对象的，改完需要 `/spellmob resync`，或者重启游戏。
- 数据包永远优先于配置里的同名规则：数据包定义了某个实体，配置里对它的规则就会被忽略（`/spellmob info` 会显示来源，日志里也会打一行提示）。

| 分节 | 键 | 默认 | 说明 |
|---|---|---|---|
| `general` | `enabled` | `true` | 总开关。 |
| | `debugLogging` | `false` | 打开 `[SpellMob]` 详细日志。 |
| `balance` | `cooldownMultiplier` | `1.0` | 缩放数据包里的所有冷却；`0` 会压到最短 1 tick。 |
| | `maxRange` | `32` | 数据包 `range` 的硬上限（最高 128）。 |
| | `interruptChannelsOnDamage` | `true` | 受伤是否打断引导法术。 |
| | `globalCooldown` | `0` | 全局冷却（tick）：一次施法后该施法者的所有法术都要再等这么久；`0` 关闭，上限 400。 |
| `adaptive` | `enabled` | `true` | 总开关：Boss 是否会学习打败过自己的玩家用过的法术。 |
| | `adaptiveTag` | `spellmob_adaptive` | 标记“会学习”的计分板标签；Boss 必须同时带上它。 |
| | `maxLearnedSpells` | `1` | 一个 Boss 一辈子最多记住几个法术（0–8）；`0` 表示不学。 |
| | `rememberedSpells` | `6` | 每场战斗最多记一个玩家的几个法术（1–16）。 |
| | `learnedCooldown` | `-1` | 学会的法术的冷却（tick，-1–1200）；`-1` 表示跟随该法术自己的默认冷却（秒 ×20），≥0 用固定值。 |
| | `learnedPriority` | `2` | 学会的法术的 Goal 优先级（0–9，越小越先执行）。 |
| | `spellBlacklist` | 传送/血步/闪避 | 永不被学会的法术 id，可以继续往里加。 |
| `scrollLearning` | `enabled` | `true` | 总开关：卷轴丢到宠物脚边能不能学法术。 |
| | `learnerTag` | `spellmob_learner` | 能学法术的宠物必须带这个标签。 |
| | `maxSpells` | `5` | 一只宠物最多学几个法术（0–16）；`0` 表示关闭。 |
| | `cooldown` | `36000` | 学完一本后的冷却（tick，0–720000），默认 30 分钟。 |
| | `ritualTicks` | `100` | 学习仪式时长（20–400 tick）。 |
| | `requireOwner` | `true` | 只有主人丢的卷轴才认。 |
| | `spellCooldownMultiplier` | `2.0` | 学会的法术冷却倍率（0.1–10），乘在法术默认冷却上。 |
| | `cooldownMin` | `60` | 学会的法术冷却下限（tick）。 |
| | `cooldownMax` | `600` | 学会的法术冷却上限（tick）。 |
| | `spellBlacklist` | 传送/召唤等 | 不能学、也不触发仪式的法术 id。 |
| `pets` | `defendOwner` | `true` | 主人被打时，附近没坐下的宠物会反击攻击者。 |
| | `aggressive` | `false` | 为真时宠物会主动索敌最近的敌对生物。 |
| | `engageRange` | `24.0` | 护主/索敌的触发距离（格）。 |
| | `castPriority` | `4` | 宠物施法 Goal 的优先级（越小越先执行，4 表示先近战再法术）。 |
| | `castRange` | `16.0` | 宠物法术的最大射程。 |
| | `shareOwnerMana` | `true` | 宠物施法扣主人的法力。 |
| | `manaCostMultiplier` | `1.0` | 宠物法力消耗倍率。 |
| | `manaCostPercent` | `100` | 宠物法力消耗的百分比（100 = 全额，50 = 半价，0 = 免费），与 `manaCostMultiplier` 相乘。 |
| | `castPriorityOverrides` | `[]` | 按生物类型单独设置施法优先级，例如 `["minecraft:wolf=2", "minecraft:cat:3"]`；没列出的用 `castPriority`。 |
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
| | `bossBar` | `false` | 是否给施法者显示 Boss 血条；与 `enabled` 独立，可以只开血条不开法阵。 |
| | `bossBarColor` | `white` | 血条颜色：`pink` / `blue` / `red` / `green` / `yellow` / `purple` / `white`。 |
| | `bossBarRange` | `48` | 这个距离内的玩家能看到血条（8–128）。 |
| | `bossBarTags` | `["spellmob_boss"]` | 带这些标签的生物显示血条；留空表示所有配置了规则的生物都显示。 |
| | `bossBarCastName` | `true` | 读条时血条名后面显示“正在施放 xxx”；关掉只显示生物名字。 |
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
| `/spellmob dump spells` | 把所有已注册的法术 id、施法类型、学派导出到 `config/spellmob-spells.txt`。 |
| `/spellmob resetcooldown [目标]` | 清掉宠物的卷轴学习冷却（不给参数就是自己），方便反复测试。 |

## 兼容性说明

- **纯服务端。** 客户端不需要装模组；法阵是服务端发送的普通粒子。
- **数据包路径必须镜像实体 id。** `data/mypack/` 下的文件永远影响不到 `minecraft:zombie`。
- **未知 JSON 键会被忽略。** 字段名拼错不会报错——用 `validate` 和 `info` 抓。
- **编辑器补全。** 仓库里的 `docs/spell_casters.schema.json` 是标准 JSON Schema，在 VS Code / IntelliJ 里给 `spell_casters/*.json` 绑定 `$schema` 后可以补全字段并标出拼错的键。
- **ISS 版本。** 以 3.4.0.9 的 API 编译，已在 3.16.3 上验证；jar 声明 `[1.20.1-3.4.0.9,)`。

## 示例

仓库的 `examples/` 下有可直接使用的包：

| 目录 | 内容 |
|---|---|
| `examples/classic/` | 最开始的五个示例：僵尸 A/B、骷髅、狼、村民、尸壳。 |
| `examples/bosses/` | 六个「名字 + 标签」双门控 Boss（虚空之眼、沙海祭司……）。 |
| `examples/channel_test/` | 会引导全部 CONTINUOUS 法术的掠夺者与卫道士。 |
| `examples/full-reference.json` | 字段参考——所有键集中在一个文件里。 |
