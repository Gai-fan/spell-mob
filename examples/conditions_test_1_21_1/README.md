# 环境条件测试包（SpellMob 2.0.6+）

这个数据包只给**指定名字**的生物挂施法规则，野生同类不受影响。

## 安装

1. 把整个 `conditions_test_1_20_1` 文件夹放进存档的 `datapacks/` 里（文件夹名可以改，比如 `spellmob_conditions_test`）。
2. 进游戏执行 `/reload`，再执行 `/spellmob resync`。
3. 用 `/spellmob info` 对准生物右键，可以看它当前命中的是哪条规则、冷却多少。

本包已使用 1.21.1 的 `pack_format`（48）。

## 怎么召唤测试对象

名字用 `CustomName` 写死，两个加载器写法一样：

```
/summon minecraft:zombie ~ ~ ~ {CustomName:'"test-time"',CustomNameVisible:1b}
/summon minecraft:zombie ~ ~ ~ {CustomName:'"test-weather"',CustomNameVisible:1b}
/summon minecraft:zombie ~ ~ ~ {CustomName:'"test-moonphase"',CustomNameVisible:1b}
/summon minecraft:zombie ~ ~ ~ {CustomName:'"test-conditions"',CustomNameVisible:1b}
/summon minecraft:zombie ~ ~ ~ {CustomName:'"test-frozen"',CustomNameVisible:1b}
/summon minecraft:zombie ~ ~ ~ {CustomName:'"test-channel"',CustomNameVisible:1b}
/summon minecraft:husk ~ ~ ~ {CustomName:'"test-husk-day"',CustomNameVisible:1b}
/summon minecraft:husk ~ ~ ~ {CustomName:'"test-husk-rain"',CustomNameVisible:1b}
/summon minecraft:husk ~ ~ ~ {CustomName:'"test-husk-burning"',CustomNameVisible:1b}
/summon minecraft:blaze ~ ~ ~ {CustomName:'"test-blaze"',CustomNameVisible:1b}
/summon minecraft:strider ~ ~ ~ {CustomName:'"test-strider"',CustomNameVisible:1b}
/summon minecraft:enderman ~ ~ ~ {CustomName:'"test-enderman"',CustomNameVisible:1b}
/summon twilightforest:armored_giant ~ ~ ~ {CustomName:'"test-giant"',CustomNameVisible:1b}
/summon twilightforest:minotaur ~ ~ ~ {CustomName:'"test-minotaur"',CustomNameVisible:1b}
```

## 每个测试对象验证什么

| 名字 | 生物 | 法术 | 触发条件 | 怎么验 |
| --- | --- | --- | --- | --- |
| `test-time` | 僵尸 | 火球（2 级） | `night` 或 `dusk` | 白天靠近不施法，`/time set night` 后开始施法 |
| `test-weather` | 僵尸 | 闪电枪（2 级） | `thunder` | `/weather thunder` 后施法；`/weather clear` 后停 |
| `test-moonphase` | 僵尸 | 唤起亡灵（1 级） | 夜晚 + 月相 0（满月） | 满月当晚施法；`/time set day 1000` 换月相后不施法 |
| `test-conditions` | 僵尸 | 冰锥（3 级） | 夜晚 + 晴天 + 主世界 + 自身不燃烧/不在水/不在岩浆 | 站水里、身上着火、下雨、或去下界都不施法 |
| `test-frozen` | 僵尸 | 冰锥（2 级） | 自身被冰冻（细雪） | 推进细雪里才施法，离开就停 |
| `test-channel` | 僵尸 | 念力（引导 80 tick = 4 秒） | 夜晚 + 自身不在水 | 观察 4 秒的引导过程与打断反馈 |
| `test-husk-day` | 尸壳 | 寒冰锥（2 级） | `day` | 只在白天施法 |
| `test-husk-rain` | 尸壳 | 尖牙（2 级） | `rain`（下雨但没打雷） | `/weather rain` 施法；`/weather thunder` 不施法（打雷算 `thunder`） |
| `test-husk-burning` | 尸壳 | 火球（2 级） | 自身着火 | 用打火石点着它，着火期间施法 |
| `test-blaze` | 烈焰人 | 岩浆炸弹（2 级） | 自身在岩浆里**且**着火 | 推进岩浆或 `/setblock` 一池岩浆让它站进去 |
| `test-strider` | 炽足兽 | 火球（1 级） | 自身在岩浆里 | 岩浆里对最近的玩家施法（被动生物，测试用） |
| `test-enderman` | 末影人 | 传送（1 级） | 夜晚 + 自身不在水 | 站水里不施法，上岸后恢复 |
| `test-giant` | 暮色装甲巨人 | 凋灵之首（2 级） | 暮色森林维度 + 夜晚 | 只在暮色夜晚施法，主世界不施法 |
| `test-minotaur` | 米诺陶 | 连锁闪电（2 级） | 下雨 + 自身不燃烧 | 雨天施法，着火时不施法 |

## 设计取舍（为什么有些条件没测）

- 末影人怕水、炽足兽怕不了岩浆，所以这两只的 `inWater` / `inLava` 用“必须为真/为假”的反向写法，确保测试是**可达成**的。
- 召唤类法术（唤起亡灵）只挂世界条件，不挂 `self` 条件——召唤不关心施法者姿态。
- 引导法术 `telekinesis` 明确写 `castTime: 80`（4 秒），满足“引导至少 3 秒”。
- 所有冷却都不低于 60 tick（3 秒），实测节奏接近“每次施法间隔 ≥ 2 秒”。
- `difficulty`、`minDay` 这类条件在旧存档里几乎恒为真，写了也看不出差别，所以不放进测试包，文档里有写法。

## 恢复

```
/kill @e[type=minecraft:zombie,name="test-time"]
```

或者直接删掉 `datapacks/` 里的这个文件夹再 `/reload`。
