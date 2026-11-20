"""Runs the same contract version 2 checks of 3-Paper PDF 1.1.0 in Rhino, Node.js (CJS, ESM, Worker) and Bun on a device
(3-Paper PDF roadmap P6.15, checkpoints 17 and 18).

Usage: py -3.12 -I .python/device_runtime_v2_check.py -s SERIAL [--runtimes rhino,node-cjs,node-esm,node-worker,bun]
       [--timeout SECONDS]

1. The host (6.8.1 with the version 2 methods), 3-Paper PDF 1.1.0, Node.js Runtime 2.2.0 and Bun Runtime 0.23.0 (API 33
   or later) must already be installed and enabled, the host allowed to read and write shared storage. The tool
   installs nothing and answers no dialog; a runtime that is not installed is reported SKIP.
2. The fixtures text.pdf, outline.pdf, form.pdf and scanned-multi.pdf go to /sdcard/Download/aj6-paper-v2/. Every
   runtime runs the same code: the warning codes of a compression that does not get smaller (OUTPUT_NOT_SMALLER), an
   encryption without an owner password (OWNER_PASSWORD_RANDOM), a decryption of a plain file (NOT_ENCRYPTED) and a
   form fill with an unknown field and ignoreMissing (FIELDS_IGNORED), then the outline budget: 10,000 outline items of
   about 76 characters are written and read back (truncated === true, a non-enumerable read-only flag that
   JSON.stringify leaves out; outline.pdf reads truncated === false).
3. Rhino runs the code as an AutoJs6 script, node-cjs as a Node project (main.cjs), node-esm as a Node project
   (main.mjs with import pdf from 'autojs6:pdf'), node-worker inside a Worker of main.cjs, bun through host.pdf with the
   pdf.control grant set for the run by a host script and put back afterwards. The console lines are read from the
   GlobalConsole logcat (not usable on OPD2413, whose logd drops console lines).
4. Every warningCodes value and the outline length must be the same in every runtime that ran. The pushed directory
   is removed afterwards.
One line per check: PASS / FAIL / SKIP, runtime, label and key values; the exit code is 1 when a check failed.

zh-CN: 在设备上用 Rhino, Node.js (CJS, ESM, Worker) 与 Bun 运行同一组 3-Paper PDF 1.1.0 契约版本 2 的检查 (3-Paper PDF 路线图 P6.15, 检查点
17 与 18). 1. 宿主 (带版本 2 方法的 6.8.1), 3-Paper PDF 1.1.0, Node.js Runtime 2.2.0 与 Bun Runtime 0.23.0 (API 33 或以上) 须已安装并启用, 宿主
有共享存储读写权限; 本工具不安装任何东西, 不应答对话框, 未安装的运行时报 SKIP. 2. 样例 text.pdf, outline.pdf, form.pdf 与 scanned-multi.pdf 推到
/sdcard/Download/aj6-paper-v2/. 每个运行时运行同一段代码: 没有变小的压缩 (OUTPUT_NOT_SMALLER), 不给所有者密码的加密 (OWNER_PASSWORD_RANDOM),
解密未加密的文件 (NOT_ENCRYPTED), 带未知字段且 ignoreMissing 的表单填写 (FIELDS_IGNORED) 的警告代码; 然后是大纲预算: 写入 10,000 个约 76 个
字符的目录项再读回 (truncated === true, 是不可枚举的只读标记, JSON.stringify 不含它; outline.pdf 读回 truncated === false). 3. Rhino 作为 AutoJs6
脚本运行, node-cjs 作为 Node 项目 (main.cjs), node-esm 作为 Node 项目 (main.mjs, import pdf from 'autojs6:pdf'), node-worker 在 main.cjs 的 Worker
中运行, bun 经 host.pdf 运行, 运行期间由宿主脚本设置 pdf.control 授权, 结束后恢复. console 行从 GlobalConsole logcat 读取 (OPD2413 的 logd 会丢
console 行, 不适用). 4. 所有运行过的运行时, 每个 warningCodes 与大纲长度都必须相同. 结束后删除推送的目录. 每项检查输出一行: PASS / FAIL / SKIP,
运行时, 名称与关键数值; 有检查失败时退出码为 1.
"""
import argparse
import json
import re
import subprocess
import sys
import threading
import time
import uuid
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
HOST = "org.autojs.autojs6"
PLUGIN = "io.github.supermonster003.autojs6.plugin.three.paper.pdf"
NODE = "io.github.supermonster003.autojs6.plugin.nodejs"
BUN = "io.github.supermonster003.autojs6.plugin.bun.runtime"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
DEVICE_DIR = "/sdcard/Download/aj6-paper-v2"
FIXTURES = ("text.pdf", "outline.pdf", "form.pdf", "scanned-multi.pdf")
RUNTIMES = ("rhino", "node-cjs", "node-esm", "node-worker", "bun")
PREFIX = re.compile(r"^\d\d:\d\d:\d\d\.\d{3}/[A-Z]: ")
MARK = "AJ6PDFV2 "

