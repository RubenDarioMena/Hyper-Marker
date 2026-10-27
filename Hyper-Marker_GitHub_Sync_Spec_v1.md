# Hyper-Marker — Especificación de implementación: carpetas GitHub sincronizadas

**Estado:** propuesta técnica para implementación por Codex, pendiente de revisión y pruebas del propietario.  
**Fecha:** 2026-10-26  
**Repositorio objetivo:** https://github.com/RubenDarioMena/Hyper-Marker  
**Alcance aprobado por el propietario en conversación:** sincronización GitHub de documentos seleccionados, solo lectura, sin servidor propio; telemetría local y protecciones preventivas; preservación del minimalismo.  
**No implica aprobación de otras funcionalidades V1.1 ni de sincronización de Flow Dots.**

## 0. Instrucciones obligatorias para el agente implementador

1. **Leer antes de programar:** `AGENTS.md`, `Agent.md`, `progress.md`, `architecture.md`, `docs/security.md`, `docs/testing.md`, `docs/validation.md`, `docs/proposals/flow-dots.md` y la especificación original `Interactive_Notes_Android_Agent_Design.md`.
2. Antes de cambiar algo, comprobar `git status`, rama, commits, remotos y cambios no guardados; **no sobrescribir trabajo ajeno**. Crear una rama de feature/PR si el entorno lo permite. No modificar la especificación original para justificar cambios: añadir esta funcionalidad como documento independiente en `docs/proposals/` cuando se integre al repositorio.
3. La app existente es nativa Android con **Kotlin, Jetpack Compose, Room v2, archivos privados y WebView aislado**; no migrar arquitectura ni rediseñar editores.
4. Construir por hitos pequeños, ejecutar pruebas y actualizar `progress.md` con resultados reales, limitaciones, próximos pasos y cambios de esquema. Mantener versiones de dependencias fijadas y sus licencias documentadas.
5. **No solicitar ni incrustar tokens de GitHub en el código, Git, prompts, capturas, ejemplos reales o logs.** El propietario los configura dentro de la app.
6. Si una decisión de seguridad o compatibilidad no puede implementarse con garantías, **detener el alcance afectado, documentarlo y pedir decisión**, en lugar de reducir protección silenciosamente.
7. La feature no debe depender de hardware/servidor futuro ni de servicios de pago.

## 1. Problema y objetivo

Hyper-Marker permite actualmente leer y anotar HTML interactivo, editar Markdown y construir Flow Dots locales. Falta una manera de **elegir repositorios, ramas y subcarpetas de GitHub desde Android** y mantener disponibles las salidas de investigación/documentación que producen agentes como Codex Cloud, sin navegar el código ni utilizar un navegador tradicional.

**Objetivo V1 de esta feature:** el usuario vincula GitHub, escoge un repositorio y una carpeta, la ve en la biblioteca con icono discreto de nube, descarga documentos compatibles y puede refrescarla manual o automáticamente, manteniendo una copia **offline**. Los documentos remotos son **de lectura solamente**; las anotaciones y notas personales locales nunca se suben al repositorio.

**No prometer tiempo real estricto.** Sin backend que reciba webhooks, ofrecer **sincronización eventual**: actualización al entrar en carpeta según antigüedad, botón «Actualizar» y sondeo periódico con WorkManager. Los horarios no serán exactos por Doze y políticas de Android.

## 2. Límites del producto / fuera de alcance

- **Incluir ahora:** conexión a API REST de GitHub, selección dinámica de repositorios accesibles y rama, selector de carpetas, sincronización incremental de `.md` y paquetes HTML ya compilados con sus recursos relativos, vista offline, estado de sincronización, credenciales seguras, telemetría local, límites de consumo, gestión de errores.
- **No incluir ahora:** servidor casero, WebSockets, webhooks, push instantáneo, Git completo/clonado, revisión de diffs o edición de código, edición/push al remoto, agentes corriendo desde Hyper-Marker, API de Tripo/ComfyUI, gestión de pull requests, sincronizar bases Room o Flow Dots, sincronización de dos vías, resolución avanzada de conflictos.
- Una carpeta sincronizada **no es una carpeta normal con escritura remota**. Diferenciar ambas en modelo de datos, sin añadir menús permanentes ni alterar los modos de lectura.
- Los archivos fuente `.tsx`, `.jsx`, `.ts` y proyectos React sin compilar **no** se convierten automáticamente en HTML ejecutable. Importar únicamente recursos web compilados compatibles con el lector existente.

## 3. Experiencia del usuario (mínima)

