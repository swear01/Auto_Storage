import hashlib
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import deploy_prism_dev

class DeployPrismDevTests(unittest.TestCase):
    FUSION_BYTES = b"official fusion test artifact"
    IRON_FURNACES_BYTES = b"iron furnaces gui test artifact"
    FARMERS_DELIGHT_BYTES = b"farmers delight gui test artifact"
    TMRV_BYTES = b"tmrv gui test artifact"
    MEKANISM_BYTES = b"mekanism gui test artifact"
    BOTANIA_BYTES = b"botania gui test artifact"
    CURIOS_BYTES = b"curios gui test artifact"
    BATCHED_SUPPORT = {
        "macfix-gui-test.jar": b"macfix gui test artifact",
        "mekanism-generators-gui-test.jar": b"mekanism generators gui test artifact",
        "modern-industrialization-gui-test.jar": b"modern industrialization gui test artifact",
        "guideme-gui-test.jar": b"guideme gui test artifact",
        "ars-nouveau-gui-test.jar": b"ars nouveau gui test artifact",
        "geckolib-gui-test.jar": b"geckolib gui test artifact",
        "powah-gui-test.jar": b"powah gui test artifact",
        "cloth-config-gui-test.jar": b"cloth config gui test artifact",
        "industrial-foregoing-gui-test.jar": b"industrial foregoing gui test artifact",
        "titanium-gui-test.jar": b"titanium gui test artifact",
        "create-gui-test.jar": b"create gui test artifact",
        "extended-crafting-gui-test.jar": b"extended crafting gui test artifact",
        "cucumber-gui-test.jar": b"cucumber gui test artifact",
    }

    def test_mekanism_finders_keep_core_and_generators_separate(self):
        with tempfile.TemporaryDirectory() as tmp:
            mods = Path(tmp)
            (mods / "mekanism-gui-test.jar").write_bytes(b"core")
            (mods / "mekanism-generators-gui-test.jar").write_bytes(b"generators")

            self.assertEqual(
                ["mekanism-gui-test.jar"],
                [path.name for path in deploy_prism_dev.mekanism_jars(mods)],
            )
            self.assertEqual(
                ["mekanism-generators-gui-test.jar"],
                [path.name for path in deploy_prism_dev.mekanism_generators_jars(mods)],
            )

    def test_gradle_build_uses_single_use_daemon(self):
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp)
            jdk = project / "jdk"
            jdk.mkdir()
            with mock.patch.object(deploy_prism_dev, "DEFAULT_JAVA_HOME", jdk):
                with mock.patch.object(deploy_prism_dev.subprocess, "run") as run:
                    deploy_prism_dev.run_gradle_build(project, "0.1.49")

            command = run.call_args.args[0]
            self.assertIn("--no-daemon", command)
            self.assertIn("-x", command)
            self.assertIn("compatKitArchive", command)
            self.assertEqual(project, run.call_args.kwargs["cwd"])
            self.assertEqual(str(jdk), run.call_args.kwargs["env"]["JAVA_HOME"])

    def stage_support(self, project_dir: Path) -> None:
        support_dir = project_dir / "build" / "prism-gui-mods"
        support_dir.mkdir(parents=True, exist_ok=True)
        (support_dir / deploy_prism_dev.IRON_FURNACES_FILENAME).write_bytes(
            self.IRON_FURNACES_BYTES
        )
        (support_dir / deploy_prism_dev.FARMERS_DELIGHT_FILENAME).write_bytes(
            self.FARMERS_DELIGHT_BYTES
        )
        (support_dir / deploy_prism_dev.TMRV_FILENAME).write_bytes(self.TMRV_BYTES)
        (support_dir / deploy_prism_dev.MEKANISM_FILENAME).write_bytes(
            self.MEKANISM_BYTES
        )
        (support_dir / "botania-gui-test.jar").write_bytes(self.BOTANIA_BYTES)
        (support_dir / "curios-gui-test.jar").write_bytes(self.CURIOS_BYTES)
        for name, contents in self.BATCHED_SUPPORT.items():
            (support_dir / name).write_bytes(contents)

    def deploy(self, project: Path, prism_mc: Path, build_runner):
        original_hash = getattr(deploy_prism_dev, "FUSION_SHA512", None)
        deploy_prism_dev.FUSION_SHA512 = hashlib.sha512(self.FUSION_BYTES).hexdigest()

        def fake_download(url: str, destination: Path) -> None:
            destination.write_bytes(self.FUSION_BYTES)

        try:
            def staged_build(project_dir: Path, version: str) -> None:
                build_runner(project_dir, version)
                self.stage_support(project_dir)

            return deploy_prism_dev.deploy(
                project,
                prism_mc,
                build_runner=staged_build,
                fusion_downloader=fake_download,
            )
        finally:
            if original_hash is None:
                del deploy_prism_dev.FUSION_SHA512
            else:
                deploy_prism_dev.FUSION_SHA512 = original_hash

    def test_deploy_bumps_patch_builds_new_jar_and_leaves_one_auto_storage_jar(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            project = root / "project"
            prism_mc = root / "prism" / "minecraft"
            mods = prism_mc / "mods"
            build_libs = project / "build" / "libs"
            project.mkdir()
            mods.mkdir(parents=True)
            build_libs.mkdir(parents=True)
            (project / "gradle.properties").write_text("mod_id=auto_storage\nmod_version=0.1.0\n")
            (mods / "auto_storage-0.1.0.jar").write_text("old")
            legacy_jar = mods / (("magic" + "_storage") + "-0.2.0.jar")
            legacy_jar.write_text("legacy")
            (mods / "Patchouli.jar").write_text("patchouli")

            def fake_build(project_dir: Path, version: str) -> None:
                self.assertEqual((project_dir / "gradle.properties").read_text(), "mod_id=auto_storage\nmod_version=0.1.1\n")
                (project_dir / "build" / "libs" / f"auto_storage-{version}.jar").write_text("new")

            result = self.deploy(project, prism_mc, fake_build)

            self.assertEqual(result.version, "0.1.1")
            self.assertEqual((project / "gradle.properties").read_text(), "mod_id=auto_storage\nmod_version=0.1.1\n")
            self.assertEqual((mods / "auto_storage-0.1.1.jar").read_text(), "new")
            self.assertEqual(sorted(p.name for p in mods.glob("auto_storage-*.jar")), ["auto_storage-0.1.1.jar"])
            self.assertEqual((mods / "Patchouli.jar").read_text(), "patchouli")
            self.assertEqual(len(result.backups), 2)
            self.assertFalse((mods / "auto_storage-0.1.0.jar").exists())
            self.assertFalse(legacy_jar.exists())
            self.assertEqual(
                {"auto_storage-0.1.0.jar": "old", legacy_jar.name: "legacy"},
                {path.name: path.read_text() for path in result.backups},
            )
            self.assertEqual(
                self.FUSION_BYTES,
                (mods / deploy_prism_dev.FUSION_FILENAME).read_bytes(),
            )
            self.assertEqual(
                [deploy_prism_dev.FUSION_FILENAME],
                [path.name for path in deploy_prism_dev.fusion_jars(mods)],
            )
            self.assertEqual(
                self.IRON_FURNACES_BYTES,
                (mods / deploy_prism_dev.IRON_FURNACES_FILENAME).read_bytes(),
            )
            self.assertEqual(
                self.FARMERS_DELIGHT_BYTES,
                (mods / deploy_prism_dev.FARMERS_DELIGHT_FILENAME).read_bytes(),
            )
            self.assertEqual(
                self.TMRV_BYTES,
                (mods / deploy_prism_dev.TMRV_FILENAME).read_bytes(),
            )
            self.assertEqual(
                self.MEKANISM_BYTES,
                (mods / deploy_prism_dev.MEKANISM_FILENAME).read_bytes(),
            )
            self.assertEqual(
                self.BOTANIA_BYTES,
                (mods / "botania-gui-test.jar").read_bytes(),
            )
            self.assertEqual(
                self.CURIOS_BYTES,
                (mods / "curios-gui-test.jar").read_bytes(),
            )

    def test_deploy_replaces_stale_gui_support_mods_and_keeps_unrelated_mods(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            project = root / "project"
            prism_mc = root / "prism" / "minecraft"
            mods = prism_mc / "mods"
            (project / "build" / "libs").mkdir(parents=True)
            mods.mkdir(parents=True)
            (project / "gradle.properties").write_text("mod_id=auto_storage\nmod_version=0.1.0\n")
            (mods / "auto_storage-0.1.0.jar").write_text("old")
            (mods / "iron-furnaces-old.jar").write_text("old iron")
            (mods / "farmers-delight-old.jar").write_text("old farmers")
            (mods / "jei-old.jar").write_text("old jei")
            (mods / "tmrv-old.jar").write_text("old tmrv")
            (mods / "botania-old.jar").write_text("old botania")
            (mods / "curios-old.jar").write_text("old curios")
            (mods / "evilcraft-old.jar").write_text("old evilcraft")
            (mods / "cyclops-core-old.jar").write_text("old cyclops")
            unrelated = mods / "Patchouli.jar"
            unrelated.write_text("keep")

            def fake_build(project_dir: Path, version: str) -> None:
                (project_dir / "build" / "libs" / f"auto_storage-{version}.jar").write_text("new")
                self.stage_support(project_dir)

            result = self.deploy(project, prism_mc, fake_build)

            self.assertEqual("keep", unrelated.read_text())
            self.assertEqual(
                [deploy_prism_dev.IRON_FURNACES_FILENAME],
                [path.name for path in deploy_prism_dev.iron_furnaces_jars(mods)],
            )
            self.assertEqual(
                [deploy_prism_dev.FARMERS_DELIGHT_FILENAME],
                [path.name for path in deploy_prism_dev.farmers_delight_jars(mods)],
            )
            self.assertEqual(
                [deploy_prism_dev.TMRV_FILENAME],
                [path.name for path in deploy_prism_dev.tmrv_jars(mods)],
            )
            self.assertEqual(
                ["botania-gui-test.jar"],
                [path.name for path in mods.glob("botania*.jar")],
            )
            self.assertEqual(
                ["curios-gui-test.jar"],
                [path.name for path in mods.glob("curios*.jar")],
            )
            self.assertEqual([], deploy_prism_dev.jei_jars(mods))
            self.assertEqual([], deploy_prism_dev.evilcraft_jars(mods))
            self.assertEqual([], deploy_prism_dev.cyclops_core_jars(mods))
            self.assertEqual(8, len(result.support_backups))
            self.assertEqual(
                {
                    "old iron",
                    "old farmers",
                    "old jei",
                    "old tmrv",
                    "old botania",
                    "old curios",
                    "old evilcraft",
                    "old cyclops",
                },
                {path.read_text() for path in result.support_backups},
            )

    def test_deploy_installs_batched_compatibility_pack_once_without_pneumaticcraft(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            project = root / "project"
            prism_mc = root / "prism" / "minecraft"
            mods = prism_mc / "mods"
            (project / "build" / "libs").mkdir(parents=True)
            mods.mkdir(parents=True)
            (project / "gradle.properties").write_text(
                "mod_id=auto_storage\nmod_version=0.1.0\n"
            )
            (mods / "auto_storage-0.1.0.jar").write_text("old")

            def fake_build(project_dir: Path, version: str) -> None:
                (project_dir / "build" / "libs" / f"auto_storage-{version}.jar").write_text(
                    "new"
                )

            result = self.deploy(project, prism_mc, fake_build)

            installed = {path.name for path in result.support_destinations}
            self.assertTrue(set(self.BATCHED_SUPPORT) <= installed)
            self.assertNotIn("evilcraft-gui-test.jar", installed)
            self.assertNotIn("cyclops-core-gui-test.jar", installed)
            self.assertEqual(1, sum(name == "guideme-gui-test.jar" for name in installed))
            self.assertEqual(1, sum(name == "curios-gui-test.jar" for name in installed))
            self.assertFalse(any("pneumatic" in name for name in installed))
            for name, contents in self.BATCHED_SUPPORT.items():
                self.assertEqual(contents, (mods / name).read_bytes())

    def test_batched_support_validation_failure_rolls_back_every_active_jar(self):
        class DeploymentAbort(BaseException):
            pass

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            project = root / "project"
            prism_mc = root / "prism" / "minecraft"
            mods = prism_mc / "mods"
            (project / "build" / "libs").mkdir(parents=True)
            mods.mkdir(parents=True)
            original_properties = "mod_id=auto_storage\nmod_version=0.1.0\n"
            (project / "gradle.properties").write_text(original_properties)
            old_auto_storage = mods / "auto_storage-0.1.0.jar"
            old_auto_storage.write_text("old auto storage")
            old_support = {}
            for name in self.BATCHED_SUPPORT:
                active = mods / name.replace("-gui-test.jar", "-old.jar")
                active.write_text(f"old {name}")
                old_support[active] = active.read_bytes()

            def fake_build(project_dir: Path, version: str) -> None:
                (project_dir / "build" / "libs" / f"auto_storage-{version}.jar").write_text(
                    "new"
                )

            original_sha512 = deploy_prism_dev.sha512

            def interrupted_hash(path: Path) -> str:
                if path.name == "create-gui-test.jar":
                    raise DeploymentAbort("interrupted during support hash verification")
                return original_sha512(path)

            deploy_prism_dev.sha512 = interrupted_hash
            try:
                with self.assertRaisesRegex(
                    DeploymentAbort, "interrupted during support hash verification"
                ):
                    self.deploy(project, prism_mc, fake_build)
            finally:
                deploy_prism_dev.sha512 = original_sha512

            self.assertEqual(original_properties, (project / "gradle.properties").read_text())
            self.assertEqual("old auto storage", old_auto_storage.read_text())
            self.assertEqual(old_support, {path: path.read_bytes() for path in old_support})
            self.assertFalse(any((mods / name).exists() for name in self.BATCHED_SUPPORT))
            self.assertEqual([], list(mods.glob(".*.staging")))

    def test_deploy_restores_version_when_build_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            project = root / "project"
            prism_mc = root / "prism" / "minecraft"
            mods = prism_mc / "mods"
            project.mkdir()
            mods.mkdir(parents=True)
            original_properties = "mod_id=auto_storage\nmod_version=0.1.0\n"
            (project / "gradle.properties").write_text(original_properties)
            (mods / "auto_storage-0.1.0.jar").write_text("old")

            def failing_build(project_dir: Path, version: str) -> None:
                raise RuntimeError("build failed")

            with self.assertRaisesRegex(RuntimeError, "build failed"):
                self.deploy(project, prism_mc, failing_build)

            self.assertEqual((project / "gradle.properties").read_text(), original_properties)
            self.assertEqual((mods / "auto_storage-0.1.0.jar").read_text(), "old")

    def test_deploy_copy_failure_restores_version_and_active_jar(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            project = root / "project"
            prism_mc = root / "prism" / "minecraft"
            mods = prism_mc / "mods"
            (project / "build" / "libs").mkdir(parents=True)
            mods.mkdir(parents=True)
            original_properties = "mod_id=auto_storage\nmod_version=0.1.0\n"
            (project / "gradle.properties").write_text(original_properties)
            old_jar = mods / "auto_storage-0.1.0.jar"
            old_jar.write_text("old")

            def fake_build(project_dir: Path, version: str) -> None:
                (project_dir / "build" / "libs" / f"auto_storage-{version}.jar").write_text("new")

            original_copy2 = deploy_prism_dev.shutil.copy2
            deploy_prism_dev.shutil.copy2 = lambda source, destination: (_ for _ in ()).throw(OSError("copy failed"))
            try:
                with self.assertRaisesRegex(OSError, "copy failed"):
                    self.deploy(project, prism_mc, fake_build)
            finally:
                deploy_prism_dev.shutil.copy2 = original_copy2

            self.assertEqual(original_properties, (project / "gradle.properties").read_text())
            self.assertEqual("old", old_jar.read_text())
            self.assertEqual([old_jar], deploy_prism_dev.auto_storage_jars(mods))

    def test_deploy_rolls_back_when_bump_is_interrupted_after_writing_properties(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            project = root / "project"
            prism_mc = root / "prism" / "minecraft"
            mods = prism_mc / "mods"
            project.mkdir()
            mods.mkdir(parents=True)
            original_properties = "mod_id=auto_storage\nmod_version=0.1.0\n"
            properties = project / "gradle.properties"
            properties.write_text(original_properties)
            old_jar = mods / "auto_storage-0.1.0.jar"
            old_jar.write_text("old")
            original_bump = deploy_prism_dev.bump_patch_version

            def interrupted_bump(properties_path: Path) -> str:
                properties_path.write_text("mod_id=auto_storage\nmod_version=0.1.1\n")
                raise KeyboardInterrupt("interrupted after bump")

            deploy_prism_dev.bump_patch_version = interrupted_bump
            try:
                with self.assertRaisesRegex(KeyboardInterrupt, "interrupted after bump"):
                    self.deploy(project, prism_mc, deploy_prism_dev.run_gradle_build)
            finally:
                deploy_prism_dev.bump_patch_version = original_bump

            self.assertEqual(original_properties, properties.read_text())
            self.assertEqual("old", old_jar.read_text())
            self.assertEqual([old_jar], deploy_prism_dev.auto_storage_jars(mods))

    def test_deploy_rolls_back_version_and_active_jar_on_keyboard_interrupt_after_swap(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            project = root / "project"
            prism_mc = root / "prism" / "minecraft"
            mods = prism_mc / "mods"
            (project / "build" / "libs").mkdir(parents=True)
            mods.mkdir(parents=True)
            original_properties = "mod_id=auto_storage\nmod_version=0.1.0\n"
            properties = project / "gradle.properties"
            properties.write_text(original_properties)
            old_jar = mods / "auto_storage-0.1.0.jar"
            old_jar.write_text("old")

            def fake_build(project_dir: Path, version: str) -> None:
                (project_dir / "build" / "libs" / f"auto_storage-{version}.jar").write_text("new")

            original_jar_finder = deploy_prism_dev.auto_storage_jars
            calls = 0

            def interrupted_jar_finder(mods_dir: Path) -> list[Path]:
                nonlocal calls
                calls += 1
                if calls == 3:
                    raise KeyboardInterrupt("interrupted after swap")
                return original_jar_finder(mods_dir)

            deploy_prism_dev.auto_storage_jars = interrupted_jar_finder
            try:
                with self.assertRaisesRegex(KeyboardInterrupt, "interrupted after swap"):
                    self.deploy(project, prism_mc, fake_build)
            finally:
                deploy_prism_dev.auto_storage_jars = original_jar_finder

            self.assertEqual(original_properties, properties.read_text())
            self.assertEqual("old", old_jar.read_text())
            self.assertEqual([old_jar], deploy_prism_dev.auto_storage_jars(mods))

    def test_deploy_rolls_back_partial_backup_on_base_exception(self):
        class DeploymentAbort(BaseException):
            pass

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            project = root / "project"
            prism_mc = root / "prism" / "minecraft"
            mods = prism_mc / "mods"
            (project / "build" / "libs").mkdir(parents=True)
            mods.mkdir(parents=True)
            original_properties = "mod_id=auto_storage\nmod_version=0.1.0\n"
            properties = project / "gradle.properties"
            properties.write_text(original_properties)
            old_jars = [
                mods / "auto_storage-0.0.9.jar",
                mods / "auto_storage-0.1.0.jar",
            ]
            for index, jar in enumerate(old_jars):
                jar.write_text(f"old-{index}")

            def fake_build(project_dir: Path, version: str) -> None:
                (project_dir / "build" / "libs" / f"auto_storage-{version}.jar").write_text("new")

            original_move = deploy_prism_dev.shutil.move
            calls = 0

            def interrupted_move(source, destination):
                nonlocal calls
                calls += 1
                if calls == 2:
                    raise DeploymentAbort("interrupted during backup")
                return original_move(source, destination)

            deploy_prism_dev.shutil.move = interrupted_move
            try:
                with self.assertRaisesRegex(DeploymentAbort, "interrupted during backup"):
                    self.deploy(project, prism_mc, fake_build)
            finally:
                deploy_prism_dev.shutil.move = original_move

            self.assertEqual(original_properties, properties.read_text())
            self.assertEqual(["old-0", "old-1"], [jar.read_text() for jar in old_jars])
            self.assertEqual(old_jars, deploy_prism_dev.auto_storage_jars(mods))

    def test_fusion_artifact_pin_matches_the_approved_official_release(self):
        self.assertEqual("1.2.12", deploy_prism_dev.FUSION_VERSION)
        self.assertEqual("h2GrA0Ku", deploy_prism_dev.FUSION_RUNTIME_VERSION)
        self.assertEqual(
            "fusion-1.2.12-neoforge-mc1.21.1.jar",
            deploy_prism_dev.FUSION_FILENAME,
        )
        self.assertEqual(
            "https://cdn.modrinth.com/data/p19vrgc2/versions/h2GrA0Ku/fusion-1.2.12-neoforge-mc1.21.1.jar",
            deploy_prism_dev.FUSION_URL,
        )
        self.assertEqual(
            "50604fa4125e846b659479a8bb8bcef5db47460a8185902b8655d8b12c6cc67eb3cc4c08fee45e82a6b215976bea2a480e32ce420f062cea88abe17cb362365c",
            deploy_prism_dev.FUSION_SHA512,
        )

    def test_macfix_is_part_of_the_transactional_gui_support_pack(self):
        self.assertEqual("macfix-gui-test.jar", deploy_prism_dev.MACFIX_FILENAME)
        self.assertIn(
            (deploy_prism_dev.MACFIX_FILENAME, deploy_prism_dev.macfix_jars),
            deploy_prism_dev.SUPPORT_ARTIFACTS,
        )

    def test_hash_mismatch_leaves_version_and_both_active_mods_untouched(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            project = root / "project"
            prism_mc = root / "prism" / "minecraft"
            mods = prism_mc / "mods"
            (project / "build" / "libs").mkdir(parents=True)
            mods.mkdir(parents=True)
            original_properties = "mod_id=auto_storage\nmod_version=0.1.0\n"
            (project / "gradle.properties").write_text(original_properties)
            old_auto_storage = mods / "auto_storage-0.1.0.jar"
            old_fusion = mods / "fusion-1.2.11-neoforge-mc1.21.1.jar"
            old_auto_storage.write_text("old auto storage")
            old_fusion.write_text("old fusion")

            def fake_build(project_dir: Path, version: str) -> None:
                (project_dir / "build" / "libs" / f"auto_storage-{version}.jar").write_text("new")
                self.stage_support(project_dir)

            def tampered_download(url: str, destination: Path) -> None:
                destination.write_bytes(b"tampered")

            with self.assertRaisesRegex(RuntimeError, "Fusion SHA-512 mismatch"):
                deploy_prism_dev.deploy(
                    project,
                    prism_mc,
                    build_runner=fake_build,
                    fusion_downloader=tampered_download,
                )

            self.assertEqual(original_properties, (project / "gradle.properties").read_text())
            self.assertTrue(old_auto_storage.is_file(), "old Auto Storage jar was stranded outside mods")
            self.assertTrue(old_fusion.is_file(), "old Fusion jar was stranded outside mods")
            self.assertEqual("old auto storage", old_auto_storage.read_text())
            self.assertEqual("old fusion", old_fusion.read_text())
            self.assertEqual([old_auto_storage], deploy_prism_dev.auto_storage_jars(mods))
            self.assertEqual([old_fusion], deploy_prism_dev.fusion_jars(mods))

    def test_base_exception_after_both_swaps_rolls_back_magic_fusion_and_version(self):
        class DeploymentAbort(BaseException):
            pass

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            project = root / "project"
            prism_mc = root / "prism" / "minecraft"
            mods = prism_mc / "mods"
            (project / "build" / "libs").mkdir(parents=True)
            mods.mkdir(parents=True)
            original_properties = "mod_id=auto_storage\nmod_version=0.1.0\n"
            (project / "gradle.properties").write_text(original_properties)
            old_auto_storage = mods / "auto_storage-0.1.0.jar"
            old_fusion = mods / "fusion-1.2.11-neoforge-mc1.21.1.jar"
            old_auto_storage.write_text("old auto storage")
            old_fusion.write_text("old fusion")

            def fake_build(project_dir: Path, version: str) -> None:
                (project_dir / "build" / "libs" / f"auto_storage-{version}.jar").write_text("new auto storage")
                self.stage_support(project_dir)

            original_hash = getattr(deploy_prism_dev, "FUSION_SHA512", None)
            deploy_prism_dev.FUSION_SHA512 = hashlib.sha512(self.FUSION_BYTES).hexdigest()

            def fake_download(url: str, destination: Path) -> None:
                destination.write_bytes(self.FUSION_BYTES)

            original_finder = deploy_prism_dev.auto_storage_jars

            def interrupted_finder(mods_dir: Path):
                expected_auto_storage = mods_dir / "auto_storage-0.1.1.jar"
                expected_fusion = mods_dir / deploy_prism_dev.FUSION_FILENAME
                if expected_auto_storage.exists() and expected_fusion.exists():
                    raise DeploymentAbort("interrupted after both swaps")
                return original_finder(mods_dir)

            deploy_prism_dev.auto_storage_jars = interrupted_finder
            try:
                with self.assertRaisesRegex(DeploymentAbort, "interrupted after both swaps"):
                    deploy_prism_dev.deploy(
                        project,
                        prism_mc,
                        build_runner=fake_build,
                        fusion_downloader=fake_download,
                    )
            finally:
                deploy_prism_dev.auto_storage_jars = original_finder
                if original_hash is None:
                    del deploy_prism_dev.FUSION_SHA512
                else:
                    deploy_prism_dev.FUSION_SHA512 = original_hash

            self.assertEqual(original_properties, (project / "gradle.properties").read_text())
            self.assertEqual("old auto storage", old_auto_storage.read_text())
            self.assertEqual("old fusion", old_fusion.read_text())
            self.assertEqual([old_auto_storage], deploy_prism_dev.auto_storage_jars(mods))
            self.assertEqual([old_fusion], deploy_prism_dev.fusion_jars(mods))

    def test_base_exception_at_backup_journal_handoff_restores_both_mods(self):
        class DeploymentAbort(BaseException):
            pass

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            project = root / "project"
            prism_mc = root / "prism" / "minecraft"
            mods = prism_mc / "mods"
            (project / "build" / "libs").mkdir(parents=True)
            mods.mkdir(parents=True)
            original_properties = "mod_id=auto_storage\nmod_version=0.1.0\n"
            (project / "gradle.properties").write_text(original_properties)
            old_auto_storage = mods / "auto_storage-0.1.0.jar"
            old_fusion = mods / "fusion-1.2.11-neoforge-mc1.21.1.jar"
            unrelated = mods / "Patchouli.jar"
            old_auto_storage.write_text("old auto storage")
            old_fusion.write_text("old fusion")
            unrelated.write_text("unrelated")

            def fake_build(project_dir: Path, version: str) -> None:
                (project_dir / "build" / "libs" / f"auto_storage-{version}.jar").write_text("new auto storage")
                self.stage_support(project_dir)

            original_hash = deploy_prism_dev.FUSION_SHA512
            deploy_prism_dev.FUSION_SHA512 = hashlib.sha512(self.FUSION_BYTES).hexdigest()

            def fake_download(url: str, destination: Path) -> None:
                destination.write_bytes(self.FUSION_BYTES)

            original_backup = deploy_prism_dev.backup_existing_jars

            def interrupted_backup(jars: list[Path], prism_minecraft_dir: Path, backups=None):
                if backups is None:
                    original_backup(jars, prism_minecraft_dir)
                else:
                    original_backup(jars, prism_minecraft_dir, backups)
                raise DeploymentAbort("interrupted at backup journal handoff")

            deploy_prism_dev.backup_existing_jars = interrupted_backup
            try:
                with self.assertRaisesRegex(DeploymentAbort, "interrupted at backup journal handoff"):
                    deploy_prism_dev.deploy(
                        project,
                        prism_mc,
                        build_runner=fake_build,
                        fusion_downloader=fake_download,
                    )
            finally:
                deploy_prism_dev.backup_existing_jars = original_backup
                deploy_prism_dev.FUSION_SHA512 = original_hash

            self.assertEqual(original_properties, (project / "gradle.properties").read_text())
            self.assertTrue(old_auto_storage.is_file(), "old Auto Storage jar was stranded outside mods")
            self.assertTrue(old_fusion.is_file(), "old Fusion jar was stranded outside mods")
            self.assertEqual("old auto storage", old_auto_storage.read_text())
            self.assertEqual("old fusion", old_fusion.read_text())
            self.assertEqual("unrelated", unrelated.read_text())
            self.assertEqual([old_auto_storage], deploy_prism_dev.auto_storage_jars(mods))
            self.assertEqual([old_fusion], deploy_prism_dev.fusion_jars(mods))
            self.assertEqual([], list(mods.glob(".*.staging")))
            backup_root = prism_mc / "auto_storage_backups"
            self.assertFalse(backup_root.exists() and any(backup_root.iterdir()))

if __name__ == "__main__":
    unittest.main()
