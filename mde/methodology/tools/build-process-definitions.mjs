#!/usr/bin/env node
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { parseEcore, allConcepts } from "./lib/ecore-parser.mjs";
import {
  PHASE_SPECS,
  ROLES,
  assignConceptsToPhases,
  SHARED_READINESS_TYPES,
  KERNEL_TYPES,
} from "./lib/phase-mappings.mjs";

const ROOT = join(import.meta.dirname, "..");
const METAMODEL_ROOT = join(ROOT, "..", "metamodels");
const OUT_DIR = join(ROOT, "process-definitions");

function taskId(phaseId, index) {
  return `${phaseId.replace(/\./g, ".t")}.${index}`;
}

function buildProcessDefinition(level) {
  const ecoreFile = join(METAMODEL_ROOT, level, `${level === "psm" ? "psm" : level}-combined.ecore`);
  const parsed = parseEcore(ecoreFile);
  const concepts = allConcepts(parsed);
  const assignment = assignConceptsToPhases(level, concepts);
  const specs = PHASE_SPECS[level];

  const phases = specs.map((spec, phaseIndex) => {
    const assigned = concepts.filter((c) => assignment.get(c) === phaseIndex);
    const eClasses = assigned.filter((c) => parsed.classes.includes(c));
    const enums = assigned.filter((c) => parsed.enums.includes(c));

    const workProducts = [
      ...eClasses.map((eClass) => ({
        metamodel: level,
        eClass,
        cardinality: KERNEL_TYPES.has(eClass) ? "0..*" : "1..*",
      })),
      ...enums.map((eEnum) => ({
        metamodel: level,
        eEnum,
        cardinality: "enum-values",
      })),
    ];

    const paletteFocus = eClasses.filter((c) => !KERNEL_TYPES.has(c) && !SHARED_READINESS_TYPES.has(c));

    const tasks = [
      {
        id: `${spec.id}.main`,
        name: spec.name,
        primaryRole: spec.primaryRole,
        workProducts,
        steps: spec.steps,
        entryCriteria: spec.entryCriteria,
        exitCriteria: spec.exitCriteria,
        validationRules: spec.validationRules,
        viewpoint: spec.viewpoint,
        paletteFocus: paletteFocus.length ? paletteFocus : eClasses.slice(0, 8),
        durationEstimate: spec.durationEstimate,
      },
    ];

    if (enums.length > 3) {
      tasks.push({
        id: `${spec.id}.enums`,
        name: `Configure ${spec.name} enumerations`,
        primaryRole: spec.primaryRole,
        workProducts: enums.map((eEnum) => ({
          metamodel: level,
          eEnum,
          cardinality: "enum-values",
        })),
        steps: [`Review and set values for: ${enums.join(", ")}`],
        entryCriteria: spec.entryCriteria,
        exitCriteria: ["Enumeration literals aligned with domain"],
        validationRules: [],
        viewpoint: spec.viewpoint,
        paletteFocus: [],
        durationEstimate: "15m",
      });
    }

    return {
      id: spec.id,
      name: spec.name,
      viewpoint: spec.viewpoint,
      primaryRole: spec.primaryRole,
      durationEstimate: spec.durationEstimate,
      tasks,
    };
  });

  return {
    processId: `modless.${level}.modeling`,
    spemVersion: "2.0",
    level,
    displayName: level.toUpperCase(),
    roles: ROLES[level],
    phases,
    milestones: [
      {
        id: `${level}.m1.evl-gate`,
        name: `${level.toUpperCase()} EVL validation gate`,
        phaseId: phases[phases.length - 1].id,
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
      {
        id: "business-modeler",
        name: "Business Modeler",
        responsibilities: ["CIM modeling phases 0-12"],
      },
      {
        id: "requirements-engineer",
        name: "Requirements Engineer",
        responsibilities: ["CIM phase 11 governance"],
      },
      {
        id: "solution-architect",
        name: "Solution Architect",
        responsibilities: ["PIM modeling and refinement"],
      },
      {
        id: "cloud-platform-engineer",
        name: "Cloud Platform Engineer",
        responsibilities: ["AWS PSM modeling and refinement"],
      },
      {
        id: "process-reviewer",
        name: "Process Reviewer",
        responsibilities: ["EVL gates at each level"],
      },
    ],
    phases: [
      {
        id: "e2e.p1.cim-modeling",
        name: "CIM Modeling (14 phases)",
        primaryRole: "business-modeler",
        childProcessId: "modless.cim.modeling",
        validationGate: "cim-semantic-validation",
        transform: null,
      },
      {
        id: "e2e.p2.cim-to-pim",
        name: "CIM → PIM Transformation",
        primaryRole: "solution-architect",
        transform: "cim-to-pim",
        validationGate: null,
      },
      {
        id: "e2e.p3.pim-refinement",
        name: "PIM Refinement (12 phases)",
        primaryRole: "solution-architect",
        childProcessId: "modless.pim.modeling",
        validationGate: "pim-semantic-validation",
        transform: null,
      },
      {
        id: "e2e.p4.pim-to-psm",
        name: "PIM → AWS PSM Transformation",
        primaryRole: "cloud-platform-engineer",
        transform: "pim-to-awspsm",
        validationGate: null,
      },
      {
        id: "e2e.p5.psm-refinement",
        name: "PSM Refinement (11 phases)",
        primaryRole: "cloud-platform-engineer",
        childProcessId: "modless.psm.modeling",
        validationGate: "psm-semantic-validation",
        transform: null,
      },
      {
        id: "e2e.p6.m2t-generation",
        name: "M2T Artifact Generation",
        primaryRole: "cloud-platform-engineer",
        transform: "awspsm-to-artifacts",
        validationGate: null,
      },
      {
        id: "e2e.p7.artifact-completion",
        name: "Artifact Review & Completion",
        primaryRole: "process-reviewer",
        transform: null,
        validationGate: null,
      },
    ],
    milestones: [
      { id: "e2e.m1.cim-ready", name: "CIM readiness approved", phaseId: "e2e.p1.cim-modeling" },
      { id: "e2e.m2.pim-ready", name: "PIM readiness approved", phaseId: "e2e.p3.pim-refinement" },
      { id: "e2e.m3.psm-ready", name: "PSM readiness approved", phaseId: "e2e.p5.psm-refinement" },
      { id: "e2e.m4.artifacts", name: "Artifacts delivered", phaseId: "e2e.p7.artifact-completion" },
    ],
  };
}

mkdirSync(OUT_DIR, { recursive: true });

for (const level of ["cim", "pim", "psm"]) {
  const def = buildProcessDefinition(level);
  const out = join(OUT_DIR, `${level}.json`);
  writeFileSync(out, `${JSON.stringify(def, null, 2)}\n`);
  console.log(`Wrote ${out}`);
}

const e2e = buildEndToEnd();
writeFileSync(join(OUT_DIR, "end-to-end.json"), `${JSON.stringify(e2e, null, 2)}\n`);
console.log("Wrote end-to-end.json");
