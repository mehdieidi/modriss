import { el } from "./dom.js";
import { escapeHtml } from "./utils.js";
import { modelingLevelConfig, modelingLevelKeys } from "./modeling-config-data.js";
import {
  activeView,
  saveCurrentTabGraphState,
  syncActiveViewFromVisibleGraph,
} from "./graph-store.js";
import { materializeActiveView } from "./view-materializer.js";
import { state } from "./state.js";
import { setStatus } from "./status.js";

const WORKBENCH_EVENT_TYPES = [
  "pointerdown",
  "pointerup",
  "mousedown",
  "mouseup",
  "click",
  "dblclick",
  "touchstart",
  "touchmove",
  "wheel",
  "keydown",
  "keyup",
  "keypress",
  "beforeinput",
  "input",
  "change",
  "compositionstart",
  "compositionupdate",
  "compositionend",
  "dragover",
  "drop",
];

function syncValidationFabAnchor(surface) {
  const canvasStage = el.canvasGrid?.closest(".canvas-stage");
  if (!canvasStage) {
    return;
  }
  const isVisible = surface && !surface.classList.contains("hidden");
  if (!isVisible) {
    canvasStage.style.removeProperty("--validation-fab-top");
    return;
  }
  const stageRect = canvasStage.getBoundingClientRect();
  const surfaceRect = surface.getBoundingClientRect();
  const top = Math.max(0, Math.round(surfaceRect.bottom - stageRect.top + 8));
  canvasStage.style.setProperty("--validation-fab-top", `${top}px`);
}

export function ensureWorkbenchSurface(existingSurface, surfaceId) {
  const panel = el.modelWorkbenchPanel || document.getElementById("modelWorkbenchPanel");
  if (!panel) {
    return existingSurface;
  }
  let surface = panel.querySelector("#modelWorkbenchSurface");
  if (!surface) {
    surface = document.createElement("div");
    surface.id = "modelWorkbenchSurface";
    surface.dataset.sharedWorkbenchSurface = "true";
    surface.className = "model-workbench-surface hidden";
    panel.appendChild(surface);
  }
  if (existingSurface && existingSurface !== surface) {
    existingSurface.remove();
  }
  surface.dataset.surfaceId = surfaceId;
  return surface;
}

export function bindWorkbenchInteractionShield(surface) {
  WORKBENCH_EVENT_TYPES.forEach((type) => {
    surface.addEventListener(
      type,
      (event) => {
        event.stopPropagation();
      },
      { passive: type !== "wheel" },
    );
  });
}

export function hideWorkbenchPalette(workbenchState) {
  if (!el.workspace || el.workspace.classList.contains("palette-hidden")) {
    return;
  }
  workbenchState.hidPalette = true;
  el.workspace.classList.add("palette-hidden");
  el.paletteRailToggleBtn?.classList.remove("active");
}

export function restoreWorkbenchPalette(workbenchState) {
  if (!workbenchState?.hidPalette || !el.workspace) {
    return;
  }
  workbenchState.hidPalette = false;
  el.workspace.classList.remove("palette-hidden");
  el.paletteRailToggleBtn?.classList.add("active");
}

