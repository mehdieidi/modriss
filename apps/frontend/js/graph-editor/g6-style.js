import { state } from "../state.js";

export const MODLESS_NODE_TYPE = "modless-node";
export const MODLESS_EDGE_TYPE = "modless-edge";
export const G6_BASE_NODE_TYPE = MODLESS_NODE_TYPE;
export const G6_BASE_EDGE_TYPE = MODLESS_EDGE_TYPE;
export const NODE_SIZE = {
  default: { width: 228, height: 112 },
  cim: { width: 176, height: 96 },
};

let cssVarCacheKey = "";
const cssVarCache = new Map();

function currentCssVarCacheKey() {
  const root = document.documentElement;
  return `${root?.className || ""}|${root?.getAttribute?.("style") || ""}`;
}

export function nodeSizeForDiagram(typeKey = state.activeType) {
  return typeKey === "cim" ? NODE_SIZE.cim : NODE_SIZE.default;
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
