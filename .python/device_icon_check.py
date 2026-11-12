# -*- coding: utf-8 -*-
"""Screenshots of the 3-Paper PDF icons in the light and dark system themes (roadmap D24, P0.2).

The script never taps the screen. For each system theme (`cmd uimode night no` / `yes`) it saves:

- `plugin-center-<serial>-<light|dark>.png` (--center): the AutoJs6 Plugin Center. The activity is not exported, so this
  needs `adb root` and is meant for emulator images. `--center-scroll N` swipes the list up N times first when the
  plugin row is below the first screen. The host keeps its own night flag and only syncs it from
  `App.onConfigurationChanged` while its process runs, so the theme is switched with the host in the foreground.
- `launcher-<serial>-<light|dark>.png` (--drawer): the launcher app list, opened with the HOME key and one upward swipe
  on the home screen. Launchers differ per vendor, so the drawer is scripted for the emulator only; a real device is
  checked by the maintainer.

With --aliases (needs `adb root`) the script also enables each of the four launcher aliases in turn in the dark theme,
saves `launcher-<serial>-alias-<mode>.png` and finally returns every alias to its Manifest default state. The plugin
must already be installed (see device_plugin_center_check.py). The original night mode is restored at the end.

Usage:
  py .python/device_icon_check.py --serial emulator-5720 --out <dir> [--center [--center-scroll N]] [--drawer] [--aliases]

Output: one `PASS` / `FAIL` line per check with its key value, then `RESULT PASS` or `RESULT FAIL` (exit code 1). The
pictures themselves are reviewed by eye.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import time
from pathlib import Path

PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.three.paper.pdf"
HOST_PACKAGE = "org.autojs.autojs6"
PLUGIN_CENTER = "org.autojs.autojs.core.plugin.center.PluginCenterActivity"
REMOTE_SCREENSHOT = "/data/local/tmp/paper_p0_icons.png"

# Launcher icon modes in the order of the Manifest; the automatic one is enabled by default (LauncherIcons.kt).
ALIASES = (
    ("light", "AdaptiveLightIconAlias"),
    ("dark", "AdaptiveDarkIconAlias"),
    ("auto", "AdaptiveAutoIconAlias"),
    ("transparent", "TransparentIconAlias"),
)


class Device:

    def __init__(self, serial: str) -> None:
        self.serial = serial

    def adb(self, *arguments: str, check: bool = True, timeout: int = 120) -> str:
        result = subprocess.run(["adb", "-s", self.serial, *arguments], capture_output=True, text=True,
                                encoding="utf-8", errors="replace", timeout=timeout)
        if check and result.returncode != 0:
            raise RuntimeError(f"adb {arguments[0]} failed: {(result.stderr or result.stdout).strip()[:200]}")
        return result.stdout

    def shell(self, command: str, check: bool = True) -> str:
        return self.adb("shell", command, check=check)

    def screenshot(self, target: Path) -> bool:
        self.shell(f"screencap -p {REMOTE_SCREENSHOT}")
        target.parent.mkdir(parents=True, exist_ok=True)
        self.adb("pull", REMOTE_SCREENSHOT, str(target))
        self.shell(f"rm -f {REMOTE_SCREENSHOT}", check=False)
        return target.is_file() and target.stat().st_size > 0

    def night(self) -> bool:
        return self.shell("cmd uimode night", check=False).strip().endswith("yes")

    def set_night(self, night: bool) -> None:
        self.shell(f"cmd uimode night {'yes' if night else 'no'}")
        time.sleep(4)

    def launcher_component(self) -> str:
        lines = self.shell(f"cmd package resolve-activity --brief -a android.intent.action.MAIN "
                           f"-c android.intent.category.LAUNCHER {PLUGIN_PACKAGE}", check=False).strip().splitlines()
        return lines[-1].strip() if lines else ""

    def screen_size(self) -> tuple[int, int]:
        match = re.search(r"(\d+)x(\d+)", self.shell("wm size"))
        return (int(match.group(1)), int(match.group(2))) if match else (1080, 2400)


def open_drawer(device: Device) -> None:
    width, height = device.screen_size()
    device.shell("input keyevent KEYCODE_HOME")
    time.sleep(2)
    device.shell(f"input swipe {width // 2} {height * 4 // 5} {width // 2} {height // 4} 300")
    time.sleep(3)


def wait_plugin_center(device: Device) -> bool:
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        resumed = next((line for line in device.shell("dumpsys activity activities", check=False).splitlines()
                        if "topResumedActivity=" in line), "")
        if PLUGIN_CENTER in resumed:
            return True
        time.sleep(3)
    return False


def set_night_with_host(device: Device, night: bool) -> None:
    """Switches the system theme while the host runs, so the host's persisted night flag follows it."""
    device.shell(f"am start -n {HOST_PACKAGE}/{PLUGIN_CENTER}", check=False)
    wait_plugin_center(device)
    device.set_night(night)
    time.sleep(2)


