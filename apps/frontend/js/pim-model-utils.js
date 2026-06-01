import {
  modelingElementDefinition,
  modelingTypeMatches
} from './modeling-config-data.js';

export const PIM_ROOT_CONTAINMENTS = Object.freeze([
  {
    feature: "services",
    types: ["ServerlessService"],
    required: true,
    title: "Services"
  },
  {
    feature: "serviceMemberships",
    types: ["ServiceElementMembership"],
    title: "Service Memberships",
    relationshipOnly: true
  },
  {
    feature: "deploymentUnits",
    types: ["DeploymentUnit"],
    required: true,
    title: "Deployment Units"
  },
  {
    feature: "environments",
    types: ["Environment"],
    required: true,
    title: "Environments"
  },
  {
    feature: "implementationProfile",
    types: ["ImplementationProfile"],
    singleton: true,
    title: "Implementation Profile"
  },
  {
    feature: "platformCapabilities",
    types: ["PlatformCapability"],
    title: "Platform Capabilities"
  },
  {
    feature: "platformMappingAssessments",
    types: ["PlatformMappingAssessment"],
    title: "Platform Mapping Assessments"
  },
  {feature: "schemas", types: ["Schema"], title: "Schemas"},
  {feature: "businessRules", types: ["BusinessRule"], title: "Business Rules"},
  {
    feature: "decisionModels",
    types: ["DecisionModel"],
    title: "Decision Models"
  },
  {
    feature: "functions",
    types: ["Function"],
    required: true,
    title: "Functions"
  },
  {feature: "apis", types: ["Api"], title: "APIs"},
  {feature: "eventTypes", types: ["EventType"], title: "Event Types"},
  {
    feature: "channels",
    types: ["Queue", "Topic", "EventBus"],
    title: "Event Channels"
  },
  {feature: "schedules", types: ["Schedule"], title: "Schedules"},
  {
    feature: "triggers",
    types: ["Trigger"],
    title: "Triggers",
    relationshipOnly: true
  },
  {feature: "dataStores", types: ["DataStore"], title: "Data Stores"},
  {feature: "objectStores", types: ["ObjectStore"], title: "Object Stores"},
  {
    feature: "dataAccesses",
    types: ["DataAccess"],
    title: "Data Accesses",
    relationshipOnly: true
  },
  {feature: "workflows", types: ["Workflow"], title: "Workflows"},
  {
    feature: "humanTasks",
    types: ["HumanTask", "ApprovalTask"],
    title: "Human Tasks"
  },
  {
    feature: "externalEndpoints",
    types: ["ExternalEndpoint"],
    title: "External Endpoints"
  },
  {
    feature: "externalAdapters",
    types: ["ExternalAdapter"],
    title: "External Adapters"
  },
  {
    feature: "identityProviders",
    types: ["IdentityProvider"],
    title: "Identity Providers"
  },
  {feature: "principals", types: ["Principal"], title: "Principals"},
  {
    feature: "policies",
    types: [
      "DataProtectionPolicy", "CompliancePolicy", "ResiliencePolicy",
      "DataQualityPolicy",
      "TimeoutPolicy", "IdempotencyPolicy", "ConcurrencyPolicy",
      "RateLimitPolicy", "BatchPolicy", "OrderingPolicy", "CachePolicy",
      "BackupPolicy", "RetentionPolicy", "CostPolicy", "ObservabilityConfig",
      "CorsPolicy", "SecurityPolicy", "AuthPolicy", "AuthorizationPolicy"
    ],
    title: "Policies"
  },
  {
    feature: "flows",
    types: [
      "RequestResponseFlow", "EventFlow", "MessageFlow", "PubSubFlow",
      "OrchestrationFlow", "ExternalIntegrationFlow"
    ],
    title: "Flows",
    relationshipOnly: true
  },
  {
    feature: "configurations",
    types: ["ConfigurationSet"],
    title: "Configuration Sets"
  },
  {feature: "secrets", types: ["Secret"], title: "Secrets"},
  {
    feature: "traceModel",
    types: ["TraceModel"],
    singleton: true,
    title: "Trace Model"
  },
  {
    feature: "readiness",
    types: ["ProductionReadinessAssessment"],
    singleton: true,
    title: "Readiness"
  }
]);

