import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class ModularCompatSdkTests(unittest.TestCase):
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
            r"def compatSourceSets = \[:\](?P<body>.*?)"
            r"def apiClassNames =",
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
