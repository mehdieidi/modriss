import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const packageRoot = path.join(root, 'mde', 'methodology', 'engineered-method');
const sourceRoot = path.join(root, 'mde', 'methodology', 'process-definitions');
const spemRoot = path.join(packageRoot, 'spem');
const libraryRoot = path.join(packageRoot, 'method-library');
const sourceNames = ['end-to-end', 'cim', 'pim', 'psm', 'artifact'];

const readJson = file => JSON.parse(fs.readFileSync(file, 'utf8'));
const processSources = sourceNames.map(name => ({
  name,
  file: path.join(sourceRoot, `${name}.json`),
  model: readJson(path.join(sourceRoot, `${name}.json`)),
}));
const coreContentFile = path.join(libraryRoot, 'core-method-content.json');
const coreContentSource = {
  name: 'engineered-core',
  file: coreContentFile,
  model: readJson(coreContentFile),
};
const fragmentsFile = path.join(libraryRoot, 'method-fragments.json');
const fragmentsModel = readJson(fragmentsFile);
const fragmentGuidance = fragmentsModel.fragments.map(fragment => ({
  id: `guidance.fragment.${fragment.id.toLowerCase()}`,
  type: 'Guidance',
  name: `${fragment.id} — ${fragment.name}`,
  guidanceKind: fragment.kind === 'continuous' ? 'Continuous Practice Pattern' : 'Process Pattern',
  description: `${fragment.problem} Result: ${fragment.resultContext}`,
  sourcePatternId: fragment.id,
  initialContext: fragment.initialContext,
  resultContext: fragment.resultContext,
  roles: fragment.roles,
  workProducts: fragment.workProducts,
  selection: fragment.selection,
  requirements: fragment.requirements,
  references: fragment.source,
}));
const fragmentSource = {
  name: 'engineered-fragments',
  file: fragmentsFile,
  model: {
    methodContent: {
      roleDefinitions: [],
      taskDefinitions: [],
      workProductDefinitions: [],
      guidance: fragmentGuidance,
    },
  },
};
const contentSources = [...processSources, coreContentSource, fragmentSource];

const consolidate = (property, type) => {
  const byId = new Map();
  for (const source of contentSources) {
    for (const item of source.model.methodContent?.[property] ?? []) {
      const existing = byId.get(item.id);
      if (!existing) {
        byId.set(item.id, { ...item, sourceProcesses: [source.name], sourceVariants: [] });
      } else if (!existing.sourceProcesses.includes(source.name)) {
        existing.sourceProcesses.push(source.name);
        if (existing.name !== item.name) {
          existing.sourceVariants.push({
            sourceProcess: source.name,
            field: 'name',
            canonicalValue: existing.name,
            sourceValue: item.name,
          });
        }
      }
    }
  }
  return [...byId.values()].sort((a, b) => a.id.localeCompare(b.id));
};

const index = {
  schemaVersion: '1.0',
  generatedFrom: contentSources.map(source => path.relative(root, source.file).replaceAll('\\', '/')),
  spemVersion: '2.0',
  representation: 'Consolidated MODRISS method content mapped to SPEM 2.0',
  roleDefinitions: consolidate('roleDefinitions', 'RoleDefinition'),
  taskDefinitions: consolidate('taskDefinitions', 'TaskDefinition'),
  workProductDefinitions: consolidate('workProductDefinitions', 'WorkProductDefinition'),
  guidance: consolidate('guidance', 'Guidance'),
  processComponents: processSources.map(source => ({
    id: source.model.processId,
    name: source.model.displayName,
    sourceProcess: source.name,
    phaseCount: (source.model.phases ?? []).length,
    workSequenceCount: (source.model.workSequences ?? []).length,
    repeatableActivities: [
      source.model.processEngine?.iteration,
      source.model.processEngine?.releaseCycle,
    ].filter(Boolean),
  })),
  methodRoleMappings: coreContentSource.model.roleMappings ?? [],
};

