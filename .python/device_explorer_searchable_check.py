"""Device check of the file manager of AutoJs6 with 3-Paper PDF: the host command "Make searchable PDF" (roadmap D67,
P6.11, the password field of appendix D Q231 (b)) and the actions "Compress PDF" / "Add watermark to PDF" (P6.12 row 2).

Usage: py -3.12 -I .python/device_explorer_searchable_check.py -s SERIAL --host-repo HOST_CHECKOUT
           [--host org.autojs.autojs6] [--values values] [--rtl-values values-ar] [--only STEP,...]
           [--uninstall-ocr PACKAGE,...] [--shots DIR] [--keep]

Preconditions: a debug host built from HOST_CHECKOUT (the file manager helpers and the host strings are read from its
tools/explorer and values directories; a debug build because run-as reads its data) and 3-Paper PDF are installed,
enabled and allowed to manage all files; an OCR plugin is installed and enabled; the app language is the one of
--values (values is English); no system dialog is pending. The tool installs nothing and answers no dialog: it only
taps the views of the host and of 3-Paper PDF, found with read-only uiautomator dumps (on an emulator it grants the
notification permission of the host with `pm grant` first, so the first-run prompt does not cover the file manager).

Setup: samples/fixtures/scanned-multi.pdf (three scanned pages, no text layer) goes to <working directory>/p611check/
as scan.pdf with note.txt and the folder folder/; a host script makes open.pdf (open password, which is also the owner
password), owner.pdf (owner password only, changes not allowed), both.pdf (open and owner password, changes not
allowed) and big.pdf (300 pages with an open password). The passwords are random for each run, reach the device in
a file the script removes at once and are never printed.

Steps (--only takes their names; each prints "PASS|FAIL|SKIP <name> <key values>"):
  menu                the "3-Paper PDF" menu of scan.pdf lists "Make searchable PDF", "Compress PDF" and "Add
                      watermark to PDF" in the order of Q266; note.txt and folder/ do not offer the command (P6.11 a);
  form                the form proposes scan-searchable.pdf, shows no password field, "Advanced" names the engine
                      ("Automatic (<engine>)") (P6.11 b, Q231 (1));
  run                 "Create" starts at once, the progress names the recognized pages, the result shows 3 recognized
                      pages, the output is in the folder and "Open" opens the viewer of 3-Paper PDF (P6.11 c);
  again               the form now proposes "scan-searchable (1).pdf"; closing it creates nothing (P6.11 b);
  cancel              "Cancel" and the back key during a run leave no output and no partial file (P6.11 e);
  actions             "Compress PDF" and "Add watermark to PDF" open 3-Paper PDF and write scan_compressed.pdf and
                      scan_watermarked.pdf next to scan.pdf (P6.12 row 2);
  open-password       open.pdf: the note and "PDF password" show within 3 s; empty and wrong passwords keep the form
                      open with their message; the right one creates open-searchable.pdf (Q231 (2));
  owner-password      owner.pdf: the "Permissions password" field shows (Q231 (3));
  both-passwords      both.pdf: the open password alone is refused as not allowing a copy, the owner password creates
                      both-searchable.pdf (Q231 (4));
  password-cancel     open.pdf with its password, cancelled during the run: no output, no partial file, and the form
                      opened again has an empty password field (Q231 (6));
  text                pdf.info / pdf.text of a host script: the outputs are not encrypted and give the recognized text
                      back (P6.11 d, Q231 (2));
  early-create        big.pdf with 3-Paper PDF stopped: "Create" tapped as soon as the form shows waits for the check
                      and then focuses the password field (Q231 (5); SKIP when the check ended before the tap);
  close-during-check  big.pdf: the form closed with the back key while the check runs; the host keeps running
                      (Q231 (8));
  secrets             none of the passwords is in logcat, in the shared preferences, databases and files of the host
                      and of 3-Paper PDF, or in a file name of the folder (Q231 (7));
  rtl                 the app language set to that of --rtl-values: the note is translated, the show-password button
                      sits at the left end of the field and switches the field to plain text, the "Advanced" arrow
                      points down while folded (Q231 (9)); the language setting of the host is restored afterwards;
  no-engine           only with --uninstall-ocr: the listed OCR plugins are uninstalled (not installed again), then the
                      command shows the guide to Plugin Center (P6.11 f). Run it last.
Removed at the end unless --keep: p611check, the runner folder and the dump files on the device. Exit code 1 when a
check failed.

zh-CN: AutoJs6 文件管理器与 3-Paper PDF 的设备检查: 宿主命令 "生成可搜索 PDF" (路线图 D67, P6.11, 附录 D Q231 (b) 的密码框) 与 "压缩 PDF" /
"为 PDF 添加水印" 两个动作 (P6.12 第 2 行). 前提: 已安装由 HOST_CHECKOUT 构建的 debug 宿主 (文件管理器辅助函数与宿主字符串从其 tools/explorer
与 values 目录读取; 用 debug 构建是因为要用 run-as 读取其数据) 与 3-Paper PDF, 均已启用并有所有文件访问权限; 已安装并启用一个 OCR 插件; 应用语言与
--values 相同 (values 为英语); 没有待处理的系统对话框. 本工具不安装任何东西, 也不应答任何对话框, 只点按宿主与 3-Paper PDF 自己的视图 (用只读的
uiautomator 转储定位; 模拟器上会先用 `pm grant` 授予宿主通知权限, 免得首次运行的询问挡住文件管理器). 准备: samples/fixtures/scanned-multi.pdf (三页扫描件, 无文字层) 以 scan.pdf 之名放到 <工作目录>/p611check/, 另有 note.txt
与文件夹 folder/; 宿主脚本生成 open.pdf (打开密码, 同时也是所有者密码), owner.pdf (只有所有者密码, 不允许修改), both.pdf (打开密码与所有者密码,
不允许修改) 与 big.pdf (300 页, 有打开密码). 密码每次运行随机生成, 经一个由脚本立即删除的文件传到设备, 从不输出. 各步骤 (--only 取其名称, 每项输出
"PASS|FAIL|SKIP <名称> <关键数值>") 见上方英文说明, 与 P6.11 的检查点 (a) - (f) 和 Q231 (b) 的检查点 (1) - (9) 对应; no-engine 只在给出
--uninstall-ocr 时运行, 会卸载所列 OCR 插件且不重新安装, 应最后运行. 结束后删除 p611check, 运行目录与设备上的转储文件 (给出 --keep 时保留). 有检查
失败时退出码为 1.
"""
import argparse
import importlib
import io
import json
import re
import secrets
import sys
import tempfile
import time
from pathlib import Path
from xml.etree import ElementTree

