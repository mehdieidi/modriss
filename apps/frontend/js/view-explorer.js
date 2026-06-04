import {state} from './state.js';
import {el} from './dom.js';
import {escapeHtml} from './utils.js';
import {setStatus} from './status.js';
import {markModelDirty, updateModelSaveUi} from './model-save-ui.js';
import {
  activeView,
  ensureActiveGraphAndViews,
  saveCurrentTabGraphState,
  setActiveViewId,
  syncActiveViewFromVisibleGraph
} from './graph-store.js';
import {materializeActiveView} from './view-materializer.js';
import {
  activeCanvasFocus,
  canvasFocusLabel,
  centerViewportOnDiagram,
  closeBoundedContextSpecialView,
  closeCanvasFocus,
  finalizeBoundedContextDraft,
  openNeighborhoodFocus,
  restoreCanvasCamera,
  setContextCreateMode
} from './canvas.js';

let renderDiagramCallback = null;
let renderPaletteCallback = null;
let host = null;
let bound = false;
let treeBound = false;
let viewMenuOpen = false;
let treeBodyScrollTop = 0;
let modelTreeMode = "elements";
let modelTreeFilter = "";

const SPEC_VIEW_NAMES = {
  cim: [
    "CIM Overview",
    "Goals and Requirements",
    "Capability Map",
    "Stakeholder and Actor Map",
    "Bounded Context and Ubiquitous Language",
    "Domain Model",
    "Aggregate and Lifecycle",
    "Domain Data and Classification",
    "Behavior/Event Storming",
    "Business Process",
    "Policy and Decision",
    "Governance and Risk",
    "Transformation Readiness",
    "Trace Overlay"
  ],
  pim: [
    "PIM Overview",
    "Service Landscape",
    "API and Contract View",
    "Function and Trigger View",
    "Event and Flow View",
    "Data and Storage View",
    "Workflow View",
    "Security View",
    "Configuration and Secrets",
    "Policy and Observability View",
    "Deployment and Environment View",
    "Schema and Event Contract View",
    "Trace and Readiness Overlay"
  ],
  psm: [
    "AWS PSM Overview",
    "Stage and Stack View",
    "Lambda Detail View",
    "API Gateway View",
    "EventBridge and Messaging View",
    "Storage View",
    "Security, IAM, and Secrets View",
    "Cognito Identity View",
    "Step Functions / ASL View",
    "Observability and Networking View",
    "Networking Detail View",
    "SAM and CloudFormation View",
    "Trace and Readiness Overlay",
    "Integration Shortcut View"
  ]
};

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function normalizeLabel(value) {
  return String(value || "").trim().toLowerCase().replaceAll(/[^a-z0-9]+/g,
      " ");
}

