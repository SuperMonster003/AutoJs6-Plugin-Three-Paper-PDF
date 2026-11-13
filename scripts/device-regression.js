/**
 * 3-Paper PDF device regression (roadmap P1.3, P2.7): every method of appendix B (reading, rendering, page operations,
 * generation, encryption, progress and cancellation), the examples of appendix B.5 and the performance baseline of P2.7,
 * run against the fixtures of samples/fixtures.
 *
 * Run it in AutoJs6 (a host with the pdf module, versionCode >= 6641) with 3-Paper PDF installed and enabled. The
 * fixtures are read from fixtures/ next to the script, or else from /sdcard/Documents/3-Paper PDF/fixtures/. Every check
 * prints one line, "AJ6PDF PASS|FAIL|SKIP <label> <key values>", the baseline prints "AJ6PDF PERF ..." lines and the
 * run ends with "AJ6PDF DONE pass=N fail=N skip=N". The same lines are appended to
 * /sdcard/Documents/3-Paper PDF/regression/result.txt, because logd drops console lines on some devices (OPD2413). The
 * other outputs in that directory are removed at the end unless KEEP is true. pdf.view opens the viewer window, so it
 * only runs when VIEW is true; OCR is checked when an OCR plugin is installed and reported as such otherwise. The host
 * tool tools/paper-pdf/device_regression.py pushes the fixtures and this script, runs it and prints the result lines.
 *
 * zh-CN: 3-Paper PDF 真机回归 (路线图 P1.3, P2.7): 用 samples/fixtures 的样例覆盖附录 B 的每个方法 (读取, 渲染, 页面操作, 生成, 加密,
 * 进度与取消), 附录 B.5 的示例与 P2.7 的性能基线. 在 AutoJs6 (带 pdf 模块的宿主, versionCode >= 6641) 中运行, 3-Paper PDF 须已安装并启用.
 * 样例从脚本旁的 fixtures/ 读取, 没有时从 /sdcard/Documents/3-Paper PDF/fixtures/ 读取. 每项检查输出一行 "AJ6PDF PASS|FAIL|SKIP <名称>
 * <关键数值>", 基线输出 "AJ6PDF PERF ..." 行, 最后一行为 "AJ6PDF DONE pass=N fail=N skip=N". 这些行同时追加到
 * /sdcard/Documents/3-Paper PDF/regression/result.txt (部分设备的 logd 会丢 console 行, 如 OPD2413); 该目录中的其他产物在结束时删除,
 * KEEP 为 true 时保留. pdf.view 会打开查看器窗口, 只在 VIEW 为 true 时运行; 装有 OCR 插件时检查 OCR, 否则如实报告. 宿主工具
 * tools/paper-pdf/device_regression.py 负责推送样例与本脚本, 运行并输出结果行.
 */

const KEEP = false;
const VIEW = false;
const PERF = true;

const DEFAULT_DIR = '/sdcard/Documents/3-Paper PDF/';
const FIXTURES = files.exists(files.path('./fixtures/text.pdf')) ? files.path('./fixtures/') : DEFAULT_DIR + 'fixtures/';
const OUT = DEFAULT_DIR + 'regression/';
const RESULT = OUT + 'result.txt';
const PLUGIN = 'io.github.supermonster003.autojs6.plugin.three.paper.pdf';

const TEXT = FIXTURES + 'text.pdf';
const OUTLINE = FIXTURES + 'outline.pdf';
const ENCRYPTED = FIXTURES + 'encrypted.pdf';
const SCANNED = FIXTURES + 'scanned.pdf';
const IMAGES = FIXTURES + 'images.pdf';
const ROTATED = FIXTURES + 'rotated.pdf';
const USER = 'paper-user';
const OWNER = 'paper-owner';
const OCR_WORDS = ['SCANNED', 'PAGE', 'PAPER', 'PDF', 'HELLO', 'WORLD', '2026'];

const counts = { pass: 0, fail: 0, skip: 0 };

// Output. zh-CN: 输出.

function emit(line) {
    console.log(line);
    try {
        files.append(RESULT, line + '\n');
    } catch (e) {
        // The console line stays. zh-CN: 仍有 console 行.
    }
}

function report(verdict, label, detail) {
    counts[verdict.toLowerCase()]++;
    emit('AJ6PDF ' + verdict + ' ' + label + (detail ? ' ' + detail : ''));
}

function describe(e) {
    if (e instanceof pdf.PdfError) return 'PdfError ' + e.code + ': ' + String(e.message).slice(0, 200);
    let text = String(e && e.message !== undefined ? e.message : e).slice(0, 200);
    // A Java exception names its first frame, where the failure starts. zh-CN: Java 异常附上第一帧, 即失败的起点.
    let java = e && e.javaException;
    let frames = java && java.getStackTrace ? java.getStackTrace() : null;
    return frames && frames.length ? text + ' at ' + frames[0] : text;
}

function check(label, body) {
    let start = Date.now();
    try {
        let detail = body();
        report('PASS', label, (detail === undefined ? '' : detail + ' ') + (Date.now() - start) + 'ms');
    } catch (e) {
        report('FAIL', label, describe(e));
    }
}

async function checkAsync(label, body) {
    let start = Date.now();
    try {
        let detail = await body();
        report('PASS', label, (detail === undefined ? '' : detail + ' ') + (Date.now() - start) + 'ms');
    } catch (e) {
        report('FAIL', label, describe(e));
    }
}

// Assertions. zh-CN: 断言.

function fail(message) {
    throw new Error(message);
}

function expect(condition, message) {
    if (!condition) fail(message);
}

function expectError(code, e) {
    expect(e instanceof pdf.PdfError, 'not a PdfError: ' + describe(e));
    expect(e.code === code, 'code ' + e.code + ', expected ' + code + ' (' + String(e.message).slice(0, 160) + ')');
    return code;
}

function expectCode(code, body) {
    try {
        body();
    } catch (e) {
        return expectError(code, e);
    }
    return fail('no error, expected ' + code);
}

async function expectRejection(code, promise) {
    try {
        await promise;
    } catch (e) {
        return expectError(code, e);
    }
    return fail('resolved, expected ' + code);
}

// Files. zh-CN: 文件.

