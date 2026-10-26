# APK debug para pruebas

[Descargar Hyper-Marker 1.0.0 (Flow Dots)](Hyper-Marker-1.0.0-debug.apk?raw=1).

Esta copia se incluye en Git por solicitud explícita del propietario para descargarla desde el teléfono. Es una compilación debug de desarrollo. Las claves privadas, SDK y caches se conservan fuera del repositorio.

## Identificación

- Fecha: 2026-10-26, America/Mexico_City.
- Fuente: [`c0de9b4`](https://github.com/RubenDarioMena/Hyper-Marker/commit/c0de9b4). Incluye el prototipo Flow Dots (Room v2) y las ruedas de acceso rápido opcionales del lector.
- Paquete: `com.hypermarker`; versión `1.0.0` (`versionCode=1`).
- Mínimo API 26 / Android 8; compile/target API 36 / Android 16. La compatibilidad real depende también de Android System WebView actualizado.
- Arquitecturas: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.
- Tamaño: 23 136 410 bytes.
- SHA-256: `3834579b90920549443ac8c4ddcf4c4713debef75bf3088c93daeae0ce0d502b`.
- Certificado debug local, huella SHA-256: `42909c43e7ba7038ddba222d89f78a491b653268f5c67da86d3cbff3b8742853` (el mismo que la instalación del S24, que se actualizó sin desinstalar). Es distinto del certificado de la entrega cloud anterior.
- Firma verificada con `apksigner verify --print-certs`; paquete, versión y arquitecturas con `aapt dump badging`.

Compilación local (macOS, JBR de Android Studio, Gradle 8.13, SDK 36): `assembleDebug` aprobado; **31/31 pruebas JVM** y **12/12 web** aprobadas; `lintDebug` con **0 errores y 42 advertencias**. Instalado con `adb install -r` en Samsung S24 Ultra (SM-S928U, Android 16) tras respaldar sus datos: la base migró a v2 conservando los 4 documentos. No se ejecutaron pruebas instrumentadas en esta entrega.

**Migración:** al abrir esta versión, la base pasa del esquema v1 al v2 (añade las tablas de Flow Dots). No hay vuelta atrás automática a una versión anterior: respaldar antes si importa conservar la posibilidad de volver.

Para verificar la descarga desde esta carpeta:

```sh
sha256sum -c Hyper-Marker-1.0.0-debug.apk.sha256
```

## Instalación y pruebas manuales

Abrir el enlace de descarga en GitHub y elegir el archivo APK descargado desde Android. Si el teléfono lo solicita, permitir a ese navegador o gestor de archivos instalar aplicaciones. En un repositorio privado, iniciar sesión en GitHub para acceder a la descarga.

Cada entorno de compilación usa su propia clave debug. Android sólo admite actualizar una instalación existente si tiene el mismo paquete y certificado. Ante un error de firma incompatible, no desinstalar una versión con notas importantes: desinstalar borra sus datos privados y V1 todavía no ofrece exportación/backup. Conservar la instalación anterior hasta resolver cómo preservar sus datos.

Comprobar inicialmente:

1. Crear carpetas y una nota Markdown, editar en Source/Visual, cerrar y volver a abrir.
2. Importar [el proyecto ZIP](../fixtures/bundle/project.zip?raw=1), abrirlo y comprobar recursos y botones.
3. En Pin, mantener pulsado, deslizar entre elementos, soltar, guardar y cancelar; verificar pines tras reabrir.
4. Dibujar, usar undo/redo y comprobar que View devuelve la interacción al HTML.

La matriz completa y los límites están en [docs/testing.md](../docs/testing.md) y [docs/validation.md](../docs/validation.md). La validación de compilación y firma en cloud no acredita una instalación ni una prueba táctil en tu teléfono.
