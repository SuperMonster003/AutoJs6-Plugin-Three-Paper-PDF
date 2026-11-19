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

El procesamiento se ejecuta en el complemento con el `PdfRenderer` de Android y PDFBox-Android, en Java puro, de modo que el host AutoJs6 sigue siendo ligero. Los scripts de AutoJs6 usan estas funciones mediante el módulo global `pdf`, igual que los scripts de los complementos Node.js Runtime y Bun Runtime; AutoJs6 abre las rutas que pasan los scripts y se las entrega al complemento, por lo que este no necesita permiso de almacenamiento para procesar los archivos de los scripts. El complemento incluye su propio visor, que se abre desde el lanzador, el gestor de archivos de AutoJs6, la opción "Abrir con" de otras aplicaciones o un script (`pdf.view`).

> El módulo `pdf` de los scripts de AutoJs6 y las entradas del gestor de archivos requieren AutoJs6 6.8.1 (compilación interna 6641) o posterior; las versiones anteriores de AutoJs6 muestran el complemento como incompatible en el centro de complementos. La caja de herramientas y el visor no dependen de AutoJs6 y funcionan por sí solos.

******

### Funciones Destacadas

******

- Lectura: número de páginas, tamaños de página, metadatos, estado del cifrado y permisos; texto y esquema (marcadores) por página; búsqueda de texto con números de página y posiciones; lista y exportación de las imágenes incrustadas.
- Renderizado: páginas como objetos `Image` o archivos PNG / JPEG / WebP, a 144 dpi por defecto, o según una resolución, una escala o un ancho y un alto de destino.
- Operaciones de páginas: combinar (conservando los marcadores), dividir, extraer, eliminar, reordenar y girar páginas; los números de página empiezan en 1 y los rangos se escriben `'1-3,5,8-'`.
- Generación: imágenes a PDF (los JPEG se incrustan tal cual, sin recodificar), texto a PDF (con la fuente que elija) y HTML a PDF (no se cargan URL remotas).
- Cifrado: abrir documentos cifrados con contraseña, cifrar con AES-256 y establecer permisos, quitar el cifrado.
- Visor: desplazamiento continuo, zoom con dos dedos, miniaturas, esquema, resaltado de búsqueda, ir a una página, introducción de contraseña y modo nocturno; otras aplicaciones pueden entregarle archivos PDF con "Abrir con" o compartiendo; los scripts pueden abrir el visor y recibir los eventos de cambio de página y de cierre.
- Caja de herramientas: 8 herramientas para combinar, dividir, convertir a imágenes, convertir imágenes a PDF, cifrar, descifrar, extraer texto y organizar páginas; las tareas se ejecutan una tras otra en segundo plano, con el progreso en la notificación; los archivos recientes también recuerdan los archivos abiertos con el selector de archivos del sistema.
- Gestor de archivos de AutoJs6: al tocar un PDF se abre en el visor del complemento, y los PDF de hasta 512 MiB dentro de archivos comprimidos también se abren directamente; el menú ofrece combinar, dividir, convertir a imágenes, extraer texto, cifrar y quitar la contraseña.
- OCR: `pdf.text(path, { ocr: true })` reconoce las páginas escaneadas sin capa de texto con un complemento OCR instalado (ML Kit OCR, Paddle OCR o Rapid OCR).
- Node.js y Bun: Node.js Runtime 2.2.0 y Bun Runtime 0.23.0 o posteriores ofrecen el mismo módulo `pdf`; sus métodos devuelven Promises, y las llamadas se pueden interrumpir con `signal` o `timeoutMs`.

******

### Uso

******

1. Descargue el APK desde [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/releases) e instálelo. Hay un único paquete para todas las ABI.
2. Para usarlo en scripts de AutoJs6 o en el gestor de archivos, actualice AutoJs6 a 6.8.1 (compilación interna 6641) o posterior y compruebe que el complemento está activado en el centro de complementos.
3. Para usar la caja de herramientas integrada, abra el complemento y conceda el acceso a todos los archivos cuando se solicite; las operaciones de scripts no necesitan este permiso.

> El complemento es compatible con dispositivos con Android 7.0 (API 24) o posterior. El procesamiento de PDF solo usa API del sistema y bibliotecas en Java puro; la única biblioteca nativa procede de Jetpack Compose y está alineada a páginas de memoria de 16 KB, por lo que un único paquete sirve para todas las ABI.

******

### Compatibilidad

******

