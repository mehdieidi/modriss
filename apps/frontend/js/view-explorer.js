import { state } from "./state.js";
import { el } from "./dom.js";
import { escapeHtml } from "./utils.js";
import { setStatus } from "./status.js";
import { markModelDirty, updateModelSaveUi } from "./model-save-ui.js";
import {
  activeView,
  ensureActiveGraphAndViews,
  isNamedInstanceView,
  saveCurrentTabGraphState,
  selectElementIdsForView,
  selectRelationshipIdsForView,
  setActiveViewId,
  syncActiveViewFromVisibleGraph,
} from "./graph-store.js";
import { materializeActiveView, viewNeedsAutoLayout } from "./view-materializer.js";
import {
  isModelingLevel,
  modelingDefaultLayoutStrategy,
  modelingLayoutStrategies,
  modelingLevelConfig,
} from "./modeling-config-data.js";
import { syncMobileDockState } from "./mobile-ui.js";
import { isMobileViewport } from "./responsive.js";
import {
  openAttributePanel,
  openConnectionPanel,
  openRootModelAttributePanel,
} from "./attr-panel.js";
import {
  activeCanvasFocus,
  clearCanvasFocus,
  closeCanvasFocus,
  fitViewportToDiagram,
  renderDiagramAsync,
  scrollToConnectionAndHighlight,
  scrollToNodeAndHighlight,
} from "./canvas.js";

let renderDiagramCallback = null;
let renderPaletteCallback = null;
let host = null;
let bound = false;
let treeBound = false;
let viewMenuOpen = false;
let layoutMenuOpen = false;
let inspectMenuOpen = false;
let treeBodyScrollTop = 0;
let modelTreeMode = "elements";
let modelTreeFilter = "";
let lastMobileViewport = null;

function layoutStrategies() {
  return modelingLayoutStrategies();
}

function defaultLayoutStrategy() {
  return modelingDefaultLayoutStrategy();
}

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function normalizeLabel(value) {
  return String(value || "")
    .trim()
    .toLowerCase()
    .replaceAll(/[^a-z0-9]+/g, " ");
}

function ensureHost() {
  host = el.modelWorkbenchPanel || document.getElementById("modelWorkbenchPanel");
  return host;
}

function syncTopbarModelingState(isModeling) {
  const modelingRow = document.getElementById("topbarModelingRow");
  modelingRow?.classList.toggle("hidden", !isModeling);
}

async function runManualAutoLayout() {
  try {
    const { autoLayoutCurrentDiagram } = await import("./model-ops.js");
    await autoLayoutCurrentDiagram({
      strategy: selectedLayoutStrategy(),
      force: true,
    });
  } catch (error) {
    console.warn("Auto layout failed", error);
  }
}

function emptyLayoutStrategyConfig() {
  return {
    id: "",
    label: "Layout",
    title: "Layout strategies unavailable until modeling config loads.",
  };
}

function selectedLayoutStrategy() {
  const strategies = layoutStrategies();
  const view = activeView();
  const stored = window.localStorage.getItem(`modriss.layoutStrategy.${state.activeType}`);
  const fallback = defaultLayoutStrategy();
  if (!strategies.length) {
    return String(view?.layoutStrategy || stored || fallback || "").toUpperCase();
  }
  const value = String(view?.layoutStrategy || stored || fallback).toUpperCase();
  return strategies.some((strategy) => strategy.id === value) ? value : fallback;
}

function selectedLayoutStrategyConfig() {
  const strategies = layoutStrategies();
  if (!strategies.length) {
    return emptyLayoutStrategyConfig();
  }
  const selected = selectedLayoutStrategy();
  return strategies.find((strategy) => strategy.id === selected) || strategies[0];
}

function layoutStrategyMenuMarkup() {
  const strategies = layoutStrategies();
  if (!strategies.length) {
    return `<div class="workbench-view-option workbench-layout-option is-disabled" aria-disabled="true">
      <span class="workbench-view-option-label">No layout strategies in config</span>
    </div>`;
  }
  const selected = selectedLayoutStrategy();
  return strategies
    .map(
      (strategy) =>
        `<button class="workbench-view-option workbench-layout-option${
          strategy.id === selected ? " is-active" : ""
        }"
               type="button"
               role="option"
               aria-selected="${strategy.id === selected ? "true" : "false"}"
               data-layout-strategy="${escapeHtml(strategy.id)}"
               title="${escapeHtml(strategy.title)}">
         <span class="workbench-view-option-label">${escapeHtml(strategy.label)}</span>
         <span class="workbench-view-option-kind">${escapeHtml(strategy.title)}</span>
       </button>`,
    )
    .join("");
}

