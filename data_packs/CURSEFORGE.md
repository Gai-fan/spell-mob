# SpellMob Showcase — CurseForge page copy

**Project name:** SpellMob Showcase
**Summary (max ~120 chars):** 15 vanilla mobs rewritten as spellcasters — a showcase pack for the SpellMob mod.

---

## Description

SpellMob Showcase is a drop-in data pack that turns fifteen vanilla mobs into spellcasters of different flavours. It is a demo pack: every file is meant to be read, copied and edited, and between them they exercise every feature the SpellMob mod offers — periodic casts, reaction triggers, health phases, tag variants, damage-over-time channels, cooldown groups, positioning modes and custom drops.

No new items, blocks or entities. Everything is driven by Minecraft's own data pack system, so the pack works on clients and dedicated servers alike and needs no client-side installation.

## Requirements

| | |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.x |
| Iron's Spells 'n Spellbooks | 1.20.1-3.4.0.9 or newer (every spell used exists in both 3.4.0.9 and 3.16.3) |
| SpellMob | 2.0.0 or newer |
| Configured (optional) | only needed if you want a GUI for the mod's own settings |

## Installation

1. Install SpellMob and Iron's Spells 'n Spellbooks.
2. Drop the data pack zip into `saves/<world>/datapacks/` (single player) or `<world>/datapacks/` (server).
3. Run `/reload`.
4. Look for `[SpellMob] Loaded 15 spell caster definition(s)` in the log.

The pack is world-scoped: a new world needs its own copy.

## What is inside

| Mob | Theme | Highlights |
| --- | --- | --- |
| Zombie | Rotting apprentice | Magic missile + slow, self-heal when hurt, enrages below 40% health |
| Husk | Dune wanderer | Scorch at range, fiery burst on death aimed along its look |
| Skeleton | Bone chanter | Arrow volley + slowing hex, lightning phase below half health, `spellmob_elite` tag variant |
| Stray | Frost archer | Icicles, ice spikes, frostwave when hurt |
| Drowned | Drowned priest | Poison splash, blood needles, toxic pool on death |
| Spider | Web weaver | Root, poison on target, haste when hurt |
| Cave spider | Venom fang | Short-range poison, poison burst on death |
| Pillager | Raid chanter | Snipes the farthest hostile, shield below half health, evasion, drops emeralds |
| Vindicator | Axe chanter | Fang strike, blood slash, execution strike on weakened targets |
| Evoker | Evoker lord | Chain lightning, summon vex, black hole below half health, summons on death |
| Witch | Swamp hag | Channelled poison breath (interruptible), blight through walls, self-heal, haste after a kill |
| Wither skeleton | Wither knight | Wither skull + blood needles sharing a cooldown group, self-heal on kill, coal and rare skull drops |
| Blaze | Blaze chanter | Fireball and fire breath sharing a cooldown group, firecracker when hurt |
| Enderman | Void walker | Magic arrows, random blink when hurt |
| Iron golem | Iron warden | Earthquake, fortify when hurt |

## Features demonstrated

- All five trigger types: `goals`, `onHurt`, `onTarget`, `onDeath`, `onKill`
- Target selection: `ATTACK_TARGET`, `NEAREST_HOSTILE`, `SELF`, `POSITION`, plus `sort`, `maxHealthPercent` and `lineOfSight`
- All five position modes: `EYES_LOOK`, `FEET_LOOK`, `TARGET_ENTITY`, `RANDOM_IN_RANGE`, `RELATIVE`
- Conditions: `healthBelow`, `healthAbove`, `minTargetDistance`, `maxTargetDistance`
- Pacing: `cooldown`, `chance`, `checkInterval`, `castTime`, `cooldownGroup`, `interruptOnDamage`
- Health-phase variants and tag-driven elite variants
- Loot rules: pillager emeralds, wither skeleton coal and skull

## Notes

- The pack makes **every** matching mob cast, not only named ones. That is intentional for a "mob overhaul" flavour; for named-boss-only setups see the `examples/bosses` pack shipped with the mod.
- Do not install it together with `examples/classic`: both define the same entities and the last loaded pack wins.
- Other data packs that write to `data/<namespace>/spell_casters/` conflict by path, as usual.
- The mod's own settings (global switch, cooldown multiplier, name protection, etc.) live in `config/spellmob-common.toml`.

## Changelog

### 1.0.0
- First release: 15 mobs, 51 spell bindings, all five triggers, all five position modes, loot rules.
