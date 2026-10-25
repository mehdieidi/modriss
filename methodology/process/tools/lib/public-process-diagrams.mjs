/** Explanatory views of the canonical process; shared by the public reference generator. */
import fs from "node:fs";
import path from "node:path";

const palette = { paper: "#f5f3ed", surface: "#fcfbf7", ink: "#102c3a", muted: "#617178", focalText: "#2c4652", rule: "#ccd2cf", accent: "#087b78", tint: "#e1eeea" };
const xml = (value) => String(value).replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;");
const figures = new Map();

function register(slug, title, description, caption, height, draw) {
  figures.set(slug, { slug, title, description, caption, height, draw });
}

function wrap(value, columns) {
  const lines = [];
  for (const paragraph of String(value).split("\n")) {
    let line = "";
    for (const word of paragraph.split(/\s+/)) {
      if (line && `${line} ${word}`.length > columns) { lines.push(line); line = ""; }
      line += `${line ? " " : ""}${word}`;
    }
    lines.push(line);
  }
  return lines;
}

class Canvas {
  constructor(slug) { this.slug = slug; this.edges = []; this.nodes = []; this.focalAreas = []; }
  text(x, y, value, kind = "label", columns = 100, anchor = "start") {
    const leading = { label: 23, detail: 20, code: 18, tag: 18 }[kind];
    const onTint = this.focalAreas.some((area) => x > area.x && x < area.x + area.w && y > area.y && y < area.y + area.h);
    this.nodes.push(`<text x="${x}" y="${y}" class="${kind}${onTint && kind !== "label" ? " focal-text" : ""}" text-anchor="${anchor}">${wrap(value, columns).map((line, i) => `<tspan x="${x}" dy="${i ? leading : 0}">${xml(line)}</tspan>`).join("")}</text>`);
  }
  box(x, y, w, h, name, detail = "", { focal = false, columns = Math.floor((w - 32) / 10), tag = "", kind = "step" } = {}) {
    if (focal) this.focalAreas.push({ x, y, w, h });
    this.nodes.push(`<g data-node="${xml(name)}" data-x="${x}" data-y="${y}" data-width="${w}" data-height="${h}"><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${kind === "terminal" ? 20 : 6}" fill="${focal ? palette.tint : palette.surface}" stroke="${focal ? palette.accent : palette.ink}" stroke-width="${focal ? 2 : 1.25}" /></g>`);
    let baseline = y + (tag ? 48 : 30);
    if (tag) this.text(x + 16, y + 23, tag, "tag");
    this.text(x + 16, baseline, name, "label", columns);
    baseline += wrap(name, columns).length * 23 + 2;
    if (detail) this.text(x + 16, baseline, detail, "detail", Math.floor((w - 32) / 7.8));
  }
  // Rounded, orthogonal routes; callers supply the corridor rather than routing through nodes.
  edge(points, { dashed = false, focal = false, arrow = true } = {}) {
    let d = `M${points[0].join(" ")}`;
    for (let i = 1; i < points.length; i++) {
      const a = points[i - 1], b = points[i], c = points[i + 1];
      if (!c) { d += `L${b.join(" ")}`; continue; }
      const r = Math.min(8, Math.hypot(b[0] - a[0], b[1] - a[1]) / 2, Math.hypot(c[0] - b[0], c[1] - b[1]) / 2);
      const before = [b[0] - Math.sign(b[0] - a[0]) * r, b[1] - Math.sign(b[1] - a[1]) * r];
      const after = [b[0] + Math.sign(c[0] - b[0]) * r, b[1] + Math.sign(c[1] - b[1]) * r];
      d += `L${before.join(" ")}Q${b.join(" ")} ${after.join(" ")}`;
    }
    this.edges.push(`<path d="${d}" fill="none" stroke="${focal ? palette.accent : palette.muted}" stroke-width="1.75"${dashed ? ' stroke-dasharray="5 4"' : ""}${arrow ? ` marker-end="url(#${this.slug}-${focal ? "accent-" : ""}arrow)"` : ""} />`);
  }
  edgeLabel(x, y, value, anchor = "middle") {
    const w = value.length * 8 + 16;
    const left = anchor === "middle" ? x - w / 2 : x - 8;
    this.nodes.push(`<rect x="${left}" y="${y - 15}" width="${w}" height="22" fill="${palette.paper}" />`);
    this.text(x, y, value, "tag", 100, anchor);
  }
  rule(y) { this.nodes.push(`<path d="M24 ${y}H936" stroke="${palette.rule}" />`); }
}

