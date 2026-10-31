#!/usr/bin/env python3
"""Generate the SpellMob showcase data pack.

Run from the repository root:
    python tools/build_showcase_pack.py

It writes data_packs/showcase/ (pack.mcmeta + data/minecraft/spell_casters/*.json)
and validates every spell id, target type, position mode and sort value.
"""

from __future__ import annotations

import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "data_packs" / "showcase"
PACKS = OUT / "data" / "minecraft" / "spell_casters"

ALLOWED_TARGETS = {
    "ATTACK_TARGET", "HOSTILE_TARGET", "LAST_DAMAGE_SOURCE", "THREATENING_ENTITY",
    "NEAREST_HOSTILE", "NEAREST_UNDEAD", "NEAREST_ARTHROPOD", "NEAREST_ANIMAL",
    "NEAREST_MONSTER", "NEAREST_PLAYER", "OWNER", "NEAREST_ALLY", "SELF",
    "RANDOM_HOSTILE", "POSITION",
}
ALLOWED_MODES = {"RELATIVE", "EYES_LOOK", "FEET_LOOK", "TARGET_ENTITY", "RANDOM_IN_RANGE"}
ALLOWED_SORTS = {"NEAREST", "FARTHEST", "LOWEST_HEALTH", "HIGHEST_HEALTH", "RANDOM"}
ALLOWED_SELECT = {"name", "nameRegex", "tags", "healthBelow", "healthAbove"}
ALLOWED_CAST = {
    "spell", "level", "cooldown", "priority", "range", "castTime", "checkInterval",
    "chance", "cooldownGroup", "interruptOnDamage", "repeatOnCast", "target", "conditions",
    "_comment",
}


def goals(*entries):
    return list(entries)


