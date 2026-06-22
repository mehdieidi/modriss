import { state } from "./state.js";
import { emptyDiagram, genId } from "./utils.js";
import { ensureReadableLayout, nodeSizeForType } from "./layout-engine.js";
import {
  modelingContainmentsForType,
  modelingElementDefinition,
  isModelingLevel,
  modelingLevelConfig,
  modelingRelationshipElementTypes,
  modelingRootContainments,
  modelingRootType,
  modelingSemanticReferenceRules,
  modelingTypeMatches,
} from "./modeling-config-data.js";
import {
  addReferenceValue,
  modelTypeOf,
  populateRootContainments,
  relationshipSemanticCopy,
  removeReferenceValue,
  semanticEdgeObjectSpec,
  semanticElementsFromRoot,
  semanticRelationshipsFromRoot,
  modelTypeMatches,
} from "./model-utils.js";

const viewNodeIndexes = new WeakMap();

function relationshipSemantics(typeKey = state.activeType) {
  return modelingLevelConfig(typeKey).relationshipSemantics || {};
}

function containmentKinds(typeKey = state.activeType) {
  return new Set(safeArray(relationshipSemantics(typeKey).containmentKinds).map(String));
}

function containmentKind(typeKey = state.activeType) {
  const kind = String(relationshipSemantics(typeKey).containmentKind || "");
  if (!kind) {
    throw new Error(`Missing containmentKind metadata for ${typeKey}`);
  }
  return kind;
}

function clone(value) {
  return value == null ? value : structuredClone(value);
}

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function boundedContextTypes() {
  return new Set(
    Object.values(state.modelingConfig.config?.levels || {})
      .map((level) => String(level?.boundedContext?.candidateType || ""))
      .filter(Boolean),
  );
}

export function prepareViewNodeIndex(view) {
  if (!view) {
    return new Map();
  }
  const index = new Map(safeArray(view.nodes).map((entry) => [entry.elementId, entry]));
  viewNodeIndexes.set(view, index);
  return index;
}

function sanitizeIdPart(value) {
  return (
    String(value || "view")
      .trim()
      .toLowerCase()
      .replaceAll(/[^a-z0-9_-]+/g, "-")
      .replaceAll(/^-+|-+$/g, "") || "view"
  );
}

function normalizeViewName(value) {
  return String(value || "")
    .trim()
    .toLowerCase()
    .replaceAll(/[^a-z0-9]+/g, " ");
}

function modelLevelValue(typeKey) {
  const root = modelingLevelConfig(typeKey).rootTemplate || {};
  return String(root.modelLevel || modelingLevelConfig(typeKey).chatType || typeKey.toUpperCase());
}

function rootScopeType(typeKey) {
  return modelingRootType(typeKey) || "";
}

function mainSurfaceRootTypes(typeKey) {
  return new Set([rootScopeType(typeKey)].filter(Boolean));
}

function viewBelongsToLevel(view, typeKey) {
  const level = String(view?.level || "")
    .trim()
    .toUpperCase();
  if (!level) {
    return true;
  }
  return level === modelLevelValue(typeKey).toUpperCase();
}

function isFocusView(view) {
  return (
    String(view?.kind || "")
      .trim()
      .toUpperCase() === "FOCUS" || String(view?.id || "").includes("-focus-")
  );
}

function elementRecords(modelJson, typeKey = state.activeType) {
  const semanticElements = isModelingLevel(typeKey)
    ? semanticElementsFromRoot(typeKey, modelJson)
    : [];
  const graphElements = Array.isArray(modelJson?.graph?.elements) ? modelJson.graph.elements : [];
  if (semanticElements.length) {
    if (!graphElements.length) {
      return semanticElements;
    }
    const graphById = new Map(
      graphElements
        .map((element) => [String(element?.id || "").trim(), element])
        .filter(([id]) => id),
    );
    const mergedSemantic = semanticElements.map((element) => {
      const graphElement = graphById.get(String(element?.id || "").trim());
      return graphElement
        ? {
            ...clone(graphElement),
            ...clone(element),
            x: Number.isFinite(Number(graphElement?.x)) ? Number(graphElement.x) : element.x,
            y: Number.isFinite(Number(graphElement?.y)) ? Number(graphElement.y) : element.y,
          }
        : element;
    });
    const semanticIds = new Set(
      semanticElements.map((element) => String(element?.id || "").trim()).filter(Boolean),
    );
    return [
      ...mergedSemantic,
      ...graphElements.filter((element) => !semanticIds.has(String(element?.id || "").trim())),
    ];
  }
  if (graphElements.length) {
    return graphElements;
  }
  if (Array.isArray(modelJson?.diagram?.elements)) {
    return modelJson.diagram.elements;
  }
  if (Array.isArray(modelJson?.workshops?.[0]?.elements)) {
    return modelJson.workshops[0].elements;
  }
  if (Array.isArray(modelJson?.nodes)) {
    return modelJson.nodes;
  }
  if (Array.isArray(modelJson?.resources)) {
    return modelJson.resources;
  }
  return [];
}

function relationshipRecords(modelJson, typeKey = state.activeType) {
  const semanticRelationships = isModelingLevel(typeKey)
    ? semanticRelationshipsFromRoot(typeKey, modelJson)
    : [];
  if (semanticRelationships.length) {
    const graphRelationships = Array.isArray(modelJson?.graph?.relationships)
      ? modelJson.graph.relationships
      : [];
    if (!graphRelationships.length) {
      return semanticRelationships;
    }
    const graphById = new Map(
      graphRelationships
        .map((relationship) => [String(relationship?.id || "").trim(), relationship])
        .filter(([id]) => id),
    );
    const mergedSemantic = semanticRelationships.map((relationship) => {
      const graphRelationship = graphById.get(String(relationship?.id || "").trim());
      return graphRelationship
        ? {
            ...clone(graphRelationship),
            ...clone(relationship),
            source: relationship.source || graphRelationship.source,
            target: relationship.target || graphRelationship.target,
            sourceElementId:
              relationship.sourceElementId ||
              graphRelationship.sourceElementId ||
              graphRelationship.source,
            targetElementId:
              relationship.targetElementId ||
              graphRelationship.targetElementId ||
              graphRelationship.target,
          }
        : relationship;
    });
    const semanticIds = new Set(
      semanticRelationships
        .map((relationship) => String(relationship?.id || "").trim())
        .filter(Boolean),
    );
    return [
      ...mergedSemantic,
      ...graphRelationships.filter(
        (relationship) => !semanticIds.has(String(relationship?.id || "").trim()),
      ),
    ];
  }
  if (Array.isArray(modelJson?.graph?.relationships)) {
    return modelJson.graph.relationships;
  }
  if (Array.isArray(modelJson?.diagram?.relationships)) {
    return modelJson.diagram.relationships;
  }
  if (Array.isArray(modelJson?.workshops?.[0]?.relations)) {
    return modelJson.workshops[0].relations;
  }
  if (Array.isArray(modelJson?.connectors)) {
    return modelJson.connectors;
  }
  return [];
}

function relationshipElementTypes(typeKey) {
  try {
    return new Set(modelingRelationshipElementTypes(typeKey));
  } catch {
    return new Set();
  }
}

function configuredContainments(typeKey, ownerType) {
  try {
    return modelingContainmentsForType(typeKey, ownerType);
  } catch {
    return [];
  }
}

function normalizedSemanticId(type, index, owner = null) {
  const ownerPrefix = owner?.id ? `${sanitizeIdPart(owner.id)}-` : "";
  return `${ownerPrefix}${sanitizeIdPart(type || "element")}-${index + 1}`;
}

function normalizeConfiguredSemanticElement(typeKey, raw, fallbackType, index, owner = null) {
  if (!raw || typeof raw !== "object") {
    return null;
  }
  const type = String(raw.eClass || raw.type || fallbackType || "");
  if (!type || relationshipElementTypes(typeKey).has(type)) {
    return null;
  }
  const id = String(raw.id || normalizedSemanticId(type, index, owner));
  return {
    eClass: type,
    id,
    name: String(
      raw.name ||
        raw.label ||
        raw.logicalId ||
        raw.physicalName ||
        raw.stackName ||
        raw.stageName ||
        id,
    ),
    label: String(
      raw.name ||
        raw.label ||
        raw.logicalId ||
        raw.physicalName ||
        raw.stackName ||
        raw.stageName ||
        id,
    ),
    ...clone(raw),
    id,
    eClass: type,
    __ownerId: owner?.id || raw.__ownerId,
    __containmentFeature: owner?.feature || raw.__containmentFeature,
  };
}

function collectConfiguredNestedElements(typeKey, parent, result, seen) {
  const parentType = semanticType(parent);
  configuredContainments(typeKey, parentType).forEach((entry) => {
    if (entry.relationshipOnly) {
      return;
    }
    const values = entry.singleton
      ? parent?.[entry.feature]
        ? [parent[entry.feature]]
        : []
      : safeArray(parent?.[entry.feature]);
    values.forEach((raw, index) => {
      if (!raw || typeof raw !== "object") {
        return;
      }
      const child = normalizeConfiguredSemanticElement(
        typeKey,
        raw,
        raw.eClass || raw.type || entry.types?.[0],
        index,
        {
          id: parent.id,
          feature: entry.feature,
        },
      );
      if (!child || seen.has(child.id)) {
        return;
      }
      seen.add(child.id);
      result.push(child);
      collectConfiguredNestedElements(typeKey, child, result, seen);
    });
  });
}

function _semanticElementsFromConfiguredRoot(typeKey, modelJson) {
  const result = [];
  const seen = new Set();
  if (!modelJson || typeof modelJson !== "object") {
    return result;
  }
  const rootContainments = modelingRootContainments(typeKey);
  rootContainments.forEach((entry) => {
    if (entry.relationshipOnly) {
      return;
    }
    const values = entry.singleton
      ? modelJson?.[entry.feature]
        ? [modelJson[entry.feature]]
        : []
      : safeArray(modelJson?.[entry.feature]);
    values.forEach((raw, index) => {
      if (!raw || typeof raw !== "object") {
        return;
      }
      const element = normalizeConfiguredSemanticElement(
        typeKey,
        raw,
        raw.eClass || raw.type || entry.types?.[0],
        index,
      );
      if (!element || seen.has(element.id)) {
        return;
      }
      seen.add(element.id);
      result.push(element);
      collectConfiguredNestedElements(typeKey, element, result, seen);
    });
  });
  return result;
}

