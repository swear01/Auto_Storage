#!/usr/bin/env python3
import argparse
import gzip
import hashlib
import json
import re
import shutil
import struct
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import NamedTuple

DEFAULT_PRISM_MINECRAFT_DIR = Path.home() / "Library/Application Support/PrismLauncher/instances/dev/minecraft"
DEFAULT_SOURCE_WORLD = "New World"
DEFAULT_WORLD_NAME = "MagicStorageGuiTest"
DEFAULT_OFFLINE_PLAYER = "MagicStorageBot"
DATAPACK_NAME = "magic_storage_gui_test"
MARKER_FILE = ".magic_storage_gui_test_world"
RUNTIME_FIXTURE_MARKER_FILE = ".magic_storage_runtime_fixture_pending"
PACK_FORMAT = 48
WORLD_DATA_VERSION = 3955
GUI_CORE_STORAGE_ID = [1297303379, -1689374253, -1229988241, 1836016741]
GUI_CORE_NETWORK_ID = [-1483115547, 1063596744, -1848102033, 706652923]
TERMINAL_SCALE_COUNTS = (10_000, 30_000)
TERMINAL_SCALE_ITEMS_PER_TYPE = 64
TERMINAL_SCALE_PROCESSING_COUNT = 130
TERMINAL_SCALE_SEGMENT_LIMIT = 63
TERMINAL_SCALE_COMPONENT = "minecraft:custom_name"
TERMINAL_SCALE_BASE_ITEMS = (
    "minecraft:oak_log",
    "minecraft:spruce_log",
    "minecraft:stone",
    "minecraft:iron_ingot",
    "magic_storage:storage_unit_t1",
    "magic_storage:storage_unit_t2",
    "magic_storage:storage_terminal",
    "magic_storage:crafting_terminal",
)

OPTION_OVERRIDES = {
    "fullscreen": "true",
    "pauseOnLostFocus": "false",
    "key_key.use": "key.keyboard.u",
    "guiScale": "4",
    "overrideWidth": "1280",
    "overrideHeight": "720",
    "tutorialStep": "none",
    "autoJump": "false",
}
OPTION_REMOVALS = {"fullscreenResolution"}

FULLSCREEN_GATE = {
    "required": True,
    "when": "after_world_ready_before_first_gui_action",
    "launch_mode": "minecraft_macos_borderless_fullscreen",
    "automatic": True,
    "accepted_methods": ["minecraft_f11_borderless"],
    "forbidden_methods": ["macos_native_fullscreen", "combined_native_and_minecraft_fullscreen"],
    "ready_log": "MS_GUI_TEST_READY",
    "verify": [
        "Minecraft content is not offset or clipped",
        "hotbar and GUI edges are fully visible",
        "macOS desktop display mode remains unchanged",
        "User confirms the entire Minecraft frame is visible",
    ],
}


class DisplayMode(NamedTuple):
    width: int
    height: int
    pixel_width: int
    pixel_height: int
    refresh_rate: int
    depth: int

    def minecraft_value(self) -> str:
        return f"{self.width}x{self.height}@{self.refresh_rate}:{self.depth}"

    def as_dict(self) -> dict[str, int]:
        return {
            "width": self.width,
            "height": self.height,
            "pixel_width": self.pixel_width,
            "pixel_height": self.pixel_height,
            "refresh_rate": self.refresh_rate,
            "depth": self.depth,
        }


def current_macos_main_display_mode(run_func=subprocess.run) -> DisplayMode:
    command = ["/usr/sbin/system_profiler", "-json", "SPDisplaysDataType"]
    result = run_func(command, text=True, capture_output=True)
    if result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip() or f"exit {result.returncode}"
        raise RuntimeError(f"system_profiler failed while reading the macOS display mode: {detail}")
    try:
        payload = json.loads(result.stdout)
    except json.JSONDecodeError as exc:
        raise RuntimeError("system_profiler returned invalid display JSON") from exc
    displays = [
        display
        for gpu in payload.get("SPDisplaysDataType", [])
        for display in gpu.get("spdisplays_ndrvs", [])
        if display.get("spdisplays_main") == "spdisplays_yes"
        and display.get("spdisplays_online") == "spdisplays_yes"
    ]
    if len(displays) != 1:
        raise RuntimeError(f"expected exactly one online main display, found {len(displays)}")
    display = displays[0]
    resolution = re.fullmatch(
        r"\s*(\d+)\s+x\s+(\d+)\s+@\s+(\d+(?:\.\d+)?)Hz\s*",
        display.get("_spdisplays_resolution", ""),
    )
    pixels = re.fullmatch(r"\s*(\d+)\s+x\s+(\d+)\s*", display.get("_spdisplays_pixels", ""))
    if resolution is None or pixels is None:
        raise RuntimeError("main display is missing an exact point or pixel resolution")
    refresh = float(resolution.group(3))
    rounded_refresh = round(refresh)
    if abs(refresh - rounded_refresh) >= 0.01:
        raise RuntimeError(f"main display refresh rate is not an integer GLFW mode: {refresh}")
    return DisplayMode(
        int(resolution.group(1)),
        int(resolution.group(2)),
        int(pixels.group(1)),
        int(pixels.group(2)),
        rounded_refresh,
        24,
    )

VOID_GENERATOR = {
    "type": "minecraft:flat",
    "biome": "minecraft:the_void",
    "layers": [{"height": 1, "block": "minecraft:air"}],
    "features": False,
    "lakes": False,
    "structure_overrides": [],
}

COPIED_RUNTIME_PATHS = (
    "region", "entities", "poi", "data", "datapacks",
    "DIM-1", "DIM1", "dimensions", "serverconfig",
    "playerdata", "advancements", "stats",
    "session.lock", "level.dat_old", "icon.png",
)

LAB = {
    "platform_y": 79,
    "reset_bounds": [-18, 79, -12, 18, 90, 12],
    "platform_bounds": [-18, 79, -12, 18, 79, 12],
    "spawn": [0, 80, 7],
}

TARGETS = {
    "storage_core": {"block": [0, 80, 0], "stand": [0.5, 80.0, 4.5], "face": [0.5, 80.5, 0.5]},
    "storage_terminal": {"block": [-1, 80, 0], "stand": [-0.5, 80.0, 4.5], "face": [-0.5, 80.5, 0.5]},
    "crafting_terminal": {"block": [1, 80, 0], "stand": [1.5, 80.0, 4.5], "face": [1.5, 80.5, 0.5]},
    "import_bus": {"block": [-1, 80, -1], "stand": [-4.5, 80.0, -0.5], "face": [-0.5, 80.5, -0.5]},
    "export_bus": {"block": [1, 80, -1], "stand": [4.5, 80.0, -0.5], "face": [1.5, 80.5, -0.5]},
    "overview": {"stand": [0.5, 85.0, 9.5], "face": [0.5, 80.0, -1.5]},
}

BASELINE = {
    "stored_items": {},
    "stored_stacks": [],
    "installed_stations": {},
    "descriptor_consumables": {},
    "station_work": {},
    "typed_resources": [],
    "energy": {
        "smelting_energy": 0,
        "blasting_energy": 0,
        "smoking_energy": 0,
        "campfire_energy": 0,
        "brew_energy": 0,
        "furnace_fuel": 0,
        "blaze_fuel": 0,
    },
    "type_capacity": {
        "finite_type_slots": 785,
        "unlimited": True,
    },
}

EXTENDED_CRAFTING_SINGULARITIES = (
    "aluminum",
    "bronze",
    "coal",
    "copper",
    "diamond",
    "electrum",
    "emerald",
    "glowstone",
    "gold",
    "invar",
    "iron",
    "lapis_lazuli",
    "lead",
    "nickel",
    "platinum",
    "redstone",
    "silver",
    "steel",
    "tin",
)
EXTENDED_CRAFTING_TEST_INGOT_TAGS = (
    "aluminum", "bronze", "electrum", "invar", "lead",
    "nickel", "platinum", "silver", "steel", "tin",
)

