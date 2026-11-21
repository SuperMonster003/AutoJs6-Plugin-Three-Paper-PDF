<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-paper-pdf-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>讀取, 渲染, 合併, 拆分, 生成, 壓縮與加密 PDF 檔案, 可填寫表格與加上水印, 內置檢視器與檔案管理器入口</p>

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

3-Paper PDF 是 AutoJs6 的 PDF 插件, 也是不安裝 AutoJs6 也能單獨使用的 PDF 工具箱與檢視器. 它可以讀取 PDF 的資訊, 文字, 大綱與內嵌圖片, 把頁面渲染為圖片; 可以合併, 拆分, 抽取, 刪除, 重排與旋轉頁面; 可以把圖片, 文字或 HTML 生成為 PDF, 並為文件加密或解密. 1.1.0 起還可以讀取與填寫表格, 加上水印, 頁碼與頁眉頁腳, 借助 OCR 插件生成可搜索 PDF, 壓縮檔案體積, 以及修改文件屬性與書籤.

處理在插件內以 Android 系統的 `PdfRenderer` 與純 Java 的 PDFBox-Android 完成, AutoJs6 主程式因此保持精簡. AutoJs6 腳本透過全域模組 `pdf` 使用這些功能, Node.js Runtime 與 Bun Runtime 插件中的腳本也可以使用; 腳本傳入的路徑由 AutoJs6 開啟後交給插件, 插件處理腳本檔案不需要儲存權限. 插件內置檢視器, 可以從啟動器, AutoJs6 的檔案管理器, 其他應用程式的 "開啟方式" 或腳本 (`pdf.view`) 開啟.

> AutoJs6 腳本的 `pdf` 模組與檔案管理器入口需要 AutoJs6 6.8.1 (內部版本號 6641) 或更新版本, 較舊的 AutoJs6 會在插件中心把本插件顯示為不相容; 工具箱與檢視器不依賴 AutoJs6, 可以單獨使用.

******

### 功能亮點

******

- 讀取: 頁數, 頁面尺寸, 中繼資料, 加密狀態與權限; 按頁擷取文字與大綱 (書籤); 搜尋文字並傳回頁碼與位置; 列出並匯出內嵌圖片.
- 渲染: 把頁面渲染為 `Image` 物件或 PNG / JPEG / WebP 檔案, 預設 144 dpi, 也可以按 dpi, 縮放比例或目標寬高指定.
- 頁面操作: 合併 (保留書籤), 拆分, 抽取, 刪除, 重排與旋轉頁面; 頁碼從 1 開始, 範圍寫作 `'1-3,5,8-'`.
- 生成: 圖片轉 PDF (JPEG 直接嵌入, 不重新編碼), 文字轉 PDF (可指定字型), HTML 轉 PDF (不載入遠端網址).
- 加密: 以密碼開啟加密文件, 以 AES-256 加密並設定權限, 移除加密.
- 表格: 列出表格欄位 (文字框, 勾選框, 單選按鈕, 組合框與清單框) 及其目前的值, 按欄位名稱填寫, 可選擇平面化為普通頁面內容; `pdf.info` 傳回表格欄位數.
- 水印與頁碼: 加上文字或圖片水印, 頁碼, 頁眉與頁腳, 可以設定位置, 旋轉, 不透明度, 平鋪與適用的頁面; 文字中可用 `{page}`, `{total}`, `{date}` 與 `{name}` 佔位.
- 可搜索 PDF: 借助 OCR 插件識別掃描頁並寫入不可見的文字層, 之後可以搜尋, 選取與複製文字, 再次讀取文字也不必重新識別; 腳本自行識別的結果可以用 `pdf.addTextLayer` 寫入.
- 壓縮: 按設定的解像度與品質重新編碼內嵌圖片, 壓縮未壓縮的資料流, 可選擇移除元數據; 提供 low / medium / high 三檔預設, 亦可以自訂數值, 結果列出壓縮前後的大小.
- 文件屬性與書籤: 修改標題, 作者, 主題, 關鍵字與建立程式; 讀出書籤 (大綱) 修改後寫回, 或全部清除.
- 檢視器: 連續捲動, 雙指縮放, 縮圖, 大綱, 搜尋醒目標示, 跳頁, 密碼輸入, 夜間模式與全螢幕閱讀; 其他應用程式可以用 "開啟方式" 或分享把 PDF 交給它; 腳本可以開啟檢視器並接收翻頁與關閉事件.
- 工具箱: 合併, 拆分, 轉圖片, 圖片轉 PDF, 加密, 解密, 擷取文字, 頁面整理, 加上水印, 加上頁碼 (兩者都附可翻頁的單頁預覽), 壓縮, 填寫表格與文件屬性 13 項工具, 水印, 頁碼與壓縮可以自訂數值; 任務在背景依次執行, 通知欄顯示進度; 結果頁可以把這次處理寫成 Rhino, Node.js 或 Bun 腳本; 最近檔案也會記住用系統檔案選擇器開啟的檔案.
- AutoJs6 檔案管理器: 點按 PDF 即在插件檢視器中開啟, 壓縮檔內不超過 512 MiB 的 PDF 也能直接開啟; 選單提供合併, 拆分, 壓縮, 轉圖片, 加上水印, 擷取文字, 加密與移除密碼, 亦可以借助 OCR 插件生成可搜索 PDF.
- OCR: `pdf.text(path, { ocr: true })` 借助已安裝的 OCR 插件 (ML Kit OCR, Paddle OCR 或 Rapid OCR) 識別沒有文字層的掃描頁.
- Node.js 與 Bun: Node.js Runtime 2.2.0 與 Bun Runtime 0.23.0 起提供同樣的 `pdf` 模組, 方法傳回 Promise, 可以用 `signal` 或 `timeoutMs` 中止呼叫.

