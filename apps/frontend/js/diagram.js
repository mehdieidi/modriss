import {state} from './state.js';
import {autoLayoutIfStacked, emptyDiagram, genId} from './utils.js';
import {
  modelingElementDefinition,
  modelingLabelField,
  modelingLegalKinds,
  modelingRootTemplate
} from './modeling-config-data.js';
import {MODEL_TYPES} from './config.js';
import {
  installGraphAndViews,
  persistEdgeLayoutInActiveView,
  serializeGraphAndViewsInto
} from './graph-store.js';
import {materializeActiveView} from './view-materializer.js';
import {
  cimSemanticElementsFromRoot,
  cimSemanticRelationshipsFromRoot
} from './cim-model-utils.js';
import {
  pimSemanticElementsFromRoot,
  pimSemanticRelationshipsFromRoot
} from './pim-model-utils.js';

function sanitizeConnectionIdPart(value) {
  const normalized = String(value ?? "").trim().toLowerCase().replaceAll(
      /[^a-z0-9_-]+/g, "_");
  return normalized || "na";
}

function connectionIdFor(modelType, index, sourceId, targetId, kind) {
  return `rel-${modelType}-${index}-${sanitizeConnectionIdPart(
      kind)}-${sanitizeConnectionIdPart(sourceId)}-${sanitizeConnectionIdPart(
      targetId)}`;
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
  const semanticRelationships = modelType === "cim"
      ? cimSemanticRelationshipsFromRoot(modelJson)
      : modelType === "pim" ? pimSemanticRelationshipsFromRoot(modelJson) : [];
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
  const semanticElements = modelType === "cim"
      ? cimSemanticElementsFromRoot(modelJson)
      : modelType === "pim" ? pimSemanticElementsFromRoot(modelJson) : [];
  if (semanticElements.length) {
    return semanticElements;
  }
  return [];
}

export function relationshipIdsFromModel(modelType, modelJson) {
  return connectionRecords(modelJson, modelType).map((connection, index) => {
    const sourceId = typeof connection?.source === "string" ? connection.source
        : connection?.source?.$ref;
    const targetId = typeof connection?.target === "string" ? connection.target
        : connection?.target?.$ref;
    const fallbackKind = MODEL_TYPES[modelType]?.connectionKinds?.[0]
        || "DEPENDS_ON";
    return connectionIdFor(modelType, index, sourceId, targetId,
        connection?.kind || fallbackKind);
  });
}

function applyAttributes(meta, definition) {
  for (const attribute of definition?.attributes || []) {
    if (!attribute?.name) {
      continue;
    }
    meta[attribute.name] = attribute.defaultValue ?? null;
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
    tags: []
  };
  const definition = modelingElementDefinition(typeKey, nodeType);
  applyAttributes(meta, definition);
  meta.lifecycleStatus ||= "INCOMPLETE";
  if (labelField === "label") {
    meta.label = label;
  } else {
    meta.name = label;
    meta.label = label;
  }
  meta.description ??= "";
  return {id, type: nodeType, label, x, y, meta};
}

export function legalKinds(typeKey, sourceType, targetType) {
  return modelingLegalKinds(typeKey, sourceType, targetType);
}

function genericRootTemplate(typeKey, modelName) {
  if (typeKey === "cim") {
    return {
      name: modelName,
      version: "1.0.0",
      domainName: "CoreDomain",
      boundedContexts: [{name: "Core", rationale: "Initial context"}],
      traceLinks: [],
      assumptions: [],
      validationIssues: [],
      manualBacklog: [],
      graph: {
        elements: [],
        relationships: [],
        traceLinks: [],
        assumptions: [],
        validationIssues: [],
        manualBacklog: []
      },
      fragments: [],
      views: [],
      diagram: {elements: [], relationships: []}
    };
  }
  if (typeKey === "pim") {
    return {
      name: modelName,
      architectureStyle: "EVENT_DRIVEN_SERVERLESS",
      traceLinks: [],
      assumptions: [],
      validationIssues: [],
      manualBacklog: [],
      graph: {
        elements: [],
        relationships: [],
        traceLinks: [],
        assumptions: [],
        validationIssues: [],
        manualBacklog: []
      },
      fragments: [],
      views: [],
      diagram: {elements: [], relationships: []}
    };
  }
  return {
    name: modelName,
    platform: "AWS",
    defaultRegion: "us-east-1",
    traceLinks: [],
    assumptions: [],
    validationIssues: [],
    manualBacklog: [],
    graph: {
      elements: [],
      relationships: [],
      traceLinks: [],
      assumptions: [],
      validationIssues: [],
      manualBacklog: []
    },
    fragments: [],
    views: [],
    diagram: {elements: [], relationships: []}
  };
}

