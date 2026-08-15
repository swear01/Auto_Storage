#!/usr/bin/env python3
"""Dump every item->resource conversion recipe from a mods directory.

Scans each jar's `data/*/recipe/*.json` payloads and lists recipes whose
type is a known conversion family (chemical_conversion, energy_conversion,
and any type whose serializer name contains conversion/to_chemical/
to_energy), plus the full per-type recipe count for review. Writes
build/transform-candidates/conversion-recipes.json and a readable report.
"""
import argparse
import json
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

CONVERSION_TYPE_HINTS = (
    "conversion",
    "to_chemical",
    "to_energy",
    "energy_from",
)
GENERATOR_TYPE_HINTS = ("generating", "burning", "energizing", "fuel")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--mods-dir",
        type=Path,
        default=Path.home()
        / "Library/Application Support/PrismLauncher/instances/atm10-as"
        / "minecraft/mods",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path(__file__).resolve().parents[1]
        / "build/transform-candidates",
    )
    args = parser.parse_args(argv)

    if not args.mods_dir.is_dir():
        parser.error(f"mods directory not found: {args.mods_dir}")

    per_type: Counter = Counter()
    conversions: dict[str, list[dict]] = defaultdict(list)
    for jar in sorted(args.mods_dir.glob("*.jar")):
        try:
            archive = zipfile.ZipFile(jar)
        except zipfile.BadZipFile:
            continue
        for name in archive.namelist():
            if not name.startswith("data/"):
                continue
            parts = name.split("/")
            if len(parts) < 4 or parts[2] != "recipe" or not name.endswith(".json"):
                continue
            try:
                payload = json.loads(archive.read(name))
            except (json.JSONDecodeError, KeyError, zipfile.BadZipFile):
                continue
            recipe_type = payload.get("type", "")
            per_type[recipe_type] += 1
            if any(hint in recipe_type.lower() for hint in CONVERSION_TYPE_HINTS):
                recipe_id = "/".join(parts[1:-1]) + ":" + parts[-1][:-5]
                conversions[jar.name].append({
                    "id": recipe_id,
                    "type": recipe_type,
                    "input": summarize(payload.get("input") or payload.get("ingredients")),
                    "output": summarize(payload.get("output") or payload.get("result")
                                        or payload.get("outputs")),
                })

    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / "conversion-recipes.json").write_text(
        json.dumps({k: sorted(v, key=lambda r: r["id"])
                    for k, v in sorted(conversions.items())},
                   indent=2, sort_keys=True) + "\n"
    )
    lines = [
        "# ATM10 conversion recipes",
        "",
        "Every `data/*/recipe/*.json` whose type hints at item->resource "
        "conversion (conversion / to_chemical / to_energy / energy_from).",
        "",
    ]
    count = 0
    for jar in sorted(conversions):
        lines.append(f"## {jar}")
        for recipe in conversions[jar]:
            count += 1
            lines.append(
                f"- `{recipe['id']}` ({recipe['type']}) "
                f"in={recipe['input']} out={recipe['output']}"
            )
    lines += ["", f"total: {count} conversion recipes", ""]
    lines += ["## All recipe types by count", ""]
    for recipe_type, n in per_type.most_common():
        lines.append(f"- {n:5d} {recipe_type}")
    (args.output_dir / "conversion-recipes.md").write_text("\n".join(lines) + "\n")
    print(f"{count} conversion recipes -> "
          f"{args.output_dir / 'conversion-recipes.md'}")
    return 0


def summarize(value) -> str:
    if value is None:
        return "-"
    if isinstance(value, list):
        return " + ".join(summarize(v) for v in value)
    if isinstance(value, dict):
        if "item" in value:
            return str(value["item"])
        if "tag" in value:
            return "#" + str(value["tag"])
        if "id" in value:
            out = str(value["id"])
            if "count" in value and value["count"] != 1:
                out += f"x{value['count']}"
            if "amount" in value:
                out += f"x{value['amount']}"
            return out
        if "chemical" in value:
            return str(value["chemical"])
        if "fluid" in value:
            return str(value["fluid"])
        return json.dumps(value, sort_keys=True)[:60]
    return str(value)[:60]


if __name__ == "__main__":
    raise SystemExit(main())
