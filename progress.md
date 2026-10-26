# Progreso y relevo

Última actualización: **2026-10-26** (America/Mexico_City).

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
