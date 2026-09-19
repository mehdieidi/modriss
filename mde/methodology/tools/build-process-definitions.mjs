#!/usr/bin/env node
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { parseEcore, allConcepts } from "./lib/ecore-parser.mjs";
import { PROCESS_PHASES } from "./lib/spem-process-spec.mjs";
import {
  END_TO_END_ARTIFACT_KINDS,
  END_TO_END_PHASES,
  END_TO_END_ROLES,
} from "./lib/spem-end-to-end.mjs";
import {
  ROLES,
  assignConceptsToTasks,
  KERNEL_TYPES,
  SHARED_READINESS_TYPES,
} from "./lib/concept-assignment.mjs";
import { ARTIFACT_KINDS } from "./lib/artifact-kinds.mjs";
import { PROCESS_GUIDELINES } from "./lib/guidelines.mjs";
import { PROCESS_ENGINES, enrichEngineWithReworkLoops } from "./lib/process-engine.mjs";
import { ITERATION_LOOPS, END_TO_END_LOOPS } from "./lib/iteration-loops.mjs";
import { CHANGE_MANAGEMENT, CROSS_LEVEL_CHANGE_WORKFLOW } from "./lib/change-management.mjs";
import { PROCESS_GOVERNANCE } from "./lib/process-governance.mjs";
import { collectProcessTasks } from "./lib/process-walk.mjs";
import { ARTIFACT_PROCESS_SPEC } from "./lib/spem-artifact.mjs";

const ROOT = join(import.meta.dirname, "..");
const METAMODEL_ROOT = join(ROOT, "..", "metamodels");
const OUT_DIR = join(ROOT, "process-definitions");

const SPEM_REFERENCE = "https://www.omg.org/spec/SPEM/2.0/PDF/";

function roleDefinitionRef(roleId) {
  return roleId ? `role.${roleId}` : undefined;
}

function taskDefinitionId(taskId) {
  return `task.${taskId}`;
}

function roleDefinitions(roles) {
  return (roles || []).map((role) => ({
    id: roleDefinitionRef(role.id),
    type: "RoleDefinition",
    name: role.name,
    responsibilities: role.responsibilities || [],
  }));
}

function workProductDefinitions(artifacts) {
  return (artifacts || []).map((artifact) => ({
    id: artifact.id,
    type: "WorkProductDefinition",
    workProductKind: artifact.workProductKind || artifact.spemKind || "Artifact",
    name: artifact.name,
    description: artifact.description,
  }));
}

function resolveTaskTypes(task, conceptAssignment, concepts) {
  if (task.readinessTypes) {
    const readiness = concepts.filter((c) => SHARED_READINESS_TYPES.has(c));
    return [...new Set([...(task.types || []), ...readiness])];
  }
  const assigned = concepts.filter((c) => conceptAssignment.get(c) === task.id);
  return [...new Set([...(task.types || []), ...assigned])];
}

function workProductParameters(inputWorkProductRefs, outputWorkProductRefs, optionalInputRefs = []) {
  const optionalInputs = new Set(optionalInputRefs);
  return [
    ...inputWorkProductRefs.map((workProductDefinitionRef) => ({
      workProductDefinitionRef,
      direction: "in",
      optional: optionalInputs.has(workProductDefinitionRef),
    })),
    ...outputWorkProductRefs.map((workProductDefinitionRef) => ({
      workProductDefinitionRef,
      direction: "out",
      optional: false,
    })),
  ];
}

