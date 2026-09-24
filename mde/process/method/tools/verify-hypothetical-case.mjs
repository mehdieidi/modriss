#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const processRoot = path.join(root, 'mde', 'process', 'definitions');
const caseRoot = path.join(root, 'mde', 'process', 'method', 'evaluation', 'coldchain-sentinel');
const processNames = ['end-to-end', 'cim', 'pim', 'psm', 'artifact'];
const definitions = processNames.map(name => JSON.parse(fs.readFileSync(path.join(processRoot, `${name}.json`), 'utf8')));
const summary = JSON.parse(fs.readFileSync(path.join(caseRoot, 'coverage-summary.json'), 'utf8'));
const readCsvIds = (file, index) => fs.readFileSync(path.join(caseRoot, file), 'utf8').trim().split(/\r?\n/).slice(1).map(line => {
  const cells = [];
  let value = '';
  let quoted = false;
  for (let i = 0; i < line.length; i++) {
    const char = line[i];
    if (char === '"' && quoted && line[i + 1] === '"') { value += '"'; i++; }
    else if (char === '"') quoted = !quoted;
    else if (char === ',' && !quoted) { cells.push(value); value = ''; }
    else value += char;
  }
  cells.push(value);
  return cells[index];
});
const taskIds = new Set(readCsvIds('task-enactment-ledger.csv', 1));
const roleIds = new Set(readCsvIds('role-enactment-ledger.csv', 0));
const workProductIds = new Set(readCsvIds('work-product-enactment-ledger.csv', 0));
const gateIds = new Set(readCsvIds('gate-decision-ledger.csv', 0));
const expectedTasks = new Set(definitions.flatMap(definition => definition.methodContent.taskDefinitions.map(task => task.id)));
const expectedRoles = new Set(definitions.flatMap(definition => definition.methodContent.roleDefinitions.map(role => role.id)));
const expectedWorkProducts = new Set(definitions.flatMap(definition => definition.methodContent.workProductDefinitions.map(workProduct => workProduct.id)));
const expectedGates = new Set(definitions.find(definition => definition.level === 'end-to-end').milestones.map(gate => gate.gateId));
const assertSet = (label, expected, actual) => {
  const missing = [...expected].filter(id => !actual.has(id));
  const unexpected = [...actual].filter(id => !expected.has(id));
  if (missing.length || unexpected.length) throw new Error(`${label} mismatch; missing=${missing.join('|')} unexpected=${unexpected.join('|')}`);
};
assertSet('tasks', expectedTasks, taskIds);
assertSet('roles', expectedRoles, roleIds);
assertSet('work products', expectedWorkProducts, workProductIds);
assertSet('gates', expectedGates, gateIds);
for (const definition of definitions) {
  const produced = new Set(definition.methodContent.taskDefinitions.flatMap(task => task.outputWorkProductRefs ?? []));
  const unproduced = definition.methodContent.workProductDefinitions.map(workProduct => workProduct.id).filter(id => !produced.has(id));
  if (unproduced.length) throw new Error(`${definition.processId} has work products without a producing task: ${unproduced.join('|')}`);
  for (const task of definition.methodContent.taskDefinitions) {
    const performers = task.performerRoleRefs ?? [];
    if (!task.primaryPerformerRoleRef || !performers.includes(task.primaryPerformerRoleRef)) {
      throw new Error(`${task.id} does not expose its primary performer through performerRoleRefs`);
    }
  }
}
if (summary.counts.boundRoles !== summary.counts.roles) throw new Error('Every role must be bound to at least one enacted task');
if (summary.counts.gates !== 9) throw new Error(`Expected G0-G8; found ${summary.counts.gates} gates`);
console.log(JSON.stringify({ status: 'ok', ...summary.counts }, null, 2));