function refId(value) {
  if (typeof value === "string") {
    return value;
  }
  if (value && typeof value === "object") {
    return value.$ref || value.id || value.elementId || "";
  }
  return "";
}

function semanticLabel(element) {
  return String(element?.name || element?.label || element?.id || "Element");
}

function semanticType(element) {
  return String(element?.eClass || element?.type || "Element");
}

function relationshipSourceId(relationship) {
  return String(
    relationship?.sourceElementId || relationship?.sourceId || refId(relationship?.source) || "",
  );
}

function relationshipTargetId(relationship) {
  return String(
    relationship?.targetElementId || relationship?.targetId || refId(relationship?.target) || "",
  );
}

function relationshipIdentity(typeKey, relationship, index) {
  const kind = String(relationship?.kind || "").trim();
  const source = relationshipSourceId(relationship);
  const target = relationshipTargetId(relationship);
  const explicit = String(relationship?.id || "").trim();
  if (explicit) {
    return explicit;
  }
  return `rel-${typeKey}-${index}-${sanitizeIdPart(kind)}-${sanitizeIdPart(
    source,
  )}-${sanitizeIdPart(target)}`;
}

function normalizeElement(element, _index) {
  const id = String(element?.id || genId("node")).trim();
  return {
    eClass: semanticType(element),
    id,
    name: semanticLabel(element),
    label: semanticLabel(element),
    status: "DRAFT",
    tags: [],
    ...clone(element),
    id,
  };
}

function normalizeRelationship(typeKey, relationship, index) {
  const sourceElementId = relationshipSourceId(relationship);
  const targetElementId = relationshipTargetId(relationship);
  const kind = String(relationship?.kind || "").trim();
  const id = relationshipIdentity(typeKey, relationship, index);
  return {
    ...clone(relationship),
    id,
    kind,
    sourceElementId,
    targetElementId,
    source: sourceElementId,
    target: targetElementId,
  };
}

function referenceIds(value) {
  if (Array.isArray(value)) {
    return value.flatMap(referenceIds);
  }
  if (typeof value === "string") {
    return value.trim().split(/\s+/).filter(Boolean);
  }
  const single = refId(value);
  return single ? [single] : [];
}

function matchesSemanticType(element, expectedType, typeKey = state.activeType) {
  return Boolean(element) && modelTypeMatches(typeKey, element, expectedType);
}

function relationshipKindMatches(kind, allowedKinds) {
  if (!allowedKinds?.size) {
    return true;
  }
  const normalized = String(kind || "")
    .trim()
    .toUpperCase();
  if (allowedKinds.has(normalized)) {
    return true;
  }
  const family = normalized.split("_")[0];
  return Boolean(family && allowedKinds.has(family));
}

function elementMatchesFilterTypes(element, filterTypes, typeKey) {
  if (!filterTypes?.size) {
    return true;
  }
  if (!element) {
    return false;
  }
  const type = semanticType(element);
  return (
    filterTypes.has(type) ||
    [...filterTypes].some((expected) => {
      try {
        return matchesSemanticType(element, expected, typeKey);
      } catch {
        return false;
      }
    })
  );
}

function synthesizeSemanticRefRelationships(graph, typeKey = state.activeType) {
  const additions = [];
  const edgeKeys = new Set();
  graph.relationshipsById.forEach((relationship) => {
    edgeKeys.add(
      `${relationship.sourceElementId}|${relationship.targetElementId}|${relationship.kind}`,
    );
  });

  let rules = [];
  try {
    const configured = modelingSemanticReferenceRules(typeKey);
    if (configured.length) {
      rules = configured;
    }
  } catch {
    rules = [];
  }

  graph.elementsById.forEach((element) => {
    for (const rule of rules) {
      if (!matchesSemanticType(element, rule.sourceType, typeKey)) {
        continue;
      }
      for (const targetId of referenceIds(element?.[rule.feature])) {
        const target = graph.elementsById.get(targetId);
        if (!matchesSemanticType(target, rule.targetType, typeKey)) {
          continue;
        }
        const kind = String(rule.kind || "REFERENCES");
        const sourceId = rule.reverse ? targetId : element.id;
        const destinationId = rule.reverse ? element.id : targetId;
        const key = `${sourceId}|${destinationId}|${kind}`;
        if (edgeKeys.has(key)) {
          continue;
        }
        edgeKeys.add(key);
        additions.push({
          id: genId(),
          kind,
          sourceElementId: sourceId,
          targetElementId: destinationId,
          source: sourceId,
          target: destinationId,
          semanticFeature: rule.feature,
          visualOnly: true,
        });
      }
    }
  });

  additions.forEach((relationship) => {
    graph.relationshipsById.set(relationship.id, relationship);
  });
}

function synthesizeContainmentRelationships(graph, typeKey = state.activeType) {
  const configuredContainmentKind = containmentKind(typeKey);
  const configuredContainmentKinds = containmentKinds(typeKey);
  const edgeKeys = new Set();
  graph.relationshipsById.forEach((relationship) => {
    if (
      relationship.containment === true ||
      configuredContainmentKinds.has(String(relationship.kind || "").toUpperCase())
    ) {
      edgeKeys.add(
        `${relationship.sourceElementId}|${relationship.targetElementId}|${configuredContainmentKind}`,
      );
    }
  });

  const addContainment = (parentId, childId, feature) => {
    if (!graph.elementsById.has(parentId) || !graph.elementsById.has(childId)) {
      return;
    }
    const key = `${parentId}|${childId}|${configuredContainmentKind}`;
    if (edgeKeys.has(key)) {
      return;
    }
    edgeKeys.add(key);
    const id = `containment-${sanitizeIdPart(parentId)}-${sanitizeIdPart(feature)}-${sanitizeIdPart(childId)}`;
    graph.relationshipsById.set(id, {
      id,
      kind: configuredContainmentKind,
      source: parentId,
      target: childId,
      sourceElementId: parentId,
      targetElementId: childId,
      semanticFeature: feature,
      containment: true,
      visualOnly: true,
    });
  };

  graph.elementsById.forEach((element) => {
    const parentId = String(element.__ownerId || "").trim();
    if (parentId) {
      addContainment(parentId, element.id, element.__containmentFeature || "contains");
    }
    let containments = [];
    try {
      containments = modelingContainmentsForType(typeKey, semanticType(element));
    } catch {
      containments = [];
    }
    containments
      .filter((entry) => !entry.relationshipOnly)
      .forEach((entry) => {
        referenceIds(element?.[entry.feature]).forEach((childId) => {
          addContainment(element.id, childId, entry.feature);
        });
      });
  });
}

function createEmptyGraph() {
  return {
    elementsById: new Map(),
    relationshipsById: new Map(),
    traceLinksById: new Map(),
    assumptionsById: new Map(),
    relationshipsBySource: new Map(),
    relationshipsByTarget: new Map(),
    relationshipsByKind: new Map(),
    containmentByParent: new Map(),
    parentByChild: new Map(),
  };
}

function addToIndex(map, key, value) {
  const normalizedKey = String(key || "").trim();
  if (!normalizedKey) {
    return;
  }
  let bucket = map.get(normalizedKey);
  if (!bucket) {
    bucket = new Set();
    map.set(normalizedKey, bucket);
  }
  bucket.add(value);
}

function rebuildGraphIndexes(graph) {
  const configuredContainmentKinds = containmentKinds();
  graph.relationshipsBySource = new Map();
  graph.relationshipsByTarget = new Map();
  graph.relationshipsByKind = new Map();
  graph.containmentByParent = new Map();
  graph.parentByChild = new Map();

  graph.relationshipsById.forEach((relationship) => {
    addToIndex(graph.relationshipsBySource, relationship.sourceElementId, relationship.id);
    addToIndex(graph.relationshipsByTarget, relationship.targetElementId, relationship.id);
    addToIndex(graph.relationshipsByKind, relationship.kind, relationship.id);
    if (
      relationship.containment === true ||
      configuredContainmentKinds.has(String(relationship.kind || "").toUpperCase())
    ) {
      addToIndex(
        graph.containmentByParent,
        relationship.sourceElementId,
        relationship.targetElementId,
      );
      if (relationship.targetElementId && !graph.parentByChild.has(relationship.targetElementId)) {
        graph.parentByChild.set(relationship.targetElementId, relationship.sourceElementId);
      }
    }
  });

  const boundedContextsByName = new Map();
  const contextTypes = boundedContextTypes();
  graph.elementsById.forEach((element) => {
    if (!contextTypes.has(semanticType(element))) {
      return;
    }
    const name = semanticLabel(element).trim().toLowerCase();
    if (name && element.id) {
      boundedContextsByName.set(name, element);
    }
  });

  graph.elementsById.forEach((element, elementId) => {
    const contextName = String(element?.contextName || element?.context || "").trim();
    if (!contextName) {
      return;
    }
    const parent = boundedContextsByName.get(contextName.toLowerCase());
    if (!parent?.id || parent.id === elementId) {
      return;
    }
    addToIndex(graph.containmentByParent, parent.id, elementId);
    if (!graph.parentByChild.has(elementId)) {
      graph.parentByChild.set(elementId, parent.id);
    }
  });
}

