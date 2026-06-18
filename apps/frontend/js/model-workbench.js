import { state } from "./state.js";
import { el } from "./dom.js";
import { escapeHtml, genId } from "./utils.js";
import { getDefaultNode } from "./diagram.js";
import {
  activeView,
  addConnectionToGraphAndActiveView,
  addNodeToGraphAndActiveView,
  saveCurrentTabGraphState,
  syncActiveViewFromVisibleGraph,
} from "./graph-store.js";
import {
  modelingElementDefinition,
  modelingLevelConfig,
  modelingRootContainments,
  modelingTypeMatches,
  modelingViewDefinition,
} from "./modeling-config-data.js";
import { markModelDirty } from "./model-save-ui.js";
import { setStatus } from "./status.js";
import {
  activeWorkbenchRepresentation,
  applyWorkbenchEdgeMode,
  bindWorkbenchInteractionShield,
  commitWorkbenchModelChange,
  downloadWorkbenchCsv,
  ensureWorkbenchSurface,
  renderLevelGuidePanel,
  renderWorkbenchBoard,
  renderWorkbenchBoardCard,
  renderWorkbenchControls,
  renderWorkbenchDashboard,
  renderWorkbenchDetail,
  renderWorkbenchMatrixSection,
  renderWorkbenchRegister,
  renderWorkbenchSliceOption,
  renderWorkbenchSliceSelect,
  renderWorkbenchSurfaceLayout,
  renderWorkbenchToolbar,
  setWorkbenchRepresentation,
  STANDARD_EDGE_MODES,
} from "./workbench-common.js";
import {
  addReferenceValue,
  compactRefLabels,
  elementLabel,
  missingRequiredFeatures,
  refId,
  refIds,
  rootContainmentForType,
} from "./model-utils.js";

let surface = null;
let bound = false;
let renderDiagramCallback = null;
let renderPaletteCallback = null;
let openAttributePanelCallback = null;
let openConnectionPanelCallback = null;
let searchRenderTimer = 0;
let openSliceMenu = "";

const DEFAULT_REPRESENTATIONS = {
  dashboard: "dashboard",
  readiness: "board",
  traceability: "matrix",
};

const WORKBENCH_MODES = [
  ["diagram", "Diagram"],
  ["dashboard", "Dashboard"],
  ["register", "Register"],
  ["matrix", "Matrix"],
  ["board", "Board"],
  ["detail", "Detail"],
  ["guide", "Guide"],
];

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function workbenchState(typeKey = state.activeType) {
  state.workbenchByType ??= {};
  const legacy = state[`${typeKey}Workbench`];
  if (!state.workbenchByType[typeKey]) {
    state.workbenchByType[typeKey] = legacy || {
      representationByViewId: {},
      registerByViewId: {},
      search: "",
      missingOnly: false,
      sliceKind: "",
      sliceValue: "",
      edgeMode: "both",
      sortKey: "name",
      hidPalette: false,
    };
  }
  return state.workbenchByType[typeKey];
}

function ensureSurface() {
  surface = ensureWorkbenchSurface(surface, "modelWorkbenchSurface");
  return surface;
}

function currentLevel() {
  try {
    return modelingLevelConfig(state.activeType);
  } catch {
    return null;
  }
}

function currentProfile(typeKey = state.activeType) {
  const view = activeView();
  try {
    const definition = modelingViewDefinition(typeKey, view);
    if (definition?.viewpoint) {
      return String(definition.viewpoint);
    }
    if (definition?.id) {
      return String(definition.id);
    }
  } catch {
    // fall through
  }
  return String(view?.viewpoint || view?.kind || view?.id || "model").toLowerCase();
}

function representationDefaults(typeKey = state.activeType) {
  const defaults = { ...DEFAULT_REPRESENTATIONS };
  safeArray(modelingLevelConfig(typeKey).viewDefinitions).forEach((view) => {
    if (view?.viewpoint) {
      defaults[String(view.viewpoint)] = layoutHintToRepresentation(view.layoutHint);
    }
    if (view?.id) {
      defaults[String(view.id)] = layoutHintToRepresentation(view.layoutHint);
    }
  });
  return defaults;
}

