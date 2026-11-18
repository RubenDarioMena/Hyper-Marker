import {test} from 'node:test';
import assert from 'node:assert/strict';
import {chromium} from '@playwright/test';
import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import {resolve, extname} from 'node:path';

// Sirve la raíz de assets como el AssetLoader del editor: /editor/… y /mermaid/… del mismo origen.
const root = resolve('../app/src/main/assets');
const types = {'.js': 'application/javascript', '.css': 'text/css', '.html': 'text/html'};

async function editor(run) {
  const server = createServer(async (req, res) => {
    try {
      const file = resolve(root, '.' + new URL(req.url, 'http://x').pathname);
      if (!file.startsWith(root + '/')) throw Error();
      res.setHeader('Content-Type', types[extname(file)] || 'text/plain'); res.end(await readFile(file));
    } catch { res.statusCode = 404; res.end(); }
  });
  await new Promise((done) => server.listen(0, '127.0.0.1', done));
  const origin = 'http://127.0.0.1:' + server.address().port;
  const browser = await chromium.launch({executablePath: process.env.CHROME_PATH || (process.platform === 'darwin' ? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' : chromium.executablePath()), headless: true});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}});
    const requests = [];
    page.on('request', (request) => requests.push(request.url()));
    await page.addInitScript(() => { window.events = []; window.pwned = false; window.MarkdownBridge = {postMessage: (value) => events.push(JSON.parse(value))}; });
    await page.goto(origin + '/editor/index.html');
    await run(page, {origin, requests});
  } finally { await browser.close(); await new Promise((done) => server.close(done)); }
}
const loaded = (page) => page.waitForFunction(() => events.some((e) => e.type === 'loaded'));

test('mermaid code blocks render as diagrams in Visual and can be opened as a diagram document', async () => {
  const note = '# Plan\n\nTexto antes.\n\n```mermaid\nflowchart LR\n  idea["Idea"] --> plan["Plan"]\n  plan --> hecho["Hecho"]\n```\n\nTexto después.\n';
  await editor(async (page, {origin, requests}) => {
    await page.evaluate((source) => startEditor(source, 'test'), note); await loaded(page);
    await page.locator('.hm-mermaid svg g.node').first().waitFor({timeout: 20_000});
    assert.equal(await page.locator('.hm-mermaid svg g.node').count(), 3);
    // El <style> interno de Mermaid sobrevive al saneado: las cajas tienen el trazo del tema neo.
    assert.notEqual(await page.evaluate(() => getComputedStyle(document.querySelector('.hm-mermaid g.node rect, .hm-mermaid g.node path')).stroke), 'none');
    await page.locator('.hm-mermaid-open').click();
    const open = await page.evaluate(() => events.find((e) => e.type === 'mermaid.open'));
    assert.equal(open?.payload.source, 'flowchart LR\n  idea["Idea"] --> plan["Plan"]\n  plan --> hecho["Hecho"]');
    assert.match(await page.evaluate(() => editorMarkdown()), /```mermaid\nflowchart LR\n {2}idea\["Idea"\] --> plan\["Plan"\]/);
    assert.deepEqual(requests.filter((url) => !url.startsWith(origin) && !url.startsWith('data:')), []);
    assert.ok(requests.some((url) => url.endsWith('/mermaid/mermaid.min.js')));
  });
});

test('notes without mermaid blocks never load Mermaid; errors and hostile labels stay inert; read-only hides the button', async () => {
  await editor(async (page, {requests}) => {
    await page.evaluate(() => startEditor('# Nota\n\n```js\nconsole.log(1)\n```\n', 'test')); await loaded(page);
    await page.waitForTimeout(300);
    assert.equal(requests.some((url) => url.includes('/mermaid/')), false);
    const hostile = '```mermaid\nflowchart LR\n  A["<img src=x onerror=window.pwned=true>"] --> B\n  click A call pwn()\n```\n\n```mermaid\nflowchart LR\n  A --> (\n```\n';
    await page.evaluate(() => { window.pwn = () => { window.pwned = true; }; });
    await page.evaluate((source) => startEditor(source, 'test', true), hostile); await page.waitForFunction(() => events.filter((e) => e.type === 'loaded').length === 2);
    await page.locator('.hm-mermaid svg').first().waitFor({timeout: 20_000});
    await page.locator('.hm-mermaid-error').first().waitFor({timeout: 20_000});
    for (const node of await page.locator('.hm-mermaid g.node').all()) await node.click({force: true}).catch(() => {});
    assert.equal(await page.evaluate(() => window.pwned), false);
    assert.equal(await page.locator('.hm-mermaid-open').count(), 0);
  });
});
