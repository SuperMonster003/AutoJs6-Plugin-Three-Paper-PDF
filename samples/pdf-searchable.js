/**
 * 生成可搜索 PDF [v6.8.1+]
 *
 * pdf 模块由 3-Paper PDF 插件提供, 先在插件中心安装并启用; 文字识别由已安装的 OCR 插件 (ML Kit OCR, Paddle OCR 或 Rapid OCR) 完成.
 * 本样例需要 AutoJs6 6.8.1 或以上与 3-Paper PDF 1.1.0 或以上: 插件为 1.0.x 时 pdf.makeSearchable 等 1.1.0 的新方法失败于 UNSUPPORTED,
 * 没有可用的 OCR 插件时 pdf.makeSearchable 失败于 OCR_UNAVAILABLE, 本样例都给出提示后结束.
 * 扫描件只有图像, 不能搜索, 也不能选中文字. 演示:
 * 1. pdf.makeSearchable 识别没有文字的页, 写入一层不可见的文字 (文本层): 页面的外观不变, 但可以搜索, 选中与复制.
 *    ocrDpi 为识别时的渲染分辨率 (默认 300), ocrOptions 选择 OCR 引擎; onProgress 先报告识别 (stage 为 'ocr'), 再报告写入.
 *    结果的 ocrPages, layerPages, skippedPages 与 lines 为识别的页数, 写入文本层的页数, 跳过的页数与写入的行数.
 * 2. 写出的文档能用 pdf.text 与 pdf.search 读到识别出的文字.
 * 3. 已有文字的页默认跳过 (计入 skippedPages), 不需要 OCR 引擎; force: true 时照样识别并写入.
 * 4. 已有识别结果时 (例如自己调用 ocr.detect, 或经过人工校对) 用 pdf.addTextLayer 直接写入: 每页给出识别所用图像的像素尺寸,
 *    每行给出文字与它在图像中的像素区域 [left, top, right, bottom]; 插件按图像与页面的比例换算位置.
 * 可选参数 (engines.execScriptFile 的 arguments): path 为扫描件 (默认为样例 fixtures/scanned.pdf, 找不到时现场生成一页扫描件),
 * outDir 为输出目录 (默认为 /sdcard/Documents/3-Paper PDF/samples/searchable), output 为写入结果摘要 (JSON) 的文件.
 */

let args = engines.myEngine().execArgv;
let base = files.join(files.getSdcardPath(), 'Documents', '3-Paper PDF');
let outDir = args.outDir ? String(args.outDir) : files.join(base, 'samples', 'searchable');
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

/*
 * 运行一个步骤. 插件为 1.0.x 时 1.1.0 的新方法失败于 UNSUPPORTED, 没有可用的 OCR 插件时失败于 OCR_UNAVAILABLE:
 * 给出提示, 不再运行后面的步骤.
 */
function step(label, body) {
    if (stopped) return;
    try {
        body();
    } catch (e) {
        if (e.code === 'UNSUPPORTED') {
            stopped = true;
            summary.unsupported = String(e.message);
            console.error(label + ' 不可用: ' + e.message);
            console.error('本样例用到 3-Paper PDF 1.1.0 的新方法. 已安装的插件为 1.0.x 时, 请在插件中心把它更新到 1.1.0 或以上, 再运行本样例.');
        } else if (e.code === 'OCR_UNAVAILABLE') {
            stopped = true;
            summary.ocrUnavailable = String(e.message);
            console.error(label + ': 没有可用的 OCR 引擎. ' + e.message);
            console.error('生成可搜索 PDF 要先安装并启用一个 OCR 插件 (ML Kit OCR, Paddle OCR 或 Rapid OCR), 再运行本样例.');
        } else {
            report(label, e);
        }
    }
}

/*
 * 没有扫描件时现场生成: 文字页渲染成图片, 再由图片生成 PDF, 得到没有文字层的一页.
 * pageSize: 'A4' 让页面保持 A4 大小; 缺省的 'fit' 按图片像素定页面大小, 再按 300 dpi 识别时会超出图像大小上限 (LIMIT_EXCEEDED).
 */
