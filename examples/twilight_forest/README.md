# 暮色森林测试数据包

用来验证 **模组生物** 能不能被 SpellMob 正常接管施法。

## 内容

| 文件 | 生物 | 类型 | 测试点 |
|---|---|---|---|
| `lich.json` | 巫妖王 | 人形 Boss | 人形 + 亡灵 + 半血换招 |
| `naga.json` | 娜迦 | 非人形 Boss | 长条碰撞箱 + 引导毒息 |
| `snow_queen.json` | 冰雪女王 | 飞行 Boss | 飞行 + 悬停施法 + 位移传送 |
| `hydra.json` | 九头蛇 | 超大 Boss | 大体积距离判定 + 引导火息 |
| `ur_ghast.json` | 凄凉恶魂 | 飞行 Boss | 远距离目标 + 黑洞 |
| `skeleton_druid.json` | 骷髅德鲁伊 | 普通怪 | 非 Boss + onHurt/onTarget |

## 安装

把整个文件夹（含 `pack.mcmeta` 和 `data/`）放进：

```
saves/<世界名>/datapacks/spellmob_twilight/
```

然后进游戏 `/reload`。日志里应该看到 `[SpellMob] Loaded N spell caster definition(s)`，N 会包含这 6 个。

## 快速测试

```mcfunction
# 巫妖王（带名字方便用 inc 找）
/summon twilightforest:lich ~ ~ ~10 {CustomName:'"测试巫妖"',PersistenceRequired:1b}

# 娜迦
/summon twilightforest:naga ~ ~ ~10 {CustomName:'"测试娜迦"',PersistenceRequired:1b}

# 冰雪女王
/summon twilightforest:snow_queen ~ ~ ~15 {CustomName:'"测试女王"',PersistenceRequired:1b}

# 看一眼有没有挂上配置
/spellmob info @e[type=twilightforest:lich,limit=1,sort=nearest]
```

想看得更清楚：`config/spellmob-common.toml` 里 `[general] debugLogging = true`，日志会打出每次施法开始和引导重放。

## 已知的测试点

- 暮色 Boss 有自己的阶段和伤害上限，SpellMob 的 `healthBelow` 变体是在**它的血量**上判定的，两者会叠加，属正常现象。
- 飞行生物如果引导时还在移动，`cone_of_cold` / `fire_breath` 这类锥形法术的方向会跟着转头走。
- 如果某只 Boss 完全不施法，先 `/spellmob info` 看是不是没匹配到（可能实体 id 和文件名不一致）。
