#!/usr/bin/env python3
import hashlib
import os
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

try:
    from bump_patch_version import bump_patch_version
except ModuleNotFoundError:
    from scripts.bump_patch_version import bump_patch_version

DEFAULT_JAVA_HOME = Path("/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home")
DEFAULT_PRISM_MINECRAFT_DIR = Path.home() / "Library/Application Support/PrismLauncher/instances/dev/minecraft"
PROJECT_ROOT = Path(__file__).resolve().parents[1]
FUSION_RUNTIME_VERSION = next(
    line.split("=", 1)[1].strip()
    for line in (PROJECT_ROOT / "gradle.properties").read_text().splitlines()
    if line.startswith("fusion_runtime_version=")
)
FUSION_VERSION = "1.2.12"
FUSION_FILENAME = "fusion-1.2.12-neoforge-mc1.21.1.jar"
FUSION_URL = (
    "https://cdn.modrinth.com/data/p19vrgc2/versions/"
    f"{FUSION_RUNTIME_VERSION}/{FUSION_FILENAME}"
)
FUSION_SHA512 = "50604fa4125e846b659479a8bb8bcef5db47460a8185902b8655d8b12c6cc67eb3cc4c08fee45e82a6b215976bea2a480e32ce420f062cea88abe17cb362365c"
MACFIX_FILENAME = "macfix-gui-test.jar"
IRON_FURNACES_FILENAME = "iron-furnaces-gui-test.jar"
FARMERS_DELIGHT_FILENAME = "farmers-delight-gui-test.jar"
TMRV_FILENAME = "tmrv-gui-test.jar"
MEKANISM_FILENAME = "mekanism-gui-test.jar"
MEKANISM_GENERATORS_FILENAME = "mekanism-generators-gui-test.jar"
BOTANIA_FILENAME = "botania-gui-test.jar"
CURIOS_FILENAME = "curios-gui-test.jar"
MODERN_INDUSTRIALIZATION_FILENAME = "modern-industrialization-gui-test.jar"
GUIDEME_FILENAME = "guideme-gui-test.jar"
ARS_NOUVEAU_FILENAME = "ars-nouveau-gui-test.jar"
GECKOLIB_FILENAME = "geckolib-gui-test.jar"
EVILCRAFT_FILENAME = "evilcraft-gui-test.jar"
CYCLOPS_CORE_FILENAME = "cyclops-core-gui-test.jar"
POWAH_FILENAME = "powah-gui-test.jar"
CLOTH_CONFIG_FILENAME = "cloth-config-gui-test.jar"
INDUSTRIAL_FOREGOING_FILENAME = "industrial-foregoing-gui-test.jar"
TITANIUM_FILENAME = "titanium-gui-test.jar"
CREATE_FILENAME = "create-gui-test.jar"
EXTENDED_CRAFTING_FILENAME = "extended-crafting-gui-test.jar"
CUCUMBER_FILENAME = "cucumber-gui-test.jar"


@dataclass(frozen=True)
class DeployResult:
    version: str
    jar: Path
    destination: Path
    backups: list[Path]
    fusion_destination: Path
    fusion_backups: list[Path]
    support_destinations: list[Path]
    support_backups: list[Path]


def run_gradle_build(project_dir: Path, version: str) -> None:
    if not DEFAULT_JAVA_HOME.exists():
        raise RuntimeError(f"JDK 21 not found at {DEFAULT_JAVA_HOME}")
    env = os.environ.copy()
    env["JAVA_HOME"] = str(DEFAULT_JAVA_HOME)
    subprocess.run(
        [
            "./gradlew",
            "build",
            "-x",
            "compatKitArchive",
            "stagePrismGuiSupportMods",
            "--console=plain",
            "--no-daemon",
        ],
        cwd=project_dir,
        env=env,
        check=True,
    )


def auto_storage_jars(mods_dir: Path) -> list[Path]:
    return sorted(mods_dir.glob("auto_storage-*.jar"))


def legacy_auto_storage_jars(mods_dir: Path) -> list[Path]:
    legacy_basename = "magic" + "_storage"
    return sorted(mods_dir.glob(f"{legacy_basename}-*.jar"))


def managed_auto_storage_jars(mods_dir: Path) -> list[Path]:
    return sorted({*auto_storage_jars(mods_dir), *legacy_auto_storage_jars(mods_dir)})


