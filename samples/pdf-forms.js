/**
 * 表单读取与填写 [v6.8.1+]
 *
 * pdf 模块由 3-Paper PDF 插件提供, 先在插件中心安装并启用. 本样例需要 AutoJs6 6.8.1 或以上与 3-Paper PDF 1.1.0 或以上:
 * 插件为 1.0.x 时 pdf.forms 等 1.1.0 的新方法失败于 UNSUPPORTED, 本样例给出提示后结束.
 * 演示:
 * 1. pdf.forms 列出表单 (AcroForm) 字段: name 为全限定字段名 (填写时以它为准), type 为 text, checkbox, radio, combo, list,
 *    button 或 signature, 另有 value, defaultValue, options, readOnly, required 等; pdf.info 的 formFieldCount 给出字段数.
 * 2. pdf.fillForm 按字段名填写并写出副本: text 填字符串, checkbox 填布尔值, radio 与 combo 填选项的值, list 填字符串或数组;
 *    按钮, 签名与只读字段不能填写. 结果的 filled 为填写的字段数.
 * 3. 出错与恢复: 未知的字段名默认失败于 INVALID_ARGUMENT, ignoreMissing: true 时改为提示 (warningCodes 含 FIELDS_IGNORED);
 *    值为 null 时恢复字段的默认值; 值的类型不对 (例如给复选框填字符串) 时失败于 INVALID_ARGUMENT. 失败时不写出文件.
 * 4. flatten: true 把字段并入页面内容: 之后 pdf.forms 返回空数组, 填写的值成为普通文字, 可以搜索.
 * Android 14 (API 34) 及以下的系统渲染器不绘制尚未扁平化的表单值, 在这些设备上查看填写结果时使用 flatten.
 * 可选参数 (engines.execScriptFile 的 arguments): path 为带表单的 PDF (默认为样例 fixtures/form.pdf, 找不到时现场写出一个有两个字段的表单),
 * outDir 为输出目录 (默认为 /sdcard/Documents/3-Paper PDF/samples/forms), output 为写入结果摘要 (JSON) 的文件.
 */

let args = engines.myEngine().execArgv;
let base = files.join(files.getSdcardPath(), 'Documents', '3-Paper PDF');
let outDir = args.outDir ? String(args.outDir) : files.join(base, 'samples', 'forms');
let summary = { errors: [] };
let stopped = false;

function fixture(name) {
    let local = files.path('./fixtures/' + name);
    if (files.exists(local)) return local;
    let shared = files.join(base, 'fixtures', name);
    return files.exists(shared) ? shared : null;
}

function outPath(name) {
    return files.join(outDir, name);
}

function report(label, e) {
    console.error(label + ' 失败 [' + (e.code || e.name) + ']: ' + e.message);
    summary.errors.push(label + ': ' + (e.code || e.name));
}

/* 运行 body, 返回它抛出的 PdfError 的 code; 没有抛出时返回 'OK'. */
function codeOf(body) {
    try {
        body();
        return 'OK';
    } catch (e) {
        if (e instanceof pdf.PdfError) return e.code;
        throw e;
    }
}

/* 运行一个步骤. 插件为 1.0.x 时 1.1.0 的新方法失败于 UNSUPPORTED: 给出提示, 不再运行后面的步骤. */
function step(label, body) {
    if (stopped) return;
    try {
        body();
    } catch (e) {
        if (e.code !== 'UNSUPPORTED') {
            report(label, e);
            return;
        }
        stopped = true;
        summary.unsupported = String(e.message);
        console.error(label + ' 不可用: ' + e.message);
        console.error('本样例用到 3-Paper PDF 1.1.0 的新方法. 已安装的插件为 1.0.x 时, 请在插件中心把它更新到 1.1.0 或以上, 再运行本样例.');
    }
}

function stream(dict, content) {
    return '<< ' + dict + '/Length ' + content.length + ' >>\nstream\n' + content + '\nendstream';
}

