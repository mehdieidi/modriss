import { apiUrl } from "./config.js";
import { ApiError, buildUserMessage, isServerErrorStatus } from "./errors.js";

export { ApiError, isPlannedFeatureError } from "./errors.js";

export function apiAuthHeaders(extraHeaders = {}) {
  const token = window.localStorage.getItem("varka.authToken");
  return {
    ...(token ? { "X-Auth-Token": token } : {}),
    ...extraHeaders,
  };
}

function isBodyWithoutContentType(body) {
  return (
    body instanceof FormData ||
    body instanceof Blob ||
    body instanceof ArrayBuffer ||
    body instanceof URLSearchParams
  );
}

function buildHeaders(extraHeaders = {}, body = null) {
  const headers = new Headers(apiAuthHeaders());
  Object.entries(extraHeaders || {}).forEach(([name, value]) => {
    if (value !== undefined && value !== null) {
      headers.set(name, value);
    }
  });
  if (body != null && !headers.has("Content-Type") && !isBodyWithoutContentType(body)) {
    headers.set("Content-Type", "application/json");
  }
  return headers;
}

async function readResponseBody(response) {
  if (response.status === 204 || response.status === 205) {
    return null;
  }
  const text = await response.text();
  if (!text.trim()) {
    return null;
  }
  const contentType = response.headers.get("content-type") || "";
  if (contentType.includes("application/json")) {
    return JSON.parse(text);
  }
  return text;
}

export async function api(path, options = {}) {
  const body = options.body ?? null;
  const response = await fetch(apiUrl(path), {
    ...options,
    headers: buildHeaders(options.headers || {}, body),
  });
  if (!response.ok) {
    const method = (options.method || "GET").toUpperCase();
    let message = `Request failed (${response.status}).`;
    let issues = [];
    let errorId = response.headers.get("X-Request-Id") || "";
    try {
      const contentType = response.headers.get("content-type") || "";
      if (contentType.includes("application/json")) {
        const payload = await response.json();
        message = payload.message || message;
        issues = Array.isArray(payload.issues) ? payload.issues : [];
        errorId = payload.errorId || errorId;
      } else if (!isServerErrorStatus(response.status)) {
        const text = (await response.text()).trim();
        if (text) {
          message = text;
        }
      }
    } catch {
      // keep generic error message
    }
    const userMessage = buildUserMessage(response.status, message, issues, errorId);
    throw new ApiError(userMessage, {
      status: response.status,
      path,
      method,
      issues,
      errorId,
    });
  }
  return readResponseBody(response);
}

export { apiUrl };
