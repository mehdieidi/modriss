import { state } from "./state.js";
import { emptyDiagram } from "./utils.js";
import {
  activeView,
  selectElementIdsForView,
  selectRelationshipIdsForView,
} from "./graph-store.js";
import { modelingElementDefinition } from "./modeling-config-data.js";

const CONTAINER_TYPES = {
  cim: new Set([
    "BoundedContextCandidate",
    "BusinessCapability",
    "BusinessProcess",
    "AggregateCandidate",
  ]),
  pim: new Set(["ServerlessService", "DeploymentUnit", "Workflow", "Api", "EventChannel"]),
  psm: new Set([
    "SamStack",
    "AwsStage",
    "ApiGatewayApi",
    "EventBridgeBus",
    "StepFunctionStateMachine",
    "IamRole",
  ]),
};

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function clone(value) {
  return value == null ? value : structuredClone(value);
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
  const kind = String(view?.kind || "")
    .trim()
    .toUpperCase();
  if (
    kind === "MAIN" ||
    kind === "FOCUS" ||
    view?.scope?.rootElementId ||
    view?.savedAt ||
    view?.sourceViewId
  ) {
    return false;
  }
  return Boolean(view?.definitionId || view?.viewpoint);
}

function pruneIsolatedGeneratedNodes(graph, view, elementIds, relationshipIds) {
  if (!shouldPruneIsolatedGeneratedNodes(view) || !relationshipIds.length) {
    return elementIds;
  }
  const connected = new Set();
  relationshipIds.forEach((relationshipId) => {
    const relationship = graph.relationshipsById.get(relationshipId);
    if (!relationship) {
      return;
    }
    connected.add(relationship.sourceElementId);
    connected.add(relationship.targetElementId);
  });
  return elementIds.filter((elementId) => connected.has(elementId));
}

export function isContainerElement(elementOrType, typeKey = state.activeType) {
  const type = typeof elementOrType === "string" ? elementOrType : elementType(elementOrType);
  if (CONTAINER_TYPES[typeKey]?.has(type)) {
    return true;
  }
  try {
    const definition = modelingElementDefinition(typeKey, type);
    if (
      !definition ||
      definition.relationshipElement ||
      definition.containedOnly ||
      definition.supportOnly
    ) {
      return false;
    }
    return safeArray(definition.references).some(
      (reference) => reference?.containment === true && reference?.many !== false,
    );
  } catch {
    return false;
  }
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
    meta: clone(element),
  };
}

function runtimeEdge(relationship, viewEdge = null) {
  if (!relationship) {
    return null;
  }
  return {
    id: relationship.id,
    sourceId: relationship.sourceElementId,
    targetId: relationship.targetElementId,
    kind: relationship.kind,
    pinPoints: safeArray(viewEdge?.pinPoints).map(clone),
    sourceAnchor: viewEdge?.sourceAnchor ? clone(viewEdge.sourceAnchor) : undefined,
    targetAnchor: viewEdge?.targetAnchor ? clone(viewEdge.targetAnchor) : undefined,
  };
}

function materializeViewGraph(view, elementIds, relationshipIds) {
  const viewNodes = viewNodeByElement(view);
  const viewEdges = viewEdgeByRelationship(view);
  const nodes = elementIds
    .map((elementId) => runtimeNode(elementId, viewNodes.get(elementId)))
    .filter(Boolean);
  const connections = relationshipIds
    .map((relationshipId) =>
      runtimeEdge(state.graph.relationshipsById.get(relationshipId), viewEdges.get(relationshipId)),
    )
    .filter(Boolean);
  return { nodes, connections };
}

export function materializeActiveView() {
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
  window.modlessViewAudit = {
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