REPO = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO / ".python"))

import device_samples_run as runner  # noqa: E402

PLUGIN = runner.PLUGIN
CHECK = "p611check"
WORK = f"{runner.BASE}/p611-runner"
RAW_DUMP = "/data/local/tmp/p611_raw_dump.xml"
EARLY = "/data/local/tmp/p611_early.sh"
DIALOG_WINDOWS = ("permissioncontroller", "GrantPermissions", "packageinstaller", "PackageInstaller")
STEPS = ("menu", "form", "run", "again", "cancel", "actions", "open-password", "owner-password", "both-passwords",
         "password-cancel", "text", "early-create", "close-during-check", "secrets", "rtl", "no-engine")
# The isolation marks a right-to-left plugin name may carry. zh-CN: 从右到左语言的插件名可能带的隔离符.
BIDI = dict.fromkeys(map(ord, chr(0x2068) + chr(0x2069) + chr(0x200E) + chr(0x200F)))

base = menu = wide = pmenu = None


def load_host(host_repo):
    """Imports the file manager helpers of the host checkout. zh-CN: 导入宿主检出中的文件管理器辅助模块."""
    global base, menu, wide, pmenu
    sys.path.insert(0, str(Path(host_repo) / "tools" / "explorer"))
    base = importlib.import_module("device_path_menu_check")
    menu = importlib.import_module("device_menu_check")
    wide = importlib.import_module("device_wide_layout_check")
    pmenu = importlib.import_module("device_plugin_menu_check")


class Checks:
    def __init__(self):
        self.failed = 0

    def report(self, verdict, name, detail=""):
        if verdict == "FAIL":
            self.failed += 1
        print(f"{verdict} {name} {detail}".rstrip(), flush=True)

    def check(self, name, ok, detail=""):
        self.report("PASS" if ok else "FAIL", name, detail)
        return ok


def unescape(value):
    return value.replace(chr(92) + "'", "'").replace(chr(92) + '"', '"').replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")


def plugin_text(values, name):
    xml = (REPO / "app" / "src" / "main" / "res" / values / "strings.xml").read_text(encoding="utf-8")
    match = re.search(r'<string name="' + re.escape(name) + r'"[^>]*>(.*?)</string>', xml, re.S)
    if not match:
        raise SystemExit(f"string {name} not found in the plugin's {values}")
    return unescape(match.group(1))


def pattern(template):
    """A regex for a format string (%1$s, %2$d). zh-CN: 格式字符串对应的正则."""
    parts = re.split(r"%\d+\$[sd]", template)
    return re.compile("^" + "(.+?)".join(map(re.escape, parts)) + "$")


class Texts:
    """The strings of one app language. zh-CN: 一种应用语言的字符串."""

    def __init__(self, host_values, plugin_values):
        h = lambda name: unescape(wide.text(host_values, name))  # noqa: E731
        p = lambda name: plugin_text(plugin_values, name)  # noqa: E731
        self.group = "Paper PDF"
        self.searchable = p("explorer_action_make_searchable")
        self.order = [p(f"explorer_action_{k}") for k in ("split", "compress", "to_images", "watermark", "extract_text", "make_searchable", "encrypt", "decrypt")]
        self.compress = p("explorer_action_compress")
        self.watermark = p("explorer_action_watermark")
        self.start = p("tool_start")
        self.done = p("tool_done")
        self.saved = pattern(p("tool_done_saved"))
        self.engine_auto = pattern(h("text_explorer_pdf_searchable_engine_auto"))
        self.recognizing = pattern(h("text_explorer_pdf_searchable_recognizing"))
        self.done_title = h("text_explorer_pdf_searchable_done")
        self.recognized_pages = h("text_explorer_pdf_searchable_recognized_pages")
        self.password = h("text_explorer_pdf_searchable_password")
        self.owner_password = h("text_explorer_pdf_searchable_owner_password")
        self.password_note = h("text_explorer_pdf_searchable_password_note")
        self.owner_password_note = h("text_explorer_pdf_searchable_owner_password_note")
        self.password_blank = h("text_explorer_pdf_searchable_password_blank")
        self.password_wrong = h("text_explorer_pdf_searchable_password_wrong")
        self.password_not_owner = h("text_explorer_pdf_searchable_password_not_owner")
        self.password_checking = h("text_explorer_pdf_searchable_password_checking")
        self.no_engine = h("text_explorer_pdf_searchable_no_engine")
        self.plugin_center = h("text_plugin_center")
        self.open = h("text_open")


# Device helpers. zh-CN: 设备辅助函数.

def q(path):
    return runner.quote(path)


def listing(serial, folder):
    out = base.shell(serial, f"ls -a {q(folder)}")
    return sorted(name for name in out.splitlines() if name and name not in (".", ".."))


def size(serial, path):
    value = base.shell(serial, f"stat -c %s {q(path)}")
    return int(value) if value.isdigit() else -1


def raw(serial):
    """All attributes of a read-only dump (focused, password, hint). zh-CN: 只读转储的全部属性."""
    base.shell(serial, f"uiautomator dump {RAW_DUMP}", timeout=60)
    xml = base.adb(serial, "exec-out", "cat", RAW_DUMP).stdout.decode("utf-8", "replace")
    base.shell(serial, f"rm -f {RAW_DUMP}")
    try:
        return [e.attrib for e in ElementTree.fromstring(xml[xml.find("<"):]).iter("node")] if "<" in xml else []
    except ElementTree.ParseError:
        return []


def rid(attrs):
    return attrs.get("resource-id", "").rpartition("/")[2]


def anywhere(nodes, text):
    """Whether [text] is the text, hint or description of a raw node. zh-CN: 某个节点的文字, 提示或描述是否为 [text]."""
    return any(text in (a.get("text", ""), a.get("hint", ""), a.get("content-desc", "")) for a in nodes)


def top_activity(serial):
    return base.shell(serial, "dumpsys activity activities | grep topResumedActivity")


def wait_top(serial, package, timeout=20):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if f" {package}/" in top_activity(serial):
            return True
        time.sleep(1)
    return False


def button(nodes, which="Positive"):
    return base.find(nodes, rid=f"md_buttonDefault{which}")


def to_check(serial, host):
    return pmenu.to_check_dir(serial, host, CHECK)


def focused_window(serial):
    return base.shell(serial, "dumpsys window | grep mCurrentFocus")


