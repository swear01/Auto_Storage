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
        self.root = Path(self.temp.name)
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
                        "marker": "compat_kit_scaffold_remains_red",
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
                    "marker": "compat_kit_scaffold_remains_red",
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
        audit_schema = json.loads(
            (schema_root / "compat-audit.schema.json").read_text()
        )
        self.assertFalse(
            audit_schema["properties"]["candidates"]["additionalProperties"]
        )
        report_schema = json.loads(
            (schema_root / "compat-report.schema.json").read_text()
        )
        self.assertFalse(report_schema["properties"]["checks"]["items"]["additionalProperties"])

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
        self.compat_kit.validate_contract(
            contract,
            require_complete=True,
            source_audit=audit,
        )
        self.assertEqual(
            set(self.compat_kit.REQUIRED_VERIFICATION_CHECKS),
            set(self.compat_kit._verification_evidence(contract, ROOT)),
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

    def test_publish_archive_is_reproducible_and_self_contained(self):
        first = self.root / "compat-kit-first.zip"
        second = self.root / "compat-kit-second.zip"
        build = (ROOT / "build.gradle").read_text()

        self.assertIn("fileTree('examples/addon')", build)

        self.compat_kit.publish_archive(first)
        self.compat_kit.publish_archive(second)

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

    def test_compatibility_matrix_verifies_every_audited_compat_artifact(self):
        build = (ROOT / "build.gradle").read_text()
        matrix = build.split(
            "tasks.named('runCompatibilityMatrixGameTestServer').configure {",
            1,
        )[1].split("\n}", 1)[0]

        self.assertIn("dependsOn compatArtifactVerificationTasks", matrix)


if __name__ == "__main__":
    unittest.main()