ENTITIES: dict[str, dict] = {
    "zombie": {
        "_comment": "Rotting apprentice: a weak ranged caster that heals itself when hurt, and enrages below 40% health.",
        "goals": goals(
            {"spell": "irons_spellbooks:magic_missile", "level": 1, "cooldown": 160, "priority": 1,
             "range": 16, "castTime": 15, "target": "ATTACK_TARGET",
             "conditions": {"minTargetDistance": 3.0, "maxTargetDistance": 16.0}},
            {"spell": "irons_spellbooks:slow", "level": 1, "cooldown": 240, "priority": 2,
             "range": 20, "chance": 0.45, "target": "ATTACK_TARGET"},
        ),
        "onHurt": {"spell": "irons_spellbooks:heal", "level": 1, "cooldown": 400,
                   "target": "SELF", "conditions": {"healthBelow": 0.5}},
        "variants": [
            {"select": {"healthBelow": 0.4}, "goals": goals(
                {"spell": "irons_spellbooks:magic_missile", "level": 2, "cooldown": 100, "priority": 1,
                 "range": 16, "castTime": 10, "target": "ATTACK_TARGET"},
                {"spell": "irons_spellbooks:fang_strike", "level": 1, "cooldown": 220, "priority": 2,
                 "range": 6, "castTime": 10, "target": "ATTACK_TARGET"},
            )},
        ],
    },
    "husk": {
        "_comment": "Dune wanderer: scorches the ground at range and bursts into flame when it dies.",
        "goals": goals(
            {"spell": "irons_spellbooks:scorch", "level": 1, "cooldown": 220, "priority": 1,
             "range": 16, "castTime": 20, "target": "ATTACK_TARGET"},
        ),
        "onDeath": {"spell": "irons_spellbooks:firecracker", "level": 1, "range": 3,
                    "target": {"type": "POSITION", "positionMode": "EYES_LOOK", "lineOfSight": False}},
    },
    "skeleton": {
        "_comment": "Bone chanter: arrow volleys and a slowing hex. Below half health it switches to lightning; the 'spellmob_elite' tag turns it into a champion.",
        "goals": goals(
            {"spell": "irons_spellbooks:arrow_volley", "level": 2, "cooldown": 180, "priority": 1,
             "range": 24, "castTime": 30, "target": "ATTACK_TARGET"},
        ),
        "onTarget": {"spell": "irons_spellbooks:slow", "level": 1, "cooldown": 260, "range": 24,
                     "target": "ATTACK_TARGET"},
        "variants": [
            {"select": {"tags": ["spellmob_elite"]}, "goals": goals(
                {"spell": "irons_spellbooks:arrow_volley", "level": 3, "cooldown": 140, "priority": 1,
                 "range": 26, "castTime": 25, "target": "ATTACK_TARGET"},
                {"spell": "irons_spellbooks:chain_lightning", "level": 2, "cooldown": 240, "priority": 2,
                 "range": 20, "chance": 0.5, "target": "ATTACK_TARGET"},
            )},
            {"select": {"healthBelow": 0.5}, "goals": goals(
                {"spell": "irons_spellbooks:lightning_lance", "level": 2, "cooldown": 200, "priority": 1,
                 "range": 24, "castTime": 30, "target": "ATTACK_TARGET"},
                {"spell": "irons_spellbooks:arrow_volley", "level": 2, "cooldown": 150, "priority": 2,
                 "range": 24, "castTime": 25, "target": "ATTACK_TARGET"},
            )},
        ],
    },
    "stray": {
        "_comment": "Frost archer: icicles at range and a freezing burst when hurt.",
        "goals": goals(
            {"spell": "irons_spellbooks:icicle", "level": 2, "cooldown": 150, "priority": 1,
             "range": 22, "castTime": 12, "target": "ATTACK_TARGET"},
            {"spell": "irons_spellbooks:ice_spikes", "level": 2, "cooldown": 220, "priority": 2,
             "range": 18, "castTime": 20, "target": "ATTACK_TARGET"},
        ),
        "onHurt": {"spell": "irons_spellbooks:frostwave", "level": 1, "cooldown": 320,
                   "range": 6, "target": "SELF"},
    },
    "drowned": {
        "_comment": "Drowned priest: poison waves and blood needles; leaves a toxic splash on death.",
        "goals": goals(
            {"spell": "irons_spellbooks:poison_splash", "level": 1, "cooldown": 200, "priority": 1,
             "range": 14, "castTime": 15, "target": "ATTACK_TARGET"},
            {"spell": "irons_spellbooks:blood_needles", "level": 2, "cooldown": 260, "priority": 2,
             "range": 18, "chance": 0.4, "target": "ATTACK_TARGET"},
        ),
        "onDeath": {"spell": "irons_spellbooks:poison_splash", "level": 1, "range": 6,
                    "target": {"type": "POSITION", "positionMode": "RANDOM_IN_RANGE"}},
    },
    "spider": {
        "_comment": "Web weaver: roots its prey in place, lashes out with poison when targeted, and speeds up when hurt.",
        "goals": goals(
            {"spell": "irons_spellbooks:root", "level": 1, "cooldown": 220, "priority": 1,
             "range": 12, "castTime": 10, "target": "ATTACK_TARGET"},
        ),
        "onTarget": {"spell": "irons_spellbooks:poison_splash", "level": 1, "cooldown": 240,
                     "range": 10, "target": "ATTACK_TARGET"},
        "onHurt": {"spell": "irons_spellbooks:haste", "level": 1, "cooldown": 400, "target": "SELF"},
    },
    "cave_spider": {
        "_comment": "Venom fang: a short-range poison caster that bursts into poison on death.",
        "goals": goals(
            {"spell": "irons_spellbooks:poison_splash", "level": 1, "cooldown": 190, "priority": 1,
             "range": 8, "castTime": 10, "target": "ATTACK_TARGET",
             "conditions": {"maxTargetDistance": 8.0}},
        ),
        "onDeath": {"spell": "irons_spellbooks:poison_splash", "level": 1, "range": 4,
                    "target": {"type": "POSITION", "positionMode": "FEET_LOOK"}},
    },
    "pillager": {
        "_comment": "Raid chanter: snipes the farthest hostile with magic arrows, hides behind a shield when low, and carries emeralds.",
        "goals": goals(
            {"spell": "irons_spellbooks:magic_arrow", "level": 2, "cooldown": 200, "priority": 0,
             "range": 28, "castTime": 25,
             "target": {"type": "NEAREST_HOSTILE", "sort": "FARTHEST"}},
            {"spell": "irons_spellbooks:magic_missile", "level": 1, "cooldown": 140, "priority": 2,
             "range": 20, "castTime": 12, "target": "ATTACK_TARGET"},
            {"spell": "irons_spellbooks:shield", "level": 1, "cooldown": 600, "priority": 3,
             "range": 2, "castTime": 10, "target": {"type": "POSITION", "positionMode": "RELATIVE",
                                                    "relativeOffset": [0, 0, 2]},
             "conditions": {"healthBelow": 0.5}},
        ),
        "onHurt": {"spell": "irons_spellbooks:evasion", "level": 1, "cooldown": 500, "target": "SELF"},
        "dropRules": [
            {"select": {"healthAbove": 0.0}, "chance": 1.0, "drops": [
                {"item": "minecraft:emerald", "minCount": 1, "maxCount": 2, "chance": 0.5},
            ]},
        ],
    },
    "vindicator": {
        "_comment": "Axe chanter: fang lines at melee range, a bleeding slash, and an execution strike on weakened foes.",
        "goals": goals(
            {"spell": "irons_spellbooks:fang_strike", "level": 2, "cooldown": 200, "priority": 1,
             "range": 6, "castTime": 10, "target": "ATTACK_TARGET"},
            {"spell": "irons_spellbooks:blood_slash", "level": 2, "cooldown": 180, "priority": 2,
             "range": 6, "target": "ATTACK_TARGET"},
            {"spell": "irons_spellbooks:blood_slash", "level": 3, "cooldown": 260, "priority": 0,
             "range": 6, "target": {"type": "NEAREST_HOSTILE", "sort": "LOWEST_HEALTH",
                                    "maxHealthPercent": 0.5}},
        ),
        "onTarget": {"spell": "irons_spellbooks:haste", "level": 1, "cooldown": 400, "target": "SELF",
                     "conditions": {"healthAbove": 0.5}},
    },
    "evoker": {
        "_comment": "Evoker lord: summons vexes, chains lightning, and opens a black hole on its target when cornered. Death still summons help.",
        "goals": goals(
            {"spell": "irons_spellbooks:chain_lightning", "level": 2, "cooldown": 300, "priority": 1,
             "range": 20, "chance": 0.5, "castTime": 20, "target": "ATTACK_TARGET"},
            {"spell": "irons_spellbooks:summon_vex", "level": 1, "cooldown": 500, "priority": 2,
             "range": 8, "castTime": 30, "target": "SELF"},
        ),
        "variants": [
            {"select": {"healthBelow": 0.5}, "goals": goals(
                {"spell": "irons_spellbooks:black_hole", "level": 1, "cooldown": 500, "priority": 0,
                 "range": 16, "castTime": 40,
                 "target": {"type": "POSITION", "positionMode": "TARGET_ENTITY"}},
                {"spell": "irons_spellbooks:chain_lightning", "level": 2, "cooldown": 260, "priority": 1,
                 "range": 20, "castTime": 20, "target": "ATTACK_TARGET"},
            )},
        ],
        "onDeath": {"spell": "irons_spellbooks:summon_vex", "level": 1, "target": "SELF"},
    },
    "witch": {
        "_comment": "Swamp hag: channels a poison cone, curses through walls, heals itself when hurt, and speeds up after a kill.",
        "goals": goals(
            {"spell": "irons_spellbooks:poison_breath", "level": 1, "cooldown": 300, "priority": 1,
             "range": 8, "castTime": 60, "interruptOnDamage": True, "target": "ATTACK_TARGET"},
            {"spell": "irons_spellbooks:blight", "level": 1, "cooldown": 260, "priority": 2,
             "range": 16, "target": {"type": "ATTACK_TARGET", "lineOfSight": False}},
        ),
        "onHurt": {"spell": "irons_spellbooks:heal", "level": 2, "cooldown": 350, "target": "SELF",
                   "conditions": {"healthBelow": 0.6, "healthAbove": 0.1}},
        "onKill": {"spell": "irons_spellbooks:haste", "level": 1, "cooldown": 300, "target": "SELF"},
    },
    "wither_skeleton": {
        "_comment": "Wither knight: wither skulls and blood needles share one cooldown group; heals itself after a kill and drops coal and, rarely, its skull.",
        "goals": goals(
            {"spell": "irons_spellbooks:wither_skull", "level": 2, "cooldown": 220, "priority": 1,
             "range": 20, "castTime": 20, "cooldownGroup": "wither_volley", "target": "ATTACK_TARGET"},
            {"spell": "irons_spellbooks:blood_needles", "level": 2, "cooldown": 260, "priority": 2,
             "range": 18, "cooldownGroup": "wither_volley", "target": "ATTACK_TARGET"},
        ),
        "onKill": {"spell": "irons_spellbooks:heal", "level": 1, "cooldown": 200, "target": "SELF"},
        "dropRules": [
            {"select": {"healthAbove": 0.0}, "chance": 1.0, "drops": [
                {"item": "minecraft:coal", "minCount": 1, "maxCount": 3, "looting": True},
                {"item": "minecraft:wither_skeleton_skull", "chance": 0.05, "looting": True},
            ]},
        ],
    },
    "blaze": {
        "_comment": "Blaze chanter: fireballs and a fire-breath channel share one cooldown group; explodes when hurt.",
        "goals": goals(
            {"spell": "irons_spellbooks:fireball", "level": 2, "cooldown": 180, "priority": 1,
             "range": 20, "castTime": 15, "cooldownGroup": "blaze_cycle", "target": "ATTACK_TARGET"},
            {"spell": "irons_spellbooks:fire_breath", "level": 1, "cooldown": 360, "priority": 2,
             "range": 10, "castTime": 40, "cooldownGroup": "blaze_cycle",
             "interruptOnDamage": True, "target": "ATTACK_TARGET"},
        ),
        "onHurt": {"spell": "irons_spellbooks:firecracker", "level": 1, "cooldown": 300, "range": 3,
                   "target": {"type": "POSITION", "positionMode": "EYES_LOOK"}},
    },
    "enderman": {
        "_comment": "Void walker: fires magic arrows and blinks away when hurt.",
        "goals": goals(
            {"spell": "irons_spellbooks:magic_arrow", "level": 2, "cooldown": 240, "priority": 1,
             "range": 24, "castTime": 20, "checkInterval": 5, "target": "ATTACK_TARGET"},
        ),
        "onHurt": {"spell": "irons_spellbooks:teleport", "level": 1, "cooldown": 240, "range": 10,
                   "target": {"type": "POSITION", "positionMode": "RANDOM_IN_RANGE"}},
    },
    "iron_golem": {
        "_comment": "Iron warden: quakes the ground around its target and fortifies itself when hurt.",
        "goals": goals(
            {"spell": "irons_spellbooks:earthquake", "level": 1, "cooldown": 300, "priority": 1,
             "range": 10, "castTime": 30, "checkInterval": 20, "target": "ATTACK_TARGET"},
        ),
        "onHurt": {"spell": "irons_spellbooks:fortify", "level": 1, "cooldown": 400, "target": "SELF",
                   "conditions": {"healthBelow": 0.8}},
    },
}


