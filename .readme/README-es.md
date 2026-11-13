<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-paper-pdf-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>Lectura, renderizado, combinación, división, generación y cifrado de archivos PDF, con visor integrado y entradas en el gestor de archivos</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Idiomas (Languages)

******

El README.md actual admite los siguientes idiomas:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-fr.md)
- Español [es] # actual
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/.readme/README-ar.md)

******

### Introducción

******

3-Paper PDF es el complemento PDF de AutoJs6 y, además, una caja de herramientas y un visor de PDF que funcionan por sí solos, sin AutoJs6. Lee la información, el texto, el esquema y las imágenes incrustadas de un PDF y renderiza páginas como imágenes; combina, divide, extrae, elimina, reordena y gira páginas; genera PDF a partir de imágenes, texto o HTML, y cifra o descifra documentos.

El procesamiento se ejecuta en el complemento con el `PdfRenderer` de Android y PDFBox-Android, en Java puro, de modo que el host AutoJs6 sigue siendo ligero. Los scripts de AutoJs6 usan estas funciones mediante el módulo global `pdf`; AutoJs6 abre las rutas que pasan los scripts y se las entrega al complemento, por lo que este no necesita permiso de almacenamiento para procesar los archivos de los scripts. El complemento incluye su propio visor, que se abre desde el lanzador, el gestor de archivos de AutoJs6 o un script (`pdf.view`).

> Esta es una versión preliminar de desarrollo: el centro de complementos de AutoJs6 puede detectar el complemento, pero la lectura, el renderizado, las operaciones de páginas, el visor y el módulo `pdf` aún no están disponibles. El texto siguiente describe los objetivos de diseño de la 1.0.0; consulte la hoja de ruta para ver el progreso.

******

### Funciones Destacadas

******

- Lectura: número de páginas, tamaños de página, metadatos, estado del cifrado y permisos; texto y esquema (marcadores) por página; búsqueda de texto con números de página y posiciones; lista y exportación de las imágenes incrustadas.
- Renderizado: páginas como objetos `Image` o archivos PNG / JPEG / WebP, a 144 dpi por defecto, o según una resolución, una escala o un ancho y un alto de destino.
- Operaciones de páginas: combinar (conservando los marcadores), dividir, extraer, eliminar, reordenar y girar páginas; los números de página empiezan en 1 y los rangos se escriben `'1-3,5,8-'`.
- Generación: imágenes a PDF (los JPEG se incrustan tal cual, sin recodificar), texto a PDF (con la fuente que elija) y HTML a PDF (no se cargan URL remotas).
- Cifrado: abrir documentos cifrados con contraseña, cifrar con AES-256 y establecer permisos, quitar el cifrado.
- Visor: desplazamiento continuo, zoom con dos dedos, miniaturas, esquema, resaltado de búsqueda, introducción de contraseña y modo nocturno; los scripts pueden abrir el visor y recibir los eventos de cambio de página y de cierre.
- Caja de herramientas y gestor de archivos: asistentes para combinar, dividir, convertir a imágenes, convertir imágenes a PDF, cifrar / descifrar y extraer texto; el gestor de archivos de AutoJs6 abre los PDF directamente en el visor del complemento, y su menú adicional ofrece las mismas herramientas.
- OCR: `pdf.text(path, { ocr: true })` llama al motor OCR de AutoJs6 para las páginas escaneadas sin capa de texto.

******

### Uso

******

1. Descargue el APK desde [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases) e instálelo. Hay un único paquete para todas las ABI.
2. Para usar el módulo `pdf` en scripts, actualice AutoJs6 a la compilación interna 6641 (6.8.1) o posterior y active el complemento en el centro de complementos.
3. Para usar la caja de herramientas integrada, abra el complemento y conceda el acceso a todos los archivos cuando se solicite; las operaciones de scripts no necesitan este permiso.

> El complemento es compatible con dispositivos con Android 7.0 (API 24) o posterior. El procesamiento de PDF solo usa API del sistema y bibliotecas en Java puro; la única biblioteca nativa procede de Jetpack Compose y está alineada a páginas de memoria de 16 KB, por lo que un único paquete sirve para todas las ABI.