function buildGraph(typeKey, modelJson) {
  const graph = createEmptyGraph();
  const seenElements = new Set();
  const allowedTypes = configuredElementTypes(typeKey);

  elementRecords(modelJson, typeKey).forEach((element, index) => {
    const normalized = normalizeElement(element, index);
    if (allowedTypes.size && !allowedTypes.has(normalized.eClass)) {
      return;
    }
    if (seenElements.has(normalized.id)) {
      console.warn(`Duplicate semantic element skipped: ${normalized.id}`);
      return;
    }
    seenElements.add(normalized.id);
    graph.elementsById.set(normalized.id, normalized);
  });

  relationshipRecords(modelJson, typeKey).forEach((relationship, index) => {
    const normalized = normalizeRelationship(typeKey, relationship, index);
    if (!normalized.sourceElementId || !normalized.targetElementId) {
      return;
    }
    if (
      !graph.elementsById.has(normalized.sourceElementId) ||
      !graph.elementsById.has(normalized.targetElementId)
    ) {
      return;
    }
    graph.relationshipsById.set(normalized.id, normalized);
  });

  if (isModelingLevel(typeKey)) {
    synthesizeContainmentRelationships(graph, typeKey);
    synthesizeSemanticRefRelationships(graph, typeKey);
  }

  safeArray(modelJson?.graph?.traceLinks || modelJson?.traceLinks).forEach((traceLink, index) => {
    const id = String(traceLink?.id || `trace-${index + 1}`);
    graph.traceLinksById.set(id, { ...clone(traceLink), id });
  });
  safeArray(modelJson?.graph?.assumptions || modelJson?.assumptions).forEach(
    (assumption, index) => {
      const id = String(assumption?.id || `assumption-${index + 1}`);
      graph.assumptionsById.set(id, { ...clone(assumption), id });
    },
  );
  graph.validationIssues = safeArray(
    modelJson?.graph?.validationIssues || modelJson?.validationIssues,
  ).map(clone);
  graph.manualBacklog = safeArray(
    Array.isArray(modelJson?.manualBacklog)
      ? modelJson.manualBacklog
      : modelJson?.graph?.manualBacklog,
  ).map(clone);

  rebuildGraphIndexes(graph);
  return graph;
}

function viewDefinitions(typeKey) {
  try {
    return safeArray(modelingLevelConfig(typeKey).viewDefinitions);
  } catch {
    return [];
  }
}

function matchingViewDefinition(typeKey, view) {
  const definitions = viewDefinitions(typeKey);
  const definitionId = String(view?.definitionId || view?.sourceDefinitionId || "")
    .trim()
    .toLowerCase();
  const viewpoint = String(view?.viewpoint || "")
    .trim()
    .toLowerCase();
  const kind = String(view?.kind || view?.viewType || "")
    .trim()
    .toLowerCase();
  const nameKey = normalizeViewName(view?.name || view?.displayName || "");
  return (
    definitions.find((definition) => {
      const id = String(definition?.id || "")
        .trim()
        .toLowerCase();
      const definitionViewpoint = String(definition?.viewpoint || "")
        .trim()
        .toLowerCase();
      const viewType = String(definition?.viewType || "")
        .trim()
        .toLowerCase();
      const displayName = normalizeViewName(definition?.displayName || definition?.name || "");
      return (
        (definitionId && id === definitionId) ||
        (viewpoint && definitionViewpoint === viewpoint) ||
        (kind && (id === kind || viewType === kind)) ||
        (nameKey && displayName === nameKey)
      );
    }) || null
  );
}

function configuredElementTypes(typeKey) {
  try {
    return new Set(
      [
        ...safeArray(modelingLevelConfig(typeKey).elements)
          .map((entry) => String(entry?.type || "").trim())
          .filter(Boolean),
        rootScopeType(typeKey),
      ].filter(Boolean),
    );
  } catch {
    return new Set();
  }
}

function edgeIdsForElementIds(graph, elementIds, relationshipKinds = []) {
  const ids = new Set(elementIds);
  const allowedKinds = new Set(
    safeArray(relationshipKinds)
      .map((kind) =>
        String(kind || "")
          .trim()
          .toUpperCase(),
      )
      .filter(Boolean),
  );
  const result = [];
  graph.relationshipsById.forEach((relationship) => {
    if (!ids.has(relationship.sourceElementId) || !ids.has(relationship.targetElementId)) {
      return;
    }
    if (!relationshipKindMatches(relationship.kind, allowedKinds)) {
      return;
    }
    result.push(relationship.id);
  });
  return result;
}

function withRelationshipEndpoints(graph, elementIds, relationshipIds = []) {
  const expanded = new Set(elementIds);
  safeArray(relationshipIds).forEach((relationshipId) => {
    const relationship = graph.relationshipsById.get(relationshipId);
    if (!relationship) {
      return;
    }
    if (relationship.sourceElementId) {
      expanded.add(relationship.sourceElementId);
    }
    if (relationship.targetElementId) {
      expanded.add(relationship.targetElementId);
    }
  });
  return [...expanded];
}

function relationshipIdsTouchingElements(graph, elementIds, relationshipKinds = []) {
  const ids = new Set(elementIds);
  const allowedKinds = new Set(
    safeArray(relationshipKinds)
      .map((kind) =>
        String(kind || "")
          .trim()
          .toUpperCase(),
      )
      .filter(Boolean),
  );
  const result = [];
  graph.relationshipsById.forEach((relationship) => {
    if (allowedKinds.size && !relationshipKindMatches(relationship.kind, allowedKinds)) {
      return;
    }
    if (
      !ids.size ||
      ids.has(relationship.sourceElementId) ||
      ids.has(relationship.targetElementId)
    ) {
      result.push(relationship.id);
    }
  });
  return result;
}

function neighborhoodElementIds(graph, rootElementId, depth) {
  const rootId = String(rootElementId || "").trim();
  if (!rootId || !graph.elementsById.has(rootId)) {
    return new Set(graph.elementsById.keys());
  }
  const maxDepth = Math.max(0, Number(depth ?? 1));
  const result = new Set([rootId]);
  const queue = [{ id: rootId, depth: 0 }];
  while (queue.length) {
    const current = queue.shift();
    if (current.depth >= maxDepth) {
      continue;
    }
    const connected = new Set();
    graph.relationshipsBySource.get(current.id)?.forEach((relationshipId) => {
      const relationship = graph.relationshipsById.get(relationshipId);
      if (relationship?.targetElementId) {
        connected.add(relationship.targetElementId);
      }
    });
    graph.relationshipsByTarget.get(current.id)?.forEach((relationshipId) => {
      const relationship = graph.relationshipsById.get(relationshipId);
      if (relationship?.sourceElementId) {
        connected.add(relationship.sourceElementId);
      }
    });
    graph.containmentByParent.get(current.id)?.forEach((childId) => connected.add(childId));
    connected.forEach((nextId) => {
      if (!graph.elementsById.has(nextId) || result.has(nextId)) {
        return;
      }
      result.add(nextId);
      queue.push({ id: nextId, depth: current.depth + 1 });
    });
  }
  return result;
}

function containedDescendantElementIds(graph, rootElementId) {
  const rootId = String(rootElementId || "").trim();
  const result = new Set();
  if (!rootId) {
    return result;
  }
  const queue = [...(graph.containmentByParent?.get(rootId) || [])];
  while (queue.length) {
    const childId = String(queue.shift() || "").trim();
    if (!childId || result.has(childId) || !graph.elementsById.has(childId)) {
      continue;
    }
    result.add(childId);
    const nested = graph.containmentByParent?.get(childId);
    if (nested?.size) {
      nested.forEach((nestedId) => queue.push(nestedId));
    }
  }
  return result;
}

export function selectElementIdsForView(graph, view, typeKey) {
  const hidden = new Set(safeArray(view?.hidden?.elementIds));
  const pinned = new Set(safeArray(view?.pinnedElementIds).map(String));
  const filterTypes = new Set(safeArray(view?.filters?.elementTypes));
  const metadataBacked = Boolean(matchingViewDefinition(typeKey, view));
  let candidates;
  const explicitNodeIds = safeArray(view?.nodes)
    .map((node) => String(node?.elementId || node?.id || ""))
    .filter(Boolean);
  const rootType = rootScopeType(typeKey);
  const hasOnlyRootExplicitNodes =
    explicitNodeIds.length > 0 &&
    explicitNodeIds.every(
      (elementId) => semanticType(graph.elementsById.get(elementId)) === rootType,
    );
  const hasSemanticFilters = Boolean(
    filterTypes.size || safeArray(view?.filters?.relationshipKinds).length,
  );
  const hasExplicitNodes =
    explicitNodeIds.length > 0 &&
    !metadataBacked &&
    !(hasOnlyRootExplicitNodes && hasSemanticFilters);
  if (hasExplicitNodes) {
    candidates = new Set(explicitNodeIds);
  } else if (
    String(view?.scope?.scopeKind || "").toUpperCase() === "CONTAINER" &&
    view?.scope?.rootElementId
  ) {
    candidates = containedDescendantElementIds(graph, view.scope.rootElementId);
  } else if (view?.scope?.rootElementId) {
    candidates = neighborhoodElementIds(
      graph,
      view.scope.rootElementId,
      view?.scope?.depth ?? view?.defaultDepth ?? 1,
    );
  } else {
    candidates = new Set(graph.elementsById.keys());
  }
  pinned.forEach((elementId) => {
    if (graph.elementsById.has(elementId)) {
      candidates.add(elementId);
    }
  });
  const selected = [];
  candidates.forEach((elementId) => {
    const element = graph.elementsById.get(elementId);
    if (!element || hidden.has(elementId)) {
      return;
    }
    if (hasExplicitNodes) {
      selected.push(elementId);
      return;
    }
    if (pinned.has(elementId)) {
      selected.push(elementId);
      return;
    }
    const includeByType =
      elementMatchesFilterTypes(element, filterTypes, typeKey) ||
      elementId === view?.scope?.rootElementId;
    if (includeByType) {
      selected.push(elementId);
    }
  });
  if (!selected.length && typeKey && !filterTypes.size && !view?.scope?.rootElementId) {
    return [...graph.elementsById.keys()].filter((elementId) => !hidden.has(elementId));
  }
  const selectedSet = new Set(selected);
  [...selected].forEach((elementId) => {
    let parentId = graph.parentByChild.get(elementId);
    while (parentId && !selectedSet.has(parentId) && !hidden.has(parentId)) {
      const parent = graph.elementsById.get(parentId);
      if (!parent || semanticType(parent) === rootScopeType(typeKey)) {
        break;
      }
      if (!elementMatchesFilterTypes(parent, filterTypes, typeKey)) {
        parentId = graph.parentByChild.get(parentId);
        continue;
      }
      selected.push(parentId);
      selectedSet.add(parentId);
      parentId = graph.parentByChild.get(parentId);
    }
  });
  const relationshipKinds = safeArray(view?.filters?.relationshipKinds);
  if (relationshipKinds.length && selected.length) {
    relationshipIdsTouchingElements(graph, selected, relationshipKinds).forEach(
      (relationshipId) => {
        const relationship = graph.relationshipsById.get(relationshipId);
        [relationship?.sourceElementId, relationship?.targetElementId].forEach((elementId) => {
          if (
            elementId &&
            graph.elementsById.has(elementId) &&
            !hidden.has(elementId) &&
            !selectedSet.has(elementId)
          ) {
            selectedSet.add(elementId);
            selected.push(elementId);
          }
        });
      },
    );
  }
  return selected;
}