register("lifecycle", "Development, operations, and shared closure",
  "Three sequential development phases contain repeatable delivery activities, while Operations and Maintenance runs from the first G7 handover until G8 acceptance and no live releases remain.",
  "Phases occur once. Delivery activities repeat inside Phase 1; Operations continues through retirement until shared G8 closure.", 432, (c) => {
    c.edge([[260, 112], [328, 112]]);
    c.edge([[616, 112], [684, 112]]);
    c.edgeLabel(294, 96, "G1");
    c.box(24, 24, 236, 176, "Inception, tailoring & organization", "Compare alternatives; tailor the method, ownership and controls.", { tag: "PHASE 0", columns: 21 });
    c.box(328, 24, 288, 176, "Active construction & evolution", "Repeat increments, qualification, promotion and outcome review.", { tag: "PHASE 1", columns: 25, focal: true });
    c.box(684, 24, 252, 176, "Retire, migrate & close", "Authorize retirement; migrate, decommission and retain records.", { tag: "PHASE 2", columns: 22 });
    c.edge([[472, 200], [472, 264]], { focal: true });
    c.edgeLabel(488, 236, "G7 HANDOVER", "start");
    c.box(328, 264, 608, 100, "Operations and Maintenance", "Ongoing, event-driven Kanban work for every accepted live release.\nStarts at first G7; ends at G8 only when no release remains live.");
    c.text(24, 290, "DevOps coordination", "label", 23);
    c.text(24, 318, "Shared readiness, telemetry,\nchange routing and learning.", "detail", 33);
    c.rule(388);
    c.text(24, 414, "G8 joins retirement and live-service closure evidence with accepted custody of retained records.", "detail");
  });

register("inception", "From product intent to an authorized method",
  "Product intent feeds feasibility and G0, then tailoring, team coordination, and quality, security, and operations baselines support the G1 decision.",
  "G0 decides pursue, explore, redirect, or stop. G1 accepts the method, ownership, controls, and first increment hypothesis.", 592, (c) => {
    const rows = [
      ["Product and System Intent", "Outcomes, success measures and the initial release / increment hypothesis."],
      ["Feasibility & viability", "Compare serverless, hybrid and other alternatives; establish costs, risks and G0."],
      ["Situational Process Tailoring", "Select the method profile and define Ready / Done for this context."],
      ["Team Topology & Coordination", "Assign ownership, interfaces, cadence and escalation paths."],
      ["Quality, Security & Operations", "Agree control objectives and release / operational strategy before G1."],
    ];
    rows.forEach(([name, result], i) => {
      const y = 24 + i * 108;
      if (i) c.edge([[480, y - 24], [480, y]]);
      c.box(80, y, 800, 84, name, result, { focal: i === 1 || i === 4 });
    });
    c.text(80, 580, "These are activities within Phase 0; the phase itself does not repeat.", "detail");
  });

