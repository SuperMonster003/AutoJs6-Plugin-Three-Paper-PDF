<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-paper-pdf-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>讀取, 算繪, 合併, 分割, 產生與加密 PDF 檔案, 內建檢視器與檔案管理員入口</p>

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
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hant-HK.md)
- 繁體中文 (台灣) [zh-Hant-TW] # 目前
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

3-Paper PDF 是 AutoJs6 的 PDF 外掛, 也是不安裝 AutoJs6 也能單獨使用的 PDF 工具箱與檢視器. 它可以讀取 PDF 的資訊, 文字, 大綱與內嵌圖片, 將頁面算繪為圖片; 可以合併, 分割, 擷取, 刪除, 重排與旋轉頁面; 可以將圖片, 文字或 HTML 產生為 PDF, 並為文件加密或解密.

處理在外掛內以 Android 系統的 `PdfRenderer` 與純 Java 的 PDFBox-Android 完成, AutoJs6 主程式因此保持精簡. AutoJs6 指令碼透過全域模組 `pdf` 使用這些功能, 指令碼傳入的路徑由 AutoJs6 開啟後交給外掛, 外掛處理指令碼檔案不需要儲存權限. 外掛內建檢視器, 可以從啟動器, AutoJs6 的檔案管理員或指令碼 (`pdf.view`) 開啟.

> 目前為開發預覽版: 外掛可以被 AutoJs6 外掛中心辨識, 但讀取, 算繪, 頁面操作, 檢視器, 工具箱與 `pdf` 模組尚未交付. 下文描述的是 1.0.0 的設計目標, 進度請見開發路線圖.

******

### 功能亮點

******

- 讀取: 頁數, 頁面尺寸, 中繼資料, 加密狀態與權限; 依頁擷取文字與大綱 (書籤); 搜尋文字並傳回頁碼與位置; 列出並匯出內嵌圖片.
- 算繪: 將頁面算繪為 `Image` 物件或 PNG / JPEG / WebP 檔案, 預設 144 dpi, 也可以依 dpi, 縮放比例或目標寬高指定.
- 頁面操作: 合併 (保留書籤), 分割, 擷取, 刪除, 重排與旋轉頁面; 頁碼從 1 開始, 範圍寫作 `'1-3,5,8-'`.
- 產生: 圖片轉 PDF (JPEG 直接嵌入, 不重新編碼), 文字轉 PDF (可指定字型), HTML 轉 PDF (不載入遠端網址).
- 加密: 以密碼開啟加密文件, 以 AES-256 加密並設定權限, 移除加密.
- 檢視器: 連續捲動, 雙指縮放, 縮圖, 大綱, 搜尋醒目提示, 密碼輸入與夜間模式; 指令碼可以開啟檢視器並接收翻頁與關閉事件.
- 工具箱與檔案管理員: 合併, 分割, 轉圖片, 圖片轉 PDF, 加密 / 解密與擷取文字的精靈; 在 AutoJs6 的檔案管理員中可以直接用外掛檢視器開啟 PDF, 溢位選單提供同樣的工具.
- OCR: `pdf.text(path, { ocr: true })` 為沒有文字層的掃描頁呼叫 AutoJs6 的 OCR 引擎.

******

### 使用方法

******

1. 從 [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases) 下載 APK 並安裝. 所有 ABI 共用一個安裝套件.
2. 如需在指令碼中使用 `pdf` 模組, 請將 AutoJs6 升級到內部版本 6641 (6.8.1) 或更新版本, 並在外掛中心啟用本外掛.
3. 如需使用外掛內建的工具箱, 開啟外掛並依提示授予所有檔案存取權限; 指令碼操作不需要此權限.

> 外掛支援 Android 7.0 (API 24) 以上的裝置. PDF 處理全部使用系統 API 與純 Java 程式庫, 唯一的原生程式庫來自 Jetpack Compose, 已依 16 KB 記憶體分頁對齊, 因此所有 ABI 共用一個安裝套件.

******

### 腳本 API

******

指令碼透過全域模組 `pdf` (也可寫作 `$pdf`) 使用這些功能 (1.0.0 設計草案, 以發布版本的文件為準):

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

- 便利方法 `pdf.info` / `pdf.text` / `pdf.render` / `pdf.merge` / `pdf.split` / `pdf.fromImages` 等每次自行開啟與關閉文件; `pdf.open(path, options)` 傳回 `PdfDocument`, 多次操作共用一次開啟, 每個指令碼最多同時開啟 8 份文件.
- 每個方法都有阻塞形式與傳回 Promise 的 `xxxAsync` 形式; `xxxAsync` 接受 `options.onProgress(progress)`, 傳回的 Promise 帶有 `cancel()`, 停止指令碼即取消進行中的操作並刪除未完成的輸出.
- `pdf.view(path, options)` 開啟檢視器並傳回 `PdfViewSession`, 可以監聽 `page` 與 `close` 事件, 呼叫 `goTo(page)` 與 `close()`.
- 失敗時擲出 `PdfError`, 其 `code` 為穩定的錯誤碼, 例如 `PASSWORD_REQUIRED`, `WRONG_PASSWORD` 或 `PAGE_OUT_OF_RANGE`; `pdf.isAvailable()` 可以預先探測外掛是否可用.