1. Biblioteca → `+` → **Añadir carpeta de GitHub**. Mantener `+` actual y comandos existentes.
2. Primera vez → **Conectar GitHub**: explicación corta de permisos y almacenamiento local; ingresar credencial en campo seguro (MVP). Para repositorios públicos permitir opcionalmente modo sin credenciales, con límites inferiores y sin acceso privado.
3. **Elegir repositorio**: listado paginado de los repositorios que devuelve la API para la cuenta/token autorizado, con búsqueda local o remota controlada; alternativa `owner/repo` si no aparece. No afirmar que esto incluye todos los repositorios de la cuenta: depende de permisos, organizaciones y token.
4. **Elegir rama**: sugerir rama predeterminada, permitir otra rama existente; mostrar nombre de rama seleccionada en detalles. **Aclarar:** lo producido por Codex Cloud en una rama de PR no será visible siguiendo `main` hasta fusionarlo; para verlo antes hay que elegir esa rama.
5. **Elegir carpeta** mediante navegación por directorios desde la rama (se admite raíz). Mostrar cantidad/tipos estimados cuando estén disponibles. La selección de carpeta es independiente de los permisos GitHub: solo restringe qué contenido decide descargar Hyper-Marker.
6. Confirmar → se crea una carpeta con símbolo de **nube**. Abrirla usa copia disponible inmediatamente; refrescará si corresponde y la política de consumo permite. Mostrar estado discreto: `Actualizado`, `Actualizando`, `Sin conexión`, `En pausa`, `Error`, `Última actualización: ...`.
7. Menú contextual de carpeta: **Actualizar ahora**, **Sincronización automática (activar/desactivar)**, **Cambiar rama**, **Configurar**, **Desvincular**. Cualquier cambio de selección importante requiere confirmación y debe preservar los documentos locales existentes.
8. En **Ajustes → GitHub → Sincronización y consumo**: resumen compacto, ver límites, pausa manual, estado rate-limit, espacio offline y errores recientes. Nunca mostrar credenciales.

### Principios UX

- El contenido ocupa la pantalla; no insertar barras fijas en los lectores de HTML, Markdown ni Flow Dots.
- Mostrar nube/estado en biblioteca y controles únicamente al solicitarlos.
- Si el usuario abre contenido sin Internet, usar el último snapshot válido y marcarlo como potencialmente desactualizado.
- Descarga remota **solo lectura**. Para modificar el Markdown: «Crear copia local editable» (puede implementarse después del lector remoto, pero antes de permitir editar un archivo sincronizado). Las anotaciones sobre HTML sincronizado siguen siendo locales.

## 4. GitHub, permisos y autenticación

### MVP recomendado: fine-grained personal access token (PAT)

- El usuario crea su PAT en **GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens**. Elegir propietario y `Only select repositories` con los repos requeridos. Permisos de repositorio **Contents: Read-only** y **Metadata: Read-only** (este último suele ser permiso básico). No conceder escritura, Actions ni Administration.
- Guardar el token **solo cifrado en almacenamiento privado**, con clave gestionada por **Android Keystore** y una implementación mantenida. No elegir automáticamente `EncryptedSharedPreferences`: está deprecado; decidir solución actual compatible con API 26, probar restauración/rotación y excluir secretos de Android Auto Backup / Device Transfer.
- Token solo en `Authorization` hacia `https://api.github.com`; deshabilitar redirecciones automáticas que pudieran reenviar headers sensibles a otro host. TLS obligatorio. Nunca acceder a GitHub desde JavaScript de un HTML importado ni exponer token al bridge de WebView.
- Credencial efímera en memoria durante la operación; se permite desconectar/reemplazar. No copiar a exports o crash reports. No adjuntar token a `raw.githubusercontent.com` a ciegas.
- **Límite de esta opción:** cuando el usuario crea un repo privado nuevo, quizá deba **añadirlo manualmente al conjunto autorizado del PAT en GitHub** antes de que aparezca. Hyper-Marker debe explicar un 404/403 sin borrar su copia local. Si se amplía acceso, no hace falta recompilar la app.
- Para la evolución multipersona/distribución: estudiar GitHub App + OAuth/PKCE o flujo de autorización recomendado, en lugar de pedir PAT indefinidamente. No crear un GitHub App con secreto cliente dentro de APK.

### API REST que utilizará el cliente