/*
 * 没有带表单的 PDF 时现场写出一个: pdf 模块不能新建表单字段, 这里按 PDF 规范直接写出一页 A4, 带一个文本框 name (默认值 Ada)
 * 与一个复选框 agree. 全部为 ASCII 字符, 字符串长度即字节数, 交叉引用表 (xref) 的偏移按长度计算.
 */
function demoForm(path) {
    let objects = [
        '<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [5 0 R 6 0 R] /DR << /Font << /Helv 4 0 R >> >> /DA (/Helv 12 Tf 0 g) >> >>',
        '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
        '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /Helv 4 0 R >> >> /Contents 7 0 R /Annots [5 0 R 6 0 R] >>',
        '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>',
        '<< /Type /Annot /Subtype /Widget /P 3 0 R /F 4 /Rect [160 740 460 760] /FT /Tx /T (name) /TU (Full name) /V (Ada) /DV (Ada) /DA (/Helv 12 Tf 0 g) >>',
        '<< /Type /Annot /Subtype /Widget /P 3 0 R /F 4 /Rect [160 700 174 714] /FT /Btn /T (agree) /V /Off /AS /Off /AP << /N << /Yes 8 0 R /Off 9 0 R >> >> >>',
        stream('', 'BT /Helv 12 Tf 72 745 Td (Name:) Tj 0 -40 Td (I agree:) Tj ET'),
        stream('/Type /XObject /Subtype /Form /BBox [0 0 14 14] ', '0 G 0.5 0.5 13 13 re S 0 g 3 3 8 8 re f'),
        stream('/Type /XObject /Subtype /Form /BBox [0 0 14 14] ', '0 G 0.5 0.5 13 13 re S'),
    ];
    let text = '%PDF-1.7\n';
    let offsets = [];
    objects.forEach((body, i) => {
        offsets.push(text.length);
        text += (i + 1) + ' 0 obj\n' + body + '\nendobj\n';
    });
    let xref = text.length;
    text += 'xref\n0 ' + (objects.length + 1) + '\n0000000000 65535 f \n';
    offsets.forEach((offset) => {
        text += ('0000000000' + offset).slice(-10) + ' 00000 n \n';
    });
    text += 'trailer\n<< /Size ' + (objects.length + 1) + ' /Root 1 0 R >>\nstartxref\n' + xref + '\n%%EOF\n';
    files.ensureDir(path);
    files.write(path, text);
    return path;
}

/* 按字段类型给出示例值; 不能填写或没有合适的值时返回 undefined. */
function sampleValue(field) {
    let options = field.options || [];
    if (field.type === 'text') {
        let value = field.password ? '0000' : field.multiline ? 'AutoJs6\n3-Paper PDF' : 'AutoJs6';
        return field.maxLength ? value.slice(0, field.maxLength) : value;
    }
    if (field.type === 'checkbox') return !field.value;
    if (field.type === 'radio' || field.type === 'combo') return options.length > 0 ? options[options.length - 1].value : undefined;
    if (field.type === 'list' && options.length > 0) {
        return field.multiSelect ? options.slice(0, 2).map(o => o.value) : options[options.length - 1].value;
    }
    return undefined;
}

