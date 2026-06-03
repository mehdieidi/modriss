const DEFAULT_NODE_W = 228;
const DEFAULT_NODE_H = 112;
const CIM_NODE_W = 176;
const CIM_NODE_H = 96;
const LAYOUT_MARGIN = 48;
const GAP_X = 72;
const GAP_Y = 56;
const ELK_ALGORITHM_LAYERED = "layered";
const DEFAULT_NODE_SPACING = 112;
const DEFAULT_LAYER_SPACING = 188;
const ELK_PORT_SIZE = 10;

let elkInstance = null;

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

function browserElk() {
  if (elkInstance) {
    return elkInstance;
  }
  const ElkConstructor = window.ELK || window.Elk;
  if (!ElkConstructor) {
    return null;
  }
  elkInstance = new ElkConstructor();
  return elkInstance;
}

function normalizeLayoutPoint(point) {
  const x = Number(point?.x);
  const y = Number(point?.y);
  if (!Number.isFinite(x) || !Number.isFinite(y)) {
    return null;
  }
  return {x: Math.round(x), y: Math.round(y)};
}

function portId(nodeId, localPortId) {
  return `${nodeId}:${localPortId}`;
}

function layoutPortId(edgeId, endpoint) {
  return `${endpoint}-${String(edgeId || "").replaceAll(/[^a-zA-Z0-9_-]+/g,
      "_")}`;
}

function elkDirection(profile = "") {
  const normalized = String(profile || "").toUpperCase();
  if (normalized.includes("DOWN")) {
    return "DOWN";
  }
  if (normalized.includes("UP")) {
    return "UP";
  }
  if (normalized.includes("LEFT")) {
    return "LEFT";
  }
  return "RIGHT";
}

function elkLayoutOptions({
  profile = "",
  nodeSpacing = DEFAULT_NODE_SPACING,
  layerSpacing = DEFAULT_LAYER_SPACING
} = {}) {
  return {
    "elk.algorithm": ELK_ALGORITHM_LAYERED,
    "elk.direction": elkDirection(profile),
    "elk.edgeRouting": "ORTHOGONAL",
    "elk.padding": `[top=${LAYOUT_MARGIN},left=${LAYOUT_MARGIN},bottom=${LAYOUT_MARGIN},right=${LAYOUT_MARGIN}]`,
    "elk.spacing.nodeNode": String(Math.max(96, numeric(nodeSpacing, 112))),
    "elk.spacing.edgeEdge": "28",
    "elk.spacing.edgeNode": "36",
    "elk.spacing.portPort": "22",
    "elk.layered.spacing.nodeNodeBetweenLayers": String(
        Math.max(172, numeric(layerSpacing, 188))),
    "elk.layered.spacing.edgeNodeBetweenLayers": "52",
    "elk.layered.spacing.edgeEdgeBetweenLayers": "42",
    "elk.layered.crossingMinimization.strategy": "LAYER_SWEEP",
    "elk.layered.crossingMinimization.greedySwitch.type": "TWO_SIDED",
    "elk.layered.nodePlacement.strategy": "NETWORK_SIMPLEX",
    "elk.layered.nodePlacement.favorStraightEdges": "true",
    "elk.layered.considerModelOrder.strategy": "NODES_AND_EDGES",
    "elk.layered.thoroughness": "24",
    "elk.layered.mergeEdges": "false",
    "elk.layered.unnecessaryBendpoints": "true",
    "elk.separateConnectedComponents": "true",
    "elk.portConstraints": "FIXED_ORDER",
    "elk.portAlignment.default": "JUSTIFIED",
    "elk.hierarchyHandling": "INCLUDE_CHILDREN"
  };
}

function semanticPortSide(port) {
  const normalized = String(port?.id || "").toLowerCase();
  if (normalized.endsWith("-in") || normalized.startsWith("in-")
      || normalized.includes("input") || normalized === "flow-in"
      || normalized === "resource-in") {
    return "WEST";
  }
  return "EAST";
}

