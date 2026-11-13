******

### Historial De Versiones

******

# v1.0.0

###### 2026/11/12

* `Aviso` Versión preliminar de desarrollo: el centro de complementos de AutoJs6 puede detectar el complemento; la lectura, el renderizado, las operaciones de páginas, el visor y el módulo de script pdf aún no están disponibles
* `Aviso` Requiere AutoJs6 6.8.1 (6641) o posterior (la compilación mínima exacta se indicará cuando lleguen los cambios del host)
* `Función` Esqueleto del complemento: protocolo de activación Wake, servicio de identidad del centro de complementos, servicio de capacidad PDF (versión de contrato 1) y servicio de entrada del gestor de archivos
* `Función` Pantalla de inicio: las herramientas, el progreso de las tareas, los archivos recientes y la concesión del acceso a todos los archivos (solo la necesita la caja de herramientas)
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
