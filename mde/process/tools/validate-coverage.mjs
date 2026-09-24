#!/usr/bin/env node
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parseEcore, allConcepts } from "./lib/ecore-parser.mjs";

const ROOT = join(import.meta.dirname, "..");
const METAMODEL_ROOT = join(ROOT, "..", "metamodels");
const COV_DIR = join(ROOT, "coverage-matrix");
const PROC_DIR = join(ROOT, "definitions");

let failed = false;

function collectStages(phases) {
  const stages = new Map();
  const visit = (stage, phaseId) => {
    stages.set(stage.id, { ...stage, phaseId });
    for (const child of stage.subStages || []) visit(child, phaseId);
  };
  for (const phase of phases || []) {
    for (const stage of phase.stages || []) visit(stage, phase.id);
  }
  return stages;
}

function validateProcess(process, fileName, metamodelClassifiers = null) {
  const methodContent = process.methodContent || {};
  const errors = [];
  if (process.type !== "Process") errors.push("root is not typed as Process");
  if (methodContent.type !== "MethodContentPackage") {
    errors.push("methodContent is not typed as MethodContentPackage");
  }
  if (process.conformance !== "mapped") errors.push("process must declare mapped SPEM conformance");
  const requiredMappingScope = new Set([
    "Core",
    "ManagedContent",
    "MethodContent",
    "ProcessStructure",
    "ProcessWithMethods",
  ]);
  for (const scope of requiredMappingScope) {
    if (!(process.mappingScope || []).includes(scope)) {
      errors.push(`process mappingScope is missing ${scope}`);
    }
  }
  const roleIds = new Set((methodContent.roleDefinitions || process.roles || []).map((role) => role.id));
  const taskDefinitions = new Map(
    (methodContent.taskDefinitions || []).map((task) => [task.id, task]),
  );
  const workProductIds = new Set(
    (methodContent.workProductDefinitions || process.artifactKinds || []).map(
      (workProduct) => workProduct.id,
    ),
  );
  const roleUses = new Map((process.roleUses || []).map((roleUse) => [roleUse.id, roleUse]));
  const workProductUses = new Map(
    (process.workProductUses || []).map((workProductUse) => [workProductUse.id, workProductUse]),
  );
  const phaseIds = new Set((process.phases || []).map((phase) => phase.id));
  const stages = collectStages(process.phases);
  const taskIds = new Set();
  const nodeIds = new Set([...phaseIds, ...stages.keys()]);
  const activities = new Map();
  const taskUseContexts = new Map();
  const referencedTaskDefinitions = new Set();
  const referencedWorkProductUses = new Set();
  const canonicalMethodIds = new Map();
  const canonicalProcessIds = new Map();
  const registerUnique = (values, context, registry) => {
    const local = new Set();
    for (const value of values || []) {
      if (!value?.id) continue;
      if (local.has(value.id)) {
        errors.push(`duplicate id ${value.id} within ${context}`);
      }
      if (registry.has(value.id)) {
        errors.push(`duplicate id ${value.id} (${registry.get(value.id)} and ${context})`);
      }
      local.add(value.id);
      registry.set(value.id, context);
    }
  };
  registerUnique(methodContent.roleDefinitions, "methodContent.roleDefinitions", canonicalMethodIds);
  registerUnique(methodContent.taskDefinitions, "methodContent.taskDefinitions", canonicalMethodIds);
  registerUnique(methodContent.workProductDefinitions, "methodContent.workProductDefinitions", canonicalMethodIds);
  registerUnique(methodContent.guidance, "methodContent.guidance", canonicalMethodIds);
  registerUnique(process.roleUses, "process.roleUses", canonicalProcessIds);
  registerUnique(process.workProductUses, "process.workProductUses", canonicalProcessIds);
  registerUnique(process.workSequences, "process.workSequences", canonicalProcessIds);
  registerUnique(process.changeManagement?.workflows, "process.changeManagement.workflows", canonicalProcessIds);
  registerUnique(process.milestones, "process.milestones", canonicalProcessIds);
  registerUnique(process.processEngine?.cycle, "process.processEngine.cycle", new Map());
  registerUnique(process.processEngine?.reworkLoops, "process.processEngine.reworkLoops", new Map());

  const registerActivity = (activity) => {
    activities.set(activity.id, activity);
    nodeIds.add(activity.id);
  };
  const taskUses = [];
  const registerActivityTree = (activity, childProperty = "subStages") => {
    registerActivity(activity);
    for (const taskUse of activity.taskUses || []) {
      taskUses.push(taskUse);
      taskUseContexts.set(taskUse.id, activity.id);
    }
    for (const child of activity[childProperty] || []) registerActivityTree(child, "subStages");
  };
  for (const phase of process.phases || []) {
    registerActivity(phase);
    for (const stage of phase.stages || []) {
      registerActivityTree(stage);
    }
  }
  for (const component of process.processComponents || []) {
    registerActivity(component);
    for (const activity of component.activities || []) registerActivityTree(activity);
  }
  registerUnique([...activities.values()], "process.activities", canonicalProcessIds);
  registerUnique(taskUses, "process.taskUses", canonicalProcessIds);
  registerUnique(
    taskUses.flatMap((taskUse) => taskUse.processParameters || []),
    "process.processParameters",
    canonicalProcessIds,
  );
  registerUnique(
    taskUses.flatMap((taskUse) => taskUse.processPerformers || []),
    "process.processPerformers",
    canonicalProcessIds,
  );

  for (const taskDefinition of methodContent.taskDefinitions || []) {
    if (taskDefinition.inputSource !== "declared") {
      errors.push(`task definition ${taskDefinition.id} does not identify declared input bindings`);
    }
    if (!Array.isArray(taskDefinition.inputWorkProductRefs)) {
      errors.push(`task definition ${taskDefinition.id} is missing inputWorkProductRefs`);
    }
    const parameters = taskDefinition.workProductParameters || [];
    const parameterKeys = new Set();
    for (const parameter of parameters) {
      if (parameter.type !== "Default_TaskDefinitionParameter") {
        errors.push(`task definition ${taskDefinition.id} parameter is not a Default_TaskDefinitionParameter`);
      }
      if (!workProductIds.has(parameter.workProductDefinitionRef)) {
        errors.push(
          `task definition ${taskDefinition.id} parameter references unknown work product ${parameter.workProductDefinitionRef}`,
        );
      }
      if (!["in", "out", "inout"].includes(parameter.direction)) {
        errors.push(`task definition ${taskDefinition.id} has invalid parameter direction ${parameter.direction}`);
      }
      const key = `${parameter.direction}:${parameter.workProductDefinitionRef}`;
      if (parameterKeys.has(key)) {
        errors.push(`task definition ${taskDefinition.id} repeats parameter ${key}`);
      }
      parameterKeys.add(key);
    }
    for (const workProductId of taskDefinition.inputWorkProductRefs || []) {
      if (!parameters.some((parameter) => parameter.direction !== "out" && parameter.workProductDefinitionRef === workProductId)) {
        errors.push(`task definition ${taskDefinition.id} input ${workProductId} is missing an input parameter`);
      }
    }
    for (const workProductId of taskDefinition.outputWorkProductRefs || []) {
      if (!parameters.some((parameter) => parameter.direction !== "in" && parameter.workProductDefinitionRef === workProductId)) {
        errors.push(`task definition ${taskDefinition.id} output ${workProductId} is missing an output parameter`);
      }
    }
    for (const roleId of taskDefinition.performerRoleRefs || []) {
      if (!roleIds.has(roleId)) {
        errors.push(`task definition ${taskDefinition.id} uses unknown role ${roleId}`);
      }
    }
    for (const workProductId of taskDefinition.outputWorkProductRefs || []) {
      if (!workProductIds.has(workProductId)) {
        errors.push(`task definition ${taskDefinition.id} uses unknown work product ${workProductId}`);
      }
    }
    for (const binding of taskDefinition.metamodelBindings || []) {
      if (!binding.metamodel || !binding.classifier || !binding.kind) {
        errors.push(`task definition ${taskDefinition.id} has an incomplete metamodel binding`);
      }
      if (metamodelClassifiers && binding.metamodel === process.level && !metamodelClassifiers.has(binding.classifier)) {
        errors.push(
          `task definition ${taskDefinition.id} binds unknown ${binding.metamodel} classifier ${binding.classifier}`,
        );
      }
    }
  }

  for (const workProductDefinition of methodContent.workProductDefinitions || []) {
    if (workProductDefinition.type !== "WorkProductDefinition") {
      errors.push(`work product ${workProductDefinition.id} is not a WorkProductDefinition`);
    }
    if (!workProductDefinition.workProductKind) {
      errors.push(`work product ${workProductDefinition.id} is missing workProductKind`);
    }
  }

  const checkRoleUses = (ownerId, refs, expectedActivityId = null) => {
    for (const roleUseId of refs || []) {
      const roleUse = roleUses.get(roleUseId);
      if (!roleUse) {
        errors.push(`${ownerId} uses unknown role use ${roleUseId}`);
        continue;
      }
      if (!roleIds.has(roleUse.roleDefinitionRef)) {
        errors.push(`role use ${roleUseId} references unknown role ${roleUse.roleDefinitionRef}`);
      }
      if (expectedActivityId && roleUse.activityRef !== expectedActivityId) {
        errors.push(
          `${ownerId} uses role use ${roleUseId} scoped to ${roleUse.activityRef}, expected ${expectedActivityId}`,
        );
      }
    }
  };

  const visitStage = (stage) => {
    if (stage.primaryRole && !roleIds.has(stage.primaryRole)) {
      errors.push(`stage ${stage.id} uses unknown role ${stage.primaryRole}`);
    }
    checkRoleUses(`stage ${stage.id}`, stage.roleUseRefs, stage.id);
    nodeIds.add(stage.id);
    for (const taskUse of stage.taskUses || []) {
      taskIds.add(taskUse.id);
      nodeIds.add(taskUse.id);
      const taskDefinition = taskDefinitions.get(taskUse.taskDefinitionRef);
      if (!taskDefinition) {
        errors.push(`task use ${taskUse.id} references unknown task definition ${taskUse.taskDefinitionRef}`);
      } else {
        if (!Array.isArray(taskUse.processParameters)) {
          errors.push(`task use ${taskUse.id} is missing explicit processParameters`);
        }
        if (!Array.isArray(taskUse.processPerformers)) {
          errors.push(`task use ${taskUse.id} is missing explicit processPerformers`);
        }
        for (const stepIndex of taskUse.selectedStepIndices || []) {
          if (!Number.isInteger(stepIndex) || stepIndex < 0 || stepIndex >= (taskDefinition.steps || []).length) {
            errors.push(
              `task use ${taskUse.id} selects invalid step ${stepIndex} from ${taskDefinition.id}`,
            );
          }
        }
        for (const workProductId of taskDefinition.inputWorkProductRefs || []) {
          if (!(taskUse.inputWorkProductUseRefs || []).some((workProductUseId) =>
            workProductUses.get(workProductUseId)?.workProductDefinitionRef === workProductId,
          )) {
            errors.push(`task use ${taskUse.id} does not bind input work product ${workProductId}`);
          }
        }
        for (const workProductId of taskDefinition.outputWorkProductRefs || []) {
          if (!(taskUse.outputWorkProductUseRefs || []).some((workProductUseId) =>
            workProductUses.get(workProductUseId)?.workProductDefinitionRef === workProductId,
          )) {
            errors.push(`task use ${taskUse.id} does not bind output work product ${workProductId}`);
          }
        }
        const expectedProcessParameterRefs = new Set();
        for (const parameter of taskUse.processParameters || []) {
          if (parameter.type !== "ProcessParameter") {
            errors.push(`process parameter ${parameter.id} is not a ProcessParameter`);
          }
          if (parameter.taskUseRef !== taskUse.id) {
            errors.push(`process parameter ${parameter.id} references ${parameter.taskUseRef}, expected ${taskUse.id}`);
          }
          if (!["in", "out", "inout"].includes(parameter.direction)) {
            errors.push(`process parameter ${parameter.id} has invalid direction ${parameter.direction}`);
          }
          const workProductUse = workProductUses.get(parameter.workProductUseRef);
          if (!workProductUse) {
            errors.push(`process parameter ${parameter.id} references unknown work product use ${parameter.workProductUseRef}`);
          } else {
            const expectedUsage = parameter.direction === "in" ? "input" : "output";
            if (parameter.direction !== "inout" && workProductUse.usage !== expectedUsage) {
              errors.push(
                `process parameter ${parameter.id} has direction ${parameter.direction} but work product use ${parameter.workProductUseRef} has usage ${workProductUse.usage}`,
              );
            }
            if (workProductUse.taskUseRef !== taskUse.id) {
              errors.push(`process parameter ${parameter.id} crosses task-use scope`);
            }
          }
          expectedProcessParameterRefs.add(parameter.workProductUseRef);
        }
        for (const workProductUseRef of [
          ...(taskUse.inputWorkProductUseRefs || []),
          ...(taskUse.outputWorkProductUseRefs || []),
        ]) {
          if (!expectedProcessParameterRefs.has(workProductUseRef)) {
            errors.push(`task use ${taskUse.id} does not expose ${workProductUseRef} through a ProcessParameter`);
          }
        }
        const performerRoleUseRefs = new Set(taskUse.performerRoleUseRefs || []);
        let primaryPerformerCount = 0;
        for (const performer of taskUse.processPerformers || []) {
          if (performer.type !== "ProcessPerformer") {
            errors.push(`process performer ${performer.id} is not a ProcessPerformer`);
          }
          if (performer.taskUseRef !== taskUse.id) {
            errors.push(`process performer ${performer.id} references ${performer.taskUseRef}, expected ${taskUse.id}`);
          }
          if (!["primary", "supporting"].includes(performer.kind)) {
            errors.push(`process performer ${performer.id} has invalid kind ${performer.kind}`);
          }
          if (performer.kind === "primary") primaryPerformerCount += 1;
          if (!performerRoleUseRefs.has(performer.roleUseRef)) {
            errors.push(`process performer ${performer.id} is not represented in performerRoleUseRefs`);
          }
        }
        if (primaryPerformerCount !== 1) {
          errors.push(`task use ${taskUse.id} must have exactly one primary ProcessPerformer; found ${primaryPerformerCount}`);
        }
        for (const roleUseRef of performerRoleUseRefs) {
          if (!(taskUse.processPerformers || []).some((performer) => performer.roleUseRef === roleUseRef)) {
            errors.push(`task use ${taskUse.id} does not expose performer ${roleUseRef} through a ProcessPerformer`);
          }
        }
      }
      checkRoleUses(`task use ${taskUse.id}`, taskUse.performerRoleUseRefs, stage.id);
      referencedTaskDefinitions.add(taskUse.taskDefinitionRef);
      for (const workProductUseId of [
        ...(taskUse.inputWorkProductUseRefs || []),
        ...(taskUse.outputWorkProductUseRefs || []),
      ]) {
        const workProductUse = workProductUses.get(workProductUseId);
        if (!workProductUse) {
          errors.push(`task use ${taskUse.id} uses unknown work product use ${workProductUseId}`);
          continue;
        }
        referencedWorkProductUses.add(workProductUseId);
        const expectedUsage = (taskUse.inputWorkProductUseRefs || []).includes(workProductUseId)
          ? "input"
          : "output";
        if (workProductUse.usage !== expectedUsage) {
          errors.push(`work product use ${workProductUseId} has usage ${workProductUse.usage}, expected ${expectedUsage}`);
        }
        if (workProductUse.activityRef !== stage.id) {
          errors.push(
            `work product use ${workProductUseId} is scoped to ${workProductUse.activityRef}, expected ${stage.id}`,
          );
        }
        if (workProductUse.taskUseRef !== taskUse.id) {
          errors.push(
            `work product use ${workProductUseId} references task use ${workProductUse.taskUseRef}, expected ${taskUse.id}`,
          );
        }
        if (!workProductIds.has(workProductUse.workProductDefinitionRef)) {
          errors.push(
            `work product use ${workProductUseId} references unknown definition ${workProductUse.workProductDefinitionRef}`,
          );
        }
      }
    }
    for (const task of stage.tasks || []) {
      taskIds.add(task.id);
      if (task.primaryRole && !roleIds.has(task.primaryRole)) {
        errors.push(`task ${task.id} uses unknown role ${task.primaryRole}`);
      }
      for (const artifactId of task.artifactIds || []) {
        if (!workProductIds.has(artifactId)) {
          errors.push(`task ${task.id} uses unknown artifact ${artifactId}`);
        }
      }
    }
    for (const child of stage.subStages || []) visitStage(child);
  };
  for (const phase of process.phases || []) {
    if (!phaseIds.has(phase.id)) errors.push(`phase ${phase.id} is not registered`);
    if (phase.primaryRole && !roleIds.has(phase.primaryRole)) {
      errors.push(`phase ${phase.id} uses unknown role ${phase.primaryRole}`);
    }
    checkRoleUses(`phase ${phase.id}`, phase.roleUseRefs, phase.id);
    for (const stage of phase.stages || []) visitStage(stage);
  }
  for (const component of process.processComponents || []) {
    checkRoleUses(`process component ${component.id}`, component.roleUseRefs, component.id);
    for (const activity of component.activities || []) visitStage(activity);
  }

  for (const roleUse of process.roleUses || []) {
    const activity = activities.get(roleUse.activityRef);
    if (!activity) {
      errors.push(`role use ${roleUse.id} references unknown activity ${roleUse.activityRef}`);
    } else if (!(activity.roleUseRefs || []).includes(roleUse.id)) {
      errors.push(`role use ${roleUse.id} is not referenced by activity ${roleUse.activityRef}`);
    }
  }

  for (const taskDefinition of taskDefinitions.values()) {
    if (!referencedTaskDefinitions.has(taskDefinition.id)) {
      errors.push(`task definition ${taskDefinition.id} is not used by any TaskUse`);
    }
  }
  for (const workProductUse of workProductUses.values()) {
    if (!referencedWorkProductUses.has(workProductUse.id)) {
      errors.push(`work product use ${workProductUse.id} is not referenced by any TaskUse`);
    }
    const taskActivityId = taskUseContexts.get(workProductUse.taskUseRef);
    if (!taskActivityId) {
      errors.push(`work product use ${workProductUse.id} references unknown task use ${workProductUse.taskUseRef}`);
    } else if (workProductUse.activityRef !== taskActivityId) {
      errors.push(
        `work product use ${workProductUse.id} activity ${workProductUse.activityRef} disagrees with task use activity ${taskActivityId}`,
      );
    }
  }

  const sequenceKeys = new Set();
  for (const sequence of process.workSequences || []) {
    if (!nodeIds.has(sequence.predecessorRef) || !nodeIds.has(sequence.successorRef)) {
      errors.push(
        `work sequence ${sequence.id} uses unknown predecessor ${sequence.predecessorRef} or successor ${sequence.successorRef}`,
      );
    }
    if (!["finishToStart", "finishToFinish", "startToStart", "startToFinish"].includes(sequence.linkKind)) {
      errors.push(`work sequence ${sequence.id} has invalid link kind ${sequence.linkKind}`);
    }
    const sequenceKey = `${sequence.predecessorRef}|${sequence.successorRef}|${sequence.linkKind}|${sequence.condition || ""}`;
    if (sequenceKeys.has(sequenceKey)) {
      errors.push(`work sequence ${sequence.id} duplicates semantic edge ${sequenceKey}`);
    }
    sequenceKeys.add(sequenceKey);
  }

  const engine = process.processEngine;
  if (!process.progressModel) errors.push("missing progressModel");
  if (!process.governance) errors.push("missing governance");
  if (engine) {
    const stepIds = new Set((engine.cycle || []).map((step) => step.id));
    for (const step of engine.cycle || []) {
      for (const phaseId of step.phaseIds || []) {
        if (!phaseIds.has(phaseId)) errors.push(`engine step ${step.id} uses unknown phase ${phaseId}`);
      }
      for (const stageId of step.stageIds || []) {
        if (!stages.has(stageId)) errors.push(`engine step ${step.id} uses unknown stage ${stageId}`);
      }
    }
    if (engine.loop && (!stepIds.has(engine.loop.fromStepId) || !stepIds.has(engine.loop.toStepId))) {
      errors.push(`engine loop uses unknown step ${engine.loop.fromStepId} or ${engine.loop.toStepId}`);
    }
    for (const loop of engine.reworkLoops || []) {
      if (!stages.has(loop.fromStageId) || !stages.has(loop.toStageId)) {
        errors.push(`engine rework loop ${loop.id} uses unknown stage ${loop.fromStageId} or ${loop.toStageId}`);
      }
    }
  }

  for (const workflow of process.changeManagement?.workflows || []) {
    // The orchestration process intentionally includes child-level workflows;
    // their stage IDs belong to the child process, not this process tree.
    if (process.level === "end-to-end" && workflow.crossLevel) continue;
    for (const stageId of workflow.impactedStages || []) {
      if (!stages.has(stageId)) errors.push(`change workflow ${workflow.id} uses unknown stage ${stageId}`);
    }
  }
  for (const milestone of process.milestones || []) {
    if (milestone.phaseId && !phaseIds.has(milestone.phaseId)) errors.push(`milestone ${milestone.id} uses unknown phase ${milestone.phaseId}`);
    if (milestone.stageId && !stages.has(milestone.stageId)) errors.push(`milestone ${milestone.id} uses unknown stage ${milestone.stageId}`);
  }

  if (errors.length) {
    console.error(`${fileName}: ${errors.join("; ")}`);
    failed = true;
  } else {
    console.log(`${fileName}: process references and governance are valid (${taskIds.size} tasks)`);
  }
}

