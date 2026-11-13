******

### Release history

******

# v1.0.0

###### 2026/11/12

* `Hint` The pdf module of AutoJs6 scripts and the file manager entries require AutoJs6 6.8.1 (internal build 6641) or later; older AutoJs6 versions show the plugin as incompatible in the Plugin Center, and the toolbox and the viewer still work on their own
* `Hint` Use in Node.js or Bun scripts requires Node.js Runtime 2.2.0 or Bun Runtime 0.23.0 or later
* `Feature` AutoJs6 scripts get the pdf module (also available as $pdf): read the information, text, outline and images of PDF files, search text, render pages to images, merge, split, extract, delete, reorder and rotate pages, generate PDF files from images, text or HTML, and encrypt and decrypt; every method has a blocking form and an Async form that can report progress and be cancelled
* `Feature` The ocr option of pdf.text recognizes scanned pages without a text layer with an installed OCR plugin (ML Kit OCR, Paddle OCR or Rapid OCR)
* `Feature` pdf.view opens a PDF in the plugin viewer; scripts receive page change and close events and can go to a page or close the viewer
* `Feature` Node.js and Bun scripts can use the pdf module too; its methods return Promises, and calls can be aborted with signal or timeoutMs
* `Feature` Apps packaged by AutoJs6 6.8.1 or later can use the pdf module too, with the needed permission added when packaging; documents and viewer sessions they open answer only the app that created them
* `Feature` Viewer: continuous scrolling, pinch zoom, thumbnails, outline, search highlights, go to page, password prompt and night mode; it opens from the launcher, and other apps can hand PDF files to it with "Open with" or sharing
* `Feature` AutoJs6 file manager: tapping a PDF opens it in the plugin viewer, and PDF files of up to 512 MiB inside archives open directly too; the menu offers merge, split, convert to images, extract text, encrypt and remove the password
* `Feature` Home screen: the tools, task progress, recent files and the all files access grant (only the toolbox needs it)
* `Feature` The recent files remember files opened with the system file picker (up to 20), which still open directly after a restart; a file that has been deleted or can no longer be accessed is reported and removed from the list
* `Feature` The toolbox offers 8 tools: merge, split (by page ranges or every n pages), PDF to images (resolution, format, pages), images to PDF (page size, orientation, margins), encrypt, remove the password, extract text (saved as TXT), and rotating, deleting and reordering pages with thumbnails; they share their processing with the file manager entries
* `Feature` Toolbox tasks run one after another in a foreground service and keep running after you leave the tool screen; the notification shows the progress and can cancel, and the results can be opened in the viewer or shared
* `Feature` A built-in file browser that can select several files and create folders; the system file picker is also available
* `Feature` Settings screen: default output folder (Documents/3-Paper PDF), default resolution of PDF to images, language, night mode, theme color, launcher icon, permission status, release history and update check
* `Feature` Update check: manually on the settings screen, or automatically when the home screen opens (at most once every 12 hours); release information comes from the official plugin index
* `Feature` Light and dark icons for the launcher and the Plugin Center; the launcher uses the adaptive automatic icon by default (it follows the system light or dark theme), and the adaptive light, adaptive dark and transparent background styles are built in, selectable on the settings screen
* `Feature` A single APK for every ABI: PDF processing contains no native library; the only native library comes from Jetpack Compose and is aligned to 16 KB memory pages
* `Feature` README, Plugin Center instructions and changelogs in 10 languages, generated from JSON sources
* `Dependency` Attach PDFBox-Android 2.0.27.0 (text, outline, images, page operations and encryption)
* `Dependency` Attach Jetpack Compose BOM 2026.09.00 (the plugin screens)
* `Dependency` Attach Navigation Compose 2.10.2
* `Dependency` Attach Material Color Utilities (MaterialKolor) 4.1.1 (theme color of the plugin screens)
* `Dependency` Attach AndroidX Core KTX 1.18.0
