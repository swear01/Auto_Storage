#!/usr/bin/env python3

import argparse
import base64
import hashlib
import json
import re
import shutil
import subprocess
import sys
import tomllib
import zipfile
from pathlib import Path


SCHEMA_VERSION = 1
SCAN_CACHE_VERSION = 6
MAX_JAR_BYTES = 512 * 1024 * 1024
MAX_ARCHIVE_ENTRIES = 100_000
MAX_UNCOMPRESSED_BYTES = 2 * 1024 * 1024 * 1024
MAX_MOD_METADATA_BYTES = 1024 * 1024
MAX_CLASS_BYTES = 16 * 1024 * 1024
MAX_CANDIDATE_CLASSES = 2_000
MAX_SIGNATURE_BYTES = 256 * 1024
MAX_PRIVATE_BYTECODE_BYTES = 1024 * 1024
MAX_SOURCE_FILES = 10_000
TOOL_VERSION = "0.3.0"
PUBLISHED_ADDON_EXAMPLE_FILES = (
    "src/main/java/example/autostorage/ExampleAddon.java",
)
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
RISK_PATTERNS = (
    ("chance_output", re.compile(r"\bgetChance(?:\s*\(|:)"), "getChance"),
    ("randomness", re.compile(r"\brandom(?:\s*\(|:)"), "random"),
    ("randomness", re.compile(r"\bRandom(?:Source)?\b"), "Random"),
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
    "source",
    "candidates",
    "risks",
}
CONTRACT_TOP_KEYS = {
    "schema",
    "kind",
    "target",
    "source_audit_sha256",
    "source_recipe_inventory_sha256",
    "families",
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
}
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
JAVA_RESERVED_IDENTIFIERS = frozenset(
    """
    abstract assert boolean break byte case catch char class const continue
    default do double else enum extends final finally float for goto if
    implements import instanceof int interface long native new package private
    protected public return short static strictfp super switch synchronized this
    throw throws transient try void volatile while true false null
    """.split()
)


def canonical_json(value) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def _recipe_inventory_sha256(class_names) -> str:
    return hashlib.sha256(
        canonical_json(sorted(class_names)).encode()
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
):
    try:
        if (
            not path.is_file()
            or path.stat().st_size != expected_size
            or _sha256_file(path) != expected_sha256
        ):
            raise ValueError(f"target jar changed during scan: {path}")
    except OSError as error:
        raise ValueError(f"target jar changed during scan: {path}") from error


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


def _read_mod_metadata(archive: zipfile.ZipFile) -> dict:
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
    if not isinstance(mods, list) or len(mods) != 1:
        raise ValueError("compat-kit requires exactly one mod in NeoForge mod metadata")
    mod = mods[0]
    required = ("modId", "version", "displayName")
    missing = [key for key in required if not isinstance(mod.get(key), str)]
    if missing:
        raise ValueError(
            "NeoForge mod metadata is missing string fields: " + ", ".join(missing)
        )
    return {
        "mod_id": mod["modId"],
        "display_name": mod["displayName"],
        "version": mod["version"],
    }


def _class_name(entry_name: str) -> str:
    return entry_name[:-6].replace("/", ".")


def _class_access_flags(payload: bytes, entry_name: str) -> int:
    if not payload.startswith(b"\xca\xfe\xba\xbe"):
        return 0
    if len(payload) < 10:
        raise ValueError(f"truncated class header: {entry_name}")
    constant_pool_count = int.from_bytes(payload[8:10], "big")
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
            offset += 2 + length
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            offset += 4
        elif tag in (5, 6):
            offset += 8
            index += 1
        elif tag in (7, 8, 16, 19, 20):
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
        raise ValueError(f"truncated class access flags: {entry_name}")
    return int.from_bytes(payload[offset:offset + 2], "big")


