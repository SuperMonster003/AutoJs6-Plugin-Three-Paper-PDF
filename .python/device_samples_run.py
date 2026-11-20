"""Runs the samples of samples/ on a device and checks their results (3-Paper PDF roadmap P5.3).

Usage: py -3.12 -I .python/device_samples_run.py -s SERIAL [--only NAME,...] [--view] [--no-demo] [--keep] [--old-plugin]
       [--timeout SECONDS]

1. The host (with the pdf module, 6.8.1 or later) and 3-Paper PDF must already be installed, enabled and allowed to
   read and write shared storage; the Node sample also needs Node.js Runtime 2.2.0 or later, the Bun sample Bun
   Runtime 0.23.0 or later (API 33 or later). A runtime that is not installed is reported as SKIP. The tool installs
   nothing and answers no dialog.
2. samples/fixtures/*.pdf go to /sdcard/Documents/3-Paper PDF/fixtures/, the samples to .../runner/. Every Rhino sample
   runs through a small wrapper that passes it an output file (engines.execScriptFile arguments) and its JSON summary
   is checked; the Node project and the Bun script print "summary {...}" as their last console line, read from the
   GlobalConsole logcat. Before each run the host is brought to the foreground (force-stop, then its launcher activity);
   a run that prints nothing within 45 seconds is started once more.
3. The Rhino samples that make a demo document when no fixture is found run a second time without the fixtures
   (the "demo" pass, as in the host's sample directory); --no-demo skips it. pdf-view.js only runs with --view (it
   opens the viewer window and closes it after 6 seconds).
4. For the Bun sample, pdf.control is granted for the run through a host script and restored to its previous value
   afterwards.
5. The pushed fixtures, samples and every output (.../samples/, which the samples write by default) are removed
   afterwards unless --keep is given; /sdcard/Documents/3-Paper PDF/ itself is removed only when it is empty and did
   not exist before. A .../samples/ that existed before is left alone and the run stops.
6. The samples of 3-Paper PDF 1.1.0 (forms, watermark, searchable, compress, properties; roadmap P6.14) and the fifth
   part of the Node project and the Bun script need plugin 1.1.0 or later. With --old-plugin (a 1.0.x plugin is
   installed) they must stop with the UNSUPPORTED hint instead; without it an UNSUPPORTED answer fails. When no OCR
   plugin is installed, the searchable sample must stop with the OCR_UNAVAILABLE hint, which counts as a pass.
Every check prints one line "PASS|FAIL|SKIP <name> <key values>"; the exit code is 1 when a check failed.

zh-CN: 在设备上运行 samples/ 中的样例并检查结果 (3-Paper PDF 路线图 P5.3). 1. 宿主 (带 pdf 模块, 6.8.1 或以上) 与 3-Paper PDF 须已安装, 启用
并有共享存储读写权限; Node 样例另需 Node.js Runtime 2.2.0 或以上, Bun 样例另需 Bun Runtime 0.23.0 或以上 (API 33 或以上), 未安装的运行时报
SKIP. 本工具不安装任何东西, 也不应答任何对话框. 2. samples/fixtures/*.pdf 推到 /sdcard/Documents/3-Paper PDF/fixtures/, 样例推到 .../runner/.
每个 Rhino 样例经一个小包装脚本运行, 包装脚本传给它输出文件 (engines.execScriptFile 的 arguments), 然后检查其 JSON 摘要; Node 项目与 Bun 脚本在
最后一行控制台输出 "summary {...}", 从 GlobalConsole logcat 读取. 每次运行前先把宿主切到前台 (force-stop 后启动其启动器活动), 45 秒内没有任何输出时再启动一次. 3. 找不到样例
文档时会现场生成演示文档的 Rhino 样例, 去掉样例文档再运行一遍 ("demo" 轮, 与宿主样例目录中的情形相同); --no-demo 跳过这一轮. pdf-view.js
只在给出 --view 时运行 (会打开查看器窗口, 6 秒后关闭). 4. Bun 样例运行期间经宿主脚本授予 pdf.control, 结束后恢复原值. 5. 结束后删除推送的样例
文档, 样例与全部产物 (样例默认写到 .../samples/), 给出 --keep 时保留; /sdcard/Documents/3-Paper PDF/ 本身只在为空且原先不存在时删除. 原先就有
.../samples/ 时不动它并停止运行. 6. 3-Paper PDF 1.1.0 的样例 (forms, watermark, searchable, compress, properties; 路线图 P6.14) 以及
Node 项目与 Bun 脚本的第 5 段需要 1.1.0 或以上的插件. 给出 --old-plugin (装的是 1.0.x 插件) 时它们必须给出 UNSUPPORTED 提示后停止; 不给时
UNSUPPORTED 算失败. 没有安装 OCR 插件时, searchable 样例必须给出 OCR_UNAVAILABLE 提示后停止, 这种情形算通过. 每项检查输出一行 "PASS|FAIL|SKIP <名称> <关键数值>"; 有检查失败时退出码为 1.
"""
import argparse
import json
import re
import shlex
import subprocess
import sys
import time
from pathlib import Path