def fusion_jars(mods_dir: Path) -> list[Path]:
    return sorted(mods_dir.glob("fusion-*.jar"))


def macfix_jars(mods_dir: Path) -> list[Path]:
    return sorted({*mods_dir.glob("macfix*.jar"), *mods_dir.glob("MacFix*.jar")})


def iron_furnaces_jars(mods_dir: Path) -> list[Path]:
    return sorted({*mods_dir.glob("iron-furnaces*.jar"), *mods_dir.glob("iron_furnaces*.jar")})


def farmers_delight_jars(mods_dir: Path) -> list[Path]:
    return sorted({*mods_dir.glob("farmers-delight*.jar"), *mods_dir.glob("FarmersDelight*.jar")})


def jei_jars(mods_dir: Path) -> list[Path]:
    return sorted({*mods_dir.glob("jei-*.jar"), *mods_dir.glob("jei_*.jar")})


def tmrv_jars(mods_dir: Path) -> list[Path]:
    return sorted({
        *mods_dir.glob("tmrv*.jar"),
        *mods_dir.glob("TMRV*.jar"),
        *mods_dir.glob("toomanyrecipeviewers*.jar"),
        *mods_dir.glob("TooManyRecipeViewers*.jar"),
    })


def mekanism_jars(mods_dir: Path) -> list[Path]:
    return sorted(
        path
        for path in {
            *mods_dir.glob("mekanism*.jar"),
            *mods_dir.glob("Mekanism*.jar"),
        }
        if "generators" not in path.stem.lower()
    )


def mekanism_generators_jars(mods_dir: Path) -> list[Path]:
    return sorted({
        *mods_dir.glob("mekanism-generators*.jar"),
        *mods_dir.glob("mekanismgenerators*.jar"),
        *mods_dir.glob("MekanismGenerators*.jar"),
    })


def botania_jars(mods_dir: Path) -> list[Path]:
    return sorted({*mods_dir.glob("botania*.jar"), *mods_dir.glob("Botania*.jar")})


def curios_jars(mods_dir: Path) -> list[Path]:
    return sorted({*mods_dir.glob("curios*.jar"), *mods_dir.glob("Curios*.jar")})


def modern_industrialization_jars(mods_dir: Path) -> list[Path]:
    return sorted({
        *mods_dir.glob("modern-industrialization*.jar"),
        *mods_dir.glob("modern_industrialization*.jar"),
        *mods_dir.glob("Modern-Industrialization*.jar"),
    })


def guideme_jars(mods_dir: Path) -> list[Path]:
    return sorted({*mods_dir.glob("guideme*.jar"), *mods_dir.glob("GuideME*.jar")})


def ars_nouveau_jars(mods_dir: Path) -> list[Path]:
    return sorted({*mods_dir.glob("ars-nouveau*.jar"), *mods_dir.glob("ars_nouveau*.jar")})


def geckolib_jars(mods_dir: Path) -> list[Path]:
    return sorted({*mods_dir.glob("geckolib*.jar"), *mods_dir.glob("GeckoLib*.jar")})


def evilcraft_jars(mods_dir: Path) -> list[Path]:
    return sorted({*mods_dir.glob("evilcraft*.jar"), *mods_dir.glob("EvilCraft*.jar")})


def cyclops_core_jars(mods_dir: Path) -> list[Path]:
    return sorted({
        *mods_dir.glob("cyclops-core*.jar"),
        *mods_dir.glob("cyclopscore*.jar"),
        *mods_dir.glob("CyclopsCore*.jar"),
    })


def powah_jars(mods_dir: Path) -> list[Path]:
    return sorted({*mods_dir.glob("powah*.jar"), *mods_dir.glob("Powah*.jar")})


def cloth_config_jars(mods_dir: Path) -> list[Path]:
    return sorted({
        *mods_dir.glob("cloth-config*.jar"),
        *mods_dir.glob("cloth_config*.jar"),
        *mods_dir.glob("ClothConfig*.jar"),
    })


def industrial_foregoing_jars(mods_dir: Path) -> list[Path]:
    return sorted({
        *mods_dir.glob("industrial-foregoing*.jar"),
        *mods_dir.glob("industrial_foregoing*.jar"),
        *mods_dir.glob("industrialforegoing*.jar"),
    })


def titanium_jars(mods_dir: Path) -> list[Path]:
    return sorted({*mods_dir.glob("titanium*.jar"), *mods_dir.glob("Titanium*.jar")})


