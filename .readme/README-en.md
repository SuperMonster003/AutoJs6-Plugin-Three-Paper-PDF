<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-paper-pdf-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>Read, render, merge, split, generate and encrypt PDF files, with a built-in viewer and file manager entries</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Languages

******

The README.md file is currently available in the following languages:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hant-TW.md)
- English [en] # current
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ar.md)

******

### Introduction

******

3-Paper PDF is the PDF plugin of AutoJs6, and also a PDF toolbox and viewer that works on its own without AutoJs6. It reads the information, text, outline and embedded images of a PDF and renders pages to images; it merges, splits, extracts, deletes, reorders and rotates pages; it generates PDF files from images, text or HTML, and encrypts or decrypts documents.

Processing runs inside the plugin with the `PdfRenderer` of Android and the pure Java PDFBox-Android, so the AutoJs6 host stays small. AutoJs6 scripts use these features through the global module `pdf`, and so do scripts of the Node.js Runtime and Bun Runtime plugins; AutoJs6 opens the paths that scripts pass and hands them to the plugin, so the plugin needs no storage permission to process script files. The plugin comes with its own viewer, which opens from the launcher, the AutoJs6 file manager, "Open with" in other apps or scripts (`pdf.view`).

> The `pdf` module of AutoJs6 scripts and the file manager entries require AutoJs6 6.8.1 (internal build 6641) or later; older AutoJs6 versions show the plugin as incompatible in the Plugin Center. The toolbox and the viewer do not depend on AutoJs6 and work on their own.

******

### Features

******

- Reading: page count, page sizes, metadata, encryption state and permissions; text and outline (bookmarks) per page; text search with page numbers and positions; listing and exporting embedded images.
- Rendering: pages to `Image` objects or PNG / JPEG / WebP files, 144 dpi by default, or by dpi, scale or target width and height.
- Page operations: merge (bookmarks kept), split, extract, delete, reorder and rotate pages; page numbers start at 1 and ranges are written as `'1-3,5,8-'`.
- Generation: images to PDF (JPEG embedded as is, without re-encoding), text to PDF (with a font of your choice) and HTML to PDF (no remote URLs are loaded).
- Encryption: open encrypted documents with a password, encrypt with AES-256 and set permissions, remove encryption.
- Viewer: continuous scrolling, pinch zoom, thumbnails, outline, search highlights, go to page, password prompt and night mode; other apps can hand PDF files to it with "Open with" or sharing; scripts can open the viewer and receive page change and close events.
- Toolbox: 8 tools to merge, split, convert to images, convert images to PDF, encrypt, decrypt, extract text and organize pages; tasks run one after another in the background, with the progress in the notification; the recent files also remember files opened with the system file picker.
- AutoJs6 file manager: tapping a PDF opens it in the plugin viewer, and PDF files of up to 512 MiB inside archives open directly too; the menu offers merge, split, convert to images, extract text, encrypt and remove the password.
- OCR: `pdf.text(path, { ocr: true })` recognizes scanned pages without a text layer with an installed OCR plugin (ML Kit OCR, Paddle OCR or Rapid OCR).
- Node.js and Bun: Node.js Runtime 2.2.0 and Bun Runtime 0.23.0 and later offer the same `pdf` module; its methods return Promises, and calls can be aborted with `signal` or `timeoutMs`.

******

### Usage

******

1. Download the APK from [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases) and install it. There is a single package for every ABI.
2. To use it in AutoJs6 scripts or the file manager, update AutoJs6 to 6.8.1 (internal build 6641) or later and make sure the plugin is enabled in the Plugin Center.
3. To use the built-in toolbox, open the plugin and grant all files access when asked; script operations do not need this permission.

> The plugin supports devices running Android 7.0 (API 24) or later. PDF processing uses only system APIs and pure Java libraries; the only native library comes from Jetpack Compose and is aligned to 16 KB memory pages, so a single package serves every ABI.

******

### Compatibility

******

- Android 7.0 (API 24) and later, a single package for every ABI; the toolbox and the viewer do not depend on AutoJs6.
- AutoJs6 scripts and file manager: require AutoJs6 6.8.1 (internal build 6641) or later.
- Node.js: requires the Node.js Runtime plugin 2.2.0 or later, loaded with `require('autojs6:pdf')`; the project declares `files` in `node.permissions` of `project.json`, plus `files.write` to write files, `ocr` to recognize text and `ui` to open the viewer.
- Bun: requires the Bun Runtime plugin 0.23.0 or later (Android 13 and later), used through `host.pdf`; turn on `pdf.control` on the settings screen of Bun Runtime, plus `ocr.control` to recognize text.
- OCR: recognizing scanned pages needs an OCR plugin (ML Kit OCR, Paddle OCR or Rapid OCR); without one, recognizing scanned pages fails with `OCR_UNAVAILABLE`, and the other features are not affected.