CRAFTING_FUEL_SCROLL_STRESS_ITEMS = tuple(
    f"minecraft:{color}_{block}"
    for block in ("wool", "terracotta")
    for color in (
        "white", "orange", "magenta", "light_blue",
        "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue",
        "brown", "green", "red", "black",
    )
) + (
    "minecraft:stone",
    "minecraft:dirt",
    "minecraft:sand",
    "minecraft:gravel",
    "minecraft:granite",
    "minecraft:diorite",
    "minecraft:andesite",
    "minecraft:deepslate",
    "minecraft:cobbled_deepslate",
    "minecraft:tuff",
    "minecraft:calcite",
    "minecraft:dripstone_block",
    "minecraft:grass_block",
    "minecraft:podzol",
    "minecraft:mycelium",
    "minecraft:clay",
    "minecraft:snow_block",
    "minecraft:ice",
    "minecraft:packed_ice",
    "minecraft:blue_ice",
    "minecraft:netherrack",
    "minecraft:soul_sand",
    "minecraft:soul_soil",
    "minecraft:basalt",
    "minecraft:blackstone",
    "minecraft:end_stone",
    "minecraft:obsidian",
    "minecraft:crying_obsidian",
)

CRAFTING_FUEL_CORE_BASELINE = {
    "storage_id": GUI_CORE_STORAGE_ID,
    "network_id": GUI_CORE_NETWORK_ID,
    "stored_items": {
        **dict.fromkeys(CRAFTING_FUEL_SCROLL_STRESS_ITEMS, 1),
        "minecraft:oak_log": 121_000,
        "minecraft:cobblestone": 999_999,
        "minecraft:redstone": 128,
        "minecraft:glowstone_dust": 16,
        "minecraft:coal": 16,
        "minecraft:charcoal": 16,
        "minecraft:copper_ingot": 16,
        "minecraft:lapis_lazuli": 16,
        "minecraft:quartz": 16,
        "minecraft:brown_mushroom": 64,
        "minecraft:red_mushroom": 64,
        "minecraft:bowl": 64,
        "minecraft:netherite_upgrade_smithing_template": 8,
        "minecraft:diamond_sword": 8,
        "minecraft:netherite_ingot": 64,
        "minecraft:iron_ingot": 64,
        "minecraft:gold_ingot": 64,
        "minecraft:bone_meal": 64,
        "minecraft:sugar_cane": 64,
        "minecraft:fishing_rod": 1,
        "minecraft:wheat_seeds": 64,
        "minecraft:fermented_spider_eye": 64,
        "minecraft:sugar": 64,
        "minecraft:milk_bucket": 64,
        "minecraft:amethyst_shard": 64,
        "minecraft:mossy_cobblestone": 64,
        "minecraft:glass_pane": 64,
        "minecraft:glass_bottle": 64,
        "minecraft:honey_bottle": 64,
        "modern_industrialization:aluminum_blade": 64,
        "ars_nouveau:source_gem": 64,
        "create:andesite_alloy": 64,
        "botania:mana_powder": 64,
        "botania:manasteel_ingot": 64,
        "botania:livingrock": 64,
        "botania:mana_pearl": 64,
        "botania:mana_diamond": 64,
        "botania:white_mystical_petal": 64,
    },
    "stored_stacks": [
        {
            "item": "extendedcrafting:singularity",
            "amount": 1,
            "components": {
                "extendedcrafting:singularity_id": f"extendedcrafting:{singularity}",
            },
        }
        for singularity in EXTENDED_CRAFTING_SINGULARITIES
    ],
    "installed_stations": {
        "magic_storage:furnace": {
            "item": "ironfurnaces:iron_furnace",
            "count": 3,
        },
        "magic_storage:blast_furnace": {
            "item": "minecraft:blast_furnace",
            "count": 1,
        },
        "magic_storage:smoker": {
            "item": "minecraft:smoker",
            "count": 1,
        },
        "magic_storage:campfire": {
            "item": "minecraft:campfire",
            "count": 1,
        },
        "magic_storage:brewing_stand": {
            "item": "minecraft:brewing_stand",
            "count": 1,
        },
        "magic_storage:crafting_table": {
            "item": "minecraft:crafting_table",
            "count": 1,
        },
        "magic_storage:stonecutter": {
            "item": "minecraft:stonecutter",
            "count": 1,
        },
        "magic_storage:smithing_table": {
            "item": "minecraft:smithing_table",
            "count": 1,
        },
        "magic_storage:extended_crafting_table": {
            "item": "extendedcrafting:ultimate_table",
            "count": 1,
        },
        "magic_storage:farmers_delight_cooking_pot": {
            "item": "farmersdelight:cooking_pot",
            "count": 1,
        },
        "magic_storage:modern_industrialization_assembler": {
            "item": "modern_industrialization:assembler",
            "count": 1,
        },
        "magic_storage:modern_industrialization_centrifuge": {
            "item": "modern_industrialization:centrifuge",
            "count": 1,
        },
        "magic_storage:modern_industrialization_chemical_reactor": {
            "item": "modern_industrialization:chemical_reactor",
            "count": 1,
        },
        "magic_storage:modern_industrialization_compressor": {
            "item": "modern_industrialization:bronze_compressor",
            "count": 1,
        },
        "magic_storage:modern_industrialization_cutting_machine": {
            "item": "modern_industrialization:bronze_cutting_machine",
            "count": 1,
        },
        "magic_storage:modern_industrialization_distillery": {
            "item": "modern_industrialization:distillery",
            "count": 1,
        },
        "magic_storage:modern_industrialization_electrolyzer": {
            "item": "modern_industrialization:electrolyzer",
            "count": 1,
        },
        "magic_storage:modern_industrialization_furnace": {
            "item": "modern_industrialization:bronze_furnace",
            "count": 1,
        },
        "magic_storage:modern_industrialization_macerator": {
            "item": "modern_industrialization:bronze_macerator",
            "count": 1,
        },
        "magic_storage:modern_industrialization_mixer": {
            "item": "modern_industrialization:bronze_mixer",
            "count": 1,
        },
        "magic_storage:modern_industrialization_packer": {
            "item": "modern_industrialization:steel_packer",
            "count": 1,
        },
        "magic_storage:modern_industrialization_polarizer": {
            "item": "modern_industrialization:polarizer",
            "count": 1,
        },
        "magic_storage:modern_industrialization_unpacker": {
            "item": "modern_industrialization:steel_unpacker",
            "count": 1,
        },
        "magic_storage:modern_industrialization_wiremill": {
            "item": "modern_industrialization:steel_wiremill",
            "count": 1,
        },
        "magic_storage:ars_nouveau_imbuement_chamber": {
            "item": "ars_nouveau:imbuement_chamber",
            "count": 1,
        },
        "magic_storage:ars_nouveau_enchanting_apparatus": {
            "item": "ars_nouveau:enchanting_apparatus",
            "count": 1,
        },
        "magic_storage:powah_energizing": {
            "item": "powah:energizing_rod_starter",
            "count": 1,
        },
        "magic_storage:powah_furnator": {
            "item": "powah:furnator_starter",
            "count": 1,
        },
        "magic_storage:industrial_foregoing_dissolution_chamber": {
            "item": "industrialforegoing:dissolution_chamber",
            "count": 1,
        },
        "magic_storage:industrial_foregoing_material_stonework_factory": {
            "item": "industrialforegoing:material_stonework_factory",
            "count": 1,
        },
        "magic_storage:create_milling": {
            "item": "create:millstone",
            "count": 1,
        },
        "magic_storage:create_crushing": {
            "item": "create:crushing_wheel",
            "count": 1,
        },
        "magic_storage:create_cutting": {
            "item": "create:mechanical_saw",
            "count": 1,
        },
        "magic_storage:create_filling": {
            "item": "create:spout",
            "count": 1,
        },
        "magic_storage:create_emptying": {
            "item": "create:item_drain",
            "count": 1,
        },
        "magic_storage:mekanism_crusher": {
            "item": "mekanism:ultimate_crushing_factory",
            "count": 2_147_483_647,
        },
        "magic_storage:mekanism_enrichment_chamber": {
            "item": "mekanism:enrichment_chamber",
            "count": 1,
        },
        "magic_storage:mekanism_energized_smelter": {
            "item": "mekanism:energized_smelter",
            "count": 1,
        },
        "magic_storage:mekanism_combiner": {
            "item": "mekanism:combiner",
            "count": 1,
        },
        "magic_storage:mekanism_osmium_compressor": {
            "item": "mekanism:osmium_compressor",
            "count": 1,
        },
        "magic_storage:mekanism_purification_chamber": {
            "item": "mekanism:purification_chamber",
            "count": 1,
        },
        "magic_storage:mekanism_chemical_injection_chamber": {
            "item": "mekanism:chemical_injection_chamber",
            "count": 1,
        },
        "magic_storage:mekanism_metallurgic_infuser": {
            "item": "mekanism:metallurgic_infuser",
            "count": 1,
        },
        "magic_storage:mekanism_precision_sawmill": {
            "item": "mekanism:precision_sawmill",
            "count": 1,
        },
        "magic_storage:mekanism_pressurized_reaction_chamber": {
            "item": "mekanism:pressurized_reaction_chamber",
            "count": 1,
        },
        "magic_storage:mekanism_rotary_condensentrator": {
            "item": "mekanism:rotary_condensentrator",
            "count": 1,
        },
        "magic_storage:mekanism_chemical_oxidizer": {
            "item": "mekanism:chemical_oxidizer",
            "count": 1,
        },
        "magic_storage:mekanism_chemical_infuser": {
            "item": "mekanism:chemical_infuser",
            "count": 1,
        },
        "magic_storage:mekanism_electrolytic_separator": {
            "item": "mekanism:electrolytic_separator",
            "count": 1,
        },
        "magic_storage:mekanism_chemical_dissolution_chamber": {
            "item": "mekanism:chemical_dissolution_chamber",
            "count": 1,
        },
        "magic_storage:mekanism_chemical_washer": {
            "item": "mekanism:chemical_washer",
            "count": 1,
        },
        "magic_storage:mekanism_chemical_crystallizer": {
            "item": "mekanism:chemical_crystallizer",
            "count": 1,
        },
        "magic_storage:mekanism_isotopic_centrifuge": {
            "item": "mekanism:isotopic_centrifuge",
            "count": 1,
        },
        "magic_storage:mekanism_antiprotonic_nucleosynthesizer": {
            "item": "mekanism:antiprotonic_nucleosynthesizer",
            "count": 1,
        },
        "magic_storage:mekanism_pigment_extractor": {
            "item": "mekanism:pigment_extractor",
            "count": 1,
        },
        "magic_storage:mekanism_pigment_mixer": {
            "item": "mekanism:pigment_mixer",
            "count": 1,
        },
        "magic_storage:mekanism_painting_machine": {
            "item": "mekanism:painting_machine",
            "count": 1,
        },
        "magic_storage:botania_mana_pool": {
            "item": "botania:mana_pool",
            "count": 1,
        },
        "magic_storage:botania_runic_altar": {
            "item": "botania:runic_altar",
            "count": 1,
        },
        "magic_storage:botania_terrestrial_agglomeration_plate": {
            "item": "botania:terrestrial_agglomeration_plate",
            "count": 1,
        },
        "magic_storage:botania_petal_apothecary": {
            "item": "botania:petal_apothecary",
            "count": 1,
        },
        "magic_storage:botania_elven_gateway": {
            "item": "botania:elven_gateway_core",
            "count": 1,
        },
    },
    "descriptor_consumables": {
        "magic_storage:axe": {"amount": 1_561, "infinite": False},
    },
    "station_work": {
        "magic_storage:farmers_delight_cooking_pot": 10_000,
        "magic_storage:modern_industrialization_assembler": 100_000,
        "magic_storage:modern_industrialization_centrifuge": 100_000,
        "magic_storage:modern_industrialization_chemical_reactor": 100_000,
        "magic_storage:modern_industrialization_compressor": 100_000,
        "magic_storage:modern_industrialization_cutting_machine": 100_000,
        "magic_storage:modern_industrialization_distillery": 100_000,
        "magic_storage:modern_industrialization_electrolyzer": 100_000,
        "magic_storage:modern_industrialization_furnace": 100_000,
        "magic_storage:modern_industrialization_macerator": 100_000,
        "magic_storage:modern_industrialization_mixer": 100_000,
        "magic_storage:modern_industrialization_packer": 100_000,
        "magic_storage:modern_industrialization_polarizer": 100_000,
        "magic_storage:modern_industrialization_unpacker": 100_000,
        "magic_storage:modern_industrialization_wiremill": 100_000,
        "magic_storage:ars_nouveau_imbuement_chamber": 10_000,
        "magic_storage:ars_nouveau_enchanting_apparatus": 10_000,
        "magic_storage:powah_energizing": 100_000,
        "magic_storage:powah_furnator": 100_000,
        "magic_storage:industrial_foregoing_dissolution_chamber": 10_000,
        "magic_storage:industrial_foregoing_material_stonework_factory": 10_000,
        "magic_storage:create_milling": 10_000,
        "magic_storage:create_crushing": 10_000,
        "magic_storage:create_cutting": 10_000,
        "magic_storage:create_filling": 10_000,
        "magic_storage:create_emptying": 10_000,
        "magic_storage:mekanism_crusher": 10_000,
        "magic_storage:mekanism_enrichment_chamber": 10_000,
        "magic_storage:mekanism_energized_smelter": 10_000,
        "magic_storage:mekanism_combiner": 10_000,
        "magic_storage:mekanism_osmium_compressor": 10_000,
        "magic_storage:mekanism_purification_chamber": 10_000,
        "magic_storage:mekanism_chemical_injection_chamber": 10_000,
        "magic_storage:mekanism_metallurgic_infuser": 10_000,
        "magic_storage:mekanism_precision_sawmill": 10_000,
        "magic_storage:mekanism_pressurized_reaction_chamber": 10_000,
        "magic_storage:mekanism_rotary_condensentrator": 10_000,
        "magic_storage:mekanism_chemical_oxidizer": 10_000,
        "magic_storage:mekanism_chemical_infuser": 10_000,
        "magic_storage:mekanism_electrolytic_separator": 10_000,
        "magic_storage:mekanism_chemical_dissolution_chamber": 10_000,
        "magic_storage:mekanism_chemical_washer": 10_000,
        "magic_storage:mekanism_chemical_crystallizer": 10_000,
        "magic_storage:mekanism_isotopic_centrifuge": 10_000,
        "magic_storage:mekanism_antiprotonic_nucleosynthesizer": 10_000,
        "magic_storage:mekanism_pigment_extractor": 10_000,
        "magic_storage:mekanism_pigment_mixer": 10_000,
        "magic_storage:mekanism_painting_machine": 10_000,
    },
    "typed_resources": [
        {
            "kind": "magic_storage:fluid",
            "resource": "minecraft:water",
            "amount": 16_000,
        },
        {
            "kind": "magic_storage:fluid",
            "resource": "minecraft:lava",
            "amount": 4_000,
        },
        {
            "kind": "magic_storage:neoforge_energy",
            "resource": "neoforge:energy",
            "amount": 500_000,
        },
        {
            "kind": "magic_storage:fluid",
            "resource": "modern_industrialization:sugar_solution",
            "amount": 16_000,
        },
        {
            "kind": "magic_storage:fluid",
            "resource": "modern_industrialization:ethanol",
            "amount": 16_000,
        },
        {
            "kind": "magic_storage:fluid",
            "resource": "modern_industrialization:benzene",
            "amount": 16_000,
        },
        {
            "kind": "magic_storage:fluid",
            "resource": "modern_industrialization:liquid_air",
            "amount": 16_000,
        },
        {
            "kind": "ars_nouveau:source",
            "resource": "ars_nouveau:source",
            "amount": 50_000,
        },
        {
            "kind": "magic_storage:fluid",
            "resource": "industrialforegoing:pink_slime",
            "amount": 16_000,
        },
        {
            "kind": "magic_storage:fluid",
            "resource": "industrialforegoing:essence",
            "amount": 16_000,
        },
        {
            "kind": "magic_storage:fluid",
            "resource": "create:honey",
            "amount": 16_000,
        },
        {
            "kind": "mekanism:chemical",
            "resource": "mekanism:oxygen",
            "amount": 5_000_000,
        },
        {
            "kind": "mekanism:chemical",
            "resource": "mekanism:hydrogen",
            "amount": 4_000_000,
        },
        {
            "kind": "mekanism:chemical",
            "resource": "mekanism:chlorine",
            "amount": 3_000_000,
        },
        {
            "kind": "botania:mana",
            "resource": "botania:mana",
            "amount": 2_000_000,
        },
    ],
    "energy": {
        "smelting_energy": 10_000,
        "blasting_energy": 10_000,
        "smoking_energy": 10_000,
        "campfire_energy": 10_000,
        "brew_energy": 10_000,
        "furnace_fuel": 32_000,
        "blaze_fuel": 9_600,
    },
}