function layoutHintToRepresentation(hint) {
  const normalized = String(hint || "").toLowerCase();
  if (normalized.includes("matrix")) {
    return "matrix";
  }
  if (normalized.includes("table") || normalized.includes("register")) {
    return "register";
  }
  if (normalized.includes("board")) {
    return "board";
  }
  if (normalized.includes("dashboard")) {
    return "dashboard";
  }
  return "diagram";
}

function activeRepresentation(typeKey, profile) {
  const viewId = activeView()?.id || typeKey;
  return activeWorkbenchRepresentation(
    workbenchState(typeKey),
    viewId,
    profile,
    representationDefaults(typeKey),
  );
}

function setActiveRepresentation(mode) {
  const typeKey = state.activeType;
  const viewId = activeView()?.id || typeKey;
  setWorkbenchRepresentation(
    workbenchState(typeKey),
    viewId,
    mode,
    renderModelWorkbenchSurface,
    renderDiagramCallback,
    renderPaletteCallback,
  );
}

function rootContainments(typeKey = state.activeType) {
  try {
    return modelingRootContainments(typeKey);
  } catch {
    return [];
  }
}

function activeRegister(typeKey, profile) {
  const viewId = activeView()?.id || typeKey;
  const configured = workbenchState(typeKey).registerByViewId[viewId];
  if (configured) {
    return configured;
  }
  const fromView = safeArray(modelingViewDefinition(typeKey, activeView())?.elementTypes)[0];
  const byView = fromView ? rootContainmentForType(typeKey, fromView)?.feature : "";
  return (
    byView || rootContainments(typeKey).find((entry) => !entry.relationshipOnly)?.feature || "all"
  );
}

function setActiveRegister(feature) {
  const typeKey = state.activeType;
  const viewId = activeView()?.id || typeKey;
  workbenchState(typeKey).registerByViewId[viewId] = feature;
}

function elements() {
  return [...(state.graph?.elementsById || new Map()).values()];
}

function relationships() {
  return [...(state.graph?.relationshipsById || new Map()).values()];
}

function typeMatchesAny(typeKey, row, types) {
  return safeArray(types).some((type) => {
    try {
      return modelingTypeMatches(typeKey, type, row.eClass || row.type);
    } catch {
      return type === (row.eClass || row.type);
    }
  });
}

function rowsForRegister(typeKey, feature) {
  if (feature === "all") {
    return [...elements(), ...relationships()];
  }
  const containment = rootContainments(typeKey).find((entry) => entry.feature === feature);
  if (!containment) {
    return [];
  }
  const rows = elements().filter((row) => typeMatchesAny(typeKey, row, containment.types));
  if (containment.relationshipOnly) {
    rows.push(...relationships().filter((row) => typeMatchesAny(typeKey, row, containment.types)));
  }
  return dedupeById(rows);
}

function dedupeById(items) {
  const seen = new Set();
  return items.filter((item) => {
    const id = String(item?.id || "");
    if (!id || seen.has(id)) {
      return false;
    }
    seen.add(id);
    return true;
  });
}

function valueText(value) {
  if (Array.isArray(value)) {
    return value.map(valueText).filter(Boolean).join(", ");
  }
  if (value && typeof value === "object") {
    return refId(value) || value.name || value.label || JSON.stringify(value);
  }
  return String(value ?? "");
}

function matchesSearch(typeKey, row) {
  const query = String(workbenchState(typeKey).search || "")
    .trim()
    .toLowerCase();
  if (!query) {
    return true;
  }
  return [
    row.id,
    row.eClass,
    row.name,
    row.label,
    ...Object.values(row).filter((value) => typeof value === "string"),
  ]
    .join(" ")
    .toLowerCase()
    .includes(query);
}

function rowInSlice(typeKey, row) {
  const wb = workbenchState(typeKey);
  const kind = String(wb.sliceKind || "");
  const value = String(wb.sliceValue || "");
  if (!kind) {
    return true;
  }
  if (kind === "lifecycle") {
    return !value || String(row.lifecycleStatus || row.status || "") === value;
  }
  if (kind === "type") {
    return !value || String(row.eClass || row.type || "") === value;
  }
  if (kind === "missing") {
    return missingRequiredFeatures(typeKey, row).length > 0;
  }
  return true;
}