******

### Script API

******

AutoJs6 scripts use these features through the global module `pdf` (also available as `$pdf`; see [AutoJs6 documentation > PDF](https://docs.autojs6.com/#/pdf) for the full reference):

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

In Node.js and Bun, the methods of the same names return Promises (Bun uses them through `host.pdf`):

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

- Convenience methods such as `pdf.info` / `pdf.text` / `pdf.render` / `pdf.merge` / `pdf.split` / `pdf.fromImages` open and close the document on each call; `pdf.open(path, options)` returns a `PdfDocument` that shares one opening across several operations, with at most 8 documents open per script.
- Each method has a blocking form and an `xxxAsync` form that returns a Promise, with the same arguments and results; the blocking form cannot be called on the UI thread (it fails with `INVALID_ARGUMENT`), so UI scripts use `xxxAsync`; methods that write files accept `onProgress`, and their Promise has `cancel()`; cancelling or stopping the script never leaves a half-written file.
- The `displayNames` of `pdf.merge` name the bookmark of each input: `null` takes the file name (without `.pdf`), and a blank string takes the document title (`Document <n>` when there is none); options larger than about 63 KiB fail with `LIMIT_EXCEEDED` and write nothing.
- `pdf.fromHtml` runs the scripts of the page by default, and `javaScript: false` turns them off; no external content is loaded: network requests are answered with empty content and recorded in `warnings`.
- `pdf.view(path, options)` opens the viewer and returns a `PdfViewSession` with the `page` / `close` events and the `goTo(page)`, `state()` and `close()` methods; it fails with `UI_REQUIRES_FOREGROUND` when AutoJs6 is not in the foreground (on Android 10 and later without the overlay permission).
- Node.js and Bun: every method returns a Promise; `signal` (an AbortSignal) and `timeoutMs` abort the call, which rejects with `CANCELLED` in both cases, and a timeout has a `TimeoutError` as its `cause`; `timeoutMs` must be a finite number greater than 0 and at most 2147483647, and fractions are rounded up.
- Failures throw a `PdfError` with a stable `code` such as `PASSWORD_REQUIRED`, `WRONG_PASSWORD` or `PAGE_OUT_OF_RANGE`; `pdf.isAvailable()` checks in advance whether the plugin is available.

******

### FAQ

******

#### Can I use it without AutoJs6?

Yes. The toolbox and the viewer work on their own; AutoJs6 is only needed when scripts process PDF files.

#### Why does the plugin ask for all files access?

Only the toolbox and the viewer opened from the launcher need it, to read and write the files you choose on screen. AutoJs6 opens the paths that scripts pass and hands them to the plugin as file descriptors, so scripts need no storage permission of the plugin.

#### Does the plugin contain native libraries?

PDF processing uses no native library: pages are rendered by the system `PdfRenderer`, and the other features use the pure Java PDFBox-Android. The only native library in the package is `libandroidx.graphics.path.so`, which comes with Jetpack Compose.

#### Why does the Plugin Center show the plugin as incompatible?

The script module and the file manager entries of the plugin require AutoJs6 6.8.1 (internal build 6641) or later. Update AutoJs6 and the plugin can be enabled; until then the toolbox and the viewer work as usual.

#### Can the AutoJs6 file manager open a PDF inside an archive?

Yes. A PDF of up to 512 MiB inside an archive opens directly in the plugin viewer; extract larger ones first.

#### What if a file in the recent files no longer opens?

Files opened with the system file picker keep their read grant and still open directly after a restart. When a file has been deleted or moved, or its grant revoked, tapping the entry shows a message and removes it from the list; choose the file again with "Open a PDF".

******

### Permissions and security

******

The plugin keeps its permission surface as small as possible:

- Protected entries: the capability service, the file manager entry service and the activation entry require the AutoJs6 plugin permission (`org.autojs.permission.PLUGIN`), so other apps cannot call them directly.
- Storage: script files arrive as file descriptors, so the plugin asks for no storage permission on behalf of scripts; all files access is only used for the files you choose in the toolbox and the viewer.
- HTML to PDF: the plugin loads no external content (network requests are answered with empty content, local files and `content:` addresses are blocked, and the page cannot navigate); the scripts of the page can be turned off with `javaScript: false`.
- Recent files: files opened with the system file picker keep their read grant so they open directly next time, for at most 20 files; the grant is released as soon as an entry drops out, is removed or the list is cleared. Files handed over by other apps through "Open with" or sharing have a one-time grant only and stay out of the recent files.
- Network and passwords: the network is only used to check for updates; document passwords are only used for the current operation and are never stored by the plugin.

******

### Engine and dependencies

******

Pages are rendered with the `PdfRenderer` of Android; text, outline, images, page operations and encryption use [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache-2.0, pure Java), and encryption relies on Bouncy Castle (MIT-style license). PDF processing contains no native library and uses no native renderer such as pdfium or MuPDF.

******

### Plugin interface

******

The following information targets AutoJs6 host and plugin developers; the host uses these identifiers to discover the plugin and negotiate capabilities:

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

### Roadmap

******

Plans, design decisions and progress are tracked as a checklist in ROADMAP.md. Unchecked items are plans, not features of the current version.

- [View ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/ROADMAP.md)

******

### Release history

******

#### v1.0.0

_2026/11/12_

- `Hint` The pdf module of AutoJs6 scripts and the file manager entries require AutoJs6 6.8.1 (internal build 6641) or later; older AutoJs6 versions show the plugin as incompatible in the Plugin Center, and the toolbox and the viewer still work on their own
- `Hint` Use in Node.js or Bun scripts requires Node.js Runtime 2.2.0 or Bun Runtime 0.23.0 or later
- `Feature` AutoJs6 scripts get the pdf module (also available as $pdf): read the information, text, outline and images of PDF files, search text, render pages to images, merge, split, extract, delete, reorder and rotate pages, generate PDF files from images, text or HTML, and encrypt and decrypt; every method has a blocking form and an Async form that can report progress and be cancelled
- `Feature` The ocr option of pdf.text recognizes scanned pages without a text layer with an installed OCR plugin (ML Kit OCR, Paddle OCR or Rapid OCR)
- `Feature` pdf.view opens a PDF in the plugin viewer; scripts receive page change and close events and can go to a page or close the viewer
- `Feature` Node.js and Bun scripts can use the pdf module too; its methods return Promises, and calls can be aborted with signal or timeoutMs
- `Feature` Viewer: continuous scrolling, pinch zoom, thumbnails, outline, search highlights, go to page, password prompt and night mode; it opens from the launcher, and other apps can hand PDF files to it with "Open with" or sharing
- `Feature` AutoJs6 file manager: tapping a PDF opens it in the plugin viewer, and PDF files of up to 512 MiB inside archives open directly too; the menu offers merge, split, convert to images, extract text, encrypt and remove the password
- `Feature` Home screen: the tools, task progress, recent files and the all files access grant (only the toolbox needs it)
- `Feature` The recent files remember files opened with the system file picker (up to 20), which still open directly after a restart; a file that has been deleted or can no longer be accessed is reported and removed from the list
- `Feature` The toolbox offers 8 tools: merge, split (by page ranges or every n pages), PDF to images (resolution, format, pages), images to PDF (page size, orientation, margins), encrypt, remove the password, extract text (saved as TXT), and rotating, deleting and reordering pages with thumbnails; they share their processing with the file manager entries
- `Feature` Toolbox tasks run one after another in a foreground service and keep running after you leave the tool screen; the notification shows the progress and can cancel, and the results can be opened in the viewer or shared
- `Feature` A built-in file browser that can select several files and create folders; the system file picker is also available
- `Feature` Settings screen: default output folder (Documents/3-Paper PDF), default resolution of PDF to images, language, night mode, theme color, launcher icon, permission status, release history and update check
- `Feature` Update check: manually on the settings screen, or automatically when the home screen opens (at most once every 12 hours); release information comes from the official plugin index
- `Feature` Light and dark icons for the launcher and the Plugin Center; the launcher uses the adaptive automatic icon by default (it follows the system light or dark theme), and the adaptive light, adaptive dark and transparent background styles are built in, selectable on the settings screen
- `Feature` A single APK for every ABI: PDF processing contains no native library; the only native library comes from Jetpack Compose and is aligned to 16 KB memory pages
- `Feature` README, Plugin Center instructions and changelogs in 10 languages, generated from JSON sources
- `Dependency` Attach PDFBox-Android 2.0.27.0 (text, outline, images, page operations and encryption)
- `Dependency` Attach Jetpack Compose BOM 2026.09.00 (the plugin screens)
- `Dependency` Attach Navigation Compose 2.10.2
- `Dependency` Attach Material Color Utilities (MaterialKolor) 4.1.1 (theme color of the plugin screens)
- `Dependency` Attach AndroidX Core KTX 1.18.0

##### For more release history, refer to

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/assets/doc/CHANGELOG-en.md)

******

### Build

******

This section targets developers who want to build the plugin from source.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:appendDigestToReleasedFiles
py .python\generate_markdown.py --check
py .python\generate_third_party_notices.py --check
py .python\generate_icon_studio.py --check
py .python\check_repository.py
```

******

### License

******

The project code is licensed under the [Mozilla Public License 2.0](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/LICENSE). Third-party components and data are listed in the third-party notices.

- [Third-party notices (THIRD_PARTY_NOTICES.md)](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/THIRD_PARTY_NOTICES.md)

******

### Related links

******

- AutoJs6 project: https://github.com/SuperMonster003/AutoJs6
