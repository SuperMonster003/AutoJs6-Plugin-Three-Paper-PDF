<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-paper-pdf-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>读取, 渲染, 合并, 拆分, 生成与加密 PDF 文件, 内置查看器与文件管理器入口</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 语言 (Languages)

******

当前 README.md 支持以下语言:

- 简体中文 [zh-Hans] # 当前
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ar.md)

******

### 简介

******

3-Paper PDF 是 AutoJs6 的 PDF 插件, 也是不装 AutoJs6 也能单独使用的 PDF 工具箱与查看器. 它可以读取 PDF 的信息, 文本, 大纲与内嵌图片, 把页面渲染为图片; 可以合并, 拆分, 抽取, 删除, 重排与旋转页面; 可以把图片, 文本或 HTML 生成为 PDF, 并对文档加密或解密.

处理在插件内用 Android 系统的 `PdfRenderer` 与纯 Java 的 PDFBox-Android 完成, AutoJs6 宿主因此保持精简. AutoJs6 脚本通过全局模块 `pdf` 使用这些功能, Node.js 与 Bun 运行时插件中的脚本也可以使用; 脚本传入的路径由 AutoJs6 打开后交给插件, 插件处理脚本文件不需要存储权限. 插件自带查看器, 可以从启动器, AutoJs6 的文件管理器, 其他应用的 "打开方式" 或脚本 (`pdf.view`) 打开.

> AutoJs6 脚本的 `pdf` 模块与文件管理器入口需要 AutoJs6 6.8.1 (内部版本号 6641) 或更高版本, 较旧的 AutoJs6 在插件中心会把本插件显示为不兼容; 工具箱与查看器不依赖 AutoJs6, 可以单独使用.

******

### 功能亮点

******

- 读取: 页数, 页面尺寸, 元数据, 加密状态与权限; 按页提取文本与大纲 (书签); 搜索文字并返回页码与位置; 列出并导出内嵌图片.
- 渲染: 把页面渲染为 `Image` 对象或 PNG / JPEG / WebP 文件, 默认 144 dpi, 也可以按 dpi, 缩放比例或目标宽高指定.
- 页面操作: 合并 (保留书签), 拆分, 抽取, 删除, 重排与旋转页面; 页码从 1 开始, 范围写作 `'1-3,5,8-'`.
- 生成: 图片转 PDF (JPEG 直接嵌入, 不重新编码), 文本转 PDF (可指定字体), HTML 转 PDF (不加载远程网址).
- 加密: 用密码打开加密文档, 以 AES-256 加密并设置权限, 去除加密.
- 查看器: 连续滚动, 双指缩放, 缩略图, 大纲, 搜索高亮, 跳页, 密码输入与夜间模式; 其他应用可以用 "打开方式" 或分享把 PDF 交给它; 脚本可以打开查看器并接收翻页与关闭事件.
- 工具箱: 合并, 拆分, 转图片, 图片转 PDF, 加密, 解密, 提取文本与页面整理 8 项工具, 任务在后台依次执行, 通知栏显示进度; 最近文件也会记住用系统文件选择器打开的文件.
- AutoJs6 文件管理器: 点按 PDF 即在插件查看器中打开, 压缩包内不超过 512 MiB 的 PDF 也能直接打开; 菜单提供合并, 拆分, 转图片, 提取文本, 加密与移除密码.
- OCR: `pdf.text(path, { ocr: true })` 借助已安装的 OCR 插件 (ML Kit OCR, Paddle OCR 或 Rapid OCR) 识别没有文本层的扫描页.
- Node.js 与 Bun: Node.js Runtime 2.2.0 与 Bun Runtime 0.23.0 起提供同样的 `pdf` 模块, 方法返回 Promise, 可以用 `signal` 或 `timeoutMs` 中止调用.

******

### 使用方法

******

