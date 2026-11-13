"""Checks that an app packaged by AutoJs6 can use the pdf module (3-Paper PDF roadmap P5.5, Q145 (b)).

Usage: py -3.12 -I .python/packaged_app_check.py -s SERIAL [--host-apk APK] [--host-test-apk APK] [--template-apk APK]
       [--plugin-apk APK] [--app-apk APK] [--no-unavailable] [--keep]

1. Needs on the device: a debuggable AutoJs6 host with the packaged entry of the pdf module (6.8.1, build 6641 or
   later), its instrumentation package (org.autojs.autojs6.test) with PackagedAppPdfDeviceTest, an APK Builder template
   plugin signed with the official certificate, and a debuggable 3-Paper PDF with the packaged entry (1.0.0, P5.5).
   The --*-apk options install those first (install -r -t); the tool answers no dialog.
2. Unless --app-apk is given, the host test PackagedAppPdfDeviceTest#producesPackagedApp packs a Rhino project
   (org.autojs.autojs6.paperpdf) with the template plugin (switched on in Plugin Center for the run if it is off) and
   the APK is pulled and installed. Its script calls pdf.fromText, open, render, merge, info, encrypt and view once each
   and writes the results to paper-packaged-result.json in its external files directory, then keeps one document open.
3. Checks: the packaging flow granted org.autojs.plugin.pdf.permission.RUNTIME; every call returned what it should
   (page counts, the size of the rendered page, PASSWORD_REQUIRED without the password, the viewer session state and
   its "finished" close reason); the plugin keeps the document in cache/pdf/k<uid>-<pid>/ and releases it after the
   app is killed (am force-stop), within 90 seconds.
4. Unless --no-unavailable is given, the plugin is uninstalled with its data kept (pm uninstall -k), the app runs again
   and every call must report PLUGIN_UNAVAILABLE; the plugin is then installed back (--plugin-apk, or the APK pulled
   from the device before the uninstall).
5. Afterwards the packaged app and the host's copies of its APK are removed and Plugin Center is put back, unless
   --keep is given. The host, its test package and the template stay installed.
Every check prints one line "PASS|FAIL|SKIP <name> <key values>"; the exit code is 1 when a check failed.

zh-CN: 检查由 AutoJs6 打包的应用能否使用 pdf 模块 (3-Paper PDF 路线图 P5.5, Q145 (b)). 1. 设备上需要: 可调试的 AutoJs6 宿主, 带 pdf 模块的
打包入口 (6.8.1, 构建号 6641 或以上), 其仪器测试包 (org.autojs.autojs6.test, 含 PackagedAppPdfDeviceTest), 官方证书签名的 APK Builder 模板插件,
以及带打包入口的可调试 3-Paper PDF (1.0.0, P5.5). 给出 --*-apk 选项时先安装它们 (install -r -t); 本工具不应答任何对话框.
2. 未给 --app-apk 时, 由宿主测试 PackagedAppPdfDeviceTest#producesPackagedApp 用模板插件打包一个 Rhino 项目 (org.autojs.autojs6.paperpdf;
模板插件在插件中心关闭时本次运行临时打开), 拉取并安装 APK. 应用脚本对 pdf.fromText, open, render, merge, info, encrypt 与 view 各调用一次,
把结果写到自身外部文件目录的 paper-packaged-result.json, 然后保持一个文档打开. 3. 检查: 打包流程授予了 org.autojs.plugin.pdf.permission.RUNTIME;
每项调用的返回符合预期 (页数, 渲染页的尺寸, 不给密码时报 PASSWORD_REQUIRED, 查看器会话状态与 "finished" 关闭原因); 插件把文档放在
cache/pdf/k<uid>-<pid>/, 应用被杀 (am force-stop) 后 90 秒内释放. 4. 未给 --no-unavailable 时, 保留数据卸载插件 (pm uninstall -k), 再运行一次应用,
每项调用都应报 PLUGIN_UNAVAILABLE; 之后装回插件 (--plugin-apk, 或卸载前从设备拉取的 APK). 5. 结束时删除打包应用与宿主里的 APK 副本,
恢复插件中心状态, 给出 --keep 时保留. 宿主, 其测试包与模板插件保持安装.
每项检查输出一行 "PASS|FAIL|SKIP <名称> <关键数值>"; 有检查失败时退出码为 1.
"""
import argparse
import json
import re
import subprocess
import sys
import tempfile
import time
from pathlib import Path

