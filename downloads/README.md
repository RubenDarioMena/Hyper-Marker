# APK debug para pruebas

[Descargar Hyper-Marker 1.0.0](Hyper-Marker-1.0.0-debug.apk?raw=1).

Esta copia se incluye en Git por solicitud explícita del propietario para descargarla desde el teléfono. Es una compilación debug de desarrollo. Las claves privadas, SDK y caches se conservan fuera del repositorio.

## Identificación

- Fecha: 2026-10-19, America/Mexico_City.
- Fuente: [`58af2aa077bc935e2fd29d7d4e20425134c767cd`](https://github.com/RubenDarioMena/Hyper-Marker/commit/58af2aa077bc935e2fd29d7d4e20425134c767cd). Los cambios de esta entrega sólo agregan el binario y documentación; el código de aplicación conserva esa revisión.
- Paquete: `com.hypermarker`; versión `1.0.0` (`versionCode=1`).
- Mínimo API 26 / Android 8; compile/target API 36 / Android 16. Incluye Android 11 dentro del rango admitido. La compatibilidad real depende también de Android System WebView actualizado.
- Arquitecturas: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.
- Tamaño: 21 083 303 bytes.
- SHA-256: `423f39d2f2878645b25bfa3b39447a9e8a87a3a1d71805ae21a63c5581529031`.
- Certificado debug, huella SHA-256: `464ff892c749162daccbf9eadea5d502b175e9d6ca290e1259514b12d857774f`.
- Firma APK v2 validada con `apksigner verify --verbose --print-certs`; paquete, versión y SDK comprobados con `aapt dump badging`.

Compilación cloud con Temurin 17.0.16+8, Gradle 8.13, SDK 36 revisión 2 y Build Tools 35.0.0: `assembleDebug` aprobado; **13/13 pruebas JVM aprobadas**, cero fallos/omisiones; `lintDebug` aprobado con **20 advertencias y cero errores**. No se ejecutaron pruebas instrumentadas ni se instaló este APK en un dispositivo en esta sesión. Los resultados Android anteriores están registrados por separado en el reporte de validación.

Para verificar la descarga desde esta carpeta:

```sh
sha256sum -c Hyper-Marker-1.0.0-debug.apk.sha256
```

## Instalación y pruebas manuales

Abrir el enlace de descarga en GitHub y elegir el archivo APK descargado desde Android. Si el teléfono lo solicita, permitir a ese navegador o gestor de archivos instalar aplicaciones. En un repositorio privado, iniciar sesión en GitHub para acceder a la descarga.

Esta compilación cloud usa una clave debug propia. Android sólo admite actualizar una instalación existente si tiene el mismo paquete y certificado. Ante un error de firma incompatible, no desinstalar una versión con notas importantes: desinstalar borra sus datos privados y V1 todavía no ofrece exportación/backup. Conservar la instalación anterior hasta resolver cómo preservar sus datos.

Comprobar inicialmente:

1. Crear carpetas y una nota Markdown, editar en Source/Visual, cerrar y volver a abrir.
2. Importar [el proyecto ZIP](../fixtures/bundle/project.zip?raw=1), abrirlo y comprobar recursos y botones.
3. En Pin, mantener pulsado, deslizar entre elementos, soltar, guardar y cancelar; verificar pines tras reabrir.
4. Dibujar, usar undo/redo y comprobar que View devuelve la interacción al HTML.

La matriz completa y los límites están en [docs/testing.md](../docs/testing.md) y [docs/validation.md](../docs/validation.md). La validación de compilación y firma en cloud no acredita una instalación ni una prueba táctil en tu teléfono.