function filteredRows(typeKey, feature) {
  const wb = workbenchState(typeKey);
  const rows = rowsForRegister(typeKey, feature)
    .filter((row) => matchesSearch(typeKey, row))
    .filter((row) => rowInSlice(typeKey, row));
  const missingFiltered = wb.missingOnly
    ? rows.filter((row) => missingRequiredFeatures(typeKey, row).length)
    : rows;
  const sortKey = wb.sortKey || "name";
  return missingFiltered.sort((a, b) =>
    valueText(a?.[sortKey] || elementLabel(a)).localeCompare(
      valueText(b?.[sortKey] || elementLabel(b)),
    ),
  );
}

function fieldDefinition(typeKey, type, fieldName) {
  try {
    const definition = modelingElementDefinition(typeKey, type);
    return (
      [...safeArray(definition?.attributes), ...safeArray(definition?.references)].find(
        (field) => field.name === fieldName,
      ) || null
    );
  } catch {
    return null;
  }
}

function columnsFor(typeKey, feature, rows) {
  const containment = rootContainments(typeKey).find((entry) => entry.feature === feature);
  const configured = safeArray(containment?.types).flatMap((type) => {
    try {
      return safeArray(modelingElementDefinition(typeKey, type)?.visibleFields);
    } catch {
      return [];
    }
  });
  const fromRows = rows.flatMap((row) =>
    Object.keys(row || {}).filter(
      (key) => !key.startsWith("__") && !["id", "eClass", "type", "x", "y", "label"].includes(key),
    ),
  );
  return [...new Set(["name", ...configured, ...fromRows])].filter(Boolean).slice(0, 12);
}

function controlForField(typeKey, row, field) {
  const definition = fieldDefinition(typeKey, row.eClass, field) || {};
  const value = row[field];
  if (definition.readonly || field === "id") {
    return `<span class="model-cell-readonly">${escapeHtml(valueText(value))}</span>`;
  }
  if (
    definition.kind === "reference" ||
    Array.isArray(value) ||
    (value && typeof value === "object")
  ) {
    return `<span class="model-ref-cell">${escapeHtml(compactRefLabels(value, state.graph.elementsById, 4) || valueText(value))}</span>`;
  }
  if (definition.fieldType === "boolean" || typeof value === "boolean") {
    return `<input class="model-table-check" data-${typeKey}-field="${escapeHtml(field)}"
        data-${typeKey}-row="${escapeHtml(row.id)}" type="checkbox" ${value ? "checked" : ""}>`;
  }
  if (definition.fieldType === "select" && Array.isArray(definition.options)) {
    return `<select class="model-table-input" data-${typeKey}-field="${escapeHtml(field)}"
        data-${typeKey}-row="${escapeHtml(row.id)}"><option value=""></option>${definition.options
          .map(
            (option) =>
              `<option value="${escapeHtml(option)}" ${String(value || "") === String(option) ? "selected" : ""}>${escapeHtml(option)}</option>`,
          )
          .join("")}</select>`;
  }
  const inputType =
    definition.fieldType === "number" || typeof value === "number" ? "number" : "text";
  return `<input class="model-table-input" data-${typeKey}-field="${escapeHtml(field)}"
      data-${typeKey}-row="${escapeHtml(row.id)}" type="${inputType}" value="${escapeHtml(valueText(value))}">`;
}

function rowBadge(typeKey, row) {
  const missing = missingRequiredFeatures(typeKey, row);
  return `<span class="model-type-badge">${escapeHtml(row.eClass || row.kind || "Element")}</span>${
    missing.length
      ? `<span class="model-missing-badge" title="${escapeHtml(missing.join(", "))}">${missing.length}</span>`
      : ""
  }`;
}

function registerOptionsMarkup(typeKey, activeFeature) {
  const entries = [
    { feature: "all", title: "All Visible Types" },
    ...rootContainments(typeKey).map((entry) => ({
      feature: entry.feature,
      title: entry.title || entry.feature,
    })),
  ];
  return entries
    .map(
      (entry) =>
        `<option value="${escapeHtml(entry.feature)}" ${activeFeature === entry.feature ? "selected" : ""}>${escapeHtml(entry.title)}</option>`,
    )
    .join("");
}

