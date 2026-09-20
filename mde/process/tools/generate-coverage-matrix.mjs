#!/usr/bin/env node
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";
import { parseEcore, allConcepts } from "./lib/ecore-parser.mjs";
import { collectProcessTasks } from "./lib/process-walk.mjs";

const ROOT = join(import.meta.dirname, "..");
const METAMODEL_ROOT = join(ROOT, "..", "metamodels");
const PROC_DIR = join(ROOT, "process-definitions");
const OUT_DIR = join(ROOT, "coverage-matrix");

function loadProcess(level) {
  return JSON.parse(readFileSync(join(PROC_DIR, `${level}.json`), "utf8"));
}

function collectTaskMappings(processDef) {
  const mappings = {};
  for (const { task } of collectProcessTasks(processDef)) {
    for (const binding of task.metamodelBindings || []) {
      if (!binding.classifier) continue;
      if (!mappings[binding.classifier]) mappings[binding.classifier] = [];
      if (!mappings[binding.classifier].includes(task.id)) {
        mappings[binding.classifier].push(task.id);
      }
    }
  }
  return mappings;
}

function generate(level) {
  const ecoreFile = join(METAMODEL_ROOT, level, `${level === "psm" ? "psm" : level}-combined.ecore`);
  const parsed = parseEcore(ecoreFile);
  const concepts = allConcepts(parsed);
  const processDef = loadProcess(level);
  const mappings = collectTaskMappings(processDef);

  const entries = concepts.map((concept) => {
    const kind = parsed.classes.includes(concept)
      ? "EClass"
      : parsed.enums.includes(concept)
        ? "EEnum"
        : "EDataType";
    const taskIds = mappings[concept] || [];
    return { concept, kind, taskIds, covered: taskIds.length > 0 };
  });

  const orphaned = entries.filter((e) => !e.covered).map((e) => e.concept);

  return {
    level,
    processId: processDef.processId,
    generatedAt: new Date().toISOString(),
    totalConcepts: concepts.length,
    coveredConcepts: entries.filter((e) => e.covered).length,
    orphanedConcepts: orphaned,
    entries,
  };
}

mkdirSync(OUT_DIR, { recursive: true });

let hasOrphans = false;
for (const level of ["cim", "pim", "psm"]) {
  const matrix = generate(level);
  const out = join(OUT_DIR, `${level}-coverage.json`);
  writeFileSync(out, `${JSON.stringify(matrix, null, 2)}\n`);
  console.log(
    `${level}: ${matrix.coveredConcepts}/${matrix.totalConcepts} covered` +
      (matrix.orphanedConcepts.length ? ` (${matrix.orphanedConcepts.length} orphaned)` : ""),
  );
  if (matrix.orphanedConcepts.length) {
    console.log("  orphaned:", matrix.orphanedConcepts.join(", "));
    hasOrphans = true;
  }
}

if (hasOrphans) {
  process.exit(1);
}
