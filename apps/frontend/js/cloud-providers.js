import { state } from "./state.js";
import { api } from "./api.js";
import { emptyDiagram, escapeHtml } from "./utils.js";
import { initializeModelingRuntimeState } from "./modeling-config-data.js";
import { defaultRootModel, toDiagram, serializeModel } from "./diagram.js";
import { saveCurrentTabGraphState, syncActiveViewFromVisibleGraph } from "./graph-store.js";
import { enhanceSelects } from "./themed-select.js";
import { defaultPsmNotation, inferPsmLevel } from "./psm-notation.js";
import { setError } from "./status.js";

let nativeLevel;
let projectId;
let selected = "aws";
let platforms = [];
let models = [];
const tabs = new Map();
const definitions = new Map();
let changing = false;
const key = () =>
  `modriss.cloudProvider:${state.auth?.user?.id || "anonymous"}:${state.project?.id}`;
export const customPsmSelected = () => selected !== "aws" && projectId === state.project?.id;
export const customPsmActive = () => state.activeType === "psm" && customPsmSelected();
export const selectedCloudProvider = () =>
  platforms.find((platform) => platform.id === selected) || { id: "aws", name: "AWS" };
export const psmEndpoint = (suffix = "") => `/projects/${state.project.id}/psm${suffix}`;

function tabFor(record) {
  return {
    modelId: record?.id || null,
    modelRevision: record?.revision || 0,
    baseModel: record?.modelJson || defaultRootModel("psm", selectedCloudProvider().name),
    diagram: record ? toDiagram("psm", record.modelJson, record.name) : emptyDiagram("psm"),
    modelName: record?.name || `${selectedCloudProvider().name} model`,
    graph: null,
    views: null,
    fragments: null,
    activeViewId: null,
    dirty: false,
  };
}

export function resetCloudProviders() {
  if (nativeLevel && state.modelingConfig.config) {
    state.modelingConfig.config.levels.psm = structuredClone(nativeLevel);
    initializeModelingRuntimeState(state.modelingConfig.config);
  }
  projectId = null;
  selected = "aws";
  state.cloudProvider = { projectId: null, id: "aws" };
  platforms = [];
  models = [];
  tabs.clear();
  definitions.clear();
  renderCloudProviderControls();
}

export async function loadCloudProviders({ restore = false, open = false } = {}) {
  nativeLevel ||= structuredClone(state.modelingConfig.config.levels.psm);
  if (projectId !== state.project?.id) {
    resetCloudProviders();
    projectId = state.project?.id;
  }
  if (!projectId) return;
  [platforms, models] = await Promise.all([
    api(psmEndpoint("/packages")),
    api(psmEndpoint("/branches")),
  ]);
  if (restore) {
    const remembered = localStorage.getItem(key());
    if (platforms.some((platform) => platform.id === remembered))
      await selectCloudProvider(remembered, { open: open || state.activeType === "psm" });
  }
  renderCloudProviderControls();
}

export async function cloudDefinition(id) {
  if (!definitions.has(id))
    definitions.set(id, await api(psmEndpoint(`/packages/${id}/definition`)));
  return definitions.get(id);
}

export function invalidateCloudDefinition(id) {
  definitions.delete(id);
}

export async function applyCloudNotation(id) {
  if (selected !== id) return;
  const definition = await cloudDefinition(id);
  const tab = state.tabs.psm;
  if (state.activeType === "psm") {
    tab.baseModel = serializeModel({ syncView: true, reconcileRelationships: true });
    tab.dirty = Boolean(tab.dirty || state.modelId);
  }
  const level = inferPsmLevel(
    selectedCloudProvider(),
    definition.schema,
    definition.visualSyntax || defaultPsmNotation(definition.schema),
  );
  if (tab.baseModel && tab.baseModel.eClass !== level.rootTemplate.eClass)
    throw new Error(
      "The saved notation uses another root type. Regenerate or import a compatible model before changing its root.",
    );
  state.modelingConfig.config.levels.psm = level;
  initializeModelingRuntimeState(state.modelingConfig.config);
  tab.graph = null;
  tab.views = null;
  tab.diagram = toDiagram("psm", tab.baseModel, tab.modelName);
  if (state.activeType === "psm")
    await (await import("./model-ops.js")).switchTab("psm", { skipSnapshot: true });
  renderCloudProviderControls();
}

