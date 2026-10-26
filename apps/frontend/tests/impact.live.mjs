// Explicit integration test: running backend + PostgreSQL, actual case-study ETL/EGX,
// and the real editor in Chromium. The isolated guest project is deleted afterwards.
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { readFile, mkdir, writeFile } from "node:fs/promises";
import { resolve, extname } from "node:path";
import { chromium } from "../../landing/node_modules/playwright/index.mjs";

const backend = process.env.IMPACT_BACKEND_URL || "http://127.0.0.1:8080";
const root = resolve("apps/frontend");
const output = resolve("apps/backend/target/impact-verification");
let token, project, browser, server, page;
async function api(path, method = "GET", body) {
  const multipart = body instanceof FormData;
  const response = await fetch(`${backend}/api${path}`, {
    method, headers: { ...(token ? { "X-Auth-Token": token } : {}),
      ...(body && !multipart ? { "Content-Type": "application/json" } : {}) },
    body: body ? multipart ? body : JSON.stringify(body) : undefined,
  });
  if (!response.ok) throw new Error(`${method} ${path}: ${response.status} ${await response.text()}`);
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}
async function transform(route, source) {
  const start = Date.now();
  const submitted = await api(`/transformations/${route}`, "POST", {
    sourceModelId: source.id, expectedRevision: source.revision,
  });
  for (;;) {
    const job = await api(`/transformations/jobs/${submitted.id}`);
    if (job.status === "SUCCEEDED") {
      console.log(`${route} succeeded in ${Date.now() - start} ms`);
      return job;
    }
    if (["FAILED", "CANCELLED"].includes(job.status)) throw new Error(JSON.stringify(job));
    if (Date.now() - start > 600000) throw new Error(`${route} timed out`);
    await new Promise((done) => setTimeout(done, 1000));
  }
}
const key = (item) => JSON.stringify([item.modelId, item.elementId]);
try {
  await mkdir(output, { recursive: true });
  const readyDeadline = Date.now() + 60000;
  for (;;) {
    try {
      const response = await fetch(`${backend}/api/health`, { signal: AbortSignal.timeout(2000) });
      if (response.ok) break;
    } catch { /* A freshly built backend can still be starting. */ }
    if (Date.now() > readyDeadline) throw new Error("Verification backend did not start within 60 seconds");
    await new Promise((done) => setTimeout(done, 1000));
  }
  const guest = await api("/auth/guest", "POST", {});
  token = guest.token;
  project = await api("/projects", "POST", { name: "Climate grants impact verification", description: "Temporary real trace-chain regression" });
  const form = new FormData();
  form.set("projectId", project.id);
  form.set("format", "xmi");
  form.set("file", new Blob([await readFile(resolve("methodology/modeling-framework/samples/climate-relief-grants/climate-relief-grants.cim.xmi"))]), "climate-relief-grants.cim.xmi");
  const imported = await api("/cim/import", "POST", form);
  const cim = await api("/cim", "POST", { projectId: project.id, name: imported.name, model: imported.modelJson });
  const pimJob = await transform("cim-to-pim", cim);
  const pim = await api(`/pim/${pimJob.resultModelId}`);
  const psmJob = await transform("pim-to-psm", pim);
  const psm = await api(`/psm/${psmJob.resultModelId}`);
  const artifactJob = await transform("psm-to-artifact", psm);
  const artifact = await api(`/artifact/${artifactJob.resultArtifactId}`);
  assert.equal(artifact.modelJson.traceabilityVersion, 2);
  const start = Date.now();
  const impact = await api(`/impact/cim/${cim.id}/element/cmd-reject`);
  const elapsed = Date.now() - start;
  const timings = [elapsed];
  for (let sample = 0; sample < 3; sample += 1) {
    const sampleStart = Date.now();
    await api(`/impact/cim/${cim.id}/element/cmd-reject`);
    timings.push(Date.now() - sampleStart);
  }
  assert.equal(impact.focalElement.elementName, "RejectEmergencyGrant");
  assert.equal(new Set(impact.downstream.map(key)).size, impact.downstream.length);
  assert.ok(impact.downstream.some((item) => item.modelType === "PIM"));
  assert.ok(impact.downstream.some((item) => item.modelType === "PSM"));
  const files = impact.downstream.filter((item) => item.modelType === "ARTIFACT");
  assert.ok(files.length > 0);
  for (const file of files) {
    assert.ok(Object.hasOwn(artifact.files, file.elementId));
    const origins = impact.traceEdges.filter((edge) => edge.targetModelId === file.modelId && edge.targetElementId === file.elementId);
    assert.ok(origins.length > 0);
    for (const edge of origins) assert.ok(artifact.modelJson.traceability[edge.sourceElementId].includes(file.elementId));
  }
  // Every displayed model node must be justified by a real stored ETL trace or generatedFrom.
  const targetModels = new Map([[pim.id, pim], [psm.id, psm]]);
  function objects(value, predicate, found = []) {
    if (value && typeof value === "object") {
      if (!Array.isArray(value) && predicate(value)) found.push(value);
      for (const child of Object.values(value)) objects(child, predicate, found);
    }
    return found;
  }
  for (const edge of impact.traceEdges.filter((edge) => targetModels.has(edge.targetModelId))) {
    const model = targetModels.get(edge.targetModelId).modelJson;
    assert.ok(objects(model, (node) => node.eClass === "TraceLink" && node.sourceElementId === edge.sourceElementId && node.targetElementId === edge.targetElementId).length ||
      objects(model, (node) => node.id === edge.targetElementId && node.generatedFrom === edge.sourceElementId).length,
    `Unrecorded trace ${JSON.stringify(edge)}`);
  }
  const file = files.find((item) => item.elementId.endsWith("handler.go")) || files[0];
  const reverse = await api(`/impact/artifact/${artifact.id}?filePath=${encodeURIComponent(file.elementId)}`);
  const directIds = Object.entries(artifact.modelJson.traceability)
    .filter(([, paths]) => paths.includes(file.elementId)).map(([id]) => id).sort();
  assert.deepEqual(reverse.upstream.filter((item) => item.modelId === psm.id).map((item) => item.elementId).sort(), directIds);
  assert.equal(directIds.length, 1, "The handler source must be the Lambda alone, even when other resources share its trace key");
  assert.equal(reverse.upstream.find((item) => item.modelId === psm.id).elementType, "AwsLambdaFunction");
  assert.ok(reverse.traceEdges.some((edge) => edge.transformationRule === "AWSPSM2ART_Lambda_To_Handler"));
  assert.deepEqual(impact.notices, []);
  console.log(`RejectEmergencyGrant: ${impact.downstream.length - files.length} model elements, ${files.length} unique files, ${impact.traceEdges.length} edges; API samples ${timings.join(", ")} ms.`);
  await writeFile(resolve(output, "case-study.json"), JSON.stringify({ impact, reverse, elapsed, timings }, null, 2));

  // Serve current source files and proxy API requests to the verification backend.
  server = createServer(async (request, response) => {
    try {
      const url = new URL(request.url, "http://localhost");
      if (url.pathname.startsWith("/api/")) {
        const chunks = [];
        for await (const chunk of request) chunks.push(chunk);
        const upstream = await fetch(`${backend}${url.pathname}${url.search}`, {
          method: request.method, headers: { "X-Auth-Token": request.headers["x-auth-token"] || "",
            "Content-Type": request.headers["content-type"] || "application/json" },
          body: chunks.length ? Buffer.concat(chunks) : undefined,
        });
        response.writeHead(upstream.status, { "Content-Type": upstream.headers.get("content-type") || "application/json" });
        response.end(Buffer.from(await upstream.arrayBuffer()));
        return;
      }
      if (url.pathname === "/backend-config.js") {
        response.setHeader("Content-Type", "text/javascript");
        response.end('window.MODRISS_BACKEND_BASE_URL = ""; window.MODRISS_DOCS_URL = "";');
        return;
      }
      const path = resolve(root, `.${url.pathname === "/" ? "/index.html" : url.pathname}`);
      if (!path.startsWith(`${root}\\`) && !path.startsWith(`${root}/`)) { response.writeHead(403).end(); return; }
      response.setHeader("Content-Type", ({ ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".svg": "image/svg+xml", ".json": "application/json", ".ttf": "font/ttf" })[extname(path)] || "application/octet-stream");
      response.end(await readFile(path));
    } catch { response.writeHead(404).end(); }
  });
  await new Promise((done) => server.listen(0, "127.0.0.1", done));
  browser = await chromium.launch({ headless: true });
  page = await browser.newPage({ viewport: { width: 1600, height: 1100 } });
  page.setDefaultTimeout(60000);
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.addInitScript(({ token, projectId, userId }) => {
    localStorage.setItem("modriss.authToken", token);
    localStorage.setItem(`modriss.lastProjectId:${userId.toLowerCase()}`, projectId);
    localStorage.setItem("modriss.editorTutorial:guest", JSON.stringify({ completed: true }));
  }, { token, projectId: project.id, userId: guest.user.id });
  await page.goto(`http://127.0.0.1:${server.address().port}`);
  await page.evaluate(async () => { window.__impactState = (await import("/js/state.js")).state; });
  await page.waitForFunction(() => Boolean(window.__impactState.project && window.__impactState.modelId &&
    window.__impactState.modelingConfig.config?.artifact?.key));
  await page.evaluate(async (cimId) => {
    await (await import("/js/model-ops.js")).loadModelById("cim", cimId, { autoLayout: false });
    await (await import("/js/canvas.js")).locateCanvasTarget({ id: "cmd-reject" });
  }, cim.id);
  // Use the real impact toggle and canvas selection handler.
  await page.locator("#impactToggleBtn").click();
  await page.waitForTimeout(400);
  const clickPoint = await page.evaluate(async () => {
    const { state } = await import("/js/state.js");
    const node = state.diagram.nodes.find((item) => item.id === "cmd-reject");
    const mapped = (await import("/js/graph-editor/g6-mapper.js")).mapNodeToG6(node);
    return (await import("/js/graph-editor/g6-editor.js")).toClientCoordinates(mapped.style.x, mapped.style.y);
  });
  await page.mouse.click(clickPoint.x, clickPoint.y);
  await page.locator(".impact-focal-name").filter({ hasText: "RejectEmergencyGrant" }).waitFor();
  assert.equal(await page.locator(".impact-stats b").nth(0).textContent(), "0");
  assert.equal(await page.locator(".impact-result-card").count(), impact.downstream.length);
  assert.equal(await page.locator(".impact-tree-children").count(), 0);
  const firstGroup = page.locator(".impact-group").first();
  assert.equal(await firstGroup.locator(".impact-result-card:visible").count(), 6);
  await firstGroup.locator("[data-impact-more]").click();
  assert.equal(await firstGroup.locator(".impact-result-card:visible").count(), 7);
  const firstEvidence = firstGroup.locator(".impact-evidence").first();
  await firstEvidence.locator("summary").click();
  assert.ok((await firstEvidence.locator("code").allTextContents()).every(Boolean));
  await page.screenshot({ path: resolve(output, "climate-grants-impact.png") });
  await page.getByRole("button", { name: `Show ${files.length} generated files`, exact: true }).click();
  await page.locator('[data-impact-files="true"]').scrollIntoViewIfNeeded();
  await page.screenshot({ path: resolve(output, "generated-file-traces.png") });
  const filter = page.getByRole("searchbox", { name: "Filter impact results" });
  await filter.fill("no-such-file-or-element");
  assert.equal(await page.locator(".impact-result-card").count(), 0);
  await filter.fill(file.elementId);
  assert.equal(await page.locator(".impact-result-card").count(), 1);
  await page.getByRole("button", { name: `Open file ${file.elementId}`, exact: true }).click();
  await page.locator(".impact-focal-name").filter({ hasText: file.elementId }).waitFor();
  assert.equal(await page.locator(".impact-result-card").count(), reverse.upstream.length);
  await page.screenshot({ path: resolve(output, "file-upstream-traces.png") });
  const locate = page.locator('.impact-result-card [data-impact-action="model"]').first();
  const targetId = await locate.getAttribute("data-element-id");
  await locate.click();
  await page.waitForFunction((id) => window.__impactState.issueLocateTargetId === id, targetId);
  assert.equal(await page.locator("#impactPanel").isVisible(), true);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: resolve(output, "impact-mobile.png") });
  const overflowing = await page.locator("#impactPanel").evaluate((panel) => panel.scrollWidth > panel.clientWidth);
  assert.equal(overflowing, false);
  await page.setViewportSize({ width: 1600, height: 1100 });
  await page.evaluate(async (id) => {
    await (await import("/js/model-ops.js")).loadModelById("cim", id, { autoLayout: false });
  }, cim.id);
  await page.locator("#impactToggleBtn").click();
  const delayed = `**/api/impact/cim/${cim.id}/element/cmd-reject`;
  await page.route(delayed, async (route) => {
    await new Promise((done) => setTimeout(done, 350));
    await route.fulfill({ json: impact }).catch(() => {});
  });
  await page.evaluate(async () => {
    const { fetchImpact } = await import("/js/impact.js");
    void fetchImpact("cmd-reject");
    void fetchImpact("cmd-approve");
  });
  await page.locator(".impact-focal-name").filter({ hasText: "ApproveEmergencyGrant" }).waitFor();
  await page.waitForTimeout(450);
  assert.equal(await page.locator(".impact-focal-name").textContent(), "ApproveEmergencyGrant");
  await page.evaluate(async () => { void (await import("/js/impact.js")).fetchImpact("cmd-reject"); });
  await page.locator("#impactPanelCloseBtn").click();
  await page.waitForTimeout(450);
  assert.equal(await page.locator("#impactPanel").isVisible(), false);
  assert.equal(await page.evaluate(async () => (await import("/js/state.js")).state.impactData), null);
  await page.unroute(delayed);
  await page.route(delayed, (route) => route.fulfill({ status: 503, json: { message: "Trace service unavailable" } }));
  await page.locator("#impactToggleBtn").click();
  await page.evaluate(async () => { await (await import("/js/impact.js")).fetchImpact("cmd-reject"); });
  assert.equal(await page.locator(".impact-retry").isVisible(), true);
  assert.equal(await page.locator("#impactPanelBody").getAttribute("aria-busy"), null);
  await page.unroute(delayed);
  await page.locator(".impact-retry").click();
  await page.locator(".impact-focal-name").filter({ hasText: "RejectEmergencyGrant" }).waitFor();
  assert.deepEqual(errors, []);
  console.log("Real editor checks passed: canvas selection, counts, trace rules, pagination, filtering, file opening, all contributors, cross-level locate, mobile width, request races, close during load, and failure/retry.");
} catch (error) {
  if (page) {
    await page.screenshot({ path: resolve(output, "failure.png") }).catch(() => {});
    console.error(await page.evaluate(async () => {
      const { state } = await import("/js/state.js");
      return { type: state.activeType, modelId: state.modelId, config: Boolean(state.modelingConfig.config),
        artifactKey: state.modelingConfig.config?.artifact?.key, configError: state.modelingConfig.error?.message,
        status: document.querySelector(".toast-message")?.textContent };
    }).catch(() => "Browser state unavailable"));
  }
  throw error;
} finally {
  await browser?.close();
  if (server) await new Promise((done) => server.close(done));
  if (project) await api(`/projects/${project.id}`, "DELETE").catch((error) => console.error(`Project cleanup failed: ${error.message}`));
}
