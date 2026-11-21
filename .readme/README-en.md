<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-paper-pdf-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>Read, render, merge, split, generate, compress and encrypt PDF files, fill in forms and add watermarks, with a built-in viewer and file manager entries</p>

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

3-Paper PDF is the PDF plugin of AutoJs6, and also a PDF toolbox and viewer that works on its own without AutoJs6. It reads the information, text, outline and embedded images of a PDF and renders pages to images; it merges, splits, extracts, deletes, reorders and rotates pages; it generates PDF files from images, text or HTML, and encrypts or decrypts documents. Since 1.1.0 it also reads and fills in forms, adds watermarks, page numbers, headers and footers, makes searchable PDF files with an OCR plugin, reduces the file size, and changes the document properties and the outline.

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
- Forms: list the form fields (text fields, check boxes, radio buttons, combo boxes and list boxes) with their current values, fill them in by field name and optionally flatten them into ordinary page content; `pdf.info` returns the number of form fields.
- Watermarks and page numbers: add text or image watermarks, page numbers, headers and footers, with position, rotation, opacity, tiling and the pages they apply to; texts can use the placeholders `{page}`, `{total}`, `{date}` and `{name}`.
- Searchable PDF: recognize scanned pages with an OCR plugin and write an invisible text layer, so the text can be searched, selected and copied, and reading the text later needs no new recognition; results a script recognized itself can be written with `pdf.addTextLayer`.
- Compression: re-encode embedded images at the chosen resolution and quality, compress uncompressed streams and optionally remove metadata; the low / medium / high presets or custom values are available, and the result gives the size before and after.
- Document properties and outline: change the title, author, subject, keywords and creator; read the outline (bookmarks), change it and write it back, or remove it.
- Viewer: continuous scrolling, pinch zoom, thumbnails, outline, search highlights, go to page, password prompt, night mode and full-screen reading; other apps can hand PDF files to it with "Open with" or sharing; scripts can open the viewer and receive page change and close events.
- Toolbox: 13 tools to merge, split, convert to images, convert images to PDF, encrypt, decrypt, extract text, organize pages, add a watermark, add page numbers (both with a one-page preview that turns through the pages), compress, fill in forms and edit the document properties, with custom values for the watermark, page numbers and compression; tasks run one after another in the background, with the progress in the notification; the result page can write the run as a Rhino, Node.js or Bun script; the recent files also remember files opened with the system file picker.
- AutoJs6 file manager: tapping a PDF opens it in the plugin viewer, and PDF files of up to 512 MiB inside archives open directly too; the menu offers merge, split, compress, convert to images, add a watermark, extract text, encrypt and remove the password, and can make a searchable PDF with an OCR plugin.
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
- Script methods new in 1.1.0 (forms, watermarks and page numbers, searchable PDF, compression, document properties and outline): require AutoJs6 6.8.1 or later and this plugin 1.1.0 or later, with the same Node.js Runtime and Bun Runtime versions as the other methods; while the plugin is still 1.0.x, these methods fail with `UNSUPPORTED` and a hint to update in the Plugin Center, write no file, and the other methods work as usual.
- Apps packaged by AutoJs6: must be packaged by AutoJs6 6.8.1 (internal build 6641) or later, and this plugin must be installed on the device that runs them; packaging adds the permission `org.autojs.plugin.pdf.permission.RUNTIME`, so `project.json` needs no change (a Node.js project declares `files`, a Bun project declares `pdf.control`).
- Node.js: requires the Node.js Runtime plugin 2.2.0 or later, loaded with `require('autojs6:pdf')`; the project declares `files` in `node.permissions` of `project.json`, plus `files.write` to write files, `ocr` to recognize text and `ui` to open the viewer.
- Bun: requires the Bun Runtime plugin 0.23.0 or later (Android 13 and later), used through `host.pdf`; turn on `pdf.control` on the settings screen of Bun Runtime, plus `ocr.control` to recognize text.
- OCR: recognizing scanned pages and making searchable PDF files need an OCR plugin (ML Kit OCR, Paddle OCR or Rapid OCR); without one, these two fail with `OCR_UNAVAILABLE`, and the other features are not affected.

