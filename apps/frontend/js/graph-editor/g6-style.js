import { state } from "../state.js";
import { modelingElementDefinition, modelingLevelConfig } from "../modeling-config-data.js";
import { measureIconNodeSize } from "./icon-node-metrics.js";

export const MODLESS_NODE_TYPE = "modless-node";
export const MODLESS_EDGE_TYPE = "modless-edge";
export const G6_BASE_NODE_TYPE = MODLESS_NODE_TYPE;
export const G6_BASE_EDGE_TYPE = MODLESS_EDGE_TYPE;
export const NODE_SIZE = {
  default: { width: 120, height: 118 },
};

let cssVarCacheKey = "";
const cssVarCache = new Map();

function currentCssVarCacheKey() {
  const root = document.documentElement;
  return `${root?.className || ""}|${root?.getAttribute?.("style") || ""}`;
}

export function nodeSizeForDiagram(typeKey = state.activeType, nodeOrType = null) {
  const type = typeof nodeOrType === "string" ? nodeOrType : nodeOrType?.type;
  const label =
    nodeOrType && typeof nodeOrType === "object" ? nodeOrType.label || nodeOrType.id || "" : "";
  const low = nodeOrType?.meta?.detailLevel === "low";
  try {
    const definition = type ? modelingElementDefinition(typeKey, type) : null;
    const configured = definition?.notation?.size;
    const policy = modelingLevelConfig(typeKey).canvasPolicy || {};
    const fallback = policy.roleSizes?.node || NODE_SIZE.default;
    const width = Math.max(
      48,
      Number(configured?.width || fallback.width || NODE_SIZE.default.width),
    );
    const measured = label
      ? measureIconNodeSize(label, { width, low })
      : { height: Number(configured?.height || fallback.height || NODE_SIZE.default.height) };
    return {
      width,
      height: Math.max(40, Number(measured.height || fallback.height || NODE_SIZE.default.height)),
    };
  } catch {
    if (label) {
      return measureIconNodeSize(label, { width: NODE_SIZE.default.width, low });
    }
    return NODE_SIZE.default;
  }
}

export function cssVar(name, fallback = "") {
  const nextCacheKey = currentCssVarCacheKey();
  if (nextCacheKey !== cssVarCacheKey) {
    cssVarCacheKey = nextCacheKey;
    cssVarCache.clear();
  }
  const cacheKey = `${name}\u0000${fallback}`;
  if (cssVarCache.has(cacheKey)) {
    return cssVarCache.get(cacheKey);
  }
  const root = document.documentElement;
  const value = root ? getComputedStyle(root).getPropertyValue(name).trim() : "";
  const resolved = value || fallback;
  cssVarCache.set(cacheKey, resolved);
  return resolved;
}

export function normalizeColor(value, fallback) {
  const text = String(value || "").trim();
  return text || fallback;
}

export function nodeAccent(node, definition = null) {
  const ui = definition?.ui && typeof definition.ui === "object" ? definition.ui : {};
  return normalizeColor(ui.color || definition?.color, cssVar("--accent", "#00a6e0"));
}

export function stickyColor(node, definition = null) {
  return normalizeColor(
    definition?.notation?.fill || definition?.color,
    cssVar("--node-warm", "#fde68a"),
  );
}

export function edgeStyleForKind(kind, presentation = {}) {
  const base = {
    stroke: cssVar("--accent", "#00a6e0"),
    lineWidth: 1.7,
    opacity: 0.9,
    lineDash: undefined,
  };
  return { ...base, ...(presentation.style || {}) };
}

export function isLightTheme() {
  return document.documentElement?.classList.contains("light");
}

export function canvasBackgroundColor() {
  return cssVar("--canvas-bg", "#0e1117");
}
