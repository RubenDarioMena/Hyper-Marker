# Validación: navegador, copias offline y Abrir con

**Fecha:** 2026-11-02, America/Mexico_City. Fuente: rama `codex/github-sync`, base `d6c6e5e`, cambios sin commit. [Alcance autorizado](proposals/web-browser.md).

## Comprobaciones ejecutadas

- `JAVA_HOME=<JBR de Android Studio> ./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline`: **PASS**. **52/52 JVM**, sin omisiones; lint **0 errores, 34 advertencias**. Se compiló Kotlin limpio después de invalidar salidas incrementales antiguas. El primer lint señaló una indentación ambigua en el bucle de luminancia existente; se añadieron llaves y el análisis final pasó.
- `npm test` en `web/`: **13/13 PASS** (controlador, pines, texto, dibujos, Milkdown y React compilado).
- Emulador `HyperMarker_GitHub_QA`, Android 16/API 36, arm64, 4 GiB, WebView **156.0.8078.47**. Se instalaron explícitamente los APK sólo en `emulator-5554`; `am instrument` con `BrowserIntegrationTest,WebViewIntegrationTest,ImportIntegrationTest`: **22/22 PASS**. Tras añadir una aserción de ausencia de bridge en el navegador, repetición dirigida de `BrowserIntegrationTest`: **4/4 PASS**. No se declara suite Android completa ni aceptación manual V1.
- Captura real con `WebView.saveWebArchive`, transporte HTTPS sintético determinista: HTML actualizado por JavaScript antes de guardar, CSS e imagen SVG. Destrucción del navegador, conversión MHTML y reapertura en `DocumentWebView`: texto dinámico, estilos, imagen y controlador **PASS**. La primera preparación del fixture con `loadDataWithBaseURL` fue rechazada por la política que bloquea `data:` como navegación principal; se sustituyó por solicitudes HTTPS interceptadas sólo dentro del test.
- MHTML sintético hostil con script y recurso HTTPS ausente: script no ejecutado, ningún socket TCP recibido, incluso construyendo el lector con `networkAllowed=true` inconsistente: **PASS**. El repositorio rechaza habilitar red en snapshots. Esquemas privilegiados/hosts internos/credenciales en URL y rutas `file://` privadas: **PASS**.
- `ACTION_VIEW` con HTM mediante MediaStore: resolución de actividad, copia privada, red por defecto y supervivencia al borrar fuente: **PASS**. Resolución MIME XHTML: **PASS**. Repositorio: publicación compensada ante MHTML inválido, preservación del archivo MHTML y duplicación/borrado independientes: **PASS**.
- UI manual en emulador: entrada por globo, URL HTTPS pública `https://example.com/`, carga real, «Descargar página» y «Estudiar copia»: **PASS**. Modo avión activado, proceso detenido y app reabierta; reapertura desde biblioteca y lectura con controles de Ver/Editar/Dibujar: **PASS**. Modo avión del emulador restaurado al terminar. Captura local `artifacts/web-browser/offline-reader.png` inspeccionada.

Toolchain real de esta sesión: JBR/OpenJDK **21.0.10**, Gradle **8.13**, AGP **8.12.0**, SDK **36**, Build Tools **35.0.0**; bytecode Java/Kotlin configurado a 17. No se cambiaron versiones/dependencias.

## APK y S24 Ultra

- Artefacto local: `artifacts/Hyper-Marker-web-offline-debug.apk` (ignorado por Git). SHA-256 **`0b4de7c29e8a0b498ba01929168c7caa9f51c73c875f9d67b62ddcadf63457da`**. `apksigner verify --print-certs`: **PASS**, certificado SHA-256 `42909c43e7ba7038ddba222d89f78a491b653268f5c67da86d3cbff3b8742853`. La copia de `downloads/` no fue reemplazada.
- Solicitud explícita posterior del propietario: instalar en Android conectado. S24 Ultra **SM-S928U, Android 16**. Al principio sólo aparecía el emulador; tras conectar y autorizar el teléfono apareció por USB.
- Firma del APK anterior idéntica. Respaldo con app detenida en `artifacts/phone-backup/s24-web-offline-2026-11-02_151404/`: APK previo, dos TAR idénticos de `files/`, `databases/`, `shared_prefs/`, `no_backup/`, extracción e integridad SQLite comprobadas. SHA-256 del TAR previo: `c3c78ddc19327073b0151dea3ec1dd9e3beb7b72c72ff6d45f3074b6fb960dc0`.
- `adb -s <S24> install -r`: **Success**, sin desinstalar ni borrar datos. Todas las filas previas iguales antes/después: **11 documentos, 9 carpetas, 1 anotación, 9 preferencias, 2 puntos, 1 conexión y 1 fuente remota**; **267 archivos privados** idénticos por SHA-256. Sin cambio de esquema Room.
- APK extraído de la instalación nueva con SHA-256 idéntico al artefacto validado. `ACTION_VIEW text/html` devuelve `com.hypermarker/.MainActivity` entre las actividades del teléfono: **PASS**. Arranque con `am start -W`: `Status: ok`, proceso activo y actividad en primer plano comprobados. La primera orden de apertura devolvió estado no exitoso; la comprobación posterior confirmó la instancia activa. No se ejecutaron pruebas instrumentadas en el teléfono personal.
- Evidencia local de la instalación: `artifacts/web-browser/s24-install.json` y `verification.json` dentro del respaldo. No contienen PAT en claro y no se versionan datos personales.

## Pendiente y límites

Probar con las páginas reales del propietario y el selector de su gestor de archivos. No comprobado en esta sesión: API 26/dispositivo OEM adicional, TalkBack, descargas grandes/falta de espacio, fallo de renderer durante captura, historial tras rotación y todas las variantes de MIME de proveedores. Una app predeterminada del sistema puede evitar que aparezca el selector aunque el filtro esté registrado.

Las copias son estáticas y sólo incluyen recursos ya cargados: no se garantiza preservar canvas/WebGL, frames, vídeos, formularios ni servicios remotos. El navegador deshabilita cookies y no conserva sesiones de autenticación. Los límites de archivo temporal de Chromium, almacenamiento y compatibilidad están en la propuesta. Las pruebas manuales pendientes de V1 y de GitHub Sync siguen pendientes.

Especificación original intacta: SHA-256 `c779db1ae246f5aba1e1084df4b0c7b3a4fc669ead8f4ded080846de5c44fdfb`.
