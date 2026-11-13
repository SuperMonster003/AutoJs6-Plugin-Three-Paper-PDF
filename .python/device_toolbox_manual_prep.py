"""Prepares the manual toolbox test of roadmap P4.1 / P4.2 on one device, reports what it left, and cleans it up.

`prepare` installs the debug plugin when the device has none or an older one (`assembleDebug` first), writes the
inputs into the shared folder `/sdcard/Download/3-paper-p4/` (the samples outline.pdf, text.pdf, images.pdf and
encrypted.pdf with user password `paper-user` / owner password `paper-owner`, the 100 page `manual-100.pdf` of the
viewer test for a task long enough to watch its notification, and two photos `photo-landscape.jpg` /
`photo-portrait.jpg`), remembers what the default output folder `/sdcard/Documents/3-Paper PDF/` already holds, and
opens the home screen through the launcher entry that is enabled. `status` prints the all files access, the
notification permission and the results written since `prepare`. `cleanup` deletes the input folder and only those
results. Nothing is tapped and no permission is granted; the person testing does both. The plugin stays installed.

Output: one line per step with PASS / FAIL / INFO and the key values, then `RESULT PASS` or `RESULT FAIL` (exit code 1).
zh-CN: 在一台设备上准备路线图 P4.1 / P4.2 的工具箱手测, 报告其产物, 以及测后清理. `prepare` 在设备没有插件或插件较旧时安装 debug
插件 (先 `assembleDebug`), 把输入写到共享目录 `/sdcard/Download/3-paper-p4/` (样例 outline.pdf, text.pdf, images.pdf 与
encrypted.pdf (用户密码 `paper-user`, 所有者密码 `paper-owner`), 查看器手测用的 100 页 `manual-100.pdf` (任务足够长, 便于观察通知),
以及两张照片 `photo-landscape.jpg` / `photo-portrait.jpg`), 记下默认输出目录 `/sdcard/Documents/3-Paper PDF/` 原有的内容, 并经当前启用
的启动器入口打开首页. `status` 输出所有文件访问权限, 通知权限, 以及 `prepare` 之后写入的结果. `cleanup` 删除输入目录, 并且只删除这些
结果. 不点按屏幕, 也不授予权限, 两者都由测试者操作. 插件保留安装.

Usage: py .python/device_toolbox_manual_prep.py --serial SERIAL [--skip-build] prepare | status | cleanup
"""

from __future__ import annotations

import argparse
import hashlib
import shlex
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import device_viewer_manual_prep as viewer  # noqa: E402
import generate_fixtures as fx  # noqa: E402

PLUGIN_PACKAGE = viewer.PLUGIN_PACKAGE
INPUTS = "/sdcard/Download/3-paper-p4"
OUTPUTS = "/sdcard/Documents/3-Paper PDF"
BEFORE = INPUTS + "/.outputs-before.txt"
COPIED_SAMPLES = ("outline.pdf", "text.pdf", "images.pdf", "encrypted.pdf")
PHOTOS = {"photo-landscape.jpg": (320, 240), "photo-portrait.jpg": (240, 320)}


