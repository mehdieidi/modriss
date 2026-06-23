export interface BackendClientOptions {
  backendUrl: string;
  authToken?: string;
}

export class BackendClient {
  readonly #baseUrl: string;
  readonly #authToken: string;

  constructor(options: BackendClientOptions) {
    this.#baseUrl = options.backendUrl.replace(/\/$/, "");
    this.#authToken = options.authToken || "";
  }

  async fetchJson<T = unknown>(path: string, init: RequestInit = {}): Promise<T | null> {
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
    return (await response.json()) as T;
  }

  async loadModel(level: string, modelId: string) {
    return this.fetchJson<Record<string, unknown>>(`/${level}/${modelId}`);
  }

  async loadModelingConfig() {
    return this.fetchJson<Record<string, unknown>>("/modeling/config");
  }

  async patchModel(level: string, modelId: string, operations: unknown[]) {
    return this.fetchJson<Record<string, unknown>>(`/${level}/${modelId}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ operations }),
    });
  }
}

export function resolveBackendUrl(): string {
  return process.env.MODLESS_BACKEND_URL || "http://127.0.0.1:8080";
}