export function selectRelationshipIdsForView(graph, view, elementIds) {
  const hidden = new Set(safeArray(view?.hidden?.relationshipIds));
  const filterKinds = new Set(
    safeArray(view?.filters?.relationshipKinds)
      .map((kind) =>
        String(kind || "")
          .trim()
          .toUpperCase(),
      )
      .filter(Boolean),
  );
  const elementSet = new Set(elementIds);
  const explicitEdgeVisibility = new Map(
    safeArray(view?.edges).map((edge) => [edge.relationshipId, edge]),
  );
  const relationshipIds = [];
  graph.relationshipsById.forEach((relationship, relationshipId) => {
    if (hidden.has(relationshipId)) {
      return;
    }
    const explicit = explicitEdgeVisibility.get(relationshipId);
    if (explicit && explicit.visible === false) {
      return;
    }
    if (
      !elementSet.has(relationship.sourceElementId) ||
      !elementSet.has(relationship.targetElementId)
    ) {
      return;
    }
    if (filterKinds.size && !relationshipKindMatches(relationship.kind, filterKinds)) {
      return;
    }
    relationshipIds.push(relationshipId);
  });
  return relationshipIds;
}

function layoutNodesForElements(graph, elementIds, existingNodes = [], typeKey = state.activeType) {
  const existingByElement = new Map(safeArray(existingNodes).map((node) => [node.elementId, node]));
  const nodes = elementIds.map((elementId, _index) => {
    const element = graph.elementsById.get(elementId);
    const existing = existingByElement.get(elementId);
    const x = Number.isFinite(Number(existing?.x))
      ? Number(existing.x)
      : Number.isFinite(Number(element?.x))
        ? Number(element.x)
        : 0;
    const y = Number.isFinite(Number(existing?.y))
      ? Number(existing.y)
      : Number.isFinite(Number(element?.y))
        ? Number(element.y)
        : 0;
    return {
      elementId,
      x,
      y,
      width: existing?.width,
      height: existing?.height,
    };
  });
  const allNodesAlreadyPositioned =
    nodes.length > 0 &&
    nodes.every(
      (node) =>
        Number.isFinite(Number(node.x)) &&
        Number.isFinite(Number(node.y)) &&
        existingByElement.has(node.elementId),
    );
  if (allNodesAlreadyPositioned) {
    return nodes;
  }
  const fakeNodes = nodes.map((node) => ({
    id: node.elementId,
    x: node.x,
    y: node.y,
    width: node.width,
    height: node.height,
  }));
  const elementIdSet = new Set(elementIds);
  const fakeEdges = [...graph.relationshipsById.values()]
    .filter(
      (relationship) =>
        elementIdSet.has(relationship.sourceElementId) &&
        elementIdSet.has(relationship.targetElementId),
    )
    .map((relationship) => ({
      sourceId: relationship.sourceElementId,
      targetId: relationship.targetElementId,
    }));
  ensureReadableLayout(fakeNodes, fakeEdges, nodeSizeForType(typeKey));
  fakeNodes.forEach((node, index) => {
    nodes[index].x = node.x;
    nodes[index].y = node.y;
  });
  return nodes;
}

function isMainSurfaceElement(typeKey, element) {
  const type = semanticType(element);
  if (mainSurfaceRootTypes(typeKey).has(type)) {
    return true;
  }
  try {
    const definition = modelingElementDefinition(typeKey, type);
    if (definition?.relationshipElement || definition?.containedOnly || definition?.supportOnly) {
      return false;
    }
    if (definition?.creatable) {
      return true;
    }
    return false;
  } catch {
    return true;
  }
}

function buildViewFromDefinition(typeKey, graph, definition, scopeElement = null) {
  const idParts = [
    "view",
    typeKey,
    definition.id || definition.displayName || "definition",
    scopeElement?.id || "global",
  ];
  const id = idParts.map(sanitizeIdPart).join("-");
  const scope = scopeElement
    ? {
        rootElementId: scopeElement.id,
        scopeKind:
          modelingElementDefinition(typeKey, semanticType(scopeElement))?.notation?.fragmentKind ||
          semanticType(scopeElement)
            .replaceAll(/([a-z0-9])([A-Z])/g, "$1_$2")
            .toUpperCase(),
        depth: definition.defaultDepth ?? 2,
      }
    : {
        scopeKind: "MODEL",
        depth: definition.defaultDepth ?? 1,
      };
  const view = {
    id,
    name: scopeElement
      ? `${semanticLabel(scopeElement)} - ${definition.displayName || id}`
      : definition.displayName || id,
    level: modelLevelValue(typeKey),
    kind: String(definition.id || "VIEW")
      .toUpperCase()
      .replaceAll("-", "_"),
    scope,
    filters: {
      elementTypes: safeArray(definition.elementTypes),
      relationshipKinds: safeArray(definition.relationshipKinds),
    },
    definitionId: String(definition.id || ""),
    viewpoint: String(definition.viewpoint || ""),
    description: String(definition.description || ""),
    palette: safeArray(definition.palette),
    pinnedElementIds: [],
    edgeLayers: safeArray(definition.edgeLayers),
    layoutProfile: definition.layoutProfile || definition.layoutHint || "DEFAULT_LAYERED",
    defaultDepth: definition.defaultDepth ?? 1,
    nodes: [],
    edges: [],
    hidden: { elementIds: [], relationshipIds: [] },
  };
  let elementIds = selectElementIdsForView(graph, view, typeKey);
  const relationshipKinds = safeArray(definition.relationshipKinds);
  if (relationshipKinds.length) {
    elementIds = withRelationshipEndpoints(
      graph,
      elementIds,
      relationshipIdsTouchingElements(graph, elementIds, relationshipKinds),
    );
  }
  const relationshipIds = selectRelationshipIdsForView(graph, view, elementIds);
  view.nodes = layoutNodesForElements(graph, elementIds, [], typeKey);
  view.edges = relationshipIds.map((relationshipId) => ({
    relationshipId,
    visible: true,
  }));
  return view;
}

function defaultMainView(typeKey, graph, modelName) {
  const configuredContainmentKinds = containmentKinds(typeKey);
  let elementIds = [...graph.elementsById.entries()]
    .filter(([, element]) => isMainSurfaceElement(typeKey, element))
    .map(([elementId]) => elementId);
  if (modelingLevelConfig(typeKey).canvasPolicy?.includeRelationshipEndpointsInMainView) {
    elementIds = withRelationshipEndpoints(
      graph,
      elementIds,
      relationshipIdsTouchingElements(graph, elementIds).filter((relationshipId) => {
        const relationship = graph.relationshipsById.get(relationshipId);
        return (
          relationship &&
          relationship.containment !== true &&
          !configuredContainmentKinds.has(String(relationship.kind || "").toUpperCase())
        );
      }),
    );
  }
  const relationshipIds = edgeIdsForElementIds(graph, elementIds);
  return {
    id: `view-${typeKey}-main`,
    name: `${modelName || typeKey.toUpperCase()} Main View`,
    level: modelLevelValue(typeKey),
    kind: "MAIN",
    scope: { scopeKind: "MODEL" },
    filters: {
      elementTypes: [],
      relationshipKinds: [],
    },
    layoutProfile: "DEFAULT_LAYERED",
    nodes: layoutNodesForElements(graph, elementIds, [], typeKey),
    edges: relationshipIds.map((relationshipId) => ({
      relationshipId,
      visible: true,
    })),
    hidden: { elementIds: [], relationshipIds: [] },
    pinnedElementIds: [],
  };
}

function generateViews(typeKey, graph, modelName) {
  const views = [defaultMainView(typeKey, graph, modelName)];
  const definitions = viewDefinitions(typeKey);
  definitions.forEach((definition) => {
    views.push(buildViewFromDefinition(typeKey, graph, definition));
    const scopeTypes = new Set(safeArray(definition.scopeTypes));
    graph.elementsById.forEach((element) => {
      if (!scopeTypes.has(semanticType(element))) {
        return;
      }
      const view = buildViewFromDefinition(typeKey, graph, definition, element);
      if (view.nodes.length > 1 || view.edges.length) {
        views.push(view);
      }
    });
  });
  const seen = new Set();
  return views.filter((view) => {
    if (seen.has(view.id)) {
      return false;
    }
    seen.add(view.id);
    return true;
  });
}

function generateGlobalViews(typeKey, graph, modelName) {
  return [
    defaultMainView(typeKey, graph, modelName),
    ...viewDefinitions(typeKey).map((definition) =>
      buildViewFromDefinition(typeKey, graph, definition),
    ),
  ];
}