def prepare_device(serial, host):
    """Answers None when the file manager can be driven, else why not. On an emulator the notification permission is
    granted with `pm grant` (test AVD only) so its first-run prompt does not cover the file manager; on a phone a
    system dialog is left to the maintainer.
    zh-CN: 可以操作文件管理器时返回 None, 否则返回原因. 模拟器上用 `pm grant` 授予通知权限 (只用于测试 AVD), 免得首次运行的询问挡住
    文件管理器; 真机上的系统对话框留给维护者处理."""
    if serial.startswith("emulator-"):
        base.shell(serial, f"pm grant {host} android.permission.POST_NOTIFICATIONS 2>/dev/null || true")
        runner.front(serial)
    deadline = time.monotonic() + 30
    while True:
        window = focused_window(serial)
        if not any(w in window for w in DIALOG_WINDOWS):
            return None
        if time.monotonic() > deadline:
            return f"a system dialog has the focus, answer it on the device: {window.strip()[:120]}"
        time.sleep(2)


def row_menu(serial, name):
    """Opens the menu of the list row [name] like `menu.open_row_menu`, which looks for the more button only right of the
    name: a right-to-left layout puts it left of the name. zh-CN: 打开列表行 [name] 的菜单, 同 `menu.open_row_menu`;
    后者只在名称右侧找 "更多" 按钮, 从右到左的布局里它在名称左侧."""
    row = base.reveal(serial, text=name, rid="name", area=base.LIST)
    if row is None:
        return []
    band = [n for n in base.dump(serial) if n.id == "more" and n.bounds[1] <= row.center[1] <= n.bounds[3]]
    after = [n for n in band if n.bounds[0] >= row.bounds[0]]
    before = [n for n in band if n.bounds[2] <= row.bounds[2]]
    more = min(after, key=lambda n: n.bounds[0]) if after else max(before, key=lambda n: n.bounds[0]) if before else None
    if more is None:
        return []
    base.tap(serial, more)
    return menu.collect_popup(serial)


def command_entry(serial, row, label, attempts=3):
    """Opens the "3-Paper PDF" menu of [row]; answers (the entries of the menu, the node of [label] or None). The host
    lists plugin commands once its plugin catalog is loaded, so a missing group is retried.
    zh-CN: 打开 [row] 的 "3-Paper PDF" 菜单; 返回 (菜单项, [label] 的节点或 None). 宿主在插件目录加载完成后才列出插件命令, 所以找不到分组时重试."""
    group = None
    for attempt in range(attempts):
        first = row_menu(serial, row)
        group = next((e for e in first if "Paper PDF" in e.translate(BIDI)), None)
        if group is not None:
            break
        menu.close_menus(serial)
        if attempt + 1 < attempts:
            time.sleep(3)
    if group is None:
        return None, None
    if not menu.tap_entry(serial, group):
        menu.close_menus(serial)
        return None, None
    time.sleep(1.5)
    items = [i for i in menu.collect_popup(serial) if i != group]
    node = next((n for n in menu.entry_nodes(base.dump(serial)) if (n.text or "").translate(BIDI) == label.translate(BIDI)), None)
    return items, node


def open_command(serial, row, label):
    """Opens [label] from the menu of [row]; answers whether it was tapped. zh-CN: 从 [row] 的菜单打开 [label]."""
    items, node = command_entry(serial, row, label)
    if node is None:
        menu.close_menus(serial)
        return False
    base.tap(serial, node)
    return True


def wait_form(serial, timeout=20):
    return base.wait_node(serial, timeout=timeout, rid="pdf_searchable_name")


def form_nodes(serial, timeout=20):
    """The raw nodes of the open form, or [] when it does not show. zh-CN: 已打开表单的原始节点; 没有表单时为 []."""
    if wait_form(serial, timeout) is None:
        return []
    return raw(serial)


def password_field(nodes):
    return next((a for a in nodes if rid(a) == "pdf_searchable_password"), None)


def wait_password(serial, timeout=10):
    """Waits for the password section; answers (seconds, raw nodes) or (None, last nodes). zh-CN: 等待密码区出现."""
    start = time.monotonic()
    nodes = []
    while time.monotonic() - start < timeout:
        nodes = raw(serial)
        if any(rid(a) == "pdf_searchable_password_section" for a in nodes):
            return round(time.monotonic() - start, 1), nodes
        time.sleep(0.5)
    return None, nodes


def hide_keyboard(serial):
    """Hides the soft keyboard, which covers the dialog buttons after typing; BACK is sent only while it shows, as BACK
    would otherwise close the form. zh-CN: 收起软键盘 (输入后它会挡住对话框按钮); 只在键盘显示时发送返回键, 否则返回键会关闭表单."""
    for _ in range(3):
        if "mInputShown=true" not in base.shell(serial, "dumpsys input_method | grep mInputShown"):
            return
        base.shell(serial, "input keyevent KEYCODE_BACK")
        time.sleep(0.8)


def tap_button(serial, which="Positive"):
    hide_keyboard(serial)
    node = button(base.dump(serial), which)
    if node is None:
        return False
    base.tap(serial, node)
    return True


def type_text(serial, value):
    base.shell(serial, f"input text {value}")
    time.sleep(0.8)


def wait_text(serial, wanted, timeout=15):
    """Waits until one of [wanted] is the text, hint or description of a node; answers it or None."""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        nodes = raw(serial)
        hit = next((w for w in wanted if anywhere(nodes, w)), None)
        if hit:
            return hit
        time.sleep(1)
    return None


def follow_run(serial, texts, timeout=420):
    """Follows the progress dialog until the result or a failure; answers (result nodes or None, progress texts seen).
    zh-CN: 跟随进度对话框直到出现结果或失败; 返回 (结果节点或 None, 见到的进度文字)."""
    seen = []
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        nodes = base.dump(serial)
        title = base.find(nodes, rid="md_title")
        content = base.find(nodes, rid="md_content")
        if content and content.text and content.text not in seen:
            seen.append(content.text)
        if title and title.text == texts.done_title:
            return nodes, seen
        if title and title.text != texts.done_title and base.find(nodes, rid="md_buttonDefaultNegative") is None and content is None:
            return None, seen
        time.sleep(1)
    return None, seen


def fact(nodes, label):
    """The value beside [label] in a key-value block. zh-CN: 键值信息块中 [label] 旁边的值."""
    key = base.find(nodes, text=label)
    if key is None:
        return None
    row = [n for n in nodes if n is not key and n.text and abs(n.center[1] - key.center[1]) <= 12 and n.cls.endswith("TextView")]
    return row[0].text if row else None


