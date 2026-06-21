import { MODEL_TYPES } from "./config.js";
import { state } from "./state.js";
import { el } from "./dom.js";
import { api } from "./api.js";
import { escapeHtml, genId } from "./utils.js";
import { ensureReadableLayout } from "./layout-engine.js";
import { getDefaultNode, legalKinds, legalKindsBetween, saveStoredEdgeLayout } from "./diagram.js";
import {
  activeView,
  addConnectionToGraphAndActiveView,
  addNodeToGraphAndActiveView,
  persistNodePositionInActiveView,
  prepareViewNodeIndex,
  removeElementFromGraph,
  syncActiveViewFromVisibleGraph,
} from "./graph-store.js";
import { isContainerElement, materializeActiveView } from "./view-materializer.js";
import {
  modelingElementDefinition,
  modelingContainmentPalette,
  modelingContainmentEntryForChildType,
  modelingContainerFocusPolicy,
  modelingContainmentsForType,
  isModelingLevel,
  modelingLevelConfig,
  modelingPalette,
  modelingRootContainments,
  modelingRelationshipKindLabel,
  modelingRelationshipPresentation,
  modelingResolveEdgeEndpoints,
  modelingShortcutConnectorRules,
  modelingTypeMatches,
  modelingViewDefinition,
} from "./modeling-config-data.js";
import { addReferenceValue, modelTypeMatches } from "./model-utils.js";
import { setStatus } from "./status.js";
// NOTE: These imports form intentional circular references (ES module live bindings).
// All functions are only called at runtime (event handlers / async), never at module init.
import { markModelDirty } from "./model-save-ui.js";
import {
  closeAttributePanel,
  openAttributePanel,
  openBoundedContextPanel,
  openConnectionPanel,
} from "./attr-panel.js";
import { fetchImpact } from "./impact.js";
import {
  captureDiagramUndoSnapshot,
  captureNodePositionUndoSnapshot,
  pushDiagramUndoSnapshot,
} from "./undo.js";
import {
  addG6Edge,
  addG6Node,
  beginG6InlineLabelEdit,
  fitG6CanvasToDiagram,
  focusG6CanvasPoint,
  focusG6Node,
  getG6Editor,
  isG6Available,
  mountG6Editor,
  onG6ViewportChanged,
  refreshG6Edges,
  renderG6Diagram,
  resetG6CanvasView,
  setG6HoverEdge,
  setG6HoverNode,
  syncG6FromState,
  toGraphCoordinates,
  updateG6ConnectionState,
  updateG6ContextBoxes,
  updateG6Edge,
  updateG6ImpactState,
  updateG6Node,
  updateG6NodeIcons,
  updateG6Selection,
  updateG6Viewport,
  zoomG6CanvasBy,
} from "./graph-editor/g6-editor.js";
import {
  canvasToViewportPoint as graphCanvasToViewportPoint,
  mountNodeExploreToolbar,
} from "./graph-editor/g6-overlays.js";

function requiredConfiguredKind(value, context) {
  const kind = String(value || "").trim();
  if (!kind || !modelingLevelConfig(state.activeType).relationshipKinds.includes(kind)) {
    throw new Error(`Missing or unknown ${context} relationship kind: ${kind || "(empty)"}`);
  }
  return kind;
}

function configuredRelationshipSemantic(name) {
  return requiredConfiguredKind(
    modelingLevelConfig(state.activeType).relationshipSemantics?.[name],
    `relationshipSemantics.${name}`,
  );
}

const DEFAULT_NODE_W = 228;
const DEFAULT_NODE_H = 112;
const DEFAULT_BOUNDED_CONTEXT_NAME = "Core";
const PLACEHOLDER_ICON = "/assets/icons/placeholder.svg";
const edgeIdsByNodeId = new Map(); // nodeId -> Set(edgeId)

function boundedContextConfig() {
  try {
    return modelingLevelConfig(state.activeType).boundedContext || {};
  } catch {
    return {};
  }
}

function supportsBoundedContext() {
  return Boolean(boundedContextConfig().enabled);
}

function boundedContextType() {
  return String(boundedContextConfig().candidateType || "");
}
const connectionsById = state.connectionsById;
let hoveredEdgeId = null;
let inlineLabelEditStartLabel = "";
let inlineLabelEditUndoSnapshot = null;

function syncCanvasIndexesFromState() {
  state.nodesById.clear();
  edgeIdsByNodeId.clear();
  connectionsById.clear();
  state.diagram.nodes.forEach((node) => {
    if (nodeVisibleInCurrentCanvasMode(node)) {
      state.nodesById.set(node.id, node);
    }
  });
  state.diagram.connections.forEach((edge) => {
    if (!state.nodesById.has(edge.sourceId) || !state.nodesById.has(edge.targetId)) {
      return;
    }
    connectionsById.set(edge.id, edge);
    [edge.sourceId, edge.targetId].forEach((nodeId) => {
      if (!edgeIdsByNodeId.has(nodeId)) {
        edgeIdsByNodeId.set(nodeId, new Set());
      }
      edgeIdsByNodeId.get(nodeId).add(edge.id);
    });
  });
}

function refLabel(value) {
  if (!value) {
    return "";
  }
  if (typeof value === "string") {
    return value;
  }
  if (typeof value === "object") {
    return value.name || value.label || value.$ref || value.id || "";
  }
  return "";
}

function configuredEdgeLabel(edge) {
  if (edge.label) {
    return edge.label;
  }
  const key = String(edge.kind || "").toUpperCase();
  try {
    return edge.bundle
      ? edge.label || "Bundled relations"
      : modelingRelationshipKindLabel(state.activeType, key) ||
          key.toLowerCase().replaceAll("_", " ");
  } catch {
    return edge.bundle ? edge.label || "Bundled relations" : key.toLowerCase().replaceAll("_", " ");
  }
}

function configuredEdgePresentation(edge) {
  if (!isModelingLevel(state.activeType)) {
    return { className: "", markerStart: "", markerEnd: "arrow" };
  }
  return modelingRelationshipPresentation(state.activeType, edge);
}

function configuredNodeNotation(node) {
  if (!isModelingLevel(state.activeType)) {
    return null;
  }
  let definition = null;
  try {
    definition = modelingElementDefinition(state.activeType, node.type);
  } catch {
    definition = null;
  }
  if (definition?.notation) {
    const lineFields = Array.isArray(definition.notation.lineFields)
      ? definition.notation.lineFields
      : [];
    return {
      tag: definition.notation.tag || "element",
      line: (meta) => {
        for (const field of lineFields) {
          const value = meta?.[field];
          const text = Array.isArray(value) ? compactList(value) : compactRefCount(value);
          if (String(text || "").trim()) {
            return text;
          }
        }
        return "";
      },
    };
  }
  return null;
}

function normalizeViewText(value) {
  return String(value || "")
    .trim()
    .toLowerCase()
    .replaceAll(/[^a-z0-9]+/g, " ");
}

function activeConfiguredViewProfile(typeKey = state.activeType) {
  const view = activeView();
  try {
    return modelingViewDefinition(typeKey, view)?.viewpoint || view?.viewpoint || view?.kind || "";
  } catch {
    return view?.viewpoint || view?.kind || "";
  }
}

function compactRefCount(value) {
  if (Array.isArray(value)) {
    return value.length ? `${value.length}` : "";
  }
  if (value && typeof value === "object") {
    return refLabel(value);
  }
  return value ? String(value) : "";
}

function compactList(value, limit = 3) {
  const values = Array.isArray(value) ? value : value ? [value] : [];
  return values.slice(0, limit).map(refLabel).filter(Boolean).join(", ");
}

function detailRow(label, value) {
  const normalized = Array.isArray(value) ? compactList(value) : value;
  const text = String(normalized || "").trim();
  if (!text) {
    return "";
  }
  return `<div class="node-model-row"><span>${escapeHtml(label)}</span><strong>${escapeHtml(
    text,
  )}</strong></div>`;
}

function detailBadge(label, issue = false) {
  const text = String(label || "").trim();
  if (!text) {
    return "";
  }
  return `<span class="node-model-badge${issue ? " node-model-issue" : ""}">${escapeHtml(text)}</span>`;
}

function detailCompartment(title, rows) {
  const content = rows.filter(Boolean).join("");
  if (!content) {
    return "";
  }
  return `<div class="node-model-compartment"><div class="node-model-compartment-title">${escapeHtml(
    title,
  )}</div>${content}</div>`;
}

function configuredNodeDetailsHtml(node) {
  return metadataNodeDetailsHtml(state.activeType, node);
}

function metadataNodeDetailsHtml(typeKey, node) {
  const meta = node.meta || {};
  let definition = null;
  try {
    definition = modelingElementDefinition(typeKey, node.type);
  } catch {
    definition = null;
  }
  const badges = [];
  const addBadge = (label, issue = false) => {
    const text = String(label || "").trim();
    if (text) {
      badges.push(detailBadge(text, issue));
    }
  };
  [...(definition?.attributes || []), ...(definition?.references || [])].forEach((field) => {
    const value = meta[field.name];
    if (field?.fieldType === "boolean" && value === true) {
      addBadge(field.name.replaceAll(/([A-Z])/g, " $1").toLowerCase());
    } else if (field?.required && !compactRefCount(value)) {
      addBadge(`missing ${field.name}`, true);
    }
  });
  const visibleFields = Array.isArray(definition?.visibleFields) ? definition.visibleFields : [];
  const sections = [
    detailCompartment(
      "Detail",
      visibleFields
        .slice(0, 8)
        .map((field) =>
          detailRow(
            field,
            Array.isArray(meta[field]) ? compactList(meta[field]) : compactRefCount(meta[field]),
          ),
        ),
    ),
  ];
  const referenceRows = (definition?.references || [])
    .filter((reference) => !reference.containment && meta[reference.name])
    .slice(0, 5)
    .map((reference) =>
      detailRow(
        reference.name,
        Array.isArray(meta[reference.name])
          ? compactList(meta[reference.name])
          : compactRefCount(meta[reference.name]),
      ),
    );
  if (referenceRows.length) {
    sections.push(detailCompartment("References", referenceRows));
  }
  return badges.length || sections.some(Boolean)
    ? `<div class="node-model-details model-node-details">${
        badges.length ? `<div class="node-model-badges">${badges.join("")}</div>` : ""
      }${sections.join("")}</div>`
    : "";
}

function commitUndoSnapshot(snapshot) {
  if (!snapshot) {
    return false;
  }
  return pushDiagramUndoSnapshot(snapshot);
}

function dragUndoSnapshot(nodeIds = []) {
  return nodeIds.length ? captureNodePositionUndoSnapshot(nodeIds) : captureDiagramUndoSnapshot();
}

function getNodeWidth() {
  try {
    return Number(modelingLevelConfig(state.activeType).canvasPolicy?.nodeWidth) || DEFAULT_NODE_W;
  } catch {
    return DEFAULT_NODE_W;
  }
}

function getNodeHeight() {
  try {
    return Number(modelingLevelConfig(state.activeType).canvasPolicy?.nodeHeight) || DEFAULT_NODE_H;
  } catch {
    return DEFAULT_NODE_H;
  }
}

export function getCurrentDiagramNodeSize() {
  return { width: getNodeWidth(), height: getNodeHeight() };
}

function normalizePinPoint(point) {
  const x = Number(point?.x);
  const y = Number(point?.y);
  if (!Number.isFinite(x) || !Number.isFinite(y)) {
    return null;
  }
  return {
    x: Math.round(x),
    y: Math.round(y),
  };
}

function normalizeEdgeAnchor(anchor) {
  if (!anchor || typeof anchor !== "object") {
    return null;
  }
  const side = anchor.side === "left" ? "left" : anchor.side === "right" ? "right" : null;
  const offsetY = Math.round(Number(anchor.offsetY));
  if (!side || !Number.isFinite(offsetY)) {
    return null;
  }
  return { side, offsetY };
}

export function edgePresentationFromLayout(layout, sourceNode, targetNode) {
  const pinPoints = Array.isArray(layout?.bendPoints)
    ? layout.bendPoints.map(normalizePinPoint).filter(Boolean)
    : [];
  const sections = Array.isArray(layout?.sections) ? layout.sections : [];
  const firstSection = sections[0];
  const lastSection = sections[sections.length - 1];
  const sourceAnchor =
    sourceNode && firstSection?.startPoint
      ? normalizeEdgeAnchor({
          side:
            Number(firstSection.startPoint.x) >= sourceNode.x + getNodeWidth() / 2
              ? "right"
              : "left",
          offsetY: Number(firstSection.startPoint.y) - sourceNode.y,
        })
      : null;
  const targetAnchor =
    targetNode && lastSection?.endPoint
      ? normalizeEdgeAnchor({
          side:
            Number(lastSection.endPoint.x) >= targetNode.x + getNodeWidth() / 2 ? "right" : "left",
          offsetY: Number(lastSection.endPoint.y) - targetNode.y,
        })
      : null;
  return {
    pinPoints,
    sourceAnchor,
    targetAnchor,
  };
}

export function pinPointsFromEdgeLayout(layout) {
  return edgePresentationFromLayout(layout, null, null).pinPoints;
}

function persistEdgePinPoints(edge) {
  if (!edge?.id) {
    return;
  }
  saveStoredEdgeLayout(state.activeType, edge.id, {
    pinPoints: Array.isArray(edge.pinPoints)
      ? edge.pinPoints.map(normalizePinPoint).filter(Boolean)
      : [],
    sourceAnchor: normalizeEdgeAnchor(edge.sourceAnchor),
    targetAnchor: normalizeEdgeAnchor(edge.targetAnchor),
  });
}

function clearTransientEdgeLayouts(edgeIds = null) {
  const edges = edgeIds
    ? [...edgeIds].map((edgeId) => connectionsById.get(edgeId)).filter(Boolean)
    : state.diagram.connections;
  edges.forEach((edge) => {
    if (!Array.isArray(edge.pinPoints) && edge.layout) {
      const presentation = edgePresentationFromLayout(edge.layout, null, null);
      edge.pinPoints = presentation.pinPoints;
      edge.sourceAnchor = normalizeEdgeAnchor(edge.sourceAnchor) || presentation.sourceAnchor;
      edge.targetAnchor = normalizeEdgeAnchor(edge.targetAnchor) || presentation.targetAnchor;
      persistEdgePinPoints(edge);
    }
    delete edge.layout;
  });
}

function normalizeContextName(value) {
  return String(value ?? "")
    .trim()
    .replace(/\s+/g, " ");
}

function isValidContextName(value) {
  return /^[A-Za-z0-9 _-]{1,80}$/.test(value);
}

function assignContextName(node, contextName) {
  if (!node || !contextName) {
    return false;
  }
  node.meta = node.meta && typeof node.meta === "object" ? node.meta : {};
  node.meta.contextName = contextName;
  syncNodeMetaToGraph(node);
  return true;
}

export function contextNameFromNode(node) {
  const meta = node?.meta || {};
  const explicit = normalizeContextName(meta.contextName);
  if (explicit) {
    return explicit;
  }
  const context = meta.context;
  if (typeof context === "string") {
    return normalizeContextName(context);
  }
  if (context && typeof context === "object") {
    return normalizeContextName(context.name || context.id || context.$ref);
  }
  return "";
}

function contextNodes(contextName) {
  return state.diagram.nodes.filter((node) => contextNameFromNode(node) === contextName);
}

function isBoundedContextNode(node) {
  const candidateType = boundedContextType();
  return Boolean(candidateType && node?.type === candidateType);
}

function boundedContextNameFromContextNode(node) {
  return normalizeContextName(node?.label || node?.meta?.name || node?.id);
}

function contextNodeForName(contextName) {
  const normalized = normalizeContextName(contextName);
  return (
    state.diagram.nodes.find(
      (node) =>
        isBoundedContextNode(node) && boundedContextNameFromContextNode(node) === normalized,
    ) || null
  );
}

