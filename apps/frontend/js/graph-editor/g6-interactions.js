import {state} from '../state.js';
import {el} from '../dom.js';
import {nodeSizeForDiagram} from './g6-style.js';
import {
  clearConnectionPreview,
  updateConnectionPreview
} from './g6-overlays.js';

function originalEvent(event) {
  return event?.originalEvent || event;
}

function eventButton(event) {
  const source = originalEvent(event);
  return Number.isFinite(source?.button) ? source.button : 0;
}

function eventClientPoint(event) {
  const source = originalEvent(event);
  return {
    x: Number(source?.clientX),
    y: Number(source?.clientY)
  };
}

function targetId(event) {
  return event?.target?.id || event?.target?.idOf?.() || "";
}

function setCanvasCursor(mode) {
  const classes = [
    "canvas-cursor-grab",
    "canvas-cursor-grabbing",
    "canvas-cursor-pointer"
  ];
  const targets = [el.canvasViewport, el.canvasGrid, el.g6EditorHost]
  .filter(Boolean);
  targets.forEach((target) => {
    target.classList.remove(...classes);
    if (mode) {
      target.classList.add(`canvas-cursor-${mode}`);
    }
  });
  const cursor = mode === "grabbing" ? "grabbing"
      : mode === "pointer" ? "pointer" : "grab";
  el.g6EditorHost?.style.setProperty("cursor", cursor);
  el.g6EditorHost?.querySelectorAll("canvas").forEach((canvas) => {
    canvas.style.cursor = cursor;
  });
}

function graphCanvasPoint(graph, clientX, clientY) {
  let converted = null;
  try {
    converted = graph?.getCanvasByClient?.([clientX, clientY]);
  } catch {
    try {
      converted = graph?.getCanvasByClient?.({x: clientX, y: clientY});
    } catch {
      converted = null;
    }
  }
  if (Array.isArray(converted)) {
    return {x: converted[0], y: converted[1]};
  }
  if (converted && Number.isFinite(converted.x)
      && Number.isFinite(converted.y)) {
    return converted;
  }
  const rect = graph?.getCanvas?.()?.getContainer?.()?.getBoundingClientRect?.();
  const localX = clientX - (rect?.left || 0);
  const localY = clientY - (rect?.top || 0);
  return {
    x: (localX - state.viewport.x) / state.viewport.scale,
    y: (localY - state.viewport.y) / state.viewport.scale
  };
}

function findNodeAtClientPoint(graph, sourceId, clientX, clientY) {
  if (!Number.isFinite(clientX) || !Number.isFinite(clientY)) {
    return null;
  }
  const point = graphCanvasPoint(graph, clientX, clientY);
  const size = nodeSizeForDiagram(state.activeType);
  for (const [nodeId, node] of state.nodesById.entries()) {
    if (nodeId === sourceId) {
      continue;
    }
    const width = Number(node?.width) || size.width;
    const height = Number(node?.height) || size.height;
    if (point.x >= node.x && point.x <= node.x + width
        && point.y >= node.y && point.y <= node.y + height) {
      return nodeId;
    }
  }
  return null;
}

function graphNodeTopLeft(graph, nodeId) {
  const data = graph?.getNodeData?.(nodeId);
  const size = nodeSizeForDiagram(state.activeType);
  const x = Number(data?.style?.x);
  const y = Number(data?.style?.y);
  if (!Number.isFinite(x) || !Number.isFinite(y)) {
    const node = state.nodesById.get(nodeId);
    return node ? {x: node.x, y: node.y, width: size.width, height: size.height}
        : null;
  }
  const width = Number(data?.style?.width) || size.width;
  const height = Number(data?.style?.height) || size.height;
  return {x: x - width / 2, y: y - height / 2, width, height};
}

function nodePositionFromGraph(graph, nodeId) {
  const topLeft = graphNodeTopLeft(graph, nodeId);
  if (!topLeft) {
    return null;
  }
  return {
    x: Math.round(topLeft.x),
    y: Math.round(topLeft.y)
  };
}

function isLinkHandleHit(graph, nodeId, clientX, clientY) {
  const topLeft = graphNodeTopLeft(graph, nodeId);
  if (!topLeft) {
    return false;
  }
  const point = graphCanvasPoint(graph, clientX, clientY);
  const centerY = topLeft.y + topLeft.height / 2;
  const leftDistance = Math.hypot(point.x - topLeft.x, point.y - centerY);
  const rightDistance = Math.hypot(point.x - (topLeft.x + topLeft.width),
      point.y - centerY);
  return Math.min(leftDistance, rightDistance) <= 16;
}

