# Plan de implementación V1

Estado inicial: todos los hitos de software pendientes. La documentación de arranque no completa H1. Seguir el orden de la sección 10 de la [especificación](../Interactive_Notes_Android_Agent_Design.md). Los IDs de prueba remiten a [testing.md](testing.md).

| Hito | Dependencias | Entregables | Criterio de salida |
| --- | --- | --- | --- |
| H1 — Base y biblioteca | Documentación leída | Proyecto Kotlin/Compose, wrapper y versiones fijadas; SDK mínimo justificado; esquema Room/migraciones; repositorios; biblioteca anidada; CRUD, duplicación, búsqueda y confirmación de borrado. | Build debug verificable; LIB-01–04 pasan; datos sobreviven al reinicio; README contiene requisitos reales. |
| H2 — Importación y recursos | H1 | SAF y recepción Sharesheet; importadores HTML/árbol/ZIP; staging y recuperación; selección de entrada; proveedor HTTPS local con límites por documento. | IMP-01–08 y SEC-01 pasan; sin documentos parciales ni escrituras fuera de la raíz; fixtures de imports disponibles. |
| H3 — Lector | H2 | WebView inmersivo; controles bajo demanda; back/forward/reload/zoom/fullscreen; consola; autoscroll; política de red; lifecycle y navegación externa. | VIEW-01–06 y SEC-02–04 pasan; páginas estáticas, SPA compilada y canvas funcionan dentro de compatibilidad declarada. |
| H4 — Markdown | H1, H3 según editor | Notas `.md`, Source/Visual editable, toolbar, autosave, undo/redo, protección round-trip y renderer sanitizado. | MD-01–04 pasan; dependencia y licencia registradas; pérdida silenciosa de sintaxis descartada. |
| H5 — Controlador y pines | H3, H4 | Protocolo versionado, modos, selección DOM y superficie; resaltado durante long-press y desplazamiento; editor Markdown, persistencia, mover/reanclar/eliminar, undo/redo y Unanchored. | PIN-01–09 y SEC-05 pasan; demostración en dispositivo/emulador del gesto obligatorio, no sólo prueba unitaria de coordenadas. |
| H6 — Dibujos | H5 | Pen/highlighter/eraser, color/ancho, clear con confirmación; coordenadas y overlay; historial y persistencia de trazos. | DRAW-01–03 pasan; interacción web restaurada en View; reflujo documentado. |
| H7 — Endurecimiento y aceptación | H1–H6 | Completar fixtures y automatización; permisos, accesibilidad, fallos de renderer, cancelación, restauración y regresiones. | Matriz V1 ejecutada con evidencia; resolver fallos obligatorios, documentar límites de compatibilidad sin esconderlos. |
| H8 — Entrega V1 | H7 | APK debug reproducible; instrucciones de build/instalación; dependencias/licencias; resultados, limitaciones y relevo actualizados. | Build desde checkout limpio y aceptación V1 verificadas; registrar ruta/artefacto, hash APK, commit fuente y entorno. |

Las pruebas de seguridad se incorporan con cada función; H7 no es el primer momento de comprobarlas. Se permite una prueba técnica temprana del gesto o del editor para resolver riesgos, sin marcar como completos sus hitos dependientes.

## Primer cambio de implementación recomendado

1. Inspeccionar JDK/SDK y seleccionar versiones compatibles documentadas.
2. Crear el proyecto `app`, wrapper y configuración reproducible, sin credenciales ni rutas personales.
3. Definir entidades Folder/Document, repositorios e invariantes de jerarquía.
4. Entregar una biblioteca mínima persistente con creación, apertura y renombrado; completar el resto de LIB antes de cerrar H1.
5. Ejecutar build y pruebas disponibles; registrar resultados reales y pendientes en [progress.md](../progress.md).

## Fuera de V1

Sólo después de aceptación V1: overrides de texto, Restore original, conflictos y reconciliación avanzada de anclas (V1.1). RAR, exportación/backup, inspector DOM/red/JS eval, etiquetas, enlaces y herramientas avanzadas de escritura son trabajo posterior. Favoritos/recientes son opcionales y no desplazan requisitos obligatorios.

## Cambios de alcance

Una dificultad técnica se registra con impacto, evidencia y alternativa propuesta. No reducir V1 unilateralmente. Si una decisión bloquea sólo una parte, continuar las tareas independientes y dejar identificado el criterio pendiente. La ausencia de SDK permite preparar código/documentación, pero no declarar build ni APK verificados.
