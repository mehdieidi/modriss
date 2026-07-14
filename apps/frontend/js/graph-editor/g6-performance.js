import { modelingDiagramEditorConfig } from "../modeling-config-data.js";
import { state } from "../state.js";

let drawFrame = 0;
let pendingRender = false;
let renderAgain = false;

function canvasPolicy() {
  const level = state.modelingConfig?.config?.levels?.[state.activeType];
  return level?.canvasPolicy && typeof level.canvasPolicy === "object" ? level.canvasPolicy : {};
}

function policyNumber(policy, key) {
  const value = Number(policy?.[key]);
  return Number.isFinite(value) ? value : null;
}

function spatialCellSize() {
  const configured = Number(modelingDiagramEditorConfig().spatialCellSize);
  return Number.isFinite(configured) && configured > 0 ? configured : null;
}

export function detailLevelForZoom(zoom = 1) {
  const policy = canvasPolicy();
  const low = policyNumber(policy, "lowDetailBelow");
  const high = policyNumber(policy, "highDetailAtOrAbove");
  if (low !== null && zoom < low) {
    return "low";
  }
  if (high !== null && zoom >= high) {
    return "high";
  }
  return "normal";
}

export function shouldShowEdgeLabels(zoom = 1, edgeCount = 0) {
  const policy = canvasPolicy();
  const labelsAt = policyNumber(policy, "edgeLabelsAtOrAbove");
  if (labelsAt !== null && zoom < labelsAt) {
    return false;
  }
  const denseThreshold = policyNumber(policy, "denseEdgeThreshold");
  const denseLabelsAt = policyNumber(policy, "denseEdgeLabelsAtOrAbove");
  if (
    denseThreshold !== null &&
    denseLabelsAt !== null &&
    edgeCount > denseThreshold &&
    zoom < denseLabelsAt
  ) {
    return false;
  }
  const veryDenseThreshold = policyNumber(policy, "veryDenseEdgeThreshold");
  const veryDenseLabelsAt = policyNumber(policy, "veryDenseEdgeLabelsAtOrAbove");
  if (
    veryDenseThreshold !== null &&
    veryDenseLabelsAt !== null &&
    edgeCount > veryDenseThreshold &&
    zoom < veryDenseLabelsAt
  ) {
    return false;
  }
  return true;
}

export function createAdjacencyIndex(edges = []) {
  const byNode = new Map();
  const byId = new Map();
  const remove = (edgeId) => {
    const edge = byId.get(edgeId);
    if (!edge) {
      return false;
    }
    [edge.sourceId, edge.targetId].forEach((nodeId) => {
      const bucket = byNode.get(nodeId);
      bucket?.delete(edgeId);
      if (bucket && !bucket.size) {
        byNode.delete(nodeId);
      }
    });
    byId.delete(edgeId);
    return true;
  };
  const add = (edge) => {
    if (!edge?.id) {
      return false;
    }
    remove(edge.id);
    byId.set(edge.id, edge);
    [edge.sourceId, edge.targetId].forEach((nodeId) => {
      if (!nodeId) {
        return;
      }
      if (!byNode.has(nodeId)) {
        byNode.set(nodeId, new Set());
      }
      byNode.get(nodeId).add(edge.id);
    });
    return true;
  };
  edges.forEach(add);
  return { byNode, byId, add, remove };
}

function finiteNumber(value, fallback = 0) {
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}

function nodeBoundsForIndex(node, fallbackSize = {}) {
  if (!node?.id) {
    return null;
  }
  const style = node.style && typeof node.style === "object" ? node.style : {};
  const size = Array.isArray(style.size) ? style.size : [];
  const width = Math.max(
    1,
    finiteNumber(
      style.width,
      finiteNumber(size[0], finiteNumber(node.width, fallbackSize.width || 1)),
    ),
  );
  const height = Math.max(
    1,
    finiteNumber(
      style.height,
      finiteNumber(size[1], finiteNumber(node.height, fallbackSize.height || 1)),
    ),
  );
  const centerX = Number(style.x);
  const centerY = Number(style.y);
  const x = Number.isFinite(centerX) ? centerX - width / 2 : finiteNumber(node.x, 0);
  let y = Number.isFinite(centerY) ? centerY - height / 2 : finiteNumber(node.y, 0);
  let indexedHeight = height;
  const data = node.data && typeof node.data === "object" ? node.data : {};
  const isContainer = Boolean(style.isContainer || data.container);
  if (isContainer) {
    const low = style.detailLevel === "low";
    const controlHeight = low ? 14 : 15;
    const topExtension = controlHeight + 4 + 16;
    y -= topExtension;
    indexedHeight += topExtension;
  }
  return {
    id: node.id,
    x,
    y,
    width,
    height: indexedHeight,
    maxX: x + width,
    maxY: y + indexedHeight,
  };
}

