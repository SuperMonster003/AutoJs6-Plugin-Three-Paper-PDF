# 3-Paper PDF 插件路线图

更新: 2026-11-12 (调度会话首轮拍板 Q1-Q3 / Q6-Q8 落盘, 基线刷新见附录 F 的 2026-11-12 快照: 宿主 6.8.0 已于 2026-11-03 公开, 宿主侧条目进 6.8.1; 多语言文案改为所有语言一次写全); 2026-10-22 (立项). 本文件是 `AutoJs6-Plugin-Three-Paper-PDF` (显示名 `3-Paper PDF`, PDF 方向, 插件选题分析 2026-10 的第 4 优先级候选) 的唯一进度与决策记录. 立项基线: 宿主 AutoJs6 `master` `e25e28f88` (2026-10-22, `VERSION_NAME` 6.8.0 未发布, `VERSION_BUILD` 4283, versionCode 5935; 该 hash 已因 6.8.0 公开时的历史重写失效, 现行基线见附录 F), 3-Folio EPUB (契约 `epub-api`, 阅读器两步启动, Explorer 预览器降级对话框) 与 3-Prism Camera (SharedMemory 位图传输, 契约模块版式) 作技术先例, 3-Slate Studio `ROADMAP.md` 作为版式与流程模板.

使用方式:

- 每个条目完成时在条目前打勾 (`[x]`), 在条目末尾写提交 hash (插件仓库) 或 "宿主本地提交 `<hash>`" (宿主仓库只落本地提交, 推送由维护者决定). 阶段总览的状态列同步更新.
- 第 1 节是维护者经七轮选择题 (2026-10-22) 选定的固定决策, 不再重问. 第 2 节是据此派生的技术决策, 进入对应阶段前可推翻 (推翻点见各条), 推翻时在条目里写明理由并改写该行.
- 实现中遇到需要维护者拍板的事, 不阻塞的按推荐项先行, 登记到附录 D (编号, 问题, 选项, 推荐项, 是否已先行, 影响的条目, 改选返工量) 与看板 `D:/idea-projects/.claude/boards/3-paper.md`; 阻塞的停下来用选择题问维护者.
- 每完成一个子阶段就把进度写进本文件, 不靠会话上下文记住.
- 条目前缀: (插件) 本仓库; (宿主) AutoJs6 仓库; (索引) AutoJs6-Official-Plugins-Index; (文档) AutoJs6-Documentation / TypeScript-Declarations / Ace / Offline-Docs; (测试) 设备或 AVD 验证; (发布) 发布门禁.

---

## 1. 固定决策

维护者 2026-10-22 经七轮选择题选定, 不再重问.

| 编号 | 事项 | 决策 |
| --- | --- | --- |
| D1 | 名称 | `3-Paper PDF`: Word `Paper` (纸页), 后缀 `PDF`. 仓库 `AutoJs6-Plugin-Three-Paper-PDF`, 机器标识 `three-paper-pdf`. |
| D2 | 1.0.0 范围 | 全集: 读取 (元数据, 页数与页尺寸, 文本, 大纲, 页面渲染为图片, 内嵌图片提取), 页面操作 (合并, 拆分, 抽页, 旋转, 删页, 重排), 生成 (图片转 PDF, 文本转 PDF, HTML 转 PDF), 加密与解密, 自绘查看器, 工具箱界面, 文件管理器入口. 表单, 水印 / 页码, 可搜索 PDF, 压缩归 1.1.0 (D20). |
| D3 | 引擎 | 系统 API (`android.graphics.pdf.PdfRenderer` 渲染, `PdfDocument` 生成) + PDFBox-Android `com.tom-roush:pdfbox-android` 2.0.27.0 (Apache-2.0, 纯 Java: 文本, 大纲, 图片, 页面操作, 加密). 单个 universal APK, PDF 处理无原生库 (Compose 自带的 `libandroidx.graphics.path.so` 除外, 见 Q9), 不拆 ABI; minSdk 24. 不用 `androidx.pdf` (minSdk 28, beta) 与 pdfium 系原生库. |
| D4 | 脚本 API 形态 | 新全局模块 `pdf` (`$pdf` 同对象): 对象层 `pdf.open(path, options)` 返回 `PdfDocument` 句柄 (多次操作共用一次打开), 便捷层 `pdf.info` / `text` / `render` / `merge` / `split` / `fromImages` 等每次自行打开关闭. 每个方法都有同步形态与 `xxxAsync` 形态 (与 `epub` 同约定: 同步阻塞脚本线程, 不可在 UI 线程调用; Async 返回 Promise, 回调在脚本线程). |
| D5 | 独立界面 | 工具箱式界面 (合并, 拆分, 转图片, 图片转 PDF, 加密 / 解密, 提取文本等向导) + 自绘 Compose 查看器 (不调系统或第三方阅读器). |
| D6 | 文件管理器入口 | Explorer Action: 主动作 = 用插件查看器打开 (宿主把 `pdf` 文件类型改为可预览并新增 `PreviewerType.PDF`, 未装插件时走 `ExplorerDocumentPreviewerPluginUi` 的引导安装 / 用其他应用打开); 溢出菜单 = 工具 (合并所选, 拆分, 转图片, 加密, 解密, 提取文本). |
| D7 | 文件访问 | 脚本传入的路径由宿主打开为 fd 传给插件, 读与写两个方向都走 fd (输出文件由宿主创建后传写 fd), 插件处理脚本文件不依赖存储权限; 插件独立界面 (工具箱, 从启动器打开的查看器) 申请 `MANAGE_EXTERNAL_STORAGE` (与 3-Maple Image 一致, API 32 及以下另加 `READ/WRITE_EXTERNAL_STORAGE`), 未授权时由界面引导. |
| D8 | HTML 转 PDF | 插件进程内离屏 WebView 的 `createPrintDocumentAdapter` 产出 PDF (隐藏的 `LayoutResultCallback` / `WriteResultCallback` 构造器经同包壳类访问, 稳定性 spike 见附录 D Q5); 输入为 HTML 字符串或本地文件路径, 不加载远程 URL; 选项 `javaScript` (缺省 true) 控制是否执行页面脚本 (Q35 已决 (c), Q61 已决 (a)). |
| D9 | 位图传输 | 渲染结果经 `SharedMemory` (API 27+) 跨进程传回宿主, 由宿主包成 `Image` 对象; API 26 及以下或 SharedMemory 失败时回落为插件把 PNG 写进宿主提供的临时文件 fd (3-Prism Camera 先例). |
| D10 | 句柄与并发 | 每个脚本最多 8 个打开的 `PdfDocument` (超出 `LIMIT_EXCEEDED`), 同一文档的操作串行, 插件全局渲染池 2 个并发; 脚本退出时宿主关闭其全部句柄. |
| D11 | 进度与取消 | Async 形态接受 `options.onProgress(progress)`; 返回的 Promise 带 `cancel()`; 契约用 oneway 进度回调 + `cancel(opId)`; 停止脚本即取消其全部进行中操作并删除未完成的输出. |
| D12 | 加密 | 1.0.0 同时做读 (带密码打开, 返回权限位) 与写 (`pdf.encrypt` AES-256, 用户 / 所有者密码与权限位; `pdf.decrypt`); 无密码打开加密文档报 `PASSWORD_REQUIRED`, 密码错报 `WRONG_PASSWORD`. |
| D13 | Node / Bun 桥接 | 1.0.0 即做: `RuntimePdfBridge` 与 `NodeBridgeModules.PDF`, 方法与 Rhino 一致 (Promise 签名), 渲染结果返回文件路径 (可选 base64), 独立授权与 `epub` 相同. |
| D14 | 向导与默认启用 | `PluginInstallWizardCatalog` `FILE_MANAGER` 分类 `official("three.paper.pdf")`, 回退标题 `Three Paper PDF`; 加入 `PluginDefaultEnabledPolicy` 默认启用 (与 3-Folio EPUB 相同). |
| D15 | CI | GitHub Actions: JVM 单元测试 + API 33 x86_64 AVD 冒烟仪器测试 (绑定, `getInfo`, 打开夹具, 渲染一页); release 工作流 tag 触发. |
| D16 | 调度分配方式 | 全部卡片制: 调度会话每批 (含首批) 发任务卡片, 由维护者点击启动. |
| D17 | 渲染默认值 | 默认 144 dpi (A4 约 1190 x 1684), 单次渲染上限 16 M 像素 (超出 `LIMIT_EXCEEDED`); 选项 `dpi` / `scale` / `width` / `height` (四选一, 等比), `transparent` (默认白底), `page` 从 1 计. |
| D18 | 图片转 PDF | `pageSize` 默认 `'fit'` (页面等于图片尺寸), 可选 `'A4'` / `'Letter'` / `[w, h]` 磅; `fit` 为 `contain` (默认) / `cover`; `margin` 磅; `orientation` 默认 `auto` (按图片长宽); JPEG 直接嵌入不重编码, 其他格式按 `quality` (默认 90) 转 JPEG, `quality: 'lossless'` 走无损 (Flate). |
| D19 | OCR | 宿主侧实现 `pdf.text(path, { ocr: true })`: 对无文本层的页 (或 `ocr: 'always'`) 经插件渲染后调用宿主 `ocr` 模块的可用引擎, 无引擎时报 `OCR_UNAVAILABLE`; 文档另给 "渲染 + `ocr.recognize`" 的手动样例. |
| D20 | 1.1.0 | 表单 (AcroForm 读取, 填写, 扁平化), 水印 / 页码 / 页眉页脚, 可搜索 PDF (OCR 结果写成不可见文本层), 压缩 (图片重采样与对象流). |
| D21 | 查看器会话 | `pdf.view(path, { page, password, search })` 打开查看器并返回 `PdfViewSession`: 事件 `page` / `close`, 方法 `goTo(page)` / `close()` / `state()`; 宿主不在前台且无法启动 Activity 时报 `UI_REQUIRES_FOREGROUND`. |
| D22 | 文本转 PDF | `pdf.fromText` 由插件用 `PdfDocument` + `StaticLayout` 排版 (系统字体或 `fontPath` 指定的 TTF / OTF), 选项 `pageSize` / `margin` / `fontSize` / `lineSpacing` / `title`. |
| D23 | 查看器生命周期 | 脚本退出后查看器留在前台 (3-Folio EPUB D32 同款), `session.close()` 只断开事件, `session.close({ finish: true })` 结束查看器 Activity. |
| D24 | 图标 | 候选 B "叠页 (前页折角 + 三条文本线)": 源图 `.python/icons/three-paper-ic-launcher-light.png` / `-dark.png` (1024 px 透明 RGBA, alpha 相同, 不透明像素 `#272727` / `#D8D8D8`, 本次立项会话生成并入库), 正式资源由 Icon Studio 在 P0.2 生成. |
| D25 | 立项会话交付 | 立项会话只交付 ROADMAP, 图标源图与看板 `3-paper.md`; 不启动调度会话, 不发任务卡片; 调度由维护者在新会话 (从桌面端已保存环境 dispatch 启动) 输入 `/dispatch 3-paper` 启用. |

---

## 2. 派生技术决策

D26-D42 据第 1 节派生, 进入对应阶段前可推翻 (推翻点见各条), 之后视同固定.

| 编号 | 决策 | 含义 | 推翻点 |
| --- | --- | --- | --- |
| D26 | 工具链基线 | 按 `AUTOJS6_PLUGIN_NEW_REPO_AGENTS.md` 的占位符表与 3-Slate Studio 的当前依赖线: compileSdk / targetSdk 37, minSdk 24, Kotlin 2.x, Compose BOM 2026.09.00 + Material 3, navigation 2.10.2, `PLATFORM_VERSIONS_PLUGIN_VERSION` 1.9.1, `pdfbox-android` 2.0.27.0 (Q1 已决锁定; 传递依赖 `bcprov-jdk15to18` / `bcpkix-jdk15to18` 1.72); 版本号起 1.0.0, `VERSION_BUILD` 在骨架阶段为提交数 + 1. 1.0.0 APK 为 universal 单包; 16 KB 页校验只需确认依赖 AAR / JAR 内无 `.so`. 2026-11-12 进入 P0.1 前核对: `PLATFORM_VERSIONS_PLUGIN_VERSION` 1.9.1 仍是平台仓库最新 tag, Compose BOM 2026.09.00 与 navigation 2.10.2 仍是 3-Chime Alarm 1.0.1 / 3-Slate Studio / 3-Prism Camera / 3-Macaw Speech 的当前线, 不变; BOM 不管理 `androidx.activity`, 另按这几个插件的同款线写 `activity-compose` 1.12.2. P0.1 落地 (2026-11-12): `activity-compose` 声明 1.12.2, 被 `androidx.activity` 原子组的冲突解析抬到 1.13.0 (与 3-Slate Studio 的锁相同), 锁文件以解析结果为准; APK 唯一的原生库是 Compose `ui-graphics` 传递的 `androidx.graphics:graphics-path` (`libandroidx.graphics.path.so`, 四个 ABI), 列入 `allowedNativeLibraries` 白名单, 由 `verifyDebugNativePageAlignment` 校验 16 KB 对齐 (Q9). | P0.1 |
| D27 | 契约命名 | `plugin-api/pdf-api`, 包 `org.autojs.plugin.pdf.api`, action `org.autojs.plugin.PDF`, engine / category `pdf`, `PdfContract.CONTRACT_VERSION = 1`, 宿主桥接包 `org.autojs.autojs.core.plugin.pdf`; 文件与 `epub-api` 同版式 (`PdfActions`, `PdfIds`, `PdfContract`, `PdfCapabilityKeys`, `PdfErrorCodes` + AIDL `IPdfPlugin`, `IPdfDocument`, `IPdfViewerSession`, oneway `IPdfProgressCallback` / `IPdfViewerCallback`), `getInfo` 居首并有 `PdfAidlOrderTest` 锁定方法顺序. | P1.1 |
| D28 | 契约请求模型 | 请求与结果用 `Bundle` (选项像 `JSON.stringify` 一样序列化, 函数被忽略, 未知键在宿主以 `INVALID_ARGUMENT` 拒绝); 输入文件一律 `ParcelFileDescriptor` (只读), 输出文件由宿主创建后传写 fd (D7); 输出文件数不定的操作 (拆分, 提取图片) 两步走: 先 `plan` 返回清单, 宿主按清单创建文件再逐个传 fd; 长操作带 `opId`, 进度经 oneway 回调; 宿主回调 Binder 死亡即取消其全部操作; 操作默认无超时, 只靠 `cancel(opId)` 与脚本停止 (Q7 已决), 契约不设默认操作超时. 草案见附录 C. | P1.1 |
| D29 | 宿主绑定与能力声明 | `PdfPluginHost` 基于 `core/plugin/AidlPluginHost.kt` (绑定 flags 同 `EpubPluginHost`, 含 `allowActivityStarts`, 以便宿主后台时插件界面弹出授权页); 查看器两步启动: `openViewer` 返回会话令牌, 宿主用显式组件 + 令牌启动 `ThreePaperPdfViewerActivity` (EPUB D12 同款); 脚本能力声明: `pdf.*` 需 `files` (读写脚本路径), `pdf.view` 需 `ui`, OCR 分支需 `ocr`; 裸脚本能力声明与 MCP / Node 的声明保持一致. | P1.2 |
| D30 | 渲染路径 | 页面渲染用系统 `PdfRenderer` (fd 必须可 seek, 宿主打开普通文件即可); 加密文档 `PdfRenderer` 打不开, 插件先用 PDFBox 解密到插件缓存目录的临时文件再渲染, 关闭句柄时删除; `transparent: false` 时先填白底 (`PdfRenderer` 不绘背景); 位图 `ARGB_8888`, 经 `SharedMemory` 传回时附 `width` / `height` / `rowBytes`. 不用 API 35 的 `PdfRenderer` 文本 API (各版本行为不一), 文本统一走 PDFBox. | P2.3 |
| D31 | 文本与大纲 | 文本用 PDFBox `PDFTextStripper` 按页提取 (`pages` 范围, 每页返回字符串, 单次调用上限 `MAX_TEXT_CHARS_PER_CALL`, 超出分块), 搜索用 `PDFTextStripper` 的位置信息返回页码与命中矩形 (查看器高亮共用); 大纲用 `PDDocumentOutline` 展平为 `{ title, page, level, children }`; 元数据用 `PDDocumentInformation` + XMP 的标题回退. | P2.2 |
| D32 | 页面操作与保存 | 合并 `PDFMergerUtility` (保留大纲与书签, 可选 `outline: 'merge' / 'none'`; `merge` 时每个输入一个大纲条目, 标题取宿主传来的 `displayNames` (文件名), 没有时退回文档标题, 再退回 `Document <n>`, Q31 已决 (b)), 拆分 `Splitter` 或按 `ranges` 复制页, 抽页 / 删页 / 重排 / 旋转 在 `PDDocument` 上操作后 `save` 到输出 fd; 页码范围写法 `'1-3,5,8-'` 或数组, 从 1 计, 越界 `PAGE_OUT_OF_RANGE`; 输出与输入同一路径时先写临时文件再由宿主替换. | P2.4 |
| D33 | 图片提取与图片转 PDF | 提取: 遍历页资源 `PDImageXObject` (含内联图片), `plan` 返回 `{ page, index, width, height, format }` 清单, JPEG (`DCTDecode`) 原样导出, 其他解码为 PNG; 图片转 PDF: JPEG 用 `JPEGFactory.createFromStream` 直接嵌入, PNG / WebP / BMP 解码后按 `quality` 走 `JPEGFactory` 或 `LosslessFactory` (D18), EXIF 方向先纠正. | P2.5 |
| D34 | 加密实现 | 读: `PDDocument.load(fd, password)` + `getCurrentAccessPermission` 映射为 `permissions` 对象; 写: `StandardProtectionPolicy` + `AccessPermission`, 默认 AES-256 (`keyLength: 256`, 可选 128), 口令加密只用平台 JCA, 不注册 BouncyCastle provider (Q4 已决 (c), spike 结论见附录 D Q4; 设备缺 AES-256 时不声明 `crypto.aes256`, `keyLength: 256` 报 `UNSUPPORTED`); `decrypt` 要求能打开的密码, 输出去除加密字典. | P2.6 |
| D35 | 查看器 | Compose 自绘: 连续纵向滚动 + 双指缩放 (分块渲染, 缩放后按 tile 重渲染, 缓存上限按内存 1/8), 页码指示与跳页, 缩略图栏, 大纲抽屉, 搜索 (D31 命中矩形高亮), 密码输入, 夜间模式 (Q3 已决: 反色渲染, 图片保留原色), 分享 / 用其他应用打开; 从启动器, Explorer 主动作与 `pdf.view` 三个入口共用同一 Activity; 事件回宿主经 `IPdfViewerCallback` (`page`, `close`). | P3.1 |
| D36 | Explorer Action 服务 | 第二个 Binder 服务 `ThreePaperPdfExplorerActionService` 实现 `explorer-action-api` 的 `IExplorerActionPlugin` (engine `explorer-action`, 只读信封 v2): 主动作由宿主 `PreviewerType.PDF` 的预览器规格直接启动查看器 (不经 Explorer Action), 溢出工具经目录声明 (合并所选, 拆分, 转图片, 加密, 解密, 提取文本), 多选时只对全为 PDF 的选择显示合并; 输出到源文件同目录, 文件名加操作后缀, 重名自动编号 (Q2 已决); 执行用与脚本相同的 `PdfEngine`, 进度显示在宿主的事务界面. | P3.3 |
| D37 | 工具箱界面 | Compose + Material 3 单 Activity + navigation: 首页 (最近文件, 工具入口), 向导页 (选文件 -> 选项 -> 执行 -> 结果, 可直接打开结果), 设置 (默认输出目录 `/sdcard/Documents/3-Paper PDF/`, 默认 dpi, 启动器图标四选项), 发行历史, 更新检查 (插件通用更新检查接口); 长任务走前台服务 + 通知进度; 文件选择用 `MANAGE_EXTERNAL_STORAGE` 下的自带文件浏览 (3-Maple Image 同款) 并支持系统文件选择器. | P4.1 |
| D38 | 错误模型 | 脚本侧 `PdfError` (`code`, `message`, `cause`), 码表见附录 C.3; 插件缺失 / 禁用 / 不兼容报 `PLUGIN_UNAVAILABLE` / `PLUGIN_DISABLED` / `PLUGIN_INCOMPATIBLE` (消息以插件中心本地化提示开头, `epub` 同款), `pdf.isAvailable()` 预探测. | P1.3 |
| D39 | 测试夹具 | JVM 测试的 PDF 夹具由 `PdfFixtures` (PDFBox) 在运行时生成 (单页文本, 多页带大纲, 加密, 纯图片 "扫描件", 含 JPEG 与 PNG 图片, 旋转页), 不入库; 真机回归脚本用的样例由 `.python/generate_fixtures.py` (纯 Python 手写最小 PDF 结构, 无依赖) 生成到 `samples/fixtures/` 入库 (总计数十 KB). | P0.3 |
| D40 | 文档与声明 | `AutoJs6-Documentation/api/pdf.md`, `pdfDocumentType.md`, `pdfViewSessionType.md` (版式照 `epub.md` / `epubBookType.md` / `epubReaderSessionType.md`), `runtimeApis` 加 Node / Bun 的 `pdf` 一节; d.ts, Ace 补全, Offline Docs 按 `epub` 同款补齐; 10 种语言只用半角标点. | P5.1 |
| D41 | 发布门与版本号 | 不等宿主公开: 所有条目通过即发 1.0.0 并登记官方索引, `REQUIRES_HOST_VERSION` 写含全部宿主条目的本地构建号 (3-Macaw Speech 先例), 插件中心对旧宿主显示不兼容. 宿主 6.8.0 已于 2026-11-03 公开后仍沿用 (Q8 已决): 不等宿主 6.8.1 公开, `REQUIRES_HOST_VERSION` 写含全部宿主条目的 6.8.1 本地构建号, 公开 6.8.0 (versionCode 6640) 的用户在插件中心看到不兼容, 直到 6.8.1 公开; 发布文件 `autojs6-plugin-three-paper-pdf-v{VERSION_NAME}-{CRC32}.apk`. | P5.4 |
| D42 | 许可与声明 | 源码 MPL-2.0; `THIRD_PARTY_NOTICES.md` 列 PDFBox-Android (Apache-2.0), Bouncy Castle (MIT 式许可, 许可全文附在声明里), 其余 AndroidX / Compose (Apache-2.0); PDFBox-Android 随包的数据文件 (LiberationSans 字体 OFL-1.1, Adobe glyphlist, CMap 等 8 项) 在声明里单列一表; README 的 "引擎" 节写明 PDF 处理无原生库与纯 Java 依赖 (Compose 的 graphics-path 例外, Q9). | P0.1 |

---

## 3. 范围与非目标

范围 (1.0.0):

