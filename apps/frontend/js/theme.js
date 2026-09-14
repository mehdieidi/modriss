import { apiUrl } from "./config.js";
import { el } from "./dom.js";

// ── Theme management ───────────────────────────────────────────────────────────
const THEME_DARK = "dark";
const THEME_LIGHT = "light";
const THEME_DARK_ICON = "/assets/icons/dark_mode.svg";
const THEME_LIGHT_ICON = "/assets/icons/light_mode.svg";
const THEME_PROFILE_STORAGE = "modriss.activeThemeProfileId";
const THEME_SCHEME_STORAGE = "modriss.themeScheme";
const APP_FAVICON = "/assets/icons/logo/modriss-favicon.svg";

let activeProfileId = "";

function isLightTheme() {
  return document.documentElement.classList.contains(THEME_LIGHT);
}

function updateThemeToggleIcon() {
  if (!el.themeRailToggleIcon) {
    updateThemeFavicon();
    return;
  }
  const iconUrl = isLightTheme() ? THEME_DARK_ICON : THEME_LIGHT_ICON;
  el.themeRailToggleIcon.style.setProperty("--icon-src", `url('${iconUrl}')`);
  updateThemeFavicon();
}

function updateThemeFavicon() {
  const favicon = document.getElementById("app-favicon");
  favicon?.setAttribute("href", APP_FAVICON);
}

function setLightClass(light) {
  document.documentElement.classList.toggle(THEME_LIGHT, light);
}

function inferLightTheme(tokens = {}) {
  const bg = String(tokens["--bg"] || tokens["--surface"] || "").trim();
  const hex = bg.match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/i)?.[1];
  if (!hex) {
    return false;
  }
  const expanded =
    hex.length === 3
      ? hex
          .split("")
          .map((part) => part + part)
          .join("")
      : hex;
  const red = Number.parseInt(expanded.slice(0, 2), 16);
  const green = Number.parseInt(expanded.slice(2, 4), 16);
  const blue = Number.parseInt(expanded.slice(4, 6), 16);
  return (red * 299 + green * 587 + blue * 114) / 1000 > 150;
}

function applyThemeProfile(profile) {
  if (!profile?.tokens || typeof profile.tokens !== "object") {
    return false;
  }
  Object.entries(profile.tokens).forEach(([name, value]) => {
    if (name.startsWith("--") && typeof value === "string") {
      document.documentElement.style.setProperty(name, value);
    }
  });
  document.documentElement.style.setProperty("--text-muted", "var(--muted)");
  activeProfileId = profile.id || "";
  if (activeProfileId) {
    localStorage.setItem(THEME_PROFILE_STORAGE, activeProfileId);
  }

  const storedScheme = localStorage.getItem(THEME_SCHEME_STORAGE);
  setLightClass(storedScheme ? storedScheme === THEME_LIGHT : inferLightTheme(profile.tokens));
  updateThemeToggleIcon();
  document.documentElement.dispatchEvent(
    new CustomEvent("modriss:theme-change", { detail: { profile } }),
  );
  return true;
}

export async function loadActiveThemeProfile() {
  try {
    const scheme = isLightTheme() ? THEME_LIGHT : THEME_DARK;
    const storageKey = `${THEME_PROFILE_STORAGE}.${scheme}`;
    const previousProfileId = activeProfileId || localStorage.getItem(storageKey) || "";
    const response = await fetch(apiUrl(`/theme?scheme=${encodeURIComponent(scheme)}`), {
      headers: { Accept: "application/json" },
      cache: "no-store",
    });
    if (!response.ok) {
      throw new Error(`Theme request failed (${response.status})`);
    }
    const profile = await response.json();
    const applied = applyThemeProfile(profile);
    if (applied && profile.id !== previousProfileId) {
      localStorage.setItem(storageKey, profile.id);
    }
  } catch (error) {
    console.warn("Could not load active theme profile", error);
  }
}

export async function initTheme() {
  const storedScheme = localStorage.getItem(THEME_SCHEME_STORAGE);
  if (storedScheme === THEME_LIGHT || storedScheme === THEME_DARK) {
    setLightClass(storedScheme === THEME_LIGHT);
  } else {
    setLightClass(true);
  }
  updateThemeToggleIcon();
  await loadActiveThemeProfile();
}

export function toggleTheme() {
  const isLight = document.documentElement.classList.toggle(THEME_LIGHT);
  localStorage.setItem(THEME_SCHEME_STORAGE, isLight ? THEME_LIGHT : THEME_DARK);
  updateThemeToggleIcon();
  void loadActiveThemeProfile();
}