function exists(path) {
    return new java.io.File(path).isFile();
}

function size(path) {
    return Number(new java.io.File(path).length());
}

function header(path, count) {
    let stream = new java.io.FileInputStream(path);
    try {
        let bytes = [];
        for (let i = 0; i < count; i++) bytes.push(stream.read());
        return bytes;
    } finally {
        stream.close();
    }
}

function ascii(bytes) {
    return bytes.map(b => String.fromCharCode(b)).join('');
}

function leftovers(dir) {
    return files.listDir(dir).filter(name => /\.part$/.test(name) || /^\.[^.]/.test(name));
}

function rotations(path, password) {
    return pdf.info(path, password ? { password: password } : undefined).pages.map(p => p.rotation).join(',');
}

function round(value) {
    return Math.round(Number(value));
}

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

function words(text) {
    let upper = String(text).toUpperCase();
    return OCR_WORDS.filter(w => upper.indexOf(w) >= 0).length;
}

function canonical(path) {
    return String(new java.io.File(path).getCanonicalPath());
}

function within(promise, ms, label) {
    return Promise.race([promise, sleep(ms).then(() => fail(label + ' did not arrive within ' + ms + ' ms'))]);
}

// Environment. zh-CN: 环境.

function environment() {
    let manager = context.getPackageManager();
    let host = manager.getPackageInfo(context.getPackageName(), 0).versionCode;
    let plugin = '-';
    try {
        plugin = manager.getPackageInfo(PLUGIN, 0).versionCode;
    } catch (e) {
        // Not installed or not visible. zh-CN: 未安装或不可见.
    }
    emit('AJ6PDF INFO device=' + device.brand + '/' + device.model + ' sdk=' + device.sdkInt + ' host=' + host + ' plugin=' + plugin + ' fixtures=' + FIXTURES);
}

// Reading. zh-CN: 读取.

function reading() {
    check('info', () => {
        let info = pdf.info(TEXT);
        expect(info.pageCount == 1, 'pageCount=' + info.pageCount);
        expect(round(info.pages[0].width) == 595 && round(info.pages[0].height) == 842, 'page ' + info.pages[0].width + 'x' + info.pages[0].height);
        expect(info.title === 'Text sample', 'title=' + info.title);
        expect(info.encrypted === false, 'encrypted=' + info.encrypted);
        expect(info.sizeBytes == 1376, 'sizeBytes=' + info.sizeBytes);
        let rotated = pdf.info(ROTATED);
        let last = rotated.pages[4];
        expect(rotations(ROTATED) === '0,90,180,270,0', 'rotations=' + rotations(ROTATED));
        expect(round(last.width) == 792 && round(last.height) == 612, 'last page ' + last.width + 'x' + last.height);
        return 'pageCount=1 595x842 sizeBytes=1376 rotations=0,90,180,270,0';
    });
    check('info.password', () => {
        expectCode('PASSWORD_REQUIRED', () => pdf.info(ENCRYPTED));
        expectCode('WRONG_PASSWORD', () => pdf.info(ENCRYPTED, { password: 'wrong' }));
        let user = pdf.info(ENCRYPTED, { password: USER });
        expect(user.encrypted === true && user.pageCount == 2, 'encrypted=' + user.encrypted + ' pageCount=' + user.pageCount);
        let p = user.permissions;
        expect(p.print === true && p.copy === false && p.modify === false && p.assemble === false, 'user permissions=' + JSON.stringify(p));
        let owner = pdf.info(ENCRYPTED, { password: OWNER }).permissions;
        expect(owner.copy === true && owner.modify === true && owner.assemble === true, 'owner permissions=' + JSON.stringify(owner));
        return 'PASSWORD_REQUIRED WRONG_PASSWORD user=print-only owner=all';
    });
    check('text', () => {
        let pages = pdf.text(TEXT);
        expect(pages.length == 1 && pages[0].indexOf('Search target: needle') >= 0, 'text=' + JSON.stringify(pages));
        expect(pages.pages.join() === '1' && pages.sources.join() === 'text', 'pages=' + pages.pages + ' sources=' + pages.sources);
        let two = pdf.text(OUTLINE, { pages: '2-3' });
        expect(two.length == 2 && two[0].indexOf('Page 2 of 6') >= 0 && two[1].indexOf('Page 3 of 6') >= 0, 'pages 2-3=' + JSON.stringify(two));
        expect(two.pages.join() === '2,3', 'pages=' + two.pages);
        let list = pdf.text(OUTLINE, { pages: [1, 6] });
        expect(list.pages.join() === '1,6' && list[1].indexOf('Page 6 of 6') >= 0, 'array pages=' + list.pages);
        let raw = pdf.text(TEXT, { layout: 'raw' });
        expect(raw[0].indexOf('quick brown fox') >= 0, 'raw=' + JSON.stringify(raw));
        let locked = pdf.text(ENCRYPTED, { password: USER, pages: '2' });
        expect(locked[0].indexOf('Page 2 of 2') >= 0, 'encrypted text=' + JSON.stringify(locked));
        let blank = pdf.text(SCANNED);
        expect(blank.length == 1 && blank[0].trim() === '' && blank.sources[0] === 'text', 'scanned without ocr=' + JSON.stringify(blank));
        return 'chars=' + pages[0].length + ' ranges=2-3,[1,6] layout=raw encrypted scanned=blank';
    });
    check('outline', () => {
        let items = pdf.outline(OUTLINE);
        expect(items.length == 4, 'top items=' + items.length);
        expect(items[0].title === 'Chapter 1' && items[0].page == 1, 'first=' + JSON.stringify(items[0]));
        expect(items[0].children.length == 1 && items[0].children[0].page == 2, 'children=' + JSON.stringify(items[0].children));
        expect(items[2].title === 'Chapter 3' && items[2].page == 5, 'third=' + JSON.stringify(items[2]));
        expect(items[3].title === 'Notes' && items[3].page === null, 'Notes without a destination=' + JSON.stringify(items[3]));
        expect(pdf.outline(TEXT).length == 0, 'text.pdf has an outline');
        return 'items=4 pages=' + items.map(i => i.page).join(',') + ' level=' + items[0].level + '/' + items[0].children[0].level;
    });
    check('search', () => {
        let hits = pdf.search(OUTLINE, 'needle');
        expect(hits.map(h => h.page).join() === '2,5', 'pages=' + hits.map(h => h.page));
        expect(hits[0].rect != null && hits[0].context.indexOf('needle') >= 0, 'hit=' + JSON.stringify(hits[0]));
        expect(pdf.search(OUTLINE, 'NEEDLE').length == 2, 'case-insensitive by default');
        expect(pdf.search(OUTLINE, 'NEEDLE', { caseSensitive: true }).length == 0, 'caseSensitive');
        expect(pdf.search(TEXT, 'need').length == 1 && pdf.search(TEXT, 'need', { wholeWord: true }).length == 0, 'wholeWord');
        expect(pdf.search(OUTLINE, 'needle', { pages: '3-' }).map(h => h.page).join() === '5', 'pages option');
        return 'hits=2 pages=2,5 caseSensitive wholeWord pages';
    });
}