- `GET /user/repos?per_page=100...`: descubrir repositorios según permisos; paginar siguiendo `Link`, con tope de páginas de seguridad. No suponer orden/lista infinita.
- `GET /repos/{owner}/{repo}`: metadatos y rama predeterminada.
- `GET /repos/{owner}/{repo}/branches?per_page=...`: ramas de la selección (paginación). Consultar una rama concreta cuando proceda; no sondear la lista completa en cada sincronización.
- `GET /repos/{owner}/{repo}/git/ref/heads/{branch}`: comprobar commit SHA de rama; usar codificación correcta para ramas con `/`.
- `GET /repos/{owner}/{repo}/contents/{path}?ref={commitSha}`: navegar y resolver archivos de la carpeta seleccionada para un snapshot consistente; comparar blob SHA/identidad y no descargar lo que ya está guardado.
- Si las carpetas son grandes, contemplar **Git Trees API** para indexación; si `truncated=true`, recorrer subárboles por separado. No asumir que el listado Contents admite más de 1,000 entradas por directorio.
- Descarga de blobs mediante API autorizada (Content API o blob endpoint compatible con tipo/tamaño). No usar `download_url` temporal como URL persistente; para privado usar acceso autenticado seguro. Establecer límite de bytes **antes y durante** el stream.
- En endpoints compatibles, guardar `ETag`/`Last-Modified` y enviar `If-None-Match`/`If-Modified-Since`. Un `304` autenticado puede no consumir límite **primario** de la API, pero **sí es petición, usa red/CPU y puede incidir en otras protecciones**: registrarlo y aplicarle presupuestos locales.
- Procesar cabeceras `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`, `Retry-After`, `Date` y `Link`. No inventar saldos de cuenta. No llamar a `/rate_limit` constantemente si las cabeceras ya proporcionan los datos.

**La API es gratuita dentro de límites, no ilimitada.** Como referencia a fecha de la spec: GitHub documenta normalmente 5,000 peticiones/hora para autenticación PAT y 60/hora sin autenticar. La app aplica límites **mucho menores** e independientes del proveedor. Verificar documentación vigente antes de programar constantes definitivas.

## 5. Modelo de sincronización incremental y datos

### Estructura propuesta (adaptar nombres al diseño real)

Agregar entidades Room con migración **explícita no destructiva** desde el esquema actualmente vigente; inspeccionar la versión real al implementar. No reutilizar `FLOW` ni mutar una carpeta local existente como si fuera remota sin su consentimiento.

- `RemoteAccount`: ID local, proveedor GitHub, alias visible, referencia a credencial segura **(nunca el token)**, creado/estado.
- `RemoteFolder`: ID local, accountId, `repositoryId`, `owner`, `repo`, `branch`, `repoPath`, folderId de biblioteca, política auto-sync, último intento/éxito, latestCommitSha, estado y motivo de pausa.
- `RemoteEntry`: ID estable derivado de identidad de carpeta remota + ruta relativa, tipo, ruta, SHA remoto, tamaño, documento local asociado, estado (activo/retirado/descarga pendiente), marca de versión y fecha.
- `SyncRun`: ID ejecución, disparador (manual/abrir/periódico), inicio/fin, resultado, operaciones, bytes, peticiones, error clasificado, próximo intento permitido.
- `ResourceMetric`: evento pequeño acotado o agregado por día/hora: proveedor, origen, contadores, tiempo, tamaños, cache hits, bloqueos por presupuesto; sin URLs con query sensible, token ni contenido.

*Datos canónicos*: GitHub manda **únicamente** los bytes del contenido remoto. Las notas personales, anotaciones, preferencias y documentos locales son autoridades independientes. La app guarda HTML y Markdown remoto en el área privada por snapshot. Mantener IDs locales de documento al actualizar el mismo recurso remoto para preservar anotaciones. Si cambian anclas HTML, pueden quedar sin ancla: mostrarlo, no borrarlas automáticamente.

### Algoritmo de una sincronización

1. Revisar conexión, batería, política Wi‑Fi/celular, credenciales, cooldown, presupuestos; **reservar presupuesto antes de iniciar**. Adquirir exclusión mutua para una única sincronización GitHub global; manual y periódica deben compartir el mismo ejecutor.
2. Solicitar ref/SHA de rama; si no cambió desde última **sincronización exitosa y completa**, terminar sin descargar documentos, registrar request y ahorro.
3. Enumerar solo el árbol de la carpeta elegida; registrar SHA de cada archivo. Evitar recorrer fuentes de código o áreas no seleccionadas. Proteger rutas (`..`, normalización Unicode, separadores ambiguos, symlinks, submódulos y enlaces externos no seguidos).
4. Planificar `added/changed/unchanged/removed` comparando SHA. Solo obtener contenido de archivos nuevos/modificados y sus recursos relativos necesarios para HTML compatible.
5. Comprobar por archivo el formato, tamaño, número de archivos, ruta y cuotas. **No descargar archivos sin límite** por ser extensiones permitidas. HTML debe conservar estructura relativa de CSS/JS/imagenes/fuentes locales; si el paquete incompleto, informar «No se pudo importar esta vista» sin publicar documento roto. Prohibir ejecutar compilación arbitraria dentro de Android.
6. Descargar a staging privado, validar integridad y límite real de bytes; procesar con las reglas seguras existentes de `SafeImporter`/almacenamiento, o una ruta equivalente de seguridad. Nunca liberar el directorio a WebView hasta confirmar snapshot consistente.
7. Aplicar cambios de forma **atómica o compensable**: actualizar entradas/archivos, revisar borrados, publicar `latestCommitSha` como exitoso **solo al finalizar completamente**. Ante caída, dejar último snapshot legible y reintentar sin duplicar entradas.
8. Documentos borrados/movidos de GitHub: **no borrar notas locales ni anotaciones**; marcar contenido remoto «Retirado de origen», permitir conservar copia offline o limpiar explícitamente. Un error 401/403/404/red **no** equivale a borrado remoto confirmado.
9. Al salir/volver a entrar, no volver a descargar si SHA coincide. Cancelar operaciones pendientes al desvincular y suprimir workers huérfanos.

