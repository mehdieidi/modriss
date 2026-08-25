import { state } from "./state.js";
import { serializeModel } from "./diagram.js";
import { isModelingLevel } from "./modeling-config-data.js";
import {
  restoreTabGraphState,
  serializeRuntimeFragments,
  serializeRuntimeGraph,
  serializeRuntimeViews,
  syncActiveViewFromVisibleGraph,
  addConnectionToGraphAndActiveView,
  addNodeToGraphAndActiveView,
  removeElementFromGraph,
  removeRelationshipFromGraph,
} from "./graph-store.js";

const MAX_DIAGRAM_HISTORY = 100;

function isModelingType(typeKey = state.activeType) {
  return isModelingLevel(typeKey);
}

function defaultModelName(typeKey = state.activeType) {
  const configured = state.modelingConfig.config?.levels?.[typeKey]?.modelNameTemplate;
  if (!configured)
    throw new Error(`Modeling config is missing a model name template for '${typeKey}'.`);
  return configured;
}

function signatureForDiagram(diagram) {
  return JSON.stringify(diagram || { nodes: [], connections: [] });
}

function historyStack(typeKey = state.activeType) {
  if (!isModelingType(typeKey)) {
    return [];
  }
  state.undo.diagramHistory[typeKey] ??= [];
  return state.undo.diagramHistory[typeKey];
}

export function clearDiagramUndoHistory(typeKey = state.activeType) {
  if (!isModelingType(typeKey)) {
    return;
  }
  state.undo.diagramHistory[typeKey] = [];
}

export function captureDiagramUndoSnapshot(typeKey = state.activeType, { syncView = true } = {}) {
  if (!isModelingType(typeKey)) {
    return null;
  }
  if (syncView) {
    syncActiveViewFromVisibleGraph();
  }
  return {
    typeKey,
    modelId: state.modelId,
    modelRevision: state.modelRevision || 0,
    modelName: state.tabs[typeKey]?.modelName || defaultModelName(typeKey),
    baseModel: structuredClone(serializeModel({ reconcileRelationships: true })),
    diagram: structuredClone(state.diagram),
    graph: structuredClone(serializeRuntimeGraph()),
    views: structuredClone(serializeRuntimeViews()),
    fragments: structuredClone(serializeRuntimeFragments()),
    activeViewId: state.views?.activeViewId || null,
    signature: signatureForDiagram(state.diagram),
  };
}

function positionSignature(positions) {
  return JSON.stringify(positions.map((position) => [position.id, position.x, position.y]));
}

export function captureNodePositionUndoSnapshot(nodeIds = [], typeKey = state.activeType) {
  if (!isModelingType(typeKey)) {
    return null;
  }
  const ids = [...new Set(nodeIds.map(String).filter(Boolean))];
  const positions = ids
    .map((id) => {
      const node =
        state.nodesById.get(id) || state.diagram.nodes.find((candidate) => candidate.id === id);
      return node ? { id, x: Math.round(node.x), y: Math.round(node.y) } : null;
    })
    .filter(Boolean);
  if (!positions.length) {
    return null;
  }
  return {
    kind: "node-position",
    typeKey,
    modelId: state.modelId,
    modelRevision: state.modelRevision || 0,
    modelName: state.tabs[typeKey]?.modelName || defaultModelName(typeKey),
    activeViewId: state.views?.activeViewId || null,
    positions,
    signature: `node-position:${positionSignature(positions)}`,
  };
}

export function captureAddElementUndoSnapshot(elementId, typeKey = state.activeType) {
  if (!isModelingType(typeKey) || !elementId) {
    return null;
  }
  return {
    kind: "add-element",
    typeKey,
    elementId: String(elementId),
    modelId: state.modelId,
    modelRevision: state.modelRevision || 0,
    modelName: state.tabs[typeKey]?.modelName || defaultModelName(typeKey),
    activeViewId: state.views?.activeViewId || null,
    signature: `add-element:${elementId}:${Date.now()}`,
  };
}

export function captureAddConnectionUndoSnapshot(connectionId, typeKey = state.activeType) {
  if (!isModelingType(typeKey) || !connectionId) {
    return null;
  }
  return {
    kind: "add-connection",
    typeKey,
    connectionId: String(connectionId),
    modelId: state.modelId,
    modelRevision: state.modelRevision || 0,
    modelName: state.tabs[typeKey]?.modelName || defaultModelName(typeKey),
    activeViewId: state.views?.activeViewId || null,
    signature: `add-connection:${connectionId}:${Date.now()}`,
  };
}