- Android 7.0 (API 24) o posterior, un único paquete para todas las ABI; la caja de herramientas y el visor no dependen de AutoJs6.
- Scripts de AutoJs6 y gestor de archivos: requieren AutoJs6 6.8.1 (compilación interna 6641) o posterior.
- Aplicaciones empaquetadas por AutoJs6: deben empaquetarse con AutoJs6 6.8.1 (compilación interna 6641) o posterior, y este complemento debe estar instalado en el dispositivo que las ejecuta; el empaquetado añade el permiso `org.autojs.plugin.pdf.permission.RUNTIME`, así que no hace falta modificar `project.json` (un proyecto Node.js declara `files` y un proyecto Bun declara `pdf.control`).
- Node.js: requiere el complemento Node.js Runtime 2.2.0 o posterior, cargado con `require('autojs6:pdf')`; el proyecto declara `files` en `node.permissions` de `project.json`, además de `files.write` para escribir archivos, `ocr` para reconocer texto y `ui` para abrir el visor.
- Bun: requiere el complemento Bun Runtime 0.23.0 o posterior (Android 13 o posterior), usado mediante `host.pdf`; active `pdf.control` en la pantalla de ajustes de Bun Runtime, además de `ocr.control` para reconocer texto.
- OCR: reconocer páginas escaneadas requiere un complemento OCR (ML Kit OCR, Paddle OCR o Rapid OCR); sin él, el reconocimiento de páginas escaneadas falla con `OCR_UNAVAILABLE`, y las demás funciones no se ven afectadas.

******

### API De Script

******

