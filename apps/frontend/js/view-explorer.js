import {state} from './state.js';
import {el} from './dom.js';
import {escapeHtml} from './utils.js';
import {setStatus} from './status.js';
import {
  activeView,
  ensureActiveGraphAndViews,
  saveCurrentTabGraphState,
  setActiveViewId,
  syncActiveViewFromVisibleGraph
} from './graph-store.js';
import {materializeActiveView} from './view-materializer.js';

let renderDiagramCallback = null;
let renderPaletteCallback = null;
let host = null;
let bound = false;
let treeBound = false;
let layerMenuOpen = false;
let viewMenuOpen = false;
let layerPanelScrollTop = 0;
let treeBodyScrollTop = 0;
let treeLayerScrollTop = 0;

const SPEC_VIEW_NAMES = {
  cim: [
    "Requirements/Objectives View",
    "Capability Map",
    "Domain Model View",
    "EventStorming / Business Behavior View",
    "Business Process View",
    "Decision View",
    "Information and Governance View",
    "Transformation Readiness View"
  ],
  pim: [
    "Serverless component diagram",
    "API surface diagram",
    "Event/message flow diagram",
    "Workflow/state-machine diagram",
    "Data access diagram",
    "Security/permission diagram",
    "Observability/resilience overlay",
    "Deployment unit diagram"
  ],
  psm: [
    "AWS resource architecture diagram",
    "SAM stack composition diagram",
    "Lambda trigger diagram",
    "API Gateway route diagram",
    "EventBridge routing diagram",
    "SQS/DLQ resilience diagram",
    "Step Functions ASL diagram",
    "IAM permission graph",
    "Stage/environment deployment diagram",
    "Observability dashboard model"
  ]
};

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function normalizeLabel(value) {
  return String(value || "").trim().toLowerCase().replaceAll(/[^a-z0-9]+/g,
      " ");
}

function relationshipKindsInGraph() {
  return [...state.graph.relationshipsByKind.keys()].sort();
}

function currentLayerKinds(view) {
  const configured = safeArray(view?.filters?.relationshipKinds);
  return configured.length ? new Set(configured)
      : new Set(relationshipKindsInGraph());
}

function ensureHost() {
  host = el.modelWorkbenchPanel || document.getElementById(
      "modelWorkbenchPanel");
  return host;
}

function captureLayerPanelState() {
  const panel = ensureHost();
  const layerPanel = panel?.querySelector("#layerMenuPanel");
  if (!layerPanel) {
    return;
  }
  layerMenuOpen = !layerPanel.classList.contains("hidden");
  layerPanelScrollTop = layerPanel.scrollTop;
}

function restoreLayerPanelState() {
  const panel = ensureHost();
  const layerPanel = panel?.querySelector("#layerMenuPanel");
  if (!layerPanel) {
    return;
  }
  layerPanel.classList.toggle("hidden", !layerMenuOpen);
  if (layerMenuOpen) {
    window.requestAnimationFrame(() => {
      layerPanel.scrollTop = layerPanelScrollTop;
    });
  }
}

async function runManualAutoLayout() {
  try {
    const {autoLayoutCurrentDiagram} = await import('./model-ops.js');
    await autoLayoutCurrentDiagram();
  } catch (error) {
    console.warn("Auto layout failed", error);
  }
}

function viewMatchesLevel(view) {
  const level = String(view?.level || "").toLowerCase();
  if (state.activeType === "psm") {
    return !level || level === "psm" || level === "aws_psm";
  }
  return !level || level === state.activeType;
}

function levelViews() {
  const all = [...state.views.byId.values()].filter(viewMatchesLevel);
  const wanted = safeArray(SPEC_VIEW_NAMES[state.activeType]).map(
      normalizeLabel);
  const wantedSet = new Set(wanted);
  const rank = (view) => {
    const kind = String(view?.kind || "").toUpperCase();
    if (kind === "MAIN") {
      return -10;
    }
    const specIndex = wanted.indexOf(normalizeLabel(view.name));
    if (specIndex >= 0) {
      return specIndex;
    }
    return 500;
  };
  const filtered = all.filter((view) => {
    const kind = String(view?.kind || "").toUpperCase();
    return kind === "MAIN"
        || wantedSet.has(normalizeLabel(view.name))
        || !view.scope?.rootElementId;
  });
  return filtered.sort((a, b) => rank(a) - rank(b)
      || String(a.name || "").localeCompare(String(b.name || "")));
}

