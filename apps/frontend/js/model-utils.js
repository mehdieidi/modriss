import {
  modelingContainmentsForType,
  modelingElementDefinition,
  modelingLevelConfig,
  modelingRelationshipElementTypes,
  modelingRootContainments,
  modelingRootType,
  modelingTypeMatches,
} from "./modeling-config-data.js";
import { genId } from "./utils.js";

function clone(value) {
  return value == null ? value : structuredClone(value);
}

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

/**
 * Returns enum values implied by a concrete type name, such as StartStep -> stepKind=START.
 * A value is inferred only when exactly one declared enum option matches, so unrelated enums
 * remain explicit user choices.
 */
export function impliedEnumValues(type, definition) {
  const typeName = String(type || "")
    .replace(/(Step|State)$/, "")
    .replace(/([a-z])([A-Z])/g, "$1_$2")
    .toUpperCase();
  if (!typeName) {
    return {};
  }
  const values = {};
  for (const field of [
    ...safeArray(definition?.attributes),
    ...safeArray(definition?.references),
  ]) {
    const options = safeArray(field?.options).map(String);
    if (!options.length || field?.many || field?.kind === "reference") {
      continue;
    }
    const matches = options.filter((option) => option === typeName);
    if (matches.length === 1) {
      values[field.name] = matches[0];
    }
  }
  return values;
}

function sanitizeIdPart(value) {
  return (
    String(value || "element")
      .trim()
      .toLowerCase()
      .replaceAll(/[^a-z0-9_-]+/g, "-")
      .replaceAll(/^-+|-+$/g, "") || "element"
  );
}

export function modelTypeOf(value) {
  return typeof value === "string" ? value : String(value?.eClass || value?.type || "");
}

export function modelTypeMatches(typeKey, value, expectedType) {
  const expected = String(expectedType || "").trim();
  if (!expected || expected === "*" || expected === "EObject") {
    return true;
  }
  const actual = modelTypeOf(value);
  if (actual === expected) {
    return true;
  }
  try {
    return modelingTypeMatches(typeKey, expected, actual);
  } catch {
    return false;
  }
}

export function refId(value) {
  if (typeof value === "string") {
    return value;
  }
  if (value && typeof value === "object") {
    return (
      value.$ref ||
      value.id ||
      value.elementId ||
      value.sourceElementId ||
      value.targetElementId ||
      ""
    );
  }
  return "";
}

export function refIds(value) {
  if (Array.isArray(value)) {
    return value.map(refId).filter(Boolean);
  }
  const single = refId(value);
  return single ? [single] : [];
}

export function setReferenceValue(element, feature, ids, many = true) {
  if (!element || !feature) {
    return;
  }
  const values = Array.isArray(ids) ? ids.map(refId).filter(Boolean) : [refId(ids)].filter(Boolean);
  element[feature] = many ? [...new Set(values)] : values[0] || null;
}

export function addReferenceValue(element, feature, id, many = true) {
  if (!element || !feature || !id) {
    return;
  }
  if (many) {
    const ids = new Set(refIds(element[feature]));
    ids.add(String(id));
    element[feature] = [...ids];
  } else {
    element[feature] = String(id);
  }
}

export function removeReferenceValue(element, feature, id, many = true) {
  if (!element || !feature || !id) {
    return;
  }
  if (many) {
    element[feature] = refIds(element[feature]).filter((item) => item !== String(id));
  } else if (refId(element[feature]) === String(id)) {
    element[feature] = null;
  }
}

export function hasReferenceValue(element, feature, id) {
  return refIds(element?.[feature]).includes(String(id));
}

export function elementLabel(element) {
  const definition = element?.eClass ? modelingElementDefinitionSafe("", element.eClass) : null;
  const configuredFields = [
    definition?.labelField,
    ...safeArray(definition?.notation?.lineFields),
    ...safeArray(definition?.visibleFields),
  ].filter(Boolean);
  for (const field of [
    "name",
    "label",
    "displayName",
    "title",
    "logicalId",
    ...configuredFields,
    "id",
  ]) {
    const value = element?.[field];
    if (value !== undefined && value !== null && String(value).trim()) {
      return String(value);
    }
  }
  return "Element";
}

