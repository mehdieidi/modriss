import {state} from './state.js';
import {autoLayoutIfStacked, emptyDiagram, genId} from './utils.js';
import {
  modelingElementDefinition,
  modelingLevelConfig,
  modelingSemanticReferenceRules,
  modelingTypeMatches
} from './modeling-config-data.js';
import {
  addReferenceValue,
  cimSemanticElementsFromRoot,
  cimSemanticRelationshipsFromRoot,
  cimTypeMatches,
  cimTypeOf,
  populateCimRootContainments,
  removeReferenceValue,
  semanticEdgeObjectSpec
} from './cim-model-utils.js';
import {
  pimRelationshipSemanticCopy,
  pimSemanticEdgeObjectSpec,
  pimSemanticElementsFromRoot,
  pimSemanticRelationshipsFromRoot,
  pimTypeMatches,
  pimTypeOf,
  populatePimRootContainments
} from './pim-model-utils.js';

const MODEL_LEVEL = {
  cim: "CIM",
  pim: "PIM",
  psm: "AWS_PSM"
};

const LEVEL_ALIASES = {
  cim: new Set(["", "CIM"]),
  pim: new Set(["", "PIM"]),
  psm: new Set(["", "PSM", "AWS_PSM"])
};

const ROOT_SCOPE_TYPE = {
  cim: "CIMModel",
  pim: "PIMModel",
  psm: "PsmModel"
};

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

const FRAGMENT_KIND_BY_TYPE = {
  BoundedContextCandidate: "BOUNDED_CONTEXT",
  BusinessCapability: "CAPABILITY",
  BusinessProcess: "BUSINESS_PROCESS",
  AggregateCandidate: "AGGREGATE",
  ServerlessService: "SERVERLESS_SERVICE",
  DeploymentUnit: "DEPLOYMENT_UNIT",
  Workflow: "WORKFLOW",
  Api: "API_SURFACE",
  EventChannel: "EVENT_FLOW",
  SamStack: "SAM_STACK",
  AwsStage: "AWS_STAGE",
  ApiGatewayApi: "API_SURFACE",
  EventBridgeBus: "EVENT_FLOW",
  StepFunctionStateMachine: "WORKFLOW",
  IamRole: "IAM_SLICE"
};

const CONTAINMENT_KINDS = new Set(["CONTAINS", "OWNS", "DEPLOYS"]);

