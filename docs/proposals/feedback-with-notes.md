# Compartir HTML con notas

Implementación autorizada por el propietario el 2026-11-08. El botón es «Exportar con notas» en las opciones del documento. Genera un ZIP con HTML y recursos originales, JSON editable/versionado, un resumen para agentes y una vista de revisión con pines y dibujos. No agrega un flujo de reimportación.

Cada dibujo incluye un PNG completo obligatorio, no sólo el viewport visible. El nombre incluye el tag y selector CSS saneados; el selector completo está en `annotations.json` y `feedback.md`. Las imágenes muy grandes se reducen proporcionalmente sin recortar. Los nuevos dibujos se agrupan por sesión; los existentes sin sesión, por elemento o página.

El resumen no usa un modelo ni OCR: reproduce las notas y contextualiza cada anotación con página, selector, texto del elemento y captura. No inventa el significado de un dibujo. La captura usa un renderer offline separado y conserva los trazos incluso si no encuentra el ancla; en ese caso declara falta de contexto y utiliza fondo blanco.

El ZIP incluye `original/`, `annotations.json`, `feedback.md`, `review.html`, `review/` y `captures/`. La vista de revisión es derivada; los originales permanecen intactos. No contiene tokens, preferencias, historial del navegador ni otros documentos. Evidencia y límites de validación en `progress.md`.
