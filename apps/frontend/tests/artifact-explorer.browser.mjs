// Run explicitly: node apps/frontend/tests/artifact-explorer.browser.mjs
// Exercise file/folder creation, artifact ZIP import and artifact ZIP download in the real editor.
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { extname, resolve } from "node:path";
import { chromium } from "../../landing/node_modules/playwright/index.mjs";

const root = resolve("apps/frontend");
const requests = [];
let currentFiles = ["src/generated.go", "docs/guide.md"];
let currentDirectories = ["src", "docs"];
const record = {
  id: "artifact",
  projectId: "project",
  name: "Generated project",
  modelJson: {
    psmPackageId: "aws",
    generationRules: { "src/generated.go": "ExampleGenerator" },
  },
  filePaths: ["src/generated.go", "docs/guide.md"],
  directoryPaths: ["src", "docs"],
};
const harness = `
  import { state } from '/js/state.js';
  import { applyModelingRuntimeConfig } from '/js/config.js';
  import { initializeModelingRuntimeState } from '/js/modeling-config-data.js';
  import { loadArtifactRecord } from '/js/artifact.js';
  import { el } from '/js/dom.js';
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
  await loadArtifactRecord(${JSON.stringify(record)}, { collapseTree: false });
  await import('/js/main.js');
  await switchTab('artifact', { skipSnapshot: true });
  window.testState = state;
  window.testEl = el;
  window.artifactExplorerReady = true;
`;

function currentRecord(paths, directories = ["src", "docs"]) {
  return { ...record, filePaths: paths, directoryPaths: directories };
}

const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, "http://localhost");
    if (url.pathname.startsWith("/api/")) {
      const chunks = [];
      for await (const chunk of request) chunks.push(chunk);
      const body = Buffer.concat(chunks);
      requests.push({ method: request.method, path: url.pathname, body });
      response.setHeader("Access-Control-Allow-Origin", "*");
      response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Auth-Token");
      response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
      if (request.method === "OPTIONS") {
        response.writeHead(204).end();
        return;
      }
      response.setHeader("Content-Type", "application/json");
      if (url.pathname === "/api/artifact/artifact/file") {
        response.setHeader("Content-Type", "text/plain");
        response.end(`// ${url.searchParams.get("path")}`);
      } else if (url.pathname.endsWith("/directories") && request.method === "POST") {
        const path = JSON.parse(body.toString("utf8")).path;
        currentDirectories = [...new Set([...currentDirectories, path])];
        response.end(
          JSON.stringify(currentRecord(currentFiles, currentDirectories)),
        );
      } else if (url.pathname.endsWith("/files") && request.method === "POST") {
        const path = JSON.parse(body.toString("utf8")).path;
        currentFiles = [...new Set([...currentFiles, path])];
        response.end(
          JSON.stringify(currentRecord(currentFiles, currentDirectories)),
        );
      } else if (url.pathname.endsWith("/files") && request.method === "PUT") {
        response.end(JSON.stringify(currentRecord(currentFiles, currentDirectories)));
      } else if (url.pathname.endsWith("/rename") && request.method === "PUT") {
        const { sourcePath, newName } = JSON.parse(body.toString("utf8"));
        const separator = sourcePath.lastIndexOf("/");
        const parent = separator < 0 ? "" : sourcePath.slice(0, separator);
        const destination = parent ? `${parent}/${newName}` : newName;
        if (currentFiles.includes(sourcePath)) {
          currentFiles = currentFiles.map((path) => path === sourcePath ? destination : path);
        } else {
          const prefix = `${sourcePath}/`;
          currentDirectories = currentDirectories.map((path) =>
            path === sourcePath || path.startsWith(prefix)
              ? `${destination}${path.slice(sourcePath.length)}`
              : path,
          );
          currentFiles = currentFiles.map((path) =>
            path.startsWith(prefix) ? `${destination}${path.slice(sourcePath.length)}` : path,
          );
        }
        response.end(JSON.stringify(currentRecord(currentFiles, currentDirectories)));
      } else if (url.pathname.endsWith("/archive")) {
        currentFiles = ["src/generated.go", "src/user-added/notes.md"];
        currentDirectories = ["src", "src/user-added", "src/user-added/empty"];
        response.end(
          JSON.stringify(
            currentRecord(currentFiles, currentDirectories),
          ),
        );
      } else if (url.pathname.endsWith("/download")) {
        response.setHeader("Content-Type", "application/zip");
        response.end(Buffer.from("PK\\u0003\\u0004"));
      } else {
        response.end("{}");
      }
      return;
    }
    if (url.pathname === "/") {
      const html = (await readFile(resolve(root, "index.html"), "utf8"))
        .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, "");
      response.setHeader("Content-Type", "text/html");
      response.end(
        html.replace(
          "</body>",
          `<script src="/vendor/monaco/vs/loader.js"></script><script type="module">${harness}</script></body>`,
        ),
      );
      return;
    }
    const path = resolve(root, `.${url.pathname}`);
    if (!path.startsWith(`${root}\\`) && !path.startsWith(`${root}/`)) {
      response.writeHead(403).end();
      return;
    }
    response.setHeader(
      "Content-Type",
      ({ ".js": "text/javascript", ".mjs": "text/javascript", ".css": "text/css", ".svg": "image/svg+xml", ".ttf": "font/ttf" })[
        extname(path)
      ] || "application/octet-stream",
    );
    let content = await readFile(path);
    if (url.pathname === "/js/main.js") {
      const source = content.toString();
      const bootstrap = source.lastIndexOf("\ninit().catch(");
      assert.ok(bootstrap >= 0);
      content = `${source.slice(0, bootstrap)}\nbindEvents();\n`;
    }
    response.end(content);
  } catch (error) {
    response.writeHead(500).end(error.message);
  }
});