- 脚本 API `pdf` 全集 (附录 B): 对象层 `PdfDocument`, 便捷层, 同步 + Async 两形态, 进度与取消, 查看器会话; 宿主侧 OCR 分支.
- 宿主契约 `pdf-api`, 绑定与能力声明, `PreviewerType.PDF` 与预览器降级对话框, 插件中心注册, 向导条目与默认启用, Node / Bun 桥接, 官方索引登记.
- 插件核心: 读取 (信息, 文本, 大纲, 渲染, 图片), 页面操作 (合并, 拆分, 抽页, 旋转, 删页, 重排), 生成 (图片, 文本, HTML), 加密 / 解密, 进度与取消.
- 查看器 (连续滚动, 缩放, 缩略图, 大纲, 搜索, 密码) 与 Explorer Action (主动作 + 溢出工具).
- 工具箱界面: 首页, 向导, 设置, 发行历史, 更新检查, Icon Studio 图标与四种启动器模式.
- 文档, d.ts, Ace, Offline Docs, README, changelog, 宿主样例脚本, 兼容矩阵, 1.0.0 发布.

非目标 (不做或排到 1.1.0 之后, 评估记录见附录 G):

- 表单, 水印 / 页码 / 页眉页脚, 可搜索 PDF, 压缩均为 1.1.0 (D20).
- 注释 (高亮, 批注, 手写), 数字签名与证书校验, PDF/A 校验与转换, 重排 (reflow) 阅读模式.
- 远程 URL 直接转 PDF, 云端转换服务, Office 文档转 PDF.
- 第三方原生渲染库 (pdfium, MuPDF) 与 `androidx.pdf` (D3).
- 工具箱向导生成等价脚本代码 (3-Slate Studio D11 同款) 不在 1.0.0.

---

## 4. 现状诊断

### 4.1 可直接复用的宿主能力

- 插件基础设施: `core/plugin/AidlPluginHost.kt` (绑定, `allowActivityStarts`), `plugin-api/common-plugin-api`, `plugin-api/epub-api` (Bundle 请求, `ParcelFileDescriptor` 输入, 两步启动阅读器, oneway 回调, `getInfo` 居首) 作契约模板, `plugin-api/explorer-action-api` (只读信封 v2, 目录声明), 插件中心与 `core/plugin/center/wizard/PluginInstallWizardCatalog.kt`, `core/plugin/center/PluginDefaultEnabledPolicy.kt`, 官方索引工作流.
- Explorer 预览链路: `util/FileUtils.kt` 的 `TYPE.PDF("pdf", TypeDataHolder.DOCUMENT)` (当前只能用其他应用打开) 与 `PreviewerType { MARKDOWN, HTML, EPUB }`, `ui/explorer/ExplorerDocumentPreviewerPluginUi.kt` 的 `specFor` / 引导安装对话框 / `openWithOtherApps`, `ExplorerPluginActionController` 的溢出工具分发.
- 脚本模块先例: `runtime/api/augment/epub/` (`Epub.kt`, `EpubBookNativeObject.kt`, `EpubCalls.kt`, `EpubJsErrors.kt`, `EpubPromises.kt`, `EpubReaderSession.kt`) 的同步 / Async 双形态, 句柄对象, 会话事件; `engine/RuntimeEpubBridge.kt` + `NodeBridgeProtocol.kt` (`NodeBridgeModules.EPUB`) 的 Node / Bun 桥接; `runtime/api/augment/ocr/` (`Ocr.kt`, Paddle / Rapid / MLKit 引擎) 供 D19 调用; `runtime/api/Mime.kt` 已有 `APPLICATION_PDF`.
- 相关插件: 3-Folio EPUB (阅读器会话, Explorer 预览器, 默认启用), 3-Prism Camera (SharedMemory + fd 回落的位图传输, 契约模块版式, CI AVD job), 3-Maple Image (Compose 工具箱, `MANAGE_EXTERNAL_STORAGE` 授权矩阵, Icon Studio 接入, 发布流程), 3-Stack Archive (Explorer Action 多选工具与宿主事务界面).

### 4.2 缺口 (需要新建或修改)

- 宿主: `plugin-api/pdf-api` 契约, `core/plugin/pdf/` (`PdfPluginHost`, `PdfBinders`, `PdfError`, `PdfErrorMapper`, `PdfViewerLauncher`, `PdfOcr`), Rhino `pdf` 模块 (`runtime/api/augment/pdf/`), `RuntimePdfBridge` + `NodeBridgeModules.PDF`, `TYPE.PDF` 改为可预览 + `PreviewerType.PDF` + 预览器规格, 向导条目, 默认启用, 能力声明, 协议文档, changelog, 样例脚本.
- 插件: 全部 (仓库刚建立, 只有 ROADMAP, `.gitattributes` 与图标源图).
- 文档四仓库: `pdf.md`, `pdfDocumentType.md`, `pdfViewSessionType.md`, `runtimeApis` 一节, d.ts, Ace, Offline Docs.

### 4.3 外部事实

- 系统 `PdfRenderer` (API 21+): 只读渲染, 需可 seek 的 fd, 不支持加密文档 (抛 `SecurityException`), 同一时刻只能打开一页, 不绘白底; API 35 新增 `Page.getTextContents` / `searchText` / 表单 API, `PdfRendererPreV` 在 API 30-34 需 S 扩展 13+, 覆盖不全, 1.0.0 不依赖 (D30). `PdfDocument` (API 19+) 用 Canvas 绘制生成 PDF, 文本不可提取为 Unicode 以外的编码问题极少, 字体嵌入由系统处理.
- PDFBox-Android 2.0.27.0 (Apache-2.0, 2023-02): 纯 Java, 需 `PDFBoxResourceLoader.init(context)`; 文本提取, 大纲, 合并 / 拆分, 图片提取与嵌入, `StandardProtectionPolicy` 加密 (口令加密只经平台 JCA 的 `Cipher` / `MessageDigest` / `SecureRandom`, 只有证书加密用到 BouncyCastle, Q4); 自带渲染器慢且字体支持弱, 只作加密文档的解密中转 (D30). 上游 PDFBox 2.0.x 仍在维护; Android 分支 2026-11-12 复核 Maven Central 最新仍是 2.0.27.0 (元数据最后更新 2023-01-02), Q1 已决锁定该版本.
- `androidx.pdf` 1.0.0-beta01 (2026-08): minSdk 28, 仅查看器组件, 不提供编辑; pdfium / MuPDF 系库为原生库且 MuPDF 为 AGPL; 均不采用 (D3).
- WebView `createPrintDocumentAdapter` (API 21+): `onLayout` / `onWrite` 的回调类构造器为隐藏 API, 同包壳类技巧被多个开源库使用但未被官方承诺, 需 spike (附录 D Q5); WebView 必须在主线程创建, 插件服务进程可用 Application 上下文离屏创建.
- Android 10+ 后台启动 Activity 受限: 宿主在后台时 `pdf.view` 可能被系统拦截, 报 `UI_REQUIRES_FOREGROUND` (D21); 宿主前台时两步启动与 EPUB 相同.

---

## 5. 目标架构

### 5.1 数据流: 读取与渲染 (脚本 -> 插件)

```text
脚本 pdf.open(path, { password }) / pdf.text(path, opts) / pdf.render(path, page, opts)
  -> 宿主 PdfModule: 解析路径 (相对脚本目录), 校验选项 (未知键 INVALID_ARGUMENT), 打开只读 fd
  -> PdfPluginHost (AidlPluginHost 子类) 绑定 ThreePaperPdfService
  -> IPdfPlugin.open(fd, options) -> IPdfDocument (插件进程持有 PDDocument + PdfRenderer, 加密文档先解密到缓存)
  -> IPdfDocument.getInfo / getOutline / getText(request) / render(request) / planImages / extractImage(id, fd)
     render: ARGB_8888 位图 -> SharedMemory (API 27+) 或 PNG 写进宿主临时 fd -> 宿主 ImageWrapper -> 脚本 Image
  -> 便捷层在同一次调用里 open -> 操作 -> close; 对象层由脚本 close() 或退出时由宿主统一关闭 (D10)
```

### 5.2 数据流: 页面操作, 生成与加密 (脚本 -> 插件)

```text
脚本 pdf.merge([a, b], out) / pdf.split(src, outDir, { ranges }) / pdf.fromImages(paths, out, opts) / pdf.encrypt(src, out, opts)
  -> 宿主 PdfModule: 打开输入只读 fd (多个), 输出文件数固定时创建输出并打开写 fd
  -> 输出文件数不定 (split, extractImages): IPdfDocument.planSplit / planImages 返回清单 -> 宿主按清单创建文件 -> 逐个传 fd
  -> IPdfPlugin.run(request, inputs[], output, progressCallback) -> opId; 进度 onProgress(opId, { percent, stage, current, total })
  -> 完成 onComplete(opId, result) -> 脚本返回 { path, pageCount, sizeBytes, warnings[] }
  -> 失败 / 取消: 插件截断输出 fd, 宿主删除未完成的输出文件, onError(opId, { code, message })
HTML 转 PDF: 插件主线程离屏 WebView -> 打印适配器 -> 写输出 fd (D8)
工具箱向导调用同一 PdfEngine, 以插件自己的存储权限直接读写路径 (D7)
```

### 5.3 数据流: 查看器

```text
脚本 pdf.view(path, { page, password, search })      Explorer 主动作 (PreviewerType.PDF)      启动器 / 工具箱结果页
  -> 宿主打开只读 fd -> IPdfPlugin.openViewer(fd, options, callback) -> IPdfViewerSession (令牌)
  -> 宿主以显式组件 + 令牌启动 ThreePaperPdfViewerActivity (两步启动, D29); 启动失败 -> UI_REQUIRES_FOREGROUND
  -> 查看器事件 page / close 经 IPdfViewerCallback 回宿主 -> PdfViewSession 在脚本线程派发
  -> session.goTo(page) -> IPdfViewerSession.goTo; session.close({ finish }) -> 断开或结束 Activity (D23)
Explorer 主动作与启动器入口不经脚本模块: 宿主直接按预览器规格启动 Activity 并传 content URI / fd
```

### 5.4 包结构 (插件)

```text
io.github.supermonster003.autojs6.plugin.three.paper.pdf
  ThreePaperPdfApplication, ThreePaperPdfService (IPdfPlugin), ThreePaperPdfInfoService, ThreePaperPdfExplorerActionService, ThreePaperPdfContract
  engine/      PdfEngine (门面), DocumentHandle (PDDocument + PdfRenderer + 解密缓存), DocumentRegistry (每宿主 token 上限 8), RenderPool (2 并发), BitmapTransport (SharedMemory / fd), TextExtractor, OutlineReader, ImageExtractor, PageOps (merge / split / extract / rotate / delete / reorder), Generators (FromImages, FromText, FromHtml), Crypto (encrypt / decrypt, provider 注册), Ops (opId, 进度, 取消)
  viewer/      ThreePaperPdfViewerActivity, PageRenderer (tile 缓存), ViewerScreen (Compose), Outline, Search, Thumbnails, PasswordDialog, SessionBridge (IPdfViewerSession 实现)
  explorer/    ExplorerActionCatalog, ExplorerToolRunner
  ui/          home, wizard/<tool>, settings, releases, update, launcher icon
  util/        Pdfbox 初始化, 临时文件, 页码范围解析, EXIF
```

### 5.5 身份派生表

| 项 | 值 |
| --- | --- |
| `{PROJECT_NAME}` | `AutoJs6-Plugin-Three-Paper-PDF` |
| `{ROOT_PROJECT_NAME}` | `autojs6-plugin-three-paper-pdf` |
| `{APP_NAME}` (不可翻译) | `3-Paper PDF` (阿拉伯语按 Three 系列双向隔离规则用 FSI / PDI 包裹) |
| `{APPLICATION_ID}` / namespace / Kotlin 包 | `io.github.supermonster003.autojs6.plugin.three.paper.pdf` |
| `{PLUGIN_ID}` / `{PLUGIN_ENGINE}` / `{PLUGIN_VARIANT}` | `three-paper-pdf` / `pdf` / `default` |
| 身份类 | `ThreePaperPdfContract`, `ThreePaperPdfService`, `ThreePaperPdfInfoService`, `ThreePaperPdfExplorerActionService`, `ThreePaperPdfViewerActivity`, `ThreePaperPdfApplication`, `Theme.ThreePaperPdf`, 测试类同名后缀 |
| 插件中心身份服务 | `ThreePaperPdfInfoService`, action `org.autojs.plugin.INFO`, category `three-paper-pdf` |
| 能力服务 | `ThreePaperPdfService`, action `org.autojs.plugin.PDF`, category `pdf`, 受 `org.autojs.permission.PLUGIN` 保护 |
| Explorer Action 服务 | `ThreePaperPdfExplorerActionService`, `explorer-action-api` 的 action 与 category, engine `explorer-action`, 同一权限 |
| 查看器 Activity | `ThreePaperPdfViewerActivity`, action `org.autojs.plugin.PDF_VIEWER_OPEN` (令牌 extra `org.autojs.plugin.pdf.extra.SESSION_TOKEN`), 另接受 `ACTION_VIEW` + `application/pdf` (启动器与其他应用) |
| `{CAPABILITY_API}` | `pdf-api` (宿主 `plugin-api/pdf-api`, AAR 复制到 `libs/pdf-api.aar`; 另需 `libs/common-plugin-api.aar` 与 `libs/explorer-action-api.aar`; 登记在 `locks/host-api-aars.lock`) |
| 宿主契约包 | `org.autojs.plugin.pdf.api` |
| 宿主桥接包 | `org.autojs.autojs.core.plugin.pdf` |
| 脚本 API | `pdf` (`$pdf` 同对象); Node / Bun 模块 `pdf` |
| `{REQUIRES_HOST_VERSION}` | 基线 6640 (公开 6.8.0 与宿主 `master` 6.8.1 开发线 `739ac2f96e` 的 `VERSION_BUILD` 相同, 宿主的 `VERSION_BUILD` 即 versionCode); P0.1 骨架写临时值 6641 (6640 不含 P1 的宿主提交, 取下一个构建号; 3-Chime Alarm T33 先例), P1.6 核对临时值, P5.4 回填为含全部宿主条目的 6.8.1 本地构建号 (D41, Q8) |
| 向导目录 | `PluginInstallWizardCatalog` `FILE_MANAGER` 分类, `official("three.paper.pdf")`, 回退标题 `Three Paper PDF`; `PluginDefaultEnabledPolicy` 默认启用 (D14) |
| `plugin_description` (zh-Hans) | `读取, 渲染, 合并, 拆分, 生成与加密 PDF 文件, 内置查看器与文件管理器入口` (句尾无点号, 不含 "AutoJs6"; P0.1 定稿, 10 语言见 `values*/strings.xml`) |
| GitHub 仓库描述 | `PDF reading, rendering, page editing, generation and encryption with a built-in viewer for AutoJs6 scripts` |
| 发布文件名 | `autojs6-plugin-three-paper-pdf-v{VERSION_NAME}-{CRC32}.apk` (universal 单包, D26) |
| 索引清单 | `release-manifests/io.github.supermonster003.autojs6.plugin.three.paper.pdf/<versionCode>.json` |
| 图标 | `.python/icons/three-paper-ic-launcher-light.png` / `-dark.png` (D24), Icon Studio 合成 (P0.2) |
| 权限 | `org.autojs.permission.PLUGIN`; 独立界面用 `MANAGE_EXTERNAL_STORAGE` (+ API 32 及以下的 `READ/WRITE_EXTERNAL_STORAGE`) (D7); `POST_NOTIFICATIONS` + `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` (工具箱长任务); `INTERNET` + `ACCESS_NETWORK_STATE` (更新检查); 脚本路径操作不需要插件自身的存储权限 |
| 许可证 | 源码 MPL-2.0; 依赖 Apache-2.0 (PDFBox-Android, AndroidX) 与 Bouncy Castle (MIT 式许可) (D42) |

---

## 6. 阶段总览

| 阶段 | 版本 | 主题 | 状态 |
| --- | --- | --- | --- |
| P0 | 1.0.0 | 仓库骨架: 构建链与身份, Icon Studio 图标, 测试与 CI | 完成 (paper-p0, 2026-11-12) |
| P1 | 宿主 6.8.1 | 契约 `pdf-api`, 宿主绑定与查看器启动, 脚本模块 `pdf`, OCR 分支, Node / Bun 桥接, Explorer 文件类型与注册 | 进行中 (P1.1 完成, P1.2 待真机绑定) |
| P2 | 1.0.0 | 插件核心: 服务与句柄, 读取, 渲染与位图传输, 页面操作, 生成, 加密, 设备矩阵 | 进行中 (P2.1 - P2.6 完成) |
| P3 | 1.0.0 | 查看器, `pdf.view` 会话, Explorer Action 服务 | 未开始 |
| P4 | 1.0.0 | 工具箱界面: 应用骨架, 向导, 设置与启动器模式 | 未开始 |
| P5 | 1.0.0 | 文档, 声明, 四仓库同步, 样例, 兼容矩阵, 发布与索引登记 | 未开始 |
| P6 | 1.1.0 | 表单, 水印 / 页码 / 页眉页脚, 可搜索 PDF, 压缩, 发布 | 未开始 |

依赖顺序:

```text
P0 (骨架)                                  P1 (宿主) 与 P0 并行: 不同仓库, P1.1 契约 AAR 产出后 P0.1 把它放进 libs/
 -> P2 (插件核心)                          依赖 P0.1, P0.3 与 P1.1 的契约常量; 真机脚本测试依赖 P1.3
     -> P3 (查看器与 Explorer Action)      P3.1 依赖 P2.3 (渲染) 与 P2.2 (搜索); P3.2 依赖 P1.3; P3.3 依赖 P1.6 与 P2.4-P2.6
         -> P4 (工具箱)                    依赖 P2 全部与 P3.1 (结果页打开查看器)
             -> P5 (文档, 发布)            依赖 P1-P4 全部通过
                 -> P6 (1.1.0)
```

可并行的批次建议 (调度会话按 D16 发卡片):

- 批次 1: P0.1 + P0.2 + P0.3 (插件骨架, 图标与 CI, 一个子会话); P1.1 + P1.2 (宿主契约与绑定, 一个子会话); 两者不同仓库可并行.
- 批次 2: P1.3 + P1.4 + P1.5 (宿主脚本模块, OCR, Node / Bun); P2.1 + P2.2 + P2.3 (插件服务, 读取, 渲染).
- 联调 (调度会话 2026-11-12 安排, 批次 2 两张卡都完成后单独发一张卡片, 可与批次 3 并行): P1.2 真机绑定 (`getInfo`), P1.3 附录 B.5 示例, P1.4 OCR, P1.5 Node 脚本的真机验收; 批次 2 的两张卡只做代码与单元 / 仪器测试. `samples/pdf-ocr-manual.js` 由联调卡片写 (要在真机跑过). P1.4 的 (文档) `pdf.md` OCR 一节与 P1.5 的 (文档) `runtimeApis` 一节并入 P5.1.
- 批次 3: P2.4 + P2.5 (页面操作, 生成); P2.6 (加密, 含 Q4 spike); P1.6 (Explorer 文件类型, 注册, 协议文档); 三者可并行.
- 批次 4: P3.1 + P3.2 (查看器与会话); P3.3 (Explorer Action 服务); P2.7 设备矩阵在 P2 全部完成后.
- 批次 5: P4.1 + P4.2 (应用骨架与向导).
- 批次 6: P5.1 + P5.2 (文档与声明) 与 P5.3 (README, 样例, 兼容矩阵) 并行; P5.4 发布.

发布门禁 (每个阶段的 "(发布)" 条目都指向本清单):

1. 插件 `./gradlew test connectedDebugAndroidTest` 通过 (API 33 AVD), 宿主涉及模块的单元测试通过.
2. 真机脚本回归: 附录 F 设备矩阵中的 Sony API 28 / 31 / 33, Xiaomi Pad API 35 四台通过, OnePlus API 35 (ColorOS) 至少跑一次; PDF 处理无原生库, 唯一的 Compose 原生库在构建时校验 16 KB 对齐 (Q9), 不需要 16 KB 设备.
3. 10 种语言资源与文档只用半角标点 (`COMMON_AGENTS.md` 规则), `prepush_check.py` 通过, `THIRD_PARTY_NOTICES.md` 与依赖一致.
4. 发布 APK 为 release 签名, 用宿主插件中心安装并跑 `samples/` 冒烟脚本; versionCode, 文件名 CRC32 与索引清单一致.
5. changelog 10 种语言同时写 (`.claude/rules/all-languages.md`, 2026-11-05 起): 子会话以 zh-Hans 为源一次写全并翻译 10 种语言, 运行生成脚本与 `--check`, 不留给维护者; 维护者审阅时可修改任何语言.

---

## P0: 仓库骨架

状态: 完成 (paper-p0, 2026-11-12). 设备: 无 (构建与单元测试); P0.3 的仪器测试用 API 33 AVD.

### P0.1 构建链与身份 (D26, D42)

- [x] (插件) 按 `AUTOJS6_PLUGIN_NEW_REPO_AGENTS.md` 建立 Gradle 工程 (`settings.gradle.kts`, `build-logic`, `gradle/libs.versions.toml`, `version.properties`, `app/`), 填入 5.5 节身份; `libs/common-plugin-api.aar`, `libs/explorer-action-api.aar` 与 `libs/pdf-api.aar` (P1.1 产出, 未产出前用 P1.1 的常量草案本地构建 AAR 顶替并在 `locks/host-api-aars.lock` 标注临时) 入库.
- [x] (插件) 依赖线: Compose BOM 2026.09.00 + Material 3, navigation 2.10.2, `pdfbox-android` 2.0.27.0 (Q1 已决锁定), PDF 处理无原生库 (Compose 的 graphics-path 例外, Q9); `./gradlew assembleDebug` 产出 universal APK; 检查依赖 AAR / JAR 内无 `.so`; `PDFBoxResourceLoader.init` 在 Application 里完成.
- [x] (插件) `ThreePaperPdfApplication`, `ThreePaperPdfInfoService`, `ThreePaperPdfService` 空实现 (返回 `getInfo` 与 `NOT_IMPLEMENTED`), `ThreePaperPdfExplorerActionService` 空目录, `ThreePaperPdfContract` (ID, engine, 契约版本), Manifest (权限, 服务, 查看器 Activity 的 `ACTION_VIEW` + `application/pdf` 过滤器, `MANAGE_EXTERNAL_STORAGE` 授权页), 10 种语言 `strings.xml` (`app_name` 不可翻译, `plugin_description` 定稿).
- [x] (插件) `LICENSE` (MPL-2.0), `README.md` 骨架 (含 "引擎与依赖" 节), `THIRD_PARTY_NOTICES.md` 生成脚本, `.gitattributes` (已入库), `.editorconfig`, `prepush_check.py` 接入, `AGENTS.md`.
- 验收: `./gradlew assembleDebug test` 通过; APK 安装到 API 33 AVD 后插件中心能识别 `three-paper-pdf` (身份服务) 并按 D14 默认启用 (宿主 P1.6 落地前显示未启用); 提交前 `VERSION_BUILD` = 提交数 + 1.
- 结果 (2026-11-12, paper-p0): 完成. E1: `./gradlew assembleDebug test` 通过, 单元测试 31 例 0 失败 (Manifest 契约, 运行时身份与常量, 10 语言字符串一致, 宿主 AAR 锁, 第三方声明, 标点); universal debug APK 21,225,165 B, versionCode 3; 运行时依赖锁 71 个产物 (`pdfbox-android` 2.0.27.0, Bouncy Castle `*-jdk15to18` 1.72), `THIRD_PARTY_NOTICES.md` 生成检查 `NOTICES_OK host=3 maven=71 data=8`; 原生库只有 4 个 ABI 的 `libandroidx.graphics.path.so` (arm64-v8a 10,096 B, armeabi-v7a 7,252 B, x86 9,284 B, x86_64 10,760 B), `verifyDebugNativePageAlignment` 16384 通过 (Q9). E2: `.python/device_plugin_center_check.py` 在 API 33 AVD (只读实例, 宿主 debug 6640 x86_64) 上 RESULT PASS: INFO / PDF / EXPLORER_ACTION 服务, WAKE, 查看器两个过滤器 (`PDF_VIEWER_OPEN` 与 VIEW `application/pdf`) 与启动器入口都解析到本插件; 插件中心列出 "3-Paper PDF 1.0.0 (3)", 开关为未启用 (宿主 P1.6 前的预期状态). `libs/pdf-api.aar` 已换入 P1.1 正式版 (宿主 `4e872a8741`, 契约版本 1, 40,584 B, sha256 `e6bdb589...`); 骨架的全部操作以 `IllegalStateException("UNSUPPORTED: NOT_IMPLEMENTED: <op>")` 拒绝 (契约只允许 IAE / ISE / SecurityException 跨 Binder), `pdfFeatures` 为空; 查看器 Activity 导出但不受权限保护, 会话令牌校验在 P3.2. `prepush_check.py` 已能识别本仓库 (构建号与生成文档检查通过, 仓库契约检查等 P0.2 的 mipmap). 10 种语言的 README / changelog / 插件说明由 `.python/generate_markdown.py --check` 校验 (`MARKDOWN_OK languages=10 artifacts=36`).