def close_result(serial):
    """Closes the result dialog with its "Close" button. zh-CN: 用 "关闭" 关闭结果对话框."""
    if tap_button(serial, "Negative"):
        return True
    return tap_button(serial, "Positive")


def partial(names):
    return [n for n in names if ".part" in n]


# Fixtures. zh-CN: 测试文件.

GENERATE = """// p611 fixtures (device_explorer_searchable_check.py)
console.log('p611 fixtures start');
let dir = DIR;
let work = WORK;
let s = JSON.parse(files.read(work + '/secrets.json'));
files.remove(work + '/secrets.json');
let out = {};
try {
    let scan = dir + '/scan.pdf';
    pdf.encrypt(scan, dir + '/open.pdf', { userPassword: s.open, ownerPassword: s.open });
    pdf.encrypt(scan, dir + '/owner.pdf', { ownerPassword: s.owner, permissions: { modify: false } });
    pdf.encrypt(scan, dir + '/both.pdf', { userPassword: s.user, ownerPassword: s.bothOwner, permissions: { modify: false } });
    let ten = [];
    for (let i = 0; i < 10; i++) ten.push(scan);
    pdf.merge(ten, work + '/big-30.pdf');
    let hundred = [];
    for (let i = 0; i < 10; i++) hundred.push(work + '/big-30.pdf');
    pdf.merge(hundred, work + '/big-300.pdf');
    pdf.encrypt(work + '/big-300.pdf', dir + '/big.pdf', { userPassword: s.open, ownerPassword: s.open });
    files.remove(work + '/big-30.pdf');
    files.remove(work + '/big-300.pdf');
    out.pages = { open: pdf.info(dir + '/open.pdf', { password: s.open }).pageCount, big: pdf.info(dir + '/big.pdf', { password: s.open }).pageCount };
    out.ok = true;
} catch (e) {
    out.error = String(e);
}
s = null;
files.write(work + '/fixtures.json', JSON.stringify(out));
"""

VERIFY = """// p611 outputs (device_explorer_searchable_check.py)
console.log('p611 verify start');
let dir = DIR;
let out = {};
NAMES.forEach(name => {
    let path = dir + '/' + name;
    try {
        let info = pdf.info(path);
        let text = pdf.text(path).join(' ');
        out[name] = { encrypted: info.encrypted, pages: info.pageCount, chars: text.length, hello: /HELLO/i.test(text), scanned: /SCANNED/i.test(text) };
    } catch (e) {
        out[name] = { error: String(e) };
    }
});
files.write(WORK + '/verify.json', JSON.stringify(out));
"""


def host_script(serial, source, result, scratch, timeout=600):
    """Runs a host script from WORK and answers the JSON it writes. zh-CN: 运行宿主脚本并返回其写出的 JSON."""
    name = Path(result).stem + ".js"
    runner.push_text(serial, source, f"{WORK}/{name}", scratch / name)
    base.shell(serial, f"rm -f {q(result)}")
    runner.launch(serial, f"{WORK}/{name}", result)
    text = runner.wait_file(serial, result, timeout)
    return json.loads(text) if text else None


def make_fixtures(serial, checks, folder, passwords, scratch):
    base.shell(serial, f"rm -rf {q(folder)} {q(WORK)}; mkdir -p {q(folder)}/folder {q(WORK)}")
    ok = base.adb(serial, "push", str(REPO / "samples" / "fixtures" / "scanned-multi.pdf"), f"{folder}/scan.pdf", timeout=120).returncode == 0
    note = scratch / "note.txt"
    note.write_text("p611 note" + chr(10), encoding="utf-8")
    ok = ok and base.adb(serial, "push", str(note), f"{folder}/note.txt").returncode == 0
    secret = scratch / "secrets.json"
    secret.write_text(json.dumps(passwords), encoding="utf-8")
    try:
        ok = ok and base.adb(serial, "push", str(secret), f"{WORK}/secrets.json").returncode == 0
    finally:
        secret.unlink()
    source = GENERATE.replace("DIR", json.dumps(folder)).replace("WORK", json.dumps(WORK), 1)
    source = source.replace("let work = WORK;", "let work = " + json.dumps(WORK) + ";")
    result = host_script(serial, source, f"{WORK}/fixtures.json", scratch) if ok else None
    base.shell(serial, f"rm -f {q(WORK)}/secrets.json")
    passed = bool(result and result.get("ok"))
    checks.check("setup: fixtures", passed, json.dumps(result.get("pages") if passed else result)[:200])
    # The generator ran the passwords through a host script: only what follows is checked. zh-CN: 生成脚本用过密码, 只检查之后的日志.
    base.adb(serial, "logcat", "-c")
    return passed


# Steps. zh-CN: 各步骤.

def step_menu(serial, checks, texts, host, shots):
    to_check(serial, host)
    items, node = command_entry(serial, "scan.pdf", texts.searchable)
    base.screenshot(serial, shots, "p611-menu-scan.png")
    menu.close_menus(serial)
    if items is None:
        return checks.check("menu: scan.pdf", False, "no 3-Paper PDF menu")
    present = [i for i in texts.order if i in items]
    in_order = [i for i in items if i in texts.order] == present
    checks.check("menu: scan.pdf", node is not None and texts.compress in items and texts.watermark in items and in_order, " | ".join(items))
    for row in ("note.txt", "folder"):
        items, node = command_entry(serial, row, texts.searchable)
        menu.close_menus(serial)
        checks.check(f"menu: {row} has no command", node is None and (items is None or texts.searchable not in items),
                     "no 3-Paper PDF menu" if items is None else " | ".join(items))


def step_form(serial, checks, texts, state, shots):
    if not open_command(serial, "scan.pdf", texts.searchable):
        return checks.check("form: open", False, "menu entry not found")
    nodes = form_nodes(serial)
    name = next((a.get("text") for a in nodes if rid(a) == "pdf_searchable_name"), None)
    time.sleep(2)
    nodes = raw(serial)
    no_password = not any(rid(a) == "pdf_searchable_password_section" for a in nodes)
    create = button(base.dump(serial))
    state["create"] = create.center if create else None
    toggle = base.find(base.dump(serial), rid="pdf_searchable_advanced_toggle")
    if toggle:
        base.tap(serial, toggle)
    engine = base.wait_node(serial, timeout=8, rid="pdf_searchable_engine")
    engine_text = engine.text if engine else ""
    match = texts.engine_auto.match(engine_text)
    base.screenshot(serial, shots, "p611-form.png")
    checks.check("form: default name", name == "scan-searchable.pdf", f"name={name}")
    checks.check("form: no password field (Q231 1)", no_password)
    checks.check("form: engine Automatic (<engine>)", bool(match and match.group(1).strip()), f"engine={engine_text}")


