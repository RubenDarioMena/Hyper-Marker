(() => {
  'use strict';
  // CSP cubre HTTP/Fetch/WebSocket; estas APIs requieren una defensa adicional.
  for (const name of ['RTCPeerConnection','webkitRTCPeerConnection','mozRTCPeerConnection','WebTransport','Worker','SharedWorker']) {
    try { Object.defineProperty(globalThis, name, { value: undefined, writable: false, configurable: false }); } catch (_) {}
  }
})();