HOST = "org.autojs.autojs6"
TEST = f"{HOST}.test"
RUNNER = f"{TEST}/androidx.test.runner.AndroidJUnitRunner"
TEMPLATE = "org.autojs.plugin.apkbuilder.template"
PLUGIN = "io.github.supermonster003.autojs6.plugin.three.paper.pdf"
APP = "org.autojs.autojs6.paperpdf"
PRODUCER = "org.autojs.autojs.apkbuilder.template.PackagedAppPdfDeviceTest#producesPackagedApp"
PRODUCE_ARG = "autojs.paper.packaged.produce"
HOST_FILES = f"/sdcard/Android/data/{HOST}/files"
PRODUCED = (f"{HOST_FILES}/paper-packaged.apk", f"{HOST_FILES}/paper-packaged.json")
RESULT = f"/sdcard/Android/data/{APP}/files/paper-packaged-result.json"
PERMISSION = "org.autojs.plugin.pdf.permission.RUNTIME"
MIN_HOST_BUILD = 6641
ENABLE_STATE = "shared_prefs/plugin_center_enable_state.xml"
TEMPLATE_OFF = f'<boolean name="key_$_enabled_plugin_$_{TEMPLATE}" value="false" />'
TEMPLATE_ON = f'<boolean name="key_$_enabled_plugin_$_{TEMPLATE}" value="true" />'
CALLS = ("fromText", "open", "render", "merge", "info", "encrypt", "view", "hold")
RESULT_TIMEOUT = 90
RELEASE_TIMEOUT = 90


def adb(serial, *args, check=True, timeout=600):
    result = subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8",
                            errors="replace", timeout=timeout)
    if check and result.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args[:3])} failed: {(result.stderr or result.stdout).strip()[:300]}")
    return result.stdout.strip()


def shell(serial, command, check=False, timeout=120):
    return adb(serial, "shell", command, check=check, timeout=timeout)


def version(serial, package):
    match = re.search(r"versionCode=(\d+)", shell(serial, f"dumpsys package {package}"))
    return int(match.group(1)) if match else None


def install(serial, apk):
    out = adb(serial, "install", "-r", "-t", str(apk), check=False)
    if "Success" not in out:
        raise RuntimeError(f"install {Path(apk).name}: {out[-300:]}")


def run_as(serial, package, command):
    return shell(serial, f"run-as {package} sh -c '{command}'")


class Checks:
    def __init__(self):
        self.failed = 0

    def report(self, verdict, name, detail=""):
        if verdict == "FAIL":
            self.failed += 1
        print(f"{verdict} {name} {detail}".rstrip(), flush=True)

    def judge(self, name, ok, detail=""):
        self.report("PASS" if ok else "FAIL", name, detail)


def enable_template(serial):
    """Switches the template on in Plugin Center; answers the original state to put back, or None.
    zh-CN: 在插件中心打开模板插件; 返回需要恢复的原状态, 不需要时返回 None."""
    state = run_as(serial, HOST, f"cat {ENABLE_STATE} 2>/dev/null")
    if TEMPLATE_OFF not in state:
        return None
    write_enable_state(serial, state.replace(TEMPLATE_OFF, TEMPLATE_ON))
    print("INFO the template plugin was switched off in Plugin Center, switched on for the check", flush=True)
    return state


def write_enable_state(serial, text):
    with tempfile.TemporaryDirectory() as work:
        local = Path(work) / "state.xml"
        local.write_text(text + "\n", encoding="utf-8", newline="\n")
        remote = "/data/local/tmp/paper-packaged-state.xml"
        adb(serial, "push", str(local), remote)
        try:
            shell(serial, f"chmod 644 {remote}; am force-stop {HOST}")
            run_as(serial, HOST, f"cp {remote} {ENABLE_STATE}")
        finally:
            shell(serial, f"rm -f {remote}")


def produce(serial, work):
    """Packs the app with the host test and pulls it. zh-CN: 用宿主测试打包并拉取 APK."""
    shell(serial, f"am force-stop {HOST}; rm -f {' '.join(PRODUCED)}")
    started = time.time()
    out = shell(serial, f"am instrument -w -r -e class {PRODUCER} -e {PRODUCE_ARG} true {RUNNER}", timeout=900)
    codes = [int(code) for code in re.findall(r"INSTRUMENTATION_STATUS_CODE: (-?\d+)", out)]
    if not codes or codes[-1] != 0:
        failure = re.search(r"INSTRUMENTATION_STATUS: stack=([^\n]*)", out)
        raise RuntimeError(f"the producer failed: {(failure.group(1) if failure else out[-300:])[:300]}")
    apk = work / "paper-packaged.apk"
    adb(serial, "pull", PRODUCED[0], str(apk))
    summary = json.loads(shell(serial, f"cat {PRODUCED[1]}") or "{}")
    return apk, summary, time.time() - started


