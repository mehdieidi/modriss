#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const processRoot = path.join(root, 'mde', 'process', 'definitions');
const outRoot = path.join(root, 'mde', 'process', 'method', 'evaluation', 'coldchain-sentinel');
const processNames = ['end-to-end', 'cim', 'pim', 'psm', 'artifact'];
const definitions = Object.fromEntries(processNames.map(name => [
  name,
  JSON.parse(fs.readFileSync(path.join(processRoot, `${name}.json`), 'utf8')),
]));

const people = {
  'role.sponsor': 'Nadia Rahimi — executive sponsor',
  'role.product-owner': 'Leila Farzan — product owner',
  'role.domain-expert': 'Omid Shariati — cold-chain specialist',
  'role.method-engineer': 'Sara Vahidi — method engineer',
  'role.delivery-lead': 'Arman Nouri — delivery lead',
  'role.requirements-engineer': 'Mina Karimi — requirements engineer',
  'role.business-modeler': 'Mina Karimi — business modeler',
  'role.solution-architect': 'Reza Tavakoli — solution architect',
  'role.cloud-platform-engineer': 'Parisa Azadi — cloud platform engineer',
  'role.application-engineer': 'Kian Mehr — software engineer',
  'role.quality-engineer': 'Yasaman Daryaei — quality engineer',
  'role.security-engineer': 'Navid Etemadi — security and privacy engineer',
  'role.finops-cost-analyst': 'Shirin Mehrabi — FinOps analyst',
  'role.release-engineer': 'Pouya Jalali — release engineer',
  'role.service-owner': 'Hoda Moradi — service owner/SRE',
  'role.process-reviewer': 'Ali Zand — assurance reviewer',
  'role.records-data-steward': 'Maryam Sadeghi — records and data steward',
};

const taskOverrides = {
  'task.e2e.ph0.st1a.t1': 'Compared serverless, container, and managed-IoT alternatives. Selected a serverless event-driven design, with a bounded proof for private ERP connectivity and an explicit AWS exit assumption.',
  'task.e2e.ph0.st1a.t2': 'Estimated request, Lambda duration/memory, workflow transition, DynamoDB, S3, logging, and transfer ranges; defined cost per monitored shipment and a 20% anomaly threshold.',
  'task.e2e.ph0.st1a.t3': 'G0 = pursue, conditional on the ERP-connectivity experiment and a monthly pilot budget ceiling.',
  'task.e2e.p2.cim-to-pim.t1': 'The transform created service and contract scaffolding. Review found that sensor-reading idempotency and late-arrival semantics needed an explicit PIM decision.',
  'task.e2e.p4.pim-to-psm.t1': 'The AWS mapping resolved API Gateway, Lambda, DynamoDB, EventBridge, SQS/SNS, Step Functions, Cognito, S3, and CloudWatch resources; private ERP connectivity remained a reviewed manual mapping.',
  'task.e2e.p7.artifact-completion.t2': 'The first retrospective found review-queue delay and duplicate evidence entry; the profile added a gate-ready signal, review service expectation, and evidence links instead of copied attachments.',
  'task.e2e.rel.a2.t1': 'Release 1 used a weighted Lambda alias and stop thresholds. Release 2 paused at 10% after duplicate alerts, retained the accepted baseline, corrected the authoritative PIM retry/idempotency policy, and was requalified.',
  'task.e2e.ops.a2.t2': 'The board enforced WIP 2 for standard service work and one expedite item. The incident displaced one standard item, which remained visible and resumed after restoration.',
  'task.e2e.ops.a3.t1': 'An expedite incident restored alert delivery by disabling a faulty retry path; the temporary production change stayed open until model and generated-source reconciliation completed.',
  'task.e2e.ops.a4.t1': 'Three dispositions were exercised: alarm-threshold tuning closed operations-only; retry/idempotency correction used a bounded PIM→PSM→artifact release path; analytics export was committed to the next planned release.',
  'task.e2e.ph2.st2.t3': 'Reconciled DynamoDB tables, S3 archives, backups, exports, CloudWatch retention, and legal-hold records; verified deletion or transferred custody before G8.',
  'task.e2e.ph2.st3.t1': 'G8 accepted only after the final live alias was removed, API and credentials were disabled, retained data had named custody, residual spend reached the agreed threshold, and both processes supplied closure evidence.',
};

