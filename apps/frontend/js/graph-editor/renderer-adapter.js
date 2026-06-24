import { state } from "../state.js";
import { getAuthToken } from "../auth.js";
import * as g6 from "./g6-renderer-adapter.js";
import { toGraphCoordinates as glspToGraphCoordinates } from "./glsp-renderer.js";
import { bindSprottyHostFocus } from "./glsp-sprotty-host.js";
import { glspSessionKey } from "./glsp-client-adapter.js";

let rendererKind = "antv-g6";
let glspRenderer = null;
let mountOptions = null;

export function activeRendererKind() {
  return rendererKind;
}

export function setCanvasMountOptions(options) {
  mountOptions = options;
}

export function configuredRendererKind() {
  return (
    state.modelingConfig.config?.diagramEditor?.renderer ||
    window.modlessFrontendBoot?.renderer ||
    "antv-g6"
  );
}

export async function initializeRendererAdapter(kind = configuredRendererKind()) {
  rendererKind = kind === "glsp-sprotty" ? "glsp-sprotty" : "antv-g6";
  if (rendererKind !== "glsp-sprotty" || glspRenderer) {
    return;
  }
  const mod = await import("./glsp-renderer.js");
  glspRenderer = mod.createGlspRenderer?.() ?? null;
  if (!glspRenderer) {
    throw new Error("GLSP renderer factory is unavailable");
  }
}

export function applyActiveRendererChrome(targetEl, kind = rendererKind) {
  const isGlsp = kind === "glsp-sprotty";
  const resolvedKind = isGlsp ? "glsp-sprotty" : "antv-g6";
  document.documentElement.dataset.renderer = resolvedKind;
  targetEl.canvasGrid?.setAttribute("data-renderer", resolvedKind);
  targetEl.canvasGrid?.classList.toggle("glsp-renderer-active", isGlsp);
  targetEl.canvasGrid?.classList.toggle("g6-renderer-active", !isGlsp);
  if (isGlsp) {
    targetEl.canvasGrid?.classList.remove("g6-renderer-unavailable");
  } else {
    targetEl.canvasGrid?.classList.remove("glsp-renderer-unavailable");
  }
}

function glspMountArgs() {
  const diagramEditor = state.modelingConfig.config?.diagramEditor || {};
  const baseCallbacks = mountOptions?.callbacks || {};
  return {
    ...(mountOptions || {}),
    glspServerUrl: diagramEditor.glspServerUrl || "ws://127.0.0.1:8081/modless",
    level: state.activeType,
    modelId: state.modelId,
    viewId: state.views?.activeViewId || "",
    authToken: getAuthToken(),
    diagram: state.diagram,
    levelConfig: state.modelingConfig.config?.levels?.[state.activeType] || {},
    validationIssues: state.validation?.issues || [],
    impactState: buildImpactOverlayState(state),
    callbacks: {
      ...baseCallbacks,
      resolveSelectionKind: (elementId) => {
        const id = String(elementId || "");
        const connections = state.diagram?.connections || [];
        if (connections.some((edge) => edge.id === id)) {
          return "edge";
        }
        return "node";
      },
    },
  };
}

function buildImpactOverlayState(state) {
  if (!state.impactMode || !state.impactData) {
    return { highlightedIds: [], severity: "info" };
  }
  const highlighted = new Set();
  if (state.impactData.focalElement?.elementId) {
    highlighted.add(state.impactData.focalElement.elementId);
  }
  for (const bucket of ["upstream", "downstream", "connectedElements"]) {
    for (const item of state.impactData[bucket] || []) {
      if (item?.elementId) {
        highlighted.add(item.elementId);
      }
    }
  }
  return { highlightedIds: [...highlighted], severity: "warning" };
}