// Rendering. zh-CN: 渲染.

function rendering() {
    check('render', () => {
        let sizes = [];
        let img = pdf.render(TEXT, 1);
        try {
            sizes.push(img.getWidth() + 'x' + img.getHeight());
            expect(img.getWidth() == 1190 && img.getHeight() == 1684, 'default 144 dpi gave ' + sizes[0]);
            expect(colors.red(images.pixel(img, 4, 4)) > 240, 'top left corner not white');
        } finally {
            img.recycle();
        }
        let checks = [
            [{ dpi: 72 }, 595, 842],
            [{ width: 300 }, 300, 425],
            [{ scale: 0.5 }, 298, 421],
            [{ dpi: 72, transparent: true }, 595, 842],
        ];
        checks.forEach(c => {
            let image = pdf.render(TEXT, 1, c[0]);
            try {
                sizes.push(image.getWidth() + 'x' + image.getHeight());
                expect(Math.abs(image.getWidth() - c[1]) <= 1 && Math.abs(image.getHeight() - c[2]) <= 1, JSON.stringify(c[0]) + ' gave ' + sizes[sizes.length - 1]);
            } finally {
                image.recycle();
            }
        });
        let turned = pdf.render(ROTATED, 2, { dpi: 72 });
        try {
            expect(turned.getWidth() == 842 && turned.getHeight() == 595, 'rotated page 2 gave ' + turned.getWidth() + 'x' + turned.getHeight());
        } finally {
            turned.recycle();
        }
        let marker = pdf.render(ROTATED, 1, { dpi: 72 });
        try {
            let bar = images.pixel(marker, 108, 54), paper = images.pixel(marker, 300, 400);
            expect(colors.red(bar) < 64 && colors.red(paper) > 240, 'marker ' + colors.toString(bar) + ' paper ' + colors.toString(paper));
        } finally {
            marker.recycle();
        }
        let locked = pdf.render(ENCRYPTED, 1, { password: USER, dpi: 72 });
        try {
            expect(locked.getWidth() == 595, 'encrypted width=' + locked.getWidth());
        } finally {
            locked.recycle();
        }
        return 'sizes=' + sizes.join(',') + ' rotated=842x595 marker=black';
    });
    check('renderToFile', () => {
        let found = [];
        [['png', 'png', [0x89, 0x50, 0x4e, 0x47]], ['jpeg', 'jpg', [0xff, 0xd8]], ['webp', 'webp', null]].forEach(f => {
            let output = OUT + 'page.' + f[1];
            let options = { format: f[0], dpi: 72 };
            if (f[0] !== 'png') options.quality = 80;
            let r = pdf.renderToFile(TEXT, 1, output, options);
            expect(r.path === output && r.width == 595 && r.height == 842 && r.format === f[0], f[0] + ' result=' + JSON.stringify(r));
            expect(exists(output) && size(output) == r.sizeBytes, f[0] + ' size ' + size(output) + ' != ' + r.sizeBytes);
            let head = header(output, 12);
            if (f[2]) expect(f[2].every((b, i) => head[i] === b), f[0] + ' header ' + head.slice(0, 4));
            else expect(ascii(head.slice(0, 4)) === 'RIFF' && ascii(head.slice(8, 12)) === 'WEBP', 'webp header ' + ascii(head));
            found.push(f[0] + '=' + r.sizeBytes);
        });
        let doc = pdf.renderToFile(OUTLINE, 3, OUT + 'page-3.png', { width: 200 });
        expect(doc.width == 200, 'width option gave ' + doc.width);
        return found.join(' ');
    });
}

// Images. zh-CN: 图片.

function imageChecks() {
    check('images', () => {
        let list = pdf.images(IMAGES);
        expect(list.length == 2, 'images=' + list.length);
        expect(list.map(i => i.format).join() === 'jpeg,png' && list.map(i => i.width + 'x' + i.height).join() === '160x120,64x64', 'list=' + JSON.stringify(list));
        expect(pdf.images(OUTLINE).length == 0 && pdf.images(IMAGES, { pages: '1' }).length == 2, 'pages option');
        return 'images=2 formats=jpeg,png';
    });
    check('extractImages', () => {
        let r = pdf.extractImages(IMAGES, OUT + 'images');
        expect(r.paths.length == 2 && r.paths.every(exists), 'paths=' + JSON.stringify(r.paths));
        let jpeg = r.paths.filter(p => /\.jpe?g$/.test(p))[0];
        expect(jpeg && size(jpeg) == 2717, 'the JPEG is not exported as is: ' + jpeg + ' ' + (jpeg && size(jpeg)));
        let png = r.paths.filter(p => /\.png$/.test(p))[0];
        expect(png && header(png, 4)[1] === 0x50, 'png=' + png);
        let named = pdf.extractImages(IMAGES, OUT + 'images-named', { namePattern: 'img-{index}' });
        expect(named.paths.map(p => files.getNameWithoutExtension(p)).join() === 'img-1,img-2', 'namePattern=' + JSON.stringify(named.paths));
        return 'names=' + r.paths.map(p => files.getName(p)).join(',') + ' jpeg=2717B';
    });
}

// Page operations. zh-CN: 页面操作.

