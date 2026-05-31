import {state} from '../state.js';
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
  let lastClickSuppressedNodeId = null;

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
    callbacks.onNodeHover?.(hoveredNodeId);
  });

  graph.on("node:pointerleave", (event) => {
    const id = targetId(event);
    if (hoveredNodeId === id) {
      hoveredNodeId = null;
      callbacks.onNodeHover?.(null);
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
    callbacks.onEdgeHover?.(targetId(event) || null);
  });

  graph.on("edge:pointerleave", () => {
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
