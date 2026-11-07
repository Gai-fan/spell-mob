# SpellMob

**Data-driven spellcasting for any mob — no new content, no client mod, just JSON.**

SpellMob is an addon for *Iron's Spells 'n Spellbooks* (ISS). It adds no blocks, items, or entities: it binds ISS spells to any mob from vanilla or other mods through data packs. Casting goes through ISS's own pipeline with `CastSource.MOB`, so cast times, cast sounds and spell behaviour match ISS itself. Mobs do not spend mana and are not limited by ISS's own cooldowns — cooldowns are part of the data pack.

- Install the mod alone and nothing changes in your world.
- Server-side only: clients can join without the mod. Casting circles are vanilla particles sent by the server.
- Five trigger types, 15 target types, per-individual variants, health phases, shared cooldown groups, and special-mob drops.

## Requirements

| | |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.x |
| Iron's Spells 'n Spellbooks | `1.20.1-3.4.0.9` or newer (verified up to `3.16.3`) |

SpellMob itself has no other dependencies. If you run ISS 3.15+, install the extra dependencies ISS asks for (newer Forge, GeckoLib, Curios, `irons_lib`) — those are ISS's requirements, not SpellMob's.

## Installation

**1. The mod.** Drop `spellmob-<version>.jar` into `mods/`.

**2. A data pack (optional).** Put it under the world's `datapacks` folder:

```
saves/<world>/datapacks/my_spells/
├── pack.mcmeta
└── data/<entity namespace>/spell_casters/<entity path>.json
```

The folder path **must mirror the entity id** — the data pack's own name is irrelevant:

| Entity | File |
|---|---|
| `minecraft:zombie` | `data/minecraft/spell_casters/zombie.json` |
| `twilightforest:lich` | `data/twilightforest/spell_casters/lich.json` |
| `minecraft:zombie_villager` | `data/minecraft/spell_casters/zombie_villager.json` |

**3. Reload.** Run `/reload` in game. The log should show:

```
[SpellMob] Loaded N spell caster definition(s)
```

## Quick start

`data/minecraft/spell_casters/zombie.json`:

```json
{
  "goals": [
    {
      "spell": "irons_spellbooks:fireball",
      "level": 2,
      "cooldown": 200,
      "priority": 1,
      "range": 24,
      "castTime": 30,
      "target": "ATTACK_TARGET"
    }
  ]
}
```

Every zombie now casts a level-2 fireball with a 30-tick wind-up, every 200 ticks, at whatever it is attacking.

> Unknown keys are ignored silently, so a typo in a key name will not raise an error. Use `/spellmob validate` and `/spellmob info <mob>` to see what was actually loaded.

## Cast fields

Every cast object — inside `goals`, `onHurt`, `onTarget`, `onDeath`, `onKill`, or a variant — accepts the same fields.

