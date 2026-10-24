// Run explicitly: node apps/frontend/tests/model-save.browser.mjs
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { extname, resolve } from "node:path";
import { chromium } from "../../landing/node_modules/playwright/index.mjs";

const root = resolve("apps/frontend");
const writes = [];
const harness = `
  import { state } from '/js/state.js';
  import { applyModelingRuntimeConfig } from '/js/config.js';
  import { initializeModelingRuntimeState } from '/js/modeling-config-data.js';
  import { resetModelSaveState, markModelDirty } from '/js/model-save-ui.js';
  state.modelingConfig.config = {
    levelOrder: ['pim'],
    levels: { pim: {
      apiType: 'pim', modelNameTemplate: 'Test model', labelField: 'name',
      rootTemplate: { eClass: 'Model', name: 'Test model' },
      elements: [], relationshipKinds: [], relationshipSemantics: {},
    } },
  };
  initializeModelingRuntimeState(state.modelingConfig.config);
  applyModelingRuntimeConfig(state.modelingConfig.config);
  state.activeType = 'pim';
  state.project = { id: 'project', name: 'Project', activeModelIds: { pim: 'model' } };
  state.modelId = 'model';
  state.modelRevision = 1;
  state.baseModel = { eClass: 'Model', name: 'Test model' };
  state.tabs.pim = { modelId: 'model', modelName: 'Test model' };
  document.getElementById('modelWorkbenchPanel').innerHTML = '<button id="saveModelBtn">Save</button>';
  window.testState = state;
  window.markModelDirty = markModelDirty;
  resetModelSaveState();
  await import('/js/main.js');
  window.saveTestReady = true;
`;
const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, "http://localhost");
    if (url.pathname.startsWith("/api/")) {
      if (["POST", "PUT", "PATCH"].includes(request.method)) {
        const chunks = [];
        for await (const chunk of request) chunks.push(chunk);
        writes.push({ method: request.method, path: url.pathname, body: JSON.parse(Buffer.concat(chunks).toString()) });
      }
      response.setHeader("Content-Type", "application/json");
      response.end(JSON.stringify(request.method === "GET" ? [] : { id: "model", revision: 2 }));
      return;
    }
    if (url.pathname === "/") {
      const html = (await readFile(resolve(root, "index.html"), "utf8"))
        .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, "");
      response.setHeader("Content-Type", "text/html");
      response.end(html.replace("</body>", `<script type="module">${harness}</script></body>`));
      return;
    }
    const path = resolve(root, `.${url.pathname}`);
    if (!path.startsWith(`${root}\\`) && !path.startsWith(`${root}/`)) {
      response.writeHead(403).end();
      return;
    }
    let content = await readFile(path);
    if (url.pathname === "/js/main.js") {
      // Keep the real event bindings and save implementation; bypass application
      // startup (authentication, renderer and project loading) in this focused harness.
      const source = content.toString();
      const bootstrap = source.lastIndexOf("\ninit().catch(");
      assert.ok(bootstrap >= 0);
      content = `${source.slice(0, bootstrap)}\nbindEvents();\nbindUnsavedModelGuard();\n`;
    }
    response.setHeader("Content-Type", ({ ".js": "text/javascript", ".css": "text/css", ".svg": "image/svg+xml" })[extname(path)] || "application/octet-stream");
    response.end(content);
  } catch {
    response.writeHead(404).end();
  }
});

await new Promise((done) => server.listen(0, "127.0.0.1", done));
let browser;
try {
  browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto(`http://127.0.0.1:${server.address().port}`);
  await page.waitForFunction(() => window.saveTestReady, null, { timeout: 10000 })
    .catch((error) => { throw new Error(`${error.message}\n${errors.join("\n")}`); });

  async function saveWithoutChanges(action) {
    const before = writes.length;
    const result = await page.evaluate((action) => {
      if (action === "shortcut") {
        document.dispatchEvent(new KeyboardEvent("keydown", { key: "s", ctrlKey: true, bubbles: true }));
      } else {
        document.getElementById(action).click();
      }
      return {
        saving: window.testState.modelSave.saving,
        buttonsReady: ["saveModelBtn", "mobileDockSaveBtn"].every((id) => {
          const button = document.getElementById(id);
          return !button.disabled && !button.classList.contains("is-saving");
        }),
        busy: Boolean(document.querySelector(".toast-busy:not(.toast-leaving)")),
        message: document.querySelector(".toast-success .toast-message")?.textContent,
      };
    }, action);
    assert.equal(result.saving, false, `${action} must return without entering saving state`);
    assert.equal(result.buttonsReady, true);
    assert.equal(result.busy, false);
    assert.equal(result.message, "Model is up to date.");
    assert.equal(writes.length, before);
  }

  for (const action of ["saveModelBtn", "mobileDockSaveBtn", "shortcut"]) {
    await saveWithoutChanges(action);
  }

  await page.evaluate(() => {
    window.testState.baseModel.name = "Changed model";
    window.markModelDirty();
    document.getElementById("saveModelBtn").click();
    if (!window.testState.modelSave.saving) throw new Error("Changed model must enter saving state");
  });
  await page.waitForFunction(() => window.testState.modelSave.successfulSaves === 1 || window.testState.modelSave.error, null, { timeout: 10000 });
  assert.equal(await page.evaluate(() => window.testState.modelSave.error), "");
  assert.ok(writes.some((write) => write.path === "/api/pim/model"));
  assert.equal(await page.evaluate(() => window.testState.modelSave.dirty), false);
  await saveWithoutChanges("saveModelBtn");

  await page.evaluate(() => {
    window.testState.modelId = null;
    document.getElementById("saveModelBtn").click();
    if (!window.testState.modelSave.saving) throw new Error("New model must enter saving state");
  });
  await page.waitForFunction(() => window.testState.modelSave.successfulSaves === 2 || window.testState.modelSave.error, null, { timeout: 10000 });
  assert.equal(await page.evaluate(() => window.testState.modelSave.error), "");
  assert.ok(writes.some((write) => write.method === "POST" && write.path === "/api/pim"));
  assert.deepEqual(errors, []);
  console.log("Model save browser regression passed: desktop, mobile, Ctrl+S, changed save, repeated save and new model.");
} finally {
  await browser?.close();
  await new Promise((done) => server.close(done));
}
