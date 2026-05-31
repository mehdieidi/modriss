let drawFrame = 0;
let pendingRender = false;

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
    graph.draw?.();
  });
}

export function scheduleGraphRender(graph) {
  if (!graph || pendingRender) {
    return;
  }
  pendingRender = true;
  window.requestAnimationFrame(() => {
    pendingRender = false;
    graph.render?.();
  });
}

export function cancelScheduledDraw() {
  if (drawFrame) {
    window.cancelAnimationFrame(drawFrame);
    drawFrame = 0;
  }
  pendingRender = false;
}