SCENARIO_PROFILES = {
    "boot-smoke": {
        "start_target": "overview",
        "target_names": (),
        "setup_blocks": (),
        "type_capacity": {"finite_type_slots": 0, "unlimited": False},
        "player_kit": {"hotbar": {}, "inventory": []},
        "hotbar_views": {},
    },
    "terminal-left-rail": {
        "start_target": "storage_terminal",
        "target_names": ("storage_core", "storage_terminal", "crafting_terminal"),
        "setup_blocks": (
            "setblock 0 80 0 magic_storage:storage_core",
            "setblock -1 80 0 magic_storage:storage_terminal",
            "setblock 1 80 0 magic_storage:crafting_terminal",
            "setblock 0 80 -1 magic_storage:creative_storage_unit",
        ),
        "type_capacity": {"finite_type_slots": 0, "unlimited": True},
        "player_kit": {
            "hotbar": {
                "1": {"slot": "hotbar.0", "item": "magic_storage:storage_terminal", "count": 1},
                "2": {"slot": "hotbar.1", "item": "magic_storage:crafting_terminal", "count": 1},
            },
            "inventory": [],
        },
        "hotbar_views": {
            "1": {"slot": 0, "function": "view_storage_terminal", "target": "storage_terminal"},
            "2": {"slot": 1, "function": "view_crafting_terminal", "target": "crafting_terminal"},
        },
    },
    "bus-configuration": {
        "start_target": "import_bus",
        "target_names": ("storage_core", "import_bus", "export_bus"),
        "setup_blocks": (
            "setblock 0 80 0 magic_storage:storage_core",
            "setblock 0 80 -1 magic_storage:storage_unit_t1",
            "setblock -1 80 -1 magic_storage:import_bus[facing=west]",
            "setblock -2 80 -1 minecraft:barrel",
            "setblock 1 80 -1 magic_storage:export_bus[facing=east]",
            "setblock 2 80 -1 minecraft:barrel",
        ),
        "type_capacity": {"finite_type_slots": 10, "unlimited": False},
        "player_kit": {
            "hotbar": {
                "5": {"slot": "hotbar.4", "item": "magic_storage:import_bus", "count": 1},
                "6": {"slot": "hotbar.5", "item": "magic_storage:export_bus", "count": 1},
                "7": {"slot": "hotbar.6", "item": "magic_storage:wrench", "count": 1},
                "9": {"slot": "hotbar.8", "item": "minecraft:barrier", "count": 1},
            },
            "inventory": [
                {"slot": "inventory.0", "item": "minecraft:cobblestone", "count": 64},
            ],
        },
        "hotbar_views": {
            "5": {"slot": 4, "function": "view_import_bus", "target": "import_bus"},
            "6": {"slot": 5, "function": "view_export_bus", "target": "export_bus"},
            "7": {"slot": 6, "function": "view_import_bus", "target": "import_bus"},
            "9": {"slot": 8, "function": "reset_from_hotbar", "target": "reset"},
        },
    },
    "crafting-fuel-page": {
        "start_target": "crafting_terminal",
        "target_names": ("storage_core", "storage_terminal", "crafting_terminal"),
        "setup_blocks": (
            "setblock 0 80 0 magic_storage:storage_core"
            "{storageSchema:1,storageId:[I;"
            + ",".join(str(value) for value in GUI_CORE_STORAGE_ID)
            + "]}",
            "setblock -1 80 0 magic_storage:storage_terminal",
            "setblock 1 80 0 magic_storage:crafting_terminal",
            "setblock 0 80 -1 magic_storage:creative_storage_unit",
        ),
        "type_capacity": {"finite_type_slots": 0, "unlimited": True},
        "core_baseline": CRAFTING_FUEL_CORE_BASELINE,
        "reset_world": False,
        "player_kit": {
            "hotbar": {
                "1": {"slot": "hotbar.0", "item": "magic_storage:storage_terminal", "count": 1},
                "2": {"slot": "hotbar.1", "item": "magic_storage:crafting_terminal", "count": 1},
                "3": {"slot": "hotbar.2", "item": "minecraft:coal", "count": 1},
            },
            "inventory": [],
        },
        "hotbar_views": {
            "1": {"slot": 0, "function": "view_storage_terminal", "target": "storage_terminal"},
            "2": {"slot": 1, "function": "view_crafting_terminal", "target": "crafting_terminal"},
        },
    },
    "terminal-scale": {
        "start_target": "crafting_terminal",
        "target_names": ("storage_core", "storage_terminal", "crafting_terminal"),
        "setup_blocks": (
            "setblock 0 80 0 magic_storage:storage_core"
            "{storageSchema:1,storageId:[I;"
            + ",".join(str(value) for value in GUI_CORE_STORAGE_ID)
            + "]}",
            "setblock -1 80 0 magic_storage:storage_terminal",
            "setblock 1 80 0 magic_storage:crafting_terminal",
            "setblock 0 80 -1 magic_storage:creative_storage_unit",
        ),
        "type_capacity": {"finite_type_slots": 0, "unlimited": True},
        "reset_world": False,
        "player_kit": {
            "hotbar": {
                "1": {"slot": "hotbar.0", "item": "magic_storage:storage_terminal", "count": 1},
                "2": {"slot": "hotbar.1", "item": "magic_storage:crafting_terminal", "count": 1},
            },
            "inventory": [],
        },
        "hotbar_views": {
            "1": {"slot": 0, "function": "view_storage_terminal", "target": "storage_terminal"},
            "2": {"slot": 1, "function": "view_crafting_terminal", "target": "crafting_terminal"},
        },
    },
    "patchouli-guide": {
        "start_target": "overview",
        "target_names": (),
        "setup_blocks": (),
        "type_capacity": {"finite_type_slots": 0, "unlimited": False},
        "player_kit": {
            "hotbar": {
                "1": {"slot": "hotbar.0", "item": "magic_storage:guide_book", "count": 1},
            },
            "inventory": [],
        },
        "hotbar_views": {},
    },
}

