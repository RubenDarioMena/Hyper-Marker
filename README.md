# Hyper-Marker

Aplicación Android nativa para organizar proyectos HTML interactivos y notas Markdown, leer sin barras permanentes y añadir pines o dibujos sin modificar los originales.

**V1 implementada y APK debug verificado.** El estado verificable, los comandos ejecutados y los límites están en [progress.md](progress.md) y [docs/validation.md](docs/validation.md). La especificación de producto original se conserva en [Interactive_Notes_Android_Agent_Design.md](Interactive_Notes_Android_Agent_Design.md).

## Qué incluye

- Biblioteca con carpetas anidadas, búsqueda por nombre, creación, renombrado, movimiento, duplicación y eliminación con confirmación.
- Importación privada de HTML/HTM, carpetas SAF y ZIP; recepción desde Compartir de Android; selección de entrada cuando existe ambigüedad.
- WebView inmersivo con historial, recarga, zoom, consola y desplazamiento automático con velocidad/dirección y pausa por toque.
- Markdown `.md` con edición Source y Visual mediante Milkdown local, autoguardado, undo/redo y protección del fuente frente a sintaxis no preservable.
- Pines: mantener aproximadamente 500 ms, ver el elemento resaltado, deslizar para cambiar de objetivo y soltar para editar. Persistencia, edición, reanclaje, eliminación y estado sin ancla.
- Pluma, resaltador, borrador, colores, anchos, undo/redo y limpieza confirmada; datos separados del HTML.
- Modo offline por defecto y permiso explícito para recursos de red por documento.

## Compilar

Requisitos: JDK 17, Android SDK con `platforms;android-36`, `build-tools;35.0.0` y platform-tools. Gradle 8.13 se descarga mediante el wrapper con checksum. Android mínimo: **8.0 / API 26**. Las funciones web necesitan Android System WebView actualizado. Versiones y licencias: [docs/dependencies.md](docs/dependencies.md).

Abrir esta carpeta en Android Studio o, desde la raíz:

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Configurar `JAVA_HOME` y `ANDROID_HOME`, o el SDK en `local.properties` (ignorado por Git). En el entorno local usado para verificar el proyecto, el JDK está en `/opt/homebrew/opt/openjdk@17`; `scripts/verify.sh` lo detecta si `JAVA_HOME` no está definido.

La copia local de entrega está en `artifacts/Hyper-Marker-1.0.0-debug.apk`, acompañada de `.apk.sha256`; los detalles de fuente limpia, firma e instalación están en [docs/validation.md](docs/validation.md). El APK debug se genera normalmente en `app/build/outputs/apk/debug/app-debug.apk`, applicationId `com.hypermarker`, versión `1.0.0` (1). No se incluyen claves de producción ni APK en Git. El workflow [Android debug](.github/workflows/android.yml) ejecuta tests JVM, lint y build y recopila APK/reportes como artefacto; consultar el resultado de la ejecución, no asumir éxito por la existencia del YAML.

## Usar

1. En la biblioteca, `+` permite crear carpetas/notas o importar un archivo, varios recursos compartidos, un ZIP o una carpeta HTML. También puedes enviar archivos a Hyper-Marker desde el menú Compartir de otra app.
2. Se prefiere `index.html`. Ante varias entradas sin candidato inequívoco, elige una. Los archivos se copian a la biblioteca privada y siguen disponibles aunque desaparezca el URI original.
3. En el lector, el botón flotante abre herramientas, navegación, consola, autoscroll y permiso de red. Android puede mostrar su aviso inicial de pantalla completa.
4. En Pin, mantén y desliza sobre el contenido. Guardar crea el pin; cancelar deja el documento intacto. También existe selección de elemento sin pulsación prolongada. La lista de pines permite abrir, mover y reanclar notas sin ancla.
5. En Draw, dibuja o borra trazos; volver a Ver restituye la interacción normal de la página. Las notas Markdown se editan desde la biblioteca o se crean desde el lector.
6. Hay un ejemplo local en la biblioteca vacía y un conjunto de [fixtures](fixtures/README.md) para probar imports, React compilado, canvas, Markdown y casos maliciosos.

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
- Visual cubre Markdown ordinario. HTML, imágenes, tablas, referencias y extensiones no preservables permanecen en Source con aviso. El autoguardado tiene una ventana de unos 500 ms; salir guarda explícitamente.
- RAR, exportación/backup, sincronización, inspector avanzado y edición del texto HTML renderizado son posteriores a V1.

## Continuar el proyecto

Leer [AGENTS.md](AGENTS.md), [Agent.md](Agent.md), [architecture.md](architecture.md), [progress.md](progress.md), [plan](docs/implementation-plan.md) y [pruebas](docs/testing.md). La estructura real está bajo `app/src/main/java/com/hypermarker/`.

Las licencias de terceros están dentro del APK, accesibles desde `+ → Licencias de código abierto`. La licencia de distribución del código propio sigue pendiente de decisión del propietario.