const CIM_REF_EDGE_RULES = Object.freeze([
  {
    sourceType: "Actor",
    feature: "issuesCommands",
    targetType: "Command",
    kind: "ISSUES"
  },
  {
    sourceType: "Actor",
    feature: "issuesQueries",
    targetType: "Query",
    kind: "ISSUES"
  },
  {
    sourceType: "Actor",
    feature: "observesEvents",
    targetType: "BusinessEvent",
    kind: "OBSERVES"
  },
  {
    sourceType: "Actor",
    feature: "playsRoles",
    targetType: "Role",
    kind: "PLAYS_ROLE"
  },
  {
    sourceType: "Role",
    feature: "assignedTo",
    targetType: "Actor",
    kind: "ASSIGNED_TO"
  },
  {
    sourceType: "Stakeholder",
    feature: "ownsGoals",
    targetType: "BusinessGoal",
    kind: "OWNS"
  },
  {
    sourceType: "BusinessGoal",
    feature: "measuredBy",
    targetType: "KPI",
    kind: "MEASURED_BY"
  },
  {
    sourceType: "BusinessGoal",
    feature: "refinedBy",
    targetType: "BusinessCapability",
    kind: "REFINED_BY"
  },
  {
    sourceType: "ExternalSystem",
    feature: "producedEvents",
    targetType: "BusinessEvent",
    kind: "PRODUCES"
  },
  {
    sourceType: "ExternalSystem",
    feature: "consumedEvents",
    targetType: "BusinessEvent",
    kind: "CONSUMED_BY",
    reverse: true
  },
  {
    sourceType: "ExternalSystem",
    feature: "exchangedInformation",
    targetType: "InformationItem",
    kind: "EXCHANGES_INFORMATION"
  },
  {
    sourceType: "BusinessCapability",
    feature: "supports",
    targetType: "BusinessGoal",
    kind: "SUPPORTS"
  },
  {
    sourceType: "BusinessCapability",
    feature: "realizesRequirements",
    targetType: "Requirement",
    kind: "REALIZES"
  },
  {
    sourceType: "BusinessCapability",
    feature: "containsCommands",
    targetType: "Command",
    kind: "CONTAINS_COMMAND"
  },
  {
    sourceType: "BusinessCapability",
    feature: "containsQueries",
    targetType: "Query",
    kind: "CONTAINS_QUERY"
  },
  {
    sourceType: "BusinessCapability",
    feature: "containsEvents",
    targetType: "BusinessEvent",
    kind: "CONTAINS_EVENT"
  },
  {
    sourceType: "BusinessCapability",
    feature: "managesEntities",
    targetType: "DomainEntity",
    kind: "MANAGES"
  },
  {
    sourceType: "BusinessCapability",
    feature: "ownsProcesses",
    targetType: "BusinessProcess",
    kind: "OWNS_PROCESS"
  },
  {
    sourceType: "BoundedContextCandidate",
    feature: "capabilities",
    targetType: "BusinessCapability",
    kind: "CONTAINS"
  },
  {
    sourceType: "BoundedContextCandidate",
    feature: "entities",
    targetType: "DomainEntity",
    kind: "CONTAINS"
  },
  {
    sourceType: "BoundedContextCandidate",
    feature: "commands",
    targetType: "Command",
    kind: "CONTAINS"
  },
  {
    sourceType: "BoundedContextCandidate",
    feature: "queries",
    targetType: "Query",
    kind: "CONTAINS"
  },
  {
    sourceType: "BoundedContextCandidate",
    feature: "events",
    targetType: "BusinessEvent",
    kind: "CONTAINS"
  },
  {
    sourceType: "BoundedContextCandidate",
    feature: "policies",
    targetType: "Policy",
    kind: "CONTAINS"
  },
  {
    sourceType: "DomainEntity",
    feature: "attributes",
    targetType: "InformationItem",
    kind: "HAS_ATTRIBUTE"
  },
  {
    sourceType: "ValueObject",
    feature: "attributes",
    targetType: "InformationItem",
    kind: "HAS_ATTRIBUTE"
  },
  {
    sourceType: "AggregateCandidate",
    feature: "root",
    targetType: "DomainEntity",
    kind: "ROOT"
  },
  {
    sourceType: "AggregateCandidate",
    feature: "members",
    targetType: "DomainEntity",
    kind: "MEMBER"
  },
  {
    sourceType: "AggregateCandidate",
    feature: "handledCommands",
    targetType: "Command",
    kind: "HANDLES"
  },
  {
    sourceType: "AggregateCandidate",
    feature: "emittedEvents",
    targetType: "BusinessEvent",
    kind: "EMITS_EVENT"
  },
  {
    sourceType: "Command",
    feature: "issuedBy",
    targetType: "Actor",
    kind: "ISSUES",
    reverse: true
  },
  {
    sourceType: "Command",
    feature: "expectedEvents",
    targetType: "BusinessEvent",
    kind: "EXPECTS"
  },
  {
    sourceType: "Command",
    feature: "input",
    targetType: "InformationItem",
    kind: "INPUT"
  },
  {
    sourceType: "Command",
    feature: "preconditions",
    targetType: "Condition",
    kind: "PRECONDITION"
  },
  {
    sourceType: "Command",
    feature: "rejectionEvents",
    targetType: "BusinessEvent",
    kind: "REJECTS_WITH"
  },
  {
    sourceType: "Command",
    feature: "possibleErrors",
    targetType: "BusinessError",
    kind: "MAY_FAIL_WITH"
  },
  {
    sourceType: "Command",
    feature: "targetAggregate",
    targetType: "AggregateCandidate",
    kind: "TARGETS"
  },
  {
    sourceType: "Command",
    feature: "targetCapability",
    targetType: "BusinessCapability",
    kind: "HANDLED_BY"
  },
  {
    sourceType: "Query",
    feature: "issuedBy",
    targetType: "Actor",
    kind: "ISSUES",
    reverse: true
  },
  {
    sourceType: "Query",
    feature: "reads",
    targetType: "DomainEntity",
    kind: "READS"
  },
  {
    sourceType: "Query",
    feature: "input",
    targetType: "InformationItem",
    kind: "INPUT"
  },
  {
    sourceType: "Query",
    feature: "output",
    targetType: "InformationItem",
    kind: "OUTPUT"
  },
  {
    sourceType: "Query",
    feature: "targetCapability",
    targetType: "BusinessCapability",
    kind: "HANDLED_BY"
  },
  {
    sourceType: "BusinessEvent",
    feature: "payload",
    targetType: "InformationItem",
    kind: "PAYLOAD"
  },
  {
    sourceType: "BusinessEvent",
    feature: "affects",
    targetType: "DomainEntity",
    kind: "AFFECTS"
  },
  {
    sourceType: "BusinessError",
    feature: "emittedEvents",
    targetType: "BusinessEvent",
    kind: "EMITS_EVENT"
  },
  {
    sourceType: "Condition",
    feature: "referencedInformation",
    targetType: "InformationItem",
    kind: "REFERENCES"
  },
  {
    sourceType: "Condition",
    feature: "referencedConcepts",
    targetType: "DomainConcept",
    kind: "REFERENCES"
  },
  {
    sourceType: "BusinessEvent",
    feature: "causedByExternalSystems",
    targetType: "ExternalSystem",
    kind: "PRODUCES",
    reverse: true
  },
  {
    sourceType: "BusinessEvent",
    feature: "consumedByPolicies",
    targetType: "Policy",
    kind: "TRIGGERS"
  },
  {
    sourceType: "BusinessEvent",
    feature: "consumedByProcesses",
    targetType: "BusinessProcess",
    kind: "FEEDS"
  },
  {
    sourceType: "BusinessEvent",
    feature: "consumedByExternalSystems",
    targetType: "ExternalSystem",
    kind: "CONSUMED_BY"
  },
  {
    sourceType: "Policy",
    feature: "triggeredBy",
    targetType: "BusinessEvent",
    kind: "TRIGGERS",
    reverse: true
  },
  {
    sourceType: "Policy",
    feature: "emitsCommands",
    targetType: "Command",
    kind: "EMITS_COMMAND"
  },
  {
    sourceType: "Policy",
    feature: "emitsEvents",
    targetType: "BusinessEvent",
    kind: "EMITS_EVENT"
  },
  {
    sourceType: "Policy",
    feature: "guards",
    targetType: "Command",
    kind: "GUARDS"
  },
  {
    sourceType: "Policy",
    feature: "constrainsQueries",
    targetType: "Query",
    kind: "CONSTRAINS"
  },
  {
    sourceType: "Policy",
    feature: "decisionTable",
    targetType: "DecisionTable",
    kind: "USES"
  },
  {
    sourceType: "CommandStep",
    feature: "command",
    targetType: "Command",
    kind: "USES"
  },
  {
    sourceType: "QueryStep",
    feature: "query",
    targetType: "Query",
    kind: "USES"
  },
  {
    sourceType: "EventStep",
    feature: "event",
    targetType: "BusinessEvent",
    kind: "USES"
  },
  {
    sourceType: "PolicyStep",
    feature: "policy",
    targetType: "Policy",
    kind: "USES"
  },
  {
    sourceType: "ExternalInteractionStep",
    feature: "externalSystem",
    targetType: "ExternalSystem",
    kind: "USES"
  },
  {
    sourceType: "DecisionRule",
    feature: "resultingCommands",
    targetType: "Command",
    kind: "RESULTS_IN"
  },
  {
    sourceType: "DecisionRule",
    feature: "resultingEvents",
    targetType: "BusinessEvent",
    kind: "RESULTS_IN"
  },
  {
    sourceType: "DecisionStep",
    feature: "decisionTable",
    targetType: "DecisionTable",
    kind: "USES"
  },
  {
    sourceType: "Requirement",
    feature: "dependsOn",
    targetType: "Requirement",
    kind: "DEPENDS_ON"
  },
  {
    sourceType: "Requirement",
    feature: "conflictsWith",
    targetType: "Requirement",
    kind: "CONFLICTS_WITH"
  },
  {
    sourceType: "Requirement",
    feature: "constrains",
    targetType: "*",
    kind: "CONSTRAINS"
  },
  {
    sourceType: "NonFunctionalRequirement",
    feature: "constrainedElements",
    targetType: "*",
    kind: "CONSTRAINS"
  },
  {
    sourceType: "SecurityConstraint",
    feature: "constrainedCommands",
    targetType: "Command",
    kind: "CONSTRAINS"
  },
  {
    sourceType: "SecurityConstraint",
    feature: "constrainedQueries",
    targetType: "Query",
    kind: "CONSTRAINS"
  },
  {
    sourceType: "SecurityConstraint",
    feature: "constrainedInformation",
    targetType: "InformationItem",
    kind: "CONSTRAINS"
  },
  {
    sourceType: "PrivacyConstraint",
    feature: "dataItems",
    targetType: "InformationItem",
    kind: "CONSTRAINS"
  },
  {
    sourceType: "ComplianceConstraint",
    feature: "scopedElements",
    targetType: "*",
    kind: "CONSTRAINS"
  },
  {
    sourceType: "Risk",
    feature: "affectedElements",
    targetType: "*",
    kind: "ATTACHED_TO"
  },
  {
    sourceType: "Assumption",
    feature: "affectedElements",
    targetType: "*",
    kind: "ATTACHED_TO"
  },
  {
    sourceType: "Hotspot",
    feature: "attachedTo",
    targetType: "*",
    kind: "ATTACHED_TO"
  }
]);

function clone(value) {
  return value == null ? value : structuredClone(value);
}

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function sanitizeIdPart(value) {
  return String(value || "view").trim().toLowerCase()
  .replaceAll(/[^a-z0-9_-]+/g, "-").replaceAll(/^-+|-+$/g, "") || "view";
}

