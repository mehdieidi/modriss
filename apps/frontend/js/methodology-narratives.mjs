/** Plain-language phase enrichments keyed by process phase id. */
const KERNEL_TYPES = new Set([
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
]);

export const PHASE_NARRATIVES = {
  "cim.p0.model-context": {
    summary:
      "Set the root CIMModel shell: domain name, business scope, organization, and modeling metadata. This is the container for everything else.",
    why: "Every other element lives under this root. A clear scope prevents drift and anchors traceability.",
    relationships: ["CONTAINS", "ANNOTATES"],
  },
  "cim.p1.strategic-intent": {
    summary:
      "Capture why the system exists: BusinessGoals with success criteria, KPIs to measure them, and Stakeholders who care about outcomes.",
    why: "Goal-Question-Metric anchors all later requirements and readiness checks. Without intent, domain modeling lacks direction.",
    relationships: ["SUPPORTS", "TRACE", "CONSTRAINS"],
  },
  "cim.p2.actors-boundaries": {
    summary:
      "Identify who interacts with the system (Actors, Roles) and external systems at the boundary—before modeling behavior.",
    why: "Commands and policies reference actors. Defining boundaries early avoids anonymous behavior on the canvas.",
    relationships: ["TRIGGERED_BY", "DEPENDS_ON", "CONSTRAINS_ACTORS"],
  },
  "cim.p3.capability-landscape": {
    summary:
      "Map what the organization can do: BusinessCapabilities, their dependencies, and criticality—linked to goals.",
    why: "Capabilities frame ownership and later bounded-context assignment. They connect strategy to delivery.",
    relationships: ["SUPPORTS", "DEPENDS_ON", "CONTAINS"],
  },
  "cim.p4.ubiquitous-language": {
    summary:
      "Build a shared glossary (UbiquitousLanguageTerm) so everyone uses the same words for domain concepts.",
    why: "DDD practice: align vocabulary before entities and processes so names stay consistent across the model.",
    relationships: ["SUPPORTS", "TRACE"],
  },
  "cim.p5.information-taxonomy": {
    summary:
      "Classify and name the data your domain cares about before you model entities. Information items are the vocabulary of facts; classifications capture sensitivity and handling rules.",
    why: "Domain entities must reference an InformationItem for identity (EVL CIM-ENTITY-001). Skipping this phase produces validation errors and unclear data ownership.",
    relationships: ["CONTAINS", "DEPENDS_ON", "REFERENCED_INFORMATION"],
  },
  "cim.p6.domain-structure": {
    summary:
      "Shape the structural heart of the domain: entities that hold state, value objects for descriptive data, and relationships between concepts—including lifecycle and invariants.",
    why: "Entities need information items from phase 5. Relationships and invariants here constrain everything you model in behavior and process phases.",
    relationships: ["GENERALIZATION", "ASSOCIATION", "DEPENDENCY", "CONTAINS"],
  },
  "cim.p7.behavior-surface": {
    summary:
      "Capture what the system does: commands that change state, queries that read it, and events that announce what happened—linked to actors and capabilities.",
    why: "Aggregates and processes in later phases assign ownership of commands and events. Without behavior, bounded contexts and workflows stay empty.",
    relationships: ["TRIGGERED_BY", "EMITS_EVENTS", "AFFECTS", "GUARDS", "PAYLOAD"],
  },
  "cim.p8.aggregate-boundaries": {
    summary:
      "Define AggregateCandidate groupings with consistency expectations—which commands each aggregate handles and which events it emits.",
    why: "Aggregates enforce transactional boundaries. They require entities and behavior from prior phases.",
    relationships: ["CONTAINS", "HANDLED_BY", "EMITS_EVENTS"],
  },
  "cim.p9.process-decisions": {
    summary:
      "Model BusinessProcess flows, policies, and decision tables that orchestrate commands, events, and human steps.",
    why: "Processes stitch isolated behavior into end-to-end business flows and automation candidates.",
    relationships: ["TRANSITION", "TRIGGERS", "RESULTS_IN", "DECISION_TABLE"],
  },
  "cim.p10.bounded-context-synthesis": {
    summary:
      "Group capabilities, entities, CQRS elements, events, and policies into cohesive bounded contexts—after you have modeled what belongs together.",
    why: "BoundedContextCandidate references concrete elements. Creating contexts too early yields empty shells; synthesis belongs after domain and behavior exploration.",
    relationships: ["CONTAINS", "CONTAINS_COMMAND", "CONTAINS_EVENT", "SUPPORTS"],
  },
  "cim.p11.requirements-governance": {
    summary:
      "Backfill formal requirements, acceptance criteria, and governance constraints with trace links to goals, domain, and behavior you already modeled.",
    why: "Twin Peaks: requirements refine the model once structure exists. constrains links need real targets on the canvas.",
    relationships: ["CONSTRAINS", "SUPPORTS", "CONFLICTS_WITH", "REFINES", "TRACE"],
  },
  "cim.p12.transformation-contracts": {
    summary:
      "Record Risks, Assumptions, Hotspots, and the TransformationProfile before CIM→PIM—what the pipeline must respect.",
    why: "Transformation is not automatic truth. Explicit contracts let reviewers gate promotion to PIM.",
    relationships: ["TRACE", "CONSTRAINS", "DEPENDS_ON"],
  },
  "cim.p13.traceability-readiness": {
    summary:
      "Close the loop: TraceModel links across goals, requirements, and domain; ProductionReadinessAssessment signs off CIM completeness.",
    why: "EVL semantic validation and readiness findings block CIM→PIM until resolved.",
    relationships: ["TRACE", "SUPPORTS", "CONSTRAINS"],
  },
  "pim.p0.architecture-posture": {
    summary:
      "Establish PIMModel root with ArchitectureStyle and ImplementationProfile—your serverless posture before services.",
    why: "Posture drives default patterns for functions, integration, and policies in all later PIM phases.",
    relationships: ["CONTAINS", "DEPENDS_ON"],
  },
  "pim.p2.contracts-schemas": {
    summary:
      "Define Schema, EventType, and EventEnvelope contracts—the payloads APIs and events carry.",
    why: "Contracts decouple producers and consumers. They map CIM commands/events to platform-neutral shapes.",
    relationships: ["PAYLOAD", "CONSTRAINS", "TRACE"],
  },
  "pim.p3.data-architecture": {
    summary:
      "Model DataStore, ObjectStore, DataModel, access patterns, and change streams for persistent data.",
    why: "Functions and APIs need durable homes. Data architecture follows contracts and precedes compute wiring.",
    relationships: ["CONTAINS", "READS", "WRITES", "STREAMS"],
  },
  "pim.p5.api-surface": {
    summary:
      "Expose Api, ApiRoute, and ApiContract elements with error mappings to business errors.",
    why: "HTTP surface connects users and systems to functions. Routes must align with schemas from phase 2.",
    relationships: ["ROUTES_TO", "PAYLOAD", "CONSTRAINS"],
  },
  "pim.p6.integration-topology": {
    summary:
      "Wire async integration: EventChannel (queues, topics, buses), Flow types, routing rules, and subscriptions.",
    why: "Serverless systems are event-driven. Topology connects functions without tight coupling.",
    relationships: ["SUBSCRIBES", "PUBLISHES", "ROUTES", "TRIGGERS"],
  },
  "pim.p1.service-boundaries": {
    summary:
      "Draw serverless service boundaries aligned to CIM bounded contexts. Each service owns a deployable slice of functions, data, and APIs.",
    why: "Service boundaries drive everything downstream—schemas, stores, and integration channels attach to services.",
    relationships: ["CONTAINS", "DEPENDS_ON", "OWNERSHIP"],
  },
  "pim.p4.compute-units": {
    summary:
      "Define Lambda-style functions with contracts and triggers—one handler per command, query, or event reaction from CIM.",
    why: "Functions are the executable core; APIs and event flows route to them in later phases.",
    relationships: ["TRIGGERS", "ROUTES_TO", "SUBSCRIBES", "INVOKES"],
  },
  "psm.p8.compute": {
    summary:
      "Materialize AWS Lambda functions with code config, layers, event source mappings, and IAM permissions matching the PIM compute model.",
    why: "Compute binds PIM behavior to runnable infrastructure; integrations and API Gateway depend on deployed functions.",
    relationships: ["INTEGRATES", "TRIGGERS", "POLICY_ATTACHMENT"],
  },
};

