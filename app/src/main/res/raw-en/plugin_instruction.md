3-Paper PDF provides the `pdf` module for AutoJs6 scripts to read, render, merge, split, generate and encrypt PDF files. It is also a PDF viewer and toolbox that can be used on its own, without AutoJs6.

### Getting started

1. Use the global `pdf` module directly in AutoJs6 scripts; see the example below.
2. To view or process PDF files on the phone, open 3-Paper PDF from the launcher, grant all files access as prompted, then tap "Open a PDF" or choose the processing you need under "Tools".
3. Tapping a PDF file in the AutoJs6 file manager opens it in the 3-Paper PDF viewer.

### Main features

- **Reading**: page count, page sizes, metadata and encryption state; text and outline (bookmarks) per page; text search; listing and exporting embedded images.
- **Rendering**: pages to image objects or PNG / JPEG / WebP files, sized by dpi, scale or target width and height.
- **Page operations**: merge (bookmarks kept), split, extract, delete, reorder and rotate pages.
- **Generation and encryption**: create PDF files from images, text or HTML; set a password and permissions on a document, or remove the password.
- **Scanned page recognition**: recognize the text of scanned pages without a text layer with an installed OCR plugin.
- **Viewer**: continuous scrolling, pinch zoom, thumbnails, outline, search highlights, go to page and night mode. Other apps can hand PDF files to it through "Open with" or sharing.
- **Toolbox**: merge, split, convert to images, convert images to PDF, encrypt, decrypt, extract text and organize pages. Tasks run one after another in the background, with the progress shown in the notification.
- **AutoJs6 file manager**: tapping a PDF opens it in the viewer, and PDF files inside archives also open directly; the file menu offers merge, split, convert to images, extract text, encrypt and remove the password.

### Using it in scripts

Use the global `pdf` module directly in AutoJs6 scripts. Page numbers start at 1, and page ranges are written as `'1-3,5,8-'`:

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

- Every method has an `Async` form that returns a Promise (for example `pdf.mergeAsync`). The blocking forms cannot be called on the UI thread, so use the `Async` forms in UI scripts.
- Pass `password` in the options to open an encrypted document. Failures throw an error with a `code`, such as `PASSWORD_REQUIRED` or `PAGE_OUT_OF_RANGE`.
- In Node.js, load it with `require('autojs6:pdf')`; in Bun, use `host.pdf`. The methods are the same and all return Promises.

### Requirements

- Android 7.0 or later. The viewer and the toolbox can be used on their own, without AutoJs6.
- The `pdf` module in scripts and the file manager entries require AutoJs6 6.8.1 or later. AutoJs6 opens the files that scripts read and write, so AutoJs6 needs the storage permission.
- The toolbox and the viewer opened from the launcher require all files access; scripts do not need this permission of the plugin to process files.
- Apps packaged by AutoJs6 can also use the `pdf` module: they must be packaged by AutoJs6 6.8.1 or later, and this plugin must be installed on the device that runs them.
- Node.js scripts require the Node.js Runtime plugin 2.2.0 or later and must declare `files` in `node.permissions` of `project.json`; Bun scripts require the Bun Runtime plugin 0.23.0 or later (Android 13 and later), with `pdf.control` turned on in its settings.
- Recognizing the text of scanned pages requires an additional OCR plugin (ML Kit OCR, Paddle OCR or Rapid OCR).

### FAQ

**Using it without AutoJs6**

The viewer and the toolbox can be used on their own; AutoJs6 is needed only when scripts process PDF files.

**Why the Plugin Center shows the plugin as incompatible**

The script module and the file manager entries require AutoJs6 6.8.1 or later, and the plugin can be enabled after AutoJs6 is updated; until then the viewer and the toolbox work as usual.

**When a file in the recent files cannot be opened**

After a file is deleted or moved, or its access grant is revoked, tapping the entry shows a message and removes it from the list. Choose the file again through "Open a PDF".

See [AutoJs6 docs > PDF](https://docs.autojs6.com/#/pdf) for the complete script reference and the [project page](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF) for more information.
