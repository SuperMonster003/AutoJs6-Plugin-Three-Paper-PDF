******

### Release history

******

# v1.0.0

###### 2026/11/12

* `Hint` Development preview: the AutoJs6 Plugin Center can discover the plugin; reading, rendering, page operations, the viewer, the toolbox and the pdf script module are not delivered yet
* `Hint` Requires AutoJs6 6.8.1 (6641) or later (the exact minimum build will be filled in once the host changes land)
* `Feature` Plugin skeleton: Wake activation protocol, Plugin Center identity service, PDF capability service (contract version 1) and file manager entry service
* `Feature` Home screen with the all files access grant (only the toolbox needs it)
* `Feature` A single APK for every ABI: PDF processing contains no native library; the only native library comes from Jetpack Compose and is aligned to 16 KB memory pages
* `Feature` README, Plugin Center instructions and changelogs in 10 languages, generated from JSON sources
* `Dependency` Attach PDFBox-Android 2.0.27.0 (text, outline, images, page operations and encryption)
* `Dependency` Attach Jetpack Compose BOM 2026.09.00 (the plugin screens)
* `Dependency` Attach Navigation Compose 2.10.2
* `Dependency` Attach AndroidX Core KTX 1.18.0