function collectLayoutPorts(nodes, edges, nodeIds) {
  const portsByNodeId = new Map(nodes.map((node) => [node.id, []]));
  const validEdges = edges.filter((edge) => nodeIds.has(edge.sourceNodeId)
      && nodeIds.has(edge.targetNodeId)
      && edge.sourceNodeId !== edge.targetNodeId);
  const sortedEdges = [...validEdges].sort((left, right) => {
    const sourceDiff = String(left.sourceNodeId || "").localeCompare(
        String(right.sourceNodeId || ""));
    if (sourceDiff) {
      return sourceDiff;
    }
    const targetDiff = String(left.targetNodeId || "").localeCompare(
        String(right.targetNodeId || ""));
    if (targetDiff) {
      return targetDiff;
    }
    return String(left.id || "").localeCompare(String(right.id || ""));
  });
  sortedEdges.forEach((edge) => {
    portsByNodeId.get(edge.sourceNodeId)?.push({
      id: layoutPortId(edge.id, "source"),
      side: semanticPortSide({id: edge.sourcePortId || "flow-out"}),
      edgeId: edge.id
    });
    portsByNodeId.get(edge.targetNodeId)?.push({
      id: layoutPortId(edge.id, "target"),
      side: semanticPortSide({id: edge.targetPortId || "flow-in"}),
      edgeId: edge.id
    });
  });
  portsByNodeId.forEach((ports) => {
    ports.sort((left, right) => {
      const sideDiff = left.side.localeCompare(right.side);
      if (sideDiff) {
        return sideDiff;
      }
      return String(left.edgeId || "").localeCompare(
          String(right.edgeId || ""));
    });
    ports.forEach((port, index) => {
      port.index = index;
    });
  });
  return portsByNodeId;
}

function toElkGraph({
  viewId = "modless-view",
  profile = "DEFAULT_LAYERED",
  options = {},
  nodes = [],
  edges = []
} = {}) {
  const nodeIds = new Set(nodes.map((node) => String(node.id || "")));
  const layoutPortsByNodeId = collectLayoutPorts(nodes, edges, nodeIds);
  return {
    id: viewId || "modless-view",
    layoutOptions: elkLayoutOptions({
      profile,
      nodeSpacing: options.nodeSpacing,
      layerSpacing: options.layerSpacing
    }),
    children: nodes.map((node) => ({
      id: node.id,
      width: Math.max(1, numeric(node.width, DEFAULT_NODE_W)),
      height: Math.max(1, numeric(node.height, DEFAULT_NODE_H)),
      labels: node.label ? [{text: String(node.label)}] : [],
      ports: (layoutPortsByNodeId.get(node.id) || []).map((port) => ({
        id: portId(node.id, port.id),
        width: ELK_PORT_SIZE,
        height: ELK_PORT_SIZE,
        layoutOptions: {
          "elk.port.side": port.side,
          "elk.port.index": String(port.index)
        }
      }))
    })),
    edges: edges.filter((edge) => nodeIds.has(edge.sourceNodeId)
        && nodeIds.has(edge.targetNodeId)
        && edge.sourceNodeId !== edge.targetNodeId).map((edge) => ({
      id: edge.id,
      sources: [portId(edge.sourceNodeId, layoutPortId(edge.id, "source"))],
      targets: [portId(edge.targetNodeId, layoutPortId(edge.id, "target"))],
      labels: edge.label ? [{text: String(edge.label)}] : []
    }))
  };
}

