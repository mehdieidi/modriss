/** Plain-language phase enrichments keyed by process phase id. */
export const PHASE_NARRATIVES = {
  "cim.ph1": {
    summary:
      "Frame the current capability slice, establish or refresh the CIM program container, and anchor modeling in measurable intent.",
    why: "Modless project created or prior CIM increment selected for evolution",
    relationships: ["CONTAINS", "DEPENDS_ON", "TRACE"],
  },
  "cim.ph2": {
    summary:
      "Map who participates in the domain, what the organization can do, and shared vocabulary.",
    why: "Increment Framing complete",
    relationships: ["CONTAINS", "DEPENDS_ON", "TRACE"],
  },
  "cim.ph3": {
    summary:
      "Explore information, structure, and behavior using Twin Peaks — iterate until CQRS surface is coherent.",
    why: "Context Discovery complete for slice",
    relationships: ["CONTAINS", "DEPENDS_ON", "TRACE"],
  },
  "cim.ph4": {
    summary:
      "Synthesize transactional boundaries, orchestration, and bounded contexts from explored domain.",
    why: "Domain Exploration coherent for slice",
    relationships: ["CONTAINS", "DEPENDS_ON", "TRACE"],
  },
  "cim.ph5": {
    summary:
      "Backfill requirements, record transformation contracts, close traceability and EVL gate.",
    why: "Domain Synthesis complete for slice",
    relationships: ["CONTAINS", "DEPENDS_ON", "TRACE"],
  },
  "pim.ph1": {
    summary:
      "Frame the current service slice, establish or refresh PIM posture, and align serverless boundaries to CIM intent.",
    why: "CIM transform complete, prior PIM increment selected, or greenfield PIM",
    relationships: ["CONTAINS", "DEPENDS_ON", "TRACE"],
  },
  "pim.ph2": {
    summary: "Define API/event contracts and persistent data architecture for the increment slice.",
    why: "Architecture & Slice Framing complete",
    relationships: ["CONTAINS", "DEPENDS_ON", "TRACE"],
  },
  "pim.ph3": {
    summary: "Define compute units and expose them through a coherent API surface.",
    why: "Contracts & Data complete for slice",
    relationships: ["CONTAINS", "DEPENDS_ON", "TRACE"],
  },
  "pim.ph4": {
    summary: "Wire async integration topology and long-running workflow orchestration.",
    why: "Compute & Exposure complete for slice",
    relationships: ["CONTAINS", "DEPENDS_ON", "TRACE"],
  },
  "pim.ph5": {
    summary:
      "Apply security, operational policies, and environment configuration across the slice.",
    why: "Integration & Orchestration complete for slice",
    relationships: ["CONTAINS", "DEPENDS_ON", "TRACE"],
  },
  "pim.ph6": {
    summary: "Assess platform capability mapping, close traceability, and pass PIM EVL gate.",
    why: "Assurance & Configuration complete for slice",
    relationships: ["CONTAINS", "DEPENDS_ON", "TRACE"],
  },
  "psm.ph1": {
    summary:
      "Frame the current deployable slice and establish or refresh AWS account, stack, and security foundations.",
    why: "PIM transform complete, prior PSM increment selected, or greenfield PSM",
    relationships: ["CONTAINS", "DEPENDS_ON", "TRACE"],
  },
  "psm.ph2": {
    summary: "Configure VPC networking and Cognito identity resources aligned to PIM auth model.",
    why: "Deployment & Slice Framing complete",
    relationships: ["CONTAINS", "DEPENDS_ON", "TRACE"],
  },
  "psm.ph3": {
    summary:
      "Provision durable storage and messaging resources matching PIM data and event channels.",
    why: "Network & Identity complete",
    relationships: ["CONTAINS", "DEPENDS_ON", "TRACE"],
  },
  "psm.ph4": {
    summary: "Configure EventBridge fabric and deploy Lambda compute matching PIM functions.",
    why: "Storage & Messaging complete for slice",
    relationships: ["CONTAINS", "DEPENDS_ON", "TRACE"],
  },
  "psm.ph5": {
    summary: "Configure API Gateway exposure and Step Functions workflows with observability.",
    why: "Event Fabric & Compute complete for slice",
    relationships: ["CONTAINS", "DEPENDS_ON", "TRACE"],
  },
  "psm.ph6": {
    summary: "Create integration relationship views, close traceability, and pass PSM EVL gate.",
    why: "API & Orchestration complete for slice",
    relationships: ["CONTAINS", "DEPENDS_ON", "TRACE"],
  },
};