### P0.2 图标与启动器模式 (D24)

- [x] (插件) 用 Icon Studio (`D:/webstorm-projects/AutoJs6-Icon-Studio`, 内核 1.4.0) 从 `.python/icons/three-paper-ic-launcher-light.png` / `-dark.png` 生成全套资源 (`ic_plugin_center`, `ic_launcher`, `ic_launcher_system*`, monochrome, 四个 launcher alias, `.icons/recipe.json`, `.python/icon_studio_runtime.py` / `generate_icon_studio.py`), 视觉尺寸 V = 0.52, 安全圆断言通过.
- [x] (插件) 设置页的启动器图标四选项 (自适应自动 / 固定亮 / 固定暗 / 透明) 与规范提示文案 (可在 P4.1 的设置页落地, 本条只要求资源与组件 alias 就绪). P0.2 就绪的部分: 四个 alias (`AdaptiveAutoIconAlias` 默认启用, 其余三个默认停用, 均指向 `ui.home.HomeActivity`, 真实入口不再导出) 与 `ui.appearance.LauncherIcons` (切换, 快捷方式归属回滚, 混合状态修复); 设置页四选项与提示文案随 P4.1 落地.
- 验收: 工程测试中的图标尺寸 / 透明度 / 安全圆断言通过; API 33 AVD 与一台真机的启动器与插件中心亮暗主题下图标可辨.
- 结果 (2026-11-12, paper-p0): 完成. E1: 独立运行时 (`.python/icon_studio_runtime.py`, 与内核 1.4.0 的 `icon_studio/runtime.py` 相同) 按 `.icons/recipe.json` 生成 28 个资源 (14 张 PNG, 含 Manifest 的 application icon / roundIcon), `generate_icon_studio.py --check` 通过, 无错误无警告; 度量: V = 0.5216 (目标 0.52 +- 0.006), 列表安全圆半径 169.65 / 216 px, 启动器前景半径 113.59 / 132 px, 透明像素 75.3%, 亮 / 暗对比度 13.94 / 11.19. 单元测试 39 例 0 失败 (新增 `IconResourcesTest` 6 例, `LauncherIconStatePolicyTest`, Manifest 四 alias 契约); lint 新增的只有生成资源固有的 `IconDuplicates` 7 条与 `IconLauncherShape` 2 条提示 (规范第 7 节允许, 不写成零警告); debug APK 21,411,073 B (versionCode 5). E2: API 33 AVD (只读实例, 宿主 debug 6640) 上 `.python/device_plugin_center_check.py` RESULT PASS (启动器入口解析到 `.launcher.AdaptiveAutoIconAlias`), `.python/device_icon_check.py --center --drawer --aliases` RESULT PASS: 插件中心亮色为 `#FAFAFA` 圆底 + 深色图案, 暗色为 `#212121` 圆底 + 浅色图案 (插件未启用, 宿主按未启用状态调暗); 暗色下逐一启用四个 alias, 启动器分别显示亮底, 暗底, 暗底 (自动随系统), 透明 (Pixel 启动器给非自适应图标加白色外框, 暗色图案在白框上对比度低, 属启动器外框行为), 最后恢复为 Manifest 默认状态. 两点观察: 宿主插件中心的亮暗由宿主持久化的夜间标记决定, 只在宿主进程存活时随系统同步, 所以脚本在宿主前台时切换主题; Launcher3 的图标缓存不随 `cmd uimode night` 刷新 (与 3-Prism Camera P3 的记录相同), 自动模式在组件状态变化或重装后显示对应变体. 截图在会话 scratchpad, 不入库. E4 (2026-11-12 15:40): XQ-AT72 (API 31, 宿主 debug 6.8.1 / 6641, 插件 debug 包) 上维护者目视确认, 暗色与亮色主题下启动器 (默认自适应自动 alias) 与宿主插件中心 (未启用状态, 宿主调暗) 的图标都清晰可辨; 测后夜间模式恢复为测前的暗色, 插件已卸载.

### P0.3 测试与 CI (D15, D39)

- [x] (插件) 单元测试骨架 (页码范围解析, 选项校验, Bundle 编解码), `PdfFixtures` 运行时夹具生成器 (PDFBox: 单页文本, 多页带大纲, 加密, 纯图片, 含图片, 旋转页), 仪器测试骨架 (服务绑定, `getInfo`); `.python/generate_fixtures.py` 生成 `samples/fixtures/` 的最小样例并入库.
- [x] (插件) GitHub Actions: `build.yml` (assemble + test), `android-test.yml` (API 33 x86_64 AVD 冒烟仪器测试, 与 CI 同款 AVD 配方), `release.yml` (tag 触发, 产出 `autojs6-plugin-three-paper-pdf-v{VERSION_NAME}-{CRC32}.apk` 与索引清单草稿).
- 验收: 本地 `./gradlew test connectedDebugAndroidTest` 通过; 工作流文件经 `prepush_check.py` 检查 (远端运行待首次推送).
- 结果 (2026-11-12, paper-p0): 完成, 远端运行待首次推送. E1: JVM 单元测试 73 例 0 失败, 本条新增 34 例: `PageRangesTest` 7, `PdfOptionsTest` 7, `PdfAnswersTest` 6, `PdfFixturesTest` 7, `SampleFixturesTest` 7. 测试对象是新增的 `util/` 层: 页码范围, 选项与类型校验, 应答 Map, Bundle 编解码, 请求大小上限 65,536 B 报 `LIMIT_EXCEEDED`; 服务的拒绝路径改为复用该层. `PdfFixtures` 在运行时用 PDFBox 生成 6 种夹具 (单页文本, 多页带大纲, AES-128 加密, 纯图片扫描件, JPEG + 带软蒙版的 PNG, 0 / 90 / 180 / 270 度旋转页), 不入库. `.python/generate_fixtures.py` 纯 Python, 无依赖, 生成 `samples/fixtures/` 的 6 个样例与 `fixtures.json` 清单 (7 个文件共 48,069 B), 清单记录页数, 文本, 大纲, 搜索页, 加密参数, 图片放置与像素值; 加密样例是 RC4-128 (R3, P = -3900, 只允许打印), 与运行时的 AES 夹具互补; `--check` 校验可重现. JVM 上不能提取文本 (原因见 P2.2 的发现), 所以 JVM 测试只断言内容流里的 `Tj` 文本. E2: `./gradlew test connectedDebugAndroidTest` 在 API 33 AVD (只读实例) 上 16 例 0 失败:
  - `ThreePaperPdfContractTest` 7 例: WAKE, 启动器 alias 与查看器过滤器; 身份服务 `getInfo` 与 PDF 服务能力经 `MarshallingBinder` 强制走 Parcel; 全部操作报 `NOT_IMPLEMENTED: <op>`; Explorer 返回空目录.
  - `PdfBundlesDeviceTest` 4 例: 应答 Bundle 经 Parcel 往返, 请求 Bundle 校验, 超大请求.
  - `SampleFixturesDeviceTest` 5 例: PDFBox-Android 按清单提取 7 个样例的文本与搜索页; 系统解码器解出存储的 JPEG; `PdfRenderer` 打开全部未加密样例, 报告的是旋转后的显示尺寸 (90 / 270 度宽高互换); 扫描件与图片的像素探针.
  - 装宿主 debug 6640 与不装宿主 (与 CI 相同, 只读实例里卸载, 不留存) 各跑一遍都通过, Explorer 测试分别走 "已授权" 与 "拒绝" 两个分支.
- 结果 (续), 工作流: 三个工作流都从 3-Slate Studio 的同名文件改编.
  - `build.yml`: 依次跑仓库契约检查, 生成文档检查, 第三方声明检查, 样例 `--check`, `release_manifest.py` 编译检查; 然后跑 `verifyRuntimeDependencyLock`, JVM 测试, 构建 debug / androidTest / release APK 并跑 lint; 最后检查 release 包的原生库集合.
  - `android-test.yml`: API 33 google_apis x86_64, pixel_6, 4096 MB, 与本地 AVD_API_33 相同, 只是用 2 核; 测试前用 `hide_error_dialogs`, `stayon`, 解锁屏幕 (3-Maple Image 配方).
  - `release.yml`: tag `v{VERSION_NAME}` 触发, 签名 secrets 沿用其他官方插件的 4 个名称, 产出 APK, SHA256SUMS 与 `.python/release_manifest.py` 写的索引清单草稿.
  - 本地复现: `build.yml` 的 Gradle 命令通过; lint 0 错误, 15 个警告都是此前已有的 (生成图标 9 个, bcpkix 库自带的 `TrustAllX509TrustManager` 3 个, 版本提示 3 个); release 包 7,780,340 B, 只含 4 个不压缩的 graphics-path 库. `appendDigestToReleasedFiles` 收集到 `autojs6-plugin-three-paper-pdf-v1.0.0-4fb4bac9.apk`, `release_manifest.py` 输出 `MANIFEST_OK versionCode=5`, 清单的 `supportedAbis` 与 APK 的 `SUPPORTED_ABIS` 元数据一致 (四个 ABI); tag 与版本不符时报 `MANIFEST_ERROR`.
  - `prepush_check.py` 6 / 6 通过. 仓库还没有 remote, 远端运行等首次推送; 首次打 tag 前, 维护者要在仓库里配置 4 个签名 secrets.

---

## P1: 宿主契约, 脚本模块与注册

状态: 进行中 (2026-11-12 P1.1 完成, P1.2 - P1.5 单元测试部分完成, 真机待联调卡片; P1.6 待批次 3). 设备: 宿主单元测试与一台 API 33 真机 (脚本模块联调需 P2 的插件 debug 包).

### P1.1 契约模块 `plugin-api/pdf-api` (D27, D28)

- [x] (宿主) 新建 `plugin-api/pdf-api`: `PdfActions`, `PdfIds`, `PdfContract` (契约版本, Bundle 键, 上限常量: `MAX_OPEN_DOCUMENTS` 8, `MAX_RENDER_PIXELS` 16 M, `MAX_TEXT_CHARS_PER_CALL`, `MAX_OPTIONS_BYTES`, `VIEWER_CLAIM_TIMEOUT_MS`), `PdfCapabilityKeys`, `PdfErrorCodes`, AIDL `IPdfPlugin` / `IPdfDocument` / `IPdfViewerSession` / oneway `IPdfProgressCallback` / `IPdfViewerCallback` (附录 C), `PdfAidlOrderTest` 锁定方法顺序, 加入 `maven-central.gradle.kts` 发布配置. 证据 E1: 18 个文件 +1740 行, `PdfAidlOrderTest` 1 项 + `PdfContractTest` 9 项通过; 操作 id 为 int, 上限取值 `MAX_TEXT_CHARS_PER_CALL` 262,144 / `MAX_OPTIONS_BYTES` 65,536 / `VIEWER_CLAIM_TIMEOUT_MS` 60 s. 宿主本地提交 `4e872a8741`.
- [x] (宿主) 协议文档 `docs/dev/pdf-plugin-protocol-v1.md` (版式照 `epub-plugin-protocol-v1.md`, 沿用宿主 `<名称>-plugin-protocol-v1.md` 的命名): 操作名, Bundle 键, fd 所有权 (输入由宿主关闭, 插件须 dup; 输出写完由插件 flush 后宿主关闭), 两步输出清单, 进度与取消语义, 错误码; 末节列出与附录 C 草案的差异. 宿主本地提交 `4e872a8741` (P1.2 的 `17a617d594` 补 "Host Client" 一节).
- 验收: `./gradlew :plugin-api:pdf-api:assemble :plugin-api:pdf-api:test` 通过 (E1); release AAR 40,584 B (sha256 `e6bdb589...e90bb`) 放在共享笔记 `pdf-api-handoff.md` 旁, paper-p0 已换入插件工作区 `libs/pdf-api.aar` 并改写 `locks/host-api-aars.lock` (sha256 一致), 随 P0.1 骨架提交入库.

### P1.2 宿主绑定与查看器启动 (D29)

- [x] (宿主) `core/plugin/pdf/PdfPluginHost` (基于 `AidlPluginHost`, `preBindValidator` 校验 action / category / 权限 / 契约版本, 绑定 flags 同 `EpubPluginHost`), `PdfBinders` (句柄注册与脚本退出清理, 每脚本 8 个上限), `PdfError` / `PdfErrorMapper` (附录 C.3 -> 脚本错误), `PdfViewerLauncher` (两步启动, 前台判断 -> `UI_REQUIRES_FOREGROUND`), `PdfBitmapReceiver` (SharedMemory / fd 回落 -> `ImageWrapper`). 实现: 9 个源文件 (另有 `PdfBundles`, `PdfClients`: 文档客户端, 可取消的 `PdfOperation`, 查看器会话); 绑定 flags 在 EPUB 之上加 `allowActivityStarts` (D29 原文, 只在 API 34+ 生效, Q21 已先行); 脚本退出顺序 取消操作 -> 断开查看器 (`close(false)`, D23) -> 关闭文档, 预留中的槽位也计入 8 个上限; 位图 API 27+ 且插件声明 `render.sharedMemory` 时走 SharedMemory, 否则 PNG 文件 (至多 96 MiB, 写在 `cacheDir/pdf-render`, 1 h 清扫), 单张至多 16 M 像素, `toImage` 包装成 `ImageWrapper`; `PdfBinders` 挂到脚本运行时与 `RuntimeResourceScope` 登记留给 P1.3. 证据 E5: 绑定与 flags 对照宿主 `AidlPluginHost.kt` / `EpubPluginHost`. 宿主本地提交 `17a617d594`.
- [x] (宿主) 能力声明: `pdf.*` -> `files`, `pdf.view` -> `ui`, OCR 分支 -> `ocr`; 裸脚本声明与 MCP / Node 一致 (对照 `epub` 的声明处). 实现为 `PdfScriptCapabilities` 能力表: 读方法要 `files`, 写输出的方法另加 `files.write` (Q20 已先行), `view` 与查看器会话要 `files` + `ui`, `ocr: true | 'always'` 加 `ocr` (D19); 接入脚本运行时与 Node / Bun 的 `requiredFor` 留给 P1.3 / P1.5. 宿主本地提交 `17a617d594`.
- 验收: 宿主单元测试覆盖错误映射, 句柄上限, 位图接收 (两条路径); 用 P2.1 的插件 debug 包在 API 33 真机绑定成功并取回 `getInfo`. 单元测试部分完成 (E1): `:app:testAppDebugUnitTest` 选 PDF 7 个测试类 55 项 + `StaticReferenceGuardTest` 9 项, 共 64 项全部通过 (错误映射 8, 句柄上限与退出清理 10, 位图接收两条路径 12, 契约版本协商 6, 操作生命周期 5, 能力表 8, 查看器启动门 6); lint 未跑. 真机绑定 (2026-11-13, paper-p1-reg): AVD_API_33 只读实例, 插件 debug 包 e1b9e8c + debug 宿主 (`VERSION_BUILD` 6641), `PdfPluginBindingDeviceTest` 经真实 Binder 绑定通过: `pdf.isAvailable()` 为 true, `getInfo` 对 3 页样例返回 3 页 (契约 1); 未装插件时 `isAvailable()` 为 false; 脚本 `tools/paper-pdf/device_check.py`, 宿主本地提交 `c76f80f53a`.

### P1.3 脚本模块 `pdf` (D4, D10, D11, D38)

- [x] (宿主) `runtime/api/augment/pdf/`: `Pdf.kt` (便捷层 + `open` + `view` + `isAvailable`), `PdfDocumentNativeObject.kt` (对象层方法), `PdfCalls.kt` (同步 / Async 双形态, 进度回调, 可取消 Promise), `PdfJsErrors.kt` (`PdfError`), `PdfViewSession.kt` (事件 `page` / `close`, `goTo` / `close` / `state`), 停止脚本时取消与关闭. 实际落位: 模块层 `runtime/api/modules/pdf/` (照 `epub` 模块, 不用 `augment`; 以上 5 个文件加注册用的 `PdfModule.kt`) 与 Rhino / Node / Bun 共用的 `runtime/api/pdf/` (`PdfService` 每脚本一个并持有 `PdfBinders`, `PdfMethods` 18 个表方法的参数解析, `PdfScriptArguments`, `PdfScriptValues`, `PdfDocumentHandle`, `PdfRequest`, `PdfOutputPaths`). 便捷层 45 个导出: 18 个表方法, `render`, `open`, `view`, `isAvailable` 各带 `xxxAsync`, 加 `PdfError`; `PdfDocument` 12 个方法. 同步形态在 UI 线程上拒绝 (D4); 停止脚本由 `RuntimeResourceScope` 新阶段 `Stage.PDF` (TTS 之后, CAMERA 之前) 取消操作 -> 断开查看器 -> 关闭文档; `ScriptModuleLayer` 顺序末尾加 `pdf`. `text` 返回值形状 (Q22), `namePattern` 占位符 (Q23), `fromHtml` 判别 (Q24), `encrypt` 的 `permissions` 写法 (Q25) 按推荐先行. 宿主本地提交 `701fd99b51`.
- [x] (宿主) 输出路径规则: 便捷层输出参数可省略时默认 `/sdcard/Documents/3-Paper PDF/<源名>-<操作>-<时间戳>.pdf` (渲染图片默认 `.png`), 返回值带实际路径; 输出与输入同路径时先写临时文件再替换 (D32). 实现: `PdfOutputPaths`, 时间戳 `yyyyMMdd-HHmmss`, 重名加 `-1` / `-2`, 目录输出不带扩展名; 每个输出先写到最终名旁边的隐藏分段文件 (`.part`), 完成后改名, 同路径时替换; 多文件输出 (`split`, `extractImages`) 用 `namePattern` (`{name}` / `{index}` / `{page}`, 展开后重名报 `INVALID_ARGUMENT`). 宿主本地提交 `701fd99b51`.
- 验收: 单元测试覆盖选项序列化, 页码范围, 默认输出路径, 错误映射; 真机脚本 (附录 B.5 示例) 在 API 33 通过 (依赖 P2.1-P2.3). 单元测试部分完成 (E1): `:app:testAppDebugUnitTest` 选集里本条 6 个测试类 46 项全部通过 (选项序列化 `PdfScriptArgumentsTest` 15, 页码范围 `PdfPageRangesTest` 5, 默认输出路径 `PdfOutputPathsTest` 9, 返回值 `PdfScriptValuesTest` 7, 方法表 `PdfMethodsTest` 6, 错误映射 `PdfJsErrorsTest` 4), 改动过的 `RuntimeResourceScopeTest` 15, `ScriptModuleOrderTest` 7, `ScriptModuleInstallsTest` 2, `ScriptSurfaceGateTest` 2 也通过; `:app` 编译通过, lint 未跑. 真机待联调卡片.

### P1.4 OCR 分支 (D19)

- [x] (宿主) `core/plugin/pdf/PdfOcr`: `pdf.text(path, { ocr: true | 'always', ocrOptions })` 对无文本层的页 (提取结果为空白) 以 `ocrDpi` (默认 300, Q6 已决; 脚本可覆盖) 渲染后调用 `ocr` 模块的当前引擎, 结果按页合并进返回值并标 `source: 'ocr'`; 无可用引擎 `OCR_UNAVAILABLE`; 进度回调按页推进. 实现: `ocr: 'always'` 识别范围内每一页; 只在有页需要识别时才解析引擎 (文本层完整的文档不要求装 OCR 插件); 引擎按自动模式的 `ocr.recognizeText` 选择已装的 OCR 插件, `ocrOptions` 接受 `mode` / `engineId` / `engine` / `variant` / `profile`, 未知键为 `INVALID_ARGUMENT`; 进度阶段 `ocr`; 返回值的 `sources` 逐页标 `'text'` / `'ocr'` (Q22). 宿主本地提交 `701fd99b51`.
- [ ] (文档) `pdf.md` 的 OCR 一节与 "渲染 + `ocr.recognize`" 手动样例 (`samples/pdf-ocr-manual.js`). 文档站部分并入 P5.1, 样例由联调卡片写 (paper-p1-script 2026-11-12).
- 验收: 真机 (已装 Paddle 或 Rapid OCR 引擎的 API 33) 对 `samples/fixtures/scanned.pdf` 识别出预期文本; 未装引擎的设备报 `OCR_UNAVAILABLE`. 单元测试部分完成 (E1): `PdfOcrTest` 10 项通过 (假渲染器 / 假引擎: 空白页与 `'always'` 的选页, `ocrDpi` 覆盖, 按页合并与来源标记, 无引擎 `OCR_UNAVAILABLE`, 进度, `ocrOptions` 校验); lint 未跑. 真机待联调卡片.

### P1.5 Node / Bun 桥接 (D13)

