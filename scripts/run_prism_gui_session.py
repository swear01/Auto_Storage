#!/usr/bin/env python3
import argparse
import hashlib
import json
import os
import re
import signal
import subprocess
import sys
import time
from pathlib import Path
from typing import Callable, NamedTuple

try:
    from prepare_prism_gui_world import DisplayMode, DEFAULT_PRISM_MINECRAFT_DIR, DEFAULT_SOURCE_WORLD, DEFAULT_WORLD_NAME, current_macos_main_display_mode, prepare_world
except ModuleNotFoundError:
    from scripts.prepare_prism_gui_world import DisplayMode, DEFAULT_PRISM_MINECRAFT_DIR, DEFAULT_SOURCE_WORLD, DEFAULT_WORLD_NAME, current_macos_main_display_mode, prepare_world

try:
    from deploy_prism_dev import (
        FUSION_FILENAME,
        FUSION_SHA512,
        IRON_FURNACES_FILENAME,
        SUPPORT_ARTIFACTS,
        fusion_jars,
        jei_jars,
        sha512,
    )
except ModuleNotFoundError:
    from scripts.deploy_prism_dev import (
        FUSION_FILENAME,
        FUSION_SHA512,
        IRON_FURNACES_FILENAME,
        SUPPORT_ARTIFACTS,
        fusion_jars,
        jei_jars,
        sha512,
    )

DEFAULT_INSTANCE_DIR = Path.home() / "Library/Application Support/PrismLauncher/instances/dev"
DEFAULT_PROJECT_DIR = Path(__file__).resolve().parents[1]
DEFAULT_RUN_ROOT = Path("build/gui-runs")
DEFAULT_TIMEOUT_SECONDS = 300
DEFAULT_PROCESS_TERMINATION_TIMEOUT_SECONDS = 5
DEFAULT_SHUTDOWN_STALL_TIMEOUT_SECONDS = 5
DEFAULT_OFFLINE_PLAYER = "MagicStorageBot"
DEFAULT_PRISM_APP = "/Applications/Prism Launcher.app"
MINIMUM_PRISM_VERSION = (11, 0, 3)
PRISM_AUTH_FORBIDDEN_PATTERNS = (
    "AuthFlow:",
    "login.microsoftonline.com",
    "user.auth.xboxlive.com",
    "xsts.auth.xboxlive.com",
    "api.minecraftservices.com/launcher/login",
    "api.minecraftservices.com/entitlements",
)
PRISM_LAUNCH_ENV_KEYS = ("HOME", "PATH", "TMPDIR", "LANG", "LC_ALL", "USER", "LOGNAME", "SHELL")
DEFAULT_REQUIRED_PATTERNS = ["SelfTest:", "MS_GUI_TEST_READY"]
DEFAULT_FORBIDDEN_PATTERNS = ["advanced_container_set_data", "ERROR", "FATAL", "Caused by"]
MANUAL_HANDOFF_MESSAGE = "Minecraft is ready in the fixed test world. Please take over for the fullscreen visual checks; close with F11, wait for the normal window, then Command-Q."
OFFLINE_AUTH_PROPERTY_ERROR = "[net.minecraft.client.Minecraft/]: Failed to fetch user properties"
ALLOWED_SINGLE_LINE_LOG_NOISE = (
    "[net.minecraft.Util/]: Invalid path in pack: botania:patchouli_books/lexica_botania/en_us/README, ignoring",
    "[Curios API/]: example is not a registered slot type!",
    "[Curios API/]: feet is not a registered slot type!",
)
PRIMARY_MONITOR_ERROR = "glfwGetPrimaryMonitor failed"
STOPPING_LOG_PATTERN = "[net.minecraft.client.Minecraft/]: Stopping!"
LOG_TIMESTAMP_PATTERN = re.compile(r"^\[\d{1,2}[A-Za-z]{3}\d{4} ")
DEPLOY_REQUIRED_MESSAGE = "Run `python3 scripts/deploy_prism_dev.py` before launching Prism."
CURRENT_RUN_LOG_CHECK = "Check latest.log for no non-whitelisted advanced_container_set_data, ERROR, FATAL, or Caused by during the current run; only the known offline profile-properties 401 exact stack, Botania snapshot README path line, and Industrial Foregoing Curios example/feet slot lines are allowed."

