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
  "Decision",
]);

/**
 * @param {import('./process-types.mjs').ProcessPhaseSpec[]} phases
 * @param {string[]} concepts
 * @returns {Map<string, string>}
 */
export function assignConceptsToTasks(phases, concepts) {
  const entries = collectAllTasks(phases);
  const explicit = new Map();
  const readinessTaskId = entries.find((e) => e.task.readinessTypes)?.task.id;
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
    if (KERNEL_TYPES.has(concept) && rootTaskId) {
      assignment.set(concept, rootTaskId);
      continue;
    }
    if (SHARED_READINESS_TYPES.has(concept) && readinessTaskId) {
      assignment.set(concept, readinessTaskId);
      continue;
    }
    if (SHARED_ENUMS.has(concept) && rootTaskId) {
      assignment.set(concept, rootTaskId);
      continue;
    }
    if (readinessTaskId) {
      assignment.set(concept, readinessTaskId);
    }
  }
  return assignment;
}

export const ROLES = {
  cim: [
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
      id: "method-engineer",
      name: "Method Engineer",
      responsibilities: ["Maintains methodology when metamodels change"],
    },
  ],
  psm: [
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
      id: "method-engineer",
      name: "Method Engineer",
      responsibilities: ["Maintains methodology when metamodels change"],
    },
  ],
};