function syncNodeMetaToGraph(node) {
  if (!node?.id || !state.graph?.elementsById) {
    return;
  }
  const element = state.graph.elementsById.get(node.id);
  if (!element) {
    return;
  }
  Object.assign(element, node.meta || {}, {
    id: node.id,
    eClass: node.type || element.eClass,
    name: node.label || element.name,
    label: node.label || element.label,
    x: node.x,
    y: node.y,
  });
}

function boundedContextNameSet() {
  const names = new Set();
  if (!supportsBoundedContext()) {
    return names;
  }
  (state.baseModel?.boundedContexts || []).forEach((context) => {
    const name = normalizeContextName(context?.name);
    if (name) {
      names.add(name);
    }
  });
  state.diagram.nodes.forEach((node) => {
    if (isBoundedContextNode(node)) {
      const name = boundedContextNameFromContextNode(node);
      if (name) {
        names.add(name);
      }
      return;
    }
    const name = contextNameFromNode(node);
    if (name) {
      names.add(name);
    }
  });
  return names;
}

function boundsForContextMembers(memberIds = []) {
  const memberNodes = memberIds
    .map((nodeId) => state.nodesById.get(nodeId))
    .filter((node) => node && !isBoundedContextNode(node));
  if (!memberNodes.length) {
    return null;
  }
  const nodeW = getNodeWidth();
  const nodeH = getNodeHeight();
  const minX = Math.min(...memberNodes.map((node) => node.x));
  const minY = Math.min(...memberNodes.map((node) => node.y));
  const maxX = Math.max(...memberNodes.map((node) => node.x + nodeW));
  const maxY = Math.max(...memberNodes.map((node) => node.y + nodeH));
  return { minX, minY, maxX, maxY };
}

function contextNodePosition(contextName, memberIds = []) {
  const bounds = boundsForContextMembers(memberIds);
  if (bounds) {
    return {
      x: Math.max(40, bounds.minX - getNodeWidth() - 70),
      y: Math.max(40, bounds.minY),
    };
  }
  const existingCount = state.diagram.nodes.filter(isBoundedContextNode).length;
  const rect = el.canvasViewport?.getBoundingClientRect();
  const center = rect
    ? toCanvasCoordinates(rect.left + rect.width / 2, rect.top + rect.height / 2)
    : { x: 160, y: 140 };
  return {
    x: Math.round(center.x + (existingCount % 3) * 220),
    y: Math.round(center.y + Math.floor(existingCount / 3) * 150),
  };
}

function ensureBoundedContextNodeForName(contextName, memberIds = []) {
  const normalized = normalizeContextName(contextName);
  const candidateType = boundedContextType();
  if (!normalized || !supportsBoundedContext() || !candidateType) {
    return null;
  }
  const existing = contextNodeForName(normalized);
  if (existing) {
    return existing;
  }
  const position = contextNodePosition(normalized, memberIds);
  const node = getDefaultNode(state.activeType, candidateType, position.x, position.y);
  node.label = normalized;
  node.meta.name = normalized;
  node.meta.label = normalized;
  node.meta.languageBoundary ??= "";
  node.meta.ownershipBoundary ??= "";
  node.meta.externalIntegrationBoundary ??= false;
  state.diagram.nodes.push(node);
  addNodeToGraphAndActiveView(node);
  return node;
}

function ensureBoundedContextNodesForAllNames() {
  let created = 0;
  boundedContextNameSet().forEach((contextName) => {
    const memberIds = contextNodes(contextName).map((node) => node.id);
    if (
      !contextNodeForName(contextName) &&
      ensureBoundedContextNodeForName(contextName, memberIds)
    ) {
      created += 1;
    }
  });
  if (created) {
    syncActiveViewFromVisibleGraph();
  }
  return created;
}

function boundedContextFeatureForNode(node) {
  const rules = Array.isArray(boundedContextConfig().membershipFeatures)
    ? boundedContextConfig().membershipFeatures
    : [];
  return (
    rules.find((rule) => modelTypeMatches(state.activeType, node, String(rule?.sourceType || "")))
      ?.feature || ""
  );
}

function syncBoundedContextMembershipRefs(contextName) {
  const contextNode = contextNodeForName(contextName);
  if (!contextNode) {
    return;
  }
  const refs = {
    capabilities: [],
    entities: [],
    commands: [],
    queries: [],
    events: [],
    policies: [],
  };
  contextNodes(contextName).forEach((node) => {
    const feature = boundedContextFeatureForNode(node);
    if (feature) {
      refs[feature].push(node.id);
    }
  });
  contextNode.meta =
    contextNode.meta && typeof contextNode.meta === "object" ? contextNode.meta : {};
  Object.assign(contextNode.meta, refs);
  syncNodeMetaToGraph(contextNode);
  const graphElement = state.graph?.elementsById?.get(contextNode.id);
  if (graphElement) {
    Object.assign(graphElement, refs);
  }
}

function nodeVisibleInBoundedContextMode(node) {
  if (!supportsBoundedContext()) {
    return true;
  }
  if (state.boundedContextViewMode === "overview") {
    return isBoundedContextNode(node);
  }
  if (state.boundedContextViewMode === "focus") {
    const active = normalizeContextName(state.activeBoundedContextName);
    return Boolean(active && contextNameFromNode(node) === active);
  }
  return !isBoundedContextNode(node);
}

function visibleBoundedContextNodeIds() {
  return new Set(
    state.diagram.nodes.filter(nodeVisibleInBoundedContextMode).map((node) => node.id),
  );
}

function elementType(element) {
  return String(element?.eClass || element?.type || "Element");
}

function elementLabel(element) {
  return String(element?.name || element?.label || element?.id || "Element");
}

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function focusStack() {
  state.canvasFocusStack = Array.isArray(state.canvasFocusStack) ? state.canvasFocusStack : [];
  return state.canvasFocusStack;
}

export function activeCanvasFocus() {
  const stack = focusStack();
  const current = stack[stack.length - 1] || null;
  if (
    !current ||
    current.typeKey !== state.activeType ||
    state.views.activeViewId !== current.focusViewId ||
    !state.views.byId.has(current.focusViewId)
  ) {
    return null;
  }
  return current;
}

export function canvasFocusLabel() {
  const focus = activeCanvasFocus();
  return focus ? `${focus.label || focus.elementId}` : "";
}

function collectContainedDescendantIds(elementId, into = new Set()) {
  const children = state.graph?.containmentByParent?.get(elementId);
  if (!children) {
    return into;
  }
  children.forEach((childId) => {
    if (!childId || into.has(childId)) {
      return;
    }
    into.add(childId);
    collectContainedDescendantIds(childId, into);
  });
  return into;
}

function collectNeighborhoodElementIds(elementId, maxDepth = 1) {
  const normalizedDepth = Math.max(1, Math.min(2, Number(maxDepth) || 1));
  const visited = new Set([elementId]);
  const queue = [{ id: elementId, depth: 0 }];
  while (queue.length) {
    const current = queue.shift();
    if (!current || current.depth >= normalizedDepth) {
      continue;
    }
    const connected = new Set();
    state.graph?.relationshipsBySource?.get(current.id)?.forEach((relationshipId) => {
      const relationship = state.graph.relationshipsById.get(relationshipId);
      const targetId = relationship?.targetElementId || relationship?.target;
      if (targetId) {
        connected.add(targetId);
      }
    });
    state.graph?.relationshipsByTarget?.get(current.id)?.forEach((relationshipId) => {
      const relationship = state.graph.relationshipsById.get(relationshipId);
      const sourceId = relationship?.sourceElementId || relationship?.source;
      if (sourceId) {
        connected.add(sourceId);
      }
    });
    connected.forEach((nextId) => {
      if (!nextId || visited.has(nextId) || !state.graph.elementsById.has(nextId)) {
        return;
      }
      visited.add(nextId);
      queue.push({ id: nextId, depth: current.depth + 1 });
    });
  }
  return visited;
}

function relationshipsWithinElementSet(elementSet) {
  const relationships = [];
  const seen = new Set();
  if (!elementSet?.size) {
    return relationships;
  }
  if (!state.graph?.relationshipsBySource?.size) {
    state.graph?.relationshipsById?.forEach((relationship) => {
      const sourceId = relationship.sourceElementId || relationship.source;
      const targetId = relationship.targetElementId || relationship.target;
      if (elementSet.has(sourceId) && elementSet.has(targetId)) {
        relationships.push(relationship);
      }
    });
    return relationships;
  }
  elementSet.forEach((sourceId) => {
    state.graph.relationshipsBySource.get(sourceId)?.forEach((relationshipId) => {
      if (seen.has(relationshipId)) {
        return;
      }
      const relationship = state.graph.relationshipsById.get(relationshipId);
      const targetId = relationship?.targetElementId || relationship?.target;
      if (!relationship || !elementSet.has(targetId)) {
        return;
      }
      seen.add(relationshipId);
      relationships.push(relationship);
    });
  });
  return relationships;
}

function viewNodesByElement(view) {
  return new Map(safeArray(view?.nodes).map((node) => [node.elementId, node]));
}

function viewEdgesByRelationship(view) {
  return new Map(safeArray(view?.edges).map((edge) => [edge.relationshipId, edge]));
}

function nodeForFocusElement(elementId, viewNode) {
  const element = state.graph?.elementsById?.get(elementId);
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
    meta: structuredClone(element),
  };
}

function edgeForFocusRelationship(relationship, viewEdge = null) {
  if (!relationship) {
    return null;
  }
  return {
    id: relationship.id,
    sourceId: relationship.sourceElementId || relationship.source,
    targetId: relationship.targetElementId || relationship.target,
    kind: relationship.kind,
    pinPoints: safeArray(viewEdge?.pinPoints).map((point) => structuredClone(point)),
    sourceAnchor: viewEdge?.sourceAnchor ? structuredClone(viewEdge.sourceAnchor) : undefined,
    targetAnchor: viewEdge?.targetAnchor ? structuredClone(viewEdge.targetAnchor) : undefined,
  };
}

function focusViewIdFor(elementId) {
  return `view-${state.activeType}-focus-${String(elementId || "").replaceAll(
    /[^A-Za-z0-9_-]+/g,
    "-",
  )}-${Date.now().toString(36)}`;
}

function containerFocusOwnerType(focus) {
  if (!focus?.elementId) {
    return String(focus?.elementType || "");
  }
  const element = state.graph?.elementsById?.get(focus.elementId);
  return String(element?.eClass || element?.type || focus.elementType || "");
}

function activeContainerFocus() {
  const focus = activeCanvasFocus();
  if (!focus?.elementId) {
    return null;
  }
  const view = activeView();
  const policy = modelingContainerFocusPolicy(state.activeType);
  const viewKind = String(policy.viewKind || "FOCUS").toUpperCase();
  const scopeKind = String(policy.scopeKind || "CONTAINER").toUpperCase();
  if (
    !view ||
    String(view.kind || "").toUpperCase() !== viewKind ||
    String(view.scope?.scopeKind || "").toUpperCase() !== scopeKind ||
    String(view.scope?.rootElementId || "") !== String(focus.elementId)
  ) {
    return null;
  }
  return {
    ...focus,
    elementType: containerFocusOwnerType(focus),
  };
}

function createContainerFocusView(node) {
  const previousView = activeView();
  const focusPolicy = modelingContainerFocusPolicy(state.activeType);
  const descendantIds = [...collectContainedDescendantIds(node.id)];
  const descendantSet = new Set(descendantIds);
  const viewNodePositions = viewNodesByElement(previousView);
  const edges = relationshipsWithinElementSet(descendantSet).map((relationship) => {
    const sourceId = relationship.sourceElementId || relationship.source;
    const targetId = relationship.targetElementId || relationship.target;
    return {
      relationshipId: relationship.id,
      sourceId,
      targetId,
      visible: true,
    };
  });
  const focusNodes = descendantIds.map((elementId) => ({
    elementId,
    ...(viewNodePositions.get(elementId) || {}),
  }));
  const fakeNodes = focusNodes.map((entry, index) => {
    const element = state.graph.elementsById.get(entry.elementId) || {};
    return {
      id: entry.elementId,
      x: Number.isFinite(Number(entry.x))
        ? Number(entry.x)
        : Number.isFinite(Number(element.x))
          ? Number(element.x)
          : 80 + (index % 3) * 250,
      y: Number.isFinite(Number(entry.y))
        ? Number(entry.y)
        : Number.isFinite(Number(element.y))
          ? Number(element.y)
          : 80 + Math.floor(index / 3) * 170,
    };
  });
  ensureReadableLayout(fakeNodes, edges, getCurrentDiagramNodeSize());
  const positionById = new Map(fakeNodes.map((entry) => [entry.id, entry]));
  const edgePositions = viewEdgesByRelationship(previousView);
  return {
    id: focusViewIdFor(node.id),
    name: `${node.label || node.id} Contents`,
    level: String(state.activeType || "").toUpperCase(),
    kind: String(focusPolicy.viewKind || "FOCUS"),
    scope: {
      rootElementId: node.id,
      scopeKind: String(focusPolicy.scopeKind || "CONTAINER"),
      depth: 999,
    },
    filters: { elementTypes: [], relationshipKinds: [] },
    layoutProfile: String(focusPolicy.layoutProfile || "CONTAINER_FOCUS"),
    autoLayoutApplied: false,
    nodes: focusNodes.map((entry) => {
      const position = positionById.get(entry.elementId) || entry;
      return {
        ...entry,
        x: Number(position.x) || 0,
        y: Number(position.y) || 0,
      };
    }),
    edges: edges.map((edge) => ({
      relationshipId: edge.relationshipId,
      sourceId: edge.sourceId,
      targetId: edge.targetId,
      visible: true,
      ...(edgePositions.get(edge.relationshipId) || {}),
    })),
    hidden: { elementIds: [], relationshipIds: [] },
  };
}

function createNeighborhoodFocusView(node, depth = 1) {
  const previousView = activeView();
  const neighborhoodSet = collectNeighborhoodElementIds(node.id, depth);
  const elementIds = [...neighborhoodSet];
  const viewNodePositions = viewNodesByElement(previousView);
  const edgePositions = viewEdgesByRelationship(previousView);
  const edges = relationshipsWithinElementSet(neighborhoodSet).map((relationship) => {
    const sourceId = relationship.sourceElementId || relationship.source;
    const targetId = relationship.targetElementId || relationship.target;
    return {
      relationshipId: relationship.id,
      sourceId,
      targetId,
      visible: true,
    };
  });
  const focusNodes = elementIds.map((elementId) => ({
    elementId,
    ...(viewNodePositions.get(elementId) || {}),
  }));
  const fakeNodes = focusNodes.map((entry, index) => {
    const element = state.graph.elementsById.get(entry.elementId) || {};
    return {
      id: entry.elementId,
      x: Number.isFinite(Number(entry.x))
        ? Number(entry.x)
        : Number.isFinite(Number(element.x))
          ? Number(element.x)
          : 80 + (index % 4) * 250,
      y: Number.isFinite(Number(entry.y))
        ? Number(entry.y)
        : Number.isFinite(Number(element.y))
          ? Number(element.y)
          : 80 + Math.floor(index / 4) * 170,
    };
  });
  ensureReadableLayout(fakeNodes, edges, getCurrentDiagramNodeSize());
  const positionById = new Map(fakeNodes.map((entry) => [entry.id, entry]));
  const normalizedDepth = Math.max(1, Math.min(2, Number(depth) || 1));
  return {
    id: focusViewIdFor(`${node.id}-n${normalizedDepth}`),
    name: `${node.label || node.id} Neighborhood ${normalizedDepth}`,
    level: String(state.activeType || "").toUpperCase(),
    kind: "FOCUS",
    scope: {
      rootElementId: node.id,
      scopeKind: "NEIGHBORHOOD",
      depth: normalizedDepth,
    },
    filters: { elementTypes: [], relationshipKinds: [] },
    layoutProfile: "FOCUS_NEIGHBORHOOD",
    autoLayoutApplied: false,
    nodes: focusNodes.map((entry) => {
      const position = positionById.get(entry.elementId) || entry;
      return {
        ...entry,
        x: Number(position.x) || 0,
        y: Number(position.y) || 0,
      };
    }),
    edges: edges.map((edge) => ({
      relationshipId: edge.relationshipId,
      sourceId: edge.sourceId,
      targetId: edge.targetId,
      visible: true,
      ...(edgePositions.get(edge.relationshipId) || {}),
    })),
    hidden: { elementIds: [], relationshipIds: [] },
  };
}

