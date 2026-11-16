# Propuesta: rediseño de la app, Flow atrevido y selector de elementos

Estado: **propuesta, sin autorización de implementación** (2026-11-16). No modifica la [especificación original](../../Interactive_Notes_Android_Agent_Design.md) ni cambia código. Amplía [Flow Dots](flow-dots.md); donde ambas difieren se indica qué habría que confirmar.

## Fuentes

- Diseño del propietario «Rediseño de la app»: 8 pantallas de 390×844 (Biblioteca, Crear o importar, Navegador, Añadir carpeta de GitHub, Lector · Ver, Lector · Editar con cruceta, Nota Markdown, Flow Dots). El archivo (3,4 MB) no se versiona.
- Lienzo complementario con las pantallas 9–14 de esta propuesta: <https://claude.ai/artifact/WG3ikDpte5YG16rgpY2iec> (privado del propietario hasta que lo comparta). Son maquetas estáticas; las fotos de los puntos son ilustraciones de relleno.
- Código revisado en `bed525a`: `MainActivity.kt`, `library/`, `flow/`, `storage/LibraryDatabase.kt` (Room v3), `storage/LibraryRepository.kt`, `viewer/Glass.kt`.

## Lenguaje visual que fija el diseño

| Token | Valor | Uso |
| --- | --- | --- |
| Papel / superficie | `#FFFDF7` / `#FFFFFF` | Fondo de app y tarjetas |
| Tinta | `#14201C` | Texto, chip activo, botones oscuros |
| Acento | `#176B5B` (presionado `#0F4D41`) | Acción principal, selección |
| Texto secundario | `#56615C` | Metadatos y etiquetas en mayúsculas |
| Borde / divisor | `#D9D5C8` / `#ECE9DE` | Contornos de 1 dp |
| Menta suave | `#E3F0EB` | Baldosas de icono |
| Pin | `#E5B62F` (suave `#FBE7A1`) | Pines, resaltado, contadores |
| Peligro | `#A3302A` | Eliminar |
| Tipografía | Inter 400/500/600; Space Grotesk 600 en títulos | Cuerpo 15–16 sp, metadatos 13 sp |
| Forma | Píldoras (radio = alto/2), tarjetas 16–28 dp, objetivos táctiles ≥ 44 dp | — |

Hoy el tema vive en dos líneas de `MainActivity.kt` (`lightColorScheme(primary = 0xFF176B5B, …)`) y cada pantalla usa Material3 sin personalizar. El acento ya coincide; faltan tipografías, formas y componentes.

## Qué cambia por pantalla

| Pantalla del diseño | Estado actual | Cambio propuesto | Archivos |
| --- | --- | --- | --- |
| 1 · Biblioteca | `TopAppBar` + `DropdownMenu`, lista `ListItem`, búsqueda y botón web separados | Cabecera con estado de GitHub y Ajustes; un solo campo «Buscar aquí o abrir una web»; chips Todo/HTML/Notas/Flow/Web; tarjeta «Continuar»; filas con baldosa de icono; botón flotante «Nuevo» | `library/LibraryScreen.kt`, `LibraryViewModel.kt` |
| 2 · Crear o importar | Entradas del `DropdownMenu` | Hoja inferior con Crear (Carpeta/Nota/Flow), Importar del teléfono y Traer de internet. «Sincronización y consumo» y «Licencias» pasan a Ajustes | `library/NewItemSheet.kt` (nuevo) |
| 3 · Navegador | `BrowserScreen` con barra superior | Barra inferior con URL, atrás/adelante/Biblioteca y botón principal «Guardar copia para estudiar» | `browser/BrowserScreen.kt` |
| 4 · Carpeta de GitHub | `GitHubAddScreen` en una pantalla | Asistente de 4 pasos (Cuenta, Repositorio, Rama, Carpeta) con migas y pie fijo | `github/GitHubAddScreen.kt` |
| 5–6 · Lector | Botones de cristal y cruceta ya funcionales | Sólo piel: barra segmentada Ver/Editar/Dibujar, píldora «Notas y cambios», cruceta con tarjetas, avisos en píldora. Sin cambios de gesto ni de protocolo | `viewer/ReaderChrome.kt`, `viewer/Glass.kt` |
| 7 · Nota Markdown | Barra de formato arriba | Cabecera con «Guardado», segmentado Visual/Fuente y barra de formato abajo (sobre el teclado) | `markdown/MarkdownScreen.kt`, `MarkdownToolbar.kt` |
| 8 · Flow Dots | Canvas claro, puntos de 9 dp, tarjeta provisional y `AlertDialog` | Sustituida por «Flow atrevido» (pantallas 9–12) | `flow/` |
| — · Mover a… | `AlertDialog` con una lista plana de rutas (`LibraryScreen.kt`, operación `move`) | Selector de elementos (pantallas 13–14) | `picker/` (nuevo) |

