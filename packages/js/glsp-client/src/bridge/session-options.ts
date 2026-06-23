export function resolveBackendUrl(options: Record<string, unknown> = {}): string {
  if (options.backendUrl) {
    return String(options.backendUrl);
  }
  if (typeof window !== "undefined") {
    const boot = (window as { modlessFrontendBoot?: { backendUrl?: string } }).modlessFrontendBoot;
    if (boot?.backendUrl) {
      return boot.backendUrl;
    }
  }
  return "http://127.0.0.1:8080";
}