def step_run(serial, checks, texts, folder, shots):
    if wait_form(serial, 3) is None and not open_command(serial, "scan.pdf", texts.searchable):
        return checks.check("run: open", False, "menu entry not found")
    if wait_form(serial) is None:
        return checks.check("run: open", False, "no form")
    started = time.monotonic()
    tap_button(serial)
    early = raw(serial)
    no_check = not anywhere(early, texts.password_checking)
    result, seen = follow_run(serial, texts)
    seconds = round(time.monotonic() - started)
    pages = [texts.recognizing.match(t) for t in seen]
    pages = [m.group(1) for m in pages if m]
    checks.check("run: starts at once, no password check (Q231 1)", no_check and bool(seen) and not wait_form(serial, 0.1), f"progress={len(seen)} texts")
    checks.check("run: progress per page", bool(pages), f"pages seen={pages}")
    if result is None:
        return checks.check("run: result", False, f"after {seconds} s; progress={seen[-3:]}")
    base.screenshot(serial, shots, "p611-result.png")
    recognized = fact(result, texts.recognized_pages)
    names = listing(serial, folder)
    checks.check("run: result", recognized == "3" and "scan-searchable.pdf" in names,
                 f"recognized={recognized} seconds={seconds} size={size(serial, folder + '/scan-searchable.pdf')}")
    opened = tap_button(serial) and wait_top(serial, PLUGIN)
    base.screenshot(serial, shots, "p611-open.png")
    base.key(serial, "KEYCODE_BACK")
    back = wait_top(serial, runner.HOST, 10)
    checks.check("run: Open shows the viewer", opened and back, "returned to the host" if back else "host not resumed")


def step_again(serial, checks, texts, folder):
    before = listing(serial, folder)
    if not open_command(serial, "scan.pdf", texts.searchable):
        return checks.check("again: open", False, "menu entry not found")
    nodes = form_nodes(serial)
    name = next((a.get("text") for a in nodes if rid(a) == "pdf_searchable_name"), None)
    tap_button(serial, "Negative")
    time.sleep(1.5)
    after = listing(serial, folder)
    checks.check("again: numbered name, closing creates nothing", name == "scan-searchable (1).pdf" and before == after, f"name={name}")


def cancel_run(serial, texts, how):
    """Waits for the progress of a started run and closes it with [how]; answers the progress text seen."""
    deadline = time.monotonic() + 60
    text = None
    while time.monotonic() < deadline:
        nodes = base.dump(serial)
        content = base.find(nodes, rid="md_content")
        if content and texts.recognizing.match(content.text or ""):
            text = content.text
            break
        time.sleep(0.5)
    if how == "back":
        base.key(serial, "KEYCODE_BACK")
    else:
        tap_button(serial, "Negative")
    time.sleep(4)
    return text


def step_cancel(serial, checks, texts, folder):
    for how in ("cancel", "back"):
        before = listing(serial, folder)
        if not open_command(serial, "scan.pdf", texts.searchable) or wait_form(serial) is None:
            checks.check(f"cancel ({how}): open", False, "no form")
            continue
        tap_button(serial)
        progress = cancel_run(serial, texts, how)
        after = listing(serial, folder)
        closed = base.find(base.dump(serial), rid="md_content") is None
        checks.check(f"cancel ({how}): nothing left", progress is not None and after == before and not partial(after) and closed,
                     f"at={progress} new={sorted(set(after) - set(before))}")


def step_actions(serial, checks, texts, folder, shots):
    for label, output, extra in ((texts.compress, "scan_compressed.pdf", None), (texts.watermark, "scan_watermarked.pdf", "CONFIDENTIAL")):
        tag = output.split("_")[1].split(".")[0]
        base.shell(serial, f"rm -f {q(folder + '/' + output)}")
        if not open_command(serial, "scan.pdf", label) or not wait_top(serial, PLUGIN):
            checks.check(f"actions: {tag}", False, "the plugin did not open")
            continue
        start = base.wait_node(serial, timeout=20, text=texts.start)
        if extra:
            field = next((n for n in base.dump(serial) if n.cls.endswith("EditText")), None)
            if field:
                base.tap(serial, field)
                type_text(serial, extra)
            start = base.wait_node(serial, timeout=10, text=texts.start)
        if start is None:
            checks.check(f"actions: {tag}", False, "no Start button")
            base.key(serial, "KEYCODE_BACK")
            continue
        base.tap(serial, start)
        saved = None
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline and saved is None:
            saved = next((n.text for n in base.dump(serial) if texts.saved.match(n.text or "")), None)
            time.sleep(1)
        base.screenshot(serial, shots, f"p612-{tag}.png")
        names = listing(serial, folder)
        checks.check(f"actions: {tag}", bool(saved and output in saved) and output in names,
                     f"saved={'yes' if saved else 'no'} size={size(serial, folder + '/' + output)} source={size(serial, folder + '/scan.pdf')}")
        done = base.wait_node(serial, timeout=5, text=texts.done)
        if done:
            base.tap(serial, done)
        if not wait_top(serial, runner.HOST, 10):
            base.key(serial, "KEYCODE_BACK")
            wait_top(serial, runner.HOST, 10)


def submit_password(serial, value):
    """Types [value] into the focused password field (all of it selected after an error) and taps "Create"."""
    if value:
        type_text(serial, value)
    tap_button(serial)


def finish(serial, checks, texts, folder, name, output):
    result, seen = follow_run(serial, texts)
    names = listing(serial, folder)
    if result is not None:
        close_result(serial)
    return checks.check(f"{name}: created", result is not None and output in names, f"progress={len(seen)} output={output in names}")


def step_open_password(serial, checks, texts, folder, passwords, shots):
    if not open_command(serial, "open.pdf", texts.searchable) or wait_form(serial) is None:
        return checks.check("open-password: form", False, "no form")
    seconds, nodes = wait_password(serial, 3)
    base.screenshot(serial, shots, "q231-open.png")
    checks.check("open-password: field within 3 s (Q231 2)", seconds is not None and anywhere(nodes, texts.password_note) and anywhere(nodes, texts.password),
                 f"after {seconds} s")
    tap_button(serial)
    blank = wait_text(serial, [texts.password_blank], 8)
    if not blank:
        base.screenshot(serial, shots, "q231-open-blank.png")
    submit_password(serial, "wrong" + secrets.token_hex(3))
    wrong = wait_text(serial, [texts.password_wrong], 20)
    still = wait_form(serial, 1) is not None
    if not (wrong and still):
        base.screenshot(serial, shots, "q231-open-wrong.png")
    checks.check("open-password: empty and wrong refused, form stays", bool(blank and wrong and still), f"blank={bool(blank)} wrong={bool(wrong)} form={still}")
    submit_password(serial, passwords["open"])
    finish(serial, checks, texts, folder, "open-password", "open-searchable.pdf")


