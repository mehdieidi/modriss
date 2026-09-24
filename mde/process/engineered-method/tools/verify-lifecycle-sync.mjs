import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const methodRoot = path.join(root, 'mde', 'process');
const engineeredRoot = path.join(methodRoot, 'engineered-method');
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

const methodProcess = JSON.parse(read('mde/process/process-definitions/end-to-end.json'));
const sequences = methodProcess.workSequences ?? [];
const requiredSequences = [
  {
    id: 'ws.release-rework.e2e.ph2.e2e.ph1',
    predecessorRef: 'e2e.ph2',
    successorRef: 'e2e.ph1',
    relation: 'release-rework',
    condition: 'G6-rejected-or-promotion-failed',
  },
  {
    id: 'ws.release-cycle.e2e.ph3.e2e.ph1',
    predecessorRef: 'e2e.ph3',
    successorRef: 'e2e.ph1',
    relation: 'release-cycle',
    condition: 'retirement-not-authorized-and-next-release-or-change-selected',
  },
  {
    id: 'ws.e2e.ph3.e2e.ph4',
    predecessorRef: 'e2e.ph3',
    successorRef: 'e2e.ph4',
    relation: 'retirement-transition',
    condition: 'retirement-authorized',
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

const releaseCycle = methodProcess.processEngine?.releaseCycle;
if (!releaseCycle?.isRepeatable || releaseCycle.repeatCondition !== 'retirement-not-authorized') {
  fail('processEngine.releaseCycle must explicitly repeat until retirement is authorized');
}
const maintenanceFlow = methodProcess.processEngine?.maintenanceFlow;
if (!maintenanceFlow?.isRepeatable || maintenanceFlow.activityKind !== 'KanbanFlow') {
  fail('processEngine.maintenanceFlow must be an explicit repeatable KanbanFlow');
}
if (maintenanceFlow.flowControl !== 'pull-with-explicit-WIP-limits-and-service-level-expectations') {
  fail('processEngine.maintenanceFlow must define pull, WIP, and SLE control');
}
for (const stageId of ['e2e.ph3.st1', 'e2e.ph3.st2a', 'e2e.ph3.st2', 'e2e.ph3.st3', 'e2e.ph3.st4']) {
  if (!maintenanceFlow.activityRefs?.includes(stageId)) fail(`maintenanceFlow is missing ${stageId}`);
}

const overview = read('mde/process/engineered-method/spem/lifecycle.puml');
const sourceView = read('mde/process/spem/end-to-end-process.activity.puml');
const releaseView = read('mde/process/engineered-method/spem/release-cycle.puml');
for (const [label, diagram] of [
  ['lifecycle.puml', overview],
  ['end-to-end-process.activity.puml', sourceView],
  ['release-cycle.puml', releaseView],
]) {
  requireText(diagram, 'Retirement authorized?', label);
  requireText(diagram.toLowerCase(), 'next release', label);
  if (!/accepted (release|baseline)/i.test(diagram)) {
    fail(`${label} must state that an accepted release or baseline remains in operation`);
  }
}
if (/Begin next release[^\n]*\n\s*detach/i.test(overview + releaseView)) {
  fail('A next-release path must loop; it may not terminate with detach');
}

const plantUmlPaths = [
  'mde/process/engineered-method/spem/lifecycle.puml',
  'mde/process/engineered-method/spem/release-cycle.puml',
  'mde/process/engineered-method/spem/operations-flow.puml',
  'mde/process/engineered-method/spem/model-driven-increment.puml',
  'mde/process/engineered-method/spem/change-routing.puml',
  'mde/process/spem/end-to-end-process.activity.puml',
  'mde/process/spem/cim-process.activity.puml',
  'mde/process/spem/pim-process.activity.puml',
  'mde/process/spem/psm-process.activity.puml',
  'mde/process/spem/artifact-process.activity.puml',
];
for (const plantUmlPath of plantUmlPaths) {
  verifyPlantUmlStructure(read(plantUmlPath), plantUmlPath);
}

const lifecycleHtml = read('mde/process/engineered-method/diagrams/modriss-lifecycle-manuscript.html');
const alternateHtml = read('mde/process/engineered-method/diagrams/modriss-lifecycle-2.html');
const operationsHtml = read('mde/process/engineered-method/diagrams/modriss-operational-flow.html');
requireText(lifecycleHtml, 'SELECTED CHANGE → PLANNED RELEASE', 'primary publication diagram');
requireText(alternateHtml, 'SELECTED CHANGE → PLANNED RELEASE', 'alternative publication diagram');
for (const expected of ['WIP', 'SLE', 'Operations-only', 'planned release']) {
  requireText(operationsHtml, expected, 'operational-flow publication diagram');
}
const lifecycleSvg = read('mde/process/engineered-method/diagrams/modriss-lifecycle-manuscript.svg');
const alternateSvg = read('mde/process/engineered-method/diagrams/modriss-lifecycle-2.svg');
const operationsSvg = read('mde/process/engineered-method/diagrams/modriss-operational-flow.svg');
requireText(lifecycleSvg, 'SELECTED CHANGE → PLANNED RELEASE', 'exported primary SVG');
requireText(alternateSvg, 'SELECTED CHANGE → PLANNED RELEASE', 'exported alternative SVG');
requireText(operationsSvg, 'Operations-only', 'exported operational-flow SVG');

const processNarrative = read('mde/process/engineered-method/04-development-process.md');
const thesisChapter = read('mde/process/engineered-method/10-thesis-process-chapter.md');
requireText(processNarrative, '### Nested lifecycle cadence', 'development-process narrative');
requireText(thesisChapter, 'The lifecycle therefore has three nested cycles and one concurrent service', 'thesis chapter');

const xml = read('mde/process/engineered-method/spem/modriss-method-library.spem.xml');
requireText(xml, 'modriss:sourceId="modriss.end-to-end.release-cycle"', 'generated SPEM XML');
requireText(xml, 'modriss:repeatCondition="retirement-not-authorized"', 'generated SPEM XML');
requireText(xml, 'modriss:sourceId="modriss.end-to-end.maintenance-flow"', 'generated SPEM XML');
requireText(xml, 'modriss:kind="KanbanFlow"', 'generated SPEM XML');
requireText(xml, 'modriss:flowControl="pull-with-explicit-WIP-limits-and-service-level-expectations"', 'generated SPEM XML');
for (const expected of requiredSequences) {
  requireText(xml, `modriss:relation="${expected.relation}"`, 'generated SPEM XML');
  requireText(xml, `modriss:condition="${expected.condition}"`, 'generated SPEM XML');
}

console.log(JSON.stringify({
  status: 'ok',
  checkedWorkSequences: requiredSequences.map(sequence => sequence.id),
  checkedPlantUmlViews: plantUmlPaths.length,
  checkedPublicationSourcesAndExports: 6,
  checkedNarratives: 2,
}, null, 2));
