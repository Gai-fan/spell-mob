"""Generates the five summon-only boss definitions shipped inside the mod jar.

Usage:  python gen_five_bosses.py <path-to-irons-spellbooks.jar> [output-root]

Spells that the jar does not know (for example the four evocation/ender spells that
only exist in 1.21.1) are skipped, so the same script produces a valid pack for both
branches: 1.20.1 simply gets the subset its Iron's Spellbooks version understands.
"""

import json
import os
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAR = sys.argv[1]
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "src", "main", "resources", "data")

with zipfile.ZipFile(JAR) as jar:
    names = [n for n in jar.namelist() if n.endswith("lang/zh_cn.json")]
    lang = json.loads(jar.read(names[0]).decode("utf-8"))

available = set()
for key in lang:
    if key.startswith("spell.") and not key.endswith(".guide"):
        spell_id = key[len("spell."):].replace(".", ":", 1)
        available.add(spell_id.split(":", 1)[1])

# (namespace, file, tag, display name, range, [(spell, target[, castTime])])
BOSSES = [
    ("twilightforest", "naga", "spellmob_naga", "娜迦", 20, [
        ("acid_orb", "ATTACK_TARGET"),
        ("blight", "ATTACK_TARGET"),
        ("earthquake", "ATTACK_TARGET"),
        ("firefly_swarm", "ATTACK_TARGET"),
        ("oakskin", "SELF"),
        ("poison_arrow", "ATTACK_TARGET"),
        ("poison_breath", "ATTACK_TARGET", 60),
        ("poison_splash", "ATTACK_TARGET"),
        ("root", "ATTACK_TARGET"),
        ("spider_aspect", "SELF"),
        ("stomp", "ATTACK_TARGET"),
    ]),
    ("twilightforest", "lich", "spellmob_lich", "巫妖", 24, [
        ("summon_vex", "ATTACK_TARGET"),
        ("fang_ward", "SELF"),
        ("chain_creeper", "ATTACK_TARGET"),
        ("slow", "ATTACK_TARGET"),
        ("throw", "ATTACK_TARGET"),
        ("starfall", "ATTACK_TARGET", 60),
        ("arcane_shackle", "ATTACK_TARGET"),
        ("summon_swords", "ATTACK_TARGET"),
        ("shadow_slash", "ATTACK_TARGET"),
        ("wither_skull", "ATTACK_TARGET"),
        ("ray_of_siphoning", "ATTACK_TARGET", 60),
        ("blood_slash", "ATTACK_TARGET"),
        ("eldritch_blast", "ATTACK_TARGET"),
    ]),
    ("twilightforest", "alpha_yeti", "spellmob_yeti", "雪怪首领", 24, [
        ("blizzard", "ATTACK_TARGET"),
        ("cone_of_cold", "ATTACK_TARGET", 60),
        ("frost_step", "SELF"),
        ("frostbite", "ATTACK_TARGET"),
        ("frostwave", "ATTACK_TARGET"),
        ("ice_block", "ATTACK_TARGET"),
        ("ice_spikes", "ATTACK_TARGET"),
        ("ice_tomb", "ATTACK_TARGET"),
        ("icicle", "ATTACK_TARGET"),
        ("ray_of_frost", "ATTACK_TARGET"),
        ("snowball", "ATTACK_TARGET"),
    ]),
    ("minecraft", "drowned", "spellmob_jack", "jack", 24, [
        ("ascension", "SELF"),
        ("ball_lightning", "ATTACK_TARGET"),
        ("chain_lightning", "ATTACK_TARGET"),
        ("charge", "SELF"),
        ("electrocute", "ATTACK_TARGET", 60),
        ("lightning_bolt", "ATTACK_TARGET"),
        ("lightning_lance", "ATTACK_TARGET"),
        ("shockwave", "ATTACK_TARGET"),
        ("thunder_step", "SELF"),
        ("thunderstorm", "ATTACK_TARGET"),
        ("volt_strike", "ATTACK_TARGET"),
    ]),
    ("minecraft", "phantom", "spellmob_phantom", "幻翼", 24, [
        ("magic_missile", "ATTACK_TARGET"),
        ("electrocute", "ATTACK_TARGET", 60),
        ("lightning_lance", "ATTACK_TARGET"),
        ("lightning_bolt", "ATTACK_TARGET"),
        ("thunderstorm", "ATTACK_TARGET"),
        ("sonic_boom", "ATTACK_TARGET"),
        ("gust", "ATTACK_TARGET"),
        ("dragon_breath", "ATTACK_TARGET", 60),
    ]),
]

HURT = {
    "spellmob_naga": ("oakskin", "SELF"),
    "spellmob_lich": ("fang_ward", "SELF"),
    "spellmob_yeti": ("frost_step", "SELF"),
    "spellmob_jack": ("thunder_step", "SELF"),
    "spellmob_phantom": ("gust", "ATTACK_TARGET"),
}
DEATH = {
    "spellmob_naga": ("stomp", "ATTACK_TARGET"),
    "spellmob_lich": ("wither_skull", "ATTACK_TARGET"),
    "spellmob_yeti": ("blizzard", "ATTACK_TARGET"),
    "spellmob_jack": ("thunderstorm", "ATTACK_TARGET"),
    "spellmob_phantom": ("sonic_boom", "ATTACK_TARGET"),
}


def cast(spell, target, cast_time=None, cooldown=160, priority=1, rng=24):
    if spell not in available:
        return None
    entry = {
        "spell": "irons_spellbooks:" + spell,
        "level": 3,
        "cooldown": cooldown,
        "priority": priority,
        "range": rng,
        "target": target,
    }
    if cast_time is not None:
        entry["castTime"] = cast_time
    return entry


written = 0
for namespace, file_name, tag, display, rng, spells in BOSSES:
    goals = []
    for index, spell in enumerate(spells):
        spell_id, target = spell[0], spell[1]
        cast_time = spell[2] if len(spell) > 2 else None
        entry = cast(spell_id, target, cast_time, cooldown=min(240, 100 + 20 * index),
                     priority=1 + index % 3, rng=rng)
        if entry is not None:
            goals.append(entry)

    if not goals:
        continue

    hurt_spell = HURT[tag]
    death_spell = DEATH[tag]
    data = {
        "_comment": (
            f"{display}：只能通过召唤获得。给它打上 spellmob_boss 与 {tag} 标签后才会施法，"
            "野生同类不受影响。血量 300 由召唤命令设置。"
        ),
        "variants": [
            {
                "select": {"tags": ["spellmob_boss", tag]},
                "override": True,
                "goals": goals,
                "onHurt": cast(hurt_spell[0], hurt_spell[1], cooldown=200, priority=0, rng=rng),
                "onDeath": cast(death_spell[0], death_spell[1], cooldown=0, priority=0, rng=rng),
            }
        ],
        "dropRules": [
            {
                "select": {"tags": ["spellmob_boss", tag]},
                "chance": 1.0,
                "drops": [
                    {"item": "minecraft:emerald", "min": 8, "max": 16, "looting": True},
                ],
            }
        ],
    }
    # onHurt / onDeath are optional definitions; drop the key instead of writing null.
    variant = data["variants"][0]
    if variant["onHurt"] is None:
        del variant["onHurt"]
    if variant["onDeath"] is None:
        del variant["onDeath"]

    target_dir = os.path.join(OUT, namespace, "spell_casters")
    os.makedirs(target_dir, exist_ok=True)
    path = os.path.join(target_dir, file_name + ".json")
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(data, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    written += 1
    print(f"{path}: {len(goals)} goals")

print("files", written, "spells available", len(available))
