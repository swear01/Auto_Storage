#!/usr/bin/env python3
"""Batch transform-candidate scan over a mods directory.

Runs the Compat Kit three-layer detector (name_term, hierarchy, bytecode)
over every jar in a Prism instance mods folder and writes
build/transform-candidates/candidates.json plus a per-mod summary with
spec-rejection hints (multiblock / passive / fluid-input) for review.
"""
import argparse
import concurrent.futures
import json
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "tools" / "compat-kit"))

from compat_kit import transform_candidates  # noqa: E402

MULTIBLOCK_HINTS = (
    "fission",
    "turbine",
    "reactor",
    "controller",
    "multiblock",
    "casing",
    "rotor",
    "valve",
    "port",
    "core",
)
PASSIVE_HINTS = ("wind", "solar")
FLUID_INPUT_HINTS = ("bio", "gasburning", "combustion", "diesel", "fluid")
DATAGEN_HINTS = ("registry", "init", "util", "api", "shapes", "renderer")


def rejection_hints(class_name: str) -> list[str]:
    lowered = class_name.lower()
    hints = []
    for group, terms in (
        ("multiblock", MULTIBLOCK_HINTS),
        ("passive", PASSIVE_HINTS),
        ("fluid-input", FLUID_INPUT_HINTS),
        ("support", DATAGEN_HINTS),
    ):
        if any(term in lowered for term in terms):
            hints.append(group)
    return hints


def scan_one(jar: Path) -> tuple[str, list[dict]]:
    try:
        return jar.name, transform_candidates(jar)
    except Exception as exc:  # noqa: BLE001 - per-jar isolation
        return jar.name, [{"class": f"<scan error>", "evidence": [str(exc)]}]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--mods-dir",
        type=Path,
        default=Path.home()
        / "Library/Application Support/PrismLauncher/instances/atm10-as"
        / "minecraft/mods",
    )
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--output-dir", type=Path, default=PROJECT_ROOT / "build/transform-candidates")
    args = parser.parse_args(argv)

    if not args.mods_dir.is_dir():
        parser.error(f"mods directory not found: {args.mods_dir}")
    jars = sorted(args.mods_dir.glob("*.jar"))
    if not jars:
        parser.error(f"no jars found in {args.mods_dir}")
    print(f"scanning {len(jars)} jars with {args.workers} workers...")

    results = {}
    with concurrent.futures.ProcessPoolExecutor(
        max_workers=args.workers
    ) as pool:
        for name, candidates in pool.map(scan_one, jars):
            results[name] = candidates

    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / "candidates.json").write_text(
        json.dumps(results, indent=2, sort_keys=True) + "\n"
    )
    lines = [
        "# ATM10 Transform-page candidates",
        "",
        "Detector: `compat-kit transform-candidates` (name_term / hierarchy / "
        "bytecode). Review each entry against the Transform-page spec in "
        "docs/compat-kit.md; rejection hints are advisory only.",
        "",
    ]
    total = 0
    for jar in sorted(results):
        candidates = [
            entry for entry in results[jar]
            if not entry["class"].startswith("<")
        ]
        if not candidates:
            continue
        total += len(candidates)
        lines.append(f"## {jar}")
        for entry in candidates:
            hints = rejection_hints(entry["class"])
            hint_text = (
                f"  (reject: {', '.join(hints)})" if hints else ""
            )
            lines.append(
                f"- `{entry['class']}` — "
                + ", ".join(entry["evidence"])
                + hint_text
            )
        lines.append("")
    lines.append(f"Total candidate entries: {total}")
    (args.output_dir / "summary.md").write_text("\n".join(lines) + "\n")
    print(f"done: {total} candidate entries -> {args.output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
