/**
 * SPEM guidance elements, method principles and phase-level guidelines.
 */

/** @type {Record<string, import('./process-types.mjs').Guideline[]>} */
export const PROCESS_GUIDELINES = {
  cim: [
    {
      id: "cim.guid.mda-intent",
      name: "Computation-independent intent",
      appliesTo: "process",
      text: "CIM captures business meaning without platform or implementation choices. Defer technology decisions to PIM/PSM.",
    },
    {
      id: "cim.guid.increment-cycle",
      name: "Thin capability slices",
      appliesTo: "cim.ph1",
      text: "Run CIM as an empirical increment cycle: select one valuable capability slice, model only enough breadth to satisfy the slice definition of done, review, then adapt the backlog.",
    },
    {
      id: "cim.guid.gqm",
      name: "Goal-Question-Metric (Basili)",
      appliesTo: "cim.ph1",
      text: "Anchor every modeling increment with measurable business goals before structural or behavioral elements.",
    },
    {
      id: "cim.guid.ddd-language",
      name: "Ubiquitous language first (Evans)",
      appliesTo: "cim.ph2",
      text: "Agree domain vocabulary before modeling entities. Renaming later is expensive. Use glossary tasks early.",
    },
    {
      id: "cim.guid.information-before-entity",
      name: "Information before entity (CIM-ENTITY-001)",
      appliesTo: "cim.ph3",
      text: "Create InformationItem elements before DomainEntity. Primary identity must reference an information item.",
    },
    {
      id: "cim.guid.event-storming",
      name: "Event storming surface (Brandolini)",
      appliesTo: "cim.ph3",
      text: "Model commands, queries, and events on a behavior surface linked to actors and capabilities before aggregates.",
    },
    {
      id: "cim.guid.twin-peaks",
      name: "Twin Peaks requirements (Nuseibeh)",
      appliesTo: "cim.ph5",
      text: "Backfill formal requirements after domain structure exists. Use engine rework loops when requirements expose gaps.",
    },
  ],
  pim: [
    {
      id: "pim.guid.serverless-boundary",
      name: "Service as deployable boundary",
      appliesTo: "process",
      text: "Align ServerlessService boundaries to CIM bounded contexts. One increment typically maps to one service slice.",
    },
    {
      id: "pim.guid.generated-is-draft",
      name: "Generated PIM is a draft",
      appliesTo: "pim.ph1",
      text: "Treat CIM→PIM output as architectural scaffolding. Every generated service slice must pass through framing, refinement, readiness, and review before PIM→PSM.",
    },
    {
      id: "pim.guid.contracts-first",
      name: "Contracts decouple producers",
      appliesTo: "pim.ph2",
      text: "Define schemas and event types before wiring functions and routes. Contracts are the integration currency.",
    },
    {
      id: "pim.guid.data-access",
      name: "Access patterns drive stores",
      appliesTo: "pim.ph2",
      text: "Model read/write access patterns from CIM queries/commands before choosing store topology.",
    },
    {
      id: "pim.guid.policy-as-code",
      name: "Policy attachment discipline",
      appliesTo: "pim.ph5",
      text: "Attach resilience, observability, and compliance policies to concrete PolicyTarget elements, not free-floating rules.",
    },
    {
      id: "pim.guid.readiness-in-cycle",
      name: "Readiness is part of delivery",
      appliesTo: "pim.ph6",
      text: "Platform mapping and EVL validation are not after-the-fact audits. They are the review gate of each service-slice cycle.",
    },
  ],
  psm: [
    {
      id: "psm.guid.sam-containment",
      name: "SAM stack containment",
      appliesTo: "process",
      text: "Most AWS resources live under SamStack. Establish stack scaffolding before resource provisioning tasks.",
    },
    {
      id: "psm.guid.generated-is-draft",
      name: "Generated PSM is a draft",
      appliesTo: "psm.ph1",
      text: "Treat PIM→PSM output as AWS scaffolding. Refine IAM, networking, resource settings, integration views, and readiness before M2T generation.",
    },
    {
      id: "psm.guid.least-privilege",
      name: "Least-privilege IAM",
      appliesTo: "psm.ph1",
      text: "Security baseline before compute. Lambda permissions and resource policies reference roles from the baseline.",
    },
    {
      id: "psm.guid.integration-views",
      name: "Integration views for traceability",
      appliesTo: "psm.ph6",
      text: "Relationship views denormalize cross-resource wiring for M2T and human review, create them before M2T generation.",
    },
    {
      id: "psm.guid.deployable-slice",
      name: "Deployable slice discipline",
      appliesTo: "psm.ph6",
      text: "A PSM increment is done only when AWS wiring, least-privilege posture, observability, traceability, and artifact-generation readiness are reviewed together.",
    },
  ],
  "end-to-end": [
    {
      id: "e2e.guid.incremental-vertical",
      name: "Thin vertical increments",
      appliesTo: "process",
      text: "Deliver one capability slice through CIM → PIM → PSM → artifact readiness per engine revolution. The engine repeats inside release, operations, and retirement lifecycle governance; avoid big-bang modeling.",
    },
    {
      id: "e2e.guid.human-in-loop",
      name: "Human-in-the-loop transforms",
      appliesTo: "process",
      text: "ETL and M2T are assistive. ManualDecision and readiness gates block promotion when automation is uncertain.",
    },
    {
      id: "e2e.guid.empirical-control",
      name: "Empirical process control",
      appliesTo: "process",
      text: "Plan a small vertical slice, inspect executable model evidence at each gate, and adapt the backlog after artifact review.",
    },
  ],
};
