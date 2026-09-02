import { api } from "./api.js";
import { applyModelingRuntimeConfig } from "./config.js";
import { state } from "./state.js";
import { setError } from "./status.js";
import { resolveThemeColor } from "./theme-colors.js";
import { emptyDiagram } from "./utils.js";

const EMPTY_CONFIG = Object.freeze({
  version: 0,
  dynamicPersistenceEnabled: true,
  defaultLevel: "",
  levelOrder: Object.freeze([]),
  levels: Object.freeze({}),
  artifact: Object.freeze({}),
  assistant: Object.freeze({}),
  methodology: Object.freeze({}),
  transformations: Object.freeze({}),
  artifactAction: Object.freeze({}),
  impactAnalysis: Object.freeze({}),
  diagramEditor: Object.freeze({}),
  layoutStrategies: Object.freeze([]),
});

function emptyLevel(displayName) {
  return {
    displayName,
    elementsPath: "/diagram/elements",
    relationshipsPath: "/diagram/relationships",
    labelField: "name",
    relationshipKinds: [],
    relationshipSemantics: {},
    relationshipLabelFields: [],
    elements: [],
    relationshipRules: [],
    relationshipKindLabels: {},
    relationshipVisualRules: [],
    badgeRules: [],
    semanticReferenceRules: [],
    semanticEdgeObjectRules: [],
    shortcutConnectorRules: [],
    workbench: {},
    scaffoldRecipes: [],
    boundedContext: {},
    viewDefinitions: [],
    universalSyntax: [],
    kernelSyntax: [],
    kernelNotation: [],
    complexityManagement: [],
    canvasPolicy: {},
    containmentPalettes: {},
    syntaxCoverage: {},
    strictnessModes: ["exploration", "methodology", "production"],
    constraints: [],
    rootTemplate: {
      name: "",
      diagram: {
        elements: [],
        relationships: [],
      },
    },
  };
}