def step_owner_password(serial, checks, texts, shots):
    if not open_command(serial, "owner.pdf", texts.searchable) or wait_form(serial) is None:
        return checks.check("owner-password: form", False, "no form")
    seconds, nodes = wait_password(serial, 5)
    base.screenshot(serial, shots, "q231-owner.png")
    checks.check("owner-password: permissions password field (Q231 3)",
                 seconds is not None and anywhere(nodes, texts.owner_password) and anywhere(nodes, texts.owner_password_note), f"after {seconds} s")
    tap_button(serial, "Negative")
    time.sleep(1)


def step_both_passwords(serial, checks, texts, folder, passwords):
    if not open_command(serial, "both.pdf", texts.searchable) or wait_form(serial) is None:
        return checks.check("both-passwords: form", False, "no form")
    seconds, nodes = wait_password(serial, 5)
    field = password_field(nodes)
    if field:
        base.shell(serial, "input tap {} {}".format(*base.Node(field).center))
    submit_password(serial, passwords["user"])
    refused = wait_text(serial, [texts.password_not_owner], 20)
    still = wait_form(serial, 1) is not None
    checks.check("both-passwords: open password refused (Q231 4)", seconds is not None and bool(refused) and still,
                 f"field={seconds} refused={bool(refused)} form={still}")
    submit_password(serial, passwords["bothOwner"])
    finish(serial, checks, texts, folder, "both-passwords", "both-searchable.pdf")


def step_password_cancel(serial, checks, texts, folder, passwords):
    before = listing(serial, folder)
    if not open_command(serial, "open.pdf", texts.searchable) or wait_form(serial) is None:
        return checks.check("password-cancel: form", False, "no form")
    seconds, nodes = wait_password(serial, 5)
    field = password_field(nodes)
    if field:
        base.shell(serial, "input tap {} {}".format(*base.Node(field).center))
    submit_password(serial, passwords["open"])
    progress = cancel_run(serial, texts, "cancel")
    after = listing(serial, folder)
    cleared = False
    if open_command(serial, "open.pdf", texts.searchable) and wait_form(serial) is not None:
        _, nodes = wait_password(serial, 5)
        field = password_field(nodes)
        cleared = field is not None and field.get("text", "") in ("", texts.password)
        tap_button(serial, "Negative")
        time.sleep(1)
    checks.check("password-cancel: nothing left, field empty (Q231 6)", progress is not None and after == before and not partial(after) and cleared,
                 f"at={progress} new={sorted(set(after) - set(before))} cleared={cleared}")


def step_text(serial, checks, folder, scratch):
    names = [n for n in ("scan-searchable.pdf", "open-searchable.pdf", "both-searchable.pdf") if n in listing(serial, folder)]
    if not names:
        return checks.report("SKIP", "text", "no output to read")
    source = VERIFY.replace("DIR", json.dumps(folder)).replace("NAMES", json.dumps(names)).replace("WORK +", json.dumps(WORK) + " +")
    result = host_script(serial, source, f"{WORK}/verify.json", scratch) or {}
    for name in names:
        value = result.get(name) or {}
        checks.check(f"text: {name}", value.get("encrypted") is False and value.get("pages") == 3 and bool(value.get("hello") or value.get("scanned")),
                     json.dumps(value)[:200])


EARLY_SCRIPT = """# p611: taps the menu entry, waits for the form window, then taps or presses back at once.
pkg=$1; x=$2; y=$3; mode=$4; cx=$5; cy=$6
count() { dumpsys window windows | grep -cE "Window #[0-9]+ Window[{][0-9a-f]+ u0 $pkg/"; }
c0=$(count)
input tap $x $y
i=0
while [ $i -lt 80 ]; do
  c=$(count)
  if [ "$c" -gt "$c0" ]; then break; fi
  i=$((i+1))
done
# A tap before the dialog has drawn its first frame does not reach it: wait for the enter animation.
if [ "$mode" = back ]; then input keyevent 4; else sleep 0.5; input tap $cx $cy; fi
echo "polls=$i"
"""


def early(serial, texts, host, mode, create, scratch):
    """Opens the form of big.pdf with 3-Paper PDF stopped and taps "Create" or presses back as soon as it shows."""
    local = scratch / "p611_early.sh"
    local.write_bytes(EARLY_SCRIPT.encode("utf-8"))
    base.adb(serial, "push", str(local), EARLY)
    items, node = command_entry(serial, "big.pdf", texts.searchable)
    if node is None:
        menu.close_menus(serial)
        return None
    base.shell(serial, f"am force-stop {PLUGIN}")
    x, y = node.center
    cx, cy = create or (0, 0)
    return base.shell(serial, f"sh {EARLY} {host} {x} {y} {mode} {cx} {cy}", timeout=120)


def step_early_create(serial, checks, texts, host, state, shots, scratch):
    create = state.get("create")
    if create is None:
        return checks.report("SKIP", "early-create", "run the form step first (the position of Create)")
    started = time.monotonic()
    polls = early(serial, texts, host, "tap", create, scratch)
    base.screenshot(serial, shots, "q231-early.png")
    first = raw(serial)
    disabled = any(rid(a) == "md_buttonDefaultPositive" and a.get("enabled") == "false" for a in first)
    seconds, nodes = wait_password(serial, 90)
    field = password_field(nodes)
    focused = field is not None and field.get("focused") == "true"
    if seconds is None or not focused:
        base.screenshot(serial, shots, "q231-early-failed.png")
    blank = anywhere(nodes, texts.password_blank)
    running = base.find(base.dump(serial), rid="md_content") is not None and wait_form(serial, 0.1) is None
    detail = f"{polls} disabled_seen={disabled} focused={focused} field_after={round(time.monotonic() - started, 1)} s"
    if blank and not disabled:
        checks.report("SKIP", "early-create (Q231 5)", "the check ended before the tap; " + detail)
    else:
        checks.check("early-create (Q231 5)", seconds is not None and focused and not blank and not running, detail)
    tap_button(serial, "Negative")
    time.sleep(1)


