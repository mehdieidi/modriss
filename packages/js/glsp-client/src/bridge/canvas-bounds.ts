import { InitializeCanvasBoundsAction } from "@eclipse-glsp/sprotty";

type ActionDispatcher = { dispatch: (action: unknown) => void | Promise<void> };

export function hostPageBounds(host: HTMLElement) {
  const rect = host.getBoundingClientRect();
  const scrollX = window.scrollX || window.pageXOffset || 0;
  const scrollY = window.scrollY || window.pageYOffset || 0;
  return {
    x: rect.left + scrollX,
    y: rect.top + scrollY,
    width: Math.max(rect.width, 1),
    height: Math.max(rect.height, 1),
  };
}

export function syncCanvasBounds(host: HTMLElement, actionDispatcher: ActionDispatcher): void {
  actionDispatcher.dispatch(InitializeCanvasBoundsAction.create(hostPageBounds(host)));
}

export function scheduleCanvasBoundsSync(
  host: HTMLElement,
  actionDispatcher: ActionDispatcher,
): () => void {
  const run = () => syncCanvasBounds(host, actionDispatcher);
  run();
  const onResize = () => run();
  window.addEventListener("resize", onResize);
  const observer =
    typeof ResizeObserver !== "undefined"
      ? new ResizeObserver(() => run())
      : null;
  observer?.observe(host);
  return () => {
    window.removeEventListener("resize", onResize);
    observer?.disconnect();
  };
}