### Identidades y ramas

Identidad de carpeta remota = cuenta + `repositoryId` + rama + path canónico. La rama es parte de identidad de origen para evitar mezclar archivos de `main` y ramas de PR. Si usuario cambia rama, explicar si creará un snapshot separado o sustituirá la vista **sin perder anotaciones**; elegir una política consistente y probarla. Un rename remoto puede aparecer como baja+alta; no prometer detección sofisticada en MVP.

### Formatos

- `.md` / `.markdown` / `.html` / `.htm`; CSS, JS compilado, imágenes y fuentes solo como **dependencias** válidas del HTML de la selección. Evaluar compatibilidad con los límites del importador original; ni un archivo arbitrario se vuelve contenido ejecutable por compartir extensión.
- Para HTML recomendar carpetas de salida (`dist/`, `docs/visual/`, etc.) con `index.html` y assets relativos. No traer repositorios completos como ZIP por defecto.
- **Flow Dots queda local.** Futuro opcional: formato abierto JSON versionado con nodes/edges y un import/export seguro. No agregar sincronización de grafo en este hito.

## 6. Política preventiva de recursos: REQUISITO BLOQUEANTE

La instrumentación tiene **dos funciones separadas**: `ResourceMeter` (medir) y `ResourceGovernor` (autorizar, limitar, pausar). **Toda** petición GitHub, programada o manual, debe pasar por el mismo cliente medido y presupuestado. No aceptar bypass silencioso desde pantallas, workers, retries, prefetch o descargas de assets.

### Presupuestos de inicio propuestos (configurables y por defecto conservadores)

| Recurso | Límite inicial de Hyper-Marker | Acción |
| --- | --- | --- |
| Concurrencia GitHub | **1** operación de red a la vez, 1 ejecución de sync global | Encolar/combinar; no duplicar |
| Sync automática | **cada 60 min como mínimo** global; inicialmente solo Wi‑Fi/no medida y batería no baja; al abrir, usar caché y cooldown | Posponer, nunca bucle |
| Refresco por apertura | si han pasado **10 min** desde chequeo de la carpeta; coalescer aperturas repetidas | Mostrar caché mientras |
| Acción manual repetida | cooldown mínimo **60 s** por carpeta salvo reintento válido tras edición de ajustes; **nunca** superar bloqueos rate-limit | Mostrar hora del próximo intento |
| Solicitudes REST | **60/h y 500/día** para toda la aplicación; **30 por sync** | Pausar antes del exceso |
| Datos descargados HTTP | **30 MiB/sync, 150 MiB/día** total | Detener stream cuando llegue a cuota |
| Archivo individual | **10 MiB** MVP (ajustable tras medir compatibilidad y riesgo) | Saltar archivo e informar |
| Almacenamiento de carpetas remotas | **500 MiB** total (configurable, con alerta al 80%) | No reemplazar última copia buena; pedir limpieza selectiva |
| Errores consecutivos | 3 fallos recuperables por proveedor/carpeta | Pausa y backoff exponencial con jitter |
| Tiempo máximo por ejecución | **5 minutos** de trabajo efectivo, además de timeouts por conexión/lectura | Cancelar limpiamente, conservar snapshot |
| Historial telemetría | agregados diarios 30 días + últimos 500 eventos sin secretos | Podar automáticamente sin trabajo periódico adicional |

Estos **son límites internos de seguridad propuestos, no cuotas oficiales de GitHub ni promesas de rendimiento**. Si un contenido legítimo excede los umbrales, mostrar por qué fue detenido y permitir un ajuste explícito y razonable desde Ajustes; si cambia política, conservar cap duro de protección. No desactivar `Retry-After` ni otros límites de GitHub para forzar actualización. En celular, predeterminar descargas grandes a solo Wi‑Fi.

