# boss_test 数据包（SpellMob 测试用）

放法：把整个 `boss_test` 文件夹丢进存档的 `datapacks/`，进游戏 `/reload` 后 `/spellmob resync`。

## 实体

| 实体 | 名字 | 法术 | 说明 |
| --- | --- | --- | --- |
| 铁傀儡 | king | 音爆（sonic_boom，3 级）、火焰吐息（fire_breath，3 级） | 变体上带 `bossBar: true`；需要 `spellmob_boss` 标签才有红色血条，需要 `spellmob_adaptive` 标签才会学习玩家法术 |
| 鹦鹉 | summer | 魔法飞弹（magic_missile，2 级）、雷鸣长枪（lightning_lance，2 级） | 未驯服变体也放法术；驯服后变体带 `learner: true`，可以喂卷轴学新法术 |
| 马 | rocky | 超负荷（charge，2 级）、冰霜射线（ray_of_frost，2 级） | 同上，驯服后可学习 |
| 僵尸 / 尸壳 | spawner_fire、spawner_ice | 火焰箭（firebolt）、冰锥（icicle） | 给刷怪笼用：刷出来的怪带对应名字就会放指定法术 |

## 召唤命令

```
/summon minecraft:iron_golem ~ ~ ~ {CustomName:'"king"',Tags:["spellmob_boss","spellmob_adaptive"]}
/summon minecraft:parrot ~ ~ ~ {CustomName:'"summer"'}
/summon minecraft:horse ~ ~ ~ {CustomName:'"rocky"'}
```

刷怪笼（每 5 秒刷一只会火焰箭的僵尸，初始化后就不用手动放了）：

```
/setblock ~ ~ ~ minecraft:spawner{SpawnData:{entity:{id:"minecraft:zombie",CustomName:'"spawner_fire"'}},SpawnCount:1,SpawnRange:4,Delay:20,MinSpawnDelay:100,MaxSpawnDelay:100,RequiredPlayerRange:16} replace
```

## 备注

- 红色血条是**全局配置**（`config/spellmob-common.toml` 里 `bossBar = true`、`bossBarColor = "red"`），不是数据包字段；改完要重启游戏。
- 学习能力：Boss 要打死玩家一次（或玩家死在它 48 格内）才算“失败”，下次交手才会从你上次用过的法术里随机学一个；黑名单/数量上限/冷却都在配置里。
- 驯服后的宠物才会学法术；`learner: true` 写在变体上等价于给这只怪打 `spellmob_learner` 标签。
