/**
 * Concept assignment: maps metamodel concepts to atomic tasks in the SPEM tree.
 */
import { collectAllTasks } from "./process-walk.mjs";

export const KERNEL_TYPES = new Set([
  "ModelElement",
  "NamedModelElement",
  "TraceableElement",
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
  "EventCarrier",
  "ConfigurableElement",
  "KeyValue",
  "Annotation",
  "SemanticRelationship",
  "Multiplicity",
  "Cardinality",
  "Expression",
  "StructuredDocument",
  "DomainConcept",
  "Objective",
  "StakeholderConcern",
  "Persona",
  "ComputeElement",
  "StorageElement",
  "IntegrationElement",
  "AwsResource",
  "AwsNativeResource",
  "WafAssociableResource",
  "ValueExpression",
  "NamedValueExpression",
]);

export const SHARED_READINESS_TYPES = new Set([
  "TraceModel",
  "TraceLink",
  "TransformationAssumption",
  "ProductionReadinessAssessment",
  "ReadinessFinding",
  "ReadinessCheck",
  "ManualDecision",
]);

export const SHARED_ENUMS = new Set([
  "Priority",
  "Severity",
  "ConstraintStrength",
  "LifecycleStatus",
  "TraceConfidence",
  "TraceLinkType",
  "FindingType",
  "StructuredFormat",
  "ExpressionLanguage",
  "ExpressionPhase",
  "ManualDecisionState",
  "Decision",
]);

// Provider-specific value vocabularies are work products of the PSM
// platform-contract task. They must be owned explicitly rather than hidden
// under the generic readiness task.
export const PSM_ENUMS = new Set([
  "EventBridgeHttpMethod",
  "EventBridgeTargetKind",
  "EventBridgeConnectionAuthorizationType",
  "SqsQueueType",
  "SnsProtocol",
  "SnsFilterPolicyScope",
  "StepFunctionType",
  "DynamoDbBillingMode",
  "DynamoDbAttributeType",
  "DynamoDbKeyType",
  "DynamoDbProjectionType",
  "DynamoDbStreamViewType",
  "DynamoDbTableClass",
  "S3VersioningStatus",
  "S3BlockPublicAccessMode",
  "CognitoMfaConfiguration",
  "IamEffect",
  "ParameterType",
  "SsmParameterTier",
  "ValueSourceKind",
  "ValueType",
  "CloudWatchComparisonOperator",
  "LogGroupClass",
  "SamCapability",
]);

export const COVERAGE_GROUPS = {
  "shared-kernel": KERNEL_TYPES,
  "shared-enums": SHARED_ENUMS,
  "shared-readiness": SHARED_READINESS_TYPES,
  "psm-enums": PSM_ENUMS,
};

/**
 * @param {import('./process-types.mjs').ProcessPhaseSpec[]} phases
 * @param {string[]} concepts
 * @returns {Map<string, string>}
 */
export function assignConceptsToTasks(phases, concepts) {
  const entries = collectAllTasks(phases);
  const explicit = new Map();
  const groupTasks = new Map();
  for (const { task } of entries) {
    for (const group of task.coverageGroups || []) {
      if (groupTasks.has(group)) {
        throw new Error(`Coverage group ${group} is assigned to more than one task.`);
      }
      groupTasks.set(group, task.id);
    }
  }
  const readinessTaskId =
    groupTasks.get("shared-readiness") || entries.find((e) => e.task.readinessTypes)?.task.id;
  const rootTaskId =
    entries.find((e) => e.task.types?.includes("CIMModel"))?.task.id ||
    entries.find((e) => e.task.types?.includes("PIMModel"))?.task.id ||
    entries.find((e) => e.task.types?.includes("AwsPsmModel"))?.task.id;

  for (const { task } of entries) {
    for (const type of task.types || []) {
      explicit.set(type, task.id);
    }
  }

  const assignment = new Map();
  for (const concept of concepts) {
    if (explicit.has(concept)) {
      assignment.set(concept, explicit.get(concept));
      continue;
    }
    if (KERNEL_TYPES.has(concept) && groupTasks.get("shared-kernel")) {
      assignment.set(concept, groupTasks.get("shared-kernel"));
      continue;
    }
    if (SHARED_ENUMS.has(concept) && groupTasks.get("shared-enums")) {
      assignment.set(concept, groupTasks.get("shared-enums"));
      continue;
    }
    if (PSM_ENUMS.has(concept) && groupTasks.get("psm-enums")) {
      assignment.set(concept, groupTasks.get("psm-enums"));
      continue;
    }
    if (SHARED_READINESS_TYPES.has(concept) && readinessTaskId) {
      assignment.set(concept, readinessTaskId);
      continue;
    }
    // Do not silently assign an unowned concept to a generic readiness task.
    // A new metamodel concept must be assigned to the task that teaches and
    // produces it, otherwise the coverage matrix is technically green while
    // the methodology is operationally incomplete.
  }
  return assignment;
}