function modelingElementDefinitionSafe(typeKey, elementType) {
  try {
    return modelingElementDefinition(typeKey || undefined, elementType);
  } catch {
    return null;
  }
}

export function compactRefLabels(value, elementsById, limit = 3) {
  const ids = refIds(value);
  const labels = ids.slice(0, limit).map((id) => elementLabel(elementsById?.get?.(id) || { id }));
  const extra = Math.max(0, ids.length - limit);
  return `${labels.join(", ")}${extra ? ` +${extra}` : ""}`;
}

export function isPresent(value) {
  if (Array.isArray(value)) {
    return value.length > 0;
  }
  if (typeof value === "boolean") {
    return true;
  }
  if (value && typeof value === "object") {
    return Boolean(refId(value) || Object.keys(value).length);
  }
  return value !== null && value !== undefined && String(value).trim() !== "";
}

function definitionForType(typeKey, type) {
  try {
    return modelingElementDefinition(typeKey, type);
  } catch {
    return null;
  }
}

function requiredFeaturesForType(typeKey, type, visited = new Set()) {
  if (!type || visited.has(type)) {
    return [];
  }
  visited.add(type);
  const definition = definitionForType(typeKey, type);
  const inherited = safeArray(definition?.supertypes).flatMap((supertype) =>
    requiredFeaturesForType(typeKey, supertype, visited),
  );
  const own = [...safeArray(definition?.attributes), ...safeArray(definition?.references)]
    .filter((field) => field?.required && !field?.readonly)
    .map((field) => field.name);
  return [...new Set([...inherited, ...own])];
}

function ownerSatisfied(element, feature) {
  return Boolean(element?.__ownerId) && !isPresent(element?.[feature]);
}

export function missingRequiredFeatures(typeKey, element, extraRequired = []) {
  const type = modelTypeOf(element);
  const required = new Set([...requiredFeaturesForType(typeKey, type), ...extraRequired]);
  return [...required].filter((feature) => {
    if (ownerSatisfied(element, feature)) {
      return false;
    }
    return !isPresent(element?.[feature]);
  });
}

export function nestedContainmentsForType(typeKey, elementType) {
  try {
    return modelingContainmentsForType(typeKey, elementType).filter(
      (entry) => safeArray(entry.types).length,
    );
  } catch {
    return [];
  }
}

export function rootContainmentForType(typeKey, type) {
  return (
    modelingRootContainments(typeKey).find((entry) =>
      safeArray(entry.types).some((candidate) => modelTypeMatches(typeKey, type, candidate)),
    ) || null
  );
}

export function isRelationshipElementType(typeKey, type) {
  try {
    return modelingRelationshipElementTypes(typeKey).includes(String(type || ""));
  } catch {
    return false;
  }
}

function normalizeElement(raw, fallbackType, index, owner = null) {
  if (!raw || typeof raw !== "object") {
    return null;
  }
  const type = String(raw.eClass || raw.type || fallbackType || "");
  if (!type) {
    return null;
  }
  const id = String(
    raw.id ||
      `${owner?.id ? `${sanitizeIdPart(owner.id)}-` : ""}${sanitizeIdPart(type)}-${index + 1}`,
  );
  return {
    eClass: type,
    id,
    name: elementLabel({ ...raw, id }),
    label: elementLabel({ ...raw, id }),
    ...clone(raw),
    id,
    eClass: type,
    __ownerId: owner?.id || raw.__ownerId,
    __containmentFeature: owner?.feature || raw.__containmentFeature,
  };
}

