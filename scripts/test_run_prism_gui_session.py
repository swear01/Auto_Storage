import hashlib
import importlib.util
import json
import signal
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT_PATH = ROOT / "scripts" / "run_prism_gui_session.py"


class RunPrismGuiSessionTests(unittest.TestCase):
    @staticmethod
    def terminal_scale_fixture_summary(scale_types: int) -> dict:
        base_items = (
            "minecraft:oak_log",
            "minecraft:spruce_log",
            "minecraft:stone",
            "minecraft:iron_ingot",
            "magic_storage:storage_unit_t1",
            "magic_storage:storage_unit_t2",
            "magic_storage:storage_terminal",
            "magic_storage:crafting_terminal",
        )
        digest = hashlib.sha256()
        amount_sum = 0
        for index in range(scale_types):
            item_id = base_items[index % len(base_items)]
            custom_name = json.dumps(
                {
                    "italic": False,
                    "text": f"Terminal Scale {index:05d}",
                },
                separators=(",", ":"),
                sort_keys=True,
            )
            amount = ((index * 104_729) % 1_000_000) + 1
            digest.update(f"{item_id}\0{custom_name}\0{amount}\n".encode())
            amount_sum += amount
        return {
            "generator_schema": 1,
            "type_count": scale_types,
            "segment_limit": 63,
            "segment_count": (scale_types + 62) // 63,
            "repository_records": 1,
            "base_item_ids": list(base_items),
            "component": "minecraft:custom_name",
            "key_sha256": digest.hexdigest(),
            "amount_sum": amount_sum,
        }

    def load_script(
        self,
        stub_prism_version: bool = True,
        stub_prism_root: bool = True,
        stub_running_prism: bool = True,
    ):
        self.assertTrue(SCRIPT_PATH.exists(), "missing scripts/run_prism_gui_session.py")
        spec = importlib.util.spec_from_file_location("run_prism_gui_session", SCRIPT_PATH)
        module = importlib.util.module_from_spec(spec)
        assert spec.loader is not None
        spec.loader.exec_module(module)
        if stub_prism_version and hasattr(module, "verify_prism_version"):
            module.verify_prism_version = lambda prism_app: "11.0.3"
        if stub_prism_root and hasattr(module, "verify_no_prism_auth_refresh"):
            module.verify_no_prism_auth_refresh = lambda *args, **kwargs: None
        if stub_running_prism and hasattr(module, "require_running_normal_prism"):
            module.require_running_normal_prism = lambda: None
        return module

    def fake_prepare(
        self,
        minecraft_dir: Path,
        source_world: str,
        target_world: str,
        scenario_name: str | None = None,
        scale_types: int | None = None,
        items_per_type: int | None = None,
    ):
        world_dir = minecraft_dir / "saves" / target_world
        world_dir.mkdir(parents=True, exist_ok=True)
        hotbar_views = {
            "terminal-left-rail": {
                "1": {"slot": 0, "function": "view_storage_terminal", "target": "storage_terminal"},
                "2": {"slot": 1, "function": "view_crafting_terminal", "target": "crafting_terminal"},
            },
            "bus-configuration": {
                "5": {"slot": 4, "function": "view_import_bus", "target": "import_bus"},
                "6": {"slot": 5, "function": "view_export_bus", "target": "export_bus"},
                "7": {"slot": 6, "function": "view_import_bus", "target": "import_bus"},
                "9": {"slot": 8, "function": "reset_from_hotbar", "target": "reset"},
            },
            "crafting-fuel-page": {
                "1": {"slot": 0, "function": "view_storage_terminal", "target": "storage_terminal"},
                "2": {"slot": 1, "function": "view_crafting_terminal", "target": "crafting_terminal"},
            },
            "terminal-scale": {
                "1": {"slot": 0, "function": "view_storage_terminal", "target": "storage_terminal"},
                "2": {"slot": 1, "function": "view_crafting_terminal", "target": "crafting_terminal"},
            },
        }.get(scenario_name, {})
        return {
            "schema_version": 5,
            "scenario": scenario_name,
            "world_name": target_world,
            "world_dir": str(world_dir),
            "start_target": {
                "terminal-left-rail": "storage_terminal",
                "bus-configuration": "import_bus",
                "crafting-fuel-page": "crafting_terminal",
                "terminal-scale": "crafting_terminal",
                "patchouli-guide": "overview",
            }.get(scenario_name, "overview"),
            "player_kit": {
                "hotbar": (
                    {
                        "3": {
                            "slot": "hotbar.2",
                            "item": "minecraft:coal",
                            "count": 1,
                        }
                    }
                    if scenario_name == "crafting-fuel-page"
                    else {
                        "1": {
                            "slot": "hotbar.0",
                            "item": "magic_storage:storage_terminal",
                            "count": 1,
                        },
                        "2": {
                            "slot": "hotbar.1",
                            "item": "magic_storage:crafting_terminal",
                            "count": 1,
                        },
                    }
                    if scenario_name == "terminal-scale"
                    else {}
                ),
                "inventory": [],
            },
            "baseline": {
                "installed_stations": (
                    {
                        "magic_storage:mekanism_crusher": {
                            "item": "mekanism:ultimate_crushing_factory",
                            "count": 2_147_483_647,
                        }
                    }
                    if scenario_name == "crafting-fuel-page"
                    else {}
                ),
                "stored_items": {},
                "stored_stacks": [],
                **(
                    {
                        "scale_fixture": self.terminal_scale_fixture_summary(
                            scale_types
                        ),
                        "runtime_fixture": {
                            "registry": "runtime",
                            "items_per_type": items_per_type,
                            "installable_descriptors": "all",
                            "processing_count": 130,
                            "instant_count": 1,
                            "ready_log": "MS_GUI_RUNTIME_FIXTURE_READY",
                        },
                    }
                    if scenario_name == "terminal-scale"
                    else {}
                ),
            },
            "bootstrap": {
                "core_preloaded": scenario_name in {
                    "crafting-fuel-page",
                    "terminal-scale",
                },
                **(
                    {
                        "runtime_fixture_ready_log":
                            "MS_GUI_RUNTIME_FIXTURE_READY",
                    }
                    if scenario_name == "terminal-scale"
                    else {}
                ),
            },
            "hotbar_views": hotbar_views,
            "world_generator": {
                "type": "minecraft:flat",
                "biome": "minecraft:the_void",
                "layers": [{"height": 1, "block": "minecraft:air"}],
                "features": False,
                "lakes": False,
                "structure_overrides": [],
            },
            "fullscreen_gate": {
                "required": True,
                "when": "after_world_ready_before_first_gui_action",
                "launch_mode": "minecraft_macos_borderless_fullscreen",
                "automatic": True,
                "accepted_methods": ["minecraft_f11_borderless"],
                "forbidden_methods": ["macos_native_fullscreen", "combined_native_and_minecraft_fullscreen"],
            },
            "desktop_display_mode": {
                "width": 1470,
                "height": 956,
                "pixel_width": 2940,
                "pixel_height": 1912,
                "refresh_rate": 60,
                "depth": 24,
            },
        }

    def test_run_session_passes_scenario_into_world_preparation(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            minecraft_dir = root / "minecraft"
            (minecraft_dir / "logs").mkdir(parents=True)
            received = []

            def prepare(minecraft, source, target, *, scenario_name):
                received.append(scenario_name)
                return self.fake_prepare(minecraft, source, target, scenario_name)

            mod.run_session(
                scenario_name="bus-configuration",
                minecraft_dir=minecraft_dir,
                instance_dir=root / "instances" / "dev",
                run_root=root / "gui-runs",
                prepare_world_func=prepare,
                configure_instance_func=lambda instance_dir: True,
                no_launch=True,
                timestamp_func=lambda: "20260723-150000",
            )

            self.assertEqual(["bus-configuration"], received)

    def test_terminal_scale_requires_supported_count_and_forwards_it_to_world_preparation(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            minecraft_dir = root / "minecraft"
            (minecraft_dir / "logs").mkdir(parents=True)

            common = {
                "minecraft_dir": minecraft_dir,
                "instance_dir": root / "instances" / "dev",
                "run_root": root / "gui-runs",
                "configure_instance_func": lambda instance_dir: True,
                "no_launch": True,
            }
            with self.assertRaisesRegex(
                ValueError,
                "terminal-scale.*--scale-types.*required",
            ):
                mod.run_session(scenario_name="terminal-scale", **common)
            with self.assertRaisesRegex(
                ValueError,
                "--scale-types.*10000.*30000",
            ):
                mod.run_session(
                    scenario_name="terminal-scale",
                    scale_types=9_999,
                    **common,
                )
            with self.assertRaisesRegex(
                ValueError,
                "--scale-types.*only.*terminal-scale",
            ):
                mod.run_session(
                    scenario_name="boot-smoke",
                    scale_types=10_000,
                    **common,
                )

            received = []

            def prepare(
                minecraft,
                source,
                target,
                *,
                scenario_name,
                scale_types,
                items_per_type,
            ):
                received.append((scenario_name, scale_types, items_per_type))
                return self.fake_prepare(
                    minecraft,
                    source,
                    target,
                    scenario_name,
                    scale_types,
                    items_per_type,
                )

            result = mod.run_session(
                scenario_name="terminal-scale",
                scale_types=10_000,
                items_per_type=64,
                prepare_world_func=prepare,
                timestamp_func=lambda: "20260728-010203",
                **common,
            )

            self.assertEqual([("terminal-scale", 10_000, 64)], received)
            self.assertEqual(10_000, result.manifest["baseline"]["scale_fixture"]["type_count"])
            self.assertEqual(
                64,
                result.manifest["baseline"]["runtime_fixture"]["items_per_type"],
            )

    def test_terminal_scale_manifest_validation_rejects_expanded_or_nonempty_fixture(self):
        mod = self.load_script()
        manifest = self.fake_prepare(
            Path("/tmp/minecraft"),
            "New World",
            "MagicStorageGuiTest",
            "terminal-scale",
            10_000,
            64,
        )
        mod.validate_scenario_manifest("terminal-scale", manifest, 10_000, 64)

        for mutation in (
            lambda value: value["player_kit"]["hotbar"].update(
                {"1": {"item": "minecraft:stone"}}
            ),
            lambda value: value["player_kit"]["inventory"].append(
                {"slot": "inventory.0", "item": "minecraft:stone", "count": 1}
            ),
            lambda value: value["baseline"]["stored_stacks"].append(
                {"item": "minecraft:stone", "amount": 1}
            ),
            lambda value: value["baseline"]["scale_fixture"].update(
                {"segment_limit": 64}
            ),
            lambda value: value["baseline"]["scale_fixture"].update(
                {"repository_records": 2}
            ),
            lambda value: value["baseline"]["scale_fixture"].update(
                {"key_sha256": "b" * 64}
            ),
            lambda value: value["baseline"]["scale_fixture"].update(
                {"amount_sum": 1}
            ),
        ):
            invalid = json.loads(json.dumps(manifest))
            mutation(invalid)
            with self.assertRaisesRegex(
                RuntimeError,
                "terminal-scale preloaded Core contract",
            ):
                mod.validate_scenario_manifest(
                    "terminal-scale",
                    invalid,
                    10_000,
                    64,
                )

    def test_terminal_scale_writes_a_focused_fullscreen_checklist(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            minecraft_dir = root / "minecraft"
            (minecraft_dir / "logs").mkdir(parents=True)
            result = mod.run_session(
                scenario_name="terminal-scale",
                scale_types=30_000,
                items_per_type=64,
                minecraft_dir=minecraft_dir,
                instance_dir=root / "instances" / "dev",
                run_root=root / "gui-runs",
                prepare_world_func=self.fake_prepare,
                configure_instance_func=lambda instance_dir: True,
                no_launch=True,
                timestamp_func=lambda: "20260728-020304",
            )

            checklist = (result.run_dir / "checklist.md").read_text()
            self.assertIn("30,000 exact component-bearing types", checklist)
            self.assertIn("empty player inventory", checklist)
            self.assertIn("Name, ID, Quantity, and Mod", checklist)
            self.assertIn("@magic_storage", checklist)
            self.assertIn("#minecraft:logs", checklist)
            self.assertIn("Storage", checklist)
            self.assertIn("Craftable", checklist)
            self.assertIn("every runtime registered item", checklist)
            self.assertIn("all installable station fields", checklist)
            self.assertIn("recipe preview", checklist)
            self.assertIn("first and far-end", checklist)
            self.assertIn("fullscreen gate", checklist)
            self.assertNotIn("install any station", checklist.lower())

    def test_terminal_scale_launch_uses_only_base_deployment_preflight(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            minecraft_dir = root / "minecraft"
            (minecraft_dir / "logs").mkdir(parents=True)
            (root / "logs").mkdir()
            (root / "logs" / "PrismLauncher-0.log").write_text("")
            preflights = []
            mod.verify_deployed_magic_storage_jar = (
                lambda project_dir, target: preflights.append("magic-storage")
            )
            mod.verify_deployed_fusion_jar = (
                lambda target: preflights.append("fusion")
            )
            mod.verify_deployed_gui_support_jars = (
                lambda project_dir, target: preflights.append("support-pack")
            )
            mod.snapshot_processes = lambda: {}

            mod.run_session(
                scenario_name="terminal-scale",
                scale_types=10_000,
                items_per_type=64,
                minecraft_dir=minecraft_dir,
                instance_dir=root / "instances" / "dev",
                run_root=root / "gui-runs",
                prepare_world_func=self.fake_prepare,
                cleanup_existing_func=lambda *args: None,
                configure_instance_func=lambda instance_dir: True,
                launcher=lambda command: None,
                wait_for_log_func=lambda **kwargs: (
                    "SelfTest: 104 passed\n"
                    "MS_GUI_RUNTIME_FIXTURE_READY\n"
                    "MS_GUI_TEST_READY\n"
                ),
                auth_verifier=lambda text: None,
                display_mode_verifier=lambda manifest: None,
                watchdog_launcher=lambda *args: None,
                timestamp_func=lambda: "20260728-030405",
            )

            self.assertEqual(
                ["magic-storage", "fusion", "support-pack"],
                preflights,
            )

    def test_crafting_scenario_rejects_a_manifest_without_preloaded_core_contract(self):
        mod = self.load_script()
        with self.assertRaisesRegex(RuntimeError, "preloaded Core contract"):
            mod.validate_scenario_manifest(
                "crafting-fuel-page",
                {
                    "schema_version": 5,
                    "scenario": "crafting-fuel-page",
                    "start_target": "crafting_terminal",
                    "bootstrap": {"core_preloaded": False},
                    "baseline": {},
                    "player_kit": {"hotbar": {}, "inventory": []},
                },
            )

    def configure_matching_deployment(self, mod, root: Path, minecraft_dir: Path) -> None:
        project_dir = root / "project"
        (project_dir / "build" / "libs").mkdir(parents=True)
        (minecraft_dir / "mods").mkdir(parents=True, exist_ok=True)
        (root / "logs").mkdir(exist_ok=True)
        (root / "logs" / "PrismLauncher-0.log").write_text("")
        (project_dir / "gradle.properties").write_text("mod_version=0.1.7\n")
        jar_bytes = b"matching build"
        (project_dir / "build" / "libs" / "magic_storage-0.1.7.jar").write_bytes(jar_bytes)
        (minecraft_dir / "mods" / "magic_storage-0.1.7.jar").write_bytes(jar_bytes)
        fusion_bytes = b"matching Fusion test artifact"
        mod.FUSION_FILENAME = "fusion-1.2.12-neoforge-mc1.21.1.jar"
        mod.FUSION_SHA512 = hashlib.sha512(fusion_bytes).hexdigest()
        (minecraft_dir / "mods" / mod.FUSION_FILENAME).write_bytes(fusion_bytes)
        mod.DEFAULT_PROJECT_DIR = project_dir

    def process_snapshots(self, minecraft_dir: Path):
        runner_prism = (
            "/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher "
            "-l dev -w MagicStorageGuiTest -o MagicStorageBot"
        )
        baseline = {
            110: (1, "/usr/bin/python3 unrelated.py"),
        }
        current = {
            **baseline,
            200: (1, runner_prism),
            201: (200, f"/usr/bin/java -Duser.dir={minecraft_dir} net.minecraft.client.main.Main"),
            300: (1, "/usr/bin/python3 other_new_work.py"),
            400: (1, "/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher -l other -w OtherWorld"),
            401: (400, "/usr/bin/java -Duser.dir=/tmp/other-instance net.minecraft.client.main.Main"),
        }
        return baseline, current

    def test_run_session_writes_artifacts_launches_world_and_marks_manual_gui_required(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            minecraft_dir = root / "minecraft"
            (minecraft_dir / "logs").mkdir(parents=True)
            latest_log = minecraft_dir / "logs" / "latest.log"
            latest_log.write_text("old run\nMS_GUI_TEST_READY\n")
            self.configure_matching_deployment(mod, root, minecraft_dir)
            mod.snapshot_processes = lambda: {}
            launched = []
            configured = []
            cleaned = []
            verified_modes = []

            result = mod.run_session(
                scenario_name="terminal-left-rail",
                minecraft_dir=minecraft_dir,
                instance_dir=root / "instances" / "dev",
                run_root=root / "gui-runs",
                prepare_world_func=self.fake_prepare,
                cleanup_existing_func=lambda *args: cleaned.append(args),
                configure_instance_func=lambda instance_dir: configured.append(instance_dir) or True,
                launcher=lambda command: launched.append(command),
                wait_for_log_func=lambda **kwargs: "SelfTest: 104 passed\nMS_GUI_TEST_READY\n",
                display_mode_verifier=lambda manifest: verified_modes.append(manifest["desktop_display_mode"]),
                timestamp_func=lambda: "20260711-010203",
            )

            self.assertTrue(result.manual_gui_required)
            self.assertEqual(1, len(cleaned))
            self.assertEqual([1470], [mode["width"] for mode in verified_modes])
            self.assertEqual([(root / "instances" / "dev").resolve()], configured)
            self.assertEqual(
                "Minecraft is ready in the fixed test world. Please take over for the fullscreen visual checks; close with F11, wait for the normal window, then Command-Q.",
                result.manual_handoff_message,
            )
            self.assertEqual(
                [
                    "/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher",
                    "-l",
                    "dev",
                    "-w",
                    "MagicStorageGuiTest",
                    "-o",
                    "MagicStorageBot",
                ],
                launched[0],
            )
            self.assertTrue(result.run_dir.name.endswith("terminal-left-rail"))
            checklist = (result.run_dir / "checklist.md").read_text()
            self.assertIn("-o MagicStorageBot", checklist)
            self.assertIn("fullscreen gate", checklist)
            self.assertIn("hotbar `1`", checklist)
            self.assertIn("hotbar `2`", checklist)
            self.assertIn("registered resource kinds only", checklist)
            self.assertIn("Gases appears only when a chemical provider", checklist)
            self.assertIn("Other appears only when an addon", checklist)
            self.assertIn("middle-click resets it to Items", checklist)
            self.assertIn("Storage and Craftable expose the resource selector", checklist)
            self.assertIn("Transform and Stations hide the resource selector", checklist)
            self.assertIn("reopen Storage Terminal", checklist)
            self.assertIn("restart the client", checklist)
            self.assertIn("Crafting-only page, source, output, and Transform Target", checklist)
            self.assertIn("Manual GUI required: yes", checklist)
            self.assertIn("Visual verification owner: user", checklist)
            self.assertIn("automatically starts in borderless Minecraft F11 fullscreen", checklist)
            self.assertIn("never attaches the GLFW window to the monitor", checklist)
            self.assertIn("Do not use the macOS green fullscreen button", checklist)
            self.assertIn("press F11 once", checklist)
            self.assertIn("wait until the normal window is visible", checklist)
            self.assertIn("then press Command-Q", checklist)
            self.assertIn("Do not press Command-Q while Minecraft F11 fullscreen is still active", checklist)
            self.assertIn("shutdown watchdog", checklist)
            self.assertNotIn("native fullscreen or F11 fullscreen", checklist)
            self.assertIn("Stop automation here and hand control to the user", checklist)
            self.assertIn("known offline profile-properties 401", checklist)
            self.assertIn("Botania snapshot README", checklist)
            self.assertIn("Industrial Foregoing Curios", checklist)
            self.assertIn("no non-whitelisted", checklist)
            self.assertNotIn("no advanced_container_set_data, ERROR, FATAL, or Caused by", checklist)
            self.assertNotIn("Computer Use bundle id", checklist)
            session = json.loads((result.run_dir / "session.json").read_text())
            self.assertEqual("terminal-left-rail", session["scenario"])
            self.assertEqual("user", session["visual_verification_owner"])
            self.assertTrue(session["manual_handoff_required"])
            self.assertEqual(result.manual_handoff_message, session["manual_handoff_message"])
            self.assertTrue(session["launch_profile"]["computer_use_wrapper_disabled"])
            self.assertTrue(session["launch_profile"]["error_console_disabled"])
            self.assertEqual("minecraft_f11_borderless", session["launch_profile"]["fullscreen_mode"])
            self.assertTrue(session["launch_profile"]["automatic_fullscreen"])
            self.assertEqual(1470, session["launch_profile"]["desktop_display_mode"]["width"])
            self.assertEqual("f11_then_command_q", session["shutdown_profile"]["safe_sequence"])
            self.assertTrue(session["shutdown_profile"]["watchdog_enabled"])
            self.assertEqual(5, session["shutdown_profile"]["stall_timeout_seconds"])
            self.assertNotIn("computer_use_wrapper", session)
            self.assertEqual("after_world_ready_before_first_gui_action", session["manifest"]["fullscreen_gate"]["when"])
            self.assertIn("SelfTest: 104 passed", (result.run_dir / "log-excerpt.log").read_text())

    def test_boot_smoke_scenario_is_non_visual_and_does_not_require_manual_gui(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            minecraft_dir = root / "minecraft"
            (minecraft_dir / "logs").mkdir(parents=True)
            self.configure_matching_deployment(mod, root, minecraft_dir)
            mod.snapshot_processes = lambda: {}
            wait_calls = []
            result = mod.run_session(
                scenario_name="boot-smoke",
                minecraft_dir=minecraft_dir,
                instance_dir=root / "instances" / "dev",
                run_root=root / "gui-runs",
                prepare_world_func=self.fake_prepare,
                configure_instance_func=lambda instance_dir: True,
                launcher=lambda command: None,
                wait_for_log_func=lambda **kwargs: wait_calls.append(kwargs) or "SelfTest: 104 passed\nMS_GUI_TEST_READY\n",
                display_mode_verifier=lambda manifest: None,
                timestamp_func=lambda: "20260711-010203",
            )

            self.assertFalse(result.manual_gui_required)
            self.assertEqual(300, wait_calls[0]["timeout_seconds"])
            checklist = (result.run_dir / "checklist.md").read_text()
            self.assertIn("Manual GUI required: no", checklist)
            self.assertIn("No visual pass is required", checklist)
            self.assertNotIn("Computer Use", checklist)

    def test_run_session_rejects_same_version_different_jar_bytes_before_launch(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            project_dir = root / "project"
            minecraft_dir = root / "minecraft"
            (project_dir / "build" / "libs").mkdir(parents=True)
            (minecraft_dir / "logs").mkdir(parents=True)
            (minecraft_dir / "mods").mkdir()
            (project_dir / "gradle.properties").write_text("mod_version=0.1.7\n")
            (project_dir / "build" / "libs" / "magic_storage-0.1.7.jar").write_bytes(b"current build")
            (minecraft_dir / "mods" / "magic_storage-0.1.7.jar").write_bytes(b"stale deployed build")
            mod.DEFAULT_PROJECT_DIR = project_dir
            launched = []

            with self.assertRaisesRegex(RuntimeError, r"contents differ.*deploy_prism_dev\.py"):
                mod.run_session(
                    scenario_name="boot-smoke",
                    minecraft_dir=minecraft_dir,
                    instance_dir=root / "instances" / "dev",
                    run_root=root / "gui-runs",
                    prepare_world_func=self.fake_prepare,
                    configure_instance_func=lambda instance_dir: True,
                    launcher=lambda command: launched.append(command),
                    wait_for_log_func=lambda **kwargs: "SelfTest: 104 passed\nMS_GUI_TEST_READY\n",
                    timestamp_func=lambda: "20260712-020304",
                )

            self.assertEqual([], launched)

    def test_run_session_rejects_multiple_deployed_magic_storage_jars_before_launch(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            project_dir = root / "project"
            minecraft_dir = root / "minecraft"
            (project_dir / "build" / "libs").mkdir(parents=True)
            (minecraft_dir / "logs").mkdir(parents=True)
            (minecraft_dir / "mods").mkdir()
            (project_dir / "gradle.properties").write_text("mod_version=0.1.7\n")
            jar_bytes = b"current build"
            (project_dir / "build" / "libs" / "magic_storage-0.1.7.jar").write_bytes(jar_bytes)
            (minecraft_dir / "mods" / "magic_storage-0.1.7.jar").write_bytes(jar_bytes)
            (minecraft_dir / "mods" / "magic_storage-0.1.6.jar").write_bytes(b"old build")
            mod.DEFAULT_PROJECT_DIR = project_dir
            launched = []

            with self.assertRaisesRegex(RuntimeError, r"exactly one.*deploy_prism_dev\.py"):
                mod.run_session(
                    scenario_name="boot-smoke",
                    minecraft_dir=minecraft_dir,
                    instance_dir=root / "instances" / "dev",
                    run_root=root / "gui-runs",
                    prepare_world_func=self.fake_prepare,
                    configure_instance_func=lambda instance_dir: True,
                    launcher=lambda command: launched.append(command),
                    wait_for_log_func=lambda **kwargs: "SelfTest: 104 passed\nMS_GUI_TEST_READY\n",
                    timestamp_func=lambda: "20260712-020305",
                )

            self.assertEqual([], launched)

    def test_run_session_rejects_missing_exact_fusion_before_launch(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            minecraft_dir = root / "minecraft"
            (minecraft_dir / "logs").mkdir(parents=True)
            self.configure_matching_deployment(mod, root, minecraft_dir)
            (minecraft_dir / "mods" / mod.FUSION_FILENAME).unlink()
            launched = []

            with self.assertRaisesRegex(RuntimeError, r"exactly one Fusion jar.*deploy_prism_dev\.py"):
                mod.run_session(
                    scenario_name="boot-smoke",
                    minecraft_dir=minecraft_dir,
                    instance_dir=root / "instances" / "dev",
                    run_root=root / "gui-runs",
                    prepare_world_func=self.fake_prepare,
                    configure_instance_func=lambda instance_dir: True,
                    launcher=lambda command: launched.append(command),
                    wait_for_log_func=lambda **kwargs: "SelfTest: 104 passed\nMS_GUI_TEST_READY\n",
                    timestamp_func=lambda: "20260714-010101",
                )

            self.assertEqual([], launched)

    def test_run_session_rejects_wrong_fusion_bytes_before_launch(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            minecraft_dir = root / "minecraft"
            (minecraft_dir / "logs").mkdir(parents=True)
            self.configure_matching_deployment(mod, root, minecraft_dir)
            (minecraft_dir / "mods" / mod.FUSION_FILENAME).write_bytes(b"wrong Fusion bytes")
            launched = []

            with self.assertRaisesRegex(RuntimeError, r"Fusion jar contents differ.*deploy_prism_dev\.py"):
                mod.run_session(
                    scenario_name="boot-smoke",
                    minecraft_dir=minecraft_dir,
                    instance_dir=root / "instances" / "dev",
                    run_root=root / "gui-runs",
                    prepare_world_func=self.fake_prepare,
                    configure_instance_func=lambda instance_dir: True,
                    launcher=lambda command: launched.append(command),
                    wait_for_log_func=lambda **kwargs: "SelfTest: 104 passed\nMS_GUI_TEST_READY\n",
                    timestamp_func=lambda: "20260714-010102",
                )

            self.assertEqual([], launched)

    def test_boot_smoke_success_cleans_only_new_runner_processes(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            minecraft_dir = root / "minecraft"
            (minecraft_dir / "logs").mkdir(parents=True)
            self.configure_matching_deployment(mod, root, minecraft_dir)
            baseline, current = self.process_snapshots(minecraft_dir)
            snapshots = iter([baseline, baseline, current])
            terminated = []
            mod.snapshot_processes = lambda: next(snapshots)
            mod.terminate_processes = lambda pids: terminated.append(pids)

            mod.run_session(
                scenario_name="boot-smoke",
                minecraft_dir=minecraft_dir,
                instance_dir=root / "instances" / "dev",
                run_root=root / "gui-runs",
                prepare_world_func=self.fake_prepare,
                configure_instance_func=lambda instance_dir: True,
                launcher=lambda command: None,
                wait_for_log_func=lambda **kwargs: "SelfTest: 104 passed\nMS_GUI_TEST_READY\n",
                display_mode_verifier=lambda manifest: None,
                timestamp_func=lambda: "20260712-030405",
            )

            self.assertEqual([[201, 200]], terminated)

    def test_visual_failure_before_handoff_cleans_only_new_runner_processes(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            minecraft_dir = root / "minecraft"
            (minecraft_dir / "logs").mkdir(parents=True)
            self.configure_matching_deployment(mod, root, minecraft_dir)
            baseline, current = self.process_snapshots(minecraft_dir)
            snapshots = iter([baseline, baseline, current])
            terminated = []
            mod.snapshot_processes = lambda: next(snapshots)
            mod.terminate_processes = lambda pids: terminated.append(pids)

            with self.assertRaisesRegex(RuntimeError, "current-run log failure"):
                mod.run_session(
                    scenario_name="terminal-left-rail",
                    minecraft_dir=minecraft_dir,
                    instance_dir=root / "instances" / "dev",
                    run_root=root / "gui-runs",
                    prepare_world_func=self.fake_prepare,
                    configure_instance_func=lambda instance_dir: True,
                    launcher=lambda command: None,
                    wait_for_log_func=lambda **kwargs: (_ for _ in ()).throw(RuntimeError("current-run log failure")),
                    timestamp_func=lambda: "20260712-030406",
                )

            self.assertEqual([[201, 200]], terminated)

    def test_visual_success_preserves_new_runner_processes_for_manual_handoff(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            minecraft_dir = root / "minecraft"
            (minecraft_dir / "logs").mkdir(parents=True)
            self.configure_matching_deployment(mod, root, minecraft_dir)
            baseline, current = self.process_snapshots(minecraft_dir)
            snapshots = iter([baseline, current])
            snapshot_calls = []
            terminated = []
            watchdogs = []

            def snapshot():
                snapshot_calls.append(True)
                return next(snapshots)

            mod.snapshot_processes = snapshot
            mod.terminate_processes = lambda pids: terminated.append(pids)

            result = mod.run_session(
                scenario_name="terminal-left-rail",
                minecraft_dir=minecraft_dir,
                instance_dir=root / "instances" / "dev",
                run_root=root / "gui-runs",
                prepare_world_func=self.fake_prepare,
                cleanup_existing_func=lambda *args: None,
                configure_instance_func=lambda instance_dir: True,
                launcher=lambda command: None,
                wait_for_log_func=lambda **kwargs: "SelfTest: 104 passed\nMS_GUI_TEST_READY\n",
                display_mode_verifier=lambda manifest: None,
                watchdog_launcher=lambda expected, log_path, cursor, run_dir: watchdogs.append(
                    (expected, log_path, cursor, run_dir)
                ),
                timestamp_func=lambda: "20260712-030407",
            )

            self.assertIsNotNone(result.manual_handoff_message)
            self.assertEqual(2, len(snapshot_calls))
            self.assertEqual([], terminated)
            self.assertEqual({201: current[201][1]}, watchdogs[0][0])
            self.assertEqual(result.run_dir, watchdogs[0][3])

    def test_verify_desktop_display_mode_rejects_resolution_change(self):
        mod = self.load_script()
        manifest = self.fake_prepare(Path("/tmp/minecraft"), "New World", "MagicStorageGuiTest")

        with self.assertRaisesRegex(RuntimeError, "changed the macOS desktop display mode"):
            mod.verify_desktop_display_mode(
                manifest,
                mode_func=lambda: mod.DisplayMode(1920, 1200, 1920, 1200, 60, 24),
            )

    def test_bus_configuration_scenario_generates_focused_fullscreen_checklist(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            minecraft_dir = root / "minecraft"
            (minecraft_dir / "logs").mkdir(parents=True)
            result = mod.run_session(
                scenario_name="bus-configuration",
                minecraft_dir=minecraft_dir,
                instance_dir=root / "instances" / "dev",
                run_root=root / "gui-runs",
                prepare_world_func=self.fake_prepare,
                configure_instance_func=lambda instance_dir: True,
                no_launch=True,
                timestamp_func=lambda: "20260722-010203",
            )

            self.assertTrue(result.manual_gui_required)
            checklist = (result.run_dir / "checklist.md").read_text()
            for expected in [
                "fullscreen gate",
                "hotbar `5`",
                "hotbar `6`",
                "Import Bus",
                "Export Bus",
                "Front",
                "Pipes",
                "Unsided access On",
                "Automation",
                "Allow",
                "Deny",
                "six side buttons",
                "Filters",
                "directionless block model",
                "current run",
            ]:
                self.assertIn(expected, checklist)

    def test_crafting_fuel_page_scenario_generates_focused_fullscreen_checklist(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            minecraft_dir = root / "minecraft"
            (minecraft_dir / "logs").mkdir(parents=True)
            result = mod.run_session(
                scenario_name="crafting-fuel-page",
                minecraft_dir=minecraft_dir,
                instance_dir=root / "instances" / "dev",
                run_root=root / "gui-runs",
                prepare_world_func=self.fake_prepare,
                configure_instance_func=lambda instance_dir: True,
                no_launch=True,
                timestamp_func=lambda: "20260712-010203",
            )

            self.assertTrue(result.manual_gui_required)
            checklist = (result.run_dir / "checklist.md").read_text()
            self.assertIn("preloaded Core record", checklist)
            self.assertIn("one Coal visual input in hotbar `3`", checklist)
            self.assertNotIn("legacy iron axe converted", checklist)
            for expected in [
                "fullscreen gate",
                "true-void",
                "hotbar `1`",
                "hotbar `2`",
                "hotbar `3`",
                "already aimed at the Crafting Terminal",
                "Storage tab",
                "Craftable tab",
                "Transform",
                "hidden single-page pager leaves no blank row",
                "Stations",
                "page tabs",
                "visual gap",
                "Auto",
                "Oak Logs",
                "Charcoal",
                "zero stored",
                "EMI",
                "visible outer margins",
                "frame and left rail are centered as one group",
                "Processing Stations",
                "Instant Stations",
                "exactly two top-aligned groups",
                "installed stack count overlays its item",
                "near-Integer.MAX_VALUE Processing aggregate",
                "accumulated station work",
                "Mana uses Mana Powder and the name Mana",
                "Fluids use a teal frame",
                "Energy uses a violet frame",
                "Other contains Axe Uses",
                "distinct amber frame",
                "dense icon grid",
                "panel-local wheel paging",
                "independent information box immediately to the right of the player inventory",
                "middle-click",
                "vanilla-style ×1/×8/×64/Max buttons",
                "complete wrapped prompt",
                "no status light",
                "Crafting Table",
                "Stonecutter",
                "Smithing Table",
                "Axe Uses",
                "Smithing Transform",
                "strip",
                "Creative Storage Unit",
                "localized unlimited type capacity",
                "Creative Storage Unit icon",
                "never changes on hover",
                "white focus border",
                "Available / Required",
                "Oak Log",
                "Smelting Energy",
                "same visual size",
                "no permanent rate formula",
                "exact item's normal tooltip",
                "current aggregate increase rate",
                "does not repeat installed/stored totals",
                "large item counts",
                "inside their own slot",
                "stored types / total type capacity",
                "persistent target list",
                "visible previous/next buttons",
                "dim representative station item",
                "stored types / total type capacity",
                "light vanilla container panels",
                "uniformly scale the complete widget",
                "top-aligns its resources",
                "at most four columns",
                "Short amounts stay on one line",
                "empty top search field",
                "selected row",
                "page indicator",
                "whole horizontal card",
                "Coal remains visibly present in the upper input slot",
                "strip in the upper input area",
                "Direct Fuel cards use only the produced resource/amount line",
                "timed cards add the required logical station family/work",
                "no detached lower selection strip",
                "Processing",
                "derive row count from available height",
                "does not resize or redistribute incomplete rows",
                "logical Furnator family",
                "four page content bottoms",
                "one result grid",
                "stations only",
                "right-click",
                "×8",
                "×64",
                "Max",
                "each bare operator",
                "behave exactly like an empty query",
                "release search focus without clearing",
                "@minecraft",
                "#minecraft:logs",
                "Auto Focus",
                "initially receives keyboard input",
                "Search Sync",
                "Off",
                "EMI Two-way",
                "Iron Furnace",
                "installed variant first",
                "exactly once per real-time second",
                "complete station-icon cycle",
                "Cooking Pot",
                "Mushroom Stew",
                "brown mushroom",
                "red mushroom",
                "bowl",
                "third-party recipe",
                "full-size available and required lines",
                "121K",
                "/200",
                "TMRV",
                "Iron Furnaces owns",
                "Magic Storage does not register",
                "Mekanism",
                "Gases appears because",
                "Other contains Axe Uses",
                "Botania Mana",
                "Ars Nouveau Source",
                "preinstalled",
                "preloaded",
                "no setup action",
                "Honey Bottle",
                "250 mB Honey",
                "Energized Steel",
                "10,000 FE",
                "Manasteel Ingot",
                "3,000 Mana",
                "Hydrogen Chloride",
                "Hydrogen",
                "Chlorine",
                "Ultimate Crafting Table",
                "Ultimate Singularity",
                "19 exact singularity variants",
                "complete 9×9 Ultimate Crafting widget",
                "scroll through the complete material ledger",
                "warm Craftable return should be immediate",
                "must not produce a new keep-up warning",
                "MacFix",
                "windowWillReturnFieldEditor",
                "F11 → bordered window → Command-Q",
                "@creat",
                "Show: All",
                "starts Off",
                "Transform and Stations expose Sort Method and Sort Order",
                "never a Chemical Tank or Brewing Stand",
                "64,000 capacity",
                "scrollbar must show a visible recessed track/frame",
                "Aluminum Blade",
                "Macerator recipe",
                "non-item output",
                "locked to Storage",
            ]:
                self.assertIn(expected, checklist)
            for forbidden in [
                "install the supplied",
                "add Coal",
                "wait until Smelting Energy",
                "Use hotbar `9`",
                "Repeat from hotbar",
                "Remove the Furnace",
                "Cycle Search Sync through Auto",
                "hotbar `3` → `unknown`",
            ]:
                self.assertNotIn(forbidden, checklist)
            self.assertNotIn("Cooking Energy", checklist)
            self.assertNotIn("Installed Machines", checklist)
            self.assertNotIn("reinstalled axe", checklist)
            self.assertNotIn("lowers its raw durability", checklist)
            self.assertNotIn("Compact Grid", checklist)
            self.assertNotIn("different fill levels", checklist)
            self.assertNotIn("all five", checklist)
            self.assertNotIn("all eight", checklist)
            self.assertNotIn("- hotbar `7`", checklist)
            self.assertNotIn("- hotbar `8`", checklist)
            self.assertNotIn("- hotbar `9`", checklist)

    def test_crafting_fuel_page_support_mod_preflight_requires_exact_staged_jars(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            project = root / "project"
            minecraft = root / "minecraft"
            staged = project / "build" / "prism-gui-mods"
            mods = minecraft / "mods"
            staged.mkdir(parents=True)
            mods.mkdir(parents=True)
            self.assertEqual(18, len(mod.SUPPORT_ARTIFACTS))
            self.assertIn(
                "macfix-gui-test.jar",
                {filename for filename, _ in mod.SUPPORT_ARTIFACTS},
            )
            self.assertIn(
                "extended-crafting-gui-test.jar",
                {filename for filename, _ in mod.SUPPORT_ARTIFACTS},
            )
            self.assertIn(
                "cucumber-gui-test.jar",
                {filename for filename, _ in mod.SUPPORT_ARTIFACTS},
            )
            self.assertFalse(any(
                "pneumatic" in filename for filename, _ in mod.SUPPORT_ARTIFACTS
            ))
            self.assertFalse(any(
                "evilcraft" in filename or "cyclops" in filename
                for filename, _ in mod.SUPPORT_ARTIFACTS
            ))
            contents = {}
            for index, (filename, _) in enumerate(mod.SUPPORT_ARTIFACTS):
                content = f"support-{index}".encode()
                contents[filename] = content
                (staged / filename).write_bytes(content)
                (mods / filename).write_bytes(content)

            mod.verify_deployed_gui_support_jars(project, minecraft)

            (mods / "botania-gui-test.jar").unlink()
            with self.assertRaisesRegex(
                    RuntimeError, "Expected exactly one Botania GUI support jar"):
                mod.verify_deployed_gui_support_jars(project, minecraft)
            (mods / "botania-gui-test.jar").write_bytes(
                contents["botania-gui-test.jar"])

            (mods / mod.IRON_FURNACES_FILENAME).write_bytes(b"stale")
            with self.assertRaisesRegex(RuntimeError, "Iron Furnaces GUI support jar contents differ"):
                mod.verify_deployed_gui_support_jars(project, minecraft)

            (mods / mod.IRON_FURNACES_FILENAME).unlink()
            with self.assertRaisesRegex(RuntimeError, "Expected exactly one Iron Furnaces GUI support jar"):
                mod.verify_deployed_gui_support_jars(project, minecraft)

            (mods / mod.IRON_FURNACES_FILENAME).write_bytes(
                contents[mod.IRON_FURNACES_FILENAME])
            (mods / "jei-incompatible.jar").write_bytes(b"jei")
            with self.assertRaisesRegex(RuntimeError, "JEI is incompatible with TMRV"):
                mod.verify_deployed_gui_support_jars(project, minecraft)

    def test_configure_instance_for_manual_handoff_disables_wrapper_and_error_console(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            instance_dir = Path(tmp) / "dev"
            instance_dir.mkdir()
            cfg = instance_dir / "instance.cfg"
            cfg.write_text(
                "InstanceType=OneSix\n"
                "OverrideCommands=true\n"
                "OverrideConsole=false\n"
                "ShowConsole=true\n"
                "ShowConsoleOnError=true\n"
                "AutoCloseConsole=false\n"
                "LowMemWarning=true\n"
                "JavaPath=/tmp/java\n"
                "WrapperCommand=/tmp/magic_storage_minecraft_cu_wrapper.sh\n"
            )

            changed = mod.configure_instance_for_manual_handoff(instance_dir)

            self.assertTrue(changed)
            self.assertEqual(
                [
                    "InstanceType=OneSix",
                    "OverrideCommands=true",
                    "OverrideConsole=true",
                    "ShowConsole=false",
                    "ShowConsoleOnError=false",
                    "AutoCloseConsole=false",
                    "LowMemWarning=false",
                    "JavaPath=/tmp/java",
                    "WrapperCommand=",
                ],
                cfg.read_text().splitlines(),
            )

    def test_launch_command_uses_existing_prism_root_instance_and_offline_player(self):
        mod = self.load_script()
        self.assertEqual(
            [
                "/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher",
                "-l",
                "dev",
                "-w",
                "MagicStorageGuiTest",
                "-o",
                "MagicStorageBot",
            ],
            mod.build_launch_command(
                "/Applications/Prism Launcher.app",
                "dev",
                "MagicStorageGuiTest",
            ),
        )

    def test_gui_runner_requires_an_already_running_normal_root_prism(self):
        mod = self.load_script(stub_running_prism=False)
        normal = {
            100: (1, "/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher"),
        }
        isolated = {
            200: (
                1,
                "/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher -d /tmp/prism-root",
            ),
        }

        mod.require_running_normal_prism(snapshot_func=lambda: normal)
        with self.assertRaisesRegex(RuntimeError, "Open the normal Prism Launcher once"):
            mod.require_running_normal_prism(snapshot_func=lambda: {})
        with self.assertRaisesRegex(RuntimeError, "Open the normal Prism Launcher once"):
            mod.require_running_normal_prism(snapshot_func=lambda: isolated)

    def test_default_launcher_detaches_with_sanitized_environment(self):
        mod = self.load_script()
        calls = []

        mod.default_launcher(
            ["/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher", "-l", "dev"],
            popen_func=lambda command, **kwargs: calls.append((command, kwargs)),
            environ={
                "HOME": "/Users/test",
                "PATH": "/usr/bin:/bin",
                "TMPDIR": "/tmp/",
                "LANG": "en_US.UTF-8",
                "SECRET_API_KEY": "must-not-reach-prism",
            },
        )

        self.assertEqual(1, len(calls))
        command, kwargs = calls[0]
        self.assertEqual(
            ["/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher", "-l", "dev"],
            command,
        )
        self.assertEqual(
            {
                "HOME": "/Users/test",
                "PATH": "/usr/bin:/bin",
                "TMPDIR": "/tmp/",
                "LANG": "en_US.UTF-8",
            },
            kwargs["env"],
        )
        self.assertIs(subprocess.DEVNULL, kwargs["stdin"])
        self.assertIs(subprocess.DEVNULL, kwargs["stdout"])
        self.assertIs(subprocess.DEVNULL, kwargs["stderr"])
        self.assertTrue(kwargs["start_new_session"])
        self.assertTrue(kwargs["close_fds"])

    def test_prism_launcher_artifact_removes_process_environment(self):
        mod = self.load_script()
        raw = (
            'Starting Prism Launcher\n'
            'Process environment:\n'
            'QList("HOME=/Users/test", "SECRET_API_KEY=must-not-persist")\n'
            'Launching with world "MagicStorageGuiTest"\n'
            'Native environment:\n'
            'QList("PATH=/usr/bin", "TOKEN=must-not-persist")\n'
            'Instance started\n'
        )

        sanitized = mod.sanitize_prism_launcher_log(raw)

        self.assertEqual(
            'Starting Prism Launcher\n'
            'Launching with world "MagicStorageGuiTest"\n'
            'Instance started\n',
            sanitized,
        )
        self.assertNotIn("must-not-persist", sanitized)

    def test_prism_launcher_log_rejects_account_auth_but_allows_offline_flow(self):
        mod = self.load_script(stub_prism_root=False)
        accepted = (
            'Loading accounts...\n'
            'Task "AuthFlow(0x1)" starting for the first time\n'
            'Task "AuthFlow(0x1)" succeeded\n'
            'RefreshSchedule: Background account refresh succeeded\n'
            'RefreshSchedule: Processing account "MagicStorageBot"\n'
            'Launching with account "MagicStorageBot"\n'
        )
        mod.verify_no_prism_auth_refresh(accepted)

        rejected = [
            'AuthFlow: "Logging in with Microsoft account."',
            'Running "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"',
            'Running "https://user.auth.xboxlive.com/user/authenticate"',
            'Running "https://xsts.auth.xboxlive.com/xsts/authorize"',
            'Running "https://api.minecraftservices.com/launcher/login"',
            'Running "https://api.minecraftservices.com/entitlements/license"',
        ]
        for text in rejected:
            with self.subTest(text=text):
                with self.assertRaisesRegex(RuntimeError, "authentication activity"):
                    mod.verify_no_prism_auth_refresh(text)

    def test_run_session_uses_existing_prism_root_and_verifies_only_current_launcher_log(self):
        mod = self.load_script(stub_prism_root=False)
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            minecraft_dir = root / "instances" / "dev" / "minecraft"
            (minecraft_dir / "logs").mkdir(parents=True)
            prism_log = root / "logs" / "PrismLauncher-0.log"
            prism_log.parent.mkdir()
            prism_log.write_text("old launcher log\n")
            self.configure_matching_deployment(mod, root, minecraft_dir)
            mod.snapshot_processes = lambda: {}
            events = []
            launched = []

            def wait_for_log(**kwargs):
                prism_log.write_text(prism_log.read_text() + "current offline launcher log\n")
                return "SelfTest: 104 passed\nMS_GUI_TEST_READY\n"

            result = mod.run_session(
                scenario_name="terminal-left-rail",
                minecraft_dir=minecraft_dir,
                instance_dir=root / "instances" / "dev",
                run_root=root / "gui-runs",
                prepare_world_func=self.fake_prepare,
                cleanup_existing_func=lambda *args: None,
                configure_instance_func=lambda instance_dir: True,
                auth_verifier=lambda text: events.append(("verify-auth", text)),
                launcher=lambda command: launched.append(command),
                wait_for_log_func=wait_for_log,
                display_mode_verifier=lambda manifest: None,
                timestamp_func=lambda: "20260722-010203",
            )

            self.assertEqual([("verify-auth", "current offline launcher log\n")], events)
            self.assertNotIn("-d", launched[0])
            self.assertFalse((result.run_dir / "prism-root").exists())
            self.assertEqual(
                "current offline launcher log\n",
                (result.run_dir / "prism-launcher.log").read_text(),
            )

    def test_prism_11_0_3_is_minimum_for_offline_cli_launch(self):
        mod = self.load_script(stub_prism_version=False)

        class Result:
            def __init__(self, stdout: str):
                self.stdout = stdout

        calls = []

        def run_with(version: str):
            def run(command, **kwargs):
                calls.append((command, kwargs))
                return Result(f"PrismLauncher {version}\n")

            return run

        with tempfile.TemporaryDirectory() as tmp:
            prism_app = Path(tmp) / "Prism Launcher.app"
            binary = prism_app / "Contents" / "MacOS" / "prismlauncher"
            binary.parent.mkdir(parents=True)
            binary.touch()

            with self.assertRaisesRegex(RuntimeError, r"11\.0\.3 or newer.*11\.0\.2"):
                mod.verify_prism_version(str(prism_app), run_func=run_with("11.0.2"))

            self.assertEqual(
                "11.0.3",
                mod.verify_prism_version(str(prism_app), run_func=run_with("11.0.3")),
            )
            self.assertEqual(
                "12.0.0",
                mod.verify_prism_version(str(prism_app), run_func=run_with("12.0.0")),
            )
            self.assertEqual([str(binary.resolve()), "--version"], calls[0][0])

    def test_run_session_rejects_old_prism_before_cleanup(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            minecraft_dir = root / "minecraft"
            (minecraft_dir / "logs").mkdir(parents=True)
            self.configure_matching_deployment(mod, root, minecraft_dir)
            events = []
            mod.verify_prism_version = lambda prism_app: (_ for _ in ()).throw(
                RuntimeError("Prism Launcher 11.0.3 or newer is required; found 11.0.2")
            )

            with self.assertRaisesRegex(RuntimeError, r"11\.0\.3 or newer"):
                mod.run_session(
                    scenario_name="boot-smoke",
                    minecraft_dir=minecraft_dir,
                    instance_dir=root / "instances" / "dev",
                    run_root=root / "gui-runs",
                    prepare_world_func=self.fake_prepare,
                    cleanup_existing_func=lambda *args: events.append("cleanup"),
                    configure_instance_func=lambda instance_dir: events.append("configure") or True,
                    launcher=lambda command: events.append("launch"),
                    wait_for_log_func=lambda **kwargs: "SelfTest: 104 passed\nMS_GUI_TEST_READY\n",
                    timestamp_func=lambda: "20260722-010203",
                )

            self.assertEqual([], events)

    def test_snapshot_processes_parses_pid_parent_and_full_command(self):
        mod = self.load_script()

        class Result:
            stdout = (
                "  100     1 /Applications/Prism Launcher.app/Contents/MacOS/prismlauncher -l dev\n"
                "  201   100 /usr/bin/java -Duser.dir=/tmp/minecraft net.minecraft.client.main.Main\n"
            )

        processes = mod.snapshot_processes(run_func=lambda *args, **kwargs: Result())

        self.assertEqual(
            {
                100: (1, "/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher -l dev"),
                201: (100, "/usr/bin/java -Duser.dir=/tmp/minecraft net.minecraft.client.main.Main"),
            },
            processes,
        )

    def test_runner_started_process_ids_include_new_prism_parent_without_launch_args(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            instance_dir = root / "instances" / "dev"
            minecraft_dir = instance_dir / "minecraft"
            baseline = {100: (1, "/usr/bin/python3 existing.py")}
            current = {
                **baseline,
                200: (1, "/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher"),
                201: (200, f"/usr/bin/java -Duser.dir={minecraft_dir} org.prismlauncher.EntryPoint"),
            }

            self.assertEqual(
                [201, 200],
                mod.runner_started_process_ids(
                    baseline,
                    current,
                    instance_dir,
                    minecraft_dir,
                    "dev",
                    "MagicStorageGuiTest",
                ),
            )

    def test_cleanup_existing_session_terminates_only_matching_dev_tree(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            instance_dir = root / "instances" / "dev"
            minecraft_dir = instance_dir / "minecraft"
            current = {
                200: (1, "/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher -l dev -w MagicStorageGuiTest -o MagicStorageBot"),
                201: (200, f"/usr/bin/java -Duser.dir={minecraft_dir} org.prismlauncher.EntryPoint"),
                300: (1, "/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher -l other -w OtherWorld"),
                301: (300, "/usr/bin/java -Duser.dir=/tmp/other-instance org.prismlauncher.EntryPoint"),
            }
            terminated = []

            mod.cleanup_existing_session(
                instance_dir,
                minecraft_dir,
                "dev",
                "MagicStorageGuiTest",
                snapshot_func=lambda: current,
                terminate_func=lambda pids: terminated.append(pids),
            )

            self.assertEqual([[201]], terminated)

    def test_cleanup_existing_session_preserves_warm_prism_without_test_java(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            instance_dir = root / "instances" / "dev"
            minecraft_dir = instance_dir / "minecraft"
            current = {
                200: (
                    1,
                    "/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher -l dev -w MagicStorageGuiTest -o MagicStorageBot",
                ),
            }
            terminated = []

            mod.cleanup_existing_session(
                instance_dir,
                minecraft_dir,
                "dev",
                "MagicStorageGuiTest",
                snapshot_func=lambda: current,
                terminate_func=lambda pids: terminated.append(pids),
            )

            self.assertEqual([], terminated)

    def test_supervise_shutdown_forces_only_same_java_after_stopping_timeout(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            log = root / "latest.log"
            log.write_text("")
            cursor = mod.log_cursor(log)
            expected_command = "/usr/bin/java -Duser.dir=/tmp/dev/minecraft org.prismlauncher.EntryPoint"
            current = {
                201: (200, expected_command),
                301: (300, "/usr/bin/java -Duser.dir=/tmp/other/minecraft org.prismlauncher.EntryPoint"),
            }
            times = iter([10.0, 10.0, 16.0])
            terminated = []

            log.write_text("[14Jul2026 22:45:45.407] [Render thread/INFO] [net.minecraft.client.Minecraft/]: Stopping!\n")
            result = mod.supervise_shutdown(
                {201: expected_command},
                log,
                cursor,
                root,
                stall_timeout_seconds=5,
                poll_seconds=0,
                snapshot_func=lambda: current,
                terminate_func=lambda pids: terminated.append(pids),
                monotonic_func=lambda: next(times),
                sleep_func=lambda seconds: None,
            )

            self.assertEqual([[201]], terminated)
            self.assertEqual("forced_after_glfw_shutdown_stall", result["status"])
            self.assertEqual([201], result["forced_pids"])
            self.assertEqual(result, json.loads((root / "shutdown.json").read_text()))

    def test_supervise_shutdown_records_graceful_exit_without_terminating(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            log = root / "latest.log"
            log.write_text("")
            cursor = mod.log_cursor(log)
            expected_command = "/usr/bin/java -Duser.dir=/tmp/dev/minecraft org.prismlauncher.EntryPoint"
            snapshots = iter([
                {201: (200, expected_command)},
                {},
            ])
            terminated = []

            result = mod.supervise_shutdown(
                {201: expected_command},
                log,
                cursor,
                root,
                stall_timeout_seconds=5,
                poll_seconds=0,
                snapshot_func=lambda: next(snapshots),
                terminate_func=lambda pids: terminated.append(pids),
                monotonic_func=lambda: 10.0,
                sleep_func=lambda seconds: None,
            )

            self.assertEqual([], terminated)
            self.assertEqual("graceful", result["status"])
            self.assertEqual([], result["forced_pids"])

    def test_start_shutdown_watchdog_writes_exact_process_config_and_detaches(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            log = root / "latest.log"
            log.write_text("ready\n")
            cursor = mod.log_cursor(log)
            expected = {201: "/usr/bin/java -Duser.dir=/tmp/dev/minecraft net.minecraft.client.main.Main"}
            launches = []

            mod.start_shutdown_watchdog(
                expected,
                log,
                cursor,
                root,
                popen_func=lambda command, **kwargs: launches.append((command, kwargs)),
            )

            config_path = root / "shutdown-watchdog.json"
            config = json.loads(config_path.read_text())
            self.assertEqual({"201": expected[201]}, config["expected_processes"])
            self.assertEqual(str(log), config["log_path"])
            self.assertEqual(
                {"size": cursor.size, "device": cursor.device, "inode": cursor.inode},
                config["cursor"],
            )
            self.assertEqual(5, config["stall_timeout_seconds"])
            command, kwargs = launches[0]
            self.assertEqual(
                [sys.executable, str(Path(mod.__file__).resolve()), "--watchdog-config", str(config_path)],
                command,
            )
            self.assertTrue(kwargs["start_new_session"])
            self.assertTrue(kwargs["close_fds"])
            self.assertEqual(subprocess.DEVNULL, kwargs["stdin"])
            self.assertEqual(subprocess.STDOUT, kwargs["stderr"])

    def test_terminate_processes_sends_term_to_each_live_owned_pid(self):
        mod = self.load_script()
        alive = {201, 200}
        signals = []

        def kill(pid, process_signal):
            signals.append((pid, process_signal))
            alive.discard(pid)

        mod.terminate_processes(
            [201, 200],
            timeout_seconds=0,
            kill_func=kill,
            process_alive_func=lambda pid: pid in alive,
            sleep_func=lambda seconds: None,
        )

        self.assertEqual([(201, signal.SIGTERM), (200, signal.SIGTERM)], signals)

    def test_log_wait_ignores_stale_errors_before_cursor_and_fails_on_current_errors(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            log = Path(tmp) / "latest.log"
            log.write_text("old ERROR before this run\nMS_GUI_TEST_READY\n")
            cursor = mod.log_cursor(log)
            log.write_text(log.read_text() + "SelfTest: 104 passed\nMS_GUI_TEST_READY\n")
            text = mod.wait_for_log_patterns(
                log_path=log,
                offset=cursor,
                required_patterns=["SelfTest:", "MS_GUI_TEST_READY"],
                forbidden_patterns=["ERROR", "FATAL", "advanced_container_set_data"],
                timeout_seconds=0,
                poll_seconds=0,
                sleep_func=lambda seconds: None,
            )
            self.assertIn("SelfTest", text)

            cursor = mod.log_cursor(log)
            log.write_text(log.read_text() + "ERROR current failure\n")
            with self.assertRaisesRegex(RuntimeError, "forbidden log pattern"):
                mod.wait_for_log_patterns(
                    log_path=log,
                    offset=cursor,
                    required_patterns=["SelfTest:"],
                    forbidden_patterns=["ERROR"],
                    timeout_seconds=0,
                    poll_seconds=0,
                    sleep_func=lambda seconds: None,
                )

    def test_log_wait_reports_locked_or_sleeping_display_for_primary_monitor_failure(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            log = Path(tmp) / "latest.log"
            log.write_text("")
            cursor = mod.log_cursor(log)
            log.write_text(
                "[12Jul2026 12:34:47.026] [main/ERROR] [EARLYDISPLAY/]: Failed to find a primary monitor\n"
                "Failed to locate a primary monitor.\n"
                "glfwGetPrimaryMonitor failed.\n"
            )

            with self.assertRaisesRegex(RuntimeError, "wake and unlock the macOS display"):
                mod.wait_for_log_patterns(
                    log_path=log,
                    offset=cursor,
                    required_patterns=["SelfTest:", "MS_GUI_TEST_READY"],
                    forbidden_patterns=["ERROR", "FATAL"],
                    timeout_seconds=0,
                    poll_seconds=0,
                    sleep_func=lambda seconds: None,
                )

    def test_log_wait_allows_only_known_offline_auth_property_fetch_noise(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            log = Path(tmp) / "latest.log"
            log.write_text("")
            cursor = mod.log_cursor(log)
            log.write_text(
                "[11Jul2026 00:54:34.535] [Download-2/ERROR] [net.minecraft.client.Minecraft/]: Failed to fetch user properties\n"
                "com.mojang.authlib.exceptions.InvalidCredentialsException: Status: 401\n"
                "\tat authlib.fetchProperties(YggdrasilUserApiService.java:150)\n"
                "Caused by: com.mojang.authlib.exceptions.MinecraftClientHttpException: Status: 401\n"
                "\tat authlib.readInputStream(MinecraftClient.java:100)\n"
                "[11Jul2026 00:54:38.495] [Server thread/INFO] [net.minecraft.server.MinecraftServer/]: [MagicStorageBot] MS_GUI_TEST_READY\n"
                "[11Jul2026 00:54:34.271] [modloading-worker-0/INFO] [com.swearprom.magicstorage.magic_storage.MagicStorage/]: SelfTest: 104 passed, 0 failed, 104 total\n"
            )
            text = mod.wait_for_log_patterns(
                log_path=log,
                offset=cursor,
                required_patterns=["SelfTest:", "MS_GUI_TEST_READY"],
                forbidden_patterns=["ERROR", "Caused by"],
                timeout_seconds=0,
                poll_seconds=0,
                sleep_func=lambda seconds: None,
            )
            self.assertIn("Failed to fetch user properties", text)

            cursor = mod.log_cursor(log)
            log.write_text(log.read_text() + "[11Jul2026 00:54:39.000] [Render thread/ERROR] [other/]: real failure\n")
            with self.assertRaisesRegex(RuntimeError, "forbidden log pattern"):
                mod.wait_for_log_patterns(
                    log_path=log,
                    offset=cursor,
                    required_patterns=["SelfTest:"],
                    forbidden_patterns=["ERROR"],
                    timeout_seconds=0,
                    poll_seconds=0,
                    sleep_func=lambda seconds: None,
                )

    def test_log_wait_allows_only_botania_snapshot_readme_pack_noise(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            log = Path(tmp) / "latest.log"
            log.write_text("")
            cursor = mod.log_cursor(log)
            log.write_text(
                "[24Jul2026 21:11:27.837] [Worker-Main-7/ERROR] [net.minecraft.Util/]: "
                "Invalid path in pack: botania:patchouli_books/lexica_botania/en_us/README, ignoring\n"
                "[24Jul2026 21:11:28.356] [Render thread/INFO] "
                "[com.swearprom.magicstorage.magic_storage.MagicStorage/]: "
                "SelfTest: 104 passed, 0 failed, 104 total\n"
                "[24Jul2026 21:11:29.000] [Server thread/INFO] "
                "[net.minecraft.server.MinecraftServer/]: [MagicStorageBot] MS_GUI_TEST_READY\n"
            )

            mod.wait_for_log_patterns(
                log_path=log,
                offset=cursor,
                required_patterns=["SelfTest:", "MS_GUI_TEST_READY"],
                forbidden_patterns=["ERROR"],
                timeout_seconds=0,
                poll_seconds=0,
                sleep_func=lambda seconds: None,
            )

            cursor = mod.log_cursor(log)
            log.write_text(
                log.read_text()
                + "[24Jul2026 21:11:30.000] [Worker-Main-7/ERROR] [net.minecraft.Util/]: "
                "Invalid path in pack: botania:other_invalid_path, ignoring\n"
            )
            with self.assertRaisesRegex(RuntimeError, "forbidden log pattern"):
                mod.wait_for_log_patterns(
                    log_path=log,
                    offset=cursor,
                    required_patterns=["SelfTest:"],
                    forbidden_patterns=["ERROR"],
                    timeout_seconds=0,
                    poll_seconds=0,
                    sleep_func=lambda seconds: None,
                )

    def test_log_wait_allows_only_industrial_foregoing_unregistered_curios_slots(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            log = Path(tmp) / "latest.log"
            log.write_text("")
            cursor = mod.log_cursor(log)
            log.write_text(
                "[24Jul2026 21:14:36.901] [Render thread/ERROR] [Curios API/]: "
                "example is not a registered slot type!\n"
                "[24Jul2026 21:14:36.901] [Render thread/ERROR] [Curios API/]: "
                "feet is not a registered slot type!\n"
                "SelfTest: 104 passed, 0 failed, 104 total\n"
                "MS_GUI_TEST_READY\n"
            )

            mod.wait_for_log_patterns(
                log_path=log,
                offset=cursor,
                required_patterns=["SelfTest:", "MS_GUI_TEST_READY"],
                forbidden_patterns=["ERROR"],
                timeout_seconds=0,
                poll_seconds=0,
                sleep_func=lambda seconds: None,
            )

            cursor = mod.log_cursor(log)
            log.write_text(
                log.read_text()
                + "[24Jul2026 21:14:37.000] [Render thread/ERROR] [Curios API/]: "
                "back is not a registered slot type!\n"
            )
            with self.assertRaisesRegex(RuntimeError, "forbidden log pattern"):
                mod.wait_for_log_patterns(
                    log_path=log,
                    offset=cursor,
                    required_patterns=["SelfTest:"],
                    forbidden_patterns=["ERROR"],
                    timeout_seconds=0,
                    poll_seconds=0,
                    sleep_func=lambda seconds: None,
                )

    def test_log_wait_reads_replaced_log_from_start_instead_of_stale_byte_offset(self):
        mod = self.load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            log = root / "latest.log"
            log.write_text("x" * 120)
            cursor = mod.log_cursor(log)
            replacement = root / "replacement.log"
            replacement.write_text(
                "ERROR current failure before stale offset\n"
                + "y" * 130
                + "\nSelfTest: 104 passed\nMS_GUI_TEST_READY\n"
            )
            replacement.replace(log)

            with self.assertRaisesRegex(RuntimeError, "forbidden log pattern"):
                mod.wait_for_log_patterns(
                    log_path=log,
                    offset=cursor,
                    required_patterns=["SelfTest:", "MS_GUI_TEST_READY"],
                    forbidden_patterns=["ERROR"],
                    timeout_seconds=0,
                    poll_seconds=0,
                    sleep_func=lambda seconds: None,
                )


if __name__ == "__main__":
    unittest.main()
