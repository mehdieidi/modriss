import { state } from "../state.js";
import { nodeTypeLabel } from "./g6-mapper.js";
import { modelingElementDefinition } from "../modeling-config-data.js";
import { el } from "../dom.js";
import { nodeSizeForDiagram } from "./g6-style.js";
import {
  isPointInNodeInteractionBounds,
  isPointInOpenControlBounds,
  isPointInOpenInteractionZone,
  linkHandlePointsForNode,
  openControlBoundsForNode,
} from "./icon-node-metrics.js";
import { clearConnectionPreview, updateConnectionPreview } from "./g6-overlays.js";
import { fingerprintElement, scheduleGraphDraw } from "./g6-performance.js";

let currentCanvasCursorMode = "";

function originalEvent(event) {
  return event?.originalEvent || event;
}

function eventButton(event) {
  const source = originalEvent(event);
  return Number.isFinite(source?.button) ? source.button : 0;
}

function eventPointerId(event) {
  const id = originalEvent(event)?.pointerId;
  return Number.isFinite(id) ? id : null;
}

function eventClientPoint(event) {
  const source = originalEvent(event);
  return {
    x: Number(source?.clientX),
    y: Number(source?.clientY),
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
    event?.element?.id,
  ];
  return (
    candidates.find(
      (id) => state.nodesById.has(id) || editor?.dataSnapshot?.edgesById?.has?.(id),
    ) || ""
  );
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
  const classes = ["canvas-cursor-grab", "canvas-cursor-grabbing", "canvas-cursor-pointer"];
  const targets = [el.canvasViewport, el.canvasGrid, el.g6EditorHost].filter(Boolean);
  targets.forEach((target) => {
    target.classList.remove(...classes);
    if (mode) {
      target.classList.add(`canvas-cursor-${mode}`);
    }
  });
  const cursor = nextMode === "grabbing" ? "grabbing" : nextMode === "pointer" ? "pointer" : "grab";
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
      converted = graph?.getCanvasByClient?.({ x: clientX, y: clientY });
    } catch {
      converted = null;
    }
  }
  if (Array.isArray(converted)) {
    return { x: converted[0], y: converted[1] };
  }
  if (converted && Number.isFinite(converted.x) && Number.isFinite(converted.y)) {
    return converted;
  }
  const rect = graph?.getCanvas?.()?.getContainer?.()?.getBoundingClientRect?.();
  const localX = clientX - (rect?.left || 0);
  const localY = clientY - (rect?.top || 0);
  return {
    x: (localX - state.viewport.x) / state.viewport.scale,
    y: (localY - state.viewport.y) / state.viewport.scale,
  };
}

function safeGraphNodeData(graph, nodeId, editor = null) {
  if (!nodeId) {
    return null;
  }
  const snapshot = editor?.dataSnapshot?.nodesById?.get?.(nodeId);
  if (snapshot) {
    return snapshot;
  }
  try {
    return graph?.getNodeData?.(nodeId) || null;
  } catch {
    return null;
  }
}

function isActiveDiagramNode(nodeId) {
  return Boolean(nodeId && state.nodesById.has(nodeId));
}

function nodeDetailLevel(graph, nodeId, editor = null) {
  const data = safeGraphNodeData(graph, nodeId, editor);
  const detailLevel = String(data?.style?.detailLevel || data?.data?.detailLevel || "");
  return detailLevel ? detailLevel === "low" : (Number(state.viewport.scale) || 1) < 0.35;
}

function nodeLabelText(nodeId) {
  const semanticNode = state.nodesById.get(nodeId);
  return semanticNode?.label || semanticNode?.id || nodeId;
}

function nodeKindText(graph, nodeId, editor = null) {
  const semanticNode = state.nodesById.get(nodeId);
  const data = safeGraphNodeData(graph, nodeId, editor);
  const fromRender = semanticNode?.kindText || data?.style?.kindText || data?.data?.kindText || "";
  if (String(fromRender).trim()) {
    return fromRender;
  }
  const nodeType =
    semanticNode?.type || data?.data?.nodeType || data?.style?.nodeType || semanticNode?.type || "";
  const definition = modelingElementDefinition(state.activeType, nodeType);
  return nodeTypeLabel(definition, nodeType);
}

