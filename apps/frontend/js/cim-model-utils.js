export const CIM_ROOT_CONTAINMENTS = Object.freeze([
  {
    feature: "requirements",
    types: [
      "Requirement",
      "NonFunctionalRequirement",
      "SecurityConstraint",
      "PrivacyConstraint",
      "ComplianceConstraint"
    ],
    required: false,
    title: "Requirements"
  },
  {
    feature: "goals",
    types: ["BusinessGoal"],
    required: true,
    title: "Goals"
  },
  {feature: "kpis", types: ["KPI"], required: false, title: "KPIs"},
  {
    feature: "stakeholders",
    types: ["Stakeholder"],
    required: false,
    title: "Stakeholders"
  },
  {
    feature: "actors",
    types: ["Actor", "ExternalSystem"],
    required: true,
    title: "Actors and External Systems"
  },
  {feature: "roles", types: ["Role"], required: false, title: "Roles"},
  {
    feature: "capabilities",
    types: ["BusinessCapability"],
    required: true,
    title: "Capabilities"
  },
  {
    feature: "capabilityDependencies",
    types: ["CapabilityDependency"],
    required: false,
    title: "Capability Dependencies",
    relationshipOnly: true
  },
  {
    feature: "boundedContexts",
    types: ["BoundedContextCandidate"],
    required: false,
    title: "Bounded Contexts"
  },
  {
    feature: "glossary",
    types: ["UbiquitousLanguageTerm"],
    required: false,
    title: "Glossary"
  },
  {
    feature: "entities",
    types: ["DomainEntity"],
    required: false,
    title: "Domain Entities"
  },
  {
    feature: "valueObjects",
    types: ["ValueObject"],
    required: false,
    title: "Value Objects"
  },
  {
    feature: "relationships",
    types: ["DomainRelationship"],
    required: false,
    title: "Domain Relationships",
    relationshipOnly: true
  },
  {
    feature: "aggregates",
    types: ["AggregateCandidate"],
    required: false,
    title: "Aggregates"
  },
  {
    feature: "informationItems",
    types: ["InformationItem"],
    required: false,
    title: "Information Items"
  },
  {
    feature: "classifications",
    types: ["DataClassification"],
    required: false,
    title: "Data Classifications"
  },
  {feature: "commands", types: ["Command"], required: false, title: "Commands"},
  {feature: "queries", types: ["Query"], required: false, title: "Queries"},
  {
    feature: "events",
    types: ["BusinessEvent"],
    required: false,
    title: "Business Events"
  },
  {
    feature: "businessErrors",
    types: ["BusinessError"],
    required: false,
    title: "Business Errors"
  },
  {
    feature: "conditions",
    types: ["Condition"],
    required: false,
    title: "Conditions"
  },
  {
    feature: "processes",
    types: ["BusinessProcess"],
    required: false,
    title: "Business Processes"
  },
  {feature: "policies", types: ["Policy"], required: false, title: "Policies"},
  {
    feature: "decisionTables",
    types: ["DecisionTable"],
    required: false,
    title: "Decision Tables"
  },
  {feature: "risks", types: ["Risk"], required: false, title: "Risks"},
  {
    feature: "assumptions",
    types: ["Assumption"],
    required: false,
    title: "Assumptions"
  },
  {
    feature: "hotspots",
    types: ["Hotspot"],
    required: false,
    title: "Hotspots"
  },
  {
    feature: "transformationProfile",
    types: ["TransformationProfile"],
    required: false,
    title: "Transformation Profile",
    singleton: true
  },
  {
    feature: "traceModel",
    types: ["TraceModel"],
    required: false,
    title: "Trace Model",
    singleton: true
  },
  {
    feature: "readiness",
    types: ["ProductionReadinessAssessment"],
    required: false,
    title: "Readiness",
    singleton: true
  }
]);

export const CIM_PROCESS_STEP_TYPES = Object.freeze([
  "StartStep",
  "EndStep",
  "CommandStep",
  "QueryStep",
  "EventStep",
  "PolicyStep",
  "HumanTaskStep",
  "ExternalInteractionStep",
  "DecisionStep",
  "WaitStep"
]);