SCENARIOS = {
    "boot-smoke": {
        "description": "Launch the fixed GUI test world and verify boot/resource logs only.",
        "manual_gui_required": False,
        "hotbar_keys": [],
        "checks": [
            "No manual visual pass is required unless this run is investigating visible UI behavior.",
            "Review log-excerpt.log for SelfTest and MS_GUI_TEST_READY.",
        ],
    },
    "terminal-left-rail": {
        "description": "Open Storage/Crafting Terminal in fullscreen and inspect terminal layout.",
        "manual_gui_required": True,
        "hotbar_keys": ["1", "2"],
        "checks": [
            "Pass the fullscreen gate before pressing hotbar/use/click/scroll.",
            "hotbar `1`, then `u`: Storage Terminal opens with left-side view buttons and no clipping.",
            "hotbar `2`, then `u`: Crafting Terminal opens with left-side view buttons and no EMI overlap.",
            "On each Storage view, cycle through registered resource kinds only; confirm the grid changes group, right-click goes backward, and middle-click resets it to Items. Gases appears only when a chemical provider such as Mekanism is loaded, and Other appears only when an addon registers another resource kind.",
            "On the Crafting Terminal, confirm Storage and Craftable expose the resource selector while Transform and Stations hide the resource selector; switching pages must not move the shared rail.",
            "In Storage Terminal choose non-default sort order, sort mode, Search Sync, and resource view; close and reopen Storage Terminal, then open Crafting Terminal and confirm those four common choices are shared.",
            "In Crafting Terminal choose non-default Crafting-only page, source, output, and Transform Target values; close and reopen it and confirm those choices return without changing the common Storage choices.",
            "Safely close and restart the client, reopen Storage Terminal and Crafting Terminal, and confirm both the common choices and Crafting-only page, source, output, and Transform Target choices survived on disk; search text, scroll, and item/recipe selection must start as session state instead of returning.",
            CURRENT_RUN_LOG_CHECK,
        ],
    },
    "bus-configuration": {
        "description": "Open Import/Export Bus configuration in fullscreen and inspect its controls and directional models.",
        "manual_gui_required": True,
        "hotbar_keys": ["5", "6", "7", "9"],
        "checks": [
            "Pass the fullscreen gate before pressing hotbar/use/click/scroll.",
            "Use hotbar `9` once to restore the clean current run baseline.",
            "hotbar `5`, then `u`: Import Bus opens a centered configuration screen with title, four primary controls, a 3×3 Filters area, six side buttons, and the player inventory fully visible without overlap or clipping.",
            "Confirm its initial labels are Front, Unsided access On, Automation On, and Deny; every label remains readable at the active GUI scale.",
            "Toggle Front to Pipes and confirm the right-side caption changes from Front face to Pipe access; close the screen and confirm the bus uses the neutral directionless block model instead of a directional arrow.",
            "Reopen the Import Bus, toggle each of the six side buttons once, and confirm enabled and disabled states are visually distinct without relying only on hover tooltips.",
            "Toggle Unsided access, Automation, and Deny/Allow once each; confirm each state changes immediately and the layout does not move.",
            "Place one supplied held item or supported resource container into the first Filters slot, confirm a representative appears without consuming the carried stack, then click the populated filter with an empty cursor to remove it.",
            "hotbar `6`, then `u`: Export Bus opens the same shared layout and control grammar, with its own title and initial Front/Allow behavior.",
            "Repeat the Pipes transition on the Export Bus and confirm its directionless block model is neutral while the directional model remains visually distinct from Import when Front is restored.",
            "Close and reopen both buses and confirm the latest mode, side mask, unsided, automation, filter policy, and filter entries persist rather than flashing back to defaults.",
            "With hotbar `7` Wrench, normal right-click a Front-mode bus and confirm its front rotates exactly once; do not dismantle the active test network during this scenario.",
            CURRENT_RUN_LOG_CHECK,
        ],
    },
    "crafting-fuel-page": {
        "description": "Verify the adaptive Crafting Terminal, Transform and Stations pages, station-gated recipes, optional-mod recipe ownership, dynamic resource kinds, and recipe readability.",
        "manual_gui_required": True,
        "hotbar_keys": ["1", "2"],
        "checks": [
            "Pass the fullscreen gate before pressing hotbar/use/click/scroll.",
            "The true-void scenario starts already aimed at the Crafting Terminal with a deterministic preloaded Core record. Every station, stored resource, and recipe ingredient needed below is already installed or stored; the only player item is one Coal visual input in hotbar `3`, so visual acceptance requires no setup action.",
            "hotbar `1`, then `u`: open the Storage Terminal and confirm the active Creative Storage Unit makes the capacity line show localized unlimited type capacity rather than a large finite sentinel.",
            "hotbar `2`, then `u`: open the Crafting Terminal; confirm the frame and left rail are centered as one group, retain visible outer margins, and use the side-by-side layout without clipping at this fullscreen size.",
            "On the Storage tab, confirm only stored stacks appear and the recipe panel, otherwise-empty player inventory, scrollbar, and slot grid are aligned to one geometry; the EMI overlay covers neither the frame nor left rail.",
            "Confirm preloaded Oak Logs show the compact large item counts form `121K`, Cobblestone is also compact, and all amounts automatically shrink/right-align inside their own slot without entering neighboring item cells.",
            "On both Storage and Craftable, confirm the resource selector exposes registered resource kinds only. Gases appears because Mekanism provides chemicals; Other appears because Botania Mana and Ars Nouveau Source providers are loaded. Items uses the Chest icon while Use Player Inventory uses the distinct Bundle icon, right-click goes backward, and middle-click resets to Items. Transform and Stations hide the resource selector.",
            "Confirm the first four page tabs are Storage, Craftable, Transform, and Stations, followed by a clear visual gap before the item-page sorting/search controls; every rail icon occupies the same visual size inside identical buttons.",
            "Type ordinary text, `@minecraft`, and `#minecraft:logs` directly in the search bar; confirm the server-owned grid applies each prefix without a separate Name/Tag/Mod selector.",
            "Confirm Auto Focus is a separate on/off control: with it enabled, opening either terminal immediately receives keyboard input in the search field and clicking another control does not release that keyboard focus; disable it and confirm ordinary focus behavior returns.",
            "Cycle Search Sync through Off, EMI, and EMI Two-way. Off must leave EMI search untouched; EMI must copy terminal text to EMI; EMI Two-way must additionally copy EMI edits back into the terminal and refresh the storage results.",
            "Click a left-rail toggle, then click empty panel space: no white focus border may remain on the button.",
            "Use Transform; confirm item-grid sorting/search/recipe controls hide, the input slot starts empty, and a persistent target list fills the left side of the workspace. Search targets filters localized target names; its previous/next buttons, page indicator, wheel paging, full-row selection, selected row, and middle-click reset stay inside that column.",
            "Shift-click the preloaded Coal from hotbar `3` into Transform. In Auto, confirm the whole horizontal card is clickable and lists every compatible exact-item use without preselecting or consuming one. Fuel and Energy target rows must filter that same Coal input rather than opening a global recipe catalog; Energy must be labeled as the produced resource, not Redstone Dust.",
            "Confirm ×1, ×8, ×64, and Max use the same visual strip as crafting and dim whenever the input, output capacity, or required station work cannot complete that amount. An input with no matching use shows neutral No transformations text.",
            "Confirm every cyclic sort/search-sync/source/output selector also resets to its documented default on middle-click; page tabs do not pretend to be on/off controls.",
            "Click one Transform card and confirm its selected state is distinct, the selected transformation preview fills the lower information area, and the amount buttons become available only after that explicit selection.",
            "Open Stations. Confirm exactly two top-aligned full-width groups—Processing Stations and Instant Stations—use the same light vanilla container panels, horizontal icon+installed-count rows, visible previous/next buttons, and panel-local wheel paging. Row count comes from available content height and should fit about seven readable 18×18-icon rows here, not a fixed five-row layout. Processing Stations includes Iron Furnace, Blast Furnace, Smoker, Campfire, Brewing Stand, Cooking Pot, Powah Furnator, and supported Mekanism machines; Instant Stations contains Crafting Table, Stonecutter, and Smithing Table without fake work totals.",
            "Use the bottom-right Search Stations control and enter ordinary text, `@mekanism`, and a matching item tag. While search is active, both station groups become one result grid containing stations only; clearing or closing search restores the two groups without moving or duplicating any installed station.",
            "Confirm Stations shows no permanent rate formula or per-tile explanatory labels. The lower hovered station detail reports the exact station name, installed count, combined decimal rate per tick, and stored work when the pointer is anywhere on that station row; otherwise it returns to type capacity.",
            "On Stations, confirm stored types / total type capacity appears in an independent information box immediately to the right of the player inventory, aligned to the inventory's top and bottom, readable without hover, and matching the Storage Core. The active network must show localized unlimited type capacity with the Creative Storage Unit icon; finite networks continue to use the Tier 1 icon.",
            "Hover the preinstalled Iron Furnace stack and confirm one tooltip reports a fixed two-decimal rate such as 1.25/tick, never a fraction, and never overlaps a second vanilla tooltip. Open the Charcoal recipe and confirm its dim representative station item starts with the installed variant first, advances exactly once per real-time second like EMI/JEI, then returns to Iron Furnace after one complete station-icon cycle.",
            "Open EMI's Smelting category and confirm TMRV imports the workstation catalysts that Iron Furnaces owns through JEI. Magic Storage does not register third-party EMI workstations.",
            "Open the Craftable tab and confirm the already installed Crafting Table exposes an Oak Log recipe, Stonecutter exposes a Cobblestone result, Smithing Table exposes the Netherite Smithing Transform, and preloaded Axe Energy exposes the Oak Log strip transformation.",
            "Confirm Charcoal is present in Craftable even though its zero stored count is not rendered, then select it to open the exact smelting Available / Required preview.",
            "Select the preloaded Cooking Pot Mushroom Stew recipe. Confirm EMI renders the third-party recipe, the station badge is Cooking Pot, and the ledger separates brown mushroom, red mushroom, and bowl from Cooking Pot work and Fuel.",
            "Select each expanded recipe once and confirm the light vanilla-style recipe workspace contains one compact raised panel: the EMI recipe diagram is used for represented standard recipes while the internal axe transformation uses the native diagram, and the material ledger follows immediately below without an oversized empty panel. Required resources align from the ledger's top edge, use at most four columns, and recipes with more than eight total item/energy/tool resources continue into a third row without clipping. Every resource keeps two full-size lines—available such as `121K`, then required such as `/200`—without truncating digits, shrinking the font, or entering the neighboring cell. Both diagrams clearly show input resources, operation arrow, exact output count, Ready/Missing state, explicit vanilla-style navigation buttons, and vanilla-style ×1/×8/×64/Max buttons. A dim station icon stays in the diagram's lower-right corner without covering recipe content.",
            "Select no item and confirm the complete wrapped prompt is centered in that same compact card without losing its final character; select a stored item with no supported recipe and confirm the panel shows a neutral No supported recipe message instead of an empty panel, white fallback surface, or red error.",
            "Toggle Craft Output and confirm Player uses a player-head icon while Storage uses the Storage Core icon; Craft Output: Storage has no status light, while the genuine Use Player Inventory on/off control may show one.",
            "In that table, confirm Oak Log shows its current available count and 1 required, while Smelting Energy and Fuel each show their current reserve and the exact recipe cooking time required for one craft.",
            "Cycle the resource selector through preloaded Fluids, Energy, Gases, Station Work, and Other. Energy must contain NeoForge power plus generated Fuel/Brew/Axe/descriptor reserves, while Station Work contains only processing-station work; confirm water/lava, Mekanism oxygen/hydrogen, Botania Mana, and Ars Nouveau Source also render with readable long amounts.",
            "Confirm each preinstalled Mekanism representative exposes its deterministic recipe family without manual per-machine registration.",
            "Return between Storage, Craftable, Transform, and Stations once more and confirm search, sort, item grid, adaptive recipe panel, transform cards, machine slots, and crafting controls restore without drifting. A warm Craftable return should be immediate and must not produce a new keep-up warning.",
            CURRENT_RUN_LOG_CHECK,
        ],
    },
    "patchouli-guide": {
        "description": "Verify Patchouli guide content in a real client when guide resources changed.",
        "manual_gui_required": True,
        "hotbar_keys": [],
        "checks": [
            "Pass the fullscreen gate before any GUI action.",
            "Open the Magic Storage Guide and confirm categories/entries render, not No Entries.",
            "Confirm log-excerpt.log includes Patchouli preloading when Patchouli is installed in the Prism dev instance.",
        ],
    },
}


