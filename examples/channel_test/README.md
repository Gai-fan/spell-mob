# 引导法术测试包（channel_test）

把 SpellMob 目前支持的 **9 个引导型（CONTINUOUS）法术**全部挂在同一只生物上，用来观察：

- 引导是否持续、每 10 tick 是否重放一次效果；
- 脚下法阵的颜色是否跟随法术所属学派（Ice 浅蓝 / Lightning 紫 / Blood 暗红 / Fire 橙 / Ender 品红 / Nature 绿 / Eldritch 深紫 / Holy 金）；
- 引导期间挨打会不会被打断（默认会，`spellmob-common.toml` 的 `interruptChannelsOnDamage`，带 `spellmob_boss` 标签的生物免疫）。

> 注意：`healing_circle`（治愈之环）在 ISS 3.16.3 里是 **LONG** 而不是 CONTINUOUS，所以不在这个测试包里——它是“读条一次、结束时放一个领域”，本来就不会重复生成。
>
> `cloud_of_regeneration`（再生之云）已从所有数据包移除：领域留在原地、怪一走动就废，不适合怪物施法。

## 安装

把整个 `channel_test` 文件夹放进存档的 `datapacks/` 下：

```
<存档>/datapacks/channel_test/pack.mcmeta
<存档>/datapacks/channel_test/data/minecraft/spell_casters/pillager.json
```

进入世界（新加的数据包需要重进世界，`/reload` 不一定能扫到新目录）。

## 召唤

```
/summon minecraft:pillager ~ ~ ~10 {CustomName:'"引导测试"',CustomNameVisible:1b,Tags:["channel_test"],PersistenceRequired:1b,Attributes:[{Name:"generic.max_health",Base:200.0}],Health:200.0f}
```

不拿弩的掠夺者不会射箭，只会站着轮流施法，方便观察。

## 看什么

- 每放完一个引导法术，下一个会立刻接上（冷却 60 tick 远短于一轮循环），大约 30–40 秒走完一轮；
- 如果想看某个法术的完整引导时长，把其它 `goals` 暂时删掉即可。

## 备注

- 法术清单（按 JSON 顺序）：`cone_of_cold`、`electrocute`、`ray_of_siphoning`、`blaze_storm`、`fire_breath`、`dragon_breath`、`poison_breath`、`starfall`、`telekinesis`。
- 删除这个包时，直接把 `datapacks/channel_test` 文件夹删掉并重进世界。
