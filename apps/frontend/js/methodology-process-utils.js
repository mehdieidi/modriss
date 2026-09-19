/**
 * Resolve the SPEM-aligned process projection for the methodology UI.
 * Process activities contain TaskUses; reusable task content lives in the
 * process definition's methodContent package.
 */

export function tasksForStage(process, stage) {
  if (!stage) return [];
  if (stage.taskUses?.length) {
    const definitions = new Map(
      (process?.methodContent?.taskDefinitions || []).map((task) => [task.id, task]),
    );
    const workProducts = new Map(
      (process?.methodContent?.workProductDefinitions || []).map((product) => [
        product.id,
        product,
      ]),
    );
    return stage.taskUses
      .map((taskUse) => {
        const definition = definitions.get(taskUse.taskDefinitionRef);
        if (!definition) return null;
        const inputUses = new Set(taskUse.inputWorkProductUseRefs || []);
        const outputUses = new Set(taskUse.outputWorkProductUseRefs || []);
        const inputArtifacts = (process?.workProductUses || [])
          .filter((use) => inputUses.has(use.id))
          .map((use) => workProducts.get(use.workProductDefinitionRef))
          .filter(Boolean);
        const artifacts = (process?.workProductUses || [])
          .filter((use) => outputUses.has(use.id))
          .map((use) => workProducts.get(use.workProductDefinitionRef))
          .filter(Boolean);
        return {
          ...definition,
          id: taskUse.id,
          taskDefinitionRef: taskUse.taskDefinitionRef,
          performerRoleUseRefs: taskUse.performerRoleUseRefs || [],
          inputWorkProductUseRefs: taskUse.inputWorkProductUseRefs || [],
          outputWorkProductUseRefs: taskUse.outputWorkProductUseRefs || [],
          inputArtifacts,
          artifacts,
          paletteFocus:
            definition.paletteFocus ||
            (definition.metamodelBindings || [])
              .filter((binding) => binding.kind === "EClass")
              .map((binding) => binding.classifier)
              .slice(0, 12),
        };
      })
      .filter(Boolean);
  }
  return stage.tasks || [];
}

export function roleForTask(process, task, stage, phase) {
  const roleUseId =
    task?.performerRoleUseRefs?.[0] || stage?.roleUseRefs?.[0] || phase?.roleUseRefs?.[0];
  const roleUse = (process?.roleUses || []).find((item) => item.id === roleUseId);
  const roleId = roleUse?.roleDefinitionRef;
  return (process?.methodContent?.roleDefinitions || []).find((role) => role.id === roleId) || null;
}

export function guidanceForProcess(process) {
  return process?.methodContent?.guidance || process?.guidelines || [];
}
