<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-paper-pdf-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>Lectura, renderizado, combinación, división, generación, compresión y cifrado de archivos PDF, relleno de formularios y marcas de agua, con visor integrado y entradas en el gestor de archivos</p>

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

3-Paper PDF es el complemento PDF de AutoJs6 y, además, una caja de herramientas y un visor de PDF que funcionan por sí solos, sin AutoJs6. Lee la información, el texto, el esquema y las imágenes incrustadas de un PDF y renderiza páginas como imágenes; combina, divide, extrae, elimina, reordena y gira páginas; genera PDF a partir de imágenes, texto o HTML, y cifra o descifra documentos. Desde la versión 1.1.0 también lee y rellena formularios, añade marcas de agua, números de página, encabezados y pies de página, crea PDF con búsqueda de texto mediante un complemento de OCR, reduce el tamaño de los archivos y modifica las propiedades del documento y el esquema.

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
- Formularios: listar los campos del formulario (campos de texto, casillas de verificación, botones de opción, cuadros combinados y cuadros de lista) con sus valores actuales, rellenarlos por nombre de campo y, si se desea, acoplarlos como contenido normal de la página; `pdf.info` devuelve el número de campos de formulario.
- Marcas de agua y números de página: añadir marcas de agua de texto o de imagen, números de página, encabezados y pies de página, con posición, rotación, opacidad, mosaico y las páginas a las que se aplican; los textos pueden usar los marcadores `{page}`, `{total}`, `{date}` y `{name}`.
- PDF con búsqueda de texto: reconocer las páginas escaneadas con un complemento de OCR y escribir una capa de texto invisible, de modo que el texto se pueda buscar, seleccionar y copiar, y que leerlo después no requiera un nuevo reconocimiento; los resultados que reconozca el propio script se pueden escribir con `pdf.addTextLayer`.
- Compresión: volver a codificar las imágenes incrustadas con la resolución y la calidad elegidas, comprimir los flujos sin comprimir y, si se desea, eliminar los metadatos; hay ajustes predefinidos low / medium / high o valores personalizados, y el resultado indica el tamaño antes y después.
- Propiedades del documento y esquema: cambiar el título, el autor, el asunto, las palabras clave y el creador; leer el esquema (marcadores), modificarlo y volver a escribirlo, o eliminarlo.
- Visor: desplazamiento continuo, zoom con dos dedos, miniaturas, esquema, resaltado de búsqueda, ir a una página, introducción de contraseña, modo nocturno y lectura a pantalla completa; otras aplicaciones pueden entregarle archivos PDF con "Abrir con" o compartiendo; los scripts pueden abrir el visor y recibir los eventos de cambio de página y de cierre.
- Caja de herramientas: 13 herramientas para combinar, dividir, convertir a imágenes, convertir imágenes a PDF, cifrar, descifrar, extraer texto, organizar páginas, añadir una marca de agua, añadir números de página (ambas con una vista previa de una página que se puede pasar), comprimir, rellenar formularios y editar las propiedades del documento, con valores personalizados para la marca de agua, los números de página y la compresión; las tareas se ejecutan una tras otra en segundo plano, con el progreso en la notificación; la pantalla de resultados puede escribir el proceso como un script de Rhino, Node.js o Bun; los archivos recientes también recuerdan los archivos abiertos con el selector de archivos del sistema y los PDF procesados con cualquier herramienta, que aparecen en "Recientes" arriba del explorador de archivos, el cual empieza en la carpeta del último archivo elegido.
- Gestor de archivos de AutoJs6: al tocar un PDF se abre en el visor del complemento, y los PDF de hasta 512 MiB dentro de archivos comprimidos también se abren directamente; el menú ofrece combinar, dividir, comprimir, convertir a imágenes, añadir una marca de agua, extraer texto, cifrar y quitar la contraseña, y puede crear un PDF con búsqueda de texto mediante un complemento de OCR.
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
- Métodos de script nuevos en 1.1.0 (formularios, marcas de agua y números de página, PDF con búsqueda de texto, compresión, propiedades del documento y esquema): requieren AutoJs6 6.8.1 o posterior y este complemento 1.1.0 o posterior, con las mismas versiones de Node.js Runtime y Bun Runtime que los demás métodos; mientras el complemento siga en 1.0.x, estos métodos fallan con `UNSUPPORTED` e indican que se actualice en el centro de complementos, no escriben ningún archivo, y los demás métodos funcionan con normalidad.
- Aplicaciones empaquetadas por AutoJs6: deben empaquetarse con AutoJs6 6.8.1 (compilación interna 6641) o posterior, y este complemento debe estar instalado en el dispositivo que las ejecuta; el empaquetado añade el permiso `org.autojs.plugin.pdf.permission.RUNTIME`, así que no hace falta modificar `project.json` (un proyecto Node.js declara `files` y un proyecto Bun declara `pdf.control`).
- Node.js: requiere el complemento Node.js Runtime 2.2.0 o posterior, cargado con `require('autojs6:pdf')`; el proyecto declara `files` en `node.permissions` de `project.json`, además de `files.write` para escribir archivos, `ocr` para reconocer texto y `ui` para abrir el visor.
- Bun: requiere el complemento Bun Runtime 0.23.0 o posterior (Android 13 o posterior), usado mediante `host.pdf`; active `pdf.control` en la pantalla de ajustes de Bun Runtime, además de `ocr.control` para reconocer texto.
- OCR: el reconocimiento de las páginas escaneadas y la creación de PDF con búsqueda de texto requieren un complemento de OCR (ML Kit OCR, Paddle OCR o Rapid OCR); sin él, estas dos funciones fallan con `OCR_UNAVAILABLE`, sin afectar a las demás funciones.

