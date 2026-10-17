import { api } from "./api.js";
import { state } from "./state.js";
import { escapeHtml } from "./utils.js";
import { zipEntryNames, preferredEntry } from "./psm-package-utils.js";
import { enhanceSelects, closeThemedSelect } from "./themed-select.js";
import { renderPsmVisualEditor } from "./psm-visual-editor.js";
import { defaultPsmNotation } from "./psm-notation.js";
import {
  loadCloudProviders,
  cloudDefinition,
  invalidateCloudDefinition,
  applyCloudNotation,
} from "./cloud-providers.js";

const h = escapeHtml;
let dialog;
let projectId;
let packages = [];
let selectedPlatformId = "aws";
let busy = false;
let returnFocus;
const uploadDrafts = new Map();
const endpoint = (suffix) => `/projects/${projectId}/psm${suffix}`;
const button = (action, label, extra = "") =>
  `<button type="button" class="btn btn-secondary btn-sm" data-psm-action="${action}" ${extra}>${label}</button>`;
const draftKey = () => `${state.auth?.user?.id || "anonymous"}:${projectId}`;

function ensureDialog() {
  if (dialog) return;
  dialog = document.createElement("dialog");
  dialog.className = "psm-dialog";
  dialog.setAttribute("aria-labelledby", "psm-title");
  dialog.innerHTML = `<header class="psm-header"><div><span class="psm-eyebrow">PROJECT SETTINGS</span><h2 id="psm-title">Cloud platforms</h2><p>Add provider packages and customize their visual notation.</p></div>${button("close", "Close", 'aria-label="Close cloud platforms"')}</header>
    <div class="psm-feedback" id="psm-feedback" role="status" aria-live="polite" hidden></div>
    <div class="psm-manager-tabs" role="tablist" aria-label="Platform settings"><button role="tab" id="psm-packages-tab" aria-controls="psm-content" data-psm-action="overview" aria-selected="true">Packages</button><button role="tab" id="psm-notation-tab" aria-controls="psm-content" data-psm-action="notation" aria-selected="false">Visual notation</button></div>
    <div class="psm-layout"><nav class="psm-navigation" aria-label="Cloud platform packages"><div id="psm-package-nav"></div></nav><section class="psm-content" id="psm-content" role="tabpanel" aria-labelledby="psm-packages-tab"></section></div>`;
  document.body.append(dialog);
  dialog.addEventListener("click", (event) => {
    const target = event.target.closest("[data-psm-action]");
    if (target && !busy) void perform(() => action(target.dataset.psmAction, target.dataset.id));
  });
  dialog.addEventListener("cancel", (event) => {
    if (event.target !== dialog) return;
    event.preventDefault();
    if (!busy) close();
  });
  dialog.addEventListener("submit", (event) => {
    event.preventDefault();
    if (!busy && event.target.id === "psm-upload-form") void perform(() => upload(event.target));
  });
  dialog.addEventListener("change", (event) => {
    if (event.target.matches("[data-zip-kind]")) void perform(() => inspectZip(event.target));
  });
  dialog.querySelector(".psm-manager-tabs").addEventListener("keydown", (event) => {
    if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
    event.preventDefault();
    const tabs = [...dialog.querySelectorAll('[role="tab"]')];
    const index = tabs.indexOf(document.activeElement);
    const next =
      event.key === "Home"
        ? tabs[0]
        : event.key === "End"
          ? tabs.at(-1)
          : tabs[(index + 1) % tabs.length];
    next.focus();
    next.click();
  });
}

function feedback(message = "", error = false) {
  const node = dialog.querySelector("#psm-feedback");
  node.hidden = !message;
  node.textContent = message;
  node.classList.toggle("psm-error", error);
}

async function perform(work) {
  try {
    await work();
  } catch (error) {
    feedback(
      error.status === 503
        ? "Platform checking is temporarily unavailable. Your files and draft have been kept; please try again shortly."
        : error.message || String(error),
      true,
    );
  }
}

function keepUploadDraft() {
  const page = dialog?.querySelector("#psm-upload-page");
  if (page) uploadDrafts.set(page.dataset.draftKey, page);
}

