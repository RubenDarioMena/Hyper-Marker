# Validación V1

Fecha: **2026-10-19**, America/Mexico_City. V1 implementada; resultados siguientes observados en ejecución real. La matriz completa está en [testing.md](testing.md); esta entrega no declara aprobados los casos manuales no ejecutados.

## Entornos

- macOS arm64; JDK Homebrew 17.0.20; Gradle 8.13; Android SDK 36, build-tools 35.0.0.
- Emulador Pixel 7 arm64 `flutter_emulator`, imagen 36.1, Android 16 / API 36 reportado por el runner; Android System WebView **153.0.8010.36**.
- Teléfono **Moto g71 5G, Android 11 / API 30**, WebView **155.0.8059.43**. Se dejó de usar tras la instrucción del propietario de desconectarlo.
- Chrome de escritorio con perfil temporal para tests DOM/editor; Node 22.23.1. Estas pruebas complementan y no sustituyen WebView Android.

## Resultados

| Comprobación | Resultado | Alcance real |
| --- | --- | --- |
| `testDebugUnitTest` | **13/13 PASS**, sin omisiones | Room/archivos y reapertura, jerarquía/ciclos/copias, recuperación de huérfanos, ZIP/rutas/límites, mensajes y MarkdownPolicy. |
| `connectedDebugAndroidTest`, emulador | **16/16 PASS**, sin omisiones | 4 importación, 11 WebView y 1 flujo completo de UI. Revisión final con sheet de herramientas totalmente expandido. |
| `connectedDebugAndroidTest`, Moto | **16/16 PASS**, sin omisiones | Las mismas clases. Revisión inmediatamente anterior al ajuste visual de expansión del sheet; no se repitió en el teléfono desconectado. |
| `npm test`, desde `web/` | **7/7 PASS** | Selección viva, deslizamiento/cancelación, anclas huérfanas, superficies, dibujo/View, edición Milkdown sin red y React/Vite compilado. |
| `lintDebug` | **PASS**, cero errores | 35 advertencias: versiones más recientes disponibles y recomendaciones Android/estilo; no ocultadas ni tratadas como errores de ejecución. |
| `assembleDebug` | **PASS** | APK debug firmado, versión 1.0.0 (1), `com.hypermarker`, mínimo API 26. |

Comando final sobre el emulador (JDK/SDK configurados):

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

El flujo UI envía un HTML mediante `ACTION_SEND` con URI real de MediaStore, crea un pin con gesto Android, guarda/cancela, ejecuta undo/redo, rota/recrea la actividad, crea Markdown, edita Source y Visual y comprueba el archivo persistido. Las pruebas de importación pierden el URI original y verifican que la copia privada sigue abriendo. El árbol SAF de proveedores externos y sus diálogos OEM requieren comprobación manual adicional; no se afirma haber recorrido todos esos diálogos.

Las once pruebas WebView ejercitan gesto real con selección antes de soltar, deslizamiento, superficie canvas, ancla desaparecida, zoom, cancelación, interacción normal en View, historial y consola, autoscroll/pausa/dirección, React compilado, estado JavaScript de sesión, recursos JSON locales y permisos de red. El opt-in WSS tiene control positivo con servidor local y la revocación comprueba que no se abre una conexión nueva. No se infiere seguridad únicamente de ausencia de crash.

## Hallazgo de seguridad corregido

La prueba inicial detectó tráfico STUN WebRTC obtenido mediante un constructor de `about:blank` dentro de iframe, pese al bloqueo de recursos y CSP ordinaria. Se corrigió con CSP `sandbox allow-scripts` sin `allow-same-origin` y bootstrap temprano de APIs. La regresión comprueba constructor principal deshabilitado e intento mediante frame sin paquetes UDP observados. También se prueban fetch/WebSocket/workers offline, lectura entre documentos y validación acotada del bridge. Son controles de canales concretos, no una garantía absoluta sobre futuras versiones del motor.

El origen opaco limita compatibilidad: almacenamiento JS sólo de sesión mediante shim, sin IndexedDB ni acceso directo a otros frames. Notas, pines y dibujos nativos sí son persistentes. Ver [security.md](security.md).

## Evidencia y reproducción

Los reportes regenerables quedan en:

- `app/build/reports/tests/testDebugUnitTest/index.html` y XML bajo `app/build/test-results/`.
- `app/build/reports/androidTests/connected/debug/index.html` y XML bajo `app/build/outputs/androidTest-results/connected/` (última ejecución: emulador).
- `app/build/reports/lint-results-debug.html` y `.xml`.
- Capturas locales de selección sostenida, deslizamiento, pin guardado y edición Visual bajo `artifacts/verification/`; son evidencia local ignorada por Git. Las pruebas Android guardan capturas en Downloads/HyperMarkerVerification antes de que Gradle desinstale el APK de prueba.

Para repetir: `./scripts/verify.sh`, `./gradlew connectedDebugAndroidTest`, y `cd web && npm ci && npm test`. Usar sólo instalaciones de prueba: el runner puede desinstalar la aplicación. El workflow Android incluido ejecuta JVM/lint/build y recopila artefactos; su existencia no certifica una ejecución remota aprobada.

## APK y comprobación desde fuente limpia

APK de build: `app/build/outputs/apk/debug/app-debug.apk`. La copia de entrega, hash y comprobación adicional desde archivo Git limpio se registran al finalizar el cierre. Se usa firma debug de desarrollo; no es una publicación en Google Play ni una firma de producción.

## Cobertura pendiente explícita

No se ejecutó la matriz manual completa de TalkBack, texto grande, lápiz físico, API 26, otros WebView/OEM, todos los proveedores SAF, revocaciones del sistema, agotamiento real del disco, kill en cada punto de transacción ni muerte forzada del renderer. Recuperación, cancelación y compensaciones tienen código y pruebas acotadas; no se presentan como fault injection exhaustiva. La ventana de autosave y el reflujo de dibujos están documentados.

La entrega acredita la implementación funcional y las suites descritas. La aceptación de uso y las validaciones de hardware/accesibilidad anteriores deben completarse antes de una distribución de producción.
