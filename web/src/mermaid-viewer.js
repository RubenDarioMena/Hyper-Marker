// Visor local de diagramas Mermaid de Hyper-Marker. Sin red: Mermaid y la fuente viajan en el APK.
// Android llama a funciones acotadas; las respuestas salen por MermaidBridge (origen exacto, ver MermaidScreen.kt).
import latin from '@fontsource/recursive/files/recursive-latin-400-normal.woff2';
import latinExt from '@fontsource/recursive/files/recursive-latin-ext-400-normal.woff2';

const FAMILY = 'Recursive Variable';
const FONT_CSS =
  `@font-face{font-family:"${FAMILY}";font-style:normal;font-weight:400;font-display:block;src:url(${latin}) format("woff2");` +
  'unicode-range:U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,U+2000-206F,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD}' +
  `@font-face{font-family:"${FAMILY}";font-style:normal;font-weight:400;font-display:block;src:url(${latinExt}) format("woff2");` +
  'unicode-range:U+0100-02BA,U+02BD-02C5,U+02C7-02CC,U+02CE-02D7,U+02DD-02FF,U+0304,U+0308,U+0329,U+1D00-1DBF,U+1E00-1E9F,U+1EF2-1EFF,U+2020,U+20A0-20AB,U+20AD-20C0,U+2113,U+2C60-2C7F,U+A720-A7FF}';
export const MAX_SOURCE = 300_000;
const MAX_SIDE = 8192;
const MAX_PIXELS = 40_000_000;
const CHUNK = 400_000;
const DIAGRAM_ID = 'hm-diagram';

const style = document.createElement('style');
style.textContent = FONT_CSS;
document.head.appendChild(style);

const mermaid = window.mermaid;
mermaid.initialize({
  startOnLoad: false, securityLevel: 'strict', theme: 'redux-color', look: 'neo',
  fontFamily: `"${FAMILY}",arial,sans-serif`, maxTextSize: MAX_SOURCE, maxEdges: 4000,
  suppressErrorRendering: true, deterministicIds: true,
  // Etiquetas SVG (sin foreignObject): el PNG puede rasterizarse sin contaminar el canvas y el SVG se abre en más editores.
  htmlLabels: false, flowchart: { htmlLabels: false, wrappingWidth: 180 },
});

let session = null;
let rendered = null; // {source, svg, type, width, height}
let queue = Promise.resolve();

function post(type, payload, extra = {}) {
  const bridge = window.MermaidBridge;
  if (bridge && session) bridge.postMessage(JSON.stringify({ sessionId: session, type, payload, ...extra }));
}

function serial(task) {
  const next = queue.then(task, task);
  queue = next.catch(() => {});
  return next;
}

function message(error) {
  const text = String(error?.message || error?.str || error || 'Error desconocido');
  return text.length > 2000 ? text.slice(0, 2000) + '…' : text;
}

async function renderNow(source) {
  if (typeof source !== 'string') throw new Error('Código inválido');
  if (source.length > MAX_SOURCE) throw new Error(`El diagrama supera ${MAX_SOURCE} caracteres`);
  if (rendered && rendered.source === source) return rendered;
  await document.fonts.load(`16px "${FAMILY}"`).catch(() => {});
  const parsed = await mermaid.parse(source);
  const { svg } = await mermaid.render(DIAGRAM_ID, source);
  const host = document.getElementById('diagram');
  host.innerHTML = svg;
  const element = host.querySelector('svg');
  const box = element.viewBox.baseVal;
  const width = box && box.width ? box.width : element.getBBox().width;
  const height = box && box.height ? box.height : element.getBBox().height;
  element.removeAttribute('style');
  element.setAttribute('width', String(width));
  element.setAttribute('height', String(height));
  element.style.width = width + 'px';
  rendered = { source, type: parsed?.diagramType || 'unknown', width, height };
  return rendered;
}

