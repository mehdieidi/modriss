import { applyDeclaredTaskInputs } from "./process-inputs.mjs";

/**
 * Full-lifecycle situational method that coordinates the three modeling
 * processes and the generated-artifact readiness process.
 *
 * This is an orchestration process: CIM, PIM, PSM, and artifact work retain
 * their own gates. The end-to-end process adds product, team, release,
 * operations, and retirement coordination around those child processes.
 */

const task = (id, name, primaryRole, steps, artifactIds = [], options = {}) => ({
  id,
  name,
  primaryRole,
  steps,
  artifactIds,
  entryCriteria: options.entryCriteria || [],
  exitCriteria: options.exitCriteria || [`${name} evidence is recorded`],
  validationRules: options.validationRules || [],
  progressEvidence: options.progressEvidence || `${name} decision, evidence links, owner, and status are recorded`,
  durationEstimate: options.durationEstimate,
  childProcessId: options.childProcessId,
  transform: options.transform,
});

const stage = (id, name, objective, primaryRole, tasks, options = {}) => ({
  id,
  name,
  objective,
  primaryRole,
  iterative: options.iterative === true,
  tasks,
});

export const END_TO_END_ARTIFACT_KINDS = [
  { id: "e2e-artifact.product-charter", name: "Product and System Charter", description: "Product goal, outcome hypothesis, scope boundaries, constraints, and success measures." },
  { id: "e2e-artifact.method-profile", name: "Situational Method Profile", description: "Tailored lifecycle, roles, work products, gates, evidence rules, and metrics for the project context." },
  { id: "e2e-artifact.team-topology", name: "Team Topology and Dependency Map", description: "Team ownership, interfaces, dependency board, decision rights, and coordination cadence." },
  { id: "e2e-artifact.increment-record", name: "Increment Record", description: "Slice goal, scope, acceptance evidence, model revisions, transformation runs, and retrospective results." },
  { id: "e2e-artifact.release-record", name: "Release Record", description: "Release scope, exact model and artifact revisions, approvals, deployment evidence, rollback identity, and outcome." },
  { id: "e2e-artifact.operations-record", name: "Operations and Learning Record", description: "SLOs, incidents, changes, product outcomes, and method-improvement actions." },
  { id: "e2e-artifact.operational-work-item", name: "Service Work Item", description: "A production, maintenance, service, or improvement demand item with source, maintenance purpose where applicable, emergency-temporary status, class of service, severity, owner, service-level expectation, state, age, evidence, and disposition." },
  { id: "e2e-artifact.service-flow-system", name: "Kanban Service-Delivery Policy and Board", description: "The explicit Definition of Workflow and visible Kanban board: requested/ready/started/finished points, workflow states, WIP controls, classes of service, service-level expectations, replenishment and review cadences, capacity policy, and flow metrics." },
  { id: "e2e-artifact.retirement-record", name: "Retirement and Closure Record", description: "Retirement decision, migration/data disposition, decommission evidence, and retained knowledge." },
];

export const END_TO_END_ROLES = [
  { id: "product-owner", name: "Product Owner", responsibilities: ["Owns product outcomes, priority, release scope, and acceptance decisions"] },
  { id: "delivery-lead", name: "Delivery Lead", responsibilities: ["Maintains the integrated process run, dependency board, risks, cadence, and impediment escalation"] },
  { id: "business-modeler", name: "Business Modeler", responsibilities: ["Leads CIM discovery and domain-model increments"] },
  { id: "requirements-engineer", name: "Requirements Engineer", responsibilities: ["Maintains requirements, acceptance criteria, and change impact evidence"] },
  { id: "domain-expert", name: "Domain Expert", responsibilities: ["Validates business language, rules, and operational fit"] },
  { id: "solution-architect", name: "Solution Architect", responsibilities: ["Leads PIM architecture, contracts, integration, and cross-level decisions"] },
  { id: "cloud-platform-engineer", name: "Cloud Platform Engineer", responsibilities: ["Leads AWS PSM, generation, environments, and platform automation"] },
  { id: "quality-engineer", name: "Quality Engineer", responsibilities: ["Owns verification strategy, evidence quality, and quality risks"] },
  { id: "security-engineer", name: "Security Engineer", responsibilities: ["Owns security, privacy, threat, and exception evidence"] },
  { id: "release-engineer", name: "Release Engineer", responsibilities: ["Owns release candidates, promotion, rollback, and deployment records"] },
  { id: "service-owner", name: "Service Owner", responsibilities: ["Accepts operational readiness, SLOs, support ownership, and service outcomes"] },
  { id: "process-reviewer", name: "Process Reviewer", responsibilities: ["Reviews gates, evidence, decisions, and process improvement"] },
  { id: "method-engineer", name: "Method Engineer", responsibilities: ["Tailors the development process and maintains its alignment with the modeling framework as metamodels change"] },
];

