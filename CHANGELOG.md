# 更新日志

本模组提供两个发行版，功能一致、版本号同步：

- **Forge 版**：Minecraft 1.20.1
- **NeoForge 版**：Minecraft 1.21.1

---

## [2.1.5] — 2026-11-08

### 新增

- **`pets.manaCostPercent`**：宠物施法实际消耗的法力百分比（默认 100，0 = 免费），与 `manaCostMultiplier` 相乘。
- **`pets.castPriorityOverrides`**：按生物类型单独设置宠物施法优先级，例如 `["minecraft:wolf=2", "minecraft:cat:3"]`；未列出的类型继续用 `castPriority`。
- **升级法阵特效**：同一法术被更高等级的卷轴覆盖时，法阵上会多出一圈螺旋上升的粒子，普通学习没有。
- **`/spellmob resetcooldown [目标]`**：立刻清掉宠物的卷轴学习冷却，方便反复测试。

### 文档

- 中英文指南、README 的 `[pets]` 配置表、命令表与卷轴教学章节同步。

---

## [2.1.4] — 2026-11-08

### 新增

- **变体级 `learner`**：在 `spell_casters` 的变体（或 `select`）里写 `"learner": true`，这只生物就能学卷轴上的法术，不必再手动打 `scrollLearning.learnerTag` 标签；只有已驯服的生物能学，因此通常和 `select.tamed` 一起用。
- **内置 `wolf.json`**：名为 `Bryan` 的狼，未驯服时只会每 4 秒放一次唤魔尖牙（`irons_spellbooks:fang_strike`，2 级）；被驯服后保留尖牙并获得学习能力；其他狼不受影响。

### 文档

- 中英文指南、README 的变体字段表与 JSON Schema 加入 `learner`。

---

## [2.1.3] — 2026-11-08

### 新增

- **宠物学习卷轴（`[scrollLearning]`）**：把法术卷轴丢到带 `learnerTag`（默认 `spellmob_learner`）的已驯服生物脚边，地上会亮起该学派的法阵，`ritualTicks` 后卷轴被消耗、宠物学会卷轴上的法术（同名更高等级的卷轴会覆盖旧等级）。黑名单法术画黑法阵并提示、卷轴留在地上；学完一本进入 `cooldown`（默认 30 分钟），一只宠物最多 `maxSpells` 个（默认 5，0 = 关闭）。学到的法术存进 NBT 并给宠物打 `spellmob_caster` 标签，没有数据包规则的宠物也会继续 tick 冷却并施法。
- **宠物施法参数（`[pets]`）**：学会的法术按 `castPriority`（默认 4）、`castRange`（默认 16）参与施法，冷却按 `clamp(法术默认冷却 × spellCooldownMultiplier, cooldownMin, cooldownMax)` 计算（默认 2 倍、60–600 tick）并同样受 `cooldownMultiplier` 缩放；`shareOwnerMana` 打开时扣主人的法力（`manaCostMultiplier` 缩放），主人在线才检查；坐下时能学卷轴但不施法。
- **宠物护主**：主人被攻击时，`engageRange` 内没坐下的宠物会反击攻击者；`pets.aggressive` 为真时宠物还会主动索敌最近的敌对生物。
- `/spellmob dump spells` 导出的法术列表仍可用于挑选卷轴，不用再猜 id。

### 文档

- 中文/英文指南与 README 新增“宠物学习卷轴”章节、`[scrollLearning]` 与 `[pets]` 配置表；中文提示文案与英文文案同步（学习成功、升级、已会、黑名单、冷却、上限、学习中断）。

---

## [2.1.2] — 2026-11-07

### 修复

- 学会引导类法术（`CONTINUOUS`）的 Boss 现在会像数据包里手写的引导法术一样每 10 tick 重放一次 `onCast`。此前这类法术只会引导满时长、在结束时结算一次，像持续拉取、持续伤害这类效果几乎看不出，和数据包定义的同类法术表现不一致。

---

## [2.1.1] — 2026-11-07

### 新增

- **会学习的 Boss（`[adaptive]`）**：带 `adaptive.adaptiveTag`（默认 `spellmob_adaptive`）标签的 Boss，在打败过某个玩家后，下次再遇到该玩家时随机学会对方上次用过的法术，并固定以最高等级释放。默认一生只学 1 个（`maxLearnedSpells`，0 = 不学），每场最多记 `rememberedSpells` 个，冷却默认跟随法术自身（`learnedCooldown = -1`）并受 `cooldownMultiplier` 缩放，优先级 `learnedPriority`，`spellBlacklist` 默认排除传送、血步、闪避。学习状态存在生物 NBT 里，`/reload` 不会丢。

### 文档

- 指南、README、CHANGELOG 补充会学习 Boss 的配置表、用法与测试标签命令。

