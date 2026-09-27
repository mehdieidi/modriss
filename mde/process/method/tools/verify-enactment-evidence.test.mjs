import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { inspectHandoff } from './verify-enactment-evidence.mjs';

const fixture = JSON.parse(fs.readFileSync('mde/process/method/evaluation/returnflow/handoff-fixture.json', 'utf8'));
test('explicit hypothetical handoff with accepted funded archive custody is consistent', () => {
  assert.deepEqual(inspectHandoff(fixture), []);
});
const counterexamples = [
  ['fiction becomes empirical evidence', r => { r.evidenceKind = 'empirically-observed'; }, /Simulated evidence/],
  ['old candidate report authorizes new code', r => { r.revision.code = 'new-code'; }, /Stale evidence/],
  ['gate has a reference but no artifact', r => { r.evidence[0].path = 'missing-returnflow-evidence'; }, /Missing evidence file/],
  ['report omits changed scope', r => { r.scope.push('RF-06'); }, /Uncovered scope/],
  ['risk exception expires before review', r => { r.gate.exceptions.push({ owner: 'Farah', acceptedBy: 'Ari', treatment: 'monitor', expiry: '2026-09-26' }); }, /Expired/],
  ['known blocker is ignored', r => { r.gate.openBlockers.push('duplicate refund'); }, /Open blockers/],
  ['first traffic has no service owner', r => { r.gate.promotion.serviceOwner = ''; }, /Missing promotion serviceOwner/],
  ['first release promises nonexistent rollback', r => { r.gate.promotion.recovery = 'Restore previous accepted baseline'; }, /First release/],
  ['small modeled alarm change is misrouted', r => { r.change.disposition = 'operations-only'; }, /controlled delivery/],
  ['temporary production drift closes unreconciled', r => { r.change.emergencyDrift = true; r.change.reconciled = false; }, /Emergency drift/],
  ['G8 hides one live release', r => { r.closure.liveReleaseCount = 1; }, /liveReleaseCount/],
  ['archive survives without funded ownership', r => { r.closure.retainedAssets[0].fundedBudgetOwner = ''; }, /Incomplete custody/],
];
for (const [name, mutate, expected] of counterexamples) test(name, () => {
  const record = structuredClone(fixture);
  mutate(record);
  assert.match(inspectHandoff(record).join('\n'), expected);
});
test('Eidi assessment preserves all groups and Simple types', () => {
  const { criteria } = JSON.parse(fs.readFileSync('mde/process/method/evaluation/returnflow/eidi-assessment.json', 'utf8'));
  assert.deepEqual([0, 1, 2].map(g => criteria.filter(c => c.group === g).length), [20, 24, 34]);
  assert.equal(new Set(criteria.map(c => c.id)).size, 78);
  const simple = criteria.filter(c => c.type === 'Simple');
  assert.deepEqual(simple.map(c => c.criterion), ['User Involvement Support', 'Definition of Modeling Boundaries']);
  assert.ok(simple.every(c => ['Yes', 'No'].includes(c.design) && ['Yes', 'No'].includes(c.realization)));
  assert.ok(criteria.every(c => c.empiricalEffectiveness === 'not evaluated'));
});