def step_close_during_check(serial, checks, texts, host, scratch):
    base.adb(serial, "logcat", "-c")
    polls = early(serial, texts, host, "back", None, scratch)
    time.sleep(6)
    alive = bool(base.shell(serial, f"pidof {host}"))
    crashes = base.shell(serial, "logcat -d -b crash | grep -c FATAL") or "0"
    listed = base.find(base.dump(serial), rid="explorer_item_list") is not None
    checks.check("close-during-check (Q231 8)", polls is not None and alive and crashes == "0" and listed and wait_form(serial, 0.1) is None,
                 f"{polls} alive={alive} fatal={crashes}")


def app_files(serial, package):
    out = base.shell(serial, f"run-as {package} find shared_prefs databases files no_backup -type f -size -8192k 2>/dev/null", timeout=120)
    return [line.strip() for line in out.splitlines() if line.strip() and not line.startswith("find:")]


def step_secrets(serial, checks, host, folder, passwords):
    values = [v for v in passwords.values()]
    forms = [enc for v in values for enc in (v.encode("utf-8"), v.encode("utf-16-le"))]
    log = base.adb(serial, "logcat", "-d", "-b", "all", timeout=120).stdout
    in_log = sum(1 for v in values if v.encode("utf-8") in log)
    checks.check("secrets: logcat", in_log == 0, f"{len(log)} bytes, passwords found={in_log}")
    for package in (host, PLUGIN):
        found = 0
        paths = app_files(serial, package)
        for path in paths:
            data = base.adb(serial, "exec-out", "run-as", package, "cat", path, timeout=120).stdout
            if any(f in data for f in forms):
                found += 1
        checks.check(f"secrets: data of {package.rsplit('.', 1)[-1]}", found == 0 and bool(paths), f"files={len(paths)} with a password={found}")
    names = listing(serial, folder)
    checks.check("secrets: file names", not any(v in n for v in values for n in names), f"{len(names)} names")


LANGUAGE_KEY = 'name="key_$_app_language"'
PREFS_TEMP = "/data/local/tmp/p611_prefs.xml"


def host_prefs(serial, host):
    return base.adb(serial, "exec-out", "run-as", host, "cat", f"shared_prefs/{host}_preferences.xml").stdout.decode("utf-8", "replace")


def language_line(xml):
    return next((line for line in xml.splitlines() if LANGUAGE_KEY in line), None)


def restore_language(serial, host, line, scratch):
    """Puts the host's language setting back to [line] (None: no setting). The host keeps a per-app locale it sees as
    its own language setting, which the plugins follow, so resetting the locale alone leaves the host and the plugins
    in the check language. zh-CN: 把宿主的语言设置恢复为 [line] (None 表示没有该设置). 宿主会把看到的应用区域设置存为自己的语言设置,
    插件跟随它, 所以只重置区域设置会让宿主与插件停留在检查用的语言."""
    base.shell(serial, f"am force-stop {host}")
    xml = host_prefs(serial, host)
    current = language_line(xml)
    if current == line or "</map>" not in xml:
        return current == line
    lines = xml.splitlines()
    if current is not None:
        lines.remove(current)
    if line is not None:
        lines.insert(lines.index("</map>"), line)
    local = scratch / "prefs.xml"
    local.write_bytes(("\n".join(lines) + "\n").encode("utf-8"))
    base.adb(serial, "push", str(local), PREFS_TEMP)
    base.shell(serial, f"chmod 644 {PREFS_TEMP}; run-as {host} cp {PREFS_TEMP} shared_prefs/{host}_preferences.xml; rm -f {PREFS_TEMP}")
    return language_line(host_prefs(serial, host)) == line


