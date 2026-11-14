"""Checks pdf.view of the host on a device: start page and search, and the launch while the host is in the background
(3-Paper PDF roadmap P3.2 host row, appendix F).

Usage: py -3.12 -I .python/device_view_check.py -s SERIAL [--keep] [--timeout SECONDS]

1. The host (with the pdf module, 6.8.1 or later) and 3-Paper PDF must already be installed, enabled and allowed to
   read and write shared storage. The tool installs nothing, changes no setting and answers no dialog.
2. A host script (pushed to /sdcard/Documents/3-Paper PDF/runner/) makes a 6-page document whose pages 2 and 5 hold
   "foo", then opens it with pdf.view(path, { page: 3, search: 'foo' }); the viewer has to stop at page 5 (the first
   hit from the start page on), be the resumed Activity and end with the close reason "finished".
3. The tool then presses HOME (input keyevent) and the script calls pdf.view again. The expected outcome follows the
   host's rule: Android 10 or later without the "display over other apps" permission answers UI_REQUIRES_FOREGROUND;
   below Android 10 or with that permission the session opens, and the tool records whether the viewer actually came
   to the front (OEM systems such as ColorOS may hold a background start back). The script and the tool hand over
   through small files in the runner directory.
4. The pushed script and its outputs (.../view-check/) are removed afterwards unless --keep is given;
   /sdcard/Documents/3-Paper PDF/ itself is removed only when it is empty and did not exist before.
Every check prints one line "PASS|FAIL|INFO <name> <key values>"; the exit code is 1 when a check failed.

zh-CN: 在设备上检查宿主的 pdf.view: 起始页与搜索, 以及宿主在后台时的启动 (3-Paper PDF 路线图 P3.2 宿主行, 附录 F). 1. 宿主 (带 pdf 模块, 6.8.1
或以上) 与 3-Paper PDF 须已安装, 启用并有共享存储读写权限. 本工具不安装任何东西, 不改任何设置, 也不应答任何对话框. 2. 宿主脚本 (推到
/sdcard/Documents/3-Paper PDF/runner/) 生成一个 6 页文档, 第 2 与第 5 页含 "foo", 再用 pdf.view(path, { page: 3, search: 'foo' }) 打开;
查看器须停在第 5 页 (从起始页起的第一个命中), 是处于 resumed 的 Activity, 并以关闭原因 "finished" 结束. 3. 之后本工具按 HOME 键 (input keyevent),
脚本再调用一次 pdf.view. 预期结果按宿主的规则: Android 10 及以上且没有 "显示在其他应用上层" 权限时报 UI_REQUIRES_FOREGROUND; 低于 Android 10
或有该权限时会话打开, 本工具记录查看器是否真的到了前台 (ColorOS 等厂商系统可能拦下后台启动). 脚本与本工具经 runner 目录里的小文件交接.
4. 结束后删除推送的脚本与产物 (.../view-check/), 给出 --keep 时保留; /sdcard/Documents/3-Paper PDF/ 本身只在为空且原先不存在时删除.
每项检查输出一行 "PASS|FAIL|INFO <名称> <关键数值>"; 有检查失败时退出码为 1.
"""
import argparse
import json
import re
import shlex
import subprocess
import sys
import tempfile
import time
from pathlib import Path

HOST = "org.autojs.autojs6"
RUN_ACTIVITY = f"{HOST}/org.autojs.autojs.external.open.RunIntentActivity"
PLUGIN = "io.github.supermonster003.autojs6.plugin.three.paper.pdf"
BASE = "/sdcard/Documents/3-Paper PDF"
RUNNER = f"{BASE}/runner"
OUT = f"{BASE}/view-check"
SCRIPT = f"{RUNNER}/view-check.js"
RESULT = f"{OUT}/result.json"

