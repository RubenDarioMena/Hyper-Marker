# Progreso y relevo

Última actualización: **2026-11-16** (America/Mexico_City).

## Estado real

**V1 implementada en Kotlin/Compose, con APK debug generado y suite automatizada aprobada.** No iniciar nuevamente H1 ni desarrollar V1.1 antes de recoger la aceptación de uso del propietario. La evidencia y los límites de cobertura están en [docs/validation.md](docs/validation.md).

## Entrega por hito

| Hito | Estado y evidencia |
| --- | --- |
| H1 — Proyecto, Room y biblioteca | Implementado. Gradle wrapper, versiones fijadas, esquema Room v1 exportado, biblioteca anidada, búsqueda, CRUD, duplicación y recuperación. Tests JVM de jerarquía, copias y reapertura real de disco/Room aprobados. |
| H2 — Importación | Implementado. SAF archivo/árbol, Sharesheet, ZIP, staging privado, selección de entrada, límites, CRC y rechazo de traversal/symlinks/colisiones. Pruebas JVM e instrumentadas de URIs, copia independiente y ZIP aprobadas. |
| H3 — Lector | Implementado. WebView inmersivo, controles transitorios, navegación, zoom, consola, autoscroll, React compilado y red opt-in. Pruebas reales de WebView en Android 11 y 16 aprobadas. |
| H4 — Markdown | Implementado. Source y Visual editable con Milkdown local, autosave, undo/redo, fuente AtomicFile y protección de sintaxis no preservable. Flujo Android editar/cambiar modo/recrear aprobado. |
| H5 — Pines | Implementado. Long-press real con resaltado mientras se sostiene y actualización al deslizar; guardar/cancelar, editar, reanclar, borrar, undo/redo y Unanchored. Pruebas táctiles Android y DOM aprobadas. |
| H6 — Dibujos | Implementado. Pluma/resaltador/borrador, color/ancho, SVG en coordenadas de página, historial, persistencia y limpieza confirmada. Pruebas de trazo y regreso a View aprobadas. |
| H7 — Endurecimiento | Implementado y validado con automatización. Se detectó y corrigió un escape WebRTC vía iframe vacío; origen opaco y restricciones tempranas ahora lo bloquean. La cobertura manual de accesibilidad y hardware sigue limitada, como detalla el reporte. |
| H8 — APK y relevo | APK debug firmado generado desde archivo Git limpio, instalado y abierto en el emulador. Hash y fuente registrados en el reporte. Instrucciones, licencias, fixtures y CI incluidos. |

## Resultados ejecutados