function enrichTaskDefinition(
  task,
  level,
  parsed,
  conceptAssignment,
  concepts,
  inputWorkProductRefs = [],
) {
  const types = resolveTaskTypes(task, conceptAssignment, concepts);
  const outputWorkProductRefs = task.artifactIds || [];

  return {
    id: taskDefinitionId(task.id),
    type: "TaskDefinition",
    name: task.name,
    purpose: task.purpose || task.name,
    viewpoint: task.viewpoint,
    performerRoleRefs: [roleDefinitionRef(task.primaryRole)].filter(Boolean),
    inputWorkProductRefs,
    inputSource: "declared",
    outputWorkProductRefs,
    workProductParameters: workProductParameters(
      inputWorkProductRefs,
      outputWorkProductRefs,
      task.optionalInputArtifactIds || [],
    ),
    coverageGroups: task.coverageGroups || [],
    steps: task.steps,
    entryCriteria: task.entryCriteria,
    exitCriteria: task.exitCriteria,
    validationRules: task.validationRules,
    progressEvidence: task.progressEvidence,
    childProcessId: task.childProcessId,
    transform: task.transform,
    durationEstimate: task.durationEstimate,
    paletteFocus: types
      .filter(
        (classifier) =>
          parsed?.classes.includes(classifier) &&
          !KERNEL_TYPES.has(classifier) &&
          !SHARED_READINESS_TYPES.has(classifier),
      )
      .slice(0, 12),
    metamodelBindings: types.map((classifier) => ({
      metamodel: level,
      classifier,
      kind: parsed?.classes.includes(classifier)
        ? "EClass"
        : parsed?.enums.includes(classifier)
          ? "EEnum"
          : "EDataType",
    })),
  };
}

function workProductUseId(taskUseId, workProductId, usage) {
  return `wpu.${taskUseId}.${usage}.${workProductId}`;
}

function roleUseId(activityId, roleId) {
  return `ru.${activityId}.${roleId}`;
}

function activityRoleUseRefs(activity, taskSpecs = []) {
  const roleIds = new Set(
    [activity.primaryRole, ...taskSpecs.map((task) => task.primaryRole)].filter(Boolean),
  );
  return [...roleIds].map((roleId) => roleUseId(activity.id, roleId));
}

function compileTaskUse(task, activity, workProductUses, inputWorkProductIdsByTaskId) {
  const inputRefs = inputWorkProductIdsByTaskId.get(task.id) || [];
  const inputUseRefs = inputRefs.map((workProductId) => {
    const id = workProductUseId(task.id, workProductId, "input");
    workProductUses.push({
      id,
      type: "WorkProductUse",
      activityRef: activity.id,
      taskUseRef: task.id,
      workProductDefinitionRef: workProductId,
      usage: "input",
    });
    return id;
  });
  const outputRefs = (task.artifactIds || []).map((workProductId) => {
    const id = workProductUseId(task.id, workProductId, "output");
    workProductUses.push({
      id,
      type: "WorkProductUse",
      activityRef: activity.id,
      taskUseRef: task.id,
      workProductDefinitionRef: workProductId,
      usage: "output",
    });
    return id;
  });

  return {
    id: task.id,
    type: "TaskUse",
    taskDefinitionRef: taskDefinitionId(task.id),
    performerRoleUseRefs: task.primaryRole
      ? [roleUseId(activity.id, task.primaryRole)]
      : [],
    selectedStepIndices: (task.steps || []).map((_, index) => index),
    inputWorkProductUseRefs: inputUseRefs,
    outputWorkProductUseRefs: outputRefs,
    childProcessId: task.childProcessId,
    transform: task.transform,
  };
}

function enrichStages(
  stages,
  level,
  parsed,
  conceptAssignment,
  concepts,
  loops,
  workProductUses,
  roleUses,
  inputWorkProductIdsByTaskId,
) {
  return (stages || []).map((stage) => {
    const taskSpecs = stage.tasks || [];
    for (const roleId of new Set([stage.primaryRole, ...taskSpecs.map((task) => task.primaryRole)].filter(Boolean))) {
      roleUses.push({
        id: roleUseId(stage.id, roleId),
        type: "RoleUse",
        activityRef: stage.id,
        roleDefinitionRef: roleDefinitionRef(roleId),
      });
    }
    const stageLoops = loops.filter(
      (l) => l.fromStageId === stage.id || l.toStageId === stage.id,
    );
    const base = {
      id: stage.id,
      type: "Activity",
      activityKind: "MODRISS::Stage",
      name: stage.name,
      objective: stage.objective,
      roleUseRefs: activityRoleUseRefs(stage, taskSpecs),
      viewpoint: stage.viewpoint,
      iterative: stage.iterative === true,
      iterationLoops: stageLoops.map((loop) => ({
        id: loop.id,
        name: loop.name,
        direction: loop.fromStageId === stage.id ? "outbound" : "inbound",
        peerStageId: loop.fromStageId === stage.id ? loop.toStageId : loop.fromStageId,
        trigger: loop.trigger,
        guidance: loop.guidance,
          twinPeaks: loop.twinPeaks === true,
      })),
    };
    return {
      ...base,
      subStages: stage.subStages?.length
        ? enrichStages(
            stage.subStages,
            level,
            parsed,
            conceptAssignment,
            concepts,
            loops,
            workProductUses,
            roleUses,
            inputWorkProductIdsByTaskId,
          )
        : undefined,
      taskUses: taskSpecs.map((task) =>
        compileTaskUse(task, stage, workProductUses, inputWorkProductIdsByTaskId),
      ),
    };
  });
}

