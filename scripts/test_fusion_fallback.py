from pathlib import Path
import json
import unittest


ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "src/main/resources/resourcepacks/fusion_connected_casing"


class FusionFallbackTests(unittest.TestCase):
    def test_fusion_overlay_is_loaded_only_when_fusion_is_present(self):
        client_setup = (ROOT / "src/main/java/com/swear/autostorage/ClientSetup.java").read_text()
        self.assertIn('ModList.get().isLoaded("fusion")', client_setup)
        self.assertIn('"resourcepacks/fusion_connected_casing"', client_setup)
        self.assertIn("PackType.CLIENT_RESOURCES", client_setup)
        self.assertIn("PackSource.DEFAULT", client_setup)
        self.assertIn("true,", client_setup)
        self.assertIn("Pack.Position.TOP", client_setup)

    def test_overlay_contains_the_fusion_models_and_connected_textures(self):
        metadata = json.loads((PACK / "pack.mcmeta").read_text())
        self.assertIn("pack", metadata)
        models = list((PACK / "assets/auto_storage/models/block").glob("*.json"))
        self.assertEqual(12, len(models))
        self.assertTrue(all(json.loads(path.read_text()).get("loader") == "fusion:model" for path in models))
        textures = list((PACK / "assets/auto_storage/textures/block").glob("*_connected.png"))
        self.assertEqual(14, len(textures))


if __name__ == "__main__":
    unittest.main()