### Métricas persistentes (locales, sin servidor)

- Conteo por 1 h/24 h/30 días y por repo/carpeta: peticiones totales, `200/304/401/403/404/429/5xx`, errores de red, retries, peticiones bloqueadas por budget.
- Bytes **enviados/recibidos a nivel HTTP medido**: body real + cabeceras si es práctico; aclarar que no equivale exactamente a consumo facturado por operador (overhead TLS/red). Bytes evitados por SHA/cache (`estimados`, identificados como estimación).
- Tiempos de sincronización, archivos nuevos/actualizados/omitidos, bytes en disco, estado de último chequeo/sync exitoso, frecuencia de chequeos, tiempo total de actividad de red.
- Datos de GitHub: `X-RateLimit-Remaining`, `Limit`, `Reset`, `Retry-After` y instante de captura; **no presentar el margen como presupuesto propio**. Contar `304` como petición local aunque no consuma límite primario cuando esté autenticada correctamente.
- Energía/CPU: métricas **indirectas** confiables (duración de tareas, ejecución en background, condiciones de batería/carga, tiempos de CPU del proceso si API segura/asequible). **No inventar vatios, mAh ni temperatura interna de la laptop**; no hacer muestreo continuo que consuma más que el trabajo observado.
- Para el futuro servidor/agentes: unidades extensibles con `requests`, `bytes_in`, `bytes_out`, `cpu_seconds`, `gpu_seconds`, `storage_bytes`, `tokens_in`, `tokens_out`, `estimated_cost`, `provider_reported_cost`; en esta feature los campos no disponibles serán `N/A`, nunca `0` fingido.

### Comportamiento frente a errores y crecimiento

- `403`/`429`: respetar `Retry-After`, `X-RateLimit-Reset`, o pausa de al menos 60 s con backoff exponencial/jitter para límites secundarios; **nunca retries inmediatos**. Un 403 por permisos no es necesariamente rate-limit: diferenciar respuesta y pedir autorización cuando corresponda.
- `401`: suspender cuenta, pedir reautenticación; no borrar caché. `404`: distinguir ausencia de carpeta/branch o denegación de repo privado; preservar snapshot.
- Fallo persistente, configuración corrupta, presupuesto desconocido, batería baja o falta de espacio → **fail closed** para sync automática y mensaje accionable; lectura offline continúa.
- Hacer budgets con ventanas persistentes resistentes a reinicios; usar reloj monotónico durante ejecución y validaciones de wall-clock para ventanillas diarias. No dejar que reinicio, cambio de hora o reprogramación cree ráfagas de solicitudes.
- La programación es **una sola periodic work única** (`WorkManager.enqueueUniquePeriodicWork`, según dependencia fijada), al menos 15 min permitidos por Android, **60 min predeterminados** y `ExistingPeriodicWorkPolicy` que evite duplicaciones; no crear un worker nuevo por cada recomposición Compose/apertura. WorkManager puede ejecutar más tarde por políticas Android. Si múltiples repos, procesar en cola bajo el presupuesto global.
- No hacer polling cada milisegundo, `while(true)` sin control, `setInterval` en WebView, `AlarmManager` exacto para esto, ni timers recursivos de retry.
- Alertar a 80% de cuota, detener en 100%, permitir ver últimas causas y pausar todo. Si no queda espacio, conservar la última versión útil sin pérdida de notas.
- **No generar telemetría remota desde la telemetría local.** Estadísticas se guardan en Room/DataStore local; nada de analytics externo, anuncios o registro de contenido.

## 7. Seguridad y aislamiento (no negociable)

1. **Mantener intactas las restricciones WebView actuales**: cada documento con URL privada, origen opaco, control de red por documento, cookies e IndexedDB restringidas, puente validado. Tener autorización de descarga GitHub **en la capa Android** no otorga Internet ni acceso al token al HTML.
2. Contenido HTML de GitHub se considera **no confiable**, incluso desde repos privados; mismo sandbox que cualquier importación local. Probar que no abre URL privada de otro documento ni puede extraer credencial.
3. Política de path traversal, symlinks, submódulos, descargas redirigidas, zip-bombs equivalentes, tamaño, número de archivos y CSS/JS con rutas remotas sigue reglas del importador. No permitir salida del staging ni acceso al filesystem por URL importada.
4. Si HTML necesita CDN para renderizarse, mantener offline por defecto; que el usuario permita red explícitamente **por documento**, no por la carpeta entera. No garantizar que todas las SPA funcionen (workers/IndexedDB no están disponibles).
5. Excluir secretos y parámetros sensibles de Crashlytics/logcat/export de estadísticas. Idealmente exportar métricas agregadas **sin repos privados, paths completos ni tokens**.
6. Si se desvincula cuenta: revocar referencias a token y workers, permitir opción separada para preservar/borrar copia local; el usuario puede revocar token en GitHub. Una app no puede garantizar revocación remota solo borrando el secreto local.