function isPointInNodeInteraction(graph, nodeId, clientX, clientY, editor) {
  if (!isActiveDiagramNode(nodeId)) {
    return false;
  }
  const topLeft = graphNodeTopLeft(graph, nodeId, editor);
  if (!topLeft) {
    return false;
  }
  const point = graphCanvasPoint(graph, clientX, clientY);
  const data = safeGraphNodeData(graph, nodeId, editor);
  const semanticNode = state.nodesById.get(nodeId);
  const isContainer =
    Boolean(data?.style?.isContainer) ||
    Boolean(data?.data?.container) ||
    Boolean(editor?.mapperOptions?.isContainer?.(semanticNode));
  return isPointInNodeInteractionBounds(
    point.x,
    point.y,
    topLeft.x,
    topLeft.y,
    topLeft.width,
    topLeft.height,
    nodeDetailLevel(graph, nodeId, editor),
    nodeLabelText(nodeId),
    { isContainer, kindText: nodeKindText(graph, nodeId, editor) },
  );
}

function isPointInOpenZone(graph, nodeId, clientX, clientY, editor) {
  if (!isActiveDiagramNode(nodeId)) {
    return false;
  }
  const node = state.nodesById.get(nodeId);
  const topLeft = graphNodeTopLeft(graph, nodeId, editor);
  if (!topLeft || !node || !editor?.mapperOptions?.isContainer?.(node)) {
    return false;
  }
  const point = graphCanvasPoint(graph, clientX, clientY);
  const low = nodeDetailLevel(graph, nodeId, editor);
  const labelText = nodeLabelText(nodeId);
  const zoom = Math.max(0.01, Number(state.viewport.scale) || 1);
  const padding = Math.max(6, 12 / zoom);
  return isPointInOpenInteractionZone(
    point.x,
    point.y,
    topLeft.x,
    topLeft.y,
    topLeft.width,
    topLeft.height,
    low,
    labelText,
    padding,
  );
}

function findNodeAtClientPoint(editor, sourceId, clientX, clientY) {
  if (!Number.isFinite(clientX) || !Number.isFinite(clientY)) {
    return null;
  }
  const graph = editor?.graph;
  const point = graphCanvasPoint(graph, clientX, clientY);
  if (editor?.spatialIndex?.size?.()) {
    const candidate = editor.spatialIndex.findAt(point.x, point.y, {
      excludeId: sourceId || "",
    });
    if (candidate && isPointInNodeInteraction(graph, candidate, clientX, clientY, editor)) {
      return candidate;
    }
  }
  let best = null;
  for (const [nodeId, node] of state.nodesById.entries()) {
    if (nodeId === sourceId) {
      continue;
    }
    const size = nodeSizeForDiagram(state.activeType, node);
    const width = Number(node?.width) || size.width;
    const height = Number(node?.height) || size.height;
    const low = nodeDetailLevel(graph, nodeId, editor);
    const labelText = node.label || node.id || nodeId;
    const isContainer = Boolean(editor?.mapperOptions?.isContainer?.(node));
    const kindText = nodeKindText(graph, nodeId, editor);
    if (
      isPointInNodeInteractionBounds(
        point.x,
        point.y,
        node.x,
        node.y,
        width,
        height,
        low,
        labelText,
        { isContainer, kindText },
      )
    ) {
      best = nodeId;
    }
  }
  return best;
}

function graphNodeTopLeft(graph, nodeId, editor = null) {
  const semanticNode = state.nodesById.get(nodeId);
  if (!semanticNode) {
    return null;
  }
  const data = safeGraphNodeData(graph, nodeId, editor);
  const size = nodeSizeForDiagram(
    state.activeType,
    semanticNode || data?.data?.nodeType || data?.style?.nodeType,
  );
  const x = Number(data?.style?.x);
  const y = Number(data?.style?.y);
  if (!Number.isFinite(x) || !Number.isFinite(y)) {
    return { x: semanticNode.x, y: semanticNode.y, width: size.width, height: size.height };
  }
  const width = Number(data?.style?.width) || size.width;
  const height = Number(data?.style?.height) || size.height;
  return { x: x - width / 2, y: y - height / 2, width, height };
}

function openControlBounds(graph, nodeId, editor = null) {
  const topLeft = graphNodeTopLeft(graph, nodeId, editor);
  const data = safeGraphNodeData(graph, nodeId, editor);
  const semanticNode = state.nodesById.get(nodeId);
  const detailLevel = String(data?.style?.detailLevel || data?.data?.detailLevel || "");
  const low = detailLevel ? detailLevel === "low" : (Number(state.viewport.scale) || 1) < 0.35;
  if (!topLeft) {
    return null;
  }
  const labelText = semanticNode?.label || semanticNode?.id || nodeId;
  return openControlBoundsForNode(topLeft.x, topLeft.y, topLeft.width, low, labelText);
}