function collectNestedElements(typeKey, parent, result, seen) {
  nestedContainmentsForType(typeKey, modelTypeOf(parent)).forEach((entry) => {
    if (entry.relationshipOnly || entry.referenceOnly) {
      return;
    }
    const rawValues = entry.singleton
      ? parent?.[entry.feature]
        ? [parent[entry.feature]]
        : []
      : safeArray(parent?.[entry.feature]);
    rawValues.forEach((raw, index) => {
      const child = normalizeElement(raw, raw?.eClass || raw?.type || entry.types?.[0], index, {
        id: parent.id,
        feature: entry.feature,
      });
      if (!child || seen.has(child.id) || isRelationshipElementType(typeKey, modelTypeOf(child))) {
        return;
      }
      seen.add(child.id);
      result.push(child);
      collectNestedElements(typeKey, child, result, seen);
    });
  });
}

export function semanticElementsFromRoot(typeKey, modelJson) {
  const result = [];
  const seen = new Set();
  if (!modelJson || typeof modelJson !== "object") {
    return result;
  }
  modelingRootContainments(typeKey).forEach((entry) => {
    if (entry.relationshipOnly || entry.referenceOnly) {
      return;
    }
    const rawValues = entry.singleton
      ? modelJson?.[entry.feature]
        ? [modelJson[entry.feature]]
        : []
      : safeArray(modelJson?.[entry.feature]);
    rawValues.forEach((raw, index) => {
      const element = normalizeElement(raw, raw?.eClass || raw?.type || entry.types?.[0], index);
      if (
        !element ||
        seen.has(element.id) ||
        isRelationshipElementType(typeKey, modelTypeOf(element))
      ) {
        return;
      }
      seen.add(element.id);
      result.push(element);
      collectNestedElements(typeKey, element, result, seen);
    });
  });
  return result;
}

function edgeObjectRules(typeKey) {
  try {
    return safeArray(modelingLevelConfig(typeKey).semanticEdgeObjectRules);
  } catch {
    return [];
  }
}

function ruleKinds(rule) {
  return new Set(
    safeArray(rule.matchKinds || rule.kinds || rule.allowedKinds).map((kind) =>
      String(kind).toUpperCase(),
    ),
  );
}

function ruleMatchesEndpointTypes(typeKey, rule, sourceType, targetType) {
  const sourceExpected = rule.sourceType || rule.matchSourceType || "*";
  const targetExpected = rule.targetType || rule.matchTargetType || "*";
  return (
    modelTypeMatches(typeKey, sourceType, sourceExpected) &&
    modelTypeMatches(typeKey, targetType, targetExpected)
  );
}

export function semanticEdgeObjectSpec(typeKey, kind, sourceType, targetType) {
  const normalizedKind = String(kind || "").toUpperCase();
  return (
    edgeObjectRules(typeKey).find((rule) => {
      const kinds = ruleKinds(rule);
      if (kinds.size && !kinds.has(normalizedKind)) {
        return false;
      }
      return ruleMatchesEndpointTypes(typeKey, rule, sourceType, targetType);
    }) || null
  );
}

function defaultForKind(rule, kind) {
  const normalizedKind = String(kind || "").toUpperCase();
  const defaults = { ...(rule.defaults || {}) };
  const kindDefaults = rule.kindDefaults || {};
  if (kindDefaults && typeof kindDefaults === "object" && kindDefaults[normalizedKind]) {
    return { ...defaults, ...kindDefaults[normalizedKind] };
  }
  return defaults;
}

function firstConfiguredValue(object, fields) {
  for (const field of safeArray(fields)) {
    const value = object?.[field];
    const id = refId(value);
    if (id) {
      return id;
    }
  }
  return "";
}

function setEndpointFields(copy, rule, sourceId, targetId) {
  if (rule.sourceFeature) {
    copy[rule.sourceFeature] = copy[rule.sourceFeature] || sourceId;
  }
  if (rule.targetFeature) {
    copy[rule.targetFeature] = copy[rule.targetFeature] || targetId;
  }
  safeArray(rule.targetFeatures).some((feature) => {
    if (copy[feature]) {
      return true;
    }
    copy[feature] = targetId;
    return true;
  });
}

