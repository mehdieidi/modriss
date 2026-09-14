const APP_NAME = "admin";
const MAX_MESSAGE = 300;

function endpoint() {
  const base = (window.MODRISS_ADMIN_BACKEND_BASE_URL || "").replace(/\/$/, "");
  return `${base}/api/telemetry/frontend`;
}

function truncate(value: unknown, max = MAX_MESSAGE) {
  const text = value == null ? "" : String(value);
  return text.length <= max ? text : text.slice(0, max);
}

function sendTelemetry(kind: string, payload: { message?: unknown; durationMs?: number } = {}) {
  const body = JSON.stringify({
    app: APP_NAME,
    kind,
    message: truncate(payload.message),
    url: truncate(window.location.href),
    durationMs: Number.isFinite(payload.durationMs) ? Math.round(payload.durationMs as number) : null,
    occurredAt: new Date().toISOString(),
  });
  const url = endpoint();
  if (navigator.sendBeacon) {
    const sent = navigator.sendBeacon(url, new Blob([body], { type: "application/json" }));
    if (sent) {
      return;
    }
  }
  fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body,
    keepalive: true,
  }).catch(() => {});
}

export function initAdminTelemetry() {
  window.addEventListener("error", (event) => {
    sendTelemetry("js_error", { message: event.message });
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
