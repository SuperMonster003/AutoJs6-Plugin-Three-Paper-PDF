/**
 * 文档属性与书签 [v6.8.1+]
 *
 * pdf 模块由 3-Paper PDF 插件提供, 先在插件中心安装并启用. 本样例需要 AutoJs6 6.8.1 或以上与 3-Paper PDF 1.1.0 或以上:
 * 插件为 1.0.x 时 pdf.setInfo 与 pdf.setOutline 失败于 UNSUPPORTED, 本样例给出提示后结束.
 * 演示:
 * 1. pdf.info 读取文档信息 (title, author, subject, keywords, creator, producer 等, 缺失时为 null).
 * 2. pdf.setInfo 修改文档信息并写出副本: 键为 title, author, subject, keywords 与 creator, 值为字符串或 null (删除该项), 未给出的键保持不变;
 *    producer 与修改日期由插件在写出时设置. 文档带有 XMP 元数据时同步修改其中对应的项.
 * 3. pdf.setOutline 替换目录 (书签): 形状与 pdf.outline 的返回值相同 ({ title, page, children }), 可以读出后修改再写回;
 *    page 为 null 的项没有目标页, 用作分组. 目录太大时 pdf.outline 只返回前面的部分 (truncated 为 true), 写回前先检查.
 * 4. items 为 null 或空数组时删除目录.
 * 两个方法的输出都可以是源文件本身 (例如 pdf.setInfo(path, path, info)); 本样例写到输出目录, 不改动源文件.
 * 可选参数 (engines.execScriptFile 的 arguments): path 为 PDF (默认为样例 fixtures/outline.pdf, 找不到时现场生成几页文字),
 * outDir 为输出目录 (默认为 /sdcard/Documents/3-Paper PDF/samples/properties), output 为写入结果摘要 (JSON) 的文件.
 */

let args = engines.myEngine().execArgv;
let base = files.join(files.getSdcardPath(), 'Documents', '3-Paper PDF');
let outDir = args.outDir ? String(args.outDir) : files.join(base, 'samples', 'properties');
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

/* 没有样例文档时现场生成几页文字. */
function demoSource() {
    let lines = ['User manual', ''];
    for (let i = 1; i <= 100; i++) {
        lines.push('Paragraph ' + i + ': document properties and outline sample of 3-Paper PDF.');
    }
    return pdf.fromText(lines.join('\n'), outPath('demo-source.pdf'), { fontSize: 14 }).path;
}

function showInfo(label, info) {
    console.log(label + ': title=' + JSON.stringify(info.title) + ', author=' + JSON.stringify(info.author) + ', subject=' + JSON.stringify(info.subject)
        + ', keywords=' + JSON.stringify(info.keywords) + ', creator=' + JSON.stringify(info.creator) + ', producer=' + JSON.stringify(info.producer));
}

/* 打印目录树. */
function showOutline(items, indent) {
    items.forEach((item) => {
        console.log(indent + item.title + (item.page === null ? '' : ' -> 第 ' + item.page + ' 页'));
        showOutline(item.children || [], indent + '  ');
    });
}

if (!pdf.isAvailable()) {
    summary.errors.push('PLUGIN_UNAVAILABLE');
    console.error('3-Paper PDF 插件未安装或未启用, 请先在插件中心安装并启用.');
} else {
    let source = args.path ? String(args.path) : fixture('outline.pdf') || demoSource();
    let pageCount = pdf.info(source).pageCount;
    summary.path = source;
    console.log('文档:', source, '|', pageCount, '页');

    /* 1 - 2. 读取并修改文档信息. */
    let titled = null;
    step('pdf.setInfo', () => {
        showInfo('修改前', pdf.info(source));
        let result = pdf.setInfo(source, outPath('info.pdf'), {
            title: '3-Paper PDF 样例',
            author: 'AutoJs6',
            subject: '文档属性与书签',
            keywords: 'pdf, sample',
            creator: null,
        });
        titled = result.path;
        let info = pdf.info(titled);
        showInfo('修改后', info);
        summary.setInfo = { title: info.title, author: info.author, keywords: info.keywords, creator: info.creator, producer: info.producer };
        result.warnings.forEach(w => console.log('  提示:', w));
    });

    /* 3. 读出目录, 在末尾加一项后写回; 没有目录时新建一个两层的目录. */
    let outlined = null;
    if (titled) {
        step('pdf.setOutline', () => {
            let items = pdf.outline(titled);
            if (items.truncated) {
                console.log('目录太大, pdf.outline 只读到前面的部分; 写回会丢失后面的项, 跳过.');
                return;
            }
            let before = items.length;
            if (before > 0) {
                items.push({ title: '附录 (样例添加)', page: pageCount, children: [] });
            } else {
                items = [
                    { title: '封面', page: 1, children: [] },
                    {
                        title: '正文',
                        page: null,
                        children: [
                            { title: '第 1 页', page: 1, children: [] },
                            { title: '最后一页', page: pageCount, children: [] },
                        ],
                    },
                ];
            }
            let result = pdf.setOutline(titled, outPath('outline.pdf'), items);
            outlined = result.path;
            let back = pdf.outline(outlined);
            summary.setOutline = { before: before, after: back.length, last: back.length > 0 ? back[back.length - 1].title : null };
            console.log('目录: 原有', before, '项 (顶层), 写回后', back.length, '项');
            showOutline(back, '  ');
        });
    }

    /* 4. 删除目录. */
    if (outlined) {
        step('删除目录', () => {
            let result = pdf.setOutline(outlined, outPath('no-outline.pdf'), null);
            summary.cleared = pdf.outline(result.path).length;
            console.log('删除目录后:', summary.cleared, '项');
        });
    }
}

if (args.output) {
    files.write(String(args.output), JSON.stringify(summary));
}
