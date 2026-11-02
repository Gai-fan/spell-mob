# 暮色森林首领包（1.21.1）

以暮色森林的**普通生物**为基础、用 `variants` 门控的五个首领配置。野生同类不会施法，只有用命令召唤、名字与标签都对得上的个体才会启用。

## 安装

1. 把本目录打包成 zip（`pack.mcmeta` 在压缩包根目录），或直接复制目录；
2. 放进 `saves/<存档>/datapacks/spellmob_twilight_bosses/`；
3. 进游戏执行 `/reload`，日志出现 `[SpellMob] Loaded N spell caster definition(s)` 即生效。

## 首领一览

| 实体 | 显示名 | 建议血量 | 主题 | 法术数 |
| --- | --- | --- | --- | --- |
| `twilightforest:redcap_sapper` | 雷帽工兵·爆破大师 | 120 | 火焰爆发、召唤恼鬼、半血引导火息 | 17 |
| `twilightforest:stable_ice_core` | 霜核·执政官 | 110 | 冰霜控场、雷电、召唤北极熊 | 15 |
| `twilightforest:death_tome` | 噬页者·万卷 | 100 | 末影与邪术、黑洞与流星 | 16 |
| `twilightforest:wraith` | 哀嚎·血影 | 120 | 鲜血消耗、瞬移、虹吸引导 | 14 |
| `twilightforest:king_spider` | 蛛巢·女王 | 130 | 剧毒、束缚、地震 | 15 |

每个首领都有两个阶段（血量低于 50% 切换）、至少十四种法术、受击/锁定/击杀/死亡四类触发器，以及掉落规则。

## 召唤命令

暮色森林的普通生物基础血量很低，必须用 `Attributes` 把上限提上去，同时把 `Health` 设成同一个值。

```mcfunction
/summon twilightforest:redcap_sapper ~ ~ ~10 {CustomName:'"雷帽工兵·爆破大师"',CustomNameVisible:1b,Tags:["spellmob_boss","tf_boss"],PersistenceRequired:1b,Attributes:[{Name:"generic.max_health",Base:120.0}],Health:120.0f}
```

```mcfunction
/summon twilightforest:stable_ice_core ~ ~ ~10 {CustomName:'"霜核·执政官"',CustomNameVisible:1b,Tags:["spellmob_boss","tf_boss"],PersistenceRequired:1b,Attributes:[{Name:"generic.max_health",Base:110.0}],Health:110.0f}
```

```mcfunction
/summon twilightforest:death_tome ~ ~ ~10 {CustomName:'"噬页者·万卷"',CustomNameVisible:1b,Tags:["spellmob_boss","tf_boss"],PersistenceRequired:1b,Attributes:[{Name:"generic.max_health",Base:100.0}],Health:100.0f}
```

```mcfunction
/summon twilightforest:wraith ~ ~ ~10 {CustomName:'"哀嚎·血影"',CustomNameVisible:1b,Tags:["spellmob_boss","tf_boss"],PersistenceRequired:1b,Attributes:[{Name:"generic.max_health",Base:120.0}],Health:120.0f}
```

```mcfunction
/summon twilightforest:king_spider ~ ~ ~10 {CustomName:'"蛛巢·女王"',CustomNameVisible:1b,Tags:["spellmob_boss","tf_boss"],PersistenceRequired:1b,Attributes:[{Name:"generic.max_health",Base:130.0}],Health:130.0f}
```

## 注意事项

- **名字必须完全一致**：`select.name` 是精确匹配，改一个字都不会启用；改名后模组会自动重同步，不需要 `/reload`。
- **标签必须带 `spellmob_boss`**：它同时决定 Boss 法阵和引导打断免疫；缺了只会按普通施法者处理。
- 想调整血量，改命令里的 `Base` 与 `Health` 两处即可，数据包本身不控制血量。
- 本包与 `spellmob_twilight` 不冲突：两者使用的实体不同，可以同时安装。
- 五个首领的法术都来自 Iron's Spells 'n Spellbooks 1.21.1-3.16.3，低于该版本可能有法术缺失。