# The checks; AJ6_DIR is replaced before the push. Plain JavaScript that Rhino, a Node module (CJS or ESM), a Worker
# (eval) and a Bun module all run unchanged; `await` also takes the plain values of Rhino's synchronous methods.
# zh-CN: 检查代码; 推送前替换 AJ6_DIR. Rhino, Node 模块 (CJS 或 ESM), Worker (eval) 与 Bun 模块都可原样运行; `await` 也接受 Rhino 同步方法的普通返回值.
CHECKS_JS = r"""
async function paperV2(pdf, log) {
  const DIR = AJ6_DIR;
  const TEXT = DIR + '/text.pdf', OUTLINE = DIR + '/outline.pdf', FORM = DIR + '/form.pdf', SCANNED = DIR + '/scanned-multi.pdf';
  const check = async (label, body) => {
    const start = Date.now();
    try {
      const detail = await body();
      log('AJ6PDFV2 PASS ' + label + ' ' + detail + ' ' + (Date.now() - start) + 'ms');
    } catch (e) {
      log('AJ6PDFV2 FAIL ' + label + ' ' + (e && e.code ? e.code + ' ' : '') + String(e && e.message !== undefined ? e.message : e).slice(0, 200));
    }
  };
  const codes = (r, code) => {
    const list = r.warningCodes;
    if (!list || list.length !== r.warnings.length) throw new Error('warningCodes ' + JSON.stringify(list) + ' for ' + r.warnings.length + ' warnings');
    if (list.indexOf(code) < 0) throw new Error('no ' + code + ' in ' + JSON.stringify(list));
    return 'codes=' + JSON.stringify(list);
  };
  await check('compress', async () => {
    const low = await pdf.compress(SCANNED, DIR + '/v2-low.pdf', { preset: 'low' });
    const again = await pdf.compress(low.path, DIR + '/v2-again.pdf', { preset: 'high' });
    if (again.warnings.join(' ').indexOf('is not smaller') < 0) throw new Error('warnings=' + JSON.stringify(again.warnings));
    return codes(again, 'OUTPUT_NOT_SMALLER');
  });
  await check('encrypt', async () => codes(await pdf.encrypt(TEXT, DIR + '/v2-random.pdf', { userPassword: 'open', permissions: { print: false } }), 'OWNER_PASSWORD_RANDOM'));
  await check('decrypt', async () => codes(await pdf.decrypt(TEXT, DIR + '/v2-plain.pdf'), 'NOT_ENCRYPTED'));
  await check('fillForm', async () => {
    const r = await pdf.fillForm(FORM, DIR + '/v2-ignored.pdf', { 'no.such.field': 'x', name: 'Runtime' }, { ignoreMissing: true });
    if (r.filled !== 1) throw new Error('filled=' + r.filled);
    return codes(r, 'FIELDS_IGNORED');
  });
  // Rhino writes the 10,000 items once (about 760,000 characters); the other runtimes read that file, because the
  // request would exceed their bridges (Node.js: one binder parcel, Bun: MAX_HOST_CALL_REQUEST_BYTES 64 KiB).
  // zh-CN: 由 Rhino 写一次 10,000 项 (约 760,000 个字符); 其他运行时读这个文件, 因为这个请求会超出它们的桥
  // (Node.js: 单个 binder parcel, Bun: MAX_HOST_CALL_REQUEST_BYTES 64 KiB).
  let read = null;
  await check('outline', async () => {
    const big = DIR + '/v2-big.pdf';
    if (AJ6_WRITE_BIG) {
      const source = await pdf.fromText('Outline budget', DIR + '/v2-big-source.pdf');
      const items = [];
      const pad = new Array(31).join('x');
      for (let i = 0; i < 10000; i++) items.push({ title: 'Section ' + ('0000' + i).slice(-5) + ' ' + pad, page: 1, children: [] });
      await pdf.setOutline(source.path, big, items);
    }
    read = await pdf.outline(big);
    if (read.truncated !== true || read.length < 1000 || read.length >= 10000) throw new Error('length=' + read.length + ' truncated=' + read.truncated);
    if (String(read[0].title).indexOf('Section 00000') !== 0) throw new Error('first=' + read[0].title);
    const d = Object.getOwnPropertyDescriptor(read, 'truncated');
    if (!d || d.enumerable || d.writable || Object.keys(read).length !== read.length) throw new Error('descriptor=' + JSON.stringify(d));
    if (JSON.stringify(read).indexOf('truncated') >= 0) throw new Error('JSON.stringify has truncated');
    const small = await pdf.outline(OUTLINE);
    if (small.truncated !== false || small.length !== 4) throw new Error('outline.pdf truncated=' + small.truncated + ' length=' + small.length);
    return 'length=' + read.length + ' truncated=true non-enumerable read-only; outline.pdf truncated=false';
  });
  // Writing the truncated outline back keeps only the front part (Q280); a bridge limit is reported, not failed.
  // zh-CN: 写回截断的大纲只保留前段 (Q280); 超出桥的上限时只报告, 不算失败.
  if (read) {
    try {
      const back = await pdf.setOutline(TEXT, DIR + '/v2-back-' + AJ6_RUNTIME + '.pdf', read);
      const again = await pdf.outline(back.path);
      log('AJ6PDFV2 ' + (again.length === read.length && again.truncated === false ? 'INFO' : 'FAIL') + ' writeBack length=' + again.length + ' truncated=' + again.truncated);
    } catch (e) {
      log('AJ6PDFV2 INFO writeBack not written: ' + (e && e.code ? e.code + ' ' : '') + String(e && e.message !== undefined ? e.message : e).slice(0, 160));
    }
  }
  log('AJ6PDFV2 DONE');
}
"""

