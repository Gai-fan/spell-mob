# SpellMob Showcase

一份可直接发布的**综合性示例数据包**：让 15 种原版生物获得风格各异的施法能力，用来展示 SpellMob 的全部数据驱动特性。

## 前置

| 项目 | 要求 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x |
| Iron's Spells 'n Spellbooks | `1.21.1-3.16.3` 或更高 |
| SpellMob | `2.0.0` 或更高 |

## 安装

1. 把本压缩包解压后的文件夹（或 zip 本身）放进：
   - 单人：`saves/<存档名>/datapacks/`
   - 服务器：`<世界目录>/datapacks/`
2. 进世界执行 `/reload`（或重启服务器）。
3. 日志出现 `[SpellMob] Loaded 15 spell caster definition(s)` 即生效。

> 数据包是**世界级**的：换存档需要重新放一份。

## 内容一览

| 生物 | 风格 | 关键机制 |
| --- | --- | --- |
| 僵尸 `zombie` | 腐化学徒 | 远程魔法飞弹、减速；受伤自愈；血量低于 40% 进入狂暴阶段（更高等级 + 牙袭） |
| 尸壳 `husk` | 沙丘行者 | 远程灼烧；死亡时朝视线方向炸出火焰 |
| 骷髅 `skeleton` | 白骨咏唱者 | 箭雨、锁定减速；半血转闪电；带 `spellmob_elite` 标签变精英 |
| 流浪者 `stray` | 霜冻射手 | 冰锥、冰刺；受伤释放冰霜波 |
| 溺尸 `drowned` | 溺亡祭司 | 毒液溅射、鲜血飞针；死亡留下毒池 |
| 蜘蛛 `spider` | 织网者 | 定身根须；被锁定时喷毒；受伤加速 |
| 洞穴蜘蛛 `cave_spider` | 毒牙 | 近距离毒液；死亡爆毒 |
| 掠夺者 `pillager` | 劫掠咏唱者 | 魔法箭狙最远敌人、魔法飞弹、半血开盾；受伤闪避；掉落绿宝石 |
| 卫道士 `vindicator` | 斧咏者 | 牙袭、血斩；对半血以下目标处决；半血以上锁定加速 |
| 唤魔者 `evoker` | 唤魔领主 | 连锁闪电、召唤恼鬼；半血对目标脚下开黑洞；死亡仍召唤 |
| 女巫 `witch` | 沼泽老妪 | 引导型毒息（受伤打断）、穿墙凋零；受伤自愈；击杀后加速 |
| 凋灵骷髅 `wither_skeleton` | 凋灵骑士 | 凋灵之首与鲜血飞针共享冷却组；击杀自愈；掉落煤炭与 5% 头颅 |
| 烈焰人 `blaze` | 烈焰咏唱者 | 火球与火焰吐息共享冷却组；受伤爆炸 |
| 末影人 `enderman` | 虚空行者 | 魔法箭；受伤随机传送 |
| 铁傀儡 `iron_golem` | 铁卫 | 地震；受伤自我强化 |

## 这份数据包演示了哪些特性

- **五种触发器**：`goals`（周期性）、`onHurt`、`onTarget`、`onDeath`、`onKill`
- **目标筛选**：`ATTACK_TARGET` / `NEAREST_HOSTILE` / `SELF` / `POSITION` 等，配合 `sort`（最远、血量最低）、`maxHealthPercent`、`lineOfSight`
- **位置模式**：`EYES_LOOK`、`FEET_LOOK`、`TARGET_ENTITY`、`RANDOM_IN_RANGE`、`RELATIVE`
- **条件**：`healthBelow` / `healthAbove` / `minTargetDistance` / `maxTargetDistance`
- **节奏控制**：`cooldown`、`chance`、`checkInterval`、`castTime`、`cooldownGroup`、`interruptOnDamage`
- **阶段变体**：僵尸狂暴、骷髅半血、唤魔者半血
- **标签变体**：骷髅 `spellmob_elite`
- **掉落规则**：掠夺者绿宝石、凋灵骷髅煤炭与头颅

## 兼容性

- 本包会让**所有**对应生物施法（不只是命名怪），因此适合"怪物强化"整合作风；如果只想让特殊怪施法，请参考 `examples/bosses` 的写法（按名字/标签门控）。
- 不要与 `examples/classic` 同时安装：两者定义同一批实体，后加载者覆盖前者。
- 与其它修改 `spell_casters/` 同路径文件的数据包互斥，按数据包优先级覆盖。

## 自定义

字段说明见主模组文档：

- 中文：`docs/guide.zh.md`
- English: `docs/guide.en.md`

改完后 `/reload` 即可，无需重启。