function nodePositionFromGraph(graph, nodeId, editor = null) {
  const topLeft = graphNodeTopLeft(graph, nodeId, editor);
  if (!topLeft) {
    return null;
  }
  return {
    x: Math.round(topLeft.x),
    y: Math.round(topLeft.y),
  };
}

function moveGraphNode(editor, nodeId, x, y) {
  const graph = editor?.graph;
  const data = safeGraphNodeData(graph, nodeId, editor);
  if (!data) {
    return;
  }
  const semanticNode = state.nodesById.get(nodeId);
  const size = nodeSizeForDiagram(state.activeType, semanticNode);
  const width = Number(data?.style?.width) || size.width;
  const height = Number(data?.style?.height) || size.height;
  const roundedX = Math.round(x);
  const roundedY = Math.round(y);
  graph.updateNodeData?.([
    {
      id: nodeId,
      style: {
        ...(data.style || {}),
        x: Math.round(roundedX + width / 2),
        y: Math.round(roundedY + height / 2),
      },
    },
  ]);
  const snapshotNode = editor?.dataSnapshot?.nodesById?.get?.(nodeId);
  if (snapshotNode) {
    const nextSnapshotNode = {
      ...snapshotNode,
      style: {
        ...(snapshotNode.style || {}),
        x: Math.round(roundedX + width / 2),
        y: Math.round(roundedY + height / 2),
      },
    };
    editor.dataSnapshot.nodesById.set(nodeId, nextSnapshotNode);
    editor.dataSnapshot.nodeFingerprints?.set?.(nodeId, fingerprintElement(nextSnapshotNode));
  }
  editor?.spatialIndex?.update?.({
    id: nodeId,
    x: roundedX,
    y: roundedY,
    width,
    height,
  });
  scheduleGraphDraw(graph);
}

function isLinkHandleVisible(editor, nodeId) {
  return (
    editor?.hoveredNodeId === nodeId ||
    state.hoveredNodeId === nodeId ||
    state.selectedNodeIds?.has?.(nodeId) ||
    state.selectedNodeId === nodeId ||
    state.connectSourceId === nodeId ||
    state.linkDrag?.sourceId === nodeId
  );
}

function isLinkHandleHit(graph, nodeId, clientX, clientY, editor) {
  if (!isActiveDiagramNode(nodeId)) {
    return false;
  }
  const topLeft = graphNodeTopLeft(graph, nodeId, editor);
  if (!topLeft || !isLinkHandleVisible(editor, nodeId)) {
    return false;
  }
  const point = graphCanvasPoint(graph, clientX, clientY);
  const semanticNode = state.nodesById.get(nodeId);
  const low = nodeDetailLevel(graph, nodeId, editor);
  const labelText = semanticNode?.label || semanticNode?.id || nodeId;
  const handles = linkHandlePointsForNode(
    topLeft.x,
    topLeft.y,
    topLeft.width,
    topLeft.height,
    low,
    labelText,
  );
  const leftDistance = Math.hypot(point.x - handles.left.x, point.y - handles.left.y);
  const rightDistance = Math.hypot(point.x - handles.right.x, point.y - handles.right.y);
  return Math.min(leftDistance, rightDistance) <= 14;
}

