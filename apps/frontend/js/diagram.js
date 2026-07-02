import { state } from "./state.js";
import { emptyDiagram, genId } from "./utils.js";
import { ensureReadableLayout, nodeSizeForType } from "./layout-engine.js";
import {
  modelingElementDefinition,
  modelingLabelField,
  modelingLegalKinds,
  modelingLegalKindsBetween,
  modelingLevelConfig,
  modelingRootTemplate,
  modelingRootType,
  isModelingLevel,
} from "./modeling-config-data.js";
import {
  installGraphAndViews,
  persistEdgeLayoutInActiveView,
  serializeGraphAndViewsInto,
} from "./graph-store.js";
import { materializeActiveView } from "./view-materializer.js";
import { semanticElementsFromRoot, semanticRelationshipsFromRoot } from "./model-utils.js";

function connectionIdFor(_modelType, _index, _sourceId, _targetId, _kind) {
  return genId();
}

function connectionRecords(modelJson, modelType = state.activeType) {
  if (Array.isArray(modelJson?.diagram?.relationships)) {
    return modelJson.diagram.relationships;
  }
  // Legacy compatibility
  if (Array.isArray(modelJson?.workshops?.[0]?.relations)) {
    return modelJson.workshops[0].relations;
  }
  if (Array.isArray(modelJson?.connectors)) {
    return modelJson.connectors;
  }
  const semanticRelationships = isModelingLevel(modelType)
    ? semanticRelationshipsFromRoot(modelType, modelJson)
    : [];
  if (semanticRelationships.length) {
    return semanticRelationships;
  }
  return [];
}

function elementRecords(modelJson, modelType = state.activeType) {
  if (Array.isArray(modelJson?.diagram?.elements)) {
    return modelJson.diagram.elements;
  }
  // Legacy compatibility
  if (Array.isArray(modelJson?.workshops?.[0]?.elements)) {
    return modelJson.workshops[0].elements;
  }
  if (Array.isArray(modelJson?.nodes)) {
    return modelJson.nodes;
  }
  if (Array.isArray(modelJson?.resources)) {
    return modelJson.resources;
  }
  const semanticElements = isModelingLevel(modelType)
    ? semanticElementsFromRoot(modelType, modelJson)
    : [];
  if (semanticElements.length) {
    return semanticElements;
  }
  return [];
}

function isRootElementRecord(modelType, element) {
  const rootType = isModelingLevel(modelType) ? modelingRootType(modelType) : "";
  return Boolean(rootType && String(element?.eClass || element?.type || "") === rootType);
}

export function relationshipIdsFromModel(modelType, modelJson) {
  return connectionRecords(modelJson, modelType).map((connection, index) => {
    const sourceId =
      typeof connection?.source === "string" ? connection.source : connection?.source?.$ref;
    const targetId =
      typeof connection?.target === "string" ? connection.target : connection?.target?.$ref;
    const fallbackKind = defaultRelationshipKind(modelType);
    return connectionIdFor(modelType, index, sourceId, targetId, connection?.kind || fallbackKind);
  });
}

function defaultRelationshipKind(modelType) {
  try {
    return modelingLevelConfig(modelType).relationshipKinds?.[0] || "";
  } catch {
    return "";
  }
}

function cloneDefault(value) {
  return value == null ? value : structuredClone(value);
}

function applyDefinitionDefaults(meta, definition) {
  for (const field of [...(definition?.attributes || []), ...(definition?.references || [])]) {
    if (!field?.name || field.readonly) {
      continue;
    }
    meta[field.name] = cloneDefault(field.defaultValue);
  }
}

export function getDefaultNode(typeKey, nodeType, x, y) {
  const id = genId(nodeType.toLowerCase());
  const labelField = modelingLabelField(typeKey);
  const label = `${nodeType}-${id.slice(-4)}`;
  const meta = {
    eClass: nodeType,
    id,
    x,
    y,
    status: "DRAFT",
    lifecycleStatus: "INCOMPLETE",
    tags: [],
  };
  const definition = modelingElementDefinition(typeKey, nodeType);
  applyDefinitionDefaults(meta, definition);
  meta.lifecycleStatus ||= "INCOMPLETE";
  if (labelField === "label") {
    meta.label = label;
  } else {
    meta.name = label;
    meta.label = label;
  }
  meta.description ??= "";
  return { id, type: nodeType, label, x, y, meta };
}