function normalizeViewName(value) {
  return String(value || "").trim().toLowerCase().replaceAll(/[^a-z0-9]+/g,
      " ");
}

function viewBelongsToLevel(view, typeKey) {
  const level = String(view?.level || "").trim().toUpperCase();
  return (LEVEL_ALIASES[typeKey] || new Set([""])).has(level);
}

function isFocusView(view) {
  return String(view?.kind || "").trim().toUpperCase() === "FOCUS"
      || String(view?.id || "").includes("-focus-");
}

function elementRecords(modelJson, typeKey = state.activeType) {
  const semanticElements = typeKey === "cim"
      ? cimSemanticElementsFromRoot(modelJson)
      : typeKey === "pim" ? pimSemanticElementsFromRoot(modelJson) : [];
  if (semanticElements.length) {
    const graphElements = Array.isArray(modelJson?.graph?.elements)
        ? modelJson.graph.elements : [];
    if (!graphElements.length) {
      return semanticElements;
    }
    const graphById = new Map(graphElements.map((element) => [
      String(element?.id || "").trim(),
      element
    ]).filter(([id]) => id));
    return semanticElements.map((element) => {
      const graphElement = graphById.get(String(element?.id || "").trim());
      return graphElement ? {
        ...clone(graphElement),
        ...clone(element),
        x: Number.isFinite(Number(graphElement?.x)) ? Number(graphElement.x)
            : element.x,
        y: Number.isFinite(Number(graphElement?.y)) ? Number(graphElement.y)
            : element.y
      } : element;
    });
  }
  if (Array.isArray(modelJson?.graph?.elements)) {
    return modelJson.graph.elements;
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
  const semanticRelationships = typeKey === "cim"
      ? cimSemanticRelationshipsFromRoot(modelJson)
      : typeKey === "pim" ? pimSemanticRelationshipsFromRoot(modelJson) : [];
  if (semanticRelationships.length) {
    const graphRelationships = Array.isArray(modelJson?.graph?.relationships)
        ? modelJson.graph.relationships : [];
    if (!graphRelationships.length) {
      return semanticRelationships;
    }
    const graphById = new Map(graphRelationships.map((relationship) => [
      String(relationship?.id || "").trim(),
      relationship
    ]).filter(([id]) => id));
    return semanticRelationships.map((relationship) => {
      const graphRelationship = graphById.get(
          String(relationship?.id || "").trim());
      return graphRelationship ? {
        ...clone(graphRelationship),
        ...clone(relationship),
        source: relationship.source || graphRelationship.source,
        target: relationship.target || graphRelationship.target,
        sourceElementId: relationship.sourceElementId
            || graphRelationship.sourceElementId || graphRelationship.source,
        targetElementId: relationship.targetElementId
            || graphRelationship.targetElementId || graphRelationship.target
      } : relationship;
    });
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
      relationship?.sourceElementId
      || relationship?.sourceId
      || refId(relationship?.source)
      || "");
}

function relationshipTargetId(relationship) {
  return String(
      relationship?.targetElementId
      || relationship?.targetId
      || refId(relationship?.target)
      || "");
}

function relationshipIdentity(typeKey, relationship, index) {
  const kind = String(relationship?.kind || "DEPENDS_ON");
  const source = relationshipSourceId(relationship);
  const target = relationshipTargetId(relationship);
  const explicit = String(relationship?.id || "").trim();
  if (explicit) {
    return explicit;
  }
  return `rel-${typeKey}-${index}-${sanitizeIdPart(kind)}-${sanitizeIdPart(
      source)}-${sanitizeIdPart(target)}`;
}

function normalizeElement(element, index) {
  const id = String(element?.id || genId("node")).trim();
  return {
    eClass: semanticType(element),
    id,
    name: semanticLabel(element),
    label: semanticLabel(element),
    status: "DRAFT",
    tags: [],
    ...clone(element),
    id
  };
}

function normalizeRelationship(typeKey, relationship, index) {
  const sourceElementId = relationshipSourceId(relationship);
  const targetElementId = relationshipTargetId(relationship);
  const kind = String(relationship?.kind || "DEPENDS_ON");
  const id = relationshipIdentity(typeKey, relationship, index);
  return {
    ...clone(relationship),
    id,
    kind,
    sourceElementId,
    targetElementId,
    source: sourceElementId,
    target: targetElementId
  };
}

function referenceIds(value) {
  if (Array.isArray(value)) {
    return value.map(refId).filter(Boolean);
  }
  const single = refId(value);
  return single ? [single] : [];
}

function matchesSemanticType(element, expectedType,
    typeKey = state.activeType) {
  if (!element) {
    return false;
  }
  if (typeKey === "cim") {
    return cimTypeMatches(element, expectedType);
  }
  if (typeKey === "pim") {
    return pimTypeMatches(element, expectedType);
  }
  return modelingTypeMatches(typeKey, expectedType, semanticType(element));
}

function synthesizeSemanticRefRelationships(graph, typeKey = state.activeType) {
  const additions = [];
  const edgeKeys = new Set();
  graph.relationshipsById.forEach((relationship) => {
    edgeKeys.add(
        `${relationship.sourceElementId}|${relationship.targetElementId}|${relationship.kind}`);
  });

  let rules = typeKey === "cim" ? CIM_REF_EDGE_RULES : [];
  try {
    const configured = modelingSemanticReferenceRules(typeKey);
    if (configured.length) {
      rules = configured;
    }
  } catch {
    rules = typeKey === "cim" ? CIM_REF_EDGE_RULES : [];
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
          id: `ref-${typeKey}-${sanitizeIdPart(kind)}-${sanitizeIdPart(
              sourceId)}-${sanitizeIdPart(destinationId)}`,
          kind,
          sourceElementId: sourceId,
          targetElementId: destinationId,
          source: sourceId,
          target: destinationId,
          semanticFeature: rule.feature,
          visualOnly: true
        });
      }
    }
  });

  additions.forEach((relationship) => {
    graph.relationshipsById.set(relationship.id, relationship);
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
    parentByChild: new Map()
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
  graph.relationshipsBySource = new Map();
  graph.relationshipsByTarget = new Map();
  graph.relationshipsByKind = new Map();
  graph.containmentByParent = new Map();
  graph.parentByChild = new Map();

  graph.relationshipsById.forEach((relationship) => {
    addToIndex(graph.relationshipsBySource, relationship.sourceElementId,
        relationship.id);
    addToIndex(graph.relationshipsByTarget, relationship.targetElementId,
        relationship.id);
    addToIndex(graph.relationshipsByKind, relationship.kind, relationship.id);
    if (CONTAINMENT_KINDS.has(String(relationship.kind || "").toUpperCase())) {
      addToIndex(graph.containmentByParent, relationship.sourceElementId,
          relationship.targetElementId);
      if (relationship.targetElementId && !graph.parentByChild.has(
          relationship.targetElementId)) {
        graph.parentByChild.set(relationship.targetElementId,
            relationship.sourceElementId);
      }
    }
  });

  graph.elementsById.forEach((element, elementId) => {
    const contextName = String(element?.contextName || element?.context || "")
    .trim();
    if (!contextName) {
      return;
    }
    const parent = [...graph.elementsById.values()].find((candidate) => {
      if (semanticType(candidate) !== "BoundedContextCandidate") {
        return false;
      }
      return semanticLabel(candidate).trim().toLowerCase()
          === contextName.toLowerCase();
    });
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
    if (!graph.elementsById.has(normalized.sourceElementId)
        || !graph.elementsById.has(normalized.targetElementId)) {
      return;
    }
    graph.relationshipsById.set(normalized.id, normalized);
  });

  if (typeKey === "cim" || typeKey === "pim") {
    synthesizeSemanticRefRelationships(graph, typeKey);
  }

  safeArray(modelJson?.graph?.traceLinks || modelJson?.traceLinks).forEach(
      (traceLink, index) => {
        const id = String(traceLink?.id || `trace-${index + 1}`);
        graph.traceLinksById.set(id, {...clone(traceLink), id});
      });
  safeArray(modelJson?.graph?.assumptions || modelJson?.assumptions).forEach(
      (assumption, index) => {
        const id = String(assumption?.id || `assumption-${index + 1}`);
        graph.assumptionsById.set(id, {...clone(assumption), id});
      });
  graph.validationIssues = safeArray(
      modelJson?.graph?.validationIssues || modelJson?.validationIssues).map(
      clone);
  graph.manualBacklog = safeArray(
      modelJson?.graph?.manualBacklog || modelJson?.manualBacklog).map(clone);

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

function configuredElementTypes(typeKey) {
  try {
    return new Set([
      ...safeArray(modelingLevelConfig(typeKey).elements).map(
          (entry) => String(entry?.type || "").trim()).filter(Boolean),
      ROOT_SCOPE_TYPE[typeKey]
    ].filter(Boolean));
  } catch {
    return new Set();
  }
}

function edgeIdsForElementIds(graph, elementIds, relationshipKinds = []) {
  const ids = new Set(elementIds);
  const allowedKinds = new Set(relationshipKinds || []);
  const result = [];
  graph.relationshipsById.forEach((relationship) => {
    if (!ids.has(relationship.sourceElementId) || !ids.has(
        relationship.targetElementId)) {
      return;
    }
    if (allowedKinds.size && !allowedKinds.has(relationship.kind)) {
      return;
    }
    result.push(relationship.id);
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
  const queue = [{id: rootId, depth: 0}];
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
    graph.containmentByParent.get(current.id)?.forEach(
        (childId) => connected.add(childId));
    connected.forEach((nextId) => {
      if (!graph.elementsById.has(nextId) || result.has(nextId)) {
        return;
      }
      result.add(nextId);
      queue.push({id: nextId, depth: current.depth + 1});
    });
  }
  return result;
}

function selectElementIdsForView(graph, view, typeKey) {
  const hidden = new Set(safeArray(view?.hidden?.elementIds));
  const filterTypes = new Set(safeArray(view?.filters?.elementTypes));
  let candidates;
  const hasExplicitNodes = safeArray(view?.nodes).length > 0;
  if (hasExplicitNodes) {
    candidates = new Set(safeArray(view.nodes).map((node) => node.elementId));
  } else if (view?.scope?.rootElementId) {
    candidates = neighborhoodElementIds(graph, view.scope.rootElementId,
        view?.scope?.depth ?? view?.defaultDepth ?? 1);
  } else {
    candidates = new Set(graph.elementsById.keys());
  }
  const selected = [];
  candidates.forEach((elementId) => {
    const element = graph.elementsById.get(elementId);
    if (!element || hidden.has(elementId)) {
      return;
    }
    const includeByType = !filterTypes.size || filterTypes.has(
        semanticType(element)) || elementId === view?.scope?.rootElementId;
    if (includeByType) {
      selected.push(elementId);
    }
  });
  if (!selected.length && typeKey && !filterTypes.size
      && !view?.scope?.rootElementId) {
    return [...graph.elementsById.keys()].filter((elementId) => !hidden.has(
        elementId));
  }
  return selected;
}

function selectRelationshipIdsForView(graph, view, elementIds) {
  const hidden = new Set(safeArray(view?.hidden?.relationshipIds));
  const filterKinds = new Set(safeArray(view?.filters?.relationshipKinds));
  const elementSet = new Set(elementIds);
  const explicitEdgeVisibility = new Map(
      safeArray(view?.edges).map((edge) => [edge.relationshipId, edge]));
  const relationshipIds = [];
  graph.relationshipsById.forEach((relationship, relationshipId) => {
    if (hidden.has(relationshipId)) {
      return;
    }
    const explicit = explicitEdgeVisibility.get(relationshipId);
    if (explicit && explicit.visible === false) {
      return;
    }
    if (!elementSet.has(relationship.sourceElementId) || !elementSet.has(
        relationship.targetElementId)) {
      return;
    }
    if (filterKinds.size && !filterKinds.has(relationship.kind)) {
      return;
    }
    relationshipIds.push(relationshipId);
  });
  return relationshipIds;
}

function layoutNodesForElements(graph, elementIds, existingNodes = []) {
  const existingByElement = new Map(
      safeArray(existingNodes).map((node) => [node.elementId, node]));
  const nodes = elementIds.map((elementId, index) => {
    const element = graph.elementsById.get(elementId);
    const existing = existingByElement.get(elementId);
    const x = Number.isFinite(Number(existing?.x)) ? Number(existing.x)
        : Number.isFinite(Number(element?.x)) ? Number(element.x) : 0;
    const y = Number.isFinite(Number(existing?.y)) ? Number(existing.y)
        : Number.isFinite(Number(element?.y)) ? Number(element.y) : 0;
    return {
      elementId,
      x,
      y,
      width: existing?.width,
      height: existing?.height,
      collapsed: Boolean(existing?.collapsed)
    };
  });
  const fakeNodes = nodes.map((node) => ({
    id: node.elementId,
    x: node.x,
    y: node.y
  }));
  autoLayoutIfStacked(fakeNodes);
  fakeNodes.forEach((node, index) => {
    nodes[index].x = node.x;
    nodes[index].y = node.y;
  });
  return nodes;
}

function buildViewFromDefinition(typeKey, graph, definition,
    scopeElement = null) {
  const idParts = [
    "view",
    typeKey,
    definition.id || definition.displayName || "definition",
    scopeElement?.id || "global"
  ];
  const id = idParts.map(sanitizeIdPart).join("-");
  const scope = scopeElement ? {
    rootElementId: scopeElement.id,
    scopeKind: FRAGMENT_KIND_BY_TYPE[semanticType(scopeElement)] || "ELEMENT",
    depth: definition.defaultDepth ?? 2
  } : {
    scopeKind: "MODEL",
    depth: definition.defaultDepth ?? 1
  };
  const view = {
    id,
    name: scopeElement
        ? `${semanticLabel(scopeElement)} - ${definition.displayName || id}`
        : definition.displayName || id,
    level: MODEL_LEVEL[typeKey],
    kind: String(definition.id || "VIEW").toUpperCase().replaceAll("-", "_"),
    scope,
    filters: {
      elementTypes: safeArray(definition.elementTypes),
      relationshipKinds: safeArray(definition.relationshipKinds)
    },
    definitionId: String(definition.id || ""),
    viewpoint: String(definition.viewpoint || ""),
    description: String(definition.description || ""),
    palette: safeArray(definition.palette),
    edgeLayers: safeArray(definition.edgeLayers),
    layoutProfile: definition.layoutProfile || "DEFAULT_LAYERED",
    defaultDepth: definition.defaultDepth ?? 1,
    nodes: [],
    edges: [],
    hidden: {elementIds: [], relationshipIds: []},
    collapsedElementIds: []
  };
  const elementIds = selectElementIdsForView(graph, view, typeKey);
  const relationshipIds = selectRelationshipIdsForView(graph, view, elementIds);
  view.nodes = layoutNodesForElements(graph, elementIds);
  view.edges = relationshipIds.map((relationshipId) => ({
    relationshipId,
    visible: true
  }));
  return view;
}

function defaultMainView(typeKey, graph, modelName) {
  const elementIds = [...graph.elementsById.keys()];
  const relationshipIds = edgeIdsForElementIds(graph, elementIds);
  return {
    id: `view-${typeKey}-main`,
    name: `${modelName || typeKey.toUpperCase()} Main View`,
    level: MODEL_LEVEL[typeKey],
    kind: "MAIN",
    scope: {scopeKind: "MODEL"},
    filters: {
      elementTypes: [],
      relationshipKinds: []
    },
    layoutProfile: "DEFAULT_LAYERED",
    nodes: layoutNodesForElements(graph, elementIds),
    edges: relationshipIds.map((relationshipId) => ({
      relationshipId,
      visible: true
    })),
    hidden: {elementIds: [], relationshipIds: []},
    collapsedElementIds: []
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

function normalizeView(view, graph, typeKey, modelName) {
  const normalized = {
    id: String(view?.id || genId("view")),
    name: String(view?.name || modelName || "View"),
    level: String(view?.level || MODEL_LEVEL[typeKey]),
    kind: String(view?.kind || "MAIN"),
    scope: clone(view?.scope || {scopeKind: "MODEL"}),
    filters: clone(view?.filters || {}),
    definitionId: String(view?.definitionId || view?.sourceDefinitionId || ""),
    viewpoint: String(view?.viewpoint || ""),
    description: String(view?.description || ""),
    palette: safeArray(view?.palette).map(String),
    edgeLayers: safeArray(view?.edgeLayers).map(String),
    layoutProfile: String(view?.layoutProfile || "DEFAULT_LAYERED"),
    defaultDepth: view?.defaultDepth,
    nodes: safeArray(view?.nodes).map((node) => ({
      elementId: String(node?.elementId || node?.id || ""),
      x: Number(node?.x) || 0,
      y: Number(node?.y) || 0,
      width: Number.isFinite(Number(node?.width)) ? Number(node.width)
          : undefined,
      height: Number.isFinite(Number(node?.height)) ? Number(node.height)
          : undefined,
      collapsed: Boolean(node?.collapsed)
    })).filter((node) => graph.elementsById.has(node.elementId)),
    edges: safeArray(view?.edges).map((edge) => ({
      ...clone(edge),
      relationshipId: String(edge?.relationshipId || edge?.id || "")
    })).filter((edge) => graph.relationshipsById.has(edge.relationshipId)),
    hidden: {
      elementIds: safeArray(view?.hidden?.elementIds).map(String),
      relationshipIds: safeArray(view?.hidden?.relationshipIds).map(String)
    },
    collapsedElementIds: safeArray(view?.collapsedElementIds).map(String)
  };
  if (!normalized.filters || typeof normalized.filters !== "object") {
    normalized.filters = {};
  }
  normalized.filters.elementTypes = safeArray(
      normalized.filters.elementTypes).map(String);
  normalized.filters.relationshipKinds = safeArray(
      normalized.filters.relationshipKinds).map(String);
  if (!normalized.nodes.length && graph.elementsById.size) {
    const elementIds = selectElementIdsForView(graph, normalized, typeKey);
    normalized.nodes = layoutNodesForElements(graph, elementIds);
  }
  if (!normalized.edges.length && graph.relationshipsById.size) {
    const elementIds = normalized.nodes.map((node) => node.elementId);
    normalized.edges = selectRelationshipIdsForView(graph, normalized,
        elementIds).map((relationshipId) => ({relationshipId, visible: true}));
  }
  return normalized;
}

function buildViews(typeKey, graph, modelJson, fallbackName) {
  const rawViews = safeArray(modelJson?.views);
  if (rawViews.length) {
    const generatedViews = generateViews(typeKey, graph,
        modelJson?.name || fallbackName);
    const normalizedViews = rawViews.map((view) => normalizeView(view, graph,
        typeKey, fallbackName)).filter((view) => viewBelongsToLevel(view,
        typeKey) && !isFocusView(view));
    const existingKeys = new Set();
    normalizedViews.forEach((view) => {
      existingKeys.add(view.id);
      existingKeys.add(normalizeViewName(view.name));
    });
    generatedViews.filter((view) => !view.scope?.rootElementId).forEach(
        (view) => {
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
    if (ids.has(relationship.sourceElementId) || ids.has(
        relationship.targetElementId)) {
      result.push(relationship.id);
    }
  });
  return result;
}

function deriveFragments(typeKey, graph, views) {
  const containerTypes = CONTAINER_TYPES[typeKey] || new Set();
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
    if (!containerTypes.has(elementType)) {
      return;
    }
    const elementIds = [...recursivelyCollectChildren(graph, element.id)];
    const relationshipIds = relationshipIdsTouching(graph, elementIds);
    fragments.push({
      id: `fragment-${sanitizeIdPart(element.id)}`,
      name: semanticLabel(element),
      level: MODEL_LEVEL[typeKey],
      fragmentKind: FRAGMENT_KIND_BY_TYPE[elementType] || "ELEMENT",
      ownerElementId: element.id,
      elementIds,
      relationshipIds,
      defaultViewId: viewsByScope.get(element.id) || views[0]?.id || null,
      parentFragmentId: graph.parentByChild.has(element.id)
          ? `fragment-${sanitizeIdPart(graph.parentByChild.get(element.id))}`
          : null
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
      elementIds: safeArray(fragment?.elementIds || fragment?.elements).map(
          String),
      relationshipIds: safeArray(
          fragment?.relationshipIds || fragment?.relationships).map(String)
    }));
  }
  return deriveFragments(typeKey, graph, views);
}

function installGraph(graph) {
  state.graph = graph;
  rebuildGraphIndexes(state.graph);
}

function installViews(views, activeViewId, typeKey = state.activeType,
    modelName = "") {
  const byId = new Map();
  safeArray(views).filter((view) => viewBelongsToLevel(view, typeKey)
      && !isFocusView(view)).forEach((view) => byId.set(view.id, view));
  if (!byId.size) {
    generateViews(typeKey, state.graph, modelName).filter(
        (view) => !view.scope?.rootElementId).forEach(
        (view) => byId.set(view.id, view));
  }
  const resolvedActive = byId.has(activeViewId) ? activeViewId
      : byId.has(`view-${typeKey}-main`) ? `view-${typeKey}-main`
          : byId.keys().next().value || null;
  state.views = {
    byId,
    activeViewId: resolvedActive,
    visibleNodeIds: new Set(),
    visibleRelationshipIds: new Set(),
    expandedContainers: new Set(),
    collapsedContainers: new Set()
  };
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
    rootIds: [...byId.keys()].filter((id) => !childIds.has(id))
  };
}

export function installGraphAndViews(typeKey, modelJson = {},
    fallbackName = "") {
  const graph = buildGraph(typeKey, modelJson || {});
  const views = buildViews(typeKey, graph, modelJson || {}, fallbackName);
  const fragments = buildFragments(typeKey, graph, views, modelJson || {});
  installGraph(graph);
  installViews(views, modelJson?.activeViewId || views[0]?.id || null, typeKey,
      fallbackName);
  installFragments(fragments);
  return {
    graph: state.graph,
    views: state.views,
    fragments: state.fragments
  };
}

export function ensureActiveGraphAndViews(typeKey = state.activeType) {
  if (!["cim", "pim", "psm"].includes(typeKey)) {
    return false;
  }
  const currentView = state.views?.byId?.get(state.views.activeViewId);
  const hasActiveView = state.views?.activeViewId
      && currentView && viewBelongsToLevel(currentView, typeKey);
  if (state.graph?.elementsById instanceof Map && hasActiveView) {
    return false;
  }
  const tab = state.tabs[typeKey] || {};
  const modelJson = state.baseModel || tab.baseModel || {
    name: tab.modelName || `${typeKey}-model`,
    diagram: {elements: [], relationships: []}
  };
  installGraphAndViews(typeKey, modelJson, tab.modelName || modelJson.name
      || `${typeKey}-model`);
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
  installViews(tab.views || [], tab.activeViewId, typeKey,
      tab.modelName || "");
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
    if (!graph.elementsById.has(normalized.sourceElementId)
        || !graph.elementsById.has(normalized.targetElementId)) {
      return;
    }
    graph.relationshipsById.set(normalized.id, normalized);
  });
  safeArray(snapshot?.traceLinks).forEach((traceLink, index) => {
    const id = String(traceLink?.id || `trace-${index + 1}`);
    graph.traceLinksById.set(id, {...clone(traceLink), id});
  });
  safeArray(snapshot?.assumptions).forEach((assumption, index) => {
    const id = String(assumption?.id || `assumption-${index + 1}`);
    graph.assumptionsById.set(id, {...clone(assumption), id});
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
    manualBacklog: safeArray(state.graph.manualBacklog).map(clone)
  };
}

export function serializeRuntimeViews() {
  return [...state.views.byId.values()].map(clone);
}

export function serializeRuntimeFragments() {
  return [...state.fragments.byId.values()].map(clone);
}

export function syncActiveViewFromVisibleGraph() {
  const view = activeView();
  if (!view || !state.diagram) {
    return;
  }
  const viewNodesById = new Map(safeArray(view.nodes).map((node) => [
    node.elementId,
    node
  ]));
  safeArray(state.diagram.nodes).forEach((node) => {
    const element = state.graph.elementsById.get(node.id) || normalizeElement({
      ...node.meta,
      id: node.id,
      eClass: node.type,
      name: node.label,
      label: node.label
    });
    element.eClass = node.type || element.eClass;
    element.name = node.label || element.name;
    element.label = node.label || element.label;
    const meta = clone(node.meta || {});
    delete meta.__collapsed;
    delete meta.__collapsedSummary;
    Object.assign(element, meta, {
      id: node.id,
      eClass: node.type || element.eClass,
      name: node.label || element.name,
      label: node.label || element.label,
      x: node.x,
      y: node.y
    });
    state.graph.elementsById.set(node.id, element);
    const viewNode = viewNodesById.get(node.id) || {elementId: node.id};
    viewNode.x = node.x;
    viewNode.y = node.y;
    viewNode.collapsed = Boolean(node.meta?.__collapsed);
    viewNodesById.set(node.id, viewNode);
  });
  view.nodes = [...viewNodesById.values()].filter(
      (node) => state.graph.elementsById.has(
          node.elementId));

  const viewEdgesById = new Map(safeArray(view.edges).map((edge) => [
    edge.relationshipId,
    edge
  ]));
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
      targetElementId: edge.targetId
    };
    relationship.kind = edge.kind;
    relationship.source = edge.sourceId;
    relationship.target = edge.targetId;
    relationship.sourceElementId = edge.sourceId;
    relationship.targetElementId = edge.targetId;
    state.graph.relationshipsById.set(edge.id, relationship);

    const viewEdge = viewEdgesById.get(edge.id) || {
      relationshipId: edge.id,
      visible: true
    };
    viewEdge.visible = true;
    viewEdge.pinPoints = safeArray(edge.pinPoints).map(clone);
    viewEdge.sourceAnchor = edge.sourceAnchor ? clone(edge.sourceAnchor)
        : undefined;
    viewEdge.targetAnchor = edge.targetAnchor ? clone(edge.targetAnchor)
        : undefined;
    viewEdgesById.set(edge.id, viewEdge);
  });
  view.edges = [...viewEdgesById.values()].filter(
      (edge) => state.graph.relationshipsById.has(edge.relationshipId));
  view.collapsedElementIds = safeArray(view.collapsedElementIds);
  rebuildGraphIndexes(state.graph);
}