export function openContainerFocus(elementId) {
  const node =
    state.nodesById.get(elementId) ||
    state.diagram.nodes.find((candidate) => candidate.id === elementId);
  if (!node || !isContainerElement(node)) {
    return false;
  }
  const previousViewId = state.views.activeViewId;
  const focusView = createContainerFocusView(node);
  prepareViewNodeIndex(focusView);
  state.views.byId.set(focusView.id, focusView);
  state.views.activeViewId = focusView.id;
  focusStack().push({
    typeKey: state.activeType,
    elementId: node.id,
    elementType: node.type,
    label: node.label || node.id,
    previousViewId,
    focusViewId: focusView.id,
  });
  state.selectedNodeId = null;
  state.selectedNodeIds = new Set();
  state.selectedConnectionId = null;
  materializeActiveView();
  renderDiagram();
  renderPalette();
  notifyModelToolsChanged();
  const descendantCount = collectContainedDescendantIds(node.id).size;
  setStatus(
    descendantCount
      ? `Opened ${node.label || node.id}. Use Back to return.`
      : `Opened empty ${node.label || node.id}. Drag elements from the palette to add contained details.`,
  );
  return true;
}

export function openNeighborhoodFocus(elementId, depth = 1) {
  const node =
    state.nodesById.get(elementId) ||
    state.diagram.nodes.find((candidate) => candidate.id === elementId);
  if (!node) {
    setStatus("Select an element before opening a neighborhood focus.");
    return false;
  }
  const neighborhood = collectNeighborhoodElementIds(node.id, depth);
  if (neighborhood.size <= 1) {
    setStatus("This element has no connected neighbors in the current model.");
    return false;
  }
  const previousViewId = state.views.activeViewId;
  const normalizedDepth = Math.max(1, Math.min(2, Number(depth) || 1));
  const focusView = createNeighborhoodFocusView(node, normalizedDepth);
  prepareViewNodeIndex(focusView);
  state.views.byId.set(focusView.id, focusView);
  state.views.activeViewId = focusView.id;
  focusStack().push({
    typeKey: state.activeType,
    elementId: node.id,
    elementType: node.type,
    label: `${node.label || node.id} depth ${normalizedDepth}`,
    previousViewId,
    focusViewId: focusView.id,
  });
  state.selectedNodeId = null;
  state.selectedNodeIds = new Set();
  state.selectedConnectionId = null;
  materializeActiveView();
  renderDiagram();
  notifyModelToolsChanged();
  updateNodeExploreToolbar();
  setStatus(`Opened ${node.label || node.id} neighborhood depth ${normalizedDepth}.`);
  return true;
}

export function closeCanvasFocus() {
  const focus = activeCanvasFocus();
  if (!focus) {
    focusStack().length = 0;
    return false;
  }
  state.views.byId.delete(focus.focusViewId);
  focusStack().pop();
  state.views.activeViewId =
    focus.previousViewId && state.views.byId.has(focus.previousViewId)
      ? focus.previousViewId
      : state.views.byId.keys().next().value || null;
  materializeActiveView();
  renderDiagram();
  renderPalette();
  notifyModelToolsChanged();
  setStatus("Returned to previous canvas");
  return true;
}

function nodeVisibleInContainerMode(node) {
  return Boolean(node);
}

function nodeVisibleInCurrentCanvasMode(node) {
  return nodeVisibleInBoundedContextMode(node) && nodeVisibleInContainerMode(node);
}

function clearContextDraftSelection() {
  state.boundedContextDraftNodeIds = new Set();
}

function notifyModelToolsChanged() {
  window.dispatchEvent(new Event("model-tools-state-change"));
}

function g6ContextBoxes() {
  if (!supportsBoundedContext() || state.boundedContextViewMode === "overview") {
    return [];
  }
  const byContext = new Map();
  state.diagram.nodes.forEach((node) => {
    const contextName = contextNameFromNode(node);
    if (!contextName) {
      return;
    }
    if (
      state.boundedContextViewMode === "focus" &&
      normalizeContextName(state.activeBoundedContextName) !== contextName
    ) {
      return;
    }
    if (!byContext.has(contextName)) {
      byContext.set(contextName, []);
    }
    byContext.get(contextName).push(node);
  });
  const nodeW = getNodeWidth();
  const nodeH = getNodeHeight();
  const paddingX = 22;
  const paddingY = 26;
  const boxes = [];
  byContext.forEach((nodes, name) => {
    if (!nodes.length) {
      return;
    }
    const minX = Math.min(...nodes.map((node) => node.x)) - paddingX;
    const minY = Math.min(...nodes.map((node) => node.y)) - paddingY;
    const maxX = Math.max(...nodes.map((node) => node.x + nodeW)) + paddingX;
    const maxY = Math.max(...nodes.map((node) => node.y + nodeH)) + paddingY;
    boxes.push({ name, minX, minY, maxX, maxY });
  });
  return boxes;
}

function g6ConnectionTargetState(source, target) {
  if (!source || !target || source.id === target.id) {
    return "illegal";
  }
  const forward = legalKindsForConnection(source.type, target.type);
  const reverse = legalKindsForConnection(target.type, source.type);
  const legal = forward.length ? forward : reverse;
  const preferred = state.preferredConnectionKind;
  return preferred
    ? legal.includes(preferred)
      ? "legal"
      : "illegal"
    : legal.length
      ? "legal"
      : "illegal";
}

function g6ConnectionTargetTypes(source, { availableTypes = [] } = {}) {
  if (!source) {
    return [];
  }
  return availableTypes.filter((targetType) => {
    if (!targetType) {
      return false;
    }
    const forward = legalKindsForConnection(source.type, targetType);
    const reverse = legalKindsForConnection(targetType, source.type);
    const legal = forward.length ? forward : reverse;
    const preferred = state.preferredConnectionKind;
    return preferred ? legal.includes(preferred) : legal.length > 0;
  });
}

function moveG6ConnectionDrag(sourceId, targetId) {
  if (!state.linkDrag || state.linkDrag.sourceId !== sourceId) {
    return;
  }
  const nextTargetId = targetId || null;
  if (state.linkDrag.hoveredTargetId === nextTargetId) {
    return;
  }
  state.linkDrag.hoveredTargetId = nextTargetId;
  updateG6ConnectionState();
}

function ensureG6Canvas() {
  window.modlessEnsureG6Canvas = ensureG6Canvas;
  window.modlessG6State = {
    ...(window.modlessG6State || {}),
    ensureCalled: true,
    rendererRequested: "antv-g6",
    activeType: state.activeType,
    stateNodes: Array.isArray(state.diagram?.nodes) ? state.diagram.nodes.length : 0,
    stateEdges: Array.isArray(state.diagram?.connections) ? state.diagram.connections.length : 0,
  };
  if (getG6Editor()) {
    return true;
  }
  el.canvasGrid?.classList.add("g6-renderer-active");
  if (!isG6Available()) {
    el.canvasGrid?.setAttribute("data-renderer", "antv-g6-unavailable");
    window.modlessG6State = {
      ...(window.modlessG6State || {}),
      available: false,
      mounted: false,
      lastError: "AntV G6 failed to load",
    };
    setStatus("AntV G6 failed to load; model canvas renderer unavailable");
    return true;
  }
  try {
    mountG6Editor(el.g6EditorHost, {
      mapper: {
        visibleNode: nodeVisibleInCurrentCanvasMode,
        isContainer: (node) =>
          isContainerElement(node) && !(supportsBoundedContext() && isBoundedContextNode(node)),
        contextNameFromNode,
        viewProfile: activeConfiguredViewProfile() || activeView()?.viewpoint || "",
      },
      callbacks: {
        onNodeClick: handleG6NodeClick,
        onNodeDoubleClick: handleG6NodeDoubleClick,
        onNodeHover: setHoveredNode,
        onEdgeClick: (edgeId) => selectConnection(edgeId, { openPicker: true }),
        onEdgeHover: setHoveredEdge,
        onCanvasClick: handleG6CanvasClick,
        onEscape: handleG6CanvasClick,
        onCanvasPointerDown: closeEdgeKindPicker,
        onCanvasPointerMove: () => {},
        onNodeDragStart: startG6NodeDrag,
        onNodeDrag: moveG6NodeDrag,
        onNodeDragEnd: endG6NodeDrag,
        onConnectionDragStart: startG6ConnectionDrag,
        onConnectionPointerMove: moveG6ConnectionDrag,
        onConnectionDragEnd: () => {
          state.linkDrag = null;
          updateG6ConnectionState();
        },
        onConnectionComplete: (sourceId, targetId) =>
          addConnection(sourceId, targetId, {
            interactivePicker: true,
            preferredKind: state.preferredConnectionKind,
          }),
        onConnectionCancel: () => {
          state.linkDrag = null;
          updateG6ConnectionState();
          setStatus("Connection canceled");
        },
        connectionTargetState: g6ConnectionTargetState,
        connectionTargetTypes: g6ConnectionTargetTypes,
        contextBoxes: g6ContextBoxes,
        onContextSelect: selectBoundedContext,
        onContextOpen: openBoundedContextFocus,
        onOpenContainer: openG6ContainerTool,
        onViewportChange: () => {
          updateNodeExploreToolbar();
          onG6ViewportChanged();
        },
        onViewportTranslate: updateNodeExploreToolbar,
        onViewportSynced: updateNodeExploreToolbar,
      },
    });
    el.canvasGrid?.classList.add("g6-renderer-active");
    el.canvasGrid?.classList.remove("g6-renderer-unavailable");
    el.canvasGrid?.setAttribute("data-renderer", "antv-g6");
    return true;
  } catch (error) {
    console.error("G6 editor mount failed", error);
    el.canvasGrid?.classList.add("g6-renderer-unavailable");
    el.canvasGrid?.setAttribute("data-renderer", "antv-g6-unavailable");
    window.modlessG6State = {
      ...(window.modlessG6State || {}),
      available: isG6Available(),
      mounted: false,
      lastError: error.message || String(error),
    };
    setStatus("AntV G6 renderer failed; model canvas renderer unavailable");
    return true;
  }
}

export function initializeModelingRenderer() {
  const mountedOrUnavailable = ensureG6Canvas();
  if (mountedOrUnavailable && getG6Editor()) {
    syncCanvasIndexesFromState();
    syncG6FromState({ full: true });
  }
  return mountedOrUnavailable;
}

export function getModelingRendererDebug() {
  const editor = getG6Editor();
  const host = el.g6EditorHost;
  const hostRect = host?.getBoundingClientRect?.();
  const canvasGrid = el.canvasGrid;
  return {
    renderer: canvasGrid?.dataset?.renderer || "",
    ensureCalled: Boolean(window.modlessG6State?.ensureCalled),
    g6Available: isG6Available(),
    mounted: Boolean(editor),
    graphReady: Boolean(editor?.graph),
    activeType: state.activeType,
    stateNodes: Array.isArray(state.diagram?.nodes) ? state.diagram.nodes.length : 0,
    stateEdges: Array.isArray(state.diagram?.connections) ? state.diagram.connections.length : 0,
    hostExists: Boolean(host),
    hostMountedClass: Boolean(host?.classList?.contains("is-mounted")),
    hostRect: hostRect
      ? {
          width: Math.round(hostRect.width),
          height: Math.round(hostRect.height),
          top: Math.round(hostRect.top),
          left: Math.round(hostRect.left),
        }
      : null,
    hostChildren: host?.children?.length || 0,
    hasCanvasDescendant: Boolean(host?.querySelector?.("canvas")),
    lastError: window.modlessG6State?.lastError || "",
    g6State: window.modlessG6State || null,
  };
}

export function setContextCreateMode(enabled) {
  const isEnabled = Boolean(enabled && supportsBoundedContext());
  state.boundedContextCreateMode = isEnabled;
  if (!isEnabled) {
    clearContextDraftSelection();
    state.boundedContextDraftName = "";
  }
  renderPalette();
  applyNodeSelectionStyles();
  notifyModelToolsChanged();
}

function startBoundedContextAssignment(contextName) {
  const normalized = normalizeContextName(contextName);
  if (!normalized) {
    return;
  }
  if (state.boundedContextCreateMode && state.boundedContextDraftName === normalized) {
    setContextCreateMode(false);
    setStatus(`Bounded context "${normalized}" selection canceled`);
    return;
  }
  state.boundedContextDraftName = normalized;
  state.boundedContextCreateMode = true;
  state.boundedContextDraftNodeIds = new Set();
  ensureBaseBoundedContext(normalized);
  ensureBoundedContextNodeForName(normalized);
  renderPalette();
  applyNodeSelectionStyles();
  notifyModelToolsChanged();
  setStatus(`Bounded context "${normalized}" is active. Select elements, then Done.`);
}

function ensureBaseWorkshopBoundedContexts() {
  if (!state.baseModel || typeof state.baseModel !== "object") {
    return null;
  }
  if (!Array.isArray(state.baseModel.boundedContexts)) {
    state.baseModel.boundedContexts = [];
  }
  return state.baseModel.boundedContexts;
}

function ensureBaseBoundedContext(contextName) {
  const contexts = ensureBaseWorkshopBoundedContexts();
  if (!contexts) {
    return;
  }
  if (contexts.some((context) => normalizeContextName(context?.name) === contextName)) {
    return;
  }
  contexts.push({ name: contextName, ubiquitousLanguage: "Domain language" });
}

function renameBaseBoundedContext(oldName, nextName) {
  const contexts = ensureBaseWorkshopBoundedContexts();
  if (!contexts) {
    return;
  }
  contexts.forEach((context) => {
    if (normalizeContextName(context?.name) !== oldName) {
      return;
    }
    context.name = nextName;
  });
}

function removeBaseBoundedContext(contextName) {
  const contexts = ensureBaseWorkshopBoundedContexts();
  if (!contexts) {
    return;
  }
  const kept = contexts.filter((context) => normalizeContextName(context?.name) !== contextName);
  contexts.length = 0;
  kept.forEach((context) => contexts.push(context));
}

function applyBoundedContextToNodes(nodeIds, contextName) {
  let updated = 0;
  nodeIds.forEach((nodeId) => {
    const node = state.nodesById.get(nodeId);
    if (!node) {
      return;
    }
    if (assignContextName(node, contextName)) {
      updated += 1;
    }
  });
  if (updated) {
    ensureBaseBoundedContext(contextName);
    ensureBoundedContextNodeForName(contextName, nodeIds);
    syncBoundedContextMembershipRefs(contextName);
  }
  return updated;
}

async function persistActiveWorkbenchOperation(operation) {
  if (!state.modelId || !operation?.opType) {
    return false;
  }
  return true;
}

function showBoundedContextNameModal(defaultValue = "") {
  if (
    !el.boundedContextNameOverlay ||
    !el.boundedContextNameInput ||
    !el.boundedContextNameSaveBtn ||
    !el.boundedContextNameCancelBtn
  ) {
    return Promise.resolve(null);
  }
  el.boundedContextNameInput.value = defaultValue;
  el.boundedContextNameOverlay.classList.remove("hidden");
  document.body.classList.add("modal-open");
  el.boundedContextNameInput.focus();
  el.boundedContextNameInput.select();
  return new Promise((resolve) => {
    const close = (value) => {
      el.boundedContextNameOverlay.classList.add("hidden");
      document.body.classList.remove("modal-open");
      el.boundedContextNameSaveBtn.removeEventListener("click", onSave);
      el.boundedContextNameCancelBtn.removeEventListener("click", onCancel);
      el.boundedContextNameOverlay.removeEventListener("click", onOverlayClick);
      el.boundedContextNameInput.removeEventListener("keydown", onKeyDown);
      resolve(value);
    };
    const onSave = () => close(el.boundedContextNameInput.value);
    const onCancel = () => close(null);
    const onOverlayClick = (event) => {
      if (event.target === el.boundedContextNameOverlay) {
        onCancel();
      }
    };
    const onKeyDown = (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        onSave();
      } else if (event.key === "Escape") {
        event.preventDefault();
        onCancel();
      }
    };
    el.boundedContextNameSaveBtn.addEventListener("click", onSave);
    el.boundedContextNameCancelBtn.addEventListener("click", onCancel);
    el.boundedContextNameOverlay.addEventListener("click", onOverlayClick);
    el.boundedContextNameInput.addEventListener("keydown", onKeyDown);
  });
}

