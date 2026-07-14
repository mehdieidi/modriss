const APP_NAME = "landing";
const MAX_MESSAGE = 300;
const ENDPOINT = "/api/telemetry/frontend";

function truncate(value, max = MAX_MESSAGE) {
  const text = value == null ? "" : String(value);
  return text.length <= max ? text : text.slice(0, max);
}

function sendTelemetry(kind, payload = {}) {
  const body = JSON.stringify({
    app: APP_NAME,
    kind,
    message: truncate(payload.message),
    url: truncate(window.location.href),
    durationMs: Number.isFinite(payload.durationMs) ? Math.round(payload.durationMs) : null,
    attributes: payload.attributes || {},
    occurredAt: new Date().toISOString(),
  });
  if (navigator.sendBeacon) {
    const sent = navigator.sendBeacon(ENDPOINT, new Blob([body], { type: "application/json" }));
    if (sent) {
      return;
    }
  }
  fetch(ENDPOINT, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body,
    keepalive: true,
  }).catch(() => {});
}

export function initLandingTelemetry() {
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
    window.setTimeout(() => {
      const navigation = performance.getEntriesByType("navigation")[0];
      if (navigation) {
        sendTelemetry("page_load", { durationMs: navigation.duration });
      }
    }, 0);
  });
}