Requisitos y funciones por versión:

| Elemento | 1.0.0 | 1.1.0 |
| --- | --- | --- |
| Android | 7.0 (API 24) o posterior | 7.0 (API 24) o posterior |
| AutoJs6 | 6.8.1 (compilación interna 6641) o posterior | 6.8.1 (compilación interna 6641) o posterior |
| Node.js Runtime | 2.2.0 o posterior | 2.2.0 o posterior |
| Bun Runtime | 0.23.0 o posterior (Android 13 o posterior) | 0.23.0 o posterior (Android 13 o posterior) |
| Complemento de OCR (opcional) | Reconocimiento de páginas escaneadas | Reconocimiento de páginas escaneadas, creación de PDF con búsqueda de texto |
| Métodos de script | Lectura, renderizado, operaciones con páginas, generación, cifrado, OCR, visor | Además: formularios, marcas de agua y números de página, PDF con búsqueda de texto, compresión, propiedades del documento y esquema |
| Caja de herramientas | 8 herramientas | 13 herramientas, valores personalizados, generación de scripts |
| Menú del gestor de archivos de AutoJs6 | 6 acciones | 9 acciones (además: comprimir, añadir marca de agua, crear PDF con búsqueda de texto) |

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
- Los métodos de escritura de la versión 1.1.0 (por ejemplo `pdf.fillForm`, `pdf.stamp` y `pdf.compress`) pueden omitir la ruta de salida (o recibir `null`) igual que los demás métodos de escritura, y el resultado se escribe en `Documents/3-Paper PDF/`; los métodos del mismo nombre de `PdfDocument` reutilizan la contraseña indicada al abrir y no aceptan `password` en sus opciones; los resultados de los métodos que escriben archivos también incluyen `warningCodes`, un código por cada entrada de `warnings`, para que los scripts puedan tratarlos por código.
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

#### Por qué los valores introducidos en el formulario no se muestran en el visor?

En Android 14 y versiones anteriores, el visor del complemento solo muestra los valores introducidos después de acoplarlos; las demás aplicaciones de PDF no se ven afectadas. Para verlos en el visor, acople al rellenar (`flatten: true`, o "Acoplar los campos" en la herramienta "Rellenar formulario"); los campos acoplados ya no se pueden editar.

#### Por qué el archivo comprimido no es más pequeño?

La compresión reduce sobre todo las imágenes incrustadas. Cuando las imágenes ya son lo bastante pequeñas, o son de un tipo que el complemento no vuelve a codificar (por ejemplo JPEG 2000, CMYK o imágenes de 1 bit), el efecto es limitado; un resultado que no es más pequeño se escribe igualmente, con un aviso en el resultado, y puede conservar el archivo original.

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

