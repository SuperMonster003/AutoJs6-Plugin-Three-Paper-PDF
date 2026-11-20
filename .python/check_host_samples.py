"""Checks that the host's sample directory holds the same Rhino samples as samples/ (3-Paper PDF roadmap P6.14).

Every Rhino sample `samples/pdf-*.js` has a copy in the host's `app/src/main/assets-app/sample/3-Paper PDF/`, named
after the second line of the sample's header (`压缩 PDF [v6.8.1+]` -> `压缩 PDF [v6.8.1+].js`). The copies must be
byte-identical, and the host directory must hold no other `.js` file. The Node project and the Bun script are not
copied to the host. The host checkout is read from `--host` (default `../AutoJs6`, next to this repository); this is
a manual check and is not part of the Gradle build. One line per sample says PASS or FAIL; the exit status is 1 when
a check fails and 2 when the host directory is missing.

zh-CN: 检查宿主样例目录与 samples/ 中的 Rhino 样例是否一致 (3-Paper PDF 路线图 P6.14). 每个 Rhino 样例 `samples/pdf-*.js` 在
宿主的 `app/src/main/assets-app/sample/3-Paper PDF/` 下有一份副本, 文件名取样例文件头的第二行 (`压缩 PDF [v6.8.1+]` ->
`压缩 PDF [v6.8.1+].js`). 副本必须逐字节相同, 宿主目录里也不能有别的 `.js` 文件. Node 项目与 Bun 脚本不复制到宿主. 宿主仓库的
位置由 `--host` 给出 (默认 `../AutoJs6`, 与本仓库同级); 这是手动检查, 不属于 Gradle 构建. 每个样例输出一行 PASS 或 FAIL; 有检查
失败时退出码为 1, 找不到宿主目录时为 2.

Usage: py .python/check_host_samples.py [--host ../AutoJs6]
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOST_SAMPLES = Path('app/src/main/assets-app/sample/3-Paper PDF')
SUFFIX = ' [v6.8.1+]'


def host_name(sample: Path) -> str:
    """The host file name of a Rhino sample: the second header line, without ` * `, plus `.js`."""
    lines = sample.read_text(encoding='utf-8').splitlines()
    if len(lines) < 2 or lines[0].strip() != '/**' or not lines[1].lstrip().startswith('* '):
        raise ValueError(f'{sample.name}: the header does not start with "/**" and a title line')
    title = lines[1].lstrip()[2:].strip()
    if not title.endswith(SUFFIX):
        raise ValueError(f'{sample.name}: the title line does not end with "{SUFFIX}"')
    return title + '.js'


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split('\n\n', 1)[0])
    parser.add_argument('--host', type=Path, default=ROOT.parent / 'AutoJs6', help='the host checkout')
    args = parser.parse_args()
    directory = args.host / HOST_SAMPLES
    if not directory.is_dir():
        print(f'FAIL host directory not found: {directory}')
        return 2

    failed = 0
    expected = set()
    for sample in sorted((ROOT / 'samples').glob('pdf-*.js')):
        try:
            name = host_name(sample)
        except ValueError as e:
            print(f'FAIL {e}')
            failed += 1
            continue
        expected.add(name)
        copy = directory / name
        if not copy.is_file():
            print(f'FAIL {sample.name}: no host copy "{name}"')
            failed += 1
        elif copy.read_bytes() != sample.read_bytes():
            print(f'FAIL {sample.name}: "{name}" differs ({copy.stat().st_size} / {sample.stat().st_size} bytes)')
            failed += 1
        else:
            print(f'PASS {sample.name} = "{name}" ({sample.stat().st_size} bytes)')
    for extra in sorted(p.name for p in directory.glob('*.js') if p.name not in expected):
        print(f'FAIL "{extra}": no sample in samples/')
        failed += 1
    print(f'{len(expected)} samples, {failed} failed')
    return 1 if failed else 0


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    sys.exit(main())