export async function finalizeBoundedContextDraft() {
  const selectedIds = [...state.boundedContextDraftNodeIds];
  if (!selectedIds.length) {
    const displayName = modelingLevelConfig(state.activeType).displayName || "model";
    setStatus(`Select one or more ${displayName} elements first`);
    return;
  }
  const contextOptions = availableBoundedContexts();
  const presetName = normalizeContextName(state.boundedContextDraftName);
  const chosen =
    presetName ||
    (await showBoundedContextNameModal(contextOptions[0] || DEFAULT_BOUNDED_CONTEXT_NAME));
  if (chosen === null) {
    setStatus("Bounded context creation canceled");
    return;
  }
  const contextName = normalizeContextName(chosen);
  if (!contextName) {
    setStatus("Bounded context name cannot be empty");
    return;
  }
  if (!isValidContextName(contextName)) {
    setStatus("Use 1-80 chars: letters, numbers, spaces, '-' or '_'");
    return;
  }
  pushDiagramUndoSnapshot();
  const updated = applyBoundedContextToNodes(selectedIds, contextName);
  syncActiveViewFromVisibleGraph();
  await persistActiveWorkbenchOperation({
    opType: "BOUNDED_CONTEXT_ASSIGN",
    viewId: state.views.activeViewId,
    contextName,
    elementIds: selectedIds,
  });
  state.selectedBoundedContextName = contextName;
  setContextCreateMode(false);
  renderDiagram();
  markModelDirty();
  notifyModelToolsChanged();
  openBoundedContextPanel(contextName);
  setStatus(
    `Assigned ${updated} element${updated !== 1 ? "s" : ""} to bounded context "${contextName}"`,
  );
}

function clearNodeMultiSelection() {
  state.selectedNodeIds = new Set();
}

function commitNodeLabel(node, rawText) {
  if (!node) {
    return "";
  }
  const previous = String(node.label || "").trim();
  const next = String(rawText ?? "").trim();
  const resolved = next || previous;
  node.label = resolved;
  node.meta = node.meta && typeof node.meta === "object" ? node.meta : {};
  if (supportsBoundedContext()) {
    node.meta.label = resolved;
  } else {
    node.meta.name = resolved;
  }
  if (state.selectedNodeId === node.id && el.attrPanelTitle) {
    el.attrPanelTitle.textContent = resolved;
  }
  return resolved;
}

function setNodeMultiSelection(ids) {
  state.selectedNodeIds = new Set(ids);
  state.selectedBoundedContextName = null;
}

function deselectEdges() {
  state.selectedConnectionId = null;
  ensureG6Canvas();
  updateG6Selection();
}

function applyNodeSelectionStyles() {
  ensureG6Canvas();
  updateG6Selection();
  updateG6ImpactState();
}

function applyHoverFocusStyles() {
  ensureG6Canvas();
  setG6HoverNode(state.hoveredNodeId);
}

function setHoveredNode(nodeId) {
  const nextHoveredNodeId = typeof nodeId === "string" && nodeId.trim() ? nodeId : null;
  if (state.hoveredNodeId === nextHoveredNodeId) {
    if (!nextHoveredNodeId) {
      applyHoverFocusStyles();
    }
    return;
  }
  state.hoveredNodeId = nextHoveredNodeId;
  applyHoverFocusStyles();
  if (state.connectSourceId) {
    updateG6ConnectionState();
  }
}

function toggleNodeInSelection(nodeId) {
  const nextSelection = new Set(state.selectedNodeIds);
  if (nextSelection.has(nodeId)) {
    nextSelection.delete(nodeId);
  } else {
    nextSelection.add(nodeId);
  }
  setNodeMultiSelection(nextSelection);
  state.selectedNodeId = nextSelection.size === 1 ? [...nextSelection][0] : null;
  deselectEdges();
  el.attributePanel.classList.add("hidden");
  el.workspace.classList.remove("attr-open", "mobile-right-open");
  if (el.mobileBackdrop) {
    el.mobileBackdrop.classList.add("hidden");
  }
  applyNodeSelectionStyles();
  if (nextSelection.size) {
    setStatus(`${nextSelection.size} element${nextSelection.size > 1 ? "s" : ""} selected`);
  } else {
    setStatus("Selection cleared");
  }
}

function availableBoundedContexts() {
  const contexts = new Set();
  if (supportsBoundedContext()) {
    (state.baseModel?.boundedContexts || []).forEach((context) => {
      const name = normalizeContextName(context?.name);
      if (name) {
        contexts.add(name);
      }
    });
    state.diagram.nodes.forEach((node) => {
      if (isBoundedContextNode(node)) {
        const contextNodeName = boundedContextNameFromContextNode(node);
        if (contextNodeName) {
          contexts.add(contextNodeName);
        }
        return;
      }
      const name = contextNameFromNode(node);
      if (name) {
        contexts.add(name);
      }
    });
  }
  return [...contexts];
}

export function renameBoundedContext(oldName, nextName) {
  const normalizedOld = normalizeContextName(oldName);
  const normalizedNext = normalizeContextName(nextName);
  if (!normalizedOld || !normalizedNext) {
    return false;
  }
  if (normalizedOld === normalizedNext) {
    return true;
  }
  if (!isValidContextName(normalizedNext)) {
    setStatus("Use 1-80 chars: letters, numbers, spaces, '-' or '_'");
    return false;
  }
  const nodes = contextNodes(normalizedOld);
  const contextNode = contextNodeForName(normalizedOld);
  if (!nodes.length && !contextNode) {
    setStatus("No elements found for selected bounded context");
    return false;
  }
  pushDiagramUndoSnapshot();
  nodes.forEach((node) => assignContextName(node, normalizedNext));
  if (contextNode) {
    contextNode.meta =
      contextNode.meta && typeof contextNode.meta === "object" ? contextNode.meta : {};
    contextNode.label = normalizedNext;
    contextNode.meta.name = normalizedNext;
    contextNode.meta.label = normalizedNext;
    syncNodeMetaToGraph(contextNode);
  }
  renameBaseBoundedContext(normalizedOld, normalizedNext);
  ensureBaseBoundedContext(normalizedNext);
  syncBoundedContextMembershipRefs(normalizedNext);
  state.selectedBoundedContextName = normalizedNext;
  syncDiagramRenderer({});
  return true;
}

export function removeElementFromBoundedContext(elementId, contextName) {
  const normalized = normalizeContextName(contextName);
  const node = state.diagram.nodes.find((candidate) => candidate.id === elementId);
  if (!node || !normalized || contextNameFromNode(node) !== normalized) {
    return false;
  }
  pushDiagramUndoSnapshot();
  if (node.meta && typeof node.meta === "object") {
    delete node.meta.contextName;
    if (typeof node.meta.context === "string") {
      delete node.meta.context;
    }
  }
  const graphElement = state.graph?.elementsById?.get(elementId);
  if (graphElement) {
    delete graphElement.contextName;
    if (typeof graphElement.context === "string") {
      delete graphElement.context;
    }
  }
  if (!contextNodes(normalized).length && !contextNodeForName(normalized)) {
    removeBaseBoundedContext(normalized);
    state.selectedBoundedContextName = null;
  } else {
    syncBoundedContextMembershipRefs(normalized);
  }
  syncDiagramRenderer({});
  markModelDirty();
  setStatus(`Removed ${node.label || node.id} from "${normalized}"`);
  return true;
}

export function deleteBoundedContext(contextName) {
  const normalized = normalizeContextName(contextName);
  if (!normalized) {
    return false;
  }
  const nodes = contextNodes(normalized);
  const contextNode = contextNodeForName(normalized);
  if (!nodes.length && !contextNode) {
    return false;
  }
  pushDiagramUndoSnapshot();
  nodes.forEach((node) => {
    if (!node.meta || typeof node.meta !== "object") {
      return;
    }
    if (normalizeContextName(node.meta.contextName) === normalized) {
      delete node.meta.contextName;
    }
    if (
      typeof node.meta.context === "string" &&
      normalizeContextName(node.meta.context) === normalized
    ) {
      delete node.meta.context;
    }
    syncNodeMetaToGraph(node);
  });
  if (contextNode) {
    state.diagram.nodes = state.diagram.nodes.filter((node) => node.id !== contextNode.id);
    state.diagram.connections = state.diagram.connections.filter(
      (edge) => edge.sourceId !== contextNode.id && edge.targetId !== contextNode.id,
    );
    removeElementFromGraph(contextNode.id);
  }
  removeBaseBoundedContext(normalized);
  state.selectedBoundedContextName = null;
  syncDiagramRenderer({});
  return true;
}

// ── Viewport helpers ──────────────────────────────────────────────────────────

export function toCanvasCoordinates(clientX, clientY) {
  ensureG6Canvas();
  return toGraphCoordinates(clientX, clientY);
}

let viewportUpdateScheduled = false;

function updateZoomControlLabel() {
  if (el.canvasZoomValue) {
    el.canvasZoomValue.textContent = `${Math.round((state.viewport.scale || 1) * 100)}%`;
  }
}

export function applyViewport() {
  ensureG6Canvas();
  updateG6Viewport();
  updateZoomControlLabel();
  el.canvasGrid?.style.setProperty("--viewport-scale", String(state.viewport.scale || 1));
  el.canvasGrid?.classList.toggle("lod-low", state.viewport.scale < 0.35);
  el.canvasGrid?.classList.toggle(
    "lod-medium",
    state.viewport.scale >= 0.35 && state.viewport.scale < 0.75,
  );
  el.canvasGrid?.classList.toggle("lod-high", state.viewport.scale >= 1.5);
  updateNodeExploreToolbar();
  // Defer cursor rendering to batch with other updates, don't render on every pan
  if (!viewportUpdateScheduled) {
    viewportUpdateScheduled = true;
    window.requestAnimationFrame(() => {
      viewportUpdateScheduled = false;
    });
  }
}

export function resetCanvasView() {
  ensureG6Canvas();
  resetG6CanvasView();
}

export function zoomCanvasBy(multiplier = 1) {
  ensureG6Canvas();
  zoomG6CanvasBy(multiplier);
}

function diagramBounds({ includeContexts = true } = {}) {
  if (!state.diagram?.nodes?.length) {
    return null;
  }
  const nodeW = getNodeWidth();
  const nodeH = getNodeHeight();
  let minX = Math.min(...state.diagram.nodes.map((node) => node.x));
  let minY = Math.min(...state.diagram.nodes.map((node) => node.y));
  let maxX = Math.max(...state.diagram.nodes.map((node) => node.x + nodeW));
  let maxY = Math.max(...state.diagram.nodes.map((node) => node.y + nodeH));
  if (includeContexts && supportsBoundedContext()) {
    const byContext = new Map();
    state.diagram.nodes.forEach((node) => {
      const contextName = contextNameFromNode(node);
      if (!contextName) {
        return;
      }
      if (!byContext.has(contextName)) {
        byContext.set(contextName, []);
      }
      byContext.get(contextName).push(node);
    });
    byContext.forEach((nodes) => {
      const contextMinX = Math.min(...nodes.map((node) => node.x)) - 22;
      const contextMinY = Math.min(...nodes.map((node) => node.y)) - 26;
      const contextMaxX = Math.max(...nodes.map((node) => node.x + nodeW)) + 22;
      const contextMaxY = Math.max(...nodes.map((node) => node.y + nodeH)) + 26;
      minX = Math.min(minX, contextMinX);
      minY = Math.min(minY, contextMinY);
      maxX = Math.max(maxX, contextMaxX);
      maxY = Math.max(maxY, contextMaxY);
    });
  }
  return {
    minX,
    minY,
    maxX,
    maxY,
    width: Math.max(1, maxX - minX),
    height: Math.max(1, maxY - minY),
  };
}

export function centerViewportOnDiagram({ fit = false } = {}) {
  const bounds = diagramBounds();
  if (!bounds) {
    return;
  }
  ensureG6Canvas();
  fitG6CanvasToDiagram(bounds, { fit });
}

export function restoreCanvasCamera(camera = null) {
  if (!camera || typeof camera !== "object") {
    return false;
  }
  const scale = Number(camera.scale);
  state.viewport = {
    x: Number(camera.x) || 0,
    y: Number(camera.y) || 0,
    scale: Number.isFinite(scale) && scale > 0 ? scale : 1,
  };
  applyViewport();
  return true;
}

function createMaskIcon(className, src, { ariaHidden = true } = {}) {
  const icon = document.createElement("span");
  icon.className = `${className} icon-svg icon-mask`;
  if (ariaHidden) {
    icon.setAttribute("aria-hidden", "true");
  }
  icon.style.setProperty("--icon-src", `url('${src || PLACEHOLDER_ICON}')`);
  return icon;
}

function setMaskIconSource(icon, src) {
  if (!icon) {
    return;
  }
  const normalized = String(src || "").trim();
  const resolved =
    normalized.startsWith("/") || normalized.startsWith(".") || normalized.endsWith(".svg")
      ? normalized
      : PLACEHOLDER_ICON;
  icon.style.setProperty("--icon-src", `url('${resolved}')`);
}

function definitionUi(definition) {
  const ui = definition?.ui && typeof definition.ui === "object" ? definition.ui : {};
  return {
    icon: ui.icon || definition?.icon,
    color: ui.color || definition?.color,
  };
}

function applyDefinitionAccent(element, definition) {
  if (!element) {
    return;
  }
  const color = String(definitionUi(definition).color || "").trim();
  if (color) {
    element.style.setProperty("--node-accent", color);
  } else {
    element.style.removeProperty("--node-accent");
  }
}

function createPaletteNotationIcon(definition) {
  const configuredIcon = String(definitionUi(definition).icon || "").trim();
  if (
    configuredIcon.startsWith("/") ||
    configuredIcon.startsWith(".") ||
    configuredIcon.endsWith(".svg")
  ) {
    return createMaskIcon("palette-item-icon", configuredIcon);
  }
  return null;
}

// ── Palette ───────────────────────────────────────────────────────────────────

function createWizardEdge(sourceId, targetId, kind) {
  const edge = {
    id: genId("e"),
    sourceId,
    targetId,
    kind,
  };
  state.diagram.connections.push(edge);
  addConnectionToGraphAndActiveView(edge);
}

function addScaffoldNode(type, x, y, label) {
  const node = getDefaultNode(state.activeType, type, Math.round(x), Math.round(y));
  node.label = label || node.label;
  node.meta.name = node.label;
  node.meta.label = node.label;
  state.diagram.nodes.push(node);
  addNodeToGraphAndActiveView(node);
  return node;
}

function idsFromReference(value) {
  if (Array.isArray(value)) {
    return value
      .map((item) =>
        typeof item === "string" ? item : item?.$ref || item?.id || item?.elementId || "",
      )
      .filter(Boolean);
  }
  if (typeof value === "string") {
    return value ? [value] : [];
  }
  if (value && typeof value === "object") {
    const id = value.$ref || value.id || value.elementId || "";
    return id ? [id] : [];
  }
  return [];
}

function addContainmentReference(parent, feature, childId) {
  const ids = new Set(idsFromReference(parent.meta?.[feature]));
  ids.add(childId);
  parent.meta = parent.meta && typeof parent.meta === "object" ? parent.meta : {};
  parent.meta[feature] = [...ids];
}

function maybeCreateContainmentEdge(parent, child) {
  const exists = state.diagram.connections.some(
    (edge) =>
      edge.sourceId === parent.id &&
      edge.targetId === child.id &&
      edge.kind === configuredRelationshipSemantic("containmentKind"),
  );
  if (!exists) {
    createWizardEdge(parent.id, child.id, configuredRelationshipSemantic("containmentKind"));
  }
}