register("increment", "The model-driven increment and its feedback",
  "An increment runs through CIM, PIM, PSM, generation, and readiness with G2 to G5 decisions; transformations connect the model levels and source changes replay affected downstream work.",
  "One capability slice moves through the model levels. G5 can accept, require rework, or defer; an accepted slice can join a release or start the next increment.", 520, (c) => {
    const coords = [[24,24], [360,24], [696,24], [696,240], [360,240], [24,240]];
    const rows = [
      ["Frame increment", "Goal, scope and acceptance evidence", "PLAN"],
      ["CIM child process", "Business intent and domain model", "G2 · CIM ACCEPTANCE"],
      ["PIM child process", "Contracts, architecture and policies", "G3 · PIM ACCEPTANCE"],
      ["PSM child process", "AWS resources and deployment wiring", "G4 · PSM ACCEPTANCE"],
      ["Model-to-text generation", "Generate and fingerprint the baseline", "GENERATE"],
      ["Readiness & acceptance", "Verify artifacts; review and adapt", "G5 · INCREMENT DECISION"],
    ];
    c.edge([[264, 100], [360, 100]]);
    c.edge([[600, 100], [696, 100]]);
    c.edgeLabel(648, 78, "CIM → PIM");
    c.edge([[816, 176], [816, 240]]);
    c.edgeLabel(832, 212, "PIM → PSM", "start");
    c.edge([[696, 316], [600, 316]]);
    c.edge([[360, 316], [264, 316]]);
    rows.forEach(([name, detail, tag], i) => c.box(...coords[i], 240, 152, name, detail, { tag, columns: 21, focal: i === 5 }));
    c.edge([[24, 316], [8, 316], [8, 200], [144, 200], [144, 176]], { dashed: true });
    c.edgeLabel(96, 224, "NEXT SLICE");
    c.edge([[104, 392], [104, 476], [360, 476]]);
    c.box(360, 452, 576, 48, "Accepted increments → release assembly", "", { columns: 52 });
  });

register("release", "Release qualification and operations handover",
  "Accepted increments form an immutable release candidate reviewed at G6, progressively promoted with rollback capability, handed to Operations at G7, and reviewed for outcomes.",
  "G6 authorizes release before production traffic. G7 records the operational handover; failed promotion retains the last accepted operating baseline.", 448, (c) => {
    c.edge([[288, 106], [352, 106]]);
    c.edge([[608, 106], [672, 106]]);
    c.edge([[800, 188], [800, 272]], { focal: true });
    c.edgeLabel(816, 232, "G7 HANDOVER", "start");
    c.edge([[672, 324], [608, 324]]);
    c.edge([[352, 324], [288, 324]], { dashed: true });
    c.box(24, 24, 264, 164, "Assemble candidate", "Pin model and artifact revisions; collect release, recovery and readiness evidence.", { tag: "RELEASE TRAIN ASSEMBLY", columns: 23 });
    c.box(352, 24, 256, 164, "Authorize release", "Review go / no-go evidence and recorded exceptions. Assign response ownership before first traffic.", { tag: "G6", focal: true, columns: 22 });
    c.box(672, 24, 264, 164, "Promote progressively", "Deploy and validate; rehearse rollback and complete handover.", { tag: "PROGRESSIVE PROMOTION", columns: 23 });
    c.box(672, 272, 264, 112, "Operations accepts", "Update the live-release set and its accountable service owners.");
    c.box(352, 272, 256, 112, "Review release", "Use outcomes and telemetry to update the roadmap.");
    c.box(24, 272, 264, 112, "Frame next increment", "When the next release is selected and retirement is not authorized.");
    c.rule(408);
    c.text(24, 434, "Rejected release or failed promotion → earliest affected delivery activity → renewed qualification.", "detail");
  });

register("retirement", "Retirement and the shared closure evidence",
  "An approved retirement plan leads to migration, decommissioning, and records verification; closure requires both retirement evidence and Operations confirmation that no release remains live.",
  "Retirement and live-service shutdown have a shared G8 decision. Retained records still need an accepted, funded custodian after closure.", 472, (c) => {
    c.edge([[280, 96], [352, 96]]);
    c.edge([[608, 96], [680, 96]]);
    c.box(24, 24, 256, 160, "Authorize retirement", "Approve scope, user communication, migration and data-disposition plans.", { tag: "RETIREMENT DECISION", columns: 22 });
    c.box(352, 24, 256, 160, "Migrate & decommission", "Migrate or dispose of data; close service, access and cost surfaces.", { tag: "EXECUTION", columns: 22 });
    c.box(680, 24, 256, 160, "Verify retained records", "Reconcile retention and disposition evidence; accept ongoing archive custody.", { tag: "RECONCILIATION", columns: 22 });
    c.edge([[808, 184], [808, 280]], { focal: true });
    c.edge([[280, 336], [376, 336]], { focal: true });
    c.box(24, 272, 256, 136, "Operations evidence", "No live release remains; shutdown and service obligations are reconciled.", { tag: "CONCURRENT PROCESS", columns: 22 });
    c.box(376, 280, 560, 128, "G8 · shared lifecycle closure", "Review retirement and operational evidence together. Record closure, retained knowledge and accepted records custody.", { focal: true });
    c.text(24, 450, "Operations continues during retirement; neither process closes the other by an activity-flow edge.", "detail");
  });

