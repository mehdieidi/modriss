import {api} from './api.js';
import {state} from './state.js';
import {setError} from './status.js';

const EMPTY_CONFIG = Object.freeze({
  version: 0,
  dynamicPersistenceEnabled: true,
  levels: Object.freeze({
    cim: Object.freeze(emptyLevel("CIM")),
    pim: Object.freeze(emptyLevel("PIM")),
    psm: Object.freeze(emptyLevel("PSM"))
  }),
  transformations: Object.freeze({
    cim_to_pim: Object.freeze(emptyTransformation()),
    pim_to_psm: Object.freeze(emptyTransformation()),
    psm_to_artifact: Object.freeze({
      ...emptyTransformation(),
      artifactType: "aws-sam",
      generationMode: "serverless_mda",
      projectRootExpression: "expr:#sourceModel['name']",
      generationRules: Object.freeze({}),
      projectStructure: Object.freeze({
        directories: Object.freeze([]),
        pathMappings: Object.freeze([]),
        passthroughUnmatched: true,
        emitGitkeep: true
      }),
      templates: Object.freeze([])
    })
  })
});

function emptyLevel(displayName) {
  return {
    displayName,
    elementsPath: "/diagram/elements",
    relationshipsPath: "/diagram/relationships",
    labelField: "name",
    relationshipKinds: [],
    elements: [],
    relationshipRules: [],
    viewDefinitions: [],
    universalSyntax: [],
    kernelSyntax: [],
    kernelNotation: [],
    complexityManagement: [],
    strictnessModes: ["exploration", "methodology", "production"],
    constraints: [],
    rootTemplate: {
      name: "",
      diagram: {
        elements: [],
        relationships: []
      }
    }
  };
}

function emptyTransformation() {
  return {
    enabled: false,
    elementMappings: [],
    relationshipMappings: []
  };
}

function clone(value) {
  return structuredClone(value);
}

function configLoadError(message) {
  const error = new Error(message);
  error.code = "MODELING_CONFIG_UNAVAILABLE";
  return error;
}

function ensureConfigShape(raw) {
  if (!raw || typeof raw !== "object") {
    throw configLoadError(
        "Backend modeling config is missing or malformed. Check /api/modeling/config and backend logs.");
  }
  const normalized = clone(EMPTY_CONFIG);
  normalized.version = Number(raw.version || 0);
  normalized.dynamicPersistenceEnabled = raw.dynamicPersistenceEnabled
      !== false;

  for (const level of ["cim", "pim", "psm"]) {
    const incoming = raw.levels?.[level];
    if (!incoming || typeof incoming !== "object") {
      throw configLoadError(
          `Backend modeling config is missing the '${level.toUpperCase()}' level definition.`);
    }
    normalized.levels[level] = {
      displayName: String(incoming.displayName || level.toUpperCase()),
      elementsPath: String(incoming.elementsPath || "/diagram/elements"),
      relationshipsPath: String(
          incoming.relationshipsPath || "/diagram/relationships"),
      labelField: String(incoming.labelField || "name"),
      relationshipKinds: Array.isArray(incoming.relationshipKinds)
          ? incoming.relationshipKinds.map((kind) => String(kind))
          : [],
      elements: Array.isArray(incoming.elements) ? incoming.elements : [],
      relationshipRules: Array.isArray(incoming.relationshipRules)
          ? incoming.relationshipRules : [],
      relationshipKindLabels: incoming.relationshipKindLabels
      && typeof incoming.relationshipKindLabels === "object"
          ? incoming.relationshipKindLabels : {},
      relationshipVisualRules: Array.isArray(incoming.relationshipVisualRules)
          ? incoming.relationshipVisualRules : [],
      semanticReferenceRules: Array.isArray(incoming.semanticReferenceRules)
          ? incoming.semanticReferenceRules : [],
      shortcutConnectorRules: Array.isArray(incoming.shortcutConnectorRules)
          ? incoming.shortcutConnectorRules : [],
      viewDefinitions: Array.isArray(incoming.viewDefinitions)
          ? incoming.viewDefinitions : [],
      universalSyntax: Array.isArray(incoming.universalSyntax)
          ? incoming.universalSyntax : [],
      kernelSyntax: Array.isArray(incoming.kernelSyntax)
          ? incoming.kernelSyntax : [],
      kernelNotation: Array.isArray(incoming.kernelNotation)
          ? incoming.kernelNotation : (Array.isArray(incoming.kernelSyntax)
              ? incoming.kernelSyntax : []),
      complexityManagement: Array.isArray(incoming.complexityManagement)
          ? incoming.complexityManagement : [],
      strictnessModes: Array.isArray(incoming.strictnessModes)
          ? incoming.strictnessModes : ["exploration", "methodology",
            "production"],
      constraints: Array.isArray(incoming.constraints)
          ? incoming.constraints : [],
      rootTemplate: incoming.rootTemplate && typeof incoming.rootTemplate
      === "object"
          ? incoming.rootTemplate
          : clone(EMPTY_CONFIG.levels[level].rootTemplate)
    };
  }

  const incomingTransformations = raw.transformations || {};
  for (const key of ["cim_to_pim", "pim_to_psm", "psm_to_artifact"]) {
    const incoming = incomingTransformations[key];
    if (!incoming || typeof incoming !== "object") {
      continue;
    }
    normalized.transformations[key] = {
      ...normalized.transformations[key],
      ...incoming,
      enabled: Boolean(incoming.enabled),
      elementMappings: Array.isArray(incoming.elementMappings)
          ? incoming.elementMappings : [],
      relationshipMappings: Array.isArray(incoming.relationshipMappings)
          ? incoming.relationshipMappings : []
    };
  }
  return normalized;
}

