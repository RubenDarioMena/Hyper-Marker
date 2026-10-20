import { Crepe } from '@milkdown/crepe';
import '@milkdown/crepe/theme/common/style.css';
import '@milkdown/crepe/theme/frame.css';
let crepe;
let session;
const send = (type, payload) => window.MarkdownBridge?.postMessage(JSON.stringify({type, sessionId:session, payload}));
window.startEditor = async (source, sessionId) => {
  session = sessionId;
  try {
    if (crepe) await crepe.destroy();
    crepe = new Crepe({root:document.getElementById('editor'), defaultValue:source,
      features:{[Crepe.Feature.ImageBlock]:false, [Crepe.Feature.Latex]:false, [Crepe.Feature.Table]:false}});
    crepe.on(listener => listener.markdownUpdated((_ctx, markdown) => send('change', {source:markdown})));
    await crepe.create();
    window.editorMarkdown = () => crepe.getMarkdown();
    send('loaded', {});
  } catch(error) { document.getElementById('error').textContent=String(error);send('error',{message:String(error)}); }
};
send('ready', {});
