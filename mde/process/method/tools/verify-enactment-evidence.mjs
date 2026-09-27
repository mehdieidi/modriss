import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Research record checks only. This is not a gate engine, EMF validator,
// evidence authenticity checker, or authorization to deploy a release.
export function inspectHandoff(record, root = process.cwd()) {
  const errors = [];
  const require = (condition, message) => { if (!condition) errors.push(message); };
  const nonempty = value => typeof value === 'string' && value.trim().length > 0;
  const kinds = new Set(['planned', 'simulated', 'repository-checked', 'empirically-observed']);
  require(kinds.has(record.evidenceKind), 'Unknown evidence kind');
  const scope = record.scope ?? [];
  require(scope.length > 0, 'Missing scope');
  const revisionKeys = ['method', 'cim', 'pim', 'psm', 'transform', 'generator', 'code', 'configuration', 'candidate'];
  for (const key of revisionKeys) require(nonempty(record.revision?.[key]), `Missing revision: ${key}`);
  const evidence = new Map();
  for (const item of record.evidence ?? []) {
    require(!evidence.has(item.id), `Duplicate evidence: ${item.id}`);
    evidence.set(item.id, item);
    require(kinds.has(item.kind), `Unknown evidence kind: ${item.id}`);
    const resolved = path.resolve(root, item.path ?? '');
    const relative = path.relative(root, resolved);
    require(relative !== '..' && !relative.startsWith(`..${path.sep}`) && !path.isAbsolute(relative), `Evidence outside repository: ${item.id}`);
    require(fs.existsSync(resolved) && fs.statSync(resolved).isFile(), `Missing evidence file: ${item.id}`);
  }
  const gate = record.gate ?? {};
  require(nonempty(gate.authority), 'Missing gate authority');
  require(/^\d{4}-\d{2}-\d{2}$/.test(gate.reviewDate ?? '') && Number.isFinite(Date.parse(gate.reviewDate)), 'Missing review date');
  require(['accept', 'exception', 'rework', 'defer', 'stop', 'blocked'].includes(gate.decision), 'Unknown gate decision');
  if (['accept', 'exception'].includes(gate.decision)) {
    require((gate.evidenceIds ?? []).length > 0, 'Acceptance needs evidence');
    require((gate.openBlockers ?? []).length === 0, 'Open blockers prevent acceptance');
    const covered = new Set();
    for (const id of gate.evidenceIds ?? []) {
      const item = evidence.get(id);
      require(Boolean(item), `Unknown evidence reference: ${id}`);
      if (!item) continue;
      require(item.kind !== 'planned', `Planned evidence cannot support acceptance: ${id}`);
      if (record.evidenceKind !== 'simulated') require(item.kind !== 'simulated', `Simulated evidence cannot support an observed acceptance: ${id}`);
      if (record.evidenceKind === 'empirically-observed') require(item.kind === 'empirically-observed', `Empirical acceptance needs observed evidence: ${id}`);
      for (const key of revisionKeys) require(item.revision?.[key] === record.revision?.[key], `Stale evidence ${id}: ${key}`);
      for (const requirement of item.scope ?? []) covered.add(requirement);
    }
    for (const requirement of scope) require(covered.has(requirement), `Uncovered scope: ${requirement}`);
    for (const exception of gate.exceptions ?? []) {
      require(nonempty(exception.owner) && nonempty(exception.acceptedBy) && nonempty(exception.treatment), 'Incomplete exception');
      require(Number.isFinite(Date.parse(exception.expiry)) && Date.parse(exception.expiry) >= Date.parse(gate.reviewDate), 'Expired or undated exception');
    }
    if (gate.id === 'G6') {
      for (const field of ['operator', 'serviceOwner', 'responseCoverage', 'recovery']) require(nonempty(gate.promotion?.[field]), `Missing promotion ${field}`);
      if (!gate.promotion?.previousLiveCandidate) require(!/^restore previous/i.test(gate.promotion?.recovery ?? ''), 'First release has no previous baseline');
    }
  }
  const change = record.change ?? {};
  if (['CIM', 'PIM', 'PSM', 'generator', 'code'].includes(change.authoritativeOwner)) require(change.disposition !== 'operations-only', 'Model/product-owned change needs controlled delivery');
  if (change.state === 'closed') require(!change.emergencyDrift || change.reconciled, 'Emergency drift prevents closure');
  const closure = record.closure ?? {};
  if (closure.decision === 'accept') {
    for (const field of ['liveReleaseCount', 'liveAccessPaths', 'unassignedChargeableResources']) require(closure[field] === 0, `G8 requires zero ${field}`);
    require(Array.isArray(closure.openObligations) && closure.openObligations.length === 0, 'Open obligations prevent G8');
    for (const asset of closure.retainedAssets ?? []) {
      for (const field of ['id', 'custodian', 'acceptance', 'accessPolicy', 'fundedBudgetOwner', 'expiryPolicy', 'deletionOwner', 'verification']) require(nonempty(asset[field]), `Incomplete custody: ${field}`);
    }
  }
  return errors;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const file = process.argv[2] ?? 'mde/process/method/evaluation/returnflow/handoff-fixture.json';
  const record = JSON.parse(fs.readFileSync(file, 'utf8'));
  const errors = inspectHandoff(record);
  console.log(JSON.stringify({ caseId: record.caseId, evidenceKind: record.evidenceKind, status: errors.length ? 'invalid' : 'record-consistent', errors, limitation: 'Checks recorded contracts; does not execute the project or authorize real gates.' }, null, 2));
  if (errors.length) process.exitCode = 1;
}