---

## [2.1.0] — 2026-11-07

### 新增

- **全局冷却 `balance.globalCooldown`**：一次施法开始后，该施法者的所有法术都要再等这么多 tick 才能起手，用来防止一轮爆发把整张法术表倒出来。单位 tick，默认 `0`（关闭），上限 400。
- **`/spellmob dump spells`**：把当前加载的所有法术 id、施法类型、学派、是否启用导出到 `config/spellmob-spells.txt`，写数据包时可以直接查，不必再猜 id 拼写。
- **JSON Schema**：新增 `docs/spell_casters.schema.json`，在 VS Code / IntelliJ 里给 `spell_casters/*.json` 绑定后可以补全字段、标出拼错的键与类型错误。
- **血条显示施法**：`presentation.bossBarCastName`（默认开）打开时，Boss 血条的名字会在读条期间变成“名字 正在施放 xxx”，施法结束后自动恢复；关闭则只显示生物名字。

### 文档

- 指南与 README 补充 `globalCooldown`、`bossBarCastName` 两个配置项、`dump spells` 命令与 Schema 的用法。

---

## [2.0.9] — 2026-11-07

### 新增

- **数据包可逐个体覆盖血条**：spell_casters 文件的根节点或变体里可以写 `"bossBar": true/false`，优先于 `presentation.bossBar` 与标签判断；不写则沿用配置。用来让同一个物种的不同个体一个显示血条、一个不显示。
- **内置测试变体**：jar 内新增 `zombie.json`、`blaze.json`、`stray.json` 三个测试文件，按标签启用，未打标签的原版生物行为不变。僵尸：施法者状态（水中/着火/冰冻）+ 世界状态（夜晚/雷雨/满月/下界/白天晴天），其中 `spellmob_test_state` 显示血条、`spellmob_test_state_nobar` 不显示，用于对比；烈焰人：着火/岩浆；流浪者：冰冻/夜晚。

### 文档

- 指南、README 与模组介绍补充“优先级与原版 AI 的抢占”：列出僵尸/骷髅/烈焰人的原版攻击 Goal 优先级，说明 `priority: 1` 才能抢过近战、写成 2 会让生物只追人不施法，以及距离超出 `range` 时同样让位给近战。

---

## [2.0.8] — 2026-11-07

### 新增

- **变体阶段锁定 `sticky`**：给变体加 `"sticky": true` 后，它一旦命中就锁定，血量被治疗回阈值以上也不会退回上一阶段（继续掉血进入更深的阶段仍然可以）。默认 `false`，保持原来的双向行为；锁定状态写入存档，区块重载不会掉阶段，改名会重置。
- **Boss 血条**：`presentation.bossBar = true` 时，带 `presentation.bossBarTags` 标签（默认 `spellmob_boss`）的施法者会显示原版 Boss 血条。名字取显示名、进度取血量比例；`bossBarColor` 可选 `pink`/`blue`/`red`/`green`/`yellow`/`purple`/`white`，`bossBarRange`（默认 48）控制可见距离。与 `presentation.enabled` 独立，可以只开血条不开法阵；生物死亡或区块卸载时自动收起。

### 文档

- 指南与 README 补充 `sticky`、`bossBar*` 配置项与“Boss 血条”一节。

---

## [2.0.7] — 2026-11-07

### 修复

- **变体里的 `dropRules` 不再被忽略**：以前解析器只读文件根节点的 `dropRules`，写在 `variants` 里的整段掉落都是死数据（内置 `husk.json` 的 Boss/精英掉落就属于这种）。
- **多条掉落规则可以同时生效**：以前是“第一条命中就忽略后面的”，两条共用同一个 `select` 时第二条永远不触发；现在所有命中的规则都会结算，掉落叠加。
- 只写 `dropRules`、不写任何施法规则的定义文件现在也能掉落（“战利品变体”），不再要求施法配置非空。

### 变更

- `dropRules` 可写在文件根节点（对该实体所有生物生效）或 `variants` 的某个变体里（只在该变体命中时生效）。
- 文档修正：数量字段是 `count` / `min` / `max`，不是 `minCount` / `maxCount`。

### 构建

- `build/libs` 里的产物现在带平台后缀（`-forge` / `-neoforge`），并新增 `verifyJar` 任务：构建时会检查 jar 内是否是本平台的 `META-INF/mods.toml`，两个分支共用工作目录时不会再误用另一个平台的 jar。

---

## [2.0.6] — 2026-11-06

### 新增

