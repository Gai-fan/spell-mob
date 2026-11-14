# SpellMob 教程（对应 2.1.0）

## 一、简介与运行环境

SpellMob 让数据包驱动的生物施放 Iron's Spells 'n Spellbooks（下称 ISS）里的法术：给实体写一个 JSON，就能规定它什么时候施法、放什么、打谁、掉什么。模组本身不含新实体、新方块，纯服务端逻辑，客户端不装也能进服。

| 平台 | Minecraft | 前置 |
|---|---|---|
| Forge | 1.20.1 | Iron's Spells 'n Spellbooks `1.20.1-3.4.0.9` 及以上 |
| NeoForge | 1.21.1 | Iron's Spells 'n Spellbooks `1.21.1-3.16.3` 及以上 |

专用服务器只需在服务端安装本模组；客户端必须安装 ISS，否则看不到法术效果。

## 二、数据包结构、加载与最小示例

数据包需要 `pack.mcmeta` 与 `data/` 目录。规则文件放在 `data/<命名空间>/spell_casters/` 下，文件名即实体 id：

- `data/minecraft/spell_casters/zombie.json` → `minecraft:zombie`
- `data/examplemod/spell_casters/example_mob.json` → `examplemod:example_mob`

一个实体类型一个文件，同物种的不同个体或不同阶段写在同一个文件的 `variants` 里。

```
my_spellmob_pack/
  pack.mcmeta
  data/minecraft/spell_casters/zombie.json
```

`pack.mcmeta`：1.20.1 用 `pack_format: 15`，1.21.1 用 `48`。

```json
{
  "pack": {
    "description": "SpellMob example",
    "pack_format": 15
  }
}
```

放进存档的 `datapacks/` 并确认启用，然后 `/reload`。日志出现 `[SpellMob] Loaded 1 spell caster definition(s)` 表示加载成功，`/datapack list` 可确认状态。

多个数据包含同一路径时，优先级高的覆盖低的。

最小示例：带 `spellmob_demo` 标签的僵尸放火球，其他僵尸不受影响。

