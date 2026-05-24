import {state} from './state.js';
import {el} from './dom.js';
import {escapeHtml} from './utils.js';
import {
  activeView,
  saveCurrentTabGraphState,
  syncActiveViewFromVisibleGraph
} from './graph-store.js';
import {materializeActiveView} from './view-materializer.js';
import {
  modelingElementDefinition,
  modelingLevelConfig,
  modelingTypeMatches,
  modelingViewDefinition
} from './modeling-config-data.js';
import {scheduleAutoSave} from './autosave.js';
import {publishDiagramUpdate} from './collaboration.js';
import {setStatus} from './status.js';

let surface = null;
let bound = false;
let renderDiagramCallback = null;
let renderPaletteCallback = null;
let openAttributePanelCallback = null;
let openConnectionPanelCallback = null;

const DEFAULT_REPRESENTATION_BY_PROFILE = {
  governance: "matrix",
  topology: "diagram",
  api: "register",
  compute: "diagram",
  eventing: "diagram",
  workflow: "diagram",
  data: "register",
  security: "matrix",
  networking: "diagram",
  observability: "register",
  configuration: "register",
  readiness: "board"
};

const PSM_EDGE_MODES = {
  both: {label: "Both"},
  flows: {label: "Flows"},
  references: {label: "Refs"}
};

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function ensureSurface() {
  if (surface) {
    return surface;
  }
  surface = document.createElement("div");
  surface.id = "psmWorkbenchSurface";
  surface.className = "cim-workbench-surface hidden";
  el.canvasGrid?.appendChild(surface);
  return surface;
}

function activeDefinition() {
  try {
    return modelingViewDefinition("psm", activeView());
  } catch {
    return null;
  }
}

function activeProfile() {
  const definition = activeDefinition();
  if (definition?.viewpoint) {
    return String(definition.viewpoint);
  }
  const text = [activeView()?.id, activeView()?.name,
    activeView()?.kind].filter(
      Boolean).join(" ").toLowerCase();
  for (const key of Object.keys(DEFAULT_REPRESENTATION_BY_PROFILE)) {
    if (text.includes(key)) {
      return key;
    }
  }
  return "topology";
}

function activeRepresentation(profile) {
  const viewId = activeView()?.id || "psm";
  return state.psmWorkbench.representationByViewId[viewId]
      || DEFAULT_REPRESENTATION_BY_PROFILE[profile] || "diagram";
}

function setActiveRepresentation(mode) {
  const viewId = activeView()?.id || "psm";
  state.psmWorkbench.representationByViewId[viewId] = mode;
  renderPsmWorkbenchSurface();
  renderDiagramCallback?.();
  renderPaletteCallback?.();
}

function titleCase(value) {
  return String(value || "").replaceAll(/[-_]+/g, " ").replace(
      /\b\w/g, (letter) => letter.toUpperCase());
}

function lensKeyFor(definition, index) {
  return String(definition?.viewpoint || definition?.id || `view-${index}`)
  .trim().toLowerCase().replaceAll(/[^a-z0-9]+/g, "-").replaceAll(
      /^-|-$/g, "") || `view-${index}`;
}

function psmLensEntries() {
  let definitions = [];
  try {
    definitions = safeArray(modelingLevelConfig("psm").viewDefinitions);
  } catch {
    definitions = [];
  }
  const byKey = new Map([["all", {key: "all", label: "All", types: []}]]);
  definitions.forEach((definition, index) => {
    const types = safeArray(definition?.elementTypes).map(String).filter(
        Boolean);
    if (!types.length) {
      return;
    }
    const key = lensKeyFor(definition, index);
    const existing = byKey.get(key);
    const label = titleCase(definition?.viewpoint || definition?.displayName
        || definition?.name || key);
    byKey.set(key, {
      key,
      label: existing?.label || label,
      types: [...new Set([...(existing?.types || []), ...types])]
    });
  });
  return [...byKey.values()];
}

function psmLens(lensKey) {
  return psmLensEntries().find((entry) => entry.key === lensKey) || null;
}

function psmFlowRelationshipKinds(allKinds) {
  let labels = {};
  try {
    labels = modelingLevelConfig("psm").relationshipKindLabels || {};
  } catch {
    labels = {};
  }
  return safeArray(allKinds).filter((kind) => {
    const text = `${kind} ${labels[kind] || ""}`.toLowerCase();
    return /flow|invoke|route|target|transition|subscription|event|message/.test(
        text);
  });
}