| Field | Type | Default | Notes |
|---|---|---|---|
| `spell` | string | — | **Required.** ISS spell id. |
| `level` | int | `1` | Clamped to the spell's max level. |
| `cooldown` | int (ticks) | the spell's own | 20 ticks = 1 second. After the config multiplier the minimum is 1 tick. |
| `priority` | int 0–9 | `1` | Lower runs first, and decides whether the cast beats vanilla AI; see [Priority and vanilla AI](#priority-and-vanilla-ai). |
| `range` | number | `16` | Blocks, capped by the config's `maxRange` (32 by default). |
| `castTime` | int (ticks) | ISS's value | `0` = instant, `> 0` = wind-up or channel. Overrides ISS's cast-time reduction attribute. |
| `checkInterval` | int (ticks) | `10` | How often the goal re-checks whether it can cast. |
| `chance` | number 0–1 | `1.0` | Rolled right before the cast starts; a failed roll does not consume the cooldown. |
| `cooldownGroup` | string | — | `[a-z0-9_.-]{1,32}`. Spells sharing a group share one cooldown — useful for phase changes. |
| `interruptOnDamage` | bool | `false` | Normal casts only. Channeled spells use the global switch instead. |
| `repeatOnCast` | bool | `true` | Channeled (CONTINUOUS) spells only: re-run `onCast` every 10 ticks, like ISS's own casters. |
| `target` | string or object | `ATTACK_TARGET` | See [Targets](#targets). |
| `conditions` | object | — | Health, target distance, plus the `world` and `self` groups below. |

### Priority and vanilla AI

The cast goal holds `MOVE + LOOK` and is registered into the vanilla `goalSelector` at `priority`. Vanilla `GoalSelector` lets a goal with a **lower number preempt** the running one, so the field is not just our internal ordering: it decides whether casting can beat melee or ranged AI:

| Mob | Vanilla attack goal | Priority |
|---|---|---|
| Zombie, husk | `ZombieAttackGoal` | 2 |
| Skeleton, stray | `RangedBowAttackGoal` | 4 |
| Blaze | `BlazeAttackGoal` | 4 |

- `priority: 1` (default): preempts melee whenever a spell is ready, so the mob stops chasing while it casts. During cooldown, or when the conditions fail, `canUse()` returns false and melee takes over again — the "stop, cast, keep chasing" loop is expected.
- `priority: 2` or higher: ties with zombie melee. The melee goal was registered first by the mob itself in `registerGoals()`, and on a tie the earlier registration wins, so **casting never gets a turn and the mob only chases**. Keep melee mobs at `1`.
- `priority: 0`: rarely needed; only mobs that ship a movement goal at priority 0–1 (some `PanicGoal`/`FloatGoal`) require it.
- A target farther than `range` also fails `canUse()` and hands control back to melee. Raise `range` (capped by the `maxRange` config, default 32) if the caster should open fire from farther away.

The melee goal belongs to the vanilla mob, so this mod can only preempt it, never remove it. Today the only ways to keep a caster out of melee are `teleport`-style spells or a larger `range`.

### Conditions

Conditions are checked before the cast starts and compare the **caster's** health with the target's distance.

| Key | Default | Meaning |
|---|---|---|
| `healthBelow` | `1.0` | Only cast when the caster's health fraction is below this. |
| `healthAbove` | `0.0` | Only cast when the caster's health fraction is above this. |
| `minTargetDistance` | `0` | Skip the cast if the target is closer than this. |
| `maxTargetDistance` | unlimited | Skip the cast if the target is farther than this. |

Conditions can also be grouped by source; the flat form keeps working (flat keys win):

```json
"conditions": {
  "self":   { "healthBelow": 0.5 },
  "target": { "minTargetDistance": 4.0 }
}
```

Distance conditions are skipped for position casts (they have no entity target).

### World and caster conditions

`conditions.world` looks at the level, `conditions.self` looks at the caster itself (next to `healthBelow`). Both are optional and are also checked by forced casts such as `onDeath`.

| Key | Value | Meaning |
|---|---|---|
| `world.time` | `"day"`, `"night"`, `"dawn"`, `"dusk"`, or an array | World time. Dimensions without a sky (the Nether, the End) never match; use `world.dimension` there. |
| `world.weather` | `"clear"`, `"rain"`, `"thunder"`, or an array | Weather. |
| `world.moonPhase` | `0`-`7`, or an array | Moon phase, `0` is a full moon, one step per day. |
| `world.dimension` | dimension id or array | Any dimension, including modded ones such as `"twilightforest:twilight_forest"`. |
| `world.difficulty` | `"peaceful"`, `"easy"`, `"normal"`, `"hard"` | World difficulty. |
| `world.minDay` | integer | Minimum world age in days. |
| `self.onFire` | `true` / `false` | Whether the caster must be burning. |
| `self.inWater` | `true` / `false` | Whether the caster must be in water. |
| `self.inLava` | `true` / `false` | Whether the caster must be in lava. |
| `self.frozen` | `true` / `false` | Whether the caster must be frozen (powder snow). |

`true` means "must be", `false` means "must not be", and leaving the key out means "don't care".

```json
"conditions": {
  "world": {
    "time": ["night", "dusk"],
    "weather": ["thunder"],
    "dimension": ["minecraft:overworld", "twilightforest:twilight_forest"],
    "minDay": 3
  },
  "self": { "onFire": false, "inWater": false },
  "target": { "maxTargetDistance": 16 }
}
```

## Triggers

| Key | Fires when | Notes |
|---|---|---|
| `goals` | periodically | Array; each entry is an independent casting goal. |
| `onHurt` | the mob takes damage | The attacker is used as the preferred target. |
| `onTarget` | the mob acquires a target | The new target is used as the preferred target. |
| `onDeath` | the mob dies | Cast immediately: cooldown and alive checks are skipped. |
| `onKill` | the mob kills something | Goes through normal cooldown and conditions. |

```json
{
  "goals": [ { "spell": "irons_spellbooks:fireball", "target": "ATTACK_TARGET" } ],
  "onHurt":  { "spell": "irons_spellbooks:heal", "cooldown": 400, "target": "SELF" },
  "onDeath": { "spell": "irons_spellbooks:ice_spikes", "level": 1, "range": 8,
               "target": { "type": "POSITION", "positionMode": "RANDOM_IN_RANGE" } },
  "onKill":  { "spell": "irons_spellbooks:heal", "target": "SELF" }
}
```

A mob only gets one event cast at a time: if it is already casting, further triggers are ignored rather than queued.

## Targets

`target` is either a string shorthand (`"ATTACK_TARGET"`) or an object:

```json
{
  "type": "NEAREST_HOSTILE",
  "sort": "FARTHEST",
  "lineOfSight": true,
  "maxHealthPercent": 0.8
}
```

### Target types

| Type | Picks |
|---|---|
| `SELF` | The caster. |
| `ATTACK_TARGET` | The caster's current attack target. |
| `HOSTILE_TARGET` | Alias of `ATTACK_TARGET`, kept for older packs. |
| `LAST_DAMAGE_SOURCE` | The last entity that hurt the caster. |
| `THREATENING_ENTITY` | An entity that attacked the caster or is targeting it. |
| `NEAREST_HOSTILE` | Nearest player or monster that is neither an ally nor the caster's own kind. |
| `NEAREST_UNDEAD` | Nearest undead mob. |
| `NEAREST_ARTHROPOD` | Nearest arthropod. |
| `NEAREST_ANIMAL` | Nearest animal. |
| `NEAREST_MONSTER` | Nearest monster (anything implementing `Enemy`). |
| `NEAREST_PLAYER` | Nearest survival / adventure player. |
| `OWNER` | The owner of a tamed mob. |
| `NEAREST_ALLY` | Nearest mob of the same type, or on the same team. |
| `RANDOM_HOSTILE` | A random hostile in range. |
| `POSITION` | A point in the world — see aim modes. |

> ⚠️ **`SELF` means "the target is me".** A caster can always find itself, so a `SELF` goal fires as soon as its cooldown is up, whether or not any enemy is around.
> If you want "only summon/heal while fighting", change that line's `target` to `ATTACK_TARGET`:

```json
"goals": [
  { "spell": "irons_spellbooks:heal", "cooldown": 400, "target": "SELF" },
  { "spell": "irons_spellbooks:summon_vex", "cooldown": 400, "target": "ATTACK_TARGET" }
]
```

### Target object fields

| Key | Default | Notes |
|---|---|---|
| `type` | `ATTACK_TARGET` | One of the types above. |
| `sort` | `NEAREST` | `NEAREST`, `FARTHEST`, `LOWEST_HEALTH`, `HIGHEST_HEALTH`, `RANDOM`. |
| `lineOfSight` | `true` | Entity targets must be visible (ignored for `POSITION`). |
| `maxHealthPercent` | `1.0` | Only accept targets at or below this health fraction; same as `filters.maxHealthPercent`. |
| `positionMode` | `RELATIVE` | `POSITION` only — see below. |
| `relativeOffset` | `[0,0,0]` | `POSITION` only; `RELATIVE` uses all three axes, `BEHIND_TARGET` uses `x` (distance) and `y` (vertical offset). |
| `placement` | empty | `POSITION` only — safety checks for the computed point, see below. |
| `filters` | empty | Target filters, see below. |

#### Target filters

```json
"target": {
  "type": "NEAREST_HOSTILE",
  "filters": {
    "entityTypes": ["minecraft:skeleton", "minecraft:stray"],
    "entityTags": ["minecraft:raiders"],
    "excludeTags": ["spellmob_immune"],
    "excludeCasterTeam": true,
    "minHealthPercent": 0.3,
    "maxHealthPercent": 0.9,
    "requireNoShield": true
  }
}
```

| Key | Default | Notes |
|---|---|---|
| `entityTypes` | empty | Only these entity ids. |
| `entityTags` | empty | Only entities carrying any of these entity-type tags. |
| `excludeTags` | empty | Entities with any of these scoreboard tags are rejected. |
| `excludeCasterTeam` | `false` | Reject entities allied to the caster's team. |
| `ignoreCreative` | `true` | Ignore creative / spectator players. |
| `minHealthPercent` / `maxHealthPercent` | `0.0` / `1.0` | Only entities inside this health band. |
| `requireNoShield` | `false` | Only entities that are not blocking. |

Filters run after a candidate is resolved and before the cast starts; `POSITION` targets ignore them.


### Aim modes (`POSITION` only)

> **Get this straight first: `POSITION` is not a landing spot — it only decides where the caster looks.**
>
> Iron's Spells has no coordinate parameter in its cast API. Projectiles always spawn at the caster's
> eye/body and fly along its current look direction. The point resolved here is fed to `lookAt`;
> it does not move where the spell spawns.
>
> - Pure projectiles (icicle, magic missile, fireball, rays): direction only. It is only useful when
>   there is no entity target at all, and it **cannot hit through walls**.
> - Spells that find their landing spot by raycast (teleport, magma bomb, black hole, ice block,
>   portal): genuinely affected, because the ray origin and direction change.
> - Self-casts / buffs / summons: completely unaffected.
>
> If you want "keep hitting the target", use an entity target such as `ATTACK_TARGET` or
> `NEAREST_HOSTILE` — `POSITION + TARGET_ENTITY` throws away line-of-sight checks and `filters`.

| Mode | Where it aims |
|---|---|
| `RELATIVE` | Caster's position + `relativeOffset`. |
| `EYES_LOOK` | Eye position + look direction × `range`. |
| `FEET_LOOK` | Feet position + look direction × `range` (a different ray origin than `EYES_LOOK`). |
| `TARGET_ENTITY` | The target's position; if there is no target the cast is skipped. |
| `BEHIND_TARGET` | Behind the target: `relativeOffset.x` is the distance (default `2` blocks) and `relativeOffset.y` the vertical offset. Lets a teleport land behind the player instead of on top of them. |
| `RANDOM_IN_RANGE` | A random point within `range` blocks. |

#### Placement checks

`RANDOM_IN_RANGE` and `BEHIND_TARGET` can reject points that land in water or lava:

```json
"target": {
  "type": "POSITION",
  "positionMode": "RANDOM_IN_RANGE",
  "placement": { "avoidFluid": true, "avoidLava": true }
}
```

| Key | Default | Notes |
|---|---|---|
| `avoidFluid` | `false` | Reject any point inside a liquid (water or lava). |
| `avoidLava` | `false` | Reject lava only; water is still allowed. |

`RANDOM_IN_RANGE` retries up to 8 times and gives up the cast when every point is rejected;
`BEHIND_TARGET` falls back to the target's own position.

## Variants

`variants` lets one entity file describe different rules for different individuals, and swap rules at a health threshold. Variants are only available in data packs — the config's `[species] extraRules` does not support them.

```json
{
  "goals": [
    { "spell": "irons_spellbooks:magic_missile", "cooldown": 120, "target": "ATTACK_TARGET" }
  ],
  "variants": [
    { "select": { "name": "A", "tags": ["boss_a"] },
      "goals": [ { "spell": "irons_spellbooks:fireball", "cooldown": 100, "range": 24 } ] },

    { "select": { "nameRegex": "^B-[0-9]+$" },
      "goals": [ { "spell": "irons_spellbooks:lightning_lance", "cooldown": 140 } ] },

    { "select": { "name": "C" }, "override": false,
      "goals": [ { "spell": "irons_spellbooks:haste", "target": "SELF" } ] },

    { "select": { "name": "D" }, "override": true },

    { "select": { "healthBelow": 0.4 },
      "goals": [ { "spell": "irons_spellbooks:ice_spikes", "cooldown": 90, "range": 20 } ] }
  ]
}
```

`select` conditions (all of the given ones must match):

| Key | Matches |
|---|---|
| `name` | Exact custom name. |
| `nameRegex` | Full match against the custom name. |
| `tags` | Contains all listed scoreboard tags. |
| `healthBelow` | Health fraction strictly below this. |
| `healthAbove` | Health fraction strictly above this. |
| `tamed` | Whether the mob is tamed (`true` / `false`; only meaningful for tamable mobs). |

Rules:

- Variants are checked **top to bottom; the first match wins.** Put specific conditions first and broad health bands last.
- `override` defaults to `true`: the variant replaces the top-level defaults entirely. With `false` the variant's spells are **added** to the defaults (variant goals first, then default goals).
- A variant with `override: true` and no spells means "this individual casts nothing".
- `name` and `nameRegex` require the mob to have a custom name.

**Renaming** a mob re-syncs its goals automatically within a tick. **Taming or untaming** does as well. **Changing tags does not** — run `/spellmob resync` afterwards.

### Phase lock (`sticky`)

Health bands are two-way by default: a boss that gets healed above the threshold falls back to the previous phase and swaps its whole spell list. Adding `"sticky": true` to a variant **locks it once it matched**, so healing does not undo it; dropping into a deeper phase is still allowed, and the lock only ever moves forward.

```json
{
  "variants": [
    { "select": { "healthBelow": 0.5 }, "sticky": true,
      "goals": [ { "spell": "irons_spellbooks:wither_skull", "cooldown": 100 } ] },

    { "select": { "healthBelow": 0.2 }, "sticky": true,
      "goals": [ { "spell": "irons_spellbooks:black_hole", "cooldown": 200 } ] }
  ]
}
```

- `sticky` defaults to `false`, i.e. the old two-way behaviour.
- Only variants marked `sticky` are remembered; the others keep matching both ways.
- Renaming resets the lock (it is a different individual now); the lock is saved with the mob, so unloading and reloading a chunk does not drop the phase.
- It is also accepted inside `select`: `"select": { "healthBelow": 0.5, "sticky": true }`.

## Drops

`dropRules` gives loot to casting mobs, and also lets a definition carry loot only (a “loot variant” that never casts).

It may appear in two places:

- **At the root of the file**: applies to every mob that resolves to the definition.
- **Inside a `variants` entry**: applies only while that variant matches; the variant that matched when the mob died decides which rules are used.

All rules whose `select` matches are rolled — from the matched variant first, then the root ones. Rules no longer stop after the first hit, so “always drop gold” and “20% chance of a loot table” can be two rules, even with the same `select`.

> ⚠️ Notes
>
> - Rules no longer shadow each other: overlapping rules stack their loot. Put the conditions in `select` instead of relying on “the first hit skips the rest”.
> - A definition with only `dropRules` and no `goals` is valid: the mob casts nothing and still drops loot.
> - If several variants share the same loot, put the rules at the root instead of repeating them.

```json
"dropRules": [
  {
    "select": { "tags": ["spellmob_boss"] },
    "chance": 1.0,
    "drops": [
      { "item": "minecraft:gold_block", "min": 3, "max": 6, "looting": true }
    ]
  },
  {
    "select": { "tags": ["spellmob_boss"] },
    "chance": 0.2,
    "drops": [
      { "lootTable": "minecraft:chests/desert_pyramid" }
    ]
  }
]
```

Inside a variant the structure is identical:

```json
"variants": [
  {
    "select": { "tags": ["spellmob_boss"] },
    "goals": [ ... ],
    "dropRules": [
      { "select": { "tags": ["spellmob_boss"] }, "chance": 1.0, "drops": [ ... ] }
    ]
  }
]
```

- Rule-level `chance` rolls once per rule; entry-level `chance` rolls per entry.
- Each entry is either an `item` or a `lootTable`, never both.
- `count` for a fixed amount, or `min` / `max` for a range.
- `requirePlayerKill` defaults to `true`, so mob farms and entity cramming do not drop loot.

## Configuration

The config lives at `config/spellmob-common.toml` (COMMON type — one file for the whole installation, not per world).

It is a plain Forge `ModConfigSpec`, so in-game config editors such as **Configured** can read it and edit it:

- Ordinary values (switches, cooldown multiplier, blacklists, particle settings) apply immediately — they are read on use, never cached.
- The `[species]` rule text (`extraRules`, `spawnEgg`, …) is parsed into rule objects at startup; after editing it run `/spellmob resync`, or restart.
- Data packs always win over a config rule for the same entity: if a data pack defines it, the config rule is ignored (`/spellmob info` shows the source, and a line is logged).

| Section | Key | Default | Meaning |
|---|---|---|---|
| `general` | `enabled` | `true` | Master switch. |
| | `debugLogging` | `false` | Verbose `[SpellMob]` logging. |
| `balance` | `cooldownMultiplier` | `1.0` | Scales every data pack cooldown; `0` compresses it to the 1-tick minimum. |
| | `maxRange` | `32` | Hard cap on `range` from data packs (up to 128). |
| | `interruptChannelsOnDamage` | `true` | Damage interrupts channeled spells. |
| | `globalCooldown` | `0` | Global cooldown in ticks: after one cast every spell of that caster waits this long. `0` disables it, max 400. |
| `adaptive` | `enabled` | `true` | Master switch for bosses learning spells from players they defeated. |
| | `adaptiveTag` | `spellmob_adaptive` | Scoreboard tag that marks a boss as able to learn; the boss must have it. |
| | `maxLearnedSpells` | `1` | How many spells one boss may learn in total (0–8); `0` disables learning. |
| | `rememberedSpells` | `6` | How many spells of one player are remembered per fight (1–16). |
| | `learnedCooldown` | `-1` | Cooldown of a learned spell in ticks (-1–1200); `-1` follows the spell's own cooldown (seconds × 20), `>= 0` uses a fixed value. |
| | `learnedPriority` | `2` | Goal priority of a learned spell (0–9, lower runs first). |
| | `spellBlacklist` | teleport/blood_step/evasion | Spells a boss may never learn. |
| `filters` | `entityBlacklist` | empty | Entity ids that never cast. |
| | `dimensionBlacklist` | empty | Dimensions where casting is disabled. |
| `protection` | `protectedTags` | empty | Mobs with these tags cannot be renamed with a name tag. |
| | `allowOpsRename` | `true` | Ops (permission 2) bypass the name lock. |
| | `channelImmuneTags` | `["spellmob_boss"]` | Mobs with these tags never lose a channel to damage. |
| `presentation` | `enabled` | `true` | Draw casting circles at all. |
| | `points` | `24` | Dots per normal circle, `0` = no circle; range 0–128, values below 6 are barely visible. |
| | `bossPoints` | `32` | Dots per boss circle, `0` = no boss circle; range 0–128. |
| | `ringScale` | `1.0` | Radius multiplier, range 0.25–4.0. |
| | `distance` | `24` | Particle view distance (4–64). |
| | `interruptFeedback` | `true` | Smoke + fizzle sound when a cast is interrupted. |
| | `bossTags` | `["spellmob_boss"]` | Casters with these tags use the boss effects: a larger outer ground ring plus pulse rings. |
| | `bossExtras` | `true` | Adds the periodic pulse rings to boss effects. |
| | `bossBar` | `false` | Show a vanilla boss bar for casters; independent from `enabled`, so you can keep the bar and drop the particles. |
| | `bossBarColor` | `white` | Bar colour: `pink` / `blue` / `red` / `green` / `yellow` / `purple` / `white`. |
| | `bossBarRange` | `48` | Players within this distance see the bar (8–128). |
| | `bossBarTags` | `["spellmob_boss"]` | Mobs with these tags get a bar; empty means every mob with rules. |
| | `bossBarCastName` | `true` | Append "casting <spell>" to the bar name while the mob casts; turn it off to keep the plain name. |
| `species` | `extraRules` | empty | Whole-species rules, one JSON object per line. Same schema as a data pack file, minus `variants`. |

When both a data pack and `[species] extraRules` define the same entity, **the data pack wins** and the config entry is ignored (a log line records it).

## Boss bar

With `presentation.bossBar = true`, casters carrying one of `bossBarTags` (default: only `spellmob_boss`) get a vanilla boss bar:

- The name is the mob's display name and the progress is its health fraction; the colour comes from `bossBarColor`.
- Only players within `bossBarRange` blocks (spectators excluded) receive it; the recipient list follows players in and out of range.
- Independent from `presentation.enabled`: set `enabled` to `false` and `bossBar` to `true` to keep only the bar.
- The bar is removed when the mob dies or its chunk unloads, and config edits apply immediately.
- Data packs can override it per individual: put `"bossBar": true` or `"bossBar": false` in the root of a `spell_casters` file or inside a variant. That beats `bossBarTags` and `presentation.bossBar`; leave it out to follow the config. Two mobs of the same type can therefore show and hide the bar respectively (the bundled `zombie.json` demonstrates exactly that).

## Adaptive bosses

This is the "second encounter" mechanic: after a boss defeats a player, the next time they meet the boss learns one of the spells that player used last time, always at that spell's maximum level.

- Gate: `adaptive.enabled` must be on and the boss must carry the scoreboard tag named by `adaptive.adaptiveTag` (default `spellmob_adaptive`). `/tag @e[type=minecraft:zombie,limit=1] add spellmob_adaptive` turns a mob into a learner.
- Recording: a fight starts when either side hits the other; every spell the player casts during it is remembered, up to `rememberedSpells` per player.
- Learning: if the player dies to the boss (or within 48 blocks of it) the fight counts as a loss; on the next fight the boss randomly learns one remembered spell that it does not know yet and that is not in `spellBlacklist`.
- Limits: at most `maxLearnedSpells` per boss (default 1, `0` disables learning); learned spells use `learnedCooldown` and `learnedPriority` like any other goal and always cast at `getMaxLevel()`. `learnedCooldown = -1` follows the spell's own default cooldown, `>= 0` pins a fixed tick value; either way `balance.cooldownMultiplier` still applies.
- The default blacklist is `irons_spellbooks:teleport`, `blood_step` and `evasion`, so bosses do not pick up escape tools.
- State is stored in the mob's NBT (`adaptive` sub-tag) and survives death, dimension changes and `/reload`; goals are rebuilt from the caster state on every sync.

## Commands

Permission level 2 required.

| Command | Does |
|---|---|
| `/spellmob list` | Lists every loaded entity type with its source (`data pack` / `config`). |
| `/spellmob info <mob>` | Shows the resolved configuration for that mob, including which variant matched and remaining cooldowns. |
| `/spellmob resync [targets]` | Rebuilds casting goals for the given mobs (or yourself). Run after changing tags. |
| `/spellmob validate` | Checks every loaded spell id and lists config entries shadowed by data packs. |
| `/spellmob dump spells` | Writes every registered spell id, cast type and school to `config/spellmob-spells.txt`. |

## Compatibility notes

- **Server-side only.** Clients do not need the mod; the circles are ordinary particles the server sends.
- **Data pack paths mirror entity ids.** A file under `data/mypack/` can never affect `minecraft:zombie`.
- **Unknown JSON keys are ignored.** Typos in field names fail silently — `validate` and `info` are the way to catch them.
- **Editor completion.** `docs/spell_casters.schema.json` is a standard JSON Schema; bind it to `spell_casters/*.json` in VS Code or IntelliJ to get completion and typo detection.
- **ISS versions.** Built against the 3.4.0.9 API and verified against 3.16.3; the jar declares `[1.20.1-3.4.0.9,)`.

## Examples

The repository ships ready-to-use packs under `examples/`:

| Folder | Contents |
|---|---|
| `examples/classic/` | The original five examples: zombies A/B, skeleton, wolf, villager, husk. |
| `examples/bosses/` | Six name + tag gated bosses (void eye, dune priest, …). |
| `examples/channel_test/` | Pillager and vindicator that channel every CONTINUOUS spell. |
| `examples/full-reference.json` | Field reference — every key in one file. |