function relationshipKindFromRule(raw, rule, fallbackKind, type) {
  const configuredKinds = ruleKinds(rule);
  const fieldValue = String(raw?.[rule?.kindField] || "").toUpperCase();
  const mappedKind = String(rule?.kindMap?.[fieldValue] || fieldValue).toUpperCase();
  const candidates = [raw?.kind, mappedKind, fallbackKind, rule?.defaultKind]
    .map((kind) => String(kind || "").toUpperCase())
    .filter(Boolean);
  const resolved = candidates.find((kind) => !configuredKinds.size || configuredKinds.has(kind));
  if (resolved) {
    return resolved;
  }
  if (configuredKinds.size === 1) {
    return [...configuredKinds][0];
  }
  throw new Error(`Cannot resolve relationship kind for ${type} from modeling metadata`);
}

export function relationshipSemanticCopy(typeKey, relationship, graph = null) {
  const type = modelTypeOf(relationship);
  const sourceId =
    refId(relationship?.source) || relationship?.sourceElementId || relationship?.sourceId || "";
  const targetId =
    refId(relationship?.target) || relationship?.targetElementId || relationship?.targetId || "";
  const sourceType = relationship?.sourceType || modelTypeOf(graph?.elementsById?.get?.(sourceId));
  const targetType = relationship?.targetType || modelTypeOf(graph?.elementsById?.get?.(targetId));
  const rule =
    edgeObjectRules(typeKey).find((candidate) => candidate.eClass === type) ||
    semanticEdgeObjectSpec(typeKey, relationship?.kind, sourceType, targetType);
  const copy = stripRuntimeFields(typeKey, {
    ...(rule ? defaultForKind(rule, relationship?.kind) : {}),
    ...relationship,
    eClass: type,
  });
  if (rule) {
    setEndpointFields(copy, rule, sourceId, targetId);
    if (rule.removeGenericEndpoints !== false) {
      delete copy.sourceElementId;
      delete copy.targetElementId;
    }
  }
  return copy;
}

function relationshipFromObject(typeKey, raw, fallbackType, fallbackKind, index, owner = null) {
  if (!raw || typeof raw !== "object") {
    return [];
  }
  const type = String(raw.eClass || raw.type || fallbackType || "");
  const rule = edgeObjectRules(typeKey).find((candidate) => candidate.eClass === type) || {};
  const sourceId =
    firstConfiguredValue(raw, [
      rule.sourceFeature,
      ...safeArray(rule.sourceFeatures),
      "source",
      "sourceElementId",
    ]) || (rule.ownerAsSource ? owner?.id || "" : "");
  const configuredTargets = [
    rule.targetFeature,
    ...safeArray(rule.targetFeatures),
    "target",
    "targetElementId",
  ].filter(Boolean);
  const targetIds = configuredTargets.flatMap((field) => refIds(raw[field]));
  const uniqueTargets = [...new Set(targetIds)].filter(Boolean);
  if (!sourceId || !uniqueTargets.length) {
    return [];
  }
  const kind = relationshipKindFromRule(raw, rule, fallbackKind, type);
  return uniqueTargets.map((targetId, targetIndex) => {
    const id = String(
      raw.id ||
        `rel-${sanitizeIdPart(kind)}-${sanitizeIdPart(sourceId)}-${sanitizeIdPart(targetId)}-${index + targetIndex + 1}`,
    );
    return {
      ...clone(raw),
      id,
      eClass: type,
      kind,
      sourceElementId: sourceId,
      targetElementId: targetId,
      source: sourceId,
      target: targetId,
      __ownerId: owner?.id || raw.__ownerId,
      __containmentFeature: owner?.feature || raw.__containmentFeature,
    };
  });
}