******

### API De Script

******

Los scripts usan estas funciones mediante el módulo global `pdf` (también disponible como `$pdf`) (borrador de diseño de la 1.0.0; prevalece la documentación de la versión publicada):

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

- Los métodos prácticos como `pdf.info` / `pdf.text` / `pdf.render` / `pdf.merge` / `pdf.split` / `pdf.fromImages` abren y cierran el documento en cada llamada; `pdf.open(path, options)` devuelve un `PdfDocument` que comparte una sola apertura entre varias operaciones, con un máximo de 8 documentos abiertos por script.
- Cada método tiene una forma bloqueante y una forma `xxxAsync` que devuelve una Promise; `xxxAsync` acepta `options.onProgress(progress)` y su Promise tiene `cancel()`; detener el script cancela las operaciones en curso y elimina las salidas sin terminar.
- `pdf.view(path, options)` abre el visor y devuelve un `PdfViewSession` con los eventos `page` y `close` y los métodos `goTo(page)` y `close()`.
- Los fallos lanzan un `PdfError` con un `code` estable como `PASSWORD_REQUIRED`, `WRONG_PASSWORD` o `PAGE_OUT_OF_RANGE`; `pdf.isAvailable()` comprueba de antemano si el complemento está disponible.

******

### Preguntas Frecuentes

******

#### Puedo usarlo sin AutoJs6?

Sí. La caja de herramientas y el visor funcionan por sí solos. AutoJs6 solo hace falta cuando los scripts procesan PDF.

#### Por qué el complemento pide acceso a todos los archivos?

Solo lo necesitan la caja de herramientas y el visor abierto desde el lanzador, para leer y escribir los archivos que elija en pantalla. AutoJs6 abre las rutas que pasan los scripts y se las entrega al complemento como descriptores de archivo, así que los scripts no necesitan ningún permiso de almacenamiento del complemento.

#### El complemento contiene bibliotecas nativas?

El procesamiento de PDF no usa ninguna biblioteca nativa: las páginas las renderiza el `PdfRenderer` del sistema y las demás funciones usan PDFBox-Android, en Java puro. La única biblioteca nativa del paquete es `libandroidx.graphics.path.so`, que viene con Jetpack Compose.

******

### Permisos Y Seguridad

******

El complemento reduce al máximo sus permisos:

- Entradas protegidas: el servicio de capacidad, el servicio de entrada del gestor de archivos y la entrada de activación exigen el permiso de complemento de AutoJs6 (`org.autojs.permission.PLUGIN`), así que otras aplicaciones no pueden llamarlos directamente.
- Almacenamiento: los archivos de los scripts llegan como descriptores de archivo, así que el complemento no pide permiso de almacenamiento para los scripts; el acceso a todos los archivos solo se usa para los archivos que elija en la caja de herramientas y el visor.
- HTML a PDF: solo se renderiza la cadena HTML o el archivo local indicados; no se cargan URL remotas.
- Red y contraseñas: la red solo se usa para buscar actualizaciones; las contraseñas de los documentos solo se usan en la operación en curso y el complemento nunca las guarda.

******

### Motor y dependencias

******

Las páginas se renderizan con el `PdfRenderer` de Android; el texto, el esquema, las imágenes, las operaciones de páginas y el cifrado usan [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache-2.0, Java puro), y el cifrado se apoya en Bouncy Castle (licencia de tipo MIT). El procesamiento de PDF no contiene bibliotecas nativas ni usa renderizadores nativos como pdfium o MuPDF.

******

### Interfaz Del Plugin

******

La siguiente información está dirigida a los desarrolladores del host AutoJs6 y de plugins; el host usa estos identificadores para descubrir el plugin y negociar capacidades:

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

### Hoja De Ruta

******

Los planes, las decisiones de diseño y el progreso se registran como lista de verificación en ROADMAP.md. Los elementos sin marcar son planes, no funciones de la versión actual.

- [Ver ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/ROADMAP.md)

******

### Historial De Versiones

******

#### v1.0.0