for (const mapping of index.methodRoleMappings) {
  for (const roleRef of mapping.roleDefinitionRefs ?? []) {
    const role = index.roleDefinitions.find(item => item.id === roleRef);
    if (role) role.methodRoleIds = [...new Set([...(role.methodRoleIds ?? []), mapping.methodRoleId])];
  }
}

fs.mkdirSync(spemRoot, { recursive: true });
fs.mkdirSync(libraryRoot, { recursive: true });
fs.writeFileSync(path.join(spemRoot, 'method-content-index.json'), `${JSON.stringify(index, null, 2)}\n`);

const csv = value => {
  const text = value == null ? '' : String(value);
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
};
const inventoryRows = [['sourceProcess', 'elementType', 'id', 'name', 'sourceFile']];
for (const source of contentSources) {
  const groups = [
    ['RoleDefinition', source.model.methodContent?.roleDefinitions ?? []],
    ['TaskDefinition', source.model.methodContent?.taskDefinitions ?? []],
    ['WorkProductDefinition', source.model.methodContent?.workProductDefinitions ?? []],
    ['Guidance', source.model.methodContent?.guidance ?? []],
  ];
  for (const [type, items] of groups) {
    for (const item of items) {
      inventoryRows.push([
        source.name,
        type,
        item.id,
        item.name ?? item.title ?? '',
        path.relative(root, source.file).replaceAll('\\', '/'),
      ]);
    }
  }
}
fs.writeFileSync(
  path.join(libraryRoot, 'repository-inventory.csv'),
  `${inventoryRows.map(row => row.map(csv).join(',')).join('\n')}\n`,
);

const mdInline = value => String(value ?? '')
  .replaceAll('|', '\\|')
  .replace(/\s+/g, ' ')
  .trim();
const mdRefs = values => (values?.length ? values.map(value => `\`${mdInline(value)}\``).join(', ') : 'None declared');
const mdSentence = value => {
  const text = mdInline(value);
  return /[.!?]$/.test(text) ? text : `${text}.`;
};
const mdSentences = values => (values?.length ? values.map(mdSentence).join(' ') : 'No additional description is declared.');
const sourceLabels = {
  'end-to-end': 'End-to-end lifecycle',
  cim: 'CIM modeling component',
  pim: 'PIM modeling component',
  psm: 'AWS PSM modeling component',
  artifact: 'Artifact-readiness component',
  'engineered-core': 'Engineered core content',
  'engineered-fragments': 'Engineered fragment catalog',
};

const catalog = [
  '# MODRISS Reusable Method Content Catalog',
  '',
  '> This catalog is generated from the authoritative process definitions and',
  '> engineered method-content sources by `../tools/build-method-package.mjs`.',
  '> Edit the source JSON rather than this file, then rebuild the package.',
  '',
  '## Scope and notation',
  '',
  `The consolidated repository contains ${index.roleDefinitions.length} RoleDefinitions, ${index.taskDefinitions.length} TaskDefinitions, ${index.workProductDefinitions.length} WorkProductDefinitions, and ${index.guidance.length} Guidance elements. Stable identifiers are shown because the delivery processes refer to these definitions through RoleUse, TaskUse, WorkProductUse, and ProcessParameter elements. “Provenance” identifies the source component from which an element was consolidated.`,
  '',
  '## Role definitions',
  '',
];

for (const role of index.roleDefinitions) {
  catalog.push(`### ${mdInline(role.name)} (\`${role.id}\`)`, '');
  catalog.push(mdSentences(role.responsibilities), '');
  catalog.push(`Conceptual role mapping: ${mdRefs(role.methodRoleIds)}. Provenance: ${(role.sourceProcesses ?? []).map(source => sourceLabels[source] ?? source).join(', ')}.`, '');
}

