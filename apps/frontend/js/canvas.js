import { MODEL_TYPES } from "./config.js";
import { state } from "./state.js";
import { el } from "./dom.js";
import { escapeHtml, genId } from "./utils.js";
import { ensureReadableLayout } from "./layout-engine.js";
import { getDefaultNode, legalKinds, legalKindsBetween, saveStoredEdgeLayout } from "./diagram.js";
import {
  activeView,
  addConnectionToGraphAndActiveView,
  addNodeToGraphAndActiveView,
  persistNodePositionInActiveView,
  prepareViewNodeIndex,
  syncActiveViewFromVisibleGraph,
} from "./graph-store.js";
import {
  CURRENT_LAYOUT_GEOMETRY_VERSION,
  isContainerElement,
  materializeActiveView,
} from "./view-materializer.js";
import {
  modelingElementDefinition,
  modelingContainerDefinition,
  modelingCanvasPaletteTypes,
  modelingContainmentEntryForChildType,
  modelingContainerFocusPolicy,
  modelingContainmentsForType,
  isModelingLevel,
  modelingLevelConfig,
  modelingLevelLabel,
  modelingStandalonePaletteType,
  modelingRootContainments,
  modelingRootType,
  modelingRelationshipKindLabel,
  modelingRelationshipPresentation,
  modelingResolveEdgeEndpoints,
  modelingRoleSize,
  modelingShortcutConnectorRules,
  modelingTypeMatches,
  modelingViewDefinition,
  resolveModelingIconSource,
} from "./modeling-config-data.js";
import { guidedPaletteFocusTypes } from "./guided-modeling.js";
import { addReferenceValue, modelTypeMatches } from "./model-utils.js";
import { setStatus } from "./status.js";
// NOTE: These imports form intentional circular references (ES module live bindings).
// All functions are only called at runtime (event handlers / async), never at module init.
import { markModelDirty } from "./model-save-ui.js";
import { closeAttributePanel, openAttributePanel, openConnectionPanel } from "./attr-panel.js";
import { fetchImpact } from "./impact.js";
import {
  captureAddConnectionUndoSnapshot,
  captureAddElementUndoSnapshot,
  captureConnectionUndoSnapshot,
  captureDiagramUndoSnapshot,
  captureNodePositionUndoSnapshot,
  pushDiagramUndoSnapshot,
} from "./undo.js";
import { getCanvasFitArea } from "./canvas-viewport-fit.js";
import {
  addCanvasEdge,
  addCanvasNode,
  beginCanvasInlineLabelEdit,
  ensureCanvas as mountActiveCanvas,
  fitCanvasToDiagram,
  focusCanvasPoint,
  focusCanvasNode,
  getCanvasEditor,
  isCanvasRendererAvailable,
  onCanvasViewportChanged,
  refreshCanvasEdges,
  removeCanvasEdge,
  removeCanvasNode,
  renderCanvasDiagram,
  resetCanvasView as adapterResetCanvasView,
  setCanvasHoverEdge,
  setCanvasHoverNode,
  setCanvasMountOptions,
  syncCanvasFromState,
  toGraphCoordinates,
  updateCanvasConnectionState,
  updateCanvasContextBoxes,
  updateCanvasEdge,
  updateCanvasImpactState,
  updateCanvasNode,
  updateCanvasSelection,
  updateCanvasViewport,
  zoomCanvasBy as adapterZoomCanvasBy,
} from "./graph-editor/renderer-adapter.js";
import { nodeSizeForDiagram } from "./graph-editor/g6-style.js";
import { isFullColorIconSource, resolveThemeColor } from "./theme-colors.js";

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

const INTERNAL_TARGET_SUMMARY_PREFIX = "internal-target";
const edgeIdsByNodeId = new Map(); // nodeId -> Set(edgeId)

function resolveIconSource(src) {
  return resolveModelingIconSource(src);
}

function configuredNodeSize(role = "detail") {
  const roleSize = modelingRoleSize(state.activeType, role);
  const policy = modelingLevelConfig(state.activeType).canvasPolicy || {};
  const width = Number(
    roleSize?.width ?? policy.nodeWidth ?? modelingRoleSize(state.activeType, "node")?.width,
  );
  const height = Number(
    roleSize?.height ?? policy.nodeHeight ?? modelingRoleSize(state.activeType, "node")?.height,
  );
  return {
    width: Number.isFinite(width) && width > 0 ? width : 0,
    height: Number.isFinite(height) && height > 0 ? height : 0,
  };
}

const connectionsById = state.connectionsById;
let hoveredEdgeId = null;
let _inlineLabelEditStartLabel = "";
let _inlineLabelEditUndoSnapshot = null;

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

// Single-element mutations must not rebuild indexes for the whole model.
export function removeConnectionFromCanvas(connectionId) {
  const id = String(connectionId || "");
  const edge = connectionsById.get(id);
  // Graph-store indexes can include relationships that are not rendered in the
  // current view. Passing one of those ids to G6 causes it to reject a removal.
  if (!id || !edge) return;
  connectionsById.delete(id);
  [edge?.sourceId, edge?.targetId].forEach((nodeId) => {
    const edgeIds = edgeIdsByNodeId.get(nodeId);
    edgeIds?.delete(id);
    if (edgeIds && !edgeIds.size) edgeIdsByNodeId.delete(nodeId);
  });
  removeCanvasEdge(id);
}