function showProviderLoading(name, destination) {
  const workspace = document.getElementById("workspace");
  const overlay = document.createElement("div");
  overlay.className = "cloud-provider-loading";
  overlay.setAttribute("role", "status");
  overlay.setAttribute("aria-live", "polite");
  overlay.setAttribute("aria-atomic", "true");
  const label =
    destination === "artifact"
      ? `Loading ${name} artifacts…`
      : destination === "psm"
        ? `Loading ${name} model…`
        : `Preparing ${name} workspace…`;
  overlay.innerHTML = `<div class="cloud-provider-loading-card"><span class="cloud-provider-loading-spinner" aria-hidden="true"></span><div><strong>${escapeHtml(label)}</strong><p>Please wait while your workspace is prepared.</p></div></div>`;
  const controls = [
    ...document.querySelectorAll(
      ".workspace-main, #modelTabs, #topbarModelingRow, #generateContextBtn",
    ),
  ];
  const previousInert = controls.map((node) => node.inert);
  controls.forEach((node) => {
    node.inert = true;
  });
  const content = document.querySelector(".workspace-main");
  const previousBusy = content?.getAttribute("aria-busy");
  content?.setAttribute("aria-busy", "true");
  (workspace || document.body).append(overlay);
  return () => {
    overlay.remove();
    controls.forEach((node, index) => {
      node.inert = previousInert[index];
    });
    if (previousBusy == null) content?.removeAttribute("aria-busy");
    else content?.setAttribute("aria-busy", previousBusy);
  };
}

