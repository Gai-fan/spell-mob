# 经典示例数据包

这些是 SpellMob 早期内置的示例，现已改为手动安装——**模组本身不再自带任何数据包**，只装模组不会改变任何生物的行为。

## 影响范围（重要）

| 文件 | 内容 | 会影响谁 |
|---|---|---|
| `zombie.json` | 只有命名为 `A` / `B` 的僵尸分别放火球 / 闪电枪；`A` 半血后换冰刺 | 只影响被命名的个体 |
| `skeleton.json` | 箭雨 + 受伤传送 | **所有骷髅** |
| `wolf.json` | 尖牙护盾 + 治疗主人 | **所有狼** |
| `villager.json` | 未命名村民放治疗；命名为 `C` / `D` 的不施法 | **所有村民** |
| `husk.json` | 五类触发器 + 五种筛选条件的全量参考 | **所有尸壳** |

后四个会改变存档里全部同类生物的行为。只想看效果的话，建议装在单独的测试存档，或者先按名字 / tag 把条件改窄。

## 安装

把整个 `examples/classic/` 复制到：

```
saves/<存档名>/datapacks/spellmob_classic/
```

目录里应包含 `pack.mcmeta` 与 `data/`。进游戏执行 `/reload`，日志出现 `[SpellMob] Loaded N spell caster definition(s)` 即生效。