function ensureHost() {
  host = el.modelWorkbenchPanel || document.getElementById(
      "modelWorkbenchPanel");
  return host;
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
    if (kind === "SAVED_VIEWPOINT") {
      return 350;
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
        || kind === "SAVED_VIEWPOINT"
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

function normalizeBoundedContextToolState() {
  if (state.activeType !== "cim") {
    state.boundedContextViewMode = "normal";
    state.activeBoundedContextName = "";
    state.boundedContextCreateMode = false;
    state.boundedContextDraftNodeIds = new Set();
    state.boundedContextDraftName = "";
    return;
  }
  const contextNames = new Set();
  (state.baseModel?.boundedContexts || []).forEach((context) => {
    const name = String(context?.name || "").trim();
    if (name) {
      contextNames.add(name);
    }
  });
  (state.diagram?.nodes || []).forEach((node) => {
    if (node.type === "BoundedContextCandidate") {
      const name = String(node.label || node.meta?.name || "").trim();
      if (name) {
        contextNames.add(name);
      }
      return;
    }
    const explicit = String(node.meta?.contextName || "").trim();
    if (explicit) {
      contextNames.add(explicit);
    }
  });
  if (state.boundedContextViewMode === "overview" && !contextNames.size) {
    state.boundedContextViewMode = "normal";
    state.activeBoundedContextName = "";
  }
  if (state.boundedContextViewMode === "focus") {
    const activeName = String(state.activeBoundedContextName || "").trim();
    const exists = contextNames.has(activeName);
    if (!activeName || !exists) {
      state.boundedContextViewMode = "normal";
      state.activeBoundedContextName = "";
    }
  }
}

function boundedContextToolMarkup() {
  if (activeCanvasFocus()) {
    return `<div class="workbench-context-actions workbench-focus-actions">
      <button class="sidebar-inline-action context-action-primary"
              id="canvasFocusBackBtn" type="button">Back</button>
      <span class="workbench-focus-label">${escapeHtml(
        canvasFocusLabel())}</span>
    </div>`;
  }
  if (state.activeType !== "cim") {
    return "";
  }
  if (state.boundedContextViewMode !== "normal") {
    return `<div class="workbench-context-actions">
      <button class="sidebar-inline-action context-action-primary"
              id="boundedContextBackBtn" type="button">Back</button>
    </div>`;
  }
  if (state.boundedContextCreateMode) {
    return `<div class="workbench-context-actions">
      <button class="sidebar-inline-action context-action-primary"
              id="boundedContextDoneBtn" type="button">Done</button>
      <button class="sidebar-inline-action" id="boundedContextCancelBtn"
              type="button">Cancel</button>
    </div>`;
  }
  return "";
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

function elementLabel(element) {
  return String(element?.name || element?.label || element?.id || "Element");
}

function elementType(element) {
  return String(element?.eClass || element?.type || "Element");
}

function activeViewElementRows(view) {
  const hiddenIds = new Set(safeArray(view?.hidden?.elementIds));
  const visibleIds = new Set(
      safeArray(view?.nodes).map((node) => node.elementId));
  return safeArray(view?.nodes).map((viewNode) => {
    const element = state.graph.elementsById.get(viewNode.elementId);
    if (!element) {
      return null;
    }
    const id = viewNode.elementId;
    return {
      id,
      element,
      checked: visibleIds.has(id) && !hiddenIds.has(id),
      search: normalizeLabel(
          `${elementLabel(element)} ${elementType(element)} ${id}`)
    };
  }).filter(Boolean);
}

function relationshipLabel(relationship) {
  return String(relationship?.name || relationship?.label
      || relationship?.kind || relationship?.eClass || "Relationship");
}

function relationshipKind(relationship) {
  return String(relationship?.kind || relationship?.eClass
      || relationship?.type || "Relationship");
}

function relationshipEndpointId(relationship, key) {
  const direct = relationship?.[`${key}ElementId`]
      || relationship?.[`${key}Id`];
  if (direct) {
    return String(direct);
  }
  const value = relationship?.[key];
  if (typeof value === "string") {
    return value;
  }
  return String(value?.id || value?.elementId || "");
}

function relationshipsForActiveView(view) {
  const fromVisibleState = state.views?.visibleRelationshipIds instanceof Set
      ? [...state.views.visibleRelationshipIds]
      : [];
  const hiddenRelationshipIds = new Set(
      safeArray(view?.hidden?.relationshipIds));
  const fromViewEdges = safeArray(view?.edges)
  .filter((edge) => edge?.visible !== false)
  .map((edge) => edge.relationshipId || edge.id)
  .filter((id) => id && !hiddenRelationshipIds.has(id));
  const relationshipIds = fromVisibleState.length ? fromVisibleState
      : fromViewEdges;
  const relationshipMap = state.graph.relationshipsById;
  const rows = relationshipIds.map((id) => relationshipMap.get(id)).filter(
      Boolean);
  if (rows.length) {
    return rows;
  }
  const visibleElementIds = new Set(
      safeArray(view?.nodes).map((node) => node.elementId));
  if (!visibleElementIds.size) {
    return [];
  }
  return [...relationshipMap.values()].filter((relationship) => {
    const sourceId = relationshipEndpointId(relationship, "source");
    const targetId = relationshipEndpointId(relationship, "target");
    return !hiddenRelationshipIds.has(relationship.id)
        && visibleElementIds.has(sourceId) && visibleElementIds.has(targetId);
  });
}

function relationshipRowsMarkup(view) {
  const filter = normalizeLabel(modelTreeFilter);
  const relationships = relationshipsForActiveView(view).map((relationship) => {
    const sourceId = relationshipEndpointId(relationship, "source");
    const targetId = relationshipEndpointId(relationship, "target");
    const source = state.graph.elementsById.get(sourceId);
    const target = state.graph.elementsById.get(targetId);
    const sourceLabel = elementLabel(source) || sourceId || "Source";
    const targetLabel = elementLabel(target) || targetId || "Target";
    const kind = relationshipKind(relationship);
    const label = relationshipLabel(relationship);
    return {
      id: relationship.id || "",
      sourceLabel,
      targetLabel,
      kind,
      label,
      search: normalizeLabel(
          `${label} ${kind} ${sourceLabel} ${targetLabel} ${relationship.id
          || ""}`)
    };
  });
  const filtered = filter
      ? relationships.filter((row) => row.search.includes(filter))
      : relationships;
  if (!relationships.length) {
    return `<div class="model-tree-empty">No relationships in this view.</div>`;
  }
  if (!filtered.length) {
    return `<div class="model-tree-empty">No matching relationships.</div>`;
  }
  return `<div class="model-tree-panel-list">
    ${filtered.map((row) => `
      <div class="model-tree-relationship">
        <div class="model-tree-relationship-main">
          <span class="model-tree-relationship-label">${escapeHtml(
      row.sourceLabel)} -> ${escapeHtml(row.targetLabel)}</span>
          <span class="model-tree-relationship-name">${escapeHtml(row.label)}</span>
        </div>
        <span class="model-tree-relationship-kind">${escapeHtml(row.kind)}</span>
      </div>`).join("")}
    </div>`;
}

function elementRowsMarkup(view) {
  const filter = normalizeLabel(modelTreeFilter);
  const rows = activeViewElementRows(view);
  const filtered = filter ? rows.filter((row) => row.search.includes(filter))
      : rows;
  if (!rows.length) {
    return `<div class="model-tree-empty">No elements in this view.</div>`;
  }
  if (!filtered.length) {
    return `<div class="model-tree-empty">No matching elements.</div>`;
  }
  return `<div class="model-tree-panel-list">
    ${filtered.map(({id, element, checked}) => `
      <label class="model-tree-node${checked ? "" : " is-hidden"}">
        <input class="model-tree-node-toggle" data-tree-element-id="${escapeHtml(
      id)}" type="checkbox" ${checked ? "checked" : ""}>
        <span class="model-tree-node-label">${escapeHtml(
      elementLabel(element))}</span>
        <span class="model-tree-node-type">${escapeHtml(elementType(element))}</span>
      </label>`).join("")}
    </div>`;
}

function renderModelTree() {
  if (!el.modelTreeBody) {
    return;
  }
  treeBodyScrollTop = el.modelTreeBody.scrollTop;
  const view = activeView();
  const isRelationshipMode = modelTreeMode === "relationships";
  const title = isRelationshipMode ? "Relationships" : "Elements";
  const placeholder = isRelationshipMode ? "Filter relationships..."
      : "Filter elements...";
  if (el.modelTreeTitle) {
    el.modelTreeTitle.textContent = title;
  }
  if (el.modelTreePanel) {
    const type = el.modelTreePanel.querySelector(".attr-panel-type");
    if (type) {
      type.textContent = `${state.activeType.toUpperCase()} ${title}`;
    }
  }
  el.modelTreeBody.innerHTML = `
    <div class="model-tree-filter-row">
      <input class="model-tree-filter-input"
             data-model-tree-filter
             placeholder="${placeholder}"
             type="search"
             value="${escapeHtml(modelTreeFilter)}">
    </div>
    <div class="model-tree-section">
      <div class="model-tree-section-title">${title}</div>
      ${isRelationshipMode ? relationshipRowsMarkup(view) : elementRowsMarkup(
      view)}
    </div>`;
  el.modelTreeBody.scrollTop = treeBodyScrollTop;
}

function setTreeOpen(open, mode = modelTreeMode) {
  if (open && modelTreeMode !== mode) {
    modelTreeFilter = "";
  }
  modelTreeMode = mode;
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
    return;
  }
  normalizeBoundedContextToolState();
  panel.classList.toggle("model-workbench-minimized", Boolean(
      state.modelingToolsMinimized));
  if (state.modelingToolsMinimized) {
    panel.innerHTML = `
      <button class="model-tools-restore" id="modelToolsRestoreBtn"
              type="button"
              title="Show modeling tools">
        <span>Modeling Tools</span>
        <strong>${escapeHtml(state.activeType.toUpperCase())}</strong>
      </button>`;
    return;
  }
  ensureActiveGraphAndViews();
  const surface = panel.querySelector("#modelWorkbenchSurface");
  surface?.remove();
  const contextTools = boundedContextToolMarkup();
  panel.innerHTML = `
    <div class="model-workbench-commandbar">
      <div class="workbench-primary-group">
        <div class="workbench-strip-title">
          <strong>Modeling</strong>
          <span>${escapeHtml(state.activeType.toUpperCase())}</span>
        </div>
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
      </div>
      ${contextTools
      ? `<div class="workbench-context-slot">${contextTools}</div>` : ""}
      <div class="workbench-action-group">
        <button class="sidebar-inline-action" id="focusDepthOneBtn" type="button"
                title="Open selected element neighborhood depth 1">Focus 1</button>
        <button class="sidebar-inline-action" id="focusDepthTwoBtn" type="button"
                title="Open selected element neighborhood depth 2">Focus 2</button>
        <button class="sidebar-inline-action" id="saveViewpointBtn" type="button"
                title="Save filters, layout, and camera as a viewpoint">Save View</button>
        <button class="sidebar-inline-action${modelTreeMode === "elements"
  && !el.modelTreePanel?.classList.contains("hidden") ? " is-active"
      : ""}" id="modelTreeToggleBtn" type="button">Tree</button>
        <button class="sidebar-inline-action${modelTreeMode === "relationships"
  && !el.modelTreePanel?.classList.contains("hidden") ? " is-active"
      : ""}" id="relationshipTreeToggleBtn" type="button">Relationships</button>
      </div>
      <div class="workbench-persist-group">
        <button class="sidebar-inline-action model-save-btn"
                id="saveModelBtn"
                type="button"
                title="Save current model (Ctrl+S)">
          <span class="model-save-btn-label" id="saveModelBtnLabel">Save</span>
          <span class="model-save-btn-progress" aria-hidden="true">
            <span class="model-save-btn-progress-bar"></span>
          </span>
        </button>
        <button class="sidebar-inline-action model-layout-btn"
                id="workbenchAutoLayoutBtn"
                type="button"
                title="Arrange current view automatically">Auto Layout</button>
        <button class="sidebar-inline-action model-tools-minimize"
                id="modelToolsMinimizeBtn"
                type="button"
                aria-label="Minimize modeling tools"
                title="Minimize modeling tools">
          <span aria-hidden="true" class="model-tools-minimize-icon icon-svg icon-mask"
                style="--icon-src: url('/assets/icons/collapse.svg');"></span>
        </button>
      </div>
    </div>`;
  if (surface) {
    panel.appendChild(surface);
  }
  updateModelSaveUi();
  renderModelTree();
}

