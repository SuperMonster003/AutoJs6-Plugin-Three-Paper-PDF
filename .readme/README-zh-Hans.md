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

处理在插件内用 Android 系统的 `PdfRenderer` 与纯 Java 的 PDFBox-Android 完成, AutoJs6 宿主因此保持精简. AutoJs6 脚本通过全局模块 `pdf` 使用这些功能, 脚本传入的路径由 AutoJs6 打开后交给插件, 插件处理脚本文件不需要存储权限. 插件自带查看器, 可以从启动器, AutoJs6 的文件管理器或脚本 (`pdf.view`) 打开.

> 当前为开发预览版: 插件可以被 AutoJs6 插件中心识别, 但读取, 渲染, 页面操作, 查看器, 工具箱与 `pdf` 模块尚未交付. 下文描述的是 1.0.0 的设计目标, 进度见开发路线图.

******

### 功能亮点

******

- 读取: 页数, 页面尺寸, 元数据, 加密状态与权限; 按页提取文本与大纲 (书签); 搜索文字并返回页码与位置; 列出并导出内嵌图片.
- 渲染: 把页面渲染为 `Image` 对象或 PNG / JPEG / WebP 文件, 默认 144 dpi, 也可以按 dpi, 缩放比例或目标宽高指定.
- 页面操作: 合并 (保留书签), 拆分, 抽取, 删除, 重排与旋转页面; 页码从 1 开始, 范围写作 `'1-3,5,8-'`.
- 生成: 图片转 PDF (JPEG 直接嵌入, 不重新编码), 文本转 PDF (可指定字体), HTML 转 PDF (不加载远程网址).
- 加密: 用密码打开加密文档, 以 AES-256 加密并设置权限, 去除加密.
- 查看器: 连续滚动, 双指缩放, 缩略图, 大纲, 搜索高亮, 密码输入与夜间模式; 脚本可以打开查看器并接收翻页与关闭事件.
- 工具箱与文件管理器: 合并, 拆分, 转图片, 图片转 PDF, 加密 / 解密与提取文本的向导; 在 AutoJs6 的文件管理器中可以直接用插件查看器打开 PDF, 溢出菜单提供同样的工具.
- OCR: `pdf.text(path, { ocr: true })` 对没有文本层的扫描页调用 AutoJs6 的 OCR 引擎.

******

### 使用方法

******

1. 从 [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases) 下载 APK 并安装. 所有 ABI 共用一个安装包.
2. 如需在脚本中使用 `pdf` 模块, 请把 AutoJs6 升级到内部版本 6641 (6.8.1) 或更高, 并在插件中心启用本插件.
3. 如需使用插件自带的工具箱, 打开插件并按提示授予所有文件访问权限; 脚本操作不需要此权限.

> 插件支持 Android 7.0 (API 24) 及以上设备. PDF 处理全部使用系统 API 与纯 Java 库, 唯一的原生库来自 Jetpack Compose, 已按 16 KB 内存页对齐, 因此所有 ABI 共用一个安装包.

******

### 脚本 API

******

脚本通过全局模块 `pdf` (也可写作 `$pdf`) 使用这些功能 (1.0.0 设计草案, 以发布版本的文档为准):

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

- 便捷方法 `pdf.info` / `pdf.text` / `pdf.render` / `pdf.merge` / `pdf.split` / `pdf.fromImages` 等每次自行打开与关闭文档; `pdf.open(path, options)` 返回 `PdfDocument`, 多次操作共用一次打开, 每个脚本最多同时打开 8 个文档.
- 每个方法都有阻塞形态与返回 Promise 的 `xxxAsync` 形态; `xxxAsync` 接受 `options.onProgress(progress)`, 返回的 Promise 带 `cancel()`, 停止脚本即取消进行中的操作并删除未完成的输出.
- `pdf.view(path, options)` 打开查看器并返回 `PdfViewSession`, 可以监听 `page` 与 `close` 事件, 调用 `goTo(page)` 与 `close()`.
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

******

### 权限与安全

******

插件在设计上尽量收窄权限面:

- 受保护的入口: 能力服务, 文件管理器入口服务与激活入口都受 AutoJs6 插件权限 (`org.autojs.permission.PLUGIN`) 保护, 其他应用无法直接调用.
- 存储: 脚本的文件以文件描述符传入, 插件不需要为脚本申请存储权限; 所有文件访问权限只用于工具箱与查看器中你选择的文件.
- HTML 转 PDF: 只渲染提供的 HTML 字符串或本地文件, 不加载远程网址.
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

_2026/11/12_

- `提示` 开发预览: 插件可以被 AutoJs6 插件中心识别, 读取, 渲染, 页面操作, 查看器, 工具箱与 pdf 脚本模块尚未交付
- `提示` 需要 AutoJs6 6.8.1 (6641) 或更高版本 (准确的最低版本号待宿主侧改动落地后回填)
- `新增` 插件骨架: Wake 激活协议, 插件中心身份服务, PDF 能力服务 (契约版本 1) 与文件管理器入口服务
- `新增` 首页提供所有文件访问权限的授权入口 (仅工具箱需要)
- `新增` 所有 ABI 共用一个 APK: PDF 处理不含原生库, 唯一的原生库来自 Jetpack Compose, 已按 16 KB 内存页对齐
- `新增` 10 种语言的 README, 插件中心说明与更新日志, 由 JSON 源文件统一生成
- `依赖` 附加 PDFBox-Android 版本 2.0.27.0 (文本, 大纲, 图片, 页面操作与加密)
- `依赖` 附加 Jetpack Compose BOM 版本 2026.09.00 (插件界面)
- `依赖` 附加 Navigation Compose 版本 2.10.2
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
