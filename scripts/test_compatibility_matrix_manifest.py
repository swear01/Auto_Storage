import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = Path(__file__).resolve().parent
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))


class CompatibilityMatrixManifestTests(unittest.TestCase):
    def test_generator_module_is_importable(self):
        from compatibility_matrix_manifest import (
            build_manifest,
            recipe_inventory_sha256,
            validate_descriptor_matrix,
            validate_manifest,
        )

        self.assertTrue(callable(build_manifest))
        self.assertTrue(callable(recipe_inventory_sha256))
        self.assertTrue(callable(validate_descriptor_matrix))
        self.assertTrue(callable(validate_manifest))

    def test_recipe_inventory_sha256_is_deterministic(self):
        from compatibility_matrix_manifest import recipe_inventory_sha256

        first = recipe_inventory_sha256(["minecraft:stone", "ae2:inscriber/logic"])
        second = recipe_inventory_sha256(["ae2:inscriber/logic", "minecraft:stone"])
        self.assertEqual(first, second)
        self.assertRegex(first, r"^[0-9a-f]{64}$")
        self.assertNotEqual(first, recipe_inventory_sha256(["minecraft:stone"]))

    def test_descriptor_matrix_rejects_omission_and_malformed_data(self):
        from compatibility_matrix_manifest import validate_descriptor_matrix

        with self.assertRaisesRegex(ValueError, "matrix"):
            validate_descriptor_matrix({"schema": 1, "id": "auto_storage:sample"})

        with self.assertRaisesRegex(ValueError, "mods"):
            validate_descriptor_matrix(
                {
                    "matrix": {
                        "descriptors": [],
                        "resourceKinds": [],
                        "acceptedRecipes": [],
                        "rejectedDescriptors": [],
                        "rejectedResourceKinds": [],
                        "recipeInventory": {
                            "namespaces": ["sample"],
                            "sha256": "0" * 64,
                        },
                    }
                }
            )

    def test_descriptor_matrix_mods_match_runtime_requirements(self):
        from compatibility_matrix_manifest import validate_descriptor_matrix

        descriptor = {
            "requires": ["sample", "required_api"],
            "matrix": {
                "mods": ["required_api", "sample"],
                "descriptors": [],
                "resourceKinds": [],
                "acceptedRecipes": [],
                "rejectedDescriptors": [],
                "rejectedResourceKinds": [],
                "recipeInventory": {
                    "namespaces": ["sample"],
                    "sha256": "0" * 64,
                },
            },
        }
        validate_descriptor_matrix(descriptor)
        descriptor["matrix"]["mods"] = ["sample", "stale_api"]
        with self.assertRaisesRegex(ValueError, "mods.*requires"):
            validate_descriptor_matrix(descriptor)

    def test_manifest_file_io_is_explicit_utf8(self):
        from compatibility_matrix_manifest import (
            load_companions,
            load_descriptors,
            validate_descriptor_matrix,
            write_manifest,
        )

        companions = {
            "schema": 1,
            "companions": [],
            "unclaimedRecipeInventory": {"sha256": "0" * 64},
        }
        descriptor = {
            "schema": 1,
            "id": "auto_storage:sample",
            "requires": ["sample"],
            "matrix": {
                "mods": ["sample"],
                "descriptors": [],
                "resourceKinds": [],
                "acceptedRecipes": [],
                "rejectedDescriptors": [],
                "rejectedResourceKinds": [],
                "recipeInventory": {
                    "namespaces": ["sample"],
                    "sha256": "1" * 64,
                },
            },
        }
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            compat = root / "src/compat/sample"
            compat.mkdir(parents=True)
            descriptor_path = compat / "compat-module.json"
            descriptor_path.write_text(json.dumps(descriptor), encoding="utf-8")
            companions_path = (
                root
                / "src/compatibilityMatrixFixture/resources/META-INF/auto_storage/"
                "compatibility-matrix-companions.json"
            )
            companions_path.parent.mkdir(parents=True)
            companions_path.write_text(json.dumps(companions), encoding="utf-8")

            original_read_text = Path.read_text
            read_encodings = []

            def recording_read_text(path, *args, **kwargs):
                read_encodings.append(kwargs.get("encoding"))
                return original_read_text(path, *args, **kwargs)

            with mock.patch.object(Path, "read_text", recording_read_text):
                load_companions(companions_path)
                load_descriptors(root / "src/compat")
            self.assertTrue(read_encodings)
            self.assertEqual({"utf-8"}, set(read_encodings))

            original_write_text = Path.write_text
            write_encodings = []

            def recording_write_text(path, data, *args, **kwargs):
                write_encodings.append(kwargs.get("encoding"))
                return original_write_text(path, data, *args, **kwargs)

            output = root / "manifest.json"
            with mock.patch.object(Path, "write_text", recording_write_text):
                write_manifest(root, output)
            self.assertEqual(["utf-8"], write_encodings)

        with self.assertRaisesRegex(ValueError, "sha256"):
            validate_descriptor_matrix(
                {
                    "matrix": {
                        "mods": ["sample"],
                        "descriptors": ["auto_storage:sample_station"],
                        "resourceKinds": [],
                        "acceptedRecipes": [],
                        "rejectedDescriptors": [],
                        "rejectedResourceKinds": [],
                        "recipeInventory": {
                            "namespaces": ["sample"],
                            "sha256": "not-a-digest",
                        },
                    }
                }
            )

    def test_build_manifest_fails_closed_on_duplicate_module_or_namespace(self):
        from compatibility_matrix_manifest import build_manifest

        descriptor = {
            "schema": 1,
            "id": "auto_storage:sample",
            "matrix": {
                "mods": ["sample"],
                "descriptors": ["auto_storage:sample_station"],
                "resourceKinds": [],
                "acceptedRecipes": ["sample:one"],
                "rejectedDescriptors": [],
                "rejectedResourceKinds": [],
                "recipeInventory": {
                    "namespaces": ["sample"],
                    "sha256": "a" * 64,
                },
            },
        }
        companions = {
            "schema": 1,
            "companions": [],
            "unclaimedRecipeInventory": {"sha256": "b" * 64},
        }
        with self.assertRaisesRegex(ValueError, "Duplicate compatibility module"):
            build_manifest([descriptor, dict(descriptor)], companions)

        colliding = {
            "schema": 1,
            "id": "auto_storage:other",
            "matrix": {
                "mods": ["other"],
                "descriptors": [],
                "resourceKinds": [],
                "acceptedRecipes": [],
                "rejectedDescriptors": [],
                "rejectedResourceKinds": [],
                "recipeInventory": {
                    "namespaces": ["sample"],
                    "sha256": "c" * 64,
                },
            },
        }
        with self.assertRaisesRegex(ValueError, "Duplicate recipe namespace"):
            build_manifest([descriptor, colliding], companions)

    def test_validate_manifest_rejects_stale_or_tampered_generated_data(self):
        from compatibility_matrix_manifest import build_manifest, validate_manifest

        descriptor = {
            "schema": 1,
            "id": "auto_storage:sample",
            "matrix": {
                "mods": ["sample"],
                "descriptors": ["auto_storage:sample_station"],
                "resourceKinds": [],
                "acceptedRecipes": ["sample:one"],
                "rejectedDescriptors": [],
                "rejectedResourceKinds": [],
                "recipeInventory": {
                    "namespaces": ["sample"],
                    "sha256": "a" * 64,
                },
            },
        }
        companions = {
            "schema": 1,
            "companions": [
                {
                    "id": "reject_only",
                    "mods": ["reject_only"],
                    "descriptors": [],
                    "resourceKinds": [],
                    "acceptedRecipes": [],
                    "rejectedDescriptors": ["auto_storage:reject_family"],
                    "rejectedResourceKinds": ["reject_only:air"],
                    "recipeInventory": {
                        "namespaces": ["reject_only"],
                        "sha256": "d" * 64,
                    },
                }
            ],
            "unclaimedRecipeInventory": {"sha256": "b" * 64},
        }
        manifest = build_manifest([descriptor], companions)
        validate_manifest(manifest, [descriptor], companions)

        tampered = json.loads(json.dumps(manifest))
        tampered["modules"][0]["acceptedRecipes"] = ["sample:forged"]
        with self.assertRaisesRegex(ValueError, "stale|tamper|drift"):
            validate_manifest(tampered, [descriptor], companions)

        gate_injection = json.loads(json.dumps(manifest))
        gate_injection["maxSharedIndexBytes"] = 8 * 1024 * 1024
        with self.assertRaisesRegex(ValueError, "stale|tamper|drift|unexpected"):
            validate_manifest(gate_injection, [descriptor], companions)

    def test_complete_descriptor_requires_no_shared_ci_or_matrix_source_edit(self):
        ci = (ROOT / ".github/workflows/ci.yml").read_text()
        release = (ROOT / ".github/workflows/release.yml").read_text()
        matrix = (
            ROOT
            / "src/compatibilityMatrixFixture/java/com/swear/autostorage/fixture/"
            "compatibilitymatrix/CompatibilityMatrixGameTests.java"
        ).read_text()
        performance = (
            ROOT
            / "src/compatibilityMatrixFixture/java/com/swear/autostorage/fixture/"
            "compatibilitymatrix/CraftablePerformanceGameTests.java"
        ).read_text()
        build = (ROOT / "build.gradle").read_text()
        workflow_test = (ROOT / "scripts/test_github_workflows.py").read_text()

        for text in (ci, release):
            self.assertIn("runCompatFixtureGameTestServers", text)
            self.assertNotIn("runAe2GameTestServer", text)
            self.assertNotIn("runTheurgyGameTestServer", text)
            self.assertNotIn("runMekanismGameTestServer", text)
        self.assertIn("runCompatFixtureGameTestServers", build)
        self.assertIn("generateCompatibilityMatrixManifest", build)
        self.assertIn("compatibility-matrix-manifest.json", build)
        self.assertNotIn(
            "mustRunAfter compatFixtureRunTasks.last()",
            build,
            "lazy last() inside configure makes every fixture mustRunAfter the final module",
        )
        self.assertRegex(
            build,
            r"mustRunAfter\s+previousCompatFixture",
            "aggregate fixture ordering must capture the previous task eagerly",
        )
        self.assertNotRegex(
            build,
            r"doFirst\s*\{[^}]*\bdelete\b",
            "doFirst must not call Project.delete under configuration cache",
        )
        self.assertIn("compatGameTestWorldDir", build)
        self.assertIn("Files.walkFileTree", build)
        self.assertIn("verifyCompatGameTestWorldCleanupSafety", build)
        self.assertNotIn("compatGameTestWorldDir.deleteDir()", build)
        self.assertIn("Files.isSymbolicLink", build)
        self.assertIn("startsWith(compatVerificationRoot)", build)
        self.assertNotIn("EXPECTED_RECIPE_COUNT", performance)
        self.assertNotIn('"ae2"', matrix)
        self.assertNotIn('"theurgy"', matrix)
        self.assertNotIn("ae2_inscriber", matrix)
        self.assertIn("CompatibilityMatrixManifest", matrix)
        self.assertIn("CompatibilityMatrixManifest", performance)
        self.assertIn("runCompatFixtureGameTestServers", workflow_test)
        self.assertNotIn("Run AE2 GameTest server", workflow_test)
        self.assertNotIn("Run Theurgy GameTest server", workflow_test)
        notes = (ROOT / "docs/notes.md").read_text()
        recipe_family = (ROOT / "docs/recipe-family-api.md").read_text()
        readme = (ROOT / "README.md").read_text()
        self.assertIn("runCompatFixtureGameTestServers", notes)
        self.assertNotIn("GameTest tasks must be separate and sequential Gradle invocations", notes)
        self.assertIn("per-module", recipe_family)
        self.assertIn("recipe-inventory", recipe_family)
        self.assertIn("runCompatFixtureGameTestServers", readme)
        self.assertNotIn("./gradlew runAe2GameTestServer", readme)
        self.assertNotIn("locks and benchmarks 12,736 recipes", readme)

        coexistence_marker = (
            'manifest.assertCoexistence(helper, "Descriptor matrix coexistence")'
        )
        self.assertIn(coexistence_marker, matrix)
        for mod_id in ("ae2", "theurgy"):
            contract = json.loads(
                (ROOT / f"compat/contracts/{mod_id}.json").read_text()
            )
            evidence = contract["verification"]["evidence"][
                "all_mod_coexistence"
            ]
            self.assertEqual(coexistence_marker, evidence[0]["marker"])

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            compat = root / "src/compat/new_mod"
            compat.mkdir(parents=True)
            descriptor = {
                "schema": 1,
                "id": "auto_storage:new_mod",
                "entrypoint": "com.swear.autostorage.compat.newmod.NewModCompatModule",
                "requires": ["new_mod"],
                "side": "both",
                "sourceSet": "compatNewMod",
                "fixture": "newModFixture",
                "expectedTests": 1,
                "dependencies": ["maven.modrinth:new-mod:1"],
                "runtimeDependencies": ["maven.modrinth:new-mod:1"],
                "matrix": {
                    "mods": ["new_mod"],
                    "descriptors": ["auto_storage:new_mod_station"],
                    "resourceKinds": [],
                    "acceptedRecipes": ["new_mod:example"],
                    "rejectedDescriptors": [],
                    "rejectedResourceKinds": [],
                    "recipeInventory": {
                        "namespaces": ["new_mod"],
                        "sha256": "e" * 64,
                    },
                },
            }
            (compat / "compat-module.json").write_text(
                json.dumps(descriptor, indent=2) + "\n"
            )
            shared_before = {
                "ci": hashlib.sha256(ci.encode()).hexdigest(),
                "release": hashlib.sha256(release.encode()).hexdigest(),
                "matrix": hashlib.sha256(matrix.encode()).hexdigest(),
                "performance": hashlib.sha256(performance.encode()).hexdigest(),
                "workflow_test": hashlib.sha256(workflow_test.encode()).hexdigest(),
            }
            from compatibility_matrix_manifest import (
                build_manifest,
                build_manifest_from_roots,
                load_companions,
            )

            companions = load_companions(
                ROOT
                / "src/compatibilityMatrixFixture/resources/META-INF/auto_storage/"
                "compatibility-matrix-companions.json"
            )
            repo_descriptors = [
                json.loads(path.read_text())
                for path in sorted((ROOT / "src/compat").glob("*/compat-module.json"))
            ]
            with_new = repo_descriptors + [descriptor]
            before = build_manifest_from_roots(ROOT)
            after = build_manifest(with_new, companions)
            self.assertEqual(
                len(before["modules"]) + 1,
                len(after["modules"]),
            )
            self.assertEqual(shared_before["ci"], hashlib.sha256(ci.encode()).hexdigest())
            self.assertEqual(
                shared_before["release"],
                hashlib.sha256(release.encode()).hexdigest(),
            )
            self.assertEqual(
                shared_before["matrix"],
                hashlib.sha256(matrix.encode()).hexdigest(),
            )
            self.assertEqual(
                shared_before["performance"],
                hashlib.sha256(performance.encode()).hexdigest(),
            )
            self.assertEqual(
                shared_before["workflow_test"],
                hashlib.sha256(workflow_test.encode()).hexdigest(),
            )


if __name__ == "__main__":
    unittest.main()
