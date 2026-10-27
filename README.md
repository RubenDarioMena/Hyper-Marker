# Hyper-Marker

Aplicación Android nativa para organizar proyectos HTML interactivos y notas Markdown, leer sin barras permanentes y añadir pines o dibujos sin modificar los originales.

**V1 implementada y APK debug verificado.** El estado verificable, los comandos ejecutados y los límites están en [progress.md](progress.md) y [docs/validation.md](docs/validation.md). La especificación de producto original se conserva en [Interactive_Notes_Android_Agent_Design.md](Interactive_Notes_Android_Agent_Design.md).

## Qué incluye

- Biblioteca con carpetas anidadas, búsqueda por nombre, creación, renombrado, movimiento, duplicación y eliminación con confirmación.
- Importación privada de HTML/HTM, carpetas SAF y ZIP; recepción desde Compartir de Android; selección de entrada cuando existe ambigüedad.
- WebView inmersivo sin barras: un toque muestra u oculta el botón de opciones (arriba a la derecha) y tres botones de cristal translúcido para los modos Ver, Editar y Dibujar. Historial, recarga, zoom al elemento y zoom original, consola y desplazamiento automático con velocidad/dirección y pausa por toque.
- Markdown `.md` sobre una hoja blanca (u oscura opcional) con edición Fuente y Visual mediante Milkdown local. La barra superior monocroma aplica estilo, tipo de letra, negrita, cursiva, tachado, código, enlaces, listas, citas, bloques de código, tablas y separadores sobre el texto seleccionado. Autoguardado, undo/redo y protección del fuente frente a sintaxis no preservable.
- Cruceta de cuatro acciones por modo al mantener presionado, reasignable y persistente; las demás acciones quedan en el menú de opciones.
- Pines: en Editar, mantener aproximadamente 500 ms, ver el elemento resaltado, deslizar para cambiar de objetivo y soltar para abrir la cruceta (Nota pin, Editar texto, Borrar adjuntos, Borrar cambios). Persistencia, edición, reanclaje, eliminación y estado sin ancla.
- Edición del texto visible como sustitución separada del HTML original, con restauración por elemento o desde la lista de notas y cambios.
- Pluma, resaltador, borrador, colores, anchos, undo/redo y limpieza confirmada; datos separados del HTML.
- Modo offline por defecto y permiso explícito para recursos de red por documento.
- GitHub Sync en `codex/github-sync`: selección de carpeta GitHub pública o autorizada por PAT, copia offline de Markdown/HTML compilado, refresco manual y periódico optativo, límites de consumo y credencial cifrada. La aceptación física con PAT del propietario sigue pendiente; ver [propuesta y evidencias](docs/proposals/github-sync.md).

## Compilar

Requisitos: JDK 17, Android SDK con `platforms;android-36`, `build-tools;35.0.0` y platform-tools. Gradle 8.13 se descarga mediante el wrapper con checksum. Android mínimo: **8.0 / API 26**. Las funciones web necesitan Android System WebView actualizado. Versiones y licencias: [docs/dependencies.md](docs/dependencies.md).

Abrir esta carpeta en Android Studio o, desde la raíz:

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Configurar `JAVA_HOME` y `ANDROID_HOME`, o el SDK en `local.properties` (ignorado por Git). En el entorno local usado para verificar el proyecto, el JDK está en `/opt/homebrew/opt/openjdk@17`; `scripts/verify.sh` lo detecta si `JAVA_HOME` no está definido.

El APK para pruebas se puede descargar desde [downloads/Hyper-Marker-1.0.0-debug.apk](downloads/Hyper-Marker-1.0.0-debug.apk?raw=1). Su [registro de entrega](downloads/README.md) identifica fuente, hash, firma y comprobaciones. Se versiona esta copia por solicitud explícita del propietario; los demás APK, SDK, caches y claves siguen siendo archivos locales.

**El APK de `downloads/` es anterior a GitHub Sync.** Para probar esta rama hay que compilar su código, revisar firma y respaldar los datos antes de instalarlo en un teléfono.

La copia local original de entrega se documenta en `artifacts/Hyper-Marker-1.0.0-debug.apk`, acompañada de `.apk.sha256`; los detalles de esa fuente limpia, firma e instalación están en [docs/validation.md](docs/validation.md). El APK debug se genera normalmente en `app/build/outputs/apk/debug/app-debug.apk`, applicationId `com.hypermarker`, versión `1.0.0` (1). No se incluyen claves privadas en Git. El workflow [Android debug](.github/workflows/android.yml) ejecuta tests JVM, lint y build y recopila APK/reportes como artefacto; consultar el resultado de la ejecución, no asumir éxito por la existencia del YAML.

## Usar

