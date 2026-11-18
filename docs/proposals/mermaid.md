# Diagramas Mermaid: leer, guardar, convertir en Flow y exportar

Estado: **pedido explícito del propietario el 2026-11-17/18** («quiero añadir la funcionalidad de leer mermaid diagrams… convertirlos en flows o añadir el diagrama a un flow existente… al exportarlo no se linkearía sino que se copiaría»; después: «permitir [exportar] el código a SVG y PNG de alta resolución»). Función nueva fuera de la [especificación original](../../Interactive_Notes_Android_Agent_Design.md), que no se modifica. Primera versión implementada; la aceptación en dispositivo está pendiente.

Referencia visual del propietario: su diagrama «Generador gravitacional / Superarma» exportado desde Mermaid con el aspecto *neo*. El código está en [`fixtures/mermaid/generador-gravitacional.mmd`](../../fixtures/mermaid/generador-gravitacional.mmd).

## Comportamiento

- **Nuevo tipo de documento `MERMAID`.** Se crea desde «Nuevo → Diagrama» (con una plantilla de tres pasos) o se importa un archivo `.mmd`/`.mermaid` desde «Archivos» o «Compartir». El código es la autoridad y se guarda solo (unos 500 ms, al pausar y al salir) en `documents/<id>/mermaid/source.mmd` con `AtomicFile`. Admite hasta 300 000 caracteres, el mismo `maxTextSize` del visor. No hay migración de Room: `type` ya es texto.
- **Pantalla con dos pestañas.** *Diagrama* dibuja el código con Mermaid local, admite zoom con dos dedos y empieza ajustado al ancho. *Código* es un editor monoespaciado con deshacer y rehacer. Un error de sintaxis muestra el mensaje del parser y deja visible, atenuado, el último diagrama válido. La miniatura de la biblioteca es la captura del visor al cerrar, como en HTML.
- **Aspecto.** `theme: "redux-color"`, `look: "neo"` y la fuente Recursive (la misma de la referencia), incrustada. Las etiquetas son SVG puro (`htmlLabels: false`), con `wrappingWidth` de 180 px para no cortar palabras como «INFRAESTRUCTURA».
- **Flow nuevo.** Crea un Flow con el mismo título en la carpeta del diagrama y copia cada nodo como punto. La primera línea de la etiqueta es el título; el resto es el cuerpo, más «Grupo: …» si el nodo pertenece a un subgrafo. Cada conexión pasa a ser una conexión del Flow. Las posiciones son las del layout de Mermaid (1 px = 1 dp de mundo) y los puntos de cada subgrafo llevan su color.
- **Añadir a un Flow.** Una hoja lista los Flows existentes. El diagrama se copia a la derecha del contenido actual (360 dp de separación, alineado arriba) con un punto amarillo de cabecera que lleva el título del diagrama y la fecha. Así se comparan varios diagramas en el mismo lienzo.
- **Copia, nunca enlace.** El Flow no guarda relación ni referencia al documento Mermaid. Editar o borrar el diagrama no cambia el Flow, y viceversa.
- **Exportar.** *SVG*: archivo autónomo con tamaño explícito, fondo blanco y la fuente incrustada (se ve igual fuera de la app y se abre en editores sin soporte de `foreignObject`). *PNG*: 2×, 3× o 4× con las dimensiones visibles antes de elegir. Por memoria se limita a 8192 px por lado y 40 MP, reduciendo proporcionalmente. El destino se elige con SAF; no se piden permisos.

## Qué no se copia literalmente (y cómo se avisa)

Flow es un grafo dirigido **sin ciclos** y sin etiquetas en las conexiones. La conversión nunca pierde información en silencio:

| Mermaid | En el Flow |
| --- | --- |
| Arista que cerraría un ciclo | No se crea; el punto de origen anota «→ destino (conexión omitida: crearía un ciclo)». |
| Etiqueta de arista (`A -- sí --> B`) | Se copia al cuerpo del destino: «← A: sí». |
| Arista repetida | Una sola conexión; la etiqueta, si la hay, se anota igual. |
| Autoconexión | Se anota en el punto («↻ Se conecta consigo mismo»). |
| Enlace invisible `~~~` | Se ignora: sólo sirve para el layout. |
| Subgrafo usado como extremo de una flecha | Se crea un punto con el título del subgrafo. |
| Forma del nodo, estilos `classDef`/`style`, iconos | No se copian; los puntos de Flow no tienen forma. |

