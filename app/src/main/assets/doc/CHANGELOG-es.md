******

### Historial De Versiones

******

# v1.0.0

###### 2026/11/14

* `Aviso` El módulo pdf de los scripts de AutoJs6 y las entradas del gestor de archivos requieren AutoJs6 6.8.1 (compilación interna 6641) o posterior; las versiones anteriores de AutoJs6 muestran el complemento como incompatible en el centro de complementos, y la caja de herramientas y el visor siguen funcionando por sí solos
* `Aviso` Usarlo en scripts de Node.js o Bun requiere Node.js Runtime 2.2.0 o Bun Runtime 0.23.0 o posterior
* `Función` Los scripts de AutoJs6 disponen del módulo pdf (también disponible como $pdf): leer la información, el texto, el esquema y las imágenes de los PDF, buscar texto, renderizar páginas como imágenes, combinar, dividir, extraer, eliminar, reordenar y girar páginas, generar PDF a partir de imágenes, texto o HTML, y cifrar y descifrar; cada método tiene una forma bloqueante y una forma Async que puede informar del progreso y cancelarse
* `Función` La opción ocr de pdf.text reconoce las páginas escaneadas sin capa de texto con un complemento OCR instalado (ML Kit OCR, Paddle OCR o Rapid OCR)
* `Función` pdf.view abre un PDF en el visor del complemento; los scripts reciben los eventos de cambio de página y de cierre, y pueden ir a una página o cerrar el visor
* `Función` Los scripts de Node.js y Bun también pueden usar el módulo pdf; sus métodos devuelven Promises, y las llamadas se pueden interrumpir con signal o timeoutMs
* `Función` Las aplicaciones empaquetadas por AutoJs6 6.8.1 o posterior también pueden usar el módulo pdf, y el permiso necesario se añade al empaquetar; los documentos y las sesiones del visor que abren solo responden a la aplicación que los creó
* `Función` Visor: desplazamiento continuo, zoom con dos dedos, miniaturas, esquema, resaltado de búsqueda, ir a una página, introducción de contraseña y modo nocturno; se abre desde el lanzador, y otras aplicaciones pueden entregarle archivos PDF con "Abrir con" o compartiendo
* `Función` Gestor de archivos de AutoJs6: al tocar un PDF se abre en el visor del complemento, y los PDF de hasta 512 MiB dentro de archivos comprimidos también se abren directamente; el menú ofrece combinar, dividir, convertir a imágenes, extraer texto, cifrar y quitar la contraseña
* `Función` Pantalla de inicio: las herramientas, el progreso de las tareas, los archivos recientes y la concesión del acceso a todos los archivos (solo la necesita la caja de herramientas)
* `Función` Los archivos recientes recuerdan los archivos abiertos con el selector de archivos del sistema (hasta 20), que se siguen abriendo directamente tras reiniciar; un archivo eliminado o al que ya no se puede acceder se notifica y se quita de la lista
* `Función` La caja de herramientas ofrece 8 herramientas: combinar, dividir (por rangos de páginas o cada n páginas), PDF a imágenes (resolución, formato, páginas), imágenes a PDF (tamaño de página, orientación, márgenes), cifrar, quitar la contraseña, extraer texto (guardado como TXT), y girar, eliminar y reordenar páginas con miniaturas; el procesamiento es el mismo que el de las entradas del gestor de archivos
* `Función` Las tareas de la caja de herramientas se ejecutan una tras otra en un servicio en primer plano y continúan al salir de la pantalla de la herramienta; la notificación muestra el progreso y permite cancelar, y los resultados se pueden abrir en el visor o compartir
* `Función` Un explorador de archivos integrado permite seleccionar varios archivos y crear carpetas; el selector de archivos del sistema también está disponible
* `Función` Pantalla de ajustes: carpeta de salida predeterminada (Documents/3-Paper PDF), resolución predeterminada de PDF a imágenes, idioma, modo nocturno, color de tema, icono del lanzador, estado de los permisos, historial de versiones y búsqueda de actualizaciones
* `Función` Búsqueda de actualizaciones: manual en la pantalla de ajustes, o automática al abrir la pantalla de inicio (como máximo una vez cada 12 horas); la información de versiones procede del índice oficial de complementos
* `Función` Iconos claros y oscuros para el lanzador y el centro de complementos; el lanzador usa por defecto el icono adaptable automático (sigue el tema claro u oscuro del sistema), y los estilos adaptable claro, adaptable oscuro y fondo transparente ya están incluidos, para elegirlos en la pantalla de ajustes
* `Función` Un único APK para todas las ABI: el procesamiento de PDF no contiene bibliotecas nativas; la única biblioteca nativa procede de Jetpack Compose y está alineada a páginas de memoria de 16 KB
* `Función` README, instrucciones del centro de complementos y registros de cambios en 10 idiomas, generados a partir de fuentes JSON
* `Dependencia` Se añade PDFBox-Android 2.0.27.0 (texto, esquema, imágenes, operaciones de páginas y cifrado)
* `Dependencia` Se añade Jetpack Compose BOM 2026.09.00 (pantallas del complemento)
* `Dependencia` Se añade Navigation Compose 2.10.2
* `Dependencia` Se añade Material Color Utilities (MaterialKolor) 4.1.1 (color de tema de las pantallas del complemento)
* `Dependencia` Se añade AndroidX Core KTX 1.18.0
