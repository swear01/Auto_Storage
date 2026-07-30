import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("bump_patch_version.py")

class BumpPatchVersionTests(unittest.TestCase):
    def test_bumps_mod_version_patch_and_preserves_other_properties(self):
        with tempfile.TemporaryDirectory() as tmp:
            props = Path(tmp) / "gradle.properties"
            props.write_text(textwrap.dedent("""\
                org.gradle.jvmargs=-Xmx3G
                mod_id=auto_storage
                mod_version=0.1.0
                neo_version=21.1.228
            """))

            result = subprocess.run(
                [sys.executable, str(SCRIPT), str(props)],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(result.stdout.strip(), "0.1.1")
            self.assertEqual(props.read_text(), textwrap.dedent("""\
                org.gradle.jvmargs=-Xmx3G
                mod_id=auto_storage
                mod_version=0.1.1
                neo_version=21.1.228
            """))

    def test_rejects_non_semver_mod_version_without_rewriting(self):
        with tempfile.TemporaryDirectory() as tmp:
            props = Path(tmp) / "gradle.properties"
            original = "mod_version=0.1\n"
            props.write_text(original)

            result = subprocess.run(
                [sys.executable, str(SCRIPT), str(props)],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Expected mod_version as MAJOR.MINOR.PATCH", result.stderr)
            self.assertEqual(props.read_text(), original)

if __name__ == "__main__":
    unittest.main()
