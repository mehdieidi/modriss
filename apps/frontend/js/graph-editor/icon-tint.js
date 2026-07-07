import { isFullColorIconSource } from "../theme-colors.js";

const svgTextCache = new Map();
const tintedUrlCache = new Map();
const pendingTintPromises = new Map();
const tintListeners = new Set();

function notifyTintListeners() {
  tintListeners.forEach((listener) => {
    try {
      listener();
    } catch {
      // Ignore listener failures during icon tint refresh.
    }
  });
}

export function onIconTintsUpdated(listener) {
  tintListeners.add(listener);
  return () => tintListeners.delete(listener);
}

function cacheKey(src, color) {
  return `${src}\u0000${color}`;
}

export function tintSvgMarkup(svgText, color) {
  const doc = new DOMParser().parseFromString(svgText, "image/svg+xml");
  const svg = doc.documentElement;
  if (svg.querySelector("parsererror")) {
    return svgText;
  }
  svg.querySelectorAll("path, circle, rect, polygon, ellipse, line, polyline").forEach((shape) => {
    const stroke = shape.getAttribute("stroke");
    const fill = shape.getAttribute("fill");
    if (fill !== "none" && fill !== "transparent") {
      shape.setAttribute("fill", color);
    }
    if (stroke && stroke !== "none" && stroke !== "transparent") {
      shape.setAttribute("stroke", color);
    }
    if (!fill && !stroke) {
      shape.setAttribute("fill", color);
    }
  });
  return new XMLSerializer().serializeToString(svg);
}

async function fetchSvgText(src) {
  if (svgTextCache.has(src)) {
    return svgTextCache.get(src);
  }
  const response = await fetch(src, { cache: "force-cache" });
  if (!response.ok) {
    throw new Error(`Failed to load icon: ${src}`);
  }
  const text = await response.text();
  svgTextCache.set(src, text);
  return text;
}

export function getTintedIconUrlSync(src, color) {
  if (!src || !color || isFullColorIconSource(src)) {
    return src || "";
  }
  return tintedUrlCache.get(cacheKey(src, color)) || null;
}

export async function ensureTintedIconUrl(src, color) {
  if (!src || !color || isFullColorIconSource(src)) {
    return src || "";
  }
  const key = cacheKey(src, color);
  if (tintedUrlCache.has(key)) {
    return tintedUrlCache.get(key);
  }
  if (!pendingTintPromises.has(key)) {
    pendingTintPromises.set(
      key,
      (async () => {
        try {
          const svgText = await fetchSvgText(src);
          const tinted = tintSvgMarkup(svgText, color);
          const url = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(tinted)}`;
          tintedUrlCache.set(key, url);
          notifyTintListeners();
          return url;
        } finally {
          pendingTintPromises.delete(key);
        }
      })(),
    );
  }
  return pendingTintPromises.get(key);
}

export async function primeIconTints(pairs = []) {
  const unique = new Map();
  pairs.forEach(({ src, color }) => {
    if (!src || !color || isFullColorIconSource(src)) {
      return;
    }
    unique.set(cacheKey(src, color), { src, color });
  });
  await Promise.all([...unique.values()].map(({ src, color }) => ensureTintedIconUrl(src, color)));
}

export function applyTintedIconsToNodes(nodes = []) {
  nodes.forEach((node) => {
    const style = node?.style;
    if (!style?.iconSrc || !style?.accent) {
      return;
    }
    const tinted = getTintedIconUrlSync(style.iconSrc, style.accent);
    if (tinted) {
      style.iconSrc = tinted;
    }
  });
}
