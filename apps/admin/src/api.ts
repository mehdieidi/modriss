declare global {
  interface Window {
    MODRISS_ADMIN_BACKEND_BASE_URL?: string;
    MODRISS_PUBLIC_IP_LOOKUP_URL?: string;
  }
}

const baseUrl = () =>
  (window.MODRISS_ADMIN_BACKEND_BASE_URL || window.location.origin).replace(/\/$/, "");
const publicIpLookupUrl = () =>
  window.MODRISS_PUBLIC_IP_LOOKUP_URL || "https://api.ipify.org?format=json";

let publicIpPromise: Promise<string> | null = null;

async function publicIp() {
  if (!publicIpPromise) {
    publicIpPromise = lookupPublicIp();
  }
  return publicIpPromise;
}

async function lookupPublicIp() {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 1500);
  try {
    const response = await fetch(publicIpLookupUrl(), {
      signal: controller.signal,
      cache: "no-store",
    });
    if (!response.ok) {
      return "";
    }
    const contentType = response.headers.get("Content-Type") || "";
    if (contentType.includes("application/json")) {
      const body = (await response.json()) as { ip?: unknown };
      return typeof body.ip === "string" ? body.ip : "";
    }
    return (await response.text()).trim();
  } catch {
    return "";
  } finally {
    window.clearTimeout(timeout);
  }
}

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

export async function api<T>(
  path: string,
  token: string,
  options: RequestInit = {},
): Promise<T> {
  const response = await fetch(`${baseUrl()}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      "X-Auth-Token": token,
      "X-Request-Id": crypto.randomUUID(),
      ...(options.headers || {}),
    },
  });
  if (!response.ok) {
    let message = `Request failed with ${response.status}`;
    try {
      const body = await response.json();
      message = body.message || message;
    } catch {
      message = response.statusText || message;
    }
    throw new ApiError(response.status, message);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export async function login(email: string, password: string) {
  const detectedPublicIp = await publicIp();
  const response = await fetch(`${baseUrl()}/api/auth/login`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Request-Id": crypto.randomUUID(),
    },
    body: JSON.stringify({ email, password, publicIp: detectedPublicIp }),
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new ApiError(response.status, body.message || "Login failed");
  }
  return (await response.json()) as { token: string };
}

export async function bootstrapAdmin(token: string, bootstrapToken: string) {
  await api<void>("/api/admin/bootstrap", token, {
    method: "POST",
    body: JSON.stringify({ bootstrapToken }),
  });
}