function isOpenControlHit(graph, nodeId, clientX, clientY, editor) {
  if (!isActiveDiagramNode(nodeId)) {
    return false;
  }
  const node = state.nodesById.get(nodeId);
  const topLeft = graphNodeTopLeft(graph, nodeId, editor);
  if (!topLeft || !node || !editor?.mapperOptions?.isContainer?.(node)) {
    return false;
  }
  const point = graphCanvasPoint(graph, clientX, clientY);
  const low = nodeDetailLevel(graph, nodeId, editor);
  const labelText = nodeLabelText(nodeId);
  const zoom = Math.max(0.01, Number(state.viewport.scale) || 1);
  const padding = Math.max(4, 8 / zoom);
  return isPointInOpenControlBounds(
    point.x,
    point.y,
    topLeft.x,
    topLeft.y,
    topLeft.width,
    low,
    labelText,
    padding,
  );
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
  let visibilityChange = null;
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
      graph.translateBy?.([dx, dy], false);
    } catch {
      scheduleGraphDraw(graph);
    }
    callbacks.onViewportTranslate?.();
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
    if (canvasPan.pointerId != null) {
      try {
        el.g6EditorHost?.releasePointerCapture?.(canvasPan.pointerId);
      } catch {
        // Capture may already be released after pointerup outside the host.
      }
    }
    canvasPan = null;
    canvasDragging = false;
    setCanvasCursor(hoveredInteractive ? "pointer" : "grab");
  };

  const hostPointerDown = (event) => {
    if (event.button !== 0 || linkDrag) {
      return;
    }
    const nodeId = findNodeAtClientPoint(editor, null, event.clientX, event.clientY);
    if (nodeId) {
      beginNodePointerDown(nodeId, event);
      event.preventDefault();
      event.stopPropagation();
      return;
    }
    if (hoveredInteractive) {
      return;
    }
    callbacks.onCanvasPointerDown?.(event);
    canvasPan = {
      pointerId: event.pointerId,
      lastX: event.clientX,
      lastY: event.clientY,
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

  const beginNodePointerDown = (id, event) => {
    hoveredNodeId = id;
    hoveredInteractive = true;
    const point = eventClientPoint(event);
    if (isOpenControlHit(graph, id, point.x, point.y, editor)) {
      openControlPress = {
        nodeId: id,
        pointerId: originalEvent(event)?.pointerId,
        clientX: point.x,
        clientY: point.y,
      };
      editor?.setOpenControlHover?.(id);
      originalEvent(event)?.preventDefault?.();
      originalEvent(event)?.stopPropagation?.();
      return;
    }
    if (isLinkHandleHit(graph, id, point.x, point.y, editor)) {
      const sourceNode = state.nodesById.get(id);
      linkDrag = { sourceId: id, clientX: point.x, clientY: point.y };
      updateConnectionPreview(graph, sourceNode, point.x, point.y);
      callbacks.onConnectionDragStart?.(id);
      originalEvent(event)?.preventDefault?.();
      originalEvent(event)?.stopPropagation?.();
      return;
    }
    if (isPointInNodeInteraction(graph, id, point.x, point.y, editor)) {
      const canvasPoint = graphCanvasPoint(graph, point.x, point.y);
      const topLeft = graphNodeTopLeft(graph, id, editor);
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
          nodeY: topLeft.y,
        };
        callbacks.onNodeDragStart?.(id);
        if (nodeDrag.pointerId != null) {
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
    callbacks.onNodeDrag?.(move.nodeId, { x: move.x, y: move.y });
  };

  const queueNodeDragMove = (nodeId, x, y) => {
    pendingNodeDragMove = { nodeId, x, y };
    if (!nodeDragFrame) {
      nodeDragFrame = window.requestAnimationFrame(flushNodeDragMove);
    }
  };

  const clearPointerHoverState = () => {
    hoveredNodeId = null;
    hoveredInteractive = false;
    openControlPress = null;
    editor?.setOpenControlHover?.(null);
    callbacks.onNodeHover?.(null);
    if (!canvasDragging) {
      setCanvasCursor("grab");
    }
  };

  const eventMatchesNodeDrag = (event) => {
    if (!nodeDrag) {
      return false;
    }
    const pointerId = eventPointerId(event);
    return nodeDrag.pointerId == null || pointerId == null || pointerId === nodeDrag.pointerId;
  };

  const finishNodeDrag = (event = null, { cancelClick = false } = {}) => {
    if (!nodeDrag || !eventMatchesNodeDrag(event)) {
      return;
    }
    flushNodeDragMove();
    const dragState = nodeDrag;
    const nodeId = dragState.nodeId;
    const wasDragged = dragged;
    nodeDrag = null;
    draggedNodeId = null;
    dragged = false;
    const position = nodePositionFromGraph(graph, nodeId, editor);
    callbacks.onNodeDragEnd?.(nodeId, position, { moved: wasDragged });
    if (wasDragged) {
      lastClickSuppressedNodeId = nodeId;
      window.setTimeout(() => {
        if (lastClickSuppressedNodeId === nodeId) {
          lastClickSuppressedNodeId = null;
        }
      }, 120);
    } else if (!cancelClick) {
      callbacks.onNodeClick?.(nodeId, dragState.startEvent);
    }
    if (dragState.pointerId != null) {
      try {
        el.g6EditorHost?.releasePointerCapture?.(dragState.pointerId);
      } catch {
        // Pointer capture may already be released.
      }
    }
  };

  const cancelNodeDrag = (event = null) => {
    finishNodeDrag(event, { cancelClick: true });
  };

  const hostPointerMove = (event) => {
    const setHoveredOpenControl = (nodeId) => {
      editor?.setOpenControlHover?.(nodeId || null);
    };
    if (nodeDrag && eventMatchesNodeDrag(event)) {
      if (Number.isFinite(event.buttons) && event.buttons === 0) {
        finishNodeDrag(event);
        return;
      }
      setHoveredOpenControl(null);
      const point = graphCanvasPoint(graph, event.clientX, event.clientY);
      const nextX = Math.round(nodeDrag.nodeX + point.x - nodeDrag.startX);
      const nextY = Math.round(nodeDrag.nodeY + point.y - nodeDrag.startY);
      const moved = Math.abs(nextX - nodeDrag.nodeX) > 1 || Math.abs(nextY - nodeDrag.nodeY) > 1;
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
      hoveredNodeId = findNodeAtClientPoint(editor, linkDrag.sourceId, point.x, point.y);
      updateConnectionPreview(graph, state.nodesById.get(linkDrag.sourceId), point.x, point.y);
      callbacks.onConnectionPointerMove?.(linkDrag.sourceId, hoveredNodeId);
      event.preventDefault();
      return;
    }
    if (!canvasPan || event.pointerId !== canvasPan.pointerId) {
      syncNodeHover(event.clientX, event.clientY);
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

  el.g6EditorHost?.addEventListener("pointerdown", hostPointerDown, true);
  el.g6EditorHost?.addEventListener("pointermove", hostPointerMove);
  el.g6EditorHost?.addEventListener("pointerleave", hostPointerLeave);
  el.g6EditorHost?.addEventListener("lostpointercapture", finishNodeDrag);
  window.addEventListener("pointerup", finishCanvasPan);
  window.addEventListener("pointercancel", finishCanvasPan);
  window.addEventListener("pointerup", finishNodeDrag);
  window.addEventListener("pointercancel", cancelNodeDrag);
  window.addEventListener("mouseup", finishNodeDrag);
  window.addEventListener("blur", cancelNodeDrag);
  window.addEventListener("contextmenu", cancelNodeDrag);
  visibilityChange = () => {
    if (document.hidden) {
      cancelNodeDrag();
    }
  };
  document.addEventListener("visibilitychange", visibilityChange);
  const finishOpenControlPress = (event) => {
    if (!openControlPress) {
      return;
    }
    const press = openControlPress;
    openControlPress = null;
    const source = originalEvent(event);
    const clientX = Number(source?.clientX);
    const clientY = Number(source?.clientY);
    const moved = Math.hypot(clientX - press.clientX, clientY - press.clientY) > 5;
    if (!moved && isOpenControlHit(graph, press.nodeId, clientX, clientY, editor)) {
      source?.preventDefault?.();
      source?.stopPropagation?.();
      suppressOpenControlClickNodeId = press.nodeId;
      window.setTimeout(() => {
        if (suppressOpenControlClickNodeId === press.nodeId) {
          suppressOpenControlClickNodeId = null;
        }
      }, 120);
      clearPointerHoverState();
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
    el.g6EditorHost?.removeEventListener("pointerdown", hostPointerDown, true);
    el.g6EditorHost?.removeEventListener("pointermove", hostPointerMove);
    el.g6EditorHost?.removeEventListener("pointerleave", hostPointerLeave);
    el.g6EditorHost?.removeEventListener("lostpointercapture", finishNodeDrag);
    window.removeEventListener("pointerup", finishCanvasPan);
    window.removeEventListener("pointercancel", finishCanvasPan);
    window.removeEventListener("pointerup", finishNodeDrag);
    window.removeEventListener("pointercancel", cancelNodeDrag);
    window.removeEventListener("mouseup", finishNodeDrag);
    window.removeEventListener("blur", cancelNodeDrag);
    window.removeEventListener("contextmenu", cancelNodeDrag);
    if (visibilityChange) {
      document.removeEventListener("visibilitychange", visibilityChange);
    }
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
    const target =
      hoveredNodeId && hoveredNodeId !== sourceId
        ? hoveredNodeId
        : findNodeAtClientPoint(editor, sourceId, linkDrag.clientX, linkDrag.clientY);
    clearConnectionPreview();
    linkDrag = null;
    callbacks.onConnectionDragEnd?.(target);
    requestAnimationFrame(() => {
      if (target) {
        callbacks.onConnectionComplete?.(sourceId, target);
      } else {
        callbacks.onConnectionCancel?.(sourceId);
      }
    });
  };

  graph.on("node:pointerdown", (event) => {
    if (eventButton(event) !== 0 || nodeDrag || linkDrag) {
      return;
    }
    const point = eventClientPoint(event);
    const id = findNodeAtClientPoint(editor, null, point.x, point.y);
    if (!id) {
      return;
    }
    beginNodePointerDown(id, event);
  });

  const syncNodeHover = (clientX, clientY) => {
    if (hoveredNodeId && !isActiveDiagramNode(hoveredNodeId)) {
      hoveredNodeId = null;
      editor?.setOpenControlHover?.(null);
    }
    let effectiveId = findNodeAtClientPoint(editor, null, clientX, clientY);
    if (
      !effectiveId &&
      hoveredNodeId &&
      isActiveDiagramNode(hoveredNodeId) &&
      isPointInOpenZone(graph, hoveredNodeId, clientX, clientY, editor)
    ) {
      effectiveId = hoveredNodeId;
    }
    const openZoneId =
      effectiveId && isPointInOpenZone(graph, effectiveId, clientX, clientY, editor)
        ? effectiveId
        : null;
    if (effectiveId === hoveredNodeId) {
      editor?.setOpenControlHover?.(openZoneId);
      return;
    }
    hoveredNodeId = effectiveId;
    hoveredInteractive = Boolean(effectiveId);
    editor?.setOpenControlHover?.(openZoneId);
    if (!canvasDragging) {
      setCanvasCursor(effectiveId ? "pointer" : "grab");
    }
    callbacks.onNodeHover?.(effectiveId);
  };

  graph.on("node:pointerover", (event) => {
    const point = eventClientPoint(event);
    syncNodeHover(point.x, point.y);
  });

  graph.on("node:pointermove", (event) => {
    if (linkDrag) {
      const point = eventClientPoint(event);
      linkDrag.clientX = point.x;
      linkDrag.clientY = point.y;
      hoveredNodeId = findNodeAtClientPoint(editor, linkDrag.sourceId, point.x, point.y);
      updateConnectionPreview(graph, state.nodesById.get(linkDrag.sourceId), point.x, point.y);
      callbacks.onConnectionPointerMove?.(linkDrag.sourceId, hoveredNodeId);
      return;
    }
    if (nodeDrag) {
      return;
    }
    const point = eventClientPoint(event);
    syncNodeHover(point.x, point.y);
  });

  graph.on("node:pointerleave", (event) => {
    const point = eventClientPoint(event);
    syncNodeHover(point.x, point.y);
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
    if (draggedNodeId && isOpenControlHit(graph, draggedNodeId, point.x, point.y, editor)) {
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
    const position = nodePositionFromGraph(graph, id, editor);
    if (position) {
      callbacks.onNodeDrag?.(id, position);
    }
  });

  graph.on("node:dragend", (event) => {
    if (linkDrag || nodeDrag) {
      return;
    }
    const id = draggedNodeId || nodeIdFromEvent(event, editor);
    const position = id ? nodePositionFromGraph(graph, id, editor) : null;
    if (id && position) {
      callbacks.onNodeDragEnd?.(id, position, { moved: dragged });
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
    if (
      !isOpenControlHit(graph, id, point.x, point.y, editor) &&
      !isLinkHandleHit(graph, id, point.x, point.y, editor) &&
      !isPointInNodeInteraction(graph, id, point.x, point.y, editor)
    ) {
      return;
    }
    if (isOpenControlHit(graph, id, point.x, point.y, editor)) {
      openControlPress = null;
      originalEvent(event)?.preventDefault?.();
      originalEvent(event)?.stopPropagation?.();
      if (suppressOpenControlClickNodeId === id) {
        suppressOpenControlClickNodeId = null;
        return;
      }
      clearPointerHoverState();
      callbacks.onOpenContainer?.(id);
      return;
    }
    callbacks.onNodeClick?.(id, originalEvent(event));
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
      updateConnectionPreview(graph, state.nodesById.get(linkDrag.sourceId), point.x, point.y);
      callbacks.onConnectionPointerMove?.(linkDrag.sourceId, hoveredNodeId);
      return;
    }
    if (!nodeDrag && !canvasPan) {
      syncNodeHover(point.x, point.y);
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
    if (event.key !== "Escape") {
      return;
    }
    if (linkDrag) {
      event.preventDefault();
      clearLinkDrag();
      return;
    }
    callbacks.onEscape?.(event);
  };
  window.addEventListener("keydown", escapeKeyDown);
}
