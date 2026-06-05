import {state} from './state.js';
import {el} from './dom.js';
import {escapeHtml} from './utils.js';
import {getDefaultNode} from './diagram.js';
import {
  activeView,
  addNodeToGraphAndActiveView,
  saveCurrentTabGraphState,
  syncActiveViewFromVisibleGraph
} from './graph-store.js';
import {
  modelingElementDefinition,
  modelingLevelConfig,
  modelingTypeMatches,
  modelingViewDefinition
} from './modeling-config-data.js';
import {markModelDirty} from './model-save-ui.js';
import {setStatus} from './status.js';
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
  STANDARD_EDGE_MODES
} from './workbench-common.js';

let surface = null;
let bound = false;
let renderDiagramCallback = null;
let renderPaletteCallback = null;
let openAttributePanelCallback = null;
let openConnectionPanelCallback = null;
let searchRenderTimer = 0;
let psmSliceMenuOpen = "";

const PSM_SLICE_KINDS = [
  ["", "All slices"],
  ["security", "Security"],
  ["production", "Production"],
  ["lifecycle", "Lifecycle"]
];

function scheduleSearchRender() {
  if (searchRenderTimer) {
    window.clearTimeout(searchRenderTimer);
  }
  searchRenderTimer = window.setTimeout(() => {
    searchRenderTimer = 0;
    renderPsmWorkbenchSurface();
  }, 90);
}

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

const DEFAULT_REGISTER_BY_PROFILE = {
  governance: "all",
  topology: "SamStack",
  api: "ApiGatewayApi",
  compute: "AwsLambdaFunction",
  eventing: "EventBridgeBus",
  workflow: "StepFunctionStateMachine",
  data: "DynamoDbTable",
  security: "IamRole",
  networking: "VpcConfig",
  observability: "CloudWatchLogGroup",
  configuration: "EnvironmentConfig",
  readiness: "ProductionReadinessAssessment"
};

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function ensureSurface() {
  surface = ensureWorkbenchSurface(surface, "psmWorkbenchSurface");
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
  return activeWorkbenchRepresentation(state.psmWorkbench, viewId, profile,
      DEFAULT_REPRESENTATION_BY_PROFILE);
}

function activeRegister(profile) {
  const viewId = activeView()?.id || "psm";
  return state.psmWorkbench.registerByViewId[viewId]
      || DEFAULT_REGISTER_BY_PROFILE[profile] || "all";
}

function resolveActiveRegister(profile = activeProfile()) {
  const requested = activeRegister(profile);
  if (requested === "all") {
    return requested;
  }
  const available = new Set([
    ...rowsForActiveView().map((row) => String(row.eClass || "")),
    ...viewTypes().map(String)
  ]);
  return available.has(requested) ? requested : "all";
}

function setActiveRegister(typeName) {
  const viewId = activeView()?.id || "psm";
  state.psmWorkbench.registerByViewId[viewId] = typeName;
  renderPsmWorkbenchSurface();
}

function setActiveRepresentation(mode) {
  const viewId = activeView()?.id || "psm";
  setWorkbenchRepresentation(state.psmWorkbench, viewId, mode,
      renderPsmWorkbenchSurface, renderDiagramCallback, renderPaletteCallback);
}

function applyPsmEdgeMode(mode) {
  applyWorkbenchEdgeMode({
    typeKey: "psm",
    workbenchState: state.psmWorkbench,
    mode,
    graph: state.graph,
    renderWorkbench: renderPsmWorkbenchSurface,
    renderDiagram: renderDiagramCallback,
    renderPalette: renderPaletteCallback
  });
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
  const filtered = rows.filter(matchesSearch).filter(rowInSlice);
  const missingFiltered = state.psmWorkbench.missingOnly
      ? filtered.filter((row) => missingRequiredFields(row).length) : filtered;
  const sortKey = state.psmWorkbench.sortKey || "name";
  return missingFiltered.sort((a, b) => valueText(a?.[sortKey]
      || elementLabel(a)).localeCompare(valueText(b?.[sortKey]
      || elementLabel(b))));
}

function registerRows(profile = activeProfile()) {
  const register = resolveActiveRegister(profile);
  const rows = rowsForActiveView();
  if (register === "all") {
    return rows;
  }
  return rows.filter((row) => modelingTypeMatches("psm", register, row.eClass));
}