function demoScan() {
    let typed = pdf.fromText('Scanned receipt\n\nTotal: 128.50\nPaid by card', outPath('demo-text.pdf'), { fontSize: 28 });
    let image = pdf.renderToFile(typed.path, 1, outPath('demo-page.png'), { dpi: 200 });
    return pdf.fromImages([image.path], outPath('demo-scan.pdf'), { pageSize: 'A4' }).path;
}

if (!pdf.isAvailable()) {
    summary.errors.push('PLUGIN_UNAVAILABLE');
    console.error('3-Paper PDF 插件未安装或未启用, 请先在插件中心安装并启用.');
} else {
    let source = args.path ? String(args.path) : fixture('scanned.pdf') || demoScan();
    summary.path = source;
    console.log('文档:', source, '| 第 1 页的文字层:', pdf.text(source, { pages: [1] })[0].trim() ? '有文字' : '空 (扫描页)');

    /* 1. 识别并写入文本层. */
    let searchable = null;
    step('pdf.makeSearchable', () => {
        let stages = [];
        let start = Date.now();
        let result = pdf.makeSearchable(source, outPath('searchable.pdf'), {
            ocrDpi: 300,
            onProgress: (p) => {
                if (stages.indexOf(p.stage) < 0) stages.push(p.stage);
                console.log('进度:', p.stage, p.percent + '%');
            },
        });
        searchable = result.path;
        summary.makeSearchable = {
            pageCount: result.pageCount,
            ocrPages: result.ocrPages,
            layerPages: result.layerPages,
            skippedPages: result.skippedPages,
            lines: result.lines,
            stages: stages,
            ms: Date.now() - start,
        };
        console.log('可搜索 PDF:', searchable, '| 识别', result.ocrPages, '页, 写入', result.layerPages, '页 (' + result.lines + ' 行), 跳过',
            result.skippedPages, '页 |', summary.makeSearchable.ms, 'ms');
    });

    /* 2. 读回识别出的文字, 再用其中第一个单词搜索. */
    if (searchable) {
        step('读回文本层', () => {
            let text = pdf.text(searchable).join('\n');
            let word = (text.match(/[A-Za-z0-9]{3,}/) || [null])[0];
            let hits = word ? pdf.search(searchable, word) : [];
            summary.read = { chars: text.trim().length, word: word, hits: hits.map(h => h.page) };
            console.log('文本层:', summary.read.chars, '个字符 | 搜索', JSON.stringify(word), '的命中页:', JSON.stringify(summary.read.hits));
            console.log(text.trim());
        });

        /* 3. 再处理一次: 每页都已有文字, 全部跳过. */
        step('跳过已有文字的页', () => {
            let again = pdf.makeSearchable(searchable, outPath('searchable-again.pdf'));
            summary.again = { ocrPages: again.ocrPages, skippedPages: again.skippedPages };
            console.log('再处理一次: 识别', again.ocrPages, '页, 跳过', again.skippedPages, '页');
        });
    }

    /* 4. 自己识别第 1 页后写入. 渲染的分辨率不限, 位置按图像与页面的比例换算. */
    step('pdf.addTextLayer', () => {
        let img = pdf.render(source, 1, { dpi: 300 });
        let layer;
        try {
            let lines = ocr.detect(img).map(block => ({
                text: block.label,
                bounds: [block.bounds.left, block.bounds.top, block.bounds.right, block.bounds.bottom],
            }));
            layer = { page: 1, width: img.getWidth(), height: img.getHeight(), lines: lines };
        } finally {
            img.recycle();
        }
        let result = pdf.addTextLayer(source, outPath('text-layer-p1.pdf'), [layer]);
        summary.addTextLayer = { detected: layer.lines.length, layerPages: result.layerPages, skippedPages: result.skippedPages, lines: result.lines };
        console.log('addTextLayer:', result.path, '| ocr.detect 给出', layer.lines.length, '行, 写入', result.layerPages, '页 (' + result.lines + ' 行)');
    });
}

if (args.output) {
    files.write(String(args.output), JSON.stringify(summary));
}