def chevron_direction(image):
    """Which way the chevron in [image] points ("down", "up", "right" or "left"), None without one: its open end is wide
    and its tip narrow, so the spread of the drawn pixels is compared at both ends of each axis.
    zh-CN: [image] 中的箭头朝向 ("down", "up", "right" 或 "left"), 没有箭头时返回 None: 箭头开口一端宽, 尖端窄, 因此比较两个方向上两端的
    像素分布宽度."""
    width, height = image.size
    background = image.getpixel((0, 0))
    ink = [[sum(abs(a - b) for a, b in zip(image.getpixel((x, y)), background)) > 90 for x in range(width)] for y in range(height)]
    rows = [xs for xs in ([x for x in range(width) if ink[y][x]] for y in range(height)) if xs]
    columns = [ys for ys in ([y for y in range(height) if ink[y][x]] for x in range(width)) if ys]
    if len(rows) < 4 or len(columns) < 4:
        return None

    def ends(lines):
        part = max(1, len(lines) // 4)
        spread = lambda chosen: sum(max(v) - min(v) for v in chosen) / len(chosen)
        return spread(lines[:part]), spread(lines[-part:])

    top, bottom = ends(rows)
    first, last = ends(columns)
    if abs(top - bottom) >= abs(first - last):
        return "down" if top > bottom else "up"
    return "right" if first > last else "left"


def arrow_direction(serial, nodes):
    """Which way the "Advanced" arrow of the form points, read from a screenshot of its bounds; None when it is not shown
    or Pillow is missing.
    zh-CN: 表单 "高级" 箭头的朝向, 从其范围内的截图判断; 箭头未显示或缺少 Pillow 时返回 None."""
    arrow = next((a for a in nodes if rid(a) == "pdf_searchable_advanced_arrow"), None)
    if arrow is None:
        return None
    try:
        from PIL import Image
    except ImportError:
        return None
    base.shell(serial, f"screencap -p {base.SHOT}")
    data = base.adb(serial, "exec-out", "cat", base.SHOT).stdout
    base.shell(serial, f"rm -f {base.SHOT}")
    if not data:
        return None
    return chevron_direction(Image.open(io.BytesIO(data)).convert("RGB").crop(base.Node(arrow).bounds))


def step_rtl(serial, checks, host, host_values, rtl_values, shots, scratch):
    texts = Texts(rtl_values, rtl_values)
    language = rtl_values.split("-", 1)[1]
    previous = re.search(r"\[(.*?)\]", base.shell(serial, f"cmd locale get-app-locales {host}"))
    previous = previous.group(1) if previous else ""
    setting = language_line(host_prefs(serial, host))
    base.shell(serial, f"cmd locale set-app-locales {host} --locales {language}")
    try:
        to_check(serial, host)
        opened = open_command(serial, "open.pdf", texts.searchable)
        if not opened or wait_form(serial) is None:
            base.screenshot(serial, shots, "q231-rtl-failed.png")
            return checks.check("rtl: form", False, "no form" if opened else "menu entry not found")
        seconds, nodes = wait_password(serial, 5)
        base.screenshot(serial, shots, "q231-rtl.png")
        field = password_field(nodes)
        toggle = None
        if field is not None:
            box = base.Node(field).bounds
            icons = [base.Node(a) for a in nodes if a.get("class", "").endswith(("ImageButton", "CheckableImageButton")) and a.get("content-desc")]
            toggle = next((n for n in icons if box[1] - 20 <= n.center[1] <= box[3] + 20), None)
        left = toggle is not None and field is not None and toggle.center[0] < (base.Node(field).bounds[0] + base.Node(field).bounds[2]) // 2
        shown = False
        if toggle is not None:
            base.tap(serial, toggle)
            after = password_field(raw(serial))
            shown = field.get("password") == "true" and after is not None and after.get("password") == "false"
        checks.check("rtl: note, field and show-password button (Q231 9)", seconds is not None and anywhere(nodes, texts.password_note) and left and shown,
                     f"language={language} toggle_left={left} toggles={shown}")
        hide_keyboard(serial)
        folded = arrow_direction(serial, raw(serial))
        unfolded = None
        advanced = next((a for a in raw(serial) if rid(a) == "pdf_searchable_advanced_toggle"), None)
        if advanced is not None:
            base.tap(serial, base.Node(advanced))
            time.sleep(0.5)
            unfolded = arrow_direction(serial, raw(serial))
        checks.check("rtl: \"Advanced\" arrow down when folded, up when unfolded", folded == "down" and unfolded == "up", f"folded={folded} unfolded={unfolded}")
        tap_button(serial, "Negative")
    finally:
        base.shell(serial, f"cmd locale set-app-locales {host} --locales {previous or chr(34) + chr(34)}")
        checks.check("rtl: language setting restored", restore_language(serial, host, setting, scratch), f"setting={'kept' if setting else 'none'}")


def step_no_engine(serial, checks, texts, host, packages, shots):
    if not packages:
        return checks.report("SKIP", "no-engine", "needs --uninstall-ocr")
    removed = [p for p in packages if "Success" in base.adb(serial, "uninstall", p, timeout=120).stdout.decode("utf-8", "replace")]
    to_check(serial, host)
    if not open_command(serial, "scan.pdf", texts.searchable):
        return checks.check("no-engine: guide (P6.11 f)", False, "menu entry not found")
    content = base.wait_node(serial, timeout=10, rid="md_content")
    nodes = base.dump(serial)
    center = button(nodes)
    base.screenshot(serial, shots, "p611-no-engine.png")
    guide = bool(content and content.text == texts.no_engine)
    plugin_center = bool(center and (center.text or "").casefold() == texts.plugin_center.casefold())
    checks.check("no-engine: guide (P6.11 f)", guide and plugin_center and wait_form(serial, 0.1) is None,
                 f"uninstalled={len(removed)}/{len(packages)} guide={guide} button={center.text if center else None!r}")
    tap_button(serial, "Negative")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--host-repo", required=True)
    parser.add_argument("--host", default=runner.HOST)
    parser.add_argument("--values", default="values")
    parser.add_argument("--rtl-values", default="values-ar")
    parser.add_argument("--only")
    parser.add_argument("--uninstall-ocr", default="")
    parser.add_argument("--shots")
    parser.add_argument("--keep", action="store_true")
    args = parser.parse_args()
    load_host(args.host_repo)
    serial, host = args.serial, args.host
    steps = [s for s in (args.only.split(",") if args.only else STEPS) if s]
    unknown = [s for s in steps if s not in STEPS]
    if unknown:
        raise SystemExit(f"unknown steps: {unknown}")
    if args.shots:
        Path(args.shots).mkdir(parents=True, exist_ok=True)
    texts = Texts(args.values, args.values)
    checks = Checks()
    state = {}
    passwords = {key: "p611" + secrets.token_hex(6) for key in ("open", "owner", "user", "bothOwner")}
    root = wide.working_directory(serial, host)
    folder = f"{root}/{CHECK}"
    print(f"INFO folder {folder}, steps {steps}", flush=True)
    with tempfile.TemporaryDirectory() as temp:
        scratch = Path(temp)
        try:
            if not make_fixtures(serial, checks, folder, passwords, scratch):
                return 1
            runner.front(serial)
            base.dismiss_welcome(serial, timeout=20)
            blocked = prepare_device(serial, host)
            if blocked:
                checks.report("FAIL", "setup", blocked)
                return 1
            calls = {
                "menu": lambda: step_menu(serial, checks, texts, host, args.shots),
                "form": lambda: (to_check(serial, host), step_form(serial, checks, texts, state, args.shots)),
                "run": lambda: step_run(serial, checks, texts, folder, args.shots),
                "again": lambda: (to_check(serial, host), step_again(serial, checks, texts, folder)),
                "cancel": lambda: (to_check(serial, host), step_cancel(serial, checks, texts, folder)),
                "actions": lambda: (to_check(serial, host), step_actions(serial, checks, texts, folder, args.shots)),
                "open-password": lambda: (to_check(serial, host), step_open_password(serial, checks, texts, folder, passwords, args.shots)),
                "owner-password": lambda: (to_check(serial, host), step_owner_password(serial, checks, texts, args.shots)),
                "both-passwords": lambda: (to_check(serial, host), step_both_passwords(serial, checks, texts, folder, passwords)),
                "password-cancel": lambda: (to_check(serial, host), step_password_cancel(serial, checks, texts, folder, passwords)),
                "text": lambda: step_text(serial, checks, folder, scratch),
                "early-create": lambda: (to_check(serial, host), step_early_create(serial, checks, texts, host, state, args.shots, scratch)),
                "close-during-check": lambda: (to_check(serial, host), step_close_during_check(serial, checks, texts, host, scratch)),
                "secrets": lambda: step_secrets(serial, checks, host, folder, passwords),
                "rtl": lambda: step_rtl(serial, checks, host, args.values, args.rtl_values, args.shots, scratch),
                "no-engine": lambda: step_no_engine(serial, checks, texts, host, [p for p in args.uninstall_ocr.split(",") if p], args.shots),
            }
            for step in steps:
                try:
                    calls[step]()
                except Exception as e:  # noqa: BLE001 - one broken step does not stop the others
                    checks.report("FAIL", step, f"{type(e).__name__}: {str(e)[:160]}")
        finally:
            if not args.keep:
                base.shell(serial, f"rm -rf {q(folder)} {q(WORK)} {EARLY} {RAW_DUMP}")
    print(f"INFO {checks.failed} failed", flush=True)
    return 1 if checks.failed else 0


if __name__ == "__main__":
    sys.exit(main())
