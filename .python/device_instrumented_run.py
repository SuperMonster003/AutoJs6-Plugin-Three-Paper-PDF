# -*- coding: utf-8 -*-
"""Runs instrumented test classes of 3-Paper PDF on one device and prints only the verdicts and key numbers.

The script installs the debug plugin and its test package (`assembleDebug` + `assembleDebugAndroidTest` first), runs
`am instrument -r` for the given classes or methods, prints one `PASS` / `FAIL` / `SKIP` line per test, then the logcat
lines of the timing and probe tags (`PaperRenderPerf`, `PaperMergePerf`, `PaperImagesPerf`, `PaperHtmlSpike`,
`PaperCryptoSpike`, `PaperViewerPerf`, `PaperStampSpike`, `PaperObjectStreams`, `PaperFontSubset`, `PaperCompress`, `PaperMetadata`,
`PaperTextLayer`, `PaperForms`). At the end the test
package is uninstalled; the plugin is uninstalled too unless it was installed before, in which case the original APK
(pulled first) is installed back with `install -r -d`.
With `--installed` the plugin already on the device is used as it is (it must be a debuggable build): only the test
package is installed and uninstalled, so a device that asks to confirm every installation (MIUI / HyperOS, ColorOS)
asks once per run. The script never taps the screen; an installation that waits for a confirmation on the device
times out and is reported. The UI tests find controls by their English text, so the plugin's own language is English
during the run (debuggable builds, through `run-as`) and put back afterwards; `--keep-language` leaves it alone.

Usage:
  py .python/device_instrumented_run.py --serial BH900ASK9E --tests PdfServiceGenerationTest[,PdfServiceCryptoTest#name]
  py .python/device_instrumented_run.py --serial emulator-5850 --tests all
  py .python/device_instrumented_run.py --serial 968e9f18 --installed --tests PdfServiceRenderingTest#timingOfAnA4TextPageAt144Dpi

A device behind a remote adb server is reached by setting `ADB_SERVER_SOCKET=tcp:<host>:<port>` before the call.
With `--shots DIR` the tests that call `DeviceShots.take` save screenshots (instrumentation argument `paperShots=1`);
they are copied to DIR through `run-as` and removed from the device before the plugin is put back.

Output: the device line, one line per test, the tagged logcat lines, then `RESULT PASS` or `RESULT FAIL` (exit code 1).
zh-CN: 在一台设备上运行 3-Paper PDF 的仪器测试类, 只输出结论与关键数值; 结束时卸载测试包, 插件此前未安装时一并卸载, 否则装回事先
拉取的原 APK. 给出 `--installed` 时直接使用设备上已装的插件 (须为可调试版本), 只安装与卸载测试包, 每次安装都要确认的设备 (MIUI / HyperOS,
ColorOS) 每次运行只需确认一次. 不点按屏幕; 需要在设备上确认的安装会超时并报告. 界面测试按英文文字找控件, 所以运行期间插件自己的语言设为英文 (可调试包, 经 `run-as`), 结束后装回; 给出 `--keep-language` 时不改. 给出 `--shots DIR` 时, 调用 `DeviceShots.take` 的测试会保存截图
(仪器参数 `paperShots=1`), 截图经 `run-as` 复制到 DIR, 在装回插件之前从设备上删除.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import tempfile
from pathlib import Path

PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.three.paper.pdf"
TEST_PACKAGE = PLUGIN_PACKAGE + ".test"
TEST_PREFIX = PLUGIN_PACKAGE + "."
RUNNER = TEST_PACKAGE + "/androidx.test.runner.AndroidJUnitRunner"
TAGS = ("PaperRenderPerf", "PaperMergePerf", "PaperImagesPerf", "PaperHtmlSpike", "PaperCryptoSpike", "PaperViewerPerf", "PaperStampSpike",
        "PaperObjectStreams", "PaperFontSubset", "PaperCompress", "PaperMetadata", "PaperTextLayer", "PaperForms")
ROOT = Path(__file__).resolve().parent.parent
# The debug APK is named after VERSION_NAME. zh-CN: debug APK 以 VERSION_NAME 命名.
VERSION_NAME = next(line.split("=", 1)[1].strip() for line in (ROOT / "version.properties").read_text(encoding="utf-8").splitlines()
                    if line.startswith("VERSION_NAME="))
PLUGIN_APK = ROOT / f"app/build/outputs/apk/debug/autojs6-plugin-three-paper-pdf-v{VERSION_NAME}.apk"
TEST_APK = ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

# `am instrument -r` status codes. zh-CN: `am instrument -r` 的状态码.
VERDICTS = {0: "PASS", -1: "FAIL", -2: "FAIL", -3: "SKIP", -4: "SKIP"}


class Device:

    def __init__(self, serial: str) -> None:
        self.serial = serial

    def adb(self, *arguments: str, check: bool = True, timeout: int = 180) -> str:
        result = subprocess.run(["adb", "-s", self.serial, *arguments], capture_output=True, text=True,
                                encoding="utf-8", errors="replace", timeout=timeout)
        if check and result.returncode != 0:
            raise RuntimeError(f"adb {arguments[0]} failed: {(result.stderr or result.stdout).strip()[:200]}")
        return result.stdout

    def shell(self, command: str, check: bool = True, timeout: int = 180) -> str:
        return self.adb("shell", command, check=check, timeout=timeout)

    def prop(self, name: str) -> str:
        return self.shell(f"getprop {name}").strip()

    def pull(self, remote: str, local: Path) -> None:
        """`adb pull`, or `exec-out cat` where the old adbd of API 24 cannot stat an APK. zh-CN: API 24 的旧 adbd 无法 stat APK 时改用 `exec-out cat`."""
        if subprocess.run(["adb", "-s", self.serial, "pull", remote, str(local)], capture_output=True, timeout=900).returncode == 0:
            return
        result = subprocess.run(["adb", "-s", self.serial, "exec-out", "cat", remote], capture_output=True, timeout=900)
        if result.returncode != 0 or not result.stdout:
            raise RuntimeError(f"cannot copy {remote} from the device")
        local.write_bytes(result.stdout)

    def install(self, apk: Path, *flags: str) -> None:
        # Slow links (a remote adb server) need minutes for the 21 MB debug APK. zh-CN: 慢链路 (远程 adb 服务器) 传 21 MB 的 debug 包要几分钟.
        output = self.adb("install", *flags, str(apk), check=False, timeout=900)
        if "Success" not in output:
            raise RuntimeError(f"installing {apk.name} failed: {output.strip()[:200]}")


def qualified(test: str) -> str:
    return test if test.startswith(TEST_PREFIX) else TEST_PREFIX + test


def instrument(device: Device, tests: list[str], timeout: int, shots: bool = False) -> list[tuple[str, str, str]]:
    selection = "" if tests == ["all"] else f"-e class {','.join(qualified(t) for t in tests)} "
    if shots:
        selection += "-e paperShots 1 "
    output = device.shell(f"am instrument -r -w {selection}{RUNNER}", check=False, timeout=timeout)
    results: list[tuple[str, str, str]] = []
    status: dict[str, str] = {}
    key = ""
    for line in output.splitlines():
        line = line.rstrip("\r")
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, _, value = line[len("INSTRUMENTATION_STATUS: "):].partition("=")
            status[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            code = int(line.split(":", 1)[1])
            if code != 1 and "test" in status:
                name = status.get("class", "").rsplit(".", 1)[-1] + "#" + status["test"]
                stack = (status.get("stack") or "").strip().splitlines()
                # The frames in test files tell which step failed (helper < test). zh-CN: 测试文件里的各帧说明是哪一步失败 (辅助方法 < 测试).
                frames = [f.strip()[f.strip().rfind("(") + 1:].rstrip(")") for f in stack if "DeviceTest.kt:" in f]
                where = f" [{' < '.join(frames[:4])}]" if frames else ""
                results.append((VERDICTS.get(code, "FAIL"), name, (stack[0][:200] if stack else "") + where))
            status = {}
        elif key == "stack" and "stack" in status and not line.startswith("INSTRUMENTATION_"):
            status["stack"] += "\n" + line
        elif line.startswith("INSTRUMENTATION_RESULT: shortMsg=") or line.startswith("INSTRUMENTATION_FAILED"):
            results.append(("FAIL", "instrumentation", line[:200]))
    if not results:
        results.append(("FAIL", "instrumentation", "no test ran: " + output.strip()[-200:]))
    return results


def copy_shots(device: Device, target: Path) -> int:
    """Copies the screenshots of `files/shots/` to [target] and removes them. zh-CN: 把 `files/shots/` 中的截图复制到 [target] 并删除."""
    listing = device.shell(f"run-as {PLUGIN_PACKAGE} ls files/shots", check=False)
    names = [n.strip() for n in listing.splitlines() if n.strip().endswith(".png")]
    target.mkdir(parents=True, exist_ok=True)
    for name in names:
        data = subprocess.run(["adb", "-s", device.serial, "exec-out", "run-as", PLUGIN_PACKAGE, "cat", f"files/shots/{name}"],
                              capture_output=True, timeout=120).stdout
        (target / name).write_bytes(data)
    device.shell(f"run-as {PLUGIN_PACKAGE} rm -rf files/shots", check=False)
    return len(names)


APPEARANCE = "shared_prefs/app-appearance.xml"


def force_english(device: Device) -> bytes | None:
    """Sets the plugin's own language to English for the run and returns the file to put back (empty bytes: there was
    none), or None when the plugin is not debuggable. The UI tests find controls by their English text; on a device whose
    plugin follows a host in another language (the E4 phones follow a Chinese AutoJs6) 17 of them failed only for that
    (2026-11-21, XQ-DQ72). The process is stopped first so that it cannot write the old file back.
    zh-CN: 运行期间把插件自己的语言设为英文, 返回结束时要装回的文件 (空字节: 原本没有), 插件不可调试时返回 None. 界面测试按英文文字找控件;
    插件跟随其他语言宿主的设备上 (E4 手机跟随中文的 AutoJs6) 有 17 项仅因此失败 (2026-11-21, XQ-DQ72). 先停止进程, 以免它把旧文件写回."""
    probe = subprocess.run(["adb", "-s", device.serial, "exec-out", "run-as", PLUGIN_PACKAGE, "ls", "-d", "."],
                           capture_output=True, timeout=60)
    if probe.returncode != 0 or b"not debuggable" in probe.stdout + probe.stderr:
        return None
    device.shell(f"am force-stop {PLUGIN_PACKAGE}", check=False)
    read = subprocess.run(["adb", "-s", device.serial, "exec-out", "run-as", PLUGIN_PACKAGE, "cat", APPEARANCE],
                          capture_output=True, timeout=60)
    original = read.stdout if read.returncode == 0 and read.stdout.lstrip().startswith(b"<?xml") else b""
    entries = re.findall(rb'^\s*(<(?:string|int) name="(?!language")[^"]+"[^\n]*)$', original, re.MULTILINE)
    english = b"<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n" + \
        b"".join(b"    " + e.strip() + b"\n" for e in entries) + b'    <string name="language">en</string>\n</map>\n'
    write_appearance(device, english)
    return original


def write_appearance(device: Device, content: bytes) -> None:
    """Writes (or with empty bytes removes) the appearance file of the stopped plugin. zh-CN: 写入 (空字节时删除) 已停止插件的外观文件."""
    device.shell(f"am force-stop {PLUGIN_PACKAGE}", check=False)
    if not content:
        device.shell(f"run-as {PLUGIN_PACKAGE} rm -f {APPEARANCE}", check=False)
        return
    result = subprocess.run(["adb", "-s", device.serial, "shell", f"run-as {PLUGIN_PACKAGE} sh -c 'mkdir -p shared_prefs && cat > {APPEARANCE}'"],
                            input=content, capture_output=True, timeout=60)
    if result.returncode != 0:
        raise RuntimeError(f"writing {APPEARANCE} failed: {(result.stderr or result.stdout).decode(errors='replace').strip()[:200]}")


def main() -> int:
    # Logcat lines may hold characters the console code page lacks (an emoji under GBK); escape them instead of failing.
    # zh-CN: logcat 行可能含控制台代码页没有的字符 (GBK 下的表情), 转义输出而不是报错.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(errors="backslashreplace")
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--tests", required=True, help="comma-separated classes or Class#method (package optional), or all")
    parser.add_argument("--timeout", type=int, default=1800, help="seconds for the whole instrumentation")
    parser.add_argument("--installed", action="store_true",
                        help="use the plugin already installed (debuggable); install only the test package")
    parser.add_argument("--shots", type=Path, help="save the screenshots of DeviceShots.take into this folder")
    parser.add_argument("--keep-language", action="store_true",
                        help="keep the plugin's language (by default it is English during the run and put back afterwards)")
    arguments = parser.parse_args()
    device = Device(arguments.serial)
    for apk in ((TEST_APK,) if arguments.installed else (PLUGIN_APK, TEST_APK)):
        if not apk.exists():
            print(f"FAIL build: {apk.relative_to(ROOT)} is missing (run assembleDebug assembleDebugAndroidTest)")
            return 1
    print(f"device {arguments.serial}: {device.prop('ro.product.model')} API {device.prop('ro.build.version.sdk')}")
    original = device.shell(f"pm path {PLUGIN_PACKAGE}", check=False).strip()
    if arguments.installed:
        installed = re.search(r"versionCode=(\d+)", device.shell(f"dumpsys package {PLUGIN_PACKAGE}", check=False))
        if not original.startswith("package:") or installed is None:
            print("FAIL setup: --installed needs 3-Paper PDF on the device")
            print("RESULT FAIL")
            return 1
        print(f"INFO using the installed plugin (versionCode {installed.group(1)})")
        original = ""
    backup_dir = tempfile.TemporaryDirectory(prefix="paper-device-run-")
    backup: Path | None = None
    if original.startswith("package:"):
        backup = Path(backup_dir.name) / "original.apk"
        try:
            device.pull(original.splitlines()[0].strip()[len("package:"):], backup)
        except (RuntimeError, subprocess.TimeoutExpired) as e:
            # Nothing is installed or removed without a copy to put back. zh-CN: 没有可装回的副本时不安装也不卸载.
            print(f"FAIL backup: {e}")
            print("RESULT FAIL")
            return 1
        print("INFO plugin was installed before; it is put back at the end")
    failed = False
    appearance: bytes | None = None
    try:
        if not arguments.installed:
            device.install(PLUGIN_APK, "-r", "-t", "-d")
        device.install(TEST_APK, "-r", "-t")
        if not arguments.keep_language:
            appearance = force_english(device)
            print("INFO plugin language set to English for the run" if appearance is not None
                  else "INFO plugin not debuggable; its language is kept")
        device.adb("logcat", "-c", check=False)
        tests = [t.strip() for t in arguments.tests.split(",") if t.strip()]
        for verdict, name, detail in instrument(device, tests, arguments.timeout, shots=arguments.shots is not None):
            failed |= verdict == "FAIL"
            print(f"{verdict} {name}" + (f": {detail}" if detail else ""))
        log = device.adb("logcat", "-d", "-v", "brief", "-s", *TAGS, check=False)
        for line in log.splitlines():
            if any(f"/{tag}" in line for tag in TAGS):
                print("  " + re.sub(r"^\w/(\w+)\(\s*\d+\):\s*", r"\1: ", line.strip()))
        if arguments.shots is not None:
            print(f"INFO screenshots: {copy_shots(device, arguments.shots)} copied to {arguments.shots}")
    except (RuntimeError, subprocess.TimeoutExpired) as e:
        failed = True
        print(f"FAIL setup: {e}")
    finally:
        device.adb("uninstall", TEST_PACKAGE, check=False)
        if appearance is not None and (arguments.installed or backup is not None):
            try:
                write_appearance(device, appearance)
                print("INFO plugin language put back")
            except (RuntimeError, subprocess.TimeoutExpired) as e:
                failed = True
                print(f"FAIL restore language: {e}")
        if arguments.installed:
            pass
        elif backup is not None:
            try:
                device.install(backup, "-r", "-d")
            except RuntimeError as e:
                failed = True
                print(f"FAIL restore: {e}")
        else:
            device.adb("uninstall", PLUGIN_PACKAGE, check=False)
        backup_dir.cleanup()
    print("RESULT FAIL" if failed else "RESULT PASS")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
