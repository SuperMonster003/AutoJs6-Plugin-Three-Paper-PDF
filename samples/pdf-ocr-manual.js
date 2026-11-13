/**
 * 扫描页文字识别 [v6.8.1+]
 *
 * pdf 模块由 3-Paper PDF 插件提供, 先在插件中心安装并启用; 文字识别由已安装的 OCR 插件 (例如 ML Kit OCR, Paddle OCR, Rapid OCR) 完成.
 * 演示两种做法:
 * 1. 手动: pdf.render 把扫描页渲染为图像 (OCR 建议 300 dpi), 交给 ocr.recognizeText 取文字, ocr.detect 取每段文字的位置,
 *    用完调用 recycle() 释放图像. 适合需要文字位置, 只识别部分区域或先做预处理的场合.
 * 2. 一步: pdf.text(path, { ocr: true }) 只识别没有文本层的页 (默认 300 dpi, 可用 ocrDpi 调整), 结果的 sources 标出每页的文字来自
 *    'text' (文本层) 还是 'ocr' (识别); ocr: 'always' 则识别范围内的每一页.
 * 没有可用的 OCR 插件时, ocr 模块会报错, pdf.text 的 OCR 分支报 OCR_UNAVAILABLE.
 * 可选参数 (engines.execScriptFile 的 arguments): path 为要识别的 PDF (默认为样例 fixtures/scanned.pdf, 找不到时现场生成一页扫描件),
 * page 为页码 (默认 1), output 为写入结果摘要 (JSON) 的文件.
 */

let args = engines.myEngine().execArgv;
let base = files.join(files.getSdcardPath(), 'Documents', '3-Paper PDF');
let page = args.page ? Number(args.page) : 1;
let summary = { page: page };
let path = null;

function fixture(name) {
    let local = files.path('./fixtures/' + name);
    if (files.exists(local)) return local;
    let shared = files.join(base, 'fixtures', name);
    return files.exists(shared) ? shared : null;
}

/*
 * 没有扫描件时现场生成: 文字页渲染成图片, 再由图片生成 PDF, 得到没有文字层的一页.
 * pageSize: 'A4' 让页面保持 A4 大小; 缺省的 'fit' 按图片像素定页面大小, 200 dpi 的图片会成为边长约为 A4 2.8 倍的页面,
 * 再按 300 dpi 渲染就超出图像大小上限 (LIMIT_EXCEEDED).
 */
function demoScan() {
    let dir = files.join(base, 'samples', 'ocr');
    let typed = pdf.fromText('Scanned receipt\n\nTotal: 128.50\nPaid by card', files.join(dir, 'demo-text.pdf'), { fontSize: 28 });
    let image = pdf.renderToFile(typed.path, 1, files.join(dir, 'demo-page.png'), { dpi: 200 });
    return pdf.fromImages([image.path], files.join(dir, 'demo-scan.pdf'), { pageSize: 'A4' }).path;
}

/* 1. 手动: 渲染后交给 ocr 模块. 文字层为空说明是扫描页 (纯图像). */
try {
    path = args.path ? String(args.path) : fixture('scanned.pdf') || demoScan();
    summary.path = path;
    console.log('文档:', path);
    let layer = pdf.text(path, { pages: [page] })[0];
    console.log('文本层:', layer.trim() ? layer.trim().length + ' 个字符' : '空 (扫描页)');

    let start = Date.now();
    let img = pdf.render(path, page, { dpi: 300 });
    try {
        let lines = ocr.recognizeText(img);
        let blocks = ocr.detect(img);
        summary.manual = {
            size: img.getWidth() + 'x' + img.getHeight(),
            lines: lines.slice(),
            blocks: blocks.length,
            ms: Date.now() - start,
        };
        console.log('手动识别:', summary.manual.size, '|', lines.length, '行 |', blocks.length, '个文字块 |', summary.manual.ms, 'ms');
        lines.forEach(line => console.log('  ' + line));
        if (blocks.length > 0) {
            let first = blocks[0];
            console.log('第一个文字块:', first.label, '| 位置:', first.bounds);
        }
    } finally {
        img.recycle();
    }
} catch (e) {
    /* 没有 OCR 插件时 ocr 模块在这里报错; PdfError 带有 code (例如 PLUGIN_UNAVAILABLE 表示未安装 3-Paper PDF). */
    summary.manual = { code: e.code || null, message: String(e.message) };
    console.error('手动识别失败 [' + (e.code || e.name) + ']: ' + e.message);
}

/* 2. 一步: pdf.text 的 OCR 分支. onProgress 的 stage 为 'ocr' 时表示正在识别. */
try {
    let start = Date.now();
    let pages = pdf.text(path, {
        pages: [page],
        ocr: true,
        onProgress: (p) => console.log('进度:', p.stage, p.percent + '%'),
    });
    summary.auto = { text: pages[0], sources: pages.sources.slice(), ms: Date.now() - start };
    console.log('pdf.text({ ocr: true }):', pages.sources[0], '|', summary.auto.ms, 'ms');
    console.log(pages[0]);
} catch (e) {
    /* 没有可用的 OCR 插件时报 OCR_UNAVAILABLE. */
    summary.auto = { code: e.code || null, message: String(e.message) };
    console.error('pdf.text 的 OCR 分支失败 [' + (e.code || e.name) + ']: ' + e.message);
}

if (args.output) {
    files.write(String(args.output), JSON.stringify(summary));
}
