"""Prepares the manual viewer test of roadmap P3.1 (E4: zoom and search by hand) on one device, and cleans it up.

`prepare` installs the debug plugin when the device has none or an older one (`assembleDebug` first), generates a
100 page manual document (`manual-100.pdf`: a heading, body text, 6 pt fine print and a 0.25 pt grid on every page,
the word "needle" on pages 7, 33, 64 and 98), copies it with the samples outline.pdf, images.pdf and encrypted.pdf
(user password `paper-user`) into the private `files/e4/` folder of the plugin through `run-as`, and opens the first
file in the viewer. `open NAME` opens another of these files, `cleanup` deletes the folder. Nothing is tapped; the
person testing does the gestures. The plugin stays installed.

Output: one line per step with PASS / FAIL and the key values, then `RESULT PASS` or `RESULT FAIL` (exit code 1).
zh-CN: 在一台设备上准备路线图 P3.1 的查看器手测 (E4: 手动缩放与搜索), 以及测后清理. `prepare` 在设备没有插件或插件较旧时安装
debug 插件 (先 `assembleDebug`), 生成 100 页手测文档 (`manual-100.pdf`: 每页有标题, 正文, 6 pt 小字与 0.25 pt 网格, 第 7, 33,
64, 98 页有 "needle"), 经 `run-as` 把它与样例 outline.pdf, images.pdf, encrypted.pdf (用户密码 `paper-user`) 复制到插件私有目录
`files/e4/`, 并在查看器中打开第一个文件. `open NAME` 打开其中另一个文件, `cleanup` 删除该目录. 不点按屏幕, 手势由测试者操作.
插件保留安装.

Usage: py .python/device_viewer_manual_prep.py --serial SERIAL [--skip-build] prepare | open NAME | cleanup
"""

from __future__ import annotations

import argparse
import hashlib
import re
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_fixtures as fx  # noqa: E402

PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.three.paper.pdf"
VIEWER = PLUGIN_PACKAGE + "/.viewer.ThreePaperPdfViewerActivity"
ROOT = Path(__file__).resolve().parent.parent
PLUGIN_APK = ROOT / "app/build/outputs/apk/debug/autojs6-plugin-three-paper-pdf-v1.0.0.apk"
SAMPLES = ROOT / "samples/fixtures"
FOLDER = "files/e4"
STAGING = "/data/local/tmp/3-paper-e4"
MANUAL = "manual-100.pdf"
MANUAL_PAGES = 100
NEEDLE_PAGES = (7, 33, 64, 98)
COPIED_SAMPLES = ("outline.pdf", "images.pdf", "encrypted.pdf")


def manual_document() -> bytes:
    """The 100 page manual document, the same bytes on every run. zh-CN: 100 页手测文档, 每次运行字节相同."""
    document = fx.Document(MANUAL)
    info = fx._information(document, "3-Paper PDF manual viewer test")
    fonts = fx._fonts(document)
    pages = fx.Pages(document)
    for number in range(1, MANUAL_PAGES + 1):
        lines = [
            f"Manual page {number} of {MANUAL_PAGES}",
            "Pinch to zoom in and out; double tap to switch between fit width and 2.5 times.",
            "Text and lines must stay sharp after zooming, without blurred tiles.",
            "The quick brown fox jumps over the lazy dog. 0123456789",
        ]
        if number in NEEDLE_PAGES:
            lines.append("The needle is on this page.")
        fine = [f"Fine print 6 pt, line {line}: the grid below has lines every 10 pt, 0.25 pt wide." for line in range(1, 6)]
        content = fx._text(lines, 780) + b"BT /F1 6 Tf 72 560 Td " + fx.NEWLINE.join(
            (b"" if index == 0 else b"0 -8 Td ") + fx.literal(line) + b" Tj" for index, line in enumerate(fine)
        ) + b" ET" + fx.NEWLINE + grid(72, 300, 451, 200)
        pages.add(content, {"Font": fonts}, compress=True)
    return document.build(fx._catalog(document, pages), info)


def grid(left: int, bottom: int, width: int, height: int) -> bytes:
    """A 0.25 pt grid with a line every 10 pt. zh-CN: 每 10 pt 一条线的 0.25 pt 网格."""
    commands = ["q 0.25 w 0 0 0 RG"]
    commands += [f"{x} {bottom} m {x} {bottom + height} l" for x in range(left, left + width + 1, 10)]
    commands += [f"{left} {y} m {left + width} {y} l" for y in range(bottom, bottom + height + 1, 10)]
    commands.append("S Q")
    return " ".join(commands).encode("ascii") + fx.NEWLINE


