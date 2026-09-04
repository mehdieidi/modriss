import { state } from "./state.js";
import { emptyDiagram } from "./utils.js";
import {
  activeView,
  reconcileGraphRelationshipsIfDirty,
  selectElementIdsForView,
  selectRelationshipIdsForView,
} from "./graph-store.js";
import {
  modelingContainmentsForType,
  modelingContainerDefinition,
  modelingElementDefinition,
  modelingLevelConfig,
} from "./modeling-config-data.js";

export const CURRENT_LAYOUT_GEOMETRY_VERSION = 2;

export function viewNeedsAutoLayout(view) {
  return (
    !view?.autoLayoutApplied ||
    Number(view?.layoutGeometryVersion) !== CURRENT_LAYOUT_GEOMETRY_VERSION
  );
}

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function elementType(element) {
  return String(element?.eClass || element?.type || "Element");
}

function elementLabel(element) {
  return String(element?.name || element?.label || element?.id || "Element");
}

function viewNodeByElement(view) {
  return new Map(safeArray(view?.nodes).map((node) => [node.elementId, node]));
}

function viewEdgeByRelationship(view) {
  return new Map(safeArray(view?.edges).map((edge) => [edge.relationshipId, edge]));
}

function shouldPruneIsolatedGeneratedNodes(view) {
  return view?.pruneIsolated === true;
}

function pruneIsolatedGeneratedNodes(graph, view, elementIds, relationshipIds) {
  if (!shouldPruneIsolatedGeneratedNodes(view) || !relationshipIds.length) {
    return elementIds;
  }
  const connected = new Set();
  const pinned = new Set(safeArray(view?.pinnedElementIds).map(String));
  relationshipIds.forEach((relationshipId) => {
    const relationship = graph.relationshipsById.get(relationshipId);
    if (!relationship) {
      return;
    }
    connected.add(relationship.sourceElementId);
    connected.add(relationship.targetElementId);
  });
  return elementIds.filter((elementId) => connected.has(elementId) || pinned.has(elementId));
}

const containerTypeCache = new Map();
let containerCacheConfigVersion = -1;

function ensureContainerCacheFresh() {
  const version = Number(state.modelingConfig?.config?.version || 0);
  if (version !== containerCacheConfigVersion) {
    containerTypeCache.clear();
    containerCacheConfigVersion = version;
  }
}

function containerCacheKey(typeKey, type) {
  return `${typeKey}:${type}`;
}

function hasContainmentCapacity(typeKey, type, definition) {
  if (!definition) {
    return false;
  }
  const containments = modelingContainmentsForType(typeKey, type);
  if (containments.some((entry) => !entry.relationshipOnly)) {
    return true;
  }
  if (modelingContainerDefinition(typeKey, elementType(element))) {
    return true;
  }
  const palette = modelingLevelConfig(typeKey).containmentPalettes?.[type];
  return Boolean(palette?.types?.length);
}

export function isContainerElement(elementOrType, typeKey = state.activeType) {
  ensureContainerCacheFresh();
  const type = typeof elementOrType === "string" ? elementOrType : elementType(elementOrType);
  const cacheKey = containerCacheKey(typeKey, type);
  if (containerTypeCache.has(cacheKey)) {
    return containerTypeCache.get(cacheKey);
  }
  let result = false;
  try {
    const definition = modelingElementDefinition(typeKey, type);
    if (!definition || definition.relationshipElement || definition.supportOnly) {
      result = false;
    } else {
      result = hasContainmentCapacity(typeKey, type, definition);
    }
  } catch {
    result = false;
  }
  containerTypeCache.set(cacheKey, result);
  return result;
}

function runtimeNode(elementId, viewNode) {
  const element = state.graph.elementsById.get(elementId);
  if (!element) {
    return null;
  }
  const x = Number.isFinite(Number(viewNode?.x))
    ? Number(viewNode.x)
    : Number.isFinite(Number(element.x))
      ? Number(element.x)
      : 0;
  const y = Number.isFinite(Number(viewNode?.y))
    ? Number(viewNode.y)
    : Number.isFinite(Number(element.y))
      ? Number(element.y)
      : 0;
  return {
    id: elementId,
    type: elementType(element),
    label: elementLabel(element),
    x,
    y,
    width: Number.isFinite(Number(viewNode?.width)) ? Number(viewNode.width) : undefined,
    height: Number.isFinite(Number(viewNode?.height)) ? Number(viewNode.height) : undefined,
    meta: element,
  };
}

