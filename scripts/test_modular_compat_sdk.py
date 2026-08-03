import json
import hashlib
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class ModularCompatSdkTests(unittest.TestCase):
    def test_theurgy_manifest_tracks_rebased_matrix_evidence(self):
        module_root = ROOT / "src/compat/theurgy"
        contract_path = ROOT / "compat/contracts/theurgy.json"
        manifest = json.loads((module_root / ".compat-kit-manifest.json").read_text())

        self.assertEqual(
            hashlib.sha256(contract_path.read_bytes()).hexdigest(),
            manifest["contract_sha256"],
        )
        for relative_path, expected_sha256 in manifest["files"].items():
            self.assertEqual(
                expected_sha256,
                hashlib.sha256((ROOT / relative_path).read_bytes()).hexdigest(),
                relative_path,
            )

    def test_draconicevolution_manifest_hashes_every_generated_file(self):
        manifest = json.loads(
            (
                ROOT
                / "src/compat/draconicevolution/.compat-kit-manifest.json"
            ).read_text()
        )
        for relative_path, expected_sha256 in manifest["files"].items():
            self.assertEqual(
                expected_sha256,
                hashlib.sha256((ROOT / relative_path).read_bytes()).hexdigest(),
                relative_path,
            )

    def test_every_compat_kit_manifest_file_hash_matches_disk(self):
        manifests = sorted((ROOT / "src/compat").glob("*/.compat-kit-manifest.json"))
        self.assertTrue(manifests)
        for manifest_path in manifests:
            manifest = json.loads(manifest_path.read_text())
            module_id = manifest_path.parent.name
            self.assertEqual(
                {"schema", "tool_version", "contract_sha256", "files"},
                set(manifest),
                module_id,
            )
            for relative_path, expected_sha256 in manifest["files"].items():
                self.assertEqual(
                    expected_sha256,
                    hashlib.sha256((ROOT / relative_path).read_bytes()).hexdigest(),
                    f"{module_id}:{relative_path}",
                )

    def test_productivebees_outcome_c_metadata_is_current_and_declarative(self):
        module_root = ROOT / "src/compat/productivebees"
        descriptor = json.loads((module_root / "compat-module.json").read_text())
        contract_path = ROOT / "compat/contracts/productivebees.json"
        contract = json.loads(contract_path.read_text())
        audit = json.loads(
            (ROOT / "compat/audits/productivebees/13.13.5.json").read_text()
        )
        manifest = json.loads((module_root / ".compat-kit-manifest.json").read_text())

        self.assertEqual(17, audit["scanner_format"])
        self.assertEqual(
            "9d48d198bc6eacf3b7729f4d60b91e661cfa15d105264ba225dee87b1d547ba1",
            audit["artifact"]["sha256"],
        )
        self.assertEqual(
            "3c818315d67abc16801626ce292bb207a7383f06",
            audit["source"]["revision"],
        )
        self.assertEqual(
            [
                ("01c29b65c7014db0f9d5e3a9c5e65b9fbc3d1e9179c5b014cb12c6c4408c0d7f", 131329),
                ("1fafb07b9d13c66d4435b0db38860d15607d9c5834d5aadeb89ce1bb3d16543d", 116961),
                ("2382ea29e50ff9deb46fa393d1e49c3a54b5d6273c252d0208d3fed903e8eb5f", 56279815),
                ("6671c8aa783d5fc3056b5a24b041edcf51b9c774b68fd85a790ae3346e4550e7", 154249),
                ("a45df2125c26219974aba7507ffc9afe7b83acc941a386af3faacb1cc0056fde", 410690),
                ("a55ae60894a9681ca0c5d5ef0ab295bdca591dbee58c56438fbe07eba63c13e4", 570291),
                ("b9b261f4ca3589077cd363fc37047557142a199b0298921ebc392c9d5b1fa754", 644189),
            ],
            [
                (entry["sha256"], entry["size"])
                for entry in audit["ancestry_classpath"]
            ],
        )
        self.assertEqual(
            [
                "curse.maven:curios-309927:6529130",
                "curse.maven:jade-324717:5444008",
                "dev.emi:emi-neoforge:1.1.22+1.21.1:api",
                "maven.modrinth:geckolib:qj2pTqCr",
                "mezz.jei:jei-1.21.1-common-api:19.25.0.322",
            ],
            [entry["dependency"] for entry in audit["ancestry_dependencies"]],
        )
        expected_recipe_classes = [
            "cy.jdkdigital.productivebees.common.recipe.AdvancedBeehiveRecipe",
            "cy.jdkdigital.productivebees.common.recipe.BeeBombBeeCageRecipe",
            "cy.jdkdigital.productivebees.common.recipe.BeeBreedingRecipe",
            "cy.jdkdigital.productivebees.common.recipe.BeeConversionRecipe",
            "cy.jdkdigital.productivebees.common.recipe.BeeFishingRecipe",
            "cy.jdkdigital.productivebees.common.recipe.BeeNBTChangerRecipe",
            "cy.jdkdigital.productivebees.common.recipe.BeeSpawningRecipe",
            "cy.jdkdigital.productivebees.common.recipe.BlockConversionRecipe",
            "cy.jdkdigital.productivebees.common.recipe.BottlerRecipe",
            "cy.jdkdigital.productivebees.common.recipe.CentrifugeRecipe",
            "cy.jdkdigital.productivebees.common.recipe.CombineGeneRecipe",
            "cy.jdkdigital.productivebees.common.recipe.ConfigurableCombBlockRecipe",
            "cy.jdkdigital.productivebees.common.recipe.ConfigurableHoneycombRecipe",
            "cy.jdkdigital.productivebees.common.recipe.HoneyTreatGeneRecipe",
            "cy.jdkdigital.productivebees.common.recipe.IncubationRecipe",
            "cy.jdkdigital.productivebees.common.recipe.ItemConversionRecipe",
        ]
        self.assertEqual(
            expected_recipe_classes,
            [
                candidate["class"]
                for candidate in audit["candidates"]["recipe_classes"]
            ],
        )
        self.assertEqual(
            expected_recipe_classes,
            sorted(family["class"] for family in contract["families"]),
        )
        self.assertTrue(all(family["status"] == "rejected" for family in contract["families"]))
        self.assertEqual(
            audit["recipe_data"]["digest"],
            contract["source_recipe_data_sha256"],
        )
        self.assertEqual(
            "curse.maven:productivebees-377897:8022994",
            contract["target"]["dependency"],
        )
        self.assertEqual(
            contract["target"]["dependency"],
            descriptor["auditArtifact"]["dependency"],
        )
        self.assertEqual(
            'manifest.assertCoexistence(helper, "Descriptor matrix coexistence")',
            contract["verification"]["evidence"]["all_mod_coexistence"][0]["marker"],
        )
        matrix = descriptor["matrix"]
        self.assertEqual(contract["matrix"], matrix)
        self.assertEqual(["productivebees"], matrix["mods"])
        self.assertEqual([], matrix["descriptors"])
        self.assertEqual([], matrix["resourceKinds"])
        self.assertEqual([], matrix["acceptedRecipes"])
        self.assertEqual([], matrix["rejectedDescriptors"])
        self.assertEqual([], matrix["rejectedResourceKinds"])
        self.assertEqual(
            ["productivebees"],
            matrix["recipeInventory"]["namespaces"],
        )
        companions = json.loads(
            (
                ROOT
                / "src/compatibilityMatrixFixture/resources/META-INF/auto_storage/compatibility-matrix-companions.json"
            ).read_text()
        )
        self.assertEqual({"schema", "companions"}, set(companions))
        self.assertNotIn("coexistenceRecipeInventory", companions)
        self.assertNotIn("unclaimedRecipeInventory", companions)
        self.assertEqual(
            hashlib.sha256(contract_path.read_bytes()).hexdigest(),
            manifest["contract_sha256"],
        )
        for relative_path, expected_sha256 in manifest["files"].items():
            self.assertEqual(
                expected_sha256,
                hashlib.sha256((ROOT / relative_path).read_bytes()).hexdigest(),
                relative_path,
            )

    def test_create_aquatic_ambitions_outcome_c_metadata_is_declarative(self):
        module_root = ROOT / "src/compat/create_aquatic_ambitions"
        descriptor = json.loads((module_root / "compat-module.json").read_text())
        contract_path = ROOT / "compat/contracts/create_aquatic_ambitions.json"
        contract = json.loads(contract_path.read_text())
        audit = json.loads(
            (
                ROOT
                / "compat/audits/create_aquatic_ambitions/2.0.4.json"
            ).read_text()
        )
        manifest = json.loads((module_root / ".compat-kit-manifest.json").read_text())

        self.assertEqual(17, audit["scanner_format"])
        self.assertTrue(audit["ancestry_classpath"])
        self.assertEqual(
            sorted(candidate["class"] for candidate in audit["candidates"]["recipe_classes"]),
            sorted(family["class"] for family in contract["families"]),
        )
        self.assertEqual(
            audit["recipe_data"]["digest"],
            contract["source_recipe_data_sha256"],
        )
        self.assertEqual(
            'manifest.assertCoexistence(helper, "Descriptor matrix coexistence")',
            contract["verification"]["evidence"]["all_mod_coexistence"][0]["marker"],
        )
        matrix = descriptor["matrix"]
        self.assertEqual(contract["matrix"], matrix)
        self.assertEqual(["create_aquatic_ambitions"], matrix["mods"])
        self.assertEqual([], matrix["descriptors"])
        self.assertEqual([], matrix["resourceKinds"])
        self.assertEqual([], matrix["acceptedRecipes"])
        self.assertEqual([], matrix["rejectedDescriptors"])
        self.assertEqual([], matrix["rejectedResourceKinds"])
        self.assertEqual(
            ["create_aquatic_ambitions"],
            matrix["recipeInventory"]["namespaces"],
        )
        self.assertEqual(
            hashlib.sha256(contract_path.read_bytes()).hexdigest(),
            manifest["contract_sha256"],
        )
        for relative_path, expected_sha256 in manifest["files"].items():
            self.assertEqual(
                expected_sha256,
                hashlib.sha256((ROOT / relative_path).read_bytes()).hexdigest(),
                relative_path,
            )

    def test_railcraft_outcome_c_uses_exact_current_audit_evidence(self):
        module_root = ROOT / "src/compat/railcraft"
        descriptor = json.loads((module_root / "compat-module.json").read_text())
        contract_path = ROOT / "compat/contracts/railcraft.json"
        contract = json.loads(contract_path.read_text())
        audit = json.loads(
            (ROOT / "compat/audits/railcraft/1.2.10.json").read_text()
        )
        manifest = json.loads((module_root / ".compat-kit-manifest.json").read_text())

        self.assertEqual(17, audit["scanner_format"])
        self.assertEqual(
            {
                "class_count": 1013,
                "class_inventory_sha256": "2432b6cdddc0268428e22f12c71fea9a1c93a80b80e59e11d3ffc02a2718b7a6",
                "sha256": "7de3dfeac277da57f9897822824332c99e53b9d36956143b38c0966f39144328",
                "size": 5290986,
            },
            audit["artifact"],
        )
        self.assertEqual(
            "7b89837df369bb0552d81016c46840792bd13d23",
            audit["source"]["revision"],
        )
        self.assertIn(
            {
                "sha256": "2382ea29e50ff9deb46fa393d1e49c3a54b5d6273c252d0208d3fed903e8eb5f",
                "size": 56279815,
            },
            audit["ancestry_classpath"],
        )
        self.assertEqual(
            {
                "com.google.guava:guava:28.2-jre",
                "curse.maven:jade-api-324717:6853386",
                "dev.emi:emi-neoforge:1.1.22+1.21.1:api",
                "mezz.jei:jei-1.21.1-common-api:19.25.0.322",
                "net.neoforged:bus:8.0.5",
            },
            {
                dependency["dependency"]
                for dependency in audit["ancestry_dependencies"]
            },
        )
        actual_recipe_classes = {
            "mods.railcraft.world.item.crafting.BlastFurnaceRecipe",
            "mods.railcraft.world.item.crafting.ChestMinecartDisassemblyRecipe",
            "mods.railcraft.world.item.crafting.CokeOvenRecipe",
            "mods.railcraft.world.item.crafting.CrusherRecipe",
            "mods.railcraft.world.item.crafting.LocomotivePaintingRecipe",
            "mods.railcraft.world.item.crafting.PatchouliBookCrafting",
            "mods.railcraft.world.item.crafting.RollingRecipe",
            "mods.railcraft.world.item.crafting.RotorRepairRecipe",
            "mods.railcraft.world.item.crafting.StoneTieRecipe",
            "mods.railcraft.world.item.crafting.TicketDuplicateRecipe",
            "mods.railcraft.world.item.crafting.VoidChestMinecartDisassemblyRecipe",
            "mods.railcraft.world.item.crafting.WoodenTieRecipe",
            "mods.railcraft.world.item.crafting.WorldSpikeMinecartDisassemblyRecipe",
        }
        self.assertEqual(
            actual_recipe_classes,
            {
                candidate["class"]
                for candidate in audit["candidates"]["recipe_classes"]
            },
        )
        self.assertEqual(
            actual_recipe_classes,
            {family["class"] for family in contract["families"]},
        )
        self.assertTrue(
            all(
                family["status"] == "rejected"
                for family in contract["families"]
            )
        )
        self.assertEqual(
            "maven.modrinth:railcraft-reborn:BrIwB6GH",
            contract["target"]["dependency"],
        )
        self.assertEqual(
            audit["recipe_data"]["digest"],
            contract["source_recipe_data_sha256"],
        )
        self.assertEqual(contract["matrix"], descriptor["matrix"])
        self.assertEqual(
            'manifest.assertCoexistence(helper, "Descriptor matrix coexistence")',
            contract["verification"]["evidence"]["all_mod_coexistence"][0]["marker"],
        )
        fixture_source = (
            ROOT
            / "src/railcraftFixture/java/com/swear/autostorage/fixture/railcraft/"
            "RailcraftIntegrationGameTests.java"
        ).read_text()
        for recipe_id in (
            "chest_minecart_disassembly",
            "locomotive_color_variant",
            "patchouli_book_crafting",
            "rotor_repair",
            "stone_tie",
            "ticket",
            "void_chest_minecart_disassembly",
            "wooden_tie",
            "worldspike_minecart_disassembly",
        ):
            self.assertIn(f'railcraft("{recipe_id}")', fixture_source)
        self.assertEqual(
            hashlib.sha256(contract_path.read_bytes()).hexdigest(),
            manifest["contract_sha256"],
        )
        for relative_path, expected_sha256 in manifest["files"].items():
            self.assertEqual(
                expected_sha256,
                hashlib.sha256((ROOT / relative_path).read_bytes()).hexdigest(),
                relative_path,
            )

    def test_productivemetalworks_outcome_c_uses_exact_current_audit_evidence(self):
        module_root = ROOT / "src/compat/productivemetalworks"
        descriptor = json.loads((module_root / "compat-module.json").read_text())
        contract_path = ROOT / "compat/contracts/productivemetalworks.json"
        contract = json.loads(contract_path.read_text())
        audit = json.loads(
            (ROOT / "compat/audits/productivemetalworks/1.15.0.json").read_text()
        )
        manifest = json.loads((module_root / ".compat-kit-manifest.json").read_text())

        self.assertEqual(17, audit["scanner_format"])
        self.assertEqual(
            {
                "class_count": 103,
                "class_inventory_sha256": "d791fe6217a452ebc476cc7f0602ac911b9f1f2ee51d54f9839bb58a29a8a1f8",
                "sha256": "1dcf9e10fc457c92d9ed466336104927169817cd509ca9ca69dec734f994d124",
                "size": 3042019,
            },
            audit["artifact"],
        )
        self.assertEqual(
            "7c6483c51e1a9def633a939ea75e0018dd079ffa",
            audit["source"]["revision"],
        )
        self.assertEqual(9, len(audit["ancestry_classpath"]))
        self.assertIn(
            {
                "sha256": "2382ea29e50ff9deb46fa393d1e49c3a54b5d6273c252d0208d3fed903e8eb5f",
                "size": 56279815,
            },
            audit["ancestry_classpath"],
        )
        self.assertEqual(
            {
                "curse.maven:jade-324717:5884231",
                "cy.jdkdigital.productivelib:productivelib:1.21.1-0.2.0",
                "maven.modrinth:fusion-connected-textures:h2GrA0Ku",
                "mezz.jei:jei-1.21.1-common-api:19.27.0.340",
                "net.createmod.ponder:ponder-neoforge:1.0.81+mc1.21.1",
                "net.neoforged.fancymodloader:loader:4.0.42",
                "net.neoforged:bus:8.0.5",
                "xyz.brassgoggledcoders:PatchouliProvider:1.21.1-1.0.11-Snapshot.4",
            },
            {
                dependency["dependency"]
                for dependency in audit["ancestry_dependencies"]
            },
        )
        self.assertEqual(1118, audit["recipe_data"]["declared_recipes"])
        self.assertEqual(1118, audit["recipe_data"]["effective_recipes"])
        actual_recipe_classes = {
            "cy.jdkdigital.productivemetalworks.recipe.BlockCastingRecipe",
            "cy.jdkdigital.productivemetalworks.recipe.EntityMeltingRecipe",
            "cy.jdkdigital.productivemetalworks.recipe.FluidAlloyingRecipe",
            "cy.jdkdigital.productivemetalworks.recipe.ICastingRecipe",
            "cy.jdkdigital.productivemetalworks.recipe.ItemCastingRecipe",
            "cy.jdkdigital.productivemetalworks.recipe.ItemMeltingRecipe",
        }
        self.assertEqual(
            actual_recipe_classes,
            {
                candidate["class"]
                for candidate in audit["candidates"]["recipe_classes"]
            },
        )
        self.assertEqual(
            actual_recipe_classes,
            {family["class"] for family in contract["families"]},
        )
        self.assertTrue(
            all(family["status"] == "rejected" for family in contract["families"])
        )
        self.assertEqual(
            audit["recipe_data"]["digest"],
            contract["source_recipe_data_sha256"],
        )
        self.assertEqual(contract["matrix"], descriptor["matrix"])
        self.assertEqual(
            'manifest.assertCoexistence(helper, "Descriptor matrix coexistence")',
            contract["verification"]["evidence"]["all_mod_coexistence"][0]["marker"],
        )
        self.assertEqual(
            hashlib.sha256(contract_path.read_bytes()).hexdigest(),
            manifest["contract_sha256"],
        )
        for relative_path, expected_sha256 in manifest["files"].items():
            self.assertEqual(
                expected_sha256,
                hashlib.sha256((ROOT / relative_path).read_bytes()).hexdigest(),
                relative_path,
            )

    def test_bundled_modules_are_metadata_owned_and_complete(self):
        compat_root = ROOT / "src/compat"
        descriptors = sorted(compat_root.glob("*/compat-module.json"))
        self.assertEqual(
            sorted(path.name for path in compat_root.iterdir() if path.is_dir()),
            sorted(path.parent.name for path in descriptors),
        )
        modules = [json.loads(path.read_text()) for path in descriptors]
        for module in modules:
            self.assertEqual(1, module["schema"])
        ids = [module["id"] for module in modules]
        self.assertEqual(sorted(ids), ids)
        self.assertEqual(len(ids), len(set(ids)))
        for module in modules:
            self.assertEqual("both", module["side"])
            self.assertTrue(module["requires"])
            self.assertTrue(module["dependencies"])
            self.assertTrue(module["runtimeDependencies"])
            self.assertIn(
                module["dependencies"][0],
                module["runtimeDependencies"],
            )
            self.assertGreater(module["expectedTests"], 0)
            self.assertIn("matrix", module)
            self.assertTrue(module["matrix"]["mods"])
            self.assertIn("recipeInventory", module["matrix"])
            self.assertRegex(module["matrix"]["recipeInventory"]["sha256"], r"^[0-9a-f]{64}$")
            class_path = Path(
                "src/compat",
                module["id"].split(":", 1)[1],
                "java",
                *module["entrypoint"].split("."),
            ).with_suffix(".java")
            source = (ROOT / class_path).read_text()
            self.assertIn("implements AutoStorageCompatModule", source)
            self.assertEqual(1, source.count("context.register("))
            self.assertNotIn("AutoStorage.", source)
            self.assertNotIn("MachineEnergyTable.", source)
        build = (ROOT / "build.gradle").read_text()
        self.assertFalse(
            (
                ROOT
                / "src/main/resources/META-INF/auto_storage/compat-modules.json"
            ).exists()
        )
        self.assertIn("JsonSlurper", build)
        self.assertIn("generateCompatModuleIndex", build)
        self.assertNotIn("def compatModules = [", build)

    def test_descriptor_build_wires_new_fixture_run_and_test_gate_without_central_switches(self):
        build = (ROOT / "build.gradle").read_text()
        self.assertIn("sourceSets.maybeCreate(spec.fixture)", build)
        self.assertRegex(
            build,
            r"(?s)compatModules\.each \{ spec ->.*?"
            r"addModdingDependenciesTo sourceSets\[spec\.fixture\]",
        )
        self.assertRegex(
            build,
            r"(?s)compatModules\.each \{ spec ->.*?"
            r"\"\$\{spec\.fixtureModId\}\".*?"
            r"sourceSet\(sourceSets\[spec\.fixture\]\)",
        )
        self.assertIn('"${spec.runName}"', build)
        self.assertIn("tasks.named(spec.runTask)", build)
        self.assertIn("spec.expectedTests", build)
        self.assertIn(
            "descriptor.expectedTests instanceof Integer",
            build,
        )
        self.assertNotIn(
            "descriptor.expectedTests instanceof Number",
            build,
        )
        self.assertIn("descriptor.auditArtifact", build)
        self.assertIn(
            "descriptor.dependencies[0] != auditArtifact.dependency",
            build,
        )
        self.assertIn(
            "!descriptor.runtimeDependencies.contains(auditArtifact.dependency)",
            build,
        )
        self.assertIn(
            "!(descriptor.runtimeDependencies instanceof List)",
            build,
        )
        self.assertIn(
            "descriptor.runtimeDependencies.isEmpty()",
            build,
        )
        self.assertIn(
            "!descriptor.runtimeDependencies.contains(descriptor.dependencies[0])",
            build,
        )
        self.assertIn("descriptor.repositories", build)
        self.assertIn(
            "descriptor.repositories == null ? [] : descriptor.repositories",
            build,
        )
        self.assertIn("compatModules.collectMany { it.repositories }", build)
        self.assertIn(
            "compatModules.collectMany { it.repositories }.unique(false).each",
            build,
        )
        self.assertNotIn(
            "repositories: descriptorRepositories.collect {\n"
            "                it.toString()\n"
            "            }.sort()",
            build,
        )
        self.assertIn("url = uri(repository)", build)
        self.assertIn("verifyCompatArtifact", build)
        self.assertIn("MessageDigest.getInstance(\"SHA-256\")", build)
        self.assertRegex(
            build,
            r"(?s)tasks\.named\(spec\.runTask\).*?dependsOn verifyTask",
        )
        self.assertRegex(
            build,
            r"(?s)tasks\.named\('check'\).*?dependsOn compatArtifactVerificationTasks",
        )
        self.assertIn(
            "def runName = descriptor.fixture.substring(",
            build,
        )
        self.assertNotIn("def compatPascalName", build)
        for fixture_id in (
            "auto_storage_mekanism_fixture {",
            "auto_storage_botania_fixture {",
            "auto_storage_create_fixture {",
        ):
            self.assertNotIn(fixture_id, build)

    def test_descriptor_runtime_transforms_are_shared_exact_and_runtime_only(self):
        build = (ROOT / "build.gradle").read_text()

        self.assertIn("descriptor.runtimeArtifactTransforms", build)
        self.assertIn("sharedCompatRuntimeTransforms", build)
        self.assertIn(
            "Conflicting compatibility runtime artifact transform",
            build,
        )
        self.assertIn(
            "Compatibility runtime artifact SHA is declared by multiple dependencies",
            build,
        )
        self.assertIn('"transform-runtime-artifact"', build)
        self.assertIn('"--expected-sha256"', build)
        self.assertIn('"--remove-entry"', build)
        self.assertIn("outputs.cacheIf", build)
        self.assertIn("runtimeTransformOutputs", build)
        self.assertIn("builtBy(runtimeTransformTasks", build)
        self.assertIn("mergeCompatRuntimeTransform", build)
        self.assertIn("verifyCompatRuntimeTransformPlanning", build)
        self.assertRegex(
            build,
            r"(?s)tasks\.named\('check'\).*?"
            r"dependsOn verifyCompatRuntimeTransformPlanning",
        )
        self.assertRegex(
            build,
            r"(?s)spec\.runtimeDependencies\.each \{ notation ->.*?"
            r"if \(spec\.runtimeArtifactTransformsByDependency\.containsKey\(notation\)\)"
            r".*?return",
        )
        self.assertNotIn("integratedDynamicsRsGameTestClass", build)
        self.assertNotIn("GameTestsAspectsRefinedStorage", build)

    def test_runtime_transform_sha_ownership_includes_audit_artifacts(self):
        build = (ROOT / "build.gradle").read_text()

        self.assertIn("def claimCompatRuntimeArtifactSha =", build)
        self.assertRegex(
            build,
            r"(?s)compatModules\.findAll \{ it\.auditArtifact != null \}\.each"
            r".*?claimCompatRuntimeArtifactSha\("
            r".*?spec\.auditArtifact\.sha256"
            r".*?spec\.auditArtifact\.dependency",
        )
        self.assertRegex(
            build,
            r"(?s)verifyCompatRuntimeTransformPlanning.*?"
            r"claimCompatRuntimeArtifactSha.*?mergeCompatRuntimeTransform",
        )

    def test_runtime_transform_planner_rejects_direct_pristine_dependency(self):
        build = (ROOT / "build.gradle").read_text()

        self.assertIn("def validateCompatRuntimeDependencyNotations =", build)
        self.assertIn(
            "Direct compatibility runtime dependency must use its shared exact transform",
            build,
        )
        self.assertRegex(
            build,
            r"(?s)sourceSets\.findAll\s*\{\s*"
            r"it\.name\.endsWith\('Fixture'\)\s*\}"
            r".*?runtimeOnlyConfigurationName"
            r".*?validateCompatRuntimeDependencyNotations",
        )
        self.assertRegex(
            build,
            r"(?s)verifyCompatRuntimeTransformPlanning.*?"
            r"validateCompatRuntimeDependencyNotations",
        )

    def test_runtime_transform_planner_validates_resolved_runtime_classpaths(self):
        build = (ROOT / "build.gradle").read_text()

        self.assertIn("class CompatRuntimeClasspathValidator", build)
        self.assertIn("Pristine compatibility runtime artifact is present", build)
        self.assertIn("Transformed compatibility runtime artifact is missing", build)
        self.assertRegex(
            build,
            r"(?s)registerCompatRuntimeIsolationVerification.*?"
            r"inputs\.files\(fixtureSourceSet\.runtimeClasspath\).*?"
            r"CompatRuntimeClasspathValidator\.validate",
        )
        self.assertRegex(
            build,
            r"(?s)tasks\.named\(spec\.runTask\).*?dependsOn runtimeIsolationTask",
        )
        self.assertRegex(
            build,
            r"(?s)verifyCompatRuntimeTransformPlanning.*?"
            r"CompatRuntimeClasspathValidator\.validate",
        )

    def test_runtime_transform_entry_character_validation_uses_character_code(self):
        build = (ROOT / "build.gradle").read_text()

        self.assertIn("character.charAt(0)", build)
        self.assertNotIn("((int) character)", build)

    def test_registration_and_reload_lifecycle_are_fail_closed_and_ordered(self):
        addon = (
            ROOT
            / "src/api/java/com/swear/autostorage/api/AutoStorageAddon.java"
        ).read_text()
        lifecycle = (
            ROOT
            / "src/api/java/com/swear/autostorage/api/AutoStorageAddonLifecycle.java"
        ).read_text()
        renderer = (
            ROOT
            / "src/main/java/com/swear/autostorage/TerminalResourceRendererApi.java"
        ).read_text()
        core = (
            ROOT / "src/main/java/com/swear/autostorage/AutoStorage.java"
        ).read_text()
        catalog = (
            ROOT
            / "src/main/java/com/swear/autostorage/CraftableRecipeCatalog.java"
        ).read_text()

        self.assertIn("AutoStorageAddonLifecycle.ensureRegistrationOpen", addon)
        self.assertIn("closeRegistration", lifecycle)
        self.assertRegex(
            renderer,
            r"(?s)if \(frozen\).*?throw new IllegalStateException",
        )
        self.assertRegex(
            core,
            r"(?s)onDatapackSync.*?runRecipeReloads\(\).*?"
            r"invalidateDatapackCaches\(\).*?CraftableRecipeCatalog\.invalidate\(\).*?"
            r"CraftableRecipeCatalog\.prewarm",
        )
        self.assertIn("CACHE.clear()", catalog)

    def test_old_optional_dispatchers_are_removed(self):
        for name in (
            "OptionalModRecipeCompatibility.java",
            "OptionalModCapabilities.java",
            "OptionalModBlockStrategies.java",
            "OptionalModContainerStrategies.java",
        ):
            self.assertFalse(
                (ROOT / "src/main/java/com/swear/autostorage" / name).exists(),
                name,
            )

    def test_main_has_no_optional_target_compile_dependencies(self):
        build = (ROOT / "build.gradle").read_text()
        dependencies = re.search(
            r"dependencies\s*\{(?P<body>.*?)\n\}", build, re.DOTALL
        ).group("body")
        forbidden = (
            "mekanism_ci_version",
            "botania_ci_version",
            "iron_furnaces_ci_version",
            "farmers_delight_ci_version",
            "modern_industrialization_ci_version",
            "ars_nouveau_ci_version",
            "evilcraft_ci_version",
            "powah_ci_version",
            "industrial_foregoing_ci_version",
            "create_ci_version",
            "pneumaticcraft_ci_version",
            "extended_crafting_ci_version",
        )
        main_compile_lines = [
            line.strip()
            for line in dependencies.splitlines()
            if line.strip().startswith(("compileOnly ", "implementation "))
        ]
        for coordinate in forbidden:
            self.assertFalse(
                any(coordinate in line for line in main_compile_lines),
                f"{coordinate} remains on main compile classpath",
            )

    def test_api_artifacts_and_isolated_compile_fixture_exist(self):
        build = (ROOT / "build.gradle").read_text()
        self.assertRegex(
            build,
            r"sourceSets\.create\('api'\)",
        )
        api_root = ROOT / "src/api/java/com/swear/autostorage/api"
        self.assertTrue(api_root.is_dir())
        self.assertFalse(
            (ROOT / "src/main/java/com/swear/autostorage/api").exists()
        )
        for task in ("apiJar", "apiSourcesJar", "apiJavadocJar"):
            self.assertRegex(build, rf"\b{task}\b")
        api_test = re.search(
            r"apiTest\s*\{(?P<body>.*?)\n\s*\}", build, re.DOTALL
        ).group("body")
        self.assertNotIn("sourceSets.main.output", api_test)
        self.assertRegex(
            build,
            r"sourceSets\.apiTest\.compileClasspath\s*\+=\s*files\(apiJar\)",
        )

    def test_bundled_modules_compile_against_the_api_artifact(self):
        build = (ROOT / "build.gradle").read_text()
        compat_source_sets = re.search(
            r"compatSourceSets\[spec\.id\] = "
            r"sourceSets\.create\(spec\.sourceSet\) \{(?P<body>.*?)\n\s*\}",
            build,
            re.DOTALL,
        ).group("body")
        self.assertNotIn("sourceSets.main.output", compat_source_sets)
        self.assertRegex(
            build,
            r"(?s)compatSourceSets\.each.*?"
            r"compileClasspath\s*\+=\s*files\(apiJar\)",
        )
        self.assertRegex(
            build,
            r"(?s)tasks\.named\(sourceSet\.compileJavaTaskName\).*?"
            r"dependsOn apiJar",
        )

    def test_transform_and_variant_extensions_are_registry_backed(self):
        transform = (
            ROOT
            / "src/main/java/com/swear/autostorage/TransformProviderApi.java"
        ).read_text()
        variants = (
            ROOT
            / "src/main/java/com/swear/autostorage/MachineVariantContributors.java"
        ).read_text()
        self.assertIn("REGISTRY_KEY", transform)
        self.assertNotIn("static final List<Provider>", transform)
        self.assertIn("MACHINE_VARIANT_CONTRIBUTOR_REGISTRY", variants)
        self.assertNotIn("static final Map<", variants)

    def test_example_addon_uses_every_public_extension_through_one_facade(self):
        example = (
            ROOT
            / "examples/addon/src/main/java/example/autostorage/ExampleAddon.java"
        ).read_text()
        self.assertEqual(1, example.count("AutoStorageAddon.register("))
        for method in (
            ".machineDescriptors(",
            ".recipeFamilies(",
            ".resourceKinds(",
            ".containerStrategies(",
            ".blockStrategies(",
            ".transformProviders(",
            ".machineVariantContributors(",
        ):
            self.assertIn(method, example)
        build = (ROOT / "build.gradle").read_text()
        addon_example = re.search(
            r"sourceSets\.create\('addonExample'\)\s*\{(?P<body>.*?)\n\}",
            build,
            re.DOTALL,
        ).group("body")
        self.assertNotIn("sourceSets.main.output", addon_example)
        self.assertIn("apiJar", addon_example)

    def test_build_has_source_and_api_bytecode_isolation_gate(self):
        build = (ROOT / "build.gradle").read_text()
        self.assertIn("verifyCompatIsolation", build)
        self.assertIsNotNone(
            re.search(
                r"tasks\.named\('check'\).*?"
                r"dependsOn tasks\.named\('verifyCompatIsolation'\)",
                build,
                re.DOTALL,
            )
        )
        self.assertIn("verifyApiSurface", build)
        api_surface = (ROOT / "api/api-surface.txt").read_text()
        for implementation_type in (
            "MachineEnergyTable",
            "StorageCoreBlockEntity",
            "CompatibilityModuleLoader",
            "net.minecraft.client",
        ):
            self.assertNotIn(implementation_type, api_surface)

    def test_sdk_is_documented_and_release_publishes_api_assets(self):
        guide = (ROOT / "docs/addon-development.md").read_text()
        self.assertIn("AutoStorageAddon.register(", guide)
        self.assertIn(":api", guide)
        self.assertIn("api-sources.jar", guide)
        self.assertIn("api-javadoc.jar", guide)
        self.assertIn("Compat Kit", guide)
        readme = (ROOT / "README.md").read_text()
        self.assertIn("docs/addon-development.md", readme)
        release = (ROOT / ".github/workflows/release.yml").read_text()
        self.assertIn("Upload addon SDK assets", release)
        self.assertRegex(
            release,
            r"gh release upload.*?auto_storage-.*?-api\.jar",
        )

    def test_addon_guide_stays_out_of_player_wiki_navigation(self):
        releasing = (ROOT / "docs/releasing.md").read_text()
        self.assertIn(
            "intentionally excluded from the player-manual Home contents and\n"
            "sidebar",
            releasing,
        )
        self.assertNotIn(
            "add `[[Addon Development|Addon-Development]]` to\n"
            "`_Sidebar.md`",
            releasing,
        )


if __name__ == "__main__":
    unittest.main()