RHINO_MAIN = """paperV2(pdf, line => console.log(line)).catch(e => console.error('Error: ' + (e && e.message || e)));
"""

NODE_CJS = """"nodejs";
const pdf = require('pdf');
console.log('AJ6PDFV2 INFO node=' + process.version + ' module=cjs');
paperV2(pdf, line => console.log(line)).catch(e => console.error('Error: ' + (e && e.stack || e)));
"""

NODE_ESM_HEAD = """"nodejs";
import pdf from 'autojs6:pdf';
console.log('AJ6PDFV2 INFO node=' + process.version + ' module=esm');
"""

NODE_ESM_TAIL = """await paperV2(pdf, line => console.log(line)).catch(e => console.error('Error: ' + (e && e.stack || e)));
"""

WORKER_BODY = """const { parentPort } = require('node:worker_threads');
const pdf = require('pdf');
parentPort.postMessage('AJ6PDFV2 INFO node=' + process.version + ' thread=worker');
paperV2(pdf, line => parentPort.postMessage(line)).catch(e => parentPort.postMessage('Error: ' + (e && e.stack || e)));
"""

WORKER_MAIN = """"nodejs";
const { Worker } = require("node:worker_threads");
const worker = new Worker(AJ6_SOURCE, { eval: true });
worker.on("message", line => console.log(String(line)));
worker.on("error", error => { console.error("Error: " + (error && error.stack || error)); process.exitCode = 1; });
"""

BUN_MAIN = """"bun";
const sdk = process.env.AUTOJS6_HOST_CLIENT_MODULE;
if (!sdk) throw new Error("Enable the Bun host capability bridge in AutoJs6 Plugin Center");
const { createHostClient } = await import(sdk);
const host = await createHostClient();
console.log("AJ6PDFV2 INFO bun=" + Bun.version);
await paperV2(host.pdf, line => console.log(line)).catch(e => console.error("Error: " + (e && e.stack || e)));
"""

GRANT_JS = """
var key = org.autojs.autojs.core.pref.keys.CredentialKeys.BUN_HOST_CAPABILITY_GRANT.key('pdf.control');
var before = key.storedOrNull();
if (AJ6_VALUE === 'absent') key.set(null, true); else key.set(java.lang.Boolean.valueOf(AJ6_VALUE === 'true'), true);
var after = key.storedOrNull();
console.log('AJ6PDFV2 grant ' + AJ6_RUN + ' ' + (before === null ? 'absent' : String(before)) + ' ' + (after === null ? 'absent' : String(after)));
"""