export async function loadModelingConfig({silent = false} = {}) {
  try {
    const config = await api("/modeling/config");
    state.modelingConfig.config = ensureConfigShape(config);
    state.modelingConfig.error = null;
    return state.modelingConfig.config;
  } catch (error) {
    state.modelingConfig.config = null;
    state.modelingConfig.error = error;
    if (!silent) {
      console.error("Modeling config load failed", error);
      setError(
          `Backend modeling config unavailable: ${error.message}. Check backend logs and /api/modeling/config.`);
    }
    throw error;
  }
}

export function modelingLevelConfig(typeKey = state.activeType) {
  const config = state.modelingConfig.config;
  if (!config?.levels?.[typeKey]) {
    throw configLoadError(
        `No backend modeling config is loaded for ${String(
            typeKey).toUpperCase()}.`);
  }
  return config.levels[typeKey];
}

export function modelingPalette(typeKey = state.activeType) {
  return (modelingLevelConfig(typeKey).elements || []).map(
      (entry) => entry?.creatable === false ? "" : String(entry.type
          || "").trim()).filter(Boolean);
}

export function modelingElementDefinition(typeKey, elementType) {
  const level = modelingLevelConfig(typeKey);
  return (level.elements || []).find((entry) => entry.type === elementType)
      || null;
}

export function modelingViewDefinition(typeKey, view) {
  const level = modelingLevelConfig(typeKey);
  const viewId = String(view?.definitionId || view?.sourceDefinitionId
      || view?.id || "").toLowerCase();
  const viewpoint = String(view?.viewpoint || "").toLowerCase();
  const kind = String(view?.kind || "").toLowerCase().replaceAll("_", "-");
  return (level.viewDefinitions || []).find((entry) => {
    const entryId = String(entry.id || "").toLowerCase();
    const entryKind = String(entry.viewType || "").toLowerCase().replaceAll("_",
        "-");
    const entryViewpoint = String(entry.viewpoint || "").toLowerCase();
    return entryId && (viewId.includes(entryId) || kind.includes(entryId)
        || kind.includes(entryKind) || (viewpoint && viewpoint
            === entryViewpoint));
  }) || null;
}

export function modelingRelationshipKindLabel(typeKey, kind) {
  const labels = modelingLevelConfig(typeKey).relationshipKindLabels || {};
  const key = String(kind || "").toUpperCase();
  return labels[key] || key.toLowerCase().replaceAll("_", " ");
}

function appendClassName(existing, addition) {
  const current = String(existing || "").trim();
  const next = String(addition || "").trim();
  if (!next) {
    return current;
  }
  return current ? `${current} ${next}` : next;
}

function readRuleValue(edge, field) {
  const key = String(field || "");
  if (!key) {
    return undefined;
  }
  return key.split(".").reduce((value, part) =>
      value && typeof value === "object" ? value[part] : undefined, edge);
}

