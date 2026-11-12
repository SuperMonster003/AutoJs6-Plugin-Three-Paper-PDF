3-Paper PDF is the PDF plugin of AutoJs6, and also a PDF toolbox and viewer that works on its own without AutoJs6. It reads the information, text, outline and embedded images of a PDF and renders pages to images; it merges, splits, extracts, deletes, reorders and rotates pages; it generates PDF files from images, text or HTML, and encrypts or decrypts documents.

> This is a development preview: the AutoJs6 Plugin Center can discover the plugin, but reading, rendering, page operations, the viewer, the toolbox and the `pdf` module are not delivered yet. The text below describes the design goals of 1.0.0; see the roadmap for progress.

### Features

- Reading: page count, page sizes, metadata, encryption state and permissions; text and outline (bookmarks) per page; text search with page numbers and positions; listing and exporting embedded images.
- Rendering: pages to `Image` objects or PNG / JPEG / WebP files, 144 dpi by default, or by dpi, scale or target width and height.
- Page operations: merge (bookmarks kept), split, extract, delete, reorder and rotate pages; page numbers start at 1 and ranges are written as `'1-3,5,8-'`.
- Generation: images to PDF (JPEG embedded as is, without re-encoding), text to PDF (with a font of your choice) and HTML to PDF (no remote URLs are loaded).
- Encryption: open encrypted documents with a password, encrypt with AES-256 and set permissions, remove encryption.
- Viewer: continuous scrolling, pinch zoom, thumbnails, outline, search highlights, password prompt and night mode; scripts can open the viewer and receive page change and close events.
- Toolbox and file manager: wizards to merge, split, convert to images, convert images to PDF, encrypt / decrypt and extract text; the AutoJs6 file manager opens PDF files directly in the plugin viewer, and its overflow menu offers the same tools.
- OCR: `pdf.text(path, { ocr: true })` calls the AutoJs6 OCR engine for scanned pages without a text layer.

Progress and design are available on the [project page](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF).
