#!/usr/bin/env python3
import gzip
import inspect
import importlib.util
import json
import shutil
import subprocess
import struct
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT_PATH = ROOT / "scripts" / "prepare_prism_gui_world.py"


TAG_BYTE = 1
TAG_INT = 3
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10


def nbt_name(value):
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def nbt_payload(tag_type, payload):
    if tag_type == TAG_BYTE:
        return struct.pack(">b", payload)
    if tag_type == TAG_INT:
        return struct.pack(">i", payload)
    if tag_type == TAG_STRING:
        return nbt_name(payload)
    if tag_type == TAG_LIST:
        child_type, items = payload
        return struct.pack(">Bi", child_type, len(items)) + b"".join(
            nbt_payload(child_type, item) for item in items
        )
    if tag_type == TAG_COMPOUND:
        return b"".join(nbt_named_tag(*item) for item in payload) + b"\x00"
    raise AssertionError(f"unsupported fixture tag type {tag_type}")


def nbt_named_tag(tag_type, name, payload):
    return struct.pack(">B", tag_type) + nbt_name(name) + nbt_payload(tag_type, payload)


def minimal_level_dat(level_name="New World", allow_commands=0, include_worldgen=True, include_player=True):
    data = [
        (TAG_STRING, "LevelName", level_name),
        (TAG_BYTE, "allowCommands", allow_commands),
    ]
    if include_worldgen:
        data.append((TAG_COMPOUND, "WorldGenSettings", [
            (TAG_COMPOUND, "dimensions", [
                (TAG_COMPOUND, "minecraft:overworld", [
                    (TAG_STRING, "type", "minecraft:overworld"),
                    (TAG_COMPOUND, "generator", [
                        (TAG_STRING, "type", "minecraft:noise"),
                        (TAG_STRING, "settings", "minecraft:overworld"),
                        (TAG_COMPOUND, "biome_source", [
                            (TAG_STRING, "type", "minecraft:multi_noise"),
                            (TAG_STRING, "preset", "minecraft:overworld"),
                        ]),
                    ]),
                ]),
            ]),
        ]))
    if include_player:
        data.append((TAG_COMPOUND, "Player", [
            (TAG_STRING, "Dimension", "minecraft:the_nether"),
            (TAG_INT, "SelectedItemSlot", 7),
        ]))
    return gzip.compress(nbt_named_tag(TAG_COMPOUND, "", [(TAG_COMPOUND, "Data", data)]))


