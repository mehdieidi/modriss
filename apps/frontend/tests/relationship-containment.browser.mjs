// Run explicitly: node apps/frontend/tests/relationship-containment.browser.mjs
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { extname, resolve } from "node:path";
import { chromium } from "../../landing/node_modules/playwright/index.mjs";

const root = resolve("apps/frontend");
const harness = `
  import { state } from '/js/state.js';
  import { applyModelingRuntimeConfig } from '/js/config.js';
  import { initializeModelingRuntimeState } from '/js/modeling-config-data.js';
  import { installGraphAndViews, serializeGraphAndViewsInto } from '/js/graph-store.js';
  import { materializeActiveView } from '/js/view-materializer.js';
  import { openConnectionPanel } from '/js/attr-panel.js';
  import { setupDnD, renderDiagramAsync } from '/js/canvas.js';
  const notation = await (await fetch('/test-cvs.json')).json();
  const ref = (name, targetType, many = true) => ({ name, targetType, containment: true, many });
  const visual = { icon: 'link', color: '#2563eb', category: 'Domain', creatable: true, notation: {} };
  const level = {
    apiType: 'cim', labelField: 'name', modelNameTemplate: 'Example',
    rootTemplate: { eClass: 'Model', modelLevel: 'CIM' },
    relationshipSemantics: { containmentKind: 'CONTAINS', containmentKinds: ['CONTAINS'] },
    relationshipKinds: ['DOMAIN_RELATIONSHIP', 'CONTAINS'],
    semanticEdgeObjectRules: [{ eClass: 'DomainRelationship', matchKinds: ['DOMAIN_RELATIONSHIP'], sourceFeature: 'source', targetFeature: 'target', rootFeature: 'relationships' }],
    containers: notation.containers,
    canvasPolicy: { ...notation.canvasPolicy, roleSizes: { node: { width: 120, height: 118 }, detail: { width: 228, height: 84 } } },
    workbench: { defaultViewDefinitionId: 'main' },
    viewDefinitions: [{ id: 'main', viewType: 'main', palette: ['DomainEntity'], canvas: ['DomainEntity'], relationshipKinds: ['DOMAIN_RELATIONSHIP'] }],
    elements: [
      { ...visual, type: 'Model', references: [ref('entities', 'DomainEntity'), ref('relationships', 'DomainRelationship')] },
      { ...visual, type: 'DomainEntity', visualRole: 'node' },
      { ...visual, type: 'DomainRelationship', visualRole: 'relationship', relationshipElement: true,
        references: [ref('sourceMultiplicity', 'Multiplicity', false), ref('targetMultiplicity', 'Multiplicity', false),
          { name: 'source', targetType: 'DomainEntity', many: false }, { name: 'target', targetType: 'DomainEntity', many: false }] },
      { ...visual, type: 'Multiplicity', visualRole: 'detail', containedOnly: true,
        attributes: [{ name: 'lower', fieldType: 'number', defaultValue: 0 }, { name: 'upper', fieldType: 'number', defaultValue: 1 }] },
    ],
  };
  const config = { version: 1, levelOrder: ['cim'], levels: { cim: level } };
  state.modelingConfig.config = config;
  initializeModelingRuntimeState(config);
  applyModelingRuntimeConfig(config);
  state.activeType = 'cim';
  state.tabs = { cim: { modelName: 'Example' } };
  state.project = { id: 'test' };
  state.baseModel = { id: 'model', eClass: 'Model', entities: [{ id: 'a', eClass: 'DomainEntity' }, { id: 'b', eClass: 'DomainEntity' }],
    relationships: [{ id: 'link', eClass: 'DomainRelationship', source: 'a', target: 'b', name: 'Domain relationship', sourceMultiplicity: [], targetMultiplicity: [] }] };
  const params = new URLSearchParams(location.search);
  if (params.has('filled')) {
    Object.assign(state.baseModel.relationships[0], {
      sourceMultiplicity: { id: 'source-m', eClass: 'Multiplicity', name: '1..1', lower: 1, upper: 1, x: 9000, y: -6000 },
      targetMultiplicity: { id: 'target-m', eClass: 'Multiplicity', name: 'One-to-One', lower: 1, upper: 1, x: 9300, y: -6000 },
    });
  }
  if (params.has('projected')) {
    state.baseModel.graph = { elements: state.baseModel.entities, relationships: [{ ...state.baseModel.relationships[0], kind: 'DOMAIN_RELATIONSHIP' }] };
    delete state.baseModel.relationships;
  }
  document.getElementById('projectOverlay').classList.add('hidden');
  installGraphAndViews('cim', state.baseModel, 'Example', { skipFragments: true, skipClientLayout: true });
  materializeActiveView();
  await renderDiagramAsync({ full: true });
  await import('/js/main.js');
  setupDnD();
  window.testState = state;
  window.openRelationship = () => openConnectionPanel('link');
  window.saveAndReload = () => {
    const contentsViewId = state.views.activeViewId;
    const saved = structuredClone(state.baseModel);
    serializeGraphAndViewsInto(saved);
    state.canvasFocusStack = [];
    installGraphAndViews('cim', saved, 'Example', { skipFragments: true, skipClientLayout: true, preserveActiveViewId: contentsViewId, preserveActiveView: true });
    materializeActiveView();
    state.baseModel = saved;
    return saved;
  };
  window.openRelationship();
  window.containmentReady = true;
`;