await new Promise((done) => server.listen(0, "127.0.0.1", done));
let browser;
try {
  browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1500, height: 1000 }, acceptDownloads: true });
  page.setDefaultTimeout(10000);
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  page.on("requestfailed", (request) => {
    if (request.failure()?.errorText !== "net::ERR_ABORTED") {
      errors.push(`${request.url()}: ${request.failure()?.errorText}`);
    }
  });
  page.on("response", (response) => {
    if (response.status() >= 400) errors.push(`HTTP ${response.status()}: ${response.url()}`);
    if (response.url().endsWith(".js") || response.url().includes(".js?")) {
      void response.allHeaders().then((headers) => {
        if (!(headers["content-type"] || "").includes("javascript")) {
          errors.push(`Wrong module content type: ${response.url()} ${headers["content-type"]}`);
        }
      });
    }
  });
  const dialogs = [];
  page.on("dialog", async (dialog) => {
    dialogs.push(dialog.type());
    await dialog.accept();
  });
  await page.goto(`http://127.0.0.1:${server.address().port}`);
  await page.waitForFunction(() => window.artifactExplorerReady).catch((error) => {
    throw new Error(`${error.message}\n${errors.join("\n")}`);
  });
  assert.equal(
    (await page.locator("#generateContextBtn .topbar-btn-label").textContent()).trim(),
    "Download Artifacts",
  );
  assert.equal(await page.locator("#artifactNewFileBtn").count(), 0);
  assert.equal(await page.locator("#artifactNewFolderBtn").count(), 0);

  await page.locator('#fileTree .file-tree-dir[data-directory-path="src"]').click({ button: "right" });
  await page.locator("#artifactContextMenu").waitFor({ state: "visible" });
  assert.equal(
    await page.locator("#artifactContextMenu").evaluate((menu) => {
      const bounds = menu.getBoundingClientRect();
      const topmost = document.elementFromPoint(bounds.left + 4, bounds.top + 4);
      return menu.contains(topmost) && Number(getComputedStyle(menu).zIndex) >= 1600;
    }),
    true,
  );
  assert.equal(await page.locator("#artifactContextNewFileBtn").evaluate((element) => element === document.activeElement), true);
  await page.locator("#artifactContextMenu").press("ArrowDown");
  assert.equal(await page.locator("#artifactContextNewFolderBtn").evaluate((element) => element === document.activeElement), true);
  await page.locator("#artifactContextMenu").press("Escape");
  await page.locator("#artifactContextMenu").waitFor({ state: "hidden" });
  await page.locator('#fileTree .file-tree-dir[data-directory-path="docs"]').click();
  assert.equal(
    await page.locator('#fileTree .file-tree-dir[data-directory-path="docs"]').evaluate((directory) =>
      directory.parentElement.querySelector(".file-tree-children").style.display),
    "none",
  );
  await page.locator('#fileTree .file-tree-dir[data-directory-path="src"]').click({ button: "right" });
  await page.locator("#artifactContextMenu").waitFor({ state: "visible" });
  await page.locator("#artifactContextNewFileBtn").click();
  await page.locator("#artifactCreateOverlay").waitFor({ state: "visible" });
  assert.equal(dialogs.includes("prompt"), false);
  await page.locator("#artifactCreateNameInput").fill("../invalid.txt");
  await page.locator("#artifactCreateSubmitBtn").click();
  assert.match(await page.locator("#artifactCreateError").textContent(), /single name/i);
  await page.locator("#artifactCreateNameInput").fill("created.txt");
  await page.locator("#artifactCreateSubmitBtn").click();
  await page
    .waitForFunction(() => window.testState.artifact.activeFile === "src/created.txt")
    .catch(async (error) => {
      throw new Error(`${error.message}\ndialogs=${dialogs}\n${errors.join("\n")}\n${requests.map((item) => `${item.method} ${item.path}`).join("\n")}`);
    });
  assert.equal(await page.locator(".file-tree-item[data-path='src/created.txt']").count(), 1);
  assert.equal(
    await page.locator('#fileTree .file-tree-dir[data-directory-path="docs"]').evaluate((directory) =>
      directory.parentElement.querySelector(".file-tree-children").style.display),
    "none",
  );

  await page.locator(".file-tree-item[data-path='src/generated.go']").click({ button: "right" });
  await page.locator("#artifactContextMenu").waitFor({ state: "visible" });
  assert.equal(await page.locator("#artifactContextRenameBtn").isDisabled(), true);
  await page.locator("#artifactContextMenu").press("Escape");

  await page.locator(".file-tree-item[data-path='src/created.txt']").click({ button: "right" });
  await page.locator("#artifactContextRenameBtn").click();
  await page.locator("#artifactCreateOverlay").waitFor({ state: "visible" });
  assert.equal(await page.locator("#artifactCreateTitle").textContent(), "Rename File");
  assert.equal(await page.locator("#artifactCreateNameInput").inputValue(), "created.txt");
  await page.locator("#artifactCreateNameInput").fill("updated.txt");
  await page.locator("#artifactCreateSubmitBtn").click();
  await page.waitForFunction(() => window.testState.artifact.activeFile === "src/updated.txt");
  assert.equal(await page.locator(".file-tree-item[data-path='src/created.txt']").count(), 0);
  await page.evaluate(() => {
    const model = window.monaco.editor.getModels().at(-1);
    model.setValue("user-edited artifact content");
  });
  const artifactSaveResponse = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/artifact/artifact/files") &&
      response.request().method() === "PUT",
  );
  await page.keyboard.press("Control+s");
  await artifactSaveResponse;
  const artifactSaveRequest = requests.find(
    (request) => request.method === "PUT" && request.path === "/api/artifact/artifact/files",
  );
  assert.deepEqual(JSON.parse(artifactSaveRequest.body.toString("utf8")), {
    path: "src/updated.txt",
    content: "user-edited artifact content",
  });

  await page.locator('#fileTree .file-tree-dir[data-directory-path="src"]').click({ button: "right" });
  await page.locator("#artifactContextNewFolderBtn").click();
  await page.locator("#artifactCreateNameInput").fill("empty");
  await page.locator("#artifactCreateSubmitBtn").click();
  await page.waitForFunction(() => window.testState.artifact.directories.includes("src/empty"));
  assert.equal(
    await page.locator('#fileTree .file-tree-dir[data-directory-path="src/empty"]').evaluate((directory) =>
      directory.parentElement.querySelector(".file-tree-children").style.display),
    "none",
  );
  await page.locator('#fileTree .file-tree-dir[data-directory-path="src/empty"]').click({ button: "right" });
  await page.locator("#artifactContextRenameBtn").click();
  await page.locator("#artifactCreateNameInput").fill("renamed-empty");
  await page.locator("#artifactCreateSubmitBtn").click();
  await page.waitForFunction(() => window.testState.artifact.directories.includes("src/renamed-empty"));
  assert.equal(
    await page.evaluate(() => window.testState.artifact.directories.includes("src/empty")),
    false,
  );
  assert.equal(
    await page.locator('#fileTree .file-tree-dir[data-directory-path="docs"]').evaluate((directory) =>
      directory.parentElement.querySelector(".file-tree-children").style.display),
    "none",
  );
  assert.equal(
    await page.locator('#fileTree .file-tree-dir[data-directory-path="src/renamed-empty"]').evaluate((directory) =>
      directory.parentElement.querySelector(".file-tree-children").style.display),
    "none",
  );

  assert.match(await page.locator("#fileTree").textContent(), /renamed-empty/);

  await page.locator("#artifactTreeToggleBtn").click();
  await page.locator("#artifactExplorerRoot").click({ button: "right" });
  await page.locator("#artifactContextNewFolderBtn").click();
  await page.locator("#artifactCreateNameInput").fill("manual");
  await page.locator("#artifactCreateSubmitBtn").click();
  await page.waitForFunction(() => window.testState.artifact.directories.includes("manual"));
  assert.equal(await page.locator("#artifactTreeToggleBtn").getAttribute("title"), "Expand folders");
  for (const path of ["src", "docs"]) {
    assert.equal(
      await page.locator(`#fileTree .file-tree-dir[data-directory-path="${path}"]`).evaluate((directory) =>
        directory.parentElement.querySelector(".file-tree-children").style.display),
      "none",
    );
  }
  assert.equal(
    await page.locator('#fileTree .file-tree-dir[data-directory-path="docs"]').evaluate((directory) =>
      directory.parentElement.querySelector(".file-tree-children").style.display),
    "none",
  );
  const createdFileRequest = requests.find(
    (item) => item.method === "POST" && item.path === "/api/artifact/artifact/files",
  );
  assert.equal(JSON.parse(createdFileRequest.body.toString("utf8")).path, "src/created.txt");
  const createdDirectories = requests
    .filter((item) => item.method === "POST" && item.path === "/api/artifact/artifact/directories")
    .map((item) => JSON.parse(item.body.toString("utf8")).path);
  assert.deepEqual(createdDirectories, ["src/empty", "manual"]);
  const renameRequests = requests.filter(
    (item) => item.method === "PUT" && item.path === "/api/artifact/artifact/rename",
  );
  assert.deepEqual(
    renameRequests.map((item) => JSON.parse(item.body.toString("utf8"))),
    [
      { sourcePath: "src/created.txt", newName: "updated.txt" },
      { sourcePath: "src/empty", newName: "renamed-empty" },
    ],
  );

  await page.locator("#artifactImportBtn").click();
  await page.locator("#artifactArchiveInput").setInputFiles({
    name: "local-refinement.zip",
    mimeType: "application/zip",
    buffer: Buffer.from("zip fixture"),
  });
  const importConfirmation = page.locator("#confirmActionOverlay");
  await importConfirmation.waitFor({ state: "visible" });
  assert.match(await importConfirmation.textContent(), /Files missing from the ZIP will be removed/);
  await page.locator("#confirmActionConfirmBtn").click();
  await page.getByRole("status").filter({ hasText: "Imported local-refinement.zip" }).waitFor();
  assert.equal(await page.locator(".file-tree-item[data-path='src/user-added/notes.md']").count(), 1);
  assert.match(await page.locator("#fileTree").textContent(), /empty/);

  const download = page.waitForEvent("download");
  await page.locator("#generateContextBtn").click();
  assert.match((await download).suggestedFilename(), /Generated project\.zip/);
  assert.ok(requests.some((item) => item.path === "/api/artifact/artifact/archive"));
  assert.ok(requests.some((item) => item.path === "/api/artifact/artifact/download"));
  assert.ok(requests.some((item) => item.path === "/api/artifact/artifact/directories"));
  assert.equal(await page.locator("#downloadProjectZipBtn").count(), 0);
  assert.deepEqual(dialogs, []);
  assert.deepEqual(errors, []);
  console.log("Artifact explorer browser checks passed: Ctrl+S save, expansion preservation, rename, file/folder creation, ZIP import and artifact ZIP download.");
} finally {
  await browser?.close();
  await new Promise((done) => server.close(done));
}