1. En la biblioteca, `+` permite crear carpetas/notas o importar un archivo, varios recursos compartidos, un ZIP o una carpeta HTML. También puedes enviar archivos a Hyper-Marker desde el menú Compartir de otra app.
2. Se prefiere `index.html`. Ante varias entradas sin candidato inequívoco, elige una. Los archivos se copian a la biblioteca privada y siguen disponibles aunque desaparezca el URI original.
3. En el lector, toca la página (fuera de botones y enlaces) para mostrar u ocultar los controles. El botón de opciones abre navegación, consola, autoscroll, permiso de red, las acciones que no están en la cruceta y su personalización. Android puede mostrar su aviso inicial de pantalla completa.
4. Mantén presionado en cualquier modo para abrir la cruceta. Ver: Autoscroll, Acercar elemento, Zoom original y Recargar. Editar: Nota pin, Editar texto, Borrar adjuntos y Borrar cambios. Dibujar: Color, Grosor, Herramienta y Estabilizar (aún no disponible). Mantén una opción de la cruceta, o usa el botón de intercambio del menú, para reasignarla.
5. En Editar, mantén y desliza sobre el contenido; al soltar, el elemento queda resaltado y la cruceta actúa sobre él. Guardar crea el pin; cancelar deja el documento intacto. También existe selección de elemento sin pulsación prolongada desde el menú. La lista de notas y cambios permite abrir, mover y reanclar notas sin ancla y restaurar textos.
6. En Dibujar, dibuja o borra trazos; un toque breve con el dedo muestra los controles y no deja un punto. Volver a Ver restituye la interacción normal de la página. Las notas Markdown se editan desde la biblioteca o se crean desde el lector.
7. Hay un ejemplo local en la biblioteca vacía y un conjunto de [fixtures](fixtures/README.md) para probar imports, React compilado, canvas, Markdown y casos maliciosos.

Para GitHub Sync, abre `+ → Añadir carpeta de GitHub`; puedes probar un repositorio público sin token o conectar un PAT fine-grained desde el campo seguro. Elige repo, rama y carpeta; el contenido remoto es de solo lectura y una copia editable se crea por separado. La [guía HTML del propietario](docs/guia-github-sync.html) explica preparación, permisos mínimos, respaldo del teléfono y pruebas de aceptación. No envíes un PAT al chat ni a Git.

## Verificar

```sh
./scripts/verify.sh
./gradlew connectedDebugAndroidTest
```

El segundo comando requiere un dispositivo/emulador conectado. Las pruebas instrumentadas crean contenido sintético y no deben ejecutarse sobre una instalación con notas personales. El runner de Gradle puede desinstalar los APK de prueba al terminar.

El editor y el fixture React compilados se versionan: Node no es necesario para compilar Android. Para regenerarlos o ejecutar sus pruebas, desde `web/`:

```sh
npm ci
npm run build
node licenses.mjs
node fixtures.mjs
npm test
```

Los tests web abren Chrome en un perfil temporal. En macOS usan Google Chrome; en otros sistemas, instalar Chromium con `npx playwright install chromium`, o proporcionar `CHROME_PATH`. Más detalles en [docs/testing.md](docs/testing.md).

## Límites de V1

- Importar salida HTML/JS/CSS de producción con rutas relativas. JSX/TSX requiere compilación externa. Para SPAs estáticas, preferir navegación por hash; no hay servidor de fallback para rutas inexistentes de BrowserRouter.
- Canvas/WebGL/SVG sin hijos significativos e iframes se anclan como superficies, sin selección semántica de objetos internos. Una ancla dudosa se marca sin ancla.
- Los dibujos usan coordenadas de página y pueden desalinearse si cambia el reflujo. Undo/redo conserva historial de sesión; el estado final sí se persiste.
- La red opt-in permite recursos HTTPS/WSS. No habilita navegación web externa, cookies de terceros, HTTP claro, permisos de cámara/micrófono, downloads, workers, WebRTC ni acceso a otros documentos.
- El contenido HTML usa un origen opaco: `localStorage`/`sessionStorage` se emulan en memoria durante la sesión; IndexedDB y acceso directo a otros frames no están disponibles. El estado JavaScript propio de la página no es un backup persistente. Pines, dibujos y Markdown sí se guardan de forma durable.
- El aislamiento WebView tiene límites. Ver [docs/security.md](docs/security.md), especialmente APIs de red, frames y páginas hostiles. No se presenta como sandbox perfecto.
- Visual cubre Markdown ordinario y tablas GFM bien formadas (Visual normaliza el relleno de columnas y usa `*` en listas). HTML, imágenes, tablas incompletas, referencias y extensiones no preservables permanecen en Fuente con aviso. El autoguardado tiene una ventana de unos 500 ms; salir guarda explícitamente.
- En Ver, la pulsación prolongada abre la cruceta en lugar de la selección de texto nativa del WebView. Los botones de cristal son translúcidos con tono adaptado al fondo; Compose no puede desenfocar el WebView situado detrás, así que no hay desenfoque real.
- La edición de texto sustituye el texto visible del elemento (formato interno incluido) sin tocar el archivo. En SPAs que vuelven a renderizar, la sustitución se reaplica como mejor esfuerzo y, si el contenido cambia, queda sin coincidencia.
- RAR, exportación/backup e inspector avanzado son posteriores. GitHub Sync descarga documentos de solo lectura; no sincroniza Flow Dots ni escribe en GitHub. La verificación final en el Samsung está pendiente.

## Continuar el proyecto

Leer [AGENTS.md](AGENTS.md), [Agent.md](Agent.md), [architecture.md](architecture.md), [progress.md](progress.md), [plan](docs/implementation-plan.md) y [pruebas](docs/testing.md). La estructura real está bajo `app/src/main/java/com/hypermarker/`.

Las licencias de terceros están dentro del APK, accesibles desde `+ → Licencias de código abierto`. La licencia de distribución del código propio sigue pendiente de decisión del propietario.