const LEVEL_INTROS = {
  cim: "Computation-independent modeling: business intent, domain, behavior, and governance before any platform choices.",
  pim: "Platform-independent serverless architecture: services, contracts, data, integration, and policies.",
  psm: "AWS-specific deployment model: SAM stacks, Lambda, data stores, messaging, IAM, and observability.",
};

const VIEWPOINT_LABELS = {
  dashboard: "Overview dashboard",
  requirements: "Goals & requirements view",
  capability: "Capability map",
  domain: "Domain model view",
  process: "Business process view",
  actor: "Actor map",
  aggregate: "Aggregate boundaries",
  eventstorming: "Event storming board",
  decision: "Decision tables",
  governance: "Governance & compliance",
  traceability: "Traceability & readiness",
  services: "Service map",
  contracts: "Contracts & schemas",
  data: "Data architecture",
  compute: "Compute units",
  api: "API surface",
  integration: "Integration topology",
  workflow: "Workflow orchestration",
  security: "Security & identity",
  policies: "Architecture policies",
  config: "External & configuration",
  readiness: "Platform readiness",
  stack: "SAM stack",
  networking: "Networking",
  identity: "Identity (Cognito)",
  storage: "Durable storage",
  messaging: "Messaging (SQS/SNS)",
  events: "Event fabric (EventBridge)",
};

