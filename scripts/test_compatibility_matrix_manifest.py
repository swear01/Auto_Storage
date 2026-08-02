import hashlib
import json
import re
import subprocess
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

    def test_global_recipe_digests_are_not_committed_baselines(self):
        from compatibility_matrix_manifest import (
            COMPATIBILITY_SUMMARY_RELATIVE_PATH,
            SHARED_AGGREGATE_PATHS,
            build_manifest,
            validate_companions,
            validate_descriptor_matrix,
        )

        self.assertEqual(
            COMPATIBILITY_SUMMARY_RELATIVE_PATH,
            "build/reports/compatibility-modules.md",
        )
        self.assertIn(
            "docs/generated/compatibility-modules.md",
            SHARED_AGGREGATE_PATHS,
        )
        self.assertIn(
            "src/compatibilityMatrixFixture/resources/META-INF/auto_storage/"
            "compatibility-matrix-companions.json",
            SHARED_AGGREGATE_PATHS,
        )

        isolated = {
            "mods": ["sample"],
            "descriptors": [],
            "resourceKinds": [],
            "acceptedRecipes": [],
            "rejectedDescriptors": [],
            "rejectedResourceKinds": [],
            "recipeInventory": {
                "namespaces": ["sample"],
                "sha256": "a" * 64,
            },
        }
        validate_descriptor_matrix({"matrix": isolated})
        with self.assertRaisesRegex(
            ValueError, "coexistence|cross-owned|isolated|must declare|unclaimed"
        ):
            validate_descriptor_matrix(
                {
                    "matrix": {
                        **isolated,
                        "coexistenceRecipeInventory": {"sha256": "b" * 64},
                    }
                }
            )

        companions = {"schema": 1, "companions": []}
        validate_companions(companions)
        for forbidden in (
            "coexistenceRecipeInventory",
            "unclaimedRecipeInventory",
        ):
            with self.assertRaisesRegex(ValueError, forbidden):
                validate_companions(
                    {
                        "schema": 1,
                        "companions": [],
                        forbidden: {"sha256": "c" * 64},
                    }
                )

        descriptor = {
            "schema": 1,
            "id": "auto_storage:sample",
            "matrix": isolated,
        }
        manifest = build_manifest([descriptor], companions)
        self.assertNotIn("coexistenceRecipeInventory", manifest)
        self.assertNotIn("unclaimedRecipeInventory", manifest)
        self.assertNotIn("coexistenceRecipeInventory", manifest["modules"][0])

    def test_peer_descriptor_digest_stays_isolated_when_module_added(self):
        from compatibility_matrix_manifest import build_manifest, recipe_inventory_sha256

        peer = {
            "schema": 1,
            "id": "auto_storage:peer",
            "matrix": {
                "mods": ["peer"],
                "descriptors": [],
                "resourceKinds": [],
                "acceptedRecipes": [],
                "rejectedDescriptors": [],
                "rejectedResourceKinds": [],
                "recipeInventory": {
                    "namespaces": ["peer"],
                    "sha256": recipe_inventory_sha256(["peer:base"]),
                },
            },
        }
        new_module = {
            "schema": 1,
            "id": "auto_storage:intruder",
            "matrix": {
                "mods": ["intruder"],
                "descriptors": [],
                "resourceKinds": [],
                "acceptedRecipes": [],
                "rejectedDescriptors": [],
                "rejectedResourceKinds": [],
                "recipeInventory": {
                    "namespaces": ["intruder"],
                    "sha256": recipe_inventory_sha256(["intruder:one"]),
                },
            },
        }
        companions = {"schema": 1, "companions": []}
        before = build_manifest([peer], companions)
        after = build_manifest([peer, new_module], companions)
        peer_before = next(
            module for module in before["modules"] if module["id"] == peer["id"]
        )
        peer_after = next(
            module for module in after["modules"] if module["id"] == peer["id"]
        )
        self.assertEqual(peer_before["recipeInventory"], peer_after["recipeInventory"])
        self.assertEqual(before.keys(), after.keys())
        self.assertNotIn("coexistenceRecipeInventory", before)
        self.assertNotIn("unclaimedRecipeInventory", after)

    def test_two_independent_module_additions_merge_without_shared_conflicts(self):
        from compatibility_matrix_manifest import (
            SHARED_AGGREGATE_PATHS,
            recipe_inventory_sha256,
        )

        def write_descriptor(root: Path, descriptor: dict) -> Path:
            module_dir = root / "src/compat" / descriptor["id"].split(":", 1)[1]
            module_dir.mkdir(parents=True, exist_ok=True)
            path = module_dir / "compat-module.json"
            path.write_text(
                json.dumps(descriptor, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )
            return path

        def git(root: Path, *args: str) -> subprocess.CompletedProcess[str]:
            return subprocess.run(
                ["git", "-C", str(root), *args],
                check=True,
                capture_output=True,
                text=True,
            )

        def changed_paths(root: Path, base: str, tip: str) -> set[str]:
            result = git(root, "diff", "--name-only", f"{base}..{tip}")
            return {line for line in result.stdout.splitlines() if line}

        base_peer = {
            "schema": 1,
            "id": "auto_storage:shared_peer",
            "entrypoint": "com.swear.autostorage.compat.sharedpeer.SharedPeerCompatModule",
            "requires": ["shared_peer"],
            "side": "both",
            "sourceSet": "compatSharedPeer",
            "fixture": "sharedPeerFixture",
            "expectedTests": 1,
            "dependencies": ["maven.modrinth:shared-peer:1"],
            "runtimeDependencies": ["maven.modrinth:shared-peer:1"],
            "matrix": {
                "mods": ["shared_peer"],
                "descriptors": [],
                "resourceKinds": [],
                "acceptedRecipes": [],
                "rejectedDescriptors": [],
                "rejectedResourceKinds": [],
                "recipeInventory": {
                    "namespaces": ["shared_peer"],
                    "sha256": recipe_inventory_sha256(["shared_peer:one"]),
                },
            },
        }
        module_a = {
            "schema": 1,
            "id": "auto_storage:synth_a",
            "entrypoint": "com.swear.autostorage.compat.syntha.SynthACompatModule",
            "requires": ["synth_a"],
            "side": "both",
            "sourceSet": "compatSynthA",
            "fixture": "synthAFixture",
            "expectedTests": 2,
            "dependencies": ["maven.modrinth:synth-a:1"],
            "runtimeDependencies": ["maven.modrinth:synth-a:1"],
            "matrix": {
                "mods": ["synth_a"],
                "descriptors": [],
                "resourceKinds": [],
                "acceptedRecipes": [],
                "rejectedDescriptors": [],
                "rejectedResourceKinds": [],
                "recipeInventory": {
                    "namespaces": ["synth_a"],
                    "sha256": recipe_inventory_sha256(["synth_a:one"]),
                },
            },
        }
        module_b = {
            "schema": 1,
            "id": "auto_storage:synth_b",
            "entrypoint": "com.swear.autostorage.compat.synthb.SynthBCompatModule",
            "requires": ["synth_b"],
            "side": "both",
            "sourceSet": "compatSynthB",
            "fixture": "synthBFixture",
            "expectedTests": 3,
            "dependencies": ["maven.modrinth:synth-b:1"],
            "runtimeDependencies": ["maven.modrinth:synth-b:1"],
            "matrix": {
                "mods": ["synth_b"],
                "descriptors": [],
                "resourceKinds": [],
                "acceptedRecipes": [],
                "rejectedDescriptors": [],
                "rejectedResourceKinds": [],
                "recipeInventory": {
                    "namespaces": ["synth_b"],
                    "sha256": recipe_inventory_sha256(["synth_b:one"]),
                },
            },
        }
        companions = {"schema": 1, "companions": []}
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_descriptor(root, base_peer)
            companions_path = (
                root
                / "src/compatibilityMatrixFixture/resources/META-INF/auto_storage/"
                "compatibility-matrix-companions.json"
            )
            companions_path.parent.mkdir(parents=True)
            companions_path.write_text(
                json.dumps(companions, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )
            (root / "README.md").write_text(
                "Compatibility summary: build/reports/compatibility-modules.md\n",
                encoding="utf-8",
            )
            git(root, "init")
            git(root, "config", "user.email", "issue77@example.com")
            git(root, "config", "user.name", "issue77")
            git(root, "add", ".")
            git(root, "commit", "-m", "base")
            base = git(root, "rev-parse", "HEAD").stdout.strip()
            peer_before = (
                root / "src/compat/shared_peer/compat-module.json"
            ).read_bytes()

            git(root, "checkout", "-b", "add-synth-a")
            write_descriptor(root, module_a)
            docs_a = root / "docs/synth-a-compatibility.md"
            docs_a.parent.mkdir(parents=True, exist_ok=True)
            docs_a.write_text("# Synth A\n", encoding="utf-8")
            git(root, "add", ".")
            git(root, "commit", "-m", "add synth_a")
            tip_a = git(root, "rev-parse", "HEAD").stdout.strip()
            paths_a = changed_paths(root, base, tip_a)

            git(root, "checkout", base)
            git(root, "checkout", "-b", "add-synth-b")
            write_descriptor(root, module_b)
            docs_b = root / "docs/synth-b-compatibility.md"
            docs_b.parent.mkdir(parents=True, exist_ok=True)
            docs_b.write_text("# Synth B\n", encoding="utf-8")
            git(root, "add", ".")
            git(root, "commit", "-m", "add synth_b")
            tip_b = git(root, "rev-parse", "HEAD").stdout.strip()
            paths_b = changed_paths(root, base, tip_b)

            self.assertTrue(paths_a)
            self.assertTrue(paths_b)
            self.assertFalse(paths_a & paths_b)
            for paths in (paths_a, paths_b):
                shared = paths & SHARED_AGGREGATE_PATHS
                self.assertFalse(
                    shared,
                    f"module PR must not edit shared aggregate paths: {sorted(shared)}",
                )
                self.assertNotIn(
                    "src/compat/shared_peer/compat-module.json",
                    paths,
                )
            self.assertEqual(
                peer_before,
                (root / "src/compat/shared_peer/compat-module.json").read_bytes(),
            )

            merge = subprocess.run(
                [
                    "git",
                    "-C",
                    str(root),
                    "merge-tree",
                    base,
                    tip_a,
                    tip_b,
                ],
                check=True,
                capture_output=True,
                text=True,
            )
            self.assertNotIn("changed in both", merge.stdout)
            self.assertNotRegex(merge.stdout, r"^<<<<<<<", re.M)

            git(root, "checkout", "add-synth-a")
            conflict_summary = (
                root / "docs/generated/compatibility-modules.md"
            )
            conflict_summary.parent.mkdir(parents=True, exist_ok=True)
            conflict_summary.write_text("# A summary\n", encoding="utf-8")
            git(root, "add", ".")
            git(root, "commit", "-m", "regen shared summary on A")
            tip_a_conflict = git(root, "rev-parse", "HEAD").stdout.strip()
            git(root, "checkout", "add-synth-b")
            conflict_summary.parent.mkdir(parents=True, exist_ok=True)
            conflict_summary.write_text("# B summary\n", encoding="utf-8")
            git(root, "add", ".")
            git(root, "commit", "-m", "regen shared summary on B")
            tip_b_conflict = git(root, "rev-parse", "HEAD").stdout.strip()
            conflict_paths_a = changed_paths(root, base, tip_a_conflict)
            conflict_paths_b = changed_paths(root, base, tip_b_conflict)
            self.assertTrue(conflict_paths_a & conflict_paths_b & SHARED_AGGREGATE_PATHS)
            conflict_merge = subprocess.run(
                [
                    "git",
                    "-C",
                    str(root),
                    "merge-tree",
                    base,
                    tip_a_conflict,
                    tip_b_conflict,
                ],
                check=True,
                capture_output=True,
                text=True,
            )
            self.assertTrue(
                "changed in both" in conflict_merge.stdout
                or "<<<<<<<" in conflict_merge.stdout,
                conflict_merge.stdout,
            )

    def test_compatibility_summary_is_build_report_artifact(self):
        from compatibility_matrix_manifest import (
            COMPATIBILITY_SUMMARY_RELATIVE_PATH,
            build_compatibility_summary,
            load_descriptors,
            render_compatibility_summary,
            validate_compatibility_summary,
            write_compatibility_summary,
        )

        build = (ROOT / "build.gradle").read_text(encoding="utf-8")
        self.assertIn(COMPATIBILITY_SUMMARY_RELATIVE_PATH, build)
        self.assertNotIn("docs/generated/compatibility-modules.md", build)
        self.assertFalse((ROOT / "docs/generated").exists())

        descriptors = load_descriptors(ROOT / "src/compat")
        with tempfile.TemporaryDirectory() as tmp:
            summary_path = Path(tmp) / COMPATIBILITY_SUMMARY_RELATIVE_PATH
            write_compatibility_summary(ROOT, summary_path)
            expected = render_compatibility_summary(
                build_compatibility_summary(descriptors, docs_root=ROOT / "docs")
            )
            self.assertEqual(expected, summary_path.read_text(encoding="utf-8"))
            validate_compatibility_summary(
                expected,
                descriptors,
                docs_root=ROOT / "docs",
            )
            with self.assertRaisesRegex(ValueError, "stale|drift|compatibility summary"):
                validate_compatibility_summary(
                    expected + "\n<!-- tampered -->\n",
                    descriptors,
                    docs_root=ROOT / "docs",
                )
            self.assertRegex(
                expected,
                re.compile(r"build/reports/compatibility-modules\.md"),
            )

    def test_repository_has_no_committed_global_recipe_digests(self):
        from compatibility_matrix_manifest import (
            build_manifest_from_roots,
            load_companions,
            load_descriptors,
        )

        companions = load_companions(
            ROOT
            / "src/compatibilityMatrixFixture/resources/META-INF/auto_storage/"
            / "compatibility-matrix-companions.json"
        )
        self.assertEqual({"schema", "companions"}, set(companions))
        self.assertNotIn("coexistenceRecipeInventory", companions)
        self.assertNotIn("unclaimedRecipeInventory", companions)
        for descriptor in load_descriptors(ROOT / "src/compat"):
            self.assertNotIn("coexistenceRecipeInventory", descriptor)
            self.assertNotIn("coexistenceRecipeInventory", descriptor["matrix"])
            self.assertNotIn("unclaimedRecipeInventory", descriptor)
            self.assertNotIn("unclaimedRecipeInventory", descriptor["matrix"])
        manifest = build_manifest_from_roots(ROOT)
        self.assertEqual({"schema", "modules", "companions"}, set(manifest))
        self.assertNotIn("coexistenceRecipeInventory", manifest)
        self.assertNotIn("unclaimedRecipeInventory", manifest)

    def test_isolated_fixtures_must_verify_descriptor_recipe_inventory(self):
        build = (ROOT / "build.gradle").read_text(encoding="utf-8")
        helper = ROOT / (
            "src/main/java/com/swear/autostorage/IsolatedRecipeInventoryEvidence.java"
        )
        self.assertTrue(helper.is_file())
        self.assertIn("isolated-recipe-inventory.json", build)
        self.assertIn("generateIsolatedRecipeInventory_", build)

        from compatibility_matrix_manifest import load_descriptors

        def fixture_sources(fixture: str) -> list[Path]:
            fixture_root = ROOT / "src" / fixture
            return [
                path
                for path in fixture_root.rglob("*.java")
                if "GameTest" in path.read_text(encoding="utf-8")
                or "@GameTest" in path.read_text(encoding="utf-8")
            ]

        for descriptor in load_descriptors(ROOT / "src/compat"):
            fixture = descriptor["fixture"]
            sources = fixture_sources(fixture)
            self.assertTrue(sources, f"missing GameTests for {fixture}")
            joined = "\n".join(path.read_text(encoding="utf-8") for path in sources)
            self.assertIn(
                "IsolatedRecipeInventoryEvidence",
                joined,
                f"{fixture} must verify descriptor recipeInventory",
            )
        pneumatic = ROOT / (
            "src/pneumaticCraftFixture/java/com/swear/autostorage/fixture/"
            "pneumaticcraft/PneumaticCraftIntegrationGameTests.java"
        )
        self.assertIn(
            "IsolatedRecipeInventoryEvidence",
            pneumatic.read_text(encoding="utf-8"),
        )


if __name__ == "__main__":
    unittest.main()
