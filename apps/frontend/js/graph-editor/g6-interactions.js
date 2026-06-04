import {state} from '../state.js';
import {el} from '../dom.js';
import {nodeSizeForDiagram} from './g6-style.js';
import {
  clearConnectionPreview,
  updateConnectionPreview
} from './g6-overlays.js';
import {fingerprintElement, scheduleGraphDraw} from './g6-performance.js';

let currentCanvasCursorMode = "";

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

function targetId(event, editor = null) {
  const target = event?.target;
  const candidates = [
    target?.idOf?.(),
    target?.owner?.id,
    target?.parentElement?.id,
    target?.parentNode?.id,
    target?.id,
    event?.item?.id,
    event?.element?.id
  ];
  return candidates.find((id) => state.nodesById.has(id)
      || editor?.dataSnapshot?.edgesById?.has?.(id)) || "";
}

function nodeIdFromEvent(event, editor, sourceId = null) {
  const id = targetId(event, editor);
  if (state.nodesById.has(id)) {
    return id;
  }
  const point = eventClientPoint(event);
  return findNodeAtClientPoint(editor, sourceId, point.x, point.y) || "";
}

function setCanvasCursor(mode) {
  const nextMode = mode || "grab";
  if (currentCanvasCursorMode === nextMode) {
    return;
  }
  currentCanvasCursorMode = nextMode;
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
  const cursor = nextMode === "grabbing" ? "grabbing"
      : nextMode === "pointer" ? "pointer" : "grab";
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

function findNodeAtClientPoint(editor, sourceId, clientX, clientY) {
  if (!Number.isFinite(clientX) || !Number.isFinite(clientY)) {
    return null;
  }
  const graph = editor?.graph;
  const point = graphCanvasPoint(graph, clientX, clientY);
  if (editor?.spatialIndex?.size?.()) {
    return editor.spatialIndex.findAt(point.x, point.y, {
      excludeId: sourceId || ""
    });
  }
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

function openControlBounds(graph, nodeId) {
  const topLeft = graphNodeTopLeft(graph, nodeId);
  const data = graph?.getNodeData?.(nodeId);
  const detailLevel = String(data?.style?.detailLevel
      || data?.data?.detailLevel || "");
  const low = detailLevel ? detailLevel === "low"
      : (Number(state.viewport.scale) || 1) < 0.35;
  if (!topLeft) {
    return null;
  }
  const controlWidth = low ? 30 : 38;
  const controlHeight = low ? 14 : 16;
  return {
    x: topLeft.x + topLeft.width - controlWidth - 9,
    y: topLeft.y + (low ? 8 : 12),
    width: controlWidth,
    height: controlHeight
  };
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

function moveGraphNode(editor, nodeId, x, y) {
  const graph = editor?.graph;
  const data = graph?.getNodeData?.(nodeId);
  if (!data) {
    return;
  }
  const width = Number(data?.style?.width) || nodeSizeForDiagram(
      state.activeType).width;
  const height = Number(data?.style?.height) || nodeSizeForDiagram(
      state.activeType).height;
  const roundedX = Math.round(x);
  const roundedY = Math.round(y);
  graph.updateNodeData?.([{
    id: nodeId,
    style: {
      ...(data.style || {}),
      x: Math.round(roundedX + width / 2),
      y: Math.round(roundedY + height / 2)
    }
  }]);
  const snapshotNode = editor?.dataSnapshot?.nodesById?.get?.(nodeId);
  if (snapshotNode) {
    const nextSnapshotNode = {
      ...snapshotNode,
      style: {
        ...(snapshotNode.style || {}),
        x: Math.round(roundedX + width / 2),
        y: Math.round(roundedY + height / 2)
      }
    };
    editor.dataSnapshot.nodesById.set(nodeId, nextSnapshotNode);
    editor.dataSnapshot.nodeFingerprints?.set?.(nodeId,
        fingerprintElement(nextSnapshotNode));
  }
  editor?.spatialIndex?.update?.({
    id: nodeId,
    x: roundedX,
    y: roundedY,
    width,
    height
  });
  scheduleGraphDraw(graph);
}

function isLinkHandleVisible(editor, nodeId) {
  return editor?.hoveredNodeId === nodeId
      || state.hoveredNodeId === nodeId
      || state.selectedNodeIds?.has?.(nodeId)
      || state.selectedNodeId === nodeId
      || state.connectSourceId === nodeId
      || state.linkDrag?.sourceId === nodeId;
}

function isLinkHandleHit(graph, nodeId, clientX, clientY, editor) {
  const topLeft = graphNodeTopLeft(graph, nodeId);
  if (!topLeft || !isLinkHandleVisible(editor, nodeId)) {
    return false;
  }
  const point = graphCanvasPoint(graph, clientX, clientY);
  const centerY = topLeft.y + topLeft.height / 2;
  const leftDistance = Math.hypot(point.x - topLeft.x, point.y - centerY);
  const rightDistance = Math.hypot(point.x - (topLeft.x + topLeft.width),
      point.y - centerY);
  return Math.min(leftDistance, rightDistance) <= 16;
}

function isOpenControlHit(graph, nodeId, clientX, clientY, editor) {
  const node = state.nodesById.get(nodeId);
  const bounds = openControlBounds(graph, nodeId);
  if (!bounds || !node || !editor?.mapperOptions?.isContainer?.(node)) {
    return false;
  }
  const point = graphCanvasPoint(graph, clientX, clientY);
  const zoom = Math.max(0.01, Number(state.viewport.scale) || 1);
  const padding = Math.max(3, 6 / zoom);
  return point.x >= bounds.x - padding
      && point.x <= bounds.x + bounds.width + padding
      && point.y >= bounds.y - padding
      && point.y <= bounds.y + bounds.height + padding;
}

export function bindG6Interactions(editor, callbacks = {}) {
  const graph = editor.graph;
  if (!graph) {
    return;
  }
  let draggedNodeId = null;
  let dragged = false;
  let linkDrag = null;
  let nodeDrag = null;
  let hoveredNodeId = null;
  let hoveredInteractive = false;
  let canvasDragging = false;
  let canvasPan = null;
  let canvasPanFrame = 0;
  let canvasPanDx = 0;
  let canvasPanDy = 0;
  let lastClickSuppressedNodeId = null;
  let nodeDragFrame = 0;
  let pendingNodeDragMove = null;
  let finishLinkDragWindow = null;
  let escapeKeyDown = null;
  let openControlPress = null;
  let suppressOpenControlClickNodeId = null;

  currentCanvasCursorMode = "";
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
      result?.then?.(() => scheduleGraphDraw(graph))?.catch?.(() =>
          scheduleGraphDraw(graph));
      if (!result?.then) {
        scheduleGraphDraw(graph);
      }
    } catch {
      scheduleGraphDraw(graph);
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
    if (findNodeAtClientPoint(editor, null, event.clientX, event.clientY)) {
      return;
    }
    callbacks.onCanvasPointerDown?.(event);
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

  const flushNodeDragMove = () => {
    if (nodeDragFrame) {
      window.cancelAnimationFrame(nodeDragFrame);
      nodeDragFrame = 0;
    }
    const move = pendingNodeDragMove;
    pendingNodeDragMove = null;
    if (!move) {
      return;
    }
    moveGraphNode(editor, move.nodeId, move.x, move.y);
    callbacks.onNodeDrag?.(move.nodeId, {x: move.x, y: move.y});
  };

  const queueNodeDragMove = (nodeId, x, y) => {
    pendingNodeDragMove = {nodeId, x, y};
    if (!nodeDragFrame) {
      nodeDragFrame = window.requestAnimationFrame(flushNodeDragMove);
    }
  };

  const hostPointerMove = (event) => {
    const setHoveredOpenControl = (nodeId) => {
      editor?.setOpenControlHover?.(nodeId || null);
    };
    if (nodeDrag && (!nodeDrag.pointerId
        || event.pointerId === nodeDrag.pointerId)) {
      setHoveredOpenControl(null);
      const point = graphCanvasPoint(graph, event.clientX, event.clientY);
      const nextX = Math.round(nodeDrag.nodeX + point.x - nodeDrag.startX);
      const nextY = Math.round(nodeDrag.nodeY + point.y - nodeDrag.startY);
      const moved = Math.abs(nextX - nodeDrag.nodeX) > 1
          || Math.abs(nextY - nodeDrag.nodeY) > 1;
      if (moved) {
        dragged = true;
      }
      queueNodeDragMove(nodeDrag.nodeId, nextX, nextY);
      event.preventDefault();
      return;
    }
    if (linkDrag) {
      setHoveredOpenControl(null);
      const point = eventClientPoint(event);
      linkDrag.clientX = point.x;
      linkDrag.clientY = point.y;
      hoveredNodeId = findNodeAtClientPoint(editor, linkDrag.sourceId,
          point.x, point.y);
      updateConnectionPreview(graph, state.nodesById.get(linkDrag.sourceId),
          point.x, point.y);
      callbacks.onConnectionPointerMove?.(linkDrag.sourceId, hoveredNodeId);
      event.preventDefault();
      return;
    }
    if (!canvasPan || event.pointerId !== canvasPan.pointerId) {
      const hoveredId = findNodeAtClientPoint(editor, null, event.clientX,
          event.clientY);
      if (hoveredId !== hoveredNodeId) {
        hoveredNodeId = hoveredId || null;
        hoveredInteractive = Boolean(hoveredNodeId);
        callbacks.onNodeHover?.(hoveredNodeId);
        setCanvasCursor(hoveredNodeId ? "pointer" : "grab");
      }
      const openHover = hoveredId
      && isOpenControlHit(graph, hoveredId, event.clientX, event.clientY,
          editor)
          ? hoveredId : null;
      setHoveredOpenControl(openHover);
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

  const hostPointerLeave = () => {
    editor?.setOpenControlHover?.(null);
    if (!linkDrag && !nodeDrag && !canvasPan) {
      hoveredNodeId = null;
      hoveredInteractive = false;
      callbacks.onNodeHover?.(null);
      setCanvasCursor("grab");
    }
  };

  el.g6EditorHost?.addEventListener("pointerdown", hostPointerDown);
  el.g6EditorHost?.addEventListener("pointermove", hostPointerMove);
  el.g6EditorHost?.addEventListener("pointerleave", hostPointerLeave);
  window.addEventListener("pointerup", finishCanvasPan);
  window.addEventListener("pointercancel", finishCanvasPan);
  const finishNodeDrag = () => {
    if (!nodeDrag) {
      return;
    }
    flushNodeDragMove();
    const nodeId = nodeDrag.nodeId;
    const position = nodePositionFromGraph(graph, nodeId);
    callbacks.onNodeDragEnd?.(nodeId, position, {moved: dragged});
    lastClickSuppressedNodeId = nodeId;
    window.setTimeout(() => {
      if (lastClickSuppressedNodeId === nodeId) {
        lastClickSuppressedNodeId = null;
      }
    }, 120);
    if (!dragged) {
      callbacks.onNodeClick?.(nodeId, nodeDrag.startEvent);
    }
    if (nodeDrag.pointerId) {
      try {
        el.g6EditorHost?.releasePointerCapture?.(nodeDrag.pointerId);
      } catch {
        // Pointer capture may already be released.
      }
    }
    nodeDrag = null;
    draggedNodeId = null;
    dragged = false;
  };
  window.addEventListener("pointerup", finishNodeDrag);
  window.addEventListener("pointercancel", finishNodeDrag);
  const finishOpenControlPress = (event) => {
    if (!openControlPress) {
      return;
    }
    const press = openControlPress;
    openControlPress = null;
    const source = originalEvent(event);
    const clientX = Number(source?.clientX);
    const clientY = Number(source?.clientY);
    const moved = Math.hypot(clientX - press.clientX,
        clientY - press.clientY) > 5;
    if (!moved && isOpenControlHit(graph, press.nodeId, clientX, clientY,
        editor)) {
      source?.preventDefault?.();
      source?.stopPropagation?.();
      suppressOpenControlClickNodeId = press.nodeId;
      window.setTimeout(() => {
        if (suppressOpenControlClickNodeId === press.nodeId) {
          suppressOpenControlClickNodeId = null;
        }
      }, 120);
      callbacks.onOpenContainer?.(press.nodeId);
    }
  };
  const cancelOpenControlPress = () => {
    openControlPress = null;
  };
  window.addEventListener("pointerup", finishOpenControlPress);
  window.addEventListener("pointercancel", cancelOpenControlPress);
  editor.disposeInteractions?.();
  editor.disposeInteractions = () => {
    if (canvasPanFrame) {
      window.cancelAnimationFrame(canvasPanFrame);
      canvasPanFrame = 0;
    }
    if (nodeDragFrame) {
      window.cancelAnimationFrame(nodeDragFrame);
      nodeDragFrame = 0;
    }
    pendingNodeDragMove = null;
    el.g6EditorHost?.removeEventListener("pointerdown", hostPointerDown);
    el.g6EditorHost?.removeEventListener("pointermove", hostPointerMove);
    el.g6EditorHost?.removeEventListener("pointerleave", hostPointerLeave);
    window.removeEventListener("pointerup", finishCanvasPan);
    window.removeEventListener("pointercancel", finishCanvasPan);
    window.removeEventListener("pointerup", finishNodeDrag);
    window.removeEventListener("pointercancel", finishNodeDrag);
    window.removeEventListener("pointerup", finishOpenControlPress);
    window.removeEventListener("pointercancel", cancelOpenControlPress);
    if (finishLinkDragWindow) {
      window.removeEventListener("pointerup", finishLinkDragWindow);
    }
    if (escapeKeyDown) {
      window.removeEventListener("keydown", escapeKeyDown);
    }
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
        : findNodeAtClientPoint(editor, sourceId, linkDrag.clientX,
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
    const id = nodeIdFromEvent(event, editor);
    hoveredNodeId = id || hoveredNodeId;
    hoveredInteractive = true;
    const point = eventClientPoint(event);
    if (id && isOpenControlHit(graph, id, point.x, point.y, editor)) {
      openControlPress = {
        nodeId: id,
        pointerId: originalEvent(event)?.pointerId,
        clientX: point.x,
        clientY: point.y
      };
      editor?.setOpenControlHover?.(id);
      originalEvent(event)?.preventDefault?.();
      originalEvent(event)?.stopPropagation?.();
      return;
    }
    if (id && isLinkHandleHit(graph, id, point.x, point.y, editor)) {
      const sourceNode = state.nodesById.get(id);
      linkDrag = {sourceId: id, clientX: point.x, clientY: point.y};
      updateConnectionPreview(graph, sourceNode, point.x, point.y);
      callbacks.onConnectionDragStart?.(id);
      originalEvent(event)?.preventDefault?.();
      originalEvent(event)?.stopPropagation?.();
      return;
    }
    if (id) {
      const canvasPoint = graphCanvasPoint(graph, point.x, point.y);
      const topLeft = graphNodeTopLeft(graph, id);
      if (topLeft) {
        draggedNodeId = id;
        dragged = false;
        nodeDrag = {
          nodeId: id,
          pointerId: originalEvent(event)?.pointerId,
          startEvent: originalEvent(event),
          startX: canvasPoint.x,
          startY: canvasPoint.y,
          nodeX: topLeft.x,
          nodeY: topLeft.y
        };
        callbacks.onNodeDragStart?.(id);
        if (nodeDrag.pointerId) {
          try {
            el.g6EditorHost?.setPointerCapture?.(nodeDrag.pointerId);
          } catch {
            // Drag still works without capture inside the canvas.
          }
        }
        originalEvent(event)?.preventDefault?.();
        originalEvent(event)?.stopPropagation?.();
      }
    }
  });

  graph.on("node:pointerover", (event) => {
    hoveredNodeId = nodeIdFromEvent(event, editor) || null;
    hoveredInteractive = true;
    const point = eventClientPoint(event);
    editor?.setOpenControlHover?.(hoveredNodeId
    && isOpenControlHit(graph, hoveredNodeId, point.x, point.y, editor)
        ? hoveredNodeId : null);
    if (!canvasDragging) {
      setCanvasCursor("pointer");
    }
    callbacks.onNodeHover?.(hoveredNodeId);
  });

  graph.on("node:pointerleave", (event) => {
    hoveredNodeId = null;
    editor?.setOpenControlHover?.(null);
    callbacks.onNodeHover?.(null);
    hoveredInteractive = false;
    if (!canvasDragging) {
      setCanvasCursor("grab");
    }
  });

  graph.on("node:dragstart", (event) => {
    if (nodeDrag) {
      return;
    }
    if (linkDrag) {
      draggedNodeId = null;
      dragged = false;
      return;
    }
    draggedNodeId = nodeIdFromEvent(event, editor);
    const point = eventClientPoint(event);
    if (draggedNodeId && isOpenControlHit(graph, draggedNodeId, point.x,
        point.y, editor)) {
      draggedNodeId = null;
      dragged = false;
      return;
    }
    dragged = false;
    if (draggedNodeId) {
      callbacks.onNodeDragStart?.(draggedNodeId);
    }
  });

  graph.on("node:drag", (event) => {
    if (linkDrag || nodeDrag) {
      return;
    }
    const id = draggedNodeId || nodeIdFromEvent(event, editor);
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
    if (linkDrag || nodeDrag) {
      return;
    }
    const id = draggedNodeId || nodeIdFromEvent(event, editor);
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
    const id = nodeIdFromEvent(event, editor);
    if (!id || lastClickSuppressedNodeId === id) {
      lastClickSuppressedNodeId = null;
      return;
    }
    const point = eventClientPoint(event);
    if (isOpenControlHit(graph, id, point.x, point.y, editor)) {
      openControlPress = null;
      originalEvent(event)?.preventDefault?.();
      originalEvent(event)?.stopPropagation?.();
      if (suppressOpenControlClickNodeId === id) {
        suppressOpenControlClickNodeId = null;
        return;
      }
      callbacks.onOpenContainer?.(id);
      return;
    }
    callbacks.onNodeClick?.(id, originalEvent(event));
  });

  graph.on("node:pointermove", (event) => {
    if (!linkDrag) {
      return;
    }
    const point = eventClientPoint(event);
    linkDrag.clientX = point.x;
    linkDrag.clientY = point.y;
    hoveredNodeId = nodeIdFromEvent(event, editor, linkDrag.sourceId) || null;
    updateConnectionPreview(graph, state.nodesById.get(linkDrag.sourceId),
        point.x, point.y);
    callbacks.onConnectionPointerMove?.(linkDrag.sourceId, hoveredNodeId);
  });

  graph.on("node:dblclick", (event) => {
    const id = nodeIdFromEvent(event, editor);
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
    callbacks.onEdgeHover?.(targetId(event, editor) || null);
  });

  graph.on("edge:pointerleave", () => {
    hoveredInteractive = false;
    if (!canvasDragging) {
      setCanvasCursor("grab");
    }
    callbacks.onEdgeHover?.(null);
  });

  graph.on("edge:click", (event) => {
    const id = targetId(event, editor);
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
    callbacks.onCanvasPointerDown?.(originalEvent(event));
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

  finishLinkDragWindow = () => {
    if (linkDrag) {
      finishLinkDrag();
    }
  };
  window.addEventListener("pointerup", finishLinkDragWindow);

  escapeKeyDown = (event) => {
    if (event.key === "Escape" && linkDrag) {
      event.preventDefault();
      clearLinkDrag();
    }
  };
  window.addEventListener("keydown", escapeKeyDown);
}