class PreparePrismGuiWorldTests(unittest.TestCase):
    @staticmethod
    def display_mode(mod):
        return lambda: mod.DisplayMode(1470, 956, 2940, 1912, 60, 24)

    def load_script(self):
        self.assertTrue(SCRIPT_PATH.exists(), "missing scripts/prepare_prism_gui_world.py")
        spec = importlib.util.spec_from_file_location("prepare_prism_gui_world", SCRIPT_PATH)
        module = importlib.util.module_from_spec(spec)
        assert spec.loader is not None
        spec.loader.exec_module(module)
        return module

    def test_scenario_profiles_keep_only_current_checklist_items_and_start_at_the_target(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)

            boot = root / "boot"
            boot.mkdir()
            boot_manifest = mod.install_datapack(boot, "boot-smoke")
            self.assertEqual("boot-smoke", boot_manifest["scenario"])
            self.assertEqual({}, boot_manifest["player_kit"]["hotbar"])
            self.assertEqual([], boot_manifest["player_kit"]["inventory"])
            self.assertEqual({}, boot_manifest["hotbar_views"])
            self.assertEqual({}, boot_manifest["targets"])

            rails = root / "rails"
            rails.mkdir()
            rail_manifest = mod.install_datapack(rails, "terminal-left-rail")
            self.assertEqual("storage_terminal", rail_manifest["start_target"])
            self.assertEqual({"1", "2"}, set(rail_manifest["player_kit"]["hotbar"]))
            self.assertEqual([], rail_manifest["player_kit"]["inventory"])
            self.assertEqual(
                {"storage_core", "storage_terminal", "crafting_terminal"},
                set(rail_manifest["targets"]),
            )

            buses = root / "buses"
            buses.mkdir()
            bus_manifest = mod.install_datapack(buses, "bus-configuration")
            self.assertEqual("import_bus", bus_manifest["start_target"])
            self.assertEqual({"5", "6", "7", "9"}, set(bus_manifest["player_kit"]["hotbar"]))
            self.assertEqual(
                [{"slot": "inventory.0", "item": "minecraft:cobblestone", "count": 64}],
                bus_manifest["player_kit"]["inventory"],
            )
            self.assertEqual(
                {"storage_core", "import_bus", "export_bus"},
                set(bus_manifest["targets"]),
            )

            crafting = root / "crafting"
            crafting.mkdir()
            crafting_manifest = mod.install_datapack(crafting, "crafting-fuel-page")
            self.assertEqual("crafting_terminal", crafting_manifest["start_target"])
            hotbar = crafting_manifest["player_kit"]["hotbar"]
            inventory = crafting_manifest["player_kit"]["inventory"]
            self.assertEqual({"1", "2", "3"}, set(hotbar))
            self.assertEqual([], inventory)
            self.assertEqual(
                {"hotbar.0", "hotbar.1", "hotbar.2"},
                {entry["slot"] for entry in hotbar.values()},
            )
            self.assertEqual(
                {"slot": "hotbar.2", "item": "minecraft:coal", "count": 1},
                hotbar["3"],
            )
            self.assertEqual(121_000, crafting_manifest["baseline"]["stored_items"]["minecraft:oak_log"])
            self.assertEqual(
                "ironfurnaces:iron_furnace",
                crafting_manifest["baseline"]["installed_stations"]["magic_storage:furnace"]["item"],
            )
            self.assertEqual(
                32_000,
                crafting_manifest["baseline"]["energy"]["furnace_fuel"],
            )
            self.assertEqual(
                1_561,
                crafting_manifest["baseline"]["descriptor_consumables"]["magic_storage:axe"]["amount"],
            )
            self.assertIn(
                {
                    "kind": "mekanism:chemical",
                    "resource": "mekanism:oxygen",
                    "amount": 5_000_000,
                },
                crafting_manifest["baseline"]["typed_resources"],
            )

            functions = crafting / "datapacks/magic_storage_gui_test/data/magic_storage_gui_test/function"
            player_ready = (functions / "player_ready.mcfunction").read_text()
            reset = (functions / "reset_from_hotbar.mcfunction").read_text()
            start = (functions / "view_crafting_terminal.mcfunction").read_text()
            self.assertIn("function magic_storage_gui_test:reset_player", player_ready)
            self.assertIn("function magic_storage_gui_test:reset_player", reset)
            self.assertIn("tp @s 1.5 80.0 4.5 facing 1.5 80.5 0.5", start)
            self.assertNotIn("view_storage_terminal", player_ready)
            self.assertIn("function magic_storage_gui_test:view_crafting_terminal", player_ready)
            self.assertNotIn("function magic_storage_gui_test:reset_from_hotbar", reset)

    def test_internal_world_build_apis_require_an_explicit_scenario(self):
        mod = self.load_script()
        install_parameter = inspect.signature(mod.install_datapack).parameters["scenario_name"]
        prepare_parameter = inspect.signature(mod.prepare_world).parameters["scenario_name"]
        self.assertIs(inspect.Parameter.empty, install_parameter.default)
        self.assertIs(inspect.Parameter.empty, prepare_parameter.default)
        self.assertEqual(inspect.Parameter.KEYWORD_ONLY, prepare_parameter.kind)

    def test_player_kit_rejects_slots_outside_minecraft_item_replace_domains(self):
        mod = self.load_script()
        with self.assertRaisesRegex(ValueError, "invalid player inventory slot"):
            mod.build_reset_player_function({
                "hotbar": {},
                "inventory": [
                    {"slot": "inventory.27", "item": "minecraft:stone", "count": 1},
                ],
            })

    def test_install_datapack_writes_void_lab_preload_and_fixed_navigation(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            world_dir = Path(tmp) / "MagicStorageGuiTest"
            world_dir.mkdir()

            manifest = mod.install_datapack(world_dir, "crafting-fuel-page")

            pack_meta = json.loads((world_dir / "datapacks/magic_storage_gui_test/pack.mcmeta").read_text())
            self.assertEqual(48, pack_meta["pack"]["pack_format"])
            self.assertEqual(5, manifest["schema_version"])
            self.assertEqual("crafting-fuel-page", manifest["scenario"])
            self.assertEqual("crafting_terminal", manifest["start_target"])
            self.assertEqual([-18, 79, -12, 18, 90, 12], manifest["lab"]["reset_bounds"])
            self.assertEqual([0, 80, 0], manifest["targets"]["storage_core"]["block"])
            self.assertEqual([-1, 80, 0], manifest["targets"]["storage_terminal"]["block"])
            self.assertEqual([1, 80, 0], manifest["targets"]["crafting_terminal"]["block"])
            self.assertEqual(
                "/function magic_storage_gui_test:view_storage_terminal",
                manifest["commands"]["view_storage_terminal"],
            )
            self.assertEqual("view_storage_terminal", manifest["hotbar_views"]["1"]["function"])
            self.assertEqual("view_crafting_terminal", manifest["hotbar_views"]["2"]["function"])
            self.assertEqual({"1", "2"}, set(manifest["hotbar_views"]))
            self.assertEqual(121_000, manifest["baseline"]["stored_items"]["minecraft:oak_log"])
            self.assertEqual(999_999, manifest["baseline"]["stored_items"]["minecraft:cobblestone"])
            self.assertEqual(
                {
                    "item": "ironfurnaces:iron_furnace",
                    "count": 3,
                },
                manifest["baseline"]["installed_stations"]["magic_storage:furnace"],
            )
            self.assertEqual(
                {
                    "item": "mekanism:ultimate_crushing_factory",
                    "count": 130,
                },
                manifest["baseline"]["installed_stations"][
                    "magic_storage:mekanism_crusher"
                ],
            )
            self.assertTrue({
                "magic_storage:mekanism_osmium_compressor",
                "magic_storage:mekanism_purification_chamber",
                "magic_storage:mekanism_chemical_injection_chamber",
                "magic_storage:mekanism_metallurgic_infuser",
                "magic_storage:mekanism_precision_sawmill",
                "magic_storage:mekanism_rotary_condensentrator",
                "magic_storage:mekanism_chemical_oxidizer",
                "magic_storage:mekanism_chemical_infuser",
                "magic_storage:mekanism_electrolytic_separator",
                "magic_storage:mekanism_chemical_dissolution_chamber",
                "magic_storage:mekanism_chemical_washer",
                "magic_storage:mekanism_chemical_crystallizer",
                "magic_storage:mekanism_isotopic_centrifuge",
                "magic_storage:mekanism_antiprotonic_nucleosynthesizer",
                "magic_storage:mekanism_pigment_extractor",
                "magic_storage:mekanism_pigment_mixer",
                "magic_storage:mekanism_painting_machine",
            }.issubset(manifest["baseline"]["installed_stations"]))
            self.assertTrue({
                "magic_storage:botania_mana_pool",
                "magic_storage:botania_runic_altar",
                "magic_storage:botania_terrestrial_agglomeration_plate",
                "magic_storage:botania_petal_apothecary",
                "magic_storage:botania_elven_gateway",
            }.issubset(manifest["baseline"]["installed_stations"]))
            self.assertEqual(
                {
                    "item": "botania:mana_pool",
                    "count": 1,
                },
                manifest["baseline"]["installed_stations"][
                    "magic_storage:botania_mana_pool"
                ],
            )
            self.assertEqual(
                {
                    "item": "farmersdelight:cooking_pot",
                    "count": 1,
                },
                manifest["baseline"]["installed_stations"][
                    "magic_storage:farmers_delight_cooking_pot"
                ],
            )
            self.assertEqual(
                {"finite_type_slots": 0, "unlimited": True},
                manifest["baseline"]["type_capacity"],
            )
            self.assertEqual(32_000, manifest["baseline"]["energy"]["furnace_fuel"])
            self.assertEqual(9_600, manifest["baseline"]["energy"]["blaze_fuel"])
            self.assertEqual(
                {"amount": 1_561, "infinite": False},
                manifest["baseline"]["descriptor_consumables"]["magic_storage:axe"],
            )
            self.assertEqual(
                10_000,
                manifest["baseline"]["station_work"]["magic_storage:mekanism_crusher"],
            )
            self.assertEqual("magic_storage:storage_terminal", manifest["player_kit"]["hotbar"]["1"]["item"])
            self.assertEqual([], manifest["player_kit"]["inventory"])
            self.assertTrue(manifest["fullscreen_gate"]["required"])
            self.assertEqual("after_world_ready_before_first_gui_action", manifest["fullscreen_gate"]["when"])
            self.assertEqual("minecraft_macos_borderless_fullscreen", manifest["fullscreen_gate"]["launch_mode"])
            self.assertTrue(manifest["fullscreen_gate"]["automatic"])
            self.assertEqual(["minecraft_f11_borderless"], manifest["fullscreen_gate"]["accepted_methods"])
            self.assertEqual(
                ["macos_native_fullscreen", "combined_native_and_minecraft_fullscreen"],
                manifest["fullscreen_gate"]["forbidden_methods"],
            )
            self.assertIn("User confirms the entire Minecraft frame is visible", manifest["fullscreen_gate"]["verify"])
            self.assertIn("macOS desktop display mode remains unchanged", manifest["fullscreen_gate"]["verify"])
            self.assertFalse(any("Computer Use" in check for check in manifest["fullscreen_gate"]["verify"]))
            self.assertEqual(
                '"/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher" -l dev -w "MagicStorageGuiTest" -o MagicStorageBot',
                manifest["launch_command"],
            )

            datapack = world_dir / "datapacks/magic_storage_gui_test"
            self.assertTrue((datapack / "data/minecraft/tags/function/load.json").exists())
            self.assertTrue((datapack / "data/minecraft/tags/function/tick.json").exists())
            setup = (datapack / "data/magic_storage_gui_test/function/setup.mcfunction").read_text()
            self.assertIn("fill -18 79 -12 18 79 12 minecraft:polished_blackstone_bricks outline", setup)
            self.assertIn("fill -17 79 -11 17 79 11 minecraft:smooth_stone", setup)
            self.assertIn("setblock -1 80 0 magic_storage:storage_terminal", setup)
            self.assertIn("setblock 1 80 0 magic_storage:crafting_terminal", setup)
            self.assertIn("setblock 0 80 -1 magic_storage:creative_storage_unit", setup)
            self.assertNotIn("magic_storage:import_bus", setup)
            self.assertNotIn("magic_storage:export_bus", setup)
            self.assertNotIn("magic_storage:storage_unit_t1", setup)
            self.assertNotIn("magic_storage:storage_unit_t6", setup)
            self.assertIn(
                "setblock 0 80 0 magic_storage:storage_core{storageSchema:1,storageId:[I;",
                setup,
            )
            self.assertNotIn("machines:{Items:", setup)
            self.assertNotIn("inventory:[", setup)
            self.assertNotIn("bottle_fuel", setup)
            player_ready = (datapack / "data/magic_storage_gui_test/function/player_ready.mcfunction").read_text()
            self.assertIn("function magic_storage_gui_test:reset_player", player_ready)
            self.assertIn("function magic_storage_gui_test:view_crafting_terminal", player_ready)
            reset_player = (datapack / "data/magic_storage_gui_test/function/reset_player.mcfunction").read_text()
            self.assertIn("item replace entity @s hotbar.0 with magic_storage:storage_terminal 1", reset_player)
            self.assertIn("item replace entity @s hotbar.1 with magic_storage:crafting_terminal 1", reset_player)
            self.assertNotIn("inventory.", reset_player)
            self.assertFalse(any(line.startswith("give @s") for line in reset_player.splitlines()))

            repository = mod._read_gzip_nbt(
                world_dir / "data/magic_storage_core_storages.dat"
            )
            repository_root = mod._require_compound(repository[2], "data")
            storage_type, storages = mod._require_item(
                repository_root, "storages", mod.TAG_LIST
            )
            self.assertEqual(mod.TAG_COMPOUND, storage_type)
            self.assertEqual(1, len(storages))
            storage = storages[0]
            segment_type, segments = mod._require_item(
                storage, "inventorySegments", mod.TAG_LIST
            )
            self.assertEqual(mod.TAG_COMPOUND, segment_type)
            entries_type, entries = mod._require_item(
                segments[0], "entries", mod.TAG_LIST
            )
            self.assertEqual(mod.TAG_COMPOUND, entries_type)
            stored_counts = {
                mod._require_item(
                    mod._require_compound(entry, "item"), "id", mod.TAG_STRING
                ): mod._require_item(entry, "count", mod.TAG_LONG)
                for entry in entries
            }
            self.assertEqual(121_000, stored_counts["minecraft:oak_log"])
            machine_type, machines = mod._require_item(
                storage, "machineDescriptors", mod.TAG_LIST
            )
            self.assertEqual(mod.TAG_COMPOUND, machine_type)
            machine_items = {
                mod._require_item(machine, "descriptorId", mod.TAG_STRING):
                mod._require_item(
                    mod._require_compound(machine, "item"), "id", mod.TAG_STRING
                )
                for machine in machines
            }
            machine_counts = {
                mod._require_item(machine, "descriptorId", mod.TAG_STRING):
                mod._require_item(machine, "count", mod.TAG_LONG)
                for machine in machines
            }
            encoded_item_counts = {
                mod._require_item(machine, "descriptorId", mod.TAG_STRING):
                mod._require_item(
                    mod._require_compound(machine, "item"), "count", mod.TAG_INT
                )
                for machine in machines
            }
            self.assertEqual(
                "ironfurnaces:iron_furnace",
                machine_items["magic_storage:furnace"],
            )
            self.assertEqual(
                "mekanism:ultimate_crushing_factory",
                machine_items["magic_storage:mekanism_crusher"],
            )
            self.assertEqual(130, machine_counts["magic_storage:mekanism_crusher"])
            self.assertTrue(all(count == 1 for count in encoded_item_counts.values()))
            ledger = mod._require_compound(storage, "resourceLedger")
            resource_type, resources = mod._require_item(
                ledger, "entries", mod.TAG_LIST
            )
            self.assertEqual(mod.TAG_COMPOUND, resource_type)
            resource_amounts = {
                (
                    mod._require_item(entry, "kind", mod.TAG_STRING),
                    mod._require_item(entry, "resource", mod.TAG_STRING),
                ): mod._require_item(entry, "amount", mod.TAG_LONG)
                for entry in resources
            }
            self.assertEqual(
                5_000_000,
                resource_amounts[("mekanism:chemical", "mekanism:oxygen")],
            )
            self.assertEqual(
                2_000_000,
                resource_amounts[("botania:mana", "botania:mana")],
            )
            for item_id in {
                "minecraft:iron_ingot",
                "minecraft:bone_meal",
                "minecraft:sugar_cane",
                "minecraft:fishing_rod",
                "minecraft:wheat_seeds",
                "botania:mana_powder",
                "botania:manasteel_ingot",
                "botania:livingrock",
                "botania:mana_pearl",
                "botania:mana_diamond",
                "botania:white_mystical_petal",
            }:
                self.assertIn(item_id, stored_counts)

            view = (datapack / "data/magic_storage_gui_test/function/view_storage_terminal.mcfunction").read_text()
            self.assertIn("tp @s -0.5 80.0 4.5 facing -0.5 80.5 0.5", view)
            self.assertNotIn("sleep", view.lower())
            hotbar = (datapack / "data/magic_storage_gui_test/function/hotbar_views.mcfunction").read_text()
            self.assertIn("SelectedItemSlot:0", hotbar)
            self.assertIn("function magic_storage_gui_test:view_storage_terminal", hotbar)
            self.assertNotIn("function magic_storage_gui_test:view_texture_gallery", hotbar)
            self.assertNotIn("function magic_storage_gui_test:home", hotbar)
            self.assertNotIn("function magic_storage_gui_test:reset_from_hotbar", hotbar)

            all_function_text = "\n".join(path.read_text() for path in datapack.rglob("*.mcfunction"))
            self.assertNotIn("command_block", all_function_text)
            self.assertNotIn("sleep", all_function_text.lower())

    def test_crafting_fuel_page_preloads_audited_optional_mod_compatibility(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            world_dir = Path(tmp) / "MagicStorageGuiTest"
            world_dir.mkdir()

            manifest = mod.install_datapack(world_dir, "crafting-fuel-page")
            expected_stations = {
                "magic_storage:modern_industrialization_assembler":
                    "modern_industrialization:assembler",
                "magic_storage:modern_industrialization_centrifuge":
                    "modern_industrialization:centrifuge",
                "magic_storage:modern_industrialization_chemical_reactor":
                    "modern_industrialization:chemical_reactor",
                "magic_storage:modern_industrialization_compressor":
                    "modern_industrialization:bronze_compressor",
                "magic_storage:modern_industrialization_cutting_machine":
                    "modern_industrialization:bronze_cutting_machine",
                "magic_storage:modern_industrialization_distillery":
                    "modern_industrialization:distillery",
                "magic_storage:modern_industrialization_electrolyzer":
                    "modern_industrialization:electrolyzer",
                "magic_storage:modern_industrialization_furnace":
                    "modern_industrialization:bronze_furnace",
                "magic_storage:modern_industrialization_macerator":
                    "modern_industrialization:bronze_macerator",
                "magic_storage:modern_industrialization_mixer":
                    "modern_industrialization:bronze_mixer",
                "magic_storage:modern_industrialization_packer":
                    "modern_industrialization:steel_packer",
                "magic_storage:modern_industrialization_polarizer":
                    "modern_industrialization:polarizer",
                "magic_storage:modern_industrialization_unpacker":
                    "modern_industrialization:steel_unpacker",
                "magic_storage:modern_industrialization_wiremill":
                    "modern_industrialization:steel_wiremill",
                "magic_storage:ars_nouveau_imbuement_chamber":
                    "ars_nouveau:imbuement_chamber",
                "magic_storage:ars_nouveau_enchanting_apparatus":
                    "ars_nouveau:enchanting_apparatus",
                "magic_storage:powah_energizing": "powah:energizing_rod_starter",
                "magic_storage:industrial_foregoing_dissolution_chamber":
                    "industrialforegoing:dissolution_chamber",
                "magic_storage:industrial_foregoing_material_stonework_factory":
                    "industrialforegoing:material_stonework_factory",
                "magic_storage:create_milling": "create:millstone",
                "magic_storage:create_crushing": "create:crushing_wheel",
                "magic_storage:create_cutting": "create:mechanical_saw",
                "magic_storage:create_filling": "create:spout",
                "magic_storage:create_emptying": "create:item_drain",
            }
            stations = manifest["baseline"]["installed_stations"]
            self.assertTrue(expected_stations.keys() <= stations.keys())
            self.assertEqual(
                expected_stations,
                {descriptor: stations[descriptor]["item"] for descriptor in expected_stations},
            )
            self.assertTrue(all(stations[descriptor]["count"] == 1 for descriptor in expected_stations))
            self.assertTrue(all(
                manifest["baseline"]["station_work"][descriptor] > 0
                for descriptor in expected_stations
            ))
            self.assertFalse(any("pneumatic" in descriptor for descriptor in stations))
            self.assertFalse(any("evilcraft" in descriptor for descriptor in stations))

            required_items = {
                "modern_industrialization:aluminum_blade",
                "ars_nouveau:source_gem",
                "minecraft:fermented_spider_eye",
                "minecraft:sugar",
                "minecraft:milk_bucket",
                "minecraft:amethyst_shard",
                "minecraft:mossy_cobblestone",
                "minecraft:glass_pane",
                "create:andesite_alloy",
                "minecraft:glass_bottle",
                "minecraft:honey_bottle",
            }
            self.assertTrue(required_items.issubset(manifest["baseline"]["stored_items"]))
            self.assertFalse(any(
                item.startswith("evilcraft:")
                for item in manifest["baseline"]["stored_items"]
            ))
            resources = {
                (entry["kind"], entry["resource"]): entry["amount"]
                for entry in manifest["baseline"]["typed_resources"]
            }
            for key in {
                ("magic_storage:fluid", "minecraft:water"),
                ("magic_storage:fluid", "minecraft:lava"),
                ("magic_storage:fluid", "modern_industrialization:sugar_solution"),
                ("magic_storage:fluid", "industrialforegoing:pink_slime"),
                ("magic_storage:fluid", "create:honey"),
                ("magic_storage:neoforge_energy", "neoforge:energy"),
                ("ars_nouveau:source", "ars_nouveau:source"),
            }:
                self.assertIn(key, resources)
                self.assertGreater(resources[key], 0)
            self.assertNotIn(("magic_storage:fluid", "evilcraft:blood"), resources)

            repository = mod._read_gzip_nbt(
                world_dir / "data/magic_storage_core_storages.dat"
            )
            repository_root = mod._require_compound(repository[2], "data")
            storage_type, storages = mod._require_item(
                repository_root, "storages", mod.TAG_LIST
            )
            self.assertEqual(mod.TAG_COMPOUND, storage_type)
            self.assertEqual(1, len(storages))
            machine_type, machines = mod._require_item(
                storages[0], "machineDescriptors", mod.TAG_LIST
            )
            self.assertEqual(mod.TAG_COMPOUND, machine_type)
            encoded_stations = {
                mod._require_item(machine, "descriptorId", mod.TAG_STRING):
                mod._require_item(
                    mod._require_compound(machine, "item"), "id", mod.TAG_STRING
                )
                for machine in machines
            }
            self.assertTrue(expected_stations.keys() <= encoded_stations.keys())
            self.assertEqual(
                expected_stations,
                {descriptor: encoded_stations[descriptor] for descriptor in expected_stations},
            )
            self.assertEqual([], manifest["player_kit"]["inventory"])
            self.assertEqual({"1", "2", "3"}, set(manifest["player_kit"]["hotbar"]))
            self.assertEqual(
                {"slot": "hotbar.2", "item": "minecraft:coal", "count": 1},
                manifest["player_kit"]["hotbar"]["3"],
            )

    def test_each_scenario_places_only_the_blocks_needed_by_its_checklist(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for scenario, required, forbidden in [
                (
                    "terminal-left-rail",
                    {"magic_storage:storage_core", "magic_storage:storage_terminal",
                     "magic_storage:crafting_terminal", "magic_storage:creative_storage_unit"},
                    {"magic_storage:import_bus", "magic_storage:export_bus", "magic_storage:storage_unit_t1"},
                ),
                (
                    "bus-configuration",
                    {"magic_storage:storage_core", "magic_storage:storage_unit_t1",
                     "magic_storage:import_bus", "magic_storage:export_bus",
                     "minecraft:barrel"},
                    {"magic_storage:storage_terminal", "magic_storage:crafting_terminal",
                     "magic_storage:creative_storage_unit"},
                ),
                (
                    "patchouli-guide",
                    set(),
                    {"magic_storage:storage_core", "magic_storage:storage_terminal",
                     "magic_storage:crafting_terminal", "magic_storage:import_bus"},
                ),
            ]:
                world_dir = root / scenario
                world_dir.mkdir()
                manifest = mod.install_datapack(world_dir, scenario)
                setup = (
                    world_dir
                    / "datapacks/magic_storage_gui_test/data/magic_storage_gui_test/function/setup.mcfunction"
                ).read_text()
                for block in required:
                    self.assertIn(block, setup)
                for block in forbidden:
                    self.assertNotIn(block, setup)
                self.assertNotIn("gallery", manifest)
                self.assertNotIn("connected_gallery", manifest)
                self.assertNotIn("view_texture_gallery", manifest["commands"])

    def test_datapack_waits_three_ticks_and_reset_reuses_setup_without_looping(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            world_dir = Path(tmp) / "MagicStorageGuiTest"
            world_dir.mkdir()
            manifest = mod.install_datapack(world_dir, "bus-configuration")
            datapack = world_dir / "datapacks/magic_storage_gui_test"
            functions = datapack / "data/magic_storage_gui_test/function"

            load_tag = json.loads((datapack / "data/minecraft/tags/function/load.json").read_text())
            self.assertEqual(["magic_storage_gui_test:load"], load_tag["values"])
            load = (functions / "load.mcfunction").read_text()
            self.assertEqual(1, load.count("scoreboard objectives add ms_gui_timer dummy"))
            self.assertIn("function magic_storage_gui_test:setup", load)
            tick = (functions / "tick.mcfunction").read_text()
            self.assertIn("scoreboard players add @a[tag=!ms_gui_ready] ms_gui_timer 1", tick)
            self.assertIn("scores={ms_gui_timer=3..}", tick)
            self.assertIn("function magic_storage_gui_test:player_ready", tick)
            self.assertIn("function magic_storage_gui_test:hotbar_views", tick)
            setup = (functions / "setup.mcfunction").read_text()
            self.assertNotIn("player_ready", setup)
            reset = (functions / "reset_from_hotbar.mcfunction").read_text()
            self.assertIn("function magic_storage_gui_test:setup", reset)
            self.assertIn("function magic_storage_gui_test:reset_player", reset)
            self.assertNotIn("function magic_storage_gui_test:player_ready", reset)
            player_ready = (functions / "player_ready.mcfunction").read_text()
            self.assertLess(
                player_ready.index("function magic_storage_gui_test:prime_hotbar_latch"),
                player_ready.index("tag @s add ms_gui_ready"),
            )
            prime = (functions / "prime_hotbar_latch.mcfunction").read_text()
            self.assertIn("SelectedItemSlot:8", prime)
            self.assertIn("tag @s add ms_hotbar_8", prime)
            self.assertEqual(3, manifest["bootstrap"]["ready_delay_ticks"])
            self.assertEqual("reset_from_hotbar", manifest["bootstrap"]["reset_function"])

    def test_update_level_dat_rewrites_overworld_to_true_void_flat_generator(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            level_dat = Path(tmp) / "level.dat"
            level_dat.write_bytes(minimal_level_dat())

            mod.update_level_dat(level_dat, "MagicStorageGuiTest", allow_commands=True)

            self.assertEqual(
                {
                    "type": "minecraft:flat",
                    "biome": "minecraft:the_void",
                    "layers": [{"height": 1, "block": "minecraft:air"}],
                    "features": False,
                    "lakes": False,
                    "structure_overrides": [],
                },
                mod.read_void_generator_summary(level_dat),
            )
            self.assertEqual({"x": 0, "y": 80, "z": 7}, mod.read_spawn_summary(level_dat))
            data = mod._data_compound(mod._read_gzip_nbt(level_dat))
            _, embedded_player = mod._find_compound_item(data, "Player")
            self.assertIsNone(embedded_player)

    def test_update_level_dat_rejects_missing_worldgen_without_mutating_file(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            level_dat = Path(tmp) / "level.dat"
            original = minimal_level_dat(include_worldgen=False)
            level_dat.write_bytes(original)

            with self.assertRaisesRegex(ValueError, "WorldGenSettings"):
                mod.update_level_dat(level_dat, "MagicStorageGuiTest", allow_commands=True)

            self.assertEqual(original, level_dat.read_bytes())

    def test_patch_options_sets_fast_reproducible_gui_values(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            options = Path(tmp) / "options.txt"
            options.write_text(
                "fullscreen:true\n"
                "fullscreenResolution:1280x720@60:24\n"
                "pauseOnLostFocus:true\n"
                "guiScale:2\n"
                "key_key.use:key.mouse.right\n"
                "unrelated:kept\n"
            )

            changed = mod.patch_options(options)

            lines = dict(line.split(":", 1) for line in options.read_text().splitlines() if ":" in line)
            self.assertTrue(changed)
            self.assertEqual("true", lines["fullscreen"])
            self.assertNotIn("fullscreenResolution", lines)
            self.assertEqual("false", lines["pauseOnLostFocus"])
            self.assertEqual("4", lines["guiScale"])
            self.assertEqual("key.keyboard.u", lines["key_key.use"])
            self.assertEqual("1280", lines["overrideWidth"])
            self.assertEqual("720", lines["overrideHeight"])
            self.assertEqual("none", lines["tutorialStep"])
            self.assertEqual("kept", lines["unrelated"])

    def test_current_macos_main_display_mode_reads_scaled_desktop_mode(self):
        mod = self.load_script()
        payload = {
            "SPDisplaysDataType": [
                {
                    "spdisplays_ndrvs": [
                        {
                            "_name": "External",
                            "_spdisplays_resolution": "1920 x 1080 @ 60.00Hz",
                            "_spdisplays_pixels": "1920 x 1080",
                            "spdisplays_online": "spdisplays_yes",
                        },
                        {
                            "_name": "Color LCD",
                            "_spdisplays_resolution": "1470 x 956 @ 60.00Hz",
                            "_spdisplays_pixels": "2940 x 1912",
                            "spdisplays_main": "spdisplays_yes",
                            "spdisplays_online": "spdisplays_yes",
                        },
                    ]
                }
            ]
        }
        calls = []

        mode = mod.current_macos_main_display_mode(
            run_func=lambda command, **kwargs: calls.append((command, kwargs))
            or subprocess.CompletedProcess(command, 0, json.dumps(payload), "")
        )

        self.assertEqual(mod.DisplayMode(1470, 956, 2940, 1912, 60, 24), mode)
        self.assertEqual(
            ["/usr/sbin/system_profiler", "-json", "SPDisplaysDataType"],
            calls[0][0],
        )

    def test_current_macos_main_display_mode_fails_closed_without_exact_main_mode(self):
        mod = self.load_script()
        payload = {"SPDisplaysDataType": [{"spdisplays_ndrvs": []}]}

        with self.assertRaisesRegex(RuntimeError, "exactly one online main display"):
            mod.current_macos_main_display_mode(
                run_func=lambda command, **kwargs: subprocess.CompletedProcess(
                    command, 0, json.dumps(payload), ""
                )
            )

    def test_patch_options_preserves_original_when_atomic_replace_fails(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            options = Path(tmp) / "options.txt"
            original = "fullscreen:true\nunrelated:kept\n"
            options.write_text(original)
            original_replace = Path.replace

            def failing_replace(path, target):
                if Path(target) == options:
                    raise OSError("replace failed")
                return original_replace(path, target)

            Path.replace = failing_replace
            try:
                with self.assertRaisesRegex(OSError, "replace failed"):
                    mod.patch_options(options)
            finally:
                Path.replace = original_replace

            self.assertEqual(original, options.read_text())
            self.assertEqual([options.name], sorted(path.name for path in options.parent.iterdir()))

    def test_prepare_world_recreates_only_marked_target_from_template(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            minecraft_dir = Path(tmp) / "minecraft"
            source = minecraft_dir / "saves" / "New World"
            source.mkdir(parents=True)
            (source / "level.dat").write_bytes(minimal_level_dat())
            (source / "region").mkdir()
            (minecraft_dir / "options.txt").write_text("fullscreen:true\n")

            first = mod.prepare_world(
                minecraft_dir,
                scenario_name="crafting-fuel-page",
                display_mode_func=self.display_mode(mod),
            )
            target = minecraft_dir / "saves" / "MagicStorageGuiTest"
            self.assertEqual(str(target.resolve()), first["world_dir"])
            self.assertTrue((target / ".magic_storage_gui_test_world").exists())
            self.assertTrue((target / "datapacks/magic_storage_gui_test/pack.mcmeta").exists())
            self.assertTrue((source / "level.dat").exists())
            self.assertEqual(
                {"LevelName": "MagicStorageGuiTest", "allowCommands": 1},
                mod.read_level_dat_summary(target / "level.dat"),
            )
            self.assertEqual("minecraft:flat", first["world_generator"]["type"])
            self.assertEqual("minecraft:the_void", first["world_generator"]["biome"])

            stale = target / "stale.txt"
            stale.write_text("old generated state")
            second = mod.prepare_world(
                minecraft_dir,
                scenario_name="crafting-fuel-page",
                display_mode_func=self.display_mode(mod),
            )
            self.assertEqual(str(target.resolve()), second["world_dir"])
            self.assertFalse(stale.exists())

            shutil.rmtree(target)
            target.mkdir()
            (target / "level.dat").write_bytes(minimal_level_dat("Personal World"))
            with self.assertRaisesRegex(RuntimeError, "not marked"):
                mod.prepare_world(
                    minecraft_dir,
                    scenario_name="crafting-fuel-page",
                    display_mode_func=self.display_mode(mod),
                )

    def test_prepare_world_strips_all_copied_runtime_state_without_mutating_source(self):
        mod = self.load_script()
        expected_paths = (
            "region", "entities", "poi", "data", "datapacks",
            "DIM-1", "DIM1", "dimensions", "serverconfig",
            "playerdata", "advancements", "stats",
            "session.lock", "level.dat_old", "icon.png",
        )
        self.assertEqual(expected_paths, mod.COPIED_RUNTIME_PATHS)
        with tempfile.TemporaryDirectory() as tmp:
            minecraft_dir = Path(tmp) / "minecraft"
            source = minecraft_dir / "saves" / "New World"
            source.mkdir(parents=True)
            source_level = minimal_level_dat()
            (source / "level.dat").write_bytes(source_level)
            directory_paths = expected_paths[:12]
            file_paths = expected_paths[12:]
            for relative in directory_paths:
                path = source / relative
                path.mkdir(parents=True)
                (path / "source-sentinel.txt").write_text(relative)
            for relative in file_paths:
                (source / relative).write_text(relative)
            (minecraft_dir / "options.txt").write_text("fullscreen:true\n")

            manifest = mod.prepare_world(
                minecraft_dir,
                scenario_name="crafting-fuel-page",
                display_mode_func=self.display_mode(mod),
            )

            target = minecraft_dir / "saves" / "MagicStorageGuiTest"
            for relative in directory_paths:
                self.assertFalse((target / relative / "source-sentinel.txt").exists(), relative)
                self.assertEqual(relative, (source / relative / "source-sentinel.txt").read_text())
            for relative in file_paths:
                self.assertFalse((target / relative).exists(), relative)
                self.assertEqual(relative, (source / relative).read_text())
            self.assertEqual(source_level, (source / "level.dat").read_bytes())
            target_data = mod._data_compound(mod._read_gzip_nbt(target / "level.dat"))
            _, embedded_player = mod._find_compound_item(target_data, "Player")
            self.assertIsNone(embedded_player)
            self.assertTrue((target / "datapacks/magic_storage_gui_test/pack.mcmeta").exists())
            self.assertEqual(list(expected_paths), manifest["stripped_template_paths"])

    def test_prepare_world_refuses_to_recreate_marked_target_when_open(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            minecraft_dir = Path(tmp) / "minecraft"
            source = minecraft_dir / "saves" / "New World"
            target = minecraft_dir / "saves" / "MagicStorageGuiTest"
            source.mkdir(parents=True)
            target.mkdir(parents=True)
            (source / "level.dat").write_bytes(minimal_level_dat())
            (target / "level.dat").write_bytes(minimal_level_dat("MagicStorageGuiTest", 1))
            (target / ".magic_storage_gui_test_world").write_text("generated")
            stale = target / "stale.txt"
            stale.write_text("keep")
            (minecraft_dir / "options.txt").write_text("fullscreen:true\n")

            original_checker = mod.world_has_open_files
            mod.world_has_open_files = lambda path: path == target.resolve()
            try:
                with self.assertRaisesRegex(RuntimeError, "appears to be open"):
                    mod.prepare_world(
                        minecraft_dir,
                        scenario_name="crafting-fuel-page",
                        display_mode_func=self.display_mode(mod),
                    )
            finally:
                mod.world_has_open_files = original_checker
            self.assertEqual("keep", stale.read_text())

    def test_copy_template_rejects_same_source_and_target_before_mutation(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            world = Path(tmp) / "World"
            world.mkdir()
            (world / mod.MARKER_FILE).write_text("generated")
            sentinel = world / "sentinel.txt"
            sentinel.write_text("keep")

            with self.assertRaisesRegex(RuntimeError, "different directories"):
                mod._copy_template_world(world, world)

            self.assertEqual("keep", sentinel.read_text())

    def test_copy_failure_preserves_previous_generated_target(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "Source"
            target = root / "Target"
            source.mkdir()
            target.mkdir()
            (source / "level.dat").write_bytes(minimal_level_dat())
            (target / mod.MARKER_FILE).write_text("generated")
            sentinel = target / "sentinel.txt"
            sentinel.write_text("keep")
            original_copytree = mod.shutil.copytree
            original_checker = mod.world_has_open_files
            mod.shutil.copytree = lambda source_path, target_path: (_ for _ in ()).throw(OSError("copy failed"))
            mod.world_has_open_files = lambda path: False
            try:
                with self.assertRaisesRegex(OSError, "copy failed"):
                    mod._copy_template_world(source, target)
            finally:
                mod.shutil.copytree = original_copytree
                mod.world_has_open_files = original_checker

            self.assertEqual("keep", sentinel.read_text())
            self.assertTrue((target / mod.MARKER_FILE).exists())

    def test_prepare_world_preserves_existing_target_when_source_level_dat_is_invalid(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            minecraft_dir = Path(tmp) / "minecraft"
            source = minecraft_dir / "saves" / "New World"
            target = minecraft_dir / "saves" / "MagicStorageGuiTest"
            source.mkdir(parents=True)
            target.mkdir()
            (source / "level.dat").write_bytes(b"not gzip nbt")
            (target / mod.MARKER_FILE).write_text("generated")
            (target / "level.dat").write_bytes(minimal_level_dat("MagicStorageGuiTest", 1))
            sentinel = target / "sentinel.txt"
            sentinel.write_text("keep")
            options = minecraft_dir / "options.txt"
            options.write_text("fullscreen:true\n")
            original_checker = mod.world_has_open_files
            mod.world_has_open_files = lambda path: False
            try:
                with self.assertRaises(gzip.BadGzipFile):
                    mod.prepare_world(
                        minecraft_dir,
                        scenario_name="crafting-fuel-page",
                        display_mode_func=self.display_mode(mod),
                    )
            finally:
                mod.world_has_open_files = original_checker

            self.assertTrue(sentinel.exists())
            self.assertEqual("keep", sentinel.read_text())
            self.assertEqual(
                {"LevelName": "MagicStorageGuiTest", "allowCommands": 1},
                mod.read_level_dat_summary(target / "level.dat"),
            )
            self.assertEqual("fullscreen:true\n", options.read_text())

    def test_prepare_world_preserves_existing_target_when_manifest_install_fails(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            minecraft_dir = Path(tmp) / "minecraft"
            source = minecraft_dir / "saves" / "New World"
            target = minecraft_dir / "saves" / "MagicStorageGuiTest"
            source.mkdir(parents=True)
            target.mkdir()
            (source / "level.dat").write_bytes(minimal_level_dat())
            (target / mod.MARKER_FILE).write_text("generated")
            sentinel = target / "sentinel.txt"
            sentinel.write_text("keep")
            options = minecraft_dir / "options.txt"
            options.write_text("fullscreen:true\n")
            original_checker = mod.world_has_open_files
            original_installer = mod.install_datapack
            mod.world_has_open_files = lambda path: False
            mod.install_datapack = lambda world_dir, scenario_name: (
                _ for _ in ()
            ).throw(OSError("manifest failed"))
            try:
                with self.assertRaisesRegex(OSError, "manifest failed"):
                    mod.prepare_world(
                        minecraft_dir,
                        scenario_name="crafting-fuel-page",
                        display_mode_func=self.display_mode(mod),
                    )
            finally:
                mod.install_datapack = original_installer
                mod.world_has_open_files = original_checker

            self.assertTrue(sentinel.exists())
            self.assertEqual("keep", sentinel.read_text())
            self.assertEqual("fullscreen:true\n", options.read_text())

    def test_gui_docs_require_fullscreen_before_gui_actions(self):
        notes = (ROOT / "docs" / "notes.md").read_text()
        self.assertIn("全螢幕 gate", notes)
        self.assertIn("所有 GUI 測試都必須先通過全螢幕 gate", notes)
        self.assertIn("任何 `u`、hotbar、點擊、滾輪、截圖前", notes)
        self.assertIn("自動進入 Minecraft F11 fullscreen", notes)
        self.assertIn("禁止 macOS 原生 fullscreen", notes)


if __name__ == "__main__":
    unittest.main()