async function mountGlspRenderer(el) {
  applyActiveRendererChrome(el, "glsp-sprotty");
  el.g6EditorHost?.classList.remove("is-mounted");

  try {
    const args = glspMountArgs();
    await glspRenderer.mount(el.glspEditorHost, args);
    el.glspEditorHost?.classList.add("is-mounted", "is-connected");
    applyActiveRendererChrome(el, "glsp-sprotty");
    if (window.modlessGlspState?.mode === "websocket") {
      bindSprottyHostFocus(el.glspEditorHost);
    } else {
      await glspRenderer.syncFromState?.({ ...args, full: true });
      glspRenderer.fitCanvasToDiagram?.(null, { fit: true });
    }
    return true;
  } catch (error) {
    console.error("GLSP renderer mount failed", error);
    el.canvasGrid?.setAttribute("data-renderer", "glsp-sprotty-unavailable");
    document.documentElement.dataset.renderer = "glsp-sprotty-unavailable";
    const { setError } = await import("../status.js");
    setError(`GLSP renderer failed (${error?.message || "mount error"}).`);
    return true;
  }
}

export function isCanvasRendererAvailable() {
  return rendererKind === "glsp-sprotty"
    ? Boolean(glspRenderer?.isAvailable?.())
    : g6.isCanvasRendererAvailable();
}

export function getCanvasEditor() {
  return rendererKind === "glsp-sprotty" ? glspRenderer?.getEditor?.() : g6.getCanvasEditor();
}

function glspShouldUseWebsocket(args = glspMountArgs()) {
  return (
    Boolean(String(args.authToken || "").trim()) &&
    Boolean(String(args.modelId || "").trim()) &&
    window.modlessFrontendBoot?.glspLocalOnly !== true
  );
}