HOST = "org.autojs.autojs6"
RUN_ACTIVITY = f"{HOST}/org.autojs.autojs.external.open.RunIntentActivity"
PLUGIN = "io.github.supermonster003.autojs6.plugin.three.paper.pdf"
NODE = "io.github.supermonster003.autojs6.plugin.nodejs"
BUN = "io.github.supermonster003.autojs6.plugin.bun.runtime"
BASE = "/sdcard/Documents/3-Paper PDF"
FIXTURES = f"{BASE}/fixtures"
RUNNER = f"{BASE}/runner"
OUTPUTS = f"{BASE}/samples"
REPO = Path(__file__).resolve().parents[1]
RHINO = ("read", "render", "pages", "generate", "encrypt", "ocr-manual", "forms", "watermark", "searchable", "compress",
         "properties", "view")
# The samples that need 3-Paper PDF 1.1.0 (roadmap P6.14). zh-CN: 需要 3-Paper PDF 1.1.0 的样例 (路线图 P6.14).
V110 = ("forms", "watermark", "searchable", "compress", "properties")
NO_DEMO = ()


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


def push_text(serial, text, remote, scratch):
    scratch.write_text(text, encoding="utf-8", newline="\n")
    adb(serial, "push", str(scratch), remote)
    scratch.unlink()


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
        if f"{HOST}/org.autojs.autojs.ui.main.MainActivity" in shell(serial, "dumpsys activity activities | grep topResumedActivity"):
            break
        time.sleep(1)
    time.sleep(3)


def run_script(serial, path):
    out = shell(serial, f"am start -n {RUN_ACTIVITY} -e path {quote(path)}")
    if "Error" in out:
        raise RuntimeError(out[:300])