// Relationship-kind changes are frequent and must not clone the complete model.
export function captureConnectionUndoSnapshot(connection, typeKey = state.activeType) {
  if (!isModelingType(typeKey) || !connection?.id) {
    return null;
  }
  return {
    kind: "connection-mutation",
    typeKey,
    connection: structuredClone(connection),
    modelId: state.modelId,
    modelRevision: state.modelRevision || 0,
    modelName: state.tabs[typeKey]?.modelName || defaultModelName(typeKey),
    activeViewId: state.views?.activeViewId || null,
    signature: `connection-mutation:${connection.id}:${JSON.stringify(connection)}`,
  };
}

export function captureDeleteElementUndoSnapshot(
  node,
  connections = [],
  typeKey = state.activeType,
) {
  if (!isModelingType(typeKey) || !node?.id) return null;
  return {
    kind: "delete-element",
    typeKey,
    node: structuredClone(node),
    connections: structuredClone(connections),
    modelId: state.modelId,
    modelRevision: state.modelRevision || 0,
    modelName: state.tabs[typeKey]?.modelName || defaultModelName(typeKey),
    activeViewId: state.views?.activeViewId || null,
    signature: `delete-element:${node.id}:${Date.now()}`,
  };
}

export function pushDiagramUndoSnapshot(snapshot = captureDiagramUndoSnapshot()) {
  if (!snapshot || !isModelingType(snapshot.typeKey)) {
    return false;
  }
  const stack = historyStack(snapshot.typeKey);
  const last = stack[stack.length - 1];
  if (last?.signature === snapshot.signature) {
    return false;
  }
  stack.push(snapshot);
  while (stack.length > MAX_DIAGRAM_HISTORY) {
    stack.shift();
  }
  return true;
}

export function hasDiagramUndoHistory(typeKey = state.activeType) {
  return historyStack(typeKey).length > 0;
}

export function popDiagramUndoSnapshot(typeKey = state.activeType) {
  if (!isModelingType(typeKey)) {
    return null;
  }
  return historyStack(typeKey).pop() || null;
}

function applyNodePositionUndoSnapshot(snapshot) {
  const positions = Array.isArray(snapshot.positions) ? snapshot.positions : [];
  if (!positions.length) {
    return false;
  }
  positions.forEach((position) => {
    const id = String(position.id || "");
    const x = Math.round(Number(position.x));
    const y = Math.round(Number(position.y));
    if (!id || !Number.isFinite(x) || !Number.isFinite(y)) {
      return;
    }
    const node =
      state.nodesById.get(id) || state.diagram.nodes.find((candidate) => candidate.id === id);
    if (node) {
      node.x = x;
      node.y = y;
      node.meta = node.meta && typeof node.meta === "object" ? node.meta : {};
      node.meta.x = x;
      node.meta.y = y;
    }
    const element = state.graph?.elementsById?.get(id);
    if (element) {
      element.x = x;
      element.y = y;
    }
  });
  state.views?.byId?.forEach((view) => {
    const byId = new Map(positions.map((position) => [String(position.id), position]));
    (Array.isArray(view.nodes) ? view.nodes : []).forEach((viewNode) => {
      const position = byId.get(String(viewNode.elementId || ""));
      if (!position) {
        return;
      }
      viewNode.x = Math.round(Number(position.x));
      viewNode.y = Math.round(Number(position.y));
    });
  });
  return true;
}

function applyAddElementUndoSnapshot(snapshot) {
  const elementId = String(snapshot.elementId || "");
  if (!elementId) {
    return false;
  }
  removeElementFromGraph(elementId);
  state.diagram.nodes = safeArray(state.diagram.nodes).filter((node) => node.id !== elementId);
  state.diagram.connections = safeArray(state.diagram.connections).filter(
    (edge) => edge.sourceId !== elementId && edge.targetId !== elementId,
  );
  state.nodesById?.delete?.(elementId);
  state.selectedNodeId = null;
  state.selectedNodeIds = new Set();
  state.selectedConnectionId = null;
  state.inlineLabelEditNodeId = null;
  if (state.tabs[snapshot.typeKey]) {
    state.tabs[snapshot.typeKey].diagram = state.diagram;
  }
  restoreTabGraphState(snapshot.typeKey);
  return true;
}