function containingFeaturesForChild(childType) {
  const entries = [];
  try {
    modelingLevelConfig(state.activeType).elements.forEach((definition) => {
      modelingContainmentsForType(state.activeType, definition.type).forEach((containment) => {
        if (
          containment.feature &&
          containment.types?.some((type) => modelTypeMatches(state.activeType, childType, type))
        ) {
          entries.push({ ownerType: definition.type, feature: containment.feature });
        }
      });
    });
  } catch {
    return [];
  }
  return entries;
}

function selectedOwnerNode(ownerTypes, childNode) {
  const selectedIds = [
    state.selectedNodeId,
    ...(state.selectedNodeIds instanceof Set ? [...state.selectedNodeIds] : []),
  ].filter(Boolean);
  const candidates = [
    ...selectedIds.map((id) => state.nodesById.get(id)).filter(Boolean),
    ...state.diagram.nodes,
  ];
  return (
    candidates.find(
      (node) =>
        node?.id &&
        node.id !== childNode.id &&
        ownerTypes.some((ownerType) => modelTypeMatches(state.activeType, node.type, ownerType)),
    ) || null
  );
}

function attachNestedNode(node, containment) {
  if (!node || !containment?.ownerType || !containment?.feature) {
    return null;
  }
  const owner = selectedOwnerNode([containment.ownerType], node);
  if (!owner) {
    return null;
  }
  node.meta.__ownerId = owner.id;
  node.meta.__containmentFeature = containment.feature;
  addContainmentReference(owner, containment.feature, node.id);
  syncNodeMetaToGraph(node);
  syncNodeMetaToGraph(owner);
  maybeCreateContainmentEdge(owner, node);
  return owner;
}

function createConfiguredCompanions(node) {
  applyConfiguredScaffold(node);
}

function applyConfiguredScaffold(node) {
  if (!node || !isModelingLevel(state.activeType)) {
    return;
  }
  const containments = containingFeaturesForChild(node.type);
  if (containments.length) {
    attachNestedNode(node, containments[0]);
  }
  const recipes = modelingLevelConfig(state.activeType).scaffoldRecipes || [];
  recipes
    .filter((recipe) => recipe?.type === node.type)
    .forEach((recipe) => {
      Object.entries(recipe.defaults || {}).forEach(([field, value]) => {
        node.meta[field] = structuredClone(value);
      });
      (recipe.children || []).forEach((child, index) => {
        const childNode = addScaffoldNode(
          child.type,
          node.x + Number(child.x || 180 + index * 48),
          node.y + Number(child.y || 96),
          child.label || child.type,
        );
        if (child.containmentFeature) {
          childNode.meta.__ownerId = node.id;
          childNode.meta.__containmentFeature = child.containmentFeature;
          addReferenceValue(node.meta, child.containmentFeature, childNode.id, true);
        }
        if (child.relationshipKind) {
          createWizardEdge(node.id, childNode.id, child.relationshipKind);
        }
      });
    });
  state.diagram.nodes.forEach(syncNodeMetaToGraph);
}

function createModelingWizard(kind) {
  const recipe = (modelingLevelConfig(state.activeType).scaffoldRecipes || []).find(
    (candidate) => candidate?.id === kind,
  );
  if (!recipe?.nodes?.length) {
    return;
  }
  const rect = el.canvasViewport?.getBoundingClientRect();
  const origin = rect
    ? toCanvasCoordinates(rect.left + rect.width / 2, rect.top + rect.height / 2)
    : { x: 120, y: 120 };
  pushDiagramUndoSnapshot();
  const nodes = recipe.nodes.map((item, index) => {
    const node = getDefaultNode(
      state.activeType,
      item.type,
      Math.round(origin.x + Number(item.x || index * 180)),
      Math.round(origin.y + Number(item.y || 0)),
    );
    node.label = item.label || node.label;
    node.meta = { ...(node.meta || {}), ...structuredClone(item.defaults || {}) };
    state.diagram.nodes.push(node);
    addNodeToGraphAndActiveView(node);
    return node;
  });
  (recipe.edges || []).forEach((edge) => {
    createWizardEdge(nodes[edge.sourceIndex]?.id, nodes[edge.targetIndex]?.id, edge.kind);
  });
  ensureG6Canvas();
  syncCanvasIndexesFromState();
  syncG6FromState({ full: false });
  markModelDirty();
  setStatus(`Created ${recipe.label || "model scaffold"}`);
}

function createPaletteActionButton(
  label,
  { title = "", active = false, done = false, compact = false } = {},
) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = `palette-quick-action${active ? " active" : ""}${
    done ? " palette-quick-action-done" : ""
  }${compact ? " palette-quick-action-compact" : ""}`;
  button.title = title || label;
  const labelSpan = document.createElement("span");
  labelSpan.className = "palette-quick-action-label";
  labelSpan.textContent = label;
  button.appendChild(labelSpan);
  return button;
}

function appendPaletteActionGroup(groupName, buildButtons) {
  if (!el.palette) {
    return false;
  }
  const buttons = buildButtons() || [];
  if (!buttons.length) {
    return false;
  }
  const group = document.createElement("div");
  group.className = "palette-group";
  const collapsed = isPaletteGroupCollapsed(groupName);
  group.classList.toggle("palette-group-collapsed", collapsed);

  const title = document.createElement("button");
  title.type = "button";
  title.className = "palette-group-title";
  title.setAttribute("aria-expanded", String(!collapsed));
  const titleMeta = document.createElement("span");
  titleMeta.className = "palette-group-meta";
  const titleChevron = document.createElement("span");
  titleChevron.className = "palette-group-chevron";
  titleChevron.setAttribute("aria-hidden", "true");
  const titleText = document.createElement("span");
  titleText.className = "palette-group-name";
  titleText.textContent = groupName;
  const titleCount = document.createElement("span");
  titleCount.className = "palette-group-count";
  const items = document.createElement("div");
  items.className = "palette-group-items palette-context-actions";

  titleCount.textContent = `${buttons.length} item${buttons.length === 1 ? "" : "s"}`;
  titleMeta.appendChild(titleChevron);
  titleMeta.appendChild(titleText);
  title.appendChild(titleMeta);
  title.appendChild(titleCount);
  title.addEventListener("click", () => {
    setPaletteGroupCollapsed(groupName, !isPaletteGroupCollapsed(groupName));
    renderPalette();
  });
  group.appendChild(title);

  buttons.forEach((button) => items.appendChild(button));
  group.appendChild(items);
  el.palette.appendChild(group);
  return true;
}

function activeViewElementTypeFilter() {
  return new Set((activeView()?.filters?.elementTypes || []).map(String));
}

function isActionableScopedPaletteType(type, creatableTypes) {
  return creatableTypes.has(type);
}

function filterScopedPaletteTypes(typeKey, scopedTypes, allTypes) {
  const creatableTypes = new Set(allTypes);
  const result = [];
  const seen = new Set();
  const add = (type) => {
    if (!type || seen.has(type)) {
      return;
    }
    seen.add(type);
    result.push(type);
  };
  scopedTypes.forEach((type) => {
    if (isActionableScopedPaletteType(type, creatableTypes)) {
      add(type);
      return;
    }
    allTypes.forEach((candidate) => {
      try {
        if (modelingTypeMatches(typeKey, type, candidate)) {
          add(candidate);
        }
      } catch {
        // Ignore stale metadata entries; missing types cannot be created.
      }
    });
  });
  return result;
}

function availableConfiguredPaletteTypes(allTypes) {
  const view = activeView();
  if (!view || String(view.kind || "").toUpperCase() === "MAIN") {
    return allTypes;
  }
  let viewDefinition = null;
  try {
    viewDefinition = modelingViewDefinition(state.activeType, view);
  } catch {
    viewDefinition = null;
  }
  const scoped = viewDefinition
    ? [
        ...(Array.isArray(viewDefinition.palette) ? viewDefinition.palette : []),
        ...(Array.isArray(viewDefinition.elementTypes) ? viewDefinition.elementTypes : []),
      ]
    : [...activeViewElementTypeFilter()];
  const filtered = filterScopedPaletteTypes(state.activeType, scoped, allTypes);
  return viewDefinition || scoped.length ? filtered : allTypes;
}

function renderWizardActions() {
  if (activeContainerFocus()) {
    return false;
  }
  const actions = (modelingLevelConfig(state.activeType).scaffoldRecipes || []).filter((recipe) => {
    const requiredTypes = (recipe.nodes || []).map((node) => node.type).filter(Boolean);
    const allowedTypes = activeViewElementTypeFilter();
    return !allowedTypes.size || requiredTypes.every((type) => allowedTypes.has(type));
  });
  if (!actions.length) {
    return false;
  }
  return appendPaletteActionGroup("Quick Actions", () => {
    return actions.map((recipe) => {
      const label = recipe.label || recipe.id || "Scaffold";
      const button = createPaletteActionButton(label, {
        title: `Create ${label}`,
      });
      button.addEventListener("click", () => createModelingWizard(recipe.id));
      return button;
    });
  });
}

function isPaletteGroupCollapsed(groupName) {
  const levelState = state.paletteGroupCollapsed?.[state.activeType] || {};
  if (!Object.prototype.hasOwnProperty.call(levelState, groupName)) {
    return true;
  }
  return Boolean(levelState[groupName]);
}

function setPaletteGroupCollapsed(groupName, collapsed) {
  state.paletteGroupCollapsed ??= {};
  state.paletteGroupCollapsed[state.activeType] ??= {};
  state.paletteGroupCollapsed[state.activeType][groupName] = Boolean(collapsed);
}

function setAllPaletteGroupsCollapsed(groupNames, collapsed) {
  groupNames.forEach((groupName) => {
    if (groupName) {
      setPaletteGroupCollapsed(groupName, collapsed);
    }
  });
}

function createBoundedContextActionControls() {
  if (!modelingLevelConfig(state.activeType).boundedContext?.enabled) {
    return [];
  }
  const controls = [];
  if (state.boundedContextViewMode !== "normal") {
    const back = createPaletteActionButton("Back", {
      done: true,
      title: `Return to full ${modelingLevelConfig(state.activeType).displayName || "model"} view`,
    });
    back.addEventListener("click", closeBoundedContextSpecialView);
    controls.push(back);
  } else {
    const overview = createPaletteActionButton("Contexts", {
      title: "Show bounded-context overview",
    });
    overview.addEventListener("click", openBoundedContextOverview);
    controls.push(overview);
  }

  if (state.boundedContextCreateMode) {
    const done = createPaletteActionButton("Done", {
      done: true,
      compact: true,
      title: "Create bounded context from selected elements",
    });
    done.addEventListener("click", () => {
      finalizeBoundedContextDraft();
    });
    controls.push(done);
    const cancel = createPaletteActionButton("Cancel", {
      compact: true,
      title: "Cancel bounded context assignment",
    });
    cancel.addEventListener("click", () => {
      setContextCreateMode(false);
      setStatus("Bounded context assignment canceled");
    });
    controls.push(cancel);
  }
  const row = document.createElement("div");
  row.className = "palette-context-inline-row";
  controls.forEach((control) => row.appendChild(control));
  return [row];
}

export function renderPalette() {
  const config = MODEL_TYPES[state.activeType];
  if (!config || !el.palette) {
    return;
  }
  const isModelingType = isModelingLevel(state.activeType);
  if (el.paletteSearchInput) {
    el.paletteSearchInput.disabled = !isModelingType;
    el.paletteSearchInput.value = isModelingType ? state.paletteSearch[state.activeType] || "" : "";
    el.paletteSearchInput.placeholder = isModelingType ? "Search elements..." : "Search disabled";
  }
  let allTypes = [];
  const containerFocus = activeContainerFocus();
  if (isModelingType) {
    try {
      allTypes = containerFocus
        ? modelingContainmentPalette(state.activeType, containerFocus.elementType)
        : modelingPalette(state.activeType);
    } catch (error) {
      console.error("Palette rendering failed", error);
      setStatus(error.message || "Backend modeling config is unavailable");
      allTypes = [];
    }
  } else {
    allTypes = Array.isArray(config.palette) ? config.palette : [];
  }
  const query = ((state.paletteSearch[state.activeType] || "") + "").trim().toLowerCase();
  const activeViewElementTypes = activeViewElementTypeFilter();
  const viewScopedTypes = containerFocus
    ? allTypes
    : isModelingType
      ? availableConfiguredPaletteTypes(allTypes)
      : activeViewElementTypes.size
        ? allTypes.filter((type) => activeViewElementTypes.has(type))
        : allTypes;
  const actionableTypes = viewScopedTypes;
  const filteredTypes = query
    ? actionableTypes.filter((type) => {
        const definition = modelingElementDefinition(state.activeType, type);
        return [
          type,
          definition?.displayName,
          definition?.category,
          definition?.notation?.tag,
        ].some((value) =>
          String(value || "")
            .toLowerCase()
            .includes(query),
        );
      })
    : actionableTypes;
  const groupedTypes = groupPaletteTypes(filteredTypes);
  syncPaletteCollapsedUi();
  el.palette.innerHTML = "";
  el.palette.dataset.activeType = state.activeType;
  const hasQuickActionsGroup = renderWizardActions();
  const namedGroups = groupedTypes.map(([groupName]) => groupName).filter(Boolean);
  if (hasQuickActionsGroup) {
    namedGroups.unshift("Quick Actions");
  }
  if (namedGroups.length > 1) {
    const groupToolbar = document.createElement("div");
    groupToolbar.className = "palette-group-toolbar";
    const toolbarLabel = document.createElement("div");
    toolbarLabel.className = "palette-group-toolbar-label";
    toolbarLabel.textContent = "Groupings";
    const toolbarActions = document.createElement("div");
    toolbarActions.className = "palette-group-toolbar-actions";
    const expandBtn = document.createElement("button");
    expandBtn.type = "button";
    expandBtn.className = "palette-group-toolbar-btn";
    expandBtn.textContent = "Expand All";
    expandBtn.addEventListener("click", () => {
      setAllPaletteGroupsCollapsed(namedGroups, false);
      renderPalette();
    });
    const collapseBtn = document.createElement("button");
    collapseBtn.type = "button";
    collapseBtn.className = "palette-group-toolbar-btn";
    collapseBtn.textContent = "Collapse All";
    collapseBtn.addEventListener("click", () => {
      setAllPaletteGroupsCollapsed(namedGroups, true);
      renderPalette();
    });
    toolbarActions.appendChild(expandBtn);
    toolbarActions.appendChild(collapseBtn);
    groupToolbar.appendChild(toolbarLabel);
    groupToolbar.appendChild(toolbarActions);
    el.palette.appendChild(groupToolbar);
  }
  groupedTypes.forEach(([groupName, types]) => {
    const group = document.createElement("div");
    group.className = "palette-group";
    const collapsed = isPaletteGroupCollapsed(groupName);
    group.classList.toggle("palette-group-collapsed", collapsed);
    if (groupName) {
      const title = document.createElement("button");
      title.type = "button";
      title.className = "palette-group-title";
      title.setAttribute("aria-expanded", String(!collapsed));
      const titleMeta = document.createElement("span");
      titleMeta.className = "palette-group-meta";
      const titleChevron = document.createElement("span");
      titleChevron.className = "palette-group-chevron";
      titleChevron.setAttribute("aria-hidden", "true");
      const titleText = document.createElement("span");
      titleText.className = "palette-group-name";
      titleText.textContent = groupName;
      const titleCount = document.createElement("span");
      titleCount.className = "palette-group-count";
      titleCount.textContent = `${types.length} item${types.length === 1 ? "" : "s"}`;
      titleMeta.appendChild(titleChevron);
      titleMeta.appendChild(titleText);
      title.appendChild(titleMeta);
      title.appendChild(titleCount);
      title.addEventListener("click", () => {
        setPaletteGroupCollapsed(groupName, !isPaletteGroupCollapsed(groupName));
        renderPalette();
      });
      group.appendChild(title);
    }
    const items = document.createElement("div");
    items.className = "palette-group-items";
    types.forEach((type) => {
      const definition = modelingElementDefinition(state.activeType, type);
      const label = definition?.displayName || type;
      const description = definition?.description || label;
      const item = document.createElement("div");
      item.className = "palette-item";
      item.draggable = true;
      item.dataset.nodeType = type;
      applyDefinitionAccent(item, definition);
      const iconImg = createPaletteNotationIcon(definition);
      const labelSpan = document.createElement("span");
      labelSpan.className = "palette-item-label";
      labelSpan.textContent = label;
      if (iconImg) {
        item.appendChild(iconImg);
      }
      item.appendChild(labelSpan);
      item.title = `${description}\nDrag to create.`;
      item.addEventListener("dragstart", (event) => {
        el.workspace?.classList.remove("mobile-left-open");
        el.mobileBackdrop?.classList.add("hidden");
        state.paletteDragType = type;
        event.dataTransfer.effectAllowed = "copy";
        event.dataTransfer.setData(
          "application/x-modless-palette-item",
          JSON.stringify({ level: state.activeType, type }),
        );
        event.dataTransfer.setData("application/x-modless-node-type", type);
        event.dataTransfer.setData("text/node-type", type);
        event.dataTransfer.setData("text/plain", type);
      });
      item.addEventListener("dragend", () => {
        state.paletteDragType = "";
      });
      items.appendChild(item);
    });
    group.appendChild(items);
    el.palette.appendChild(group);
  });
  if (!filteredTypes.length) {
    const empty = document.createElement("div");
    empty.className = "palette-empty";
    const containerFocus = activeContainerFocus();
    empty.textContent = containerFocus
      ? `No containable elements configured for ${containerFocus.label || containerFocus.elementType}`
      : isModelingType
        ? "No backend palette available"
        : "No matching elements";
    el.palette.appendChild(empty);
  }
}