class SessionResult(NamedTuple):
    scenario: str
    manual_gui_required: bool
    manual_handoff_message: str | None
    run_dir: Path
    launch_command: list[str]
    log_excerpt: str
    manifest: dict


class LogCursor(NamedTuple):
    size: int
    device: int | None
    inode: int | None


def timestamp() -> str:
    return time.strftime("%Y%m%d-%H%M%S")


def log_cursor(log_path: Path) -> LogCursor:
    if not log_path.exists():
        return LogCursor(0, None, None)
    stat_result = log_path.stat()
    return LogCursor(stat_result.st_size, stat_result.st_dev, stat_result.st_ino)


def read_log_since(log_path: Path, cursor: LogCursor) -> str:
    if not log_path.exists():
        return ""
    stat_result = log_path.stat()
    same_file = cursor.device == stat_result.st_dev and cursor.inode == stat_result.st_ino
    start = cursor.size if same_file and stat_result.st_size >= cursor.size else 0
    with log_path.open("rb") as handle:
        handle.seek(start)
        return handle.read().decode("utf-8", errors="replace")


def find_pattern(text: str, pattern: str) -> bool:
    return re.search(pattern, text) is not None if pattern.startswith("(?") else pattern in text


def remove_allowed_log_noise(text: str) -> str:
    lines = text.splitlines(keepends=True)
    filtered = []
    skipping_offline_auth_error = False
    for line in lines:
        if any(noise in line for noise in ALLOWED_SINGLE_LINE_LOG_NOISE):
            continue
        if OFFLINE_AUTH_PROPERTY_ERROR in line:
            skipping_offline_auth_error = True
            continue
        if skipping_offline_auth_error and LOG_TIMESTAMP_PATTERN.match(line):
            skipping_offline_auth_error = False
        if not skipping_offline_auth_error:
            filtered.append(line)
    return "".join(filtered)


