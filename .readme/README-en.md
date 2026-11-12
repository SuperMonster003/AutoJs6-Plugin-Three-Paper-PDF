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

Processing runs inside the plugin with the `PdfRenderer` of Android and the pure Java PDFBox-Android, so the AutoJs6 host stays small. AutoJs6 scripts use these features through the global module `pdf`; AutoJs6 opens the paths that scripts pass and hands them to the plugin, so the plugin needs no storage permission to process script files. The plugin comes with its own viewer, which opens from the launcher, the AutoJs6 file manager or scripts (`pdf.view`).

> This is a development preview: the AutoJs6 Plugin Center can discover the plugin, but reading, rendering, page operations, the viewer, the toolbox and the `pdf` module are not delivered yet. The text below describes the design goals of 1.0.0; see the roadmap for progress.

******

### Features

******

- Reading: page count, page sizes, metadata, encryption state and permissions; text and outline (bookmarks) per page; text search with page numbers and positions; listing and exporting embedded images.
- Rendering: pages to `Image` objects or PNG / JPEG / WebP files, 144 dpi by default, or by dpi, scale or target width and height.
- Page operations: merge (bookmarks kept), split, extract, delete, reorder and rotate pages; page numbers start at 1 and ranges are written as `'1-3,5,8-'`.
- Generation: images to PDF (JPEG embedded as is, without re-encoding), text to PDF (with a font of your choice) and HTML to PDF (no remote URLs are loaded).
- Encryption: open encrypted documents with a password, encrypt with AES-256 and set permissions, remove encryption.
- Viewer: continuous scrolling, pinch zoom, thumbnails, outline, search highlights, password prompt and night mode; scripts can open the viewer and receive page change and close events.
- Toolbox and file manager: wizards to merge, split, convert to images, convert images to PDF, encrypt / decrypt and extract text; the AutoJs6 file manager opens PDF files directly in the plugin viewer, and its overflow menu offers the same tools.
- OCR: `pdf.text(path, { ocr: true })` calls the AutoJs6 OCR engine for scanned pages without a text layer.

******

### Usage

******

1. Download the APK from [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases) and install it. There is a single package for every ABI.
2. To use the `pdf` module in scripts, update AutoJs6 to internal build 6641 (6.8.1) or later and enable the plugin in the Plugin Center.
3. To use the built-in toolbox, open the plugin and grant all files access when asked; script operations do not need this permission.

> The plugin supports devices running Android 7.0 (API 24) or later. PDF processing uses only system APIs and pure Java libraries; the only native library comes from Jetpack Compose and is aligned to 16 KB memory pages, so a single package serves every ABI.

******

### Script API

******

Scripts use these features through the global module `pdf` (also available as `$pdf`) (1.0.0 design draft; the documentation of the released version prevails):

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

- Convenience methods such as `pdf.info` / `pdf.text` / `pdf.render` / `pdf.merge` / `pdf.split` / `pdf.fromImages` open and close the document on each call; `pdf.open(path, options)` returns a `PdfDocument` that shares one opening across several operations, with at most 8 documents open per script.
- Each method has a blocking form and an `xxxAsync` form that returns a Promise; `xxxAsync` accepts `options.onProgress(progress)` and its Promise has `cancel()`; stopping the script cancels running operations and deletes unfinished output.
- `pdf.view(path, options)` opens the viewer and returns a `PdfViewSession` with the `page` and `close` events and the `goTo(page)` and `close()` methods.
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

******

### Permissions and security

******

The plugin keeps its permission surface as small as possible:

- Protected entries: the capability service, the file manager entry service and the activation entry require the AutoJs6 plugin permission (`org.autojs.permission.PLUGIN`), so other apps cannot call them directly.
- Storage: script files arrive as file descriptors, so the plugin asks for no storage permission on behalf of scripts; all files access is only used for the files you choose in the toolbox and the viewer.
- HTML to PDF: only the given HTML string or local file is rendered; no remote URLs are loaded.
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

- `Hint` Development preview: the AutoJs6 Plugin Center can discover the plugin; reading, rendering, page operations, the viewer, the toolbox and the pdf script module are not delivered yet
- `Hint` Requires AutoJs6 6.8.1 (6641) or later (the exact minimum build will be filled in once the host changes land)
- `Feature` Plugin skeleton: Wake activation protocol, Plugin Center identity service, PDF capability service (contract version 1) and file manager entry service
- `Feature` Home screen with the all files access grant (only the toolbox needs it)
- `Feature` Light and dark icons for the launcher and the Plugin Center; the launcher uses the adaptive automatic icon by default (it follows the system light or dark theme), and the adaptive light, adaptive dark and transparent background styles are built in, to be chosen on the settings screen that comes later
- `Feature` A single APK for every ABI: PDF processing contains no native library; the only native library comes from Jetpack Compose and is aligned to 16 KB memory pages
- `Feature` README, Plugin Center instructions and changelogs in 10 languages, generated from JSON sources
- `Dependency` Attach PDFBox-Android 2.0.27.0 (text, outline, images, page operations and encryption)
- `Dependency` Attach Jetpack Compose BOM 2026.09.00 (the plugin screens)
- `Dependency` Attach Navigation Compose 2.10.2
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
