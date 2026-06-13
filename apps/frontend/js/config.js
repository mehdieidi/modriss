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

export const DEFAULT_AWS_ACCOUNT_ID = "000000000000";
export const DEFAULT_AWS_REGION = "us-east-1";
export const CHAT_ATTACHMENT_MAX_BYTES = 300000;
export const LOG_HINT = "See backend logs (default path: logs/backend.log)";
export const MOBILE_BREAKPOINT = 768;
export const TOUCH_MOVE_THRESHOLD = 6;

export const MODEL_TYPES = {
  cim: {
    apiType: "cim",
    chatType: "CIM",
  },
  pim: {
    apiType: "pim",
    chatType: "PIM",
  },
  psm: {
    apiType: "psm",
    chatType: "PSM",
  },
};