def wait_for_log_patterns(
    log_path: Path,
    offset: LogCursor,
    required_patterns: list[str],
    forbidden_patterns: list[str],
    timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS,
    poll_seconds: float = 0.25,
    sleep_func: Callable[[float], None] = time.sleep,
) -> str:
    deadline = time.monotonic() + timeout_seconds
    while True:
        text = read_log_since(log_path, offset)
        filtered_text = remove_allowed_log_noise(text)
        if PRIMARY_MONITOR_ERROR in filtered_text:
            raise RuntimeError(
                "Minecraft could not access a primary monitor; wake and unlock the macOS display, then rerun the same GUI scenario."
            )
        for pattern in forbidden_patterns:
            if find_pattern(filtered_text, pattern):
                raise RuntimeError(f"forbidden log pattern after session start: {pattern}")
        missing = [pattern for pattern in required_patterns if not find_pattern(text, pattern)]
        if not missing:
            return text
        if time.monotonic() >= deadline:
            raise RuntimeError(f"timed out waiting for log patterns: {', '.join(missing)}")
        sleep_func(poll_seconds)


def verify_prism_version(prism_app: str, run_func=subprocess.run) -> str:
    binary = Path(prism_app).expanduser().resolve() / "Contents/MacOS/prismlauncher"
    if not binary.is_file():
        raise RuntimeError(f"Prism Launcher executable not found: {binary}")
    result = run_func(
        [str(binary), "--version"],
        text=True,
        capture_output=True,
        check=True,
    )
    match = re.fullmatch(r"PrismLauncher\s+(\d+)\.(\d+)\.(\d+)\s*", result.stdout)
    if match is None:
        raise RuntimeError(f"Unable to parse Prism Launcher version: {result.stdout.strip()}")
    version = tuple(int(part) for part in match.groups())
    version_text = ".".join(match.groups())
    if version < MINIMUM_PRISM_VERSION:
        raise RuntimeError(
            "Prism Launcher 11.0.3 or newer is required for the offline CLI launch; "
            f"found {version_text}"
        )
    return version_text


def verify_no_prism_auth_refresh(text: str) -> None:
    lower_text = text.lower()
    for pattern in PRISM_AUTH_FORBIDDEN_PATTERNS:
        if pattern.lower() in lower_text:
            raise RuntimeError(
                f"Prism authentication activity detected in current launcher log: {pattern}"
            )


def sanitize_prism_launcher_log(text: str) -> str:
    sanitized = []
    skip_environment_values = False
    for line in text.splitlines(keepends=True):
        if "Process environment:" in line or "Native environment:" in line:
            skip_environment_values = True
            continue
        if skip_environment_values and "QList(" in line:
            skip_environment_values = False
            continue
        skip_environment_values = False
        sanitized.append(line)
    return "".join(sanitized)


def build_launch_command(prism_app: str, instance: str, world: str) -> list[str]:
    binary = Path(prism_app).expanduser().resolve() / "Contents/MacOS/prismlauncher"
    return [
        str(binary),
        "-l",
        instance,
        "-w",
        world,
        "-o",
        DEFAULT_OFFLINE_PLAYER,
    ]


def default_launcher(command: list[str], popen_func=subprocess.Popen, environ=os.environ) -> None:
    launch_environment = {key: environ[key] for key in PRISM_LAUNCH_ENV_KEYS if key in environ}
    popen_func(
        command,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        env=launch_environment,
        start_new_session=True,
        close_fds=True,
    )


def snapshot_processes(run_func=subprocess.run) -> dict[int, tuple[int, str]]:
    result = run_func(
        ["ps", "-axo", "pid=,ppid=,command="],
        text=True,
        capture_output=True,
        check=True,
    )
    processes = {}
    for line_number, line in enumerate(result.stdout.splitlines(), start=1):
        if not line.strip():
            continue
        fields = line.strip().split(None, 2)
        if len(fields) != 3:
            raise RuntimeError(f"unable to parse process snapshot line {line_number}: {line}")
        try:
            pid = int(fields[0])
            parent_pid = int(fields[1])
        except ValueError as exc:
            raise RuntimeError(f"unable to parse process ids on snapshot line {line_number}: {line}") from exc
        processes[pid] = (parent_pid, fields[2])
    return processes


def is_prism_launcher_command(command: str) -> bool:
    lower_command = command.lower()
    return "prismlauncher" in lower_command or "/prism launcher.app/" in lower_command


def require_running_normal_prism(snapshot_func=None) -> None:
    snapshot_func = snapshot_processes if snapshot_func is None else snapshot_func
    for _, command in snapshot_func().values():
        if not is_prism_launcher_command(command):
            continue
        if re.search(r"(?:^|\s)(?:-d|--dir)(?:=|\s)", command) is None:
            return
    raise RuntimeError(
        "Open the normal Prism Launcher once and wait for account initialization to finish, then leave it running and rerun this GUI scenario. "
        "The runner refuses to start a new launcher process because Prism refreshes Microsoft/Xbox ownership on every cold start even when -o is used."
    )


def process_is_alive(pid: int) -> bool:
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError as exc:
        raise RuntimeError(f"permission denied while checking process {pid}") from exc
    return True


def terminate_processes(
    pids: list[int],
    timeout_seconds: float = DEFAULT_PROCESS_TERMINATION_TIMEOUT_SECONDS,
    kill_func=os.kill,
    process_alive_func=process_is_alive,
    sleep_func=time.sleep,
) -> None:
    ordered_pids = list(dict.fromkeys(pids))
    if any(pid <= 1 for pid in ordered_pids):
        raise RuntimeError(f"refusing to terminate unsafe process ids: {ordered_pids}")

    for pid in ordered_pids:
        if not process_alive_func(pid):
            continue
        try:
            kill_func(pid, signal.SIGTERM)
        except ProcessLookupError:
            continue

    deadline = time.monotonic() + timeout_seconds
    remaining = [pid for pid in ordered_pids if process_alive_func(pid)]
    while remaining and time.monotonic() < deadline:
        sleep_func(0.1)
        remaining = [pid for pid in remaining if process_alive_func(pid)]

    for pid in remaining:
        try:
            kill_func(pid, signal.SIGKILL)
        except ProcessLookupError:
            continue

    kill_deadline = time.monotonic() + timeout_seconds
    remaining = [pid for pid in remaining if process_alive_func(pid)]
    while remaining and time.monotonic() < kill_deadline:
        sleep_func(0.1)
        remaining = [pid for pid in remaining if process_alive_func(pid)]
    if remaining:
        raise RuntimeError(f"failed to terminate runner-started processes: {remaining}")


