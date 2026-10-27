// Run explicitly: node apps/frontend/tests/impact.browser.mjs
// Real editor DOM, event bindings and Monaco; deterministic trace API fixtures.
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { extname, resolve } from "node:path";
import { chromium } from "../../landing/node_modules/playwright/index.mjs";

const root = resolve("apps/frontend");
const paths = ["src/order & payment.js", "template.yaml", "README.md"];
const requests = [];
const upstream = ["CIM", "PIM", "PSM"].map((modelType) => ({
  modelId: modelType.toLowerCase(), modelType, modelName: `${modelType} model`,
  elementId: "order", elementName: `${modelType} order`, elementType: "Function",
}));
function traces(filePath) {
  return {
    modelId: "artifact", modelName: "Generated project",
    upstream: filePath === paths[2] ? [] : upstream,
    traceEdges: filePath === paths[2] ? [] : upstream.map((source, index) => ({
      sourceModelId: source.modelId, sourceElementId: source.elementId,
      targetModelId: upstream[index + 1]?.modelId || "artifact",
      targetElementId: upstream[index + 1]?.elementId || filePath,
      transformationRule: `GenerateOrder${index}`,
    })),
    notices: filePath === paths[2] ? ["No recorded file provenance."] : [],
  };
}
const harness = `
  import { state } from '/js/state.js';
  import { applyModelingRuntimeConfig } from '/js/config.js';
  import { initializeModelingRuntimeState } from '/js/modeling-config-data.js';
  import { loadArtifactRecord } from '/js/artifact.js';
  import { switchTab } from '/js/model-ops.js';
  state.modelingConfig.config = {
    levelOrder: ['pim'], artifact: { key: 'artifact' },
    levels: { pim: { apiType: 'pim', labelField: 'name', elements: [],
      relationshipKinds: [], relationshipSemantics: {},
      rootTemplate: { eClass: 'Model', name: 'Test model' } } },
  };
  initializeModelingRuntimeState(state.modelingConfig.config);
  applyModelingRuntimeConfig(state.modelingConfig.config);
  state.project = { id: 'project', name: 'Project' };
  state.modelId = null;
  await loadArtifactRecord({ id: 'artifact', name: 'Generated project',
    filePaths: ${JSON.stringify(paths)} }, { collapseTree: false });
  await import('/js/main.js');
  await switchTab('artifact', { skipSnapshot: true });
  window.testState = state;
  window.impactTestReady = true;
`;
const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, "http://localhost");
    if (url.pathname.startsWith("/api/")) {
      if (url.pathname === "/api/impact/artifact/artifact") {
        const filePath = url.searchParams.get("filePath");
        assert.ok(paths.includes(filePath));
        requests.push(filePath);
        response.setHeader("Content-Type", "application/json");
        response.end(JSON.stringify(traces(filePath)));
      } else if (url.pathname === "/api/artifact/artifact/file") {
        response.setHeader("Content-Type", "text/plain");
        response.end(`// ${url.searchParams.get("path")}`);
      } else {
        response.setHeader("Content-Type", "application/json");
        response.end("[]");
      }
      return;
    }
    if (url.pathname === "/") {
      const html = (await readFile(resolve(root, "index.html"), "utf8"))
        .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, "");
      response.setHeader("Content-Type", "text/html");
      response.end(html.replace("</body>", `<script src="/vendor/monaco/vs/loader.js"></script><script type="module">${harness}</script></body>`));
      return;
    }
    const path = resolve(root, `.${url.pathname}`);
    if (!path.startsWith(`${root}\\`) && !path.startsWith(`${root}/`)) {
      response.writeHead(403).end();
      return;
    }
    let content = await readFile(path);
    if (url.pathname === "/js/main.js") {
      // Bind real controls without starting authentication or loading a project.
      const source = content.toString();
      const bootstrap = source.lastIndexOf("\ninit().catch(");
      assert.ok(bootstrap >= 0);
      content = `${source.slice(0, bootstrap)}\nbindEvents();\n`;
    }
    response.setHeader("Content-Type", ({ ".js": "text/javascript", ".css": "text/css", ".svg": "image/svg+xml", ".ttf": "font/ttf" })[extname(path)] || "application/octet-stream");
    response.end(content);
  } catch (error) {
    response.writeHead(500).end(error.message);
  }
});