export async function selectCloudProvider(
  id,
  { open = false, openLevel = open ? "psm" : null, reload = false } = {},
) {
  if (changing) return;
  if (id !== "aws" && !platforms.some((platform) => platform.id === id))
    throw new Error("Cloud provider is no longer available in this project.");
  const unchanged = id === selected && !reload;
  if (unchanged && !openLevel) return;
  changing = true;
  let finishLoading;
  try {
    if (!unchanged && state.artifact.dirty) {
      const { confirmAction } = await import("./confirm-action.js");
      const confirmed = await confirmAction({
        title: "Switch cloud provider?",
        message:
          "The open artifact file has unsaved changes. Switching providers discards these edits.",
        confirmLabel: "Discard & switch",
      });
      if (!confirmed) return false;
    }
    const replacingActivePsm = state.activeType === "psm";
    const destination =
      openLevel ||
      (replacingActivePsm ? "psm" : state.activeType === "artifact" ? "artifact" : null);
    const platform = platforms.find((item) => item.id === id);
    finishLoading = showProviderLoading(platform?.name || "AWS", destination);
    renderCloudProviderControls();
    // Let the loading state paint before cached models or notation are rendered.
    await new Promise((resolve) => requestAnimationFrame(() => setTimeout(resolve, 0)));
    if (unchanged) {
      await (await import("./model-ops.js")).switchTab(openLevel);
      return;
    }
    // Resolve everything before changing the selected provider so a failed load
    // leaves the currently open editor and unsaved model intact.
    const definition = id === "aws" ? null : await cloudDefinition(id);
    const nextLevel = definition
      ? inferPsmLevel(
          platform,
          definition.schema,
          definition.visualSyntax || defaultPsmNotation(definition.schema),
        )
      : structuredClone(nativeLevel);
    let record;
    if (!tabs.has(id) || reload) {
      const model = models.find((model) => model.packageId === id);
      if (model) record = await api(psmEndpoint(`/branches/${model.id}/editor`));
    }
    if (state.activeType === "psm") {
      syncActiveViewFromVisibleGraph();
      state.tabs.psm.modelId = state.modelId;
      state.tabs.psm.modelRevision = state.modelRevision;
      state.tabs.psm.baseModel = state.baseModel;
      state.tabs.psm.diagram = state.diagram;
      saveCurrentTabGraphState("psm");
    }
    state.tabs.psm.cloudUi = {
      undoHistory: state.undo.diagramHistory.psm || [],
      modelReplacements: state.undo.modelReplacements.filter(
        (snapshot) => snapshot.typeKey === "psm",
      ),
      paletteSearch: state.paletteSearch.psm || "",
      paletteGroups: state.paletteGroupCollapsed.psm || {},
    };
    tabs.set(selected, state.tabs.psm);
    (await import("./artifact.js")).clearArtifactState();
    selected = id;
    state.cloudProvider = { projectId, id };
    state.modelingConfig.config.levels.psm = nextLevel;
    initializeModelingRuntimeState(state.modelingConfig.config);
    const nextTab = reload || !tabs.has(id) ? tabFor(record) : tabs.get(id);
    state.tabs.psm = nextTab;
    state.undo.diagramHistory.psm = nextTab.cloudUi?.undoHistory || [];
    state.undo.modelReplacements = [
      ...state.undo.modelReplacements.filter((snapshot) => snapshot.typeKey !== "psm"),
      ...(nextTab.cloudUi?.modelReplacements || []),
    ];
    state.paletteSearch.psm = nextTab.cloudUi?.paletteSearch || "";
    state.paletteGroupCollapsed.psm = nextTab.cloudUi?.paletteGroups || {};
    tabs.set(id, nextTab);
    localStorage.setItem(key(), selected);
    if (destination)
      await (
        await import("./model-ops.js")
      ).switchTab(destination, { skipSnapshot: replacingActivePsm });
    renderCloudProviderControls();
    window.dispatchEvent(new Event("modriss-cloud-provider-change"));
  } finally {
    finishLoading?.();
    changing = false;
    renderCloudProviderControls();
  }
}

export function rememberCloudModel(record, providerId = selected) {
  models = [
    { ...record, packageId: providerId },
    ...models.filter((model) => model.packageId !== providerId),
  ];
}

export async function confirmPsmRegeneration() {
  const existing = models.find((model) => model.packageId === selected);
  if (existing || state.tabs.psm?.dirty) {
    const { confirmAction } = await import("./confirm-action.js");
    return confirmAction({
      title: `Regenerate ${selectedCloudProvider().name} model`,
      message:
        "This platform keeps one model. Regeneration replaces its current contents with the transformation result. Existing artifact history stays available.",
      confirmLabel: "Regenerate model",
    });
  }
  return true;
}

export async function generateSelectedPsm(sourceId, revision) {
  const provider = selectedCloudProvider();
  const existing = models.find((model) => model.packageId === provider.id);
  const branch = await api(psmEndpoint("/branches"), {
    method: "POST",
    body: JSON.stringify({
      packageId: selected,
      sourcePimId: sourceId,
      expectedRevision: revision,
      expectedTargetRevision: existing?.revision || 0,
      name: existing?.name || `${selectedCloudProvider().name} model`,
    }),
  });
  rememberCloudModel(branch, provider.id);
  const record = await api(psmEndpoint(`/branches/${branch.id}/editor`));
  tabs.delete(provider.id);
  return record;
}