function normalizeView(view, graph, typeKey, modelName) {
  const normalized = {
    id: String(view?.id || genId("view")),
    name: String(view?.name || modelName || "View"),
    level: String(view?.level || modelLevelValue(typeKey)),
    kind: String(view?.kind || "MAIN"),
    scope: clone(view?.scope || { scopeKind: "MODEL" }),
    filters: clone(view?.filters || {}),
    definitionId: String(view?.definitionId || view?.sourceDefinitionId || ""),
    viewpoint: String(view?.viewpoint || ""),
    description: String(view?.description || ""),
    sourceViewId: String(view?.sourceViewId || ""),
    savedAt: String(view?.savedAt || ""),
    autoLayoutApplied: Boolean(view?.autoLayoutApplied),
    camera:
      view?.camera && typeof view.camera === "object"
        ? {
            x: Number(view.camera.x) || 0,
            y: Number(view.camera.y) || 0,
            scale: Number.isFinite(Number(view.camera.scale)) ? Number(view.camera.scale) : 1,
          }
        : null,
    palette: safeArray(view?.palette).map(String),
    pinnedElementIds: safeArray(view?.pinnedElementIds).map(String),
    edgeLayers: safeArray(view?.edgeLayers).map(String),
    layoutProfile: String(view?.layoutProfile || "DEFAULT_LAYERED"),
    defaultDepth: view?.defaultDepth,
    nodes: safeArray(view?.nodes)
      .map((node) => ({
        elementId: String(node?.elementId || node?.id || ""),
        x: Number(node?.x) || 0,
        y: Number(node?.y) || 0,
        width: Number.isFinite(Number(node?.width)) ? Number(node.width) : undefined,
        height: Number.isFinite(Number(node?.height)) ? Number(node.height) : undefined,
      }))
      .filter((node) => graph.elementsById.has(node.elementId)),
    edges: safeArray(view?.edges)
      .map((edge) => ({
        ...clone(edge),
        relationshipId: String(edge?.relationshipId || edge?.id || ""),
      }))
      .filter((edge) => graph.relationshipsById.has(edge.relationshipId)),
    hidden: {
      elementIds: safeArray(view?.hidden?.elementIds).map(String),
      relationshipIds: safeArray(view?.hidden?.relationshipIds).map(String),
    },
  };
  if (!normalized.filters || typeof normalized.filters !== "object") {
    normalized.filters = {};
  }
  normalized.filters.elementTypes = safeArray(normalized.filters.elementTypes).map(String);
  normalized.filters.relationshipKinds = safeArray(normalized.filters.relationshipKinds).map(
    String,
  );
  const definition = matchingViewDefinition(typeKey, normalized);
  normalized.pinnedElementIds = normalized.pinnedElementIds.filter((elementId) =>
    graph.elementsById.has(elementId),
  );
  if (definition) {
    if (!normalized.definitionId) {
      normalized.definitionId = String(definition.id || "");
    }
    if (!normalized.viewpoint) {
      normalized.viewpoint = String(definition.viewpoint || "");
    }
    normalized.filters.elementTypes = [
      ...new Set([
        ...normalized.filters.elementTypes,
        ...safeArray(definition.elementTypes).map(String),
        ...safeArray(definition.palette).map(String),
      ]),
    ];
    normalized.filters.relationshipKinds = [
      ...new Set([
        ...normalized.filters.relationshipKinds,
        ...safeArray(definition.relationshipKinds).map(String),
      ]),
    ];
    if (!view?.layoutProfile && (definition.layoutProfile || definition.layoutHint)) {
      normalized.layoutProfile = String(definition.layoutProfile || definition.layoutHint);
    }
  }
  if (graph.elementsById.size) {
    let elementIds =
      definition || !normalized.nodes.length
        ? selectElementIdsForView(graph, normalized, typeKey)
        : normalized.nodes.map((node) => node.elementId);
    const explicitRelationshipIds = normalized.edges.map((edge) => edge.relationshipId);
    elementIds = withRelationshipEndpoints(graph, elementIds, [
      ...explicitRelationshipIds,
      ...relationshipIdsTouchingElements(graph, elementIds, normalized.filters.relationshipKinds),
    ]);
    normalized.nodes = layoutNodesForElements(graph, elementIds, normalized.nodes, typeKey);
  }
  if (!normalized.edges.length && graph.relationshipsById.size) {
    const elementIds = normalized.nodes.map((node) => node.elementId);
    normalized.edges = selectRelationshipIdsForView(graph, normalized, elementIds).map(
      (relationshipId) => ({ relationshipId, visible: true }),
    );
  }
  return normalized;
}

function buildViews(typeKey, graph, modelJson, fallbackName) {
  const rawViews = safeArray(modelJson?.views);
  if (rawViews.length) {
    // Persisted models only need missing global defaults filled in. Generating every
    // scoped view here performs repeated selection and layout work, then discards
    // those scoped views below.
    const generatedViews = generateGlobalViews(typeKey, graph, modelJson?.name || fallbackName);
    const normalizedViews = rawViews
      .map((view) => normalizeView(view, graph, typeKey, fallbackName))
      .filter((view) => viewBelongsToLevel(view, typeKey) && !isFocusView(view));
    const existingKeys = new Set();
    normalizedViews.forEach((view) => {
      existingKeys.add(view.id);
      existingKeys.add(normalizeViewName(view.name));
    });
    generatedViews
      .filter((view) => !view.scope?.rootElementId)
      .forEach((view) => {
        const nameKey = normalizeViewName(view.name);
        if (existingKeys.has(view.id) || existingKeys.has(nameKey)) {
          return;
        }
        normalizedViews.push(view);
        existingKeys.add(view.id);
        existingKeys.add(nameKey);
      });
    return normalizedViews.length ? normalizedViews : generatedViews;
  }
  return generateViews(typeKey, graph, modelJson?.name || fallbackName);
}

function directChildrenOf(graph, parentId) {
  return new Set(graph.containmentByParent.get(parentId) || []);
}

function recursivelyCollectChildren(graph, parentId, into = new Set()) {
  directChildrenOf(graph, parentId).forEach((childId) => {
    if (into.has(childId)) {
      return;
    }
    into.add(childId);
    recursivelyCollectChildren(graph, childId, into);
  });
  return into;
}

function relationshipIdsTouching(graph, elementIds) {
  const ids = new Set(elementIds);
  const result = [];
  graph.relationshipsById.forEach((relationship) => {
    if (ids.has(relationship.sourceElementId) || ids.has(relationship.targetElementId)) {
      result.push(relationship.id);
    }
  });
  return result;
}

function deriveFragments(typeKey, graph, views) {
  const viewsByScope = new Map();
  safeArray(views).forEach((view) => {
    const rootElementId = String(view?.scope?.rootElementId || "");
    if (rootElementId && !viewsByScope.has(rootElementId)) {
      viewsByScope.set(rootElementId, view.id);
    }
  });
  const fragments = [];
  graph.elementsById.forEach((element) => {
    const elementType = semanticType(element);
    const definition = modelingElementDefinition(typeKey, elementType);
    if (definition?.visualRole !== "container") {
      return;
    }
    const elementIds = [...recursivelyCollectChildren(graph, element.id)];
    const relationshipIds = relationshipIdsTouching(graph, elementIds);
    fragments.push({
      id: `fragment-${sanitizeIdPart(element.id)}`,
      name: semanticLabel(element),
      level: modelLevelValue(typeKey),
      fragmentKind:
        definition?.notation?.fragmentKind ||
        elementType.replaceAll(/([a-z0-9])([A-Z])/g, "$1_$2").toUpperCase(),
      ownerElementId: element.id,
      elementIds,
      relationshipIds,
      defaultViewId: viewsByScope.get(element.id) || views[0]?.id || null,
      parentFragmentId: graph.parentByChild.has(element.id)
        ? `fragment-${sanitizeIdPart(graph.parentByChild.get(element.id))}`
        : null,
    });
  });
  return fragments;
}

function buildFragments(typeKey, graph, views, modelJson) {
  const rawFragments = safeArray(modelJson?.fragments);
  if (rawFragments.length) {
    return rawFragments.map((fragment) => ({
      ...clone(fragment),
      id: String(fragment?.id || genId("fragment")),
      elementIds: safeArray(fragment?.elementIds || fragment?.elements).map(String),
      relationshipIds: safeArray(fragment?.relationshipIds || fragment?.relationships).map(String),
    }));
  }
  return deriveFragments(typeKey, graph, views);
}

function installGraph(graph) {
  state.graph = graph;
  rebuildGraphIndexes(state.graph);
}

function mainViewId(typeKey) {
  return `view-${typeKey}-main`;
}

function preferredDefaultViewId(byId, typeKey) {
  const mainId = mainViewId(typeKey);
  const views = [...byId.values()];
  const contentfulNonMain = views.find(
    (view) =>
      view.id !== mainId &&
      !view.scope?.rootElementId &&
      (safeArray(view.nodes).length || safeArray(view.edges).length),
  );
  if (contentfulNonMain) {
    return contentfulNonMain.id;
  }
  const anyNonMain = views.find((view) => view.id !== mainId && !view.scope?.rootElementId);
  if (anyNonMain) {
    return anyNonMain.id;
  }
  return byId.has(mainId) ? mainId : byId.keys().next().value || null;
}

function installViews(views, activeViewId, typeKey = state.activeType, modelName = "") {
  const byId = new Map();
  safeArray(views)
    .filter((view) => viewBelongsToLevel(view, typeKey) && !isFocusView(view))
    .forEach((view) => byId.set(view.id, view));
  if (!byId.size) {
    generateViews(typeKey, state.graph, modelName)
      .filter((view) => !view.scope?.rootElementId)
      .forEach((view) => byId.set(view.id, view));
  }
  const resolvedActive = byId.has(activeViewId)
    ? activeViewId
    : preferredDefaultViewId(byId, typeKey);
  state.views = {
    byId,
    activeViewId: resolvedActive,
    visibleNodeIds: new Set(),
    visibleRelationshipIds: new Set(),
    expandedContainers: new Set(),
  };
  byId.forEach(prepareViewNodeIndex);
}

function installFragments(fragments) {
  const byId = new Map();
  const childIds = new Set();
  safeArray(fragments).forEach((fragment) => {
    byId.set(fragment.id, fragment);
    if (fragment.parentFragmentId) {
      childIds.add(fragment.id);
    }
  });
  state.fragments = {
    byId,
    rootIds: [...byId.keys()].filter((id) => !childIds.has(id)),
  };
}

