const APP_NAME = "admin";
const MAX_MESSAGE = 300;

declare global {
  interface Window {
    MODRISS_PUBLIC_COUNTRY_LOOKUP_URL?: string;
  }
}

let publicCountryPromise: Promise<string> | null = null;

function endpoint() {
  const base = (window.MODRISS_ADMIN_BACKEND_BASE_URL || "").replace(/\/$/, "");
  return `${base}/api/telemetry/frontend`;
}

function truncate(value: unknown, max = MAX_MESSAGE) {
  const text = value == null ? "" : String(value);
  return text.length <= max ? text : text.slice(0, max);
}

function sendTelemetry(
  kind: string,
  payload: { message?: unknown; durationMs?: number; attributes?: Record<string, string> } = {},
) {
  const body = JSON.stringify({
    app: APP_NAME,
    kind,
    message: truncate(payload.message),
    url: truncate(window.location.href),
    durationMs: Number.isFinite(payload.durationMs) ? Math.round(payload.durationMs as number) : null,
    attributes: payload.attributes || {},
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

async function lookupPublicCountry() {
  if (!publicCountryPromise) {
    publicCountryPromise = (async () => {
      const controller = new AbortController();
      const timeout = window.setTimeout(() => controller.abort(), 1500);
      try {
        const url = window.MODRISS_PUBLIC_COUNTRY_LOOKUP_URL || "https://ipapi.co/country/";
        const response = await fetch(url, { signal: controller.signal, cache: "no-store" });
        if (!response.ok) return "";
        const contentType = response.headers.get("Content-Type") || "";
        if (contentType.includes("application/json")) {
          const body = (await response.json()) as { country_code?: unknown };
          return typeof body.country_code === "string" ? body.country_code : "";
        }
        return (await response.text()).trim();
      } catch {
        return "";
      } finally {
        window.clearTimeout(timeout);
      }
    })();
  }
  return publicCountryPromise;
}

async function sendPageView() {
  const country = await lookupPublicCountry();
  sendTelemetry("page_view", { attributes: { country, referrer: document.referrer } });
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
    void sendPageView();
    window.setTimeout(() => {
      const navigation = performance.getEntriesByType("navigation")[0];
      if (navigation) {
        sendTelemetry("page_load", { durationMs: navigation.duration });
      }
    }, 0);
  });
}