def run_app(serial):
    """Starts the packaged app and answers its result once done, or None. zh-CN: 启动打包应用, 返回完成后的结果或 None."""
    shell(serial, f"am force-stop {APP}; rm -f {RESULT}")
    shell(serial, f"monkey -p {APP} -c android.intent.category.LAUNCHER 1")
    deadline = time.time() + RESULT_TIMEOUT
    while time.time() < deadline:
        text = shell(serial, f"cat {RESULT} 2>/dev/null")
        try:
            result = json.loads(text) if text else None
        except ValueError:
            result = None
        if result and result.get("done"):
            return result
        time.sleep(2)
    return None


def value(result, name):
    """The value of a step that succeeded ({} when it has none), or None. zh-CN: 成功步骤的返回值 (没有时为 {}), 否则为 None."""
    step = result.get("steps", {}).get(name) or {}
    return (step.get("value") or {}) if step.get("ok") else None


def check_calls(checks, result):
    steps = result.get("steps", {})
    failed = {name: steps[name].get("code") for name in CALLS if name in steps and not steps[name].get("ok")}
    checks.judge("available", result.get("available") == "true", f"isAvailable={result.get('available')} failed={failed}")
    made = value(result, "fromText")
    checks.judge("fromText", made is not None and made.get("pageCount") == 2, f"pages={made and made.get('pageCount')}")
    opened = value(result, "open")
    checks.judge("open", opened is not None and opened.get("pageCount") == 1, f"pageCount={opened and opened.get('pageCount')}")
    rendered = value(result, "render")
    size = rendered and f"{rendered.get('width')}x{rendered.get('height')}"
    checks.judge("render", bool(rendered) and rendered.get("width", 0) > 0 and rendered.get("height", 0) > 0, f"72dpi={size}")
    merged = value(result, "merge")
    checks.judge("merge", merged is not None and merged.get("pageCount") == 2,
                 f"pageCount={merged and merged.get('pageCount')} bytes={merged and merged.get('sizeBytes')}")
    info = value(result, "info")
    checks.judge("info", info is not None and info.get("pageCount") == 2 and info.get("encrypted") is False,
                 f"pageCount={info and info.get('pageCount')} encrypted={info and info.get('encrypted')}")
    locked = value(result, "encrypt")
    checks.judge("encrypt", locked is not None and locked.get("withoutPassword") == "PASSWORD_REQUIRED" and locked.get("encrypted") is True,
                 f"withoutPassword={locked and locked.get('withoutPassword')} encrypted={locked and locked.get('encrypted')}")
    view = value(result, "view")
    state = (view or {}).get("state") or {}
    checks.judge("view", view is not None and view.get("reason") == "finished" and state.get("pageCount") == 2,
                 f"state={state.get('page')}/{state.get('pageCount')} visible={state.get('visible')} reason={view and view.get('reason')}")
    held = value(result, "hold")
    checks.judge("hold", held is not None, f"pageCount={held.get('pageCount')}" if held is not None else f"code={steps.get('hold', {}).get('code')}")


def check_release(checks, serial, result):
    """The plugin keeps the held document in k<uid>-<pid>/ and drops it once the app is killed.
    zh-CN: 插件把持有的文档放在 k<uid>-<pid>/, 应用被杀后释放."""
    directory = f"k{result.get('uid')}-{result.get('pid')}"
    listing = run_as(serial, PLUGIN, "ls cache/pdf 2>&1")
    if "not debuggable" in listing or "unknown package" in listing:
        checks.report("SKIP", "release", f"run-as {PLUGIN} is not possible ({listing[:80]})")
        return
    held = run_as(serial, PLUGIN, f"ls cache/pdf/{directory} 2>/dev/null").split()
    checks.judge("held", directory in listing.split() and len(held) >= 1, f"dir={directory} entries={len(held)}")
    shell(serial, f"am force-stop {APP}")
    started = time.time()
    while time.time() - started < RELEASE_TIMEOUT:
        if directory not in run_as(serial, PLUGIN, "ls cache/pdf 2>/dev/null").split():
            checks.report("PASS", "release", f"dir removed {time.time() - started:.0f} s after am force-stop")
            return
        time.sleep(2)
    checks.report("FAIL", "release", f"{directory} still present {RELEASE_TIMEOUT} s after am force-stop")