def _is_inspectable_class(
    archive: zipfile.ZipFile,
    entry_name: str,
) -> bool:
    if (
        entry_name.startswith("META-INF/versions/")
        or not entry_name.endswith(".class")
        or entry_name.endswith("module-info.class")
    ):
        return False
    nested_segments = _class_name(entry_name).split("$")[1:]
    named = all(
        re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", segment)
        for segment in nested_segments
    )
    if not named:
        return False
    entry = archive.getinfo(entry_name)
    if entry.file_size > MAX_CLASS_BYTES:
        raise ValueError(
            f"class entry exceeds {MAX_CLASS_BYTES} bytes: {entry_name}"
        )
    return not (
        _class_access_flags(archive.read(entry_name), entry_name) & 0x1000
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


def _run_javap(
    jar: Path,
    class_name: str,
    *options: str,
    output_limit: int = MAX_SIGNATURE_BYTES,
    output_label: str = "public signature",
) -> str:
    command = ["javap", *options, "-classpath", str(jar), class_name]
    try:
        result = subprocess.run(
            command,
            capture_output=True,
            check=False,
            text=True,
            timeout=20,
        )
    except FileNotFoundError as error:
        raise RuntimeError(
            "javap was not found; set JAVA_HOME to JDK 21 before scanning"
        ) from error
    except subprocess.TimeoutExpired as error:
        raise RuntimeError(f"javap timed out for {class_name}") from error
    if result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip()
        raise RuntimeError(f"javap failed for {class_name}: {detail}")
    encoded = result.stdout.encode("utf-8")
    if len(encoded) > output_limit:
        raise ValueError(
            f"{output_label} exceeds {output_limit} bytes: {class_name}"
        )
    return result.stdout.strip()


def _source_evidence(source: Path | None, candidate_names: set[str]) -> dict:
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
    source_suffixes = {
        name.split("$", 1)[0].replace(".", "/") + ".java"
        for name in candidate_names
    }
    files = []
    for path in java_files:
        relative = path.relative_to(source).as_posix()
        if (
            path.is_file()
            and not path.is_symlink()
            and any(
                relative == suffix or relative.endswith("/" + suffix)
                for suffix in source_suffixes
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
    if candidate_names and not files:
        raise ValueError("source checkout has no candidate matches")
    return {"revision": revision, "files": files}


def _risk_evidence(candidates: list[dict]) -> list[dict]:
    collected: dict[str, set[str]] = {}
    for candidate in candidates:
        class_name = candidate["class"]
        signature = candidate["public_signature"]
        for code, pattern, label in RISK_PATTERNS:
            if pattern.search(signature):
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
                    evidence = f"{class_name}#{label}"
                else:
                    evidence = f"{class_name}: {label}"
                collected.setdefault(code, set()).add(evidence)
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


def _validate_audit(audit: dict):
    if not isinstance(audit, dict):
        raise ValueError("audit must be a JSON object")
    unknown = sorted(set(audit) - AUDIT_TOP_KEYS)
    if unknown:
        raise ValueError("audit has unknown keys: " + ", ".join(unknown))
    if audit.get("schema") != SCHEMA_VERSION:
        raise ValueError(f"unsupported audit schema: {audit.get('schema')}")
    if audit.get("scanner_format") != SCAN_CACHE_VERSION:
        raise ValueError(
            "unsupported audit scanner format: "
            f"{audit.get('scanner_format')}"
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
    if not isinstance(artifact, dict) or set(artifact) != {"sha256", "size"}:
        raise ValueError("audit artifact requires sha256 and size")
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

    candidates = audit["candidates"]
    candidate_buckets = {
        "recipe_classes",
        "resource_apis",
        "station_classes",
    }
    if not isinstance(candidates, dict) or set(candidates) != candidate_buckets:
        raise ValueError(
            "audit candidates require recipe_classes, resource_apis, "
            "and station_classes"
        )
    seen_classes = set()
    for bucket in sorted(candidate_buckets):
        records = candidates[bucket]
        if not isinstance(records, list):
            raise ValueError(f"audit candidates {bucket} must be a list")
        for index, record in enumerate(records):
            location = f"audit candidates {bucket} {index}"
            if not isinstance(record, dict) or set(record) != {
                "class",
                "public_signature",
            }:
                raise ValueError(
                    f"{location} requires class and public_signature"
                )
            class_name = record["class"]
            if not isinstance(class_name, str) or not re.fullmatch(
                r"[A-Za-z_$][A-Za-z0-9_$.]*",
                class_name,
            ):
                raise ValueError(f"{location} has invalid class")
            expected_bucket = _candidate_bucket(class_name)
            if expected_bucket != bucket:
                raise ValueError(
                    f"{location} candidate bucket mismatch: "
                    f"expected {expected_bucket}"
                )
            if class_name in seen_classes:
                raise ValueError(f"audit repeats candidate class {class_name}")
            seen_classes.add(class_name)
            if (
                not isinstance(record["public_signature"], str)
                or not record["public_signature"].strip()
            ):
                raise ValueError(f"{location} has empty public_signature")

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


def scan_jar(
    jar,
    *,
    source=None,
    cache_dir=None,
    signature_reader=None,
    risk_reader=None,
) -> dict:
    jar = Path(jar).resolve()
    if not jar.is_file():
        raise ValueError(f"target jar does not exist: {jar}")
    artifact_size = jar.stat().st_size
    if artifact_size > MAX_JAR_BYTES:
        raise ValueError(f"target jar exceeds {MAX_JAR_BYTES} bytes: {jar}")
    artifact_sha = _sha256_file(jar)
    cache_path = None
    if cache_dir is not None and source is None:
        cache_path = (
            Path(cache_dir)
            / artifact_sha
            / f"v{SCAN_CACHE_VERSION}"
            / "audit.json"
        )
        if cache_path.is_file():
            cached = json.loads(cache_path.read_text())
            _validate_audit(cached)
            if cached["artifact"]["sha256"] != artifact_sha:
                raise ValueError(f"cached audit SHA mismatch: {cache_path}")
            _require_unchanged_artifact(jar, artifact_sha, artifact_size)
            return cached

    reader = signature_reader or (
        lambda class_name: _run_javap(jar, class_name, "-public")
    )
    private_reader = risk_reader or (
        signature_reader
        if signature_reader is not None
        else lambda class_name: _run_javap(
            jar,
            class_name,
            "-c",
            "-p",
            output_limit=MAX_PRIVATE_BYTECODE_BYTES,
            output_label="private bytecode",
        )
    )
    with zipfile.ZipFile(jar) as archive:
        _validate_archive(jar, archive)
        target = _read_mod_metadata(archive)
        classified = {
            "recipe_classes": [],
            "resource_apis": [],
            "station_classes": [],
        }
        class_names = sorted(
            _class_name(name)
            for name in archive.namelist()
            if _is_inspectable_class(archive, name)
        )
        candidates = [
            (class_name, bucket)
            for class_name in class_names
            if (bucket := _candidate_bucket(class_name)) is not None
        ]
        if len(candidates) > MAX_CANDIDATE_CLASSES:
            raise ValueError(
                f"target jar exceeds {MAX_CANDIDATE_CLASSES} candidate classes"
            )
        risk_candidates = []
        for class_name, bucket in candidates:
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
                    "public_signature": signature.strip(),
                }
            )
            if bucket == "recipe_classes":
                risk_signature = private_reader(class_name)
                if not isinstance(risk_signature, str) or not risk_signature.strip():
                    raise ValueError(f"empty private bytecode for {class_name}")
                if (
                    len(risk_signature.encode("utf-8"))
                    > MAX_PRIVATE_BYTECODE_BYTES
                ):
                    raise ValueError(
                        "private bytecode exceeds "
                        f"{MAX_PRIVATE_BYTECODE_BYTES} bytes: {class_name}"
                    )
                risk_candidates.append(
                    {
                        "class": class_name,
                        "public_signature": risk_signature.strip(),
                    }
                )

    all_candidates = [
        candidate
        for bucket in classified.values()
        for candidate in bucket
    ]
    source_path = Path(source) if source is not None else None
    audit = {
        "schema": SCHEMA_VERSION,
        "scanner_format": SCAN_CACHE_VERSION,
        "kind": "auto_storage_compat_audit",
        "target": target,
        "artifact": {
            "sha256": artifact_sha,
            "size": artifact_size,
        },
        "source": _source_evidence(
            source_path,
            {candidate["class"] for candidate in all_candidates},
        ),
        "candidates": classified,
        "risks": _risk_evidence(risk_candidates),
    }
    _require_unchanged_artifact(jar, artifact_sha, artifact_size)
    _validate_audit(audit)
    if cache_path is not None:
        cache_path.parent.mkdir(parents=True, exist_ok=True)
        cache_path.write_text(canonical_json(audit))
    return audit


def _family_id(class_name: str) -> str:
    simple = class_name.rsplit(".", 1)[-1]
    words = re.sub(r"(?<!^)(?=[A-Z])", "_", simple).lower()
    return re.sub(r"[^a-z0-9_]+", "_", words).strip("_")


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
        "families": families,
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


def _unknown_keys(value: dict, allowed: set[str], location: str):
    unknown = sorted(set(value) - allowed)
    if unknown:
        raise ValueError(f"{location} has unknown keys: {', '.join(unknown)}")


def _validate_nonempty_string(value, location: str):
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{location} must be a non-empty string")


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
    if value["category"] not in ("instant", "process", "transform"):
        raise ValueError(f"{location} station has invalid category")
    variants = value["variants"]
    if not isinstance(variants, list) or not variants:
        raise ValueError(f"{location} station variants must be a non-empty list")
    for index, variant in enumerate(variants):
        variant_location = f"{location} station variant {index}"
        if not isinstance(variant, dict):
            raise ValueError(f"{variant_location} must be an object")
        _unknown_keys(variant, VARIANT_KEYS, variant_location)
        if not {"item", "rate"} <= set(variant):
            raise ValueError(f"{variant_location} requires item and rate")
        _validate_nonempty_string(variant["item"], f"{variant_location} item")
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
        if (
            isinstance(denominator, bool)
            or not isinstance(denominator, int)
            or denominator <= 0
        ):
            raise ValueError(f"{variant_location} rate denominator must be positive")
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
    if "dependency" in target:
        _validate_nonempty_string(target["dependency"], "contract target dependency")
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
        recipe_type = family.get("recipe_type")
        if recipe_type is not None:
            _validate_nonempty_string(recipe_type, f"family {family_id} recipe_type")
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
        audit_target = source_audit["target"]
        for key in ("mod_id", "display_name", "version"):
            if target[key] != audit_target.get(key):
                raise ValueError(f"contract target {key} does not match source audit")
        if source_audit["artifact"]["sha256"] != source_audit_sha256:
            raise ValueError("contract target artifact does not match source audit")
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
    else:
        verification = contract.get("verification")
        if not isinstance(verification, dict) or set(verification) != VERIFICATION_KEYS:
            raise ValueError("draft contract has invalid verification keys")
    return contract


def _pascal(identifier: str) -> str:
    words = re.findall(r"[A-Za-z0-9]+", identifier)
    if not words:
        raise ValueError(f"identifier has no Java-safe characters: {identifier}")
    return "".join(word[:1].upper() + word[1:].lower() for word in words)


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


def _bundled_files(contract: dict) -> dict[str, bytes]:
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
        "requires": [mod_id],
        "side": "both",
        "sourceSet": source_set,
        "fixture": fixture,
        "expectedTests": contract["verification"]["expected_game_tests"],
        "dependencies": [target["dependency"]],
        "runtimeDependencies": [
            target["dependency"],
            *target["runtime_dependencies"],
        ],
        "repositories": target["repositories"],
        "auditArtifact": {
            "dependency": target["dependency"],
            "sha256": contract["source_audit_sha256"],
        },
    }
    module = f"""package {module_package};

import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.RecipeFamily;
import com.swear.autostorage.RecipeFamilyApi;
import com.swear.autostorage.api.AutoStorageApi;
import com.swear.autostorage.api.AutoStorageCompatContext;
import com.swear.autostorage.api.AutoStorageCompatModule;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class {class_prefix}CompatModule implements AutoStorageCompatModule {{
    private static final DeferredRegister<MachineDescriptor> MACHINES =
            MachineDescriptorApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final DeferredRegister<RecipeFamily> RECIPES =
            RecipeFamilyApi.createDeferredRegister(AutoStorageApi.MOD_ID);

    @Override
    public void register(AutoStorageCompatContext context) {{
        {class_prefix}Compat.register(MACHINES, RECIPES);
        context.register(addon -> addon
                .machineDescriptors(MACHINES)
                .recipeFamilies(RECIPES));
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
import net.neoforged.neoforge.registries.DeferredRegister;

public final class {class_prefix}Compat {{
    private {class_prefix}Compat() {{
    }}

    public static void register(
            DeferredRegister<MachineDescriptor> machines,
            DeferredRegister<RecipeFamily> recipes
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
    if target["repositories"]:
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
    target_dependency = _groovy_string(target["dependency"])
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
    version = neo_version

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
    compatKitTargetArtifact({target_dependency})
}}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

def expectedCompatKitTargetSha256 = "{contract['source_audit_sha256']}"
def verifyCompatKitTargetArtifact = tasks.register("verifyCompatKitTargetArtifact") {{
    inputs.files(configurations.compatKitTargetArtifact)
    inputs.property("expectedSha256", expectedCompatKitTargetSha256)
    doLast {{
        def artifacts = inputs.files.files.findAll {{ it.name.endsWith(".jar") }}
        if (artifacts.size() != 1) {{
            throw new GradleException(
                    "Compat Kit target verification expected one resolved jar, found ${{artifacts.size()}}")
        }}
        def digest = java.security.MessageDigest.getInstance("SHA-256")
        artifacts.iterator().next().withInputStream {{ input ->
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
    }}
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
      - run: tools/compat-kit/compat-kit verify compat/contract.json --audit compat/audit.json --addon .
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
    _validate_materialization_root(root)
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


def _validate_materialization_root(root: Path):
    for ancestor in root.absolute().parents:
        if ancestor.is_symlink():
            raise ValueError(
                f"generated path ancestor is a symlink: {ancestor}"
            )
    if root.is_symlink():
        raise ValueError("generated path parent is a symlink: .")
    if root.exists() and not root.is_dir():
        raise ValueError("generated path parent is not a directory: .")


def _validate_bundled_identifier_collisions(
    root: Path,
    generated_descriptor: dict,
    mod_id: str,
):
    _validate_materialization_root(root)
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
        for key in ("id", "entrypoint", "sourceSet", "fixture"):
            if existing.get(key) == generated_descriptor[key]:
                raise ValueError(
                    "bundled compatibility identifier collision: "
                    f"{key} {generated_descriptor[key]}"
                )


def scaffold_bundled(
    contract: dict,
    root,
    *,
    source_audit: dict,
) -> list[Path]:
    validate_contract(
        contract,
        require_complete=True,
        source_audit=source_audit,
    )
    _validate_bundled_verification(contract)
    mod_id = contract["target"]["mod_id"]
    root = Path(root)
    files = _bundled_files(contract)
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
) -> list[Path]:
    validate_contract(
        contract,
        require_complete=True,
        source_audit=source_audit,
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


def _game_test_blocks(text: str) -> list[str]:
    code = _java_code_mask(text)
    blocks = []
    for annotation in re.finditer(r"@GameTest\s*\(", code):
        opening = _game_test_method_opening(code, annotation.end())
        closing = _java_block_end(text, opening)
        blocks.append(text[opening + 1:closing])
    return blocks


def _game_test_task_source_root(
    contract: dict,
    root: Path,
    mode: str,
    task: str,
) -> Path | None:
    if not re.fullmatch(r"run(?:[A-Za-z0-9]+)?GameTestServer", task):
        return None
    if mode == "addon" or task == "runGameTestServer":
        return root / "src/main/java"
    verification = contract["verification"]
    if task == verification["game_test_task"]:
        fixture = verification["fixture"]
    else:
        task_name = task.removeprefix("run").removesuffix("GameTestServer")
        fixture = task_name[0].lower() + task_name[1:] + "Fixture"
    return root / f"src/{fixture}/java"


def _verification_evidence(
    contract: dict,
    root: Path,
    mode: str,
) -> dict[str, list[str]]:
    resolved = {}
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
            task_source_root = _game_test_task_source_root(
                contract,
                root,
                mode,
                task,
            )
            if task_source_root is not None:
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
            if not any(record["marker"] in text for text in matching_texts):
                raise ValueError(
                    f"verification evidence marker not found for {check}: "
                    f"{record['marker']}"
                )
            if re.fullmatch(r"run(?:[A-Za-z0-9]+)?GameTestServer", task) and not any(
                record["marker"] in block
                for text in matching_texts
                for block in _game_test_blocks(text)
            ):
                raise ValueError(
                    "evidence marker is not inside an @GameTest method for "
                    f"{check}: {record['marker']}"
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
    bundled_root=None,
    addon_root=None,
    command_runner=None,
) -> dict:
    validate_contract(
        contract,
        require_complete=True,
        source_audit=source_audit,
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
        _bundled_files(contract)
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
    for schema in sorted((tool_root / "schema").glob("*.json")):
        files[f"schema/{schema.name}"] = schema.read_bytes()
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


def _candidate_map(audit: dict, bucket: str) -> dict[str, str]:
    return {
        candidate["class"]: candidate["public_signature"]
        for candidate in audit["candidates"][bucket]
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
    contract_affected = (
        old["artifact"]["sha256"] != new["artifact"]["sha256"]
    )
    for bucket in ("recipe_classes", "resource_apis", "station_classes"):
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
    return {
        "schema": SCHEMA_VERSION,
        "kind": "auto_storage_compat_delta",
        "mod_id": old["target"]["mod_id"],
        "from_version": old["target"]["version"],
        "to_version": new["target"]["version"],
        **bucket_changes,
        "risks": risk_changes,
        "contract_affected": contract_affected,
    }


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
    scan.add_argument("--source")
    scan.add_argument("--output", required=True)
    scan.add_argument("--cache", default="build/compat-kit/cache")

    decide = subparsers.add_parser("decide")
    decide.add_argument("audit")
    decide.add_argument("--output", required=True)
    decide.add_argument("--next-actions", required=True)

    delta = subparsers.add_parser("diff")
    delta.add_argument("old_audit")
    delta.add_argument("new_target")
    delta.add_argument("--source")
    delta.add_argument("--output", required=True)
    delta.add_argument("--cache", default="build/compat-kit/cache")

    scaffold = subparsers.add_parser("scaffold")
    scaffold_target = scaffold.add_mutually_exclusive_group(required=True)
    scaffold_target.add_argument("--bundled")
    scaffold_target.add_argument("--addon")
    scaffold.add_argument("--output")
    scaffold.add_argument("--audit", required=True)

    verify = subparsers.add_parser("verify")
    verify.add_argument("contract")
    verify.add_argument("--audit", required=True)
    verify_target = verify.add_mutually_exclusive_group(required=True)
    verify_target.add_argument("--bundled", nargs="?", const=".")
    verify_target.add_argument("--addon")
    verify.add_argument("--output", default="build/compat-kit/report.json")

    publish = subparsers.add_parser("publish")
    publish.add_argument("--output", required=True)
    publish.add_argument("--version", required=True)
    return parser


def main(argv=None) -> int:
    parser = _build_parser()
    args = parser.parse_args(argv)
    try:
        if args.command == "scan":
            audit = scan_jar(
                args.jar,
                source=args.source,
                cache_dir=args.cache,
            )
            _write_json(args.output, audit)
        elif args.command == "decide":
            contract, actions = decide_audit(_read_json(args.audit))
            _write_json(args.output, contract)
            action_path = Path(args.next_actions)
            action_path.parent.mkdir(parents=True, exist_ok=True)
            action_path.write_text(actions)
        elif args.command == "diff":
            old = _read_json(args.old_audit)
            new_path = Path(args.new_target)
            if new_path.suffix == ".json":
                new = _read_json(new_path)
            else:
                new = scan_jar(
                    new_path,
                    source=args.source,
                    cache_dir=args.cache,
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
                )
            else:
                if not args.output:
                    raise ValueError("scaffold --addon requires --output")
                scaffold_addon(
                    contract,
                    args.output,
                    source_audit=source_audit,
                )
        elif args.command == "verify":
            contract = _read_json(args.contract)
            report = verify_contract(
                contract,
                source_audit=_read_json(args.audit),
                bundled_root=args.bundled,
                addon_root=args.addon,
            )
            _write_json(args.output, report)
        elif args.command == "publish":
            publish_archive(args.output, args.version)
        else:
            parser.error(f"unsupported command: {args.command}")
    except (OSError, RuntimeError, ValueError, zipfile.BadZipFile) as error:
        print(f"compat-kit: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