function stringListMatches(values, actual) {
  if (!Array.isArray(values) || !values.length) {
    return false;
  }
  const text = String(actual || "").toUpperCase();
  return values.map((value) => String(value || "").toUpperCase())
  .includes(text);
}

function fieldMatcherMatches(matcher, edge) {
  if (!matcher || typeof matcher !== "object") {
    return false;
  }
  const actual = readRuleValue(edge, matcher.field);
  if (Array.isArray(matcher.values)) {
    return stringListMatches(matcher.values, actual);
  }
  if (Object.hasOwn(matcher, "equals")) {
    return String(actual || "").toUpperCase()
        === String(matcher.equals || "").toUpperCase();
  }
  if (matcher.exists) {
    return actual !== undefined && actual !== null && actual !== "";
  }
  return false;
}

function visualRuleMatches(rule, edge, kind) {
  if (!rule || typeof rule !== "object") {
    return false;
  }
  if (stringListMatches(rule.matchKinds, kind)) {
    return true;
  }
  if (stringListMatches(rule.matchEClasses, edge?.eClass)) {
    return true;
  }
  return Array.isArray(rule.matchFields)
      && rule.matchFields.some((matcher) => fieldMatcherMatches(matcher, edge));
}

function applyVisualRule(presentation, rule) {
  presentation.className = appendClassName(presentation.className,
      rule.className);
  if (Object.hasOwn(rule, "markerStart")) {
    presentation.markerStart = String(rule.markerStart || "");
  }
  if (Object.hasOwn(rule, "markerEnd")) {
    presentation.markerEnd = String(rule.markerEnd || "");
  }
  const style = {};
  ["stroke", "lineWidth", "opacity", "lineDash"].forEach((field) => {
    if (Object.hasOwn(rule, field)) {
      style[field] = rule[field];
    }
  });
  presentation.style = {...presentation.style, ...style};
}

export function modelingRelationshipPresentation(typeKey, edge) {
  const kind = String(edge?.kind || "").toUpperCase();
  const relationship = state.graph?.relationshipsById?.get(edge?.id) || edge
      || {};
  const presentation = {
    className: "",
    markerStart: "",
    markerEnd: "arrow",
    style: {}
  };
  const rules = modelingLevelConfig(typeKey).relationshipVisualRules || [];
  for (const rule of rules) {
    if (!visualRuleMatches(rule, relationship, kind)) {
      continue;
    }
    applyVisualRule(presentation, rule);
    (rule.variants || []).forEach((variant) => {
      if (fieldMatcherMatches(variant, relationship)) {
        applyVisualRule(presentation, variant);
      }
    });
    break;
  }
  return presentation;
}

export function modelingSemanticReferenceRules(typeKey = state.activeType) {
  return modelingLevelConfig(typeKey).semanticReferenceRules || [];
}

export function modelingShortcutConnectorRules(typeKey = state.activeType) {
  return modelingLevelConfig(typeKey).shortcutConnectorRules || [];
}

export function modelingRootType(typeKey = state.activeType) {
  const root = modelingLevelConfig(typeKey).rootTemplate || {};
  return String(root.eClass || root.type || ({
    cim: "CIMModel",
    pim: "PIMModel",
    psm: "AwsPsmModel"
  })[typeKey] || "");
}

export function modelingConcreteTypesFor(typeKey, expectedType) {
  const level = modelingLevelConfig(typeKey);
  return (level.elements || []).filter((entry) => {
    if (!entry?.type || entry.abstract || entry.supportOnly) {
      return false;
    }
    return modelingTypeMatches(typeKey, expectedType, entry.type);
  }).map((entry) => entry.type);
}

export function modelingRelationshipElementTypes(typeKey = state.activeType) {
  return (modelingLevelConfig(typeKey).elements || []).filter((entry) =>
      entry?.relationshipElement).map((entry) => entry.type);
}

function containmentTitle(feature) {
  return String(feature || "").replaceAll(/([a-z0-9])([A-Z])/g, "$1 $2")
  .replaceAll(/[-_]+/g, " ").replace(/\b\w/g, (letter) =>
      letter.toUpperCase());
}

