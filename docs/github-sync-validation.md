# GitHub Sync — validación y entrega al S24

Fecha: **2026-10-27**, America/Mexico_City. Fuente Android/web: **`899499ad55b64f3a7a62a8dbcdf14dbee5288f10`**, rama `codex/github-sync`.

## Estado verificable

Implementación de GitHub Sync entregada e instalada en el Samsung por solicitud del propietario, quien realizará las pruebas manuales. **La aceptación completa permanece abierta:** la última suite instrumentada tiene tres fallos y no se verificó aún un PAT privado real ni la cadencia periódica en Samsung. No confundir este reporte con un PASS de toda la matriz.

| Comprobación | Resultado final |
| --- | --- |
| `:app:testDebugUnitTest` | **48/48 PASS**, sin omisiones. Incluye migraciones, cuotas/reapertura/corrupción/reloj, streams parciales, paginación, escala de 10.001 archivos, paths/modos hostiles, snapshots, cancelación, concurrencia, recuperación y copias locales independientes. |
| `npm test` en `web/` | **13/13 PASS**. Incluye Markdown remoto de solo lectura y regresiones de editor/controlador/React. |
| `:app:lintDebug` | **PASS**, 0 errores, 49 advertencias; no se afirma ausencia de advertencias. |
| `:app:assembleDebug`, `:app:assembleDebugAndroidTest` | **PASS** con JBR de Android Studio, Gradle 8.13, SDK 36; dependencias offline. |
| Instrumentación Android completa, última corrida | **21/24 PASS, 3 FAIL**, sin omisiones. Emulador Android 16, WebView 134.0.6998.135. Detalle abajo. |
| GitHub / Keystore en Android | PASS del cifrado/rotación/borrado sintético; 100 programaciones conservan un único worker con sus restricciones; ejecución de worker sin credencial y sin fuentes automáticas; paquete remoto aislado de credencial/otro documento; renderer conserva una generación. |
| GitHub público real | PASS: `RubenDarioMena/Hyper-Marker`, `main`, `fixtures/project`, snapshot `a557113dd5ff5da2208ff177db521a24d01744a0`. Descarga de 2 HTML y assets; CSS y SVG visibles en modo avión; JS cambió `Contar: 0` a `Contar: 1`; misma copia abierta tras detener/reabrir la app sin conexión. |
| Selector real de ramas | PASS con `owner/repo` manual: aparece `main` como fila seleccionable además del campo de entrada. Se corrigió una acción anidada que omitía cargar las ramas mientras `busy` seguía activo. Una rama real de PR sigue pendiente. |
| Consumo real | Panel abierto y revisado: 16 peticiones, 45.823 B recibidos, 15 respuestas 200 y una 404 por un owner escrito incorrectamente durante la prueba. Sin aumento de peticiones al leer la copia offline. La consulta posterior del selector añade peticiones contabilizadas. |
| S24 Ultra, conservación de datos | **PASS**: actualización `adb install -r`, migración Room v2→v3, integridad SQLite `ok`, registros previos idénticos y 9 archivos canónicos con SHA-256 igual. Sin desinstalar ni instrumentar el teléfono personal. |

## Fallos y límites de instrumentación

La primera corrida en el AVD previo (`flutter_emulator`, render software, 1080×2400) terminó 11/22 PASS y 11 FAIL con un diálogo observado de **System UI isn't responding**. No se atribuyen esos fallos a GitHub Sync ni se usan para declarar aceptación.

Se creó `HyperMarker_GitHub_QA` con la imagen instalada de Android 36.1, 2 GiB RAM, 720×1600, densidad 320 y GPU host. En una corrida posterior hubo 23/24 PASS: el primer test quedó cubierto por el aviso inicial de Android «Viewing full screen». La prueba ahora descarta ese aviso cuando está presente. Se eliminó también un selector ambiguo que podía tocar el campo de búsqueda en lugar del título de una nota, y se espera al controlador antes del flujo compartido.

**Última corrida del código entregado: 21/24 PASS.** Permanecen sin resolver:

1. `AppWorkflowTest.markdownKeepsReadingPositionAndRestoresToolsAfterTimeout`: timeout del callback de evaluación del WebView al iniciar Visual.
2. `AppWorkflowTest.sharePinSaveCancelUndoAndVisualMarkdownSurviveRecreation`: timeout de evaluación al esperar la inicialización del lector.
3. `WebViewIntegrationTest.drawingCompletesTransactionAndCanvasUsesSurface`: no se observó la transacción de trazo esperada.

Las corridas fueron variables; no existe evidencia suficiente para atribuir definitivamente los tres casos al entorno o al producto. El resto de pruebas de seguridad, GitHub, importación y WebView pasó en esa última corrida. El propietario pidió recibir la build y realizar la comprobación manual; no se continuó repitiendo la suite ni se ejecutó sobre sus datos personales.

Evidencia local ignorada por Git: `artifacts/github-sync-review/` (`final-gradle.log`, `web-tests.log`, `instrumented-final.log`, capturas offline, XML del selector/panel y firma). No se publicó contenido privado de la biblioteca ni una credencial.

## APK y respaldo

- APK local entregado: `artifacts/Hyper-Marker-github-sync-debug.apk` (32.487.181 bytes).
- SHA-256: `d8db8249fa6fa895b57e9104dcc6f5796a60b2538585df2f1dba9488e07e6280`.
- `com.hypermarker`, `1.0.0` (1), mínimo 26, compile/target 36.
- Firma verificada: SHA-256 del certificado `42909c43e7ba7038ddba222d89f78a491b653268f5c67da86d3cbff3b8742853`; coincide con la instalación previa del S24.
- El APK versionado de `downloads/` sigue siendo el anterior de Flow Dots. No se sustituyó como si esta aceptación estuviera cerrada.
- Respaldo local anterior a instalar: `artifacts/phone-backup/s24-github-sync-2026-10-27_011640/`, ignorado por Git. Dos TAR idénticos de `files/`, `databases/`, `shared_prefs/` y `no_backup/`, extracción validada, integridad de SQLite comprobada y APK anterior conservado.
- SHA-256 del TAR previo: `1a17b642887e3e633ea1cbcf956846ca0bc4812c1d2399157af3eb4f993698a6`.
- Antes/después: **6 carpetas, 6 documentos, 1 anotación, 4 preferencias, 2 puntos y 1 conexión**, con todas las filas previas idénticas. Los marcadores de runtime `files/profileInstalled` y `android.app.ActivityThread.IDS.xml` cambiaron por la actualización; los 9 archivos canónicos de datos/preferencias conservaron su hash.
- App instalada, migrada y abierta en SM-S928U; WebView del teléfono: **155.0.8059.43**. No se tocó el teléfono después de la entrega al propietario.

## Comprobaciones manuales abiertas

Ingresar el PAT sólo en la app; probar acceso privado limitado, repo autorizado posteriormente, rama de PR, actualización real y anotaciones conservadas, revocación/expiración, Wi-Fi/datos, offline tras reinicio, limpieza de retirados, accesibilidad y ejecución periódica bajo políticas Samsung. Comprobar también Fuente/Visual, pin y dibujo por los tres casos instrumentados pendientes. No forzar rate-limit de GitHub real: 403/429 y sus pausas se prueban con transporte falso.

El contenido remoto conserva las limitaciones del lector: referencias HTML/CSS estáticas verificadas; dependencias construidas por JS no inferibles exhaustivamente; red del WebView apagada por documento; sin compilar JSX/TSX ni sincronizar Flow Dots.
