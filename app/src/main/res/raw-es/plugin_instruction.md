3-Paper PDF es el complemento PDF de AutoJs6 y, además, una caja de herramientas y un visor de PDF que funcionan por sí solos, sin AutoJs6. Lee la información, el texto, el esquema y las imágenes incrustadas de un PDF y renderiza páginas como imágenes; combina, divide, extrae, elimina, reordena y gira páginas; genera PDF a partir de imágenes, texto o HTML, y cifra o descifra documentos.

> Esta es una versión preliminar de desarrollo: el centro de complementos de AutoJs6 puede detectar el complemento, pero la lectura, el renderizado, las operaciones de páginas, el visor, la caja de herramientas y el módulo `pdf` aún no están disponibles. El texto siguiente describe los objetivos de diseño de la 1.0.0; consulte la hoja de ruta para ver el progreso.

### Funciones Destacadas

- Lectura: número de páginas, tamaños de página, metadatos, estado del cifrado y permisos; texto y esquema (marcadores) por página; búsqueda de texto con números de página y posiciones; lista y exportación de las imágenes incrustadas.
- Renderizado: páginas como objetos `Image` o archivos PNG / JPEG / WebP, a 144 dpi por defecto, o según una resolución, una escala o un ancho y un alto de destino.
- Operaciones de páginas: combinar (conservando los marcadores), dividir, extraer, eliminar, reordenar y girar páginas; los números de página empiezan en 1 y los rangos se escriben `'1-3,5,8-'`.
- Generación: imágenes a PDF (los JPEG se incrustan tal cual, sin recodificar), texto a PDF (con la fuente que elija) y HTML a PDF (no se cargan URL remotas).
- Cifrado: abrir documentos cifrados con contraseña, cifrar con AES-256 y establecer permisos, quitar el cifrado.
- Visor: desplazamiento continuo, zoom con dos dedos, miniaturas, esquema, resaltado de búsqueda, introducción de contraseña y modo nocturno; los scripts pueden abrir el visor y recibir los eventos de cambio de página y de cierre.
- Caja de herramientas y gestor de archivos: asistentes para combinar, dividir, convertir a imágenes, convertir imágenes a PDF, cifrar / descifrar y extraer texto; el gestor de archivos de AutoJs6 abre los PDF directamente en el visor del complemento, y su menú adicional ofrece las mismas herramientas.
- OCR: `pdf.text(path, { ocr: true })` llama al motor OCR de AutoJs6 para las páginas escaneadas sin capa de texto.

El progreso y el diseño están disponibles en la [página del proyecto](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Paper-PDF).