def read_spell_ids(jar_path: Path) -> set[str]:
    """Collect short spell ids from the Iron's Spellbooks language file."""
    if not jar_path.is_file():
        raise SystemExit(f"missing jar: {jar_path}")
    with zipfile.ZipFile(jar_path) as jar:
        lang = json.loads(jar.read("assets/irons_spellbooks/lang/en_us.json").decode("utf-8"))
    prefix = "spell.irons_spellbooks."
    return {key[len(prefix):] for key in lang if key.startswith(prefix)}


def main() -> int:
    PACKS.mkdir(parents=True, exist_ok=True)

    (OUT / "pack.mcmeta").write_text(json.dumps({
        "pack": {
            "pack_format": 15,
            "description": "SpellMob Showcase - 15 vanilla mobs that cast Iron's Spells",
        }
    }, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    for entity, payload in ENTITIES.items():
        (PACKS / f"{entity}.json").write_text(
            json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    ids = read_spell_ids(ROOT / "libs" / "irons_spellbooks-1.20.1-3.4.0.9.jar")
    if not ids:
        raise SystemExit("could not read spell ids from the Iron's Spellbooks jar")

    problems = []
    spells = 0
    for entity, payload in ENTITIES.items():
        for path, cast in list(_walk(payload)):
            if "spell" in cast:
                spells += 1
                spell = cast["spell"]
                if spell.startswith("irons_spellbooks:"):
                    short = spell.split(":", 1)[1]
                    if short not in ids:
                        problems.append(f"{entity}{path}: unknown spell {spell}")
                else:
                    problems.append(f"{entity}{path}: not an ISS spell {spell}")
            target = cast.get("target")
            if isinstance(target, str):
                if target not in ALLOWED_TARGETS:
                    problems.append(f"{entity}{path}: bad target {target}")
            elif isinstance(target, dict):
                if target.get("type", "ATTACK_TARGET") not in ALLOWED_TARGETS:
                    problems.append(f"{entity}{path}: bad target type {target.get('type')}")
                if target.get("positionMode", "RELATIVE") not in ALLOWED_MODES:
                    problems.append(f"{entity}{path}: bad positionMode {target.get('positionMode')}")
                if target.get("sort", "NEAREST") not in ALLOWED_SORTS:
                    problems.append(f"{entity}{path}: bad sort {target.get('sort')}")
            extra = set(cast) - ALLOWED_CAST
            if extra:
                problems.append(f"{entity}{path}: unknown cast keys {sorted(extra)}")

        for rule in payload.get("dropRules", []):
            sel = rule.get("select", {})
            if not sel:
                problems.append(f"{entity}: dropRules entry needs a select")
            for key in set(sel) - ALLOWED_SELECT:
                problems.append(f"{entity}: unknown select key {key}")
            for entry in rule.get("drops", []):
                if ("item" in entry) == ("lootTable" in entry):
                    problems.append(f"{entity}: drop entry needs exactly one of item/lootTable")

        for variant in payload.get("variants", []):
            sel = variant.get("select", {})
            if not sel:
                problems.append(f"{entity}: variant needs a select")
            for key in set(sel) - ALLOWED_SELECT:
                problems.append(f"{entity}: unknown select key {key}")

    if problems:
        print("FAILED")
        for problem in problems:
            print(" -", problem)
        return 1

    files = sorted(p.name for p in PACKS.glob("*.json"))
    print(f"OK  entities={len(files)} spells={spells}")
    print("    " + ", ".join(files))
    return 0


def _walk(payload):
    for key in ("goals",):
        for index, cast in enumerate(payload.get(key, [])):
            yield f".{key}[{index}]", cast
    for key in ("onHurt", "onTarget", "onDeath", "onKill"):
        if key in payload:
            yield f".{key}", payload[key]
    for index, variant in enumerate(payload.get("variants", [])):
        for sub_index, cast in enumerate(variant.get("goals", [])):
            yield f".variants[{index}].goals[{sub_index}]", cast
        for key in ("onHurt", "onTarget", "onDeath", "onKill"):
            if key in variant:
                yield f".variants[{index}].{key}", variant[key]


if __name__ == "__main__":
    raise SystemExit(main())
