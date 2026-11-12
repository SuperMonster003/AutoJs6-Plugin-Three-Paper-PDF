******

### Historial De Versiones

******

# v1.0.0

###### 2026/11/12

* `Aviso` Versión preliminar de desarrollo: el centro de complementos de AutoJs6 puede detectar el complemento; la lectura, el renderizado, las operaciones de páginas, el visor, la caja de herramientas y el módulo de script pdf aún no están disponibles
* `Aviso` Requiere AutoJs6 6.8.1 (6641) o posterior (la compilación mínima exacta se indicará cuando lleguen los cambios del host)
* `Función` Esqueleto del complemento: protocolo de activación Wake, servicio de identidad del centro de complementos, servicio de capacidad PDF (versión de contrato 1) y servicio de entrada del gestor de archivos
* `Función` Pantalla de inicio con la concesión del acceso a todos los archivos (solo la necesita la caja de herramientas)
* `Función` Un único APK para todas las ABI: el procesamiento de PDF no contiene bibliotecas nativas; la única biblioteca nativa procede de Jetpack Compose y está alineada a páginas de memoria de 16 KB
* `Función` README, instrucciones del centro de complementos y registros de cambios en 10 idiomas, generados a partir de fuentes JSON
* `Dependencia` Se añade PDFBox-Android 2.0.27.0 (texto, esquema, imágenes, operaciones de páginas y cifrado)
* `Dependencia` Se añade Jetpack Compose BOM 2026.09.00 (pantallas del complemento)
* `Dependencia` Se añade Navigation Compose 2.10.2
* `Dependencia` Se añade AndroidX Core KTX 1.18.0
