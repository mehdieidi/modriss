/**
 * Walk SPEM process trees: Phase → Stage → (subStage)* → Task.
 */

/** @param {import('./process-types.mjs').StageSpec[]} stages */
export function flattenStages(stages) {
  /** @type {import('./process-types.mjs').StageSpec[]} */
  const out = [];
  for (const stage of stages || []) {
    if (stage.subStages?.length) {
      out.push(...flattenStages(stage.subStages));
    } else {
      out.push(stage);
    }
  }
  return out;
}

/** @param {import('./process-types.mjs').ProcessPhaseSpec[]} phases */
export function collectAllTasks(phases) {
  /** @type {Array<{ task: import('./process-types.mjs').TaskSpec, phaseId: string, stageId: string, stagePath: string[] }>} */
  const out = [];
  for (const phase of phases || []) {
    walkStages(phase.stages || [], phase.id, [], out);
  }
  return out;
}

/**
 * @param {import('./process-types.mjs').StageSpec[]} stages
 * @param {string} phaseId
 * @param {string[]} ancestors
 * @param {Array<{ task: import('./process-types.mjs').TaskSpec, phaseId: string, stageId: string, stagePath: string[] }>} out
 */
function walkStages(stages, phaseId, ancestors, out) {
  for (const stage of stages || []) {
    const path = [...ancestors, stage.id];
    if (stage.subStages?.length) {
      walkStages(stage.subStages, phaseId, path, out);
    }
    for (const task of stage.tasks || []) {
      out.push({ task, phaseId, stageId: stage.id, stagePath: path });
    }
  }
}

/** @param {import('./process-types.mjs').ProcessPhaseSpec[]} phases */
export function countTasks(phases) {
  return collectAllTasks(phases).length;
}

/**
 * @param {import('./process-types.mjs').ProcessPhaseSpec[]} phases
 * @param {string} taskId
 */
export function findTaskContext(phases, taskId) {
  for (const entry of collectAllTasks(phases)) {
    if (entry.task.id === taskId) return entry;
  }
  return null;
}

/**
 * @param {import('./process-types.mjs').ProcessPhaseSpec[]} phases
 * @param {string} stageId
 */
export function findStage(phases, stageId) {
  for (const phase of phases || []) {
    const found = findStageInList(phase.stages || [], stageId);
    if (found) return { phase, stage: found };
  }
  return null;
}

/** @param {import('./process-types.mjs').StageSpec[]} stages @param {string} stageId */
function findStageInList(stages, stageId) {
  for (const stage of stages || []) {
    if (stage.id === stageId) return stage;
    if (stage.subStages?.length) {
      const nested = findStageInList(stage.subStages, stageId);
      if (nested) return nested;
    }
  }
  return null;
}
