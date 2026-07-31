import hashlib
import importlib.util
import json
import subprocess
import tempfile
import tomllib
import unittest
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "tools/compat-kit/compat_kit.py"


def load_compat_kit():
    if not MODULE_PATH.is_file():
        raise AssertionError("missing tools/compat-kit/compat_kit.py")
    spec = importlib.util.spec_from_file_location("auto_storage_compat_kit", MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def write_fixture_jar(path: Path, version: str = "1.2.3", extra_class: str | None = None):
    mods_toml = f"""
modLoader="javafml"
loaderVersion="[4,)"
license="MIT"

[[mods]]
modId="samplemod"
version="{version}"
displayName="Sample Machines"
""".strip()
    entries = {
        "META-INF/neoforge.mods.toml": mods_toml.encode(),
        "samplemod/recipe/CrushingRecipe.class": b"recipe",
        "samplemod/recipe/ChanceRecipe.class": b"chance",
        "samplemod/api/FluidHandler.class": b"fluid",
        "samplemod/machine/CrusherBlock.class": b"station",
        "samplemod/decor/CasingBlock.class": b"decor",
        "samplemod/Unrelated.class": b"other",
    }
    if extra_class:
        entries[extra_class.replace(".", "/") + ".class"] = b"extra"
    with zipfile.ZipFile(path, "w") as archive:
        for name, payload in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2020, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, payload)


