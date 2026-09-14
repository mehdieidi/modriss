/**
 * Process engine, iterative kernel that revolves through phases/stages and delivers increments.
 */

/** @type {Record<string, import('./process-types.mjs').ProcessEngineSpec>} */
export const PROCESS_ENGINES = {
  cim: {
    id: "modriss.cim.engine",
    displayName: "CIM Modeling Engine",
    description:
      "Revolves through increment framing → context discovery → domain exploration → synthesis → readiness/review per capability slice.",
    incrementUnit: "capability-slice",
    deliverable: {
      name: "CIM increment slice",
      description: "Validated domain, behavior, and governance elements for one bounded slice.",
    },
    cycle: [
      {
        id: "cim.engine.frame",
        name: "Frame Increment",
        type: "phase",
        primaryRole: "business-modeler",
        phaseIds: ["cim.ph1"],
      },
      {
        id: "cim.engine.discover",
        name: "Discover Context",
        type: "phase",
        phaseIds: ["cim.ph2"],
        reworkLoopIds: [],
      },
      {
        id: "cim.engine.explore",
        name: "Explore Domain",
        type: "phase",
        phaseIds: ["cim.ph3"],
        reworkLoopIds: ["cim.loop.language-refine", "cim.loop.twin-peaks-explore"],
      },
      {
        id: "cim.engine.synthesize",
        name: "Synthesize Domain",
        type: "phase",
        phaseIds: ["cim.ph4"],
        reworkLoopIds: [
          "cim.loop.aggregate-rework",
          "cim.loop.process-gap",
          "cim.loop.context-resynth",
        ],
      },
      {
        id: "cim.engine.converge",
        name: "Converge, Review & Adapt",
        type: "phase",
        phaseIds: ["cim.ph5"],
        reworkLoopIds: ["cim.loop.requirements-backfill", "cim.loop.readiness-rework"],
      },
    ],
    loop: {
      fromStepId: "cim.engine.converge",
      toStepId: "cim.engine.frame",
      condition: "increment-backlog-remaining",
      guidance:
        "After the phase-embedded review/adapt stage, revolve the CIM engine for the next capability slice.",
    },
  },
  pim: {
    id: "modriss.pim.engine",
    displayName: "PIM Modeling Engine",
    description:
      "Revolves through service-slice framing → contracts/data → compute/API → integration → assurance → readiness per service slice.",
    incrementUnit: "service-slice",
    deliverable: {
      name: "PIM increment slice",
      description: "Runnable serverless architecture for one service boundary.",
    },
    cycle: [
      {
        id: "pim.engine.frame",
        name: "Frame Service Slice",
        type: "phase",
        primaryRole: "solution-architect",
        phaseIds: ["pim.ph1"],
      },
      {
        id: "pim.engine.contracts-data",
        name: "Contracts & Data",
        type: "phase",
        phaseIds: ["pim.ph2"],
        reworkLoopIds: [],
      },
      {
        id: "pim.engine.compute-expose",
        name: "Compute & API",
        type: "phase",
        phaseIds: ["pim.ph3"],
        reworkLoopIds: ["pim.loop.contract-gap", "pim.loop.data-access", "pim.loop.api-contract"],
      },
      {
        id: "pim.engine.integrate",
        name: "Integrate & Orchestrate",
        type: "phase",
        phaseIds: ["pim.ph4"],
        reworkLoopIds: ["pim.loop.integration-topology"],
      },
      {
        id: "pim.engine.assure",
        name: "Assure & Configure",
        type: "phase",
        phaseIds: ["pim.ph5"],
        reworkLoopIds: ["pim.loop.security-gap", "pim.loop.policy-ripple"],
      },
      {
        id: "pim.engine.gate",
        name: "Readiness, Review & Adapt",
        type: "phase",
        phaseIds: ["pim.ph6"],
        validationGate: "pim-semantic-validation",
        reworkLoopIds: ["pim.loop.readiness-rework"],
      },
    ],
    loop: {
      fromStepId: "pim.engine.gate",
      toStepId: "pim.engine.frame",
      condition: "service-slices-remaining",
      guidance:
        "After the phase-embedded review/adapt stage, revolve the PIM engine for the next service slice.",
    },
  },
  psm: {
    id: "modriss.psm.engine",
    displayName: "AWS PSM Modeling Engine",
    description:
      "Revolves through deployable-slice framing → network/identity → storage/messaging → events/compute → API/workflow → readiness per deployable slice.",
    incrementUnit: "deployable-slice",
    deliverable: {
      name: "PSM increment slice",
      description: "Wired AWS resources and integration views for one deployable slice.",
    },
    cycle: [
      {
        id: "psm.engine.frame",
        name: "Frame Deployable Slice",
        type: "phase",
        primaryRole: "cloud-platform-engineer",
        phaseIds: ["psm.ph1"],
      },
      {
        id: "psm.engine.network-identity",
        name: "Network & Identity",
        type: "phase",
        phaseIds: ["psm.ph2"],
        reworkLoopIds: ["psm.loop.iam-baseline"],
      },
      {
        id: "psm.engine.storage-messaging",
        name: "Storage & Messaging",
        type: "phase",
        phaseIds: ["psm.ph3"],
        reworkLoopIds: [],
      },
      {
        id: "psm.engine.events-compute",
        name: "Events & Compute",
        type: "phase",
        phaseIds: ["psm.ph4"],
        reworkLoopIds: ["psm.loop.storage-compute", "psm.loop.event-fabric"],
      },
      {
        id: "psm.engine.api-workflow",
        name: "API & Workflow",
        type: "phase",
        phaseIds: ["psm.ph5"],
        reworkLoopIds: ["psm.loop.api-integration", "psm.loop.workflow-obs"],
      },
      {
        id: "psm.engine.readiness",
        name: "Readiness, Review & Adapt",
        type: "phase",
        phaseIds: ["psm.ph6"],
        validationGate: "psm-semantic-validation",
        reworkLoopIds: ["psm.loop.readiness-rework"],
      },
    ],
    loop: {
      fromStepId: "psm.engine.readiness",
      toStepId: "psm.engine.frame",
      condition: "deployable-slices-remaining",
      guidance:
        "After the phase-embedded review/adapt stage, revolve the PSM engine for the next deployable slice.",
    },
  },
  "end-to-end": {
    id: "modriss.end-to-end.engine",
    displayName: "End-to-End MDE Engine",
    description: "One vertical CIM → PIM → PSM → artifacts revolution per capability increment.",
    incrementUnit: "capability-slice",
    deliverable: {
      name: "Deployable increment",
      description: "Models at all levels plus generated artifacts for one slice.",
    },
    cycle: [
      {
        id: "e2e.p0.increment-planning",
        name: "Increment Planning",
        type: "orchestration",
        primaryRole: "business-modeler",
      },
      {
        id: "e2e.p1.cim-modeling",
        name: "CIM Engine",
        type: "child-process",
        childProcessId: "modriss.cim.modeling",
      },
      {
        id: "e2e.p2.cim-to-pim",
        name: "CIM → PIM ETL",
        type: "transform",
        transform: "cim-to-pim",
        primaryRole: "solution-architect",
      },
      {
        id: "e2e.p3.pim-refinement",
        name: "PIM Engine",
        type: "child-process",
        childProcessId: "modriss.pim.modeling",
      },
      {
        id: "e2e.p4.pim-to-psm",
        name: "PIM → PSM ETL",
        type: "transform",
        transform: "pim-to-awspsm",
        primaryRole: "cloud-platform-engineer",
      },
      {
        id: "e2e.p5.psm-refinement",
        name: "PSM Engine",
        type: "child-process",
        childProcessId: "modriss.psm.modeling",
      },
      {
        id: "e2e.p6.m2t-generation",
        name: "M2T Generation",
        type: "transform",
        transform: "awspsm-to-artifacts",
        primaryRole: "cloud-platform-engineer",
      },
      {
        id: "e2e.p7.artifact-completion",
        name: "Increment Closure",
        type: "deliverable",
        primaryRole: "process-reviewer",
      },
    ],
    loop: {
      fromStepId: "e2e.p7.artifact-completion",
      toStepId: "e2e.p0.increment-planning",
      condition: "increment-backlog-remaining",
      guidance: "Revolve end-to-end engine for next capability increment.",
    },
    reworkLoopIds: [
      "e2e.loop.pim-feedback-cim",
      "e2e.loop.psm-feedback-pim",
      "e2e.loop.artifact-feedback-psm",
    ],
  },
};

export function enrichEngineWithReworkLoops(level, engine, levelLoops, e2eLoops = []) {
  const loopById = new Map([...levelLoops, ...e2eLoops].map((l) => [l.id, l]));
  const referencedIds = new Set(engine.reworkLoopIds || []);
  for (const step of engine.cycle) {
    for (const id of step.reworkLoopIds || []) {
      referencedIds.add(id);
    }
  }
  const reworkLoops = [...referencedIds].map((id) => loopById.get(id)).filter(Boolean);
  return { ...engine, reworkLoops };
}