def create_jars(mods_dir: Path) -> list[Path]:
    return sorted({*mods_dir.glob("create-*.jar"), *mods_dir.glob("Create-*.jar")})


def extended_crafting_jars(mods_dir: Path) -> list[Path]:
    return sorted({
        *mods_dir.glob("extended-crafting*.jar"),
        *mods_dir.glob("extended_crafting*.jar"),
        *mods_dir.glob("ExtendedCrafting*.jar"),
    })


def cucumber_jars(mods_dir: Path) -> list[Path]:
    return sorted({*mods_dir.glob("cucumber*.jar"), *mods_dir.glob("Cucumber*.jar")})


SUPPORT_ARTIFACTS = (
    (MACFIX_FILENAME, macfix_jars),
    (IRON_FURNACES_FILENAME, iron_furnaces_jars),
    (FARMERS_DELIGHT_FILENAME, farmers_delight_jars),
    (TMRV_FILENAME, tmrv_jars),
    (MEKANISM_FILENAME, mekanism_jars),
    (MEKANISM_GENERATORS_FILENAME, mekanism_generators_jars),
    (BOTANIA_FILENAME, botania_jars),
    (CURIOS_FILENAME, curios_jars),
    (MODERN_INDUSTRIALIZATION_FILENAME, modern_industrialization_jars),
    (GUIDEME_FILENAME, guideme_jars),
    (ARS_NOUVEAU_FILENAME, ars_nouveau_jars),
    (GECKOLIB_FILENAME, geckolib_jars),
    (POWAH_FILENAME, powah_jars),
    (CLOTH_CONFIG_FILENAME, cloth_config_jars),
    (INDUSTRIAL_FOREGOING_FILENAME, industrial_foregoing_jars),
    (TITANIUM_FILENAME, titanium_jars),
    (CREATE_FILENAME, create_jars),
    (EXTENDED_CRAFTING_FILENAME, extended_crafting_jars),
    (CUCUMBER_FILENAME, cucumber_jars),
)


def support_jars(mods_dir: Path) -> list[Path]:
    return [
        *[path for _, finder in SUPPORT_ARTIFACTS for path in finder(mods_dir)],
        *evilcraft_jars(mods_dir),
        *cyclops_core_jars(mods_dir),
    ]


def sha512(path: Path) -> str:
    digest = hashlib.sha512()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download_url(url: str, destination: Path) -> None:
    with urllib.request.urlopen(url, timeout=60) as response, destination.open("wb") as output:
        shutil.copyfileobj(response, output)


def create_backup_plan(jars: list[Path], prism_minecraft_dir: Path) -> list[Path]:
    if not jars:
        return []
    backup_dir = prism_minecraft_dir / "auto_storage_backups" / datetime.now().strftime("%Y%m%d-%H%M%S")
    backup_dir.mkdir(parents=True, exist_ok=False)
    return [backup_dir / jar.name for jar in jars]


def backup_existing_jars(
    jars: list[Path],
    prism_minecraft_dir: Path,
    backups: list[Path] | None = None,
) -> list[Path]:
    if not jars:
        return []
    if backups is None:
        backups = create_backup_plan(jars, prism_minecraft_dir)
    if len(backups) != len(jars):
        raise RuntimeError("Backup journal does not match active jar inventory")
    backup_dir = backups[0].parent
    try:
        for jar, dest in zip(jars, backups):
            shutil.move(str(jar), str(dest))
    except BaseException as backup_error:
        rollback_errors = []
        for jar, backup in reversed(list(zip(jars, backups))):
            if not backup.exists():
                continue
            try:
                shutil.move(str(backup), str(jar))
            except BaseException as rollback_error:
                rollback_errors.append(str(rollback_error))
        if backup_dir.exists():
            try:
                backup_dir.rmdir()
            except BaseException as rollback_error:
                rollback_errors.append(str(rollback_error))
        if rollback_errors:
            raise RuntimeError(
                f"Backing up active jars failed ({backup_error}); rollback also failed: {'; '.join(rollback_errors)}"
            ) from backup_error
        raise
    return backups


