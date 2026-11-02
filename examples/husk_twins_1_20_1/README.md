# 双尸壳展示包（1.20.1）

同一份 `husk.json` 里用两个**标签变体**做出两种尸壳，野生尸壳没有标签，因此不会施法。

- **沙海王·阿努比斯**（Boss）：`husk_boss` + `spellmob_boss`
- **沙海卫兵**（精英）：`husk_elite`

## 法术清单

| 类型 | Boss（12 种） | 精英（12 种） |
| --- | --- | --- |
| 瞬发弹体 | fireball | firebolt、magic_missile |
| 吟唱 | lightning_lance | thunderstorm |
| 引导（连续） | cone_of_cold | poison_breath |
| 范围 | ice_spikes | magma_bomb、telekinesis |
| 控制 | root | slow |
| 位移 | teleport（随机落点） | —（受伤时向身后闪 4 格） |
| 召唤 | summon_vex | raise_dead |
| 连锁 / 多段 | chain_lightning | fang_strike |
| 斩杀 | wither_skull | — |
| 治疗 | heal（血量 <60%） | greater_heal（血量 <50%） |
| 增益 | haste | fortify |
| 防御 | shield | evasion |

两套法术**完全不重复**（共 24 种），并覆盖了五种触发器：`goals`、`onHurt`、`onTarget`、`onDeath`、`onKill`。

## 召唤指令

```mcfunction
/summon minecraft:husk ~ ~ ~5 {CustomName:'"沙海王·阿努比斯"',CustomNameVisible:1b,Tags:["husk_boss","spellmob_boss"],PersistenceRequired:1b,Attributes:[{Name:"generic.max_health",Base:300.0}],Health:300.0f}
/summon minecraft:husk ~ ~ ~5 {CustomName:'"沙海卫兵"',CustomNameVisible:1b,Tags:["husk_elite"],PersistenceRequired:1b,Attributes:[{Name:"generic.max_health",Base:300.0}],Health:300.0f}
```

## 注意

- 带 `spellmob_boss` 的 Boss 会多出一面竖立六芒星、引导时免疫伤害打断、且无法被命名牌改名（`protectedTags` 需在配置里包含 `spellmob_boss`）。
- 精英的引导（poison_breath）会被伤害打断，这是普通生物的默认行为。
- 装包后 `/reload` 一次；若之前装过别的 `husk.json` 数据包，同路径会互相遮蔽，只保留优先级最高的一份。
