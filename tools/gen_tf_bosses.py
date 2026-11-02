"""Generates the five Twilight Forest ordinary-mob bosses.

Run from the spell_mob project root:
    python tools/gen_tf_bosses.py
"""
import json
import os

ROOT = 'examples/twilight_forest_bosses_1_21_1'
OUT = ROOT + '/data/twilightforest/spell_casters'
TAGS = ["spellmob_boss", "tf_boss"]


def spell(name, level, cooldown, priority, rng, target="ATTACK_TARGET", **extra):
    data = {
        "spell": "irons_spellbooks:" + name,
        "level": level,
        "cooldown": cooldown,
        "priority": priority,
        "range": rng,
        "target": target,
    }
    data.update(extra)
    return data


def position(mode, offset=None, rng=8.0):
    target = {"type": "POSITION", "positionMode": mode, "range": rng}
    if offset is not None:
        target["relativeOffset"] = offset
    return target


BOSSES = {}

BOSSES['redcap_sapper'] = {
    "_comment": "爆破大师·雷帽：红帽工兵分支首领。火系爆发 + 召唤恼鬼；半血后进入引导与轰炸阶段，死亡时以火球炸开。",
    "variants": [
        {
            "select": {"name": "雷帽工兵·爆破大师", "tags": TAGS, "healthBelow": 0.5},
            "override": True,
            "goals": [
                spell("fire_breath", 2, 200, 2, 10, castTime=40, interruptOnDamage=True),
                spell("blaze_storm", 2, 260, 2, 14, castTime=40),
                spell("raise_hell", 3, 300, 1, 16, castTime=40),
                spell("flaming_barrage", 2, 140, 1, 20),
                spell("heat_surge", 2, 180, 2, 12),
                spell("firebolt", 2, 90, 1, 22),
            ],
            "onHurt": spell("wall_of_fire", 1, 240, 0, 0, position("RELATIVE", [0, 0, 2])),
            "onDeath": spell("fireball", 3, 0, 0, 0, position("RANDOM_IN_RANGE", None, 6.0)),
            "onKill": spell("fire_arrow", 2, 160, 0, 20),
        },
        {
            "select": {"name": "雷帽工兵·爆破大师", "tags": TAGS},
            "override": True,
            "goals": [
                spell("fireball", 3, 100, 1, 24),
                spell("magma_bomb", 2, 160, 2, 20),
                spell("firecracker", 2, 200, 2, 16, castTime=10),
                spell("lob_creeper", 2, 220, 3, 18),
                spell("scorch", 2, 180, 3, 12),
                spell("summon_vex", 1, 320, 4, 20, castTime=20),
            ],
            "onHurt": spell("wall_of_fire", 1, 240, 0, 0, position("RELATIVE", [0, 0, 2])),
            "onTarget": spell("slow", 1, 150, 0, 20),
        },
    ],
    "dropRules": [
        {
            "select": {"tags": TAGS},
            "drops": [
                {"item": "minecraft:emerald", "minCount": 3, "maxCount": 6, "looting": True},
                {"item": "minecraft:blaze_powder", "minCount": 2, "maxCount": 4},
                {"item": "minecraft:enchanted_golden_apple", "chance": 0.15},
            ],
        }
    ],
}