STATIC_FUNCTIONS = {
    "load": """
scoreboard objectives add ms_gui_timer dummy
function magic_storage_gui_test:setup
""",
    "tick": """
execute as @a[tag=!ms_gui_ready] unless score @s ms_gui_timer matches 0.. run scoreboard players set @s ms_gui_timer 0
scoreboard players add @a[tag=!ms_gui_ready] ms_gui_timer 1
execute as @a[tag=!ms_gui_ready,scores={ms_gui_timer=3..}] run function magic_storage_gui_test:player_ready
execute as @a[tag=ms_gui_ready] run function magic_storage_gui_test:hotbar_views
""",
}

TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12


class NbtReader:
    def __init__(self, data: bytes):
        self.data = data
        self.pos = 0

    def read(self, length: int) -> bytes:
        if self.pos + length > len(self.data):
            raise ValueError("truncated NBT")
        out = self.data[self.pos:self.pos + length]
        self.pos += length
        return out

    def unpack(self, fmt: str):
        return struct.unpack(fmt, self.read(struct.calcsize(fmt)))[0]

    def read_string(self) -> str:
        length = self.unpack(">H")
        return self.read(length).decode("utf-8")

    def read_named_tag(self):
        tag_type = self.unpack(">B")
        if tag_type == TAG_END:
            return (TAG_END, "", None)
        name = self.read_string()
        return (tag_type, name, self.read_payload(tag_type))

    def read_payload(self, tag_type: int):
        if tag_type == TAG_BYTE:
            return self.unpack(">b")
        if tag_type == TAG_SHORT:
            return self.unpack(">h")
        if tag_type == TAG_INT:
            return self.unpack(">i")
        if tag_type == TAG_LONG:
            return self.unpack(">q")
        if tag_type == TAG_FLOAT:
            return self.unpack(">f")
        if tag_type == TAG_DOUBLE:
            return self.unpack(">d")
        if tag_type == TAG_BYTE_ARRAY:
            return self.read(self.unpack(">i"))
        if tag_type == TAG_STRING:
            return self.read_string()
        if tag_type == TAG_LIST:
            child_type = self.unpack(">B")
            length = self.unpack(">i")
            return (child_type, [self.read_payload(child_type) for _ in range(length)])
        if tag_type == TAG_COMPOUND:
            items = []
            while True:
                child_type = self.unpack(">B")
                if child_type == TAG_END:
                    return items
                items.append((child_type, self.read_string(), self.read_payload(child_type)))
        if tag_type == TAG_INT_ARRAY:
            return [self.unpack(">i") for _ in range(self.unpack(">i"))]
        if tag_type == TAG_LONG_ARRAY:
            return [self.unpack(">q") for _ in range(self.unpack(">i"))]
        raise ValueError(f"unsupported NBT tag type {tag_type}")