function applyPsmLens(lensKey) {
  const lens = psmLens(lensKey);
  if (!lens) {
    return;
  }
  state.psmWorkbench.activeLens = lensKey;
  const view = activeView();
  if (!view) {
    return;
  }
  syncActiveViewFromVisibleGraph();
  view.filters ??= {};
  if (!Array.isArray(view.__psmBaseElementTypes)) {
    view.__psmBaseElementTypes = safeArray(view.filters.elementTypes).map(
        String);
  }
  const baseTypes = safeArray(view.__psmBaseElementTypes);
  const lensTypes = safeArray(lens.types);
  view.filters.elementTypes = lensTypes.length
      ? (baseTypes.length
          ? lensTypes.filter((type) => baseTypes.some((baseType) =>
              modelingTypeMatches("psm", baseType, type)
              || modelingTypeMatches("psm", type, baseType)))
          : lensTypes)
      : baseTypes;
  if (lensTypes.length && !view.filters.elementTypes.length) {
    view.filters.elementTypes = lensTypes;
  }
  materializeActiveView();
  saveCurrentTabGraphState("psm");
  renderPsmWorkbenchSurface();
  renderDiagramCallback?.();
  renderPaletteCallback?.();
  setStatus(`${lens.label} lens applied`);
}

function applyPsmEdgeMode(mode) {
  if (!PSM_EDGE_MODES[mode]) {
    return;
  }
  state.psmWorkbench.edgeMode = mode;
  const view = activeView();
  if (!view) {
    return;
  }
  syncActiveViewFromVisibleGraph();
  const allKinds = [...state.graph.relationshipsByKind.keys()].sort();
  const flowKinds = psmFlowRelationshipKinds(allKinds);
  view.filters ??= {};
  if (mode === "both") {
    view.filters.relationshipKinds = [];
  } else if (mode === "flows") {
    view.filters.relationshipKinds = flowKinds.length ? flowKinds
        : ["__PSM_NO_FLOW_EDGES__"];
  } else {
    const flowKindSet = new Set(flowKinds);
    const referenceKinds = allKinds.filter((kind) =>
        !flowKindSet.has(kind));
    view.filters.relationshipKinds = referenceKinds.length ? referenceKinds
        : ["__PSM_NO_REFERENCE_EDGES__"];
  }
  materializeActiveView();
  saveCurrentTabGraphState("psm");
  renderPsmWorkbenchSurface();
  renderDiagramCallback?.();
  renderPaletteCallback?.();
  setStatus(`${PSM_EDGE_MODES[mode].label} edge mode applied`);
}

function elements() {
  return [...(state.graph?.elementsById || new Map()).values()];
}

function relationships() {
  return [...(state.graph?.relationshipsById || new Map()).values()];
}

function elementLabel(row) {
  return String(row?.name || row?.label || row?.logicalId || row?.id || "");
}

function valueText(value) {
  if (Array.isArray(value)) {
    return value.map(valueText).filter(Boolean).join(", ");
  }
  if (value && typeof value === "object") {
    return valueText(value.$ref || value.id || value.name || value.label);
  }
  return String(value ?? "");
}

function refIds(value) {
  if (Array.isArray(value)) {
    return value.flatMap(refIds);
  }
  if (value && typeof value === "object") {
    return [value.$ref || value.id].filter(Boolean);
  }
  return value ? [String(value)] : [];
}

function typeMatchesAny(actual, expectedTypes) {
  const actualType = String(actual || "");
  return safeArray(expectedTypes).some((expected) =>
      modelingTypeMatches("psm", expected, actualType));
}

function fieldDefinition(type, fieldName) {
  try {
    const definition = modelingElementDefinition("psm", type);
    return [...safeArray(definition?.attributes),
      ...safeArray(definition?.references)].find((field) =>
        field.name === fieldName) || null;
  } catch {
    return null;
  }
}

function viewTypes() {
  const definition = activeDefinition();
  const types = safeArray(definition?.elementTypes);
  return types.length ? types : safeArray(activeView()?.filters?.elementTypes);
}

