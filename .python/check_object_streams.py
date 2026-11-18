"""Reads the object stream outputs of ObjectStreamWriterTest with third-party parsers (roadmap P6.7, spike S4).

`gradlew :app:testDebugUnitTest --tests '*.write.ObjectStreamWriterTest'` writes every fixture and sample twice to
app/build/objstm: `<name>.classic.pdf` (PDDocument.save) and `<name>.objstm.pdf` (the plugin's object stream writer).
This script opens each pair with pypdf in strict mode and compares the page count, the page sizes and rotations, the
extracted text of every page, the outline, the form fields and the document information (without /ModDate and
/Producer); the object stream output must use object streams and a cross-reference stream. When `qpdf` or poppler's
`pdfinfo` is on PATH, each output also passes `qpdf --check` and `pdfinfo` with the same page count. One line per pair
says PASS or FAIL with the sizes; the exit status is 1 when any pair fails.

zh-CN: 用第三方解析器读回 ObjectStreamWriterTest 的对象流输出 (路线图 P6.7, spike S4). 运行
`gradlew :app:testDebugUnitTest --tests '*.write.ObjectStreamWriterTest'` 后, 每个夹具与样例在 app/build/objstm 下各有两份:
`<名称>.classic.pdf` (PDDocument.save) 与 `<名称>.objstm.pdf` (插件的对象流写出器). 本脚本用 pypdf 的严格模式打开每一对, 比较
页数, 页面尺寸与旋转, 每页提取的文本, 大纲, 表单字段与文档信息 (不含 /ModDate 与 /Producer); 对象流输出必须使用对象流与交叉
引用流. PATH 上有 `qpdf` 或 poppler 的 `pdfinfo` 时, 每份输出还要通过 `qpdf --check`, 并由 `pdfinfo` 读出相同的页数. 每对输出
一行 PASS 或 FAIL 及体积; 有任何一对失败时退出码为 1.

Usage: py .python/check_object_streams.py [--directory app/build/objstm]
"""
from __future__ import annotations

import argparse
import logging
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHANGED_INFO = {'/ModDate', '/Producer'}


def describe(value) -> str:
    """A comparable form of a PDF value; dictionaries list their sorted keys only."""
    from pypdf.generic import ArrayObject, DictionaryObject, IndirectObject, NameObject

    if isinstance(value, IndirectObject):
        value = value.get_object()
    if isinstance(value, DictionaryObject):
        return '<<' + ' '.join(sorted(str(key) for key in value.keys())) + '>>'
    if isinstance(value, ArrayObject):
        return '[' + ' '.join(describe(item) for item in value) + ']'
    if isinstance(value, NameObject):
        return str(value)
    if isinstance(value, float):
        return f'{value:.3f}'
    if isinstance(value, int) and not isinstance(value, bool):
        return f'{float(value):.3f}'
    return repr(str(value))


def outline(reader) -> list[str]:
    result: list[str] = []

    def walk(items, depth: int) -> None:
        for item in items:
            if isinstance(item, list):
                walk(item, depth + 1)
                continue
            try:
                page = reader.get_destination_page_number(item)
            except Exception:  # noqa: BLE001 - an entry without a page destination
                page = None
            result.append(f'{depth} {item.title} -> {page}')

    walk(reader.outline, 0)
    return result


def summary(path: Path) -> dict:
    from pypdf import PdfReader

    reader = PdfReader(path, strict=True)
    fields = reader.get_fields() or {}
    info = reader.metadata or {}
    return {
        'pages': len(reader.pages),
        'boxes': [f'{describe(page.mediabox)} {describe(page.cropbox)} {page.rotation}' for page in reader.pages],
        'text': [page.extract_text() for page in reader.pages],
        'outline': outline(reader),
        'fields': sorted(f'{name} {field.get("/FT")} {describe(field.get("/V"))} {describe(field.get("/DV"))}' for name, field in fields.items()),
        'info': {key: describe(value) for key, value in sorted(info.items()) if key not in CHANGED_INFO},
    }


def layout_problems(path: Path) -> list[str]:
    raw = path.read_bytes()
    problems = []
    if b'/Type /ObjStm' not in raw:
        problems.append('no object stream')
    if b'/Type /XRef' not in raw:
        problems.append('no cross-reference stream')
    if b'trailer' in raw or b'\nxref\n' in raw:
        problems.append('a classic cross-reference table or trailer')
    return problems


def external(path: Path, pages: int) -> list[str]:
    problems = []
    qpdf = shutil.which('qpdf')
    if qpdf:
        run = subprocess.run([qpdf, '--check', str(path)], capture_output=True, text=True, check=False)
        if run.returncode not in (0, 3):  # 3: warnings only. zh-CN: 3 表示只有警告.
            problems.append(f'qpdf --check exit {run.returncode}')
    pdfinfo = shutil.which('pdfinfo')
    if pdfinfo:
        run = subprocess.run([pdfinfo, str(path)], capture_output=True, text=True, check=False, encoding='utf-8', errors='replace')
        match = re.search(r'^Pages:\s+(\d+)', run.stdout, re.MULTILINE)
        if run.returncode != 0 or not match or int(match.group(1)) != pages:
            problems.append(f'pdfinfo exit {run.returncode}, pages {match.group(1) if match else "?"}')
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    parser.add_argument('--directory', type=Path, default=ROOT / 'app' / 'build' / 'objstm')
    arguments = parser.parse_args()
    try:
        import pypdf
    except ImportError:
        print('FAIL pypdf is not installed (py -m pip install pypdf)')
        return 1
    # pypdf logs recoverable oddities; strict mode already raises on the ones that matter. zh-CN: 严格模式已对要紧的问题抛异常.
    logging.getLogger('pypdf').setLevel(logging.ERROR)
    pairs = sorted(arguments.directory.glob('*.objstm.pdf'))
    if not pairs:
        print(f'FAIL no *.objstm.pdf in {arguments.directory}; run the ObjectStreamWriterTest JVM test first')
        return 1
    tools = ['pypdf ' + pypdf.__version__] + [name for name in ('qpdf', 'pdfinfo') if shutil.which(name)]
    print('tools: ' + ', '.join(tools))
    failures = 0
    for compact in pairs:
        name = compact.name.removesuffix('.objstm.pdf')
        classic = compact.with_name(name + '.classic.pdf')
        problems = layout_problems(compact)
        try:
            expected = summary(classic)
            actual = summary(compact)
            for key in expected:
                if expected[key] != actual[key]:
                    problems.append(f'{key} differs')
            problems += external(compact, actual['pages'])
        except Exception as error:  # noqa: BLE001 - report any parser failure as a failed pair
            problems.append(f'{type(error).__name__}: {error}')
            actual = {'pages': '?'}
        sizes = f'classic {classic.stat().st_size} B, objstm {compact.stat().st_size} B, {actual["pages"]} pages'
        if problems:
            failures += 1
            print(f'FAIL {name}: {sizes}; ' + '; '.join(problems))
        else:
            print(f'PASS {name}: {sizes}')
    print(f'{len(pairs) - failures} / {len(pairs)} pairs pass')
    return 1 if failures else 0


if __name__ == '__main__':
    sys.exit(main())