- [x] (宿主) `engine/RuntimePdfBridge.kt` + `NodeBridgeModules.PDF = "pdf"` + `BunAutomationBridge` 对应分支: 方法与 Rhino 一致, 渲染返回 `{ path }` (默认写入脚本目录下 `.pdf-render/` 或指定 `output`) 或 `{ base64 }` (`encoding: 'base64'`), `view` 的事件经桥接事件流; 独立授权与 `epub` 相同. 实现: `RuntimeOperationClients` 的 `pdf` 族 (`open / start / poll / cancel / close`), 操作为 18 个表方法, `render`, `open`, `view`, `isAvailable`, `document.<方法>` 与 `view.goTo / state / close`, 参数解析与 Rhino 共用 `PdfMethods`; `render` 返回 `{ path, width, height, format, sizeBytes }`, base64 至多 16 MiB, 查看器事件积压 1024 条时以 `close` reason `overflow` 结束 (Q27); Node 请求按 `PdfScriptCapabilities.requiredFor` 声明能力 (`NodeBridgePermissionManifest`, 写文件的 `render` 加 `files.write`); Bun 授权 `pdf.control` (默认关, OCR 另要 `ocr.control`, provider 清单加 `files` / `files.write` / `ui`, Q26), 设置开关 `plugin_bun_pdf_control_grant` 与 11 个 values 目录的字符串. 宿主开发文档 `docs/dev/runtime-pdf-2026-11-12.md`. Node / Bun 插件仓库的 JavaScript 门面 (`autojs6:pdf` / `$autojs.pdf` / Bun `host.pdf`) 不在本条, 何时做见 Q28; api-coverage 矩阵 45 个 pdf 导出在 Node main / Worker / Bun 三列登记为 pending (135 格). 宿主本地提交 `701fd99b51`.
- [ ] (文档) `runtimeApis` 加 "Node.js / Bun / Worker 的 pdf" 一节; Node 桥协议文档追加 `pdf` 模块. 文档站部分并入 P5.1, 样例由联调卡片写; 宿主侧桥接协议已写在宿主开发文档 `docs/dev/runtime-pdf-2026-11-12.md` (`701fd99b51`, paper-p1-script 2026-11-12).
- 验收: 宿主 Node 桥单元测试覆盖方法表与编解码; 真机 Node 脚本读取信息, 渲染一页, 合并两个文件通过. 单元测试部分完成 (E1): `RuntimePdfBridgeTest` 7 项 (操作表对照 Rhino 方法表, 每个操作的能力声明, Bun 授权与 provider 能力, 渲染目标, 文档 / 会话 id, 参数 / 结果 / `PdfTexts` 的 JSON 编解码), `RuntimePluginFamilyGrantTest` 3 项, `BunAutomationPolicyTest` 21 项 (路由总数 367) 通过; lint 未跑. 真机待联调卡片 (Node / Bun 门面先要落地, Q28).

### P1.6 Explorer 文件类型, 注册, 协议文档与版本回填 (D6, D14, D41)

- [x] (宿主) `util/FileUtils.kt`: `TYPE.PDF` 改为 `TypeDataHolder.PDF_VIEWER` (图标字符与 EPUB 同风格, `IDENTITY_PREVIEWABLE`, `PreviewerType.PDF`); `ExplorerDocumentPreviewerPluginUi.specFor` 加 PDF 规格 (`THREE_PAPER_PDF_PACKAGE`, `plugin_three_paper_pdf_name` 10 语言, `application/pdf`), `maximumArchiveDocumentBytes` 给 PDF 独立上限; 未装插件时保持 "用其他应用打开" 回退. 实现: 图标 U+25A5; PDF 不直接启动 Activity (与表格不同), 点按 `.pdf` (含压缩包里的条目) 走 `ExplorerPluginActionController.launchDocumentPreviewerOrShowGuidance`, 启动插件 Explorer Action 服务的主打开动作; 未装 / 未启用 / 未授权时引导对话框给出 "插件中心" (定位到本插件) 与 "用其他应用打开" (`application/pdf`); `THREE_PAPER_PDF_PACKAGE` = `PdfIds.OFFICIAL_PACKAGE`; `plugin_three_paper_pdf_name` 写在 11 个 values 目录 (ar 带 FSI / PDI); 压缩包内上限 `MAX_ARCHIVE_PDF_BYTES` 256 MiB (Q40 已先行); PDF 只能预览, 不再有 "编辑" 按钮 (Q42 已先行). 宿主本地提交 `bd77e1e998`.
- [x] (宿主) `PluginInstallWizardCatalog` 加 `entry(official("three.paper.pdf"), "Three Paper PDF", FILE_MANAGER)`; `PluginDefaultEnabledPolicy` 加默认启用; 插件中心说明文案 10 语言. 实现: 向导条目排在 3-Folio EPUB 之后, 不进标准 / 精简预设; 默认启用无需改代码 (`PluginDefaultEnabledPolicy` 只对 Agent / MCP / YOLO 要求官方签名, 其他插件一律默认启用, 单测锁定); 插件中心发现 (`InstalledPluginRepository` 探测分支与 PDF 服务查询, `PluginCenterViewModel` 引擎表 `pdf`, `PluginCenterFragment`, 清单 `<queries>`); 说明文案来自官方索引条目 (P5.4 写 10 语言), 宿主只放后备名称 (Q41 已先行). 宿主本地提交 `289d77dcd1`.
- [x] (宿主) changelog 10 种语言同时写 (6.8.1 版本 key, 通俗文案, all-languages 规则), `docs/dev/` 协议文档定稿, 核对插件的 `REQUIRES_HOST_VERSION` 临时值 (P0.1 骨架为 6641, P5.4 回填). 实现: 6.8.1 功能两条 (`pdf` 模块, 也可写作 `$pdf`; 文件管理器打开 PDF), 分别在 xlsx 两条之后, 10 种语言, `generate_markdown.py --check` `MARKDOWN_OK`, `check_translations.py` `CHANGELOG_TRANSLATIONS_OK`; Node / Bun 与文档链接待落地后补写 (Q44 已先行); 协议文档加 "Host Registration And File Manager (P1.6)" 一节与 "Minimum host build"; 临时值核对: 插件 `requiredHostVersion`, `ThreePaperPdfContract.REQUIRED_HOST_VERSION`, 宿主 `PdfIds.REQUIRED_HOST_VERSION_CODE` 都是 6641, 一致; P5.4 回填值 6641 (宿主 `VERSION_BUILD` 即整个 versionCode, 公开 6.8.0 与 6.8.1 开发线都是 6640, 闹钟 / 相机 / Java Libraries 先例; 6.8.1 发布前另行提高 `VERSION_BUILD` 时改为发布构建号, Q43 已先行). 宿主本地提交 `c5406585d2` / `a5c61511b4`.
- 验收: 宿主 Explorer 点击 `.pdf` 文件: 已装插件时打开查看器 (依赖 P3.1), 未装时弹引导对话框; 向导页出现条目; 老宿主 (Redmi, 或公开 6.8.0) 安装插件显示不兼容. 结果: 单元测试 `DocumentPreviewerFileTypeTest` (`.pdf` 映射, 规格, 256 MiB 上限, 回退), `PluginCenterPdfRegistrationTest` (发现表, 默认启用, 向导条目, 清单, 公开 6.8.0 = 6640 < 6641 不兼容), `PluginInstallWizardCatalogTest` (55 条) 通过; AVD_API_33 只读实例 (debug 宿主 6641) 上 `tools/paper-pdf/device_check.py` 的仪器测试通过: 未装插件时点按 `.pdf` 弹引导对话框 (名称, 原因, "用其他应用打开" 与 "插件中心" 两个按钮, 插件中心定位到本插件), 向导 "文件管理器" 分类在 3-Folio EPUB 之后出现本插件行 (假索引条目), 已装插件时点按分派到插件的主打开动作 (假动作). "已装插件时打开查看器" 依赖 P3.1 / P3.3 的 Explorer Action 主打开动作, 本条未验证; 老宿主不兼容只有单测, 未在 Redmi 上装插件验证.

---

## P2: 插件核心

状态: 进行中 (paper-p2-core: P2.1 - P2.3 完成; paper-p2-ops: P2.4 完成). 设备: API 33 AVD (仪器测试) + Sony XQ-DQ72 API 33 (联调) + G8441 API 28 (SharedMemory 回落与低端性能).

### P2.1 服务, 句柄与任务模型 (D10, D11, D28)

- [x] (插件) `ThreePaperPdfService` 实现 `IPdfPlugin` (`getInfo`, `getCapabilities`, `open`, `run`, `cancel`, `openViewer`), `DocumentRegistry` (每个宿主进程最多 `MAX_DOCUMENTS_PER_HOST` = 32 个句柄, 每脚本 8 个由宿主句柄表执行; 空闲超时关闭, 宿主连接断开时全部关闭并清理临时文件), `Ops` (opId, 进度节流 250 ms (`PROGRESS_THROTTLE_MS`), 取消标志贯穿 PDFBox 循环; 默认无超时, 只靠 `cancel(opId)`, 宿主 Binder 死亡与脚本停止结束操作, Q7 已决), `RenderPool` (2 并发, 其余排队), 临时文件目录 `cache/pdf/<token>/`.
- [x] (插件) 契约版本协商与 `PdfCapabilityKeys` (`pdfFeatures` 只列端到端可用的取值 (`render.sharedMemory`, `generate.html`, `crypto.aes256`, `viewer`), 回传 `pdfMaxOpenDocuments`, `pdfMaxRenderPixels`, `pdfRenderConcurrency`, `pdfPdfboxVersion`; OCR 在宿主侧, 不是插件能力).
- 验收: 仪器测试: 绑定, `getInfo`, 打开夹具, 同一宿主第 33 个句柄 `LIMIT_EXCEEDED`, 取消长操作后输出被截断; token 死亡后临时目录为空.
- 按 P1.1 契约同步 (paper-p2-core 步骤 0, 2026-11-12): 原文 "每宿主 token 8 个句柄, 第 9 个 LIMIT_EXCEEDED" 与 "进度节流 100 ms" 改为契约的 `MAX_DOCUMENTS_PER_HOST` 32 (每脚本 `MAX_OPEN_DOCUMENTS` 8 由宿主执行) 与 `PROGRESS_THROTTLE_MS` 250; 能力键改为附录 C 的 `pdf*` 键. `open` 不带宿主 token, "token" 指调用方 (Binder 调用 uid 与 pid) 及其服务连接.
- 结果 (2026-11-12, paper-p2-core): 完成. 结构: `engine/` 层 `DocumentRegistry` (每个宿主进程 32 个句柄, 第 33 个 `LIMIT_EXCEEDED`; 每个句柄 dup 源描述符, 打开时即解析, 临时目录 `cache/pdf/<uid>-<pid>/<id>/`, 关闭时删除, 宿主目录随最后一个句柄删除), `PdfSession` (同文档调用串行, `close()` 使在途调用以 `SESSION_CLOSED` 结束, 资源由最后持锁者释放), `PdfLoader` (经 `Os.pread` 的定位读取源 + `ScratchFile`, 16 MiB 内存其余落临时目录), `Operations` (opId 进程内唯一, 进度节流 250 ms, 完成前 fsync 并关闭输出, 失败或取消时截断为 0 字节, 回调 Binder 死亡取消该宿主全部操作并关闭其文档), `RenderPool` (公平信号量 2 个许可, 排队中的渲染随文档关闭退出), `PdfEngine` (服务创建时清空 `cache/pdf/`, 每 60 s 释放空闲 5 分钟文档的解析状态, 附录 D Q10 已决: 2026-11-12 维护者选 (a), 保持现状); 服务最后一个绑定解除时关闭全部文档与操作 (宿主每个句柄与操作各持一个绑定). `service/PdfDocumentStub` 按契约应答 (`contractVersion` / `requestId` 回传, 应答超过 768 KiB 报 `LIMIT_EXCEEDED`); 本条尚未实现的方法与 `run` 的六个操作仍报 `UNSUPPORTED` + `NOT_IMPLEMENTED: <op>`, `pdfFeatures` 为空. 能力新增 `pdfMaxOpenDocuments` 32, `pdfMaxRenderPixels` 16,000,000, `pdfRenderConcurrency` 2, `pdfPdfboxVersion` 2.0.27.0 (`BuildConfig.PDFBOX_VERSION` 取自版本目录). E1: JVM 单元测试 85 例 0 失败, 本条新增 12 例 (`PdfSourceReadTest` 4, `ConcurrencyTest` 3, `PdfLoaderTest` 5), `PluginRuntimeInfoTest` 加 4 个能力断言; lint 0 问题. E2: AVD_API_33 只读实例 (emulator-5790) `am instrument` 27 例 0 失败, 本条新增 11 例: `PdfServiceDocumentsTest` 6 (绑定与能力, 打开样例后关闭再调用为 `SESSION_CLOSED` 且回传 `requestId`, 32 个句柄后第 33 个 `LIMIT_EXCEEDED`, `open` 的密码 / 损坏文件 / 缺版本 / 版本 99 / 超长密码 / 管道错误码, 最后一个绑定解除后宿主临时目录消失, `run` 未知操作), `PdfEngineDeviceTest` 5 (完成的操作刷新输出且只应答一次, 取消长操作后输出为 0 字节且进度间隔 >= 245 ms, 失败操作回传错误码, 空闲释放后句柄仍可用并重新解析, 关闭使在途调用以 `SESSION_CLOSED` 结束并删除临时目录); `ThreePaperPdfContractTest` 改为逐个核对未实现的方法.

### P2.2 读取: 信息, 文本, 大纲, 搜索 (D31)

- [x] (插件) `getInfo` (页数, 每页尺寸与旋转, 元数据, PDF 版本, 是否加密与权限位, 文件大小), `getOutline`, `getText` (页范围 + 分块, 保留换行, `layout: 'reading' | 'raw'`, 契约名, 原文 `simple`), `search` (关键字, 大小写与整词选项, 返回页码与命中矩形, 页上限).
- 验收: 单元测试对运行时夹具断言文本, 大纲层级, 搜索命中; 仪器测试经 Binder 取文本分块.
- P0.3 的发现 (paper-p0, 2026-11-12): (1) `PDFTextStripper` 不按位置排序时, 在转 90 / 270 度的页面上会在词中断行 (`rotated.pdf` 第 2 页得到 "R|o|tate 90", 第 4 页得到 "R|o|ta|te| 2|70"), `sortByPosition = true` 后五页都正确, `layout: 'simple'` 应按位置排序, `raw` 若保留内容流顺序要在文档里说明; (2) JVM 上 PDFBox-Android 的 AFM / CMap 字体资源要 `PDFBoxResourceLoader.init(Context)` 才能加载, 单元测试不能直接调用 `PDFTextStripper`, 文本与搜索的断言放仪器测试 (`SampleFixturesDeviceTest` 已按清单断言), JVM 测试只断言内容流里的 `Tj` 文本, 大纲与结构.
- 结果 (2026-11-12, paper-p2-core): 完成. 结构: 新增 `read/` 层: `DocumentInfoReader` (`getInfo`: 页数, 每页 CropBox 宽高与旋转, 信息字典元数据 (无标题时回落到 XMP `dc:title`), 日期为带数字时区的 ISO 8601, PDF 版本, 是否加密, 当前密码授予的权限位, 文件大小), `OutlineReader` (深度优先扁平化为 `title` / `page` / `level`, 页面目标支持显式目标, 命名目标与 `GoTo` 动作, 没有页面目标为 0, 损坏的环被截断, 最深 64 层), `PageSweep` (逐页 `PDFTextStripper`, 每 256 个字形检查取消与关闭), `TextReader` + `TextChunker` (`page` / `offset` 续读, `maxChars` 缺省与上限都是 `MAX_TEXT_CHARS_PER_CALL`, 每页保留换行并以一个换行结尾, 不截断代理对, 应答文本预算 700,000 字节), `SearchReader` (`KeywordMatcher`: 大小写与整词选项, 关键字里的空白匹配任意空白 (含换行), 命中不重叠; 命中矩形按行拆分, 坐标为显示页 (已旋转) 左上原点的点, 由字形原点, 前进量与字体描述的 ascent / descent 求得, 保留两位小数并截在页内; `context` 取命中前后各 40 个字符; `limit` 0 或缺省为 100, 上限 1000, `offset` 分页; 列表 JSON 预算 320,000 字符). `layout: 'reading'` 按位置排序, `raw` 保留内容流顺序 (转 90 / 270 度的页面会在词中断行, 文档须说明, 见上面的 P0.3 发现); 搜索总按阅读顺序. 不检查文本相关的权限位 (附录 D Q11, 已决: 2026-11-12 维护者选 (a), 保持现状). `ThreePaperPdfContract.IMPLEMENTED_OPS` = `info`, `outline`, `text`, `search`, 其余方法仍报 `UNSUPPORTED` + `NOT_IMPLEMENTED: <op>`. E1: JVM 单元测试 104 例 0 失败, 本条新增 19 例 (`DocumentReadersTest` 8: 文本 / 旋转 / 加密样例的信息与权限位, 缺省元数据与 XMP 标题, 日期时区, 大纲层级与 `GoTo` 目标, 空大纲与预算截断, 断环与关闭; `ReadingPartsTest` 11: 分块, 选项缺省与上限, 关键字匹配, 命中收集, 四种旋转下的显示矩形, 字形与上下文); lint 0 错误, 15 个警告都是此前已有的. E2: AVD_API_33 只读实例 (emulator-5790) `am instrument` 36 例 0 失败, 本条新增 `PdfServiceReadingTest` 9 例 (全部经 Binder: 信息与权限位, 大纲, 文本保留换行并回传 `requestId`, `pages: '2-5'` + `maxChars: 20` 的分块拼起来等于整段文本, reading / raw 在旋转页上的差异, 错误请求, 搜索的页码 / 选项 / 分页 / 跨行命中 / 关键字错误, 命中矩形在显示页上的位置 (`rotated.pdf` 五页, 误差 0.5 点), 用户密码打开时可读文本 (Q11)).

### P2.3 渲染与位图传输 (D9, D17, D30)

- [x] (插件) `render(request)`: 选项 `dpi` / `scale` / `width` / `height` 解析为目标尺寸 (16 M 像素上限), 白底或透明, `PdfRenderer` 渲染 (加密文档先解密到缓存), `BitmapTransport` 写 SharedMemory 或 PNG 到宿主 fd; `renderToFile` 直接编码 PNG / JPEG / WebP 到输出 fd.
- [x] (插件) 渲染池与同文档串行; 大页 (> 16 M) 的降采样建议写进错误消息.
- 验收: 仪器测试断言 144 dpi A4 尺寸, 透明选项, 16 M 上限; G8441 (API 28) 走 SharedMemory, API 24 AVD 走 fd 回落; 1 页 A4 文本页渲染耗时记录进附录 F 性能表.
- 结果 (2026-11-12, paper-p2-core): 完成. 结构: 新增 `render/` 层: `PageRendering` (`render` / `renderToFile`: 先检查请求并按显示页 (CropBox 截在 MediaBox 内, 再按 /Rotate 转, 与 `getInfo` / `search` 共用 `PageFrame`) 算出目标尺寸, 尺寸键至多一个, 缺省 144 dpi, 四舍五入; 超过 16,000,000 像素报 `LIMIT_EXCEEDED`, 消息给出不超限的 `dpi` / `scale` / `width` / `height` 上界; 之后才占渲染池名额 (2 个许可, 排队中的渲染随文档关闭退出), 同文档调用由会话串行; ARGB_8888 位图白底或透明, 应答就绪后回收), `PageRenderer` (会话资源: 系统 `PdfRenderer` 读源描述符的副本; 加密文档用打开时的密码另行解析, 去掉安全设置后存为文档临时目录的 `render-decrypted.pdf`, 关闭句柄或空闲释放 (Q10) 时删除; API 28 起用浮点变换把显示页映射到位图, 以下不传变换, 由渲染器自己铺满, 因为 Android 7 的渲染器把变换作用于未旋转, 未裁剪的页面, 旋转页会画歪 (本条在 API 24 仪器测试中发现并修正)), `BitmapTransport` (API 27+ 只读 `SharedMemory` 区域, `rowBytes = width * 4`, 插件在回复写出后关闭自己的引用; 回落 PNG 与 `renderToFile` 从输出的当前位置写, `CountingOutputStream` 计数并执行 `MAX_RENDER_FILE_BYTES` 与停止信号, 失败时输出截断为 0 字节). `render` 在 API 27+ 默认 `sharedMemory`; `transport: 'file'`, API 27 以下或区域创建失败时把 PNG 写进 `fallbackOutput` 并回传 `sizeBytes`. `renderToFile` 支持 `png` / `jpeg` / `webp` (`quality` 1-100, 缺省 90; `webp` 在 API 30+ 为有损), `jpeg` + `transparent: true` 报 `INVALID_ARGUMENT` (附录 D Q12, 已决: 2026-11-12 维护者选 (a), 保持现状). `ThreePaperPdfContract.IMPLEMENTED_OPS` 加 `render` / `renderToFile`, `pdfFeatures` 在 API 27+ 为 `[render.sharedMemory]`, 以下为空. E1: JVM 单元测试 108 例 0 失败, 本条新增 4 例 (`PdfOptionsTest` 降采样上界 1, `CountingOutputStreamTest` 3), `PluginRuntimeInfoTest` 改为核对已实现方法与按级别的 `pdfFeatures`; lint 0 错误, 15 个警告都是此前已有的. E2: `am instrument` 全套 48 例, 本条新增 `PdfServiceRenderingTest` 12 例 (全部经 Binder: 缺省 144 dpi A4 为 1190 x 1684 白底; 透明选项边缘 alpha 为 0; `file` 传输的 PNG 签名, `sizeBytes` 与尺寸; 四个尺寸键与互斥; 16 M 上限 (dpi 407 报 `LIMIT_EXCEEDED` 并建议 `dpi <= 406` / `width <= 3362` / `height <= 4758`, dpi 406 得 3355 x 4748); `rotated.pdf` 五页的显示尺寸与黑条位置; 加密文档的解密副本在关闭后删除; `renderToFile` 三种格式; 错误选项 (gif, quality 0 与 101, jpeg + 透明) 后输出为 0 字节; 页码, 传输与已关闭文档的错误码; 20 次渲染后描述符不增长 (API 33 起测试端先回收, 因为收到的 Bundle 惰性保留 Parcel 副本); 附录 F 耗时). 已执行: G8441 (API 28, SharedMemory) 48 例 0 失败; AVD_API_24 只读实例 (emulator-5794, fd 回落) 48 例 0 失败 (修正前 `rotatedPagesRenderAsDisplayed` 第 2 页失败); AVD_API_25 / AVD_API_27 只读实例 (emulator-5796 fd 回落 / emulator-5798 SharedMemory, 核对旧版渲染器) 各 48 例 0 失败; AVD_API_33 只读实例 (emulator-5790) 48 例 0 失败 (在渲染器修正之前的构建上执行, 修正只改 API 28 以下的分支). 未执行: XQ-DQ72 联调 (属 P2.7 与 P1 的真机脚本). 耗时见附录 F 性能表.

### P2.4 页面操作 (D32)