function pageOperations() {
    check('merge', () => {
        let r = pdf.merge([TEXT, OUTLINE], OUT + 'merged.pdf');
        expect(r.path === OUT + 'merged.pdf' && r.pageCount == 7, 'result=' + JSON.stringify(r));
        expect(r.sizeBytes == size(r.path) && r.warnings.length == 0, 'sizeBytes=' + r.sizeBytes + ' file=' + size(r.path) + ' warnings=' + r.warnings);
        expect(pdf.text(r.path, { pages: '2' })[0].indexOf('Page 1 of 6') >= 0, 'page 2 is not page 1 of outline.pdf');
        let none = pdf.merge([OUTLINE, OUTLINE], OUT + 'merged-none.pdf', { outline: 'none' });
        expect(none.pageCount == 12 && pdf.outline(none.path).length == 0, 'outline none: ' + none.pageCount + ' pages');
        expectCode('PASSWORD_REQUIRED', () => pdf.merge([TEXT, ENCRYPTED], OUT + 'merged-locked.pdf'));
        let locked = pdf.merge([TEXT, ENCRYPTED], OUT + 'merged-locked.pdf', { passwords: [null, OWNER] });
        expect(locked.pageCount == 3, 'with passwords: ' + locked.pageCount);
        return 'pageCount=7 sizeBytes=' + r.sizeBytes + ' outline=none passwords';
    });
    check('split', () => {
        let r = pdf.split(OUTLINE, OUT + 'split', { every: 2 });
        expect(r.paths.length == 3, 'every 2: ' + r.paths.length + ' parts');
        expect(r.paths.map(p => pdf.info(p).pageCount).join() === '2,2,2', 'part pages');
        expect(r.paths.map(p => files.getName(p)).join() === 'outline-1.pdf,outline-2.pdf,outline-3.pdf', 'names=' + r.paths.map(p => files.getName(p)));
        let ranges = pdf.split(OUTLINE, OUT + 'split-ranges', { ranges: ['1-2', '5-'], namePattern: 'part-{index}' });
        expect(ranges.paths.map(p => pdf.info(p).pageCount).join() === '2,2', 'ranges parts');
        expect(pdf.text(ranges.paths[1], { pages: '1' })[0].indexOf('Page 5 of 6') >= 0, 'second range starts at page 5');
        expectCode('INVALID_ARGUMENT', () => pdf.split(OUTLINE, OUT + 'split-none', {}));
        return 'every=2 parts=3 ranges=2';
    });
    check('extract', () => {
        let r = pdf.extract(OUTLINE, OUT + 'extract.pdf', '2-3');
        expect(r.pageCount == 2 && pdf.text(r.path)[0].indexOf('Page 2 of 6') >= 0, 'extract=' + JSON.stringify(r));
        return 'pageCount=2';
    });
    check('delete', () => {
        let r = pdf.delete(OUTLINE, OUT + 'delete.pdf', '1,6');
        expect(r.pageCount == 4 && pdf.text(r.path)[0].indexOf('Page 2 of 6') >= 0, 'delete=' + JSON.stringify(r));
        expectCode('INVALID_ARGUMENT', () => pdf.delete(TEXT, OUT + 'delete-all.pdf', '1'));
        return 'pageCount=4 all=INVALID_ARGUMENT';
    });
    check('reorder', () => {
        let r = pdf.reorder(OUTLINE, OUT + 'reorder.pdf', [6, 5, 4, 3, 2, 1]);
        let texts = pdf.text(r.path);
        expect(r.pageCount == 6 && texts[0].indexOf('Page 6 of 6') >= 0 && texts[5].indexOf('Page 1 of 6') >= 0, 'reorder first=' + texts[0]);
        return 'order=6..1';
    });
    check('rotate', () => {
        let all = pdf.rotate(ROTATED, OUT + 'rotate.pdf', 90);
        expect(rotations(all.path) === '90,180,270,0,90', 'all pages: ' + rotations(all.path));
        let one = pdf.rotate(ROTATED, OUT + 'rotate-1.pdf', 270, { pages: '1' });
        expect(rotations(one.path) === '270,90,180,270,0', 'page 1: ' + rotations(one.path));
        expectCode('INVALID_ARGUMENT', () => pdf.rotate(ROTATED, OUT + 'rotate-45.pdf', 45));
        return 'additive 90 / 270 on page 1, 45=INVALID_ARGUMENT';
    });
}

// Generation and encryption. zh-CN: 生成与加密.