function collectTaskDefinitions(
  stages,
  level,
  parsed,
  conceptAssignment,
  concepts,
  output,
  inputWorkProductIdsByTaskId,
) {
  for (const stage of stages || []) {
    for (const task of stage.tasks || []) {
      if (!Array.isArray(task.inputArtifactIds)) {
        throw new Error(
          `${task.id}: inputArtifactIds must be explicitly declared in the process authoring source; use [] when no input WorkProductDefinition is required`,
        );
      }
      const inputWorkProductRefs = [...new Set(task.inputArtifactIds)];
      inputWorkProductIdsByTaskId.set(task.id, inputWorkProductRefs);
      output.push(
        enrichTaskDefinition(
          task,
          level,
          parsed,
          conceptAssignment,
          concepts,
          inputWorkProductRefs,
        ),
      );
    }
    collectTaskDefinitions(
      stage.subStages,
      level,
      parsed,
      conceptAssignment,
      concepts,
      output,
      inputWorkProductIdsByTaskId,
    );
  }
}

function normalizeEngine(engine, processId) {
  if (!engine) return null;
  const cycle = (engine.cycle || []).map((step) => ({
    ...step,
    performerRoleRef: roleDefinitionRef(step.primaryRole),
    primaryRole: undefined,
  }));
  return {
    ...engine,
    extension: "MODRISS::ProcessEngine",
    cycle,
    iteration: {
      id: `${processId}.iteration`,
      type: "Activity",
      activityKind: "Iteration",
      name: engine.displayName,
      isRepeatable: true,
      activityRefs: cycle.flatMap((step) => step.phaseIds || step.stageIds || [step.id]),
    },
    spemMapping:
      "MODRISS extension realized as an Activity(kind = Iteration) containing process Activities and WorkSequences.",
  };
}

function addWorkSequence(workSequences, sequence) {
  const exists = workSequences.some(
    (item) =>
      item.predecessorRef === sequence.predecessorRef &&
      item.successorRef === sequence.successorRef &&
      item.linkKind === sequence.linkKind,
  );
  if (!exists) workSequences.push(sequence);
}

function addAdjacentSequences(items, kind, workSequences) {
  for (let index = 1; index < items.length; index += 1) {
    const predecessor = items[index - 1];
    const successor = items[index];
    addWorkSequence(workSequences, {
      id: `ws.${predecessor.id}.${successor.id}`,
      type: "WorkSequence",
      predecessorRef: predecessor.id,
      successorRef: successor.id,
      linkKind: "finishToStart",
      relation: kind,
    });
  }
}