catalog.push('## Task definitions', '');
for (const sourceName of sourceNames) {
  const tasks = index.taskDefinitions.filter(task => task.sourceProcesses?.includes(sourceName));
  catalog.push(`### ${sourceLabels[sourceName]} (${tasks.length} tasks)`, '');
  for (const task of tasks) {
    catalog.push(`#### ${mdInline(task.name)} (\`${task.id}\`)`, '');
    const purpose = mdInline(task.purpose ?? task.description ?? task.name);
    catalog.push(`${purpose.endsWith('.') ? purpose : `${purpose}.`} The task is performed by ${mdRefs(task.performerRoleRefs)}. It consumes ${mdRefs(task.inputWorkProductRefs)} and produces or updates ${mdRefs(task.outputWorkProductRefs)}.`, '');
    if (task.entryCriteria?.length) catalog.push(`**Entry.** ${mdSentences(task.entryCriteria)}`, '');
    if (task.steps?.length) {
      catalog.push('**Work.**', '');
      for (const step of task.steps) catalog.push(`- ${mdInline(step)}`);
      catalog.push('');
    }
    if (task.exitCriteria?.length) catalog.push(`**Exit.** ${mdSentences(task.exitCriteria)}`, '');
    if (task.validationRules?.length) catalog.push(`**Checks.** ${mdSentences(task.validationRules)}`, '');
    if (task.durationEstimate) catalog.push(`Indicative duration: ${mdInline(task.durationEstimate)}.`, '');
  }
}

catalog.push('## Work-product definitions', '');
for (const workProduct of index.workProductDefinitions) {
  catalog.push(`### ${mdInline(workProduct.name)} (\`${workProduct.id}\`)`, '');
  const description = mdInline(workProduct.description ?? workProduct.purpose ?? workProduct.name);
  catalog.push(`${description.endsWith('.') ? description : `${description}.`} Kind: ${mdInline(workProduct.workProductKind ?? 'Artifact')}. Provenance: ${(workProduct.sourceProcesses ?? []).map(source => sourceLabels[source] ?? source).join(', ')}.`, '');
}

catalog.push('## Guidance definitions', '');
for (const guidance of index.guidance) {
  catalog.push(`### ${mdInline(guidance.name)} (\`${guidance.id}\`)`, '');
  const guidanceText = mdInline(guidance.text ?? guidance.description ?? guidance.name);
  catalog.push(guidanceText.endsWith('.') ? guidanceText : `${guidanceText}.`, '');
  const appliesTo = guidance.appliesTo ? ` Applies to \`${mdInline(guidance.appliesTo)}\`.` : '';
  const pattern = guidance.sourcePatternId ? ` Pattern source: \`${mdInline(guidance.sourcePatternId)}\`.` : '';
  catalog.push(`Guidance kind: ${mdInline(guidance.guidanceKind ?? 'Practice guidance')}.${appliesTo}${pattern} Provenance: ${(guidance.sourceProcesses ?? []).map(source => sourceLabels[source] ?? source).join(', ')}.`, '');
}

fs.writeFileSync(path.join(libraryRoot, 'reusable-method-content-catalog.md'), `${catalog.join('\n')}\n`);

const escapeXml = value => String(value ?? '')
  .replaceAll('&', '&amp;')
  .replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;')
  .replaceAll('"', '&quot;')
  .replaceAll("'", '&apos;');
const xmiId = value => `id_${String(value).replace(/[^A-Za-z0-9_.-]/g, '_')}`;
const attr = (name, value) => value == null || value === '' ? '' : ` ${name}="${escapeXml(value)}"`;
const line = (depth, text) => `${'  '.repeat(depth)}${text}`;

