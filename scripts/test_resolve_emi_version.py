from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/resolve_emi_version.py"


def load_resolver(test_case: unittest.TestCase):
    test_case.assertTrue(SCRIPT.exists(), "missing scripts/resolve_emi_version.py")
    spec = spec_from_file_location("resolve_emi_version", SCRIPT)
    test_case.assertIsNotNone(spec)
    test_case.assertIsNotNone(spec.loader)
    module = module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ResolveEmiVersionTests(unittest.TestCase):
    def test_selects_numeric_latest_supported_minecraft_build(self):
        resolver = load_resolver(self)
        versions = [
            self.modrinth_version("minimum", "1.1.24+1.21.1+neoforge"),
            self.modrinth_version("older", "1.1.9+1.21.1+neoforge"),
            self.modrinth_version("latest", "1.2.3+1.21.1+neoforge"),
            self.modrinth_version(
                "wrong-minecraft",
                "1.9.0+1.20.6+neoforge",
                minecraft_version="1.20.6",
            ),
        ]

        self.assertEqual(
            "1.2.3+1.21.1",
            resolver.latest_compatible_version(versions, "1.21.1", (1, 1, 24), (2, 0, 0)),
        )

    def test_excludes_versions_below_minimum_or_at_upper_bound(self):
        resolver = load_resolver(self)
        versions = [
            self.modrinth_version("below", "1.1.23+1.21.1+neoforge"),
            self.modrinth_version("minimum", "1.1.24+1.21.1+neoforge"),
            self.modrinth_version("upper", "2.0.0+1.21.1+neoforge"),
            self.modrinth_version(
                "wrong-minecraft",
                "9.0.0+1.20.4+neoforge",
                minecraft_version="1.20.4",
            ),
        ]

        self.assertEqual(
            "1.1.24+1.21.1",
            resolver.latest_compatible_version(versions, "1.21.1", (1, 1, 24), (2, 0, 0)),
        )

    def test_rejects_metadata_without_a_compatible_release(self):
        resolver = load_resolver(self)
        versions = [
            self.modrinth_version("below", "1.1.23+1.21.1+neoforge"),
            self.modrinth_version(
                "wrong-minecraft",
                "1.1.24+1.20.6+neoforge",
                minecraft_version="1.20.6",
            ),
        ]

        with self.assertRaisesRegex(ValueError, "no compatible EMI release"):
            resolver.latest_compatible_version(versions, "1.21.1", (1, 1, 24), (2, 0, 0))

    def test_excludes_non_release_or_invalid_runtime_artifacts(self):
        resolver = load_resolver(self)
        versions = [
            self.modrinth_version(
                "beta",
                "1.9.0+1.21.1+neoforge",
                version_type="beta",
            ),
            self.modrinth_version(
                "api-only",
                "1.8.0+1.21.1+neoforge",
                filename="emi-api.jar",
            ),
            self.modrinth_version("release", "1.2.3+1.21.1+neoforge"),
        ]

        self.assertEqual(
            "1.2.3+1.21.1",
            resolver.latest_compatible_version(versions, "1.21.1", (1, 1, 24), (2, 0, 0)),
        )

    def test_fetches_filtered_modrinth_versions_without_changelogs(self):
        resolver = load_resolver(self)
        calls = []

        class Response:
            def __enter__(self):
                return self

            def __exit__(self, *args):
                return False

            def read(self):
                return b"[]"

        def opener(request, timeout):
            calls.append((request.full_url, request.headers, timeout))
            return Response()

        self.assertEqual([], resolver.fetch_versions("1.21.1", opener=opener))
        self.assertEqual(1, len(calls))
        url, headers, timeout = calls[0]
        self.assertTrue(url.startswith("https://api.modrinth.com/v2/project/emi/version?"))
        self.assertIn("loaders=%5B%22neoforge%22%5D", url)
        self.assertIn("game_versions=%5B%221.21.1%22%5D", url)
        self.assertIn("include_changelog=false", url)
        self.assertEqual("Auto-Storage-EMI-compatibility-check", headers["User-agent"])
        self.assertEqual(30, timeout)

    def test_reads_the_release_range_as_inclusive_minimum_and_exclusive_upper_bound(self):
        resolver = load_resolver(self)

        self.assertEqual(
            ((1, 1, 24), (2, 0, 0)),
            resolver.parse_supported_range("[1.1.24,2)"),
        )
        for invalid in ["[1.1.24]", "(1.1.24,2)", "[1.1.24,2]", "1.1.24"]:
            with self.subTest(invalid=invalid):
                with self.assertRaisesRegex(ValueError, "inclusive minimum and exclusive upper bound"):
                    resolver.parse_supported_range(invalid)

    def test_baseline_coordinate_must_match_range_and_minecraft_version(self):
        resolver = load_resolver(self)

        resolver.validate_baseline("1.1.24+1.21.1", "1.21.1", (1, 1, 24), (2, 0, 0))
        with self.assertRaisesRegex(ValueError, "minimum supported EMI version"):
            resolver.validate_baseline("1.1.25+1.21.1", "1.21.1", (1, 1, 24), (2, 0, 0))
        with self.assertRaisesRegex(ValueError, "Minecraft version"):
            resolver.validate_baseline("1.1.24+1.21", "1.21.1", (1, 1, 24), (2, 0, 0))

    @staticmethod
    def modrinth_version(
        version_id,
        version_number,
        minecraft_version="1.21.1",
        version_type="release",
        filename="emi.jar",
    ):
        return {
            "id": version_id,
            "version_number": version_number,
            "version_type": version_type,
            "status": "listed",
            "loaders": ["neoforge"],
            "game_versions": [minecraft_version],
            "files": [
                {
                    "filename": filename,
                    "primary": True,
                    "size": 10,
                }
            ],
        }


if __name__ == "__main__":
    unittest.main()