- **`conditions.world`**：世界时间（`day` / `night` / `dawn` / `dusk`）、天气（`clear` / `rain` / `thunder`）、月相（`0`–`7`）、维度（支持模组维度，如 `twilightforest:twilight_forest`）、难度、世界天数下限（`minDay`）。
- **`conditions.self`**：`onFire`、`inWater`、`inLava`、`frozen` 四类施法者状态，`true` 表示必须满足、`false` 表示必须不满足、不写表示不关心。
- 示例数据包 `examples/conditions_demo`（1.20.1，1.21.1 只需把 `pack_format` 改成 48）。

### 说明

- 世界条件只在起手前判定一次，不影响性能；`onDeath` 等强制施法同样受这些条件约束。
- 没有天空的维度（下界、末地）永远不会满足 `world.time`，请改用 `world.dimension`。

---

## [2.0.5] — 2026-11-06

### 新增

- **`POSITION + BEHIND_TARGET`**：瞄准目标背后（`relativeOffset.x` 距离，默认 2 格；`relativeOffset.y` 垂直偏移），适合让传送类法术落到玩家身后。
- **`target.placement`**：给 `POSITION` 的落点加安全检查，`avoidFluid` 拒绝任何液体，`avoidLava` 只拒绝岩浆；`RANDOM_IN_RANGE` 最多重试 8 次，`BEHIND_TARGET` 不安全时退回目标位置。
- `/spellmob validate` 新增黄色警告：`POSITION` 目标 + 投射物法术（id 含 missile/bolt/ball/icicle/lance/ray/skull… 且开启 `lineOfSight`）时提示“只会改朝向”，警告数与问题数一起计入汇总。

### 变更

- 文档与 README 把“位置模式”改称**“瞄准模式”**，明确写清 `POSITION` 只改变视线方向、不改变法术出生点：纯投射物只改朝向（打不穿墙），只有传送/岩浆炸弹/黑洞这类靠视线找落点的法术真正受影响，自施法无效。
- 内置 `minecraft:husk` 的一处 `icicle + POSITION/TARGET_ENTITY` 改回 `ATTACK_TARGET`，避免丢掉视线检测与 `filters`。
- 文档补充：`SELF` 表示“目标就是自己”，与是否有敌人无关（附改 `ATTACK_TARGET` 的示例）；`dropRules` 必须是根节点数组且只使用第一条命中的规则；`config/spellmob-common.toml` 可被 **Configured** 这类配置编辑模组读取，普通数值改完即时生效，`[species]` 规则文本需要 `/spellmob resync` 或重启。

### 修复

- `TargetResolver` 的随机落点现在按 `placement` 判定重试，不再直接命中液体里的点。

---

## [2.0.4] — 2026-11-05

### 新增

- **目标过滤 `target.filters`**：在目标类型之上再筛一层，支持 `entityTypes`（实体 id 白名单）、`entityTags`（实体类型标签）、`excludeTags`（计分板标签排除）、`excludeCasterTeam`（排除同队）、`ignoreCreative`（忽略创造/旁观）、`minHealthPercent` / `maxHealthPercent`（血量区间）、`requireNoShield`（只打没有格挡的目标）。
- **分组条件**：`conditions` 支持按来源分组的嵌套写法 `self{...}` / `target{...}`，旧的扁平写法继续有效（同名键以扁平写法为准）。
- **`select.tamed`**：按是否驯服筛选变体，驯服瞬间自动重同步，不需要 `/reload`。
- **`presentation.bossPoints`**：Boss 脚下外环单独配置点数（默认 32，范围 0–128）。

### 变更

- `presentation.points` 改为直接配置点数（0–128，默认 24），取代原来的 `density` 四档；`0` 表示不画普通圆环。
- 移除 `general.castSounds`：施法音效完全由 Iron's Spellbooks 播放，模组不再重复播放起手音与结束音，这个开关已无法影响任何声音。
- Boss 特效回到“脚下大环 + 周期性脉冲 + 释放冲击波 + 打断烟雾”的形态，头顶符环与光柱不再出现。

### 修复

- 施法音效重复播放：Iron's Spellbooks 的 `onServerPreCast` 与 `onCast` 已经播放起手音与结束音，模组此前又各播了一遍；现在删掉重复播放，音效完全交给 ISS。
- 施法途中死亡时死亡法术会被吞掉：现在先用 `CASTER_DIED` 收尾当前施法，再正常释放 `onDeath`。
- `onDeath` / `onKill` 的强制施法会绕过 `entityBlacklist` 与 `dimensionBlacklist`，与“永不施法”的约定冲突；现在同样检查这两个名单。
- 创造模式玩家不再被硬性排除：改由 `target.filters.ignoreCreative`（默认 `true`）控制，旁观者仍然始终排除。
- 内置示例 `minecraft:drowned` 里的 `irons_spellbooks:thunder_step` 并未注册，改用已注册的 `irons_spellbooks:charge`。
- 移除无人调用的 `ISpellCaster.clear()` / `SpellCaster.clear()`，并修正若干过时注释。

---