function emptyTransformation() {
  return {
    enabled: false,
    elementMappings: [],
    relationshipMappings: [],
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
      "Backend modeling config is missing or malformed. Check /api/modeling/config and backend logs.",
    );
  }
  const normalized = clone(EMPTY_CONFIG);
  normalized.version = Number(raw.version || 0);
  normalized.dynamicPersistenceEnabled = raw.dynamicPersistenceEnabled !== false;
  normalized.defaultLevel = String(raw.defaultLevel || "");
  normalized.artifact = raw.artifact && typeof raw.artifact === "object" ? raw.artifact : {};
  normalized.assistant = raw.assistant && typeof raw.assistant === "object" ? raw.assistant : {};
  normalized.methodology =
    raw.methodology && typeof raw.methodology === "object" ? raw.methodology : {};
  normalized.artifactAction =
    raw.artifactAction && typeof raw.artifactAction === "object" ? raw.artifactAction : {};
  normalized.impactAnalysis =
    raw.impactAnalysis && typeof raw.impactAnalysis === "object" ? raw.impactAnalysis : {};
  normalized.diagramEditor =
    raw.diagramEditor && typeof raw.diagramEditor === "object"
      ? { ...raw.diagramEditor }
      : { ...EMPTY_CONFIG.diagramEditor };
  normalized.layoutStrategies = Array.isArray(raw.layoutStrategies) ? raw.layoutStrategies : [];

  const incomingLevels = raw.levels && typeof raw.levels === "object" ? raw.levels : {};
  const levelOrder = Array.isArray(raw.levelOrder) ? raw.levelOrder.map(String) : [];
  Object.keys(incomingLevels).forEach((level) => {
    if (!levelOrder.includes(level)) {
      levelOrder.push(level);
    }
  });
  if (!levelOrder.length) {
    throw configLoadError("Backend modeling config does not define any modeling levels.");
  }
  normalized.levelOrder = levelOrder;
  normalized.levels = {};
  for (const level of levelOrder) {
    const incoming = raw.levels?.[level];
    if (!incoming || typeof incoming !== "object") {
      throw configLoadError(
        `Backend modeling config is missing the '${level.toUpperCase()}' level definition.`,
      );
    }
    normalized.levels[level] = {
      displayName: String(incoming.displayName || level.toUpperCase()),
      elementsPath: String(incoming.elementsPath || "/diagram/elements"),
      relationshipsPath: String(incoming.relationshipsPath || "/diagram/relationships"),
      labelField: String(incoming.labelField || "name"),
      relationshipKinds: Array.isArray(incoming.relationshipKinds)
        ? incoming.relationshipKinds.map((kind) => String(kind))
        : [],
      relationshipSemantics:
        incoming.relationshipSemantics && typeof incoming.relationshipSemantics === "object"
          ? incoming.relationshipSemantics
          : {},
      relationshipLabelFields: Array.isArray(incoming.relationshipLabelFields)
        ? incoming.relationshipLabelFields.map(String)
        : [],
      elements: Array.isArray(incoming.elements) ? incoming.elements : [],
      relationshipRules: Array.isArray(incoming.relationshipRules)
        ? incoming.relationshipRules
        : [],
      relationshipKindLabels:
        incoming.relationshipKindLabels && typeof incoming.relationshipKindLabels === "object"
          ? incoming.relationshipKindLabels
          : {},
      relationshipVisualRules: Array.isArray(incoming.relationshipVisualRules)
        ? incoming.relationshipVisualRules
        : [],
      badgeRules: Array.isArray(incoming.badgeRules) ? incoming.badgeRules : [],
      semanticReferenceRules: Array.isArray(incoming.semanticReferenceRules)
        ? incoming.semanticReferenceRules
        : [],
      semanticReferenceKindMappings:
        incoming.semanticReferenceKindMappings &&
        typeof incoming.semanticReferenceKindMappings === "object"
          ? incoming.semanticReferenceKindMappings
          : {},
      semanticReferenceExclusions: Array.isArray(incoming.semanticReferenceExclusions)
        ? incoming.semanticReferenceExclusions
        : [],
      semanticEdgeObjectRules: Array.isArray(incoming.semanticEdgeObjectRules)
        ? incoming.semanticEdgeObjectRules
        : [],
      shortcutConnectorRules: Array.isArray(incoming.shortcutConnectorRules)
        ? incoming.shortcutConnectorRules
        : [],
      workbench:
        incoming.workbench && typeof incoming.workbench === "object" ? incoming.workbench : {},
      scaffoldRecipes: Array.isArray(incoming.scaffoldRecipes) ? incoming.scaffoldRecipes : [],
      boundedContext:
        incoming.boundedContext && typeof incoming.boundedContext === "object"
          ? incoming.boundedContext
          : {},
      viewDefinitions: Array.isArray(incoming.viewDefinitions) ? incoming.viewDefinitions : [],
      universalSyntax: Array.isArray(incoming.universalSyntax) ? incoming.universalSyntax : [],
      kernelSyntax: Array.isArray(incoming.kernelSyntax) ? incoming.kernelSyntax : [],
      kernelNotation: Array.isArray(incoming.kernelNotation)
        ? incoming.kernelNotation
        : Array.isArray(incoming.kernelSyntax)
          ? incoming.kernelSyntax
          : [],
      complexityManagement: Array.isArray(incoming.complexityManagement)
        ? incoming.complexityManagement
        : [],
      canvasPolicy:
        incoming.canvasPolicy && typeof incoming.canvasPolicy === "object"
          ? incoming.canvasPolicy
          : {},
      containmentPalettes:
        incoming.containmentPalettes && typeof incoming.containmentPalettes === "object"
          ? incoming.containmentPalettes
          : {},
      syntaxCoverage:
        incoming.syntaxCoverage && typeof incoming.syntaxCoverage === "object"
          ? incoming.syntaxCoverage
          : {},
      elementMappings: Array.isArray(incoming.elementMappings) ? incoming.elementMappings : [],
      cvsVersion: Number(incoming.cvsVersion || 0),
      cvsPrimitives:
        incoming.cvsPrimitives && typeof incoming.cvsPrimitives === "object"
          ? incoming.cvsPrimitives
          : {},
      cvsReferenceMappings: Array.isArray(incoming.cvsReferenceMappings)
        ? incoming.cvsReferenceMappings
        : [],
      cvsRelationshipMappings: Array.isArray(incoming.cvsRelationshipMappings)
        ? incoming.cvsRelationshipMappings
        : [],
      cvsMetamodelRef:
        incoming.cvsMetamodelRef && typeof incoming.cvsMetamodelRef === "object"
          ? incoming.cvsMetamodelRef
          : {},
      strictnessModes: Array.isArray(incoming.strictnessModes) ? incoming.strictnessModes : [],
      constraints: Array.isArray(incoming.constraints) ? incoming.constraints : [],
      rootTemplate:
        incoming.rootTemplate && typeof incoming.rootTemplate === "object"
          ? incoming.rootTemplate
          : clone(emptyLevel(level.toUpperCase()).rootTemplate),
      starterTemplate:
        incoming.starterTemplate && typeof incoming.starterTemplate === "object"
          ? incoming.starterTemplate
          : null,
      apiType: String(incoming.apiType || level),
      chatType: String(incoming.chatType || level.toUpperCase()),
      modelNameTemplate: String(incoming.modelNameTemplate || ""),
    };
  }

  const incomingTransformations = raw.transformations || {};
  normalized.transformations = {};
  for (const key of Object.keys(incomingTransformations)) {
    const incoming = incomingTransformations[key];
    if (!incoming || typeof incoming !== "object") {
      continue;
    }
    normalized.transformations[key] = {
      ...emptyTransformation(),
      ...incoming,
      enabled: Boolean(incoming.enabled),
      elementMappings: Array.isArray(incoming.elementMappings) ? incoming.elementMappings : [],
      relationshipMappings: Array.isArray(incoming.relationshipMappings)
        ? incoming.relationshipMappings
        : [],
    };
  }
  return normalized;
}