- [x] (插件) `merge` (多输入 fd, 大纲合并选项, 进度按文件), `planSplit` + `split` (按 `ranges` 或 `every: n` 页), `extract`, `delete`, `reorder`, `rotate` (90 的倍数, 页范围); 元数据保留与 `producer` 写入插件名.
- 验收: 单元测试覆盖范围解析与每种操作的页数 / 旋转断言; 仪器测试经 Binder 合并两个夹具并拆分回去.
- 结果 (2026-11-12, paper-p2-ops): 完成. 结构: 新增 `edit/` 层: `PageEditPlan` (把 `save` 请求按协议顺序换算成页面放置: 先 `deletePages`, 再在其余页中保留 `pages`, 再按 `order` 重排 (必须是保留页的排列), 最后按 `degrees` 旋转 `rotatePages` (缺省为全部保留页), 删光全部页报 `INVALID_ARGUMENT`; `split` 的 `ranges` (每段内升序) 或 `every: n`, 两者恰好一个, 超过 `MAX_SPLIT_OUTPUTS` 报 `LIMIT_EXCEEDED`; `planSplit` 清单 `{index, pages, pageCount}`), `PageEditor` (在为写出另行加载的副本上改写页面: 先把可继承的 `Resources` / `MediaBox` / `CropBox` / `Rotate` 复制到每页, 再把放置的页按新顺序挂到一个新的扁平页面树根下; 有页被移除时切断指向它们的大纲条目 (其下还有保留条目的留下但去掉目标), 命名目标, 链接目标, 打开动作, 结构树, 文章线程与页标签 (顺序改变时也删页标签); 大纲损坏时丢弃大纲并回报 `warnings`), `PdfMerger` (`PDFMergerUtility.appendDocument` 逐个追加; `outline: merge` 时每个输入一个折叠的顶层条目 (文档标题, 没有标题时 `Document <n>`, 指向其第一页), 其下放该输入自己的大纲, `none` 丢弃全部大纲; 元数据取第一个输入), `PdfWriting` (`producer` 写 `3-Paper PDF <版本>`, `modificationDate` 为写出时间, 其余元数据保留; 经 `CountingOutputStream` 写出并响应停止信号; 写出的副本不带加密, 所以只用用户密码打开的加密输入报 `PERMISSION_DENIED`, 附录 D Q30), `PageOperations` (`save` 与 `split` 在会话锁内为写出另行加载源文档 (不动已打开的解析状态), 未改动时原样另存; `split` 每段加载一次; `merge` 的目标文档用混合内存 (主存上限同打开, 其余落到操作的临时目录), 源文档保持打开到目标写完; 进度: `save` 处理 / 写出, `split` 按段, `merge` 按文件). `run` 由新的 `RunOperations` 处理: 先检查输入与请求 (预先拒绝的请求直接抛出, 未实现的操作仍是 `NOT_IMPLEMENTED: <op>`), 复制输入归操作所有, 每个操作在 `DocumentRegistry` 锁内建 `<root>/<uid>-<pid>/run-<id>/` 临时目录, 结束即删 (宿主目录随之清空). `split` / `save` 在文档锁内检查请求, 之后在操作线程上再次持锁执行, 文档 `close()` 先取消其上运行中的操作再关闭. `merge` 接受 1 个输入 (与宿主一致, 协议文字写 2 个起, 附录 D Q32); 每个输入的大纲条目标题见 Q31. `ThreePaperPdfContract.IMPLEMENTED_OPS` 加 `merge` / `planSplit` / `split` / `save`, 新增 `PRODUCER`. JVM 测试打开 `unitTests.isReturnDefaultValues` (PDFBox-Android 合并 AcroForm 时调用 `android.util.Log`, 其他插件仓库同款). E1: JVM 单元测试 131 例 0 失败, 本条新增 23 例 (`PageEditPlanTest` 11: 范围解析, `save` 各步骤顺序与拒绝, `order` 排列, 旋转, `split` 两种方式与上限, `planSplit` 清单, `merge` 请求检查; `PageOperationsTest` 12: 提取保留大纲与元数据并写 `producer`, 删除后无目标的条目, 旋转与重排, 未改动另存, 拆分后每段的大纲, 可继承属性, 命名目标与链接, 合并的大纲包装与 `none`, 加密输入 (用户密码 `PERMISSION_DENIED`, 所有者密码写出不加密的副本), 取消的写出报 `CANCELLED`), `PluginRuntimeInfoTest` 核对新的已实现方法与 `PRODUCER`; `lintDebug` 0 错误, 15 条警告与 P2.3 基线相同 (IconDuplicates 7, TrustAllX509TrustManager 3, GradleDependency 2, IconLauncherShape 2, AndroidGradlePluginVersion 1), 无新增. E2: 仪器测试全套 53 例, 本条新增 `PdfServicePageOperationsTest` 5 例 (全部经 Binder: `outline.pdf` (6 页) + `rotated.pdf` (5 页) 合并为 11 页, `sizeBytes` 等于文件长度, `producer` 与旋转逐页一致, 大纲有两个输入的条目; `planSplit` + `split` 按 `1-6` / `7-11` 拆回, 两段页数与旋转同原文件; `save` 提取 `2-4` 并按 `[4, 3, 2]` 重排, 旋转 90 度; 预先拒绝 (缺回调, 删光全部页, 页码越界, 拆分段数与输出数不符, 空输入合并) 抛异常; 用户密码合并报 `PERMISSION_DENIED` 且输出为 0 字节, 所有者密码合并 3 页, 用户密码打开的文档 `save` 抛 `SecurityException`; run 临时目录随操作删除; 100 页合并耗时). 已执行: G8441 (API 28) 52 例 0 失败 (耗时用例加入之前) 与耗时用例单跑; AVD_API_33 只读实例 (emulator-5850) 53 例 0 失败; AVD_API_24 只读实例 (emulator-5852) 53 例 0 失败. 未执行: 真机 API 31 / 33 / 35 (属 P2.7). 100 页合并耗时见附录 F. 已知限制: 拆分 / 提取不裁剪表单域 (被删页上的控件仍留在 AcroForm 中); XMP 元数据里的 producer 不更新; PDFBox 2.0 写经典 xref, 不写对象流, 输出可能比输入大; 长时间的 `save` / `split` 占着文档锁, 同一文档上的其他调用等它结束; 1000 个输入的合并瞬时最多持有 2000 个描述符 (宿主的与插件复制的).

### P2.5 生成: 图片, 文本, HTML (D8, D18, D22, D33)

- [x] (插件) `fromImages` (JPEG 直嵌, 其他转码, `pageSize` / `fit` / `margin` / `orientation`, EXIF 纠正, 进度按图片), `planImages` + `extractImage` (JPEG 原样, 其他 PNG), `fromText` (`PdfDocument` + `StaticLayout`, 自动分页, `fontPath`), `fromHtml` (主线程离屏 WebView + 打印适配器 + 同包壳类; spike 结论登记附录 D Q5, 失败路径报 `UNSUPPORTED`).
- 验收: 单元测试断言生成文件页数与页尺寸; 仪器测试 `fromHtml` 在 API 33 AVD 产出可被 `PdfRenderer` 打开的文件; 真机 API 28 / 31 / 33 / 35 各跑一次 `fromHtml`.
- 结果 (2026-11-13, paper-p2-ops): 完成 (真机 API 33 的 `fromHtml` 未执行, 见下). 结构: 新增 `generate/` 层: `PageLayout` (页面几何, 单位为点: `pageSize` 为 `fit` / `A4` / `Letter` / `[宽, 高]`, `margin`, `fromImages` 的 `fit` (`contain` / `cover`, `cover` 超出内容框时裁剪) 与 `orientation` (`auto` 按图片宽高比转页面); `fromText` / `fromHtml` 的 `fit` 用 A4 并回报警告, 附录 D Q33), `JpegInfo` (只读标记段: 尺寸, 分量, 精度, Adobe APP14 (CMYK 反相) 与 EXIF 方向), `ImagesToPdf` (每张图片一页, 按输入顺序, 进度按图片; JPEG 以 `DCTDecode` 原样嵌入, EXIF 方向由绘制矩阵实现, 不重新编码; 其他 `BitmapFactory` 能解码的图片按 `quality` (缺省 90) 转 JPEG, `quality: 'lossless'` 时 Flate 无损; 超过 `MAX_RENDER_PIXELS` 的图片按 2 的幂降采样解码并回报警告, 页面几何仍按原尺寸; 单张输入上限 64 MiB), `TextToPdf` (UTF-8 文本经 `StaticLayout` 排到系统 `PdfDocument` 页面, 按整行分页; 字体为可选的第二个输入 (TrueType / OpenType, 字体集合取第一个字面, 按魔数识别), 没有时用系统默认字体 (附录 D Q34); `fontSize`, `lineSpacing`, `margin`, `title`; 文本按至多 8192 个字符分块, 附近有换行时在换行后切, 否则在空格后切), `HtmlToPdf` (主线程离屏 WebView 载入 HTML, 打印适配器按 `pageSize` 与 `margin` 分页写到 run 目录的文件; 执行 JavaScript (附录 D Q35), 不加载远程内容 (`blockNetworkLoads`, 每个 http / https / ftp / ws 请求都以空内容应答并计入 `warnings`), 关闭 file / content URL, 拒绝离开该页的导航; `timeout` 覆盖整个转换, 超时报 `TIMEOUT`; 设备不允许的环节 (无 WebView, 隐藏 API 策略, 排版或写出失败) 报 `UNSUPPORTED`), `GeneratedDocuments` (写 `title` (`fromHtml` 取页面标题), `creationDate`, 经 `PdfWriting` 写 `producer` 与 `modificationDate`; `PdfDocument` 只能给整点, 打印适配器只能给密耳, 偏差在 1 点内的页面恢复为请求的精确尺寸, 内容自定尺寸 (CSS `@page`) 的页面不动), `InputBytes` (按绝对位置读整个输入, 超过上限在读取前报 `LIMIT_EXCEEDED`); 壳类 `android.print.PaperPrintCallbacks` (附录 D Q5 (a), R8 保留包名与类名; 可用时 `pdfFeatures` 声明 `generate.html`, `fromHtml` 在所有设备上都列入已实现方法). 新增 `read/PageImages` (`planImages` / `extractImage`: 页面内容流按首次绘制的顺序列出图片 XObject (每个流每页一次, 含表单内的, 至多 25 层) 与内联图片 (每次出现); 最后一个过滤器为 `DCTDecode` 的为 `jpeg` 并原样写出 (先解开其前的过滤器), 其余由 PDFBox 解码后无损编码为 `png`; `pages` / `offset` / `limit` 分页). `RunOperations` 接上 `fromImages` / `fromText` / `fromHtml`, `PdfDocumentStub` 接上 `planImages` / `extractImage`, `PdfOptions` 增加浮点, 长整数与原始值读取. `ThreePaperPdfContract.IMPLEMENTED_OPS` 加上述 5 个方法. E1: JVM 单元测试 146 例 0 失败, 本条新增 15 例 (`GenerationTest` 12: 页面尺寸与边距, 适配 / 转页 / 裁剪, JPEG 标记段与 EXIF, 八种方向矩阵, 降采样, `fromImages` 原样嵌入与每图一页 (页数与页尺寸), 三个操作的请求检查, 文本分块, 字体魔数; `PageImagesTest` 3: 列出顺序含内联与表单, 分页, 提前结束), `PluginRuntimeInfoTest` 核对新的已实现方法与 `generate.html`; `lintDebug` 0 错误, 15 条警告与 P2.3 基线相同, 无新增. E2: 仪器测试全套 58 例, 本条新增 `PdfServiceGenerationTest` 5 例 (全部经 Binder: `fromImages` 两张 JPEG 按 `fit` 得 300 x 200 / 120 x 160 点两页, `sizeBytes` 等于文件长度, 原样嵌入后 `extractImage` 取回的字节与输入相同, A4 + `orientation: auto` 转页, `lossless` 得 PNG; `fromText` 200 行在 A4 上至少 3 页, 首末行可提取, `title` 写入, 18 点 + Letter 页数更多, 错误选项 `INVALID_ARGUMENT`; `fromHtml` 120 段落至少 2 页, 一个远程资源被拦截并回报, 脚本写入的文字可提取, 标题取自页面, `PdfRenderer` 能打开并渲染出深色像素, 极短 `timeout` 报 `TIMEOUT`, 未声明 `generate.html` 的设备上断言 `UNSUPPORTED` (本次各设备都声明了, 未走到该分支); `planImages` / `extractImage` 对夹具 `images.pdf` 的 JPEG (2717 B, SHA-256 与夹具清单一致) 与带透明的 PNG, 越界 `index` 报 `INVALID_ARGUMENT`; 20 张图片耗时). 已执行: AVD_API_33 只读实例 (emulator-5850) 58 例 0 失败 (最终构建复跑一次); AVD_API_24 只读实例 (emulator-5852) 58 例 0 失败; G8441 (API 28) 58 例 0 失败; XQ-AT72 (API 31), Xiaomi Pad (API 35), AVD_API_37.1_16K 只读实例 (emulator-5854) 各跑 `PdfServiceGenerationTest` 5 例 0 失败. `fromHtml` 的逐级结果见附录 D Q5 (API 24 / 28 / 31 / 33 AVD / 35 / 37 均可用). 未执行: 真机 API 33 (XQ-DQ72): 远程 adb 服务器在安装 21 MB 的 debug 包时设备掉线, 留给 P2.7 (掉线后可能残留未装完的插件, P2.7 先查 `pm path`). 设备脚本 `.python/device_instrumented_run.py` (安装插件与测试包, 运行指定测试类, 只输出通过 / 失败与 `Paper*` 标签的关键数值, 结束时卸载或装回原版插件; 远程 adb 用 `ADB_SERVER_SOCKET`). 20 张图片耗时见附录 F. 已知限制: JPEG 2000 (`JPXDecode`) 图片的 `extractImage` 报 `UNSUPPORTED` (PDFBox-Android 不能解码); 无换行的超长段落每 8192 个字符多一个换行; `fromHtml` 的页数随设备的 WebView 版本不同 (AVD 自带旧版 5 页, 真机 6 页).

### P2.6 加密 (D12, D34)

- [x] (插件) spike: BouncyCastle provider 注册方式与系统 `BC` provider 的冲突 (附录 D Q4), 结论写进本条; `encrypt` (AES-256 / 128, 用户 / 所有者密码, 权限位), `decrypt`, 带密码 `open`, `PASSWORD_REQUIRED` / `WRONG_PASSWORD` 区分.
- 验收: 单元测试加密后用错误密码打开报 `WRONG_PASSWORD`, 正确密码权限位一致; 真机 API 28 与 35 各验证一次 (provider 行为随系统版本变化).
- 结果 (2026-11-13, paper-p2-ops): 完成. spike 结论: 采用新增的 Q4 (c), 不注册任何 provider (附录 D Q4). PDFBox 的口令加密 (标准安全处理器) 只经平台 JCA 的 `Cipher` (`AES/CBC/PKCS5Padding`, `AES/CBC/NoPadding`, `AES/ECB/NoPadding`, RC4 为 PDFBox 自带实现), `MessageDigest` (MD5, SHA-256 / 384 / 512) 与 `SecureRandom`, 不引用 BouncyCastle, 因此 (b) 无处可传; (a) 会让整个进程的 AES, SHA 与 X.509 改由插件打包的 BC 提供, 8 MiB SHA-256 慢约 2 - 30 倍, TLS 与 WebView 不受影响 (逐机数据见附录 D Q4). 结构: 新增 `edit/PdfEncryption`: `encryptRequest` 在产生 id 之前检查 (恰好 1 个输入; 两个密码都为空报 `INVALID_ARGUMENT`; `permissions` 为契约权限名数组, 未知名称报 `INVALID_ARGUMENT`, 缺省为全部授予; `keyLength` 只能是 128 / 256, 缺省 256, 设备缺 AES-256 时 256 报 `UNSUPPORTED`); `encrypt` 用 `password` 打开输入 (已加密的输入须用所有者密码, 否则 `PERMISSION_DENIED`; 原有加密字典整体替换), 所有者密码按附录 D Q36 (a), `StandardProtectionPolicy` + `AccessPermission` (8 个权限位与 `DocumentInfoReader.permissions` 互逆), 偏好 AES: 256 位写 V5 R6 (AESV3), 128 位写 V4 R4 (AESV2), 结果为 `pageCount`, `sizeBytes`, 有警告时加 `warnings`; `decrypt` 用 `password` 打开, 须为所有者 (否则 `PERMISSION_DENIED`, 与 Q30 同一检查), 去掉加密写出, 输入未加密时原样另存并回报警告. AES-256 可用性在首次使用时探测一次 (32 字节密钥的三种 AES 变换与 SHA-256 / 384 / 512), `pdfFeatures` 据此声明 `crypto.aes256` (按契约顺序排在 `generate.html` 之后). `PdfWriting` 拆出 `writeProtected` (按 `protect` 设置的策略加密写出) 与共用的写出过程. `RunOperations` 接上 `encrypt` / `decrypt`, `ThreePaperPdfContract.IMPLEMENTED_OPS` 加这两个方法 (至此 `run` 的操作全部实现, 未实现的只剩 `openViewer`). 带密码 `open` 与 `PASSWORD_REQUIRED` / `WRONG_PASSWORD` 的区分沿用 P2.1 的 `PdfLoader`, 本条用加密产物覆盖; 读取不强制权限位, 加密按给定权限写入 (Q11). `proguard-rules.pro` 去掉整体保留 BC 的两条 keep 规则, release 包 8,091,632 -> 7,272,432 B (R8 映射核对: `StandardSecurityHandler` 供反射调用的两个构造器仍在, BC 只剩证书加密处理器引用到的 167 个类). E1: JVM 单元测试 153 例 0 失败, 本条新增 7 例 (`PdfEncryptionTest`: 请求检查 (缺省值, 未知权限, 128 / 256 以外的长度, 无 AES-256 时 `UNSUPPORTED`, 输入个数) 与 `decrypt` 请求; AES-256 与 AES-128 加密后无密码报 `PASSWORD_REQUIRED`, 错误密码报 `WRONG_PASSWORD`, 用户密码的权限位恰为请求的 `[print, copy, fillForms]` (契约顺序), 所有者得到全部权限, V / R 与加密过滤器正确, 内容流与标题保留, `producer` 写插件名; 用所有者密码重新加密 AES-128 夹具: 只有用户密码时 `PERMISSION_DENIED`, 旧密码均为 `WRONG_PASSWORD`, 新权限生效, 内容流不变; Q36 的各情形; `decrypt` 需要所有者密码, 未加密输入回报警告; 探测与权限映射互逆), `PluginRuntimeInfoTest` 核对新的已实现方法与 `crypto.aes256` 的声明顺序; `lintDebug` 0 错误, 15 条警告与 P2.3 基线相同, 无新增. E2: 仪器测试全套 62 例, 本条新增 `PdfServiceCryptoTest` 4 例 (全部经 Binder: `outline.pdf` 按 256 / 128 位加密, `pageCount` 6, `sizeBytes` 等于文件长度, 无警告; `open` 无密码 `PASSWORD_REQUIRED`, 错误密码 `WRONG_PASSWORD`, 用户密码的 `permissions` 与请求一致, 所有者全部权限, 文本与原文件相同; 用户密码 `decrypt` 报 `PERMISSION_DENIED`, 所有者密码 `decrypt` 后 `encrypted` 为 false 且文本相同; 夹具 `encrypted.pdf` (RC4 128 位) 只能用所有者密码 重新加密, 之后旧用户密码为 `WRONG_PASSWORD`; 未加密输入 `decrypt` 回报 1 条警告; 无密码, `keyLength: 192`, 未知权限在启动前报 `INVALID_ARGUMENT`; 声明的 `crypto.aes256` 与设备探测一致; provider spike `PaperCryptoSpike`, 测完恢复 provider 列表并断言不变). 已执行: AVD_API_33 只读实例 (emulator-5850) 62 例 0 失败; G8441 (API 28), Xiaomi Pad (API 35, 安装无需确认), AVD_API_24 只读实例 (emulator-5852) 各跑 `PdfServiceCryptoTest` 4 例 0 失败, AES-256 在四个级别均可用. 未执行: G8441 与 API 24 AVD 的全套复跑 (本条对既有路径只改了 `PdfWriting` 的内部结构, 页面操作在 API 33 AVD 全套中通过); 其他真机属 P2.7. 已知限制: 给证书加密 (Adobe.PubSec, 契约之外) 的文档传密码时, PDFBox 在触及 BC 之前报解密材料不兼容, 映射为 `INVALID_PDF` (未改); 未给 `ownerPassword` 且收回权限时生成的所有者密码不返回, 之后无法解除 (Q36, 有警告); 加密输出用 PDFBox 2.0 写经典 xref; 协议文字的补充请求见共享笔记 `pdf-api-change-requests.md` CR-5.

### P2.7 设备矩阵与性能基线

- [ ] (测试) `scripts/device-regression.js` 覆盖附录 B 的每个方法 (读取, 渲染, 页面操作, 生成, 加密, 进度与取消), 只输出通过 / 失败与关键数值; 在附录 F 的设备上各跑一次, 回填性能基线表 (A4 文本页 144 dpi 渲染耗时, 100 页合并耗时, 20 张图片转 PDF 耗时).
- 验收: 四台 Sony / Xiaomi 设备通过, OnePlus (ColorOS) 至少一次; 失败项登记为条目或待决.

---

## P3: 查看器与 Explorer Action

状态: 未开始. 设备: API 33 AVD + Sony XQ-AT72 API 31 (界面) + Xiaomi Pad API 35 (大屏) + OnePlus API 35 (ColorOS 后台启动).

### P3.1 Compose 查看器 (D35)

- [ ] (插件) `ThreePaperPdfViewerActivity` + `ViewerScreen`: 连续纵向滚动, 双指缩放与 tile 重渲染, 页码指示与跳页, 缩略图栏, 大纲抽屉, 搜索与高亮, 密码对话框, 夜间模式 (Q3 已决: 反色渲染, 图片保留原色), 分享 / 用其他应用打开, 三个入口 (令牌, `ACTION_VIEW` content URI, 工具箱结果页) 共用.
- 验收: 仪器测试打开夹具并断言页数与跳页; 真机 API 31 / 35 的缩放与搜索手测 (E4); 100 页文档滚动无 ANR (API 28).

### P3.2 `pdf.view` 会话 (D21, D23)

- [ ] (插件) `openViewer` 返回 `IPdfViewerSession` (令牌, `goTo`, `close(finish)`, `getState`), 未在 `VIEWER_CLAIM_TIMEOUT_MS` 内被 Activity 认领则关闭; 事件 `page` / `close` 经 `IPdfViewerCallback`; 脚本退出后查看器留前台, 第二个会话替换第一个.
- [ ] (宿主) `PdfViewSession` 联调: `pdf.view(path, { page: 3, search: 'foo' })` 打开并定位; 宿主后台时报 `UI_REQUIRES_FOREGROUND`. 状态 (2026-11-13, paper-p3-host): 代码核对完成, 无缺口 (page / search 映射为 page / keyword, 插件调用前与 startActivity 前各查一次前台, 宿主 Activity 暂停即视为后台); 补单测 `PdfScriptArgumentsTest.viewOptionsAreCheckedBeforeTheViewerOpens` (宿主 2d194f5da3); 真机联调留批次 5.
- 验收: 真机脚本 (附录 B.5 查看器示例) 在 API 33 通过; OnePlus (ColorOS) 后台启动行为记录进附录 F.

### P3.3 Explorer Action 服务 (D6, D36)

