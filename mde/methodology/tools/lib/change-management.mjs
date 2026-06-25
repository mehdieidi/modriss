/**
 * Change-management workflows for evolving models after phases are complete.
 * Covers element add/change/remove, trace ripple, and cross-level re-transform.
 */

/** @type {Record<string, { workflows: import('./process-types.mjs').ChangeWorkflow[] }>} */
export const CHANGE_MANAGEMENT = {
  cim: {
    workflows: [
      {
        id: "cim.change.scope-expand",
        name: "Expand Increment Scope",
        trigger: "Stakeholders add a new capability or value stream to the current increment.",
        steps: [
          "Re-enter the engine Plan Increment step; update increment scope annotation on CIMModel.",
          "Replay Discover stage (actors, capabilities, language) for the new slice.",
          "Continue Explore → Construct → Converge for new elements only; merge into existing model.",
          "Re-run traceability and readiness before CIM→PIM.",
        ],
        impactedStages: ["cim.s2.discover", "cim.s3.explore", "cim.s4.construct", "cim.s5.converge"],
      },
      {
        id: "cim.change.element-add",
        name: "Add Domain Element",
        trigger: "A new concept (entity, command, event, process step) is needed outside the current phase.",
        steps: [
          "Identify the metamodel phase that owns the concept (use methodology palette phase hints).",
          "Navigate to that phase; create the element with correct containment and references.",
          "Run Impact Analysis: find trace links, constrains, and bounded-context memberships affected.",
          "Update dependent elements in later phases; mark downstream tasks incomplete if EVL rules break.",
        ],
        impactedStages: ["cim.s3.explore", "cim.s4.construct", "cim.s5.converge"],
      },
      {
        id: "cim.change.element-modify",
        name: "Modify Existing Element",
        trigger: "Rename, restructure, or change attributes/relationships on an existing element.",
        steps: [
          "Record ManualDecision or Annotation describing the change rationale.",
          "Use Impact Analysis to list trace links, requirements constrains, and process transitions affected.",
          "Apply Twin Peaks loop if requirements and structure diverge.",
          "Re-validate EVL rules for all impacted phases before marking tasks complete.",
        ],
        impactedStages: ["cim.s3.explore", "cim.s4.construct", "cim.s5.converge"],
      },
      {
        id: "cim.change.element-remove",
        name: "Retire Domain Element",
        trigger: "A concept is deprecated or removed from scope.",
        steps: [
          "Find incoming references (commands, events, processes, requirements, contexts).",
          "Remove or redirect references; update bounded-context memberships.",
          "Delete the element; run EVL to confirm zero orphan references.",
          "Update TransformationProfile assumptions if CIM→PIM mapping changes.",
        ],
        impactedStages: ["cim.s4.construct", "cim.s5.converge"],
      },
      {
        id: "cim.change.post-transform",
        name: "Post-Transform CIM Change",
        trigger: "CIM must change after PIM or PSM already exists for this project.",
        steps: [
          "Freeze downstream models or branch increment.",
          "Apply CIM change with full impact analysis.",
          "Re-run CIM EVL and readiness gate.",
          "Re-execute CIM→PIM ETL; reconcile PIM trace links and manual decisions.",
          "Propagate to PSM and artifacts per end-to-end change workflow.",
        ],
        impactedStages: ["cim.s5.converge"],
        crossLevel: true,
      },
    ],
  },
  pim: {
    workflows: [
      {
        id: "pim.change.service-rescope",
        name: "Rescope Service Boundary",
        trigger: "ServerlessService membership or ownership must change.",
        steps: [
          "Adjust ServiceElementMembership and ownership on affected deployable elements.",
          "Replay Capability Core stage for moved elements (contracts, data, compute, API).",
          "Re-run integration topology and assurance phases for cross-service flows.",
          "Update platform mapping assessment before PIM→PSM.",
        ],
        impactedStages: ["pim.s1.posture", "pim.s2.capability-core", "pim.s3.integration", "pim.s4.assurance"],
      },
      {
        id: "pim.change.contract-evolve",
        name: "Evolve Contract Version",
        trigger: "Schema or event type needs breaking or compatible change.",
        steps: [
          "Set SchemaCompatibility and version annotations on Schema/EventType.",
          "Update all FunctionContract, ApiContract, and route payload references.",
          "Apply data migration assumptions in TransformationProfile if needed.",
          "Re-validate integration subscriptions and access patterns.",
        ],
        impactedStages: ["pim.s2.capability-core", "pim.s3.integration"],
      },
      {
        id: "pim.change.policy-attach",
        name: "Attach or Change Policy",
        trigger: "New resilience, observability, or compliance policy applies to existing elements.",
        steps: [
          "Create ArchitecturePolicy and PolicySetting targets.",
          "Verify PolicyTarget elements exist and are correctly linked.",
          "Run policy EVL suite; replay assurance stage if attachments span services.",
        ],
        impactedStages: ["pim.s4.assurance"],
      },
      {
        id: "pim.change.post-transform",
        name: "Post-Transform PIM Change",
        trigger: "PIM must change after PSM or artifacts exist.",
        steps: [
          "Apply PIM change with impact analysis on trace links to CIM and PSM.",
          "Re-run PIM EVL and readiness.",
          "Re-execute PIM→PSM ETL; review platform mapping deltas.",
          "Regenerate M2T artifacts; run deployment dry-run.",
        ],
        impactedStages: ["pim.s5.readiness"],
        crossLevel: true,
      },
    ],
  },
  psm: {
    workflows: [
      {
        id: "psm.change.resource-resize",
        name: "Resize or Reconfigure Resource",
        trigger: "AWS resource properties change (capacity, runtime, VPC, encryption).",
        steps: [
          "Locate resource in the phase that owns its eClass (storage, compute, API, etc.).",
          "Apply NativeProperty or typed attribute changes.",
          "Check dependsOn, IAM policies, and integration views for ripple effects.",
          "Update relationship views; re-run PSM EVL before M2T.",
        ],
        impactedStages: ["psm.s3.data-events", "psm.s4.compute-expose", "psm.s5.orchestrate"],
      },
      {
        id: "psm.change.iam-tighten",
        name: "Tighten IAM Posture",
        trigger: "Security review requires least-privilege IAM changes.",
        steps: [
          "Return to Security Baseline phase; adjust IamRole, IamPolicy, IamStatement.",
          "Replay compute and API phases for LambdaPermission and resource policies.",
          "Regenerate integration views; verify no overly permissive statements remain.",
        ],
        impactedStages: ["psm.s1.foundation", "psm.s4.compute-expose"],
      },
      {
        id: "psm.change.import-adopt",
        name: "Adopt Existing AWS Resource",
        trigger: "Brownfield resource imported via ResourceImport instead of net-new creation.",
        steps: [
          "Add ResourceImport with stable physical ID.",
          "Link to PIM trace source; avoid duplicate logical resources.",
          "Refresh integration views to reference imported resource ARNs.",
        ],
        impactedStages: ["psm.s6.readiness"],
      },
      {
        id: "psm.change.post-m2t",
        name: "Post-M2T PSM Change",
        trigger: "PSM changes after artifacts were generated or deployed.",
        steps: [
          "Apply PSM model change at owning phase.",
          "Re-run PSM EVL and integration views.",
          "Regenerate M2T artifacts; diff against prior generation.",
          "Run CI validation and staged deployment.",
        ],
        impactedStages: ["psm.s6.readiness"],
        crossLevel: true,
      },
    ],
  },
};

export const CROSS_LEVEL_CHANGE_WORKFLOW = {
  id: "e2e.change.propagate",
  name: "Cross-Level Change Propagation",
  trigger: "A model change at any level must propagate through CIM → PIM → PSM → artifacts.",
  steps: [
    "Complete level-local change workflow and EVL gate for the source level.",
    "Run Impact Analysis on TraceModel: list downstream TraceLinks with reduced confidence.",
    "Re-run ETL for affected level pair (cim-to-pim, pim-to-awspsm) with merge strategy.",
    "Replay refinement phases on target level for impacted increment slice only.",
    "Regenerate M2T artifacts; run end-to-end validation suite.",
    "Record ManualDecision for human resolution items; update readiness assessments.",
  ],
};
