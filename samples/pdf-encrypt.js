/**
 * 加密与解密 [v6.8.1+]
 *
 * pdf 模块由 3-Paper PDF 插件提供, 先在插件中心安装并启用.
 * 两种密码:
 * - 打开密码 (userPassword): 打开文档时要输入; 用它打开后只能做 permissions 允许的事.
 * - 所有者密码 (ownerPassword): 拥有全部权限, 解密 (pdf.decrypt) 与合并加密文档都需要它.
 * 两个密码至少给一个; 只给所有者密码时任何人都能打开文档, 但受权限限制.
 * 演示:
 * 1. pdf.encrypt 加密. permissions 写成对象时只改列出的权限 (例如 { copy: false }), 写成数组时只允许列出的权限 (例如 ['print']);
 *    权限名: print, printHighQuality, copy, modify, annotate, fillForms, extractForAccessibility, assemble.
 *    keyLength 为 256 (AES-256, 默认) 或 128 (AES-128).
 * 2. 读取加密文档: 不给密码时报 PASSWORD_REQUIRED, 密码不对时报 WRONG_PASSWORD; pdf.info 的 permissions 给出打开后的权限.
 * 3. pdf.decrypt 写出不加密的副本: 用所有者密码可以, 只用打开密码时报 PERMISSION_DENIED.
 * 可选参数 (engines.execScriptFile 的 arguments): path 为要加密的 PDF (默认为样例 fixtures/text.pdf, 找不到时现场生成),
 * outDir 为输出目录 (默认为 /sdcard/Documents/3-Paper PDF/samples/encrypt), output 为写入结果摘要 (JSON) 的文件.
 */

let args = engines.myEngine().execArgv;
let base = files.join(files.getSdcardPath(), 'Documents', '3-Paper PDF');
let outDir = args.outDir ? String(args.outDir) : files.join(base, 'samples', 'encrypt');
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

if (!pdf.isAvailable()) {
    summary.errors.push('PLUGIN_UNAVAILABLE');
    console.error('3-Paper PDF 插件未安装或未启用, 请先在插件中心安装并启用.');
} else {
    let source = args.path ? String(args.path) : fixture('text.pdf')
        || pdf.fromText('Secret report\n\nOnly for the team.', outPath('demo-source.pdf')).path;
    summary.path = source;
    console.log('文档:', source);

    /* 1. 加密: 对象写法, 禁止复制与修改, 其他权限保持允许. */
    let locked = null;
    try {
        locked = pdf.encrypt(source, outPath('locked.pdf'), {
            userPassword: 'open-me',
            ownerPassword: 'owner-secret',
            permissions: { copy: false, modify: false },
        }).path;
        /* 数组写法: 只允许打印; 用 128 位密钥. */
        let printOnly = pdf.encrypt(source, outPath('print-only.pdf'), {
            ownerPassword: 'owner-secret',
            permissions: ['print'],
            keyLength: 128,
        });
        summary.encrypt = { locked: locked, printOnly: pdf.info(printOnly.path).permissions };
        console.log('已加密:', locked);
        console.log('只给所有者密码 (不需要打开密码):', printOnly.path, '| 权限:', JSON.stringify(summary.encrypt.printOnly));
    } catch (e) {
        report('pdf.encrypt', e);
    }

    if (locked) {
        /* 2. 读取加密文档. */
        try {
            summary.open = {
                withoutPassword: codeOf(() => pdf.info(locked)),
                wrongPassword: codeOf(() => pdf.info(locked, { password: 'guess' })),
            };
            let info = pdf.info(locked, { password: 'open-me' });
            summary.open.permissions = info.permissions;
            console.log('不给密码:', summary.open.withoutPassword, '| 密码不对:', summary.open.wrongPassword);
            console.log('用打开密码: 加密', info.encrypted, '| 权限:', JSON.stringify(info.permissions));
            console.log('文字:', JSON.stringify(pdf.text(locked, { pages: [1], password: 'open-me' })[0].trim()));
        } catch (e) {
            report('读取加密文档', e);
        }

        /* 3. 解密. */
        try {
            summary.decrypt = { withUserPassword: codeOf(() => pdf.decrypt(locked, outPath('decrypted-user.pdf'), { password: 'open-me' })) };
            let plain = pdf.decrypt(locked, outPath('decrypted.pdf'), { password: 'owner-secret' });
            summary.decrypt.encryptedAfter = pdf.info(plain.path).encrypted;
            console.log('只用打开密码解密:', summary.decrypt.withUserPassword);
            console.log('用所有者密码解密:', plain.path, '| 仍加密:', summary.decrypt.encryptedAfter);
        } catch (e) {
            report('pdf.decrypt', e);
        }
    }
}

if (args.output) {
    files.write(String(args.output), JSON.stringify(summary));
}