function generation() {
    check('fromImages', () => {
        let png = pdf.renderToFile(OUTLINE, 1, OUT + 'album-1.png', { dpi: 72 }).path;
        let jpeg = pdf.renderToFile(OUTLINE, 2, OUT + 'album-2.jpg', { dpi: 72, format: 'jpeg' }).path;
        let r = pdf.fromImages([png, jpeg], OUT + 'album.pdf', { pageSize: 'A4', fit: 'contain', margin: 24 });
        let info = pdf.info(r.path);
        expect(r.pageCount == 2 && round(info.pages[0].width) == 595 && round(info.pages[0].height) == 842, 'album=' + JSON.stringify(r) + ' page ' + info.pages[0].width);
        let fit = pdf.fromImages([png], OUT + 'album-fit.pdf', { quality: 'lossless' });
        expect(fit.pageCount == 1, 'fit=' + JSON.stringify(fit));
        return 'pageCount=2 A4 sizeBytes=' + r.sizeBytes;
    });
    check('fromText', () => {
        let r = pdf.fromText('Hello PDF\nSecond line', OUT + 'notes.pdf', { fontSize: 14, title: 'Notes' });
        expect(r.pageCount == 1 && pdf.text(r.path)[0].indexOf('Second line') >= 0, 'notes=' + JSON.stringify(r));
        expect(pdf.info(r.path).title === 'Notes', 'title=' + pdf.info(r.path).title);
        let lines = [];
        for (let i = 1; i <= 200; i++) lines.push('Line ' + i);
        let long = pdf.fromText(lines.join('\n'), OUT + 'notes-long.pdf', { pageSize: 'Letter', margin: 36 });
        expect(long.pageCount > 1 && round(pdf.info(long.path).pages[0].width) == 612, 'long text: ' + long.pageCount + ' pages');
        return 'pageCount=1 title long=' + long.pageCount + ' pages';
    });
    check('fromHtml', () => {
        let r = pdf.fromHtml('<h1>Report</h1><p>Hello</p>', OUT + 'report.pdf');
        expect(r.pageCount >= 1 && pdf.text(r.path)[0].indexOf('Hello') >= 0, 'html=' + JSON.stringify(r));
        files.write(OUT + 'page.html', '<html><body><h2>From a file</h2><p>Body text</p></body></html>');
        let file = pdf.fromHtml(OUT + 'page.html', OUT + 'page.pdf', { pageSize: 'A4', margin: 36 });
        expect(file.pageCount >= 1 && pdf.text(file.path)[0].indexOf('Body text') >= 0, 'html file=' + JSON.stringify(file));
        return 'pageCount=' + r.pageCount + ' file=' + file.pageCount;
    });
    check('encrypt', () => {
        let source = pdf.fromText('Secret report', OUT + 'secret.pdf').path;
        let r = pdf.encrypt(source, OUT + 'secret-locked.pdf', { userPassword: 'open', ownerPassword: 'owner', permissions: { print: true, copy: false } });
        expect(r.pageCount == 1, 'encrypt=' + JSON.stringify(r));
        expectCode('PASSWORD_REQUIRED', () => pdf.info(r.path));
        let p = pdf.info(r.path, { password: 'open' }).permissions;
        expect(p.print === true && p.copy === false && p.modify === true, 'object permissions=' + JSON.stringify(p));
        let list = pdf.encrypt(source, OUT + 'secret-list.pdf', { userPassword: 'open', permissions: ['print'], keyLength: 128 });
        let q = pdf.info(list.path, { password: 'open' }).permissions;
        expect(q.print === true && q.copy === false && q.modify === false, 'array permissions=' + JSON.stringify(q));
        expectCode('INVALID_ARGUMENT', () => pdf.encrypt(source, OUT + 'secret-none.pdf', {}));
        return 'AES-256 object permissions, AES-128 array permissions';
    });
    check('decrypt', () => {
        let locked = OUT + 'secret-locked.pdf';
        expect(exists(locked), 'needs the output of encrypt');
        expectCode('PERMISSION_DENIED', () => pdf.decrypt(locked, OUT + 'secret-user.pdf', { password: 'open' }));
        let r = pdf.decrypt(locked, OUT + 'secret-open.pdf', { password: 'owner' });
        expect(pdf.info(r.path).encrypted === false && pdf.text(r.path)[0].indexOf('Secret report') >= 0, 'decrypt=' + JSON.stringify(r));
        return 'owner=decrypted user=PERMISSION_DENIED';
    });
}

// The object layer. zh-CN: 对象层.

async function documents() {
    // Leave the first part of this async function first (see sync.beforeAwait in main).
    // zh-CN: 先离开本 async 函数的第一段 (见 main 中的 sync.beforeAwait).
    await null;
    await checkAsync('open', async () => {
        let doc = pdf.open(OUTLINE);
        try {
            expect(doc.pageCount == 6 && doc.path === OUTLINE && doc.isClosed === false, 'doc=' + doc.pageCount + ' ' + doc.path);
            expect(doc.info().pageCount == 6, 'info');
            let page = doc.pageSize(1);
            expect(round(page.width) == 595 && round(page.height) == 842, 'pageSize=' + JSON.stringify(page));
            let texts = doc.text('1-2');
            expect(texts.length == 2 && texts[1].indexOf('needle') >= 0, 'text=' + JSON.stringify(texts));
            expect(doc.outline().length == 4, 'outline');
            expect(doc.search('needle').map(h => h.page).join() === '2,5', 'search');
            let img = doc.render(1, { dpi: 72 });
            try {
                expect(img.getWidth() == 595, 'render width=' + img.getWidth());
            } finally {
                img.recycle();
            }
            let file = doc.renderToFile(2, OUT + 'doc-page-2.png', { dpi: 72 });
            expect(file.width == 595 && exists(file.path), 'renderToFile=' + JSON.stringify(file));
            expect(doc.images().length == 0, 'images');
            let saved = doc.save(OUT + 'doc-saved.pdf', { pages: '1-3', rotate: { pages: '2', degrees: 90 } });
            expect(saved.pageCount == 3 && rotations(saved.path) === '0,90,0', 'save=' + JSON.stringify(saved) + ' ' + rotations(saved.path));
            let pruned = doc.save(OUT + 'doc-pruned.pdf', { delete: '1,2', order: [6, 5, 4, 3] });
            expect(pruned.pageCount == 4 && pdf.text(pruned.path, { pages: '1' })[0].indexOf('Page 6 of 6') >= 0, 'order + delete=' + JSON.stringify(pruned));
            let info = await doc.infoAsync();
            expect(info.pageCount == 6, 'infoAsync');
        } finally {
            doc.close();
        }
        expect(doc.isClosed === true, 'isClosed after close');
        expectCode('SESSION_CLOSED', () => doc.info());
        let images = pdf.open(IMAGES);
        try {
            let r = images.extractImages(OUT + 'doc-images');
            expect(r.paths.length == 2, 'extractImages=' + JSON.stringify(r.paths));
        } finally {
            images.close();
        }
        return 'pageCount=6 save=3 pages rotate order+delete SESSION_CLOSED';
    });
    check('open.limit', () => {
        let opened = [];
        try {
            for (let i = 0; i < 8; i++) opened.push(pdf.open(TEXT));
            expectCode('LIMIT_EXCEEDED', () => opened.push(pdf.open(TEXT)));
        } finally {
            opened.forEach(d => d.close());
        }
        let again = pdf.open(TEXT);
        again.close();
        return 'open=8 ninth=LIMIT_EXCEEDED reopen after close';
    });
    check('open.password', () => {
        expectCode('PASSWORD_REQUIRED', () => pdf.open(ENCRYPTED));
        let doc = pdf.open(ENCRYPTED, { password: USER });
        try {
            expect(doc.pageCount == 2 && doc.text('2')[0].indexOf('Page 2 of 2') >= 0, 'encrypted doc');
        } finally {
            doc.close();
        }
        return 'PASSWORD_REQUIRED user=read';
    });
}

// Progress and cancellation. zh-CN: 进度与取消.

function inputs(count) {
    let list = [];
    for (let i = 0; i < count; i++) list.push(OUTLINE);
    return list;
}