export function removeNodeFromCanvas(nodeId, connectionIds = []) {
  const id = String(nodeId || "");
  if (!id) return;
  const connected = new Set(connectionIds.length ? connectionIds : edgeIdsByNodeId.get(id));
  connected.forEach((connectionId) => removeConnectionFromCanvas(connectionId));
  edgeIdsByNodeId.delete(id);
  state.nodesById.delete(id);
  removeCanvasNode(id);
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

function elementAttributePanelIsActive() {
  return Boolean(
    state.selectedNodeId && el.attributePanel && !el.attributePanel.classList.contains("hidden"),
  );
}

function _configuredEdgePresentation(edge) {
  if (!isModelingLevel(state.activeType)) {
    return { className: "", markerStart: "", markerEnd: "arrow" };
  }
  return modelingRelationshipPresentation(state.activeType, edge);
}

function _configuredNodeNotation(node) {
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

function _normalizeViewText(value) {
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

function _configuredNodeDetailsHtml(node) {
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
  return configuredNodeSize("detail").width || configuredNodeSize("node").width;
}

function getNodeHeight() {
  return configuredNodeSize("detail").height || configuredNodeSize("node").height;
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

// Switching to a normal view abandons the temporary container-navigation state
// without changing the selected view. The saved focus view itself is retained
// so reopening the container can preserve its layout.
export function clearCanvasFocus() {
  const stack = focusStack();
  const hadFocus = stack.length > 0;
  stack.length = 0;
  return hadFocus;
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

function isGroupingRelationshipForFocus(relationship) {
  if (!relationship) {
    return false;
  }
  if (relationship.containment === true) {
    return true;
  }
  const kind = String(relationship.kind || "").toUpperCase();
  try {
    const kinds = new Set(
      safeArray(modelingLevelConfig(state.activeType).relationshipSemantics?.containmentKinds).map(
        (entry) => String(entry).toUpperCase(),
      ),
    );
    return kinds.has(kind);
  } catch {
    return false;
  }
}

function isContainmentRelationship(relationship) {
  return isGroupingRelationshipForFocus(relationship);
}

function viewNodesByElement(view) {
  return new Map(safeArray(view?.nodes).map((node) => [node.elementId, node]));
}

function viewEdgesByRelationship(view) {
  return new Map(safeArray(view?.edges).map((edge) => [edge.relationshipId, edge]));
}

function viewNodeForElement(view, elementId) {
  return safeArray(view?.nodes).find((entry) => String(entry?.elementId || "") === elementId);
}

function ensureFocusPortalNode(elementId, anchorElementId = "") {
  const view = activeView();
  const element = state.graph?.elementsById?.get(elementId);
  if (!view || !element || viewNodeForElement(view, elementId)) {
    return false;
  }
  const anchor = anchorElementId ? viewNodeForElement(view, anchorElementId) : null;
  view.nodes = safeArray(view.nodes);
  view.nodes.unshift({
    elementId,
    x: Number.isFinite(Number(anchor?.x)) ? Number(anchor.x) - getNodeWidth() - 80 : 32,
    y: Number.isFinite(Number(anchor?.y)) ? Number(anchor.y) : 40,
    portal: true,
  });
  return true;
}

function _nodeForFocusElement(elementId, viewNode) {
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

function _edgeForFocusRelationship(relationship, viewEdge = null) {
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

function focusViewIdFor(elementId, flavor = "container") {
  // A focus canvas is a real saved view. Its identity must be stable across closing,
  // switching views, and browser reloads; timestamps made every reopen a new view.
  return `view-${state.activeType}-focus-${String(flavor || "container").replaceAll(
    /[^A-Za-z0-9_-]+/g,
    "-",
  )}-${String(elementId || "").replaceAll(/[^A-Za-z0-9_-]+/g, "-")}`;
}

function savedFocusViewFor(elementId, scopeKind, depth = undefined) {
  const normalizedElementId = String(elementId || "");
  const normalizedScopeKind = String(scopeKind || "").toUpperCase();
  return [...state.views.byId.values()].find((view) => {
    if (
      String(view?.kind || "").toUpperCase() !== "FOCUS" &&
      !String(view?.id || "").includes("-focus-")
    ) {
      return false;
    }
    if (
      String(view?.scope?.rootElementId || "") !== normalizedElementId ||
      String(view?.scope?.scopeKind || "").toUpperCase() !== normalizedScopeKind
    ) {
      return false;
    }
    return depth === undefined || Number(view?.scope?.depth) === Number(depth);
  });
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
  const containerProfile = modelingContainerDefinition(state.activeType, node.type) || {};
  const configuredCanvas = safeArray(containerProfile.canvas).map(String).filter(Boolean);
  const configuredPalette = safeArray(containerProfile.palette).map(String).filter(Boolean);
  const descendantIds = [...collectContainedDescendantIds(node.id)];
  const descendantSet = new Set(descendantIds);
  const portalIds = new Set();
  const visibleIds = new Set(descendantIds);
  state.graph.relationshipsById.forEach((relationship) => {
    if (!relationship?.sourceElementId || !relationship?.targetElementId) {
      return;
    }
    if (isGroupingRelationshipForFocus(relationship)) {
      return;
    }
    const sourceInside = descendantSet.has(relationship.sourceElementId);
    const targetInside = descendantSet.has(relationship.targetElementId);
    if (sourceInside === targetInside) {
      return;
    }
    const portalId = sourceInside ? relationship.targetElementId : relationship.sourceElementId;
    visibleIds.add(portalId);
    if (!descendantSet.has(portalId)) {
      portalIds.add(portalId);
    }
  });
  const visibleElementIds = [...visibleIds];
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
  state.graph.relationshipsById.forEach((relationship) => {
    if (isGroupingRelationshipForFocus(relationship)) {
      return;
    }
    const sourceId = relationship.sourceElementId || relationship.source;
    const targetId = relationship.targetElementId || relationship.target;
    if (
      sourceId &&
      targetId &&
      visibleIds.has(sourceId) &&
      visibleIds.has(targetId) &&
      !edges.some((edge) => edge.relationshipId === relationship.id)
    ) {
      edges.push({
        relationshipId: relationship.id,
        sourceId,
        targetId,
        visible: true,
      });
    }
  });
  const focusNodes = visibleElementIds.map((elementId) => ({
    elementId,
    portal: portalIds.has(elementId) ? true : undefined,
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
    id: focusViewIdFor(node.id, "container"),
    name: `${node.label || node.id} Contents`,
    level: modelingLevelLabel(state.activeType),
    kind: String(focusPolicy.viewKind || "FOCUS"),
    scope: {
      rootElementId: node.id,
      scopeKind: String(focusPolicy.scopeKind || "CONTAINER"),
      depth: 999,
    },
    filters: {
      elementTypes: configuredCanvas,
      relationshipKinds: safeArray(containerProfile.relationshipKinds),
    },
    palette: configuredPalette,
    canvas: configuredCanvas,
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
    id: focusViewIdFor(node.id, `neighborhood-${normalizedDepth}`),
    name: `${node.label || node.id} Neighborhood ${normalizedDepth}`,
    level: modelingLevelLabel(state.activeType),
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
  const focusPolicy = modelingContainerFocusPolicy(state.activeType);
  const focusViewId = focusViewIdFor(node.id, "container");
  const focusView =
    state.views.byId.get(focusViewId) ||
    savedFocusViewFor(node.id, String(focusPolicy.scopeKind || "CONTAINER")) ||
    createContainerFocusView(node);
  if (!state.views.byId.has(focusViewId)) {
    prepareViewNodeIndex(focusView);
    state.views.byId.set(focusView.id, focusView);
  }
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
  void renderDiagramAsync({ full: true });
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
  const focusViewId = focusViewIdFor(node.id, `neighborhood-${normalizedDepth}`);
  const focusView =
    state.views.byId.get(focusViewId) ||
    savedFocusViewFor(node.id, "NEIGHBORHOOD", normalizedDepth) ||
    createNeighborhoodFocusView(node, normalizedDepth);
  if (!state.views.byId.has(focusViewId)) {
    prepareViewNodeIndex(focusView);
    state.views.byId.set(focusView.id, focusView);
  }
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
  void renderDiagramAsync({ full: true });
  notifyModelToolsChanged();
  setStatus(`Opened ${node.label || node.id} neighborhood depth ${normalizedDepth}.`);
  return true;
}

export function closeCanvasFocus() {
  const focus = activeCanvasFocus();
  if (!focus) {
    clearCanvasFocus();
    return false;
  }
  focusStack().pop();
  state.views.activeViewId =
    focus.previousViewId && state.views.byId.has(focus.previousViewId)
      ? focus.previousViewId
      : state.views.byId.keys().next().value || null;
  materializeActiveView();
  void renderDiagramAsync({ full: true });
  renderPalette();
  notifyModelToolsChanged();
  setStatus("Returned to previous canvas");
  return true;
}

function nodeVisibleInContainerMode(node) {
  return Boolean(node);
}

function nodeVisibleInCurrentCanvasMode(node) {
  return nodeVisibleInContainerMode(node);
}

function notifyModelToolsChanged() {
  window.dispatchEvent(new Event("model-tools-state-change"));
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
  updateCanvasConnectionState();
}

function ensureCanvas() {
  window.varkaEnsureG6Canvas = ensureCanvas;
  const renderer =
    state.modelingConfig.config?.diagramEditor?.renderer ||
    window.varkaFrontendBoot?.renderer ||
    "antv-g6";
  window.varkaG6State = {
    ...(window.varkaG6State || {}),
    ensureCalled: true,
    rendererRequested: renderer,
    activeType: state.activeType,
    stateNodes: Array.isArray(state.diagram?.nodes) ? state.diagram.nodes.length : 0,
    stateEdges: Array.isArray(state.diagram?.connections) ? state.diagram.connections.length : 0,
  };
  setCanvasMountOptions({
    mapper: {
      visibleNode: nodeVisibleInCurrentCanvasMode,
      isContainer: (node) => isContainerElement(node),
      viewProfile: activeConfiguredViewProfile() || activeView()?.viewpoint || "",
    },
    callbacks: {
      onNodeClick: handleG6NodeClick,
      onNodeDoubleClick: handleG6NodeDoubleClick,
      onNodeHover: setHoveredNode,
      onEdgeClick: handleG6EdgeClick,
      onEdgeHover: setHoveredEdge,
      onCanvasClick: handleG6CanvasClick,
      onEscape: handleG6Escape,
      onCanvasPointerDown: closeEdgeKindPicker,
      onCanvasPointerMove: () => {},
      onNodeDragStart: startG6NodeDrag,
      onNodeDrag: moveG6NodeDrag,
      onNodeDragEnd: endG6NodeDrag,
      onConnectionDragStart: startG6ConnectionDrag,
      onConnectionPointerMove: moveG6ConnectionDrag,
      onConnectionDragEnd: () => {
        state.linkDrag = null;
        updateCanvasConnectionState();
      },
      onConnectionComplete: (sourceId, targetId) =>
        addConnection(sourceId, targetId, {
          interactivePicker: true,
          preferredKind: state.preferredConnectionKind,
        }),
      onConnectionCancel: () => {
        state.linkDrag = null;
        updateCanvasConnectionState();
        setStatus("Connection canceled");
      },
      connectionTargetState: g6ConnectionTargetState,
      connectionTargetTypes: g6ConnectionTargetTypes,
      onOpenContainer: openG6ContainerTool,
      onViewportChange: () => {
        updateZoomControlLabel();
        onCanvasViewportChanged();
      },
    },
  });
  return mountActiveCanvas();
}

export async function initializeModelingRenderer() {
  const mountedOrUnavailable = await ensureCanvas();
  syncCanvasIndexesFromState();
  if (mountedOrUnavailable) {
    renderCanvasDiagram();
  }
  return mountedOrUnavailable;
}

export function getModelingRendererDebug() {
  const editor = getCanvasEditor();
  const host = el.g6EditorHost;
  const hostRect = host?.getBoundingClientRect?.();
  const canvasGrid = el.canvasGrid;
  return {
    renderer: canvasGrid?.dataset?.renderer || "",
    ensureCalled: Boolean(window.varkaG6State?.ensureCalled),
    g6Available: isCanvasRendererAvailable(),
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
    lastError: window.varkaG6State?.lastError || "",
    g6State: window.varkaG6State || null,
  };
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
  node.meta.name = resolved;
  if (node.meta.label !== undefined) {
    node.meta.label = resolved;
  }
  if (state.selectedNodeId === node.id && el.attrPanelTitle) {
    el.attrPanelTitle.textContent = resolved;
  }
  return resolved;
}

function setNodeMultiSelection(ids) {
  state.selectedNodeIds = new Set(ids);
}

function deselectEdges() {
  state.selectedConnectionId = null;
  ensureCanvas();
  updateCanvasSelection();
}

function applyNodeSelectionStyles() {
  ensureCanvas();
  updateCanvasSelection();
  updateCanvasImpactState();
}

function applyHoverFocusStyles() {
  setCanvasHoverNode(state.hoveredNodeId);
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
    updateCanvasConnectionState();
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

// ── Viewport helpers ──────────────────────────────────────────────────────────

export function toCanvasCoordinates(clientX, clientY) {
  ensureCanvas();
  return toGraphCoordinates(clientX, clientY);
}

let viewportUpdateScheduled = false;

function updateZoomControlLabel() {
  if (el.canvasZoomValue) {
    el.canvasZoomValue.textContent = `${Math.round((state.viewport.scale || 1) * 100)}%`;
  }
}

export function applyViewport() {
  ensureCanvas();
  updateCanvasViewport();
  updateZoomControlLabel();
  el.canvasGrid?.style.setProperty("--viewport-scale", String(state.viewport.scale || 1));
  el.canvasGrid?.classList.toggle("lod-low", state.viewport.scale < 0.35);
  el.canvasGrid?.classList.toggle(
    "lod-medium",
    state.viewport.scale >= 0.35 && state.viewport.scale < 0.75,
  );
  el.canvasGrid?.classList.toggle("lod-high", state.viewport.scale >= 1.5);
  // Defer cursor rendering to batch with other updates, don't render on every pan
  if (!viewportUpdateScheduled) {
    viewportUpdateScheduled = true;
    window.requestAnimationFrame(() => {
      viewportUpdateScheduled = false;
    });
  }
}

export function resetCanvasView() {
  ensureCanvas();
  adapterResetCanvasView();
}

export function zoomCanvasBy(multiplier = 1) {
  ensureCanvas();
  adapterZoomCanvasBy(multiplier);
}

function nodeExtent(node, defaultW, defaultH) {
  const x = Number(node?.x) || 0;
  const y = Number(node?.y) || 0;
  const w = Number.isFinite(Number(node?.width)) ? Number(node.width) : defaultW;
  const h = Number.isFinite(Number(node?.height)) ? Number(node.height) : defaultH;
  return { minX: x, minY: y, maxX: x + w, maxY: y + h };
}

function diagramBounds({ forFit = false } = {}) {
  if (!state.diagram?.nodes?.length) {
    return null;
  }
  if (forFit) {
    // Fit is anchored to visible nodes; stale edge bend points can be far outside the active view.
  }
  const nodeW = getNodeWidth();
  const nodeH = getNodeHeight();
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  state.diagram.nodes.forEach((node) => {
    const extent = nodeExtent(node, nodeW, nodeH);
    minX = Math.min(minX, extent.minX);
    minY = Math.min(minY, extent.minY);
    maxX = Math.max(maxX, extent.maxX);
    maxY = Math.max(maxY, extent.maxY);
  });
  const bounds = {
    minX,
    minY,
    maxX,
    maxY,
    width: Math.max(1, maxX - minX),
    height: Math.max(1, maxY - minY),
  };
  window.varkaLastDiagramFitBounds = {
    activeType: state.activeType,
    nodeCount: state.diagram.nodes.length,
    edgeCount: state.diagram.connections.length,
    bounds,
    nodes: state.diagram.nodes.map((node) => ({
      id: node.id,
      type: node.type,
      x: Number(node?.x) || 0,
      y: Number(node?.y) || 0,
    })),
  };
  return bounds;
}

function applyViewportFit({ fit = false } = {}) {
  if (!isModelingLevel(state.activeType)) {
    return false;
  }
  const bounds = diagramBounds({ forFit: fit });
  if (!bounds) {
    return false;
  }
  ensureCanvas();
  const fitArea = getCanvasFitArea();
  return fitCanvasToDiagram(bounds, { fit, fitArea }) !== false;
}

async function waitForViewportPaint(frames = 2) {
  if (typeof window === "undefined" || typeof window.requestAnimationFrame !== "function") {
    return;
  }
  for (let index = 0; index < frames; index += 1) {
    await new Promise((resolve) => window.requestAnimationFrame(resolve));
  }
}

export function centerViewportOnDiagram({ fit = false } = {}) {
  applyViewportFit({ fit });
}

export async function fitViewportToDiagram({ fit = true, frames = 2, retry = true } = {}) {
  if (!isModelingLevel(state.activeType)) {
    return false;
  }
  if (!Array.isArray(state.diagram?.nodes) || !state.diagram.nodes.length) {
    resetCanvasView();
    return false;
  }
  try {
    await renderDiagramAsync();
  } catch (error) {
    console.warn("fitViewportToDiagram: render failed", error);
  }
  await waitForViewportPaint(frames);
  if (!isModelingLevel(state.activeType)) {
    return false;
  }
  let applied = applyViewportFit({ fit });
  if (!applied && retry) {
    await waitForViewportPaint(1);
    if (!isModelingLevel(state.activeType)) {
      return false;
    }
    applied = applyViewportFit({ fit });
  }
  return applied;
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

function setIconSource(icon, src) {
  const resolved = resolveIconSource(src);
  const fullColor = isFullColorIconSource(src) || isFullColorIconSource(resolved);
  icon.classList.toggle("icon-mask", !fullColor);
  icon.classList.toggle("icon-image", fullColor);
  icon.style.setProperty("--icon-src", `url('${resolved}')`);
}

function createMaskIcon(className, src, { ariaHidden = true } = {}) {
  const icon = document.createElement("span");
  icon.className = `${className} icon-svg`;
  if (ariaHidden) {
    icon.setAttribute("aria-hidden", "true");
  }
  setIconSource(icon, src);
  return icon;
}

function _setMaskIconSource(icon, src) {
  if (!icon) {
    return;
  }
  const normalized = String(src || "").trim();
  setIconSource(icon, normalized);
}

function definitionUi(definition) {
  const ui = definition?.ui && typeof definition.ui === "object" ? definition.ui : {};
  return {
    icon: ui.icon || definition?.icon,
    color: ui.color || definition?.color,
  };
}

export function applyDefinitionAccent(element, definition) {
  if (!element) {
    return;
  }
  const color = resolveThemeColor(definitionUi(definition).color, "");
  if (color) {
    element.style.setProperty("--node-accent", color);
    const icon = element.querySelector(".palette-item-icon");
    if (icon) {
      icon.style.color = color;
    }
  } else {
    element.style.removeProperty("--node-accent");
    const icon = element.querySelector(".palette-item-icon");
    if (icon) {
      icon.style.removeProperty("color");
    }
  }
}

function createPaletteNotationIcon(definition) {
  const configuredIcon = String(definitionUi(definition).icon || "").trim();
  if (!configuredIcon) {
    return null;
  }
  const icon = createMaskIcon("palette-item-icon", configuredIcon);
  const color = resolveThemeColor(definitionUi(definition).color, "");
  if (color) {
    icon.style.color = color;
  }
  return icon;
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

function _createConfiguredCompanions(node) {
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
  ensureCanvas();
  syncCanvasIndexesFromState();
  syncCanvasFromState({ full: false });
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
    togglePaletteGroupDom(group, title, groupName);
  });
  group.appendChild(title);

  buttons.forEach((button) => items.appendChild(button));
  group.appendChild(items);
  el.palette.appendChild(group);
  return true;
}

function scopedViewContainmentOwnerType() {
  const view = activeView();
  const rootId = String(view?.scope?.rootElementId || "").trim();
  if (!rootId) {
    return null;
  }
  const element = state.graph.elementsById.get(rootId);
  if (!element || !isContainerElement(element)) {
    return null;
  }
  return element.eClass || element.type || null;
}

function isEntryTypeViewDefinition(definition) {
  return Boolean(definition) && safeArray(definition.scopeTypes).length > 0;
}

function entryTypeViewPaletteTypes(definition) {
  if (!isEntryTypeViewDefinition(definition)) {
    return null;
  }
  const configured = safeArray(definition.palette).map(String).filter(Boolean);
  if (configured.length) {
    return configured;
  }
  return [];
}

function paletteTypesForActiveView() {
  const containerFocus = activeContainerFocus();
  if (containerFocus) {
    return modelingCanvasPaletteTypes(state.activeType, {
      ownerType: containerFocus.elementType,
    }).filter((type) => type !== modelingRootType(state.activeType));
  }
  if (!isModelingLevel(state.activeType)) {
    const config = MODEL_TYPES[state.activeType];
    return Array.isArray(config?.palette) ? config.palette : [];
  }
  const definition = modelingViewDefinition(state.activeType, activeView());
  const entryPalette = entryTypeViewPaletteTypes(definition);
  if (entryPalette !== null) {
    return entryPalette;
  }
  return availableConfiguredPaletteTypes(
    modelingCanvasPaletteTypes(state.activeType, { ownerType: null }),
  ).filter((type) => type !== modelingRootType(state.activeType));
}

function paletteEmptyHint() {
  const containerFocus = activeContainerFocus();
  if (containerFocus) {
    return `No containable elements configured for ${containerFocus.label || containerFocus.elementType}`;
  }
  if (!isModelingLevel(state.activeType)) {
    return "No matching elements";
  }
  try {
    const definition = modelingViewDefinition(state.activeType, activeView());
    if (isEntryTypeViewDefinition(definition) && !safeArray(definition.palette).length) {
      const entryType = String(definition.scopeTypes[0] || "").trim();
      const entryDefinition = entryType
        ? modelingElementDefinition(state.activeType, entryType)
        : null;
      const entryLabel = entryDefinition?.displayName || entryType || "container";
      const entryPlural = entryLabel.endsWith("s") ? entryLabel : `${entryLabel}s`;
      return `Open a ${entryLabel} to add its inner elements. Create ${entryPlural} from Service Landscape.`;
    }
  } catch {
    // Fall through to generic hint.
  }
  return "No backend palette available";
}

function activeViewElementTypeFilter() {
  return new Set((activeView()?.filters?.elementTypes || []).map(String));
}

function isActionableScopedPaletteType(type, creatableTypes) {
  return type !== modelingRootType(state.activeType) && creatableTypes.has(type);
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
  const scopeOwnerType = scopedViewContainmentOwnerType();
  if (scopeOwnerType) {
    return modelingCanvasPaletteTypes(state.activeType, { ownerType: scopeOwnerType });
  }
  const definition = modelingViewDefinition(state.activeType, view);
  const entryPalette = entryTypeViewPaletteTypes(definition);
  if (entryPalette !== null) {
    return entryPalette;
  }
  const configuredPalette = Array.isArray(view.palette) ? view.palette : null;
  const hasExplicitPalette = configuredPalette !== null;
  const scoped = hasExplicitPalette ? [...configuredPalette] : [...activeViewElementTypeFilter()];
  const filtered = filterScopedPaletteTypes(state.activeType, scoped, allTypes).filter((type) =>
    modelingStandalonePaletteType(state.activeType, type),
  );
  return scoped.length ? filtered : allTypes;
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

function togglePaletteGroupDom(group, title, groupName) {
  const collapsed = isPaletteGroupCollapsed(groupName);
  group.classList.toggle("palette-group-collapsed", collapsed);
  title?.setAttribute("aria-expanded", String(!collapsed));
}

function syncAllPaletteGroupsDom() {
  if (!el.palette) {
    return;
  }
  el.palette.querySelectorAll(".palette-group").forEach((group) => {
    const groupName = group.querySelector(".palette-group-name")?.textContent?.trim();
    if (!groupName) {
      return;
    }
    const title = group.querySelector(".palette-group-title");
    togglePaletteGroupDom(group, title, groupName);
  });
}

function setAllPaletteGroupsCollapsed(groupNames, collapsed) {
  groupNames.forEach((groupName) => {
    if (groupName) {
      setPaletteGroupCollapsed(groupName, collapsed);
    }
  });
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
  const query = ((state.paletteSearch[state.activeType] || "") + "").trim().toLowerCase();
  let actionableTypes = [];
  try {
    actionableTypes = paletteTypesForActiveView();
  } catch (error) {
    console.error("Palette rendering failed", error);
    setStatus(error, { prefix: "Backend modeling config is unavailable.", error: true });
    actionableTypes = [];
  }
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
  if (namedGroups.filter(Boolean).length >= 1) {
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
      syncAllPaletteGroupsDom();
    });
    const collapseBtn = document.createElement("button");
    collapseBtn.type = "button";
    collapseBtn.className = "palette-group-toolbar-btn";
    collapseBtn.textContent = "Collapse All";
    collapseBtn.addEventListener("click", () => {
      setAllPaletteGroupsCollapsed(namedGroups, true);
      syncAllPaletteGroupsDom();
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
        togglePaletteGroupDom(group, title, groupName);
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
      const focusTypes = guidedPaletteFocusTypes();
      if (focusTypes.size && focusTypes.has(type)) {
        item.classList.add("palette-item-task-focus");
      }
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
          "application/x-varka-palette-item",
          JSON.stringify({ level: state.activeType, type }),
        );
        event.dataTransfer.setData("application/x-varka-node-type", type);
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
    empty.textContent = paletteEmptyHint();
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
  ensureCanvas();
  syncCanvasIndexesFromState();
  syncCanvasFromState({ full: false });
  updateCanvasSelection();
  updateCanvasImpactState();
  return;
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
  ensureCanvas();
  setCanvasHoverEdge(hoveredEdgeId);
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
  const undoSnapshot = captureConnectionUndoSnapshot(edge);
  edge.sourceId = resolved.sourceId;
  edge.targetId = resolved.targetId;
  edge.kind = resolved.kind;
  addConnectionToGraphAndActiveView(edge);
  commitUndoSnapshot(undoSnapshot);
  ensureCanvas();
  updateCanvasEdge(edgeId);
  updateCanvasSelection();
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
  return [...new Set(legalKinds(state.activeType, sourceType, targetType))];
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
  ensureCanvas();
  if (
    state.selectedConnectionId &&
    !state.diagram.connections.some((edge) => edge.id === state.selectedConnectionId)
  ) {
    state.selectedConnectionId = null;
  }
  syncCanvasIndexesFromState();
  syncCanvasFromState({ full: false });
  updateCanvasSelection();
}

function _hasModelArtifact(keys) {
  const root = state.baseModel && typeof state.baseModel === "object" ? state.baseModel : {};
  return keys.some((key) => {
    const value = root[key];
    return Array.isArray(value) ? value.length > 0 : Boolean(value);
  });
}

let renderDiagramTask = Promise.resolve();

export function renderDiagram() {
  renderDiagramTask = renderDiagramTask
    .then(() => renderDiagramNow())
    .catch((error) => {
      console.error("renderDiagram failed", error);
    });
}

export function renderDiagramAsync(options = {}) {
  return renderDiagramTask.then(() => renderDiagramNow(options));
}

async function renderDiagramNow({ full = false } = {}) {
  if (!isModelingLevel(state.activeType)) {
    return;
  }
  try {
    await ensureCanvas();
  } catch (error) {
    console.warn("ensureCanvas failed", error);
    return;
  }
  if (!isModelingLevel(state.activeType)) {
    return;
  }
  syncCanvasIndexesFromState();
  if (full) {
    await syncCanvasFromState({ full: true });
  } else {
    await renderCanvasDiagram();
  }
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
  if (!isModelingLevel(state.activeType)) {
    return;
  }
  materializeActiveView();
  ensureCanvas();
  syncCanvasIndexesFromState();
  syncCanvasFromState({ full });
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
  ensureCanvas();
  updateCanvasSelection();
  updateCanvasContextBoxes(null, { useCache: true });
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
  if (state.issueLocateTargetId === nodeId) {
    state.issueLocateTargetId = null;
    updateCanvasImpactState();
  }
  const modifiers = eventModifierState(event);
  if (modifiers.shiftKey || modifiers.ctrlKey || modifiers.metaKey) {
    toggleNodeInSelection(nodeId);
    return;
  }
  setNodeMultiSelection([nodeId]);
  activateNode(nodeId);
  updateCanvasSelection();
}

function handleG6EdgeClick(edgeId) {
  selectConnection(edgeId, { openPicker: true });
  if (state.issueLocateTargetId === edgeId) {
    state.issueLocateTargetId = null;
    updateCanvasImpactState();
  }
}

function handleG6NodeDoubleClick(nodeId, event = {}) {
  const node = state.nodesById.get(nodeId);
  if (!node) {
    return;
  }
  event?.preventDefault?.();
  if (isContainerElement(node)) {
    openContainerFocus(nodeId);
    return;
  }
  const undoSnapshot = captureDiagramUndoSnapshot();
  const startLabel = node.label;
  beginCanvasInlineLabelEdit(node, {
    onCommit: (rawText) => {
      const resolved = commitNodeLabel(node, rawText);
      syncNodeMetaToGraph(node);
      updateCanvasNode(node.id);
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
  if (cancelConnectionDraw({ silent: true })) {
    setStatus("Draw mode canceled");
    return;
  }
  if (state.issueLocateTargetId) {
    state.issueLocateTargetId = null;
    updateCanvasImpactState();
  }
  closeAttributePanel();
  state.selectedNodeId = null;
  state.selectedNodeIds = new Set();
  state.selectedConnectionId = null;
  updateCanvasSelection();
}

function handleG6Escape() {
  clearHoveredEdge();
  closeEdgeKindPicker();
  if (cancelConnectionDraw()) {
    return;
  }
  closeAttributePanel();
  state.selectedNodeId = null;
  state.selectedNodeIds = new Set();
  state.selectedConnectionId = null;
  updateCanvasSelection();
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
    const view = activeView();
    // A deliberate drag establishes this view's layout, so initial-load auto-layout must not
    // replace the user's saved arrangement later.
    if (view) {
      view.autoLayoutApplied = true;
      view.layoutGeometryVersion = CURRENT_LAYOUT_GEOMETRY_VERSION;
    }
    commitUndoSnapshot(state.dragNode?.undoSnapshot);
    persistNodePositionInActiveView(node);
    markModelDirty({
      viewSynced: true,
      kind: "positions",
      position: { elementId: node.id, viewId: activeView()?.id, x: node.x, y: node.y },
    });
  }
  refreshCanvasEdges([...(edgeIdsByNodeId.get(node.id) || [])]);
  updateCanvasContextBoxes();
  updateCanvasSelection();
  state.dragNode = null;
}

function startG6ConnectionDrag(sourceId) {
  state.linkDrag = { sourceId, pointerX: 0, pointerY: 0, hoveredTargetId: null };
  updateCanvasConnectionState();
  setStatus("Drag to another element to create a legal connection");
}

function openG6ContainerTool(nodeId) {
  openContainerFocus(nodeId);
}

function selectConnection(connectionId, { openPicker = false } = {}) {
  if (!connectionId) {
    return;
  }
  if (state.selectedConnectionId === connectionId) {
    closeAttributePanel();
    ensureCanvas();
    state.selectedConnectionId = null;
    updateCanvasSelection();
    return;
  }
  openConnectionPanel(connectionId);
  ensureCanvas();
  updateCanvasSelection();
  updateCanvasEdge(connectionId);
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
      ensureCanvas();
      updateCanvasConnectionState();
      return;
    }
    if (state.connectSourceId === nodeId) {
      setStatus("Source and target cannot be the same");
      return;
    }
    const drawSourceId = state.connectSourceId;
    const drawKind = state.preferredConnectionKind;
    addConnection(state.connectSourceId, nodeId, {
      interactivePicker: true,
      preferredKind: state.preferredConnectionKind,
    });
    if (state.connectMode && drawKind) {
      state.connectSourceId = drawSourceId;
      const kindLabel = modelingRelationshipKindLabel(state.activeType, drawKind);
      setStatus(`Drawing ${kindLabel} — select another highlighted target or cancel`);
    } else {
      state.connectSourceId = null;
    }
    ensureCanvas();
    updateCanvasConnectionState();
    notifyConnectionDrawStateChange();
    syncConnectionDrawChrome();
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

function diagramEdgeFromRelationship(relationship, viewEdge = null) {
  if (!relationship) {
    return null;
  }
  return {
    id: relationship.id,
    sourceId: relationship.sourceElementId,
    targetId: relationship.targetElementId,
    kind: relationship.kind,
    pinPoints: Array.isArray(viewEdge?.pinPoints)
      ? viewEdge.pinPoints.map((point) => ({ ...point }))
      : [],
    sourceAnchor: viewEdge?.sourceAnchor ? { ...viewEdge.sourceAnchor } : undefined,
    targetAnchor: viewEdge?.targetAnchor ? { ...viewEdge.targetAnchor } : undefined,
  };
}

function registerDiagramEdge(edge) {
  connectionsById.set(edge.id, edge);
  [edge.sourceId, edge.targetId].forEach((nodeId) => {
    if (!edgeIdsByNodeId.has(nodeId)) {
      edgeIdsByNodeId.set(nodeId, new Set());
    }
    edgeIdsByNodeId.get(nodeId).add(edge.id);
  });
}

function paletteDroppedNodeIsVisible(node) {
  const ownerId = String(node?.meta?.__ownerId || "").trim();
  if (!ownerId) {
    return true;
  }
  const view = activeView();
  return (
    String(view?.scope?.scopeKind || "").toUpperCase() === "CONTAINER" &&
    String(view?.scope?.rootElementId || "") === ownerId
  );
}

function diagramNodeIsOnCanvas(nodeId) {
  const normalized = String(nodeId || "").trim();
  if (!normalized) {
    return false;
  }
  return state.diagram.nodes.some((candidate) => candidate.id === normalized);
}

function syncPaletteDropContainmentEdges(node) {
  const incomingRelationshipIds = state.graph.relationshipsByTarget.get(node.id);
  if (!incomingRelationshipIds?.size) {
    return;
  }
  const view = activeView();
  const viewEdgesById = new Map((view?.edges || []).map((edge) => [edge.relationshipId, edge]));
  const knownConnectionIds = new Set(state.diagram.connections.map((edge) => edge.id));
  incomingRelationshipIds.forEach((relationshipId) => {
    const relationship = state.graph.relationshipsById.get(relationshipId);
    if (!relationship || !isContainmentRelationship(relationship)) {
      return;
    }
    if (knownConnectionIds.has(relationshipId)) {
      return;
    }
    const edge = diagramEdgeFromRelationship(relationship, viewEdgesById.get(relationshipId));
    if (!edge) {
      return;
    }
    if (!diagramNodeIsOnCanvas(edge.sourceId) || !diagramNodeIsOnCanvas(edge.targetId)) {
      return;
    }
    state.diagram.connections.push(edge);
    registerDiagramEdge(edge);
    addCanvasEdge(edge);
    knownConnectionIds.add(relationshipId);
  });
}

function applyPaletteDropToCanvas(node) {
  if (!paletteDroppedNodeIsVisible(node)) {
    return false;
  }
  if (!state.diagram.nodes.some((candidate) => candidate.id === node.id)) {
    state.diagram.nodes.push(node);
  }
  state.nodesById.set(node.id, node);
  addCanvasNode(node);
  syncPaletteDropContainmentEdges(node);
  const ownerId = String(node.meta?.__ownerId || "").trim();
  if (ownerId && state.nodesById.has(ownerId)) {
    updateCanvasNode(ownerId);
  }
  if (state.tabs[state.activeType]) {
    state.tabs[state.activeType].diagram = state.diagram;
  }
  updateCanvasContextBoxes(null, { useCache: true });
  return true;
}

// ── Drag-and-drop from palette ────────────────────────────────────────────────

export function setupDnD() {
  ensureCanvas();
  renderCanvasDiagram();
  el.canvasViewport.addEventListener("dragover", (e) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = "copy";
  });
  el.canvasViewport.addEventListener("drop", (e) => {
    e.preventDefault();
    let paletteItem = null;
    try {
      paletteItem = JSON.parse(e.dataTransfer.getData("application/x-varka-palette-item"));
    } catch {
      paletteItem = null;
    }
    const type = String(
      paletteItem?.type ||
        e.dataTransfer.getData("application/x-varka-node-type") ||
        e.dataTransfer.getData("text/node-type") ||
        state.paletteDragType ||
        "",
    ).trim();
    state.paletteDragType = "";
    const containerFocus = activeContainerFocus();
    const allowedTypes = new Set(paletteTypesForActiveView());
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
    const pos = toCanvasCoordinates(e.clientX, e.clientY);
    const node = getDefaultNode(state.activeType, type, Math.round(pos.x), Math.round(pos.y));
    requestAnimationFrame(() => {
      pushDiagramUndoSnapshot(captureAddElementUndoSnapshot(node.id));
      assignNodeToSemanticContainer(node);
      addNodeToGraphAndActiveView(node);
      applyPaletteDropToCanvas(node);
      const created = state.graph.elementsById.get(node.id);
      setStatus(`Added ${created?.eClass || type}`);
      markModelDirty();
    });
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

function tryAssignNodeToContainerOwner(node, owner) {
  if (!owner) {
    return false;
  }
  const containment = modelingContainmentsForType(
    state.activeType,
    owner.eClass || owner.type,
  ).find((entry) => !entry.relationshipOnly && containmentAcceptsType(entry, node.type));
  if (!containment || (containment.many === false && owner[containment.feature])) {
    return false;
  }
  node.meta.__ownerId = owner.id;
  node.meta.__containmentFeature = containment.feature;
  addReferenceValue(owner, containment.feature, node.id, containment.many !== false);
  const ownerNode = state.nodesById.get(owner.id);
  if (ownerNode?.meta) {
    ownerNode.meta[containment.feature] = owner[containment.feature];
  }
  return true;
}

function assignEntryTypeViewOwner(node) {
  if (!node?.type || !isModelingLevel(state.activeType)) {
    return false;
  }
  let definition;
  try {
    definition = modelingViewDefinition(state.activeType, activeView());
  } catch {
    return false;
  }
  const scopeTypes = new Set(safeArray(definition?.scopeTypes).map(String));
  if (!scopeTypes.has(node.type)) {
    return false;
  }
  const ownerSpecs = containingFeaturesForChild(node.type);
  if (!ownerSpecs.length) {
    return false;
  }
  const ownerTypes = [...new Set(ownerSpecs.map((entry) => entry.ownerType))];
  const candidates = [...state.graph.elementsById.values()].filter((element) =>
    ownerTypes.some((ownerType) =>
      modelTypeMatches(state.activeType, ownerType, element.eClass || element.type),
    ),
  );
  if (!candidates.length) {
    setStatus(`Create a ${ownerTypes[0]} first (Service Landscape view).`, { error: true });
    return false;
  }
  const selectedOwner =
    candidates.find((candidate) => candidate.id === state.selectedNodeId) ||
    (candidates.length === 1 ? candidates[0] : null);
  if (!selectedOwner) {
    setStatus(`Select a parent ${ownerTypes[0]} before adding ${node.type}.`, { error: true });
    return false;
  }
  const containment =
    ownerSpecs.find((entry) =>
      modelTypeMatches(
        state.activeType,
        entry.ownerType,
        selectedOwner.eClass || selectedOwner.type,
      ),
    ) || ownerSpecs[0];
  return Boolean(attachNestedNode(node, containment));
}

function assignNodeToSemanticContainer(node) {
  if (assignNodeToFocusedContainer(node)) {
    return true;
  }
  if (assignEntryTypeViewOwner(node)) {
    return true;
  }
  if (
    modelingRootContainments(state.activeType).some((entry) =>
      containmentAcceptsType(entry, node.type),
    )
  ) {
    return false;
  }
  const size = nodeSizeForDiagram(state.activeType, node);
  const centerX = node.x + size.width / 2;
  const centerY = node.y + size.height / 2;
  const editor = getCanvasEditor();
  const candidateId = editor?.spatialIndex?.findAt?.(centerX, centerY, { excludeId: "" });
  if (candidateId) {
    const owner = state.graph.elementsById.get(candidateId);
    if (tryAssignNodeToContainerOwner(node, owner)) {
      return true;
    }
  }
  if (state.selectedNodeId) {
    const owner = state.graph.elementsById.get(state.selectedNodeId);
    if (tryAssignNodeToContainerOwner(node, owner)) {
      return true;
    }
  }
  return false;
}

// ── Connection management ─────────────────────────────────────────────────────

function directConnectionCandidate(source, target) {
  return {
    id: "container",
    mode: "container",
    label: target.label || target.id,
    type: target.type,
    kinds: legalKindsBetween(state.activeType, source.type, target.type),
  };
}

function containedTargetCandidates(source, container) {
  if (!isModelingLevel(state.activeType) || !isContainerElement(container)) {
    return [];
  }
  return [...collectContainedDescendantIds(container.id)]
    .map((elementId) => {
      const element = state.graph?.elementsById?.get(elementId);
      if (!element) {
        return null;
      }
      const type = elementType(element);
      const kinds = legalKindsBetween(state.activeType, source.type, type);
      if (!kinds.length) {
        return null;
      }
      return {
        id: elementId,
        mode: "contained",
        label: elementLabel(element),
        type,
        kinds,
      };
    })
    .filter(Boolean)
    .sort(
      (left, right) => left.type.localeCompare(right.type) || left.label.localeCompare(right.label),
    );
}

function buildContainerTargetChoices(source, container) {
  const direct = directConnectionCandidate(source, container);
  const contained = containedTargetCandidates(source, container);
  return [...(direct.kinds.length ? [direct] : []), ...contained];
}

function closeContainerTargetPicker() {
  const picker = document.querySelector(".container-target-picker");
  picker?.remove();
}

function positionFloatingPicker(picker, source, container) {
  const nodeW = getNodeWidth();
  const nodeH = getNodeHeight();
  const midX = (source.x + nodeW / 2 + container.x + nodeW / 2) / 2;
  const midY = (source.y + nodeH / 2 + container.y + nodeH / 2) / 2;
  const viewportPoint = canvasToViewportPoint(midX, midY);
  const viewportRect = el.canvasViewport?.getBoundingClientRect?.();
  const margin = 10;
  const viewportWidth = viewportRect?.width || window.innerWidth;
  const viewportHeight = viewportRect?.height || window.innerHeight;
  const width = picker.offsetWidth || 280;
  const height = picker.offsetHeight || 220;
  picker.style.left = `${Math.round(
    Math.min(Math.max(viewportPoint.x - width / 2, margin), viewportWidth - width - margin),
  )}px`;
  picker.style.top = `${Math.round(
    Math.min(Math.max(viewportPoint.y - 24, margin), viewportHeight - height - margin),
  )}px`;
}

function renderContainerTargetPicker(source, container, choices, preferredKind) {
  closeContainerTargetPicker();
  const picker = document.createElement("div");
  picker.className = "container-target-picker";
  picker.setAttribute("role", "dialog");
  picker.setAttribute("aria-label", "Choose container relationship target");
  const directChoice = choices.find((choice) => choice.mode === "container");
  const containedChoices = choices.filter((choice) => choice.mode === "contained");
  picker.innerHTML = `
    <div class="container-target-picker-head">
      <span>Connect To</span>
      <button class="container-target-picker-close" type="button" aria-label="Close">&times;</button>
    </div>
    <div class="container-target-picker-body">
      ${
        directChoice
          ? `<button class="container-target-option is-container" type="button" data-target-mode="container">
              <span class="container-target-option-label">${escapeHtml(directChoice.label)}</span>
              <span class="container-target-option-meta">${escapeHtml(directChoice.type)}</span>
            </button>`
          : ""
      }
      ${
        containedChoices.length
          ? `<div class="container-target-group-label">Inside ${escapeHtml(
              container.label || container.type,
            )}</div>${containedChoices
              .map(
                (choice) => `<button class="container-target-option" type="button"
                    data-target-mode="contained"
                    data-contained-target-id="${escapeHtml(choice.id)}">
                  <span class="container-target-option-label">${escapeHtml(choice.label)}</span>
                  <span class="container-target-option-meta">${escapeHtml(choice.type)}</span>
                </button>`,
              )
              .join("")}`
          : ""
      }
    </div>`;
  el.canvasViewport?.appendChild(picker);
  requestAnimationFrame(() => positionFloatingPicker(picker, source, container));

  const close = () => closeContainerTargetPicker();
  picker.querySelector(".container-target-picker-close")?.addEventListener("click", close);
  picker.querySelectorAll("[data-target-mode]").forEach((button) => {
    button.addEventListener("click", () => {
      const mode = button.getAttribute("data-target-mode");
      closeContainerTargetPicker();
      if (mode === "container") {
        addConnection(source.id, container.id, {
          interactivePicker: true,
          preferredKind,
          allowContainedTargetPrompt: false,
        });
        return;
      }
      const targetId = button.getAttribute("data-contained-target-id");
      void connectToContainedTarget(source, container, targetId, preferredKind).catch((error) => {
        console.error("Unable to connect to contained target", error);
        setStatus("Unable to create the connection to the contained target");
      });
    });
  });
  const outsideClick = (event) => {
    const target = event.target instanceof Element ? event.target : null;
    if (!target || target.closest(".container-target-picker")) {
      return;
    }
    closeContainerTargetPicker();
    document.removeEventListener("mousedown", outsideClick);
  };
  setTimeout(() => document.addEventListener("mousedown", outsideClick), 0);
}

function maybeOpenContainerTargetPicker(source, target, preferredKind) {
  const choices = buildContainerTargetChoices(source, target);
  const hasContainedTargets = choices.some((choice) => choice.mode === "contained");
  if (!hasContainedTargets) {
    return false;
  }
  renderContainerTargetPicker(source, target, choices, preferredKind);
  setStatus(`Choose whether to connect to ${target.label || target.type} or one of its contents.`);
  return true;
}

function summaryEdgeId(sourceId, containerId, targetId, kind) {
  return `${INTERNAL_TARGET_SUMMARY_PREFIX}-${sourceId}-${containerId}-${targetId}-${kind}`.replaceAll(
    /[^A-Za-z0-9_-]+/g,
    "-",
  );
}

function ensureInternalTargetSummaryEdge(source, container, containedTarget, kind) {
  const id = summaryEdgeId(source.id, container.id, containedTarget.id, kind);
  if (!state.graph.relationshipsById.has(id)) {
    state.graph.relationshipsById.set(id, {
      id,
      kind,
      source: source.id,
      target: container.id,
      sourceElementId: source.id,
      targetElementId: container.id,
      sourceType: source.type,
      targetType: container.type,
      label: `${modelingRelationshipKindLabel(state.activeType, kind)} inside`,
      visualOnly: true,
      internalTargetElementId: containedTarget.id,
      internalTargetType: containedTarget.type,
    });
  }
  const previousViewId = focusStack()[focusStack().length - 1]?.previousViewId;
  const previousView = previousViewId ? state.views.byId.get(previousViewId) : null;
  if (previousView && !safeArray(previousView.edges).some((edge) => edge.relationshipId === id)) {
    previousView.edges = [
      ...safeArray(previousView.edges),
      { relationshipId: id, sourceId: source.id, targetId: container.id, visible: true },
    ];
  }
}

async function connectToContainedTarget(source, container, targetId, preferredKind = null) {
  const targetElement = state.graph?.elementsById?.get(targetId);
  if (!targetElement) {
    setStatus("Contained target is no longer available");
    return false;
  }
  const containedTarget = {
    id: targetElement.id,
    type: elementType(targetElement),
    label: elementLabel(targetElement),
  };
  const kinds = legalKindsBetween(state.activeType, source.type, containedTarget.type);
  const kind = kinds.includes(preferredKind) ? preferredKind : kinds[0];
  if (!kind) {
    setStatus("No legal relationship exists for that contained target");
    return false;
  }
  const opened = openContainerFocus(container.id);
  if (!opened) {
    return false;
  }
  if (ensureFocusPortalNode(source.id, containedTarget.id)) {
    materializeActiveView();
  }
  // The focus transition rebuilds the canvas asynchronously. Wait until the
  // portal source and contained target have been mounted before adding an edge;
  // otherwise the incremental renderer discards the edge because its endpoints
  // do not yet exist in the new canvas.
  await renderDiagramAsync({ full: true });
  const created = addConnection(source.id, containedTarget.id, {
    // Selecting a contained target is the final choice in the container picker.
    // Do not reopen the relationship-kind picker after the focus view has opened:
    // the resolved kind is already the one selected for this target.
    interactivePicker: false,
    preferredKind: kind,
    allowContainedTargetPrompt: false,
  });
  if (created) {
    ensureInternalTargetSummaryEdge(source, container, containedTarget, kind);
    // Opening a container schedules a full canvas render. Re-materialize after the
    // relationship is stored so that render sees both the portal source and this
    // new relationship in the focused view, rather than the pre-connection view.
    materializeActiveView();
    await syncCanvasFromState({ full: true });
    markModelDirty();
    setStatus(`Connected ${source.label || source.type} to ${containedTarget.label}.`);
  }
  return created;
}

export function addConnection(
  sourceId,
  targetId,
  { interactivePicker = false, preferredKind = null, allowContainedTargetPrompt = true } = {},
) {
  const source = state.nodesById.get(sourceId);
  const target = state.nodesById.get(targetId);
  if (!source || !target) {
    updateCanvasConnectionState();
    return false;
  }
  if (source.id === target.id) {
    setStatus("Source and target cannot be the same");
    updateCanvasConnectionState();
    return false;
  }
  if (
    allowContainedTargetPrompt &&
    interactivePicker &&
    isContainerElement(target) &&
    maybeOpenContainerTargetPicker(source, target, preferredKind)
  ) {
    return true;
  }
  const pairKinds = legalKindsBetween(state.activeType, source.type, target.type);
  if (!pairKinds.length) {
    if (createShortcutConnection(source, target)) {
      return true;
    }
    setStatus("Illegal connection type for selected nodes");
    updateCanvasConnectionState();
    return false;
  }
  const forwardKinds = legalKindsForConnection(source.type, target.type);
  const reverseKinds = legalKindsForConnection(target.type, source.type);
  let kind = pairKinds.includes(preferredKind) ? preferredKind : pairKinds[0];
  const resolved = resolveEdgeEndpointsForKind(source, target, kind, sourceId, targetId);
  if (!resolved) {
    setStatus("Illegal connection type for selected nodes");
    updateCanvasConnectionState();
    return false;
  }
  const resolvedSource = state.nodesById.get(resolved.sourceId);
  const resolvedTarget = state.nodesById.get(resolved.targetId);
  if (!resolvedSource || !resolvedTarget) {
    updateCanvasConnectionState();
    return false;
  }
  kind = resolved.kind;

  const exists = state.diagram.connections.some(
    (edge) => edge.sourceId === resolvedSource.id && edge.targetId === resolvedTarget.id,
  );
  if (exists) {
    setStatus("Connection already exists between these elements");
    updateCanvasConnectionState();
    return false;
  }

  const edge = {
    id: genId("e"),
    sourceId: resolvedSource.id,
    targetId: resolvedTarget.id,
    kind,
  };
  pushDiagramUndoSnapshot(captureAddConnectionUndoSnapshot(edge.id));
  state.diagram.connections.push(edge);
  addConnectionToGraphAndActiveView(edge);
  if (!elementAttributePanelIsActive()) {
    state.selectedConnectionId = edge.id;
  }
  connectionsById.set(edge.id, edge);
  [edge.sourceId, edge.targetId].forEach((nodeId) => {
    if (!edgeIdsByNodeId.has(nodeId)) {
      edgeIdsByNodeId.set(nodeId, new Set());
    }
    edgeIdsByNodeId.get(nodeId).add(edge.id);
  });
  addCanvasEdge(edge);
  updateCanvasSelection();
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
  ensureCanvas();
  syncCanvasIndexesFromState();
  syncCanvasFromState({ full: false });
  markModelDirty({ viewSynced: true });
  setStatus(`Created ${rule.label || "shortcut connector"}`);
  return true;
}

function shortcutSemanticElement(node) {
  return state.graph.elementsById.get(node?.id) || node?.meta || null;
}

function shortcutStackOwner(binding, source, target) {
  const expectedType = String(binding?.ownerType || "").trim();
  const childType = String(binding?.type || "").trim();
  const feature = String(binding?.feature || "").trim();
  const acceptsBinding = (element) => {
    const ownerType = element?.eClass || element?.type;
    if (!ownerType) {
      return false;
    }
    if (expectedType && !modelingTypeMatchesSafe(expectedType, ownerType)) {
      return false;
    }
    return modelingContainmentsForType(state.activeType, ownerType).some((entry) => {
      if (feature && entry.feature !== feature) {
        return false;
      }
      return !childType || containmentAcceptsType(entry, childType);
    });
  };
  for (const endpoint of [source, target]) {
    const element = shortcutSemanticElement(endpoint);
    const owner = element?.__ownerId ? state.graph.elementsById.get(element.__ownerId) : null;
    if (owner && acceptsBinding(owner)) {
      return owner;
    }
  }
  return [...state.graph.elementsById.values()].find(acceptsBinding);
}

function shortcutBindingOwner(binding, source, target) {
  if (binding.owner === "source") {
    return shortcutSemanticElement(source);
  }
  if (binding.owner === "target") {
    return shortcutSemanticElement(target);
  }
  if (binding.owner === "stack") {
    return shortcutStackOwner(binding, source, target);
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
  const rootFeature =
    String(rule.rootFeature || "").trim() ||
    modelingRootContainments(state.activeType).find((entry) =>
      containmentAcceptsType(entry, viewType),
    )?.feature ||
    "";
  if (rootFeature) {
    relationship.rootFeature = rootFeature;
    relationship.__containmentFeature = rootFeature;
  }
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

export const CONNECTION_DRAW_STATE_EVENT = "connection-draw-state-change";

let connectionDrawHintEl = null;

export function isConnectionDrawActive() {
  return Boolean(state.connectMode && state.connectSourceId);
}

export function notifyConnectionDrawStateChange() {
  window.dispatchEvent(new Event(CONNECTION_DRAW_STATE_EVENT));
}

function ensureConnectionDrawHint() {
  if (connectionDrawHintEl?.isConnected) {
    return connectionDrawHintEl;
  }
  const host = el.canvasGrid?.closest(".canvas-stage");
  if (!host) {
    return null;
  }
  connectionDrawHintEl = document.createElement("div");
  connectionDrawHintEl.className = "canvas-connection-draw-hint hidden";
  connectionDrawHintEl.setAttribute("role", "status");
  host.appendChild(connectionDrawHintEl);
  return connectionDrawHintEl;
}

export function syncConnectionDrawChrome() {
  const active = isConnectionDrawActive();
  el.canvasGrid?.classList.toggle("is-connection-draw-active", active);
  el.g6EditorHost?.classList.toggle("is-connection-draw-active", active);

  const hint = ensureConnectionDrawHint();
  if (!hint) {
    return;
  }
  if (!active) {
    hint.classList.add("hidden");
    hint.replaceChildren();
    return;
  }

  const source = state.nodesById.get(state.connectSourceId);
  const kind = state.preferredConnectionKind;
  const kindLabel = kind ? modelingRelationshipKindLabel(state.activeType, kind) : "relationship";
  const sourceLabel = source?.label || source?.type || "element";

  hint.classList.remove("hidden");
  hint.replaceChildren();

  const label = document.createElement("span");
  label.className = "canvas-connection-draw-hint-label";
  label.innerHTML = `Drawing <strong>${escapeHtml(kindLabel)}</strong> from <strong>${escapeHtml(
    sourceLabel,
  )}</strong> — click a highlighted target`;

  const cancel = document.createElement("button");
  cancel.type = "button";
  cancel.className = "btn btn-secondary btn-sm canvas-connection-draw-hint-cancel";
  cancel.textContent = "Cancel";
  cancel.title = "Exit draw mode (Esc)";
  cancel.addEventListener("click", () => {
    cancelConnectionDraw();
  });

  hint.append(label, cancel);
}

export function cancelConnectionDraw({ silent = false } = {}) {
  if (!isConnectionDrawActive()) {
    return false;
  }
  state.connectMode = false;
  state.connectSourceId = null;
  state.preferredConnectionKind = null;
  closeEdgeKindPicker();
  ensureCanvas();
  updateCanvasConnectionState();
  syncConnectionDrawChrome();
  notifyConnectionDrawStateChange();
  if (!silent) {
    setStatus("Draw mode canceled");
  }
  return true;
}

export function setConnectMode(enabled) {
  if (!enabled) {
    cancelConnectionDraw({ silent: true });
    setStatus("Connect mode disabled");
    return;
  }
  state.connectMode = true;
  state.connectSourceId = null;
  state.preferredConnectionKind = null;
  ensureCanvas();
  updateCanvasConnectionState();
  syncConnectionDrawChrome();
  notifyConnectionDrawStateChange();
  setStatus("Connect mode enabled - click source then target");
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
  ensureCanvas();
  updateCanvasConnectionState();
  syncConnectionDrawChrome();
  notifyConnectionDrawStateChange();
  const kindLabel = preferredKind
    ? modelingRelationshipKindLabel(state.activeType, preferredKind)
    : null;
  setStatus(
    kindLabel
      ? `Drawing ${kindLabel} — click a highlighted target on the canvas`
      : "Select a highlighted legal target on the canvas",
  );
  return true;
}

// ── Impact highlight (called by renderNodes and impact module) ────────────────

export function highlightImpactedNodes() {
  ensureCanvas();
  updateCanvasImpactState();
}

export function scrollToNodeAndHighlight(elementId) {
  ensureCanvas();
  const node =
    state.nodesById.get(elementId) ||
    state.diagram.nodes.find((candidate) => candidate.id === elementId);
  if (!node) {
    return;
  }
  focusCanvasNode(elementId);
  state.issueLocateTargetId = elementId;
  updateCanvasImpactState();
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
  ensureCanvas();
  state.selectedConnectionId = connectionId;
  state.issueLocateTargetId = connectionId;
  state.viewport.scale = Math.max(Number(state.viewport.scale) || 1, 1.2);
  focusCanvasPoint(midX, midY);
  updateCanvasSelection();
  updateCanvasEdge(connectionId);
  updateCanvasImpactState();
  return true;
}