function csvEscape(text) {
  return /[",\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

export function downloadWorkbenchCsv(fileName, rows, columns, valueText) {
  const csv = [
    columns.join(","),
    ...rows.map((row) =>
      columns.map((column) => csvEscape(String(valueText(row[column])))).join(","),
    ),
  ].join("\n");
  const blob = new Blob([csv], { type: "text/csv" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = fileName;
  anchor.click();
  URL.revokeObjectURL(url);
}

export function activeWorkbenchRepresentation(workbenchState, viewId, profile, defaults) {
  return workbenchState.representationByViewId[viewId] || defaults[profile] || "diagram";
}

export function setWorkbenchRepresentation(
  workbenchState,
  viewId,
  mode,
  renderWorkbench,
  renderDiagram,
  renderPalette,
) {
  workbenchState.representationByViewId[viewId] = mode;
  renderWorkbench();
  renderDiagram?.();
  renderPalette?.();
}

const STANDARD_WORKBENCH_MODES = [
  ["diagram", "Diagram"],
  ["dashboard", "Dashboard"],
  ["register", "Register"],
  ["matrix", "Matrix"],
  ["board", "Board"],
  ["detail", "Detail"],
  ["guide", "Guide"],
];

export const STANDARD_EDGE_MODES = {
  both: { label: "Both" },
  flows: { label: "Flows" },
  references: { label: "Refs" },
};

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function flowRelationshipKinds(typeKey, allKinds) {
  let labels = {};
  try {
    labels = modelingLevelConfig(typeKey).relationshipKindLabels || {};
  } catch {
    labels = {};
  }
  return safeArray(allKinds).filter((kind) => {
    const text = `${kind} ${labels[kind] || ""}`.toLowerCase();
    return /flow|invoke|route|target|transition|subscription|event|message|request|response|data access|external|process|command|query/.test(
      text,
    );
  });
}

export function applyWorkbenchEdgeMode({
  typeKey,
  workbenchState,
  mode,
  graph = state.graph,
  renderWorkbench,
  renderDiagram,
  renderPalette,
}) {
  if (!STANDARD_EDGE_MODES[mode]) {
    return;
  }
  workbenchState.edgeMode = mode;
  const view = activeView();
  if (!view) {
    renderWorkbench?.();
    return;
  }
  syncActiveViewFromVisibleGraph();
  const allKinds = [...(graph?.relationshipsByKind?.keys?.() || [])].sort();
  const flowKinds = flowRelationshipKinds(typeKey, allKinds);
  view.filters ??= {};
  if (mode === "both") {
    view.filters.relationshipKinds = [];
  } else if (mode === "flows") {
    view.filters.relationshipKinds = flowKinds.length
      ? flowKinds
      : [`__${typeKey.toUpperCase()}_NO_FLOW_EDGES__`];
  } else {
    const flowKindSet = new Set(flowKinds);
    const referenceKinds = allKinds.filter((kind) => !flowKindSet.has(kind));
    view.filters.relationshipKinds = referenceKinds.length
      ? referenceKinds
      : [`__${typeKey.toUpperCase()}_NO_REFERENCE_EDGES__`];
  }
  materializeActiveView();
  saveCurrentTabGraphState(typeKey);
  renderWorkbench?.();
  renderDiagram?.();
  renderPalette?.();
  setStatus(`${STANDARD_EDGE_MODES[mode].label} edge mode applied`);
}

export function renderWorkbenchSliceOption({ typeKey, optionKind, value, label, selected }) {
  return `<button class="workbench-view-option${selected ? " is-active" : ""}"
            type="button"
            data-${typeKey}-slice-option="${escapeHtml(optionKind)}"
            data-${typeKey}-slice-option-value="${escapeHtml(value)}"
            role="option"
            aria-selected="${selected ? "true" : "false"}">
      <span class="workbench-view-option-label">${escapeHtml(label)}</span>
    </button>`;
}

export function renderWorkbenchSliceSelect({ typeKey, optionKind, open, label, menuHtml }) {
  const isKind = optionKind === "kind";
  const dataAttr = isKind ? `data-${typeKey}-slice-kind` : `data-${typeKey}-slice-value`;
  const legacyClass = " model-slice-select-wrap";
  const legacySelectClass = " model-slice-select";
  const legacyMenuClass = " model-slice-menu";
  return `<div class="workbench-view-select-wrap workbench-slice-select-wrap${legacyClass}${
    open ? " is-open" : ""
  }">
      <button class="sidebar-select workbench-view-select workbench-slice-select${legacySelectClass}"
              type="button"
              ${dataAttr}
              data-${typeKey}-slice-toggle="${escapeHtml(optionKind)}"
              aria-haspopup="listbox"
              aria-expanded="${open ? "true" : "false"}">
        <span class="workbench-view-select-label">${escapeHtml(label)}</span>
      </button>
      <span class="workbench-view-select-caret" aria-hidden="true"></span>
      <div class="workbench-view-menu workbench-slice-menu${legacyMenuClass}${
        open ? "" : " hidden"
      }" role="listbox">
        ${menuHtml}
      </div>
    </div>`;
}

export function renderWorkbenchControls({
  typeKey,
  title,
  profile,
  representation,
  workbenchState,
  sliceControlsHtml = "",
  edgeModes = STANDARD_EDGE_MODES,
  modes = STANDARD_WORKBENCH_MODES,
  searchPlaceholder = "Search model...",
}) {
  const dataPrefix = typeKey;
  return `<div class="model-surface-header">
    <div class="model-surface-title">
      <strong>${escapeHtml(title || `${typeKey.toUpperCase()} View`)}</strong>
      <span>${escapeHtml(profile)}</span>
    </div>
    <div class="model-mode-tabs">${modes
      .map(
        ([mode, label]) => `
      <button class="${representation === mode ? "is-active" : ""}"
              data-${dataPrefix}-mode="${escapeHtml(mode)}"
              type="button">${escapeHtml(label)}</button>`,
      )
      .join("")}</div>
    <div class="model-edge-toolbar" aria-label="${escapeHtml(typeKey.toUpperCase())} edge visibility">
      <span>Edges</span>
      <div class="model-edge-buttons">${Object.entries(edgeModes)
        .map(
          ([key, mode]) => `
        <button class="${(workbenchState.edgeMode || "both") === key ? "is-active" : ""}"
                data-${dataPrefix}-edge-mode="${escapeHtml(key)}"
                type="button">${escapeHtml(mode.label)}</button>`,
        )
        .join("")}</div>
    </div>
    <div class="model-filter-row">
      <input data-${dataPrefix}-search placeholder="${escapeHtml(searchPlaceholder)}" type="search"
             value="${escapeHtml(workbenchState.search || "")}">
      ${sliceControlsHtml}
      <label class="model-check-label">
        <input data-${dataPrefix}-missing-only type="checkbox" ${
          workbenchState.missingOnly ? "checked" : ""
        }> Missing required
      </label>
    </div>
  </div>`;
}

export function renderWorkbenchToolbar(actions = []) {
  return `<div class="model-toolbar">${actions.filter(Boolean).join("")}</div>`;
}

export function renderWorkbenchRegister({
  typeKey,
  toolbarHtml = "",
  rows = [],
  columns = [],
  getLabel,
  getBadgeHtml,
  renderCell,
  emptyText = "No rows in this slice.",
}) {
  const colSpan = columns.length + 2;
  return `${toolbarHtml}
    <div class="model-table-wrap">
      <table class="model-table">
        <thead><tr>
          <th>Element</th>
          ${columns
            .map(
              (column) =>
                `<th><button data-${typeKey}-sort="${escapeHtml(
                  column,
                )}" type="button">${escapeHtml(column)}</button></th>`,
            )
            .join("")}
          <th></th>
        </tr></thead>
        <tbody>
          ${
            rows.length
              ? rows
                  .map(
                    (row) => `<tr>
            <td>
              <button class="model-link" data-${typeKey}-open="${escapeHtml(
                row.id,
              )}" type="button">${escapeHtml(getLabel(row))}</button>
              <div class="model-row-meta">${getBadgeHtml(row)}</div>
            </td>
            ${columns.map((column) => `<td>${renderCell(row, column)}</td>`).join("")}
            <td><button class="model-icon-action" data-${typeKey}-open="${escapeHtml(
              row.id,
            )}" title="Open detail" type="button">Open</button></td>
          </tr>`,
                  )
                  .join("")
              : `<tr><td colspan="${colSpan}"
              class="model-empty">${escapeHtml(emptyText)}</td></tr>`
          }
        </tbody>
      </table>
    </div>`;
}

export function renderWorkbenchDashboard({
  metrics = [],
  primaryHtml = "",
  actionsHtml = "",
  secondaryHtml = "",
}) {
  const foundation =
    primaryHtml ||
    `<section class="model-root-form">
    <div class="model-section-title">Model Foundation</div>
    <div class="model-ok">Foundation requirements are satisfied.</div>
  </section>`;
  const activity =
    secondaryHtml ||
    `<section class="model-view-entry-list">
    <div class="model-section-title">Actions</div>
    ${actionsHtml || `<div class="model-empty">No actions available.</div>`}
  </section>`;
  return `<div class="model-dashboard-grid">
    ${foundation}
    <section class="model-health">
      <div class="model-section-title">Health Summary</div>
      <div class="model-metric-grid">${metrics
        .map(
          ([label, value]) => `
        <div class="model-metric"><strong>${escapeHtml(value)}</strong><span>${escapeHtml(
          label,
        )}</span></div>`,
        )
        .join("")}</div>
    </section>
    ${activity}
  </div>`;
}

export function renderWorkbenchMatrixSection({
  title,
  rows = [],
  columns = [],
  typeKey,
  getRowLabel,
  getColumnLabel,
  getColumnMeta = () => "",
  getCellHtml,
  emptyText = "No rows or columns available.",
}) {
  if (!rows.length || !columns.length) {
    return `<section class="model-matrix-section">
      <div class="model-section-title">${escapeHtml(title)}</div>
      <div class="model-empty">${escapeHtml(emptyText)}</div>
    </section>`;
  }
  return `<section class="model-matrix-section">
    <div class="model-section-title">${escapeHtml(title)}</div>
    <div class="model-matrix-wrap">
      <table class="model-matrix">
        <thead><tr><th></th>${columns
          .map(
            (column) =>
              `<th>${escapeHtml(getColumnLabel(column))}${
                getColumnMeta(column) ? `<span>${escapeHtml(getColumnMeta(column))}</span>` : ""
              }</th>`,
          )
          .join("")}</tr></thead>
        <tbody>${rows
          .map(
            (row) => `<tr>
          <th><button class="model-link" data-${typeKey}-open="${escapeHtml(
            row.id,
          )}" type="button">${escapeHtml(getRowLabel(row))}</button>
            <span>${escapeHtml(row.eClass || row.kind || "")}</span></th>
          ${columns.map((column) => getCellHtml(row, column)).join("")}
        </tr>`,
          )
          .join("")}</tbody>
      </table>
    </div>
  </section>`;
}

export function renderWorkbenchDataTable({
  title,
  columns = [],
  rows = [],
  emptyText = "No rows available.",
}) {
  return `<section class="model-matrix-section">
    <div class="model-section-title">${escapeHtml(title)}</div>
    <div class="model-table-wrap">
      <table class="model-table">
        <thead><tr>${columns
          .map((column) => `<th>${escapeHtml(column)}</th>`)
          .join("")}</tr></thead>
        <tbody>${
          rows.join("") ||
          `<tr><td colspan="${columns.length}"
            class="model-empty">${escapeHtml(emptyText)}</td></tr>`
        }</tbody>
      </table>
    </div>
  </section>`;
}

export function renderWorkbenchBoard({ lanes = [] }) {
  return `<div class="model-board">${lanes
    .map(
      (lane) => `
    <section class="model-board-lane">
      <div class="model-section-title">${escapeHtml(lane.title)}${
        lane.count !== undefined ? ` ${escapeHtml(lane.count)}` : ""
      }</div>
      ${
        lane.cards?.length
          ? lane.cards.join("")
          : `<div class="model-empty">${escapeHtml(lane.emptyText || "No items")}</div>`
      }
    </section>`,
    )
    .join("")}</div>`;
}

export function renderWorkbenchBoardCard({ typeKey, id, title, meta = "", body = "" }) {
  return `<button class="model-board-card" data-${typeKey}-open="${escapeHtml(id)}" type="button">
    <strong>${escapeHtml(title)}</strong>
    <span>${escapeHtml(meta)}</span>
    ${body ? `<em>${escapeHtml(body)}</em>` : ""}
  </button>`;
}

export function renderWorkbenchDetail({
  typeKey,
  row,
  fields = [],
  title,
  typeLabel,
  valueText,
  stats = [],
  emptyText = "Select an element on the diagram or open one from a register.",
}) {
  if (!row) {
    return `<section class="model-detail-projection">
      <div class="model-empty">${escapeHtml(emptyText)}</div>
    </section>`;
  }
  return `<section class="model-detail-projection">
    <div class="model-detail-title">
      <strong>${escapeHtml(title)}</strong>
      <span>${escapeHtml(typeLabel)}</span>
    </div>
    <div class="model-detail-grid">
      ${fields
        .map(
          (field) => `<div>
        <span>${escapeHtml(field)}</span>
        <strong>${escapeHtml(valueText(row[field]))}</strong>
      </div>`,
        )
        .join("")}
      ${stats
        .map(
          ([label, value]) => `<div><span>${escapeHtml(label)}</span>
        <strong>${escapeHtml(value)}</strong></div>`,
        )
        .join("")}
    </div>
    <div class="model-dashboard-actions">
      <button class="model-action" data-${typeKey}-open="${escapeHtml(row.id)}"
          type="button">Open Full Inspector</button>
    </div>
  </section>`;
}

function selectorForWorkbenchControl(control) {
  if (!control?.dataset) {
    return "";
  }
  const levelPrefixes = new Set(modelingLevelKeys());
  const dataName = Object.keys(control.dataset).find(
    (key) =>
      [...levelPrefixes].some((prefix) => key.startsWith(prefix)) &&
      (key.endsWith("Search") || key.endsWith("SliceToggle") || key.endsWith("Register")),
  );
  if (!dataName) {
    return "";
  }
  return `[data-${dataName.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`)}]`;
}

function captureWorkbenchFocus(host) {
  const control = document.activeElement;
  if (!host?.contains(control)) {
    return null;
  }
  const selector = selectorForWorkbenchControl(control);
  if (!selector) {
    return null;
  }
  return {
    selector,
    isSearch: selector.endsWith("-search]"),
    start: typeof control.selectionStart === "number" ? control.selectionStart : null,
    end: typeof control.selectionEnd === "number" ? control.selectionEnd : null,
  };
}

function restoreWorkbenchFocus(host, snapshot) {
  if (!snapshot) {
    return;
  }
  const control = host?.querySelector(snapshot.selector);
  if (!control || typeof control.focus !== "function") {
    return;
  }
  try {
    control.focus({ preventScroll: true });
  } catch {
    control.focus();
  }
  if (snapshot.start !== null && typeof control.setSelectionRange === "function") {
    const length = String(control.value || "").length;
    control.setSelectionRange(
      Math.min(snapshot.start, length),
      Math.min(snapshot.end ?? snapshot.start, length),
    );
  }
}

export function renderWorkbenchSurfaceLayout({
  host,
  activeType,
  expectedType,
  minimized,
  representation,
  controlsHtml,
  bodyHtml,
  workbenchState,
  surfaceActiveClass,
  surfaceDockClass,
  hasLevelConfig = true,
}) {
  const focusSnapshot = captureWorkbenchFocus(host);
  const canvasStage = el.canvasGrid?.closest(".canvas-stage");
  const isSharedSurface = host?.dataset?.sharedWorkbenchSurface === "true";
  if (activeType !== expectedType || minimized || !hasLevelConfig) {
    const ownsSharedSurface =
      host?.dataset?.workbenchType === expectedType || !host?.dataset?.workbenchType;
    if (isSharedSurface && !ownsSharedSurface) {
      el.canvasGrid?.classList.remove(surfaceActiveClass, surfaceDockClass);
      canvasStage?.classList.remove(surfaceActiveClass, surfaceDockClass);
      restoreWorkbenchPalette(workbenchState);
      return;
    }
    host.className = "model-workbench-surface hidden";
    if (host?.dataset) {
      delete host.dataset.workbenchType;
    }
    el.canvasGrid?.classList.remove(surfaceActiveClass, surfaceDockClass);
    canvasStage?.classList.remove(surfaceActiveClass, surfaceDockClass);
    syncValidationFabAnchor(host);
    restoreWorkbenchPalette(workbenchState);
    return;
  }
  if (host?.dataset) {
    host.dataset.workbenchType = expectedType;
  }
  if (focusSnapshot?.isSearch && representation === "diagram") {
    host.className = "model-workbench-surface model-workbench-dock";
    restoreWorkbenchFocus(host, focusSnapshot);
    el.canvasGrid?.classList.remove(surfaceActiveClass);
    el.canvasGrid?.classList.add(surfaceDockClass);
    canvasStage?.classList.remove(surfaceActiveClass);
    canvasStage?.classList.add(surfaceDockClass);
    requestAnimationFrame(() => syncValidationFabAnchor(host));
    restoreWorkbenchPalette(workbenchState);
    return;
  }
  if (focusSnapshot?.isSearch && representation !== "diagram") {
    const body = host.querySelector(".model-surface-body");
    if (body) {
      host.className = "model-workbench-surface";
      body.innerHTML = bodyHtml;
      restoreWorkbenchFocus(host, focusSnapshot);
      el.canvasGrid?.classList.add(surfaceActiveClass);
      el.canvasGrid?.classList.remove(surfaceDockClass);
      canvasStage?.classList.add(surfaceActiveClass);
      canvasStage?.classList.remove(surfaceDockClass);
      requestAnimationFrame(() => syncValidationFabAnchor(host));
      hideWorkbenchPalette(workbenchState);
      return;
    }
  }
  if (representation === "diagram") {
    host.className = "model-workbench-surface model-workbench-dock";
    host.innerHTML = controlsHtml;
    restoreWorkbenchFocus(host, focusSnapshot);
    el.canvasGrid?.classList.remove(surfaceActiveClass);
    el.canvasGrid?.classList.add(surfaceDockClass);
    canvasStage?.classList.remove(surfaceActiveClass);
    canvasStage?.classList.add(surfaceDockClass);
    requestAnimationFrame(() => syncValidationFabAnchor(host));
    restoreWorkbenchPalette(workbenchState);
    return;
  }
  host.className = "model-workbench-surface";
  host.innerHTML = `${controlsHtml}<div class="model-surface-body">${bodyHtml}</div>`;
  restoreWorkbenchFocus(host, focusSnapshot);
  el.canvasGrid?.classList.add(surfaceActiveClass);
  el.canvasGrid?.classList.remove(surfaceDockClass);
  canvasStage?.classList.add(surfaceActiveClass);
  canvasStage?.classList.remove(surfaceDockClass);
  requestAnimationFrame(() => syncValidationFabAnchor(host));
  hideWorkbenchPalette(workbenchState);
}

export function commitWorkbenchModelChange({
  typeKey,
  renderWorkbench,
  renderDiagram,
  renderPalette,
  message,
  syncActiveViewFromVisibleGraph,
  saveCurrentTabGraphState,
  markModelDirty,
  setStatus,
}) {
  syncActiveViewFromVisibleGraph();
  saveCurrentTabGraphState(typeKey);
  renderWorkbench();
  renderDiagram?.();
  renderPalette?.();
  markModelDirty?.();
  if (message) {
    setStatus(message);
  }
}

function compactList(values, fallback = "Any") {
  const items = Array.isArray(values) ? values.map(String).filter(Boolean) : [];
  if (!items.length) {
    return fallback;
  }
  if (items.length <= 6) {
    return items.join(", ");
  }
  return `${items.slice(0, 6).join(", ")} +${items.length - 6}`;
}

function renderGuideCard(title, body, meta = "") {
  return `<section class="model-guide-card">
    <div>
      <strong>${escapeHtml(title)}</strong>
      ${meta ? `<span>${escapeHtml(meta)}</span>` : ""}
    </div>
    <p>${escapeHtml(body)}</p>
  </section>`;
}

function renderNotationCards(level) {
  const universal = Array.isArray(level.universalSyntax) ? level.universalSyntax : [];
  const kernel = Array.isArray(level.kernelNotation) ? level.kernelNotation : [];
  const cards = [
    ...universal.map((entry) =>
      renderGuideCard(
        entry.element || entry.name || "Syntax element",
        entry.notation || entry.behavior || entry.description || "",
        compactList(entry.surfaces, ""),
      ),
    ),
    ...kernel.map((entry) =>
      renderGuideCard(
        entry.element || entry.name || "Kernel notation",
        entry.notation || entry.behavior || entry.description || "",
        compactList(entry.surfaces, ""),
      ),
    ),
  ];
  return cards.join("");
}

function renderComplexityCards(level) {
  return (level.complexityManagement || [])
    .map((entry) =>
      renderGuideCard(
        entry.technique || "Technique",
        entry.behavior || entry.description || "",
        compactList(entry.surfaces, ""),
      ),
    )
    .join("");
}

function renderRelationshipLegend(level) {
  const labels = level.relationshipKindLabels || {};
  const rules = Array.isArray(level.relationshipVisualRules) ? level.relationshipVisualRules : [];
  if (!rules.length) {
    return "";
  }
  return rules
    .map((rule, index) => {
      const kinds = (rule.matchKinds || []).map(
        (kind) => labels[kind] || String(kind).toLowerCase().replaceAll("_", " "),
      );
      const swatchStyle = [
        rule.stroke ? `--guide-edge-color:${escapeHtml(rule.stroke)}` : "",
        Array.isArray(rule.lineDash) ? "--guide-edge-dash:6px" : "",
      ]
        .filter(Boolean)
        .join(";");
      return `<div class="model-guide-legend-row">
      <span class="model-guide-edge-swatch ${
        Array.isArray(rule.lineDash) ? "is-dashed" : ""
      }" style="${swatchStyle}"></span>
      <div>
        <strong>${escapeHtml(rule.label || kinds[0] || `Relationship style ${index + 1}`)}</strong>
        <span>${escapeHtml(compactList(kinds, "Configured by eClass/field"))}</span>
      </div>
    </div>`;
    })
    .join("");
}

function renderViewCards(level) {
  return (level.viewDefinitions || [])
    .map((view) =>
      renderGuideCard(
        view.displayName || view.name || view.id || "View",
        view.description || `Viewpoint ${view.viewpoint || view.viewType || view.id || "model"}`,
        `${view.viewType || "view"} | ${compactList(view.elementTypes, "configured elements")}`,
      ),
    )
    .join("");
}

export function renderLevelGuidePanel(typeKey) {
  const level = modelingLevelConfig(typeKey);
  return `<div class="model-guide-panel">
    <section class="model-guide-section">
      <div class="model-guide-heading">
        <strong>Concrete Syntax</strong>
        <span>${escapeHtml(level.displayName || typeKey.toUpperCase())}</span>
      </div>
      <div class="model-guide-grid">${renderNotationCards(level)}</div>
    </section>
    <section class="model-guide-section">
      <div class="model-guide-heading">
        <strong>Relationship Legend</strong>
        <span>${escapeHtml(String((level.relationshipKinds || []).length))} kinds</span>
      </div>
      <div class="model-guide-legend">${renderRelationshipLegend(level)}</div>
    </section>
    <section class="model-guide-section">
      <div class="model-guide-heading">
        <strong>Complexity Management</strong>
        <span>${escapeHtml(String((level.complexityManagement || []).length))} techniques</span>
      </div>
      <div class="model-guide-grid">${renderComplexityCards(level)}</div>
    </section>
    <section class="model-guide-section">
      <div class="model-guide-heading">
        <strong>Views</strong>
        <span>${escapeHtml(String((level.viewDefinitions || []).length))} definitions</span>
      </div>
      <div class="model-guide-grid">${renderViewCards(level)}</div>
    </section>
  </div>`;
}