1. 从 [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases) 下载 APK 并安装. 所有 ABI 共用一个安装包.
2. 如需在 AutoJs6 脚本或文件管理器中使用, 请把 AutoJs6 升级到 6.8.1 (内部版本号 6641) 或更高版本, 并确认插件中心中本插件已启用.
3. 如需使用插件自带的工具箱, 打开插件并按提示授予所有文件访问权限; 脚本操作不需要此权限.

> 插件支持 Android 7.0 (API 24) 及以上设备. PDF 处理全部使用系统 API 与纯 Java 库, 唯一的原生库来自 Jetpack Compose, 已按 16 KB 内存页对齐, 因此所有 ABI 共用一个安装包.

******

### 兼容性

******

- Android 7.0 (API 24) 及以上, 所有 ABI 共用一个安装包; 工具箱与查看器不依赖 AutoJs6.
- AutoJs6 脚本与文件管理器: 需要 AutoJs6 6.8.1 (内部版本号 6641) 或更高版本.
- AutoJs6 打包的应用: 需由 AutoJs6 6.8.1 (内部版本号 6641) 或更高版本打包, 运行的设备上需安装本插件; 打包时自动加入 `org.autojs.plugin.pdf.permission.RUNTIME` 权限, 不需要修改 `project.json` (Node.js 项目需声明 `files`, Bun 项目需声明 `pdf.control`).
- Node.js: 需要 Node.js Runtime 插件 2.2.0 及以上, 用 `require('autojs6:pdf')` 加载; 项目在 `project.json` 的 `node.permissions` 中声明 `files`, 写文件另需 `files.write`, 识别文字另需 `ocr`, 打开查看器另需 `ui`.
- Bun: 需要 Bun Runtime 插件 0.23.0 及以上 (Android 13 及以上), 通过 `host.pdf` 使用; 在 Bun Runtime 的设置页开启 `pdf.control`, 识别文字另需 `ocr.control`.
- OCR: 识别扫描页文字需另装 OCR 插件 (ML Kit OCR, Paddle OCR 或 Rapid OCR); 未安装时识别扫描页报 `OCR_UNAVAILABLE`, 其他功能不受影响.

******

### 脚本 API

******

