import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const methodRoot = path.join(root, 'mde', 'process');
const engineeredRoot = path.join(methodRoot, 'method');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const fail = message => {
  throw new Error(message);
};
const requireText = (text, expected, label) => {
  if (!text.includes(expected)) fail(`${label} is missing: ${expected}`);
};
const count = (text, pattern) => [...text.matchAll(pattern)].length;
const verifyPlantUmlStructure = (text, label) => {
  requireText(text, '@startuml', label);
  requireText(text, '@enduml', label);
  const firstLane = text.search(/^\s*\|[^|\r\n]+\|\s*$/m);
  const firstStart = text.search(/^\s*start\s*$/m);
  if (firstLane >= 0 && firstStart >= 0 && firstLane > firstStart) {
    fail(`${label} must declare its initial swimlane before start`);
  }
  if (/^\s*skinparam\s+\w+\s*\{[^\r\n]*\}\s*$/m.test(text)) {
    fail(`${label} must use a multiline skinparam block for renderer compatibility`);
  }
  const pairs = [
    ['repeat', /^\s*repeat\s*$/gm, 'repeat while', /^\s*repeat while\b/gm],
    ['if', /^\s*if\s*\(/gm, 'endif', /^\s*endif\s*$/gm],
    ['brace-delimited block', /\{\s*$/gm, 'block close', /^\s*}\s*$/gm],
  ];
  for (const [openName, openPattern, closeName, closePattern] of pairs) {
    const opens = count(text, openPattern);
    const closes = count(text, closePattern);
    if (opens !== closes) fail(`${label} has ${opens} ${openName} and ${closes} ${closeName} statements`);
  }
};

const methodProcess = JSON.parse(read('mde/process/definitions/end-to-end.json'));
const processDefinitions = ['end-to-end', 'cim', 'pim', 'psm', 'artifact']
  .map(level => JSON.parse(read(`mde/process/definitions/${level}.json`)));
const allRoleDefinitionIds = new Set(processDefinitions.flatMap(process => process.methodContent?.roleDefinitions?.map(role => role.id) ?? []));
const allWorkProductDefinitionIds = new Set(processDefinitions.flatMap(process => process.methodContent?.workProductDefinitions?.map(workProduct => workProduct.id) ?? []));
const sequences = methodProcess.workSequences ?? [];
const requiredSequences = [
  {
    id: 'ws.release-rework.e2e.rel.a2.e2e.p0',
    predecessorRef: 'e2e.rel.a2',
    successorRef: 'e2e.p0.increment-planning',
    relation: 'release-rework',
    condition: 'G6-rejected-or-promotion-failed',
  },
  {
    id: 'ws.release-cycle.e2e.rel.a3.e2e.p0',
    predecessorRef: 'e2e.rel.a3',
    successorRef: 'e2e.p0.increment-planning',
    relation: 'release-cycle',
    condition: 'next-release-selected-and-retirement-not-authorized',
  },
  {
    id: 'ws.handover.e2e.rel.a2.e2e.ops',
    predecessorRef: 'e2e.rel.a2',
    successorRef: 'modriss.operations-maintenance',
    relation: 'operational-handover',
    condition: 'G6-authorized-and-G7-transitioned',
  },
  {
    id: 'ws.ops-change.e2e.ops.e2e.p0',
    predecessorRef: 'modriss.operations-maintenance',
    successorRef: 'e2e.p0.increment-planning',
    relation: 'maintenance-change-feedback',
    condition: 'service-item-requires-product-change',
  },
  {
    id: 'ws.retirement-development.e2e.ph1.e2e.ph2',
    predecessorRef: 'e2e.ph1',
    successorRef: 'e2e.ph2',
    relation: 'phase-order',
    condition: 'retirement-authorized-and-no-development-or-release-work-in-flight',
  },
];

for (const expected of requiredSequences) {
  const actual = sequences.find(sequence => sequence.id === expected.id);
  if (!actual) fail(`Missing lifecycle WorkSequence ${expected.id}`);
  for (const [property, value] of Object.entries(expected)) {
    if (actual[property] !== value) {
      fail(`${expected.id}.${property} must be ${value}; found ${actual[property]}`);
    }
  }
  if (!actual.guidance) fail(`${expected.id} must carry transition guidance`);
}

for (const forbiddenId of [
  'ws.retirement-operations.e2e.ops.e2e.ph2',
  'ws.retirement-close.e2e.ph2.e2e.ops',
]) {
  if (sequences.some(sequence => sequence.id === forbiddenId)) {
    fail(`${forbiddenId} must not exist: G8 is shared closure evidence, not an activity-flow edge between the processes`);
  }
}

const releaseCycle = methodProcess.processEngine?.releaseCycle;
if (!releaseCycle?.isRepeatable || releaseCycle.activityKind !== 'Iteration' || releaseCycle.repeatCondition !== 'next-release-selected-and-retirement-not-authorized') {
  fail('processEngine.releaseCycle must be an Iteration of activities, not phases');
}
const phases = methodProcess.phases ?? [];
if (phases.length !== 3 || phases.map(phase => phase.id).join(',') !== 'e2e.ph0,e2e.ph1,e2e.ph2') {
  fail('Development and Delivery must contain exactly three ordered, one-time phases');
}
const phaseIds = new Set(phases.map(phase => phase.id));
for (const activityRef of releaseCycle.activityRefs ?? []) {
  if (phaseIds.has(activityRef)) fail(`releaseCycle must not repeat phase ${activityRef}`);
}
for (const level of ['cim', 'pim', 'psm', 'artifact']) {
  const subprocess = JSON.parse(read(`mde/process/definitions/${level}.json`));
  for (const activity of subprocess.phases ?? []) {
    if (activity.activityKind !== 'MODRISS::Stage') {
      fail(`${level}.${activity.id} is repeatable subprocess structure and must be a Stage, not a Phase`);
    }
    for (const child of activity.stages ?? []) {
      if (child.activityKind !== 'MODRISS::SubStage') {
        fail(`${level}.${child.id} must be a SubStage below ${activity.id}`);
      }
    }
  }
}
const serviceDeliveryFlow = methodProcess.processEngine?.serviceDeliveryFlow;
if (!serviceDeliveryFlow?.isRepeatable || !serviceDeliveryFlow.isOngoing || !serviceDeliveryFlow.isEventDriven || serviceDeliveryFlow.activityKind !== 'KanbanFlow') {
  fail('processEngine.serviceDeliveryFlow must be an explicit repeatable KanbanFlow');
}
if (serviceDeliveryFlow.flowControl !== 'pull-with-explicit-WIP-limits-and-service-level-expectations') {
  fail('processEngine.serviceDeliveryFlow must define pull, WIP, and SLE control');
}
for (const stageId of ['e2e.ops.a1', 'e2e.ops.a2', 'e2e.ops.a3', 'e2e.ops.a4', 'e2e.ops.a5']) {
  if (!serviceDeliveryFlow.activityRefs?.includes(stageId)) fail(`serviceDeliveryFlow is missing ${stageId}`);
}
const operationsProcess = methodProcess.processComponents?.find(component => component.id === 'modriss.operations-maintenance');
if (!operationsProcess || operationsProcess.type !== 'Process' || !operationsProcess.isOngoing || !operationsProcess.isEventDriven) {
  fail('Operations and Maintenance must be an ongoing, event-driven Process component');
}
if ((methodProcess.phases ?? []).some(phase => /operate|operations|maintenance/i.test(phase.name))) {
  fail('Operations and Maintenance must not be represented as a lifecycle Phase');
}

const gates = methodProcess.milestones ?? [];
if (gates.map(gate => gate.gateId).join(',') !== 'G0,G1,G2,G3,G4,G5,G6,G7,G8') {
  fail('The executable process must define exactly the ordered gate register G0-G8');
}
for (const gate of gates) {
  if (!gate.acceptanceCondition) fail(`${gate.gateId} must define an acceptance condition`);
  if (!gate.authorityRoleRefs?.length) fail(`${gate.gateId} must name decision authority`);
  if (!gate.requiredEvidenceRefs?.length) fail(`${gate.gateId} must name required evidence`);
  for (const roleRef of gate.authorityRoleRefs) {
    if (!allRoleDefinitionIds.has(roleRef)) fail(`${gate.gateId} references unknown authority ${roleRef}`);
  }
  for (const evidenceRef of gate.requiredEvidenceRefs) {
    if (!allWorkProductDefinitionIds.has(evidenceRef)) fail(`${gate.gateId} references unknown evidence ${evidenceRef}`);
  }
}
const taskDefinitions = methodProcess.methodContent?.taskDefinitions ?? [];
for (const taskId of [
  'task.e2e.ph0.st1a.t1',
  'task.e2e.ph0.st1a.t2',
  'task.e2e.ph0.st1a.t3',
  'task.e2e.p7.artifact-completion.t2',
  'task.e2e.ph2.st2.t3',
]) {
  if (!taskDefinitions.some(task => task.id === taskId)) fail(`Missing corrected executable task ${taskId}`);
}
const roleBindings = new Map((methodProcess.methodContent?.roleDefinitions ?? []).map(role => [role.id, 0]));
for (const task of taskDefinitions) {
  for (const roleRef of task.performerRoleRefs ?? []) {
    if (roleBindings.has(roleRef)) roleBindings.set(roleRef, roleBindings.get(roleRef) + 1);
  }
}
for (const [roleRef, bindings] of roleBindings) {
  if (bindings === 0) fail(`${roleRef} has no executable task binding`);
}

const overview = read('mde/process/method/spem/views/lifecycle.puml');
const sourceView = read('mde/process/method/spem/views/end-to-end-process.activity.puml');
const releaseView = read('mde/process/method/spem/views/release-cycle.puml');
for (const [label, diagram] of [
  ['lifecycle.puml', overview],
  ['end-to-end-process.activity.puml', sourceView],
  ['release-cycle.puml', releaseView],
]) {
  requireText(diagram.toLowerCase(), 'retirement', label);
  requireText(diagram.toLowerCase(), 'next release', label);
  if (!/accepted (release|baseline)/i.test(diagram)) {
    fail(`${label} must state that an accepted release or baseline remains in operation`);
  }
}
if (/Begin next release[^\n]*\n\s*detach/i.test(overview + releaseView)) {
  fail('A next-release path must loop; it may not terminate with detach');
}

const plantUmlPaths = [
  'mde/process/method/spem/views/lifecycle.puml',
  'mde/process/method/spem/views/release-cycle.puml',
  'mde/process/method/spem/views/operations-flow.puml',
  'mde/process/method/spem/views/model-driven-increment.puml',
  'mde/process/method/spem/views/change-routing.puml',
  'mde/process/method/spem/views/end-to-end-process.activity.puml',
  'mde/process/method/spem/views/cim-process.activity.puml',
  'mde/process/method/spem/views/pim-process.activity.puml',
  'mde/process/method/spem/views/psm-process.activity.puml',
  'mde/process/method/spem/views/artifact-process.activity.puml',
];
for (const plantUmlPath of plantUmlPaths) {
  verifyPlantUmlStructure(read(plantUmlPath), plantUmlPath);
}

const lifecycleHtml = read('mde/process/method/diagrams/modriss-lifecycle-manuscript.html');
const alternateHtml = read('mde/process/method/diagrams/modriss-lifecycle-2.html');
const operationsHtml = read('mde/process/method/diagrams/modriss-operational-flow.html');
for (const [label, diagram] of [['primary publication diagram', lifecycleHtml], ['alternative publication diagram', alternateHtml]]) {
  requireText(diagram, 'TWO COORDINATED PROCESSES', label);
  requireText(diagram, 'NOT A PHASE', label);
  requireText(diagram, 'DEVOPS INTERFACE', label);
  requireText(diagram, 'G8 = SHARED CLOSURE CRITERIA', label);
  requireText(diagram, 'NOT AN ACTIVITY FLOW BETWEEN THE PROCESSES', label);
}
for (const expected of ['WIP', 'SLE', 'Operations-only', 'planned release']) {
  requireText(operationsHtml, expected, 'operational-flow publication diagram');
}
const lifecycleSvg = read('mde/process/method/diagrams/modriss-lifecycle-manuscript.svg');
const alternateSvg = read('mde/process/method/diagrams/modriss-lifecycle-2.svg');
const operationsSvg = read('mde/process/method/diagrams/modriss-operational-flow.svg');
requireText(lifecycleSvg, 'TWO COORDINATED PROCESSES', 'exported primary SVG');
requireText(alternateSvg, 'NOT A PHASE', 'exported alternative SVG');
requireText(operationsSvg, 'Operations-only', 'exported operational-flow SVG');
for (const [label, diagram] of [['exported primary SVG', lifecycleSvg], ['exported alternative SVG', alternateSvg]]) {
  requireText(diagram, '<style>', label);
  requireText(diagram, '.lane {', label);
  requireText(diagram, '.node {', label);
}

const processNarrative = read('mde/process/method/04-development-process.md');
const thesisChapter = read('mde/process/method/10-thesis-process-chapter.md');
requireText(processNarrative, '## Normative process vocabulary', 'development-process narrative');
requireText(processNarrative, 'Operations and Maintenance Process — ongoing and event-driven', 'development-process narrative');
requireText(thesisChapter, 'two coordinated processes', 'thesis chapter');

const xml = read('mde/process/method/spem/modriss-method-library.spem.xml');
requireText(xml, 'modriss:sourceId="modriss.end-to-end.release-cycle"', 'generated SPEM XML');
requireText(xml, 'modriss:repeatCondition="next-release-selected-and-retirement-not-authorized"', 'generated SPEM XML');
requireText(xml, 'modriss:sourceId="modriss.end-to-end.service-delivery-flow"', 'generated SPEM XML');
requireText(xml, 'modriss:kind="KanbanFlow"', 'generated SPEM XML');
requireText(xml, 'modriss:isOngoing="true"', 'generated SPEM XML');
requireText(xml, 'modriss:isEventDriven="true"', 'generated SPEM XML');
requireText(xml, 'modriss:sourceId="modriss.operations-maintenance"', 'generated SPEM XML');
requireText(xml, 'modriss:flowControl="pull-with-explicit-WIP-limits-and-service-level-expectations"', 'generated SPEM XML');
for (const expected of requiredSequences) {
  requireText(xml, `modriss:relation="${expected.relation}"`, 'generated SPEM XML');
  requireText(xml, `modriss:condition="${expected.condition}"`, 'generated SPEM XML');
}

console.log(JSON.stringify({
  status: 'ok',
  checkedWorkSequences: requiredSequences.map(sequence => sequence.id),
  checkedGates: gates.map(gate => gate.gateId),
  checkedBoundRoles: roleBindings.size,
  checkedPlantUmlViews: plantUmlPaths.length,
  checkedPublicationSourcesAndExports: 6,
  checkedNarratives: 2,
}, null, 2));