export const PIM_ABSTRACT_TYPES = Object.freeze([
  "ModelElement",
  "TraceableElement",
  "SemanticRelationship",
  "TransformationAssumption",
  "ComputeElement",
  "StorageElement",
  "IntegrationElement",
  "EventChannel",
  "Flow",
  "ArchitecturePolicy",
  "DeployableElement",
  "InvocationSource",
  "InvocationTarget",
  "FunctionTarget",
  "WorkflowTarget",
  "SubscriptionTarget",
  "RoutingTarget",
  "FlowEndpoint",
  "ProtectedResource",
  "PolicyTarget",
  "DataAccessTarget",
  "ExternalCallTarget",
  "EnvironmentTarget",
  "CredentialRequirementLike",
  "RouteEndpoint",
  "EventCarrier"
]);

export const PIM_RELATIONSHIP_TYPES = Object.freeze([
  "ServiceElementMembership",
  "Trigger",
  "DataAccess",
  "RequestResponseFlow",
  "EventFlow",
  "MessageFlow",
  "PubSubFlow",
  "OrchestrationFlow",
  "ExternalIntegrationFlow",
  "WorkflowTransition",
  "Permission",
  "Subscription",
  "EventRoutingRule",
  "PlatformMappingAssessment",
  "TraceLink"
]);

export const PIM_NESTED_CONTAINMENTS = Object.freeze({
  ModelElement: [{feature: "annotations", types: ["Annotation"]}],
  Function: [{
    feature: "contract",
    types: ["FunctionContract"],
    singleton: true
  }],
  Schema: [
    {feature: "fields", types: ["SchemaField"]},
    {feature: "constraints", types: ["SchemaConstraint"]}
  ],
  SchemaField: [
    {feature: "enumValues", types: ["SchemaEnumLiteral"]},
    {feature: "constraints", types: ["SchemaValidationConstraint"]},
    {feature: "cardinality", types: ["Cardinality"], singleton: true},
    {feature: "arrayItem", types: ["SchemaField"], singleton: true},
    {feature: "mapValue", types: ["SchemaField"], singleton: true}
  ],
  EventType: [{feature: "envelope", types: ["EventEnvelope"], singleton: true}],
  Api: [
    {feature: "routes", types: ["ApiRoute"]},
    {feature: "contract", types: ["ApiContract"], singleton: true}
  ],
  ApiRoute: [{feature: "errorMappings", types: ["ErrorMapping"]}],
  DataStore: [
    {feature: "ownedDataModels", types: ["DataModel"]},
    {feature: "accessPatterns", types: ["AccessPattern"]},
    {feature: "indexCandidates", types: ["IndexCandidate"]},
    {feature: "changeStream", types: ["DataChangeStream"], singleton: true}
  ],
  DataModel: [{feature: "storageFields", types: ["DataField"]}],
  ObjectStore: [
    {feature: "notificationRules", types: ["ObjectNotificationRule"]}
  ],
  Topic: [{
    feature: "subscriptions",
    types: ["Subscription"],
    relationshipOnly: true
  }],
  EventBus: [{
    feature: "routingRules",
    types: ["EventRoutingRule"],
    relationshipOnly: true
  }],
  Workflow: [
    {feature: "states", types: ["WorkflowState"]},
    {
      feature: "transitions",
      types: ["WorkflowTransition"],
      relationshipOnly: true
    }
  ],
  WorkflowState: [
    {feature: "condition", types: ["Expression"], singleton: true},
    {feature: "humanTask", types: ["HumanTask"], singleton: true},
    {feature: "branches", types: ["ParallelBranch"]},
    {feature: "mapConfig", types: ["MapStateConfig"], singleton: true},
    {feature: "callbackConfig", types: ["CallbackTaskConfig"], singleton: true},
    {feature: "retry", types: ["RetryPolicy"], singleton: true},
    {feature: "catchHandlers", types: ["ErrorHandler"]},
    {feature: "compensation", types: ["CompensationPolicy"], singleton: true}
  ],
  ParallelBranch: [
    {feature: "states", types: ["WorkflowState"]},
    {
      feature: "transitions",
      types: ["WorkflowTransition"],
      relationshipOnly: true
    }
  ],
  BusinessRule: [{
    feature: "expression",
    types: ["Expression"],
    singleton: true
  }],
  DecisionModel: [{feature: "rules", types: ["DecisionRule"]}],
  DecisionRule: [
    {feature: "condition", types: ["Expression"], singleton: true},
    {feature: "outcome", types: ["Expression"], singleton: true}
  ],
  HumanTask: [{
    feature: "escalation",
    types: ["EscalationPolicy"],
    singleton: true
  }],
  Principal: [{
    feature: "permissions",
    types: ["Permission"],
    relationshipOnly: true
  }],
  ConfigurationSet: [
    {feature: "parameters", types: ["ConfigParameter"]},
    {feature: "environmentVariables", types: ["EnvironmentVariable"]}
  ],
  ExternalAdapter: [{feature: "credentials", types: ["CredentialRequirement"]}],
  ResiliencePolicy: [
    {feature: "retry", types: ["RetryPolicy"], singleton: true},
    {feature: "deadLetter", types: ["DeadLetterPolicy"], singleton: true},
    {feature: "timeout", types: ["TimeoutPolicy"], singleton: true}
  ],
  ObservabilityConfig: [
    {feature: "logging", types: ["LoggingPolicy"], singleton: true},
    {feature: "metrics", types: ["MetricPolicy"]},
    {feature: "tracing", types: ["TracingPolicy"], singleton: true},
    {feature: "alerts", types: ["AlertPolicy"]},
    {feature: "slos", types: ["Slo"]}
  ],
  MetricPolicy: [{feature: "dimensions", types: ["MetricDimension"]}],
  TraceModel: [{
    feature: "links",
    types: ["TraceLink"],
    relationshipOnly: true
  }],
  ProductionReadinessAssessment: [
    {feature: "findings", types: ["ReadinessFinding"]},
    {feature: "checks", types: ["ReadinessCheck"]},
    {feature: "manualDecisions", types: ["ManualDecision"]}
  ]
});

