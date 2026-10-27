import { Crepe } from '@milkdown/crepe';
import { editorViewCtx } from '@milkdown/kit/core';
import { callCommand } from '@milkdown/kit/utils';
import { lift } from '@milkdown/kit/prose/commands';
import { createCodeBlockCommand, insertHrCommand, liftListItemCommand, toggleEmphasisCommand, toggleInlineCodeCommand, toggleStrongCommand,
  turnIntoTextCommand, wrapInBlockquoteCommand, wrapInBulletListCommand, wrapInHeadingCommand, wrapInOrderedListCommand } from '@milkdown/kit/preset/commonmark';
import { insertTableCommand, toggleStrikethroughCommand } from '@milkdown/kit/preset/gfm';
import '@milkdown/crepe/theme/common/style.css';
import '@milkdown/crepe/theme/frame.css';
let crepe;
let session;
const send = (type, payload) => window.MarkdownBridge?.postMessage(JSON.stringify({type, sessionId:session, payload}));
window.startEditor = async (source, sessionId, readOnly = false) => {
  session = sessionId;
  try {
    if (crepe) await crepe.destroy();
    // La barra flotante se sustituye por la barra nativa superior, que actúa sobre la misma selección.
    crepe = new Crepe({root:document.getElementById('editor'), defaultValue:source,
      features:{[Crepe.Feature.ImageBlock]:false, [Crepe.Feature.Latex]:false, [Crepe.Feature.Toolbar]:false, [Crepe.Feature.TopBar]:false, [Crepe.Feature.AI]:false}});
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