def runner_started_process_ids(
    baseline: dict[int, tuple[int, str]],
    current: dict[int, tuple[int, str]],
    instance_dir: Path,
    minecraft_dir: Path,
    instance: str,
    world: str,
) -> list[int]:
    new_processes = {pid: process for pid, process in current.items() if pid not in baseline}
    targets = set()

    for pid, (_, command) in new_processes.items():
        is_runner_prism = is_prism_launcher_command(command) and all(
            marker in command
            for marker in [f"-l {instance}", f"-w {world}", f"-o {DEFAULT_OFFLINE_PLAYER}"]
        )
        is_instance_java = is_instance_java_command(command, instance_dir, minecraft_dir)
        if is_runner_prism or is_instance_java:
            targets.add(pid)

    for pid in list(targets):
        seen = {pid}
        parent_pid = new_processes[pid][0]
        while parent_pid in new_processes:
            if parent_pid in seen:
                raise RuntimeError(f"cycle detected in process tree at pid {parent_pid}")
            seen.add(parent_pid)
            if is_prism_launcher_command(new_processes[parent_pid][1]):
                targets.add(parent_pid)
            parent_pid = new_processes[parent_pid][0]

    changed = True
    while changed:
        changed = False
        for pid, (parent_pid, _) in new_processes.items():
            if pid not in targets and parent_pid in targets:
                targets.add(pid)
                changed = True

    def process_depth(pid: int) -> int:
        depth = 0
        seen = {pid}
        parent_pid = current[pid][0]
        while parent_pid in targets:
            if parent_pid in seen:
                raise RuntimeError(f"cycle detected in process tree at pid {parent_pid}")
            seen.add(parent_pid)
            depth += 1
            parent_pid = current[parent_pid][0]
        return depth

    return sorted(targets, key=lambda pid: (process_depth(pid), pid), reverse=True)


def is_instance_java_command(command: str, instance_dir: Path, minecraft_dir: Path) -> bool:
    is_java = re.search(r"(?:^|/)java(?:\s|$)", command) is not None
    if not is_java:
        return False
    if str(instance_dir) in command or str(minecraft_dir) in command:
        return True
    user_dir_match = re.search(r'-Duser\.dir=(?:"([^"]+)"|\'([^\']+)\'|(\S+))', command)
    if user_dir_match is None:
        return False
    user_dir = Path(next(value for value in user_dir_match.groups() if value is not None)).expanduser().resolve()
    return user_dir in {instance_dir.expanduser().resolve(), minecraft_dir.expanduser().resolve()}


def cleanup_existing_session(
    instance_dir: Path,
    minecraft_dir: Path,
    instance: str,
    world: str,
    snapshot_func=None,
    terminate_func=None,
) -> None:
    snapshot_func = snapshot_processes if snapshot_func is None else snapshot_func
    terminate_func = terminate_processes if terminate_func is None else terminate_func
    current = snapshot_func()
    targets = {
        pid
        for pid, (_, command) in current.items()
        if is_instance_java_command(command, instance_dir, minecraft_dir)
    }
    changed = True
    while changed:
        changed = False
        for pid, (parent_pid, _) in current.items():
            if pid not in targets and parent_pid in targets:
                targets.add(pid)
                changed = True
    if targets:
        terminate_func(sorted(targets, reverse=True))


def supervise_shutdown(
    expected_processes: dict[int, str],
    log_path: Path,
    cursor: LogCursor,
    run_dir: Path,
    stall_timeout_seconds: float = DEFAULT_SHUTDOWN_STALL_TIMEOUT_SECONDS,
    poll_seconds: float = 0.25,
    snapshot_func=None,
    terminate_func=None,
    monotonic_func=time.monotonic,
    sleep_func=time.sleep,
) -> dict:
    snapshot_func = snapshot_processes if snapshot_func is None else snapshot_func
    terminate_func = terminate_processes if terminate_func is None else terminate_func
    stopping_since = None
    stopping_detected = False
    while True:
        current = snapshot_func()
        matching = {
            pid: command
            for pid, command in expected_processes.items()
            if pid in current and current[pid][1] == command
        }
        if not matching:
            result = {
                "status": "graceful",
                "stopping_detected": stopping_detected,
                "forced_pids": [],
                "stall_timeout_seconds": stall_timeout_seconds,
            }
            write_json(run_dir / "shutdown.json", result)
            return result

        if STOPPING_LOG_PATTERN in read_log_since(log_path, cursor):
            stopping_detected = True
            if stopping_since is None:
                stopping_since = monotonic_func()
            elif monotonic_func() - stopping_since >= stall_timeout_seconds:
                forced_pids = sorted(matching)
                terminate_func(forced_pids)
                result = {
                    "status": "forced_after_glfw_shutdown_stall",
                    "stopping_detected": True,
                    "forced_pids": forced_pids,
                    "stall_timeout_seconds": stall_timeout_seconds,
                }
                write_json(run_dir / "shutdown.json", result)
                return result
        sleep_func(poll_seconds)


def start_shutdown_watchdog(
    expected_processes: dict[int, str],
    log_path: Path,
    cursor: LogCursor,
    run_dir: Path,
    popen_func=subprocess.Popen,
) -> None:
    config_path = run_dir / "shutdown-watchdog.json"
    write_json(config_path, {
        "expected_processes": {str(pid): command for pid, command in expected_processes.items()},
        "log_path": str(log_path),
        "cursor": {"size": cursor.size, "device": cursor.device, "inode": cursor.inode},
        "run_dir": str(run_dir),
        "stall_timeout_seconds": DEFAULT_SHUTDOWN_STALL_TIMEOUT_SECONDS,
    })
    watchdog_log = (run_dir / "shutdown-watchdog.log").open("ab")
    try:
        popen_func(
            [sys.executable, str(Path(__file__).resolve()), "--watchdog-config", str(config_path)],
            stdin=subprocess.DEVNULL,
            stdout=watchdog_log,
            stderr=subprocess.STDOUT,
            start_new_session=True,
            close_fds=True,
        )
    finally:
        watchdog_log.close()


def run_shutdown_watchdog(config_path: Path) -> dict:
    config = json.loads(config_path.read_text())
    cursor_data = config["cursor"]
    return supervise_shutdown(
        {int(pid): command for pid, command in config["expected_processes"].items()},
        Path(config["log_path"]),
        LogCursor(cursor_data["size"], cursor_data["device"], cursor_data["inode"]),
        Path(config["run_dir"]),
        stall_timeout_seconds=float(config["stall_timeout_seconds"]),
    )