async function progress() {
    // Leave the first part of this async function first (see sync.beforeAwait in main).
    // zh-CN: 先离开本 async 函数的第一段 (见 main 中的 sync.beforeAwait).
    await null;
    await checkAsync('async.progress', async () => {
        let seen = [];
        let task = pdf.mergeAsync(inputs(20), OUT + 'async.pdf', { onProgress: p => seen.push(p) });
        expect(typeof task.then === 'function' && typeof task.cancel === 'function', 'not a cancellable Promise');
        let r = await task;
        expect(r.pageCount == 120, 'pageCount=' + r.pageCount);
        expect(seen.length > 0, 'no progress');
        let percents = seen.map(p => p.percent);
        expect(percents.every((v, i) => i === 0 || v >= percents[i - 1]) && percents[percents.length - 1] <= 100, 'percents=' + percents);
        expect(typeof seen[0].opId === 'number' && typeof seen[0].stage === 'string' && seen[0].total >= seen[0].current, 'progress=' + JSON.stringify(seen[0]));
        expect(task.cancel() === false, 'cancel() after settling is not false');
        let stages = seen.map(p => p.stage).filter((s, i, a) => a.indexOf(s) === i);
        return 'events=' + seen.length + ' stages=' + stages.join('/') + ' last=' + percents[percents.length - 1];
    });
    check('sync.progress', () => {
        let seen = [];
        let r = pdf.merge(inputs(10), OUT + 'sync.pdf', { onProgress: p => seen.push(p.percent) });
        expect(r.pageCount == 60 && seen.length > 0, 'pageCount=' + r.pageCount + ' events=' + seen.length);
        return 'events=' + seen.length;
    });
    await checkAsync('async.cancel', async () => {
        let output = OUT + 'cancelled.pdf';
        let task = pdf.mergeAsync(inputs(60), output);
        let cancelled = task.cancel();
        await within(expectRejection('CANCELLED', task), 60000, 'the cancellation');
        expect(cancelled === true, 'cancel()=' + cancelled);
        await sleep(300);
        expect(!exists(output) && leftovers(OUT).length == 0, 'left ' + output + ' ' + leftovers(OUT));
        return 'cancel()=true CANCELLED no output';
    });
    // cancel() right after the call, often before the work has started: the Promise must still settle every time.
    // zh-CN: 调用后立即 cancel(), 往往在工作开始之前: Promise 仍须每次都落定.
    await checkAsync('async.cancel.early', async () => {
        let rounds = 20, cancelledCount = 0, resolvedCount = 0;
        for (let i = 0; i < rounds; i++) {
            let task = i % 2 === 0 ? pdf.infoAsync(TEXT) : pdf.mergeAsync([OUTLINE, OUTLINE], OUT + 'early-' + i + '.pdf');
            let cancelled = task.cancel();
            let outcome = await within(task.then(() => 'RESOLVED', e => e instanceof pdf.PdfError ? e.code : String(e)), 10000, 'round ' + (i + 1));
            if (outcome === 'CANCELLED' && cancelled === true) cancelledCount++;
            else if (outcome === 'RESOLVED' && cancelled === false) resolvedCount++;
            else fail('round ' + (i + 1) + ': cancel()=' + cancelled + ' outcome=' + outcome);
        }
        await sleep(300);
        expect(leftovers(OUT).length == 0, 'left ' + leftovers(OUT));
        return 'rounds=' + rounds + ' CANCELLED=' + cancelledCount + ' finished first=' + resolvedCount;
    });
    await checkAsync('async.cancel.progress', async () => {
        let output = OUT + 'cancelled-mid.pdf', at = -1, task;
        task = pdf.mergeAsync(inputs(100), output, {
            onProgress: p => {
                if (at < 0) {
                    at = p.percent;
                    task.cancel();
                }
            },
        });
        let code = await within(expectRejection('CANCELLED', task), 60000, 'the cancellation');
        await sleep(300);
        expect(at >= 0, 'no progress before the end');
        expect(!exists(output) && leftovers(OUT).length == 0, 'left ' + output + ' ' + leftovers(OUT));
        return code + ' at ' + at + '%';
    });
    await checkAsync('async.errors', async () => {
        await expectRejection('FILE_NOT_FOUND', pdf.infoAsync(FIXTURES + 'missing.pdf'));
        await expectRejection('WRONG_PASSWORD', pdf.textAsync(ENCRYPTED, { password: 'wrong' }));
        let texts = await pdf.textAsync(OUTLINE, { pages: '5' });
        expect(texts.pages.join() === '5' && texts[0].indexOf('needle') >= 0, 'textAsync=' + JSON.stringify(texts));
        let img = await pdf.renderAsync(TEXT, 1, { dpi: 72 });
        try {
            expect(img.getWidth() == 595, 'renderAsync width=' + img.getWidth());
        } finally {
            img.recycle();
        }
        return 'rejections FILE_NOT_FOUND / WRONG_PASSWORD, textAsync, renderAsync';
    });
}

// Errors, threads and output paths. zh-CN: 错误, 线程与输出路径.

