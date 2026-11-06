# 环境条件演示包（1.20.1）

演示 `conditions.world` 与 `conditions.self` 的用法。所有变体都用标签门控，**只有带指定标签的僵尸才会施法**，野生僵尸行为不变。

| 标签 | 触发关系 | 演示的条件 |
|---|---|---|
| `spellmob_demo_night` | 夜晚 / 黄昏 | `world.time` |
| `spellmob_demo_storm` | 雷雨天 | `world.weather` |
| `spellmob_demo_wet` | 泡在水里 | `self.inWater` |
| `spellmob_demo_burning` | 身上着火 | `self.onFire` |
| `spellmob_demo_moon` | 满月的夜晚 | `world.time` + `world.moonPhase` |

## 安装

把整个目录（`pack.mcmeta` + `data`）压缩成 zip，放进存档的 `datapacks/`，然后：

```mcfunction
/reload
/spellmob resync
```

## 试一下

```mcfunction
# 夜晚的火焰弹
/summon minecraft:zombie ~ ~ ~ {Tags:["spellmob_demo_night"]}
/time set night

# 雷雨天的闪电
/summon minecraft:zombie ~ ~ ~ {Tags:["spellmob_demo_storm"]}
/weather thunder

# 水里的冰锥（把僵尸放进水里）
/summon minecraft:zombie ~ ~ ~ {Tags:["spellmob_demo_wet"]}

# 着火时的火球
/summon minecraft:zombie ~ ~ ~ {Tags:["spellmob_demo_burning"]}

# 满月的夜晚（满月 = 世界天数为 8 的倍数）
/summon minecraft:zombie ~ ~ ~ {Tags:["spellmob_demo_moon"]}
/time set 18000
```

僵尸需要有一个玩家目标才会起手；站远一点等它出手即可。看不到效果时先跑 `/spellmob validate`，再用 `/spellmob info` 看选中的变体和来源。

## 说明

- `world.time` 只在有天空的维度有意义；下界、末地永远不满足时间条件，请改用 `world.dimension`。
- `self` 里的 `true` / `false` 是“必须满足 / 必须不满足”，删掉键表示不关心。
- 条件都在起手前判一次，不满足就跳过这次施法（不消耗冷却）。
- 本目录是 1.21.1 版（`pack_format` 48）；1.20.1 版请把 `pack_format` 改成 `15`。