function activeViewLabel() {
  const view = activeView();
  if (!view) {
    return `No ${state.activeType.toUpperCase()} views`;
  }
  return view.name || view.id;
}

function viewMenuMarkup() {
  const options = levelViews();
  if (!options.length) {
    return `<div class="workbench-view-option-empty">No ${escapeHtml(
        state.activeType.toUpperCase())} views</div>`;
  }
  return options.map((view) => `
    <button class="workbench-view-option${view.id === state.views.activeViewId
      ? " is-active" : ""}"
            type="button"
            data-view-option="${escapeHtml(view.id)}"
            role="option"
            aria-selected="${view.id === state.views.activeViewId ? "true"
      : "false"}">
      <span class="workbench-view-option-label">${escapeHtml(
      view.name || view.id)}</span>
      <span class="workbench-view-option-kind">${escapeHtml(
      String(view.kind || "view").toLowerCase())}</span>
    </button>`).join("");
}

function layerMarkup(view) {
  const kinds = relationshipKindsInGraph();
  if (!kinds.length) {
    return `<div class="model-tree-empty">No relationships</div>`;
  }
  const enabled = currentLayerKinds(view);
  return kinds.map((kind) => `
    <label class="layer-toggle">
      <input data-layer-kind="${escapeHtml(kind)}" type="checkbox" ${
      enabled.has(kind) ? "checked" : ""}>
      <span>${escapeHtml(kind)}</span>
    </label>`).join("");
}

function elementLabel(element) {
  return String(element?.name || element?.label || element?.id || "Element");
}

function elementType(element) {
  return String(element?.eClass || element?.type || "Element");
}

function viewListMarkup() {
  const views = levelViews();
  if (!views.length) {
    return `<div class="model-tree-empty">No ${escapeHtml(
        state.activeType.toUpperCase())} views</div>`;
  }
  return views.map((view) => {
    const active = view.id === state.views.activeViewId;
    const kind = String(view.kind || "VIEW").replaceAll("_", " ");
    return `<label class="model-tree-view${active ? " is-active" : ""}">
      <input data-tree-view-id="${escapeHtml(view.id)}" name="model-tree-active-view"
             type="radio" ${active ? "checked" : ""}>
      <span class="model-tree-view-label">${escapeHtml(view.name || view.id)}</span>
      <span class="model-tree-view-kind">${escapeHtml(kind)}</span>
    </label>`;
  }).join("");
}

function renderModelTree() {
  if (!el.modelTreeBody) {
    return;
  }
  treeBodyScrollTop = el.modelTreeBody.scrollTop;
  treeLayerScrollTop = el.modelTreeBody.querySelector(
      ".model-tree-layer-list")?.scrollTop || treeLayerScrollTop;
  const view = activeView();
  const hiddenIds = new Set(safeArray(view?.hidden?.elementIds));
  const visibleIds = new Set(
      safeArray(view?.nodes).map((node) => node.elementId));
  const nodes = safeArray(view?.nodes).map((viewNode) => {
    const element = state.graph.elementsById.get(viewNode.elementId);
    return element ? {viewNode, element} : null;
  }).filter(Boolean);

  if (el.modelTreeTitle) {
    el.modelTreeTitle.textContent = `${state.activeType.toUpperCase()} views`;
  }
  const elementToggles = nodes.length ? `
      <div class="model-tree-panel-list">
        ${nodes.map(({viewNode, element}) => {
    const id = viewNode.elementId;
    const checked = visibleIds.has(id) && !hiddenIds.has(id);
    return `<label class="model-tree-node${checked ? "" : " is-hidden"}">
          <input class="model-tree-node-toggle" data-tree-element-id="${escapeHtml(
        id)}" type="checkbox" ${checked ? "checked" : ""}>
          <span class="model-tree-node-label">${escapeHtml(
        elementLabel(element))}</span>
          <span class="model-tree-node-type">${escapeHtml(elementType(element))}</span>
        </label>`;
  }).join("")}
      </div>` : `<div class="model-tree-empty">No elements in this view.</div>`;
  el.modelTreeBody.innerHTML = `
    <div class="model-tree-section">
      <div class="model-tree-section-title">Views</div>
      <div class="model-tree-panel-list">${viewListMarkup()}</div>
    </div>
    <div class="model-tree-section">
      <div class="model-tree-section-title">Relationship Layers</div>
      <div class="model-tree-layer-list">${layerMarkup(view)}</div>
    </div>
    <div class="model-tree-section">
      <div class="model-tree-section-title">Elements</div>
      ${elementToggles}
    </div>`;
  el.modelTreeBody.scrollTop = treeBodyScrollTop;
  const layerList = el.modelTreeBody.querySelector(".model-tree-layer-list");
  if (layerList) {
    layerList.scrollTop = treeLayerScrollTop;
  }
}

