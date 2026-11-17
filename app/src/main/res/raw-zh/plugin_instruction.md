3-Paper PDF 为 AutoJs6 脚本提供 `pdf` 模块, 用于读取, 渲染, 合并, 拆分, 生成与加密 PDF 文件. 它同时是可以单独使用的 PDF 查看器与工具箱, 无需安装 AutoJs6.

### 开始使用

1. 在 AutoJs6 脚本中直接使用全局模块 `pdf`, 示例见下文.
2. 如需在手机上查看或处理 PDF, 从启动器打开 3-Paper PDF, 按提示授予所有文件访问权限, 然后点按 "打开 PDF", 或在 "工具" 中选择所需的处理.
3. 在 AutoJs6 的文件管理器中点按 PDF 文件, 即在 3-Paper PDF 的查看器中打开.

### 主要功能

- **读取**: 页数, 页面尺寸, 元数据与加密状态; 按页提取文本与大纲 (书签); 搜索文字; 列出并导出内嵌图片.
- **渲染**: 把页面渲染为图像对象或 PNG / JPEG / WebP 文件, 可以按 dpi, 缩放比例或目标宽高指定尺寸.
- **页面操作**: 合并 (保留书签), 拆分, 抽取, 删除, 重排与旋转页面.
- **生成与加密**: 把图片, 文本或 HTML 生成为 PDF; 为文档设置密码与权限, 或去除密码.
- **扫描页识别**: 借助已安装的 OCR 插件识别没有文本层的扫描页.
- **查看器**: 连续滚动, 双指缩放, 缩略图, 大纲, 搜索高亮, 跳页与夜间模式. 其他应用可以通过 "打开方式" 或分享把 PDF 交给它.
- **工具箱**: 合并, 拆分, 转图片, 图片转 PDF, 加密, 解密, 提取文本与页面整理. 任务在后台依次执行, 通知栏显示进度.
- **AutoJs6 文件管理器**: 点按 PDF 即用查看器打开, 压缩包内的 PDF 也可直接打开; 文件菜单提供合并, 拆分, 转图片, 提取文本, 加密与移除密码.

### 在脚本中使用

在 AutoJs6 脚本中直接使用全局模块 `pdf`. 页码从 1 开始, 页码范围写作 `'1-3,5,8-'`:

```js
let info = pdf.info("./book.pdf");
console.log(info.pageCount, info.title);

let texts = pdf.text("./book.pdf", { pages: "1-3" });
console.log(texts[0]);

let img = pdf.render("./book.pdf", 1, { dpi: 200 });
images.save(img, "./page-1.png");
img.recycle();

pdf.merge(["./a.pdf", "./b.pdf"], "./ab.pdf");
```

- 每个方法都有返回 Promise 的 `Async` 形态 (例如 `pdf.mergeAsync`). 同步形态不能在 UI 线程调用, 界面脚本请使用 `Async` 形态.
- 打开加密文档时在选项中传入 `password`. 出错时抛出带 `code` 的错误, 例如 `PASSWORD_REQUIRED` 或 `PAGE_OUT_OF_RANGE`.
- Node.js 中使用 `require('autojs6:pdf')` 加载, Bun 中通过 `host.pdf` 使用, 方法相同, 均返回 Promise.

### 运行要求

- Android 7.0 或更高版本. 查看器与工具箱可以单独使用, 无需 AutoJs6.
- 脚本中的 `pdf` 模块与文件管理器入口需要 AutoJs6 6.8.1 或更高版本. 脚本读写的文件由 AutoJs6 打开, 因此 AutoJs6 需要存储权限.
- 工具箱与从启动器打开的查看器需要所有文件访问权限, 脚本处理文件不需要插件的这项权限.
- AutoJs6 打包的应用同样可以使用 `pdf` 模块: 需由 AutoJs6 6.8.1 或更高版本打包, 运行的设备上需安装本插件.
- Node.js 脚本需要 Node.js Runtime 插件 2.2.0 或更高版本, 并在 `project.json` 的 `node.permissions` 中声明 `files`; Bun 脚本需要 Bun Runtime 插件 0.23.0 或更高版本 (Android 13 及以上), 并在其设置页开启 `pdf.control`.
- 识别扫描页的文字需要另行安装 OCR 插件 (ML Kit OCR, Paddle OCR 或 Rapid OCR).

### 常见问题

**未安装 AutoJs6 时是否可用**

可以. 查看器与工具箱均可单独使用, 仅在脚本中处理 PDF 时需要 AutoJs6.

**插件中心显示本插件不兼容的原因**

脚本模块与文件管理器入口需要 AutoJs6 6.8.1 或更高版本, 升级 AutoJs6 后即可启用; 在此之前查看器与工具箱照常可用.

**最近文件中的文件无法打开时的处理**

文件被删除, 移动或访问授权被撤销后, 点按该条目会给出提示并将其从列表中移除. 请通过 "打开 PDF" 重新选择.

完整的脚本说明见 [AutoJs6 文档 > PDF](https://docs.autojs6.com/#/pdf), 其他信息见 [项目主页](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF).