export async function loadModelingConfig({ silent = false } = {}) {
  try {
    const config = await api("/modeling/config");
    state.modelingConfig.config = ensureConfigShape(config);
    state.modelingConfig.error = null;
    applyModelingRuntimeConfig(state.modelingConfig.config);
    initializeModelingRuntimeState(state.modelingConfig.config);
    return state.modelingConfig.config;
  } catch (error) {
    state.modelingConfig.config = null;
    state.modelingConfig.error = error;
    if (!silent) {
      console.error("Modeling config load failed", error);
      setError(error, { prefix: "Backend modeling config unavailable." });
    }
    throw error;
  }
}

function emptyTabState(typeKey, level) {
  return {
    modelId: null,
    modelRevision: 0,
    baseModel: null,
    diagram: emptyDiagram(typeKey),
    modelName: level.modelNameTemplate,
    graph: null,
    views: null,
    fragments: null,
    activeViewId: null,
    dirty: false,
  };
}

export function initializeModelingRuntimeState(config) {
  rebuildModelingConfigIndexes(config);
  const levels = config?.levels || {};
  const order = Array.isArray(config?.levelOrder) ? config.levelOrder : Object.keys(levels);
  order.forEach((typeKey) => {
    const level = levels[typeKey] || {};
    state.tabs[typeKey] ??= emptyTabState(typeKey, level);
    state.modelsCache[typeKey] ??= [];
    state.paletteSearch[typeKey] ??= "";
    state.paletteGroupCollapsed[typeKey] ??= {};
    state.undo.diagramHistory[typeKey] ??= [];
  });
  if (!state.activeType || !order.includes(state.activeType)) {
    state.activeType =
      config.defaultLevel && order.includes(config.defaultLevel)
        ? config.defaultLevel
        : order[0] || "";
    state.diagram = emptyDiagram(state.activeType);
    state.visibleGraph = emptyDiagram(state.activeType);
  }
}