export function serializeGraphAndViewsInto(root) {
  syncActiveViewFromVisibleGraph();
  const graph = serializeRuntimeGraph();
  root.graph = graph;
  root.fragments = serializeRuntimeFragments();
  root.views = serializeRuntimeViews();
  root.activeViewId = state.views.activeViewId;
  root.traceLinks = graph.traceLinks;
  root.assumptions = graph.assumptions;
  root.validationIssues = graph.validationIssues;
  root.manualBacklog = graph.manualBacklog;
  root.diagram ??= {};
  root.diagram.elements = graph.elements.map((element) => {
    const copyElement = clone(element);
    delete copyElement.__collapsed;
    delete copyElement.__collapsedSummary;
    return copyElement;
  });
  root.diagram.relationships = graph.relationships.map((relationship) => ({
    id: relationship.id,
    kind: relationship.kind,
    source: relationship.sourceElementId || relationship.source,
    target: relationship.targetElementId || relationship.target,
    note: relationship.note || relationship.description || ""
  }));
  if (state.activeType === "cim") {
    populateCimRootContainments(root, state.graph);
  } else if (state.activeType === "pim") {
    populatePimRootContainments(root, state.graph);
  }
  return root;
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
    y: node.y
  });
  state.graph.elementsById.set(node.id, element);
  const view = activeView();
  if (view && !safeArray(view.nodes).some((entry) => entry.elementId
      === node.id)) {
    view.nodes.push({elementId: node.id, x: node.x, y: node.y});
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
    if (relationship.sourceElementId === id || relationship.targetElementId
        === id) {
      relationshipIds.push(relationshipId);
    }
  });
  relationshipIds.forEach(
      (relationshipId) => state.graph.relationshipsById.delete(
          relationshipId));
  state.views.byId.forEach((view) => {
    view.nodes = safeArray(view.nodes).filter((node) => node.elementId !== id);
    view.edges = safeArray(view.edges).filter(
        (edge) => !relationshipIds.includes(edge.relationshipId));
    view.hidden = view.hidden || {elementIds: [], relationshipIds: []};
    view.hidden.elementIds = safeArray(view.hidden.elementIds).filter(
        (item) => item !== id);
    view.hidden.relationshipIds = safeArray(
        view.hidden.relationshipIds).filter(
        (item) => !relationshipIds.includes(item));
    view.collapsedElementIds = safeArray(view.collapsedElementIds).filter(
        (item) => item !== id);
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
    return (modelingElementDefinition(state.activeType, type)?.references
        || []).find((reference) => reference.name === featureName) || null;
  } catch {
    return null;
  }
}

