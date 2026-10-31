# Kitchen Sink（全条件参考）

这一份是**参考型**数据包，用来展示 SpellMob 配置可以写到多细，不建议直接放进正式存档。

## 内容

| 文件 | 实体 | 演示重点 |
| --- | --- | --- |
| `vindicator.json` | 卫道士 | 五种 `select` 条件、五种触发器、15 种目标类型、5 种位置模式、5 种排序、4 个 `conditions` 键、全部节奏字段、两条 `dropRules` |
| `pillager.json` | 掠夺者 | 按法术功能各取一个：瞬发/吟唱/引导、单体/范围/持续、控制/治疗/增益/防御/位移/召唤/连锁/斩杀/穿墙，以及阶段变体与掉落 |

## 测试方法

1. 把 `examples/kitchen_sink` 整个文件夹复制到 `saves/<存档>/datapacks/`。
2. `/reload`，日志应出现 `[SpellMob] Loaded 2 spell caster definition(s)`。
3. 召唤带标签的生物，例如：

```
/summon minecraft:vindicator ~ ~ ~10 {CustomName:'"典狱长"',CustomNameVisible:1b,Tags:["spellmob_demo"],PersistenceRequired:1b}
/summon minecraft:pillager ~ ~ ~10 {CustomName:'"风暴术士"',CustomNameVisible:1b,Tags:["spellmob_demo","storm"],PersistenceRequired:1b}
```

4. 用 `/spellmob info @e[type=minecraft:vindicator,limit=1,sort=nearest]` 查看命中的变体。

## 注意

- 卫道士文件里有**不按名字门控**的 `healthBelow` / `healthAbove` 变体，所以野生卫道士在血量低于 35% 或高于 80% 时也会施法。这是为了演示条件写法，正式使用时应把这两个变体也加上名字或标签条件。
- 两个文件都只覆盖 `minecraft:vindicator` 与 `minecraft:pillager`，不会影响其它生物。
- 与 `examples/classic`、`examples/bosses` 同时安装时，同一实体的文件以最后加载者为准。