El gesto obligatorio de Pin (mantener → resaltar → deslizar → soltar) no se toca; el rediseño del lector es visual.

## Flow atrevido

Dirección: el Flow deja de ser papel con puntitos y pasa a ser un **lienzo de tinta** (`#14201C`) donde los puntos son **retratos**. La biblioteca sigue siendo clara; entrar a un Flow se siente como cambiar de espacio. Menta `#89D5BC` (el primario del tema oscuro actual) para conexiones y anillos; amarillo pin para lo seleccionado y los contadores.

### Punto con foto (pantalla 9)

- Un punto puede tener foto. Con foto mide 56–88 dp (según jerarquía y zoom), lleva anillo de color y una etiqueta-píldora que monta sobre el borde inferior. Sin foto sigue siendo el punto pequeño de hoy: ambos conviven.
- Un contador amarillo indica cuántos elementos tiene relacionados cuando son 2 o más.
- Por debajo de escala 0,45 (el umbral donde hoy desaparecen los títulos) todos los puntos se dibujan como puntos simples.
- Mantener y deslizar para conectar, mantener y soltar para plegar, Reordenar y Encuadrar se conservan tal cual.

### Tocar un punto

| Elementos relacionados | Resultado |
| --- | --- |
| 0 | Tarjeta con la nota del punto (título y cuerpo), como hoy pero con el nuevo estilo |
| 1 | **Punto abierto** (pantalla 10): la foto viaja a la esquina de una tarjeta que ocupa ~70 % de la pantalla con la vista previa del documento, la nota del punto, «Abrir documento» y acciones (Conectar, Relacionar, Foto, Eliminar) |
| 2 o más | **Menú radial** (pantalla 11): el punto crece al centro y lo rodean hasta 6 mini-vistas; si hay más, el séptimo pétalo es «+N». Tocar un pétalo lo pone en foco y muestra abajo título, tipo y «Abrir»; girar el anillo pasa de página; «Ver los N» abre la lista completa en el selector |

Transición: la foto se anima desde su posición en el canvas hasta su destino (220–280 ms, resorte suave) con un velo del 55 % sobre el resto; los pétalos salen del centro escalonados. Con las animaciones del sistema desactivadas todo aparece sin movimiento. Tocar el centro, el velo o Atrás cierra.

### Foto del punto (pantalla 12)

Hoja con vista de cómo quedará en el lienzo y tres orígenes: galería (Photo Picker del sistema, sin permisos nuevos), la vista previa de un elemento relacionado, o sin foto. Color de anillo entre cinco opciones. La imagen se copia al directorio privado del Flow, reescalada a 512 px en WebP.

### Datos (Room v4, `MIGRATION_3_4`)

```text
flow_nodes        + avatarPath TEXT NULL      -- ruta relativa dentro del documento FLOW
                  + avatarLinkId TEXT NULL    -- si la foto es la vista previa de un elemento relacionado
                  + ringColor INTEGER NULL

flow_node_links   id TEXT PK
                  documentId TEXT  → documents(id) CASCADE   -- el Flow
                  nodeId TEXT      → flow_nodes(id) CASCADE
                  targetDocumentId TEXT NULL → documents(id) CASCADE
                  targetFolderId   TEXT NULL → folders(id)   CASCADE
                  position INTEGER, createdAt, updatedAt, deletedAt   -- mismo patrón de tombstone que flow_edges
                  índices: nodeId; único (nodeId, targetDocumentId); único (nodeId, targetFolderId)

documents         + lastOpenedAt INTEGER NULL  -- para «Continuar» en la biblioteca
```