def project(main):
    return json.dumps({"name": "aj6-paper-v2", "type": "node", "main": main,
                       "node": {"timeoutMs": 300000, "permissions": ["files", "files.write"]}}, indent=2)


def adb(serial, *args, check=False, timeout=180):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", check=check, timeout=timeout)


def shell(serial, command):
    return adb(serial, "shell", command).stdout


def version_code(serial, package):
    match = re.search(r"versionCode=(\d+)", shell(serial, "dumpsys package " + package))
    return int(match.group(1)) if match else None


def front(serial):
    """Force-stops the host and starts its launcher activity in a cleared task. zh-CN: 结束宿主后在新任务中启动其启动器活动."""
    shell(serial, "am force-stop " + HOST)
    out = shell(serial, "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER " + HOST)
    activity = next((line.strip() for line in out.splitlines() if line.strip().startswith(HOST + "/") and "leakcanary" not in line), None)
    if activity:
        shell(serial, "am start -f 0x10008000 -n " + activity)
        time.sleep(4)


class Console:
    """Collects the GlobalConsole lines of one run until [done] matches. zh-CN: 收集一次运行的 GlobalConsole 行, 直到 [done] 匹配."""

    def __init__(self, serial, done):
        self.lines, self.done, self.finished = [], done, threading.Event()
        adb(serial, "logcat", "-c")
        self.process = subprocess.Popen(["adb", "-s", serial, "logcat", "-v", "raw", "-s", "GlobalConsole:*"],
                                        stdout=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
        threading.Thread(target=self.read, daemon=True).start()

    def read(self):
        for line in self.process.stdout:
            line = PREFIX.sub("", line.rstrip("\r\n"))
            self.lines.append(line)
            if self.done(line):
                self.finished.set()

    def close(self):
        self.process.kill()


def run(serial, files, main, wait, done, scratch):
    """Pushes [files] next to the fixtures, runs [main] and answers the console lines. zh-CN: 推送 [files], 运行 [main], 返回 console 行."""
    pushed = []
    for name, content in files.items():
        local = scratch / name.replace("/", "_")
        local.write_text(content, encoding="utf-8", newline="\n")
        adb(serial, "push", str(local), f"{DEVICE_DIR}/{name}", check=True)
        local.unlink()
        pushed.append(f"{DEVICE_DIR}/{name}")
    front(serial)
    console = Console(serial, done)
    try:
        time.sleep(0.5)
        adb(serial, "shell", "am", "start", "-n", f"{HOST}/{RUN_ACTIVITY}", "-e", "path", f"{DEVICE_DIR}/{main}", check=True)
        console.finished.wait(wait)
    finally:
        console.close()
        shell(serial, "rm -rf " + " ".join(f"'{path}'" for path in pushed))
    return console.lines


def set_grant(serial, value, scratch):
    """Sets the Bun grant pdf.control; answers its previous value. zh-CN: 设置 Bun 授权 pdf.control, 返回原值."""
    token = uuid.uuid4().hex[:8]
    script = "var AJ6_RUN = '%s', AJ6_VALUE = '%s';%s" % (token, value, GRANT_JS)
    pattern = re.compile(r"AJ6PDFV2 grant %s (\S+) (\S+)" % token)
    lines = run(serial, {"grant.js": script}, "grant.js", 30, lambda line: pattern.search(line) is not None, scratch)
    found = next((pattern.search(line) for line in lines if pattern.search(line)), None)
    if not found:
        raise RuntimeError("setting pdf.control: no answer from the host script")
    print(f"INFO bun grant pdf.control: {found.group(1)} -> {found.group(2)}", flush=True)
    return found.group(1)


def collect(runtime, lines):
    """Prints the result lines of one run; answers {label: detail of a PASS} and the failure count."""
    passed, failures, finished = {}, 0, False
    for line in lines:
        if line.startswith(MARK + "INFO"):
            print(f"INFO {runtime} {line[len(MARK) + 5:]}", flush=True)
        elif line.startswith(MARK + "PASS") or line.startswith(MARK + "FAIL"):
            verdict, rest = line[len(MARK):].split(" ", 1)
            label, _, detail = rest.partition(" ")
            if verdict == "PASS":
                passed[label] = re.sub(r" \d+ms$", "", detail)
            failures += verdict == "FAIL"
            print(f"{verdict} {runtime} {rest}", flush=True)
        elif line.startswith(MARK + "DONE"):
            finished = True
    if not finished:
        errors = [line for line in lines if "Error" in line][:3]
        print(f"FAIL {runtime} did not finish: {' | '.join(errors or lines[-3:])[:600]}", flush=True)
        failures += 1
    return passed, failures


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--runtimes", default=",".join(RUNTIMES))
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()
    serial = args.serial
    sdk = int(shell(serial, "getprop ro.build.version.sdk").strip() or 0)
    versions = {name: version_code(serial, package) for name, package in (("host", HOST), ("plugin", PLUGIN), ("node", NODE), ("bun", BUN))}
    print(f"INFO device={shell(serial, 'getprop ro.product.model').strip()} sdk={sdk} " + " ".join(f"{k}={v}" for k, v in versions.items()), flush=True)
    scratch = REPO / "build" / "device-runtime-v2" / re.sub(r"[^A-Za-z0-9._-]", "_", serial)
    scratch.mkdir(parents=True, exist_ok=True)
    shell(serial, f"rm -rf '{DEVICE_DIR}'; mkdir -p '{DEVICE_DIR}'")
    for name in FIXTURES:
        adb(serial, "push", str(REPO / "samples" / "fixtures" / name), f"{DEVICE_DIR}/{name}", check=True)
    shared = CHECKS_JS.replace("AJ6_DIR", json.dumps(DEVICE_DIR))
    done = lambda line: line.startswith(MARK + "DONE") or line.startswith("Error")
    results, failures = {}, 0
    try:
        for runtime in [r.strip() for r in args.runtimes.split(",") if r.strip()]:
            # Only Rhino writes the large outline; the others read its file (see the outline check).
            # zh-CN: 只有 Rhino 写大目录, 其他运行时读它的文件 (见大纲检查).
            checks = shared.replace("AJ6_WRITE_BIG", "true" if runtime == "rhino" else "false").replace("AJ6_RUNTIME", json.dumps(runtime))
            if runtime.startswith("node") and versions["node"] is None:
                print(f"SKIP {runtime} Node.js Runtime not installed", flush=True)
                continue
            if runtime == "bun" and (versions["bun"] is None or sdk < 33):
                print(f"SKIP bun {'Bun Runtime not installed' if sdk >= 33 else f'API {sdk} < 33'}", flush=True)
                continue
            if runtime == "rhino":
                files, entry = {"v2-rhino.js": checks + RHINO_MAIN}, "v2-rhino.js"
            elif runtime == "node-cjs":
                files, entry = {"project.json": project("main.cjs"), "main.cjs": NODE_CJS + checks}, "main.cjs"
            elif runtime == "node-esm":
                files, entry = {"project.json": project("main.mjs"), "main.mjs": NODE_ESM_HEAD + checks + NODE_ESM_TAIL}, "main.mjs"
            elif runtime == "node-worker":
                files, entry = {"project.json": project("main.cjs"), "main.cjs": WORKER_MAIN.replace("AJ6_SOURCE", json.dumps(checks + WORKER_BODY))}, "main.cjs"
            elif runtime == "bun":
                files, entry = {"v2.bun.js": BUN_MAIN + checks}, "v2.bun.js"
            else:
                print(f"FAIL {runtime} unknown runtime", flush=True)
                failures += 1
                continue
            before = set_grant(serial, "true", scratch) if runtime == "bun" else None
            try:
                passed, failed = collect(runtime, run(serial, files, entry, args.timeout, done, scratch))
            finally:
                if before is not None:
                    set_grant(serial, before, scratch)
            results[runtime] = passed
            failures += failed
        labels = sorted({label for passed in results.values() for label in passed})
        for label in labels:
            values = {runtime: passed.get(label) for runtime, passed in results.items()}
            same = len(set(values.values())) == 1 and None not in values.values()
            failures += not same
            print(f"{'PASS' if same else 'FAIL'} same {label} in {','.join(values)}" + ("" if same else f": {values}"), flush=True)
    finally:
        shell(serial, f"rm -rf '{DEVICE_DIR}'")
        for leftover in scratch.glob("*"):
            leftover.unlink()
        scratch.rmdir()
    print("PASS run" if failures == 0 else f"FAIL run ({failures})", flush=True)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