export function syncPaletteCollapsedUi() {
  if (!el.palette) {
    return;
  }
  const shouldCollapsePalette = !!state.paletteCollapsed && state.activeType !== "artifact";
  el.palette.classList.toggle("palette-collapsed", !!state.paletteCollapsed);
  el.workspace?.classList.toggle("palette-collapsed", shouldCollapsePalette);
  const paletteSearchRow = el.paletteSearchInput?.closest(".palette-search-row");
  if (paletteSearchRow) {
    paletteSearchRow.classList.toggle("hidden", shouldCollapsePalette);
  }
}

function paletteGroupForType(type) {
  try {
    return modelingElementDefinition(state.activeType, type)?.category || "Model Elements";
  } catch {
    return "Model Elements";
  }
}

function groupPaletteTypes(types) {
  const buckets = new Map();
  types.forEach((type) => {
    const groupName = paletteGroupForType(type);
    if (!buckets.has(groupName)) {
      buckets.set(groupName, []);
    }
    buckets.get(groupName).push(type);
  });
  return [...buckets.entries()]
    .sort(([left], [right]) => String(left).localeCompare(String(right)))
    .map(([groupName, members]) => [
      groupName,
      [...members].sort((left, right) => {
        const leftDefinition = modelingElementDefinition(state.activeType, left);
        const rightDefinition = modelingElementDefinition(state.activeType, right);
        return String(leftDefinition?.displayName || left).localeCompare(
          String(rightDefinition?.displayName || right),
        );
      }),
    ]);
}

// ── Node rendering ────────────────────────────────────────────────────────────

export function renderNodes() {
  // Compatibility wrapper: callers still use the old name, but node rendering
  // is handled exclusively by the G6 adapter.
  ensureG6Canvas();
  syncCanvasIndexesFromState();
  syncG6FromState({ full: false });
  updateG6Selection();
  updateG6ImpactState();
  return;
}

// ── Node explore toolbar ──────────────────────────────────────────────────────

let nodeExploreToolbarBound = false;

function shouldShowNodeExploreToolbar() {
  return (
    isModelingLevel(state.activeType) &&
    Boolean(state.selectedNodeId) &&
    !state.connectMode &&
    !state.impactMode &&
    !state.boundedContextCreateMode
  );
}

const NODE_EXPLORE_TOOLBAR_CANVAS_W = 68;
const NODE_EXPLORE_TOOLBAR_CANVAS_H = 18;
const NODE_EXPLORE_TOOLBAR_GAP = 4;

function positionNodeExploreToolbar(node) {
  const graph = getG6Editor()?.graph;
  if (!el.nodeExploreToolbar || !node || !graph) {
    return;
  }
  const nodeW = getNodeWidth();
  const centerX = node.x + nodeW / 2;
  const topY = node.y - NODE_EXPLORE_TOOLBAR_GAP - NODE_EXPLORE_TOOLBAR_CANVAS_H;
  const leftX = centerX - NODE_EXPLORE_TOOLBAR_CANVAS_W / 2;

  const topLeft = graphCanvasToViewportPoint(graph, leftX, topY);
  const bottomRight = graphCanvasToViewportPoint(
    graph,
    leftX + NODE_EXPLORE_TOOLBAR_CANVAS_W,
    topY + NODE_EXPLORE_TOOLBAR_CANVAS_H,
  );

  const left = Math.min(topLeft.x, bottomRight.x);
  const top = Math.min(topLeft.y, bottomRight.y);
  const width = Math.max(1, Math.abs(bottomRight.x - topLeft.x));
  const height = Math.max(1, Math.abs(bottomRight.y - topLeft.y));

  el.nodeExploreToolbar.style.left = `${Math.round(left)}px`;
  el.nodeExploreToolbar.style.top = `${Math.round(top)}px`;
  el.nodeExploreToolbar.style.width = `${Math.round(width)}px`;
  el.nodeExploreToolbar.style.height = `${Math.round(height)}px`;
  el.nodeExploreToolbar.style.transform = "none";
  el.nodeExploreToolbar.style.fontSize = `${Math.max(5, Math.round(height * 0.42))}px`;
}

export function updateNodeExploreToolbar() {
  if (!el.nodeExploreToolbar) {
    return;
  }
  mountNodeExploreToolbar(el.nodeExploreToolbar);
  if (!shouldShowNodeExploreToolbar()) {
    el.nodeExploreToolbar.classList.add("hidden");
    return;
  }
  const node = state.nodesById.get(state.selectedNodeId);
  if (!node) {
    el.nodeExploreToolbar.classList.add("hidden");
    return;
  }
  el.nodeExploreToolbar.classList.remove("hidden");
  // Measure while visible so width/height are available before positioning.
  positionNodeExploreToolbar(node);
}

function ensureNodeExploreToolbarBindings() {
  if (nodeExploreToolbarBound) {
    return;
  }
  nodeExploreToolbarBound = true;
  mountNodeExploreToolbar(el.nodeExploreToolbar);
  el.nodeExploreNearbyBtn?.addEventListener("click", (event) => {
    event.stopPropagation();
    if (!state.selectedNodeId) {
      setStatus("Select an element before opening nearby neighbors.");
      return;
    }
    openNeighborhoodFocus(state.selectedNodeId, 1);
  });
  el.nodeExploreExtendedBtn?.addEventListener("click", (event) => {
    event.stopPropagation();
    if (!state.selectedNodeId) {
      setStatus("Select an element before opening extended neighbors.");
      return;
    }
    openNeighborhoodFocus(state.selectedNodeId, 2);
  });
}

// ── Edge rendering ────────────────────────────────────────────────────────────

let edgeKindPickerBound = false;

function setEdgeKindMenuOpen(open) {
  state.edgeKindPicker.menuOpen = Boolean(open);
  el.edgeKindMenu?.classList.toggle("hidden", !open);
  el.edgeKindTrigger?.setAttribute("aria-expanded", open ? "true" : "false");
  el.edgeKindPicker?.classList.toggle("is-menu-open", open);
}

function updateEdgeKindTriggerLabel(activeKind, options) {
  if (!el.edgeKindTriggerLabel) {
    return;
  }
  const active =
    options.find((option) => option.kind === activeKind) ||
    options.find((option) => option.value === activeKind);
  el.edgeKindTriggerLabel.textContent = active?.label || "Select relationship";
}

function canvasToViewportPoint(x, y) {
  return {
    x: x * state.viewport.scale + state.viewport.x,
    y: y * state.viewport.scale + state.viewport.y,
  };
}

function setHoveredEdge(edgeId) {
  hoveredEdgeId = edgeId || null;
  ensureG6Canvas();
  setG6HoverEdge(hoveredEdgeId);
}

function clearHoveredEdge() {
  setHoveredEdge(null);
}

function closeEdgeKindPicker() {
  state.edgeKindPicker.open = false;
  state.edgeKindPicker.menuOpen = false;
  state.edgeKindPicker.edgeId = null;
  state.edgeKindPicker.drawnFromId = null;
  state.edgeKindPicker.drawnToId = null;
  state.edgeKindPicker.options = [];
  if (el.edgeKindPicker) {
    el.edgeKindPicker.classList.add("hidden");
    el.edgeKindPicker.classList.remove("is-menu-open");
    el.edgeKindPicker.style.removeProperty("left");
    el.edgeKindPicker.style.removeProperty("top");
  }
  setEdgeKindMenuOpen(false);
  if (el.edgeKindMenu) {
    el.edgeKindMenu.innerHTML = "";
  }
  if (el.edgeKindTriggerLabel) {
    el.edgeKindTriggerLabel.textContent = "Select relationship";
  }
}

function buildKindOptions(source, target) {
  const kinds = legalKindsBetween(state.activeType, source.type, target.type);
  const traceKind = configuredRelationshipSemantic("traceKind");
  if (supportsBoundedContext() && state.preferredConnectionKind === traceKind) {
    kinds.push(traceKind);
  }
  const uniqueKinds = [...new Set(kinds)];
  return uniqueKinds
    .map((kind) => ({
      kind,
      label: configuredEdgeLabel({ kind }),
      value: kind,
    }))
    .sort((a, b) => a.label.localeCompare(b.label) || a.kind.localeCompare(b.kind));
}

function resolveEdgeEndpointsForKind(nodeA, nodeB, kind, preferredSourceId, preferredTargetId) {
  return modelingResolveEdgeEndpoints(
    state.activeType,
    nodeA,
    nodeB,
    kind,
    preferredSourceId,
    preferredTargetId,
  );
}

function updateEdgeKind(edgeId, kind, preferredSourceId, preferredTargetId) {
  const edge = state.diagram.connections.find((item) => item.id === edgeId);
  if (!edge || !kind) {
    return;
  }
  const drawnFromId = preferredSourceId || state.edgeKindPicker.drawnFromId || edge.sourceId;
  const drawnToId = preferredTargetId || state.edgeKindPicker.drawnToId || edge.targetId;
  const nodeA = state.nodesById.get(drawnFromId);
  const nodeB = state.nodesById.get(drawnToId);
  if (!nodeA || !nodeB) {
    return;
  }
  const resolved = resolveEdgeEndpointsForKind(nodeA, nodeB, kind, drawnFromId, drawnToId);
  if (!resolved) {
    return;
  }
  if (
    edge.sourceId === resolved.sourceId &&
    edge.targetId === resolved.targetId &&
    edge.kind === resolved.kind
  ) {
    return;
  }
  const undoSnapshot = captureDiagramUndoSnapshot();
  edge.sourceId = resolved.sourceId;
  edge.targetId = resolved.targetId;
  edge.kind = resolved.kind;
  addConnectionToGraphAndActiveView(edge);
  commitUndoSnapshot(undoSnapshot);
  ensureG6Canvas();
  updateG6Edge(edgeId);
  updateG6Selection();
  markModelDirty();
  setStatus(`Connection updated: ${configuredEdgeLabel(edge)}`);
}

function renderEdgeKindMenu(options, activeKind) {
  if (!el.edgeKindMenu) {
    return;
  }
  updateEdgeKindTriggerLabel(activeKind, options);
  if (!options.length) {
    el.edgeKindMenu.innerHTML =
      '<div class="edge-kind-picker-empty">No legal relationships between these elements.</div>';
    return;
  }
  el.edgeKindMenu.innerHTML = options
    .map(
      (option) => `<button class="edge-kind-picker-option${
        option.kind === activeKind ? " is-active" : ""
      }"
              type="button"
              data-edge-kind="${escapeHtml(option.kind)}"
              role="option"
              aria-selected="${option.kind === activeKind ? "true" : "false"}">
        <span class="edge-kind-picker-option-label">${escapeHtml(option.label)}</span>
        <span class="edge-kind-picker-option-kind">${escapeHtml(option.kind)}</span>
      </button>`,
    )
    .join("");
}

function positionEdgeKindPicker(pos) {
  if (!el.edgeKindPicker) {
    return;
  }
  const viewportRect = el.canvasViewport?.getBoundingClientRect?.();
  const margin = 8;
  const viewportWidth = viewportRect?.width || window.innerWidth;
  const viewportHeight = viewportRect?.height || window.innerHeight;
  const width = el.edgeKindPicker.offsetWidth || 200;
  const height = el.edgeKindPicker.offsetHeight || 56;
  const x = Math.min(Math.max(pos.x - width / 2, margin), viewportWidth - width - margin);
  const y = Math.min(Math.max(pos.y - height / 2, margin), viewportHeight - height - margin);
  el.edgeKindPicker.style.left = `${Math.round(x)}px`;
  el.edgeKindPicker.style.top = `${Math.round(y)}px`;
}

function ensureEdgeKindPickerBindings() {
  if (edgeKindPickerBound || !el.edgeKindPicker || !el.edgeKindMenu) {
    return;
  }
  edgeKindPickerBound = true;
  ensureNodeExploreToolbarBindings();
  el.edgeKindTrigger?.addEventListener("click", (event) => {
    if (!state.edgeKindPicker.open) {
      return;
    }
    event.stopPropagation();
    setEdgeKindMenuOpen(!state.edgeKindPicker.menuOpen);
    if (state.edgeKindPicker.menuOpen) {
      requestAnimationFrame(() => {
        if (!state.edgeKindPicker.open) {
          return;
        }
        const pos = canvasToViewportPoint(state.edgeKindPicker.x, state.edgeKindPicker.y);
        positionEdgeKindPicker(pos);
      });
    }
  });
  el.edgeKindMenu.addEventListener("click", (event) => {
    const target = event.target instanceof Element ? event.target : null;
    const option = target?.closest("[data-edge-kind]");
    if (!option || !state.edgeKindPicker.open) {
      return;
    }
    const edgeId = state.edgeKindPicker.edgeId;
    const kind = option.getAttribute("data-edge-kind");
    if (!edgeId || !kind) {
      return;
    }
    updateEdgeKind(edgeId, kind);
    renderEdgeKindMenu(state.edgeKindPicker.options, kind);
    setEdgeKindMenuOpen(false);
    event.stopPropagation();
  });
  document.addEventListener("mousedown", (event) => {
    if (!state.edgeKindPicker.open) {
      return;
    }
    const target = event.target instanceof Element ? event.target : null;
    if (!target) {
      closeEdgeKindPicker();
      return;
    }
    if (target.closest(".edge-kind-picker")) {
      return;
    }
    closeEdgeKindPicker();
  });
  document.addEventListener(
    "touchstart",
    (event) => {
      if (!state.edgeKindPicker.open) {
        return;
      }
      const target = event.target instanceof Element ? event.target : null;
      if (!target) {
        closeEdgeKindPicker();
        return;
      }
      if (target.closest(".edge-kind-picker")) {
        return;
      }
      closeEdgeKindPicker();
    },
    { passive: true },
  );
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && state.edgeKindPicker.open) {
      if (state.edgeKindPicker.menuOpen) {
        setEdgeKindMenuOpen(false);
        return;
      }
      closeEdgeKindPicker();
    }
  });
}