## [2.0.3] — 2026-11-03

### 新增

- **五名召唤限定的内置 Boss**，全部打包进 jar，野生同类不受影响（靠 `spellmob_boss` + 各自专属标签识别）：
  - 娜迦（`twilightforest:naga`）：全部自然系法术
  - 巫妖（`twilightforest:lich`）：召唤恼鬼、尖牙之环、苦力怕之环、迟缓、星海落瀑、凋零之首、血吸光束等
  - 雪怪首领（`twilightforest:alpha_yeti`）：全部冰系法术
  - 溺尸「jack」（`minecraft:drowned`）：全部雷电系法术
  - 幻翼（`minecraft:phantom`）：魔法飞弹、电刑、雷鸣长枪、雷暴云、音爆、呼啸之风、龙息等
  - 统一 300 点生命、引导类法术 3 秒、冷却 5–12 秒
- 法阵半径倍率 `ringScale`（0.25–4.0），可按生物体型放大或缩小法阵。

### 变更

- 召唤类法术（`summon_vex`、`summon_swords`、`summon_polar_bear`、`summon_horse`、`raise_dead`、`raise_hell`）统一改为 `ATTACK_TARGET`：**没有攻击目标时不会召唤**，避免怪物挂机时凭空叫小弟。
- 法阵点数改为直接配置 `points`（0–128），取代原来的 `density` 四档；0 表示不画圆环。
- 默认内置双尸壳示例（`husk_boss` 标签为 Boss 版、`husk_elite` 为精英版，各 12 种法术），进游戏即可战斗。

### 修复

- 1.20.1 版不再直接引用 ISS 的 `ParticleRegistry` 字段：ISS 3.16.x 把字段类型从 `RegistryObject` 改成 `Supplier`，旧写法会抛 `NoSuchFieldError` 导致崩溃。
- 表现层异常改为吞掉 `Throwable`，任何粒子问题都不再中断施法。
- 内置 `husk.json` 引用了 1.20.1 不存在的 `ice_spikes`，已改为 `icicle`。
- 修正 1.20.1 版内置数据里几个 1.21.1 才有的法术 id（`raise_hell` → `raise_dead`、`fang_swirl` → `fang_strike`、`gravity_fissure` → `black_hole`）。
- **Boss 法阵的脚底环改为学派色 dust**：普通施法、吟唱中与完成时都不再叠加烟雾外环（1.20.1 之前因为换成原版 `SMOKE` 而整圈发黑）；被打断时的烟雾环保留。

### 性能

- 只为“有施法配置的实体类型”创建施法者数据（能力/附件），避免全服每只牛、羊、村民每 tick 分配一份冷却表。
- 施法者数据与 ISS 的 `MagicData` 均改为首次施法时才创建。
- 生物进入区块时先查配置再同步 Goal，没有配置的实体不再复制一遍 Goal 列表。


## [2.0.0] — 2026-10-31

**完全重写。**

### 新增

- 数据驱动：`data/<命名空间>/spell_casters/<实体 id>.json`，任意生物都能通过数据包学会法术。
- 五种触发器：`goals`（周期施法）、`onHurt`（受伤）、`onTarget`（锁定目标）、`onDeath`（死亡）、`onKill`（击杀）。
- 15 种目标类型（当前目标、最近敌对、最近亡灵、主人、自己、坐标……）与 5 种位置模式（相对、视线方向、随机落点……）。
- 变体系统：同一个生物文件里按名字、正则、标签、血量阶段区分不同个体，`override` 决定覆盖还是合并。
- 掉落规则 `dropRules`：只让会施法的特殊怪掉落自定义战利品，支持掉落表与抢夺加成。
- 施法表现：脚下圆环法阵、学派配色、引导进度可视化、被打断时的烟雾反馈。
- 名字锁：带保护标签的生物无法被玩家用命名牌改名（OP 可放行）。
- `[species] extraRules`：在配置文件里直接写物种级规则，无需数据包。
- 命令：`/spellmob list`、`/spellmob info`、`/spellmob resync`、`/spellmob validate`。

### 修复（相对 1.x）

- 配置系统从未注册、所有配置项实际不生效。
- 施法 Goal 被原版近战 Goal 饿死，生物几乎从不施法。
- `/reload` 不更新已加载生物，且移除法会永久锁死 Goal 旗标。
- `POSITION` 目标解析成自己，位移类法术固定朝一个方向。
- 施法减速写进基础移速，每次施法都会越走越慢。
- 性能监控的调度器关闭后无法重启，退出存档再进会报错。
- 命中率统计分母错误，缓存反而比直接计算更慢。
- `maxHealthPercent` 等字段解析了却从未生效。

---

## [1.0.0] — 2026-10

初版：通过数据包让任意生物施放 Iron's Spells 法术。