function setTreeOpen(open) {
  el.modelTreePanel?.classList.toggle("hidden", !open);
  el.workspace?.classList.toggle("views-open", !!open);
  if (open) {
    el.attributePanel?.classList.add("hidden");
    el.impactPanel?.classList.add("hidden");
    el.workspace?.classList.remove("attr-open", "impact-open",
        "mobile-right-open");
    if (window.innerWidth <= 920) {
      el.workspace?.classList.add("mobile-right-open");
      el.mobileBackdrop?.classList.remove("hidden");
    }
  } else {
    el.workspace?.classList.remove("mobile-right-open");
    el.mobileBackdrop?.classList.add("hidden");
  }
  renderModelTree();
}

export function renderViewWorkbench() {
  const panel = ensureHost();
  if (!panel) {
    return;
  }
  const isModeling = ["cim", "pim", "psm"].includes(state.activeType);
  panel.classList.toggle("hidden", !isModeling);
  if (!isModeling) {
    layerMenuOpen = false;
    return;
  }
  ensureActiveGraphAndViews();
  const view = activeView();
  captureLayerPanelState();
  panel.innerHTML = `
    <div class="workbench-view-select-wrap${viewMenuOpen ? " is-open" : ""}">
      <button class="sidebar-select workbench-view-select"
              id="activeViewSelect"
              type="button"
              title="${escapeHtml(state.activeType.toUpperCase())} view"
              aria-haspopup="listbox"
              aria-expanded="${viewMenuOpen ? "true" : "false"}">
        <span class="workbench-view-select-label">${escapeHtml(
      activeViewLabel())}</span>
      </button>
      <span class="workbench-view-select-caret" aria-hidden="true"></span>
      <div class="workbench-view-menu${viewMenuOpen ? "" : " hidden"}"
           id="activeViewMenu"
           role="listbox"
           aria-label="${escapeHtml(state.activeType.toUpperCase())} views">
        ${viewMenuMarkup()}
      </div>
    </div>
    <button class="sidebar-inline-action" id="workbenchAutoLayoutBtn" type="button">Auto Layout</button>
    <button class="sidebar-inline-action" id="modelTreeToggleBtn" type="button">Views</button>
    <div class="workbench-layer-menu">
      <button class="sidebar-inline-action" id="layerMenuBtn" type="button">Layers</button>
      <div class="workbench-layer-panel${layerMenuOpen ? ""
      : " hidden"}" id="layerMenuPanel">${layerMarkup(view)}</div>
    </div>`;
  restoreLayerPanelState();
  renderModelTree();
}

async function applyLayerToggle(kind, checked) {
  const view = activeView();
  if (!view) {
    return;
  }
  syncActiveViewFromVisibleGraph();
  const allKinds = relationshipKindsInGraph();
  const next = currentLayerKinds(view);
  if (checked) {
    next.add(kind);
  } else {
    next.delete(kind);
  }
  view.filters ??= {};
  view.filters.relationshipKinds = allKinds.length === next.size ? []
      : [...next];
  materializeActiveView();
  renderDiagramCallback?.();
  renderViewWorkbench();
  saveCurrentTabGraphState();
  setStatus(checked ? `Layer shown: ${kind}` : `Layer hidden: ${kind}`);
}

