"bun";

/**
 * Bun 中使用 pdf 模块 [v6.8.1+]
 *
 * 需要 AutoJs6 6.8.1 或以上, Bun Runtime 插件 0.23.0 或以上 (Android 13 或以上), 3-Paper PDF 插件 1.0.0 或以上; 第 5 段 (页码与印章, 书签,
 * 压缩与表单) 需要 3-Paper PDF 1.1.0 或以上, 插件为 1.0.x 时这一段的方法以 UNSUPPORTED 拒绝, 本例给出提示后跳过.
 * 先在插件中心 > Bun Runtime 的设置里授予 pdf.control (用 OCR 识别页面另需 ocr.control).
 * host.pdf 与 Node.js 的 require('pdf') 相同: 每个方法返回 Promise (带 cancel()), text 返回 { texts, pages, sources },
 * render 写出 PNG 文件或在 encoding: 'base64' 时返回 Base64; 选项 signal (AbortSignal) 与 timeoutMs (毫秒) 可以取消调用,
 * 先到者生效, 取消后以 CANCELLED 拒绝, err.cause 是中止原因 (超时时为 TimeoutError).
 * pdf 的路径由 AutoJs6 读写, 不受 Bun Runtime 插件自身存储权限的限制, 所以本例只经 host.pdf 读写共享存储.
 * 输入: 脚本旁的 fixtures/ 或 /sdcard/Documents/3-Paper PDF/fixtures/ 里的 outline.pdf, 都没有时现场生成演示文档;
 * 第 5 段另用同一位置的 form.pdf 演示 forms 与 fillForm, 没有时跳过这两个方法.
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

  /*
   * 5. 3-Paper PDF 1.1.0 的新方法: 页码与文字印章, 替换书签, 压缩, 读取与填写表单. pdf.info 另给出 formFieldCount.
   *    插件为 1.0.x 时以 UNSUPPORTED 拒绝.
   */
  try {
    const stamped = await pdf.stamp(book, outDir + "/stamped.pdf", {
      pageNumbers: true,
      items: [{ type: "text", text: "Bun", fontSize: 36, position: "top-right", opacity: 0.4, pages: 1 }],
    });
    const outlined = await pdf.setOutline(stamped.path, outDir + "/outlined.pdf", [
      { title: "Start", page: 1, children: [] },
      { title: "End", page: stamped.pageCount, children: [] },
    ]);
    const outline = await pdf.outline(outlined.path);
    const small = await pdf.compress(outlined.path, outDir + "/compressed.pdf", { imageDpi: 96, imageQuality: 60, removeMetadata: true });
    summary.v110 = {
      stamp: stamped.pageCount,
      outline: outline.map(o => o.title),
      compress: { sourceBytes: small.sourceBytes, sizeBytes: small.sizeBytes, warningCodes: small.warningCodes },
      formFieldCount: (await pdf.info(book)).formFieldCount,
    };
    console.log(`页码与印章: ${stamped.path} | 书签: ${summary.v110.outline.join(", ")}`);
    console.log(`compress: ${small.sourceBytes} -> ${small.sizeBytes} 字节 | warningCodes: ${JSON.stringify(small.warningCodes)}`);
    const form = await fixture("form.pdf");
    if (form) {
      const fields = await pdf.forms(form);
      const filled = await pdf.fillForm(form, outDir + "/form-filled.pdf", { "address.city": "Berlin" });
      summary.v110.fields = fields.length;
      summary.v110.filled = filled.filled;
      console.log(`forms: ${fields.length} 个字段 | fillForm: 填写 ${filled.filled} 个 -> ${filled.path}`);
    }
  } catch (err) {
    if (err.code !== "UNSUPPORTED") throw err;
    summary.v110 = { unsupported: err.message };
    console.error(`1.1.0 的新方法不可用: ${err.message}`);
    console.error("已安装的 3-Paper PDF 为 1.0.x 时, 请在插件中心把它更新到 1.1.0 或以上, 再运行第 5 段.");
  }
} catch (err) {
  report("pdf", err);
}
console.log("summary " + JSON.stringify(summary));