- **13/13** tests JVM aprobados; **16/16** instrumentados en emulador Android 16; **16/16** en Moto g71 5G Android 11 antes de desconectarlo.
- **7/7** pruebas web aprobadas: controlador, Milkdown editable sin red y React compilado con assets relativos.
- `lintDebug` aprobado sin errores; quedan advertencias de actualizaciones de dependencias y recomendaciones de Android/estilo. No se afirma lint sin advertencias.
- `assembleDebug` aprobado; paquete `com.hypermarker`, versión `1.0.0` (1), mínimo API 26.
- El último ajuste del sheet de herramientas evita su expansión parcial y pasó las 16 pruebas en el emulador. La suite del Moto corresponde a la revisión inmediatamente anterior a ese ajuste visual; no se volvió a usar el teléfono después de la instrucción de desconexión.
- Build limpio del commit fuente `97bb5dfb1dc85c4ee1598f1baf3f25fef4aa0ad8` aprobado; APK entregado en `artifacts/Hyper-Marker-1.0.0-debug.apk`, firma verificada e instalación/arranque reales aprobados.
- Verificación documental: 47 enlaces locales resolubles; diff de archivos propios sin problemas de whitespace. El bundle generado y textos de licencias conservan whitespace de terceros.
- **CI remota PASS:** ejecución [39362311919](https://github.com/RubenDarioMena/Hyper-Marker/actions/runs/39362311919) del commit `02e6b8c`; JVM/lint/build y upload del APK/reportes aprobados. Corregido el uso del paquete SDK obsoleto `tools` en el setup.
- Especificación original intacta: SHA-256 `c779db1ae246f5aba1e1084df4b0c7b3a4fc669ead8f4ded080846de5c44fdfb`.

## Decisiones y límites de compatibilidad

- Android nativo, módulo `app`; JDK 17, Gradle 8.13, AGP 8.12.0, Kotlin 2.1.20, compile/target SDK 36. Versiones/licencias en [docs/dependencies.md](docs/dependencies.md).
- Originales HTML inmutables; anotaciones JSON v1 en Room; Markdown autoritativo en archivo privado. Undo/redo es de sesión; el estado final sí se persiste.
- Cada documento tiene URL propia y CSP con origen opaco. `localStorage`/`sessionStorage` son de sesión en memoria; IndexedDB, workers, WebRTC y acceso directo entre frames están deshabilitados. Compatibilidad deliberadamente acotada; no prometer ejecución de cualquier web arbitraria.
- Red apagada por documento; permitir HTTPS/WSS no concede acceso general a Android. La revocación destruye el renderer anterior. WebView actualizado requerido para scripts/anotaciones.
- Canvas/SVG/iframe se anclan como superficie; trazos pueden desalinearse ante reflujo. React debe importarse compilado con rutas relativas; JSX/TSX no se ejecuta.
- Markdown no preservable permanece en Source con aviso. Autosave tiene una ventana aproximada de 500 ms; salir guarda explícitamente.
- No ejecutado: TalkBack completo, lápiz físico, dispositivo API 26, todas las variantes OEM/proveedores SAF y pruebas exhaustivas de falta de espacio/muerte del proceso/renderer. No equivalen a fallos observados ni a casos PASS.
- Exportación/backup, RAR, sincronización e inspector avanzado quedan fuera de V1. La licencia del código propio y firma/distribución de producción requieren decisión del propietario.

## Rediseño UI/UX del lector y Markdown (2026-10-22)

- Fuente: `main` sincronizado con GitHub (fast-forward a `6149943`), cambios sin commit al cierre de esta sesión.
- **Autorización explícita del propietario:** rediseño minimalista del visor, cruceta reasignable y "Editar texto". Esta última es un override de V1.1 adelantado por esa solicitud; la reconciliación avanzada de anclas sigue pendiente.
- Lector: se eliminó el botón flotante. Un toque fuera de contenido interactivo alterna el botón de opciones (arriba a la derecha) y los modos Ver / Editar / Dibujar (abajo) como botones de cristal translúcido con tono adaptado al fondo. En Ver se ocultan solos a los 5 s. Deshacer/Rehacer aparecen junto a opciones en Editar y Dibujar.
- Cruceta por pulsación prolongada en los tres modos: Ver (Autoscroll, Acercar elemento, Zoom original, Recargar), Editar (Nota pin, Editar texto, Borrar adjuntos, Borrar cambios) y Dibujar (Color, Grosor, Herramienta, Estabilizar, este último aún no disponible). Reasignable desde la cruceta o el menú, persistente en `SharedPreferences`; el resto de acciones quedan en el menú.
- Editar conserva el gesto obligatorio (resaltado vivo y deslizamiento); al soltar, la selección queda resaltada y aparece la cruceta. "Mode PIN" pasó a llamarse `EDIT` en el protocolo. Nuevos eventos `tap`, `hold` y `longpress`; nuevo tipo de anotación `TEXT` sin cambio de esquema Room.
- Markdown: hoja blanca u oscura, barra superior monocroma (estilo/tipo de letra, negrita, cursiva, tachado, código, enlace, listas, cita, bloque de código, tabla y separador) que actúa sobre la selección en Fuente y Visual. Las tablas GFM bien formadas ya pueden editarse en Visual. "Source" se rotula "Fuente".
- Validación con el código final: JVM **22/22**; web **10/10** (7 controlador + 3 editor/React); instrumentadas **17/17** en emulador Android 16 (WebView 153.0.8010.36) tras reinicio en frío; `lintDebug` 0 errores, 40 advertencias; `assembleDebug` PASS. Detalle y fallos ambientales en [docs/validation.md](docs/validation.md).
- Revisión visual manual en emulador: cromo, menú, cruceta en los tres modos, acercar elemento, panel de color, editar/restaurar texto y hoja Markdown clara/oscura con formato sobre selección.
- Límites nuevos: sin desenfoque real detrás del cristal (Compose no puede desenfocar el WebView); en Ver la pulsación prolongada reemplaza la selección de texto nativa; la sustitución de texto aplana el formato interno del elemento; Visual normaliza el relleno de tablas y usa `*` en listas.
- Teléfono S24 Ultra (SM-S928U, Android 16): la instalación existente (1.0.0, 2026-10-20) usaba otro certificado debug y `adb install -r` fue rechazado (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Con autorización explícita del propietario se respaldaron los datos con `run-as` (`artifacts/phone-backup/`, ignorado por Git), se desinstaló, se instaló el APK `075df2ef…` y se restauraron `files/`, `databases/` y `shared_prefs/`. Esquema Room idéntico (`a7f7a30c…`), 7 archivos con SHA-256 igual al respaldo y los 3 documentos (2 HTML y 1 Markdown, en RTS auto / Factorio) abiertos en el teléfono. Las suites instrumentadas no se ejecutaron en el teléfono porque desinstalan la app.
- Observación en el teléfono: en páginas con su propia barra inferior de navegación, la barra de modos de cristal la cubre mientras está visible (se oculta sola en Ver).
- Siguiente paso: usar el nuevo flujo con documentos reales en el teléfono; después, commit/push y actualización del APK descargable en `downloads/`.

## Siguiente paso concreto

Descargar el [APK debug versionado](downloads/README.md), usarlo con documentos representativos y recoger aceptación de uso. Revisar la compatibilidad de firma con cualquier instalación anterior antes de reemplazarla. Priorizar la validación manual de TalkBack, lápiz y proveedores SAF del teléfono habitual antes de distribuir una versión de producción. Para continuar, revisar este archivo y el reporte; no confundir cobertura automatizada aprobada con certificación completa de la matriz manual.

## Entrega desde cloud para descarga por Git

- Fuente actualizada desde `main`: `58af2aa077bc935e2fd29d7d4e20425134c767cd`. Sin cambios al código de aplicación.
- APK generado y firmado en Linux cloud con Temurin 17.0.16+8, Gradle 8.13, SDK 36 r2 y Build Tools 35.0.0. Instalación de herramientas verificada por checksum; repetición del instalador y `assembleDebug` aprobadas.
- Pruebas JVM **13/13 PASS**, sin omisiones; `lintDebug` **PASS**, 20 advertencias y cero errores. El primer intento falló en cinco pruebas por la caché de Robolectric en un home de sólo lectura; se configuró un home JVM escribible y la repetición pasó completa.
- Firma APK v2, applicationId, versión, SDK y cuatro arquitecturas verificados. El hash de la copia coincide con el APK de build.
- Por solicitud explícita del propietario, se agrega `downloads/Hyper-Marker-1.0.0-debug.apk` a Git con una excepción específica en `.gitignore`, su SHA-256 y el [registro de entrega](downloads/README.md). Las claves privadas y otras salidas siguen fuera de Git.
- Esta compilación usa su propio certificado debug; no promete actualizar instalaciones firmadas en otro entorno. No se ejecutaron pruebas instrumentadas ni instalación/arranque en dispositivo en esta sesión cloud. Las pruebas Android de la entrega original siguen documentadas por separado.
- Siguiente paso: descargar el APK desde GitHub y realizar pruebas de uso en el teléfono, conservando cualquier instalación con datos importantes ante una incompatibilidad de firma.

## Continuidad Fuente/Visual y barra Markdown (2026-10-26)

- Estado Git al iniciar: `main` y `origin/main` coincidían en `56ee1a7`; árbol de trabajo limpio tras `git fetch origin`. No hubo divergencia que requiriese pull o merge.
- El editor Fuente usa `TextFieldState` con `ScrollState` expuesto; al pasar a Visual se aplica su fracción vertical. Visual entrega Markdown y fracción de desplazamiento en una sola captura al volver a Fuente. También sitúa el cursor cerca de esa fracción, y conserva el último valor Visual hasta que el usuario interactúe con Fuente. La correspondencia es proporcional: la altura de bloques y el ajuste de líneas pueden diferir entre modos, por lo que no se promete la misma línea exacta.
- Cabecera, selector de modo y barra de formato de Markdown se ocultan tras 5 s sin interacción. Un botón accesible «Mostrar herramientas» los restaura; tocar la hoja también los muestra. El temporizador espera mientras se mantiene el dedo, mientras el menú de estilos está abierto y se desactiva con exploración táctil. La preferencia de 5 s sigue el valor por defecto de los controles de [Android Media3](https://developer.android.com/reference/androidx/media3/ui/PlayerControlView); no se encontró un estándar universal de 4 s para editores.
- El HTML original y los archivos Markdown autoritativos siguen separados de anotaciones. No se cambió el esquema ni la política de red.
- Idiomas: **evaluación, sin selector implementado todavía**. El proyecto carece de `strings.xml` y hay aproximadamente 169 líneas con `Text`, descripciones, etiquetas o menús en los 21 archivos Kotlin. La migración inicial consiste en extraer textos de UI y errores a recursos, agregar español e inglés, revisar plurales/longitudes, configurar idiomas por app en Android 13+ y un selector compatible con API 26–32. La guía oficial describe [`stringResource`](https://developer.android.com/guide/topics/resources/localization) y [preferencias de idioma por app](https://developer.android.com/guide/topics/resources/app-languages). Estimación preliminar: **2–3 días de trabajo** para español/inglés, selector y QA en dos versiones Android; después **0,5–1,5 días por idioma** con traducción revisada. No traduce automáticamente el contenido importado ni las notas del usuario.
- Validación: `npm run build` PASS; pruebas web **11/11 PASS** (incluida captura/restauración de posición); `testDebugUnitTest` **22/22 PASS**, `assembleDebug` y `lintDebug` PASS con JBR de Android Studio y SDK local. Una suite instrumentada previa al último ajuste ejecutó **17 casos, 15 PASS y 2 FAIL**: `AppWorkflowTest` buscó el cromo del lector después de su ocultación automática; `WebViewIntegrationTest.autoscrollPausesOnTouchAndSupportsReverseDirection` falló en el emulador. El primero se ajustó en la prueba; el segundo sigue sin resolverse ni atribuirse a este cambio. Dos ejecuciones aisladas de MD-06 detectaron inicialmente una vuelta al 0 % y motivaron el cambio a `TextFieldState` y el respaldo de posición. La ejecución instrumentada del código final **no está verificada**: el emulador de 2 GiB sufrió presión de memoria, reportó timeouts de `adb` y cerró el proceso de instrumentación antes de ejecutar MD-06, incluso tras reiniciarlo. No se registra MD-06 como PASS.
- Siguiente paso concreto: ejecutar MD-06 en un emulador/dispositivo con memoria suficiente y corregir cualquier discrepancia visual que aparezca. Repetir el caso de autoscroll en un entorno estable antes de atribuirle un fallo funcional. La internacionalización queda pendiente de implementación.

## Ruedas opcionales, accesos rápidos y contraste (2026-10-26)

- Añadida en el menú del lector la preferencia persistente «Ruedas de acceso rápido» bajo el título del HTML. Al desactivarla, se oculta la cruceta y se reemplaza la barra Ver/Editar/Dibujar por Autoscroll/Pin/Dibujar; Pin queda armado hasta el próximo toque y abre el editor de la nota; Dibujar entra con pluma básica.
- En la rueda Ver, Configuración reemplaza Zoom original; las disposiciones guardadas que tenían Zoom original se migran al leerlas y esa acción sigue disponible en el menú. Los botones translúcidos usan borde gris oscuro semitransparente.
- Validación local repetida: `testDebugUnitTest` **22/22 PASS**, `assembleDebug` y `lintDebug` PASS; web **12/12 PASS**. El APK instrumentado se compiló con `:app:packageDebugAndroidTest`.
- En S24 Ultra (SM-S928U, Android 16), el caso dirigido `quickControlsArmPinAndSettingsRestoreWheels` pasó **1/1**: apagar ruedas, comprobar Pin sin botones de modo, tocar el elemento y abrir el editor, cerrar sin guardar (sin crear anotación) y volver a activar la barra de modos. La prueba abre el HTML importado directamente para centrarse en el lector; no valida la navegación de biblioteca. Autoscroll, Dibujar, Configuración en la rueda Ver y el borde no tuvieron una comprobación física independiente en esta corrida.
- Se intentó ampliar la corrida para esas acciones, pero el teléfono volvió a mostrar el panel de notificaciones/pantalla de bloqueo; ese intento no se registra como PASS. El APK de prueba se retiró y la pantalla volvió a su configuración anterior.
- Una invocación inicial de `connectedDebugAndroidTest` detectó tanto el S24 como el emulador. Al detenerla, el paquete del lector dejó de estar instalado en el teléfono; se reinstaló el APK debug. Como `allowBackup=false`, la base privada original pudo eliminarse. Tras la elección explícita del propietario, se restauró desde `artifacts/phone-backup/hypermarker-s24-pre-uninstall.tar` la copia del **22 de octubre**: base con 3 documentos y 0 anotaciones, más los originales privados (2 HTML y 1 fuente Markdown). La app volvió a abrir. La copia puede no incluir cambios posteriores al 22; se conservó sin modificar el archivo de respaldo. El paquete de pruebas se retiró y `stay_on_while_plugged_in` volvió a su valor anterior (0).
- Siguiente paso concreto: confirmar que la copia restaurada contiene el estado esperado del teléfono; revisar el diff y solicitar aceptación antes de crear un commit o distribuir otro APK.

## Flow Dots: propuesta y prototipo F1+F3 (2026-10-26)

- Fuente: `main` en `9e27ed3`, con cambios sin commit de otra sesión (Codex) en `viewer/`, `controller.js`, pruebas y documentos; no se tocaron esos archivos de código. Sin commit.
- **Autorización explícita del propietario:** iniciar Flow Dots antes de la aceptación de uso de V1, con grafo dirigido sin ciclos y cancelación al soltar una conexión en vacío. Registrada en [docs/proposals/flow-dots.md](docs/proposals/flow-dots.md); la especificación original no se modificó.
- Cambios: Room **v2** con `flow_nodes`/`flow_edges` (tombstones `deletedAt` y `updatedAt` pensados para sincronizar) y `MIGRATION_1_2` explícita; esquema `2.json` exportado. Tipo de documento `FLOW` en biblioteca («Nuevo Flow», icono propio, duplicar/borrar con el grafo). Paquete `flow/`: `FlowGraph` (ciclos, plegado por alcanzabilidad, búsqueda del punto más cercano), `FlowCamera`, `FlowViewModel`, `FlowScreen` (canvas, gestos, Reordenar, tarjeta provisional, «Conectar con…», quitar conexiones, borrar con confirmación).
- Validación: `testDebugUnitTest` **31/31 PASS** (9 nuevos: grafo, cámara, repositorio, duplicado/borrado y migración de una base v1 creada con el esquema exportado); `lintDebug` 0 errores, 42 advertencias, ninguna en archivos de Flow; `assembleDebug` PASS. JBR de Android Studio, Gradle offline.
- Prueba manual en emulador Android 16 (sdk_gphone64_arm64) con `adb`: FLOW-01 crear, FLOW-02, FLOW-03 (dos conexiones), FLOW-04 (aviso de ciclo y cancelación en vacío), FLOW-05 (plegado con «+2» y despliegue), FLOW-06 (mover en Reordenar) y Encuadrar: **PASS**. Persistencia tras salir/reabrir y tras reinstalar el APK: PASS. Corregidos durante la prueba: el gesto leía un conjunto de padres obsoleto y decía que un padre no tenía hijos; las flechas que llegan desde arriba tapaban el título del hijo.
- No verificado: zoom con dos dedos (adb no inyecta multitouch), duplicar/renombrar/mover un Flow desde la UI (cubiertos sólo por pruebas JVM), TalkBack, rendimiento con cientos de puntos, pruebas instrumentadas y uso en el S24.
- Limitaciones del prototipo: la tarjeta del punto seleccionado cubre la parte inferior del canvas; el editor es un diálogo simple (F2 lo sustituye por la nota rica a pantalla completa); sin undo/redo ni fotos; la cámara y la selección se conservan mientras viva la actividad.
- **Precaución:** instalar este APK en el S24 migrará su base real a v2. Respaldar antes (`run-as`) y no mezclarlo con la recuperación pendiente de la otra sesión.
- Siguiente paso concreto: probar el prototipo con dedo en un dispositivo (pellizco, sensación de los 500 ms, toques accidentales en vacío) y decidir ajustes antes de F2.

## Registro para futuras sesiones

```text
Fecha y commit fuente:
Hito/casos trabajados:
Cambios completados:
Decisiones confirmadas o modificadas y motivo:
Validación: comando/pasos, entorno, resultado, evidencia:
Limitaciones/bloqueos y pruebas no ejecutadas:
Siguiente paso concreto:
```

## GitHub Sync: cierre de implementación y entrega al S24 (2026-10-27)

- Alcance: terminar los pendientes de GitHub Sync. Posteriormente el propietario indicó «yo lo haré, pásalo al S24»: se entregó la build para su comprobación manual, conservando los fallos de automatización como pendientes.
- Fuente de código: `899499ad55b64f3a7a62a8dbcdf14dbee5288f10`, rama `codex/github-sync`. Se conservaron e integraron los cambios locales de la implementación anterior; la especificación original sigue intacta (SHA-256 `c779db1ae246f5aba1e1084df4b0c7b3a4fc669ead8f4ded080846de5c44fdfb`).
- Completado: panel de consumo por fuente/periodo, exportación anonimizada por SAF, espacio/CPU/batería y límites; paginación de ramas/repos; suspensión persistida de PAT rechazado; pantalla segura; worker público sin PAT y cancelación al desconectar; reloj monotónico/arranque y corrupción fail-closed; stream limitado por saldo antes de leer; exclusión mutua de publicación/copia/desvinculación/recuperación; limpieza de staging/versiones; renderer fijado a una generación; conservación de retirados. Corregida la carga de ramas desde owner/repo manual y la carrera al copiar Markdown remoto mientras se sincroniza.
- Validación: **48/48 JVM**, **13/13 web**, `assembleDebug`, `assembleDebugAndroidTest` y `lintDebug` PASS (0 errores, 49 advertencias). GitHub público real: descarga, lista `main`, CSS/SVG/JS offline y reapertura del snapshot después de reiniciar el proceso PASS. WorkManager, Keystore sintético y aislamiento de contenido remoto PASS en Android.
- **No se declara suite Android completa aprobada:** última corrida **21/24 PASS**, tres fallos (timeouts de evaluación en Markdown y flujo compartido de pines, y transacción de trazo no observada). Hubo resultados variables y ANR de System UI en el AVD anterior; no se atribuyen definitivamente los tres fallos finales al entorno ni al producto. Detalle en [docs/github-sync-validation.md](docs/github-sync-validation.md).
- S24 Ultra: respaldo previo en `artifacts/phone-backup/s24-github-sync-2026-10-27_011640/` con dos TAR idénticos, extracción validada, SQLite íntegra y APK previo conservado. Firma idéntica; `adb install -r` PASS sin desinstalar. Migración v2→v3 conservó exactamente **6 carpetas, 6 documentos, 1 anotación, 4 preferencias, 2 puntos y 1 conexión**; todas sus filas iguales y **9 archivos canónicos** con el mismo SHA-256. La app quedó abierta para el propietario; no se instrumentó el teléfono personal.
- APK instalado: `artifacts/Hyper-Marker-github-sync-debug.apk`, SHA-256 `d8db8249fa6fa895b57e9104dcc6f5796a60b2538585df2f1dba9488e07e6280`. El APK público de `downloads/` sigue siendo anterior a Sync, pendiente de aceptación; no se publica como verificado un reemplazo.
- Siguiente paso concreto: el propietario prueba PAT privado, repo nuevo autorizado, rama de PR, actualización, revocación, Wi-Fi/datos, lectura offline tras reinicio y los flujos Markdown/pin/dibujo. Quedan accesibilidad y cadencia periódica real bajo Samsung. Registrar resultados antes de declarar aceptación completa o sustituir el APK descargable.

## Navegador HTTPS, copias offline, Abrir con e instalación S24 (2026-11-02)

- Fuente: `codex/github-sync`, base `d6c6e5e`, árbol limpio al comenzar. Implementación y documentación sin commit al cierre; no se publicó ni reemplazó el APK de `downloads/`.
- **Autorización explícita:** navegador con barra de búsqueda/URL, HTTPS por defecto, descargar la página actual para estudiar sin conexión; posteriormente «Abrir con» HTML e instalación en el Android conectado. Decisión separada en [web-browser.md](docs/proposals/web-browser.md); especificación original intacta.
- Implementado: `BrowserScreen`/`BrowserViewModel`/`BrowserWebView`, búsqueda DuckDuckGo o dominio/URL HTTPS, back/forward/reload, entrada desde biblioteca/menú y enlaces tocados del lector. Navegador sin bridge, AssetLoader, accesos de archivo ni permisos web. Los HTML nuevos (incluidos los nuevos paquetes GitHub) permiten recursos HTTPS/WSS; se respetan preferencias anteriores. Duplicados/copias locales conservan su comportamiento offline.
- Descarga: MHTML real de Chromium, original conservado aparte, conversión limitada a proyecto local con CSS/imágenes y referencias reescritas. Publicación compensada de `WEB_SNAPSHOT` en carpeta actual, apertura en lector con anotaciones separadas y enlace a la página original. Las capturas nunca habilitan red ni scripts originales; se impone también ante una preferencia inconsistente. No dependen de caché. Sin migración Room.
- Android: filtro `ACTION_VIEW` para `text/html` / `application/xhtml+xml`, URI de contenido/archivo; importación privada HTML/HTM/XHTML. Rutas `file://` canónicas limitadas a `/storage/`; ningún permiso general de archivos nuevo.
- Validación: **52/52 JVM**, **13/13 web**, `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` **PASS** (0 errores, 34 advertencias). **22/22 Android dirigidas** en emulador API 36, WebView 156.0.8078.47, 4 GiB; navegador repetido **4/4** tras añadir aserción de ausencia de bridge. Captura/reapertura real con CSS/imagen/texto dinámico, bloqueo de scripts/red, Abrir con HTM y regresiones de pines/dibujos/importación PASS. UI pública HTTPS → descargar → biblioteca → modo avión → reiniciar proceso → leer copia PASS. No se declara suite Android completa ni aceptación V1. [Reporte](docs/web-browser-validation.md).
- APK local `artifacts/Hyper-Marker-web-offline-debug.apk`, SHA-256 `0b4de7c29e8a0b498ba01929168c7caa9f51c73c875f9d67b62ddcadf63457da`, firma verificada. Entorno real: JBR 21.0.10 de Android Studio, Gradle 8.13/SDK 36, bytecode configurado a 17.
- S24 Ultra SM-S928U Android 16: firma compatible; respaldo previo verificado en `artifacts/phone-backup/s24-web-offline-2026-11-02_151404/`. Instalación `-r` **Success**, sin desinstalar. Antes/después se conservaron todas las filas (**11 documentos, 9 carpetas, 1 anotación, 9 preferencias, 2 puntos, 1 conexión, 1 fuente remota**) y **267 archivos privados** idénticos. Hash del APK extraído del teléfono igual al validado. Resolución `ACTION_VIEW text/html`, proceso y actividad en primer plano PASS. No se instrumentó el teléfono personal.
- Límites: captura estática de recursos ya cargados; sin garantía para vídeos/canvas/frames ni funciones del servidor. Navegador sin cookies/sesiones persistentes; algunos sitios autenticados no funcionarán. `saveWebArchive` no admite cuota/cancelación mientras escribe; el límite de 32 MiB se comprueba al convertir, con timeout y limpieza. Historial tras rotación, accesibilidad, API 26 y gestores SAF adicionales no comprobados.
- **Siguiente paso concreto:** el propietario prueba «Abrir con» desde su gestor habitual, sus URL reales y una descarga seguida de modo avión. Después registrar aceptación/ajustes y revisar el diff para un commit; el APK público y pendientes de V1/Sync siguen sin declararse aceptados.

## Samsung Tab: instalación completada (2026-11-02)

- Samsung SM-P613. Respaldo previo TAR legible y APK anterior conservados en `artifacts/phone-backup/tab-web-offline-20261102-152147/`. La firma anterior era incompatible; con autorización explícita se desinstaló. Una desconexión USB interrumpió el primer intento de instalación nueva. Tras reconectar, `adb -s <tablet> install -r artifacts/Hyper-Marker-web-offline-debug.apk`: Success. `am start -W`: Status ok, arranque COLD en 2637 ms; proceso activo comprobado. Instalación limpia, sin restaurar el respaldo. No se ejecutaron pruebas instrumentadas en la tablet. Siguiente paso: probar páginas reales, descarga offline y Abrir con desde el gestor de archivos.

## Publicación al repositorio (2026-11-02)

- Por solicitud explícita del propietario se prepara commit y push de navegador HTTPS, capturas offline, Abrir con HTML, pruebas y documentación en `codex/github-sync`. Remoto comprobado sin divergencia antes de publicar. Se conservan las validaciones anteriores (52 JVM, 13 web, 22 Android dirigidas); no hubo cambios de código posteriores a esas comprobaciones. Respaldos, APK locales y datos personales quedan fuera del commit. La copia pública de `downloads/` continúa siendo la anterior. Siguiente paso: comprobar aceptación de uso en S24/Tab y resultados de CI remota, sin darla por aprobada por el solo push.

## ZIP de GitHub y eliminación de cuotas locales (2026-11-08)

- Solicitud explícita del propietario: eliminar el límite local observado en el teléfono y descargar repositorios/carpetas como ZIP; conservar los flujos de archivos individuales, como HTML/APK.
- Base de trabajo: rama remota `codex/github-sync`, con el navegador HTTPS y sincronización que usa la instalación mostrada. `main` no contiene esa función. Checkout nuevo, sin cambios ajenos; cambio aislado en una rama de revisión, sin instalar en teléfono.
- Eliminadas cuotas de solicitudes por hora/día/ejecución y presupuestos de tráfico diario/por ejecución, incluida la reducción sin token. Se conserva medición, pausa manual, rechazo de credencial, exclusión mutua, validación del reloj y espera indicada por GitHub. Se elimina también el mínimo entre refrescos manuales exitosos; los errores mantienen su backoff.
- Al cambiar la revisión se descarga un ZIP por commit, en vez de un blob por archivo. La navegación y enumeración de metadatos siguen usando REST; la comprobación condicional de una revisión sin cambios no descarga el ZIP nuevamente. El transporte sólo admite la redirección de archivos a HTTPS de `codeload.github.com` sin reenviar el PAT.
- Extracción privada: comprobar rutas, raíz única, colisiones, tipos, número de entradas, CRC, tamaño, compresión y SHA Git de los archivos seleccionados. Se preservan topes de seguridad de extracción/disco y publicación con compensación; una descarga inválida conserva la versión anterior y anotaciones.
- El panel informa ausencia de cuotas locales de tráfico. Los contadores conservan al menos las últimas 24 h aunque haya más de 500 solicitudes, para no subestimar el consumo sin cuotas. No se modifican los flujos existentes de importación/descarga individual ni se agrega un instalador de APK.
- Pruebas añadidas/actualizadas: muchas solicitudes públicas y bytes sin bloqueo tras reapertura, streams fallidos contabilizados, un ZIP para 40 documentos, ninguna descarga de blob, 304 sin redescarga, snapshots incompletos/alterados/rutas hostiles, redirecciones seguras. Se mantienen los casos de publicación, disco, cancelación y recuperación existentes.
- Validación de esta revisión: `:app:testDebugUnitTest` **54/54 PASS**, sin errores/fallos/omisiones; `:app:lintDebug` y `:app:assembleDebug` **PASS** en el entorno cloud con SDK 36, Build Tools 35 y JDK 17. `git diff --check` aprobado. Sin prueba física, PAT real ni instalación en el Samsung. No se ejecutan pruebas web porque no se modifica su código. El APK debug generado usa un certificado propio de este entorno; no se promete compatibilidad de firma con las instalaciones personales existentes.

## Feedback del lector: selección, toggles y dibujo por elemento (2026-11-08)

- Autorización: corregir selección de texto sin cruceta, alternar modos con el mismo botón e implementar dibujo sujeto al elemento HTML con espera visible, marco verde y confirmar/cancelar. El último pedido, compartir HTML con notas para feedback a agentes, queda sólo como propuesta en `docs/proposals/feedback-with-notes.md`.
- Cambios aislados en `fix/anchored-drawing-feedback`, sobre la corrección ZIP ya preparada. Sin cambios ajenos, instalación física ni publicación de PR.
- VIEW sin ruedas permite selección/contextmenu del navegador y el long-click nativo. El botón Dibujar alterna DRAW/VIEW; Pin y Autoscroll conservan su toggle existente.
- DRAW requiere mantener un elemento con un círculo de progreso grande. Marco verde persistente; puntos firmados relativos al centro en CSS px, sin escalar letras con el elemento. Al cambiar resolución/posición/dimensiones se recomputa el centro. Ante pérdida de identidad del ancla se oculta el dibujo y se marca sin ancla; trazos antiguos PAGE_CSS conservan compatibilidad.
- Sesión provisional en memoria: confirmar publica todos sus cambios como una acción de undo; cancelar restaura el estado anterior, incluido el borrado provisional. Al cambiar de modo, cerrar o reconstruir el renderer se descarta el borrador. Herramientas de color/grosor no pierden el elemento elegido. Botones paloma verde/X roja arriba a la derecha.
- Validación final: **56/56 JVM PASS**, **15/15 web PASS**, `lintDebug`, `assembleDebug` y `assembleDebugAndroidTest` PASS; diff sin errores de whitespace. Las pruebas cubren anclaje, reflujo, resolución, pérdida de elemento, compatibilidad, selección y persistencia/confirmación/cancelación/undo. Sin prueba física Android; el caso instrumentado de dibujo se adapta al nuevo gesto y compila, pero no se declara ejecutado. APK firmado con la clave debug de este entorno; no instalado ni publicado.

## Exportar con notas y capturas completas (2026-11-08)

- Autorización explícita: implementar la propuesta de exportación con una captura obligatoria por dibujo, incluyendo su totalidad y el elemento HTML/selector CSS en el nombre.
- Menú Documento → Exportar con notas, selector SAF ZIP y espera de exportación. No exporta borradores sin confirmar. ZIP con originales intactos, recursos, JSON de anotaciones, resumen determinista sin OCR/interpretación y vista de revisión con pines/SVG y capturas.
- Las sesiones nuevas incluyen drawingId; las antiguas se agrupan por elemento/página. Los nombres de PNG incluyen número, tag y selector saneado; el selector completo y la correspondencia con trazos quedan en JSON y Markdown.
- Renderer privado/offline sobre copia del original: capturas por unión completa de puntos/grosor/margen, sin recorte por viewport; rasterizado nativo de los trazos. Reducción proporcional si es grande, con límites de resolución. Si falta página/ancla, PNG blanco con el dibujo completo y aviso explícito de falta de contexto. El lector mantiene página y desplazamiento.
- Temporales privados y limpieza compensatoria; copia bajo mutex, validación de rutas y exclusión de symlinks externos. Sin instalar ni publicar los cambios.
- Validación final: **60/60 JVM PASS**, **17/17 web PASS**, `lintDebug` PASS (0 errores, 56 advertencias), `assembleDebug` y `assembleDebugAndroidTest` PASS; `git diff --check` aprobado. Se prueban ZIP, nombres, agrupación, originales exactos, notas escapadas, PNG reales con puntos negativos/fuera de pantalla y reducción. El ZIP generado se abre en Chromium y muestra pines/SVG/capturas. El caso Android de captura completa compila, pero no se ejecutó físicamente. La prueba física de SAF y composición WebView permanece pendiente; no se instaló el APK.

## Integración de GitHub ZIP y exportación de feedback a main (2026-11-10)

- Comparación remota: `fix/github-zip-downloads` estaba 4 commits delante de `main`; `feat/export-feedback-notes` estaba 7 commits delante y contenía íntegra la rama ZIP más los cambios de selección/dibujo anclado, exportación de documentos con notas y capturas completas y el APK de descarga. El commit más nuevo es `833664bf6937033287156d3301c0d187e9e2ca96`; ambas ramas comparten la historia desde `main`.
- `main` avanzó localmente por fast-forward hasta `833664b`, sin conflictos ni pérdida de cambios. Se incluye la documentación/validación existente de las ramas. En esta sesión no se ejecutaron suites de pruebas.
- `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew assembleDebug --offline`: **BUILD SUCCESSFUL** con las funciones integradas. APK local `artifacts/Hyper-Marker-feedback-main-debug.apk`, SHA-256 `a68425d53eb28cb9336b511058ed392dc635128fde510caba0868eda4e25d5af`; paquete `com.hypermarker`, versión `1.0.0` (1), min API 26, target API 36, firma SHA-256 `42909c43e7ba7038ddba222d89f78a491b653268f5c67da86d3cbff3b8742853`.
- El APK versionado en `downloads/Hyper-Marker-feedback-debug.apk` tiene firma SHA-256 distinta (`4452e12f73a79a348ce11cf4f0e4818aab5483258e6b7b00d4441e8328a5c303`); el APK local se conserva por separado para dispositivos con la firma `42909c43…`. `adb devices -l` no detectó teléfonos/tablets: sin respaldos ni instalación en esta sesión.
- Publicado en `origin/main` mediante fast-forward más el commit documental `a4ec285`. Pendiente: al conectar los dispositivos, hacer respaldo fresco y comprobar firma/datos antes de instalar. La app aún declara `1.0.0` (1), así que esta entrega no cambia el versionCode.

## Actualización de Samsung Tab SM-P613 (2026-11-10)

- ADB detectó `R52X300Y59W`, Android conectado como `device`. Antes de actualizar se extrajo el APK anterior y se respaldaron `files/`, `databases/`, `shared_prefs/` y `no_backup/` en `artifacts/phone-backup/tab-feedback-main-20261110-014639/`. El TAR se pudo listar y extraer; SQLite del respaldo devuelve `PRAGMA integrity_check = ok`, user_version 3. Incluye 2 HTML originales.
- Certificado instalado y APK nuevo coinciden: SHA-256 `42909c43e7ba7038ddba222d89f78a491b653268f5c67da86d3cbff3b8742853`. `adb install -r artifacts/Hyper-Marker-feedback-main-debug.apk` conservó la instalación; versión 1.0.0 (1). `MainActivity` quedó reanudada, proceso `com.hypermarker` activo (PID 13771). Sin pruebas instrumentadas.

## Actualización de Samsung S24 Ultra SM-S928U (2026-11-10)

- ADB detectó `R5CX732ZD2K`. Se guardó el APK previo y el respaldo completo de datos en `artifacts/phone-backup/s24-feedback-main-20261110-014857/`. La transferencia TAR inicial se truncó mientras el dispositivo se desconectó; no se usó esa copia. Al reconectar, se detuvo la app y se creó `app-data.tar.gz`; `gzip -t` y `tar -tzf` PASS; SQLite del respaldo `integrity_check = ok`, user_version 3, 16 documentos.
- El certificado del APK previo coincide con el nuevo (`42909c43e7ba7038ddba222d89f78a491b653268f5c67da86d3cbff3b8742853`). `adb install -r artifacts/Hyper-Marker-feedback-main-debug.apk`: **Success**. `MainActivity` arranque COLD con Status ok, proceso activo (PID 31628) y en primer plano. Versión 1.0.0 (1). Sin pruebas instrumentadas.

## Propuesta de rediseño, Flow atrevido y selector de elementos (2026-11-16)

- Fuente: `bed525a`, rama `claude/relaxed-brahmagupta-eop7d6`, árbol limpio al comenzar. **Sólo documentación; sin cambios de código, build ni pruebas en esta sesión.**
- Entrada: diseño del propietario «Rediseño de la app» (8 pantallas: Biblioteca, Crear o importar, Navegador, Carpeta de GitHub, Lector Ver/Editar, Nota Markdown, Flow Dots) y su pedido de un Flow más atrevido: puntos con «foto de perfil», punto que se expande con la vista previa de su documento, menú radial con mini-vistas cuando hay varios elementos relacionados, y un selector de elementos mejor para mover y para relacionar elementos con un punto.
- Entregado: [docs/proposals/ui-redesign.md](docs/proposals/ui-redesign.md) con tokens del diseño, cambios por pantalla frente al código actual, modelo de datos propuesto (Room v4: columnas de foto en `flow_nodes`, tabla `flow_node_links`, `lastOpenedAt`), módulo de vistas previas, selector con tres modos, fases R0–R7, seguridad, riesgos y pruebas a registrar. Maquetas estáticas de las pantallas 9–14 en un lienzo privado del propietario, enlazado desde la propuesta.
- **Nada de esto está autorizado ni implementado.** Es una propuesta; la especificación original no se modificó. Las siete decisiones pendientes del propietario están al final de la propuesta.
- No verificado: las maquetas no se renderizaron ni revisaron en dispositivo; la viabilidad de la miniatura HTML fuera de pantalla y el rendimiento con muchas fotos son supuestos por validar en R2/R3.
- Siguiente paso concreto: el propietario revisa el lienzo y responde las decisiones 1–7. Con autorización, empezar por R0 (tema y componentes) y R1 (selector en modo Destino, sustituye «Mover a…»).
- **Actualización del mismo día:** el propietario autorizó la propuesta pero indicó **no implementarla aún**. Resueltas: relacionar documentos, carpetas e imágenes desde el principio; lienzo de Flow siempre oscuro; vista previa con un solo elemento. Pendientes las decisiones 5–7. El modelo de datos de la propuesta aún no refleja el destino «imagen» de una relación; ajustarlo antes de R3/R4. El HTML del diseño y las maquetas no están en Git (sólo en el lienzo privado).
- Documentos de diseño versionados en `docs/design/` por pedido del propietario: su HTML (pantallas 1–8) y las fuentes de las pantallas 9–14, que sólo se renderizan dentro del lienzo.

## Implementación del rediseño (2026-11-16)

- Encargo: «revisa `ui-redesign.md` y las propuestas en `docs/design/` y procede a la implementación». Rama `claude/relaxed-brahmagupta-eop7d6`, base `e59e3d2`. Especificación original intacta.
- **Implementado (R0–R6 y accesibilidad de R7)**, detalle en [architecture.md](architecture.md#rediseño-de-la-app-2026-11-16):
  - R0: tema de tokens (`ui/theme`), Inter y Space Grotesk variables (OFL) empaquetadas, componentes compartidos (`ui/Components.kt`, `ui/Kinds.kt`).
  - R1: selector de elementos (`picker/`) en modos Destino, Relacionar y Lista. Sustituye «Mover a…» y elige destino en «Guardar copia» del navegador; recientes, migas, búsqueda global, crear carpeta, exclusiones explicadas.
  - R2: `PreviewStore` (Markdown y Flow dibujados; HTML/Web por captura al cerrar el lector; carpeta e imagen). Sin render fuera de pantalla de HTML nunca abiertos (se muestra el dibujo del tipo).
  - R3–R4: Room v4 (`MIGRATION_3_4`, esquema `4.json`), lienzo de tinta, puntos con foto, anillo y contador, hoja «Foto del punto», punto abierto con vista previa y transición, menú radial con páginas y giro, relaciones con documentos, carpetas e **imágenes** (decisión 2), visor de imágenes, pila de retorno al Flow.
  - R5: biblioteca (búsqueda/web en un campo, chips, «Continuar» con `lastOpenedAt`, filas con baldosa, botón «Nuevo»), hoja «Nuevo», hoja de acciones y Ajustes (GitHub, licencias).
  - R6: piel del lector (barra segmentada de tinta, «Notas y cambios», cruceta en tarjetas con Pin amarillo, avisos en píldora, botón Volver), Markdown (cabecera con «Guardado», Visual/Fuente, barra de formato abajo con «+»), navegador (barra inferior) y asistente de GitHub en 4 pasos. Sin cambios de gesto, protocolo ni políticas.
  - R7 (parcial): nodos semánticos por punto visible con acciones personalizadas (editar, conectar, relacionar, foto, plegar, lista) y «Ver los N» como alternativa al radial.
- Decisiones 5–7: aplicada la recomendación (cascada sin marcas, fuentes empaquetadas, 6 pétalos + «+N»); pendientes de confirmación del propietario.
- Validación (entorno de esta sesión: contenedor Linux sin KVM, OpenJDK 21, Android SDK 36 / build-tools 36.0.0 instalados con `sdkmanager`; Maven Central devolvió 429 de forma intermitente y se reintentó):
  - `./gradlew testDebugUnitTest` **84/84 PASS** (nuevos: `ItemPickerStateTest`, `RadialLayoutTest`, `FlowLinksTest` con migración v3→v4, `RedesignScreenshotTest`; existentes actualizados para migrar hasta v4).
  - `lintDebug` 0 errores, 65 advertencias (actualizaciones de dependencias y sugerencias KTX/estilo).
  - `assembleDebug` y `assembleDebugAndroidTest` PASS. APK local `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `099ec5bb576630548b34be342a201f50cc4270228a3119104944078325aebb77`; **no versionado ni publicado** en `downloads/`.
  - Capturas renderizadas por Compose (Robolectric, gráficos nativos, 390×844 dp) en [docs/design/capturas-implementacion/](docs/design/capturas-implementacion/): pantallas 1–4, 7 y 9–14 revisadas contra las maquetas.
- **No verificado**: ninguna prueba instrumentada ni táctil (sin emulador ni dispositivo). Pendiente en dispositivo: gestos del lienzo con fotos, transición del punto abierto, giro del anillo, Photo Picker real, captura de miniatura HTML al cerrar el lector, lector (pantallas 5–6) sin captura automática porque Robolectric no ejecuta WebView, TalkBack, rendimiento con muchas fotos y `AppWorkflowTest` (se conservaron las descripciones que usa).
- **Precaución**: instalar este APK migra la base a v4. En S24/Tab respaldar antes con `run-as`, como en v2 y v3.
- Siguiente paso concreto: instalar el APK en el S24 tras respaldo, recorrer PICK-01…06, FLOW-09…15 y PREV-01…04 de [docs/testing.md](docs/testing.md), ejecutar la suite instrumentada en emulador y registrar resultados; confirmar o cambiar las decisiones 5–7.

## Feedback de legibilidad y usabilidad (2026-11-16)

- Fuente: rama `claude/legibilidad-usabilidad-issues-s9pq6z` sobre `bed525a`, árbol limpio al comenzar. Especificación original intacta. Cuatro puntos de feedback del propietario.
- **Cabecera Markdown que desplazaba el texto:** las herramientas de la hoja flotan sobre el contenido. En Fuente el hueco superior forma parte del área desplazable; en Visual es `padding-top` del editor. Ocultar/mostrar ya no mueve el texto; el botón «Mostrar herramientas» también flota.
- **Imágenes, tablas o enlaces bloqueaban Visual:** nuevo `MarkdownProtection`. Bloques no compatibles → bloque de código `hm-raw-<nonce>`; fragmentos en línea (imágenes, HTML, autoenlaces, `{…}`, referencias a definiciones acotadas) → código en línea `⟦…⟧`, con la fuente visible y editable. Al guardar/volver a Fuente se desenvuelven tal cual. También en la vista Markdown remota de solo lectura. `MarkdownPolicy` deja de marcar `- [texto](url)` y delimitadores `|:-|`. Sólo un bloque sin cerrar o `⟦`/`⟧` en la nota mantienen el aviso.
- **githack se quedaba en el aviso:** causa probable, la verificación de Cloudflare sin cookies (bucle). Cookies propias sólo durante la sesión del navegador, borradas al entrar/salir; `Worker` permitido en el navegador (WebRTC sigue bloqueado); estado «verificando» en vez de error; botón «Abrir en otra aplicación». Decisión registrada en `docs/proposals/web-browser.md`. Desde el entorno cloud githack respondió 200 sin desafío, así que **no se reprodujo**.
- **Formularios perdidos al girar:** `configChanges` de orientación/tamaño/teclado en `MainActivity` (la Activity y los WebView ya no se recrean). Respaldo adicional `form.state` → `documents/<id>/form-buffer.json` (fuera de la raíz servida), restaurado tras `controller.ready`, con reintentos para campos tardíos; nunca contraseñas, archivos ni campos ocultos. Menú «Borrar respaldo de formularios».
- Validación (entorno cloud, JDK 21, SDK 36/Build Tools 35 instalados con autorización del propietario): `testDebugUnitTest` **69/69 PASS**, `lintDebug` **PASS** (0 errores, 56 advertencias), `assembleDebug` y `assembleDebugAndroidTest` **PASS**; web `npm test` **19/19 PASS** (Chromium 1243). Nuevas pruebas: `MarkdownProtectionTest` (7), formularios en `BridgeValidatorTest`, `BrowserCookiesTest`, web de respaldo/restauración de formularios y round trip de fragmentos protegidos con el Milkdown empaquetado.
- No ejecutado: pruebas instrumentadas, prueba física de rotación, del desafío de Cloudflare en teléfono y de la UI flotante. APK no instalado ni publicado; `downloads/` sin cambios. La firma del APK de este entorno no coincide con las instalaciones personales.
- Siguiente paso concreto: en el S24, abrir un enlace raw.githack.com, girar con un formulario rellenado, recargar/reabrir, y abrir en Visual una nota con imágenes/HTML/tablas comprobando que la fuente se conserva; registrar resultados (UX-01…UX-06 en `docs/testing.md`).

## Fusión de rediseño y legibilidad en main (2026-11-16)

- Origen: `main` en `bed525a`; fast-forward a `claude/relaxed-brahmagupta-eop7d6` (`22f544f`, rediseño R0–R6) y fusión `--no-ff` de `claude/legibilidad-usabilidad-issues-s9pq6z` (`5e49b76`). Ambas ramas partían de `bed525a`. Se conservaron las notas locales de instalación del 2026-11-10 que estaban sin commitear.
- Conflictos resueltos:
  - `MarkdownScreen.kt`: estilo y orden del rediseño (cabecera 60 dp con «Guardado», pestañas Visual/Fuente, barra de formato abajo) con la cabecera **flotante** de legibilidad (hueco superior desplazable en Fuente, `padding-top` en Visual) y `MarkdownProtection`. La barra de formato inferior queda en el flujo, no flotante, para que el cursor nunca quede tapado al escribir; al ocultarla sólo crece el borde inferior y el texto no se desplaza.
  - `BrowserScreen.kt`: barra inferior del rediseño más el aviso «verificando» y el botón «Abrir en otra aplicación» de legibilidad.
  - `LibraryRepository.kt`: se conservan `MAX_FLOW_LINKS` y `FORM_BUFFER_LIMIT`. `progress.md`: ambas entradas.
- Validación local (macOS, JBR de Android Studio): `testDebugUnitTest` **93/93 PASS**, `lintDebug` 0 errores / 65 advertencias, `assembleDebug` PASS. Web: `controller.test.mjs` + `editor.test.mjs` **18/18 PASS**; `feedback-review.test.mjs` (no tocado por ninguna rama) se quedó colgado sin CPU en este entorno y se detuvo: **no verificado**.
- APK `artifacts/Hyper-Marker-redesign-main-debug.apk` (no versionado), SHA-256 `4f6fe339035f3433fccfa0cb013021c49b10dc1004a0d81bde8bf28957eba74b`; `com.hypermarker` 1.0.0 (1), firma `42909c43e7ba7038ddba222d89f78a491b653268f5c67da86d3cbff3b8742853` (coincide con S24 y Tab). `adb devices -l`: ninguno conectado; sin instalación.
- **Precaución**: este APK migra la base a Room v4 (`MIGRATION_3_4`). Respaldar con `run-as` antes de instalar en cada dispositivo.
- No verificado: UI combinada de Markdown en dispositivo (cabecera flotante + barra inferior con teclado), pruebas instrumentadas.
- Siguiente paso concreto: conectar los dispositivos, respaldar datos, `adb install -r` y recorrer UX-01…06, PICK-01…06, FLOW-09…15 y PREV-01…04 de `docs/testing.md`.

## Actualización de Samsung Tab SM-P613 con rediseño + legibilidad (2026-11-16)

- ADB detectó `R52X300Y59W` (Android 14). Respaldo previo en `artifacts/phone-backup/tab-redesign-main-20261116-003722/`: APK instalado (`previous.apk`, SHA-256 `8354a5ed…e520`, firma `42909c43…`) y `app-data.tar.gz` (`databases files no_backup shared_prefs`); `gzip -t` PASS, 33 entradas, SQLite `integrity_check = ok`, **user_version 4** (la base ya había migrado con una compilación del rediseño instalada a las 00:16), 5 documentos.
- El primer `adb install -r` se interrumpió por desconexión USB; según `lastUpdateTime` (00:38:16) llegó a aplicarse. Tras reiniciar el servidor ADB se reinstaló: **Success** (00:39:32). El APK instalado coincide con `artifacts/Hyper-Marker-redesign-main-debug.apk` (SHA-256 `4f6fe339…a74b`). `MainActivity` arranque COLD, Status ok, 3,36 s, proceso activo y en primer plano, 5 carpetas en `files/documents`, sin entradas en el buffer de fallos. Sin pruebas instrumentadas ni recorrido manual.
- S24 Ultra (`R5CX732ZD2K`) no detectado: **pendiente** respaldo e instalación.

## Actualización de Samsung S24 Ultra SM-S928U con rediseño + legibilidad (2026-11-16)

- Por indicación del propietario, **instalación directa sin respaldo previo**. Instalación anterior del 2026-11-15 23:55. `adb install -r artifacts/Hyper-Marker-redesign-main-debug.apk`: **Success**; APK instalado con SHA-256 `4f6fe339…a74b` (coincide). `MainActivity` en primer plano, proceso activo, sin fallos de `com.hypermarker` en el buffer de crash de hoy. Sin pruebas instrumentadas ni recorrido manual.