export function installGraphAndViews(typeKey, modelJson = {}, fallbackName = "") {
  const graph = buildGraph(typeKey, modelJson || {});
  const views = buildViews(typeKey, graph, modelJson || {}, fallbackName);
  const fragments = buildFragments(typeKey, graph, views, modelJson || {});
  installGraph(graph);
  installViews(views, modelJson?.activeViewId || null, typeKey, fallbackName);
  installFragments(fragments);
  return {
    graph: state.graph,
    views: state.views,
    fragments: state.fragments,
  };
}

export function ensureActiveGraphAndViews(typeKey = state.activeType) {
  if (!isModelingLevel(typeKey)) {
    return false;
  }
  const currentView = state.views?.byId?.get(state.views.activeViewId);
  const hasActiveView =
    state.views?.activeViewId && currentView && viewBelongsToLevel(currentView, typeKey);
  if (state.graph?.elementsById instanceof Map && hasActiveView) {
    return false;
  }
  const tab = state.tabs[typeKey] || {};
  const modelJson = state.baseModel ||
    tab.baseModel || {
      name: tab.modelName || `${typeKey}-model`,
      diagram: { elements: [], relationships: [] },
    };
  installGraphAndViews(typeKey, modelJson, tab.modelName || modelJson.name || `${typeKey}-model`);
  return true;
}

export function activeView() {
  return state.views.byId.get(state.views.activeViewId) || null;
}

export function setActiveViewId(viewId) {
  if (!state.views.byId.has(viewId)) {
    return false;
  }
  syncActiveViewFromVisibleGraph();
  state.views.activeViewId = viewId;
  state.selectedNodeId = null;
  state.selectedNodeIds = new Set();
  state.selectedConnectionId = null;
  return true;
}

export function saveCurrentTabGraphState(typeKey = state.activeType) {
  const tab = state.tabs[typeKey];
  if (!tab) {
    return;
  }
  tab.graph = serializeRuntimeGraph();
  tab.views = serializeRuntimeViews();
  tab.fragments = serializeRuntimeFragments();
  tab.activeViewId = state.views.activeViewId;
}

export function restoreTabGraphState(typeKey = state.activeType) {
  const tab = state.tabs[typeKey];
  if (!tab?.graph) {
    installGraphAndViews(typeKey, tab?.baseModel || {}, tab?.modelName || "");
    return;
  }
  installGraph(buildGraphFromSnapshot(tab.graph, typeKey));
  installViews(tab.views || [], tab.activeViewId, typeKey, tab.modelName || "");
  installFragments(tab.fragments || []);
}

function buildGraphFromSnapshot(snapshot, typeKey = state.activeType) {
  const graph = createEmptyGraph();
  const allowedTypes = configuredElementTypes(typeKey);
  safeArray(snapshot?.elements).forEach((element, index) => {
    const normalized = normalizeElement(element, index);
    if (allowedTypes.size && !allowedTypes.has(normalized.eClass)) {
      return;
    }
    graph.elementsById.set(normalized.id, normalized);
  });
  safeArray(snapshot?.relationships).forEach((relationship, index) => {
    const normalized = normalizeRelationship("model", relationship, index);
    if (
      !graph.elementsById.has(normalized.sourceElementId) ||
      !graph.elementsById.has(normalized.targetElementId)
    ) {
      return;
    }
    graph.relationshipsById.set(normalized.id, normalized);
  });
  safeArray(snapshot?.traceLinks).forEach((traceLink, index) => {
    const id = String(traceLink?.id || `trace-${index + 1}`);
    graph.traceLinksById.set(id, { ...clone(traceLink), id });
  });
  safeArray(snapshot?.assumptions).forEach((assumption, index) => {
    const id = String(assumption?.id || `assumption-${index + 1}`);
    graph.assumptionsById.set(id, { ...clone(assumption), id });
  });
  graph.validationIssues = safeArray(snapshot?.validationIssues).map(clone);
  graph.manualBacklog = safeArray(snapshot?.manualBacklog).map(clone);
  rebuildGraphIndexes(graph);
  return graph;
}

export function serializeRuntimeGraph() {
  return {
    elements: [...state.graph.elementsById.values()].map(clone),
    relationships: [...state.graph.relationshipsById.values()].map(clone),
    traceLinks: [...state.graph.traceLinksById.values()].map(clone),
    assumptions: [...state.graph.assumptionsById.values()].map(clone),
    validationIssues: safeArray(state.graph.validationIssues).map(clone),
    manualBacklog: safeArray(state.graph.manualBacklog).map(clone),
  };
}

function manualBacklogKey(task, _index) {
  const explicit = String(task?.id || "").trim();
  if (explicit) {
    return `id:${explicit}`;
  }
  const title = String(task?.name || task?.title || "")
    .trim()
    .toLowerCase();
  const elementId = String(
    task?.elementId ||
      task?.relatedElementId ||
      task?.targetElementId ||
      task?.sourceElementId ||
      "",
  )
    .trim()
    .toLowerCase();
  const category = String(task?.category || "")
    .trim()
    .toLowerCase();
  return `fallback:${category}:${title}:${elementId}`;
}

function mergeManualBacklog(primary, secondary) {
  const merged = [];
  const seen = new Set();
  safeArray(primary).forEach((task, index) => {
    const key = manualBacklogKey(task, index);
    if (seen.has(key)) {
      return;
    }
    seen.add(key);
    merged.push(clone(task));
  });
  safeArray(secondary).forEach((task, index) => {
    const key = manualBacklogKey(task, index);
    if (seen.has(key)) {
      return;
    }
    seen.add(key);
    merged.push(clone(task));
  });
  return merged;
}

export function serializeRuntimeViews() {
  return [...state.views.byId.values()].filter((view) => !isFocusView(view)).map(clone);
}

export function serializeRuntimeFragments() {
  return [...state.fragments.byId.values()].map(clone);
}

export function syncActiveViewFromVisibleGraph({ rebuildIndexes = true } = {}) {
  const view = activeView();
  if (!view || !state.diagram) {
    return;
  }
  const viewNodesById = new Map(safeArray(view.nodes).map((node) => [node.elementId, node]));
  safeArray(state.diagram.nodes).forEach((node) => {
    const element =
      state.graph.elementsById.get(node.id) ||
      normalizeElement({
        ...node.meta,
        id: node.id,
        eClass: node.type,
        name: node.label,
        label: node.label,
      });
    element.eClass = node.type || element.eClass;
    element.name = node.label || element.name;
    element.label = node.label || element.label;
    const meta = clone(node.meta || {});
    Object.assign(element, meta, {
      id: node.id,
      eClass: node.type || element.eClass,
      name: node.label || element.name,
      label: node.label || element.label,
      x: node.x,
      y: node.y,
    });
    state.graph.elementsById.set(node.id, element);
    const viewNode = viewNodesById.get(node.id) || { elementId: node.id };
    viewNode.x = node.x;
    viewNode.y = node.y;
    viewNodesById.set(node.id, viewNode);
  });
  view.nodes = [...viewNodesById.values()].filter((node) =>
    state.graph.elementsById.has(node.elementId),
  );
  viewNodeIndexes.set(view, new Map(view.nodes.map((node) => [node.elementId, node])));

  const viewEdgesById = new Map(safeArray(view.edges).map((edge) => [edge.relationshipId, edge]));
  safeArray(state.diagram.connections).forEach((edge) => {
    if (edge.bundle || String(edge.kind).toUpperCase() === "EDGE_BUNDLE") {
      return;
    }
    const relationship = state.graph.relationshipsById.get(edge.id) || {
      id: edge.id,
      kind: edge.kind,
      source: edge.sourceId,
      target: edge.targetId,
      sourceElementId: edge.sourceId,
      targetElementId: edge.targetId,
    };
    relationship.kind = edge.kind;
    relationship.source = edge.sourceId;
    relationship.target = edge.targetId;
    relationship.sourceElementId = edge.sourceId;
    relationship.targetElementId = edge.targetId;
    state.graph.relationshipsById.set(edge.id, relationship);

    const viewEdge = viewEdgesById.get(edge.id) || {
      relationshipId: edge.id,
      visible: true,
    };
    viewEdge.visible = true;
    viewEdge.pinPoints = safeArray(edge.pinPoints).map(clone);
    viewEdge.sourceAnchor = edge.sourceAnchor ? clone(edge.sourceAnchor) : undefined;
    viewEdge.targetAnchor = edge.targetAnchor ? clone(edge.targetAnchor) : undefined;
    viewEdgesById.set(edge.id, viewEdge);
  });
  view.edges = [...viewEdgesById.values()].filter((edge) =>
    state.graph.relationshipsById.has(edge.relationshipId),
  );
  if (rebuildIndexes) {
    rebuildGraphIndexes(state.graph);
  }
}

export function persistNodePositionInActiveView(node) {
  const view = activeView();
  if (!view || !node?.id) {
    return false;
  }
  const index = viewNodeIndexes.get(view) || prepareViewNodeIndex(view);
  let viewNode = index.get(node.id);
  if (!viewNode) {
    viewNode = { elementId: node.id };
    view.nodes = [...safeArray(view.nodes), viewNode];
    index.set(node.id, viewNode);
  }
  viewNode.x = node.x;
  viewNode.y = node.y;
  return true;
}

export function serializeGraphAndViewsInto(root) {
  syncActiveViewFromVisibleGraph();
  const graph = serializeRuntimeGraph();
  const manualBacklog = mergeManualBacklog(root.manualBacklog, graph.manualBacklog);
  graph.manualBacklog = manualBacklog.map(clone);
  graph.validationIssues = [];
  state.graph.manualBacklog = manualBacklog.map(clone);
  state.graph.validationIssues = [];
  root.graph = graph;
  root.fragments = serializeRuntimeFragments();
  const views = serializeRuntimeViews();
  root.views = views;
  const currentView = activeView();
  const focusStack = Array.isArray(state.canvasFocusStack) ? state.canvasFocusStack : [];
  const focusBaseViewId = focusStack[focusStack.length - 1]?.previousViewId;
  root.activeViewId = isFocusView(currentView)
    ? focusBaseViewId || views[0]?.id || null
    : state.views.activeViewId;
  root.traceLinks = graph.traceLinks;
  root.assumptions = graph.assumptions;
  root.validationIssues = [];
  root.manualBacklog = manualBacklog;
  delete root.diagram;
  if (isModelingLevel(state.activeType)) {
    populateRootContainments(state.activeType, root, state.graph);
  }
  return root;
}