const contentElementXml = (element, type, depth) => {
  const lines = [line(depth, `<ownedMethodContentMember xmi:type="spem:${type}" xmi:id="${xmiId(element.id)}" name="${escapeXml(element.name ?? element.title ?? element.id)}" modriss:sourceId="${escapeXml(element.id)}">`)];
  if (type === 'RoleDefinition') {
    for (const methodRoleId of element.methodRoleIds ?? (element.methodRoleId ? [element.methodRoleId] : [])) {
      lines.push(line(depth + 1, `<modriss:methodRoleId>${escapeXml(methodRoleId)}</modriss:methodRoleId>`));
    }
    for (const responsibility of element.responsibilities ?? []) {
      lines.push(line(depth + 1, `<modriss:responsibility>${escapeXml(responsibility)}</modriss:responsibility>`));
    }
  }
  if (type === 'WorkProductDefinition') {
    lines.push(line(depth + 1, `<modriss:workProductKind>${escapeXml(element.workProductKind ?? 'Artifact')}</modriss:workProductKind>`));
    if (element.methodWorkProductId) lines.push(line(depth + 1, `<modriss:methodWorkProductId>${escapeXml(element.methodWorkProductId)}</modriss:methodWorkProductId>`));
    if (element.description) lines.push(line(depth + 1, `<modriss:description>${escapeXml(element.description)}</modriss:description>`));
  }
  if (type === 'TaskDefinition') {
    if (element.purpose) lines.push(line(depth + 1, `<modriss:purpose>${escapeXml(element.purpose)}</modriss:purpose>`));
    for (const roleRef of element.performerRoleRefs ?? []) {
      lines.push(line(depth + 1, `<modriss:defaultPerformer roleDefinitionRef="${xmiId(roleRef)}"/>`));
    }
    for (const parameter of element.workProductParameters ?? []) {
      const optionality = parameter.optional ? 'optional' : 'mandatory';
      lines.push(line(depth + 1, `<ownedTaskDefinitionParameter xmi:type="spem:Default_TaskDefinitionParameter" xmi:id="${xmiId(`${element.id}.${parameter.direction}.${parameter.workProductDefinitionRef}`)}" direction="${escapeXml(parameter.direction)}" optionality="${optionality}" parameterType="${xmiId(parameter.workProductDefinitionRef)}"/>`));
    }
    for (const [stepIndex, step] of (element.steps ?? []).entries()) {
      lines.push(line(depth + 1, `<step xmi:type="spem:Step" xmi:id="${xmiId(`${element.id}.step.${stepIndex + 1}`)}" sectionName="Step ${stepIndex + 1}" sectionDescription="${escapeXml(step)}"/>`));
    }
    for (const binding of element.metamodelBindings ?? []) {
      lines.push(line(depth + 1, `<modriss:metamodelBinding metamodel="${escapeXml(binding.metamodel)}" classifier="${escapeXml(binding.classifier)}" kind="${escapeXml(binding.kind)}"/>`));
    }
  }
  if (type === 'Guidance') {
    lines.push(line(depth + 1, `<modriss:guidanceKind>${escapeXml(element.guidanceKind ?? element.kind ?? 'Guideline')}</modriss:guidanceKind>`));
    if (element.description) lines.push(line(depth + 1, `<modriss:description>${escapeXml(element.description)}</modriss:description>`));
    if (element.sourcePatternId) lines.push(line(depth + 1, `<modriss:sourcePatternId>${escapeXml(element.sourcePatternId)}</modriss:sourcePatternId>`));
    if (element.initialContext) lines.push(line(depth + 1, `<modriss:initialContext>${escapeXml(element.initialContext)}</modriss:initialContext>`));
    if (element.resultContext) lines.push(line(depth + 1, `<modriss:resultContext>${escapeXml(element.resultContext)}</modriss:resultContext>`));
    if (element.selection) lines.push(line(depth + 1, `<modriss:selectionRule>${escapeXml(element.selection)}</modriss:selectionRule>`));
    for (const role of element.roles ?? []) lines.push(line(depth + 1, `<modriss:methodRoleRef>${escapeXml(role)}</modriss:methodRoleRef>`));
    for (const workProduct of element.workProducts ?? []) lines.push(line(depth + 1, `<modriss:methodWorkProductRef>${escapeXml(workProduct)}</modriss:methodWorkProductRef>`));
    for (const requirement of element.requirements ?? []) lines.push(line(depth + 1, `<modriss:requirementRef>${escapeXml(requirement)}</modriss:requirementRef>`));
    for (const reference of element.references ?? []) lines.push(line(depth + 1, `<modriss:patternSource>${escapeXml(reference)}</modriss:patternSource>`));
  }
  for (const sourceProcess of element.sourceProcesses ?? []) {
    lines.push(line(depth + 1, `<modriss:sourceProcess>${escapeXml(sourceProcess)}</modriss:sourceProcess>`));
  }
  lines.push(line(depth, '</ownedMethodContentMember>'));
  return lines;
};