export function modelingLevelConfig(typeKey = state.activeType) {
  const config = state.modelingConfig.config;
  if (!config?.levels?.[typeKey]) {
    throw configLoadError(
      `No backend modeling config is loaded for ${String(typeKey).toUpperCase()}.`,
    );
  }
  return config.levels[typeKey];
}

export function modelingLevelKeys() {
  const config = state.modelingConfig.config;
  return Array.isArray(config?.levelOrder) && config.levelOrder.length
    ? [...config.levelOrder]
    : Object.keys(config?.levels || {});
}

export function modelingArtifactKey() {
  const key = String(state.modelingConfig.config?.artifact?.key || "").trim();
  if (!key) throw configLoadError("Backend modeling config does not define an artifact level key.");
  return key;
}

export function isArtifactLevel(typeKey = state.activeType) {
  return String(typeKey || "") === modelingArtifactKey();
}

export function modelingAssistantConfig() {
  const assistant = state.modelingConfig.config?.assistant;
  if (!assistant || typeof assistant !== "object") {
    throw configLoadError("Backend modeling config does not define assistant settings.");
  }
  return assistant;
}

export function modelingMethodologyConfig() {
  const methodology = state.modelingConfig.config?.methodology;
  if (!methodology || typeof methodology !== "object") {
    throw configLoadError("Backend modeling config does not define methodology settings.");
  }
  return methodology;
}

export function modelingImpactConfig() {
  const impact = state.modelingConfig.config?.impactAnalysis;
  return impact && typeof impact === "object" ? impact : {};
}

export function modelingDiagramEditorConfig() {
  const editor = state.modelingConfig.config?.diagramEditor;
  return editor && typeof editor === "object" ? editor : {};
}

export function modelingLayoutStrategies() {
  const strategies = state.modelingConfig.config?.layoutStrategies;
  return Array.isArray(strategies) ? strategies : [];
}

export function modelingDefaultLayoutStrategy() {
  const configured = String(modelingDiagramEditorConfig().defaultLayoutStrategy || "").trim();
  if (configured) {
    return configured.toUpperCase();
  }
  const first = modelingLayoutStrategies()[0];
  return String(first?.id || "").toUpperCase();
}

export function modelingIconAliases() {
  const aliases = modelingDiagramEditorConfig().iconAliases;
  return aliases && typeof aliases === "object" ? aliases : {};
}

export function modelingPlaceholderIcon() {
  return String(modelingDiagramEditorConfig().placeholderIcon || "");
}

export function modelingIconBasePath() {
  return String(modelingDiagramEditorConfig().iconBasePath || "/assets/icons").replace(/\/$/, "");
}

export function modelingDefaultNotation() {
  const notation = modelingDiagramEditorConfig().defaultNotation;
  return notation && typeof notation === "object" ? notation : {};
}

export function modelingRoleSize(typeKey = state.activeType, role = "node") {
  const sizes = modelingCanvasPolicy(typeKey).roleSizes;
  if (sizes && typeof sizes === "object" && sizes[role]) {
    return sizes[role];
  }
  return null;
}

export function modelingKernelTypes() {
  const types = new Set();
  for (const levelKey of modelingLevelKeys()) {
    for (const entry of modelingLevelConfig(levelKey).elements || []) {
      if (entry?.abstract && entry?.type) {
        types.add(String(entry.type));
      }
    }
  }
  return types;
}

export function resolveModelingIconSource(icon) {
  const normalized = String(icon || "").trim();
  if (!normalized) {
    return modelingPlaceholderIcon();
  }
  const aliases = modelingIconAliases();
  const resolved = aliases[normalized] || normalized;
  if (resolved.startsWith("/") || resolved.startsWith(".") || resolved.endsWith(".svg")) {
    return resolved;
  }
  if (/^[a-z0-9_-]+$/i.test(resolved)) {
    return `${modelingIconBasePath()}/${resolved}.svg`;
  }
  return modelingPlaceholderIcon();
}