function openEdgeKindPicker(edgeId, options, canvasX, canvasY, { drawnFromId, drawnToId } = {}) {
  if (!el.edgeKindPicker || !el.edgeKindMenu) {
    return;
  }
  ensureEdgeKindPickerBindings();
  const edge = state.diagram.connections.find((item) => item.id === edgeId);
  if (!edge) {
    closeEdgeKindPicker();
    return;
  }
  state.edgeKindPicker.open = true;
  state.edgeKindPicker.edgeId = edgeId;
  state.edgeKindPicker.drawnFromId = drawnFromId || edge.sourceId;
  state.edgeKindPicker.drawnToId = drawnToId || edge.targetId;
  state.edgeKindPicker.options = [...options];
  state.edgeKindPicker.x = canvasX;
  state.edgeKindPicker.y = canvasY;

  renderEdgeKindMenu(options, edge.kind);
  setEdgeKindMenuOpen(false);
  const pos = canvasToViewportPoint(canvasX, canvasY);
  el.edgeKindPicker.classList.remove("hidden");
  positionEdgeKindPicker(pos);
  el.edgeKindTrigger?.focus?.();
}

function legalKindsForConnection(sourceType, targetType) {
  const kinds = new Set(legalKinds(state.activeType, sourceType, targetType));
  const traceKind = configuredRelationshipSemantic("traceKind");
  if (supportsBoundedContext() && state.preferredConnectionKind === traceKind) {
    kinds.add(traceKind);
  }
  return [...kinds];
}

function openG6EdgeKindPicker(edgeId) {
  const edge = connectionsById.get(edgeId);
  if (!edge || edge.bundle) {
    return;
  }
  const source = state.nodesById.get(edge.sourceId);
  const target = state.nodesById.get(edge.targetId);
  if (!source || !target) {
    return;
  }
  const options = buildKindOptions(source, target);
  if (!options.length) {
    return;
  }
  const nodeW = getNodeWidth();
  const nodeH = getNodeHeight();
  openEdgeKindPicker(
    edge.id,
    options,
    (source.x + nodeW / 2 + target.x + nodeW / 2) / 2,
    (source.y + nodeH / 2 + target.y + nodeH / 2) / 2,
    { drawnFromId: edge.sourceId, drawnToId: edge.targetId },
  );
}

export function renderEdges() {
  ensureG6Canvas();
  if (
    state.selectedConnectionId &&
    !state.diagram.connections.some((edge) => edge.id === state.selectedConnectionId)
  ) {
    state.selectedConnectionId = null;
  }
  syncCanvasIndexesFromState();
  syncG6FromState({ full: false });
  updateG6Selection();
}

function hasModelArtifact(keys) {
  const root = state.baseModel && typeof state.baseModel === "object" ? state.baseModel : {};
  return keys.some((key) => {
    const value = root[key];
    return Array.isArray(value) ? value.length > 0 : Boolean(value);
  });
}

export function renderDiagram() {
  ensureG6Canvas();
  syncCanvasIndexesFromState();
  renderG6Diagram();
  el.canvasGrid?.style.setProperty("--viewport-scale", String(state.viewport.scale || 1));
  el.canvasGrid?.classList.toggle("lod-low", state.viewport.scale < 0.35);
  el.canvasGrid?.classList.toggle(
    "lod-medium",
    state.viewport.scale >= 0.35 && state.viewport.scale < 0.75,
  );
  el.canvasGrid?.classList.toggle("lod-high", state.viewport.scale >= 1.5);
  Object.keys(MODEL_TYPES).forEach((typeKey) => {
    el.workspace?.classList.toggle(`${typeKey}-view-active`, state.activeType === typeKey);
    if (state.activeType === typeKey) {
      el.workspace?.setAttribute(
        `data-${typeKey}-view-profile`,
        activeConfiguredViewProfile(typeKey) || "",
      );
    } else {
      el.workspace?.removeAttribute(`data-${typeKey}-view-profile`);
    }
  });
}

// Compatibility helper for non-canvas modules that changed semantic state.
// The public name is kept for existing callers, but the modeling surface is
// G6-only: this applies a diff to the graph renderer.
export function syncDiagramRenderer({ full = false } = {}) {
  ensureG6Canvas();
  syncCanvasIndexesFromState();
  syncG6FromState({ full });
  el.canvasGrid?.style.setProperty("--viewport-scale", String(state.viewport.scale || 1));
  el.canvasGrid?.classList.toggle("lod-low", state.viewport.scale < 0.35);
  el.canvasGrid?.classList.toggle(
    "lod-medium",
    state.viewport.scale >= 0.35 && state.viewport.scale < 0.75,
  );
  el.canvasGrid?.classList.toggle("lod-high", state.viewport.scale >= 1.5);
  Object.keys(MODEL_TYPES).forEach((typeKey) => {
    el.workspace?.classList.toggle(`${typeKey}-view-active`, state.activeType === typeKey);
  });
}

export function syncRendererSelection() {
  ensureG6Canvas();
  updateG6Selection();
  updateG6ContextBoxes(null, { useCache: true });
  updateNodeExploreToolbar();
}

// ── Canvas event handlers ─────────────────────────────────────────────────────

function eventModifierState(event) {
  return {
    shiftKey: Boolean(event?.shiftKey),
    ctrlKey: Boolean(event?.ctrlKey),
    metaKey: Boolean(event?.metaKey),
  };
}

function handleG6NodeClick(nodeId, event = {}) {
  if (supportsBoundedContext() && state.boundedContextCreateMode) {
    const node = state.nodesById.get(nodeId);
    if (isBoundedContextNode(node)) {
      return;
    }
    const next = new Set(state.boundedContextDraftNodeIds);
    if (next.has(nodeId)) {
      next.delete(nodeId);
    } else {
      next.add(nodeId);
    }
    state.boundedContextDraftNodeIds = next;
    updateG6ImpactState();
    setStatus(`${next.size} element${next.size !== 1 ? "s" : ""} selected for bounded context`);
    return;
  }

  const modifiers = eventModifierState(event);
  if (modifiers.shiftKey || modifiers.ctrlKey || modifiers.metaKey) {
    toggleNodeInSelection(nodeId);
    return;
  }
  const node = state.nodesById.get(nodeId);
  if (supportsBoundedContext() && isBoundedContextNode(node)) {
    if (state.boundedContextViewMode === "overview") {
      setNodeMultiSelection([nodeId]);
      selectBoundedContext(boundedContextNameFromContextNode(node));
      return;
    }
    startBoundedContextAssignment(boundedContextNameFromContextNode(node));
    setNodeMultiSelection([nodeId]);
    updateG6Selection();
    return;
  }
  setNodeMultiSelection([nodeId]);
  activateNode(nodeId);
  updateG6Selection();
}

function handleG6NodeDoubleClick(nodeId, event = {}) {
  const node = state.nodesById.get(nodeId);
  if (!node) {
    return;
  }
  event?.preventDefault?.();
  if (supportsBoundedContext() && isBoundedContextNode(node)) {
    openBoundedContextFocus(boundedContextNameFromContextNode(node));
    return;
  }
  if (isContainerElement(node)) {
    openContainerFocus(nodeId);
    return;
  }
  const undoSnapshot = captureDiagramUndoSnapshot();
  const startLabel = node.label;
  beginG6InlineLabelEdit(node, {
    onCommit: (rawText) => {
      const resolved = commitNodeLabel(node, rawText);
      syncNodeMetaToGraph(node);
      updateG6Node(node.id);
      if (resolved !== startLabel) {
        commitUndoSnapshot(undoSnapshot);
        markModelDirty();
      }
    },
  });
}

function handleG6CanvasClick() {
  clearHoveredEdge();
  closeEdgeKindPicker();
  closeAttributePanel();
  state.selectedNodeId = null;
  state.selectedNodeIds = new Set();
  state.selectedConnectionId = null;
  state.selectedBoundedContextName = null;
  updateG6Selection();
}

function startG6NodeDrag(nodeId) {
  const node = state.nodesById.get(nodeId);
  if (!node) {
    return;
  }
  clearHoveredEdge();
  closeEdgeKindPicker();
  clearTransientEdgeLayouts(edgeIdsByNodeId.get(nodeId));
  state.dragNode = {
    id: nodeId,
    startX: 0,
    startY: 0,
    nodeX: node.x,
    nodeY: node.y,
    moved: false,
    undoSnapshot: dragUndoSnapshot([nodeId]),
  };
}

function moveG6NodeDrag(nodeId, position) {
  const node = state.nodesById.get(nodeId);
  if (!node || !position) {
    return;
  }
  node.x = Math.round(position.x);
  node.y = Math.round(position.y);
  node.meta = node.meta && typeof node.meta === "object" ? node.meta : {};
  node.meta.x = node.x;
  node.meta.y = node.y;
  state.dragNode = state.dragNode || { id: nodeId };
  state.dragNode.moved = true;
  updateG6NodeIcons();
  if (nodeId === state.selectedNodeId) {
    updateNodeExploreToolbar();
  }
}

function endG6NodeDrag(nodeId, position, { moved = false } = {}) {
  const node = state.nodesById.get(nodeId);
  if (!node) {
    state.dragNode = null;
    return;
  }
  if (position) {
    node.x = Math.round(position.x);
    node.y = Math.round(position.y);
    node.meta = node.meta && typeof node.meta === "object" ? node.meta : {};
    node.meta.x = node.x;
    node.meta.y = node.y;
  }
  syncNodeMetaToGraph(node);
  if (moved || state.dragNode?.moved) {
    commitUndoSnapshot(state.dragNode?.undoSnapshot);
    persistNodePositionInActiveView(node);
    markModelDirty();
  }
  refreshG6Edges([...(edgeIdsByNodeId.get(node.id) || [])]);
  updateG6ContextBoxes();
  updateG6Selection();
  state.dragNode = null;
}

function startG6ConnectionDrag(sourceId) {
  state.linkDrag = { sourceId, pointerX: 0, pointerY: 0, hoveredTargetId: null };
  updateG6ConnectionState();
  setStatus("Drag to another element to create a legal connection");
}

function openG6ContainerTool(nodeId) {
  const node = state.nodesById.get(nodeId);
  if (supportsBoundedContext() && isBoundedContextNode(node)) {
    openBoundedContextFocus(boundedContextNameFromContextNode(node));
    return;
  }
  openContainerFocus(nodeId);
}

function selectConnection(connectionId, { openPicker = false } = {}) {
  if (!connectionId) {
    return;
  }
  if (state.selectedConnectionId === connectionId) {
    closeAttributePanel();
    ensureG6Canvas();
    state.selectedConnectionId = null;
    updateG6Selection();
    return;
  }
  openConnectionPanel(connectionId);
  ensureG6Canvas();
  updateG6Selection();
  updateG6Edge(connectionId);
  if (openPicker) {
    openG6EdgeKindPicker(connectionId);
  }
  setStatus("Connection selected (press Delete to remove)");
}

function activateNode(nodeId) {
  if (state.connectMode) {
    if (!state.connectSourceId) {
      state.connectSourceId = nodeId;
      setStatus(`Connection source: ${nodeId}. Select target.`);
      ensureG6Canvas();
      updateG6ConnectionState();
      return;
    }
    if (state.connectSourceId === nodeId) {
      setStatus("Source and target cannot be the same");
      return;
    }
    addConnection(state.connectSourceId, nodeId, {
      interactivePicker: true,
      preferredKind: state.preferredConnectionKind,
    });
    state.connectSourceId = null;
    ensureG6Canvas();
    updateG6ConnectionState();
    return;
  }

  // In impact mode, clicking a node fetches impact analysis for it
  if (state.impactMode) {
    if (!state.modelId) {
      setStatus("Save or load a model first to use impact analysis");
    } else {
      fetchImpact(nodeId);
    }
    return;
  }

  // Toggle: clicking the same node again closes the panel
  if (state.selectedNodeId === nodeId) {
    closeAttributePanel();
    clearNodeMultiSelection();
    return;
  }

  setNodeMultiSelection([nodeId]);
  openAttributePanel(nodeId);
}

function selectBoundedContext(contextName) {
  const normalized = normalizeContextName(contextName);
  if (!normalized) {
    return;
  }
  state.selectedBoundedContextName = normalized;
  state.selectedNodeId = null;
  state.selectedNodeIds = new Set();
  state.selectedConnectionId = null;
  applyNodeSelectionStyles();
  openBoundedContextPanel(normalized);
  ensureG6Canvas();
  updateG6ContextBoxes();
  updateG6Selection();
}

function openBoundedContextFocus(contextName) {
  const normalized = normalizeContextName(contextName);
  if (!normalized) {
    return;
  }
  state.boundedContextViewMode = "focus";
  state.activeBoundedContextName = normalized;
  state.boundedContextCreateMode = false;
  clearContextDraftSelection();
  renderDiagram();
  notifyModelToolsChanged();
  setStatus(`Opened bounded context "${normalized}". Use Back to return.`);
}

export function openBoundedContextOverview() {
  const createdContextNodes = ensureBoundedContextNodesForAllNames();
  const hasContextNodes = state.diagram.nodes.some(isBoundedContextNode);
  if (!hasContextNodes) {
    state.boundedContextViewMode = "normal";
    state.activeBoundedContextName = "";
    state.boundedContextCreateMode = false;
    clearContextDraftSelection();
    renderDiagram();
    notifyModelToolsChanged();
    setStatus(
      `No bounded contexts yet. Use New Context or add a ${
        modelingElementDefinition(state.activeType, boundedContextType())?.displayName ||
        boundedContextType() ||
        "context"
      }.`,
    );
    return;
  }
  state.boundedContextViewMode = "overview";
  state.activeBoundedContextName = "";
  state.boundedContextCreateMode = false;
  clearContextDraftSelection();
  renderDiagram();
  if (createdContextNodes) {
    markModelDirty();
  }
  notifyModelToolsChanged();
  setStatus("Bounded context overview");
}

export function closeBoundedContextSpecialView() {
  state.boundedContextViewMode = "normal";
  state.activeBoundedContextName = "";
  renderDiagram();
  notifyModelToolsChanged();
  setStatus("Returned to full model view");
}

// ── Drag-and-drop from palette ────────────────────────────────────────────────

export function setupDnD() {
  ensureG6Canvas();
  renderG6Diagram();
  el.canvasViewport.addEventListener("dragover", (e) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = "copy";
  });
  el.canvasViewport.addEventListener("drop", async (e) => {
    e.preventDefault();
    let paletteItem = null;
    try {
      paletteItem = JSON.parse(e.dataTransfer.getData("application/x-modless-palette-item"));
    } catch {
      paletteItem = null;
    }
    const type = String(
      paletteItem?.type ||
        e.dataTransfer.getData("application/x-modless-node-type") ||
        e.dataTransfer.getData("text/node-type") ||
        state.paletteDragType ||
        "",
    ).trim();
    state.paletteDragType = "";
    const containerFocus = activeContainerFocus();
    const allowedTypes = new Set(
      containerFocus
        ? modelingContainmentPalette(state.activeType, containerFocus.elementType)
        : modelingPalette(state.activeType),
    );
    if (
      !type ||
      (paletteItem?.level && paletteItem.level !== state.activeType) ||
      !allowedTypes.has(type)
    ) {
      setStatus(
        type
          ? containerFocus
            ? `${type} is not containable in ${containerFocus.label || containerFocus.elementType}`
            : `${type} is not a standalone palette element`
          : "Invalid palette drop",
      );
      return;
    }
    pushDiagramUndoSnapshot();
    const pos = toCanvasCoordinates(e.clientX, e.clientY);
    const beforeNodeIds = new Set(state.diagram.nodes.map((item) => item.id));
    const beforeEdgeIds = new Set(state.diagram.connections.map((item) => item.id));
    const node = getDefaultNode(state.activeType, type, Math.round(pos.x), Math.round(pos.y));
    assignNodeToSemanticContainer(node);
    state.diagram.nodes.push(node);
    addNodeToGraphAndActiveView(node);
    materializeActiveView();
    if (state.tabs[state.activeType]) {
      state.tabs[state.activeType].diagram = state.diagram;
    }
    syncCanvasIndexesFromState();
    ensureG6Canvas();
    const addedVisibleNodes = state.diagram.nodes.filter((item) => !beforeNodeIds.has(item.id));
    const addedVisibleEdges = state.diagram.connections.filter(
      (item) => !beforeEdgeIds.has(item.id),
    );
    if (addedVisibleNodes.length === 1 && !addedVisibleEdges.length) {
      addG6Node(addedVisibleNodes[0]);
    } else {
      syncG6FromState({ full: false });
    }
    updateG6Selection();
    updateG6ContextBoxes();
    const created = state.graph.elementsById.get(node.id);
    setStatus(`Added ${created?.eClass || type}`);
    markModelDirty();
  });
}

