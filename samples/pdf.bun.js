"bun";

/**
 * Bun 中使用 pdf 模块 [v6.8.1+]
 *
 * 需要 AutoJs6 6.8.1 或以上, Bun Runtime 插件 0.23.0 或以上 (Android 13 或以上), 3-Paper PDF 插件 1.0.0 或以上.
 * 先在插件中心 > Bun Runtime 的设置里授予 pdf.control (用 OCR 识别页面另需 ocr.control).
 * host.pdf 与 Node.js 的 require('pdf') 相同: 每个方法返回 Promise (带 cancel()), text 返回 { texts, pages, sources },
 * render 写出 PNG 文件或在 encoding: 'base64' 时返回 Base64; 选项 signal (AbortSignal) 与 timeoutMs (毫秒) 可以取消调用,
 * 先到者生效, 取消后以 CANCELLED 拒绝, err.cause 是中止原因 (超时时为 TimeoutError).
 * pdf 的路径由 AutoJs6 读写, 不受 Bun Runtime 插件自身存储权限的限制, 所以本例只经 host.pdf 读写共享存储.
 * 输入: 脚本旁的 fixtures/ 或 /sdcard/Documents/3-Paper PDF/fixtures/ 里的 outline.pdf, 都没有时现场生成演示文档.
 * 输出写到 /sdcard/Documents/3-Paper PDF/samples/bun/ (目录自动创建), 最后一行输出结果摘要 (JSON).
 */

const sdk = process.env.AUTOJS6_HOST_CLIENT_MODULE;
if (!sdk) throw new Error("请先在插件中心启用 Bun Runtime 的宿主能力桥");
const { createHostClient } = await import(sdk);
const host = await createHostClient();
if (!host.hasCapability("pdf.control")) throw new Error("请先在插件中心 > Bun Runtime 的设置里授予 pdf.control");
const pdf = host.pdf;

const base = "/sdcard/Documents/3-Paper PDF";
const outDir = base + "/samples/bun";
const summary = { errors: [] };

function report(label, err) {
  console.error(`${label} 失败 [${err.code || err.name}]: ${err.message}`);
  summary.errors.push(`${label}: ${err.code || err.name}`);
}

/* 用 pdf.info 判断文件是否存在: 找不到时以 FILE_NOT_FOUND 拒绝. */
async function fixture(name) {
  for (const dir of [import.meta.dir + "/fixtures", base + "/fixtures"]) {
    try {
      await pdf.info(dir + "/" + name);
      return dir + "/" + name;
    } catch (err) {
      if (err.code !== "FILE_NOT_FOUND") throw err;
    }
  }
  return null;
}

try {
  if (!(await pdf.isAvailable())) throw new Error("3-Paper PDF 插件未安装或未启用, 请先在插件中心安装并启用.");
  const book = (await fixture("outline.pdf"))
    || (await pdf.fromText("Bun sample\n\nSearch target: needle", outDir + "/demo.pdf")).path;
  summary.path = book;
  console.log("文档:", book);

  /* 1. 文字与搜索. */
  const { texts, pages } = await pdf.text(book, { pages: [1] });
  const hits = await pdf.search(book, "needle");
  summary.text = { pages, chars: texts[0].length, hits: hits.map(h => h.page) };
  console.log(`第 1 页 ${texts[0].length} 个字符 | "needle" 出现在第 ${summary.text.hits.join(", ")} 页`);

  /* 2. 渲染为 PNG 文件. */
  const image = await pdf.render(book, 1, { width: 480, output: outDir + "/page-1.png" });
  summary.render = `${image.width}x${image.height}`;
  console.log(`render: ${summary.render} -> ${image.path}`);

  /* 3. 提取页面, 用 timeoutMs 限定最长时间. */
  const extracted = await pdf.extract(book, outDir + "/first-page.pdf", "1", { timeoutMs: 30000 });
  summary.extract = extracted.pageCount;
  console.log(`extract: ${extracted.pageCount} 页 -> ${extracted.path}`);

  /* 4. timeoutMs 很短时以 CANCELLED 拒绝, err.cause 是 TimeoutError (也可能在超时前就完成). */
  try {
    await pdf.merge(Array(8).fill(book), outDir + "/timeout.pdf", { timeoutMs: 1 });
    summary.timeout = "finished";
  } catch (err) {
    if (err.code !== "CANCELLED") throw err;
    summary.timeout = `CANCELLED (${err.cause && err.cause.name})`;
  }
  console.log("timeoutMs: 1 ->", summary.timeout);
} catch (err) {
  report("pdf", err);
}
console.log("summary " + JSON.stringify(summary));
