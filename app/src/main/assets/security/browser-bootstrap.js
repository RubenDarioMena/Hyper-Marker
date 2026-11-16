(() => {
  'use strict';
  // Navegador remoto: sin bridge ni archivos locales. Se conservan Workers (los usan verificaciones
  // como Cloudflare), pero WebRTC sigue deshabilitado para no exponer direcciones de red locales.
  for (const name of ['RTCPeerConnection','webkitRTCPeerConnection','mozRTCPeerConnection']) {
    try { Object.defineProperty(globalThis, name, { value: undefined, writable: false, configurable: false }); } catch (_) {}
  }
})();