await new Promise((done) => server.listen(0, "127.0.0.1", done));
let browser;
try {
  browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
  page.setDefaultTimeout(10000);
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto(`http://127.0.0.1:${server.address().port}`);
  await page.waitForFunction(() => window.impactTestReady)
    .catch((error) => { throw new Error(`${error.message}\n${errors.join("\n")}`); });
  const toggle = page.locator("#impactToggleBtn");
  const panel = page.locator("#impactPanel");
  const file = (index) => page.locator(`.file-tree-item[data-path=${JSON.stringify(paths[index])}]`);
  async function expectFile(index) {
    await page.waitForFunction((path) => window.testState.impactData?.focalElement.elementId === path, paths[index]);
    assert.equal(await panel.isVisible(), true);
    assert.equal(await page.locator(".impact-focal-name").textContent(), paths[index]);
    assert.equal(await page.locator("#impactPanelBody").getAttribute("aria-busy"), null);
  }
  async function expectOpen(index) {
    await page.waitForFunction((path) => window.testState.artifact.activeFile === path && !window.testState.artifact.openingFile, paths[index]);
  }

  assert.equal(await toggle.isEnabled(), true);
  await toggle.click();
  assert.equal(await panel.isVisible(), true);
  assert.match(await panel.textContent(), /Select a file in the explorer/);
  assert.equal(requests.length, 0);
  await file(0).click();
  await expectFile(0);
  await expectOpen(0);
  assert.deepEqual(requests, [paths[0]]);
  assert.deepEqual(await page.locator(".impact-stats b").allTextContents(), ["3", "0", "0"]);
  assert.deepEqual(await page.locator(".impact-result-name").allTextContents(), upstream.map((item) => item.elementName));
  assert.equal(await page.locator(".impact-evidence code").count(), 3);

  // Opening a file outside impact mode stays ordinary; enabling it analyzes that file.
  await toggle.click();
  assert.equal(await panel.isVisible(), false);
  await file(1).click();
  await expectOpen(1);
  assert.equal(requests.length, 1);
  await toggle.click();
  await expectFile(1);
  assert.deepEqual(requests, [paths[0], paths[1]]);
  await file(0).click();
  await expectFile(0);

  // A late response for an earlier selection cannot overwrite the current file.
  const delayed = "**/api/impact/artifact/artifact?filePath=template.yaml";
  let release, started;
  const pending = new Promise((done) => { release = done; });
  const requested = new Promise((done) => { started = done; });
  await page.route(delayed, async (route) => {
    started();
    await pending;
    await route.fulfill({ json: traces(paths[1]) }).catch(() => {});
  });
  await file(1).click();
  await requested;
  await file(0).click();
  await expectFile(0);
  release();
  await page.unrouteAll({ behavior: "wait" });
  await expectFile(0);

  // Closing during a request clears results and keeps the pane closed.
  const closing = new Promise((done) => { release = done; });
  const closingRequested = new Promise((done) => { started = done; });
  await page.route(delayed, async (route) => {
    started();
    await closing;
    await route.fulfill({ json: traces(paths[1]) }).catch(() => {});
  });
  await file(1).click();
  await closingRequested;
  await page.locator("#impactPanelCloseBtn").click();
  release();
  await page.unrouteAll({ behavior: "wait" });
  assert.equal(await panel.isVisible(), false);
  assert.equal(await page.evaluate(() => window.testState.impactData), null);
  await expectOpen(1);

  // API failures can be retried for the same open file.
  await page.route(delayed, (route) => route.fulfill({ status: 503, json: { message: "Trace service unavailable" } }));
  await toggle.click();
  await page.locator(".impact-retry").waitFor();
  assert.equal(await page.locator("#impactPanelBody").getAttribute("aria-busy"), null);
  await page.unrouteAll({ behavior: "wait" });
  await page.locator(".impact-retry").click();
  await expectFile(1);

  await file(2).click();
  await expectFile(2);
  assert.deepEqual(await page.locator(".impact-stats b").allTextContents(), ["0", "0", "0"]);
  assert.match(await page.locator(".impact-notice").textContent(), /No recorded file provenance/);
  await page.setViewportSize({ width: 390, height: 844 });
  assert.equal(await panel.isVisible(), true);
  assert.ok((await panel.boundingBox()).width <= 390);
  assert.deepEqual(errors, []);
  console.log("Impact browser regression passed: artifact toggle, file selection, open file, source traces, request races, close, retry, missing provenance and mobile pane.");
} finally {
  await browser?.close();
  await new Promise((done) => server.close(done));
}
