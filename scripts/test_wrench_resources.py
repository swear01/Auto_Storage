import json
from pathlib import Path
import struct
import unittest


ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"


class WrenchResourceTests(unittest.TestCase):
    def read_json(self, relative: str) -> dict:
        path = RESOURCES / relative
        self.assertTrue(path.is_file(), f"missing {path.relative_to(ROOT)}")
        return json.loads(path.read_text())

    def test_wrench_has_exact_early_game_recipe(self):
        self.assertEqual(
            {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [
                    {"item": "minecraft:brush"},
                    {"item": "minecraft:tripwire_hook"},
                ],
                "result": {"id": "auto_storage:wrench"},
            },
            self.read_json("data/auto_storage/recipe/wrench.json"),
        )

    def test_wrench_joins_common_interoperability_tag(self):
        self.assertEqual(
            {"replace": False, "values": ["auto_storage:wrench"]},
            self.read_json("data/c/tags/item/tools/wrench.json"),
        )

    def test_wrench_uses_plain_inventory_item_model_and_native_texture(self):
        self.assertEqual(
            {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": "auto_storage:item/wrench"},
            },
            self.read_json("assets/auto_storage/models/item/wrench.json"),
        )
        texture = RESOURCES / "assets/auto_storage/textures/item/wrench.png"
        self.assertTrue(texture.is_file(), f"missing {texture.relative_to(ROOT)}")
        with texture.open("rb") as stream:
            header = stream.read(24)
        self.assertEqual(b"\x89PNG\r\n\x1a\n", header[:8])
        self.assertEqual(b"IHDR", header[12:16])
        self.assertEqual((16, 16), struct.unpack(">II", header[16:24]))
        self.assertFalse(texture.with_suffix(".png.mcmeta").exists())

    def test_wrench_name_is_localized_with_language_parity(self):
        english = self.read_json("assets/auto_storage/lang/en_us.json")
        traditional_chinese = self.read_json("assets/auto_storage/lang/zh_tw.json")
        key = "item.auto_storage.wrench"
        self.assertEqual("Wrench", english.get(key))
        self.assertEqual("扳手", traditional_chinese.get(key))
        self.assertEqual(set(english), set(traditional_chinese))

    def test_shared_hook_is_registered_and_uses_the_common_tag(self):
        auto_storage = (
            ROOT
            / "src/main/java/com/swear/autostorage/AutoStorage.java"
        ).read_text()
        actions_path = (
            ROOT
            / "src/main/java/com/swear/autostorage/WrenchActions.java"
        )
        self.assertIn('ITEMS.register("wrench"', auto_storage)
        self.assertIn("output.accept(WRENCH.get())", auto_storage)
        self.assertIn(
            "NeoForge.EVENT_BUS.addListener(WrenchActions::onRightClickBlock)",
            auto_storage,
        )
        self.assertTrue(actions_path.is_file(), f"missing {actions_path.relative_to(ROOT)}")
        actions = actions_path.read_text()
        self.assertIn("Tags.Items.TOOLS_WRENCH", actions)
        self.assertIn("InteractionHand.MAIN_HAND", actions)
        self.assertNotIn("level.isClientSide() && level.setBlock", actions)


if __name__ == "__main__":
    unittest.main()