BOSSES['stable_ice_core'] = {
    "_comment": "霜核执政官：稳定冰核的异变体。冰系控场 + 雷电；半血后引导冰锥与暴风雪，死亡时引爆冰墓。",
    "variants": [
        {
            "select": {"name": "霜核·执政官", "tags": TAGS, "healthBelow": 0.5},
            "override": True,
            "goals": [
                spell("blizzard", 2, 180, 1, 18),
                spell("cone_of_cold", 2, 200, 2, 10, castTime=100, interruptOnDamage=True),
                spell("thunderstorm", 2, 260, 2, 20),
                spell("frostbite", 2, 120, 2, 16),
                spell("lightning_lance", 2, 160, 3, 24),
            ],
            "onHurt": spell("frost_step", 1, 180, 0, 0, position("RELATIVE", [0, 0, -4])),
            "onDeath": spell("ice_tomb", 2, 0, 0, 0, position("RANDOM_IN_RANGE", None, 6.0)),
        },
        {
            "select": {"name": "霜核·执政官", "tags": TAGS},
            "override": True,
            "goals": [
                spell("ice_spikes", 2, 100, 1, 20, castTime=20),
                spell("ray_of_frost", 2, 140, 2, 20),
                spell("icicle", 2, 120, 2, 24),
                spell("frostwave", 2, 180, 3, 14),
                spell("ice_block", 1, 240, 3, 12),
                spell("summon_polar_bear", 1, 420, 4, 16, castTime=20),
            ],
            "onHurt": spell("frost_step", 1, 180, 0, 0, position("RELATIVE", [0, 0, -4])),
            "onTarget": spell("slow", 1, 160, 0, 20),
        },
    ],
    "dropRules": [
        {
            "select": {"tags": TAGS},
            "drops": [
                {"item": "minecraft:blue_ice", "minCount": 4, "maxCount": 8},
                {"item": "minecraft:packed_ice", "minCount": 8, "maxCount": 16},
                {"item": "minecraft:diamond", "chance": 0.12},
            ],
        }
    ],
}

BOSSES['death_tome'] = {
    "_comment": "噬页者·万卷：死亡之书的异变体。末影与邪术混合，半血后开启黑洞与流星；死亡时残留邪术冲击。",
    "variants": [
        {
            "select": {"name": "噬页者·万卷", "tags": TAGS, "healthBelow": 0.5},
            "override": True,
            "goals": [
                spell("black_hole", 2, 300, 1, 16, castTime=40),
                spell("starfall", 2, 240, 2, 20, castTime=60, interruptOnDamage=True),
                spell("dragon_breath", 1, 260, 3, 10, castTime=60),
                spell("gravity_fissure", 2, 200, 2, 18),
                spell("summon_swords", 2, 280, 3, 16, castTime=30),
            ],
            "onHurt": spell("teleport", 1, 200, 0, 0, position("RANDOM_IN_RANGE", None, 8.0)),
            "onDeath": spell("eldritch_blast", 2, 0, 0, 0, position("RANDOM_IN_RANGE", None, 5.0)),
        },
        {
            "select": {"name": "噬页者·万卷", "tags": TAGS},
            "override": True,
            "goals": [
                spell("magic_missile", 3, 80, 1, 20),
                spell("magic_arrow", 2, 120, 2, 24),
                spell("eldritch_blast", 2, 140, 2, 16),
                spell("sonic_boom", 2, 180, 3, 14),
                spell("telekinesis", 1, 260, 4, 16, castTime=80),
                spell("abyssal_shroud", 1, 300, 5, 8),
                spell("planar_sight", 1, 320, 6, 12),
            ],
            "onHurt": spell("teleport", 1, 200, 0, 0, position("RANDOM_IN_RANGE", None, 8.0)),
            "onTarget": spell("slow", 1, 150, 0, 20),
        },
    ],
    "dropRules": [
        {
            "select": {"tags": TAGS},
            "drops": [
                {"item": "minecraft:ender_pearl", "minCount": 2, "maxCount": 5},
                {"item": "minecraft:book", "minCount": 3, "maxCount": 6},
                {"item": "minecraft:amethyst_shard", "chance": 0.2, "minCount": 2, "maxCount": 4},
            ],
        }
    ],
}

