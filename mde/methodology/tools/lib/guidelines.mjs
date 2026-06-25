/**
 * SPEM guidance elements — method principles and phase-level guidelines.
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
      id: "cim.guid.gqm",
      name: "Goal-Question-Metric (Basili)",
      appliesTo: "cim.ph1",
      text: "Anchor every modeling increment with measurable business goals before structural or behavioral elements.",
    },
    {
      id: "cim.guid.ddd-language",
      name: "Ubiquitous language first (Evans)",
      appliesTo: "cim.ph2",
      text: "Agree domain vocabulary before modeling entities. Renaming later is expensive — use glossary tasks early.",
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
      text: "Attach resilience, observability, and compliance policies to concrete PolicyTarget elements — not free-floating rules.",
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
      id: "psm.guid.least-privilege",
      name: "Least-privilege IAM",
      appliesTo: "psm.ph1",
      text: "Security baseline before compute. Lambda permissions and resource policies reference roles from the baseline.",
    },
    {
      id: "psm.guid.integration-views",
      name: "Integration views for traceability",
      appliesTo: "psm.ph7",
      text: "Relationship views denormalize cross-resource wiring for M2T and human review — create them before M2T generation.",
    },
  ],
  "end-to-end": [
    {
      id: "e2e.guid.incremental-vertical",
      name: "Thin vertical increments",
      appliesTo: "process",
      text: "Deliver one capability slice through CIM → PIM → PSM → artifacts per engine revolution. Avoid big-bang modeling.",
    },
    {
      id: "e2e.guid.human-in-loop",
      name: "Human-in-the-loop transforms",
      appliesTo: "process",
      text: "ETL and M2T are assistive. ManualDecision and readiness gates block promotion when automation is uncertain.",
    },
  ],
};
