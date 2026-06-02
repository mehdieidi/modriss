import {el} from './dom.js';
import {escapeHtml} from './utils.js';
import {modelingLevelConfig} from './modeling-config-data.js';

const WORKBENCH_EVENT_TYPES = [
  "mousedown",
  "mouseup",
  "click",
  "dblclick",
  "touchstart",
  "touchmove",
  "wheel",
  "dragover",
  "drop"
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
  const panel = el.modelWorkbenchPanel || document.getElementById(
      "modelWorkbenchPanel");
  if (!panel) {
    return existingSurface;
  }
  let surface = panel.querySelector("#modelWorkbenchSurface");
  if (!surface) {
    surface = document.createElement("div");
    surface.id = "modelWorkbenchSurface";
    surface.dataset.sharedWorkbenchSurface = "true";
    surface.className = "cim-workbench-surface hidden";
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
    surface.addEventListener(type, (event) => {
      event.stopPropagation();
    }, {passive: type !== "wheel"});
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
    ...rows.map((row) => columns.map((column) => csvEscape(
        String(valueText(row[column])))).join(","))
  ].join("\n");
  const blob = new Blob([csv], {type: "text/csv"});
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = fileName;
  anchor.click();
  URL.revokeObjectURL(url);
}

export function activeWorkbenchRepresentation(workbenchState, viewId, profile,
    defaults) {
  return workbenchState.representationByViewId[viewId] || defaults[profile]
      || "diagram";
}

export function setWorkbenchRepresentation(workbenchState, viewId, mode,
    renderWorkbench, renderDiagram, renderPalette) {
  workbenchState.representationByViewId[viewId] = mode;
  renderWorkbench();
  renderDiagram?.();
  renderPalette?.();
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
  hasLevelConfig = true
}) {
  const canvasStage = el.canvasGrid?.closest(".canvas-stage");
  const isSharedSurface = host?.dataset?.sharedWorkbenchSurface === "true";
  if (activeType !== expectedType || minimized || !hasLevelConfig) {
    const ownsSharedSurface = host?.dataset?.workbenchType === expectedType
        || !host?.dataset?.workbenchType;
    if (isSharedSurface && !ownsSharedSurface) {
      el.canvasGrid?.classList.remove(surfaceActiveClass, surfaceDockClass);
      canvasStage?.classList.remove(surfaceActiveClass, surfaceDockClass);
      restoreWorkbenchPalette(workbenchState);
      return;
    }
    host.className = "cim-workbench-surface hidden";
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
  if (representation === "diagram") {
    host.className = "cim-workbench-surface cim-workbench-dock";
    host.innerHTML = controlsHtml;
    el.canvasGrid?.classList.remove(surfaceActiveClass);
    el.canvasGrid?.classList.add(surfaceDockClass);
    canvasStage?.classList.remove(surfaceActiveClass);
    canvasStage?.classList.add(surfaceDockClass);
    requestAnimationFrame(() => syncValidationFabAnchor(host));
    restoreWorkbenchPalette(workbenchState);
    return;
  }
  host.className = "cim-workbench-surface";
  host.innerHTML = `${controlsHtml}<div class="cim-surface-body">${bodyHtml}</div>`;
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
  setStatus
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
  return `<section class="cim-guide-card">
    <div>
      <strong>${escapeHtml(title)}</strong>
      ${meta ? `<span>${escapeHtml(meta)}</span>` : ""}
    </div>
    <p>${escapeHtml(body)}</p>
  </section>`;
}

function renderNotationCards(level) {
  const universal = Array.isArray(level.universalSyntax)
      ? level.universalSyntax : [];
  const kernel = Array.isArray(level.kernelNotation)
      ? level.kernelNotation : [];
  const cards = [
    ...universal.map((entry) => renderGuideCard(
        entry.element || entry.name || "Syntax element",
        entry.notation || entry.behavior || entry.description || "",
        compactList(entry.surfaces, ""))),
    ...kernel.map((entry) => renderGuideCard(
        entry.element || entry.name || "Kernel notation",
        entry.notation || entry.behavior || entry.description || "",
        compactList(entry.surfaces, "")))
  ];
  return cards.join("");
}

function renderComplexityCards(level) {
  return (level.complexityManagement || []).map((entry) => renderGuideCard(
      entry.technique || "Technique",
      entry.behavior || entry.description || "",
      compactList(entry.surfaces, ""))).join("");
}

function renderRelationshipLegend(level) {
  const labels = level.relationshipKindLabels || {};
  const rules = Array.isArray(level.relationshipVisualRules)
      ? level.relationshipVisualRules : [];
  if (!rules.length) {
    return "";
  }
  return rules.map((rule, index) => {
    const kinds = (rule.matchKinds || []).map((kind) =>
        labels[kind] || String(kind).toLowerCase().replaceAll("_", " "));
    const swatchStyle = [
      rule.stroke ? `--guide-edge-color:${escapeHtml(rule.stroke)}` : "",
      Array.isArray(rule.lineDash) ? "--guide-edge-dash:6px" : ""
    ].filter(Boolean).join(";");
    return `<div class="cim-guide-legend-row">
      <span class="cim-guide-edge-swatch ${Array.isArray(rule.lineDash)
        ? "is-dashed" : ""}" style="${swatchStyle}"></span>
      <div>
        <strong>${escapeHtml(rule.label || kinds[0]
        || `Relationship style ${index + 1}`)}</strong>
        <span>${escapeHtml(compactList(kinds, "Configured by eClass/field"))}</span>
      </div>
    </div>`;
  }).join("");
}

function renderViewCards(level) {
  return (level.viewDefinitions || []).map((view) => renderGuideCard(
      view.displayName || view.name || view.id || "View",
      view.description || `Viewpoint ${view.viewpoint || view.viewType
      || view.id || "model"}`,
      `${view.viewType || "view"} | ${compactList(view.elementTypes,
          "configured elements")}`)).join("");
}

export function renderLevelGuidePanel(typeKey) {
  const level = modelingLevelConfig(typeKey);
  return `<div class="cim-guide-panel">
    <section class="cim-guide-section">
      <div class="cim-guide-heading">
        <strong>Concrete Syntax</strong>
        <span>${escapeHtml(level.displayName || typeKey.toUpperCase())}</span>
      </div>
      <div class="cim-guide-grid">${renderNotationCards(level)}</div>
    </section>
    <section class="cim-guide-section">
      <div class="cim-guide-heading">
        <strong>Relationship Legend</strong>
        <span>${escapeHtml(String((level.relationshipKinds || []).length))} kinds</span>
      </div>
      <div class="cim-guide-legend">${renderRelationshipLegend(level)}</div>
    </section>
    <section class="cim-guide-section">
      <div class="cim-guide-heading">
        <strong>Complexity Management</strong>
        <span>${escapeHtml(String((level.complexityManagement || []).length))} techniques</span>
      </div>
      <div class="cim-guide-grid">${renderComplexityCards(level)}</div>
    </section>
    <section class="cim-guide-section">
      <div class="cim-guide-heading">
        <strong>Views</strong>
        <span>${escapeHtml(String((level.viewDefinitions || []).length))} definitions</span>
      </div>
      <div class="cim-guide-grid">${renderViewCards(level)}</div>
    </section>
  </div>`;
}