Requirements and features by version:

| Item | 1.0.0 | 1.1.0 |
| --- | --- | --- |
| Android | 7.0 (API 24) or later | 7.0 (API 24) or later |
| AutoJs6 | 6.8.1 (internal build 6641) or later | 6.8.1 (internal build 6641) or later |
| Node.js Runtime | 2.2.0 or later | 2.2.0 or later |
| Bun Runtime | 0.23.0 or later (Android 13 or later) | 0.23.0 or later (Android 13 or later) |
| OCR plugin (optional) | Recognize scanned pages | Recognize scanned pages, make searchable PDF files |
| Script methods | Reading, rendering, page operations, generation, encryption, OCR, viewer | Plus forms, watermarks and page numbers, searchable PDF, compression, document properties and outline |
| Toolbox | 8 tools | 13 tools, custom values, script generation |
| AutoJs6 file manager menu | 6 actions | 9 actions (plus compress, add watermark, make searchable PDF) |

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
- The writing methods of 1.1.0 (for example `pdf.fillForm`, `pdf.stamp` and `pdf.compress`) can omit the output path (or pass `null`) like the other writing methods, and the result goes to `Documents/3-Paper PDF/`; the methods of the same name on `PdfDocument` reuse the password given when opening and do not accept `password` in their options; results of methods that write files also carry `warningCodes`, one code per entry of `warnings`, so scripts can handle them by code.
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

#### Why are the filled-in form values not shown in the viewer?

On Android 14 and earlier, the plugin viewer shows filled-in values only after flattening; other PDF apps show them either way. To see them in the viewer, flatten when filling in (`flatten: true`, or "Flatten fields" in the "Fill in form" tool); flattened fields can no longer be edited.

#### Why did the compressed file not get smaller?

Compression mainly shrinks embedded images. When the images are already small enough, or of a type the plugin does not re-encode (for example JPEG 2000, CMYK or 1-bit images), the effect is limited; an output that is not smaller is still written, with a hint in the result, and you can keep the original file.

******

### Permissions and security

******

The plugin keeps its permission surface as small as possible:

- Protected entries: the capability service, the file manager entry service and the activation entry require the AutoJs6 plugin permission (`org.autojs.permission.PLUGIN`), so other apps cannot call them directly. The service and viewer entry for apps packaged by AutoJs6 require `org.autojs.plugin.pdf.permission.RUNTIME` (added when the app is packaged); documents and viewer sessions opened through them answer only the app that created them, and files are passed as file descriptors here too.
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

#### v1.1.0

_2026/11/20_

