# -*- coding: utf-8 -*-
"""Install 3-Paper PDF next to an AutoJs6 host build on one device and check what the host discovers (roadmap P0.1).

The script never taps the screen. It installs the host APK (only when the installed versionCode differs) and the
plugin APK, asks the package manager for every discovery component the host resolves (INFO, PDF and EXPLORER_ACTION
services, the viewer, the launcher entry), then optionally opens the host Plugin Center and saves a screenshot for
visual review. Opening the Plugin Center directly needs `adb root` (the activity is not exported), so `--center` is
meant for emulator images that allow root.

Usage:
  py .python/device_plugin_center_check.py --serial emulator-5720 --host-apk <host.apk> [--plugin-apk <plugin.apk>]
                                           [--center --screenshot <out.png>] [--uninstall]

Output: one `PASS` / `FAIL` line per check with its key value, then `RESULT PASS` or `RESULT FAIL` (exit code 1).
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import time
from pathlib import Path

PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.three.paper.pdf"
HOST_PACKAGE = "org.autojs.autojs6"
PLUGIN_CENTER = "org.autojs.autojs.core.plugin.center.PluginCenterActivity"
DEFAULT_CATEGORY = "android.intent.category.DEFAULT"
REMOTE_SCREENSHOT = "/data/local/tmp/paper_p0_plugin_center.png"

# (label, query kind, intent arguments, expected component class relative to the plugin package)
COMPONENTS: tuple[tuple[str, str, list[str], str], ...] = (
    ("info-service", "query-services", ["-a", "org.autojs.plugin.INFO", "-c", "three-paper-pdf"],
     ".service.ThreePaperPdfInfoService"),
    ("pdf-service", "query-services", ["-a", "org.autojs.plugin.PDF", "-c", "pdf"], ".service.ThreePaperPdfService"),
    ("explorer-service", "query-services", ["-a", "org.autojs.plugin.EXPLORER_ACTION", "-c", DEFAULT_CATEGORY],
     ".ThreePaperPdfExplorerActionService"),
    ("wake-activity", "query-activities", ["-a", "org.autojs.plugin.action.WAKE", "-c", DEFAULT_CATEGORY], ".WakeActivity"),
    ("viewer-host-action", "query-activities", ["-a", "org.autojs.plugin.PDF_VIEWER_OPEN", "-c", DEFAULT_CATEGORY],
     ".viewer.ThreePaperPdfViewerActivity"),
    ("viewer-view-pdf", "query-activities",
     ["-a", "android.intent.action.VIEW", "-c", DEFAULT_CATEGORY, "-d", "content://paper.test/a.pdf", "-t", "application/pdf"],
     ".viewer.ThreePaperPdfViewerActivity"),
)


class Device:

    def __init__(self, serial: str) -> None:
        self.serial = serial

    def adb(self, *arguments: str, check: bool = True, timeout: int = 180) -> str:
        result = subprocess.run(["adb", "-s", self.serial, *arguments], capture_output=True, text=True,
                                encoding="utf-8", errors="replace", timeout=timeout)
        if check and result.returncode != 0:
            raise RuntimeError(f"adb {arguments[0]} failed: {(result.stderr or result.stdout).strip()[:200]}")
        return result.stdout

    def shell(self, command: str, check: bool = True) -> str:
        return self.adb("shell", command, check=check)

    def version_code(self, package: str) -> int | None:
        lines = [line for line in self.shell(f"pm list packages --show-versioncode {package}", check=False).splitlines()
                 if line.startswith(f"package:{package} ")]
        if not lines:
            return None
        match = re.search(r"versionCode:(\d+)", lines[0])
        return int(match.group(1)) if match else None


def aapt2() -> str:
    """aapt2 of the newest build tools under ANDROID_HOME / ANDROID_SDK_ROOT, or `aapt2` from PATH."""
    for variable in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk = os.environ.get(variable)
        if sdk:
            tools = sorted((Path(sdk) / "build-tools").glob("*/aapt2*"), key=lambda path: path.parent.name)
            if tools:
                return str(tools[-1])
    return "aapt2"


def apk_version_code(apk: Path) -> int | None:
    """Reads versionCode with aapt2; None when aapt2 is unavailable."""
    try:
        output = subprocess.run([aapt2(), "dump", "badging", str(apk)], capture_output=True, text=True, timeout=60).stdout
    except (OSError, subprocess.TimeoutExpired):
        return None
    match = re.search(r"versionCode='(\d+)'", output)
    return int(match.group(1)) if match else None


def default_plugin_apk(root: Path) -> Path:
    candidates = sorted((root / "app/build/outputs/apk/debug").glob("autojs6-plugin-three-paper-pdf-v*.apk"))
    if not candidates:
        raise SystemExit("FAIL plugin-apk missing (run gradlew :app:assembleDebug)")
    return candidates[-1]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--serial", required=True, help="adb serial of the one device to use")
    parser.add_argument("--host-apk", type=Path, help="AutoJs6 host APK to install when its versionCode differs")
    parser.add_argument("--plugin-apk", type=Path, help="plugin APK (defaults to the debug build of this repository)")
    parser.add_argument("--center", action="store_true", help="open the Plugin Center through adb root and take a screenshot")
    parser.add_argument("--screenshot", type=Path, help="where to save the Plugin Center screenshot (with --center)")
    parser.add_argument("--uninstall", action="store_true", help="uninstall the plugin at the end")
    arguments = parser.parse_args(argv)
    root = Path(__file__).resolve().parents[1]
    device = Device(arguments.serial)
    failures = 0

    def report(ok: bool, label: str, value: object) -> None:
        nonlocal failures
        failures += 0 if ok else 1
        print(f"{'PASS' if ok else 'FAIL'} {label} {value}")

    device.adb("wait-for-device", timeout=300)
    deadline = time.monotonic() + 300
    while device.shell("getprop sys.boot_completed", check=False).strip() != "1":
        if time.monotonic() > deadline:
            report(False, "boot", "sys.boot_completed != 1 after 300 s")
            print("RESULT FAIL")
            return 1
        time.sleep(3)
    report(True, "boot", f"sdk={device.shell('getprop ro.build.version.sdk').strip()} abi={device.shell('getprop ro.product.cpu.abi').strip()}")

    if arguments.host_apk:
        wanted = apk_version_code(arguments.host_apk)
        installed = device.version_code(HOST_PACKAGE)
        if installed is None or wanted is None or installed != wanted:
            device.adb("install", "-r", "-d", str(arguments.host_apk), timeout=600)
        installed = device.version_code(HOST_PACKAGE)
        report(installed is not None, "host", f"versionCode={installed}")
    else:
        installed = device.version_code(HOST_PACKAGE)
        report(installed is not None, "host", f"versionCode={installed} (not reinstalled)")

    plugin_apk = arguments.plugin_apk or default_plugin_apk(root)
    device.adb("install", "-r", "-d", str(plugin_apk), timeout=600)
    plugin_version = device.version_code(PLUGIN_PACKAGE)
    report(plugin_version is not None, "plugin", f"versionCode={plugin_version} apk={plugin_apk.name}")

    for label, kind, intent, expected in COMPONENTS:
        output = device.shell(f"cmd package {kind} --components " + " ".join(intent) + f" -p {PLUGIN_PACKAGE}", check=False)
        components = sorted({line.strip() for line in output.splitlines() if line.strip().startswith(PLUGIN_PACKAGE + "/")})
        target = f"{PLUGIN_PACKAGE}/{expected}"
        report(components == [target], label, components[0].split("/", 1)[1] if len(components) == 1 else components)

    launcher = device.shell(f"cmd package resolve-activity --brief -a android.intent.action.MAIN "
                            f"-c android.intent.category.LAUNCHER {PLUGIN_PACKAGE}", check=False).strip().splitlines()
    report(bool(launcher) and launcher[-1].startswith(PLUGIN_PACKAGE + "/"), "launcher", launcher[-1] if launcher else None)

    if arguments.center:
        device.adb("root", check=False)
        device.adb("wait-for-device", timeout=120)
        device.shell(f"am force-stop {HOST_PACKAGE}", check=False)
        # ANR dialogs of a busy emulator cover the screenshot; emulator images run read-only, so the setting is not kept.
        device.shell("settings put global hide_error_dialogs 1", check=False)
        started = device.shell(f"am start -W -n {HOST_PACKAGE}/{PLUGIN_CENTER}", check=False)
        # A freshly booted emulator often reports `Status: timeout` although the activity comes up a little later, so
        # the resumed activity decides. A System UI ANR dialog of the busy emulator is closed with a broadcast, never a tap.
        resumed = ""
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            resumed = next((line.strip() for line in device.shell("dumpsys activity activities", check=False).splitlines()
                            if "topResumedActivity=" in line), "")
            if PLUGIN_CENTER in resumed:
                break
            time.sleep(3)
        status = "ok" if "Status: ok" in started else "timeout" if "Status: timeout" in started else "other"
        report(PLUGIN_CENTER in resumed, "plugin-center", f"resumed am-start={status}" if PLUGIN_CENTER in resumed else resumed[:160])
        time.sleep(6)
        device.shell("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS", check=False)
        time.sleep(2)
        device.shell(f"screencap -p {REMOTE_SCREENSHOT}")
        target = arguments.screenshot or root / "app/build/reports/device/plugin_center.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        device.adb("pull", REMOTE_SCREENSHOT, str(target))
        device.shell(f"rm -f {REMOTE_SCREENSHOT}", check=False)
        report(target.is_file() and target.stat().st_size > 0, "screenshot", target)

    if arguments.uninstall:
        device.adb("uninstall", PLUGIN_PACKAGE, check=False)
        report(device.version_code(PLUGIN_PACKAGE) is None, "uninstall", PLUGIN_PACKAGE)

    print(f"RESULT {'PASS' if failures == 0 else 'FAIL'}")
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