const taskUseXml = (taskUse, depth) => {
  const lines = [line(depth, `<nestedBreakdownElement xmi:type="spem:TaskUse" xmi:id="${xmiId(taskUse.id)}" name="${escapeXml(taskUse.id)}" task="${xmiId(taskUse.taskDefinitionRef)}" modriss:sourceId="${escapeXml(taskUse.id)}">`)];
  for (const parameter of taskUse.processParameters ?? []) {
    lines.push(line(depth + 1, `<ownedProcessParameter xmi:type="spem:ProcessParameter" xmi:id="${xmiId(parameter.id)}" direction="${escapeXml(parameter.direction)}" parameterType="${xmiId(parameter.workProductUseRef)}" modriss:optional="${parameter.optional ? 'true' : 'false'}"/>`));
  }
  lines.push(line(depth, '</nestedBreakdownElement>'));
  for (const performer of taskUse.processPerformers ?? []) {
    lines.push(line(depth, `<nestedBreakdownElement xmi:type="spem:ProcessPerformer" xmi:id="${xmiId(performer.id)}" linkedTaskUse="${xmiId(performer.taskUseRef)}" linkedRoleUse="${xmiId(performer.roleUseRef)}" modriss:kind="${escapeXml(performer.kind ?? 'primary')}"/>`));
  }
  return lines;
};

const activityXml = (activity, depth, kind) => {
  const lines = [line(depth, `<nestedBreakdownElement xmi:type="spem:Activity" xmi:id="${xmiId(activity.id)}" name="${escapeXml(activity.name ?? activity.id)}" modriss:kind="${escapeXml(kind)}" modriss:sourceId="${escapeXml(activity.id)}">`)];
  for (const taskUse of activity.taskUses ?? []) lines.push(...taskUseXml(taskUse, depth + 1));
  for (const subStage of activity.subStages ?? []) lines.push(...activityXml(subStage, depth + 1, 'MODRISS::SubStage'));
  for (const stage of activity.stages ?? []) lines.push(...activityXml(stage, depth + 1, 'MODRISS::Stage'));
  lines.push(line(depth, '</nestedBreakdownElement>'));
  return lines;
};

const processXml = (source, depth) => {
  const model = source.model;
  const rootId = xmiId(`${model.processId}.root`);
  const lines = [line(depth, `<ownedProcessMember xmi:type="spem:Activity" xmi:id="${rootId}" name="${escapeXml(model.displayName)}" modriss:sourceId="${escapeXml(model.processId)}" modriss:kind="MODRISS::DeliveryProcess">`)];
  for (const roleUse of model.roleUses ?? []) {
    lines.push(line(depth + 1, `<nestedBreakdownElement xmi:type="spem:RoleUse" xmi:id="${xmiId(roleUse.id)}" name="${escapeXml(roleUse.id)}" role="${xmiId(roleUse.roleDefinitionRef)}" modriss:activityRef="${xmiId(roleUse.activityRef)}"/>`));
  }
  for (const use of model.workProductUses ?? []) {
    lines.push(line(depth + 1, `<nestedBreakdownElement xmi:type="spem:WorkProductUse" xmi:id="${xmiId(use.id)}" name="${escapeXml(use.id)}" workProduct="${xmiId(use.workProductDefinitionRef)}" modriss:activityRef="${xmiId(use.activityRef)}" modriss:usage="${escapeXml(use.usage)}"/>`));
  }
  for (const phase of model.phases ?? []) lines.push(...activityXml(phase, depth + 1, 'Phase'));
  for (const repeatable of [model.processEngine?.iteration, model.processEngine?.releaseCycle].filter(Boolean)) {
    const activityRefs = (repeatable.activityRefs ?? []).map(xmiId).join(' ');
    lines.push(line(depth + 1, `<nestedBreakdownElement xmi:type="spem:Activity" xmi:id="${xmiId(repeatable.id)}" name="${escapeXml(repeatable.name)}" modriss:sourceId="${escapeXml(repeatable.id)}" modriss:kind="Iteration" modriss:isRepeatable="${Boolean(repeatable.isRepeatable)}"${attr('modriss:activityRefs', activityRefs)}${attr('modriss:repeatCondition', repeatable.repeatCondition)}${attr('modriss:exitCondition', repeatable.exitCondition)}${attr('modriss:guidance', repeatable.guidance)}/>`));
  }
  for (const sequence of model.workSequences ?? []) {
    lines.push(line(depth + 1, `<nestedBreakdownElement xmi:type="spem:WorkSequence" xmi:id="${xmiId(sequence.id)}" predecessor="${xmiId(sequence.predecessorRef)}" successor="${xmiId(sequence.successorRef)}" linkKind="${escapeXml(sequence.linkKind)}" modriss:relation="${escapeXml(sequence.relation ?? '')}"${attr('modriss:condition', sequence.condition)}${attr('modriss:guidance', sequence.guidance)}/>`));
  }
  for (const milestone of model.milestones ?? []) {
    lines.push(line(depth + 1, `<nestedBreakdownElement xmi:type="spem:Milestone" xmi:id="${xmiId(milestone.id)}" name="${escapeXml(milestone.name ?? milestone.id)}" modriss:sourceId="${escapeXml(milestone.id)}"/>`));
  }
  lines.push(line(depth, '</ownedProcessMember>'));
  return lines;
};

