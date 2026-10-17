// Explicit integration check against a running Compose stack. Uses an isolated guest/project,
// uploads through the real workbench, and cleans up the test project afterwards.
import assert from "node:assert/strict";
import { resolve } from "node:path";
import { chromium } from "../../landing/node_modules/playwright/index.mjs";

const backend = process.env.PSM_BACKEND_URL || "http://127.0.0.1:8080";
const editor = process.env.PSM_EDITOR_URL || "http://editor.localhost:8088";
const sample = resolve("methodology/modeling-framework/samples/custom-psm-azure");
let token;
let project;
let browser;
let page;
async function api(path, method = "GET", body) {
  const response = await fetch(`${backend}/api${path}`, {
    method, headers: { ...(token ? { "X-Auth-Token": token } : {}), ...(body ? { "Content-Type": "application/json" } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) throw new Error(`${method} ${path} returned ${response.status}`);
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}
try {
  const deadline = Date.now() + 60000;
  while (true) {
    try {
      const health = await fetch(`${backend}/api/health`, { signal: AbortSignal.timeout(2000) });
      if (health.ok) break;
    } catch { /* Backend may still be starting after a rebuild. */ }
    if (Date.now() >= deadline) throw new Error("Backend did not become ready within 60 seconds.");
    await new Promise((ready) => setTimeout(ready, 1000));
  }
  const guest = await api("/auth/guest", "POST", {});
  token = guest.token;
  project = await api("/projects", "POST", { name: "PSM deployed upload check", description: "Temporary browser integration fixture" });
  await api("/pim", "POST", { projectId: project.id, name: "AzureDemo", model: {
    eClass: "PIMModel", name: "AzureDemo", domainName: "Azure example", architectureStyle: "WORKFLOW_ORCHESTRATED_SERVERLESS",
    services: [{ eClass: "ServerlessService", id: "hello-service", name: "HelloService",
      functions: [{ eClass: "Function", id: "hello-function", name: "Hello", stateless: true }] }],
  } });
  browser = await chromium.launch({ headless: true });
  page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  page.setDefaultTimeout(60000);
  await page.addInitScript(({ token, projectId, userId }) => {
    localStorage.setItem("modriss.authToken", token);
    localStorage.setItem(`modriss.lastProjectId:${userId.toLowerCase()}`, projectId);
  }, { token, projectId: project.id, userId: guest.user.id });
  const editorResponse = await page.goto(editor);
  assert.equal(editorResponse.status(), 200, 'The editor server must be ready before running the browser workflow');
  await page.locator("#projectLabel").filter({ hasText: project.name }).waitFor({ state: "attached" });
  await page.evaluate(async () => { window.__psmTestState = (await import('/js/state.js')).state; });
  await page.waitForFunction(() => window.__psmTestState.activeType === 'pim' && window.__psmTestState.modelId);
  assert.equal(await page.locator(".workspace-rail #psmPlatformsBtn").count(), 1);
  assert.equal(await page.locator(".topbar #psmPlatformsBtn").count(), 0);
  await page.getByRole("button", { name: "Cloud platforms", exact: true }).click();
  assert.equal(await page.locator("#psm-source-pim, #psm-create-form").count(), 0);
  await page.getByRole("button", { name: "Add platform", exact: true }).click();
  await page.getByLabel("Platform name", { exact: true }).fill("Azure");
  await page.getByLabel("Ecore metamodel").setInputFiles(resolve(sample, "azure.ecore"));
  await page.getByLabel("ETL package", { exact: true }).setInputFiles(resolve(sample, "transformation.zip"));
  await page.locator('[name="transformationEntry"] option[value="main.etl"]').waitFor({ state: "attached" });
  await page.getByLabel("EGX / EGL package", { exact: true }).setInputFiles(resolve(sample, "generation.zip"));
  await page.locator('[name="generationEntry"] option[value="main.egx"]').waitFor({ state: "attached" });
  await page.getByLabel("EVL package", { exact: true }).setInputFiles(resolve(sample, "validation.zip"));
  await page.getByRole("button", { name: "Close cloud platforms" }).click();
  await page.getByRole("button", { name: "Cloud platforms", exact: true }).click();
  await page.getByRole("heading", { name: "Cloud platform packages", exact: true }).waitFor();
  assert.equal(await page.locator("#psm-upload-form").count(), 0);
  await page.getByRole("button", { name: "Add platform", exact: true }).click();
  assert.equal(await page.getByLabel("Platform name", { exact: true }).inputValue(), "Azure");
  assert.equal(await page.locator('[name="ecore"]').evaluate((input) => input.files[0].name), "azure.ecore");
  for (const name of ["ecore", "transformation", "generation", "validation"]) {
    await page.locator(`#psm-upload-form [name="${name}"]`).dispatchEvent("cancel", { bubbles: true });
    assert.equal(await page.locator(".psm-dialog").evaluate((dialog) => dialog.open), true);
  }
  if (process.env.PSM_SCREENSHOT) await page.screenshot({ path: process.env.PSM_SCREENSHOT });
  await page.getByRole("button", { name: "Check & add platform" }).click();
  await page.locator("#psm-feedback").filter({ hasText: "Azure is ready" }).waitFor();
  await page.getByRole("button", { name: "Close cloud platforms" }).click();
  const chooseProvider = async (holder, name) => {
    await page.locator(holder).getByRole("button", { name: /^Cloud provider:/ }).click();
    await page.getByRole("option", { name, exact: true }).click();
  };
  const waitForEditor = async (provider) => page.waitForFunction((provider) => {
    const state = window.__psmTestState;
    return state.activeType === 'psm' && state.modelingConfig.config.levels.psm.displayName === provider
      && state.modelId === state.tabs.psm.modelId && state.baseModel === state.tabs.psm.baseModel
      && !document.querySelector('#psmTabProvider .attr-custom-select-trigger')?.disabled;
  }, provider);
  await page.locator('#modelTabs [data-type="psm"]').click();
  await waitForEditor('AWS');
  assert.equal(await page.locator('.psm-dialog').evaluate((dialog) => dialog.open), false);
  await page.locator('#modelTabs [data-type="pim"]').click();
  await page.waitForFunction(() => window.__psmTestState.activeType === 'pim');
  await chooseProvider('#psmGenerateProvider', 'Azure');
  await page.waitForFunction(() => document.querySelector('#generateContextBtn').title.includes('Azure'));
  const psmButton = page.getByRole('button', { name: 'Generate Azure PSM', exact: true });
  assert.equal((await psmButton.boundingBox()).height, 26);
  await psmButton.click();
  await page.waitForFunction(() => {
    const state = window.__psmTestState;
    return state.activeType === 'psm' && state.modelId && state.diagram.nodes.some((node) => node.type === 'AzureFunction');
  });
  const generated = await api(`/projects/${project.id}/psm/branches`);
  assert.equal(generated.length, 1);
  const modelId = generated[0].id;
  const notationDefaults = await page.evaluate(async () => {
    const { state } = await import('/js/state.js');
    const level = state.modelingConfig.config.levels.psm;
    return { views: level.views.length, groups: [...new Set(level.elements.map((element) => element.category))], icons: level.elements.map((element) => element.icon), nodes: state.diagram.nodes.length };
  });
  assert.equal(notationDefaults.views, 1);
  assert.equal(notationDefaults.nodes, 1);
  assert.deepEqual(notationDefaults.groups, ['Resources']);
  assert.ok(notationDefaults.icons.every((icon) => icon === 'category'));
  assert.equal(await page.locator('.psm-dialog').evaluate((dialog) => dialog.open), false);
  if (process.env.PSM_SCREENSHOT) await page.screenshot({ path: process.env.PSM_SCREENSHOT.replace(/\.png$/, '-canvas.png') });
  await chooseProvider('#psmTabProvider', 'AWS · Built in');
  await waitForEditor('AWS');
  await page.evaluate(async () => {
    const { invalidateCloudDefinition } = await import('/js/cloud-providers.js');
    const packages = await (await import('/js/api.js')).api(`/projects/${window.__psmTestState.project.id}/psm/packages`);
    invalidateCloudDefinition(packages.find((item) => item.name === 'Azure').id);
  });
  const definitionUrl = '**/api/projects/*/psm/packages/*/definition';
  await page.route(definitionUrl, async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 800));
    await route.continue();
  });
  await chooseProvider('#psmTabProvider', 'Azure');
  await page.getByRole('status').filter({ hasText: 'Loading Azure model…' }).waitFor();
  assert.equal(await page.locator('.workspace-main').getAttribute('aria-busy'), 'true');
  if (process.env.PSM_SCREENSHOT) await page.screenshot({ path: process.env.PSM_SCREENSHOT.replace(/\.png$/, '-model-loading.png') });
  await waitForEditor('Azure');
  await page.locator('.cloud-provider-loading').waitFor({ state: 'detached' });
  await page.unroute(definitionUrl);
  assert.equal(await page.evaluate(async () => (await import('/js/state.js')).state.modelId), modelId);
  await page.evaluate(async () => {
    const { state } = await import('/js/state.js');
    const { openAttributePanel } = await import('/js/attr-panel.js');
    openAttributePanel(state.diagram.nodes.find((node) => node.type === 'AzureFunction').id);
  });
  await page.getByRole('button', { name: 'Business Fields', exact: true }).click();
  const runtime = page.locator('#attrPanelBody [data-attr-key="runtime"]');
  await runtime.fill('unsupported');
  await runtime.press('Tab');
  await page.locator('#attrPanelApplyBtn').click();
  await page.locator('#saveModelBtn').click();
  await page.waitForFunction(() => window.__psmTestState.modelRevision >= 2);
  await page.locator('#validateModelBtn').click();
  await page.getByText(/SupportedRuntime:/).first().waitFor();
  await runtime.fill('nodejs20');
  await runtime.press('Tab');
  await page.locator('#attrPanelApplyBtn').click();
  await page.locator('#saveModelBtn').click();
  await page.waitForFunction(() => window.__psmTestState.modelRevision >= 3);
  await page.locator('#validateModelBtn').click();
  await page.waitForFunction(() => !window.__psmTestState.validation.inProgress && window.__psmTestState.validation.issues.length === 0);
  await page.locator('#generateContextBtn').click();
  await page.waitForFunction(() => {
    const state = window.__psmTestState;
    return state.activeType === 'artifact' && state.artifact.id;
  });
  const artifacts = await api(`/projects/${project.id}/psm/branches/${modelId}/artifacts`);
  assert.ok(artifacts[0].filePaths.includes('azure-application.txt'));
  assert.ok(artifacts[0].filePaths.includes('functions/Hello.txt'));
  const summaries = await api(`/artifact?projectId=${project.id}`);
  const packageId = generated[0].packageId;
  assert.equal(summaries.find((item) => item.id === artifacts[0].id).cloudProviderId, packageId);
  await page.evaluate(async () => {
    await (await import('/js/artifact.js')).openArtifactFile('azure-application.txt');
    const model = window.monaco.editor.getModels().find((model) => model.uri.scheme === 'artifact' && model.uri.path.endsWith('azure-application.txt'));
    model.setValue('Unsaved test edit');
  });
  assert.equal(await page.evaluate(() => window.__psmTestState.artifact.dirty), true);
  await chooseProvider('#artifactTabProvider', 'AWS · Built in');
  await page.getByRole('button', { name: 'Cancel', exact: true }).click();
  await page.waitForFunction(() => !document.querySelector('#artifactTabProvider .attr-custom-select-trigger').disabled);
  assert.equal(await page.evaluate(() => window.__psmTestState.artifact.dirty), true);
  assert.equal(await page.evaluate(() => window.__psmTestState.artifact.id), artifacts[0].id);
  await chooseProvider('#artifactTabProvider', 'AWS · Built in');
  await page.getByRole('button', { name: 'Discard & switch', exact: true }).click();
  await page.waitForFunction(() => window.__psmTestState.activeType === 'artifact' && window.__psmTestState.cloudProvider.id === 'aws' && !window.__psmTestState.artifact.id);
  await page.locator('#artifactExplorerRoot').filter({ hasText: 'No artifacts yet' }).waitFor();
  const artifactUrl = '**/api/artifact?projectId=*';
  await page.route(artifactUrl, async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 800));
    await route.continue();
  });
  await chooseProvider('#artifactTabProvider', 'Azure');
  await page.getByRole('status').filter({ hasText: 'Loading Azure artifacts…' }).waitFor();
  if (process.env.PSM_SCREENSHOT) await page.screenshot({ path: process.env.PSM_SCREENSHOT.replace(/\.png$/, '-artifacts-loading.png') });
  await page.waitForFunction((id) => window.__psmTestState.activeType === 'artifact' && window.__psmTestState.artifact.id === id, artifacts[0].id);
  await page.locator('.cloud-provider-loading').waitFor({ state: 'detached' });
  assert.equal(await page.locator('.workspace-main').evaluate((node) => node.inert), false);
  await page.unroute(artifactUrl);
  assert.equal(await page.locator('#modelTabs [data-type="psm"]').textContent(), 'Azure');
  if (process.env.PSM_SCREENSHOT) await page.screenshot({ path: process.env.PSM_SCREENSHOT.replace(/\.png$/, '-artifacts.png') });
  await page.locator('#modelTabs [data-type="psm"]').click();
  await waitForEditor('Azure');
  await page.getByRole('button', { name: 'Cloud platforms', exact: true }).click();
  await page.locator('.psm-provider-card').filter({ has: page.getByText('Azure', { exact: true }) }).click();
  await page.getByRole('tab', { name: 'Visual notation', exact: true }).click();
  await page.getByRole('heading', { name: 'Azure visual notation', exact: true }).waitFor();
  await page.getByLabel('Palette group 1 name', { exact: true }).fill('Compute');
  await page.getByRole('button', { name: 'Add view', exact: true }).click();
  await page.getByLabel('View 2 name', { exact: true }).fill('Functions view');
  await page.getByRole('button', { name: /Save visual notation/ }).click();
  await page.getByText('Visual notation saved', { exact: true }).waitFor();
  await page.getByRole('button', { name: 'Close cloud platforms' }).click();
  await page.locator('#saveModelBtn').click();
  await page.waitForFunction(() => !window.__psmTestState.modelSave.dirty);
  await page.reload();
  await page.evaluate(async () => { window.__psmTestState = (await import('/js/state.js')).state; });
  await waitForEditor('Azure');
  assert.equal(await page.evaluate(async () => (await import('/js/state.js')).state.modelId), modelId);
  assert.equal(await page.evaluate(async () => (await import('/js/state.js')).state.modelingConfig.config.levels.psm.views.length), 2);
  await page.locator('#modelTabs [data-type="pim"]').click();
  await page.getByRole('button', { name: 'Generate Azure PSM', exact: true }).click();
  await page.getByRole('button', { name: 'Regenerate model', exact: true }).click();
  await waitForEditor('Azure');
  await page.waitForFunction(() => window.__psmTestState.modelRevision >= 5);
  const regenerated = await api(`/projects/${project.id}/psm/branches`);
  assert.equal(regenerated.length, 1);
  assert.equal(regenerated[0].id, modelId);
  assert.equal((await api(`/projects/${project.id}/psm/branches/${modelId}/artifacts`)).length, 1);
  if (process.env.PSM_SCREENSHOT) await page.screenshot({ path: process.env.PSM_SCREENSHOT.replace(/\.png$/, '-final.png') });
  console.log('Deployed PSM workflow passed: provider tab/editor and generation selectors, Ecore canvas, properties/save/EVL/artifacts, notation customization, reload restoration, one-model regeneration and artifact history.');
} catch (error) {
  if (page && process.env.PSM_SCREENSHOT) {
    await page.screenshot({ path: process.env.PSM_SCREENSHOT.replace(/\.png$/, "-failure.png") });
    console.error("Visible workspace status:", await page.locator("#psm-feedback, #statusText, #projectLabel").allTextContents());
  }
  throw error;
} finally {
  await browser?.close();
  if (project) await api(`/projects/${project.id}`, "DELETE");
}
