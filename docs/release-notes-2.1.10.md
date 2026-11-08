# SpellMob 更新日志汇总（2.0.6 → 2.1.10）

> 上一个公开发行版是 2.0.5。下面按主题汇总这之间所有版本的变化；逐版本的细节见 `CHANGELOG.md`。
> 两个发行版（Forge 1.20.1 / NeoForge 1.21.1）功能一致、版本号同步。

## 数据包能力

- **目标筛选 `target.filters`（2.1.0）**：`entityTypes`、`entityTags`、`excludeTags`、`excludeCasterTeam`、`ignoreCreative`、`minHealthPercent`、`maxHealthPercent`、`requireNoShield`；被筛掉的候选不会消耗冷却。
- **嵌套条件（2.1.0）**：`conditions.self` / `conditions.target` 分组，旧的扁平键继续可用。
- **瞄准模式与落点合法性（2.0.5）**：新增 `BEHIND_TARGET`；`placement: { avoidFluid, avoidLava }` 让随机落点避开液体/岩浆；文档把“位置模式”改名为“瞄准模式”，明确它只改朝向、不改法术起点。
- **世界与施法者条件（2.0.6）**：`conditions.world`（`time`/`timeOfDay`、`weather`、`moonPhase`、`dimension`、`difficulty`、`minDay`）与 `conditions.self`（`onFire`/`inWater`/`inLava`/`frozen`）。
- **阶段锁定 `sticky`（2.0.8）**：变体命中后血量回升也不退回，用于 Boss 分阶段；Goal 重建时同样遵守锁。
- **掉落修复（2.0.7）**：`dropRules` 现在可以写在根节点**或**变体里；所有命中的规则都会结算（不再 first-match-wins），只写掉落的“战利品变体”也能生效。
- **`bossBar` 数据包覆盖（2.0.9）**：变体或 `select` 里写 `bossBar: true/false` 可单独开关某只 Boss 的血条。

## Boss

- **Boss 血条（2.0.8）**：颜色、范围、标签可配，默认只对 `spellmob_boss` 生效；可显示“正在施放 xxx”。
- **会学习的 Boss（2.1.1 / 2.1.9）**：Boss 记住交战玩家的法术，玩家败给它之后，下次交手随机学一个（永远最高等级）；数量上限、记忆条数、冷却、优先级、黑名单都可配。2.1.9 起可在变体里直接写 `"adaptive": true`，不必再手动打标签。
- **战后重置（2.1.8）**：玩家离开一定范围且超过宽限时间后，Boss 回满血、清阶段锁与目标（保留已学法术）。
- **内置 `king`（2.1.10）**：命名为 `king` 的铁傀儡会放音爆 + 火焰吐息，带血条并会学习玩家法术。

## 宠物

- **卷轴教学（2.1.3）**：把法术卷轴丢到已驯服宠物脚下，宠物会开法阵并学法术；冷却中/黑名单/已满会有字幕提示，黑名单法阵变黑；中断不消耗卷轴。
- **`learner` 字段（2.1.4）**：变体写 `"learner": true` 等价于 `spellmob_learner` 标签。
- **护主与主动索敌（2.1.3）**：主人被打时宠物反击；`pets.aggressive` 可让宠物主动索敌。
- **法力与冷却（2.1.3 / 2.1.5）**：宠物施法可扣主人法力（`shareOwnerMana`、`manaCostPercent`、`manaCostMultiplier`）；学来的法术按 `clamp(法术CD×倍率, 下限, 上限)` 计算冷却；`castPriorityOverrides` 可按生物类型单独设优先级。
- **升级螺旋（2.1.5）**：学同名更高等级法术时，法阵上叠加螺旋上升粒子。
- **`/spellmob resetcooldown`（2.1.5）**：重置宠物学习冷却，方便测试。

## 工具与命令

- **全局 GCD（2.1.0）**：`balance.globalCooldown`，默认关闭，防止一轮爆发糊脸。
- **`/spellmob dump spells`（2.1.0）**：把当前注册的法术导出到 `config/spellmob-spells.txt`（id、施法类型、学派、是否启用）。
- **JSON Schema（2.1.0）**：`docs/spell_casters.schema.json`，编辑器可补全与校验。
- **`/spellmob validate` 增强（2.0.5）**：POSITION + 投射物会给出警告，提示“只改朝向”。

## 修复

- 施法音效重复播放（ISS 基类已经播过一次）。
- 死亡法术被在途引导吞掉；强制施法绕过实体/维度黑名单。
- 创造模式玩家被硬性排除；`ignoreCreative` 现在真正生效。
- 示例里的无效法术 id（`thunder_step` → `charge`）。
- 学来的法术 Goal 在 `CastGoalSync` 里被早退丢掉。
- 粘性阶段在 Goal 重建、血条、`learner` 判定上不一致。
- 施法期间被原版“浮水/逃跑”Goal 打断。
- 关闭卷轴教学后投掷队列只增不减；仪式中断时卷轴被吞。
- 普通宠物被误判为能学法术而分配施法者状态。
- 构建产物跨平台污染：`archiveClassifier` 区分 `forge` / `neoforge`，并加 `verifyJar` 断言 jar 内的加载器元数据正确。

## 兼容性

- Forge 1.20.1 + Iron's Spells `[1.20.1-3.4.0.9,)`
- NeoForge 1.21.1 + Iron's Spells `1.21.1-3.16.3`
- 数据包 `pack_format`：1.20.1 = 15，1.21.1 = 48
