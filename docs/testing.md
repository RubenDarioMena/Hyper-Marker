# Plan de pruebas y aceptación

**Infraestructura implementada:** tests JVM, instrumentados Android y pruebas DOM/editor con Playwright. Los resultados reales y límites de cobertura están en [validation.md](validation.md). La matriz deriva de la sección 9 de la [especificación](../Interactive_Notes_Android_Agent_Design.md) y agrega controles de consistencia necesarios para implementarla. La matriz siguiente define la aceptación; consultar el reporte para distinguir PASS, cobertura parcial y validaciones de dispositivo no realizadas.

## Estrategia

- Unitarias JVM: rutas/ZIP, límites, jerarquías, validación de mensajes, payloads y comandos de anotación.
- Instrumentadas Android: Room real, SAF/URIs, WebView/DOM, lifecycle, integración Compose y almacenamiento.
- Manuales en emulador y teléfono cuando esté disponible: gesto táctil, lápiz, zoom, rotación, Sharesheet, TalkBack, inmersión y comportamiento con distintas versiones de WebView. Registrar lo no disponible.
- Pruebas de DOM aisladas ayudan a validar selección/anclas, pero no sustituyen la integración con WebView real.

## Fixtures

La carpeta [fixtures/](../fixtures/README.md) contiene ejemplos sintéticos, ZIP de prueba, corpus Markdown y una salida React/Vite compilada. Su README documenta regeneración y expectativas.

| Fixture | Contenido y propósito |
| --- | --- |
| `standalone/` | HTML monolítico con texto, links, botones, elementos anidados, IDs y consola. |
| `project/` | `index.html`, segunda página y CSS/JS/imagen relativos, nombres con espacios/Unicode. |
| `bundle/` | ZIP generado del proyecto, variante con directorio envolvente y entrada ambigua. |
| `react-built/` | SPA React/Vite con producción compilada y rutas relativas; fuente, lockfile y comando de generación reproducible. No prometer ejecutar JSX crudo. |
| `surface/` | Canvas/PixiJS o WebGL compatible y SVG; comprobar fallback de superficie. |
| `dynamic/` | Elementos que cambian, desaparecen, reutilizan IDs o se duplican; reflow, transformaciones e iframe. |
| `markdown/` | Corpus de headings/listas/emphasis/links/código y construcciones no compatibles para round-trip. |
| `malicious/` | Generador controlado de ZIP traversal, absolutos, symlinks, colisiones y exceso de límites; payloads bridge inválidos y solicitudes entre documentos. |

No almacenar bombas ZIP reales de gran tamaño: generar casos acotados usando límites inyectables para las pruebas. La generación de fixtures maliciosos no debe escribir fuera de su directorio temporal.

## Matriz de aceptación V1