def photo(width: int, height: int) -> bytes:
    """A gradient photo, the same bytes on every run. zh-CN: 渐变照片, 每次运行字节相同."""
    pixels = [(x * 255 // width, y * 255 // height, 160) for y in range(height) for x in range(width)]
    return fx.jpeg(width, height, pixels)


def inputs() -> dict[str, bytes]:
    files = {name: (viewer.SAMPLES / name).read_bytes() for name in COPIED_SAMPLES}
    files[viewer.MANUAL] = viewer.manual_document()
    files.update({name: photo(*size) for name, size in PHOTOS.items()})
    return files


def listing(device: viewer.Device, folder: str) -> list[str]:
    output = device.shell(f"ls -1 {shlex.quote(folder)} 2>/dev/null", check=False)
    return sorted(line.strip() for line in output.splitlines() if line.strip())


def granted(device: viewer.Device) -> tuple[str, str]:
    """The all files access and the notification permission as the system reports them. zh-CN: 系统报告的所有文件访问与通知权限."""
    api = int(device.shell("getprop ro.build.version.sdk").strip() or 0)
    if api >= 30:
        storage = device.shell(f"appops get {PLUGIN_PACKAGE} MANAGE_EXTERNAL_STORAGE", check=False).strip() or "unknown"
    else:
        dump = device.shell(f"dumpsys package {PLUGIN_PACKAGE}", check=False)
        storage = "granted" if "android.permission.WRITE_EXTERNAL_STORAGE: granted=true" in dump else "not granted"
    if api >= 33:
        dump = device.shell(f"dumpsys package {PLUGIN_PACKAGE}", check=False)
        notifications = "granted" if "android.permission.POST_NOTIFICATIONS: granted=true" in dump else "not granted"
    else:
        notifications = "not needed"
    return storage, notifications


def open_home(device: viewer.Device) -> bool:
    output = device.shell(f"monkey -p {PLUGIN_PACKAGE} -c android.intent.category.LAUNCHER 1", check=False)
    started = "Events injected: 1" in output
    print(f"{'PASS' if started else 'FAIL'} open home")
    return started


def prepare(device: viewer.Device, build_first: bool) -> bool:
    api = device.shell("getprop ro.build.version.sdk").strip()
    model = device.shell("getprop ro.product.model").strip()
    print(f"INFO device={model} api={api}")
    before = device.installed_version()
    wanted = viewer.apk_version()
    if before is None or before < wanted:
        if build_first:
            viewer.build()
        output = device.adb("install", "-r", "-t", "-d", str(viewer.PLUGIN_APK), check=False, timeout=900)
        if "Success" not in output:
            print(f"FAIL install {output.strip()[:160]}")
            return False
    print(f"PASS plugin versionCode={device.installed_version()} (before={before})")
    files = inputs()
    existing = listing(device, OUTPUTS)
    device.shell(f"rm -rf {INPUTS} && mkdir -p {INPUTS}")
    with tempfile.TemporaryDirectory(prefix="3-paper-p4-") as temporary:
        for name, data in files.items():
            local = Path(temporary) / name
            local.write_bytes(data)
            device.adb("push", str(local), f"{INPUTS}/{name}")
        record = Path(temporary) / "before.txt"
        record.write_text("".join(f"{name}\n" for name in existing), encoding="utf-8", newline="\n")
        device.adb("push", str(record), BEFORE)
    ok = True
    for name, data in files.items():
        listed = device.shell(f"md5sum {INPUTS}/{name}", check=False).split()
        same = bool(listed) and listed[0] == hashlib.md5(data).hexdigest()
        ok &= same
        print(f"{'PASS' if same else 'FAIL'} input {name} bytes={len(data)}")
    print(f"INFO output folder already holds {len(existing)} entries (kept by cleanup)")
    storage, notifications = granted(device)
    print(f"INFO all files access={storage} notifications={notifications}")
    return ok and open_home(device)


def written(device: viewer.Device) -> list[str]:
    kept = set(line.strip() for line in device.shell(f"cat {BEFORE} 2>/dev/null", check=False).splitlines() if line.strip())
    return [name for name in listing(device, OUTPUTS) if name not in kept]


def status(device: viewer.Device) -> bool:
    storage, notifications = granted(device)
    print(f"INFO all files access={storage} notifications={notifications}")
    results = written(device)
    print(f"INFO results={len(results)}")
    for name in results:
        path = shlex.quote(f"{OUTPUTS}/{name}")
        count = device.shell(f"if [ -d {path} ]; then ls -1 {path} | wc -l; else echo 1; fi", check=False).strip()
        print(f"INFO result {name} files={count}")
    return True


def cleanup(device: viewer.Device) -> bool:
    if not listing(device, INPUTS):
        print(f"PASS cleanup nothing to do ({INPUTS} is gone)")
        return True
    results = written(device)
    for name in results:
        device.shell(f"rm -rf {shlex.quote(f'{OUTPUTS}/{name}')}", check=False)
    left = written(device)
    device.shell(f"rm -rf {INPUTS}", check=False)
    gone = not listing(device, INPUTS)
    print(f"{'PASS' if not left else 'FAIL'} cleanup results removed={len(results) - len(left)} left={len(left)}")
    print(f"{'PASS' if gone else 'FAIL'} cleanup {INPUTS}")
    return not left and gone


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepares the manual toolbox test (P4) of 3-Paper PDF on one device.")
    parser.add_argument("--serial", required=True)
    parser.add_argument("command", choices=("prepare", "status", "cleanup"))
    parser.add_argument("--skip-build", action="store_true", help="install the APK as built, without assembleDebug")
    args = parser.parse_args()
    device = viewer.Device(args.serial)
    try:
        if args.command == "prepare":
            ok = prepare(device, build_first=not args.skip_build)
        elif args.command == "status":
            ok = status(device)
        else:
            ok = cleanup(device)
    except (RuntimeError, subprocess.SubprocessError) as error:
        print(f"FAIL {error}")
        ok = False
    print("RESULT PASS" if ok else "RESULT FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
