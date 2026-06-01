let drawFrame = 0;
let pendingRender = false;
let renderAgain = false;
const DEFAULT_SPATIAL_CELL_SIZE = 256;

export function detailLevelForZoom(zoom = 1) {
  if (zoom < 0.35) {
    return "low";
  }
  if (zoom >= 1.45) {
    return "high";
  }
  return "normal";
}

export function shouldShowEdgeLabels(zoom = 1, edgeCount = 0) {
  if (zoom < 0.75) {
    return false;
  }
  if (edgeCount > 1200 && zoom < 1.25) {
    return false;
  }
  if (edgeCount > 2500 && zoom < 1.65) {
    return false;
  }
  return true;
}

export function createAdjacencyIndex(edges = []) {
  const byNode = new Map();
  const byId = new Map();
  edges.forEach((edge) => {
    if (!edge?.id) {
      return;
    }
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
  });
  return {byNode, byId};
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
  const width = Math.max(1,
      finiteNumber(style.width, finiteNumber(size[0],
          finiteNumber(node.width, fallbackSize.width || 1))));
  const height = Math.max(1,
      finiteNumber(style.height, finiteNumber(size[1],
          finiteNumber(node.height, fallbackSize.height || 1))));
  const centerX = Number(style.x);
  const centerY = Number(style.y);
  const x = Number.isFinite(centerX)
      ? centerX - width / 2
      : finiteNumber(node.x, 0);
  const y = Number.isFinite(centerY)
      ? centerY - height / 2
      : finiteNumber(node.y, 0);
  return {
    id: node.id,
    x,
    y,
    width,
    height,
    maxX: x + width,
    maxY: y + height
  };
}

export function createSpatialIndex(nodes = [], {
  cellSize = DEFAULT_SPATIAL_CELL_SIZE,
  fallbackSize = {}
} = {}) {
  const cells = new Map();
  const entries = new Map();
  let orderCounter = 0;

  const keyFor = (x, y) =>
      `${Math.floor(x / cellSize)}:${Math.floor(y / cellSize)}`;

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
      order: Number.isFinite(order) ? order : orderCounter
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
    findAt(x, y, {excludeId = ""} = {}) {
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
        if (!entry || x < entry.x || x > entry.maxX || y < entry.y
            || y > entry.maxY) {
          return;
        }
        if (entry.order >= bestOrder) {
          best = id;
          bestOrder = entry.order;
        }
      });
      return best;
    }
  };
  return index.rebuild(nodes, {fallbackSize});
}

export function fingerprintElement(element) {
  return JSON.stringify({
    id: element.id,
    source: element.source,
    target: element.target,
    data: element.data,
    style: element.style,
    type: element.type
  });
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
      edgeFingerprints
    }
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
      window.modlessG6State = {
        ...(window.modlessG6State || {}),
        lastError: error.message || String(error)
      };
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
      window.modlessG6State = {
        ...(window.modlessG6State || {}),
        lastError: error.message || String(error)
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