AutoJs6 脚本通过全局模块 `pdf` (也可写作 `$pdf`) 使用这些功能 (完整说明见 [AutoJs6 文档 > PDF](https://docs.autojs6.com/#/pdf)):

```javascript
let info = pdf.info("./book.pdf");
console.log(info.pageCount, info.title);
let pages = pdf.text("./scan.pdf", { ocr: true });
let img = pdf.render("./book.pdf", 1, { dpi: 200 });
images.save(img, "./page-1.png");
img.recycle();

let doc = pdf.open("./book.pdf", { password: "1234" });
let hits = doc.search("AutoJs6");
doc.save("./book-first-3.pdf", { pages: "1-3", rotate: { pages: "2", degrees: 90 } });
doc.close();

pdf.mergeAsync(["./a.pdf", "./b.pdf"], "./ab.pdf", { onProgress: (p) => console.log(p.stage, p.percent) })
    .then((r) => console.log(r.path, r.pageCount));

pdf.fromImages(["./1.jpg", "./2.png"], "./album.pdf", { pageSize: "A4", fit: "contain", margin: 24 });
pdf.fromText(files.read("./notes.txt"), "./notes.pdf", { fontSize: 14 });
pdf.fromHtml("<h1>Report</h1><p>Hello</p>", "./report.pdf");
pdf.encrypt("./report.pdf", "./report-locked.pdf", { userPassword: "open", ownerPassword: "owner", permissions: { print: true, copy: false } });

let session = pdf.view("./book.pdf", { page: 5, search: "AutoJs6" });
session.on("page", (e) => console.log("page", e.page));
session.on("close", () => console.log("closed"));
```

Node.js 与 Bun 中同名方法返回 Promise (Bun 通过 `host.pdf` 使用):

```javascript
"nodejs";
const pdf = require("autojs6:pdf");

(async () => {
    const { texts, pages } = await pdf.text("./book.pdf", { pages: "1-3" });
    console.log(pages, texts[0]);
    const merged = await pdf.merge(["./a.pdf", "./b.pdf"], "./ab.pdf", {
        displayNames: ["Part A", null],
        timeoutMs: 60000,
    });
    console.log(merged.path, merged.pageCount);
})();
```

- 便捷方法 `pdf.info` / `pdf.text` / `pdf.render` / `pdf.merge` / `pdf.split` / `pdf.fromImages` 等每次自行打开与关闭文档; `pdf.open(path, options)` 返回 `PdfDocument`, 多次操作共用一次打开, 每个脚本最多同时打开 8 个文档.
- 每个方法都有阻塞形态与返回 Promise 的 `xxxAsync` 形态, 二者参数与结果相同; 阻塞形态不能在 UI 线程调用 (报 `INVALID_ARGUMENT`), 界面脚本请用 `xxxAsync`; 写文件的方法接受 `onProgress`, Promise 带 `cancel()`; 取消或停止脚本时不会留下写了一半的文件.
- `pdf.merge` 的 `displayNames` 为每个输入的书签项命名: `null` 取文件名 (去掉 `.pdf`), 空白字符串取该文档的标题 (没有标题时为 `Document <n>`); 选项超过约 63 KiB 时报 `LIMIT_EXCEEDED`, 不写输出.
- `pdf.fromHtml` 默认执行页面中的脚本, `javaScript: false` 可关闭; 不加载任何外部内容, 网络请求以空内容应答并记入 `warnings`.
- `pdf.view(path, options)` 打开查看器并返回 `PdfViewSession`, 可监听 `page` / `close` 事件, 调用 `goTo(page)`, `state()`, `close()`; AutoJs6 不在前台时 (Android 10 及以上且没有悬浮窗权限) 报 `UI_REQUIRES_FOREGROUND`.
- Node.js 与 Bun: 全部方法返回 Promise; `signal` (AbortSignal) 与 `timeoutMs` 中止调用, 都以 `CANCELLED` 拒绝, 超时的 `cause` 为 `TimeoutError`; `timeoutMs` 须为大于 0 且不超过 2147483647 的有限数, 小数向上取整.
- 失败时抛出 `PdfError`, 其 `code` 为稳定的错误码, 例如 `PASSWORD_REQUIRED`, `WRONG_PASSWORD` 或 `PAGE_OUT_OF_RANGE`; `pdf.isAvailable()` 可以预先探测插件是否可用.

******

### 常见问题

******

#### 不装 AutoJs6 能用吗?

可以. 工具箱与查看器都能单独使用, 只有在脚本中处理 PDF 时才需要 AutoJs6.

#### 为什么插件要申请所有文件访问权限?

只有工具箱和从启动器打开的查看器需要它, 用于读写你在界面上选择的文件. 脚本传入的路径由 AutoJs6 打开后以文件描述符交给插件, 不需要插件的存储权限.

#### 插件包含原生库吗?

PDF 处理不使用原生库: 页面渲染使用系统的 `PdfRenderer`, 其余功能使用纯 Java 的 PDFBox-Android. 安装包中唯一的原生库是 Jetpack Compose 自带的 `libandroidx.graphics.path.so`.

#### 插件中心为什么显示本插件不兼容?

本插件的脚本模块与文件管理器入口需要 AutoJs6 6.8.1 (内部版本号 6641) 或更高版本. 升级 AutoJs6 后即可启用; 在此之前工具箱与查看器照常可用.

#### AutoJs6 文件管理器能直接打开压缩包里的 PDF 吗?

能. 压缩包内不超过 512 MiB 的 PDF 可以直接在插件查看器中打开, 更大的请先解压.

#### 最近文件里的文件打不开了怎么办?

用系统文件选择器打开的文件会保留读取授权, 重启后仍能直接打开. 文件被删除, 移动或授权被撤销后, 点按该条会给出提示并把它从列表中移除, 请用 "打开 PDF" 重新选择.

******

### 权限与安全

******

插件在设计上尽量收窄权限面:

- 受保护的入口: 能力服务, 文件管理器入口服务与激活入口都受 AutoJs6 插件权限 (`org.autojs.permission.PLUGIN`) 保护, 其他应用无法直接调用. 供 AutoJs6 打包的应用使用的服务与查看器入口需要 `org.autojs.plugin.pdf.permission.RUNTIME` 权限 (打包时自动加入); 经这两个入口打开的文档与查看器会话只允许创建它们的应用调用, 文件同样以文件描述符传入.
- 存储: 脚本的文件以文件描述符传入, 插件不需要为脚本申请存储权限; 所有文件访问权限只用于工具箱与查看器中你选择的文件.
- HTML 转 PDF: 插件不加载任何外部内容 (网络请求以空内容应答, 本地文件与 `content:` 地址被禁用, 页面不能跳转); 页面中的脚本可以用 `javaScript: false` 关闭.
- 最近文件: 用系统文件选择器打开的文件保留读取授权以便下次直接打开, 至多 20 个; 条目被挤出, 移除或清空时随即释放授权. 其他应用经 "打开方式" 或分享交来的文件只有一次性授权, 不进最近文件.
- 网络与密码: 网络只用于检查更新; 文档密码只在当次操作中使用, 插件不保存.

******

### 引擎与依赖

******

页面渲染使用 Android 系统的 `PdfRenderer`; 文本, 大纲, 图片, 页面操作与加密使用 [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache-2.0, 纯 Java), 加密依赖 Bouncy Castle (MIT 式许可). PDF 处理不包含原生库, 不使用 pdfium 或 MuPDF 等原生渲染库.

******

### 插件接口

******

以下信息面向 AutoJs6 宿主与插件开发者, 宿主通过这些标识发现插件并完成能力协商:

```text
application id: io.github.supermonster003.autojs6.plugin.three.paper.pdf
plugin id: three-paper-pdf
engine: pdf
variant: default
info service: org.autojs.plugin.INFO (category three-paper-pdf)
capability service: org.autojs.plugin.PDF (category pdf)
file manager entry service: org.autojs.plugin.EXPLORER_ACTION (engine explorer-action)
viewer action: org.autojs.plugin.PDF_VIEWER_OPEN
wake action: org.autojs.plugin.action.WAKE
minimum host build: 6641
native libraries: libandroidx.graphics.path.so of Jetpack Compose only (arm64-v8a, armeabi-v7a, x86_64, x86; 16 KB aligned); a single APK for every ABI
```

******

### 开发路线图

******

功能规划, 设计决策与完成情况以可勾选清单维护在 ROADMAP.md 中. 未勾选条目表示规划意向, 不代表当前版本能力.

- [查看 ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/ROADMAP.md)

******

### 发行历史

******

#### v1.0.0

_2026/11/14_

- `提示` AutoJs6 脚本的 pdf 模块与文件管理器入口需要 AutoJs6 6.8.1 (内部版本号 6641) 或更高版本; 较旧的 AutoJs6 在插件中心显示本插件不兼容, 工具箱与查看器仍可单独使用
- `提示` 在 Node.js 或 Bun 脚本中使用需要 Node.js Runtime 2.2.0 或 Bun Runtime 0.23.0 及以上版本
- `新增` AutoJs6 脚本可以使用 pdf 模块 (也可写作 $pdf): 读取 PDF 的信息, 文本, 大纲与图片, 搜索文字, 把页面渲染为图片, 合并, 拆分, 抽取, 删除, 重排与旋转页面, 用图片, 文本或 HTML 生成 PDF, 以及加密与解密; 每个方法都有阻塞与 Async 两种形态, 可以报告进度并取消
- `新增` pdf.text 的 ocr 选项借助已安装的 OCR 插件 (ML Kit OCR, Paddle OCR 或 Rapid OCR) 识别没有文本层的扫描页
- `新增` pdf.view 在插件查看器中打开 PDF, 脚本可以接收翻页与关闭事件, 也可以跳到指定页或关闭查看器
- `新增` Node.js 与 Bun 脚本也能使用 pdf 模块, 方法返回 Promise, 可以用 signal 或 timeoutMs 中止调用
- `新增` 由 AutoJs6 6.8.1 或更高版本打包的应用也能使用 pdf 模块, 打包时自动加入所需权限; 打开的文档与查看器会话只允许创建它们的应用调用
- `新增` 查看器: 连续滚动, 双指缩放, 缩略图, 大纲, 搜索高亮, 跳页, 密码输入与夜间模式; 可以从启动器打开, 也可以在其他应用中用 "打开方式" 或分享交给本插件
- `新增` AutoJs6 文件管理器: 点按 PDF 即在插件查看器中打开, 压缩包内不超过 512 MiB 的 PDF 也能直接打开; 菜单提供合并, 拆分, 转图片, 提取文本, 加密与移除密码
- `新增` 首页: 工具入口, 任务进度, 最近文件, 以及所有文件访问权限的授权入口 (仅工具箱需要)
- `新增` 最近文件会记住用系统文件选择器打开的文件 (至多 20 个), 重启后仍能直接打开; 文件已被删除或无法访问时给出提示并从列表中移除
- `新增` 工具箱提供 8 项工具: 合并, 拆分 (按页码范围或每 n 页), PDF 转图片 (分辨率, 格式, 页码), 图片转 PDF (页面大小, 方向, 页边距), 加密, 移除密码, 提取文本 (另存为 TXT), 以及通过缩略图旋转, 删除与重排页面; 处理逻辑与文件管理器入口相同
- `新增` 工具箱任务在前台服务中依次执行, 离开工具页面后继续运行; 通知栏显示进度并可取消, 完成后可以在查看器中打开或分享结果
- `新增` 自带文件浏览器, 可以多选文件与新建文件夹; 也可以改用系统文件选择器
- `新增` 设置页: 默认输出目录 (Documents/3-Paper PDF), PDF 转图片的默认分辨率, 语言, 夜间模式, 主题色, 启动器图标, 权限状态, 发行历史与检查更新
- `新增` 检查更新: 可在设置页手动检查, 也可以在打开首页时自动检查 (每 12 小时最多一次); 版本信息来自官方插件索引
- `新增` 启动器与插件中心图标各有亮暗两套; 启动器默认使用自适应自动图标 (随系统亮暗切换), 另已内置自适应亮色, 自适应暗色与透明背景三种样式, 可以在设置页切换
- `新增` 所有 ABI 共用一个 APK: PDF 处理不含原生库, 唯一的原生库来自 Jetpack Compose, 已按 16 KB 内存页对齐
- `新增` 10 种语言的 README, 插件中心说明与更新日志, 由 JSON 源文件统一生成
- `依赖` 附加 PDFBox-Android 版本 2.0.27.0 (文本, 大纲, 图片, 页面操作与加密)
- `依赖` 附加 Jetpack Compose BOM 版本 2026.09.00 (插件界面)
- `依赖` 附加 Navigation Compose 版本 2.10.2
- `依赖` 附加 Material Color Utilities (MaterialKolor) 版本 4.1.1 (插件界面的主题色)
- `依赖` 附加 AndroidX Core KTX 版本 1.18.0

##### 更多发行历史可参阅

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/assets/doc/CHANGELOG-zh-Hans.md)

******

### 构建

******

本节面向希望从源码构建插件的开发者.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:appendDigestToReleasedFiles
py .python\generate_markdown.py --check
py .python\generate_third_party_notices.py --check
py .python\generate_icon_studio.py --check
py .python\check_repository.py
```

******

### 许可

******

项目代码使用 [Mozilla Public License 2.0](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/LICENSE). 第三方组件与数据的许可见第三方声明.

- [第三方声明 (THIRD_PARTY_NOTICES.md)](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/THIRD_PARTY_NOTICES.md)

******

### 相关链接

******

- AutoJs6 项目: https://github.com/SuperMonster003/AutoJs6