function setLayoutStrategy(strategyId) {
  const strategies = layoutStrategies();
  if (!strategies.length) {
    return;
  }
  const fallback = defaultLayoutStrategy();
  const strategy = String(strategyId || fallback).toUpperCase();
  const selected = layoutStrategies().some((item) => item.id === strategy) ? strategy : fallback;
  const view = activeView();
  if (view) {
    view.layoutStrategy = selected;
  }
  window.localStorage.setItem(`modriss.layoutStrategy.${state.activeType}`, selected);
  layoutMenuOpen = false;
  renderViewWorkbench();
  setStatus("Layout strategy selected. Run Auto Layout to apply it.");
}

function viewMatchesLevel(view) {
  const level = String(view?.level || "").toLowerCase();
  const config = modelingLevelConfig(state.activeType);
  const aliases = [
    state.activeType,
    config.apiType,
    config.chatType,
    config.displayName,
    config.rootTemplate?.modelLevel,
  ]
    .map((value) => String(value || "").toLowerCase())
    .filter(Boolean);
  return !level || aliases.includes(level);
}

function canvasFocusToolMarkup() {
  // Canvas navigation is represented by the floating control on the canvas.
  // Keeping it out of the topbar prevents a second Back action from competing
  // with the view and lifecycle controls at compact widths.
  return "";
}

function metadataViewKeys() {
  try {
    return safeArray(modelingLevelConfig(state.activeType).views).map((definition) =>
      normalizeLabel(
        [definition?.displayName, definition?.name, definition?.id].filter(Boolean).join(" "),
      ),
    );
  } catch {
    return [];
  }
}

function viewMetadataKey(view) {
  return normalizeLabel(
    [view?.name, view?.displayName, view?.definitionId, view?.sourceDefinitionId]
      .filter(Boolean)
      .join(" "),
  );
}

function metadataViewDefinitionIds() {
  try {
    return safeArray(modelingLevelConfig(state.activeType).views).map((definition) =>
      normalizeLabel(String(definition?.id || "")),
    );
  } catch {
    return [];
  }
}

function viewMatchesCatalogDefinition(view) {
  const definitionId = normalizeLabel(String(view?.definitionId || view?.sourceDefinitionId || ""));
  if (!definitionId) {
    return false;
  }
  return metadataViewDefinitionIds().includes(definitionId);
}

function metadataKeyMatches(viewKey, metadataKey) {
  return Boolean(viewKey && metadataKey) && viewKey === metadataKey;
}

function levelViews() {
  const all = [...state.views.byId.values()].filter(viewMatchesLevel);
  const wanted = metadataViewKeys();
  const wantedSet = new Set(wanted);
  const definitionIds = new Set(metadataViewDefinitionIds());
  const rank = (view) => {
    const kind = String(view?.kind || "").toUpperCase();
    if (kind === "MAIN") {
      return -10;
    }
    if (kind === "SAVED_VIEWPOINT") {
      return 350;
    }
    const definitionId = normalizeLabel(
      String(view?.definitionId || view?.sourceDefinitionId || ""),
    );
    if (definitionId && definitionIds.has(definitionId)) {
      return [...definitionIds].indexOf(definitionId);
    }
    const specIndex = wanted.findIndex((key) => metadataKeyMatches(viewMetadataKey(view), key));
    if (specIndex >= 0) {
      return specIndex;
    }
    return 500;
  };
  const filtered = all.filter((view) => {
    if (isNamedInstanceView(state.activeType, view)) {
      return false;
    }
    const kind = String(view?.kind || "").toUpperCase();
    if (kind === "MAIN" || kind === "SAVED_VIEWPOINT") {
      return true;
    }
    if (viewMatchesCatalogDefinition(view)) {
      return true;
    }
    const viewName = normalizeLabel(String(view?.name || ""));
    return wantedSet.has(viewName);
  });
  return filtered.sort(
    (a, b) => rank(a) - rank(b) || String(a.name || "").localeCompare(String(b.name || "")),
  );
}

