import importlib.util
import shlex
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT_PATH = ROOT / "scripts" / "setup_prism_computer_use_wrapper.py"


class SetupPrismComputerUseWrapperTests(unittest.TestCase):
    def load_script(self):
        self.assertTrue(SCRIPT_PATH.exists(), "missing scripts/setup_prism_computer_use_wrapper.py")
        spec = importlib.util.spec_from_file_location("setup_prism_computer_use_wrapper", SCRIPT_PATH)
        module = importlib.util.module_from_spec(spec)
        assert spec.loader is not None
        spec.loader.exec_module(module)
        return module

    def test_setup_writes_app_bundle_wrapper_and_patches_instance_cfg(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            instance_dir = root / "PrismLauncher" / "instances" / "dev"
            instance_dir.mkdir(parents=True)
            cfg = instance_dir / "instance.cfg"
            cfg.write_text("InstanceType=OneSix\nname=dev\nOverrideCommands=false\n")
            app_path = root / "AutoStorageMinecraftCU.app"
            wrapper_path = root / "auto_storage_minecraft_cu_wrapper.sh"

            result = mod.setup(instance_dir=instance_dir, app_path=app_path, wrapper_path=wrapper_path)

            self.assertEqual("run.hapi.autostorage.minecraftcu", result.bundle_id)
            self.assertTrue(result.app_executable.exists())
            self.assertTrue(result.wrapper_path.exists())
            self.assertTrue(result.app_executable.stat().st_mode & 0o111)
            self.assertTrue(result.wrapper_path.stat().st_mode & 0o111)
            self.assertIn("run.hapi.autostorage.minecraftcu", (app_path / "Contents/Info.plist").read_text())
            self.assertIn("--mscu-stdin", result.app_executable.read_text())
            self.assertIn("exec \"$@\" < \"$stdin_path\"", result.app_executable.read_text())
            self.assertIn("mkfifo", result.wrapper_path.read_text())
            self.assertIn("open -n -W \"$APP\"", result.wrapper_path.read_text())

            cfg_lines = cfg.read_text().splitlines()
            self.assertIn("OverrideCommands=true", cfg_lines)
            self.assertIn(f"WrapperCommand={wrapper_path}", cfg_lines)
            self.assertEqual(1, sum(line.startswith("OverrideCommands=") for line in cfg_lines))
            self.assertEqual(1, sum(line.startswith("WrapperCommand=") for line in cfg_lines))

    def test_setup_is_idempotent_and_preserves_unrelated_cfg_lines(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            instance_dir = root / "instances" / "dev"
            instance_dir.mkdir(parents=True)
            cfg = instance_dir / "instance.cfg"
            wrapper_path = root / "auto_storage_minecraft_cu_wrapper.sh"
            app_path = root / "AutoStorageMinecraftCU.app"
            cfg.write_text(f"InstanceType=OneSix\nWrapperCommand={wrapper_path}\nOverrideCommands=true\nJavaPath=/tmp/java\n")

            first = mod.setup(instance_dir=instance_dir, app_path=app_path, wrapper_path=wrapper_path)
            second = mod.setup(instance_dir=instance_dir, app_path=app_path, wrapper_path=wrapper_path)

            self.assertFalse(second.instance_cfg_changed)
            self.assertEqual(first.wrapper_path, second.wrapper_path)
            text = cfg.read_text()
            self.assertIn("JavaPath=/tmp/java", text)
            self.assertEqual(1, text.count("OverrideCommands="))
            self.assertEqual(1, text.count("WrapperCommand="))

    def test_generated_shell_scripts_quote_custom_paths_with_spaces(self):
        mod = self.load_script()
        app_path = Path("/tmp/Auto Storage Minecraft CU.app")
        log_path = Path("/tmp/auto storage minecraft cu.log")

        self.assertIn(f"APP={shlex.quote(str(app_path))}", mod.wrapper_text(app_path, log_path))
        self.assertIn(f"LOG={shlex.quote(str(log_path))}", mod.wrapper_text(app_path, log_path))
        self.assertIn(f"LOG={shlex.quote(str(log_path))}", mod.app_executable_text(log_path))


if __name__ == "__main__":
    unittest.main()
