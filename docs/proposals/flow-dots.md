# Propuesta: Flow Dots

Estado: **autorizada por el propietario el 2026-10-26** como funcionalidad nueva fuera de la [especificación original](../../Interactive_Notes_Android_Agent_Design.md), que no se modifica. Lo más cercano en ella es §8 «Links between notes». La autorización incluye iniciar antes de cerrar la aceptación de uso de V1; no convierte V1.1 en trabajo aprobado.

## Idea

Nuevo tipo de documento con un canvas infinito. Cada punto es una nota con su título visible encima. Tocar un punto abre la nota como tarjeta flotante; tocar la tarjeta la expande a página completa con botón atrás arriba a la izquierda. Los puntos se conectan: el punto del que sale la conexión es el padre. Mantener presionado un punto pliega o despliega sus descendientes; mantener y arrastrar extiende una conexión hacia otro punto. Un modo «Reordenar» suspende las demás funciones y permite mover los puntos. Las notas admitirán fotos. La misma nota rica sustituirá el editor de los Pins. Todo se respaldará más adelante en un backend self-hosted.

## Decisiones confirmadas

| Tema | Decisión | Motivo |
| --- | --- | --- |
| Plataforma | Compose nativo dentro de Hyper-Marker | Compose cubre canvas, gestos, transiciones y selector de fotos; Flutter add-to-app duplicaría motor, diseño y toolchain; un canvas web contradice la regla nativa del proyecto. |
| Estructura | Grafo dirigido **sin ciclos**; un punto puede tener varios padres | Permite relaciones reales sin perder la noción de padre. Los ciclos se rechazan al conectar. |
| Plegado | Un punto plegado oculta los nodos alcanzables sólo a través de él | Un hijo con otro padre visible sigue visible; nunca desaparece un nodo por una ruta alternativa. |
| Soltar conexión en vacío | Se cancela | Evita crear nodos accidentales. |
| Persistencia | Tablas Room por entidad (`flow_nodes`, `flow_edges`) con `updatedAt` y tombstone `deletedAt` | Preparadas para sincronización incremental futura; evita reescribir el grafo entero como un blob. |
| Coordenadas | Unidades de mundo en dp; la cámara (desplazamiento, escala) es estado de sesión | La posición del punto es dato del documento; la vista no se sincroniza. |

## Fases

| Fase | Contenido | Estado |
| --- | --- | --- |
| F0 | Esta propuesta | Hecha |
| F1 | Room v2 con migración, tipo `FLOW` en biblioteca, canvas con pan/zoom, crear y persistir puntos | Prototipo |
| F2 | Módulo `note/`: tarjeta flotante → página completa con transición compartida | Pendiente |
| F3 | Conexiones por mantener+arrastrar, aristas, plegado, borrar punto/conexión | Prototipo |
| F4 | Reordenar completo, undo/redo, duplicado con medios | Reordenar básico en prototipo; resto pendiente |
| F5 | Fotos: selector, copia privada reescalada, deduplicación por hash, limpieza (tabla `attachments`, Room v3) | Pendiente |
| F6 | Pins adoptan `note/` y fotos (payload PIN v2) | Pendiente |
| F7 | Accesibilidad completa, pruebas instrumentadas, documentación y APK | Pendiente |

## Alcance del prototipo F1+F3

- Biblioteca: «Nuevo Flow» crea un documento `FLOW`; renombrar, mover, duplicar y borrar funcionan igual que el resto.
- Canvas: un dedo en vacío desplaza, dos dedos hacen zoom y desplazan; botón para encuadrar todos los puntos.
- Toque en vacío: abre el editor para un punto en esa posición; sólo se crea al guardar con contenido.
- Toque en punto: tarjeta flotante provisional con título, cuerpo y acciones (editar, plegar, conectar con…, eliminar). F2 la reemplaza por la nota rica.
- Mantener ≥ 500 ms sobre un punto (o el timeout largo de Android si es mayor) con háptica: soltar sin mover pliega/despliega; mover extiende una conexión elástica que resalta el punto de destino; soltar sobre un punto válido conecta, en vacío o sobre un destino que formaría ciclo cancela con aviso.
- Multitouch o movimiento antes del umbral cancelan el long-press.
- Reordenar: arrastrar un punto lo mueve y se guarda al soltar; crear, conectar y plegar quedan suspendidos.
- Alternativa sin gesto: «Conectar con…» lista los destinos válidos desde la tarjeta.

Fuera del prototipo: fotos, página completa, undo/redo, Markdown visual en el cuerpo, nodos accesibles por TalkBack dentro del canvas, sincronización.

## Riesgos abiertos

- Toque en vacío frente a desplazamiento: mitigado con el slop táctil y la creación diferida al guardar; validar en el teléfono.
- Rendimiento con miles de puntos: se dibuja todo en un `Canvas` con descarte fuera de la vista; medir en el S24.
- Migración Room v1→v2: primera del proyecto; probar sobre una copia de la base real antes de instalar en el teléfono.
- Sincronización: la estrategia de conflictos se define con el backend.
