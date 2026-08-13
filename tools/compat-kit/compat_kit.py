#!/usr/bin/env python3

import argparse
import base64
import copy
import hashlib
import itertools
import json
import math
import os
import re
import shlex
import shutil
import subprocess
import sys
import threading
import time
import tomllib
import zipfile
from pathlib import Path


SCHEMA_VERSION = 1
SCAN_CACHE_VERSION = 17
CANDIDATE_CLASSIFIER_VERSION = 4
SCAN_CACHE_DIRECTORY = (
    f"v{SCAN_CACHE_VERSION}-classifier-{CANDIDATE_CLASSIFIER_VERSION}"
)
LEGACY_SCAN_CACHE_VERSIONS = frozenset({7, 8, 9, 10, 11, 12, 13, 14, 15, 16})
MAX_JAR_BYTES = 512 * 1024 * 1024
MAX_ARCHIVE_ENTRIES = 100_000
MAX_UNCOMPRESSED_BYTES = 2 * 1024 * 1024 * 1024
MAX_MOD_METADATA_BYTES = 1024 * 1024
MAX_CLASS_BYTES = 16 * 1024 * 1024
MAX_CLASSPATH_JARS = 128
MAX_CLASSPATH_CLASSES = 200_000
MAX_CANDIDATE_CLASSES = 2_000
MAX_NESTED_CLASS_OWNER_DEPTH = 1_024
MAX_SIGNATURE_BYTES = 256 * 1024
MAX_PRIVATE_BYTECODE_BYTES = 1024 * 1024
MAX_SOURCE_FILES = 10_000
MAX_RECIPE_FILES = 50_000
MAX_RECIPE_JSON_BYTES = 1024 * 1024
MAX_PACK_METADATA_BYTES = 1024 * 1024
MAX_RECIPE_SAMPLES_PER_SERIALIZER = 16
MAX_RUNTIME_PROBE_RECIPES = 50_000
MAX_RUNTIME_PROBE_VALUES = 4_096
GRADLE_INTEGER_MAX = 2_147_483_647
JAVA_LONG_MAX = 9_223_372_036_854_775_807
TOOL_VERSION = "0.3.1"
SOURCE_EVIDENCE_PATH_PATTERN = (
    r"^(?!/)(?![A-Za-z]:)(?!.*(?:^|/)\.\.?(?:/|$))(?!.*//)(?!.*\\)"
    r"(?!.*[\u0000-\u001f\u007f])(?:[^/]+/)*[^/]+\.java$"
)
PUBLISHED_ADDON_EXAMPLE_FILES = (
    "src/main/java/example/autostorage/ExampleAddon.java",
)
PUBLISHED_SCHEMA_FILES = (
    "compat-audit.schema.json",
    "compat-contract.schema.json",
    "compat-conformance-plan.schema.json",
    "compat-delta.schema.json",
    "compat-generation-plan.schema.json",
    "compat-proposals.schema.json",
    "compat-report.schema.json",
    "compat-resource-plan.schema.json",
    "compat-runtime-probe-plan.schema.json",
    "compat-runtime-probe.schema.json",
)
BUNDLED_FIXED_SOURCE_SETS = frozenset({
    "main",
    "test",
    "fusionRuntime",
    "apiTest",
    "recipeAddonFixture",
    "pneumaticCraftFixture",
    "compatibilityMatrixFixture",
    "api",
    "addonExample",
    "compatKitGeneratedFixture",
})
REQUIRED_VERIFICATION_CHECKS = (
    "absent_target_no_classload",
    "present_target_load_once",
    "ingredient_shortage_atomic",
    "destination_capacity_atomic",
    "checked_overflow_atomic",
    "stale_recipe_holder_atomic",
    "catalyst_tool_remainder_exact",
    "multi_output_merge_exact",
    "mixed_resource_rollback_atomic",
    "dedicated_server_client_isolation",
    "api_only_compilation",
    "all_mod_coexistence",
)
MOD_METADATA_PATHS = (
    "META-INF/neoforge.mods.toml",
    "META-INF/mods.toml",
)
RECIPE_TERMS = ("recipe", "serializer")
RESOURCE_TERMS = (
    "chemical",
    "energy",
    "fluid",
    "gas",
    "mana",
    "power",
    "source",
)
STATION_TERMS = (
    "assembler",
    "crusher",
    "furnace",
    "inscriber",
    "machine",
    "mill",
    "processor",
    "station",
)
RECIPE_BUILDER_TERMS = ("recipebuilder", "recipe_builder")
DATAGEN_TERMS = ("datagen", "datagenerator", ".data.", ".datagen.")
CLIENT_VIEWER_TERMS = (
    ".client.",
    ".emi.",
    ".jei.",
    ".rei.",
    "recipecategory",
    "recipeviewer",
    "recipewidget",
)
CURRENT_CANDIDATE_BUCKETS = (
    "recipe_classes",
    "recipe_types",
    "recipe_serializers",
    "recipe_builders",
    "datagen_classes",
    "client_viewer_classes",
    "station_classes",
    "block_entity_classes",
    "resource_apis",
)
RISK_PATTERNS = (
    ("chance_output", re.compile(r"\bgetChance(?:\s*\(|:)"), "getChance"),
    ("randomness", re.compile(r"\brandom(?:\s*\(|:)"), "random"),
    ("randomness", re.compile(r"\bRandom(?:Source)?\b"), "Random"),
    (
        "randomness",
        re.compile(r"\bThreadLocalRandom\b"),
        "ThreadLocalRandom",
    ),
    (
        "randomness",
        re.compile(r"\bSplittableRandom\b"),
        "SplittableRandom",
    ),
    (
        "randomness",
        re.compile(r"\bSecureRandom\b"),
        "SecureRandom",
    ),
    (
        "randomness",
        re.compile(r"\bRandomGenerator\b"),
        "RandomGenerator",
    ),
    (
        "multiblock",
        re.compile(r"\b(?:MultiBlock|Multiblock)[A-Za-z0-9_$]*\b"),
        "multiblock API",
    ),
    (
        "live_machine_state",
        re.compile(
            r"\b(?:BlockEntity|IItemHandler|IFluidHandler|IEnergyStorage|"
            r"IChemicalHandler)\b"
        ),
        "live machine state",
    ),
    (
        "generic_ingredients",
        re.compile(r"\bgetIngredients(?:\s*\(|:)"),
        "getIngredients",
    ),
    ("simulation_required", re.compile(r"\binsertItem(?:\s*\(|:)"), "insertItem"),
    ("simulation_required", re.compile(r"\bextractItem(?:\s*\(|:)"), "extractItem"),
    ("simulation_required", re.compile(r"\bfill(?:\s*\(|:)"), "fill"),
    ("simulation_required", re.compile(r"\bdrain(?:\s*\(|:)"), "drain"),
    (
        "simulation_required",
        re.compile(r"\breceiveEnergy(?:\s*\(|:)"),
        "receiveEnergy",
    ),
    (
        "simulation_required",
        re.compile(r"\bextractEnergy(?:\s*\(|:)"),
        "extractEnergy",
    ),
    ("world_mutation", re.compile(r"\b(?:Level|ServerLevel|BlockPos)\b"), "world API"),
    ("entity_mutation", re.compile(r"\b(?:Entity|LivingEntity|Player)\b"), "entity API"),
    ("unbounded_output", re.compile(r"\b(?:Stream|Iterator)\s*<"), "streaming output"),
)
AUDIT_TOP_KEYS = {
    "schema",
    "scanner_format",
    "kind",
    "target",
    "artifact",
    "ancestry_classpath",
    "ancestry_dependencies",
    "source",
    "structural_class_graph",
    "structural_hierarchy",
    "structural_candidate_inventory_sha256",
    "candidates",
    "recipe_data",
    "risks",
}
CONTRACT_TOP_KEYS = {
    "schema",
    "kind",
    "target",
    "source_audit_sha256",
    "source_recipe_inventory_sha256",
    "source_recipe_data_sha256",
    "families",
    "matrix",
    "verification",
}
FAMILY_KEYS = {
    "id",
    "class",
    "status",
    "recipe_type",
    "station",
    "inputs",
    "outputs",
    "costs",
    "risks",
    "evidence",
    "decision",
}
TARGET_KEYS = {
    "mod_id",
    "display_name",
    "version",
    "dependency",
    "repositories",
    "runtime_dependencies",
    "runtime_artifact_transforms",
}
RUNTIME_ARTIFACT_TRANSFORM_KEYS = {"sha256", "remove_entries"}
STATION_KEYS = {"descriptor_id", "category", "variants"}
VARIANT_KEYS = {"item", "rate", "bounds"}
RATE_KEYS = {"numerator", "denominator"}
TERM_KEYS = {"role", "resource_kind", "amount", "selector"}
COST_KEYS = {"resource_kind", "amount", "selector"}
VERIFICATION_KEYS = {
    "fixture",
    "expected_game_tests",
    "game_test_task",
    "gradle_tasks",
    "checks",
    "evidence",
}
VERIFICATION_EVIDENCE_KEYS = {"task", "source", "marker"}
MATRIX_LIST_KEYS = (
    "mods",
    "descriptors",
    "resourceKinds",
    "acceptedRecipes",
    "rejectedDescriptors",
    "rejectedResourceKinds",
)
MATRIX_KEYS = set(MATRIX_LIST_KEYS) | {"recipeInventory"}
JAVA_RESERVED_IDENTIFIERS = frozenset(
    """
    abstract assert boolean break byte case catch char class const continue
    default do double else enum extends final finally float for goto if
    implements import instanceof int interface long native new package private
    protected public return short static strictfp super switch synchronized this
    throw throws transient try void volatile while true false null
    _ exports module non-sealed open opens permits provides record requires
    sealed to transitive uses var when with yield
    """.split()
)
JAVA_CONTEXTUAL_KEYWORDS = frozenset(
    """
    exports opens requires uses yield module permits sealed var non-sealed
    provides to when open record transitive with
    """.split()
)
JAVA_HARD_RESERVED_IDENTIFIERS = (
    JAVA_RESERVED_IDENTIFIERS - JAVA_CONTEXTUAL_KEYWORDS
)
GENERATION_RENDERER_TYPES = frozenset({
    "BigDecimal",
    "Block",
    "Blocks",
    "BuiltInRegistries",
    "Component",
    "DeferredRegister",
    "Item",
    "ItemStack",
    "Items",
    "List",
    "MachineCategory",
    "MachineDescriptor",
    "MachineDescriptorApi",
    "MachineVariant",
    "MachineWorkRate",
    "Objects",
    "RecipeFamily",
    "RecipeFamilyApi",
    "RecipeFamilyCost",
    "RecipeFamilyFactories",
    "RecipePresentationKind",
    "ResourceLocation",
})
CONFORMANCE_RENDERER_TYPES = frozenset({
    "CompatibilityConformanceHarness",
    "Dist",
    "FMLEnvironment",
    "GameTest",
    "GameTestHelper",
    "GameTestHolder",
    "Map",
    "ResourceLocation",
})
RESOURCE_RENDERER_TYPES = frozenset({
    "BlockPos",
    "BuiltInRegistries",
    "Direction",
    "HolderLookup",
    "Item",
    "ItemStack",
    "Items",
    "Level",
    "Objects",
    "Optional",
    "ResourceLocation",
    "StorageResourceBlockStrategy",
    "StorageResourceContainerStrategy",
    "StorageResourceHandler",
    "StorageResourceKey",
    "StorageResourceKind",
    "TerminalResourceRendererApi",
})
RESOURCE_BRIDGE_RENDERER_TYPES = RESOURCE_RENDERER_TYPES
TRANSLATION_KEY = re.compile(r"^[a-z0-9_.-]+$")


def canonical_json(value) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def _java_string(value: str) -> str:
    if not isinstance(value, str):
        raise ValueError("Java string value must be a string")
    return json.dumps(value, ensure_ascii=True)


def _recipe_inventory_sha256(class_names) -> str:
    return hashlib.sha256(
        canonical_json(sorted(class_names)).encode()
    ).hexdigest()


def _structural_candidate_inventory_sha256(
    artifact: dict,
    ancestry_classpath: list[dict],
    class_names,
) -> str:
    return hashlib.sha256(
        canonical_json({
            "artifact": artifact,
            "ancestry_classpath": ancestry_classpath,
            "classes": sorted(class_names),
        }).encode()
    ).hexdigest()


def _target_class_inventory_sha256(records: list[dict]) -> str:
    return hashlib.sha256(
        canonical_json([
            {
                "class": record["class"],
                "metadata": record["metadata"],
            }
            for record in records
        ]).encode()
    ).hexdigest()


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _require_unchanged_artifact(
    path: Path,
    expected_sha256: str,
    expected_size: int,
    label: str = "target jar",
):
    try:
        if (
            not path.is_file()
            or path.stat().st_size != expected_size
            or _sha256_file(path) != expected_sha256
        ):
            raise ValueError(f"{label} changed during scan: {path}")
    except OSError as error:
        raise ValueError(f"{label} changed during scan: {path}") from error


def _validate_archive(path: Path, archive: zipfile.ZipFile):
    if not path.is_file():
        raise ValueError(f"target jar does not exist: {path}")
    if path.stat().st_size > MAX_JAR_BYTES:
        raise ValueError(f"target jar exceeds {MAX_JAR_BYTES} bytes: {path}")
    entries = archive.infolist()
    if len(entries) > MAX_ARCHIVE_ENTRIES:
        raise ValueError(
            f"target jar exceeds {MAX_ARCHIVE_ENTRIES} archive entries: {path}"
        )
    total = sum(entry.file_size for entry in entries)
    if total > MAX_UNCOMPRESSED_BYTES:
        raise ValueError(
            f"target jar exceeds {MAX_UNCOMPRESSED_BYTES} uncompressed bytes: {path}"
        )


def normalize_jar(source, output) -> Path:
    unresolved_source = Path(source)
    if unresolved_source.is_symlink():
        raise ValueError(f"source jar is a symlink: {source}")
    source_path = unresolved_source.resolve()
    if not source_path.is_file():
        raise ValueError(f"source jar does not exist: {source}")
    source_size = source_path.stat().st_size
    source_sha256 = _sha256_file(source_path)
    output_path = Path(output).absolute()
    if output_path.is_symlink():
        raise ValueError(f"normalized jar output is a symlink: {output}")
    if output_path.resolve() == source_path:
        raise ValueError("normalized jar output must differ from source")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary = output_path.with_name(
        f".{output_path.name}.tmp-{os.getpid()}"
    )
    temporary.unlink(missing_ok=True)
    try:
        with zipfile.ZipFile(source_path) as source_archive:
            _validate_archive(source_path, source_archive)
            entries = source_archive.infolist()
            names = [entry.filename for entry in entries]
            if len(set(names)) != len(names):
                raise ValueError("source jar repeats an archive entry")
            for name in names:
                logical_name = name[:-1] if name.endswith("/") else name
                if (
                    not logical_name
                    or logical_name.startswith("/")
                    or "\\" in logical_name
                    or any(
                        part in ("", ".", "..")
                        for part in logical_name.split("/")
                    )
                ):
                    raise ValueError(
                        "source jar has a non-canonical entry path"
                    )
            with zipfile.ZipFile(
                temporary,
                "w",
                compression=zipfile.ZIP_STORED,
                allowZip64=True,
            ) as normalized_archive:
                for entry in sorted(entries, key=lambda item: item.filename):
                    info = zipfile.ZipInfo(
                        entry.filename,
                        (1980, 1, 1, 0, 0, 0),
                    )
                    info.compress_type = zipfile.ZIP_STORED
                    info.create_system = 3
                    info.external_attr = (
                        (0o40755 if entry.is_dir() else 0o100644) << 16
                    )
                    if entry.is_dir():
                        normalized_archive.writestr(info, b"")
                        continue
                    with source_archive.open(entry) as input_stream:
                        with normalized_archive.open(
                            info,
                            "w",
                            force_zip64=True,
                        ) as output_stream:
                            shutil.copyfileobj(
                                input_stream,
                                output_stream,
                                length=64 * 1024,
                            )
        _require_unchanged_artifact(
            source_path,
            source_sha256,
            source_size,
            "source jar",
        )
        os.replace(temporary, output_path)
    finally:
        temporary.unlink(missing_ok=True)
    return output_path


def _validate_exact_zip_entry_path(entry: str, location: str):
    if not isinstance(entry, str) or not entry:
        raise ValueError(f"{location} must be a non-empty exact ZIP entry path")
    if (
        entry.startswith("/")
        or entry.endswith("/")
        or "\\" in entry
        or re.search(r"[\x00-\x1f\x7f]", entry) is not None
        or any(part in ("", ".", "..") for part in entry.split("/"))
        or any(character in entry for character in "*?[]")
    ):
        raise ValueError(f"{location} must be a safe exact ZIP entry path")


def transform_runtime_artifact(
    source,
    output,
    *,
    expected_sha256: str,
    remove_entries,
) -> Path:
    if not isinstance(expected_sha256, str) or not re.fullmatch(
        r"[0-9a-f]{64}", expected_sha256
    ):
        raise ValueError("runtime artifact expected SHA must be a SHA-256 digest")
    if not isinstance(remove_entries, list) or not remove_entries:
        raise ValueError("runtime artifact remove entries must be a non-empty list")
    if len(set(remove_entries)) != len(remove_entries):
        raise ValueError("runtime artifact remove entries must be unique")
    for index, entry in enumerate(remove_entries):
        _validate_exact_zip_entry_path(
            entry,
            f"runtime artifact remove entry {index}",
        )

    unresolved_source = Path(source)
    if unresolved_source.is_symlink():
        raise ValueError(f"runtime artifact source is a symlink: {source}")
    source_path = unresolved_source.resolve()
    if not source_path.is_file():
        raise ValueError(f"runtime artifact source does not exist: {source}")
    source_size = source_path.stat().st_size
    source_sha256 = _sha256_file(source_path)
    if source_sha256 != expected_sha256:
        raise ValueError(
            "runtime artifact SHA-256 mismatch: expected "
            f"{expected_sha256}, got {source_sha256}"
        )

    output_path = Path(output).absolute()
    if output_path.is_symlink():
        raise ValueError(f"runtime artifact output is a symlink: {output}")
    if output_path.resolve() == source_path:
        raise ValueError("runtime artifact output must differ from source")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary = output_path.with_name(f".{output_path.name}.tmp-{os.getpid()}")
    temporary.unlink(missing_ok=True)
    try:
        with zipfile.ZipFile(source_path) as source_archive:
            _validate_archive(source_path, source_archive)
            entries = source_archive.infolist()
            names = [entry.filename for entry in entries]
            if len(set(names)) != len(names):
                raise ValueError("runtime artifact repeats a ZIP entry")
            for name in names:
                logical_name = name[:-1] if name.endswith("/") else name
                if (
                    not logical_name
                    or logical_name.startswith("/")
                    or "\\" in logical_name
                    or any(
                        part in ("", ".", "..")
                        for part in logical_name.split("/")
                    )
                ):
                    raise ValueError(
                        "runtime artifact has a non-canonical ZIP entry path"
                    )
            missing = sorted(set(remove_entries) - set(names))
            if missing:
                raise ValueError(
                    "runtime artifact is missing exact ZIP entries: "
                    + ", ".join(missing)
                )
            removed = set(remove_entries)
            with zipfile.ZipFile(
                temporary,
                "w",
                compression=zipfile.ZIP_STORED,
                allowZip64=True,
            ) as transformed_archive:
                for entry in sorted(entries, key=lambda item: item.filename):
                    if entry.filename in removed:
                        continue
                    info = zipfile.ZipInfo(
                        entry.filename,
                        (1980, 1, 1, 0, 0, 0),
                    )
                    info.compress_type = zipfile.ZIP_STORED
                    info.create_system = 3
                    info.external_attr = (
                        (0o40755 if entry.is_dir() else 0o100644) << 16
                    )
                    if entry.is_dir():
                        transformed_archive.writestr(info, b"")
                        continue
                    with source_archive.open(entry) as input_stream:
                        with transformed_archive.open(
                            info,
                            "w",
                            force_zip64=True,
                        ) as output_stream:
                            shutil.copyfileobj(
                                input_stream,
                                output_stream,
                                length=64 * 1024,
                            )
        _require_unchanged_artifact(
            source_path,
            source_sha256,
            source_size,
            "runtime artifact",
        )
        os.replace(temporary, output_path)
    finally:
        temporary.unlink(missing_ok=True)
    return output_path


def _read_mod_metadata(
    archive: zipfile.ZipFile,
    *,
    selected_mod_id: str | None = None,
) -> dict:
    metadata_path = next(
        (candidate for candidate in MOD_METADATA_PATHS if candidate in archive.namelist()),
        None,
    )
    if metadata_path is None:
        raise ValueError("target jar has no NeoForge mod metadata")
    metadata_entry = archive.getinfo(metadata_path)
    if metadata_entry.file_size > MAX_MOD_METADATA_BYTES:
        raise ValueError(
            f"mod metadata exceeds {MAX_MOD_METADATA_BYTES} bytes: {metadata_path}"
        )
    try:
        metadata = tomllib.loads(archive.read(metadata_path).decode("utf-8"))
    except (UnicodeDecodeError, tomllib.TOMLDecodeError) as error:
        raise ValueError(f"invalid NeoForge mod metadata: {error}") from error
    mods = metadata.get("mods")
    if not isinstance(mods, list) or not mods:
        raise ValueError("compat-kit requires NeoForge mod metadata with mods")
    required = ("modId", "version", "displayName")
    for mod in mods:
        if not isinstance(mod, dict):
            raise ValueError("NeoForge mod metadata mods entries must be tables")
        missing = [key for key in required if not isinstance(mod.get(key), str)]
        if missing:
            raise ValueError(
                "NeoForge mod metadata is missing string fields: " + ", ".join(missing)
            )
    if selected_mod_id is None:
        if len(mods) != 1:
            raise ValueError(
                "compat-kit requires exactly one mod in NeoForge mod metadata; "
                "use --mod-id for multi-mod jars"
            )
        mod = mods[0]
    else:
        selected = [
            mod
            for mod in mods
            if isinstance(mod, dict) and mod.get("modId") == selected_mod_id
        ]
        if len(selected) != 1:
            raise ValueError(
                "NeoForge mod metadata does not contain exactly one selected "
                f"mod ID: {selected_mod_id}"
            )
        mod = selected[0]
    return {
        "mod_id": mod["modId"],
        "display_name": mod["displayName"],
        "version": mod["version"],
    }


def _class_name(entry_name: str) -> str:
    return entry_name[:-6].replace("/", ".")


def _decode_modified_utf8(payload: bytes, entry_name: str) -> str:
    units = []
    offset = 0
    while offset < len(payload):
        first = payload[offset]
        offset += 1
        if 0x01 <= first <= 0x7F:
            units.append(first)
            continue
        if 0xC0 <= first <= 0xDF:
            if offset >= len(payload) or payload[offset] & 0xC0 != 0x80:
                raise ValueError(f"invalid class modified UTF-8 value: {entry_name}")
            value = ((first & 0x1F) << 6) | (payload[offset] & 0x3F)
            offset += 1
            if value < 0x80 and value != 0:
                raise ValueError(f"invalid class modified UTF-8 value: {entry_name}")
            units.append(value)
            continue
        if 0xE0 <= first <= 0xEF:
            if (
                offset + 1 >= len(payload)
                or payload[offset] & 0xC0 != 0x80
                or payload[offset + 1] & 0xC0 != 0x80
            ):
                raise ValueError(f"invalid class modified UTF-8 value: {entry_name}")
            value = (
                ((first & 0x0F) << 12)
                | ((payload[offset] & 0x3F) << 6)
                | (payload[offset + 1] & 0x3F)
            )
            offset += 2
            if value < 0x800:
                raise ValueError(f"invalid class modified UTF-8 value: {entry_name}")
            units.append(value)
            continue
        raise ValueError(f"invalid class modified UTF-8 value: {entry_name}")
    characters = []
    index = 0
    while index < len(units):
        unit = units[index]
        if 0xD800 <= unit <= 0xDBFF and index + 1 < len(units):
            following = units[index + 1]
            if 0xDC00 <= following <= 0xDFFF:
                characters.append(chr(
                    0x10000 + ((unit - 0xD800) << 10) + following - 0xDC00
                ))
                index += 2
                continue
        characters.append(chr(unit))
        index += 1
    return "".join(characters)


def _class_metadata(payload: bytes, entry_name: str) -> dict | None:
    if not payload.startswith(b"\xca\xfe\xba\xbe"):
        return None
    if len(payload) < 10:
        raise ValueError(f"truncated class header: {entry_name}")
    constant_pool_count = int.from_bytes(payload[8:10], "big")
    utf8_entries: dict[int, bytes] = {}
    class_entries: dict[int, int] = {}
    offset = 10
    index = 1
    while index < constant_pool_count:
        if offset >= len(payload):
            raise ValueError(f"truncated class constant pool: {entry_name}")
        tag = payload[offset]
        offset += 1
        if tag == 1:
            if offset + 2 > len(payload):
                raise ValueError(f"truncated class UTF-8 length: {entry_name}")
            length = int.from_bytes(payload[offset:offset + 2], "big")
            offset += 2
            if offset + length > len(payload):
                raise ValueError(f"truncated class UTF-8 value: {entry_name}")
            utf8_entries[index] = payload[offset:offset + length]
            offset += length
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            offset += 4
        elif tag in (5, 6):
            offset += 8
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            if offset + 2 > len(payload):
                raise ValueError(f"truncated class constant pool: {entry_name}")
            if tag == 7:
                class_entries[index] = int.from_bytes(payload[offset:offset + 2], "big")
            offset += 2
        elif tag == 15:
            offset += 3
        else:
            raise ValueError(
                f"unsupported class constant-pool tag {tag}: {entry_name}"
            )
        if offset > len(payload):
            raise ValueError(f"truncated class constant pool: {entry_name}")
        index += 1
    if offset + 2 > len(payload):
        raise ValueError(f"truncated class declaration: {entry_name}")
    access_flags = int.from_bytes(payload[offset:offset + 2], "big")
    if offset + 8 > len(payload) and access_flags & 0x1000:
        return {
            "access_flags": access_flags,
            "super_class": None,
            "interfaces": [],
            "inner_class_entry": False,
            "inner_name": None,
            "enclosing_method": False,
            "source_file": None,
        }
    if offset + 8 > len(payload):
        raise ValueError(f"truncated class declaration: {entry_name}")
    this_class = int.from_bytes(payload[offset + 2:offset + 4], "big")
    super_class = int.from_bytes(payload[offset + 4:offset + 6], "big")
    interface_count = int.from_bytes(payload[offset + 6:offset + 8], "big")
    offset += 8
    if offset + interface_count * 2 > len(payload):
        raise ValueError(f"truncated class interfaces: {entry_name}")

    def resolve_class(class_index: int) -> str | None:
        if class_index == 0:
            return None
        name_index = class_entries.get(class_index)
        encoded_name = utf8_entries.get(name_index) if name_index is not None else None
        if not encoded_name:
            raise ValueError(f"invalid class name reference: {entry_name}")
        name = _decode_modified_utf8(encoded_name, entry_name)
        return name.replace("/", ".")

    def resolve_utf8(utf8_index: int) -> str:
        encoded = utf8_entries.get(utf8_index)
        if encoded is None:
            raise ValueError(f"invalid class UTF-8 reference: {entry_name}")
        return _decode_modified_utf8(encoded, entry_name)

    interfaces = []
    for interface_index in range(interface_count):
        class_index = int.from_bytes(
            payload[offset + interface_index * 2:offset + interface_index * 2 + 2],
            "big",
        )
        interfaces.append(resolve_class(class_index))
    offset += interface_count * 2
    declared_name = resolve_class(this_class)
    expected_name = _class_name(entry_name)
    if declared_name != expected_name:
        raise ValueError(
            f"class declaration name mismatch: {entry_name} declares {declared_name}"
        )
    def skip_members(member_offset: int, kind: str) -> int:
        if member_offset + 2 > len(payload):
            raise ValueError(f"truncated class {kind}: {entry_name}")
        count = int.from_bytes(payload[member_offset:member_offset + 2], "big")
        member_offset += 2
        for _ in range(count):
            if member_offset + 8 > len(payload):
                raise ValueError(f"truncated class {kind}: {entry_name}")
            attribute_count = int.from_bytes(
                payload[member_offset + 6:member_offset + 8],
                "big",
            )
            member_offset += 8
            for _ in range(attribute_count):
                if member_offset + 6 > len(payload):
                    raise ValueError(
                        f"truncated class {kind} attribute: {entry_name}"
                    )
                length = int.from_bytes(
                    payload[member_offset + 2:member_offset + 6],
                    "big",
                )
                member_offset += 6
                if member_offset + length > len(payload):
                    raise ValueError(
                        f"truncated class {kind} attribute: {entry_name}"
                    )
                member_offset += length
        return member_offset

    offset = skip_members(offset, "fields")
    offset = skip_members(offset, "methods")
    if offset + 2 > len(payload):
        raise ValueError(f"truncated class attributes: {entry_name}")
    attribute_count = int.from_bytes(payload[offset:offset + 2], "big")
    offset += 2
    inner_class_entry = False
    inner_name = None
    outer_class = None
    enclosing_method = False
    source_file = None
    for _ in range(attribute_count):
        if offset + 6 > len(payload):
            raise ValueError(f"truncated class attribute: {entry_name}")
        name_index = int.from_bytes(payload[offset:offset + 2], "big")
        length = int.from_bytes(payload[offset + 2:offset + 6], "big")
        offset += 6
        if offset + length > len(payload):
            raise ValueError(f"truncated class attribute: {entry_name}")
        attribute_name = resolve_utf8(name_index)
        attribute = payload[offset:offset + length]
        offset += length
        if attribute_name == "EnclosingMethod":
            if length != 4:
                raise ValueError(
                    f"invalid EnclosingMethod attribute: {entry_name}"
                )
            enclosing_method = True
        elif attribute_name == "SourceFile":
            if length != 2:
                raise ValueError(f"invalid SourceFile attribute: {entry_name}")
            source_file = resolve_utf8(int.from_bytes(attribute, "big"))
            if (
                not source_file.endswith(".java")
                or "/" in source_file
                or "\\" in source_file
            ):
                raise ValueError(f"invalid SourceFile attribute: {entry_name}")
        elif attribute_name == "InnerClasses":
            if length < 2:
                raise ValueError(f"invalid InnerClasses attribute: {entry_name}")
            classes_count = int.from_bytes(attribute[:2], "big")
            if length != 2 + classes_count * 8:
                raise ValueError(f"invalid InnerClasses attribute: {entry_name}")
            for index in range(classes_count):
                entry_offset = 2 + index * 8
                inner_class_index = int.from_bytes(
                    attribute[entry_offset:entry_offset + 2],
                    "big",
                )
                if inner_class_index != this_class:
                    continue
                inner_class_entry = True
                outer_class = resolve_class(int.from_bytes(
                    attribute[entry_offset + 2:entry_offset + 4],
                    "big",
                ))
                inner_name_index = int.from_bytes(
                    attribute[entry_offset + 4:entry_offset + 6],
                    "big",
                )
                inner_name = (
                    None
                    if inner_name_index == 0
                    else resolve_utf8(inner_name_index)
                )
    if offset != len(payload):
        raise ValueError(f"trailing class data: {entry_name}")
    return {
        "access_flags": access_flags,
        "super_class": resolve_class(super_class),
        "interfaces": interfaces,
        "inner_class_entry": inner_class_entry,
        "inner_name": inner_name,
        "outer_class": outer_class,
        "enclosing_method": enclosing_method,
        "source_file": source_file,
    }


def _class_access_flags(payload: bytes, entry_name: str) -> int:
    metadata = _class_metadata(payload, entry_name)
    return 0 if metadata is None else metadata["access_flags"]


def _is_inspectable_class(
    archive: zipfile.ZipFile,
    entry_name: str,
    *,
    inspectable_cache: dict[str, bool] | None = None,
) -> bool:
    cache = inspectable_cache if inspectable_cache is not None else {}
    if entry_name in cache:
        return cache[entry_name]
    pending = []
    pending_set = set()
    current_entry = entry_name
    while current_entry not in cache:
        if current_entry in pending_set:
            raise ValueError(
                "nested class ownership contains a cycle: " + entry_name
            )
        if (
            current_entry.startswith("META-INF/versions/")
            or not current_entry.endswith(".class")
            or current_entry.endswith("module-info.class")
        ):
            cache[current_entry] = False
            break
        entry = archive.getinfo(current_entry)
        if entry.file_size > MAX_CLASS_BYTES:
            raise ValueError(
                f"class entry exceeds {MAX_CLASS_BYTES} bytes: {current_entry}"
            )
        metadata = _class_metadata(archive.read(current_entry), current_entry)
        if metadata is None:
            nested_segments = _class_name(current_entry).split("$")[1:]
            cache[current_entry] = all(
                re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", segment)
                for segment in nested_segments
            )
            break
        if (
            metadata["access_flags"] & 0x1000
            or metadata["enclosing_method"]
            or (
                metadata["inner_class_entry"]
                and metadata["inner_name"] is None
            )
        ):
            cache[current_entry] = False
            break
        outer_class = metadata.get("outer_class")
        if not metadata["inner_class_entry"] or outer_class is None:
            cache[current_entry] = True
            break
        outer_entry = outer_class.replace(".", "/") + ".class"
        try:
            archive.getinfo(outer_entry)
        except KeyError as error:
            raise ValueError(
                "nested class owner is missing from its archive: "
                f"{outer_entry} for {current_entry}"
            ) from error
        if len(pending) >= MAX_NESTED_CLASS_OWNER_DEPTH:
            raise ValueError(
                "nested class ownership exceeds "
                f"{MAX_NESTED_CLASS_OWNER_DEPTH} levels: {entry_name}"
            )
        pending.append(current_entry)
        pending_set.add(current_entry)
        current_entry = outer_entry
    inspectable = cache[current_entry]
    for nested_entry in reversed(pending):
        cache[nested_entry] = inspectable
    return cache[entry_name]


def _classpath_metadata(paths) -> tuple[
    dict[str, dict],
    list[dict],
    dict[str, Path],
    list[tuple[Path, str, int]],
]:
    raw_paths = list(paths or ())
    if len(raw_paths) > MAX_CLASSPATH_JARS:
        raise ValueError(
            f"ancestry classpath exceeds {MAX_CLASSPATH_JARS} jars"
        )
    metadata_by_class = {}
    class_locations = {}
    records = []
    artifact_checks = []
    class_count = 0
    seen_artifacts = set()
    for raw_path in raw_paths:
        unresolved = Path(raw_path)
        if unresolved.is_symlink():
            raise ValueError(f"ancestry classpath jar is a symlink: {raw_path}")
        path = unresolved.resolve()
        if not path.is_file():
            raise ValueError(f"ancestry classpath jar does not exist: {raw_path}")
        size = path.stat().st_size
        digest = _sha256_file(path)
        identity = (digest, size)
        if identity in seen_artifacts:
            raise ValueError("ancestry classpath repeats an artifact")
        seen_artifacts.add(identity)
        with zipfile.ZipFile(path) as archive:
            _validate_archive(path, archive)
            inspectable_cache = {}
            for entry_name in sorted(archive.namelist()):
                if not _is_inspectable_class(
                    archive,
                    entry_name,
                    inspectable_cache=inspectable_cache,
                ):
                    continue
                class_count += 1
                if class_count > MAX_CLASSPATH_CLASSES:
                    raise ValueError(
                        "ancestry classpath exceeds "
                        f"{MAX_CLASSPATH_CLASSES} classes"
                    )
                class_name = _class_name(entry_name)
                metadata = _class_metadata(
                    archive.read(entry_name),
                    entry_name,
                )
                if class_name in metadata_by_class:
                    raise ValueError(
                        "ancestry classpath repeats class " + class_name
                    )
                metadata_by_class[class_name] = metadata
                class_locations[class_name] = path
        _require_unchanged_artifact(path, digest, size, "classpath jar")
        records.append({"sha256": digest, "size": size})
        artifact_checks.append((path, digest, size))
    return (
        metadata_by_class,
        sorted(
            records,
            key=lambda record: (record["sha256"], record["size"]),
        ),
        class_locations,
        artifact_checks,
    )


def _candidate_bucket(class_name: str) -> str | None:
    lowered = class_name.lower()
    if any(term in lowered for term in RECIPE_TERMS):
        return "recipe_classes"
    if any(term in lowered for term in RESOURCE_TERMS):
        return "resource_apis"
    if any(term in lowered for term in STATION_TERMS):
        return "station_classes"
    return None


RECIPE_INTERFACE = "net.minecraft.world.item.crafting.Recipe"
RECIPE_TYPE_INTERFACE = "net.minecraft.world.item.crafting.RecipeType"
RECIPE_SERIALIZER_INTERFACE = "net.minecraft.world.item.crafting.RecipeSerializer"
BLOCK_ENTITY_CLASS = "net.minecraft.world.level.block.entity.BlockEntity"
KNOWN_ANCESTRY_ROOTS = frozenset({
    RECIPE_INTERFACE,
    RECIPE_TYPE_INTERFACE,
    RECIPE_SERIALIZER_INTERFACE,
    BLOCK_ENTITY_CLASS,
})
_JDK_MODULE_CLASSES = None
_JDK_MODULE_KEY = None


def _jdk_21_toolchain() -> tuple[Path, Path, Path, str, dict]:
    javap = shutil.which("javap")
    if javap is None:
        raise RuntimeError(
            "javap was not found; set JAVA_HOME to JDK 21 before scanning"
        )
    javap = Path(javap).resolve()
    jdk = javap.parent.parent
    release = jdk / "release"
    try:
        if not release.is_file() or release.stat().st_size > 64 * 1024:
            raise RuntimeError(
                "JDK module inventory requires JDK 21; resolved toolchain "
                "has no bounded release metadata"
            )
        release_text = release.read_text()
    except (OSError, UnicodeError) as error:
        raise RuntimeError(
            "JDK module inventory requires JDK 21; failed to read release metadata"
        ) from error
    version_match = re.search(
        r'^JAVA_VERSION=(?:"([^"]+)"|([^\s]+))$',
        release_text,
        re.MULTILINE,
    )
    version = (
        next(
            (group for group in version_match.groups() if group is not None),
            None,
        )
        if version_match is not None
        else None
    )
    try:
        major = int(version.split(".", 1)[0])
    except (AttributeError, ValueError) as error:
        raise RuntimeError(
            "JDK module inventory requires JDK 21; release metadata has no "
            "valid JAVA_VERSION"
        ) from error
    if major != 21:
        raise RuntimeError(
            "JDK module inventory requires JDK 21; resolved "
            f"JAVA_VERSION={version}"
        )
    try:
        javap_version = subprocess.run(
            [str(javap), "-version"],
            capture_output=True,
            check=False,
            text=True,
            timeout=5,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise RuntimeError(
            "javap executable could not be verified as JDK 21"
        ) from error
    javap_output = (
        javap_version.stdout.strip() or javap_version.stderr.strip()
    )
    javap_match = re.fullmatch(
        r"(?:javap\s+)?([0-9]+)(?:\.[0-9]+)*(?:[-+][^\s]+)?",
        javap_output,
    )
    if (
        javap_version.returncode != 0
        or javap_match is None
        or int(javap_match.group(1)) != 21
    ):
        raise RuntimeError(
            "javap executable must report JDK 21; reported "
            + (javap_output or f"exit {javap_version.returncode}")
        )
    jmods = jdk / "jmods"
    if not jmods.is_dir():
        raise RuntimeError(
            "JDK module metadata was not found; set JAVA_HOME to JDK 21 before scanning"
        )
    try:
        modules = [
            {
                "name": module.name,
                "size": module.stat().st_size,
                "mtime_ns": module.stat().st_mtime_ns,
            }
            for module in sorted(jmods.glob("*.jmod"))
            if module.is_file()
        ]
    except OSError as error:
        raise RuntimeError(
            f"failed to inspect JDK module metadata: {error}"
        ) from error
    if not modules:
        raise RuntimeError("JDK module metadata contains no modules")
    identity = {
        "home": str(jdk),
        "java_version": version,
        "javap": {
            "path": str(javap),
            "version": javap_output,
            "size": javap.stat().st_size,
            "sha256": _sha256_file(javap),
        },
        "modules": modules,
    }
    return javap, jdk, jmods, version, identity


def _jdk_module_classes() -> frozenset[str]:
    global _JDK_MODULE_CLASSES, _JDK_MODULE_KEY
    _, _, jmods, _, identity = _jdk_21_toolchain()
    key = hashlib.sha256(canonical_json(identity).encode("utf-8")).hexdigest()
    if _JDK_MODULE_CLASSES is not None and _JDK_MODULE_KEY == key:
        return _JDK_MODULE_CLASSES
    classes = set()
    try:
        for jmod in sorted(jmods.glob("*.jmod")):
            with zipfile.ZipFile(jmod) as archive:
                for entry_name in archive.namelist():
                    if (
                        entry_name.startswith("classes/")
                        and entry_name.endswith(".class")
                        and not entry_name.endswith("module-info.class")
                    ):
                        classes.add(
                            _class_name(entry_name.removeprefix("classes/"))
                        )
    except (OSError, zipfile.BadZipFile) as error:
        raise RuntimeError(
            f"failed to read JDK module metadata: {error}"
        ) from error
    if not classes:
        raise RuntimeError("JDK module metadata contains no classes")
    _JDK_MODULE_CLASSES = frozenset(classes)
    _JDK_MODULE_KEY = key
    return _JDK_MODULE_CLASSES


def _current_name_bucket(class_name: str) -> tuple[str, str] | None:
    lowered = class_name.lower()
    groups = (
        ("client_viewer_classes", CLIENT_VIEWER_TERMS),
        ("recipe_builders", RECIPE_BUILDER_TERMS),
        ("datagen_classes", DATAGEN_TERMS),
        ("resource_apis", RESOURCE_TERMS),
        ("station_classes", STATION_TERMS),
    )
    for bucket, terms in groups:
        term = next((term for term in terms if term in lowered), None)
        if term is not None:
            return bucket, term
    return None


def _inheritance_path(
    class_name: str,
    target: str,
    metadata_by_class: dict[str, dict | None],
) -> list[str] | None:
    def visit(current: str, trail: tuple[str, ...]) -> list[str] | None:
        if current == target:
            return [*trail, current]
        if current in trail:
            return None
        metadata = metadata_by_class.get(current)
        if metadata is None:
            return None
        parents = [
            *metadata["interfaces"],
            *([metadata["super_class"]] if metadata["super_class"] else []),
        ]
        for parent in parents:
            if parent == target:
                return [*trail, current, parent]
            result = visit(parent, (*trail, current))
            if result is not None:
                return result
        return None

    return visit(class_name, ())


def _require_resolved_ancestry(
    target_classes: list[str],
    metadata_by_class: dict[str, dict | None],
):
    resolved = set()
    visiting = set()

    def visit(class_name: str, owner: str):
        if (
            class_name in resolved
            or class_name in KNOWN_ANCESTRY_ROOTS
        ):
            return
        if class_name in visiting:
            return
        metadata = metadata_by_class.get(class_name)
        if metadata is None:
            if class_name in _jdk_module_classes():
                resolved.add(class_name)
                return
            raise ValueError(
                "unresolved ancestry; supply --classpath for "
                f"{owner} -> {class_name}"
            )
        visiting.add(class_name)
        parents = [
            *metadata["interfaces"],
            *([metadata["super_class"]] if metadata["super_class"] else []),
        ]
        for parent in parents:
            visit(parent, owner)
        visiting.remove(class_name)
        resolved.add(class_name)

    for class_name in target_classes:
        if metadata_by_class.get(class_name) is not None:
            visit(class_name, class_name)


def _implementation_ancestry(
    class_name: str,
    metadata_by_class: dict[str, dict | None],
) -> list[str]:
    implementations = []
    visited = set()

    def visit(current: str, *, root: bool = False):
        if current in visited or current in KNOWN_ANCESTRY_ROOTS:
            return
        visited.add(current)
        metadata = metadata_by_class.get(current)
        if metadata is None:
            if root:
                implementations.append(current)
            return
        implementations.append(current)
        parents = [
            *metadata["interfaces"],
            *([metadata["super_class"]] if metadata["super_class"] else []),
        ]
        for parent in parents:
            visit(parent)

    visit(class_name, root=True)
    return implementations


def _classify_candidate(
    class_name: str,
    metadata_by_class: dict[str, dict | None],
) -> tuple[str, dict] | None:
    metadata = metadata_by_class.get(class_name)
    if metadata is not None:
        access_flags = metadata["access_flags"]
        is_interface = access_flags & 0x0200 != 0
        concrete = access_flags & (0x0200 | 0x0400) == 0
        if concrete or is_interface:
            recipe_path = _inheritance_path(
                class_name,
                RECIPE_INTERFACE,
                metadata_by_class,
            )
            if (
                recipe_path is not None
                and class_name != RECIPE_INTERFACE
            ):
                return "recipe_classes", {
                    "method": "class_hierarchy",
                    "evidence": recipe_path,
                }
        if concrete:
            serializer_path = _inheritance_path(
                class_name,
                RECIPE_SERIALIZER_INTERFACE,
                metadata_by_class,
            )
            if serializer_path is not None:
                return "recipe_serializers", {
                    "method": "class_hierarchy",
                    "evidence": serializer_path,
                }
            recipe_type_path = _inheritance_path(
                class_name,
                RECIPE_TYPE_INTERFACE,
                metadata_by_class,
            )
            if recipe_type_path is not None:
                return "recipe_types", {
                    "method": "class_hierarchy",
                    "evidence": recipe_type_path,
                }
        if not is_interface:
            block_entity_path = _inheritance_path(
                class_name,
                BLOCK_ENTITY_CLASS,
                metadata_by_class,
            )
            if block_entity_path is not None:
                return "block_entity_classes", {
                    "method": "class_hierarchy",
                    "evidence": block_entity_path,
                }
        name_classification = _current_name_bucket(class_name)
        if name_classification is not None:
            bucket, term = name_classification
            return bucket, {"method": "name_term", "evidence": [term]}
        return None

    name_classification = _current_name_bucket(class_name)
    if name_classification is not None:
        bucket, term = name_classification
        return bucket, {"method": "name_term", "evidence": [term]}
    bucket = _candidate_bucket(class_name)
    if bucket is None:
        return None
    lowered = class_name.lower()
    terms = {
        "recipe_classes": RECIPE_TERMS,
        "resource_apis": RESOURCE_TERMS,
        "station_classes": STATION_TERMS,
    }[bucket]
    term = next(term for term in terms if term in lowered)
    return bucket, {"method": "name_term", "evidence": [term]}


def _run_javap(
    jar: Path,
    class_name: str | tuple[str, ...],
    *options: str,
    output_limit: int = MAX_SIGNATURE_BYTES,
    output_label: str = "public signature",
    javap: Path | None = None,
) -> str:
    if javap is None:
        javap, _, _, _, _ = _jdk_21_toolchain()
    class_names = (
        (class_name,)
        if isinstance(class_name, str)
        else tuple(class_name)
    )
    class_label = ", ".join(class_names)
    command = [str(javap), *options, "-classpath", str(jar), *class_names]
    try:
        process = subprocess.Popen(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
    except FileNotFoundError as error:
        raise RuntimeError(
            "javap was not found; set JAVA_HOME to JDK 21 before scanning"
        ) from error

    def terminate():
        if process.poll() is not None:
            return
        process.terminate()
        try:
            process.wait(timeout=1)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()

    streams = {
        "stdout": {
            "file": process.stdout,
            "chunks": [],
            "size": 0,
            "limit": output_limit,
            "overflow": False,
            "error": None,
        },
        "stderr": {
            "file": process.stderr,
            "chunks": [],
            "size": 0,
            "limit": MAX_SIGNATURE_BYTES,
            "overflow": False,
            "error": None,
        },
    }
    reader_event = threading.Event()

    def read_stream(name):
        state = streams[name]
        try:
            while True:
                read_size = min(
                    64 * 1024,
                    state["limit"] - state["size"] + 1,
                )
                chunk = state["file"].read1(max(1, read_size))
                if not chunk:
                    return
                state["size"] += len(chunk)
                if state["size"] > state["limit"]:
                    state["overflow"] = True
                    reader_event.set()
                    return
                state["chunks"].append(chunk)
        except (OSError, ValueError) as error:
            state["error"] = error
            reader_event.set()

    readers = [
        threading.Thread(target=read_stream, args=(name,), daemon=True)
        for name in streams
    ]
    for reader in readers:
        reader.start()
    deadline = time.monotonic() + 20
    timed_out = False
    try:
        while any(reader.is_alive() for reader in readers):
            if reader_event.wait(timeout=0.01):
                terminate()
                break
            if time.monotonic() >= deadline:
                timed_out = True
                terminate()
                break
        for reader in readers:
            reader.join(timeout=1)
        if timed_out:
            raise RuntimeError(f"javap timed out for {class_label}")
        for name, state in streams.items():
            if state["error"] is not None:
                raise RuntimeError(
                    f"failed to read javap {name} for {class_label}"
                ) from state["error"]
            if not state["overflow"]:
                continue
            if name == "stdout":
                raise ValueError(
                    f"{output_label} exceeds {output_limit} bytes: "
                    f"{class_label}"
                )
            raise RuntimeError(
                f"javap error output exceeds {MAX_SIGNATURE_BYTES} "
                f"bytes: {class_label}"
            )
        remaining = deadline - time.monotonic()
        try:
            return_code = process.wait(timeout=max(remaining, 0.001))
        except subprocess.TimeoutExpired as error:
            terminate()
            raise RuntimeError(f"javap timed out for {class_label}") from error
    finally:
        for state in streams.values():
            state["file"].close()

    stdout = b"".join(streams["stdout"]["chunks"]).decode(
        "utf-8",
        errors="replace",
    )
    stderr = b"".join(streams["stderr"]["chunks"]).decode(
        "utf-8",
        errors="replace",
    )
    if return_code != 0:
        detail = stderr.strip() or stdout.strip()
        raise RuntimeError(f"javap failed for {class_label}: {detail}")
    return stdout.strip()


def _public_signatures(
    jar: Path,
    class_names: list[str],
    javap: Path,
) -> dict[str, str]:
    signatures = {}
    for offset in range(0, len(class_names), 32):
        batch = class_names[offset:offset + 32]
        output = _run_javap(
            jar,
            tuple(batch),
            "-public",
            output_limit=MAX_SIGNATURE_BYTES * len(batch),
            javap=javap,
        )
        sections = []
        current = []
        for line in output.splitlines():
            current.append(line)
            if line.strip() == "}":
                sections.append("\n".join(current).strip())
                current = []
        if current or len(sections) != len(batch):
            raise RuntimeError(
                "javap returned an unexpected public-signature batch"
            )
        for class_name, signature in zip(batch, sections, strict=True):
            if len(signature.encode("utf-8")) > MAX_SIGNATURE_BYTES:
                raise ValueError(
                    f"public signature exceeds {MAX_SIGNATURE_BYTES} bytes: "
                    + class_name
                )
            signatures[class_name] = signature
    return signatures


def _candidate_source_suffix(
    class_name: str,
    metadata: dict | None,
) -> str | None:
    if metadata is not None:
        source_file = metadata.get("source_file")
        if source_file is None:
            return None
        package, _, _ = class_name.rpartition(".")
        return (
            f"{package.replace('.', '/')}/{source_file}"
            if package
            else source_file
        )
    return class_name.split("$", 1)[0].replace(".", "/") + ".java"


def _candidate_source_class(
    class_name: str,
    metadata_by_class: dict[str, dict | None],
    ancestry: tuple[str, ...] = (),
) -> str:
    seen = set(ancestry)
    inner_names = []
    current_class = class_name
    while True:
        if current_class in seen:
            raise ValueError(
                "nested class ownership contains a cycle: " + current_class
            )
        seen.add(current_class)
        metadata = metadata_by_class.get(current_class)
        if metadata is None or not metadata.get("inner_class_entry"):
            return current_class + "".join(
                "." + inner_name
                for inner_name in reversed(inner_names)
            )
        inner_name = metadata.get("inner_name")
        outer_class = metadata.get("outer_class")
        if not inner_name or not outer_class:
            raise ValueError(
                "named nested class has unresolved owner: " + current_class
            )
        if outer_class not in metadata_by_class:
            raise ValueError(
                "named nested class owner is unresolved: " + current_class
            )
        if len(inner_names) >= MAX_NESTED_CLASS_OWNER_DEPTH:
            raise ValueError(
                "nested class ownership exceeds "
                f"{MAX_NESTED_CLASS_OWNER_DEPTH} levels: {class_name}"
            )
        inner_names.append(inner_name)
        current_class = outer_class


def _validate_candidate_source_class(
    class_name: str,
    source_class: object,
    location: str,
):
    # Audited bytecode may already live under contextual-keyword package segments
    # such as `module` (e.g. mods.railcraft.world.module.*).
    if not isinstance(source_class, str) or JAVA_TYPE.fullmatch(source_class) is None:
        raise ValueError(f"{location} has invalid source_class")
    package, separator, binary_simple_name = class_name.rpartition(".")
    if any(
        segment in JAVA_HARD_RESERVED_IDENTIFIERS
        for segment in package.split(".")
    ):
        raise ValueError(f"{location} has invalid source_class")
    expected_prefix = package + "." if separator else ""
    if not source_class.startswith(expected_prefix):
        raise ValueError(f"{location} source_class does not match class")
    source_simple_name = source_class[len(expected_prefix):]
    if any(
        segment in JAVA_RESERVED_IDENTIFIERS
        for segment in source_simple_name.split(".")
    ):
        raise ValueError(f"{location} has invalid source_class")
    if source_simple_name.replace(".", "$") != binary_simple_name:
        raise ValueError(f"{location} source_class does not match class")


def _source_evidence(source: Path | None, candidate_suffixes: set[str]) -> dict:
    if source is None:
        return {"revision": None, "files": []}
    source = source.resolve()
    if not source.is_dir():
        raise ValueError(f"source checkout does not exist: {source}")
    git_root_result = subprocess.run(
        ["git", "-C", str(source), "rev-parse", "--show-toplevel"],
        capture_output=True,
        check=False,
        text=True,
        timeout=10,
    )
    if git_root_result.returncode == 0:
        git_root = Path(git_root_result.stdout.strip()).resolve()
    elif (
        git_root_result.returncode == 128
        and "not a git repository" in git_root_result.stderr
    ):
        raise ValueError("source must be inside a Git worktree")
    else:
        raise RuntimeError(
            "failed to discover source checkout: "
            + (
                git_root_result.stderr.strip()
                or git_root_result.stdout.strip()
            )
        )
    status = subprocess.run(
        [
            "git",
            "-C",
            str(git_root),
            "status",
            "--porcelain",
            "--untracked-files=all",
        ],
        capture_output=True,
        check=False,
        text=True,
        timeout=10,
    )
    if status.returncode != 0:
        raise RuntimeError(
            "failed to inspect source checkout status: "
            + (status.stderr.strip() or status.stdout.strip())
        )
    if status.stdout.strip():
        raise ValueError(
            "source checkout is dirty: " + status.stdout.splitlines()[0]
        )
    try:
        source_relative = source.relative_to(git_root)
    except ValueError as error:
        raise RuntimeError(
            f"source checkout is outside its Git worktree: {source}"
        ) from error
    pathspec = source_relative.as_posix() if source_relative.parts else "."
    tracked = subprocess.run(
        [
            "git",
            "-C",
            str(git_root),
            "ls-files",
            "-z",
            "--cached",
            "--",
            pathspec,
        ],
        capture_output=True,
        check=False,
        text=True,
        timeout=10,
    )
    if tracked.returncode != 0:
        raise RuntimeError(
            "failed to list tracked source files: "
            + (tracked.stderr.strip() or tracked.stdout.strip())
        )
    java_files = [
        git_root / relative
        for relative in tracked.stdout.split("\0")
        if relative.endswith(".java")
    ]
    if len(java_files) > MAX_SOURCE_FILES:
        raise ValueError(
            f"source checkout exceeds {MAX_SOURCE_FILES} Java files: {source}"
        )
    files = []
    for path in java_files:
        relative = path.relative_to(source).as_posix()
        if (
            path.is_file()
            and not path.is_symlink()
            and any(
                relative == suffix or relative.endswith("/" + suffix)
                for suffix in candidate_suffixes
            )
        ):
            files.append(relative)
    result = subprocess.run(
        ["git", "-C", str(git_root), "rev-parse", "HEAD"],
        capture_output=True,
        check=False,
        text=True,
        timeout=10,
    )
    if result.returncode != 0:
        raise RuntimeError(
            "failed to resolve source checkout revision: "
            + (result.stderr.strip() or result.stdout.strip())
        )
    revision = result.stdout.strip()
    if candidate_suffixes and not files:
        raise ValueError("source checkout has no candidate matches")
    return {"revision": revision, "files": files}


RECIPE_PATH = re.compile(
    r"^data/(?P<namespace>[a-z0-9_.-]+)/recipe/"
    r"(?P<path>[a-z0-9_./-]+)\.json$"
)
TAG_PATH = re.compile(
    r"^data/[a-z0-9_.-]+/tags/[a-z0-9_./-]+\.json$"
)
RESOURCE_LOCATION = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")
RESOURCE_PATH = re.compile(r"^[a-z0-9_./-]+$")
GAME_TEST_NAMESPACE = re.compile(r"^[a-z0-9_.-]+$")


def _minecraft_resource_location(value: str) -> str | None:
    """Match Minecraft ResourceLocation.parse / bySeparator default-namespace rules."""
    if RESOURCE_LOCATION.fullmatch(value):
        return value
    if value.startswith(":"):
        path = value[1:]
        if RESOURCE_PATH.fullmatch(path):
            return f"minecraft:{path}"
        return None
    if RESOURCE_PATH.fullmatch(value):
        return f"minecraft:{value}"
    return None


def _recipe_record(source_id: str, relative_path: str, payload: bytes) -> dict:
    match = RECIPE_PATH.fullmatch(relative_path)
    if match is None:
        raise ValueError(f"invalid recipe data path: {relative_path}")
    if len(payload) > MAX_RECIPE_JSON_BYTES:
        raise ValueError(
            f"recipe JSON exceeds {MAX_RECIPE_JSON_BYTES} bytes: {relative_path}"
        )
    try:
        value = json.loads(payload.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError(f"invalid recipe JSON {relative_path}: {error}") from error
    if not isinstance(value, dict):
        raise ValueError(f"recipe JSON must be an object: {relative_path}")
    serializer_id = value.get("type")
    if not isinstance(serializer_id, str):
        raise ValueError(f"recipe JSON has invalid type: {relative_path}")
    serializer_id = _minecraft_resource_location(serializer_id)
    if serializer_id is None:
        raise ValueError(f"recipe JSON has invalid type: {relative_path}")
    recipe_id = f"{match.group('namespace')}:{match.group('path')}"
    conditions = value.get("neoforge:conditions", [])
    if "neoforge:conditions" in value and not isinstance(conditions, list):
        raise ValueError(f"recipe JSON conditions must be a list: {relative_path}")
    condition_types = []
    for index, condition in enumerate(conditions):
        condition_type = condition.get("type") if isinstance(condition, dict) else None
        if not isinstance(condition_type, str):
            raise ValueError(
                f"recipe JSON condition {index} has invalid type: {relative_path}"
            )
        condition_type = _minecraft_resource_location(condition_type)
        if condition_type is None:
            raise ValueError(
                f"recipe JSON condition {index} has invalid type: {relative_path}"
            )
        condition_types.append(condition_type)
    return {
        "recipe_id": recipe_id,
        "source": source_id,
        "serializer_id": serializer_id,
        "payload_sha256": hashlib.sha256(payload).hexdigest(),
        "fields": sorted(value),
        "array_sizes": {
            key: len(field_value)
            for key, field_value in sorted(value.items())
            if isinstance(field_value, list)
        },
        "condition_types": sorted(set(condition_types)),
    }


def _update_data_root_digest(
    digest,
    relative_path: str,
    payload: bytes,
):
    digest.update(relative_path.encode("utf-8"))
    digest.update(b"\0")
    digest.update(hashlib.sha256(payload).digest())


def _bounded_data_root_payload(
    path: Path,
    maximum_bytes: int,
    location: str,
) -> bytes:
    with path.open("rb") as stream:
        payload = stream.read(maximum_bytes + 1)
    if len(payload) > maximum_bytes:
        raise ValueError(f"{location} exceeds {maximum_bytes} bytes")
    return payload


def _validated_pack_metadata(
    root: Path,
    source_id: str,
) -> tuple[str, bytes] | None:
    path = root / "pack.mcmeta"
    if path.is_symlink():
        raise ValueError(f"recipe data {source_id} pack metadata is a symlink")
    if not path.exists():
        return None
    if not path.is_file():
        raise ValueError(f"recipe data {source_id} pack metadata is not a file")
    try:
        payload = _bounded_data_root_payload(
            path,
            MAX_PACK_METADATA_BYTES,
            f"recipe data pack metadata: {source_id}/pack.mcmeta",
        )
        metadata = json.loads(payload)
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ValueError(
            f"invalid recipe data pack metadata: {source_id}/pack.mcmeta"
        ) from error
    if not isinstance(metadata, dict):
        raise ValueError(
            f"recipe data pack metadata must be an object: {source_id}/pack.mcmeta"
        )
    if "filter" in metadata:
        raise ValueError(
            f"recipe data pack filter is unsupported: {source_id}/pack.mcmeta"
        )
    if "overlays" in metadata:
        raise ValueError(
            f"recipe data pack overlays are unsupported: {source_id}/pack.mcmeta"
        )
    return "pack.mcmeta", payload


def _recipe_data_inventory(
    archive: zipfile.ZipFile,
    artifact_sha: str,
    data_roots,
) -> dict:
    layers: list[tuple[str, str, list[tuple[str, bytes]]]] = []
    external_evidence_files = 0
    jar_entries = []
    recipe_entries = sorted(
        (
            entry
            for entry in archive.infolist()
            if RECIPE_PATH.fullmatch(entry.filename) is not None
        ),
        key=lambda entry: entry.filename,
    )
    seen_recipe_entries = set()
    for entry in recipe_entries:
        if entry.filename in seen_recipe_entries:
            raise ValueError(
                f"duplicate recipe ZIP entry: {entry.filename}"
            )
        seen_recipe_entries.add(entry.filename)
    for entry in recipe_entries:
        name = entry.filename
        if entry.file_size > MAX_RECIPE_JSON_BYTES:
            raise ValueError(
                f"recipe JSON exceeds {MAX_RECIPE_JSON_BYTES} bytes: {name}"
            )
        jar_entries.append((name, archive.read(entry)))
    layers.append(("target_jar", artifact_sha, jar_entries))

    for index, raw_root in enumerate(data_roots or (), start=1):
        root = Path(raw_root).resolve()
        source_id = f"data_root_{index}"
        if not root.is_dir():
            raise ValueError(f"recipe data root is not a directory: {raw_root}")
        if Path(raw_root).is_symlink():
            raise ValueError(f"recipe data root is a symlink: data_root_{index}")
        files = []
        tag_files = []
        for path in sorted(root.rglob("*.json")):
            relative = path.relative_to(root)
            relative_path = relative.as_posix()
            is_recipe = RECIPE_PATH.fullmatch(relative_path) is not None
            is_tag = TAG_PATH.fullmatch(relative_path) is not None
            if not is_recipe and not is_tag:
                continue
            if path.is_symlink() or any(parent.is_symlink() for parent in path.parents if parent != root.parent):
                raise ValueError(
                    f"recipe data contains a symlink: data_root_{index}/{relative.as_posix()}"
                )
            if path.is_file():
                (files if is_recipe else tag_files).append(path)
        if len(files) + len(tag_files) > MAX_RECIPE_FILES:
            raise ValueError(
                "recipe data root exceeds "
                f"{MAX_RECIPE_FILES} recipe/tag files: data_root_{index}"
            )
        external_evidence_files += len(files) + len(tag_files)
        if external_evidence_files > MAX_RECIPE_FILES:
            raise ValueError(
                f"recipe data inventory exceeds {MAX_RECIPE_FILES} files"
            )
        entries = []
        root_digest = hashlib.sha256()
        recipe_files = set(files)
        for path in sorted(
            [*files, *tag_files],
            key=lambda candidate: candidate.relative_to(root).as_posix(),
        ):
            relative_path = path.relative_to(root).as_posix()
            payload = _bounded_data_root_payload(
                path,
                MAX_RECIPE_JSON_BYTES,
                (
                    "recipe JSON"
                    if path in recipe_files
                    else "recipe tag JSON"
                )
                + f": data_root_{index}/{relative_path}",
            )
            _update_data_root_digest(
                root_digest,
                relative_path,
                payload,
            )
            if path in recipe_files:
                entries.append((relative_path, payload))
        pack_metadata = _validated_pack_metadata(root, source_id)
        if pack_metadata is not None:
            relative_path, payload = pack_metadata
            _update_data_root_digest(root_digest, relative_path, payload)
        layers.append((source_id, root_digest.hexdigest(), entries))

    if sum(len(entries) for _, _, entries in layers) > MAX_RECIPE_FILES:
        raise ValueError(f"recipe inventory exceeds {MAX_RECIPE_FILES} files")

    selected: dict[str, dict] = {}
    origins: dict[str, list[str]] = {}
    for source_id, _, entries in layers:
        for relative_path, payload in entries:
            record = _recipe_record(source_id, relative_path, payload)
            recipe_id = record["recipe_id"]
            origins.setdefault(recipe_id, []).append(source_id)
            selected[recipe_id] = record

    grouped: dict[str, list[dict]] = {}
    for record in selected.values():
        grouped.setdefault(record["serializer_id"], []).append(record)
    serializers = []
    for serializer_id, records in sorted(grouped.items()):
        ordered = sorted(records, key=lambda record: record["recipe_id"])
        fields = sorted({field for record in ordered for field in record["fields"]})
        max_array_sizes: dict[str, int] = {}
        for record in ordered:
            for field, size in record["array_sizes"].items():
                max_array_sizes[field] = max(max_array_sizes.get(field, 0), size)
        serializers.append({
            "serializer_id": serializer_id,
            "recipe_count": len(ordered),
            "conditional_recipes": sum(bool(record["condition_types"]) for record in ordered),
            "condition_types": sorted({
                condition_type
                for record in ordered
                for condition_type in record["condition_types"]
            }),
            "sample_recipe_ids": [
                record["recipe_id"]
                for record in ordered[:MAX_RECIPE_SAMPLES_PER_SERIALIZER]
            ],
            "fields": fields,
            "max_array_sizes": dict(sorted(max_array_sizes.items())),
        })
    sources = [
        {"id": source_id, "sha256": digest, "declared_recipes": len(entries)}
        for source_id, digest, entries in layers
    ]
    digest_payload = {
        "sources": sources,
        "effective": [
            {
                "recipe_id": recipe_id,
                "source": record["source"],
                "payload_sha256": record["payload_sha256"],
            }
            for recipe_id, record in sorted(selected.items())
        ],
    }
    return {
        "format": 1,
        "digest": hashlib.sha256(canonical_json(digest_payload).encode("utf-8")).hexdigest(),
        "sources": sources,
        "declared_recipes": sum(len(entries) for _, _, entries in layers),
        "effective_recipes": len(selected),
        "serializers": serializers,
        "overrides": [
            {"recipe_id": recipe_id, "sources": sources}
            for recipe_id, sources in sorted(origins.items())
            if len(sources) > 1
        ],
    }


def _risk_matches(signature: str) -> tuple[tuple[str, str], ...]:
    return tuple(
        (code, label)
        for code, pattern, label in RISK_PATTERNS
        if pattern.search(signature)
    )


def _record_risk_evidence(
    collected: dict[str, set[str]],
    class_name: str,
    matches: tuple[tuple[str, str], ...],
    source_class: str | None = None,
):
    for code, label in matches:
        inherited = (
            ""
            if source_class is None or source_class == class_name
            else f" via {source_class}"
        )
        if label in {
            "drain",
            "extractEnergy",
            "extractItem",
            "fill",
            "getChance",
            "getIngredients",
            "insertItem",
            "random",
            "receiveEnergy",
        }:
            evidence = f"{class_name}#{label}{inherited}"
        else:
            evidence = f"{class_name}: {label}{inherited}"
        collected.setdefault(code, set()).add(evidence)


def _collect_risk_evidence(
    collected: dict[str, set[str]],
    class_name: str,
    signature: str,
    source_class: str | None = None,
):
    _record_risk_evidence(
        collected,
        class_name,
        _risk_matches(signature),
        source_class,
    )


def _finalize_risk_evidence(
    collected: dict[str, set[str]],
) -> list[dict]:
    for code, evidence in collected.items():
        exact_classes = {
            item.split("#", 1)[0]
            for item in evidence
            if "#" in item
        }
        collected[code] = {
            item
            for item in evidence
            if ":" not in item or item.split(":", 1)[0] not in exact_classes
        }
    return [
        {
            "code": code,
            "disposition": "needs_decision",
            "evidence": sorted(evidence),
        }
        for code, evidence in sorted(collected.items())
    ]


def _risk_evidence(candidates: list[dict]) -> list[dict]:
    collected: dict[str, set[str]] = {}
    for candidate in candidates:
        _collect_risk_evidence(
            collected,
            candidate["class"],
            candidate["public_signature"],
        )
    return _finalize_risk_evidence(collected)


def _validate_classification(class_name: str, bucket: str, value: dict, location: str):
    if not isinstance(value, dict) or set(value) != {"method", "evidence"}:
        raise ValueError(f"{location} classification requires method and evidence")
    method = value["method"]
    evidence = value["evidence"]
    if (
        not isinstance(evidence, list)
        or not evidence
        or any(not isinstance(item, str) or not item for item in evidence)
    ):
        raise ValueError(f"{location} classification evidence must be non-empty strings")
    if method == "class_hierarchy":
        targets = {
            "recipe_classes": RECIPE_INTERFACE,
            "recipe_types": RECIPE_TYPE_INTERFACE,
            "recipe_serializers": RECIPE_SERIALIZER_INTERFACE,
            "block_entity_classes": BLOCK_ENTITY_CLASS,
        }
        if bucket not in targets or evidence[0] != class_name or evidence[-1] != targets[bucket]:
            raise ValueError(f"{location} candidate bucket mismatch")
        if len(evidence) != len(set(evidence)):
            raise ValueError(f"{location} classification hierarchy contains a cycle")
        return
    if method == "name_term":
        if len(evidence) != 1:
            raise ValueError(f"{location} name-term classification requires one term")
        expected = _current_name_bucket(class_name)
        expected_bucket = expected[0] if expected is not None else None
        if expected_bucket is None:
            expected_bucket = _candidate_bucket(class_name)
        if expected_bucket != bucket or evidence[0] not in class_name.lower():
            raise ValueError(
                f"{location} candidate bucket mismatch: expected {expected_bucket}"
            )
        return
    raise ValueError(f"{location} has invalid classification method")


def _direct_hierarchy_bucket(
    class_name: str,
    public_signature: str,
) -> str | None:
    declaration = public_signature.split("{", 1)[0]
    class_match = re.search(
        rf"\b(?:class|interface|enum|record)\s+{re.escape(class_name)}"
        r"(?=[\s<({]|$)",
        declaration,
    )
    if class_match is None:
        return None
    suffix = declaration[class_match.end():]
    flattened = []
    generic_depth = 0
    for character in suffix:
        if character == "<":
            generic_depth += 1
        elif character == ">" and generic_depth:
            generic_depth -= 1
        elif generic_depth == 0:
            flattened.append(character)
    parent_types = {
        match.group(1)
        for match in re.finditer(
            r"(?:\bextends\b|\bimplements\b|,)\s*"
            r"([A-Za-z_$][A-Za-z0-9_$.]*)",
            "".join(flattened),
        )
    }
    return next(
        (
            target_bucket
            for target, target_bucket in (
                (RECIPE_INTERFACE, "recipe_classes"),
                (RECIPE_SERIALIZER_INTERFACE, "recipe_serializers"),
                (RECIPE_TYPE_INTERFACE, "recipe_types"),
                (BLOCK_ENTITY_CLASS, "block_entity_classes"),
            )
            if target in parent_types
        ),
        None,
    )


def _validate_hierarchy_priority(
    class_name: str,
    bucket: str,
    classification: dict,
    hierarchy: dict | None,
    public_signature: str,
    location: str,
):
    direct_bucket = _direct_hierarchy_bucket(class_name, public_signature)
    if hierarchy is None:
        if classification["method"] == "class_hierarchy":
            raise ValueError(f"{location} candidate bucket mismatch")
        if direct_bucket is not None and direct_bucket != bucket:
            raise ValueError(f"{location} candidate bucket mismatch")
        return
    if not isinstance(hierarchy, dict):
        raise ValueError(f"{location} hierarchy must be null or a classification")
    evidence = hierarchy.get("evidence")
    target_buckets = {
        RECIPE_INTERFACE: "recipe_classes",
        RECIPE_TYPE_INTERFACE: "recipe_types",
        RECIPE_SERIALIZER_INTERFACE: "recipe_serializers",
        BLOCK_ENTITY_CLASS: "block_entity_classes",
    }
    hierarchy_bucket = (
        target_buckets.get(evidence[-1])
        if isinstance(evidence, list) and evidence
        else None
    )
    if hierarchy_bucket is None:
        raise ValueError(f"{location} candidate bucket mismatch")
    if direct_bucket is not None and direct_bucket != hierarchy_bucket:
        raise ValueError(f"{location} candidate bucket mismatch")
    _validate_classification(
        class_name,
        hierarchy_bucket,
        hierarchy,
        location,
    )
    if bucket != hierarchy_bucket or classification != hierarchy:
        raise ValueError(f"{location} candidate bucket mismatch")


def _validate_structural_hierarchy(value: object) -> dict[str, dict]:
    if not isinstance(value, list):
        raise ValueError("audit structural_hierarchy must be a list")
    by_class = {}
    for index, record in enumerate(value):
        location = f"audit structural_hierarchy {index}"
        if not isinstance(record, dict) or set(record) != {
            "class",
            "classification",
        }:
            raise ValueError(
                f"{location} requires class and classification"
            )
        class_name = record["class"]
        if not isinstance(class_name, str) or not re.fullmatch(
            r"[A-Za-z_$][A-Za-z0-9_$.]*",
            class_name,
        ):
            raise ValueError(f"{location} has invalid class")
        if class_name in by_class:
            raise ValueError(
                f"audit structural_hierarchy repeats class {class_name}"
            )
        classification = record["classification"]
        evidence = (
            classification.get("evidence")
            if isinstance(classification, dict)
            else None
        )
        target_buckets = {
            RECIPE_INTERFACE: "recipe_classes",
            RECIPE_TYPE_INTERFACE: "recipe_types",
            RECIPE_SERIALIZER_INTERFACE: "recipe_serializers",
            BLOCK_ENTITY_CLASS: "block_entity_classes",
        }
        bucket = (
            target_buckets.get(evidence[-1])
            if isinstance(evidence, list) and evidence
            else None
        )
        if bucket is None:
            raise ValueError(f"{location} candidate bucket mismatch")
        _validate_classification(
            class_name,
            bucket,
            classification,
            location,
        )
        by_class[class_name] = classification
    if list(by_class) != sorted(by_class):
        raise ValueError("audit structural_hierarchy must be sorted by class")
    return by_class


def _structural_metadata(metadata: dict | None) -> dict | None:
    if metadata is None:
        return None
    return {
        "access_flags": metadata["access_flags"],
        "super_class": metadata["super_class"],
        "interfaces": list(metadata["interfaces"]),
    }


def _build_structural_class_graph(
    target_artifact_classes: list[str],
    metadata_by_class: dict[str, dict | None],
    target_artifact_sha256: str,
    classpath_class_locations: dict[str, Path],
    classpath_artifact_checks: list[tuple[Path, str, int]],
) -> list[dict]:
    classpath_sha256_by_path = {
        path: digest for path, digest, _ in classpath_artifact_checks
    }
    included = set(target_artifact_classes)
    pending = list(target_artifact_classes)
    while pending:
        class_name = pending.pop()
        metadata = metadata_by_class.get(class_name)
        if metadata is None:
            continue
        parents = [
            *metadata["interfaces"],
            *([metadata["super_class"]] if metadata["super_class"] else []),
        ]
        for parent in parents:
            if parent in metadata_by_class and parent not in included:
                included.add(parent)
                pending.append(parent)

    graph = []
    target_class_set = set(target_artifact_classes)
    for class_name in sorted(included):
        if class_name in target_class_set:
            owner_sha256 = target_artifact_sha256
        else:
            owner_path = classpath_class_locations.get(class_name)
            owner_sha256 = classpath_sha256_by_path.get(owner_path)
            if owner_sha256 is None:
                raise ValueError(
                    "structural ancestry owner is unresolved: " + class_name
                )
        graph.append({
            "class": class_name,
            "owner_sha256": owner_sha256,
            "metadata": _structural_metadata(metadata_by_class.get(class_name)),
        })
    return graph


def _reachable_ancestry_classpath(
    ancestry_classpath: list[dict],
    structural_class_graph: list[dict],
    target_artifact_sha256: str,
) -> list[dict]:
    reachable_sha256 = {
        record["owner_sha256"]
        for record in structural_class_graph
        if record["owner_sha256"] != target_artifact_sha256
    }
    return [
        record
        for record in ancestry_classpath
        if record["sha256"] in reachable_sha256
    ]


def _normalize_ancestry_dependencies(
    values,
    ancestry_classpath: list[dict],
) -> list[dict]:
    identities_by_sha256 = {
        record["sha256"]: record for record in ancestry_classpath
    }
    normalized = []
    seen_sha256 = set()
    seen_dependencies = set()
    for index, value in enumerate(values or ()):
        if not isinstance(value, str) or "=" not in value:
            raise ValueError(
                f"classpath dependency {index} must use sha256=group:name:version[:classifier]"
            )
        sha256, dependency = value.split("=", 1)
        if not re.fullmatch(r"[0-9a-f]{64}", sha256):
            raise ValueError(f"classpath dependency {index} has invalid SHA")
        _validate_resolvable_dependency_coordinate(
            dependency,
            f"classpath dependency {index}",
        )
        identity = identities_by_sha256.get(sha256)
        if identity is None:
            raise ValueError(
                f"classpath dependency {index} does not match a supplied classpath artifact"
            )
        if sha256 in seen_sha256:
            raise ValueError("classpath dependencies repeat an artifact")
        if dependency in seen_dependencies:
            raise ValueError("classpath dependencies repeat a coordinate")
        seen_sha256.add(sha256)
        seen_dependencies.add(dependency)
        normalized.append({
            "dependency": dependency,
            "sha256": sha256,
            "size": identity["size"],
        })
    return sorted(
        normalized,
        key=lambda record: (
            record["dependency"],
            record["sha256"],
            record["size"],
        ),
    )


def _reachable_ancestry_dependencies(
    ancestry_dependencies: list[dict],
    ancestry_classpath: list[dict],
) -> list[dict]:
    reachable = {
        (record["sha256"], record["size"])
        for record in ancestry_classpath
    }
    return [
        record
        for record in ancestry_dependencies
        if (record["sha256"], record["size"]) in reachable
    ]


def _validate_structural_class_graph(
    value: object,
    artifact_sha256: str,
    ancestry_classpath: list[dict],
) -> tuple[dict[str, dict | None], list[str]]:
    if not isinstance(value, list):
        raise ValueError("audit structural_class_graph must be a list")
    if len(value) > MAX_ARCHIVE_ENTRIES + MAX_CLASSPATH_CLASSES:
        raise ValueError("audit structural_class_graph has too many classes")
    allowed_owners = {
        artifact_sha256,
        *(record["sha256"] for record in ancestry_classpath),
    }
    metadata_by_class = {}
    target_classes = []
    for index, record in enumerate(value):
        location = f"audit structural_class_graph {index}"
        if not isinstance(record, dict) or set(record) != {
            "class",
            "owner_sha256",
            "metadata",
        }:
            raise ValueError(
                f"{location} requires class, owner_sha256, and metadata"
            )
        class_name = record["class"]
        if not isinstance(class_name, str) or not JAVA_TYPE.fullmatch(class_name):
            raise ValueError(f"{location} has invalid class")
        if class_name in metadata_by_class:
            raise ValueError(
                f"audit structural_class_graph repeats class {class_name}"
            )
        owner_sha256 = record["owner_sha256"]
        if (
            not isinstance(owner_sha256, str)
            or not re.fullmatch(r"[0-9a-f]{64}", owner_sha256)
            or owner_sha256 not in allowed_owners
        ):
            raise ValueError(f"{location} has unknown owner_sha256")
        metadata = record["metadata"]
        if metadata is not None:
            if not isinstance(metadata, dict) or set(metadata) != {
                "access_flags",
                "super_class",
                "interfaces",
            }:
                raise ValueError(f"{location} has invalid metadata")
            access_flags = metadata["access_flags"]
            if isinstance(access_flags, bool) or not isinstance(access_flags, int):
                raise ValueError(f"{location} has invalid access_flags")
            super_class = metadata["super_class"]
            if super_class is not None and (
                not isinstance(super_class, str)
                or not JAVA_TYPE.fullmatch(super_class)
            ):
                raise ValueError(f"{location} has invalid super_class")
            interfaces = metadata["interfaces"]
            if (
                not isinstance(interfaces, list)
                or any(
                    not isinstance(interface, str)
                    or not JAVA_TYPE.fullmatch(interface)
                    for interface in interfaces
                )
                or len(interfaces) != len(set(interfaces))
            ):
                raise ValueError(f"{location} has invalid interfaces")
            metadata = {
                "access_flags": access_flags,
                "super_class": super_class,
                "interfaces": interfaces,
            }
        metadata_by_class[class_name] = metadata
        if owner_sha256 == artifact_sha256:
            target_classes.append(class_name)
    if list(metadata_by_class) != sorted(metadata_by_class):
        raise ValueError("audit structural_class_graph must be sorted by class")
    return metadata_by_class, target_classes


def _validate_recipe_data(value: dict, artifact_sha: str):
    if not isinstance(value, dict) or set(value) != {
        "format",
        "digest",
        "sources",
        "declared_recipes",
        "effective_recipes",
        "serializers",
        "overrides",
    }:
        raise ValueError(
            "audit recipe_data has invalid fields"
        )
    if value["format"] != 1:
        raise ValueError("audit recipe_data has unsupported format")
    if not isinstance(value["digest"], str) or not re.fullmatch(
        r"[0-9a-f]{64}", value["digest"]
    ):
        raise ValueError("audit recipe_data has invalid digest")
    sources = value["sources"]
    if not isinstance(sources, list) or not sources:
        raise ValueError("audit recipe_data sources must be a non-empty list")
    source_ids = []
    for index, source in enumerate(sources):
        if not isinstance(source, dict) or set(source) != {
            "id",
            "sha256",
            "declared_recipes",
        }:
            raise ValueError(f"audit recipe_data source {index} is invalid")
        expected_id = "target_jar" if index == 0 else f"data_root_{index}"
        if source["id"] != expected_id:
            raise ValueError(f"audit recipe_data source {index} has invalid id")
        if not isinstance(source["sha256"], str) or not re.fullmatch(
            r"[0-9a-f]{64}", source["sha256"]
        ):
            raise ValueError(f"audit recipe_data source {index} has invalid SHA")
        source_ids.append(source["id"])
        declared = source["declared_recipes"]
        if isinstance(declared, bool) or not isinstance(declared, int) or declared < 0:
            raise ValueError(f"audit recipe_data source {index} has invalid count")
    if sources[0]["sha256"] != artifact_sha:
        raise ValueError("audit recipe_data target jar SHA does not match artifact")
    declared_total = value["declared_recipes"]
    effective_total = value["effective_recipes"]
    if (
        isinstance(declared_total, bool)
        or not isinstance(declared_total, int)
        or declared_total < 0
        or declared_total > MAX_RECIPE_FILES
        or isinstance(effective_total, bool)
        or not isinstance(effective_total, int)
        or effective_total < 0
        or effective_total > declared_total
        or declared_total != sum(source["declared_recipes"] for source in sources)
    ):
        raise ValueError("audit recipe_data recipe counts are invalid")
    serializers = value["serializers"]
    if not isinstance(serializers, list):
        raise ValueError("audit recipe_data serializers must be a list")
    serializer_ids = []
    counted = 0
    for index, serializer in enumerate(serializers):
        location = f"audit recipe_data serializer {index}"
        if not isinstance(serializer, dict) or set(serializer) != {
            "serializer_id",
            "recipe_count",
            "conditional_recipes",
            "condition_types",
            "sample_recipe_ids",
            "fields",
            "max_array_sizes",
        }:
            raise ValueError(f"{location} is invalid")
        serializer_id = serializer["serializer_id"]
        if not isinstance(serializer_id, str) or not RESOURCE_LOCATION.fullmatch(serializer_id):
            raise ValueError(f"{location} has invalid serializer_id")
        serializer_ids.append(serializer_id)
        recipe_count = serializer["recipe_count"]
        conditioned = serializer["conditional_recipes"]
        if (
            isinstance(recipe_count, bool)
            or not isinstance(recipe_count, int)
            or recipe_count < 1
            or isinstance(conditioned, bool)
            or not isinstance(conditioned, int)
            or conditioned < 0
            or conditioned > recipe_count
        ):
            raise ValueError(f"{location} has invalid counts")
        counted += recipe_count
        condition_types = serializer["condition_types"]
        if (
            not isinstance(condition_types, list)
            or condition_types != sorted(set(condition_types))
            or any(
                not isinstance(condition_type, str)
                or not RESOURCE_LOCATION.fullmatch(condition_type)
                for condition_type in condition_types
            )
        ):
            raise ValueError(f"{location} has invalid condition_types")
        samples = serializer["sample_recipe_ids"]
        if (
            not isinstance(samples, list)
            or not samples
            or len(samples) > MAX_RECIPE_SAMPLES_PER_SERIALIZER
            or samples != sorted(set(samples))
            or any(not isinstance(item, str) or not RESOURCE_LOCATION.fullmatch(item) for item in samples)
        ):
            raise ValueError(f"{location} has invalid sample_recipe_ids")
        fields = serializer["fields"]
        if not isinstance(fields, list) or fields != sorted(set(fields)) or any(
            not isinstance(field, str) or not field for field in fields
        ):
            raise ValueError(f"{location} has invalid fields")
        sizes = serializer["max_array_sizes"]
        if not isinstance(sizes, dict) or any(
            not isinstance(field, str)
            or not field
            or isinstance(size, bool)
            or not isinstance(size, int)
            or size < 0
            for field, size in sizes.items()
        ):
            raise ValueError(f"{location} has invalid max_array_sizes")
    if serializer_ids != sorted(set(serializer_ids)) or counted != effective_total:
        raise ValueError("audit recipe_data serializer inventory mismatch")
    overrides = value["overrides"]
    if not isinstance(overrides, list):
        raise ValueError("audit recipe_data overrides must be a list")
    override_ids = []
    for index, override in enumerate(overrides):
        if not isinstance(override, dict) or set(override) != {"recipe_id", "sources"}:
            raise ValueError(f"audit recipe_data override {index} is invalid")
        recipe_id = override["recipe_id"]
        override_sources = override["sources"]
        if (
            not isinstance(recipe_id, str)
            or not RESOURCE_LOCATION.fullmatch(recipe_id)
            or not isinstance(override_sources, list)
            or len(override_sources) < 2
            or any(source not in source_ids for source in override_sources)
        ):
            raise ValueError(f"audit recipe_data override {index} is invalid")
        override_ids.append(recipe_id)
    if override_ids != sorted(set(override_ids)):
        raise ValueError("audit recipe_data overrides must be sorted and unique")


def _validate_audit(
    audit: dict,
    *,
    allow_legacy_classifier_drift: bool = False,
):
    if not isinstance(audit, dict):
        raise ValueError("audit must be a JSON object")
    unknown = sorted(set(audit) - AUDIT_TOP_KEYS)
    if unknown:
        raise ValueError("audit has unknown keys: " + ", ".join(unknown))
    if audit.get("schema") != SCHEMA_VERSION:
        raise ValueError(f"unsupported audit schema: {audit.get('schema')}")
    scanner_format = audit.get("scanner_format")
    if scanner_format not in {*LEGACY_SCAN_CACHE_VERSIONS, SCAN_CACHE_VERSION}:
        raise ValueError(
            "unsupported audit scanner format: "
            f"{scanner_format}"
        )
    if (
        allow_legacy_classifier_drift
        and scanner_format not in LEGACY_SCAN_CACHE_VERSIONS
    ):
        raise ValueError("classifier drift is valid only for legacy audits")
    structural_inventory_sha256 = audit.get(
        "structural_candidate_inventory_sha256"
    )
    if scanner_format in {13, 14, 15, 16, SCAN_CACHE_VERSION}:
        if not isinstance(structural_inventory_sha256, str) or not re.fullmatch(
            r"[0-9a-f]{64}",
            structural_inventory_sha256,
        ):
            raise ValueError(
                "audit structural candidate inventory must be a SHA-256 digest"
            )
    elif "structural_candidate_inventory_sha256" in audit:
        raise ValueError(
            "legacy audit must not contain structural candidate inventory"
        )
    if audit.get("kind") != "auto_storage_compat_audit":
        raise ValueError(f"invalid audit kind: {audit.get('kind')}")
    for key in ("target", "artifact", "source", "candidates", "risks"):
        if key not in audit:
            raise ValueError(f"audit is missing {key}")
    target = audit["target"]
    if not isinstance(target, dict) or set(target) != {
        "mod_id",
        "display_name",
        "version",
    }:
        raise ValueError("audit target requires mod_id, display_name, and version")
    if not isinstance(target["mod_id"], str) or not re.fullmatch(
        r"[a-z0-9_-]+",
        target["mod_id"],
    ):
        raise ValueError("audit target has invalid mod_id")
    for key in ("display_name", "version"):
        if not isinstance(target[key], str) or not target[key].strip():
            raise ValueError(f"audit target {key} must be a non-empty string")

    artifact = audit["artifact"]
    artifact_keys = {"sha256", "size"}
    if scanner_format in {15, 16, SCAN_CACHE_VERSION}:
        artifact_keys.update({"class_count", "class_inventory_sha256"})
    if not isinstance(artifact, dict) or set(artifact) != artifact_keys:
        raise ValueError(
            "audit artifact requires " + ", ".join(sorted(artifact_keys))
        )
    if not isinstance(artifact["sha256"], str) or not re.fullmatch(
        r"[0-9a-f]{64}",
        artifact["sha256"],
    ):
        raise ValueError("audit artifact sha256 must be a SHA-256 digest")
    if (
        isinstance(artifact["size"], bool)
        or not isinstance(artifact["size"], int)
        or artifact["size"] <= 0
    ):
        raise ValueError("audit artifact size must be a positive integer")
    if scanner_format in {15, 16, SCAN_CACHE_VERSION}:
        if (
            isinstance(artifact["class_count"], bool)
            or not isinstance(artifact["class_count"], int)
            or not 0 <= artifact["class_count"] <= MAX_ARCHIVE_ENTRIES
        ):
            raise ValueError("audit artifact class_count is invalid")
        if (
            not isinstance(artifact["class_inventory_sha256"], str)
            or not re.fullmatch(
                r"[0-9a-f]{64}",
                artifact["class_inventory_sha256"],
            )
        ):
            raise ValueError(
                "audit artifact class_inventory_sha256 is invalid"
            )

    if scanner_format != 7:
        classpath = audit.get("ancestry_classpath")
        if not isinstance(classpath, list):
            raise ValueError("audit is missing ancestry_classpath")
        if len(classpath) > MAX_CLASSPATH_JARS:
            raise ValueError("audit ancestry_classpath has too many artifacts")
        normalized_classpath = []
        for index, record in enumerate(classpath):
            if not isinstance(record, dict) or set(record) != {"sha256", "size"}:
                raise ValueError(
                    f"audit ancestry_classpath {index} requires sha256 and size"
                )
            if not isinstance(record["sha256"], str) or not re.fullmatch(
                r"[0-9a-f]{64}", record["sha256"]
            ):
                raise ValueError(
                    f"audit ancestry_classpath {index} has invalid SHA"
                )
            if (
                isinstance(record["size"], bool)
                or not isinstance(record["size"], int)
                or record["size"] <= 0
            ):
                raise ValueError(
                    f"audit ancestry_classpath {index} has invalid size"
                )
            normalized_classpath.append(record)
        if classpath != sorted(
            normalized_classpath,
            key=lambda record: (record["sha256"], record["size"]),
        ) or len({(record["sha256"], record["size"]) for record in classpath}) != len(classpath):
            raise ValueError(
                "audit ancestry_classpath must be sorted and unique"
            )
    elif "ancestry_classpath" in audit:
        raise ValueError("legacy audit must not contain ancestry_classpath")

    if scanner_format in {16, SCAN_CACHE_VERSION}:
        ancestry_dependencies = audit.get("ancestry_dependencies")
        if not isinstance(ancestry_dependencies, list):
            raise ValueError("audit is missing ancestry_dependencies")
        if len(ancestry_dependencies) > MAX_CLASSPATH_JARS:
            raise ValueError("audit ancestry_dependencies has too many artifacts")
        classpath_identities = {
            (record["sha256"], record["size"])
            for record in audit["ancestry_classpath"]
        }
        normalized_dependencies = []
        for index, record in enumerate(ancestry_dependencies):
            location = f"audit ancestry_dependencies {index}"
            if not isinstance(record, dict) or set(record) != {
                "dependency",
                "sha256",
                "size",
            }:
                raise ValueError(
                    f"{location} requires dependency, sha256, and size"
                )
            _validate_resolvable_dependency_coordinate(
                record["dependency"],
                f"{location} dependency",
            )
            if not isinstance(record["sha256"], str) or not re.fullmatch(
                r"[0-9a-f]{64}", record["sha256"]
            ):
                raise ValueError(f"{location} has invalid SHA")
            if (
                isinstance(record["size"], bool)
                or not isinstance(record["size"], int)
                or record["size"] <= 0
            ):
                raise ValueError(f"{location} has invalid size")
            if (record["sha256"], record["size"]) not in classpath_identities:
                raise ValueError(
                    f"{location} does not match ancestry_classpath"
                )
            normalized_dependencies.append(record)
        if ancestry_dependencies != sorted(
            normalized_dependencies,
            key=lambda record: (
                record["dependency"],
                record["sha256"],
                record["size"],
            ),
        ):
            raise ValueError(
                "audit ancestry_dependencies must be sorted"
            )
        if len({
            (record["dependency"], record["sha256"], record["size"])
            for record in ancestry_dependencies
        }) != len(ancestry_dependencies) or len({
            record["dependency"] for record in ancestry_dependencies
        }) != len(ancestry_dependencies) or len({
            record["sha256"] for record in ancestry_dependencies
        }) != len(ancestry_dependencies):
            raise ValueError(
                "audit ancestry_dependencies must be unique"
            )
    elif "ancestry_dependencies" in audit:
        raise ValueError(
            "legacy audit must not contain ancestry_dependencies"
        )

    if scanner_format in {14, 15, 16, SCAN_CACHE_VERSION}:
        if "structural_class_graph" not in audit:
            raise ValueError("audit is missing structural_class_graph")
        structural_class_graph = audit["structural_class_graph"]
    else:
        if "structural_class_graph" in audit:
            raise ValueError(
                "legacy audit must not contain structural_class_graph"
            )
        structural_class_graph = None

    source = audit["source"]
    if not isinstance(source, dict) or set(source) != {"revision", "files"}:
        raise ValueError("audit source requires revision and files")
    revision = source["revision"]
    if revision is not None and (
        not isinstance(revision, str)
        or not re.fullmatch(r"[0-9a-f]{40,64}", revision)
    ):
        raise ValueError("audit source revision must be null or a Git digest")
    files = source["files"]
    if not isinstance(files, list) or any(
        not isinstance(path, str) or not path for path in files
    ):
        raise ValueError("audit source files must be a string list")
    if len(set(files)) != len(files):
        raise ValueError("audit source files must not contain duplicates")
    if files != sorted(files):
        raise ValueError("audit source files must be sorted")
    for path in files:
        if not re.fullmatch(SOURCE_EVIDENCE_PATH_PATTERN, path):
            raise ValueError(
                "audit source files must be safe repository-relative Java paths"
            )
    if revision is None and files:
        raise ValueError("audit source null revision requires empty files")

    if scanner_format != 7 and "recipe_data" not in audit:
        raise ValueError("audit is missing recipe_data")
    if scanner_format == 7 and "recipe_data" in audit:
        raise ValueError("legacy audit must not contain recipe_data")

    if scanner_format in {10, 11, 12, 13, 14, 15, 16, SCAN_CACHE_VERSION}:
        if "structural_hierarchy" not in audit:
            raise ValueError("audit is missing structural_hierarchy")
        structural_hierarchy = _validate_structural_hierarchy(
            audit["structural_hierarchy"]
        )
    else:
        if "structural_hierarchy" in audit:
            raise ValueError(
                "legacy audit must not contain structural_hierarchy"
            )
        structural_hierarchy = {}

    candidates = audit["candidates"]
    candidate_buckets = {
        "recipe_classes",
        "resource_apis",
        "station_classes",
    }
    if scanner_format != 7:
        candidate_buckets = set(CURRENT_CANDIDATE_BUCKETS)
    if not isinstance(candidates, dict) or set(candidates) != candidate_buckets:
        raise ValueError(
            "audit candidates require " + ", ".join(sorted(candidate_buckets))
        )
    seen_classes = set()
    candidates_by_class = {}
    for bucket in sorted(candidate_buckets):
        records = candidates[bucket]
        if not isinstance(records, list):
            raise ValueError(f"audit candidates {bucket} must be a list")
        for index, record in enumerate(records):
            location = f"audit candidates {bucket} {index}"
            record_keys = {"class", "public_signature"}
            if scanner_format != 7:
                record_keys.add("classification")
            if scanner_format in {9, 11, 12, 13, 14, 15, 16, SCAN_CACHE_VERSION}:
                record_keys.add("hierarchy")
            if scanner_format in {13, 14, 15, 16, SCAN_CACHE_VERSION}:
                record_keys.add("source_class")
            if not isinstance(record, dict) or set(record) != record_keys:
                raise ValueError(
                    f"{location} has invalid candidate fields"
                )
            class_name = record["class"]
            if not isinstance(class_name, str) or not re.fullmatch(
                r"[A-Za-z_$][A-Za-z0-9_$.]*",
                class_name,
            ):
                raise ValueError(f"{location} has invalid class")
            if (
                not isinstance(record["public_signature"], str)
                or not record["public_signature"].strip()
            ):
                raise ValueError(f"{location} has empty public_signature")
            if scanner_format in {13, 14, 15, 16, SCAN_CACHE_VERSION}:
                _validate_candidate_source_class(
                    class_name,
                    record["source_class"],
                    location,
                )
            if scanner_format != 7:
                _validate_classification(
                    class_name,
                    bucket,
                    record["classification"],
                    location,
                )
                if allow_legacy_classifier_drift:
                    pass
                elif scanner_format == 9:
                    _validate_hierarchy_priority(
                        class_name,
                        bucket,
                        record["classification"],
                        record["hierarchy"],
                        record["public_signature"],
                        location,
                    )
                elif scanner_format == 10:
                    _validate_hierarchy_priority(
                        class_name,
                        bucket,
                        record["classification"],
                        structural_hierarchy.get(class_name),
                        record["public_signature"],
                        location,
                    )
                elif scanner_format in {11, 12, 13, 14, 15, 16, SCAN_CACHE_VERSION}:
                    candidate_hierarchy = record["hierarchy"]
                    persisted_hierarchy = structural_hierarchy.get(class_name)
                    if candidate_hierarchy != persisted_hierarchy:
                        raise ValueError(f"{location} candidate bucket mismatch")
                    _validate_hierarchy_priority(
                        class_name,
                        bucket,
                        record["classification"],
                        candidate_hierarchy,
                        record["public_signature"],
                        location,
                    )
            else:
                expected_bucket = _candidate_bucket(class_name)
                if expected_bucket != bucket:
                    raise ValueError(
                        f"{location} candidate bucket mismatch: "
                        f"expected {expected_bucket}"
                    )
            if class_name in seen_classes:
                raise ValueError(f"audit repeats candidate class {class_name}")
            seen_classes.add(class_name)
            if scanner_format != 7:
                candidates_by_class[class_name] = (
                    bucket,
                    record["classification"],
                )

    if revision is not None and seen_classes and not files:
        raise ValueError(
            "audit with classified candidates requires at least one source file"
        )

    if scanner_format in {10, 11, 12, 13, 14, 15, 16, SCAN_CACHE_VERSION}:
        unknown_structural_classes = sorted(
            set(structural_hierarchy) - seen_classes
        )
        if unknown_structural_classes:
            raise ValueError(
                "audit structural_hierarchy owner is not an audited candidate: "
                + ", ".join(unknown_structural_classes)
            )
    if scanner_format in {13, 14, 15, 16, SCAN_CACHE_VERSION}:
        expected_structural_inventory_sha256 = (
            _structural_candidate_inventory_sha256(
                artifact,
                audit["ancestry_classpath"],
                structural_hierarchy,
            )
        )
        if (
            structural_inventory_sha256
            != expected_structural_inventory_sha256
        ):
            raise ValueError(
                "audit structural candidate inventory does not match "
                "structural hierarchy"
            )
    if scanner_format in {14, 15, 16, SCAN_CACHE_VERSION}:
        structural_graph_metadata, structural_graph_target_classes = (
            _validate_structural_class_graph(
                structural_class_graph,
                artifact["sha256"],
                audit["ancestry_classpath"],
            )
        )
        if scanner_format in {15, 16, SCAN_CACHE_VERSION}:
            target_records = [
                {
                    "class": class_name,
                    "metadata": structural_graph_metadata[class_name],
                }
                for class_name in structural_graph_target_classes
            ]
            if (
                len(target_records) != artifact["class_count"]
                or _target_class_inventory_sha256(target_records)
                != artifact["class_inventory_sha256"]
            ):
                raise ValueError(
                    "audit structural graph does not match target class inventory"
                )
        independently_classified = {}
        independently_structural = {}
        for class_name in structural_graph_target_classes:
            classification = _classify_candidate(
                class_name,
                structural_graph_metadata,
            )
            if classification is None:
                continue
            independently_classified[class_name] = classification
            if classification[1]["method"] == "class_hierarchy":
                independently_structural[class_name] = classification[1]
        if not allow_legacy_classifier_drift:
            if candidates_by_class != independently_classified:
                raise ValueError(
                    "audit candidates do not match independent structural evidence"
                )
            if structural_hierarchy != independently_structural:
                raise ValueError(
                    "audit structural hierarchy does not match independent "
                    "structural evidence"
                )

    if scanner_format != 7:
        _validate_recipe_data(audit["recipe_data"], artifact["sha256"])

    recipe_classes = {
        record["class"] for record in candidates["recipe_classes"]
    }
    risks = audit["risks"]
    if not isinstance(risks, list):
        raise ValueError("audit risks must be a list")
    seen_risks = set()
    for index, risk in enumerate(risks):
        location = f"audit risk {index}"
        if not isinstance(risk, dict) or set(risk) != {
            "code",
            "disposition",
            "evidence",
        }:
            raise ValueError(
                f"{location} requires code, disposition, and evidence"
            )
        code = risk["code"]
        if not isinstance(code, str) or not re.fullmatch(r"[a-z0-9_]+", code):
            raise ValueError(f"{location} has invalid code")
        if code in seen_risks:
            raise ValueError(f"audit repeats risk code {code}")
        seen_risks.add(code)
        if risk["disposition"] != "needs_decision":
            raise ValueError(f"{location} has invalid disposition")
        evidence = risk["evidence"]
        if (
            not isinstance(evidence, list)
            or not evidence
            or any(not isinstance(item, str) or not item for item in evidence)
            or len(set(evidence)) != len(evidence)
        ):
            raise ValueError(f"{location} evidence must be a unique string list")
        for item in evidence:
            owner = item.split("#", 1)[0].split(":", 1)[0]
            if owner not in recipe_classes:
                raise ValueError(
                    f"{location} risk evidence owner is not an audited "
                    "recipe candidate"
                )


def _validate_audit_ancestry_graph(
    audit: dict,
    source_classpath,
    target_metadata_by_class: dict[str, dict | None],
) -> tuple[
    dict[str, dict | None],
    dict[str, Path],
    list[tuple[Path, str, int]],
]:
    expected_artifacts = audit["ancestry_classpath"]
    raw_paths = list(source_classpath or ())
    if expected_artifacts and not raw_paths:
        raise ValueError(
            "complete validation requires exact ancestry artifacts"
        )
    if len(raw_paths) > MAX_CLASSPATH_JARS:
        raise ValueError(
            f"exact ancestry artifacts exceed {MAX_CLASSPATH_JARS} jars"
        )
    class_entries: dict[str, list[tuple[Path, str]]] = {}
    artifact_checks = []
    actual_artifacts = []
    seen_artifacts = set()
    for raw_path in raw_paths:
        unresolved = Path(raw_path)
        if unresolved.is_symlink():
            raise ValueError(
                f"exact ancestry artifact is a symlink: {raw_path}"
            )
        path = unresolved.resolve()
        if not path.is_file():
            raise ValueError(
                f"exact ancestry artifact does not exist: {raw_path}"
            )
        size = path.stat().st_size
        digest = _sha256_file(path)
        identity = (digest, size)
        if identity in seen_artifacts:
            raise ValueError("exact ancestry artifacts repeat an artifact")
        seen_artifacts.add(identity)
        with zipfile.ZipFile(path) as archive:
            _validate_archive(path, archive)
            inspectable_cache = {}
            for entry_name in archive.namelist():
                if not _is_inspectable_class(
                    archive,
                    entry_name,
                    inspectable_cache=inspectable_cache,
                ):
                    continue
                class_name = _class_name(entry_name)
                if class_name in class_entries:
                    raise ValueError(
                        "exact ancestry artifacts repeat class " + class_name
                    )
                class_entries[class_name] = [(path, entry_name)]
        actual_artifacts.append({"sha256": digest, "size": size})
        artifact_checks.append((path, digest, size))
    actual_artifacts.sort(
        key=lambda record: (record["sha256"], record["size"])
    )
    if actual_artifacts != expected_artifacts:
        raise ValueError(
            "exact ancestry artifacts do not match source audit"
        )

    metadata_by_class = dict(target_metadata_by_class)
    class_locations = {}
    pending = list(target_metadata_by_class)
    while pending:
        class_name = pending.pop()
        metadata = metadata_by_class.get(class_name)
        if metadata is None:
            continue
        parents = [
            *metadata["interfaces"],
            *([metadata["super_class"]] if metadata["super_class"] else []),
        ]
        for parent in parents:
            if parent in metadata_by_class:
                continue
            matches = []
            for path, entry_name in class_entries.get(parent, []):
                with zipfile.ZipFile(path) as archive:
                    matches.append((
                        path,
                        _class_metadata(archive.read(entry_name), entry_name),
                    ))
            if len(matches) > 1:
                raise ValueError(
                    "exact ancestry artifacts repeat class " + parent
                )
            if not matches:
                continue
            owner_path, parent_metadata = matches[0]
            metadata_by_class[parent] = parent_metadata
            class_locations[parent] = owner_path
            pending.append(parent)

    _require_resolved_ancestry(
        sorted(target_metadata_by_class),
        metadata_by_class,
    )
    actual_graph = _build_structural_class_graph(
        sorted(target_metadata_by_class),
        metadata_by_class,
        audit["artifact"]["sha256"],
        class_locations,
        artifact_checks,
    )
    if actual_graph != audit["structural_class_graph"]:
        raise ValueError(
            "exact ancestry graph does not match source audit"
        )
    for path, digest, size in artifact_checks:
        _require_unchanged_artifact(
            path,
            digest,
            size,
            "ancestry artifact",
        )
    return metadata_by_class, class_locations, artifact_checks


def _validate_audit_target_artifact(
    audit: dict,
    source_artifact,
    *,
    source_classpath=(),
) -> None:
    _validate_audit(audit)
    if audit["scanner_format"] != SCAN_CACHE_VERSION:
        raise ValueError(
            "target artifact class inventory requires a current scanner-format audit"
        )
    artifact = audit["artifact"]
    jar = Path(source_artifact).resolve()
    _require_unchanged_artifact(
        jar,
        artifact["sha256"],
        artifact["size"],
        "target artifact",
    )
    records = []
    target_metadata_by_class = {}
    seen_classes = set()
    target_recipe_data = None
    target = None
    with zipfile.ZipFile(jar) as archive:
        _validate_archive(jar, archive)
        try:
            target = _read_mod_metadata(
                archive,
                selected_mod_id=audit["target"]["mod_id"],
            )
        except ValueError as error:
            raise ValueError(
                "target metadata does not match exact artifact"
            ) from error
        inspectable_cache = {}
        for entry_name in sorted(archive.namelist()):
            if not _is_inspectable_class(
                archive,
                entry_name,
                inspectable_cache=inspectable_cache,
            ):
                continue
            class_name = _class_name(entry_name)
            if class_name in seen_classes:
                raise ValueError(
                    "target artifact repeats normalized class " + class_name
                )
            seen_classes.add(class_name)
            metadata = _class_metadata(
                archive.read(entry_name),
                entry_name,
            )
            target_metadata_by_class[class_name] = metadata
            records.append({
                "class": class_name,
                "metadata": _structural_metadata(metadata),
            })
        target_recipe_data = _recipe_data_inventory(
            archive,
            artifact["sha256"],
            (),
        )
    if target != audit["target"]:
        raise ValueError(
            "target metadata does not match exact artifact"
        )
    records.sort(key=lambda record: record["class"])
    expected_records = [
        {
            "class": record["class"],
            "metadata": record["metadata"],
        }
        for record in audit["structural_class_graph"]
        if record["owner_sha256"] == artifact["sha256"]
    ]
    if (
        len(records) != artifact["class_count"]
        or _target_class_inventory_sha256(records)
        != artifact["class_inventory_sha256"]
        or records != expected_records
    ):
        raise ValueError(
            "target artifact class inventory does not match source audit"
        )
    metadata_by_class, class_locations, ancestry_artifact_checks = (
        _validate_audit_ancestry_graph(
            audit,
            source_classpath,
            target_metadata_by_class,
        )
    )
    for records in audit["candidates"].values():
        for candidate in records:
            expected_source_class = _candidate_source_class(
                candidate["class"],
                target_metadata_by_class,
            )
            if candidate["source_class"] != expected_source_class:
                raise ValueError(
                    "target artifact source_class does not match source audit: "
                    + candidate["class"]
                )

    inspectable_candidates = [
        candidate
        for records in audit["candidates"].values()
        for candidate in records
        if target_metadata_by_class.get(candidate["class"]) is not None
    ]
    javap = None
    jdk_identity = None
    if inspectable_candidates:
        javap, _, _, _, jdk_identity = _jdk_21_toolchain()
        actual_signatures = _public_signatures(
            jar,
            [candidate["class"] for candidate in inspectable_candidates],
            javap,
        )
        for candidate in inspectable_candidates:
            actual_signature = actual_signatures[candidate["class"]]
            if actual_signature != candidate["public_signature"]:
                raise ValueError(
                    "target artifact public signature does not match "
                    "source audit: " + candidate["class"]
                )

    inspectable_recipe_classes = {
        candidate["class"]
        for candidate in audit["candidates"]["recipe_classes"]
        if target_metadata_by_class.get(candidate["class"]) is not None
    }
    if inspectable_recipe_classes:
        collected_risks: dict[str, set[str]] = {}
        risk_matches_by_class: dict[str, tuple[tuple[str, str], ...]] = {}
        for candidate in audit["candidates"]["recipe_classes"]:
            class_name = candidate["class"]
            if class_name not in inspectable_recipe_classes:
                continue
            for implementation_class in _implementation_ancestry(
                class_name,
                metadata_by_class,
            ):
                owner = (
                    jar
                    if implementation_class in target_metadata_by_class
                    else class_locations.get(implementation_class)
                )
                if owner is None:
                    raise ValueError(
                        "target artifact risk owner is unresolved: "
                        + implementation_class
                    )
                matches = risk_matches_by_class.get(implementation_class)
                if matches is None:
                    private_bytecode = _run_javap(
                        owner,
                        implementation_class,
                        "-c",
                        "-p",
                        output_limit=MAX_PRIVATE_BYTECODE_BYTES,
                        output_label="private bytecode",
                        javap=javap,
                    )
                    matches = _risk_matches(private_bytecode)
                    risk_matches_by_class[implementation_class] = matches
                _record_risk_evidence(
                    collected_risks,
                    class_name,
                    matches,
                    implementation_class,
                )
        actual_risks = _finalize_risk_evidence(collected_risks)
        expected_risks = []
        for risk in audit["risks"]:
            evidence = [
                item
                for item in risk["evidence"]
                if item.split("#", 1)[0].split(":", 1)[0]
                in inspectable_recipe_classes
            ]
            if evidence:
                expected_risks.append({**risk, "evidence": evidence})
        if actual_risks != expected_risks:
            raise ValueError(
                "target artifact risk evidence does not match source audit"
            )
    if javap is not None and jdk_identity is not None:
        _require_unchanged_artifact(
            javap,
            jdk_identity["javap"]["sha256"],
            jdk_identity["javap"]["size"],
            "javap executable",
        )
    for path, digest, size in ancestry_artifact_checks:
        _require_unchanged_artifact(
            path,
            digest,
            size,
            "ancestry artifact",
        )
    recipe_data = audit["recipe_data"]
    if (
        recipe_data["sources"][0] != target_recipe_data["sources"][0]
        or (
            len(recipe_data["sources"]) == 1
            and recipe_data != target_recipe_data
        )
    ):
        raise ValueError(
            "target artifact recipe inventory does not match source audit"
        )
    _require_unchanged_artifact(
        jar,
        artifact["sha256"],
        artifact["size"],
        "target artifact",
    )


def scan_jar(
    jar,
    *,
    selected_mod_id=None,
    source=None,
    classpath=None,
    classpath_dependencies=None,
    cache_dir=None,
    signature_reader=None,
    risk_reader=None,
    class_metadata_reader=None,
    data_roots=None,
) -> dict:
    data_roots = tuple(data_roots or ())
    jar = Path(jar).resolve()
    if not jar.is_file():
        raise ValueError(f"target jar does not exist: {jar}")
    artifact_size = jar.stat().st_size
    if artifact_size > MAX_JAR_BYTES:
        raise ValueError(f"target jar exceeds {MAX_JAR_BYTES} bytes: {jar}")
    artifact_sha = _sha256_file(jar)
    (
        external_metadata,
        supplied_ancestry_classpath,
        classpath_class_locations,
        classpath_artifact_checks,
    ) = _classpath_metadata(classpath)
    supplied_ancestry_dependencies = _normalize_ancestry_dependencies(
        classpath_dependencies,
        supplied_ancestry_classpath,
    )
    classpath_digest = hashlib.sha256(
        canonical_json({
            "artifacts": supplied_ancestry_classpath,
            "dependencies": supplied_ancestry_dependencies,
        }).encode("utf-8")
    ).hexdigest()
    javap, _, _, _, jdk_identity = _jdk_21_toolchain()
    cache_path = None
    cache_jdk_path = None

    reader = signature_reader or (
        lambda class_name: _run_javap(
            jar,
            class_name,
            "-public",
            javap=javap,
        )
    )
    with zipfile.ZipFile(jar) as archive:
        _validate_archive(jar, archive)
        target = _read_mod_metadata(
            archive,
            selected_mod_id=selected_mod_id,
        )
        recipe_data = _recipe_data_inventory(
            archive,
            artifact_sha,
            data_roots,
        )
        if cache_dir is not None and source is None:
            base_cache_identity = (
                recipe_data["digest"] if data_roots else artifact_sha
            )
            base_cache_identity = hashlib.sha256(
                (
                    base_cache_identity
                    + ":mod_id:"
                    + target["mod_id"]
                ).encode("utf-8")
            ).hexdigest()
            cache_identity = (
                hashlib.sha256(
                    (base_cache_identity + ":" + classpath_digest).encode("utf-8")
                ).hexdigest()
                if supplied_ancestry_classpath
                else base_cache_identity
            )
            cache_path = (
                Path(cache_dir)
                / cache_identity
                / SCAN_CACHE_DIRECTORY
                / "audit.json"
            )
            cache_jdk_path = cache_path.with_name("jdk-toolchain.json")
            if cache_path.is_file():
                cached = json.loads(cache_path.read_text())
                _validate_audit(cached)
                if not cache_jdk_path.is_file():
                    raise ValueError(
                        f"cached JDK identity is missing: {cache_jdk_path}"
                    )
                try:
                    cached_jdk_identity = json.loads(cache_jdk_path.read_text())
                except (OSError, UnicodeError, json.JSONDecodeError) as error:
                    raise ValueError(
                        f"malformed cached JDK identity: {cache_jdk_path}"
                    ) from error
            else:
                cached = None
                cached_jdk_identity = None
            if cached_jdk_identity == jdk_identity:
                if cached["artifact"]["sha256"] != artifact_sha:
                    raise ValueError(f"cached audit SHA mismatch: {cache_path}")
                if cached["recipe_data"]["digest"] != recipe_data["digest"]:
                    raise ValueError(f"cached recipe-data digest mismatch: {cache_path}")
                if cached["ancestry_classpath"] != _reachable_ancestry_classpath(
                    supplied_ancestry_classpath,
                    cached["structural_class_graph"],
                    artifact_sha,
                ):
                    raise ValueError(f"cached ancestry classpath mismatch: {cache_path}")
                if cached["ancestry_dependencies"] != (
                    _reachable_ancestry_dependencies(
                        supplied_ancestry_dependencies,
                        cached["ancestry_classpath"],
                    )
                ):
                    raise ValueError(
                        f"cached ancestry dependencies mismatch: {cache_path}"
                    )
                for path, digest, size in classpath_artifact_checks:
                    _require_unchanged_artifact(
                        path,
                        digest,
                        size,
                        "classpath jar",
                    )
                _require_unchanged_artifact(jar, artifact_sha, artifact_size)
                if data_roots and _recipe_data_inventory(
                    archive,
                    artifact_sha,
                    data_roots,
                ) != recipe_data:
                    raise ValueError("recipe data roots changed during scan")
                return cached
        classified = {bucket: [] for bucket in CURRENT_CANDIDATE_BUCKETS}
        structural_hierarchy = []
        class_entries = {}
        inspectable_cache = {}
        for name in archive.namelist():
            if not _is_inspectable_class(
                archive,
                name,
                inspectable_cache=inspectable_cache,
            ):
                continue
            class_name = _class_name(name)
            if class_name in class_entries:
                raise ValueError(
                    "target jar repeats normalized class " + class_name
                )
            class_entries[class_name] = name
        class_names = sorted(class_entries)
        target_metadata = {
            class_name: (
                class_metadata_reader(class_name)
                if class_metadata_reader is not None
                else _class_metadata(
                    archive.read(class_entries[class_name]),
                    class_entries[class_name],
                )
            )
            for class_name in class_names
        }
        duplicate_target_classes = sorted(
            set(target_metadata) & set(external_metadata)
        )
        if duplicate_target_classes:
            raise ValueError(
                "target jar repeats ancestry classpath class "
                + duplicate_target_classes[0]
            )
        if risk_reader is not None:
            private_reader = risk_reader
        elif signature_reader is not None:
            private_reader = signature_reader
        else:
            def private_reader(class_name: str) -> str:
                owner = (
                    jar
                    if class_name in target_metadata
                    else classpath_class_locations.get(class_name)
                )
                if owner is None:
                    raise ValueError(
                        "private bytecode owner is unresolved: " + class_name
                    )
                return _run_javap(
                    owner,
                    class_name,
                    "-c",
                    "-p",
                    output_limit=MAX_PRIVATE_BYTECODE_BYTES,
                    output_label="private bytecode",
                    javap=javap,
                )
        metadata_by_class = dict(external_metadata)
        metadata_by_class.update(target_metadata)
        _require_resolved_ancestry(class_names, metadata_by_class)
        candidates = [
            (class_name, classification[0], classification[1])
            for class_name in class_names
            if (
                classification := _classify_candidate(
                    class_name,
                    metadata_by_class,
                )
            ) is not None
        ]
        if len(candidates) > MAX_CANDIDATE_CLASSES:
            raise ValueError(
                f"target jar exceeds {MAX_CANDIDATE_CLASSES} candidate classes"
            )
        structural_class_graph = _build_structural_class_graph(
            class_names,
            metadata_by_class,
            artifact_sha,
            classpath_class_locations,
            classpath_artifact_checks,
        )
        ancestry_classpath = _reachable_ancestry_classpath(
            supplied_ancestry_classpath,
            structural_class_graph,
            artifact_sha,
        )
        ancestry_dependencies = _reachable_ancestry_dependencies(
            supplied_ancestry_dependencies,
            ancestry_classpath,
        )
        collected_risks: dict[str, set[str]] = {}
        risk_matches_by_class: dict[str, tuple[tuple[str, str], ...]] = {}
        for class_name, bucket, classification in candidates:
            signature = reader(class_name)
            if not isinstance(signature, str) or not signature.strip():
                raise ValueError(f"empty public signature for {class_name}")
            if len(signature.encode("utf-8")) > MAX_SIGNATURE_BYTES:
                raise ValueError(
                    f"public signature exceeds {MAX_SIGNATURE_BYTES} bytes: {class_name}"
                )
            classified[bucket].append(
                {
                    "class": class_name,
                    "source_class": _candidate_source_class(
                        class_name,
                        target_metadata,
                    ),
                    "public_signature": signature.strip(),
                    "classification": classification,
                    "hierarchy": (
                        copy.deepcopy(classification)
                        if classification["method"] == "class_hierarchy"
                        else None
                    ),
                }
            )
            if classification["method"] == "class_hierarchy":
                structural_hierarchy.append(
                    {
                        "class": class_name,
                        "classification": copy.deepcopy(classification),
                    }
                )
            if bucket == "recipe_classes":
                implementation_classes = _implementation_ancestry(
                    class_name,
                    metadata_by_class,
                )
                for implementation_class in implementation_classes:
                    if (
                        implementation_class not in target_metadata
                        and implementation_class not in external_metadata
                    ):
                        continue
                    matches = risk_matches_by_class.get(implementation_class)
                    if matches is None:
                        risk_signature = private_reader(implementation_class)
                        if (
                            not isinstance(risk_signature, str)
                            or not risk_signature.strip()
                        ):
                            raise ValueError(
                                "empty private bytecode for "
                                + implementation_class
                            )
                        if (
                            len(risk_signature.encode("utf-8"))
                            > MAX_PRIVATE_BYTECODE_BYTES
                        ):
                            raise ValueError(
                                "private bytecode exceeds "
                                f"{MAX_PRIVATE_BYTECODE_BYTES} bytes: "
                                + implementation_class
                            )
                        matches = _risk_matches(risk_signature)
                        risk_matches_by_class[implementation_class] = matches
                        del risk_signature
                    _record_risk_evidence(
                        collected_risks,
                        class_name,
                        matches,
                        implementation_class,
                    )
    all_candidates = [
        candidate
        for bucket in classified.values()
        for candidate in bucket
    ]
    target_class_records = [
        {
            "class": record["class"],
            "metadata": record["metadata"],
        }
        for record in structural_class_graph
        if record["owner_sha256"] == artifact_sha
    ]
    artifact = {
        "sha256": artifact_sha,
        "size": artifact_size,
        "class_count": len(target_class_records),
        "class_inventory_sha256": _target_class_inventory_sha256(
            target_class_records
        ),
    }
    source_path = Path(source) if source is not None else None
    source_suffixes = {
        candidate["class"]: _candidate_source_suffix(
            candidate["class"],
            target_metadata.get(candidate["class"]),
        )
        for candidate in all_candidates
    }
    unavailable_source_classes = sorted(
        class_name
        for class_name, suffix in source_suffixes.items()
        if suffix is None
    )
    if source_path is not None and unavailable_source_classes:
        raise ValueError(
            "source mapping unavailable for " + unavailable_source_classes[0]
        )
    audit = {
        "schema": SCHEMA_VERSION,
        "scanner_format": SCAN_CACHE_VERSION,
        "kind": "auto_storage_compat_audit",
        "target": target,
        "artifact": artifact,
        "ancestry_classpath": ancestry_classpath,
        "ancestry_dependencies": ancestry_dependencies,
        "source": _source_evidence(
            source_path,
            {
                suffix
                for suffix in source_suffixes.values()
                if suffix is not None
            },
        ),
        "structural_class_graph": structural_class_graph,
        "structural_hierarchy": structural_hierarchy,
        "structural_candidate_inventory_sha256": (
            _structural_candidate_inventory_sha256(
                artifact,
                ancestry_classpath,
                [record["class"] for record in structural_hierarchy],
            )
        ),
        "candidates": classified,
        "recipe_data": recipe_data,
        "risks": _finalize_risk_evidence(collected_risks),
    }
    for path, digest, size in classpath_artifact_checks:
        _require_unchanged_artifact(path, digest, size, "classpath jar")
    _require_unchanged_artifact(
        javap,
        jdk_identity["javap"]["sha256"],
        jdk_identity["javap"]["size"],
        "javap executable",
    )
    _require_unchanged_artifact(jar, artifact_sha, artifact_size)
    _validate_audit(audit)
    if data_roots:
        with zipfile.ZipFile(jar) as verification_archive:
            _validate_archive(jar, verification_archive)
            if _recipe_data_inventory(
                verification_archive,
                artifact_sha,
                data_roots,
            ) != recipe_data:
                raise ValueError("recipe data roots changed during scan")
        _require_unchanged_artifact(jar, artifact_sha, artifact_size)
    if cache_path is not None:
        cache_path.parent.mkdir(parents=True, exist_ok=True)
        cache_path.write_text(canonical_json(audit))
        cache_jdk_path.write_text(canonical_json(jdk_identity))
    return audit


def migrate_audit(
    legacy_audit: dict,
    jar,
    *,
    source=None,
    classpath=None,
    classpath_dependencies=None,
    cache_dir=None,
    signature_reader=None,
    risk_reader=None,
    class_metadata_reader=None,
    data_roots=None,
) -> dict:
    if legacy_audit.get("scanner_format") not in LEGACY_SCAN_CACHE_VERSIONS:
        raise ValueError("migrate-audit requires a legacy scanner-format audit")
    _validate_audit(
        legacy_audit,
        allow_legacy_classifier_drift=True,
    )
    migrated = scan_jar(
        jar,
        selected_mod_id=legacy_audit["target"]["mod_id"],
        source=source,
        classpath=classpath,
        classpath_dependencies=classpath_dependencies,
        cache_dir=cache_dir,
        signature_reader=signature_reader,
        risk_reader=risk_reader,
        class_metadata_reader=class_metadata_reader,
        data_roots=data_roots,
    )
    if migrated["target"] != legacy_audit["target"]:
        raise ValueError("migration target identity does not match legacy audit")
    if any(
        migrated["artifact"][key] != legacy_audit["artifact"][key]
        for key in ("sha256", "size")
    ):
        raise ValueError("migration artifact does not match legacy audit")
    return migrated


def _family_id(class_name: str) -> str:
    simple = class_name.rsplit(".", 1)[-1]
    words = re.sub(r"(?<!^)(?=[A-Z])", "_", simple).lower()
    normalized = re.sub(r"[^a-z0-9_]+", "_", words).strip("_")
    if normalized:
        return normalized
    return f"class_{class_name.encode('utf-8').hex()}"


def _audited_risks_by_class(audit: dict) -> dict[str, list[str]]:
    risks_by_class: dict[str, list[str]] = {}
    for risk in audit["risks"]:
        for evidence in risk["evidence"]:
            class_name = evidence.split("#", 1)[0].split(":", 1)[0]
            risks_by_class.setdefault(class_name, []).append(risk["code"])
    return {
        class_name: sorted(set(risks))
        for class_name, risks in risks_by_class.items()
    }


def decide_audit(audit: dict) -> tuple[dict, str]:
    _validate_audit(audit)
    risks_by_class = _audited_risks_by_class(audit)
    recipe_candidates = audit["candidates"]["recipe_classes"]
    simple_ids = [_family_id(candidate["class"]) for candidate in recipe_candidates]
    duplicate_ids = {
        family_id for family_id in simple_ids if simple_ids.count(family_id) > 1
    }
    families = []
    action_lines = [
        f"# Compatibility decisions for {audit['target']['display_name']}",
        "",
        "Every candidate remains blocked until the contract records consumed inputs, "
        "catalysts/tools, complete outputs/remainders, station, work/resource costs, "
        "deterministic bounds, and an evidence-backed acceptance or rejection.",
        "",
    ]
    for candidate in recipe_candidates:
        class_name = candidate["class"]
        risks = risks_by_class.get(class_name, [])
        family_id = _family_id(class_name)
        if family_id in duplicate_ids:
            family_id = f"{family_id}_{class_name.encode('utf-8').hex()}"
        family = {
            "id": family_id,
            "class": class_name,
            "status": "needs_decision",
            "recipe_type": None,
            "station": None,
            "inputs": [],
            "outputs": [],
            "costs": [],
            "risks": risks,
            "evidence": [f"{class_name}#public_signature"],
            "decision": None,
        }
        families.append(family)
        action_lines.append(f"## {class_name}")
        action_lines.append(
            "- Decide exact recipe type, consumed inputs, catalysts/tools, complete "
            "outputs/remainders, station, costs, and deterministic bounds."
        )
        if risks:
            action_lines.append("- Resolve risk flags: " + ", ".join(risks) + ".")
        action_lines.append("")
    contract = {
        "schema": SCHEMA_VERSION,
        "kind": "auto_storage_compat_contract",
        "target": dict(audit["target"]),
        "source_audit_sha256": audit["artifact"]["sha256"],
        "source_recipe_inventory_sha256": _recipe_inventory_sha256(
            candidate["class"] for candidate in recipe_candidates
        ),
        "source_recipe_data_sha256": audit["recipe_data"]["digest"],
        "families": families,
        "matrix": _pending_contract_matrix(audit["target"]["mod_id"]),
        "verification": {
            "fixture": None,
            "expected_game_tests": None,
            "game_test_task": None,
            "gradle_tasks": [],
            "checks": [],
            "evidence": {},
        },
    }
    validate_contract(contract, require_complete=False, source_audit=audit)
    return contract, "\n".join(action_lines).rstrip() + "\n"


def _validate_migration_source_contract(
    contract: dict,
    source_audit: dict,
):
    scanner_format = source_audit.get("scanner_format")
    _validate_audit(
        source_audit,
        allow_legacy_classifier_drift=(
            scanner_format in LEGACY_SCAN_CACHE_VERSIONS
        ),
    )
    if not isinstance(contract, dict):
        raise ValueError("contract must be a JSON object")
    if scanner_format != 7:
        validate_contract(
            contract,
            require_complete=False,
        )
        if (
            source_audit["recipe_data"]["digest"]
            != contract["source_recipe_data_sha256"]
        ):
            raise ValueError("contract recipe data does not match source audit")
    else:
        if "source_recipe_data_sha256" in contract:
            raise ValueError(
                "format 7 contract must not bind unverifiable recipe data"
            )
        allowed_keys = CONTRACT_TOP_KEYS - {"source_recipe_data_sha256"}
        required_keys = allowed_keys - {"matrix"}
        _unknown_keys(contract, allowed_keys, "format 7 contract")
        missing = sorted(required_keys - set(contract))
        if missing:
            raise ValueError(
                "format 7 contract is missing keys: " + ", ".join(missing)
            )
        normalized = copy.deepcopy(contract)
        normalized["source_recipe_data_sha256"] = "0" * 64
        normalized["matrix"] = copy.deepcopy(
            contract.get(
                "matrix",
                _pending_contract_matrix(normalized["target"]["mod_id"]),
            )
        )
        validate_contract(normalized, require_complete=False)

    target = contract["target"]
    audit_target = source_audit["target"]
    for key in ("mod_id", "display_name", "version"):
        if target[key] != audit_target[key]:
            raise ValueError(
                f"contract target {key} does not match source audit"
            )
    if source_audit["artifact"]["sha256"] != contract["source_audit_sha256"]:
        raise ValueError("contract target artifact does not match source audit")
    audited_recipe_classes = {
        candidate["class"]
        for candidate in source_audit["candidates"]["recipe_classes"]
    }
    contract_recipe_classes = {
        family["class"] for family in contract["families"]
    }
    if contract_recipe_classes != audited_recipe_classes:
        raise ValueError(
            "contract families do not match audited recipe candidates"
        )
    audited_risks = _audited_risks_by_class(source_audit)
    if any(
        set(family["risks"])
        != set(audited_risks.get(family["class"], []))
        for family in contract["families"]
    ):
        raise ValueError("contract family risks do not match source audit")


def migrate_contract(
    old_contract: dict,
    old_audit: dict,
    new_audit: dict,
) -> tuple[dict, str]:
    _validate_migration_source_contract(
        old_contract,
        old_audit,
    )
    _validate_audit(new_audit)
    if new_audit["scanner_format"] != SCAN_CACHE_VERSION:
        raise ValueError("contract migration requires a current new audit")
    if old_audit["target"] != new_audit["target"]:
        raise ValueError("contract migration audit target does not match")
    if old_audit["artifact"]["sha256"] != new_audit["artifact"]["sha256"]:
        raise ValueError("contract migration artifact SHA does not match")

    migrated, _ = decide_audit(new_audit)
    migrated["target"] = copy.deepcopy(old_contract["target"])
    migrated["matrix"] = copy.deepcopy(
        old_contract.get(
            "matrix",
            _pending_contract_matrix(old_contract["target"]["mod_id"]),
        )
    )
    migrated["verification"] = copy.deepcopy(old_contract["verification"])
    old_by_class = {
        family["class"]: family
        for family in old_contract["families"]
    }
    new_classes = {
        family["class"] for family in migrated["families"]
    }
    removed = sorted(set(old_by_class) - new_classes)
    removed_accepted = [
        class_name
        for class_name in removed
        if old_by_class[class_name]["status"] == "accepted"
    ]
    if removed_accepted:
        raise ValueError(
            "contract migration removed accepted recipe family: "
            + ", ".join(removed_accepted)
        )

    def evidence_by_class(audit: dict) -> dict[str, tuple]:
        signatures = {
            candidate["class"]: (
                candidate["public_signature"],
                candidate.get("source_class", candidate["class"]),
            )
            for candidate in audit["candidates"]["recipe_classes"]
        }
        risks = {class_name: [] for class_name in signatures}
        for risk in audit["risks"]:
            for item in risk["evidence"]:
                owner = item.split("#", 1)[0].split(":", 1)[0]
                if owner in risks:
                    risks[owner].append((risk["code"], item))
        recipe_digest = audit.get("recipe_data", {}).get("digest")
        ancestry = tuple(
            (record["sha256"], record["size"])
            for record in audit.get("ancestry_classpath", [])
        )
        ancestry_dependencies = tuple(
            (record["dependency"], record["sha256"], record["size"])
            for record in audit.get("ancestry_dependencies", [])
        )
        return {
            class_name: (
                signature,
                tuple(sorted(risks[class_name])),
                recipe_digest,
                ancestry,
                ancestry_dependencies,
            )
            for class_name, signature in signatures.items()
        }

    old_evidence = evidence_by_class(old_audit)
    new_evidence = evidence_by_class(new_audit)
    changed_evidence = []
    decision_fields = (
        "status",
        "recipe_type",
        "station",
        "inputs",
        "outputs",
        "costs",
        "decision",
    )
    for family in migrated["families"]:
        previous = old_by_class.get(family["class"])
        if previous is None:
            continue
        if old_evidence.get(family["class"]) != new_evidence.get(family["class"]):
            changed_evidence.append(family["class"])
            continue
        for field in decision_fields:
            family[field] = copy.deepcopy(previous[field])

    validate_contract(
        migrated,
        require_complete=False,
        source_audit=new_audit,
    )
    unresolved = [
        family
        for family in migrated["families"]
        if family["status"] == "needs_decision"
    ]
    lines = [
        f"# Contract migration for {new_audit['target']['display_name']}",
        "",
    ]
    if unresolved:
        lines.extend(["## Unresolved recipe families", ""])
        lines.extend(
            f"- `{family['class']}`" for family in unresolved
        )
    else:
        lines.append("No unresolved recipe families.")
    if removed:
        lines.extend(["", "## Removed legacy non-recipe candidates", ""])
        lines.extend(f"- `{class_name}`" for class_name in removed)
    if changed_evidence:
        lines.extend(["", "## Reopened changed evidence", ""])
        lines.extend(
            f"- `{class_name}` changed evidence"
            for class_name in sorted(changed_evidence)
        )
    return migrated, "\n".join(lines).rstrip() + "\n"


def _unknown_keys(value: dict, allowed: set[str], location: str):
    unknown = sorted(set(value) - allowed)
    if unknown:
        raise ValueError(f"{location} has unknown keys: {', '.join(unknown)}")


def _validate_nonempty_string(value, location: str):
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{location} must be a non-empty string")


def _validate_dependency_coordinate(value, location: str):
    _validate_nonempty_string(value, location)
    if re.search(r"[\x00-\x1f\x7f]", value):
        raise ValueError(f"{location} must not contain control characters")
    if not re.fullmatch(r"[^:\s]+:[^:\s]+:[^:\s]+", value):
        raise ValueError(
            f"{location} must use group:name:version Maven coordinates"
        )


def _validate_resolvable_dependency_coordinate(value, location: str):
    _validate_nonempty_string(value, location)
    if re.search(r"[\x00-\x1f\x7f]", value):
        raise ValueError(f"{location} must not contain control characters")
    if not re.fullmatch(r"[^:\s]+:[^:\s]+:[^:\s]+(?::[^:\s]+)?", value):
        raise ValueError(
            f"{location} must use group:name:version[:classifier] Maven coordinates"
        )


def _validate_amount(value, location: str):
    if isinstance(value, bool) or not isinstance(value, (int, str)):
        raise ValueError(f"{location} amount must be a positive integer or expression")
    if isinstance(value, int) and value <= 0:
        raise ValueError(f"{location} amount must be positive")
    if isinstance(value, str) and not value.strip():
        raise ValueError(f"{location} amount expression must not be empty")


def _validate_unique_strings(value, location: str, *, allow_empty: bool):
    if not isinstance(value, list) or (not allow_empty and not value):
        raise ValueError(f"{location} must be a {'non-empty ' if not allow_empty else ''}list")
    for index, entry in enumerate(value):
        _validate_nonempty_string(entry, f"{location} {index}")
    if len(set(value)) != len(value):
        raise ValueError(f"{location} must not contain duplicates")


def _pending_contract_matrix(mod_id: str) -> dict:
    return {
        "mods": [mod_id],
        "descriptors": [],
        "resourceKinds": [],
        "acceptedRecipes": [],
        "rejectedDescriptors": [],
        "rejectedResourceKinds": [],
        "recipeInventory": {
            "namespaces": [mod_id],
            "sha256": "0" * 64,
        },
    }


def _validate_contract_matrix(matrix, mod_id: str):
    if not isinstance(matrix, dict) or set(matrix) != MATRIX_KEYS:
        raise ValueError(
            "contract matrix must declare mods, descriptors, resourceKinds, "
            "acceptedRecipes, rejectedDescriptors, rejectedResourceKinds, "
            "and recipeInventory"
        )
    for key in MATRIX_LIST_KEYS:
        _validate_unique_strings(
            matrix[key], f"contract matrix {key}", allow_empty=key != "mods"
        )
    if mod_id not in matrix["mods"]:
        raise ValueError("contract matrix mods must include target mod_id")
    for matrix_mod_id in matrix["mods"]:
        if not re.fullmatch(r"[a-z0-9_]+", matrix_mod_id):
            raise ValueError("contract matrix mods contains invalid mod id")
    for key in MATRIX_LIST_KEYS[1:]:
        for value in matrix[key]:
            if RESOURCE_LOCATION.fullmatch(value) is None:
                raise ValueError(f"contract matrix {key} contains invalid resource id")
    inventory = matrix["recipeInventory"]
    if not isinstance(inventory, dict) or set(inventory) != {"namespaces", "sha256"}:
        raise ValueError(
            "contract matrix recipeInventory must declare namespaces and sha256"
        )
    _validate_unique_strings(
        inventory["namespaces"],
        "contract matrix recipeInventory namespaces",
        allow_empty=False,
    )
    for namespace in inventory["namespaces"]:
        if not re.fullmatch(r"[a-z0-9_]+", namespace):
            raise ValueError(
                "contract matrix recipeInventory namespaces contains invalid namespace"
            )
    if not isinstance(inventory["sha256"], str) or not re.fullmatch(
        r"[0-9a-f]{64}", inventory["sha256"]
    ):
        raise ValueError("contract matrix recipeInventory sha256 must be a SHA-256 digest")


def _validate_runtime_artifact_transforms(
    value,
    target: dict,
    source_audit_sha256: str,
):
    if not isinstance(value, dict) or not value:
        raise ValueError(
            "contract target runtime_artifact_transforms must be a non-empty object"
        )
    if len(value) != 1:
        raise ValueError(
            "contract target runtime_artifact_transforms must own exactly one artifact"
        )
    runtime_dependencies = {
        target.get("dependency"),
        *target.get("runtime_dependencies", []),
    }
    for dependency, transform in sorted(value.items()):
        location = f"contract target runtime_artifact_transforms {dependency!r}"
        _validate_dependency_coordinate(dependency, f"{location} dependency")
        if not isinstance(transform, dict):
            raise ValueError(f"{location} must be an object")
        _unknown_keys(transform, RUNTIME_ARTIFACT_TRANSFORM_KEYS, location)
        if set(transform) != RUNTIME_ARTIFACT_TRANSFORM_KEYS:
            raise ValueError(
                f"{location} requires sha256 and remove_entries"
            )
        if dependency not in runtime_dependencies:
            raise ValueError(
                f"{location} dependency must be an exact runtime dependency"
            )
        sha256 = transform["sha256"]
        if not isinstance(sha256, str) or not re.fullmatch(r"[0-9a-f]{64}", sha256):
            raise ValueError(f"{location} sha256 must be a SHA-256 digest")
        if dependency == target.get("dependency") and sha256 != source_audit_sha256:
            raise ValueError(f"{location} target SHA must match the source audit")
        if dependency != target.get("dependency") and sha256 == source_audit_sha256:
            raise ValueError(
                f"{location} repeats the pristine target artifact under another dependency"
            )
        entries = transform["remove_entries"]
        _validate_unique_strings(
            entries,
            f"{location} remove_entries",
            allow_empty=False,
        )
        for entry_index, entry in enumerate(entries):
            _validate_exact_zip_entry_path(
                entry,
                f"{location} remove_entries {entry_index}",
            )


def _validate_terms(value, location: str):
    if not isinstance(value, list):
        raise ValueError(f"{location} must be a list")
    for index, term in enumerate(value):
        term_location = f"{location} {index}"
        if not isinstance(term, dict):
            raise ValueError(f"{term_location} must be an object")
        _unknown_keys(term, TERM_KEYS, term_location)
        if not {"role", "resource_kind", "amount"} <= set(term):
            raise ValueError(
                f"{term_location} requires role, resource_kind, and amount"
            )
        _validate_nonempty_string(term["role"], f"{term_location} role")
        _validate_nonempty_string(
            term["resource_kind"], f"{term_location} resource_kind"
        )
        _validate_amount(term["amount"], term_location)
        if "selector" in term:
            _validate_nonempty_string(term["selector"], f"{term_location} selector")


def _validate_costs(value, location: str):
    if not isinstance(value, list):
        raise ValueError(f"{location} must be a list")
    for index, cost in enumerate(value):
        cost_location = f"{location} {index}"
        if not isinstance(cost, dict):
            raise ValueError(f"{cost_location} must be an object")
        _unknown_keys(cost, COST_KEYS, cost_location)
        if not {"resource_kind", "amount"} <= set(cost):
            raise ValueError(f"{cost_location} requires resource_kind and amount")
        _validate_nonempty_string(
            cost["resource_kind"], f"{cost_location} resource_kind"
        )
        _validate_amount(cost["amount"], cost_location)
        if "selector" in cost:
            _validate_nonempty_string(cost["selector"], f"{cost_location} selector")


def _validate_station(value, location: str):
    if not isinstance(value, dict):
        raise ValueError(f"{location} station must be an object")
    _unknown_keys(value, STATION_KEYS, f"{location} station")
    if set(value) != STATION_KEYS:
        raise ValueError(
            f"{location} station requires descriptor_id, category, and variants"
        )
    _validate_nonempty_string(
        value["descriptor_id"], f"{location} station descriptor_id"
    )
    if not RESOURCE_LOCATION.fullmatch(value["descriptor_id"]):
        raise ValueError(f"{location} station descriptor_id must be a resource location")
    if value["category"] not in ("instant", "process", "transform"):
        raise ValueError(f"{location} station has invalid category")
    variants = value["variants"]
    if not isinstance(variants, list) or not variants:
        raise ValueError(f"{location} station variants must be a non-empty list")
    seen_items = set()
    for index, variant in enumerate(variants):
        variant_location = f"{location} station variant {index}"
        if not isinstance(variant, dict):
            raise ValueError(f"{variant_location} must be an object")
        _unknown_keys(variant, VARIANT_KEYS, variant_location)
        if not {"item", "rate"} <= set(variant):
            raise ValueError(f"{variant_location} requires item and rate")
        _validate_nonempty_string(variant["item"], f"{variant_location} item")
        if not RESOURCE_LOCATION.fullmatch(variant["item"]):
            raise ValueError(f"{variant_location} item must be a resource location")
        if variant["item"] in seen_items:
            raise ValueError(
                f"{location} has duplicate station variant item: {variant['item']}"
            )
        seen_items.add(variant["item"])
        rate = variant["rate"]
        if not isinstance(rate, dict):
            raise ValueError(f"{variant_location} rate must be an object")
        _unknown_keys(rate, RATE_KEYS, f"{variant_location} rate")
        if set(rate) != RATE_KEYS:
            raise ValueError(
                f"{variant_location} rate requires numerator and denominator"
            )
        numerator = rate["numerator"]
        denominator = rate["denominator"]
        if (
            isinstance(numerator, bool)
            or not isinstance(numerator, int)
            or numerator < 0
        ):
            raise ValueError(f"{variant_location} rate numerator must be non-negative")
        if numerator > JAVA_LONG_MAX:
            raise ValueError(
                f"{variant_location} rate numerator must not exceed {JAVA_LONG_MAX}"
            )
        if (
            isinstance(denominator, bool)
            or not isinstance(denominator, int)
            or denominator <= 0
        ):
            raise ValueError(f"{variant_location} rate denominator must be positive")
        if denominator > JAVA_LONG_MAX:
            raise ValueError(
                f"{variant_location} rate denominator must not exceed {JAVA_LONG_MAX}"
            )
        if value["category"] == "process" and numerator == 0:
            raise ValueError(
                f"{location} process station variants require positive rates"
            )
        if value["category"] == "instant" and numerator != 0:
            raise ValueError(
                f"{location} instant station variants require zero rates"
            )
        if "bounds" in variant:
            _validate_nonempty_string(variant["bounds"], f"{variant_location} bounds")


def _validate_verification_evidence(verification: dict):
    evidence = verification["evidence"]
    if not isinstance(evidence, dict):
        raise ValueError("verification evidence must be an object")
    if set(evidence) != set(verification["checks"]):
        raise ValueError("verification evidence keys must exactly match checks")
    gradle_tasks = set(verification["gradle_tasks"])
    for check, records in evidence.items():
        if not isinstance(records, list) or not records:
            raise ValueError(f"verification evidence for {check} must be non-empty")
        for index, record in enumerate(records):
            location = f"verification evidence {check} {index}"
            if not isinstance(record, dict):
                raise ValueError(f"{location} must be an object")
            _unknown_keys(record, VERIFICATION_EVIDENCE_KEYS, location)
            if set(record) != VERIFICATION_EVIDENCE_KEYS:
                raise ValueError(f"{location} requires task, source, and marker")
            task = record["task"]
            source = record["source"]
            marker = record["marker"]
            _validate_nonempty_string(task, f"{location} task")
            if task not in gradle_tasks:
                raise ValueError(f"{location} task is not declared in gradle_tasks")
            _validate_nonempty_string(source, f"{location} source")
            source_path = Path(source)
            if source_path.is_absolute() or ".." in source_path.parts:
                raise ValueError(f"{location} source must be a safe relative glob")
            _validate_nonempty_string(marker, f"{location} marker")


def validate_contract(
    contract: dict,
    *,
    require_complete: bool,
    source_audit: dict | None = None,
    source_artifact=None,
    source_classpath=(),
):
    if not isinstance(contract, dict):
        raise ValueError("contract must be a JSON object")
    _unknown_keys(contract, CONTRACT_TOP_KEYS, "contract")
    if contract.get("schema") != SCHEMA_VERSION:
        raise ValueError(f"unsupported contract schema: {contract.get('schema')}")
    if contract.get("kind") != "auto_storage_compat_contract":
        raise ValueError(f"invalid contract kind: {contract.get('kind')}")
    source_audit_sha256 = contract.get("source_audit_sha256")
    if not isinstance(source_audit_sha256, str) or not re.fullmatch(
        r"[0-9a-f]{64}",
        source_audit_sha256,
    ):
        raise ValueError("contract source_audit_sha256 must be a SHA-256 digest")
    source_recipe_inventory_sha256 = contract.get(
        "source_recipe_inventory_sha256"
    )
    if not isinstance(source_recipe_inventory_sha256, str) or not re.fullmatch(
        r"[0-9a-f]{64}",
        source_recipe_inventory_sha256,
    ):
        raise ValueError(
            "contract source_recipe_inventory_sha256 must be a SHA-256 digest"
        )
    source_recipe_data_sha256 = contract.get("source_recipe_data_sha256")
    if not isinstance(source_recipe_data_sha256, str) or not re.fullmatch(
        r"[0-9a-f]{64}",
        source_recipe_data_sha256,
    ):
        raise ValueError(
            "contract source_recipe_data_sha256 must be a SHA-256 digest"
        )
    if not isinstance(contract.get("families"), list):
        raise ValueError("contract families must be a list")
    target = contract.get("target")
    if not isinstance(target, dict):
        raise ValueError("contract target must be an object")
    _unknown_keys(target, TARGET_KEYS, "contract target")
    for key in ("mod_id", "display_name", "version"):
        if not isinstance(target.get(key), str) or not target[key].strip():
            raise ValueError(f"contract target requires {key}")
    if not re.fullmatch(r"[a-z0-9_-]+", target["mod_id"]):
        raise ValueError("contract target has invalid mod_id")
    _validate_contract_matrix(contract.get("matrix"), target["mod_id"])
    if "dependency" in target:
        _validate_dependency_coordinate(
            target["dependency"],
            "contract target dependency",
        )
    if "repositories" in target:
        repositories = target["repositories"]
        _validate_unique_strings(
            repositories, "contract target repositories", allow_empty=True
        )
        invalid = [
            repository
            for repository in repositories
            if not re.fullmatch(r"https://[^\s]+", repository)
        ]
        if invalid:
            raise ValueError("contract target repositories must use HTTPS URLs")
    if "runtime_dependencies" in target:
        _validate_unique_strings(
            target["runtime_dependencies"],
            "contract target runtime_dependencies",
            allow_empty=True,
        )
        for index, dependency in enumerate(target["runtime_dependencies"]):
            _validate_dependency_coordinate(
                dependency,
                f"contract target runtime_dependencies {index}",
            )
        if target.get("dependency") in target["runtime_dependencies"]:
            raise ValueError(
                "contract target runtime_dependencies must not repeat target dependency"
            )
    if "runtime_artifact_transforms" in target:
        _validate_runtime_artifact_transforms(
            target["runtime_artifact_transforms"],
            target,
            source_audit_sha256,
        )
    unresolved = []
    seen_ids = set()
    seen_classes = set()
    for index, family in enumerate(contract["families"]):
        if not isinstance(family, dict):
            raise ValueError(f"family {index} must be an object")
        _unknown_keys(family, FAMILY_KEYS, f"family {index}")
        family_id = family.get("id")
        if not isinstance(family_id, str) or not re.fullmatch(r"[a-z0-9_]+", family_id):
            raise ValueError(f"family {index} has invalid id")
        if family_id in seen_ids:
            raise ValueError(f"duplicate family id: {family_id}")
        seen_ids.add(family_id)
        family_class = family.get("class")
        _validate_nonempty_string(family_class, f"family {family_id} class")
        if family_class in seen_classes:
            raise ValueError(f"duplicate family class: {family_class}")
        seen_classes.add(family_class)
        is_transform_family = (
            isinstance(family.get("inputs"), list)
            and family["inputs"] == [{
                "role": "consume",
                "resource_kind": "item",
                "amount": 1,
                "selector": "transform.input",
            }]
        )
        recipe_type = family.get("recipe_type")
        if recipe_type is not None:
            _validate_nonempty_string(recipe_type, f"family {family_id} recipe_type")
            if not RESOURCE_LOCATION.fullmatch(recipe_type):
                raise ValueError(
                    f"family {family_id} recipe_type must be a resource location"
                )
        station = family.get("station")
        if station is not None:
            _validate_station(station, f"family {family_id}")
        _validate_terms(family.get("inputs"), f"family {family_id} inputs")
        _validate_terms(family.get("outputs"), f"family {family_id} outputs")
        _validate_costs(family.get("costs"), f"family {family_id} costs")
        _validate_unique_strings(
            family.get("risks"), f"family {family_id} risks", allow_empty=True
        )
        _validate_unique_strings(
            family.get("evidence"),
            f"family {family_id} evidence",
            allow_empty=False,
        )
        decision = family.get("decision")
        if decision is not None:
            _validate_nonempty_string(decision, f"family {family_id} decision")
        status = family.get("status")
        if status not in ("accepted", "rejected", "needs_decision"):
            raise ValueError(f"family {family_id} has invalid status: {status}")
        if status == "needs_decision":
            unresolved.append(family_id)
        elif status == "rejected":
            if not isinstance(family.get("decision"), str) or not family["decision"].strip():
                raise ValueError(f"rejected family {family_id} requires a decision")
        else:
            for key in ("station", "recipe_type", "inputs", "outputs", "costs", "decision"):
                if key == "recipe_type" and is_transform_family:
                    continue
                value = family.get(key)
                if value is None or value == "" or (key != "costs" and value == []):
                    raise ValueError(f"accepted family {family_id} requires {key}")
            outputs = family["outputs"]
            if not isinstance(outputs, list) or not any(
                isinstance(output, dict) and output.get("role") == "primary"
                for output in outputs
            ):
                raise ValueError(f"accepted family {family_id} requires one primary output")
    if _recipe_inventory_sha256(seen_classes) != source_recipe_inventory_sha256:
        raise ValueError(
            "contract recipe family inventory does not match source audit"
        )
    if source_audit is not None:
        _validate_audit(source_audit)
        if (
            require_complete
            and source_audit["scanner_format"] != SCAN_CACHE_VERSION
        ):
            raise ValueError(
                "complete contract requires a current scanner-format audit"
            )
        audit_target = source_audit["target"]
        for key in ("mod_id", "display_name", "version"):
            if target[key] != audit_target.get(key):
                raise ValueError(f"contract target {key} does not match source audit")
        if source_audit["artifact"]["sha256"] != source_audit_sha256:
            raise ValueError("contract target artifact does not match source audit")
        if source_audit["recipe_data"]["digest"] != source_recipe_data_sha256:
            raise ValueError("contract recipe data does not match source audit")
        audited_recipe_classes = {
            candidate["class"]
            for candidate in source_audit["candidates"]["recipe_classes"]
        }
        if seen_classes != audited_recipe_classes:
            raise ValueError(
                "contract families do not match audited recipe candidates"
            )
        audited_risks = _audited_risks_by_class(source_audit)
        contract_risks = {
            family["class"]: set(family["risks"])
            for family in contract["families"]
        }
        if any(
            contract_risks[class_name] != set(audited_risks.get(class_name, []))
            for class_name in audited_recipe_classes
        ):
            raise ValueError("contract family risks do not match source audit")
        if (
            source_recipe_inventory_sha256
            != _recipe_inventory_sha256(audited_recipe_classes)
        ):
            raise ValueError(
                "contract recipe family inventory does not match source audit"
            )
    if require_complete and unresolved:
        raise ValueError("contract has unresolved families: " + ", ".join(unresolved))
    if require_complete:
        if (
            contract["matrix"]["recipeInventory"]["sha256"]
            == _pending_contract_matrix(target["mod_id"])["recipeInventory"]["sha256"]
        ):
            raise ValueError(
                "complete contract must replace the pending matrix recipe inventory digest"
            )
        if not isinstance(target.get("dependency"), str) or not target["dependency"].strip():
            raise ValueError("complete contract target requires dependency")
        if not isinstance(target.get("repositories"), list):
            raise ValueError("complete contract target requires repositories")
        if not isinstance(target.get("runtime_dependencies"), list):
            raise ValueError(
                "complete contract target requires runtime_dependencies"
            )
        verification = contract.get("verification")
        if not isinstance(verification, dict):
            raise ValueError("complete contract requires verification")
        if set(verification) != VERIFICATION_KEYS:
            raise ValueError(
                "verification keys must be fixture, expected_game_tests, "
                "game_test_task, gradle_tasks, checks, and evidence"
            )
        if not isinstance(verification["fixture"], str) or not verification["fixture"]:
            raise ValueError("verification requires fixture")
        if (
            not isinstance(verification["expected_game_tests"], int)
            or isinstance(verification["expected_game_tests"], bool)
            or verification["expected_game_tests"] <= 0
        ):
            raise ValueError("verification requires positive expected_game_tests")
        if verification["expected_game_tests"] > GRADLE_INTEGER_MAX:
            raise ValueError(
                "verification expected_game_tests must not exceed 2147483647"
            )
        if not isinstance(verification["gradle_tasks"], list) or not verification["gradle_tasks"]:
            raise ValueError("verification requires gradle_tasks")
        _validate_unique_strings(
            verification["gradle_tasks"],
            "verification gradle_tasks",
            allow_empty=False,
        )
        invalid_tasks = [
            task
            for task in verification["gradle_tasks"]
            if not re.fullmatch(r"[A-Za-z][A-Za-z0-9]*", task)
        ]
        if invalid_tasks:
            raise ValueError("verification has invalid gradle task names")
        _validate_nonempty_string(
            verification["game_test_task"], "verification game_test_task"
        )
        if verification["game_test_task"] not in verification["gradle_tasks"]:
            raise ValueError("verification game_test_task must be in gradle_tasks")
        _validate_unique_strings(
            verification["checks"], "verification checks", allow_empty=False
        )
        if set(verification["checks"]) != set(REQUIRED_VERIFICATION_CHECKS):
            raise ValueError(
                "verification checks must exactly match the required checks"
            )
        _validate_verification_evidence(verification)
        if source_audit is None:
            raise ValueError("complete contract requires its source audit")
        if source_artifact is None:
            raise ValueError("complete contract requires exact source artifact")
        _validate_audit_target_artifact(
            source_audit,
            source_artifact,
            source_classpath=source_classpath,
        )
    else:
        verification = contract.get("verification")
        if not isinstance(verification, dict) or set(verification) != VERIFICATION_KEYS:
            raise ValueError("draft contract has invalid verification keys")
    return contract


def _pascal(identifier: str) -> str:
    words = re.findall(r"[A-Za-z0-9]+", identifier)
    if not words:
        if not identifier:
            raise ValueError("identifier has no Java-safe characters: ")
        encoded = "".join(f"{ord(character):04x}" for character in identifier)
        return "Encoded" + encoded
    return "".join(word[:1].upper() + word[1:].lower() for word in words)


def _generated_java_identifier(value: str) -> str:
    if not value:
        raise ValueError("generated Java identifier is empty")
    if value[0].isdigit() or value in JAVA_RESERVED_IDENTIFIERS:
        value = "_" + value
    if JAVA_MEMBER.fullmatch(value) is None:
        raise ValueError(f"invalid generated Java identifier: {value}")
    return value


def _family_java_identifier(value: str) -> str:
    if value[0].isdigit() or value in JAVA_RESERVED_IDENTIFIERS:
        value = "family$" + value
    return _generated_java_identifier(value)


def _resource_java_names(resource_id: str) -> tuple[str, str, str]:
    suffix = _generated_java_identifier(
        _pascal(resource_id.split(":", 1)[1])
    )
    normalized = re.sub(r"[^A-Za-z0-9_]", "_", resource_id)
    constant = _generated_java_identifier(normalized.upper())
    test_name = _generated_java_identifier(normalized.lower())
    return suffix, constant, test_name


def _java_segment(identifier: str) -> str:
    value = re.sub(r"[^a-z0-9]", "", identifier.lower())
    if not value or value[0].isdigit() or value in JAVA_RESERVED_IDENTIFIERS:
        raise ValueError(f"invalid Java package segment: {identifier}")
    return value


def _fixture_mods_toml(target: dict) -> str:
    mod_id = target["mod_id"]
    fixture_id = f"auto_storage_{mod_id}_fixture"
    description = _toml_basic_string(
        f"Compat Kit generated RED fixture for {target['display_name']}."
    )
    return f"""modLoader="javafml"
loaderVersion="[4,)"
license="MIT"

[[mods]]
modId="{fixture_id}"
version="1.0.0"
displayName="Auto Storage {_pascal(mod_id)} Fixture"
description={description}

[[dependencies.{fixture_id}]]
modId="neoforge"
type="required"
versionRange="[21.1,)"
ordering="NONE"
side="BOTH"

[[dependencies.{fixture_id}]]
modId="minecraft"
type="required"
versionRange="[1.21.1,1.22)"
ordering="NONE"
side="BOTH"

[[dependencies.{fixture_id}]]
modId="auto_storage"
type="required"
versionRange="[0.3,)"
ordering="AFTER"
side="BOTH"

[[dependencies.{fixture_id}]]
modId="{mod_id}"
type="required"
versionRange="[0,)"
ordering="AFTER"
side="BOTH"
"""


def _bundled_files(contract: dict, source_audit: dict) -> dict[str, bytes]:
    target = contract["target"]
    mod_id = target["mod_id"]
    package_segment = _java_segment(mod_id)
    class_prefix = _pascal(mod_id)
    source_set = f"compat{class_prefix}"
    fixture = contract["verification"]["fixture"]
    module_package = f"com.swear.autostorage.compat.{package_segment}"
    fixture_package = f"com.swear.autostorage.fixture.{package_segment}"
    module_path = module_package.replace(".", "/")
    fixture_path = fixture_package.replace(".", "/")
    descriptor = {
        "schema": 1,
        "id": f"auto_storage:{mod_id}",
        "entrypoint": f"{module_package}.{class_prefix}CompatModule",
        "requires": list(contract["matrix"]["mods"]),
        "side": "both",
        "sourceSet": source_set,
        "fixture": fixture,
        "expectedTests": contract["verification"]["expected_game_tests"],
        "dependencies": [
            target["dependency"],
            *(
                record["dependency"]
                for record in source_audit["ancestry_dependencies"]
            ),
        ],
        "runtimeDependencies": [
            target["dependency"],
            *target["runtime_dependencies"],
        ],
        "repositories": target["repositories"],
        "auditArtifact": {
            "dependency": target["dependency"],
            "sha256": contract["source_audit_sha256"],
        },
        "matrix": copy.deepcopy(contract["matrix"]),
    }
    if "runtime_artifact_transforms" in target:
        descriptor["runtimeArtifactTransforms"] = [
            {
                "dependency": dependency,
                **copy.deepcopy(transform),
            }
            for dependency, transform in sorted(
                target["runtime_artifact_transforms"].items()
            )
        ]
    module = f"""package {module_package};

import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.RecipeFamily;
import com.swear.autostorage.RecipeFamilyApi;
import com.swear.autostorage.TransformProvider;
import com.swear.autostorage.TransformProviderApi;
import com.swear.autostorage.api.AutoStorageApi;
import com.swear.autostorage.api.AutoStorageCompatContext;
import com.swear.autostorage.api.AutoStorageCompatModule;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class {class_prefix}CompatModule implements AutoStorageCompatModule {{
    private static final DeferredRegister<MachineDescriptor> MACHINES =
            MachineDescriptorApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final DeferredRegister<RecipeFamily> RECIPES =
            RecipeFamilyApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final DeferredRegister<TransformProvider> TRANSFORMS =
            TransformProviderApi.createDeferredRegister(AutoStorageApi.MOD_ID);

    @Override
    public void register(AutoStorageCompatContext context) {{
        {class_prefix}Compat.register(MACHINES, RECIPES, TRANSFORMS);
        context.register(addon -> addon
                .machineDescriptors(MACHINES)
                .recipeFamilies(RECIPES)
                .transformProviders(TRANSFORMS));
    }}
}}
"""
    accepted = [
        family["id"]
        for family in contract["families"]
        if family["status"] == "accepted"
    ]
    adapter = f"""package {module_package};

import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.RecipeFamily;
import com.swear.autostorage.TransformProvider;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class {class_prefix}Compat {{
    private {class_prefix}Compat() {{
    }}

    public static void register(
            DeferredRegister<MachineDescriptor> machines,
            DeferredRegister<RecipeFamily> recipes,
            DeferredRegister<TransformProvider> transforms
    ) {{
        throw new IllegalStateException(
                "compat-kit scaffold is intentionally RED: implement {', '.join(accepted)}");
    }}
}}
"""
    fixture_mod = f"""package {fixture_package};

import net.neoforged.fml.common.Mod;

@Mod({class_prefix}FixtureMod.MODID)
public final class {class_prefix}FixtureMod {{
    public static final String MODID = "auto_storage_{mod_id}_fixture";
}}
"""
    checks = "\n".join(
        f'            "{check}",' for check in REQUIRED_VERIFICATION_CHECKS
    ).rstrip(",")
    fixture_tests = f"""package {fixture_package};

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

@GameTestHolder({class_prefix}FixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class {class_prefix}IntegrationGameTests {{
    private static final Set<String> REQUIRED_CHECKS = Set.of(
{checks});

    private {class_prefix}IntegrationGameTests() {{
    }}

    @GameTest(template = "craftingtests.platform")
    public static void compat_kit_scaffold_remains_red(GameTestHelper helper) {{
        helper.fail("compat-kit scaffold is intentionally RED: " + REQUIRED_CHECKS);
    }}
}}
"""
    structure = base64.b64decode(
        "H4sICMY9CmoC/2JlaGF2aW9yYWx0ZXN0cy5wbGF0Zm9ybS5uYnQAjdPLCsJADAXQm0zV"
        "VpF+gH/i2rVL92OJMGhb7WTl1/uA+gDBG5hFSGYuHJg5ELDYRI87G3LqO6C+VChyulrA"
        "Y/hxKszO8WTuNr+3WqLYxtZQt6mzZogHX2fvO8M4WL4HMQ2oMN2f+uaYH7dXFcK5z+Pb"
        "rwqYZI9uz+Z7R4gdJXbGEiJLiCwhspTIUiJL/2QJYSiEoRCGQhgKYSiEoRCGQhgKYaiEo"
        "RKGShgqYaiEoRKGShgqYfhrp7TOkyd7/m3gBsmsFS9XBAAA"
    )
    return {
        f"src/compat/{mod_id}/compat-module.json": canonical_json(descriptor).encode(),
        f"src/compat/{mod_id}/java/{module_path}/{class_prefix}Compat.java": adapter.encode(),
        f"src/compat/{mod_id}/java/{module_path}/{class_prefix}CompatModule.java": module.encode(),
        f"src/{fixture}/java/{fixture_path}/{class_prefix}FixtureMod.java": fixture_mod.encode(),
        (
            f"src/{fixture}/java/{fixture_path}/"
            f"{class_prefix}IntegrationGameTests.java"
        ): fixture_tests.encode(),
        f"src/{fixture}/resources/META-INF/neoforge.mods.toml": _fixture_mods_toml(
            target
        ).encode(),
        (
            f"src/{fixture}/resources/data/auto_storage_{mod_id}_fixture/"
            "structure/craftingtests.platform.nbt"
        ): structure,
    }


def _wrapper_files() -> dict[str, bytes]:
    required = (
        "gradlew",
        "gradlew.bat",
        "gradle/wrapper/gradle-wrapper.jar",
        "gradle/wrapper/gradle-wrapper.properties",
    )
    tool_file = Path(__file__).resolve()
    roots = (tool_file.parent, tool_file.parents[2])
    for root in roots:
        if all((root / relative).is_file() for relative in required):
            return {
                relative: (root / relative).read_bytes()
                for relative in required
            }
    searched = ", ".join(str(root) for root in roots)
    raise ValueError(
        "compat-kit distribution is missing a complete wrapper template in: "
        + searched
    )


def _toml_basic_string(value: str) -> str:
    return json.dumps(value, ensure_ascii=False).replace("\u007f", "\\u007f")


def _groovy_string(value: str) -> str:
    return (
        '"'
        + value.replace("\\", "\\\\").replace("$", "\\$").replace('"', '\\"')
        + '"'
    )


def _addon_files(contract: dict, source_audit: dict) -> dict[str, bytes]:
    target = contract["target"]
    mod_id = target["mod_id"]
    package_segment = _java_segment(mod_id) + "autostorage"
    package = f"com.example.{package_segment}"
    package_path = package.replace(".", "/")
    class_prefix = _pascal(mod_id)
    addon_id = f"{mod_id}_auto_storage"
    accepted = [
        family["id"]
        for family in contract["families"]
        if family["status"] == "accepted"
    ]
    settings = f"""pluginManagement {{
    repositories {{
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }}
}}

plugins {{
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}}

rootProject.name = '{addon_id}'
"""
    properties = f"""org.gradle.jvmargs=-Xmx2G
org.gradle.daemon=true
org.gradle.caching=true

minecraft_version=1.21.1
neo_version=21.1.229
parchment_minecraft_version=1.21.1
parchment_mappings_version=2024.11.17
auto_storage_version={TOOL_VERSION}
patchouli_version=1.21.1-93-NEOFORGE
mod_id={addon_id}
mod_version=0.1.0
mod_group_id={package}
"""
    reviewed_groups = sorted({
        dependency.split(":", 1)[0]
        for dependency in [
            target["dependency"],
            *target["runtime_dependencies"],
            *(
                record["dependency"]
                for record in source_audit["ancestry_dependencies"]
            ),
        ]
    })
    reviewed_group_includes = "".join(
        f"            includeGroup({_groovy_string(group)})\n"
        for group in reviewed_groups
    )
    fallback_excluded_groups = {
        "com.swear.autostorage",
        "vazkii.patchouli",
    }
    fallback_excluded_groups.update(
        dependency.split(":", 1)[0]
        for dependency in target["runtime_dependencies"]
    )
    fallback_group_excludes = "".join(
        f"            excludeGroup({_groovy_string(group)})\n"
        for group in sorted(fallback_excluded_groups)
    )
    repository_lines = "".join(
        "    maven {\n"
        f"        url = uri({_groovy_string(repository)})\n"
        "        content {\n"
        f"{reviewed_group_includes}"
        "        }\n"
        "    }\n"
        for repository in target["repositories"]
    )
    central_declaration = (
        "    mavenCentral {\n"
        "        content {\n"
        f"{fallback_group_excludes}"
        "        }\n"
        "    }\n"
    )
    runtime_dependency_lines = "".join(
        f"    runtimeOnly({_groovy_string(dependency)}) "
        "{ transitive = false }\n"
        for dependency in target["runtime_dependencies"]
    )
    ancestry_dependency_lines = "".join(
        f"    compileOnly({_groovy_string(record['dependency'])}) "
        "{ transitive = false }\n"
        f"    compatKitAncestryArtifacts({_groovy_string(record['dependency'])}) "
        "{ transitive = false }\n"
        for record in source_audit["ancestry_dependencies"]
    )
    target_dependency = _groovy_string(target["dependency"])
    ancestry_records = "\n".join(
        "    [sha256: "
        + _groovy_string(record["sha256"])
        + f", size: {record['size']}L],"
        for record in source_audit["ancestry_classpath"]
    )
    ancestry_identity = hashlib.sha256(
        canonical_json({
            "artifacts": source_audit["ancestry_classpath"],
            "dependencies": source_audit["ancestry_dependencies"],
        }).encode()
    ).hexdigest()
    build = f"""plugins {{
    id 'java-library'
    id 'net.neoforged.moddev' version '2.0.141'
}}

version = mod_version
group = mod_group_id

configurations {{
    compatKitTargetArtifact {{
        canBeConsumed = false
        canBeResolved = true
        transitive = false
    }}
    compatKitAncestryArtifacts {{
        canBeConsumed = false
        canBeResolved = true
        transitive = true
    }}
}}

repositories {{
{repository_lines}
{central_declaration}
    maven {{
        url = uri("https://maven.blamejared.com")
        content {{
            includeGroup("vazkii.patchouli")
        }}
    }}
    ivy {{
        name = "AutoStorageReleases"
        url = uri("https://github.com/swear01/Auto_Storage/releases/download/v${{auto_storage_version}}")
        patternLayout {{
            artifact("[artifact]-[revision](-[classifier]).[ext]")
        }}
        metadataSources {{
            artifact()
        }}
        content {{
            includeGroup("com.swear.autostorage")
        }}
    }}
}}

neoForge {{
    enable {{
        version = neo_version
        disableRecompilation = true
    }}

    parchment {{
        mappingsVersion = parchment_mappings_version
        minecraftVersion = parchment_minecraft_version
    }}

    mods {{
        {addon_id} {{
            sourceSet sourceSets.main
        }}
    }}

    runs {{
        gameTestServer {{
            type = "gameTestServer"
            systemProperty "neoforge.enabledGameTestNamespaces", "{addon_id}"
        }}
    }}
}}

dependencies {{
    compileOnly("com.swear.autostorage:auto_storage:${{auto_storage_version}}:api")
    runtimeOnly("com.swear.autostorage:auto_storage:${{auto_storage_version}}")
    runtimeOnly("vazkii.patchouli:Patchouli:${{patchouli_version}}")
    compileOnly({target_dependency}) {{ transitive = false }}
    runtimeOnly({target_dependency}) {{ transitive = false }}
{runtime_dependency_lines}
{ancestry_dependency_lines}
    compatKitTargetArtifact({target_dependency})
    compatKitAncestryArtifacts({target_dependency})
}}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

def expectedCompatKitTargetSha256 = "{contract['source_audit_sha256']}"
def expectedCompatKitAncestryArtifacts = [
{ancestry_records}
]
def stagedCompatKitTargetArtifact = layout.buildDirectory.file("compat-kit/target.jar")
def stagedCompatKitAncestryArtifacts = layout.buildDirectory.dir("compat-kit/ancestry")
def compatKitProjectDir = layout.projectDirectory.asFile
def compatKitMinecraftArtifacts =
        tasks.named("createMinecraftArtifacts").get().outputs.files
def compatKitSha256 = {{ File artifact ->
    def digest = java.security.MessageDigest.getInstance("SHA-256")
    artifact.withInputStream {{ input ->
        byte[] buffer = new byte[8192]
        for (int read = input.read(buffer); read != -1; read = input.read(buffer)) {{
            digest.update(buffer, 0, read)
        }}
    }}
    digest.digest().encodeHex().toString()
}}
def compatKitNormalizeJar = {{ File artifact, File output ->
    output.parentFile.mkdirs()
    def command = [
        "python3",
        "tools/compat-kit/compat_kit.py",
        "normalize-jar",
        artifact.absolutePath,
        output.absolutePath,
    ]
    def process = new ProcessBuilder(command)
            .directory(compatKitProjectDir)
            .redirectErrorStream(true)
            .start()
    def processOutput = process.inputStream.getText("UTF-8").trim()
    def exitCode = process.waitFor()
    if (exitCode != 0) {{
        throw new GradleException(
                "Compat Kit jar normalization failed for " + artifact
                + " (exit " + exitCode + ")"
                + (processOutput ? ": " + processOutput : ""))
    }}
    output
}}
def stageCompatKitTargetArtifact = tasks.register("stageCompatKitTargetArtifact") {{
    inputs.files(configurations.compatKitTargetArtifact)
    inputs.property("expectedSha256", expectedCompatKitTargetSha256)
    outputs.file(stagedCompatKitTargetArtifact)
    doLast {{
        def artifacts = inputs.files.files.findAll {{ it.name.endsWith(".jar") }}
        if (artifacts.size() != 1) {{
            throw new GradleException(
                    "Compat Kit target verification expected one resolved jar, found ${{artifacts.size()}}")
        }}
        def artifact = artifacts.iterator().next()
        def digest = java.security.MessageDigest.getInstance("SHA-256")
        artifact.withInputStream {{ input ->
            byte[] buffer = new byte[8192]
            for (int read = input.read(buffer); read != -1; read = input.read(buffer)) {{
                digest.update(buffer, 0, read)
            }}
        }}
        def actual = digest.digest().encodeHex().toString()
        if (actual != expectedCompatKitTargetSha256) {{
            throw new GradleException(
                    "Compat Kit target SHA-256 mismatch: expected ${{expectedCompatKitTargetSha256}}, got ${{actual}}")
        }}
        def staged = stagedCompatKitTargetArtifact.get().asFile
        staged.parentFile.mkdirs()
        java.nio.file.Files.copy(
                artifact.toPath(),
                staged.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        def stagedDigest = java.security.MessageDigest.getInstance("SHA-256")
        staged.withInputStream {{ input ->
            byte[] buffer = new byte[8192]
            for (int read = input.read(buffer); read != -1; read = input.read(buffer)) {{
                stagedDigest.update(buffer, 0, read)
            }}
        }}
        def stagedActual = stagedDigest.digest().encodeHex().toString()
        if (stagedActual != expectedCompatKitTargetSha256) {{
            throw new GradleException(
                    "Compat Kit staged target SHA-256 mismatch: expected ${{expectedCompatKitTargetSha256}}, got ${{stagedActual}}")
        }}
    }}
}}
def stageCompatKitAncestryArtifacts = tasks.register("stageCompatKitAncestryArtifacts") {{
    inputs.files(
            configurations.compatKitAncestryArtifacts,
            configurations.additionalRuntimeClasspath,
            compatKitMinecraftArtifacts)
    inputs.property(
            "expectedArtifacts",
            {_groovy_string(ancestry_identity)})
    outputs.dir(stagedCompatKitAncestryArtifacts)
    doLast {{
        def expectedBySha256 = expectedCompatKitAncestryArtifacts.collectEntries {{
            [(it.sha256): it]
        }}
        def matches = [:]
        inputs.files.files.findAll {{
            it.isFile() && it.name.endsWith(".jar")
        }}.each {{ artifact ->
            def size = artifact.length()
            if (expectedCompatKitAncestryArtifacts.any {{ it.size == size }}) {{
                def sha256 = compatKitSha256(artifact)
                def expected = expectedBySha256[sha256]
                if (expected != null && expected.size == size) {{
                    matches[sha256] = artifact
                }}
            }}
        }}
        def canonicalRoot = new File(
                temporaryDir,
                "compat-kit-canonical-platform")
        canonicalRoot.deleteDir()
        def observedCanonical = []
        compatKitMinecraftArtifacts.files
                .findAll {{ it.isFile() && it.name.endsWith(".jar") }}
                .sort {{ left, right ->
                    left.absolutePath <=> right.absolutePath
                }}
                .eachWithIndex {{ artifact, index ->
                    def canonical = compatKitNormalizeJar(
                            artifact,
                            new File(canonicalRoot, index + ".jar"))
                    def size = canonical.length()
                    def sha256 = compatKitSha256(canonical)
                    observedCanonical.add(
                            sha256 + ":" + size + "=" + artifact.name)
                    def expected = expectedBySha256[sha256]
                    if (expected != null && expected.size == size) {{
                        matches[sha256] = canonical
                    }}
                }}
        def missing = expectedBySha256.keySet() - matches.keySet()
        if (!missing.isEmpty()) {{
            throw new GradleException(
                    "Compat Kit exact ancestry artifacts are unresolved: "
                    + missing.toList().sort().join(", ")
                    + "; observed ModDev canonical artifacts: "
                    + (observedCanonical
                            ? observedCanonical.sort().join(", ")
                            : "<none>"))
        }}
        def stagedRoot = stagedCompatKitAncestryArtifacts.get().asFile
        project.delete(stagedRoot)
        stagedRoot.mkdirs()
        expectedCompatKitAncestryArtifacts.each {{ expected ->
            def source = matches[expected.sha256]
            def staged = new File(stagedRoot, expected.sha256 + ".jar")
            java.nio.file.Files.copy(
                    source.toPath(),
                    staged.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            if (
                staged.length() != expected.size
                || compatKitSha256(staged) != expected.sha256
            ) {{
                throw new GradleException(
                        "Compat Kit staged ancestry artifact changed: "
                        + expected.sha256)
            }}
        }}
    }}
}}
def verifyCompatKitTargetArtifact = tasks.register("verifyCompatKitTargetArtifact") {{
    dependsOn stageCompatKitTargetArtifact
    dependsOn stageCompatKitAncestryArtifacts
}}

tasks.named("check").configure {{
    dependsOn verifyCompatKitTargetArtifact
}}
tasks.named("runGameTestServer").configure {{
    dependsOn verifyCompatKitTargetArtifact
}}
"""
    entrypoint = f"""package {package};

import com.swear.autostorage.api.AutoStorageAddon;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod({class_prefix}AutoStorageAddon.MOD_ID)
public final class {class_prefix}AutoStorageAddon {{
    public static final String MOD_ID = "{addon_id}";

    public {class_prefix}AutoStorageAddon(IEventBus modBus) {{
        AutoStorageAddon.register(MOD_ID, modBus, addon ->
                {class_prefix}Compat.register(addon));
    }}
}}
"""
    adapter = f"""package {package};

import com.swear.autostorage.api.AutoStorageAddon;

public final class {class_prefix}Compat {{
    private {class_prefix}Compat() {{
    }}

    public static void register(AutoStorageAddon.Registration addon) {{
        throw new IllegalStateException(
                "compat-kit scaffold is intentionally RED: implement {', '.join(accepted)}");
    }}
}}
"""
    checks = "\n".join(
        f'            "{check}",' for check in REQUIRED_VERIFICATION_CHECKS
    ).rstrip(",")
    fixture_tests = f"""package {package};

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

@GameTestHolder({class_prefix}AutoStorageAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class {class_prefix}IntegrationGameTests {{
    private static final Set<String> REQUIRED_CHECKS = Set.of(
{checks});

    private {class_prefix}IntegrationGameTests() {{
    }}

    @GameTest(template = "craftingtests.platform")
    public static void compat_kit_scaffold_remains_red(GameTestHelper helper) {{
        helper.fail("compat-kit scaffold is intentionally RED: " + REQUIRED_CHECKS);
    }}
}}
"""
    version_parts = TOOL_VERSION.split(".")
    compatible_minor_upper = (
        f"{version_parts[0]}.{int(version_parts[1]) + 1}"
    )
    mods_toml = f"""modLoader="javafml"
loaderVersion="[4,)"
license="MIT"

[[mods]]
modId="{addon_id}"
version="${{file.jarVersion}}"
displayName={_toml_basic_string(target['display_name'] + ' Auto Storage Integration')}

[[dependencies.{addon_id}]]
modId="auto_storage"
type="required"
versionRange="[{TOOL_VERSION},{compatible_minor_upper})"
ordering="AFTER"
side="BOTH"

[[dependencies.{addon_id}]]
modId="{mod_id}"
type="required"
versionRange="[0,)"
ordering="AFTER"
side="BOTH"
"""
    workflow = """name: CI

on:
  pull_request:
  push:
    branches: [main]

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v6
      - run: ./gradlew build --console=plain --no-daemon
      - run: ./gradlew runGameTestServer --console=plain --no-daemon
      - run: ./gradlew stageCompatKitTargetArtifact --console=plain --no-daemon
      - run: ./gradlew stageCompatKitAncestryArtifacts --console=plain --no-daemon
      - shell: bash
        run: |
          classpath_args=()
          while IFS= read -r artifact; do
            classpath_args+=(--classpath "$artifact")
          done < <(find build/compat-kit/ancestry -type f -name '*.jar' -print | sort)
          tools/compat-kit/compat-kit verify compat/contract.json --audit compat/audit.json --jar build/compat-kit/target.jar "${classpath_args[@]}" --addon .
"""
    structure = base64.b64decode(
        "H4sICMY9CmoC/2JlaGF2aW9yYWx0ZXN0cy5wbGF0Zm9ybS5uYnQAjdPLCsJADAXQm0zV"
        "VpF+gH/i2rVL92OJMGhb7WTl1/uA+gDBG5hFSGYuHJg5ELDYRI87G3LqO6C+VChyulrA"
        "Y/hxKszO8WTuNr+3WqLYxtZQt6mzZogHX2fvO8M4WL4HMQ2oMN2f+uaYH7dXFcK5z+Pb"
        "rwqYZI9uz+Z7R4gdJXbGEiJLiCwhspTIUiJL/2QJYSiEoRCGQhgKYSiEoRCGQhgKYaiEo"
        "RKGShgqYaiEoRKGShgqYfhrp7TOkyd7/m3gBsmsFS9XBAAA"
    )
    files = {
        "settings.gradle": settings.encode(),
        "gradle.properties": properties.encode(),
        "build.gradle": build.encode(),
        (
            f"src/main/java/{package_path}/{class_prefix}AutoStorageAddon.java"
        ): entrypoint.encode(),
        f"src/main/java/{package_path}/{class_prefix}Compat.java": adapter.encode(),
        (
            f"src/main/java/{package_path}/{class_prefix}IntegrationGameTests.java"
        ): fixture_tests.encode(),
        "src/main/resources/META-INF/neoforge.mods.toml": mods_toml.encode(),
        (
            f"src/main/resources/data/{addon_id}/structure/"
            "craftingtests.platform.nbt"
        ): structure,
        ".github/workflows/ci.yml": workflow.encode(),
        "compat/contract.json": canonical_json(contract).encode(),
        "compat/audit.json": canonical_json(source_audit).encode(),
        "tools/compat-kit/compat_kit.py": Path(__file__).read_bytes(),
        "tools/compat-kit/compat-kit": (
            b'#!/bin/sh\nexec python3 "$(dirname "$0")/compat_kit.py" "$@"\n'
        ),
    }
    files.update(_wrapper_files())
    return files


def _contract_sha256(contract: dict) -> str:
    return hashlib.sha256(canonical_json(contract).encode()).hexdigest()


def _manifest(files: dict[str, bytes], contract: dict) -> bytes:
    entries = {
        relative: hashlib.sha256(payload).hexdigest()
        for relative, payload in sorted(files.items())
    }
    return canonical_json(
        {
            "schema": SCHEMA_VERSION,
            "tool_version": TOOL_VERSION,
            "contract_sha256": _contract_sha256(contract),
            "files": entries,
        }
    ).encode()


def _materialize(
    root: Path,
    files: dict[str, bytes],
    manifest_path: str,
    contract: dict,
) -> list[Path]:
    complete = dict(files)
    complete[manifest_path] = _manifest(files, contract)
    root = _validate_materialization_root(root)
    for relative, payload in sorted(complete.items()):
        target = root / relative
        ancestor = root
        for part in Path(relative).parts[:-1]:
            ancestor /= part
            if ancestor.is_symlink():
                raise ValueError(
                    "generated path parent is a symlink: "
                    + ancestor.relative_to(root).as_posix()
                )
            if ancestor.exists() and not ancestor.is_dir():
                raise ValueError(
                    "generated path parent is not a directory: "
                    + ancestor.relative_to(root).as_posix()
                )
        if target.is_symlink():
            raise ValueError(f"generated path is a symlink: {relative}")
        if target.exists() and (
            not target.is_file() or target.read_bytes() != payload
        ):
            raise ValueError(f"generated file drift: {relative}")
    generated = []
    for relative, payload in sorted(complete.items()):
        target = root / relative
        if not target.exists():
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(payload)
        if relative in ("gradlew", "tools/compat-kit/compat-kit"):
            target.chmod(0o755)
        generated.append(target)
    return generated


def _validate_materialization_root(root: Path) -> Path:
    root = Path(os.path.abspath(root))
    for ancestor in root.parents:
        if ancestor.is_symlink():
            raise ValueError(
                f"generated path ancestor is a symlink: {ancestor}"
            )
    if root.is_symlink():
        raise ValueError("generated path parent is a symlink: .")
    if root.exists() and not root.is_dir():
        raise ValueError("generated path parent is not a directory: .")
    return root


def _validate_bundled_identifier_collisions(
    root: Path,
    generated_descriptor: dict,
    mod_id: str,
):
    root = _validate_materialization_root(root)
    generated_source_sets = {
        generated_descriptor["sourceSet"],
        generated_descriptor["fixture"],
    }
    if len(generated_source_sets) != 2:
        raise ValueError(
            "bundled compatibility identifier collision: source set "
            f"{generated_descriptor['sourceSet']}"
        )
    for source_set in sorted(generated_source_sets):
        if source_set in BUNDLED_FIXED_SOURCE_SETS:
            raise ValueError(
                "bundled compatibility identifier collision: source set "
                f"{source_set}"
            )
    own_descriptor = root / f"src/compat/{mod_id}/compat-module.json"
    compat_root = root
    for part in ("src", "compat"):
        compat_root /= part
        if compat_root.is_symlink():
            raise ValueError(
                "generated path parent is a symlink: "
                + compat_root.relative_to(root).as_posix()
            )
        if compat_root.exists() and not compat_root.is_dir():
            raise ValueError(
                "generated path parent is not a directory: "
                + compat_root.relative_to(root).as_posix()
            )
    module_roots = (
        sorted(compat_root.iterdir()) if compat_root.is_dir() else []
    )
    for module_root in module_roots:
        if module_root.is_symlink():
            raise ValueError(
                "existing compatibility module is a symlink: "
                + module_root.relative_to(root).as_posix()
            )
        descriptor_path = module_root / "compat-module.json"
        if not descriptor_path.exists():
            continue
        if descriptor_path.is_symlink():
            raise ValueError(
                "existing compatibility module is a symlink: "
                + descriptor_path.relative_to(root).as_posix()
            )
        if descriptor_path == own_descriptor:
            continue
        try:
            existing = json.loads(descriptor_path.read_text())
        except (OSError, json.JSONDecodeError) as error:
            raise ValueError(
                f"invalid existing compatibility descriptor: {descriptor_path}"
            ) from error
        if not isinstance(existing, dict):
            raise ValueError(
                f"invalid existing compatibility descriptor: {descriptor_path}"
            )
        for key in ("id", "entrypoint"):
            if existing.get(key) == generated_descriptor[key]:
                raise ValueError(
                    "bundled compatibility identifier collision: "
                    f"{key} {generated_descriptor[key]}"
                )
        existing_source_sets = {
            existing.get("sourceSet"),
            existing.get("fixture"),
        }
        for source_set in sorted(generated_source_sets):
            if source_set in existing_source_sets:
                raise ValueError(
                    "bundled compatibility identifier collision: source set "
                    f"{source_set}"
                )


def scaffold_bundled(
    contract: dict,
    root,
    *,
    source_audit: dict,
    source_artifact,
    source_classpath=(),
) -> list[Path]:
    validate_contract(
        contract,
        require_complete=True,
        source_audit=source_audit,
        source_artifact=source_artifact,
        source_classpath=source_classpath,
    )
    _validate_bundled_verification(contract)
    mod_id = contract["target"]["mod_id"]
    root = Path(root)
    files = _bundled_files(contract, source_audit)
    descriptor_path = f"src/compat/{mod_id}/compat-module.json"
    descriptor = json.loads(files[descriptor_path])
    _validate_bundled_identifier_collisions(
        root,
        descriptor,
        mod_id,
    )
    return _materialize(
        root,
        files,
        f"src/compat/{mod_id}/.compat-kit-manifest.json",
        contract,
    )


def _validate_bundled_verification(contract: dict):
    verification = contract["verification"]
    fixture = verification["fixture"]
    if not re.fullmatch(r"[a-z][A-Za-z0-9]*Fixture", fixture):
        raise ValueError(
            "bundled fixture must be a Java-safe identifier ending in Fixture"
        )
    fixture_name = fixture.removesuffix("Fixture")
    expected_task = (
        f"run{fixture_name[0].upper()}{fixture_name[1:]}GameTestServer"
    )
    if verification["game_test_task"] != expected_task:
        raise ValueError("bundled game_test_task must match fixture")


def _validate_addon_verification(contract: dict):
    verification = contract["verification"]
    if contract["target"].get("runtime_artifact_transforms"):
        raise ValueError(
            "runtime_artifact_transforms are supported only by bundled descriptor fixtures"
        )
    if (
        verification["fixture"] != "main"
        or verification["game_test_task"] != "runGameTestServer"
        or verification["gradle_tasks"] != ["build", "runGameTestServer"]
    ):
        raise ValueError(
            "addon verification tasks must be build and runGameTestServer "
            "with fixture main"
        )


def scaffold_addon(
    contract: dict,
    output,
    *,
    source_audit: dict,
    source_artifact,
    source_classpath=(),
) -> list[Path]:
    validate_contract(
        contract,
        require_complete=True,
        source_audit=source_audit,
        source_artifact=source_artifact,
        source_classpath=source_classpath,
    )
    _validate_addon_verification(contract)
    return _materialize(
        Path(output),
        _addon_files(contract, source_audit),
        ".compat-kit-manifest.json",
        contract,
    )


def _load_and_verify_manifest(
    root: Path,
    manifest_path: Path,
    contract: dict,
    verification_files: dict[str, bytes],
) -> str:
    if not manifest_path.is_file():
        raise ValueError(f"missing compat-kit manifest: {manifest_path}")
    try:
        manifest = json.loads(manifest_path.read_text())
    except json.JSONDecodeError as error:
        raise ValueError(f"invalid compat-kit manifest: {error}") from error
    if set(manifest) != {
        "schema",
        "tool_version",
        "contract_sha256",
        "files",
    }:
        raise ValueError("invalid compat-kit manifest keys")
    if manifest["schema"] != SCHEMA_VERSION:
        raise ValueError(f"unsupported compat-kit manifest schema: {manifest['schema']}")
    if not isinstance(manifest["files"], dict) or not manifest["files"]:
        raise ValueError("compat-kit manifest has no files")
    if manifest["contract_sha256"] != _contract_sha256(contract):
        raise ValueError(
            "compat-kit contract drift: scaffold was generated from a different contract"
        )
    for relative, payload in verification_files.items():
        manifest_sha = manifest["files"].get(relative)
        if not isinstance(manifest_sha, str) or not re.fullmatch(
            r"[0-9a-f]{64}",
            manifest_sha,
        ):
            raise ValueError(
                f"compat-kit manifest is missing verification file: {relative}"
            )
        generated_sha = hashlib.sha256(payload).hexdigest()
        target = root / relative
        if (
            manifest_sha != generated_sha
            or not target.is_file()
            or _sha256_file(target) != generated_sha
        ):
            raise ValueError(
                f"generated verification file drift: {relative}"
            )
    return _sha256_file(manifest_path)


def _default_command_runner(command, cwd):
    return subprocess.run(
        command,
        cwd=cwd,
        capture_output=True,
        check=False,
        text=True,
        timeout=1_800,
    )


def _clear_game_test_world(root: Path):
    root = Path(os.path.abspath(root))
    for path in (*root.parents, root):
        if path.is_symlink():
            raise ValueError(
                f"GameTest world path has symlinked ancestor: {path}"
            )
    resolved_root = root.resolve()
    run = root / "run"
    world = run / "world"
    for path in (run, world):
        if path.is_symlink():
            raise ValueError(
                f"GameTest world path has symlinked ancestor: {path}"
            )
    resolved_world = world.resolve()
    if resolved_world != resolved_root and resolved_root not in resolved_world.parents:
        raise ValueError(
            f"GameTest world path escapes verification root: {world}"
        )
    if world.exists():
        if not world.is_dir():
            raise ValueError(f"GameTest world path is not a directory: {world}")
        shutil.rmtree(world)


def _source_text(root: Path) -> str:
    sources = sorted(root.rglob("*.java"))
    if len(sources) > MAX_SOURCE_FILES:
        raise ValueError(f"verification root exceeds {MAX_SOURCE_FILES} Java files")
    return "\n".join(path.read_text() for path in sources)


def _java_block_end(text: str, opening: int) -> int:
    depth = 0
    index = opening
    state = "code"
    while index < len(text):
        if state == "code":
            if text.startswith('"""', index):
                state = "text"
                index += 3
                continue
            if text.startswith("//", index):
                state = "line"
                index += 2
                continue
            if text.startswith("/*", index):
                state = "block"
                index += 2
                continue
            character = text[index]
            if character == '"':
                state = "string"
            elif character == "'":
                state = "char"
            elif character == "{":
                depth += 1
            elif character == "}":
                depth -= 1
                if depth == 0:
                    return index
        elif state == "line":
            if text[index] == "\n":
                state = "code"
        elif state == "block":
            if text.startswith("*/", index):
                state = "code"
                index += 2
                continue
        elif state == "text":
            if text[index] == "\\":
                index += 2
                continue
            if text.startswith('"""', index):
                state = "code"
                index += 3
                continue
        elif text[index] == "\\":
            index += 2
            continue
        elif state == "string" and text[index] == '"':
            state = "code"
        elif state == "char" and text[index] == "'":
            state = "code"
        index += 1
    raise ValueError("unterminated @GameTest method body")


def _java_code_mask(text: str) -> str:
    masked = list(text)
    index = 0
    state = "code"
    while index < len(text):
        if state == "code":
            if text.startswith('"""', index):
                masked[index:index + 3] = "   "
                state = "text"
                index += 3
                continue
            if text.startswith("//", index):
                masked[index:index + 2] = "  "
                state = "line"
                index += 2
                continue
            if text.startswith("/*", index):
                masked[index:index + 2] = "  "
                state = "block"
                index += 2
                continue
            if text[index] == '"':
                masked[index] = " "
                state = "string"
            elif text[index] == "'":
                masked[index] = " "
                state = "char"
        elif state == "line":
            if text[index] == "\n":
                state = "code"
            else:
                masked[index] = " "
        elif state == "block":
            if text.startswith("*/", index):
                masked[index:index + 2] = "  "
                state = "code"
                index += 2
                continue
            if text[index] != "\n":
                masked[index] = " "
        elif state == "text":
            if text[index] == "\\":
                masked[index] = " "
                if index + 1 < len(text):
                    if text[index + 1] != "\n":
                        masked[index + 1] = " "
                    index += 2
                    continue
            if text.startswith('"""', index):
                masked[index:index + 3] = "   "
                state = "code"
                index += 3
                continue
            if text[index] != "\n":
                masked[index] = " "
        elif text[index] == "\\":
            masked[index] = " "
            if index + 1 < len(text):
                if text[index + 1] != "\n":
                    masked[index + 1] = " "
                index += 2
                continue
        elif state == "string" and text[index] == '"':
            masked[index] = " "
            state = "code"
        elif state == "char" and text[index] == "'":
            masked[index] = " "
            state = "code"
        else:
            if text[index] != "\n":
                masked[index] = " "
        index += 1
    return "".join(masked)


def _java_without_comments(text: str) -> str:
    masked = list(text)
    index = 0
    state = "code"
    while index < len(text):
        if state == "code":
            if text.startswith('"""', index):
                state = "text"
                index += 3
                continue
            if text.startswith("//", index):
                masked[index:index + 2] = "  "
                state = "line"
                index += 2
                continue
            if text.startswith("/*", index):
                masked[index:index + 2] = "  "
                state = "block"
                index += 2
                continue
            if text[index] == '"':
                state = "string"
            elif text[index] == "'":
                state = "char"
        elif state == "line":
            if text[index] == "\n":
                state = "code"
            else:
                masked[index] = " "
        elif state == "block":
            if text.startswith("*/", index):
                masked[index:index + 2] = "  "
                state = "code"
                index += 2
                continue
            if text[index] != "\n":
                masked[index] = " "
        elif state == "text":
            if text[index] == "\\":
                index += 2
                continue
            if text.startswith('"""', index):
                state = "code"
                index += 3
                continue
        elif text[index] == "\\":
            index += 2
            continue
        elif state == "string" and text[index] == '"':
            state = "code"
        elif state == "char" and text[index] == "'":
            state = "code"
        index += 1
    return "".join(masked)


def _has_eligible_java_unicode_escape(text: str) -> bool:
    contiguous_backslashes = 0
    index = 0
    while index < len(text):
        if text[index] != "\\":
            contiguous_backslashes = 0
            index += 1
            continue
        eligible = contiguous_backslashes % 2 == 0
        if eligible and re.match(r"\\u+[0-9a-fA-F]{4}", text[index:]):
            return True
        contiguous_backslashes += 1
        index += 1
    return False


def _game_test_method_opening(code: str, annotation_end: int) -> int:
    parentheses = 1
    for index in range(annotation_end, len(code)):
        character = code[index]
        if character == "(":
            parentheses += 1
        elif character == ")":
            parentheses -= 1
            if parentheses < 0:
                raise ValueError("invalid @GameTest method declaration")
        elif character == "{" and parentheses == 0:
            return index
        elif character == ";" and parentheses == 0:
            raise ValueError("@GameTest annotation has no method body")
    raise ValueError("@GameTest annotation has no method body")


def _game_test_methods(text: str) -> list[dict]:
    code = _java_code_mask(text)
    methods = []
    for annotation in re.finditer(
        r"@(?:[A-Za-z_$][A-Za-z0-9_$]*\.)*GameTest\s*\(",
        code,
    ):
        opening = _game_test_method_opening(code, annotation.end())
        closing = _java_block_end(text, opening)
        methods.append({
            "annotation_start": annotation.start(),
            "body": text[opening + 1:closing],
        })
    return methods


def _game_test_blocks(text: str) -> list[str]:
    return [method["body"] for method in _game_test_methods(text)]


def _bundled_game_test_namespace(root: Path, fixture: str) -> str:
    special_namespaces = {
        "main": "auto_storage",
        "recipeAddonFixture": "auto_storage_recipe_fixture",
        "pneumaticCraftFixture": "auto_storage_pneumaticcraft_fixture",
        "compatibilityMatrixFixture": "auto_storage_compatibility_matrix_fixture",
    }
    if fixture in special_namespaces:
        return special_namespaces[fixture]
    descriptors = sorted(root.glob("src/compat/*/compat-module.json"))
    if len(descriptors) > MAX_SOURCE_FILES:
        raise ValueError(
            f"verification root exceeds {MAX_SOURCE_FILES} compatibility modules"
        )
    matching_directories = []
    for descriptor_path in descriptors:
        try:
            descriptor = json.loads(descriptor_path.read_text())
        except (OSError, UnicodeError, json.JSONDecodeError) as error:
            raise ValueError(
                f"invalid compatibility module descriptor: {descriptor_path}"
            ) from error
        if descriptor.get("fixture") == fixture:
            matching_directories.append(descriptor_path.parent.name)
    if len(matching_directories) != 1:
        raise ValueError(
            "GameTest task fixture must map to exactly one compatibility module: "
            f"{fixture}"
        )
    return f"auto_storage_{matching_directories[0]}_fixture"


def _game_test_task_context(
    contract: dict,
    root: Path,
    mode: str,
    task: str,
) -> tuple[Path, str] | None:
    if not re.fullmatch(r"run(?:[A-Za-z0-9]+)?GameTestServer", task):
        return None
    if mode == "addon":
        return (
            root / "src/main/java",
            f"{contract['target']['mod_id']}_auto_storage",
        )
    if task == "runGameTestServer":
        fixture = "main"
    elif task == contract["verification"]["game_test_task"]:
        fixture = contract["verification"]["fixture"]
    else:
        task_name = task.removeprefix("run").removesuffix("GameTestServer")
        fixture = task_name[0].lower() + task_name[1:] + "Fixture"
    source_root = (
        root / "src/main/java"
        if fixture == "main"
        else root / f"src/{fixture}/java"
    )
    return source_root, _bundled_game_test_namespace(root, fixture)


def _java_string_constant_expressions(
    source_roots: tuple[Path, ...],
) -> dict[tuple[str, str], str]:
    sources = sorted({
        path
        for source_root in source_roots
        for path in source_root.rglob("*.java")
    })
    if len(sources) > MAX_SOURCE_FILES:
        raise ValueError(f"verification root exceeds {MAX_SOURCE_FILES} Java files")
    expressions = _JavaStringConstantExpressions()
    for path in sources:
        text = _java_without_comments(path.read_text())
        class_spans = _java_class_spans(text)
        context = _java_compilation_context(text)
        for match in re.finditer(
            r"\b(?:public\s+|protected\s+|private\s+)?"
            r"(?:static\s+final|final\s+static)\s+String\s+"
            r"([A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*"
            r"(\"(?:\\.|[^\"\\])*\"|"
            r"(?:[A-Za-z_$][A-Za-z0-9_$]*\.)+"
            r"[A-Za-z_$][A-Za-z0-9_$]*)\s*;",
            text,
        ):
            owners = [
                span
                for span in class_spans
                if span["opening"] < match.start() < span["closing"]
            ]
            if not owners:
                continue
            owner = min(
                owners,
                key=lambda span: span["closing"] - span["opening"],
            )
            key = (owner["qualified_name"], match.group(1))
            expression = match.group(2)
            previous = expressions.get(key)
            if previous is not None and previous != expression:
                raise ValueError(
                    "ambiguous Java string constant for GameTest holder namespace: "
                    f"{owner['qualified_name']}.{match.group(1)}"
                )
            expressions[key] = expression
            expressions.contexts[key] = (
                context,
                owner["qualified_name"],
            )

    return expressions


class _JavaStringConstantExpressions(dict):
    def __init__(self):
        super().__init__()
        self.contexts = {}


def _java_compilation_context(
    text: str,
) -> tuple[str, dict[str, str], dict[str, tuple[str, str]]]:
    code = _java_code_mask(text)
    package_match = re.search(
        r"\bpackage\s+([A-Za-z_$][A-Za-z0-9_$]*"
        r"(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*)\s*;",
        code,
    )
    package_name = package_match.group(1) if package_match else ""
    imports = {}
    for match in re.finditer(
        r"\bimport\s+(?!static\b)"
        r"([A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)+)\s*;",
        code,
    ):
        qualified_name = match.group(1)
        simple_name = qualified_name.rsplit(".", 1)[1]
        previous = imports.get(simple_name)
        if previous is not None and previous != qualified_name:
            raise ValueError(
                "ambiguous Java import for GameTest holder namespace: "
                + simple_name
            )
        imports[simple_name] = qualified_name
    static_imports = {}
    for match in re.finditer(
        r"\bimport\s+static\s+"
        r"([A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)+)"
        r"\.([A-Za-z_$][A-Za-z0-9_$]*)\s*;",
        code,
    ):
        owner = match.group(1)
        member = match.group(2)
        imported = (owner, member)
        previous = static_imports.get(member)
        if previous is not None and previous != imported:
            raise ValueError(
                "ambiguous Java static import for GameTest holder namespace: "
                + member
            )
        static_imports[member] = imported
    return package_name, imports, static_imports


def _qualified_java_constant_key(
    key: tuple[str, str],
    expressions: dict[tuple[str, str], str],
    context: tuple[str, dict[str, str], dict[str, tuple[str, str]]],
    lexical_owner: str | None = None,
) -> tuple[str, str]:
    owner, member = key
    package_name, imports, _ = context
    owner_parts = owner.split(".")
    candidates = []

    def add(candidate: str) -> None:
        if candidate not in candidates:
            candidates.append(candidate)

    if "." in owner:
        add(owner)
    scope = lexical_owner
    while scope is not None and scope != package_name:
        add(f"{scope}.{owner}")
        scope = scope.rsplit(".", 1)[0] if "." in scope else None
    imported = imports.get(owner_parts[0])
    if imported is not None:
        add(".".join([imported, *owner_parts[1:]]))
    if package_name:
        add(f"{package_name}.{owner}")
    else:
        add(owner)
    for candidate in candidates:
        candidate_key = (candidate, member)
        if candidate_key in expressions:
            return candidate_key
    suffix = "." + owner
    matches = [
        candidate
        for candidate in expressions
        if candidate[1] == member
        and (
            candidate[0] == owner
            or candidate[0].endswith(suffix)
        )
    ]
    if len(matches) != 1:
        label = "ambiguous" if matches else "unresolved"
        raise ValueError(
            f"{label} Java string constant for GameTest holder namespace: "
            f"{owner}.{member}"
        )
    return matches[0]


def _resolve_java_string_constant(
    key: tuple[str, str],
    expressions: dict[tuple[str, str], str],
    context: tuple[
        str,
        dict[str, str],
        dict[str, tuple[str, str]],
    ] | None = None,
    resolving: set[tuple[str, str]] | None = None,
    lexical_owner: str | None = None,
) -> str:
    resolving = set() if resolving is None else resolving
    context = ("", {}, {}) if context is None else context
    qualified_key = _qualified_java_constant_key(
        key,
        expressions,
        context,
        lexical_owner,
    )
    if qualified_key in resolving:
        raise ValueError(
            "unresolved Java string constant for GameTest holder namespace: "
            f"{qualified_key[0]}.{qualified_key[1]}"
        )
    expression = expressions[qualified_key]
    if expression.startswith('"'):
        try:
            return json.loads(expression)
        except json.JSONDecodeError as error:
            raise ValueError(
                "invalid Java string constant for GameTest holder namespace: "
                f"{qualified_key[0]}.{qualified_key[1]}"
            ) from error
    owner, member = expression.rsplit(".", 1)
    resolving.add(qualified_key)
    nested_context, nested_lexical_owner = getattr(
        expressions,
        "contexts",
        {},
    ).get(qualified_key, (("", {}), None))
    value = _resolve_java_string_constant(
        (owner, member),
        expressions,
        nested_context,
        resolving=resolving,
        lexical_owner=nested_lexical_owner,
    )
    resolving.remove(qualified_key)
    return value


def _game_test_holder_annotations(
    text: str,
    constant_expressions: dict[tuple[str, str], str],
) -> list[dict]:
    code = _java_code_mask(text)
    uncommented = _java_without_comments(text)
    context = _java_compilation_context(text)
    class_spans = _java_class_spans(text)
    annotations = []
    for annotation in re.finditer(
        r"@(?:[A-Za-z_$][A-Za-z0-9_$]*\.)*GameTestHolder\s*\(",
        code,
    ):
        opening = code.find("(", annotation.start())
        depth = 1
        closing = None
        for index in range(opening + 1, len(code)):
            if code[index] == "(":
                depth += 1
            elif code[index] == ")":
                depth -= 1
                if depth == 0:
                    closing = index
                    break
        if closing is None:
            raise ValueError("invalid @GameTestHolder annotation")
        expression = uncommented[opening + 1:closing].strip()
        if re.fullmatch(r'"(?:\\.|[^"\\])*"', expression):
            try:
                namespace = json.loads(expression)
            except json.JSONDecodeError as error:
                raise ValueError(
                    "invalid GameTest holder namespace string"
                ) from error
        else:
            constant = re.fullmatch(
                r"((?:[A-Za-z_$][A-Za-z0-9_$]*\.)*"
                r"[A-Za-z_$][A-Za-z0-9_$]*)\."
                r"([A-Za-z_$][A-Za-z0-9_$]*)",
                expression,
            )
            if constant is None:
                static_constant = context[2].get(expression)
                if static_constant is None:
                    raise ValueError(
                        f"unresolved GameTest holder namespace: {expression}"
                    )
                constant_key = static_constant
            else:
                constant_key = (constant.group(1), constant.group(2))
            annotated_classes = [
                span
                for span in class_spans
                if (
                    span["declaration_boundary"]
                    <= annotation.start()
                    < span["declaration_start"]
                )
            ]
            annotated_class = (
                min(
                    annotated_classes,
                    key=lambda span: span["declaration_start"] - annotation.start(),
                )
                if annotated_classes
                else None
            )
            enclosing_classes = [] if annotated_class is None else [
                span
                for span in class_spans
                if (
                    span["opening"] < annotated_class["opening"]
                    and annotated_class["closing"] < span["closing"]
                )
            ]
            lexical_owner = (
                min(
                    enclosing_classes,
                    key=lambda span: span["closing"] - span["opening"],
                )["qualified_name"]
                if enclosing_classes
                else None
            )
            namespace = _resolve_java_string_constant(
                constant_key,
                constant_expressions,
                context,
                lexical_owner=lexical_owner,
            )
        annotations.append({
            "start": annotation.start(),
            "end": closing + 1,
            "namespace": namespace,
        })
    return annotations


def _game_test_holder_namespaces(
    text: str,
    constant_expressions: dict[tuple[str, str], str],
) -> list[str]:
    return [
        annotation["namespace"]
        for annotation in _game_test_holder_annotations(
            text,
            constant_expressions,
        )
    ]


def _java_class_spans(text: str) -> list[dict]:
    code = _java_code_mask(text)
    spans = []
    for declaration in re.finditer(
        r"\b(?:class|interface|enum|record)\s+"
        r"(?P<name>[A-Za-z_$][A-Za-z0-9_$]*)\b[^{};]*\{",
        code,
    ):
        opening = code.rfind("{", declaration.start(), declaration.end())
        closing = _java_block_end(text, opening)
        declaration_boundary = max(
            code.rfind(";", 0, declaration.start()),
            code.rfind("{", 0, declaration.start()),
            code.rfind("}", 0, declaration.start()),
        ) + 1
        spans.append({
            "name": declaration.group("name"),
            "declaration_start": declaration.start(),
            "declaration_boundary": declaration_boundary,
            "opening": opening,
            "closing": closing,
        })
    package_name, _, _ = _java_compilation_context(text)
    for span in sorted(spans, key=lambda value: value["opening"]):
        parents = [
            candidate
            for candidate in spans
            if (
                candidate["opening"] < span["opening"]
                and span["closing"] < candidate["closing"]
            )
        ]
        parent = (
            min(
                parents,
                key=lambda value: value["closing"] - value["opening"],
            )
            if parents
            else None
        )
        if parent is not None:
            span["qualified_name"] = (
                f"{parent['qualified_name']}.{span['name']}"
            )
        elif package_name:
            span["qualified_name"] = f"{package_name}.{span['name']}"
        else:
            span["qualified_name"] = span["name"]
    return spans


def _validate_game_test_holder_namespace(
    path: Path,
    text: str,
    expected_namespace: str,
    constant_expressions: dict[tuple[str, str], str],
    methods: list[dict] | None = None,
):
    methods = _game_test_methods(text) if methods is None else methods
    holders = _game_test_holder_annotations(text, constant_expressions)
    class_spans = _java_class_spans(text)
    for method in methods:
        owners = [
            span
            for span in class_spans
            if span["opening"] < method["annotation_start"] < span["closing"]
        ]
        owner = (
            min(owners, key=lambda span: span["closing"] - span["opening"])
            if owners
            else None
        )
        namespaces = [] if owner is None else [
            holder["namespace"]
            for holder in holders
            if (
                owner["declaration_boundary"]
                <= holder["start"]
                < owner["declaration_start"]
            )
        ]
        if len(namespaces) != 1 or namespaces[0] != expected_namespace:
            found = ", ".join(namespaces) if namespaces else "none"
            raise ValueError(
                "GameTest holder namespace does not match declared task: "
                f"{path} expected {expected_namespace}, found {found}"
            )


def _verification_evidence(
    contract: dict,
    root: Path,
    mode: str,
) -> dict[str, list[str]]:
    resolved = {}
    java_constant_expressions_by_roots = {}
    for check, records in contract["verification"]["evidence"].items():
        resolved_records = []
        for record in records:
            matches = sorted(
                path
                for path in root.glob(record["source"])
                if path.is_file()
            )
            if not matches:
                raise ValueError(
                    f"verification evidence source matched no files: {record['source']}"
                )
            task = record["task"]
            task_context = _game_test_task_context(
                contract,
                root,
                mode,
                task,
            )
            if task_context is not None:
                task_source_root, expected_namespace = task_context
                resolved_root = root.resolve()
                resolved_task_source = task_source_root.resolve()
                if (
                    resolved_task_source != resolved_root
                    and resolved_root not in resolved_task_source.parents
                ):
                    raise ValueError(
                        "verification evidence source is outside task source set: "
                        f"{record['source']}"
                    )
                task_matches = [
                    path
                    for path in matches
                    if (
                        path.resolve() == resolved_task_source
                        or resolved_task_source in path.resolve().parents
                    )
                ]
                if not task_matches:
                    raise ValueError(
                        "verification evidence source is outside task source set: "
                        f"{record['source']}"
                    )
                matches = task_matches
            if len(matches) > MAX_SOURCE_FILES:
                raise ValueError(
                    f"verification evidence source exceeds {MAX_SOURCE_FILES} files: "
                    f"{record['source']}"
                )
            matching_texts = [path.read_text() for path in matches]
            for path, text in zip(matches, matching_texts):
                if _has_eligible_java_unicode_escape(text):
                    raise ValueError(
                        "Unicode escapes are unsupported in verification evidence: "
                        f"{path}"
                    )
            if not any(record["marker"] in text for text in matching_texts):
                raise ValueError(
                    f"verification evidence marker not found for {check}: "
                    f"{record['marker']}"
                )
            if task_context is not None:
                marker_sources = []
                for path, text in zip(matches, matching_texts):
                    marker_methods = [
                        method
                        for method in _game_test_methods(text)
                        if record["marker"]
                        in _java_without_comments(method["body"])
                    ]
                    if marker_methods:
                        marker_sources.append((path, text, marker_methods))
                if not marker_sources:
                    raise ValueError(
                        "evidence marker is not inside an @GameTest method for "
                        f"{check}: {record['marker']}"
                    )
                constant_roots = (task_source_root,)
                if (
                    mode == "bundled"
                    and task_source_root == root / "src/main/java"
                ):
                    constant_roots += (root / "src/api/java",)
                constant_root_key = tuple(
                    source_root.resolve() for source_root in constant_roots
                )
                if constant_root_key not in java_constant_expressions_by_roots:
                    java_constant_expressions_by_roots[constant_root_key] = (
                        _java_string_constant_expressions(constant_roots)
                    )
                java_constant_expressions = (
                    java_constant_expressions_by_roots[constant_root_key]
                )
                for path, text, marker_methods in marker_sources:
                    _validate_game_test_holder_namespace(
                        path,
                        text,
                        expected_namespace,
                        java_constant_expressions,
                        marker_methods,
                    )
            resolved_records.append(
                f"{task}:{record['source']}#{record['marker']}"
            )
        resolved[check] = resolved_records
    return resolved


def _game_test_count(root: Path) -> int:
    sources = sorted(root.rglob("*.java"))
    if len(sources) > MAX_SOURCE_FILES:
        raise ValueError(f"verification root exceeds {MAX_SOURCE_FILES} Java files")
    return sum(len(_game_test_blocks(path.read_text())) for path in sources)


def verify_contract(
    contract: dict,
    *,
    source_audit: dict,
    source_artifact,
    source_classpath=(),
    bundled_root=None,
    addon_root=None,
    command_runner=None,
) -> dict:
    validate_contract(
        contract,
        require_complete=True,
        source_audit=source_audit,
        source_artifact=source_artifact,
        source_classpath=source_classpath,
    )
    if (bundled_root is None) == (addon_root is None):
        raise ValueError("verify requires exactly one of bundled_root or addon_root")
    mode = "bundled" if bundled_root is not None else "addon"
    if mode == "addon":
        _validate_addon_verification(contract)
    else:
        _validate_bundled_verification(contract)
    root = Path(bundled_root if bundled_root is not None else addon_root)
    if not root.is_dir():
        raise ValueError(f"verification root does not exist: {root}")
    mod_id = contract["target"]["mod_id"]
    manifest_path = (
        root / f"src/compat/{mod_id}/.compat-kit-manifest.json"
        if mode == "bundled"
        else root / ".compat-kit-manifest.json"
    )
    generated_files = (
        _bundled_files(contract, source_audit)
        if mode == "bundled"
        else _addon_files(contract, source_audit)
    )
    verification_paths = (
        (f"src/compat/{mod_id}/compat-module.json",)
        if mode == "bundled"
        else (
            "build.gradle",
            "settings.gradle",
            "gradle.properties",
            "gradlew",
            "gradlew.bat",
            "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties",
        )
    )
    verification_files = {
        relative: generated_files[relative]
        for relative in verification_paths
    }
    manifest_sha = _load_and_verify_manifest(
        root,
        manifest_path,
        contract,
        verification_files,
    )
    sources = _source_text(root / "src")
    if "compat-kit scaffold is intentionally RED" in sources:
        raise ValueError(
            "compat-kit scaffold is intentionally RED; implement the adapter and tests"
        )
    if mode == "addon":
        forbidden = (
            "com.swear.autostorage.StorageCoreBlockEntity",
            "com.swear.autostorage.CompatibilityModuleLoader",
            "com.swear.autostorage.internal.",
            "com.swear.autostorage.compat.",
        )
        found = [name for name in forbidden if name in sources]
        if found:
            raise ValueError("addon has forbidden implementation link: " + ", ".join(found))
        build = root / "build.gradle"
        if not build.is_file():
            raise ValueError("addon is missing build.gradle")
        build_text = build.read_text()
        if ":api" not in build_text or "compileOnly" not in build_text:
            raise ValueError("addon build does not compile against the API artifact")
        commands = [
            ["./gradlew", task, "--console=plain", "--no-daemon"]
            for task in contract["verification"]["gradle_tasks"]
        ]
        fixture_root = root / "src/main/java"
        game_test_task = contract["verification"]["game_test_task"]
    else:
        fixture = contract["verification"]["fixture"]
        fixture_root = root / f"src/{fixture}/java"
        fixture_text = _source_text(fixture_root) if fixture_root.is_dir() else ""
        if "@GameTest" not in fixture_text:
            raise ValueError(f"bundled fixture {fixture} has no GameTests")
        commands = [
            ["./gradlew", task, "--console=plain", "--no-daemon"]
            for task in contract["verification"]["gradle_tasks"]
        ]
        game_test_task = contract["verification"]["game_test_task"]
    expected_game_tests = contract["verification"]["expected_game_tests"]
    source_game_tests = _game_test_count(fixture_root)
    if source_game_tests != expected_game_tests:
        raise ValueError(
            f"verification expected {expected_game_tests} GameTests, "
            f"but source declares {source_game_tests}"
        )
    evidence = _verification_evidence(contract, root, mode)
    runner = command_runner or _default_command_runner
    command_reports = []
    game_test_output = None
    for command in commands:
        if mode == "bundled" or (len(command) > 1 and command[1].startswith("run")):
            _clear_game_test_world(root)
        result = runner(command, root)
        stdout = result.stdout or ""
        stderr = result.stderr or ""
        command_reports.append(
            {
                "command": command,
                "exit_code": result.returncode,
                "stdout_sha256": hashlib.sha256(stdout.encode()).hexdigest(),
                "stderr_sha256": hashlib.sha256(stderr.encode()).hexdigest(),
            }
        )
        if result.returncode != 0:
            detail = stderr.strip() or stdout.strip()
            raise RuntimeError(
                f"verification command failed ({result.returncode}): "
                f"{' '.join(command)}\n{detail[-4000:]}"
            )
        if command[1] == game_test_task:
            game_test_output = stdout + "\n" + stderr
    if game_test_output is None:
        raise ValueError(f"verification did not run game_test_task: {game_test_task}")
    passed_matches = re.findall(
        r"All\s+(\d+)\s+required tests passed\s+:\)",
        game_test_output,
    )
    if len(passed_matches) > 1:
        raise ValueError(
            "verification found conflicting GameTest success summaries: "
            + ", ".join(passed_matches)
        )
    if not passed_matches or int(passed_matches[0]) != expected_game_tests:
        actual = passed_matches[0] if passed_matches else "missing"
        raise ValueError(
            f"verification expected {expected_game_tests} GameTests to pass, "
            f"but command reported {actual}"
        )
    return {
        "schema": SCHEMA_VERSION,
        "kind": "auto_storage_compat_report",
        "tool_version": TOOL_VERSION,
        "target": contract["target"],
        "mode": mode,
        "manifest_sha256": manifest_sha,
        "checks": [
            {
                "id": check,
                "status": "passed",
                "evidence": evidence[check],
            }
            for check in REQUIRED_VERIFICATION_CHECKS
        ],
        "commands": command_reports,
        "status": "passed",
    }


def _published_addon_example_files(root: Path) -> dict[str, bytes]:
    files = {}
    for relative in PUBLISHED_ADDON_EXAMPLE_FILES:
        source = root / relative
        if not source.is_file() or source.is_symlink():
            raise ValueError(f"missing published addon example file: {source}")
        files[f"examples/addon/{relative}"] = source.read_bytes()
    return files


def _published_schema_files(root: Path) -> dict[str, bytes]:
    files = {}
    for name in PUBLISHED_SCHEMA_FILES:
        source = root / name
        if not source.is_file() or source.is_symlink():
            raise ValueError(f"missing published schema file: {source}")
        files[f"schema/{name}"] = source.read_bytes()
    return files


def _publish_files() -> dict[str, bytes]:
    tool_root = Path(__file__).resolve().parent
    repo_root = tool_root.parents[1]
    files = {
        "compat-kit": (tool_root / "compat-kit").read_bytes(),
        "compat_kit.py": Path(__file__).read_bytes(),
        "README.md": (tool_root / "README.md").read_bytes(),
        "LICENSE": (repo_root / "LICENSE").read_bytes(),
        "examples/github-actions/compat-kit.yml": (
            tool_root / "examples/github-actions/compat-kit.yml"
        ).read_bytes(),
    }
    files.update(_published_schema_files(tool_root / "schema"))
    files.update(
        _published_addon_example_files(repo_root / "examples/addon")
    )
    files["templates/craftingtests.platform.nbt"] = base64.b64decode(
        "H4sICMY9CmoC/2JlaGF2aW9yYWx0ZXN0cy5wbGF0Zm9ybS5uYnQAjdPLCsJADAXQm0zV"
        "VpF+gH/i2rVL92OJMGhb7WTl1/uA+gDBG5hFSGYuHJg5ELDYRI87G3LqO6C+VChyulrA"
        "Y/hxKszO8WTuNr+3WqLYxtZQt6mzZogHX2fvO8M4WL4HMQ2oMN2f+uaYH7dXFcK5z+Pb"
        "rwqYZI9uz+Z7R4gdJXbGEiJLiCwhspTIUiJL/2QJYSiEoRCGQhgKYSiEoRCGQhgKYaiEo"
        "RKGShgqYaiEoRKGShgqYfhrp7TOkyd7/m3gBsmsFS9XBAAA"
    )
    for relative, payload in _wrapper_files().items():
        files[relative] = payload
    return files


def publish_archive(output, release_version):
    if release_version != TOOL_VERSION:
        raise ValueError(
            "release version does not match compat-kit tool version: "
            f"{release_version} != {TOOL_VERSION}"
        )
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    root = "auto-storage-compat-kit"
    with zipfile.ZipFile(
        output,
        "w",
        compression=zipfile.ZIP_DEFLATED,
        compresslevel=9,
    ) as archive:
        for relative, payload in sorted(_publish_files().items()):
            info = zipfile.ZipInfo(f"{root}/{relative}", (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            mode = 0o755 if relative in ("compat-kit", "gradlew") else 0o644
            info.external_attr = mode << 16
            archive.writestr(info, payload, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    return output


def _candidate_map(audit: dict, bucket: str) -> dict[str, tuple[str, str]]:
    return {
        candidate["class"]: (
            candidate["public_signature"],
            candidate.get("source_class", candidate["class"]),
        )
        for candidate in audit["candidates"].get(bucket, [])
    }


def diff_audits(old: dict, new: dict) -> dict:
    _validate_audit(old)
    _validate_audit(new)
    if old["target"]["mod_id"] != new["target"]["mod_id"]:
        raise ValueError(
            "audit mod IDs differ: "
            f"{old['target']['mod_id']} != {new['target']['mod_id']}"
        )
    bucket_changes = {}
    ancestry_changed = (
        old.get("ancestry_classpath", [])
        != new.get("ancestry_classpath", [])
        or old.get("ancestry_dependencies", [])
        != new.get("ancestry_dependencies", [])
    )
    contract_affected = (
        old["artifact"]["sha256"] != new["artifact"]["sha256"]
        or ancestry_changed
    )
    for bucket in CURRENT_CANDIDATE_BUCKETS:
        old_map = _candidate_map(old, bucket)
        new_map = _candidate_map(new, bucket)
        added = sorted(set(new_map) - set(old_map))
        removed = sorted(set(old_map) - set(new_map))
        changed = sorted(
            class_name
            for class_name in set(old_map) & set(new_map)
            if old_map[class_name] != new_map[class_name]
        )
        bucket_changes[bucket] = {
            "added": added,
            "removed": removed,
            "changed": changed,
        }
        contract_affected = contract_affected or bool(added or removed or changed)
    old_risks = {(risk["code"], tuple(risk["evidence"])) for risk in old["risks"]}
    new_risks = {(risk["code"], tuple(risk["evidence"])) for risk in new["risks"]}
    risk_changes = {
        "added": [
            {"code": code, "evidence": list(evidence)}
            for code, evidence in sorted(new_risks - old_risks)
        ],
        "removed": [
            {"code": code, "evidence": list(evidence)}
            for code, evidence in sorted(old_risks - new_risks)
        ],
    }
    contract_affected = contract_affected or bool(
        risk_changes["added"] or risk_changes["removed"]
    )
    old_recipe_data = old.get("recipe_data")
    new_recipe_data = new.get("recipe_data")
    old_digest = old_recipe_data.get("digest") if old_recipe_data else None
    new_digest = new_recipe_data.get("digest") if new_recipe_data else None
    old_sources = old_recipe_data.get("sources") if old_recipe_data else None
    new_sources = new_recipe_data.get("sources") if new_recipe_data else None
    recipe_data_changes = {
        "from_digest": old_digest,
        "to_digest": new_digest,
        "sources_changed": old_sources != new_sources,
        "changed": old_digest != new_digest,
    }
    contract_affected = contract_affected or recipe_data_changes["changed"]
    return {
        "schema": SCHEMA_VERSION,
        "kind": "auto_storage_compat_delta",
        "mod_id": old["target"]["mod_id"],
        "from_version": old["target"]["version"],
        "to_version": new["target"]["version"],
        **bucket_changes,
        "ancestry_changed": ancestry_changed,
        "recipe_data": recipe_data_changes,
        "risks": risk_changes,
        "contract_affected": contract_affected,
    }


NUMERIC_MEMBER = re.compile(
    r"\b(?:byte|short|int|long|float|double|java\.lang\.(?:Byte|Short|Integer|Long|Float|Double))\s+"
    r"(?P<member>[A-Za-z_$][A-Za-z0-9_$]*)\s*\([^;{}]*\)"
)
NUMERIC_FIELD = re.compile(
    r"\b(?:byte|short|int|long|float|double|java\.lang\.(?:Byte|Short|Integer|Long|Float|Double))\s+"
    r"(?P<member>[A-Za-z_$][A-Za-z0-9_$]*)\s*;"
)


def _rate_binding_candidates(candidate: dict) -> list[dict]:
    bindings = []
    for match in itertools.chain(
        NUMERIC_MEMBER.finditer(candidate["public_signature"]),
        NUMERIC_FIELD.finditer(candidate["public_signature"]),
    ):
        member = match.group("member")
        lowered = member.lower()
        if "slot" in lowered:
            continue
        if any(term in lowered for term in ("parallel", "processes", "lanes", "operations")):
            template = "parallel_lanes"
        elif any(term in lowered for term in ("tick", "time", "duration")):
            template = "config_tick_ratio"
        elif any(term in lowered for term in ("speed", "rate", "throughput")):
            template = "public_numeric_getter"
        else:
            continue
        bindings.append({
            "member": member,
            "template": template,
            "status": "needs_decision",
            "evidence": f"{candidate['class']}#{member}:{match.group(0)}",
        })
    return sorted(bindings, key=lambda binding: (binding["member"], binding["template"]))


def _requirement_classification(field: str) -> str | None:
    lowered = field.lower()
    if any(term in lowered for term in (
        "heat",
        "pressure",
        "rpm",
        "multiblock",
        "world",
        "entity",
        "chance",
        "random",
        "environment",
        "biome",
        "dimension",
    )):
        return "unsupported_live_state"
    if any(term in lowered for term in (
        "item",
        "ingredient",
        "fluid",
        "chemical",
        "gas",
        "energy",
        "mana",
        "source",
        "catalyst",
        "tool",
        "remainder",
        "container",
    )):
        return "transaction_representable"
    if any(term in lowered for term in (
        "duration",
        "processing_time",
        "ticks",
        "speed",
        "rate",
        "parallel",
        "processes",
        "lanes",
    )):
        return "station_descriptor_representable"
    return None


def _validate_proposals(proposals: dict):
    if not isinstance(proposals, dict) or set(proposals) != {
        "schema",
        "kind",
        "target",
        "source_audit_digest",
        "machine_candidates",
        "requirement_candidates",
    }:
        raise ValueError("compatibility proposals have invalid fields")
    if proposals["schema"] != 1 or proposals["kind"] != "auto_storage_compat_proposals":
        raise ValueError("compatibility proposals have invalid identity")
    if not re.fullmatch(r"[0-9a-f]{64}", proposals["source_audit_digest"]):
        raise ValueError("compatibility proposals have invalid audit digest")
    for index, candidate in enumerate(proposals["machine_candidates"]):
        if not isinstance(candidate, dict) or set(candidate) != {
            "class",
            "status",
            "evidence",
            "rate_bindings",
        }:
            raise ValueError(f"machine proposal {index} is invalid")
        if candidate["status"] != "needs_decision" or not candidate["evidence"]:
            raise ValueError(f"machine proposal {index} must remain unresolved")
        for binding in candidate["rate_bindings"]:
            if binding.get("status") != "needs_decision" or binding.get("template") not in {
                "config_tick_ratio",
                "public_numeric_getter",
                "parallel_lanes",
            }:
                raise ValueError(f"machine proposal {index} has invalid rate binding")
    for index, candidate in enumerate(proposals["requirement_candidates"]):
        if not isinstance(candidate, dict) or set(candidate) != {
            "serializer_id",
            "field",
            "classification",
            "status",
            "evidence",
        }:
            raise ValueError(f"requirement proposal {index} is invalid")
        if candidate["status"] != "needs_decision" or candidate["classification"] not in {
            "transaction_representable",
            "station_descriptor_representable",
            "unsupported_live_state",
        }:
            raise ValueError(f"requirement proposal {index} must remain unresolved")


def propose_audit(audit: dict) -> dict:
    _validate_audit(audit)
    if audit["scanner_format"] != SCAN_CACHE_VERSION:
        raise ValueError("propose requires a current scanner-format audit")
    machines = [
        {
            "class": candidate["class"],
            "status": "needs_decision",
            "evidence": [
                f"{candidate['class']}#public_signature",
                *candidate["classification"]["evidence"],
            ],
            "rate_bindings": _rate_binding_candidates(candidate),
        }
        for candidate in (
            audit["candidates"]["station_classes"]
            + audit["candidates"]["block_entity_classes"]
        )
    ]
    requirements = []
    for serializer in audit["recipe_data"]["serializers"]:
        for field in serializer["fields"]:
            classification = _requirement_classification(field)
            if classification is None:
                continue
            requirements.append({
                "serializer_id": serializer["serializer_id"],
                "field": field,
                "classification": classification,
                "status": "needs_decision",
                "evidence": f"recipe_data:{serializer['serializer_id']}#{field}",
            })
    proposals = {
        "schema": 1,
        "kind": "auto_storage_compat_proposals",
        "target": dict(audit["target"]),
        "source_audit_digest": hashlib.sha256(
            canonical_json(audit).encode("utf-8")
        ).hexdigest(),
        "machine_candidates": sorted(machines, key=lambda candidate: candidate["class"]),
        "requirement_candidates": sorted(
            requirements,
            key=lambda candidate: (candidate["serializer_id"], candidate["field"]),
        ),
    }
    _validate_proposals(proposals)
    return proposals


def _runtime_probe_spec(
    audit: dict,
    game_test_namespace: str,
    plan: dict | None = None,
) -> dict:
    proposals = propose_audit(audit)
    unresolved = [
        {
            "kind": "machine_candidate",
            "id": candidate["class"],
            "evidence": candidate["evidence"],
        }
        for candidate in proposals["machine_candidates"]
    ]
    unresolved.extend(
        {
            "kind": "resource_api",
            "id": candidate["class"],
            "evidence": [
                f"{candidate['class']}#public_signature",
                *candidate["classification"]["evidence"],
            ],
        }
        for candidate in audit["candidates"]["resource_apis"]
    )
    return {
        "schema": 1,
        "kind": "auto_storage_runtime_probe_spec",
        "authority": "evidence_only",
        "target": dict(audit["target"]),
        "game_test_namespace": game_test_namespace,
        "source_audit_digest": hashlib.sha256(
            canonical_json(audit).encode("utf-8")
        ).hexdigest(),
        "source_probe_plan_digest": (
            hashlib.sha256(canonical_json(plan).encode("utf-8")).hexdigest()
            if plan is not None
            else None
        ),
        "limits": {
            "loaded_recipes": MAX_RUNTIME_PROBE_RECIPES,
            "config_values": MAX_RUNTIME_PROBE_VALUES,
            "capability_surfaces": MAX_RUNTIME_PROBE_VALUES,
        },
        "unresolved": sorted(
            unresolved,
            key=lambda entry: (entry["kind"], entry["id"]),
        ),
    }


def _validate_runtime_probe_plan(plan: dict, audit: dict):
    if not isinstance(plan, dict) or set(plan) != {
        "schema",
        "kind",
        "source_audit_digest",
        "target",
        "config_values",
        "capability_surfaces",
    }:
        raise ValueError("runtime probe plan has invalid fields")
    if plan["schema"] != 1 or plan["kind"] != "auto_storage_runtime_probe_plan":
        raise ValueError("runtime probe plan has invalid identity")
    expected_digest = hashlib.sha256(
        canonical_json(audit).encode("utf-8")
    ).hexdigest()
    if plan["source_audit_digest"] != expected_digest:
        raise ValueError("runtime probe plan source audit does not match")
    if plan["target"] != audit["target"]:
        raise ValueError("runtime probe plan target does not match")
    for name in ("config_values", "capability_surfaces"):
        entries = plan[name]
        if not isinstance(entries, list) or len(entries) > MAX_RUNTIME_PROBE_VALUES:
            raise ValueError(f"runtime probe plan {name} is invalid")
        keys = []
        for index, entry in enumerate(entries):
            required = {"id", "source", "item", "accessor"}
            if name == "capability_surfaces":
                required.add("surface")
            if not isinstance(entry, dict) or set(entry) != required:
                raise ValueError(f"runtime probe plan {name} entry {index} is invalid")
            if any(
                not isinstance(entry[field], str) or not entry[field]
                for field in required - {"accessor"}
            ):
                raise ValueError(f"runtime probe plan {name} entry {index} is invalid")
            if not RESOURCE_LOCATION.fullmatch(entry["item"]):
                raise ValueError(f"runtime probe plan {name} entry {index} has invalid item")
            if name == "capability_surfaces" and not RESOURCE_LOCATION.fullmatch(
                entry["surface"]
            ):
                raise ValueError(
                    f"runtime probe plan {name} entry {index} has invalid surface"
                )
            _validate_direct_accessor(
                entry["accessor"],
                f"runtime probe plan {name} entry {index}",
                expected_value_type=(
                    "boolean" if name == "capability_surfaces" else "number"
                ),
            )
            keys.append((entry["id"], entry.get("surface", ""), entry["source"]))
        if keys != sorted(set(keys)):
            raise ValueError(f"runtime probe plan {name} must be sorted and unique")
    return plan


def _runtime_probe_java(
    audit: dict,
    spec: dict,
    game_test_namespace: str,
    plan: dict | None = None,
) -> tuple[str, str]:
    mod_id = audit["target"]["mod_id"]
    package_name = f"com.example.autostorageprobe.{_java_segment(mod_id)}"
    class_name = f"{_pascal(mod_id)}RuntimeProbeGameTests"
    config_calls = []
    capability_calls = []
    plan_digest = None
    if plan is not None:
        plan_digest = hashlib.sha256(
            canonical_json(plan).encode("utf-8")
        ).hexdigest()
        for entry in plan["config_values"]:
            value = _render_numeric_accessor(entry["accessor"], entry["item"])
            config_calls.append(
                "        addConfig(configValues, "
                f"{json.dumps(entry['id'])}, {value}, {json.dumps(entry['source'])});"
            )
        for entry in plan["capability_surfaces"]:
            value = _render_numeric_accessor(entry["accessor"], entry["item"])
            capability_calls.append(
                "        addCapability(capabilitySurfaces, "
                f"{json.dumps(entry['id'])}, {json.dumps(entry['surface'])}, "
                f"{value}, {json.dumps(entry['source'])});"
            )
    plan_digest_statement = (
        'root.add("source_probe_plan_digest", com.google.gson.JsonNull.INSTANCE);'
        if plan_digest is None
        else 'root.addProperty("source_probe_plan_digest", '
        + json.dumps(plan_digest)
        + ");"
    )
    source = f'''package {package_name};

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@GameTestHolder("{game_test_namespace}")
public final class {class_name} {{
    private static final int MAX_LOADED_RECIPES = {MAX_RUNTIME_PROBE_RECIPES};
    private static final String TARGET_MOD_ID = "{mod_id}";
    private static final String SOURCE_AUDIT_DIGEST = "{spec['source_audit_digest']}";
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private {class_name}() {{
    }}

    @GameTest(template = "empty")
    public static void collect_runtime_evidence(GameTestHelper helper) {{
        String outputProperty = System.getProperty("compatKitProbeOutput");
        if (outputProperty == null || outputProperty.isBlank()) {{
            helper.fail("compatKitProbeOutput system property is required");
            return;
        }}
        List<RecipeHolder<?>> recipes = helper.getLevel().getRecipeManager().getRecipes()
                .stream()
                .sorted(Comparator.comparing(holder -> holder.id().toString()))
                .toList();
        if (recipes.size() > MAX_LOADED_RECIPES) {{
            helper.fail("runtime probe recipe inventory exceeds " + MAX_LOADED_RECIPES);
            return;
        }}

        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        root.addProperty("kind", "auto_storage_runtime_probe");
        root.addProperty("authority", "evidence_only");
        JsonObject target = new JsonObject();
        target.addProperty("mod_id", "{mod_id}");
        target.addProperty("display_name", {json.dumps(audit['target']['display_name'])});
        target.addProperty("version", {json.dumps(audit['target']['version'])});
        root.add("target", target);
        root.addProperty("source_audit_digest", SOURCE_AUDIT_DIGEST);
        {plan_digest_statement}
        root.addProperty("loaded_recipe_count", recipes.size());

        JsonArray recipeRecords = new JsonArray();
        for (RecipeHolder<?> holder : recipes) {{
            Recipe<?> recipe = holder.value();
            ItemStack result = recipe.getResultItem(helper.getLevel().registryAccess());
            JsonObject record = new JsonObject();
            record.addProperty("id", holder.id().toString());
            record.addProperty("type", BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString());
            record.addProperty("serializer", BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()).toString());
            record.addProperty("class", recipe.getClass().getName());
            record.addProperty("ingredient_count", recipe.getIngredients().size());
            record.addProperty("special", recipe.isSpecial());
            record.addProperty("result_item", result.isEmpty()
                    ? "minecraft:air"
                    : BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
            record.addProperty("result_count", result.isEmpty() ? 0 : result.getCount());
            recipeRecords.add(record);
        }}
        root.add("recipes", recipeRecords);

        JsonObject registries = new JsonObject();
        registries.add("blocks", targetIds(BuiltInRegistries.BLOCK.keySet().stream()));
        registries.add("items", targetIds(BuiltInRegistries.ITEM.keySet().stream()));
        registries.add("block_entity_types", targetIds(
                BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet().stream()));
        root.add("registries", registries);
        JsonArray configValues = new JsonArray();
{chr(10).join(config_calls)}
        root.add("config_values", configValues);
        JsonArray capabilitySurfaces = new JsonArray();
{chr(10).join(capability_calls)}
        root.add("capability_surfaces", capabilitySurfaces);

        Path output = Path.of(outputProperty).toAbsolutePath().normalize();
        try {{
            Path parent = output.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(
                    output,
                    GSON.toJson(root) + "\\n",
                    StandardCharsets.UTF_8);
        }} catch (IOException error) {{
            helper.fail("runtime probe output failed: " + error.getMessage());
            return;
        }}
        helper.succeed();
    }}

    private static JsonArray targetIds(Stream<ResourceLocation> ids) {{
        JsonArray result = new JsonArray();
        ids.filter(id -> id.getNamespace().equals(TARGET_MOD_ID))
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .forEach(id -> result.add(id.toString()));
        return result;
    }}

    private static void addConfig(
            JsonArray values,
            String id,
            Number value,
            String source
    ) {{
        JsonObject config = new JsonObject();
        config.addProperty("id", id);
        config.addProperty("value", value);
        config.addProperty("source", source);
        values.add(config);
    }}

    private static void addCapability(
            JsonArray values,
            String id,
            String surface,
            boolean available,
            String source
    ) {{
        JsonObject capability = new JsonObject();
        capability.addProperty("id", id);
        capability.addProperty("surface", surface);
        capability.addProperty("available", available);
        capability.addProperty("source", source);
        values.add(capability);
    }}

    private static Block requiredBlock(ResourceLocation id) {{
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block == Blocks.AIR) throw new IllegalStateException("Missing probe block " + id);
        return block;
    }}

    private static ResourceLocation id(String namespace, String path) {{
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }}
}}
'''
    return package_name, source


def scaffold_runtime_probe(
    audit: dict,
    output,
    *,
    game_test_namespace: str,
    plan: dict | None = None,
) -> list[Path]:
    _validate_audit(audit)
    if audit["scanner_format"] != SCAN_CACHE_VERSION:
        raise ValueError("probe requires a current scanner-format audit")
    if (
        not isinstance(game_test_namespace, str)
        or not GAME_TEST_NAMESPACE.fullmatch(game_test_namespace)
    ):
        raise ValueError("probe has invalid game_test_namespace")
    if plan is not None:
        _validate_runtime_probe_plan(plan, audit)
    spec = _runtime_probe_spec(audit, game_test_namespace, plan)
    package_name, source = _runtime_probe_java(
        audit,
        spec,
        game_test_namespace,
        plan,
    )
    package_path = package_name.replace(".", "/")
    class_name = f"{_pascal(audit['target']['mod_id'])}RuntimeProbeGameTests"
    files = {
        "probe-spec.json": canonical_json(spec).encode("utf-8"),
        f"src/main/java/{package_path}/{class_name}.java": source.encode("utf-8"),
    }
    if plan is not None:
        files["probe-plan.json"] = canonical_json(plan).encode("utf-8")
    return _materialize(
        Path(output),
        files,
        ".compat-kit-probe-manifest.json",
        audit,
    )


def validate_runtime_probe_output(
    output: dict,
    audit: dict,
    *,
    plan: dict | None = None,
):
    _validate_audit(audit)
    expected_keys = {
        "schema",
        "kind",
        "authority",
        "target",
        "source_audit_digest",
        "source_probe_plan_digest",
        "loaded_recipe_count",
        "recipes",
        "registries",
        "config_values",
        "capability_surfaces",
    }
    if not isinstance(output, dict) or set(output) != expected_keys:
        raise ValueError("runtime probe output has invalid fields")
    if (
        output["schema"] != 1
        or output["kind"] != "auto_storage_runtime_probe"
        or output["authority"] != "evidence_only"
    ):
        raise ValueError("runtime probe output has invalid identity")
    if output["target"] != audit["target"]:
        raise ValueError("runtime probe target does not match audit")
    expected_digest = hashlib.sha256(
        canonical_json(audit).encode("utf-8")
    ).hexdigest()
    if output["source_audit_digest"] != expected_digest:
        raise ValueError("runtime probe source audit does not match")
    if plan is not None:
        _validate_runtime_probe_plan(plan, audit)
        expected_plan_digest = hashlib.sha256(
            canonical_json(plan).encode("utf-8")
        ).hexdigest()
    else:
        expected_plan_digest = None
    if output["source_probe_plan_digest"] != expected_plan_digest:
        raise ValueError("runtime probe source probe plan does not match")
    recipes = output["recipes"]
    count = output["loaded_recipe_count"]
    if (
        isinstance(count, bool)
        or not isinstance(count, int)
        or count < 0
        or count > MAX_RUNTIME_PROBE_RECIPES
        or not isinstance(recipes, list)
        or len(recipes) != count
    ):
        raise ValueError("runtime probe recipe count is invalid")
    recipe_ids = []
    for index, recipe in enumerate(recipes):
        if not isinstance(recipe, dict) or set(recipe) != {
            "id",
            "type",
            "serializer",
            "class",
            "ingredient_count",
            "special",
            "result_item",
            "result_count",
        }:
            raise ValueError(f"runtime probe recipe {index} is invalid")
        for field in ("id", "type", "serializer", "result_item"):
            if not isinstance(recipe[field], str) or not RESOURCE_LOCATION.fullmatch(
                recipe[field]
            ):
                raise ValueError(f"runtime probe recipe {index} has invalid {field}")
        if not isinstance(recipe["class"], str) or not recipe["class"]:
            raise ValueError(f"runtime probe recipe {index} has invalid class")
        for field in ("ingredient_count", "result_count"):
            value = recipe[field]
            if isinstance(value, bool) or not isinstance(value, int) or value < 0:
                raise ValueError(f"runtime probe recipe {index} has invalid {field}")
        if not isinstance(recipe["special"], bool):
            raise ValueError(f"runtime probe recipe {index} has invalid special")
        recipe_ids.append(recipe["id"])
    if recipe_ids != sorted(set(recipe_ids)):
        raise ValueError("runtime probe recipes must be sorted and unique")

    registries = output["registries"]
    if not isinstance(registries, dict) or set(registries) != {
        "blocks",
        "items",
        "block_entity_types",
    }:
        raise ValueError("runtime probe registries are invalid")
    target_namespace = audit["target"]["mod_id"]
    for name, values in registries.items():
        if (
            not isinstance(values, list)
            or values != sorted(set(values))
            or any(
                not isinstance(value, str)
                or not RESOURCE_LOCATION.fullmatch(value)
                for value in values
            )
        ):
            raise ValueError(f"runtime probe registry {name} is invalid")
        if any(value.split(":", 1)[0] != target_namespace for value in values):
            raise ValueError(f"runtime probe registry {name} must use target namespace")
    for name in ("config_values", "capability_surfaces"):
        values = output[name]
        if not isinstance(values, list) or len(values) > MAX_RUNTIME_PROBE_VALUES:
            raise ValueError(f"runtime probe {name} is invalid")
    config_keys = []
    for index, entry in enumerate(output["config_values"]):
        if not isinstance(entry, dict) or set(entry) != {"id", "value", "source"}:
            raise ValueError(f"runtime probe config_values entry {index} is invalid")
        if any(
            not isinstance(entry[field], str) or not entry[field]
            for field in ("id", "source")
        ):
            raise ValueError(f"runtime probe config_values entry {index} is invalid")
        value = entry["value"]
        if isinstance(value, bool) or not isinstance(value, (int, float)) or (
            isinstance(value, float) and not math.isfinite(value)
        ):
            raise ValueError(f"runtime probe config_values entry {index} is invalid")
        config_keys.append((entry["id"], entry["source"]))
    if config_keys != sorted(set(config_keys)):
        raise ValueError("runtime probe config_values must be sorted and unique")
    capability_keys = []
    for index, entry in enumerate(output["capability_surfaces"]):
        if not isinstance(entry, dict) or set(entry) != {
            "id",
            "surface",
            "available",
            "source",
        }:
            raise ValueError(
                f"runtime probe capability_surfaces entry {index} is invalid"
            )
        if any(
            not isinstance(entry[field], str) or not entry[field]
            for field in ("id", "surface", "source")
        ):
            raise ValueError(
                f"runtime probe capability_surfaces entry {index} is invalid"
            )
        if not isinstance(entry["available"], bool):
            raise ValueError(
                f"runtime probe capability_surfaces entry {index} is invalid"
            )
        capability_keys.append((entry["id"], entry["surface"], entry["source"]))
    if capability_keys != sorted(set(capability_keys)):
        raise ValueError("runtime probe capability_surfaces must be sorted and unique")
    if plan is not None:
        expected_config_keys = [
            (entry["id"], entry["source"])
            for entry in plan["config_values"]
        ]
        if config_keys != expected_config_keys:
            raise ValueError("runtime probe plan config_values do not match output")
        expected_capability_keys = [
            (entry["id"], entry["surface"], entry["source"])
            for entry in plan["capability_surfaces"]
        ]
        if capability_keys != expected_capability_keys:
            raise ValueError(
                "runtime probe plan capability_surfaces do not match output"
            )
    return output


def _materialize_plain(root: Path, files: dict[str, bytes]) -> list[Path]:
    root = _validate_materialization_root(root)
    for relative, payload in sorted(files.items()):
        path = Path(relative)
        if path.is_absolute() or ".." in path.parts or not path.parts:
            raise ValueError(f"generated path is unsafe: {relative}")
        target = root / path
        ancestor = root
        for part in path.parts[:-1]:
            ancestor /= part
            if ancestor.is_symlink():
                raise ValueError(
                    "generated path parent is a symlink: "
                    + ancestor.relative_to(root).as_posix()
                )
            if ancestor.exists() and not ancestor.is_dir():
                raise ValueError(
                    "generated path parent is not a directory: "
                    + ancestor.relative_to(root).as_posix()
                )
        if target.is_symlink():
            raise ValueError(f"generated path is a symlink: {relative}")
        if target.exists() and (
            not target.is_file() or target.read_bytes() != payload
        ):
            raise ValueError(f"generated file drift: {relative}")
    generated = []
    for relative, payload in sorted(files.items()):
        target = root / relative
        if not target.exists():
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(payload)
        if relative == "commands.sh":
            target.chmod(0o755)
        generated.append(target)
    return generated


def _worker_candidate_summary(contract: dict, audit: dict) -> dict:
    proposals = propose_audit(audit)
    machine_candidates = []
    for candidate in proposals["machine_candidates"]:
        compact = dict(candidate)
        compact["evidence"] = [
            evidence.replace("#public_signature", "#public-surface")
            for evidence in candidate["evidence"]
        ]
        machine_candidates.append(compact)
    return {
        "schema": 1,
        "kind": "auto_storage_compat_worker_summary",
        "target": dict(audit["target"]),
        "counts": {
            bucket: len(audit["candidates"][bucket])
            for bucket in CURRENT_CANDIDATE_BUCKETS
        },
        "candidates": {
            bucket: [
                {
                    "class": candidate["class"],
                    "classification": candidate["classification"],
                }
                for candidate in audit["candidates"][bucket]
            ]
            for bucket in CURRENT_CANDIDATE_BUCKETS
        },
        "recipe_data": {
            "digest": audit["recipe_data"]["digest"],
            "declared_recipes": audit["recipe_data"]["declared_recipes"],
            "effective_recipes": audit["recipe_data"]["effective_recipes"],
            "serializers": audit["recipe_data"]["serializers"],
        },
        "risks": audit["risks"],
        "machine_candidates": machine_candidates,
        "requirement_candidates": proposals["requirement_candidates"],
        "unresolved_families": [
            {
                "id": family["id"],
                "class": family["class"],
                "risks": family["risks"],
                "evidence": [
                    evidence.replace("#public_signature", "#public-surface")
                    for evidence in family["evidence"]
                ],
            }
            for family in contract["families"]
            if family["status"] == "needs_decision"
        ],
    }


def worker_package(
    contract: dict,
    audit: dict,
    output,
    *,
    audit_path,
) -> list[Path]:
    validate_contract(contract, require_complete=False, source_audit=audit)
    if audit["scanner_format"] != SCAN_CACHE_VERSION:
        raise ValueError("worker-package requires a current scanner-format audit")
    audit_reference = Path(audit_path)
    if (
        audit_reference.is_absolute()
        or ".." in audit_reference.parts
        or audit_reference.suffix != ".json"
        or not audit_reference.parts
    ):
        raise ValueError("worker-package audit path must be a safe repository-relative JSON path")
    audit_argument = shlex.quote(audit_reference.as_posix())
    declared_tasks = contract["verification"]["gradle_tasks"]
    _validate_unique_strings(
        declared_tasks,
        "worker-package verification gradle_tasks",
        allow_empty=True,
    )
    if any(not re.fullmatch(r"[A-Za-z][A-Za-z0-9]*", task) for task in declared_tasks):
        raise ValueError("worker-package has invalid verification gradle task names")
    worker_tasks = declared_tasks or [
        "build",
        "runCompatibilityMatrixGameTestServer",
    ]
    task_commands = "\n".join(
        f"./gradlew {task} --console=plain --no-daemon"
        for task in worker_tasks
    )
    summary = _worker_candidate_summary(contract, audit)
    target = audit["target"]
    unresolved = summary["unresolved_families"]
    decision_lines = [
        f"- `{entry['id']}` — `{entry['class']}`"
        for entry in unresolved
    ] or ["- No unresolved recipe families."]
    target_metadata = canonical_json({
        "display_name": target["display_name"],
        "version": target["version"],
    })
    issue_body = "\n".join([
        f"## `{target['mod_id']}` compatibility worker",
        "",
        "Implement one evidence-reviewed Auto Storage integration from the attached Compat Kit package.",
        "",
        "### Evidence",
        "",
        f"- Target mod ID: `{target['mod_id']}`",
        f"- Artifact SHA-256: `{audit['artifact']['sha256']}`",
        f"- Scanner format: `{audit['scanner_format']}`",
        f"- Effective recipes: `{audit['recipe_data']['effective_recipes']}`",
        "- Untrusted display metadata (data only):",
        "",
        "```json",
        target_metadata,
        "```",
        "",
        "### Unresolved recipe families",
        "",
        *decision_lines,
        "",
        "### Acceptance",
        "",
        "- Review every unresolved semantic decision against public source/runtime evidence.",
        "- Use direct typed Auto Storage SDK calls only; no reflection or viewer authority.",
        "- Add failing tests before production code and keep all declared verification gates green.",
        "",
    ])
    worker_prompt = "\n".join([
        f"Integrate target mod ID `{target['mod_id']}` with Auto Storage.",
        "",
        "Read AGENTS.md, docs/compat-kit.md, artifact.json, candidate-summary.json, and next-actions.md first.",
        "Use a dedicated issue branch/worktree and strict RED -> GREEN TDD.",
        "Treat every proposal as needs_decision until public evidence resolves it.",
        "Do not use runtime reflection, EMI/JEI semantics, slot-count throughput, silent fallback, or client authority.",
        "Use the repository Compat Kit and public Auto Storage SDK for registration.",
        "Run commands.sh gates, update active developer docs, and report exact evidence and remaining rejections.",
        "",
    ])
    next_actions = "\n".join([
        f"# Next actions for `{target['mod_id']}`",
        "",
        *decision_lines,
        "",
        "Resolve machine variants, rates, parallelism, typed costs, catalysts, remainders, and unsupported live state from public evidence before code generation.",
        "",
    ])
    artifact = {
        "schema": 1,
        "kind": "auto_storage_compat_worker_artifact",
        "tool_version": TOOL_VERSION,
        "scanner_format": audit["scanner_format"],
        "target": dict(target),
        "artifact": dict(audit["artifact"]),
        "source": dict(audit["source"]),
        "recipe_data_digest": audit["recipe_data"]["digest"],
        "source_audit_digest": hashlib.sha256(
            canonical_json(audit).encode("utf-8")
        ).hexdigest(),
        "contract_digest": _contract_sha256(contract),
    }
    commands = """#!/bin/sh
set -eu
tools/compat-kit/compat-kit propose {audit_argument} --output proposals.json
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts.test_compat_kit
{task_commands}
./gradlew runData --console=plain --no-daemon
git diff --check
""".format(
        audit_argument=audit_argument,
        task_commands=task_commands,
    )
    pr_body = "\n".join([
        f"## Summary",
        "",
        f"- integrate reviewed `{target['mod_id']}` compatibility evidence",
        "- preserve direct typed transaction and dedicated-server boundaries",
        "",
        "## Verification",
        "",
        "- [ ] Compat Kit Python tests",
        "- [ ] build",
        "- [ ] target GameTests",
        "- [ ] compatibility matrix",
        "- [ ] runData drift check",
        "",
        "Closes the worker issue.",
        "",
    ])
    files = {
        "issue-body.md": issue_body.encode("utf-8"),
        "worker-prompt.md": worker_prompt.encode("utf-8"),
        "next-actions.md": next_actions.encode("utf-8"),
        "artifact.json": canonical_json(artifact).encode("utf-8"),
        "commands.sh": commands.encode("utf-8"),
        "candidate-summary.json": canonical_json(summary).encode("utf-8"),
        "pr-body.md": pr_body.encode("utf-8"),
    }
    return _materialize_plain(Path(output), files)


JAVA_TYPE = re.compile(
    r"^[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*$"
)
JAVA_MEMBER = re.compile(r"^[A-Za-z_$][A-Za-z0-9_$]*$")
RATE_TEMPLATES = frozenset({
    "fixed",
    "config_tick_ratio",
    "public_numeric_getter",
    "tier_multiplier",
    "parallel_lanes",
    "speed_times_parallel",
})


def _is_java_type(value) -> bool:
    return (
        isinstance(value, str)
        and JAVA_TYPE.fullmatch(value) is not None
        and all(
            segment not in JAVA_RESERVED_IDENTIFIERS
            for segment in value.split(".")
        )
    )


def _is_java_member(value) -> bool:
    return (
        isinstance(value, str)
        and JAVA_MEMBER.fullmatch(value) is not None
        and value not in JAVA_RESERVED_IDENTIFIERS
    )


def _validate_generated_class_name(
    value: object,
    reserved_types: frozenset[str],
    location: str,
):
    if not _is_java_member(value):
        raise ValueError(f"{location} has invalid class_name")
    if value in reserved_types:
        raise ValueError(f"{location} uses a reserved generated class")


def _validate_direct_accessor(
    accessor: dict,
    location: str,
    *,
    expected_value_type: str | None = None,
):
    if not isinstance(accessor, dict):
        raise ValueError(f"{location} accessor must be an object")
    kind = accessor.get("kind")
    required = {
        "static_field_value_get": {"kind", "owner", "member", "value_type"},
        "static_method": {"kind", "owner", "member", "value_type"},
        "registry_block_method": {
            "kind", "owner", "member", "value_type", "block_id",
        },
        "enum_constant_numeric_field": {
            "kind", "owner", "constant", "member", "value_type",
        },
    }.get(kind)
    if required is None:
        raise ValueError(f"{location} has invalid accessor kind")
    if set(accessor) != required:
        raise ValueError(f"{location} has invalid fields for {kind}")
    value_type = accessor["value_type"]
    if value_type not in {"integral", "number", "boolean"}:
        raise ValueError(f"{location} has invalid accessor value_type")
    if expected_value_type is not None and value_type != expected_value_type:
        raise ValueError(
            f"{location} accessor value_type must be {expected_value_type}"
        )
    if not _is_java_type(accessor["owner"]):
        raise ValueError(f"{location} has invalid accessor owner")
    if not _is_java_member(accessor["member"]):
        raise ValueError(f"{location} has invalid accessor member")
    if "constant" in accessor and not _is_java_member(accessor["constant"]):
        raise ValueError(f"{location} has invalid accessor constant")
    if "block_id" in accessor and not RESOURCE_LOCATION.fullmatch(
        accessor["block_id"]
    ):
        raise ValueError(f"{location} has invalid accessor block_id")


def _validate_positive_long(value, location: str):
    if (
        isinstance(value, bool)
        or not isinstance(value, int)
        or value <= 0
        or value > 9_223_372_036_854_775_807
    ):
        raise ValueError(f"{location} must be a positive long")


def _validate_rate_binding(binding: dict, location: str, *, allow_zero: bool = False):
    if not isinstance(binding, dict):
        raise ValueError(f"{location} must be an object")
    template = binding.get("template")
    if template not in RATE_TEMPLATES:
        raise ValueError(f"{location} has invalid rate template")
    item = binding.get("item")
    if not isinstance(item, str) or not RESOURCE_LOCATION.fullmatch(item):
        raise ValueError(f"{location} has invalid item")
    required = {
        "fixed": {"item", "template", "numerator", "denominator"},
        "config_tick_ratio": {"item", "template", "numerator", "accessor"},
        "public_numeric_getter": {"item", "template", "accessor"},
        "tier_multiplier": {
            "item", "template", "numerator", "denominator", "accessor",
        },
        "parallel_lanes": {
            "item", "template", "numerator", "denominator", "accessor",
        },
        "speed_times_parallel": {
            "item", "template", "denominator", "speed_accessor",
            "parallel_accessor",
        },
    }[template]
    if set(binding) != required:
        raise ValueError(f"{location} has invalid fields for {template}")
    if "numerator" in binding:
        numerator = binding["numerator"]
        if (
            isinstance(numerator, bool)
            or not isinstance(numerator, int)
            or numerator < (0 if allow_zero and template == "fixed" else 1)
            or numerator > 9_223_372_036_854_775_807
        ):
            qualifier = "non-negative" if allow_zero and template == "fixed" else "positive"
            raise ValueError(f"{location} numerator must be {qualifier} long")
    if "denominator" in binding:
        _validate_positive_long(binding["denominator"], f"{location} denominator")
    for name in ("accessor", "speed_accessor", "parallel_accessor"):
        if name in binding:
            _validate_direct_accessor(
                binding[name],
                f"{location} {name}",
                expected_value_type="integral",
            )


def _java_resource_location(value: str) -> str:
    namespace, path = value.split(":", 1)
    return f'id("{namespace}", "{path}")'


def _render_numeric_accessor(accessor: dict, item: str) -> str:
    _validate_direct_accessor(accessor, "rate")
    owner = accessor["owner"]
    member = accessor["member"]
    if accessor["kind"] == "static_field_value_get":
        return f"{owner}.{member}.get()"
    if accessor["kind"] == "static_method":
        return f"{owner}.{member}()"
    if accessor["kind"] == "enum_constant_numeric_field":
        return f"{owner}.{accessor['constant']}.{member}"
    return (
        f"(({owner}) requiredBlock("
        f"{_java_resource_location(accessor['block_id'])})).{member}()"
    )


def _render_rate_binding(binding: dict, *, allow_zero: bool = False) -> str:
    _validate_rate_binding(binding, "rate binding", allow_zero=allow_zero)
    item = binding["item"]
    stack = f"new ItemStack(requiredItem({_java_resource_location(item)}))"
    template = binding["template"]
    if template == "fixed":
        return (
            f"MachineVariant.of({stack}, MachineWorkRate.of("
            f"{binding['numerator']}L, {binding['denominator']}L))"
        )
    if template == "config_tick_ratio":
        value = _render_numeric_accessor(binding["accessor"], item)
        rate = (
            f"MachineWorkRate.of({binding['numerator']}L, "
            f"exactPositiveIntegral({value}, \"{item} configured ticks\"))"
        )
    elif template == "public_numeric_getter":
        value = _render_numeric_accessor(binding["accessor"], item)
        rate = (
            f"MachineWorkRate.of(exactPositiveIntegral({value}, "
            f"\"{item} public rate\"), 1L)"
        )
    elif template in {"tier_multiplier", "parallel_lanes"}:
        value = _render_numeric_accessor(binding["accessor"], item)
        rate = (
            "MachineWorkRate.of(Math.multiplyExact("
            f"{binding['numerator']}L, exactPositiveIntegral({value}, "
            f"\"{item} {template}\")), {binding['denominator']}L)"
        )
    else:
        speed = _render_numeric_accessor(binding["speed_accessor"], item)
        parallel = _render_numeric_accessor(binding["parallel_accessor"], item)
        rate = (
            "MachineWorkRate.of(Math.multiplyExact("
            f"exactPositiveIntegral({speed}, \"{item} speed\"), "
            f"exactPositiveIntegral({parallel}, \"{item} parallel\")), "
            f"{binding['denominator']}L)"
        )
    return f"MachineVariant.derived({stack}, () -> {rate})"


def _validate_recipe_binding(binding: dict, expected_kind: str, location: str):
    if not isinstance(binding, dict) or set(binding) != {
        "kind",
        "member",
        "arguments",
    }:
        raise ValueError(f"{location} requires kind, member, and arguments")
    if binding["kind"] != expected_kind:
        raise ValueError(f"{location} has invalid kind")
    if not _is_java_member(binding["member"]):
        raise ValueError(f"{location} has invalid member")
    allowed_arguments = {
        "ingredient_method": {"none"},
        "item_stack_method": {"none", "registries"},
        "numeric_method": {"none"},
    }[expected_kind]
    if binding["arguments"] not in allowed_arguments:
        raise ValueError(f"{location} has invalid arguments")


def _validate_direct_binding(binding: dict, expected_kind: str, location: str):
    if not isinstance(binding, dict) or set(binding) != {"kind", "owner", "member"}:
        raise ValueError(f"{location} requires kind, owner, and member")
    if binding["kind"] != expected_kind:
        raise ValueError(f"{location} has invalid kind")
    if not _is_java_type(binding["owner"]):
        raise ValueError(f"{location} has invalid owner")
    if not _is_java_member(binding["member"]):
        raise ValueError(f"{location} has invalid member")


def _validate_generation_plan(plan: dict, contract: dict):
    if not isinstance(plan, dict) or set(plan) != {
        "schema",
        "kind",
        "source_contract_digest",
        "target",
        "package",
        "class_name",
        "families",
        "resource_kinds",
    }:
        raise ValueError("generation plan has invalid fields")
    if plan["schema"] != 1 or plan["kind"] != "auto_storage_compat_generation_plan":
        raise ValueError("generation plan has invalid identity")
    if plan["source_contract_digest"] != _contract_sha256(contract):
        raise ValueError("generation plan contract digest does not match")
    expected_target = {
        key: contract["target"][key]
        for key in ("mod_id", "display_name", "version")
    }
    if plan["target"] != expected_target:
        raise ValueError("generation plan target does not match contract")
    if not _is_java_type(plan["package"]):
        raise ValueError("generation plan has invalid package")
    _validate_generated_class_name(
        plan["class_name"],
        GENERATION_RENDERER_TYPES,
        "generation plan",
    )
    if not isinstance(plan["resource_kinds"], list):
        raise ValueError("generation plan resource_kinds must be a list")
    if plan["resource_kinds"]:
        raise ValueError("generation plan resource kinds require resource-scaffold")
    accepted = {
        family["id"]: family
        for family in contract["families"]
        if family["status"] == "accepted"
    }
    families = plan["families"]
    if not isinstance(families, list):
        raise ValueError("generation plan families must be a list")
    family_ids = [
        entry.get("id") for entry in families if isinstance(entry, dict)
    ]
    if len(family_ids) != len(families) or len(family_ids) != len(set(family_ids)):
        raise ValueError("generation plan has duplicate family IDs")
    if set(family_ids) != set(accepted):
        raise ValueError("generation plan families must match accepted contract families")
    registration_ids = [
        entry.get("registration_id")
        for entry in families
        if isinstance(entry, dict) and entry.get("status") == "generate"
    ]
    if len(registration_ids) != len(set(registration_ids)):
        raise ValueError("generation plan has duplicate registration IDs")
    descriptor_definitions = {}
    for index, entry in enumerate(families):
        location = f"generation family {index}"
        if not isinstance(entry, dict):
            raise ValueError(f"{location} must be an object")
        status = entry.get("status")
        if status == "red_boundary":
            if set(entry) != {"id", "status", "reason"}:
                raise ValueError(f"{location} RED boundary has invalid fields")
            _validate_nonempty_string(entry["reason"], f"{location} reason")
            continue
        if status != "generate" or set(entry) != {
            "id",
            "status",
            "shape",
            "registration_id",
            "station_label_key",
            "bindings",
            "rate_bindings",
        }:
            raise ValueError(f"{location} has invalid fields")
        if entry["shape"] not in {
            "single_item_to_item", "typed_resources", "transform"
        }:
            raise ValueError(f"{location} has unsupported generation shape")
        if not isinstance(entry["registration_id"], str) or not RESOURCE_LOCATION.fullmatch(
            entry["registration_id"]
        ):
            raise ValueError(f"{location} has invalid registration_id")
        _validate_nonempty_string(
            entry["station_label_key"], f"{location} station_label_key"
        )
        if not TRANSLATION_KEY.fullmatch(entry["station_label_key"]):
            raise ValueError(f"{location} has invalid station_label_key")
        bindings = entry["bindings"]
        contract_family = accepted[entry["id"]]
        if entry["shape"] == "single_item_to_item":
            if not isinstance(bindings, dict) or set(bindings) != {
                "input",
                "output",
                "cost",
            }:
                raise ValueError(f"{location} bindings are invalid")
            _validate_recipe_binding(
                bindings["input"], "ingredient_method", f"{location} input"
            )
            _validate_recipe_binding(
                bindings["output"], "item_stack_method", f"{location} output"
            )
            cost_binding = bindings["cost"]
            if cost_binding == {"kind": "free"}:
                free_cost = True
            else:
                _validate_recipe_binding(
                    cost_binding, "numeric_method", f"{location} cost"
                )
                free_cost = False
            if (
                contract_family["inputs"] != [{
                    "role": "consume",
                    "resource_kind": "item",
                    "amount": 1,
                    "selector": "recipe.input",
                }]
                or contract_family["outputs"] != [{
                    "role": "primary",
                    "resource_kind": "item",
                    "amount": "recipe.output.count",
                    "selector": "recipe.output",
                }]
                or (
                    free_cost
                    and contract_family["costs"]
                )
                or (
                    not free_cost
                    and contract_family["costs"] != [{
                        "resource_kind": "auto_storage:station_work",
                        "amount": "recipe.processing_time",
                    }]
                )
            ):
                raise ValueError(f"{location} contract shape is not supported")
        elif entry["shape"] == "transform":
            if not isinstance(bindings, dict) or set(bindings) != {
                "input_items",
                "output",
                "amount_per_item",
                "station",
                "station_work_per_item",
                "retained_items",
                "target_label_key",
                "source_label_key",
            }:
                raise ValueError(f"{location} transform bindings are invalid")
            input_items = bindings["input_items"]
            if not isinstance(input_items, list) or not input_items:
                raise ValueError(f"{location} transform input items must be non-empty")
            if len(input_items) != len(set(input_items)):
                raise ValueError(f"{location} transform input items must be unique")
            for item in input_items:
                if not isinstance(item, str) or not RESOURCE_LOCATION.fullmatch(item):
                    raise ValueError(f"{location} transform input item is invalid")
            output = bindings["output"]
            if not isinstance(output, dict) or set(output) != {"kind", "resource"}:
                raise ValueError(f"{location} transform output is invalid")
            for key in ("kind", "resource"):
                if not isinstance(output[key], str) or not RESOURCE_LOCATION.fullmatch(
                    output[key]
                ):
                    raise ValueError(f"{location} transform output {key} is invalid")
            amount = bindings["amount_per_item"]
            if isinstance(amount, bool) or not isinstance(amount, int) \
                    or not 1 <= amount <= 9223372036854775807:
                raise ValueError(
                    f"{location} transform amount per item must be in 1..Long.MAX_VALUE"
                )
            station = bindings["station"]
            if station is not None and (
                not isinstance(station, str) or not RESOURCE_LOCATION.fullmatch(station)
            ):
                raise ValueError(f"{location} transform station is invalid")
            work = bindings["station_work_per_item"]
            if isinstance(work, bool) or not isinstance(work, int) \
                    or not 0 <= work <= 9223372036854775807:
                raise ValueError(
                    f"{location} transform station work must be in 0..Long.MAX_VALUE"
                )
            if (station is None) != (work == 0):
                raise ValueError(
                    f"{location} transform station requires matching work"
                )
            if station is not None and station != contract_family["station"][
                "descriptor_id"
            ]:
                raise ValueError(
                    f"{location} transform station does not match the contract descriptor"
                )
            retained = bindings["retained_items"]
            if not isinstance(retained, list):
                raise ValueError(f"{location} transform retained items must be a list")
            retained_items = []
            for retained_index, entry_retained in enumerate(retained):
                if not isinstance(entry_retained, dict) or set(entry_retained) != {
                    "item",
                    "count",
                }:
                    raise ValueError(
                        f"{location} transform retained item {retained_index} is invalid"
                    )
                item = entry_retained["item"]
                if not isinstance(item, str) or not RESOURCE_LOCATION.fullmatch(item):
                    raise ValueError(
                        f"{location} transform retained item {retained_index} id is invalid"
                    )
                count = entry_retained["count"]
                if isinstance(count, bool) or not isinstance(count, int) \
                        or not 1 <= count <= 64:
                    raise ValueError(
                        f"{location} transform retained item {retained_index} "
                        "count must be in 1..64"
                    )
                if item in retained_items:
                    raise ValueError(
                        f"{location} transform retained items must be unique"
                    )
                retained_items.append(item)
            for label_key in ("target_label_key", "source_label_key"):
                label = bindings[label_key]
                if not isinstance(label, str) or not TRANSLATION_KEY.fullmatch(label):
                    raise ValueError(f"{location} transform {label_key} is invalid")
            if contract_family["station"]["category"] not in {
                "process",
                "instant",
            }:
                raise ValueError(
                    f"{location} transform families require a process or "
                    "instant station descriptor"
                )
            if contract_family["inputs"] != [{
                "role": "consume",
                "resource_kind": "item",
                "amount": 1,
                "selector": "transform.input",
            }] or len(contract_family["outputs"]) != 1 or \
                    contract_family["outputs"][0]["resource_kind"] == "item" or \
                    contract_family["outputs"][0]["role"] != "primary" or \
                    contract_family["costs"]:
                raise ValueError(f"{location} contract shape is not supported")
        else:
            if not isinstance(bindings, dict) or set(bindings) != {
                "eligibility",
                "plan",
                "cost",
            }:
                raise ValueError(f"{location} bindings are invalid")
            _validate_direct_binding(
                bindings["eligibility"],
                "static_recipe_predicate_method",
                f"{location} eligibility",
            )
            _validate_direct_binding(
                bindings["plan"], "static_typed_plan_method", f"{location} plan"
            )
            _validate_direct_binding(
                bindings["cost"],
                "static_recipe_family_cost_method",
                f"{location} cost",
            )
        rates = entry["rate_bindings"]
        if not isinstance(rates, list) or not rates:
            raise ValueError(f"{location} requires rate bindings")
        allow_zero = contract_family["station"]["category"] == "instant"
        if contract_family["station"]["category"] == "transform" \
                and entry["shape"] != "transform":
            raise ValueError(f"{location} transform stations are not generated")
        for rate_index, rate in enumerate(rates):
            _validate_rate_binding(
                rate,
                f"{location} rate {rate_index}",
                allow_zero=allow_zero,
            )
            if allow_zero and (
                rate["template"] != "fixed" or rate["numerator"] != 0
            ):
                raise ValueError(f"{location} instant station rate must be fixed zero")
        contract_items = {
            variant["item"]
            for variant in contract_family["station"]["variants"]
        }
        rate_items = [rate["item"] for rate in rates]
        if len(rate_items) != len(set(rate_items)):
            raise ValueError(f"{location} has duplicate rate binding item")
        if set(rate_items) != contract_items:
            raise ValueError(f"{location} rate items do not match contract station")
        contract_rates = {
            variant["item"]: variant["rate"]
            for variant in contract_family["station"]["variants"]
        }
        for rate in rates:
            if rate["template"] == "fixed" and (
                rate["numerator"]
                != contract_rates[rate["item"]]["numerator"]
                or rate["denominator"]
                != contract_rates[rate["item"]]["denominator"]
            ):
                raise ValueError(f"{location} fixed rate does not match contract")
        descriptor_id = contract_family["station"]["descriptor_id"]
        if entry["registration_id"].split(":", 1)[0] != descriptor_id.split(":", 1)[0]:
            raise ValueError(f"{location} registration namespace does not match descriptor")
        descriptor_definition = (
            entry["station_label_key"],
            canonical_json(contract_family["station"]),
            canonical_json(rates),
        )
        previous_definition = descriptor_definitions.get(descriptor_id)
        if (
            previous_definition is not None
            and previous_definition != descriptor_definition
        ):
            raise ValueError(
                f"{location} shared descriptor {descriptor_id} has conflicting definitions"
            )
        descriptor_definitions[descriptor_id] = descriptor_definition
    return plan


def _recipe_call(binding: dict, variable: str) -> str:
    arguments = "registries" if binding["arguments"] == "registries" else ""
    return f"{variable}.{binding['member']}({arguments})"


def _generated_compat_java(
    contract: dict,
    audit: dict,
    plan: dict,
) -> tuple[str, list[dict]]:
    generated = [entry for entry in plan["families"] if entry["status"] == "generate"]
    boundaries = [entry for entry in plan["families"] if entry["status"] == "red_boundary"]
    contract_by_id = {family["id"]: family for family in contract["families"]}
    source_class_by_binary = {
        candidate["class"]: candidate["source_class"]
        for candidate in audit["candidates"]["recipe_classes"]
    }
    descriptor_namespaces = {
        contract_by_id[entry["id"]]["station"]["descriptor_id"].split(":", 1)[0]
        for entry in generated
    }
    if len(descriptor_namespaces) > 1:
        raise ValueError("generated families require one descriptor namespace")
    descriptor_namespace = next(iter(descriptor_namespaces), "auto_storage")
    body = []
    registered_descriptors = set()
    for entry in generated:
        if entry["shape"] == "transform":
            continue
        family = contract_by_id[entry["id"]]
        descriptor_id = family["station"]["descriptor_id"]
        descriptor_path = descriptor_id.split(":", 1)[1]
        descriptor_variable = _family_java_identifier(entry["id"]) + "Descriptor"
        descriptor_item_namespace = descriptor_id.split(":", 1)[0]
        body.append(
            f"        ResourceLocation {descriptor_variable} = id(\"{descriptor_item_namespace}\", \"{descriptor_path}\");"
        )
        if descriptor_id not in registered_descriptors:
            category = (
                "MachineCategory.PROCESS"
                if family["station"]["category"] == "process"
                else "MachineCategory.INSTANT"
            )
            allow_zero = family["station"]["category"] == "instant"
            variants = ",\n                        ".join(
                _render_rate_binding(binding, allow_zero=allow_zero)
                for binding in entry["rate_bindings"]
            )
            body.extend([
                f"        machineDescriptors.register({descriptor_variable}.getPath(), () ->",
                "                MachineDescriptor.installableVariants(",
                f"                        {descriptor_variable},",
                f"                        Component.translatable({_java_string(entry['station_label_key'])}),",
                "                        () -> List.of(",
                f"                        {variants}),",
                f"                        {category},",
                "                        MachineDescriptorApi.MAX_INSTALLED_COUNT,",
                "                        null));",
            ])
            registered_descriptors.add(descriptor_id)
        recipe_type = _java_resource_location(family["recipe_type"])
        recipe_class = source_class_by_binary[family["class"]]
        if entry["shape"] == "single_item_to_item":
            input_call = _recipe_call(entry["bindings"]["input"], "recipe")
            output_call = _recipe_call(entry["bindings"]["output"], "recipe")
            cost_binding = entry["bindings"]["cost"]
            cost_expression = (
                "RecipeFamilyCost.free()"
                if cost_binding["kind"] == "free"
                else "RecipeFamilyCost.stationWork("
                + _recipe_call(cost_binding, "recipe")
                + ")"
            )
            body.extend([
                "        recipeFamilies.register("
                + _java_string(entry["registration_id"].split(":", 1)[1])
                + ", () ->",
                "                RecipeFamilyFactories.singleItemToItem(",
                f"                        {recipe_class}.class,",
                f"                        () -> BuiltInRegistries.RECIPE_TYPE.get({recipe_type}),",
                f"                        {descriptor_variable},",
                f"                        recipe -> {input_call},",
                f"                        (recipe, registries) -> {output_call},",
                f"                        recipe -> {cost_expression},",
                "                        RecipePresentationKind.CRAFTING));",
            ])
        else:
            eligibility_binding = entry["bindings"]["eligibility"]
            plan_binding = entry["bindings"]["plan"]
            cost_binding = entry["bindings"]["cost"]
            body.extend([
                "        recipeFamilies.register("
                + _java_string(entry["registration_id"].split(":", 1)[1])
                + ", () ->",
                "                RecipeFamilyFactories.deterministicResources(",
                f"                        {recipe_class}.class,",
                f"                        () -> BuiltInRegistries.RECIPE_TYPE.get({recipe_type}),",
                f"                        {descriptor_variable},",
                "                        recipe -> "
                f"{eligibility_binding['owner']}.{eligibility_binding['member']}(recipe),",
                "                        (recipe, registries) -> "
                f"{plan_binding['owner']}.{plan_binding['member']}(recipe, registries),",
                "                        recipe -> "
                f"{cost_binding['owner']}.{cost_binding['member']}(recipe),",
                "                        RecipePresentationKind.CRAFTING));",
            ])
    for entry in generated:
        if entry["shape"] != "transform":
            continue
        family = contract_by_id[entry["id"]]
        descriptor_id = family["station"]["descriptor_id"]
        descriptor_path = descriptor_id.split(":", 1)[1]
        descriptor_variable = _family_java_identifier(entry["id"]) + "Descriptor"
        descriptor_item_namespace = descriptor_id.split(":", 1)[0]
        descriptor_item = entry["rate_bindings"][0]["item"]
        descriptor_item_namespace, descriptor_item_path = descriptor_item.split(
            ":", 1
        )
        if descriptor_id not in registered_descriptors:
            category = (
                "MachineCategory.PROCESS"
                if family["station"]["category"] == "process"
                else "MachineCategory.INSTANT"
            )
            allow_zero = family["station"]["category"] == "instant"
            variants = ",\n                        ".join(
                _render_rate_binding(binding, allow_zero=allow_zero)
                for binding in entry["rate_bindings"]
            )
            body.extend([
                f"        ResourceLocation {descriptor_variable} = "
                f"id(\"{descriptor_item_namespace}\", \"{descriptor_path}\");",
                f"        machineDescriptors.register({descriptor_variable}.getPath(), () ->",
                "                MachineDescriptor.installableVariants(",
                f"                        {descriptor_variable},",
                f"                        Component.translatable({_java_string(entry['station_label_key'])}),",
                "                        () -> List.of(",
                f"                        {variants}),",
                f"                        {category},",
                "                        MachineDescriptorApi.MAX_INSTALLED_COUNT,",
                "                        null));",
            ])
            registered_descriptors.add(descriptor_id)
        bindings = entry["bindings"]
        output = bindings["output"]
        output_kind = output["kind"].split(":", 1)
        output_resource = output["resource"].split(":", 1)
        station = bindings["station"]
        station_expr = (
            f"id(\"{station.split(':', 1)[0]}\", \"{station.split(':', 1)[1]}\")"
            if station is not None
            else "null"
        )
        station_work = bindings["station_work_per_item"]
        retained_expr = "List.of()"
        if bindings["retained_items"]:
            retained_expr = "List.of(" + ", ".join(
                "new ItemStack(requiredItem("
                + _java_resource_location(item["item"])
                + f"), {item['count']})"
                for item in bindings["retained_items"]
            ) + ")"
        input_checks = " || ".join(
            "stack.is(requiredItem(" + _java_resource_location(item) + "))"
            for item in bindings["input_items"]
        )
        target = bindings["output"]["kind"]
        target_namespace, target_path = target.split(":", 1)
        body.extend([
            f"        transformProviders.register({descriptor_variable}.getPath(), () ->",
            "                TransformProvider.of(",
            f"                        id(\"{target_namespace}\", \"{target_path}\"),",
            f"                        new ItemStack(requiredItem("
            f"id(\"{descriptor_item_namespace}\", \"{descriptor_item_path}\"))),",
            f"                        Component.translatable({_java_string(bindings['target_label_key'])}),",
            f"                        Component.translatable({_java_string(bindings['source_label_key'])}),",
            "                        stack -> {",
            f"                            if (!({input_checks})) return null;",
            "                            return new TransformProviderApi.Result(",
            f"                                    StorageResourceKey.of("
            f"id(\"{output_kind[0]}\", \"{output_kind[1]}\"), "
            f"id(\"{output_resource[0]}\", \"{output_resource[1]}\"), "
            "new CompoundTag()),",
            f"                                    {bindings['amount_per_item']}L,",
            f"                                    {station_expr},",
            f"                                    {station_work}L,",
            f"                                    {retained_expr}));",
            "                        }));",
        ])
    if boundaries:
        reasons = "; ".join(
            f"{entry['id']}: {entry['reason']}" for entry in boundaries
        )
        body.append(
            "        throw new IllegalStateException("
            + _java_string("Compat generation RED boundary: " + reasons)
            + ");"
        )
    source = f'''package {plan['package']};

import com.swear.autostorage.MachineCategory;
import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.MachineVariant;
import com.swear.autostorage.MachineWorkRate;
import com.swear.autostorage.RecipeFamily;
import com.swear.autostorage.RecipeFamilyApi;
import com.swear.autostorage.RecipeFamilyCost;
import com.swear.autostorage.RecipeFamilyFactories;
import com.swear.autostorage.RecipePresentationKind;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.TransformProvider;
import com.swear.autostorage.TransformProviderApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class {plan['class_name']} {{
    private {plan['class_name']}() {{
    }}

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies,
            DeferredRegister<TransformProvider> transformProviders
    ) {{
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        Objects.requireNonNull(transformProviders, "transformProviders");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {{
            throw new IllegalArgumentException("Generated descriptor register targets the wrong registry");
        }}
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {{
            throw new IllegalArgumentException("Generated family register targets the wrong registry");
        }}
        if (!transformProviders.getRegistryKey().equals(TransformProviderApi.REGISTRY_KEY)) {{
            throw new IllegalArgumentException("Generated transform provider register targets the wrong registry");
        }}
        if (!machineDescriptors.getNamespace().equals(recipeFamilies.getNamespace())
                || !machineDescriptors.getNamespace().equals(transformProviders.getNamespace())) {{
            throw new IllegalArgumentException("Generated descriptors, families, and transform providers must share one namespace");
        }}
        if (!machineDescriptors.getNamespace().equals("{descriptor_namespace}")) {{
            throw new IllegalArgumentException("Generated descriptor namespace must be {descriptor_namespace}");
        }}
{chr(10).join(body)}
    }}

    private static long exactPositiveIntegral(Number value, String name) {{
        Objects.requireNonNull(value, name);
        try {{
            long exact = new BigDecimal(value.toString()).longValueExact();
            if (exact <= 0) throw new ArithmeticException();
            return exact;
        }} catch (NumberFormatException | ArithmeticException error) {{
            throw new IllegalStateException(name + " must be an exact positive integer: " + value, error);
        }}
    }}

    private static Item requiredItem(ResourceLocation id) {{
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) throw new IllegalStateException("Missing station item " + id);
        return item;
    }}

    private static Block requiredBlock(ResourceLocation id) {{
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block == Blocks.AIR) throw new IllegalStateException("Missing station block " + id);
        return block;
    }}

    private static ResourceLocation id(String namespace, String path) {{
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }}
}}
'''
    return source, boundaries


def generate_compatibility(
    contract: dict,
    audit: dict,
    plan: dict,
    output,
    *,
    source_artifact,
    source_classpath=(),
) -> list[Path]:
    validate_contract(
        contract,
        require_complete=True,
        source_audit=audit,
        source_artifact=source_artifact,
        source_classpath=source_classpath,
    )
    _validate_generation_plan(plan, contract)
    source, boundaries = _generated_compat_java(contract, audit, plan)
    package_path = plan["package"].replace(".", "/")
    files = {}
    if any(entry["status"] == "generate" for entry in plan["families"]):
        files[
            f"src/main/java/{package_path}/{plan['class_name']}.java"
        ] = source.encode("utf-8")
    if boundaries:
        files["RED_BOUNDARIES.md"] = (
            "# Unsupported compatibility boundaries\n\n"
            + "\n".join(
                f"- `{entry['id']}`: {entry['reason']}" for entry in boundaries
            )
            + "\n"
        ).encode("utf-8")
    files["generation-plan.json"] = canonical_json(plan).encode("utf-8")
    return _materialize(
        Path(output),
        files,
        ".compat-kit-generation-manifest.json",
        contract,
    )


def _validate_conformance_plan(plan: dict, contract: dict):
    if not isinstance(plan, dict) or set(plan) != {
        "schema",
        "kind",
        "source_contract_digest",
        "target",
        "package",
        "class_name",
        "game_test_namespace",
        "families",
    }:
        raise ValueError("conformance plan has invalid fields")
    if plan["schema"] != 1 or plan["kind"] != "auto_storage_compat_conformance_plan":
        raise ValueError("conformance plan has invalid identity")
    if plan["source_contract_digest"] != _contract_sha256(contract):
        raise ValueError("conformance plan contract digest does not match")
    expected_target = {
        key: contract["target"][key]
        for key in ("mod_id", "display_name", "version")
    }
    if plan["target"] != expected_target:
        raise ValueError("conformance plan target does not match contract")
    if not _is_java_type(plan["package"]):
        raise ValueError("conformance plan has invalid package")
    _validate_generated_class_name(
        plan["class_name"],
        CONFORMANCE_RENDERER_TYPES,
        "conformance plan",
    )
    if (
        not isinstance(plan["game_test_namespace"], str)
        or not GAME_TEST_NAMESPACE.fullmatch(plan["game_test_namespace"])
    ):
        raise ValueError("conformance plan has invalid game_test_namespace")
    accepted_ids = {
        family["id"]
        for family in contract["families"]
        if family["status"] == "accepted"
    }
    if not accepted_ids:
        raise ValueError(
            "conformance plan requires at least one accepted contract family"
        )
    families = plan["families"]
    if not isinstance(families, list):
        raise ValueError(
            "conformance plan families must match accepted contract families"
        )
    family_ids = [
        family.get("id") for family in families if isinstance(family, dict)
    ]
    if len(family_ids) != len(families) or len(family_ids) != len(set(family_ids)):
        raise ValueError("conformance plan has duplicate family IDs")
    if set(family_ids) != accepted_ids:
        raise ValueError(
            "conformance plan families must match accepted contract families"
        )
    for index, family in enumerate(families):
        location = f"conformance family {index}"
        if not isinstance(family, dict) or set(family) != {
            "id",
            "sample_recipe_id",
            "provider",
            "batch",
            "expected_deltas",
        }:
            raise ValueError(f"{location} has invalid fields")
        if not isinstance(family["sample_recipe_id"], str) or not RESOURCE_LOCATION.fullmatch(
            family["sample_recipe_id"]
        ):
            raise ValueError(f"{location} has invalid sample_recipe_id")
        provider = family["provider"]
        if not isinstance(provider, dict) or set(provider) != {
            "owner",
            "factory_member",
        }:
            raise ValueError(f"{location} provider is invalid")
        if not _is_java_type(provider["owner"]):
            raise ValueError(f"{location} has invalid provider owner")
        if not _is_java_member(provider["factory_member"]):
            raise ValueError(f"{location} has invalid provider factory_member")
        _validate_positive_long(family["batch"], f"{location} batch")
        if family["batch"] < 2:
            raise ValueError(f"{location} batch must be at least 2")
        deltas = family["expected_deltas"]
        if not isinstance(deltas, dict) or set(deltas) != {
            "happy",
            "catalyst_tool_remainder",
            "multi_output",
        }:
            raise ValueError(f"{location} expected_deltas are invalid")
        for mode, delta in deltas.items():
            if (
                not isinstance(delta, dict)
                or not delta
                or list(delta) != sorted(delta)
            ):
                raise ValueError(
                    f"{location} expected_deltas {mode} must be sorted and non-empty"
                )
            for key, value in delta.items():
                if not isinstance(key, str) or not key:
                    raise ValueError(
                        f"{location} expected_deltas {mode} has invalid key"
                    )
                if (
                    isinstance(value, bool)
                    or not isinstance(value, int)
                    or value == 0
                    or abs(value) > 9_223_372_036_854_775_807
                ):
                    raise ValueError(
                        f"{location} expected_deltas {mode} has invalid amount"
                    )
                if (
                    mode == "happy"
                    and abs(value)
                    > 9_223_372_036_854_775_807 // family["batch"]
                ):
                    raise ValueError(
                        f"{location} expected_deltas happy batch product "
                        "overflows signed long"
                    )
    return plan


def _conformance_harness_java(package_name: str) -> str:
    return f'''package {package_name};

import net.minecraft.gametest.framework.GameTestHelper;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CompatibilityConformanceHarness {{
    public enum Mode {{
        HAPPY,
        ONE_SHORT,
        DESTINATION_FULL,
        CHECKED_OVERFLOW,
        STALE_HOLDER,
        CATALYST_TOOL_REMAINDER,
        MULTI_OUTPUT,
        MIXED_RESOURCE_ROLLBACK
    }}

    public record Snapshot(Map<String, Long> amounts) {{
        public Snapshot {{
            amounts = Map.copyOf(amounts);
        }}
    }}

    public record Attempt(boolean success) {{
    }}

    public interface Scenario {{
        void reset();

        void configure(Mode mode);

        Snapshot snapshot();

        Attempt attempt(long crafts);

        boolean coexistenceHealthy();
    }}

    private CompatibilityConformanceHarness() {{
    }}

    public static void assertDelta(
            GameTestHelper helper,
            Snapshot before,
            Snapshot after,
            Map<String, Long> perCraft,
            long crafts
    ) {{
        Map<String, Long> expected = new LinkedHashMap<>(before.amounts());
        for (Map.Entry<String, Long> entry : perCraft.entrySet()) {{
            long delta = Math.multiplyExact(entry.getValue(), crafts);
            expected.merge(entry.getKey(), delta, Math::addExact);
        }}
        expected.values().removeIf(value -> value == 0L);
        Map<String, Long> actual = new LinkedHashMap<>(after.amounts());
        actual.values().removeIf(value -> value == 0L);
        if (!expected.equals(actual)) {{
            helper.fail("Conformance delta mismatch: expected " + expected
                    + " but was " + actual);
        }}
    }}

    public static void assertUnchanged(
            GameTestHelper helper,
            Snapshot before,
            Snapshot after
    ) {{
        if (!before.equals(after)) {{
            helper.fail("Atomic rollback mismatch: before " + before
                    + " after " + after);
        }}
    }}

    public static void requireSuccess(GameTestHelper helper, Attempt attempt) {{
        if (!attempt.success()) helper.fail("Expected conformance craft success");
    }}

    public static void requireFailure(GameTestHelper helper, Attempt attempt) {{
        if (attempt.success()) helper.fail("Expected conformance craft rejection");
    }}
}}
'''


def _java_map_entries(delta: dict[str, int]) -> str:
    entries = ",\n                    ".join(
        f'Map.entry({_java_string(key)}, {value}L)'
        for key, value in delta.items()
    )
    return f"Map.ofEntries(\n                    {entries})"


def _conformance_tests_java(plan: dict) -> str:
    cases = []
    for index, family in enumerate(plan["families"]):
        name = _family_java_identifier(family["id"].lower())
        provider = family["provider"]
        factory = (
            f"{provider['owner']}.{provider['factory_member']}(helper, "
            f"ResourceLocation.parse({_java_string(family['sample_recipe_id'])}))"
        )
        happy_delta = _java_map_entries(family["expected_deltas"]["happy"])
        catalyst_delta = _java_map_entries(
            family["expected_deltas"]["catalyst_tool_remainder"]
        )
        multi_output_delta = _java_map_entries(
            family["expected_deltas"]["multi_output"]
        )
        batch = family["batch"]
        cases.append(f'''
    private static CompatibilityConformanceHarness.Scenario scenario{index}(
            GameTestHelper helper
    ) {{
        return {factory};
    }}

    private static Map<String, Long> expectedDelta{index}Happy() {{
        return {happy_delta};
    }}

    private static Map<String, Long> expectedDelta{index}CatalystToolRemainder() {{
        return {catalyst_delta};
    }}

    private static Map<String, Long> expectedDelta{index}MultiOutput() {{
        return {multi_output_delta};
    }}

    @GameTest(template = "empty")
    public static void {name}_happy_path_and_batching(GameTestHelper helper) {{
        var scenario = scenario{index}(helper);
        scenario.reset();
        scenario.configure(CompatibilityConformanceHarness.Mode.HAPPY);
        var before = scenario.snapshot();
        CompatibilityConformanceHarness.requireSuccess(helper, scenario.attempt(1L));
        CompatibilityConformanceHarness.assertDelta(
                helper, before, scenario.snapshot(), expectedDelta{index}Happy(), 1L);
        scenario.reset();
        scenario.configure(CompatibilityConformanceHarness.Mode.HAPPY);
        before = scenario.snapshot();
        CompatibilityConformanceHarness.requireSuccess(helper, scenario.attempt({batch}L));
        CompatibilityConformanceHarness.assertDelta(
                helper, before, scenario.snapshot(), expectedDelta{index}Happy(), {batch}L);
        helper.succeed();
    }}

    @GameTest(template = "empty")
    public static void {name}_one_short_shortage_is_atomic(GameTestHelper helper) {{
        assertAtomic(helper, scenario{index}(helper),
                CompatibilityConformanceHarness.Mode.ONE_SHORT);
    }}

    @GameTest(template = "empty")
    public static void {name}_destination_capacity_is_atomic(GameTestHelper helper) {{
        assertAtomic(helper, scenario{index}(helper),
                CompatibilityConformanceHarness.Mode.DESTINATION_FULL);
    }}

    @GameTest(template = "empty")
    public static void {name}_checked_overflow_is_atomic(GameTestHelper helper) {{
        assertAtomic(helper, scenario{index}(helper),
                CompatibilityConformanceHarness.Mode.CHECKED_OVERFLOW);
    }}

    @GameTest(template = "empty")
    public static void {name}_stale_holder_is_atomic(GameTestHelper helper) {{
        assertAtomic(helper, scenario{index}(helper),
                CompatibilityConformanceHarness.Mode.STALE_HOLDER);
    }}

    @GameTest(template = "empty")
    public static void {name}_catalyst_tool_remainder_is_exact(GameTestHelper helper) {{
        assertExact(helper, scenario{index}(helper),
                CompatibilityConformanceHarness.Mode.CATALYST_TOOL_REMAINDER,
                expectedDelta{index}CatalystToolRemainder());
    }}

    @GameTest(template = "empty")
    public static void {name}_multi_output_merge_is_exact(GameTestHelper helper) {{
        assertExact(helper, scenario{index}(helper),
                CompatibilityConformanceHarness.Mode.MULTI_OUTPUT,
                expectedDelta{index}MultiOutput());
    }}

    @GameTest(template = "empty")
    public static void {name}_mixed_resource_rollback_is_atomic(GameTestHelper helper) {{
        assertAtomic(helper, scenario{index}(helper),
                CompatibilityConformanceHarness.Mode.MIXED_RESOURCE_ROLLBACK);
    }}

    @GameTest(template = "empty")
    public static void {name}_dedicated_server_client_isolation(GameTestHelper helper) {{
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) helper.fail("Conformance requires a dedicated server");
        helper.succeed();
    }}

    @GameTest(template = "empty")
    public static void {name}_all_mod_coexistence(GameTestHelper helper) {{
        var scenario = scenario{index}(helper);
        if (!scenario.coexistenceHealthy()) helper.fail("Compatibility coexistence failed");
        helper.succeed();
    }}
''')
    return f'''package {plan['package']};

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.Map;

@GameTestHolder("{plan['game_test_namespace']}")
public final class {plan['class_name']} {{
    private {plan['class_name']}() {{
    }}

    private static void assertAtomic(
            GameTestHelper helper,
            CompatibilityConformanceHarness.Scenario scenario,
            CompatibilityConformanceHarness.Mode mode
    ) {{
        scenario.reset();
        scenario.configure(mode);
        var before = scenario.snapshot();
        CompatibilityConformanceHarness.requireFailure(helper, scenario.attempt(1L));
        CompatibilityConformanceHarness.assertUnchanged(helper, before, scenario.snapshot());
        helper.succeed();
    }}

    private static void assertExact(
            GameTestHelper helper,
            CompatibilityConformanceHarness.Scenario scenario,
            CompatibilityConformanceHarness.Mode mode,
            Map<String, Long> expectedDelta
    ) {{
        scenario.reset();
        scenario.configure(mode);
        var before = scenario.snapshot();
        CompatibilityConformanceHarness.requireSuccess(helper, scenario.attempt(1L));
        CompatibilityConformanceHarness.assertDelta(
                helper, before, scenario.snapshot(), expectedDelta, 1L);
        helper.succeed();
    }}
{''.join(cases)}
}}
'''


def scaffold_conformance_tests(
    contract: dict,
    audit: dict,
    plan: dict,
    output,
    *,
    source_artifact,
    source_classpath=(),
) -> list[Path]:
    validate_contract(
        contract,
        require_complete=True,
        source_audit=audit,
        source_artifact=source_artifact,
        source_classpath=source_classpath,
    )
    _validate_conformance_plan(plan, contract)
    package_path = plan["package"].replace(".", "/")
    files = {
        (
            f"src/main/java/{package_path}/"
            "CompatibilityConformanceHarness.java"
        ): _conformance_harness_java(plan["package"]).encode("utf-8"),
        (
            f"src/main/java/{package_path}/{plan['class_name']}.java"
        ): _conformance_tests_java(plan).encode("utf-8"),
        "conformance-plan.json": canonical_json(plan).encode("utf-8"),
    }
    return _materialize(
        Path(output),
        files,
        ".compat-kit-conformance-manifest.json",
        contract,
    )


STANDARD_RESOURCE_KIND_IDS = frozenset({
    "auto_storage:item",
    "auto_storage:fluid",
    "auto_storage:neoforge_energy",
    "auto_storage:work",
})


def _validate_resource_plan(plan: dict, contract: dict):
    if not isinstance(plan, dict) or set(plan) != {
        "schema",
        "kind",
        "source_contract_digest",
        "target",
        "package",
        "class_name",
        "game_test_namespace",
        "resources",
    }:
        raise ValueError("resource plan has invalid fields")
    if plan["schema"] != 1 or plan["kind"] != "auto_storage_compat_resource_plan":
        raise ValueError("resource plan has invalid identity")
    if plan["source_contract_digest"] != _contract_sha256(contract):
        raise ValueError("resource plan contract digest does not match")
    expected_target = {
        key: contract["target"][key]
        for key in ("mod_id", "display_name", "version")
    }
    if plan["target"] != expected_target:
        raise ValueError("resource plan target does not match contract")
    if not _is_java_type(plan["package"]):
        raise ValueError("resource plan has invalid package")
    _validate_generated_class_name(
        plan["class_name"],
        RESOURCE_RENDERER_TYPES,
        "resource plan",
    )
    if (
        not isinstance(plan["game_test_namespace"], str)
        or not GAME_TEST_NAMESPACE.fullmatch(plan["game_test_namespace"])
    ):
        raise ValueError("resource plan has invalid game_test_namespace")
    resources = plan["resources"]
    if not isinstance(resources, list) or not resources:
        raise ValueError("resource plan requires resources")
    seen_ids = set()
    seen_bridges = set()
    seen_snapshot_keys = set()
    generated_types = {plan["class_name"], plan["class_name"] + "GameTests"}
    generated_suffixes = set()
    generated_constants = set()
    for index, resource in enumerate(resources):
        location = f"resource plan entry {index}"
        if not isinstance(resource, dict) or set(resource) != {
            "id",
            "representative_item",
            "variant_aware",
            "bridge_name",
            "snapshot_key",
            "sample_amount",
            "test_provider",
        }:
            raise ValueError(f"{location} has invalid fields")
        resource_id = resource["id"]
        if not isinstance(resource_id, str) or not RESOURCE_LOCATION.fullmatch(resource_id):
            raise ValueError(f"{location} has invalid id")
        if resource_id in STANDARD_RESOURCE_KIND_IDS:
            raise ValueError(f"{location} must reuse standard resource kind {resource_id}")
        if resource_id in seen_ids:
            raise ValueError(f"{location} repeats id")
        seen_ids.add(resource_id)
        representative = resource["representative_item"]
        if not isinstance(representative, str) or not RESOURCE_LOCATION.fullmatch(
            representative
        ):
            raise ValueError(f"{location} has invalid representative_item")
        if not isinstance(resource["variant_aware"], bool):
            raise ValueError(f"{location} variant_aware must be boolean")
        bridge_name = resource["bridge_name"]
        if not _is_java_member(bridge_name):
            raise ValueError(f"{location} has invalid bridge_name")
        if bridge_name in RESOURCE_BRIDGE_RENDERER_TYPES:
            raise ValueError(f"{location} uses a reserved generated class")
        if bridge_name in seen_bridges:
            raise ValueError(f"{location} repeats bridge_name")
        if bridge_name in generated_types:
            raise ValueError(f"{location} has generated class collision")
        seen_bridges.add(bridge_name)
        generated_types.add(bridge_name)
        snapshot_key = resource["snapshot_key"]
        if not isinstance(snapshot_key, str) or not snapshot_key:
            raise ValueError(f"{location} has invalid snapshot_key")
        if snapshot_key in seen_snapshot_keys:
            raise ValueError(f"{location} repeats snapshot_key")
        seen_snapshot_keys.add(snapshot_key)
        _validate_positive_long(resource["sample_amount"], f"{location} sample_amount")
        suffix, constant, _ = _resource_java_names(resource_id)
        if (
            suffix in generated_suffixes
            or constant in generated_constants
        ):
            raise ValueError(f"{location} has generated name collision")
        generated_suffixes.add(suffix)
        generated_constants.add(constant)
        provider = resource["test_provider"]
        if not isinstance(provider, dict) or set(provider) != {
            "owner",
            "factory_member",
        }:
            raise ValueError(f"{location} test_provider is invalid")
        if not _is_java_type(provider["owner"]):
            raise ValueError(f"{location} has invalid test provider owner")
        if not _is_java_member(provider["factory_member"]):
            raise ValueError(f"{location} has invalid test provider factory_member")
    return plan


def _resource_bridge_java(package_name: str, bridge_name: str) -> str:
    return f'''package {package_name};

import com.swear.autostorage.StorageResourceContainerStrategy;
import com.swear.autostorage.StorageResourceHandler;
import com.swear.autostorage.StorageResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;

public interface {bridge_name}<C> {{
    Optional<StorageResourceContainerStrategy.Transfer> planDeposit(
            ItemStack singleContainer,
            HolderLookup.Provider registries
    );

    Optional<StorageResourceContainerStrategy.Transfer> planWithdraw(
            ItemStack singleContainer,
            StorageResourceKey key,
            long maxAmount,
            HolderLookup.Provider registries
    );

    Optional<StorageResourceHandler> find(
            Level level,
            BlockPos pos,
            Direction side
    );

    boolean render(
            C context,
            StorageResourceKey key,
            long amount,
            int x,
            int y,
            float partialTick
    );
}}
'''


def _resource_registration_java(plan: dict) -> str:
    methods = []
    for index, resource in enumerate(plan["resources"]):
        suffix, constant, _ = _resource_java_names(resource["id"])
        kind_id = _java_resource_location(resource["id"])
        representative_id = _java_resource_location(resource["representative_item"])
        bridge = resource["bridge_name"]
        factory = "variantAware" if resource["variant_aware"] else "variantless"
        methods.append(f'''
    public static final ResourceLocation {constant} =
            {kind_id};

    public static StorageResourceKind {suffix}Kind() {{
        return StorageResourceKind.{factory}(() ->
                new ItemStack(requiredItem({representative_id})));
    }}

    public static <C> StorageResourceContainerStrategy {suffix}Containers(
            {bridge}<C> bridge
    ) {{
        Objects.requireNonNull(bridge, "bridge");
        return new StorageResourceContainerStrategy() {{
            @Override
            public ResourceLocation kindId() {{
                return {kind_id};
            }}

            @Override
            public Optional<Transfer> planDeposit(
                    ItemStack singleContainer,
                    HolderLookup.Provider registries
            ) {{
                return bridge.planDeposit(singleContainer, registries);
            }}

            @Override
            public Optional<Transfer> planWithdraw(
                    ItemStack singleContainer,
                    StorageResourceKey key,
                    long maxAmount,
                    HolderLookup.Provider registries
            ) {{
                return bridge.planWithdraw(singleContainer, key, maxAmount, registries);
            }}
        }};
    }}

    public static <C> StorageResourceBlockStrategy {suffix}Blocks(
            {bridge}<C> bridge
    ) {{
        Objects.requireNonNull(bridge, "bridge");
        return new StorageResourceBlockStrategy() {{
            @Override
            public ResourceLocation kindId() {{
                return {kind_id};
            }}

            @Override
            public Optional<StorageResourceHandler> find(
                    Level level,
                    BlockPos pos,
                    Direction side
            ) {{
                return bridge.find(level, pos, side);
            }}
        }};
    }}

    public static <C> void register{suffix}Renderer(
            Class<C> contextType,
            {bridge}<C> bridge
    ) {{
        Objects.requireNonNull(bridge, "bridge");
        TerminalResourceRendererApi.register(
                {kind_id}, contextType, bridge::render);
    }}
''')
    return f'''package {plan['package']};

import com.swear.autostorage.StorageResourceBlockStrategy;
import com.swear.autostorage.StorageResourceContainerStrategy;
import com.swear.autostorage.StorageResourceHandler;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.StorageResourceKind;
import com.swear.autostorage.TerminalResourceRendererApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Optional;

public final class {plan['class_name']} {{
    private {plan['class_name']}() {{
    }}
{''.join(methods)}
    private static Item requiredItem(ResourceLocation id) {{
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) throw new IllegalStateException("Missing resource representative " + id);
        return item;
    }}

    private static ResourceLocation id(String namespace, String path) {{
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }}
}}
'''


def _resource_tests_java(plan: dict) -> str:
    tests = []
    for index, resource in enumerate(plan["resources"]):
        _, _, name = _resource_java_names(resource["id"])
        provider = resource["test_provider"]
        create = f"{provider['owner']}.{provider['factory_member']}(helper)"
        snapshot_key = _java_string(resource["snapshot_key"])
        sample_amount = resource["sample_amount"]
        tests.append(f'''
    private static ResourceScenario scenario{index}(GameTestHelper helper) {{
        return {create};
    }}

    @GameTest(template = "empty")
    public static void {name}_persistence_round_trip(GameTestHelper helper) {{
        var scenario = scenario{index}(helper);
        scenario.reset();
        scenario.seed();
        var before = scenario.snapshot();
        if (!Long.valueOf({sample_amount}L).equals(before.amounts().get({snapshot_key}))) {{
            helper.fail("Seeded resource amount mismatch");
        }}
        byte[] saved = Objects.requireNonNull(scenario.save(), "saved resource state");
        scenario.clear();
        if (scenario.snapshot().amounts().containsKey({snapshot_key})) {{
            helper.fail("Resource clear retained seeded key");
        }}
        scenario.load(saved);
        assertUnchanged(helper, before, scenario.snapshot());
        helper.succeed();
    }}

    @GameTest(template = "empty")
    public static void {name}_container_deposit_and_withdraw(GameTestHelper helper) {{
        var scenario = scenario{index}(helper);
        scenario.reset();
        var before = scenario.snapshot();
        if (!scenario.deposit()) helper.fail("Resource container deposit failed");
        assertDelta(helper, before, scenario.snapshot(), {snapshot_key}, {sample_amount}L);
        if (!scenario.withdraw()) helper.fail("Resource container withdrawal failed");
        assertUnchanged(helper, before, scenario.snapshot());
        helper.succeed();
    }}

    @GameTest(template = "empty")
    public static void {name}_mixed_resource_rollback_is_atomic(GameTestHelper helper) {{
        var scenario = scenario{index}(helper);
        scenario.reset();
        scenario.seed();
        var before = scenario.snapshot();
        if (scenario.attemptMixedRollback()) helper.fail("Mixed resource rollback unexpectedly committed");
        if (!before.equals(scenario.snapshot())) helper.fail("Mixed resource rollback mutated state");
        helper.succeed();
    }}

    @GameTest(template = "empty")
    public static void {name}_dedicated_server_client_isolation(GameTestHelper helper) {{
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) helper.fail("Resource conformance requires a dedicated server");
        helper.succeed();
    }}
''')
    return f'''package {plan['package']};

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@GameTestHolder("{plan['game_test_namespace']}")
public final class {plan['class_name']}GameTests {{
    public record Snapshot(Map<String, Long> amounts) {{
        public Snapshot {{
            amounts = Map.copyOf(amounts);
        }}
    }}

    public interface ResourceScenario {{
        void reset();

        void seed();

        Snapshot snapshot();

        byte[] save();

        void clear();

        void load(byte[] saved);

        boolean deposit();

        boolean withdraw();

        boolean attemptMixedRollback();
    }}

    private {plan['class_name']}GameTests() {{
    }}

    private static void assertDelta(
            GameTestHelper helper,
            Snapshot before,
            Snapshot after,
            String key,
            long delta
    ) {{
        Map<String, Long> expected = new LinkedHashMap<>(before.amounts());
        expected.merge(key, delta, Math::addExact);
        expected.values().removeIf(value -> value == 0L);
        Map<String, Long> actual = new LinkedHashMap<>(after.amounts());
        actual.values().removeIf(value -> value == 0L);
        if (!expected.equals(actual)) {{
            helper.fail("Resource delta mismatch: expected " + expected + " but was " + actual);
        }}
    }}

    private static void assertUnchanged(
            GameTestHelper helper,
            Snapshot before,
            Snapshot after
    ) {{
        if (!before.equals(after)) {{
            helper.fail("Resource atomicity mismatch: before " + before + " after " + after);
        }}
    }}
{''.join(tests)}
}}
'''


def scaffold_resource_integration(
    contract: dict,
    audit: dict,
    plan: dict,
    output,
    *,
    source_artifact,
    source_classpath=(),
) -> list[Path]:
    validate_contract(
        contract,
        require_complete=True,
        source_audit=audit,
        source_artifact=source_artifact,
        source_classpath=source_classpath,
    )
    _validate_resource_plan(plan, contract)
    package_path = plan["package"].replace(".", "/")
    files = {
        (
            f"src/main/java/{package_path}/{plan['class_name']}.java"
        ): _resource_registration_java(plan).encode("utf-8"),
        (
            f"src/main/java/{package_path}/{plan['class_name']}GameTests.java"
        ): _resource_tests_java(plan).encode("utf-8"),
        "resource-plan.json": canonical_json(plan).encode("utf-8"),
    }
    for resource in plan["resources"]:
        files[
            f"src/main/java/{package_path}/{resource['bridge_name']}.java"
        ] = _resource_bridge_java(
            plan["package"], resource["bridge_name"]
        ).encode("utf-8")
    return _materialize(
        Path(output),
        files,
        ".compat-kit-resource-manifest.json",
        contract,
    )


def _read_json(path) -> dict:
    try:
        return json.loads(Path(path).read_text())
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"failed to read JSON {path}: {error}") from error


def _write_json(path, value):
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(canonical_json(value))


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="compat-kit")
    subparsers = parser.add_subparsers(dest="command", required=True)

    scan = subparsers.add_parser("scan")
    scan.add_argument("--jar", required=True)
    scan.add_argument("--mod-id")
    scan.add_argument("--source")
    scan.add_argument("--classpath", action="append", default=[])
    scan.add_argument("--classpath-dependency", action="append", default=[])
    scan.add_argument("--data-root", action="append", default=[])
    scan.add_argument("--output", required=True)
    scan.add_argument("--cache", default="build/compat-kit/cache")

    decide = subparsers.add_parser("decide")
    decide.add_argument("audit")
    decide.add_argument("--output", required=True)
    decide.add_argument("--next-actions", required=True)

    propose = subparsers.add_parser("propose")
    propose.add_argument("audit")
    propose.add_argument("--output", required=True)

    migrate = subparsers.add_parser("migrate-audit")
    migrate.add_argument("audit")
    migrate.add_argument("--jar", required=True)
    migrate.add_argument("--source")
    migrate.add_argument("--classpath", action="append", default=[])
    migrate.add_argument("--classpath-dependency", action="append", default=[])
    migrate.add_argument("--data-root", action="append", default=[])
    migrate.add_argument("--output", required=True)
    migrate.add_argument("--cache", default="build/compat-kit/cache")

    migrate_contract_parser = subparsers.add_parser("migrate-contract")
    migrate_contract_parser.add_argument("contract")
    migrate_contract_parser.add_argument("--old-audit", required=True)
    migrate_contract_parser.add_argument("--new-audit", required=True)
    migrate_contract_parser.add_argument("--output", required=True)
    migrate_contract_parser.add_argument("--next-actions", required=True)

    probe = subparsers.add_parser("probe")
    probe.add_argument("audit")
    probe.add_argument("--plan")
    probe.add_argument("--game-test-namespace", required=True)
    probe.add_argument("--output", required=True)

    validate_probe = subparsers.add_parser("validate-probe")
    validate_probe.add_argument("output")
    validate_probe.add_argument("--audit", required=True)
    validate_probe.add_argument("--plan")

    worker = subparsers.add_parser("worker-package")
    worker.add_argument("contract")
    worker.add_argument("--audit", required=True)
    worker.add_argument("--output", required=True)

    generate = subparsers.add_parser("generate")
    generate.add_argument("contract")
    generate.add_argument("--audit", required=True)
    generate.add_argument("--jar", required=True)
    generate.add_argument("--classpath", action="append", default=[])
    generate.add_argument("--plan", required=True)
    generate.add_argument("--output", required=True)

    conformance = subparsers.add_parser("conformance")
    conformance.add_argument("contract")
    conformance.add_argument("--audit", required=True)
    conformance.add_argument("--jar", required=True)
    conformance.add_argument("--classpath", action="append", default=[])
    conformance.add_argument("--plan", required=True)
    conformance.add_argument("--output", required=True)

    resource = subparsers.add_parser("resource-scaffold")
    resource.add_argument("contract")
    resource.add_argument("--audit", required=True)
    resource.add_argument("--jar", required=True)
    resource.add_argument("--classpath", action="append", default=[])
    resource.add_argument("--plan", required=True)
    resource.add_argument("--output", required=True)

    delta = subparsers.add_parser("diff")
    delta.add_argument("old_audit")
    delta.add_argument("new_target")
    delta.add_argument("--source")
    delta.add_argument("--classpath", action="append", default=[])
    delta.add_argument("--classpath-dependency", action="append", default=[])
    delta.add_argument("--data-root", action="append", default=[])
    delta.add_argument("--output", required=True)
    delta.add_argument("--cache", default="build/compat-kit/cache")

    scaffold = subparsers.add_parser("scaffold")
    scaffold_target = scaffold.add_mutually_exclusive_group(required=True)
    scaffold_target.add_argument("--bundled")
    scaffold_target.add_argument("--addon")
    scaffold.add_argument("--output")
    scaffold.add_argument("--audit", required=True)
    scaffold.add_argument("--jar", required=True)
    scaffold.add_argument("--classpath", action="append", default=[])

    verify = subparsers.add_parser("verify")
    verify.add_argument("contract")
    verify.add_argument("--audit", required=True)
    verify.add_argument("--jar", required=True)
    verify.add_argument("--classpath", action="append", default=[])
    verify_target = verify.add_mutually_exclusive_group(required=True)
    verify_target.add_argument("--bundled", nargs="?", const=".")
    verify_target.add_argument("--addon")
    verify.add_argument("--output", default="build/compat-kit/report.json")

    publish = subparsers.add_parser("publish")
    publish.add_argument("--output", required=True)
    publish.add_argument("--version", required=True)

    normalize = subparsers.add_parser("normalize-jar")
    normalize.add_argument("source")
    normalize.add_argument("output")
    transform_runtime = subparsers.add_parser("transform-runtime-artifact")
    transform_runtime.add_argument("source")
    transform_runtime.add_argument("output")
    transform_runtime.add_argument("--expected-sha256", required=True)
    transform_runtime.add_argument("--remove-entry", action="append", required=True)
    return parser


def main(argv=None) -> int:
    parser = _build_parser()
    args = parser.parse_args(argv)
    try:
        if args.command == "scan":
            audit = scan_jar(
                args.jar,
                selected_mod_id=args.mod_id,
                source=args.source,
                classpath=args.classpath,
                classpath_dependencies=args.classpath_dependency,
                cache_dir=args.cache,
                data_roots=args.data_root,
            )
            _write_json(args.output, audit)
        elif args.command == "decide":
            contract, actions = decide_audit(_read_json(args.audit))
            _write_json(args.output, contract)
            action_path = Path(args.next_actions)
            action_path.parent.mkdir(parents=True, exist_ok=True)
            action_path.write_text(actions)
        elif args.command == "propose":
            _write_json(args.output, propose_audit(_read_json(args.audit)))
        elif args.command == "migrate-audit":
            _write_json(
                args.output,
                migrate_audit(
                    _read_json(args.audit),
                    args.jar,
                    source=args.source,
                    classpath=args.classpath,
                    classpath_dependencies=args.classpath_dependency,
                    cache_dir=args.cache,
                    data_roots=args.data_root,
                ),
            )
        elif args.command == "migrate-contract":
            contract, actions = migrate_contract(
                _read_json(args.contract),
                _read_json(args.old_audit),
                _read_json(args.new_audit),
            )
            _write_json(args.output, contract)
            action_path = Path(args.next_actions)
            action_path.parent.mkdir(parents=True, exist_ok=True)
            action_path.write_text(actions)
        elif args.command == "probe":
            scaffold_runtime_probe(
                _read_json(args.audit),
                args.output,
                game_test_namespace=args.game_test_namespace,
                plan=_read_json(args.plan) if args.plan else None,
            )
        elif args.command == "validate-probe":
            validate_runtime_probe_output(
                _read_json(args.output),
                _read_json(args.audit),
                plan=_read_json(args.plan) if args.plan else None,
            )
        elif args.command == "worker-package":
            worker_package(
                _read_json(args.contract),
                _read_json(args.audit),
                args.output,
                audit_path=args.audit,
            )
        elif args.command == "generate":
            generate_compatibility(
                _read_json(args.contract),
                _read_json(args.audit),
                _read_json(args.plan),
                args.output,
                source_artifact=args.jar,
                source_classpath=args.classpath,
            )
        elif args.command == "conformance":
            scaffold_conformance_tests(
                _read_json(args.contract),
                _read_json(args.audit),
                _read_json(args.plan),
                args.output,
                source_artifact=args.jar,
                source_classpath=args.classpath,
            )
        elif args.command == "resource-scaffold":
            scaffold_resource_integration(
                _read_json(args.contract),
                _read_json(args.audit),
                _read_json(args.plan),
                args.output,
                source_artifact=args.jar,
                source_classpath=args.classpath,
            )
        elif args.command == "diff":
            old = _read_json(args.old_audit)
            new_path = Path(args.new_target)
            if new_path.suffix == ".json":
                new = _read_json(new_path)
            else:
                _validate_audit(old)
                new = scan_jar(
                    new_path,
                    selected_mod_id=old["target"]["mod_id"],
                    source=args.source,
                    classpath=args.classpath,
                    classpath_dependencies=args.classpath_dependency,
                    cache_dir=args.cache,
                    data_roots=args.data_root,
                )
            _write_json(args.output, diff_audits(old, new))
        elif args.command == "scaffold":
            contract_path = args.bundled or args.addon
            contract = _read_json(contract_path)
            source_audit = _read_json(args.audit)
            if args.bundled:
                root = Path(args.output) if args.output else Path.cwd()
                scaffold_bundled(
                    contract,
                    root,
                    source_audit=source_audit,
                    source_artifact=args.jar,
                    source_classpath=args.classpath,
                )
            else:
                if not args.output:
                    raise ValueError("scaffold --addon requires --output")
                scaffold_addon(
                    contract,
                    args.output,
                    source_audit=source_audit,
                    source_artifact=args.jar,
                    source_classpath=args.classpath,
                )
        elif args.command == "verify":
            contract = _read_json(args.contract)
            report = verify_contract(
                contract,
                source_audit=_read_json(args.audit),
                source_artifact=args.jar,
                source_classpath=args.classpath,
                bundled_root=args.bundled,
                addon_root=args.addon,
            )
            _write_json(args.output, report)
        elif args.command == "publish":
            publish_archive(args.output, args.version)
        elif args.command == "normalize-jar":
            normalize_jar(args.source, args.output)
        elif args.command == "transform-runtime-artifact":
            transform_runtime_artifact(
                args.source,
                args.output,
                expected_sha256=args.expected_sha256,
                remove_entries=args.remove_entry,
            )
        else:
            parser.error(f"unsupported command: {args.command}")
    except (OSError, RuntimeError, ValueError, zipfile.BadZipFile) as error:
        print(f"compat-kit: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