function selector(parent, id, openLevel) {
  let holder = document.getElementById(id);
  if (!holder) {
    holder = document.createElement("div");
    holder.id = id;
    holder.className = "cloud-provider-control";
    holder.innerHTML = '<select aria-label="Cloud provider" data-menu-width="260"></select>';
    parent.append(holder);
    holder.querySelector("select").addEventListener("change", (event) => {
      void selectCloudProvider(event.target.value, { openLevel }).catch((error) => {
        renderCloudProviderControls();
        setError(error, { prefix: "Could not switch cloud provider." });
      });
    });
  }
  const select = holder.querySelector("select");
  select.innerHTML = `<option value="aws">AWS · Built in</option>${platforms.map((platform) => `<option value="${escapeHtml(platform.id)}">${escapeHtml(platform.name)}</option>`).join("")}`;
  select.value = selected;
  select.disabled = !state.project?.id || changing;
  enhanceSelects(holder);
  holder.querySelector(".attr-custom-select-trigger").title =
    id === "artifactTabProvider"
      ? "Choose a cloud provider for artifacts"
      : id === "psmGenerateProvider"
        ? "Choose which PSM to generate"
        : "Choose a cloud provider to edit";
  return holder;
}

export function renderCloudProviderControls() {
  const tab = document.querySelector('#modelTabs .tab[data-type="psm"]');
  if (!tab) return;
  tab.textContent = selectedCloudProvider().name;
  tab.title = `Open ${selectedCloudProvider().name} editor`;
  if (!tab.parentElement.classList.contains("cloud-tab-split")) {
    const wrapper = document.createElement("div");
    wrapper.className = "cloud-tab-split";
    tab.before(wrapper);
    wrapper.append(tab);
  }
  selector(tab.parentElement, "psmTabProvider", "psm").hidden = state.activeType !== "psm";
  const artifactTab = document.querySelector('#modelTabs .tab[data-type="artifact"]');
  if (artifactTab) {
    artifactTab.title = `Open ${selectedCloudProvider().name} artifacts`;
    if (!artifactTab.parentElement.classList.contains("cloud-tab-split")) {
      const wrapper = document.createElement("div");
      wrapper.className = "cloud-tab-split";
      artifactTab.before(wrapper);
      wrapper.append(artifactTab);
    }
    selector(artifactTab.parentElement, "artifactTabProvider", "artifact").hidden =
      state.activeType !== "artifact";
  }
  document.querySelectorAll("#modelTabs .tab").forEach((item) => {
    if (item.classList.contains("active")) item.setAttribute("aria-current", "page");
    else item.removeAttribute("aria-current");
  });
  const generate = document.getElementById("generateContextBtn");
  if (generate) {
    if (!generate.parentElement.classList.contains("cloud-generate-split")) {
      const wrapper = document.createElement("div");
      wrapper.className = "cloud-generate-split";
      generate.before(wrapper);
      wrapper.append(generate);
    }
    const control = selector(generate.parentElement, "psmGenerateProvider", null);
    control.hidden = state.activeType !== "pim";
    if (state.activeType === "pim") {
      const label = generate.querySelector(".topbar-btn-label");
      if (label) label.textContent = `Generate ${selectedCloudProvider().name} PSM`;
      generate.title = `Generate the ${selectedCloudProvider().name} model from this PIM`;
      if (customPsmSelected() && selectedCloudProvider().hasTransformation === false) {
        generate.disabled = true;
        generate.title =
          "This provider has no ETL package. Create or import its model in the PSM editor.";
      } else generate.disabled = false;
    } else if (customPsmActive()) {
      const label = generate.querySelector(".topbar-btn-label");
      if (label) label.textContent = "Generate artifacts";
      generate.disabled = selectedCloudProvider().hasGeneration === false;
      generate.title = generate.disabled
        ? "This provider has no EGX / EGL generation package."
        : `Generate ${selectedCloudProvider().name} artifacts`;
    }
  }
}

window.addEventListener("modriss-active-modeling-level-change", () =>
  queueMicrotask(renderCloudProviderControls),
);
window.addEventListener("beforeunload", (event) => {
  if ([state.tabs.psm, ...tabs.values()].some((tab) => tab?.dirty)) {
    event.preventDefault();
    event.returnValue = "";
  }
});