| ID | Acción | Resultado obligatorio |
| --- | --- | --- |
| LIB-01 | Crear tres niveles, documentos, renombrar, mover y reiniciar. | Árbol y contenido correctos después del reinicio. |
| LIB-02 | Buscar títulos y carpetas; probar nombres repetidos. | Resultados correctos sin confundir identidades. |
| LIB-03 | Duplicar HTML con anotaciones y Markdown; editar copia; borrar carpeta y cancelar/confirmar. | Copias independientes, alcance de eliminación claro y cancelación sin pérdida. |
| LIB-04 | Intentar ciclo al mover carpetas; interrumpir operación de archivos/Room. | Ciclo rechazado y recuperación consistente. |
| IMP-01 | Importar HTML/HTM independiente mediante SAF. | Copia privada que abre tras reiniciar y perder URI temporal. |
| IMP-02 | Importar carpeta con CSS/JS/imagen y páginas relativas. | Recursos y links internos correctos. |
| IMP-03 | Importar ZIP normal/envolvente y selección ambigua. | `index.html` preferido; selector cuando no hay entrada inequívoca. |
| IMP-04 | Compartir uno/varios archivos desde proveedor compatible. | URIs/ClipData manejados; errores claros para contenido no admitido. |
| IMP-05 | ZIP traversal, absoluto, symlink y colisiones normalizadas. | Operación rechazada; ningún archivo fuera de staging ni sobrescritura. |
| IMP-06 | Superar bytes, cantidad, profundidad o ratio; simular poco espacio. | Fallo limitado y limpieza; biblioteca sin documentos parciales. |
| IMP-07 | Cancelar o matar proceso durante copia/confirmación. | Reinicio recupera o limpia operación; originales anteriores intactos. |
| IMP-08 | HTML malformado, ZIP corrupto o recursos faltantes. | Error explicable sin caída de biblioteca ni ampliación de permisos. |
| VIEW-01 | Abrir lector, mostrar controles, entrar/salir fullscreen. | Sin toolbar permanente, navegación del sistema accesible. |
| VIEW-02 | Back/forward/reload y navegación entre páginas. | Back usa historial antes de salir; límites del documento preservados. |
| VIEW-03 | HTML interactivo, SPA compilada y canvas. | Botones, scripts y recursos funcionan dentro de compatibilidad declarada. |
| VIEW-04 | Producir log/warn/error/error no capturado/asset faltante. | Consola colapsable muestra lo observable y limita memoria; alcance documentado. |
| VIEW-05 | Autoscroll play/pause, velocidades/direcciones y toque manual. | Cambio visible y pausa por interacción/cambio de modo. |
| VIEW-06 | Zoom, rotación, segundo plano y fallo de renderer. | Documento recuperable, notas intactas, sin doble controlador ni eventos repetidos. |
| MD-01 | Crear `.md`, editar headings/listas/emphasis/links en Source y Visual. | Ambos permiten edición; guardar/reabrir conserva contenido compatible. |
| MD-02 | Cambiar modos con sintaxis no soportada. | Advertencia y fuente autoritativa preservada, sin pérdida silenciosa. |
| MD-03 | Autosave, undo/redo, navegación y cierre abrupto. | Revisiones guardadas recuperables, sin guardado antiguo sobrescribiendo nuevo; ventana de pérdida documentada. |
| MD-04 | Markdown con HTML/scripts/links maliciosos en nota y pin. | Renderizado seguro, sin ejecución o acceso nativo no autorizado. |
| PIN-01 | Mantener aproximadamente 500 ms en Editar. | Resaltado con relleno y contorno del elemento exacto aparece **mientras se sostiene**. |
| PIN-02 | Deslizar tras activar, sin soltar. | Objetivo y límites visuales se actualizan en vivo. |
| PIN-03 | Soltar, elegir Nota pin, cancelar, guardar vacío y guardar contenido. | Cruceta al soltar con el elemento resaltado; Nota pin abre el editor; sin pin al cancelar/vacío; pin persistente sólo al guardar contenido significativo. |
| PIN-04 | Scroll/zoom/rotación/cerrar y reabrir, y cambiar de página interna. | Pin conserva objetivo y posición relativa; no aparece en otra página indebidamente. |
| PIN-05 | Texto, enlace, DOM anidado, canvas/SVG, transformaciones e iframe. | Objetivo mostrado coincide con ancla; límites de superficie/iframe explícitos, sin precisión semántica falsa. |
| PIN-06 | Eliminar/reemplazar objetivo o reutilizar selector para otro elemento. | Recuperación única defendible o Unanchored; jamás adhesión silenciosa incorrecta. |
| PIN-07 | Tap corto, multitouch, movimiento previo al umbral, pointercancel y salida de modo. | No activa links en Editar; cancela gestos ambiguos; limpia resaltado/estado. |
| PIN-08 | Abrir/editar/mover/reanclar/borrar pin, undo/redo y volver a abrir. | Operaciones coherentes y persistencia del estado final. |
| PIN-09 | Salir a View; usar links, botones, scroll; alternativa accesible de selección. | Interactividad normal restaurada y creación/reanclaje accesible sin long-press. |
| DRAW-01 | Pen/highlighter/eraser, color/ancho, scroll, undo/redo y reabrir. | Trazo correcto, estado final persistente y coordenadas consistentes. |
| DRAW-02 | Clear con cancelación/confirmación; zoom y rotación. | Confirmación respetada; sin pérdida accidental; reflujo documentado. |
| DRAW-03 | Cambiar Draw → View y usar controles HTML. | Capas no capturan eventos indebidamente. |
| UI-01 | Tocar la página en Ver fuera y dentro de contenido interactivo; esperar; tocar en Editar/Dibujar. | El cromo (opciones + Ver/Editar/Dibujar) se alterna sólo fuera de botones/enlaces; en Ver se oculta solo; en Dibujar un toque breve con el dedo no deja punto. |
| UI-02 | Mantener presionado en Ver, Editar y Dibujar. | Cruceta de cuatro acciones del modo en el punto soltado; en Ver/Editar el elemento queda resaltado; el clic del contenido no se activa. |
| UI-03 | Reasignar desde la cruceta y desde el menú; cerrar y reabrir. | Intercambio sin duplicados, acción desplazada visible en el menú y persistencia entre documentos. |
| UI-04 | Acercar elemento y Zoom original. | El bloque completo cabe y queda centrado; la vuelta restablece la escala inicial. |
| UI-05 | Desactivar ruedas desde la primera opción bajo el título del documento; tocar Pin y un elemento; entrar a Dibujar; volver a activar ruedas; mantener en Ver. | La preferencia persiste, la barra muestra Autoscroll/Pin/Dibujar sin botones de modo, Pin permanece marcado hasta seleccionar y abre el editor sin activar el HTML; Dibujar usa pluma; la rueda Ver abre Configuración en vez de Zoom original. |
| TXT-01 | Editar texto de un elemento, recargar, restaurar y deshacer. | Override separado, reaplicado sólo si el original coincide, restauración de los nodos originales; inputs/superficies no editables; pines del elemento siguen anclados. |
| MD-05 | Seleccionar texto en Fuente y Visual y aplicar estilo, tipo de letra, listas, cita, código, enlace, tabla y separador; alternar hoja oscura. | El formato se aplica a la selección (o al cursor) y repetirlo lo retira; tablas GFM bien formadas se editan en Visual; tema y letra persisten. |
| MD-06 | Desplazar una nota larga en Fuente y Visual, alternar ambos modos; esperar 5 s sin tocar y recuperar la barra. | La posición proporcional de lectura se conserva en ambos sentidos; la barra se oculta y reaparece al tocar la hoja o «Mostrar herramientas». Con exploración táctil no se oculta automáticamente. |
| SEC-01 | Pedir rutas fuera del proyecto, codificadas y de otro documento. | Rechazo sin lectura ni fallback de red/filesystem. |
| SEC-02 | Abrir documento OFFLINE con recursos locales y remotos. | Local funciona sin conexión; no salen solicitudes remotas por canales probados, incluidos sockets/workers. |
| SEC-03 | Permitir red en A, abrir B, revocar en A con conexiones activas. | Permiso sólo de A; revocación efectiva; B continúa OFFLINE. |
| SEC-04 | Intentar permisos, popups, esquemas externos y leer storage de otro documento. | Sin privilegios automáticos ni exposición entre documentos; límites verificados. |
| SEC-05 | Enviar mensajes falsos, enormes, de subframes, fuera de modo o sesiones viejas. | Sin privilegios ni cambios a otros documentos; rechazo acotado, biblioteca estable. |
| A11Y-01 | TalkBack, texto grande, contraste, navegación del sistema, dedo/lápiz. | Controles identificables/utilizables; alternativa sin gesto prolongado; registrar hardware no probado. |
| BUILD-01 | Compilar checkout limpio e instalar APK debug. | Comando reproducible, archivo instalable y apertura real; registrar hash/commit/entorno. |