function buildWorkSequences(phases, engine, loops) {
  const workSequences = [];
  addAdjacentSequences(phases, "phase-order", workSequences);

  const walkStages = (stages) => {
    for (const stage of stages || []) {
      addAdjacentSequences(stage.subStages || [], "activity-order", workSequences);
      addAdjacentSequences(stage.taskUses || [], "task-order", workSequences);
      walkStages(stage.subStages);
    }
  };
  for (const phase of phases || []) {
    addAdjacentSequences(phase.stages || [], "activity-order", workSequences);
    walkStages(phase.stages);
  }

  const engineActivity = (step) =>
    step?.phaseIds?.[0] || step?.stageIds?.[0] || step?.id;
  const cycle = engine?.cycle || [];
  for (let index = 1; index < cycle.length; index += 1) {
    const predecessor = engineActivity(cycle[index - 1]);
    const successor = engineActivity(cycle[index]);
    if (predecessor && successor) {
      const exists = workSequences.some(
        (item) => item.predecessorRef === predecessor && item.successorRef === successor,
      );
      if (!exists) {
        addWorkSequence(workSequences, {
          id: `ws.engine.${predecessor}.${successor}`,
          type: "WorkSequence",
          predecessorRef: predecessor,
          successorRef: successor,
          linkKind: "finishToStart",
          relation: "engine-cycle",
        });
      }
    }
  }
  if (engine?.loop) {
    const predecessor = engineActivity(cycle.find((step) => step.id === engine.loop.fromStepId));
    const successor = engineActivity(cycle.find((step) => step.id === engine.loop.toStepId));
    if (predecessor && successor) {
      addWorkSequence(workSequences, {
        id: `ws.engine-loop.${predecessor}.${successor}`,
        type: "WorkSequence",
        predecessorRef: predecessor,
        successorRef: successor,
        linkKind: "finishToStart",
        relation: "iteration-loop",
        condition: engine.loop.condition,
        guidance: engine.loop.guidance,
      });
    }
  }
  for (const loop of loops || []) {
    addWorkSequence(workSequences, {
      id: `ws.rework.${loop.id}`,
      type: "WorkSequence",
      predecessorRef: loop.fromStageId,
      successorRef: loop.toStageId,
      linkKind: "finishToStart",
      relation: "rework",
      condition: loop.trigger,
      guidance: loop.guidance,
    });
  }
  return workSequences;
}

function compileMethodContent({ packageId, roles, artifacts, guidelines, taskDefinitions }) {
  return {
    packageId,
    type: "MethodContentPackage",
    roleDefinitions: roleDefinitions(roles),
    taskDefinitions,
    workProductDefinitions: workProductDefinitions(artifacts),
    guidance: (guidelines || []).map((guideline) => ({
      type: "Guidance",
      ...guideline,
    })),
  };
}

function spemMetadata() {
  return {
    spemVersion: "2.0",
    representation: "MODRISS JSON DSL mapped to SPEM 2.0",
    conformance: "mapped",
    mappingScope: [
      "Core",
      "ManagedContent",
      "MethodContent",
      "ProcessStructure",
      "ProcessWithMethods",
    ],
    normativeReference: SPEM_REFERENCE,
  };
}

function buildProcessDefinition(level) {
  const ecoreFile = join(METAMODEL_ROOT, level, `${level === "psm" ? "psm" : level}-combined.ecore`);
  const parsed = parseEcore(ecoreFile);
  const concepts = allConcepts(parsed);
  const phaseSpecs = PROCESS_PHASES[level];
  const conceptAssignment = assignConceptsToTasks(phaseSpecs, concepts);
  const unassigned = concepts.filter((concept) => !conceptAssignment.has(concept));
  if (unassigned.length) {
    throw new Error(
      `${level}: every metamodel concept must have an owning task; unassigned: ${unassigned.join(", ")}`,
    );
  }
  const loops = ITERATION_LOOPS[level] || [];
  const processId = `modriss.${level}.modeling`;
  const roleUses = [];
  const workProductUses = [];
  const taskDefinitions = [];
  const inputWorkProductIdsByTaskId = new Map();
  collectTaskDefinitions(
    phaseSpecs.flatMap((phase) => phase.stages),
    level,
    parsed,
    conceptAssignment,
    concepts,
    taskDefinitions,
    inputWorkProductIdsByTaskId,
  );

  const phases = phaseSpecs.map((phase) => ({
    id: phase.id,
    type: "Activity",
    activityKind: "Phase",
    name: phase.name,
    order: phase.order,
    objective: phase.objective,
    roleUseRefs: [roleUseId(phase.id, phase.primaryRole)],
    entryCriteria: phase.entryCriteria,
    exitCriteria: phase.exitCriteria,
    inEngine: phase.inEngine === true,
    stages: enrichStages(
      phase.stages,
      level,
      parsed,
      conceptAssignment,
      concepts,
      loops,
      workProductUses,
      roleUses,
      inputWorkProductIdsByTaskId,
    ),
  }));

  for (const phase of phaseSpecs) {
    roleUses.push({
      id: roleUseId(phase.id, phase.primaryRole),
      type: "RoleUse",
      activityRef: phase.id,
      roleDefinitionRef: roleDefinitionRef(phase.primaryRole),
    });
  }
  const processEngine = normalizeEngine(
    enrichEngineWithReworkLoops(level, PROCESS_ENGINES[level], loops),
    processId,
  );

  const lastPhase = phases[phases.length - 1];

  return {
    processId,
    type: "Process",
    ...spemMetadata(),
    level,
    displayName: level.toUpperCase(),
    methodContent: compileMethodContent({
      packageId: `modriss.method-content.${level}`,
      roles: ROLES[level],
      artifacts: ARTIFACT_KINDS[level] || [],
      guidelines: PROCESS_GUIDELINES[level] || [],
      taskDefinitions,
    }),
    roleUses,
    workProductUses,
    progressModel: PROCESS_GOVERNANCE[level]?.progressModel,
    governance: PROCESS_GOVERNANCE[level]?.governance,
    processEngine,
    phases,
    workSequences: buildWorkSequences(phases, processEngine, loops),
    changeManagement: CHANGE_MANAGEMENT[level] || { workflows: [] },
    milestones: [
      {
        id: `${level}.m1.evl-gate`,
        type: "Milestone",
        name: `${level.toUpperCase()} EVL validation gate`,
        phaseId: lastPhase?.id,
      },
    ],
  };
}

