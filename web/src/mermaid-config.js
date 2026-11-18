// Configuración Mermaid compartida por el visor de diagramas y la vista previa de bloques ```mermaid en Markdown.
import latin from '@fontsource/recursive/files/recursive-latin-400-normal.woff2';
import latinExt from '@fontsource/recursive/files/recursive-latin-ext-400-normal.woff2';

export const FAMILY = 'Recursive Variable';
export const FONT_CSS =
  `@font-face{font-family:"${FAMILY}";font-style:normal;font-weight:400;font-display:block;src:url(${latin}) format("woff2");` +
  'unicode-range:U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,U+2000-206F,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD}' +
  `@font-face{font-family:"${FAMILY}";font-style:normal;font-weight:400;font-display:block;src:url(${latinExt}) format("woff2");` +
  'unicode-range:U+0100-02BA,U+02BD-02C5,U+02C7-02CC,U+02CE-02D7,U+02DD-02FF,U+0304,U+0308,U+0329,U+1D00-1DBF,U+1E00-1E9F,U+1EF2-1EFF,U+2020,U+20A0-20AB,U+20AD-20C0,U+2113,U+2C60-2C7F,U+A720-A7FF}';
export const MAX_SOURCE = 300_000;

export const CONFIG = {
  startOnLoad: false, securityLevel: 'strict', theme: 'redux-color', look: 'neo',
  fontFamily: `"${FAMILY}",arial,sans-serif`, maxTextSize: MAX_SOURCE, maxEdges: 4000,
  suppressErrorRendering: true, deterministicIds: true,
  // Etiquetas SVG (sin foreignObject): el PNG puede rasterizarse sin contaminar el canvas y el SVG se abre en más editores.
  htmlLabels: false, flowchart: { htmlLabels: false, wrappingWidth: 180 },
};

/** Inserta la fuente de los diagramas en el documento actual (una vez). */
export function installFont() {
  if (document.getElementById('hm-mermaid-font')) return;
  const style = document.createElement('style');
  style.id = 'hm-mermaid-font';
  style.textContent = FONT_CSS;
  document.head.appendChild(style);
}

/**
 * Ajustes tras dibujar para igualar las referencias con etiquetas HTML. Con etiquetas SVG, Mermaid reserva el ancho mínimo de un estado para su etiqueta pero
 * dibuja el texto desde la izquierda: se recentra cada etiqueta en su caja (como en las referencias con etiquetas HTML).
 * Necesita el SVG insertado en el documento para medir.
 */
export function polish(svg) {
  // Fondo de las etiquetas de flecha: gris sólido como con etiquetas HTML (con SVG queda blanco al 50 % en estados).
  svg.querySelectorAll('g.edgeLabel rect.background').forEach((rect) => {
    if (Number(rect.getAttribute('width')) > 0) rect.setAttribute('style', 'fill:#cccccc;opacity:1;stroke:none');
  });
  svg.querySelectorAll('g.node.statediagram-state').forEach((node) => {
    const shape = node.querySelector(':scope > rect, :scope > path');
    const label = node.querySelector(':scope > g.label');
    const move = /translate\(\s*([-\d.e]+)[ ,]+([-\d.e]+)\s*\)/.exec(label?.getAttribute('transform') || '');
    if (!shape || !label || !move) return;
    const outer = shape.getBBox(), inner = label.getBBox();
    if (!outer.width || !inner.width) return;
    const x = Number(move[1]) + (outer.x + outer.width / 2) - (Number(move[1]) + inner.x + inner.width / 2);
    label.setAttribute('transform', `translate(${x}, ${move[2]})`);
  });
}

/** Mismo ajuste para un SVG en texto (vista previa de Markdown): se mide fuera de pantalla y se devuelve serializado. */
export function polishText(svgText) {
  const host = document.createElement('div');
  host.style.cssText = 'position:absolute;left:-100000px;top:0;visibility:hidden';
  host.innerHTML = svgText;
  document.body.appendChild(host);
  try { const svg = host.querySelector('svg'); if (svg) polish(svg); return host.innerHTML; } finally { host.remove(); }
}