/** Dibuja el código. Un error deja visible el último diagrama válido, atenuado, con el mensaje encima. */
window.renderDiagram = (source, sessionId) => {
  if (sessionId) session = sessionId;
  return serial(async () => {
    try {
      const value = await renderNow(source);
      document.body.classList.remove('failed', 'stale');
      post('rendered', { ok: true, diagramType: value.type, width: value.width, height: value.height });
      return true;
    } catch (error) {
      document.getElementById('error').textContent = message(error);
      document.body.classList.add('failed');
      document.body.classList.toggle('stale', !!rendered);
      post('rendered', { ok: false, error: message(error) });
      return false;
    }
  });
};

function svgElement() {
  const element = document.querySelector('#diagram svg');
  if (!element) throw new Error('Todavía no hay un diagrama dibujado');
  return element;
}

/** Copia autónoma del SVG: tamaño explícito, fondo blanco y fuente incrustada para verse igual fuera de la app. */
export function standaloneSvg() {
  const element = svgElement();
  const copy = element.cloneNode(true);
  const box = element.viewBox.baseVal;
  copy.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
  copy.setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink');
  copy.setAttribute('width', String(rendered.width));
  copy.setAttribute('height', String(rendered.height));
  copy.removeAttribute('style');
  const ns = 'http://www.w3.org/2000/svg';
  const fonts = document.createElementNS(ns, 'style');
  fonts.textContent = FONT_CSS;
  const background = document.createElementNS(ns, 'rect');
  background.setAttribute('x', String(box.x)); background.setAttribute('y', String(box.y));
  background.setAttribute('width', String(box.width)); background.setAttribute('height', String(box.height));
  background.setAttribute('fill', '#ffffff');
  copy.insertBefore(background, copy.firstChild);
  copy.insertBefore(fonts, copy.firstChild);
  // Las animaciones de aristas no tienen sentido en una imagen fija.
  copy.querySelectorAll('[class*="edge-animation"]').forEach((node) => node.classList.remove('edge-animation-slow', 'edge-animation-fast'));
  return '<?xml version="1.0" encoding="UTF-8"?>\n' + new XMLSerializer().serializeToString(copy);
}

/** Escala real para un PNG: la pedida, reducida si superaría el lado o los píxeles máximos. */
export function pngSize(width, height, requested) {
  let scale = Math.max(1, Math.min(8, Number(requested) || 1));
  scale = Math.min(scale, MAX_SIDE / width, MAX_SIDE / height, Math.sqrt(MAX_PIXELS / (width * height)));
  return { scale, width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) };
}

async function png(scale) {
  const text = standaloneSvg();
  const size = pngSize(rendered.width, rendered.height, scale);
  const url = URL.createObjectURL(new Blob([text], { type: 'image/svg+xml' }));
  try {
    const image = new Image();
    image.decoding = 'sync';
    await new Promise((done, fail) => { image.onload = done; image.onerror = () => fail(new Error('No se pudo rasterizar el SVG')); image.src = url; });
    const canvas = document.createElement('canvas');
    canvas.width = size.width; canvas.height = size.height;
    const context = canvas.getContext('2d');
    context.fillStyle = '#ffffff'; context.fillRect(0, 0, size.width, size.height);
    context.drawImage(image, 0, 0, size.width, size.height);
    const blob = await new Promise((done, fail) => canvas.toBlob((value) => value ? done(value) : fail(new Error('El PNG es demasiado grande para este dispositivo')), 'image/png'));
    const bytes = new Uint8Array(await blob.arrayBuffer());
    let binary = '';
    for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
    canvas.width = 0; canvas.height = 0;
    return { data: btoa(binary), width: size.width, height: size.height };
  } finally { URL.revokeObjectURL(url); }
}

function sendChunks(requestId, kind, data, meta) {
  const total = Math.max(1, Math.ceil(data.length / CHUNK));
  for (let index = 0; index < total; index++)
    post('export', { kind, index, total, data: data.slice(index * CHUNK, (index + 1) * CHUNK), ...(index === total - 1 ? meta : {}) }, { requestId });
}

