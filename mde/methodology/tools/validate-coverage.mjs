#!/usr/bin/env node
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parseEcore, allConcepts } from "./lib/ecore-parser.mjs";

const ROOT = join(import.meta.dirname, "..");
const METAMODEL_ROOT = join(ROOT, "..", "metamodels");
const COV_DIR = join(ROOT, "coverage-matrix");
const PROC_DIR = join(ROOT, "process-definitions");

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

function validateProcess(process, fileName) {
  const methodContent = process.methodContent || {};
  const errors = [];
  if (process.type !== "Process") errors.push("root is not typed as Process");
  if (methodContent.type !== "MethodPackage") errors.push("methodContent is not typed as MethodPackage");
  if (process.conformance !== "mapped") errors.push("process must declare mapped SPEM conformance");
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

  for (const taskDefinition of methodContent.taskDefinitions || []) {
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
    }
  }

  const checkRoleUses = (ownerId, refs) => {
    for (const roleUseId of refs || []) {
      const roleUse = roleUses.get(roleUseId);
      if (!roleUse) {
        errors.push(`${ownerId} uses unknown role use ${roleUseId}`);
        continue;
      }
      if (!roleIds.has(roleUse.roleDefinitionRef)) {
        errors.push(`role use ${roleUseId} references unknown role ${roleUse.roleDefinitionRef}`);
      }
    }
  };

  const visitStage = (stage) => {
    if (stage.primaryRole && !roleIds.has(stage.primaryRole)) {
      errors.push(`stage ${stage.id} uses unknown role ${stage.primaryRole}`);
    }
    checkRoleUses(`stage ${stage.id}`, stage.roleUseRefs);
    nodeIds.add(stage.id);
    for (const taskUse of stage.taskUses || []) {
      taskIds.add(taskUse.id);
      nodeIds.add(taskUse.id);
      const taskDefinition = taskDefinitions.get(taskUse.taskDefinitionRef);
      if (!taskDefinition) {
        errors.push(`task use ${taskUse.id} references unknown task definition ${taskUse.taskDefinitionRef}`);
      }
      checkRoleUses(`task use ${taskUse.id}`, taskUse.performerRoleUseRefs);
      for (const workProductUseId of taskUse.outputWorkProductUseRefs || []) {
        const workProductUse = workProductUses.get(workProductUseId);
        if (!workProductUse) {
          errors.push(`task use ${taskUse.id} uses unknown work product use ${workProductUseId}`);
          continue;
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
    checkRoleUses(`phase ${phase.id}`, phase.roleUseRefs);
    for (const stage of phase.stages || []) visitStage(stage);
  }

  for (const sequence of process.workSequences || []) {
    if (!nodeIds.has(sequence.predecessorRef) || !nodeIds.has(sequence.successorRef)) {
      errors.push(
        `work sequence ${sequence.id} uses unknown predecessor ${sequence.predecessorRef} or successor ${sequence.successorRef}`,
      );
    }
    if (!["finishToStart", "finishToFinish", "startToStart", "startToFinish"].includes(sequence.linkKind)) {
      errors.push(`work sequence ${sequence.id} has invalid link kind ${sequence.linkKind}`);
    }
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
    validateProcess(process, `${level}.json`);
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