function matchesSearch(row) {
  const query = String(state.psmWorkbench.search || "").trim().toLowerCase();
  if (!query) {
    return true;
  }
  return [row.id, row.eClass, row.name, row.label, row.logicalId,
    ...Object.values(row).filter((value) => typeof value === "string")]
  .join(" ").toLowerCase().includes(query);
}

function rowInSlice(row) {
  const kind = String(state.psmWorkbench.sliceKind || "");
  const value = String(state.psmWorkbench.sliceValue || "");
  if (!kind) {
    return true;
  }
  if (kind === "lifecycle") {
    return !value || String(row.lifecycleStatus || "") === value;
  }
  if (kind === "security") {
    return Boolean(row.encryptionRequired || row.encrypted
        || row.authRequired || row.publicAccessMode === "BLOCK"
        || row.principal || row.role || row.kmsKey
        || /iam|kms|secret|ssm|cognito|authorizer|security/i.test(String(
            row.eClass || "")));
  }
  if (kind === "production") {
    return Boolean(row.productionCritical || row.productionMode
        || row.retainInProduction || row.deletionProtectionEnabled
        || row.backupEnabled || row.pointInTimeRecoveryEnabled);
  }
  return true;
}

function rowInActiveLens(row) {
  const lensKey = state.psmWorkbench.activeLens || "all";
  const lens = psmLens(lensKey);
  if (!lens || !lens.types.length) {
    return true;
  }
  if (typeMatchesAny(row?.eClass || row?.type, lens.types)) {
    return true;
  }
  return Object.values(row || {}).some((value) => refIds(value).some((id) => {
    const target = state.graph?.elementsById?.get(id);
    return typeMatchesAny(target?.eClass, lens.types);
  }));
}

function rowsForActiveView() {
  const allowed = new Set(viewTypes());
  const rows = elements().filter((row) => {
    if (!allowed.size) {
      return true;
    }
    const type = row.eClass || row.type;
    return [...allowed].some((expected) =>
        modelingTypeMatches("psm", expected, type));
  });
  const filtered = rows.filter(matchesSearch).filter(rowInSlice)
  .filter(rowInActiveLens);
  const missingFiltered = state.psmWorkbench.missingOnly
      ? filtered.filter((row) => missingRequiredFields(row).length) : filtered;
  const sortKey = state.psmWorkbench.sortKey || "name";
  return missingFiltered.sort((a, b) => valueText(a?.[sortKey]
      || elementLabel(a)).localeCompare(valueText(b?.[sortKey]
      || elementLabel(b))));
}

function columnsForRows(rows) {
  const definition = activeDefinition();
  const preferred = ["name", "logicalId", "physicalName"];
  const configured = rows.flatMap((row) => {
    try {
      return safeArray(modelingElementDefinition("psm", row.eClass)
          ?.visibleFields);
    } catch {
      return [];
    }
  });
  const extras = ["stack", "role", "kmsKey", "runtime", "timeoutSeconds",
    "memorySizeMb", "billingMode", "publicAccessMode", "retentionInDays",
    "productionCritical"];
  return [...new Set([...preferred, ...configured, ...extras])].filter(
      (column) => rows.some((row) => row[column] !== undefined)
          || safeArray(definition?.elementTypes).length).slice(0, 12);
}

function controlForField(row, field) {
  const definition = fieldDefinition(row.eClass, field) || {};
  const value = row[field];
  if (definition.readonly || field === "id") {
    return `<span class="cim-cell-readonly">${escapeHtml(
        valueText(value))}</span>`;
  }
  if (definition.kind === "reference" || Array.isArray(value)
      || (value && typeof value === "object")) {
    return `<span class="cim-ref-cell">${escapeHtml(valueText(value))}</span>`;
  }
  if (definition.fieldType === "boolean" || typeof value === "boolean") {
    return `<input class="cim-table-check" data-psm-field="${escapeHtml(field)}"
        data-psm-row="${escapeHtml(row.id)}" type="checkbox" ${
        value ? "checked" : ""}>`;
  }
  if (definition.fieldType === "select" && Array.isArray(definition.options)) {
    return `<select class="cim-table-input" data-psm-field="${escapeHtml(field)}"
        data-psm-row="${escapeHtml(row.id)}"><option value=""></option>${
        definition.options.map((option) => `<option value="${escapeHtml(option)}"
          ${String(value || "") === String(option) ? "selected" : ""}>${
            escapeHtml(option)}</option>`).join("")}</select>`;
  }
  const type = definition.fieldType === "number" || typeof value === "number"
      ? "number" : "text";
  return `<input class="cim-table-input" data-psm-field="${escapeHtml(field)}"
      data-psm-row="${escapeHtml(row.id)}" type="${type}" value="${escapeHtml(
      valueText(value))}">`;
}

