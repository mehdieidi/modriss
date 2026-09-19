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
import { countTasks } from "./lib/process-walk.mjs";

const ROOT = join(import.meta.dirname, "..");
const METAMODEL_ROOT = join(ROOT, "..", "metamodels");
const OUT_DIR = join(ROOT, "process-definitions");

function workProductsForTypes(types, level, parsed) {
  return types.map((name) => {
    if (parsed.enums.includes(name)) {
      return { metamodel: level, eEnum: name, cardinality: "enum-values" };
    }
    return {
      metamodel: level,
      eClass: name,
      cardinality:
        KERNEL_TYPES.has(name) || SHARED_READINESS_TYPES.has(name) ? "0..*" : "1..*",
    };
  });
}

function resolveTaskTypes(task, conceptAssignment, concepts) {
  if (task.readinessTypes) {
    const readiness = concepts.filter((c) => SHARED_READINESS_TYPES.has(c));
    return [...new Set([...(task.types || []), ...readiness])];
  }
  const assigned = concepts.filter((c) => conceptAssignment.get(c) === task.id);
  return [...new Set([...(task.types || []), ...assigned])];
}

function enrichTask(task, level, parsed, conceptAssignment, concepts) {
  const types = resolveTaskTypes(task, conceptAssignment, concepts);
  const workProducts = workProductsForTypes(types, level, parsed);
  const paletteFocus = types.filter(
    (t) =>
      parsed.classes.includes(t) &&
      !KERNEL_TYPES.has(t) &&
      !SHARED_READINESS_TYPES.has(t),
  );
  const artifacts = (task.artifactIds || [])
    .map((id) => ARTIFACT_KINDS[level]?.find((a) => a.id === id))
    .filter(Boolean)
    .map((a) => ({ id: a.id, name: a.name }));

  return {
    id: task.id,
    name: task.name,
    primaryRole: task.primaryRole,
    viewpoint: task.viewpoint,
    artifacts,
    artifactIds: task.artifactIds || [],
    coverageGroups: task.coverageGroups || [],
    workProducts,
    steps: task.steps,
    entryCriteria: task.entryCriteria,
    exitCriteria: task.exitCriteria,
    validationRules: task.validationRules,
    progressEvidence: task.progressEvidence,
    childProcessId: task.childProcessId,
    transform: task.transform,
    paletteFocus: paletteFocus.slice(0, 12),
    durationEstimate: task.durationEstimate,
  };
}

function enrichStages(stages, level, parsed, conceptAssignment, concepts, loops) {
  return (stages || []).map((stage) => {
    const stageLoops = loops.filter(
      (l) => l.fromStageId === stage.id || l.toStageId === stage.id,
    );
    const base = {
      id: stage.id,
      name: stage.name,
      objective: stage.objective,
      primaryRole: stage.primaryRole,
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
    if (stage.subStages?.length) {
      return {
        ...base,
        subStages: enrichStages(
          stage.subStages,
          level,
          parsed,
          conceptAssignment,
          concepts,
          loops,
        ),
      };
    }
    return {
      ...base,
      tasks: (stage.tasks || []).map((t) =>
        enrichTask(t, level, parsed, conceptAssignment, concepts),
      ),
    };
  });
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

  const phases = phaseSpecs.map((phase) => ({
    id: phase.id,
    name: phase.name,
    order: phase.order,
    objective: phase.objective,
    primaryRole: phase.primaryRole,
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
    ),
  }));

  const lastPhase = phases[phases.length - 1];

  return {
    processId: `modriss.${level}.modeling`,
    spemVersion: "2.0",
    level,
    displayName: level.toUpperCase(),
    roles: ROLES[level],
    artifactKinds: ARTIFACT_KINDS[level] || [],
    guidelines: PROCESS_GUIDELINES[level] || [],
    progressModel: PROCESS_GOVERNANCE[level]?.progressModel,
    governance: PROCESS_GOVERNANCE[level]?.governance,
    processEngine: enrichEngineWithReworkLoops(level, PROCESS_ENGINES[level], loops),
    phases,
    changeManagement: CHANGE_MANAGEMENT[level] || { workflows: [] },
    milestones: [
      {
        id: `${level}.m1.evl-gate`,
        name: `${level.toUpperCase()} EVL validation gate`,
        phaseId: lastPhase?.id,
      },
    ],
  };
}

function buildEndToEnd() {
  return {
    processId: "modriss.end-to-end.modeling",
    spemVersion: "2.0",
    level: "end-to-end",
    displayName: "Full Software Lifecycle with CIM → PIM → PSM",
    roles: END_TO_END_ROLES,
    artifactKinds: END_TO_END_ARTIFACT_KINDS,
    guidelines: PROCESS_GUIDELINES["end-to-end"] || [],
    progressModel: PROCESS_GOVERNANCE["end-to-end"]?.progressModel,
    governance: PROCESS_GOVERNANCE["end-to-end"]?.governance,
    processEngine: enrichEngineWithReworkLoops(
      "end-to-end",
      PROCESS_ENGINES["end-to-end"],
      [],
      END_TO_END_LOOPS,
    ),
    phases: END_TO_END_PHASES,
    changeManagement: {
      workflows: [
        CROSS_LEVEL_CHANGE_WORKFLOW,
        ...(CHANGE_MANAGEMENT.cim?.workflows?.filter((w) => w.crossLevel) || []),
        ...(CHANGE_MANAGEMENT.pim?.workflows?.filter((w) => w.crossLevel) || []),
        ...(CHANGE_MANAGEMENT.psm?.workflows?.filter((w) => w.crossLevel) || []),
      ],
    },
    milestones: [
      { id: "e2e.m0.method-tailored", name: "Method and team topology approved", phaseId: "e2e.ph0", stageId: "e2e.ph0.st3" },
      { id: "e2e.m1.increment-accepted", name: "Vertical increment accepted", phaseId: "e2e.ph1", stageId: "e2e.p7.artifact-completion" },
      { id: "e2e.m2.release-promoted", name: "Release promoted and handed over", phaseId: "e2e.ph2", stageId: "e2e.ph2.st2" },
      { id: "e2e.m3.operational-learning", name: "Operational learning reviewed", phaseId: "e2e.ph3", stageId: "e2e.ph3.st4" },
      { id: "e2e.m4.retired", name: "Lifecycle retired and closed", phaseId: "e2e.ph4", stageId: "e2e.ph4.st3" },
    ],
  };
}

mkdirSync(OUT_DIR, { recursive: true });

for (const level of ["cim", "pim", "psm"]) {
  const def = buildProcessDefinition(level);
  const out = join(OUT_DIR, `${level}.json`);
  writeFileSync(out, `${JSON.stringify(def, null, 2)}\n`);
  console.log(`Wrote ${out} (${def.phases.length} phases, ${countTasks(def.phases)} tasks)`);
}

writeFileSync(join(OUT_DIR, "end-to-end.json"), `${JSON.stringify(buildEndToEnd(), null, 2)}\n`);
console.log("Wrote end-to-end.json");