- [ ] (插件) `ThreePaperPdfExplorerActionService` 目录声明 (合并所选, 拆分, 转图片, 加密, 解密, 提取文本), 执行走 `PdfEngine`, 输出到源文件同目录 (文件名加操作后缀, 重名自动编号; Q2 已决), 进度回宿主事务界面, 错误本地化.
- [x] (宿主) `ExplorerPluginActionController` 对 PDF 类型的溢出工具展示与多选合并条件 (依赖 P1.6). 结果 (2026-11-13, paper-p3-host): 通用匹配器已满足 (单个 PDF 的溢出工具按目录; 选择栏须全为 PDF, 同一目录, 至多 128); 补宿主规则 `ExplorerPdfActionPolicy`: 官方包的多目标选择栏动作至少 2 个目标 (Q80, 已按推荐先行); JVM 测试 `ExplorerPdfEntryPointsTest` 6 项 (宿主 f11d55ef34). 真机待下一批复验.
- 验收: 真机 Explorer 对单个与多选 PDF 执行每个工具, 结果文件出现在约定位置并可打开.

---

## P4: 工具箱界面

状态: 未开始. 设备: API 33 AVD + Xiaomi Pad API 35 (存储授权需手动) + Sony XQ-AT72 API 31.

### P4.1 应用骨架, 首页, 设置, 发行历史 (D7, D37)

- [ ] (插件) 单 Activity + navigation, 首页 (最近文件, 工具入口, 存储授权引导), 设置 (默认输出目录, 默认 dpi, 启动器图标四选项, 语言跟随系统), 发行历史, 更新检查, `MANAGE_EXTERNAL_STORAGE` 授权流程 (API 24-29 走运行时权限, API 30+ 走所有文件访问页).
- 验收: 仪器测试导航与设置持久化; 真机 API 31 / 35 的授权流程手测 (E4).

### P4.2 工具箱向导 (D5)

- [ ] (插件) 向导: 合并 (多选排序), 拆分 (范围 / 每 n 页), 转图片 (dpi, 格式, 页范围), 图片转 PDF (多选排序, 页面选项), 加密 / 解密, 提取文本 (另存 txt), 旋转 / 删页 / 重排 (缩略图拖拽); 长任务前台服务 + 通知进度; 结果页可打开查看器或分享.
- 验收: 每个向导在 API 33 AVD 走通一次; 真机 Xiaomi Pad 大屏布局与 Sony API 31 各手测一次.

---

## P5: 文档, 声明, 四仓库同步, 样例与发布

状态: 未开始. 设备: 发布门禁的四台 + OnePlus.

### P5.1 文档 (AutoJs6-Documentation) (D40)

- [ ] (文档) `api/pdf.md` (模块说明, 可用性, 同步 / Async, 路径与 fd 说明, 每个方法, OCR 一节, 错误码表), `api/pdfDocumentType.md`, `api/pdfViewSessionType.md`, `runtimeApis` 一节, 侧栏与索引登记, 文档站 changelog 10 语言 JSON 同时写 (all-languages 规则).
- 并入 (调度会话 2026-11-12): P1.4 的 (文档) `pdf.md` OCR 一节 (渲染 + `ocr.recognize` 的手动样例写法, 样例文件 `samples/pdf-ocr-manual.js` 由联调卡片提供) 与 P1.5 的 (文档) `runtimeApis` 的 "Node.js / Bun / Worker 的 pdf" 一节在本条完成.
- 验收: 文档站本地构建通过, 链接检查通过.

### P5.2 声明 (AutoJs6-TypeScript-Declarations, Ace Editor, Offline Docs)

- [ ] (文档) d.ts (`pdf` 模块, `PdfDocument`, `PdfViewSession`, `PdfError`, 选项与返回类型), Ace 补全片段, Offline Docs 打包清单.
- 验收: d.ts 编译检查通过; 宿主编辑器补全出现 `pdf.`.

### P5.3 README, changelog, 样例与兼容矩阵

- [ ] (插件) README (功能, 安装, 引擎与依赖, 权限说明, 脚本示例, 兼容矩阵), `CHANGELOG.md` 10 语言一次写全 (以 zh-Hans 为源翻译, all-languages 规则), `samples/` (读取, 渲染, 合并拆分, 图片转 PDF, 加密, 查看器, OCR 手动) 与宿主 `samples/` 同步.
- 验收: `prepush_check.py` 通过; 样例在 API 33 真机全部通过.

### P5.4 1.0.0 发布 (D41)

- [ ] (发布) 走第 6 节发布门禁 1-5; `REQUIRES_HOST_VERSION` 回填为含全部宿主条目的 6.8.1 本地构建号 (D41, Q8: 不等宿主 6.8.1 公开); tag `v1.0.0`, release 工作流产出 APK; (索引) 登记 `release-manifests/.../<versionCode>.json`; 宿主 changelog 与本文件阶段总览更新.
- 验收: 插件中心从索引安装 1.0.0 并默认启用; Explorer 与脚本冒烟通过.

---

## P6: 1.1.0 表单, 水印, 可搜索 PDF 与压缩 (D20)

状态: 未开始. 开工前由维护者确认 1.1.0 范围不变.

### P6.1 表单

- [ ] (插件 / 宿主) `pdf.forms(path)` 读取 AcroForm 字段, `pdf.fillForm(src, out, values, { flatten })`, 契约版本 2 新增方法; 文档与 d.ts.

### P6.2 水印, 页码, 页眉页脚

- [ ] (插件 / 宿主) `pdf.watermark(src, out, { text | image, opacity, rotation, position })`, `pdf.stamp(src, out, { pageNumbers, header, footer, font })`.

### P6.3 可搜索 PDF

- [ ] (宿主 / 插件) `pdf.makeSearchable(src, out, { ocrOptions })`: 宿主 OCR 结果 (文本与位置) 经契约写入不可见文本层 (PDFBox 渲染模式 3), 进度按页.

### P6.4 压缩

- [ ] (插件 / 宿主) `pdf.compress(src, out, { imageDpi, imageQuality, removeMetadata })`: 图片重采样与重编码, 对象流, 去冗余; 返回前后体积.

### P6.5 1.1.0 发布

- [ ] (发布) 发布门禁 1-5, 契约版本 2 的向后兼容测试 (1.0.0 宿主 + 1.1.0 插件, 反之亦然), 索引登记.

---

## 附录 A. 目录结构 (1.0.0 目标)

```text
AutoJs6-Plugin-Three-Paper-PDF/
  ROADMAP.md  README.md  CHANGELOG.md  LICENSE  THIRD_PARTY_NOTICES.md  AGENTS.md
  .gitattributes  .editorconfig  version.properties  settings.gradle.kts  build-logic/  gradle/
  libs/common-plugin-api.aar  libs/explorer-action-api.aar  libs/pdf-api.aar  locks/host-api-aars.lock
  .python/icons/three-paper-ic-launcher-{light,dark}.png  .python/generate_fixtures.py  .python/icon_studio_runtime.py  .icons/recipe.json
  app/src/main/java/io/github/supermonster003/autojs6/plugin/three/paper/pdf/   (5.4 节包结构)
  app/src/main/res/values*/strings.xml  (10 语言)
  app/src/test/  app/src/androidTest/
  samples/*.js  samples/fixtures/*.pdf  scripts/device-regression.js
  .github/workflows/{build,android-test,release}.yml
```

## 附录 B. 脚本 API 草案

路径参数相对脚本工作目录解析 (与 `files.path` 相同), 不接受 `content://`. 页码从 1 计; 页范围写法 `'1-3,5,8-'` 或数组. 每个方法都有 `xxxAsync` 形态, Async 形态另接受 `onProgress` 并返回可 `cancel()` 的 Promise.

### B.1 `pdf` 便捷层

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `pdf.isAvailable()` | `boolean` | 插件已装, 启用且兼容 |
| `pdf.info(path, { password })` | `PdfInfo` | 页数, 页尺寸, 元数据, 加密与权限 |
| `pdf.text(path, { pages, password, ocr, ocrOptions, layout })` | `string[]` (按页) | `ocr: true` 只对无文本层的页识别, `'always'` 全部识别 (D19) |
| `pdf.outline(path, { password })` | `PdfOutlineItem[]` | `{ title, page, level, children }` |
| `pdf.search(path, keyword, { pages, caseSensitive, wholeWord, password })` | `PdfSearchHit[]` | `{ page, rect, context }` |
| `pdf.render(path, page, { dpi, scale, width, height, transparent, password })` | `Image` | D17 默认 144 dpi |
| `pdf.renderToFile(path, page, output, { ...render 选项, format, quality })` | `{ path, width, height }` | PNG / JPEG / WebP |
| `pdf.images(path, { pages, password })` | `PdfImageInfo[]` | 清单 (不导出) |
| `pdf.extractImages(path, outputDir, { pages, password, namePattern })` | `{ paths[] }` | 两步清单导出 |
| `pdf.merge(inputs[], output, { outline, passwords })` | `PdfResult` | |
| `pdf.split(path, outputDir, { ranges | every, namePattern, password })` | `{ paths[] }` | |
| `pdf.extract(path, output, pages, { password })` | `PdfResult` | 抽页 |
| `pdf.delete(path, output, pages, { password })` | `PdfResult` | |
| `pdf.reorder(path, output, order[], { password })` | `PdfResult` | |
| `pdf.rotate(path, output, degrees, { pages, password })` | `PdfResult` | 90 的倍数 |
| `pdf.fromImages(paths[], output, { pageSize, fit, margin, orientation, quality })` | `PdfResult` | D18 |
| `pdf.fromText(text, output, { pageSize, margin, fontSize, fontPath, lineSpacing, title })` | `PdfResult` | D22 |
| `pdf.fromHtml(htmlOrPath, output, { pageSize, margin, baseUrl, timeout })` | `PdfResult` | D8, 不加载远程 URL |
| `pdf.encrypt(path, output, { userPassword, ownerPassword, permissions, keyLength })` | `PdfResult` | D12, 默认 AES-256 |
| `pdf.decrypt(path, output, { password })` | `PdfResult` | |
| `pdf.open(path, { password })` | `PdfDocument` | 对象层 |
| `pdf.view(path, { page, password, search })` | `PdfViewSession` | D21 |

### B.2 `PdfDocument` 对象

`info()`, `pageCount`, `pageSize(page)`, `text(pages?)`, `outline()`, `search(keyword, options)`, `render(page, options)`, `renderToFile(page, output, options)`, `images(pages?)`, `extractImages(outputDir, options)`, `save(output, { pages, order, rotate, delete })` (一次保存应用多项页面操作), `close()`; 每个方法同样有 `xxxAsync`. 文档在插件进程中保持打开直到 `close()` 或脚本退出; 每脚本最多 8 个 (D10).

### B.3 `PdfViewSession` 对象

`on('page', (e) => { e.page, e.pageCount })`, `on('close', (e) => { e.reason })`, `goTo(page)`, `state()` (`{ page, pageCount, zoom, token }`), `close({ finish })` (D23).

### B.4 返回对象与错误

- `PdfInfo`: `{ pageCount, pages: [{ width, height, rotation }], title, author, subject, keywords, creator, producer, creationDate, modificationDate, version, encrypted, permissions: { print, copy, modify, annotate, fillForms, extractForAccessibility, assemble, printHighQuality }, sizeBytes }`.
- `PdfResult`: `{ path, pageCount, sizeBytes, warnings: string[] }`.
- `PdfError`: `{ code, message, cause }`, 码表见附录 C.3; 进度对象 `{ opId, percent, stage, current, total }`.

### B.5 示例

```javascript
// 读取与渲染
let info = pdf.info('./book.pdf');
console.log(info.pageCount, info.title);
let pages = pdf.text('./scan.pdf', { ocr: true });
let img = pdf.render('./book.pdf', 1, { dpi: 200 });
images.save(img, './page-1.png');
img.recycle();

// 对象层: 一次打开多次操作
let doc = pdf.open('./book.pdf', { password: '1234' });
let hits = doc.search('AutoJs6');
doc.save('./book-first-3.pdf', { pages: '1-3', rotate: { pages: '2', degrees: 90 } });
doc.close();

// 合并与进度 (Async)
let task = pdf.mergeAsync(['./a.pdf', './b.pdf'], './ab.pdf', {
    onProgress: (p) => console.log(p.stage, p.percent),
});
task.then((r) => console.log(r.path, r.pageCount));

// 生成
pdf.fromImages(['./1.jpg', './2.png'], './album.pdf', { pageSize: 'A4', fit: 'contain', margin: 24 });
pdf.fromText(files.read('./notes.txt'), './notes.pdf', { fontSize: 14 });
pdf.fromHtml('<h1>Report</h1><p>Hello</p>', './report.pdf');

// 加密
pdf.encrypt('./report.pdf', './report-locked.pdf', { userPassword: 'open', ownerPassword: 'owner', permissions: { print: true, copy: false } });

// 查看器
let session = pdf.view('./book.pdf', { page: 5, search: 'AutoJs6' });
session.on('page', (e) => console.log('page', e.page));
session.on('close', () => console.log('closed'));
```

## 附录 C. 契约 (P1.1 已发布的 v1)

来源与优先级: 宿主 `plugin-api/pdf-api` (提交 4e872a8741, 即 `libs/pdf-api.aar`, 40,584 B, sha256 e6bdb589...e90bb) 与协议文档 `AutoJs6/docs/dev/pdf-plugin-protocol-v1.md` 为准, 本附录只摘录事实. 2026-11-12 由 paper-p2-core 按已发布契约同步, 替换立项时的草案 (草案与发布版的差异见协议文档末节 "Differences From The Roadmap Appendix C Draft": 去掉 `fontPath` / `html` 两个键, `limits.*` 改为独立能力键, `render.sharedMemory` 等改为 `pdfFeatures` 的取值, 新增应答键与上限). 契约 v1 在批次 2 冻结, 需要改契约时写进交接目录 `pdf-api-change-requests.md` 并登记待决. 2026-11-13 按 Q60 就地在 v1 加 `displayNames` (merge) 与 `javaScript` (fromHtml), 契约版本仍为 1: 宿主 465d7a141f, 新 AAR 40,670 B, sha256 0bb80366...ff169e94 (交接目录), 由 paper-p3-plugin 换入 `libs/`.

### C.1 `plugin-api/pdf-api`

```text
PdfActions:        INFO, SERVICE_ACTION "org.autojs.plugin.PDF", SERVICE_CATEGORY "pdf", PLUGIN_PERMISSION "org.autojs.permission.PLUGIN",
                   VIEWER_ACTIVITY_ACTION "org.autojs.plugin.PDF_VIEWER_OPEN", EXTRA_SESSION_TOKEN "org.autojs.plugin.pdf.extra.SESSION_TOKEN"
PdfIds:            PLUGIN_ID "three-paper-pdf", ENGINE "pdf", CATEGORY "pdf", VARIANT_DEFAULT "default", OFFICIAL_PACKAGE, REQUIRED_HOST_VERSION_CODE 6641
PdfContract:       CONTRACT_VERSION = MIN_CONTRACT_VERSION = MAX_CONTRACT_VERSION = 1
  通用键           contractVersion, requestId, op, errorCode, errorMessage, warnings
  打开             displayName, password, passwords / displayNames (merge 每个输入一个; displayNames 是宿主传的文件名, 作书签标题, Q31)
  渲染             page, dpi, scale, width, height, transparent, transport, sharedMemory, rowBytes, format, quality
  文本与搜索       pages, page, offset, maxChars, layout, keyword, caseSensitive, wholeWord, limit; 应答 texts, pageNumbers, hasMore, nextPage, nextOffset, items
  页面操作         ranges, every, order, degrees, rotatePages, deletePages, outline, index
  生成             pageSize, fit, margin, orientation, fontSize, lineSpacing, title, baseUrl, timeout, javaScript (fromHtml, 缺省 DEFAULT_JAVA_SCRIPT = true, Q35 / Q61)
  加密             userPassword, ownerPassword, permissions, keyLength
  getInfo 应答     pageCount, pageWidths, pageHeights (float[], 旋转前的 crop box, 单位 pt), pageRotations (int[]), title, author, subject, keywords,
                   creator, producer, creationDate, modificationDate (ISO 8601 带时区, 无则不带该键), pdfVersion, encrypted, permissions (String[]), sizeBytes
  进度与查看器     opId, percent, stage, current, total, token, reason, zoom, visible
  items 字段       FIELD_*: title, page, level, rects, context, index, width, height, format, pages, pageCount, sizeBytes
  取值集合         TRANSPORTS sharedMemory / file; RENDER_FORMATS png (默认) / jpeg / webp; quality 1-100 (默认 90), fromImages 另有 lossless;
                   LAYOUTS reading (默认, 按位置排序) / raw (内容流顺序); outline merge (默认) / none; pageSize fit (默认) / A4 / Letter;
                   fit contain / cover; orientation auto / portrait / landscape; keyLength 128 / 256 (默认 256);
                   PERMISSIONS print, copy, modify, annotate, fillForms, extractForAccessibility, assemble, printHighQuality;
                   STAGES opening / processing / writing; REASONS closed / replaced / timeout / finished;
                   FEATURES render.sharedMemory, generate.html, crypto.aes256, viewer
  上限             DEFAULT_DPI 144 (MIN_DPI 1, MAX_DPI 2400), MAX_RENDER_PIXELS 16,000,000, BYTES_PER_PIXEL 4, MAX_RENDER_FILE_BYTES 96 MiB,
                   MAX_OPEN_DOCUMENTS 8 (每脚本, 宿主执行), MAX_DOCUMENTS_PER_HOST 32 (每宿主进程, 插件执行),
                   MAX_TEXT_CHARS_PER_CALL 262,144, MIN / MAX_KEYWORD_LENGTH 1 / 256, DEFAULT_SEARCH_LIMIT 100, MAX_SEARCH_HITS 1000,
                   DEFAULT_PLAN_LIMIT 500, MAX_PLAN_ITEMS 1000, MAX_RUN_INPUTS 1000, MAX_SPLIT_OUTPUTS 1000, MAX_TEXT_INPUT_BYTES 16 MiB,
                   MAX_PASSWORD_LENGTH 127, MAX_OPTIONS_BYTES 64 KiB, MAX_ANSWER_BYTES 768 KiB, MAX_EVENT_BYTES 16 KiB,
                   MAX_ERROR_MESSAGE_BYTES 4 KiB, PROGRESS_THROTTLE_MS 250, VIEWER_CLAIM_TIMEOUT_MS 60 s; 没有默认操作超时 (Q7)
PdfCapabilityKeys: requiresHostVersion (Long), pdfContractVersion (Int), pdfMaxContractVersion (Int, 可选), pdfFeatures (String[], FEATURES 的子集),
                   pdfMaxOpenDocuments (Int, <= 32), pdfMaxRenderPixels (Long, <= 16,000,000), pdfRenderConcurrency (Int, D10: 2), pdfPdfboxVersion (String)
PdfOps:            ALL (见 C.2), RUN_OPS, CANCELLABLE, isRunOp(op)
PdfErrorCodes:     ALL, HOST_ONLY, encode(code, detail) -> "CODE: detail", decode(message); 取值见 C.3
AIDL:
  IPdfPlugin        getInfo(), getCapabilities(), open(source, options) -> IPdfDocument, run(request, inputs[], output, IPdfProgressCallback) -> int opId,
                    cancel(opId) -> boolean, openViewer(source, options, IPdfViewerCallback) -> IPdfViewerSession
  IPdfDocument      getInfo(), getOutline(), getText(request), search(request), render(request, fallbackOutput), renderToFile(request, output),
                    planImages(request), extractImage(request, output), planSplit(request), split(request, outputs[], callback) -> int opId,
                    save(request, output, callback) -> int opId, close()
  IPdfViewerSession getState(), goTo(page), close(finish)
  oneway IPdfProgressCallback  onProgress(opId, progress), onComplete(opId, result), onError(opId, error)
  oneway IPdfViewerCallback    onPage(token, event), onClosed(token, reason)
```

要点 (细节见协议文档同名各节):

- 约定: 宿主写的每个 Bundle 带协商后的 `contractVersion`, 插件的应答, 进度, 完成与事件 Bundle 带请求的版本; Bundle 应答要么带结果键, 要么带 `errorCode` + `errorMessage`; 不回 Bundle 的方法 (`open`, `run`, `cancel`, `openViewer`, `split`, `save`, `goTo`, 各 `close`) 跨 Binder 抛 `IllegalArgumentException` / `IllegalStateException` / `SecurityException`, 消息为 `CODE: detail`. 读者忽略不认识的键. 页码从 1 起, 页范围是 `1-3,5,8-` 形式的字符串; 尺寸与搜索矩形单位为 pt, 矩形 `[left, top, right, bottom]` 以显示方向 (旋转后) 的页面左上角为原点.
- 描述符: 输入是只读的普通文件, 宿主持有并在调用返回后关闭, 插件要保留的 (打开的文档, 查看器会话, 进行中的 `run`) 自行 dup. 输出是宿主新建的空文件: 同步方法 (`renderToFile`, `extractImage`, `render` 的回落) 应答前写完并 flush; 异步操作 (`run`, `split`, `save`) 在返回 opId 前 dup 输出, `onComplete` 前 flush 并关闭; `onError` (含 `CANCELLED`) 后宿主删除输出. 数量取决于文档的输出分两步: `planSplit` / `planImages` 返回清单, 宿主按清单建输出.
- 渲染: ARGB_8888 预乘 alpha, `Bitmap.copyPixelsToBuffer` 的字节布局, 默认白底, `transparent` 时透明. `transport` `sharedMemory` (默认, API 27+) 应答 `transport`, `sharedMemory` (只读 `SharedMemory`), `width`, `height`, `rowBytes` (>= width * 4), 插件写完应答即释放自己的引用; API 27 以下, 宿主要 `file`, 或建共享内存失败时, 写不超过 96 MiB 的 PNG 到 `fallbackOutput` (v1 总是有效), 应答 `transport` `file`, `width`, `height`, `sizeBytes`. 两个尺寸键为 `INVALID_ARGUMENT`, 超过像素上限为 `LIMIT_EXCEEDED`. `renderToFile` 应答 `width`, `height`, `format`, `sizeBytes`.
- 并发与关闭: 同一文档的调用在插件里串行, 不同文档并行, 渲染最多 `pdfRenderConcurrency` 个同时进行; 文档关闭时进行中的同步调用以 `SESSION_CLOSED` 结束, 并取消该文档的 `split` / `save`; `close` 幂等, 之后的调用为 `SESSION_CLOSED`. 插件为每个宿主进程最多保留 `pdfMaxOpenDocuments` 个打开的文档, 超出时 `open` 为 `LIMIT_EXCEEDED`; 每脚本 8 个由宿主句柄表执行.
- 进度与取消: opId > 0 且在插件进程内唯一, 预先拒绝的请求直接抛出而不返回 opId; 每个操作每 250 ms 至多一次 `onProgress` (`op`, `percent`, `stage`, `current`, `total`), 最后恰好一次 `onComplete` 或 `onError`; `cancel(opId)` 在操作仍在运行时返回 true, 操作以 `CANCELLED` 结束; 插件对收到的每个回调 linkToDeath, 回调 Binder 死亡时取消该宿主的全部操作.
- 版本协商: 宿主接受 `pdfContractVersion` 落在自身范围内的插件, 取 `pdfMaxContractVersion` (无则为基线) 与自身最大值的较小者写进每个请求; 后续版本只在接口末尾追加方法, 追加可选键与开放集合的取值.