def _pack_string(value: str) -> bytes:
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def _write_payload(tag_type: int, payload) -> bytes:
    if tag_type == TAG_BYTE:
        return struct.pack(">b", payload)
    if tag_type == TAG_SHORT:
        return struct.pack(">h", payload)
    if tag_type == TAG_INT:
        return struct.pack(">i", payload)
    if tag_type == TAG_LONG:
        return struct.pack(">q", payload)
    if tag_type == TAG_FLOAT:
        return struct.pack(">f", payload)
    if tag_type == TAG_DOUBLE:
        return struct.pack(">d", payload)
    if tag_type == TAG_BYTE_ARRAY:
        return struct.pack(">i", len(payload)) + bytes(payload)
    if tag_type == TAG_STRING:
        return _pack_string(payload)
    if tag_type == TAG_LIST:
        child_type, items = payload
        return struct.pack(">Bi", child_type, len(items)) + b"".join(_write_payload(child_type, item) for item in items)
    if tag_type == TAG_COMPOUND:
        return b"".join(_write_named_tag(child_type, name, item) for child_type, name, item in payload) + b"\x00"
    if tag_type == TAG_INT_ARRAY:
        return struct.pack(">i", len(payload)) + b"".join(struct.pack(">i", item) for item in payload)
    if tag_type == TAG_LONG_ARRAY:
        return struct.pack(">i", len(payload)) + b"".join(struct.pack(">q", item) for item in payload)
    raise ValueError(f"unsupported NBT tag type {tag_type}")


def _write_named_tag(tag_type: int, name: str, payload) -> bytes:
    return struct.pack(">B", tag_type) + _pack_string(name) + _write_payload(tag_type, payload)


def _read_gzip_nbt(path: Path):
    return NbtReader(gzip.decompress(path.read_bytes())).read_named_tag()


def _write_gzip_nbt(path: Path, root) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(_write_named_tag(*root)))


def _find_compound_item(compound: list, name: str):
    for index, item in enumerate(compound):
        if item[1] == name:
            return index, item
    return None, None


def _set_compound_item(compound: list, name: str, tag_type: int, payload) -> None:
    index, _ = _find_compound_item(compound, name)
    item = (tag_type, name, payload)
    if index is None:
        compound.append(item)
    else:
        compound[index] = item


def _remove_compound_item(compound: list, name: str) -> None:
    index, _ = _find_compound_item(compound, name)
    if index is not None:
        compound.pop(index)


def _data_compound(root):
    tag_type, _, payload = root
    if tag_type != TAG_COMPOUND:
        raise ValueError("level.dat root is not a compound")
    _, data_item = _find_compound_item(payload, "Data")
    if data_item is None or data_item[0] != TAG_COMPOUND:
        raise ValueError("level.dat is missing Data compound")
    return data_item[2]


def _require_compound(compound: list, name: str) -> list:
    _, item = _find_compound_item(compound, name)
    if item is None or item[0] != TAG_COMPOUND:
        raise ValueError(f"level.dat is missing {name} compound")
    return item[2]


def _require_item(compound: list, name: str, tag_type: int):
    _, item = _find_compound_item(compound, name)
    if item is None or item[0] != tag_type:
        raise ValueError(f"level.dat is missing {name} tag type {tag_type}")
    return item[2]


def _item_stack_nbt(
        item_id: str,
        count: int = 1,
        components: dict[str, str] | None = None,
) -> list:
    if not item_id or count <= 0:
        raise ValueError(f"invalid preloaded item stack: {item_id} x{count}")
    result = [
        (TAG_INT, "count", count),
        (TAG_STRING, "id", item_id),
    ]
    if components:
        if any(not key or not value for key, value in components.items()):
            raise ValueError(f"invalid preloaded item components: {components}")
        result.append((
            TAG_COMPOUND,
            "components",
            [
                (TAG_STRING, key, value)
                for key, value in sorted(components.items())
            ],
        ))
    return result


def validate_scale_types(
    scenario_name: str,
    scale_types: int | None,
    items_per_type: int | None = None,
) -> None:
    if scenario_name == "terminal-scale":
        if scale_types is None:
            raise ValueError(
                "terminal-scale --scale-types is required and must be 10000 or 30000"
            )
        if scale_types not in TERMINAL_SCALE_COUNTS:
            raise ValueError("--scale-types accepts only 10000 or 30000")
    elif scale_types is not None:
        raise ValueError("--scale-types is only valid for terminal-scale")
    if scenario_name == "terminal-scale":
        if items_per_type is not None and items_per_type <= 0:
            raise ValueError("--items-per-type must be positive")
    elif items_per_type is not None:
        raise ValueError("--items-per-type is only valid for terminal-scale")


def _terminal_scale_variant(index: int) -> tuple[str, str, int]:
    item_id = TERMINAL_SCALE_BASE_ITEMS[index % len(TERMINAL_SCALE_BASE_ITEMS)]
    custom_name = json.dumps(
        {
            "text": f"Terminal Scale {index:05d}",
            "italic": False,
        },
        separators=(",", ":"),
        sort_keys=True,
    )
    amount = ((index * 104_729) % 1_000_000) + 1
    return item_id, custom_name, amount


def _terminal_scale_core_baseline(scale_types: int) -> dict:
    return {
        "storage_id": list(GUI_CORE_STORAGE_ID),
        "network_id": list(GUI_CORE_NETWORK_ID),
        "stored_items": {},
        "stored_stacks": [],
        "installed_stations": {},
        "descriptor_consumables": {},
        "station_work": {},
        "typed_resources": [],
        "energy": dict(BASELINE["energy"]),
        "scale_types": scale_types,
    }


def terminal_scale_summary(scale_types: int) -> dict:
    digest = hashlib.sha256()
    amount_sum = 0
    for index in range(scale_types):
        item_id, custom_name, amount = _terminal_scale_variant(index)
        digest.update(f"{item_id}\0{custom_name}\0{amount}\n".encode())
        amount_sum += amount
    return {
        "generator_schema": 1,
        "type_count": scale_types,
        "segment_limit": TERMINAL_SCALE_SEGMENT_LIMIT,
        "segment_count": (
            scale_types + TERMINAL_SCALE_SEGMENT_LIMIT - 1
        ) // TERMINAL_SCALE_SEGMENT_LIMIT,
        "repository_records": 1,
        "base_item_ids": list(TERMINAL_SCALE_BASE_ITEMS),
        "component": TERMINAL_SCALE_COMPONENT,
        "key_sha256": digest.hexdigest(),
        "amount_sum": amount_sum,
    }