## 8. Paquetes/cambios de arquitectura sugeridos

Respetar módulo único actual `app/`, introducir paquetes orientativos (ajustar tras estudiar código):

```text
com.hypermarker.github/
  GitHubApiClient.kt            // llamadas HTTP centralizadas + headers, ETag y límites
  GitHubAuthStore.kt            // credencial segura con Keystore, no visible a WebView
  GitHubBrowserRepository.kt    // listas y selección de cuenta/repo/branch/folder
  GitHubSyncEngine.kt           // snapshots incrementales + staging/rollback
  GitHubSyncWorker.kt           // WorkManager único + restricciones
  GitHubSyncCoordinator.kt      // mutex/coalescing manual/auto/apertura
com.hypermarker.metrics/
  ResourceMeter.kt              // contadores y agregados locales
  ResourceGovernor.kt           // presupuestos, bloqueos, cooldown
  ResourceSettings.kt           // opciones, límites y persistencia
com.hypermarker.storage/
  ... entidades/DAO/migración versionada para remote folders, entries y runs
com.hypermarker.library/
  ... UI mínima de nube/selección/estado/context menu
```

**No tomar nombres anteriores como archivos existentes.** Revisar si ya existen helpers de HTTP, storage, ajustes o WorkManager reutilizables. Elegir librería HTTP mantenida y fijar versión/licencia. Nunca duplicar fuentes de verdad (`.md` archivo autoritativo; Room índice/estado).

## 9. Secuencia de implementación para Codex

### H0 — Preparación y respaldo

- [ ] Leer documentos del repositorio y comprobar estado Git real, migraciones y datos de prueba.
- [ ] Crear rama de implementación, documento de propuesta en `docs/proposals/` y matriz de pruebas `SYNC-*`, `METRIC-*`, `SEC-GH-*`.
- [ ] Decidir/adaptar librería HTTP, almacenamiento de token actualizado, WorkManager y migración no destructiva; documentar dependencias/versión/licencias.

### H1 — Telemetría, budgets y transporte antes del primer sync

- [ ] Crear `ResourceMeter` y `ResourceGovernor` independientes de UI, con fake clock y fake transport inyectables.
- [ ] Persistir presupuestos incluso tras cerrar/reabrir app; contar bytes de streams parciales, cancelados, errores y 304.
- [ ] Implementar cliente GitHub **único**: autenticación, timeouts, paginación acotada, ETag, parseo de errores, `Retry-After`, rate limit, sanitización de logging.
- [ ] Tests unitarios para límites, 403/429, backoff, diferencias entre cuota GitHub e interna, cancelación y operaciones simultáneas.

### H2 — Conectar cuenta y seleccionar fuente

- [ ] Almacenar PAT fine-grained cifrado con Keystore + exclusión de backups; pantalla corta para conectar/rotar/desconectar.
- [ ] Listar repos paginados, buscar/ingresar `owner/repo`, elegir rama y navegar carpetas con permisos mínimos.
- [ ] Evitar listar repos no autorizados como fallos genéricos; dar explicación y pasos manuales de permiso GitHub.
- [ ] Persistir `RemoteFolder` y migración Room; añadir nube discreta en biblioteca.

### H3 — Sincronización segura y offline

- [ ] Comprobar SHA rama y archivos, descargar solo cambios, staging atómico, deduplicación, restauración tras interrupción.
- [ ] Importar Markdown y HTML con recursos relativos bajo restricciones existentes. No permitir ediciones remotas ni sobrescribir notas locales.
- [ ] Estados `actualizado/en curso/offline/pausado/error/retirado`; botón manual y lectura última copia válida.
- [ ] Casos delete/rename/branch update/permission revoked/data partially downloaded.

### H4 — Sincronización automática y pantalla consumo

- [ ] Un solo WorkManager periódico y cola global; restricciones Wi‑Fi/batería; cooldown por carpeta y budgets globales.
- [ ] Ajustes «Sincronización y consumo» con límites, total 24h/30 días, bytes, requests/304/fallos, último éxito y pausa.
- [ ] Configurar opciones explícitas; aviso al 80%; pausa y explicación al 100%.

### H5 — Pruebas de regresión y entrega

