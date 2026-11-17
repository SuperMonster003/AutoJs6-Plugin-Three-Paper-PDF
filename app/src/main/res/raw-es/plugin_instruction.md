3-Paper PDF proporciona el módulo `pdf` a los scripts de AutoJs6 para leer, renderizar, combinar, dividir, generar y cifrar archivos PDF. También es un visor y una caja de herramientas de PDF que se pueden usar por separado, sin AutoJs6.

### Primeros pasos

1. Use directamente el módulo global `pdf` en los scripts de AutoJs6; vea el ejemplo más abajo.
2. Para ver o procesar archivos PDF en el teléfono, abra 3-Paper PDF desde el lanzador, conceda el acceso a todos los archivos cuando se solicite y toque "Abrir un PDF" o elija el procesamiento que necesite en "Herramientas".
3. Al tocar un archivo PDF en el gestor de archivos de AutoJs6, se abre en el visor de 3-Paper PDF.

### Funciones principales

- **Lectura**: número de páginas, tamaños de página, metadatos y estado del cifrado; texto y esquema (marcadores) por página; búsqueda de texto; lista y exportación de las imágenes incrustadas.
- **Renderizado**: páginas como objetos de imagen o archivos PNG / JPEG / WebP, con el tamaño indicado por dpi, escala o ancho y alto de destino.
- **Operaciones de páginas**: combinar (conservando los marcadores), dividir, extraer, eliminar, reordenar y girar páginas.
- **Generación y cifrado**: crear archivos PDF a partir de imágenes, texto o HTML; establecer una contraseña y permisos en un documento, o quitar la contraseña.
- **Reconocimiento de páginas escaneadas**: reconocer el texto de páginas escaneadas sin capa de texto con un complemento de OCR instalado.
- **Visor**: desplazamiento continuo, zoom con dos dedos, miniaturas, esquema, resaltado de búsqueda, ir a una página y modo nocturno. Otras aplicaciones pueden entregarle archivos PDF mediante "Abrir con" o al compartir.
- **Caja de herramientas**: combinar, dividir, convertir a imágenes, convertir imágenes a PDF, cifrar, descifrar, extraer texto y organizar páginas. Las tareas se ejecutan una tras otra en segundo plano, con el progreso en la notificación.
- **Gestor de archivos de AutoJs6**: al tocar un PDF se abre en el visor, y los PDF dentro de archivos comprimidos también se abren directamente; el menú de archivos ofrece combinar, dividir, convertir a imágenes, extraer texto, cifrar y quitar la contraseña.

### Uso en scripts

Use directamente el módulo global `pdf` en los scripts de AutoJs6. Los números de página empiezan en 1 y los rangos se escriben como `'1-3,5,8-'`:

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

- Cada método tiene una forma `Async` que devuelve una Promise (por ejemplo, `pdf.mergeAsync`). Las formas bloqueantes no se pueden llamar en el hilo de la interfaz; use las formas `Async` en los scripts de interfaz.
- Pase `password` en las opciones para abrir un documento cifrado. Los fallos lanzan un error con un `code`, como `PASSWORD_REQUIRED` o `PAGE_OUT_OF_RANGE`.
- En Node.js, cárguelo con `require('autojs6:pdf')`; en Bun, use `host.pdf`. Los métodos son los mismos y todos devuelven Promise.

### Requisitos

- Android 7.0 o posterior. El visor y la caja de herramientas se pueden usar por separado, sin AutoJs6.
- El módulo `pdf` de los scripts y las entradas del gestor de archivos requieren AutoJs6 6.8.1 o posterior. AutoJs6 abre los archivos que leen y escriben los scripts, por lo que necesita el permiso de almacenamiento.
- La caja de herramientas y el visor abierto desde el lanzador requieren el acceso a todos los archivos; los scripts no necesitan este permiso del complemento para procesar archivos.
- Las aplicaciones empaquetadas por AutoJs6 también pueden usar el módulo `pdf`: deben empaquetarse con AutoJs6 6.8.1 o posterior, y este complemento debe estar instalado en el dispositivo que las ejecuta.
- Los scripts de Node.js requieren el complemento Node.js Runtime 2.2.0 o posterior y deben declarar `files` en `node.permissions` de `project.json`; los scripts de Bun requieren el complemento Bun Runtime 0.23.0 o posterior (Android 13 o posterior), con `pdf.control` activado en sus ajustes.
- Reconocer el texto de páginas escaneadas requiere un complemento de OCR adicional (ML Kit OCR, Paddle OCR o Rapid OCR).

### Preguntas frecuentes

**Uso sin AutoJs6**

El visor y la caja de herramientas se pueden usar por separado; AutoJs6 solo es necesario cuando los scripts procesan archivos PDF.

**Motivos por los que el centro de complementos muestra el complemento como incompatible**

El módulo de scripts y las entradas del gestor de archivos requieren AutoJs6 6.8.1 o posterior, y el complemento se puede activar después de actualizar AutoJs6; mientras tanto, el visor y la caja de herramientas funcionan con normalidad.

**Casos en que un archivo reciente no se abre**

Si un archivo se eliminó o se movió, o se revocó su permiso de acceso, al tocar la entrada se muestra un aviso y se quita de la lista. Vuelva a elegir el archivo con "Abrir un PDF".

La referencia completa de scripts está en [Documentación de AutoJs6 > PDF](https://docs.autojs6.com/#/pdf), y el resto de la información en la [página del proyecto](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF).