const LEVEL_INTROS = {
  cim: "CIM process engine advances one capability slice per cycle: frame → discover → explore → synthesize → converge/review → repeat.",
  pim: "PIM process engine advances one service slice per cycle: frame → contracts/data → compute/API → integrate → assure → readiness/review → repeat.",
  psm: "PSM process engine advances one deployable AWS slice per cycle: frame → network/identity → data/messaging → events/compute → API/workflow → readiness/review → repeat.",
};

const STAGE_NARRATIVES = {
  "cim.ph1": {
    summary:
      "Frame the capability slice, verify the model root, and anchor the cycle in measurable goals.",
  },
  "cim.s2.discover": {
    summary:
      "Map who and what: actors, capabilities, and ubiquitous language for the selected slice.",
  },
  "cim.s3.explore": {
    summary: "Twin Peaks exploration loop: taxonomy → structure → behavior until CQRS is coherent.",
  },
  "cim.s4.construct": {
    summary: "Synthesize aggregates, processes, and bounded contexts from explored domain.",
  },
  "cim.s5.converge": {
    summary:
      "Backfill requirements, transformation contracts, traceability, and EVL readiness gate.",
  },
  "pim.ph1": {
    summary:
      "Frame the service slice, verify architecture posture, and align service boundaries to CIM traces.",
  },
  "pim.s2.capability-core": {
    summary:
      "Iterate contracts, data, compute, and API until the increment slice is internally runnable.",
  },
  "pim.s3.integration": {
    summary: "Wire async channels, flows, and workflow orchestration across services.",
  },
  "pim.s4.assurance": {
    summary: "Apply security, policies, external adapters, and environment configuration.",
  },
  "pim.s5.readiness": {
    summary: "Platform mapping assessment, trace closure, and PIM EVL gate.",
  },
  "psm.ph1": {
    summary:
      "Frame the deployable slice, verify AWS account strategy, SAM scaffolding, and security baseline.",
  },
  "psm.s2.platform": {
    summary: "Networking and Cognito identity aligned to PIM auth model.",
  },
  "psm.s3.data-events": {
    summary: "DynamoDB, S3, messaging, and EventBridge — iterate with compute wiring.",
  },
  "psm.s4.compute-expose": {
    summary: "Lambda and API Gateway exposure matching PIM surface.",
  },
  "psm.s5.orchestrate": {
    summary: "Step Functions and CloudWatch observability.",
  },
  "psm.s6.readiness": {
    summary: "Integration relationship views and PSM EVL gate before M2T.",
  },
};

export function stageNarrative(stage) {
  const custom = STAGE_NARRATIVES[stage?.id];
  return {
    summary: custom?.summary || stage?.objective || stage?.name || "",
  };
}

const VIEWPOINT_LABELS = {
  dashboard: "Overview dashboard",
  requirements: "Goals & requirements view",
  capability: "Capability",
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

function collectLeafTasks(phase) {
  function walkStages(stages) {
    const out = [];
    for (const stage of stages || []) {
      if (stage.subStages?.length) {
        out.push(...walkStages(stage.subStages));
      } else if (stage.tasks?.length) {
        out.push(...stage.tasks);
      }
    }
    return out;
  }
  return walkStages(phase?.stages);
}

export function phaseNarrative(phase, level, { kernelTypes = new Set() } = {}) {
  const custom = PHASE_NARRATIVES[phase?.id];
  const tasks = collectLeafTasks(phase);
  const mainTask = tasks[0];
  const focusTypes = mainTask?.paletteFocus || [];
  const steps = mainTask?.steps || [];
  const concepts = [...new Set(focusTypes)].filter(
    (name) => name && !kernelTypes.has(name) && !name.endsWith("Type"),
  );

  return {
    summary:
      custom?.summary ||
      phase?.objective ||
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
    entryCriteria: phase?.entryCriteria || mainTask?.entryCriteria || [],
    exitCriteria: phase?.exitCriteria || mainTask?.exitCriteria || [],
    validationRules: mainTask?.validationRules || [],
    viewpoint: mainTask?.viewpoint || phase?.viewpoint,
    viewpointLabel: viewpointLabel(mainTask?.viewpoint || phase?.viewpoint),
    duration: phase?.durationEstimate || mainTask?.durationEstimate || "",
    role: formatRole(phase?.primaryRole || mainTask?.primaryRole),
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