function renderRegister(typeKey, profile) {
  const feature = activeRegister(typeKey, profile);
  const rows = filteredRows(typeKey, feature);
  const columns = columnsFor(typeKey, feature, rows);
  const containment = rootContainments(typeKey).find((entry) => entry.feature === feature);
  const addType = safeArray(containment?.types).find((type) => {
    try {
      return modelingElementDefinition(typeKey, type)?.creatable !== false;
    } catch {
      return false;
    }
  });
  const toolbarHtml = renderWorkbenchToolbar([
    `<select class="model-select" data-${typeKey}-register>${registerOptionsMarkup(typeKey, feature)}</select>`,
    addType
      ? `<button class="model-action model-action-primary" data-${typeKey}-add-type="${escapeHtml(addType)}" type="button">Add ${escapeHtml(addType)}</button>`
      : "",
    `<button class="model-action" data-${typeKey}-export="${escapeHtml(feature)}" type="button">Export CSV</button>`,
  ]);
  return renderWorkbenchRegister({
    typeKey,
    toolbarHtml,
    rows,
    columns,
    getLabel: elementLabel,
    getBadgeHtml: (row) => rowBadge(typeKey, row),
    renderCell: (row, field) => controlForField(typeKey, row, field),
    emptyText: "No model elements in this view.",
  });
}

function renderDashboard(typeKey) {
  const missing = elements().filter((row) => missingRequiredFeatures(typeKey, row).length).length;
  const metrics = [
    ["Elements", elements().length],
    ["Relationships", relationships().length],
    ["Types", new Set(elements().map((row) => row.eClass)).size],
    ["Missing", missing],
  ];
  const requiredContainments = rootContainments(typeKey).filter((entry) => entry.required);
  const missingRoot = requiredContainments
    .filter((entry) => !rowsForRegister(typeKey, entry.feature).length)
    .map((entry) => entry.title || entry.feature);
  const primaryHtml = `<section class="model-root-form">
    <div class="model-section-title">Model Foundation</div>
    ${
      missingRoot.length
        ? `<div class="model-alert">Missing required foundation: ${escapeHtml(missingRoot.join(", "))}</div>`
        : `<div class="model-ok">Foundation requirements are satisfied.</div>`
    }
  </section>`;
  const secondaryHtml = `<section class="model-view-entry-list">
    <div class="model-section-title">Actions</div>
    <button class="model-view-entry" data-${typeKey}-create-root type="button">
      <span>${missingRoot.length ? "Create Foundation" : "Refresh Foundation"}</span>
      <strong>foundation</strong>
    </button>
    <button class="model-view-entry" data-${typeKey}-mode="diagram" type="button">
      <span>Open Diagram</span>
      <strong>diagram</strong>
    </button>
  </section>`;
  return renderWorkbenchDashboard({ metrics, primaryHtml, secondaryHtml });
}

function renderMatrix(typeKey) {
  const rows = elements();
  const rels = relationships();
  const sourceIds = new Set(
    rels.map((relationship) => relationship.sourceElementId).filter(Boolean),
  );
  const targetIds = new Set(
    rels.map((relationship) => relationship.targetElementId).filter(Boolean),
  );
  const sources = rows.filter((row) => sourceIds.has(row.id));
  const targets = rows.filter((row) => targetIds.has(row.id));
  return renderWorkbenchMatrixSection({
    title: "Relationships",
    rows: sources,
    columns: targets,
    typeKey,
    getRowLabel: elementLabel,
    getColumnLabel: elementLabel,
    getColumnMeta: (column) => column.eClass || "",
    getCellHtml: (row, column) => {
      const rel = rels.find(
        (candidate) =>
          candidate.sourceElementId === row.id && candidate.targetElementId === column.id,
      );
      return rel
        ? `<td class="is-linked"><button class="model-icon-action" data-${typeKey}-open="${escapeHtml(rel.id)}" type="button">${escapeHtml(rel.kind || rel.eClass || "link")}</button></td>`
        : `<td></td>`;
    },
    emptyText: "No relationships in this view.",
  });
}