export const CIM_ABSTRACT_TYPES = Object.freeze([
  "ModelElement",
  "TraceableElement",
  "SemanticRelationship",
  "TransformationAssumption",
  "DomainConcept",
  "ProcessStep"
]);

export const CIM_REQUIRED_FEATURES = Object.freeze({
  ModelElement: ["id", "name"],
  CIMModel: ["domainName", "goals", "actors", "capabilities"],
  Command: ["expectedEvents"],
  Query: ["output"],
  DomainEntity: ["identityAttribute"],
  DomainRelationship: ["source", "target"],
  AggregateCandidate: ["root", "members"],
  LifecycleStateDefinition: ["stateName"],
  NonFunctionalRequirement: ["constrainedElements"],
  PrivacyConstraint: ["dataItems"],
  BusinessCapability: ["supports"],
  CapabilityDependency: ["source", "target"],
  UbiquitousLanguageTerm: ["term"],
  BusinessProcess: ["steps"],
  CommandStep: ["command"],
  QueryStep: ["query"],
  EventStep: ["event"],
  PolicyStep: ["policy"],
  ExternalInteractionStep: ["externalSystem"],
  ProcessTransition: ["source", "target"],
  DecisionTable: ["rules"],
  KeyValue: ["key"],
  Annotation: ["owner"],
  TraceLink: ["linkType", "traceModel", "source", "target"],
  StructuredDocument: ["format"],
  ReadinessFinding: ["severity", "assessment"],
  ReadinessCheck: ["checkId", "severity", "assessment"],
  ManualDecision: ["question"]
});

export const CIM_COMMON_METADATA_FIELDS = Object.freeze([
  "id",
  "name",
  "summary",
  "description",
  "documentation",
  "modelTags",
  "externalId",
  "lifecycleStatus"
]);

export const CIM_TRACEABILITY_FIELDS = Object.freeze([
  "sourceReference",
  "sourceExcerpt",
  "sourceQualifiedName",
  "sourceUri",
  "sourceLine",
  "traceId",
  "generatedFrom",
  "generatedByTransformation",
  "rationale",
  "reviewStatus",
  "reviewNotes",
  "manuallyMaintained"
]);