- Una relación apunta a un documento **o** a una carpeta de la biblioteca; nunca a rutas del sistema de archivos. Es un identificador interno: no concede acceso a nada y no copia el original.
- Borrar el documento o la carpeta elimina la relación por cascada. Duplicar un Flow copia relaciones y fotos; borrarlo las elimina.
- Límite propuesto: 64 relaciones por punto.
- Repositorio: `linkFlowNode`, `unlinkFlowNode`, `reorderFlowLinks`, `setFlowAvatar`, `clearFlowAvatar`, con las mismas validaciones y `write {}` que el resto de Flow.

### Vistas previas (`preview/`, nuevo)

Un único `PreviewStore` sirve al Flow y al selector. Guarda miniaturas en `cacheDir/previews/<documentId>-<revision>.webp`; se regeneran cuando cambia la revisión.

| Tipo | Cómo se genera |
| --- | --- |
| Markdown (local y GitHub) | Texto de las primeras líneas dibujado a un `Bitmap`, sin WebView |
| Flow | Mini-grafo dibujado con la misma geometría de `FlowCamera.fit` |
| HTML y página web guardada | Captura al cerrar el lector (muestra dónde se quedó el usuario). Para documentos nunca abiertos, un render fuera de pantalla con el renderer privado y sin red que ya usa `export/FeedbackCapture.kt`, con scripts desactivados y de uno en uno |
| Carpeta | Icono y número de elementos |

La vista previa del punto abierto es una **imagen**, no un WebView vivo: abre al instante y no ejecuta la página. El contenido real sólo se ejecuta al pulsar «Abrir documento», con las políticas actuales.

### Estructura de código

`FlowScreen.kt` (373 líneas, todo en un archivo) se divide: `FlowCanvas` (gestos y dibujo; las fotos se pintan con `drawImage` recortado en círculo desde una caché LRU de `ImageBitmap`, sin dependencia nueva), `FlowDotSheet`, `FlowRadialMenu`, `FlowAvatarSheet`. La geometría del radial (posiciones, paginado, pétalo bajo el dedo) va en una función pura junto a `FlowGraph` para probarla en JVM.

Abrir un elemento desde un punto debe volver al Flow al pulsar Atrás. Hoy `LibraryViewModel.openedId` es un solo valor; se añade una pila corta de retorno.

## Selector de elementos (`picker/`, nuevo)

Un componente para toda la app, en hoja inferior a pantalla casi completa, con tres modos sobre el mismo estado (`ItemPickerState`, puro y probable en JVM):

| Modo | Devuelve | Uso |
| --- | --- | --- |
| Destino (pantalla 13) | Una carpeta o la raíz | Mover (sustituye el `AlertDialog` actual); después, destino de «Guardar copia» y de «Crear copia local» |
| Relacionar (pantalla 14) | Varios documentos o carpetas | Relacionar elementos existentes con un punto |
| Lista | Nada; abre un elemento | «Ver los N» del menú radial |

Comportamiento común:

- Cabecera que dice **qué** se está haciendo y **sobre qué** (el elemento que se mueve o la foto del punto).
- Búsqueda en toda la biblioteca; migas tocables; se entra a las carpetas en vez de leer rutas largas.
- «Usadas hace poco»: últimas 4 carpetas destino (preferencias locales).
- Lo que no se puede elegir se ve y explica por qué: la propia carpeta y sus descendientes (`FolderRules`), carpetas de GitHub de solo lectura, elementos ya relacionados.
- En Destino se listan carpetas y se resume cuántos documentos hay; el botón del pie nombra el destino («Mover a «Proyectos»») y permite crear una carpeta ahí mismo.
- En Relacionar: chips por tipo, cuadrícula de dos columnas con miniaturas de `PreviewStore`, selección múltiple con contador y botón «Relacionar N».

## Fases propuestas