******

### 使用方法

******

1. 從 [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases) 下載 APK 並安裝. 所有 ABI 共用一個安裝套件.
2. 如需在 AutoJs6 腳本或檔案管理器中使用, 請將 AutoJs6 升級到 6.8.1 (內部版本號 6641) 或更新版本, 並確認插件中心中本插件已啟用.
3. 如需使用插件內置的工具箱, 開啟插件並按提示授予所有檔案存取權限; 腳本操作不需要此權限.

> 插件支援 Android 7.0 (API 24) 或以上的裝置. PDF 處理全部使用系統 API 與純 Java 程式庫, 唯一的原生程式庫來自 Jetpack Compose, 已按 16 KB 記憶體分頁對齊, 因此所有 ABI 共用一個安裝套件.

******

### 相容性

******

- Android 7.0 (API 24) 或以上, 所有 ABI 共用一個安裝套件; 工具箱與檢視器不依賴 AutoJs6.
- AutoJs6 腳本與檔案管理器: 需要 AutoJs6 6.8.1 (內部版本號 6641) 或更新版本.
- 1.1.0 新增的腳本方法 (表格, 水印與頁碼, 可搜索 PDF, 壓縮, 文件屬性與書籤): 需要 AutoJs6 6.8.1 或更新版本與本插件 1.1.0 或更新版本, Node.js Runtime 與 Bun Runtime 的版本要求與其他方法相同; 插件仍為 1.0.x 時這些方法會報 `UNSUPPORTED` 並提示到插件中心更新, 不會寫出檔案, 其他方法照常可用.
- AutoJs6 打包的應用程式: 需由 AutoJs6 6.8.1 (內部版本號 6641) 或更新版本打包, 執行的裝置上需安裝本插件; 打包時自動加入 `org.autojs.plugin.pdf.permission.RUNTIME` 權限, 不需要修改 `project.json` (Node.js 專案需聲明 `files`, Bun 專案需聲明 `pdf.control`).
- Node.js: 需要 Node.js Runtime 插件 2.2.0 或以上, 以 `require('autojs6:pdf')` 載入; 專案在 `project.json` 的 `node.permissions` 中聲明 `files`, 寫入檔案另需 `files.write`, 識別文字另需 `ocr`, 開啟檢視器另需 `ui`.
- Bun: 需要 Bun Runtime 插件 0.23.0 或以上 (Android 13 或以上), 透過 `host.pdf` 使用; 在 Bun Runtime 的設定頁開啟 `pdf.control`, 識別文字另需 `ocr.control`.
- OCR: 識別掃描頁文字與生成可搜索 PDF 需另外安裝 OCR 插件 (ML Kit OCR, Paddle OCR 或 Rapid OCR); 未安裝時這兩項會報 `OCR_UNAVAILABLE`, 其他功能不受影響.

各版本的運行要求與功能範圍:

| 項目 | 1.0.0 | 1.1.0 |
| --- | --- | --- |
| Android | 7.0 (API 24) 或以上 | 7.0 (API 24) 或以上 |
| AutoJs6 | 6.8.1 (內部版本號 6641) 或以上 | 6.8.1 (內部版本號 6641) 或以上 |
| Node.js Runtime | 2.2.0 或以上 | 2.2.0 或以上 |
| Bun Runtime | 0.23.0 或以上 (Android 13 或以上) | 0.23.0 或以上 (Android 13 或以上) |
| OCR 插件 (可選) | 識別掃描頁 | 識別掃描頁, 生成可搜索 PDF |
| 腳本方法 | 讀取, 渲染, 頁面操作, 生成, 加密, OCR, 檢視器 | 另加表格, 水印與頁碼, 可搜索 PDF, 壓縮, 文件屬性與書籤 |
| 工具箱 | 8 項工具 | 13 項工具, 可自訂數值, 可生成腳本 |
| AutoJs6 檔案管理器選單 | 6 項 | 9 項 (另加壓縮, 加上水印, 生成可搜索 PDF) |

******

### 腳本 API

******

AutoJs6 腳本透過全域模組 `pdf` (也可寫作 `$pdf`) 使用這些功能 (完整說明見 [AutoJs6 文件 > PDF](https://docs.autojs6.com/#/pdf)):

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

// 1.1.0
pdf.fillForm("./form.pdf", "./form-filled.pdf", { "applicant.name": "AutoJs6", "applicant.agree": true }, { flatten: true });
pdf.watermark("./report.pdf", "./report-draft.pdf", { text: "DRAFT" });
pdf.stamp("./report.pdf", "./report-numbered.pdf", { pageNumbers: { format: "{page} / {total}" } });
pdf.compress("./photos.pdf", "./photos-small.pdf", { preset: "low" });
pdf.setInfo("./report.pdf", "./report-info.pdf", { title: "Report", author: "AutoJs6" });

let session = pdf.view("./book.pdf", { page: 5, search: "AutoJs6" });
session.on("page", (e) => console.log("page", e.page));
session.on("close", () => console.log("closed"));
```

Node.js 與 Bun 中同名方法傳回 Promise (Bun 透過 `host.pdf` 使用):

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

- 便捷方法 `pdf.info` / `pdf.text` / `pdf.render` / `pdf.merge` / `pdf.split` / `pdf.fromImages` 等每次自行開啟與關閉文件; `pdf.open(path, options)` 傳回 `PdfDocument`, 多次操作共用一次開啟, 每個腳本最多同時開啟 8 份文件.
- 每個方法都有阻塞形式與傳回 Promise 的 `xxxAsync` 形式, 兩者參數與結果相同; 阻塞形式不能在 UI 執行緒呼叫 (報 `INVALID_ARGUMENT`), 介面腳本請用 `xxxAsync`; 寫入檔案的方法接受 `onProgress`, Promise 帶有 `cancel()`; 取消或停止腳本時不會留下寫了一半的檔案.
- `pdf.merge` 的 `displayNames` 為每個輸入的書籤項命名: `null` 取檔案名稱 (去掉 `.pdf`), 空白字串取該文件的標題 (沒有標題時為 `Document <n>`); 選項超過約 63 KiB 時報 `LIMIT_EXCEEDED`, 不寫入輸出.
- `pdf.fromHtml` 預設執行頁面中的腳本, `javaScript: false` 可關閉; 不載入任何外部內容, 網絡請求以空內容回應並記入 `warnings`.
- 1.1.0 的寫出方法 (例如 `pdf.fillForm`, `pdf.stamp`, `pdf.compress`) 與其他寫出方法一樣可以省略輸出路徑 (或傳入 `null`), 結果寫到 `Documents/3-Paper PDF/`; `PdfDocument` 的同名方法沿用開啟時的密碼, 選項中不能再寫 `password`; 寫出檔案的結果另有與 `warnings` 一一對應的 `warningCodes`, 方便腳本按代碼處理.
- `pdf.view(path, options)` 開啟檢視器並傳回 `PdfViewSession`, 可以監聽 `page` / `close` 事件, 呼叫 `goTo(page)`, `state()`, `close()`; AutoJs6 不在前台時 (Android 10 或以上且沒有懸浮窗權限) 報 `UI_REQUIRES_FOREGROUND`.
- Node.js 與 Bun: 全部方法傳回 Promise; `signal` (AbortSignal) 與 `timeoutMs` 可中止呼叫, 均以 `CANCELLED` 拒絕, 逾時的 `cause` 為 `TimeoutError`; `timeoutMs` 須為大於 0 且不超過 2147483647 的有限數, 小數向上取整.
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

#### 插件中心為甚麼顯示本插件不相容?

本插件的腳本模組與檔案管理器入口需要 AutoJs6 6.8.1 (內部版本號 6641) 或更新版本. 升級 AutoJs6 後即可啟用; 在此之前工具箱與檢視器照常可用.

#### AutoJs6 檔案管理器能直接開啟壓縮檔內的 PDF 嗎?

可以. 壓縮檔內不超過 512 MiB 的 PDF 可以直接在插件檢視器中開啟, 更大的請先解壓.

#### 最近檔案中的檔案開不了怎麼辦?

用系統檔案選擇器開啟的檔案會保留讀取授權, 重新啟動後仍能直接開啟. 檔案被刪除, 移動或授權被撤銷後, 點按該項目會顯示提示並將它從清單中移除, 請用 "開啟 PDF" 重新選擇.

#### 填寫的表格值在檢視器中為甚麼看不到?

在 Android 14 或更早版本上, 插件檢視器只有平面化後才會顯示填寫的值, 其他 PDF 應用程式不受影響. 需要在檢視器中查看時, 填寫時開啟平面化 (`flatten: true`, 或 "填寫表格" 工具中的 "平面化欄位"); 平面化後的欄位無法再編輯.

#### 壓縮後的檔案為甚麼沒有變小?

壓縮主要是縮小內嵌圖片. 圖片已經足夠小, 或者是插件不會重新編碼的類型 (例如 JPEG 2000, CMYK 或 1 位元圖片) 時效果有限; 結果不比原檔案小時仍會寫出, 並在結果中提示, 可以保留原檔案.

******

### 權限與安全

******

插件在設計上盡量收窄權限範圍:

- 受保護的入口: 能力服務, 檔案管理器入口服務與啟動入口都受 AutoJs6 插件權限 (`org.autojs.permission.PLUGIN`) 保護, 其他應用程式無法直接呼叫. 供 AutoJs6 打包的應用程式使用的服務與檢視器入口需要 `org.autojs.plugin.pdf.permission.RUNTIME` 權限 (打包時自動加入); 經這兩個入口開啟的文件與檢視器工作階段只允許建立它們的應用程式呼叫, 檔案同樣以檔案描述符傳入.
- 儲存空間: 腳本的檔案以檔案描述符傳入, 插件不需要為腳本申請儲存權限; 所有檔案存取權限只用於工具箱與檢視器中你選擇的檔案.
- HTML 轉 PDF: 插件不載入任何外部內容 (網絡請求以空內容回應, 本機檔案與 `content:` 位址被禁止, 頁面不能跳轉); 頁面中的腳本可以用 `javaScript: false` 關閉.
- 最近檔案: 用系統檔案選擇器開啟的檔案保留讀取授權, 以便下次直接開啟, 最多 20 個; 項目被擠出, 移除或清除時隨即釋放授權. 其他應用程式經 "開啟方式" 或分享交來的檔案只有一次性授權, 不會進入最近檔案.
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

#### v1.1.0

_2026/11/20_

- `提示` 新增的腳本方法與檔案管理器的 "生成可搜索 PDF" 需要 AutoJs6 6.8.1 或更新版本, 在 Node.js 或 Bun 腳本中使用另需 Node.js Runtime 2.2.0 或 Bun Runtime 0.23.0 或更新版本; 工具箱的新工具不依賴 AutoJs6
- `新增` pdf.forms 與 pdf.fillForm: 讀取表格欄位 (文字框, 勾選框, 單選按鈕, 組合框, 清單框) 並按欄位名稱填寫, 可以平面化為普通頁面內容; pdf.info 新增表格欄位數
- `新增` pdf.watermark 與 pdf.stamp: 加上文字或圖片水印, 頁碼, 頁眉與頁腳, 可設定位置, 旋轉, 不透明度, 平鋪與適用的頁面
- `新增` pdf.makeSearchable 借助 OCR 插件為掃描頁寫入不可見的文字層, 生成可以搜尋和複製文字的 PDF; pdf.addTextLayer 寫入腳本自行識別的結果
- `新增` pdf.compress 重新編碼內嵌圖片並壓縮資料流, 可移除元數據, 提供 low / medium / high 三檔預設, 亦可以自訂解像度與品質; 結果列出壓縮前後的大小
- `新增` pdf.setInfo 修改標題, 作者, 主題, 關鍵字與建立程式等文件屬性, pdf.setOutline 按 pdf.outline 傳回的結構改寫或清除書籤
- `新增` 新方法都有 Async 形式與 PdfDocument 的同名方法, 在 Node.js, Bun 與 AutoJs6 打包的應用程式中同樣可用; 寫出檔案的結果另有 warningCodes, 方便腳本按代碼處理提示
- `新增` 工具箱新增 "為 PDF 加上水印" (附單頁預覽), "加上頁碼", "壓縮 PDF", "填寫表格" 與 "文件屬性" 5 項工具, 共 13 項; 水印, 頁碼與壓縮除預設外亦可以輸入自訂數值
- `新增` 水印與頁碼工具的單頁預覽可以翻到文件的任一頁, 預覽中的頁碼與總頁數隨所看的頁面變化, 與生成的結果相同; 不在所選頁碼範圍內的頁面按原樣顯示並註明
- `新增` 工具箱與檔案管理器動作的結果頁可以把這次處理寫成 Rhino, Node.js 或 Bun 腳本, 並可 "複製腳本", "儲存為 .js" 或 "在 AutoJs6 中開啟"; 腳本中的密碼留作 null, 需自行填寫
- `新增` AutoJs6 檔案管理器的 PDF 選單新增 "壓縮 PDF" 與 "為 PDF 加上水印", 結果儲存在原檔案旁; 另有借助 OCR 插件的 "生成可搜索 PDF"
- `新增` 檢視器支援全螢幕閱讀: 單擊頁面收起工具列時, 頁碼與系統狀態列, 導航列一併隱藏 (從螢幕邊緣向內滑動可暫時顯示系統列), 再次單擊即恢復; 設定頁新增 "檢視器" 部分, 可分別關閉 "全螢幕時隱藏頁碼" 與 "全螢幕時隱藏系統列"
- `修復` 部分系統字體下 (例如 ColorOS 15), pdf.fromText 生成的 PDF 中的部分連字號 (如 "3-Paper" 與 "A-B" 中的) 在提取文字或搜尋時變成空字符
- `修復` 檢視器開啟文件時第一頁的頂部被頂部工具列遮住, 翻到最後一頁時末尾被縮圖列遮住; 現在頁面在文件兩端讓開工具列, 收起或顯示工具列時頁面不再移動
- `修復` 外觀跟隨 AutoJs6, 而系統的語言或夜間模式與 AutoJs6 不同時, 開啟插件或在設定中更改外觀後, 介面偶爾先按系統的語言與夜間模式顯示一下 (例如閃過一次黑色背景) 再變回來
- `優化` 發行歷史頁改為按版本展開或收起: 收起時顯示日期與各類別的項目數, 展開後每個類別只標示一次, 可一次展開或收起全部, 打開時展開最新版本; 頁面標題由 "版本記錄" 改為 "發行歷史", 副標題為插件名稱, 設定頁 "關於" 部分與更新對話框中的入口也隨之改名
- `優化` 工具結果頁的處理提示改為按介面語言顯示, 此前只顯示英文
- `優化` 插件中心中的使用說明重新編寫, 分為開始使用, 主要功能, 在腳本中使用, 運行要求與常見問題五部分
- `優化` 工具的結果頁與失敗頁按左上角的返回或系統返回鍵會回到選項頁, 已選的檔案與填寫的內容都會保留, 修改後可以再次生成, 新結果自動編號, 不會覆蓋上一次的結果; 在選項頁再返回才離開工具

#### v1.0.0

_2026/11/14_

- `提示` AutoJs6 腳本的 pdf 模組與檔案管理器入口需要 AutoJs6 6.8.1 (內部版本號 6641) 或更新版本; 較舊的 AutoJs6 在插件中心顯示本插件不相容, 工具箱與檢視器仍可單獨使用
- `提示` 在 Node.js 或 Bun 腳本中使用需要 Node.js Runtime 2.2.0 或 Bun Runtime 0.23.0 或更新版本
- `新增` AutoJs6 腳本可以使用 pdf 模組 (也可寫作 $pdf): 讀取 PDF 的資訊, 文字, 大綱與圖片, 搜尋文字, 把頁面渲染為圖片, 合併, 拆分, 抽取, 刪除, 重排與旋轉頁面, 以圖片, 文字或 HTML 生成 PDF, 以及加密與解密; 每個方法都有阻塞與 Async 兩種形式, 可以回報進度並取消
- `新增` pdf.text 的 ocr 選項借助已安裝的 OCR 插件 (ML Kit OCR, Paddle OCR 或 Rapid OCR) 識別沒有文字層的掃描頁
- `新增` pdf.view 在插件檢視器中開啟 PDF, 腳本可以接收翻頁與關閉事件, 也可以跳到指定頁或關閉檢視器
- `新增` Node.js 與 Bun 腳本也能使用 pdf 模組, 方法傳回 Promise, 可以用 signal 或 timeoutMs 中止呼叫
- `新增` 由 AutoJs6 6.8.1 或更新版本打包的應用程式也能使用 pdf 模組, 打包時自動加入所需權限; 開啟的文件與檢視器工作階段只允許建立它們的應用程式呼叫
- `新增` 檢視器: 連續捲動, 雙指縮放, 縮圖, 大綱, 搜尋醒目標示, 跳頁, 密碼輸入與夜間模式; 可以從啟動器開啟, 也可以在其他應用程式中用 "開啟方式" 或分享交給本插件
- `新增` AutoJs6 檔案管理器: 點按 PDF 即在插件檢視器中開啟, 壓縮檔內不超過 512 MiB 的 PDF 也能直接開啟; 選單提供合併, 拆分, 轉圖片, 提取文字, 加密與移除密碼
- `新增` 首頁: 工具入口, 任務進度, 最近檔案, 以及所有檔案存取權限的授權入口 (僅工具箱需要)
- `新增` 最近檔案會記住用系統檔案選擇器開啟的檔案 (最多 20 個), 重新啟動後仍能直接開啟; 檔案已被刪除或無法存取時會顯示提示並從清單中移除
- `新增` 工具箱提供 8 項工具: 合併, 拆分 (按頁碼範圍或每 n 頁), PDF 轉圖片 (解像度, 格式, 頁碼), 圖片轉 PDF (頁面大小, 方向, 頁邊距), 加密, 移除密碼, 提取文字 (另存為 TXT), 以及透過縮圖旋轉, 刪除與重新排列頁面; 處理邏輯與檔案管理器入口相同
- `新增` 工具箱任務在前台服務中依次執行, 離開工具頁面後繼續運行; 通知欄顯示進度並可取消, 完成後可以在檢視器中開啟或分享結果
- `新增` 內置檔案瀏覽器, 可以多選檔案與新增資料夾; 亦可以改用系統檔案選擇器
- `新增` 設定頁: 預設輸出目錄 (Documents/3-Paper PDF), PDF 轉圖片的預設解像度, 語言, 夜間模式, 主題色, 啟動器圖示, 權限狀態, 發行歷史與檢查更新
- `新增` 檢查更新: 可在設定頁手動檢查, 亦可以在開啟首頁時自動檢查 (每 12 小時最多一次); 版本資訊來自官方插件索引
- `新增` 啟動器與插件中心圖示各有亮暗兩套; 啟動器預設使用自適應自動圖示 (隨系統亮暗切換), 另已內置自適應亮色, 自適應暗色與透明背景三種樣式, 可以在設定頁切換
- `新增` 所有 ABI 共用一個 APK: PDF 處理不含原生庫, 唯一的原生庫來自 Jetpack Compose, 已按 16 KB 記憶體分頁對齊
- `新增` 10 種語言的 README, 插件中心說明與更新日誌, 由 JSON 來源檔統一生成
- `依賴` 附加 PDFBox-Android 版本 2.0.27.0 (文字, 大綱, 圖片, 頁面操作與加密)
- `依賴` 附加 Jetpack Compose BOM 版本 2026.09.00 (插件畫面)
- `依賴` 附加 Navigation Compose 版本 2.10.2
- `依賴` 附加 Material Color Utilities (MaterialKolor) 版本 4.1.1 (插件畫面的主題色)
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
