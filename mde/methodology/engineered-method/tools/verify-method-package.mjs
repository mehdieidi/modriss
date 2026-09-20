import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const packageRoot = path.join(root, 'mde', 'methodology', 'engineered-method');
const readJson = file => JSON.parse(fs.readFileSync(file, 'utf8'));
const fail = message => {
  throw new Error(message);
};

const index = readJson(path.join(packageRoot, 'spem', 'method-content-index.json'));
const core = readJson(path.join(packageRoot, 'method-library', 'core-method-content.json'));
const fragments = readJson(path.join(packageRoot, 'method-library', 'method-fragments.json'));
const xml = fs.readFileSync(path.join(packageRoot, 'spem', 'modriss-method-library.spem.xml'), 'utf8');

const groups = [
  ['roleDefinitions', index.roleDefinitions],
  ['taskDefinitions', index.taskDefinitions],
  ['workProductDefinitions', index.workProductDefinitions],
  ['guidance', index.guidance],
];
for (const [name, items] of groups) {
  const ids = items.map(item => item.id);
  if (new Set(ids).size !== ids.length) fail(`Duplicate ${name} IDs in consolidated index`);
}

const xmiIds = [...xml.matchAll(/\bxmi:id="([^"]+)"/g)].map(match => match[1]);
const xmiIdSet = new Set(xmiIds);
if (xmiIdSet.size !== xmiIds.length) {
  const seen = new Set();
  const duplicates = [...new Set(xmiIds.filter(id => seen.has(id) || !seen.add(id)))];
  fail(`Duplicate xmi:id values: ${duplicates.join(', ')}`);
}

const referenceAttributes = [
  'task',
  'role',
  'workProduct',
  'predecessor',
  'successor',
  'linkedTaskUse',
  'linkedRoleUse',
  'parameterType',
  'roleDefinitionRef',
  'methodPluginSelection',
];
for (const attribute of referenceAttributes) {
  const pattern = new RegExp(`\\b${attribute}="([^"]+)"`, 'g');
  for (const match of xml.matchAll(pattern)) {
    if (!xmiIdSet.has(match[1])) fail(`Unresolved ${attribute} reference: ${match[1]}`);
  }
}

const coreWorkProducts = core.methodContent?.workProductDefinitions ?? [];
const methodIds = coreWorkProducts.map(item => item.methodWorkProductId);
if (coreWorkProducts.length !== 29 || new Set(methodIds).size !== 29) {
  fail('Engineered core must contain exactly 29 uniquely identified lifecycle work products');
}
if ((fragments.fragments ?? []).length !== 18) fail('Expected 17 lifecycle fragments and one continuous fragment');

const methodRoleMappings = index.methodRoleMappings ?? [];
if (methodRoleMappings.length !== 16 || new Set(methodRoleMappings.map(item => item.methodRoleId)).size !== 16) {
  fail('Expected 16 unique conceptual role mappings');
}
const roleIds = new Set(index.roleDefinitions.map(item => item.id));
for (const mapping of methodRoleMappings) {
  for (const roleRef of mapping.roleDefinitionRefs ?? []) {
    if (!roleIds.has(roleRef)) fail(`Unresolved conceptual role mapping: ${mapping.methodRoleId} -> ${roleRef}`);
  }
}

const patternGuidance = index.guidance.filter(item => item.sourcePatternId);
if (patternGuidance.length !== fragments.fragments.length) {
  fail('Every method fragment must appear as Guidance in the consolidated method content');
}
if ((index.processComponents ?? []).length !== 5) fail('Expected five assembled process components');

console.log(JSON.stringify({
  status: 'ok',
  uniqueXmiIds: xmiIds.length,
  roles: index.roleDefinitions.length,
  tasks: index.taskDefinitions.length,
  workProducts: index.workProductDefinitions.length,
  guidance: index.guidance.length,
  processComponents: index.processComponents.length,
  processPatterns: patternGuidance.length,
  conceptualRoleMappings: methodRoleMappings.length,
}, null, 2));