const PIM_REQUIRED_FEATURES = Object.freeze({
  ModelElement: ["id", "name"],
  PIMModel: ["architectureStyle", "services", "deploymentUnits", "environments",
    "functions"],
  ServiceElementMembership: ["service", "element", "ownershipKind"],
  PlatformCapability: ["platform", "providerService", "supportLevel"],
  PlatformMappingAssessment: ["source", "supportLevel"],
  BusinessRule: ["naturalLanguageRule"],
  DecisionModel: ["hitPolicy", "rules"],
  DecisionRule: ["condition", "outcome"],
  Function: ["functionKind", "contract"],
  Trigger: ["invocationMode", "source"],
  Api: ["apiStyle", "routes"],
  ApiRoute: ["method", "pathTemplate"],
  Schema: ["schemaKind"],
  SchemaField: ["fieldType"],
  SchemaEnumLiteral: ["literal"],
  EventType: ["semanticName", "schema"],
  DataStore: ["storeKind", "consistencyNeed", "ownedDataModels",
    "accessPatterns"],
  DataChangeStream: ["enabled"],
  ObjectNotificationRule: ["eventTypes", "targets"],
  DataModel: ["dataModelKind", "schema"],
  DataField: ["fieldType"],
  DataAccess: ["mode", "function", "store"],
  Schedule: ["scheduleExpression"],
  EventChannel: ["channelKind", "orderingRequirement", "deliverySemantics"],
  Subscription: ["channel", "target"],
  EventRoutingRule: ["targets"],
  Flow: ["source", "target"],
  RequestResponseFlow: ["apiRoute"],
  EventFlow: ["eventType", "channel"],
  MessageFlow: ["messageSchema", "queue"],
  PubSubFlow: ["topic"],
  OrchestrationFlow: ["workflow"],
  ExternalIntegrationFlow: ["adapter"],
  Workflow: ["workflowKind", "states", "startState", "endStates"],
  WorkflowState: ["stateKind"],
  WorkflowTransition: ["source", "target"],
  ParallelBranch: ["states", "startState", "endStates"],
  MapStateConfig: ["itemsPath"],
  CallbackTaskConfig: ["taskTokenPath"],
  HumanTask: ["taskDescription", "assignees"],
  ApprovalTask: ["taskDescription"],
  EscalationPolicy: ["escalationRule", "afterSeconds"],
  ExternalEndpoint: ["externalSystemName", "protocolFamily"],
  IdentityProvider: ["identityKind"],
  Principal: ["principalKind"],
  Permission: ["effect", "targetResource"],
  ConfigurationSet: ["scope"],
  EnvironmentVariable: ["variableName"],
  Secret: ["secretKind"],
  CredentialRequirement: ["secretKind"],
  ImplementationProfile: ["primaryLanguage", "packageManager"],
  TraceLink: ["linkType"],
  ReadinessFinding: ["severity"],
  ReadinessCheck: ["checkId", "severity"],
  ManualDecision: ["question"],
  StructuredDocument: ["format"],
  Expression: ["language", "body"],
  Annotation: ["key"]
});

function clone(value) {
  return value == null ? value : structuredClone(value);
}

export function pimTypeOf(value) {
  return typeof value === "string" ? value : String(
      value?.eClass || value?.type || "");
}

