/**
 * Walk the MODRISS process projection: Phase → Stage → (subStage)* → TaskUse.
 *
 * Source specifications still use a compact `tasks` authoring form. Generated
 * process definitions expose the SPEM-aligned `taskUses` form and keep method
 * content in `methodContent.taskDefinitions`. The helpers below understand
 * both forms so the coverage and documentation generators can consume either
 * authoring or compiled definitions.
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
 * Resolve a generated TaskUse to the task definition it references.
 *
 * @param {object} process
 * @param {object} taskUse
 * @returns {object|null}
 */
export function resolveTaskUse(process, taskUse) {
  const definitions = process?.methodContent?.taskDefinitions || [];
  const definition = definitions.find((item) => item.id === taskUse?.taskDefinitionRef);
  if (!definition) return null;

  const workProducts = new Map(
    (process.methodContent?.workProductDefinitions || []).map((item) => [item.id, item]),
  );
  const inputRefs = taskUse.inputWorkProductUseRefs || [];
  const outputRefs = taskUse.outputWorkProductUseRefs || [];
  const inputUses = (process.workProductUses || []).filter((item) => inputRefs.includes(item.id));
  const outputUses = (process.workProductUses || []).filter((item) =>
    outputRefs.includes(item.id),
  );

  return {
    ...definition,
    id: taskUse.id,
    taskDefinitionRef: taskUse.taskDefinitionRef,
    performerRoleUseRefs: taskUse.performerRoleUseRefs || [],
    processPerformers: taskUse.processPerformers || [],
    inputWorkProductUseRefs: inputRefs,
    outputWorkProductUseRefs: outputRefs,
    processParameters: taskUse.processParameters || [],
    inputArtifacts: inputUses
      .map((item) => workProducts.get(item.workProductDefinitionRef))
      .filter(Boolean)
      .map(({ id, name, description }) => ({ id, name, description })),
    artifacts: outputUses
      .map((item) => workProducts.get(item.workProductDefinitionRef))
      .filter(Boolean)
      .map(({ id, name, description }) => ({ id, name, description })),
    paletteFocus: (definition.metamodelBindings || [])
      .filter((binding) => binding.kind === "EClass")
      .map((binding) => binding.classifier)
      .slice(0, 12),
  };
}

/**
 * Collect generated TaskUses with their method definitions resolved.
 *
 * @param {object} process
 * @returns {Array<{ task: object, phaseId: string, stageId: string, stagePath: string[] }>}
 */
export function collectProcessTasks(process) {
  const out = [];
  for (const phase of process?.phases || []) {
    walkProcessStages(phase.stages || [], phase.id, [], process, out);
  }
  for (const component of process?.processComponents || []) {
    walkProcessStages(component.activities || [], component.id, [], process, out);
  }
  return out;
}

function walkProcessStages(stages, phaseId, ancestors, process, out) {
  for (const stage of stages || []) {
    const path = [...ancestors, stage.id];
    if (stage.subStages?.length) {
      walkProcessStages(stage.subStages, phaseId, path, process, out);
    }
    for (const taskUse of stage.taskUses || []) {
      const task = resolveTaskUse(process, taskUse);
      if (task) out.push({ task, phaseId, stageId: stage.id, stagePath: path });
    }
    // This fallback keeps the helper useful for source specifications and
    // hand-authored legacy definitions during the migration period.
    for (const task of stage.tasks || []) {
      out.push({ task, phaseId, stageId: stage.id, stagePath: path });
    }
  }
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
