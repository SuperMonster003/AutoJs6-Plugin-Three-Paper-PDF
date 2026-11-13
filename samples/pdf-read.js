/**
 * 读取文字与目录 [v6.8.1+]
 *
 * pdf 模块由 3-Paper PDF 插件提供, 先在插件中心安装并启用.
 * 演示:
 * 1. pdf.info 读取页数, 每页尺寸 (单位为点, 1 点 = 1/72 英寸) 与旋转角度, 标题, 是否加密与权限.
 * 2. pdf.text 按页提取文字, 返回数组 (第 n 项是所选第 n 页的文字, pages 属性给出对应页码);
 *    layout 默认为 'reading' (按阅读顺序), 'raw' 按内容流顺序.
 * 3. pdf.outline 读取目录 (书签): 每项含 title, page (没有目标页时为 null), level 与 children.
 * 4. pdf.search 搜索关键词: 每个结果含页码 page, 位置 rect ([left, top, right, bottom], 单位为点) 与上下文 context;
 *    关键词跨行时 rects 给出每一行的部分, rect 是它们的并集.
 * 5. 加密文档: 不给密码时报 PASSWORD_REQUIRED, 密码不对时报 WRONG_PASSWORD, 用 password 选项给出密码.
 * 页码从 1 开始; pages 可以写成 '1-3,5' 这样的字符串, 也可以写成 [1, 2] 这样的数组.
 * 出错时抛出 pdf.PdfError, 其 code 说明原因 (例如 FILE_NOT_FOUND, INVALID_PDF, PAGE_OUT_OF_RANGE).
 * 可选参数 (engines.execScriptFile 的 arguments): path 为要读取的 PDF (默认为样例 fixtures/outline.pdf, 找不到时现场生成演示文档),
 * outDir 为生成演示文档的目录, output 为写入结果摘要 (JSON) 的文件.
 */

let args = engines.myEngine().execArgv;
let base = files.join(files.getSdcardPath(), 'Documents', '3-Paper PDF');
let outDir = args.outDir ? String(args.outDir) : files.join(base, 'samples', 'read');
let summary = { errors: [] };

function fixture(name) {
    let local = files.path('./fixtures/' + name);
    if (files.exists(local)) return local;
    let shared = files.join(base, 'fixtures', name);
    return files.exists(shared) ? shared : null;
}

function outPath(name) {
    let path = files.join(outDir, name);
    files.ensureDir(path);
    return path;
}

function report(label, e) {
    console.error(label + ' 失败 [' + (e.code || e.name) + ']: ' + e.message);
    summary.errors.push(label + ': ' + (e.code || e.name));
}

/* 没有样例文档时现场生成: 两段文字各生成一个 PDF, 再合并为一个; 合并时 displayNames 成为目录项. */
function demoDocument() {
    let one = pdf.fromText('Chapter 1\n\nThe quick brown fox jumps over the lazy dog.\nSearch target: needle', outPath('demo-1.pdf'));
    let two = pdf.fromText('Chapter 2\n\nAnother needle in the haystack.', outPath('demo-2.pdf'));
    return pdf.merge([one.path, two.path], outPath('demo.pdf'), { displayNames: ['Chapter 1', 'Chapter 2'] }).path;
}

function printOutline(items, indent) {
    items.forEach((item) => {
        console.log(indent + item.title + ' -> ' + (item.page === null ? '(无目标页)' : '第 ' + item.page + ' 页'));
        printOutline(item.children, indent + '    ');
    });
}

function countOutline(items) {
    return items.reduce((n, item) => n + 1 + countOutline(item.children), 0);
}

