#!/usr/bin/env python3
import argparse
import json
import shlex
import stat
import sys
from pathlib import Path
from typing import NamedTuple

BUNDLE_ID = "run.hapi.autostorage.minecraftcu"
APP_NAME = "AutoStorageMinecraftCU"
DEFAULT_INSTANCE_DIR = Path.home() / "Library/Application Support/PrismLauncher/instances/dev"
DEFAULT_APP_PATH = Path("/tmp/AutoStorageMinecraftCU.app")
DEFAULT_WRAPPER_PATH = Path("/tmp/auto_storage_minecraft_cu_wrapper.sh")
LOG_PATH = Path("/tmp/auto_storage_minecraft_cu.log")


class SetupResult(NamedTuple):
    bundle_id: str
    app_path: Path
    app_executable: Path
    wrapper_path: Path
    instance_cfg: Path
    instance_cfg_changed: bool


def _chmod_executable(path: Path) -> None:
    mode = path.stat().st_mode
    path.chmod(mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)


def app_executable_path(app_path: Path) -> Path:
    return app_path / "Contents" / "MacOS" / APP_NAME


def info_plist_text() -> str:
    return f"""<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">
<plist version=\"1.0\">
<dict>
    <key>CFBundleDisplayName</key>
    <string>{APP_NAME}</string>
    <key>CFBundleExecutable</key>
    <string>{APP_NAME}</string>
    <key>CFBundleIdentifier</key>
    <string>{BUNDLE_ID}</string>
    <key>CFBundleName</key>
    <string>{APP_NAME}</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>LSMinimumSystemVersion</key>
    <string>12.0</string>
</dict>
</plist>
"""


def app_executable_text(log_path: Path = LOG_PATH) -> str:
    quoted_log_path = shlex.quote(str(log_path))
    return f"""#!/bin/zsh
set -u
LOG={quoted_log_path}
{{
  echo \"=== app launch $(date -Iseconds) pid=$$ ===\"
  echo \"raw argc=$#\"
  printf 'raw argv:'; printf ' [%q]' \"$@\"; echo
}} >> \"$LOG\" 2>&1
cwd=\"\"
stdin_path=\"\"
while [[ $# -gt 0 ]]; do
  case \"$1\" in
    --mscu-cwd=*) cwd=\"${{1#--mscu-cwd=}}\"; shift ;;
    --mscu-stdin=*) stdin_path=\"${{1#--mscu-stdin=}}\"; shift ;;
    *) break ;;
  esac
done
if [[ -n \"$cwd\" ]]; then
  cd \"$cwd\" || {{ echo \"cd failed: $cwd\" >> \"$LOG\"; exit 111; }}
fi
{{
  echo \"cwd=$(pwd)\"
  echo \"stdin_path=$stdin_path\"
  echo \"exec argc=$#\"
  printf 'exec argv:'; printf ' [%q]' \"$@\"; echo
  echo \"--- child stdout/stderr follows ---\"
}} >> \"$LOG\" 2>&1
if [[ $# -eq 0 ]]; then
  echo \"no command, sleeping for probe\" >> \"$LOG\"
  exec /bin/sleep 300
fi
if [[ -n \"$stdin_path\" ]]; then
  exec \"$@\" < \"$stdin_path\" >> \"$LOG\" 2>&1
else
  exec \"$@\" >> \"$LOG\" 2>&1
fi
"""


def wrapper_text(app_path: Path, log_path: Path = LOG_PATH) -> str:
    quoted_app_path = shlex.quote(str(app_path))
    quoted_log_path = shlex.quote(str(log_path))
    return f"""#!/bin/zsh
set -u
APP={quoted_app_path}
LOG={quoted_log_path}
cwd=\"$(pwd)\"
fifo=\"/tmp/auto_storage_minecraft_cu_stdin.$$\"
mkfifo \"$fifo\" || exit 112
cleanup() {{
  [[ -n \"${{cat_pid:-}}\" ]] && kill \"$cat_pid\" 2>/dev/null || true
  rm -f \"$fifo\"
}}
trap cleanup EXIT INT TERM
{{
  echo \"=== wrapper launch $(date -Iseconds) pid=$$ cwd=$cwd fifo=$fifo ===\"
  echo \"argc=$#\"
  printf 'argv:'; printf ' [%q]' \"$@\"; echo
}} >> \"$LOG\" 2>&1
cat > \"$fifo\" &
cat_pid=$!
/usr/bin/open -n -W \"$APP\" --args \"--mscu-cwd=$cwd\" \"--mscu-stdin=$fifo\" \"$@\"
exit_status=$?
{{
  echo \"=== wrapper exit $(date -Iseconds) pid=$$ status=$exit_status ===\"
}} >> \"$LOG\" 2>&1
exit \"$exit_status\"
"""