function normalizeElkResponse(layout) {
  const children = Array.isArray(layout?.children) ? layout.children : [];
  const minX = Math.min(...children.map((node) => numeric(node.x,
      LAYOUT_MARGIN)), LAYOUT_MARGIN);
  const minY = Math.min(...children.map((node) => numeric(node.y,
      LAYOUT_MARGIN)), LAYOUT_MARGIN);
  const shiftX = Math.round(LAYOUT_MARGIN - minX);
  const shiftY = Math.round(LAYOUT_MARGIN - minY);
  const shiftPoint = (point) => point ? {
    x: Math.round(point.x + shiftX),
    y: Math.round(point.y + shiftY)
  } : null;

  const nodes = children.map((node) => ({
    id: node.id,
    x: Math.round(numeric(node.x) + shiftX),
    y: Math.round(numeric(node.y) + shiftY),
    width: numeric(node.width, DEFAULT_NODE_W),
    height: numeric(node.height, DEFAULT_NODE_H)
  }));
  const edges = (Array.isArray(layout?.edges) ? layout.edges : []).map(
      (edge) => {
        const sections = (Array.isArray(edge.sections) ? edge.sections : [])
        .map((section) => {
          const startPoint = shiftPoint(normalizeLayoutPoint(
              section.startPoint));
          const endPoint = shiftPoint(normalizeLayoutPoint(section.endPoint));
          if (!startPoint || !endPoint) {
            return null;
          }
          const bendPoints = (Array.isArray(section.bendPoints)
              ? section.bendPoints : []).map(normalizeLayoutPoint)
          .filter(Boolean).map(shiftPoint);
          return {startPoint, endPoint, bendPoints};
        }).filter(Boolean);
        return {
          id: edge.id,
          sections,
          bendPoints: sections.flatMap((section) => section.bendPoints)
        };
      });
  return {nodes, edges, warnings: []};
}

export function isBrowserElkAvailable() {
  return Boolean(browserElk()?.layout);
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

export async function layoutWithBrowserElk(request = {}) {
  const elk = browserElk();
  if (!elk?.layout) {
    throw new Error("elkjs is not loaded in the browser.");
  }
  const graph = toElkGraph(request);
  const layout = await elk.layout(graph);
  return normalizeElkResponse(layout);
}

export function deterministicLayoutResponse(request = {}, nodeSize) {
  const nodes = (Array.isArray(request.nodes) ? request.nodes : []).map(
      (node) => ({
        id: node.id,
        label: node.label,
        x: numeric(node.x, LAYOUT_MARGIN),
        y: numeric(node.y, LAYOUT_MARGIN)
      }));
  const nodeLookup = new Map(nodes.map((node) => [node.id, node]));
  const edges = (Array.isArray(request.edges) ? request.edges : []).map(
      (edge) => ({
        id: edge.id,
        sourceId: edge.sourceNodeId,
        targetId: edge.targetNodeId
      })).filter((edge) => nodeLookup.has(edge.sourceId)
      && nodeLookup.has(edge.targetId));
  applyDeterministicLayout(nodes, edges, nodeSize, {
    gapX: numeric(request.options?.nodeSpacing, GAP_X),
    gapY: numeric(request.options?.layerSpacing, GAP_Y)
  });
  const size = normalizeNodeSize(nodeSize);
  return {
    nodes: nodes.map((node) => ({
      id: node.id,
      x: node.x,
      y: node.y,
      width: size.width,
      height: size.height
    })),
    edges: edges.map((edge) => {
      const source = nodeLookup.get(edge.sourceId);
      const target = nodeLookup.get(edge.targetId);
      const startPoint = {
        x: Math.round(source.x + size.width),
        y: Math.round(source.y + size.height / 2)
      };
      const endPoint = {
        x: Math.round(target.x),
        y: Math.round(target.y + size.height / 2)
      };
      const midX = Math.round((startPoint.x + endPoint.x) / 2);
      const needsBends = Math.abs(endPoint.x - startPoint.x) > 80
          && Math.abs(endPoint.y - startPoint.y) > 30;
      const bendPoints = needsBends ? [
        {x: midX, y: startPoint.y},
        {x: midX, y: endPoint.y}
      ] : [];
      return {
        id: edge.id,
        sections: [{startPoint, endPoint, bendPoints}],
        bendPoints
      };
    }),
    warnings: ["Browser elkjs was unavailable; used deterministic fallback layout."]
  };
}

export function ensureReadableLayout(nodes, edges = [], nodeSize,
    options = {}) {
  if (layoutLooksStacked(nodes, nodeSize, options)) {
    return applyDeterministicLayout(nodes, edges, nodeSize, options);
  }
  return resolveNodeOverlaps(nodes, nodeSize, options);
}