function buildEndToEnd() {
  const processId = "modriss.end-to-end.modeling";
  const roleUses = [];
  const workProductUses = [];
  const taskDefinitions = [];
  const inputWorkProductIdsByTaskId = new Map();
  collectTaskDefinitions(
    END_TO_END_PHASES.flatMap((phase) => phase.stages),
    "end-to-end",
    null,
    new Map(),
    [],
    taskDefinitions,
    inputWorkProductIdsByTaskId,
  );
  const phases = END_TO_END_PHASES.map((phase) => ({
    ...phase,
    primaryRole: undefined,
    type: "Activity",
    activityKind: "Phase",
    roleUseRefs: [roleUseId(phase.id, phase.primaryRole)],
    stages: enrichStages(
      phase.stages,
      "end-to-end",
      null,
      new Map(),
      [],
      END_TO_END_LOOPS,
      workProductUses,
      roleUses,
      inputWorkProductIdsByTaskId,
    ),
  }));
  for (const phase of END_TO_END_PHASES) {
    roleUses.push({
      id: roleUseId(phase.id, phase.primaryRole),
      type: "RoleUse",
      activityRef: phase.id,
      roleDefinitionRef: roleDefinitionRef(phase.primaryRole),
    });
  }
  const processEngine = normalizeEngine(
    enrichEngineWithReworkLoops(
      "end-to-end",
      PROCESS_ENGINES["end-to-end"],
      [],
      END_TO_END_LOOPS,
    ),
    processId,
  );
  return {
    processId,
    type: "Process",
    ...spemMetadata(),
    level: "end-to-end",
    displayName: "Full Software Lifecycle with CIM → PIM → PSM",
    methodContent: compileMethodContent({
      packageId: "modriss.method-content.end-to-end",
      roles: END_TO_END_ROLES,
      artifacts: END_TO_END_ARTIFACT_KINDS,
      guidelines: PROCESS_GUIDELINES["end-to-end"] || [],
      taskDefinitions,
    }),
    roleUses,
    workProductUses,
    progressModel: PROCESS_GOVERNANCE["end-to-end"]?.progressModel,
    governance: PROCESS_GOVERNANCE["end-to-end"]?.governance,
    processEngine,
    phases,
    workSequences: buildWorkSequences(phases, processEngine, END_TO_END_LOOPS),
    changeManagement: {
      workflows: [
        CROSS_LEVEL_CHANGE_WORKFLOW,
        ...(CHANGE_MANAGEMENT.cim?.workflows?.filter((w) => w.crossLevel) || []),
        ...(CHANGE_MANAGEMENT.pim?.workflows?.filter((w) => w.crossLevel) || []),
        ...(CHANGE_MANAGEMENT.psm?.workflows?.filter((w) => w.crossLevel) || []),
      ],
    },
    milestones: [
      { type: "Milestone", id: "e2e.m0.method-tailored", name: "Method and team topology approved", phaseId: "e2e.ph0", stageId: "e2e.ph0.st3" },
      { type: "Milestone", id: "e2e.m1.increment-accepted", name: "Vertical increment accepted", phaseId: "e2e.ph1", stageId: "e2e.p7.artifact-completion" },
      { type: "Milestone", id: "e2e.m2.release-promoted", name: "Release promoted and handed over", phaseId: "e2e.ph2", stageId: "e2e.ph2.st2" },
      { type: "Milestone", id: "e2e.m3.operational-learning", name: "Operational learning reviewed", phaseId: "e2e.ph3", stageId: "e2e.ph3.st4" },
      { type: "Milestone", id: "e2e.m4.retired", name: "Lifecycle retired and closed", phaseId: "e2e.ph4", stageId: "e2e.ph4.st3" },
    ],
  };
}