### C.2 操作名 (`PdfOps`)

`ALL`: `info`, `outline`, `text`, `search`, `render`, `renderToFile`, `planImages`, `extractImage`, `merge`, `planSplit`, `split`, `save` (extract / delete / reorder / rotate 的组合, 键 `deletePages`, `pages`, `order`, `degrees`, `rotatePages`), `fromImages`, `fromText`, `fromHtml`, `encrypt`, `decrypt`, `openViewer`. `RUN_OPS` (经 `run` 的 `op` 键启动): `merge`, `fromImages`, `fromText`, `fromHtml`, `encrypt`, `decrypt`; `CANCELLABLE` = `RUN_OPS` + `split`, `save`. 插件不读脚本路径 (D7), 所以 `fromText` 的字体是第二个输入, `fromHtml` 的 HTML 是第一个输入, 都由宿主写入或打开.

### C.3 错误码

`PLUGIN_UNAVAILABLE`, `PLUGIN_DISABLED`, `PLUGIN_INCOMPATIBLE` (宿主侧), `INVALID_ARGUMENT`, `FILE_NOT_FOUND`, `INVALID_PDF`, `PASSWORD_REQUIRED`, `WRONG_PASSWORD`, `PERMISSION_DENIED` (文档权限位禁止的操作), `PAGE_OUT_OF_RANGE`, `LIMIT_EXCEEDED`, `SESSION_CLOSED`, `UI_REQUIRES_FOREGROUND`, `OCR_UNAVAILABLE`, `STORAGE_PERMISSION_DENIED` (仅独立界面), `CANCELLED`, `TIMEOUT`, `UNSUPPORTED`, `IO_ERROR`, `INTERNAL`.

## 附录 D. 待决事项

格式: 编号 | 事项 | 选项 (推荐项标 "(推荐)") | 拍板时点 | 状态.

| 编号 | 事项 | 选项 | 拍板时点 | 状态 |
| --- | --- | --- | --- | --- |
| Q1 | PDFBox-Android 版本 | (a) 2.0.27.0 (推荐, Maven Central 已验证, 2023-02) / (b) P0.1 时若 Android 分支已发更新版则用更新版 (需复核 BouncyCastle 传递依赖) | P0.1 | 已决 (2026-11-12 12:20, 维护者选 (a) 锁定 2.0.27.0; 当日复核 Maven Central 最新仍是 2.0.27.0, 元数据最后更新 2023-01-02) |
| Q2 | Explorer 溢出工具的输出位置 | (a) 与源文件同目录, 文件名加操作后缀, 重名自动编号 (推荐, 与宿主事务结果可见性一致) / (b) 统一写到 `/sdcard/Documents/3-Paper PDF/` | P3.3 | 已决 (2026-11-12 12:21, 维护者选 (a) 与源文件同目录) |
| Q3 | 查看器夜间模式 | (a) 做, 反色渲染并保留图片原色 (推荐, 成本低) / (b) 1.0.0 不做 | P3.1 | 已决 (2026-11-12 12:20, 维护者选 (a) 做) |
| Q4 | BouncyCastle provider 注册 | (c) 不注册任何 provider, 口令加密只用平台 JCA (推荐, 已先行; spike 新增的选项) / (a) `Security.insertProviderAt(BouncyCastleProvider, 1)` 并先移除同名系统 provider (立项时的推荐, PDFBox 文档做法) / (b) 不注册全局 provider, 只在加密调用处传入 provider 实例 | P2.6 spike 后 | 已决 (2026-11-13 08:58, 维护者选 (c) 不注册 provider, 保持现状; paper-p2-ops 先行; spike 结论: PDFBox 2.0.27 的口令加密 (标准安全处理器, R2 - R6) 只经平台 JCA 的 `Cipher` (`AES/CBC/PKCS5Padding`, `AES/CBC/NoPadding`, `AES/ECB/NoPadding`; RC4 为 PDFBox 自带实现), `MessageDigest` (MD5, SHA-256 / 384 / 512) 与 `SecureRandom`, 不引用 BC; 只有证书加密 `PublicKeySecurityHandler` (Adobe.PubSec, 契约之外) 经 `SecurityProvider` 用到 BC, 所以 (b) 对口令加密无处可传, 不适用. `PdfServiceCryptoTest#providerSpike` (标签 `PaperCryptoSpike`) 在 AVD API 24, G8441 (API 28), AVD API 33, Xiaomi Pad (API 35) 上: provider 顺序均为 AndroidNSSP, AndroidOpenSSL, CertPathProvider, AndroidKeyStoreBCWorkaround, BC (系统内置 `com.android.org.bouncycastle`, 第 5 位), HarmonyJSSE, AndroidKeyStore; (c) 下 AES, SHA, X.509 与 TLS 都由 AndroidOpenSSL (Conscrypt) 提供, AES-256 探测均通过; (a) 下 AES, SHA-256 / 512 与 X.509 (`CertificateFactory`) 全部改由插件打包的 BC 提供 (整个插件进程), TLS 与 `HttpsURLConnection` 默认 socket 工厂仍是 Conscrypt, WebView 走 Chromium 网络栈不经 JCA, 均不受影响; 代价是性能: 8 MiB SHA-256 G8441 8 -> 241 ms, Xiaomi Pad 4 -> 123 ms, AVD 33 8 -> 218 ms, AVD 24 44 -> 97 ms; 加密 `outline.pdf` AES-256 G8441 173 -> 229 ms, Xiaomi Pad 61 -> 118 ms, AVD 33 122 -> 237 ms, AVD 24 123 -> 177 ms (AES-128 基本持平). 采用 (c): 不注册 provider, R8 不再整体保留 BC (release 包 8,091,632 -> 7,272,432 B); D34 中 "需要 BouncyCastle provider" 一句以本行为准. 设备缺 AES-256 时不声明 `crypto.aes256`, `keyLength: 256` 报 `UNSUPPORTED` (协议文字请求见共享笔记 pdf-api-change-requests CR-5). 影响 P2.6 `PdfEncryption`, `proguard-rules.pro`; 改选返工: (a) Application 里注册约 10 行 + 恢复 BC 的 R8 keep 规则 (release 约 +0.8 MB) + 仪器测试改断言 provider 为 BC) |
| Q5 | WebView 打印适配器隐藏 API | (a) 同包壳类访问隐藏构造器, 失败回落 `UNSUPPORTED` (推荐, 已先行) / (b) 自写 HTML 排版到 `PdfDocument` (大, 只支持子集) | P2.5 spike 后 | 已决 (2026-11-13 08:58, 维护者选 (a) 同包壳类, 保持现状; paper-p2-ops 先行; spike 结论: (a) 在测到的每个级别都可用, 壳类 `android.print.PaperPrintCallbacks` 在 `android.print` 包内继承两个隐藏回调 (设备上为 `@hide public` 构造器; API 37 设备 + target 37 也未被隐藏 API 策略拦截), 同一 120 段测试页 (`PaperHtmlSpike`, `generate.html` 均声明): API 24 AVD 5 页 34802 B 1494 ms, API 28 G8441 6 页 13082 B 793 ms, API 31 XQ-AT72 6 页 18162 B 498 ms, API 33 AVD 5 页 17132 B 2574 ms (全套中 849 ms), API 35 Xiaomi Pad 6 页 38241 B 429 ms, API 37 16 KB AVD 6 页 32615 B 2354 ms; 页数随 WebView 版本不同 (AVD 自带旧版 5 页, 真机 6 页); 真机 API 33 (XQ-DQ72) 未执行: 远程 adb 在安装中掉线, 留给 P2.7; 访问失败时不声明 `generate.html`, `fromHtml` 报 `UNSUPPORTED`; 影响 P2.5 `HtmlToPdf`, 宿主 `pdf.fromHtml` 文档; 改选 (b) 返工: 新写 HTML 子集排版器, 约 1500 行以上, 不能执行脚本与 CSS 分页) |
| Q6 | OCR 渲染 dpi | (a) 300 dpi (推荐, 识别率优先) / (b) 沿用渲染默认 144 dpi (快) | P1.4 | 已决 (2026-11-12 12:20, 维护者选 (a) 300 dpi; 脚本可用 `ocrDpi` 覆盖) |
| Q7 | 操作超时 | (a) 默认无超时, 只靠取消与脚本停止 (推荐, 大文档合并耗时不可预估) / (b) 默认 120 s, `timeout` 选项可改 | P2.1 | 已决 (2026-11-12 12:20, 维护者选 (a) 默认无超时; P1.1 契约也不设默认操作超时) |
| Q8 | D41 发布门在宿主 6.8.0 已公开后的适用 | (a) 沿用 D41: 不等宿主 6.8.1 公开, 1.0.0 全部条目通过即发布并登记官方索引, `REQUIRES_HOST_VERSION` 写含全部宿主条目的 6.8.1 本地构建号, 公开 6.8.0 用户在插件中心看到不兼容直到 6.8.1 公开 (推荐) / (b) 等宿主 6.8.1 公开再发 | P5.4, P1.6 | 已决 (2026-11-12 12:21, 维护者选 (a) 沿用 D41) |
| Q9 | Compose 自带的原生库 `libandroidx.graphics.path.so` (`androidx.graphics:graphics-path`, 由 `ui-graphics` 传递, 四个 ABI 各 7-11 KB, 已 16 KB 对齐) 与 D3 / D26 "无原生库" 的字面冲突 | (a) 列入原生库白名单, universal APK 照常, `supportedAbis` 为空, 文档写 "PDF 处理无原生库, 唯一的原生库来自 Compose" (推荐, 已先行; 3-Slate Studio 等 Compose 插件同款) / (b) 用 `packaging.jniLibs.excludes` 排除该库 (API 34 以下 Compose 的路径迭代依赖它, 排除后可能 `UnsatisfiedLinkError`, 需在四台设备复核) | P0.1 | 已决 (2026-11-12 15:48, 维护者选 (a) 列入白名单, 保持现状; 由 paper-p0 先行实施) |
| Q10 | 文档句柄的空闲处理 (P2.1 原文 "空闲超时关闭") | (a) 空闲 5 分钟只释放解析状态 (PDFBox 对象, 以及 P2.3 的渲染器与解密副本), 句柄仍有效, 下一次调用重新解析源 (推荐, 已先行; 契约没有 "句柄被插件关闭" 的事件, 脚本长时间持有的句柄若被关闭, 之后的调用会意外得到 `SESSION_CLOSED`) / (b) 空闲超时直接关闭句柄并删除临时目录 (字面实现, 脚本须处理 `SESSION_CLOSED`) / (c) 不做空闲处理, 只靠 `close()`, 脚本停止与宿主连接断开 | P2.1 | 已决 (2026-11-12 19:17-19:21, 维护者选 A, 保持现状; 由 paper-p2-core 先行实施, 影响 P2.1 `DocumentRegistry.sweep` / `PdfSession.releaseIdle`, P2.3 解密副本的生命周期) |
| Q11 | `getText` / `search` 是否执行 copy (提取文本) 权限位 | (a) 不执行: 能打开的文档就能读文本与搜索, `getInfo` 照实回报权限位, 由脚本自行决定 (推荐, 已先行; 只有用户密码的文档常把 copy 关掉, 执行会让脚本读不了自己有密码的文件, PDFBox 本身也不强制) / (b) 没有 copy 权限时 `getText` / `search` 报 `PERMISSION_DENIED` (用所有者密码打开或权限含 copy 时照常) | P2.2 | 已决 (2026-11-12 19:17-19:21, 维护者选 A, 保持现状; 由 paper-p2-core 先行实施, 影响 P2.2 `TextReader` / `SearchReader`, P3.1 查看器的搜索; P2.6 `encrypt` 写入的权限位照实写) |
| Q12 | `renderToFile` 的 `jpeg` 遇到 `transparent: true` 怎么处理 | (a) 报 `INVALID_ARGUMENT`, 消息提示去掉 `transparent` 或改用 `png` / `webp` (推荐, 已先行; `jpeg` 没有 alpha 通道, 静默合成到白底会让脚本以为透明生效了) / (b) 忽略 `transparent`, 合成到白底后照常编码 | P2.3 | 已决 (2026-11-12 19:17-19:21, 维护者选 A, 保持现状; 由 paper-p2-core 先行实施, 影响 P2.3 `PageRendering.renderToFile`, 宿主 `pdf` 模块文档的 `renderToFile` 选项说明) |
| Q20 | `pdf.*` 写输出的方法要求的脚本能力 | (a) 读 `files`, 写输出另加 `files.write` (推荐, 已先行, 与 Node / MCP 的能力清单一致, `files` 不隐含 `files.write`) / (b) 只要 `files` (D29 字面 "files 读写") | P1.2 | 已决 (2026-11-12 15:48, 维护者选 A, 保持现状; 由 paper-p1-host 在宿主 P1.2 先行实施) |
| Q21 | `PdfPluginHost` 是否设 `allowActivityStarts` | (a) 设 (推荐, 已先行, D29 原文 "含 allowActivityStarts, 以便宿主后台时插件界面弹出授权页"; 只在 API 34+ 且宿主可见时由系统放行) / (b) 与 `EpubPluginHost` 完全相同, 不设 (EPUB 实际未设) | P1.2 | 已决 (2026-11-12 15:48, 维护者选 A, 保持现状; 由 paper-p1-host 在宿主 P1.2 先行实施) |
| Q22 | `pdf.text` / `PdfDocument.text` 的返回值形状 | (a) Rhino 返回字符串数组 (每页一项), 另带不可枚举的 `pages` (页码) 与 `sources` (`'text'` / `'ocr'`); Node / Bun 返回 `{ texts, pages, sources }` 三个平行数组 (推荐, 已先行; 不用 OCR 时脚本照常按数组用, OCR 来源逐页可查, JSON 可直接跨桥) / (b) 两端都返回按页对象数组 `[{ page, text, source }]` / (c) 只返回合并后的整段字符串 | P1.3 / P1.4 / P1.5 | 已决 (2026-11-12 19:17-19:21, 维护者选 A, 保持现状; 由 paper-p1-script 在宿主 701fd99b51 先行实施) |
| Q23 | 多文件输出的 `namePattern` 占位符 | (a) `{name}` (源名) / `{index}` (从 1 计) / `{page}` (页码), `split` 默认 `{name}-{index}`, `extractImages` 默认 `{name}-p{page}-{index}`, 展开后重名报 `INVALID_ARGUMENT` (推荐, 已先行) / (b) 只支持 `{index}` / (c) printf 风格 (`%d`) | P1.3 | 已决 (2026-11-12 19:17-19:21, 维护者选 A, 保持现状; 由 paper-p1-script 在宿主 701fd99b51 先行实施) |
| Q24 | `pdf.fromHtml(htmlOrPath)` 如何区分 HTML 文本与文件路径 | (a) 含 `<` 的字符串视为 HTML 本身, 其他视为 HTML 文件路径, URI 拒绝 (推荐, 已先行; 合法路径几乎不含 `<`) / (b) 增加选项 `{ html: true }` 显式指定 / (c) 拆成 `fromHtml(html)` 与 `fromHtmlFile(path)` 两个方法 | P1.3, P2.5 | 已决 (2026-11-12 19:17-19:21, 维护者选 A, 保持现状; 由 paper-p1-script 在宿主 701fd99b51 先行实施) |
| Q25 | `pdf.encrypt` 的 `permissions` 写法 | (a) 名称数组 (只授予列出的) 或布尔值对象 (未列出的保持授予, `false` 撤销) 都接受 (推荐, 已先行) / (b) 只接受名称数组 / (c) 只接受布尔值对象 | P1.3, P2.6 | 已决 (2026-11-12 19:17-19:21, 维护者选 A, 保持现状; 由 paper-p1-script 在宿主 701fd99b51 先行实施; 宿主把两种写法都换算成授予名称数组 `permissions` 发给插件) |
| Q26 | Bun 的 `pdf.control` 授权范围 | (a) `pdf.control` (默认关, 同 `epub.control`) 覆盖除 OCR 外的全部操作, `text` 带 `ocr: true` / `'always'` 时另要 `ocr.control`; 授予时 Bun provider 清单声明 `files` / `files.write` / `ui`, 不进 `nodeCapabilities` (flow 工具与打包应用不借此拿到文件能力) (推荐, 已先行) / (b) `pdf.control` 也覆盖 OCR / (c) 拆成 `pdf.read` 与 `pdf.write` 两个授权 | P1.5 | 已决 (2026-11-12 19:17-19:21, 维护者选 A, 保持现状; 由 paper-p1-script 在宿主 701fd99b51 先行实施) |
| Q27 | Node / Bun 下 `render` 与查看器事件的细节 | (a) `render` 默认把 PNG 写到脚本工作目录下 `.pdf-render/` 并返回 `{ path, width, height, format, sizeBytes }`, 写文件另要 `files.write`; `encoding: 'base64'` 返回 `{ base64, ... }`, PNG 超过 16 MiB 报 `LIMIT_EXCEEDED`; 查看器事件积压 1024 条未取时以 `close` reason `overflow` 结束会话, 查看器留给用户 (推荐, 已先行) / (b) base64 不设上限 / (c) `render` 一律返回 base64, 不写文件 | P1.5 | 已决 (2026-11-12 19:17-19:21, 维护者选 A, 保持现状; 由 paper-p1-script 在宿主 701fd99b51 先行实施) |
| Q28 | Node / Bun 插件仓库的 pdf 门面 (`autojs6:pdf` / `$autojs.pdf`, Bun `host.pdf`, `PdfError` / `PdfDocument` / `PdfViewSession` 包装, d.ts 与一致性测试) 何时做 | (a) 联调卡片之前单发一张 "Node / Bun pdf 门面" 卡片, 两个运行时仓库各一个本地提交 (推荐; 联调卡片的 Node 真机验收要用到门面) / (b) 并入联调卡片 / (c) 并入 P5.2 (d.ts / Ace) | 联调卡片之前 | 已决 (2026-11-12 19:17-19:21, 维护者选 A: 联调前单发 Node / Bun 门面卡片; 批次 3 的 paper-rt-facade 执行) |
| Q30 | 只用用户密码打开的加密输入能否 `save` / `split` / `merge` | (a) 不能, 报 `PERMISSION_DENIED`, 消息提示用所有者密码打开 (推荐, 已先行; 写出的副本不带加密, 只凭用户密码就能得到去掉限制的副本等于绕过所有者设的权限位, 与 `decrypt` 要求所有者密码一致) / (b) 允许, 写出时用同一用户密码与原权限位重新加密 (所有者密码不可知, 只能另生成, 原所有者从此打不开限制) / (c) 允许并写出不加密的副本, 不检查 | P2.4 | 已决 (2026-11-13 08:58, 维护者选 (a) 不能, 报 `PERMISSION_DENIED`, 保持现状; paper-p2-ops 先行; 影响 P2.4 `PdfWriting.checkWritable`, P3.3 Explorer 溢出工具的错误提示; 改选返工: (b) 约 40 行 + 2 个测试, (c) 删检查约 5 行并改 3 个测试) |
| Q31 | `merge` 时每个输入的大纲条目叫什么 (协议只写 "每个输入一个条目") | (a) 输入的文档标题, 没有标题时 `Document <n>` (n 从 1 计) (推荐, 已先行; 契约 v1 不变) / (b) 契约 v2 增加 `displayNames` (每个输入的显示名, 宿主传文件名), 没有时退回 (a) | P2.4 | 已决 (2026-11-13 08:58, 维护者选 (b) 契约加 `displayNames` (非推荐项, 要返工); 按 Q60 就地改契约 v1, 契约版本仍为 1, 不写协商分支; 宿主把每个输入的文件名传给插件作大纲标题, 没有时退回文档标题, 再退回 `Document <n>`; 批次 4 由 paper-p3-host (契约与宿主) 与 paper-p3-plugin (`PdfMerger`) 落实; 原先行 (a) 由 paper-p2-ops 实施; 影响 P2.4 `PdfMerger`, 宿主 `pdf.merge` 文档; (b) 是契约改动, 已写进交接目录 `pdf-api-change-requests.md`; 改选返工: (b) 宿主与插件各约 20 行 + 契约版本 2 与协商测试) |
| Q32 | `merge` 只给 1 个输入 (协议表写 "2 to MAX_RUN_INPUTS", 宿主脚本模块允许 1 个) | (a) 插件接受 1 个输入 (相当于规整地另存一份), 协议表改为 "1 to MAX_RUN_INPUTS" (推荐, 已先行; 与宿主现状一致, 无代码改动) / (b) 宿主与插件都拒绝 1 个输入, 报 `INVALID_ARGUMENT` | P2.4 | 已决 (2026-11-13 08:58, 维护者选 (a) 接受 1 个输入, 保持现状; paper-p2-ops 先行; 影响 P2.4 `PageOperations.mergeRequest`, 宿主协议文档第 174 行 (协议文字改动已写进 `pdf-api-change-requests.md`); 改选返工: (b) 插件 1 行 + 1 个测试, 宿主约 3 行 + 1 个测试) |
| Q33 | `fromText` / `fromHtml` 的 `pageSize` 为 `'fit'` 怎么处理 (`fit` 只对图片有意义, 这两个操作默认 A4) | (a) 用 A4, 并在 `warnings` 回报 `fromText has no image to fit; pageSize "fit" used A4` (推荐, 已先行; 脚本照搬 `fromImages` 的选项时不至于失败, 警告说明了实际尺寸) / (b) 报 `INVALID_ARGUMENT` | P2.5 | 已决 (2026-11-13 08:58, 维护者选 (a) 用 A4 并警告, 保持现状; paper-p2-ops 先行; 影响 P2.5 `PageLayout.documentPageSize`, 宿主 `pdf.fromText` / `pdf.fromHtml` 文档的 `pageSize` 说明; 改选返工: (b) 约 3 行 + 2 个测试) |
| Q34 | `fromText` 没有字体输入时用什么字体 (协议表写 "the bundled font", D22 写 "系统字体") | (a) 系统默认字体 `Typeface.DEFAULT` (中日韩等文字经系统字体回退显示), 协议文字改为 "the system default font" (推荐, 已先行; 与 D22 一致, APK 不增大, 只改协议文字, 已写进交接目录 `pdf-api-change-requests.md` CR-4) / (b) 插件打包一款字体 (例如 Noto Sans 拉丁子集约 0.5 MB, 含中日韩约 16 MB 以上) 作为默认字体, 协议不变 | P2.5 | 已决 (2026-11-13 08:58, 维护者选 (a) 系统默认字体, 保持现状; paper-p2-ops 先行; 影响 P2.5 `TextToPdf`, 宿主协议文档第 176 行, 宿主 `pdf.fromText` 文档; 改选返工: (b) 约 10 行 + 字体资产 + `THIRD_PARTY_NOTICES` 条目 + 10 语言 README 体积说明, APK 增大如上) |
| Q35 | `fromHtml` 是否执行 HTML 中的 JavaScript (D8 只规定不加载远程 URL) | (a) 执行 (推荐, 已先行; 用脚本生成内容的页面与浏览器里看到的一致; 远程请求一律空应答并计入 `warnings`, file / content URL 关闭, 拒绝导航离开, 死循环可用 `timeout` 终止, 插件进程无宿主权限) / (b) 不执行, 只排版静态 HTML / (c) 契约 v2 增加选项 `javaScript` (默认按 (a) 或 (b)) | P2.5 | 已决 (2026-11-13 08:58, 维护者选 (c) 契约加选项 `javaScript` (非推荐项, 要返工); 按 Q60 就地改契约 v1, 按 Q61 缺省 true (执行脚本, 与原先行 (a) 的行为一致), 传 false 时只排静态 HTML; 批次 4 由 paper-p3-host (契约与宿主) 与 paper-p3-plugin (`HtmlToPdf`) 落实; 原先行 (a) 由 paper-p2-ops 实施; 影响 P2.5 `HtmlToPdf`, 宿主 `pdf.fromHtml` 文档; 改选返工: (b) 1 行 + 改 1 个仪器测试 (断言脚本生成的文字), (c) 契约版本 2 与协商测试, 宿主与插件各约 15 行) |
| Q36 | `encrypt` 未给所有者密码时的所有者密码 (契约与协议没有规定; 宿主只要求两个密码至少给出一个, 空串也算给出) | (a) 授予全部权限时所有者密码等于用户密码 (PDFBox 默认), 有权限被收回时生成随机所有者密码 (16 字节随机数的 32 位十六进制, 不返回), 权限位生效, `warnings` 说明之后无法解除限制, `decrypt` 也无法去掉加密 (推荐, 已先行) / (b) 一律等于用户密码, 权限位实际不生效, 只回报警告 / (c) 收回权限而没有所有者密码时报 `INVALID_ARGUMENT` | P2.6 | 已决 (2026-11-13 08:58, 维护者选 (a) 按权限分情况, 保持现状; paper-p2-ops 先行; 同时: 用户密码与所有者密码相同 且收回权限时警告权限位不生效; 两个密码都为空报 `INVALID_ARGUMENT` (宿主放行单独的 `userPassword: ""`); 协议文字请求见共享笔记 pdf-api-change-requests CR-5; 影响 P2.6 `PdfEncryption.protect`, 宿主 `pdf.encrypt` 文档; 改选返工: (b) 删约 5 行并改 1 个单元测试, (c) 改约 3 行并改 1 个单元测试, 协议文字随之改) |
| Q40 | 文件管理器直接从压缩包打开 PDF 的大小上限 | (a) 256 MiB, 与 EPUB / 表格相同 (推荐, 已先行; 扫描版 PDF 常有几十到上百 MB, 超过上限时提示先解压) / (b) 128 MiB / (c) 512 MiB | P1.6 | 已决 (2026-11-13 08:58, 维护者选 (c) 512 MiB (非推荐项, 宿主返工); 原先行 (a) 由 paper-p1-reg 实施; 批次 4 由 paper-p3-host 落实: 宿主 `ExplorerDocumentPreviewerPluginUi.MAX_ARCHIVE_PDF_BYTES`, 1 处单测断言, 10 语言 changelog 中的数字) |
| Q41 | 插件中心与安装向导里 3-Paper PDF 的说明文案从哪里来 | (a) 来自官方索引条目 (P5.4 登记时写 10 语言说明), 宿主只放后备名称 `plugin_three_paper_pdf_name` ("3-Paper PDF", 11 个 values 目录) (推荐, 已先行; EPUB / 表格 / 相机同做法) / (b) 宿主为官方插件增加内置说明字符串 | P1.6, P5.4 | 已决 (2026-11-13 08:58, 维护者选 (a) 来自官方索引, 保持现状; paper-p1-reg 先行; P1.6 第 2 行 "插件中心说明文案 10 语言" 按 (a) 理解为后备名称) |
| Q42 | PDF 文件在文件管理器里不再显示 "编辑" 按钮 | (a) PDF 归为 `IDENTITY_PREVIEWABLE` (只能预览), 编辑按钮不再出现, "用其他应用打开" 仍在引导对话框与 "更多" 菜单里 (推荐, 已先行; 与 EPUB 相同) / (b) 另加 `IDENTITY_EXTERNAL_EDITABLE`, 保留 "编辑" 按钮交给外部应用 | P1.6 | 已决 (2026-11-13 08:58, 维护者选 (a) 不显示编辑按钮, 保持现状; paper-p1-reg 先行; 影响宿主 `FileUtils.TypeDataHolder.PDF_VIEWER`) |
| Q43 | P5.4 回填的 `REQUIRES_HOST_VERSION` | (a) 保持 6641 (含 P1 宿主提交的第一个构建号; 宿主 `VERSION_BUILD` 即整个 versionCode, 公开 6.8.0 与 6.8.1 开发线都是 6640; 闹钟 / 相机 / Java Libraries 先例) (推荐, 已先行, 写进宿主协议文档 "Minimum host build") / (b) 6.8.1 发布前另行提高 `VERSION_BUILD` 时改为发布构建号 | P5.4 | 已决 (2026-11-13 08:58, 维护者选 (a) 6641, 保持现状; paper-p1-reg 先行; 插件 `app/build.gradle.kts` `requiredHostVersion`, `ThreePaperPdfContract.REQUIRED_HOST_VERSION`, 宿主 `PdfIds.REQUIRED_HOST_VERSION_CODE` 三处为 6641) |
| Q44 | 6.8.1 changelog 的 pdf 条目是否提 Node.js / Bun 与文档链接 | (a) 暂不提, 只写 AutoJs6 脚本的 `pdf` 模块 (也可写作 `$pdf`) 与文件管理器; Node / Bun 门面写在两个运行时插件自己的 changelog 里, 随 Q50 发版; 文档页 (P5.1 / P5.3) 落地后由对应条目补写链接 (推荐, 已先行) / (b) 现在就写上 Node / Bun 与文档链接 | P1.6, P5.1, P5.3 | 已决 (2026-11-13 08:58, 维护者选 (a) 暂不提, 保持现状; paper-p1-reg 先行) |
| Q50 | 含 pdf 门面的 Node.js Runtime 2.2.0 / Bun Runtime 0.23.0 何时发布 | (a) 联调卡片真机通过后, 在 3-Paper PDF 1.0.0 发布 (P5.4) 之前发布, 与同样在这两个版本里的 3-Prism camera / 3-Slate media studio 门面协调时间 (推荐, 已先行: 两仓只做本地提交, 未发版) / (b) 不等联调, 现在就发 / (c) 等宿主 6.8.1 公开后再发 | 联调卡片, P5.4 | 已决 (2026-11-13 08:58, 维护者选 (a) 联调通过后, P5.4 之前发布; paper-rt-facade 先行, Node 262168ad, Bun 03ecab8; 改选无代码返工) |
| Q51 | 3-Paper PDF 1.0.0 文档写的最低运行时版本 | (a) Node.js Runtime 2.2.0 / Bun Runtime 0.23.0 (推荐, 已先行: 两仓 changelog 与 HOST-API 已按此写; Q50 发布前若版本号变动, P5.1 同步改) / (b) 3-Paper 文档只写宿主 6.8.1, 运行时版本只出现在两仓 changelog | P5.1, P5.4 | 已决 (2026-11-13 08:58, 维护者选 (a) 写 2.2.0 / 0.23.0, 保持现状; paper-rt-facade 先行) |
| Q52 | Node / Bun 的同步名 (info, render 等 22 个) 返回什么 | (a) 与 xxxAsync 一样返回带 `cancel()` 的 Promise (推荐, 已先行; 桥接全异步, Node 主线程不能阻塞等宿主, 与 Bun `host.camera` 等门面一致, api-coverage 135 格全部 covered) / (b) 只导出 xxxAsync, 22 个同步名在 api-coverage 登记为不适用 / (c) Worker 里用 `Atomics.wait` 同步返回, 主线程仍是 Promise | 两仓门面, d.ts, P5.1 | 已决 (2026-11-13 08:58, 维护者选 (a) 返回 Promise, 保持现状; paper-rt-facade 先行) |
| Q53 | Node / Bun 下 `PdfDocument` 的形状 | (a) `path` / `pageCount` / `isClosed` 是属性 (`pageCount` 在 open 时已知), 11 个方法各带 Async; `close` 幂等且总是 resolve (推荐, 已先行) / (b) 照 Rhino 提供 `pageCount()` 方法 (返回 Promise) | 两仓门面, d.ts, P5.1 | 已决 (2026-11-13 08:58, 维护者选 (a) 属性, 保持现状; paper-rt-facade 先行) |
| Q54 | Node / Bun 下 `PdfViewSession` 方法的返回值 | (a) `goTo` / `close` 解析为会话本身 (可链式), `state` 返回 Promise; `goTo` / `close` 返回的 Promise 不带 `cancel()` (推荐, 已先行) / (b) `goTo` / `close` 解析为 undefined | 两仓门面, d.ts | 已决 (2026-11-13 08:58, 维护者选 (a) 会话本身, 保持现状; paper-rt-facade 先行) |
| Q55 | Node / Bun 调用选项是否支持 `signal` (AbortSignal) / `timeoutMs` | (a) 不支持, 只有返回值的 `cancel()` 与 `onProgress` (推荐, 已先行; Q7 定为默认无超时, Rhino 也没有) / (b) 支持 AbortSignal, 中止即 cancel | 两仓门面, d.ts, P5.1 | 已决 (2026-11-13 08:58, 维护者选 (b) 支持 AbortSignal (非推荐项, 要返工); 只加 `signal`, 不加 `timeoutMs`; 批次 4 由 paper-integ 在两仓门面落实, 约 20 行 + 2 个测试) |
| Q56 | Node / Bun 的 `isAvailable` 要不要能力 | (a) 也要 `files` (推荐, 已先行; 宿主 `PdfScriptCapabilities.requiredFor` 对全部方法要 `files`, 门面照表声明) / (b) 不要能力 (要改宿主能力表) | 门面, 宿主 `PdfScriptCapabilities` | 已决 (2026-11-13 08:58, 维护者选 (a) 也要 `files`, 保持现状; paper-rt-facade 先行) |
| Q57 | Node / Bun 参数个数不符时怎么报 | (a) 以 `INVALID_ARGUMENT` 的 PdfError reject, 不同步抛出 (推荐, 已先行; 所有错误都从 Promise 出来) / (b) 同步抛出 PdfError (与 Rhino 同步名一致) | 两仓门面, 测试 | 已决 (2026-11-13 08:58, 维护者选 (a) Promise reject, 保持现状; paper-rt-facade 先行) |
| Q58 | Node / Bun 缺能力 / 授权时的错误码 | (a) Node 保留宿主的 `ERR_AUTOJS6_CAPABILITY_NOT_DECLARED` 与 `capability` / `missingCapabilities`, Bun 为 PdfError `NOT_GRANTED` + `capability: 'pdf.control'` (与 epub 一致, OCR 缺 `ocr.control` 时也是 `pdf.control`, 消息写明) (推荐, 已先行) / (b) Node 也映射为 `NOT_GRANTED` | 两仓门面, P5.1 | 已决 (2026-11-13 08:58, 维护者选 (a) 各随运行时惯例, 保持现状; paper-rt-facade 先行) |
| Q59 | 查看器 `close` 事件的 reason | (a) 插件给的 reason 照传 (closed / replaced / timeout / finished), 宿主积压时 `overflow`, 轮询失败时门面本地给 `error`, 事件缺 reason 时为 `host` (推荐, 已先行) / (b) 门面归一成 `user` / `script` / `error` 三种 | 两仓门面, d.ts, P3.2, P5.1 | 已决 (2026-11-13 08:58, 维护者选 (a) 照传插件的值, 保持现状; paper-rt-facade 先行; 插件 P3.2 按契约 `REASONS` 发 closed / replaced / timeout / finished) |
| Q60 | Q31 / Q35 的契约改动 (`displayNames`, `javaScript`) 版本号怎么处理 | (a) 就地改 v1: 契约版本仍为 1, 不写 v1 / v2 协商分支, `pdf-api.aar` 重出一版, P6 仍是契约版本 2 (推荐; 宿主 6.8.1 与插件 1.0.0 均未发布, 没有已部署的 v1 对端) / (b) 升到 v2 并保留 v1 回落与协商测试, P6 顺延为 3 | 批次 4 | 已决 (2026-11-13 08:58, 维护者选 (a) 就地改 v1; 调度会话追问; 影响 `pdf-api`, 宿主 `core/plugin/pdf` 与脚本模块, 插件 `PdfMerger` / `HtmlToPdf`, 协议文档) |
| Q61 | `pdf.fromHtml` 的 `javaScript` 选项缺省值 | (a) 缺省 true, 与已实现行为和仪器测试一致, 脚本传 false 关闭 (推荐) / (b) 缺省 false | 批次 4 | 已决 (2026-11-13 08:58, 维护者选 (a) 缺省执行; 调度会话追问; 影响宿主脚本模块, 插件 `HtmlToPdf`, Node / Bun d.ts, P5.1 文档) |