def started(serial, output, timeout=45):
    """Answers whether the sample printed anything or wrote its output in time. zh-CN: 样例是否按时开始输出."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if console_lines(serial) or console_lines(serial, "E") or (output and exists(serial, output)):
            return True
        time.sleep(3)
    return False


def launch(serial, path, output=None):
    """Brings the host to the front and runs path; retries once when nothing starts. zh-CN: 切前台后运行, 没有开始时重试一次."""
    for attempt in (1, 2):
        adb(serial, "logcat", "-c", check=False)
        front(serial)
        run_script(serial, path)
        if started(serial, output):
            return
        print(f"INFO {path.rsplit('/', 1)[-1]} printed nothing within 45 s (attempt {attempt})", flush=True)


def wait_file(serial, path, timeout):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if exists(serial, path):
            time.sleep(1)
            return shell(serial, f"cat {quote(path)}")
        time.sleep(2)
    return None


def console_lines(serial, level="D"):
    # console.log of the Node.js and Bun runtimes arrives at level D. zh-CN: Node.js 与 Bun 运行时的 console.log 是 D 级.
    lines = shell(serial, f"logcat -d -v raw -s GlobalConsole:{level}", timeout=60).splitlines()
    return [re.sub(r"^\d\d:\d\d:\d\d\.\d{3}/[A-Z]: ", "", line) for line in lines if line.strip()]


def console_errors(serial):
    return [line[:200] for line in console_lines(serial, "E")][-6:]


class Checks:
    def __init__(self):
        self.failed = 0

    def report(self, verdict, name, detail=""):
        if verdict == "FAIL":
            self.failed += 1
        print(f"{verdict} {name} {detail}".rstrip(), flush=True)

    def judge(self, name, summary, conditions, detail):
        if summary is None:
            self.report("FAIL", name, "no summary within the timeout")
            return
        errors = summary.get("errors") or []
        broken = [label for label, ok in conditions if not ok]
        if errors or broken:
            self.report("FAIL", name, f"errors={errors} failed={broken} {detail}")
        else:
            self.report("PASS", name, detail)


def rhino(serial, name, args, scratch, extra=None):
    """Runs samples/pdf-<name>.js through a wrapper; answers its summary or None. zh-CN: 经包装脚本运行样例, 返回摘要或 None."""
    sample = f"{RUNNER}/pdf-{name}.js"
    output = f"{RUNNER}/summary-{name}.json"
    arguments = {"output": output, **(extra or {})}
    wrapper = f"engines.execScriptFile({json.dumps(sample)}, {{ arguments: {json.dumps(arguments)} }});\n"
    push_text(serial, wrapper, f"{RUNNER}/run-{name}.js", scratch / f"run-{name}.js")
    shell(serial, f"rm -f {quote(output)}")
    launch(serial, f"{RUNNER}/run-{name}.js", output)
    text = wait_file(serial, output, args.timeout)
    if text is None:
        for line in console_errors(serial):
            print(f"INFO console {line}")
        return None
    return json.loads(text)


def check_rhino(serial, checks, name, demo, args, scratch):
    label = f"{name}{'.demo' if demo else ''}"
    if name == "view":
        s = rhino(serial, name, args, scratch, {"autoCloseMs": 6000})
        state = (s or {}).get("state") or {}
        checks.judge(label, s, [("reason", (s or {}).get("reason") == "finished"),
                                ("goTo", state.get("page") == state.get("pageCount") and bool(state.get("pageCount")))],
                     f"reason={(s or {}).get('reason')} pages={(s or {}).get('pages')} state={state}")
        return
    s = rhino(serial, name, args, scratch)
    if s is None:
        checks.judge(label, None, [], "")
        return
    if name in V110:
        unsupported = str(s.get("unsupported") or "")
        if args.old_plugin:
            checks.judge(f"{label}.unsupported", s, [("UNSUPPORTED hint", bool(unsupported))], f"unsupported={unsupported[:120]!r}")
            return
        if unsupported:
            checks.report("FAIL", label, f"UNSUPPORTED {unsupported[:120]!r} (a 1.0.x plugin? use --old-plugin)")
            return
    if name == "read":
        info, enc = s.get("info") or {}, s.get("encrypted") or {}
        perms = enc.get("permissions") or {}
        conditions = [
            ("pageCount", info.get("pageCount") == (2 if demo else 6)),
            ("outline", (s.get("outline") or 0) >= 2),
            ("search", s.get("search") == ([1, 2] if demo else [2, 5])),
            ("PASSWORD_REQUIRED", enc.get("withoutPassword") == "PASSWORD_REQUIRED"),
            ("WRONG_PASSWORD", enc.get("wrongPassword") == "WRONG_PASSWORD"),
            ("permissions", perms.get("copy") is False),
        ]
        detail = (f"pageCount={info.get('pageCount')} outline={s.get('outline')} search={s.get('search')} "
                  f"locked={enc.get('withoutPassword')}/{enc.get('wrongPassword')} copy={perms.get('copy')}")
    elif name == "render":
        conditions = [("render", bool((s.get("render") or {}).get("size"))),
                      ("renderToFile", str((s.get("renderToFile") or {}).get("size", "")).startswith("800x")),
                      ("images", len(s.get("images") or []) == (1 if demo else 2)),
                      ("extracted", len(s.get("extracted") or []) == len(s.get("images") or []))]
        detail = (f"render={(s.get('render') or {}).get('size')} jpeg={(s.get('renderToFile') or {}).get('size')} "
                  f"images={s.get('images')} extracted={s.get('extracted')}")
    elif name == "pages":
        edit, split, merge = s.get("edit") or {}, s.get("split") or {}, s.get("merge") or {}
        conditions = [
            ("merge", merge.get("pageCount") == 7 and (merge.get("outline") or [None])[0] == "Cover" and len(merge.get("outline") or []) == 2),
            ("split", split.get("every") == 3 and split.get("ranges") == ["part-1.pdf", "part-2.pdf"]),
            ("edit", edit.get("extract") == 2 and edit.get("delete") == 4 and edit.get("reorderFirst") == ("Page 6 of 6" if demo else "Chapter 3, section 1")
             and edit.get("rotation") == 90),
            ("save", (s.get("save") or {}).get("pageCount") == 3),
            ("mergeAsync", (s.get("mergeAsync") or {}).get("pageCount") == 13 and bool((s.get("mergeAsync") or {}).get("stages"))),
            ("cancel", s.get("cancel") in ("CANCELLED", "finished")),
        ]
        detail = (f"merge={merge.get('pageCount')} outline={merge.get('outline')} split={split.get('every')}/{split.get('ranges')} "
                  f"edit={edit} save={(s.get('save') or {}).get('pageCount')} async={s.get('mergeAsync')} cancel={s.get('cancel')}")
    elif name == "generate":
        images, text, html = s.get("fromImages") or {}, s.get("fromText") or {}, s.get("fromHtml") or {}
        conditions = [("fromImages", images.get("pageCount") == 2 and bool(images.get("fitPage"))),
                      ("fromText", (text.get("pageCount") or 0) >= 2 and text.get("title") == "Meeting notes"),
                      ("fromHtml", html.get("scriptRan") is True and html.get("scriptSkipped") is True and html.get("filePages") == 1),
                      ("remote blocked", (html.get("fileWarnings") or 0) >= 1)]
        detail = f"fromImages={images} fromText={text} fromHtml={html}"
    elif name == "encrypt":
        opened, dec = s.get("open") or {}, s.get("decrypt") or {}
        perms, only = opened.get("permissions") or {}, (s.get("encrypt") or {}).get("printOnly") or {}
        conditions = [("open", opened.get("withoutPassword") == "PASSWORD_REQUIRED" and opened.get("wrongPassword") == "WRONG_PASSWORD"),
                      ("permissions", perms.get("copy") is False and perms.get("modify") is False and perms.get("print") is True),
                      ("printOnly", only.get("print") is True and only.get("copy") is False),
                      ("decrypt", dec.get("withUserPassword") == "PERMISSION_DENIED" and dec.get("encryptedAfter") is False)]
        detail = (f"open={opened.get('withoutPassword')}/{opened.get('wrongPassword')} copy={perms.get('copy')} "
                  f"printOnly.copy={only.get('copy')} decrypt={dec.get('withUserPassword')}/encrypted={dec.get('encryptedAfter')}")
    elif name == "forms":
        forms, fill, recover, flat = (s.get(key) or {} for key in ("forms", "fill", "recover", "flatten"))
        conditions = [
            ("forms", forms.get("count") == (2 if demo else 11)),
            ("fillForm", fill.get("filled") == (2 if demo else 9) and fill.get("matched") == fill.get("filled")),
            ("recover", recover.get("strict") == "INVALID_ARGUMENT" and recover.get("ignoreMissing") == ["FIELDS_IGNORED"]
             and recover.get("restored") == "Ada" and recover.get("defaultValue") == "Ada" and recover.get("wrongType") == "INVALID_ARGUMENT"),
            ("flatten", flat.get("fields") == 0 and flat.get("formFieldCount") == 0 and flat.get("hits") == ([1] if demo else [1, 2])),
        ]
        detail = (f"forms={forms.get('count')} filled={fill.get('filled')}/{fill.get('matched')} warningCodes={fill.get('warningCodes')} "
                  f"recover={recover} flatten={flat}")
    elif name == "watermark":
        pages = 3 if demo else 6
        numbers, stamp = s.get("pageNumbers") or {}, s.get("stamp") or {}
        conditions = [(key, (s.get(key) or {}).get("pageCount") == pages) for key in ("watermark", "tile", "image")]
        conditions += [("pageNumbers", numbers.get("pageCount") == pages and numbers.get("found") is True),
                       ("stamp", stamp.get("items") == 2 and stamp.get("secondPage") is True)]
        detail = (f"pages={[(s.get(key) or {}).get('pageCount') for key in ('watermark', 'tile', 'image')]} pageNumbers={numbers} "
                  f"stamp={stamp}")
    elif name == "searchable":
        if s.get("ocrUnavailable"):
            conditions = [("OCR_UNAVAILABLE hint", "makeSearchable" not in s)]
            detail = f"no OCR engine: {str(s.get('ocrUnavailable'))[:120]!r}"
        else:
            made, read, again, layer = (s.get(key) or {} for key in ("makeSearchable", "read", "again", "addTextLayer"))
            conditions = [
                ("makeSearchable", made.get("ocrPages") == 1 and made.get("layerPages") == 1 and (made.get("lines") or 0) >= 1),
                ("read", read.get("hits") == [1]),
                ("again", again.get("ocrPages") == 0 and again.get("skippedPages") == 1),
                ("addTextLayer", layer.get("layerPages") == 1 and 1 <= (layer.get("lines") or 0) <= (layer.get("detected") or 0)),
            ]
            detail = (f"makeSearchable={made.get('ocrPages')}/{made.get('layerPages')}/{made.get('lines')} lines {made.get('ms')}ms "
                      f"read={read} again={again} addTextLayer={layer}")
    elif name == "compress":
        low, high, custom = (s.get(key) or {} for key in ("low", "high", "custom"))
        conditions = [("low", low.get("kept") is True and (low.get("sizeBytes") or 0) < (low.get("sourceBytes") or 0)),
                      ("high", bool(high.get("sourceBytes"))),
                      ("custom", custom.get("kept") is True)]
        detail = "; ".join(f"{key}={v.get('sourceBytes')}->{v.get('sizeBytes')} kept={v.get('kept')} images={v.get('images')}"
                           for key, v in (("low", low), ("high", high), ("custom", custom)))
    elif name == "properties":
        info, outline = s.get("setInfo") or {}, s.get("setOutline") or {}
        conditions = [("setInfo", info.get("title") == "3-Paper PDF 样例" and info.get("author") == "AutoJs6" and info.get("creator") is None),
                      ("setOutline", [outline.get("before"), outline.get("after")] == ([0, 2] if demo else [4, 5])
                       and outline.get("last") == ("正文" if demo else "附录 (样例添加)")),
                      ("cleared", s.get("cleared") == 0)]
        detail = f"setInfo={info} setOutline={outline} cleared={s.get('cleared')}"
    else:  # ocr-manual
        manual, auto = s.get("manual") or {}, s.get("auto") or {}
        if "code" in auto:
            conditions = [("ocr unavailable", auto.get("code") == "OCR_UNAVAILABLE" and "code" in manual)]
            detail = f"pdf.text code={auto.get('code')} manual code={manual.get('code')}"
        else:
            conditions = [("manual", bool(manual.get("lines"))), ("auto", auto.get("sources") == ["ocr"] and bool(str(auto.get("text") or "").strip()))]
            detail = (f"manual size={manual.get('size')} lines={len(manual.get('lines') or [])} blocks={manual.get('blocks')} "
                      f"{manual.get('ms')}ms; pdf.text sources={auto.get('sources')} {auto.get('ms')}ms")
        s = {**s, "errors": []}
    checks.judge(label, s, conditions, detail)


def console_summary(serial, timeout):
    deadline = time.time() + timeout
    while time.time() < deadline:
        for line in reversed(console_lines(serial)):
            if line.startswith("summary {"):
                return json.loads(line[len("summary "):])
        time.sleep(3)
    return None


def check_node(serial, checks, args, scratch):
    if version(serial, NODE) is None:
        checks.report("SKIP", "node", "Node.js Runtime not installed")
        return
    directory = f"{RUNNER}/pdf-node"
    shell(serial, f"mkdir -p {quote(directory)}")
    for name in ("project.json", "main.js"):
        adb(serial, "push", str(REPO / "samples" / "node" / "pdf-node" / name), f"{directory}/{name}")
    launch(serial, f"{directory}/main.js")
    s = console_summary(serial, args.timeout)
    if s is None:
        for line in console_errors(serial):
            print(f"INFO console {line}")
    text, merge, v110 = (s or {}).get("text") or {}, (s or {}).get("merge") or {}, (s or {}).get("v110") or {}
    checks.judge("node", s, [("text", text.get("pages") == [1, 2] and text.get("sources") == ["text", "text"]),
                             ("render", bool((s or {}).get("render"))),
                             ("merge", merge.get("pageCount") == 12 and (merge.get("outline") or [None])[0] == "First copy"),
                             ("cancel", (s or {}).get("cancel") in ("CANCELLED", "finished")),
                             *v110_conditions(args, v110, [("stamp", v110.get("stamp") == 6),
                                                           ("setInfo", v110.get("title") == "Node.js sample"),
                                                           ("compress", isinstance((v110.get("compress") or {}).get("warningCodes"), list)),
                                                           ("forms", v110.get("fields") == 0 and v110.get("filled") == 2)])],
                 f"text={text} render={(s or {}).get('render')} merge={merge} cancel={(s or {}).get('cancel')} v110={v110}")


def v110_conditions(args, v110, conditions):
    """The checks of the fifth part (3-Paper PDF 1.1.0) of the Node project and the Bun script. zh-CN: 第 5 段的检查."""
    if args.old_plugin:
        return [("v110 UNSUPPORTED hint", bool(v110.get("unsupported")))]
    return [("v110 supported", not v110.get("unsupported")), *conditions]


GRANT_JS = """
var OUT = @@OUT@@, VALUE = @@VALUE@@;
var r = {};
try {
    var key = org.autojs.autojs.core.pref.keys.CredentialKeys.BUN_HOST_CAPABILITY_GRANT.key('pdf.control');
    var before = key.storedOrNull();
    r.before = before === null ? 'absent' : String(before);
    if (VALUE === 'absent') key.set(null, true); else key.set(java.lang.Boolean.valueOf(VALUE === 'true'), true);
    r.after = key.storedOrNull() === null ? 'absent' : String(key.storedOrNull());
} catch (e) {
    r.error = String(e.message || e).slice(0, 200);
}
files.write(OUT, JSON.stringify(r));
"""


def set_pdf_grant(serial, value, scratch):
    """Sets the Bun grant pdf.control ("true" / "false" / "absent"); answers its value before. zh-CN: 设置 Bun 授权并返回原值."""
    out = f"{RUNNER}/grant.json"
    script = GRANT_JS.replace("@@OUT@@", json.dumps(out)).replace("@@VALUE@@", json.dumps(value))
    push_text(serial, script, f"{RUNNER}/grant.js", scratch / "grant.js")
    shell(serial, f"rm -f {quote(out)}")
    launch(serial, f"{RUNNER}/grant.js", out)
    text = wait_file(serial, out, 60)
    result = json.loads(text) if text else {"error": "no answer"}
    if "error" in result:
        raise RuntimeError(f"setting pdf.control failed: {result['error']}")
    return result["before"]


def check_bun(serial, checks, args, scratch, sdk):
    if version(serial, BUN) is None or sdk < 33:
        checks.report("SKIP", "bun", "Bun Runtime not installed" if sdk >= 33 else f"API {sdk} < 33")
        return
    adb(serial, "push", str(REPO / "samples" / "pdf.bun.js"), f"{RUNNER}/pdf.bun.js")
    before = set_pdf_grant(serial, "true", scratch)
    try:
        launch(serial, f"{RUNNER}/pdf.bun.js")
        s = console_summary(serial, args.timeout)
        if s is None:
            for line in console_errors(serial):
                print(f"INFO console {line}")
    finally:
        set_pdf_grant(serial, before, scratch)
    text, v110 = (s or {}).get("text") or {}, (s or {}).get("v110") or {}
    checks.judge("bun", s, [("text", text.get("pages") == [1] and text.get("hits") == [2, 5]),
                            ("render", str((s or {}).get("render", "")).startswith("480x")),
                            ("extract", (s or {}).get("extract") == 1),
                            ("timeout", str((s or {}).get("timeout", "")) in ("finished", "CANCELLED (TimeoutError)")),
                            *v110_conditions(args, v110, [("stamp", v110.get("stamp") == 6),
                                                          ("setOutline", v110.get("outline") == ["Start", "End"]),
                                                          ("compress", isinstance((v110.get("compress") or {}).get("warningCodes"), list)),
                                                          ("formFieldCount", v110.get("formFieldCount") == 0),
                                                          ("forms", v110.get("fields") == 11 and v110.get("filled") == 1)])],
                 f"text={text} render={(s or {}).get('render')} extract={(s or {}).get('extract')} timeout={(s or {}).get('timeout')} "
                 f"v110={v110} grant restored to {before}")


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--only", help="comma-separated names: " + ", ".join(RHINO) + ", node, bun")
    parser.add_argument("--view", action="store_true", help="also run pdf-view.js (opens the viewer window)")
    parser.add_argument("--no-demo", action="store_true", help="skip the pass without fixtures")
    parser.add_argument("--keep", action="store_true", help="keep the pushed files and every output")
    parser.add_argument("--old-plugin", action="store_true",
                        help="a 1.0.x plugin is installed: the 1.1.0 samples must stop with the UNSUPPORTED hint")
    parser.add_argument("--timeout", type=int, default=300, help="seconds per sample")
    args = parser.parse_args()
    serial = args.serial
    wanted = set(args.only.split(",")) if args.only else set(RHINO) | {"node", "bun"}
    if not args.view and not args.only:
        wanted.discard("view")
    scratch = REPO / "build" / "device-samples" / re.sub(r"[^A-Za-z0-9._-]", "_", serial)
    scratch.mkdir(parents=True, exist_ok=True)

    sdk = int(shell(serial, "getprop ro.build.version.sdk") or 0)
    host, plugin = version(serial, HOST), version(serial, PLUGIN)
    print(f"INFO device={shell(serial, 'getprop ro.product.model')} sdk={sdk} host={host} plugin={plugin} "
          f"node={version(serial, NODE)} bun={version(serial, BUN)}")
    if host is None or plugin is None:
        print("FAIL host or plugin not installed")
        return 1
    if exists(serial, OUTPUTS):
        print(f"FAIL {OUTPUTS} exists already; move it away first")
        return 1
    base_existed = exists(serial, BASE)
    shell(serial, f"rm -rf {quote(FIXTURES)} {quote(RUNNER)}; mkdir -p {quote(FIXTURES)} {quote(RUNNER)}")
    for fixture in sorted((REPO / "samples" / "fixtures").glob("*.pdf")):
        adb(serial, "push", str(fixture), f"{FIXTURES}/{fixture.name}")
    for name in RHINO:
        if name in wanted:
            adb(serial, "push", str(REPO / "samples" / f"pdf-{name}.js"), f"{RUNNER}/pdf-{name}.js")
    checks = Checks()
    try:
        for name in RHINO:
            if name in wanted:
                check_rhino(serial, checks, name, False, args, scratch)
        if "node" in wanted:
            check_node(serial, checks, args, scratch)
        if "bun" in wanted:
            check_bun(serial, checks, args, scratch, sdk)
        if not args.no_demo:
            # The host's sample directory has no fixtures: the samples make their own demo documents.
            # zh-CN: 宿主样例目录里没有样例文档: 样例自己生成演示文档.
            shell(serial, f"rm -rf {quote(FIXTURES)} {quote(OUTPUTS)}")
            for name in RHINO:
                if name in wanted and name not in NO_DEMO:
                    check_rhino(serial, checks, name, True, args, scratch)
    finally:
        if not args.keep:
            shell(serial, f"rm -rf {quote(FIXTURES)} {quote(RUNNER)} {quote(OUTPUTS)}")
            if not base_existed:
                shell(serial, f"rmdir {quote(BASE)} 2>/dev/null")
        for leftover in scratch.glob("*"):
            leftover.unlink()
        scratch.rmdir()
    print("PASS run" if checks.failed == 0 else f"FAIL run ({checks.failed} failed)")
    return 0 if checks.failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
