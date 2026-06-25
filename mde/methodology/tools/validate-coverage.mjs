#!/usr/bin/env node
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parseEcore, allConcepts } from "./lib/ecore-parser.mjs";

const ROOT = join(import.meta.dirname, "..");
const METAMODEL_ROOT = join(ROOT, "..", "metamodels");
const COV_DIR = join(ROOT, "coverage-matrix");
const PROC_DIR = join(ROOT, "process-definitions");

let failed = false;

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
    JSON.parse(readFileSync(procPath, "utf8"));
  } catch {
    console.error(`Missing process definition: ${procPath}`);
    failed = true;
  }
}

// end-to-end
try {
  JSON.parse(readFileSync(join(PROC_DIR, "end-to-end.json"), "utf8"));
  console.log("end-to-end: process definition present");
} catch {
  console.error("Missing process definition: end-to-end.json");
  failed = true;
}

if (failed) {
  process.exit(1);
}
console.log("validate-coverage: OK");