if (!pdf.isAvailable()) {
    summary.errors.push('PLUGIN_UNAVAILABLE');
    console.error('3-Paper PDF 插件未安装或未启用, 请先在插件中心安装并启用.');
} else {
    let source = args.path ? String(args.path) : fixture('form.pdf') || demoForm(outPath('demo-form.pdf'));
    summary.path = source;
    console.log('文档:', source);

    /* 1. 列出字段. */
    let fields = [];
    step('pdf.forms', () => {
        fields = pdf.forms(source);
        let count = pdf.info(source).formFieldCount;
        summary.forms = { count: count, names: fields.map(f => f.name) };
        console.log('表单字段:', fields.length, '个 | pdf.info 的 formFieldCount:', count);
        fields.forEach((f) => {
            let options = f.options ? ' | 选项: ' + f.options.map(o => o.value).join(', ') : '';
            let flags = (f.readOnly ? ' | 只读' : '') + (f.required ? ' | 必填' : '');
            console.log('  ' + f.name + ' (' + f.type + ', 第 ' + f.page + ' 页): ' + JSON.stringify(f.value) + options + flags);
        });
    });

    let values = {};
    fields.filter(f => !f.readOnly).forEach((f) => {
        let value = sampleValue(f);
        if (value !== undefined) values[f.name] = value;
    });
    if (!stopped && Object.keys(values).length === 0) {
        stopped = true;
        console.log('文档中没有可以填写的表单字段, 换一个带表单的 PDF 再试.');
    }

    /* 2. 填写并读回. */
    let filled = null;
    step('pdf.fillForm', () => {
        let result = pdf.fillForm(source, outPath('form-filled.pdf'), values);
        filled = result.path;
        let after = {};
        pdf.forms(filled).forEach((f) => {
            after[f.name] = f.value;
        });
        let matched = Object.keys(values).filter(name => JSON.stringify(after[name]) === JSON.stringify(values[name]));
        summary.fill = { filled: result.filled, matched: matched.length, warningCodes: result.warningCodes.slice() };
        console.log('已填写:', filled, '| 填写的字段数:', result.filled, '| 读回一致:', matched.length);
        Object.keys(values).forEach(name => console.log('  ' + name + ' = ' + JSON.stringify(after[name])));
        result.warnings.forEach(w => console.log('  提示:', w));
    });

    /* 3. 出错与恢复. */
    let text = fields.filter(f => f.type === 'text' && !f.readOnly && !f.password)[0];
    let checkbox = fields.filter(f => f.type === 'checkbox' && !f.readOnly)[0];
    if (filled && text) {
        step('出错与恢复', () => {
            let mixed = {};
            mixed['no.such.field'] = 'x';
            mixed[text.name] = null;
            let recover = { strict: codeOf(() => pdf.fillForm(filled, outPath('form-strict.pdf'), mixed)) };
            let result = pdf.fillForm(filled, outPath('form-restored.pdf'), mixed, { ignoreMissing: true });
            recover.ignoreMissing = result.warningCodes.slice();
            recover.restored = pdf.forms(result.path).filter(f => f.name === text.name)[0].value;
            recover.defaultValue = text.defaultValue;
            if (checkbox) {
                let wrong = {};
                wrong[checkbox.name] = 'yes';
                recover.wrongType = codeOf(() => pdf.fillForm(filled, outPath('form-wrong-type.pdf'), wrong));
            }
            summary.recover = recover;
            console.log('未知字段名:', recover.strict, '| ignoreMissing 的 warningCodes:', JSON.stringify(recover.ignoreMissing));
            console.log(text.name + ' 填 null 后:', JSON.stringify(recover.restored), '| 默认值:', JSON.stringify(recover.defaultValue));
            if (checkbox) console.log('给复选框 ' + checkbox.name + ' 填字符串:', recover.wrongType);
        });
    }

    /* 4. 扁平化: 同样的值填写后把全部字段并入页面内容. 字段名数组 (例如 flatten: ['name']) 只扁平化所列的字段. */
    step('flatten', () => {
        let result = pdf.fillForm(source, outPath('form-flat.pdf'), values, { flatten: true });
        let hits = pdf.search(result.path, 'AutoJs6');
        summary.flatten = {
            fields: pdf.forms(result.path).length,
            formFieldCount: pdf.info(result.path).formFieldCount,
            hits: hits.map(h => h.page),
        };
        console.log('已扁平化:', result.path, '| 剩余字段:', summary.flatten.fields, '| 搜索 AutoJs6 的命中页:', JSON.stringify(summary.flatten.hits));
    });
}

if (args.output) {
    files.write(String(args.output), JSON.stringify(summary));
}