export const ROLES = {
  cim: [
    {
      id: "product-owner",
      name: "Product Owner",
      responsibilities: ["Product goal, ordered backlog, value decisions, acceptance"],
    },
    {
      id: "delivery-lead",
      name: "Delivery Lead",
      responsibilities: ["Team coordination, dependencies, flow metrics, escalation, retrospectives"],
    },
    {
      id: "domain-expert",
      name: "Domain Expert",
      responsibilities: ["Business meaning, rules, examples, language, and scenario validation"],
    },
    {
      id: "business-modeler",
      name: "Business Modeler",
      responsibilities: ["Intent, domain, behavior, process, transformation contracts"],
    },
    {
      id: "requirements-engineer",
      name: "Requirements Engineer",
      responsibilities: ["Requirements, acceptance criteria, governance constraints"],
    },
    {
      id: "security-engineer",
      name: "Security & Privacy Engineer",
      responsibilities: ["Threats, privacy, compliance, risk acceptance, and control evidence"],
    },
    {
      id: "process-reviewer",
      name: "Process Reviewer",
      responsibilities: ["EVL gate approval, readiness sign-off"],
    },
    {
      id: "method-engineer",
      name: "Method Engineer",
      responsibilities: ["Maintains methodology when metamodels change"],
    },
  ],
  pim: [
    {
      id: "product-owner",
      name: "Product Owner",
      responsibilities: ["Product goal, ordered backlog, value decisions, acceptance"],
    },
    {
      id: "delivery-lead",
      name: "Delivery Lead",
      responsibilities: ["Team coordination, dependencies, flow metrics, escalation, retrospectives"],
    },
    {
      id: "domain-expert",
      name: "Domain Expert",
      responsibilities: ["Validates that architecture still realizes business scenarios"],
    },
    {
      id: "solution-architect",
      name: "Solution Architect",
      responsibilities: ["Service boundaries, contracts, integration, policies, readiness"],
    },
    {
      id: "process-reviewer",
      name: "Process Reviewer",
      responsibilities: ["EVL gate approval, readiness sign-off"],
    },
    {
      id: "quality-engineer",
      name: "Quality Engineer",
      responsibilities: ["Contract, integration, quality-attribute, and evidence strategy"],
    },
    {
      id: "security-engineer",
      name: "Security Engineer",
      responsibilities: ["Identity, authorization, data protection, and security review"],
    },
    {
      id: "cloud-platform-engineer",
      name: "Cloud Platform Engineer",
      responsibilities: ["Provider constraints, deployability feedback, and platform handoff"],
    },
    {
      id: "method-engineer",
      name: "Method Engineer",
      responsibilities: ["Maintains methodology when metamodels change"],
    },
  ],
  psm: [
    {
      id: "product-owner",
      name: "Product Owner",
      responsibilities: ["Value, release scope, acceptance, and risk decisions"],
    },
    {
      id: "delivery-lead",
      name: "Delivery Lead",
      responsibilities: ["Team coordination, dependencies, flow metrics, escalation, retrospectives"],
    },
    {
      id: "solution-architect",
      name: "Solution Architect",
      responsibilities: ["PIM intent, provider-independent trade-offs, and architecture consistency"],
    },
    {
      id: "cloud-platform-engineer",
      name: "Cloud Platform Engineer",
      responsibilities: ["AWS resources, IAM, networking, observability, deployment posture"],
    },
    {
      id: "process-reviewer",
      name: "Process Reviewer",
      responsibilities: ["EVL gate approval, readiness sign-off"],
    },
    {
      id: "quality-engineer",
      name: "Quality Engineer",
      responsibilities: ["Infrastructure, contract, deployment, and operational verification evidence"],
    },
    {
      id: "security-engineer",
      name: "Security Engineer",
      responsibilities: ["IAM, network, secrets, encryption, threat findings, and exception control"],
    },
    {
      id: "service-owner",
      name: "Service Owner / SRE",
      responsibilities: ["SLOs, observability, incident readiness, cost, recovery, and handover"],
    },
    {
      id: "release-engineer",
      name: "Release Engineer",
      responsibilities: ["Versioning, promotion, approvals, rollback, and release evidence"],
    },
    {
      id: "method-engineer",
      name: "Method Engineer",
      responsibilities: ["Maintains methodology when metamodels change"],
    },
  ],
};