function sanitizeRootForType(typeKey, root) {
  if (!root || typeof root !== "object") {
    return root;
  }
  if (typeKey === "cim") {
    delete root.architectureStyle;
    delete root.platform;
    delete root.defaultRegion;
  } else if (typeKey === "pim") {
    delete root.domainName;
    delete root.platform;
    delete root.defaultRegion;
  } else if (typeKey === "psm") {
    delete root.domainName;
    delete root.architectureStyle;
  }
  delete root.nodes;
  delete root.connectors;
  delete root.resources;
  return root;
}

export function defaultRootModel(typeKey, modelName) {
  const configured = modelingRootTemplate(typeKey, modelName);
  if (configured && Object.keys(configured).length) {
    configured.name = modelName;
    configured.diagram ??= {elements: [], relationships: []};
    configured.diagram.elements ??= [];
    configured.diagram.relationships ??= [];
    configured.graph ??= {
      elements: [],
      relationships: [],
      traceLinks: [],
      assumptions: [],
      validationIssues: [],
      manualBacklog: []
    };
    configured.fragments ??= [];
    configured.views ??= [];
    return configured;
  }
  return genericRootTemplate(typeKey, modelName);
}

export function serializeModel() {
  const name = (state.tabs[state.activeType]?.modelName
      || `${state.activeType}-model`).trim();
  const root = structuredClone(
      state.baseModel || defaultRootModel(state.activeType, name));
  sanitizeRootForType(state.activeType, root);
  root.name = name;
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
      ...node.meta
    };
    if (!element.name && element.label) {
      element.name = element.label;
    }
    return element;
  });

  root.diagram.relationships = state.diagram.connections.filter(
      (edge) => !edge.bundle
          && String(edge.kind).toUpperCase() !== "EDGE_BUNDLE").map(
      (edge, index) => {
        // Create relationship without layout field (backend doesn't support it).
        // Layout data is managed separately in frontend-only storage for rendering.
        const relationship = {
          id: edge.id || connectionIdFor(state.activeType, index, edge.sourceId,
              edge.targetId, edge.kind),
          kind: edge.kind,
          source: edge.sourceId,
          target: edge.targetId,
          note: ""
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
  if (materialized?.nodes?.length || modelJson?.graph || Array.isArray(
      modelJson?.views)) {
    return materialized;
  }

  const diagram = emptyDiagram(modelType);
  diagram.name = modelJson?.name || fallbackName || `${modelType}-model`;
  const rawElements = elementRecords(modelJson, modelType);
  const rawRelationships = connectionRecords(modelJson, modelType);

  // Deduplicate elements by ID, keeping only the first occurrence.
  // This prevents duplicate node IDs in the layout endpoint which would cause validation errors.
  const seenIds = new Set();
  const duplicateIds = new Set();
  const deduplicatedElements = [];

  for (const element of rawElements) {
    const elementId = element.id || genId("node");
    if (seenIds.has(elementId)) {
      duplicateIds.add(elementId);
      console.warn(
          `Duplicate element ID detected: "${elementId}" (${element.eClass} "${element.name}"). Skipping duplicate.`);
    } else {
      seenIds.add(elementId);
      deduplicatedElements.push(element);
    }
  }

  diagram.nodes = deduplicatedElements.map((element) => ({
    id: element.id || genId("node"),
    type: element.eClass || "Element",
    label: element.name || element.label || "Element",
    x: Number.isFinite(element.x) ? element.x : 0,
    y: Number.isFinite(element.y) ? element.y : 0,
    meta: structuredClone(element)
  }));

  diagram.connections = rawRelationships.map((relation, index) => {
    const edge = {
      id:
          relation.id
          || connectionIdFor(
              modelType,
              index,
              typeof relation.source === "string" ? relation.source
                  : relation.source?.$ref,
              typeof relation.target === "string" ? relation.target
                  : relation.target?.$ref,
              relation.kind || (MODEL_TYPES[modelType]?.connectionKinds?.[0]
                  || "DEPENDS_ON")
          ),
      sourceId: typeof relation.source === "string" ? relation.source
          : relation.source?.$ref,
      targetId: typeof relation.target === "string" ? relation.target
          : relation.target?.$ref,
      kind: relation.kind || (MODEL_TYPES[modelType]?.connectionKinds?.[0]
          || "DEPENDS_ON")
    };
    // Restore frontend-only edge presentation data (not persisted to backend).
    // Newer versions store explicit pin points; older versions may still have ELK bend points.
    const storedLayout = getStoredEdgeLayout(modelType, edge.id);
    if (Array.isArray(storedLayout?.pinPoints)) {
      edge.pinPoints = storedLayout.pinPoints.map((point) => ({
        x: Number(point?.x) || 0,
        y: Number(point?.y) || 0
      }));
    } else if (Array.isArray(storedLayout?.bendPoints)) {
      edge.pinPoints = storedLayout.bendPoints.map((point) => ({
        x: Number(point?.x) || 0,
        y: Number(point?.y) || 0
      }));
    }
    if (storedLayout?.sourceAnchor && typeof storedLayout.sourceAnchor
        === "object") {
      edge.sourceAnchor = {
        side: storedLayout.sourceAnchor.side,
        offsetY: Number(storedLayout.sourceAnchor.offsetY) || 0
      };
    }
    if (storedLayout?.targetAnchor && typeof storedLayout.targetAnchor
        === "object") {
      edge.targetAnchor = {
        side: storedLayout.targetAnchor.side,
        offsetY: Number(storedLayout.targetAnchor.offsetY) || 0
      };
    }
    return edge;
  })
  .filter((edge) => {
    // Filter out edges that reference duplicate nodes
    if (duplicateIds.has(edge.sourceId) || duplicateIds.has(edge.targetId)) {
      console.warn(
          `Skipping edge "${edge.id}" because it references a duplicate node (source: "${edge.sourceId}", target: "${edge.targetId}").`);
      return false;
    }
    return !!(edge.sourceId && edge.targetId);
  });

  autoLayoutIfStacked(diagram.nodes);
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
 * @param {string} modelType - CIM, PIM, or PSM
 * @param {string} edgeId - The edge ID
 * @returns {object|null} The stored layout/presentation object or null
 */
function getStoredEdgeLayout(modelType, edgeId) {
  try {
    const stored = localStorage.getItem(
        edgeLayoutStorageKey(modelType, edgeId));
    return stored ? JSON.parse(stored) : null;
  } catch (e) {
    console.warn(`Failed to retrieve edge layout for ${edgeId}:`, e);
    return null;
  }
}

/**
 * Store edge presentation data in browser storage for persistence across reloads.
 * @param {string} modelType - CIM, PIM, or PSM
 * @param {string} edgeId - The edge ID
 * @param {object} layout - The presentation object (for example {pinPoints:[...]}).
 */
function saveStoredEdgeLayout(modelType, edgeId, layout) {
  if (persistEdgeLayoutInActiveView(edgeId, layout)) {
    return;
  }
  try {
    if (layout && typeof layout === "object") {
      localStorage.setItem(edgeLayoutStorageKey(modelType, edgeId),
          JSON.stringify(layout));
    }
  } catch (e) {
    console.warn(`Failed to store edge layout for ${edgeId}:`, e);
  }
}

/**
 * Clear all stored edge layouts for a given model type.
 * @param {string} modelType - CIM, PIM, or PSM
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
    keysToDelete.forEach(key => localStorage.removeItem(key));
  } catch (e) {
    console.warn(`Failed to clear edge layouts for ${modelType}:`, e);
  }
}

export {getStoredEdgeLayout, saveStoredEdgeLayout, clearStoredEdgeLayouts};