function activeViewLabel() {
  const focus = activeCanvasFocus();
  if (focus) {
    return focus.label || focus.elementId;
  }
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
      state.activeType.toUpperCase(),
    )} views</div>`;
  }
  return options
    .map(
      (view) => `
    <button class="workbench-view-option${view.id === state.views.activeViewId ? " is-active" : ""}"
            type="button"
            data-view-option="${escapeHtml(view.id)}"
            role="option"
            aria-selected="${view.id === state.views.activeViewId ? "true" : "false"}">
      <span class="workbench-view-option-label">${escapeHtml(view.name || view.id)}</span>
    </button>`,
    )
    .join("");
}

function elementLabel(element) {
  return String(element?.name || element?.label || element?.id || "Element");
}

function elementType(element) {
  return String(element?.eClass || element?.type || "Element");
}

function inspectionView(view, hiddenOverrides = {}) {
  return {
    ...view,
    hidden: {
      elementIds: safeArray(hiddenOverrides.elementIds ?? view?.hidden?.elementIds),
      relationshipIds: safeArray(hiddenOverrides.relationshipIds ?? view?.hidden?.relationshipIds),
    },
  };
}

function inspectableElementIdsForView(view) {
  if (!view || !state.graph?.elementsById) {
    return [];
  }
  return selectElementIdsForView(
    state.graph,
    inspectionView(view, { elementIds: [] }),
    state.activeType,
  );
}

function activeViewElementRows(view) {
  const hiddenIds = new Set(safeArray(view?.hidden?.elementIds));
  const visibleIds =
    state.views?.visibleNodeIds instanceof Set
      ? state.views.visibleNodeIds
      : new Set(safeArray(view?.nodes).map((node) => node.elementId));
  return inspectableElementIdsForView(view)
    .map((id) => {
      const element = state.graph.elementsById.get(id);
      if (!element) {
        return null;
      }
      return {
        id,
        element,
        checked: visibleIds.has(id) && !hiddenIds.has(id),
        search: normalizeLabel(`${elementLabel(element)} ${elementType(element)} ${id}`),
      };
    })
    .filter(Boolean);
}

function relationshipLabel(relationship) {
  return String(
    relationship?.name ||
      relationship?.label ||
      relationship?.kind ||
      relationship?.eClass ||
      "Relationship",
  );
}

function relationshipKind(relationship) {
  return String(relationship?.kind || relationship?.eClass || relationship?.type || "Relationship");
}

function relationshipEndpointId(relationship, key) {
  const direct = relationship?.[`${key}ElementId`] || relationship?.[`${key}Id`];
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
  const relationshipMap = state.graph.relationshipsById;
  const inspectableView = inspectionView(view, {
    elementIds: [],
    relationshipIds: [],
  });
  const elementIds = inspectableElementIdsForView(view);
  const visibleElementIds = new Set(elementIds);
  const relationshipIds = selectRelationshipIdsForView(
    state.graph,
    inspectableView,
    elementIds,
    state.activeType,
  );
  return relationshipIds
    .map((id) => relationshipMap.get(id))
    .filter((relationship) => {
      if (!relationship) {
        return false;
      }
      return (
        visibleElementIds.has(relationshipEndpointId(relationship, "source")) &&
        visibleElementIds.has(relationshipEndpointId(relationship, "target"))
      );
    });
}

function relationshipRowsMarkup(view) {
  const filter = normalizeLabel(modelTreeFilter);
  const hiddenRelationshipIds = new Set(safeArray(view?.hidden?.relationshipIds));
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
      checked: !hiddenRelationshipIds.has(relationship.id),
      search: normalizeLabel(
        `${label} ${kind} ${sourceLabel} ${targetLabel} ${relationship.id || ""}`,
      ),
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
    ${filtered
      .map(
        (row) => `
      <div class="model-tree-relationship${row.checked ? "" : " is-hidden"}"
           data-tree-select-relationship-id="${escapeHtml(row.id)}"
           role="button"
           tabindex="0">
        <input class="model-tree-node-toggle"
               data-tree-relationship-id="${escapeHtml(row.id)}"
               type="checkbox"
               ${row.checked ? "checked" : ""}>
        <div class="model-tree-relationship-main">
          <span class="model-tree-relationship-label">${escapeHtml(
            row.sourceLabel,
          )} -> ${escapeHtml(row.targetLabel)}</span>
          <span class="model-tree-relationship-name">${escapeHtml(row.label)}</span>
        </div>
        <span class="model-tree-relationship-kind">${escapeHtml(row.kind)}</span>
        <button class="model-tree-locate-btn"
                data-tree-locate-relationship-id="${escapeHtml(row.id)}"
                type="button">locate</button>
      </div>`,
      )
      .join("")}
    </div>`;
}