function runtimeEdge(relationship, viewEdge = null) {
  if (!relationship) {
    return null;
  }
  return {
    ...relationship,
    id: relationship.id,
    sourceId: relationship.sourceElementId,
    targetId: relationship.targetElementId,
    kind: relationship.kind,
    pinPoints: safeArray(viewEdge?.pinPoints).map((point) => ({ ...point })),
    sourceAnchor: viewEdge?.sourceAnchor ? { ...viewEdge.sourceAnchor } : undefined,
    targetAnchor: viewEdge?.targetAnchor ? { ...viewEdge.targetAnchor } : undefined,
  };
}

function materializeViewGraph(view, elementIds, relationshipIds) {
  const viewNodes = viewNodeByElement(view);
  const viewEdges = viewEdgeByRelationship(view);
  const nodes = elementIds
    .map((elementId) => runtimeNode(elementId, viewNodes.get(elementId)))
    .filter(Boolean);
  const nodeIds = new Set(nodes.map((node) => node.id));
  const connections = relationshipIds
    .map((relationshipId) =>
      runtimeEdge(state.graph.relationshipsById.get(relationshipId), viewEdges.get(relationshipId)),
    )
    .filter((connection) => {
      if (!connection?.sourceId || !connection?.targetId) {
        return false;
      }
      return nodeIds.has(connection.sourceId) && nodeIds.has(connection.targetId);
    });
  // Materialization is intentionally read-only. Any automatic layout is an explicit,
  // persisted operation; changing coordinates while merely selecting a view would overwrite
  // a user's saved layout when that view is later synchronized.
  return { nodes, connections };
}

export function materializeActiveView() {
  reconcileGraphRelationshipsIfDirty(state.activeType);
  const view = activeView();
  if (!view) {
    state.visibleGraph = emptyDiagram(state.activeType);
    state.diagram = state.visibleGraph;
    return state.visibleGraph;
  }
  const elementIds = selectElementIdsForView(state.graph, view, state.activeType);
  let relationshipIds = selectRelationshipIdsForView(state.graph, view, elementIds);
  const visibleElementIds = pruneIsolatedGeneratedNodes(
    state.graph,
    view,
    elementIds,
    relationshipIds,
  );
  if (visibleElementIds.length !== elementIds.length) {
    relationshipIds = selectRelationshipIdsForView(state.graph, view, visibleElementIds);
  }
  const visibleElementIdSet = new Set(visibleElementIds);
  const visible = materializeViewGraph(view, visibleElementIds, relationshipIds);
  const materializedNodeIds = new Set(visible.nodes.map((node) => node.id));
  const materializedEdgeIds = new Set(visible.connections.map((edge) => edge.id));
  window.varkaViewAudit = {
    viewId: view.id,
    graphNodes: state.graph.elementsById.size,
    graphEdges: state.graph.relationshipsById.size,
    selectedNodes: elementIds.length,
    visibleNodesAfterPrune: visibleElementIds.length,
    materializedNodes: visible.nodes.length,
    selectedEdges: relationshipIds.length,
    materializedEdges: visible.connections.length,
    prunedIsolatedNodeIds: elementIds.filter((id) => !visibleElementIdSet.has(id)),
    missingNodeIds: visibleElementIds.filter((id) => !materializedNodeIds.has(id)),
    missingEdgeIds: relationshipIds.filter((id) => !materializedEdgeIds.has(id)),
  };
  state.views.visibleNodeIds = materializedNodeIds;
  state.views.visibleRelationshipIds = materializedEdgeIds;
  state.views.expandedContainers = new Set(
    visibleElementIds.filter((elementId) => {
      const element = state.graph.elementsById.get(elementId);
      return isContainerElement(element);
    }),
  );
  state.visibleGraph = {
    type: state.activeType,
    name: view.name || `${state.activeType}-view`,
    nodes: visible.nodes,
    connections: visible.connections,
  };
  state.diagram = state.visibleGraph;
  return state.visibleGraph;
}