register("operations", "Kanban controls a service item, not the lifecycle",
  "Service work is requested, made ready, pulled into progress under WIP control, verified, and completed; observation and incident or maintenance work feed this ongoing service system.",
  "Requested → Ready → In Progress → Verify → Done. The workflow policy defines WIP controls and service expectations; Done closes one item.", 368, (c) => {
    const names = ["Requested", "Ready", "In Progress", "Verify", "Done"];
    const details = ["Capture demand, source and owner", "Triage, classify and agree evidence", "Pull when capacity is available", "Check outcome and reconcile source", "Retain evidence and disposition"];
    names.forEach((name, i) => {
      const x = 24 + i * 184;
      if (i) c.edge([[x - 24, 100], [x, 100]]);
      c.box(x, 24, 160, 164, name, details[i], { tag: i === 2 ? "STARTED" : i === 4 ? "FINISHED" : "", columns: 13, focal: i === 2 });
    });
    c.text(24, 224, "Operate & observe", "label");
    c.text(24, 252, "SLOs, telemetry, cost\nand product outcomes", "detail");
    c.text(344, 224, "Respond & maintain", "label");
    c.text(344, 252, "Incidents, problems, risks,\nchange and reconciliation", "detail");
    c.text(664, 224, "Inspect & improve", "label");
    c.text(664, 252, "WIP, work-item age, cycle\ntime, throughput and SLEs", "detail");
    c.rule(312);
    c.text(24, 340, "Maintenance purpose, emergency-temporary status and class of service are separate item attributes.", "detail");
  });

register("change-routing", "Three destinations for operational demand",
  "A maintenance item is routed to operations-only work, the smallest affected model-driven delivery activity, or a planned release backlog; product changes return through release controls and G7.",
  "Choose the disposition explicitly. Emergency restoration remains temporary until its authoritative source and downstream evidence are reconciled.", 464, (c) => {
    c.edge([[480, 100], [480, 152]]);
    c.edge([[360, 188], [176, 188], [176, 268]]);
    c.edge([[480, 224], [480, 268]]);
    c.edge([[600, 188], [800, 188], [800, 268]]);
    c.edgeLabel(256, 172, "RUNBOOK");
    c.edgeLabel(496, 248, "BOUNDED CHANGE", "start");
    c.edgeLabel(712, 172, "PLANNED");
    c.box(304, 24, 352, 76, "Assess maintenance demand", "Source, impact, ownership and urgency");
    c.nodes.push(`<path d="M480 152L600 188L480 224L360 188Z" fill="${palette.tint}" stroke="${palette.accent}" stroke-width="2" />`);
    c.text(480, 194, "Disposition?", "label", 100, "middle");
    c.box(24, 268, 288, 132, "Operations-only work", "Perform and verify service work. Close the item with evidence; avoid model / product drift.", { columns: 24 });
    c.box(352, 268, 272, 132, "Affected delivery work", "Enter at CIM, PIM, PSM, generator or artifact source; requalify and hand back at G7.", { columns: 23, focal: true });
    c.box(664, 268, 272, 132, "Planned release backlog", "Commit the product-changing work to planned delivery; retain the service-item link.", { columns: 23 });
    c.rule(424);
    c.text(24, 450, "A persistent model-owned change must pass the affected model-driven and release controls.", "detail");
  });