JS = r"""
const OUT = '__OUT__/';
const RESULT = OUT + 'result.json';
const summary = { errors: [] };

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

function describe(e) {
    if (e instanceof pdf.PdfError) return e.code;
    return String(e).slice(0, 200);
}

function mark(name, data) {
    files.write(OUT + name, JSON.stringify(data || {}));
}

async function waitFor(name, ms) {
    let deadline = Date.now() + ms;
    while (Date.now() < deadline) {
        if (files.exists(OUT + name)) return JSON.parse(files.read(OUT + name) || '{}');
        await sleep(300);
    }
    throw new Error(name + ' did not arrive within ' + ms + ' ms');
}

function makeDocument() {
    let parts = [];
    for (let i = 1; i <= 6; i++) {
        let text = 'Page ' + i + ((i === 2 || i === 5) ? '\n\nfoo marks this page' : '\n\nnothing to find here');
        parts.push(pdf.fromText(text, OUT + 'part-' + i + '.pdf', { fontSize: 18 }).path);
    }
    return pdf.merge(parts, OUT + 'view-check.pdf').path;
}

function open(path, record) {
    let session = pdf.view(path, { page: 3, search: 'foo' });
    record.pages = [];
    session.on('page', e => record.pages.push(e.page));
    record.closed = new Promise(resolve => session.on('close', e => resolve(e.reason)));
    return session;
}

async function settle(session, target, ms) {
    let deadline = Date.now() + ms;
    let state = session.state();
    while (Date.now() < deadline && !(state.pageCount > 0 && state.page === target)) {
        await sleep(250);
        state = session.state();
    }
    return state;
}

async function finish(session, record) {
    session.close({ finish: true });
    record.reason = await Promise.race([record.closed, sleep(10000).then(() => 'timeout')]);
    delete record.closed;
}

async function main() {
    summary.sdk = device.sdkInt;
    summary.overlays = Boolean(android.provider.Settings.canDrawOverlays(context));
    let path = makeDocument();

    let front = {};
    let session = open(path, front);
    front.state = await settle(session, 5, 10000);
    mark('stage-front.json', front.state);
    front.ack = await waitFor('ack-front.json', 20000);
    await finish(session, front);
    summary.front = front;

    mark('stage-home.json');
    await waitFor('go-home.json', 30000);
    let back = {};
    try {
        session = open(path, back);
        back.opened = true;
    } catch (e) {
        back.opened = false;
        back.error = describe(e);
    }
    if (back.opened) {
        back.state = await settle(session, 5, 8000);
        mark('stage-back.json', back.state);
        back.ack = await waitFor('ack-back.json', 20000);
        await finish(session, back);
    }
    summary.back = back;
}

files.ensureDir(RESULT);
let keepAlive = setInterval(() => {}, 60000);
main().then(null, e => summary.errors.push(describe(e))).then(() => {
    files.write(RESULT, JSON.stringify(summary));
    clearInterval(keepAlive);
});
"""


def adb(serial, *args, check=True, timeout=300):
    result = subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8",
                            errors="replace", timeout=timeout)
    if check and result.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args[:3])} failed: {(result.stderr or result.stdout).strip()[:300]}")
    return result.stdout.strip()


def shell(serial, command, check=False, timeout=120):
    return adb(serial, "shell", command, check=check, timeout=timeout)


def quote(path):
    return shlex.quote(path)


def version(serial, package):
    match = re.search(r"versionCode=(\d+)", shell(serial, f"dumpsys package {package}"))
    return int(match.group(1)) if match else None


def exists(serial, path):
    return shell(serial, f"[ -e {quote(path)} ] && echo yes || echo no") == "yes"


def resumed(serial):
    """The package of the top resumed Activity. zh-CN: 处于 resumed 的顶层 Activity 所属的包."""
    text = shell(serial, "dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity'")
    match = re.search(r"\{\S+ \S+ ([\w.]+)/", text)
    return match.group(1) if match else "-"


def front(serial):
    shell(serial, f"am force-stop {HOST}")
    # A debug host also has the LeakCanary launcher activity. zh-CN: debug 宿主还有 LeakCanary 的启动器活动.
    found = shell(serial, f"cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER {HOST}")
    activity = next((line.strip() for line in found.splitlines() if line.strip().startswith(HOST + "/") and "leakcanary" not in line), None)
    if activity is None:
        raise RuntimeError("the launcher activity of the host was not found")
    shell(serial, f"am start -f 0x10008000 -n {activity}")
    # A run intent sent while the splash screen is still up can be lost: wait for the main activity.
    # zh-CN: 启动页还在时发出的运行意图可能丢失: 等主活动进入 RESUMED.
    deadline = time.time() + 40
    while time.time() < deadline:
        if f"{HOST}/org.autojs.autojs.ui.main.MainActivity" in shell(serial, "dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity'"):
            break
        time.sleep(1)
    time.sleep(3)


def wait(serial, path, timeout):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if exists(serial, path):
            time.sleep(0.5)
            return json.loads(shell(serial, f"cat {quote(path)}") or "{}")
        time.sleep(1)
    return None


def push_text(serial, text, remote):
    with tempfile.TemporaryDirectory() as directory:
        local = Path(directory) / Path(remote).name
        local.write_text(text, encoding="utf-8", newline="\n")
        adb(serial, "push", str(local), remote)