- [ ] Ejecutar suites existentes de JVM/WebView/Room/importadores/seguridad. No dar por hechos los casos que no se ejecuten.
- [ ] Tests nuevos con servidor mock + reloj virtual, sin provocar carga artificial a GitHub real.
- [ ] Probar migración Room sobre copia de una base previa y persistencia con Flow Dots, Markdown, pines y dibujos existentes.
- [ ] Prueba manual en teléfono: Wi‑Fi/datos, modo avión, reinicio, revocar token, cambiar branch, carpeta muy grande, cierre inesperado, repos públicos/privados autorizados, HTML con assets relativos.
- [ ] Verificar `lintDebug`, `testDebugUnitTest`, `assembleDebug`, y las instrumentadas cuando haya dispositivo. Verificar firma APK **antes** de instalar sobre datos personales (instalaciones anteriores tuvieron incompatibilidad de firma debug documentada).
- [ ] Actualizar `architecture.md`, `docs/security.md`, `docs/testing.md`, `docs/dependencies.md`, `progress.md`, `README.md`. Reportar pruebas reales/pendientes, hashes y restricciones de consumo.

## 10. Matriz mínima de aceptación verificable

| ID | Condición / ensayo | Resultado obligatorio |
| --- | --- | --- |
| SYNC-01 | Token fine-grained válido con acceso solo a repo A | Se puede elegir A; B no se trata como accesible por defecto |
| SYNC-02 | Repositorio creado después de instalar app | Se puede descubrir/añadir cuando GitHub haya autorizado ese repo al token |
| SYNC-03 | Elegir rama de Codex/PR distinta de main | Se ve contenido de rama elegida, no se mezcla con main |
| SYNC-04 | Abrir carpeta offline tras sync exitosa | Se leen los últimos documentos sin llamadas HTTP |
| SYNC-05 | Rama y blobs sin cambios | No vuelve a descargar archivos; se contabiliza cualquier petición realizada |
| SYNC-06 | Se modificó un Markdown o asset HTML | Solo cambian archivos necesarios; snapshot consistente y anotaciones locales conservadas |
| SYNC-07 | Red caída durante descarga | Último snapshot sigue legible y no hay publicación parcial |
| SYNC-08 | Archivo remoto eliminado o credencial revocada | Sin pérdida silenciosa de documentos/anotaciones; estado informativo |
| SYNC-09 | Documento importado intenta acceder a GitHub token/otro doc | Acceso rechazado; token nunca visible en JS/bridge |
| SYNC-10 | Rutas hostiles, submódulos, symlinks, HTML externo | Se rechazan o manejan explícitamente sin escape de sandbox |
| METRIC-01 | 304 autenticado, 200 y error HTTP | Contadores correctos; 304 cuenta localmente; rate limit remoto diferenciado |
| METRIC-02 | 100 aperturas de carpeta / recomposiciones | No se multiplican workers ni sync concurrentes |
| METRIC-03 | 403/429 con Retry-After / reset | No se vuelve a llamar antes de lo permitido; pausa persistente |
| METRIC-04 | Cuotas por hora/día/tamaño excedidas | Se corta antes del límite, con caché previa intacta y motivo claro |
| METRIC-05 | Reinicio/cambio de hora/configuración corrupta | No libera tormenta de reintentos |
| METRIC-06 | API falsa: cientos de repos/miles de archivos | Test de escala con mocks y presupuestos aplicados, sin bombardear GitHub |
| SEC-GH-01 | Logs/backup/export/revocar credencial | Token no aparece ni se restaura inseguramente |
| REG-01 | Suite V1 + Flow Dots + migración Room | Sin regresiones funcionales ni pérdida de datos |

**Criterio de cierre:** tests pertinentes documentados y aprobados; límites habilitados *antes* del primer worker automático; prueba offline, protección de tokens, persistencia tras reinicio y migración real verificadas; funcionalidad con controles minimalistas; reporte preciso de limitaciones.

## 11. Lista de tareas MANUALES para el propietario

Estas acciones corresponden al usuario, no al agente; **no entregar token al chat ni enviarlo por GitHub**.

### Antes de implementar

