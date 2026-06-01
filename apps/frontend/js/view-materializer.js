import {state} from './state.js';
import {emptyDiagram} from './utils.js';
import {activeView} from './graph-store.js';
import {
  modelingElementDefinition,
  modelingTypeMatches
} from './modeling-config-data.js';

const CONTAINER_TYPES = {
  cim: new Set([
    "BoundedContextCandidate",
    "BusinessCapability",
    "BusinessProcess",
    "AggregateCandidate"
  ]),
  pim: new Set([
    "ServerlessService",
    "DeploymentUnit",
    "Workflow",
    "Api",
    "EventChannel"
  ]),
  psm: new Set([
    "SamStack",
    "AwsStage",
    "ApiGatewayApi",
    "EventBridgeBus",
    "StepFunctionStateMachine",
    "IamRole"
  ])
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
  return new Map(safeArray(view?.edges).map((edge) => [
    edge.relationshipId,
    edge
  ]));
}

function selectedElementIds(view) {
  const hidden = new Set(safeArray(view?.hidden?.elementIds));
  const filterTypes = new Set(safeArray(view?.filters?.elementTypes));
  const candidates = safeArray(view?.nodes).length
      ? safeArray(view.nodes).map((node) => node.elementId)
      : [...state.graph.elementsById.keys()];
  return candidates.filter((elementId) => {
    if (hidden.has(elementId)) {
      return false;
    }
    const element = state.graph.elementsById.get(elementId);
    if (!element) {
      return false;
    }
    return !filterTypes.size || filterTypes.has(elementType(element))
        || [...filterTypes].some((expected) => {
          try {
            return modelingTypeMatches(state.activeType, expected,
                elementType(element));
          } catch {
            return false;
          }
        }) || elementId === view?.scope?.rootElementId;
  });
}

function selectedRelationshipIds(view, elementIds) {
  const hidden = new Set(safeArray(view?.hidden?.relationshipIds));
  const allowedKinds = new Set(safeArray(view?.filters?.relationshipKinds));
  const elementSet = new Set(elementIds);
  const edgeById = viewEdgeByRelationship(view);
  const result = [];
  state.graph.relationshipsById.forEach((relationship, relationshipId) => {
    if (hidden.has(relationshipId)) {
      return;
    }
    const viewEdge = edgeById.get(relationshipId);
    if (viewEdge?.visible === false) {
      return;
    }
    if (!elementSet.has(relationship.sourceElementId) || !elementSet.has(
        relationship.targetElementId)) {
      return;
    }
    if (allowedKinds.size && !allowedKinds.has(relationship.kind)) {
      return;
    }
    result.push(relationshipId);
  });
  return result;
}

export function isContainerElement(elementOrType, typeKey = state.activeType) {
  const type = typeof elementOrType === "string" ? elementOrType
      : elementType(elementOrType);
  if (CONTAINER_TYPES[typeKey]?.has(type)) {
    return true;
  }
  try {
    const definition = modelingElementDefinition(typeKey, type);
    if (!definition || definition.relationshipElement
        || definition.containedOnly
        || definition.supportOnly) {
      return false;
    }
    return safeArray(definition.references).some((reference) =>
        reference?.containment === true && reference?.many !== false);
  } catch {
    return false;
  }
}

function runtimeNode(elementId, viewNode) {
  const element = state.graph.elementsById.get(elementId);
  if (!element) {
    return null;
  }
  const x = Number.isFinite(Number(viewNode?.x)) ? Number(viewNode.x)
      : Number.isFinite(Number(element.x)) ? Number(element.x) : 0;
  const y = Number.isFinite(Number(viewNode?.y)) ? Number(viewNode.y)
      : Number.isFinite(Number(element.y)) ? Number(element.y) : 0;
  return {
    id: elementId,
    type: elementType(element),
    label: elementLabel(element),
    x,
    y,
    meta: clone(element)
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
    sourceAnchor: viewEdge?.sourceAnchor ? clone(viewEdge.sourceAnchor)
        : undefined,
    targetAnchor: viewEdge?.targetAnchor ? clone(viewEdge.targetAnchor)
        : undefined
  };
}

function materializeViewGraph(view, elementIds, relationshipIds) {
  const viewNodes = viewNodeByElement(view);
  const viewEdges = viewEdgeByRelationship(view);
  const nodes = elementIds.map((elementId) =>
      runtimeNode(elementId, viewNodes.get(elementId))).filter(Boolean);
  const connections = relationshipIds.map((relationshipId) =>
      runtimeEdge(state.graph.relationshipsById.get(relationshipId),
          viewEdges.get(relationshipId))).filter(Boolean);
  return {nodes, connections};
}

export function materializeActiveView() {
  const view = activeView();
  if (!view) {
    state.visibleGraph = emptyDiagram(state.activeType);
    state.diagram = state.visibleGraph;
    return state.visibleGraph;
  }
  const elementIds = selectedElementIds(view);
  const relationshipIds = selectedRelationshipIds(view, elementIds);
  const visible = materializeViewGraph(view, elementIds, relationshipIds);
  state.views.visibleNodeIds = new Set(visible.nodes.map((node) => node.id));
  state.views.visibleRelationshipIds = new Set(
      visible.connections.map((edge) => edge.id));
  state.views.expandedContainers = new Set(elementIds.filter((elementId) => {
    const element = state.graph.elementsById.get(elementId);
    return isContainerElement(element);
  }));
  state.visibleGraph = {
    type: state.activeType,
    name: view.name || `${state.activeType}-view`,
    nodes: visible.nodes,
    connections: visible.connections
  };
  state.diagram = state.visibleGraph;
  return state.visibleGraph;
}