function csv(value) {
  const text = Array.isArray(value) ? value.join(' | ') : String(value ?? '');
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

function occurrence(processName, taskId) {
  if (processName !== 'end-to-end') return processName === 'artifact' ? 'Increment 1 and Release 1; repeated selectively for Release 2' : 'Increment 1; affected tasks repeated for Increment 2';
  if (taskId.includes('.ph0.')) return 'Phase 0 — once';
  if (taskId.includes('.ops.')) return 'Operations window — repeated by event and cadence';
  if (taskId.includes('.ph2.')) return 'Phase 2 — once';
  if (taskId.includes('.rel.')) return 'Release 1 and Release 2';
  return 'Phase 1 — Increment 1; affected path repeated in Increment 2';
}

function defaultTaskNote(processName, task) {
  const outputs = task.outputWorkProductRefs?.length ? task.outputWorkProductRefs.join(', ') : 'linked evidence';
  const context = {
    'end-to-end': 'ColdChain Sentinel lifecycle',
    cim: 'shipment monitoring, temperature excursion, alert acknowledgement, compliance, and stakeholder intent',
    pim: 'provider-independent ingestion, contracts, event flow, workflow, security, resilience, data, and observability design',
    psm: 'AWS deployment slice and its resource relationships',
    artifact: 'generated SAM/application baseline, completed logic, tests, pipeline, readiness, and handover',
  }[processName];
  return `Performed “${task.name}” for the ${context}; reviewed the declared entry/exit and validation rules; recorded ${outputs}.`;
}

const tasks = [];
for (const processName of processNames) {
  for (const task of definitions[processName].methodContent.taskDefinitions ?? []) {
    const primary = task.primaryPerformerRoleRef ?? task.performerRoleRefs?.[0] ?? '';
    const supporting = (task.performerRoleRefs ?? []).filter(role => role !== primary);
    tasks.push({
      process: processName,
      taskId: task.id,
      taskName: task.name,
      occurrence: occurrence(processName, task.id),
      primaryRole: primary,
      primaryAssignee: people[primary] ?? primary,
      supportingRoles: supporting,
      inputRefs: task.inputWorkProductRefs ?? [],
      outputRefs: task.outputWorkProductRefs ?? [],
      status: 'simulated-complete',
      caseEvidence: taskOverrides[task.id] ?? defaultTaskNote(processName, task),
      assessment: taskOverrides[task.id] ? 'critical-path evidence retained' : 'useful and enactable; no unowned handoff observed',
    });
  }
}

const roleMap = new Map();
for (const [processName, definition] of Object.entries(definitions)) {
  for (const role of definition.methodContent.roleDefinitions ?? []) {
    const entry = roleMap.get(role.id) ?? { id: role.id, name: role.name, processes: new Set(), tasks: new Set() };
    entry.processes.add(processName);
    roleMap.set(role.id, entry);
  }
}
for (const task of tasks) for (const role of [task.primaryRole, ...task.supportingRoles]) roleMap.get(role)?.tasks.add(task.taskId);

const workProductMap = new Map();
for (const [processName, definition] of Object.entries(definitions)) {
  for (const workProduct of definition.methodContent.workProductDefinitions ?? []) {
    const entry = workProductMap.get(workProduct.id) ?? { ...workProduct, processes: new Set(), producers: new Set(), consumers: new Set() };
    entry.processes.add(processName);
    workProductMap.set(workProduct.id, entry);
  }
}
for (const task of tasks) {
  for (const ref of task.outputRefs) workProductMap.get(ref)?.producers.add(task.taskId);
  for (const ref of task.inputRefs) workProductMap.get(ref)?.consumers.add(task.taskId);
}

const gateDecisions = {
  G0: 'Pursue with bounded ERP-connectivity experiment and pilot budget ceiling.',
  G1: 'Authorized standard profile, named owners, first vertical slice, evidence rules, and operations policy.',
  G2: 'Accepted CIM revision for shipment telemetry and excursion response.',
  G3: 'Accepted PIM after adding idempotency and late-event policies.',
  G4: 'Accepted AWS PSM with manual private-ERP mapping explicitly owned.',
  G5: 'Increment 1 accepted; Increment 2 initially reworked, then accepted after retry-policy correction.',
  G6: 'Release 1 authorized; Release 2 rejected once at stop threshold, corrected, then authorized.',
  G7: 'Each promoted release was handed to Operations with SLO, runbook, rollback, cost, and support evidence.',
  G8: 'Shared closure accepted after zero live releases and closure of access, cost, records, and data obligations.',
};
const gates = (definitions['end-to-end'].milestones ?? []).map(gate => ({
  gateId: gate.gateId,
  name: gate.name,
  authorities: gate.authorityRoleRefs ?? [],
  evidence: gate.requiredEvidenceRefs ?? [],
  condition: gate.acceptanceCondition,
  decision: gateDecisions[gate.gateId] ?? 'Simulated decision recorded.',
  status: 'simulated-accepted',
}));

fs.mkdirSync(outRoot, { recursive: true });
const taskHeader = ['process', 'task_id', 'task_name', 'occurrence', 'primary_role', 'primary_assignee', 'supporting_roles', 'inputs', 'outputs', 'status', 'case_evidence', 'assessment'];
const taskRows = tasks.map(task => [task.process, task.taskId, task.taskName, task.occurrence, task.primaryRole, task.primaryAssignee, task.supportingRoles, task.inputRefs, task.outputRefs, task.status, task.caseEvidence, task.assessment]);
fs.writeFileSync(path.join(outRoot, 'task-enactment-ledger.csv'), `${[taskHeader, ...taskRows].map(row => row.map(csv).join(',')).join('\n')}\n`);

const roleHeader = ['role_id', 'role_name', 'hypothetical_assignee', 'source_processes', 'task_bindings', 'status'];
const roleRows = [...roleMap.values()].sort((a, b) => a.id.localeCompare(b.id)).map(role => [role.id, role.name, people[role.id] ?? role.id, [...role.processes].sort(), [...role.tasks].sort(), role.tasks.size ? 'exercised' : 'unbound']);
fs.writeFileSync(path.join(outRoot, 'role-enactment-ledger.csv'), `${[roleHeader, ...roleRows].map(row => row.map(csv).join(',')).join('\n')}\n`);

const wpHeader = ['work_product_id', 'name', 'method_work_product_ids', 'source_processes', 'producer_tasks', 'consumer_tasks', 'hypothetical_instance', 'status'];
const wpRows = [...workProductMap.values()].sort((a, b) => a.id.localeCompare(b.id)).map(wp => [wp.id, wp.name, wp.methodWorkProductIds ?? wp.methodWorkProductId ?? [], [...wp.processes].sort(), [...wp.producers].sort(), [...wp.consumers].sort(), `CS-${wp.id.replace(/[^A-Za-z0-9]+/g, '-').toUpperCase()}-V1`, 'simulated-produced-and-reviewed']);
fs.writeFileSync(path.join(outRoot, 'work-product-enactment-ledger.csv'), `${[wpHeader, ...wpRows].map(row => row.map(csv).join(',')).join('\n')}\n`);

const gateHeader = ['gate_id', 'name', 'authority_roles', 'required_evidence', 'acceptance_condition', 'decision', 'status'];
const gateRows = gates.map(gate => [gate.gateId, gate.name, gate.authorities, gate.evidence, gate.condition, gate.decision, gate.status]);
fs.writeFileSync(path.join(outRoot, 'gate-decision-ledger.csv'), `${[gateHeader, ...gateRows].map(row => row.map(csv).join(',')).join('\n')}\n`);

const summary = {
  caseId: 'coldchain-sentinel-hypothetical-01',
  evidenceKind: 'analytical hypothetical enactment; not empirical observation',
  system: 'ColdChain Sentinel — serverless cold-chain monitoring and excursion response',
  lifecycle: ['Phase 0 once', 'Phase 1 with two increments and two releases', 'Operations from first G7 through Phase 2', 'Phase 2 once', 'shared G8 closure'],
  operationalItems: [
    'operations-only: tune an alarm threshold and retain evidence',
    'bounded MDE/release: correct retry/idempotency policy from PIM through release',
    'planned release: add compliance analytics export',
    'expedite incident: restore alert delivery, then reconcile the temporary change',
  ],
  counts: {
    tasks: tasks.length,
    roles: roleMap.size,
    boundRoles: [...roleMap.values()].filter(role => role.tasks.size).length,
    workProducts: workProductMap.size,
    gates: gates.length,
  },
  conclusion: 'The corrected process is internally coherent and enactable for this scenario. Remaining claims about efficiency, usability, and comparative effectiveness require empirical cases with practitioners.',
};
fs.writeFileSync(path.join(outRoot, 'coverage-summary.json'), `${JSON.stringify(summary, null, 2)}\n`);
console.log(JSON.stringify(summary, null, 2));
