/**
 * 合并拆分与页面编辑 [v6.8.1+]
 *
 * pdf 模块由 3-Paper PDF 插件提供, 先在插件中心安装并启用.
 * 每个操作都写出新文件, 不改动原文档; 返回值含输出路径 path 与页数 pageCount (拆分返回 { paths }).
 * 演示:
 * 1. pdf.merge 合并多个文档. outline 为 'merge' (默认) 时每个输入在目录里占一项, 标题取 displayNames 中对应的名称
 *    (为 null 时用文件名去掉 .pdf), 原有目录放在该项下面; 'none' 不保留目录. 加密的输入用 passwords 给出所有者密码.
 * 2. pdf.split 拆分: every 每 n 页一份, ranges 按页码范围各成一份, namePattern 设定文件名 ({index} 为序号).
 * 3. pdf.extract 提取指定页, pdf.delete 删除指定页 (不能删掉全部页), pdf.reorder 按新顺序排列,
 *    pdf.rotate 在原有角度上再旋转 90 的倍数 (默认所有页, pages 选项指定页).
 * 4. pdf.open 打开文档对象, 一次打开后多次读取; doc.save 一次完成选页, 删页, 重排与旋转. 用完调用 close()
 *    (每个脚本至多同时打开 8 个文档).
 * 5. 每个方法都有 xxxAsync 形式, 返回 Promise, 调用 cancel() 可以取消 (以 CANCELLED 拒绝, 未写完的输出会被删除);
 *    onProgress 回调报告进度 { opId, percent, stage, current, total }, 同步方法同样支持.
 * 输出路径可以省略 (例如 pdf.merge([a, b])), 此时写到 /sdcard/Documents/3-Paper PDF/ 下, 返回值给出实际路径.
 * 可选参数 (engines.execScriptFile 的 arguments): path 为要操作的 PDF (默认为样例 fixtures/outline.pdf, 找不到时现场生成演示文档),
 * outDir 为输出目录 (默认为 /sdcard/Documents/3-Paper PDF/samples/pages), output 为写入结果摘要 (JSON) 的文件.
 */

let args = engines.myEngine().execArgv;
let base = files.join(files.getSdcardPath(), 'Documents', '3-Paper PDF');
let outDir = args.outDir ? String(args.outDir) : files.join(base, 'samples', 'pages');
let summary = { errors: [] };

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

function firstLine(path) {
    return pdf.text(path, { pages: [1] })[0].trim().split('\n')[0];
}

/* 没有样例文档时现场生成: 6 个单页文档 ("Page n of 6") 合并为一个 6 页的文档. */
function demoBook() {
    let pages = [];
    for (let i = 1; i <= 6; i++) {
        pages.push(pdf.fromText('Page ' + i + ' of 6', outPath('demo/page-' + i + '.pdf')).path);
    }
    return pdf.merge(pages, outPath('demo/book.pdf'), { outline: 'none' }).path;
}

function demoCover() {
    return pdf.fromText('Cover\n\nPage operations sample', outPath('demo/cover.pdf')).path;
}

