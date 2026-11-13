******

### Release history

******

# v1.0.0

###### 2026/11/12

* `Hint` Development preview: the AutoJs6 Plugin Center can discover the plugin; reading, rendering, page operations, the viewer and the pdf script module are not delivered yet
* `Hint` Requires AutoJs6 6.8.1 (6641) or later (the exact minimum build will be filled in once the host changes land)
* `Feature` Plugin skeleton: Wake activation protocol, Plugin Center identity service, PDF capability service (contract version 1) and file manager entry service
* `Feature` Home screen: the tools, task progress, recent files and the all files access grant (only the toolbox needs it)
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