function errors() {
    check('errors', () => {
        expectCode('FILE_NOT_FOUND', () => pdf.info(FIXTURES + 'missing.pdf'));
        files.write(OUT + 'not-a-pdf.pdf', 'plain text');
        expectCode('INVALID_PDF', () => pdf.info(OUT + 'not-a-pdf.pdf'));
        expectCode('PAGE_OUT_OF_RANGE', () => pdf.render(TEXT, 2));
        expectCode('INVALID_ARGUMENT', () => pdf.render(TEXT, 0));
        expectCode('INVALID_ARGUMENT', () => pdf.render(TEXT, 1, { dpi: 72, scale: 2 }));
        expectCode('INVALID_ARGUMENT', () => pdf.info('content://media/external/file/1'));
        expectCode('INVALID_ARGUMENT', () => pdf.text(TEXT, { unknownOption: true }));
        let e = new pdf.PdfError('x', 'IO_ERROR');
        expect(String(e) === 'PdfError [IO_ERROR]: x' && e.code === 'IO_ERROR' && e instanceof pdf.PdfError, 'PdfError=' + String(e));
        return 'FILE_NOT_FOUND INVALID_PDF PAGE_OUT_OF_RANGE INVALID_ARGUMENT x4 PdfError';
    });
    check('sync.uiThread', () => {
        let latch = new java.util.concurrent.CountDownLatch(1), outcome = 'not run';
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new java.lang.Runnable({
            run: function () {
                try {
                    pdf.info(TEXT);
                    outcome = 'resolved';
                } catch (e) {
                    outcome = e && e.code ? e.code : describe(e);
                } finally {
                    latch.countDown();
                }
            },
        }));
        expect(latch.await(20, java.util.concurrent.TimeUnit.SECONDS), 'the UI thread did not answer');
        expect(outcome === 'INVALID_ARGUMENT', 'outcome=' + outcome);
        return 'INVALID_ARGUMENT';
    });
    check('output.default', () => {
        let made = [];
        try {
            let r = pdf.merge([TEXT, TEXT]);
            made.push(r.path);
            // The result may name shared storage by its real path (/storage/emulated/0/...). zh-CN: 结果可能用共享存储的真实路径 (/storage/emulated/0/...).
            let home = canonical(DEFAULT_DIR) + '/';
            expect(canonical(r.path).indexOf(home) === 0 && /\/text-merge-\d{8}-\d{6}(-\d+)?\.pdf$/.test(r.path), 'merge path=' + r.path);
            expect(exists(r.path) && r.pageCount == 2, 'merge output');
            let png = pdf.renderToFile(TEXT, 1);
            made.push(png.path);
            expect(canonical(png.path).indexOf(home) === 0 && /\.png$/.test(png.path) && exists(png.path), 'render path=' + png.path);
            return files.getName(r.path) + ' ' + files.getName(png.path);
        } finally {
            made.forEach(p => files.remove(p));
        }
    });
    check('output.samePath', () => {
        let same = OUT + 'same.pdf';
        files.copy(OUTLINE, same);
        let r = pdf.rotate(same, same, 90, { pages: '1' });
        expect(r.path === same && rotations(same) === '90,0,0,0,0,0', 'rotations=' + rotations(same));
        expect(leftovers(OUT).length == 0, 'left ' + leftovers(OUT));
        return 'replaced in place';
    });
    check('path.relative', () => {
        let name = 'aj6-paper-relative.pdf', absolute = files.path('./' + name);
        files.copy(TEXT, absolute);
        try {
            expect(pdf.info('./' + name).pageCount == 1, 'relative info');
            return 'cwd=' + files.cwd();
        } finally {
            files.remove(absolute);
        }
    });
}

// Appendix B.5. zh-CN: 附录 B.5.

async function examples() {
    // Leave the first part of this async function first (see sync.beforeAwait in main).
    // zh-CN: 先离开本 async 函数的第一段 (见 main 中的 sync.beforeAwait).
    await null;
    check('b5.read', () => {
        let info = pdf.info(OUTLINE);
        let img = pdf.render(OUTLINE, 1, { dpi: 200 });
        let size = img.getWidth() + 'x' + img.getHeight();
        try {
            images.save(img, OUT + 'page-1.png');
        } finally {
            img.recycle();
        }
        expect(info.pageCount == 6 && info.title === 'Outline sample', 'info=' + info.pageCount + ' ' + info.title);
        expect(Math.abs(Number(size.split('x')[0]) - 1653) <= 1 && exists(OUT + 'page-1.png'), 'render 200 dpi=' + size);
        return 'pageCount=6 title render200=' + size;
    });
    check('b5.ocr', () => {
        try {
            let pages = pdf.text(SCANNED, { ocr: true });
            let found = words(pages.join('\n'));
            expect(pages.sources[0] === 'ocr' && pages[0].trim().length > 0, 'sources=' + pages.sources + ' chars=' + pages[0].trim().length);
            if (found >= 6) return 'ocr=yes words=' + found + '/' + OCR_WORDS.length;
            // How well the engine reads the page is the OCR plugin's business: compare with the ocr module on a 300 dpi
            // render of the same page (one word of slack). zh-CN: 引擎识别得好不好归 OCR 插件: 与 ocr 模块识别同一页 300 dpi 渲染的结果比较 (允许差一个词).
            let img = pdf.render(SCANNED, 1, { dpi: 300 }), manual;
            try {
                manual = words(ocr.recognizeText(img).join('\n'));
            } finally {
                img.recycle();
            }
            expect(found >= manual - 1, 'words=' + found + '/' + OCR_WORDS.length + ', ocr.recognizeText found ' + manual);
            return 'ocr=yes words=' + found + '/' + OCR_WORDS.length + ' ocr.recognizeText=' + manual + '/' + OCR_WORDS.length + ' (the OCR engine reads this page poorly)';
        } catch (e) {
            if (e instanceof pdf.PdfError && e.code === 'OCR_UNAVAILABLE') return 'ocr=OCR_UNAVAILABLE (no OCR plugin)';
            throw e;
        }
    });
    check('b5.document', () => {
        let doc = pdf.open(ENCRYPTED, { password: OWNER });
        try {
            let hits = doc.search('Page');
            let r = doc.save(OUT + 'book-first-2.pdf', { pages: '1-2', rotate: { pages: '2', degrees: 90 } });
            expect(hits.length >= 2 && r.pageCount == 2, 'hits=' + hits.length + ' save=' + JSON.stringify(r));
            let encrypted = pdf.info(r.path, { password: OWNER }).encrypted;
            return 'hits=' + hits.length + ' saved=2 pages encrypted=' + encrypted;
        } finally {
            doc.close();
        }
    });
    await checkAsync('b5.mergeAsync', async () => {
        let stages = [];
        let task = pdf.mergeAsync([TEXT, OUTLINE], OUT + 'ab.pdf', { onProgress: p => stages.push(p.stage + ':' + p.percent) });
        let r = await task.then(result => result);
        expect(r.path === OUT + 'ab.pdf' && r.pageCount == 7, 'merge=' + JSON.stringify(r));
        return 'pageCount=7 progress=' + stages.length;
    });
    check('b5.generate', () => {
        let one = pdf.renderToFile(TEXT, 1, OUT + '1.jpg', { format: 'jpeg', dpi: 72 }).path;
        let two = pdf.renderToFile(OUTLINE, 1, OUT + '2.png', { dpi: 72 }).path;
        let album = pdf.fromImages([one, two], OUT + 'album-b5.pdf', { pageSize: 'A4', fit: 'contain', margin: 24 });
        files.write(OUT + 'notes.txt', 'Notes\nwritten by the regression script');
        let notes = pdf.fromText(files.read(OUT + 'notes.txt'), OUT + 'notes-b5.pdf', { fontSize: 14 });
        let report = pdf.fromHtml('<h1>Report</h1><p>Hello</p>', OUT + 'report-b5.pdf');
        expect(album.pageCount == 2 && notes.pageCount == 1 && report.pageCount >= 1, 'pages ' + album.pageCount + '/' + notes.pageCount + '/' + report.pageCount);
        let locked = pdf.encrypt(report.path, OUT + 'report-locked.pdf', { userPassword: 'open', ownerPassword: 'owner', permissions: { print: true, copy: false } });
        expect(pdf.info(locked.path, { password: 'open' }).permissions.copy === false, 'encrypted permissions');
        return 'album=2 notes=1 report=' + report.pageCount + ' locked';
    });
    if (!VIEW) {
        report('SKIP', 'b5.view', 'VIEW = false (the viewer opens a window; enable VIEW to check it)');
        return;
    }
    await checkAsync('b5.view', async () => {
        let session = pdf.view(OUTLINE, { page: 5, search: 'needle' });
        let pages = [];
        session.on('page', e => pages.push(e.page));
        let closed = new Promise(resolve => session.on('close', e => resolve(e.reason)));
        await sleep(2000);
        session.goTo(2);
        await sleep(1500);
        let state = session.state();
        session.close({ finish: true });
        let reason = await within(closed, 10000, 'the close event');
        expect(state.pageCount == 6 && pages.indexOf(2) >= 0, 'state=' + JSON.stringify(state) + ' pages=' + pages);
        return 'pages=' + pages.join(',') + ' close=' + reason;
    });
}