def write_app_bundle(app_path: Path) -> Path:
    executable = app_executable_path(app_path)
    executable.parent.mkdir(parents=True, exist_ok=True)
    (app_path / "Contents").mkdir(parents=True, exist_ok=True)
    (app_path / "Contents" / "Info.plist").write_text(info_plist_text())
    executable.write_text(app_executable_text())
    _chmod_executable(executable)
    return executable


def write_wrapper(wrapper_path: Path, app_path: Path) -> None:
    wrapper_path.parent.mkdir(parents=True, exist_ok=True)
    wrapper_path.write_text(wrapper_text(app_path))
    _chmod_executable(wrapper_path)


def patch_instance_cfg(instance_cfg: Path, wrapper_path: Path) -> bool:
    instance_cfg.parent.mkdir(parents=True, exist_ok=True)
    original = instance_cfg.read_text() if instance_cfg.exists() else ""
    updates = {
        "OverrideCommands": "true",
        "WrapperCommand": str(wrapper_path),
    }
    seen = set()
    output = []
    for line in original.splitlines():
        if "=" in line:
            key, _ = line.split("=", 1)
            if key in updates:
                output.append(f"{key}={updates[key]}")
                seen.add(key)
                continue
        output.append(line)
    for key, value in updates.items():
        if key not in seen:
            output.append(f"{key}={value}")
    updated = "\n".join(output) + ("\n" if output else "")
    if updated != original:
        instance_cfg.write_text(updated)
        return True
    return False


def setup(
    instance_dir: Path = DEFAULT_INSTANCE_DIR,
    app_path: Path = DEFAULT_APP_PATH,
    wrapper_path: Path = DEFAULT_WRAPPER_PATH,
) -> SetupResult:
    instance_dir = instance_dir.expanduser().resolve()
    app_path = app_path.expanduser().absolute()
    wrapper_path = wrapper_path.expanduser().absolute()
    app_executable = write_app_bundle(app_path)
    write_wrapper(wrapper_path, app_path)
    instance_cfg = instance_dir / "instance.cfg"
    cfg_changed = patch_instance_cfg(instance_cfg, wrapper_path)
    return SetupResult(
        bundle_id=BUNDLE_ID,
        app_path=app_path,
        app_executable=app_executable,
        wrapper_path=wrapper_path,
        instance_cfg=instance_cfg,
        instance_cfg_changed=cfg_changed,
    )


def result_to_json(result: SetupResult) -> dict:
    return {
        "bundle_id": result.bundle_id,
        "app_path": str(result.app_path),
        "app_executable": str(result.app_executable),
        "wrapper_path": str(result.wrapper_path),
        "instance_cfg": str(result.instance_cfg),
        "instance_cfg_changed": result.instance_cfg_changed,
    }


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="Create the Prism Minecraft Computer Use wrapper app and patch the dev instance.")
    parser.add_argument("--instance-dir", type=Path, default=DEFAULT_INSTANCE_DIR)
    parser.add_argument("--app-path", type=Path, default=DEFAULT_APP_PATH)
    parser.add_argument("--wrapper-path", type=Path, default=DEFAULT_WRAPPER_PATH)
    args = parser.parse_args(argv[1:])
    try:
        result = setup(args.instance_dir, args.app_path, args.wrapper_path)
    except Exception as exc:
        print(str(exc), file=sys.stderr)
        return 1
    print(json.dumps(result_to_json(result), indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
