export class BackendClient {
  #baseUrl;
  #authToken;
  constructor(options) {
    this.#baseUrl = options.backendUrl.replace(/\/$/, "");
    this.#authToken = options.authToken || "";
  }
  async fetchJson(path, init = {}) {
    const headers = new Headers(init.headers || {});
    if (this.#authToken) {
      headers.set("X-Auth-Token", this.#authToken);
    }
    const response = await fetch(`${this.#baseUrl}/api${path}`, { ...init, headers });
    if (!response.ok) {
      throw new Error(`Backend ${path} failed: ${response.status}`);
    }
    if (response.status === 204) {
      return null;
    }
    return await response.json();
  }
  async loadModel(level, modelId) {
    return this.fetchJson(`/${level}/${modelId}`);
  }
  async loadModelingConfig() {
    return this.fetchJson("/modeling/config");
  }
  async patchModel(level, modelId, operations) {
    return this.fetchJson(`/${level}/${modelId}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ operations }),
    });
  }
}
export function resolveBackendUrl() {
  return process.env.MODLESS_BACKEND_URL || "http://127.0.0.1:8080";
}
