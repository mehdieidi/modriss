import {apiUrl, LOG_HINT} from './config.js';

export class ApiError extends Error {
  constructor(message,
      {status = 0, path = "", method = "GET", issues = []} = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.path = path;
    this.method = method;
    this.issues = Array.isArray(issues) ? issues : [];
    this.featureUnavailable = status === 501;
  }
}

export function apiAuthHeaders(extraHeaders = {}) {
  const token = window.localStorage.getItem("modless.authToken");
  return {
    ...(token ? {"X-Auth-Token": token} : {}),
    ...extraHeaders
  };
}

function isBodyWithoutContentType(body) {
  return body instanceof FormData
      || body instanceof Blob
      || body instanceof ArrayBuffer
      || body instanceof URLSearchParams;
}

function buildHeaders(extraHeaders = {}, body = null) {
  const headers = new Headers(apiAuthHeaders());
  Object.entries(extraHeaders || {}).forEach(([name, value]) => {
    if (value !== undefined && value !== null) {
      headers.set(name, value);
    }
  });
  if (body != null && !headers.has("Content-Type")
      && !isBodyWithoutContentType(body)) {
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
    headers: buildHeaders(options.headers || {}, body)
  });
  if (!response.ok) {
    let message = `HTTP ${response.status}`;
    let issues = [];
    try {
      const contentType = response.headers.get("content-type") || "";
      if (contentType.includes("application/json")) {
        const body = await response.json();
        message = body.message || message;
        issues = Array.isArray(body.issues) ? body.issues : [];
      } else {
        const text = (await response.text()).trim();
        if (text) {
          message = text;
        }
      }
    } catch {
      // keep generic error message
    }
    const method = (options.method || "GET").toUpperCase();
    throw new ApiError(`${method} ${path} failed: ${message} (${LOG_HINT})`, {
      status: response.status,
      path,
      method,
      issues
    });
  }
  return readResponseBody(response);
}

export function isPlannedFeatureError(error) {
  return error instanceof ApiError && error.featureUnavailable;
}