- `Aviso` Los nuevos métodos de script y la acción "Crear PDF con búsqueda de texto" del gestor de archivos requieren AutoJs6 6.8.1 o posterior, y usarlos en scripts de Node.js o Bun requiere además Node.js Runtime 2.2.0 o Bun Runtime 0.23.0 o posterior; las nuevas herramientas de la caja de herramientas no dependen de AutoJs6
- `Función` pdf.forms y pdf.fillForm leen los campos de formulario (campos de texto, casillas de verificación, botones de opción, cuadros combinados, cuadros de lista) y los rellenan por nombre de campo, con la opción de acoplarlos como contenido normal de la página; pdf.info añade el número de campos de formulario
- `Función` pdf.watermark y pdf.stamp añaden marcas de agua de texto o de imagen, números de página, encabezados y pies de página, con posición, rotación, opacidad, mosaico y las páginas a las que se aplican
- `Función` pdf.makeSearchable escribe una capa de texto invisible en las páginas escaneadas mediante un complemento de OCR y crea PDF cuyo texto se puede buscar y copiar; pdf.addTextLayer escribe los resultados que reconozca el propio script
- `Función` pdf.compress vuelve a codificar las imágenes incrustadas y comprime los flujos, puede eliminar los metadatos, y ofrece los ajustes predefinidos low / medium / high o una resolución y una calidad personalizadas; el resultado indica el tamaño antes y después
- `Función` pdf.setInfo cambia propiedades del documento como el título, el autor, el asunto, las palabras clave y el creador, y pdf.setOutline reescribe o elimina el esquema con la estructura que devuelve pdf.outline
- `Función` Cada método nuevo tiene una forma Async y un método del mismo nombre en PdfDocument, y funciona también en Node.js, Bun y las aplicaciones empaquetadas por AutoJs6; los resultados de los métodos que escriben archivos también incluyen warningCodes, para que los scripts traten los avisos por código
- `Función` La caja de herramientas añade 5 herramientas, "Añadir marca de agua al PDF" (con vista previa de una página), "Añadir números de página", "Comprimir PDF", "Rellenar formulario" y "Propiedades del documento", 13 en total; la marca de agua, los números de página y la compresión aceptan también valores personalizados además de los ajustes predefinidos
- `Función` La vista previa de una página de las herramientas de marca de agua y números de página pasa a cualquier página del documento; el número de página y el total de páginas de la vista previa siguen la página mostrada, igual que en el resultado, y una página fuera de las elegidas se muestra tal cual con una nota
- `Función` La pantalla de resultados de las herramientas y de las acciones del gestor de archivos puede escribir el proceso como un script de Rhino, Node.js o Bun, con "Copiar script", "Guardar como .js" y "Abrir en AutoJs6"; las contraseñas quedan como null en el script para que las rellene usted
- `Función` El menú PDF del gestor de archivos de AutoJs6 añade "Comprimir PDF" y "Añadir marca de agua al PDF", que guardan el resultado junto al archivo original, y "Crear PDF con búsqueda de texto" mediante un complemento de OCR
- `Función` El visor lee a pantalla completa: al tocar la página para ocultar las barras también se ocultan el número de página, la barra de estado y la barra de navegación (desliza desde el borde de la pantalla para mostrar las barras del sistema un momento), y otro toque las recupera; la nueva sección "Visor" de los ajustes desactiva por separado "Ocultar el número de página en pantalla completa" y "Ocultar las barras del sistema en pantalla completa"
- `Corrección` Problema por el que algunos guiones (como los de "3-Paper" y "A-B") de los PDF generados por pdf.fromText se convertían en caracteres nulos al extraer texto o buscar con algunas fuentes del sistema (como en ColorOS 15)
- `Corrección` Problema por el que la barra superior del visor tapaba la parte superior de la primera página al abrir un documento, y la barra de miniaturas el final de la última página; ahora las páginas dejan libres las barras en los dos extremos del documento y ya no se mueven al ocultar o mostrar las barras
- `Corrección` Problema por el que, con la apariencia siguiendo a AutoJs6 y un idioma o modo nocturno del sistema distintos de los de AutoJs6, las pantallas se mostraban a veces un instante con el idioma y el modo nocturno del sistema (por ejemplo, un destello negro) al abrir el complemento o tras cambiar la apariencia en los ajustes
- `Corrección` Problema por el que, tras cambiar el idioma, el modo nocturno o el color de tema en los ajustes, el cuadro de diálogo de selección permanecía a veces unos dos segundos sobre la pantalla actualizada
- `Corrección` Problema por el que una herramienta de la caja de herramientas volvía como una pantalla de opciones vacía, sin los archivos elegidos ni el resultado, cuando el sistema cerraba el complemento mientras usted estaba en otra aplicación (por ejemplo, viendo el archivo generado); ahora se restauran la pantalla de resultados y los archivos elegidos, un archivo cifrado vuelve a pedir su contraseña, y las acciones del gestor de archivos también restauran su pantalla de resultados
- `Mejora` La página Historial de versiones ahora se expande y contrae por versión: una versión contraída muestra su fecha y el número de entradas por categoría, una expandida nombra cada categoría una sola vez; todas las versiones se pueden expandir o contraer a la vez y la más reciente se abre expandida; la página lleva el nombre del complemento como subtítulo
- `Mejora` Las indicaciones de procesamiento de la pantalla de resultados de las herramientas ahora se muestran en el idioma de la interfaz en lugar de solo en inglés
- `Mejora` Se reescribieron las instrucciones del centro de complementos en cinco partes: primeros pasos, funciones principales, uso en scripts, requisitos y preguntas frecuentes
- `Mejora` Volver desde la pantalla de resultado o de error de una herramienta, con la flecha de la barra superior o la tecla atrás del sistema, ahora lleva a las opciones con los archivos elegidos y todo lo escrito conservado, para volver a ejecutar tras un cambio; el nuevo resultado recibe un número en lugar de reemplazar el anterior, y volver desde las opciones cierra la herramienta
- `Mejora` Elegir archivos es más rápido: el explorador de archivos muestra "Recientes" arriba (los archivos recientes de la pantalla de inicio, que ahora incluyen los PDF procesados con cualquier herramienta) para elegir con un toque; empieza en la carpeta del último archivo elegido, compartida por todas las herramientas y por "Abrir un PDF" de la pantalla de inicio (las imágenes recuerdan su propia carpeta)

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
