/**
 * 压缩 PDF [v6.8.1+]
 *
 * pdf 模块由 3-Paper PDF 插件提供, 先在插件中心安装并启用. 本样例需要 AutoJs6 6.8.1 或以上与 3-Paper PDF 1.1.0 或以上:
 * 插件为 1.0.x 时 pdf.compress 失败于 UNSUPPORTED, 本样例给出提示后结束.
 * pdf.compress 减小 PDF 的体积, 主要依靠降低图片的分辨率: 有效分辨率高于 imageDpi 的图片缩小后以 JPEG (质量 imageQuality) 重新编码,
 * 重新编码后不比原图小时保留原图; 另外压缩未压缩的数据流, 丢弃不再引用的对象, 并以对象流写出 (objectStreams, 默认 true).
 * 演示:
 * 1. 预设 preset: 'low' (96 dpi, 质量 60), 'medium' (默认, 150 dpi, 质量 75), 'high' (220 dpi, 质量 85).
 * 2. 自定义: imageDpi (36 至 1200) 与 imageQuality (1 至 100) 优先于预设; removeMetadata: true 删除 XMP 元数据,
 *    文档信息 (producer 除外) 与页面缩略图.
 * 3. 结果的 sourceBytes 与 sizeBytes 为压缩前后的大小, images 给出图片总数, 重新编码与未处理的数目. 输出不比源文件小时照样写出,
 *    warningCodes 含 OUTPUT_NOT_SMALLER, 由脚本决定是否保留: 本样例删除这样的输出.
 * 可选参数 (engines.execScriptFile 的 arguments): path 为要压缩的 PDF (默认为样例 fixtures/scanned-multi.pdf, 找不到时现场生成一页 300 dpi 的扫描件),
 * outDir 为输出目录 (默认为 /sdcard/Documents/3-Paper PDF/samples/compress), output 为写入结果摘要 (JSON) 的文件.
 */

let args = engines.myEngine().execArgv;
let base = files.join(files.getSdcardPath(), 'Documents', '3-Paper PDF');
let outDir = args.outDir ? String(args.outDir) : files.join(base, 'samples', 'compress');
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

/* 没有样例文档时现场生成: 文字页按 300 dpi 渲染成 JPEG, 再放到 A4 页面上, 相当于一页 300 dpi 的扫描件. */
function demoScan() {
    let typed = pdf.fromText('Scanned contract\n\nPage 1\n\nThe quick brown fox jumps over the lazy dog.', outPath('demo-text.pdf'), { fontSize: 20 });
    let image = pdf.renderToFile(typed.path, 1, outPath('demo-page.jpg'), { dpi: 300, format: 'jpeg', quality: 95 });
    return pdf.fromImages([image.path], outPath('demo-scan.pdf'), { pageSize: 'A4' }).path;
}

/* 打印一次压缩的结果; 输出没有变小 (OUTPUT_NOT_SMALLER) 时删除它. 返回写入摘要的数值. */
function show(label, result) {
    let ratio = result.sourceBytes ? Math.round(result.sizeBytes * 100 / result.sourceBytes) + '%' : '?';
    let images = { total: result.images.total, recompressed: result.images.recompressed, skipped: result.images.skipped };
    console.log(label + ':', result.sourceBytes, '->', result.sizeBytes, '字节 (' + ratio + ') | 图片: 共', images.total, '张, 重新编码',
        images.recompressed, '张, 未处理', images.skipped, '张 | 对象流:', result.objectStreams);
    result.warnings.forEach(w => console.log('  提示:', w));
    let kept = result.warningCodes.indexOf('OUTPUT_NOT_SMALLER') < 0;
    if (!kept) {
        files.remove(result.path);
        console.log('  没有变小, 已删除输出.');
    }
    return { sourceBytes: result.sourceBytes, sizeBytes: result.sizeBytes, images: images, objectStreams: result.objectStreams, kept: kept };
}

if (!pdf.isAvailable()) {
    summary.errors.push('PLUGIN_UNAVAILABLE');
    console.error('3-Paper PDF 插件未安装或未启用, 请先在插件中心安装并启用.');
} else {
    let source = args.path ? String(args.path) : fixture('scanned-multi.pdf') || demoScan();
    let info = pdf.info(source);
    summary.path = source;
    console.log('文档:', source, '|', info.pageCount, '页,', info.sizeBytes, '字节');

    /* 1. 预设. onProgress 的 stage 依次为 'opening', 'processing' (按页), 'writing' 与 'verifying'. */
    step('preset: low', () => {
        summary.low = show('preset: low', pdf.compress(source, outPath('low.pdf'), {
            preset: 'low',
            onProgress: p => console.log('进度:', p.stage, p.percent + '%'),
        }));
    });
    step('preset: high', () => {
        summary.high = show('preset: high', pdf.compress(source, outPath('high.pdf'), { preset: 'high' }));
    });

    /* 2. 自定义分辨率与质量, 并删除元数据. */
    step('自定义', () => {
        summary.custom = show('72 dpi, 质量 50, 删除元数据', pdf.compress(source, outPath('custom.pdf'), {
            imageDpi: 72,
            imageQuality: 50,
            removeMetadata: true,
        }));
    });
}

if (args.output) {
    files.write(String(args.output), JSON.stringify(summary));
}
