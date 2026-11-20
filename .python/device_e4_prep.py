"""Prepares the manual E4 test of 3-Paper PDF 1.1.0 (roadmap P6.15 row 4) on one device, reports it and cleans it up.

`prepare` writes the inputs into the shared folder `/sdcard/Download/3-paper-e4/`, remembers what the default output
folder `/sdcard/Documents/3-Paper PDF/` already holds and checks every input on the device:

- the samples text.pdf, outline.pdf, images.pdf, encrypted.pdf (user password `paper-user`, owner password
  `paper-owner`), form.pdf (11 fields), scanned.pdf and scanned-multi.pdf (three 300 dpi scans), the 100 page
  manual-100.pdf of the viewer test and the photos photo-landscape.jpg / photo-portrait.jpg of the toolbox test;
- form-60.pdf: 60 fields on three pages (text, check box, combo box, required and read-only ones, a signed and an
  unsigned signature field), more than the 40 after which the fill-in tool folds the later pages;
- form-xfa-dynamic.pdf (catalog `NeedsRendering true`) and form-xfa-static.pdf (an XFA stream next to the AcroForm);
- pages-300.pdf: 300 text pages ("needle" on pages 7, 150 and 299), for long tasks and the large script that goes to
  the host through `codeUri`;
- article-3.pdf: three pages of dense text, the source of scan-like.pdf;
- stamp.png: a 360 x 120 image with transparency, for the image watermark;
- made on the device by a host script with the pdf module: scan-like.pdf (the pages of article-3.pdf rendered at
  200 dpi, turned by 1.2 degrees, grey JPEG quality 70: a stand-in for a real scan, which the tester may use instead),
  encrypted-aes256.pdf (AES-256, user password `e4-user`, owner password `e4-owner`) and owner-only.pdf (owner password
  `e4-owner` only, printing not allowed).

The host (6.8.1 with contract version 2), the plugin (1.1.0) and an OCR plugin must be installed and enabled; the
script installs nothing, taps nothing and grants no permission. `status` prints the results written since `prepare`,
`cleanup` deletes the input folder and only those results. The passwords above are test values of these samples.

Output: one line per step with PASS / FAIL / INFO and the key values, then `RESULT PASS` or `RESULT FAIL` (exit code 1).

zh-CN: 在一台设备上准备 3-Paper PDF 1.1.0 的 E4 手测 (路线图 P6.15 第 4 行), 报告其产物并在测后清理. `prepare` 把输入写到共享目录
`/sdcard/Download/3-paper-e4/`, 记下默认输出目录 `/sdcard/Documents/3-Paper PDF/` 原有的内容, 并在设备上逐个核对输入: 样例 text.pdf,
outline.pdf, images.pdf, encrypted.pdf (用户密码 `paper-user`, 所有者密码 `paper-owner`), form.pdf (11 个字段), scanned.pdf 与
scanned-multi.pdf (三页 300 dpi 扫描), 查看器手测的 100 页 manual-100.pdf, 工具箱手测的两张照片; form-60.pdf (三页 60 个字段: 文本, 勾选框,
下拉框, 必填与只读字段, 已签名与未签名的签名字段, 超过填写工具折叠后续页的 40 个); form-xfa-dynamic.pdf (目录 `NeedsRendering true`) 与
form-xfa-static.pdf (AcroForm 旁带 XFA 流); pages-300.pdf (300 页文字, 第 7, 150, 299 页有 "needle", 用于长任务与经 `codeUri` 交给宿主的大脚本);
article-3.pdf (三页密排文字, scan-like.pdf 的来源); stamp.png (360 x 120 带透明度, 用于图片水印); 由宿主脚本在设备上用 pdf 模块生成的
scan-like.pdf (article-3.pdf 各页以 200 dpi 渲染, 旋转 1.2 度, 灰度 JPEG 质量 70, 代替真实扫描件, 测试者也可以换成真实的), encrypted-aes256.pdf
(AES-256, 用户密码 `e4-user`, 所有者密码 `e4-owner`) 与 owner-only.pdf (只有所有者密码 `e4-owner`, 不允许打印). 宿主 (6.8.1, 带契约 2), 插件
(1.1.0) 与一个 OCR 插件须已安装并启用; 本脚本不安装任何东西, 不点按屏幕, 也不授予权限. `status` 列出 `prepare` 之后写入的结果, `cleanup` 删除
输入目录, 并且只删除这些结果. 上面的密码都是这些样例的测试值.

`perf` (after `prepare`) runs makeSearchable on scan-like.pdf at 300 and 200 dpi and prints the time, the lines and how
many words of the article the text layer holds (appendix F). zh-CN: `perf` (在 `prepare` 之后) 以 300 与 200 dpi 对 scan-like.pdf
运行 makeSearchable, 输出耗时, 行数与文字层里能找到的文章词数 (附录 F).

Usage: py -3.12 -I .python/device_e4_prep.py --serial SERIAL prepare | perf | status | cleanup
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shlex
import struct
import subprocess
import sys
import tempfile
import time
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import device_toolbox_manual_prep as toolbox  # noqa: E402
import device_viewer_manual_prep as viewer  # noqa: E402
import generate_fixtures as fx  # noqa: E402

HOST = "org.autojs.autojs6"
RUN_ACTIVITY = f"{HOST}/org.autojs.autojs.external.open.RunIntentActivity"
INPUTS = "/sdcard/Download/3-paper-e4"
OUTPUTS = toolbox.OUTPUTS
BEFORE = INPUTS + "/.outputs-before.txt"
COPIED_SAMPLES = ("text.pdf", "outline.pdf", "images.pdf", "encrypted.pdf", "form.pdf", "scanned.pdf", "scanned-multi.pdf")
DEVICE_MADE = ("scan-like.pdf", "encrypted-aes256.pdf", "owner-only.pdf")
NEEDLE_PAGES = (7, 150, 299)
WORDS = ("paper", "scanner", "archive", "invoice", "harbor", "garden", "signal", "window", "copper", "river", "ledger",
         "orange", "meadow", "lantern", "station", "summer", "pencil", "button", "marble", "canvas", "rocket", "village")

DEVICE_JS = r"""
var DIR = @@DIR@@;
var OUT = @@OUT@@;
var answer = {};
try {
    var parts = [];
    for (var page = 1; page <= 3; page++) {
        var image = pdf.render(DIR + '/article-3.pdf', page, { dpi: 200 });
        var turned = images.rotate(image, 1.2);
        var grey = images.grayscale(turned);
        var file = DIR + '/.scan-' + page + '.jpg';
        images.save(grey, file, 'jpg', 70);
        image.recycle(); turned.recycle(); grey.recycle();
        parts.push(file);
    }
    answer.scan = pdf.fromImages(parts, DIR + '/scan-like.pdf', { pageSize: 'A4' }).pageCount;
    parts.forEach(function (file) { files.remove(file); });
    answer.aes = pdf.encrypt(DIR + '/text.pdf', DIR + '/encrypted-aes256.pdf', { userPassword: 'e4-user', ownerPassword: 'e4-owner', keyLength: 256 }).pageCount;
    answer.owner = pdf.encrypt(DIR + '/outline.pdf', DIR + '/owner-only.pdf', { ownerPassword: 'e4-owner', permissions: { print: false } }).pageCount;
    answer.fields60 = pdf.forms(DIR + '/form-60.pdf').length;
    answer.signed = pdf.forms(DIR + '/form-60.pdf').filter(function (f) { return f.signed === true; }).length;
    answer.xfaDynamic = pdf.info(DIR + '/form-xfa-dynamic.pdf').xfa;
    answer.xfaStatic = pdf.info(DIR + '/form-xfa-static.pdf').xfa;
    answer.pages300 = pdf.info(DIR + '/pages-300.pdf').pageCount;
} catch (e) {
    answer.error = String(e);
}
files.write(OUT, JSON.stringify(answer));
"""


PERF_JS = r"""
var DIR = @@DIR@@;
var OUT = @@OUT@@;
var WORDS = @@WORDS@@;
var answer = { runs: [] };
try {
    [300, 200].forEach(function (dpi) {
        var target = DIR + '/.perf-searchable-' + dpi + '.pdf';
        var start = Date.now();
        var r = pdf.makeSearchable(DIR + '/scan-like.pdf', target, { ocrDpi: dpi });
        var ms = Date.now() - start;
        var found = pdf.text(target).map(function (text) {
            var lower = String(text).toLowerCase();
            return WORDS.filter(function (w) { return lower.indexOf(w) >= 0; }).length;
        });
        answer.runs.push({ dpi: dpi, ms: ms, ocrPages: r.ocrPages, lines: r.lines, sizeBytes: r.sizeBytes, words: found });
        files.remove(target);
    });
} catch (e) {
    answer.error = String(e);
}
files.write(OUT, JSON.stringify(answer));
"""


def png(width: int, height: int, pixel) -> bytes:
    """An RGBA PNG from pixel(x, y) -> (r, g, b, a). zh-CN: 由 pixel(x, y) -> (r, g, b, a) 生成的 RGBA PNG."""
    rows = b"".join(b"\x00" + bytes(channel for x in range(width) for channel in pixel(x, y)) for y in range(height))

    def chunk(kind: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b"")


def stamp() -> bytes:
    """A red frame, a translucent fill and a diagonal band on a transparent ground. zh-CN: 透明底上的红框, 半透明填充与斜带."""
    width, height = 360, 120

    def pixel(x: int, y: int):
        if x < 8 or y < 8 or x >= width - 8 or y >= height - 8:
            return 198, 40, 40, 255
        if abs((x - 20) - (y * 3)) < 14 or abs((x - 200) - (y * 3)) < 14:
            return 198, 40, 40, 200
        return 198, 40, 40, 48

    return png(width, height, pixel)


def sentence(seed: int, count: int) -> str:
    return " ".join(WORDS[(seed * 7 + index * 5) % len(WORDS)] for index in range(count)).capitalize() + "."


def article() -> bytes:
    document = fx.Document("e4/article-3.pdf")
    info = fx._information(document, "E4 article")
    fonts = fx._fonts(document)
    pages = fx.Pages(document)
    for number in range(1, 4):
        lines = [f"Article page {number}"] + [sentence(number * 100 + line, 9) for line in range(38)]
        pages.add(fx._text(lines, 770), {"Font": fonts}, compress=True)
    return document.build(fx._catalog(document, pages), info)


def pages_300() -> bytes:
    document = fx.Document("e4/pages-300.pdf")
    info = fx._information(document, "E4 300 pages")
    fonts = fx._fonts(document)
    pages = fx.Pages(document)
    for number in range(1, 301):
        lines = [f"Page {number} of 300"] + [sentence(number + line, 8) for line in range(6)]
        if number in NEEDLE_PAGES:
            lines.append("Search target: needle")
        pages.add(fx._text(lines, 770), {"Font": fonts}, compress=True)
    return document.build(fx._catalog(document, pages), info)


def form_60() -> bytes:
    """58 fields plus a signed and an unsigned signature field over three pages. zh-CN: 三页 58 个字段加已签名与未签名的签名字段."""
    document = fx.Document("e4/form-60.pdf")
    info = fx._information(document, "E4 form with 60 fields")
    helv = document.add({"Type": fx.Name("Font"), "Subtype": fx.Name("Type1"), "BaseFont": fx.Name("Helvetica"),
                         "Encoding": fx.Name("WinAnsiEncoding")})
    zadb = document.add({"Type": fx.Name("Font"), "Subtype": fx.Name("Type1"), "BaseFont": fx.Name("ZapfDingbats")})
    form = fx.FormBuilder(document, helv)
    form.annotations = {1: [], 2: [], 3: []}
    labels: dict[int, list[tuple[int, str]]] = {1: [], 2: [], 3: []}
    for index in range(60):
        page, row = index // 20 + 1, index % 20
        y = 780 - row * 34
        number = index + 1
        if index >= 58:
            name = "approval" if index == 58 else "witness"
            signature = None
            if index == 58:
                signature = document.add({"Type": fx.Name("Sig"), "Filter": fx.Name("Adobe.PPKLite"),
                                          "SubFilter": fx.Name("adbe.pkcs7.detached"), "ByteRange": [0, 0, 0, 0],
                                          "Contents": fx.Raw("<00>"), "M": fx.DATE, "Name": "E4 signer"})
            entries = {"FT": fx.Name("Sig"), "T": name}
            if signature:
                entries["V"] = signature
            form.fields.append(form._widget(page, (fx.FORM_FIELD_X, y - 6, 160, 24), form._appearance(160, 24, b"0 g 1 w 0.5 0.5 159 23 re S\n"), **entries))
            labels[page].append((y, "Signature " + name))
            continue
        name = f"field{number:02d}"
        if number % 5 == 0:
            form.fields.append(form.check_box(page, name, (fx.FORM_FIELD_X, y - 4, 14, 14), checked=number % 10 == 0))
            labels[page].append((y, f"Check {number}"))
        elif number % 7 == 0:
            form.fields.append(form.choice(page, name, (fx.FORM_FIELD_X, y - 6, fx.FORM_FIELD_WIDTH, 20), fx.FLAG_COMBO,
                                           [("a", "Alpha"), ("b", "Beta"), ("c", "Gamma")], ["a"], default="a"))
            labels[page].append((y, f"Choice {number}"))
        else:
            flags = (fx.FLAG_REQUIRED if number % 9 == 0 else 0) | (fx.FLAG_READ_ONLY if number % 11 == 0 else 0)
            value = f"Value {number}" if number % 3 == 0 else None
            form.fields.append(form.text(page, name, (fx.FORM_FIELD_X, y - 6, fx.FORM_FIELD_WIDTH, 20), value, flags=flags,
                                         label=f"Text field {number}"))
            labels[page].append((y, f"Text {number}" + (" *" if flags & fx.FLAG_REQUIRED else "")))
    pages = fx.Pages(document)
    for page in (1, 2, 3):
        parts = [f"BT /F1 10 Tf 72 {y} Td ".encode("ascii") + fx.literal(label) + b" Tj ET" for y, label in labels[page]]
        pages.add(fx.NEWLINE.join(parts) + fx.NEWLINE, {"Font": {"F1": helv}}, annotations=form.annotations[page])
    form.finish(pages.kids)
    acro_form = document.add({"Fields": form.fields, "DA": "/Helv 0 Tf 0 g", "DR": {"Font": {"Helv": helv, "ZaDb": zadb}},
                              "NeedAppearances": False, "SigFlags": 3})
    return document.build(fx._catalog(document, pages, AcroForm=acro_form), info)


XDP = (b'<?xml version="1.0" encoding="UTF-8"?>\n<xdp:xdp xmlns:xdp="http://ns.adobe.com/xdp/">'
       b'<template xmlns="http://www.xfa.org/schema/xfa-template/3.3/"><subform name="form1" layout="tb">'
       b'<field name="name"><ui><textEdit/></ui></field></subform></template></xdp:xdp>\n')


def form_xfa(dynamic: bool) -> bytes:
    name = "e4/form-xfa-dynamic.pdf" if dynamic else "e4/form-xfa-static.pdf"
    document = fx.Document(name)
    info = fx._information(document, "E4 XFA form (dynamic)" if dynamic else "E4 XFA form (static)")
    helv = document.add({"Type": fx.Name("Font"), "Subtype": fx.Name("Type1"), "BaseFont": fx.Name("Helvetica"),
                         "Encoding": fx.Name("WinAnsiEncoding")})
    form = fx.FormBuilder(document, helv)
    form.fields.append(form.text(1, "name", (fx.FORM_FIELD_X, 754, fx.FORM_FIELD_WIDTH, 20), "XFA", label="Name"))
    pages = fx.Pages(document)
    pages.add(b"BT /F1 11 Tf 72 760 Td " + fx.literal("Name") + b" Tj ET" + fx.NEWLINE, {"Font": {"F1": helv}}, annotations=form.annotations[1])
    form.finish(pages.kids)
    xfa = document.add(fx.Stream({}, XDP))
    acro_form = document.add({"Fields": form.fields, "DA": "/Helv 0 Tf 0 g", "DR": {"Font": {"Helv": helv}}, "XFA": xfa})
    extra = {"AcroForm": acro_form}
    if dynamic:
        extra["NeedsRendering"] = True
    return document.build(fx._catalog(document, pages, **extra), info)


def inputs() -> dict[str, bytes]:
    files = {name: (viewer.SAMPLES / name).read_bytes() for name in COPIED_SAMPLES}
    files[viewer.MANUAL] = viewer.manual_document()
    files.update({name: toolbox.photo(*size) for name, size in toolbox.PHOTOS.items()})
    files["form-60.pdf"] = form_60()
    files["form-xfa-dynamic.pdf"] = form_xfa(True)
    files["form-xfa-static.pdf"] = form_xfa(False)
    files["pages-300.pdf"] = pages_300()
    files["article-3.pdf"] = article()
    files["stamp.png"] = stamp()
    return files


def front(device: viewer.Device) -> None:
    found = device.shell(f"cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER {HOST}", check=False)
    activity = next((line.strip() for line in found.splitlines() if line.strip().startswith(HOST + "/") and "leakcanary" not in line), None)
    if activity is None:
        raise RuntimeError("the launcher activity of the host was not found")
    device.shell(f"am start -f 0x10008000 -n {activity}", check=False)
    time.sleep(4)


def device_made(device: viewer.Device, temporary: Path, timeout: int = 240, source: str = DEVICE_JS) -> dict | None:
    """Runs a host script that writes its answer to a JSON file; answers it, or None. zh-CN: 运行把答复写成 JSON 文件的宿主脚本."""
    out = f"{INPUTS}/.device-answer.json"
    script = f"{INPUTS}/.device-made.js"
    local = temporary / "device-made.js"
    local.write_text(source.replace("@@DIR@@", json.dumps(INPUTS)).replace("@@OUT@@", json.dumps(out)), encoding="utf-8", newline="\n")
    device.adb("push", str(local), script)
    front(device)
    device.shell(f"am start -n {RUN_ACTIVITY} -e path {shlex.quote(script)}", check=False)
    deadline = time.time() + timeout
    text = ""
    while time.time() < deadline and not text:
        time.sleep(3)
        text = device.shell(f"cat {shlex.quote(out)} 2>/dev/null", check=False).strip()
    device.shell(f"rm -f {shlex.quote(out)} {shlex.quote(script)}", check=False)
    return json.loads(text) if text else None


def prepare(device: viewer.Device) -> bool:
    api = device.shell("getprop ro.build.version.sdk").strip()
    model = device.shell("getprop ro.product.model").strip()
    host = device.shell(f"dumpsys package {HOST} | grep -m1 versionCode", check=False).split()
    print(f"INFO device={model} api={api} host={host[0] if host else '-'} plugin versionCode={device.installed_version()}")
    files = inputs()
    existing = toolbox.listing(device, OUTPUTS)
    device.shell(f"rm -rf {INPUTS} && mkdir -p {INPUTS}")
    ok = True
    with tempfile.TemporaryDirectory(prefix="3-paper-e4-") as temporary:
        for name, data in files.items():
            local = Path(temporary) / name
            local.write_bytes(data)
            device.adb("push", str(local), f"{INPUTS}/{name}")
        record = Path(temporary) / "before.txt"
        record.write_text("".join(f"{name}\n" for name in existing), encoding="utf-8", newline="\n")
        device.adb("push", str(record), BEFORE)
        for name, data in files.items():
            listed = device.shell(f"md5sum {INPUTS}/{name}", check=False).split()
            same = bool(listed) and listed[0] == hashlib.md5(data).hexdigest()
            ok &= same
            print(f"{'PASS' if same else 'FAIL'} input {name} bytes={len(data)}")
        answer = device_made(device, Path(temporary))
    if not answer or "error" in answer:
        print(f"FAIL device-made {(answer or {}).get('error', 'no answer from the host script')[:300]}")
        return False
    checks = {
        "scan-like.pdf": answer.get("scan") == 3,
        "encrypted-aes256.pdf": answer.get("aes") == 1,
        "owner-only.pdf": answer.get("owner") == 6,
        "form-60.pdf fields": answer.get("fields60") == 60 and answer.get("signed") == 1,
        "form-xfa-dynamic.pdf": answer.get("xfaDynamic") == "dynamic",
        "form-xfa-static.pdf": answer.get("xfaStatic") == "static",
        "pages-300.pdf": answer.get("pages300") == 300,
    }
    for label, passed in checks.items():
        ok &= passed
        print(f"{'PASS' if passed else 'FAIL'} device {label}")
    print(f"INFO device answer scan={answer.get('scan')} aes={answer.get('aes')} owner={answer.get('owner')} fields60={answer.get('fields60')} "
          f"xfa={answer.get('xfaDynamic')}/{answer.get('xfaStatic')} pages300={answer.get('pages300')}")
    sizes = device.shell(f"cd {INPUTS} && ls -l {' '.join(DEVICE_MADE)}", check=False)
    for line in sizes.splitlines():
        parts = line.split()
        if len(parts) >= 5:
            print(f"INFO device-made {parts[-1]} bytes={parts[4]}")
    print(f"INFO output folder already holds {len(existing)} entries (kept by cleanup)")
    return ok


def perf(device: viewer.Device) -> bool:
    """makeSearchable on scan-like.pdf (3 pages) at 300 and 200 dpi: time, lines and the words of the article found.
    zh-CN: 对 scan-like.pdf (3 页) 以 300 与 200 dpi 运行 makeSearchable: 耗时, 行数与识别出的文章词数."""
    if "scan-like.pdf" not in toolbox.listing(device, INPUTS):
        print("FAIL perf scan-like.pdf is missing: run prepare first")
        return False
    with tempfile.TemporaryDirectory(prefix="3-paper-e4-") as temporary:
        answer = device_made(device, Path(temporary), timeout=600, source=PERF_JS.replace("@@WORDS@@", json.dumps(list(WORDS))))
    if not answer or "error" in answer:
        print(f"FAIL perf {(answer or {}).get('error', 'no answer from the host script')[:300]}")
        return False
    plugin = device.installed_version()
    for run in answer["runs"]:
        print(f"PERF makeSearchable scan-like dpi={run['dpi']} ms={run['ms']} perPage={round(run['ms'] / 3)}ms ocrPages={run['ocrPages']} "
              f"lines={run['lines']} words={run['words']}/{len(WORDS)} sizeBytes={run['sizeBytes']} plugin={plugin}")
    return all(run["ocrPages"] == 3 for run in answer["runs"])


def written(device: viewer.Device) -> list[str]:
    kept = set(line.strip() for line in device.shell(f"cat {BEFORE} 2>/dev/null", check=False).splitlines() if line.strip())
    return [name for name in toolbox.listing(device, OUTPUTS) if name not in kept]


def status(device: viewer.Device) -> bool:
    results = written(device)
    print(f"INFO inputs={len(toolbox.listing(device, INPUTS))} results={len(results)}")
    for name in results:
        print(f"INFO result {name}")
    return True


def cleanup(device: viewer.Device) -> bool:
    if not toolbox.listing(device, INPUTS):
        print(f"PASS cleanup nothing to do ({INPUTS} is gone)")
        return True
    results = written(device)
    for name in results:
        device.shell(f"rm -rf {shlex.quote(f'{OUTPUTS}/{name}')}", check=False)
    left = written(device)
    device.shell(f"rm -rf {INPUTS}", check=False)
    gone = not toolbox.listing(device, INPUTS)
    print(f"{'PASS' if not left else 'FAIL'} cleanup results removed={len(results) - len(left)} left={len(left)}")
    print(f"{'PASS' if gone else 'FAIL'} cleanup {INPUTS}")
    return not left and gone


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepares the manual E4 test of 3-Paper PDF 1.1.0 on one device.")
    parser.add_argument("--serial", required=True)
    parser.add_argument("command", choices=("prepare", "perf", "status", "cleanup"))
    args = parser.parse_args()
    device = viewer.Device(args.serial)
    try:
        ok = {"prepare": prepare, "perf": perf, "status": status, "cleanup": cleanup}[args.command](device)
    except (RuntimeError, subprocess.SubprocessError) as error:
        print(f"FAIL {error}")
        ok = False
    print("RESULT PASS" if ok else "RESULT FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