| Fase | Contenido | Depende de | Validación |
| --- | --- | --- | --- |
| R0 | Tema: tokens, tipografías empaquetadas, formas; componentes compartidos (hoja, chip, fila, baldosa, botones) | — | Capturas clara/oscura; lint |
| R1 | Selector en modo Destino; reemplaza «Mover a…» | R0 | JVM de exclusiones, búsqueda y migas; instrumentada de mover |
| R2 | `PreviewStore` (Markdown, Flow, carpeta; HTML al cerrar el lector) | R0 | JVM de claves e invalidación; revisión de seguridad del render sin red |
| R3 | Flow: lienzo de tinta, puntos con foto, hoja de foto, tarjeta nueva; Room v4 | R0, R2 | Migración 3→4 con esquema exportado; FLOW-01…08 sin regresión |
| R4 | Relaciones: selector en modo Relacionar, punto abierto, menú radial, pila de retorno | R1, R3 | JVM de relaciones y geometría radial; prueba táctil en dispositivo |
| R5 | Biblioteca y hoja «Nuevo» | R0 | AppWorkflowTest actualizado |
| R6 | Piel de Lector, Markdown, Navegador y asistente de GitHub | R0 | Suites existentes; VIEW, PIN, DRAW y MD sin regresión |
| R7 | TalkBack del canvas (un nodo por punto visible, acciones personalizadas, lista como alternativa al radial), documentación y APK | Todas | Matriz manual |

Selector y Flow van primero porque son lo pedido; R5 y R6 son independientes y pueden intercalarse.

Casos de prueba nuevos a registrar en `docs/testing.md` al implementar: `PICK-01…06` (destinos inválidos, búsqueda, recientes, crear carpeta, multiselección, ya relacionados), `FLOW-09…15` (foto, relación única, radial con 2/6/7+ elementos, borrar el documento relacionado, duplicar con fotos, volver al Flow, migración v3→v4), `PREV-01…04`.

## Seguridad

- Sin permisos nuevos: Photo Picker para fotos, sin acceso general a archivos.
- Las miniaturas se generan sin red, sin bridge y sin scripts; no se ejecuta contenido importado para previsualizarlo.
- Las relaciones son identificadores de la biblioteca; el selector sólo muestra la biblioteca.
- Las fotos son copias privadas dentro del documento Flow; el original del usuario no se modifica. Los originales importados siguen intactos.

## Riesgos

- Memoria con muchas fotos: caché LRU con tope, decodificación reducida, puntos simples a poco zoom.
- Coste de miniaturas HTML: perezosas, una a la vez, con tope de tiempo; si fallan se muestra el icono del tipo.
- Migración en los dispositivos reales (S24, Tab): respaldar con `run-as` antes de instalar, como en v2 y v3.
- Conflicto de gestos: el arrastre del anillo radial frente al desplazamiento del canvas; el radial captura los gestos mientras está abierto.
- Sin desenfoque real detrás de las hojas sobre contenido WebView (límite ya conocido); en Flow el velo es color sólido.

## Decisiones que necesita el propietario

| # | Decisión | Recomendación |
| --- | --- | --- |
| 1 | Autorizar esta propuesta como trabajo nuevo antes de la aceptación de V1, y que sustituya la tarjeta provisional y parte de F2 de Flow Dots | Sí, por fases R0–R4 primero |
| 2 | Qué se puede relacionar con un punto | Documentos y carpetas de la biblioteca; fotos y archivos sueltos quedan para F5 |
| 3 | Punto con un solo elemento: ¿vista previa o abrir directo? | Vista previa; «Abrir documento» a un toque |
| 4 | ¿El lienzo de Flow es siempre oscuro o sigue el tema del sistema? | Siempre oscuro: es su identidad |
| 5 | Al borrar un documento relacionado | La relación desaparece (cascada), sin marcas de «roto» |
| 6 | Empaquetar Inter y Space Grotesk (OFL) en el APK | Sí; registrar licencias en `docs/dependencies.md` |
| 7 | Menú radial con más de 6 elementos | 6 pétalos + «+N» y giro por páginas |
