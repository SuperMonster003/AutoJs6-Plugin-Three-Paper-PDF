/**
 * 页面渲染与图片导出 [v6.8.1+]
 *
 * pdf 模块由 3-Paper PDF 插件提供, 先在插件中心安装并启用.
 * 演示:
 * 1. pdf.render 把一页渲染为图像 (默认 144 dpi; dpi, scale, width, height 至多给一个, 只给 width 或 height 时按比例缩放),
 *    得到的图像可以交给 images 模块处理, 用完调用 recycle() 释放. transparent: true 保留透明背景 (默认为白底).
 * 2. pdf.renderToFile 直接写成图片文件: format 为 'png' (默认), 'jpeg' 或 'webp', quality (1-100) 用于 jpeg 与 webp;
 *    返回 { path, width, height, sizeBytes }.
 * 3. pdf.images 列出页面里嵌入的图片 ({ page, index, width, height, format }), pdf.extractImages 把它们导出为文件,
 *    namePattern 设定文件名 ({index} 为序号), 返回 { paths }.
 * 省略输出路径时 (例如 pdf.renderToFile(path, 1)), 文件写到 /sdcard/Documents/3-Paper PDF/ 下, 返回值给出实际路径.
 * 可选参数 (engines.execScriptFile 的 arguments): path 为要渲染的 PDF (默认为样例 fixtures/images.pdf, 找不到时现场生成演示文档),
 * outDir 为输出目录 (默认为 /sdcard/Documents/3-Paper PDF/samples/render), output 为写入结果摘要 (JSON) 的文件.
 */

let args = engines.myEngine().execArgv;
let base = files.join(files.getSdcardPath(), 'Documents', '3-Paper PDF');
let outDir = args.outDir ? String(args.outDir) : files.join(base, 'samples', 'render');
let summary = { errors: [] };

function fixture(name) {
    let local = files.path('./fixtures/' + name);
    if (files.exists(local)) return local;
    let shared = files.join(base, 'fixtures', name);
    return files.exists(shared) ? shared : null;
}

/* pdf 模块会自动创建输出文件所在的目录; images.save 不会, 所以这里先建好. */
function outPath(name) {
    let path = files.join(outDir, name);
    files.ensureDir(path);
    return path;
}

function report(label, e) {
    console.error(label + ' 失败 [' + (e.code || e.name) + ']: ' + e.message);
    summary.errors.push(label + ': ' + (e.code || e.name));
}

/* 没有样例文档时现场生成: 先把一页文字渲染成 PNG, 再把这张图片做成 PDF, 这样文档里就有一张嵌入的图片. */
function demoDocument() {
    let text = pdf.fromText('Rendering sample\n\nThis page becomes a picture.', outPath('demo-text.pdf'));
    let picture = pdf.renderToFile(text.path, 1, outPath('demo-picture.png'), { dpi: 72 });
    return pdf.fromImages([picture.path], outPath('demo.pdf')).path;
}

if (!pdf.isAvailable()) {
    summary.errors.push('PLUGIN_UNAVAILABLE');
    console.error('3-Paper PDF 插件未安装或未启用, 请先在插件中心安装并启用.');
} else {
    let path = args.path ? String(args.path) : fixture('images.pdf') || demoDocument();
    summary.path = path;
    console.log('文档:', path);

    /* 1. 渲染为图像, 交给 images 模块保存; 用完 recycle(). */
    try {
        let img = pdf.render(path, 1, { dpi: 150 });
        try {
            let file = outPath('page-1.png');
            images.save(img, file);
            summary.render = { size: img.getWidth() + 'x' + img.getHeight(), file: file };
            console.log('pdf.render (150 dpi):', summary.render.size, '->', file);
        } finally {
            img.recycle();
        }
        let thumb = pdf.render(path, 1, { width: 240 });
        console.log('pdf.render (宽 240):', thumb.getWidth() + 'x' + thumb.getHeight());
        thumb.recycle();
    } catch (e) {
        report('pdf.render', e);
    }

    /* 2. 直接写成图片文件. */
    try {
        let jpeg = pdf.renderToFile(path, 1, outPath('page-1.jpg'), { format: 'jpeg', quality: 85, width: 800 });
        summary.renderToFile = { size: jpeg.width + 'x' + jpeg.height, sizeBytes: jpeg.sizeBytes };
        console.log('pdf.renderToFile (jpeg, 宽 800):', jpeg.width + 'x' + jpeg.height, '|', jpeg.sizeBytes, '字节 ->', jpeg.path);
    } catch (e) {
        report('pdf.renderToFile', e);
    }

    /* 3. 嵌入的图片: 先列出, 再导出. */
    try {
        let list = pdf.images(path);
        summary.images = list.map(i => i.format + ' ' + i.width + 'x' + i.height);
        console.log('嵌入图片:', list.length, '张');
        list.forEach(i => console.log('  第 ' + i.page + ' 页 #' + i.index + ':', i.format, i.width + 'x' + i.height));
        /* 输出目录不存在时自动创建; 文件扩展名按图片格式决定. */
        let saved = pdf.extractImages(path, files.join(outDir, 'images'), { namePattern: 'img-{index}' });
        summary.extracted = saved.paths.map(p => files.getName(p));
        console.log('导出:', summary.extracted.join(', '));
    } catch (e) {
        report('嵌入图片', e);
    }
}

if (args.output) {
    files.write(String(args.output), JSON.stringify(summary));
}