function registerTypeOptions(profile = activeProfile()) {
  const fromRows = [...new Set(rowsForActiveView().map((row) =>
      String(row.eClass || "")).filter(Boolean))];
  const fromView = [...new Set(viewTypes().map(String).filter(Boolean))];
  const types = (fromRows.length ? fromRows : fromView).sort((left, right) =>
      left.localeCompare(right));
  const active = resolveActiveRegister(profile);
  return [
    `<option value="all"${active === "all" ? " selected"
        : ""}>All Visible Types</option>`,
    ...types.map((type) => `<option value="${escapeHtml(type)}"${
        active === type ? " selected" : ""}>${escapeHtml(type)}</option>`)
  ].join("");
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

function exportCsv(profile = activeProfile()) {
  const rows = registerRows(profile);
  const columns = ["id", "eClass", ...columnsForRows(rows)];
  downloadWorkbenchCsv(
      `psm-${resolveActiveRegister(profile).toLowerCase()}.csv`,
      rows, columns, valueText);
}

function activeAddType(profile = activeProfile()) {
  const register = resolveActiveRegister(profile);
  if (register !== "all") {
    return register;
  }
  return viewTypes()[0] || rowsForActiveView()[0]?.eClass || "SamStack";
}

function renderRegister(profile) {
  const rows = registerRows(profile);
  const columns = columnsForRows(rows);
  const addType = activeAddType(profile);
  const toolbarHtml = renderWorkbenchToolbar([
    `<select class="cim-select" data-psm-register>${registerTypeOptions(
        profile)}</select>`,
    `<button class="cim-action cim-action-primary" data-psm-add-type="${escapeHtml(
        addType)}" type="button">Add ${escapeHtml(addType)}</button>`,
    `<button class="cim-action" data-psm-export type="button">Export CSV</button>`,
    `<label class="cim-action cim-file-action">Import CSV
      <input class="hidden" data-psm-import type="file" accept=".csv,text/csv">
    </label>`
  ]);
  return renderWorkbenchRegister({
    typeKey: "psm",
    toolbarHtml,
    rows,
    columns,
    getLabel: elementLabel,
    getBadgeHtml: rowBadge,
    renderCell: controlForField,
    emptyText: "No PSM elements in this view."
  });
}

function renderMatrix() {
  const rows = rowsForActiveView();
  const rels = relationships();
  const visibleIds = new Set(rows.map((row) => row.id));
  const scopedRels = rels.filter((rel) => visibleIds.has(rel.sourceElementId)
      || visibleIds.has(rel.targetElementId));
  const sources = rows.filter((row) => scopedRels.some((rel) =>
      rel.sourceElementId === row.id));
  const targets = rows.filter((row) => scopedRels.some((rel) =>
      rel.targetElementId === row.id));
  return renderWorkbenchMatrixSection({
    title: "Resource Connectors",
    rows: sources,
    columns: targets,
    typeKey: "psm",
    getRowLabel: elementLabel,
    getColumnLabel: elementLabel,
    getColumnMeta: (column) => column.eClass || "",
    getCellHtml: (row, column) => {
      const rel = scopedRels.find((candidate) =>
          candidate.sourceElementId === row.id
          && candidate.targetElementId === column.id);
      if (!rel) {
        return `<td></td>`;
      }
      const label = rel.kind || rel.semanticFeature || rel.feature || "x";
      return `<td class="is-linked"><button class="cim-icon-action"
          data-psm-open-relationship="${escapeHtml(rel.id)}"
          type="button">${escapeHtml(label)}</button></td>`;
    },
    emptyText: "No connectors in this view."
  });
}

function renderBoard() {
  const rows = rowsForActiveView();
  const groups = [
    ["Blocking", (row) => Boolean(row.productionBlocking || row.blocking
        || String(row.severity || "").toUpperCase() === "BLOCKER")],
    ["Open", (row) => !/ready|complete|accepted|passed/i.test(String(
        row.lifecycleStatus || row.readinessStatus || row.status || ""))],
    ["Accepted / Passed", (row) => /ready|complete|accepted|passed/i.test(
        String(row.lifecycleStatus || row.readinessStatus || row.status || ""))]
  ];
  return renderWorkbenchBoard({
    lanes: groups.map(([group, predicate]) => {
      const items = rows.filter(predicate);
      return {
        title: group,
        count: items.length,
        cards: items.map((row) => renderWorkbenchBoardCard({
          typeKey: "psm",
          id: row.id,
          title: elementLabel(row),
          meta: row.eClass || "",
          body: row.severity || row.readinessStatus || row.lifecycleStatus || ""
        })),
        emptyText: "No items"
      };
    })
  });
}

function renderDetail() {
  const selected = state.selectedNodeId
      ? state.graph?.elementsById?.get(state.selectedNodeId) : null;
  const row = selected || registerRows(activeProfile())[0]
      || rowsForActiveView()[0];
  if (!row) {
    return renderWorkbenchDetail({
      typeKey: "psm",
      row: null,
      valueText,
      emptyText: "No PSM elements in this view."
    });
  }
  return renderWorkbenchDetail({
    typeKey: "psm",
    row,
    fields: columnsForRows([row]),
    title: elementLabel(row),
    typeLabel: row.eClass || "PSM",
    valueText,
    stats: [
      ["missing required", missingRequiredFields(row).join(", ") || "none"],
      ["references", Object.values(row).flatMap(refIds).length]
    ],
    emptyText: "No PSM elements in this view."
  });
}

function countRowsMatchingTypes(types) {
  return elements().filter((row) => typeMatchesAny(row.eClass, types)).length;
}

function renderDashboard() {
  const stacks = countRowsMatchingTypes(["SamStack"]);
  const stages = countRowsMatchingTypes(["AwsStage"]);
  const cards = [
    ["Elements", elements().length],
    ["Connectors", relationships().length],
    ["Stacks", stacks],
    ["Stages", stages],
    ["Missing", elements().filter((item) =>
        missingRequiredFields(item).length).length]
  ];
  const missing = [
    stacks ? "" : "stack",
    stages ? "" : "stage"
  ].filter(Boolean);
  const foundationHtml = `<section class="cim-root-form">
    <div class="cim-section-title">Model Foundation</div>
    ${missing.length ? `<div class="cim-alert">Missing required foundation:
      ${escapeHtml(missing.join(", "))}</div>` : `<div class="cim-ok">
      Foundation requirements are satisfied.</div>`}
    <label class="cim-form-field">
      <span>stack</span>
      <input readonly type="text" value="${escapeHtml(
      stacks ? "Configured" : "")}">
    </label>
    <label class="cim-form-field">
      <span>stage</span>
      <input readonly type="text" value="${escapeHtml(
      stages ? "Configured" : "")}">
    </label>
  </section>`;
  const actionsHtml = `<section class="cim-view-entry-list">
    <div class="cim-section-title">Actions</div>
    <button class="cim-view-entry" data-psm-create-root type="button">
      <span>${stacks && stages ? "Refresh PSM Foundation"
      : "Create PSM Foundation"}</span>
      <strong>foundation</strong>
    </button>
    <button class="cim-view-entry" data-psm-mode="diagram" type="button">
      <span>Open Diagram</span>
      <strong>diagram</strong>
    </button>
  </section>`;
  return renderWorkbenchDashboard({
    metrics: cards,
    primaryHtml: foundationHtml,
    secondaryHtml: actionsHtml
  });
}

function renderControls(profile, representation) {
  const definition = activeDefinition();
  return renderWorkbenchControls({
    typeKey: "psm",
    title: activeView()?.name || definition?.displayName || "PSM View",
    profile,
    representation,
    workbenchState: state.psmWorkbench,
    sliceControlsHtml: `${psmSliceSelectMarkup("kind")}${psmSliceSelectMarkup(
        "value")}`,
    edgeModes: STANDARD_EDGE_MODES,
    searchPlaceholder: "Search PSM"
  });
}

function sliceItems() {
  if (state.psmWorkbench.sliceKind === "lifecycle") {
    return [...new Set(elements().map((item) =>
        String(item.lifecycleStatus || "")).filter(Boolean))]
    .map((status) => ({id: status, name: status}));
  }
  return [];
}

function psmSliceKindLabel(value = state.psmWorkbench.sliceKind) {
  return PSM_SLICE_KINDS.find(([key]) => key === value)?.[1] || "All slices";
}

function psmSliceValueLabel() {
  const value = state.psmWorkbench.sliceValue || "";
  if (!value) {
    return "Any";
  }
  const item = sliceItems().find((candidate) => candidate.id === value);
  return item ? elementLabel(item) : value;
}

function sliceOptionButton({value, label, selected, optionKind}) {
  return renderWorkbenchSliceOption({
    typeKey: "psm",
    optionKind,
    value,
    label,
    selected
  });
}

function psmSliceMenuMarkup(optionKind) {
  if (optionKind === "kind") {
    return PSM_SLICE_KINDS.map(([value, label]) => sliceOptionButton({
      value,
      label,
      selected: state.psmWorkbench.sliceKind === value,
      optionKind
    })).join("");
  }
  const options = [{id: "", name: "Any"}, ...sliceItems()];
  return options.map((item) => sliceOptionButton({
    value: item.id,
    label: item.id ? elementLabel(item) : item.name,
    selected: (state.psmWorkbench.sliceValue || "") === item.id,
    optionKind
  })).join("");
}

function psmSliceSelectMarkup(optionKind) {
  const isKind = optionKind === "kind";
  const open = psmSliceMenuOpen === optionKind;
  const label = isKind ? psmSliceKindLabel() : psmSliceValueLabel();
  return renderWorkbenchSliceSelect({
    typeKey: "psm",
    optionKind,
    open,
    label,
    menuHtml: psmSliceMenuMarkup(optionKind)
  });
}

function renderBody(representation) {
  if (representation === "dashboard") {
    return renderDashboard();
  }
  if (representation === "register") {
    return renderRegister(activeProfile());
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
  if (representation === "guide") {
    return renderLevelGuidePanel("psm");
  }
  return "";
}

export function renderPsmWorkbenchSurface() {
  const host = ensureSurface();
  let hasLevelConfig = true;
  try {
    modelingLevelConfig("psm");
  } catch {
    hasLevelConfig = false;
  }
  const profile = activeProfile();
  const representation = activeRepresentation(profile);
  const controls = renderControls(profile, representation);
  renderWorkbenchSurfaceLayout({
    host,
    activeType: state.activeType,
    expectedType: "psm",
    minimized: state.modelingToolsMinimized,
    representation,
    controlsHtml: controls,
    bodyHtml: renderBody(representation),
    workbenchState: state.psmWorkbench,
    surfaceActiveClass: "psm-surface-active",
    surfaceDockClass: "psm-surface-dock",
    hasLevelConfig
  });
}

function commitModelChange(message) {
  commitWorkbenchModelChange({
    typeKey: "psm",
    renderWorkbench: renderPsmWorkbenchSurface,
    renderDiagram: renderDiagramCallback,
    renderPalette: renderPaletteCallback,
    message,
    syncActiveViewFromVisibleGraph,
    saveCurrentTabGraphState,
    markModelDirty,
    setStatus
  });
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

function currentCenter() {
  const rect = el.canvasViewport?.getBoundingClientRect();
  if (!rect) {
    return {x: 120, y: 120};
  }
  return {
    x: Math.round((rect.width / 2 - state.viewport.x) / state.viewport.scale),
    y: Math.round((rect.height / 2 - state.viewport.y) / state.viewport.scale)
  };
}

function addNode(type, x, y, name = "") {
  const node = getDefaultNode("psm", type, Math.round(x), Math.round(y));
  if (name) {
    node.label = name;
    node.meta.name = name;
    node.meta.label = name;
  }
  state.diagram.nodes.push(node);
  addNodeToGraphAndActiveView(node);
  return node;
}

function createPsmElement(type) {
  syncActiveViewFromVisibleGraph();
  const center = currentCenter();
  const node = addNode(type, center.x, center.y, type);
  switch (type) {
    case "SamStack":
      node.meta.stackName ||= "main-stack";
      break;
    case "AwsStage":
      node.meta.stageName ||= "dev";
      break;
    case "AwsLambdaFunction":
      node.meta.runtime ||= "provided.al2";
      node.meta.memorySizeMb ||= 256;
      node.meta.timeoutSeconds ||= 30;
      break;
    case "ApiGatewayApi":
      node.meta.endpointType ||= "REGIONAL";
      break;
    case "DynamoDbTable":
      node.meta.billingMode ||= "PAY_PER_REQUEST";
      node.meta.pointInTimeRecoveryEnabled ??= true;
      break;
    case "S3Bucket":
      node.meta.publicAccessMode ||= "BLOCK";
      node.meta.versioningEnabled ??= true;
      break;
    case "IamRole":
      node.meta.roleName ||= node.label;
      break;
    case "CloudWatchLogGroup":
      node.meta.retentionInDays ||= 30;
      break;
    case "EnvironmentConfig":
      node.meta.environmentName ||= "dev";
      break;
    default:
      break;
  }
  commitModelChange(`Added ${type}`);
  openAttributePanelCallback?.(node.id);
}

function createRequiredRoot() {
  const center = currentCenter();
  const stack = elements().find((row) => row.eClass === "SamStack")
      || addNode("SamStack", center.x - 140, center.y, "Main Stack");
  const stage = elements().find((row) => row.eClass === "AwsStage")
      || addNode("AwsStage", center.x + 140, center.y, "Dev Stage");
  const deployedStacks = new Set(refIds(stage.deploysStacks));
  deployedStacks.add(stack.id);
  stage.deploysStacks = [...deployedStacks];
  const visibleStage = state.nodesById.get(stage.id);
  if (visibleStage?.meta) {
    visibleStage.meta.deploysStacks = stage.deploysStacks;
  }
  commitModelChange("Completed PSM root model");
  openAttributePanelCallback?.(stack.id);
}

async function importCsv(file, profile = activeProfile()) {
  const type = activeAddType(profile);
  if (!type || !file) {
    return;
  }
  const text = await file.text();
  const [headerLine, ...lines] = text.split(/\r?\n/).filter(Boolean);
  const headers = headerLine.split(",").map((item) => item.trim());
  lines.forEach((line, index) => {
    const values = line.split(",");
    const center = currentCenter();
    const node = addNode(type, center.x + index * 34, center.y + index * 34,
        `${type} ${index + 1}`);
    headers.forEach((header, columnIndex) => {
      if (header && values[columnIndex] !== undefined) {
        node.meta[header] = values[columnIndex];
      }
    });
    node.label = node.meta.name || node.meta.logicalId || node.label;
    node.meta.name = node.label;
    node.meta.label = node.label;
  });
  commitModelChange(`Imported ${lines.length} ${type} row(s)`);
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
    const mode = target?.closest("[data-psm-mode]")?.dataset?.psmMode;
    if (mode) {
      setActiveRepresentation(mode);
      return;
    }
    const edgeMode = target?.closest("[data-psm-edge-mode]")?.dataset
        ?.psmEdgeMode;
    if (edgeMode) {
      applyPsmEdgeMode(edgeMode);
      return;
    }
    const addType = target?.closest("[data-psm-add-type]")?.dataset?.psmAddType;
    if (addType) {
      createPsmElement(addType);
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
    if (target?.closest("[data-psm-create-root]")) {
      createRequiredRoot();
      return;
    }
    if (target?.closest("[data-psm-export]")) {
      exportCsv(activeProfile());
      return;
    }
    const sortKey = target?.closest("[data-psm-sort]")?.dataset?.psmSort;
    if (sortKey) {
      state.psmWorkbench.sortKey = sortKey;
      renderPsmWorkbenchSurface();
      return;
    }
    const sliceToggle = target?.closest("[data-psm-slice-toggle]")?.dataset
        ?.psmSliceToggle;
    if (sliceToggle) {
      psmSliceMenuOpen = psmSliceMenuOpen === sliceToggle ? "" : sliceToggle;
      renderPsmWorkbenchSurface();
      return;
    }
    const sliceOption = target?.closest("[data-psm-slice-option]");
    if (sliceOption) {
      const optionKind = sliceOption.dataset.psmSliceOption;
      const value = sliceOption.dataset.psmSliceOptionValue || "";
      if (optionKind === "kind") {
        state.psmWorkbench.sliceKind = value;
        state.psmWorkbench.sliceValue = "";
      } else {
        state.psmWorkbench.sliceValue = value;
      }
      psmSliceMenuOpen = "";
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
    if (target.dataset.psmRegister !== undefined) {
      setActiveRegister(target.value);
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
      return;
    }
    if (target.dataset.psmImport !== undefined && target.files?.[0]) {
      void importCsv(target.files[0], activeProfile());
    }
  });
  host.addEventListener("input", (event) => {
    const target = event.target instanceof HTMLInputElement ? event.target
        : null;
    if (target?.dataset?.psmSearch !== undefined) {
      state.psmWorkbench.search = target.value;
      scheduleSearchRender();
    }
  });
  document.addEventListener("click", (event) => {
    const target = event.target instanceof Element ? event.target : null;
    if (psmSliceMenuOpen && !target?.closest(".workbench-slice-select-wrap")) {
      psmSliceMenuOpen = "";
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