function renderBoard(typeKey) {
  const rows = elements();
  const groups = [
    ["Missing Required", (row) => missingRequiredFeatures(typeKey, row).length > 0],
    ["Draft", (row) => /draft|open|new/i.test(String(row.status || row.lifecycleStatus || ""))],
    ["Other", () => true],
  ];
  return renderWorkbenchBoard({
    lanes: groups.map(([title, predicate], index) => {
      const previous = groups.slice(0, index).map(([, item]) => item);
      const items = rows.filter((row) => predicate(row) && !previous.some((item) => item(row)));
      return {
        title,
        count: items.length,
        cards: items.map((row) =>
          renderWorkbenchBoardCard({
            typeKey,
            id: row.id,
            title: elementLabel(row),
            meta: row.eClass || "",
            body:
              missingRequiredFeatures(typeKey, row).join(", ") ||
              row.status ||
              row.lifecycleStatus ||
              "",
          }),
        ),
        emptyText: "No items",
      };
    }),
  });
}

function renderDetail(typeKey, profile) {
  const selected = state.selectedNodeId
    ? state.graph?.elementsById?.get(state.selectedNodeId)
    : null;
  const register = activeRegister(typeKey, profile);
  const row = selected || filteredRows(typeKey, register)[0] || elements()[0] || relationships()[0];
  if (!row) {
    return renderWorkbenchDetail({
      typeKey,
      row: null,
      valueText,
      emptyText: "No model element selected.",
    });
  }
  const definition = modelingElementDefinition(typeKey, row.eClass || row.type);
  const fields = [
    ...safeArray(definition?.visibleFields),
    ...safeArray(definition?.attributes)
      .filter((field) => field?.required)
      .map((field) => field.name),
    ...safeArray(definition?.references)
      .filter((field) => field?.required)
      .map((field) => field.name),
    ...Object.keys(row),
  ].filter((field) => !String(field).startsWith("__"));
  return renderWorkbenchDetail({
    typeKey,
    row,
    fields: [...new Set(fields)].slice(0, 18),
    title: elementLabel(row),
    typeLabel: row.eClass || row.type || "Element",
    valueText,
    stats: [
      ["missing required", missingRequiredFeatures(typeKey, row).join(", ") || "none"],
      ["references", Object.values(row).flatMap(refIds).length],
    ],
  });
}

function sliceItems(typeKey) {
  const wb = workbenchState(typeKey);
  if (wb.sliceKind === "type") {
    return [
      ...new Set(
        elements()
          .map((row) => row.eClass)
          .filter(Boolean),
      ),
    ]
      .sort()
      .map((type) => ({ id: type, name: type }));
  }
  if (wb.sliceKind === "lifecycle") {
    return [
      ...new Set(
        elements()
          .map((row) => String(row.lifecycleStatus || row.status || ""))
          .filter(Boolean),
      ),
    ]
      .sort()
      .map((status) => ({ id: status, name: status }));
  }
  if (wb.sliceKind === "missing") {
    return [{ id: "true", name: "Missing required" }];
  }
  return [];
}

function sliceKinds(typeKey) {
  return safeArray(modelingLevelConfig(typeKey).workbench?.sliceKinds).length
    ? modelingLevelConfig(typeKey).workbench.sliceKinds
    : [
        { value: "", label: "All slices" },
        { value: "type", label: "Type" },
        { value: "lifecycle", label: "Lifecycle" },
        { value: "missing", label: "Missing required" },
      ];
}

function sliceKindLabel(typeKey) {
  const value = workbenchState(typeKey).sliceKind || "";
  return sliceKinds(typeKey).find((entry) => entry.value === value)?.label || "All slices";
}

function sliceValueLabel(typeKey) {
  const value = workbenchState(typeKey).sliceValue || "";
  if (!value) {
    return "Any";
  }
  const item = sliceItems(typeKey).find((candidate) => candidate.id === value);
  return item ? elementLabel(item) : value;
}