def _core_repository_root(core_baseline: dict) -> tuple:
    stored_items = core_baseline["stored_items"]
    inventory_entries = [
        [
            (TAG_LONG, "count", amount),
            (TAG_COMPOUND, "item", _item_stack_nbt(item_id)),
        ]
        for item_id, amount in sorted(stored_items.items())
    ]
    inventory_entries.extend(
        [
            (TAG_LONG, "count", stack["amount"]),
            (
                TAG_COMPOUND,
                "item",
                _item_stack_nbt(stack["item"], components=stack["components"]),
            ),
        ]
        for stack in core_baseline.get("stored_stacks", [])
    )
    scale_types = core_baseline.get("scale_types")
    if scale_types is not None:
        validate_scale_types("terminal-scale", scale_types)
        inventory_entries.extend(
            [
                (TAG_LONG, "count", amount),
                (
                    TAG_COMPOUND,
                    "item",
                    _item_stack_nbt(
                        item_id,
                        components={TERMINAL_SCALE_COMPONENT: custom_name},
                    ),
                ),
            ]
            for item_id, custom_name, amount in (
                _terminal_scale_variant(index)
                for index in range(scale_types)
            )
        )
    inventory_segments = [
        [
            (
                TAG_LIST,
                "entries",
                (
                    TAG_COMPOUND,
                    inventory_entries[
                        offset:offset + TERMINAL_SCALE_SEGMENT_LIMIT
                    ],
                ),
            )
        ]
        for offset in range(
            0,
            len(inventory_entries),
            TERMINAL_SCALE_SEGMENT_LIMIT,
        )
    ]
    descriptor_consumables = [
        [
            (TAG_STRING, "descriptorId", descriptor_id),
            (TAG_LONG, "amount", value["amount"]),
            (TAG_BYTE, "infinite", 1 if value["infinite"] else 0),
        ]
        for descriptor_id, value in sorted(
            core_baseline["descriptor_consumables"].items()
        )
    ]
    machine_descriptors = [
        [
            (TAG_STRING, "descriptorId", descriptor_id),
            (
                TAG_COMPOUND,
                "item",
                _item_stack_nbt(value["item"]),
            ),
            (TAG_LONG, "count", value["count"]),
        ]
        for descriptor_id, value in sorted(
            core_baseline["installed_stations"].items()
        )
    ]
    machine_work = [
        [
            (TAG_STRING, "descriptorId", descriptor_id),
            (TAG_LONG, "amount", amount),
        ]
        for descriptor_id, amount in sorted(core_baseline["station_work"].items())
    ]
    resource_entries = [
        [
            (TAG_STRING, "kind", value["kind"]),
            (TAG_STRING, "resource", value["resource"]),
            (TAG_COMPOUND, "variant", []),
            (TAG_LONG, "amount", value["amount"]),
        ]
        for value in sorted(
            core_baseline["typed_resources"],
            key=lambda entry: (entry["kind"], entry["resource"]),
        )
    ]
    storage = [
        (TAG_INT_ARRAY, "storageId", core_baseline["storage_id"]),
        (TAG_INT_ARRAY, "networkId", core_baseline["network_id"]),
        (
            TAG_COMPOUND,
            "energy",
            [
                (TAG_LONG, energy_id, amount)
                for energy_id, amount in core_baseline["energy"].items()
            ],
        ),
        (
            TAG_LIST,
            "descriptorConsumables",
            (TAG_COMPOUND, descriptor_consumables),
        ),
        (
            TAG_LIST,
            "machineDescriptors",
            (TAG_COMPOUND, machine_descriptors),
        ),
        (TAG_LIST, "machineWork", (TAG_COMPOUND, machine_work)),
        (
            TAG_LIST,
            "inventorySegments",
            (TAG_COMPOUND, inventory_segments),
        ),
        (
            TAG_COMPOUND,
            "resourceLedger",
            [
                (TAG_INT, "schema", 1),
                (TAG_LIST, "entries", (TAG_COMPOUND, resource_entries)),
            ],
        ),
    ]
    return (
        TAG_COMPOUND,
        "",
        [
            (
                TAG_COMPOUND,
                "data",
                [
                    (TAG_INT, "schemaVersion", 1),
                    (TAG_LIST, "storages", (TAG_COMPOUND, [storage])),
                    (TAG_LIST, "recoveries", (TAG_COMPOUND, [])),
                ],
            ),
            (TAG_INT, "DataVersion", WORLD_DATA_VERSION),
        ],
    )


def install_core_repository_baseline(world_dir: Path, profile: dict) -> None:
    core_baseline = profile.get("core_baseline")
    if core_baseline is None:
        return
    _write_gzip_nbt(
        world_dir / "data" / "magic_storage_core_storages.dat",
        _core_repository_root(core_baseline),
    )


def _rewrite_overworld_as_true_void(data: list) -> None:
    worldgen = _require_compound(data, "WorldGenSettings")
    dimensions = _require_compound(worldgen, "dimensions")
    overworld = _require_compound(dimensions, "minecraft:overworld")
    void_settings = [
        (TAG_LIST, "structure_overrides", (TAG_STRING, [])),
        (TAG_LIST, "layers", (TAG_COMPOUND, [[
            (TAG_INT, "height", 1),
            (TAG_STRING, "block", "minecraft:air"),
        ]])),
        (TAG_BYTE, "lakes", 0),
        (TAG_BYTE, "features", 0),
        (TAG_STRING, "biome", "minecraft:the_void"),
    ]
    generator = [
        (TAG_STRING, "type", "minecraft:flat"),
        (TAG_COMPOUND, "settings", void_settings),
    ]
    _set_compound_item(overworld, "generator", TAG_COMPOUND, generator)


def update_level_dat(
    path: Path,
    level_name: str,
    allow_commands: bool = True,
) -> None:
    root = _read_gzip_nbt(path)
    data = _data_compound(root)
    _set_compound_item(data, "LevelName", TAG_STRING, level_name)
    _set_compound_item(data, "allowCommands", TAG_BYTE, 1 if allow_commands else 0)
    _set_compound_item(data, "SpawnX", TAG_INT, LAB["spawn"][0])
    _set_compound_item(data, "SpawnY", TAG_INT, LAB["spawn"][1])
    _set_compound_item(data, "SpawnZ", TAG_INT, LAB["spawn"][2])
    _rewrite_overworld_as_true_void(data)
    _remove_compound_item(data, "Player")
    _write_gzip_nbt(path, root)


def read_level_dat_summary(path: Path) -> dict:
    data = _data_compound(_read_gzip_nbt(path))
    summary = {}
    for key in ("LevelName", "allowCommands"):
        _, item = _find_compound_item(data, key)
        if item is not None:
            summary[key] = item[2]
    return summary


def read_spawn_summary(path: Path) -> dict:
    data = _data_compound(_read_gzip_nbt(path))
    return {
        "x": _require_item(data, "SpawnX", TAG_INT),
        "y": _require_item(data, "SpawnY", TAG_INT),
        "z": _require_item(data, "SpawnZ", TAG_INT),
    }


def read_void_generator_summary(path: Path) -> dict:
    data = _data_compound(_read_gzip_nbt(path))
    worldgen = _require_compound(data, "WorldGenSettings")
    dimensions = _require_compound(worldgen, "dimensions")
    overworld = _require_compound(dimensions, "minecraft:overworld")
    generator = _require_compound(overworld, "generator")
    settings = _require_compound(generator, "settings")
    layer_type, layers = _require_item(settings, "layers", TAG_LIST)
    if layer_type != TAG_COMPOUND:
        raise ValueError("level.dat true-void layers must be compounds")
    structure_type, structures = _require_item(settings, "structure_overrides", TAG_LIST)
    if structure_type != TAG_STRING:
        raise ValueError("level.dat true-void structure_overrides must be strings")
    return {
        "type": _require_item(generator, "type", TAG_STRING),
        "biome": _require_item(settings, "biome", TAG_STRING),
        "layers": [
            {
                "height": _require_item(layer, "height", TAG_INT),
                "block": _require_item(layer, "block", TAG_STRING),
            }
            for layer in layers
        ],
        "features": bool(_require_item(settings, "features", TAG_BYTE)),
        "lakes": bool(_require_item(settings, "lakes", TAG_BYTE)),
        "structure_overrides": list(structures),
    }


def _clean_lines(text: str) -> list[str]:
    return text.splitlines()


def patch_options(options_path: Path) -> bool:
    options_path.parent.mkdir(parents=True, exist_ok=True)
    original = options_path.read_text() if options_path.exists() else ""
    seen = set()
    output = []
    for line in _clean_lines(original):
        if ":" not in line:
            output.append(line)
            continue
        key, _ = line.split(":", 1)
        if key in OPTION_REMOVALS:
            continue
        if key in OPTION_OVERRIDES:
            output.append(f"{key}:{OPTION_OVERRIDES[key]}")
            seen.add(key)
        else:
            output.append(line)
    for key, value in OPTION_OVERRIDES.items():
        if key not in seen:
            output.append(f"{key}:{value}")
    updated = "\n".join(output) + ("\n" if output else "")
    if updated != original:
        staging = None
        try:
            with tempfile.NamedTemporaryFile(
                mode="w",
                prefix=f".{options_path.name}.",
                suffix=".staging",
                dir=options_path.parent,
                delete=False,
            ) as staging_file:
                staging = Path(staging_file.name)
                staging_file.write(updated)
            staging.replace(options_path)
            staging = None
        finally:
            if staging is not None and staging.exists():
                staging.unlink()
        return True
    return False


def _write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.strip() + "\n")


def build_setup_function(profile: dict) -> str:
    lines = [
        "gamerule commandBlockOutput false",
        "gamerule sendCommandFeedback false",
        "gamerule doDaylightCycle false",
        "gamerule doWeatherCycle false",
        "gamerule doMobSpawning false",
        "time set noon",
        "weather clear",
        "difficulty peaceful",
        "fill -18 80 -12 18 90 12 minecraft:air",
        "fill -18 79 -12 18 79 12 minecraft:polished_blackstone_bricks outline",
        "fill -17 79 -11 17 79 11 minecraft:smooth_stone",
        "kill @e[type=minecraft:item,x=-18,y=79,z=-12,dx=36,dy=11,dz=24]",
        "setworldspawn 0 80 7",
        "spawnpoint @a 0 80 7",
    ]
    lines.extend(profile["setup_blocks"])
    if "runtime_items_per_type" in profile:
        lines.append(
            "magic_storage _gui_test_seed 0 80 0 "
            f"{profile['runtime_items_per_type']}"
        )
    lines.extend([
        "tag @a remove ms_gui_ready",
        "scoreboard players reset @a ms_gui_timer",
    ])
    for index in range(9):
        lines.append(f"tag @a remove ms_hotbar_{index}")
    return "\n".join(lines) + "\n"


