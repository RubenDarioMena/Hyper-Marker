# Seguridad y límites de confianza V1

Los documentos importados son código no confiable. Esta guía describe defensas implementadas; resultados en [validation.md](validation.md). WebView no se considera un sandbox perfecto ni un firewall del sistema.

## Archivos e importación

- SAF/Compartir se copian a almacenamiento privado antes de publicar el documento. No se depende de acceso indefinido a un URI temporal.
- `PathGuard` rechaza rutas vacías/absolutas, traversal, componentes `.`/`..`, separadores alternativos, NUL y nombres ambiguos con `%` o `:`. Se valida pertenencia canónica a la raíz, no sólo prefijos de texto.
- ZIP rechaza symlinks, entradas Unix especiales, colisiones incluso por mayúsculas/minúsculas, formatos no legibles y CRC incorrecto. Se cuentan bytes reales y se preservan rutas relativas.
- Límites de importación: 10.000 entradas, 100 MiB por archivo, 500 MiB totales, hasta 64 componentes, ratio ZIP 200:1. El ZIP comprimido de entrada también está limitado. Los tests inyectan presupuestos pequeños.
- Copias/cancelaciones/fallos usan staging y compensación. Al iniciar el proceso se limpian operaciones incompletas y directorios sin metadatos. La biblioteca no publica imports parciales.
- Los originales quedan separados y el código de anotación nunca escribe dentro de ellos. Duplicar crea identidades/archivos nuevos. Borrar requiere confirmación y limpia descendientes.

## Origen, CSP y canales de red

Cada documento tiene un host virtual propio y sólo su instancia de AssetLoader puede servir su raíz privada. No se sirven Room, staging, otros documentos ni archivos del sistema. `file://`, `content://`, acceso universal, popups, permisos web, cookies y descargas están deshabilitados.

Las respuestas del proyecto aplican CSP con **`sandbox allow-scripts` sin `allow-same-origin`**. El contenido se ejecuta con origen opaco: no puede recuperar APIs de una ventana hija vacía del mismo origen. Esta defensa se añadió después de comprobar que bloquear Fetch/WebSocket no basta para impedir un paquete STUN de WebRTC mediante un iframe vacío.

Se instala un bootstrap antes de scripts importados usando `DOCUMENT_START_SCRIPT`: deshabilita PeerConnection/WebTransport/Worker/SharedWorker. Se aplica a todos los frames del visor, sin concederles privilegios nativos. Workers y service workers también están bloqueados por CSP/configuración nativa. Si el proveedor no soporta el bootstrap temprano, el lector deshabilita JavaScript e informa que debe actualizarse WebView.

OFFLINE es el valor inicial. Se combinan `blockNetworkLoads`, intercepción de solicitudes, CSP y bootstrap. `connect-src` permite exclusivamente la URL privada del proyecto para que `fetch('datos.json')` funcione; recursos remotos, sockets y canales deshabilitados se rechazan. Las respuestas locales llevan CORS para que módulos ES y fetch funcionen desde el origen opaco. Ese encabezado no abre otros proyectos: el handler sólo autoriza el documento activo.

El permiso por documento permite recursos HTTPS/WSS y recrea WebView al cambiar/revocar, cerrando el anterior. No permite HTTP claro, navegación principal externa, cámara/micrófono, workers, WebRTC, descargas ni acceso a otras notas. El permiso Android `INTERNET` del manifiesto es necesario para la opción online, pero no implementa por sí solo la política.

La cobertura concreta incluye sockets TCP/UDP locales de prueba, Fetch, WebSocket, workers y acceso entre documentos en los proveedores probados. No equivale a auditar todos los canales de todas las versiones de WebView; mantener estas regresiones al actualizar dependencias/proveedor.

## Compatibilidad del almacenamiento web

El origen opaco impide cookies, IndexedDB y DOM storage nativo. Se emulan APIs comunes de `localStorage` y `sessionStorage` en memoria para permitir interactividad durante una sesión. Esos valores no se restauran al recrear/cerrar el visor. Las páginas que dependan de almacenamiento web persistente o comunicación directa entre frames tienen esta limitación explícita; no se oculta tras el permiso de red.

Markdown, pines, trazos y preferencias se guardan en almacenamiento nativo privado. `AtomicFile` protege el Markdown; Room guarda metadatos y anotaciones. Backups automáticos y transferencia de datos están deshabilitados en el manifiesto/reglas de extracción; exportar/backup es una función posterior.

## Bridge

El editor confiable usa origen exacto. El lector aislado necesita transporte de mensajes con regla `*` porque su origen es opaco; la autorización real exige **frame principal + URL HTTPS privada del documento activo + sesión/revisión + modo + contrato validado**. Los subframes no pueden usar el bridge para acciones nativas. Navegaciones completas renuevan sesión; eventos tardíos o de otra página se descartan.

La página comparte contexto con el controlador: una sesión no es un secreto frente a ella. Por ello los mensajes siguen siendo no confiables. No existe `addJavascriptInterface`, eval nativo expuesto, lectura arbitraria de archivos, ejecución de procesos, intents ni concesión de permisos. Guardar pines requiere editor nativo y contenido significativo.

`BridgeValidator` limita tipos, tamaño, frecuencia, IDs de petición y coordenadas finitas. `longpress` sólo se acepta en Ver/Dibujar y con rectángulo finito y positivo; `target.selected` sólo en Editar, con texto acotado a 20 000 caracteres y listas de ids acotadas. Esas listas no son confiables: borrar adjuntos o cambios sólo elimina ids existentes del tipo y página indicados, tras confirmación y con deshacer. Las sustituciones de texto se aplican con `textContent`, nunca como HTML, no modifican el archivo original y excluyen campos de formulario. El repositorio verifica pertenencia, cantidad, tamaño agregado y tipos de anotación. Títulos/Markdown viajan como JSON y se presentan como texto o mediante un editor controlado; no se interpolan como scripts. Fuente Markdown con HTML/extensiones no preservables permanece en Source.

## Disponibilidad y riesgos residuales

Consola limitada a 300 entradas, imports por streaming, recálculos DOM agrupados, persistencia por trazo/operación e historial de edición acotado. Una caída del renderer no elimina originales ni notas guardadas. Errores de guardado dejan estado pendiente y permiten reintentar.

HTML puede consumir recursos, cambiar su DOM o interferir visualmente con overlays. No se garantiza identidad semántica de objetos canvas, recuperación de DOM arbitrariamente cambiante ni alineación de dibujos después del reflujo. El aislamiento y las APIs web dependen del proveedor instalado. Los límites se prueban con contenido sintético; no se prometen privilegios o compatibilidad que no hayan sido verificados.