function sliceMenuMarkup(typeKey, optionKind) {
  const options =
    optionKind === "kind"
      ? sliceKinds(typeKey).map((entry) => ({ id: entry.value, name: entry.label }))
      : [{ id: "", name: "Any" }, ...sliceItems(typeKey)];
  const selected =
    optionKind === "kind" ? workbenchState(typeKey).sliceKind : workbenchState(typeKey).sliceValue;
  return options
    .map((item) =>
      renderWorkbenchSliceOption({
        typeKey,
        optionKind,
        value: item.id,
        label: item.name,
        selected: String(selected || "") === String(item.id || ""),
      }),
    )
    .join("");
}

function sliceSelectMarkup(typeKey, optionKind) {
  const open = openSliceMenu === optionKind;
  return renderWorkbenchSliceSelect({
    typeKey,
    optionKind,
    open,
    label: optionKind === "kind" ? sliceKindLabel(typeKey) : sliceValueLabel(typeKey),
    menuHtml: sliceMenuMarkup(typeKey, optionKind),
  });
}

function renderControls(typeKey, profile, representation) {
  const level = currentLevel();
  return renderWorkbenchControls({
    typeKey,
    title: activeView()?.name || level?.displayName || "Model View",
    profile,
    representation,
    workbenchState: workbenchState(typeKey),
    sliceControlsHtml: `${sliceSelectMarkup(typeKey, "kind")}${sliceSelectMarkup(typeKey, "value")}`,
    edgeModes: STANDARD_EDGE_MODES,
    modes: WORKBENCH_MODES,
    searchPlaceholder: `Search ${level?.displayName || "model"}`,
  });
}

function renderBody(typeKey, profile, representation) {
  if (representation === "dashboard") {
    return renderDashboard(typeKey);
  }
  if (representation === "register") {
    return renderRegister(typeKey, profile);
  }
  if (representation === "matrix") {
    return renderMatrix(typeKey);
  }
  if (representation === "board") {
    return renderBoard(typeKey);
  }
  if (representation === "detail") {
    return renderDetail(typeKey, profile);
  }
  if (representation === "guide") {
    return renderLevelGuidePanel(typeKey);
  }
  return "";
}

export function renderModelWorkbenchSurface() {
  const host = ensureSurface();
  const typeKey = state.activeType;
  const profile = currentProfile(typeKey);
  const representation = activeRepresentation(typeKey, profile);
  renderWorkbenchSurfaceLayout({
    host,
    activeType: state.activeType,
    expectedType: typeKey,
    minimized: state.modelingToolsMinimized,
    representation,
    controlsHtml: renderControls(typeKey, profile, representation),
    bodyHtml: renderBody(typeKey, profile, representation),
    workbenchState: workbenchState(typeKey),
    surfaceActiveClass: `${typeKey}-surface-active`,
    surfaceDockClass: `${typeKey}-surface-dock`,
  });
}

function scheduleSearchRender() {
  if (searchRenderTimer) {
    window.clearTimeout(searchRenderTimer);
  }
  searchRenderTimer = window.setTimeout(() => {
    searchRenderTimer = 0;
    renderModelWorkbenchSurface();
  }, 90);
}

function currentCenter() {
  const rect = el.canvasViewport?.getBoundingClientRect();
  if (!rect) {
    return { x: 120, y: 120 };
  }
  return {
    x: Math.round((rect.width / 2 - state.viewport.x) / state.viewport.scale),
    y: Math.round((rect.height / 2 - state.viewport.y) / state.viewport.scale),
  };
}

function addNode(typeKey, type, x, y, name = "") {
  const node = getDefaultNode(typeKey, type, Math.round(x), Math.round(y));
  if (name) {
    node.label = name;
    node.meta.name = name;
    node.meta.label = name;
  }
  state.diagram.nodes.push(node);
  addNodeToGraphAndActiveView(node);
  return node;
}

function connect(source, target, kind) {
  if (!source?.id || !target?.id || !kind) {
    return null;
  }
  const edge = {
    id: genId("e"),
    sourceId: source.id,
    targetId: target.id,
    kind,
  };
  state.diagram.connections.push(edge);
  addConnectionToGraphAndActiveView(edge);
  return edge;
}

