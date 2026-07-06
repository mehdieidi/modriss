import { state } from "./state.js";
import { serializeModel } from "./diagram.js";
import { isModelingLevel } from "./modeling-config-data.js";
import {
  restoreTabGraphState,
  serializeRuntimeFragments,
  serializeRuntimeGraph,
  serializeRuntimeViews,
  syncActiveViewFromVisibleGraph,
} from "./graph-store.js";

const MAX_DIAGRAM_HISTORY = 100;

function isModelingType(typeKey = state.activeType) {
  return isModelingLevel(typeKey);
}

function defaultModelName(typeKey = state.activeType) {
  return state.modelingConfig.config?.levels?.[typeKey]?.modelNameTemplate || `${typeKey}-model`;
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

export function captureDiagramUndoSnapshot(typeKey = state.activeType) {
  if (!isModelingType(typeKey)) {
    return null;
  }
  syncActiveViewFromVisibleGraph();
  return {
    typeKey,
    modelId: state.modelId,
    modelRevision: state.modelRevision || 0,
    modelName: state.tabs[typeKey]?.modelName || defaultModelName(typeKey),
    baseModel: structuredClone(serializeModel()),
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

export function applyDiagramUndoSnapshot(snapshot) {
  if (!snapshot || !isModelingType(snapshot.typeKey)) {
    return false;
  }
  if (snapshot.kind === "node-position") {
    return applyNodePositionUndoSnapshot(snapshot);
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