async function openWorkbenchView(viewId) {
  viewMenuOpen = false;
  if (setActiveViewId(viewId)) {
    materializeActiveView();
    renderPaletteCallback?.();
    renderDiagramCallback?.();
    renderViewWorkbench();
    if (["cim", "pim", "psm"].includes(state.activeType)
        && state.diagram?.nodes?.length) {
      try {
        const {autoLayoutCurrentDiagram} = await import('./model-ops.js');
        await autoLayoutCurrentDiagram();
      } catch (error) {
        console.warn("View auto layout failed", error);
      }
      return;
    }
    saveCurrentTabGraphState();
    setStatus("View selected.");
  }
}

async function toggleTreeElement(elementId, checked) {
  const view = activeView();
  if (!view || !elementId) {
    return;
  }
  syncActiveViewFromVisibleGraph();
  view.hidden ??= {elementIds: [], relationshipIds: []};
  const hidden = new Set(safeArray(view.hidden.elementIds));
  if (checked) {
    hidden.delete(elementId);
  } else {
    hidden.add(elementId);
  }
  view.hidden.elementIds = [...hidden];
  materializeActiveView();
  renderDiagramCallback?.();
  renderViewWorkbench();
  saveCurrentTabGraphState();
}

function bindTreeEvents() {
  if (treeBound) {
    return;
  }
  treeBound = true;
  el.modelTreeCloseBtn?.addEventListener("click", () => setTreeOpen(false));
  el.modelTreeBody?.addEventListener("change", (event) => {
    const target = event.target instanceof Element ? event.target : null;
    const elementId = target?.dataset?.treeElementId;
    const viewId = target?.dataset?.treeViewId;
    const layerKind = target?.dataset?.layerKind;
    if (viewId) {
      void openWorkbenchView(viewId);
      return;
    }
    if (layerKind) {
      applyLayerToggle(layerKind, Boolean(target.checked));
      return;
    }
    if (elementId) {
      toggleTreeElement(elementId, Boolean(target.checked));
    }
  });
}

function bindWorkbenchEvents() {
  const panel = ensureHost();
  if (!panel || bound) {
    return;
  }
  bound = true;
  panel.addEventListener("change", (event) => {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) {
      return;
    }
    const layerKind = target.dataset?.layerKind;
    if (layerKind) {
      applyLayerToggle(layerKind, Boolean(target.checked));
    }
  });
  panel.addEventListener("click", (event) => {
    const target = event.target instanceof Element ? event.target : null;
    if (target?.closest("#activeViewSelect")) {
      viewMenuOpen = !viewMenuOpen;
      renderViewWorkbench();
      return;
    }
    const selectedView = target?.closest(
        "[data-view-option]")?.dataset?.viewOption;
    if (selectedView) {
      void openWorkbenchView(selectedView);
      return;
    }
    if (target?.closest("#workbenchAutoLayoutBtn")) {
      runManualAutoLayout();
      return;
    }
    if (target?.closest("#modelTreeToggleBtn")) {
      setTreeOpen(el.modelTreePanel?.classList.contains("hidden"));
      return;
    }
    if (target?.closest("#layerMenuBtn")) {
      layerMenuOpen = !layerMenuOpen;
      panel.querySelector("#layerMenuPanel")?.classList.toggle("hidden",
          !layerMenuOpen);
    }
  });
  document.addEventListener("click", (event) => {
    const target = event.target instanceof Element ? event.target : null;
    if (!target?.closest(".workbench-view-select-wrap") && viewMenuOpen) {
      viewMenuOpen = false;
      renderViewWorkbench();
    }
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && viewMenuOpen) {
      viewMenuOpen = false;
      renderViewWorkbench();
    }
  });
}

export function initViewWorkbench({renderDiagram, renderPalette} = {}) {
  renderDiagramCallback = renderDiagram || renderDiagramCallback;
  renderPaletteCallback = renderPalette || renderPaletteCallback;
  ensureHost();
  bindWorkbenchEvents();
  bindTreeEvents();
  renderViewWorkbench();
}