function stripConfiguredRuntimeFields(element, typeKey) {
  const copyElement = clone(element) || {};
  const type = semanticType(copyElement);
  [
    "x",
    "y",
    "label",
    "status",
    "tags",
    "visualOnly",
    "bundle",
    "countsByKind",
    "underlyingRelationshipIds",
    "sourceType",
    "targetType",
    "semanticFeature",
    "semanticSourceElementId",
    "semanticTargetElementId",
    "semanticDirection",
    "rootFeature",
  ].forEach((key) => delete copyElement[key]);
  const semanticKindAttribute = safeArray(
    modelingElementDefinition(typeKey, type)?.attributes,
  ).some((attribute) => attribute?.name === "kind");
  if (!semanticKindAttribute) {
    delete copyElement.kind;
  }
  try {
    (modelingElementDefinition(typeKey, type)?.references || [])
      .filter((reference) => reference?.readonly)
      .forEach((reference) => delete copyElement[reference.name]);
  } catch {
    // keep best-effort serialization when config is unavailable
  }
  delete copyElement.__ownerId;
  delete copyElement.__containmentFeature;
  return copyElement;
}

function elementMatchesAnyType(typeKey, element, types) {
  return safeArray(types).some((type) => {
    try {
      return modelingTypeMatches(typeKey, type, semanticType(element));
    } catch {
      return type === semanticType(element);
    }
  });
}

function elementReferencesId(element, id) {
  return Object.values(element || {}).some((value) => referenceIds(value).includes(id));
}

function configuredChildren(parent, entry, elements, typeKey) {
  const explicitIds = new Set(referenceIds(parent?.[entry.feature]));
  return elements.filter((candidate) => {
    if (candidate.id === parent.id || !elementMatchesAnyType(typeKey, candidate, entry.types)) {
      return false;
    }
    if (candidate.__ownerId === parent.id && candidate.__containmentFeature === entry.feature) {
      return true;
    }
    if (explicitIds.has(candidate.id)) {
      return true;
    }
    return elementReferencesId(candidate, parent.id);
  });
}

function attachConfiguredContainments(
  copyElement,
  sourceElement,
  elements,
  typeKey,
  visited = new Set(),
) {
  const key = `${sourceElement.id}:${semanticType(sourceElement)}`;
  if (visited.has(key)) {
    return;
  }
  visited.add(key);
  configuredContainments(typeKey, semanticType(sourceElement)).forEach((entry) => {
    if (entry.relationshipOnly) {
      return;
    }
    let children = configuredChildren(sourceElement, entry, elements, typeKey);
    if (!children.length && entry.required && sourceElement.id && entry.many !== false) {
      children = elements.filter(
        (candidate) =>
          !candidate.__ownerId && elementMatchesAnyType(typeKey, candidate, entry.types),
      );
    }
    const copies = children.map((child) => {
      const childCopy = stripConfiguredRuntimeFields(child, typeKey);
      attachConfiguredContainments(childCopy, child, elements, typeKey, visited);
      return childCopy;
    });
    copyElement[entry.feature] = entry.singleton ? copies[0] || null : copies;
  });
}

function _populateConfiguredRootContainments(typeKey, root, graph) {
  const elements = [...graph.elementsById.values()];
  const rootType = modelingRootType(typeKey);
  root.eClass ||= rootType;
  try {
    (modelingElementDefinition(typeKey, rootType)?.references || [])
      .filter((reference) => reference?.readonly)
      .forEach((reference) => delete root[reference.name]);
  } catch {
    // keep serialization best-effort when metadata cannot be resolved
  }
  modelingRootContainments(typeKey).forEach((entry) => {
    if (entry.relationshipOnly) {
      return;
    }
    const owned = elements.filter(
      (element) => !element.__ownerId && elementMatchesAnyType(typeKey, element, entry.types),
    );
    const copies = owned.map((element) => {
      const copyElement = stripConfiguredRuntimeFields(element, typeKey);
      attachConfiguredContainments(copyElement, element, elements, typeKey);
      return copyElement;
    });
    root[entry.feature] = entry.singleton ? copies[0] || null : copies;
  });
}

export function addNodeToGraphAndActiveView(node) {
  if (!node?.id) {
    return;
  }
  const element = normalizeElement({
    ...node.meta,
    id: node.id,
    eClass: node.type,
    name: node.label,
    label: node.label,
    x: node.x,
    y: node.y,
  });
  state.graph.elementsById.set(node.id, element);
  const view = activeView();
  const viewNodeIndex = view ? viewNodeIndexes.get(view) || prepareViewNodeIndex(view) : null;
  if (view && !viewNodeIndex.has(node.id)) {
    const viewNode = { elementId: node.id, x: node.x, y: node.y };
    view.nodes.push(viewNode);
    viewNodeIndex.set(node.id, viewNode);
  }
  if (view) {
    view.pinnedElementIds = [...new Set([...safeArray(view.pinnedElementIds), node.id])];
    view.hidden ??= { elementIds: [], relationshipIds: [] };
    view.hidden.elementIds = safeArray(view.hidden.elementIds).filter((id) => id !== node.id);
  }
}

export function removeElementFromGraph(elementId) {
  const id = String(elementId || "");
  if (!id) {
    return;
  }
  state.graph.elementsById.delete(id);
  const relationshipIds = [];
  state.graph.relationshipsById.forEach((relationship, relationshipId) => {
    if (relationship.sourceElementId === id || relationship.targetElementId === id) {
      relationshipIds.push(relationshipId);
    }
  });
  relationshipIds.forEach((relationshipId) => state.graph.relationshipsById.delete(relationshipId));
  state.views.byId.forEach((view) => {
    view.nodes = safeArray(view.nodes).filter((node) => node.elementId !== id);
    viewNodeIndexes.delete(view);
    view.edges = safeArray(view.edges).filter(
      (edge) => !relationshipIds.includes(edge.relationshipId),
    );
    view.hidden = view.hidden || { elementIds: [], relationshipIds: [] };
    view.hidden.elementIds = safeArray(view.hidden.elementIds).filter((item) => item !== id);
    view.hidden.relationshipIds = safeArray(view.hidden.relationshipIds).filter(
      (item) => !relationshipIds.includes(item),
    );
    view.pinnedElementIds = safeArray(view.pinnedElementIds).filter((item) => item !== id);
  });
  rebuildGraphIndexes(state.graph);
}

function normalizeOppositeName(value) {
  const raw = String(value || "").trim();
  if (!raw) {
    return "";
  }
  const hashIndex = raw.lastIndexOf("#");
  const slashIndex = raw.lastIndexOf("/");
  const marker = Math.max(hashIndex, slashIndex);
  return raw.substring(marker + 1).replace(/^@?/, "");
}

function referenceDefinition(type, featureName) {
  try {
    return (
      (modelingElementDefinition(state.activeType, type)?.references || []).find(
        (reference) => reference.name === featureName,
      ) || null
    );
  } catch {
    return null;
  }
}

function setOppositeReference(sourceElement, targetElement, reference) {
  const opposite = normalizeOppositeName(reference?.opposite);
  if (!opposite || reference?.readonly || !sourceElement?.id || !targetElement?.id) {
    return;
  }
  const oppositeDefinition = referenceDefinition(semanticType(targetElement), opposite);
  const many = oppositeDefinition?.many !== false;
  addReferenceValue(targetElement, opposite, sourceElement.id, many);
}

function removeOppositeReference(sourceElement, targetElement, reference) {
  const opposite = normalizeOppositeName(reference?.opposite);
  if (!opposite || reference?.readonly || !sourceElement?.id || !targetElement?.id) {
    return;
  }
  const oppositeDefinition = referenceDefinition(semanticType(targetElement), opposite);
  const many = oppositeDefinition?.many !== false;
  removeReferenceValue(targetElement, opposite, sourceElement.id, many);
}

function configuredSemanticReferenceRules(typeKey = state.activeType) {
  try {
    const configured = modelingSemanticReferenceRules(typeKey);
    if (configured.length) {
      return configured;
    }
  } catch {
    return [];
  }
  return [];
}

function ruleMatchesForward(rule, sourceElement, targetElement, kind, typeKey = state.activeType) {
  return (
    String(rule?.kind || "").toUpperCase() === String(kind || "").toUpperCase() &&
    matchesSemanticType(sourceElement, rule?.sourceType, typeKey) &&
    matchesSemanticType(targetElement, rule?.targetType, typeKey)
  );
}

function ruleMatchesReverse(rule, sourceElement, targetElement, kind, typeKey = state.activeType) {
  return (
    Boolean(rule?.reverse) &&
    String(rule?.kind || "").toUpperCase() === String(kind || "").toUpperCase() &&
    matchesSemanticType(targetElement, rule?.sourceType, typeKey) &&
    matchesSemanticType(sourceElement, rule?.targetType, typeKey)
  );
}

function semanticRuleSpecificity(rule) {
  return (
    (rule?.sourceType && rule.sourceType !== "*" ? 1 : 0) +
    (rule?.targetType && rule.targetType !== "*" ? 1 : 0)
  );
}

function findSemanticReferenceRule(sourceElement, targetElement, kind, typeKey = state.activeType) {
  const matches = [];
  configuredSemanticReferenceRules(typeKey).forEach((rule) => {
    if (ruleMatchesForward(rule, sourceElement, targetElement, kind, typeKey)) {
      matches.push({ rule, reversedVisual: false });
    } else if (ruleMatchesReverse(rule, sourceElement, targetElement, kind, typeKey)) {
      matches.push({ rule, reversedVisual: true });
    }
  });
  matches.sort((a, b) => semanticRuleSpecificity(b.rule) - semanticRuleSpecificity(a.rule));
  return matches[0] || null;
}