def verify_desktop_display_mode(manifest: dict, mode_func=current_macos_main_display_mode) -> DisplayMode:
    expected_data = manifest.get("desktop_display_mode")
    if not isinstance(expected_data, dict):
        raise RuntimeError("GUI manifest is missing the captured macOS desktop display mode")
    try:
        expected = DisplayMode(**expected_data)
    except (TypeError, ValueError) as exc:
        raise RuntimeError("GUI manifest contains an invalid macOS desktop display mode") from exc
    actual = mode_func()
    if actual != expected:
        raise RuntimeError(
            "Minecraft changed the macOS desktop display mode: "
            f"expected {expected.width}x{expected.height}@{expected.refresh_rate} "
            f"({expected.pixel_width}x{expected.pixel_height} pixels), found "
            f"{actual.width}x{actual.height}@{actual.refresh_rate} "
            f"({actual.pixel_width}x{actual.pixel_height} pixels)"
        )
    return actual


def cleanup_started_processes(
    baseline: dict[int, tuple[int, str]],
    instance_dir: Path,
    minecraft_dir: Path,
    instance: str,
    world: str,
) -> None:
    current = snapshot_processes()
    targets = runner_started_process_ids(baseline, current, instance_dir, minecraft_dir, instance, world)
    if targets:
        terminate_processes(targets)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_deployed_magic_storage_jar(project_dir: Path, minecraft_dir: Path) -> None:
    project_dir = project_dir.expanduser().resolve()
    minecraft_dir = minecraft_dir.expanduser().resolve()
    properties_path = project_dir / "gradle.properties"
    if not properties_path.is_file():
        raise RuntimeError(f"Gradle properties not found: {properties_path}. {DEPLOY_REQUIRED_MESSAGE}")
    versions = [
        line.split("=", 1)[1].strip()
        for line in properties_path.read_text().splitlines()
        if line.split("=", 1)[0].strip() == "mod_version" and "=" in line
    ]
    if len(versions) != 1 or not versions[0]:
        raise RuntimeError(f"Expected exactly one mod_version in {properties_path}. {DEPLOY_REQUIRED_MESSAGE}")

    build_jar = project_dir / "build" / "libs" / f"magic_storage-{versions[0]}.jar"
    if not build_jar.is_file():
        raise RuntimeError(f"Current Magic Storage build jar not found: {build_jar}. {DEPLOY_REQUIRED_MESSAGE}")

    mods_dir = minecraft_dir / "mods"
    deployed_jars = sorted(mods_dir.glob("magic_storage-*.jar")) if mods_dir.is_dir() else []
    if len(deployed_jars) != 1:
        found = ", ".join(path.name for path in deployed_jars) or "none"
        raise RuntimeError(
            f"Expected exactly one Magic Storage jar in Prism dev mods at {mods_dir}, found {len(deployed_jars)}: {found}. "
            f"{DEPLOY_REQUIRED_MESSAGE}"
        )

    deployed_jar = deployed_jars[0]
    if deployed_jar.name != build_jar.name:
        raise RuntimeError(
            f"Prism dev Magic Storage jar version does not match the current build: expected {build_jar.name}, "
            f"found {deployed_jar.name}. {DEPLOY_REQUIRED_MESSAGE}"
        )

    build_hash = sha256_file(build_jar)
    deployed_hash = sha256_file(deployed_jar)
    if build_hash != deployed_hash:
        raise RuntimeError(
            f"Magic Storage jar contents differ for {build_jar.name}: build SHA-256 {build_hash}, "
            f"Prism dev SHA-256 {deployed_hash}. {DEPLOY_REQUIRED_MESSAGE}"
        )


def verify_deployed_fusion_jar(minecraft_dir: Path) -> None:
    mods_dir = minecraft_dir.expanduser().resolve() / "mods"
    deployed_jars = fusion_jars(mods_dir) if mods_dir.is_dir() else []
    if len(deployed_jars) != 1 or deployed_jars[0].name != FUSION_FILENAME:
        found = ", ".join(path.name for path in deployed_jars) or "none"
        raise RuntimeError(
            f"Expected exactly one Fusion jar named {FUSION_FILENAME} in Prism dev mods at {mods_dir}, "
            f"found {len(deployed_jars)}: {found}. {DEPLOY_REQUIRED_MESSAGE}"
        )
    deployed_hash = sha512(deployed_jars[0])
    if deployed_hash != FUSION_SHA512:
        raise RuntimeError(
            f"Fusion jar contents differ for {FUSION_FILENAME}: expected SHA-512 {FUSION_SHA512}, "
            f"found {deployed_hash}. {DEPLOY_REQUIRED_MESSAGE}"
        )


def verify_deployed_gui_support_jars(project_dir: Path, minecraft_dir: Path) -> None:
    project_dir = project_dir.expanduser().resolve()
    mods_dir = minecraft_dir.expanduser().resolve() / "mods"
    staged_dir = project_dir / "build" / "prism-gui-mods"
    incompatible_jei = jei_jars(mods_dir) if mods_dir.is_dir() else []
    if incompatible_jei:
        found = ", ".join(path.name for path in incompatible_jei)
        raise RuntimeError(
            f"JEI is incompatible with TMRV and must not be installed in Prism dev mods: {found}. "
            f"{DEPLOY_REQUIRED_MESSAGE}"
        )
    labels = {
        "iron-furnaces": "Iron Furnaces",
        "farmers-delight": "Farmer's Delight",
        "tmrv": "TMRV",
        "guideme": "GuideME",
    }
    for filename, finder in SUPPORT_ARTIFACTS:
        key = filename.removesuffix("-gui-test.jar")
        label = labels.get(key, key.replace("-", " ").title())
        staged = staged_dir / filename
        if not staged.is_file():
            raise RuntimeError(
                f"Staged {label} GUI support jar not found: {staged}. {DEPLOY_REQUIRED_MESSAGE}"
            )
        deployed = finder(mods_dir) if mods_dir.is_dir() else []
        expected = mods_dir / filename
        if deployed != [expected]:
            found = ", ".join(path.name for path in deployed) or "none"
            raise RuntimeError(
                f"Expected exactly one {label} GUI support jar named {filename} in {mods_dir}, "
                f"found {len(deployed)}: {found}. {DEPLOY_REQUIRED_MESSAGE}"
            )
        if sha256_file(staged) != sha256_file(expected):
            raise RuntimeError(
                f"{label} GUI support jar contents differ for {filename}. {DEPLOY_REQUIRED_MESSAGE}"
            )


