"""Device check of the file manager catalog of the plugin as the installed host reads it (roadmap P6.15, release gate 6:
"the file manager catalog works" with a host that has contract 1 only, or with the current host).

    py -3.12 -I .python/device_explorer_catalog_check.py -s SERIAL [--timeout 60]

A host script calls the host's own Explorer Action registry (refresh, then the snapshot it keeps for the file manager)
and lists the actions of the plugin. Expected: the nine plugin tools (open, merge, split, compress, to-images,
watermark, extract-text, encrypt, decrypt), each with a label; the host command "pdf-make-searchable" (P6.11, within
protocol 28) is listed exactly when the host knows it, and a host without it drops that entry alone rather than the
whole catalog; logcat has no "Failed to load Explorer Action catalog" line for the plugin. Needs a debug host (the
registry is read by reflection) with the plugin installed and enabled; nothing is tapped. Prints PASS / FAIL lines
with the key values only and removes its files on the device. Exit code 0 when every check passes.

zh-CN: 在设备上按已安装宿主的读取方式核对插件的文件管理器目录 (路线图 P6.15, 发布门禁 6 的 "文件管理器目录正常": 只含契约 1 的宿主,
或当前宿主). 宿主脚本调用宿主自己的 Explorer Action 注册表 (先 refresh, 再读它为文件管理器保存的快照), 列出本插件的动作. 预期: 插件的
九个工具都在且都有标签; 宿主命令 "pdf-make-searchable" (P6.11, 协议 28 之内) 只在宿主认识它时出现, 不认识的宿主只丢掉这一项而不是整份
目录; logcat 里没有本插件的 "Failed to load Explorer Action catalog". 需要 debug 宿主 (注册表经反射读取), 插件已安装并启用; 不点按任何界面.
只输出每项的 PASS / FAIL 与关键数值, 结束时删掉设备上的文件. 全部通过时退出码为 0.
"""

import argparse
import json
import shlex
import subprocess
import sys
import time
from pathlib import Path

HOST = "org.autojs.autojs6"
PLUGIN = "io.github.supermonster003.autojs6.plugin.three.paper.pdf"
RUN_ACTIVITY = f"{HOST}/org.autojs.autojs.external.open.RunIntentActivity"
BASE = "/storage/emulated/0/Documents/3-Paper PDF/catalog-check"
TOOLS = ("open", "merge", "split", "compress", "to-images", "watermark", "extract-text", "encrypt", "decrypt")
HOST_COMMAND = "pdf-make-searchable"

PROBE_JS = r"""
var OUT = @@OUT@@;
var PLUGIN = @@PLUGIN@@;
var answer = { actions: [] };
var loader = context.getApplicationContext().getClassLoader();
function load(name) { return java.lang.Class.forName(name, true, loader); }
try {
    try {
        load('org.autojs.plugin.explorer.api.ExplorerActionHostEngineIds').getField('PDF_MAKE_SEARCHABLE');
        answer.hostKnowsCommand = true;
    } catch (e) {
        answer.hostKnowsCommand = false;
    }
    var Registry = load('org.autojs.autojs.core.plugin.explorer.ExplorerActionRegistry');
    var registry = Registry.getField('INSTANCE').get(null);
    var state = { done: false, value: null };
    var emptyContext = load('kotlin.coroutines.EmptyCoroutineContext').getField('INSTANCE').get(null);
    // A direct call lets Rhino turn the object into the Continuation interface (Method.invoke would pass it as is).
    // zh-CN: 直接调用时 Rhino 会把这个对象转换为 Continuation 接口 (经 Method.invoke 则会原样传入).
    var first = registry.refresh(context, {
        getContext: function () { return emptyContext; },
        resumeWith: function (result) { state.value = String(result); state.done = true; }
    });
    if (String(first) !== 'COROUTINE_SUSPENDED') { state.value = String(first); state.done = true; }
    var deadline = Date.now() + 60000;
    while (!state.done && Date.now() < deadline) sleep(200);
    answer.refresh = state.done ? state.value : 'timeout';
    var field = Registry.getDeclaredField('snapshot');
    field.setAccessible(true);
    var holder = field.get(java.lang.reflect.Modifier.isStatic(field.getModifiers()) ? null : registry);
    var list = holder.get();
    answer.total = list.size();
    for (var i = 0; i < list.size(); i++) {
        var action = list.get(i);
        if (String(action.getPackageName()) !== PLUGIN) continue;
        answer.actions.push({ id: String(action.getDescriptor().getId()), title: String(action.getMenuTitle() || '') });
    }
} catch (e) {
    answer.error = String(e);
}
files.write(OUT, JSON.stringify(answer));
"""