class CompatKitAuditTests(unittest.TestCase):
    def setUp(self):
        self.compat_kit = load_compat_kit()
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name).resolve()
        self.jar = self.root / "samplemod.jar"
        write_fixture_jar(self.jar)

    def tearDown(self):
        self.temp.cleanup()

    @staticmethod
    def signatures(class_name: str) -> str:
        signatures = {
            "samplemod.recipe.CrushingRecipe": (
                "public final class samplemod.recipe.CrushingRecipe "
                "implements net.minecraft.world.item.crafting.Recipe { "
                "public net.minecraft.world.item.ItemStack getResultItem(); }"
            ),
            "samplemod.recipe.ChanceRecipe": (
                "public final class samplemod.recipe.ChanceRecipe { "
                "public float getChance(); public java.util.Random random(); }"
            ),
            "samplemod.api.FluidHandler": (
                "public interface samplemod.api.FluidHandler { "
                "public net.neoforged.neoforge.fluids.FluidStack getFluid(); }"
            ),
            "samplemod.machine.CrusherBlock": (
                "public final class samplemod.machine.CrusherBlock "
                "extends net.minecraft.world.level.block.Block { }"
            ),
        }
        return signatures[class_name]

    def test_scan_is_deterministic_compact_and_fact_only(self):
        source = self.root / "source"
        source_file = source / "src/main/java/samplemod/recipe/CrushingRecipe.java"
        source_file.parent.mkdir(parents=True)
        source_file.write_text(
            "package samplemod.recipe;\n"
            "final class CrushingRecipe { int processingTime; }\n"
        )
        unrelated_same_name = (
            source
            / "src/main/java/samplemod/unrelated/CrushingRecipe.java"
        )
        unrelated_same_name.parent.mkdir(parents=True)
        unrelated_same_name.write_text(
            "package samplemod.unrelated;\nfinal class CrushingRecipe {}\n"
        )
        subprocess.run(["git", "init", "-q", source], check=True)
        subprocess.run(["git", "-C", source, "add", "."], check=True)
        subprocess.run(
            [
                "git",
                "-C",
                source,
                "-c",
                "user.name=Compat Kit Test",
                "-c",
                "user.email=compat-kit@example.invalid",
                "commit",
                "-qm",
                "fixture",
            ],
            check=True,
        )

        first = self.compat_kit.scan_jar(
            self.jar,
            source=source,
            signature_reader=self.signatures,
        )
        second = self.compat_kit.scan_jar(
            self.jar,
            source=source,
            signature_reader=self.signatures,
        )

        self.assertEqual(first, second)
        self.assertEqual(1, first["schema"])
        self.assertEqual("auto_storage_compat_audit", first["kind"])
        self.assertEqual(
            {
                "mod_id": "samplemod",
                "display_name": "Sample Machines",
                "version": "1.2.3",
            },
            first["target"],
        )
        self.assertEqual(hashlib.sha256(self.jar.read_bytes()).hexdigest(), first["artifact"]["sha256"])
        self.assertEqual(self.jar.stat().st_size, first["artifact"]["size"])
        self.assertEqual(
            [
                "samplemod.recipe.ChanceRecipe",
                "samplemod.recipe.CrushingRecipe",
            ],
            [candidate["class"] for candidate in first["candidates"]["recipe_classes"]],
        )
        self.assertEqual(
            ["samplemod.api.FluidHandler"],
            [candidate["class"] for candidate in first["candidates"]["resource_apis"]],
        )
        self.assertEqual(
            ["samplemod.machine.CrusherBlock"],
            [candidate["class"] for candidate in first["candidates"]["station_classes"]],
        )
        self.assertNotIn("samplemod.decor.CasingBlock", json.dumps(first))
        self.assertEqual(
            ["src/main/java/samplemod/recipe/CrushingRecipe.java"],
            first["source"]["files"],
        )
        self.assertNotIn(str(self.root), json.dumps(first))
        self.assertNotIn("consumes", json.dumps(first))
        self.assertNotIn("catalyst", json.dumps(first))

    def test_scan_records_risks_with_exact_evidence_but_does_not_decide_semantics(self):
        audit = self.compat_kit.scan_jar(
            self.jar,
            signature_reader=self.signatures,
        )

        risks = {risk["code"]: risk for risk in audit["risks"]}
        self.assertIn("chance_output", risks)
        self.assertEqual(
            ["samplemod.recipe.ChanceRecipe#getChance"],
            risks["chance_output"]["evidence"],
        )
        self.assertIn("randomness", risks)
        self.assertEqual(
            ["samplemod.recipe.ChanceRecipe#random"],
            risks["randomness"]["evidence"],
        )
        for risk in audit["risks"]:
            self.assertEqual("needs_decision", risk["disposition"])

    def test_scan_uses_private_bytecode_for_risks_without_publishing_it(self):
        audit = self.compat_kit.scan_jar(
            self.jar,
            signature_reader=self.signatures,
            risk_reader=lambda class_name: (
                "public NonNullList getIngredients(); "
                "private MultiblockController controller; "
                "private BlockEntity machine; "
                "private void execute() { "
                "RandomSource random; IItemHandler.insertItem(); }"
                if class_name == "samplemod.recipe.CrushingRecipe"
                else self.signatures(class_name)
            ),
        )

        randomness = next(
            risk for risk in audit["risks"] if risk["code"] == "randomness"
        )
        self.assertIn(
            "samplemod.recipe.CrushingRecipe: Random",
            randomness["evidence"],
        )
        risks = {risk["code"]: risk for risk in audit["risks"]}
        self.assertEqual(
            ["samplemod.recipe.CrushingRecipe: multiblock API"],
            risks["multiblock"]["evidence"],
        )
        self.assertEqual(
            ["samplemod.recipe.CrushingRecipe: live machine state"],
            risks["live_machine_state"]["evidence"],
        )
        self.assertEqual(
            ["samplemod.recipe.CrushingRecipe#getIngredients"],
            risks["generic_ingredients"]["evidence"],
        )
        self.assertEqual(
            ["samplemod.recipe.CrushingRecipe#insertItem"],
            risks["simulation_required"]["evidence"],
        )
        crushing = next(
            candidate
            for candidate in audit["candidates"]["recipe_classes"]
            if candidate["class"] == "samplemod.recipe.CrushingRecipe"
        )
        self.assertNotIn("RandomSource", crushing["public_signature"])
        self.assertEqual({"class", "public_signature"}, set(crushing))

    def test_scan_detects_capability_mutation_in_real_javap_invocation_syntax(self):
        audit = self.compat_kit.scan_jar(
            self.jar,
            signature_reader=self.signatures,
            risk_reader=lambda class_name: (
                "  12: invokeinterface #42,  4 // InterfaceMethod "
                "net/neoforged/neoforge/items/IItemHandler.insertItem:"
                "(IILnet/minecraft/world/item/ItemStack;Z)"
                "Lnet/minecraft/world/item/ItemStack;\n"
                "  18: invokevirtual #51 // Method "
                "samplemod/recipe/Output.getChance:()F\n"
                "  21: invokevirtual #52 // Method "
                "samplemod/recipe/Output.random:()Ljava/util/Random;\n"
                "  24: invokevirtual #53 // Method "
                "samplemod/recipe/Output.getIngredients:()Ljava/util/List;"
                if class_name == "samplemod.recipe.CrushingRecipe"
                else self.signatures(class_name)
            ),
        )

        risks = {risk["code"]: risk for risk in audit["risks"]}
        self.assertEqual(
            ["samplemod.recipe.CrushingRecipe#insertItem"],
            risks["simulation_required"]["evidence"],
        )
        self.assertEqual(
            [
                "samplemod.recipe.ChanceRecipe#getChance",
                "samplemod.recipe.CrushingRecipe#getChance",
            ],
            risks["chance_output"]["evidence"],
        )
        self.assertIn(
            "samplemod.recipe.CrushingRecipe#random",
            risks["randomness"]["evidence"],
        )
        self.assertEqual(
            ["samplemod.recipe.CrushingRecipe#getIngredients"],
            risks["generic_ingredients"]["evidence"],
        )

    def test_scan_allows_bounded_large_private_bytecode(self):
        large_private_bytecode = (
            "private void generated() { "
            + ("aload_0 nop " * 24_000)
            + "RandomSource random; }"
        )
        self.assertGreater(
            len(large_private_bytecode.encode("utf-8")),
            self.compat_kit.MAX_SIGNATURE_BYTES,
        )

        audit = self.compat_kit.scan_jar(
            self.jar,
            signature_reader=self.signatures,
            risk_reader=lambda class_name: (
                large_private_bytecode
                if class_name == "samplemod.recipe.CrushingRecipe"
                else self.signatures(class_name)
            ),
        )

        randomness = next(
            risk for risk in audit["risks"] if risk["code"] == "randomness"
        )
        self.assertIn(
            "samplemod.recipe.CrushingRecipe: Random",
            randomness["evidence"],
        )

    def test_scan_cache_is_sha_keyed_and_does_not_repeat_javap(self):
        cache = self.root / "cache"
        first = self.compat_kit.scan_jar(
            self.jar,
            cache_dir=cache,
            signature_reader=self.signatures,
        )

        def unexpected_reader(_class_name: str):
            raise AssertionError("cached scan unexpectedly invoked javap")

        second = self.compat_kit.scan_jar(
            self.jar,
            cache_dir=cache,
            signature_reader=unexpected_reader,
        )

        self.assertEqual(first, second)
        cached = (
            cache
            / first["artifact"]["sha256"]
            / f"v{self.compat_kit.SCAN_CACHE_VERSION}"
            / "audit.json"
        )
        self.assertEqual(first, json.loads(cached.read_text()))

    def test_scan_ignores_cache_from_an_older_scanner_format(self):
        cache = self.root / "cache"
        artifact_sha = hashlib.sha256(self.jar.read_bytes()).hexdigest()
        legacy = cache / artifact_sha / "audit.json"
        legacy.parent.mkdir(parents=True)
        legacy.write_text("{}")
        calls = []

        def recording_reader(class_name: str):
            calls.append(class_name)
            return self.signatures(class_name)

        audit = self.compat_kit.scan_jar(
            self.jar,
            cache_dir=cache,
            signature_reader=recording_reader,
        )

        self.assertTrue(calls)
        self.assertEqual(artifact_sha, audit["artifact"]["sha256"])

    def test_scan_rejects_dirty_git_source_checkout(self):
        source = self.root / "source"
        source_file = source / "src/main/java/samplemod/recipe/CrushingRecipe.java"
        source_file.parent.mkdir(parents=True)
        source_file.write_text("package samplemod.recipe;\nfinal class CrushingRecipe {}\n")
        subprocess.run(["git", "init", "-q", source], check=True)
        subprocess.run(["git", "-C", source, "add", "."], check=True)
        subprocess.run(
            [
                "git",
                "-C",
                source,
                "-c",
                "user.name=Compat Kit Test",
                "-c",
                "user.email=compat-kit@example.invalid",
                "commit",
                "-qm",
                "fixture",
            ],
            check=True,
        )
        source_file.write_text(
            "package samplemod.recipe;\nfinal class CrushingRecipe { int dirty; }\n"
        )

        with self.assertRaisesRegex(ValueError, "source checkout is dirty"):
            self.compat_kit.scan_jar(
                self.jar,
                source=source,
                signature_reader=self.signatures,
            )

    def test_scan_rejects_source_outside_git_worktree(self):
        source = self.root / "source"
        source_file = source / "src/main/java/samplemod/recipe/CrushingRecipe.java"
        source_file.parent.mkdir(parents=True)
        source_file.write_text(
            "package samplemod.recipe;\nfinal class CrushingRecipe {}\n"
        )

        with self.assertRaisesRegex(
            ValueError,
            "source must be inside a Git worktree",
        ):
            self.compat_kit.scan_jar(
                self.jar,
                source=source,
                signature_reader=self.signatures,
            )

    def test_scan_rejects_source_checkout_without_candidate_matches(self):
        source = self.root / "source"
        source_file = source / "src/main/java/unrelated/Decor.java"
        source_file.parent.mkdir(parents=True)
        source_file.write_text("package unrelated;\nfinal class Decor {}\n")
        subprocess.run(["git", "init", "-q", source], check=True)
        subprocess.run(["git", "-C", source, "add", "."], check=True)
        subprocess.run(
            [
                "git",
                "-C",
                source,
                "-c",
                "user.name=Compat Kit Test",
                "-c",
                "user.email=compat-kit@example.invalid",
                "commit",
                "-qm",
                "fixture",
            ],
            check=True,
        )

        with self.assertRaisesRegex(
            ValueError,
            "source checkout has no candidate matches",
        ):
            self.compat_kit.scan_jar(
                self.jar,
                source=source,
                signature_reader=self.signatures,
            )

    def test_scan_requires_candidate_source_path_segment_boundary(self):
        source = self.root / "source"
        source_file = (
            source
            / "src/main/java/notsamplemod/recipe/CrushingRecipe.java"
        )
        source_file.parent.mkdir(parents=True)
        source_file.write_text(
            "package notsamplemod.recipe;\nfinal class CrushingRecipe {}\n"
        )
        subprocess.run(["git", "init", "-q", source], check=True)
        subprocess.run(["git", "-C", source, "add", "."], check=True)
        subprocess.run(
            [
                "git",
                "-C",
                source,
                "-c",
                "user.name=Compat Kit Test",
                "-c",
                "user.email=compat-kit@example.invalid",
                "commit",
                "-qm",
                "fixture",
            ],
            check=True,
        )

        with self.assertRaisesRegex(
            ValueError,
            "source checkout has no candidate matches",
        ):
            self.compat_kit.scan_jar(
                self.jar,
                source=source,
                signature_reader=self.signatures,
            )

    def test_scan_rejects_ignored_candidate_source(self):
        source = self.root / "source"
        (source / ".gitignore").parent.mkdir(parents=True)
        (source / ".gitignore").write_text("build/\n")
        tracked = source / "src/main/java/unrelated/Decor.java"
        tracked.parent.mkdir(parents=True)
        tracked.write_text("package unrelated;\nfinal class Decor {}\n")
        ignored = source / "build/samplemod/recipe/CrushingRecipe.java"
        ignored.parent.mkdir(parents=True)
        ignored.write_text(
            "package samplemod.recipe;\nfinal class CrushingRecipe {}\n"
        )
        subprocess.run(["git", "init", "-q", source], check=True)
        subprocess.run(["git", "-C", source, "add", "."], check=True)
        subprocess.run(
            [
                "git",
                "-C",
                source,
                "-c",
                "user.name=Compat Kit Test",
                "-c",
                "user.email=compat-kit@example.invalid",
                "commit",
                "-qm",
                "fixture",
            ],
            check=True,
        )

        with self.assertRaisesRegex(
            ValueError,
            "source checkout has no candidate matches",
        ):
            self.compat_kit.scan_jar(
                self.jar,
                source=source,
                signature_reader=self.signatures,
            )

    def test_scan_discovers_enclosing_git_root_for_source_subdirectory(self):
        repository = self.root / "repository"
        source = repository / "module"
        source_file = source / "src/main/java/samplemod/recipe/CrushingRecipe.java"
        source_file.parent.mkdir(parents=True)
        source_file.write_text(
            "package samplemod.recipe;\nfinal class CrushingRecipe {}\n"
        )
        subprocess.run(["git", "init", "-q", repository], check=True)
        subprocess.run(["git", "-C", repository, "add", "."], check=True)
        subprocess.run(
            [
                "git",
                "-C",
                repository,
                "-c",
                "user.name=Compat Kit Test",
                "-c",
                "user.email=compat-kit@example.invalid",
                "commit",
                "-qm",
                "fixture",
            ],
            check=True,
        )
        expected_revision = subprocess.check_output(
            ["git", "-C", repository, "rev-parse", "HEAD"],
            text=True,
        ).strip()

        audit = self.compat_kit.scan_jar(
            self.jar,
            source=source,
            signature_reader=self.signatures,
        )

        self.assertEqual(expected_revision, audit["source"]["revision"])
        (repository / "untracked-outside-module.txt").write_text("dirty\n")
        with self.assertRaisesRegex(ValueError, "source checkout is dirty"):
            self.compat_kit.scan_jar(
                self.jar,
                source=source,
                signature_reader=self.signatures,
            )

    def test_scan_records_and_validates_current_scanner_format(self):
        audit = self.compat_kit.scan_jar(
            self.jar,
            signature_reader=self.signatures,
        )

        self.assertEqual(
            self.compat_kit.SCAN_CACHE_VERSION,
            audit["scanner_format"],
        )
        audit["scanner_format"] -= 1
        with self.assertRaisesRegex(
            ValueError,
            "unsupported audit scanner format",
        ):
            self.compat_kit._validate_audit(audit)

    def test_scan_rejects_malformed_current_cache_structure(self):
        cache = self.root / "cache"
        artifact_sha = hashlib.sha256(self.jar.read_bytes()).hexdigest()
        cached = (
            cache
            / artifact_sha
            / f"v{self.compat_kit.SCAN_CACHE_VERSION}"
            / "audit.json"
        )
        cached.parent.mkdir(parents=True)
        malformed = {
            "schema": 1,
            "scanner_format": self.compat_kit.SCAN_CACHE_VERSION,
            "kind": "auto_storage_compat_audit",
            "target": {},
            "artifact": {"sha256": artifact_sha, "size": 1},
            "source": {"revision": None, "files": []},
            "candidates": {},
            "risks": [],
        }
        cached.write_text(json.dumps(malformed))

        with self.assertRaisesRegex(ValueError, "audit target"):
            self.compat_kit.scan_jar(
                self.jar,
                cache_dir=cache,
                signature_reader=lambda _: "unexpected",
            )

    def test_scan_keeps_named_nested_recipe_classes_but_excludes_synthetic_classes(self):
        nested_jar = self.root / "samplemod-nested.jar"
        write_fixture_jar(nested_jar)
        with zipfile.ZipFile(nested_jar, "a") as archive:
            archive.writestr(
                "samplemod/recipe/CrusherRecipes$PolishingRecipe.class",
                b"named nested recipe",
            )
            archive.writestr(
                "samplemod/recipe/CrusherRecipes$1.class",
                b"anonymous recipe",
            )
            archive.writestr(
                "samplemod/recipe/CrusherRecipes$1LocalRecipe.class",
                b"local recipe",
            )
            archive.writestr(
                "samplemod/recipe/CrusherRecipes$WhenMappings.class",
                bytes.fromhex("cafebabe0000003d000110000000"),
            )
        source = self.root / "source"
        source_file = (
            source
            / "src/main/java/samplemod/recipe/CrusherRecipes.java"
        )
        source_file.parent.mkdir(parents=True)
        source_file.write_text(
            "package samplemod.recipe;\n"
            "final class CrusherRecipes { static final class PolishingRecipe {} }\n"
        )
        subprocess.run(["git", "init", "-q", source], check=True)
        subprocess.run(["git", "-C", source, "add", "."], check=True)
        subprocess.run(
            [
                "git",
                "-C",
                source,
                "-c",
                "user.name=Compat Kit Test",
                "-c",
                "user.email=compat-kit@example.invalid",
                "commit",
                "-qm",
                "fixture",
            ],
            check=True,
        )

        def nested_signatures(class_name: str) -> str:
            if class_name == "samplemod.recipe.CrusherRecipes$PolishingRecipe":
                return (
                    "public final class "
                    "samplemod.recipe.CrusherRecipes$PolishingRecipe "
                    "implements net.minecraft.world.item.crafting.Recipe { }"
                )
            return self.signatures(class_name)

        audit = self.compat_kit.scan_jar(
            nested_jar,
            source=source,
            signature_reader=nested_signatures,
        )

        recipe_classes = [
            candidate["class"]
            for candidate in audit["candidates"]["recipe_classes"]
        ]
        self.assertIn(
            "samplemod.recipe.CrusherRecipes$PolishingRecipe",
            recipe_classes,
        )
        self.assertNotIn("samplemod.recipe.CrusherRecipes$1", recipe_classes)
        self.assertNotIn(
            "samplemod.recipe.CrusherRecipes$1LocalRecipe",
            recipe_classes,
        )
        self.assertNotIn(
            "samplemod.recipe.CrusherRecipes$WhenMappings",
            recipe_classes,
        )
        self.assertEqual(
            ["src/main/java/samplemod/recipe/CrusherRecipes.java"],
            audit["source"]["files"],
        )

    def test_scan_ignores_multi_release_archive_paths_and_scans_root_class_once(self):
        multi_release_jar = self.root / "samplemod-multi-release.jar"
        write_fixture_jar(multi_release_jar)
        with zipfile.ZipFile(multi_release_jar, "a") as archive:
            archive.writestr(
                "samplemod/recipe/VersionedRecipe.class",
                b"root recipe",
            )
            archive.writestr(
                "META-INF/versions/21/samplemod/recipe/VersionedRecipe.class",
                b"versioned recipe",
            )
        calls = []

        def multi_release_signatures(class_name: str) -> str:
            calls.append(class_name)
            if class_name == "samplemod.recipe.VersionedRecipe":
                return (
                    "public final class samplemod.recipe.VersionedRecipe "
                    "implements net.minecraft.world.item.crafting.Recipe { }"
                )
            return self.signatures(class_name)

        audit = self.compat_kit.scan_jar(
            multi_release_jar,
            signature_reader=multi_release_signatures,
            risk_reader=lambda class_name: (
                "public final class samplemod.recipe.VersionedRecipe { }"
                if class_name == "samplemod.recipe.VersionedRecipe"
                else self.signatures(class_name)
            ),
        )

        self.assertEqual(1, calls.count("samplemod.recipe.VersionedRecipe"))
        self.assertNotIn("META-INF.versions", calls)
        self.assertIn(
            "samplemod.recipe.VersionedRecipe",
            {
                candidate["class"]
                for candidate in audit["candidates"]["recipe_classes"]
            },
        )

    def test_scan_rejects_malformed_or_ambiguous_mod_metadata(self):
        malformed = self.root / "malformed.jar"
        with zipfile.ZipFile(malformed, "w") as archive:
            archive.writestr("samplemod/Recipe.class", b"recipe")
        with self.assertRaisesRegex(ValueError, "NeoForge mod metadata"):
            self.compat_kit.scan_jar(malformed, signature_reader=lambda _: "")

        ambiguous = self.root / "ambiguous.jar"
        with zipfile.ZipFile(ambiguous, "w") as archive:
            archive.writestr(
                "META-INF/neoforge.mods.toml",
                'modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n'
                '[[mods]]\nmodId="first"\nversion="1"\ndisplayName="First"\n'
                '[[mods]]\nmodId="second"\nversion="1"\ndisplayName="Second"\n',
            )
        with self.assertRaisesRegex(ValueError, "exactly one mod"):
            self.compat_kit.scan_jar(ambiguous, signature_reader=lambda _: "")

    def test_scan_rejects_oversized_mod_metadata_before_decompression(self):
        oversized = self.root / "oversized-metadata.jar"
        with zipfile.ZipFile(
            oversized,
            "w",
            compression=zipfile.ZIP_DEFLATED,
        ) as archive:
            archive.writestr(
                "META-INF/neoforge.mods.toml",
                b"x" * (self.compat_kit.MAX_MOD_METADATA_BYTES + 1),
            )
            archive.writestr("samplemod/Recipe.class", b"recipe")

        with self.assertRaisesRegex(ValueError, "mod metadata exceeds"):
            self.compat_kit.scan_jar(
                oversized,
                signature_reader=lambda _: "public class Recipe {}",
            )

    def test_scan_rejects_oversized_class_before_decompression(self):
        oversized = self.root / "oversized-class.jar"
        mods_toml = """
modLoader="javafml"
loaderVersion="[4,)"
license="MIT"

[[mods]]
modId="samplemod"
version="1.2.3"
displayName="Sample Machines"
""".strip()
        with zipfile.ZipFile(
            oversized,
            "w",
            compression=zipfile.ZIP_DEFLATED,
        ) as archive:
            archive.writestr("META-INF/neoforge.mods.toml", mods_toml)
            archive.writestr(
                "samplemod/recipe/OversizedRecipe.class",
                b"x" * (self.compat_kit.MAX_CLASS_BYTES + 1),
            )

        with self.assertRaisesRegex(ValueError, "class entry exceeds"):
            self.compat_kit.scan_jar(
                oversized,
                signature_reader=lambda _: "public class OversizedRecipe {}",
            )

    def test_scan_rejects_target_replaced_during_inspection(self):
        replacement = self.root / "replacement.jar"
        write_fixture_jar(replacement, version="1.2.4")
        replaced = False

        def replacing_reader(class_name: str):
            nonlocal replaced
            if not replaced:
                replacement.replace(self.jar)
                replaced = True
            return self.signatures(class_name)

        with self.assertRaisesRegex(ValueError, "target jar changed during scan"):
            self.compat_kit.scan_jar(
                self.jar,
                signature_reader=replacing_reader,
            )

    def test_audit_validation_rejects_candidate_bucket_drift(self):
        audit = self.source_audit()
        candidate = audit["candidates"]["recipe_classes"].pop(0)
        audit["candidates"]["station_classes"].append(candidate)

        with self.assertRaisesRegex(ValueError, "candidate bucket mismatch"):
            self.compat_kit._validate_audit(audit)

    def test_audit_validation_binds_risk_evidence_to_recipe_candidates(self):
        audit = self.source_audit()
        audit["risks"][0]["evidence"] = ["not.a.Candidate#getChance"]

        with self.assertRaisesRegex(
            ValueError,
            "risk evidence owner is not an audited recipe candidate",
        ):
            self.compat_kit._validate_audit(audit)

    def test_decide_outputs_strict_needs_decision_contract_and_actions(self):
        audit = self.compat_kit.scan_jar(
            self.jar,
            signature_reader=self.signatures,
        )

        contract, actions = self.compat_kit.decide_audit(audit)

        self.assertEqual(1, contract["schema"])
        self.assertEqual("auto_storage_compat_contract", contract["kind"])
        self.assertEqual(audit["target"], contract["target"])
        self.assertRegex(
            contract["source_recipe_inventory_sha256"],
            r"^[0-9a-f]{64}$",
        )
        self.assertTrue(contract["families"])
        self.assertTrue(all(family["status"] == "needs_decision" for family in contract["families"]))
        self.assertIn("samplemod.recipe.ChanceRecipe", actions)
        self.assertIn("chance_output", actions)
        self.assertIn("consumed inputs", actions)
        self.assertIn("complete outputs", actions)
        self.compat_kit.validate_contract(contract, require_complete=False)
        with self.assertRaisesRegex(ValueError, "unresolved"):
            self.compat_kit.validate_contract(contract, require_complete=True)

    def test_decide_disambiguates_normalization_colliding_recipe_class_names(self):
        audit = self.compat_kit.scan_jar(
            self.jar,
            signature_reader=self.signatures,
        )
        prototype = dict(audit["candidates"]["recipe_classes"][0])
        for class_name in ("samplemod.a_b.Recipe", "samplemod.a.b.Recipe"):
            duplicate = dict(prototype)
            duplicate["class"] = class_name
            audit["candidates"]["recipe_classes"].append(duplicate)

        contract, _ = self.compat_kit.decide_audit(audit)

        ids = [family["id"] for family in contract["families"]]
        self.assertEqual(len(ids), len(set(ids)))
        colliding_ids = {
            family["class"]: family["id"]
            for family in contract["families"]
            if family["class"] in {"samplemod.a_b.Recipe", "samplemod.a.b.Recipe"}
        }
        self.assertNotEqual(
            colliding_ids["samplemod.a_b.Recipe"],
            colliding_ids["samplemod.a.b.Recipe"],
        )
        for family_id in colliding_ids.values():
            self.assertRegex(family_id, r"^recipe_[0-9a-f]+$")

    def test_contract_validation_rejects_unknown_keys_and_incomplete_acceptance(self):
        audit = self.compat_kit.scan_jar(
            self.jar,
            signature_reader=self.signatures,
        )
        contract, _ = self.compat_kit.decide_audit(audit)
        contract["unexpected"] = True
        with self.assertRaisesRegex(ValueError, "unknown keys.*unexpected"):
            self.compat_kit.validate_contract(contract, require_complete=False)

        del contract["unexpected"]
        family = contract["families"][0]
        family["status"] = "accepted"
        with self.assertRaisesRegex(ValueError, "accepted family.*station"):
            self.compat_kit.validate_contract(contract, require_complete=False)

    def test_contract_validation_rejects_an_omitted_audited_recipe_candidate(self):
        audit = self.compat_kit.scan_jar(
            self.jar,
            signature_reader=self.signatures,
        )
        contract = self.accepted_contract()
        contract["families"] = [
            family
            for family in contract["families"]
            if family["class"] != "samplemod.recipe.ChanceRecipe"
        ]
        contract["source_recipe_inventory_sha256"] = (
            self.compat_kit._recipe_inventory_sha256(
                family["class"] for family in contract["families"]
            )
        )

        with self.assertRaisesRegex(
            ValueError,
            "contract families do not match audited recipe candidates",
        ):
            self.compat_kit.validate_contract(
                contract,
                require_complete=True,
                source_audit=audit,
            )

    def test_complete_contract_preserves_audited_family_risks(self):
        audit = self.source_audit()
        contract = self.accepted_contract()
        family = next(
            family for family in contract["families"] if family["risks"]
        )
        family["risks"] = []

        with self.assertRaisesRegex(
            ValueError,
            "family risks do not match source audit",
        ):
            self.compat_kit.validate_contract(
                contract,
                require_complete=True,
                source_audit=audit,
            )

    def test_contract_station_rates_match_runtime_categories(self):
        audit = self.source_audit()
        mutations = (
            ("process", 0, "process station variants require positive rates"),
            ("instant", 1, "instant station variants require zero rates"),
        )
        for category, numerator, expected in mutations:
            with self.subTest(category=category):
                contract = self.accepted_contract()
                family = next(
                    family
                    for family in contract["families"]
                    if family["status"] == "accepted"
                )
                family["station"]["category"] = category
                family["station"]["variants"][0]["rate"]["numerator"] = numerator
                with self.assertRaisesRegex(ValueError, expected):
                    self.compat_kit.validate_contract(
                        contract,
                        require_complete=True,
                        source_audit=audit,
                    )

    def test_accepted_family_can_declare_no_resource_costs(self):
        audit = self.source_audit()
        contract = self.accepted_contract()
        family = next(
            family
            for family in contract["families"]
            if family["status"] == "accepted"
        )
        family["costs"] = []

        self.compat_kit.validate_contract(
            contract,
            require_complete=True,
            source_audit=audit,
        )

    def test_contract_validation_rejects_malformed_nested_semantics(self):
        mutations = (
            ("station", "invalid", "station must be an object"),
            ("inputs", "invalid", "inputs must be a list"),
            (
                "outputs",
                [{"role": "primary", "resource_kind": "item", "amount": 0}],
                "amount must be positive",
            ),
            (
                "costs",
                [{"resource_kind": "fuel", "amount": 1, "unknown": True}],
                "unknown keys",
            ),
            (
                "inputs",
                [
                    {
                        "role": "consume",
                        "resource_kind": "item",
                        "selector": "recipe.input",
                    }
                ],
                "requires role, resource_kind, and amount",
            ),
            (
                "costs",
                [
                    {
                        "resource_kind": "fuel",
                        "selector": "recipe.fuel",
                    }
                ],
                "requires resource_kind and amount",
            ),
        )
        for field, value, expected in mutations:
            with self.subTest(field=field):
                contract = self.accepted_contract()
                accepted = next(
                    family
                    for family in contract["families"]
                    if family["status"] == "accepted"
                )
                accepted[field] = value
                with self.assertRaisesRegex(ValueError, expected):
                    self.compat_kit.validate_contract(
                        contract,
                        require_complete=True,
                    )

    def test_diff_reports_only_changed_evidence_and_contract_impact(self):
        old = self.compat_kit.scan_jar(
            self.jar,
            signature_reader=self.signatures,
        )
        new_jar = self.root / "samplemod-new.jar"
        write_fixture_jar(new_jar, version="1.3.0", extra_class="samplemod.recipe.PolishingRecipe")

        def new_signatures(class_name: str) -> str:
            if class_name == "samplemod.recipe.PolishingRecipe":
                return (
                    "public final class samplemod.recipe.PolishingRecipe "
                    "implements net.minecraft.world.item.crafting.Recipe { }"
                )
            return self.signatures(class_name)

        new = self.compat_kit.scan_jar(
            new_jar,
            signature_reader=new_signatures,
        )
        delta = self.compat_kit.diff_audits(old, new)

        self.assertEqual("1.2.3", delta["from_version"])
        self.assertEqual("1.3.0", delta["to_version"])
        self.assertEqual(
            ["samplemod.recipe.PolishingRecipe"],
            delta["recipe_classes"]["added"],
        )
        self.assertEqual([], delta["recipe_classes"]["removed"])
        self.assertTrue(delta["contract_affected"])
        self.assertNotIn("samplemod.Unrelated", json.dumps(delta))

    def test_diff_requires_contract_review_when_artifact_bytes_change(self):
        old = self.compat_kit.scan_jar(
            self.jar,
            signature_reader=self.signatures,
        )
        new_jar = self.root / "samplemod-implementation-change.jar"
        write_fixture_jar(new_jar, version="1.2.4")
        new = self.compat_kit.scan_jar(
            new_jar,
            signature_reader=self.signatures,
        )

        delta = self.compat_kit.diff_audits(old, new)

        self.assertTrue(delta["contract_affected"])
        for bucket in (
            "recipe_classes",
            "resource_apis",
            "station_classes",
        ):
            self.assertEqual(
                {"added": [], "removed": [], "changed": []},
                delta[bucket],
            )
        self.assertEqual({"added": [], "removed": []}, delta["risks"])

    def source_audit(self) -> dict:
        return self.compat_kit.scan_jar(
            self.jar,
            signature_reader=self.signatures,
        )

    def accepted_contract(self) -> dict:
        audit = self.source_audit()
        contract, _ = self.compat_kit.decide_audit(audit)
        contract["target"]["dependency"] = "com.example:samplemod:1.2.3"
        contract["target"]["repositories"] = [
            "https://repo.example.com/releases"
        ]
        contract["target"]["runtime_dependencies"] = []
        for family in contract["families"]:
            if family["class"] == "samplemod.recipe.ChanceRecipe":
                family["status"] = "rejected"
                family["decision"] = "Random output cannot be represented deterministically."
                continue
            family.update(
                {
                    "status": "accepted",
                    "recipe_type": "samplemod:crushing",
                    "station": {
                        "descriptor_id": "auto_storage:samplemod_crusher",
                        "category": "process",
                        "variants": [
                            {
                                "item": "samplemod:crusher",
                                "rate": {"numerator": 1, "denominator": 1},
                            }
                        ],
                    },
                    "inputs": [
                        {
                            "role": "consume",
                            "resource_kind": "item",
                            "amount": 1,
                            "selector": "recipe.input",
                        }
                    ],
                    "outputs": [
                        {
                            "role": "primary",
                            "resource_kind": "item",
                            "amount": "recipe.output.count",
                            "selector": "recipe.output",
                        }
                    ],
                    "costs": [
                        {
                            "resource_kind": "auto_storage:station_work",
                            "amount": "recipe.processing_time",
                        }
                    ],
                    "decision": "Exact single-input output with bounded processing time.",
                }
            )
        contract["verification"] = {
            "fixture": "samplemodFixture",
            "expected_game_tests": 1,
            "game_test_task": "runSamplemodGameTestServer",
            "gradle_tasks": [
                "compileCompatSamplemodJava",
                "runSamplemodGameTestServer",
            ],
            "checks": list(self.compat_kit.REQUIRED_VERIFICATION_CHECKS),
            "evidence": {
                check: [
                    {
                        "task": "runSamplemodGameTestServer",
                        "source": "**/SamplemodIntegrationGameTests.java",
                        "marker": "helper.succeed();",
                    }
                ]
                for check in self.compat_kit.REQUIRED_VERIFICATION_CHECKS
            },
        }
        self.compat_kit.validate_contract(
            contract,
            require_complete=True,
            source_audit=audit,
        )
        return contract

    def addon_contract(self) -> dict:
        contract = self.accepted_contract()
        contract["verification"]["fixture"] = "main"
        contract["verification"]["game_test_task"] = "runGameTestServer"
        contract["verification"]["gradle_tasks"] = [
            "build",
            "runGameTestServer",
        ]
        contract["verification"]["evidence"] = {
            check: [
                {
                    "task": "runGameTestServer",
                    "source": "**/SamplemodIntegrationGameTests.java",
                    "marker": "helper.succeed();",
                }
            ]
            for check in self.compat_kit.REQUIRED_VERIFICATION_CHECKS
        }
        self.compat_kit.validate_contract(
            contract,
            require_complete=True,
            source_audit=self.source_audit(),
        )
        return contract

    def test_bundled_scaffold_is_descriptor_owned_fail_closed_and_drift_checked(self):
        contract = self.accepted_contract()
        output_root = self.root / "bundled"

        generated = self.compat_kit.scaffold_bundled(
            contract,
            output_root,
            source_audit=self.source_audit(),
        )

        expected = {
            "src/compat/samplemod/compat-module.json",
            "src/compat/samplemod/java/com/swear/autostorage/compat/samplemod/SamplemodCompat.java",
            "src/compat/samplemod/java/com/swear/autostorage/compat/samplemod/SamplemodCompatModule.java",
            "src/samplemodFixture/java/com/swear/autostorage/fixture/samplemod/SamplemodFixtureMod.java",
            "src/samplemodFixture/java/com/swear/autostorage/fixture/samplemod/SamplemodIntegrationGameTests.java",
            "src/samplemodFixture/resources/META-INF/neoforge.mods.toml",
            "src/samplemodFixture/resources/data/auto_storage_samplemod_fixture/structure/craftingtests.platform.nbt",
            "src/compat/samplemod/.compat-kit-manifest.json",
        }
        self.assertEqual(
            expected,
            {path.relative_to(output_root).as_posix() for path in generated},
        )
        descriptor = json.loads(
            (output_root / "src/compat/samplemod/compat-module.json").read_text()
        )
        self.assertEqual(["samplemod"], descriptor["requires"])
        self.assertEqual(
            ["com.example:samplemod:1.2.3"],
            descriptor["dependencies"],
        )
        self.assertEqual(
            ["com.example:samplemod:1.2.3"],
            descriptor["runtimeDependencies"],
        )
        self.assertEqual(
            ["https://repo.example.com/releases"],
            descriptor["repositories"],
        )
        self.assertEqual(
            {
                "dependency": "com.example:samplemod:1.2.3",
                "sha256": contract["source_audit_sha256"],
            },
            descriptor["auditArtifact"],
        )
        self.assertEqual(1, descriptor["expectedTests"])
        module = (
            output_root
            / "src/compat/samplemod/java/com/swear/autostorage/compat/samplemod/"
            "SamplemodCompatModule.java"
        ).read_text()
        adapter = (
            output_root
            / "src/compat/samplemod/java/com/swear/autostorage/compat/samplemod/"
            "SamplemodCompat.java"
        ).read_text()
        fixture = (
            output_root
            / "src/samplemodFixture/java/com/swear/autostorage/fixture/samplemod/"
            "SamplemodIntegrationGameTests.java"
        ).read_text()
        self.assertEqual(1, module.count("context.register("))
        self.assertIn("SamplemodCompat.register(MACHINES, RECIPES)", module)
        self.assertIn("throw new IllegalStateException", adapter)
        self.assertIn("compat-kit scaffold is intentionally RED", adapter)
        for check in self.compat_kit.REQUIRED_VERIFICATION_CHECKS:
            self.assertIn(check, fixture)

        regenerated = self.compat_kit.scaffold_bundled(
            contract,
            output_root,
            source_audit=self.source_audit(),
        )
        self.assertEqual(generated, regenerated)
        descriptor_path = output_root / "src/compat/samplemod/compat-module.json"
        descriptor_path.write_text(descriptor_path.read_text() + "\n")
        with self.assertRaisesRegex(ValueError, "generated file drift"):
            self.compat_kit.scaffold_bundled(
                contract,
                output_root,
                source_audit=self.source_audit(),
            )

    def test_external_scaffold_is_api_only_and_has_reusable_ci(self):
        contract = self.addon_contract()
        output = self.root / "samplemod-auto-storage"

        generated = self.compat_kit.scaffold_addon(
            contract,
            output,
            source_audit=self.source_audit(),
        )

        relative = {path.relative_to(output).as_posix() for path in generated}
        for required in (
            "build.gradle",
            "compat/audit.json",
            "gradle.properties",
            "settings.gradle",
            "gradlew",
            "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties",
            ".github/workflows/ci.yml",
            "src/main/java/com/example/samplemodautostorage/SamplemodAutoStorageAddon.java",
            "src/main/java/com/example/samplemodautostorage/SamplemodCompat.java",
            "src/main/java/com/example/samplemodautostorage/SamplemodIntegrationGameTests.java",
            "src/main/resources/data/samplemod_auto_storage/structure/craftingtests.platform.nbt",
            ".compat-kit-manifest.json",
        ):
            self.assertIn(required, relative)
        build = (output / "build.gradle").read_text()
        entrypoint = (
            output
            / "src/main/java/com/example/samplemodautostorage/"
            "SamplemodAutoStorageAddon.java"
        ).read_text()
        workflow = (output / ".github/workflows/ci.yml").read_text()
        self.assertIn(
            'compileOnly("com.swear.autostorage:auto_storage:${auto_storage_version}:api")',
            build,
        )
        self.assertIn(
            'compileOnly("com.example:samplemod:1.2.3") '
            "{ transitive = false }",
            build,
        )
        self.assertIn(
            'runtimeOnly("com.example:samplemod:1.2.3") '
            "{ transitive = false }",
            build,
        )
        self.assertIn(
            'runtimeOnly("vazkii.patchouli:Patchouli:${patchouli_version}")',
            build,
        )
        self.assertIn(
            'url = uri("https://maven.blamejared.com")',
            build,
        )
        self.assertIn("compatKitTargetArtifact", build)
        self.assertIn("verifyCompatKitTargetArtifact", build)
        self.assertIn(contract["source_audit_sha256"], build)
        self.assertIn(
            'url = uri("https://repo.example.com/releases")',
            build,
        )
        self.assertLess(
            build.index('url = uri("https://repo.example.com/releases")'),
            build.index("mavenCentral"),
        )
        self.assertIn('includeGroup("com.example")', build)
        self.assertNotIn('excludeGroup("com.example")', build)
        self.assertIn('excludeGroup("vazkii.patchouli")', build)
        self.assertIn('excludeGroup("com.swear.autostorage")', build)
        self.assertIn('includeGroup("vazkii.patchouli")', build)
        self.assertIn('includeGroup("com.swear.autostorage")', build)
        self.assertIn("gameTestServer", build)
        self.assertNotIn("src/main", build)
        self.assertEqual(1, entrypoint.count("AutoStorageAddon.register("))
        self.assertIn("./gradlew build", workflow)
        self.assertIn("./gradlew runGameTestServer", workflow)
        self.assertIn(
            "compat-kit verify compat/contract.json "
            "--audit compat/audit.json --addon .",
            workflow,
        )
        self.assertTrue((output / "compat/audit.json").is_file())
        self.assertNotIn("implementation project", build)

    def test_external_scaffold_preflights_all_drift_before_writing(self):
        contract = self.addon_contract()
        output = self.root / "samplemod-auto-storage"
        output.mkdir()
        build = output / "build.gradle"
        build.write_text("existing project\n")

        with self.assertRaisesRegex(
            ValueError,
            "generated file drift: build.gradle",
        ):
            self.compat_kit.scaffold_addon(
                contract,
                output,
                source_audit=self.source_audit(),
            )

        self.assertEqual(
            ["build.gradle"],
            [
                path.relative_to(output).as_posix()
                for path in sorted(output.rglob("*"))
                if path.is_file()
            ],
        )
        self.assertEqual("existing project\n", build.read_text())

    def test_external_scaffold_preflights_conflicting_ancestor_before_writing(self):
        contract = self.addon_contract()
        output = self.root / "samplemod-auto-storage"
        output.mkdir()
        ancestor = output / "src"
        ancestor.write_text("existing file\n")

        with self.assertRaisesRegex(
            ValueError,
            "generated path parent is not a directory: src",
        ):
            self.compat_kit.scaffold_addon(
                contract,
                output,
                source_audit=self.source_audit(),
            )

        self.assertEqual(
            ["src"],
            [
                path.relative_to(output).as_posix()
                for path in sorted(output.rglob("*"))
                if path.is_file()
            ],
        )
        self.assertEqual("existing file\n", ancestor.read_text())

    def test_external_scaffold_rejects_symlinked_ancestor_before_writing(self):
        contract = self.addon_contract()
        output = self.root / "samplemod-auto-storage"
        output.mkdir()
        external = self.root / "external"
        external.mkdir()
        (output / "src").symlink_to(external, target_is_directory=True)

        with self.assertRaisesRegex(
            ValueError,
            "generated path parent is a symlink: src",
        ):
            self.compat_kit.scaffold_addon(
                contract,
                output,
                source_audit=self.source_audit(),
            )

        self.assertEqual([], list(external.iterdir()))

    def test_external_scaffold_rejects_symlink_above_output_root(self):
        contract = self.addon_contract()
        external = self.root / "external"
        external.mkdir()
        link = self.root / "link"
        link.symlink_to(external, target_is_directory=True)
        output = link / "samplemod-auto-storage"

        with self.assertRaisesRegex(
            ValueError,
            "generated path ancestor is a symlink",
        ):
            self.compat_kit.scaffold_addon(
                contract,
                output,
                source_audit=self.source_audit(),
            )

        self.assertEqual([], list(external.iterdir()))

    def test_external_scaffold_normalizes_output_before_symlink_preflight(self):
        contract = self.addon_contract()
        external = self.root / "external"
        external.mkdir()
        link = self.root / "link"
        link.symlink_to(external, target_is_directory=True)
        output = self.root / "missing" / ".." / "link" / "samplemod-auto-storage"

        with self.assertRaisesRegex(
            ValueError,
            "generated path ancestor is a symlink",
        ):
            self.compat_kit.scaffold_addon(
                contract,
                output,
                source_audit=self.source_audit(),
            )

        self.assertFalse((self.root / "missing").exists())
        self.assertEqual([], list(external.iterdir()))

    def test_external_scaffold_repairs_reused_launcher_modes(self):
        contract = self.addon_contract()
        output = self.root / "samplemod-auto-storage"
        source_audit = self.source_audit()
        self.compat_kit.scaffold_addon(
            contract,
            output,
            source_audit=source_audit,
        )
        launchers = (
            output / "gradlew",
            output / "tools/compat-kit/compat-kit",
        )
        for launcher in launchers:
            launcher.chmod(0o644)

        self.compat_kit.scaffold_addon(
            contract,
            output,
            source_audit=source_audit,
        )

        for launcher in launchers:
            self.assertEqual(0o755, launcher.stat().st_mode & 0o777)

    def test_external_scaffold_bounds_auto_storage_to_current_minor(self):
        contract = self.addon_contract()
        output = self.root / "samplemod-auto-storage"

        self.compat_kit.scaffold_addon(
            contract,
            output,
            source_audit=self.source_audit(),
        )

        metadata = (
            output / "src/main/resources/META-INF/neoforge.mods.toml"
        ).read_text()
        self.assertIn('versionRange="[0.3.0,0.4)"', metadata)
        self.assertNotIn('versionRange="[0.3.0,1)"', metadata)

    def test_scaffolds_preserve_reviewed_target_runtime_dependencies(self):
        contract = self.addon_contract()
        contract["target"]["runtime_dependencies"] = [
            "org.example:samplemod-runtime:4.5.6"
        ]
        output = self.root / "samplemod-runtime-addon"

        self.compat_kit.scaffold_addon(
            contract,
            output,
            source_audit=self.source_audit(),
        )

        build = (output / "build.gradle").read_text()
        self.assertIn(
            'runtimeOnly("org.example:samplemod-runtime:4.5.6") '
            "{ transitive = false }",
            build,
        )
        self.assertIn('includeGroup("org.example")', build)
        self.assertIn('excludeGroup("org.example")', build)

        bundled = self.root / "samplemod-runtime-bundled"
        contract["verification"]["fixture"] = "samplemodFixture"
        contract["verification"]["game_test_task"] = "runSamplemodGameTestServer"
        contract["verification"]["gradle_tasks"] = [
            "compileCompatSamplemodJava",
            "runSamplemodGameTestServer",
        ]
        for records in contract["verification"]["evidence"].values():
            for record in records:
                record["task"] = "runSamplemodGameTestServer"
        self.compat_kit.scaffold_bundled(
            contract,
            bundled,
            source_audit=self.source_audit(),
        )
        descriptor = json.loads(
            (bundled / "src/compat/samplemod/compat-module.json").read_text()
        )
        self.assertEqual(
            [
                "com.example:samplemod:1.2.3",
                "org.example:samplemod-runtime:4.5.6",
            ],
            descriptor["runtimeDependencies"],
        )

    def test_external_scaffold_excludes_runtime_groups_from_central_without_repositories(self):
        contract = self.addon_contract()
        contract["target"]["repositories"] = []
        contract["target"]["runtime_dependencies"] = [
            "org.runtime:samplemod-runtime:4.5.6"
        ]
        output = self.root / "central-target-runtime-addon"

        self.compat_kit.scaffold_addon(
            contract,
            output,
            source_audit=self.source_audit(),
        )

        build = (output / "build.gradle").read_text()
        self.assertIn('excludeGroup("org.runtime")', build)
        self.assertNotIn('excludeGroup("com.example")', build)

    def test_contract_validation_rejects_control_characters_in_dependencies(self):
        cases = (
            ("dependency", "com.example:samplemod:1.2.3\ninvalid"),
            (
                "runtime_dependencies",
                ["org.example:samplemod-runtime:4.5.6\tinvalid"],
            ),
        )
        for field, value in cases:
            with self.subTest(field=field):
                contract = self.addon_contract()
                contract["target"][field] = value
                with self.assertRaisesRegex(
                    ValueError,
                    "must not contain control characters",
                ):
                    self.compat_kit.validate_contract(
                        contract,
                        require_complete=True,
                        source_audit=self.source_audit(),
                    )

    def test_external_scaffold_escapes_target_display_name_as_toml(self):
        contract = self.addon_contract()
        audit = self.source_audit()
        display_name = 'Sample "Quoted" \\\\ Machines\nSecond Line'
        contract["target"]["display_name"] = display_name
        audit["target"]["display_name"] = display_name
        output = self.root / "quoted-addon"

        self.compat_kit.scaffold_addon(
            contract,
            output,
            source_audit=audit,
        )

        metadata = tomllib.loads(
            (output / "src/main/resources/META-INF/neoforge.mods.toml").read_text()
        )
        self.assertEqual(
            display_name + " Auto Storage Integration",
            metadata["mods"][0]["displayName"],
        )

    def test_external_scaffold_escapes_del_as_toml(self):
        contract = self.addon_contract()
        audit = self.source_audit()
        display_name = "Sample\u007fMachines"
        contract["target"]["display_name"] = display_name
        audit["target"]["display_name"] = display_name
        output = self.root / "del-addon"

        self.compat_kit.scaffold_addon(
            contract,
            output,
            source_audit=audit,
        )

        metadata_path = output / "src/main/resources/META-INF/neoforge.mods.toml"
        self.assertIn("\\u007f", metadata_path.read_text())
        metadata = tomllib.loads(metadata_path.read_text())
        self.assertEqual(
            display_name + " Auto Storage Integration",
            metadata["mods"][0]["displayName"],
        )

    def test_external_scaffold_escapes_reviewed_values_as_groovy_strings(self):
        contract = self.addon_contract()
        contract["target"]["repositories"] = [
            'https://repo.example.com/$channel/\\"quoted\\"'
        ]
        output = self.root / "groovy-escaped-addon"

        self.compat_kit.scaffold_addon(
            contract,
            output,
            source_audit=self.source_audit(),
        )

        build = (output / "build.gradle").read_text()
        self.assertIn(
            'url = uri("https://repo.example.com/\\$channel/'
            '\\\\\\\"quoted\\\\\\\"")',
            build,
        )

    def test_bundled_scaffold_escapes_target_description_as_toml(self):
        contract = self.accepted_contract()
        audit = self.source_audit()
        display_name = "Sample ''' Machines\\Control\nSecond Line"
        contract["target"]["display_name"] = display_name
        audit["target"]["display_name"] = display_name
        output = self.root / "quoted-bundled"

        self.compat_kit.scaffold_bundled(
            contract,
            output,
            source_audit=audit,
        )

        metadata = tomllib.loads(
            (
                output
                / "src/samplemodFixture/resources/META-INF/neoforge.mods.toml"
            ).read_text()
        )
        self.assertEqual(
            f"Compat Kit generated RED fixture for {display_name}.",
            metadata["mods"][0]["description"],
        )

    def test_external_scaffold_rejects_bundled_only_verification_tasks(self):
        with self.assertRaisesRegex(
            ValueError,
            "addon verification tasks must be build and runGameTestServer",
        ):
            self.compat_kit.scaffold_addon(
                self.accepted_contract(),
                self.root / "addon",
                source_audit=self.source_audit(),
            )

    def test_bundled_scaffold_rejects_unsafe_fixture_paths(self):
        contract = self.accepted_contract()
        contract["verification"]["fixture"] = "../../outside"

        with self.assertRaisesRegex(
            ValueError,
            "bundled fixture must be a Java-safe identifier ending in Fixture",
        ):
            self.compat_kit.scaffold_bundled(
                contract,
                self.root / "bundled",
                source_audit=self.source_audit(),
            )
        self.assertFalse((self.root / "outside").exists())

    def test_bundled_scaffold_binds_game_test_task_to_fixture(self):
        contract = self.accepted_contract()
        contract["verification"]["game_test_task"] = (
            "runFarmersDelightGameTestServer"
        )
        contract["verification"]["gradle_tasks"] = [
            "compileCompatSamplemodJava",
            "runFarmersDelightGameTestServer",
        ]
        for records in contract["verification"]["evidence"].values():
            for record in records:
                record["task"] = "runFarmersDelightGameTestServer"

        with self.assertRaisesRegex(
            ValueError,
            "bundled game_test_task must match fixture",
        ):
            self.compat_kit.scaffold_bundled(
                contract,
                self.root / "bundled",
                source_audit=self.source_audit(),
            )

    def test_bundled_scaffold_rejects_normalized_identifier_collision(self):
        contract = self.accepted_contract()
        audit = self.source_audit()
        contract["target"]["mod_id"] = "foo__bar"
        audit["target"]["mod_id"] = "foo__bar"
        output = self.root / "bundled"
        existing = output / "src/compat/foo_bar/compat-module.json"
        existing.parent.mkdir(parents=True)
        existing.write_text(
            json.dumps(
                {
                    "schema": 1,
                    "id": "auto_storage:foo_bar",
                    "entrypoint": (
                        "com.swear.autostorage.compat.foobar."
                        "FooBarCompatModule"
                    ),
                    "requires": ["foo_bar"],
                    "side": "both",
                    "sourceSet": "compatFooBar",
                    "fixture": "fooBarFixture",
                    "expectedTests": 1,
                    "dependencies": ["com.example:foo_bar:1.0.0"],
                    "runtimeDependencies": ["com.example:foo_bar:1.0.0"],
                    "repositories": ["https://repo.example.com/releases"],
                    "auditArtifact": {
                        "dependency": "com.example:foo_bar:1.0.0",
                        "sha256": "0" * 64,
                    },
                }
            )
        )

        with self.assertRaisesRegex(
            ValueError,
            "bundled compatibility identifier collision",
        ):
            self.compat_kit.scaffold_bundled(
                contract,
                output,
                source_audit=audit,
            )

        self.assertFalse((output / "src/compat/foo__bar").exists())

    def test_bundled_collision_scan_rejects_symlinked_existing_module(self):
        contract = self.accepted_contract()
        output = self.root / "bundled"
        external = self.root / "external-module"
        external.mkdir()
        (external / "compat-module.json").write_text("{}")
        compat_root = output / "src/compat"
        compat_root.mkdir(parents=True)
        (compat_root / "external").symlink_to(
            external,
            target_is_directory=True,
        )

        with self.assertRaisesRegex(
            ValueError,
            "existing compatibility module is a symlink",
        ):
            self.compat_kit.scaffold_bundled(
                contract,
                output,
                source_audit=self.source_audit(),
            )

        self.assertFalse((output / "src/compat/samplemod").exists())

    def test_bundled_scaffold_rejects_java_keyword_package_segment(self):
        contract = self.accepted_contract()
        audit = self.source_audit()
        contract["target"]["mod_id"] = "class"
        audit["target"]["mod_id"] = "class"

        with self.assertRaisesRegex(ValueError, "invalid Java package segment"):
            self.compat_kit.scaffold_bundled(
                contract,
                self.root / "bundled",
                source_audit=audit,
            )

    def test_scaffold_rejects_unresolved_or_semantically_incomplete_contracts(self):
        audit = self.compat_kit.scan_jar(
            self.jar,
            signature_reader=self.signatures,
        )
        unresolved, _ = self.compat_kit.decide_audit(audit)
        with self.assertRaisesRegex(ValueError, "unresolved"):
            self.compat_kit.scaffold_bundled(
                unresolved,
                self.root / "bundled",
                source_audit=audit,
            )
        with self.assertRaisesRegex(ValueError, "unresolved"):
            self.compat_kit.scaffold_addon(
                unresolved,
                self.root / "addon",
                source_audit=audit,
            )

    def test_schemas_are_versioned_strict_and_cover_all_machine_readable_documents(self):
        schema_root = ROOT / "tools/compat-kit/schema"
        expected = {
            "compat-audit.schema.json": "auto_storage_compat_audit",
            "compat-contract.schema.json": "auto_storage_compat_contract",
            "compat-delta.schema.json": "auto_storage_compat_delta",
            "compat-report.schema.json": "auto_storage_compat_report",
        }
        for name, kind in expected.items():
            with self.subTest(schema=name):
                schema = json.loads((schema_root / name).read_text())
                self.assertEqual("https://json-schema.org/draft/2020-12/schema", schema["$schema"])
                self.assertFalse(schema["additionalProperties"])
                self.assertIn("schema", schema["required"])
                self.assertIn("kind", schema["required"])
                self.assertEqual({"const": 1}, schema["properties"]["schema"])
                self.assertEqual({"const": kind}, schema["properties"]["kind"])
        contract_schema = json.loads(
            (schema_root / "compat-contract.schema.json").read_text()
        )
        family_schema = contract_schema["properties"]["families"]["items"]
        verification_schema = contract_schema["properties"]["verification"]
        self.assertFalse(family_schema["additionalProperties"])
        self.assertFalse(verification_schema["additionalProperties"])
        self.assertEqual(
            {
                "fixture",
                "expected_game_tests",
                "game_test_task",
                "gradle_tasks",
                "checks",
                "evidence",
            },
            set(verification_schema["required"]),
        )
        self.assertIn(
            "source_recipe_inventory_sha256",
            contract_schema["required"],
        )
        self.assertIn(
            "runtime_dependencies",
            contract_schema["properties"]["target"]["properties"],
        )
        station_schema = family_schema["properties"]["station"]["oneOf"][1]
        station_rate_rules = {
            rule["if"]["properties"]["category"]["const"]:
            rule["then"]["properties"]["variants"]["items"]["properties"]["rate"][
                "properties"
            ]["numerator"]
            for rule in station_schema["allOf"]
        }
        self.assertEqual({"minimum": 1}, station_rate_rules["process"])
        self.assertEqual({"const": 0}, station_rate_rules["instant"])
        family_status_rules = {
            rule["if"]["properties"]["status"]["const"]: rule["then"][
                "properties"
            ]
            for rule in family_schema["allOf"]
        }
        accepted = family_status_rules["accepted"]
        self.assertEqual(
            {"type": "string", "minLength": 1},
            accepted["recipe_type"],
        )
        self.assertEqual({"type": "object"}, accepted["station"])
        self.assertEqual({"minItems": 1}, accepted["inputs"])
        self.assertEqual(1, accepted["outputs"]["minItems"])
        self.assertEqual(
            "primary",
            accepted["outputs"]["contains"]["properties"]["role"]["const"],
        )
        self.assertEqual(
            {"type": "string", "minLength": 1},
            accepted["decision"],
        )
        self.assertEqual(
            {"type": "string", "minLength": 1},
            family_status_rules["rejected"]["decision"],
        )
        audit_schema = json.loads(
            (schema_root / "compat-audit.schema.json").read_text()
        )
        self.assertIn("scanner_format", audit_schema["required"])
        self.assertEqual(
            {"const": self.compat_kit.SCAN_CACHE_VERSION},
            audit_schema["properties"]["scanner_format"],
        )
        self.assertFalse(
            audit_schema["properties"]["candidates"]["additionalProperties"]
        )
        report_schema = json.loads(
            (schema_root / "compat-report.schema.json").read_text()
        )
        checks_schema = report_schema["properties"]["checks"]
        self.assertFalse(checks_schema["items"]["additionalProperties"])
        self.assertEqual(
            len(self.compat_kit.REQUIRED_VERIFICATION_CHECKS),
            checks_schema["minItems"],
        )
        self.assertEqual(checks_schema["minItems"], checks_schema["maxItems"])
        required_report_checks = {
            rule["contains"]["properties"]["id"]["const"]
            for rule in checks_schema["allOf"]
        }
        self.assertEqual(
            set(self.compat_kit.REQUIRED_VERIFICATION_CHECKS),
            required_report_checks,
        )
        self.assertEqual(
            1,
            report_schema["properties"]["commands"]["minItems"],
        )
        addon_commands = report_schema["allOf"][0]["then"]["properties"][
            "commands"
        ]
        self.assertEqual(2, addon_commands["minItems"])
        self.assertEqual(2, addon_commands["maxItems"])
        self.assertEqual(
            ["build", "runGameTestServer"],
            [
                command["contains"]["properties"]["command"]["prefixItems"][1][
                    "const"
                ]
                for command in addon_commands["allOf"]
            ],
        )

    def test_contract_schema_requires_scaffold_inputs_after_family_decisions(self):
        schema = json.loads(
            (
                ROOT / "tools/compat-kit/schema/compat-contract.schema.json"
            ).read_text()
        )

        completion_rule = schema["allOf"][0]
        self.assertEqual(
            "needs_decision",
            completion_rule["if"]["properties"]["families"]["contains"][
                "properties"
            ]["status"]["const"],
        )
        self.assertEqual(
            {
                "mod_id",
                "display_name",
                "version",
                "dependency",
                "repositories",
                "runtime_dependencies",
            },
            set(
                completion_rule["else"]["properties"]["target"]["required"]
            ),
        )
        target = schema["properties"]["target"]["properties"]
        self.assertEqual(
            "^[^\\u0000-\\u001F\\u007F]+$",
            target["dependency"]["pattern"],
        )
        self.assertEqual(
            target["dependency"]["pattern"],
            target["runtime_dependencies"]["items"]["pattern"],
        )
        verification = completion_rule["else"]["properties"]["verification"]
        self.assertEqual(
            {"type": "string", "minLength": 1},
            verification["properties"]["fixture"],
        )
        self.assertEqual(
            {"type": "integer", "minimum": 1},
            verification["properties"]["expected_game_tests"],
        )
        self.assertEqual(
            {"type": "string", "pattern": "^[A-Za-z][A-Za-z0-9]*$"},
            verification["properties"]["game_test_task"],
        )
        self.assertEqual(
            1,
            verification["properties"]["gradle_tasks"]["minItems"],
        )
        checks = verification["properties"]["checks"]
        self.assertEqual(
            len(self.compat_kit.REQUIRED_VERIFICATION_CHECKS),
            checks["minItems"],
        )
        self.assertEqual(checks["minItems"], checks["maxItems"])
        self.assertEqual(
            set(self.compat_kit.REQUIRED_VERIFICATION_CHECKS),
            {
                rule["contains"]["properties"]["id"]["const"]
                if "properties" in rule["contains"]
                else rule["contains"]["const"]
                for rule in checks["allOf"]
            },
        )
        evidence = verification["properties"]["evidence"]
        self.assertFalse(evidence["additionalProperties"])
        self.assertEqual(
            set(self.compat_kit.REQUIRED_VERIFICATION_CHECKS),
            set(evidence["required"]),
        )
        self.assertEqual(
            set(evidence["required"]),
            set(evidence["properties"]),
        )

    def test_report_schema_requires_target_identity(self):
        schema = json.loads(
            (
                ROOT / "tools/compat-kit/schema/compat-report.schema.json"
            ).read_text()
        )

        target = schema["properties"]["target"]
        self.assertFalse(target["additionalProperties"])
        self.assertEqual(
            {"mod_id", "display_name", "version"},
            set(target["required"]),
        )
        self.assertEqual(
            "^[a-z0-9_-]+$",
            target["properties"]["mod_id"]["pattern"],
        )
        self.assertEqual(
            set(self.addon_contract()["target"]),
            set(target["properties"]),
        )

    def test_committed_ae2_contract_covers_every_audited_recipe_candidate(self):
        audit = json.loads(
            (ROOT / "compat/audits/ae2/19.2.17.json").read_text()
        )
        contract = json.loads(
            (ROOT / "compat/contracts/ae2.json").read_text()
        )
        descriptor = json.loads(
            (ROOT / "src/compat/ae2/compat-module.json").read_text()
        )

        audited_recipe_classes = {
            candidate["class"]
            for candidate in audit["candidates"]["recipe_classes"]
        }
        self.assertIn(
            "appeng.recipes.handlers.InscriberRecipe$Ingredients",
            audited_recipe_classes,
        )
        self.assertEqual(
            audited_recipe_classes,
            {family["class"] for family in contract["families"]},
        )
        self.assertEqual(
            audit["artifact"]["sha256"],
            contract["source_audit_sha256"],
        )
        self.assertEqual(
            {
                "dependency": contract["target"]["dependency"],
                "sha256": audit["artifact"]["sha256"],
            },
            descriptor["auditArtifact"],
        )
        self.assertEqual(
            contract["target"]["repositories"],
            descriptor["repositories"],
        )
        self.assertEqual(
            [
                contract["target"]["dependency"],
                *contract["target"]["runtime_dependencies"],
            ],
            descriptor["runtimeDependencies"],
        )
        self.assertEqual(8, contract["verification"]["expected_game_tests"])
        self.assertEqual(8, descriptor["expectedTests"])
        self.assertEqual(
            "AE2 missing-ingredient transaction was not an atomic no-op",
            contract["verification"]["evidence"]["ingredient_shortage_atomic"][0][
                "marker"
            ],
        )
        fixture = (
            ROOT
            / "src/ae2Fixture/java/com/swear/autostorage/fixture/ae2/"
            "Ae2IntegrationGameTests.java"
        ).read_text()
        shortage_body = fixture.split(
            "public static void missing_press_middle_ingredient_is_atomic",
            1,
        )[1].split("\n    @GameTest", 1)[0]
        self.assertNotIn("seedPressInputs", shortage_body)
        self.assertIn("Items.REDSTONE", shortage_body)
        self.assertIn('ae2Item("printed_silicon")', shortage_body)
        self.assertIn('ae2Item("printed_logic_processor")', shortage_body)
        self.assertIn("getResourceAmount", shortage_body)
        self.assertIn("getStationWork", shortage_body)
        self.compat_kit.validate_contract(
            contract,
            require_complete=True,
            source_audit=audit,
        )
        self.assertEqual(
            set(self.compat_kit.REQUIRED_VERIFICATION_CHECKS),
            set(self.compat_kit._verification_evidence(contract, ROOT, "bundled")),
        )

    def test_recipe_family_verification_commands_include_ae2_fixture(self):
        guide = (ROOT / "docs/recipe-family-api.md").read_text()
        fixture_section = guide.split("## Repository verification fixture", 1)[1]
        commands = fixture_section.split("```bash", 1)[1].split("```", 1)[0]

        self.assertIn("./gradlew runAe2GameTestServer", commands)

    def test_verify_runs_declared_commands_and_emits_complete_report(self):
        contract = self.accepted_contract()
        output_root = self.root / "bundled"
        source_audit = self.source_audit()
        self.compat_kit.scaffold_bundled(
            contract,
            output_root,
            source_audit=source_audit,
        )
        adapter = (
            output_root
            / "src/compat/samplemod/java/com/swear/autostorage/compat/samplemod/"
            "SamplemodCompat.java"
        )
        adapter.write_text(
            adapter.read_text().replace(
                'throw new IllegalStateException(\n'
                '                "compat-kit scaffold is intentionally RED: implement crushing_recipe");',
                "machines.getRegistryKey();\n        recipes.getRegistryKey();",
            )
        )
        fixture = (
            output_root
            / "src/samplemodFixture/java/com/swear/autostorage/fixture/samplemod/"
            "SamplemodIntegrationGameTests.java"
        )
        fixture.write_text(
            fixture.read_text().replace(
                'helper.fail("compat-kit scaffold is intentionally RED: " + REQUIRED_CHECKS);',
                "helper.succeed();",
            ).replace(
                "    private static final Set<String> REQUIRED_CHECKS = Set.of(\n"
                + "\n".join(
                    f'            "{check}",'
                    for check in self.compat_kit.REQUIRED_VERIFICATION_CHECKS
                ).rstrip(",")
                + ");\n\n",
                "",
            )
        )
        manifest = output_root / "src/compat/samplemod/.compat-kit-manifest.json"
        manifest_data = json.loads(manifest.read_text())
        for path in (adapter, fixture):
            relative = path.relative_to(output_root).as_posix()
            manifest_data["files"][relative] = hashlib.sha256(path.read_bytes()).hexdigest()
        manifest.write_text(
            json.dumps(manifest_data, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
        )
        stale_build_source = output_root / "build/generated/StaleScaffold.java"
        stale_build_source.parent.mkdir(parents=True)
        stale_build_source.write_text(
            'class StaleScaffold { String value = "compat-kit scaffold is intentionally RED"; }\n'
        )
        commands = []
        world = output_root / "run/world"
        world.mkdir(parents=True)
        (world / "sentinel").write_text("stale")

        def runner(command, cwd):
            self.assertFalse(world.exists())
            commands.append((command, cwd))
            world.mkdir(parents=True)
            (world / "sentinel").write_text("stale")
            output = (
                "All 1 required tests passed :)\n"
                if command[1] == "runSamplemodGameTestServer"
                else "green\n"
            )
            return subprocess.CompletedProcess(command, 0, output, "")

        report = self.compat_kit.verify_contract(
            contract,
            source_audit=source_audit,
            bundled_root=output_root,
            command_runner=runner,
        )

        self.assertEqual("auto_storage_compat_report", report["kind"])
        self.assertEqual("passed", report["status"])
        self.assertEqual(
            list(self.compat_kit.REQUIRED_VERIFICATION_CHECKS),
            [check["id"] for check in report["checks"]],
        )
        self.assertTrue(all(check["status"] == "passed" for check in report["checks"]))
        self.assertEqual(contract["verification"]["gradle_tasks"], [
            command[0][1] for command in commands
        ])
        self.assertTrue(all(command[0][0] == "./gradlew" for command in commands))
        self.assertTrue(all(command[1] == output_root for command in commands))
        self.assertTrue(all(check["evidence"] for check in report["checks"]))

    def test_verify_rejects_missing_check_evidence_or_wrong_gametest_count(self):
        contract = self.accepted_contract()
        contract["verification"]["evidence"].pop("checked_overflow_atomic")
        with self.assertRaisesRegex(ValueError, "verification evidence keys"):
            self.compat_kit.validate_contract(contract, require_complete=True)

        contract = self.accepted_contract()
        output_root = self.root / "bundled"
        source_audit = self.source_audit()
        self.compat_kit.scaffold_bundled(
            contract,
            output_root,
            source_audit=source_audit,
        )
        adapter = (
            output_root
            / "src/compat/samplemod/java/com/swear/autostorage/compat/samplemod/"
            "SamplemodCompat.java"
        )
        adapter.write_text(
            adapter.read_text().replace(
                'throw new IllegalStateException(\n'
                '                "compat-kit scaffold is intentionally RED: implement crushing_recipe");',
                "machines.getRegistryKey();\n        recipes.getRegistryKey();",
            )
        )
        fixture = (
            output_root
            / "src/samplemodFixture/java/com/swear/autostorage/fixture/samplemod/"
            "SamplemodIntegrationGameTests.java"
        )
        fixture.write_text(
            fixture.read_text().replace(
                'helper.fail("compat-kit scaffold is intentionally RED: " + REQUIRED_CHECKS);',
                "helper.succeed();",
            )
        )
        with self.assertRaisesRegex(ValueError, "expected 1 GameTests"):
            self.compat_kit.verify_contract(
                contract,
                source_audit=source_audit,
                bundled_root=output_root,
                command_runner=lambda command, cwd: subprocess.CompletedProcess(
                    command, 0, "All 2 required tests passed :)\n", ""
                ),
            )

    def test_verify_rejects_conflicting_gametest_success_summaries(self):
        contract = self.accepted_contract()
        output_root = self.root / "conflicting-summary"
        source_audit = self.source_audit()
        self.compat_kit.scaffold_bundled(
            contract,
            output_root,
            source_audit=source_audit,
        )
        adapter = (
            output_root
            / "src/compat/samplemod/java/com/swear/autostorage/compat/samplemod/"
            "SamplemodCompat.java"
        )
        adapter.write_text(
            adapter.read_text().replace(
                'throw new IllegalStateException(\n'
                '                "compat-kit scaffold is intentionally RED: implement crushing_recipe");',
                "machines.getRegistryKey();\n        recipes.getRegistryKey();",
            )
        )
        fixture = (
            output_root
            / "src/samplemodFixture/java/com/swear/autostorage/fixture/samplemod/"
            "SamplemodIntegrationGameTests.java"
        )
        fixture.write_text(
            fixture.read_text().replace(
                'helper.fail("compat-kit scaffold is intentionally RED: " + REQUIRED_CHECKS);',
                "helper.succeed();",
            )
        )

        with self.assertRaisesRegex(
            ValueError,
            "conflicting GameTest success summaries",
        ):
            self.compat_kit.verify_contract(
                contract,
                source_audit=source_audit,
                bundled_root=output_root,
                command_runner=lambda command, cwd: subprocess.CompletedProcess(
                    command,
                    0,
                    (
                        "All 1 required tests passed :)\n"
                        "All 0 required tests passed :)\n"
                    ),
                    "",
                ),
            )

    def test_verify_rejects_gametest_marker_outside_annotated_method(self):
        contract = self.accepted_contract()
        for records in contract["verification"]["evidence"].values():
            for record in records:
                record["marker"] = "detached_behavior_marker"
        output_root = self.root / "detached-evidence"
        source_audit = self.source_audit()
        self.compat_kit.scaffold_bundled(
            contract,
            output_root,
            source_audit=source_audit,
        )
        adapter = (
            output_root
            / "src/compat/samplemod/java/com/swear/autostorage/compat/samplemod/"
            "SamplemodCompat.java"
        )
        adapter.write_text(
            adapter.read_text().replace(
                'throw new IllegalStateException(\n'
                '                "compat-kit scaffold is intentionally RED: implement crushing_recipe");',
                "machines.getRegistryKey();\n        recipes.getRegistryKey();",
            )
        )
        fixture = (
            output_root
            / "src/samplemodFixture/java/com/swear/autostorage/fixture/samplemod/"
            "SamplemodIntegrationGameTests.java"
        )
        fixture.write_text(
            fixture.read_text()
            .replace(
                "public final class SamplemodIntegrationGameTests {",
                "public final class SamplemodIntegrationGameTests {\n"
                '    private static final String MARKER = "detached_behavior_marker";',
            )
            .replace(
                'helper.fail("compat-kit scaffold is intentionally RED: " + REQUIRED_CHECKS);',
                "helper.succeed();",
            )
        )

        with self.assertRaisesRegex(
            ValueError,
            "evidence marker is not inside an @GameTest method",
        ):
            self.compat_kit.verify_contract(
                contract,
                source_audit=source_audit,
                bundled_root=output_root,
                command_runner=lambda command, cwd: subprocess.CompletedProcess(
                    command, 0, "All 1 required tests passed :)\n", ""
                ),
            )

    def test_verify_rejects_gametest_marker_only_in_method_declaration(self):
        contract = self.accepted_contract()
        marker = "compat_kit_scaffold_remains_red"
        for records in contract["verification"]["evidence"].values():
            for record in records:
                record["marker"] = marker
        output_root = self.root / "declaration-only-evidence"
        source_audit = self.source_audit()
        self.compat_kit.scaffold_bundled(
            contract,
            output_root,
            source_audit=source_audit,
        )
        adapter = (
            output_root
            / "src/compat/samplemod/java/com/swear/autostorage/compat/samplemod/"
            "SamplemodCompat.java"
        )
        adapter.write_text(
            adapter.read_text().replace(
                'throw new IllegalStateException(\n'
                '                "compat-kit scaffold is intentionally RED: implement crushing_recipe");',
                "machines.getRegistryKey();\n        recipes.getRegistryKey();",
            )
        )
        fixture = (
            output_root
            / "src/samplemodFixture/java/com/swear/autostorage/fixture/samplemod/"
            "SamplemodIntegrationGameTests.java"
        )
        fixture.write_text(
            fixture.read_text().replace(
                'helper.fail("compat-kit scaffold is intentionally RED: " + REQUIRED_CHECKS);',
                "helper.succeed();",
            )
        )

        with self.assertRaisesRegex(
            ValueError,
            "evidence marker is not inside an @GameTest method",
        ):
            self.compat_kit.verify_contract(
                contract,
                source_audit=source_audit,
                bundled_root=output_root,
                command_runner=lambda command, cwd: subprocess.CompletedProcess(
                    command, 0, "All 1 required tests passed :)\n", ""
                ),
            )

    def test_verify_ignores_commented_gametest_annotations(self):
        contract = self.accepted_contract()
        marker = "commented_annotation_marker();"
        for records in contract["verification"]["evidence"].values():
            for record in records:
                record["marker"] = marker
        output_root = self.root / "commented-annotation-evidence"
        source_audit = self.source_audit()
        self.compat_kit.scaffold_bundled(
            contract,
            output_root,
            source_audit=source_audit,
        )
        adapter = (
            output_root
            / "src/compat/samplemod/java/com/swear/autostorage/compat/samplemod/"
            "SamplemodCompat.java"
        )
        adapter.write_text(
            adapter.read_text().replace(
                'throw new IllegalStateException(\n'
                '                "compat-kit scaffold is intentionally RED: implement crushing_recipe");',
                "machines.getRegistryKey();\n        recipes.getRegistryKey();",
            )
        )
        fixture = (
            output_root
            / "src/samplemodFixture/java/com/swear/autostorage/fixture/samplemod/"
            "SamplemodIntegrationGameTests.java"
        )
        fixture_text = fixture.read_text().replace(
                'helper.fail("compat-kit scaffold is intentionally RED: " + REQUIRED_CHECKS);',
                "helper.succeed();",
            )
        fixture.write_text(
            fixture_text.rsplit("\n}\n", 1)[0]
            + "\n"
            "    // @GameTest(template = \"craftingtests.platform\")\n"
            "    public static void fakeEvidence(GameTestHelper helper) {\n"
            f"        {marker}\n"
            "    }\n"
            "}\n"
        )

        with self.assertRaisesRegex(
            ValueError,
            "evidence marker is not inside an @GameTest method",
        ):
            self.compat_kit.verify_contract(
                contract,
                source_audit=source_audit,
                bundled_root=output_root,
                command_runner=lambda command, cwd: subprocess.CompletedProcess(
                    command, 0, "All 1 required tests passed :)\n", ""
                ),
            )

    def test_verify_skips_braces_in_annotations_before_gametest_method(self):
        contract = self.accepted_contract()
        marker = "brace_annotation_marker"
        for records in contract["verification"]["evidence"].values():
            for record in records:
                record["marker"] = marker
        output_root = self.root / "brace-annotation-evidence"
        source_audit = self.source_audit()
        self.compat_kit.scaffold_bundled(
            contract,
            output_root,
            source_audit=source_audit,
        )
        adapter = (
            output_root
            / "src/compat/samplemod/java/com/swear/autostorage/compat/samplemod/"
            "SamplemodCompat.java"
        )
        adapter.write_text(
            adapter.read_text().replace(
                'throw new IllegalStateException(\n'
                '                "compat-kit scaffold is intentionally RED: implement crushing_recipe");',
                "machines.getRegistryKey();\n        recipes.getRegistryKey();",
            )
        )
        fixture = (
            output_root
            / "src/samplemodFixture/java/com/swear/autostorage/fixture/samplemod/"
            "SamplemodIntegrationGameTests.java"
        )
        fixture.write_text(
            fixture.read_text()
            .replace(
                '@GameTest(template = "craftingtests.platform")\n',
                '@GameTest(template = "craftingtests.platform")\n'
                f'    @SuppressWarnings({{"{marker}"}})\n',
            )
            .replace(
                'helper.fail("compat-kit scaffold is intentionally RED: " + REQUIRED_CHECKS);',
                "helper.succeed();",
            )
        )

        with self.assertRaisesRegex(
            ValueError,
            "evidence marker is not inside an @GameTest method",
        ):
            self.compat_kit.verify_contract(
                contract,
                source_audit=source_audit,
                bundled_root=output_root,
                command_runner=lambda command, cwd: subprocess.CompletedProcess(
                    command, 0, "All 1 required tests passed :)\n", ""
                ),
            )

    def test_gametest_parser_keeps_escaped_text_block_delimiter_inside_literal(self):
        source = (
            "final class TextBlockGameTests {\n"
            '    @GameTest(template = "craftingtests.platform")\n'
            "    static void realTest(GameTestHelper helper) {\n"
            '        String value = """\n'
            '                escaped \\""" delimiter\n'
            '                """;\n'
            "        helper.succeed();\n"
            "    }\n"
            "    static void unrelatedHelper() {\n"
            "        forged_marker();\n"
            '        // """\n'
            "    }\n"
            "}\n"
        )

        blocks = self.compat_kit._game_test_blocks(source)

        self.assertEqual(1, len(blocks))
        self.assertIn("helper.succeed();", blocks[0])
        self.assertNotIn("forged_marker();", blocks[0])

    def test_verify_binds_gametest_evidence_source_to_declared_task(self):
        contract = self.accepted_contract()
        marker = "wrongSourceMarker();"
        contract["verification"]["evidence"]["ingredient_shortage_atomic"] = [
            {
                "task": "runSamplemodGameTestServer",
                "source": "**/BaseGameTests.java",
                "marker": marker,
            }
        ]
        output_root = self.root / "wrong-task-source-evidence"
        source_audit = self.source_audit()
        self.compat_kit.scaffold_bundled(
            contract,
            output_root,
            source_audit=source_audit,
        )
        adapter = (
            output_root
            / "src/compat/samplemod/java/com/swear/autostorage/compat/samplemod/"
            "SamplemodCompat.java"
        )
        adapter.write_text(
            adapter.read_text().replace(
                'throw new IllegalStateException(\n'
                '                "compat-kit scaffold is intentionally RED: implement crushing_recipe");',
                "machines.getRegistryKey();\n        recipes.getRegistryKey();",
            )
        )
        fixture = (
            output_root
            / "src/samplemodFixture/java/com/swear/autostorage/fixture/samplemod/"
            "SamplemodIntegrationGameTests.java"
        )
        fixture.write_text(
            fixture.read_text().replace(
                'helper.fail("compat-kit scaffold is intentionally RED: " + REQUIRED_CHECKS);',
                "helper.succeed();",
            )
        )
        base = (
            output_root
            / "src/main/java/com/example/BaseGameTests.java"
        )
        base.parent.mkdir(parents=True)
        base.write_text(
            "package com.example;\n"
            "final class BaseGameTests {\n"
            "    @GameTest(template = \"craftingtests.platform\")\n"
            "    static void wrongSource(GameTestHelper helper) {\n"
            f"        {marker}\n"
            "    }\n"
            "}\n"
        )

        with self.assertRaisesRegex(
            ValueError,
            "verification evidence source is outside task source set",
        ):
            self.compat_kit.verify_contract(
                contract,
                source_audit=source_audit,
                bundled_root=output_root,
                command_runner=lambda command, cwd: subprocess.CompletedProcess(
                    command, 0, "All 1 required tests passed :)\n", ""
                ),
            )

    def test_verify_fails_closed_on_red_scaffold_contract_drift_or_command_failure(self):
        contract = self.accepted_contract()
        output_root = self.root / "bundled"
        source_audit = self.source_audit()
        self.compat_kit.scaffold_bundled(
            contract,
            output_root,
            source_audit=source_audit,
        )
        with self.assertRaisesRegex(ValueError, "intentionally RED"):
            self.compat_kit.verify_contract(
                contract,
                source_audit=source_audit,
                bundled_root=output_root,
                command_runner=lambda command, cwd: subprocess.CompletedProcess(
                    command, 0, "", ""
                ),
            )

        descriptor = output_root / "src/compat/samplemod/compat-module.json"
        descriptor.write_text(
            descriptor.read_text().replace(
                contract["source_audit_sha256"],
                "0" * 64,
            )
        )
        with self.assertRaisesRegex(
            ValueError,
            "generated verification file drift",
        ):
            self.compat_kit.verify_contract(
                contract,
                source_audit=source_audit,
                bundled_root=output_root,
                command_runner=lambda command, cwd: subprocess.CompletedProcess(
                    command, 0, "", ""
                ),
            )

        adapter = (
            output_root
            / "src/compat/samplemod/java/com/swear/autostorage/compat/samplemod/"
            "SamplemodCompat.java"
        )
        changed_contract = json.loads(json.dumps(contract))
        changed_contract["families"][0]["decision"] += " Changed after scaffolding."
        with self.assertRaisesRegex(ValueError, "contract drift"):
            self.compat_kit.verify_contract(
                changed_contract,
                source_audit=source_audit,
                bundled_root=output_root,
                command_runner=lambda command, cwd: subprocess.CompletedProcess(
                    command, 0, "", ""
                ),
            )

    def test_external_verify_rejects_implementation_links(self):
        contract = self.addon_contract()
        output = self.root / "addon"
        source_audit = self.source_audit()
        self.compat_kit.scaffold_addon(
            contract,
            output,
            source_audit=source_audit,
        )
        adapter = (
            output
            / "src/main/java/com/example/samplemodautostorage/SamplemodCompat.java"
        )
        adapter.write_text(
            adapter.read_text()
            .replace(
                'throw new IllegalStateException(\n'
                '                "compat-kit scaffold is intentionally RED: implement crushing_recipe");',
                "addon.recipeFamilies(null);",
            )
            + "\nimport com.swear.autostorage.StorageCoreBlockEntity;\n"
        )
        fixture = (
            output
            / "src/main/java/com/example/samplemodautostorage/"
            "SamplemodIntegrationGameTests.java"
        )
        fixture.write_text(
            fixture.read_text().replace(
                'helper.fail("compat-kit scaffold is intentionally RED: " + REQUIRED_CHECKS);',
                "helper.succeed();",
            )
        )
        manifest = output / ".compat-kit-manifest.json"
        manifest_data = json.loads(manifest.read_text())
        relative = adapter.relative_to(output).as_posix()
        manifest_data["files"][relative] = hashlib.sha256(adapter.read_bytes()).hexdigest()
        manifest.write_text(
            json.dumps(manifest_data, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
        )
        with self.assertRaisesRegex(ValueError, "implementation link"):
            self.compat_kit.verify_contract(
                contract,
                source_audit=source_audit,
                addon_root=output,
                command_runner=lambda command, cwd: subprocess.CompletedProcess(
                    command, 0, "", ""
                ),
            )

    def test_external_verify_rejects_manifest_self_attested_build_changes(self):
        contract = self.addon_contract()
        output = self.root / "addon"
        source_audit = self.source_audit()
        self.compat_kit.scaffold_addon(
            contract,
            output,
            source_audit=source_audit,
        )
        adapter = (
            output
            / "src/main/java/com/example/samplemodautostorage/SamplemodCompat.java"
        )
        adapter.write_text(
            adapter.read_text().replace(
                'throw new IllegalStateException(\n'
                '                "compat-kit scaffold is intentionally RED: implement crushing_recipe");',
                "addon.recipeFamilies(null);",
            )
        )
        fixture = (
            output
            / "src/main/java/com/example/samplemodautostorage/"
            "SamplemodIntegrationGameTests.java"
        )
        fixture.write_text(
            fixture.read_text().replace(
                'helper.fail("compat-kit scaffold is intentionally RED: " + REQUIRED_CHECKS);',
                "helper.succeed();",
            )
        )
        build = output / "build.gradle"
        original = build.read_text()
        changed = original.replace(
            "    dependsOn verifyCompatKitTargetArtifact\n",
            "",
        )
        self.assertNotEqual(original, changed)
        build.write_text(changed)
        manifest = output / ".compat-kit-manifest.json"
        manifest_data = json.loads(manifest.read_text())
        relative = build.relative_to(output).as_posix()
        manifest_data["files"][relative] = hashlib.sha256(build.read_bytes()).hexdigest()
        manifest.write_text(
            json.dumps(manifest_data, ensure_ascii=False, indent=2, sort_keys=True)
            + "\n"
        )

        with self.assertRaisesRegex(ValueError, "generated verification file drift"):
            self.compat_kit.verify_contract(
                contract,
                source_audit=source_audit,
                addon_root=output,
                command_runner=lambda command, cwd: subprocess.CompletedProcess(
                    command, 0, "All 1 required tests passed :)\n", ""
                ),
            )

    def test_external_verify_rejects_manifest_self_attested_wrapper_changes(self):
        contract = self.addon_contract()
        output = self.root / "addon"
        source_audit = self.source_audit()
        self.compat_kit.scaffold_addon(
            contract,
            output,
            source_audit=source_audit,
        )
        wrapper = output / "gradlew"
        wrapper.write_text("#!/bin/sh\nprintf 'forged verification output\\n'\n")
        manifest = output / ".compat-kit-manifest.json"
        manifest_data = json.loads(manifest.read_text())
        manifest_data["files"]["gradlew"] = hashlib.sha256(
            wrapper.read_bytes()
        ).hexdigest()
        manifest.write_text(
            json.dumps(manifest_data, ensure_ascii=False, indent=2, sort_keys=True)
            + "\n"
        )

        with self.assertRaisesRegex(
            ValueError,
            "generated verification file drift: gradlew",
        ):
            self.compat_kit.verify_contract(
                contract,
                source_audit=source_audit,
                addon_root=output,
                command_runner=lambda command, cwd: subprocess.CompletedProcess(
                    command, 0, "All 1 required tests passed :)\n", ""
                ),
            )

    def test_external_verify_runs_build_and_fresh_gametest_world(self):
        contract = self.addon_contract()
        output = self.root / "addon"
        source_audit = self.source_audit()
        self.compat_kit.scaffold_addon(
            contract,
            output,
            source_audit=source_audit,
        )
        adapter = (
            output
            / "src/main/java/com/example/samplemodautostorage/SamplemodCompat.java"
        )
        adapter.write_text(
            adapter.read_text().replace(
                'throw new IllegalStateException(\n'
                '                "compat-kit scaffold is intentionally RED: implement crushing_recipe");',
                "addon.recipeFamilies(null);",
            )
        )
        fixture = (
            output
            / "src/main/java/com/example/samplemodautostorage/"
            "SamplemodIntegrationGameTests.java"
        )
        fixture.write_text(
            fixture.read_text().replace(
                'helper.fail("compat-kit scaffold is intentionally RED: " + REQUIRED_CHECKS);',
                "helper.succeed();",
            )
        )
        world = output / "run/world"
        world.mkdir(parents=True)
        (world / "sentinel").write_text("stale")
        commands = []

        def runner(command, cwd):
            commands.append(command)
            if command[1] == "build":
                self.assertTrue(world.exists())
                output_text = "green\n"
            else:
                self.assertEqual("runGameTestServer", command[1])
                self.assertFalse(world.exists())
                output_text = "All 1 required tests passed :)\n"
            return subprocess.CompletedProcess(command, 0, output_text, "")

        report = self.compat_kit.verify_contract(
            contract,
            source_audit=source_audit,
            addon_root=output,
            command_runner=runner,
        )

        self.assertEqual("passed", report["status"])
        self.assertEqual(["build", "runGameTestServer"], [
            command[1] for command in commands
        ])

    def test_external_verify_refuses_symlinked_gametest_world_parent(self):
        contract = self.addon_contract()
        output = self.root / "addon"
        source_audit = self.source_audit()
        self.compat_kit.scaffold_addon(
            contract,
            output,
            source_audit=source_audit,
        )
        adapter = (
            output
            / "src/main/java/com/example/samplemodautostorage/SamplemodCompat.java"
        )
        adapter.write_text(
            adapter.read_text().replace(
                'throw new IllegalStateException(\n'
                '                "compat-kit scaffold is intentionally RED: implement crushing_recipe");',
                "addon.recipeFamilies(null);",
            )
        )
        fixture = (
            output
            / "src/main/java/com/example/samplemodautostorage/"
            "SamplemodIntegrationGameTests.java"
        )
        fixture.write_text(
            fixture.read_text().replace(
                'helper.fail("compat-kit scaffold is intentionally RED: " + REQUIRED_CHECKS);',
                "helper.succeed();",
            )
        )
        external = self.root / "external-run"
        world = external / "world"
        world.mkdir(parents=True)
        sentinel = world / "sentinel"
        sentinel.write_text("keep")
        (output / "run").symlink_to(external, target_is_directory=True)
        commands = []

        def runner(command, cwd):
            commands.append(command)
            return subprocess.CompletedProcess(command, 0, "green\n", "")

        with self.assertRaisesRegex(
            ValueError,
            "GameTest world path has symlinked ancestor",
        ):
            self.compat_kit.verify_contract(
                contract,
                source_audit=source_audit,
                addon_root=output,
                command_runner=runner,
            )

        self.assertEqual(["build"], [command[1] for command in commands])
        self.assertEqual("keep", sentinel.read_text())

    def test_publish_archive_is_reproducible_and_self_contained(self):
        first = self.root / "compat-kit-first.zip"
        second = self.root / "compat-kit-second.zip"
        build = (ROOT / "build.gradle").read_text()

        self.assertNotIn("fileTree('examples/addon')", build)
        self.assertIn(
            "file('examples/addon/src/main/java/example/autostorage/"
            "ExampleAddon.java')",
            build,
        )
        self.assertIn("'--version'", build)
        self.assertIn("mod_version.toString()", build)

        self.compat_kit.publish_archive(first, self.compat_kit.TOOL_VERSION)
        self.compat_kit.publish_archive(second, self.compat_kit.TOOL_VERSION)

        self.assertEqual(first.read_bytes(), second.read_bytes())
        with zipfile.ZipFile(first) as archive:
            names = set(archive.namelist())
            for required in (
                "auto-storage-compat-kit/compat-kit",
                "auto-storage-compat-kit/compat_kit.py",
                "auto-storage-compat-kit/README.md",
                "auto-storage-compat-kit/LICENSE",
                "auto-storage-compat-kit/schema/compat-audit.schema.json",
                "auto-storage-compat-kit/schema/compat-contract.schema.json",
                "auto-storage-compat-kit/schema/compat-delta.schema.json",
                "auto-storage-compat-kit/schema/compat-report.schema.json",
                "auto-storage-compat-kit/examples/github-actions/compat-kit.yml",
                "auto-storage-compat-kit/examples/addon/src/main/java/example/autostorage/ExampleAddon.java",
                "auto-storage-compat-kit/templates/craftingtests.platform.nbt",
                "auto-storage-compat-kit/gradle/wrapper/gradle-wrapper.jar",
            ):
                self.assertIn(required, names)
            self.assertTrue(
                all(info.date_time == (1980, 1, 1, 0, 0, 0) for info in archive.infolist())
            )
            archive.extractall(self.root / "extracted")

        extracted_module_path = (
            self.root
            / "extracted/auto-storage-compat-kit/compat_kit.py"
        )
        spec = importlib.util.spec_from_file_location(
            "extracted_auto_storage_compat_kit",
            extracted_module_path,
        )
        extracted_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(extracted_module)
        extracted_module.scaffold_addon(
            self.addon_contract(),
            self.root / "extracted-addon",
            source_audit=self.source_audit(),
        )
        self.assertTrue((self.root / "extracted-addon/gradlew").is_file())

    def test_publish_excludes_local_example_outputs(self):
        example_root = self.root / "example"
        tracked = (
            example_root
            / "src/main/java/example/autostorage/ExampleAddon.java"
        )
        tracked.parent.mkdir(parents=True)
        tracked.write_text("package example.autostorage;\n")
        sentinel = example_root / "build/review-sentinel.txt"
        sentinel.parent.mkdir(parents=True)
        sentinel.write_text("local\n")

        files = self.compat_kit._published_addon_example_files(example_root)

        self.assertEqual(
            {
                "examples/addon/src/main/java/example/autostorage/"
                "ExampleAddon.java"
            },
            set(files),
        )
        self.assertNotIn("review-sentinel.txt", "\n".join(files))

    def test_publish_excludes_local_schema_outputs(self):
        schema_root = self.root / "schema"
        schema_root.mkdir()
        for name in (
            "compat-audit.schema.json",
            "compat-contract.schema.json",
            "compat-delta.schema.json",
            "compat-report.schema.json",
        ):
            (schema_root / name).write_text("{}")
        (schema_root / "review-sentinel.json").write_text("{}")

        files = self.compat_kit._published_schema_files(schema_root)

        self.assertEqual(
            {
                "schema/compat-audit.schema.json",
                "schema/compat-contract.schema.json",
                "schema/compat-delta.schema.json",
                "schema/compat-report.schema.json",
            },
            set(files),
        )
        self.assertNotIn("review-sentinel.json", "\n".join(files))

    def test_publish_rejects_release_version_drift(self):
        with self.assertRaisesRegex(
            ValueError,
            "release version does not match compat-kit tool version",
        ):
            self.compat_kit.publish_archive(
                self.root / "compat-kit.zip",
                "0.3.1",
            )

    def test_compatibility_matrix_verifies_every_audited_compat_artifact(self):
        build = (ROOT / "build.gradle").read_text()
        matrix = build.split(
            "tasks.named('runCompatibilityMatrixGameTestServer').configure {",
            1,
        )[1].split("\n}", 1)[0]

        self.assertIn("dependsOn compatArtifactVerificationTasks", matrix)


if __name__ == "__main__":
    unittest.main()
