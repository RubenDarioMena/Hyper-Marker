import {test} from 'node:test';
import assert from 'node:assert/strict';
import {chromium} from '@playwright/test';
import {createServer} from 'node:http';
import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {resolve, extname} from 'node:path';

const root = resolve('../app/src/main/assets/mermaid');
const sample = await readFile(resolve('../fixtures/mermaid/generador-gravitacional.mmd'), 'utf8');
const types = {'.js': 'application/javascript', '.html': 'text/html'};

async function open(run) {
  const server = createServer(async (req, res) => {
    try {
      const file = resolve(root, '.' + new URL(req.url, 'http://x').pathname);
      if (!file.startsWith(root + '/')) throw Error();
      const body = await readFile(file);
      res.setHeader('Content-Type', types[extname(file)] || 'text/plain'); res.end(body);
    } catch { res.statusCode = 404; res.end(); }
  });
  await new Promise((done) => server.listen(0, '127.0.0.1', done));
  const origin = 'http://127.0.0.1:' + server.address().port;
  const browser = await chromium.launch({executablePath: process.env.CHROME_PATH || (process.platform === 'darwin' ? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' : chromium.executablePath()), headless: true});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}});
    const remote = [], problems = [];
    page.on('request', (request) => { const url = request.url(); if (!url.startsWith(origin) && !url.startsWith('data:') && !url.startsWith('blob:')) remote.push(url); });
    page.on('console', (entry) => { if (entry.type() === 'error') problems.push(entry.text()); });
    page.on('pageerror', (error) => problems.push(String(error)));
    await page.addInitScript(() => { window.events = []; window.pwned = false; window.MermaidBridge = {postMessage: (value) => events.push(JSON.parse(value))}; });
    await page.goto(origin + '/index.html');
    await run(page, {remote, problems});
  } finally { await browser.close(); await new Promise((done) => server.close(done)); }
}

const wait = async (page, predicate, arg) => { for (let i = 0; i < 200; i++) { if (await page.evaluate(predicate, arg)) return; await new Promise((r) => setTimeout(r, 50)); } throw new Error('timeout'); };
const collect = (page, requestId) => page.evaluate((id) => {
  const parts = events.filter((e) => e.requestId === id && e.type === 'export');
  return {failed: events.find((e) => e.requestId === id && e.type === 'failed')?.payload, total: parts[0]?.payload.total, data: parts.map((e) => e.payload.data).join(''), last: parts.at(-1)?.payload};
}, requestId);

test('renders the reference flowchart offline with the neo look and the bundled font, under the page CSP', async () => {
  await open(async (page, {remote, problems}) => {
    assert.equal(await page.evaluate((code) => renderDiagram(code, 'test'), sample), true);
    const rendered = await page.evaluate(() => events.find((e) => e.type === 'rendered').payload);
    assert.equal(rendered.ok, true); assert.equal(rendered.diagramType, 'flowchart-v2');
    assert.ok(rendered.width > 600 && rendered.height > 1000, JSON.stringify(rendered));
    assert.equal(await page.locator('#diagram g.node').count(), 21);
    assert.equal(await page.locator('#diagram g.cluster[data-look="neo"]').count(), 2);
    assert.ok(await page.evaluate(() => document.fonts.check('16px "Recursive Variable"')));
    const words = await page.evaluate(() => [...document.querySelectorAll('#diagram g.node tspan')].map((t) => t.textContent.trim()).join(' ').replace(/\s+/g, ' '));
    assert.match(words, /CAPTURA DE AGUJERO NEGRO/);
    await mkdir('test-results', {recursive: true});
    await page.screenshot({path: 'test-results/mermaid-phone.png', fullPage: true});
    assert.deepEqual(remote, []); assert.deepEqual(problems, []);
  });
});

test('extracts nodes, edges, subgraphs and layout centres for the Flow conversion', async () => {
  await open(async (page) => {
    await page.evaluate((code) => { renderDiagram(code, 'test'); return requestGraph(code, 'g1'); }, sample);
    const graph = JSON.parse((await collect(page, 'g1')).data);
    assert.equal(graph.supported, true); assert.equal(graph.direction, 'TB');
    assert.equal(graph.nodes.length, 21); assert.equal(graph.edges.length, 25); assert.equal(graph.groups.length, 2);
    const a1 = graph.nodes.find((n) => n.id === 'A1');
    assert.match(a1.label, /^Transmutación nuclear<br\/?>Producir elementos escasos$/);
    assert.ok(Number.isFinite(a1.x) && Number.isFinite(a1.y));
    const e = graph.nodes.find((n) => n.id === 'E'), u = graph.nodes.find((n) => n.id === 'U');
    assert.ok(e.y < a1.y && a1.y < u.y, 'el layout TB conserva el orden vertical');
    assert.deepEqual(graph.edges.slice(0, 3).map((x) => x.from + '>' + x.to), ['A1>A2', 'A2>A3', 'A3>A4']);
    const branchA = graph.groups.find((g) => g.id === 'A');
    assert.equal(branchA.title, 'RAMA A · MATERIA EXTREMA'); assert.deepEqual([...branchA.nodes].sort(), ['A1', 'A2', 'A3', 'A4']);
    assert.match(branchA.color, /^#[0-9a-f]{6}$/);
    assert.notEqual(branchA.color, graph.groups.find((g) => g.id === 'B').color);
  });
});

test('exports a standalone SVG with embedded font and a high-resolution PNG', async () => {
  await open(async (page, {remote}) => {
    await page.evaluate((code) => renderDiagram(code, 'test'), sample);
    await page.evaluate((code) => exportDiagram(code, 's1', 'svg'), sample);
    const svg = await collect(page, 's1');
    assert.equal(svg.failed, undefined);
    assert.match(svg.data, /^<\?xml/); assert.match(svg.data, /@font-face\{font-family:"Recursive Variable"/); assert.match(svg.data, /data:font\/woff2;base64,/);
    assert.match(svg.data, /<rect[^>]*fill="#ffffff"/);
    assert.doesNotMatch(svg.data, /<foreignObject/);
    await page.evaluate((code) => exportDiagram(code, 'p1', 'png', 3), sample);
    const png = await collect(page, 'p1');
    assert.equal(png.failed, undefined); assert.ok(png.total > 1, 'el PNG viaja en varios fragmentos');
    const bytes = Buffer.from(png.data, 'base64');
    assert.equal(bytes.subarray(1, 4).toString(), 'PNG');
    const width = bytes.readUInt32BE(16), height = bytes.readUInt32BE(20);
    assert.equal(width, png.last.width); assert.equal(height, png.last.height);
    const size = await page.evaluate(() => events.find((e) => e.type === 'rendered').payload);
    assert.equal(width, Math.round(size.width * 3)); assert.equal(height, Math.round(size.height * 3));
    await writeFile('test-results/mermaid-export.png', bytes);
    // Una imagen gigantesca se reduce proporcionalmente en vez de fallar.
    const capped = await page.evaluate(() => mermaidViewer.pngSize(4000, 3000, 8));
    assert.ok(capped.width <= 8192 && capped.height <= 8192 && capped.width * capped.height <= 40_000_000);
    assert.ok(Math.abs(capped.width / capped.height - 4 / 3) < 0.01);
    assert.deepEqual(remote, []);
  });
});

test('invalid code reports the parser error and keeps the last valid diagram', async () => {
  await open(async (page) => {
    await page.evaluate((code) => renderDiagram(code, 'test'), 'flowchart LR\n  A --> B');
    assert.equal(await page.evaluate(() => renderDiagram('flowchart LR\n  A --> (', 'test')), false);
    const failure = await page.evaluate(() => events.filter((e) => e.type === 'rendered').at(-1).payload);
    assert.equal(failure.ok, false); assert.ok(failure.error.length > 5);
    assert.equal(await page.locator('#diagram g.node').count(), 2);
    assert.ok(await page.evaluate(() => document.body.classList.contains('stale')));
    await page.evaluate(() => requestGraph('sequenceDiagram\n  Ana->>Luis: Hola', 'g2'));
    assert.equal(JSON.parse((await collect(page, 'g2')).data).supported, false);
  });
});

test('hostile labels, click directives and init directives cannot run script or load remote content', async () => {
  await open(async (page, {remote}) => {
    const hostile = [
      '%%{init: {"securityLevel": "loose", "startOnLoad": true}}%%',
      'flowchart LR',
      '  A["<img src=x onerror=window.pwned=true>"] --> B["<script>window.pwned=true</script>"]',
      '  B --> C["<a href=\\"javascript:window.pwned=true\\">enlace</a>"]',
      '  C --> D["<img src=https://example.com/pixel.png>"]',
      '  click A call pwn()',
      '  click B href "javascript:window.pwned=true"',
    ].join('\n');
    await page.evaluate(() => { window.pwn = () => { window.pwned = true; }; });
    await page.evaluate((code) => renderDiagram(code, 'test'), hostile);
    for (const node of await page.locator('#diagram g.node').all()) await node.click({force: true}).catch(() => {});
    await page.waitForTimeout(200);
    assert.equal(await page.evaluate(() => window.pwned), false);
    assert.equal(await page.locator('#diagram script').count(), 0);
    assert.deepEqual(remote, []);
  });
});
