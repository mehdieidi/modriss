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
import { isMobileViewport } from "../responsive.js";

let currentCanvasCursorMode = "";
const DEFERRED_NODE_DRAG_EDGE_THRESHOLD = 350;
const PHONE_EDGE_HOLD_MS = 430;
const PHONE_EDGE_HOLD_MOVE_TOLERANCE = 9;
const PINCH_MIN_SCALE = 0.01;
const PINCH_MAX_SCALE = 2.5;

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

function setCanvasPointerCaptureActive(active) {
  const targets = [el.canvasViewport, el.canvasGrid, el.g6EditorHost].filter(Boolean);
  targets.forEach((target) => {
    target.classList.toggle("g6-pointer-captured", Boolean(active));
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

function _openControlBounds(graph, nodeId, editor = null) {
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
    return false;
  }
  const semanticNode = state.nodesById.get(nodeId);
  const size = nodeSizeForDiagram(state.activeType, semanticNode);
  const width = Number(data?.style?.width) || size.width;
  const height = Number(data?.style?.height) || size.height;
  const roundedX = Math.round(x);
  const roundedY = Math.round(y);
  const nextCenterX = Math.round(roundedX + width / 2);
  const nextCenterY = Math.round(roundedY + height / 2);
  if (Number(data?.style?.x) === nextCenterX && Number(data?.style?.y) === nextCenterY) {
    return false;
  }
  if (typeof graph.translateElementTo === "function") {
    // G6's translate path only applies position changes. The generic data
    // update path recalculates the complete custom node style on every frame,
    // which is needlessly expensive in large, edge-heavy diagrams.
    const result = graph.translateElementTo(nodeId, [nextCenterX, nextCenterY], false);
    result?.catch?.((error) => {
      console.error("G6 node translation failed", error);
    });
  } else {
    graph.updateNodeData?.([
      {
        id: nodeId,
        style: {
          x: nextCenterX,
          y: nextCenterY,
        },
      },
    ]);
    scheduleGraphDraw(graph);
  }
  const snapshotNode = editor?.dataSnapshot?.nodesById?.get?.(nodeId);
  if (snapshotNode) {
    const nextSnapshotNode = {
      ...snapshotNode,
      style: {
        ...(snapshotNode.style || {}),
        x: nextCenterX,
        y: nextCenterY,
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
  return true;
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
  let canvasPanTranslateX = 0;
  let canvasPanTranslateY = 0;
  let lastClickSuppressedNodeId = null;
  let nodeDragFrame = 0;
  let pendingNodeDragMove = null;
  let finishLinkDragWindow = null;
  let pendingPhoneEdgeHold = null;
  let escapeKeyDown = null;
  let visibilityChange = null;
  let openControlPress = null;
  let suppressOpenControlClickNodeId = null;
  let ignoreTouchCanvasClickUntil = 0;
  let ignoreTouchGestureClickUntil = 0;
  const touchPointers = new Map();
  let pinchGesture = null;

  const suppressFollowingCanvasClick = (event) => {
    ignoreTouchCanvasClickUntil =
      performance.now() + (originalEvent(event)?.pointerType === "touch" ? 220 : 120);
  };

  const suppressFollowingNodeClick = (nodeId) => {
    lastClickSuppressedNodeId = nodeId;
    window.setTimeout(() => {
      if (lastClickSuppressedNodeId === nodeId) {
        lastClickSuppressedNodeId = null;
      }
    }, 220);
  };

  currentCanvasCursorMode = "";
  setCanvasCursor("grab");

  const createNodeDragPreview = (drag, node, clientX, clientY) => {
    const preview = document.createElement("div");
    const zoom = Math.max(0.01, Number(graph.getZoom?.()) || state.viewport.scale || 1);
    const width = Math.max(48, Math.round(drag.width * zoom));
    const height = Math.max(36, Math.round(drag.height * zoom));
    preview.className = "g6-node-drag-preview";
    preview.textContent = node?.label || node?.id || drag.nodeId;
    Object.assign(preview.style, {
      position: "fixed",
      zIndex: "1000",
      width: `${width}px`,
      height: `${height}px`,
      padding: "10px 12px",
      boxSizing: "border-box",
      overflow: "hidden",
      border: "2px solid var(--accent-select, #5ecbff)",
      borderRadius: "10px",
      background: "var(--node-bg, #131923)",
      color: "var(--text-strong, #e7ecf5)",
      boxShadow: "0 12px 28px rgba(0, 0, 0, 0.32)",
      font: "700 12px/1.35 var(--font-ui, sans-serif)",
      pointerEvents: "none",
      willChange: "transform",
    });
    document.body.appendChild(preview);
    drag.preview = preview;
    drag.pointerOffsetX = (drag.startX - drag.nodeX) * zoom;
    drag.pointerOffsetY = (drag.startY - drag.nodeY) * zoom;
    updateNodeDragPreview(drag, clientX, clientY);
  };

  const updateNodeDragPreview = (drag, clientX, clientY) => {
    if (!drag?.preview) {
      return;
    }
    drag.preview.style.transform = `translate3d(${Math.round(clientX - drag.pointerOffsetX)}px, ${Math.round(clientY - drag.pointerOffsetY)}px, 0)`;
  };

  const removeNodeDragPreview = (drag) => {
    drag?.preview?.remove?.();
    if (drag) {
      drag.preview = null;
    }
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
    canvasPanTranslateX += dx;
    canvasPanTranslateY += dy;
    const host = editor?.host || el.g6EditorHost;
    if (host) {
      // Let the browser compositor move the already-painted canvas while the
      // pointer is down. Re-rendering a large graph for every pointer event
      // makes panning fall behind the cursor, especially with long PSM edges.
      host.style.transform = `translate3d(${canvasPanTranslateX}px, ${canvasPanTranslateY}px, 0)`;
      host.style.willChange = "transform";
    }
  };

  const commitCanvasPan = () => {
    const dx = canvasPanTranslateX;
    const dy = canvasPanTranslateY;
    canvasPanTranslateX = 0;
    canvasPanTranslateY = 0;
    const host = editor?.host || el.g6EditorHost;
    if (host) {
      host.style.transform = "";
      host.style.willChange = "";
    }
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

  const isTouchPointer = (event) => originalEvent(event)?.pointerType === "touch";

  const updateTouchPointer = (event) => {
    if (!isTouchPointer(event) || !Number.isFinite(event.pointerId)) {
      return;
    }
    touchPointers.set(event.pointerId, {
      x: Number(event.clientX),
      y: Number(event.clientY),
    });
  };

  const pinchPoints = () => {
    const points = [...touchPointers.values()];
    if (points.length < 2) {
      return null;
    }
    const first = points[0];
    const second = points[1];
    return {
      distance: Math.hypot(second.x - first.x, second.y - first.y),
      midpoint: {
        x: (first.x + second.x) / 2,
        y: (first.y + second.y) / 2,
      },
    };
  };

  const beginPinchGesture = () => {
    const points = pinchPoints();
    if (!points || points.distance < 1 || linkDrag) {
      return false;
    }
    // A second finger changes the gesture from selection/panning to zooming.
    // Finish the one-pointer gesture first so its compositor translation is not
    // left behind when G6 applies the pinch transform.
    openControlPress = null;
    editor?.setOpenControlHover?.(null);
    if (nodeDrag) {
      cancelNodeDrag();
    }
    if (canvasPan) {
      finishCanvasPan();
    }
    const scale = Math.max(
      PINCH_MIN_SCALE,
      Math.min(PINCH_MAX_SCALE, Number(graph.getZoom?.()) || state.viewport.scale || 1),
    );
    pinchGesture = {
      initialDistance: points.distance,
      initialScale: scale,
      graphPoint: graphCanvasPoint(graph, points.midpoint.x, points.midpoint.y),
    };
    touchPointers.forEach((_point, pointerId) => {
      try {
        el.g6EditorHost?.setPointerCapture?.(pointerId);
      } catch {
        // Pointer capture is a best-effort upgrade for touches leaving the host.
      }
    });
    return true;
  };

  const updatePinchGesture = () => {
    if (!pinchGesture) {
      return false;
    }
    const points = pinchPoints();
    if (!points || points.distance < 1) {
      return true;
    }
    const rect =
      graph?.getCanvas?.()?.getContainer?.()?.getBoundingClientRect?.() ||
      el.g6EditorHost?.getBoundingClientRect?.() ||
      el.canvasViewport?.getBoundingClientRect?.();
    if (!rect) {
      return true;
    }
    const scale = Math.max(
      PINCH_MIN_SCALE,
      Math.min(
        PINCH_MAX_SCALE,
        pinchGesture.initialScale * (points.distance / pinchGesture.initialDistance),
      ),
    );
    const nextX = points.midpoint.x - rect.left - pinchGesture.graphPoint.x * scale;
    const nextY = points.midpoint.y - rect.top - pinchGesture.graphPoint.y * scale;
    state.viewport.scale = scale;
    state.viewport.x = nextX;
    state.viewport.y = nextY;
    try {
      graph.zoomTo?.(scale, false);
      graph.translateTo?.([nextX, nextY], false);
    } catch {
      // G6 will report a normal viewport update on the next successful frame.
    }
    callbacks.onViewportChange?.();
    return true;
  };

  const finishPinchGesture = () => {
    if (!pinchGesture) {
      return;
    }
    pinchGesture = null;
    touchPointers.clear();
    ignoreTouchCanvasClickUntil = Math.max(ignoreTouchCanvasClickUntil, performance.now() + 350);
    ignoreTouchGestureClickUntil = performance.now() + 350;
    setCanvasPointerCaptureActive(Boolean(nodeDrag || linkDrag));
    setCanvasCursor("grab");
  };

  const finishTouchPointer = (event) => {
    if (!isTouchPointer(event)) {
      return;
    }
    touchPointers.delete(event.pointerId);
    if (pinchGesture && touchPointers.size < 2) {
      finishPinchGesture();
    }
  };

  const finishCanvasPan = () => {
    if (!canvasPan) {
      return;
    }
    flushCanvasPan();
    commitCanvasPan();
    if (canvasPan.pointerId != null) {
      try {
        el.g6EditorHost?.releasePointerCapture?.(canvasPan.pointerId);
      } catch {
        // Capture may already be released after pointerup outside the host.
      }
    }
    canvasPan = null;
    canvasDragging = false;
    setCanvasPointerCaptureActive(Boolean(nodeDrag || linkDrag));
    setCanvasCursor(hoveredInteractive ? "pointer" : "grab");
  };

  const hostPointerDown = (event) => {
    updateTouchPointer(event);
    if (isTouchPointer(event) && touchPointers.size >= 2 && !linkDrag) {
      if (!pinchGesture) {
        beginPinchGesture();
      }
      event.preventDefault();
      event.stopPropagation();
      return;
    }
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
    canvasPanTranslateX = 0;
    canvasPanTranslateY = 0;
    canvasDragging = true;
    setCanvasPointerCaptureActive(true);
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
      linkDrag = {
        sourceId: id,
        pointerId: originalEvent(event)?.pointerId,
        clientX: point.x,
        clientY: point.y,
      };
      setCanvasPointerCaptureActive(true);
      updateConnectionPreview(graph, sourceNode, point.x, point.y);
      callbacks.onConnectionDragStart?.(id);
      if (linkDrag.pointerId != null) {
        try {
          el.g6EditorHost?.setPointerCapture?.(linkDrag.pointerId);
        } catch {
          // Link dragging can continue inside the canvas without capture.
        }
      }
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
          width: topLeft.width,
          height: topLeft.height,
          lastX: topLeft.x,
          lastY: topLeft.y,
          deferred: state.diagram.connections.length >= DEFERRED_NODE_DRAG_EDGE_THRESHOLD,
          phoneEdgeHold: isTouchPointer(event) && isMobileViewport(),
        };
        if (nodeDrag.deferred) {
          createNodeDragPreview(nodeDrag, state.nodesById.get(id), point.x, point.y);
          const hideResult = graph.hideElement?.(id, false);
          hideResult?.catch?.((error) => console.error("G6 node hide failed", error));
        }
        setCanvasPointerCaptureActive(true);
        callbacks.onNodeDragStart?.(id);
        if (nodeDrag.pointerId != null) {
          try {
            el.g6EditorHost?.setPointerCapture?.(nodeDrag.pointerId);
          } catch {
            // Drag still works without capture inside the canvas.
          }
        }
        if (nodeDrag.phoneEdgeHold) {
          pendingPhoneEdgeHold = {
            nodeId: id,
            pointerId: nodeDrag.pointerId,
            clientX: point.x,
            clientY: point.y,
            timer: window.setTimeout(() => {
              const hold = pendingPhoneEdgeHold;
              if (
                !hold ||
                hold.nodeId !== id ||
                nodeDrag?.nodeId !== id ||
                (hold.pointerId != null && nodeDrag.pointerId !== hold.pointerId)
              ) {
                return;
              }
              pendingPhoneEdgeHold = null;
              // The hold wins only while the node is still in its initial touch
              // state. Cancel the regular node drag before starting the edge
              // gesture so the node never moves under the user's finger.
              cancelNodeDrag();
              ignoreTouchGestureClickUntil = performance.now() + 350;
              const sourceNode = state.nodesById.get(id);
              linkDrag = {
                sourceId: id,
                pointerId: hold.pointerId,
                clientX: hold.clientX,
                clientY: hold.clientY,
              };
              setCanvasPointerCaptureActive(true);
              updateConnectionPreview(graph, sourceNode, hold.clientX, hold.clientY);
              callbacks.onConnectionDragStart?.(id);
              if (linkDrag.pointerId != null) {
                try {
                  el.g6EditorHost?.setPointerCapture?.(linkDrag.pointerId);
                } catch {
                  // Link dragging can continue inside the canvas without capture.
                }
              }
            }, PHONE_EDGE_HOLD_MS),
          };
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
    if (move.deferred) {
      if (nodeDrag?.nodeId === move.nodeId) {
        nodeDrag.lastX = move.x;
        nodeDrag.lastY = move.y;
        updateNodeDragPreview(nodeDrag, move.clientX, move.clientY);
      }
      callbacks.onNodeDrag?.(move.nodeId, { x: move.x, y: move.y });
      return;
    }
    const moved = moveGraphNode(editor, move.nodeId, move.x, move.y);
    if (!moved) {
      return;
    }
    callbacks.onNodeDrag?.(move.nodeId, { x: move.x, y: move.y });
  };

  const queueNodeDragMove = (nodeId, x, y, clientX, clientY) => {
    pendingNodeDragMove = { nodeId, x, y, clientX, clientY, deferred: Boolean(nodeDrag?.deferred) };
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

  const cancelPhoneEdgeHold = () => {
    if (!pendingPhoneEdgeHold) {
      return;
    }
    window.clearTimeout(pendingPhoneEdgeHold.timer);
    pendingPhoneEdgeHold = null;
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
    cancelPhoneEdgeHold();
    flushNodeDragMove();
    const dragState = nodeDrag;
    const nodeId = dragState.nodeId;
    const wasDragged = dragged;
    if (dragState.deferred && wasDragged) {
      moveGraphNode(editor, nodeId, dragState.lastX, dragState.lastY);
    }
    if (dragState.deferred) {
      const showResult = graph.showElement?.(nodeId, false);
      showResult?.catch?.((error) => console.error("G6 node show failed", error));
      removeNodeDragPreview(dragState);
    }
    nodeDrag = null;
    draggedNodeId = null;
    dragged = false;
    setCanvasPointerCaptureActive(Boolean(canvasPan || linkDrag));
    const position = dragState.deferred
      ? { x: Math.round(dragState.lastX), y: Math.round(dragState.lastY) }
      : nodePositionFromGraph(graph, nodeId, editor);
    callbacks.onNodeDragEnd?.(nodeId, position, { moved: wasDragged });
    if (wasDragged) {
      suppressFollowingNodeClick(nodeId);
    } else if (!cancelClick) {
      if (lastClickSuppressedNodeId !== nodeId) {
        suppressFollowingNodeClick(nodeId);
        suppressFollowingCanvasClick(dragState.startEvent);
        callbacks.onNodeClick?.(nodeId, dragState.startEvent);
      }
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
    updateTouchPointer(event);
    if (isTouchPointer(event) && pinchGesture) {
      updatePinchGesture();
      event.preventDefault();
      return;
    }
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
      const phoneHoldDistance = pendingPhoneEdgeHold
        ? Math.hypot(
            event.clientX - pendingPhoneEdgeHold.clientX,
            event.clientY - pendingPhoneEdgeHold.clientY,
          )
        : Infinity;
      if (pendingPhoneEdgeHold && phoneHoldDistance <= PHONE_EDGE_HOLD_MOVE_TOLERANCE) {
        event.preventDefault();
        return;
      }
      if (pendingPhoneEdgeHold) {
        cancelPhoneEdgeHold();
      }
      const moved = Math.abs(nextX - nodeDrag.nodeX) > 1 || Math.abs(nextY - nodeDrag.nodeY) > 1;
      if (moved) {
        dragged = true;
      }
      queueNodeDragMove(nodeDrag.nodeId, nextX, nextY, event.clientX, event.clientY);
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

  const preventCanvasTextSelection = (event) => {
    event.preventDefault();
  };

  const pinchPointerMoveCapture = (event) => {
    if (!isTouchPointer(event) || !pinchGesture) {
      return;
    }
    updateTouchPointer(event);
    updatePinchGesture();
    event.preventDefault();
    event.stopImmediatePropagation();
  };

  el.g6EditorHost?.addEventListener("pointerdown", hostPointerDown, true);
  el.g6EditorHost?.addEventListener("pointermove", pinchPointerMoveCapture, true);
  el.g6EditorHost?.addEventListener("pointermove", hostPointerMove);
  el.g6EditorHost?.addEventListener("pointerleave", hostPointerLeave);
  el.canvasViewport?.addEventListener("selectstart", preventCanvasTextSelection, true);
  el.g6EditorHost?.addEventListener("lostpointercapture", finishNodeDrag);
  window.addEventListener("pointerup", finishCanvasPan);
  window.addEventListener("pointercancel", finishCanvasPan);
  window.addEventListener("pointerup", finishTouchPointer);
  window.addEventListener("pointercancel", finishTouchPointer);
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
    const host = editor?.host || el.g6EditorHost;
    if (host) {
      host.style.transform = "";
      host.style.willChange = "";
    }
    if (nodeDragFrame) {
      window.cancelAnimationFrame(nodeDragFrame);
      nodeDragFrame = 0;
    }
    cancelPhoneEdgeHold();
    removeNodeDragPreview(nodeDrag);
    setCanvasPointerCaptureActive(false);
    pinchGesture = null;
    touchPointers.clear();
    pendingNodeDragMove = null;
    el.g6EditorHost?.removeEventListener("pointerdown", hostPointerDown, true);
    el.g6EditorHost?.removeEventListener("pointermove", pinchPointerMoveCapture, true);
    el.g6EditorHost?.removeEventListener("pointermove", hostPointerMove);
    el.g6EditorHost?.removeEventListener("pointerleave", hostPointerLeave);
    el.canvasViewport?.removeEventListener("selectstart", preventCanvasTextSelection, true);
    el.g6EditorHost?.removeEventListener("lostpointercapture", finishNodeDrag);
    window.removeEventListener("pointerup", finishCanvasPan);
    window.removeEventListener("pointercancel", finishCanvasPan);
    window.removeEventListener("pointerup", finishTouchPointer);
    window.removeEventListener("pointercancel", finishTouchPointer);
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
    const pointerId = linkDrag.pointerId;
    linkDrag = null;
    setCanvasPointerCaptureActive(Boolean(canvasPan || nodeDrag));
    if (pointerId != null) {
      try {
        el.g6EditorHost?.releasePointerCapture?.(pointerId);
      } catch {
        // Capture may already be released.
      }
    }
    clearConnectionPreview();
    callbacks.onConnectionDragEnd?.(null);
  };

  const finishLinkDrag = () => {
    if (!linkDrag) {
      return;
    }
    const sourceId = linkDrag.sourceId;
    const pointerId = linkDrag.pointerId;
    const target =
      hoveredNodeId && hoveredNodeId !== sourceId
        ? hoveredNodeId
        : findNodeAtClientPoint(editor, sourceId, linkDrag.clientX, linkDrag.clientY);
    clearConnectionPreview();
    linkDrag = null;
    setCanvasPointerCaptureActive(Boolean(canvasPan || nodeDrag));
    if (pointerId != null) {
      try {
        el.g6EditorHost?.releasePointerCapture?.(pointerId);
      } catch {
        // Capture may already be released.
      }
    }
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
    if (isTouchPointer(event) && performance.now() < ignoreTouchGestureClickUntil) {
      return;
    }
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
    suppressFollowingNodeClick(id);
    suppressFollowingCanvasClick(event);
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
    if (isTouchPointer(event) && performance.now() < ignoreTouchGestureClickUntil) {
      return;
    }
    const id = targetId(event, editor);
    if (id) {
      callbacks.onEdgeClick?.(id, originalEvent(event));
    }
  });

  graph.on("canvas:click", (event) => {
    if (linkDrag) {
      return;
    }
    if (performance.now() < ignoreTouchCanvasClickUntil) {
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