export function levelIntro(level) {
  return LEVEL_INTROS[level] || "";
}

export function viewpointLabel(viewpoint) {
  return VIEWPOINT_LABELS[viewpoint] || viewpoint || "Canvas view";
}

export function phaseNarrative(phase, level) {
  const custom = PHASE_NARRATIVES[phase?.id];
  const mainTask = (phase?.tasks || []).find((t) => t.id?.endsWith(".main")) || phase?.tasks?.[0];
  const focusTypes = mainTask?.paletteFocus || [];
  const steps = mainTask?.steps || [];
  const concepts = [...new Set(focusTypes)].filter(
    (name) => name && !KERNEL_TYPES.has(name) && !name.endsWith("Type"),
  );

  return {
    summary:
      custom?.summary ||
      (steps.length
        ? steps[0]
        : `In this phase you establish ${phase?.name?.toLowerCase() || "model elements"} for your ${level.toUpperCase()} model.`),
    why:
      custom?.why ||
      (steps.length > 1
        ? steps.slice(1).join(" ")
        : `This phase follows the metamodel dependency order so later phases can reference what you create here.`),
    relationships: custom?.relationships || ["CONTAINS", "DEPENDS_ON", "TRACE"],
    concepts: concepts.slice(0, 24),
    steps: mainTask?.steps || [],
    entryCriteria: mainTask?.entryCriteria || phase?.tasks?.[0]?.entryCriteria || [],
    exitCriteria: mainTask?.exitCriteria || phase?.tasks?.[0]?.exitCriteria || [],
    validationRules: mainTask?.validationRules || [],
    viewpoint: phase?.viewpoint,
    viewpointLabel: viewpointLabel(phase?.viewpoint),
    duration: phase?.durationEstimate || "",
    role: formatRole(phase?.primaryRole),
  };
}

function formatRole(roleId) {
  const labels = {
    "business-modeler": "Business Modeler",
    "requirements-engineer": "Requirements Engineer",
    "solution-architect": "Solution Architect",
    "cloud-platform-engineer": "Cloud Platform Engineer",
    "process-reviewer": "Process Reviewer",
  };
  return labels[roleId] || roleId || "";
}