function setView(view) {
  keepUploadDraft();
  closeThemedSelect();
  dialog.dataset.view = view;
  const notation = view === "notation";
  dialog.querySelectorAll('[role="tab"]').forEach((tab) => {
    const selected = notation === (tab.id === "psm-notation-tab");
    tab.setAttribute("aria-selected", String(selected));
    tab.tabIndex = selected ? 0 : -1;
  });
  dialog
    .querySelector("#psm-content")
    .setAttribute("aria-labelledby", notation ? "psm-notation-tab" : "psm-packages-tab");
  renderNavigation();
}

async function operation(label, work) {
  busy = true;
  dialog.setAttribute("aria-busy", "true");
  dialog.querySelectorAll("button, input, select, textarea").forEach((node) => {
    node.dataset.wasDisabled = String(node.disabled);
    node.disabled = true;
  });
  feedback(label);
  try {
    return await work();
  } finally {
    busy = false;
    dialog.removeAttribute("aria-busy");
    dialog.querySelectorAll("button, input, select, textarea").forEach((node) => {
      node.disabled = node.dataset.wasDisabled === "true";
      delete node.dataset.wasDisabled;
    });
    enhanceSelects(dialog);
  }
}

function close() {
  keepUploadDraft();
  closeThemedSelect();
  dialog.close();
  returnFocus?.focus();
}

export async function openPsmPlatforms() {
  if (!state.project?.id) {
    (await import("./status.js")).setStatus("Open a project to add cloud platforms.");
    return;
  }
  ensureDialog();
  keepUploadDraft();
  if (projectId !== state.project.id) {
    packages = [];
    selectedPlatformId = "aws";
  }
  projectId = state.project.id;
  returnFocus = document.activeElement;
  overview();
  if (!dialog.open) dialog.showModal();
  await perform(async () => {
    packages = await operation("Loading platform packages…", () => api(endpoint("/packages")));
    feedback();
    if (!packages.some((item) => item.id === selectedPlatformId)) selectedPlatformId = "aws";
    overview();
  });
}

function renderNavigation() {
  const platformItem = (id, name, note) =>
    `<button type="button" class="psm-nav-item ${id === selectedPlatformId ? "selected" : ""}" data-psm-action="package" data-id="${h(id)}" ${id === selectedPlatformId ? 'aria-current="page"' : ""}><span class="psm-nav-plus" aria-hidden="true"><img src="/assets/icons/process-map/cloud.svg" width="18" height="18" alt="" /></span><span class="psm-nav-copy"><strong>${h(name)}</strong><small>${h(note)}</small></span></button>`;
  dialog.querySelector("#psm-package-nav").innerHTML =
    `<div class="psm-models-heading"><h3>Project platforms</h3><span class="psm-model-count">${packages.length + 1}</span></div><p class="psm-nav-hint">Packages and visual notation</p>${platformItem("aws", "Amazon Web Services", "Built in")}${packages.map((platform) => platformItem(platform.id, platform.name, "Custom PSM package")).join("")}<div class="psm-navigation-note"><strong>Model in the workspace</strong><p>Use the provider dropdown beside the PSM tab to switch editors. Generate models from the PIM tab.</p></div>`;
}

function overview() {
  setView("overview");
  dialog.querySelector("#psm-content").innerHTML =
    `<div class="psm-section-heading"><div><h3>Cloud platform packages</h3><p>Add a PSM language and its Epsilon scripts. Each package has one model, edited in the main workspace.</p></div>${button("upload", "Add platform")}</div><div class="psm-provider-grid">${[{ id: "aws", name: "Amazon Web Services", description: "Built-in AWS language and generation pipeline." }, ...packages].map((platform) => `<button type="button" class="psm-provider-card" data-psm-action="package" data-id="${h(platform.id)}"><span class="psm-badge">${platform.id === "aws" ? "Built in" : "Project package"}</span><strong>${h(platform.name)}</strong><span class="psm-provider-description">${h(platform.description || "Custom language and model-driven scripts")}</span><small>View package ${platform.id === "aws" ? "" : "& visual notation"} →</small></button>`).join("")}</div>`;
}

