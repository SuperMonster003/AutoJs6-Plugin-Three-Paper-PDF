"""Generates the minimal PDF samples of samples/fixtures (roadmap P0.3, D39).

The samples are written by hand from the PDF 1.7 syntax with the standard library only: uncompressed text in the
standard Helvetica fonts, a small Deflate encoder, baseline JPEG encoders (colour and grey), the RC4 standard security
handler (revision 3) and a 5 x 7 block font for the scanned pages. Every computation is integer arithmetic and every
object, date and file identifier is fixed, so each run writes the same bytes on every platform; `--check` compares the
committed files with a fresh run. fixtures.json lists what each sample contains, for the device tests and later roadmap
items (P1.4 recognizes the text of scanned.pdf; for 1.1.0, P6.2, form.pdf lists its AcroForm fields and
scanned-multi.pdf is three pages of 300 dpi scans). JVM tests build their own documents with PDFBox at run time
(PdfFixtures).

zh-CN: 生成 samples/fixtures 的最小 PDF 样例 (路线图 P0.3, D39). 样例按 PDF 1.7 语法手写, 只用标准库: 标准 Helvetica 字体的
未压缩文本, 一个小型 Deflate 编码器, 基线 JPEG 编码器 (彩色与灰度), RC4 标准安全处理器 (修订版 3), 以及扫描页使用的 5 x 7
点阵字体. 全部计算都是整数运算, 所有对象, 日期与文件标识都是固定值, 因此在任何平台上每次运行都写出相同的字节; `--check`
用新生成的结果比对已提交的文件. fixtures.json 列出每个样例的内容, 供设备测试与后续路线图条目使用 (P1.4 识别 scanned.pdf 的
文字; 1.1.0 的 P6.2 起, form.pdf 列出其 AcroForm 字段, scanned-multi.pdf 为三页 300 dpi 扫描件). JVM 测试在运行时用 PDFBox
自行构建文档 (PdfFixtures).

Usage: py .python/generate_fixtures.py [--check]
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'samples' / 'fixtures'
MANIFEST = 'fixtures.json'

BACKSLASH = chr(92)
NEWLINE = b'\n'

AUTHOR = 'AutoJs6'
SUBJECT = '3-Paper PDF test fixture'
KEYWORDS = 'autojs6, pdf, fixture'
CREATOR = '.python/generate_fixtures.py'
PRODUCER = '3-Paper PDF fixtures'
DATE = 'D:20261112080000Z'
USER_PASSWORD = 'paper-user'
OWNER_PASSWORD = 'paper-owner'

A4 = (595, 842)
LETTER_LANDSCAPE = (792, 612)


# region PDF objects

class Name(str):
    """A PDF name object. zh-CN: PDF 名称对象."""


class Ref(int):
    """An indirect reference to object number `int(self)`, generation 0. zh-CN: 指向对象号 `int(self)` (代号 0) 的间接引用."""


class Raw(str):
    """A token written as is, such as a decimal number. zh-CN: 原样写出的记号, 例如小数."""


def literal(text: str) -> bytes:
    escaped = text.replace(BACKSLASH, BACKSLASH * 2).replace('(', BACKSLASH + '(').replace(')', BACKSLASH + ')')
    return b'(' + escaped.encode('latin-1') + b')'


def serialize(value, crypt=None) -> bytes:
    """Writes a PDF value; strings of encrypted objects pass through `crypt` and become hex strings.
    zh-CN: 写出 PDF 值; 加密对象中的字符串经 `crypt` 处理后写成十六进制字符串."""
    if isinstance(value, bool):
        return b'true' if value else b'false'
    if value is None:
        return b'null'
    if isinstance(value, Name):
        return b'/' + value.encode('ascii')
    if isinstance(value, Ref):
        return f'{int(value)} 0 R'.encode('ascii')
    if isinstance(value, Raw):
        return value.encode('ascii')
    if isinstance(value, int):
        return str(value).encode('ascii')
    if isinstance(value, bytes):
        return b'<' + value.hex().upper().encode('ascii') + b'>'
    if isinstance(value, str):
        if crypt is None:
            return literal(value)
        return b'<' + crypt(value.encode('latin-1')).hex().upper().encode('ascii') + b'>'
    if isinstance(value, list):
        return b'[' + b' '.join(serialize(item, crypt) for item in value) + b']'
    if isinstance(value, dict):
        return b'<< ' + b' '.join(b'/' + key.encode('ascii') + b' ' + serialize(item, crypt) for key, item in value.items()) + b' >>'
    raise TypeError(f'cannot write {value!r}')


class Stream:
    def __init__(self, dictionary: dict, data: bytes):
        self.dictionary = dictionary
        self.data = data


class Document:
    """Collects numbered objects and writes the file with its cross-reference table.
    zh-CN: 收集编号对象, 写出文件及其交叉引用表."""

    def __init__(self, name: str):
        self.objects: list = []
        self.file_id = hashlib.md5(f'three-paper-pdf/{name}'.encode('ascii')).digest()
        self.security: Security | None = None

    def reserve(self) -> Ref:
        self.objects.append(None)
        return Ref(len(self.objects))

    def put(self, ref: Ref, value) -> Ref:
        self.objects[ref - 1] = value
        return ref

    def add(self, value) -> Ref:
        return self.put(self.reserve(), value)

    def build(self, root: Ref, info: Ref) -> bytes:
        out = bytearray(b'%PDF-1.7' + NEWLINE + b'%' + bytes([0xE2, 0xE3, 0xCF, 0xD3]) + NEWLINE)
        offsets = []
        encrypt_ref = self.add(self.security.dictionary()) if self.security else None
        for number, value in enumerate(self.objects, 1):
            if value is None:
                raise ValueError(f'object {number} was reserved but never written')
            offsets.append(len(out))
            crypt = None
            if self.security and number != encrypt_ref:
                crypt = self.security.crypt_for(number)
            out += f'{number} 0 obj'.encode('ascii') + NEWLINE
            if isinstance(value, Stream):
                data = crypt(value.data) if crypt else value.data
                out += serialize({**value.dictionary, 'Length': len(data)}, crypt) + NEWLINE + b'stream' + NEWLINE
                out += data + NEWLINE + b'endstream'
            else:
                out += serialize(value, crypt)
            out += NEWLINE + b'endobj' + NEWLINE
        xref = len(out)
        out += f'xref'.encode('ascii') + NEWLINE + f'0 {len(self.objects) + 1}'.encode('ascii') + NEWLINE
        out += b'0000000000 65535 f ' + NEWLINE
        for offset in offsets:
            out += f'{offset:010d} 00000 n '.encode('ascii') + NEWLINE
        trailer = {'Size': len(self.objects) + 1, 'Root': root, 'Info': info, 'ID': [self.file_id, self.file_id]}
        if encrypt_ref:
            trailer['Encrypt'] = encrypt_ref
        out += b'trailer' + NEWLINE + serialize(trailer) + NEWLINE
        out += b'startxref' + NEWLINE + str(xref).encode('ascii') + NEWLINE + b'%%EOF' + NEWLINE
        return bytes(out)

# endregion

# region Deflate

LENGTH_BASE = [3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258]
LENGTH_EXTRA = [0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0]
DISTANCE_BASE = [1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097,
                 6145, 8193, 12289, 16385, 24577]
DISTANCE_EXTRA = [0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13]


class DeflateBits:
    """Packs bits least significant first; Huffman codes go most significant first (RFC 1951 3.1.1).
    zh-CN: 按低位在前打包比特; Huffman 码按高位在前写入 (RFC 1951 3.1.1)."""

    def __init__(self):
        self.out = bytearray()
        self.value = 0
        self.count = 0

    def bits(self, value: int, count: int):
        self.value |= value << self.count
        self.count += count
        while self.count >= 8:
            self.out.append(self.value & 0xFF)
            self.value >>= 8
            self.count -= 8

    def code(self, code: int, length: int):
        self.bits(int(format(code, f'0{length}b')[::-1], 2), length)

    def literal_length(self, symbol: int):
        if symbol < 144:
            self.code(0x30 + symbol, 8)
        elif symbol < 256:
            self.code(0x190 + symbol - 144, 9)
        elif symbol < 280:
            self.code(symbol - 256, 7)
        else:
            self.code(0xC0 + symbol - 280, 8)

    def finish(self) -> bytes:
        if self.count:
            self.out.append(self.value & 0xFF)
        return bytes(self.out)


def deflate(data: bytes) -> bytes:
    """A zlib stream with one fixed Huffman block and greedy LZ77 matches. Unlike the zlib module, whose output depends
    on the library build, the bytes depend on this code only.
    zh-CN: 由一个固定 Huffman 块与贪心 LZ77 匹配组成的 zlib 流. zlib 模块的输出随库的构建而变, 这里的字节只取决于本代码."""
    bits = DeflateBits()
    bits.bits(1, 1)  # BFINAL
    bits.bits(1, 2)  # BTYPE 01: fixed Huffman codes. zh-CN: BTYPE 01: 固定 Huffman 码.
    last_seen: dict[bytes, int] = {}
    size = len(data)
    position = 0
    while position < size:
        length = 0
        if position + 3 <= size:
            key = data[position:position + 3]
            previous = last_seen.get(key)
            last_seen[key] = position
            if previous is not None and position - previous <= 32768:
                limit = min(258, size - position)
                if data[previous:previous + limit] == data[position:position + limit]:
                    length = limit
                else:
                    length = 3
                    while length < limit and data[previous + length] == data[position + length]:
                        length += 1
                distance = position - previous
        if length >= 3:
            index = max(i for i, base in enumerate(LENGTH_BASE) if base <= length)
            bits.literal_length(257 + index)
            bits.bits(length - LENGTH_BASE[index], LENGTH_EXTRA[index])
            index = max(i for i, base in enumerate(DISTANCE_BASE) if base <= distance)
            bits.code(index, 5)
            bits.bits(distance - DISTANCE_BASE[index], DISTANCE_EXTRA[index])
            position += length
            # Remembering the last matched byte lets a run continue at distance 1, the cheapest code.
            # zh-CN: 记住最后一个匹配字节, 使连续的相同字节能以距离 1 (最便宜的编码) 继续匹配.
            last_seen[data[position - 1:position + 2]] = position - 1
        else:
            bits.literal_length(data[position])
            position += 1
    bits.literal_length(256)
    # CMF 0x78 (Deflate, 32 KiB window) and FLG 0x01 make a header divisible by 31.
    # zh-CN: CMF 0x78 (Deflate, 32 KiB 窗口) 与 FLG 0x01 组成可被 31 整除的头部.
    stream = bytes([0x78, 0x01]) + bits.finish() + struct.pack('>I', zlib.adler32(data))
    if zlib.decompress(stream) != data:
        raise AssertionError('Deflate round trip failed')
    return stream

# endregion

# region JPEG

def _zigzag() -> list[int]:
    cells = sorted((x + y, x if (x + y) % 2 == 0 else y, y * 8 + x) for y in range(8) for x in range(8))
    return [cell[2] for cell in cells]


ZIGZAG = _zigzag()

# ITU T.81 Annex K.1 quantization tables in natural order. zh-CN: ITU T.81 附录 K.1 的量化表 (自然顺序).
LUMA_QUANT = [
    16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55, 14, 13, 16, 24, 40, 57, 69, 56,
    14, 17, 22, 29, 51, 87, 80, 62, 18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92,
    49, 64, 78, 87, 103, 121, 120, 101, 72, 92, 95, 98, 112, 100, 103, 99,
]
CHROMA_QUANT = [
    17, 18, 24, 47, 99, 99, 99, 99, 18, 21, 26, 66, 99, 99, 99, 99, 24, 26, 56, 99, 99, 99, 99, 99,
    47, 66, 99, 99, 99, 99, 99, 99,
] + [99] * 32

# ITU T.81 Annex K.3 Huffman tables. zh-CN: ITU T.81 附录 K.3 的 Huffman 表.
DC_LUMA = ([0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0], list(range(12)))
DC_CHROMA = ([0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0], list(range(12)))
AC_LUMA = ([0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7D], [
    0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12, 0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07,
    0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xA1, 0x08, 0x23, 0x42, 0xB1, 0xC1, 0x15, 0x52, 0xD1, 0xF0,
    0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0A, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x25, 0x26, 0x27, 0x28,
    0x29, 0x2A, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
    0x4A, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5A, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69,
    0x6A, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7A, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
    0x8A, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9A, 0xA2, 0xA3, 0xA4, 0xA5, 0xA6, 0xA7,
    0xA8, 0xA9, 0xAA, 0xB2, 0xB3, 0xB4, 0xB5, 0xB6, 0xB7, 0xB8, 0xB9, 0xBA, 0xC2, 0xC3, 0xC4, 0xC5,
    0xC6, 0xC7, 0xC8, 0xC9, 0xCA, 0xD2, 0xD3, 0xD4, 0xD5, 0xD6, 0xD7, 0xD8, 0xD9, 0xDA, 0xE1, 0xE2,
    0xE3, 0xE4, 0xE5, 0xE6, 0xE7, 0xE8, 0xE9, 0xEA, 0xF1, 0xF2, 0xF3, 0xF4, 0xF5, 0xF6, 0xF7, 0xF8,
    0xF9, 0xFA,
])
AC_CHROMA = ([0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 0x77], [
    0x00, 0x01, 0x02, 0x03, 0x11, 0x04, 0x05, 0x21, 0x31, 0x06, 0x12, 0x41, 0x51, 0x07, 0x61, 0x71,
    0x13, 0x22, 0x32, 0x81, 0x08, 0x14, 0x42, 0x91, 0xA1, 0xB1, 0xC1, 0x09, 0x23, 0x33, 0x52, 0xF0,
    0x15, 0x62, 0x72, 0xD1, 0x0A, 0x16, 0x24, 0x34, 0xE1, 0x25, 0xF1, 0x17, 0x18, 0x19, 0x1A, 0x26,
    0x27, 0x28, 0x29, 0x2A, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48,
    0x49, 0x4A, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5A, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
    0x69, 0x6A, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7A, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87,
    0x88, 0x89, 0x8A, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9A, 0xA2, 0xA3, 0xA4, 0xA5,
    0xA6, 0xA7, 0xA8, 0xA9, 0xAA, 0xB2, 0xB3, 0xB4, 0xB5, 0xB6, 0xB7, 0xB8, 0xB9, 0xBA, 0xC2, 0xC3,
    0xC4, 0xC5, 0xC6, 0xC7, 0xC8, 0xC9, 0xCA, 0xD2, 0xD3, 0xD4, 0xD5, 0xD6, 0xD7, 0xD8, 0xD9, 0xDA,
    0xE2, 0xE3, 0xE4, 0xE5, 0xE6, 0xE7, 0xE8, 0xE9, 0xEA, 0xF2, 0xF3, 0xF4, 0xF5, 0xF6, 0xF7, 0xF8,
    0xF9, 0xFA,
])

# The AC tables must assign every baseline symbol exactly once: end of block, sixteen zeros and run / size pairs.
# zh-CN: AC 表必须恰好为每个基线符号分配一次编码: 块结束, 十六个零, 以及游程 / 尺寸组合.
_AC_SYMBOLS = {0x00, 0xF0} | {(run << 4) | size for run in range(16) for size in range(1, 11)}
for _bits, _values in (AC_LUMA, AC_CHROMA):
    if sum(_bits) != len(_values) or set(_values) != _AC_SYMBOLS or len(_values) != len(_AC_SYMBOLS):
        raise AssertionError('malformed AC Huffman table')

# Integer DCT basis: round(C(u) * cos((2x + 1) u pi / 16) * 4096), C(0) = 1 / sqrt(2); no value lies near a rounding
# boundary, so the table is the same on every platform.
# zh-CN: 整数 DCT 基: round(C(u) * cos((2x + 1) u pi / 16) * 4096), C(0) = 1 / sqrt(2); 没有取值接近舍入边界, 因此各平台上
# 的表都相同.
DCT_BASIS = [[round((math.sqrt(0.5) if u == 0 else 1.0) * math.cos((2 * x + 1) * u * math.pi / 16) * 4096) for x in range(8)]
             for u in range(8)]


def _scaled(table: list[int], quality: int) -> list[int]:
    scale = 5000 // quality if quality < 50 else 200 - 2 * quality
    return [max(1, min(255, (value * scale + 50) // 100)) for value in table]


def _huffman_codes(table) -> dict[int, tuple[int, int]]:
    counts, values = table
    codes = {}
    code = 0
    index = 0
    for length in range(1, 17):
        for _ in range(counts[length - 1]):
            codes[values[index]] = (code, length)
            code += 1
            index += 1
        code <<= 1
    return codes


def _round_divide(numerator: int, denominator: int) -> int:
    magnitude = (2 * abs(numerator) + denominator) // (2 * denominator)
    return -magnitude if numerator < 0 else magnitude


class JpegBits:
    """Most significant bit first, with a zero byte stuffed after every 0xFF. zh-CN: 高位在前, 每个 0xFF 之后填充一个零字节."""

    def __init__(self):
        self.out = bytearray()
        self.value = 0
        self.count = 0

    def write(self, value: int, length: int):
        for shift in range(length - 1, -1, -1):
            self.value = (self.value << 1) | ((value >> shift) & 1)
            self.count += 1
            if self.count == 8:
                self.out.append(self.value)
                if self.value == 0xFF:
                    self.out.append(0)
                self.value = 0
                self.count = 0

    def finish(self) -> bytes:
        if self.count:
            self.write((1 << (8 - self.count)) - 1, 8 - self.count)
        return bytes(self.out)


def _segment(marker: int, payload: bytes) -> bytes:
    return bytes([0xFF, marker]) + struct.pack('>H', len(payload) + 2) + payload


def jpeg(width: int, height: int, pixels: list[tuple[int, int, int]], quality: int = 85) -> bytes:
    """A baseline JFIF file, 4:4:4 YCbCr, standard Huffman tables. zh-CN: 基线 JFIF 文件, 4:4:4 YCbCr, 标准 Huffman 表."""
    planes = [[0] * (width * height) for _ in range(3)]
    for index, (r, g, b) in enumerate(pixels):
        planes[0][index] = (19595 * r + 38470 * g + 7471 * b + 32768) >> 16
        planes[1][index] = max(0, min(255, ((-11059 * r - 21709 * g + 32768 * b + 32768) >> 16) + 128))
        planes[2][index] = max(0, min(255, ((32768 * r - 27439 * g - 5329 * b + 32768) >> 16) + 128))
    quantizers = [_scaled(LUMA_QUANT, quality), _scaled(CHROMA_QUANT, quality)]
    dc_codes = [_huffman_codes(DC_LUMA), _huffman_codes(DC_CHROMA)]
    ac_codes = [_huffman_codes(AC_LUMA), _huffman_codes(AC_CHROMA)]
    bits = JpegBits()
    predictions = [0, 0, 0]

    def amplitude(value: int) -> tuple[int, int]:
        size = abs(value).bit_length()
        return (value if value >= 0 else value + (1 << size) - 1), size

    for block_y in range(0, height, 8):
        for block_x in range(0, width, 8):
            for component in range(3):
                table = 0 if component == 0 else 1
                plane = planes[component]
                samples = [plane[min(block_y + y, height - 1) * width + min(block_x + x, width - 1)] - 128
                           for y in range(8) for x in range(8)]
                rows = [[sum(samples[y * 8 + x] * DCT_BASIS[u][x] for x in range(8)) for u in range(8)] for y in range(8)]
                coefficients = [0] * 64
                for v in range(8):
                    for u in range(8):
                        total = sum(rows[y][u] * DCT_BASIS[v][y] for y in range(8))
                        # F(u, v) = total / (4 * 4096 * 4096), then divided by the quantizer.
                        # zh-CN: F(u, v) = total / (4 * 4096 * 4096), 再除以量化值.
                        coefficients[v * 8 + u] = _round_divide(total, 4 * 4096 * 4096 * quantizers[table][v * 8 + u])
                ordered = [coefficients[index] for index in ZIGZAG]
                value, size = amplitude(ordered[0] - predictions[component])
                predictions[component] = ordered[0]
                bits.write(*dc_codes[table][size])
                bits.write(value, size)
                run = 0
                for coefficient in ordered[1:]:
                    if coefficient == 0:
                        run += 1
                        continue
                    while run > 15:
                        bits.write(*ac_codes[table][0xF0])
                        run -= 16
                    value, size = amplitude(coefficient)
                    bits.write(*ac_codes[table][(run << 4) | size])
                    bits.write(value, size)
                    run = 0
                if run:
                    bits.write(*ac_codes[table][0x00])

    out = bytearray([0xFF, 0xD8])
    out += _segment(0xE0, b'JFIF' + bytes([0, 1, 1, 0, 0, 1, 0, 1, 0, 0]))
    out += _segment(0xDB, b''.join(bytes([index]) + bytes(quantizers[index][z] for z in ZIGZAG) for index in range(2)))
    out += _segment(0xC0, bytes([8]) + struct.pack('>HH', height, width) + bytes([3, 1, 0x11, 0, 2, 0x11, 1, 3, 0x11, 1]))
    for table_class, table in ((0x00, DC_LUMA), (0x10, AC_LUMA), (0x01, DC_CHROMA), (0x11, AC_CHROMA)):
        out += _segment(0xC4, bytes([table_class]) + bytes(table[0]) + bytes(table[1]))
    out += _segment(0xDA, bytes([3, 1, 0x00, 2, 0x11, 3, 0x11, 0, 63, 0]))
    out += bits.finish()
    out += bytes([0xFF, 0xD9])
    return bytes(out)


def _block_coefficients(samples: list[int], quantizer: list[int]) -> list[int]:
    """Quantized DCT coefficients of one 8 x 8 block in natural order, as `jpeg` computes them.
    zh-CN: 一个 8 x 8 块的量化 DCT 系数 (自然顺序), 算法与 `jpeg` 相同."""
    rows = [[sum(samples[y * 8 + x] * DCT_BASIS[u][x] for x in range(8)) for u in range(8)] for y in range(8)]
    coefficients = [0] * 64
    for v in range(8):
        for u in range(8):
            total = sum(rows[y][u] * DCT_BASIS[v][y] for y in range(8))
            coefficients[v * 8 + u] = _round_divide(total, 4 * 4096 * 4096 * quantizer[v * 8 + u])
    return coefficients


def _uniform_coefficients(sample: int, quantizer: list[int]) -> list[int]:
    """The coefficients of a block whose 64 samples all equal `sample`: the AC terms of the integer basis cancel
    exactly (its rows are symmetric or antisymmetric), so only the DC term remains.
    zh-CN: 64 个样本都等于 `sample` 的块的系数: 整数基的各行对称或反对称, AC 项精确抵消, 只剩 DC 项."""
    total = 64 * DCT_BASIS[0][0] * DCT_BASIS[0][0] * sample
    return [_round_divide(total, 4 * 4096 * 4096 * quantizer[0])] + [0] * 63


# The fast path must agree with the general one. zh-CN: 快速路径必须与一般算法一致.
for _sample in (-128, -104, 0, 120, 127):
    if _uniform_coefficients(_sample, _scaled(LUMA_QUANT, 85)) != _block_coefficients([_sample] * 64, _scaled(LUMA_QUANT, 85)):
        raise AssertionError('uniform block fast path differs')


def gray_jpeg(width: int, height: int, pixels: bytes, quality: int = 85) -> bytes:
    """A baseline JFIF file with one grey component and the standard luminance tables; uniform blocks (all of a page
    drawn on the 8 pixel grid) take the exact fast path.
    zh-CN: 单个灰度分量, 使用标准亮度表的基线 JFIF 文件; 均匀的块 (按 8 像素网格绘制的页面全部如此) 走精确的快速路径."""
    quantizer = _scaled(LUMA_QUANT, quality)
    dc_codes = _huffman_codes(DC_LUMA)
    ac_codes = _huffman_codes(AC_LUMA)
    bits = JpegBits()
    prediction = 0

    def amplitude(value: int) -> tuple[int, int]:
        size = abs(value).bit_length()
        return (value if value >= 0 else value + (1 << size) - 1), size

    for block_y in range(0, height, 8):
        rows = [min(block_y + y, height - 1) * width for y in range(8)]
        for block_x in range(0, width, 8):
            if block_x + 8 <= width:
                lines = [pixels[row + block_x:row + block_x + 8] for row in rows]
            else:
                lines = [bytes(pixels[row + min(block_x + x, width - 1)] for x in range(8)) for row in rows]
            first = lines[0]
            if first.count(first[0]) == 8 and all(line == first for line in lines):
                coefficients = _uniform_coefficients(first[0] - 128, quantizer)
            else:
                coefficients = _block_coefficients([value - 128 for line in lines for value in line], quantizer)
            ordered = [coefficients[index] for index in ZIGZAG]
            value, size = amplitude(ordered[0] - prediction)
            prediction = ordered[0]
            bits.write(*dc_codes[size])
            bits.write(value, size)
            run = 0
            for coefficient in ordered[1:]:
                if coefficient == 0:
                    run += 1
                    continue
                while run > 15:
                    bits.write(*ac_codes[0xF0])
                    run -= 16
                value, size = amplitude(coefficient)
                bits.write(*ac_codes[(run << 4) | size])
                bits.write(value, size)
                run = 0
            if run:
                bits.write(*ac_codes[0x00])

    out = bytearray([0xFF, 0xD8])
    out += _segment(0xE0, b'JFIF' + bytes([0, 1, 1, 0, 0, 1, 0, 1, 0, 0]))
    out += _segment(0xDB, bytes([0]) + bytes(quantizer[z] for z in ZIGZAG))
    out += _segment(0xC0, bytes([8]) + struct.pack('>HH', height, width) + bytes([1, 1, 0x11, 0]))
    for table_class, table in ((0x00, DC_LUMA), (0x10, AC_LUMA)):
        out += _segment(0xC4, bytes([table_class]) + bytes(table[0]) + bytes(table[1]))
    out += _segment(0xDA, bytes([1, 1, 0x00, 0, 63, 0]))
    out += bits.finish()
    out += bytes([0xFF, 0xD9])
    return bytes(out)

# endregion

# region Standard security handler (RC4, revision 3)

PASSWORD_PADDING = bytes.fromhex('28BF4E5E4E758A4164004E56FFFA01082E2E00B6D0683E802F0CA9FE6453697A')


def rc4(key: bytes, data: bytes) -> bytes:
    state = list(range(256))
    j = 0
    for i in range(256):
        j = (j + state[i] + key[i % len(key)]) & 0xFF
        state[i], state[j] = state[j], state[i]
    out = bytearray()
    i = j = 0
    for byte in data:
        i = (i + 1) & 0xFF
        j = (j + state[i]) & 0xFF
        state[i], state[j] = state[j], state[i]
        out.append(byte ^ state[(state[i] + state[j]) & 0xFF])
    return bytes(out)


def _padded(password: str) -> bytes:
    return (password.encode('latin-1') + PASSWORD_PADDING)[:32]


def _rounds(key: bytes, data: bytes) -> bytes:
    for round_index in range(20):
        data = rc4(bytes(byte ^ round_index for byte in key), data)
    return data


class Security:
    """ISO 32000-1 7.6.3 algorithms 2, 3 and 5 with a 128-bit key. zh-CN: ISO 32000-1 7.6.3 的算法 2, 3, 5, 128 位密钥."""

    KEY_BYTES = 16
    # Print only: bit 3, the reserved bits 7 and 8 and bits 13 to 32 (ISO 32000-1 table 22).
    # zh-CN: 只允许打印: 第 3 位, 保留的第 7, 8 位与第 13 到 32 位 (ISO 32000-1 表 22).
    PERMISSIONS = struct.unpack('<i', struct.pack('<I', 0xFFFFF000 | 0xC0 | 0x04))[0]

    def __init__(self, file_id: bytes, user: str, owner: str):
        digest = hashlib.md5(_padded(owner)).digest()
        for _ in range(50):
            digest = hashlib.md5(digest[:self.KEY_BYTES]).digest()
        self.owner_entry = _rounds(digest[:self.KEY_BYTES], _padded(user))
        digest = hashlib.md5(_padded(user) + self.owner_entry + struct.pack('<i', self.PERMISSIONS) + file_id).digest()
        for _ in range(50):
            digest = hashlib.md5(digest[:self.KEY_BYTES]).digest()
        self.key = digest[:self.KEY_BYTES]
        self.user_entry = _rounds(self.key, hashlib.md5(PASSWORD_PADDING + file_id).digest()) + bytes(16)

    def dictionary(self) -> dict:
        return {
            'Filter': Name('Standard'), 'V': 2, 'R': 3, 'Length': self.KEY_BYTES * 8,
            'O': self.owner_entry, 'U': self.user_entry, 'P': self.PERMISSIONS,
        }

    def crypt_for(self, number: int):
        object_key = hashlib.md5(self.key + number.to_bytes(3, 'little') + bytes(2)).digest()[:min(self.KEY_BYTES + 5, 16)]
        return lambda data: rc4(object_key, data)

# endregion

# region Block font for the scanned page

GLYPHS = {
    ' ': ('.....',) * 7,
    '0': ('.###.', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'),
    '1': ('..#..', '.##..', '..#..', '..#..', '..#..', '..#..', '.###.'),
    '2': ('.###.', '#...#', '....#', '...#.', '..#..', '.#...', '#####'),
    '3': ('####.', '....#', '....#', '.###.', '....#', '....#', '####.'),
    '6': ('..##.', '.#...', '#....', '####.', '#...#', '#...#', '.###.'),
    'A': ('.###.', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'),
    'C': ('.###.', '#...#', '#....', '#....', '#....', '#...#', '.###.'),
    'D': ('####.', '#...#', '#...#', '#...#', '#...#', '#...#', '####.'),
    'E': ('#####', '#....', '#....', '####.', '#....', '#....', '#####'),
    'F': ('#####', '#....', '#....', '####.', '#....', '#....', '#....'),
    'G': ('.###.', '#...#', '#....', '#.###', '#...#', '#...#', '.####'),
    'H': ('#...#', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'),
    'L': ('#....', '#....', '#....', '#....', '#....', '#....', '#####'),
    'N': ('#...#', '##..#', '##..#', '#.#.#', '#..##', '#..##', '#...#'),
    'O': ('.###.', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'),
    'P': ('####.', '#...#', '#...#', '####.', '#....', '#....', '#....'),
    'R': ('####.', '#...#', '#...#', '####.', '#.#..', '#..#.', '#...#'),
    'S': ('.####', '#....', '#....', '.###.', '....#', '....#', '####.'),
    'W': ('#...#', '#...#', '#...#', '#.#.#', '#.#.#', '#.#.#', '.#.#.'),
}

SCAN_SIZE = (1240, 1754)  # A4 at 150 dpi. zh-CN: A4, 150 dpi.
SCAN_DOT = 10
SCAN_ORIGIN = (120, 160)
SCAN_LINE_DOTS = 11
SCAN_PAPER = 248
SCAN_INK = 24
SCAN_LINES = ['SCANNED PAGE', 'PAPER PDF', 'HELLO WORLD', '2026']


def scanned_pixels() -> bytes:
    width, height = SCAN_SIZE
    pixels = bytearray([SCAN_PAPER]) * (width * height)
    for line_index, line in enumerate(SCAN_LINES):
        top = SCAN_ORIGIN[1] + line_index * SCAN_LINE_DOTS * SCAN_DOT
        for char_index, char in enumerate(line):
            left = SCAN_ORIGIN[0] + char_index * 6 * SCAN_DOT
            for row, pattern in enumerate(GLYPHS[char]):
                for column, cell in enumerate(pattern):
                    if cell != '#':
                        continue
                    for y in range(top + row * SCAN_DOT, top + (row + 1) * SCAN_DOT):
                        start = y * width + left + column * SCAN_DOT
                        pixels[start:start + SCAN_DOT] = bytes([SCAN_INK]) * SCAN_DOT
    return bytes(pixels)

# endregion

# region Samples


def _information(document: Document, title: str) -> Ref:
    return document.add({
        'Title': title, 'Author': AUTHOR, 'Subject': SUBJECT, 'Keywords': KEYWORDS,
        'Creator': CREATOR, 'Producer': PRODUCER, 'CreationDate': DATE, 'ModDate': DATE,
    })


def _fonts(document: Document) -> dict:
    regular = document.add({'Type': Name('Font'), 'Subtype': Name('Type1'), 'BaseFont': Name('Helvetica'),
                            'Encoding': Name('WinAnsiEncoding')})
    bold = document.add({'Type': Name('Font'), 'Subtype': Name('Type1'), 'BaseFont': Name('Helvetica-Bold'),
                         'Encoding': Name('WinAnsiEncoding')})
    return {'F1': regular, 'F2': bold}


def _text(lines: list[str], top: int) -> bytes:
    """The first line is an 18 pt bold heading, the rest 12 pt regular lines. zh-CN: 第一行为 18 pt 粗体标题, 其余为 12 pt 常规行."""
    parts = [b'BT /F2 18 Tf 72 ' + str(top).encode('ascii') + b' Td ' + literal(lines[0]) + b' Tj /F1 12 Tf']
    for index, line in enumerate(lines[1:]):
        parts.append((b'0 -28 Td ' if index == 0 else b'0 -16 Td ') + literal(line) + b' Tj')
    parts.append(b'ET')
    return NEWLINE.join(parts) + NEWLINE


class Pages:
    def __init__(self, document: Document):
        self.document = document
        self.ref = document.reserve()
        self.kids: list[Ref] = []

    def add(self, content: bytes, resources: dict, size=A4, rotate: int = 0, compress: bool = False,
            annotations: list[Ref] | None = None) -> Ref:
        dictionary = {'Filter': Name('FlateDecode')} if compress else {}
        stream = self.document.add(Stream(dictionary, deflate(content) if compress else content))
        page = {'Type': Name('Page'), 'Parent': self.ref, 'MediaBox': [0, 0, size[0], size[1]], 'Resources': resources,
                'Contents': stream}
        if rotate:
            page['Rotate'] = rotate
        if annotations:
            page['Annots'] = list(annotations)
        self.kids.append(self.document.add(page))
        return self.kids[-1]

    def finish(self) -> Ref:
        return self.document.put(self.ref, {'Type': Name('Pages'), 'Kids': list(self.kids), 'Count': len(self.kids)})


def _catalog(document: Document, pages: Pages, **extra) -> Ref:
    return document.add({'Type': Name('Catalog'), 'Pages': pages.finish(), **extra})


def text_sample() -> tuple[bytes, dict]:
    lines = [
        'Text sample',
        'The quick brown fox jumps over the lazy dog.',
        'Numbers: 0123456789',
        'Symbols: (parentheses) [brackets] {braces} ' + BACKSLASH + ' backslash',
        'Search target: needle',
        'Last line of the page.',
    ]
    document = Document('text.pdf')
    info = _information(document, 'Text sample')
    fonts = _fonts(document)
    pages = Pages(document)
    pages.add(_text(lines, 770), {'Font': fonts})
    return document.build(_catalog(document, pages), info), {
        'title': 'Text sample', 'pages': 1, 'pageSize': list(A4), 'text': [lines],
        'notes': 'Helvetica and Helvetica-Bold, not embedded, WinAnsiEncoding; the content stream is uncompressed.',
    }


def outline_sample() -> tuple[bytes, dict]:
    document = Document('outline.pdf')
    info = _information(document, 'Outline sample')
    fonts = _fonts(document)
    pages = Pages(document)
    text = []
    for number in range(1, 7):
        chapter = (number + 1) // 2
        lines = [f'Chapter {chapter}' if number % 2 else f'Chapter {chapter}, section 1', f'Page {number} of 6']
        if number in (2, 5):
            lines.append('The needle is on this page.')
        text.append(lines)
        pages.add(_text(lines, 770), {'Font': fonts}, compress=True)

    root = document.reserve()
    chapters = [document.reserve() for _ in range(3)]
    sections = [document.reserve() for _ in range(3)]
    notes = document.reserve()
    top_level = chapters + [notes]
    outline = []
    for index, chapter in enumerate(chapters):
        item = {'Title': f'Chapter {index + 1}', 'Parent': root}
        if index > 0:
            item['Prev'] = top_level[index - 1]
        item['Next'] = top_level[index + 1]
        item['First'] = sections[index]
        item['Last'] = sections[index]
        item['Count'] = 1
        start = pages.kids[index * 2]
        if index == 2:
            # Chapter 3 uses a GoTo action instead of a destination. zh-CN: 第 3 章用 GoTo 动作代替目标.
            item['A'] = {'S': Name('GoTo'), 'D': [start, Name('Fit')]}
        else:
            item['Dest'] = [start, Name('Fit')]
        document.put(chapter, item)
        document.put(sections[index], {
            'Title': f'Chapter {index + 1}, section 1', 'Parent': chapter,
            'Dest': [pages.kids[index * 2 + 1], Name('XYZ'), 72, 770, 0],
        })
        outline.append({'title': f'Chapter {index + 1}', 'page': index * 2 + 1, 'action': index == 2,
                        'children': [{'title': f'Chapter {index + 1}, section 1', 'page': index * 2 + 2, 'children': []}]})
    # An entry without a destination, such as a heading of the outline. zh-CN: 没有目标的条目, 例如大纲中的标题.
    document.put(notes, {'Title': 'Notes', 'Parent': root, 'Prev': chapters[-1]})
    outline.append({'title': 'Notes', 'page': None, 'children': []})
    document.put(root, {'Type': Name('Outlines'), 'First': chapters[0], 'Last': notes, 'Count': 7})
    catalog = _catalog(document, pages, Outlines=root, PageMode=Name('UseOutlines'))
    return document.build(catalog, info), {
        'title': 'Outline sample', 'pages': 6, 'pageSize': list(A4), 'text': text, 'outline': outline,
        'search': {'needle': [2, 5]},
        'notes': 'Chapter items are open and use /Fit destinations (chapter 3 a GoTo action); sections use /XYZ; '
                 'content streams are FlateDecode.',
    }


def encrypted_sample() -> tuple[bytes, dict]:
    document = Document('encrypted.pdf')
    document.security = Security(document.file_id, USER_PASSWORD, OWNER_PASSWORD)
    info = _information(document, 'Encrypted sample')
    fonts = _fonts(document)
    pages = Pages(document)
    text = [['Encrypted sample', 'Page 1 of 2', 'Opened with the user or the owner password.'], ['Page 2', 'Page 2 of 2']]
    for lines in text:
        pages.add(_text(lines, 770), {'Font': fonts})
    return document.build(_catalog(document, pages), info), {
        'title': 'Encrypted sample', 'pages': 2, 'pageSize': list(A4), 'text': text,
        'userPassword': USER_PASSWORD, 'ownerPassword': OWNER_PASSWORD,
        'encryption': {'filter': 'Standard', 'v': 2, 'r': 3, 'keyLength': 128, 'algorithm': 'RC4'},
        'permissions': {'p': Security.PERMISSIONS, 'print': True, 'printFaithful': False, 'modify': False,
                        'extractContent': False, 'modifyAnnotations': False, 'fillInForm': False,
                        'extractForAccessibility': False, 'assemble': False},
        'notes': 'The user password allows printing only; the owner password grants every permission.',
    }


def scanned_sample() -> tuple[bytes, dict]:
    document = Document('scanned.pdf')
    info = _information(document, 'Scanned sample')
    width, height = SCAN_SIZE
    pixels = scanned_pixels()
    image = document.add(Stream({
        'Type': Name('XObject'), 'Subtype': Name('Image'), 'Width': width, 'Height': height,
        'ColorSpace': Name('DeviceGray'), 'BitsPerComponent': 8, 'Filter': Name('FlateDecode'),
    }, deflate(pixels)))
    ink = [index for index, value in enumerate(pixels) if value == SCAN_INK]
    rows = [index // width for index in ink]
    columns = [index % width for index in ink]
    pages = Pages(document)
    content = f'q {A4[0]} 0 0 {A4[1]} 0 0 cm /Im1 Do Q'.encode('ascii') + NEWLINE
    pages.add(content, {'XObject': {'Im1': image}})
    return document.build(_catalog(document, pages), info), {
        'title': 'Scanned sample', 'pages': 1, 'pageSize': list(A4), 'text': [[]],
        'image': {'name': 'Im1', 'width': width, 'height': height, 'colorSpace': 'DeviceGray', 'bitsPerComponent': 8,
                  'filter': 'FlateDecode', 'paper': SCAN_PAPER, 'ink': SCAN_INK, 'inkPixels': len(ink),
                  # Left, top, right, bottom in image pixels, right and bottom exclusive. zh-CN: 左, 上, 右, 下 (图像像素, 右与下不含).
                  'inkBox': [min(columns), min(rows), max(columns) + 1, max(rows) + 1]},
        'ocrText': SCAN_LINES,
        'glyphs': {'dot': SCAN_DOT, 'origin': list(SCAN_ORIGIN), 'advanceDots': 6, 'lineDots': SCAN_LINE_DOTS,
                   'font': '5 x 7 block capitals'},
        'notes': 'An image of block capitals filling the page at 150 dpi and no text layer, for OCR (roadmap P1.4).',
    }


JPEG_SIZE = (160, 120)
RGBA_SIZE = (64, 64)


def jpeg_pixels() -> list[tuple[int, int, int]]:
    """A blue to orange gradient with a white square in the middle. zh-CN: 蓝到橙的渐变, 中间一个白色方块."""
    width, height = JPEG_SIZE
    pixels = []
    for y in range(height):
        for x in range(width):
            if 60 <= x < 100 and 40 <= y < 80:
                pixels.append((255, 255, 255))
            else:
                pixels.append((30 + 220 * x // (width - 1), 90 + 60 * y // (height - 1), 220 - 190 * x // (width - 1)))
    return pixels


def rgba_planes() -> tuple[bytes, bytes]:
    """Four colour quadrants (red, green, blue, yellow) and a soft mask that is opaque inside a circle.
    zh-CN: 四个颜色象限 (红, 绿, 蓝, 黄), 软蒙版在圆内不透明."""
    width, height = RGBA_SIZE
    colours = [(220, 40, 40), (40, 180, 60), (40, 80, 220), (240, 200, 40)]
    rgb = bytearray()
    alpha = bytearray()
    radius = width // 2 - 2
    for y in range(height):
        for x in range(width):
            rgb += bytes(colours[(2 if y >= height // 2 else 0) + (1 if x >= width // 2 else 0)])
            dx, dy = 2 * x + 1 - width, 2 * y + 1 - height
            alpha.append(255 if dx * dx + dy * dy <= 4 * radius * radius else 0)
    return bytes(rgb), bytes(alpha)


def images_sample() -> tuple[bytes, dict]:
    document = Document('images.pdf')
    info = _information(document, 'Images sample')
    fonts = _fonts(document)
    photo = jpeg(*JPEG_SIZE, jpeg_pixels())
    jpeg_ref = document.add(Stream({
        'Type': Name('XObject'), 'Subtype': Name('Image'), 'Width': JPEG_SIZE[0], 'Height': JPEG_SIZE[1],
        'ColorSpace': Name('DeviceRGB'), 'BitsPerComponent': 8, 'Filter': Name('DCTDecode'),
    }, photo))
    rgb, alpha = rgba_planes()
    mask = document.add(Stream({
        'Type': Name('XObject'), 'Subtype': Name('Image'), 'Width': RGBA_SIZE[0], 'Height': RGBA_SIZE[1],
        'ColorSpace': Name('DeviceGray'), 'BitsPerComponent': 8, 'Filter': Name('FlateDecode'),
    }, deflate(alpha)))
    rgba = document.add(Stream({
        'Type': Name('XObject'), 'Subtype': Name('Image'), 'Width': RGBA_SIZE[0], 'Height': RGBA_SIZE[1],
        'ColorSpace': Name('DeviceRGB'), 'BitsPerComponent': 8, 'Filter': Name('FlateDecode'), 'SMask': mask,
    }, deflate(rgb)))
    lines = ['Images sample', 'A JPEG photo and an RGB image with a soft mask.']
    content = _text(lines, 770) + b'q 240 0 0 180 72 520 cm /Im1 Do Q' + NEWLINE + b'q 128 0 0 128 360 546 cm /Im2 Do Q' + NEWLINE
    pages = Pages(document)
    pages.add(content, {'Font': fonts, 'XObject': {'Im1': jpeg_ref, 'Im2': rgba}})
    return document.build(_catalog(document, pages), info), {
        'title': 'Images sample', 'pages': 1, 'pageSize': list(A4), 'text': [lines],
        'images': [
            {'name': 'Im1', 'filter': 'DCTDecode', 'width': JPEG_SIZE[0], 'height': JPEG_SIZE[1], 'colorSpace': 'DeviceRGB',
             'bytes': len(photo), 'sha256': hashlib.sha256(photo).hexdigest(), 'placement': [72, 520, 240, 180],
             'centre': [255, 255, 255]},
            {'name': 'Im2', 'filter': 'FlateDecode', 'width': RGBA_SIZE[0], 'height': RGBA_SIZE[1], 'colorSpace': 'DeviceRGB',
             'softMask': True, 'placement': [360, 546, 128, 128],
             'quadrants': [[220, 40, 40], [40, 180, 60], [40, 80, 220], [240, 200, 40]]},
        ],
        'notes': 'Im1 is stored as a baseline JPEG, so extracting it returns the stored bytes (sha256); Im2 is opaque '
                 'inside a circle and transparent in the corners.',
    }


ROTATIONS = [0, 90, 180, 270, 0]


def rotated_sample() -> tuple[bytes, dict]:
    document = Document('rotated.pdf')
    info = _information(document, 'Rotated sample')
    fonts = _fonts(document)
    pages = Pages(document)
    text = []
    sizes = []
    for index, rotation in enumerate(ROTATIONS):
        size = LETTER_LANDSCAPE if index == len(ROTATIONS) - 1 else A4
        lines = [f'Rotate {rotation}', f'Page {index + 1} of {len(ROTATIONS)}']
        # A black bar at the top left of the unrotated page shows the orientation after rendering.
        # zh-CN: 未旋转页面左上角的黑色条块, 用于判断渲染后的方向.
        marker = f'0 g 36 {size[1] - 72} 144 36 re f'.encode('ascii') + NEWLINE
        pages.add(marker + _text(lines, size[1] - 120), {'Font': fonts}, size=size, rotate=rotation)
        text.append(lines)
        sizes.append(list(size))
    return document.build(_catalog(document, pages), info), {
        'title': 'Rotated sample', 'pages': len(ROTATIONS), 'text': text, 'rotations': ROTATIONS, 'mediaBoxes': sizes,
        'marker': 'A 144 x 36 pt black bar 36 pt from the left and top edges of the unrotated page.',
    }


MULTI_SCAN_PAGE = (420, 595)  # A5 in points. zh-CN: A5, 单位为点.
MULTI_SCAN_SIZE = (1750, 2480)  # A5 at 300 dpi. zh-CN: A5, 300 dpi.
MULTI_SCAN_DOT = 16
MULTI_SCAN_ORIGIN = (160, 320)
MULTI_SCAN_PAGES = 3


def multi_scan_lines(number: int) -> list[str]:
    return [f'PAGE {number} OF {MULTI_SCAN_PAGES}', 'SCANNED PAPER', 'HELLO WORLD']


def multi_scan_pixels(lines: list[str]) -> bytes:
    """Block capitals drawn with 16 pixel dots on the 16 pixel grid, so every 8 x 8 JPEG block is uniform.
    zh-CN: 用 16 像素的点在 16 像素网格上绘制的方块大写字母, 使每个 8 x 8 JPEG 块都是均匀的."""
    width, height = MULTI_SCAN_SIZE
    dot = MULTI_SCAN_DOT
    pixels = bytearray([SCAN_PAPER]) * (width * height)
    ink_row = bytes([SCAN_INK]) * dot
    for line_index, line in enumerate(lines):
        top = MULTI_SCAN_ORIGIN[1] + line_index * SCAN_LINE_DOTS * dot
        for char_index, char in enumerate(line):
            left = MULTI_SCAN_ORIGIN[0] + char_index * 6 * dot
            for row, pattern in enumerate(GLYPHS[char]):
                for column, cell in enumerate(pattern):
                    if cell != '#':
                        continue
                    for y in range(top + row * dot, top + (row + 1) * dot):
                        start = y * width + left + column * dot
                        pixels[start:start + dot] = ink_row
    return bytes(pixels)


def scanned_multi_sample() -> tuple[bytes, dict]:
    document = Document('scanned-multi.pdf')
    info = _information(document, 'Multi-page scan sample')
    width, height = MULTI_SCAN_SIZE
    pages = Pages(document)
    images = []
    for number in range(1, MULTI_SCAN_PAGES + 1):
        lines = multi_scan_lines(number)
        photo = gray_jpeg(width, height, multi_scan_pixels(lines))
        image = document.add(Stream({
            'Type': Name('XObject'), 'Subtype': Name('Image'), 'Width': width, 'Height': height,
            'ColorSpace': Name('DeviceGray'), 'BitsPerComponent': 8, 'Filter': Name('DCTDecode'),
        }, photo))
        content = f'q {MULTI_SCAN_PAGE[0]} 0 0 {MULTI_SCAN_PAGE[1]} 0 0 cm /Im1 Do Q'.encode('ascii') + NEWLINE
        pages.add(content, {'XObject': {'Im1': image}}, size=MULTI_SCAN_PAGE)
        images.append({'bytes': len(photo), 'sha256': hashlib.sha256(photo).hexdigest()})
    return document.build(_catalog(document, pages), info), {
        'title': 'Multi-page scan sample', 'pages': MULTI_SCAN_PAGES, 'pageSize': list(MULTI_SCAN_PAGE),
        'text': [[] for _ in range(MULTI_SCAN_PAGES)],
        'image': {'name': 'Im1', 'width': width, 'height': height, 'dpi': 300, 'colorSpace': 'DeviceGray',
                  'bitsPerComponent': 8, 'filter': 'DCTDecode', 'paper': SCAN_PAPER, 'ink': SCAN_INK, 'pages': images},
        'ocrText': [multi_scan_lines(number) for number in range(1, MULTI_SCAN_PAGES + 1)],
        'glyphs': {'dot': MULTI_SCAN_DOT, 'origin': list(MULTI_SCAN_ORIGIN), 'advanceDots': 6, 'lineDots': SCAN_LINE_DOTS,
                   'font': '5 x 7 block capitals'},
        'notes': 'Three A5 pages, each one full-page grey baseline JPEG at 300 dpi and no text layer, for the '
                 'searchable PDF and compression items of 1.1.0 (roadmap P6.5, P6.6).',
    }


FORM_LABEL_TOP = 760
FORM_ROW = 52
FORM_FIELD_X = 200
FORM_FIELD_WIDTH = 300
# Field flags, ISO 32000-1 tables 221, 226, 228 and 230. zh-CN: 字段标志, ISO 32000-1 表 221, 226, 228 与 230.
FLAG_READ_ONLY = 1
FLAG_REQUIRED = 1 << 1
FLAG_MULTILINE = 1 << 12
FLAG_PASSWORD = 1 << 13
FLAG_NO_TOGGLE_TO_OFF = 1 << 14
FLAG_RADIO = 1 << 15
FLAG_PUSH_BUTTON = 1 << 16
FLAG_COMBO = 1 << 17
FLAG_MULTI_SELECT = 1 << 21
BOX = b'0 g 1 w 0.5 0.5 13 13 re S' + NEWLINE
CHECK = b'3 7 m 6 3 l 11 11 l S' + NEWLINE
DOT = b'4 4 6 6 re f' + NEWLINE


def _row_y(index: int) -> int:
    return FORM_LABEL_TOP - index * FORM_ROW


class FormBuilder:
    """Widgets merged with their terminal fields, appearance streams included, and the manifest entry of each field.
    zh-CN: 与终端字段合并的控件 (含外观流), 以及每个字段的清单条目."""

    def __init__(self, document: Document, helv: Ref):
        self.document = document
        self.resources = {'Font': {'Helv': helv}}
        self.fields: list[Ref] = []
        self.manifest: list[dict] = []
        self.annotations: dict[int, list[Ref]] = {1: [], 2: []}
        self.pending: list[tuple[Ref, int, dict]] = []

    def _appearance(self, width: int, height: int, content: bytes, resources: dict | None = None) -> Ref:
        dictionary = {'Type': Name('XObject'), 'Subtype': Name('Form'), 'BBox': [0, 0, width, height]}
        if resources:
            dictionary['Resources'] = resources
        return self.document.add(Stream(dictionary, content))

    def _widget(self, page: int, rect: tuple[int, int, int, int], normal, state: str | None = None, **entries) -> Ref:
        x, y, width, height = rect
        widget = {'Type': Name('Annot'), 'Subtype': Name('Widget'), 'Rect': [x, y, x + width, y + height], 'F': 4,
                  'AP': {'N': normal}}
        if state:
            widget['AS'] = Name(state)
        widget.update(entries)
        ref = self.document.reserve()
        self.annotations[page].append(ref)
        self.pending.append((ref, page, widget))
        return ref

    @staticmethod
    def _shown_rect(rect: tuple[int, int, int, int]) -> list[int]:
        """Left, top, width, height with the origin at the top left of the A4 page (roadmap D62).
        zh-CN: 左, 上, 宽, 高, 原点在 A4 页的左上角 (路线图 D62)."""
        x, y, width, height = rect
        return [x, A4[1] - y - height, width, height]

    def _text_content(self, lines: list[str], height: int) -> bytes:
        parts = [f'/Tx BMC q BT /Helv 10 Tf 0 g 12 TL 2 {height - 13} Td'.encode('ascii')]
        parts += [literal(line) + b' Tj T*' for line in lines]
        parts.append(b'ET Q EMC')
        return NEWLINE.join(parts) + NEWLINE

    def text(self, page: int, name: str, rect, value: str | None, flags: int = 0, default: str | None = None,
             label: str | None = None, parent: Ref | None = None, qualified: str | None = None) -> Ref:
        shown = [] if value is None else ['*' * len(value)] if flags & FLAG_PASSWORD else value.split('\n')
        entries = {'FT': Name('Tx'), 'T': name, 'DA': '/Helv 10 Tf 0 g'}
        if flags:
            entries['Ff'] = flags
        if value is not None:
            entries['V'] = value
        if default is not None:
            entries['DV'] = default
        if label:
            entries['TU'] = label
        if parent:
            entries['Parent'] = parent
        ref = self._widget(page, rect, self._appearance(rect[2], rect[3], self._text_content(shown, rect[3]), self.resources), **entries)
        self.manifest.append({
            'name': qualified or name, 'type': 'text', 'page': page, 'rect': self._shown_rect(rect), 'value': value,
            'defaultValue': default, 'label': label, 'readOnly': bool(flags & FLAG_READ_ONLY),
            'required': bool(flags & FLAG_REQUIRED), 'multiline': bool(flags & FLAG_MULTILINE),
            'password': bool(flags & FLAG_PASSWORD),
        })
        return ref

    def check_box(self, page: int, name: str, rect, checked: bool) -> Ref:
        states = {'Yes': self._appearance(14, 14, BOX + CHECK), 'Off': self._appearance(14, 14, BOX)}
        state = 'Yes' if checked else 'Off'
        ref = self._widget(page, rect, states, state, FT=Name('Btn'), T=name, V=Name(state), DV=Name('Off'))
        self.manifest.append({'name': name, 'type': 'checkbox', 'page': page, 'rect': self._shown_rect(rect), 'value': checked,
                              'defaultValue': False, 'exportValue': 'Yes'})
        return ref

    def radio(self, page: int, name: str, y: int, options: list[str], selected: str, default: str) -> Ref:
        group = self.document.reserve()
        kids = []
        rects = []
        for index, option in enumerate(options):
            rect = (FORM_FIELD_X + index * 60, y, 14, 14)
            states = {option: self._appearance(14, 14, BOX + DOT), 'Off': self._appearance(14, 14, BOX)}
            kids.append(self._widget(page, rect, states, option if option == selected else 'Off', Parent=group))
            rects.append(self._shown_rect(rect))
        self.document.put(group, {'FT': Name('Btn'), 'T': name, 'Ff': FLAG_RADIO | FLAG_NO_TOGGLE_TO_OFF, 'V': Name(selected),
                                  'DV': Name(default), 'Kids': kids})
        self.manifest.append({'name': name, 'type': 'radio', 'page': page, 'widgets': rects, 'value': selected,
                              'defaultValue': default, 'options': [{'value': option, 'label': option} for option in options]})
        return group

    def choice(self, page: int, name: str, rect, flags: int, options: list[tuple[str, str]], values: list[str],
               default: str | None) -> Ref:
        x, y, width, height = rect
        if flags & FLAG_COMBO:
            label = dict(options)[values[0]]
            content = b'/Tx BMC q BT /Helv 10 Tf 0 g 2 ' + str(height - 13).encode('ascii') + b' Td ' + literal(label) + b' Tj ET Q EMC' + NEWLINE
            opt = [label for _, label in options]
        else:
            parts = [b'/Tx BMC q']
            for index, (export, _) in enumerate(options):
                if export in values:
                    parts.append(f'0.6 0.75 0.9 rg 0 {height - (index + 1) * 16} {width} 16 re f'.encode('ascii'))
            parts.append(f'BT /Helv 10 Tf 0 g 16 TL 2 {height - 12} Td'.encode('ascii'))
            parts += [literal(label) + b' Tj T*' for _, label in options]
            parts.append(b'ET Q EMC')
            content = NEWLINE.join(parts) + NEWLINE
            opt = [[export, label] for export, label in options]
        entries = {'FT': Name('Ch'), 'T': name, 'DA': '/Helv 10 Tf 0 g', 'Ff': flags, 'Opt': opt,
                   'V': values[0] if len(values) == 1 else list(values)}
        if not flags & FLAG_COMBO:
            entries['I'] = [index for index, (export, _) in enumerate(options) if export in values]
        if default is not None:
            entries['DV'] = default
        ref = self._widget(page, rect, self._appearance(width, height, content, self.resources), **entries)
        self.manifest.append({
            'name': name, 'type': 'combo' if flags & FLAG_COMBO else 'list', 'page': page, 'rect': self._shown_rect(rect),
            'value': values[0] if flags & FLAG_COMBO else values, 'defaultValue': default,
            'options': [{'value': export, 'label': label} for export, label in options],
            'multiSelect': bool(flags & FLAG_MULTI_SELECT),
        })
        return ref

    def push_button(self, page: int, name: str, rect, caption: str) -> Ref:
        x, y, width, height = rect
        content = f'q 0.85 g 0 0 {width} {height} re f Q BT /Helv 10 Tf 0 g 18 6 Td '.encode('ascii') + literal(caption) + b' Tj ET' + NEWLINE
        ref = self._widget(page, rect, self._appearance(width, height, content, self.resources), FT=Name('Btn'), T=name,
                           DA='/Helv 10 Tf 0 g', Ff=FLAG_PUSH_BUTTON, MK={'CA': caption})
        self.manifest.append({'name': name, 'type': 'button', 'page': page, 'rect': self._shown_rect(rect), 'value': None})
        return ref

    def finish(self, pages: list[Ref]) -> None:
        for ref, page, widget in self.pending:
            self.document.put(ref, {**widget, 'P': pages[page - 1]})


FORM_LABELS = [['Name', 'Notes', 'Agree', 'Size', 'Country', 'ID', 'Email', 'PIN'], ['Colors', '', 'City', 'Submit']]


def form_sample() -> tuple[bytes, dict]:
    document = Document('form.pdf')
    info = _information(document, 'Form sample')
    helv = document.add({'Type': Name('Font'), 'Subtype': Name('Type1'), 'BaseFont': Name('Helvetica'),
                         'Encoding': Name('WinAnsiEncoding')})
    zadb = document.add({'Type': Name('Font'), 'Subtype': Name('Type1'), 'BaseFont': Name('ZapfDingbats')})
    form = FormBuilder(document, helv)

    def row(index: int, height: int = 20) -> tuple[int, int, int, int]:
        return FORM_FIELD_X, _row_y(index) - 6, FORM_FIELD_WIDTH, height

    form.fields.append(form.text(1, 'name', row(0), 'Ada Lovelace', default='Ada', label='Full name'))
    form.fields.append(form.text(1, 'notes', (FORM_FIELD_X, _row_y(1) - 26, FORM_FIELD_WIDTH, 40), 'Line 1\nLine 2',
                                 flags=FLAG_MULTILINE))
    form.fields.append(form.check_box(1, 'agree', (FORM_FIELD_X, _row_y(2) - 4, 14, 14), checked=True))
    form.fields.append(form.radio(1, 'size', _row_y(3) - 4, ['small', 'medium', 'large'], selected='medium', default='small'))
    form.fields.append(form.choice(1, 'country', row(4), FLAG_COMBO, [('China', 'China'), ('France', 'France'), ('Japan', 'Japan')],
                                   ['France'], default='China'))
    form.fields.append(form.text(1, 'id', row(5), 'P-001', flags=FLAG_READ_ONLY))
    form.fields.append(form.text(1, 'email', row(6), None, flags=FLAG_REQUIRED, label='Email address'))
    form.fields.append(form.text(1, 'pin', row(7), '1234', flags=FLAG_PASSWORD))
    form.fields.append(form.choice(2, 'colors', (FORM_FIELD_X, _row_y(0) + 14 - 54, FORM_FIELD_WIDTH, 54), FLAG_MULTI_SELECT,
                                   [('r', 'Red'), ('g', 'Green'), ('b', 'Blue')], ['r', 'b'], default='g'))
    address = document.reserve()
    city = form.text(2, 'city', row(2), 'Paris', parent=address, qualified='address.city')
    document.put(address, {'T': 'address', 'Kids': [city]})
    form.fields.append(address)
    form.fields.append(form.push_button(2, 'submit', (FORM_FIELD_X, _row_y(3) - 6, 80, 20), 'Submit'))

    pages = Pages(document)
    text = []
    for number, labels in enumerate(FORM_LABELS, 1):
        parts = [f'BT /F1 11 Tf 72 {_row_y(index)} Td '.encode('ascii') + literal(label) + b' Tj ET'
                 for index, label in enumerate(labels) if label]
        pages.add(NEWLINE.join(parts) + NEWLINE, {'Font': {'F1': helv}}, annotations=form.annotations[number])
        text.append([label for label in labels if label])
    form.finish(pages.kids)
    acro_form = document.add({'Fields': form.fields, 'DA': '/Helv 0 Tf 0 g', 'DR': {'Font': {'Helv': helv, 'ZaDb': zadb}},
                              'NeedAppearances': False})
    return document.build(_catalog(document, pages, AcroForm=acro_form), info), {
        'title': 'Form sample', 'pages': len(FORM_LABELS), 'pageSize': list(A4), 'text': text, 'fields': form.manifest,
        'notes': 'AcroForm fields with appearance streams in Helvetica (WinAnsiEncoding, not embedded): text, multiline, '
                 'check box, radio group, combo box, multi-select list box with export values, read-only, required, '
                 'password, push button and a field under the non-terminal field address; NeedAppearances is false. '
                 'Rectangles are left, top, width, height in points from the top left corner (roadmap D62).',
    }


SAMPLES = {
    'text.pdf': text_sample,
    'outline.pdf': outline_sample,
    'encrypted.pdf': encrypted_sample,
    'scanned.pdf': scanned_sample,
    'images.pdf': images_sample,
    'rotated.pdf': rotated_sample,
    'form.pdf': form_sample,
    'scanned-multi.pdf': scanned_multi_sample,
}

# endregion


def generate() -> dict[str, bytes]:
    files: dict[str, bytes] = {}
    manifest = {'format': 1, 'generator': CREATOR, 'author': AUTHOR, 'date': DATE, 'fixtures': {}}
    for name, build in SAMPLES.items():
        data, description = build()
        files[name] = data
        manifest['fixtures'][name] = {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(), **description}
    files[MANIFEST] = (json.dumps(manifest, indent=2, ensure_ascii=True) + '\n').encode('ascii')
    return files


def main() -> int:
    parser = argparse.ArgumentParser(description='Generate the PDF samples of samples/fixtures.')
    parser.add_argument('--check', action='store_true', help='compare the committed files with a fresh run')
    args = parser.parse_args()
    files = generate()
    if args.check:
        present = {path.name for path in OUTPUT.glob('*') if path.is_file()} if OUTPUT.is_dir() else set()
        problems = [f'{name}: differs' for name, data in files.items() if not (OUTPUT / name).is_file()
                    or (OUTPUT / name).read_bytes() != data]
        problems += [f'{name}: not generated' for name in sorted(present - set(files))]
        if problems:
            print('samples/fixtures is out of date, run py .python/generate_fixtures.py', *problems, sep='\n  ')
            return 1
        print(f'samples/fixtures is up to date ({len(files)} files, {sum(map(len, files.values()))} bytes)')
        return 0
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for name, data in files.items():
        (OUTPUT / name).write_bytes(data)
    print(f'wrote {len(files)} files, {sum(map(len, files.values()))} bytes, to {OUTPUT.relative_to(ROOT).as_posix()}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