const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, "http://localhost");
    if (url.pathname === "/test-cvs.json") {
      response.setHeader("Content-Type", "application/json");
      response.end(await readFile(resolve("methodology/modeling-framework/notation/cim.cvs.json")));
      return;
    }
    if (url.pathname.startsWith("/api/")) {
      response.setHeader("Content-Type", "application/json");
      response.end("[]");
      return;
    }
    if (url.pathname === "/") {
      const html = (await readFile(resolve(root, "index.html"), "utf8")).replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, "");
      response.setHeader("Content-Type", "text/html");
      response.end(html.replace("</body>", `<script src="/vendor/antv/g6.min.js"></script><script type="module">${harness}</script></body>`));
      return;
    }
    const path = resolve(root, `.${url.pathname}`);
    if (!path.startsWith(root + "/") && !path.startsWith(root + "\\")) { response.writeHead(403).end(); return; }
    let content = await readFile(path);
    if (url.pathname === "/js/main.js") {
      const source = content.toString();
      const bootstrap = source.lastIndexOf("\ninit().catch(");
      assert.ok(bootstrap >= 0);
      content = source.slice(0, bootstrap) + "\nbindEvents();\n";
    }
    response.setHeader("Content-Type", ({ ".js": "text/javascript", ".css": "text/css", ".svg": "image/svg+xml" })[extname(path)] || "application/octet-stream");
    response.end(content);
  } catch { response.writeHead(404).end(); }
});
await new Promise((done) => server.listen(0, "127.0.0.1", done));
let browser;
try {
  browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto(`http://127.0.0.1:${server.address().port}`);
  await page.waitForFunction(() => window.containmentReady, null, { timeout: 15000 })
    .catch((error) => { throw new Error(error.message + "\n" + errors.join("\n")); });
  assert.equal(await page.locator("#attrPanelOpenBtn").isVisible(), true);
  await page.locator("#attrPanelOpenBtn").click();
  await page.waitForFunction(() => window.testState.views.byId.get(window.testState.views.activeViewId)?.scope?.rootElementId === 'link');
  assert.equal(await page.locator('[data-node-type="Multiplicity"]').count(), 1);

  const dropMultiplicity = () => page.evaluate(() => {
    const canvas = document.getElementById('canvasViewport');
    const rect = canvas.getBoundingClientRect();
    const dataTransfer = new DataTransfer();
    dataTransfer.setData('application/x-modriss-palette-item', JSON.stringify({ level: 'cim', type: 'Multiplicity' }));
    canvas.dispatchEvent(new DragEvent('drop', { bubbles: true, cancelable: true, dataTransfer, clientX: rect.left + 240, clientY: rect.top + 200 }));
  });
  await dropMultiplicity();
  await page.locator('[data-slot-index="1"]').click();
  await page.waitForFunction(() => typeof window.testState.graph.relationshipsById.get('link').targetMultiplicity === 'string');
  assert.deepEqual(await page.evaluate(() => window.testState.graph.relationshipsById.get('link').sourceMultiplicity), []);
  assert.equal(await page.evaluate(() => window.testState.diagram.nodes.length), 1);
  await page.locator('#canvasFocusBackBtn').click();
  await page.waitForFunction(() => window.testState.connectionsById.has('link'));
  await page.evaluate(() => window.openRelationship());
  await page.locator('#attrPanelOpenBtn').click();
  await page.waitForFunction(() => window.testState.diagram.nodes.length === 1 && window.testState.diagram.nodes[0].type === 'Multiplicity');
  const firstSave = await page.evaluate(() => window.saveAndReload());
  assert.ok(firstSave.relationships[0].targetMultiplicity.id);
  await dropMultiplicity();
  await page.waitForFunction(() => window.testState.graph.relationshipsById.get('link').sourceMultiplicity);
  assert.equal(await page.locator('[data-slot-index]').count(), 0);
  assert.equal(await page.evaluate(() => window.testState.diagram.nodes.length), 2);
  await dropMultiplicity();
  await page.waitForTimeout(100);
  assert.equal(await page.evaluate(() => window.testState.diagram.nodes.length), 2, "A full singleton slot cannot be overwritten.");
  const saved = await page.evaluate(() => window.saveAndReload());
  assert.ok(saved.relationships[0].sourceMultiplicity.id);
  assert.ok(saved.relationships[0].targetMultiplicity.id);
  assert.notEqual(saved.relationships[0].sourceMultiplicity.id, saved.relationships[0].targetMultiplicity.id);

  for (const mode of ['filled', 'filled&projected']) {
    await page.goto(`http://127.0.0.1:${server.address().port}/?${mode}`);
    await page.waitForFunction(() => window.containmentReady);
    await page.locator('#attrPanelOpenBtn').click();
    await page.waitForFunction(() => window.testState.diagram.nodes.length === 2 &&
      window.testState.diagram.nodes.every((node) => node.type === 'Multiplicity'));
    await page.waitForFunction(async () => {
      const { getCanvasEditor } = await import('/js/graph-editor/renderer-adapter.js');
      const graph = getCanvasEditor()?.graph;
      const rect = document.getElementById('canvasViewport').getBoundingClientRect();
      return graph && window.testState.diagram.nodes.every((node) => {
        const point = graph.getClientByCanvas([node.x + 100, node.y + 40]);
        return point[0] > rect.left && point[0] < rect.right && point[1] > rect.top && point[1] < rect.bottom;
      });
    });
    assert.deepEqual(await page.evaluate(() => window.testState.diagram.nodes.map((node) => node.label).sort()), ['1..1', 'One-to-One']);
    await dropMultiplicity();
    await page.waitForTimeout(100);
    assert.equal(await page.evaluate(() => window.testState.diagram.nodes.length), 2);
    assert.equal(await page.locator('[data-slot-index]').count(), 0, 'Existing multiplicities must not be replaced by a drop.');
  }
  assert.deepEqual(errors, []);
  console.log("Relationship Open, G6 rendering, slot selection, empty stored slots, existing offscreen multiplicities, graph projections and save/reload passed.");
} finally {
  await browser?.close();
  await new Promise((done) => server.close(done));
}