export function isModelingLevel(typeKey) {
  return modelingLevelKeys().includes(typeKey);
}

export function defaultModelingLevel() {
  const config = state.modelingConfig.config;
  return config?.defaultLevel && isModelingLevel(config.defaultLevel)
    ? config.defaultLevel
    : modelingLevelKeys()[0] || "";
}

export function modelingLevelListLabel() {
  const names = modelingLevelKeys().map((key) => modelingLevelConfig(key).displayName || key);
  if (!names.length) {
    return "a modeling level";
  }
  if (names.length === 1) {
    return names[0];
  }
  return `${names.slice(0, -1).join(", ")}, or ${names[names.length - 1]}`;
}

export function modelingCanvasPaletteTypes(typeKey = state.activeType, { ownerType = null } = {}) {
  if (ownerType) {
    return modelingContainmentPalette(typeKey, ownerType);
  }
  return modelingPalette(typeKey);
}

export function modelingStandalonePaletteType(typeKey, type) {
  const roles = new Set(modelingStandalonePaletteRoles(typeKey));
  const rootType = modelingRootType(typeKey);
  const normalizedType = String(type || "").trim();
  if (!normalizedType || normalizedType === rootType) {
    return false;
  }
  try {
    const entry = modelingElementDefinition(typeKey, normalizedType);
    return Boolean(
      entry?.creatable === true &&
        !entry?.abstract &&
        !entry?.relationshipElement &&
        !entry?.containedOnly &&
        !entry?.supportOnly &&
        roles.has(String(entry?.visualRole || "node")),
    );
  } catch {
    return false;
  }
}

export function modelingPalette(typeKey = state.activeType) {
  return (modelingLevelConfig(typeKey).elements || [])
    .map((entry) =>
      modelingStandalonePaletteType(typeKey, entry?.type) ? String(entry.type || "").trim() : "",
    )
    .filter(Boolean);
}

export function modelingLevelLabel(typeKey = state.activeType) {
  const root = modelingLevelConfig(typeKey).rootTemplate || {};
  return String(root.modelLevel || modelingLevelConfig(typeKey).chatType || typeKey.toUpperCase());
}

export function modelingCanvasPolicy(typeKey = state.activeType) {
  const policy = modelingLevelConfig(typeKey).canvasPolicy;
  return policy && typeof policy === "object" ? policy : {};
}

export function modelingContainerFocusPolicy(typeKey = state.activeType) {
  const focus = modelingCanvasPolicy(typeKey).containerFocus;
  return focus && typeof focus === "object" ? focus : {};
}

export function modelingStandalonePaletteRoles(typeKey = state.activeType) {
  const roles = modelingCanvasPolicy(typeKey).standalonePaletteRoles;
  return Array.isArray(roles) && roles.length ? roles.map(String) : [];
}

export function modelingContainmentPalette(typeKey, ownerType) {
  const ownerKey = String(ownerType || "");
  const palettes = modelingLevelConfig(typeKey).containmentPalettes;
  if (palettes && typeof palettes === "object") {
    const entry = palettes[ownerKey];
    if (entry && typeof entry === "object" && Array.isArray(entry.types) && entry.types.length) {
      return entry.types.map(String).filter(Boolean);
    }
  }
  const types = new Set();
  modelingContainmentsForType(typeKey, ownerKey)
    .filter((entry) => !entry.relationshipOnly)
    .forEach((entry) => {
      (entry.types || []).forEach((type) => {
        if (type) {
          types.add(String(type));
        }
      });
    });
  return [...types];
}