function setOppositeReference(sourceElement, targetElement, reference) {
  const opposite = normalizeOppositeName(reference?.opposite);
  if (!opposite || reference?.readonly || !sourceElement?.id
      || !targetElement?.id) {
    return;
  }
  const oppositeDefinition = referenceDefinition(semanticType(targetElement),
      opposite);
  const many = oppositeDefinition?.many !== false;
  addReferenceValue(targetElement, opposite, sourceElement.id, many);
}

function removeOppositeReference(sourceElement, targetElement, reference) {
  const opposite = normalizeOppositeName(reference?.opposite);
  if (!opposite || reference?.readonly || !sourceElement?.id
      || !targetElement?.id) {
    return;
  }
  const oppositeDefinition = referenceDefinition(semanticType(targetElement),
      opposite);
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
    // fall through
  }
  return typeKey === "cim" ? CIM_REF_EDGE_RULES : [];
}

function ruleMatchesForward(rule, sourceElement, targetElement, kind,
    typeKey = state.activeType) {
  return String(rule?.kind || "").toUpperCase() === String(kind || "")
      .toUpperCase()
      && matchesSemanticType(sourceElement, rule?.sourceType, typeKey)
      && matchesSemanticType(targetElement, rule?.targetType, typeKey);
}

function ruleMatchesReverse(rule, sourceElement, targetElement, kind,
    typeKey = state.activeType) {
  return Boolean(rule?.reverse)
      && String(rule?.kind || "").toUpperCase() === String(kind || "")
      .toUpperCase()
      && matchesSemanticType(targetElement, rule?.sourceType, typeKey)
      && matchesSemanticType(sourceElement, rule?.targetType, typeKey);
}