function createElement(typeKey, type) {
  syncActiveViewFromVisibleGraph();
  const center = currentCenter();
  const node = addNode(typeKey, type, center.x, center.y, type);
  safeArray(modelingLevelConfig(typeKey).scaffoldRecipes)
    .filter((recipe) => recipe?.type === type)
    .forEach((recipe) => applyScaffoldRecipe(typeKey, node, recipe, center));
  commitModelChange(`Added ${type}`);
  openAttributePanelCallback?.(node.id);
}

function applyScaffoldRecipe(typeKey, node, recipe, center) {
  safeArray(recipe.defaults).forEach((entry) => {
    if (entry?.field) {
      node.meta[entry.field] = entry.value;
    }
  });
  safeArray(recipe.children).forEach((child, index) => {
    const childNode = addNode(
      typeKey,
      child.type,
      center.x + Number(child.x || 180 + index * 60),
      center.y + Number(child.y || 120),
      child.label || child.type,
    );
    if (child.containmentFeature) {
      childNode.meta.__ownerId = node.id;
      childNode.meta.__containmentFeature = child.containmentFeature;
      addReferenceValue(node.meta, child.containmentFeature, childNode.id, true);
    }
    if (child.relationshipKind) {
      connect(node, childNode, child.relationshipKind);
    }
  });
}

function createRequiredRoot(typeKey) {
  const center = currentCenter();
  rootContainments(typeKey)
    .filter((entry) => entry.required)
    .forEach((entry, index) => {
      const exists = rowsForRegister(typeKey, entry.feature).length > 0;
      const type = safeArray(entry.types)[0];
      if (!exists && type) {
        addNode(typeKey, type, center.x + index * 220, center.y, type);
      }
    });
  commitModelChange("Completed model foundation");
}

function commitModelChange(message) {
  commitWorkbenchModelChange({
    typeKey: state.activeType,
    renderWorkbench: renderModelWorkbenchSurface,
    renderDiagram: renderDiagramCallback,
    renderPalette: renderPaletteCallback,
    message,
    syncActiveViewFromVisibleGraph,
    saveCurrentTabGraphState,
    markModelDirty,
    setStatus,
  });
}

function updateField(typeKey, rowId, field, rawValue, inputType = "text") {
  const row = state.graph.elementsById.get(rowId) || state.graph.relationshipsById.get(rowId);
  if (!row) {
    return;
  }
  const definition = fieldDefinition(typeKey, row.eClass, field);
  let value = rawValue;
  if (inputType === "checkbox") {
    value = Boolean(rawValue);
  } else if (definition?.fieldType === "number") {
    value = Number(rawValue);
  }
  row[field] = value;
  const visible = state.nodesById.get(rowId);
  if (visible?.meta) {
    visible.meta[field] = value;
    if (field === "name") {
      visible.label = String(value || visible.label);
      visible.meta.name = visible.label;
      visible.meta.label = visible.label;
    }
  }
  commitModelChange(`Updated ${field}`);
}

function exportCsv(typeKey, feature) {
  const rows = filteredRows(typeKey, feature);
  const columns = ["id", "eClass", ...columnsFor(typeKey, feature, rows)];
  downloadWorkbenchCsv(`${typeKey}-${feature}.csv`, rows, columns, valueText);
}

