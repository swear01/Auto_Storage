from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
OLD_SNAKE = "magic" + "_storage"
OLD_PACKAGE = "magic" + "storage"
OLD_CLASS = "Magic" + "Storage"
OLD_KEBAB = "Magic" + "-Storage"
OLD_KEBAB_LOWER = "magic" + "-storage"
OLD_REPO = "Magic" + "_Storage"
OLD_PARTIAL = "magic" + "_stor"
OLD_LOWER_CAMEL = "magic" + "Storage"
OLD_CRAFTING_CAMEL = "magic" + "Crafting"
OLD_CRAFTING_SNAKE = "magic" + "_crafting"
OLD_CRAFTING_KEBAB = "magic" + "-crafting"

OLD_EXECUTABLE_IDENTITIES = (
    OLD_SNAKE,
    OLD_PACKAGE,
    OLD_CLASS,
    OLD_KEBAB,
    OLD_KEBAB_LOWER,
    OLD_REPO,
    OLD_PARTIAL,
    OLD_LOWER_CAMEL,
    OLD_CRAFTING_CAMEL,
    OLD_CRAFTING_SNAKE,
    OLD_CRAFTING_KEBAB,
)


class ProjectIdentityTests(unittest.TestCase):
    def test_canonical_0_3_identity(self):
        properties = (ROOT / "gradle.properties").read_text()
        self.assertIn("mod_id=auto_storage", properties)
        self.assertIn("mod_name=Auto Storage", properties)
        self.assertIn("mod_version=0.3.0", properties)
        self.assertIn("mod_group_id=com.swear.autostorage", properties)

        main_class = ROOT / "src/main/java/com/swear/autostorage/AutoStorage.java"
        self.assertTrue(main_class.is_file())
        text = main_class.read_text()
        self.assertIn("package com.swear.autostorage;", text)
        self.assertIn("public class AutoStorage", text)
        self.assertIn('public static final String MODID = "auto_storage";', text)

        self.assertTrue((ROOT / "src/main/resources/assets/auto_storage").is_dir())
        self.assertTrue((ROOT / "src/main/resources/data/auto_storage").is_dir())

    def test_executable_tree_has_no_legacy_identity(self):
        roots = (
            ROOT / ".github",
            ROOT / "build.gradle",
            ROOT / "gradle.properties",
            ROOT / "scripts",
            ROOT / "src",
        )
        offenders = []
        for root in roots:
            paths = [root] if root.is_file() else root.rglob("*")
            for path in paths:
                if not path.is_file() or path == Path(__file__):
                    continue
                relative = path.relative_to(ROOT).as_posix()
                if any(old in relative for old in OLD_EXECUTABLE_IDENTITIES):
                    offenders.append(relative)
                    continue
                try:
                    text = path.read_text()
                except UnicodeDecodeError:
                    continue
                if any(old in text for old in OLD_EXECUTABLE_IDENTITIES):
                    offenders.append(relative)
        self.assertEqual([], sorted(set(offenders)))

    def test_ci_and_release_use_auto_storage_jar(self):
        ci = (ROOT / ".github/workflows/ci.yml").read_text()
        release = (ROOT / ".github/workflows/release.yml").read_text()
        smoke = (ROOT / ".github/workflows/client-smoke.yml").read_text()
        self.assertIn("build/libs/auto_storage-*.jar", ci)
        self.assertIn(
            "build/libs/auto_storage-${{ steps.release-meta.outputs.version }}.jar",
            release,
        )
        self.assertIn("cp build/libs/auto_storage-*.jar run/mods/", smoke)


if __name__ == "__main__":
    unittest.main()