## 附录 E. 证据等级

| 等级 | 含义 |
| --- | --- |
| E1 | 单元测试 (JVM) |
| E2 | 仪器测试 (AVD, 与 CI 同款配方) |
| E3 | 真机脚本 (`scripts/device-regression.js` 或宿主 `samples/`), 只记通过 / 失败与关键数值 |
| E4 | 需维护者在手机上操作的项 (授权对话框, 查看器手势, 启动器图标), 由维护者确认 |
| E5 | 外部文档或源码阅读 (Android / PDFBox 文档, 宿主源码), 需注明出处 |

## 附录 F. 基线快照与设备矩阵

基线 (2026-11-12, 进入 P0 时刷新):

- 宿主 AutoJs6: 6.8.0 已于 2026-11-03 公开 (tag `v6.8.0` = `b7c88e117d`, versionCode 6640 = `VERSION_BUILD` 4988 + `VERSION_CODE_OFFSET` 1652); 公开时重写过历史, 立项基线 `e25e28f88` 已失效. `master` 为 6.8.1 开发线 `739ac2f96e` (2026-11-12 12:11), `VERSION_NAME` 6.8.1, `VERSION_BUILD` 6640; 自 `ef27581df9` 起宿主不再用 offset, `VERSION_BUILD` 即 versionCode, 6.8.1 开发期间与公开 6.8.0 同为 6640, 所以插件临时 `REQUIRES_HOST_VERSION` 取 6641 (5.5 节). 宿主侧 P1 条目进 6.8.1.
- 插件平台与依赖线: `PLATFORM_VERSIONS_PLUGIN_VERSION` 1.9.1 (仍是最新 tag), Compose BOM 2026.09.00, navigation 2.10.2, `activity-compose` 1.12.2 (D26 核对不变; 解析为 1.13.0, 见 D26); `pdfbox-android` 2.0.27.0 (Q1 已决).
- 流程: 多语言文案自 2026-11-05 起所有语言一次写全 (`.claude/rules/all-languages.md`), 不再留给维护者同步.

基线 (2026-10-22, 立项, 仅作历史):

- 宿主 AutoJs6 `master` `e25e28f88` (已失效, 见上), `VERSION_NAME` 6.8.0 (未发布), `VERSION_BUILD` 4283, versionCode 5935 (偏移 1652), minSdk 24, compile / target 37; `plugin-api/epub-api` 契约版本 2, `explorer-action-api` 只读信封 v2.
- 插件平台: `PLATFORM_VERSIONS_PLUGIN_VERSION` 1.9.1; 3-Slate Studio 立项依赖线: Compose BOM 2026.09.00, navigation 2.10.2; 3-Maple Image: `MANAGE_EXTERNAL_STORAGE`; 3-Macaw Speech 1.0.0 发布时 `REQUIRES_HOST_VERSION` 5456 (宿主未公开, D41 先例); Icon Studio 内核 1.4.0.
- 外部: `pdfbox-android` 2.0.27.0 (BouncyCastle jdk15to18 1.72); 系统 `PdfRenderer` / `PdfDocument` API 21+ / 19+.

设备矩阵 (`COMMON_AGENTS.md`):

| 设备 | API | 用途 |
| --- | --- | --- |
| Sony G8441 (`BH900ASK9E`) | 28 | 低端性能基线, SharedMemory (API 27+) 最低真机, 运行时存储权限路径 |
| Sony XQ-AT72 (`QV710AF65F`) | 31 | 查看器与工具箱界面, 所有文件访问授权页 |
| Sony XQ-DQ72 (`QV770340J7`) | 33 | 主力联调, 脚本回归, OCR (注意 debug 宿主在此机的 ART JIT ANR 记忆) |
| Xiaomi Pad 23046RP50C (`968e9f18`) | 35 | HyperOS, 大屏查看器与向导, 存储授权需手动 |
| OnePlus OPD2413 (`63751f63`) | 35 | ColorOS 后台启动 Activity / 前台服务 |
| Redmi 22120RN86C (`bek749scrwv4wo8h`) | 33 | 旧宿主隐藏验证 (不兼容提示) |
| AVD API 24 / 33 / 35 / 37 (`E:\.android\avd\`) | 24 / 33 / 35 / 37 | 最低版本与 fd 回落, CI 同款仪器测试 (33), 新系统行为 |

16 KB 页设备: 1.0.0 只有 Compose 的 graphics-path 原生库, 构建时校验 16 KB 对齐 (Q9), 不需要; 若未来引入原生库再租用 Samsung SM-A566B / SM-F971B.

性能基线表 (渲染列由 P2.3 填入, 合并列由 P2.4 填入, 其余由 P2.7 回填). 渲染列: `text.pdf` 第 1 页, 144 dpi 即 1190 x 1684, 一次 `render` 调用 (仪器测试, 服务与测试同进程, 调用经过真实 Parcel) 连续 12 次取后 10 次的中位数, 括号内为宿主侧接收 (映射区域或解码 PNG 并拷进位图) 的中位数; 测试 `PdfServiceRenderingTest.timingOfAnA4TextPageAt144Dpi`, logcat 标签 `PaperRenderPerf`. 合并列: 四个 25 页的 A4 文本文档 (测试内用系统 `PdfDocument` 生成, 每页 60 行) 经 Binder `run` `merge` 为 100 页, 从调用到 `onComplete` 计时, 预热 1 次后 3 次的中位数, 括号内为 3 次的范围; 测试 `PdfServicePageOperationsTest.timingOfAHundredPageMerge`, logcat 标签 `PaperMergePerf`. 20 张图片列 (P2.5 填入): 测试内生成 20 张 1600 x 1200 的 JPEG 照片 (原样嵌入) 与 20 张同尺寸的 PNG 图画 (转码为 JPEG 90), 两组各经 Binder `run` `fromImages` 按缺省选项 (`pageSize: 'fit'`, 页面即图片尺寸) 转为 20 页, 从调用到 `onComplete` 计时, 预热 1 次后 3 次的中位数, 写作 "JPEG / PNG"; 测试 `PdfServiceGenerationTest.timingOfTwentyImages`, logcat 标签 `PaperImagesPerf`.

| 设备 | A4 文本页 144 dpi 渲染耗时 | 100 页合并耗时 | 20 张图片转 PDF 耗时 | 备注 |
| --- | --- | --- | --- | --- |
| Sony G8441 (API 28) | 14.7 ms (接收 6.9 ms) | 440 ms (421 - 524 ms; 输入共 205 KB, 输出 224 KB) | 120 ms / 1613 ms (JPEG 输入共 498 KB, 输出 509 KB; 2026-11-13 全套中为 132 ms / 1657 ms) | SharedMemory; 2026-11-12 paper-p2-core, 单独运行, 前三次全套运行为 14.0 - 14.5 ms |
| AVD API 33 (x86_64) | 21.2 ms (接收 7.9 ms) | 1325 ms (886 - 1798 ms; 输入共 882 KB, 输出 899 KB; 与另外 5 个模拟器并行, 只作参考) | 102 ms / 1354 ms (全套中; 前一次全套为 78 ms / 1475 ms) | SharedMemory; 与 G8441, API 24 的全套并行运行时测得, 只作参考 |
| AVD API 27 (x86) | 16.7 ms (接收 3.2 ms) | (P2.7) | (P2.7) | SharedMemory; 单独运行 |
| AVD API 25 (x86_64) | 132.0 ms (接收 32.1 ms) | (P2.7) | (P2.7) | fd 回落 (PNG 编码 + 解码); 单独运行 |
| AVD API 24 (x86) | 264.1 ms (接收 53.1 ms) | 1261 ms (901 - 1353 ms; 输入共 1.8 MB, 输出 1.8 MB; 与其他模拟器并行) | 158 ms / 1621 ms (全套中) | fd 回落 (PNG 编码 + 解码); 单独运行, 32 位镜像, 与其他模拟器并行时为 200 - 420 ms |
| Sony XQ-AT72 (API 31) | (P2.7) | (P2.7) | 95 ms / 940 ms | 2026-11-13 paper-p2-ops, 只跑 `PdfServiceGenerationTest` |
| Xiaomi Pad 23046RP50C (API 35) | (P2.7) | (P2.7) | 50 ms / 694 ms | 2026-11-13 paper-p2-ops, 只跑 `PdfServiceGenerationTest` |
| AVD API 37.1 16 KB (x86_64) | (P2.7) | (P2.7) | 169 ms / 1318 ms | 2026-11-13 paper-p2-ops, 只跑 `PdfServiceGenerationTest` |

## 附录 G. 已评估暂缓

| 事项 | 结论 |
| --- | --- |
| 只用系统 API 不引入 PDFBox | 做不了文本提取 (API 35 以下), 页面操作, 加密与图片提取; 维护者选 D3 |
| pdfium / MuPDF 原生渲染 | 原生库与 ABI 拆分, MuPDF 为 AGPL; 系统 `PdfRenderer` 已够用 (D3) |
| `androidx.pdf` 查看器组件 | minSdk 28 且仍为 beta, 与自绘查看器重复; 维护者选 D5 |
| 插件全程申请存储权限按路径读写 | 维护者选脚本路径走 fd, 界面申请权限的组合 (D7); 评估时的替代项 "全部走 fd, 界面用系统选择器" 因界面文件浏览体验而放弃 |
| 位图经 PNG 文件往返 | API 27+ 用 SharedMemory 省一次编码; 文件 fd 只作回落 (D9) |
| 使用 API 35 `PdfRenderer` 文本 / 搜索 API | 版本覆盖不全 (需 S 扩展 13+ 或 API 35), 结果与 PDFBox 不一致; 统一走 PDFBox (D30) |
| 远程 URL 转 PDF | 网络与安全边界扩大; 1.0.0 只收本地 HTML (D8) |
| 注释, 数字签名, PDF/A, reflow | 不在 1.0.0 与 1.1.0 范围 (第 3 节) |
| 工具箱生成脚本代码 | 3-Slate Studio D11 同款, 价值在但非本插件首要; 1.1.0 后再评估 |
| 等宿主 6.8.0 公开再发 1.0.0 | 发布时点不可控; 照 Macaw 先例不等 (D41). 6.8.0 已于 2026-11-03 公开, 对 6.8.1 同样不等 (Q8 已决) |
