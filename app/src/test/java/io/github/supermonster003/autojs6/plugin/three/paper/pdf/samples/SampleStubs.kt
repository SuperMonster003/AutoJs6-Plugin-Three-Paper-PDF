package io.github.supermonster003.autojs6.plugin.three.paper.pdf.samples

/**
 * Stubs the samples of roadmap P6.14 run against ([SamplesScriptTest]): `pdf` with a small document model (pages of
 * text, form fields, outline and document information per path), `files`, `engines`, `console` and `ocr` for Rhino,
 * promise answers for Node.js and Bun. Every call is recorded with its arguments made plain (functions as
 * `'<function>'`, `undefined` as `'<undefined>'`), so the test can hold them against the signature table. The fixtures
 * mirror `samples/fixtures` (outline.pdf, form.pdf, scanned.pdf, scanned-multi.pdf) closely enough for the samples to
 * take every branch; the real files are checked on devices (P6.15).
 * Modes: `normal`; `unsupported` (a 1.0.x plugin: the 1.1.0 methods fail with `UNSUPPORTED`, `pdf.info` has no
 * `formFieldCount`); `noOcr` (`makeSearchable` fails with `OCR_UNAVAILABLE` when pages need recognition); `unavailable`
 * (`pdf.isAvailable()` is false).
 * zh-CN: 路线图 P6.14 的样例运行时使用的桩对象 ([SamplesScriptTest]): 带简单文档模型 (按路径保存各页文字, 表单字段, 目录与文档信息) 的
 * `pdf`, 以及 Rhino 的 `files`, `engines`, `console` 与 `ocr`; Node.js 与 Bun 得到 Promise 形式的返回值. 每次调用连同参数一起记录, 参数
 * 转为普通值 (函数为 `'<function>'`, `undefined` 为 `'<undefined>'`), 供测试对照签名表. 夹具与 `samples/fixtures` (outline.pdf,
 * form.pdf, scanned.pdf, scanned-multi.pdf) 足够接近, 让样例走到每个分支; 真实文件在设备上检查 (P6.15).
 * 模式: `normal`; `unsupported` (1.0.x 插件: 1.1.0 的方法失败于 `UNSUPPORTED`, `pdf.info` 没有 `formFieldCount`); `noOcr` (有页要识别时
 * `makeSearchable` 失败于 `OCR_UNAVAILABLE`); `unavailable` (`pdf.isAvailable()` 为 false).
 */
internal object SampleStubs {

    /** The methods 3-Paper PDF 1.1.0 added. zh-CN: 3-Paper PDF 1.1.0 新增的方法. */
    val V110 = listOf("forms", "fillForm", "watermark", "stamp", "addTextLayer", "compress", "setInfo", "setOutline", "makeSearchable")

    /** The message of `UNSUPPORTED` on a 1.0.x plugin. zh-CN: 1.0.x 插件上 `UNSUPPORTED` 的消息. */
    const val UNSUPPORTED_MESSAGE = "需要 3-Paper PDF 1.1.0 或更高版本. 请在插件中心更新后重试."

    /** Where the fixtures are. zh-CN: 夹具所在的目录. */
    const val FIXTURES = "/sdcard/Documents/3-Paper PDF/fixtures"