function semanticRuleSpecificity(rule) {
  return (rule?.sourceType && rule.sourceType !== "*" ? 1 : 0)
      + (rule?.targetType && rule.targetType !== "*" ? 1 : 0);
}

function findSemanticReferenceRule(sourceElement, targetElement, kind,
    typeKey = state.activeType) {
  const matches = [];
  configuredSemanticReferenceRules(typeKey).forEach((rule) => {
    if (ruleMatchesForward(rule, sourceElement, targetElement, kind, typeKey)) {
      matches.push({rule, reversedVisual: false});
    } else if (ruleMatchesReverse(rule, sourceElement, targetElement, kind,
        typeKey)) {
      matches.push({rule, reversedVisual: true});
    }
  });
  matches.sort((a, b) => semanticRuleSpecificity(b.rule)
      - semanticRuleSpecificity(a.rule));
  return matches[0] || null;
}

function removePreviousSemanticReference(edge, previous) {
  if (!previous?.sourceElementId || !previous?.targetElementId
      || !previous?.feature) {
    return;
  }
  const sourceElement = state.graph.elementsById.get(previous.sourceElementId);
  const targetElement = state.graph.elementsById.get(previous.targetElementId);
  if (!sourceElement || !targetElement) {
    return;
  }
  const reference = referenceDefinition(semanticType(sourceElement),
      previous.feature);
  const many = reference?.many !== false;
  removeReferenceValue(sourceElement, previous.feature, targetElement.id, many);
  removeOppositeReference(sourceElement, targetElement, reference);
}