async function showPackage(id) {
  selectedPlatformId = id;
  setView("package");
  const native = id === "aws";
  const platform = native
    ? {
        name: "Amazon Web Services",
        description: "The built-in AWS PSM language and scripts are maintained with the platform.",
      }
    : packages.find((item) => item.id === id);
  dialog.querySelector("#psm-content").innerHTML =
    `<div class="psm-section-heading"><div><h3>${h(platform.name)}</h3><p>${h(platform.description || "Project cloud package")}</p></div>${button("overview", "All packages")}</div>${native ? '<p class="psm-muted">Manage built-in concrete syntax in the admin Visual Syntax editor.</p>' : `<p class="psm-muted">This package is ready for the canvas with default notation. The metamodel and scripts are immutable; its visual notation can be changed at any time.</p><dl><dt>PIM → PSM entry script</dt><dd>${h(platform.transformationEntry || "Not supplied · manual modeling available")}</dd><dt>PSM → artifacts entry script</dt><dd>${h(platform.generationEntry || "Not supplied")}</dd><dt>Validation</dt><dd>${platform.hasValidation ? "EVL constraints included" : "Structural Ecore validation"}</dd></dl><div class="psm-actions">${button("notation", "Design visual notation")}</div>`}`;
}

async function showNotation() {
  setView("notation");
  const content = dialog.querySelector("#psm-content");
  if (selectedPlatformId === "aws" && packages.length) selectedPlatformId = packages[0].id;
  renderNavigation();
  if (selectedPlatformId === "aws") {
    content.innerHTML =
      "<h3>Visual notation</h3><p>Add a custom platform package to design its notation here. The built-in AWS notation is maintained in the admin Visual Syntax editor.</p>";
    return;
  }
  content.innerHTML = '<p class="psm-muted">Loading visual notation…</p>';
  const platform = packages.find((item) => item.id === selectedPlatformId);
  const definition = await cloudDefinition(platform.id);
  renderPsmVisualEditor(content, {
    platform: { ...platform, projectId },
    schema: definition.schema,
    syntax: definition.visualSyntax || defaultPsmNotation(definition.schema),
    save: async (syntax) => {
      await api(endpoint(`/packages/${platform.id}/notation`), {
        method: "PUT",
        body: JSON.stringify(syntax),
      });
      invalidateCloudDefinition(platform.id);
      await applyCloudNotation(platform.id);
    },
  });
}

function uploadForm() {
  setView("upload");
  const content = dialog.querySelector("#psm-content");
  const draft = uploadDrafts.get(draftKey());
  if (draft) {
    content.replaceChildren(draft);
    return;
  }
  content.innerHTML = `<div id="psm-upload-page" data-draft-key="${h(draftKey())}">
    ${button("overview", "← Back to platforms")}
    <div class="psm-section-heading"><div><h3>Add a cloud platform</h3><p>Upload an Ecore language to start modeling. Add script packages to enable transformation, code generation and semantic validation.</p></div></div>
    <form id="psm-upload-form"><div class="psm-form-grid"><label>Platform name<input name="name" placeholder="e.g. Azure" maxlength="120" required /></label><label>Description (optional)<input name="description" maxlength="1000" placeholder="What this platform supports" /></label></div>
    <div class="psm-package-grid">
    <section class="psm-package-field"><div class="psm-package-heading"><span class="psm-file-type">ECORE</span><h4>PSM metamodel</h4></div><p>The language that describes your cloud resources.</p><label>Ecore metamodel<input type="file" name="ecore" accept=".ecore" required /></label><small>One self-contained .ecore file · up to 5 MB</small></section>
    <section class="psm-package-field"><div class="psm-package-heading"><span class="psm-file-type">ETL</span><h4>PIM → PSM <span class="psm-optional">Optional</span></h4></div><p>Transform the shared architecture into your cloud model.</p><label>ETL package<input type="file" name="transformation" accept=".zip" data-zip-kind="transformation" /></label><label class="psm-entry-label" hidden>Entry script<select name="transformationEntry"><option value="">Choose an ETL ZIP first</option></select></label><small id="psm-transformation-files">ZIP containing ETL scripts · up to 10 MB</small></section>
    <section class="psm-package-field"><div class="psm-package-heading"><span class="psm-file-type">EGX / EGL</span><h4>PSM → artifacts <span class="psm-optional">Optional</span></h4></div><p>Generate code and files from your cloud model.</p><label>EGX / EGL package<input type="file" name="generation" accept=".zip" data-zip-kind="generation" /></label><label class="psm-entry-label" hidden>Entry script<select name="generationEntry"><option value="">Choose a generation ZIP first</option></select></label><small id="psm-generation-files">ZIP containing EGX scripts and EGL templates · up to 10 MB</small></section>
    <section class="psm-package-field"><div class="psm-package-heading"><span class="psm-file-type">EVL</span><h4>Model validation <span class="psm-optional">Optional</span></h4></div><p>Check cloud-specific constraints when you validate a PSM.</p><label>EVL package<input type="file" name="validation" accept=".zip" data-zip-kind="validation" /></label><small id="psm-validation-files">ZIP containing EVL constraints · up to 10 MB</small></section>
    </div><div class="psm-upload-footer"><span>Your draft stays here if you close this window.</span><button class="btn btn-primary" type="submit">Check &amp; add platform</button></div></form></div>`;
  keepUploadDraft();
  enhanceSelects(content);
  dialog.querySelector('[name="name"]').focus();
}