const END_TO_END_LIFECYCLE_ACTIVITIES_SOURCE = [
  {
    id: "e2e.ph0",
    name: "Initiate, Tailor & Organize",
    order: 0,
    objective: "Establish the product/system purpose, process profile, team topology, quality baseline, and release strategy before modeling begins.",
    primaryRole: "product-owner",
    entryCriteria: ["A problem, opportunity, or mandated change has an accountable sponsor"],
    exitCriteria: ["The method profile is approved", "Teams, ownership, dependencies, and decision rights are explicit", "The first increment has a testable outcome hypothesis"],
    inEngine: false,
    tailoringNote: "Tailor by risk, novelty, regulatory exposure, team distribution, and architecture volatility; retain evidence and gate obligations even when activities are combined.",
    stages: [
      stage("e2e.ph0.st1", "Product and System Intent", "Turn the opportunity into an outcome-oriented product/system charter and release hypothesis.", "product-owner", [
        task("e2e.ph0.st1.t1", "Define product outcomes and success measures", "product-owner", ["State the user or mission problem and desired outcomes.", "Define measurable product, operational, security, and quality outcomes.", "Record assumptions, constraints, non-goals, and the first release hypothesis."], ["e2e-artifact.product-charter"], { exitCriteria: ["Outcome measures and non-goals are accepted by stakeholders"], validationRules: ["Every initial scope item is connected to an outcome or mandatory constraint"] }),
        task("e2e.ph0.st1.t2", "Establish the initial release and increment hypothesis", "requirements-engineer", ["Identify the smallest useful vertical capability slice.", "Define acceptance signals and the evidence needed to call it usable.", "Record unresolved assumptions as owned decisions rather than hidden risks."], ["e2e-artifact.product-charter", "e2e-artifact.increment-record"], { validationRules: ["The first slice crosses the required lifecycle boundary and has observable acceptance evidence"] }),
      ]),
      stage("e2e.ph0.st2", "Situational Process Tailoring", "Select and tailor reusable method content and process activities to the project context without removing essential control objectives.", "method-engineer", [
        task("e2e.ph0.st2.t1", "Assess context and process-tailoring risks", "method-engineer", ["Assess criticality, regulatory obligations, novelty, uncertainty, team distribution, system size, and delivery cadence.", "Select the required CIM, PIM, PSM, artifact, and lifecycle activities.", "Record excluded, combined, or delegated activities with rationale and compensating evidence."], ["e2e-artifact.method-profile"], { validationRules: ["Every tailoring decision names its context, consequence, owner, and review point"] }),
        task("e2e.ph0.st2.t2", "Define the tailored Definition of Ready and Done", "process-reviewer", ["Define entry and exit evidence for model slices, transformations, release candidates, and operations.", "Define which findings are blocking and how time-bound risk acceptance works.", "Publish the profile version used by the process run."], ["e2e-artifact.method-profile"], { validationRules: ["A task cannot be accepted solely because activity occurred; required evidence and gate criteria are explicit"] }),
      ]),
      stage("e2e.ph0.st3", "Team Topology & Coordination", "Make ownership and coordination explicit for multiple teams working on one integrated model and product.", "delivery-lead", [
        task("e2e.ph0.st3.t1", "Define team ownership and interfaces", "delivery-lead", ["Partition work by bounded capability, service, platform concern, or lifecycle responsibility.", "Assign model ownership, repository ownership, decision rights, and backup owners.", "Define interface contracts and the shared dependency board."], ["e2e-artifact.team-topology"], { validationRules: ["Every owned model scope has one accountable owner and a named integration path"] }),
        task("e2e.ph0.st3.t2", "Set coordination cadence and escalation paths", "delivery-lead", ["Set local team syncs, cross-team integration reviews, increment reviews, release reviews, and retrospectives.", "Define escalation thresholds for aging dependencies, blocking findings, and conflicting model ownership.", "Define how concurrent model edits are merged, reviewed, and reconciled."], ["e2e-artifact.team-topology", "e2e-artifact.method-profile"], { validationRules: ["Coordination events produce decisions, dependency status, and evidence links"] }),
      ]),
      stage("e2e.ph0.st4", "Quality, Security & Operations Baseline", "Set cross-cutting quality, security, operational, and release constraints before design detail accumulates.", "quality-engineer", [
        task("e2e.ph0.st4.t1", "Define quality and security control objectives", "security-engineer", ["Identify privacy, threat, compliance, resilience, performance, accessibility, and audit obligations.", "Map control objectives to model evidence, generated artifact evidence, and explicit human decisions.", "Set severity and blocking policies."], ["e2e-artifact.method-profile"], { validationRules: ["Critical controls have an accountable owner and verification evidence path"] }),
        task("e2e.ph0.st4.t2", "Define operational and release strategy", "service-owner", ["Define environments, support ownership, SLO hypotheses, observability expectations, rollback posture, and release cadence.", "Define the Kanban service-delivery system: board states and start/finish points, WIP controls, classes of service, service-level expectations, capacity allocation, replenishment and review cadences, and expedite/reconciliation policy.", "Identify data migration, compatibility, and progressive-delivery constraints and record the initial release decision policy."], ["e2e-artifact.product-charter", "e2e-artifact.method-profile", "e2e-artifact.service-flow-system"], { validationRules: ["The release strategy names promotion, rollback, and post-deployment validation evidence", "The operational policy prevents unknown future maintenance demand from being represented as pre-scheduled tasks"] }),
      ]),
    ],
  },
  {
    id: "e2e.ph1",
    name: "Iterative-Incremental Model-Driven Delivery",
    order: 1,
    objective: "Deliver usable vertical increments by coordinating CIM, PIM, PSM, transformation, generation, and artifact-readiness child processes.",
    primaryRole: "delivery-lead",
    entryCriteria: ["A tailored method profile and team topology exist", "An increment goal and acceptance hypothesis are ready"],
    exitCriteria: ["The increment is accepted, deferred with explicit rationale, or returned for bounded rework", "The increment record contains model, transformation, artifact, and acceptance evidence"],
    inEngine: true,
    tailoringNote: "The engine is the repeatable kernel. Teams may work concurrently inside a cycle, but promotion remains evidence-based and downstream work cannot silently accept unresolved upstream decisions.",
    stages: [
      stage("e2e.p0.increment-planning", "Frame Increment", "Select a thin, valuable, testable capability slice and establish its evidence plan.", "product-owner", [
        task("e2e.p0.increment-planning.t1", "Plan the vertical increment", "product-owner", ["Select scope from the ordered product backlog.", "Define outcome, acceptance criteria, dependencies, risks, and expected model/artifact evidence.", "Assign scope items to owning teams and establish the increment ledger."], ["e2e-artifact.increment-record"], { exitCriteria: ["Increment scope, owners, acceptance evidence, and dependencies are agreed"], validationRules: ["The slice is small enough to inspect end-to-end and large enough to demonstrate a user or operational outcome"] }),
      ]),
      stage("e2e.p1.cim-modeling", "CIM Child Process", "Refine business intent and domain behavior for the selected slice.", "business-modeler", [
        task("e2e.p1.cim-modeling.t1", "Run the CIM increment", "business-modeler", ["Execute the CIM process for the selected capability slice.", "Record semantic decisions, assumptions, trace links, readiness findings, and accepted CIM revision.", "Return unresolved cross-team or product decisions to the integrated decision register."], ["e2e-artifact.increment-record"], { childProcessId: "modriss.cim.modeling", exitCriteria: ["CIM gate evidence is accepted or explicitly reworked"], validationRules: ["Assistant-applied model changes pass structural Ecore/EMF conformance; semantic validation remains an explicit user/model validation workflow"] }),
      ]),
      stage("e2e.p2.cim-to-pim", "CIM → PIM Transformation", "Transform the accepted CIM revision into PIM scaffolding while preserving traceability and surfacing manual decisions.", "solution-architect", [
        task("e2e.p2.cim-to-pim.t1", "Execute and inspect CIM-to-PIM transformation", "solution-architect", ["Pin the source CIM revision and transformation profile.", "Run the transformation and inspect report, trace links, assumptions, and manual decisions.", "Accept, reject, or route each material decision and record the target PIM revision."], ["e2e-artifact.increment-record"], { transform: "cim-to-pim", validationRules: ["Every generated target scope item is traceable to a source or explicit transformation decision"] }),
      ]),
      stage("e2e.p3.pim-refinement", "PIM Child Process", "Refine service boundaries, contracts, data, behavior, integration, assurance, and platform intent.", "solution-architect", [
        task("e2e.p3.pim-refinement.t1", "Run the PIM increment", "solution-architect", ["Execute the PIM process for the transformed slice.", "Review contracts, data access, integration, policy, platform mapping, and operational assumptions.", "Record accepted PIM revision and open platform decisions."], ["e2e-artifact.increment-record"], { childProcessId: "modriss.pim.modeling", exitCriteria: ["PIM gate evidence is accepted or explicitly reworked"], validationRules: ["Generated PIM is treated as scaffolding until the solution architect accepts material decisions"] }),
      ]),
      stage("e2e.p4.pim-to-psm", "PIM → AWS PSM Transformation", "Map accepted platform-independent architecture to AWS-specific deployment intent.", "cloud-platform-engineer", [
        task("e2e.p4.pim-to-psm.t1", "Execute and inspect PIM-to-PSM transformation", "cloud-platform-engineer", ["Pin the source PIM revision and AWS mapping profile.", "Run the transformation and inspect resource mappings, assumptions, security implications, and trace links.", "Record accepted target PSM revision or route mapping gaps back to PIM."], ["e2e-artifact.increment-record"], { transform: "pim-to-awspsm", validationRules: ["Every deployable PIM scope item has an accepted AWS mapping or a documented exception"] }),
      ]),
      stage("e2e.p5.psm-refinement", "PSM Child Process", "Refine AWS resources, relationships, security, observability, and deployment readiness for the slice.", "cloud-platform-engineer", [
        task("e2e.p5.psm-refinement.t1", "Run the PSM increment", "cloud-platform-engineer", ["Execute the AWS PSM process for the transformed slice.", "Review resource relationships, IAM, networking, data, eventing, APIs, workflows, and operational views.", "Record accepted PSM revision and unresolved deployment risks."], ["e2e-artifact.increment-record"], { childProcessId: "modriss.psm.modeling", exitCriteria: ["PSM gate evidence is accepted or explicitly reworked"], validationRules: ["AWS platform choices are traceable to accepted PIM intent or explicit platform decisions"] }),
      ]),
      stage("e2e.p6.m2t-generation", "Model-to-Text Generation", "Generate a reproducible artifact baseline from the accepted PSM revision.", "cloud-platform-engineer", [
        task("e2e.p6.m2t-generation.t1", "Generate and fingerprint the artifact baseline", "cloud-platform-engineer", ["Pin PSM revision, generator version, templates, and environment profile.", "Generate application, infrastructure, configuration, tests, and documentation artifacts.", "Record the generation manifest and route structural gaps to PSM or generator ownership."], ["e2e-artifact.increment-record"], { transform: "awspsm-to-artifacts", validationRules: ["The generated baseline is reproducible from recorded inputs and contains no secret material"] }),
      ]),
      stage("e2e.p7.artifact-completion", "Increment Readiness & Acceptance", "Verify the exact generated candidate, capture acceptance evidence, and decide whether the increment is usable, reworkable, or deferred.", "process-reviewer", [
        task("e2e.p7.artifact-completion.t1", "Run artifact readiness and accept the increment", "process-reviewer", ["Execute the artifact deployment-readiness process for the generated candidate.", "Review verification, security, rollback, operational, and acceptance evidence.", "Record increment outcome, open findings, deferred scope, and feedback loops for the next cycle."], ["e2e-artifact.increment-record"], { childProcessId: "modriss.artifact.deployment-readiness", exitCriteria: ["Increment is accepted, explicitly deferred, or returned through a named rework loop"], validationRules: ["No blocking finding is silently closed; risk acceptance is explicit, owned, time-bound, and traceable"] }),
      ]),
    ],
  },
  {
    id: "e2e.release-activities",
    name: "Release & Transition",
    order: 2,
    objective: "Assemble accepted increments into a controlled release, promote progressively, validate in the target environment, and hand over operational ownership.",
    primaryRole: "release-engineer",
    entryCriteria: ["Release scope is composed of accepted increments", "Artifact, security, quality, and operations evidence is available"],
    exitCriteria: ["Release outcome is recorded", "Service ownership and rollback posture are accepted", "Post-deployment validation is complete"],
    inEngine: false,
    stages: [
      stage("e2e.rel.a1", "Release Train Assembly", "Compose and verify a release from accepted increment records.", "release-engineer", [
        task("e2e.rel.a1.t1", "Assemble the release candidate", "release-engineer", ["Select accepted increments and compatible model/artifact revisions.", "Resolve cross-team dependency and compatibility checks.", "Create the release record with exact inputs and promotion sequence."], ["e2e-artifact.release-record"], { validationRules: ["Every release scope item maps to an accepted increment and exact artifact identity"] }),
        task("e2e.rel.a1.t2", "Review release evidence and go/no-go criteria", "process-reviewer", ["Inspect readiness assessments, validation results, security exceptions, rollback plan, operational runbooks, and approvals.", "Confirm open blockers are zero or explicitly accepted under the tailored method profile.", "Record the go/no-go decision and decision owners."], ["e2e-artifact.release-record"], { validationRules: ["Go/no-go is evidence-based and not inferred from task completion"] }),
      ]),
      stage("e2e.rel.a2", "Progressive Promotion", "Promote the release through environments with controlled observation and rollback readiness.", "release-engineer", [
        task("e2e.rel.a2.t1", "Deploy and validate progressively", "release-engineer", ["Deploy the exact candidate to the approved environment sequence.", "Run smoke, functional, security, data, observability, and compatibility checks.", "Compare observed outcomes with release acceptance signals and stop or roll back on threshold breach."], ["e2e-artifact.release-record"], { validationRules: ["Promotion evidence identifies candidate, environment, timestamp, operator, and observed result"] }),
        task("e2e.rel.a2.t2", "Complete handover and rollback rehearsal", "service-owner", ["Verify dashboards, alerts, runbooks, escalation paths, support ownership, and recovery access.", "Confirm rollback and data-recovery actions are usable for the release.", "Accept operational ownership or return the release to rework."], ["e2e-artifact.release-record", "e2e-artifact.operations-record"], { validationRules: ["Operational acceptance names an accountable service owner and recovery evidence"] }),
      ]),
      stage("e2e.rel.a3", "Release Review", "Inspect release outcomes and feed product, process, and architecture learning back into the backlog.", "product-owner", [
        task("e2e.rel.a3.t1", "Review release outcome and update roadmap", "product-owner", ["Compare product and operational outcomes with the release hypothesis.", "Accept outcomes, revise priorities, and create follow-up increments for gaps.", "Record decisions and changes to scope, measures, and assumptions."], ["e2e-artifact.release-record", "e2e-artifact.product-charter"], { validationRules: ["Outcome learning changes a backlog, product decision, or explicitly confirms the hypothesis"] }),
      ]),
    ],
  },
  {
    id: "e2e.ops",
    name: "Operations and Maintenance",
    order: null,
    objective: "Operate the service and manage production, maintenance, and improvement demand through a continuous Kanban service-delivery system while planned releases continue through the increment engine; restore service, reconcile authoritative sources, and learn.",
    primaryRole: "service-owner",
    entryCriteria: ["A service or operational capability has been released or is being maintained"],
    exitCriteria: ["Operational demand and flow decisions are visible", "Changes are traced through the appropriate lifecycle path", "Product and method learning is reviewed at the agreed cadence"],
    inEngine: false,
    stages: [
      stage("e2e.ops.a1", "Operate and Observe", "Use operational evidence to assess service health, user outcomes, and control effectiveness.", "service-owner", [
        task("e2e.ops.a1.t1", "Review SLOs, telemetry, and product outcomes", "service-owner", ["Inspect service levels, errors, cost, security signals, usage, provider events, and product outcome measures.", "Compare observations with the service and product hypotheses.", "Capture actionable demand as visible service work items rather than inserting hidden work into a release plan."], ["e2e-artifact.operations-record", "e2e-artifact.operational-work-item"], { iterative: true, validationRules: ["Operational decisions are based on identified evidence and thresholds"] }),
      ], { iterative: true }),
      stage("e2e.ops.a2", "Kanban Service-Delivery Management", "Visualize and manage service demand through an explicit Definition of Workflow, replenishment, pull, WIP controls, service expectations, and feedback cadences.", "delivery-lead", [
        task("e2e.ops.a2.t1", "Triage and make service demand ready", "service-owner", ["Record the demand source, impact, affected service, evidence, owner, and requested outcome on the Kanban board.", "Where the item is maintenance, classify its purpose as corrective, preventive, adaptive, additive, or perfective; record any emergency temporary-restoration status; assign a class of service separately according to the board policy.", "Refine the item until it meets the Ready policy and identify its likely disposition: operations-only response, the shortest safe model-driven change path, or a planned release backlog."], ["e2e-artifact.operational-work-item", "e2e-artifact.service-flow-system"], { iterative: true, validationRules: ["Maintenance purpose, emergency-temporary status, and class of service are recorded independently", "Every ready item has an accountable owner, expected outcome, evidence need, and visible disposition"] }),
        task("e2e.ops.a2.t2", "Replenish, pull, and manage Kanban flow", "delivery-lead", ["At the replenishment cadence, select eligible items into Ready according to capacity, risk, class-of-service policy, and value; pull a ready item only when the downstream WIP control permits.", "Use the board to manage work-item age, blocked work, service-level expectations, and reserved service capacity; an expedite item may displace other work only under the explicit expedite policy and must remain visible.", "Hold a daily flow review and periodic service-delivery review using WIP, throughput, cycle time, work-item age, SLE attainment, arrival rate, blocked time, and expedite frequency; adapt the Definition of Workflow through an explicit improvement decision."], ["e2e-artifact.operational-work-item", "e2e-artifact.service-flow-system", "e2e-artifact.operations-record"], { iterative: true, validationRules: ["A team does not start ordinary service work beyond its WIP control", "The expedite class has at most one active item unless the method profile records an exceptional incident-command policy", "Requested, Ready, In Progress, Verify, and Done states and their entry/exit policies are visible on the board"] }),
      ], { iterative: true }),
      stage("e2e.ops.a3", "Incident, Problem & Risk Response", "Restore service and address systemic causes while preserving traceability and learning.", "service-owner", [
        task("e2e.ops.a3.t1", "Manage incidents and emergency recovery", "service-owner", ["Triage impact, stabilize service, communicate status, and execute approved recovery actions.", "Record incident timeline, affected scope, decisions, temporary modifications, and evidence.", "Create permanent corrective, security, or change work for systemic causes and keep it visible after restoration."], ["e2e-artifact.operations-record", "e2e-artifact.operational-work-item"], { iterative: true, validationRules: ["Recovery and customer impact are recorded before closure", "An emergency temporary modification is not treated as the permanent corrective change"] }),
        task("e2e.ops.a3.t2", "Perform problem and risk learning", "quality-engineer", ["Analyze contributing causes across requirements, models, transformation, generation, deployment, and operation.", "Update controls, tests, model patterns, process guidance, or method profile as appropriate.", "Place permanent corrective work on the Kanban board or deliberately commit it to a planned release, and verify reconciliation after any emergency downstream fix."], ["e2e-artifact.operations-record", "e2e-artifact.method-profile", "e2e-artifact.operational-work-item"], { validationRules: ["The corrective action is routed to the earliest responsible source rather than only patched downstream"] }),
      ], { iterative: true }),
      stage("e2e.ops.a4", "Maintenance Change and Reconciliation", "Evolve the product through controlled impact analysis, authoritative-source change, release, and emergency-fix reconciliation.", "delivery-lead", [
        task("e2e.ops.a4.t1", "Assess and route a maintenance change", "delivery-lead", ["Classify the authoritative source as product/domain, architecture, platform, generator, artifact, or operations and confirm whether the item remains a bounded Kanban service item or is committed to a planned release.", "Use traces and dependency ownership to identify impacted downstream levels and teams.", "Send product-changing work to the Development and Delivery Process at the smallest affected phase or activity; after release, reconcile emergency downstream fixes into the authoritative source and close the service item only when evidence returns."], ["e2e-artifact.increment-record", "e2e-artifact.operations-record", "e2e-artifact.operational-work-item"], { iterative: true, validationRules: ["The change record identifies source revision, impacted levels, downstream evidence, acceptance decision, and service-work-item disposition"] }),
      ], { iterative: true }),
      stage("e2e.ops.a5", "Service and Process Improvement", "Improve the product and both connected processes using evidence from releases, service work, incidents, and dependencies.", "process-reviewer", [
        task("e2e.ops.a5.t1", "Inspect flow, quality, and coordination metrics", "process-reviewer", ["Review planned-delivery forecast, Kanban WIP, throughput, cycle time, work-item age, SLE attainment, demand arrival rate, expedite frequency, rework, trace coverage, blockers, dependency age, escaped defects, and product outcomes.", "Look for systemic queues, starvation between planned and service work, missing work products, invalid gates, and coordination failures.", "Approve bounded changes to the Definition of Workflow, capacity policy, DevOps interface, or method profile and record their expected effect."], ["e2e-artifact.operations-record", "e2e-artifact.method-profile", "e2e-artifact.service-flow-system"], { iterative: true, validationRules: ["Metrics lead to inspectable improvement experiments rather than individual performance rankings"] }),
      ], { iterative: true }),
    ],
  },
  {
    id: "e2e.ph2",
    name: "Retire, Migrate & Close",
    order: 2,
    objective: "Retire a product or service safely, preserve required knowledge and evidence, and close the lifecycle with explicit learning.",
    primaryRole: "service-owner",
    entryCriteria: ["A retirement decision is authorized", "Replacement, migration, or end-of-life obligations are known"],
    exitCriteria: ["Users, data, integrations, environments, and operational ownership are safely transitioned or closed", "Required records and lessons are retained", "Shared G8 closure evidence is accepted and no live release remains"],
    inEngine: false,
    stages: [
      stage("e2e.ph2.st1", "Retirement Decision & Plan", "Define why, when, and how the service will be retired while operations continue safely.", "product-owner", [
        task("e2e.ph2.st1.t1", "Approve retirement scope and plan", "product-owner", ["Confirm business, technical, legal, security, and operational reasons for retirement.", "Define replacement, user communication, compatibility, rollback, and exit criteria.", "Assign owners and schedule decision checkpoints."], ["e2e-artifact.retirement-record"], { validationRules: ["The plan identifies affected stakeholders, dependencies, and irreversible actions"] }),
      ]),
      stage("e2e.ph2.st2", "Migrate and Decommission", "Move or dispose of data, users, integrations, infrastructure, and operational obligations safely.", "cloud-platform-engineer", [
        task("e2e.ph2.st2.t1", "Execute migration and data disposition", "cloud-platform-engineer", ["Execute migration, archival, retention, deletion, or export according to approved policy.", "Validate completeness, confidentiality, integrity, and recoverability where required.", "Record final data and integration evidence."], ["e2e-artifact.retirement-record"], { validationRules: ["No data or integration is silently abandoned"] }),
        task("e2e.ph2.st2.t2", "Decommission service and access", "service-owner", ["Continue required operational coverage during migration, then disable traffic, scheduled work, credentials, access paths, alerts, and environments in the approved order.", "Verify replacement ownership and customer communication.", "Retain required source, model, trace, release, incident, and decision records."], ["e2e-artifact.retirement-record"], { validationRules: ["Decommission evidence covers runtime, data, access, cost, and support surfaces"] }),
      ]),
      stage("e2e.ph2.st3", "Closure & Organizational Learning", "Assemble Development and Delivery closure evidence, evaluate it with Operations and Maintenance evidence at shared G8, and feed reusable learning into future method profiles and product planning.", "process-reviewer", [
        task("e2e.ph2.st3.t1", "Complete closure review", "process-reviewer", ["Confirm retirement exit criteria and records are complete.", "Evaluate Development and Delivery evidence together with operational shutdown evidence at shared G8; do not model this synchronization as an activity-flow edge between the processes.", "Review product, architecture, operational, and process outcomes.", "Publish reusable patterns, risks, and process changes for future projects."], ["e2e-artifact.retirement-record", "e2e-artifact.method-profile"], { validationRules: ["Closure identifies retained evidence, unresolved obligations, and accountable custodians", "G8 is accepted only when no live release remains"] }),
      ]),
    ],
  },
];

