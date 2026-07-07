export function isLightTheme() {
  return document.documentElement?.classList.contains("light");
}

function parseHex(hex) {
  const normalized = String(hex || "").trim();
  const match = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(normalized);
  if (!match) {
    return null;
  }
  return {
    r: Number.parseInt(match[1], 16),
    g: Number.parseInt(match[2], 16),
    b: Number.parseInt(match[3], 16),
  };
}

function toHex({ r, g, b }) {
  const channel = (value) =>
    Math.max(0, Math.min(255, Math.round(value)))
      .toString(16)
      .padStart(2, "0");
  return `#${channel(r)}${channel(g)}${channel(b)}`;
}

function mixTowardWhite(hex, amount) {
  const rgb = parseHex(hex);
  if (!rgb) {
    return hex;
  }
  const ratio = Math.max(0, Math.min(1, amount));
  return toHex({
    r: rgb.r + (255 - rgb.r) * ratio,
    g: rgb.g + (255 - rgb.g) * ratio,
    b: rgb.b + (255 - rgb.b) * ratio,
  });
}

function relativeLuminance(hex) {
  const rgb = parseHex(hex);
  if (!rgb) {
    return 0.5;
  }
  const channel = (value) => {
    const normalized = value / 255;
    return normalized <= 0.03928 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4;
  };
  const rs = channel(rgb.r);
  const gs = channel(rgb.g);
  const bs = channel(rgb.b);
  return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs;
}

export function normalizeThemeColor(value, fallback = "") {
  if (value && typeof value === "object") {
    const light = String(value.light || value.dark || fallback || "").trim();
    const dark = String(value.dark || value.light || fallback || "").trim();
    return { light: light || dark, dark: dark || light };
  }
  const text = String(value || fallback || "").trim();
  return { light: text, dark: text };
}

export function themeColorPairFromHex(hex) {
  const normalized = String(hex || "").trim();
  if (!normalized) {
    return { light: "", dark: "" };
  }
  const lum = relativeLuminance(normalized);
  if (lum < 0.12) {
    return { light: mixTowardWhite(normalized, 0.35), dark: normalized };
  }
  if (lum > 0.62) {
    return { light: normalized, dark: mixTowardWhite(normalized, 0.15) };
  }
  return {
    light: normalized,
    dark: mixTowardWhite(normalized, 0.42),
  };
}

export function resolveThemeColor(value, fallback = "") {
  const pair = normalizeThemeColor(value, fallback);
  const resolved = isLightTheme() ? pair.light : pair.dark;
  return String(resolved || pair.light || pair.dark || fallback || "").trim();
}

export function isFullColorIconSource(src) {
  const normalized = String(src || "")
    .trim()
    .toLowerCase();
  return /(?:^|\/)aws-[^/]+\.svg(?:[?#].*)?$/i.test(normalized) || /^aws-/i.test(normalized);
}
