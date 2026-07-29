import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class MacOsBorderlessFullscreenTests(unittest.TestCase):
    def test_client_mixin_replaces_monitor_backed_f11_without_native_fullscreen(self):
        metadata = (ROOT / "src/main/templates/META-INF/neoforge.mods.toml").read_text()
        self.assertIn('config="magic_storage.mixins.json"', metadata)

        config = json.loads((ROOT / "src/main/resources/magic_storage.mixins.json").read_text())
        self.assertTrue(config["required"])
        self.assertEqual(
            ["MacOsWindowMixin", "MinecraftDisconnectMixin"],
            config["client"],
        )
        self.assertEqual(1, config["injectors"]["defaultRequire"])

        source = (
            ROOT
            / "src/main/java/com/swearprom/magicstorage/magic_storage/mixin/MacOsWindowMixin.java"
        ).read_text()
        self.assertIn("glfwSetWindowMonitor", source)
        self.assertIn("glfwGetWindowMonitor", source)
        self.assertNotIn("glfwCreateWindow", source)
        self.assertIn('method = "setMode"', source)
        self.assertIn("Minecraft.ON_OSX", source)
        self.assertIn("GLFW_DECORATED", source)
        self.assertIn("GLFW_DONT_CARE", source)
        self.assertIn("glfwGetCocoaWindow", source)
        self.assertIn("if (!CocoaWindow.isBorderlessFullscreen(window))", source)
        self.assertIn("return CocoaWindow.isBorderlessFullscreen(window) ? 1L", source)
        self.assertIn("setPresentationOptions:", source)
        self.assertIn("setLevel:", source)
        self.assertNotIn("toggleFullScreen", source)
        self.assertNotIn("MacosUtil", source)
        self.assertNotIn("MacOsWindowAccess", source)
        self.assertNotIn("@Shadow", source)

    def test_disconnect_leaves_borderless_fullscreen_before_the_forced_render_tick(self):
        source = (
            ROOT
            / "src/main/java/com/swearprom/magicstorage/magic_storage/mixin/MinecraftDisconnectMixin.java"
        ).read_text()
        self.assertIn('method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V"', source)
        self.assertIn("@At(\"HEAD\")", source)
        self.assertIn("Minecraft.ON_OSX", source)
        self.assertIn("minecraft.level != null", source)
        self.assertIn("window.isFullscreen()", source)
        self.assertIn("window.toggleFullScreen()", source)
        self.assertNotIn("MacOsWindowMixin", source)
        self.assertNotIn("MacOsWindowAccess", source)

        access = (
            ROOT
            / "src/main/java/com/swearprom/magicstorage/magic_storage/mixin/MacOsWindowAccess.java"
        )
        self.assertFalse(access.exists())


if __name__ == "__main__":
    unittest.main()