Al terminar, un diálogo resume puntos, conexiones y cada categoría anterior, con «Abrir Flow» (Atrás vuelve al diagrama).

**Sólo `flowchart`/`graph` se convierten.** Los demás tipos (secuencia, clases, estados, ER, Gantt, mapa mental…) se dibujan, guardan y exportan, pero «Flow nuevo»/«Añadir a Flow» responden que ese tipo aún no se convierte.

## Implementación

- `web/src/mermaid.html` + `web/src/mermaid-viewer.js` se construyen en `app/src/main/assets/mermaid/`. `mermaid.min.js` 12.3.0 se copia sin cambios desde npm, y el visor propio lleva la fuente Recursive como `data:` (`@fontsource/recursive` 5.3.0, OFL).
- Funciones expuestas al host: `renderDiagram(source, session)`, `requestGraph(source, id)` y `exportDiagram(source, id, 'svg'|'png', escala)`. Las respuestas salen por `MermaidBridge` en fragmentos de 400 000 caracteres.
- La estructura del grafo sale del **propio parser de Mermaid** (`mermaidAPI.getDiagramFromText` → `db.getVertices/getEdges/getSubGraphs`), no de una gramática paralela, y las posiciones del SVG ya dibujado. Así se acepta exactamente la misma sintaxis que se ve.
- `MermaidFlow` (Kotlin, puro) valida el JSON y planifica la copia. `LibraryRepository.copyMermaidToFlow` la escribe en una transacción bajo el mutex: todo o nada, con límites de 5000 puntos y 20 000 conexiones por Flow.
- `ChunkAssembler` exige fragmentos en orden, un total estable y un máximo por tipo (SVG 24 M, PNG 96 M caracteres base64, grafo 16 M).
- Los puntos sin foto ahora se pintan con `ringColor` si lo tienen (antes sólo el anillo de los puntos con foto lo usaba). Efecto en datos existentes: un punto al que se le quitó la foto conserva el color de anillo que el usuario había elegido y ahora se pinta con él en lugar de menta.

## Seguridad

- El código Mermaid importado no es de confianza. El visor usa `securityLevel: 'strict'` (DOMPurify en etiquetas, `click` sin efecto) y Mermaid impide cambiar `securityLevel` desde directivas `%%{init}%%`.
- WebView propio: origen `https://mermaid.hypermarker.invalid`, sólo assets `/mermaid/…` del APK, `blockNetworkLoads`, sin archivos, contenido, almacenamiento DOM, permisos ni navegación. CSP `default-src 'none'`, `script-src 'self'`, `connect-src 'none'`, imágenes y fuentes sólo `data:`/`blob:`.
- El bridge exige frame principal, origen exacto, sesión aleatoria y mensajes ≤ 1 MB. Aun así, su contenido se trata como no confiable: textos recortados, coordenadas finitas, límites de cantidad y escritura sólo en el destino elegido por el usuario.
- La prueba web `hostile labels…` comprueba que `<img onerror>`, `<script>`, `javascript:`, `click … call` y una directiva `init` con `securityLevel: loose` no ejecutan código ni cargan recursos remotos.

## Límites conocidos

- APK +5,5 MB sin comprimir por `mermaid.min.js` (incluye todos los tipos de diagrama).
- El zoom es el del WebView (pellizco); no hay panel de minimapa ni ajuste automático en diagramas muy anchos.
- El editor de código no resalta sintaxis ni autocompleta. Los errores muestran el mensaje del parser, sin marcar la línea en el editor.
- En notas Markdown, la vista previa de bloques mermaid sólo existe en Visual; en Fuente se ve el código.
- No se probó todavía en dispositivo: WebView de Android real, zoom táctil, SAF, captura de miniatura ni rendimiento con diagramas de cientos de nodos.

