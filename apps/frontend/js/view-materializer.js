import {state} from './state.js';
import {emptyDiagram} from './utils.js';
import {activeView} from './graph-store.js';

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

function descendantsOf(elementId, into = new Set()) {
  const children = state.graph.containmentByParent.get(elementId);
  if (!children) {
    return into;
  }
  children.forEach((childId) => {
    if (into.has(childId)) {
      return;
    }
    into.add(childId);
    descendantsOf(childId, into);
  });
  return into;
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
        || elementId === view?.scope?.rootElementId;
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
  return Boolean(CONTAINER_TYPES[typeKey]?.has(type));
}

function collapsedContainerIds(view, elementIds) {
  const visible = new Set(elementIds);
  const collapsed = new Set(safeArray(view?.collapsedElementIds));
  safeArray(view?.nodes).forEach((node) => {
    if (node?.collapsed) {
      collapsed.add(node.elementId);
    }
  });
  return [...collapsed].filter((elementId) => {
    const element = state.graph.elementsById.get(elementId);
    return visible.has(elementId) && isContainerElement(element);
  });
}

function summaryForCollapsed(containerId, visibleIds) {
  const descendants = [...descendantsOf(containerId)].filter(
      (id) => visibleIds.has(
          id));
  const descendantSet = new Set(descendants);
  const counts = {};
  descendants.forEach((elementId) => {
    const type = elementType(state.graph.elementsById.get(elementId));
    counts[type] = (counts[type] || 0) + 1;
  });
  let hiddenEdges = 0;
  state.graph.relationshipsById.forEach((relationship) => {
    if (descendantSet.has(relationship.sourceElementId) || descendantSet.has(
        relationship.targetElementId)) {
      hiddenEdges += 1;
    }
  });
  return {
    hiddenNodes: descendants.length,
    hiddenEdges,
    elementCounts: counts
  };
}

function runtimeNode(elementId, viewNode, collapsed = false, summary = null) {
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
    meta: {
      ...clone(element),
      __collapsed: collapsed,
      __collapsedSummary: summary
    }
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

function bundleKey(sourceId, targetId) {
  return `${sourceId}->${targetId}`;
}

function materializeCollapsedGraph(view, elementIds, relationshipIds) {
  const visibleIds = new Set(elementIds);
  const viewNodes = viewNodeByElement(view);
  const viewEdges = viewEdgeByRelationship(view);
  const collapsedIds = collapsedContainerIds(view, elementIds);
  const ownerByDescendant = new Map();
  const descendantsByOwner = new Map();

  collapsedIds.forEach((containerId) => {
    const descendants = [...descendantsOf(containerId)].filter(
        (id) => visibleIds.has(id));
    descendantsByOwner.set(containerId, new Set(descendants));
    descendants.forEach((descendantId) => {
      ownerByDescendant.set(descendantId, containerId);
    });
  });

  const hiddenDescendants = new Set(ownerByDescendant.keys());
  const nodes = [];
  elementIds.forEach((elementId) => {
    if (hiddenDescendants.has(elementId)) {
      return;
    }
    const summary = descendantsByOwner.has(elementId)
        ? summaryForCollapsed(elementId, visibleIds)
        : null;
    const node = runtimeNode(elementId, viewNodes.get(elementId),
        Boolean(summary), summary);
    if (node) {
      nodes.push(node);
    }
  });

  const bundles = new Map();
  const connections = [];
  relationshipIds.forEach((relationshipId) => {
    const relationship = state.graph.relationshipsById.get(relationshipId);
    if (!relationship) {
      return;
    }
    const sourceProxy = ownerByDescendant.get(relationship.sourceElementId)
        || relationship.sourceElementId;
    const targetProxy = ownerByDescendant.get(relationship.targetElementId)
        || relationship.targetElementId;
    if (sourceProxy === targetProxy) {
      return;
    }
    const sourceWasCollapsed = sourceProxy !== relationship.sourceElementId;
    const targetWasCollapsed = targetProxy !== relationship.targetElementId;
    if (sourceWasCollapsed || targetWasCollapsed) {
      const key = bundleKey(sourceProxy, targetProxy);
      let bundle = bundles.get(key);
      if (!bundle) {
        bundle = {
          id: `bundle-${sourceProxy}-${targetProxy}`,
          sourceId: sourceProxy,
          targetId: targetProxy,
          kind: "EDGE_BUNDLE",
          bundle: true,
          countsByKind: {},
          underlyingRelationshipIds: []
        };
        bundles.set(key, bundle);
      }
      bundle.countsByKind[relationship.kind] = (bundle.countsByKind[relationship.kind]
          || 0) + 1;
      bundle.underlyingRelationshipIds.push(relationship.id);
      return;
    }
    const edge = runtimeEdge(relationship, viewEdges.get(relationship.id));
    if (edge) {
      connections.push(edge);
    }
  });

  bundles.forEach((bundle) => {
    bundle.label = `${bundle.underlyingRelationshipIds.length} relations`;
    connections.push(bundle);
  });
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
  const collapsed = materializeCollapsedGraph(view, elementIds,
      relationshipIds);
  state.views.visibleNodeIds = new Set(collapsed.nodes.map((node) => node.id));
  state.views.visibleRelationshipIds = new Set(
      collapsed.connections.map((edge) => edge.id));
  state.views.collapsedContainers = new Set(collapsed.nodes.filter(
      (node) => node.meta?.__collapsed).map((node) => node.id));
  state.views.expandedContainers = new Set(elementIds.filter((elementId) => {
    const element = state.graph.elementsById.get(elementId);
    return isContainerElement(element) && !state.views.collapsedContainers.has(
        elementId);
  }));
  state.visibleGraph = {
    type: state.activeType,
    name: view.name || `${state.activeType}-view`,
    nodes: collapsed.nodes,
    connections: collapsed.connections
  };
  state.diagram = state.visibleGraph;
  return state.visibleGraph;
}