export function legalKinds(typeKey, sourceType, targetType) {
  return modelingLegalKinds(typeKey, sourceType, targetType);
}

export function legalKindsBetween(typeKey, typeA, typeB) {
  return modelingLegalKindsBetween(typeKey, typeA, typeB);
}

function sanitizeRootForType(typeKey, root) {
  if (!root || typeof root !== "object") {
    return root;
  }
  delete root.nodes;
  delete root.connectors;
  delete root.resources;
  return root;
}

export function defaultRootModel(typeKey, modelName) {
  const configured = modelingRootTemplate(typeKey, modelName);
  if (!configured || !Object.keys(configured).length) {
    throw new Error(`Missing backend rootTemplate for ${typeKey.toUpperCase()}.`);
  }
  configured.name = modelName;
  configured.id ??= genId();
  configured.diagram ??= { elements: [], relationships: [] };
  configured.diagram.elements ??= [];
  configured.diagram.relationships ??= [];
  configured.graph ??= {
    elements: [],
    relationships: [],
    traceLinks: [],
    assumptions: [],
    validationIssues: [],
    manualBacklog: [],
  };
  configured.fragments ??= [];
  configured.views ??= [];
  return configured;
}

export function serializeModel() {
  const name = (state.tabs[state.activeType]?.modelName || `${state.activeType}-model`).trim();
  const root = structuredClone(state.baseModel || defaultRootModel(state.activeType, name));
  sanitizeRootForType(state.activeType, root);
  if (!String(root.name || "").trim()) {
    root.name = name;
  }
  root.diagram ??= {};

  root.diagram.elements = state.diagram.nodes.map((node) => {
    const element = {
      eClass: node.type,
      id: node.id,
      name: node.label,
      label: node.label,
      description: "",
      x: node.x,
      y: node.y,
      status: "DRAFT",
      tags: [],
      ...node.meta,
    };
    if (!element.name && element.label) {
      element.name = element.label;
    }
    return element;
  });

  root.diagram.relationships = state.diagram.connections
    .filter((edge) => !edge.bundle && String(edge.kind).toUpperCase() !== "EDGE_BUNDLE")
    .map((edge, index) => {
      // Create relationship without layout field (backend doesn't support it).
      // Layout data is managed separately in frontend-only storage for rendering.
      const relationship = {
        id:
          edge.id ||
          connectionIdFor(state.activeType, index, edge.sourceId, edge.targetId, edge.kind),
        kind: edge.kind,
        source: edge.sourceId,
        target: edge.targetId,
        note: "",
      };
      return relationship;
    });
  serializeGraphAndViewsInto(root);
  return root;
}

export function toGraphAndViews(modelType, modelJson, fallbackName) {
  return installGraphAndViews(modelType, modelJson || {}, fallbackName);
}

