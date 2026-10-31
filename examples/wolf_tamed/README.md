# 驯服狼（wolf_tamed）

演示 `select.tamed`：野生狼完全保持原版行为，被玩家驯服后才解锁法术；取消驯服又会立刻回到不施法状态。

## 安装

把本目录整体放到存档的数据包目录，或用压缩包形式导入：

```
saves/<存档名>/datapacks/wolf_tamed/
├── pack.mcmeta
└── data/minecraft/spell_casters/wolf.json
```

进世界后执行 `/reload`，看到 `[SpellMob] Loaded N spell caster definition(s)` 即生效（N 包含本包的 1 份）。

## 行为

| 状态 | 法术 |
|---|---|
| 野生狼 | 不施法（文件里没有任何顶层默认，`variants` 全部要求 `tamed: true`）。 |
| 已驯服，血量 ≥ 35% | 近身 `fang_strike`（6 格内）、远距离 `magic_missile`（5 格以外）；受伤时 `fortify` 给自己和 8 格内的主人；锁定目标时 `slow`；击杀后 `haste`。 |
| 已驯服，血量 < 35% | 优先 `heal` 自愈、`shield` 套盾，战斗法术暂时让位（优先级 0）。 |

驯服瞬间会自动重同步目标，不需要 `/reload`；`shield` 与 `fortify` 都是 10 tick 吟唱且受伤会打断。

## 测试

```mcfunction
/summon minecraft:wolf ~ ~ ~3 {CustomName:'"看门狼"',CustomNameVisible:1b,PersistenceRequired:1b}
```

先用骨头驯服，再 `/spellmob info @e[type=wolf,limit=1,sort=nearest]` 查看匹配到的变体与法术；把狼打到半血以下可以观察它切到保命阶段。

## 注意

- 本包的 `wolf.json` 与 `examples/classic` 里的同名文件冲突，同一存档只应启用其中一个（数据包按加载顺序后者覆盖前者）。
- 变体从上往下匹配，第一条命中即生效；`tamed: true` 必须写在 `tamed: false` 之前（本包只用了 `true`，所以不受影响）。
