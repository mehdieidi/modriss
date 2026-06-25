#!/usr/bin/env node
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { parseEcore, allConcepts } from "./lib/ecore-parser.mjs";
import { PROCESS_PHASES } from "./lib/spem-process-spec.mjs";
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
      cardinality: KERNEL_TYPES.has(name) ? "0..*" : "1..*",
    };
  });
}

function resolveTaskTypes(task, conceptAssignment, concepts) {
  if (task.readinessTypes) {
    const readiness = concepts.filter((c) => SHARED_READINESS_TYPES.has(c));
    const orphans = concepts.filter((c) => conceptAssignment.get(c) === task.id);
    return [...new Set([...(task.types || []), ...readiness, ...orphans])];
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
    workProducts,
    steps: task.steps,
    entryCriteria: task.entryCriteria,
    exitCriteria: task.exitCriteria,
    validationRules: task.validationRules,
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
    processId: `modless.${level}.modeling`,
    spemVersion: "2.0",
    level,
    displayName: level.toUpperCase(),
    roles: ROLES[level],
    artifactKinds: ARTIFACT_KINDS[level] || [],
    guidelines: PROCESS_GUIDELINES[level] || [],
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
    processId: "modless.end-to-end.modeling",
    spemVersion: "2.0",
    level: "end-to-end",
    displayName: "CIM → PIM → PSM → Artifacts",
    roles: [
      { id: "business-modeler", name: "Business Modeler", responsibilities: ["CIM engine cycles"] },
      { id: "requirements-engineer", name: "Requirements Engineer", responsibilities: ["CIM convergence governance"] },
      { id: "solution-architect", name: "Solution Architect", responsibilities: ["PIM engine and CIM→PIM ETL"] },
      { id: "cloud-platform-engineer", name: "Cloud Platform Engineer", responsibilities: ["PSM engine, ETL, M2T"] },
      { id: "process-reviewer", name: "Process Reviewer", responsibilities: ["EVL gates and increment closure"] },
    ],
    artifactKinds: [],
    guidelines: PROCESS_GUIDELINES["end-to-end"] || [],
    processEngine: enrichEngineWithReworkLoops(
      "end-to-end",
      PROCESS_ENGINES["end-to-end"],
      [],
      END_TO_END_LOOPS,
    ),
    phases: [
      {
        id: "e2e.ph1",
        name: "Increment Lifecycle",
        order: 1,
        objective: "Plan, execute child process engines, transform, and close each vertical increment.",
        primaryRole: "business-modeler",
        entryCriteria: ["Program chartered"],
        exitCriteria: ["Increment artifacts validated or backlog empty"],
        inEngine: true,
        stages: PROCESS_ENGINES["end-to-end"].cycle.map((step) => ({
          id: step.id,
          name: step.name,
          objective: step.steps?.join(" ") || step.name,
          primaryRole: step.primaryRole || "business-modeler",
          tasks: [
            {
              id: `${step.id}.t1`,
              name: step.name,
              primaryRole: step.primaryRole || "business-modeler",
              steps: step.steps || [step.name],
              entryCriteria: [],
              exitCriteria: [`${step.name} complete`],
              validationRules: step.validationGate ? [step.validationGate] : [],
              workProducts: [],
              artifacts: [],
              artifactIds: [],
              paletteFocus: [],
              childProcessId: step.childProcessId,
              transform: step.transform,
            },
          ],
        })),
      },
    ],
    changeManagement: {
      workflows: [
        CROSS_LEVEL_CHANGE_WORKFLOW,
        ...(CHANGE_MANAGEMENT.cim?.workflows?.filter((w) => w.crossLevel) || []),
        ...(CHANGE_MANAGEMENT.pim?.workflows?.filter((w) => w.crossLevel) || []),
        ...(CHANGE_MANAGEMENT.psm?.workflows?.filter((w) => w.crossLevel) || []),
      ],
    },
    milestones: [
      { id: "e2e.m0.increment-planned", name: "Increment scope agreed", phaseId: "e2e.ph1", stageId: "e2e.p0.increment-planning" },
      { id: "e2e.m1.cim-ready", name: "CIM readiness approved", phaseId: "e2e.ph1", stageId: "e2e.p1.cim-modeling" },
      { id: "e2e.m2.pim-ready", name: "PIM readiness approved", phaseId: "e2e.ph1", stageId: "e2e.p3.pim-refinement" },
      { id: "e2e.m3.psm-ready", name: "PSM readiness approved", phaseId: "e2e.ph1", stageId: "e2e.p5.psm-refinement" },
      { id: "e2e.m4.artifacts", name: "Increment artifacts delivered", phaseId: "e2e.ph1", stageId: "e2e.p7.artifact-completion" },
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