function removePreviousSemanticReference(edge, previous) {
  if (!previous?.sourceElementId || !previous?.targetElementId || !previous?.feature) {
    return;
  }
  const sourceElement = state.graph.elementsById.get(previous.sourceElementId);
  const targetElement = state.graph.elementsById.get(previous.targetElementId);
  if (!sourceElement || !targetElement) {
    return;
  }
  const reference = referenceDefinition(semanticType(sourceElement), previous.feature);
  const many = reference?.many !== false;
  removeReferenceValue(sourceElement, previous.feature, targetElement.id, many);
  removeOppositeReference(sourceElement, targetElement, reference);
}

function applySemanticReference(edge, previous = null) {
  if (!isModelingLevel(state.activeType) || !edge?.sourceId || !edge?.targetId) {
    return null;
  }
  const visualSource = state.graph.elementsById.get(edge.sourceId);
  const visualTarget = state.graph.elementsById.get(edge.targetId);
  if (!visualSource || !visualTarget) {
    return null;
  }
  const matched = findSemanticReferenceRule(
    visualSource,
    visualTarget,
    edge.kind,
    state.activeType,
  );
  if (!matched?.rule?.feature) {
    return null;
  }
  const semanticSource = matched.reversedVisual ? visualTarget : visualSource;
  const semanticTarget = matched.reversedVisual ? visualSource : visualTarget;
  const feature = matched.rule.feature;
  const previousSignature =
    previous ||
    (edge.semanticFeature
      ? {
          sourceElementId: edge.semanticSourceElementId || edge.sourceId,
          targetElementId: edge.semanticTargetElementId || edge.targetId,
          feature: edge.semanticFeature,
        }
      : null);
  if (
    previousSignature &&
    (previousSignature.sourceElementId !== semanticSource.id ||
      previousSignature.targetElementId !== semanticTarget.id ||
      previousSignature.feature !== feature)
  ) {
    removePreviousSemanticReference(edge, previousSignature);
  }
  const reference = referenceDefinition(semanticType(semanticSource), feature);
  const many = reference?.many !== false;
  addReferenceValue(semanticSource, feature, semanticTarget.id, many);
  setOppositeReference(semanticSource, semanticTarget, reference);

  edge.semanticFeature = feature;
  edge.semanticSourceElementId = semanticSource.id;
  edge.semanticTargetElementId = semanticTarget.id;
  edge.semanticDirection = matched.reversedVisual ? "reverse-visual" : "forward";
  edge.visualOnly = false;
  return {
    sourceElementId: semanticSource.id,
    targetElementId: semanticTarget.id,
    feature,
  };
}

function materializeSemanticEdgeObject(edge, relationship) {
  if (!isModelingLevel(state.activeType) || !edge?.sourceId || !edge?.targetId) {
    return relationship;
  }
  const source = state.graph.elementsById.get(edge.sourceId);
  const target = state.graph.elementsById.get(edge.targetId);
  const spec = semanticEdgeObjectSpec(
    state.activeType,
    edge.kind,
    modelTypeOf(source),
    modelTypeOf(target),
  );
  if (!spec) {
    return relationship;
  }
  const existing = state.graph.relationshipsById.get(edge.id) || {};
  const materialized = {
    ...relationshipSemanticCopy(
      state.activeType,
      {
        ...existing,
        ...relationship,
        ...spec.defaults,
        eClass: spec.eClass,
        kind: edge.kind,
        source: edge.sourceId,
        target: edge.targetId,
        sourceElementId: edge.sourceId,
        targetElementId: edge.targetId,
        sourceType: modelTypeOf(source),
        targetType: modelTypeOf(target),
      },
      state.graph,
    ),
    ...existing,
    ...relationship,
    eClass: spec.eClass,
    source: edge.sourceId,
    target: edge.targetId,
    sourceElementId: edge.sourceId,
    targetElementId: edge.targetId,
    name: existing.name || relationship.name || `${spec.eClass} ${edge.id}`,
    rootFeature: spec.rootFeature,
    visualOnly: false,
  };
  if (spec.ownerType && spec.rootFeature) {
    let owner = [...state.graph.elementsById.values()].find((element) =>
      matchesSemanticType(element, spec.ownerType, state.activeType),
    );
    if (!owner) {
      owner = normalizeElement({
        id: genId(),
        eClass: spec.ownerType,
        name:
          modelingElementDefinition(state.activeType, spec.ownerType)?.displayName ||
          spec.ownerType,
      });
      state.graph.elementsById.set(owner.id, owner);
    }
    materialized.__ownerId = owner.id;
    materialized.__containmentFeature = spec.rootFeature;
  } else if (source?.__ownerId && source.__ownerId === target?.__ownerId && spec.rootFeature) {
    materialized.__ownerId = source.__ownerId;
    materialized.__containmentFeature = spec.rootFeature;
  } else if (spec.ownerAsSource && spec.rootFeature) {
    materialized.__ownerId = source.id;
    materialized.__containmentFeature = spec.rootFeature;
  }
  return materialized;
}

export function addConnectionToGraphAndActiveView(edge) {
  if (!edge?.id || edge.bundle) {
    return;
  }
  const existing = state.graph.relationshipsById.get(edge.id);
  const previousSemanticReference = existing?.semanticFeature
    ? {
        sourceElementId: existing.semanticSourceElementId || existing.sourceElementId,
        targetElementId: existing.semanticTargetElementId || existing.targetElementId,
        feature: existing.semanticFeature,
      }
    : null;
  let relationship = normalizeRelationship(
    state.activeType,
    {
      ...existing,
      id: edge.id,
      kind: edge.kind,
      source: edge.sourceId,
      target: edge.targetId,
      sourceElementId: edge.sourceId,
      targetElementId: edge.targetId,
    },
    state.graph.relationshipsById.size,
  );
  relationship.sourceType =
    semanticType(state.graph.elementsById.get(edge.sourceId)) || relationship.sourceType;
  relationship.targetType =
    semanticType(state.graph.elementsById.get(edge.targetId)) || relationship.targetType;
  relationship = materializeSemanticEdgeObject(edge, relationship);
  const semanticReference = applySemanticReference(
    {
      ...relationship,
      sourceId: edge.sourceId,
      targetId: edge.targetId,
    },
    previousSemanticReference,
  );
  if (semanticReference) {
    Object.assign(relationship, {
      semanticFeature: semanticReference.feature,
      semanticSourceElementId: semanticReference.sourceElementId,
      semanticTargetElementId: semanticReference.targetElementId,
    });
  } else if (previousSemanticReference) {
    removePreviousSemanticReference(edge, previousSemanticReference);
    delete relationship.semanticFeature;
    delete relationship.semanticSourceElementId;
    delete relationship.semanticTargetElementId;
  }
  state.graph.relationshipsById.set(edge.id, relationship);
  const view = activeView();
  if (view && !safeArray(view.edges).some((entry) => entry.relationshipId === edge.id)) {
    view.edges.push({ relationshipId: edge.id, visible: true });
  }
  rebuildGraphIndexes(state.graph);
}

export function removeRelationshipFromGraph(relationshipId) {
  const id = String(relationshipId || "");
  if (!id) {
    return;
  }
  const relationship = state.graph.relationshipsById.get(id);
  if (relationship?.semanticFeature) {
    removePreviousSemanticReference(relationship, {
      sourceElementId: relationship.semanticSourceElementId || relationship.sourceElementId,
      targetElementId: relationship.semanticTargetElementId || relationship.targetElementId,
      feature: relationship.semanticFeature,
    });
  }
  state.graph.relationshipsById.delete(id);
  state.views.byId.forEach((view) => {
    view.edges = safeArray(view.edges).filter((edge) => edge.relationshipId !== id);
    if (view.hidden) {
      view.hidden.relationshipIds = safeArray(view.hidden.relationshipIds).filter(
        (item) => item !== id,
      );
    }
  });
  rebuildGraphIndexes(state.graph);
}

export function persistEdgeLayoutInActiveView(edgeId, layout) {
  const view = activeView();
  if (!view || !edgeId) {
    return false;
  }
  let viewEdge = safeArray(view.edges).find((edge) => edge.relationshipId === edgeId);
  if (!viewEdge) {
    viewEdge = { relationshipId: edgeId, visible: true };
    view.edges.push(viewEdge);
  }
  viewEdge.pinPoints = safeArray(layout?.pinPoints).map(clone);
  viewEdge.sourceAnchor = layout?.sourceAnchor ? clone(layout.sourceAnchor) : undefined;
  viewEdge.targetAnchor = layout?.targetAnchor ? clone(layout.targetAnchor) : undefined;
  if (Array.isArray(layout?.sections)) {
    viewEdge.sections = clone(layout.sections);
  }
  return true;
}

export function persistEdgeLayoutsInActiveView(layoutsByEdgeId) {
  const view = activeView();
  if (!view || !layoutsByEdgeId?.size) {
    return false;
  }
  const viewEdgesById = new Map(safeArray(view.edges).map((edge) => [edge.relationshipId, edge]));
  layoutsByEdgeId.forEach((layout, edgeId) => {
    let viewEdge = viewEdgesById.get(edgeId);
    if (!viewEdge) {
      viewEdge = { relationshipId: edgeId, visible: true };
      viewEdgesById.set(edgeId, viewEdge);
    }
    viewEdge.pinPoints = safeArray(layout?.pinPoints).map(clone);
    viewEdge.sourceAnchor = layout?.sourceAnchor ? clone(layout.sourceAnchor) : undefined;
    viewEdge.targetAnchor = layout?.targetAnchor ? clone(layout.targetAnchor) : undefined;
    if (Array.isArray(layout?.sections)) {
      viewEdge.sections = clone(layout.sections);
    }
  });
  view.edges = [...viewEdgesById.values()];
  return true;
}

export function visibleGraphFallback(typeKey = state.activeType) {
  return emptyDiagram(typeKey);
}