const CIM_SUPERTYPES = Object.freeze({
  ExternalSystem: ["Actor", "TraceableElement", "ModelElement"],
  Requirement: ["TraceableElement", "ModelElement"],
  NonFunctionalRequirement: ["Requirement", "TraceableElement", "ModelElement"],
  SecurityConstraint: [
    "NonFunctionalRequirement",
    "Requirement",
    "TraceableElement",
    "ModelElement"
  ],
  PrivacyConstraint: [
    "NonFunctionalRequirement",
    "Requirement",
    "TraceableElement",
    "ModelElement"
  ],
  ComplianceConstraint: [
    "NonFunctionalRequirement",
    "Requirement",
    "TraceableElement",
    "ModelElement"
  ],
  DomainEntity: ["DomainConcept", "TraceableElement", "ModelElement"],
  ValueObject: ["DomainConcept", "TraceableElement", "ModelElement"],
  AggregateCandidate: ["DomainConcept", "TraceableElement", "ModelElement"],
  DomainRelationship: [
    "SemanticRelationship",
    "TraceableElement",
    "ModelElement"
  ],
  CapabilityDependency: [
    "SemanticRelationship",
    "TraceableElement",
    "ModelElement"
  ],
  ProcessTransition: [
    "SemanticRelationship",
    "TraceableElement",
    "ModelElement"
  ],
  TraceLink: ["TraceableElement", "ModelElement"],
  TraceModel: ["TraceableElement", "ModelElement"],
  TransformationAssumption: ["TraceableElement", "ModelElement"],
  Assumption: [
    "TransformationAssumption",
    "TraceableElement",
    "ModelElement"
  ],
  ProductionReadinessAssessment: ["TraceableElement", "ModelElement"],
  ReadinessFinding: ["TraceableElement", "ModelElement"],
  ReadinessCheck: ["TraceableElement", "ModelElement"],
  ManualDecision: ["TraceableElement", "ModelElement"],
  StructuredDocument: ["TraceableElement", "ModelElement"],
  Annotation: ["KeyValue"],
  AcceptanceCriterion: ["TraceableElement", "ModelElement"],
  BusinessGoal: ["TraceableElement", "ModelElement"],
  KPI: ["TraceableElement", "ModelElement"],
  Stakeholder: ["TraceableElement", "ModelElement"],
  Actor: ["TraceableElement", "ModelElement"],
  Role: ["TraceableElement", "ModelElement"],
  BusinessCapability: ["TraceableElement", "ModelElement"],
  BoundedContextCandidate: ["TraceableElement", "ModelElement"],
  UbiquitousLanguageTerm: ["TraceableElement", "ModelElement"],
  LifecycleStateDefinition: ["TraceableElement", "ModelElement"],
  BusinessInvariant: ["TraceableElement", "ModelElement"],
  InformationItem: ["TraceableElement", "ModelElement"],
  DataClassification: ["TraceableElement", "ModelElement"],
  Command: ["TraceableElement", "ModelElement"],
  Query: ["TraceableElement", "ModelElement"],
  BusinessEvent: ["TraceableElement", "ModelElement"],
  BusinessError: ["TraceableElement", "ModelElement"],
  Condition: ["TraceableElement", "ModelElement"],
  BusinessProcess: ["TraceableElement", "ModelElement"],
  Policy: ["TraceableElement", "ModelElement"],
  DecisionTable: ["TraceableElement", "ModelElement"],
  DecisionRule: ["TraceableElement", "ModelElement"],
  ExceptionScenario: ["TraceableElement", "ModelElement"],
  TemporalConstraint: ["TraceableElement", "ModelElement"],
  QualityScenario: ["TraceableElement", "ModelElement"],
  Risk: ["TraceableElement", "ModelElement"],
  Hotspot: ["TraceableElement", "ModelElement"],
  TransformationProfile: ["TraceableElement", "ModelElement"],
  StartStep: ["ProcessStep", "TraceableElement", "ModelElement"],
  EndStep: ["ProcessStep", "TraceableElement", "ModelElement"],
  CommandStep: ["ProcessStep", "TraceableElement", "ModelElement"],
  QueryStep: ["ProcessStep", "TraceableElement", "ModelElement"],
  EventStep: ["ProcessStep", "TraceableElement", "ModelElement"],
  PolicyStep: ["ProcessStep", "TraceableElement", "ModelElement"],
  HumanTaskStep: ["ProcessStep", "TraceableElement", "ModelElement"],
  ExternalInteractionStep: ["ProcessStep", "TraceableElement", "ModelElement"],
  DecisionStep: ["ProcessStep", "TraceableElement", "ModelElement"],
  WaitStep: ["ProcessStep", "TraceableElement", "ModelElement"]
});

export const CIM_NESTED_CONTAINMENTS = Object.freeze({
  ModelElement: [
    {feature: "annotations", types: ["Annotation"]}
  ],
  Requirement: [
    {feature: "acceptanceCriteria", types: ["AcceptanceCriterion"]}
  ],
  NonFunctionalRequirement: [
    {feature: "acceptanceCriteria", types: ["AcceptanceCriterion"]},
    {feature: "scenarios", types: ["QualityScenario"]}
  ],
  SecurityConstraint: [
    {feature: "acceptanceCriteria", types: ["AcceptanceCriterion"]},
    {feature: "scenarios", types: ["QualityScenario"]}
  ],
  PrivacyConstraint: [
    {feature: "acceptanceCriteria", types: ["AcceptanceCriterion"]},
    {feature: "scenarios", types: ["QualityScenario"]}
  ],
  ComplianceConstraint: [
    {feature: "acceptanceCriteria", types: ["AcceptanceCriterion"]},
    {feature: "scenarios", types: ["QualityScenario"]}
  ],
  DomainEntity: [
    {feature: "lifecycleStates", types: ["LifecycleStateDefinition"]},
    {feature: "invariants", types: ["BusinessInvariant"]}
  ],
  AggregateCandidate: [
    {feature: "invariants", types: ["BusinessInvariant"]}
  ],
  BusinessProcess: [
    {feature: "steps", types: CIM_PROCESS_STEP_TYPES},
    {
      feature: "transitions",
      types: ["ProcessTransition"],
      relationshipOnly: true
    },
    {feature: "exceptions", types: ["ExceptionScenario"]},
    {feature: "temporalConstraints", types: ["TemporalConstraint"]}
  ],
  DecisionTable: [
    {feature: "rules", types: ["DecisionRule"]}
  ],
  TransformationProfile: [
    {feature: "requiredDecisions", types: ["ManualDecision"]}
  ],
  TraceModel: [
    {feature: "links", types: ["TraceLink"], relationshipOnly: true}
  ],
  ProductionReadinessAssessment: [
    {feature: "findings", types: ["ReadinessFinding"]},
    {feature: "checks", types: ["ReadinessCheck"]},
    {feature: "manualDecisions", types: ["ManualDecision"]}
  ]
});

