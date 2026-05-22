import {el} from './dom.js';

// ── Theme management ───────────────────────────────────────────────────────────
const CUSTOM_THEME_COLORS_KEY = "theme-custom-colors";
const TOPBAR_VAR = "--topbar-custom-bg";
const CANVAS_VAR = "--canvas-custom-bg";
const CHAT_VAR = "--chat-custom-bg";
const THEME_DARK = "dark";
const THEME_LIGHT = "light";
const THEME_DARK_ICON = "/assets/icons/dark_mode.svg";
const THEME_LIGHT_ICON = "/assets/icons/light_mode.svg";

function normalizeHexColor(color, fallback = "#000000") {
  if (!color) {
    return fallback;
  }
  const value = String(color).trim();
  const hex6 = /^#([0-9a-f]{6})$/i.exec(value);
  if (hex6) {
    return `#${hex6[1].toLowerCase()}`;
  }
  const hex3 = /^#([0-9a-f]{3})$/i.exec(value);
  if (hex3) {
    const [r, g, b] = hex3[1].toLowerCase().split("");
    return `#${r}${r}${g}${g}${b}${b}`;
  }
  const rgb = /^rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})/i.exec(
      value);
  if (rgb) {
    const toHex = (n) => Math.max(0, Math.min(255, Number(n))).toString(
        16).padStart(2, "0");
    return `#${toHex(rgb[1])}${toHex(rgb[2])}${toHex(rgb[3])}`;
  }
  return fallback;
}

function isLightTheme() {
  return document.documentElement.classList.contains(THEME_LIGHT);
}

function currentThemeKey() {
  return isLightTheme() ? THEME_LIGHT : THEME_DARK;
}

function updateThemeToggleIcon() {
  if (!el.themeRailToggleIcon) {
    return;
  }
  const iconUrl = isLightTheme() ? THEME_DARK_ICON : THEME_LIGHT_ICON;
  el.themeRailToggleIcon.style.setProperty("--icon-src", `url('${iconUrl}')`);
}

function normalizeColors(colors = {}) {
  return {
    topbar: colors.topbar ? normalizeHexColor(colors.topbar) : "",
    canvas: colors.canvas ? normalizeHexColor(colors.canvas) : "",
    chat: colors.chat ? normalizeHexColor(colors.chat) : ""
  };
}

function clearAppliedThemeColors() {
  const root = document.documentElement;
  root.style.removeProperty(TOPBAR_VAR);
  root.style.removeProperty(CANVAS_VAR);
  root.style.removeProperty(CHAT_VAR);
}

function readThemeColorStore() {
  try {
    const parsed = JSON.parse(
        localStorage.getItem(CUSTOM_THEME_COLORS_KEY) || "{}");
    if (typeof parsed !== "object" || parsed === null) {
      return null;
    }
    // v2 store: { dark: {..}, light: {..} }
    if ((typeof parsed.dark === "object" && parsed.dark !== null)
        || (typeof parsed.light === "object" && parsed.light !== null)) {
      return {
        dark: normalizeColors(parsed.dark || {}),
        light: normalizeColors(parsed.light || {})
      };
    }
    // Legacy flat store: treat as dark-only to avoid corrupting light mode.
    return {
      dark: normalizeColors(parsed),
      light: normalizeColors({})
    };
  } catch {
    // Ignore malformed persisted data and fall back to defaults.
    return null;
  }
}

function writeThemeColorStore(store) {
  localStorage.setItem(CUSTOM_THEME_COLORS_KEY, JSON.stringify(store));
}

function readThemeColorsForCurrentTheme() {
  const store = readThemeColorStore();
  if (!store) {
    return normalizeColors({});
  }
  const key = currentThemeKey();
  return normalizeColors(store[key] || {});
}

function applyThemeColorsToRoot(colors = {}) {
  const root = document.documentElement;
  clearAppliedThemeColors();
  if (colors.topbar) {
    root.style.setProperty(TOPBAR_VAR, normalizeHexColor(colors.topbar));
  }
  if (colors.canvas) {
    root.style.setProperty(CANVAS_VAR, normalizeHexColor(colors.canvas));
  }
  if (colors.chat) {
    root.style.setProperty(CHAT_VAR, normalizeHexColor(colors.chat));
  }
}

export function applyThemeColors(colors = {}) {
  const normalized = normalizeColors(colors);
  applyThemeColorsToRoot(normalized);
  const store = readThemeColorStore() || {
    dark: normalizeColors({}),
    light: normalizeColors({})
  };
  store[currentThemeKey()] = normalized;
  writeThemeColorStore(store);
}

export function resetThemeColors() {
  clearAppliedThemeColors();
  const store = readThemeColorStore();
  if (!store) {
    localStorage.removeItem(CUSTOM_THEME_COLORS_KEY);
    return;
  }
  store[currentThemeKey()] = normalizeColors({});
  const hasDark = Object.values(store.dark || {}).some(Boolean);
  const hasLight = Object.values(store.light || {}).some(Boolean);
  if (!hasDark && !hasLight) {
    localStorage.removeItem(CUSTOM_THEME_COLORS_KEY);
    return;
  }
  writeThemeColorStore(store);
}

export function getThemeColorDefaults() {
  const style = getComputedStyle(document.documentElement);
  return {
    topbar: normalizeHexColor(
        style.getPropertyValue(TOPBAR_VAR).trim() || style.getPropertyValue(
            "--panel").trim(), "#111827"),
    canvas: normalizeHexColor(
        style.getPropertyValue(CANVAS_VAR).trim() || style.getPropertyValue(
            "--canvas-bg").trim(), "#0b1120"),
    chat: normalizeHexColor(
        style.getPropertyValue(CHAT_VAR).trim() || style.getPropertyValue(
            "--panel").trim(), "#111827")
  };
}

export function initTheme() {
  const stored = localStorage.getItem("theme");
  if (stored === THEME_LIGHT) {
    document.documentElement.classList.add(THEME_LIGHT);
  }
  const customColors = readThemeColorsForCurrentTheme();
  applyThemeColorsToRoot(customColors);
  updateThemeToggleIcon();
}

export function toggleTheme() {
  const isLight = document.documentElement.classList.toggle(THEME_LIGHT);
  localStorage.setItem("theme", isLight ? THEME_LIGHT : THEME_DARK);
  const customColors = readThemeColorsForCurrentTheme();
  applyThemeColorsToRoot(customColors);
  updateThemeToggleIcon();
}