******

### 常見問題

******

#### 不安裝 AutoJs6 能用嗎?

可以. 工具箱與檢視器都能單獨使用, 只有在指令碼中處理 PDF 時才需要 AutoJs6.

#### 為什麼外掛要申請所有檔案存取權限?

只有工具箱和從啟動器開啟的檢視器需要它, 用於讀寫你在畫面上選擇的檔案. 指令碼傳入的路徑由 AutoJs6 開啟後以檔案描述元交給外掛, 不需要外掛的儲存權限.

#### 外掛包含原生程式庫嗎?

PDF 處理不使用原生程式庫: 頁面算繪使用系統的 `PdfRenderer`, 其餘功能使用純 Java 的 PDFBox-Android. 安裝套件中唯一的原生程式庫是 Jetpack Compose 內建的 `libandroidx.graphics.path.so`.

******

### 權限與安全

******

外掛在設計上盡量縮小權限範圍:

- 受保護的入口: 能力服務, 檔案管理員入口服務與啟用入口都受 AutoJs6 外掛權限 (`org.autojs.permission.PLUGIN`) 保護, 其他應用程式無法直接呼叫.
- 儲存空間: 指令碼的檔案以檔案描述元傳入, 外掛不需要為指令碼申請儲存權限; 所有檔案存取權限只用於工具箱與檢視器中你選擇的檔案.
- HTML 轉 PDF: 只算繪提供的 HTML 字串或本機檔案, 不載入遠端網址.
- 網路與密碼: 網路只用於檢查更新; 文件密碼只在當次操作中使用, 外掛不會儲存.

******

### 引擎與相依性

******

頁面算繪使用 Android 系統的 `PdfRenderer`; 文字, 大綱, 圖片, 頁面操作與加密使用 [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache-2.0, 純 Java), 加密相依於 Bouncy Castle (MIT 式授權). PDF 處理不包含原生程式庫, 不使用 pdfium 或 MuPDF 等原生算繪程式庫.

******

### 外掛介面

******

以下資訊面向 AutoJs6 主程式與外掛開發者, 主程式透過這些識別碼發現外掛並完成能力協商:

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

功能規劃, 設計決策與完成情況以可勾選清單維護在 ROADMAP.md 中. 未勾選的項目表示規劃意向, 不代表目前版本的能力.

- [檢視 ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/ROADMAP.md)

******

### 發行歷史

******

#### v1.0.0

_2026/11/12_

- `提示` 開發預覽: 外掛可以被 AutoJs6 外掛中心辨識, 讀取, 算繪, 頁面操作, 檢視器, 工具箱與 pdf 指令碼模組尚未交付
- `提示` 需要 AutoJs6 6.8.1 (6641) 或更新版本 (準確的最低版本號待主程式端改動落地後回填)
- `新增` 外掛骨架: Wake 啟用協定, 外掛中心身分服務, PDF 能力服務 (契約版本 1) 與檔案管理員入口服務
- `新增` 首頁提供所有檔案存取權限的授權入口 (僅工具箱需要)
- `新增` 所有 ABI 共用一個 APK: PDF 處理不含原生程式庫, 唯一的原生程式庫來自 Jetpack Compose, 已依 16 KB 記憶體分頁對齊
- `新增` 10 種語言的 README, 外掛中心說明與更新日誌, 由 JSON 來源檔統一產生
- `相依性` 附加 PDFBox-Android 版本 2.0.27.0 (文字, 大綱, 圖片, 頁面操作與加密)
- `相依性` 附加 Jetpack Compose BOM 版本 2026.09.00 (外掛畫面)
- `相依性` 附加 Navigation Compose 版本 2.10.2
- `相依性` 附加 AndroidX Core KTX 版本 1.18.0

##### 更多發行歷史可參閱

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/assets/doc/CHANGELOG-zh-Hant-TW.md)

******

### 建置

******

本節面向希望從原始碼建置外掛的開發者.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:appendDigestToReleasedFiles
py .python\generate_markdown.py --check
py .python\generate_third_party_notices.py --check
py .python\check_repository.py
```

******

### 授權

******

專案程式碼採用 [Mozilla Public License 2.0](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/LICENSE). 第三方元件與資料的授權見第三方聲明.

- [第三方聲明 (THIRD_PARTY_NOTICES.md)](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/THIRD_PARTY_NOTICES.md)

******

### 相關連結

******

- AutoJs6 專案: https://github.com/SuperMonster003/AutoJs6