function applySemanticReference(edge, previous = null) {
  if (!["cim", "pim"].includes(state.activeType) || !edge?.sourceId
      || !edge?.targetId) {
    return null;
  }
  const visualSource = state.graph.elementsById.get(edge.sourceId);
  const visualTarget = state.graph.elementsById.get(edge.targetId);
  if (!visualSource || !visualTarget) {
    return null;
  }
  const matched = findSemanticReferenceRule(visualSource, visualTarget,
      edge.kind, state.activeType);
  if (!matched?.rule?.feature) {
    return null;
  }
  const semanticSource = matched.reversedVisual ? visualTarget : visualSource;
  const semanticTarget = matched.reversedVisual ? visualSource : visualTarget;
  const feature = matched.rule.feature;
  const previousSignature = previous
      || (edge.semanticFeature ? {
        sourceElementId: edge.semanticSourceElementId || edge.sourceId,
        targetElementId: edge.semanticTargetElementId || edge.targetId,
        feature: edge.semanticFeature
      } : null);
  if (previousSignature
      && (previousSignature.sourceElementId !== semanticSource.id
          || previousSignature.targetElementId !== semanticTarget.id
          || previousSignature.feature !== feature)) {
    removePreviousSemanticReference(edge, previousSignature);
  }
  const reference = referenceDefinition(semanticType(semanticSource), feature);
  const many = reference?.many !== false;
  addReferenceValue(semanticSource, feature, semanticTarget.id, many);
  setOppositeReference(semanticSource, semanticTarget, reference);

  edge.semanticFeature = feature;
  edge.semanticSourceElementId = semanticSource.id;
  edge.semanticTargetElementId = semanticTarget.id;
  edge.semanticDirection = matched.reversedVisual ? "reverse-visual"
      : "forward";
  edge.visualOnly = false;
  return {
    sourceElementId: semanticSource.id,
    targetElementId: semanticTarget.id,
    feature
  };
}

function materializeCimSemanticEdgeObject(edge, relationship) {
  if (state.activeType !== "cim" || !edge?.sourceId || !edge?.targetId) {
    return relationship;
  }
  const source = state.graph.elementsById.get(edge.sourceId);
  const target = state.graph.elementsById.get(edge.targetId);
  const spec = semanticEdgeObjectSpec(edge.kind, cimTypeOf(source),
      cimTypeOf(target));
  if (!spec) {
    return relationship;
  }
  const existing = state.graph.relationshipsById.get(edge.id) || {};
  return {
    ...spec.defaults,
    ...existing,
    ...relationship,
    eClass: spec.eClass,
    source: edge.sourceId,
    target: edge.targetId,
    sourceElementId: edge.sourceId,
    targetElementId: edge.targetId,
    name: existing.name || relationship.name || `${spec.eClass} ${edge.id}`,
    rootFeature: spec.rootFeature,
    relationshipType: spec.eClass === "DomainRelationship"
        ? (existing.relationshipType || relationship.relationshipType
            || spec.defaults.relationshipType || "ASSOCIATION")
        : existing.relationshipType
  };
}

