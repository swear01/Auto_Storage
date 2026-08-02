#!/usr/bin/env python3
"""Deterministic compatibility-matrix assertion manifest generator."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any


SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
RESOURCE_ID_RE = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")
MOD_ID_RE = re.compile(r"^[a-z0-9_]+$")

MATRIX_LIST_KEYS = (
    "mods",
    "descriptors",
    "resourceKinds",
    "acceptedRecipes",
    "rejectedDescriptors",
    "rejectedResourceKinds",
)

COMPATIBILITY_SUMMARY_RELATIVE_PATH = "build/reports/compatibility-modules.md"

SHARED_AGGREGATE_PATHS = frozenset(
    {
        "docs/generated/compatibility-modules.md",
        COMPATIBILITY_SUMMARY_RELATIVE_PATH,
        "src/compatibilityMatrixFixture/resources/META-INF/auto_storage/"
        "compatibility-matrix-companions.json",
        "README.md",
        "docs/overview.md",
        "docs/plan.md",
        "docs/roadmap.md",
        "docs/structure.md",
        "docs/notes.md",
        "docs/addon-development.md",
    }
)


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def recipe_inventory_sha256(recipe_ids: list[str]) -> str:
    return hashlib.sha256(
        canonical_json(sorted(recipe_ids)).encode()
    ).hexdigest()


def _require_string_list(value: Any, field: str) -> list[str]:
    if not isinstance(value, list) or any(not isinstance(item, str) for item in value):
        raise ValueError(f"matrix {field} must be a string list")
    if len(value) != len(set(value)):
        raise ValueError(f"matrix {field} must not contain duplicates")
    return list(value)


def _require_resource_ids(values: list[str], field: str) -> list[str]:
    for item in values:
        if not RESOURCE_ID_RE.fullmatch(item):
            raise ValueError(f"matrix {field} contains invalid id: {item}")
    return values


def _require_mod_ids(values: list[str], field: str) -> list[str]:
    for item in values:
        if not MOD_ID_RE.fullmatch(item):
            raise ValueError(f"matrix {field} contains invalid mod id: {item}")
    return values


def _validate_recipe_inventory(inventory: Any, field: str) -> dict[str, Any]:
    if not isinstance(inventory, dict):
        raise ValueError(f"{field} must be an object")
    namespaces = inventory.get("namespaces")
    sha256 = inventory.get("sha256")
    if inventory.keys() != {"namespaces", "sha256"}:
        raise ValueError(f"{field} must declare only namespaces and sha256")
    namespaces = _require_string_list(namespaces, f"{field}.namespaces")
    namespaces = _require_mod_ids(namespaces, f"{field}.namespaces")
    if not namespaces:
        raise ValueError(f"{field}.namespaces must not be empty")
    if not isinstance(sha256, str) or not SHA256_RE.fullmatch(sha256):
        raise ValueError(f"{field}.sha256 must be a SHA-256 digest")
    return {"namespaces": namespaces, "sha256": sha256}


def validate_descriptor_matrix(descriptor: dict[str, Any]) -> dict[str, Any]:
    matrix = descriptor.get("matrix")
    if not isinstance(matrix, dict):
        raise ValueError("compatibility descriptor requires matrix object")
    for forbidden in ("coexistenceRecipeInventory", "unclaimedRecipeInventory"):
        if forbidden in matrix:
            raise ValueError(
                f"matrix must not declare {forbidden}; "
                "cross-module inventory evidence is recorded by the matrix report, "
                "not committed baselines"
            )
    expected_keys = set(MATRIX_LIST_KEYS) | {"recipeInventory"}
    if matrix.keys() != expected_keys:
        raise ValueError(
            "matrix must declare mods, descriptors, resourceKinds, "
            "acceptedRecipes, rejectedDescriptors, rejectedResourceKinds, "
            "and recipeInventory"
        )
    validated = {
        key: _require_string_list(matrix[key], key) for key in MATRIX_LIST_KEYS
    }
    validated["mods"] = _require_mod_ids(validated["mods"], "mods")
    if not validated["mods"]:
        raise ValueError("matrix mods must not be empty")
    if "requires" in descriptor:
        requires = _require_mod_ids(
            _require_string_list(descriptor["requires"], "requires"),
            "requires",
        )
        if set(validated["mods"]) != set(requires):
            raise ValueError("matrix mods must match descriptor requires")
    validated["descriptors"] = _require_resource_ids(
        validated["descriptors"], "descriptors"
    )
    validated["resourceKinds"] = _require_resource_ids(
        validated["resourceKinds"], "resourceKinds"
    )
    validated["acceptedRecipes"] = _require_resource_ids(
        validated["acceptedRecipes"], "acceptedRecipes"
    )
    validated["rejectedDescriptors"] = _require_resource_ids(
        validated["rejectedDescriptors"], "rejectedDescriptors"
    )
    validated["rejectedResourceKinds"] = _require_resource_ids(
        validated["rejectedResourceKinds"], "rejectedResourceKinds"
    )
    validated["recipeInventory"] = _validate_recipe_inventory(
        matrix["recipeInventory"], "recipeInventory"
    )
    return validated


def validate_companions(companions_doc: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(companions_doc, dict) or companions_doc.get("schema") != 1:
        raise ValueError("companions schema must be 1")
    expected = {"schema", "companions"}
    if companions_doc.keys() != expected:
        unexpected = sorted(set(companions_doc) - expected)
        raise ValueError(
            "companions must declare only schema and companions"
            + (f"; unexpected {', '.join(unexpected)}" if unexpected else "")
        )
    companions = companions_doc["companions"]
    if not isinstance(companions, list):
        raise ValueError("companions must be a list")
    validated_companions = []
    seen_ids: set[str] = set()
    for companion in companions:
        if not isinstance(companion, dict) or not isinstance(companion.get("id"), str):
            raise ValueError("each companion requires a string id")
        companion_id = companion["id"]
        if companion_id in seen_ids:
            raise ValueError(f"Duplicate companion id: {companion_id}")
        seen_ids.add(companion_id)
        for forbidden in ("coexistenceRecipeInventory", "unclaimedRecipeInventory"):
            if forbidden in companion:
                raise ValueError(
                    f"companions entries must not declare {forbidden}; "
                    "global inventory digests are matrix report evidence only"
                )
        matrix_fields = {
            key: companion.get(key) for key in MATRIX_LIST_KEYS
        }
        matrix_fields["recipeInventory"] = companion.get("recipeInventory")
        validated = validate_descriptor_matrix({"matrix": matrix_fields})
        validated_companions.append({"id": companion_id, **validated})
    return {
        "schema": 1,
        "companions": validated_companions,
    }


def build_manifest(
    descriptors: list[dict[str, Any]],
    companions_doc: dict[str, Any],
) -> dict[str, Any]:
    companions = validate_companions(companions_doc)
    modules = []
    seen_module_ids: set[str] = set()
    claimed_namespaces: set[str] = set()
    for descriptor in descriptors:
        module_id = descriptor.get("id")
        if not isinstance(module_id, str) or not module_id.startswith("auto_storage:"):
            raise ValueError("descriptor id must be auto_storage:<mod>")
        if module_id in seen_module_ids:
            raise ValueError(f"Duplicate compatibility module ID: {module_id}")
        seen_module_ids.add(module_id)
        matrix = validate_descriptor_matrix(descriptor)
        for namespace in matrix["recipeInventory"]["namespaces"]:
            if namespace in claimed_namespaces:
                raise ValueError(f"Duplicate recipe namespace: {namespace}")
            claimed_namespaces.add(namespace)
        modules.append({"id": module_id, **matrix})
    for companion in companions["companions"]:
        for namespace in companion["recipeInventory"]["namespaces"]:
            if namespace in claimed_namespaces:
                raise ValueError(f"Duplicate recipe namespace: {namespace}")
            claimed_namespaces.add(namespace)
    modules.sort(key=lambda module: module["id"])
    companions["companions"] = sorted(
        companions["companions"], key=lambda companion: companion["id"]
    )
    return {
        "schema": 1,
        "modules": modules,
        "companions": companions["companions"],
    }


MANIFEST_KEYS = {
    "schema",
    "modules",
    "companions",
}


def validate_manifest(
    manifest: dict[str, Any],
    descriptors: list[dict[str, Any]],
    companions_doc: dict[str, Any],
) -> None:
    expected = build_manifest(descriptors, companions_doc)
    if manifest != expected:
        raise ValueError(
            "compatibility matrix manifest drift or tamper detected"
        )
    unexpected = set(manifest) - MANIFEST_KEYS
    if unexpected:
        raise ValueError(
            f"compatibility matrix manifest has unexpected keys: {sorted(unexpected)}"
        )


def build_compatibility_summary(
    descriptors: list[dict[str, Any]],
    *,
    docs_root: Path | None = None,
) -> list[dict[str, Any]]:
    rows = []
    seen: set[str] = set()
    for descriptor in descriptors:
        module_id = descriptor.get("id")
        if not isinstance(module_id, str) or not module_id.startswith("auto_storage:"):
            raise ValueError("descriptor id must be auto_storage:<mod>")
        if module_id in seen:
            raise ValueError(f"Duplicate compatibility module ID: {module_id}")
        seen.add(module_id)
        directory_id = module_id.split(":", 1)[1]
        fixture = descriptor.get("fixture")
        expected_tests = descriptor.get("expectedTests")
        if not isinstance(fixture, str) or not fixture:
            raise ValueError(f"{module_id} requires fixture")
        if not isinstance(expected_tests, int) or expected_tests <= 0:
            raise ValueError(f"{module_id} requires positive expectedTests")
        doc_name = f"{directory_id.replace('_', '-')}-compatibility.md"
        has_doc = docs_root is not None and (docs_root / doc_name).is_file()
        rows.append(
            {
                "id": module_id,
                "directory": directory_id,
                "fixture": fixture,
                "expectedTests": expected_tests,
                "doc": doc_name if has_doc else None,
            }
        )
    rows.sort(key=lambda row: row["id"])
    return rows


def render_compatibility_summary(rows: list[dict[str, Any]]) -> str:
    lines = [
        "<!-- Generated by scripts/compatibility_matrix_manifest.py."
        " Do not hand-edit. -->",
        "",
        "# Compatibility modules",
        "",
        "CI/release artifact written to"
        f" `{COMPATIBILITY_SUMMARY_RELATIVE_PATH}`."
        " Routine module PRs update only the owning descriptor and"
        " module-owned compatibility doc; they do not commit this report.",
        "",
        "| Module | Fixture | Expected tests | Compatibility doc |",
        "| --- | --- | ---: | --- |",
    ]
    for row in rows:
        if row["doc"] is None:
            doc_link = "—"
        else:
            doc_link = f"`docs/{row['doc']}`"
        lines.append(
            f"| `{row['id']}` | `{row['fixture']}` | {row['expectedTests']} "
            f"| {doc_link} |"
        )
    lines.append("")
    return "\n".join(lines)


def validate_compatibility_summary(
    text: str,
    descriptors: list[dict[str, Any]],
    *,
    docs_root: Path | None = None,
) -> None:
    expected = render_compatibility_summary(
        build_compatibility_summary(descriptors, docs_root=docs_root)
    )
    if text != expected:
        raise ValueError("compatibility summary drift or tamper detected")


def write_compatibility_summary(root: Path, output: Path) -> None:
    descriptors = load_descriptors(root / "src/compat")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        render_compatibility_summary(
            build_compatibility_summary(descriptors, docs_root=root / "docs")
        ),
        encoding="utf-8",
    )


def load_companions(path: Path) -> dict[str, Any]:
    if not path.is_file():
        raise ValueError(f"missing companions file: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def load_descriptors(compat_root: Path) -> list[dict[str, Any]]:
    descriptors = []
    for path in sorted(compat_root.glob("*/compat-module.json")):
        descriptors.append(json.loads(path.read_text(encoding="utf-8")))
    if not descriptors:
        raise ValueError("No compatibility module descriptors found")
    return descriptors


def build_manifest_from_roots(root: Path) -> dict[str, Any]:
    descriptors = load_descriptors(root / "src/compat")
    companions = load_companions(
        root
        / "src/compatibilityMatrixFixture/resources/META-INF/auto_storage/"
        / "compatibility-matrix-companions.json"
    )
    return build_manifest(descriptors, companions)


def write_manifest(root: Path, output: Path) -> None:
    manifest = build_manifest_from_roots(root)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(canonical_json(manifest), encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Generate or validate the compatibility matrix assertion manifest"
    )
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument(
        "--output",
        type=Path,
        help="Write the generated manifest to this path",
    )
    parser.add_argument(
        "--check",
        type=Path,
        help="Fail unless this manifest matches descriptors/companions",
    )
    parser.add_argument(
        "--summary-output",
        type=Path,
        help="Write the generated compatibility module summary markdown",
    )
    parser.add_argument(
        "--check-summary",
        type=Path,
        help="Fail unless this summary matches descriptors",
    )
    args = parser.parse_args(argv)
    root = args.root.resolve()
    descriptors = load_descriptors(root / "src/compat")
    companions = load_companions(
        root
        / "src/compatibilityMatrixFixture/resources/META-INF/auto_storage/"
        / "compatibility-matrix-companions.json"
    )
    manifest = build_manifest(descriptors, companions)
    if args.check is not None:
        actual = json.loads(args.check.read_text(encoding="utf-8"))
        validate_manifest(actual, descriptors, companions)
    if args.check_summary is not None:
        validate_compatibility_summary(
            args.check_summary.read_text(encoding="utf-8"),
            descriptors,
            docs_root=root / "docs",
        )
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(canonical_json(manifest), encoding="utf-8")
    if args.summary_output is not None:
        write_compatibility_summary(root, args.summary_output)
    if (
        args.output is None
        and args.check is None
        and args.summary_output is None
        and args.check_summary is None
    ):
        sys.stdout.write(canonical_json(manifest))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
