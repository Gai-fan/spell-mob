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
| `priority` | int 0–9 | `1` | Lower runs first, and also decides who wins against vanilla AI: the cast goal holds MOVE + LOOK, so `1` preempts melee (priority 2). Raise it if you want melee to win. |
| `range` | number | `16` | Blocks, capped by the config's `maxRange` (32 by default). |
| `castTime` | int (ticks) | ISS's value | `0` = instant, `> 0` = wind-up or channel. Overrides ISS's cast-time reduction attribute. |
| `checkInterval` | int (ticks) | `10` | How often the goal re-checks whether it can cast. |
| `chance` | number 0–1 | `1.0` | Rolled right before the cast starts; a failed roll does not consume the cooldown. |
| `cooldownGroup` | string | — | `[a-z0-9_.-]{1,32}`. Spells sharing a group share one cooldown — useful for phase changes. |
| `interruptOnDamage` | bool | `false` | Normal casts only. Channeled spells use the global switch instead. |
| `repeatOnCast` | bool | `true` | Channeled (CONTINUOUS) spells only: re-run `onCast` every 10 ticks, like ISS's own casters. |
| `target` | string or object | `ATTACK_TARGET` | See [Targets](#targets). |
| `conditions` | object | — | `healthBelow`, `healthAbove`, `minTargetDistance`, `maxTargetDistance`. |

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
| `POSITION` | A point in the world — see position modes. |

### Target object fields

| Key | Default | Notes |
|---|---|---|
| `type` | `ATTACK_TARGET` | One of the types above. |
| `sort` | `NEAREST` | `NEAREST`, `FARTHEST`, `LOWEST_HEALTH`, `HIGHEST_HEALTH`, `RANDOM`. |
| `lineOfSight` | `true` | Entity targets must be visible (ignored for `POSITION`). |
| `maxHealthPercent` | `1.0` | Only accept targets at or below this health fraction; same as `filters.maxHealthPercent`. |
| `positionMode` | `RELATIVE` | `POSITION` only — see below. |
| `relativeOffset` | `[0,0,0]` | `POSITION` + `RELATIVE` only. |
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


### Position modes (`POSITION` only)

| Mode | Where it lands |
|---|---|
| `RELATIVE` | Caster's position + `relativeOffset`. |
| `EYES_LOOK` | Eye position + look direction × `range`. |
| `FEET_LOOK` | Feet position + look direction × `range`. |
| `TARGET_ENTITY` | The target's position; if there is no target the cast is skipped. |
| `RANDOM_IN_RANGE` | A random point within `range` blocks. |

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

## Drops

`dropRules` gives loot to the mobs that actually cast. Rules are only evaluated for a mob whose casting config resolves to something non-empty; ordinary mobs are untouched.

```json
"dropRules": [
  {
    "select": { "tags": ["spellmob_boss"] },
    "chance": 1.0,
    "drops": [
      { "item": "minecraft:emerald", "minCount": 2, "maxCount": 4, "looting": true },
      { "lootTable": "minecraft:chests/desert_pyramid", "chance": 0.2 }
    ]
  }
]
```

- Only the **first matching rule** is used.
- Rule-level `chance` rolls once; entry-level `chance` rolls per entry.
- Each entry is either an `item` or a `lootTable`, never both.
- `count` for a fixed amount, or `minCount` / `maxCount` for a range.
- `requirePlayerKill` defaults to `true`, so mob farms and entity cramming do not drop loot.

## Configuration

The config lives at `config/spellmob-common.toml` (COMMON type — one file for the whole installation, not per world).

| Section | Key | Default | Meaning |
|---|---|---|---|
| `general` | `enabled` | `true` | Master switch. |
| | `debugLogging` | `false` | Verbose `[SpellMob]` logging. |
| `balance` | `cooldownMultiplier` | `1.0` | Scales every data pack cooldown; `0` compresses it to the 1-tick minimum. |
| | `maxRange` | `32` | Hard cap on `range` from data packs (up to 128). |
| | `interruptChannelsOnDamage` | `true` | Damage interrupts channeled spells. |
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
| `species` | `extraRules` | empty | Whole-species rules, one JSON object per line. Same schema as a data pack file, minus `variants`. |

When both a data pack and `[species] extraRules` define the same entity, **the data pack wins** and the config entry is ignored (a log line records it).

## Commands

Permission level 2 required.

| Command | Does |
|---|---|
| `/spellmob list` | Lists every loaded entity type with its source (`data pack` / `config`). |
| `/spellmob info <mob>` | Shows the resolved configuration for that mob, including which variant matched and remaining cooldowns. |
| `/spellmob resync [targets]` | Rebuilds casting goals for the given mobs (or yourself). Run after changing tags. |
| `/spellmob validate` | Checks every loaded spell id and lists config entries shadowed by data packs. |

## Compatibility notes

- **Server-side only.** Clients do not need the mod; the circles are ordinary particles the server sends.
- **Data pack paths mirror entity ids.** A file under `data/mypack/` can never affect `minecraft:zombie`.
- **Unknown JSON keys are ignored.** Typos in field names fail silently — `validate` and `info` are the way to catch them.
- **ISS versions.** Built against the 3.4.0.9 API and verified against 3.16.3; the jar declares `[1.20.1-3.4.0.9,)`.

## Examples

The repository ships ready-to-use packs under `examples/`:

| Folder | Contents |
|---|---|
| `examples/classic/` | The original five examples: zombies A/B, skeleton, wolf, villager, husk. |
| `examples/bosses/` | Six name + tag gated bosses (void eye, dune priest, …). |
| `examples/channel_test/` | Pillager and vindicator that channel every CONTINUOUS spell. |
| `examples/full-reference.json` | Field reference — every key in one file. |