## Decisiones del propietario (2026-11-18)

1. **Bloques ` ```mermaid ` en notas Markdown: sí.** Implementado (ver abajo).
2. **Estados y mapas mentales → Flow: pendiente, con evaluación.** Se dibujan y exportan desde ya; la conversión queda anotada (evaluación abajo).
3. **Flow → Mermaid: sí**, sobre todo para exportar SVG/PNG, compartir la idea general de un sistema o imprimirlo para anotar. Implementado (ver abajo).
4. **Referencias:** entregadas en SVG y PNG. Los SVG están en [`docs/design/mermaid/`](../design/mermaid/) y los códigos en `fixtures/mermaid/muestra-*.mmd`.

## Bloques mermaid dentro de notas Markdown

- En **Visual**, cada bloque ` ```mermaid ` (o ` ```mmd `) se muestra como diagrama con el mismo estilo del visor. «Editar código» despliega el código del bloque y «Solo diagrama» lo vuelve a ocultar (vista previa de bloques de código de Milkdown). **Fuente** sigue mostrando el texto tal cual; el Markdown guardado no cambia.
- Mermaid (5,5 MB) se carga **sólo** si la nota tiene un bloque de ese tipo. Usa el mismo asset y la misma configuración que el visor (`web/src/mermaid-config.js`).
- «Abrir como diagrama», bajo cada diagrama, guarda la nota y crea un documento Mermaid **copiado** del bloque («Diagrama · <nota>», misma carpeta). Desde él se convierte a Flow o se exporta. Atrás vuelve a la nota. En la vista de solo lectura de GitHub el botón no aparece.
- Un bloque con errores muestra el mensaje del parser en lugar del diagrama. Milkdown sanea la vista previa con DOMPurify además del modo `strict` de Mermaid; el botón se atiende por delegación, porque el saneado elimina manejadores.

## Flow → Mermaid

- Botón de diagrama en la cabecera del Flow → «Crear diagrama Mermaid». Crea un documento Mermaid **copiado** del Flow, en su carpeta, y lo abre. El Flow no cambia y no queda enlazado.
- Cada punto es una caja con el título y hasta dos líneas de su nota (80 caracteres por línea). El orden es de arriba abajo y de izquierda a derecha. Mermaid recalcula la disposición: las posiciones manuales del Flow no se conservan.
- Deshace las anotaciones de la conversión Mermaid→Flow para que ida y vuelta no pierdan estructura: «Grupo: X» vuelve a ser subgrafo; «← Padre: texto» vuelve a ser la etiqueta de esa flecha; «→ Destino (conexión omitida: crearía un ciclo)» vuelve como flecha punteada; «↻ Se conecta consigo mismo» vuelve como autoconexión. Una anotación que no corresponde a ningún punto queda como texto.
- Los puntos con color y sin grupo conservan el color en el borde (`style … stroke`). Fotos y relaciones no se copian.
- Límite: 4000 conexiones (el `maxEdges` del visor). El texto se escapa con entidades de Mermaid (`#quot;`, `#35;`, `#124;`…).
- Corrección encontrada al probarlo: el parser de Mermaid guarda esas entidades con marcadores internos (`ﬂ°…¶ß`). El extractor ahora los devuelve como entidades HTML y Kotlin las decodifica, así que una etiqueta con comillas o `#` ya no llega rota a Flow.

## Referencias visuales del propietario: hallazgos

Las cuatro muestras se dibujan como en las referencias: formas (estadio, rombo, subrutina, cilindro, bandera, círculo), flechas gruesa y punteada, `classDef`, subgrafos anidados con colores distintos, estados con inicio y fin, y mapa mental con ramas de color. Diferencias corregidas:

- **Estados con texto alineado a la izquierda.** Con etiquetas SVG, Mermaid reserva el ancho mínimo del estado pero dibuja el texto desde la izquierda. `polish()` recentra cada etiqueta en su caja (visor y notas).
- **Fondo de las etiquetas de flecha.** En estados quedaba blanco al 50 % (una regla CSS más específica); ahora es gris sólido `#cccccc`, como en las referencias.

Diferencia aceptada: las referencias usan etiquetas HTML y parten «¿Recursos suficientes?» o «Construir módulo» en dos líneas. Aquí, con `wrappingWidth` 180, caben en una. Se mantienen las etiquetas SVG porque el PNG las necesita (las HTML contaminan el canvas) y el SVG exportado se abre en más editores.

## Evaluación: estados y mapas mentales → Flow (pendiente)

Comprobado en el parser incluido: ambos tipos exponen `db.getData()` con la misma forma (nodos con `label`, `parentId` e `isGroup`; aristas con `start`, `end` y `label`). La extracción sería genérica y reutilizaría `MermaidFlow.plan`.

| Tipo | Complejidad | Utilidad inmediata | Comentario |
| --- | --- | --- | --- |
| Mapa mental | Baja (≈ medio día con pruebas) | **Alta** para planear: lluvia de ideas → puntos de Flow para luego relacionar documentos y fotos. | Es un árbol: encaja sin pérdidas en Flow (sin ciclos). Las posiciones radiales de Mermaid se pueden conservar. |
| Estados | Baja–media (≈ 1 día) | Media-baja | Las máquinas de estados suelen ser cíclicas (Activo → Inactivo): varias transiciones quedarían como anotaciones. Hay que traducir los pseudoestados inicio/fin y los estados compuestos (grupos). |

Recomendación: cuando se retome, empezar por mapas mentales. Los estados aportan poco mientras Flow no admita ciclos.

## Evaluación: texto en medio de las flechas de Flow (pregunta del 2026-11-18)

Viable y de alcance acotado (≈ 1–1,5 días con pruebas), recomendado:

- **Datos:** columna `label` en `flow_edges` (Room v5, migración `ALTER TABLE … ADD COLUMN`, sin reescribir filas). Hay que respaldar el S24/Tab antes de instalar, como en v2–v4.
- **Dibujo:** el texto en una píldora en el punto medio de la curva (`FlowCanvas` ya calcula la curva y la flecha). Se oculta por debajo del zoom de detalle, igual que los títulos.
- **Edición:** hoy las flechas no se pueden tocar. Propuesta: tocar cerca del punto medio abre «Texto de la conexión» (editar o quitar). Alternativa accesible en la tarjeta del punto, donde ya se listan las conexiones para quitarlas.
- **Mermaid:** las etiquetas pasarían directamente a la conexión en vez de anotarse en el cuerpo del destino, y Flow → Mermaid las exportaría como `-->|texto|`. Las anotaciones «← A: texto» de copias anteriores se podrían migrar a la etiqueta al editar.
- **Riesgos:** etiquetas largas que tapan puntos (limitar a ~60 caracteres visibles con elipsis) y toques ambiguos entre flecha y punto (los puntos tienen prioridad).
## Muestras para pedir referencias

Para afinar el aspecto con combinaciones que el diagrama de referencia no cubre, sirven las capturas de estos códigos desde el mismo editor que se usó para la referencia:

```mermaid
flowchart LR
    start([Inicio]) --> check{¿Recursos suficientes?}
    check -- sí --> build[[Construir módulo]]
    check -- no --> mine[(Minar materiales)]
    mine -.-> check
    build ==> test>Prueba de carga]
    test --> done((Listo))
    classDef warn fill:#fff3cd,stroke:#e0a800
    class mine warn
```

```mermaid
flowchart TB
    subgraph Fábrica
      direction LR
      subgraph Línea1["Línea 1"]
        a1 --> a2
      end
      subgraph Línea2["Línea 2"]
        b1 --> b2
      end
    end
    Fábrica --> Almacén
    a2 ~~~ b1
```

```mermaid
stateDiagram-v2
    [*] --> Inactivo
    Inactivo --> Cargando: energía
    Cargando --> Activo
    Activo --> Inactivo: apagar
    Activo --> [*]
```

```mermaid
mindmap
  root((Superarma))
    Materia extrema
      Transmutación
      Ultradensos
    Partículas
      Acelerador
      Microagujeros
```
