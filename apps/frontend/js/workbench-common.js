import {el} from './dom.js';

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

export function ensureWorkbenchSurface(existingSurface, surfaceId) {
  if (existingSurface) {
    return existingSurface;
  }
  const surface = document.createElement("div");
  surface.id = surfaceId;
  surface.className = "cim-workbench-surface hidden";
  el.canvasGrid?.appendChild(surface);
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
  if (activeType !== expectedType || minimized || !hasLevelConfig) {
    host.className = "cim-workbench-surface hidden";
    el.canvasGrid?.classList.remove(surfaceActiveClass, surfaceDockClass);
    restoreWorkbenchPalette(workbenchState);
    return;
  }
  if (representation === "diagram") {
    host.className = "cim-workbench-surface cim-workbench-dock";
    host.innerHTML = controlsHtml;
    el.canvasGrid?.classList.remove(surfaceActiveClass);
    el.canvasGrid?.classList.add(surfaceDockClass);
    restoreWorkbenchPalette(workbenchState);
    return;
  }
  host.className = "cim-workbench-surface";
  host.innerHTML = `${controlsHtml}<div class="cim-surface-body">${bodyHtml}</div>`;
  el.canvasGrid?.classList.add(surfaceActiveClass);
  el.canvasGrid?.classList.remove(surfaceDockClass);
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
  scheduleAutoSave,
  publishDiagramUpdate,
  setStatus
}) {
  syncActiveViewFromVisibleGraph();
  saveCurrentTabGraphState(typeKey);
  renderWorkbench();
  renderDiagram?.();
  renderPalette?.();
  scheduleAutoSave({delayMs: 250});
  publishDiagramUpdate({immediate: true});
  if (message) {
    setStatus(message);
  }
}
