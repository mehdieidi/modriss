/**
 * SPEM work product / artifact kind catalog per modeling level.
 * Tasks reference artifactIds; workProducts bind artifact kinds to metamodel types.
 */

/** @type {Record<string, import('./process-types.mjs').ArtifactKind[]>} */
export const ARTIFACT_KINDS = {
  cim: [
    { id: "cim-artifact.model-root", name: "CIM Model Root", description: "CIMModel container and program metadata" },
    { id: "cim-artifact.strategic-intent", name: "Strategic Intent Package", description: "Goals, KPIs, and stakeholder map" },
    { id: "cim-artifact.participation-model", name: "Participation Model", description: "Actors, roles, and external systems" },
    { id: "cim-artifact.capability-map", name: "Capability Map", description: "Business capabilities and dependencies" },
    { id: "cim-artifact.glossary", name: "Ubiquitous Language Glossary", description: "Domain term definitions" },
    { id: "cim-artifact.information-taxonomy", name: "Information Taxonomy", description: "Data classifications and information items" },
    { id: "cim-artifact.domain-structure", name: "Domain Structure Model", description: "Entities, value objects, relationships, invariants" },
    { id: "cim-artifact.behavior-surface", name: "CQRS Behavior Surface", description: "Commands, queries, events, conditions" },
    { id: "cim-artifact.aggregate-model", name: "Aggregate Boundary Model", description: "Aggregate candidates and consistency rules" },
    { id: "cim-artifact.process-model", name: "Business Process Model", description: "Processes, steps, transitions" },
    { id: "cim-artifact.decision-model", name: "Decision & Policy Model", description: "Policies and decision tables" },
    { id: "cim-artifact.context-map", name: "Bounded Context Map", description: "Context boundary assignments" },
    { id: "cim-artifact.requirements-package", name: "Requirements Package", description: "Functional and non-functional requirements" },
    { id: "cim-artifact.governance-package", name: "Governance Constraint Package", description: "Security, privacy, compliance constraints" },
    { id: "cim-artifact.transformation-contract", name: "Transformation Contract", description: "Risks, assumptions, hotspots, profile" },
    { id: "cim-artifact.trace-readiness", name: "Trace & Readiness Record", description: "TraceModel and production readiness assessment" },
  ],
  pim: [
    { id: "pim-artifact.architecture-posture", name: "Architecture Posture", description: "PIMModel root and implementation profile" },
    { id: "pim-artifact.service-map", name: "Service Boundary Map", description: "Serverless services and memberships" },
    { id: "pim-artifact.contract-catalog", name: "Contract Catalog", description: "Schemas, event types, envelopes" },
    { id: "pim-artifact.data-architecture", name: "Data Architecture", description: "Stores, models, access patterns" },
    { id: "pim-artifact.compute-catalog", name: "Compute Catalog", description: "Functions, contracts, triggers" },
    { id: "pim-artifact.api-catalog", name: "API Catalog", description: "APIs, routes, error mappings" },
    { id: "pim-artifact.integration-topology", name: "Integration Topology", description: "Channels, flows, schedules" },
    { id: "pim-artifact.workflow-model", name: "Workflow Model", description: "Workflows, human tasks, escalation" },
    { id: "pim-artifact.security-model", name: "Security Model", description: "Identity, principals, permissions" },
    { id: "pim-artifact.policy-catalog", name: "Architecture Policy Catalog", description: "Operational and compliance policies" },
    { id: "pim-artifact.config-package", name: "Configuration Package", description: "Environments, secrets, external adapters" },
    { id: "pim-artifact.platform-readiness", name: "Platform Readiness Record", description: "Mapping assessment, trace, readiness" },
  ],
  psm: [
    { id: "psm-artifact.deployment-strategy", name: "Deployment Strategy", description: "Account, region, naming, tagging policies" },
    { id: "psm-artifact.stack-scaffold", name: "SAM Stack Scaffold", description: "Stacks, globals, CFN parameters" },
    { id: "psm-artifact.security-baseline", name: "Security Baseline", description: "IAM, KMS, secrets, SSM" },
    { id: "psm-artifact.network-identity", name: "Network & Identity", description: "VPC, Cognito resources" },
    { id: "psm-artifact.storage-layer", name: "Durable Storage Layer", description: "DynamoDB and S3 resources" },
    { id: "psm-artifact.messaging-layer", name: "Messaging Layer", description: "SQS and SNS resources" },
    { id: "psm-artifact.event-fabric", name: "Event Fabric", description: "EventBridge buses, rules, pipes" },
    { id: "psm-artifact.compute-layer", name: "Lambda Compute Layer", description: "Functions, mappings, permissions" },
    { id: "psm-artifact.api-layer", name: "API Gateway Layer", description: "HTTP/REST/WebSocket APIs" },
    { id: "psm-artifact.workflow-observability", name: "Workflow & Observability", description: "Step Functions and CloudWatch" },
    { id: "psm-artifact.integration-views", name: "Integration View Catalog", description: "Cross-resource relationship views" },
    { id: "psm-artifact.deployment-readiness", name: "Deployment Readiness Record", description: "Trace and readiness closure" },
  ],
};