def answer(serial, name, data):
    shell(serial, f"echo {quote(json.dumps(data))} > {quote(OUT + '/' + name)}")


def run(serial, timeout):
    """Drives the script through both stages; answers its summary or None. zh-CN: 驱动脚本走完两个阶段, 返回摘要或 None."""
    push_text(serial, JS.replace("__OUT__", OUT), SCRIPT)
    for attempt in (1, 2):
        front(serial)
        shell(serial, f"am start -n {RUN_ACTIVITY} -e path {quote(SCRIPT)}")
        state = wait(serial, f"{OUT}/stage-front.json", 45 if attempt == 1 else timeout)
        if state is not None or exists(serial, RESULT):
            break
        print(f"INFO view-check.js gave no sign within 45 s (attempt {attempt})", flush=True)
    else:
        return None
    if state is not None:
        answer(serial, "ack-front.json", {"resumed": resumed(serial)})
        if wait(serial, f"{OUT}/stage-home.json", timeout) is not None:
            shell(serial, "input keyevent KEYCODE_HOME")
            time.sleep(3)
            answer(serial, "go-home.json", {"resumed": resumed(serial)})
            if wait(serial, f"{OUT}/stage-back.json", 30) is not None:
                time.sleep(2)
                answer(serial, "ack-back.json", {"resumed": resumed(serial)})
    return wait(serial, RESULT, timeout)


def main():
    parser = argparse.ArgumentParser(description="Checks pdf.view of the host on a device (P3.2).")
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--keep", action="store_true", help="keep the pushed script and the outputs")
    parser.add_argument("--timeout", type=int, default=120, help="seconds to wait for each stage (default 120)")
    args = parser.parse_args()
    serial = args.serial

    host, plugin = version(serial, HOST), version(serial, PLUGIN)
    if host is None or host < 6641 or plugin is None:
        print(f"FAIL setup host={host} plugin={plugin} (needs host 6641 or later and 3-Paper PDF)")
        return 1
    sdk = int(shell(serial, "getprop ro.build.version.sdk") or 0)
    model = shell(serial, "getprop ro.product.model")
    print(f"INFO device {model} sdk={sdk} host={host} plugin={plugin}", flush=True)

    base_existed = exists(serial, BASE)
    shell(serial, f"rm -rf {quote(OUT)}; mkdir -p {quote(OUT)} {quote(RUNNER)}")
    failed = 0
    try:
        s = run(serial, args.timeout)
        if s is None:
            print("FAIL view no result within the timeout")
            return 1
        if s.get("errors"):
            print(f"FAIL view errors={s['errors']}")
            failed += 1
        overlays = s.get("overlays")
        f = s.get("front") or {}
        state = f.get("state") or {}
        on_top = (f.get("ack") or {}).get("resumed")
        ok = state.get("page") == 5 and state.get("pageCount") == 6 and on_top == PLUGIN and f.get("reason") == "finished"
        failed += 0 if ok else 1
        print(f"{'PASS' if ok else 'FAIL'} view.front page={state.get('page')}/{state.get('pageCount')} "
              f"visible={state.get('visible')} resumed={on_top} pages={f.get('pages')} close={f.get('reason')}")

        b = s.get("back")
        if b is None:
            print("FAIL view.background no result")
            return 1
        launchable = sdk < 29 or bool(overlays)
        if not launchable:
            ok = not b.get("opened") and b.get("error") == "UI_REQUIRES_FOREGROUND"
            failed += 0 if ok else 1
            print(f"{'PASS' if ok else 'FAIL'} view.background expected=UI_REQUIRES_FOREGROUND "
                  f"got={b.get('error') or 'opened'} sdk={sdk} overlays={overlays}")
        else:
            state = b.get("state") or {}
            on_top = (b.get("ack") or {}).get("resumed")
            ok = bool(b.get("opened"))
            failed += 0 if ok else 1
            print(f"{'PASS' if ok else 'FAIL'} view.background expected=opens got={'opened' if ok else b.get('error')} "
                  f"sdk={sdk} overlays={overlays} page={state.get('page')}/{state.get('pageCount')} "
                  f"visible={state.get('visible')} resumed={on_top} close={b.get('reason')}")
            print(f"INFO view.background viewer {'came to the front' if on_top == PLUGIN else 'stayed behind (' + str(on_top) + ')'}")
    finally:
        if not args.keep:
            shell(serial, f"rm -rf {quote(OUT)} {quote(SCRIPT)}; rmdir {quote(RUNNER)} 2>/dev/null")
            if not base_existed:
                shell(serial, f"rmdir {quote(BASE)} 2>/dev/null")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