    /**
     * `__sampleStubs({ mode, fixtures, node, args, record, wrap, fail })`: [fixtures] says whether the fixtures exist,
     * `wrap` and `fail` turn a result or an error into what a call answers.
     * zh-CN: `__sampleStubs({ mode, fixtures, node, args, record, wrap, fail })`: `fixtures` 表示夹具是否存在, `wrap` 与 `fail` 把结果或错误
     * 转为调用的返回值.
     */
    val JS = """
        function __sampleStubs(o) {
            var mode = o.mode;
            var node = o.node;
            var docs = {};
            var written = {};
            var logs = [];
            var V110 = ${V110.joinToString(", ", "[", "]") { "'$it'" }};

            function PdfError(code, message) {
                this.name = 'PdfError';
                this.code = code;
                this.message = message;
            }
            PdfError.prototype = Object.create(Error.prototype);
            PdfError.prototype.constructor = PdfError;

            function clone(value) {
                return value === undefined ? undefined : JSON.parse(JSON.stringify(value));
            }
            function plain(value) {
                if (typeof value === 'function') return '<function>';
                if (value === undefined) return '<undefined>';
                if (value === null || typeof value !== 'object') return value;
                if (Array.isArray(value)) return value.map(plain);
                var copy = {};
                Object.keys(value).forEach(function (key) { copy[key] = plain(value[key]); });
                return copy;
            }
            function doc(path) {
                var found = docs[String(path)];
                if (!found) throw new PdfError('FILE_NOT_FOUND', 'File not found: ' + path);
                return found;
            }
            function put(path, d) {
                docs[String(path)] = d;
                return d;
            }
            function make(pages, extra) {
                var d = { pages: pages, fields: [], outline: [], info: { title: null, author: null, subject: null, keywords: null, creator: null, producer: 'Fixture' }, size: 20000, images: 0 };
                Object.keys(extra || {}).forEach(function (key) { d[key] = extra[key]; });
                return d;
            }
            function copyDoc(d) {
                return { pages: d.pages.slice(), fields: clone(d.fields), outline: clone(d.outline), info: clone(d.info), size: d.size, images: d.images };
            }
            function result(path, d, extra) {
                var r = { path: String(path), pageCount: d.pages.length, sizeBytes: d.size, warnings: [], warningCodes: [] };
                Object.keys(extra || {}).forEach(function (key) { r[key] = extra[key]; });
                return r;
            }
            function progress(options, stages) {
                if (options && typeof options.onProgress === 'function') {
                    stages.forEach(function (stage) { options.onProgress({ stage: stage, percent: 100, current: 1, total: 1 }); });
                }
            }
            function pagesOf(spec, count) {
                var list = [];
                if (spec === undefined || spec === null) {
                    for (var i = 1; i <= count; i++) list.push(i);
                } else if (typeof spec === 'number') {
                    list = [spec];
                } else if (Array.isArray(spec)) {
                    list = spec.map(Number);
                } else {
                    String(spec).split(',').forEach(function (part) {
                        var m = /^\s*(\d*)\s*(-?)\s*(\d*)\s*$/.exec(part);
                        if (!m) throw new PdfError('INVALID_ARGUMENT', 'bad page range: ' + spec);
                        var from = m[1] ? Number(m[1]) : 1;
                        var to = m[2] ? (m[3] ? Number(m[3]) : count) : from;
                        for (var p = from; p <= to; p++) list.push(p);
                    });
                }
                list.forEach(function (p) {
                    if (p < 1 || p > count) throw new PdfError('PAGE_OUT_OF_RANGE', 'page ' + p + ' of ' + count);
                });
                return list;
            }
            function field(name, type, value, extra) {
                var f = { name: name, type: type, value: value, defaultValue: null, readOnly: false, required: false, page: 1 };
                Object.keys(extra || {}).forEach(function (key) { f[key] = extra[key]; });
                return f;
            }
            function choices() {
                return Array.prototype.slice.call(arguments).map(function (value) { return { value: value, label: value }; });
            }
            /* The fields of a form written by hand (the demo form of the forms sample). */
            function formFields(text) {
                var fields = [];
                String(text).split('endobj').forEach(function (chunk) {
                    var type = /\/FT \/(\w+)/.exec(chunk);
                    var name = /\/T \(([^)]*)\)/.exec(chunk);
                    if (!type || !name) return;
                    var value = /\/V \(([^)]*)\)/.exec(chunk);
                    var initial = /\/DV \(([^)]*)\)/.exec(chunk);
                    fields.push(type[1] === 'Tx'
                        ? field(name[1], 'text', value ? value[1] : '', { defaultValue: initial ? initial[1] : null })
                        : field(name[1], 'checkbox', /\/V \/Yes/.test(chunk)));
                });
                return fields;
            }

            if (o.fixtures) {
                var base = '${FIXTURES}/';
                put(base + 'outline.pdf', make(['Chapter 1', 'Chapter 1 continued: needle', 'Chapter 2', 'Chapter 2 continued', 'Chapter 3: needle', 'Notes'], {
                    outline: [
                        { title: 'Chapter 1', page: 1, children: [] },
                        { title: 'Chapter 2', page: 3, children: [] },
                        { title: 'Chapter 3', page: 5, children: [] },
                        { title: 'Notes', page: null, children: [] }
                    ]
                }));
                put(base + 'form.pdf', make(['Application form', 'Address'], {
                    fields: [
                        field('name', 'text', 'Ada Lovelace', { defaultValue: 'Ada' }),
                        field('notes', 'text', '', { multiline: true }),
                        field('agree', 'checkbox', true),
                        field('size', 'radio', 'medium', { options: choices('small', 'medium', 'large') }),
                        field('country', 'combo', 'France', { options: choices('China', 'France', 'Japan') }),
                        field('id', 'text', 'A-001', { readOnly: true }),
                        field('email', 'text', '', { required: true }),
                        field('pin', 'text', '', { password: true }),
                        field('colors', 'list', ['r', 'b'], { multiSelect: true, options: choices('r', 'g', 'b') }),
                        field('address.city', 'text', 'Paris', { page: 2 }),
                        field('submit', 'button', null)
                    ]
                }));
                put(base + 'scanned.pdf', make([''], { size: 300000, images: 1 }));
                put(base + 'scanned-multi.pdf', make(['', '', ''], { size: 900000, images: 3 }));
            }

            function unsupported() {
                throw new PdfError('UNSUPPORTED', '${UNSUPPORTED_MESSAGE}');
            }

            var impl = {
                isAvailable: function () {
                    return mode !== 'unavailable';
                },
                info: function (path) {
                    var d = doc(path);
                    var info = {
                        pageCount: d.pages.length, sizeBytes: d.size, encrypted: false,
                        title: d.info.title, author: d.info.author, subject: d.info.subject,
                        keywords: d.info.keywords, creator: d.info.creator, producer: d.info.producer
                    };
                    if (mode !== 'unsupported') {
                        info.formFieldCount = d.fields.length;
                        info.xfa = false;
                    }
                    return info;
                },
                text: function (path, options) {
                    var d = doc(path);
                    var list = pagesOf(options ? options.pages : undefined, d.pages.length);
                    var texts = list.map(function (p) { return d.pages[p - 1]; });
                    return node ? { texts: texts, pages: list, sources: list.map(function () { return 'text'; }) } : texts;
                },
                search: function (path, keyword) {
                    var hits = [];
                    doc(path).pages.forEach(function (text, i) {
                        if (text.indexOf(String(keyword)) >= 0) hits.push({ page: i + 1, index: hits.length, rects: [], context: text });
                    });
                    return hits;
                },
                outline: function (path) {
                    return clone(doc(path).outline);
                },
                render: function (path, page, options) {
                    pagesOf(page, doc(path).pages.length);
                    options = options || {};
                    var width = options.width || Math.round(595 * (options.dpi || 72) / 72);
                    var height = Math.round(width * 842 / 595);
                    if (node) {
                        if (options.encoding === 'base64') return { base64: 'AAAA', width: width, height: height, sizeBytes: 3 };
                        var output = options.output || '.pdf-render/page-' + page + '.png';
                        written[output] = '<image>';
                        return { path: output, width: width, height: height, sizeBytes: 1000 };
                    }
                    return {
                        getWidth: function () { return width; },
                        getHeight: function () { return height; },
                        recycle: function () { o.record('img.recycle', []); }
                    };
                },
                renderToFile: function (path, page, output, options) {
                    pagesOf(page, doc(path).pages.length);
                    options = options || {};
                    var width = options.width || Math.round(595 * (options.dpi || 72) / 72);
                    written[String(output)] = '<image>';
                    return { path: String(output), width: width, height: Math.round(width * 842 / 595), sizeBytes: 1000 };
                },
                fromText: function (text, output, options) {
                    var lines = String(text).split('\n');
                    var pages = [];
                    for (var i = 0; i < lines.length; i += 45) pages.push(lines.slice(i, i + 45).join('\n'));
                    progress(options, ['writing']);
                    return result(output, put(output, make(pages, { size: 2000 * pages.length })));
                },
                fromImages: function (paths, output, options) {
                    progress(options, ['writing']);
                    return result(output, put(output, make(paths.map(function () { return ''; }), { size: 400000 * paths.length, images: paths.length })));
                },
                merge: function (inputs, output, options) {
                    var names = (options && options.displayNames) || [];
                    var d = make([]);
                    inputs.forEach(function (input, i) {
                        if (names[i]) d.outline.push({ title: names[i], page: d.pages.length + 1, children: [] });
                        d.pages = d.pages.concat(doc(input).pages);
                    });
                    progress(options, ['merging', 'writing']);
                    return result(output, put(output, d));
                },
                extract: function (path, output, pages) {
                    var d = copyDoc(doc(path));
                    d.pages = pagesOf(pages, d.pages.length).map(function (p) { return d.pages[p - 1]; });
                    return result(output, put(output, d));
                },
                forms: function (path) {
                    return clone(doc(path).fields);
                },
                fillForm: function (path, output, values, options) {
                    options = options || {};
                    var d = copyDoc(doc(path));
                    var find = function (name) { return d.fields.filter(function (f) { return f.name === name; })[0]; };
                    var unknown = Object.keys(values).filter(function (name) { return !find(name); });
                    if (unknown.length > 0 && !options.ignoreMissing) throw new PdfError('INVALID_ARGUMENT', 'Unknown fields: ' + unknown.join(', '));
                    var filled = 0;
                    Object.keys(values).forEach(function (name) {
                        var f = find(name);
                        if (!f) return;
                        var value = values[name];
                        if (f.readOnly || f.type === 'button' || f.type === 'signature') throw new PdfError('INVALID_ARGUMENT', name + ' cannot be filled');
                        if (value !== null && f.type === 'checkbox' && typeof value !== 'boolean') throw new PdfError('INVALID_ARGUMENT', name + ' takes a boolean');
                        if (value !== null && f.type === 'text' && typeof value !== 'string') throw new PdfError('INVALID_ARGUMENT', name + ' takes a string');
                        f.value = value === null ? clone(f.defaultValue) : clone(value);
                        filled++;
                    });
                    if (options.flatten) {
                        d.fields.forEach(function (f) {
                            if (typeof f.value === 'string' && f.value) d.pages[f.page - 1] += '\n' + f.value;
                        });
                        d.fields = [];
                    }
                    progress(options, ['writing']);
                    put(output, d);
                    return result(output, d, {
                        filled: filled,
                        warnings: unknown.length > 0 ? ['Ignored unknown fields: ' + unknown.join(', ')] : [],
                        warningCodes: unknown.length > 0 ? ['FIELDS_IGNORED'] : []
                    });
                },
                watermark: function (path, output, options) {
                    var d = copyDoc(doc(path));
                    if (options.text) {
                        pagesOf(options.pages, d.pages.length).forEach(function (p) { d.pages[p - 1] += '\n' + options.text; });
                    }
                    progress(options, ['writing']);
                    return result(output, put(output, d));
                },
                stamp: function (path, output, options) {
                    var d = copyDoc(doc(path));
                    var count = d.pages.length;
                    var add = function (p, text) { d.pages[p - 1] += '\n' + text; };
                    var fill = function (text, shown) {
                        return String(text).replace(/\{page\}/g, String(shown)).replace(/\{total\}/g, String(count))
                            .replace(/\{date\}/g, '2026-11-20').replace(/\{name\}/g, 'source');
                    };
                    if (options.pageNumbers) {
                        var numbers = options.pageNumbers === true ? {} : options.pageNumbers;
                        pagesOf(numbers.pages !== undefined ? numbers.pages : options.pages, count).forEach(function (p, i) {
                            add(p, fill(numbers.format || '{page} / {total}', numbers.start === undefined ? p : numbers.start + i));
                        });
                    }
                    ['header', 'footer'].forEach(function (key) {
                        var value = options[key];
                        if (!value) return;
                        var spec = typeof value === 'string' ? { center: value } : value;
                        pagesOf(spec.pages !== undefined ? spec.pages : options.pages, count).forEach(function (p) {
                            add(p, ['left', 'center', 'right'].filter(function (k) { return spec[k]; }).map(function (k) { return fill(spec[k], p); }).join(' '));
                        });
                    });
                    (options.items || []).forEach(function (item) {
                        if (item.type === 'text') pagesOf(item.pages, count).forEach(function (p) { add(p, fill(item.text, p)); });
                    });
                    progress(options, ['writing']);
                    return result(output, put(output, d));
                },
                compress: function (path, output, options) {
                    options = options || {};
                    var d = copyDoc(doc(path));
                    var source = d.size;
                    var factor = options.imageDpi ? 0.15 : { low: 0.2, medium: 0.3, high: 1.1 }[options.preset || 'medium'];
                    d.size = Math.round(source * factor);
                    var bigger = d.size >= source;
                    progress(options, ['opening', 'processing', 'writing', 'verifying']);
                    put(output, d);
                    return result(output, d, {
                        sourceBytes: source,
                        images: { total: d.images, recompressed: bigger ? 0 : d.images, skipped: bigger ? d.images : 0 },
                        objectStreams: options.objectStreams !== false,
                        warnings: bigger ? ['The output is not smaller than the source.'] : [],
                        warningCodes: bigger ? ['OUTPUT_NOT_SMALLER'] : []
                    });
                },
                setInfo: function (path, output, info, options) {
                    var d = copyDoc(doc(path));
                    Object.keys(info).forEach(function (key) { d.info[key] = info[key] === null ? null : String(info[key]); });
                    d.info.producer = '3-Paper PDF';
                    progress(options, ['writing']);
                    return result(output, put(output, d));
                },
                setOutline: function (path, output, items, options) {
                    var d = copyDoc(doc(path));
                    d.outline = items ? clone(items) : [];
                    progress(options, ['writing']);
                    return result(output, put(output, d));
                },
                makeSearchable: function (path, output, options) {
                    options = options || {};
                    var d = copyDoc(doc(path));
                    var range = pagesOf(options.pages, d.pages.length);
                    var todo = range.filter(function (p) { return options.force || !d.pages[p - 1].trim(); });
                    if (todo.length > 0 && mode === 'noOcr') throw new PdfError('OCR_UNAVAILABLE', 'No OCR engine is available.');
                    progress(options, todo.length > 0 ? ['ocr', 'writing'] : ['writing']);
                    todo.forEach(function (p) { d.pages[p - 1] = 'SCANNED RECEIPT\nTotal 128.50'; });
                    put(output, d);
                    return result(output, d, { ocrPages: todo.length, layerPages: todo.length, skippedPages: range.length - todo.length, lines: todo.length * 2 });
                },
                addTextLayer: function (path, output, layers, options) {
                    options = options || {};
                    var d = copyDoc(doc(path));
                    var layerPages = 0;
                    var skipped = 0;
                    var lines = 0;
                    layers.forEach(function (layer) {
                        if (!options.force && d.pages[layer.page - 1].trim()) {
                            skipped++;
                            return;
                        }
                        d.pages[layer.page - 1] = layer.lines.map(function (line) { return line.text; }).join('\n');
                        layerPages++;
                        lines += layer.lines.length;
                    });
                    progress(options, ['writing']);
                    put(output, d);
                    return result(output, d, { layerPages: layerPages, skippedPages: skipped, lines: lines });
                }
            };

            var pdf = { PdfError: PdfError };
            Object.keys(impl).forEach(function (name) {
                pdf[name] = function () {
                    o.record('pdf.' + name, Array.prototype.slice.call(arguments).map(plain));
                    try {
                        if (mode === 'unsupported' && V110.indexOf(name) >= 0) unsupported();
                        return o.wrap(impl[name].apply(null, arguments));
                    } catch (e) {
                        return o.fail(e);
                    }
                };
            });

            var console = {};
            ['log', 'warn', 'error'].forEach(function (level) {
                console[level] = function () {
                    var text = Array.prototype.slice.call(arguments).map(function (part) {
                        return typeof part === 'string' ? part : JSON.stringify(part);
                    }).join(' ');
                    logs.push(level + ': ' + text);
                    if (o.onLog) o.onLog(text);
                };
            });

            var files = {
                join: function () { return Array.prototype.slice.call(arguments).join('/'); },
                getSdcardPath: function () { return '/sdcard'; },
                path: function (path) { return '/sdcard/Scripts/' + String(path).replace(/^\.\//, ''); },
                exists: function (path) { return docs[String(path)] !== undefined || written[String(path)] !== undefined; },
                ensureDir: function (path) {
                    o.record('files.ensureDir', [String(path)]);
                    return true;
                },
                write: function (path, text) {
                    o.record('files.write', [String(path)]);
                    written[String(path)] = String(text);
                    if (String(text).indexOf('%PDF-') === 0) put(path, make(['Name: I agree:'], { fields: formFields(text), size: String(text).length }));
                },
                remove: function (path) {
                    o.record('files.remove', [String(path)]);
                    delete docs[String(path)];
                    delete written[String(path)];
                    return true;
                }
            };

            var ocr = {
                detect: function (img) {
                    o.record('ocr.detect', [img.getWidth(), img.getHeight()]);
                    return [
                        { label: 'SCANNED RECEIPT', bounds: { left: 80, top: 120, right: 1400, bottom: 220 } },
                        { label: 'Total 128.50', bounds: { left: 80, top: 260, right: 900, bottom: 340 } }
                    ];
                }
            };

            return {
                pdf: pdf,
                console: console,
                files: files,
                ocr: ocr,
                engines: { myEngine: function () { return { execArgv: o.args }; } },
                report: function () { return { written: written, logs: logs }; }
            };
        }
    """.trimIndent()

}
