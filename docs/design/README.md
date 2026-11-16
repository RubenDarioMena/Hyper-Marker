# Documentos de diseño

Material de referencia de la [propuesta de rediseño](../proposals/ui-redesign.md). No es código de la app.

- `rediseno-de-la-app.html`: diseño del propietario, pantallas 1–8. Archivo autónomo; se abre en un navegador.
- `flow-atrevido/`: fuentes de las pantallas 9–14 (Flow y selector de elementos). Son artboards `.dc.html` del lienzo <https://claude.ai/artifact/WG3ikDpte5YG16rgpY2iec> y `canvas.json` con su disposición; dependen del `support.js` del lienzo, así que **no se renderizan solos** al abrirlos desde el repositorio. El lienzo es la vista de referencia.
- `capturas-implementacion/`: capturas de la implementación renderizadas por `RedesignScreenshotTest` (Robolectric, sin dispositivo), a 1× para comparar con las maquetas. El lector (5–6) no tiene captura porque requiere WebView.