// Baseline (P2.7). zh-CN: 基线 (P2.7).

function performance() {
    if (!PERF) {
        report('SKIP', 'perf', 'PERF = false');
        return;
    }
    check('perf.render144', () => {
        let times = [];
        for (let i = 0; i < 5; i++) {
            let start = Date.now();
            let img = pdf.render(TEXT, 1);
            times.push(Date.now() - start);
            img.recycle();
        }
        let sorted = times.slice().sort((a, b) => a - b);
        emit('AJ6PDF PERF render144 median=' + sorted[2] + 'ms min=' + sorted[0] + 'ms max=' + sorted[4] + 'ms runs=5 size=1190x1684');
        return 'median=' + sorted[2] + 'ms';
    });
    check('perf.merge100', () => {
        let list = inputs(16).concat([TEXT, TEXT, TEXT, TEXT]);
        let start = Date.now();
        let r = pdf.merge(list, OUT + 'perf-merge.pdf', { outline: 'none' });
        let ms = Date.now() - start;
        expect(r.pageCount == 100, 'pageCount=' + r.pageCount);
        emit('AJ6PDF PERF merge100 ms=' + ms + ' inputs=20 pages=100 sizeBytes=' + r.sizeBytes);
        return 'ms=' + ms;
    });
    check('perf.fromImages20', () => {
        let jpeg = pdf.renderToFile(OUTLINE, 1, OUT + 'perf-0.jpg', { format: 'jpeg', quality: 85 });
        let list = [];
        for (let i = 1; i <= 20; i++) {
            files.copy(jpeg.path, OUT + 'perf-' + i + '.jpg');
            list.push(OUT + 'perf-' + i + '.jpg');
        }
        let start = Date.now();
        let r = pdf.fromImages(list, OUT + 'perf-album.pdf', { pageSize: 'A4' });
        let ms = Date.now() - start;
        expect(r.pageCount == 20, 'pageCount=' + r.pageCount);
        emit('AJ6PDF PERF fromImages20 ms=' + ms + ' image=' + jpeg.width + 'x' + jpeg.height + ' jpeg=' + jpeg.sizeBytes + 'B sizeBytes=' + r.sizeBytes);
        return 'ms=' + ms;
    });
}

// Run. zh-CN: 运行.

function cleanup() {
    if (KEEP) return;
    files.listDir(OUT).forEach(name => {
        let path = OUT + name;
        if (path === RESULT) return;
        if (files.isDir(path)) files.removeDir(path);
        else files.remove(path);
    });
}

async function main() {
    environment();
    if (!pdf.isAvailable()) {
        report('FAIL', 'isAvailable', 'false: install and enable 3-Paper PDF');
        return;
    }
    report('PASS', 'isAvailable', 'true');
    // A failing synchronous call in the first part of an async function, before its first await. The Rhino interpreter
    // of host builds up to 6641 computes Error stacks wrongly there (ArrayIndexOutOfBoundsException from
    // InterpreterData.getLineNumberFromPc instead of the PdfError); the other checks run after the await below, so the
    // plugin's own answers stay covered.
    // zh-CN: async 函数第一个 await 之前的同步调用失败. 宿主构建 6641 及以前的 Rhino 解释器在这里计算 Error 栈出错 (抛出
    // InterpreterData.getLineNumberFromPc 的 ArrayIndexOutOfBoundsException 而不是 PdfError); 其他检查在下面的 await 之后运行, 插件本身的应答照样覆盖.
    check('sync.beforeAwait', () => {
        expectCode('PASSWORD_REQUIRED', () => pdf.info(ENCRYPTED));
        return 'PASSWORD_REQUIRED';
    });
    await null;
    reading();
    rendering();
    imageChecks();
    pageOperations();
    generation();
    await documents();
    await progress();
    errors();
    await examples();
    performance();
}

files.removeDir(OUT);
files.ensureDir(RESULT);
let keepAlive = setInterval(() => {}, 60000);
main().then(null, e => report('FAIL', 'main', describe(e))).then(() => {
    try {
        cleanup();
    } catch (e) {
        report('FAIL', 'cleanup', describe(e));
    }
    emit('AJ6PDF DONE pass=' + counts.pass + ' fail=' + counts.fail + ' skip=' + counts.skip);
    clearInterval(keepAlive);
});