export function modelingContainmentsForType(typeKey, ownerType) {
  const definition = modelingElementDefinition(typeKey, ownerType);
  const relationshipTypes = new Set(modelingRelationshipElementTypes(typeKey));
  return (definition?.references || []).filter((reference) =>
      reference?.containment && !reference.readonly && reference.name
      && reference.targetType).map((reference) => {
    const concreteTypes = modelingConcreteTypesFor(typeKey,
        reference.targetType);
    const types = concreteTypes.length ? concreteTypes : [reference.targetType];
    return {
      feature: reference.name,
      targetType: reference.targetType,
      types,
      required: Boolean(reference.required),
      many: reference.many !== false,
      singleton: reference.many === false,
      title: containmentTitle(reference.name),
      relationshipOnly: types.length > 0 && types.every((type) =>
          relationshipTypes.has(type))
    };
  });
}

export function modelingRootContainments(typeKey = state.activeType) {
  return modelingContainmentsForType(typeKey, modelingRootType(typeKey));
}

export function modelingTypeMatches(typeKey, expected, actual) {
  if (!expected || expected === "*") {
    return true;
  }
  if (expected === actual) {
    return true;
  }
  try {
    const definition = modelingElementDefinition(typeKey, actual);
    const configured = Array.isArray(definition?.supertypes)
        ? definition.supertypes.map(String) : [];
    if (configured.includes(expected)) {
      return true;
    }
  } catch {
    return false;
  }
  return false;
}

function ruleSpecificity(rule) {
  const sourceSpecific = rule?.sourceType && rule.sourceType !== "*" ? 1 : 0;
  const targetSpecific = rule?.targetType && rule.targetType !== "*" ? 1 : 0;
  return sourceSpecific + targetSpecific;
}

function isWildcardRule(rule) {
  return rule?.sourceType === "*" || rule?.targetType === "*";
}

function isMethodologyWildcardExempt(rule) {
  const exemptSources = new Set([
    "Hotspot",
    "OpenQuestion",
    "Risk",
    "Assumption",
    "RequirementLink",
    "GoalSatisfactionLink"
  ]);
  return exemptSources.has(rule?.sourceType);
}

function normalizeKinds(kinds) {
  const deduped = new Set();
  (kinds || []).forEach((kind) => {
    const normalized = String(kind || "").trim();
    if (normalized) {
      deduped.add(normalized);
    }
  });
  return [...deduped];
}

export function modelingLegalKinds(typeKey, sourceType, targetType) {
  const rules = modelingLevelConfig(typeKey).relationshipRules || [];
  const strictness = state.modelingStrictness || "methodology";
  const matched = [];
  for (const rule of rules) {
    if (strictness !== "exploration" && isWildcardRule(rule)
        && !isMethodologyWildcardExempt(rule)) {
      continue;
    }
    if (modelingTypeMatches(typeKey, rule.sourceType, sourceType)
        && modelingTypeMatches(typeKey, rule.targetType, targetType)
        && Array.isArray(rule.allowedKinds)) {
      matched.push(rule);
    }
  }
  if (!matched.length) {
    if (strictness === "exploration") {
      return normalizeKinds(modelingLevelConfig(typeKey).relationshipKinds);
    }
    return [];
  }
  const bestSpecificity = Math.max(...matched.map(ruleSpecificity));
  const winners = matched.filter(
      (rule) => ruleSpecificity(rule) === bestSpecificity);
  return normalizeKinds(winners.flatMap((rule) => rule.allowedKinds));
}

export function modelingRootTemplate(typeKey, modelName) {
  const root = clone(modelingLevelConfig(typeKey).rootTemplate || {});
  root.name = modelName;
  root.diagram ??= {elements: [], relationships: []};
  root.diagram.elements ??= [];
  root.diagram.relationships ??= [];
  root.graph ??= {
    elements: [],
    relationships: [],
    traceLinks: [],
    assumptions: [],
    validationIssues: [],
    manualBacklog: []
  };
  root.views ??= [];
  root.fragments ??= [];
  return root;
}

export function modelingLabelField(typeKey) {
  return String(modelingLevelConfig(typeKey).labelField || "name");
}

export function transformationKeyForLevel(levelKey) {
  if (levelKey === "cim") {
    return "cim_to_pim";
  }
  if (levelKey === "pim") {
    return "pim_to_psm";
  }
  return "psm_to_artifact";
}
