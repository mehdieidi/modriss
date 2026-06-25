/**
 * Process engine — iterative kernel that revolves through phases/stages and delivers increments.
 */

/** @type {Record<string, import('./process-types.mjs').ProcessEngineSpec>} */
export const PROCESS_ENGINES = {
  cim: {
    id: "modless.cim.engine",
    displayName: "CIM Modeling Engine",
    description:
      "Revolves through Context Discovery → Domain Exploration → Domain Synthesis → Convergence per capability slice.",
    incrementUnit: "capability-slice",
    deliverable: {
      name: "CIM increment slice",
      description: "Validated domain, behavior, and governance elements for one bounded slice.",
    },
    onboarding: {
      name: "Program establishment",
      description: "Once per program before the first engine cycle.",
      phaseIds: ["cim.ph1"],
    },
    cycle: [
      {
        id: "cim.engine.plan",
        name: "Plan Increment",
        type: "task",
        primaryRole: "business-modeler",
        steps: [
          "Select capability or bounded-context slice from backlog.",
          "Agree roles, scope, and exit criteria for this revolution.",
        ],
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
        reworkLoopIds: [
          "cim.loop.language-refine",
          "cim.loop.twin-peaks-explore",
        ],
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
        name: "Converge & Gate",
        type: "phase",
        phaseIds: ["cim.ph5"],
        reworkLoopIds: ["cim.loop.requirements-backfill", "cim.loop.readiness-rework"],
      },
      {
        id: "cim.engine.deliver",
        name: "Deliver Increment",
        type: "deliverable",
        steps: ["Merge slice into program CIM; update increment backlog."],
      },
      {
        id: "cim.engine.retrospect",
        name: "Retrospective",
        type: "task",
        primaryRole: "process-reviewer",
        steps: ["Review cycle; adjust slice sizing for next revolution."],
      },
    ],
    loop: {
      fromStepId: "cim.engine.retrospect",
      toStepId: "cim.engine.plan",
      condition: "increment-backlog-remaining",
      guidance: "Revolve the CIM engine for the next capability slice.",
    },
  },
  pim: {
    id: "modless.pim.engine",
    displayName: "PIM Modeling Engine",
    description:
      "Revolves through contracts/data → compute/API → integration → assurance per service slice.",
    incrementUnit: "service-slice",
    deliverable: {
      name: "PIM increment slice",
      description: "Runnable serverless architecture for one service boundary.",
    },
    onboarding: {
      name: "Architecture establishment",
      phaseIds: ["pim.ph1"],
    },
    cycle: [
      {
        id: "pim.engine.plan",
        name: "Plan Service Slice",
        type: "task",
        primaryRole: "solution-architect",
        steps: ["Select service slice; map to CIM bounded contexts and traces."],
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
        name: "Platform Readiness Gate",
        type: "phase",
        phaseIds: ["pim.ph6"],
        validationGate: "pim-semantic-validation",
        reworkLoopIds: ["pim.loop.readiness-rework"],
      },
      {
        id: "pim.engine.deliver",
        name: "Deliver Increment",
        type: "deliverable",
        steps: ["Merge service slice into program PIM."],
      },
      {
        id: "pim.engine.retrospect",
        name: "Retrospective",
        type: "task",
        primaryRole: "process-reviewer",
        steps: ["Review patterns; resize next service slice."],
      },
    ],
    loop: {
      fromStepId: "pim.engine.retrospect",
      toStepId: "pim.engine.plan",
      condition: "service-slices-remaining",
      guidance: "Revolve the PIM engine for the next service slice.",
    },
  },
  psm: {
    id: "modless.psm.engine",
    displayName: "AWS PSM Modeling Engine",
    description:
      "Revolves through storage/messaging → events/compute → API/workflow → readiness per deployable slice.",
    incrementUnit: "deployable-slice",
    deliverable: {
      name: "PSM increment slice",
      description: "Wired AWS resources and integration views for one deployable slice.",
    },
    onboarding: {
      name: "Deployment foundation",
      phaseIds: ["psm.ph1", "psm.ph2"],
    },
    cycle: [
      {
        id: "psm.engine.plan",
        name: "Plan Deployable Slice",
        type: "task",
        primaryRole: "cloud-platform-engineer",
        steps: ["Select PIM service slice to materialize on AWS."],
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
        name: "Integration Views & Gate",
        type: "phase",
        phaseIds: ["psm.ph6"],
        validationGate: "psm-semantic-validation",
        reworkLoopIds: ["psm.loop.readiness-rework"],
      },
      {
        id: "psm.engine.deliver",
        name: "Deliver Increment",
        type: "deliverable",
        steps: ["Merge resources into program PSM; confirm integration views."],
      },
      {
        id: "psm.engine.retrospect",
        name: "Retrospective",
        type: "task",
        primaryRole: "process-reviewer",
        steps: ["Review IAM and wiring patterns for next slice."],
      },
    ],
    loop: {
      fromStepId: "psm.engine.retrospect",
      toStepId: "psm.engine.plan",
      condition: "deployable-slices-remaining",
      guidance: "Revolve the PSM engine for the next deployable slice.",
    },
  },
  "end-to-end": {
    id: "modless.end-to-end.engine",
    displayName: "End-to-End MDE Engine",
    description: "One vertical CIM → PIM → PSM → artifacts revolution per capability increment.",
    incrementUnit: "capability-slice",
    deliverable: {
      name: "Deployable increment",
      description: "Models at all levels plus generated artifacts for one slice.",
    },
    cycle: [
      { id: "e2e.p0.increment-planning", name: "Increment Planning", type: "orchestration", primaryRole: "business-modeler" },
      { id: "e2e.p1.cim-modeling", name: "CIM Engine", type: "child-process", childProcessId: "modless.cim.modeling" },
      { id: "e2e.p2.cim-to-pim", name: "CIM → PIM ETL", type: "transform", transform: "cim-to-pim", primaryRole: "solution-architect" },
      { id: "e2e.p3.pim-refinement", name: "PIM Engine", type: "child-process", childProcessId: "modless.pim.modeling" },
      { id: "e2e.p4.pim-to-psm", name: "PIM → PSM ETL", type: "transform", transform: "pim-to-awspsm", primaryRole: "cloud-platform-engineer" },
      { id: "e2e.p5.psm-refinement", name: "PSM Engine", type: "child-process", childProcessId: "modless.psm.modeling" },
      { id: "e2e.p6.m2t-generation", name: "M2T Generation", type: "transform", transform: "awspsm-to-artifacts", primaryRole: "cloud-platform-engineer" },
      { id: "e2e.p7.artifact-completion", name: "Increment Closure", type: "deliverable", primaryRole: "process-reviewer" },
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
