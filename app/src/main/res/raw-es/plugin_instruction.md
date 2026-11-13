3-Paper PDF es el complemento PDF de AutoJs6 y, además, una caja de herramientas y un visor de PDF que funcionan por sí solos, sin AutoJs6. Lee la información, el texto, el esquema y las imágenes incrustadas de un PDF y renderiza páginas como imágenes; combina, divide, extrae, elimina, reordena y gira páginas; genera PDF a partir de imágenes, texto o HTML, y cifra o descifra documentos.

> El módulo `pdf` de los scripts de AutoJs6 y las entradas del gestor de archivos requieren AutoJs6 6.8.1 (compilación interna 6641) o posterior; las versiones anteriores de AutoJs6 muestran el complemento como incompatible en el centro de complementos. La caja de herramientas y el visor no dependen de AutoJs6 y funcionan por sí solos.

### Funciones Destacadas

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

Consulte [Documentación de AutoJs6 > PDF](https://docs.autojs6.com/#/pdf) para el uso en scripts y la [página del proyecto](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF) para el progreso.
