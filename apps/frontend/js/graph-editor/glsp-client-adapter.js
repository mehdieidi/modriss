/**
 * Bridges the Eclipse GLSP WebSocket client (vendor bundle) with the local SVG
 * fallback renderer. Prefers the real Sprotty client when a session is available.
 */

import { bindSprottyHostFocus } from "./glsp-sprotty-host.js";
import { state } from "../state.js";

let websocketRenderer = null;
let localRenderer = null;
let activeMode = "none";

export function glspSessionKey(options = {}) {
  return [
    options.level || "",
    options.modelId || "",
    options.viewId || "",
    options.glspServerUrl || "",
  ].join(":");
}

function markWebsocketUnavailable(options = {}, reason = "") {
  const key = glspSessionKey(options);
  window.modlessGlspState = {
    ...(window.modlessGlspState || {}),
    wsUnavailableFor: key,
    wsUnavailableReason: reason || "unknown",
  };
  if (reason) {
    console.warn("[GLSP] WebSocket diagram session disabled for this model:", reason);
  }
}

function isWebsocketUnavailableForSession(options = {}) {
  return window.modlessGlspState?.wsUnavailableFor === glspSessionKey(options);
}

async function abandonWebsocketRenderer() {
  if (!websocketRenderer) {
    return;
  }
  try {
    await websocketRenderer.unmount?.();
  } catch (error) {
    console.warn("[GLSP] WebSocket renderer cleanup failed", error);
  }
}

async function loadWebsocketRenderer() {
  if (websocketRenderer) {
    return websocketRenderer;
  }
  const mod = await import("/vendor/glsp/modless-glsp.js?v=glsp-20260624j");
  websocketRenderer = mod.createGlspRenderer?.() ?? null;
  if (!websocketRenderer) {
    throw new Error("GLSP vendor bundle did not export createGlspRenderer");
  }
  return websocketRenderer;
}

async function loadLocalRenderer() {
  if (localRenderer) {
    return localRenderer;
  }
  const mod = await import("./glsp-editor.js");
  localRenderer = mod.createGlspRenderer?.() ?? null;
  if (!localRenderer) {
    throw new Error("Local GLSP shim did not export createGlspRenderer");
  }
  return localRenderer;
}

function preferLocalRenderer(options = {}) {
  if (window.modlessFrontendBoot?.glspLocalOnly === true) {
    return true;
  }
  if (options.forceLocal === true) {
    return true;
  }
  if (isWebsocketUnavailableForSession(options)) {
    return true;
  }
  if (!String(options.authToken || "").trim()) {
    return true;
  }
  // Defer WebSocket GLSP until a persisted model is open (avoids empty-session remount loops).
  if (!String(options.modelId || "").trim()) {
    return true;
  }
  return false;
}

export function glspWantsWebsocketSession(options = {}) {
  return !preferLocalRenderer(options);
}

const PROXIED_METHODS = [
  "syncFromState",
  "renderDiagram",
  "updateSelection",
  "updateViewport",
  "updateConnectionState",
  "updateContextBoxes",
  "updateImpactState",
  "updateNode",
  "updateEdge",
  "updateNodeIcons",
  "refreshEdges",
  "resetCanvasView",
  "zoomCanvasBy",
  "fitCanvasToDiagram",
  "focusNode",
  "focusCanvasPoint",
  "onViewportChanged",
  "beginInlineLabelEdit",
  "addNode",
  "addEdge",
  "setHoverNode",
  "setHoverEdge",
  "applyElkLayout",
  "undo",
  "redo",
  "canUndo",
  "canRedo",
  "unmount",
];

export class GlspClientAdapter {
  #delegate = null;
  #mountOptions = {};

  #callDelegate(name, args) {
    const delegate = this.#delegate;
    if (!delegate || typeof delegate[name] !== "function") {
      return undefined;
    }
    return delegate[name](...args);
  }

  constructor() {
    this.isAvailable = () => this.#delegate?.isAvailable?.() ?? true;
    this.getEditor = () => (this.#delegate?.getEditor?.() ? this : null);
    for (const name of PROXIED_METHODS) {
      this[name] = (...args) => this.#callDelegate(name, args);
    }
  }

  get activeMode() {
    return activeMode;
  }

  async mount(host, options = {}) {
    this.#mountOptions = { ...options };

    if (preferLocalRenderer(options)) {
      return this.#mountLocal(host, options);
    }

    try {
      const renderer = await loadWebsocketRenderer();
      const connected = await renderer.mount(host, options);
      const editor = renderer.getEditor?.();
      if (connected && editor) {
        this.#delegate = renderer;
        activeMode = "websocket";
        window.modlessGlspState = {
          ...(window.modlessGlspState || {}),
          mode: "websocket",
          connected: true,
          wsUnavailableFor: "",
          wsUnavailableReason: "",
        };
        bindSprottyHostFocus(host);
        return true;
      }
      const statusText = host.querySelector(".glsp-canvas-status")?.textContent?.trim() || "";
      const reason = connected
        ? "GLSP client mounted without an active editor"
        : statusText || "GLSP server did not return a diagram session";
      markWebsocketUnavailable(options, reason);
      await abandonWebsocketRenderer();
      console.warn("[GLSP] WebSocket session unavailable; using local SVG renderer.", reason);
    } catch (error) {
      const reason = error instanceof Error ? error.message : String(error);
      markWebsocketUnavailable(options, reason);
      await abandonWebsocketRenderer();
      console.warn("[GLSP] WebSocket client failed; using local SVG renderer.", error);
    }

    return this.#mountLocal(host, options);
  }

  async #mountLocal(host, options) {
    host.querySelector(".glsp-canvas-status")?.remove();
    host.classList.remove("glsp-idle", "glsp-diagram-host");
    const renderer = await loadLocalRenderer();
    await renderer.mount(host, options);
    this.#delegate = renderer;
    activeMode = "local";
    window.modlessGlspState = {
      ...(window.modlessGlspState || {}),
      mode: "local",
      connected: true,
    };
    await renderer.syncFromState?.({ ...options, full: true });
    if (state.diagram?.nodes?.length) {
      renderer.fitCanvasToDiagram?.(null, { fit: true });
    }
    return true;
  }

  updateMountOptions(options = {}) {
    this.#mountOptions = { ...this.#mountOptions, ...options };
    this.#delegate?.updateMountOptions?.(this.#mountOptions);
  }

  async unmount() {
    try {
      await this.#delegate?.unmount?.();
    } catch (error) {
      console.warn("[GLSP] Renderer unmount failed", error);
    }
    if (this.#delegate === websocketRenderer) {
      await abandonWebsocketRenderer();
    }
    this.#delegate = null;
    activeMode = "none";
    window.modlessGlspState = {
      ...(window.modlessGlspState || {}),
      mode: "none",
      connected: false,
    };
  }
}

export function createGlspRenderer() {
  return new GlspClientAdapter();
}

export function createRenderer(kind) {
  if (kind === "glsp-sprotty") {
    return createGlspRenderer();
  }
  return null;
}

export const MODLESS_DIAGRAM_TYPE = "modless-diagram";
