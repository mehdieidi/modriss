// Run explicitly: node apps/frontend/tests/assistant-model-lock.browser.mjs
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { extname, resolve } from "node:path";
import { chromium } from "../../landing/node_modules/playwright/index.mjs";

const root = resolve("apps/frontend");
let terminal = false;
let terminalState = "SUCCEEDED";
let remoteLocked = false;
let failLockCheck = false;
let failPoll = true;
let failSubmission = true;
let finalRefreshRequested = false;
let finishRefresh;
const finalRefresh = new Promise((done) => { finishRefresh = done; });
const writes = [];
const model = { id: "root", eClass: "Model", name: "Assistant result", entities: [] };
const harness = `
  import { state } from '/js/state.js';
  import { applyModelingRuntimeConfig } from '/js/config.js';
  import { initializeModelingRuntimeState } from '/js/modeling-config-data.js';
  import { installGraphAndViews } from '/js/graph-store.js';
  import { materializeActiveView } from '/js/view-materializer.js';
  import { resetModelSaveState, markModelDirty } from '/js/model-save-ui.js';
  import { sendChatMessage, handleChatSendButtonClick } from '/js/chat.js';
  import { saveCurrentModel, loadModelById } from '/js/model-ops.js';
  import { refreshAssistantModelLock } from '/js/assistant-model-lock-monitor.js';
  import { applyAttributePanel, deleteSelection } from '/js/attr-panel.js';
  import { setupDnD } from '/js/canvas.js';
  const config = { version: 1, levelOrder: ['cim'], assistant: {}, levels: { cim: {
    apiType: 'cim', assistantEnabled: true, labelField: 'name', modelNameTemplate: 'Example',
    rootTemplate: { eClass: 'Model' },
    relationshipSemantics: { containmentKind: 'CONTAINS', containmentKinds: ['CONTAINS'] },
    workbench: { defaultViewDefinitionId: 'main' },
    viewDefinitions: [{ id: 'main', viewType: 'main', canvas: ['DomainEntity'], palette: ['DomainEntity'] }],
    elements: [
      { type: 'Model', references: [{ name: 'entities', targetType: 'DomainEntity', containment: true, many: true }] },
      { type: 'DomainEntity', visualRole: 'node', icon: 'link', creatable: true, notation: {}, attributes: [{ name: 'name', fieldType: 'text' }] },
    ],
  } } };
  state.modelingConfig.config = config;
  initializeModelingRuntimeState(config);
  applyModelingRuntimeConfig(config);
  state.activeType = 'cim';
  state.tabs = { cim: { modelName: 'Example' } };
  state.project = { id: 'project', name: 'Project', activeModelIds: { cim: 'model' } };
  state.modelId = 'model';
  state.modelRevision = 1;
  state.baseModel = { id: 'root', eClass: 'Model', name: 'Original', entities: [] };
  installGraphAndViews('cim', state.baseModel, 'Example', { skipFragments: true, skipClientLayout: true });
  materializeActiveView();
  state.chat.sessions.set('project:cim', { sessionId: 'session', projectId: 'project' });
  window.prepareChatScopeForTest();
  document.getElementById('modelWorkbenchPanel').innerHTML = '<button id="saveModelBtn">Save</button>';
  document.getElementById('projectOverlay').classList.add('hidden');
  resetModelSaveState();
  setupDnD();
  window.testState = state;
  window.stopRequest = handleChatSendButtonClick;
  window.loadStoredModel = () => loadModelById('cim', 'model', { autoLayout: false });
  window.refreshLock = refreshAssistantModelLock;
  window.startRequest = () => {
    document.getElementById('chatInput').value = 'Update the model';
    window.requestDone = false;
    void sendChatMessage().then(() => { window.requestDone = true; });
  };
  window.attemptSave = () => saveCurrentModel({ quiet: true, rethrow: true }).catch(() => {});
  window.attemptMutations = async () => { applyAttributePanel(); await deleteSelection(); };
  window.markModelDirty = markModelDirty;
  window.lockTestReady = true;
`;