def deploy(
    project_dir: Path,
    prism_minecraft_dir: Path = DEFAULT_PRISM_MINECRAFT_DIR,
    build_runner=run_gradle_build,
    fusion_downloader=download_url,
) -> DeployResult:
    project_dir = project_dir.resolve()
    prism_minecraft_dir = prism_minecraft_dir.expanduser().resolve()
    mods_dir = prism_minecraft_dir / "mods"
    if not mods_dir.is_dir():
        raise RuntimeError(f"Prism dev mods directory not found: {mods_dir}")
    original_jar_paths = set(managed_auto_storage_jars(mods_dir))
    original_fusion_paths = set(fusion_jars(mods_dir))
    original_support_paths = {
        *jei_jars(mods_dir),
        *support_jars(mods_dir),
    }

    properties_path = project_dir / "gradle.properties"
    original_properties = properties_path.read_text()

    backups = []
    fusion_backups = []
    support_backups = []
    destination = None
    fusion_destination = mods_dir / FUSION_FILENAME
    support_destinations = [mods_dir / filename for filename, _ in SUPPORT_ARTIFACTS]
    staging = None
    fusion_staging = None
    support_stagings = []
    try:
        version = bump_patch_version(properties_path)
        destination = mods_dir / f"auto_storage-{version}.jar"
        build_runner(project_dir, version)

        source_jar = project_dir / "build" / "libs" / f"auto_storage-{version}.jar"
        if not source_jar.is_file():
            raise RuntimeError(f"Built jar not found: {source_jar}")
        support_sources = [
            project_dir / "build" / "prism-gui-mods" / filename
            for filename, _ in SUPPORT_ARTIFACTS
        ]
        missing_support = [str(path) for path in support_sources if not path.is_file()]
        if missing_support:
            raise RuntimeError(
                "Prism GUI support artifact(s) not staged: " + ", ".join(missing_support)
            )

        file_descriptor, staging_name = tempfile.mkstemp(prefix=f".{source_jar.name}.", suffix=".staging", dir=mods_dir)
        os.close(file_descriptor)
        staging = Path(staging_name)
        shutil.copy2(source_jar, staging)

        for source in support_sources:
            file_descriptor, support_staging_name = tempfile.mkstemp(
                prefix=f".{source.name}.", suffix=".staging", dir=mods_dir
            )
            os.close(file_descriptor)
            support_staging = Path(support_staging_name)
            shutil.copy2(source, support_staging)
            support_stagings.append(support_staging)

        installed_fusion = fusion_jars(mods_dir)
        fusion_is_exact = (
            installed_fusion == [fusion_destination]
            and sha512(fusion_destination) == FUSION_SHA512
        )
        if not fusion_is_exact:
            file_descriptor, fusion_staging_name = tempfile.mkstemp(
                prefix=f".{FUSION_FILENAME}.",
                suffix=".staging",
                dir=mods_dir,
            )
            os.close(file_descriptor)
            fusion_staging = Path(fusion_staging_name)
            fusion_downloader(FUSION_URL, fusion_staging)
            actual_fusion_sha512 = sha512(fusion_staging)
            if actual_fusion_sha512 != FUSION_SHA512:
                raise RuntimeError(
                    f"Fusion SHA-512 mismatch: expected {FUSION_SHA512}, got {actual_fusion_sha512}"
                )

        project_jars_to_backup = managed_auto_storage_jars(mods_dir)
        fusion_to_backup = fusion_jars(mods_dir) if fusion_staging is not None else []
        support_to_backup = [*jei_jars(mods_dir), *support_jars(mods_dir)]
        jars_to_backup = [*project_jars_to_backup, *fusion_to_backup, *support_to_backup]
        all_backups = create_backup_plan(jars_to_backup, prism_minecraft_dir)
        backups = all_backups[:len(project_jars_to_backup)]
        fusion_start = len(project_jars_to_backup)
        support_start = fusion_start + len(fusion_to_backup)
        fusion_backups = all_backups[fusion_start:support_start]
        support_backups = all_backups[support_start:]
        backup_existing_jars(jars_to_backup, prism_minecraft_dir, all_backups)
        staging.replace(destination)
        staging = None
        if fusion_staging is not None:
            fusion_staging.replace(fusion_destination)
            fusion_staging = None
        for support_staging, support_destination in zip(
            support_stagings, support_destinations
        ):
            support_staging.replace(support_destination)
        support_stagings = []

        jars = auto_storage_jars(mods_dir)
        if jars != [destination]:
            names = ", ".join(str(p) for p in jars)
            raise RuntimeError(f"Expected exactly one Auto Storage jar in {mods_dir}, found: {names}")
        legacy_jars = legacy_auto_storage_jars(mods_dir)
        if legacy_jars:
            names = ", ".join(str(path) for path in legacy_jars)
            raise RuntimeError(f"Legacy Auto Storage jars remain in {mods_dir}: {names}")
        installed_fusion = fusion_jars(mods_dir)
        if installed_fusion != [fusion_destination]:
            names = ", ".join(str(path) for path in installed_fusion)
            raise RuntimeError(f"Expected exactly one Fusion jar in {mods_dir}, found: {names}")
        actual_fusion_sha512 = sha512(fusion_destination)
        if actual_fusion_sha512 != FUSION_SHA512:
            raise RuntimeError(
                f"Installed Fusion SHA-512 mismatch: expected {FUSION_SHA512}, got {actual_fusion_sha512}"
            )
        installed_support = [finder(mods_dir) for _, finder in SUPPORT_ARTIFACTS]
        for source, expected, installed in zip(
            support_sources, support_destinations, installed_support
        ):
            if installed != [expected]:
                names = ", ".join(str(path) for path in installed)
                raise RuntimeError(
                    f"Expected exactly one GUI support jar named {expected.name}, found: {names}"
                )
            if sha512(source) != sha512(expected):
                raise RuntimeError(f"Installed GUI support jar differs from staged artifact: {expected.name}")
        incompatible_jei = jei_jars(mods_dir)
        if incompatible_jei:
            names = ", ".join(str(path) for path in incompatible_jei)
            raise RuntimeError(f"JEI is incompatible with TMRV and must not remain in {mods_dir}: {names}")

        return DeployResult(
            version=version,
            jar=source_jar,
            destination=destination,
            backups=backups,
            fusion_destination=fusion_destination,
            fusion_backups=fusion_backups,
            support_destinations=support_destinations,
            support_backups=support_backups,
        )
    except BaseException as deploy_error:
        rollback_errors = []
        for staged in (staging, fusion_staging, *support_stagings):
            if staged is not None and staged.exists():
                try:
                    staged.unlink()
                except BaseException as rollback_error:
                    rollback_errors.append(str(rollback_error))
        if destination is not None and destination.exists() and (destination not in original_jar_paths or backups):
            try:
                destination.unlink()
            except BaseException as rollback_error:
                rollback_errors.append(str(rollback_error))
        if (
            fusion_destination.exists()
            and (fusion_destination not in original_fusion_paths or fusion_backups)
        ):
            try:
                fusion_destination.unlink()
            except BaseException as rollback_error:
                rollback_errors.append(str(rollback_error))
        for support_destination in support_destinations:
            if (
                support_destination.exists()
                and (support_destination not in original_support_paths or support_backups)
            ):
                try:
                    support_destination.unlink()
                except BaseException as rollback_error:
                    rollback_errors.append(str(rollback_error))
        for backup in [*backups, *fusion_backups, *support_backups]:
            if not backup.exists():
                continue
            try:
                shutil.move(str(backup), str(mods_dir / backup.name))
            except BaseException as rollback_error:
                rollback_errors.append(str(rollback_error))
        for backup_dir in {
            backup.parent for backup in [*backups, *fusion_backups, *support_backups]
        }:
            if backup_dir.exists():
                try:
                    backup_dir.rmdir()
                except BaseException as rollback_error:
                    rollback_errors.append(str(rollback_error))
        try:
            properties_path.write_text(original_properties)
        except BaseException as rollback_error:
            rollback_errors.append(str(rollback_error))
        if rollback_errors:
            raise RuntimeError(
                f"Deployment failed ({deploy_error}); rollback also failed: {'; '.join(rollback_errors)}"
            ) from deploy_error
        raise


def main() -> int:
    try:
        result = deploy(Path.cwd())
    except Exception as exc:
        print(str(exc), file=sys.stderr)
        return 1
    print(f"Deployed Auto Storage {result.version}")
    print(f"Jar: {result.jar}")
    print(f"Destination: {result.destination}")
    print(f"Fusion: {result.fusion_destination}")
    for path in result.support_destinations:
        print(f"GUI support: {path}")
    if result.backups:
        print("Backed up old jar(s):")
        for path in result.backups:
            print(f"- {path}")
    if result.fusion_backups:
        print("Backed up old Fusion jar(s):")
        for path in result.fusion_backups:
            print(f"- {path}")
    if result.support_backups:
        print("Backed up old GUI support jar(s):")
        for path in result.support_backups:
            print(f"- {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