function collectNestedRelationships(typeKey, parent, result) {
  nestedContainmentsForType(typeKey, modelTypeOf(parent)).forEach((entry) => {
    const rawValues = entry.singleton
      ? parent?.[entry.feature]
        ? [parent[entry.feature]]
        : []
      : safeArray(parent?.[entry.feature]);
    if (entry.relationshipOnly) {
      rawValues.forEach((raw, index) => {
        result.push(
          ...relationshipFromObject(
            typeKey,
            raw,
            raw?.eClass || raw?.type || entry.types?.[0],
            "",
            index,
            {
              id: parent.id,
              feature: entry.feature,
            },
          ),
        );
      });
      return;
    }
    rawValues.forEach((raw) => {
      if (raw && typeof raw === "object") {
        collectNestedRelationships(typeKey, raw, result);
      }
    });
  });
}

export function semanticRelationshipsFromRoot(typeKey, modelJson) {
  const result = [];
  if (!modelJson || typeof modelJson !== "object") {
    return result;
  }
  modelingRootContainments(typeKey).forEach((entry) => {
    const rawValues = entry.singleton
      ? modelJson?.[entry.feature]
        ? [modelJson[entry.feature]]
        : []
      : safeArray(modelJson?.[entry.feature]);
    if (entry.relationshipOnly) {
      rawValues.forEach((raw, index) => {
        result.push(
          ...relationshipFromObject(
            typeKey,
            raw,
            raw?.eClass || raw?.type || entry.types?.[0],
            "",
            index,
          ),
        );
      });
      return;
    }
    rawValues.forEach((raw) => {
      if (raw && typeof raw === "object") {
        collectNestedRelationships(typeKey, raw, result);
      }
    });
  });
  return result.filter(Boolean);
}

function readonlyReferenceNames(typeKey, type) {
  try {
    return new Set(
      safeArray(modelingElementDefinition(typeKey, type)?.references)
        .filter((reference) => reference?.readonly)
        .map((reference) => reference.name),
    );
  } catch {
    return new Set();
  }
}

export function stripRuntimeFields(typeKey, element) {
  const copy = clone(element) || {};
  const type = modelTypeOf(copy);
  const semanticKindAttribute = safeArray(definitionForType(typeKey, type)?.attributes).some(
    (attribute) => attribute?.name === "kind",
  );
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
  ].forEach((key) => delete copy[key]);
  if (!semanticKindAttribute) {
    delete copy.kind;
  }
  readonlyReferenceNames(typeKey, type).forEach((key) => delete copy[key]);
  delete copy.__ownerId;
  delete copy.__containmentFeature;
  return copy;
}

function childElementsForContainment(parent, entry, graph, typeKey) {
  const ids = new Set(refIds(parent?.[entry.feature]));
  const children = [];
  const addCandidate = (candidate) => {
    if (!candidate?.id || candidate.id === parent.id) {
      return;
    }
    if (!safeArray(entry.types).some((type) => modelTypeMatches(typeKey, candidate, type))) {
      return;
    }
    if (
      (candidate.__ownerId === parent.id && candidate.__containmentFeature === entry.feature) ||
      ids.has(candidate.id)
    ) {
      children.push(candidate);
    }
  };
  graph.elementsById?.forEach(addCandidate);
  graph.relationshipsById?.forEach(addCandidate);
  return children;
}

function attachNestedContainments(typeKey, copy, sourceElement, graph, visited = new Set()) {
  if (!copy || !sourceElement?.id) {
    return;
  }
  const visitKey = `${sourceElement.id}:${modelTypeOf(sourceElement)}`;
  if (visited.has(visitKey)) {
    return;
  }
  visited.add(visitKey);
  nestedContainmentsForType(typeKey, modelTypeOf(sourceElement)).forEach((entry) => {
    const children = childElementsForContainment(sourceElement, entry, graph, typeKey);
    const copies = children.map((child) => {
      const childCopy = isRelationshipElementType(typeKey, modelTypeOf(child))
        ? relationshipSemanticCopy(typeKey, child, graph)
        : stripRuntimeFields(typeKey, child);
      attachNestedContainments(typeKey, childCopy, child, graph, visited);
      return childCopy;
    });
    copy[entry.feature] = entry.singleton ? copies[0] || null : copies;
  });
}

