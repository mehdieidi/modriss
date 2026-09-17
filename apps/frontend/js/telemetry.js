import { apiUrl } from "./config.js";

const APP_NAME = "frontend";
const MAX_MESSAGE = 300;

function telemetryEnabled() {
  if (typeof window.MODRISS_FRONTEND_TELEMETRY_ENABLED === "boolean") {
    return window.MODRISS_FRONTEND_TELEMETRY_ENABLED;
  }
  const localHosts = new Set(["localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]"]);
  if (localHosts.has(window.location.hostname)) {
    return false;
  }
  try {
    const backendHost = new URL(window.MODRISS_BACKEND_BASE_URL || window.location.href).hostname;
    return !localHosts.has(backendHost);
  } catch {
    return true;
  }
}

function truncate(value, max = MAX_MESSAGE) {
  const text = value == null ? "" : String(value);
  return text.length <= max ? text : text.slice(0, max);
}

function sendTelemetry(kind, payload = {}) {
  if (!telemetryEnabled()) {
    return;
  }
  const body = JSON.stringify({
    app: APP_NAME,
    kind,
    message: truncate(payload.message),
    url: truncate(window.location.href),
    durationMs: Number.isFinite(payload.durationMs) ? Math.round(payload.durationMs) : null,
    attributes: payload.attributes || {},
    occurredAt: new Date().toISOString(),
  });
  const endpoint = apiUrl("/telemetry/frontend");
  if (navigator.sendBeacon) {
    const sent = navigator.sendBeacon(endpoint, new Blob([body], { type: "application/json" }));
    if (sent) {
      return;
    }
  }
  fetch(endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body,
    keepalive: true,
  }).catch(() => {});
}

export function initFrontendTelemetry() {
  window.addEventListener("error", (event) => {
    sendTelemetry("js_error", {
      message: event.message,
      attributes: {
        source: truncate(event.filename, 160),
        line: String(event.lineno || ""),
        column: String(event.colno || ""),
      },
    });
  });
  window.addEventListener("unhandledrejection", (event) => {
    sendTelemetry("unhandled_rejection", {
      message: event.reason instanceof Error ? event.reason.message : String(event.reason || ""),
    });
  });
  window.addEventListener("load", () => {
    sendTelemetry("page_view", { attributes: { referrer: document.referrer } });
    window.setTimeout(() => {
      const navigation = performance.getEntriesByType("navigation")[0];
      if (navigation) {
        sendTelemetry("page_load", { durationMs: navigation.duration });
      }
    }, 0);
  });
}