```json
{
  "variants": [
    {
      "select": { "tags": ["spellmob_demo"] },
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

```
/summon minecraft:zombie ~ ~ ~5 {PersistenceRequired:1b,Tags:["spellmob_demo"]}
/spellmob info @e[type=minecraft:zombie,sort=nearest,limit=1]
```

从 1.20.1 迁移到 1.21.1 时结构与字段不变，只改 `pack_format`。

## 三、规则文件的根节点

| 字段 | 作用 |
|---|---|
| `goals` | 周期性施法条目 |
| `onHurt` | 受到伤害时触发 |
| `onTarget` | 获得新攻击目标时触发 |
| `onDeath` | 死亡时触发 |
| `onKill` | 击杀其他生物后触发 |
| `variants` | 按名称、标签、生命比例或驯服状态匹配个体 |
| `dropRules` | 追加掉落 |
| `bossBar` | 为这个物种强制开/关 Boss 血条（布尔值，可被变体覆盖） |
| `disableAttackGoals` | 为 true 时移除原版近战/远程攻击 Goal，默认 false；可被变体覆盖 |

至少要包含其中一个字段，全部不存在时文件被忽略。以 `_` 开头的键可以当注释。

## 四、施法条目的通用字段

`goals` 的每条记录、以及 `onHurt`、`onTarget`、`onDeath`、`onKill` 共用这套字段（`onDeath` 对 `castTime`、`cooldown`、`chance` 的处理不同，见触发器一节）。

| 字段 | 默认 | 说明 |
|---|---|---|
| `spell` | 必填 | ISS 中已注册且未禁用的法术 id |
| `level` | `1` | 法术等级，受该法术上限约束 |
| `target` | `ATTACK_TARGET` | 目标写法见第七节 |
| `range` | `16.0` | 施法距离，最终受 `balance.maxRange` 限制 |
| `castTime` | `-1` | 吟唱 tick；`-1` 用 ISS 计算值，`0` 瞬发 |
| `cooldown` | `-1` | 冷却 tick；`-1` 用法术默认，最小 1 tick |
| `cooldownGroup` | 无 | 共享冷却组，1–32 个字符，小写字母/数字/下划线/点/连字符 |
| `priority` | `1` | Goal 优先级 0–9，越小越先，仅 `goals` 使用 |
| `checkInterval` | `10` | 周期检查间隔 1–200 tick |
| `chance` | `1.0` | 每次尝试真正施放的概率 0–1 |
| `interruptOnDamage` | `false` | 吟唱期受伤是否打断；引导法术改用 `balance.interruptChannelsOnDamage` 与 `protection.channelImmuneTags` |
| `repeatOnCast` | `true` | 仅 CONTINUOUS 引导法术：是否每 10 tick 重跑 `onCast` |
| `conditions` | 无 | 施法前条件，见第八节 |

### 优先级与原版 AI 的抢占

原版 `GoalSelector` 先按优先级数字从小到大取，再按注册顺序取第一个能运行的 Goal。施法 Goal 占用 `MOVE` 与 `LOOK`，所以：

- 僵尸/尸壳近战 `ZombieAttackGoal` 是 2 → 施法写 `priority: 1` 才能抢在它前面；
- 铁傀儡近战是 1 → 施法要写 `priority: 0`；
- 数字相同则原版 Goal 先注册、先运行，法术永远排不上。

经验：普通怪用 1，铁傀儡这类近战为 1 的用 0，Boss 用 0–1。

## 五、周期性施法

`goals` 每条注册为一个 Goal，每 `checkInterval` tick 检查一次。施法期间占 `MOVE`/`LOOK`，原版近战不会打断吟唱。

目标死亡、离开 `range + 4` 格、需要视线而视线丢失，或法术 `shouldAIStopCasting` 为 true 时，当前施法取消，冷却不返还。

```json
{
  "goals": [
    { "spell": "irons_spellbooks:fireball", "level": 2, "cooldown": 120, "priority": 1, "range": 20.0, "castTime": 20 },
    { "spell": "irons_spellbooks:magic_missile", "level": 1, "cooldown": 80, "priority": 2, "range": 16.0 }
  ]
}
```

`chance` 控制每次尝试真正施放的概率，未命中则等下一次 `checkInterval`。
`cooldownGroup` 让多个法术共用冷却，适合阶段切换与固定轮换。

## 六、触发器

### onHurt

受到伤害时触发，优先以伤害来源为目标。生物正在吟唱时不会开始新施法；若当前吟唱配置了 `interruptOnDamage`，或被全局规则打断，先取消再尝试触发。

```json
{
  "onHurt": {
    "spell": "irons_spellbooks:teleport",
    "level": 1,
    "cooldown": 300,
    "range": 10.0,
    "target": { "type": "POSITION", "positionMode": "RELATIVE", "relativeOffset": [0, 0, 4], "lineOfSight": false }
  }
}
```

### onTarget

获得新攻击目标时触发，优先以新目标为目标。

```json
{ "onTarget": { "spell": "irons_spellbooks:slow", "level": 1, "cooldown": 200, "range": 20.0 } }
```

### onDeath

死亡时触发，按瞬发处理：不读 `castTime`，不检查冷却与 `chance`，但仍检查 `general.enabled`、实体/维度黑名单与 `conditions`。若死亡时正在施法，先以 `CASTER_DIED` 取消再放死亡法术。

实体目标需要存在合法目标；无法保证有目标时请用 `SELF` 或 `POSITION`。

```json
{
  "onDeath": {
    "spell": "irons_spellbooks:icicle",
    "level": 2,
    "range": 8.0,
    "target": { "type": "POSITION", "positionMode": "RANDOM_IN_RANGE", "lineOfSight": false }
  }
}
```

### onKill

击杀其他生物后触发，走正常冷却与条件检查。

```json
{ "onKill": { "spell": "irons_spellbooks:haste", "level": 1, "cooldown": 300, "target": "SELF" } }
```

被击杀目标已死亡，实体目标会退化为尸体位置；`SELF` 不受影响。

## 七、目标选择

`target` 可以写成字符串（等价于 `type` 为该值、`lineOfSight: true` 的对象写法）。

### 目标类型

| 类型 | 含义 |
|---|---|
| `ATTACK_TARGET` | 当前攻击目标 |
| `HOSTILE_TARGET` | `ATTACK_TARGET` 的兼容别名 |
| `LAST_DAMAGE_SOURCE` | 最后一个伤害来源实体 |
| `THREATENING_ENTITY` | 攻击过施法者、或正以施法者为目标的实体 |
| `NEAREST_HOSTILE` | 最近的敌对目标，排除同队与同类 |
| `NEAREST_UNDEAD` | 最近的亡灵生物 |
| `NEAREST_ARTHROPOD` | 最近的节肢生物 |
| `NEAREST_ANIMAL` | 最近的动物 |
| `NEAREST_MONSTER` | 最近的怪物 |
| `NEAREST_PLAYER` | 最近的玩家，默认排除创造与旁观 |
| `OWNER` | 驯服生物的主人 |
| `NEAREST_ALLY` | 最近的同类型或同队生物（不含玩家） |
| `SELF` | 施法者自己 |
| `RANDOM_HOSTILE` | 范围内随机一个敌对目标 |
| `POSITION` | 不锁定实体，瞄准一个坐标 |

### 目标对象字段

| 字段 | 默认 | 说明 |
|---|---|---|
| `type` | `ATTACK_TARGET` | 目标类型 |
| `lineOfSight` | `true` | 实体目标是否需要视线；`POSITION` 忽略 |
| `sort` | `NEAREST` | 多候选时排序：`NEAREST`、`FARTHEST`、`LOWEST_HEALTH`、`HIGHEST_HEALTH`、`RANDOM` |
| `filters` | 空 | 目标过滤 |
| `minHealthPercent` / `maxHealthPercent` | 空 | 也可写在目标对象顶层，等价于 `filters` 中的同名字段 |

`sort` 影响多候选目标与 `THREATENING_ENTITY`，平局按距离。

### filters 字段

| 字段 | 默认 | 说明 |
|---|---|---|
| `entityTypes` | 空 | 只接受列出的实体 id，任意一个匹配即可 |
| `entityTags` | 空 | 只接受带其中任意一个实体类型标签的目标 |
| `excludeTags` | 空 | 带这些计分板标签的目标直接排除 |
| `excludeCasterTeam` | `false` | 排除与施法者同队的目标 |
| `ignoreCreative` | `true` | 排除创造模式玩家；旁观始终排除 |
| `minHealthPercent` / `maxHealthPercent` | `0.0` / `1.0` | 只接受生命比例落在区间内的目标 |
| `requireNoShield` | `false` | 只接受没有举盾格挡的目标 |

被 `filters` 筛掉的候选**不消耗冷却**。

```json
{
  "target": {
    "type": "NEAREST_PLAYER",
    "sort": "LOWEST_HEALTH",
    "filters": { "excludeTags": ["spellmob_immune"], "ignoreCreative": true, "requireNoShield": true }
  }
}
```

`requireNoShield` 只影响“选谁当目标”，不会让法术伤害穿透盾牌——伤害结算仍走原版盾牌逻辑。

### 瞄准模式（仅 `POSITION`）

**`POSITION` 不是“落点”，它只决定朝哪看。** ISS 的施法接口没有坐标参数，投射物一律从施法者眼睛/身体生成，再沿当时视线飞出。解析出的点会被喂给 `lookAt`。

- 纯投射物（冰锥、魔法飞弹、火球、射线）：只改朝向，打不到墙后目标；
- 靠视线找落点的法术（传送、岩浆炸弹、黑洞、冰块、传送门）：真正受影响；
- 自施法/增益/召唤：完全无效。

| 模式 | 瞄准哪儿 |
|---|---|
| `RELATIVE` | 施法者位置 + `relativeOffset` |
| `EYES_LOOK` | 眼睛位置 + 视线 × `range` |
| `FEET_LOOK` | 脚下位置 + 视线 × `range` |
| `TARGET_ENTITY` | 目标所在位置；没有目标就不施放 |
| `BEHIND_TARGET` | 目标背后：`relativeOffset.x` 是距离（默认 2 格），`relativeOffset.y` 是垂直偏移 |
| `RANDOM_IN_RANGE` | `range` 格内随机一点 |

落点安全检查：

```json
"target": { "type": "POSITION", "positionMode": "RANDOM_IN_RANGE", "placement": { "avoidFluid": true, "avoidLava": true } }
```

`avoidFluid` 拒绝任何液体，`avoidLava` 只拒绝岩浆。`RANDOM_IN_RANGE` 最多重试 8 次，全失败则放弃本次施法；`BEHIND_TARGET` 不安全时退回目标位置。

`/spellmob validate` 会在“`POSITION` + 投射物”时给出警告：只会改朝向。

## 八、施法条件

| 字段 | 默认 | 说明 |
|---|---|---|
| `healthBelow` | `1.0` | 施法者生命比例 ≤ 该值才施放 |
| `healthAbove` | `0.0` | 施法者生命比例 ≥ 该值才施放 |
| `minTargetDistance` | `0` | 与目标距离下限 |
| `maxTargetDistance` | 无 | 与目标距离上限 |

条件可以按来源分组嵌套（`self` / `target` / `world`），扁平写法仍有效，同名键同时出现时以扁平为准。`POSITION` 目标不参与距离判定。

### 世界条件 `world`

| 字段 | 可选值 |
|---|---|
| `time` / `timeOfDay` | `day`（0–11999）、`dusk`（12000–12999）、`night`（13000–22999）、`dawn`（23000–23999） |
| `weather` | `clear`、`rain`、`thunder`（打雷优先于下雨） |
| `moonPhase` | `0`–`7` |
| `dimension` | 维度 id，如 `minecraft:the_nether`、`twilightforest:twilight_forest` |
| `difficulty` | `peaceful`、`easy`、`normal`、`hard` |
| `minDay` | 世界天数下限 |

没有天空光的维度（下界、末地）里时间条件恒为 false。

### 施法者条件 `self`

`onFire`、`inWater`、`inLava`、`frozen`，三态：不写 = 不关心，`true` = 必须是，`false` = 必须不是。`frozen` 指被细雪冻住（`getTicksFrozen() > 0`）。

```json
{
  "conditions": {
    "self": { "healthBelow": 0.6, "onFire": false },
    "world": { "time": "night", "weather": "thunder", "dimension": "minecraft:overworld" },
    "target": { "minTargetDistance": 4.0, "maxTargetDistance": 16.0 }
  }
}
```

## 九、变体

`variants` 按顺序匹配，第一条命中的生效。只在数据包可用（配置的 `[species] extraRules` 不支持）。

| 字段 | 说明 |
|---|---|
| `name` | 自定义名完全相等 |
| `nameRegex` | 自定义名整串正则 |
| `tags` | 必须拥有列出的全部计分板标签 |
| `healthBelow` | 血量比例严格低于 |
| `healthAbove` | 血量比例严格高于 |
| `tamed` | 是否已驯服 |

变体额外字段：

| 字段 | 默认 | 说明 |
|---|---|---|
| `override` | `true` | `true` 完全替换顶层默认；`false` 把变体 goals 追加在默认之前，触发器仅在变体未定义时回落到顶层 |
| `sticky` | `false` | 命中后锁定阶段，血量回升不退回 |
| `bossBar` | 跟随配置 | `true`/`false` 单独开关这只生物的血条 |
| `learner` | `false` | 允许学卷轴法术，等价于打 `scrollLearning.learnerTag` 标签 |
| `adaptive` | `false` | 允许学习打败过的玩家的法术，等价于打 `adaptive.adaptiveTag` 标签 |
| `disableAttackGoals` | 跟随根层 | `true` 移除原版近战/远程攻击 Goal，`false` 恢复；不写跟随根层 |

`override: true` 又不写任何法术 = 这个个体什么都不放（可用于“沉默”）。

**改名、驯服状态、生命阶段变化**会自动重建 Goal；**改计分板标签或数据包**后要 `/spellmob resync`。

### 阶段锁定 `sticky`

默认血量阶段是双向的。加 `"sticky": true` 后一旦命中就锁定，血量回升也不退回；继续掉到更深阶段仍然允许，锁只往前走。

```json
"variants": [
  { "select": { "healthBelow": 0.5 }, "sticky": true, "goals": [ { "spell": "irons_spellbooks:wither_skull", "cooldown": 100 } ] },
  { "select": { "healthBelow": 0.2 }, "sticky": true }
]
```

锁定状态写进存档，区块卸载不会掉阶段；改名会重置锁定。

### 卷轴学习 `learner`

```json
{ "select": { "name": "Bryan", "tamed": true }, "learner": true,
  "goals": [ { "spell": "irons_spellbooks:fang_strike", "cooldown": 80 } ] }