class Device:
    def __init__(self, serial: str) -> None:
        self.serial = serial

    def adb(self, *arguments: str, check: bool = True, timeout: int = 180) -> str:
        result = subprocess.run(["adb", "-s", self.serial, *arguments], capture_output=True, text=True,
                                encoding="utf-8", errors="replace", timeout=timeout)
        if check and result.returncode != 0:
            raise RuntimeError(f"adb {arguments[0]} failed: {(result.stdout + result.stderr).strip()[:200]}")
        return result.stdout

    def shell(self, command: str, check: bool = True) -> str:
        return self.adb("shell", command, check=check)

    def run_as(self, command: str, check: bool = True) -> str:
        return self.shell(f"run-as {PLUGIN_PACKAGE} sh -c '{command}'", check=check)

    def installed_version(self) -> int | None:
        found = re.search(r"versionCode=(\d+)", self.shell(f"dumpsys package {PLUGIN_PACKAGE}", check=False))
        return int(found.group(1)) if found else None


def apk_version() -> int:
    properties = (ROOT / "version.properties").read_text(encoding="utf-8")
    return int(re.search(r"^VERSION_BUILD=(\d+)$", properties, re.MULTILINE).group(1))


def build() -> None:
    gradle = ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew")
    subprocess.run([str(gradle), "-Pkotlin.compiler.execution.strategy=in-process", ":app:assembleDebug", "-q"],
                   cwd=ROOT, check=True, timeout=1800)


def open_file(device: Device, name: str) -> bool:
    home = device.run_as("pwd").strip()
    output = device.shell(f"am start -W -a android.intent.action.VIEW -t application/pdf "
                          f"-d file://{home}/{FOLDER}/{name} -n {VIEWER}", check=False)
    started = "Status: ok" in output or "Complete" in output
    print(f"{'PASS' if started else 'FAIL'} open {name}")
    return started


def prepare(device: Device, build_first: bool) -> bool:
    api = device.shell("getprop ro.build.version.sdk").strip()
    model = device.shell("getprop ro.product.model").strip()
    print(f"INFO device={model} api={api}")
    before = device.installed_version()
    wanted = apk_version()
    if before is None or before < wanted:
        if build_first:
            build()
        output = device.adb("install", "-r", "-t", "-d", str(PLUGIN_APK), check=False, timeout=900)
        if "Success" not in output:
            print(f"FAIL install {output.strip()[:160]}")
            return False
    after = device.installed_version()
    print(f"PASS plugin versionCode={after} (before={before})")
    files = {MANUAL: manual_document(), **{name: (SAMPLES / name).read_bytes() for name in COPIED_SAMPLES}}
    with tempfile.TemporaryDirectory(prefix="3-paper-e4-") as temporary:
        device.shell(f"rm -rf {STAGING} && mkdir -p {STAGING}")
        for name, data in files.items():
            local = Path(temporary) / name
            local.write_bytes(data)
            device.adb("push", str(local), f"{STAGING}/{name}")
    device.run_as(f"mkdir -p {FOLDER} && cp {STAGING}/* {FOLDER}/", check=False)
    device.shell(f"rm -rf {STAGING}", check=False)
    ok = True
    for name, data in files.items():
        listed = device.run_as(f"md5sum {FOLDER}/{name}", check=False).split()
        same = bool(listed) and listed[0] == hashlib.md5(data).hexdigest()
        ok &= same
        print(f"{'PASS' if same else 'FAIL'} copy {name} bytes={len(data)}")
    return ok and open_file(device, MANUAL)


def cleanup(device: Device) -> bool:
    device.run_as(f"rm -rf {FOLDER}", check=False)
    device.shell(f"rm -rf {STAGING}", check=False)
    left = device.run_as(f"ls {FOLDER} 2>/dev/null", check=False).strip()
    print(f"{'PASS' if not left else 'FAIL'} cleanup {FOLDER}")
    return not left


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepares the manual viewer test (E4) of 3-Paper PDF on one device.")
    parser.add_argument("--serial", required=True)
    parser.add_argument("command", choices=("prepare", "open", "cleanup"))
    parser.add_argument("name", nargs="?", default=MANUAL)
    parser.add_argument("--skip-build", action="store_true", help="install the APK as built, without assembleDebug")
    args = parser.parse_args()
    device = Device(args.serial)
    try:
        if args.command == "prepare":
            ok = prepare(device, build_first=not args.skip_build)
        elif args.command == "open":
            ok = open_file(device, args.name)
        else:
            ok = cleanup(device)
    except (RuntimeError, subprocess.SubprocessError) as error:
        print(f"FAIL {error}")
        ok = False
    print("RESULT PASS" if ok else "RESULT FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
