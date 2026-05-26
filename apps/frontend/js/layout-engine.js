const DEFAULT_NODE_W = 228;
const DEFAULT_NODE_H = 112;
const CIM_NODE_W = 176;
const CIM_NODE_H = 96;
const LAYOUT_MARGIN = 48;
const GAP_X = 72;
const GAP_Y = 56;

function numeric(value, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function normalizeNodeSize(nodeSize = {}) {
  const width = numeric(nodeSize.width, DEFAULT_NODE_W);
  const height = numeric(nodeSize.height, DEFAULT_NODE_H);
  return {
    width: width > 0 ? width : DEFAULT_NODE_W,
    height: height > 0 ? height : DEFAULT_NODE_H
  };
}

function spacingFor(nodeSize, options = {}) {
  const size = normalizeNodeSize(nodeSize);
  return {
    width: size.width,
    height: size.height,
    stepX: size.width + numeric(options.gapX, GAP_X),
    stepY: size.height + numeric(options.gapY, GAP_Y),
    marginX: numeric(options.marginX, GAP_X / 2),
    marginY: numeric(options.marginY, GAP_Y / 2)
  };
}

function rectFor(node, nodeSize, options = {}) {
  const spacing = spacingFor(nodeSize, options);
  return {
    minX: numeric(node.x) - spacing.marginX,
    minY: numeric(node.y) - spacing.marginY,
    maxX: numeric(node.x) + spacing.width + spacing.marginX,
    maxY: numeric(node.y) + spacing.height + spacing.marginY
  };
}

function rectsOverlap(a, b) {
  return a.minX < b.maxX && a.maxX > b.minX
      && a.minY < b.maxY && a.maxY > b.minY;
}

function sortNodes(nodes) {
  return [...nodes].sort((left, right) => {
    const yDiff = numeric(left.y) - numeric(right.y);
    if (Math.abs(yDiff) > 0.5) {
      return yDiff;
    }
    const xDiff = numeric(left.x) - numeric(right.x);
    if (Math.abs(xDiff) > 0.5) {
      return xDiff;
    }
    return String(left.id || "").localeCompare(String(right.id || ""));
  });
}

function syncNodeMeta(node) {
  node.x = Math.round(numeric(node.x));
  node.y = Math.round(numeric(node.y));
  if (node.meta && typeof node.meta === "object") {
    node.meta.x = node.x;
    node.meta.y = node.y;
  }
}

function clampPosition(position, options = {}) {
  return {
    x: Math.max(numeric(options.minX, LAYOUT_MARGIN), Math.round(position.x)),
    y: Math.max(numeric(options.minY, LAYOUT_MARGIN), Math.round(position.y))
  };
}

function candidateScore(dx, dy, preferDx, preferDy) {
  const distance = Math.abs(dx) + Math.abs(dy);
  const biasPenalty = (preferDx && Math.sign(dx) !== Math.sign(preferDx)
          ? 0.75 : 0)
      + (preferDy && Math.sign(dy) !== Math.sign(preferDy) ? 0.5 : 0);
  const verticalPenalty = Math.abs(dy) * 0.12;
  return distance + biasPenalty + verticalPenalty;
}

function findFreePosition(origin, occupiedRects, nodeSize, options = {}) {
  const spacing = spacingFor(nodeSize, options);
  const start = clampPosition(origin, options);
  const originRect = {
    minX: start.x - spacing.marginX,
    minY: start.y - spacing.marginY,
    maxX: start.x + spacing.width + spacing.marginX,
    maxY: start.y + spacing.height + spacing.marginY
  };
  if (!occupiedRects.some((rect) => rectsOverlap(originRect, rect))) {
    return start;
  }

  const preferDx = numeric(options.preferDx, 0);
  const preferDy = numeric(options.preferDy, 0);
  const maxRadius = Math.max(8, occupiedRects.length + 3);
  for (let radius = 1; radius <= maxRadius; radius += 1) {
    const candidates = [];
    for (let dx = -radius; dx <= radius; dx += 1) {
      for (let dy = -radius; dy <= radius; dy += 1) {
        if (Math.max(Math.abs(dx), Math.abs(dy)) !== radius) {
          continue;
        }
        candidates.push({
          dx,
          dy,
          score: candidateScore(dx, dy, preferDx, preferDy)
        });
      }
    }
    candidates.sort((left, right) => left.score - right.score);
    for (const candidate of candidates) {
      const position = clampPosition({
        x: start.x + candidate.dx * spacing.stepX,
        y: start.y + candidate.dy * spacing.stepY
      }, options);
      const rect = {
        minX: position.x - spacing.marginX,
        minY: position.y - spacing.marginY,
        maxX: position.x + spacing.width + spacing.marginX,
        maxY: position.y + spacing.height + spacing.marginY
      };
      if (!occupiedRects.some((occupied) => rectsOverlap(rect, occupied))) {
        return position;
      }
    }
  }

  const maxBottom = occupiedRects.reduce((maxValue, rect) =>
      Math.max(maxValue, rect.maxY), start.y);
  return clampPosition({
    x: start.x,
    y: maxBottom + spacing.marginY * 2
  }, options);
}

function componentOrderKey(component) {
  return component.map((node) => String(node.id || "")).sort().join("|");
}

function connectedComponents(nodes, edges) {
  const nodesById = new Map(nodes.map((node) => [node.id, node]));
  const adjacency = new Map(nodes.map((node) => [node.id, new Set()]));
  edges.forEach((edge) => {
    if (!nodesById.has(edge.sourceId) || !nodesById.has(edge.targetId)) {
      return;
    }
    adjacency.get(edge.sourceId).add(edge.targetId);
    adjacency.get(edge.targetId).add(edge.sourceId);
  });
  const visited = new Set();
  const components = [];
  for (const node of nodes) {
    if (visited.has(node.id)) {
      continue;
    }
    const queue = [node.id];
    const component = [];
    visited.add(node.id);
    while (queue.length) {
      const currentId = queue.shift();
      const current = nodesById.get(currentId);
      if (!current) {
        continue;
      }
      component.push(current);
      adjacency.get(currentId).forEach((neighborId) => {
        if (visited.has(neighborId)) {
          return;
        }
        visited.add(neighborId);
        queue.push(neighborId);
      });
    }
    components.push(component);
  }
  return components.sort((left, right) => {
    const sizeDiff = right.length - left.length;
    if (sizeDiff) {
      return sizeDiff;
    }
    return componentOrderKey(left).localeCompare(componentOrderKey(right));
  });
}

function sortLayerNodes(nodes, edgesBySource, edgesByTarget) {
  return [...nodes].sort((left, right) => {
    const leftWeight = (edgesByTarget.get(left.id)?.size || 0)
        - (edgesBySource.get(left.id)?.size || 0);
    const rightWeight = (edgesByTarget.get(right.id)?.size || 0)
        - (edgesBySource.get(right.id)?.size || 0);
    if (leftWeight !== rightWeight) {
      return leftWeight - rightWeight;
    }
    return String(left.label || left.id || "").localeCompare(
        String(right.label || right.id || ""));
  });
}

export function nodeSizeForType(typeKey) {
  return typeKey === "cim"
      ? {width: CIM_NODE_W, height: CIM_NODE_H}
      : {width: DEFAULT_NODE_W, height: DEFAULT_NODE_H};
}

export function layoutLooksStacked(nodes, nodeSize, options = {}) {
  if (!Array.isArray(nodes) || nodes.length < 2) {
    return false;
  }
  const spacing = spacingFor(nodeSize, options);
  const allZero = nodes.every((node) => numeric(node.x) === 0
      && numeric(node.y) === 0);
  const coarsePositions = new Set(nodes.map((node) => {
    const x = Math.round(numeric(node.x) / Math.max(spacing.stepX, 1));
    const y = Math.round(numeric(node.y) / Math.max(spacing.stepY, 1));
    return `${x},${y}`;
  }));
  let overlapCount = 0;
  for (let i = 0; i < nodes.length; i += 1) {
    const leftRect = rectFor(nodes[i], nodeSize, options);
    for (let j = i + 1; j < nodes.length; j += 1) {
      if (rectsOverlap(leftRect, rectFor(nodes[j], nodeSize, options))) {
        overlapCount += 1;
        if (overlapCount > 0) {
          return true;
        }
      }
    }
  }
  return allZero || coarsePositions.size <= Math.ceil(nodes.length * 0.6);
}

export function resolveNodeOverlaps(nodes, nodeSize, options = {}) {
  if (!Array.isArray(nodes) || nodes.length < 2) {
    return new Set();
  }
  const movableNodeIds = options.movableNodeIds
      ? new Set(options.movableNodeIds) : new Set(nodes.map((node) => node.id));
  const fixedNodes = sortNodes(nodes.filter((node) => !movableNodeIds.has(
      node.id)));
  const movableNodes = sortNodes(nodes.filter((node) => movableNodeIds.has(
      node.id)));
  const occupiedRects = fixedNodes.map((node) => rectFor(node, nodeSize,
      options));
  const movedNodeIds = new Set();
  for (const node of movableNodes) {
    const preferred = {
      x: numeric(node.x, LAYOUT_MARGIN),
      y: numeric(node.y, LAYOUT_MARGIN)
    };
    const resolved = findFreePosition(preferred, occupiedRects, nodeSize, {
      ...options,
      preferDx: options.preferDxByNodeId?.get(node.id) ?? options.preferDx,
      preferDy: options.preferDyByNodeId?.get(node.id) ?? options.preferDy
    });
    if (resolved.x !== Math.round(numeric(node.x))
        || resolved.y !== Math.round(numeric(node.y))) {
      movedNodeIds.add(node.id);
    }
    node.x = resolved.x;
    node.y = resolved.y;
    syncNodeMeta(node);
    occupiedRects.push(rectFor(node, nodeSize, options));
  }
  return movedNodeIds;
}

export function applyDeterministicLayout(nodes, edges = [], nodeSize,
    options = {}) {
  if (!Array.isArray(nodes) || !nodes.length) {
    return new Set();
  }
  const size = normalizeNodeSize(nodeSize);
  const validNodesById = new Map(nodes.map((node) => [node.id, node]));
  const validEdges = edges.filter((edge) => validNodesById.has(edge.sourceId)
      && validNodesById.has(edge.targetId) && edge.sourceId !== edge.targetId);
  const edgesBySource = new Map(nodes.map((node) => [node.id, new Set()]));
  const edgesByTarget = new Map(nodes.map((node) => [node.id, new Set()]));
  validEdges.forEach((edge) => {
    edgesBySource.get(edge.sourceId).add(edge.targetId);
    edgesByTarget.get(edge.targetId).add(edge.sourceId);
  });

  const components = connectedComponents(nodes, validEdges);
  let currentY = numeric(options.startY, LAYOUT_MARGIN);
  const movedNodeIds = new Set();

  components.forEach((component) => {
    const depth = new Map(component.map((node) => [node.id, 0]));
    for (let pass = 0; pass < component.length; pass += 1) {
      let changed = false;
      validEdges.forEach((edge) => {
        if (!depth.has(edge.sourceId) || !depth.has(edge.targetId)) {
          return;
        }
        const nextDepth = depth.get(edge.sourceId) + 1;
        if (nextDepth > depth.get(edge.targetId)) {
          depth.set(edge.targetId, nextDepth);
          changed = true;
        }
      });
      if (!changed) {
        break;
      }
    }

    const layers = new Map();
    component.forEach((node) => {
      const layerIndex = depth.get(node.id) || 0;
      if (!layers.has(layerIndex)) {
        layers.set(layerIndex, []);
      }
      layers.get(layerIndex).push(node);
    });
    const sortedLayers = [...layers.entries()].sort(([left], [right]) =>
        left - right);
    const maxRows = Math.max(...sortedLayers.map(([, layerNodes]) =>
        layerNodes.length));
    const componentStartX = numeric(options.startX, LAYOUT_MARGIN);
    sortedLayers.forEach(([layerIndex, layerNodes]) => {
      const ordered = sortLayerNodes(layerNodes, edgesBySource, edgesByTarget);
      const rowOffset = (maxRows - ordered.length) / 2;
      ordered.forEach((node, rowIndex) => {
        node.x = componentStartX + layerIndex * (size.width + GAP_X + 28);
        node.y = currentY + Math.round((rowOffset + rowIndex) * (size.height
            + GAP_Y));
        syncNodeMeta(node);
        movedNodeIds.add(node.id);
      });
    });
    currentY += maxRows * (size.height + GAP_Y) + size.height + GAP_Y * 2;
  });

  resolveNodeOverlaps(nodes, size, options).forEach((id) => movedNodeIds.add(
      id));
  return movedNodeIds;
}

export function ensureReadableLayout(nodes, edges = [], nodeSize,
    options = {}) {
  if (layoutLooksStacked(nodes, nodeSize, options)) {
    return applyDeterministicLayout(nodes, edges, nodeSize, options);
  }
  return resolveNodeOverlaps(nodes, nodeSize, options);
}