function materializePimSemanticEdgeObject(edge, relationship) {
  if (state.activeType !== "pim" || !edge?.sourceId || !edge?.targetId) {
    return relationship;
  }
  const source = state.graph.elementsById.get(edge.sourceId);
  const target = state.graph.elementsById.get(edge.targetId);
  const spec = pimSemanticEdgeObjectSpec(edge.kind, pimTypeOf(source),
      pimTypeOf(target));
  if (!spec) {
    return relationship;
  }
  const existing = state.graph.relationshipsById.get(edge.id) || {};
  const materialized = {
    ...spec.defaults,
    ...existing,
    ...relationship,
    eClass: spec.eClass,
    source: edge.sourceId,
    target: edge.targetId,
    sourceElementId: edge.sourceId,
    targetElementId: edge.targetId,
    name: existing.name || relationship.name || `${spec.eClass} ${edge.id}`,
    rootFeature: spec.rootFeature,
    visualOnly: false
  };
  if (spec.eClass === "WorkflowTransition" && source?.__ownerId
      && source.__ownerId === target?.__ownerId) {
    materialized.__ownerId = source.__ownerId;
    materialized.__containmentFeature = "transitions";
  } else if (spec.eClass === "Permission") {
    materialized.__ownerId = source.id;
    materialized.__containmentFeature = "permissions";
  } else if (spec.eClass === "Subscription") {
    materialized.__ownerId = source.id;
    materialized.__containmentFeature = "subscriptions";
  }
  const semantic = pimRelationshipSemanticCopy(materialized, state.graph);
  Object.assign(materialized, semantic, {
    source: edge.sourceId,
    target: edge.targetId,
    sourceElementId: edge.sourceId,
    targetElementId: edge.targetId
  });
  materializePimSideEffects(materialized, source, target);
  return materialized;
}

function materializePimSideEffects(relationship, source, target) {
  if (!source?.id || !target?.id) {
    return;
  }
  const type = pimTypeOf(relationship);
  if (type === "DataAccess") {
    const mode = String(relationship.mode || "").toUpperCase();
    if (mode === "READ" || mode === "READ_WRITE") {
      addReferenceValue(source, "reads", target.id, true);
    }
    if (mode === "WRITE" || mode === "READ_WRITE" || mode === "APPEND"
        || mode === "DELETE") {
      addReferenceValue(source, "writes", target.id, true);
    }
  } else if (type === "Trigger") {
    addReferenceValue(target, "triggers", relationship.id, true);
  } else if (type === "Subscription") {
    addReferenceValue(source, "subscriptions", relationship.id, true);
  } else if (type === "Permission") {
    addReferenceValue(source, "permissions", relationship.id, true);
  }
}

function removePimMaterializedSideEffects(relationship) {
  if (state.activeType !== "pim" || !relationship) {
    return;
  }
  const source = state.graph.elementsById.get(relationship.sourceElementId);
  const target = state.graph.elementsById.get(relationship.targetElementId);
  const type = pimTypeOf(relationship);
  if (type === "DataAccess" && source && target) {
    removeReferenceValue(source, "reads", target.id, true);
    removeReferenceValue(source, "writes", target.id, true);
  } else if (type === "Trigger" && target) {
    removeReferenceValue(target, "triggers", relationship.id, true);
  } else if (type === "Subscription" && source) {
    removeReferenceValue(source, "subscriptions", relationship.id, true);
  } else if (type === "Permission" && source) {
    removeReferenceValue(source, "permissions", relationship.id, true);
  }
}

export function addConnectionToGraphAndActiveView(edge) {
  if (!edge?.id || edge.bundle) {
    return;
  }
  const existing = state.graph.relationshipsById.get(edge.id);
  const previousSemanticReference = existing?.semanticFeature ? {
    sourceElementId: existing.semanticSourceElementId
        || existing.sourceElementId,
    targetElementId: existing.semanticTargetElementId
        || existing.targetElementId,
    feature: existing.semanticFeature
  } : null;
  let relationship = normalizeRelationship(state.activeType, {
    ...existing,
    id: edge.id,
    kind: edge.kind,
    source: edge.sourceId,
    target: edge.targetId,
    sourceElementId: edge.sourceId,
    targetElementId: edge.targetId
  }, state.graph.relationshipsById.size);
  relationship.sourceType = semanticType(state.graph.elementsById.get(
      edge.sourceId)) || relationship.sourceType;
  relationship.targetType = semanticType(state.graph.elementsById.get(
      edge.targetId)) || relationship.targetType;
  relationship = materializeCimSemanticEdgeObject(edge, relationship);
  relationship = materializePimSemanticEdgeObject(edge, relationship);
  const semanticReference = applySemanticReference({
        ...relationship,
        sourceId: edge.sourceId,
        targetId: edge.targetId
      },
      previousSemanticReference);
  if (semanticReference) {
    Object.assign(relationship, {
      semanticFeature: semanticReference.feature,
      semanticSourceElementId: semanticReference.sourceElementId,
      semanticTargetElementId: semanticReference.targetElementId
    });
  } else if (previousSemanticReference) {
    removePreviousSemanticReference(edge, previousSemanticReference);
    delete relationship.semanticFeature;
    delete relationship.semanticSourceElementId;
    delete relationship.semanticTargetElementId;
  }
  state.graph.relationshipsById.set(edge.id, relationship);
  const view = activeView();
  if (view && !safeArray(view.edges).some((entry) => entry.relationshipId
      === edge.id)) {
    view.edges.push({relationshipId: edge.id, visible: true});
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
      sourceElementId: relationship.semanticSourceElementId
          || relationship.sourceElementId,
      targetElementId: relationship.semanticTargetElementId
          || relationship.targetElementId,
      feature: relationship.semanticFeature
    });
  }
  removePimMaterializedSideEffects(relationship);
  state.graph.relationshipsById.delete(id);
  state.views.byId.forEach((view) => {
    view.edges = safeArray(view.edges).filter((edge) => edge.relationshipId
        !== id);
    if (view.hidden) {
      view.hidden.relationshipIds = safeArray(
          view.hidden.relationshipIds).filter((item) => item !== id);
    }
  });
  rebuildGraphIndexes(state.graph);
}

export function persistEdgeLayoutInActiveView(edgeId, layout) {
  const view = activeView();
  if (!view || !edgeId) {
    return false;
  }
  let viewEdge = safeArray(view.edges).find(
      (edge) => edge.relationshipId === edgeId);
  if (!viewEdge) {
    viewEdge = {relationshipId: edgeId, visible: true};
    view.edges.push(viewEdge);
  }
  viewEdge.pinPoints = safeArray(layout?.pinPoints).map(clone);
  viewEdge.sourceAnchor = layout?.sourceAnchor ? clone(layout.sourceAnchor)
      : undefined;
  viewEdge.targetAnchor = layout?.targetAnchor ? clone(layout.targetAnchor)
      : undefined;
  if (Array.isArray(layout?.sections)) {
    viewEdge.sections = clone(layout.sections);
  }
  return true;
}

export function visibleGraphFallback(typeKey = state.activeType) {
  return emptyDiagram(typeKey);
}