function savedViewpointId(name) {
  const normalized = normalizeLabel(name).replaceAll(" ", "-") || "viewpoint";
  return `view-${state.activeType}-saved-${normalized}-${Date.now().toString(
      36)}`;
}

function saveCurrentViewpoint() {
  const view = activeView();
  if (!view) {
    setStatus("No active view to save.");
    return;
  }
  syncActiveViewFromVisibleGraph();
  const defaultName = `${view.name
  || state.activeType.toUpperCase()} Viewpoint`;
  const name = window.prompt("Viewpoint name", defaultName);
  if (!String(name || "").trim()) {
    return;
  }
  const source = activeView();
  const viewpoint = structuredClone(source);
  viewpoint.id = savedViewpointId(name);
  viewpoint.name = String(name).trim();
  viewpoint.kind = "SAVED_VIEWPOINT";
  viewpoint.sourceViewId = source.id;
  viewpoint.savedAt = new Date().toISOString();
  viewpoint.camera = {
    x: Number(state.viewport?.x) || 0,
    y: Number(state.viewport?.y) || 0,
    scale: Number(state.viewport?.scale) || 1
  };
  state.views.byId.set(viewpoint.id, viewpoint);
  state.views.activeViewId = viewpoint.id;
  materializeActiveView();
  renderPaletteCallback?.();
  renderDiagramCallback?.();
  renderViewWorkbench();
  saveCurrentTabGraphState();
  markModelDirty();
  updateModelSaveUi();
  setStatus(`Saved viewpoint: ${viewpoint.name}`);
}