_2026/11/12_

- `Aviso` Versión preliminar de desarrollo: el centro de complementos de AutoJs6 puede detectar el complemento; la lectura, el renderizado, las operaciones de páginas, el visor y el módulo de script pdf aún no están disponibles
- `Aviso` Requiere AutoJs6 6.8.1 (6641) o posterior (la compilación mínima exacta se indicará cuando lleguen los cambios del host)
- `Función` Esqueleto del complemento: protocolo de activación Wake, servicio de identidad del centro de complementos, servicio de capacidad PDF (versión de contrato 1) y servicio de entrada del gestor de archivos
- `Función` Pantalla de inicio: las herramientas, el progreso de las tareas, los archivos recientes y la concesión del acceso a todos los archivos (solo la necesita la caja de herramientas)
- `Función` La caja de herramientas ofrece 8 herramientas: combinar, dividir (por rangos de páginas o cada n páginas), PDF a imágenes (resolución, formato, páginas), imágenes a PDF (tamaño de página, orientación, márgenes), cifrar, quitar la contraseña, extraer texto (guardado como TXT), y girar, eliminar y reordenar páginas con miniaturas; el procesamiento es el mismo que el de las entradas del gestor de archivos
- `Función` Las tareas de la caja de herramientas se ejecutan una tras otra en un servicio en primer plano y continúan al salir de la pantalla de la herramienta; la notificación muestra el progreso y permite cancelar, y los resultados se pueden abrir en el visor o compartir
- `Función` Un explorador de archivos integrado permite seleccionar varios archivos y crear carpetas; el selector de archivos del sistema también está disponible
- `Función` Pantalla de ajustes: carpeta de salida predeterminada (Documents/3-Paper PDF), resolución predeterminada de PDF a imágenes, idioma, modo nocturno, color de tema, icono del lanzador, estado de los permisos, historial de versiones y búsqueda de actualizaciones
- `Función` Búsqueda de actualizaciones: manual en la pantalla de ajustes, o automática al abrir la pantalla de inicio (como máximo una vez cada 12 horas); la información de versiones procede del índice oficial de complementos
- `Función` Iconos claros y oscuros para el lanzador y el centro de complementos; el lanzador usa por defecto el icono adaptable automático (sigue el tema claro u oscuro del sistema), y los estilos adaptable claro, adaptable oscuro y fondo transparente ya están incluidos, para elegirlos en la pantalla de ajustes
- `Función` Un único APK para todas las ABI: el procesamiento de PDF no contiene bibliotecas nativas; la única biblioteca nativa procede de Jetpack Compose y está alineada a páginas de memoria de 16 KB
- `Función` README, instrucciones del centro de complementos y registros de cambios en 10 idiomas, generados a partir de fuentes JSON
- `Dependencia` Se añade PDFBox-Android 2.0.27.0 (texto, esquema, imágenes, operaciones de páginas y cifrado)
- `Dependencia` Se añade Jetpack Compose BOM 2026.09.00 (pantallas del complemento)
- `Dependencia` Se añade Navigation Compose 2.10.2
- `Dependencia` Se añade Material Color Utilities (MaterialKolor) 4.1.1 (color de tema de las pantallas del complemento)
- `Dependencia` Se añade AndroidX Core KTX 1.18.0

##### Para más historial de versiones

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/assets/doc/CHANGELOG-es.md)

******

### Compilación

******

Esta sección está dirigida a desarrolladores que quieran compilar el plugin desde el código fuente.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:appendDigestToReleasedFiles
py .python\generate_markdown.py --check
py .python\generate_third_party_notices.py --check
py .python\generate_icon_studio.py --check
py .python\check_repository.py
```

******

### Licencia

******

El código del proyecto se distribuye bajo la [Mozilla Public License 2.0](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/LICENSE). Los componentes y datos de terceros se enumeran en los avisos de terceros.

- [Avisos de terceros (THIRD_PARTY_NOTICES.md)](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/THIRD_PARTY_NOTICES.md)

******

### Enlaces

******

- Proyecto AutoJs6: https://github.com/SuperMonster003/AutoJs6