def configure_instance_for_manual_handoff(instance_dir: Path) -> bool:
    instance_cfg = instance_dir.expanduser().resolve() / "instance.cfg"
    if not instance_cfg.is_file():
        raise RuntimeError(f"Prism instance config not found: {instance_cfg}")
    original = instance_cfg.read_text()
    overrides = {
        "OverrideConsole": "true",
        "ShowConsole": "false",
        "ShowConsoleOnError": "false",
        "WrapperCommand": "",
    }
    output = []
    found = set()
    for line in original.splitlines():
        key = line.split("=", 1)[0]
        if key in overrides:
            output.append(f"{key}={overrides[key]}")
            found.add(key)
        else:
            output.append(line)
    for key, value in overrides.items():
        if key not in found:
            output.append(f"{key}={value}")
    updated = "\n".join(output) + "\n"
    if updated == original:
        return False
    instance_cfg.write_text(updated)
    return True


def write_json(path: Path, data: dict) -> None:
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n")


def write_checklist(
    path: Path,
    scenario_name: str,
    scenario: dict,
    manifest: dict,
    launch_command: list[str],
    log_path: Path,
) -> None:
    manual = "yes" if scenario["manual_gui_required"] else "no"
    lines = [
        f"# Prism GUI Session — {scenario_name}",
        "",
        f"Description: {scenario['description']}",
        f"Manual GUI required: {manual}",
        f"Visual verification owner: {'user' if scenario['manual_gui_required'] else 'none'}",
        f"Log path: `{log_path}`",
        f"Launch command: `{' '.join(launch_command)}`",
        "",
        "## Fullscreen gate",
        "",
        "Minecraft automatically starts in borderless Minecraft F11 fullscreen; wait for the current MS_GUI_TEST_READY log line before taking control.",
        "On macOS the client never attaches the GLFW window to the monitor, and the runner fails closed if the desktop display mode changes.",
        "Do not use the macOS green fullscreen button or Control-Command-F, and never combine native fullscreen with Minecraft F11 fullscreen.",
        "Do not perform hotbar/use/click/scroll/screenshot actions until the fullscreen gate is visually confirmed.",
        "To close this test session safely, press F11 once, wait until the normal window is visible, then press Command-Q.",
        "Do not press Command-Q while Minecraft F11 fullscreen is still active.",
        "A runner-owned shutdown watchdog terminates only this test client if the Minecraft shutdown log appears but the Java process stalls for five seconds.",
        "",
        "## Scenario steps",
        "",
    ]
    if scenario["manual_gui_required"]:
        lines.extend([
            "Stop automation here and hand control to the user after Minecraft reaches MS_GUI_TEST_READY.",
            "The user performs the fullscreen gate and the scenario steps below.",
            "",
        ])
    for check in scenario["checks"]:
        lines.append(f"- {check}")
    if scenario["hotbar_keys"]:
        lines.extend(["", "## Hotbar targets", ""])
        hotbar = manifest.get("hotbar_views", {})
        for key in scenario["hotbar_keys"]:
            view = hotbar.get(key, {})
            target = view.get("target", "unknown")
            lines.append(f"- hotbar `{key}` → `{target}`")
    if not scenario["manual_gui_required"]:
        lines.extend(["", "No visual pass is required for this scenario unless the current code change touches visible GUI/Patchouli behavior."])
    path.write_text("\n".join(lines) + "\n")


def session_to_json(result: SessionResult, instance_cfg_changed: bool, log_path: Path) -> dict:
    fullscreen_gate = result.manifest["fullscreen_gate"]
    return {
        "scenario": result.scenario,
        "manual_gui_required": result.manual_gui_required,
        "manual_handoff_required": result.manual_gui_required,
        "manual_handoff_message": result.manual_handoff_message,
        "visual_verification_owner": "user" if result.manual_gui_required else "none",
        "run_dir": str(result.run_dir),
        "launch_command": result.launch_command,
        "log_path": str(log_path),
        "manifest": result.manifest,
        "launch_profile": {
            "offline_player": DEFAULT_OFFLINE_PLAYER,
            "uses_existing_prism_root": True,
            "computer_use_wrapper_disabled": True,
            "error_console_disabled": True,
            "fullscreen_mode": fullscreen_gate["accepted_methods"][0],
            "automatic_fullscreen": fullscreen_gate["automatic"],
            "desktop_display_mode": result.manifest["desktop_display_mode"],
            "instance_cfg_changed": instance_cfg_changed,
        },
        "shutdown_profile": {
            "safe_sequence": "f11_then_command_q",
            "watchdog_enabled": result.manual_gui_required,
            "stall_timeout_seconds": DEFAULT_SHUTDOWN_STALL_TIMEOUT_SECONDS,
            "direct_command_q_while_fullscreen": "forbidden",
        },
    }