def _view_function(target_name: str) -> str:
    target = TARGETS[target_name]
    stand = " ".join(str(value) for value in target["stand"])
    face = " ".join(str(value) for value in target["face"])
    return f"tp @s {stand} facing {face}\n"


def scenario_profile(
    scenario_name: str,
    scale_types: int | None = None,
    items_per_type: int | None = None,
) -> dict:
    if scenario_name not in SCENARIO_PROFILES:
        raise ValueError(f"unknown GUI scenario profile: {scenario_name}")
    validate_scale_types(scenario_name, scale_types, items_per_type)
    profile = SCENARIO_PROFILES[scenario_name]
    if scenario_name != "terminal-scale":
        return profile
    return {
        **profile,
        "core_baseline": _terminal_scale_core_baseline(scale_types),
        "runtime_items_per_type": (
            TERMINAL_SCALE_ITEMS_PER_TYPE
            if items_per_type is None
            else items_per_type
        ),
    }


def build_reset_player_function(player_kit: dict) -> str:
    seen_slots = set()
    for entry in player_kit["hotbar"].values():
        slot = entry["slot"]
        if slot not in {f"hotbar.{index}" for index in range(9)}:
            raise ValueError(f"invalid player hotbar slot: {slot}")
        if slot in seen_slots:
            raise ValueError(f"duplicate player kit slot: {slot}")
        seen_slots.add(slot)
    for entry in player_kit["inventory"]:
        slot = entry["slot"]
        if slot not in {f"inventory.{index}" for index in range(27)}:
            raise ValueError(f"invalid player inventory slot: {slot}")
        if slot in seen_slots:
            raise ValueError(f"duplicate player kit slot: {slot}")
        seen_slots.add(slot)
    lines = [
        "gamemode creative @s",
        "effect give @s minecraft:night_vision infinite 0 true",
        "clear @s",
    ]
    for entry in player_kit["hotbar"].values():
        lines.append(
            f"item replace entity @s {entry['slot']} with {entry['item']} {entry['count']}"
        )
    for entry in player_kit["inventory"]:
        lines.append(
            f"item replace entity @s {entry['slot']} with {entry['item']} {entry['count']}"
        )
    return "\n".join(lines) + "\n"


def build_player_ready_function(profile: dict) -> str:
    lines = [
        f"function {DATAPACK_NAME}:reset_player",
        "spawnpoint @s 0 80 7",
        f"function {DATAPACK_NAME}:view_{profile['start_target']}",
        f"function {DATAPACK_NAME}:prime_hotbar_latch",
    ]
    if "runtime_items_per_type" in profile:
        lines.append("magic_storage _gui_test_warm_craftable 0 80 0")
    lines.extend([
        "tag @s add ms_gui_ready",
        "say MS_GUI_TEST_READY",
    ])
    return "\n".join(lines) + "\n"


def build_reset_from_hotbar_function(profile: dict) -> str:
    lines = []
    if profile.get("reset_world", True):
        lines.append(f"function {DATAPACK_NAME}:setup")
    lines.extend([
        f"function {DATAPACK_NAME}:reset_player",
        f"function {DATAPACK_NAME}:view_{profile['start_target']}",
    ])
    return "\n".join(lines) + "\n"


def build_hotbar_views_function(hotbar_views: dict) -> str:
    lines = []
    for view in hotbar_views.values():
        slot = view["slot"]
        lines.append(
            f"execute if entity @s[nbt={{SelectedItemSlot:{slot}}},tag=!ms_hotbar_{slot}] run function {DATAPACK_NAME}:{view['function']}"
        )
    for index in range(9):
        lines.append(f"tag @s remove ms_hotbar_{index}")
    for view in hotbar_views.values():
        slot = view["slot"]
        lines.append(f"execute if entity @s[nbt={{SelectedItemSlot:{slot}}}] run tag @s add ms_hotbar_{slot}")
    return "\n".join(lines) + "\n"


def build_prime_hotbar_latch_function(hotbar_views: dict) -> str:
    lines = [f"tag @s remove ms_hotbar_{index}" for index in range(9)]
    for view in hotbar_views.values():
        slot = view["slot"]
        lines.append(
            f"execute if entity @s[nbt={{SelectedItemSlot:{slot}}}] run tag @s add ms_hotbar_{slot}"
        )
    return "\n".join(lines) + "\n"


def build_function_bodies(
    scenario_name: str,
    scale_types: int | None = None,
    items_per_type: int | None = None,
) -> dict[str, str]:
    profile = scenario_profile(scenario_name, scale_types, items_per_type)
    hotbar_views = profile["hotbar_views"]
    bodies = dict(STATIC_FUNCTIONS)
    bodies.update({
        "setup": build_setup_function(profile),
        "reset_player": build_reset_player_function(profile["player_kit"]),
        "reset_from_hotbar": build_reset_from_hotbar_function(profile),
        "player_ready": build_player_ready_function(profile),
        "hotbar_views": build_hotbar_views_function(hotbar_views),
        "prime_hotbar_latch": build_prime_hotbar_latch_function(hotbar_views),
    })
    view_targets = {profile["start_target"]}
    view_targets.update(
        view["target"]
        for view in hotbar_views.values()
        if view["function"].startswith("view_")
    )
    for target_name in sorted(view_targets):
        bodies[f"view_{target_name}"] = _view_function(target_name)
    return bodies


def build_manifest(
    world_dir: Path,
    scenario_name: str,
    scale_types: int | None = None,
    items_per_type: int | None = None,
) -> dict:
    profile = scenario_profile(scenario_name, scale_types, items_per_type)
    core_baseline = profile.get("core_baseline", {})
    baseline = {
        **BASELINE,
        "storage_id": list(core_baseline.get("storage_id", [])),
        "network_id": list(core_baseline.get("network_id", [])),
        "stored_items": dict(core_baseline.get("stored_items", {})),
        "stored_stacks": json.loads(json.dumps(
            core_baseline.get("stored_stacks", [])
        )),
        "installed_stations": json.loads(json.dumps(
            core_baseline.get("installed_stations", {})
        )),
        "descriptor_consumables": json.loads(json.dumps(
            core_baseline.get("descriptor_consumables", {})
        )),
        "station_work": dict(core_baseline.get("station_work", {})),
        "typed_resources": json.loads(json.dumps(
            core_baseline.get("typed_resources", [])
        )),
        "energy": {
            **BASELINE["energy"],
            **core_baseline.get("energy", {}),
        },
        "type_capacity": dict(profile["type_capacity"]),
    }
    if scenario_name == "terminal-scale":
        baseline["scale_fixture"] = terminal_scale_summary(scale_types)
        baseline["runtime_fixture"] = {
            "registry": "runtime",
            "items_per_type": profile["runtime_items_per_type"],
            "installable_descriptors": "all",
            "processing_count": TERMINAL_SCALE_PROCESSING_COUNT,
            "instant_count": 1,
            "ready_log": "MS_GUI_RUNTIME_FIXTURE_READY",
        }
    commands = {
        name: f"/function {DATAPACK_NAME}:{name}"
        for name in build_function_bodies(
            scenario_name,
            scale_types,
            items_per_type,
        )
    }
    return {
        "schema_version": 5,
        "scenario": scenario_name,
        "start_target": profile["start_target"],
        "world_name": world_dir.name,
        "world_dir": str(world_dir),
        "datapack": DATAPACK_NAME,
        "pack_format": PACK_FORMAT,
        "world_generator": VOID_GENERATOR,
        "stripped_template_paths": list(COPIED_RUNTIME_PATHS),
        "lab": LAB,
        "targets": {
            name: TARGETS[name]
            for name in profile["target_names"]
        },
        "baseline": baseline,
        "player_kit": profile["player_kit"],
        "bootstrap": {
            "ready_delay_ticks": 3,
            "ready_log": "MS_GUI_TEST_READY",
            "reset_function": (
                "reset_from_hotbar"
                if any(
                    view["function"] == "reset_from_hotbar"
                    for view in profile["hotbar_views"].values()
                )
                else None
            ),
            "core_preloaded": bool(core_baseline),
            **(
                {
                    "runtime_fixture_ready_log":
                        "MS_GUI_RUNTIME_FIXTURE_READY",
                }
                if scenario_name == "terminal-scale"
                else {}
            ),
        },
        "commands": commands,
        "hotbar_views": profile["hotbar_views"],
        "fullscreen_gate": FULLSCREEN_GATE,
        "open_key": "key.keyboard.u",
        "launch_command": f'"/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher" -l dev -w "{world_dir.name}" -o {DEFAULT_OFFLINE_PLAYER}',
    }