async function inspectZip(input) {
  const file = input.files[0];
  if (!file) return;
  if (file.size > 10 * 1024 * 1024) {
    input.value = "";
    throw new Error("Each ZIP must be at most 10 MB.");
  }
  const names = zipEntryNames(await file.arrayBuffer());
  const kind = input.dataset.zipKind;
  const extension = kind === "transformation" ? ".etl" : kind === "generation" ? ".egx" : ".evl";
  const entries = names.filter((name) => name.endsWith(extension));
  if (!entries.length || (kind === "generation" && !names.some((name) => name.endsWith(".egl")))) {
    input.value = "";
    throw new Error(
      kind === "generation"
        ? "Generation ZIP needs an EGX entry script and EGL templates."
        : `ZIP needs at least one ${extension} file.`,
    );
  }
  const select = input.form.elements[`${kind}Entry`];
  if (select) {
    select.innerHTML = entries
      .map((name) => `<option value="${h(name)}">${h(name)}</option>`)
      .join("");
    select.value = preferredEntry(names, extension);
    select.closest("label").hidden = entries.length < 2;
  }
  dialog.querySelector(`#psm-${kind}-files`).textContent =
    `${names.length} files${select ? ` · Starts at ${select.value}` : " · Ready"}`;
  feedback();
}

async function upload(form) {
  const data = new FormData(form);
  for (const kind of ["transformation", "generation", "validation"]) {
    if (!form.elements[kind].files.length) {
      data.delete(kind);
      data.delete(`${kind}Entry`);
    }
  }
  if (form.elements.ecore.files[0].size > 5 * 1024 * 1024)
    throw new Error("Ecore must be at most 5 MB.");
  const platform = await operation("Checking metamodel and Epsilon scripts…", () =>
    api(endpoint("/packages"), { method: "POST", body: data }),
  );
  dialog.querySelector("#psm-upload-page")?.remove();
  uploadDrafts.delete(draftKey());
  selectedPlatformId = platform.id;
  packages = await api(endpoint("/packages"));
  await loadCloudProviders();
  await showPackage(platform.id);
  feedback(
    `${platform.name} is ready. Select it beside the PSM tab to open its editor, or generate it from the PIM tab.`,
  );
}

async function action(name, id) {
  feedback();
  if (name === "close") return close();
  if (name === "overview") return overview();
  if (name === "upload") return uploadForm();
  if (name === "notation") return showNotation();
  if (name === "package") {
    selectedPlatformId = id;
    if (dialog.dataset.view === "notation") return showNotation();
    return showPackage(id);
  }
}

document
  .getElementById("psmPlatformsBtn")
  ?.addEventListener("click", () => void openPsmPlatforms());
