# Entorno y dependencias fijadas

La implementación inicial usa un módulo `app`, namespace/applicationId `com.hypermarker`, `versionCode=1`, `versionName=1.0.0`. No contiene credenciales ni firma de producción.

| Componente | Versión / decisión | Motivo y licencia |
| --- | --- | --- |
| Android mínimo | API 26 / Android 8 | Base práctica para APIs de archivos usadas por el importador y una WebView actualizable. Se requiere proveedor WebView con `WEB_MESSAGE_LISTENER` y `DOCUMENT_START_SCRIPT` para el lector interactivo seguro; Visual requiere mensajería; se informa si falta. |
| compileSdk / targetSdk | 36 / 36 | SDK disponible y compatible con el AGP fijado. |
| Build Tools | 35.0.0 (default de AGP 8.12) | Disponible localmente; preparado en CI. |
| JDK | 17; compilación local Homebrew 17.0.20 | Bytecode Java/Kotlin 17. El wrapper no contiene rutas locales. |
| Gradle / AGP | 8.13 / 8.12.0 | Compatibilidad oficial; distribución Gradle verificada con SHA-256 en el wrapper. |
| Kotlin / Compose plugin | 2.1.20 / 2.1.20 | Compiler plugin de la misma versión. Apache-2.0. |
| KSP | 2.1.20-1.0.32 | Generación de Room. Apache-2.0. |
| Compose BOM | 2025.04.01 | Material 3, iconos y UI con versiones coherentes. Apache-2.0. |
| Activity / Lifecycle | 1.10.1 / 2.9.0 | Compose, lifecycle y ViewModels. Apache-2.0. |
| Room | 2.7.1 | Persistencia y esquema v1 exportado en `app/schemas/`. Apache-2.0. |
| AndroidX WebKit | 1.14.0 | AssetLoader, mensajes con origen/frame y controles de service workers. Apache-2.0. |
| DocumentFile | 1.0.1 | Acceso SAF a árboles. Apache-2.0. |
| AndroidX WorkManager Runtime KTX | 2.12.0 | Un solo trabajo periódico para GitHub Sync; API mínima 24, compatible con la mínima 26 de la app. Apache-2.0. Dependencia nueva de esta rama, compilada en emulador Android 16. |
| Apache Commons Compress | 1.28.0 | ZIP con atributos Unix, rechazo de symlinks y control de entradas. Apache-2.0 y avisos incluidos. |
| Milkdown Crepe / kit / utils | 7.22.3 | Editor visual Markdown local basado en ProseMirror; `kit` aporta los comandos de formato usados por la barra superior. MIT; dependencias transitivas y textos completos en `assets/licenses/WEB.txt`. |
| esbuild | 0.28.2 | Genera el bundle local del editor. MIT, herramienta de desarrollo. |
| JUnit / Robolectric | 4.13.2 / 4.14.1 | Tests JVM y Room/JSON Android; EPL-1.0 / MIT, herramientas de prueba. |
| AndroidX Test runner / ext / rules / UIAutomator | 1.6.2 / 1.2.1 / 1.6.1 / 2.3.0 | Instrumentación en emulador. Apache-2.0, herramientas de prueba. |
| Playwright | 1.51.1 | Tests DOM/editor en Chrome aislado. Apache-2.0, herramienta de prueba. |
| React / Vite | 19.3.1 / 8.3.4 | Fixture compilado, no shell de la aplicación. MIT. |

El proyecto conserva versiones probadas; no usa dependencias `+`. Las advertencias de lint sobre versiones más nuevas no significan que se hayan actualizado o validado automáticamente.

## Editor y licencias

Los archivos generados del editor se distribuyen en `app/src/main/assets/editor/`. Para cambiarlos, editar `web/src/`, ejecutar `npm ci`, `npm run build` y `node licenses.mjs` desde `web/`. El generador incorpora las licencias de dependencias de producción; `remark-math` necesita una copia de su licencia upstream en `web/license-overrides/` porque el paquete publicado no la incluye. El editor no usa CDN, red, telemetría ni carga de imágenes remotas. Las construcciones fuera del subconjunto preservable permanecen en Source con aviso.

Las licencias se incluyen como assets del APK y se consultan desde el menú `+` de la biblioteca. Esto no asigna una licencia nueva al código propio de Hyper-Marker; la decisión de distribución del proyecto sigue correspondiendo a su propietario.

## Referencias primarias consultadas

- [Compatibilidad AGP 8.12 / Gradle / JDK / API 36](https://developer.android.com/build/releases/agp-8-12-0-release-notes).
- [Room y releases](https://developer.android.com/jetpack/androidx/releases/room), [WebKit y releases](https://developer.android.com/jetpack/androidx/releases/webkit).
- [WorkManager 2.12.0](https://developer.android.com/jetpack/androidx/releases/work) y [trabajo periódico con restricciones](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).
- [Mensajería de WebViewCompat](https://developer.android.com/reference/androidx/webkit/WebViewCompat).
- [Milkdown Crepe](https://milkdown.dev/docs/guide/using-crepe) y código/tipos del paquete fijado en el lockfile.
- [Licencia de remark-math](https://github.com/remarkjs/remark-math/blob/main/license).
- [Setup Android](https://github.com/android-actions/setup-android), [Setup Java](https://github.com/actions/setup-java), [Upload artifact](https://github.com/actions/upload-artifact). Las acciones de CI se fijan a commits concretos.