export async function ensureCanvas() {
  await initializeRendererAdapter(configuredRendererKind());
  const { el } = await import("../dom.js");

  if (rendererKind === "glsp-sprotty") {
    applyActiveRendererChrome(el, "glsp-sprotty");
    const args = glspMountArgs();
    const wantsWebsocket = glspShouldUseWebsocket(args);
    const onWebsocket = window.modlessGlspState?.mode === "websocket";
    const wsUnavailable = window.modlessGlspState?.wsUnavailableFor === glspSessionKey(args);

    if (wantsWebsocket && !onWebsocket && !wsUnavailable && glspRenderer?.getEditor?.()) {
      await glspRenderer.unmount?.();
    }

    if (glspRenderer?.getEditor?.()) {
      glspRenderer.updateMountOptions?.(args);
      try {
        await glspRenderer.syncFromState?.(args);
      } catch (error) {
        console.warn("[GLSP] Diagram sync failed", error);
      }
      return true;
    }
    return mountGlspRenderer(el);
  }

  const { setStatus } = await import("../status.js");

  applyActiveRendererChrome(el, "antv-g6");
  el.glspEditorHost?.classList.remove("is-mounted", "is-connected");
  if (g6.getCanvasEditor()) {
    g6.syncCanvasFromState({ full: false });
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
    applyActiveRendererChrome(el, "antv-g6");
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

export async function syncCanvasFromState(options = {}) {
  if (rendererKind === "glsp-sprotty") {
    if (!glspRenderer?.getEditor?.()) {
      await ensureCanvas();
    }
    try {
      await glspRenderer?.syncFromState?.({ ...glspMountArgs(), ...options });
    } catch (error) {
      console.warn("[GLSP] syncCanvasFromState failed", error);
    }
    return;
  }
  g6.syncCanvasFromState(options);
}

export const renderCanvasDiagram = (...args) =>
  rendererKind === "glsp-sprotty"
    ? glspRenderer?.renderDiagram?.(...args)
    : g6.renderCanvasDiagram(...args);
export const updateCanvasSelection = (...args) =>
  rendererKind === "glsp-sprotty"
    ? glspRenderer?.updateSelection?.(...args)
    : g6.updateCanvasSelection(...args);
export const updateCanvasViewport = (...args) =>
  rendererKind === "glsp-sprotty"
    ? glspRenderer?.updateViewport?.(...args)
    : g6.updateCanvasViewport(...args);
export const updateCanvasConnectionState = (...args) =>
  rendererKind === "glsp-sprotty"
    ? glspRenderer?.updateConnectionState?.(...args)
    : g6.updateCanvasConnectionState(...args);
export const updateCanvasContextBoxes = (...args) =>
  rendererKind === "glsp-sprotty"
    ? glspRenderer?.updateContextBoxes?.(...args)
    : g6.updateCanvasContextBoxes(...args);
export const updateCanvasImpactState = (...args) =>
  rendererKind === "glsp-sprotty"
    ? glspRenderer?.updateImpactState?.(...args)
    : g6.updateCanvasImpactState(...args);
export const updateCanvasNode = (...args) =>
  rendererKind === "glsp-sprotty"
    ? glspRenderer?.updateNode?.(...args)
    : g6.updateCanvasNode(...args);
export const updateCanvasEdge = (...args) =>
  rendererKind === "glsp-sprotty"
    ? glspRenderer?.updateEdge?.(...args)
    : g6.updateCanvasEdge(...args);
export const updateCanvasNodeIcons = (...args) =>
  rendererKind === "glsp-sprotty"
    ? glspRenderer?.updateNodeIcons?.(...args)
    : g6.updateCanvasNodeIcons(...args);
export const refreshCanvasEdges = (...args) =>
  rendererKind === "glsp-sprotty"
    ? glspRenderer?.refreshEdges?.(...args)
    : g6.refreshCanvasEdges(...args);
export const resetCanvasView = (...args) =>
  rendererKind === "glsp-sprotty"
    ? glspRenderer?.resetCanvasView?.(...args)
    : g6.resetCanvasView(...args);
export const zoomCanvasBy = (...args) =>
  rendererKind === "glsp-sprotty"
    ? glspRenderer?.zoomCanvasBy?.(...args)
    : g6.zoomCanvasBy(...args);
export const fitCanvasToDiagram = (...args) =>
  rendererKind === "glsp-sprotty"
    ? glspRenderer?.fitCanvasToDiagram?.(...args)
    : g6.fitCanvasToDiagram(...args);
export const focusCanvasNode = (...args) =>
  rendererKind === "glsp-sprotty"
    ? glspRenderer?.focusNode?.(...args)
    : g6.focusCanvasNode(...args);
export const focusCanvasPoint = (...args) =>
  rendererKind === "glsp-sprotty"
    ? glspRenderer?.focusCanvasPoint?.(...args)
    : g6.focusCanvasPoint(...args);
export const onCanvasViewportChanged = (...args) =>
  rendererKind === "glsp-sprotty" ? undefined : g6.onCanvasViewportChanged(...args);
export const beginCanvasInlineLabelEdit = (...args) =>
  rendererKind === "glsp-sprotty"
    ? glspRenderer?.beginInlineLabelEdit?.(...args)
    : g6.beginCanvasInlineLabelEdit(...args);
export const addCanvasNode = (...args) =>
  rendererKind === "glsp-sprotty" ? glspRenderer?.addNode?.(...args) : g6.addCanvasNode(...args);
export const addCanvasEdge = (...args) =>
  rendererKind === "glsp-sprotty" ? glspRenderer?.addEdge?.(...args) : g6.addCanvasEdge(...args);
export const setCanvasHoverNode = (...args) =>
  rendererKind === "glsp-sprotty"
    ? glspRenderer?.setHoverNode?.(...args)
    : g6.setCanvasHoverNode(...args);
export const setCanvasHoverEdge = (...args) =>
  rendererKind === "glsp-sprotty"
    ? glspRenderer?.setHoverEdge?.(...args)
    : g6.setCanvasHoverEdge(...args);
export const undoCanvasEdit = () =>
  rendererKind === "glsp-sprotty" ? glspRenderer?.undo?.() : undefined;
export const redoCanvasEdit = () =>
  rendererKind === "glsp-sprotty" ? glspRenderer?.redo?.() : undefined;
export const applyGlspElkLayout = () =>
  rendererKind === "glsp-sprotty" ? glspRenderer?.applyElkLayout?.() : undefined;
export function toGraphCoordinates(clientX, clientY) {
  return rendererKind === "glsp-sprotty"
    ? glspToGraphCoordinates(clientX, clientY)
    : g6.toGraphCoordinates(clientX, clientY);
}

export function createRenderer(kind) {
  rendererKind = kind === "glsp-sprotty" ? "glsp-sprotty" : "antv-g6";
  return rendererKind === "glsp-sprotty" ? glspRenderer : g6;
}