function buildArtifactProcess() {
  const source = ARTIFACT_PROCESS_SPEC;
  const roleUses = [];
  const workProductUses = [];
  const taskDefinitions = [];
  const inputWorkProductIdsByTaskId = new Map();
  collectTaskDefinitions(
    source.phases.flatMap((phase) => phase.stages),
    source.level,
    null,
    new Map(),
    [],
    taskDefinitions,
    inputWorkProductIdsByTaskId,
  );
  const phases = source.phases.map((phase) => ({
    id: phase.id,
    type: "Activity",
    activityKind: "Phase",
    name: phase.name,
    order: phase.order,
    objective: phase.objective,
    roleUseRefs: [roleUseId(phase.id, phase.primaryRole)],
    entryCriteria: phase.entryCriteria,
    exitCriteria: phase.exitCriteria,
    inEngine: phase.inEngine === true,
    stages: enrichStages(
      phase.stages,
      source.level,
      null,
      new Map(),
      [],
      [],
      workProductUses,
      roleUses,
      inputWorkProductIdsByTaskId,
    ),
  }));
  for (const phase of source.phases) {
    roleUses.push({
      id: roleUseId(phase.id, phase.primaryRole),
      type: "RoleUse",
      activityRef: phase.id,
      roleDefinitionRef: roleDefinitionRef(phase.primaryRole),
    });
  }
  const processEngine = normalizeEngine(source.processEngine, source.processId);
  return {
    processId: source.processId,
    type: "Process",
    ...spemMetadata(),
    level: source.level,
    displayName: source.displayName,
    methodContent: compileMethodContent({
      packageId: "modriss.method-content.artifact",
      roles: source.roles,
      artifacts: source.artifacts,
      guidelines: source.guidelines,
      taskDefinitions,
    }),
    roleUses,
    workProductUses,
    progressModel: source.progressModel,
    governance: source.governance,
    processEngine,
    phases,
    workSequences: buildWorkSequences(phases, processEngine, []),
    changeManagement: source.changeManagement,
    milestones: source.milestones.map((milestone) => ({
      type: "Milestone",
      ...milestone,
    })),
  };
}

mkdirSync(OUT_DIR, { recursive: true });

for (const level of ["cim", "pim", "psm"]) {
  const def = buildProcessDefinition(level);
  const out = join(OUT_DIR, `${level}.json`);
  writeFileSync(out, `${JSON.stringify(def, null, 2)}\n`);
  console.log(
    `Wrote ${out} (${def.phases.length} phases, ${collectProcessTasks(def).length} tasks)`,
  );
}

const endToEnd = buildEndToEnd();
writeFileSync(join(OUT_DIR, "end-to-end.json"), `${JSON.stringify(endToEnd, null, 2)}\n`);
console.log(`Wrote end-to-end.json (${collectProcessTasks(endToEnd).length} tasks)`);

const artifact = buildArtifactProcess();
writeFileSync(join(OUT_DIR, "artifact.json"), `${JSON.stringify(artifact, null, 2)}\n`);
console.log(`Wrote artifact.json (${collectProcessTasks(artifact).length} tasks)`);