export function createSpatialIndex(
  nodes = [],
  { cellSize = spatialCellSize() || 256, fallbackSize = {} } = {},
) {
  const cells = new Map();
  const entries = new Map();
  let orderCounter = 0;

  const keyFor = (x, y) => `${Math.floor(x / cellSize)}:${Math.floor(y / cellSize)}`;

  const cellKeysForBounds = (bounds) => {
    const minCellX = Math.floor(bounds.x / cellSize);
    const minCellY = Math.floor(bounds.y / cellSize);
    const maxCellX = Math.floor(bounds.maxX / cellSize);
    const maxCellY = Math.floor(bounds.maxY / cellSize);
    const keys = [];
    for (let cellX = minCellX; cellX <= maxCellX; cellX += 1) {
      for (let cellY = minCellY; cellY <= maxCellY; cellY += 1) {
        keys.push(`${cellX}:${cellY}`);
      }
    }
    return keys;
  };

  const remove = (id) => {
    const entry = entries.get(id);
    if (!entry) {
      return false;
    }
    entry.cells.forEach((key) => {
      const bucket = cells.get(key);
      if (!bucket) {
        return;
      }
      bucket.delete(id);
      if (!bucket.size) {
        cells.delete(key);
      }
    });
    entries.delete(id);
    return true;
  };

  const add = (node, nextFallbackSize = fallbackSize, order = null) => {
    const bounds = nodeBoundsForIndex(node, nextFallbackSize);
    if (!bounds) {
      return false;
    }
    const keys = cellKeysForBounds(bounds);
    keys.forEach((key) => {
      if (!cells.has(key)) {
        cells.set(key, new Set());
      }
      cells.get(key).add(bounds.id);
    });
    entries.set(bounds.id, {
      ...bounds,
      cells: keys,
      order: Number.isFinite(order) ? order : orderCounter,
    });
    if (!Number.isFinite(order)) {
      orderCounter += 1;
    }
    return true;
  };

  const index = {
    rebuild(nextNodes = [], options = {}) {
      cells.clear();
      entries.clear();
      orderCounter = 0;
      const nextFallbackSize = options.fallbackSize || fallbackSize;
      nextNodes.forEach((node) => add(node, nextFallbackSize));
      return index;
    },
    update(node, options = {}) {
      if (!node?.id) {
        return false;
      }
      const previous = entries.get(node.id);
      remove(node.id);
      return add(node, options.fallbackSize || fallbackSize, previous?.order);
    },
    remove,
    has(id) {
      return entries.has(id);
    },
    size() {
      return entries.size;
    },
    findAt(x, y, { excludeId = "" } = {}) {
      if (!Number.isFinite(x) || !Number.isFinite(y)) {
        return null;
      }
      const bucket = cells.get(keyFor(x, y));
      if (!bucket?.size) {
        return null;
      }
      let best = null;
      let bestOrder = -1;
      bucket.forEach((id) => {
        if (id === excludeId) {
          return;
        }
        const entry = entries.get(id);
        if (!entry || x < entry.x || x > entry.maxX || y < entry.y || y > entry.maxY) {
          return;
        }
        if (entry.order >= bestOrder) {
          best = id;
          bestOrder = entry.order;
        }
      });
      return best;
    },
  };
  return index.rebuild(nodes, { fallbackSize });
}

function fingerprintStyle(style) {
  if (!style || typeof style !== "object") {
    return "";
  }
  const size = Array.isArray(style.size) ? style.size : [];
  return [
    style.x,
    style.y,
    style.width,
    style.height,
    size[0],
    size[1],
    style.isContainer,
    style.detailLevel,
    style.stroke,
    style.fill,
    style.lineWidth,
    style.opacity,
  ].join("|");
}

function fingerprintBadges(badges) {
  if (!Array.isArray(badges) || !badges.length) {
    return "";
  }
  return badges
    .map((badge) => [badge?.key, badge?.label, badge?.tone, badge?.icon].filter(Boolean).join(":"))
    .join(",");
}

