import { state } from "../state.js";
import { el } from "../dom.js";
import { applyViewportTransform } from "./glsp-renderer-core.js";
import { updateGlspHoverVisual } from "./glsp-editor.js";

const DRAG_THRESHOLD_PX = 5;
const VIEWPORT_EFFECTS_DEBOUNCE_MS = 150;

let currentCanvasCursorMode = "";

function applyLightweightViewportChrome() {
  const scale = state.viewport.scale || 1;
  if (el.canvasZoomValue) {
    el.canvasZoomValue.textContent = `${Math.round(scale * 100)}%`;
  }
  el.canvasGrid?.style.setProperty("--viewport-scale", String(scale));
}

function setCanvasCursor(mode) {
  const nextMode = mode || "grab";
  if (currentCanvasCursorMode === nextMode) {
    return;
  }
  currentCanvasCursorMode = nextMode;
  const classes = ["canvas-cursor-grab", "canvas-cursor-grabbing", "canvas-cursor-pointer"];
  const targets = [el.canvasViewport, el.canvasGrid, el.glspEditorHost].filter(Boolean);
  targets.forEach((target) => {
    target.classList.remove(...classes);
    if (mode) {
      target.classList.add(`canvas-cursor-${mode}`);
    }
  });
  const cursor = nextMode === "grabbing" ? "grabbing" : nextMode === "pointer" ? "pointer" : "grab";
  el.glspEditorHost?.style.setProperty("cursor", cursor);
}

function contextNameFromTarget(target) {
  return target?.closest?.(".glsp-context-box")?.dataset?.contextName || "";
}

function nodeIdFromTarget(target) {
  return target?.closest?.(".glsp-node")?.dataset?.id || "";
}

function edgeIdFromTarget(target) {
  return target?.closest?.(".glsp-edge")?.dataset?.id || "";
}

function pointerMoved(start, event) {
  return Math.hypot(event.clientX - start.x, event.clientY - start.y) > DRAG_THRESHOLD_PX;
}

function scheduleInteractionAction(callback) {
  window.requestAnimationFrame(() => {
    callback();
  });
}