for (const level of ["cim", "pim", "psm"]) {
  const matrixPath = join(COV_DIR, `${level}-coverage.json`);
  let matrix;
  try {
    matrix = JSON.parse(readFileSync(matrixPath, "utf8"));
  } catch {
    console.error(`Missing coverage matrix: ${matrixPath}. Run generate-coverage-matrix.mjs first.`);
    failed = true;
    continue;
  }

  const ecoreFile = join(METAMODEL_ROOT, level, `${level === "psm" ? "psm" : level}-combined.ecore`);
  const concepts = allConcepts(parseEcore(ecoreFile));
  const covered = new Set(
    (matrix.entries || []).filter((e) => e.covered).map((e) => e.concept),
  );
  const orphans = concepts.filter((c) => !covered.has(c));

  if (orphans.length) {
    console.error(`${level}: ${orphans.length} orphaned concept(s): ${orphans.join(", ")}`);
    failed = true;
  } else {
    console.log(`${level}: all ${concepts.length} concepts covered`);
  }

  // Verify process definition exists
  const procPath = join(PROC_DIR, `${level}.json`);
  try {
    const process = JSON.parse(readFileSync(procPath, "utf8"));
    validateProcess(process, `${level}.json`, new Set(concepts));
  } catch {
    console.error(`Missing process definition: ${procPath}`);
    failed = true;
  }
}

// end-to-end
try {
  const process = JSON.parse(readFileSync(join(PROC_DIR, "end-to-end.json"), "utf8"));
  validateProcess(process, "end-to-end.json");
} catch {
  console.error("Missing process definition: end-to-end.json");
  failed = true;
}

try {
  const process = JSON.parse(readFileSync(join(PROC_DIR, "artifact.json"), "utf8"));
  validateProcess(process, "artifact.json");
} catch {
  console.error("Missing process definition: artifact.json");
  failed = true;
}

if (failed) {
  process.exit(1);
}
console.log("validate-coverage: OK");
