import {state} from './state.js';
import {serializeModel} from './diagram.js';
import {
  restoreTabGraphState,
  serializeRuntimeFragments,
  serializeRuntimeGraph,
  serializeRuntimeViews,
  syncActiveViewFromVisibleGraph
} from './graph-store.js';

const MAX_DIAGRAM_HISTORY = 100;

function isModelingType(typeKey = state.activeType) {
  return ["cim", "pim", "psm"].includes(typeKey);
}

function defaultModelName(typeKey = state.activeType) {
  return `${typeKey}-model`;
}

function signatureForDiagram(diagram) {
  return JSON.stringify(diagram || {nodes: [], connections: []});
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
    modelName: state.tabs[typeKey]?.modelName || defaultModelName(typeKey),
    baseModel: structuredClone(serializeModel()),
    diagram: structuredClone(state.diagram),
    graph: structuredClone(serializeRuntimeGraph()),
    views: structuredClone(serializeRuntimeViews()),
    fragments: structuredClone(serializeRuntimeFragments()),
    activeViewId: state.views?.activeViewId || null,
    signature: signatureForDiagram(state.diagram)
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

export function applyDiagramUndoSnapshot(snapshot) {
  if (!snapshot || !isModelingType(snapshot.typeKey)) {
    return false;
  }
  state.modelId = snapshot.modelId;
  state.baseModel = structuredClone(snapshot.baseModel);
  state.diagram = structuredClone(snapshot.diagram);
  state.selectedNodeId = null;
  state.selectedNodeIds = new Set();
  state.selectedConnectionId = null;
  state.selectedBoundedContextName = null;
  state.inlineLabelEditNodeId = null;
  if (state.tabs[snapshot.typeKey]) {
    state.tabs[snapshot.typeKey].modelId = snapshot.modelId;
    state.tabs[snapshot.typeKey].baseModel = structuredClone(
        snapshot.baseModel);
    state.tabs[snapshot.typeKey].diagram = structuredClone(snapshot.diagram);
    state.tabs[snapshot.typeKey].graph = structuredClone(snapshot.graph);
    state.tabs[snapshot.typeKey].views = structuredClone(snapshot.views);
    state.tabs[snapshot.typeKey].fragments = structuredClone(
        snapshot.fragments);
    state.tabs[snapshot.typeKey].activeViewId = snapshot.activeViewId;
    state.tabs[snapshot.typeKey].modelName = snapshot.modelName
        || defaultModelName(snapshot.typeKey);
  }
  restoreTabGraphState(snapshot.typeKey);
  return true;
}
