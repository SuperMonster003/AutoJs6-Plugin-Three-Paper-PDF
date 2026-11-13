3-Paper PDF is the PDF plugin of AutoJs6, and also a PDF toolbox and viewer that works on its own without AutoJs6. It reads the information, text, outline and embedded images of a PDF and renders pages to images; it merges, splits, extracts, deletes, reorders and rotates pages; it generates PDF files from images, text or HTML, and encrypts or decrypts documents.

> The `pdf` module of AutoJs6 scripts and the file manager entries require AutoJs6 6.8.1 (internal build 6641) or later; older AutoJs6 versions show the plugin as incompatible in the Plugin Center. The toolbox and the viewer do not depend on AutoJs6 and work on their own.

### Features

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

See [AutoJs6 documentation > PDF](https://docs.autojs6.com/#/pdf) for script usage and the [project page](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF) for progress.