const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, "http://localhost");
    if (url.pathname.startsWith("/api/")) {
      if (["POST", "PUT", "PATCH"].includes(request.method)) {
        const chunks = [];
        for await (const chunk of request) chunks.push(chunk);
        writes.push({ path: url.pathname, body: chunks.length ? JSON.parse(Buffer.concat(chunks).toString()) : null });
      }
      response.setHeader("Content-Type", "application/json");
      let body = {};
      if (url.pathname.endsWith("/messages")) {
        if (failSubmission) { failSubmission = false; response.writeHead(503).end('{"message":"Acknowledgement lost"}'); return; }
        body = { turnId: "turn", eventCursor: 0 };
      }
      else if (url.pathname.endsWith("/events")) {
        response.setHeader("Content-Type", "text/event-stream");
        response.end('event: model.checkpoint.committed\ndata: {"payload":{"modelId":"model","revision":2}}\n\n');
        return;
      } else if (url.pathname === "/api/chatbot/turns/turn") {
        if (failPoll) { failPoll = false; response.writeHead(503).end('{"message":"Disconnected"}'); return; }
        body = { turnId: "turn", state: terminal ? terminalState : "RUNNING", modelId: "model", revision: 2, finalMessage: "Done" };
      } else if (url.pathname.endsWith("/editing-lock")) {
        if (failLockCheck) { failLockCheck = false; response.writeHead(503).end('{"message":"Disconnected"}'); return; }
        body = { locked: remoteLocked };
      } else if (url.pathname === "/api/cim/model" && request.method === "GET") {
        if (terminal) { finalRefreshRequested = true; await finalRefresh; }
        body = { id: "model", revision: 2, name: "Example", modelJson: model };
      }
      response.end(JSON.stringify(body));
      return;
    }
    if (url.pathname === "/") {
      const html = (await readFile(resolve(root, "index.html"), "utf8"))
        .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, "");
      response.setHeader("Content-Type", "text/html");
      response.end(html.replace("</body>", `<script src="/vendor/antv/g6.min.js"></script><script type="module">${harness}</script></body>`));
      return;
    }
    const path = resolve(root, `.${url.pathname}`);
    if (!path.startsWith(`${root}\\`) && !path.startsWith(`${root}/`)) { response.writeHead(403).end(); return; }
    response.setHeader("Content-Type", ({ ".js": "text/javascript", ".css": "text/css", ".svg": "image/svg+xml" })[extname(path)] || "application/octet-stream");
    let content = await readFile(path);
    if (url.pathname === "/js/chat.js") {
      content = content.toString().replace(
        '} catch {\n          setStatus("The assistant finished.',
        '} catch (error) {\n          window.testRefreshError = error.stack;\n          setStatus("The assistant finished.',
      );
      // Opening the real chat establishes this scope. Avoid unrelated thread hydration in
      // this request/locking harness while retaining the production scope checks.
      content += '\nwindow.prepareChatScopeForTest = () => { renderedChatScopeKey = chatScopeKey(); };\n';
    }
    response.end(content);
  } catch { response.writeHead(404).end(); }
});
await new Promise((done) => server.listen(0, "127.0.0.1", done));
let browser;
try {
  browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto(`http://127.0.0.1:${server.address().port}`);
  await page.waitForFunction(() => window.lockTestReady, null, { timeout: 15000 })
    .catch((error) => { throw new Error(`${error.message}\n${errors.join("\n")}`); });
  await page.evaluate(() => window.startRequest());
  await page.waitForFunction(() => window.testState.assistantModelLocked);
  await page.waitForFunction(() => window.testState.modelRevision === 2);
  assert.equal(await page.locator("#saveModelBtn").isDisabled(), true);
  assert.equal(await page.locator("#mobileDockSaveBtn").isDisabled(), true);
  assert.equal(await page.evaluate(() => document.getElementById("canvasViewport").inert), true);
  await page.evaluate(async () => {
    await window.attemptSave();
    await window.attemptMutations();
    const dataTransfer = new DataTransfer();
    dataTransfer.setData('application/x-modriss-palette-item', JSON.stringify({ level: 'cim', type: 'DomainEntity' }));
    document.getElementById('canvasViewport').dispatchEvent(new DragEvent('drop', { bubbles: true, cancelable: true, dataTransfer }));
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 's', ctrlKey: true, bubbles: true, cancelable: true }));
  });
  assert.equal(writes.length, 2, "Only the assistant submission and its retry may write while locked");
  assert.equal(writes[0].body.idempotencyKey, writes[1].body.idempotencyKey);
  assert.ok(writes.every((write) => write.path.endsWith('/messages')));
  assert.equal(await page.evaluate(() => window.testState.diagram.nodes.length), 0);
  assert.equal(await page.evaluate(() => window.requestDone), false, "A polling disconnect must keep the request and lock active");

  // A draft from an already queued callback must be discarded even when the final revision
  // equals the checkpoint already displayed on the canvas.
  await page.evaluate(() => {
    window.testState.baseModel.name = 'Stray local draft';
    window.testState.diagram.nodes.push({ id: 'stray', type: 'DomainEntity', label: 'Stray', meta: {} });
    window.markModelDirty();
  });
  terminal = true;
  await page.waitForFunction(() => document.querySelector('[data-chat-turn-state="SUCCEEDED"]'));
  assert.equal(finalRefreshRequested, true);
  assert.equal(await page.evaluate(() => window.testState.assistantModelLocked), true);
  assert.equal(await page.locator("#saveModelBtn").isDisabled(), true);
  finishRefresh();
  await page.waitForFunction(() => window.requestDone && !window.testState.assistantModelLocked, null, { timeout: 10000 })
    .catch(async (error) => { throw new Error(`${error.message}\n${JSON.stringify(await page.evaluate(() => ({ error: window.testRefreshError, done: window.requestDone, locked: window.testState.assistantModelLocked, revision: window.testState.modelRevision, name: window.testState.baseModel.name, status: document.getElementById('chatMessages').textContent }))) }\n${errors.join("\n")}`); });
  assert.equal(await page.evaluate(() => window.testState.baseModel.name), "Assistant result");
  assert.equal(await page.evaluate(() => window.testState.diagram.nodes.length), 0);
  assert.equal(await page.evaluate(() => window.testState.modelSave.dirty), false);
  assert.equal(await page.locator("#saveModelBtn").isDisabled(), false);
  assert.equal(await page.locator("#mobileDockSaveBtn").isDisabled(), false);
  assert.equal(await page.evaluate(() => document.getElementById("canvasViewport").inert), false);

  terminal = false;
  await page.evaluate(() => window.startRequest());
  await page.waitForFunction(() => window.testState.assistantModelLocked);
  await page.evaluate(() => window.stopRequest());
  await page.waitForFunction(() => document.getElementById('chatSendBtn').getAttribute('aria-label') === 'Stopping...');
  assert.equal(await page.evaluate(() => window.testState.assistantModelLocked), true);
  assert.equal(await page.locator("#saveModelBtn").isDisabled(), true);
  terminalState = "CANCELLED";
  terminal = true;
  await page.waitForFunction(() => window.requestDone && !window.testState.assistantModelLocked);
  assert.ok(writes.some((write) => write.path.endsWith('/cancel')));

  remoteLocked = true;
  await page.evaluate(() => window.loadStoredModel());
  assert.equal(await page.evaluate(() => window.testState.assistantModelLocked), true, "Reloading a model must discover another tab's active request");
  await page.evaluate(() => { window.testState.baseModel.name = 'Another tab draft'; window.markModelDirty(); });
  failLockCheck = true;
  await page.evaluate(() => window.refreshLock().catch(() => {}));
  assert.equal(await page.evaluate(() => window.testState.assistantModelLocked), true);
  remoteLocked = false;
  await page.evaluate(() => window.refreshLock());
  assert.equal(await page.evaluate(() => window.testState.baseModel.name), "Assistant result");
  assert.equal(await page.evaluate(() => window.testState.assistantModelLocked), false);
  assert.deepEqual(errors, []);
  console.log("Assistant lock browser regression passed: submission, checkpoints, disconnect, blocked edits/saves, final refresh, draft discard, cancellation and reloaded tabs.");
} finally {
  finishRefresh();
  await browser?.close();
  await new Promise((done) => server.close(done));
}