function containmentAcceptsType(entry, type) {
  return (entry?.types || []).some((candidate) => modelingTypeMatchesSafe(candidate, type));
}

function assignNodeToFocusedContainer(node) {
  const focus = activeContainerFocus();
  if (!focus) {
    return false;
  }
  const owner = state.graph.elementsById.get(focus.elementId);
  const ownerNode = state.nodesById.get(focus.elementId);
  if (!owner) {
    return false;
  }
  const containmentEntry = modelingContainmentEntryForChildType(
    state.activeType,
    focus.elementType,
    node.type,
  );
  if (!containmentEntry?.feature) {
    return false;
  }
  const containment = modelingContainmentsForType(state.activeType, focus.elementType).find(
    (entry) => entry.feature === containmentEntry.feature,
  );
  if (
    !containment ||
    containment.relationshipOnly ||
    !containmentAcceptsType(containment, node.type) ||
    (containment.many === false && owner[containment.feature])
  ) {
    return false;
  }
  node.meta.__ownerId = owner.id;
  node.meta.__containmentFeature = containment.feature;
  addReferenceValue(owner, containment.feature, node.id, containment.many !== false);
  if (ownerNode?.meta) {
    ownerNode.meta[containment.feature] = owner[containment.feature];
  }
  return true;
}

function assignNodeToSemanticContainer(node) {
  if (assignNodeToFocusedContainer(node)) {
    return true;
  }
  if (
    modelingRootContainments(state.activeType).some((entry) =>
      containmentAcceptsType(entry, node.type),
    )
  ) {
    return false;
  }
  const candidates = [];
  state.graph.elementsById.forEach((owner) => {
    const containment = modelingContainmentsForType(
      state.activeType,
      owner.eClass || owner.type,
    ).find((entry) => !entry.relationshipOnly && containmentAcceptsType(entry, node.type));
    if (!containment || (containment.many === false && owner[containment.feature])) {
      return;
    }
    const visibleOwner = state.nodesById.get(owner.id);
    const distance = visibleOwner
      ? Math.hypot(Number(visibleOwner.x) - node.x, Number(visibleOwner.y) - node.y)
      : Number.POSITIVE_INFINITY;
    candidates.push({ owner, containment, distance });
  });
  candidates.sort((left, right) => {
    const leftSelected = left.owner.id === state.selectedNodeId ? 1 : 0;
    const rightSelected = right.owner.id === state.selectedNodeId ? 1 : 0;
    return rightSelected - leftSelected || left.distance - right.distance;
  });
  const selected = candidates[0];
  if (!selected) {
    return false;
  }
  node.meta.__ownerId = selected.owner.id;
  node.meta.__containmentFeature = selected.containment.feature;
  addReferenceValue(
    selected.owner,
    selected.containment.feature,
    node.id,
    selected.containment.many !== false,
  );
  return true;
}

// ── Connection management ─────────────────────────────────────────────────────

export function addConnection(
  sourceId,
  targetId,
  { interactivePicker = false, preferredKind = null } = {},
) {
  const source = state.nodesById.get(sourceId);
  const target = state.nodesById.get(targetId);
  if (!source || !target) {
    return false;
  }
  if (source.id === target.id) {
    setStatus("Source and target cannot be the same");
    return false;
  }
  const pairKinds = legalKindsBetween(state.activeType, source.type, target.type);
  if (!pairKinds.length) {
    if (createShortcutConnection(source, target)) {
      return true;
    }
    setStatus("Illegal connection type for selected nodes");
    return false;
  }
  const forwardKinds = legalKindsForConnection(source.type, target.type);
  const reverseKinds = legalKindsForConnection(target.type, source.type);
  let kind = pairKinds.includes(preferredKind) ? preferredKind : pairKinds[0];
  const resolved = resolveEdgeEndpointsForKind(source, target, kind, sourceId, targetId);
  if (!resolved) {
    setStatus("Illegal connection type for selected nodes");
    return false;
  }
  const resolvedSource = state.nodesById.get(resolved.sourceId);
  const resolvedTarget = state.nodesById.get(resolved.targetId);
  if (!resolvedSource || !resolvedTarget) {
    return false;
  }
  kind = resolved.kind;

  const exists = state.diagram.connections.some(
    (edge) => edge.sourceId === resolvedSource.id && edge.targetId === resolvedTarget.id,
  );
  if (exists) {
    setStatus("Connection already exists between these elements");
    return false;
  }

  const edge = {
    id: genId("e"),
    sourceId: resolvedSource.id,
    targetId: resolvedTarget.id,
    kind,
  };
  pushDiagramUndoSnapshot();
  state.diagram.connections.push(edge);
  addConnectionToGraphAndActiveView(edge);
  state.selectedConnectionId = edge.id;
  ensureG6Canvas();
  connectionsById.set(edge.id, edge);
  [edge.sourceId, edge.targetId].forEach((nodeId) => {
    if (!edgeIdsByNodeId.has(nodeId)) {
      edgeIdsByNodeId.set(nodeId, new Set());
    }
    edgeIdsByNodeId.get(nodeId).add(edge.id);
  });
  addG6Edge(edge);
  updateG6Selection();
  markModelDirty();
  if (interactivePicker) {
    const nodeW = getNodeWidth();
    const nodeH = getNodeHeight();
    const sx = resolvedSource.x + nodeW / 2;
    const sy = resolvedSource.y + nodeH / 2;
    const tx = resolvedTarget.x + nodeW / 2;
    const ty = resolvedTarget.y + nodeH / 2;
    const options = buildKindOptions(source, target);
    if (options.length) {
      openEdgeKindPicker(edge.id, options, (sx + tx) / 2, (sy + ty) / 2, {
        drawnFromId: sourceId,
        drawnToId: targetId,
      });
    }
  }
  if (interactivePicker && pairKinds.length > 1) {
    setStatus("Connection added. Choose relationship type.");
  } else if (!forwardKinds.length && reverseKinds.length) {
    setStatus(
      `Connection added with legal direction: ${
        resolvedSource.label || resolvedSource.type
      } -> ${resolvedTarget.label || resolvedTarget.type}`,
    );
  } else {
    setStatus(`Connection added: ${configuredEdgeLabel(edge)}`);
  }
  return true;
}

function createShortcutConnection(source, target) {
  if (!isModelingLevel(state.activeType)) {
    return false;
  }
  let rule = null;
  try {
    rule = modelingShortcutConnectorRules(state.activeType).find(
      (candidate) =>
        modelingTypeMatchesSafe(candidate.sourceType, source.type) &&
        modelingTypeMatchesSafe(candidate.targetType, target.type),
    );
  } catch {
    rule = null;
  }
  if (!rule) {
    return false;
  }
  const intermediateTypes = Array.isArray(rule.intermediateTypes) ? rule.intermediateTypes : [];
  const edgeKinds = Array.isArray(rule.edgeKinds) ? rule.edgeKinds : [];
  if (!intermediateTypes.length || edgeKinds.length < intermediateTypes.length + 1) {
    return false;
  }
  pushDiagramUndoSnapshot();
  const chain = [source];
  intermediateTypes.forEach((type, index) => {
    const offsetX = 180 + index * 140;
    const offsetY = index % 2 === 0 ? 72 : -72;
    const node = getDefaultNode(state.activeType, type, source.x + offsetX, source.y + offsetY);
    node.label = `${type}-${node.id.slice(-4)}`;
    node.meta.name = node.label;
    node.meta.label = node.label;
    applyShortcutContainment(node, rule, source, target);
    state.diagram.nodes.push(node);
    addNodeToGraphAndActiveView(node);
    chain.push(node);
  });
  chain.push(target);
  for (let index = 0; index < chain.length - 1; index++) {
    const edge = {
      id: genId("e"),
      sourceId: chain[index].id,
      targetId: chain[index + 1].id,
      kind: requiredConfiguredKind(edgeKinds[index], `shortcut edgeKinds[${index}]`),
    };
    state.diagram.connections.push(edge);
    addConnectionToGraphAndActiveView(edge);
  }
  createShortcutViewRelationship(rule, source, target, chain.slice(1, -1));
  syncActiveViewFromVisibleGraph();
  ensureG6Canvas();
  syncCanvasIndexesFromState();
  syncG6FromState({ full: false });
  markModelDirty();
  setStatus(`Created ${rule.label || "shortcut connector"}`);
  return true;
}

function shortcutSemanticElement(node) {
  return state.graph.elementsById.get(node?.id) || node?.meta || null;
}

function shortcutStackOwner(source, target) {
  for (const endpoint of [source, target]) {
    const element = shortcutSemanticElement(endpoint);
    const owner = element?.__ownerId ? state.graph.elementsById.get(element.__ownerId) : null;
    if (owner && modelingTypeMatchesSafe("SamStack", owner.eClass || owner.type)) {
      return owner;
    }
  }
  return [...state.graph.elementsById.values()].find((element) =>
    modelingTypeMatchesSafe("SamStack", element.eClass || element.type),
  );
}

function shortcutBindingOwner(binding, source, target) {
  if (binding.owner === "source") {
    return shortcutSemanticElement(source);
  }
  if (binding.owner === "target") {
    return shortcutSemanticElement(target);
  }
  if (binding.owner === "stack") {
    return shortcutStackOwner(source, target);
  }
  return null;
}

function attachShortcutChild(child, owner, feature) {
  if (!child?.meta || !owner?.id || !feature) {
    return false;
  }
  const containment = modelingContainmentsForType(
    state.activeType,
    owner.eClass || owner.type,
  ).find((entry) => entry.feature === feature);
  if (!containment) {
    return false;
  }
  child.meta.__ownerId = owner.id;
  child.meta.__containmentFeature = feature;
  addReferenceValue(owner, feature, child.id, containment.many !== false);
  return true;
}

function ensureShortcutScaffold(binding, source, target) {
  const scaffoldOwnerBinding = { owner: binding.scaffoldOwner };
  const owner = shortcutBindingOwner(scaffoldOwnerBinding, source, target);
  if (!owner || !binding.scaffoldType || !binding.scaffoldFeature) {
    return null;
  }
  const existingId = Array.isArray(owner[binding.scaffoldFeature])
    ? owner[binding.scaffoldFeature][0]
    : owner[binding.scaffoldFeature];
  const existing = state.graph.elementsById.get(String(existingId || ""));
  if (existing) {
    return existing;
  }
  const scaffold = getDefaultNode(state.activeType, binding.scaffoldType, 0, 0);
  if (!attachShortcutChild(scaffold, owner, binding.scaffoldFeature)) {
    return null;
  }
  const element = {
    ...scaffold.meta,
    id: scaffold.id,
    eClass: scaffold.type,
    name: scaffold.label,
    label: scaffold.label,
  };
  state.graph.elementsById.set(element.id, element);
  return element;
}

function applyShortcutContainment(node, rule, source, target) {
  const binding = (rule.containmentBindings || []).find((candidate) =>
    modelingTypeMatchesSafe(candidate.type, node.type),
  );
  if (!binding) {
    return;
  }
  const owner =
    binding.owner === "scaffold"
      ? ensureShortcutScaffold(binding, source, target)
      : shortcutBindingOwner(binding, source, target);
  attachShortcutChild(node, owner, binding.feature);
}

function createShortcutViewRelationship(rule, source, target, intermediates) {
  const viewType = String(rule.viewType || "").trim();
  if (!viewType) {
    return null;
  }
  const edge = {
    id: genId("e"),
    sourceId: source.id,
    targetId: target.id,
    kind: requiredConfiguredKind(rule.summaryKind, "shortcut summaryKind"),
    label: String(rule.label || viewType),
  };
  state.diagram.connections.push(edge);
  addConnectionToGraphAndActiveView(edge);
  const relationship = state.graph.relationshipsById.get(edge.id);
  if (!relationship) {
    return null;
  }
  relationship.eClass = viewType;
  relationship.name = edge.label;
  relationship.generated = true;
  relationship.rootFeature = "relationshipViews";
  relationship.__containmentFeature = "relationshipViews";
  applyShortcutViewReferences(relationship, rule, source, target, intermediates);
  return relationship;
}

function applyShortcutViewReferences(relationship, rule, source, target, intermediates) {
  const bindings = Array.isArray(rule.viewReferences) ? rule.viewReferences : [];
  bindings.forEach((binding) => {
    const feature = String(binding?.feature || "").trim();
    if (!feature) {
      return;
    }
    if (binding.role === "source") {
      relationship[feature] = source.id;
      return;
    }
    if (binding.role === "target") {
      relationship[feature] = target.id;
      return;
    }
    const expectedType = String(binding?.type || "").trim();
    const match = intermediates.find((item) => modelingTypeMatchesSafe(expectedType, item.type));
    if (match) {
      relationship[feature] = match.id;
    } else if (binding.required === false) {
      relationship[feature] = null;
    }
  });
}

function modelingTypeMatchesSafe(expected, actual) {
  try {
    return modelingTypeMatches(state.activeType, expected, actual);
  } catch {
    return expected === actual;
  }
}

// ── Connect mode ─────────────────────────────────────────────────────────────

export function setConnectMode(enabled) {
  state.connectMode = enabled;
  state.connectSourceId = null;
  if (!enabled) {
    state.preferredConnectionKind = null;
    closeEdgeKindPicker();
  }
  ensureG6Canvas();
  updateG6ConnectionState();
  setStatus(enabled ? "Connect mode enabled - click source then target" : "Connect mode disabled");
}

export function startConnectionFromNode(nodeId, preferredKind = null) {
  const node =
    state.nodesById.get(nodeId) || state.diagram.nodes.find((candidate) => candidate.id === nodeId);
  if (!node) {
    return false;
  }
  state.connectMode = true;
  state.connectSourceId = nodeId;
  state.preferredConnectionKind = preferredKind || null;
  ensureG6Canvas();
  updateG6ConnectionState();
  setStatus(
    preferredKind
      ? `${preferredKind}: select a highlighted legal target`
      : "Select a highlighted legal target",
  );
  return true;
}

// ── Impact highlight (called by renderNodes and impact module) ────────────────

export function highlightImpactedNodes() {
  ensureG6Canvas();
  updateG6ImpactState();
}

export function scrollToNodeAndHighlight(elementId) {
  ensureG6Canvas();
  const node =
    state.nodesById.get(elementId) ||
    state.diagram.nodes.find((candidate) => candidate.id === elementId);
  if (!node) {
    return;
  }
  focusG6Node(elementId);
  const previousImpact = state.impactData;
  state.impactData = {
    ...(state.impactData || {}),
    focalElement: { elementId },
  };
  updateG6ImpactState();
  setTimeout(() => {
    state.impactData = previousImpact;
    updateG6ImpactState();
  }, 2500);
}

export function scrollToConnectionAndHighlight(connectionId) {
  const edge = state.diagram.connections.find((item) => item.id === connectionId);
  if (!edge) {
    return false;
  }
  const source = state.nodesById.get(edge.sourceId);
  const target = state.nodesById.get(edge.targetId);
  if (!source || !target) {
    return false;
  }

  const nodeW = getNodeWidth();
  const nodeH = getNodeHeight();
  const midX = (source.x + nodeW / 2 + target.x + nodeW / 2) / 2;
  const midY = (source.y + nodeH / 2 + target.y + nodeH / 2) / 2;
  ensureG6Canvas();
  state.selectedConnectionId = connectionId;
  focusG6CanvasPoint(midX, midY);
  updateG6Selection();
  updateG6Edge(connectionId);
  setTimeout(() => updateG6Edge(connectionId), 1800);
  return true;
}