const TYPE_TO_ROOT_CONTAINMENT = new Map();
CIM_ROOT_CONTAINMENTS.forEach((entry) => {
  entry.types.forEach((type) => TYPE_TO_ROOT_CONTAINMENT.set(type, entry));
});

export function cimTypeOf(value) {
  if (typeof value === "string") {
    return value;
  }
  return String(value?.eClass || value?.type || "");
}

export function cimTypeMatches(value, expectedType) {
  const expected = String(expectedType || "").trim();
  if (!expected || expected === "*" || expected === "EObject") {
    return true;
  }
  const actual = cimTypeOf(value);
  if (actual === expected) {
    return true;
  }
  if (expected === "ModelElement" && actual) {
    return true;
  }
  return (CIM_SUPERTYPES[actual] || []).includes(expected);
}

export function cimRootContainmentForType(type) {
  return TYPE_TO_ROOT_CONTAINMENT.get(String(type || "")) || null;
}

export function isCimRelationshipElementType(type) {
  return ["DomainRelationship", "CapabilityDependency", "ProcessTransition",
    "TraceLink"].includes(String(type || ""));
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

export function setReferenceValue(element, feature, ids, many = true) {
  if (!element || !feature) {
    return;
  }
  const values = Array.isArray(ids) ? ids.map(refId).filter(Boolean)
      : [refId(ids)].filter(Boolean);
  element[feature] = many ? [...new Set(values)] : (values[0] || null);
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
    element[feature] = refIds(element[feature]).filter(
        (item) => item !== String(id));
  } else if (refId(element[feature]) === String(id)) {
    element[feature] = null;
  }
}

export function hasReferenceValue(element, feature, id) {
  return refIds(element?.[feature]).includes(String(id));
}

export function elementLabel(element) {
  return String(element?.name || element?.label || element?.term
      || element?.id || "Element");
}