def open_plugin_center(device: Device, scroll: int) -> bool:
    device.shell(f"am force-stop {HOST_PACKAGE}", check=False)
    device.shell(f"am start -n {HOST_PACKAGE}/{PLUGIN_CENTER}", check=False)
    if not wait_plugin_center(device):
        return False
    time.sleep(6)
    # A System UI ANR dialog of a busy emulator is closed with a broadcast, never a tap.
    device.shell("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS", check=False)
    time.sleep(2)
    width, height = device.screen_size()
    if scroll:
        # The host fills the list progressively; swiping before every plugin row is there scrolls nothing.
        time.sleep(20)
    for _ in range(scroll):
        device.shell(f"input swipe {width // 2} {height * 3 // 4} {width // 2} {height // 4} 400")
        time.sleep(2)
    return True


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--serial", required=True, help="adb serial of the one device to use")
    parser.add_argument("--out", type=Path, required=True, help="directory for the screenshots")
    parser.add_argument("--center", action="store_true", help="Plugin Center screenshots (adb root)")
    parser.add_argument("--center-scroll", type=int, default=0, help="upward swipes in the Plugin Center list before the screenshot")
    parser.add_argument("--drawer", action="store_true", help="launcher app list screenshots (emulator)")
    parser.add_argument("--aliases", action="store_true", help="cycle the four launcher aliases in the dark theme (adb root)")
    arguments = parser.parse_args(argv)
    device = Device(arguments.serial)
    failures = 0

    def report(ok: bool, label: str, value: object) -> None:
        nonlocal failures
        failures += 0 if ok else 1
        print(f"{'PASS' if ok else 'FAIL'} {label} {value}")

    if not device.shell(f"pm list packages {PLUGIN_PACKAGE}", check=False).strip():
        report(False, "plugin", "not installed")
        print("RESULT FAIL")
        return 1
    if arguments.center or arguments.aliases:
        device.adb("root", check=False)
        device.adb("wait-for-device", timeout=120)
    device.shell("settings put global hide_error_dialogs 1", check=False)
    device.shell("input keyevent KEYCODE_WAKEUP", check=False)
    serial = arguments.serial.replace(":", "_")
    original_night = device.night()
    component = device.launcher_component()
    report(component.endswith("/.launcher.AdaptiveAutoIconAlias"), "launcher-default", component.split("/", 1)[-1])
    try:
        for theme, night in (("light", False), ("dark", True)):
            if arguments.center:
                set_night_with_host(device, night)
            else:
                device.set_night(night)
            report(device.night() == night, f"night-mode-{theme}", "yes" if night else "no")
            if arguments.center:
                opened = open_plugin_center(device, arguments.center_scroll)
                report(opened, f"plugin-center-{theme}", "resumed" if opened else "not resumed")
                target = arguments.out / f"plugin-center-{serial}-{theme}.png"
                report(device.screenshot(target), f"screenshot-center-{theme}", target.name)
                device.shell(f"am force-stop {HOST_PACKAGE}", check=False)
            if arguments.drawer:
                open_drawer(device)
                target = arguments.out / f"launcher-{serial}-{theme}.png"
                report(device.screenshot(target), f"screenshot-launcher-{theme}", target.name)
                device.shell("input keyevent KEYCODE_HOME", check=False)
        if arguments.aliases:
            for mode, alias in ALIASES:
                for _, other in ALIASES:
                    state = "enable" if other == alias else "disable"
                    device.shell(f"pm {state} {PLUGIN_PACKAGE}/{PLUGIN_PACKAGE}.launcher.{other}", check=False)
                time.sleep(3)
                component = device.launcher_component()
                report(component.endswith(f"/.launcher.{alias}"), f"alias-{mode}", component.split("/", 1)[-1])
                open_drawer(device)
                target = arguments.out / f"launcher-{serial}-alias-{mode}.png"
                report(device.screenshot(target), f"screenshot-alias-{mode}", target.name)
                device.shell("input keyevent KEYCODE_HOME", check=False)
            for _, alias in ALIASES:
                device.shell(f"pm default-state {PLUGIN_PACKAGE}/{PLUGIN_PACKAGE}.launcher.{alias}", check=False)
            time.sleep(2)
            component = device.launcher_component()
            report(component.endswith("/.launcher.AdaptiveAutoIconAlias"), "alias-restored", component.split("/", 1)[-1])
    finally:
        if arguments.center:
            set_night_with_host(device, original_night)
            device.shell(f"am force-stop {HOST_PACKAGE}", check=False)
        else:
            device.set_night(original_night)
        device.shell("input keyevent KEYCODE_HOME", check=False)

    print(f"RESULT {'PASS' if failures == 0 else 'FAIL'}")
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
