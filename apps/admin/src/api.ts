declare global {
  interface Window {
    VARKA_ADMIN_BACKEND_BASE_URL?: string;
  }
}

const baseUrl = () =>
  (window.VARKA_ADMIN_BACKEND_BASE_URL || "http://127.0.0.1:8080").replace(/\/$/, "");

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
  const response = await fetch(`${baseUrl()}/api/auth/login`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Request-Id": crypto.randomUUID(),
    },
    body: JSON.stringify({ email, password }),
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