export function bindG6Interactions(editor, callbacks = {}) {
  const graph = editor.graph;
  if (!graph) {
    return;
  }
  let draggedNodeId = null;
  let dragged = false;
  let linkDrag = null;
  let hoveredNodeId = null;
  let hoveredInteractive = false;
  let canvasDragging = false;
  let canvasPan = null;
  let canvasPanFrame = 0;
  let canvasPanDx = 0;
  let canvasPanDy = 0;
  let lastClickSuppressedNodeId = null;

  setCanvasCursor("grab");

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
    try {
      const result = graph.translateBy?.([dx, dy], false);
      result?.then?.(() => graph.draw?.())?.catch?.(() => graph.draw?.());
      if (!result?.then) {
        graph.draw?.();
      }
    } catch {
      graph.draw?.();
    }
  };

  const queueCanvasPan = (dx, dy) => {
    canvasPanDx += dx;
    canvasPanDy += dy;
    if (!canvasPanFrame) {
      canvasPanFrame = window.requestAnimationFrame(flushCanvasPan);
    }
  };

  const finishCanvasPan = () => {
    if (!canvasPan) {
      return;
    }
    flushCanvasPan();
    try {
      el.g6EditorHost?.releasePointerCapture?.(canvasPan.pointerId);
    } catch {
      // Capture may already be released after pointerup outside the host.
    }
    canvasPan = null;
    canvasDragging = false;
    setCanvasCursor(hoveredInteractive ? "pointer" : "grab");
  };

  const hostPointerDown = (event) => {
    if (event.button !== 0 || hoveredInteractive || linkDrag) {
      return;
    }
    if (findNodeAtClientPoint(graph, null, event.clientX, event.clientY)) {
      return;
    }
    canvasPan = {
      pointerId: event.pointerId,
      lastX: event.clientX,
      lastY: event.clientY
    };
    canvasDragging = true;
    setCanvasCursor("grabbing");
    try {
      event.currentTarget?.setPointerCapture?.(event.pointerId);
    } catch {
      // Pointer capture is a firmness upgrade; panning still works without it.
    }
    event.preventDefault();
  };

  const hostPointerMove = (event) => {
    if (!canvasPan || event.pointerId !== canvasPan.pointerId) {
      return;
    }
    const dx = event.clientX - canvasPan.lastX;
    const dy = event.clientY - canvasPan.lastY;
    canvasPan.lastX = event.clientX;
    canvasPan.lastY = event.clientY;
    if (dx || dy) {
      queueCanvasPan(dx, dy);
    }
    event.preventDefault();
  };

  el.g6EditorHost?.addEventListener("pointerdown", hostPointerDown);
  el.g6EditorHost?.addEventListener("pointermove", hostPointerMove);
  window.addEventListener("pointerup", finishCanvasPan);
  window.addEventListener("pointercancel", finishCanvasPan);
  editor.disposeInteractions?.();
  editor.disposeInteractions = () => {
    if (canvasPanFrame) {
      window.cancelAnimationFrame(canvasPanFrame);
      canvasPanFrame = 0;
    }
    el.g6EditorHost?.removeEventListener("pointerdown", hostPointerDown);
    el.g6EditorHost?.removeEventListener("pointermove", hostPointerMove);
    window.removeEventListener("pointerup", finishCanvasPan);
    window.removeEventListener("pointercancel", finishCanvasPan);
  };

  const clearLinkDrag = () => {
    if (!linkDrag) {
      return;
    }
    linkDrag = null;
    clearConnectionPreview();
    callbacks.onConnectionDragEnd?.(null);
  };

  const finishLinkDrag = () => {
    if (!linkDrag) {
      return;
    }
    const sourceId = linkDrag.sourceId;
    const target = hoveredNodeId && hoveredNodeId !== sourceId
        ? hoveredNodeId
        : findNodeAtClientPoint(graph, sourceId, linkDrag.clientX,
            linkDrag.clientY);
    clearConnectionPreview();
    linkDrag = null;
    callbacks.onConnectionDragEnd?.(target);
    if (target) {
      callbacks.onConnectionComplete?.(sourceId, target);
    } else {
      callbacks.onConnectionCancel?.(sourceId);
    }
  };

  graph.on("node:pointerdown", (event) => {
    if (eventButton(event) !== 0) {
      return;
    }
    const id = targetId(event);
    hoveredNodeId = id || hoveredNodeId;
    hoveredInteractive = true;
    const point = eventClientPoint(event);
    if (id && isLinkHandleHit(graph, id, point.x, point.y)) {
      const sourceNode = state.nodesById.get(id);
      linkDrag = {sourceId: id};
      updateConnectionPreview(graph, sourceNode, point.x, point.y);
      callbacks.onConnectionDragStart?.(id);
      originalEvent(event)?.preventDefault?.();
      originalEvent(event)?.stopPropagation?.();
    }
  });

  graph.on("node:pointerover", (event) => {
    hoveredNodeId = targetId(event) || null;
    hoveredInteractive = true;
    if (!canvasDragging) {
      setCanvasCursor("pointer");
    }
    callbacks.onNodeHover?.(hoveredNodeId);
  });

  graph.on("node:pointerleave", (event) => {
    const id = targetId(event);
    if (hoveredNodeId === id) {
      hoveredNodeId = null;
      callbacks.onNodeHover?.(null);
    }
    hoveredInteractive = false;
    if (!canvasDragging) {
      setCanvasCursor("grab");
    }
  });

  graph.on("node:dragstart", (event) => {
    draggedNodeId = targetId(event);
    dragged = false;
    if (draggedNodeId) {
      callbacks.onNodeDragStart?.(draggedNodeId);
    }
  });

  graph.on("node:drag", (event) => {
    const id = targetId(event) || draggedNodeId;
    if (!id) {
      return;
    }
    dragged = true;
    const position = nodePositionFromGraph(graph, id);
    if (position) {
      callbacks.onNodeDrag?.(id, position);
    }
  });

  graph.on("node:dragend", (event) => {
    const id = targetId(event) || draggedNodeId;
    const position = id ? nodePositionFromGraph(graph, id) : null;
    if (id && position) {
      callbacks.onNodeDragEnd?.(id, position, {moved: dragged});
      if (dragged) {
        lastClickSuppressedNodeId = id;
        window.setTimeout(() => {
          if (lastClickSuppressedNodeId === id) {
            lastClickSuppressedNodeId = null;
          }
        }, 120);
      }
    }
    draggedNodeId = null;
    dragged = false;
  });

  graph.on("node:click", (event) => {
    const id = targetId(event);
    if (!id || lastClickSuppressedNodeId === id) {
      lastClickSuppressedNodeId = null;
      return;
    }
    callbacks.onNodeClick?.(id, originalEvent(event));
  });

  graph.on("node:dblclick", (event) => {
    const id = targetId(event);
    if (!id) {
      return;
    }
    callbacks.onNodeDoubleClick?.(id, originalEvent(event));
  });

  graph.on("edge:pointerover", (event) => {
    hoveredInteractive = true;
    if (!canvasDragging) {
      setCanvasCursor("pointer");
    }
    callbacks.onEdgeHover?.(targetId(event) || null);
  });

  graph.on("edge:pointerleave", () => {
    hoveredInteractive = false;
    if (!canvasDragging) {
      setCanvasCursor("grab");
    }
    callbacks.onEdgeHover?.(null);
  });

  graph.on("edge:click", (event) => {
    const id = targetId(event);
    if (id) {
      callbacks.onEdgeClick?.(id, originalEvent(event));
    }
  });

  graph.on("canvas:click", (event) => {
    if (linkDrag) {
      return;
    }
    callbacks.onCanvasClick?.(originalEvent(event));
  });

  graph.on("canvas:pointerdown", (event) => {
    if (eventButton(event) !== 0 || linkDrag) {
      return;
    }
    hoveredInteractive = false;
  });

  graph.on("canvas:pointerup", () => {
    finishCanvasPan();
  });

  graph.on("canvas:pointermove", (event) => {
    const point = eventClientPoint(event);
    if (linkDrag) {
      linkDrag.clientX = point.x;
      linkDrag.clientY = point.y;
      updateConnectionPreview(graph, state.nodesById.get(linkDrag.sourceId),
          point.x, point.y);
      callbacks.onConnectionPointerMove?.(linkDrag.sourceId, hoveredNodeId);
      return;
    }
    if (!canvasDragging && !hoveredInteractive) {
      setCanvasCursor("grab");
    }
    callbacks.onCanvasPointerMove?.(point.x, point.y);
  });

  graph.on("aftertransform", () => {
    callbacks.onViewportChange?.();
  });

  window.addEventListener("pointerup", () => {
    if (linkDrag) {
      finishLinkDrag();
    }
  });

  window.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && linkDrag) {
      event.preventDefault();
      clearLinkDrag();
    }
  });
}