// The product lifecycle uses Phase only for one-time, sequential macro periods.
// Release planning, construction iterations, qualification, deployment, and
// handover are repeatable Activities inside the single active-life phase.
const inceptionPhase = END_TO_END_LIFECYCLE_ACTIVITIES_SOURCE.find(
  activity => activity.id === "e2e.ph0",
);
const activeLifePhase = END_TO_END_LIFECYCLE_ACTIVITIES_SOURCE.find(
  activity => activity.id === "e2e.ph1",
);
const releaseActivityGroup = END_TO_END_LIFECYCLE_ACTIVITIES_SOURCE.find(
  activity => activity.name === "Release & Transition",
);
const retirementPhase = END_TO_END_LIFECYCLE_ACTIVITIES_SOURCE.find(
  activity => activity.id === "e2e.ph2",
);

inceptionPhase.name = "Inception, Tailoring & Organization";
activeLifePhase.name = "Active Product Construction & Evolution";
activeLifePhase.objective =
  "Construct and evolve the product through repeatable model-driven increments and releases while the independent Operations and Maintenance Process sustains accepted live baselines.";
activeLifePhase.entryCriteria = [
  "G1 authorizes the product, method profile, team topology, and first delivery hypothesis",
];
activeLifePhase.exitCriteria = [
  "Retirement is authorized and no development or release work remains in flight",
];
activeLifePhase.inEngine = false;
activeLifePhase.tailoringNote =
  "This Phase occurs once. Repeatable delivery iterations and release activities run inside it; they are not phases.";