const stageDetails = {
  cim: [
    ["Charter, capability slice and strategic intent", "A scoped increment with a business goal"],
    ["Participants, capability topology and glossary", "A shared context and ubiquitous language"],
    ["Information, domain structure and CQRS behavior", "Entities, value objects, commands, queries and events"],
    ["Aggregates, processes and bounded contexts", "Coherent ownership and consistency boundaries"],
    ["Requirements, contracts, trace and explicit validation", "Readiness evidence; review and adapt the increment"],
  ],
  pim: [
    ["Service slice, architecture posture and boundaries", "A scoped platform-independent service design"],
    ["Schemas, event contracts, stores and access patterns", "Contracts and data architecture"],
    ["Functions, compute profiles and API routes", "Compute and API catalogs bound to contracts"],
    ["Channels, integration topology and workflows", "Connected producers, consumers and orchestration"],
    ["Identity, architecture policies and configuration", "Assurance and configuration decisions"],
    ["Platform mapping, trace and explicit validation", "Platform readiness evidence; review and adapt"],
  ],
  psm: [
    ["Deployable slice, accounts, stacks and security", "Deployment strategy, scaffolding and IAM baseline"],
    ["VPC / network posture and Cognito identity", "Network and identity resources"],
    ["DynamoDB, S3, SQS and SNS", "Durable storage and messaging resources"],
    ["EventBridge rules, schedules, pipes and Lambda", "Event fabric and compute bindings"],
    ["API Gateway, Step Functions and CloudWatch", "APIs, workflows and observability resources"],
    ["Integration views, trace and explicit validation", "Generation readiness evidence; review and adapt"],
  ],
  artifact: [
    ["Inventory templates, baseline output and classify gaps", "A controlled generated-project baseline"],
    ["Externalize parameters, secrets and delivery controls", "Environment contract and repeatable automation"],
    ["Build, validate infrastructure and exercise staging", "Verification, acceptance and observability evidence"],
    ["Plan release / recovery; approve and rehearse handover", "A deployment-ready package for release coordination"],
  ],
};

const taskExamples = {
  "development-delivery": { level: "end-to-end", id: "task.e2e.ph0.st1a.t1", steps: ["Compare serverless, hybrid and other alternatives.", "Run bounded experiments for material uncertainty.", "Record recommendation, risks and review triggers."] },
  "operations-maintenance": { level: "end-to-end", id: "task.e2e.ops.a4.t1", steps: ["Identify the authoritative source and affected levels.", "Choose service work, bounded change or planned release.", "Reconcile source and evidence before item closure."] },
  cim: { level: "cim", id: "task.cim.ph4.st1.t1", steps: ["Group commands and emitted events into aggregates.", "Record consistency and interaction expectations."] },
  pim: { level: "pim", id: "task.pim.ph3.st1.t1", steps: ["Create functions and bind their contracts.", "Set compute profile and execution model per handler."] },
  psm: { level: "psm", id: "task.psm.ph4.st2.t1", steps: ["Create Lambda model elements from PIM functions.", "Configure code, layers, aliases and environment."] },
  artifact: { level: "artifact", id: "task.artifact.ph3.st1.t1", steps: ["Build and package through the delivery pipeline.", "Validate templates and retain build results.", "Match deployment artifacts to the tagged revision."] },
};

register("spem-bindings", "Reusable method content and its process uses",
  "Task, role and work-product definitions are reused through TaskUse, RoleUse and WorkProductUse; ProcessPerformer and ProcessParameter bind roles and directional evidence to an activity's task occurrence.",
  "Definitions describe reusable teaching. Uses place that teaching inside an activity; performers and parameters make accountability and evidence direction explicit.", 480, (c) => {
    c.box(24, 24, 264, 96, "RoleDefinition", "Reusable responsibility");
    c.box(352, 24, 256, 96, "TaskDefinition", "Reusable instructions and criteria");
    c.box(672, 24, 264, 96, "WorkProductDefinition", "Reusable evidence description", { columns: 24 });
    [156, 480, 804].forEach((x) => c.edge([[x, 120], [x, 200]]));
    c.edgeLabel(172, 162, "REUSED AS", "start");
    c.edgeLabel(496, 162, "REUSED AS", "start");
    c.edgeLabel(820, 162, "REUSED AS", "start");
    c.box(24, 200, 264, 96, "RoleUse", "Role in this activity");
    c.box(352, 200, 256, 96, "TaskUse", "Task occurrence in this activity", { focal: true });
    c.box(672, 200, 264, 96, "WorkProductUse", "Input or output in this activity");
    c.edge([[288, 248], [352, 248]]);
    c.edge([[672, 248], [608, 248]]);
    c.text(24, 336, "ProcessPerformer", "label");
    c.text(24, 364, "Binds RoleUse to TaskUse as\nprimary or supporting performer.", "detail");
    c.text(560, 336, "ProcessParameter", "label");
    c.text(560, 364, "Binds WorkProductUse to TaskUse\nwith an explicit in / out direction.", "detail");
    c.rule(420);
    c.text(24, 448, "WorkSequence links predecessor and successor work, including explicit iteration and rework conditions.", "detail");
  });