export function bindGlspInteractions(editor, callbacks = {}) {
  editor?.disposeInteractions?.();
  const host = editor?.host;
  if (!host) {
    return;
  }

  let canvasPan = null;
  let nodeDrag = null;
  let edgePress = null;
  let contextPress = null;
  let hostRect = null;
  let canvasPanFrame = 0;
  let canvasPanDx = 0;
  let canvasPanDy = 0;
  let nodeDragFrame = 0;
  let pendingNodeDrag = null;
  let hoverFrame = 0;
  let pendingHoverTarget = null;
  let wheelFrame = 0;
  let pendingWheel = null;
  let viewportEffectsTimer = 0;

  const scheduleHeavyViewportEffects = () => {
    if (viewportEffectsTimer) {
      window.clearTimeout(viewportEffectsTimer);
    }
    viewportEffectsTimer = window.setTimeout(() => {
      viewportEffectsTimer = 0;
      callbacks.onViewportChange?.();
    }, VIEWPORT_EFFECTS_DEBOUNCE_MS);
  };

  const invalidateHostRect = () => {
    hostRect = null;
  };

  const ensureHostRect = () => {
    if (!hostRect) {
      const surface = host.querySelector(".glsp-diagram-surface");
      hostRect = surface?.getBoundingClientRect?.() || host.getBoundingClientRect?.();
    }
    return hostRect;
  };

  const flushCanvasPan = () => {
    canvasPanFrame = 0;
    const dx = canvasPanDx;
    const dy = canvasPanDy;
    canvasPanDx = 0;
    canvasPanDy = 0;
    if (!dx && !dy) {
      return;
    }
    state.viewport.x += dx;
    state.viewport.y += dy;
    applyViewportTransform(host, state.viewport);
  };

  const queueCanvasPan = (dx, dy) => {
    canvasPanDx += dx;
    canvasPanDy += dy;
    if (!canvasPanFrame) {
      canvasPanFrame = window.requestAnimationFrame(flushCanvasPan);
    }
  };

  const flushNodeDrag = () => {
    nodeDragFrame = 0;
    const move = pendingNodeDrag;
    pendingNodeDrag = null;
    if (!move) {
      return;
    }
    callbacks.onNodeDrag?.(move.nodeId, { x: move.x, y: move.y });
    editor.moveNodeVisual?.(move.nodeId, move.x, move.y);
  };

  const queueNodeDrag = (nodeId, x, y) => {
    pendingNodeDrag = { nodeId, x, y };
    if (!nodeDragFrame) {
      nodeDragFrame = window.requestAnimationFrame(flushNodeDrag);
    }
  };

  const applyHoverFromTarget = (target) => {
    const hoverNodeId = nodeIdFromTarget(target) || null;
    const hoverEdgeId = edgeIdFromTarget(target) || null;
    if (hoverNodeId === editor.hoveredNodeId && hoverEdgeId === editor.hoveredEdgeId) {
      setCanvasCursor(hoverNodeId || hoverEdgeId ? "pointer" : "grab");
      return;
    }
    const prevNode = editor.hoveredNodeId;
    const prevEdge = editor.hoveredEdgeId;
    updateGlspHoverVisual(hoverNodeId, hoverEdgeId);
    if (hoverNodeId !== prevNode) {
      callbacks.onNodeHover?.(hoverNodeId);
    }
    if (hoverEdgeId !== prevEdge) {
      callbacks.onEdgeHover?.(hoverEdgeId);
    }
    setCanvasCursor(hoverNodeId || hoverEdgeId ? "pointer" : "grab");
  };

  const flushHover = () => {
    hoverFrame = 0;
    if (canvasPan || nodeDrag || edgePress || contextPress) {
      return;
    }
    applyHoverFromTarget(pendingHoverTarget);
    pendingHoverTarget = null;
  };

  const queueHover = (target) => {
    pendingHoverTarget = target;
    if (!hoverFrame) {
      hoverFrame = window.requestAnimationFrame(flushHover);
    }
  };

  const flushWheel = () => {
    wheelFrame = 0;
    const event = pendingWheel;
    pendingWheel = null;
    if (!event) {
      return;
    }
    const prev = state.viewport.scale || 1;
    const delta = event.deltaY > 0 ? 0.92 : 1.08;
    const next = Math.max(0.01, Math.min(2.5, prev * delta));
    const rect = ensureHostRect();
    const localX = event.clientX - (rect?.left || 0);
    const localY = event.clientY - (rect?.top || 0);
    const scale = state.viewport.scale || 1;
    const graphX = (localX - state.viewport.x) / scale;
    const graphY = (localY - state.viewport.y) / scale;
    state.viewport.x -= graphX * (next - prev);
    state.viewport.y -= graphY * (next - prev);
    state.viewport.scale = next;
    applyViewportTransform(host, state.viewport);
    applyLightweightViewportChrome();
    scheduleHeavyViewportEffects();
  };

  const surfacePointerDown = (event) => {
    if (event.button !== 0) {
      return;
    }
    const nodeId = nodeIdFromTarget(event.target);
    const edgeId = edgeIdFromTarget(event.target);
    const contextName = contextNameFromTarget(event.target);

    if (contextName) {
      callbacks.onCanvasPointerDown?.(event);
      contextPress = {
        contextName,
        pointerId: event.pointerId,
        startClient: { x: event.clientX, y: event.clientY },
        startEvent: event,
        moved: false,
      };
      host.setPointerCapture?.(event.pointerId);
      setCanvasCursor("pointer");
      return;
    }

    if (nodeId) {
      callbacks.onCanvasPointerDown?.(event);
      const node = state.nodesById.get(nodeId);
      if (node) {
        nodeDrag = {
          nodeId,
          pointerId: event.pointerId,
          startClient: { x: event.clientX, y: event.clientY },
          startPos: { x: node.x, y: node.y },
          startEvent: event,
          moved: false,
        };
        editor.beginNodeDrag?.(nodeId);
        callbacks.onNodeDragStart?.(nodeId, event);
        host.setPointerCapture?.(event.pointerId);
        setCanvasCursor("grabbing");
      }
      return;
    }

    if (edgeId) {
      callbacks.onCanvasPointerDown?.(event);
      edgePress = {
        edgeId,
        pointerId: event.pointerId,
        startClient: { x: event.clientX, y: event.clientY },
        startEvent: event,
        moved: false,
      };
      host.setPointerCapture?.(event.pointerId);
      return;
    }

    callbacks.onCanvasPointerDown?.(event);
    canvasPan = {
      pointerId: event.pointerId,
      lastClient: { x: event.clientX, y: event.clientY },
      startClient: { x: event.clientX, y: event.clientY },
      startEvent: event,
      moved: false,
    };
    host.setPointerCapture?.(event.pointerId);
    setCanvasCursor("grabbing");
  };

  const surfacePointerMove = (event) => {
    if (nodeDrag?.pointerId === event.pointerId) {
      if (!nodeDrag.moved && pointerMoved(nodeDrag.startClient, event)) {
        nodeDrag.moved = true;
      }
      const dx = (event.clientX - nodeDrag.startClient.x) / (state.viewport.scale || 1);
      const dy = (event.clientY - nodeDrag.startClient.y) / (state.viewport.scale || 1);
      queueNodeDrag(
        nodeDrag.nodeId,
        Math.round(nodeDrag.startPos.x + dx),
        Math.round(nodeDrag.startPos.y + dy),
      );
      return;
    }

    if (edgePress?.pointerId === event.pointerId) {
      if (!edgePress.moved && pointerMoved(edgePress.startClient, event)) {
        edgePress.moved = true;
      }
      return;
    }

    if (contextPress?.pointerId === event.pointerId) {
      if (!contextPress.moved && pointerMoved(contextPress.startClient, event)) {
        contextPress.moved = true;
      }
      return;
    }

    if (canvasPan?.pointerId === event.pointerId) {
      if (!canvasPan.moved && pointerMoved(canvasPan.startClient, event)) {
        canvasPan.moved = true;
      }
      queueCanvasPan(
        event.clientX - canvasPan.lastClient.x,
        event.clientY - canvasPan.lastClient.y,
      );
      canvasPan.lastClient = { x: event.clientX, y: event.clientY };
      return;
    }

    queueHover(event.target);
  };

  const surfacePointerUp = (event) => {
    if (nodeDrag?.pointerId === event.pointerId) {
      flushNodeDrag();
      const dx = (event.clientX - nodeDrag.startClient.x) / (state.viewport.scale || 1);
      const dy = (event.clientY - nodeDrag.startClient.y) / (state.viewport.scale || 1);
      const position = {
        x: Math.round(nodeDrag.startPos.x + dx),
        y: Math.round(nodeDrag.startPos.y + dy),
      };
      const moved =
        nodeDrag.moved ||
        pointerMoved(nodeDrag.startClient, event) ||
        position.x !== nodeDrag.startPos.x ||
        position.y !== nodeDrag.startPos.y;
      const nodeId = nodeDrag.nodeId;
      const startEvent = nodeDrag.startEvent;
      callbacks.onNodeDragEnd?.(nodeId, position, { moved });
      editor.endNodeDrag?.();
      nodeDrag = null;
      host.releasePointerCapture?.(event.pointerId);
      invalidateHostRect();
      setCanvasCursor("grab");
      if (!moved) {
        scheduleInteractionAction(() => callbacks.onNodeClick?.(nodeId, startEvent));
      }
      return;
    }

    if (edgePress?.pointerId === event.pointerId) {
      const press = edgePress;
      edgePress = null;
      host.releasePointerCapture?.(event.pointerId);
      if (!press.moved && !pointerMoved(press.startClient, event)) {
        scheduleInteractionAction(() => callbacks.onEdgeClick?.(press.edgeId, press.startEvent));
      }
      return;
    }

    if (contextPress?.pointerId === event.pointerId) {
      const press = contextPress;
      contextPress = null;
      host.releasePointerCapture?.(event.pointerId);
      if (!press.moved && !pointerMoved(press.startClient, event)) {
        scheduleInteractionAction(() =>
          callbacks.onContextSelect?.(press.contextName, press.startEvent),
        );
      }
      setCanvasCursor("grab");
      return;
    }

    if (canvasPan?.pointerId === event.pointerId) {
      flushCanvasPan();
      const moved = canvasPan.moved || pointerMoved(canvasPan.startClient, event);
      const startEvent = canvasPan.startEvent;
      canvasPan = null;
      host.releasePointerCapture?.(event.pointerId);
      invalidateHostRect();
      if (viewportEffectsTimer) {
        window.clearTimeout(viewportEffectsTimer);
        viewportEffectsTimer = 0;
      }
      callbacks.onViewportChange?.();
      setCanvasCursor("grab");
      if (!moved) {
        scheduleInteractionAction(() => callbacks.onCanvasClick?.(startEvent));
      }
    }
  };

  const surfaceDoubleClick = (event) => {
    const nodeId = nodeIdFromTarget(event.target);
    if (!nodeId) {
      return;
    }
    scheduleInteractionAction(() => callbacks.onNodeDoubleClick?.(nodeId, event));
  };

  const surfaceWheel = (event) => {
    event.preventDefault();
    pendingWheel = event;
    if (!wheelFrame) {
      wheelFrame = window.requestAnimationFrame(flushWheel);
    }
  };

  const surfaceKeyDown = (event) => {
    if (event.key === "Escape") {
      callbacks.onEscape?.(event);
    }
  };

  const onWindowResize = () => invalidateHostRect();

  host.addEventListener("pointerdown", surfacePointerDown);
  host.addEventListener("pointermove", surfacePointerMove);
  host.addEventListener("pointerup", surfacePointerUp);
  host.addEventListener("pointercancel", surfacePointerUp);
  host.addEventListener("dblclick", surfaceDoubleClick);
  host.addEventListener("wheel", surfaceWheel, { passive: false });
  host.addEventListener("keydown", surfaceKeyDown);
  window.addEventListener("resize", onWindowResize);
  host.tabIndex = 0;

  editor.disposeInteractions = () => {
    host.removeEventListener("pointerdown", surfacePointerDown);
    host.removeEventListener("pointermove", surfacePointerMove);
    host.removeEventListener("pointerup", surfacePointerUp);
    host.removeEventListener("pointercancel", surfacePointerUp);
    host.removeEventListener("dblclick", surfaceDoubleClick);
    host.removeEventListener("wheel", surfaceWheel);
    host.removeEventListener("keydown", surfaceKeyDown);
    window.removeEventListener("resize", onWindowResize);
    if (canvasPanFrame) {
      window.cancelAnimationFrame(canvasPanFrame);
      canvasPanFrame = 0;
    }
    if (nodeDragFrame) {
      window.cancelAnimationFrame(nodeDragFrame);
      nodeDragFrame = 0;
    }
    if (hoverFrame) {
      window.cancelAnimationFrame(hoverFrame);
      hoverFrame = 0;
    }
    if (wheelFrame) {
      window.cancelAnimationFrame(wheelFrame);
      wheelFrame = 0;
    }
    if (viewportEffectsTimer) {
      window.clearTimeout(viewportEffectsTimer);
      viewportEffectsTimer = 0;
    }
    setCanvasCursor("");
    invalidateHostRect();
  };

  setCanvasCursor("grab");
}