activeLifePhase.stages = [...activeLifePhase.stages, ...releaseActivityGroup.stages];

END_TO_END_LIFECYCLE_ACTIVITIES_SOURCE.splice(
  END_TO_END_LIFECYCLE_ACTIVITIES_SOURCE.indexOf(releaseActivityGroup),
  1,
);

// Inputs are method-authoring decisions.  Empty arrays are intentional and
// mean that the task has no required WorkProductDefinition input.
const END_TO_END_INPUT_ARTIFACT_IDS = {
  "e2e.ph0.st1.t1": [],
  "e2e.ph0.st1.t2": ["e2e-artifact.product-charter"],
  "e2e.ph0.st2.t1": ["e2e-artifact.product-charter", "e2e-artifact.increment-record"],
  "e2e.ph0.st2.t2": [
    "e2e-artifact.method-profile",
    "e2e-artifact.product-charter",
    "e2e-artifact.increment-record",
  ],
  "e2e.ph0.st3.t1": ["e2e-artifact.method-profile", "e2e-artifact.product-charter"],
  "e2e.ph0.st3.t2": [
    "e2e-artifact.team-topology",
    "e2e-artifact.method-profile",
    "e2e-artifact.product-charter",
  ],
  "e2e.ph0.st4.t1": [
    "e2e-artifact.team-topology",
    "e2e-artifact.method-profile",
    "e2e-artifact.product-charter",
  ],
  "e2e.ph0.st4.t2": [
    "e2e-artifact.method-profile",
    "e2e-artifact.team-topology",
    "e2e-artifact.product-charter",
  ],
  "e2e.p0.increment-planning.t1": [
    "e2e-artifact.product-charter",
    "e2e-artifact.method-profile",
    "e2e-artifact.team-topology",
  ],
  "e2e.p1.cim-modeling.t1": [
    "e2e-artifact.increment-record",
    "e2e-artifact.product-charter",
    "e2e-artifact.method-profile",
    "e2e-artifact.team-topology",
  ],
  "e2e.p2.cim-to-pim.t1": [
    "e2e-artifact.increment-record",
    "e2e-artifact.method-profile",
    "e2e-artifact.product-charter",
  ],
  "e2e.p3.pim-refinement.t1": [
    "e2e-artifact.increment-record",
    "e2e-artifact.product-charter",
    "e2e-artifact.method-profile",
  ],
  "e2e.p4.pim-to-psm.t1": [
    "e2e-artifact.increment-record",
    "e2e-artifact.method-profile",
    "e2e-artifact.product-charter",
  ],
  "e2e.p5.psm-refinement.t1": [
    "e2e-artifact.increment-record",
    "e2e-artifact.method-profile",
    "e2e-artifact.product-charter",
  ],
  "e2e.p6.m2t-generation.t1": [
    "e2e-artifact.increment-record",
    "e2e-artifact.method-profile",
    "e2e-artifact.team-topology",
  ],
  "e2e.p7.artifact-completion.t1": [
    "e2e-artifact.increment-record",
    "e2e-artifact.method-profile",
    "e2e-artifact.product-charter",
  ],
  "e2e.rel.a1.t1": [
    "e2e-artifact.increment-record",
    "e2e-artifact.product-charter",
    "e2e-artifact.method-profile",
    "e2e-artifact.team-topology",
  ],
  "e2e.rel.a1.t2": [
    "e2e-artifact.release-record",
    "e2e-artifact.increment-record",
    "e2e-artifact.product-charter",
    "e2e-artifact.method-profile",
  ],
  "e2e.rel.a2.t1": [
    "e2e-artifact.release-record",
    "e2e-artifact.product-charter",
    "e2e-artifact.method-profile",
    "e2e-artifact.team-topology",
  ],
  "e2e.rel.a2.t2": [
    "e2e-artifact.release-record",
    "e2e-artifact.product-charter",
    "e2e-artifact.method-profile",
    "e2e-artifact.team-topology",
  ],
  "e2e.rel.a3.t1": [
    "e2e-artifact.release-record",
    "e2e-artifact.operations-record",
    "e2e-artifact.product-charter",
    "e2e-artifact.increment-record",
    "e2e-artifact.method-profile",
  ],
  "e2e.ops.a1.t1": [
    "e2e-artifact.release-record",
    "e2e-artifact.product-charter",
    "e2e-artifact.operations-record",
    "e2e-artifact.method-profile",
    "e2e-artifact.service-flow-system",
  ],
  "e2e.ops.a2.t1": [
    "e2e-artifact.operational-work-item",
    "e2e-artifact.operations-record",
    "e2e-artifact.method-profile",
    "e2e-artifact.service-flow-system",
  ],
  "e2e.ops.a2.t2": [
    "e2e-artifact.operational-work-item",
    "e2e-artifact.operations-record",
    "e2e-artifact.method-profile",
    "e2e-artifact.service-flow-system",
  ],
  "e2e.ops.a3.t1": [
    "e2e-artifact.operational-work-item",
    "e2e-artifact.service-flow-system",
    "e2e-artifact.operations-record",
    "e2e-artifact.release-record",
    "e2e-artifact.method-profile",
  ],
  "e2e.ops.a3.t2": [
    "e2e-artifact.operational-work-item",
    "e2e-artifact.service-flow-system",
    "e2e-artifact.operations-record",
    "e2e-artifact.increment-record",
    "e2e-artifact.release-record",
    "e2e-artifact.method-profile",
  ],
  "e2e.ops.a4.t1": [
    "e2e-artifact.operational-work-item",
    "e2e-artifact.service-flow-system",
    "e2e-artifact.operations-record",
    "e2e-artifact.method-profile",
    "e2e-artifact.increment-record",
    "e2e-artifact.product-charter",
    "e2e-artifact.release-record",
  ],
  "e2e.ops.a5.t1": [
    "e2e-artifact.operational-work-item",
    "e2e-artifact.service-flow-system",
    "e2e-artifact.increment-record",
    "e2e-artifact.operations-record",
    "e2e-artifact.product-charter",
    "e2e-artifact.method-profile",
    "e2e-artifact.release-record",
  ],
  "e2e.ph2.st1.t1": [
    "e2e-artifact.operations-record",
    "e2e-artifact.release-record",
    "e2e-artifact.product-charter",
    "e2e-artifact.method-profile",
  ],
  "e2e.ph2.st2.t1": [
    "e2e-artifact.retirement-record",
    "e2e-artifact.operations-record",
    "e2e-artifact.release-record",
    "e2e-artifact.method-profile",
  ],
  "e2e.ph2.st2.t2": [
    "e2e-artifact.retirement-record",
    "e2e-artifact.operations-record",
    "e2e-artifact.release-record",
    "e2e-artifact.team-topology",
  ],
  "e2e.ph2.st3.t1": [
    "e2e-artifact.retirement-record",
    "e2e-artifact.operations-record",
    "e2e-artifact.product-charter",
    "e2e-artifact.increment-record",
    "e2e-artifact.method-profile",
    "e2e-artifact.release-record",
  ],
};

const END_TO_END_ACTIVITIES_WITH_INPUTS = applyDeclaredTaskInputs(
  END_TO_END_LIFECYCLE_ACTIVITIES_SOURCE,
  END_TO_END_INPUT_ARTIFACT_IDS,
  "end-to-end",
);

export const END_TO_END_PHASES = END_TO_END_ACTIVITIES_WITH_INPUTS.filter(
  activity => activity.id !== "e2e.ops",
);

const operationsWithInputs = END_TO_END_ACTIVITIES_WITH_INPUTS.find(
  activity => activity.id === "e2e.ops",
);

export const END_TO_END_OPERATIONS_PROCESS = {
  id: "modriss.operations-maintenance",
  name: "Operations and Maintenance Process",
  objective: operationsWithInputs.objective,
  primaryRole: operationsWithInputs.primaryRole,
  entryCriteria: ["G7 operational handover has accepted at least one live release"],
  exitCriteria: ["G8 lifecycle closure is accepted and no live release remains"],
  isOngoing: true,
  isEventDriven: true,
  activities: operationsWithInputs.stages,
};
