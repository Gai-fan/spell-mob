## 简介

SpellMob 是 Iron's Spells 'n Spellbooks（下称 ISS）的附属模组。它不添加新的物品、方块、法术或实体，作用是把 ISS 已有的法术分配给任意生物，并为这些生物补充目标选择、施法节奏、阶段变化与掉落规则。最新版本为 2.0.5，Forge 1.20.1 与 NeoForge 1.21.1 各有一份发行版，功能一致。

## 数据包

模组以数据包作为唯一的内容入口：每个生物对应一份 JSON 文件，位于 `data/<命名空间>/spell_casters/<实体 ID>.json`，内容描述该生物在什么条件下、对什么目标、以什么节奏施放哪些法术。数据包在世界加载或执行 `/reload` 时解析。模组逻辑全部在服务端执行，联机时客户端无需安装。

数据包属于世界级资源，更换存档需要重新放置。1.20.1 的数据包格式为 15，1.21.1 为 48。

## 触发器

包含五类触发器：

- `goals`：周期性施法，生物空闲时按优先级与冷却尝试。
- `onHurt`：受到伤害时触发。
- `onTarget`：锁定目标时触发。
- `onDeath`：死亡时触发，以强制瞬发方式施放，忽略冷却与概率，但仍受条件与黑名单限制。
- `onKill`：击杀生物时触发。

每条触发器包含一个或多个法术条目，字段相同。事件触发器与 `goals` 共用同一个施法状态，吟唱期间触发的事件会被忽略。

## 目标

`target` 支持 15 种取值，包括 `ATTACK_TARGET`（当前攻击目标）、`LAST_DAMAGE_SOURCE`（最后伤害来源）、`NEAREST_HOSTILE`（最近的敌对生物）、`NEAREST_PLAYER`、`OWNER`（驯服生物的主人）、`SELF`、`POSITION` 等。

`target` 可写为字符串简写，也可写为对象，追加以下筛选：

- `sort`：多个候选目标时的排序，取值 `NEAREST`、`FARTHEST`、`LOWEST_HEALTH`、`HIGHEST_HEALTH`、`RANDOM`。
- `lineOfSight`：是否要求视线。
- `filters`：按实体 ID、实体类型标签、排除标签、队伍、生命值比例、是否举盾、是否创造模式筛选候选目标。
- `positionMode` 与 `relativeOffset`：当 `target` 为 `POSITION` 时决定朝向，支持 `RELATIVE`（自身位置加偏移）、`EYES_LOOK`（沿视线方向）、`FEET_LOOK`、`TARGET_ENTITY`、`BEHIND_TARGET`（目标背后）与 `RANDOM_IN_RANGE`（范围内随机点）。ISS 的施法接口不接收坐标，投射物始终从施法者身上生成，因此坐标目标只改变朝向，对自施法与增益类法术无效。
- `placement`：坐标目标的落点检查，可用 `avoidFluid` 与 `avoidLava` 避开液体与岩浆。

## 变体

`variants` 数组用于描述同一生物的不同个体或不同阶段，每个变体由 `select` 与数据组成。

`select` 支持 `name`（精确名称）、`nameRegex`（正则）、`tags`（计分板标签，需全部匹配）、`healthBelow` 与 `healthAbove`（生命值比例）以及 `tamed`（是否被驯服）。

变体按数组顺序匹配，第一条命中的变体生效。`override` 为 `true` 时完全替换顶层配置，为 `false` 时与顶层配置合并。生物名称变化时，模组会自动重新同步该生物的施法配置；标签变化不会自动同步，需要执行 `/spellmob resync`。

## 掉落

`dropRules` 数组为生物追加掉落。规则可以写在文件根节点（对整个实体生效），也可以写进某个变体（仅该变体命中时生效）；所有 `select` 命中的规则都会结算，支持规则级概率。条目可以是物品或原版战利品表，并可设置数量区间、概率、是否受抢夺影响、是否要求玩家击杀。只写掉落、不写施法规则也是合法的。

## 施法节奏

施法时长取自法术本身，也可在条目中用 `castTime` 覆盖。吟唱期间生物会转向目标并显示法阵。

引导型法术指 ISS 中 castType 为 `CONTINUOUS` 的法术，共 10 个；引导期间每 10 tick 结算一次效果，冷却从引导结束时开始计算。

打断条件包括目标死亡或超出射程、失去视线、受到伤害（由 `interruptOnDamage` 控制），以及法术自身要求停止施法。全局配置可统一打断引导型法术，带有 `channelImmuneTags` 标签的生物不受该规则影响。

法术条目的 `priority` 同时决定施法能否抢过原版 AI：施法占用移动与视线，默认值 `1` 可以抢在僵尸近战（优先级 2）、骷髅弓箭（优先级 4）之前；写成 `2` 或更大时，原版攻击 Goal 会一直占着移动权，生物只会追人、不会施法。给近战生物编写规则时保持默认值即可。

## 施法表现

表现全部由服务端发包。吟唱时生物脚下显示一圈尘粒子法阵，颜色取自法术所属学派的显示颜色，读条进度以法阵半径变化表示，受伤打断时附加烟雾与熄灭音效。带有 `bossTags` 标签的生物会额外显示一圈更大的外环并周期性向外扩散脉冲。表现层可在配置中关闭，或调整点数、半径倍率与可见距离。

## 法术条目字段

`spell`（必填，法术 ID）、`level`（1 至 10）、`cooldown`（tick）、`priority`（0 至 9）、`range`（格）、`castTime`（tick）、`checkInterval`（tick）、`chance`（0 至 1）、`cooldownGroup`（共享冷却标识）、`interruptOnDamage`、`repeatOnCast`、`target`、`conditions`（`healthBelow`、`healthAbove`、`minTargetDistance`、`maxTargetDistance`，也可写作 `self` 与 `target` 两组）。

## 命令

- `/spellmob list` 列出已加载的生物配置。
- `/spellmob info <目标>` 显示该生物当前命中的变体、法术与剩余冷却。
- `/spellmob resync [目标]` 重新解析并重建施法配置。
- `/spellmob validate` 校验已加载配置中的法术 ID 与冲突，并对坐标目标搭配投射物法术的情况给出提示。

以上命令需要权限等级 2。

## 配置

配置文件为 `config/spellmob-common.toml`，包含总开关、调试日志、冷却倍率、施法距离上限、实体黑名单、维度黑名单、名字保护标签、是否允许管理员改名、引导免疫标签、表现层参数与物种级规则。

物种级规则以 JSON 行的形式写入配置，格式与数据包条目一致。若数据包已定义同一生物，则以数据包为准，配置中的同名规则会被忽略并在日志中提示。

## 内置内容

模组自带双尸壳示例与五名召唤限定 Boss（娜迦、巫妖、雪怪首领、溺尸、幻翼），全部以标签限定，未打标签的野生生物保持原版行为。仓库的 `examples` 目录另提供经典、暮色森林与字段全开的测试示例。

## 前置与兼容

需要安装 ISS，1.20.1 一侧要求版本不低于 1.20.1-3.4.0.9。可选安装 Configured，用于在游戏内修改配置。模组不注册网络通道，不添加方块、物品与实体，客户端无需安装。

## 授权

本模组使用 MIT 协议，作者为盖饭君404。

- GitHub：<https://github.com/Gai-fan/spell-mob>
- Gitee：<https://gitee.com/gaifan-jun-404/spell-mob>