def adb(serial, *args, check=True, timeout=120):
    result = subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8",
                            errors="replace", timeout=timeout)
    if check and result.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args[:3])} failed: {(result.stderr or result.stdout).strip()[:300]}")
    return result.stdout.strip()


def shell(serial, command, timeout=120):
    return adb(serial, "shell", command, check=False, timeout=timeout)


def front(serial):
    """Brings the host to the foreground first (RunIntentActivity of a frozen host does nothing on some systems)."""
    found = shell(serial, f"cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER {HOST}")
    activity = next((line.strip() for line in found.splitlines() if line.strip().startswith(HOST + "/") and "leakcanary" not in line), None)
    if activity is None:
        raise RuntimeError("the launcher activity of the host was not found")
    shell(serial, f"am start -f 0x10008000 -n {activity}")
    time.sleep(4)


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--timeout", type=int, default=60)
    args = parser.parse_args()
    serial = args.serial
    scratch = Path(__file__).resolve().parent / "__pycache__"
    scratch.mkdir(exist_ok=True)
    out, script = f"{BASE}/answer.json", f"{BASE}/probe.js"
    failures = 0

    def report(ok, label, detail=""):
        nonlocal failures
        failures += 0 if ok else 1
        print(f"{'PASS' if ok else 'FAIL'} {label}" + (f" {detail}" if detail else ""), flush=True)

    host_version = shell(serial, f"dumpsys package {HOST} | grep -m1 versionCode").split()
    plugin_version = shell(serial, f"dumpsys package {PLUGIN} | grep -m1 versionCode").split()
    print(f"INFO host {host_version[0] if host_version else '-'} plugin {plugin_version[0] if plugin_version else '-'}", flush=True)
    since = shell(serial, "date +'%m-%d %H:%M:%S.000'")
    shell(serial, f"mkdir -p {shlex.quote(BASE)} && rm -f {shlex.quote(out)}")
    local = scratch / "catalog-probe.tmp"
    local.write_text(PROBE_JS.replace("@@OUT@@", json.dumps(out)).replace("@@PLUGIN@@", json.dumps(PLUGIN)),
                     encoding="utf-8", newline="\n")
    try:
        adb(serial, "push", str(local), script)
    finally:
        local.unlink()
    try:
        front(serial)
        started = shell(serial, f"am start -n {RUN_ACTIVITY} -e path {shlex.quote(script)}")
        if "Error" in started:
            raise RuntimeError(started[:300])
        deadline, text = time.time() + args.timeout, ""
        while time.time() < deadline and not text:
            time.sleep(2)
            text = shell(serial, f"cat {shlex.quote(out)} 2>/dev/null")
        if not text:
            report(False, "catalog.answer", "no answer from the host script")
            return 1
        answer = json.loads(text)
        if "error" in answer:
            report(False, "catalog.answer", answer["error"][:300])
            return 1
        ids = [action["id"] for action in answer["actions"]]
        untitled = [action["id"] for action in answer["actions"] if not action["title"].strip()]
        report(answer["refresh"] in ("true", "false"), "catalog.refresh", f"refresh={answer['refresh']} actions={answer['total']}")
        missing = [tool for tool in TOOLS if tool not in ids]
        report(not missing and not untitled, "catalog.tools", f"plugin actions={len(ids)} missing={missing or '-'} untitled={untitled or '-'}")
        listed = HOST_COMMAND in ids
        report(listed == answer["hostKnowsCommand"], "catalog.hostCommand",
               f"host knows {HOST_COMMAND}={str(answer['hostKnowsCommand']).lower()} listed={str(listed).lower()}")
        extra = [i for i in ids if i not in TOOLS and i != HOST_COMMAND]
        report(not extra and len(ids) == len(set(ids)), "catalog.noExtra", f"extra={extra or '-'}")
        log = shell(serial, f"logcat -d -T {shlex.quote(since)}", timeout=60)
        failed = [line for line in log.splitlines() if "Explorer Action catalog" in line and PLUGIN in line]
        report(not failed, "catalog.log", f"catalog warnings for the plugin={len(failed)}")
    finally:
        shell(serial, f"rm -rf {shlex.quote(BASE)}")
    print(f"DONE fail={failures}", flush=True)
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