def check_unavailable(checks, serial, plugin_apk, work):
    apk = Path(plugin_apk) if plugin_apk else None
    if apk is None:
        paths = [line.removeprefix("package:") for line in shell(serial, f"pm path {PLUGIN}").splitlines()]
        if len(paths) != 1:
            checks.report("SKIP", "unavailable", f"cannot keep a copy of the plugin ({len(paths)} APKs)")
            return
        apk = work / "plugin.apk"
        adb(serial, "pull", paths[0], str(apk))
    shell(serial, f"pm uninstall -k {PLUGIN}")
    try:
        result = run_app(serial)
        if result is None:
            checks.report("FAIL", "unavailable", f"no result within {RESULT_TIMEOUT} s")
            return
        codes = {name: (result["steps"].get(name) or {}).get("code") for name in CALLS if name != "hold"}
        wrong = {name: code for name, code in codes.items() if code != "PLUGIN_UNAVAILABLE"}
        checks.judge("unavailable", result.get("available") == "false" and not wrong,
                     f"isAvailable={result.get('available')} {len(codes) - len(wrong)}/{len(codes)} calls PLUGIN_UNAVAILABLE"
                     + (f" other={wrong}" if wrong else ""))
    finally:
        shell(serial, f"am force-stop {APP}")
        install(serial, apk)
        print(f"INFO plugin installed back (versionCode {version(serial, PLUGIN)})", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--host-apk", help="AutoJs6 debug APK to install first")
    parser.add_argument("--host-test-apk", help="AutoJs6 androidTest APK to install first")
    parser.add_argument("--template-apk", help="APK Builder template plugin APK (official signature) to install first")
    parser.add_argument("--plugin-apk", help="3-Paper PDF APK to install first and to install back after the unavailable pass")
    parser.add_argument("--app-apk", help="a packaged app built before, instead of producing one")
    parser.add_argument("--no-unavailable", action="store_true", help="skip the pass without the plugin")
    parser.add_argument("--keep", action="store_true", help="keep the packaged app and the produced files")
    args = parser.parse_args()
    serial = args.serial
    for apk in (args.host_apk, args.host_test_apk, args.template_apk, args.plugin_apk):
        if apk:
            install(serial, apk)
    sdk = shell(serial, "getprop ro.build.version.sdk")
    host, plugin, template = version(serial, HOST), version(serial, PLUGIN), version(serial, TEMPLATE)
    print(f"INFO device={shell(serial, 'getprop ro.product.model')} sdk={sdk} host={host} plugin={plugin} template={template} "
          f"test={version(serial, TEST)}", flush=True)
    if host is None or host < MIN_HOST_BUILD or plugin is None:
        print(f"FAIL setup host {host} (needs {MIN_HOST_BUILD} or later) or plugin {plugin} missing")
        return 1
    checks = Checks()
    original_state = None
    with tempfile.TemporaryDirectory() as temp:
        work = Path(temp)
        try:
            if args.app_apk:
                apk = Path(args.app_apk)
            else:
                if version(serial, TEST) is None or template is None:
                    print("FAIL setup the host test package or the template plugin is missing")
                    return 1
                original_state = enable_template(serial)
                apk, summary, seconds = produce(serial, work)
                checks.report("PASS", "package", f"{summary.get('bytes')} bytes in {seconds:.0f} s")
            shell(serial, f"pm uninstall {APP}")
            install(serial, apk)
            granted = f"{PERMISSION}: granted=true" in shell(serial, f"dumpsys package {APP}")
            checks.judge("permission", granted, f"{PERMISSION} granted={granted}")
            result = run_app(serial)
            if result is None:
                checks.report("FAIL", "run", f"no result within {RESULT_TIMEOUT} s")
            else:
                check_calls(checks, result)
                if result.get("steps", {}).get("hold", {}).get("ok"):
                    check_release(checks, serial, result)
                else:
                    checks.report("SKIP", "release", "no document was held")
            if not args.no_unavailable:
                check_unavailable(checks, serial, args.plugin_apk, work)
        finally:
            shell(serial, f"am force-stop {APP}")
            if not args.keep:
                shell(serial, f"pm uninstall {APP}; rm -f {' '.join(PRODUCED)}")
            if original_state is not None:
                write_enable_state(serial, original_state)
    print("PASS run" if checks.failed == 0 else f"FAIL run ({checks.failed} failed)")
    return 0 if checks.failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
