# 暮色森林施法示例（SpellMob · Minecraft 1.21.1）

给暮色森林（Twilight Forest 1.21.1）的八种生物加上 Iron's Spells 施法配置，用于测试与二次修改。

## 安装

1. 把整个 `twilight_forest_1_21_1` 文件夹复制到存档的 `datapacks/` 下，改名为 `spellmob_twilight`：
   `saves/<存档名>/datapacks/spellmob_twilight/`
2. 进世界执行 `/reload`。
3. 看到 `[SpellMob] Loaded N spell caster definition(s)` 即生效（N 包含本包 8 条）。

数据包内容只有 `pack.mcmeta` 与 `data/twilightforest/spell_casters/*.json`，不改动原版或其他模组的任何文件。

## 生物与法术

| 生物 | 定位 | 主要法术 |
|---|---|---|
| 巫妖 `lich` | 远程 + 控场 | 魔法飞弹、冰封、召唤恼鬼、瞬移；半血后箭雨、亡者复苏、霜波 |
| 娜迦 `naga` | 近身毒系 | 毒牙、毒液喷溅、缠根；半血开始引导毒息 |
| 冰雪女王 `snow_queen` | 冰系法师 | 冰锥、霜波、召唤冰熊、暴风雪；受击闪现；半血后引导冰锥 |
| 九头蛇 `hydra` | 火系炮台 | 岩浆弹、火球、焦灼；残血引导火息与炼狱 |
| 暮色恶魂 `ur_ghast` | 远程轰击 | 声波、魔法箭、重力裂隙；半血黑洞与连锁闪电 |
| 牛头人 `minotaur` | 冲锋战士 | 冲锋、践踏、毒牙；受伤硬化，锁定目标时减速 |
| 骷髅德鲁伊 `skeleton_druid` | 毒系辅助 | 毒液喷溅、缠根、萤火虫群；受伤自愈，锁定目标时加速 |
| 卡米奈特傀儡 `carminite_golem` | 火系坦克 | 焦灼、岩浆弹、硬化；受伤开盾 |

## 召唤测试

```
/summon twilightforest:lich ~ ~ ~10 {CustomName:'"巫妖"',CustomNameVisible:1b}
/summon twilightforest:naga ~ ~ ~10 {CustomName:'"娜迦"',CustomNameVisible:1b}
/summon twilightforest:snow_queen ~ ~ ~10 {CustomName:'"冰雪女王"',CustomNameVisible:1b}
/summon twilightforest:hydra ~ ~ ~10 {CustomName:'"九头蛇"',CustomNameVisible:1b}
/summon twilightforest:ur_ghast ~ ~ ~10 {CustomName:'"暮色恶魂"',CustomNameVisible:1b}
/summon twilightforest:minotaur ~ ~ ~10 {CustomName:'"牛头人"',CustomNameVisible:1b}
/summon twilightforest:skeleton_druid ~ ~ ~10 {CustomName:'"骷髅德鲁伊"',CustomNameVisible:1b}
/summon twilightforest:carminite_golem ~ ~ ~10 {CustomName:'"卡米奈特傀儡"',CustomNameVisible:1b}
```

想让它用 Boss 法阵，追加标签即可：
`Tags:["spellmob_boss"]`（同时会免疫伤害打断引导、且无法被命名牌改名）。

## 调整

- 每个文件顶层的 `goals` 是默认行为，`variants` 里按血量切换阶段（`healthBelow`）。
- 冷却与优先级都在每条法术里：`cooldown`（tick）、`priority`（数字越小越优先抢占）。
- 引导类法术（`cone_of_cold`、`poison_breath`、`fire_breath`、`blizzard` 等）可以加 `"interruptOnDamage": true` 让受击打断。
- 改完文件执行 `/reload` 即可，不需要重启游戏。