- `Hint` The new script methods and "Make searchable PDF" of the file manager require AutoJs6 6.8.1 or later, and use in Node.js or Bun scripts requires Node.js Runtime 2.2.0 or Bun Runtime 0.23.0 or later; the new toolbox tools do not depend on AutoJs6
- `Feature` pdf.forms and pdf.fillForm read form fields (text fields, check boxes, radio buttons, combo boxes, list boxes) and fill them in by field name, optionally flattened into ordinary page content; pdf.info adds the number of form fields
- `Feature` pdf.watermark and pdf.stamp add text or image watermarks, page numbers, headers and footers, with position, rotation, opacity, tiling and the pages they apply to
- `Feature` pdf.makeSearchable writes an invisible text layer into scanned pages with an OCR plugin, making PDF files whose text can be searched and copied; pdf.addTextLayer writes results a script recognized itself
- `Feature` pdf.compress re-encodes embedded images and compresses streams, can remove metadata, and offers the low / medium / high presets or custom resolution and quality; the result gives the size before and after
- `Feature` pdf.setInfo changes document properties such as the title, author, subject, keywords and creator, and pdf.setOutline rewrites or removes the outline in the shape pdf.outline returns
- `Feature` Every new method has an Async form and a method of the same name on PdfDocument, and works in Node.js, Bun and apps packaged by AutoJs6 too; results of methods that write files also carry warningCodes, so scripts can handle the hints by code
- `Feature` The toolbox adds 5 tools, "Add watermark to PDF" (with a one-page preview), "Add page numbers", "Compress PDF", "Fill in form" and "Document properties", 13 in all; watermark, page numbers and compression also accept custom values besides the presets
- `Feature` The one-page preview of the watermark and page numbers tools turns to any page of the document; the page number and the page count in the preview follow the page shown, as in the result, and a page outside the chosen pages shows as it is with a note
- `Feature` The result page of toolbox and file manager actions can write the run as a Rhino, Node.js or Bun script, with "Copy script", "Save as .js" and "Open in AutoJs6"; passwords are left as null in the script for you to fill in
- `Feature` The PDF menu of the AutoJs6 file manager adds "Compress PDF" and "Add watermark to PDF", which save the result next to the original file, and "Make searchable PDF" with an OCR plugin
- `Feature` The viewer reads full screen: the tap on the page that hides the bars also hides the page number, the status bar and the navigation bar (swipe in from the edge of the screen to show the system bars for a moment), and another tap brings them back; the new "Viewer" section of the settings turns "Hide the page number in full screen" and "Hide the system bars in full screen" off one by one
- `Fix` Issue where some hyphens (such as those in "3-Paper" and "A-B") in PDFs generated by pdf.fromText turned into null characters in text extraction and search with some system fonts (such as on ColorOS 15)
- `Fix` Issue where the top bar of the viewer covered the top of the first page when a document opened, and the thumbnail bar covered the end of the last page; the pages now keep clear of the bars at both ends of the document and no longer move when the bars hide or show
- `Improvement` The release history page now expands and collapses by version: a collapsed version shows its date and the number of entries per category, an expanded one names each category once; all versions can be expanded or collapsed at once, and the newest one opens expanded; the page is titled "Release history" (formerly "Version history") with the plugin name as the subtitle, and its entries in the "About" section of the settings and in the update dialog are renamed to match
- `Improvement` The processing hints on the tool result page are now shown in the interface language instead of English only
- `Improvement` Rewrote the instructions shown in the Plugin Center in five parts: getting started, main features, using it in scripts, requirements and FAQ
- `Improvement` Back on the result or failure page of a tool, with the arrow of the top bar or the system back key, now returns to the options with the chosen files and everything typed kept, so a change can run again; the new result gets a number instead of replacing the previous one, and back on the options leaves the tool

#### v1.0.0

_2026/11/14_

- `Hint` The pdf module of AutoJs6 scripts and the file manager entries require AutoJs6 6.8.1 (internal build 6641) or later; older AutoJs6 versions show the plugin as incompatible in the Plugin Center, and the toolbox and the viewer still work on their own
- `Hint` Use in Node.js or Bun scripts requires Node.js Runtime 2.2.0 or Bun Runtime 0.23.0 or later
- `Feature` AutoJs6 scripts get the pdf module (also available as $pdf): read the information, text, outline and images of PDF files, search text, render pages to images, merge, split, extract, delete, reorder and rotate pages, generate PDF files from images, text or HTML, and encrypt and decrypt; every method has a blocking form and an Async form that can report progress and be cancelled
- `Feature` The ocr option of pdf.text recognizes scanned pages without a text layer with an installed OCR plugin (ML Kit OCR, Paddle OCR or Rapid OCR)
- `Feature` pdf.view opens a PDF in the plugin viewer; scripts receive page change and close events and can go to a page or close the viewer
- `Feature` Node.js and Bun scripts can use the pdf module too; its methods return Promises, and calls can be aborted with signal or timeoutMs
- `Feature` Apps packaged by AutoJs6 6.8.1 or later can use the pdf module too, with the needed permission added when packaging; documents and viewer sessions they open answer only the app that created them
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