export function populateRootContainments(typeKey, root, graph) {
  if (!root || !graph?.elementsById) {
    return root;
  }
  const rootType = modelingRootType(typeKey);
  if (rootType) {
    root.eClass ||= rootType;
  }
  modelingRootContainments(typeKey).forEach((entry) => {
    root[entry.feature] = entry.singleton ? null : [];
  });
  graph.elementsById.forEach((element) => {
    if (element.__ownerId) {
      return;
    }
    const containment = rootContainmentForType(typeKey, modelTypeOf(element));
    if (!containment || containment.relationshipOnly) {
      return;
    }
    const copy = stripRuntimeFields(typeKey, element);
    attachNestedContainments(typeKey, copy, element, graph);
    if (containment.singleton) {
      root[containment.feature] = copy;
    } else {
      root[containment.feature].push(copy);
    }
  });
  graph.relationshipsById?.forEach((relationship) => {
    if (relationship.visualOnly) {
      return;
    }
    const containment = rootContainmentForType(typeKey, modelTypeOf(relationship));
    if (!containment || !containment.relationshipOnly) {
      return;
    }
    const copy = relationshipSemanticCopy(typeKey, relationship, graph);
    if (containment.singleton) {
      root[containment.feature] = copy;
    } else {
      root[containment.feature].push(copy);
    }
  });
  return root;
}

const POPULATE_CONTAINMENTS_YIELD_EVERY = 80;

export async function populateRootContainmentsAsync(typeKey, root, graph) {
  if (!root || !graph?.elementsById) {
    return root;
  }
  const { yieldToMain } = await import("./utils.js");
  const rootType = modelingRootType(typeKey);
  if (rootType) {
    root.eClass ||= rootType;
  }
  modelingRootContainments(typeKey).forEach((entry) => {
    root[entry.feature] = entry.singleton ? null : [];
  });
  let processed = 0;
  for (const element of graph.elementsById.values()) {
    if (element.__ownerId) {
      continue;
    }
    const containment = rootContainmentForType(typeKey, modelTypeOf(element));
    if (!containment || containment.relationshipOnly) {
      continue;
    }
    const copy = stripRuntimeFields(typeKey, element);
    attachNestedContainments(typeKey, copy, element, graph);
    if (containment.singleton) {
      root[containment.feature] = copy;
    } else {
      root[containment.feature].push(copy);
    }
    processed += 1;
    if (processed % POPULATE_CONTAINMENTS_YIELD_EVERY === 0) {
      await yieldToMain();
    }
  }
  for (const relationship of graph.relationshipsById?.values() || []) {
    if (relationship.visualOnly) {
      continue;
    }
    const containment = rootContainmentForType(typeKey, modelTypeOf(relationship));
    if (!containment || !containment.relationshipOnly) {
      continue;
    }
    const copy = relationshipSemanticCopy(typeKey, relationship, graph);
    if (containment.singleton) {
      root[containment.feature] = copy;
    } else {
      root[containment.feature].push(copy);
    }
    processed += 1;
    if (processed % POPULATE_CONTAINMENTS_YIELD_EVERY === 0) {
      await yieldToMain();
    }
  }
  return root;
}

export function createConfiguredElement(
  typeKey,
  type,
  { id = genId(type.toLowerCase()), label = "" } = {},
) {
  const definition = definitionForType(typeKey, type);
  const name = label || `${type}-${String(id).slice(-4)}`;
  const element = {
    eClass: type,
    id,
    name,
    label: name,
  };
  [...safeArray(definition?.attributes), ...safeArray(definition?.references)].forEach((field) => {
    if (
      !field?.name ||
      field.readonly ||
      Object.prototype.hasOwnProperty.call(element, field.name)
    ) {
      return;
    }
    element[field.name] = clone(
      field.defaultValue ?? impliedEnumValues(type, definition)[field.name] ?? null,
    );
  });
  return element;
}
