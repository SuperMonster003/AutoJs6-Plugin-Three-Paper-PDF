<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-paper-pdf-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>讀取, 渲染, 合併, 拆分, 生成與加密 PDF 檔案, 內置檢視器與檔案管理器入口</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 語言 (Languages)

******

目前 README.md 支援以下語言:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hans.md)
- 繁體中文 (香港) [zh-Hant-HK] # 目前
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ar.md)

******

### 簡介

******

3-Paper PDF 是 AutoJs6 的 PDF 插件, 也是不安裝 AutoJs6 也能單獨使用的 PDF 工具箱與檢視器. 它可以讀取 PDF 的資訊, 文字, 大綱與內嵌圖片, 把頁面渲染為圖片; 可以合併, 拆分, 抽取, 刪除, 重排與旋轉頁面; 可以把圖片, 文字或 HTML 生成為 PDF, 並為文件加密或解密.

處理在插件內以 Android 系統的 `PdfRenderer` 與純 Java 的 PDFBox-Android 完成, AutoJs6 主程式因此保持精簡. AutoJs6 腳本透過全域模組 `pdf` 使用這些功能, 腳本傳入的路徑由 AutoJs6 開啟後交給插件, 插件處理腳本檔案不需要儲存權限. 插件內置檢視器, 可以從啟動器, AutoJs6 的檔案管理器或腳本 (`pdf.view`) 開啟.

> 目前為開發預覽版: 插件可以被 AutoJs6 插件中心識別, 但讀取, 渲染, 頁面操作, 檢視器, 工具箱與 `pdf` 模組尚未交付. 下文描述的是 1.0.0 的設計目標, 進度請見開發路線圖.

******

### 功能亮點

******

- 讀取: 頁數, 頁面尺寸, 中繼資料, 加密狀態與權限; 按頁擷取文字與大綱 (書籤); 搜尋文字並傳回頁碼與位置; 列出並匯出內嵌圖片.
- 渲染: 把頁面渲染為 `Image` 物件或 PNG / JPEG / WebP 檔案, 預設 144 dpi, 也可以按 dpi, 縮放比例或目標寬高指定.
- 頁面操作: 合併 (保留書籤), 拆分, 抽取, 刪除, 重排與旋轉頁面; 頁碼從 1 開始, 範圍寫作 `'1-3,5,8-'`.
- 生成: 圖片轉 PDF (JPEG 直接嵌入, 不重新編碼), 文字轉 PDF (可指定字型), HTML 轉 PDF (不載入遠端網址).
- 加密: 以密碼開啟加密文件, 以 AES-256 加密並設定權限, 移除加密.
- 檢視器: 連續捲動, 雙指縮放, 縮圖, 大綱, 搜尋醒目標示, 密碼輸入與夜間模式; 腳本可以開啟檢視器並接收翻頁與關閉事件.
- 工具箱與檔案管理器: 合併, 拆分, 轉圖片, 圖片轉 PDF, 加密 / 解密與擷取文字的精靈; 在 AutoJs6 的檔案管理器中可以直接用插件檢視器開啟 PDF, 溢出選單提供同樣的工具.
- OCR: `pdf.text(path, { ocr: true })` 為沒有文字層的掃描頁呼叫 AutoJs6 的 OCR 引擎.

******

### 使用方法

******

1. 從 [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases) 下載 APK 並安裝. 所有 ABI 共用一個安裝套件.
2. 如需在腳本中使用 `pdf` 模組, 請將 AutoJs6 升級到內部版本 6641 (6.8.1) 或更新版本, 並在插件中心啟用本插件.
3. 如需使用插件內置的工具箱, 開啟插件並按提示授予所有檔案存取權限; 腳本操作不需要此權限.

> 插件支援 Android 7.0 (API 24) 或以上的裝置. PDF 處理全部使用系統 API 與純 Java 程式庫, 唯一的原生程式庫來自 Jetpack Compose, 已按 16 KB 記憶體分頁對齊, 因此所有 ABI 共用一個安裝套件.

******

### 腳本 API

******

腳本透過全域模組 `pdf` (也可寫作 `$pdf`) 使用這些功能 (1.0.0 設計草案, 以發佈版本的文件為準):

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

- 便捷方法 `pdf.info` / `pdf.text` / `pdf.render` / `pdf.merge` / `pdf.split` / `pdf.fromImages` 等每次自行開啟與關閉文件; `pdf.open(path, options)` 傳回 `PdfDocument`, 多次操作共用一次開啟, 每個腳本最多同時開啟 8 份文件.
- 每個方法都有阻塞形式與傳回 Promise 的 `xxxAsync` 形式; `xxxAsync` 接受 `options.onProgress(progress)`, 傳回的 Promise 帶有 `cancel()`, 停止腳本即取消進行中的操作並刪除未完成的輸出.
- `pdf.view(path, options)` 開啟檢視器並傳回 `PdfViewSession`, 可以監聽 `page` 與 `close` 事件, 呼叫 `goTo(page)` 與 `close()`.
- 失敗時拋出 `PdfError`, 其 `code` 為穩定的錯誤碼, 例如 `PASSWORD_REQUIRED`, `WRONG_PASSWORD` 或 `PAGE_OUT_OF_RANGE`; `pdf.isAvailable()` 可以預先探測插件是否可用.