async function openWorkbenchView(viewId) {
  viewMenuOpen = false;
  const targetView = state.views.byId.get(viewId);
  const needsInitialLayout = targetView && !targetView.autoLayoutApplied;
  if (setActiveViewId(viewId)) {
    materializeActiveView();
    renderPaletteCallback?.();
    renderDiagramCallback?.();
    renderViewWorkbench();
    saveCurrentTabGraphState();
    if (needsInitialLayout) {
      try {
        const {autoLayoutCurrentDiagram} = await import('./model-ops.js');
        await autoLayoutCurrentDiagram({
          progress: true,
          save: false,
          status: false
        });
        setStatus("View selected and arranged.");
      } catch (error) {
        console.warn("Initial view auto layout failed", error);
        if (!restoreCanvasCamera(targetView?.camera)) {
          centerViewportOnDiagram({fit: true});
        }
        setStatus("View selected. Auto layout failed.");
      }
      return;
    }
    if (!restoreCanvasCamera(targetView?.camera)) {
      centerViewportOnDiagram({fit: true});
    }
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
  el.modelTreeCloseBtn?.addEventListener("click", () => {
    setTreeOpen(false);
    renderViewWorkbench();
  });
  el.modelTreeBody?.addEventListener("change", (event) => {
    const target = event.target instanceof Element ? event.target : null;
    const elementId = target?.dataset?.treeElementId;
    if (elementId) {
      toggleTreeElement(elementId, Boolean(target.checked));
    }
  });
  el.modelTreeBody?.addEventListener("input", (event) => {
    const target = event.target instanceof Element ? event.target : null;
    if (!target?.matches("[data-model-tree-filter]")) {
      return;
    }
    modelTreeFilter = target.value || "";
    renderModelTree();
    el.modelTreeBody?.querySelector("[data-model-tree-filter]")?.focus();
  });
}

function bindWorkbenchEvents() {
  const panel = ensureHost();
  if (!panel || bound) {
    return;
  }
  bound = true;
  panel.addEventListener("click", (event) => {
    const target = event.target instanceof Element ? event.target : null;
    if (target?.closest("#modelToolsRestoreBtn")) {
      state.modelingToolsMinimized = false;
      renderDiagramCallback?.();
      renderViewWorkbench();
      setStatus("Modeling tools restored");
      return;
    }
    if (target?.closest("#modelToolsMinimizeBtn")) {
      state.modelingToolsMinimized = true;
      viewMenuOpen = false;
      setTreeOpen(false);
      renderDiagramCallback?.();
      renderViewWorkbench();
      setStatus("Modeling tools minimized");
      return;
    }
    if (target?.closest("#activeViewSelect")) {
      viewMenuOpen = !viewMenuOpen;
      renderViewWorkbench();
      return;
    }
    if (target?.closest("#boundedContextBackBtn")) {
      closeBoundedContextSpecialView();
      renderViewWorkbench();
      return;
    }
    if (target?.closest("#canvasFocusBackBtn")) {
      closeCanvasFocus();
      renderViewWorkbench();
      return;
    }
    if (target?.closest("#focusDepthOneBtn")) {
      if (state.selectedNodeId) {
        openNeighborhoodFocus(state.selectedNodeId, 1);
      } else {
        setStatus("Select an element before opening Focus 1.");
      }
      renderViewWorkbench();
      return;
    }
    if (target?.closest("#focusDepthTwoBtn")) {
      if (state.selectedNodeId) {
        openNeighborhoodFocus(state.selectedNodeId, 2);
      } else {
        setStatus("Select an element before opening Focus 2.");
      }
      renderViewWorkbench();
      return;
    }
    if (target?.closest("#saveViewpointBtn")) {
      saveCurrentViewpoint();
      return;
    }
    if (target?.closest("#boundedContextDoneBtn")) {
      void finalizeBoundedContextDraft().then(renderViewWorkbench);
      return;
    }
    if (target?.closest("#boundedContextCancelBtn")) {
      setContextCreateMode(false);
      renderViewWorkbench();
      setStatus("Bounded context assignment canceled");
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
      const open = el.modelTreePanel?.classList.contains("hidden")
          || modelTreeMode !== "elements";
      setTreeOpen(open, "elements");
      renderViewWorkbench();
      return;
    }
    if (target?.closest("#relationshipTreeToggleBtn")) {
      const open = el.modelTreePanel?.classList.contains("hidden")
          || modelTreeMode !== "relationships";
      setTreeOpen(open, "relationships");
      renderViewWorkbench();
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
  window.addEventListener("model-tools-state-change", () => {
    renderViewWorkbench();
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
