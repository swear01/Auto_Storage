from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[1]


class GitHubWorkflowTests(unittest.TestCase):
    GAME_TEST_STEPS = (
        (
            "Run GameTest server",
            "./gradlew runGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/gametest.log",
        ),
        (
            "Run recipe addon GameTest server",
            "./gradlew runRecipeAddonGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/recipe-addon-gametest.log",
        ),
        (
            "Run Mekanism GameTest server",
            "./gradlew runMekanismGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/mekanism-gametest.log",
        ),
        (
            "Run Botania GameTest server",
            "./gradlew runBotaniaGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/botania-gametest.log",
        ),
        (
            "Run Iron Furnaces GameTest server",
            "./gradlew runIronFurnacesGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/iron-furnaces-gametest.log",
        ),
        (
            "Run Farmer's Delight GameTest server",
            "./gradlew runFarmersDelightGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/farmers-delight-gametest.log",
        ),
        (
            "Run Modern Industrialization GameTest server",
            "./gradlew runModernIndustrializationGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/modern-industrialization-gametest.log",
        ),
        (
            "Run Ars Nouveau GameTest server",
            "./gradlew runArsNouveauGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/ars-nouveau-gametest.log",
        ),
        (
            "Run EvilCraft GameTest server",
            "./gradlew runEvilCraftGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/evilcraft-gametest.log",
        ),
        (
            "Run Powah GameTest server",
            "./gradlew runPowahGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/powah-gametest.log",
        ),
        (
            "Run Industrial Foregoing GameTest server",
            "./gradlew runIndustrialForegoingGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/industrial-foregoing-gametest.log",
        ),
        (
            "Run Create GameTest server",
            "./gradlew runCreateGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/create-gametest.log",
        ),
        (
            "Run PneumaticCraft GameTest server",
            "./gradlew runPneumaticCraftGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/pneumaticcraft-gametest.log",
        ),
        (
            "Run Extended Crafting GameTest server",
            "./gradlew runExtendedCraftingGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/extended-crafting-gametest.log",
        ),
        (
            "Run optional compatibility matrix GameTest server",
            "./gradlew runCompatibilityMatrixGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/compatibility-matrix-gametest.log",
        ),
    )
    CLEAR_GAME_TEST_WORLD = (
        "python3 -c 'import shutil; shutil.rmtree(\"run/world\", ignore_errors=True)'"
    )

    def read_required(self, relative_path: str) -> str:
        path = ROOT / relative_path
        self.assertTrue(path.exists(), f"missing {relative_path}")
        return path.read_text()

    def assert_isolated_sequential_game_test_steps(self, text: str):
        previous_end = -1
        for name, command in self.GAME_TEST_STEPS:
            marker = f"      - name: {name}\n        run: |\n"
            start = text.find(marker)
            self.assertGreater(start, previous_end, f"missing or out-of-order step: {name}")
            body_start = start + len(marker)
            body_end = text.find("\n      - name:", body_start)
            if body_end == -1:
                body_end = len(text)
            body = [line.strip() for line in text[body_start:body_end].splitlines()]
            self.assertEqual(
                ["set -o pipefail", self.CLEAR_GAME_TEST_WORLD, command],
                body,
                f"{name} must be an isolated step that clears only run/world first",
            )
            previous_end = body_end

    def test_ci_and_release_run_all_game_test_fixtures_in_isolated_sequential_steps(self):
        for relative_path in (".github/workflows/ci.yml", ".github/workflows/release.yml"):
            with self.subTest(workflow=relative_path):
                self.assert_isolated_sequential_game_test_steps(
                    self.read_required(relative_path)
                )

    def test_ci_workflow_runs_full_project_verification_and_uploads_jar(self):
        text = self.read_required(".github/workflows/ci.yml")
        self.assertIn("name: CI", text)
        self.assertIn("actions/checkout@v7", text)
        self.assertIn("actions/setup-java@v5", text)
        self.assertIn("distribution: temurin", text)
        self.assertIn("java-version: '21'", text)
        self.assertIn("gradle/actions/setup-gradle@v6", text)
        self.assertIn("cache-provider: basic", text)
        self.assertIn("mkdir -p build/ci-logs", text)
        self.assertIn("set -o pipefail", text)
        self.assertIn("./gradlew build --console=plain --no-daemon 2>&1 | tee build/ci-logs/build.log", text)
        self.assertIn("./gradlew runGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/gametest.log", text)
        self.assertIn("./gradlew runRecipeAddonGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/recipe-addon-gametest.log", text)
        self.assertIn("./gradlew runMekanismGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/mekanism-gametest.log", text)
        self.assertIn("./gradlew runBotaniaGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/botania-gametest.log", text)
        self.assertIn("./gradlew runModernIndustrializationGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/modern-industrialization-gametest.log", text)
        self.assertIn("./gradlew runArsNouveauGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/ars-nouveau-gametest.log", text)
        self.assertIn("./gradlew runEvilCraftGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/evilcraft-gametest.log", text)
        self.assertIn("./gradlew runPowahGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/powah-gametest.log", text)
        self.assertIn("./gradlew runIndustrialForegoingGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/industrial-foregoing-gametest.log", text)
        self.assertIn("./gradlew runExtendedCraftingGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/extended-crafting-gametest.log", text)
        self.assertIn("./gradlew runCreateGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/create-gametest.log", text)
        self.assertIn("./gradlew runPneumaticCraftGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/pneumaticcraft-gametest.log", text)
        self.assertIn("PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover scripts 2>&1 | tee build/ci-logs/python-unittest.log", text)
        self.assertIn("./gradlew runData --console=plain --no-daemon 2>&1 | tee build/ci-logs/datagen.log", text)
        self.assertIn("git status --porcelain -- src/generated/resources src/main/resources", text)
        self.assertIn("actions/upload-artifact@v7", text)
        self.assertIn("name: auto-storage-ci-logs", text)
        self.assertIn("${{ always() }}", text)
        self.assertIn("build/ci-logs/**", text)
        self.assertIn("run/logs/**", text)
        self.assertIn("build/reports/**", text)
        self.assertIn("build/libs/auto_storage-*.jar", text)
        self.assertIn("contents: read", text)
        self.assertIn("Verify minimum and latest compatible EMI releases", text)
        self.assertIn('MIN_EMI="$(sed -n \'s/^emi_version=//p\' gradle.properties)"', text)
        self.assertIn('MIN_EMI_RUNTIME="$(sed -n \'s/^emi_runtime_version=//p\' gradle.properties)"', text)
        self.assertIn('LATEST_EMI="$(python3 scripts/resolve_emi_version.py)"', text)
        self.assertIn('LATEST_EMI_RUNTIME="$(python3 scripts/resolve_emi_runtime.py "$LATEST_EMI")"', text)
        self.assertIn('-Pemi_version="$MIN_EMI"', text)
        self.assertIn('-Pemi_runtime_version="$MIN_EMI_RUNTIME"', text)
        self.assertIn('-Pemi_version="$LATEST_EMI"', text)
        self.assertIn('-Pemi_runtime_version="$LATEST_EMI_RUNTIME"', text)
        self.assertIn("build/ci-logs/emi-minimum.log", text)
        self.assertIn("build/ci-logs/emi-latest.log", text)
        self.assertIn('bash scripts/stage_emi_runtime.sh "$MIN_EMI"', text)
        self.assertIn('MIN_EMI_RUNTIME="$(python3 scripts/resolve_emi_runtime.py "$MIN_EMI")"', text)
        self.assertIn('bash scripts/stage_emi_runtime.sh "$MIN_EMI" "$MIN_EMI_RUNTIME"', text)
        self.assertIn("build/ci-logs/emi-runtime.log", text)
        self.assertNotIn("runClient", text)
        self.assertNotIn("xvfb-run", text)

    def test_release_workflow_builds_tests_checks_tag_and_publishes_jar(self):
        text = self.read_required(".github/workflows/release.yml")
        self.assertIn("name: Release", text)
        self.assertIn("'v*.*.*'", text)
        self.assertIn("contents: write", text)
        self.assertIn(
            "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1",
            text,
        )
        self.assertIn("fetch-depth: 0", text)
        self.assertIn(
            "actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95",
            text,
        )
        self.assertIn(
            "gradle/actions/setup-gradle@90ddb51e90a5fd9ba75f40cf85156b7b41bf76a3",
            text,
        )
        self.assertIn("grep '^mod_version=' gradle.properties", text)
        self.assertIn('"v${VERSION}" != "$TAG"', text)
        self.assertIn("./gradlew build --console=plain --no-daemon 2>&1 | tee build/ci-logs/build.log", text)
        self.assertIn("./gradlew runGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/gametest.log", text)
        self.assertIn("./gradlew runRecipeAddonGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/recipe-addon-gametest.log", text)
        self.assertIn("./gradlew runMekanismGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/mekanism-gametest.log", text)
        self.assertIn("./gradlew runBotaniaGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/botania-gametest.log", text)
        self.assertIn("./gradlew runModernIndustrializationGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/modern-industrialization-gametest.log", text)
        self.assertIn("./gradlew runEvilCraftGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/evilcraft-gametest.log", text)
        self.assertIn("./gradlew runPowahGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/powah-gametest.log", text)
        self.assertIn("./gradlew runIndustrialForegoingGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/industrial-foregoing-gametest.log", text)
        self.assertIn("./gradlew runCreateGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/create-gametest.log", text)
        self.assertIn("./gradlew runPneumaticCraftGameTestServer --console=plain --no-daemon 2>&1 | tee build/ci-logs/pneumaticcraft-gametest.log", text)
        self.assertIn("PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover scripts 2>&1 | tee build/ci-logs/python-unittest.log", text)
        self.assertIn("./gradlew runData --console=plain --no-daemon 2>&1 | tee build/ci-logs/datagen.log", text)
        self.assertIn("git status --porcelain -- src/generated/resources src/main/resources", text)
        self.assertIn("Generate release notes", text)
        self.assertIn("git log --pretty='- %s (%h)'", text)
        self.assertIn("environment: publishing", text)
        self.assertIn("Check publisher configuration", text)
        self.assertIn("MODRINTH_TOKEN: ${{ secrets.MODRINTH_TOKEN }}", text)
        self.assertIn("CURSEFORGE_TOKEN: ${{ secrets.CURSEFORGE_TOKEN }}", text)
        self.assertIn("MODRINTH_PROJECT_ID: ${{ vars.MODRINTH_PROJECT_ID }}", text)
        self.assertIn("CURSEFORGE_PROJECT_ID: ${{ vars.CURSEFORGE_PROJECT_ID }}", text)
        self.assertIn(
            "uses: Kira-NT/mc-publish@52307b03863581dec6b652b83e597aec02ebb075",
            text,
        )
        self.assertIn("github-prerelease: true", text)
        self.assertIn("version-type: alpha", text)
        self.assertIn("loaders: neoforge", text)
        self.assertIn("game-versions: 1.21.1", text)
        self.assertIn("fRiHVvU7", text)
        self.assertIn("580555", text)
        self.assertIn("nU0bVIaL", text)
        self.assertIn("306770", text)
        self.assertIn("changelog-file: build/release-notes.md", text)
        self.assertIn("name: auto-storage-release-logs", text)
        self.assertIn(
            "files: build/libs/auto_storage-${{ steps.release-meta.outputs.version }}.jar",
            text,
        )
        self.assertIn("github-token: ${{ secrets.GITHUB_TOKEN }}", text)
        self.assertIn("Verify minimum and latest compatible EMI releases", text)
        self.assertIn('MIN_EMI="$(sed -n \'s/^emi_version=//p\' gradle.properties)"', text)
        self.assertIn('MIN_EMI_RUNTIME="$(sed -n \'s/^emi_runtime_version=//p\' gradle.properties)"', text)
        self.assertIn('LATEST_EMI="$(python3 scripts/resolve_emi_version.py)"', text)
        self.assertIn('LATEST_EMI_RUNTIME="$(python3 scripts/resolve_emi_runtime.py "$LATEST_EMI")"', text)
        self.assertIn('-Pemi_version="$MIN_EMI"', text)
        self.assertIn('-Pemi_runtime_version="$MIN_EMI_RUNTIME"', text)
        self.assertIn('-Pemi_version="$LATEST_EMI"', text)
        self.assertIn('-Pemi_runtime_version="$LATEST_EMI_RUNTIME"', text)
        self.assertIn('bash scripts/stage_emi_runtime.sh "$MIN_EMI"', text)
        self.assertIn('MIN_EMI_RUNTIME="$(python3 scripts/resolve_emi_runtime.py "$MIN_EMI")"', text)
        self.assertIn('bash scripts/stage_emi_runtime.sh "$MIN_EMI" "$MIN_EMI_RUNTIME"', text)
        self.assertIn("build/ci-logs/emi-runtime.log", text)
        self.assertNotIn("runClient", text)
        self.assertNotIn("xvfb-run", text)

    def test_release_pins_actions_and_gradle_distribution(self):
        text = self.read_required(".github/workflows/release.yml")
        action_lines = [
            line.strip()
            for line in text.splitlines()
            if line.strip().startswith("uses:")
        ]
        for line in action_lines:
            with self.subTest(action=line):
                self.assertRegex(line, r"^uses: [^@]+@[0-9a-f]{40}$")
        wrapper = self.read_required("gradle/wrapper/gradle-wrapper.properties")
        self.assertIn(
            "distributionSha256Sum=72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f",
            wrapper,
        )

    def test_public_repo_docs_explain_ci_cd_and_manual_gui_gate(self):
        readme = self.read_required("README.md")
        notes = self.read_required("docs/notes.md")
        agents = self.read_required("AGENTS.md")
        structure = self.read_required("docs/structure.md")
        combined = "\n".join([readme, notes, agents, structure])
        self.assertIn("https://github.com/swear01/Auto_Storage", combined)
        self.assertIn("GitHub Actions", combined)
        self.assertIn("./gradlew runGameTestServer", combined)
        self.assertIn("tag `v<mod_version>`", combined)
        self.assertIn("Prism dev / manual handoff", combined)
        self.assertIn("Visual verification owner: user", combined)
        self.assertIn("datagen drift", combined)
        self.assertIn("release notes", combined)
        self.assertIn("-o AutoStorageBot", combined)
        self.assertIn("scripts/stage_emi_runtime.sh", combined)
        self.assertIn("same Gradle command once", combined)
        self.assertIn("Modrinth / CurseForge", self.read_required("docs/roadmap.md"))
        self.assertIn("MIT License", readme)
        self.assertIn(".github/workflows/", structure)

    def test_release_examples_derive_current_mod_version(self):
        readme = self.read_required("README.md")
        notes = self.read_required("docs/notes.md")
        self.assertIn('version="$(sed -n \'s/^mod_version=//p\' gradle.properties)"', readme)
        self.assertIn('git tag "v${version}"', readme)
        self.assertIn('git push origin main "v${version}"', readme)
        self.assertIn("目前版本以 `gradle.properties` 的唯一 `mod_version` 為準", notes)

    def test_release_guide_and_project_icon_are_complete(self):
        guide = self.read_required("docs/releasing.md")
        for required in (
            "Auto Storage",
            "https://modrinth.com/mod/auto-storage",
            "1630575",
            "MIT",
            "MODRINTH_TOKEN",
            "CURSEFORGE_TOKEN",
            "MODRINTH_PROJECT_ID",
            "CURSEFORGE_PROJECT_ID",
            "publishing",
            "gh run rerun",
            "SHA-256",
        ):
            with self.subTest(required=required):
                self.assertIn(required, guide)

        icon = ROOT / "art/release/auto-storage-project-icon.png"
        self.assertTrue(icon.exists(), "missing release project icon")
        data = icon.read_bytes()
        self.assertEqual(b"\x89PNG\r\n\x1a\n", data[:8])
        self.assertEqual((512, 512), (
            int.from_bytes(data[16:20], "big"),
            int.from_bytes(data[20:24], "big"),
        ))
        self.assertLess(len(data), 2 * 1024 * 1024)

    def test_release_requires_versioned_breaking_notes(self):
        release = self.read_required(".github/workflows/release.yml")
        self.assertIn(
            'RELEASE_NOTES_FILE="docs/release-notes/${VERSION}.md"',
            release,
        )
        self.assertIn('test -f "$RELEASE_NOTES_FILE"', release)
        self.assertIn('cat "$RELEASE_NOTES_FILE"', release)

        notes = self.read_required("docs/release-notes/0.3.0.md")
        self.assertIn("does not migrate", notes)
        self.assertIn("0.2.x", notes)
        self.assertIn("new world", notes)

    def test_public_name_and_mit_license_are_consistent(self):
        properties = self.read_required("gradle.properties")
        self.assertIn("mod_id=auto_storage", properties)
        self.assertIn("mod_name=Auto Storage", properties)
        self.assertIn("mod_license=MIT", properties)

        license_text = self.read_required("LICENSE")
        self.assertIn("MIT License", license_text)
        self.assertIn("Copyright (c) 2026 swear01", license_text)

        readme = self.read_required("README.md")
        self.assertTrue(readme.startswith("# Auto Storage\n"))
        self.assertIn("## License\n\nMIT License.", readme)
        self.assertNotIn("Magic Storage", readme)

        build = self.read_required("build.gradle")
        self.assertIn("tasks.named('jar', Jar).configure", build)
        self.assertIn("from(rootProject.file('LICENSE'))", build)

        release = self.read_required(".github/workflows/release.yml")
        self.assertIn("name: Auto Storage ${{ steps.release-meta.outputs.version }} Alpha", release)
        self.assertIn('echo "## Auto Storage ${GITHUB_REF_NAME}"', release)

        player_facing = "\n".join([
            self.read_required("src/main/resources/assets/auto_storage/lang/en_us.json"),
            self.read_required("src/main/resources/assets/auto_storage/lang/zh_tw.json"),
            self.read_required("src/main/resources/data/auto_storage/patchouli_books/guide/book.json"),
            self.read_required("src/main/templates/META-INF/neoforge.mods.toml"),
        ])
        self.assertIn("Auto Storage", player_facing)
        self.assertNotIn("Magic Storage", player_facing)
        for locale in ("en_us", "zh_tw"):
            lang = json.loads(self.read_required(
                f"src/main/resources/assets/auto_storage/lang/{locale}.json"
            ))
            self.assertEqual("Auto Storage", lang["itemGroup.auto_storage"])

        for path in ROOT.glob("src/*/resources/META-INF/neoforge.mods.toml"):
            with self.subTest(path=path.relative_to(ROOT)):
                self.assertNotIn("All Rights Reserved", path.read_text())

    def test_current_public_name_reaches_active_docs_and_runtime_diagnostics(self):
        for path in ROOT.joinpath("docs").rglob("*.md"):
            for line_number, line in enumerate(path.read_text().splitlines(), 1):
                if "Magic Storage" in line and "Terraria" not in line:
                    self.fail(
                        f"stale public name in {path.relative_to(ROOT)}:{line_number}"
                    )

        for path in ROOT.joinpath("src").rglob("*"):
            if path.is_file() and path.suffix in {".java", ".json", ".mcmeta", ".toml"}:
                with self.subTest(path=path.relative_to(ROOT)):
                    self.assertNotIn("Magic Storage", path.read_text())

        for path in ROOT.joinpath("scripts").glob("*.py"):
            if path.name.startswith("test_"):
                continue
            with self.subTest(path=path.relative_to(ROOT)):
                self.assertNotIn("Magic Storage", path.read_text())

    def test_current_release_evidence_uses_latest_python_total(self):
        for relative_path in ("docs/notes.md", "docs/plan.md"):
            with self.subTest(path=relative_path):
                self.assertIn("Python 302", self.read_required(relative_path))

    def test_0_2_0_release_evidence_is_recorded(self):
        release_url = "https://github.com/swear01/Auto_Storage/releases/tag/v0.2.0"
        release_hash = "64cbe2705f2a1d20f83eb1bf848c1df7b74ffe9dab0e4fb958cfde498457b43c"
        for relative_path in (
            "docs/notes.md",
            "docs/plan.md",
            "docs/roadmap.md",
            "docs/releasing.md",
        ):
            text = self.read_required(relative_path)
            with self.subTest(path=relative_path):
                self.assertIn(release_url, text)
                self.assertIn(release_hash, text)
                self.assertIn("30468162688", text)

    def test_current_manual_gui_log_check_does_not_pin_historical_version_or_selftest_total(self):
        notes = self.read_required("docs/notes.md")
        manual_section = notes.split("GUI 測試項目仍由當次變更動態決定", 1)[1].split("## Reference Source", 1)[0]
        self.assertIn("SelfTest: [0-9]+ passed, 0 failed, [0-9]+ total", manual_section)
        self.assertNotRegex(manual_section, r"SelfTest: \d+ passed")
        self.assertNotRegex(manual_section, r"0\.1\.\d+ 必須重新產生 current-run artifact")

    def test_client_smoke_workflow_is_manual_only_and_uses_neoforge_runtime_client(self):
        text = self.read_required(".github/workflows/client-smoke.yml")
        self.assertIn("name: Client Smoke", text)
        self.assertIn("workflow_dispatch:", text)
        self.assertNotIn("pull_request", text)
        self.assertNotIn("branches:", text)
        self.assertIn("actions/checkout@v7", text)
        self.assertIn("actions/setup-java@v5", text)
        self.assertIn("gradle/actions/setup-gradle@v6", text)
        self.assertIn("./gradlew build --console=plain --no-daemon", text)
        self.assertIn("cp build/libs/auto_storage-*.jar run/mods/", text)
        self.assertIn(
            "./gradlew stageClientSmokeSupportMods --console=plain --no-daemon",
            text,
        )
        self.assertIn(
            "cp build/client-smoke-mods/patchouli-neoforge.jar run/mods/",
            text,
        )
        self.assertIn(
            "cp build/client-smoke-mods/fusion-connected-textures.jar run/mods/",
            text,
        )
        self.assertIn(
            'python3 -m zipfile -t "run/mods/patchouli-neoforge.jar"',
            text,
        )
        self.assertIn(
            'python3 -m zipfile -t "run/mods/fusion-connected-textures.jar"',
            text,
        )
        self.assertIn('EMI_VERSION="$(python3 scripts/resolve_emi_version.py)"', text)
        self.assertIn('EMI_RUNTIME_VERSION="$(python3 scripts/resolve_emi_runtime.py "$EMI_VERSION")"', text)
        self.assertIn('bash scripts/stage_emi_runtime.sh "$EMI_VERSION" "$EMI_RUNTIME_VERSION"', text)
        self.assertNotIn('./gradlew stageEmiRuntime -Pemi_version="$EMI_VERSION"', text)
        self.assertIn('cp "build/client-smoke-mods/emi-neoforge-${EMI_VERSION}.jar" run/mods/', text)
        self.assertIn("python3 -m zipfile -t", text)
        self.assertNotIn("curl ", text)
        self.assertIn("headlesshq/mc-runtime-test@4.4.0", text)
        self.assertIn("timeout-minutes: 10", text)
        self.assertIn("mc: '1.21.1'", text)
        self.assertIn("modloader: neoforge", text)
        self.assertIn("regex: '.*neoforge.*'", text)
        self.assertIn("mc-runtime-test: neoforge", text)
        self.assertIn("xvfb: 'true'", text)
        self.assertIn("dummy-assets: 'true'", text)
        self.assertIn("headlessmc-command: '--jvm \"-Djava.awt.headless=true\"'", text)
        self.assertIn("Client Smoke is a boot/resource smoke test, not GUI layout approval", text)


if __name__ == "__main__":
    unittest.main()