Los scripts de AutoJs6 usan estas funciones mediante el módulo global `pdf` (también disponible como `$pdf`; referencia completa en [Documentación de AutoJs6 > PDF](https://docs.autojs6.com/#/pdf)):

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

En Node.js y Bun, los métodos del mismo nombre devuelven Promises (Bun los usa mediante `host.pdf`):

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

- Los métodos prácticos como `pdf.info` / `pdf.text` / `pdf.render` / `pdf.merge` / `pdf.split` / `pdf.fromImages` abren y cierran el documento en cada llamada; `pdf.open(path, options)` devuelve un `PdfDocument` que comparte una sola apertura entre varias operaciones, con un máximo de 8 documentos abiertos por script.
- Cada método tiene una forma bloqueante y una forma `xxxAsync` que devuelve una Promise, con los mismos argumentos y resultados; la forma bloqueante no se puede llamar en el hilo de la interfaz (falla con `INVALID_ARGUMENT`), así que los scripts de interfaz usan `xxxAsync`; los métodos que escriben archivos aceptan `onProgress` y su Promise tiene `cancel()`; cancelar o detener el script nunca deja un archivo a medio escribir.
- Los `displayNames` de `pdf.merge` dan nombre al marcador de cada entrada: `null` toma el nombre del archivo (sin `.pdf`), y una cadena vacía o de espacios toma el título del documento (`Document <n>` si no tiene); unas opciones de más de unos 63 KiB fallan con `LIMIT_EXCEEDED` y no escriben nada.
- `pdf.fromHtml` ejecuta por defecto los scripts de la página, y `javaScript: false` los desactiva; no se carga ningún contenido externo: las solicitudes de red reciben una respuesta vacía y quedan registradas en `warnings`.
- `pdf.view(path, options)` abre el visor y devuelve un `PdfViewSession` con los eventos `page` / `close` y los métodos `goTo(page)`, `state()` y `close()`; falla con `UI_REQUIRES_FOREGROUND` cuando AutoJs6 no está en primer plano (en Android 10 o posterior sin el permiso de superposición).
- Node.js y Bun: todos los métodos devuelven una Promise; `signal` (un AbortSignal) y `timeoutMs` interrumpen la llamada, que se rechaza con `CANCELLED` en ambos casos, y un tiempo agotado tiene como `cause` un `TimeoutError`; `timeoutMs` debe ser un número finito mayor que 0 y como máximo 2147483647, y los decimales se redondean hacia arriba.
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

#### Por qué el centro de complementos muestra el complemento como incompatible?

El módulo de scripts y las entradas del gestor de archivos del complemento requieren AutoJs6 6.8.1 (compilación interna 6641) o posterior. Tras actualizar AutoJs6 se puede activar el complemento; mientras tanto, la caja de herramientas y el visor funcionan con normalidad.

#### Puede el gestor de archivos de AutoJs6 abrir un PDF dentro de un archivo comprimido?

Sí. Un PDF de hasta 512 MiB dentro de un archivo comprimido se abre directamente en el visor del complemento; extraiga primero los más grandes.

#### Qué hago si un archivo de los archivos recientes ya no se abre?

Los archivos abiertos con el selector de archivos del sistema conservan su permiso de lectura y se siguen abriendo directamente tras reiniciar. Si el archivo se ha eliminado o movido, o se ha revocado el permiso, al tocar la entrada se muestra un aviso y se quita de la lista; vuelva a elegir el archivo con "Abrir un PDF".

******

### Permisos Y Seguridad

******

El complemento reduce al máximo sus permisos:

- Entradas protegidas: el servicio de capacidad, el servicio de entrada del gestor de archivos y la entrada de activación exigen el permiso de complemento de AutoJs6 (`org.autojs.permission.PLUGIN`), así que otras aplicaciones no pueden llamarlos directamente. El servicio y la entrada del visor para aplicaciones empaquetadas por AutoJs6 exigen `org.autojs.plugin.pdf.permission.RUNTIME` (se añade al empaquetar); los documentos y las sesiones del visor abiertos por esas entradas solo responden a la aplicación que los creó, y los archivos también llegan como descriptores de archivo.
- Almacenamiento: los archivos de los scripts llegan como descriptores de archivo, así que el complemento no pide permiso de almacenamiento para los scripts; el acceso a todos los archivos solo se usa para los archivos que elija en la caja de herramientas y el visor.
- HTML a PDF: el complemento no carga ningún contenido externo (las solicitudes de red reciben una respuesta vacía, los archivos locales y las direcciones `content:` están bloqueados y la página no puede navegar); los scripts de la página se pueden desactivar con `javaScript: false`.
- Archivos recientes: los archivos abiertos con el selector de archivos del sistema conservan su permiso de lectura para abrirse directamente la próxima vez, hasta un máximo de 20; el permiso se libera en cuanto una entrada sale de la lista o se quita, o se borra la lista. Los archivos que otras aplicaciones entregan con "Abrir con" o compartiendo solo tienen un permiso de un solo uso y no entran en los archivos recientes.
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

#### v1.1.0

_2026/11/20_

- `Mejora` La página Historial de versiones ahora se expande y contrae por versión: una versión contraída muestra su fecha y el número de entradas por categoría, una expandida nombra cada categoría una sola vez; todas las versiones se pueden expandir o contraer a la vez y la más reciente se abre expandida; la página lleva el nombre del complemento como subtítulo

#### v1.0.0

_2026/11/14_

- `Aviso` El módulo pdf de los scripts de AutoJs6 y las entradas del gestor de archivos requieren AutoJs6 6.8.1 (compilación interna 6641) o posterior; las versiones anteriores de AutoJs6 muestran el complemento como incompatible en el centro de complementos, y la caja de herramientas y el visor siguen funcionando por sí solos
- `Aviso` Usarlo en scripts de Node.js o Bun requiere Node.js Runtime 2.2.0 o Bun Runtime 0.23.0 o posterior
- `Función` Los scripts de AutoJs6 disponen del módulo pdf (también disponible como $pdf): leer la información, el texto, el esquema y las imágenes de los PDF, buscar texto, renderizar páginas como imágenes, combinar, dividir, extraer, eliminar, reordenar y girar páginas, generar PDF a partir de imágenes, texto o HTML, y cifrar y descifrar; cada método tiene una forma bloqueante y una forma Async que puede informar del progreso y cancelarse
- `Función` La opción ocr de pdf.text reconoce las páginas escaneadas sin capa de texto con un complemento OCR instalado (ML Kit OCR, Paddle OCR o Rapid OCR)
- `Función` pdf.view abre un PDF en el visor del complemento; los scripts reciben los eventos de cambio de página y de cierre, y pueden ir a una página o cerrar el visor
- `Función` Los scripts de Node.js y Bun también pueden usar el módulo pdf; sus métodos devuelven Promises, y las llamadas se pueden interrumpir con signal o timeoutMs
- `Función` Las aplicaciones empaquetadas por AutoJs6 6.8.1 o posterior también pueden usar el módulo pdf, y el permiso necesario se añade al empaquetar; los documentos y las sesiones del visor que abren solo responden a la aplicación que los creó
- `Función` Visor: desplazamiento continuo, zoom con dos dedos, miniaturas, esquema, resaltado de búsqueda, ir a una página, introducción de contraseña y modo nocturno; se abre desde el lanzador, y otras aplicaciones pueden entregarle archivos PDF con "Abrir con" o compartiendo
- `Función` Gestor de archivos de AutoJs6: al tocar un PDF se abre en el visor del complemento, y los PDF de hasta 512 MiB dentro de archivos comprimidos también se abren directamente; el menú ofrece combinar, dividir, convertir a imágenes, extraer texto, cifrar y quitar la contraseña
- `Función` Pantalla de inicio: las herramientas, el progreso de las tareas, los archivos recientes y la concesión del acceso a todos los archivos (solo la necesita la caja de herramientas)
- `Función` Los archivos recientes recuerdan los archivos abiertos con el selector de archivos del sistema (hasta 20), que se siguen abriendo directamente tras reiniciar; un archivo eliminado o al que ya no se puede acceder se notifica y se quita de la lista
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
