import { el } from "./dom.js";

// ── Theme management ───────────────────────────────────────────────────────────
const THEME_DARK = "dark";
const THEME_LIGHT = "light";
const THEME_DARK_ICON = "/assets/icons/dark_mode.svg";
const THEME_LIGHT_ICON = "/assets/icons/light_mode.svg";

function isLightTheme() {
  return document.documentElement.classList.contains(THEME_LIGHT);
}

function updateThemeToggleIcon() {
  if (!el.themeRailToggleIcon) {
    return;
  }
  const iconUrl = isLightTheme() ? THEME_DARK_ICON : THEME_LIGHT_ICON;
  el.themeRailToggleIcon.style.setProperty("--icon-src", `url('${iconUrl}')`);
}

export function initTheme() {
  const stored = localStorage.getItem("theme");
  if (stored === THEME_LIGHT) {
    document.documentElement.classList.add(THEME_LIGHT);
  }
  updateThemeToggleIcon();
}

export function toggleTheme() {
  const isLight = document.documentElement.classList.toggle(THEME_LIGHT);
  localStorage.setItem("theme", isLight ? THEME_LIGHT : THEME_DARK);
  updateThemeToggleIcon();
  document.documentElement.dispatchEvent(new CustomEvent("varka:theme-change"));
}