function syncPart(book, cover) {
    /* 1. 合并: 封面用 displayNames 命名, 第二项为 null 时用文件名. */
    try {
        let merged = pdf.merge([cover, book], outPath('merged.pdf'), { displayNames: ['Cover', null] });
        let outline = pdf.outline(merged.path);
        summary.merge = { pageCount: merged.pageCount, outline: outline.map(o => o.title) };
        console.log('合并:', merged.pageCount, '页 | 目录:', summary.merge.outline.join(', '), '->', merged.path);
    } catch (e) {
        report('pdf.merge', e);
    }

    /* 2. 拆分. */
    try {
        let parts = pdf.split(book, outPath('split'), { every: 2 });
        let ranges = pdf.split(book, outPath('split-ranges'), { ranges: ['1-2', '5-'], namePattern: 'part-{index}' });
        summary.split = { every: parts.paths.length, ranges: ranges.paths.map(p => files.getName(p)) };
        console.log('每 2 页拆分:', parts.paths.length, '份 | 按范围拆分:', summary.split.ranges.join(', '));
    } catch (e) {
        report('pdf.split', e);
    }

    /* 3. 提取, 删除, 重排, 旋转. 页码范围可以写 '2-3', '1,6', '5-' (第 5 页到最后). */
    try {
        let extracted = pdf.extract(book, outPath('extract.pdf'), '2-3');
        let deleted = pdf.delete(book, outPath('delete.pdf'), '1,6');
        let reordered = pdf.reorder(book, outPath('reorder.pdf'), [6, 5, 4, 3, 2, 1]);
        let rotated = pdf.rotate(book, outPath('rotate.pdf'), 90, { pages: '1' });
        summary.edit = {
            extract: extracted.pageCount,
            delete: deleted.pageCount,
            reorderFirst: firstLine(reordered.path),
            rotation: pdf.info(rotated.path).pages[0].rotation,
        };
        console.log('提取 2-3:', extracted.pageCount, '页 | 删除 1,6:', deleted.pageCount, '页');
        console.log('倒序后第 1 页:', summary.edit.reorderFirst, '| 旋转后第 1 页角度:', summary.edit.rotation);
    } catch (e) {
        report('页面编辑', e);
    }

    /* 4. 文档对象: 打开一次, 多次读取, 最后用 save 一次完成选页与旋转. */
    try {
        let doc = pdf.open(book);
        try {
            let size = doc.pageSize(1);
            console.log('pdf.open:', doc.pageCount, '页 | 第 1 页', Math.round(size.width) + 'x' + Math.round(size.height), '点');
            let saved = doc.save(outPath('saved.pdf'), { pages: '1-3', rotate: { pages: '2', degrees: 90 } });
            summary.save = { pageCount: saved.pageCount };
            console.log('doc.save (前 3 页, 第 2 页转 90 度):', saved.pageCount, '页 ->', saved.path);
        } finally {
            doc.close();
        }
    } catch (e) {
        report('pdf.open', e);
    }
}

async function asyncPart(book, cover) {
    /* 5. 异步合并并显示进度. */
    try {
        let stages = [];
        let merged = await pdf.mergeAsync([cover, book, book], outPath('merged-async.pdf'), {
            onProgress: (p) => {
                if (stages.indexOf(p.stage) < 0) stages.push(p.stage);
                console.log('进度:', p.stage, p.percent + '%');
            },
        });
        summary.mergeAsync = { pageCount: merged.pageCount, stages: stages };
        console.log('mergeAsync:', merged.pageCount, '页');
    } catch (e) {
        report('pdf.mergeAsync', e);
    }

    /* 取消: cancel() 返回 true 表示已发出取消; 操作已经完成时返回 false, Promise 照常给出结果. */
    let task = pdf.mergeAsync([book, book, book, book, book, book, book, book], outPath('cancelled.pdf'));
    let cancelled = task.cancel();
    try {
        await task;
        summary.cancel = 'finished';
        console.log('取消前已经完成');
    } catch (e) {
        if (e instanceof pdf.PdfError && e.code === 'CANCELLED') {
            summary.cancel = 'CANCELLED';
            console.log('cancel() =', cancelled, '| 已取消, 输出文件存在:', files.exists(outPath('cancelled.pdf')));
        } else {
            report('取消', e);
        }
    }
}

if (!pdf.isAvailable()) {
    summary.errors.push('PLUGIN_UNAVAILABLE');
    console.error('3-Paper PDF 插件未安装或未启用, 请先在插件中心安装并启用.');
    if (args.output) files.write(String(args.output), JSON.stringify(summary));
} else {
    let book = args.path ? String(args.path) : fixture('outline.pdf') || demoBook();
    let cover = fixture('text.pdf') || demoCover();
    summary.path = book;
    console.log('文档:', book);
    syncPart(book, cover);
    asyncPart(book, cover).catch(e => report('异步演示', e)).then(() => {
        if (args.output) files.write(String(args.output), JSON.stringify(summary));
    });
}
