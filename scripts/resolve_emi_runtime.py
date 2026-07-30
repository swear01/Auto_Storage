import json
from pathlib import Path
import sys
from urllib.parse import urlencode
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[1]
VERSIONS_URL = "https://api.modrinth.com/v2/project/emi/version"


def resolve_runtime_version_id(
    versions: list[dict],
    api_version: str,
    minecraft_version: str,
) -> str:
    expected_version = f"{api_version}+neoforge"
    matches = [
        version
        for version in versions
        if version.get("version_number") == expected_version
        and version.get("version_type") == "release"
        and version.get("status") == "listed"
        and "neoforge" in version.get("loaders", [])
        and minecraft_version in version.get("game_versions", [])
    ]
    if not matches:
        raise ValueError(f"no exact Modrinth EMI runtime for {api_version}")
    if len(matches) > 1:
        raise ValueError(f"multiple exact Modrinth EMI runtimes for {api_version}")

    version = matches[0]
    primary_jars = [
        file
        for file in version.get("files", [])
        if file.get("primary") is True
        and isinstance(file.get("filename"), str)
        and file["filename"].endswith(".jar")
        and not file["filename"].endswith("-api.jar")
        and isinstance(file.get("size"), int)
        and file["size"] > 0
    ]
    if len(primary_jars) != 1:
        raise ValueError("Modrinth EMI release must contain one valid primary runtime jar")

    version_id = version.get("id")
    if not isinstance(version_id, str) or not version_id:
        raise ValueError("Modrinth EMI runtime is missing its version ID")
    return version_id


def read_properties(path: Path) -> dict[str, str]:
    properties = {}
    for raw_line in path.read_text().splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        properties[key.strip()] = value.strip()
    return properties


def fetch_versions(minecraft_version: str) -> list[dict]:
    query = urlencode({
        "loaders": json.dumps(["neoforge"], separators=(",", ":")),
        "game_versions": json.dumps([minecraft_version], separators=(",", ":")),
    })
    request = Request(
        f"{VERSIONS_URL}?{query}",
        headers={"User-Agent": "Auto-Storage-EMI-runtime-check"},
    )
    with urlopen(request, timeout=30) as response:
        payload = json.load(response)
    if not isinstance(payload, list):
        raise ValueError("Modrinth EMI versions response must be a list")
    return payload


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: resolve_emi_runtime.py <emi-api-version>")
    api_version = sys.argv[1]
    properties = read_properties(ROOT / "gradle.properties")
    minecraft_version = properties["minecraft_version"]
    print(resolve_runtime_version_id(
        fetch_versions(minecraft_version),
        api_version,
        minecraft_version,
    ))


if __name__ == "__main__":
    main()
