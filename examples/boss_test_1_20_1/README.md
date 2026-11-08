# boss_test 数据包（SpellMob 测试用）

放法：把整个 `boss_test` 文件夹丢进存档的 `datapacks/`，进游戏 `/reload` 后 `/spellmob resync`。

## 实体

| 实体 | 名字 | 法术 | 说明 |
| --- | --- | --- | --- |
| 铁傀儡 | king | 音爆（sonic_boom，3 级）、火焰吐息（fire_breath，3 级） | 变体上带 `bossBar: true` 和 `adaptive: true`：有红色血条，且会学习打败过的玩家的法术 |
| 鹦鹉 | summer | 魔法飞弹（magic_missile，2 级）、雷鸣长枪（lightning_lance，2 级） | 未驯服变体也放法术；驯服后变体带 `learner: true`，可以喂卷轴学新法术 |
| 马 | rocky | 超负荷（charge，2 级）、冰霜射线（ray_of_frost，2 级） | 同上，驯服后可学习 |

## 召唤命令

```
/summon minecraft:iron_golem ~ ~ ~ {CustomName:'"king"',Tags:["spellmob_boss"]}
/summon minecraft:parrot ~ ~ ~ {CustomName:'"summer"'}
/summon minecraft:horse ~ ~ ~ {CustomName:'"rocky"'}
```

铁傀儡的 `spellmob_boss` 标签只影响法阵/Boss 特效的外观；学习能力由 JSON 里的 `adaptive: true` 提供，不需要再手动加 `spellmob_adaptive` 标签（老数据包也可以继续用标签）。

## 学法术的测试流程

1. 先让 king 打你（或者你打它），进入交战状态；
2. 你在这段时间里放几个法术（每个都会被记住，最多 `adaptive.rememberedSpells` 个）；
3. 让 king 把你打死（或死在它 48 格内）——这算一次「失败」；
4. 复活后回到它身边、再交手一次，它就会从你上次用过的法术里随机学一个，之后用最高等级释放。

学到的法术用 `learnedPriority`（默认 2）参与施法；铁傀儡自带近战 Goal 优先级是 1，会抢 MOVE，所以测试时我把 `config/spellmob-common.toml` 的 `learnedPriority` 改成了 0，让它先施法。换成别的 Boss 时按需调回 2 或 3。

## 备注

- 红色血条是**全局配置**（`config/spellmob-common.toml` 里 `bossBar = true`、`bossBarColor = "red"`），不是数据包字段；改完要重启游戏。
- 驯服后的宠物才会学法术；`learner: true` 写在变体上等价于给这只怪打 `spellmob_learner` 标签。
- 本包只定义了 king / summer / rocky 三个名字，其它同种生物行为不变；不要在这个包里放 `husk.json`/`zombie.json`，那会顶掉模组内置的同名规则。