register("gate-review", "Evidence review and the next authorized scope",
  "A task produces evidence tied to a scope and revision, the named authorities review it, and acceptance authorizes that scope while unresolved findings return to source correction and a new review.",
  "Task completion supplies evidence. The named authorities decide acceptance and record conditions or rework for the specific scope and revision.", 452, (c) => {
    c.edge([[288, 84], [352, 84]]);
    c.edge([[480, 144], [480, 200]]);
    c.edge([[592, 260], [672, 260]], { focal: true });
    c.edge([[480, 320], [480, 360]]);
    c.edgeLabel(632, 244, "ACCEPT");
    c.edgeLabel(496, 344, "REWORK / DEFER", "start");
    c.edge([[352, 396], [312, 396], [312, 172], [156, 172], [156, 144]], { dashed: true });
    c.box(24, 24, 264, 120, "Perform scoped work", "Use named inputs and retain the result, revision and trace links.");
    c.box(352, 24, 256, 120, "Prepare gate evidence", "Check required evidence, its currency and the decision authorities.");
    c.nodes.push(`<path d="M480 200L592 260L480 320L368 260Z" fill="${palette.tint}" stroke="${palette.accent}" stroke-width="2" />`);
    c.text(480, 254, "Authorities", "label", 100, "middle");
    c.text(480, 278, "accept?", "label", 100, "middle");
    c.box(672, 212, 264, 104, "Authorize next scope", "Record the decision, conditions and next owner.", { focal: true });
    c.box(352, 360, 584, 68, "Resolve findings and rebuild affected evidence", "Re-enter the responsible source activity; submit a new review.");
  });

register("evidence-lineage", "Traceable evidence across an increment and release",
  "Increment evidence identifies CIM, PIM and PSM revisions plus transformation runs, generated baseline identity, verification results, and release and operational records so changed sources reveal downstream review impact.",
  "Keep exact revisions and transformation runs linked. A changed source requires affected outputs and acceptance evidence to be refreshed.", 352, (c) => {
    const rows = [
      [24, "CIM revision", "Intent and domain"],
      [264, "PIM revision", "Architecture and contracts"],
      [504, "PSM revision", "Platform resources"],
      [744, "Generated baseline", "Fingerprint and source tag"],
    ];
    rows.forEach(([x, name, detail], i) => {
      c.box(x, 24, 192, 108, name, detail, { columns: 16 });
      if (i) c.edge([[x - 48, 80], [x, 80]]);
    });
    c.edge([[840, 132], [840, 208]]);
    c.edge([[744, 256], [656, 256]]);
    c.edge([[344, 256], [256, 256]]);
    c.box(744, 208, 192, 108, "Verification", "Build, tests and readiness evidence");
    c.box(344, 208, 312, 108, "Release record", "Candidate identity, approval, rollback and G7 handover", { focal: true });
    c.box(24, 208, 232, 108, "Operations record", "Live baseline, outcomes and change evidence", { columns: 20 });
  });