function elementRowsMarkup(view) {
  const filter = normalizeLabel(modelTreeFilter);
  const rows = activeViewElementRows(view);
  const filtered = filter ? rows.filter((row) => row.search.includes(filter)) : rows;
  if (!rows.length) {
    return `<div class="model-tree-empty">No elements in this view.</div>`;
  }
  if (!filtered.length) {
    return `<div class="model-tree-empty">No matching elements.</div>`;
  }
  return `<div class="model-tree-panel-list">
    ${filtered
      .map(
        ({ id, element, checked }) => `
      <div class="model-tree-node${checked ? "" : " is-hidden"}"
           data-tree-select-element-id="${escapeHtml(id)}"
           role="button"
           tabindex="0">
        <input class="model-tree-node-toggle" data-tree-element-id="${escapeHtml(
          id,
        )}" type="checkbox" ${checked ? "checked" : ""}>
        <span class="model-tree-node-label">${escapeHtml(elementLabel(element))}</span>
        <span class="model-tree-node-type">${escapeHtml(elementType(element))}</span>
        <button class="model-tree-locate-btn"
                data-tree-locate-element-id="${escapeHtml(id)}"
                type="button">locate</button>
      </div>`,
      )
      .join("")}
    </div>`;
}

function elementCountForView(view) {
  return activeViewElementRows(view).length;
}

function relationshipCountForView(view) {
  return relationshipsForActiveView(view).length;
}