- [ ] Decidir repositorio piloto: sugerencia `RubenDarioMena/Codemageddon` y una carpeta pequeña de documentación/HTML compilado (ej. `docs/`); confirmar que realmente existe y contiene documentos útiles.
- [ ] Confirmar si también se probará un repositorio **privado** y cuál. Evitar datos de empresa o cualquier secreto en pruebas.
- [ ] Respaldar **Hyper-Marker del teléfono** antes de instalar una APK nueva; comprobar si el APK usa la misma firma. Ante `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, **no desinstalar** sin respaldar/verificar restauración.

### Al probar autenticación

- [ ] Abrir [GitHub: Fine-grained PATs](https://github.com/settings/personal-access-tokens/new) desde navegador de confianza e iniciar sesión.
- [ ] Elegir cuenta `Resource owner`, `Only select repositories`, y solo los repos de prueba. Permisos `Contents → Read-only` y metadata básica. Poner nombre descriptivo (`Hyper-Marker Android Read Only`) y expiración razonable.
- [ ] Copiar el token **una vez dentro del campo seguro en la APK**, no dentro de Codex/ChatGPT, documentación o commit. La app debe confirmar permisos sin mostrar la credencial.
- [ ] Si aparece un repo privado nuevo no autorizado, volver a ajustes de token en GitHub y agregarlo a la selección; regresar a Hyper-Marker y refrescar repositorios.
- [ ] Si no se usa más el token, revocarlo desde GitHub y desconectar app.

### Durante aceptación

- [ ] Elegir repo, rama `main` y una carpeta; forzar sincronización; poner teléfono en modo avión y verificar lectura.
- [ ] Pedir a Codex Cloud crear o actualizar un `.md` pequeño **en una rama conocida**, confirmar dónde lo dejó (rama/PR), escogerla en Hyper-Marker y actualizar. No esperar que un PR no fusionado cambie `main`.
- [ ] Abrir un HTML con CSS/JS/imagenes relativas de la carpeta y revisar que se renderice offline, con el aislamiento ya existente.
- [ ] Comprobar que una anotación local persiste tras refrescar el HTML remoto y que una nota local no se reescribe.
- [ ] Revisar Ajustes → Sincronización y consumo: peticiones, MB recibidos, tasa de fallos, 304, pausas. Probar modo Wi‑Fi y datos móviles sin realizar carga innecesaria.
- [ ] Ajustar políticas de sync solo si los contadores reales lo justifican; registrar excepciones y motivos.

### Más adelante — servidor casero

- [ ] **Ninguna acción necesaria ahora.** Cuando exista laptop, medir RAM/CPU/disco/red/energía del hardware antes de decidir Docker, sincronización push y monitoreo de agentes. No acoplar Hyper-Marker a una IP de LAN fija ni a una API hipotética.

## 12. Riesgos abiertos y decisiones aplazadas

- PAT manual es adecuado como **MVP personal**, no necesariamente UX final distribuible. Reconsiderar OAuth/GitHub App si se comparte la aplicación.
- Definir política de archivos demasiado grandes, ignorar rutas y priorización visual en repositorios que contienen muchos artefactos. Los límites propuestos requieren calibración con uso real.
- Elegir si una carpeta remota tiene copia local editable por separado o se muestra con edición deshabilitada. Nunca convertir archivos remotos en autoridad de notas locales.
- El hecho de que GitHub tenga cambios no significa que el contenido esté compilado o que los HTML sean compatibles con CSP/Worker/IndexedDB de Hyper-Marker.
- `Flow Dots` usa grafos dirigidos acíclicos persistidos en Room; **no hay formato de intercambio estable** en la implementación actual. Diseñar JSON v1 aparte cuando sea requerido.
- Los presupuestos del dispositivo **no miden** automáticamente consumo de los agentes en la nube. Una futura skill general deberá definir cómo medir costos reales/tokens/CPU/energía por cada backend/proveedor.

## 13. Fuentes técnicas oficiales

- GitHub — [Fine-grained personal access tokens](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens).
- GitHub — [Rate limits REST](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api).
- GitHub — [Best practices; ETag/304 y rate limits](https://docs.github.com/en/rest/using-the-rest-api/best-practices-for-using-the-rest-api).
- GitHub — [Lista de repositorios autorizados](https://docs.github.com/en/rest/repos/repos#list-repositories-for-the-authenticated-user).
- GitHub — [Contenido, límites y download_url](https://docs.github.com/en/rest/repos/contents#get-repository-content).
- GitHub — [Git trees, truncamiento](https://docs.github.com/en/rest/git/trees#get-a-tree).
- GitHub — [Git references](https://docs.github.com/en/rest/git/refs#get-a-reference).
- Android — [WorkManager, intervalo mínimo de 15 min y ejecución inexacta](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest).
- Android — [EncryptedSharedPreferences deprecado](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences).

---

**Mensaje sugerido para Codex al entregar este archivo:** «Implementa esta especificación por hitos en Hyper-Marker. Primero lee AGENTS.md y Agent.md, inspecciona el estado real del repositorio, crea tests para ResourceGovernor y transporte HTTP antes de la sincronización y respeta todas las salvaguardas. No uses credenciales reales ni afirmes pruebas no ejecutadas. La conexión al servidor doméstico y la skill general de métricas preventivas quedan para otra etapa. Si surgen decisiones incompatibles con el diseño existente, documenta el bloqueo y pregunta antes de modificar interfaces principales». 
