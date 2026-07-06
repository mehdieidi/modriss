import * as g6 from "./g6-renderer-adapter.js";

let mountOptions = null;

export function activeRendererKind() {
  return "antv-g6";
}

export function configuredRendererKind() {
  return "antv-g6";
}

export async function initializeRendererAdapter() {
  // AntV G6 is the only diagram renderer.
}

export function setCanvasMountOptions(options) {
  mountOptions = options;
}

export function applyActiveRendererChrome(targetEl) {
  document.documentElement.dataset.renderer = "antv-g6";
  targetEl.canvasGrid?.setAttribute("data-renderer", "antv-g6");
  targetEl.canvasGrid?.classList.add("g6-renderer-active");
  targetEl.canvasGrid?.classList.remove("g6-renderer-unavailable");
}

export function isCanvasRendererAvailable() {
  return g6.isCanvasRendererAvailable();
}

export function getCanvasEditor() {
  return g6.getCanvasEditor();
}

export async function ensureCanvas() {
  await initializeRendererAdapter();
  const { el } = await import("../dom.js");
  const { setStatus } = await import("../status.js");

  applyActiveRendererChrome(el);
  if (g6.getCanvasEditor()) {
    return true;
  }
  if (!g6.isCanvasRendererAvailable()) {
    el.canvasGrid?.setAttribute("data-renderer", "antv-g6-unavailable");
    document.documentElement.dataset.renderer = "antv-g6-unavailable";
    el.canvasGrid?.classList.add("g6-renderer-unavailable");
    setStatus("AntV G6 failed to load; model canvas renderer unavailable");
    return true;
  }
  try {
    g6.mountCanvasEditor(el.g6EditorHost, mountOptions || {});
    el.g6EditorHost?.classList.add("is-mounted");
    applyActiveRendererChrome(el);
    g6.syncCanvasFromState({ full: true });
    return true;
  } catch (error) {
    console.error("G6 editor mount failed", error);
    el.canvasGrid?.classList.add("g6-renderer-unavailable");
    el.canvasGrid?.setAttribute("data-renderer", "antv-g6-unavailable");
    document.documentElement.dataset.renderer = "antv-g6-unavailable";
    setStatus("AntV G6 renderer failed; model canvas renderer unavailable");
    return true;
  }
}

export const syncCanvasFromState = (...args) => g6.syncCanvasFromState(...args);
export const renderCanvasDiagram = (...args) => g6.renderCanvasDiagram(...args);
export const updateCanvasSelection = (...args) => g6.updateCanvasSelection(...args);
export const updateCanvasViewport = (...args) => g6.updateCanvasViewport(...args);
export const updateCanvasConnectionState = (...args) => g6.updateCanvasConnectionState(...args);
export const updateCanvasContextBoxes = (...args) => g6.updateCanvasContextBoxes(...args);
export const updateCanvasImpactState = (...args) => g6.updateCanvasImpactState(...args);
export const updateCanvasNode = (...args) => g6.updateCanvasNode(...args);
export const updateCanvasEdge = (...args) => g6.updateCanvasEdge(...args);
export const updateCanvasNodeIcons = (...args) => g6.updateCanvasNodeIcons(...args);
export const refreshCanvasEdges = (...args) => g6.refreshCanvasEdges(...args);
export const resetCanvasView = (...args) => g6.resetCanvasView(...args);
export const zoomCanvasBy = (...args) => g6.zoomCanvasBy(...args);
export const fitCanvasToDiagram = (...args) => g6.fitCanvasToDiagram(...args);
export const focusCanvasNode = (...args) => g6.focusCanvasNode(...args);
export const focusCanvasPoint = (...args) => g6.focusCanvasPoint(...args);
export const onCanvasViewportChanged = (...args) => g6.onCanvasViewportChanged(...args);
export const beginCanvasInlineLabelEdit = (...args) => g6.beginCanvasInlineLabelEdit(...args);
export const addCanvasNode = (...args) => g6.addCanvasNode(...args);
export const addCanvasEdge = (...args) => g6.addCanvasEdge(...args);
export const setCanvasHoverNode = (...args) => g6.setCanvasHoverNode(...args);
export const setCanvasHoverEdge = (...args) => g6.setCanvasHoverEdge(...args);
export const toGraphCoordinates = (...args) => g6.toGraphCoordinates(...args);

export function createRenderer() {
  return g6;
}