function missingRequiredFields(row) {
  let definition = null;
  try {
    definition = modelingElementDefinition("psm", row.eClass);
  } catch {
    return [];
  }
  return [...safeArray(definition?.attributes),
    ...safeArray(definition?.references)].filter((field) =>
      field.required && !field.containment && !valueText(row[field]).trim())
  .map((field) => field.name);
}

function rowBadge(row) {
  const missing = missingRequiredFields(row);
  return `<span class="cim-type-badge">${escapeHtml(
      row.eClass || "PSM")}</span>${
      missing.length ? `<span class="cim-missing-badge" title="${escapeHtml(
          missing.join(", "))}">${missing.length}</span>` : ""}`;
}

function renderRegister() {
  const rows = rowsForActiveView();
  const columns = columnsForRows(rows);
  return `<div class="cim-toolbar">
    <button class="cim-action cim-action-primary" data-psm-open-create type="button">Add From Palette</button>
  </div>
  <div class="cim-table-wrap"><table class="cim-table">
    <thead><tr><th>Type</th>${columns.map((column) =>
      `<th><button data-psm-sort="${escapeHtml(column)}">${escapeHtml(
          column)}</button></th>`).join("")}<th></th></tr></thead>
    <tbody>${rows.map((row) => `<tr>
      <td>${rowBadge(row)}</td>
      ${columns.map(
      (column) => `<td>${controlForField(row, column)}</td>`).join("")}
      <td><button class="cim-icon-action" data-psm-open="${escapeHtml(row.id)}"
          type="button">Open</button></td>
    </tr>`).join("") || `<tr><td colspan="${columns.length
  + 2}">No PSM elements in this view.</td></tr>`}
    </tbody>
  </table></div>`;
}

function renderMatrix() {
  const rows = rowsForActiveView();
  const rels = relationships();
  const visibleIds = new Set(rows.map((row) => row.id));
  return `<div class="cim-matrix-wrap"><table class="cim-matrix">
    <thead><tr><th>Source</th><th>Kind</th><th>Target</th><th>Feature</th><th></th></tr></thead>
    <tbody>${rels.filter((rel) => visibleIds.has(rel.sourceElementId)
      || visibleIds.has(rel.targetElementId)).map((rel) => {
    const source = state.graph.elementsById.get(rel.sourceElementId);
    const target = state.graph.elementsById.get(rel.targetElementId);
    return `<tr><td>${escapeHtml(elementLabel(source) || rel.sourceElementId)}</td>
      <td>${escapeHtml(rel.kind || "")}</td>
      <td>${escapeHtml(elementLabel(target) || rel.targetElementId)}</td>
      <td>${escapeHtml(rel.semanticFeature || rel.feature || "")}</td>
      <td><button class="cim-icon-action" data-psm-open-relationship="${escapeHtml(
        rel.id)}" type="button">Open</button></td></tr>`;
  }).join("") || `<tr><td colspan="5">No connectors in this view.</td></tr>`}</tbody>
  </table></div>`;
}

function renderBoard() {
  const rows = rowsForActiveView();
  const groups = ["BLOCKER", "ERROR", "WARNING", "INFO", "DRAFT", "READY"];
  return `<div class="cim-board">${groups.map((group) => {
    const items = rows.filter(
        (row) => String(row.severity || row.readinessStatus
                || row.lifecycleStatus || "DRAFT").toUpperCase().includes(group)
            || (group === "READY" && /ready|complete/i.test(String(
                row.lifecycleStatus || row.readinessStatus || ""))));
    return `<section class="cim-board-column"><div class="cim-section-title">${escapeHtml(
        group)}</div>${items.map((row) => `<button class="cim-board-card"
          data-psm-open="${escapeHtml(
        row.id)}" type="button"><strong>${escapeHtml(
        elementLabel(row))}</strong><span>${escapeHtml(
        row.eClass || "")}</span></button>`)
    .join("") || `<div class="cim-empty-card">No items</div>`}</section>`;
  }).join("")}</div>`;
}

function renderDetail() {
  const rows = rowsForActiveView().slice(0, 24);
  return rows.map((row) => {
        const fields = columnsForRows([row]);
        return `<section class="cim-detail-projection">
      <div class="cim-detail-title"><strong>${escapeHtml(elementLabel(row))}</strong>
        <span>${escapeHtml(row.eClass || "PSM")}</span></div>
      <div class="cim-detail-grid">${fields.map((field) =>
            `<div><span>${escapeHtml(field)}</span><strong>${escapeHtml(
                valueText(row[field]))}</strong></div>`).join("")}
        <div><span>missing required</span><strong>${escapeHtml(
            missingRequiredFields(row).join(", ") || "none")}</strong></div>
        <div><span>references</span><strong>${Object.values(row).flatMap(
            refIds).length}</strong></div>
      </div>
      <div class="cim-dashboard-actions"><button class="cim-action"
        data-psm-open="${escapeHtml(row.id)}" type="button">Open Full Inspector</button></div>
    </section>`;
      }).join("")
      || `<div class="cim-empty-card">No PSM elements in this view.</div>`;
}

function countRowsMatchingTypes(types) {
  return elements().filter((row) => typeMatchesAny(row.eClass, types)).length;
}

function renderDashboard() {
  const lensCards = psmLensEntries().filter((lens) => lens.key !== "all").map(
      (lens) => [lens.label, countRowsMatchingTypes(lens.types)]);
  const cards = [
    ["Elements", elements().length],
    ["Connectors", relationships().length],
    ["Missing", elements().filter((item) =>
        missingRequiredFields(item).length).length],
    ...lensCards
  ];
  return `<div class="cim-dashboard-grid">
    ${cards.map(([label, value]) => `<div class="cim-metric">
      <strong>${escapeHtml(value)}</strong><span>${escapeHtml(label)}</span>
    </div>`).join("")}
  </div>`;
}

function renderControls(profile, representation) {
  const modes = [
    ["diagram", "Diagram"],
    ["dashboard", "Dashboard"],
    ["register", "Register"],
    ["matrix", "Matrix"],
    ["board", "Board"],
    ["detail", "Detail"]
  ];
  const definition = activeDefinition();
  return `<div class="cim-surface-header">
    <div class="cim-surface-title">
      <strong>${escapeHtml(activeView()?.name || definition?.displayName
      || "PSM View")}</strong>
      <span>${escapeHtml(profile)}</span>
    </div>
    <div class="cim-mode-tabs">
      ${modes.map(([mode, label]) =>
      `<button class="${representation === mode ? "is-active" : ""}"
          data-psm-mode="${mode}" type="button">${escapeHtml(label)}</button>`)
  .join("")}
    </div>
    <div class="pim-lens-toolbar" aria-label="PSM lens">
      <span>Lens</span>
      <div class="pim-lens-buttons">
        ${psmLensEntries().map((lens) =>
      `<button class="${(state.psmWorkbench.activeLens || "all") === lens.key
          ? "is-active" : ""}"
              data-psm-lens="${lens.key}" type="button">${escapeHtml(
          lens.label)}</button>`).join("")}
      </div>
    </div>
    <div class="pim-edge-toolbar" aria-label="PSM edge visibility">
      <span>Edges</span>
      <div class="pim-edge-buttons">
        ${Object.entries(PSM_EDGE_MODES).map(([key, mode]) =>
      `<button class="${(state.psmWorkbench.edgeMode || "both") === key
          ? "is-active" : ""}"
              data-psm-edge-mode="${key}" type="button">${escapeHtml(
          mode.label)}</button>`).join("")}
      </div>
    </div>
    <div class="cim-filter-row">
      <input data-psm-search placeholder="Search PSM" value="${escapeHtml(
      state.psmWorkbench.search || "")}">
      <select data-psm-slice-kind>
        <option value="">All slices</option>
        ${["security", "production", "lifecycle"].map((item) =>
      `<option value="${item}" ${
          state.psmWorkbench.sliceKind === item ? "selected" : ""}>${
          escapeHtml(item)}</option>`).join("")}
      </select>
      <select data-psm-slice-value><option value="">Any</option>${sliceOptions()}</select>
      <label class="cim-check-label"><input data-psm-missing-only type="checkbox" ${
      state.psmWorkbench.missingOnly ? "checked" : ""}> Missing required</label>
    </div>
    <div class="cim-guidance">${escapeHtml(representation === "diagram"
      ? "Use the palette to add concrete AWS resources, choose a connection tool, then click a source and legal target on the canvas."
      : "Edit this projection directly. Open rows for full details, use matrices for references, and switch back to Diagram for spatial modeling.")}</div>
    <div class="cim-quick-help">${(representation === "diagram"
      ? [
        "Add concrete AWS resources from the palette.",
        "Use Connection Tools or drag a node handle; legal targets turn green.",
        "Open Register/Matrix when the view gets dense."
      ]
      : [
        "The palette is hidden so this editor has room.",
        "Use Register for bulk AWS fields, Matrix for references, Board for readiness.",
        "Use Open on any row to edit the full detail drawer."
      ]).map((item) => `<span>${escapeHtml(item)}</span>`).join("")}</div>
  </div>`;
}

function sliceOptions() {
  if (state.psmWorkbench.sliceKind === "lifecycle") {
    const statuses = [...new Set(elements().map((item) =>
        String(item.lifecycleStatus || "")).filter(Boolean))];
    return statuses.map((status) => `<option value="${escapeHtml(status)}" ${
        state.psmWorkbench.sliceValue === status ? "selected" : ""}>${
        escapeHtml(status)}</option>`).join("");
  }
  return "";
}

function renderBody(representation) {
  if (representation === "dashboard") {
    return renderDashboard();
  }
  if (representation === "register") {
    return renderRegister();
  }
  if (representation === "matrix") {
    return renderMatrix();
  }
  if (representation === "board") {
    return renderBoard();
  }
  if (representation === "detail") {
    return renderDetail();
  }
  return "";
}

export function renderPsmWorkbenchSurface() {
  const host = ensureSurface();
  if (state.activeType !== "psm" || state.modelingToolsMinimized) {
    host.className = "cim-workbench-surface hidden";
    el.canvasGrid?.classList.remove("psm-surface-active", "psm-surface-dock");
    restorePaletteAfterWorkbench();
    return;
  }
  try {
    modelingLevelConfig("psm");
  } catch {
    host.className = "cim-workbench-surface hidden";
    return;
  }
  const profile = activeProfile();
  const representation = activeRepresentation(profile);
  const controls = renderControls(profile, representation);
  if (representation === "diagram") {
    host.className = "cim-workbench-surface cim-workbench-dock";
    host.innerHTML = controls;
    el.canvasGrid?.classList.remove("psm-surface-active");
    el.canvasGrid?.classList.add("psm-surface-dock");
    restorePaletteAfterWorkbench();
    return;
  }
  host.className = "cim-workbench-surface";
  host.innerHTML = `${controls}<div class="cim-surface-body">${renderBody(
      representation)}</div>`;
  el.canvasGrid?.classList.add("psm-surface-active");
  el.canvasGrid?.classList.remove("psm-surface-dock");
  hidePaletteForWorkbench();
}

function hidePaletteForWorkbench() {
  if (!el.workspace || el.workspace.classList.contains("palette-hidden")) {
    return;
  }
  state.psmWorkbench.hidPalette = true;
  el.workspace.classList.add("palette-hidden");
  el.paletteRailToggleBtn?.classList.remove("active");
}

function restorePaletteAfterWorkbench() {
  if (!state.psmWorkbench.hidPalette || !el.workspace) {
    return;
  }
  state.psmWorkbench.hidPalette = false;
  el.workspace.classList.remove("palette-hidden");
  el.paletteRailToggleBtn?.classList.add("active");
}

function commitModelChange(message) {
  syncActiveViewFromVisibleGraph();
  saveCurrentTabGraphState("psm");
  renderPsmWorkbenchSurface();
  renderDiagramCallback?.();
  renderPaletteCallback?.();
  scheduleAutoSave({delayMs: 250});
  publishDiagramUpdate({immediate: true});
  if (message) {
    setStatus(message);
  }
}

function updateField(rowId, field, rawValue, inputType = "text") {
  const row = state.graph.elementsById.get(rowId);
  if (!row) {
    return;
  }
  const definition = fieldDefinition(row.eClass, field);
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
    if (field === "name" || field === "logicalId") {
      visible.label = String(value || visible.label);
      visible.meta.name = visible.label;
      visible.meta.label = visible.label;
    }
  }
  commitModelChange(`Updated ${field}`);
}

function bindSurfaceEvents() {
  const host = ensureSurface();
  if (bound) {
    return;
  }
  bound = true;
  ["mousedown", "mouseup", "click", "dblclick", "touchstart", "touchmove",
    "wheel", "dragover", "drop"].forEach((type) => {
    host.addEventListener(type, (event) => event.stopPropagation(),
        {passive: type !== "wheel"});
  });
  host.addEventListener("click", (event) => {
    const target = event.target instanceof Element ? event.target : null;
    const mode = target?.closest("[data-psm-mode]")?.dataset?.psmMode;
    if (mode) {
      setActiveRepresentation(mode);
      return;
    }
    const lens = target?.closest("[data-psm-lens]")?.dataset?.psmLens;
    if (lens) {
      applyPsmLens(lens);
      return;
    }
    const edgeMode = target?.closest("[data-psm-edge-mode]")?.dataset
        ?.psmEdgeMode;
    if (edgeMode) {
      applyPsmEdgeMode(edgeMode);
      return;
    }
    const openId = target?.closest("[data-psm-open]")?.dataset?.psmOpen;
    if (openId) {
      openAttributePanelCallback?.(openId);
      return;
    }
    const openRelationship = target?.closest(
        "[data-psm-open-relationship]")?.dataset?.psmOpenRelationship;
    if (openRelationship) {
      openConnectionPanelCallback?.(openRelationship);
      return;
    }
    if (target?.closest("[data-psm-open-create]")) {
      setStatus("Use the PSM palette to add a concrete resource or stack.");
      return;
    }
    const sortKey = target?.closest("[data-psm-sort]")?.dataset?.psmSort;
    if (sortKey) {
      state.psmWorkbench.sortKey = sortKey;
      renderPsmWorkbenchSurface();
    }
  });
  host.addEventListener("change", (event) => {
    const target = event.target instanceof HTMLInputElement
    || event.target instanceof HTMLSelectElement ? event.target : null;
    if (!target) {
      return;
    }
    if (target.dataset.psmSearch !== undefined) {
      state.psmWorkbench.search = target.value;
      renderPsmWorkbenchSurface();
      return;
    }
    if (target.dataset.psmSliceKind !== undefined) {
      state.psmWorkbench.sliceKind = target.value;
      state.psmWorkbench.sliceValue = "";
      renderPsmWorkbenchSurface();
      return;
    }
    if (target.dataset.psmSliceValue !== undefined) {
      state.psmWorkbench.sliceValue = target.value;
      renderPsmWorkbenchSurface();
      return;
    }
    if (target.dataset.psmMissingOnly !== undefined) {
      state.psmWorkbench.missingOnly = target.checked;
      renderPsmWorkbenchSurface();
      return;
    }
    if (target.dataset.psmField && target.dataset.psmRow) {
      updateField(target.dataset.psmRow, target.dataset.psmField,
          target.type === "checkbox" ? target.checked : target.value,
          target.type);
    }
  });
  host.addEventListener("input", (event) => {
    const target = event.target instanceof HTMLInputElement ? event.target
        : null;
    if (target?.dataset?.psmSearch !== undefined) {
      state.psmWorkbench.search = target.value;
      renderPsmWorkbenchSurface();
    }
  });
}

export function initPsmWorkbenchSurface({
  renderDiagram,
  renderPalette,
  openAttributePanel,
  openConnectionPanel
} = {}) {
  renderDiagramCallback = renderDiagram || renderDiagramCallback;
  renderPaletteCallback = renderPalette || renderPaletteCallback;
  openAttributePanelCallback = openAttributePanel || openAttributePanelCallback;
  openConnectionPanelCallback = openConnectionPanel
      || openConnectionPanelCallback;
  ensureSurface();
  bindSurfaceEvents();
}
