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
      viewDefinitions: Array.isArray(incoming.viewDefinitions)
          ? incoming.viewDefinitions : [],
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
      (entry) => String(entry.type || "").trim()).filter(Boolean);
}

export function modelingElementDefinition(typeKey, elementType) {
  const level = modelingLevelConfig(typeKey);
  return (level.elements || []).find((entry) => entry.type === elementType)
      || null;
}

function matchesRuleType(expected, actual) {
  if (!expected || expected === "*") {
    return true;
  }
  return expected === actual;
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

const CIM_REFERENCE_RULES = Object.freeze([
  ["Actor", "Command", ["ISSUES"]],
  ["Actor", "Query", ["ISSUES"]],
  ["Actor", "BusinessEvent", ["OBSERVES"]],
  ["Role", "Actor", ["ASSIGNED_TO"]],
  ["ExternalSystem", "BusinessEvent", ["PRODUCES"]],
  ["BusinessEvent", "ExternalSystem", ["CONSUMED_BY"]],
  ["BusinessCapability", "BusinessGoal", ["SUPPORTS"]],
  ["BusinessCapability", "Requirement", ["REALIZES"]],
  ["BusinessCapability", "Command", ["CONTAINS_COMMAND"]],
  ["BusinessCapability", "Query", ["CONTAINS_QUERY"]],
  ["BusinessCapability", "BusinessEvent", ["CONTAINS_EVENT"]],
  ["BusinessCapability", "DomainEntity", ["MANAGES"]],
  ["BusinessCapability", "BusinessCapability", ["DEPENDS_ON"]],
  ["BoundedContextCandidate", "BusinessCapability", ["CONTAINS"]],
  ["BoundedContextCandidate", "DomainEntity", ["CONTAINS"]],
  ["BoundedContextCandidate", "Command", ["CONTAINS"]],
  ["BoundedContextCandidate", "Query", ["CONTAINS"]],
  ["BoundedContextCandidate", "BusinessEvent", ["CONTAINS"]],
  ["DomainEntity", "DomainEntity", ["DOMAIN_RELATIONSHIP"]],
  ["DomainEntity", "ValueObject", ["DOMAIN_RELATIONSHIP"]],
  ["ValueObject", "DomainEntity", ["DOMAIN_RELATIONSHIP"]],
  ["ValueObject", "ValueObject", ["DOMAIN_RELATIONSHIP"]],
  ["AggregateCandidate", "DomainEntity", ["ROOT", "MEMBER"]],
  ["Command", "BusinessEvent", ["EXPECTS", "REJECTS_WITH"]],
  ["Command", "BusinessError", ["MAY_FAIL_WITH"]],
  ["Command", "AggregateCandidate", ["TARGETS"]],
  ["Command", "BusinessCapability", ["HANDLED_BY"]],
  ["Query", "DomainEntity", ["READS"]],
  ["BusinessEvent", "Policy", ["TRIGGERS"]],
  ["BusinessEvent", "BusinessProcess", ["FEEDS"]],
  ["Policy", "Command", ["EMITS_COMMAND", "GUARDS"]],
  ["Policy", "BusinessEvent", ["EMITS_EVENT"]],
  ["Policy", "Query", ["CONSTRAINS"]],
  ["BusinessProcess", "ProcessStep", ["CONTAINS"]],
  ["StartStep", "ProcessStep", ["TRANSITION"]],
  ["ProcessStep", "ProcessStep", ["TRANSITION"]],
  ["ProcessStep", "EndStep", ["TRANSITION"]],
  ["DecisionStep", "DecisionTable", ["USES"]],
  ["DecisionRule", "Command", ["RESULTS_IN"]],
  ["DecisionRule", "BusinessEvent", ["RESULTS_IN"]],
  ["NonFunctionalRequirement", "*", ["CONSTRAINS"]],
  ["SecurityConstraint", "*", ["CONSTRAINS"]],
  ["PrivacyConstraint", "*", ["CONSTRAINS"]],
  ["ComplianceConstraint", "*", ["CONSTRAINS"]],
  ["Risk", "*", ["ATTACHED_TO"]],
  ["Assumption", "*", ["ATTACHED_TO"]],
  ["Hotspot", "*", ["ATTACHED_TO"]]
]);

function cimReferenceKinds(sourceType, targetType) {
  const kinds = [];
  for (const [source, target, ruleKinds] of CIM_REFERENCE_RULES) {
    if (matchesRuleType(source, sourceType) && matchesRuleType(target,
        targetType)) {
      kinds.push(...ruleKinds);
    }
  }
  return normalizeKinds(kinds);
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
    if (matchesRuleType(rule.sourceType, sourceType)
        && matchesRuleType(rule.targetType, targetType)
        && Array.isArray(rule.allowedKinds)) {
      matched.push(rule);
    }
  }
  if (!matched.length) {
    if (typeKey === "cim") {
      const semanticKinds = cimReferenceKinds(sourceType, targetType);
      if (semanticKinds.length) {
        return semanticKinds;
      }
    }
    if (strictness === "exploration") {
      return normalizeKinds([
        ...modelingLevelConfig(typeKey).relationshipKinds,
        ...(typeKey === "cim" ? cimReferenceKinds(sourceType, targetType) : [])
      ]);
    }
    return [];
  }
  const bestSpecificity = Math.max(...matched.map(ruleSpecificity));
  const winners = matched.filter(
      (rule) => ruleSpecificity(rule) === bestSpecificity);
  return normalizeKinds([
    ...winners.flatMap((rule) => rule.allowedKinds),
    ...(typeKey === "cim" ? cimReferenceKinds(sourceType, targetType) : [])
  ]);
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
