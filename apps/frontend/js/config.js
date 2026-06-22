function normalizeBaseUrl(value) {
  if (!value) {
    return "";
  }
  return String(value).trim().replace(/\/+$/, "");
}

export const backendBaseUrl = normalizeBaseUrl(window.MODLESS_BACKEND_BASE_URL);
export const backendOrigin = backendBaseUrl || window.location.origin;
export const apiBase = backendBaseUrl ? `${backendBaseUrl}/api` : "/api";

export function apiUrl(path) {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${apiBase}${normalizedPath}`;
}

export function websocketUrl(path) {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  const backend = new URL(backendOrigin);
  const protocol = backend.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${backend.host}${normalizedPath}`;
}

export const CHAT_ATTACHMENT_MAX_BYTES = 300000;
export const LOG_HINT = "See backend logs (default path: logs/backend.log)";
export const MOBILE_BREAKPOINT = 920;
export const TABLET_BREAKPOINT = 1100;
export const COMPACT_BREAKPOINT = 760;
export const PHONE_BREAKPOINT = 560;
export const TOUCH_MOVE_THRESHOLD = 6;

export const MODEL_TYPES = {};

export function applyModelingRuntimeConfig(config) {
  Object.keys(MODEL_TYPES).forEach((key) => delete MODEL_TYPES[key]);
  const levels = config?.levels && typeof config.levels === "object" ? config.levels : {};
  const order = Array.isArray(config?.levelOrder) ? config.levelOrder : Object.keys(levels);
  order.forEach((key) => {
    const level = levels[key];
    if (!level || typeof level !== "object") {
      return;
    }
    MODEL_TYPES[key] = {
      apiType: String(level.apiType || key),
      chatType: String(level.chatType || key.toUpperCase()),
    };
  });
}
