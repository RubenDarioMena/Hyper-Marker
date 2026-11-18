import { Crepe } from '@milkdown/crepe';
import { editorViewCtx } from '@milkdown/kit/core';
import { callCommand } from '@milkdown/kit/utils';
import { lift } from '@milkdown/kit/prose/commands';
import { createCodeBlockCommand, insertHrCommand, liftListItemCommand, toggleEmphasisCommand, toggleInlineCodeCommand, toggleStrongCommand,
  turnIntoTextCommand, wrapInBlockquoteCommand, wrapInBulletListCommand, wrapInHeadingCommand, wrapInOrderedListCommand } from '@milkdown/kit/preset/commonmark';
import { insertTableCommand, toggleStrikethroughCommand } from '@milkdown/kit/preset/gfm';
import '@milkdown/crepe/theme/common/style.css';
import '@milkdown/crepe/theme/frame.css';
import { CONFIG as MERMAID_CONFIG, installFont, polishText } from './mermaid-config.js';
let crepe;
let readOnlyMode = false;
let session;
const MERMAID_LANGUAGE = /^(mermaid|mmd)$/i;
let mermaidReady = null;
let mermaidRenders = 0;
const diagrams = new Map();
/** Carga Mermaid (5,5 MB) sólo cuando la nota contiene un bloque ```mermaid. Mismo asset y configuración que el visor. */
function loadMermaid() {
  if (!mermaidReady) mermaidReady = new Promise((done, fail) => {
    const script = document.createElement('script');
    script.src = '../mermaid/mermaid.min.js';
    script.onload = () => { installFont(); window.mermaid.initialize(MERMAID_CONFIG); done(window.mermaid); };
    script.onerror = () => { mermaidReady = null; fail(new Error('No se pudo cargar el visor de diagramas')); };
    document.head.appendChild(script);
  });
  return mermaidReady;
}
function mermaidFailure(error) {
  const box = document.createElement('div');
  box.className = 'hm-mermaid-error';
  box.textContent = 'El diagrama tiene errores: ' + String(error?.message || error || '').slice(0, 600);
  return box;
}
/** Vista previa asíncrona: Milkdown sanea el resultado (DOMPurify) antes de insertarlo. */
function mermaidPreview(content, apply) {
  const source = String(content || '').trim();
  if (!source) return null;
  loadMermaid().then(async (mermaid) => {
    const id = 'hm-md-' + (++mermaidRenders);
    try {
      const { svg } = await mermaid.render(id, source);
      const key = String(mermaidRenders);
      diagrams.set(key, source);
      if (diagrams.size > 200) diagrams.delete(diagrams.keys().next().value);
      apply('<div class="hm-mermaid">' + polishText(svg) + '</div>' + (readOnlyMode ? '' :
        '<button type="button" class="hm-mermaid-open" data-hm-diagram="' + key + '">Abrir como diagrama</button>'));
    } catch (error) {
      document.getElementById(id)?.remove(); document.getElementById('d' + id)?.remove();
      apply(mermaidFailure(error));
    }
  }, (error) => apply(mermaidFailure(error)));
  return undefined;
}
// La vista previa se inserta como HTML saneado (sin manejadores): el botón se atiende por delegación.
document.addEventListener('click', (event) => {
  const button = event.target?.closest?.('[data-hm-diagram]');
  if (!button) return;
  event.preventDefault(); event.stopPropagation();
  const source = diagrams.get(button.dataset.hmDiagram);
  if (source && !readOnlyMode) send('mermaid.open', {source});
}, true);
const send = (type, payload) => window.MarkdownBridge?.postMessage(JSON.stringify({type, sessionId:session, payload}));
window.startEditor = async (source, sessionId, readOnly = false) => {
  session = sessionId;
  try {
    if (crepe) await crepe.destroy();
    // La barra flotante se sustituye por la barra nativa superior, que actúa sobre la misma selección.
    crepe = new Crepe({root:document.getElementById('editor'), defaultValue:source,
      features:{[Crepe.Feature.ImageBlock]:false, [Crepe.Feature.Latex]:false, [Crepe.Feature.Toolbar]:false, [Crepe.Feature.TopBar]:false, [Crepe.Feature.AI]:false},
      featureConfigs:{[Crepe.Feature.CodeMirror]:{
        // Los bloques ```mermaid se dibujan bajo el código; el resto de bloques no tiene vista previa.
        renderPreview:(language, content, apply) => MERMAID_LANGUAGE.test(language || '') ? mermaidPreview(content, apply) : null,
        previewOnlyByDefault:true, previewLabel:'Diagrama', previewLoading:'Dibujando diagrama…',
        previewToggleText:(previewOnly) => previewOnly ? 'Editar código' : 'Solo diagrama'}}});
    readOnlyMode = readOnly;
    crepe.setReadonly(readOnly);
    crepe.on(listener => listener.markdownUpdated((_ctx, markdown) => send('change', {source:markdown})));
    await crepe.create();
    window.editorMarkdown = () => crepe.getMarkdown();
    send('loaded', {});
  } catch(error) { document.getElementById('error').textContent=String(error);send('error',{message:String(error)}); }
};
const scrollFraction = () => {
  const root = document.scrollingElement || document.documentElement;
  const range = Math.max(0, root.scrollHeight - root.clientHeight);
  return range ? Math.min(1, Math.max(0, root.scrollTop / range)) : 0;
};
window.editorSnapshot = () => JSON.stringify({source:crepe?.getMarkdown() ?? '', fraction:scrollFraction()});
window.editorSetScrollFraction = fraction => {
  const value = Math.min(1, Math.max(0, Number(fraction) || 0));
  requestAnimationFrame(() => requestAnimationFrame(() => {
    const root = document.scrollingElement || document.documentElement;
    root.scrollTop = value * Math.max(0, root.scrollHeight - root.clientHeight);
  }));
};
const depth = (state, name) => { const {$from} = state.selection; for (let d = $from.depth; d > 0; d--) if ($from.node(d).type.name === name) return d; return -1; };
const heading = state => { const {$from} = state.selection; return $from.parent.type.name === 'heading' ? $from.parent.attrs.level : 0; };
function toggleList(ctx, view, name, wrap) {
  const other = name === 'bullet_list' ? 'ordered_list' : 'bullet_list';
  const inside = depth(view.state, name) >= 0;
  if (inside || depth(view.state, other) >= 0) callCommand(liftListItemCommand.key)(ctx);
  return inside || callCommand(wrap.key)(ctx);
}
function link(view, href) {
  if (!/^(https?:|mailto:|#|\/|\.)/i.test(href)) return false;
  const {state} = view, {from, to, empty} = state.selection, mark = state.schema.marks.link.create({href});
  view.dispatch(empty ? state.tr.insertText('enlace', from).addMark(from, from + 6, mark) : state.tr.addMark(from, to, mark));
  return true;
}
/** Aplica un formato a la selección actual del editor; con selección vacía actúa en el cursor. */
window.editorCommand = (name, value) => {
  if (!crepe) return false;
  return crepe.editor.action(ctx => {
    const view = ctx.get(editorViewCtx), call = (command, payload) => callCommand(command.key, payload)(ctx);
    const done = ({
      bold: () => call(toggleStrongCommand), italic: () => call(toggleEmphasisCommand), strike: () => call(toggleStrikethroughCommand),
      code: () => call(toggleInlineCodeCommand),
      bullets: () => toggleList(ctx, view, 'bullet_list', wrapInBulletListCommand),
      numbered: () => toggleList(ctx, view, 'ordered_list', wrapInOrderedListCommand),
      quote: () => depth(view.state, 'blockquote') >= 0 ? lift(view.state, view.dispatch) : call(wrapInBlockquoteCommand),
      heading: () => !value || heading(view.state) === value ? call(turnIntoTextCommand) : call(wrapInHeadingCommand, value),
      codeblock: () => call(createCodeBlockCommand), table: () => call(insertTableCommand, {row:3, col:3}), rule: () => call(insertHrCommand),
      link: () => link(view, String(value || '')),
    })[name]?.() ?? false;
    view.focus();
    return done;
  });
};
window.setEditorStyle = ({dark, font}) => {
  document.documentElement.classList.toggle('dark', !!dark);
  document.documentElement.dataset.font = ['serif', 'mono'].includes(font) ? font : 'sans';
};
send('ready', {});
