# Progreso y relevo

Última actualización: **2026-10-19** (America/Mexico_City).

## Estado real

**V1 implementada en Kotlin/Compose, con APK debug generado y suite automatizada aprobada.** No iniciar nuevamente H1 ni desarrollar V1.1 antes de recoger la aceptación de uso del propietario. La evidencia y los límites de cobertura están en [docs/validation.md](docs/validation.md).

## Entrega por hito

| Hito | Estado y evidencia |
| --- | --- |
| H1 — Proyecto, Room y biblioteca | Implementado. Gradle wrapper, versiones fijadas, esquema Room v1 exportado, biblioteca anidada, búsqueda, CRUD, duplicación y recuperación. Tests JVM de jerarquía, copias y reapertura real de disco/Room aprobados. |
| H2 — Importación | Implementado. SAF archivo/árbol, Sharesheet, ZIP, staging privado, selección de entrada, límites, CRC y rechazo de traversal/symlinks/colisiones. Pruebas JVM e instrumentadas de URIs, copia independiente y ZIP aprobadas. |
| H3 — Lector | Implementado. WebView inmersivo, controles transitorios, navegación, zoom, consola, autoscroll, React compilado y red opt-in. Pruebas reales de WebView en Android 11 y 16 aprobadas. |
| H4 — Markdown | Implementado. Source y Visual editable con Milkdown local, autosave, undo/redo, fuente AtomicFile y protección de sintaxis no preservable. Flujo Android editar/cambiar modo/recrear aprobado. |
| H5 — Pines | Implementado. Long-press real con resaltado mientras se sostiene y actualización al deslizar; guardar/cancelar, editar, reanclar, borrar, undo/redo y Unanchored. Pruebas táctiles Android y DOM aprobadas. |
| H6 — Dibujos | Implementado. Pluma/resaltador/borrador, color/ancho, SVG en coordenadas de página, historial, persistencia y limpieza confirmada. Pruebas de trazo y regreso a View aprobadas. |
| H7 — Endurecimiento | Implementado y validado con automatización. Se detectó y corrigió un escape WebRTC vía iframe vacío; origen opaco y restricciones tempranas ahora lo bloquean. La cobertura manual de accesibilidad y hardware sigue limitada, como detalla el reporte. |
| H8 — APK y relevo | APK debug firmado generado, lint y build aprobados. La comprobación adicional desde archivo Git limpio se registra en el cierre del reporte. Instrucciones, licencias, fixtures y CI incluidos. |

## Resultados ejecutados

- **13/13** tests JVM aprobados; **16/16** instrumentados en emulador Android 16; **16/16** en Moto g71 5G Android 11 antes de desconectarlo.
- **7/7** pruebas web aprobadas: controlador, Milkdown editable sin red y React compilado con assets relativos.
- `lintDebug` aprobado sin errores; quedan advertencias de actualizaciones de dependencias y recomendaciones de Android/estilo. No se afirma lint sin advertencias.
- `assembleDebug` aprobado; paquete `com.hypermarker`, versión `1.0.0` (1), mínimo API 26.
- El último ajuste del sheet de herramientas evita su expansión parcial y pasó las 16 pruebas en el emulador. La suite del Moto corresponde a la revisión inmediatamente anterior a ese ajuste visual; no se volvió a usar el teléfono después de la instrucción de desconexión.
- Especificación original intacta: SHA-256 `c779db1ae246f5aba1e1084df4b0c7b3a4fc669ead8f4ded080846de5c44fdfb`.

## Decisiones y límites de compatibilidad

- Android nativo, módulo `app`; JDK 17, Gradle 8.13, AGP 8.12.0, Kotlin 2.1.20, compile/target SDK 36. Versiones/licencias en [docs/dependencies.md](docs/dependencies.md).
- Originales HTML inmutables; anotaciones JSON v1 en Room; Markdown autoritativo en archivo privado. Undo/redo es de sesión; el estado final sí se persiste.
- Cada documento tiene URL propia y CSP con origen opaco. `localStorage`/`sessionStorage` son de sesión en memoria; IndexedDB, workers, WebRTC y acceso directo entre frames están deshabilitados. Compatibilidad deliberadamente acotada; no prometer ejecución de cualquier web arbitraria.
- Red apagada por documento; permitir HTTPS/WSS no concede acceso general a Android. La revocación destruye el renderer anterior. WebView actualizado requerido para scripts/anotaciones.
- Canvas/SVG/iframe se anclan como superficie; trazos pueden desalinearse ante reflujo. React debe importarse compilado con rutas relativas; JSX/TSX no se ejecuta.
- Markdown no preservable permanece en Source con aviso. Autosave tiene una ventana aproximada de 500 ms; salir guarda explícitamente.
- No ejecutado: TalkBack completo, lápiz físico, dispositivo API 26, todas las variantes OEM/proveedores SAF y pruebas exhaustivas de falta de espacio/muerte del proceso/renderer. No equivalen a fallos observados ni a casos PASS.
- Exportación/backup, RAR, sincronización e inspector avanzado quedan fuera de V1. La licencia del código propio y firma/distribución de producción requieren decisión del propietario.

## Siguiente paso concreto

Instalar el APK debug de [README.md](README.md), usarlo con documentos representativos y recoger aceptación de uso. Priorizar la validación manual de TalkBack, lápiz y proveedores SAF del teléfono habitual antes de distribuir una versión de producción. Para continuar, revisar este archivo y el reporte; no confundir cobertura automatizada aprobada con certificación completa de la matriz manual.

## Registro para futuras sesiones

```text
Fecha y commit fuente:
Hito/casos trabajados:
Cambios completados:
Decisiones confirmadas o modificadas y motivo:
Validación: comando/pasos, entorno, resultado, evidencia:
Limitaciones/bloqueos y pruebas no ejecutadas:
Siguiente paso concreto:
```
