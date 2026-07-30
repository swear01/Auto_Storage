import json
from pathlib import Path
import re
from urllib.parse import urlencode
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[1]
VERSIONS_URL = "https://api.modrinth.com/v2/project/emi/version"
ARTIFACT_VERSION = re.compile(r"^(\d+)\.(\d+)\.(\d+)\+(.+)$")
MODRINTH_VERSION = re.compile(r"^(\d+)\.(\d+)\.(\d+)\+(.+)\+neoforge$")


def numeric_version(value: str) -> tuple[int, int, int]:
    parts = value.split(".")
    if not 1 <= len(parts) <= 3 or any(not part.isdigit() for part in parts):
        raise ValueError(f"unsupported numeric version: {value}")
    return tuple(int(part) for part in parts) + (0,) * (3 - len(parts))


def parse_supported_range(value: str) -> tuple[tuple[int, int, int], tuple[int, int, int]]:
    match = re.fullmatch(r"\[([^,]+),([^)]+)\)", value)
    if match is None:
        raise ValueError("EMI range must use an inclusive minimum and exclusive upper bound")
    minimum = numeric_version(match.group(1))
    upper_exclusive = numeric_version(match.group(2))
    if minimum >= upper_exclusive:
        raise ValueError("EMI range minimum must be below its upper bound")
    return minimum, upper_exclusive


def parse_artifact_version(value: str) -> tuple[tuple[int, int, int], str] | None:
    match = ARTIFACT_VERSION.fullmatch(value)
    if match is None:
        return None
    return tuple(int(match.group(index)) for index in range(1, 4)), match.group(4)


def validate_baseline(
    baseline: str,
    minecraft_version: str,
    minimum: tuple[int, int, int],
    upper_exclusive: tuple[int, int, int],
) -> None:
    parsed = parse_artifact_version(baseline)
    if parsed is None:
        raise ValueError(f"unsupported EMI baseline coordinate: {baseline}")
    version, artifact_minecraft_version = parsed
    if artifact_minecraft_version != minecraft_version:
        raise ValueError("EMI baseline Minecraft version does not match the project")
    if version != minimum:
        raise ValueError("EMI baseline must equal the minimum supported EMI version")
    if version >= upper_exclusive:
        raise ValueError("EMI baseline is outside the supported range")


def latest_compatible_version(
    versions: list[dict],
    minecraft_version: str,
    minimum: tuple[int, int, int],
    upper_exclusive: tuple[int, int, int],
) -> str:
    candidates = []
    for release in versions:
        if not isinstance(release, dict):
            continue
        if release.get("version_type") != "release" or release.get("status") != "listed":
            continue
        if "neoforge" not in release.get("loaders", []):
            continue
        if minecraft_version not in release.get("game_versions", []):
            continue
        version_number = release.get("version_number")
        if not isinstance(version_number, str):
            continue
        match = MODRINTH_VERSION.fullmatch(version_number)
        if match is None:
            continue
        version = tuple(int(match.group(index)) for index in range(1, 4))
        artifact_minecraft_version = match.group(4)
        if artifact_minecraft_version != minecraft_version:
            continue
        primary_jars = [
            file
            for file in release.get("files", [])
            if isinstance(file, dict)
            and file.get("primary") is True
            and isinstance(file.get("filename"), str)
            and file["filename"].endswith(".jar")
            and not file["filename"].endswith("-api.jar")
            and isinstance(file.get("size"), int)
            and file["size"] > 0
        ]
        if len(primary_jars) != 1:
            continue
        if minimum <= version < upper_exclusive:
            candidates.append((version, f"{version[0]}.{version[1]}.{version[2]}+{minecraft_version}"))
    if not candidates:
        raise ValueError(
            f"no compatible EMI release for Minecraft {minecraft_version} in the configured range"
        )
    return max(candidates)[1]


def read_properties(path: Path) -> dict[str, str]:
    properties = {}
    for raw_line in path.read_text().splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        properties[key.strip()] = value.strip()
    return properties


def fetch_versions(minecraft_version: str, opener=urlopen) -> list[dict]:
    query = urlencode({
        "loaders": json.dumps(["neoforge"], separators=(",", ":")),
        "game_versions": json.dumps([minecraft_version], separators=(",", ":")),
        "include_changelog": "false",
    })
    request = Request(
        f"{VERSIONS_URL}?{query}",
        headers={"User-Agent": "Auto-Storage-EMI-compatibility-check"},
    )
    with opener(request, timeout=30) as response:
        payload = json.loads(response.read())
    if not isinstance(payload, list):
        raise ValueError("Modrinth EMI versions response must be a list")
    return payload


def main() -> None:
    properties = read_properties(ROOT / "gradle.properties")
    minecraft_version = properties["minecraft_version"]
    baseline = properties["emi_version"]
    minimum, upper_exclusive = parse_supported_range(properties["emi_version_range"])
    validate_baseline(baseline, minecraft_version, minimum, upper_exclusive)
    print(
        latest_compatible_version(
            fetch_versions(minecraft_version),
            minecraft_version,
            minimum,
            upper_exclusive,
        )
    )


if __name__ == "__main__":
    main()
