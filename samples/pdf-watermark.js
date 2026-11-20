/**
 * 水印与页码 [v6.8.1+]
 *
 * pdf 模块由 3-Paper PDF 插件提供, 先在插件中心安装并启用. 本样例需要 AutoJs6 6.8.1 或以上与 3-Paper PDF 1.1.0 或以上:
 * 插件为 1.0.x 时 pdf.watermark 等 1.1.0 的新方法失败于 UNSUPPORTED, 本样例给出提示后结束.
 * 演示:
 * 1. pdf.watermark 加文字水印: 默认位于页面中央, 逆时针旋转 45 度, 灰色 (#808080), 不透明度 0.3.
 * 2. 平铺: tile: true 把水印 (旋转后) 铺满整页, gapX / gapY 为相邻两份的间距 (磅); pages 只加在部分页.
 * 3. 图片水印: image 为 PNG, JPEG 或 WebP 文件, width / height / scale 定大小; position 为九个位置名之一
 *    ('top-left', 'top-center', ..., 'bottom-right') 或 [x, y], 贴着页边的一侧向内缩进 margin (默认 36 磅).
 * 4. pdf.stamp 加页码: pageNumbers: true 在每页底部居中加 '{page} / {total}'.
 * 5. 页码, 页眉, 页脚与印章一起加: 封面不加页码, 正文从 1 开始编号 ({ pages: '2-', start: 1 }); 文字可用占位符 {page}, {total},
 *    {date} 与 {name}; items 为文字或图片印章的数组 (每次至多 64 个, 其中图片至多 16 个).
 * layer: 'under' 把水印画在页面内容之下 (扫描件的整页图像会把它完全遮住). 数值超出范围时失败于 INVALID_ARGUMENT, 消息指出是哪个选项.
 * 可选参数 (engines.execScriptFile 的 arguments): path 为要加水印的 PDF (默认为样例 fixtures/outline.pdf, 找不到时现场生成几页文字),
 * image 为图片水印用的图片 (默认把第 1 页渲染成宽 160 像素的小图), outDir 为输出目录 (默认为 /sdcard/Documents/3-Paper PDF/samples/watermark),
 * output 为写入结果摘要 (JSON) 的文件.
 */

let args = engines.myEngine().execArgv;
let base = files.join(files.getSdcardPath(), 'Documents', '3-Paper PDF');
let outDir = args.outDir ? String(args.outDir) : files.join(base, 'samples', 'watermark');
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

/*
 * 没有样例文档时现场生成几页文字. 四周留 72 pt: fromText 的默认边距 (36 pt) 与页码, 页眉页脚的默认边距相同,
 * 不加大的话页码会压在每页最后一行正文上.
 */
function demoSource() {
    let lines = ['Quarterly report', ''];
    for (let i = 1; i <= 100; i++) {
        lines.push('Line ' + i + ': watermark and page number sample of 3-Paper PDF.');
    }
    return pdf.fromText(lines.join('\n'), outPath('demo-source.pdf'), { fontSize: 14, margin: 72 }).path;
}

/* 第 n 页的文字. */
function pageText(path, n) {
    return pdf.text(path, { pages: [n] })[0];
}

if (!pdf.isAvailable()) {
    summary.errors.push('PLUGIN_UNAVAILABLE');
    console.error('3-Paper PDF 插件未安装或未启用, 请先在插件中心安装并启用.');
} else {
    let source = args.path ? String(args.path) : fixture('outline.pdf') || demoSource();
    summary.path = source;
    console.log('文档:', source, '|', pdf.info(source).pageCount, '页');

    /* 1. 文字水印, 全部用默认值. 中文等文字使用系统字体; fontPath 可以指定 TrueType 或 OpenType 字体文件. */
    step('pdf.watermark', () => {
        let result = pdf.watermark(source, outPath('draft.pdf'), { text: '草稿 DRAFT' });
        summary.watermark = { pageCount: result.pageCount };
        console.log('文字水印:', result.path, '|', result.pageCount, '页');
    });

    /* 2. 平铺的红色小字, 只加在第 1 - 2 页. */
    step('平铺水印', () => {
        let result = pdf.watermark(source, outPath('confidential.pdf'), {
            text: 'CONFIDENTIAL',
            fontSize: 18,
            color: '#C62828',
            opacity: 0.15,
            rotation: 30,
            tile: true,
            gapX: 96,
            gapY: 96,
            pages: '1-2',
        });
        summary.tile = { pageCount: result.pageCount };
        console.log('平铺水印:', result.path);
    });

    /* 3. 图片水印: 宽 120 磅 (高度按图片的宽高比), 位于右下角. */
    let logo = null;
    step('图片水印', () => {
        logo = args.image ? String(args.image) : pdf.renderToFile(source, 1, outPath('logo.png'), { width: 160 }).path;
        let result = pdf.watermark(source, outPath('logo-watermark.pdf'), {
            image: logo,
            width: 120,
            position: 'bottom-right',
            opacity: 0.5,
        });
        summary.image = { pageCount: result.pageCount };
        console.log('图片水印:', result.path, '| 图片:', logo);
    });

    /* 4. 页码. */
    step('pdf.stamp 页码', () => {
        let result = pdf.stamp(source, outPath('numbered.pdf'), { pageNumbers: true });
        let expected = result.pageCount + ' / ' + result.pageCount;
        summary.pageNumbers = { pageCount: result.pageCount, found: pageText(result.path, result.pageCount).indexOf(expected) >= 0 };
        console.log('页码:', result.path, '| 最后一页有 "' + expected + '":', summary.pageNumbers.found);
    });

    /* 5. 页码, 页眉, 页脚与印章. 顶层的 fontSize, color, margin 与 pages 是页码, 页眉与页脚的默认值, 不作用于 items. */
    step('pdf.stamp 页眉页脚与印章', () => {
        let items = [{
            type: 'text',
            text: 'APPROVED',
            fontSize: 48,
            color: '#2E7D32',
            opacity: 0.5,
            rotation: 15,
            position: 'top-right',
            pages: 1,
        }];
        if (logo) {
            items.push({ type: 'image', image: logo, width: 72, position: 'top-left', pages: 1 });
        }
        let result = pdf.stamp(source, outPath('stamped.pdf'), {
            pageNumbers: { pages: '2-', start: 1, format: '- {page} -' },
            header: { pages: '2-', left: '{name}', right: '{date}' },
            footer: { right: '3-Paper PDF', fontSize: 8, color: '#666666' },
            items: items,
        });
        summary.stamp = { pageCount: result.pageCount, items: items.length, secondPage: pageText(result.path, 2).indexOf('- 1 -') >= 0 };
        console.log('页眉页脚与印章:', result.path, '| 第 2 页的页码为 "- 1 -":', summary.stamp.secondPage);
        result.warnings.forEach(w => console.log('  提示:', w));
    });
}

if (args.output) {
    files.write(String(args.output), JSON.stringify(summary));
}