export function fingerprintElement(element) {
  const data = element?.data && typeof element.data === "object" ? element.data : {};
  return [
    element.id,
    element.source,
    element.target,
    element.type,
    data.nodeType,
    data.label,
    data.diagramType,
    data.notation,
    data.kindText,
    data.detailText,
    data.tokenText,
    fingerprintBadges(data.badges),
    data.showHandles,
    data.accent,
    data.sticky,
    data.viewProfile,
    data.contextName,
    data.container,
    data.detailLevel,
    data.kind,
    data.presentationClass,
    data.markerStart,
    data.markerEnd,
    Array.isArray(data.pinPoints) ? data.pinPoints.length : 0,
    data.sourceAnchor?.side,
    data.sourceAnchor?.offsetY,
    data.targetAnchor?.side,
    data.targetAnchor?.offsetY,
    fingerprintStyle(element.style),
  ].join("\x1e");
}

export function diffGraphData(previous, next) {
  const prevNodes = previous?.nodesById || new Map();
  const prevEdges = previous?.edgesById || new Map();
  const prevNodeFingerprints = previous?.nodeFingerprints || new Map();
  const prevEdgeFingerprints = previous?.edgeFingerprints || new Map();
  const nextNodes = new Map(next.nodes.map((node) => [node.id, node]));
  const nextEdges = new Map(next.edges.map((edge) => [edge.id, edge]));
  const nodeFingerprints = new Map();
  const edgeFingerprints = new Map();
  const addNodes = [];
  const updateNodes = [];
  const removeNodeIds = [];
  const addEdges = [];
  const updateEdges = [];
  const removeEdgeIds = [];

  nextNodes.forEach((node, id) => {
    const fingerprint = fingerprintElement(node);
    nodeFingerprints.set(id, fingerprint);
    if (!prevNodes.has(id)) {
      addNodes.push(node);
    } else if (prevNodeFingerprints.get(id) !== fingerprint) {
      updateNodes.push(node);
    }
  });
  prevNodes.forEach((_, id) => {
    if (!nextNodes.has(id)) {
      removeNodeIds.push(id);
    }
  });

  nextEdges.forEach((edge, id) => {
    const fingerprint = fingerprintElement(edge);
    edgeFingerprints.set(id, fingerprint);
    if (!prevEdges.has(id)) {
      addEdges.push(edge);
    } else if (prevEdgeFingerprints.get(id) !== fingerprint) {
      updateEdges.push(edge);
    }
  });
  prevEdges.forEach((_, id) => {
    if (!nextEdges.has(id)) {
      removeEdgeIds.push(id);
    }
  });

  return {
    addNodes,
    updateNodes,
    removeNodeIds,
    addEdges,
    updateEdges,
    removeEdgeIds,
    snapshot: {
      nodesById: nextNodes,
      edgesById: nextEdges,
      nodeFingerprints,
      edgeFingerprints,
    },
  };
}

export function scheduleGraphDraw(graph) {
  if (!graph || drawFrame) {
    return;
  }
  drawFrame = window.requestAnimationFrame(() => {
    drawFrame = 0;
    const result = graph.draw?.();
    result?.catch?.((error) => {
      console.error("G6 draw failed", error);
      window.varkaG6State = {
        ...(window.varkaG6State || {}),
        lastError: error.message || String(error),
      };
    });
  });
}

let incrementalMutationFrame = 0;
const incrementalMutations = [];

export function scheduleIncrementalCanvasMutation(fn) {
  if (typeof fn !== "function") {
    return;
  }
  incrementalMutations.push(fn);
  if (incrementalMutationFrame) {
    return;
  }
  incrementalMutationFrame = window.requestAnimationFrame(() => {
    incrementalMutationFrame = 0;
    const batch = incrementalMutations.splice(0, incrementalMutations.length);
    batch.forEach((mutation) => {
      try {
        mutation();
      } catch (error) {
        console.error("Incremental canvas mutation failed", error);
      }
    });
  });
}

export function scheduleGraphRender(graph) {
  if (!graph) {
    return;
  }
  if (pendingRender) {
    renderAgain = true;
    return;
  }
  pendingRender = true;
  window.requestAnimationFrame(() => {
    pendingRender = false;
    const result = graph.render?.();
    result?.catch?.((error) => {
      console.error("G6 render failed", error);
      window.varkaG6State = {
        ...(window.varkaG6State || {}),
        lastError: error.message || String(error),
      };
    });
    if (renderAgain) {
      renderAgain = false;
      scheduleGraphRender(graph);
    }
  });
}

export function cancelScheduledDraw() {
  if (drawFrame) {
    window.cancelAnimationFrame(drawFrame);
    drawFrame = 0;
  }
  pendingRender = false;
  renderAgain = false;
}