def run_session(
    scenario_name: str,
    minecraft_dir: Path = DEFAULT_PRISM_MINECRAFT_DIR,
    instance_dir: Path = DEFAULT_INSTANCE_DIR,
    run_root: Path = DEFAULT_RUN_ROOT,
    source_world: str = DEFAULT_SOURCE_WORLD,
    world: str = DEFAULT_WORLD_NAME,
    prism_app: str = DEFAULT_PRISM_APP,
    instance: str = "dev",
    timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS,
    poll_seconds: float = 0.25,
    no_launch: bool = False,
    prepare_world_func=prepare_world,
    cleanup_existing_func=cleanup_existing_session,
    configure_instance_func=configure_instance_for_manual_handoff,
    auth_verifier=None,
    launcher=default_launcher,
    wait_for_log_func=wait_for_log_patterns,
    display_mode_verifier=None,
    watchdog_launcher=start_shutdown_watchdog,
    timestamp_func=timestamp,
) -> SessionResult:
    if scenario_name not in SCENARIOS:
        raise RuntimeError(f"unknown GUI scenario: {scenario_name}")
    scenario = SCENARIOS[scenario_name]
    if not no_launch:
        verify_prism_version(prism_app)
    minecraft_dir = minecraft_dir.expanduser().resolve()
    instance_dir = instance_dir.expanduser().resolve()
    run_root = run_root.expanduser().resolve()

    if not no_launch:
        require_running_normal_prism()
        verify_deployed_magic_storage_jar(DEFAULT_PROJECT_DIR, minecraft_dir)
        verify_deployed_fusion_jar(minecraft_dir)
        if scenario_name == "crafting-fuel-page":
            verify_deployed_gui_support_jars(DEFAULT_PROJECT_DIR, minecraft_dir)
        cleanup_existing_func(instance_dir, minecraft_dir, instance, world)
    instance_cfg_changed = configure_instance_func(instance_dir)
    manifest = prepare_world_func(
        minecraft_dir,
        source_world,
        world,
        scenario_name=scenario_name,
    )
    log_path = minecraft_dir / "logs" / "latest.log"
    offset = log_cursor(log_path)

    run_dir = run_root / f"{timestamp_func()}-{scenario_name}"
    run_dir.mkdir(parents=True, exist_ok=False)
    prism_log_path = instance_dir.parent.parent / "logs" / "PrismLauncher-0.log"
    prism_log_offset = log_cursor(prism_log_path)
    launch_command = build_launch_command(prism_app, instance, world)
    write_json(run_dir / "manifest.json", manifest)
    write_checklist(run_dir / "checklist.md", scenario_name, scenario, manifest, launch_command, log_path)

    process_baseline = None
    try:
        if no_launch:
            log_excerpt = ""
        else:
            process_baseline = snapshot_processes()
            launcher(launch_command)
            log_excerpt = wait_for_log_func(
                log_path=log_path,
                offset=offset,
                required_patterns=DEFAULT_REQUIRED_PATTERNS,
                forbidden_patterns=DEFAULT_FORBIDDEN_PATTERNS,
                timeout_seconds=timeout_seconds,
                poll_seconds=poll_seconds,
            )
            if not prism_log_path.is_file():
                raise RuntimeError(f"Prism launcher log not found: {prism_log_path}")
            prism_log_text = read_log_since(prism_log_path, prism_log_offset)
            verify_auth = verify_no_prism_auth_refresh if auth_verifier is None else auth_verifier
            verify_auth(prism_log_text)
            (run_dir / "prism-launcher.log").write_text(sanitize_prism_launcher_log(prism_log_text))
            verifier = verify_desktop_display_mode if display_mode_verifier is None else display_mode_verifier
            verifier(manifest)
        (run_dir / "log-excerpt.log").write_text(log_excerpt)

        result = SessionResult(
            scenario=scenario_name,
            manual_gui_required=bool(scenario["manual_gui_required"]),
            manual_handoff_message=MANUAL_HANDOFF_MESSAGE if scenario["manual_gui_required"] and not no_launch else None,
            run_dir=run_dir,
            launch_command=launch_command,
            log_excerpt=log_excerpt,
            manifest=manifest,
        )
        write_json(run_dir / "session.json", session_to_json(result, instance_cfg_changed, log_path))
        if process_baseline is not None and scenario["manual_gui_required"]:
            current_processes = snapshot_processes()
            owned_pids = runner_started_process_ids(
                process_baseline,
                current_processes,
                instance_dir,
                minecraft_dir,
                instance,
                world,
            )
            expected_java = {
                pid: current_processes[pid][1]
                for pid in owned_pids
                if is_instance_java_command(current_processes[pid][1], instance_dir, minecraft_dir)
            }
            if expected_java:
                watchdog_launcher(expected_java, log_path, log_cursor(log_path), run_dir)
    except BaseException as session_error:
        if process_baseline is not None:
            try:
                cleanup_started_processes(process_baseline, instance_dir, minecraft_dir, instance, world)
            except Exception as cleanup_error:
                raise RuntimeError(
                    f"Prism session failed before handoff ({session_error}); process cleanup also failed ({cleanup_error})"
                ) from session_error
        raise

    if process_baseline is not None and not scenario["manual_gui_required"]:
        cleanup_started_processes(process_baseline, instance_dir, minecraft_dir, instance, world)
    return result


def main(argv: list[str]) -> int:
    if len(argv) == 3 and argv[1] == "--watchdog-config":
        try:
            result = run_shutdown_watchdog(Path(argv[2]))
        except Exception as exc:
            print(str(exc), file=sys.stderr)
            return 1
        print(json.dumps(result, indent=2, ensure_ascii=False))
        return 0
    parser = argparse.ArgumentParser(description="Prepare and launch the fixed Prism GUI test world with current-run log polling.")
    parser.add_argument("--scenario", choices=sorted(SCENARIOS), default="boot-smoke")
    parser.add_argument("--minecraft-dir", type=Path, default=DEFAULT_PRISM_MINECRAFT_DIR)
    parser.add_argument("--instance-dir", type=Path, default=DEFAULT_INSTANCE_DIR)
    parser.add_argument("--run-root", type=Path, default=DEFAULT_RUN_ROOT)
    parser.add_argument("--source-world", default=DEFAULT_SOURCE_WORLD)
    parser.add_argument("--world", default=DEFAULT_WORLD_NAME)
    parser.add_argument("--prism-app", default=DEFAULT_PRISM_APP)
    parser.add_argument("--instance", default="dev")
    parser.add_argument("--timeout", type=float, default=DEFAULT_TIMEOUT_SECONDS)
    parser.add_argument("--poll", type=float, default=0.25)
    parser.add_argument("--no-launch", action="store_true", help="Prepare artifacts/checklist without launching Prism or waiting for logs.")
    args = parser.parse_args(argv[1:])
    try:
        result = run_session(
            scenario_name=args.scenario,
            minecraft_dir=args.minecraft_dir,
            instance_dir=args.instance_dir,
            run_root=args.run_root,
            source_world=args.source_world,
            world=args.world,
            prism_app=args.prism_app,
            instance=args.instance,
            timeout_seconds=args.timeout,
            poll_seconds=args.poll,
            no_launch=args.no_launch,
        )
    except Exception as exc:
        print(str(exc), file=sys.stderr)
        return 1
    print(json.dumps({
        "scenario": result.scenario,
        "manual_gui_required": result.manual_gui_required,
        "manual_handoff_message": result.manual_handoff_message,
        "run_dir": str(result.run_dir),
        "launch_command": result.launch_command,
    }, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
