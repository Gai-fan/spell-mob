# filters 测试包

用来验证 2.1.0 新增的 `target.filters` 与 `minHealthPercent`。五个变体都靠计分板标签识别，
野生同类生物不受影响。

## 安装

把整个文件夹放到存档的 `datapacks/` 下（文件夹名随意），进游戏后 `/reload`。
1.20.1 的 `pack_format` 是 15，1.21.1 是 48，别混用。

## 召唤命令

```
/summon minecraft:zombie ~ ~ ~2 {Tags:["filter_execute"],CustomName:'"处刑者"',CustomNameVisible:1b,PersistenceRequired:1b}
/summon minecraft:zombie ~ ~ ~2 {Tags:["filter_knight"],CustomName:'"骑士"',CustomNameVisible:1b,PersistenceRequired:1b}
/summon minecraft:zombie ~ ~ ~2 {Tags:["filter_assassin"],CustomName:'"刺客"',CustomNameVisible:1b,PersistenceRequired:1b}
/summon minecraft:zombie ~ ~ ~2 {Tags:["filter_loner"],CustomName:'"独行侠"',CustomNameVisible:1b,PersistenceRequired:1b}
/summon minecraft:zombie ~ ~ ~2 {Tags:["filter_hunter"],CustomName:'"猎手"',CustomNameVisible:1b,PersistenceRequired:1b}
```

1.21.1 的召唤语法去掉 `Tags` 之外的 `CustomName` JSON 引号差异即可，`Tags` 写法相同。

## 预期行为

| 变体 | 过滤条件 | 怎么测 |
| --- | --- | --- |
| 处刑者 | `maxHealthPercent: 0.35` | 满血站在它面前：不施法；把自己打到 30% 血：它开始打你 |
| 骑士 | `minHealthPercent: 0.5` | 满血靠近：开火；血量低于 50%：它不再打你 |
| 刺客 | `requireNoShield: true` + `ignoreCreative: true` | 举盾靠近：不施法；放下盾：立刻施法；创造模式：完全不理 |
| 独行侠 | `excludeCasterTeam: true` | `/team add red`、`/team join red @s`、`/team join red @e[tag=filter_loner]` 后它不打你；退出队伍后恢复 |
| 猎手 | `entityTags: ["minecraft:raiders"]` + `excludeTags: ["spellmob_immune"]` | 旁边召唤劫掠者：它只打劫掠者；给劫掠者加 `spellmob_immune` 标签后停手 |

## 观察方式

- `/spellmob info @e[tag=filter_execute,limit=1]` 能看到当前生效的变体与冷却；
- 打开 `debugLogging` 后，`[SpellMob]` 日志会记录每次起手；
- 过滤发生在索敌阶段，被过滤掉的候选不会消耗冷却，所以“不打”时冷却应该一直是 0。

## 注意

- `requireNoShield` 判断的是**目标是否举盾**（右键格挡），不是背包里有没有盾；它只影响“选不选你”，
  不改变伤害结算，正对你的法术仍然会被盾牌挡下。
- 五个变体都写了 `"override": true`，所以它们不会继承任何物种默认配置。