const xml = [
  '<?xml version="1.0" encoding="UTF-8"?>',
  '<xmi:XMI xmi:version="2.1"',
  '  xmlns:xmi="http://schema.omg.org/spec/XMI/2.1"',
  '  xmlns:spem="http://schema.omg.org/spec/SPEM/2.0/spem.xml"',
  '  xmlns:modriss="http://modriss.org/spec/method/1.0">',
  line(1, '<spem:MethodLibrary xmi:id="id_modriss.method.library" name="MODRISS Engineered Method Library">'),
  line(2, '<packagedElement xmi:type="spem:MethodPlugin" xmi:id="id_modriss.method.plugin.core" name="MODRISS Core" userChangeable="true">'),
];

xml.push(line(3, '<packagedElement xmi:type="spem:MethodContentPackage" xmi:id="id_package.content.canonical" name="MODRISS Consolidated Method Content">'));
for (const [type, items] of [
  ['RoleDefinition', index.roleDefinitions],
  ['TaskDefinition', index.taskDefinitions],
  ['WorkProductDefinition', index.workProductDefinitions],
  ['Guidance', index.guidance],
]) {
  for (const item of items) xml.push(...contentElementXml(item, type, 4));
}
xml.push(line(3, '</packagedElement>'));

xml.push(line(3, '<packagedElement xmi:type="spem:ProcessPackage" xmi:id="id_package.processes" name="MODRISS Delivery Processes">'));
for (const source of processSources) xml.push(...processXml(source, 4));
xml.push(line(3, '</packagedElement>'));
xml.push(line(2, '</packagedElement>'));
for (const [id, name] of [
  ['exploration', 'Exploration'],
  ['standard', 'Standard Product Delivery'],
  ['multi-team', 'Multi-Team Product and Platform'],
  ['regulated', 'Regulated or High-Criticality'],
]) {
  xml.push(line(2, `<packagedElement xmi:type="spem:MethodConfiguration" xmi:id="${xmiId(`configuration.${id}`)}" name="${escapeXml(name)}" methodPluginSelection="id_modriss.method.plugin.core" modriss:profileRef="${escapeXml(id)}"/>`));
}
xml.push(line(1, '</spem:MethodLibrary>'));
xml.push('</xmi:XMI>');

fs.writeFileSync(path.join(spemRoot, 'modriss-method-library.spem.xml'), `${xml.join('\n')}\n`);

const counts = {
  roles: index.roleDefinitions.length,
  tasks: index.taskDefinitions.length,
  workProducts: index.workProductDefinitions.length,
  guidance: index.guidance.length,
  processComponents: index.processComponents.length,
  sourceInventoryRows: inventoryRows.length - 1,
};
console.log(JSON.stringify(counts, null, 2));