def install_datapack(
    world_dir: Path,
    scenario_name: str,
    scale_types: int | None = None,
    items_per_type: int | None = None,
) -> dict:
    profile = scenario_profile(
        scenario_name,
        scale_types,
        items_per_type,
    )
    datapack = world_dir / "datapacks" / DATAPACK_NAME
    if datapack.exists():
        shutil.rmtree(datapack)

    manifest = build_manifest(
        world_dir,
        scenario_name,
        scale_types,
        items_per_type,
    )
    install_core_repository_baseline(world_dir, profile)
    _write_text(datapack / "pack.mcmeta", json.dumps({"pack": {"pack_format": PACK_FORMAT, "description": "Magic Storage true-void GUI test lab"}}, indent=2))
    _write_text(datapack / "data/minecraft/tags/function/load.json", json.dumps({"values": [f"{DATAPACK_NAME}:load"]}, indent=2))
    _write_text(datapack / "data/minecraft/tags/function/tick.json", json.dumps({"values": [f"{DATAPACK_NAME}:tick"]}, indent=2))
    if scenario_name == "crafting-fuel-page":
        for name in EXTENDED_CRAFTING_TEST_INGOT_TAGS:
            _write_text(
                datapack / "data/c/tags/item/ingots" / f"{name}.json",
                json.dumps(
                    {"replace": False, "values": ["minecraft:iron_ingot"]},
                    indent=2,
                ),
            )
    function_bodies = build_function_bodies(
        scenario_name,
        scale_types,
        items_per_type,
    )
    for name, body in function_bodies.items():
        _write_text(datapack / "data" / DATAPACK_NAME / "function" / f"{name}.mcfunction", body)
    _write_text(datapack / "magic_storage_gui_test_manifest.json", json.dumps(manifest, indent=2))
    runtime_marker = world_dir / RUNTIME_FIXTURE_MARKER_FILE
    if scenario_name == "terminal-scale":
        _write_text(
            runtime_marker,
            "generated by scripts/prepare_prism_gui_world.py",
        )
    elif runtime_marker.exists():
        runtime_marker.unlink()
    return manifest


def world_has_open_files(world_dir: Path) -> bool:
    if not world_dir.exists():
        return False
    lsof = shutil.which("lsof") or "/usr/sbin/lsof"
    try:
        result = subprocess.run([lsof, "+D", str(world_dir)], text=True, capture_output=True)
    except FileNotFoundError as exc:
        raise RuntimeError("lsof not found; cannot safely check whether the Prism world is open") from exc
    if result.returncode == 0:
        return bool(result.stdout.strip())
    if result.returncode == 1:
        return False
    detail = result.stderr.strip() or result.stdout.strip() or f"exit {result.returncode}"
    raise RuntimeError(f"lsof failed while checking {world_dir}: {detail}")


def _stage_template_world(source: Path, target: Path) -> Path:
    source = source.resolve()
    target = target.resolve()
    if source == target:
        raise RuntimeError("source and target worlds must be different directories")
    if not source.is_dir():
        raise RuntimeError(f"source world not found: {source}")
    if world_has_open_files(source):
        raise RuntimeError(f"source world appears to be open; quit Minecraft before copying: {source}")
    if target.exists():
        marker = target / MARKER_FILE
        if not marker.exists():
            raise RuntimeError(f"target world exists but is not marked as generated: {target}")
        if world_has_open_files(target):
            raise RuntimeError(f"target world appears to be open; quit Minecraft before recreating: {target}")
    staging = Path(tempfile.mkdtemp(prefix=f".{target.name}.staging-", dir=target.parent))
    shutil.rmtree(staging)
    try:
        shutil.copytree(source, staging)
        for relative in COPIED_RUNTIME_PATHS:
            path = staging / relative
            if path.is_dir() and not path.is_symlink():
                shutil.rmtree(path)
            elif path.exists() or path.is_symlink():
                path.unlink()
        (staging / MARKER_FILE).write_text("generated by scripts/prepare_prism_gui_world.py\n")
        return staging
    except BaseException:
        if staging.exists():
            shutil.rmtree(staging)
        raise


def _swap_staged_world(staging: Path, target: Path) -> None:
    if target.exists():
        backup = Path(tempfile.mkdtemp(prefix=f".{target.name}.backup-", dir=target.parent))
        shutil.rmtree(backup)
        target.rename(backup)
        try:
            staging.rename(target)
        except BaseException:
            backup.rename(target)
            raise
        shutil.rmtree(backup)
    else:
        staging.rename(target)


def _copy_template_world(source: Path, target: Path) -> None:
    target = target.resolve()
    staging = _stage_template_world(source, target)
    try:
        _swap_staged_world(staging, target)
    finally:
        if staging.exists():
            shutil.rmtree(staging)


def prepare_world(
    minecraft_dir: Path = DEFAULT_PRISM_MINECRAFT_DIR,
    source_world: str = DEFAULT_SOURCE_WORLD,
    target_world: str = DEFAULT_WORLD_NAME,
    *,
    scenario_name: str,
    scale_types: int | None = None,
    items_per_type: int | None = None,
    display_mode_func=current_macos_main_display_mode,
) -> dict:
    validate_scale_types(scenario_name, scale_types, items_per_type)
    minecraft_dir = minecraft_dir.expanduser().resolve()
    source = minecraft_dir / "saves" / source_world
    target = minecraft_dir / "saves" / target_world
    display_mode = display_mode_func()
    staging = _stage_template_world(source, target)
    try:
        level_dat = staging / "level.dat"
        if not level_dat.exists():
            raise RuntimeError(f"level.dat not found in generated world: {level_dat}")
        update_level_dat(level_dat, target_world, allow_commands=True)
        world_generator = read_void_generator_summary(level_dat)
        if world_generator != VOID_GENERATOR:
            raise ValueError(f"generated overworld does not match the true-void contract: {world_generator}")

        if scenario_name == "terminal-scale":
            manifest = install_datapack(
                staging,
                scenario_name,
                scale_types,
                items_per_type,
            )
        else:
            manifest = install_datapack(staging, scenario_name)
        options_path = minecraft_dir / "options.txt"
        options_changed = patch_options(options_path)
        manifest.update(
            {
                "world_name": target_world,
                "world_dir": str(target),
                "source_world": source_world,
                "options_path": str(options_path),
                "options_changed": options_changed,
                "desktop_display_mode": display_mode.as_dict(),
                "level": read_level_dat_summary(level_dat),
                "world_generator": world_generator,
                "launch_command": build_manifest(
                    target,
                    scenario_name,
                    scale_types,
                    items_per_type,
                )["launch_command"],
            }
        )
        manifest_text = json.dumps(manifest, indent=2)
        _write_text(staging / "magic_storage_gui_test_manifest.json", manifest_text)
        _write_text(
            staging / "datapacks" / DATAPACK_NAME / "magic_storage_gui_test_manifest.json",
            manifest_text,
        )
        _swap_staged_world(staging, target)
    finally:
        if staging.exists():
            shutil.rmtree(staging)
    return manifest


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="Prepare the Prism dev Magic Storage GUI test world.")
    parser.add_argument("--minecraft-dir", type=Path, default=DEFAULT_PRISM_MINECRAFT_DIR)
    parser.add_argument("--source-world", default=DEFAULT_SOURCE_WORLD)
    parser.add_argument("--world", default=DEFAULT_WORLD_NAME)
    parser.add_argument(
        "--scenario",
        choices=sorted(SCENARIO_PROFILES),
        default="boot-smoke",
    )
    parser.add_argument(
        "--scale-types",
        type=int,
        choices=TERMINAL_SCALE_COUNTS,
    )
    parser.add_argument("--items-per-type", type=int)
    args = parser.parse_args(argv[1:])
    try:
        result = prepare_world(
            args.minecraft_dir,
            args.source_world,
            args.world,
            scenario_name=args.scenario,
            scale_types=args.scale_types,
            items_per_type=args.items_per_type,
        )
    except Exception as exc:
        print(str(exc), file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