if (!pdf.isAvailable()) {
    summary.errors.push('PLUGIN_UNAVAILABLE');
    console.error('3-Paper PDF 插件未安装或未启用, 请先在插件中心安装并启用.');
} else {
    let path = args.path ? String(args.path) : fixture('outline.pdf') || demoDocument();
    summary.path = path;
    console.log('文档:', path);

    /* 1. 文档信息. */
    try {
        let info = pdf.info(path);
        let first = info.pages[0];
        summary.info = { pageCount: info.pageCount, title: info.title, encrypted: info.encrypted, firstPage: first.width + 'x' + first.height };
        console.log('页数:', info.pageCount, '| 标题:', info.title, '| 加密:', info.encrypted, '| 大小:', info.sizeBytes, '字节');
        console.log('第 1 页:', first.width + ' x ' + first.height, '点, 旋转', first.rotation, '度');
    } catch (e) {
        report('pdf.info', e);
    }

    /* 2. 提取文字: 读前两页 (只有一页时读第 1 页). */
    try {
        let texts = pdf.text(path, { pages: '1-2' });
        summary.text = { pages: texts.pages.slice(), chars: texts.map(t => t.length) };
        texts.forEach((t, i) => console.log('第 ' + texts.pages[i] + ' 页文字:', JSON.stringify(t.trim())));
        let raw = pdf.text(path, { pages: [1], layout: 'raw' })[0];
        console.log('第 1 页 (raw 顺序):', raw.trim().length, '个字符');
    } catch (e) {
        if (e.code === 'PAGE_OUT_OF_RANGE') {
            summary.text = { pages: [1], chars: [pdf.text(path, { pages: [1] })[0].length] };
            console.log('文档只有一页, 只读第 1 页');
        } else {
            report('pdf.text', e);
        }
    }

    /* 3. 目录. */
    try {
        let outline = pdf.outline(path);
        summary.outline = countOutline(outline);
        console.log('目录:', outline.length ? summary.outline + ' 项' : '没有目录');
        printOutline(outline, '  ');
    } catch (e) {
        report('pdf.outline', e);
    }

    /* 4. 搜索: wholeWord 只匹配整词, caseSensitive 区分大小写, limit 限制结果数. */
    try {
        let hits = pdf.search(path, 'needle', { wholeWord: true, limit: 20 });
        summary.search = hits.map(h => h.page);
        console.log('搜索 "needle":', hits.length, '处');
        hits.forEach((h) => {
            let where = h.rect ? '[' + h.rect.map(v => Math.round(v)).join(', ') + ']' : '(未知)';
            console.log('  第 ' + h.page + ' 页, 位置 ' + where + ':', h.context);
        });
    } catch (e) {
        report('pdf.search', e);
    }

    /* 5. 加密文档: 先不给密码, 再用打开密码 (用户密码) 读取. 演示文档的打开密码为 paper-user. */
    try {
        let locked = args.path ? null : fixture('encrypted.pdf');
        if (!locked) {
            locked = pdf.encrypt(path, outPath('demo-encrypted.pdf'), {
                userPassword: 'paper-user',
                ownerPassword: 'paper-owner',
                permissions: { copy: false, modify: false },
            }).path;
        }
        summary.encrypted = {};
        try {
            pdf.info(locked);
        } catch (e) {
            if (!(e instanceof pdf.PdfError)) throw e;
            summary.encrypted.withoutPassword = e.code;
            console.log('不给密码:', e.code);
        }
        try {
            pdf.info(locked, { password: 'wrong' });
        } catch (e) {
            if (!(e instanceof pdf.PdfError)) throw e;
            summary.encrypted.wrongPassword = e.code;
            console.log('密码不对:', e.code);
        }
        let info = pdf.info(locked, { password: 'paper-user' });
        summary.encrypted.permissions = info.permissions;
        console.log('用打开密码读取: 加密', info.encrypted, '| 权限:', JSON.stringify(info.permissions));
        console.log('第 1 页文字:', JSON.stringify(pdf.text(locked, { pages: [1], password: 'paper-user' })[0].trim()));
    } catch (e) {
        report('加密文档', e);
    }
}

if (args.output) {
    files.write(String(args.output), JSON.stringify(summary));
}
