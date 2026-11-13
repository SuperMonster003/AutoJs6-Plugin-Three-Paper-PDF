/**
 * 在查看器中打开 [v6.8.1+]
 *
 * pdf 模块由 3-Paper PDF 插件提供, 先在插件中心安装并启用.
 * pdf.view 用 3-Paper PDF 的查看器打开文档, 返回查看器会话:
 * - 选项: page 为起始页 (从 1 开始), password 为打开密码, search 为打开后要搜索的关键词.
 * - 事件: 'page' ({ page, pageCount }, 用户翻页时触发), 'close' ({ reason }, 会话结束时触发, 也是最后一个事件).
 * - 方法: goTo(page) 跳到指定页, state() 返回 { page, pageCount, zoom, token, visible },
 *   close() 只断开会话 (查看器留给用户), close({ finish: true }) 同时关闭查看器.
 * 会话打开期间脚本保持运行; 脚本先结束时会话随之结束, 查看器留给用户.
 * 可选参数 (engines.execScriptFile 的 arguments): path 为要查看的 PDF (默认为样例 fixtures/outline.pdf, 找不到时现场生成),
 * autoCloseMs 为自动关闭的毫秒数 (默认 0, 表示等用户关闭查看器), output 为写入结果摘要 (JSON) 的文件.
 */

let args = engines.myEngine().execArgv;
let base = files.join(files.getSdcardPath(), 'Documents', '3-Paper PDF');
let autoCloseMs = args.autoCloseMs ? Number(args.autoCloseMs) : 0;
let summary = { errors: [], pages: [] };

function fixture(name) {
    let local = files.path('./fixtures/' + name);
    if (files.exists(local)) return local;
    let shared = files.join(base, 'fixtures', name);
    return files.exists(shared) ? shared : null;
}

/* 没有样例文档时现场生成一个 6 页的文档. */
function demoDocument() {
    let lines = [];
    for (let i = 1; i <= 240; i++) {
        lines.push('Line ' + i + (i % 50 === 0 ? ' - needle' : ''));
    }
    return pdf.fromText(lines.join('\n'), files.join(base, 'samples', 'view', 'demo.pdf'), { fontSize: 14 }).path;
}

function finish() {
    if (args.output) files.write(String(args.output), JSON.stringify(summary));
}

if (!pdf.isAvailable()) {
    summary.errors.push('PLUGIN_UNAVAILABLE');
    console.error('3-Paper PDF 插件未安装或未启用, 请先在插件中心安装并启用.');
    finish();
} else {
    let path = args.path ? String(args.path) : fixture('outline.pdf') || demoDocument();
    summary.path = path;
    let session = pdf.view(path, { page: 2, search: 'needle' });
    console.log('已打开查看器:', path);

    session.on('page', (e) => {
        summary.pages.push(e.page);
        console.log('翻到第 ' + e.page + ' / ' + e.pageCount + ' 页');
    });
    session.on('close', (e) => {
        summary.reason = e.reason;
        console.log('查看器会话结束:', e.reason);
        finish();
    });

    /* 2 秒后跳到最后一页, 再读取一次状态. */
    setTimeout(() => {
        if (session.isClosed) return;
        let last = session.state().pageCount;
        session.goTo(last);
        setTimeout(() => {
            if (session.isClosed) return;
            let state = session.state();
            summary.state = { page: state.page, pageCount: state.pageCount, visible: state.visible };
            console.log('当前状态: 第', state.page, '/', state.pageCount, '页 | 缩放', state.zoom, '| 可见', state.visible);
        }, 1500);
    }, 2000);

    if (autoCloseMs > 0) {
        setTimeout(() => {
            if (!session.isClosed) session.close({ finish: true });
        }, autoCloseMs);
    } else {
        console.log('关闭查看器即可结束脚本');
    }
}
