import json
from pathlib import Path
import re
import struct
import unittest


ROOT = Path(__file__).resolve().parents[1]


class CreativeStorageUnitTests(unittest.TestCase):
    def test_terminal_capacity_display_uses_explicit_unlimited_state(self):
        storage_screen = (ROOT / "src/main/java/com/swearprom/magicstorage/magic_storage/StorageTerminalScreen.java").read_text()
        crafting_screen = (ROOT / "src/main/java/com/swearprom/magicstorage/magic_storage/CraftingTerminalScreen.java").read_text()
        en_us = json.loads((ROOT / "src/main/resources/assets/magic_storage/lang/en_us.json").read_text())
        zh_tw = json.loads((ROOT / "src/main/resources/assets/magic_storage/lang/zh_tw.json").read_text())

        self.assertIn("menu.hasUnlimitedTypeCapacity()", storage_screen)
        self.assertIn('"gui.magic_storage.type_capacity_unlimited"', storage_screen)
        self.assertIn("menu.hasUnlimitedTypeCapacity()", crafting_screen)
        self.assertIn('"gui.magic_storage.type_capacity_unlimited"', crafting_screen)
        self.assertEqual("%s / Unlimited types", en_us["gui.magic_storage.type_capacity_unlimited"])
        self.assertEqual("%s / 無限種類", zh_tw["gui.magic_storage.type_capacity_unlimited"])

    def test_registration_loot_tag_localization_and_no_recipe(self):
        magic_storage = (ROOT / "src/main/java/com/swearprom/magicstorage/magic_storage/MagicStorage.java").read_text()
        self.assertIn('ITEMS.register("creative_storage_unit", () -> new CreativeStorageUnitBlockItem(', magic_storage)
        self.assertIn("output.accept(CREATIVE_STORAGE_UNIT_ITEM.get())", magic_storage)

        en_us = json.loads((ROOT / "src/main/resources/assets/magic_storage/lang/en_us.json").read_text())
        zh_tw = json.loads((ROOT / "src/main/resources/assets/magic_storage/lang/zh_tw.json").read_text())
        self.assertEqual("Creative Storage Unit", en_us["block.magic_storage.creative_storage_unit"])
        self.assertEqual("創造儲存單元", zh_tw["block.magic_storage.creative_storage_unit"])

        pickaxe_tag = json.loads((ROOT / "src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json").read_text())
        self.assertIn("magic_storage:creative_storage_unit", pickaxe_tag["values"])

        loot_path = ROOT / "src/main/resources/data/magic_storage/loot_table/blocks/creative_storage_unit.json"
        self.assertTrue(loot_path.exists(), "Creative Storage Unit needs an explosion-safe self-drop loot table")
        loot = json.loads(loot_path.read_text())
        self.assertEqual("magic_storage:creative_storage_unit", loot["pools"][0]["entries"][0]["name"])
        self.assertEqual("minecraft:survives_explosion", loot["pools"][0]["conditions"][0]["condition"])

        recipe_dir = ROOT / "src/main/resources/data/magic_storage/recipe"
        self.assertFalse((recipe_dir / "creative_storage_unit.json").exists())
        for recipe_path in recipe_dir.glob("*.json"):
            recipe = json.loads(recipe_path.read_text())
            result = recipe.get("result")
            result_id = result.get("id") if isinstance(result, dict) else result
            self.assertNotEqual(
                "magic_storage:creative_storage_unit",
                result_id,
                f"Creative Storage Unit must not be craftable: {recipe_path.name}",
            )

    def test_item_tooltip_localizes_unlimited_capacity_without_item_generation_claims(self):
        item_path = ROOT / "src/main/java/com/swearprom/magicstorage/magic_storage/CreativeStorageUnitBlockItem.java"
        self.assertTrue(item_path.is_file(), "Creative Storage Unit needs a dedicated tooltip-bearing BlockItem")
        item = item_path.read_text()
        self.assertIn("extends BlockItem", item)
        self.assertIn("appendHoverText", item)
        self.assertIn('Component.translatable("tooltip.magic_storage.creative_storage_unit")', item)
        self.assertNotIn("net.minecraft.client", item)

        en_us = json.loads((ROOT / "src/main/resources/assets/magic_storage/lang/en_us.json").read_text())
        zh_tw = json.loads((ROOT / "src/main/resources/assets/magic_storage/lang/zh_tw.json").read_text())
        self.assertEqual(
            "Unlimited distinct item types; does not generate items",
            en_us["tooltip.magic_storage.creative_storage_unit"],
        )
        self.assertEqual(
            "無限物品種類容量；不會生成物品",
            zh_tw["tooltip.magic_storage.creative_storage_unit"],
        )

    def test_fuel_capacity_uses_creative_icon_only_for_unlimited_state(self):
        crafting_screen = (ROOT / "src/main/java/com/swearprom/magicstorage/magic_storage/CraftingTerminalScreen.java").read_text()
        method_start = crafting_screen.index("private void renderUtilityStatus")
        method_end = crafting_screen.index("private void drawStatusLine", method_start)
        method = crafting_screen[method_start:method_end]
        self.assertRegex(
            method,
            re.compile(
                r"menu\.hasUnlimitedTypeCapacity\(\)\s*"
                r"\?\s*MagicStorage\.CREATIVE_STORAGE_UNIT_ITEM\.get\(\)\.getDefaultInstance\(\)\s*"
                r":\s*MagicStorage\.STORAGE_UNIT_T1_ITEM\.get\(\)\.getDefaultInstance\(\)",
                re.DOTALL,
            ),
        )

    def test_visual_assets_are_complete_and_use_the_storage_family_pipeline(self):
        resources = ROOT / "src/main/resources"
        visual_paths = [
            resources / "assets/magic_storage/blockstates/creative_storage_unit.json",
            resources / "assets/magic_storage/models/block/creative_storage_unit.json",
            resources / "assets/magic_storage/models/item/creative_storage_unit.json",
            resources / "assets/magic_storage/textures/block/creative_storage_unit.png",
            resources / "resourcepacks/fusion_connected_casing/assets/magic_storage/models/block/creative_storage_unit.json",
            resources / "resourcepacks/fusion_connected_casing/assets/magic_storage/textures/block/creative_storage_unit_connected.png",
            resources / "resourcepacks/fusion_connected_casing/assets/magic_storage/textures/block/creative_storage_unit_connected.png.mcmeta",
        ]
        for path in visual_paths:
            self.assertTrue(path.is_file(), f"missing Creative Storage Unit asset: {path.relative_to(ROOT)}")
            if path.suffix == ".json" or path.name.endswith(".mcmeta"):
                json.loads(path.read_text())
        for texture, dimensions in ((visual_paths[3], (16, 16)), (visual_paths[5], (80, 16))):
            with texture.open("rb") as stream:
                header = stream.read(24)
            self.assertEqual(b"\x89PNG\r\n\x1a\n", header[:8])
            self.assertEqual(dimensions, struct.unpack(">II", header[16:24]))

        manifest = json.loads((
            ROOT / "art/texture-generation/20260714-terminal-family/selection.json"
        ).read_text())
        self.assertEqual(
            "creative_infinity_cell",
            manifest["members"]["magic_storage:block/creative_storage_unit"]["role"],
        )
        self.assertEqual(
            {"#3FDCE5", "#C083FF"},
            set(manifest["creative_ornament"]["accents"]),
        )
        self.assertGreaterEqual(len(manifest["creative_ornament"]["detail_points"]), 20)
        self.assertIn(
            "magic_storage:block/creative_storage_unit_connected",
            manifest["connected_textures"],
        )


if __name__ == "__main__":
    unittest.main()