export function pimTypeMatches(value, expectedType) {
  const actual = pimTypeOf(value);
  return modelingTypeMatches("pim", expectedType, actual);
}

export function pimRootContainmentForType(type) {
  return PIM_ROOT_CONTAINMENTS.find((entry) =>
          (entry.types || []).some((candidate) => pimTypeMatches(type, candidate)))
      || null;
}

export function isPimRelationshipElementType(type) {
  return PIM_RELATIONSHIP_TYPES.includes(String(type || ""));
}

export function refId(value) {
  if (typeof value === "string") {
    return value;
  }
  if (value && typeof value === "object") {
    return value.$ref || value.id || value.elementId || value.sourceElementId
        || value.targetElementId || "";
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

export function elementLabel(element) {
  return String(element?.name || element?.label || element?.semanticName
      || element?.variableName || element?.literal || element?.id || "Element");
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

function definitionForType(type) {
  try {
    return modelingElementDefinition("pim", type);
  } catch {
    return null;
  }
}

function inheritedRequiredFeatures(type) {
  const definition = definitionForType(type);
  const metadataRequired = [
    ...(definition?.attributes || []),
    ...(definition?.references || [])
  ].filter((field) => field?.required && !field?.readonly).map(
      (field) => field.name);
  const supertypeRequired = (definition?.supertypes || []).flatMap(
      (supertype) => PIM_REQUIRED_FEATURES[supertype] || []);
  return [...new Set([
    ...supertypeRequired,
    ...(PIM_REQUIRED_FEATURES[type] || []),
    ...metadataRequired
  ])];
}

export function missingRequiredFeatures(element, extraRequired = []) {
  const type = pimTypeOf(element);
  const required = new Set(
      [...inheritedRequiredFeatures(type), ...extraRequired]);
  return [...required].filter((feature) => {
    if (derivedRequiredFeatureIsSatisfied(element, feature)) {
      return false;
    }
    return !isPresent(element?.[feature]);
  });
}

function derivedRequiredFeatureIsSatisfied(element, feature) {
  const type = pimTypeOf(element);
  if (type === "Annotation" && feature === "owner") {
    return Boolean(element?.__ownerId || element?.owner);
  }
  if (type === "TraceLink" && feature === "traceModel") {
    return Boolean(element?.__ownerId || element?.traceModel);
  }
  if ((type === "ReadinessFinding" || type === "ReadinessCheck"
      || type === "ManualDecision") && feature === "assessment") {
    return Boolean(element?.__ownerId || element?.assessment);
  }
  const ownerFeatures = new Set([
    "ownerSchema", "field", "api", "apiRoute", "dataStore", "dataModel",
    "ownerTopic", "eventBus", "workflow", "state", "principal",
    "configurationSet", "observability", "metric"
  ]);
  return ownerFeatures.has(feature) && Boolean(element?.__ownerId);
}

function readonlyReferenceNames(type) {
  const definition = definitionForType(type);
  return new Set((definition?.references || []).filter(
      (reference) => reference?.readonly).map((reference) => reference.name));
}

function stripRuntimeFields(element) {
  const copy = clone(element) || {};
  const type = pimTypeOf(copy);
  [
    "x", "y", "label", "status", "tags", "visualOnly", "bundle", "countsByKind",
    "underlyingRelationshipIds", "sourceType", "targetType",
    "semanticFeature", "semanticSourceElementId", "semanticTargetElementId",
    "semanticDirection", "rootFeature", "kind"
  ].forEach((key) => delete copy[key]);
  readonlyReferenceNames(type).forEach((key) => delete copy[key]);
  delete copy.__ownerId;
  delete copy.__containmentFeature;
  return copy;
}

export function nestedContainmentsForType(elementType) {
  const entries = [];
  Object.entries(PIM_NESTED_CONTAINMENTS).forEach(
      ([ownerType, containments]) => {
        if (ownerType === "ModelElement"
            ? pimTypeMatches(elementType, "ModelElement")
            : pimTypeMatches(elementType, ownerType)) {
          entries.push(...containments);
        }
      });
  const byFeature = new Map();
  entries.forEach((entry) => {
    const current = byFeature.get(entry.feature) || {
      ...entry,
      types: []
    };
    current.types = [...new Set([...current.types, ...(entry.types || [])])]
    .filter((type) => type && !PIM_ABSTRACT_TYPES.includes(type));
    current.relationshipOnly = Boolean(
        current.relationshipOnly || entry.relationshipOnly);
    current.singleton = Boolean(current.singleton || entry.singleton);
    byFeature.set(entry.feature, current);
  });
  return [...byFeature.values()].filter((entry) => entry.types.length);
}

function nestedContainmentCopies(parentId, parentType, graph, feature) {
  const parent = graph.elementsById.get(parentId);
  const ids = new Set(refIds(parent?.[feature]));
  const containment = nestedContainmentsForType(parentType).find(
      (entry) => entry.feature === feature);
  const allowedTypes = new Set(containment?.types || []);
  const children = [];
  graph.elementsById.forEach((candidate) => {
    const candidateType = pimTypeOf(candidate);
    if (allowedTypes.size && ![...allowedTypes].some((type) =>
        pimTypeMatches(candidateType, type))) {
      return;
    }
    if ((candidate.__ownerId === parentId
            && candidate.__containmentFeature === feature)
        || ids.has(candidate.id)) {
      children.push(candidate);
    }
  });
  graph.relationshipsById?.forEach((candidate) => {
    const candidateType = pimTypeOf(candidate);
    if (allowedTypes.size && ![...allowedTypes].some((type) =>
        pimTypeMatches(candidateType, type))) {
      return;
    }
    if ((candidate.__ownerId === parentId
            && candidate.__containmentFeature === feature)
        || ids.has(candidate.id)
        || relationshipBelongsToParentContainment(candidate, parent, parentId,
            feature, graph)) {
      children.push(candidate);
    }
  });
  const copies = children.map((child) => {
    const type = pimTypeOf(child);
    const copy = isPimRelationshipElementType(type)
        ? pimRelationshipSemanticCopy(child, graph)
        : stripRuntimeFields(child);
    attachNestedContainments(copy, child.id, type, graph);
    return copy;
  }).filter(Boolean);
  return containment?.singleton ? (copies[0] || null) : copies;
}

function relationshipBelongsToParentContainment(relationship, parent, parentId,
    feature, graph) {
  const type = pimTypeOf(relationship);
  if (feature === "transitions" && type === "WorkflowTransition") {
    const source = graph.elementsById.get(refId(relationship.source)
        || relationship.sourceElementId);
    const target = graph.elementsById.get(refId(relationship.target)
        || relationship.targetElementId);
    return source?.__ownerId === parentId && target?.__ownerId === parentId;
  }
  if (feature === "permissions" && type === "Permission") {
    return relationship.sourceElementId === parentId;
  }
  if (feature === "subscriptions" && type === "Subscription") {
    return relationship.sourceElementId === parentId;
  }
  if (feature === "links" && type === "TraceLink") {
    return true;
  }
  return false;
}

function attachNestedContainments(copy, elementId, elementType, graph) {
  if (!copy) {
    return;
  }
  nestedContainmentsForType(elementType).forEach((entry) => {
    const value = nestedContainmentCopies(elementId, elementType, graph,
        entry.feature);
    if (entry.singleton) {
      if (value) {
        copy[entry.feature] = value;
      }
    } else {
      copy[entry.feature] = Array.isArray(value) ? value : [];
    }
  });
}

export function populatePimRootContainments(root, graph) {
  if (!root || !graph?.elementsById) {
    return root;
  }
  root.eClass ||= "PIMModel";
  root.modelLevel ||= "PIM";
  root.architectureStyle ||= "EVENT_DRIVEN_SERVERLESS";
  root.providerIndependent = root.providerIndependent !== false;
  PIM_ROOT_CONTAINMENTS.forEach((entry) => {
    root[entry.feature] = entry.singleton ? null : [];
  });

  graph.elementsById.forEach((element) => {
    if (element.__ownerId) {
      return;
    }
    const type = pimTypeOf(element);
    const containment = pimRootContainmentForType(type);
    if (!containment || containment.relationshipOnly) {
      return;
    }
    const copy = stripRuntimeFields(element);
    attachNestedContainments(copy, element.id, type, graph);
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
    const type = pimTypeOf(relationship);
    const containment = pimRootContainmentForType(type);
    if (!containment || !containment.relationshipOnly) {
      return;
    }
    const copy = pimRelationshipSemanticCopy(relationship, graph);
    if (!copy) {
      return;
    }
    if (containment.singleton) {
      root[containment.feature] = copy;
    } else {
      root[containment.feature].push(copy);
    }
  });

  const traceLinks = [];
  graph.relationshipsById?.forEach((relationship) => {
    if (relationship.visualOnly || pimTypeOf(relationship) !== "TraceLink") {
      return;
    }
    const copy = pimRelationshipSemanticCopy(relationship, graph);
    if (copy) {
      traceLinks.push(copy);
    }
  });
  if (traceLinks.length) {
    root.traceModel ??= {
      eClass: "TraceModel",
      id: "trace-model",
      name: "Trace Model",
      links: []
    };
    const existingIds = new Set((Array.isArray(root.traceModel.links)
        ? root.traceModel.links : []).map((link) => link?.id).filter(Boolean));
    root.traceModel.links = [
      ...(Array.isArray(root.traceModel.links) ? root.traceModel.links : []),
      ...traceLinks.filter((link) => !existingIds.has(link.id))
    ];
  }
  return root;
}

function generatedSemanticId(type, index, ownerId = "") {
  const owner = ownerId ? `${String(ownerId).replaceAll(/[^a-z0-9_-]+/gi,
      "-")}-` : "";
  return `${owner}${String(type || "element").toLowerCase()}-${index + 1}`;
}

function normalizeSemanticElement(raw, fallbackType, index, owner = null) {
  if (!raw || typeof raw !== "object") {
    return null;
  }
  const type = raw.eClass || raw.type || fallbackType;
  if (isPimRelationshipElementType(type)) {
    return null;
  }
  const id = String(raw.id || generatedSemanticId(type, index, owner?.id));
  return {
    eClass: type,
    id,
    name: String(raw.name || raw.label || raw.semanticName || raw.variableName
        || raw.literal || id),
    label: String(raw.name || raw.label || raw.semanticName || raw.variableName
        || raw.literal || id),
    ...clone(raw),
    id,
    eClass: type,
    __ownerId: owner?.id || raw.__ownerId,
    __containmentFeature: owner?.feature || raw.__containmentFeature
  };
}

function collectNestedSemanticElements(parent, result) {
  const type = pimTypeOf(parent);
  nestedContainmentsForType(type).forEach((entry) => {
    if (entry.relationshipOnly) {
      return;
    }
    const rawValues = entry.singleton
        ? (parent?.[entry.feature] ? [parent[entry.feature]] : [])
        : (Array.isArray(parent?.[entry.feature]) ? parent[entry.feature] : []);
    rawValues.forEach((raw, index) => {
      const child = normalizeSemanticElement(raw, raw?.eClass || raw?.type
          || entry.types[0], index, {id: parent.id, feature: entry.feature});
      if (!child) {
        return;
      }
      result.push(child);
      collectNestedSemanticElements(child, result);
    });
  });
}

export function pimSemanticElementsFromRoot(modelJson) {
  const result = [];
  if (!modelJson || typeof modelJson !== "object") {
    return result;
  }
  PIM_ROOT_CONTAINMENTS.forEach((entry) => {
    if (entry.relationshipOnly) {
      return;
    }
    const rawValues = entry.singleton
        ? (modelJson[entry.feature] ? [modelJson[entry.feature]] : [])
        : (Array.isArray(modelJson[entry.feature]) ? modelJson[entry.feature]
            : []);
    rawValues.forEach((raw, index) => {
      const element = normalizeSemanticElement(raw, raw?.eClass || raw?.type
          || entry.types[0], index);
      if (!element) {
        return;
      }
      result.push(element);
      collectNestedSemanticElements(element, result);
    });
  });
  return result;
}

function relationshipFromObject(raw, fallbackType, kind, sourceId, targetId,
    index,
    owner = null) {
  if (!sourceId || !targetId) {
    return null;
  }
  const type = raw?.eClass || raw?.type || fallbackType;
  const id = String(raw?.id || `rel-pim-${String(
      kind).toLowerCase()}-${sourceId}-${targetId}-${index + 1}`);
  return {
    ...clone(raw),
    id,
    eClass: type,
    kind,
    sourceElementId: sourceId,
    targetElementId: targetId,
    source: sourceId,
    target: targetId,
    __ownerId: owner?.id || raw?.__ownerId,
    __containmentFeature: owner?.feature || raw?.__containmentFeature
  };
}

function flowKind(type) {
  return ({
    RequestResponseFlow: "REQUEST_RESPONSE",
    EventFlow: "EVENT_FLOW",
    MessageFlow: "MESSAGE_FLOW",
    PubSubFlow: "PUB_SUB",
    OrchestrationFlow: "ORCHESTRATES",
    ExternalIntegrationFlow: "EXTERNAL_CALL"
  })[type] || "FLOW";
}

function dataAccessKind(mode) {
  return ({
    READ: "READS",
    WRITE: "WRITES",
    READ_WRITE: "READ_WRITE",
    APPEND: "APPEND",
    DELETE: "DELETE"
  })[String(mode || "").toUpperCase()] || "DATA_ACCESS";
}

function addRootRelationshipObjects(modelJson, result) {
  (Array.isArray(modelJson?.triggers) ? modelJson.triggers : []).forEach(
      (raw, index) => {
        result.push(
            relationshipFromObject(raw, "Trigger", "INVOKES", refId(raw.source),
                refId(raw.invokesFunction) || refId(raw.startsWorkflow),
                index));
      });
  (Array.isArray(modelJson?.dataAccesses) ? modelJson.dataAccesses
      : []).forEach((raw, index) => {
    result.push(
        relationshipFromObject(raw, "DataAccess", dataAccessKind(raw.mode),
            refId(raw.function), refId(raw.store), index));
  });
  (Array.isArray(modelJson?.flows) ? modelJson.flows : []).forEach(
      (raw, index) => {
        const type = raw?.eClass || raw?.type || "Flow";
        result.push(relationshipFromObject(raw, type, flowKind(type),
            refId(raw.source), refId(raw.target), index));
      });
  const traceLinks = Array.isArray(modelJson?.traceModel?.links)
      ? modelJson.traceModel.links : [];
  traceLinks.forEach((raw, index) => {
    result.push(relationshipFromObject(raw, "TraceLink", "TRACE",
        refId(raw.source) || String(raw.sourceElementId || ""),
        refId(raw.target) || String(raw.targetElementId || ""), index,
        {id: modelJson.traceModel?.id, feature: "links"}));
  });
}

function addNestedRelationshipObjects(parent, result) {
  const type = pimTypeOf(parent);
  if (type === "Topic") {
    (Array.isArray(parent.subscriptions) ? parent.subscriptions : []).forEach(
        (raw, index) => {
          result.push(
              relationshipFromObject(raw, "Subscription", "SUBSCRIBES_TO",
                  parent.id, refId(raw.target), index,
                  {id: parent.id, feature: "subscriptions"}));
        });
  }
  if (type === "EventBus") {
    (Array.isArray(parent.routingRules) ? parent.routingRules : []).forEach(
        (raw, index) => {
          refIds(raw.targets).forEach((targetId, targetIndex) => {
            result.push(
                relationshipFromObject(raw, "EventRoutingRule", "ROUTES_TO",
                    parent.id, targetId, index + targetIndex,
                    {id: parent.id, feature: "routingRules"}));
          });
        });
  }
  if (type === "Workflow") {
    (Array.isArray(parent.transitions) ? parent.transitions : []).forEach(
        (raw, index) => {
          result.push(
              relationshipFromObject(raw, "WorkflowTransition", "TRANSITION",
                  refId(raw.source), refId(raw.target), index,
                  {id: parent.id, feature: "transitions"}));
        });
  }
  if (type === "Principal") {
    (Array.isArray(parent.permissions) ? parent.permissions : []).forEach(
        (raw, index) => {
          result.push(relationshipFromObject(raw, "Permission", "PERMISSION",
              parent.id, refId(raw.targetResource), index,
              {id: parent.id, feature: "permissions"}));
        });
  }
  nestedContainmentsForType(type).filter(
      (entry) => !entry.relationshipOnly).forEach((entry) => {
    const rawValues = entry.singleton
        ? (parent?.[entry.feature] ? [parent[entry.feature]] : [])
        : (Array.isArray(parent?.[entry.feature]) ? parent[entry.feature] : []);
    rawValues.forEach((raw) => {
      if (raw && typeof raw === "object") {
        addNestedRelationshipObjects(raw, result);
      }
    });
  });
}

export function pimSemanticRelationshipsFromRoot(modelJson) {
  const result = [];
  if (!modelJson || typeof modelJson !== "object") {
    return result;
  }
  addRootRelationshipObjects(modelJson, result);
  pimSemanticElementsFromRoot(modelJson).forEach((element) =>
      addNestedRelationshipObjects(element, result));
  return result.filter(Boolean);
}

export function pimSemanticEdgeObjectSpec(kind, sourceType, targetType) {
  const normalizedKind = String(kind || "").toUpperCase();
  if (["READS", "WRITES", "READ_WRITE", "APPEND", "DELETE",
        "DATA_ACCESS"].includes(normalizedKind)
      && pimTypeMatches(sourceType, "FunctionTarget")
      && pimTypeMatches(targetType, "DataAccessTarget")) {
    return {
      eClass: "DataAccess",
      rootFeature: "dataAccesses",
      defaults: {
        mode: normalizedKind === "READS" ? "READ"
            : normalizedKind === "WRITES" ? "WRITE"
                : normalizedKind === "DATA_ACCESS" ? "READ_WRITE"
                    : normalizedKind
      }
    };
  }
  if (["INVOKES", "TRIGGERS"].includes(normalizedKind)
      && pimTypeMatches(sourceType, "InvocationSource")
      && pimTypeMatches(targetType, "InvocationTarget")) {
    return {
      eClass: "Trigger",
      rootFeature: "triggers",
      defaults: {
        invocationMode: normalizedKind === "TRIGGERS" ? "ASYNCHRONOUS"
            : "SYNCHRONOUS"
      }
    };
  }
  if (normalizedKind === "TRANSITION"
      && pimTypeMatches(sourceType, "WorkflowState")
      && pimTypeMatches(targetType, "WorkflowState")) {
    return {
      eClass: "WorkflowTransition",
      rootFeature: "transitions",
      defaults: {conditionExpression: "", defaultTransition: false}
    };
  }
  if (normalizedKind === "PERMISSION"
      && pimTypeMatches(sourceType, "Principal")
      && pimTypeMatches(targetType, "ProtectedResource")) {
    return {
      eClass: "Permission",
      rootFeature: "permissions",
      defaults: {effect: "ALLOW", leastPrivilegeConfirmed: false}
    };
  }
  if (normalizedKind === "SUBSCRIBES_TO"
      && pimTypeMatches(sourceType, "Topic")
      && pimTypeMatches(targetType, "SubscriptionTarget")) {
    return {
      eClass: "Subscription",
      rootFeature: "subscriptions",
      defaults: {rawDelivery: false, deadLetterRequired: false}
    };
  }
  const flowType = ({
    REQUEST_RESPONSE: "RequestResponseFlow",
    EVENT_FLOW: "EventFlow",
    MESSAGE_FLOW: "MessageFlow",
    PUB_SUB: "PubSubFlow",
    ORCHESTRATES: "OrchestrationFlow",
    EXTERNAL_CALL: "ExternalIntegrationFlow",
    FLOW: "RequestResponseFlow"
  })[normalizedKind];
  if (flowType && pimTypeMatches(sourceType, "FlowEndpoint")
      && pimTypeMatches(targetType, "FlowEndpoint")) {
    return {
      eClass: flowType,
      rootFeature: "flows",
      defaults: {
        flowPurpose: "",
        criticalPath: false,
        containsPersonalData: false
      }
    };
  }
  if (normalizedKind === "TRACE") {
    return {
      eClass: "TraceLink",
      rootFeature: "links",
      defaults: {linkType: "OTHER", confidence: ""}
    };
  }
  return null;
}

export function pimRelationshipSemanticCopy(relationship, graph) {
  const type = pimTypeOf(relationship);
  const copy = stripRuntimeFields({
    ...(pimSemanticEdgeObjectSpec(relationship.kind, relationship.sourceType,
        relationship.targetType)?.defaults || {}),
    ...relationship,
    eClass: type
  });
  const sourceId = refId(relationship.source) || relationship.sourceElementId;
  const targetId = refId(relationship.target) || relationship.targetElementId;
  const target = graph?.elementsById?.get(targetId);
  if (type === "DataAccess") {
    copy.function = copy.function || sourceId;
    copy.store = copy.store || targetId;
    copy.mode = copy.mode || (relationship.kind === "READS" ? "READ"
        : relationship.kind === "WRITES" ? "WRITE" : "READ_WRITE");
    delete copy.source;
    delete copy.target;
  } else if (type === "Trigger") {
    copy.source = copy.source || sourceId;
    if (pimTypeMatches(target, "WorkflowTarget")) {
      copy.startsWorkflow = copy.startsWorkflow || targetId;
      delete copy.invokesFunction;
    } else {
      copy.invokesFunction = copy.invokesFunction || targetId;
      delete copy.startsWorkflow;
    }
    delete copy.target;
  } else if (type === "Permission") {
    copy.targetResource = copy.targetResource || targetId;
    copy.effect ||= "ALLOW";
    delete copy.source;
    delete copy.target;
  } else if (type === "Subscription") {
    copy.channel = copy.channel || sourceId;
    copy.target = copy.target || targetId;
    delete copy.source;
  } else if (type === "TraceLink") {
    copy.source = copy.source || sourceId;
    copy.target = copy.target || targetId;
    copy.sourceElementId ||= copy.source;
    copy.targetElementId ||= copy.target;
  } else {
    copy.source = copy.source || sourceId;
    copy.target = copy.target || targetId;
  }
  delete copy.sourceElementId;
  delete copy.targetElementId;
  return copy;
}
