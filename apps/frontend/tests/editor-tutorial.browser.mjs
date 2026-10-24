// Run explicitly: node apps/frontend/tests/editor-tutorial.browser.mjs
// Exercise the tutorial against the real editor shell; model state is the test boundary.
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { resolve, extname } from "node:path";
import { chromium } from "../../landing/node_modules/playwright/index.mjs";

const root = resolve("apps/frontend");
const harness = `
  import { state } from '/js/state.js';
  import { initEditorTutorial } from '/js/editor-tutorial.js';
  window.testState = state;
  state.auth.user = { id: new URLSearchParams(location.search).get('user') || 'account-a', guest: new URLSearchParams(location.search).has('guest') };
  state.activeType = 'cim';
  state.project = localStorage.getItem('test.project') ? { id: 'library' } : null;
  document.getElementById('projectOverlay').classList.toggle('hidden', !!state.project);
  document.getElementById('createProjectBtn').addEventListener('click', () => {
    if (!document.getElementById('newProjectName').value.trim()) return;
    state.project = { id: 'library' };
    localStorage.setItem('test.project', 'library');
    document.getElementById('projectOverlay').classList.add('hidden');
  });
  document.getElementById('modelTabs').innerHTML = '<button data-type="cim">CIM</button><button>PIM</button><button>PSM</button>';
  document.getElementById('modelWorkbenchPanel').innerHTML = '<button id="activeViewSelect">View</button><button id="saveModelBtn">Save</button>';
  document.getElementById('topbarModelingRow').classList.remove('hidden');
  document.getElementById('palette').innerHTML = '<div data-node-type="BusinessGoal">Business Goal</div><div data-node-type="Requirement">Requirement</div>';
  initEditorTutorial();
`;
const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, "http://localhost");
    if (url.pathname === "/") {
      const html = (await readFile(resolve(root, "index.html"), "utf8")).replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, "");
      response.setHeader("Content-Type", "text/html");
      response.end(html.replace("</body>", `<script type="module">${harness}</script></body>`));
      return;
    }
    const path = resolve(root, `.${url.pathname}`);
    if (!path.startsWith(`${root}\\`) && !path.startsWith(`${root}/`)) {
      response.writeHead(403).end();
      return;
    }
    response.setHeader("Content-Type", ({ ".js": "text/javascript", ".css": "text/css", ".svg": "image/svg+xml" })[extname(path)] || "application/octet-stream");
    response.end(await readFile(path));
  } catch {
    response.writeHead(404).end();
  }
});
await new Promise((done) => server.listen(0, "127.0.0.1", done));
const browser = await chromium.launch({ headless: true });
const origin = `http://127.0.0.1:${server.address().port}`;
const errors = [];
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  page.on("pageerror", (error) => errors.push(error.message));
  const next = page.locator(".editor-tutorial-next");
  const title = page.locator("#editorTutorialTitle");
  await page.goto(origin);
  await title.waitFor();
  assert.equal(await title.textContent(), "Create or open a project");
  assert.equal(await next.isDisabled(), true);
  await page.locator("#newProjectName").fill("My first model");
  await page.locator("#createProjectBtn").click({ timeout: 2500 });
  await page.waitForFunction(() => document.querySelector("#editorTutorialTitle")?.textContent === "The canvas");
  await next.click();
  await page.reload();
  await page.waitForFunction(() => document.querySelector("#editorTutorialTitle")?.textContent === "Modeling levels");
  while ((await title.textContent()) !== "Select CIM") await next.click();
  await next.click();
  assert.equal(await next.isDisabled(), true);
  await page.evaluate(() => {
    window.testState.views.byId.set("goals", { id: "goals", definitionId: "strategy-goals" });
    window.testState.views.activeViewId = "goals";
  });
  await page.waitForFunction(() => !document.querySelector(".editor-tutorial-next").disabled);
  await next.click();
  assert.equal(await next.isDisabled(), true);
  await page.evaluate(() => window.testState.graph.elementsById.set("goal", { id: "goal", eClass: "BusinessGoal" }));
  await page.waitForFunction(() => !document.querySelector(".editor-tutorial-next").disabled);
  await next.click();
  // A refresh loses unsaved model content. The tour must return to creation instead of getting stuck.
  await page.reload();
  await page.waitForFunction(() => document.querySelector("#editorTutorialTitle")?.textContent === "1. Add a business goal");
  assert.equal(await next.isDisabled(), true);
  await page.evaluate(() => window.testState.graph.elementsById.set("goal", { id: "goal", eClass: "BusinessGoal" }));
  await page.waitForFunction(() => !document.querySelector(".editor-tutorial-next").disabled);
  await next.click();
  await page.evaluate(() => Object.assign(window.testState.graph.elementsById.get("goal"), { name: "Reduce borrowing wait", successCriterion: "Within two days" }));
  await page.waitForFunction(() => !document.querySelector(".editor-tutorial-next").disabled);
  await next.click();
  await page.evaluate(() => window.testState.graph.elementsById.set("requirement", { id: "requirement", eClass: "Requirement" }));
  await page.waitForFunction(() => !document.querySelector(".editor-tutorial-next").disabled);
  await next.click();
  await page.evaluate(() => Object.assign(window.testState.graph.elementsById.get("requirement"), { name: "Reserve a book", fitCriterion: "Reserve an available book" }));
  await page.waitForFunction(() => !document.querySelector(".editor-tutorial-next").disabled);
  await next.click();
  await page.evaluate(() => { window.testState.graph.elementsById.get("requirement").supportsGoals = ["goal"]; });
  await page.waitForFunction(() => !document.querySelector(".editor-tutorial-next").disabled);
  await next.click();
  assert.equal(await next.isDisabled(), true);
  await page.evaluate(() => {
    window.testState.modelId = "saved-model";
    Object.assign(window.testState.modelSave, { dirty: false, saving: false, error: "", lastSavedAt: Date.now(), successfulSaves: 1 });
  });
  await page.waitForFunction(() => !document.querySelector(".editor-tutorial-next").disabled);
  await next.click();
  assert.equal(await title.textContent(), "CIM example saved");
  await next.click();
  await page.reload();
  await page.waitForLoadState("networkidle");
  assert.equal(await page.locator(".editor-tutorial").count(), 0, "completion survives reload");
  await page.goto(`${origin}/?user=account-b`);
  await title.waitFor();
  await page.locator(".editor-tutorial-finish").click();
  await page.reload();
  await page.waitForLoadState("networkidle");
  assert.equal(await page.locator(".editor-tutorial").count(), 0, "early dismissal survives reload");

  const mobile = await browser.newPage({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
  mobile.on("pageerror", (error) => errors.push(error.message));
  await mobile.goto(`${origin}/?guest`);
  await mobile.locator("#editorTutorialTitle").waitFor();
  const bounds = await mobile.locator(".editor-tutorial-card").boundingBox();
  assert.ok(bounds.x >= 0 && bounds.y >= 0 && bounds.x + bounds.width <= 390 && bounds.y + bounds.height <= 844);
  await mobile.locator(".editor-tutorial-minimize").click();
  assert.equal(await mobile.locator("#editorTutorialBody").isVisible(), false);
  await mobile.locator("#newProjectName").fill("Mobile project");
  await mobile.locator("#createProjectBtn").click({ timeout: 2500 });
  await mobile.waitForFunction(() => document.querySelector("#editorTutorialTitle")?.textContent === "The canvas");
  await mobile.locator(".editor-tutorial-minimize").click();
  await mobile.locator("#editorTutorialTitle").focus();
  await mobile.keyboard.press("Escape");
  await mobile.reload();
  await mobile.waitForLoadState("networkidle");
  assert.equal(await mobile.locator(".editor-tutorial").count(), 0);
  assert.deepEqual(errors, []);
  console.log("Tutorial browser checks passed: first visit, project controls, resumption, full CIM flow, save gating, completion, account isolation, mobile, and keyboard dismissal.");
} finally {
  await browser.close();
  await new Promise((done) => server.close(done));
}