## Flow Dots (prototipo)

Funcionalidad fuera de V1 ([propuesta](proposals/flow-dots.md)). Estos casos no forman parte de la aceptación V1.

| ID | Acción | Resultado obligatorio |
| --- | --- | --- |
| FLOW-01 | Crear un Flow en la biblioteca; renombrar, mover, duplicar y borrar. | Tipo `FLOW` con icono propio; la copia tiene identidades nuevas e independientes; borrar elimina puntos y conexiones. |
| FLOW-02 | Tocar el vacío, cancelar; tocar de nuevo y guardar con título. | Cancelar o guardar vacío no crea nada; guardar crea el punto en esa posición y persiste tras cerrar/reabrir. |
| FLOW-03 | Mantener sobre un punto y deslizar hasta otro; soltar. | Háptica al activarse, línea elástica, destino resaltado, conexión padre → hijo creada. |
| FLOW-04 | Intentar conectar hijo → antepasado; soltar en vacío. | Destino en rojo y aviso de ciclo sin crear nada; en vacío se cancela. |
| FLOW-05 | Mantener y soltar sin mover sobre un padre; repetir. | Oculta los descendientes sin otra ruta visible y muestra «+N»; repetir los restaura. |
| FLOW-06 | Reordenar: arrastrar un punto, Listo, reabrir. | Posición nueva persistida; crear/conectar/plegar suspendidos mientras dura. |
| FLOW-07 | Desplazar con un dedo, zoom con dos, Encuadrar. | El zoom mantiene el punto bajo los dedos; Encuadrar muestra todos los puntos visibles. |
| FLOW-08 | Abrir una base v1 con datos y actualizar a v2. | La migración conserva documentos y anotaciones y Room valida el esquema. |

## Comandos previstos

Tareas disponibles desde la raíz (JDK 17 y SDK configurados):

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew connectedDebugAndroidTest
./gradlew assembleDebug
```

`connectedDebugAndroidTest` requiere un emulador/dispositivo. Un resultado sin tests ejecutados no es evidencia de cobertura. Si se usa CI, documentar preparación SDK/JDK, tareas, ruta de APK y recolección de artefactos; no afirmar generación en la nube hasta verificarla.

## Registro de evidencia

En `progress.md` o un reporte enlazado registrar: fecha, commit fuente, hito/casos, comando o pasos, resultado (PASS/FAIL/NO EJECUTADO), dispositivo/API Android, versión WebView, versiones de toolchain y ruta a evidencia cuando exista. Para PIN-01/02 se recomienda video o secuencia de capturas con pasos reproducibles. Para seguridad, registrar intento y ausencia del efecto prohibido, no sólo que no hubo crash.

V1 se acepta cuando los requisitos obligatorios están implementados y la matriz correspondiente está verificada, con limitaciones concretas de compatibilidad comunicadas. Pruebas pendientes por falta de entorno siguen pendientes; una compilación por sí sola no completa V1.
