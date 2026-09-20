/**
 * Attach the explicitly authored input WorkProductDefinition identifiers to
 * every task in a compact process specification.
 *
 * A process specification is a method-authoring artifact, not a trace emitted
 * by a previous compiler pass.  Requiring a declaration for every task keeps
 * the method semantics stable when task order, grouping, or stage boundaries
 * change.  An empty array is a valid declaration for a task with no required
 * input work products.
 *
 * @param {import('./process-types.mjs').ProcessPhaseSpec[]} phases
 * @param {Record<string, string[]>} inputArtifactIdsByTaskId
 * @param {string} sourceName
 * @returns {import('./process-types.mjs').ProcessPhaseSpec[]}
 */
export function applyDeclaredTaskInputs(phases, inputArtifactIdsByTaskId, sourceName) {
  const seenTaskIds = new Set();
  const visitStages = (stages = []) =>
    stages.map((stage) => ({
      ...stage,
      tasks: (stage.tasks || []).map((task) => {
        if (!Object.prototype.hasOwnProperty.call(inputArtifactIdsByTaskId, task.id)) {
          throw new Error(
            `${sourceName}: task ${task.id} must explicitly declare inputArtifactIds; use [] when it has no required inputs`,
          );
        }
        const inputArtifactIds = inputArtifactIdsByTaskId[task.id];
        seenTaskIds.add(task.id);
        if (!Array.isArray(inputArtifactIds)) {
          throw new Error(`${sourceName}: task ${task.id} inputArtifactIds must be an array`);
        }
        return {
          ...task,
          inputArtifactIds: [...new Set(inputArtifactIds)],
        };
      }),
      subStages: stage.subStages ? visitStages(stage.subStages) : stage.subStages,
    }));

  const result = (phases || []).map((phase) => ({
    ...phase,
    stages: visitStages(phase.stages),
  }));
  const unknownDeclarations = Object.keys(inputArtifactIdsByTaskId).filter(
    (taskId) => !seenTaskIds.has(taskId),
  );
  if (unknownDeclarations.length) {
    throw new Error(`${sourceName}: input declarations have no matching task: ${unknownDeclarations.join(", ")}`);
  }
  return result;
}