function renderModelTree() {
  if (!el.modelTreeBody) {
    return;
  }
  treeBodyScrollTop = el.modelTreeBody.scrollTop;
  const view = activeView();
  const isRelationshipMode = modelTreeMode === "relationships";
  const count = isRelationshipMode ? relationshipCountForView(view) : elementCountForView(view);
  const title = isRelationshipMode ? "Relationships" : "Elements";
  const titleWithCount = `${title} (${count})`;
  const placeholder = isRelationshipMode ? "Filter relationships..." : "Filter elements...";
  if (el.modelTreeTitle) {
    el.modelTreeTitle.textContent = titleWithCount;
  }
  if (el.modelTreePanel) {
    const type = el.modelTreePanel.querySelector(".attr-panel-type");
    if (type) {
      type.textContent = `${state.activeType.toUpperCase()} ${titleWithCount}`;
    }
  }
  el.modelTreeBody.innerHTML = `
    <div class="model-tree-filter-row">
      <input class="model-tree-filter-input"
             data-model-tree-filter
             dir="ltr"
             autocomplete="off"
             placeholder="${placeholder}"
             type="search"
             value="${escapeHtml(modelTreeFilter)}">
    </div>
    <div class="model-tree-section">
      <div class="model-tree-section-title">${escapeHtml(titleWithCount)}</div>
      ${isRelationshipMode ? relationshipRowsMarkup(view) : elementRowsMarkup(view)}
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
    el.workspace?.classList.remove("attr-open", "impact-open", "mobile-right-open");
    if (window.innerWidth <= 920) {
      el.workspace?.classList.add("mobile-right-open");
    }
    syncMobileDockState();
  } else {
    el.workspace?.classList.remove("mobile-right-open");
    syncMobileDockState();
  }
  renderModelTree();
}

export function renderViewWorkbench() {
  const panel = ensureHost();
  if (!panel) {
    return;
  }
  const isModeling = isModelingLevel(state.activeType);
  syncTopbarModelingState(isModeling);
  panel.classList.toggle("hidden", !isModeling);
  if (!isModeling) {
    return;
  }
  ensureActiveGraphAndViews();
  const mobileViewport = isMobileViewport();
  const contextTools = canvasFocusToolMarkup();
  panel.innerHTML = `
    <div class="model-workbench-commandbar">
      <div class="workbench-section workbench-view-section">
        <span class="workbench-section-label">View</span>
        <div class="workbench-view-select-wrap${viewMenuOpen ? " is-open" : ""}">
          <button class="sidebar-select workbench-view-select"
                  id="activeViewSelect"
                  type="button"
                  title="${escapeHtml(state.activeType.toUpperCase())} view"
                  aria-haspopup="listbox"
                  aria-expanded="${viewMenuOpen ? "true" : "false"}">
            <span class="workbench-view-select-label">${escapeHtml(activeViewLabel())}</span>
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
      ${contextTools ? `<div class="workbench-divider"></div><div class="workbench-context-slot">${contextTools}</div>` : ""}
      <div class="workbench-divider"></div>
      <div class="workbench-action-group">
        <div class="workbench-tool-cluster workbench-inspect-cluster" aria-label="Inspect model structure">
          <span class="workbench-control-label">Inspect</span>
          <div class="workbench-btn-group workbench-inspect-inline">
            <button class="sidebar-inline-action" id="rootModelAttributesBtn"
                    type="button" aria-label="Edit root model attributes"
                    title="Edit root model attributes">Root</button>
            <button class="sidebar-inline-action${
              modelTreeMode === "elements" && !el.modelTreePanel?.classList.contains("hidden")
                ? " is-active"
                : ""
            }" id="modelTreeToggleBtn" type="button">Elements</button>
            <button class="sidebar-inline-action${
              modelTreeMode === "relationships" && !el.modelTreePanel?.classList.contains("hidden")
                ? " is-active"
                : ""
            }" id="relationshipTreeToggleBtn" type="button">Relationships</button>
          </div>
          ${
            mobileViewport
              ? ""
              : `<div class="workbench-view-select-wrap workbench-inspect-menu-wrap${inspectMenuOpen ? " is-open" : ""}">
            <button class="sidebar-select workbench-view-select workbench-inspect-select"
                    id="inspectMenuToggle"
                    type="button"
                    aria-haspopup="listbox"
                    aria-expanded="${inspectMenuOpen ? "true" : "false"}"
                    title="Inspect model structure">
              <span class="workbench-view-select-label">Inspect</span>
            </button>
            <span class="workbench-view-select-caret" aria-hidden="true"></span>
            <div class="workbench-view-menu workbench-inspect-menu${inspectMenuOpen ? "" : " hidden"}"
                 id="inspectMenu"
                 role="listbox"
                 aria-label="Inspect model structure">
              <button class="workbench-view-option" type="button" role="option" data-inspect-action="root">
                <span class="workbench-view-option-label">Root</span>
              </button>
              <button class="workbench-view-option${
                modelTreeMode === "elements" && !el.modelTreePanel?.classList.contains("hidden")
                  ? " is-active"
                  : ""
              }" type="button" role="option" data-inspect-action="elements">
                <span class="workbench-view-option-label">Elements</span>
              </button>
              <button class="workbench-view-option${
                modelTreeMode === "relationships" &&
                !el.modelTreePanel?.classList.contains("hidden")
                  ? " is-active"
                  : ""
              }" type="button" role="option" data-inspect-action="relationships">
                <span class="workbench-view-option-label">Relationships</span>
              </button>
            </div>
          </div>`
          }
        </div>
        <div class="workbench-tool-cluster workbench-arrange-cluster" aria-label="Arrange active view">
          <span class="workbench-control-label">Arrange</span>
          <div class="workbench-btn-group">
            <div class="workbench-layout-select-wrap${layoutMenuOpen ? " is-open" : ""}">
              <button class="sidebar-select workbench-view-select workbench-layout-select"
                      id="layoutStrategySelect"
                      type="button"
                      title="${escapeHtml(selectedLayoutStrategyConfig().title)}"
                      aria-haspopup="listbox"
                      aria-expanded="${layoutMenuOpen ? "true" : "false"}">
                <span class="workbench-view-select-label">${escapeHtml(
                  selectedLayoutStrategyConfig().label,
                )}</span>
              </button>
              <span class="workbench-view-select-caret workbench-layout-select-caret"
                    aria-hidden="true"></span>
              <div class="workbench-view-menu workbench-layout-menu${layoutMenuOpen ? "" : " hidden"}"
                   id="layoutStrategyMenu"
                   role="listbox"
                   aria-label="Auto layout strategies">
                ${layoutStrategyMenuMarkup()}
              </div>
            </div>
            <button class="sidebar-inline-action model-layout-btn"
                    id="workbenchAutoLayoutBtn"
                    type="button"
                    title="Arrange current view automatically">Apply</button>
          </div>
        </div>
      </div>
      <div class="workbench-spacer"></div>
      <div class="workbench-persist-group">
        <button class="sidebar-inline-action model-save-btn"
                id="saveModelBtn"
                type="button"
                aria-label="Save model"
                title="Save current model (Ctrl+S)">
          <span class="model-save-btn-label" id="saveModelBtnLabel">Save</span>
          <span aria-hidden="true" class="model-save-btn-progress">
            <span class="model-save-btn-progress-bar"></span>
          </span>
        </button>
      </div>
    </div>`;
  updateModelSaveUi();
  renderModelTree();
}

export async function openWorkbenchView(viewId) {
  viewMenuOpen = false;
  if (viewId !== state.views.activeViewId && state.views.byId.has(viewId)) {
    clearCanvasFocus();
  }
  if (setActiveViewId(viewId)) {
    materializeActiveView();
    renderPaletteCallback?.();
    await renderDiagramAsync({ full: true });
    renderViewWorkbench();
    saveCurrentTabGraphState();
    await fitViewportToDiagram({ fit: true, frames: 3 });
    const view = activeView();
    // A generated/imported view has no durable arrangement until its first activation.
    // Thereafter `autoLayoutApplied` is persisted by the backend (or set by a user drag),
    // making ordinary view switches strictly read-only.
    if (viewNeedsAutoLayout(view) && state.diagram.nodes.length) {
      const { autoLayoutCurrentDiagram } = await import("./model-ops.js");
      await autoLayoutCurrentDiagram({
        progress: true,
        status: true,
        force: false,
      });
      return;
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
  view.hidden ??= { elementIds: [], relationshipIds: [] };
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

async function toggleTreeRelationship(relationshipId, checked) {
  const view = activeView();
  if (!view || !relationshipId) {
    return;
  }
  syncActiveViewFromVisibleGraph();
  view.hidden ??= { elementIds: [], relationshipIds: [] };
  const hidden = new Set(safeArray(view.hidden.relationshipIds));
  if (checked) {
    hidden.delete(relationshipId);
  } else {
    hidden.add(relationshipId);
  }
  view.hidden.relationshipIds = [...hidden];
  materializeActiveView();
  renderDiagramCallback?.();
  renderViewWorkbench();
  saveCurrentTabGraphState();
  markModelDirty();
  setStatus(checked ? "Relationship shown in this view." : "Relationship hidden from this view.");
}

async function ensureTreeElementVisible(elementId) {
  const view = activeView();
  if (!view || !elementId) {
    return false;
  }
  view.hidden ??= { elementIds: [], relationshipIds: [] };
  const hidden = new Set(safeArray(view.hidden.elementIds));
  if (!hidden.has(elementId)) {
    return true;
  }
  syncActiveViewFromVisibleGraph();
  hidden.delete(elementId);
  view.hidden.elementIds = [...hidden];
  materializeActiveView();
  renderViewWorkbench();
  saveCurrentTabGraphState();
  await renderDiagramAsync({ full: true });
  return true;
}

async function ensureTreeRelationshipVisible(relationshipId) {
  const view = activeView();
  if (!view || !relationshipId) {
    return false;
  }
  view.hidden ??= { elementIds: [], relationshipIds: [] };
  const hidden = new Set(safeArray(view.hidden.relationshipIds));
  if (!hidden.has(relationshipId)) {
    return true;
  }
  syncActiveViewFromVisibleGraph();
  hidden.delete(relationshipId);
  view.hidden.relationshipIds = [...hidden];
  materializeActiveView();
  renderViewWorkbench();
  saveCurrentTabGraphState();
  await renderDiagramAsync({ full: true });
  return true;
}

async function locateTreeElement(elementId) {
  if (!elementId || !state.graph?.elementsById?.has(elementId)) {
    setStatus("Element is no longer available.");
    return;
  }
  await ensureTreeElementVisible(elementId);
  scrollToNodeAndHighlight(elementId);
  setStatus("Located element on canvas.");
}

async function locateTreeRelationship(relationshipId) {
  if (!relationshipId || !state.graph?.relationshipsById?.has(relationshipId)) {
    setStatus("Relationship is no longer available.");
    return;
  }
  await ensureTreeRelationshipVisible(relationshipId);
  if (scrollToConnectionAndHighlight(relationshipId)) {
    setStatus("Located relationship on canvas.");
  } else {
    setStatus("Relationship is not visible in the current canvas.");
  }
}

async function selectTreeElement(elementId) {
  if (!elementId || !state.graph?.elementsById?.has(elementId)) {
    setStatus("Element is no longer available.");
    return;
  }
  await ensureTreeElementVisible(elementId);
  openAttributePanel(elementId);
  renderViewWorkbench();
  setStatus("Element selected.");
}

async function selectTreeRelationship(relationshipId) {
  if (!relationshipId || !state.graph?.relationshipsById?.has(relationshipId)) {
    setStatus("Relationship is no longer available.");
    return;
  }
  await ensureTreeRelationshipVisible(relationshipId);
  openConnectionPanel(relationshipId);
  renderViewWorkbench();
  setStatus("Relationship selected.");
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
      return;
    }
    const relationshipId = target?.dataset?.treeRelationshipId;
    if (relationshipId) {
      toggleTreeRelationship(relationshipId, Boolean(target.checked));
    }
  });
  el.modelTreeBody?.addEventListener("click", (event) => {
    const target = event.target instanceof Element ? event.target : null;
    if (target?.closest(".model-tree-node-toggle")) {
      return;
    }
    const elementId = target?.closest("[data-tree-locate-element-id]")?.dataset
      ?.treeLocateElementId;
    if (elementId) {
      event.preventDefault();
      event.stopPropagation();
      void locateTreeElement(elementId);
      return;
    }
    const relationshipId = target?.closest("[data-tree-locate-relationship-id]")?.dataset
      ?.treeLocateRelationshipId;
    if (relationshipId) {
      event.preventDefault();
      event.stopPropagation();
      void locateTreeRelationship(relationshipId);
      return;
    }
    const elementRowId = target?.closest("[data-tree-select-element-id]")?.dataset
      ?.treeSelectElementId;
    if (elementRowId) {
      event.preventDefault();
      void selectTreeElement(elementRowId);
      return;
    }
    const relationshipRowId = target?.closest("[data-tree-select-relationship-id]")?.dataset
      ?.treeSelectRelationshipId;
    if (relationshipRowId) {
      event.preventDefault();
      void selectTreeRelationship(relationshipRowId);
    }
  });
  el.modelTreeBody?.addEventListener("keydown", (event) => {
    if (event.key !== "Enter" && event.key !== " ") {
      return;
    }
    const target = event.target instanceof Element ? event.target : null;
    if (!target?.matches("[data-tree-select-element-id], [data-tree-select-relationship-id]")) {
      return;
    }
    event.preventDefault();
    const elementId = target.dataset?.treeSelectElementId;
    if (elementId) {
      void selectTreeElement(elementId);
      return;
    }
    const relationshipId = target.dataset?.treeSelectRelationshipId;
    if (relationshipId) {
      void selectTreeRelationship(relationshipId);
    }
  });
  el.modelTreeBody?.addEventListener("input", (event) => {
    const target = event.target instanceof Element ? event.target : null;
    if (!target?.matches("[data-model-tree-filter]")) {
      return;
    }
    const selectionStart = target.selectionStart;
    const selectionEnd = target.selectionEnd;
    const selectionDirection = target.selectionDirection;
    modelTreeFilter = target.value || "";
    renderModelTree();
    const filterInput = el.modelTreeBody?.querySelector("[data-model-tree-filter]");
    filterInput?.focus();
    if (
      filterInput instanceof HTMLInputElement &&
      typeof selectionStart === "number" &&
      typeof selectionEnd === "number"
    ) {
      filterInput.setSelectionRange(selectionStart, selectionEnd, selectionDirection || "none");
    }
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
    if (target?.closest("#activeViewSelect")) {
      viewMenuOpen = !viewMenuOpen;
      layoutMenuOpen = false;
      inspectMenuOpen = false;
      renderViewWorkbench();
      event.stopPropagation();
      return;
    }
    if (target?.closest("#layoutStrategySelect")) {
      layoutMenuOpen = !layoutMenuOpen;
      viewMenuOpen = false;
      inspectMenuOpen = false;
      renderViewWorkbench();
      event.stopPropagation();
      return;
    }
    if (target?.closest("#canvasFocusBackBtn")) {
      closeCanvasFocus();
      renderViewWorkbench();
      return;
    }
    const selectedView = target?.closest("[data-view-option]")?.dataset?.viewOption;
    if (selectedView) {
      void openWorkbenchView(selectedView);
      return;
    }
    const selectedLayout = target?.closest("[data-layout-strategy]")?.dataset?.layoutStrategy;
    if (selectedLayout) {
      setLayoutStrategy(selectedLayout);
      return;
    }
    if (target?.closest("#workbenchAutoLayoutBtn")) {
      runManualAutoLayout();
      return;
    }
    if (target?.closest("#rootModelAttributesBtn")) {
      openRootModelAttributePanel();
      return;
    }
    if (target?.closest("#inspectMenuToggle")) {
      inspectMenuOpen = !inspectMenuOpen;
      viewMenuOpen = false;
      layoutMenuOpen = false;
      renderViewWorkbench();
      event.stopPropagation();
      return;
    }
    const inspectAction = target?.closest("[data-inspect-action]")?.dataset?.inspectAction;
    if (inspectAction) {
      inspectMenuOpen = false;
      if (inspectAction === "root") {
        openRootModelAttributePanel();
        renderViewWorkbench();
      } else if (inspectAction === "elements" || inspectAction === "relationships") {
        const open =
          el.modelTreePanel?.classList.contains("hidden") || modelTreeMode !== inspectAction;
        setTreeOpen(open, inspectAction);
        renderViewWorkbench();
      }
      return;
    }
    if (target?.closest("#modelTreeToggleBtn")) {
      const open = el.modelTreePanel?.classList.contains("hidden") || modelTreeMode !== "elements";
      setTreeOpen(open, "elements");
      renderViewWorkbench();
      return;
    }
    if (target?.closest("#relationshipTreeToggleBtn")) {
      const open =
        el.modelTreePanel?.classList.contains("hidden") || modelTreeMode !== "relationships";
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
    if (!target?.closest(".workbench-layout-select-wrap") && layoutMenuOpen) {
      layoutMenuOpen = false;
      renderViewWorkbench();
    }
    if (!target?.closest(".workbench-inspect-menu-wrap") && inspectMenuOpen) {
      inspectMenuOpen = false;
      renderViewWorkbench();
    }
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && (viewMenuOpen || layoutMenuOpen || inspectMenuOpen)) {
      viewMenuOpen = false;
      layoutMenuOpen = false;
      inspectMenuOpen = false;
      renderViewWorkbench();
    }
  });
  window.addEventListener("model-tools-state-change", () => {
    renderViewWorkbench();
  });
}

export function initViewWorkbench({ renderDiagram, renderPalette } = {}) {
  renderDiagramCallback = renderDiagram || renderDiagramCallback;
  renderPaletteCallback = renderPalette || renderPaletteCallback;
  ensureHost();
  bindWorkbenchEvents();
  bindTreeEvents();
  lastMobileViewport = isMobileViewport();
  window.addEventListener("resize", () => {
    const nextMobileViewport = isMobileViewport();
    if (nextMobileViewport === lastMobileViewport) {
      return;
    }
    lastMobileViewport = nextMobileViewport;
    renderViewWorkbench();
  });
  renderViewWorkbench();
}