export function modelingContainmentEntryForChildType(typeKey, ownerType, childType) {
  const ownerKey = String(ownerType || "");
  const palettes = modelingLevelConfig(typeKey).containmentPalettes;
  const entry = palettes?.[ownerKey];
  const features = Array.isArray(entry?.features) ? entry.features : [];
  const configured =
    features.find((feature) =>
      (feature?.types || []).some((type) => modelingTypeMatches(typeKey, type, childType)),
    ) || null;
  if (configured) {
    return configured;
  }
  const containment = modelingContainmentsForType(typeKey, ownerKey).find(
    (candidate) =>
      !candidate.relationshipOnly &&
      (candidate.types || []).some((type) => modelingTypeMatches(typeKey, type, childType)),
  );
  if (!containment) {
    return null;
  }
  return {
    feature: containment.feature,
    targetType: containment.targetType,
    types: containment.types,
    many: containment.many !== false,
  };
}

const elementDefinitionByType = new Map();
const concreteTypesCache = new Map();
const containmentCache = new Map();

function rebuildModelingConfigIndexes(config) {
  elementDefinitionByType.clear();
  concreteTypesCache.clear();
  containmentCache.clear();
  const levels = config?.levels || {};
  Object.keys(levels).forEach((typeKey) => {
    const byType = new Map();
    (levels[typeKey].elements || []).forEach((entry) => {
      if (entry?.type) {
        byType.set(entry.type, entry);
      }
    });
    elementDefinitionByType.set(typeKey, byType);
  });
}

function containmentCacheKey(typeKey, ownerType) {
  return `${typeKey}:${ownerType}`;
}

function concreteTypesCacheKey(typeKey, expectedType) {
  return `${typeKey}:${expectedType}`;
}

export function modelingElementDefinition(typeKey, elementType) {
  return elementDefinitionByType.get(typeKey)?.get(elementType) || null;
}

function normalizeViewDefinitionName(value) {
  return String(value || "")
    .trim()
    .toLowerCase()
    .replaceAll(/[^a-z0-9]+/g, " ");
}

export function modelingViewDefinition(typeKey, view) {
  const definitions = modelingLevelConfig(typeKey).viewDefinitions || [];
  const definitionId = String(view?.definitionId || view?.sourceDefinitionId || "")
    .trim()
    .toLowerCase();
  const viewpoint = String(view?.viewpoint || "")
    .trim()
    .toLowerCase();
  const kind = String(view?.kind || view?.viewType || "")
    .trim()
    .toLowerCase()
    .replaceAll("_", "-");
  const nameKey = normalizeViewDefinitionName(view?.name || view?.displayName || "");
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
        .toLowerCase()
        .replaceAll("_", "-");
      const displayName = normalizeViewDefinitionName(
        definition?.displayName || definition?.name || "",
      );
      return (
        (definitionId && id === definitionId) ||
        (viewpoint && definitionViewpoint === viewpoint) ||
        (kind && (id === kind || viewType === kind)) ||
        (nameKey && displayName === nameKey)
      );
    }) || null
  );
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
  return key
    .split(".")
    .reduce((value, part) => (value && typeof value === "object" ? value[part] : undefined), edge);
}

function stringListMatches(values, actual) {
  if (!Array.isArray(values) || !values.length) {
    return false;
  }
  const text = String(actual || "").toUpperCase();
  return values.map((value) => String(value || "").toUpperCase()).includes(text);
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
    return String(actual || "").toUpperCase() === String(matcher.equals || "").toUpperCase();
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
  return (
    Array.isArray(rule.matchFields) &&
    rule.matchFields.some((matcher) => fieldMatcherMatches(matcher, edge))
  );
}

function applyVisualRule(presentation, rule) {
  presentation.className = appendClassName(presentation.className, rule.className);
  if (Object.hasOwn(rule, "markerStart")) {
    presentation.markerStart = String(rule.markerStart || "");
  }
  if (Object.hasOwn(rule, "markerEnd")) {
    presentation.markerEnd = String(rule.markerEnd || "");
  }
  const style = {};
  ["stroke", "lineWidth", "opacity", "lineDash"].forEach((field) => {
    if (Object.hasOwn(rule, field)) {
      let value = rule[field];
      if (field === "stroke") {
        value = resolveThemeColor(value, resolveThemeColor("#64748b", "#64748b"));
      }
      style[field] = value;
    }
  });
  presentation.style = { ...presentation.style, ...style };
}

