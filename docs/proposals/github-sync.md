# GitHub en Hyper-Marker: implementación y aceptación

Fuente de alcance: [especificación de sincronización](../../Hyper-Marker_GitHub_Sync_Spec_v1.md), autorizada por el propietario como función adicional a V1. La [especificación original](../../Interactive_Notes_Android_Agent_Design.md) no se modifica. La guía de acciones del propietario está en [guia-github-sync.html](../guia-github-sync.html).

## Decisiones implementadas en esta rama

| Tema | Decisión | Evidencia y límite |
| --- | --- | --- |
| Red | REST de GitHub exclusivamente desde Android mediante `GitHubApiClient`; `HttpsURLConnection` sin redirecciones, host fijo `api.github.com`, TLS, timeouts y límites del cuerpo. | Se probó con transporte falso y una importación pública real en emulador. No hay acceso GitHub desde WebView. |
| Credencial | Un PAT activo cifrado con AES-GCM y clave Android Keystore; conexión pública sin token opcional. Auto Backup y Device Transfer permanecen desactivados. | Prueba instrumentada con credencial sintética en Android 16 aprobada. La rotación de una cuenta multipersona no está diseñada. |
| Fuente | Una carpeta por identidad repo ID + rama + ruta; otra rama crea otra carpeta. La cuenta es única para el MVP. | Branch y path se eligen en UI. Un cambio de nombre de archivo se trata como baja + alta. |
| Room | Versión 3 con `MIGRATION_2_3` y tablas `remote_folders` / `remote_entries`; documentos `REMOTE_MARKDOWN` son de solo lectura. | Migración v1→v2→v3 y v2→v3 comprobadas en JVM; v2→v3 también observada sobre la base existente del emulador con el mismo conteo de documentos y puntos Flow. |
| Snapshot | Obtener SHA de ref y árbol, comparar blob SHA, copiar sin red lo inalterado, verificar SHA de blob, preparar en privado, cambiar metadatos Room al final y mantener IDs de documentos. | Un test con servidor simulado cubre 304, actualización incremental, error antes/durante descarga y conservación de anotaciones/offline. La publicación entre archivos y Room usa compensación; no hay garantía de transacción de filesystem idéntica a una transacción SQL. |
| HTML | Un documento HTML por paquete de carpeta; incluye páginas HTML y recursos permitidos bajo la selección, con rutas relativas. | `fixtures/project` público se abrió sin red, con CSS/SVG y JS interactivo. Referencias estáticas `src`/`href`/CSS `url()` ausentes hacen fallar todo el snapshot; referencias dinámicas construidas por JS no pueden comprobarse exhaustivamente. |
| Markdown | Cada `.md` tiene ID estable; fuente remota privada de solo lectura, vista Visual local para sintaxis preservable y botón para crear copia local editable. | La vista Visual usa Milkdown `setReadonly(true)` sin red; el texto fuente sigue visible para sintaxis no preservable. Prueba web de solo lectura aprobada. |
| Automatización | Una `PeriodicWorkRequest` global con `KEEP`, período 60 min y restricciones de red no medida, batería y espacio; se activa por carpeta. La entrada a carpeta refresca tras 10 min sólo con red no medida y batería suficiente. | Worker ejecutable sin credencial, programación única y restricciones comprobables en Android. La cadencia real de 60 min y el comportamiento del Samsung quedan para aceptación física; Android puede retrasarla. |
| Recursos | Presupuesto persistente previo a cada petición, 60/h, 500/día, 30 por ejecución; cuerpos recibidos medidos antes de copiar; 150 MiB/día, 30 MiB/sync, 10 MiB/archivo por defecto; tamaños ajustables 30/60 y 10/20 MiB con topes. Pausa manual y espera por rate limit. | Pruebas JVM de 200/304/429, streams parciales, bytes, reapertura y cambios de reloj; sin token se reduce a 20/h y 100/día. Métrica HTTP no equivale al consumo facturado por la operadora. |

## Cierre técnico y entrega para teléfono

El panel de consumo incluye 1 h/24 h/30 días por fuente y total, cuerpos recibidos/enviados, estados, operaciones bloqueadas, almacenamiento y últimas ejecuciones con duración, CPU del proceso y condiciones de batería/carga. El ahorro por caché se identifica como estimación; cabeceras/TLS, energía real y recursos de agentes no se miden. La exportación por SAF anonimiza las fuentes. El detalle conserva hasta 500 peticiones y 500 ejecuciones, con agregados diarios HTTP de hasta 30 días.

Se comparte el bloqueo del repositorio para publicar, recuperar, copiar y desvincular; 100 aperturas concurrentes se coalescen. Los tests incluyen rutas hostiles, symlinks/submódulos, colisiones normalizadas, árbol de 10.001 archivos, falta de espacio, cancelación previa a Room, interrupción tras commit Room, recuperación de puntero, conservación de Flow/Markdown/anotaciones, documentos retirados y desvinculación. La limpieza de staging y generaciones sin referencia ocurre al arrancar. Los HTML anteriores se retienen mientras puedan tener lectores abiertos y siguen sujetos al cap de disco.

El navegador verifica `Link`, pagina repositorios y ramas y hace explícito el cap de 500 resultados. Owner/repo y nombre de rama se pueden ingresar manualmente. La pantalla de credenciales impide capturas; un 401 suspende el token de forma persistente hasta validar su sustitución. Presupuesto corrupto y cambios incompatibles del reloj bloquean red.

Los resultados de la compilación final y las comprobaciones Android se registran en `../github-sync-validation.md` y `../../progress.md`.

## Aceptación que requiere el Samsung desbloqueado y al propietario

- Antes de instalar: respaldo actual verificado de documentos, Room, Flow Dots, anotaciones y preferencias; comparar firma y conteos. No usar `connectedDebugAndroidTest` sobre el teléfono personal.
- Ingresar el PAT directamente en la APK, probar acceso privado limitado, ampliación a un repo nuevo, expiración/revocación y una rama de PR real. No transmitir la credencial al agente.
- Probar actualización real, modo avión después de reiniciar, anotaciones conservadas, Wi-Fi/datos, limpieza de retirados, accesibilidad y cadencia del worker bajo las políticas OEM. No provocar deliberadamente rate-limit: 403/429 y sus pausas se cubren con transporte falso.

## Límites de compatibilidad

La validación previa al snapshot comprueba referencias estáticas HTML/CSS. Las rutas construidas dinámicamente por JavaScript no pueden inferirse exhaustivamente; los assets permitidos de la carpeta seleccionada se incluyen y los fallos en ejecución se muestran en la consola existente. Una dependencia externa sigue requiriendo permiso de red por documento. El proceso no compila JSX/TSX ni elude el aislamiento del lector.

No se afirma aceptación física del Samsung ni del PAT por una prueba de emulador. El reporte distingue los casos automatizados de esos pasos pendientes.

## Referencias técnicas verificadas

- [GitHub REST: contenido](https://docs.github.com/en/rest/repos/contents), [trees](https://docs.github.com/en/rest/git/trees), [refs](https://docs.github.com/en/rest/git/refs), [blobs](https://docs.github.com/en/rest/git/blobs) y [solicitudes condicionales](https://docs.github.com/en/rest/using-the-rest-api/best-practices-for-using-the-rest-api).
- [WorkManager 2.12.0](https://developer.android.com/jetpack/androidx/releases/work), [periodicidad y restricciones](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work), [política `KEEP`](https://developer.android.com/reference/androidx/work/ExistingPeriodicWorkPolicy).
- [Android Auto Backup y reglas de extracción](https://developer.android.com/identity/data/autobackup).
