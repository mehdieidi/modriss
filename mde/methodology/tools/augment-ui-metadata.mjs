#!/usr/bin/env node
import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { collectProcessTasks } from "./lib/process-walk.mjs";

const ROOT = join(import.meta.dirname, "..");
const COV_DIR = join(ROOT, "coverage-matrix");
const METADATA_DIR = join(
  ROOT,
  "..",
  "..",
  "packages",
  "java",
  "platform-modeling",
  "src",
  "main",
  "resources",
  "modeling",
);

for (const level of ["cim", "pim", "psm"]) {
  const matrix = JSON.parse(readFileSync(join(COV_DIR, `${level}-coverage.json`), "utf8"));
  const conceptToTasks = new Map(
    (matrix.entries || []).map((e) => [e.concept, e.taskIds || []]),
  );
  const process = JSON.parse(
    readFileSync(join(ROOT, "process-definitions", `${level}.json`), "utf8"),
  );
  const conceptToPhase = new Map();
  const conceptToStage = new Map();
  for (const { task, phaseId, stageId } of collectProcessTasks(process)) {
    for (const binding of task.metamodelBindings || []) {
      const key = binding.classifier;
      if (key) {
        conceptToPhase.set(key, phaseId);
        conceptToStage.set(key, stageId);
      }
    }
  }

  const metaPath = join(METADATA_DIR, `${level}-ui-metadata.json`);
  const meta = JSON.parse(readFileSync(metaPath, "utf8"));
  let updated = 0;
  for (const element of meta.elements || []) {
    const type = element.type;
    if (!type) continue;
    const phaseId = conceptToPhase.get(type);
    const stageId = conceptToStage.get(type);
    const taskIds = conceptToTasks.get(type) || [];
    if (phaseId) {
      element.processPhaseId = phaseId;
      updated++;
    }
    if (stageId) {
      element.processStageId = stageId;
    }
    if (taskIds.length) {
      element.processTaskIds = taskIds;
    }
  }
  writeFileSync(metaPath, `${JSON.stringify(meta, null, 2)}\n`);
  console.log(`${level}-ui-metadata.json: linked ${updated} elements`);
}