/** Exporta el diagrama de `source` como SVG o PNG (base64) en fragmentos. */
window.exportDiagram = (source, requestId, kind, scale) => serial(async () => {
  try {
    await renderNow(source);
    if (kind === 'svg') { sendChunks(requestId, 'svg', standaloneSvg(), { width: rendered.width, height: rendered.height }); return; }
    if (kind !== 'png') throw new Error('Formato no admitido');
    const value = await png(scale);
    sendChunks(requestId, 'png', value.data, { width: value.width, height: value.height });
  } catch (error) { post('failed', { message: message(error) }, { requestId }); }
});

const LABEL_LIMIT = 4000;
const clip = (value) => String(value ?? '').slice(0, LABEL_LIMIT);

function centers(prefix, selector) {
  const element = svgElement();
  const box = element.viewBox.baseVal;
  const frame = element.getBoundingClientRect();
  const unit = box.width / frame.width;
  const result = new Map();
  element.querySelectorAll(selector).forEach((node) => {
    const id = node.id || '';
    if (!id.startsWith(prefix)) return;
    const rect = node.getBoundingClientRect();
    result.set(id.slice(prefix.length), {
      x: box.x + (rect.left - frame.left + rect.width / 2) * unit, y: box.y + (rect.top - frame.top + rect.height / 2) * unit,
      width: rect.width * unit, height: rect.height * unit, node,
    });
  });
  return result;
}

function color(node) {
  const shape = node?.querySelector('rect, path, polygon');
  if (!shape) return null;
  const match = getComputedStyle(shape).stroke.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);
  return match ? '#' + match.slice(1, 4).map((v) => Number(v).toString(16).padStart(2, '0')).join('') : null;
}

/**
 * Estructura de un flowchart: puntos con etiqueta y centro del layout, aristas en orden de origen y subgrafos.
 * Usa el parser de Mermaid (no una gramática propia) para aceptar la misma sintaxis que se dibuja.
 */
export async function graph(source) {
  await renderNow(source);
  const diagram = await mermaid.mermaidAPI.getDiagramFromText(source);
  if (!String(diagram.type).startsWith('flowchart')) return { type: diagram.type, supported: false };
  const db = diagram.db;
  const vertices = db.getVertices();
  const entries = vertices instanceof Map ? [...vertices.values()] : Object.values(vertices);
  const placed = new Map();
  centers(DIAGRAM_ID + '-flowchart-', 'g.node').forEach((value, key) => placed.set(key.replace(/-\d+$/, ''), value));
  const clusters = centers(DIAGRAM_ID + '-', 'g.cluster');
  const nodes = entries.map((vertex) => {
    const at = placed.get(vertex.id);
    return { id: clip(vertex.id), label: clip(vertex.text ?? vertex.id), shape: vertex.type || 'rect',
      x: at?.x ?? null, y: at?.y ?? null, width: at?.width ?? null, height: at?.height ?? null };
  });
  const edges = db.getEdges().map((edge) => ({ from: clip(edge.start), to: clip(edge.end), label: clip(edge.text), stroke: edge.stroke || 'normal' }));
  const groups = db.getSubGraphs().map((group) => {
    const at = clusters.get(group.id);
    return { id: clip(group.id), title: clip(group.title || group.id), nodes: (group.nodes || []).map(clip),
      x: at?.x ?? null, y: at?.y ?? null, width: at?.width ?? null, height: at?.height ?? null, color: color(at?.node) };
  });
  return { type: diagram.type, supported: true, direction: db.getDirection?.() || 'TB', nodes, edges, groups };
}

window.requestGraph = (source, requestId) => serial(async () => {
  try {
    const value = JSON.stringify(await graph(source));
    sendChunks(requestId, 'graph', value, {});
  } catch (error) { post('failed', { message: message(error) }, { requestId }); }
});

window.mermaidViewer = { graph, standaloneSvg, pngSize, png, renderNow };
