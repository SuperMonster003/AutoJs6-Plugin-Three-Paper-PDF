3-Paper PDF 為 AutoJs6 腳本提供 `pdf` 模組, 用於讀取, 渲染, 合併, 拆分, 生成與加密 PDF 檔案, 亦能填寫表格, 加上水印與頁碼, 壓縮檔案體積. 它同時是可以單獨使用的 PDF 檢視器與工具箱, 無需安裝 AutoJs6.

### 開始使用

1. 在 AutoJs6 腳本中直接使用全域模組 `pdf`, 範例見下文.
2. 如需在手機上檢視或處理 PDF, 從啟動器開啟 3-Paper PDF, 按提示授予所有檔案存取權限, 然後點按 "開啟 PDF", 或在 "工具" 中選擇所需的處理.
3. 在 AutoJs6 的檔案管理器中點按 PDF 檔案, 即在 3-Paper PDF 的檢視器中開啟.

### 主要功能

- **讀取**: 頁數, 頁面尺寸, 中繼資料與加密狀態; 按頁擷取文字與大綱 (書籤); 搜尋文字; 列出並匯出內嵌圖片.
- **渲染**: 把頁面渲染為圖像物件或 PNG / JPEG / WebP 檔案, 可以按 dpi, 縮放比例或目標寬高指定尺寸.
- **頁面操作**: 合併 (保留書籤), 拆分, 抽取, 刪除, 重排與旋轉頁面.
- **生成與加密**: 把圖片, 文字或 HTML 生成為 PDF; 為文件設定密碼與權限, 或移除密碼.
- **編輯**: 填寫表格, 加上水印, 頁碼與頁眉頁腳, 壓縮檔案體積, 修改標題, 作者等文件屬性與書籤.
- **掃描頁識別**: 借助已安裝的 OCR 插件識別沒有文字層的掃描頁, 亦可以為掃描件寫入文字層, 生成可搜索的 PDF.
- **檢視器**: 連續捲動, 雙指縮放, 縮圖, 大綱, 搜尋醒目標示, 跳頁, 夜間模式與全螢幕閱讀 (單擊頁面收起工具列, 可在設定中選擇是否同時隱藏頁碼與系統列). 其他應用程式可以透過 "開啟方式" 或分享把 PDF 交給它.
- **工具箱**: 合併, 拆分, 壓縮, 加上水印等 13 項工具, 處理完成後可以把同樣的處理寫成腳本. 任務在背景依次執行, 通知欄顯示進度.
- **AutoJs6 檔案管理器**: 點按 PDF 即用檢視器開啟, 壓縮檔內的 PDF 也可直接開啟; 檔案選單提供合併, 拆分, 壓縮, 加上水印等處理, 亦可以生成可搜索 PDF.

### 在腳本中使用

在 AutoJs6 腳本中直接使用全域模組 `pdf`. 頁碼從 1 開始, 頁碼範圍寫作 `'1-3,5,8-'`:

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

- 每個方法都有傳回 Promise 的 `Async` 形式 (例如 `pdf.mergeAsync`). 同步形式不能在 UI 執行緒調用, 介面腳本請使用 `Async` 形式.
- 開啟加密文件時在選項中傳入 `password`. 出錯時拋出帶 `code` 的錯誤, 例如 `PASSWORD_REQUIRED` 或 `PAGE_OUT_OF_RANGE`.
- Node.js 中使用 `require('autojs6:pdf')` 載入, Bun 中透過 `host.pdf` 使用, 方法相同, 均傳回 Promise.

### 運行要求

- Android 7.0 或更高版本. 檢視器與工具箱可以單獨使用, 無需 AutoJs6.
- 腳本中的 `pdf` 模組與檔案管理器入口需要 AutoJs6 6.8.1 或更高版本. 腳本讀寫的檔案由 AutoJs6 開啟, 因此 AutoJs6 需要儲存權限.
- 工具箱與從啟動器開啟的檢視器需要所有檔案存取權限, 腳本處理檔案不需要插件的這項權限.
- AutoJs6 打包的應用程式同樣可以使用 `pdf` 模組: 需由 AutoJs6 6.8.1 或更高版本打包, 運行的裝置上需安裝本插件.
- Node.js 腳本需要 Node.js Runtime 插件 2.2.0 或更高版本, 並在 `project.json` 的 `node.permissions` 中聲明 `files`; Bun 腳本需要 Bun Runtime 插件 0.23.0 或更高版本 (Android 13 或以上), 並在其設定頁開啟 `pdf.control`.
- 識別掃描頁的文字與生成可搜索 PDF 需要另行安裝 OCR 插件 (ML Kit OCR, Paddle OCR 或 Rapid OCR).

### 常見問題

**未安裝 AutoJs6 時是否可用**

可以. 檢視器與工具箱均可單獨使用, 僅在腳本中處理 PDF 時需要 AutoJs6.

**插件中心顯示本插件不相容的原因**

腳本模組與檔案管理器入口需要 AutoJs6 6.8.1 或更高版本, 升級 AutoJs6 後即可啟用; 在此之前檢視器與工具箱照常可用.

**最近檔案中的檔案無法開啟時的處理**

檔案被刪除, 移動或存取授權被撤銷後, 點按該項目會給出提示並將其從清單中移除. 請透過 "開啟 PDF" 重新選擇.

**填寫的表格值在檢視器中不顯示的原因**

在 Android 14 或更早版本上, 內置檢視器只有平面化後才會顯示填寫的值, 其他 PDF 應用程式不受影響. 需要在檢視器中查看時, 填寫時選擇 "平面化欄位".

完整的腳本說明見 [AutoJs6 文檔 > PDF](https://docs.autojs6.com/#/pdf), 其他資訊見 [項目主頁](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF).
