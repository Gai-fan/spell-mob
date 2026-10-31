# 数据包与示例（SpellMob 2.0.1 起）

模组本身**不附带任何数据包**：只装模组，世界里所有生物的行为保持不变。
需要效果时，从本目录挑一份装进世界。

## 目录

| 目录 / 文件 | 内容 | 影响范围 |
|---|---|---|
| `classic/` | 早期内置的五个示例：僵尸 A/B、骷髅、狼、村民、尸壳 | 骷髅 / 狼 / 村民 / 尸壳作用于**全部同类**；僵尸只作用于命名 A/B |
| `bosses/` | 六个「名字 + tag」双门控 Boss | 只影响被命名的个体 |
| `channel_test/` | 引导型（CONTINUOUS）法术测试，掠夺者 + 卫道士 | 全部同类，仅供测试 |
| `full-reference.json` | 字段最全的参考 JSON（不是数据包） | — |

## 安装任意一份

```text
saves/<存档名>/datapacks/<包名>/
├── pack.mcmeta
└── data/<namespace>/spell_casters/<entity_id>.json
```

进游戏执行 `/reload`，日志出现 `[SpellMob] Loaded N spell caster definition(s)` 即生效。

## 注意

- `classic/` 与 `bosses/` 都带 `husk.json`，同时安装时 `bosses/` 会覆盖前者；
- 示例包会改变存档里同类生物的行为，建议先备份世界，或在测试存档里试；
- 服务端安装即可，客户端无需安装本模组。
