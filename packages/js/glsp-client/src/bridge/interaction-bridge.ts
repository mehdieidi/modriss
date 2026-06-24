/**
 * Shell helpers: wheel zoom, background pan, and Sprotty tool activation.
 */
import {
  EnableDefaultToolsAction,
  MoveViewportAction,
  ZoomAction,
} from "@eclipse-glsp/client";

type ActionDispatcher = { dispatch: (action: unknown) => void | Promise<void> };

type InteractionBridgeOptions = {
  host: HTMLElement;
  diagramRoot: HTMLElement;
  actionDispatcher: ActionDispatcher;
  onViewportChange?: () => void;
};

function isNodeOrEdgeTarget(target: EventTarget | null): boolean {
  if (!(target instanceof Element)) {
    return false;
  }
  return Boolean(
    target.closest(
      ".modless-node, .modless-edge, .sprotty-node, .sprotty-edge, .sprotty-port, .modless-port",
    ),
  );
}

function isBackgroundTarget(target: EventTarget | null): boolean {
  return !isNodeOrEdgeTarget(target);
}

function readViewportZoom(diagramRoot: HTMLElement): number {
  const transform =
    diagramRoot.querySelector<SVGElement>(".sprotty-graph")?.getAttribute("transform") ||
    diagramRoot.querySelector<SVGElement>("g[transform]")?.getAttribute("transform") ||
    "";
  const match = transform.match(/scale\(([-\d.]+)\)/);
  const zoom = match ? Number(match[1]) : 1;
  return Number.isFinite(zoom) && zoom > 0 ? zoom : 1;
}

function focusDiagram(diagramRoot: HTMLElement): void {
  const focusTarget =
    diagramRoot.querySelector<HTMLElement>(".sprotty-graph") ||
    diagramRoot.querySelector<HTMLElement>("svg") ||
    diagramRoot;
  focusTarget?.focus?.({ preventScroll: true });
}

export function installInteractionBridge(options: InteractionBridgeOptions): () => void {
  const { host, diagramRoot, actionDispatcher, onViewportChange } = options;
  const disposers: Array<() => void> = [];
  let panPointerId: number | null = null;
  let lastPanClient = { x: 0, y: 0 };

  const stripGridBackground = () => {
    diagramRoot.querySelectorAll<HTMLElement>(".sprotty-graph, svg").forEach((el) => {
      el.tabIndex = 0;
      el.style.outline = "none";
      el.style.overflow = "visible";
      el.classList.remove("grid-background");
    });
  };

  const onWheel = (event: WheelEvent) => {
    event.preventDefault();
    const factor = event.deltaY > 0 ? 1 / 1.1 : 1.1;
    actionDispatcher.dispatch(ZoomAction.create({ zoomFactor: factor }));
    onViewportChange?.();
  };

  const onPointerDown = (event: PointerEvent) => {
    if (event.button !== 0) {
      return;
    }
    focusDiagram(diagramRoot);
    if (!isBackgroundTarget(event.target)) {
      return;
    }
    actionDispatcher.dispatch(EnableDefaultToolsAction.create());
    panPointerId = event.pointerId;
    lastPanClient = { x: event.clientX, y: event.clientY };
    host.setPointerCapture?.(event.pointerId);
    host.classList.add("canvas-cursor-grabbing");
    event.preventDefault();
  };

  const onPointerMove = (event: PointerEvent) => {
    if (panPointerId !== event.pointerId) {
      return;
    }
    const zoom = readViewportZoom(diagramRoot);
    const dx = (event.clientX - lastPanClient.x) / zoom;
    const dy = (event.clientY - lastPanClient.y) / zoom;
    if (!dx && !dy) {
      return;
    }
    lastPanClient = { x: event.clientX, y: event.clientY };
    actionDispatcher.dispatch(MoveViewportAction.create({ moveX: -dx, moveY: -dy }));
    onViewportChange?.();
    event.preventDefault();
  };

  const endPan = (event: PointerEvent) => {
    if (panPointerId !== event.pointerId) {
      return;
    }
    panPointerId = null;
    host.releasePointerCapture?.(event.pointerId);
    host.classList.remove("canvas-cursor-grabbing");
    onViewportChange?.();
  };

  stripGridBackground();
  host.addEventListener("wheel", onWheel, { passive: false });
  host.addEventListener("pointerdown", onPointerDown);
  host.addEventListener("pointermove", onPointerMove);
  host.addEventListener("pointerup", endPan);
  host.addEventListener("pointercancel", endPan);

  disposers.push(() => host.removeEventListener("wheel", onWheel));
  disposers.push(() => host.removeEventListener("pointerdown", onPointerDown));
  disposers.push(() => host.removeEventListener("pointermove", onPointerMove));
  disposers.push(() => host.removeEventListener("pointerup", endPan));
  disposers.push(() => host.removeEventListener("pointercancel", endPan));

  actionDispatcher.dispatch(EnableDefaultToolsAction.create());
  window.requestAnimationFrame(() => {
    stripGridBackground();
    focusDiagram(diagramRoot);
  });

  return () => {
    for (const dispose of disposers) {
      dispose();
    }
  };
}
