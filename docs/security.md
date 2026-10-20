# Seguridad y límites de confianza

Diseño y criterios pendientes de implementación. Esta guía desarrolla las secciones 1, 5 y 9 de la [especificación](../Interactive_Notes_Android_Agent_Design.md); no constituye una auditoría ni una garantía de sandbox perfecto.

## Activos y entradas no confiables

Proteger originales, Markdown, anotaciones, otros documentos de la biblioteca y datos privados de Android. Considerar no confiables archivos/URIs recibidos, proveedores externos, entradas ZIP, nombres/rutas, HTML/CSS/JS, mensajes del controlador, texto Markdown y destinos de navegación. Un documento puede intentar leer otro, agotar recursos, suplantar eventos o provocar accesos remotos.

## Importación

- Copiar al almacenamiento privado antes de publicar en la biblioteca. Gestionar permisos SAF sólo durante el tiempo necesario; no depender de URIs temporales de Compartir.
- Normalizar nombres y rutas antes de escribir. Rechazar rutas absolutas, traversal, separadores alternativos peligrosos, bytes nulos, enlaces simbólicos y colisiones de destino. Propuesta conservadora: rechazar todos los symlinks del import, aunque algunos pudieran permanecer dentro de la raíz.
- Comprobar que cada destino resuelto queda dentro de la raíz canónica de staging. No usar una simple comparación de prefijos que confunda directorios hermanos. No sobrescribir archivos ya extraídos; detectar colisiones tras normalización.
- Contar bytes realmente leídos/descomprimidos, no confiar en tamaños declarados. No extraer ZIP anidados automáticamente. Aplicar los mismos límites al copiar árboles SAF.
- Propuesta inicial ajustable: 10.000 archivos, 100 MiB por archivo, 500 MiB totales, 64 componentes de profundidad y relación de compresión ZIP máxima 200:1. Validar estas cifras con fixtures/dispositivos; documentar cualquier cambio. Límites no sustituyen cancelación, control de espacio libre ni protección de memoria.
- Mostrar error claro, permitir cancelar, limpiar staging y recuperar operaciones interrumpidas. Una entrada inválida rechaza la operación completa; no producir una importación aparentemente exitosa con archivos omitidos silenciosamente.

## Acceso local y origen

- Servir sólo el documento activo mediante HTTPS virtual controlado. Validar también rutas de solicitudes web, incluyendo rutas codificadas, separadores y decodificación consistente; una ruta rechazada no debe caer al filesystem ni a red como fallback.
- No servir la base Room, Markdown de otros documentos, staging, archivos del sistema ni carpetas superiores. MIME correcto para recursos; entrada inexistente genera error visible, no acceso ampliado.
- Deshabilitar acceso irrestricto `file://`, acceso universal desde file URLs y esquemas peligrosos. No exponer `content://` a páginas como vía indirecta de lectura.
- Validar aislamiento entre documentos para cookies, DOM storage, cache y service workers. Usar una ruta diferente bajo el mismo host no constituye aislamiento de origen. Si no se puede ofrecer persistencia web aislada, optar por una política más restrictiva y documentar el efecto en compatibilidad.

## Política de red y capacidades web

`trustPolicy` propuesta: `OFFLINE` por defecto y `NETWORK_ALLOWED` sólo tras acción del usuario para ese documento. Duplicados/importaciones nuevas vuelven a OFFLINE como valor seguro. Permitir red no concede micrófono, cámara, ubicación, archivos, downloads automáticos ni interfaces nativas.

En H3 implementar y verificar controles para navegación principal, recursos secundarios, iframes, fetch/XHR, WebSockets, service workers, redirecciones y solicitudes tras cambiar de documento. Un solo callback de intercepción no debe asumirse suficiente. El proveedor local debe seguir funcionando offline. Rechazar contenido mixto y definir esquemas permitidos; abrir destinos externos sólo con acción explícita. No cargar páginas externas privilegiadas dentro de un contexto con bridge activo.

Al revocar permiso de red, detener/recargar o recrear el viewer según sea necesario para cortar conexiones activas; comprobar que no siguen canales abiertos. Registrar cualquier canal no controlable y resolverlo antes de afirmar que el modo offline bloquea solicitudes de red. El permiso Android `INTERNET`, si se declara para la función opt-in, no implementa por sí mismo la autorización por documento.

## Bridge y anotaciones

- Usar contrato versionado, frame/origen autorizados, documento/revisión/sesión activa, tipos conocidos, límites de frecuencia/tamaño y validación estricta de campos. Rechazar números no finitos y referencias ajenas.
- No exponer acceso general al filesystem, procesos, intents, credenciales ni lectura/escritura arbitraria de Room. No implementar JS eval remoto como parte de la consola V1.
- La página puede compartir contexto con el controlador inyectado. Tratar sus mensajes como datos no confiables incluso con origen correcto. Los identificadores de sesión no son secretos frente a esa página.
- El modo nativo limita qué eventos acepta la app; un mensaje aislado no crea un pin sin editor/guardado. Validar pertenencia de las anotaciones y requerir confirmación para operaciones destructivas previstas por producto.
- Renderizar títulos/cuerpos como texto o Markdown sanitizado, sin interpolación en scripts/HTML. Un pin nunca modifica el original. Limitar buffers de consola y evitar registrar contenido privado innecesariamente.

## Disponibilidad y privacidad

Limitar memoria de importación, consola y geometría; agrupar observadores DOM y cancelar trabajos al salir. Una caída de renderer no debe destruir datos persistidos. Definir política de backup Android explícita en H1/H7: propuesta conservadora sin copia automática de documentos personales hasta diseñar y verificar backup/restauración. No introducir analítica ni transferencia de documentos como requisito implícito.

Usar fixtures sintéticos, nunca archivos personales, para pruebas maliciosas. No incluir tokens, claves de firma, contraseñas, SDK locales ni contenido de usuario en commits.

## Riesgos residuales que deben comunicarse

HTML puede alterar su DOM, ocultar elementos, consumir recursos o interferir con overlays dentro del WebView. El anclaje puede fallar en páginas dinámicas/iframes y los dibujos pueden desalinearse por reflujo. WebView depende de la versión instalada del sistema; documentar Android/WebView probados. Las páginas que requieran servicios remotos o APIs web no compatibles pueden quedar incompletas. Ningún documento debe presentarse como perfectamente aislado sin pruebas que sustenten el alcance concreto de esa afirmación.
