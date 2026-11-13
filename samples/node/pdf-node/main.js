'use strict';

/**
 * Node.js 中使用 pdf 模块 [v6.8.1+]
 *
 * 需要 AutoJs6 6.8.1 或以上, Node.js Runtime 插件 2.2.0 或以上, 3-Paper PDF 插件 1.0.0 或以上.
 * require('pdf') (也可写 require('autojs6:pdf')) 得到与 AutoJs6 脚本 pdf 模块相同的方法, 选项, 默认值与错误码, 区别是:
 * - 每个方法都返回 Promise (带 cancel()), xxxAsync 是同一调用的别名.
 * - text 返回 { texts, pages, sources }; render 把图片写成 PNG 文件 (默认在工作目录的 .pdf-render/ 下, output 选项指定文件),
 *   encoding: 'base64' 时直接返回 Base64.
 * - 选项 signal (AbortSignal) 与 timeoutMs (毫秒) 都可以取消调用, 同时给出时先到者生效; 取消后以 CANCELLED 拒绝,
 *   err.cause 是中止原因 (超时时为 TimeoutError).
 * - project.json 的 node.permissions 要声明能力: 读取需要 files, 写文件 (包括不带 encoding: 'base64' 的 render) 另需 files.write,
 *   OCR 另需 ocr, 查看器 (view) 需要 files 与 ui; 缺少时以 ERR_AUTOJS6_CAPABILITY_NOT_DECLARED 拒绝.
 * pdf 的路径由 AutoJs6 读写, 不受 Node.js Runtime 插件自身存储权限的限制; 插件里的 fs 模块访问 /sdcard 则需要给插件授予
 * "所有文件访问权限", 所以本例只经 pdf 模块读写共享存储.
 * 输入: 项目目录的 fixtures/ 或 /sdcard/Documents/3-Paper PDF/fixtures/ 里的 outline.pdf, 都没有时现场生成演示文档.
 * 输出写到 /sdcard/Documents/3-Paper PDF/samples/node/ (目录自动创建), 最后一行输出结果摘要 (JSON).
 */

const path = require('node:path');
const pdf = require('pdf');

const base = '/sdcard/Documents/3-Paper PDF';
const outDir = path.join(base, 'samples', 'node');
const summary = { errors: [] };

/* 用 pdf.info 判断文件是否存在: 找不到时以 FILE_NOT_FOUND 拒绝. */
async function fixture(name) {
  for (const dir of [path.join(__dirname, 'fixtures'), path.join(base, 'fixtures')]) {
    const file = path.join(dir, name);
    try {
      await pdf.info(file);
      return file;
    } catch (err) {
      if (err.code !== 'FILE_NOT_FOUND') throw err;
    }
  }
  return null;
}

function report(label, err) {
  console.error(`${label} 失败 [${err.code || err.name}]: ${err.message}`);
  summary.errors.push(`${label}: ${err.code || err.name}`);
}

/* 没有样例文档时现场生成: 两个单页文档合并为一个, displayNames 成为目录项. */
async function demoDocument() {
  const one = await pdf.fromText('Chapter 1\n\nSearch target: needle', path.join(outDir, 'demo-1.pdf'));
  const two = await pdf.fromText('Chapter 2\n\nAnother needle.', path.join(outDir, 'demo-2.pdf'));
  return (await pdf.merge([one.path, two.path], path.join(outDir, 'demo.pdf'), { displayNames: ['Chapter 1', 'Chapter 2'] })).path;
}

async function main() {
  if (!(await pdf.isAvailable())) {
    summary.errors.push('PLUGIN_UNAVAILABLE');
    console.error('3-Paper PDF 插件未安装或未启用, 请先在插件中心安装并启用.');
    return;
  }
  const book = (await fixture('outline.pdf')) || (await demoDocument());
  summary.path = book;
  console.log('文档:', book);

  /* 1. 信息与文字. */
  try {
    const info = await pdf.info(book);
    const { texts, pages, sources } = await pdf.text(book, { pages: '1-2', layout: 'raw' });
    summary.text = { pageCount: info.pageCount, pages, sources, chars: texts.map(t => t.length) };
    console.log(`页数: ${info.pageCount} | 第 ${pages.join(', ')} 页文字来源: ${sources.join(', ')}`);
    texts.forEach((t, i) => console.log(`第 ${pages[i]} 页: ${JSON.stringify(t.trim())}`));
  } catch (err) {
    report('pdf.text', err);
  }

  /* 2. 渲染: 写成 PNG 文件, 或直接取 Base64. */
  try {
    const file = await pdf.render(book, 1, { dpi: 96, output: path.join(outDir, 'page-1.png') });
    const inline = await pdf.render(book, 1, { width: 120, encoding: 'base64' });
    summary.render = { size: `${file.width}x${file.height}`, base64Bytes: inline.sizeBytes };
    console.log(`render: ${file.width}x${file.height} -> ${file.path} | Base64 缩略图 ${inline.sizeBytes} 字节`);
  } catch (err) {
    report('pdf.render', err);
  }

  /* 3. 合并: displayNames 命名目录项, timeoutMs 限定最长时间, onProgress 报告进度. */
  try {
    const merged = await pdf.merge([book, book], path.join(outDir, 'merged.pdf'), {
      displayNames: ['First copy', null],
      timeoutMs: 60000,
      onProgress: p => console.log(`进度: ${p.stage} ${p.percent}%`),
    });
    const outline = await pdf.outline(merged.path);
    summary.merge = { pageCount: merged.pageCount, outline: outline.map(o => o.title) };
    console.log(`merge: ${merged.pageCount} 页 | 目录: ${summary.merge.outline.join(', ')}`);
  } catch (err) {
    report('pdf.merge', err);
  }

  /* 4. 用 AbortController 取消: 中止后以 CANCELLED 拒绝, err.cause 是中止原因. */
  const controller = new AbortController();
  const task = pdf.merge(Array(8).fill(book), path.join(outDir, 'cancelled.pdf'), { signal: controller.signal });
  controller.abort(new Error('stopped by the sample'));
  try {
    await task;
    summary.cancel = 'finished';
    console.log('中止前已经完成');
  } catch (err) {
    if (err.code === 'CANCELLED') {
      summary.cancel = 'CANCELLED';
      console.log(`已取消: ${err.code} | 原因: ${err.cause && err.cause.message}`);
    } else {
      report('取消', err);
    }
  }
}

main()
  .catch(err => report('main', err))
  .finally(() => console.log('summary ' + JSON.stringify(summary)));