export function compactRefLabels(value, elementsById, limit = 3) {
  const labels = refIds(value).slice(0, limit).map((id) => {
    const element = elementsById?.get?.(id);
    return elementLabel(element || {id});
  });
  const extra = Math.max(0, refIds(value).length - limit);
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

export function missingRequiredFeatures(element, extraRequired = []) {
  const type = cimTypeOf(element);
  const required = new Set([
    ...(CIM_SUPERTYPES[type] || []).flatMap(
        (supertype) => CIM_REQUIRED_FEATURES[supertype] || []),
    ...(CIM_REQUIRED_FEATURES[type] || []),
    ...extraRequired
  ]);
  return [...required].filter((feature) => {
    if (derivedRequiredFeatureIsSatisfied(element, feature)) {
      return false;
    }
    if (traceEndpointRequirementIsSatisfied(element, feature)) {
      return false;
    }
    return !isPresent(element?.[feature]);
  });
}

function derivedRequiredFeatureIsSatisfied(element, feature) {
  const type = cimTypeOf(element);
  if (type === "Annotation" && feature === "owner") {
    return Boolean(element?.__ownerId || element?.owner);
  }
  if (type === "TraceLink" && feature === "traceModel") {
    return true;
  }
  if ((type === "ReadinessFinding" || type === "ReadinessCheck")
      && feature === "assessment") {
    return Boolean(element?.__ownerId || element?.assessment);
  }
  return false;
}

function traceEndpointRequirementIsSatisfied(element, feature) {
  if (cimTypeOf(element) !== "TraceLink") {
    return false;
  }
  if (feature === "source") {
    return isPresent(element?.source) || isPresent(element?.sourceElementId);
  }
  if (feature === "target") {
    return isPresent(element?.target) || isPresent(element?.targetElementId);
  }
  return false;
}

export function semanticEdgeObjectSpec(kind, sourceType, targetType) {
  const normalizedKind = String(kind || "").toUpperCase();
  if (normalizedKind === "DOMAIN_RELATIONSHIP"
      && cimTypeMatches(sourceType, "DomainConcept")
      && cimTypeMatches(targetType, "DomainConcept")) {
    return {
      eClass: "DomainRelationship",
      rootFeature: "relationships",
      defaults: {relationshipType: "ASSOCIATION"}
    };
  }
  if (normalizedKind === "DEPENDS_ON"
      && cimTypeMatches(sourceType, "BusinessCapability")
      && cimTypeMatches(targetType, "BusinessCapability")) {
    return {
      eClass: "CapabilityDependency",
      rootFeature: "capabilityDependencies",
      defaults: {dependencyReason: "", criticalPath: false}
    };
  }
  if (normalizedKind === "TRANSITION"
      && cimTypeMatches(sourceType, "ProcessStep")
      && cimTypeMatches(targetType, "ProcessStep")) {
    return {
      eClass: "ProcessTransition",
      rootFeature: "transitions",
      defaults: {label: "", conditionExpression: "", orderIndex: 0}
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

function clone(value) {
  return value == null ? value : structuredClone(value);
}

function stripRuntimeFields(element) {
  const copy = clone(element) || {};
  const type = cimTypeOf(copy);
  delete copy.x;
  delete copy.y;
  delete copy.label;
  delete copy.status;
  delete copy.tags;
  delete copy.__collapsed;
  delete copy.__collapsedSummary;
  delete copy.visualOnly;
  delete copy.bundle;
  delete copy.countsByKind;
  delete copy.underlyingRelationshipIds;
  if (type === "Annotation") {
    delete copy.owner;
  }
  if (type === "TraceLink") {
    delete copy.traceModel;
  }
  if (type === "ReadinessFinding" || type === "ReadinessCheck"
      || type === "ManualDecision") {
    delete copy.assessment;
  }
  return copy;
}

function nestedContainmentsForType(elementType) {
  const inheritedTypes = CIM_SUPERTYPES[elementType] || [];
  const shouldIncludeModelElement = elementType === "ModelElement"
      || inheritedTypes.includes("ModelElement");
  const entries = [
    ...(shouldIncludeModelElement ? (CIM_NESTED_CONTAINMENTS.ModelElement
        || []) : []),
    ...(CIM_NESTED_CONTAINMENTS[elementType] || [])
  ];
  const byFeature = new Map();
  entries.forEach((entry) => {
    if (!entry?.feature) {
      return;
    }
    if (!byFeature.has(entry.feature)) {
      byFeature.set(entry.feature, {
        ...entry,
        types: [...(entry.types || [])]
      });
      return;
    }
    const existing = byFeature.get(entry.feature);
    existing.types = [...new Set([...(existing.types || []),
      ...(entry.types || [])])];
    existing.relationshipOnly = Boolean(
        existing.relationshipOnly || entry.relationshipOnly);
  });
  return [...byFeature.values()];
}

function nestedContainmentCopies(parentId, parentType, graph, feature) {
  const parent = graph.elementsById.get(parentId);
  const ids = new Set(refIds(parent?.[feature]));
  const childIds = new Set();
  const children = [];
  const containment = nestedContainmentsForType(parentType).find(
      (entry) => entry.feature === feature);
  const allowedTypes = new Set(containment?.types || []);
  graph.elementsById.forEach((candidate) => {
    const candidateType = cimTypeOf(candidate);
    if (allowedTypes.size && !allowedTypes.has(candidateType)) {
      return;
    }
    if ((candidate.__ownerId === parentId
            && candidate.__containmentFeature === feature)
        || ids.has(candidate.id)) {
      if (childIds.has(candidate.id)) {
        return;
      }
      childIds.add(candidate.id);
      children.push(candidate);
    }
  });
  graph.relationshipsById?.forEach((candidate) => {
    const candidateType = cimTypeOf(candidate);
    if (allowedTypes.size && !allowedTypes.has(candidateType)) {
      return;
    }
    if ((candidate.__ownerId === parentId
            && candidate.__containmentFeature === feature)
        || ids.has(candidate.id)
        || relationshipBelongsToParentContainment(candidate, parent,
            parentId, feature, graph)) {
      if (childIds.has(candidate.id)) {
        return;
      }
      childIds.add(candidate.id);
      children.push(candidate);
    }
  });
  return children.map((child) => {
    const copy = stripRuntimeFields(child);
    if (isCimRelationshipElementType(cimTypeOf(child))) {
      copy.source = refId(child.source) || child.sourceElementId;
      copy.target = refId(child.target) || child.targetElementId;
    }
    if (cimTypeOf(child) === "TraceLink") {
      copy.source = refId(child.source) || child.sourceElementId;
      copy.target = refId(child.target) || child.targetElementId;
      copy.sourceElementId ||= copy.source;
      copy.targetElementId ||= copy.target;
    }
    attachNestedContainments(copy, child.id, child.eClass || child.type, graph);
    return copy;
  });
}

function relationshipBelongsToParentContainment(relationship, parent, parentId,
    feature, graph) {
  const type = cimTypeOf(relationship);
  if (feature === "transitions" && type === "ProcessTransition") {
    const stepIds = new Set(refIds(parent?.steps));
    const sourceId = refId(relationship.source) || relationship.sourceElementId;
    const targetId = refId(relationship.target) || relationship.targetElementId;
    const source = graphElementOwner(graph, sourceId);
    const target = graphElementOwner(graph, targetId);
    return (stepIds.has(sourceId) && stepIds.has(targetId))
        || (source === parentId && target === parentId);
  }
  if (feature === "links" && type === "TraceLink") {
    return true;
  }
  return false;
}

function graphElementOwner(graph, elementId) {
  return graph?.elementsById?.get(elementId)?.__ownerId || "";
}

function attachNestedContainments(copy, elementId, elementType, graph) {
  nestedContainmentsForType(elementType).forEach((entry) => {
    copy[entry.feature] = nestedContainmentCopies(elementId, elementType,
        graph, entry.feature);
  });
}

export function populateCimRootContainments(root, graph) {
  if (!root || !graph?.elementsById) {
    return root;
  }
  CIM_ROOT_CONTAINMENTS.forEach((entry) => {
    root[entry.feature] = entry.singleton ? null : [];
  });

  graph.elementsById.forEach((element) => {
    const containment = cimRootContainmentForType(cimTypeOf(element));
    if (!containment || containment.relationshipOnly) {
      return;
    }
    const copy = stripRuntimeFields(element);
    attachNestedContainments(copy, element.id, element.eClass || element.type,
        graph);
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
    const type = relationship.eClass || semanticEdgeObjectSpec(
        relationship.kind,
        relationship.sourceType,
        relationship.targetType)?.eClass;
    const containment = cimRootContainmentForType(type);
    if (!containment || !containment.relationshipOnly) {
      return;
    }
    const copy = stripRuntimeFields({
      ...relationship,
      eClass: type,
      source: relationship.source || relationship.sourceElementId,
      target: relationship.target || relationship.targetElementId
    });
    root[containment.feature].push(copy);
  });

  const traceLinks = [];
  graph.relationshipsById?.forEach((relationship) => {
    if (relationship.visualOnly || cimTypeOf(relationship) !== "TraceLink") {
      return;
    }
    traceLinks.push(stripRuntimeFields({
      ...relationship,
      eClass: "TraceLink",
      source: refId(relationship.source) || relationship.sourceElementId,
      target: refId(relationship.target) || relationship.targetElementId
    }));
  });
  if (traceLinks.length) {
    root.traceModel ??= {eClass: "TraceModel", id: "trace-model", links: []};
    const existing = new Set(refIds(root.traceModel.links));
    const existingIds = new Set((Array.isArray(root.traceModel.links)
        ? root.traceModel.links : []).map((link) => link?.id).filter(Boolean));
    root.traceModel.links = [
      ...(Array.isArray(root.traceModel.links) ? root.traceModel.links : []),
      ...traceLinks.filter((link) => !existing.has(link.id)
          && !existingIds.has(link.id))
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
  if (isCimRelationshipElementType(type)) {
    return null;
  }
  const id = String(raw.id || generatedSemanticId(type, index, owner?.id));
  return {
    eClass: type,
    id,
    name: String(raw.name || raw.label || raw.term || id),
    label: String(raw.name || raw.label || raw.term || id),
    ...clone(raw),
    id,
    eClass: type,
    __ownerId: owner?.id || raw.__ownerId,
    __containmentFeature: owner?.feature || raw.__containmentFeature
  };
}

function collectNestedSemanticElements(parent, result) {
  const type = cimTypeOf(parent);
  const containments = nestedContainmentsForType(type);
  containments.forEach((entry) => {
    if (entry.relationshipOnly) {
      return;
    }
    const values = Array.isArray(parent?.[entry.feature])
        ? parent[entry.feature] : [];
    values.forEach((raw, index) => {
      const fallbackType = raw?.eClass || raw?.type || entry.types[0];
      const child = normalizeSemanticElement(raw, fallbackType, index, {
        id: parent.id,
        feature: entry.feature
      });
      if (!child) {
        return;
      }
      result.push(child);
      collectNestedSemanticElements(child, result);
    });
  });
}

export function cimSemanticElementsFromRoot(modelJson) {
  const result = [];
  if (!modelJson || typeof modelJson !== "object") {
    return result;
  }
  CIM_ROOT_CONTAINMENTS.forEach((entry) => {
    if (entry.relationshipOnly) {
      return;
    }
    const rawValues = entry.singleton
        ? (modelJson[entry.feature] ? [modelJson[entry.feature]] : [])
        : (Array.isArray(modelJson[entry.feature])
            ? modelJson[entry.feature] : []);
    rawValues.forEach((raw, index) => {
      const fallbackType = raw?.eClass || raw?.type || entry.types[0];
      const element = normalizeSemanticElement(raw, fallbackType, index);
      if (!element) {
        return;
      }
      result.push(element);
      collectNestedSemanticElements(element, result);
    });
  });
  return result;
}

function relationshipFromSemanticObject(raw, fallbackType, fallbackKind, index,
    owner = null) {
  if (!raw || typeof raw !== "object") {
    return null;
  }
  const type = raw.eClass || raw.type || fallbackType;
  const sourceId = refId(raw.source) || refId(raw.sourceElementId);
  const targetId = refId(raw.target) || refId(raw.targetElementId);
  if (!sourceId || !targetId) {
    return null;
  }
  const id = String(raw.id || `rel-${String(fallbackKind).toLowerCase()}-${
      sourceId}-${targetId}-${index + 1}`);
  return {
    ...clone(raw),
    id,
    eClass: type,
    kind: fallbackKind,
    sourceElementId: sourceId,
    targetElementId: targetId,
    source: sourceId,
    target: targetId,
    __ownerId: owner?.id || raw.__ownerId,
    __containmentFeature: owner?.feature || raw.__containmentFeature
  };
}

export function cimSemanticRelationshipsFromRoot(modelJson) {
  const result = [];
  const addMany = (values, type, kind, owner = null) => {
    (Array.isArray(values) ? values : []).forEach((raw, index) => {
      const relationship = relationshipFromSemanticObject(raw, type, kind,
          index, owner);
      if (relationship) {
        result.push(relationship);
      }
    });
  };
  addMany(modelJson?.relationships, "DomainRelationship",
      "DOMAIN_RELATIONSHIP");
  addMany(modelJson?.capabilityDependencies, "CapabilityDependency",
      "DEPENDS_ON");

  (Array.isArray(modelJson?.processes) ? modelJson.processes : []).forEach(
      (process) => addMany(process?.transitions, "ProcessTransition",
          "TRANSITION", {id: process?.id, feature: "transitions"}));

  const traceLinks = Array.isArray(modelJson?.traceModel?.links)
      ? modelJson.traceModel.links : [];
  traceLinks.forEach((raw, index) => {
    const sourceId = refId(raw.source) || String(raw.sourceElementId || "");
    const targetId = refId(raw.target) || String(raw.targetElementId || "");
    if (!sourceId || !targetId) {
      return;
    }
    result.push({
      ...clone(raw),
      id: String(raw.id || `trace-${index + 1}`),
      eClass: "TraceLink",
      kind: "TRACE",
      sourceElementId: sourceId,
      targetElementId: targetId,
      source: sourceId,
      target: targetId,
      __ownerId: modelJson.traceModel?.id,
      __containmentFeature: "links"
    });
  });
  return result;
}