function addCanonicalViews(definitions) {
  for (const [key, details] of Object.entries(stageDetails)) {
    const process = definitions[key];
    const count = process.phases.length;
    if (count !== details.length) throw new Error(`Refresh ${key} diagram: stage count changed`);
    const taskCount = (activity) => (activity.taskUses || []).length + (activity.stages || activity.subStages || []).reduce((n, item) => n + taskCount(item), 0);
    register(`${key}-activities`, `${key.toUpperCase()} activity and evidence flow`,
      `${process.phases.map((item) => item.name).join(" leads to ")}; feedback returns to affected source activities before readiness is reconsidered.`,
      key === "artifact" ? "Four readiness stages turn generated output into a verified handover package. Findings reopen the affected source and readiness work." : "Follow the activity sequence for the current slice. Readiness findings reopen affected work; the next slice starts after review and adaptation.",
      48 + count * 120 + 28, (c) => {
        process.phases.forEach((stage, i) => {
          const y = 24 + i * 120;
          if (i) {
            if (!process.workSequences.some((s) => s.predecessorRef === process.phases[i - 1].id && s.successorRef === stage.id)) throw new Error(`No canonical flow into ${stage.id}`);
            c.edge([[500, y - 24], [500, y]]);
          }
          c.box(64, y, 872, 96, stage.name, "", { focal: i === count - 1, columns: 31 });
          c.text(80, y + 80, `${stage.id} · ${taskCount(stage)} tasks`, "code");
          c.nodes.push(`<path d="M420 ${y + 12}V${y + 84}" stroke="${palette.rule}" />`);
          c.text(440, y + 28, details[i][0], "detail", 59);
          c.text(440, y + 68, `→ ${details[i][1]}`, "detail", 59);
        });
        const lastMid = 24 + (count - 1) * 120 + 48;
        c.edge([[64, lastMid], [24, lastMid], [24, 72], [64, 72]], { dashed: true });
        c.text(64, 48 + count * 120 + 4, key === "artifact" ? "Rework: correct the source and repeat affected readiness stages." : "Next slice: return to framing. Rework: return to the earliest affected source activity.", "detail");
      });
  }
  for (const [scope, example] of Object.entries(taskExamples)) {
    const process = definitions[example.level];
    const task = process.methodContent.taskDefinitions.find((item) => item.id === example.id);
    if (!task) throw new Error(`Missing diagram task ${example.id}`);
    const roles = new Map(process.methodContent.roleDefinitions.map((item) => [item.id, item.name]));
    const products = new Map(process.methodContent.workProductDefinitions.map((item) => [item.id, item.name]));
    const inputs = task.inputWorkProductRefs.map((id) => products.get(id));
    const outputs = task.outputWorkProductRefs.map((id) => products.get(id));
    const productLines = (items) => items.flatMap((item) => wrap(item, 29));
    const procedureHeight = 30 + wrap(task.name, 25).length * 23 + 22 + example.steps.reduce((height, step, i) => height + wrap(`${i + 1}. ${step}`, 31).length * 20 + 14, 0) + 8;
    const bodyHeight = Math.ceil(Math.max(200, procedureHeight, 104 + Math.max(productLines(inputs).length, productLines(outputs).length) * 20) / 4) * 4;
    register(`${scope}-task`, task.name, `${roles.get(task.primaryPerformerRoleRef)} performs ${task.name.toLowerCase()}, consuming ${inputs.join(", ")} and producing ${outputs.join(", ")}.`,
      `Example task: ${task.name}. All declared inputs and outputs are shown; the detailed task entry below supplies the full procedure and criteria.`,
      180 + bodyHeight, (c) => {
        c.box(336, 24, 288, 72, roles.get(task.primaryPerformerRoleRef), "Primary performer", { columns: 25 });
        c.edge([[480, 96], [480, 152]]);
        c.edge([[272, 240], [336, 240]]);
        c.edge([[624, 240], [688, 240]]);
        c.box(24, 152, 248, bodyHeight, "Inputs", "", { tag: `${inputs.length} WORK PRODUCT${inputs.length === 1 ? "" : "S"}` });
        c.text(40, 240, productLines(inputs).join("\n"), "detail", 29);
        c.box(336, 152, 288, bodyHeight, task.name, "", { focal: true, columns: 25 });
        let y = 152 + 30 + wrap(task.name, 25).length * 23 + 22;
        example.steps.forEach((step, i) => {
          const text = `${i + 1}. ${step}`;
          c.text(352, y, text, "detail", 31);
          y += wrap(text, 31).length * 20 + 14;
        });
        c.box(688, 152, 248, bodyHeight, "Outputs", "", { tag: `${outputs.length} WORK PRODUCT${outputs.length === 1 ? "" : "S"}` });
        c.text(704, 240, productLines(outputs).join("\n"), "detail", 29);
      });
  }
}