******

### 常見問題

******

#### 不安裝 AutoJs6 能用嗎?

可以. 工具箱與檢視器都能單獨使用, 只有在腳本中處理 PDF 時才需要 AutoJs6.

#### 為甚麼插件要申請所有檔案存取權限?

只有工具箱和從啟動器開啟的檢視器需要它, 用於讀寫你在畫面上選擇的檔案. 腳本傳入的路徑由 AutoJs6 開啟後以檔案描述符交給插件, 不需要插件的儲存權限.

#### 插件包含原生程式庫嗎?

PDF 處理不使用原生程式庫: 頁面渲染使用系統的 `PdfRenderer`, 其餘功能使用純 Java 的 PDFBox-Android. 安裝套件中唯一的原生程式庫是 Jetpack Compose 內置的 `libandroidx.graphics.path.so`.

******

### 權限與安全

******

插件在設計上盡量收窄權限範圍:

- 受保護的入口: 能力服務, 檔案管理器入口服務與啟動入口都受 AutoJs6 插件權限 (`org.autojs.permission.PLUGIN`) 保護, 其他應用程式無法直接呼叫.
- 儲存空間: 腳本的檔案以檔案描述符傳入, 插件不需要為腳本申請儲存權限; 所有檔案存取權限只用於工具箱與檢視器中你選擇的檔案.
- HTML 轉 PDF: 只渲染提供的 HTML 字串或本機檔案, 不載入遠端網址.
- 網絡與密碼: 網絡只用於檢查更新; 文件密碼只在當次操作中使用, 插件不會儲存.

******

### 引擎與依賴

******

頁面渲染使用 Android 系統的 `PdfRenderer`; 文字, 大綱, 圖片, 頁面操作與加密使用 [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache-2.0, 純 Java), 加密依賴 Bouncy Castle (MIT 式授權). PDF 處理不包含原生程式庫, 不使用 pdfium 或 MuPDF 等原生渲染程式庫.

******

### 外掛接口

******

以下資訊面向 AutoJs6 宿主與外掛開發者, 宿主透過這些標識發現外掛並完成能力協商:

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

### 開發路線圖

******

功能規劃, 設計決策與完成情況以可勾選清單維護在 ROADMAP.md 中. 未勾選條目表示規劃意向, 不代表目前版本能力.

- [查看 ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/ROADMAP.md)

******

### 發行歷史

******

#### v1.0.0

_2026/11/12_

- `提示` 開發預覽: 插件可以被 AutoJs6 插件中心識別, 讀取, 渲染, 頁面操作, 檢視器, 工具箱與 pdf 腳本模組尚未交付
- `提示` 需要 AutoJs6 6.8.1 (6641) 或更高版本 (準確的最低版本號待宿主端改動落地後回填)
- `新增` 插件骨架: Wake 啟動協議, 插件中心身份服務, PDF 能力服務 (契約版本 1) 與檔案管理器入口服務
- `新增` 首頁提供所有檔案存取權限的授權入口 (僅工具箱需要)
- `新增` 啟動器與插件中心圖示各有亮暗兩套; 啟動器預設使用自適應自動圖示 (隨系統亮暗切換), 另已內置自適應亮色, 自適應暗色與透明背景三種樣式, 切換入口隨之後的設定頁提供
- `新增` 所有 ABI 共用一個 APK: PDF 處理不含原生庫, 唯一的原生庫來自 Jetpack Compose, 已按 16 KB 記憶體分頁對齊
- `新增` 10 種語言的 README, 插件中心說明與更新日誌, 由 JSON 來源檔統一生成
- `依賴` 附加 PDFBox-Android 版本 2.0.27.0 (文字, 大綱, 圖片, 頁面操作與加密)
- `依賴` 附加 Jetpack Compose BOM 版本 2026.09.00 (插件畫面)
- `依賴` 附加 Navigation Compose 版本 2.10.2
- `依賴` 附加 AndroidX Core KTX 版本 1.18.0

##### 更多發行歷史可參閱

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/assets/doc/CHANGELOG-zh-Hant-HK.md)

******

### 構建

******

本節面向希望從源碼構建外掛的開發者.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:appendDigestToReleasedFiles
py .python\generate_markdown.py --check
py .python\generate_third_party_notices.py --check
py .python\generate_icon_studio.py --check
py .python\check_repository.py
```

******

### 許可

******

項目代碼使用 [Mozilla Public License 2.0](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/LICENSE). 第三方元件與數據的授權見第三方聲明.

- [第三方聲明 (THIRD_PARTY_NOTICES.md)](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/THIRD_PARTY_NOTICES.md)

******

### 相關連結

******

- AutoJs6 項目: https://github.com/SuperMonster003/AutoJs6