function bindSurfaceEvents() {
  const host = ensureSurface();
  if (bound) {
    return;
  }
  bound = true;
  bindWorkbenchInteractionShield(host);
  host.addEventListener("click", (event) => {
    const target = event.target instanceof Element ? event.target : null;
    const typeKey = state.activeType;
    const mode = target?.closest(`[data-${typeKey}-mode]`)?.dataset?.[`${typeKey}Mode`];
    if (mode) {
      setActiveRepresentation(mode);
      return;
    }
    const edgeMode = target?.closest(`[data-${typeKey}-edge-mode]`)?.dataset?.[
      `${typeKey}EdgeMode`
    ];
    if (edgeMode) {
      applyWorkbenchEdgeMode({
        typeKey,
        workbenchState: workbenchState(typeKey),
        mode: edgeMode,
        graph: state.graph,
        renderWorkbench: renderModelWorkbenchSurface,
        renderDiagram: renderDiagramCallback,
        renderPalette: renderPaletteCallback,
      });
      return;
    }
    const addType = target?.closest(`[data-${typeKey}-add-type]`)?.dataset?.[`${typeKey}AddType`];
    if (addType) {
      createElement(typeKey, addType);
      return;
    }
    const openId = target?.closest(`[data-${typeKey}-open]`)?.dataset?.[`${typeKey}Open`];
    if (openId) {
      if (state.graph?.relationshipsById?.has(openId)) {
        openConnectionPanelCallback?.(openId);
      } else {
        openAttributePanelCallback?.(openId);
      }
      return;
    }
    if (target?.closest(`[data-${typeKey}-create-root]`)) {
      createRequiredRoot(typeKey);
      return;
    }
    const exportFeature = target?.closest(`[data-${typeKey}-export]`)?.dataset?.[
      `${typeKey}Export`
    ];
    if (exportFeature) {
      exportCsv(typeKey, exportFeature);
      return;
    }
    const sortKey = target?.closest(`[data-${typeKey}-sort]`)?.dataset?.[`${typeKey}Sort`];
    if (sortKey) {
      workbenchState(typeKey).sortKey = sortKey;
      renderModelWorkbenchSurface();
      return;
    }
    const sliceToggle = target?.closest(`[data-${typeKey}-slice-toggle]`)?.dataset?.[
      `${typeKey}SliceToggle`
    ];
    if (sliceToggle) {
      openSliceMenu = openSliceMenu === sliceToggle ? "" : sliceToggle;
      renderModelWorkbenchSurface();
      return;
    }
    const sliceOption = target?.closest(`[data-${typeKey}-slice-option]`);
    if (sliceOption) {
      const optionKind = sliceOption.dataset[`${typeKey}SliceOption`];
      const value = sliceOption.dataset[`${typeKey}SliceOptionValue`] || "";
      if (optionKind === "kind") {
        workbenchState(typeKey).sliceKind = value;
        workbenchState(typeKey).sliceValue = "";
      } else {
        workbenchState(typeKey).sliceValue = value;
      }
      openSliceMenu = "";
      renderModelWorkbenchSurface();
    }
  });
  host.addEventListener("change", (event) => {
    const target =
      event.target instanceof HTMLInputElement || event.target instanceof HTMLSelectElement
        ? event.target
        : null;
    if (!target) {
      return;
    }
    const typeKey = state.activeType;
    if (target.dataset[`${typeKey}Register`] !== undefined) {
      setActiveRegister(target.value);
      renderModelWorkbenchSurface();
      return;
    }
    if (target.dataset[`${typeKey}Search`] !== undefined) {
      workbenchState(typeKey).search = target.value;
      renderModelWorkbenchSurface();
      return;
    }
    if (target.dataset[`${typeKey}MissingOnly`] !== undefined) {
      workbenchState(typeKey).missingOnly = target.checked;
      renderModelWorkbenchSurface();
      return;
    }
    if (target.dataset[`${typeKey}Field`] && target.dataset[`${typeKey}Row`]) {
      updateField(
        typeKey,
        target.dataset[`${typeKey}Row`],
        target.dataset[`${typeKey}Field`],
        target.type === "checkbox" ? target.checked : target.value,
        target.type,
      );
    }
  });
  host.addEventListener("input", (event) => {
    const target = event.target instanceof HTMLInputElement ? event.target : null;
    const typeKey = state.activeType;
    if (target?.dataset?.[`${typeKey}Search`] !== undefined) {
      workbenchState(typeKey).search = target.value;
      scheduleSearchRender();
    }
  });
  document.addEventListener("click", (event) => {
    const target = event.target instanceof Element ? event.target : null;
    if (openSliceMenu && !target?.closest(".workbench-slice-select-wrap")) {
      openSliceMenu = "";
      renderModelWorkbenchSurface();
    }
  });
}

export function initModelWorkbenchSurface({
  renderDiagram,
  renderPalette,
  openAttributePanel,
  openConnectionPanel,
} = {}) {
  renderDiagramCallback = renderDiagram || renderDiagramCallback;
  renderPaletteCallback = renderPalette || renderPaletteCallback;
  openAttributePanelCallback = openAttributePanel || openAttributePanelCallback;
  openConnectionPanelCallback = openConnectionPanel || openConnectionPanelCallback;
  ensureSurface();
  bindSurfaceEvents();
}