BOSSES['wraith'] = {
    "_comment": "哀嚎之影：幽魂异变体。鲜血系消耗与瞬移；半血后引导虹吸，击杀回血，死亡时召唤亡者。",
    "variants": [
        {
            "select": {"name": "哀嚎·血影", "tags": TAGS, "healthBelow": 0.5},
            "override": True,
            "goals": [
                spell("ray_of_siphoning", 2, 220, 2, 16, castTime=80, interruptOnDamage=True),
                spell("sacrifice", 2, 300, 3, 12),
                spell("shadow_slash", 2, 140, 1, 12),
                spell("echoing_strikes", 2, 200, 2, 14),
                spell("blood_step", 1, 160, 3, 16),
            ],
            "onKill": spell("heal", 2, 260, 0, 0, "SELF"),
            "onDeath": spell("raise_dead", 2, 0, 0, 0, position("RELATIVE", [0, 0, 2])),
        },
        {
            "select": {"name": "哀嚎·血影", "tags": TAGS},
            "override": True,
            "goals": [
                spell("blood_needles", 3, 90, 1, 22),
                spell("blood_slash", 2, 120, 2, 10),
                spell("wither_skull", 2, 180, 2, 18),
                spell("devour", 2, 240, 3, 8),
                spell("heartstop", 1, 420, 5, 12),
            ],
            "onHurt": spell("blood_step", 1, 200, 0, 0, position("RELATIVE", [0, 0, 4])),
            "onTarget": spell("slow", 1, 160, 0, 20),
        },
    ],
    "dropRules": [
        {
            "select": {"tags": TAGS},
            "drops": [
                {"item": "minecraft:redstone", "minCount": 4, "maxCount": 9},
                {"item": "minecraft:ghast_tear", "minCount": 1, "maxCount": 2},
                {"item": "minecraft:netherite_scrap", "chance": 0.15},
            ],
        }
    ],
}

BOSSES['king_spider'] = {
    "_comment": "蛛巢女王：巨型蜘蛛首领。自然系毒与束缚，半血后引导毒息并引发地震；死亡时留下亡者。",
    "variants": [
        {
            "select": {"name": "蛛巢·女王", "tags": TAGS, "healthBelow": 0.5},
            "override": True,
            "goals": [
                spell("poison_breath", 2, 220, 2, 10, castTime=80, interruptOnDamage=True),
                spell("earthquake", 2, 240, 2, 12),
                spell("stomp", 2, 160, 2, 8),
                spell("blight", 2, 180, 2, 14),
                spell("gluttony", 2, 320, 3, 8),
            ],
            "onDeath": spell("raise_dead", 2, 0, 0, 0, position("RANDOM_IN_RANGE", None, 5.0)),
            "onKill": spell("heal", 1, 240, 0, 0, "SELF"),
        },
        {
            "select": {"name": "蛛巢·女王", "tags": TAGS},
            "override": True,
            "goals": [
                spell("poison_splash", 3, 90, 1, 18),
                spell("poison_arrow", 2, 120, 2, 24),
                spell("root", 2, 160, 2, 14),
                spell("acid_orb", 2, 140, 2, 20),
                spell("spider_aspect", 1, 300, 4, 8),
                spell("firefly_swarm", 2, 200, 3, 16),
            ],
            "onHurt": spell("blight", 1, 220, 0, 0, position("RELATIVE", [0, 0, 2])),
            "onTarget": spell("slow", 1, 160, 0, 20),
        },
    ],
    "dropRules": [
        {
            "select": {"tags": TAGS},
            "drops": [
                {"item": "minecraft:spider_eye", "minCount": 4, "maxCount": 8},
                {"item": "minecraft:string", "minCount": 8, "maxCount": 16},
                {"item": "minecraft:golden_apple", "chance": 0.12},
            ],
        }
    ],
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, data in BOSSES.items():
        path = os.path.join(OUT, name + '.json')
        with open(path, 'w', encoding='utf-8', newline='\n') as handle:
            json.dump(data, handle, ensure_ascii=False, indent=2)
            handle.write('\n')
    with open(os.path.join(ROOT, 'pack.mcmeta'), 'w', encoding='utf-8', newline='\n') as handle:
        json.dump({"pack": {"pack_format": 48,
                            "description": "SpellMob - 暮色森林普通生物首领（1.21.1）"}},
                  handle, ensure_ascii=False, indent=2)
        handle.write('\n')
    print("written", sorted(os.listdir(OUT)))


if __name__ == '__main__':
    main()
