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
  }
}

export function apiAuthHeaders(extraHeaders = {}) {
  const token = window.localStorage.getItem("modless.authToken");
  return {
    ...(token ? {"X-Auth-Token": token} : {}),
    ...extraHeaders
  };
}

export async function api(path, options = {}) {
  const response = await fetch(apiUrl(path), {
    headers: {
      "Content-Type": "application/json",
      ...apiAuthHeaders(options.headers || {})
    },
    ...options
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
  if (response.status === 204) {
    return null;
  }
  const text = await response.text();
  if (!text.trim()) {
    return null;
  }
  return JSON.parse(text);
}