function applyAddConnectionUndoSnapshot(snapshot) {
  const connectionId = String(snapshot.connectionId || "");
  if (!connectionId) {
    return false;
  }
  removeRelationshipFromGraph(connectionId);
  state.diagram.connections = safeArray(state.diagram.connections).filter(
    (edge) => edge.id !== connectionId,
  );
  state.connectionsById?.delete?.(connectionId);
  state.selectedConnectionId = null;
  if (state.tabs[snapshot.typeKey]) {
    state.tabs[snapshot.typeKey].diagram = state.diagram;
  }
  restoreTabGraphState(snapshot.typeKey);
  return true;
}

function applyConnectionMutationUndoSnapshot(snapshot) {
  const previous = snapshot.connection;
  if (!previous?.id) return false;
  let current = state.diagram.connections.find((edge) => edge.id === previous.id);
  if (current) {
    Object.assign(current, structuredClone(previous));
  } else {
    current = structuredClone(previous);
    state.diagram.connections.push(current);
  }
  removeRelationshipFromGraph(previous.id);
  addConnectionToGraphAndActiveView(current);
  state.connectionsById?.set?.(previous.id, current);
  if (state.tabs[snapshot.typeKey]) state.tabs[snapshot.typeKey].diagram = state.diagram;
  return true;
}

function applyDeleteElementUndoSnapshot(snapshot) {
  const node = snapshot.node;
  if (!node?.id || state.diagram.nodes.some((candidate) => candidate.id === node.id)) return false;
  state.diagram.nodes.push(structuredClone(node));
  addNodeToGraphAndActiveView(node);
  snapshot.connections.forEach((connection) => {
    if (!state.diagram.connections.some((edge) => edge.id === connection.id)) {
      state.diagram.connections.push(structuredClone(connection));
      addConnectionToGraphAndActiveView(connection);
    }
  });
  if (state.tabs[snapshot.typeKey]) state.tabs[snapshot.typeKey].diagram = state.diagram;
  return true;
}

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

export function applyDiagramUndoSnapshot(snapshot) {
  if (!snapshot || !isModelingType(snapshot.typeKey)) {
    return false;
  }
  if (snapshot.kind === "node-position") {
    return applyNodePositionUndoSnapshot(snapshot);
  }
  if (snapshot.kind === "add-element") {
    return applyAddElementUndoSnapshot(snapshot);
  }
  if (snapshot.kind === "add-connection") {
    return applyAddConnectionUndoSnapshot(snapshot);
  }
  if (snapshot.kind === "connection-mutation") {
    return applyConnectionMutationUndoSnapshot(snapshot);
  }
  if (snapshot.kind === "delete-element") {
    return applyDeleteElementUndoSnapshot(snapshot);
  }
  state.modelId = snapshot.modelId;
  state.modelRevision = snapshot.modelRevision || 0;
  state.baseModel = structuredClone(snapshot.baseModel);
  state.diagram = structuredClone(snapshot.diagram);
  state.selectedNodeId = null;
  state.selectedNodeIds = new Set();
  state.selectedConnectionId = null;
  state.inlineLabelEditNodeId = null;
  if (state.tabs[snapshot.typeKey]) {
    state.tabs[snapshot.typeKey].modelId = snapshot.modelId;
    state.tabs[snapshot.typeKey].modelRevision = snapshot.modelRevision || 0;
    state.tabs[snapshot.typeKey].baseModel = structuredClone(snapshot.baseModel);
    state.tabs[snapshot.typeKey].diagram = structuredClone(snapshot.diagram);
    state.tabs[snapshot.typeKey].graph = structuredClone(snapshot.graph);
    state.tabs[snapshot.typeKey].views = structuredClone(snapshot.views);
    state.tabs[snapshot.typeKey].fragments = structuredClone(snapshot.fragments);
    state.tabs[snapshot.typeKey].activeViewId = snapshot.activeViewId;
    state.tabs[snapshot.typeKey].modelName =
      snapshot.modelName || defaultModelName(snapshot.typeKey);
  }
  restoreTabGraphState(snapshot.typeKey);
  return true;
}