export function processFigure(slug, depth = 2) {
  const item = figures.get(slug);
  if (!item) throw new Error(`Unknown process diagram ${slug}; generate diagrams before rendering pages`);
  const url = `${"../".repeat(depth)}assets/diagrams/process-reference/${slug}.svg`;
  return `<figure class="doc-diagram">\n  <a class="doc-diagram__link" href="${url}" aria-label="Open full-size diagram: ${xml(item.title)}">\n    <img src="${url}" alt="${xml(item.description)}" loading="lazy" />\n  </a>\n  <figcaption>${xml(item.caption)}</figcaption>\n</figure>\n\n`;
}

export function generatePublicProcessDiagrams(root, definitions) {
  addCanonicalViews(definitions);
  const source = path.join(root, "methodology/process/method/diagrams/process-reference");
  const output = path.join(root, "docs/public-docs/docs/assets/diagrams/process-reference");
  fs.mkdirSync(source, { recursive: true });
  fs.mkdirSync(output, { recursive: true });
  // Embed the local brand face because SVGs loaded through <img> cannot fetch web fonts.
  const font = fs.readFileSync(path.join(root, "docs/public-docs/docs/assets/fonts/SpaceGrotesk-Diagrams.woff2")).toString("base64");
  for (const item of figures.values()) {
    const c = new Canvas(item.slug);
    item.draw(c);
    const style = `@font-face{font-family:'Space Grotesk';font-style:normal;font-weight:300 700;src:url(data:font/woff2;base64,${font}) format('woff2')}text{font-family:'Space Grotesk','Segoe UI',sans-serif;fill:${palette.ink}}.label{font-size:18px;font-weight:600}.detail{font-size:14px;fill:${palette.muted}}.code{font-family:Consolas,monospace;font-size:12px;fill:${palette.muted}}.tag{font-size:12px;font-weight:600;letter-spacing:.4px;fill:${palette.muted}}.focal-text{fill:${palette.focalText}}`;
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="960" height="${item.height}" viewBox="0 0 960 ${item.height}" role="img" aria-labelledby="${item.slug}-title ${item.slug}-desc">\n<title id="${item.slug}-title">${xml(item.title)}</title>\n<desc id="${item.slug}-desc">${xml(item.description)}</desc>\n<defs><style>${style}</style><marker id="${item.slug}-arrow" markerWidth="8" markerHeight="6" refX="7" refY="3" orient="auto"><path d="M0 0L8 3L0 6Z" fill="${palette.muted}" /></marker><marker id="${item.slug}-accent-arrow" markerWidth="8" markerHeight="6" refX="7" refY="3" orient="auto"><path d="M0 0L8 3L0 6Z" fill="${palette.accent}" /></marker></defs>\n<rect width="960" height="${item.height}" fill="${palette.paper}" />\n${c.edges.join("\n")}\n${c.nodes.join("\n")}\n</svg>`;
    const html = `<!DOCTYPE html>\n<html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${xml(item.title)}</title><style>body{margin:0;background:${palette.paper}}main{max-width:960px;margin:auto;min-width:0}.diagram-container{width:100%;overflow-x:auto}svg{display:block;width:100%;min-width:960px;height:auto}@media print{.diagram-container{overflow-x:visible}svg{min-width:0}}</style></head><body><main><div class="diagram-container">\n${svg}\n</div></main></body></html>\n`;
    // Keep an editable, self-contained HTML source before emitting the same inline SVG for MkDocs.
    fs.writeFileSync(path.join(source, `${item.slug}.html`), html);
    fs.writeFileSync(path.join(output, `${item.slug}.svg`), `<?xml version="1.0" encoding="UTF-8"?>\n${svg}\n`);
  }
  return figures.size;
}
