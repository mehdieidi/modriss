import { state } from "../state.js";
import { resolveThemeColor } from "../theme-colors.js";
import {
  modelingElementDefinition,
  modelingLevelConfig,
  modelingRoleSize,
} from "../modeling-config-data.js";
import { measureIconNodeSize } from "./icon-node-metrics.js";

export const MODRISS_NODE_TYPE = "modriss-node";
export const MODRISS_EDGE_TYPE = "modriss-edge";
export const G6_BASE_NODE_TYPE = MODRISS_NODE_TYPE;
export const G6_BASE_EDGE_TYPE = MODRISS_EDGE_TYPE;

function defaultNodeSize(typeKey) {
  const node = modelingRoleSize(typeKey, "node");
  return {
    width: Number(node?.width || 0),
    height: Number(node?.height || 0),
  };
}

function explicitNodeSize(nodeOrType) {
  if (!nodeOrType || typeof nodeOrType !== "object") {
    return null;
  }
  const width = Number(nodeOrType.width);
  const height = Number(nodeOrType.height);
  if (!Number.isFinite(width) || width <= 0 || !Number.isFinite(height) || height <= 0) {
    return null;
  }
  return { width, height };
}

let cssVarCacheKey = "";
const cssVarCache = new Map();

function currentCssVarCacheKey() {
  const root = document.documentElement;
  return `${root?.className || ""}|${root?.getAttribute?.("style") || ""}`;
}

export function nodeSizeForDiagram(typeKey = state.activeType, nodeOrType = null) {
  const explicit = explicitNodeSize(nodeOrType);
  if (explicit) {
    return explicit;
  }
  const type = typeof nodeOrType === "string" ? nodeOrType : nodeOrType?.type;
  const label =
    nodeOrType && typeof nodeOrType === "object" ? nodeOrType.label || nodeOrType.id || "" : "";
  const low = nodeOrType?.meta?.detailLevel === "low";
  try {
    const definition = type ? modelingElementDefinition(typeKey, type) : null;
    const configured = definition?.notation?.size;
    const policy = modelingLevelConfig(typeKey).canvasPolicy || {};
    const fallback = policy.roleSizes?.node || defaultNodeSize(typeKey);
    const width = Math.max(
      48,
      Number(configured?.width || fallback.width || defaultNodeSize(typeKey).width),
    );
    const measured = label
      ? measureIconNodeSize(label, { width, low })
      : {
          height: Number(configured?.height || fallback.height || defaultNodeSize(typeKey).height),
        };
    return {
      width,
      height: Math.max(
        40,
        Number(measured.height || fallback.height || defaultNodeSize(typeKey).height),
      ),
    };
  } catch {
    if (label) {
      return measureIconNodeSize(label, { width: defaultNodeSize(typeKey).width, low });
    }
    return defaultNodeSize(typeKey);
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
  return resolveThemeColor(ui.color || definition?.color, cssVar("--accent", "#00a6e0"));
}

export function stickyColor(node, definition = null) {
  return resolveThemeColor(
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
  const merged = { ...base, ...(presentation.style || {}) };
  if (merged.stroke) {
    merged.stroke = resolveThemeColor(merged.stroke, merged.stroke);
  }
  return merged;
}

export function canvasBackgroundColor() {
  return cssVar("--canvas-bg", "#0e1117");
}