export function modelingRelationshipPresentation(typeKey, edge) {
  const kind = String(edge?.kind || "").toUpperCase();
  const relationship = state.graph?.relationshipsById?.get(edge?.id) || edge || {};
  const presentation = {
    className: "",
    markerStart: "",
    markerEnd: "arrow",
    style: {},
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

export function modelingSemanticEdgeObjectRules(typeKey = state.activeType) {
  return modelingLevelConfig(typeKey).semanticEdgeObjectRules || [];
}

export function modelingShortcutConnectorRules(typeKey = state.activeType) {
  return modelingLevelConfig(typeKey).shortcutConnectorRules || [];
}

export function modelingRootType(typeKey = state.activeType) {
  const root = modelingLevelConfig(typeKey).rootTemplate || {};
  return String(root.eClass || root.type || "");
}

export function modelingConcreteTypesFor(typeKey, expectedType) {
  const cacheKey = concreteTypesCacheKey(typeKey, expectedType);
  if (concreteTypesCache.has(cacheKey)) {
    return concreteTypesCache.get(cacheKey);
  }
  const level = modelingLevelConfig(typeKey);
  const result = (level.elements || [])
    .filter((entry) => {
      if (!entry?.type || entry.abstract) {
        return false;
      }
      return modelingTypeMatches(typeKey, expectedType, entry.type);
    })
    .map((entry) => entry.type);
  concreteTypesCache.set(cacheKey, result);
  return result;
}

export function modelingRelationshipElementTypes(typeKey = state.activeType) {
  return (
    (modelingLevelConfig(typeKey).elements || [])
      // Some PIM classes are drawn as relationship-shaped nodes but are still real semantic
      // containment objects (notably Schedule and Trigger). Only non-creatable relationship
      // records are edge-only and may be omitted from the semantic element tree during Save/XMI
      // reconstruction.
      .filter((entry) => entry?.relationshipElement === true && entry?.containedOnly !== true)
      .map((entry) => entry.type)
  );
}

function containmentTitle(feature) {
  return String(feature || "")
    .replaceAll(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replaceAll(/[-_]+/g, " ")
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

export function modelingContainmentsForType(typeKey, ownerType) {
  const cacheKey = containmentCacheKey(typeKey, ownerType);
  if (containmentCache.has(cacheKey)) {
    return containmentCache.get(cacheKey);
  }
  const definition = modelingElementDefinition(typeKey, ownerType);
  const relationshipTypes = new Set(modelingRelationshipElementTypes(typeKey));
  const result = (definition?.references || [])
    .filter(
      (reference) =>
        reference?.containment && !reference.readonly && reference.name && reference.targetType,
    )
    .map((reference) => {
      const concreteTypes = modelingConcreteTypesFor(typeKey, reference.targetType);
      const types = concreteTypes.length ? concreteTypes : [reference.targetType];
      return {
        feature: reference.name,
        targetType: reference.targetType,
        types,
        required: Boolean(reference.required),
        many: reference.many !== false,
        singleton: reference.many === false,
        title: containmentTitle(reference.name),
        relationshipOnly: types.length > 0 && types.every((type) => relationshipTypes.has(type)),
      };
    });
  containmentCache.set(cacheKey, result);
  return result;
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
      ? definition.supertypes.map(String)
      : [];
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
  return Boolean(rule?.edgeObjectType || rule?.allowWildcardInStrictMode);
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

function manualRelationshipKinds(typeKey, kinds) {
  const semantics = modelingLevelConfig(typeKey).relationshipSemantics || {};
  const internalKinds = new Set(
    [semantics.traceKind]
      .map((kind) =>
        String(kind || "")
          .trim()
          .toUpperCase(),
      )
      .filter(Boolean),
  );
  return normalizeKinds(kinds).filter(
    (kind) =>
      !internalKinds.has(
        String(kind || "")
          .trim()
          .toUpperCase(),
      ),
  );
}

export function modelingLegalKinds(typeKey, sourceType, targetType) {
  const rules = modelingLevelConfig(typeKey).relationshipRules || [];
  const strictness = state.modelingStrictness || "methodology";
  const matched = [];
  for (const rule of rules) {
    if (rule.containment === true) {
      continue;
    }
    if (
      strictness !== "exploration" &&
      isWildcardRule(rule) &&
      !isMethodologyWildcardExempt(rule)
    ) {
      continue;
    }
    if (
      modelingTypeMatches(typeKey, rule.sourceType, sourceType) &&
      modelingTypeMatches(typeKey, rule.targetType, targetType) &&
      Array.isArray(rule.allowedKinds)
    ) {
      matched.push(rule);
    }
  }
  if (!matched.length) {
    if (strictness === "exploration") {
      return manualRelationshipKinds(typeKey, modelingLevelConfig(typeKey).relationshipKinds);
    }
    return [];
  }
  const bestSpecificity = Math.max(...matched.map(ruleSpecificity));
  const winners = matched.filter((rule) => ruleSpecificity(rule) === bestSpecificity);
  return manualRelationshipKinds(
    typeKey,
    winners.flatMap((rule) => rule.allowedKinds),
  );
}

export function modelingLegalKindsBetween(typeKey, typeA, typeB) {
  return normalizeKinds([
    ...modelingLegalKinds(typeKey, typeA, typeB),
    ...modelingLegalKinds(typeKey, typeB, typeA),
  ]);
}

export function modelingResolveEdgeEndpoints(
  typeKey,
  nodeA,
  nodeB,
  kind,
  preferredSourceId,
  preferredTargetId,
) {
  const normalizedKind = String(kind || "").trim();
  if (!nodeA || !nodeB || !normalizedKind) {
    return null;
  }
  const forwardLegal = modelingLegalKinds(typeKey, nodeA.type, nodeB.type).includes(normalizedKind);
  const reverseLegal = modelingLegalKinds(typeKey, nodeB.type, nodeA.type).includes(normalizedKind);
  if (!forwardLegal && !reverseLegal) {
    return null;
  }
  if (forwardLegal && reverseLegal) {
    if (nodeA.id === preferredSourceId && nodeB.id === preferredTargetId) {
      return { sourceId: nodeA.id, targetId: nodeB.id, kind: normalizedKind };
    }
    if (nodeA.id === preferredTargetId && nodeB.id === preferredSourceId) {
      return { sourceId: nodeB.id, targetId: nodeA.id, kind: normalizedKind };
    }
    return { sourceId: nodeA.id, targetId: nodeB.id, kind: normalizedKind };
  }
  if (forwardLegal) {
    return { sourceId: nodeA.id, targetId: nodeB.id, kind: normalizedKind };
  }
  return { sourceId: nodeB.id, targetId: nodeA.id, kind: normalizedKind };
}

export function modelingRootTemplate(typeKey, modelName) {
  const root = clone(modelingLevelConfig(typeKey).rootTemplate || {});
  root.name = modelName;
  root.diagram ??= { elements: [], relationships: [] };
  root.diagram.elements ??= [];
  root.diagram.relationships ??= [];
  root.graph ??= {
    elements: [],
    relationships: [],
    traceLinks: [],
    assumptions: [],
    validationIssues: [],
    manualBacklog: [],
  };
  root.views ??= [];
  root.fragments ??= [];
  return root;
}

export function modelingLabelField(typeKey) {
  return String(modelingLevelConfig(typeKey).labelField || "name");
}

export function transformationKeyForLevel(levelKey) {
  const transformations = state.modelingConfig.config?.transformations || {};
  return (
    Object.entries(transformations).find(([, transformation]) => {
      return transformation?.sourceLevel === levelKey || transformation?.level === levelKey;
    })?.[0] || ""
  );
}

export function transformationForLevel(levelKey = state.activeType) {
  const key = transformationKeyForLevel(levelKey);
  return key ? state.modelingConfig.config?.transformations?.[key] || null : null;
}