```

只有已驯服的生物能学卷轴；`examples/classic` 里的 `wolf.json`（`Bryan`）就是例子。

## 十、掉落规则

`dropRules` 可以写在**根节点**，也可以写在**变体里**（只在该变体命中时生效）。所有命中的规则都会结算，不再 first-match-wins。

| 字段 | 默认 | 说明 |
|---|---|---|
| `select` | 必填 | 与变体相同的选择器 |
| `chance` | `1.0` | 整条规则掷一次 |
| `drops` | 必填 | 掉落条目数组 |

条目字段：

| 字段 | 默认 | 说明 |
|---|---|---|
| `item` / `lootTable` | 二选一 | 物品 id 或战利品表 id |
| `count` | `1` | 固定数量，与 `min`/`max` 二选一 |
| `min` / `max` | `1` | 数量区间（含两端） |
| `chance` | `1.0` | 条目独立概率 |
| `looting` | `false` | 是否按抢夺等级追加 0 到 N 个 |
| `requirePlayerKill` | `true` | 只有玩家击杀才掉（防刷怪塔） |

注意：

- 死亡时生命比例为 0，`healthAbove` 不会命中、`healthBelow` 恒成立，掉落筛选请用 `name`/`nameRegex`/`tags`/`tamed`；
- 数量超过堆叠上限会拆成多组；
- 只写 `dropRules`、不写任何施法规则的文件同样会掉落（“战利品变体”）。

```json
{
  "dropRules": [
    { "select": { "tags": ["spellmob_boss"] }, "chance": 1.0,
      "drops": [ { "item": "minecraft:emerald", "min": 4, "max": 8, "looting": true },
                 { "item": "minecraft:gold_block", "count": 1 } ] }
  ]
}
```

## 十一、Boss 表现与学习

### Boss 血条

`presentation.bossBar = true` 打开后，带 `presentation.bossBarTags`（默认 `spellmob_boss`）标签的生物会显示血条；颜色由全局 `bossBarColor` 决定（`white`/`red`/`pink`/`blue`/`green`/`yellow`/`purple`），范围 `bossBarRange` 默认 48 格。数据包可用变体 `bossBar: true/false` 单独覆盖某只生物。

`bossBarCastName = true` 时，吟唱期间血条名字后面会显示“正在施放 xxx”。

### 阶段演出（onPhase）

变体里写 `onPhase`，生物第一次进入该变体时播一次演出：标题、音效、屏幕震动、地面冲击波、击退、定身无敌、召唤增援、血条换色改名。

最小写法：

```json
"onPhase": {
  "title": "%s 进入了第二阶段",
  "shake": { "duration": 15, "radius": 16.0 },
  "burst": { "radius": 6.0, "color": "auto" },
  "knockback": 5.0,
  "invulnerableTicks": 40,
  "summon": { "entity": "minecraft:iron_golem", "count": 2, "radius": 3.0 },
  "bossBarColor": "red",
  "bossBarName": "%s · 第二阶段",
  "phaseTicks": 40
}
```

- 演出期间生物停止移动、取消在途施法、不能被伤害，结束后恢复 AI 并重建技能表。
- `weather` 与 `time` 字段要生效，必须在配置里打开 `bosses.allowWeatherChange`（默认关闭）。
- `replay` 默认 `false`：每只生物只播一次，重载也不重播；写 `true` 则每次进入都播。
- 只写 `onPhase` 不写 `goals` 也合法，生物保留原来的技能表。

### 会学习的 Boss（adaptive）

Boss 记住与它交战玩家施放的法术；玩家败给它之后，下次交手时随机学一个，永远以该法术最高等级释放。

触发链：

1. 交战：任一方打到对方；
2. 记录：交战期间玩家施放的每个法术（每人最多 `rememberedSpells` 条）；
3. 失败：玩家死给它，或死在它 48 格内；
4. 学习：再次交战，从上次记录里随机学一个（受 `maxLearnedSpells`、`spellBlacklist` 限制）；
5. 施放：学来的法术按 `learnedCooldown`（`-1` 跟随法术自身冷却）与 `learnedPriority` 参与正常施法。

开启方式：配置 `[adaptive] enabled = true`，然后满足其一——变体写 `"adaptive": true`，或给生物打 `adaptiveTag`（默认 `spellmob_adaptive`）标签。

> 如果“学会了却看不到它放”，先检查 `learnedPriority`：铁傀儡这类近战 Goal 是 1，学来的法术默认优先级 1 且后注册，会一直轮不上，把 `learnedPriority` 改成 0 即可。

### 战后重置

`[bosses] resetOnEscape = true` 时，玩家全部离开 `escapeRadius`（默认 32 格）并持续 `escapeGrace`（默认 600 tick）后，Boss 回满血、清阶段锁与目标、停止寻路，保留已学法术，相当于“重置回一阶段”。

## 十二、宠物学习卷轴

已驯服的宠物（带 `scrollLearning.learnerTag` 标签，或变体 `learner: true`）可以被卷轴教会法术：

1. 主人把 ISS 法术卷轴丢到宠物脚下；
2. 宠物脚下展开法阵（颜色取该法术学派色），持续 `ritualTicks`（默认 100 tick）；
3. 完成时卷轴消耗，法术记入宠物；同名更高等级会用螺旋粒子表示“升级”，并覆盖低等级；
4. 中断条件：宠物死亡、卷轴消失、距离超过 3.5 格——中断不消耗卷轴。

判定顺序与字幕提示：学习冷却中（提示还需多久）、黑名单（法阵变黑）、数量已满、已会同级或更高。

宠物施法参数在 `[pets]`：

- `castPriority`（默认 4）/ `castRange`（默认 16）：宠物法术的 Goal 优先级与距离；`castPriorityOverrides` 可按生物类型单独设，如 `minecraft:wolf=2`；
- `spellSelection`（默认 `smart`）：每次起手前按目标挑法术——贴身优先近身法术、远处优先投射物、目标在空中时跳过地面法术；改成 `priority` 则按学会顺序取第一个可用的；
- `shareOwnerMana`（默认 true）：宠物施法扣主人的法力；`manaCostPercent`（默认 100）与 `manaCostMultiplier`（默认 1.0）控制消耗；
- 学来法术的冷却 = `clamp(法术自带冷却秒数 × spellCooldownMultiplier(2.0), cooldownMin(60), cooldownMax(600))`；
- `defendOwner`（默认 true）：主人被攻击时宠物反击；`aggressive`（默认 false）：宠物主动索敌；
- 坐下时不施法，但**可以**学习。

`/spellmob resetcooldown` 可重置学习冷却。

## 十三、命令与调试

| 命令 | 作用 |
|---|---|
| `/reload` | 重新加载数据包，日志出现 `Loaded N spell caster definition(s)` |
| `/spellmob list` | 查看已加载的实体类型 |
| `/spellmob info <目标>` | 查看命中的变体、最终配置与剩余冷却 |
| `/spellmob validate` | 检查已加载定义（含 POSITION + 投射物警告） |
| `/spellmob resync` | 按当前标签与状态重建 Goal |
| `/spellmob dump spells` | 把已注册法术导出到 `config/spellmob-spells.txt` |
| `/spellmob resetcooldown [目标]` | 重置宠物学习冷却 |

`general.debugLogging = true` 后日志会输出施法开始/完成/取消原因与 Goal 同步。

`docs/spell_casters.schema.json` 是完整的 JSON Schema，放进编辑器可补全与校验。

## 十四、配置项

| 键 | 默认 | 说明 |
|---|---|---|
| `general.enabled` | `true` | 总开关 |
| `general.debugLogging` | `false` | 调试日志 |
| `balance.maxRange` | `32.0` | 数据包 `range` 上限 |
| `balance.cooldownMultiplier` | `1.0` | 所有冷却倍率 |
| `balance.globalCooldown` | `0` | 全局 GCD（tick），0 关闭 |
| `balance.interruptChannelsOnDamage` | `true` | 引导法术是否被伤害打断 |
| `filters.entityBlacklist` | 空 | 不施法的实体 |
| `filters.dimensionBlacklist` | 空 | 不施法的维度 |
| `protection.channelImmuneTags` | `["spellmob_boss"]` | 引导免打断的标签 |
| `presentation.enabled` | `true` | 粒子表现总开关 |
| `presentation.points` / `bossPoints` | `24` / `32` | 环的粒子点数，0 关闭 |
| `presentation.distance` | `24.0` | 超过该距离不显示 |
| `presentation.ringScale` | `1.0` | 环半径倍率 |
| `presentation.bossBar` | `false` | Boss 血条开关 |
| `presentation.bossBarColor` | `white` | 血条颜色 |
| `presentation.bossBarRange` | `48.0` | 血条可见距离 |
| `presentation.bossBarTags` | `["spellmob_boss"]` | 哪些标签显示血条 |
| `presentation.bossBarCastName` | `true` | 血条显示“正在施放 xxx” |
| `species.extraRules` | 空 | 物种级规则，一行一条 JSON，不支持 variants |
| `adaptive.enabled` | `true` | 会学习 Boss 总开关 |
| `adaptive.adaptiveTag` | `spellmob_adaptive` | 学习标签 |
| `adaptive.maxLearnedSpells` | `1` | 一生最多学几个（0 = 不学） |
| `adaptive.rememberedSpells` | `6` | 每人最多记几个 |
| `adaptive.learnedCooldown` | `-1` | 学来法术冷却，-1 跟随法术 |
| `adaptive.learnedPriority` | `1` | 学来法术 Goal 优先级 |
| `adaptive.spellBlacklist` | 传送/血步/闪避 | 禁止学习 |
| `scrollLearning.enabled` | `true` | 卷轴教学开关 |
| `scrollLearning.learnerTag` | `spellmob_learner` | 能学的标签 |
| `scrollLearning.maxSpells` | `5` | 最多学几个 |
| `scrollLearning.cooldown` | `36000` | 每次学习后的冷却 |
| `scrollLearning.ritualTicks` | `100` | 法阵时长 |
| `scrollLearning.requireOwner` | `true` | 只认主人丢的卷轴 |
| `scrollLearning.spellCooldownMultiplier` | `2.0` | 学来法术冷却倍率 |
| `scrollLearning.cooldownMin` / `cooldownMax` | `60` / `600` | 冷却上下限 |
| `pets.defendOwner` | `true` | 护主 |
| `pets.aggressive` | `false` | 主动索敌 |
| `pets.engageRange` | `24.0` | 护主索敌范围 |
| `pets.castPriority` | `4` | 宠物法术优先级 |
| `pets.castRange` | `16.0` | 宠物施法距离 |
| `pets.shareOwnerMana` | `true` | 宠物施法扣主人法力 |
| `pets.manaCostMultiplier` | `1.0` | 法力消耗倍率 |
| `pets.manaCostPercent` | `100` | 法力消耗百分比 |
| `pets.castPriorityOverrides` | 空 | 按类型覆盖优先级，如 `minecraft:wolf=2` |
| `bosses.resetOnEscape` | `true` | Boss 战后重置 |
| `bosses.escapeRadius` | `32.0` | 判定离开的范围 |
| `bosses.escapeGrace` | `600` | 离开多久后重置 |

配置改完需要重启游戏或服务器；数据包改完执行 `/reload`（改标签后再 `/spellmob resync`）。

## 十五、常见问题

1. **数据包没加载**：路径与实体 id 不对应、`pack_format` 与版本不匹配、包没启用。用 `/datapack list` 确认。
2. **文件被忽略**：根节点没有任何可用字段。
3. **法术不施放**：法术 id 拼错、ISS 版本没有该法术、法术被禁用。用 `/spellmob validate` 和 `/spellmob dump spells` 对照。
4. **目标不匹配**：类型/模式拼错、需要视线但被挡住、超出 `range`、被 `filters` 排除、`conditions` 不满足。
5. **施法不触发**：正在吟唱、冷却中、`chance` 没中、`general.enabled` 关、实体/维度黑名单。
6. **变体不生效**：标签没加、名称或血量条件不满足、前面的变体先命中。改标签后 `/spellmob resync`。
7. **近战怪只追人不施法**：`priority` 没抢过原版攻击 Goal（见第四节）。
8. **死亡法术不放**：用了实体目标但没有合法目标，改用 `SELF`/`POSITION`。
9. **掉落不触发**：`dropRules` 写在了不支持的位置、用了 `minCount`/`maxCount`（应为 `count`/`min`/`max`）、死亡时用 `healthAbove`、`requirePlayerKill` 为 true 但击杀者不是玩家、或者该生物解析出的施法配置为空。
10. **学会了不放**：`learnedPriority` 与近战 Goal 撞车，调小到 0。
11. **表现不显示**：`presentation.enabled` 关、`points`/`bossPoints` 为 0、玩家超出 `distance`。
12. **血条不显示**：`bossBar` 没开、生物没有 `bossBarTags` 标签、变体写了 `bossBar: false`。
