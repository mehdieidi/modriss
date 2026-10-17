// Run explicitly: node apps/frontend/tests/psm-platforms.browser.mjs
// Uses the repository's existing landing Playwright dependency and mocks only the API boundary.
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { resolve, extname } from "node:path";
import { chromium } from "../../landing/node_modules/playwright/index.mjs";

const root = resolve("apps/frontend");
const sample = resolve("methodology/modeling-framework/samples/custom-psm-azure");
const packages = [];
const branches = [];
const nativeModels = [];
const artifacts = [
  { id: 'azure-artifact', cloudProviderId: 'azure', name: 'Azure output', modelJson: { psmPackageId: 'azure' }, filePaths: ['azure.txt'] },
  { id: 'aws-artifact', cloudProviderId: 'aws', name: 'AWS output', modelJson: {}, filePaths: ['aws.txt'] },
];
let rejectNextUpload = true;
let savedNotation;
const schema = [
  { name: 'AzureApplication', namespace: 'https://example.org/azure', features: [
    { name: 'name', type: 'EString', kind: 'attribute', required: true },
    { name: 'location', type: 'EString', kind: 'attribute', required: true },
    { name: 'functions', type: 'AzureFunction', typeNamespace: 'https://example.org/azure', kind: 'reference', containment: true, many: true },
  ] },
  { name: 'AzureFunction', namespace: 'https://example.org/azure', features: [
    { name: 'name', type: 'EString', kind: 'attribute', required: true },
    { name: 'runtime', type: 'EString', kind: 'attribute', required: true },
  ] },
];
const xmi = '<?xml version="1.0"?><azure:AzureApplication xmlns:azure="https://example.org/azure" name="Demo" location="westeurope"><functions name="Hello" runtime="nodejs20"/></azure:AzureApplication>';
const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, "http://localhost");
    if (url.pathname.startsWith("/api/")) {
      const chunks = [];
      for await (const chunk of request) chunks.push(chunk);
      const body = Buffer.concat(chunks);
      const json = body.length && request.headers["content-type"]?.includes("application/json") ? JSON.parse(body) : {};
      let result;
      const base = "/api/projects/project/psm";
      if (url.pathname === '/api/artifact') result = artifacts;
      else if (url.pathname.startsWith('/api/artifact/')) result = artifacts.find((item) => url.pathname.endsWith(item.id));
      else if (url.pathname === '/api/projects/project') result = { id: 'project', ...json };
      else if (url.pathname.endsWith('/definition')) result = { schema, visualSyntax: savedNotation || null };
      else if (url.pathname.endsWith('/notation')) { savedNotation = json; result = { schema, visualSyntax: savedNotation }; }
      else if (url.pathname === "/api/pim") result = [{ id: "pim", name: "Shared architecture", revision: 3 },
        ...Array.from({ length: 5 }, (_, i) => ({ id: `pim-${i + 1}`, name: `Alternative architecture ${i + 1}`, revision: 1 }))];
      else if (url.pathname === "/api/psm") result = nativeModels;
      else if (url.pathname === `${base}/packages` && request.method === "POST") {
        if (rejectNextUpload) {
          rejectNextUpload = false;
          response.statusCode = 503;
          response.setHeader("Content-Type", "application/json");
          response.end(JSON.stringify({ message: "The service is temporarily unavailable." }));
          return;
        }
        result = { id: "azure", name: "Azure", description: "Functions example", transformationEntry: "main.etl", generationEntry: "main.egx", hasValidation: true, createdAt: new Date().toISOString() };
        packages.push(result);
      } else if (url.pathname === `${base}/packages`) result = packages;
      else if (url.pathname === `${base}/branches` && request.method === "POST") {
        result = { id: `branch-${branches.length + 1}`, packageId: "azure", name: json.name, sourcePimId: "pim", sourcePimRevision: 3, revision: 1, xmi, updatedAt: new Date().toISOString() };
        branches.push(result);
      } else if (url.pathname === `${base}/branches`) result = branches;
      else if (url.pathname.endsWith("/schema")) result = { valid: true, issues: [], schema: [
        { name: "AzureApplication", namespace: "https://example.org/azure", features: [
          { name: "name", type: "EString", kind: "attribute", required: true },
          { name: "location", type: "EString", kind: "attribute", required: true },
          { name: "enabled", type: "EBoolean", kind: "attribute", defaultValue: "true" },
          { name: "functions", type: "AzureFunction", typeNamespace: "https://example.org/azure", kind: "reference", containment: true, upperBound: -1 },
        ] },
        { name: "AzureFunction", namespace: "https://example.org/azure", features: [
          { name: "name", type: "EString", kind: "attribute", required: true },
          { name: "runtime", type: "EString", kind: "attribute", required: true },
        ] },
      ] };
      else if (url.pathname.endsWith("/validate")) {
        const branch = branches.find((b) => url.pathname.includes(b.id));
        result = { valid: !branch.xmi.includes("unsupported"), issues: branch.xmi.includes("unsupported") ? ["SupportedRuntime: Choose nodejs20."] : [] };
      } else if (url.pathname.endsWith("/artifacts") && request.method === "POST") result = { id: "artifact" };
      else if (url.pathname.endsWith("/artifacts")) result = [{ id: "artifact", name: "Azure artifacts", filePaths: ["azure-application.txt"], createdAt: new Date().toISOString() }];
      else {
        const branch = branches.find((b) => url.pathname.endsWith(`/${b.id}`));
        if (!branch) throw new Error(`Unexpected API path ${url.pathname}`);
        if (request.method === "PUT") Object.assign(branch, { xmi: json.xmi, name: json.name, revision: branch.revision + 1 });
        result = branch;
      }
      response.setHeader("Content-Type", "application/json");
      response.end(JSON.stringify(result));
      return;
    }
    if (url.pathname === "/") {
      response.setHeader("Content-Type", "text/html");
      response.end(`<html><head><meta charset="utf-8"><link rel="stylesheet" href="/css/variables.css"><link rel="stylesheet" href="/css/buttons.css"><link rel="stylesheet" href="/css/panels.css"><link rel="stylesheet" href="/css/psm-platforms.css"></head><body style="background:var(--bg);font-family:var(--font-ui)"><div id="modelTabs"><button class="tab" data-type="psm">AWS</button></div><button id="generateContextBtn"><span class="topbar-btn-label">Generate PSM</span></button><button id="psmPlatformsBtn">Cloud Platforms</button><script type="module">import {state} from '/js/state.js';state.project={id:'project'};state.activeType='pim';state.modelId='pim';state.tabs.pim={modelId:'pim'};state.tabs.psm={};state.modelingConfig.config={levelOrder:['pim','psm'],levels:{pim:{modelNameTemplate:'PIM'},psm:{modelNameTemplate:'AWS',rootTemplate:{eClass:'PSMModel'}}},artifact:{key:'artifact'},diagramEditor:{iconAliases:{functions:'/assets/icons/process-map/cloud.svg',placeholder:'/assets/icons/process-map/cloud.svg'}}};await import('/js/psm-platforms.js');</script></body></html>`);
      return;
    }
    const path = resolve(root, `.${url.pathname}`);
    if (!path.startsWith(root)) throw new Error("Invalid path");
    const mime = { ".js": "text/javascript", ".css": "text/css", ".ttf": "font/ttf", ".svg": "image/svg+xml" }[extname(path)] || "application/octet-stream";
    response.setHeader("Content-Type", mime);
    response.end(await readFile(path));
  } catch (error) {
    response.statusCode = 500;
    response.end(error.message);
  }
});
await new Promise((ready) => server.listen(0, "127.0.0.1", ready));
const browser = await chromium.launch({ headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto(`http://127.0.0.1:${server.address().port}`);
  await page.evaluate(async () => { await import('/js/psm-platforms.js'); });
  await page.getByRole("button", { name: "Cloud Platforms", exact: true }).click();
  await page.getByRole("heading", { name: "Cloud platform packages", exact: true }).waitFor();
  assert.equal(await page.locator('#psm-source-pim, #psm-create-form').count(), 0);
  assert.equal(await page.getByRole('tab', { name: 'Visual notation', exact: true }).count(), 1);
  assert.equal(await page.locator('[data-psm-action="upload"]').count(), 1);
  await page.getByRole("button", { name: "Add platform", exact: true }).click();
  await page.getByLabel("Platform name", { exact: true }).fill("Azure");
  await page.getByLabel("Description (optional)", { exact: true }).fill("Draft must survive picker cancellation");
  for (const name of ["ecore", "transformation", "generation", "validation"]) {
    await page.locator(`#psm-upload-form [name="${name}"]`).dispatchEvent("cancel", { bubbles: true });
    assert.equal(await page.locator(".psm-dialog").evaluate((dialog) => dialog.open), true);
    assert.equal(await page.getByLabel("Platform name", { exact: true }).inputValue(), "Azure");
  }
  await page.getByLabel("Ecore metamodel").setInputFiles(resolve(sample, "azure.ecore"));
  await page.getByLabel("ETL package", { exact: true }).setInputFiles(resolve(sample, "transformation.zip"));
  await page.locator('[name="transformationEntry"] option[value="main.etl"]').waitFor({ state: "attached" });
  const entryTrigger = page.locator('[name="transformationEntry"]').locator("..").getByRole("button", { name: /^Entry script:/ });
  assert.equal(await page.locator('[name="transformationEntry"]').isVisible(), false);
  await entryTrigger.click();
  await page.keyboard.press("End");
  await page.keyboard.press("Enter");
  assert.equal(await page.locator('[name="transformationEntry"]').inputValue(), "rules/functions.etl");
  await entryTrigger.click();
  await page.keyboard.press("Home");
  await page.keyboard.press("Enter");
  assert.equal(await page.locator('[name="transformationEntry"]').inputValue(), "main.etl");
  await page.getByLabel("EGX / EGL package").setInputFiles(resolve(sample, "generation.zip"));
  await page.locator('[name="generationEntry"] option[value="main.egx"]').waitFor({ state: "attached" });
  await page.getByLabel("EVL package", { exact: true }).setInputFiles(resolve(sample, "validation.zip"));
  await page.getByRole("button", { name: "Close cloud platforms" }).click();
  nativeModels.push({ id: "aws", name: "AWS baseline", revision: 1 });
  await page.getByRole("button", { name: "Cloud Platforms", exact: true }).click();
  await page.getByRole("heading", { name: "Cloud platform packages", exact: true }).waitFor();
  assert.equal(await page.locator("#psm-upload-form").count(), 0);
  await page.getByRole("button", { name: "Add platform", exact: true }).click();
  assert.equal(await page.getByLabel("Platform name", { exact: true }).inputValue(), "Azure");
  assert.equal(await page.locator('[name="transformation"]').evaluate((input) => input.files[0].name), "transformation.zip");
  await page.getByRole("button", { name: "← Back to platforms" }).click();
  assert.equal(await page.locator('[data-psm-action="upload"]').count(), 1);
  await page.getByRole("button", { name: "Add platform", exact: true }).click();
  assert.equal(await page.locator('[name="generation"]').evaluate((input) => input.files[0].name), "generation.zip");
  const submitBounds = await page.getByRole("button", { name: "Check & add platform" }).boundingBox();
  assert.ok(submitBounds.y >= 0 && submitBounds.y + submitBounds.height <= 1000, "Upload action stays visible while editing packages");
  if (process.env.PSM_SCREENSHOT) {
    await page.screenshot({ path: process.env.PSM_SCREENSHOT.replace(/\.png$/, "-upload.png") });
    await page.evaluate(() => document.documentElement.classList.add("light"));
    await page.locator(".psm-dialog .btn").first().evaluate((node) => getComputedStyle(node).backgroundColor);
    await page.waitForTimeout(250);
    await page.screenshot({ path: process.env.PSM_SCREENSHOT.replace(/\.png$/, "-upload-light.png") });
    await page.evaluate(() => document.documentElement.classList.remove("light"));
  }
  await page.setViewportSize({ width: 390, height: 844 });
  assert.ok(await page.locator(".psm-dialog").evaluate((dialog) => dialog.scrollWidth <= dialog.clientWidth + 1));
  assert.equal(await page.locator('[name="transformation"]').evaluate((input) => input.files[0].name), "transformation.zip");
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.getByRole("button", { name: "Check & add platform" }).click();
  await page.locator("#psm-feedback").filter({ hasText: "Your files and draft have been kept" }).waitFor();
  assert.equal(await page.getByLabel("Platform name", { exact: true }).inputValue(), "Azure");
  assert.equal(await page.locator('[name="ecore"]').evaluate((input) => input.files[0].name), "azure.ecore");
  await page.getByRole("button", { name: "Check & add platform" }).click();
  await page.locator("#psm-feedback").filter({ hasText: "Azure is ready" }).waitFor();
  assert.equal(await page.locator('[data-psm-action="generate"], [data-psm-action="validate"]').count(), 0);
  await page.getByRole('tab', { name: 'Visual notation', exact: true }).click();
  await page.getByRole('heading', { name: 'Azure visual notation', exact: true }).waitFor();
  await page.getByLabel('Palette group 1 name', { exact: true }).fill('Compute');
  await page.getByLabel('Palette group 1 name', { exact: true }).press('Tab');
  const functionRow = page.locator('[data-type-row="AzureFunction"]');
  await functionRow.getByRole('button', { name: 'Icon: category', exact: true }).click();
  await page.getByRole('option', { name: 'functions', exact: true }).click();
  await page.getByRole('button', { name: 'Add view', exact: true }).click();
  await page.getByLabel('View 2 name', { exact: true }).fill('Functions view');
  await page.getByRole('button', { name: /Save visual notation/ }).click();
  await page.getByText('Visual notation saved', { exact: true }).waitFor();
  assert.equal(savedNotation.elements.find((element) => element.type === 'AzureFunction').icon, 'functions');
  assert.equal(savedNotation.views.length, 2);
  assert.equal(savedNotation.paletteGroups[0].name, 'Compute');
  if (process.env.PSM_SCREENSHOT) await page.screenshot({ path: process.env.PSM_SCREENSHOT.replace(/\.png$/, '-notation.png') });
  await page.setViewportSize({ width: 390, height: 844 });
  assert.ok(await page.locator('.psm-dialog').evaluate((dialog) => dialog.scrollWidth <= dialog.clientWidth + 1));
  await page.getByRole('button', { name: 'Close cloud platforms' }).click();
  await page.setViewportSize({ width: 1440, height: 1000 });
  const chooseProvider = async (name) => {
    await page.locator('#psmGenerateProvider').getByRole('button', { name: /^Cloud provider:/ }).click();
    const bounds = await page.getByRole('listbox', { name: 'Cloud provider', exact: true }).boundingBox();
    assert.ok(bounds.width >= 240);
    await page.getByRole('option', { name, exact: true }).click();
  };
  await page.evaluate(async () => (await import('/js/cloud-providers.js')).invalidateCloudDefinition('azure'));
  const definitionUrl = '**/api/projects/project/psm/packages/azure/definition';
  await page.route(definitionUrl, async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 700));
    await route.continue();
  });
  await chooseProvider('Azure');
  await page.getByRole('status').filter({ hasText: 'Preparing Azure workspace…' }).waitFor();
  assert.equal(await page.locator('#modelTabs').evaluate((node) => node.inert), true);
  await page.locator('.cloud-provider-loading').waitFor({ state: 'detached' });
  assert.equal(await page.locator('#modelTabs').evaluate((node) => node.inert), false);
  await page.unroute(definitionUrl);
  await page.waitForFunction(() => document.querySelector('#generateContextBtn').title.includes('Azure'));
  await page.getByRole('button', { name: 'Generate Azure PSM', exact: true }).waitFor();
  assert.match(await page.locator('#generateContextBtn').getAttribute('title'), /Azure/);
  assert.equal(await page.locator('#modelTabs [data-type="psm"]').textContent(), 'Azure');
  await page.evaluate(async () => {
    const { state } = await import('/js/state.js');
    state.tabs.psm.baseModel.location = 'eastus';
    state.tabs.psm.dirty = true;
    state.undo.diagramHistory.psm = [{ provider: 'azure' }];
    state.paletteSearch.psm = 'azure function';
  });
  await chooseProvider('AWS · Built in');
  await page.waitForFunction(() => document.querySelector('#generateContextBtn').title.includes('AWS'));
  assert.equal(await page.locator('#generateContextBtn .topbar-btn-label').textContent(), 'Generate AWS PSM');
  assert.deepEqual(await page.evaluate(async () => (await import('/js/state.js')).state.undo.diagramHistory.psm), []);
  assert.equal(await page.evaluate(async () => (await import('/js/state.js')).state.paletteSearch.psm), '');
  await chooseProvider('Azure');
  await page.waitForFunction(() => document.querySelector('#generateContextBtn').title.includes('Azure'));
  assert.equal(await page.locator('#generateContextBtn .topbar-btn-label').textContent(), 'Generate Azure PSM');
  assert.equal(await page.evaluate(async () => (await import('/js/state.js')).state.tabs.psm.baseModel.location), 'eastus');
  assert.deepEqual(await page.evaluate(async () => (await import('/js/state.js')).state.undo.diagramHistory.psm), [{ provider: 'azure' }]);
  assert.equal(await page.evaluate(async () => (await import('/js/state.js')).state.paletteSearch.psm), 'azure function');
  const loadArtifact = () => page.evaluate(async () => {
    await (await import('/js/artifact.js')).loadCurrentProjectArtifact();
    const { state } = await import('/js/state.js');
    return { id: state.artifact.id, files: state.artifact.files };
  });
  assert.deepEqual(await loadArtifact(), { id: 'azure-artifact', files: [{ path: 'azure.txt' }] });
  await chooseProvider('AWS · Built in');
  await page.waitForFunction(() => document.querySelector('#generateContextBtn').title.includes('AWS'));
  await page.evaluate(async () => (await import('/js/cloud-providers.js')).invalidateCloudDefinition('azure'));
  await page.route(definitionUrl, async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 700));
    await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'Provider is temporarily unavailable' }) });
  });
  await chooseProvider('Azure');
  await page.locator('.cloud-provider-loading').waitFor();
  await page.locator('.cloud-provider-loading').waitFor({ state: 'detached' });
  assert.equal(await page.locator('#modelTabs').evaluate((node) => node.inert), false);
  assert.equal(await page.locator('#psmGenerateProvider select').inputValue(), 'aws');
  assert.equal(await page.locator('#generateContextBtn').isEnabled(), true);
  await page.unroute(definitionUrl);
  await chooseProvider('AWS · Built in');
  await page.waitForFunction(() => document.querySelector('#generateContextBtn').title.includes('AWS'));
  assert.deepEqual(await loadArtifact(), { id: 'aws-artifact', files: [{ path: 'aws.txt' }] });
  await chooseProvider('Azure');
  await page.waitForFunction(() => document.querySelector('#generateContextBtn').title.includes('Azure'));
  assert.deepEqual(await loadArtifact(), { id: 'azure-artifact', files: [{ path: 'azure.txt' }] });
  assert.equal(await page.locator('.psm-dialog').evaluate((dialog) => dialog.open), false);
  await page.evaluate(() => {
    const tab = document.createElement('button');
    tab.className = 'tab';
    tab.dataset.type = 'artifact';
    tab.textContent = 'Artifacts';
    document.getElementById('modelTabs').append(tab);
  });
  for (const level of ['cim', 'pim', 'psm', 'artifact']) {
    await page.evaluate(async (level) => {
      const { state } = await import('/js/state.js');
      state.activeType = level;
      (await import('/js/cloud-providers.js')).renderCloudProviderControls();
    }, level);
    assert.equal(await page.locator('#psmTabProvider').isVisible(), level === 'psm');
    assert.equal(await page.locator('#artifactTabProvider').isVisible(), level === 'artifact');
    assert.equal(await page.locator('#psmGenerateProvider').isVisible(), level === 'pim');
  }
  assert.deepEqual(errors, []);
  console.log("PSM browser workflow passed: package-only manager, upload cancellation and draft/retry, notation groups/icons/views, themed provider selectors, per-provider draft preservation and mobile layout.");
} finally {
  await browser.close();
  server.close();
}
