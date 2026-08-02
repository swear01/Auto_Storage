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
    if companions_doc.keys() != {"schema", "companions", "unclaimedRecipeInventory"}:
        raise ValueError(
            "companions must declare schema, companions, and unclaimedRecipeInventory"
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
        matrix_fields = {
            key: companion.get(key) for key in MATRIX_LIST_KEYS
        }
        matrix_fields["recipeInventory"] = companion.get("recipeInventory")
        validated = validate_descriptor_matrix({"matrix": matrix_fields})
        validated_companions.append({"id": companion_id, **validated})
    unclaimed = companions_doc["unclaimedRecipeInventory"]
    if not isinstance(unclaimed, dict) or unclaimed.keys() != {"sha256"}:
        raise ValueError("unclaimedRecipeInventory must declare only sha256")
    sha256 = unclaimed["sha256"]
    if not isinstance(sha256, str) or not SHA256_RE.fullmatch(sha256):
        raise ValueError("unclaimedRecipeInventory.sha256 must be a SHA-256 digest")
    return {
        "schema": 1,
        "companions": validated_companions,
        "unclaimedRecipeInventory": {"sha256": sha256},
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
        "unclaimedRecipeInventory": companions["unclaimedRecipeInventory"],
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
    unexpected = set(manifest) - {"schema", "modules", "companions", "unclaimedRecipeInventory"}
    if unexpected:
        raise ValueError(
            f"compatibility matrix manifest has unexpected keys: {sorted(unexpected)}"
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
    args = parser.parse_args(argv)
    root = args.root.resolve()
    manifest = build_manifest_from_roots(root)
    if args.check is not None:
        actual = json.loads(args.check.read_text(encoding="utf-8"))
        validate_manifest(
            actual,
            load_descriptors(root / "src/compat"),
            load_companions(
                root
                / "src/compatibilityMatrixFixture/resources/META-INF/auto_storage/"
                / "compatibility-matrix-companions.json"
            ),
        )
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(canonical_json(manifest), encoding="utf-8")
    elif args.check is None:
        sys.stdout.write(canonical_json(manifest))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