export function toDiagram(modelType, modelJson, fallbackName) {
  const previousActiveType = state.activeType;
  let materialized;
  try {
    state.activeType = modelType;
    toGraphAndViews(modelType, modelJson || {}, fallbackName);
    materialized = materializeActiveView();
  } finally {
    state.activeType = previousActiveType;
  }
  if (materialized?.nodes?.length || modelJson?.graph || Array.isArray(modelJson?.views)) {
    return materialized;
  }

  const diagram = emptyDiagram(modelType);
  diagram.name = modelJson?.name || fallbackName || `${modelType}-model`;
  const rawElements = elementRecords(modelJson, modelType);
  const rawRelationships = connectionRecords(modelJson, modelType);

  diagram.nodes = rawElements
    .filter((element) => !isRootElementRecord(modelType, element))
    .map((element) => ({
      id: element.id || genId("node"),
      type: element.eClass || "Element",
      label: element.name || element.label || "Element",
      x: Number.isFinite(element.x) ? element.x : 0,
      y: Number.isFinite(element.y) ? element.y : 0,
      meta: structuredClone(element),
    }));

  diagram.connections = rawRelationships
    .map((relation, index) => {
      const edge = {
        id:
          relation.id ||
          connectionIdFor(
            modelType,
            index,
            typeof relation.source === "string" ? relation.source : relation.source?.$ref,
            typeof relation.target === "string" ? relation.target : relation.target?.$ref,
            relation.kind || defaultRelationshipKind(modelType),
          ),
        sourceId: typeof relation.source === "string" ? relation.source : relation.source?.$ref,
        targetId: typeof relation.target === "string" ? relation.target : relation.target?.$ref,
        kind: relation.kind || defaultRelationshipKind(modelType),
      };
      // Restore frontend-only edge presentation data (not persisted to backend).
      // Newer versions store explicit pin points; older versions may still have ELK bend points.
      const storedLayout = getStoredEdgeLayout(modelType, edge.id);
      if (Array.isArray(storedLayout?.pinPoints)) {
        edge.pinPoints = storedLayout.pinPoints.map((point) => ({
          x: Number(point?.x) || 0,
          y: Number(point?.y) || 0,
        }));
      } else if (Array.isArray(storedLayout?.bendPoints)) {
        edge.pinPoints = storedLayout.bendPoints.map((point) => ({
          x: Number(point?.x) || 0,
          y: Number(point?.y) || 0,
        }));
      }
      if (storedLayout?.sourceAnchor && typeof storedLayout.sourceAnchor === "object") {
        edge.sourceAnchor = {
          side: storedLayout.sourceAnchor.side,
          offsetY: Number(storedLayout.sourceAnchor.offsetY) || 0,
        };
      }
      if (storedLayout?.targetAnchor && typeof storedLayout.targetAnchor === "object") {
        edge.targetAnchor = {
          side: storedLayout.targetAnchor.side,
          offsetY: Number(storedLayout.targetAnchor.offsetY) || 0,
        };
      }
      return edge;
    })
    .filter((edge) => !!(edge.sourceId && edge.targetId));

  ensureReadableLayout(diagram.nodes, diagram.connections, nodeSizeForType(modelType));
  return diagram;
}

/**
 * Storage key for edge layouts in browser localStorage.
 * Layouts are stored separately because the backend doesn't support custom layout fields.
 * @private
 */
function edgeLayoutStorageKey(modelType, edgeId) {
  return `elk_layout:${modelType}:${edgeId}`;
}

/**
 * Retrieve stored edge presentation data from browser storage.
 * @param {string} modelType - configured modeling level key
 * @param {string} edgeId - The edge ID
 * @returns {object|null} The stored layout/presentation object or null
 */
function getStoredEdgeLayout(modelType, edgeId) {
  try {
    const stored = localStorage.getItem(edgeLayoutStorageKey(modelType, edgeId));
    return stored ? JSON.parse(stored) : null;
  } catch (e) {
    console.warn(`Failed to retrieve edge layout for ${edgeId}:`, e);
    return null;
  }
}

/**
 * Store edge presentation data in browser storage for persistence across reloads.
 * @param {string} modelType - configured modeling level key
 * @param {string} edgeId - The edge ID
 * @param {object} layout - The presentation object (for example {pinPoints:[...]}).
 */
function saveStoredEdgeLayout(modelType, edgeId, layout) {
  if (persistEdgeLayoutInActiveView(edgeId, layout)) {
    return;
  }
  try {
    if (layout && typeof layout === "object") {
      localStorage.setItem(edgeLayoutStorageKey(modelType, edgeId), JSON.stringify(layout));
    }
  } catch (e) {
    console.warn(`Failed to store edge layout for ${edgeId}:`, e);
  }
}

/**
 * Clear all stored edge layouts for a given model type.
 * @param {string} modelType - configured modeling level key
 */
function clearStoredEdgeLayouts(modelType) {
  try {
    const keysToDelete = [];
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key?.startsWith(`elk_layout:${modelType}:`)) {
        keysToDelete.push(key);
      }
    }
    keysToDelete.forEach((key) => localStorage.removeItem(key));
  } catch (e) {
    console.warn(`Failed to clear edge layouts for ${modelType}:`, e);
  }
}

export { getStoredEdgeLayout, saveStoredEdgeLayout, clearStoredEdgeLayouts };
