import assert from "node:assert/strict";
import test from "node:test";

import { renderableLoopEdges } from "../../apps/frontend/js/methodology-process-utils.js";

test("keeps loop and rework edges whose endpoints are visible", () => {
  const nodes = new Map([
    ["phase-1", { id: "phase-1" }],
    ["phase-2", { id: "phase-2" }],
  ]);
  const loop = { from: "phase-2", to: "phase-1", kind: "loop" };
  const rework = { from: "phase-1", to: "phase-2", kind: "rework" };

  assert.deepEqual(renderableLoopEdges([loop, rework], nodes), [loop, rework]);
});

test("drops stale loop endpoints instead of passing undefined nodes to the router", () => {
  const nodes = new Map([["phase-1", { id: "phase-1" }]]);
  const valid = { from: "phase-1", to: "phase-1", kind: "loop" };
  const missingSource = { from: "removed-phase", to: "phase-1", kind: "loop" };
  const missingTarget = { from: "phase-1", to: "removed-phase", kind: "rework" };
  const ordinaryFlow = { from: "phase-1", to: "removed-phase", kind: "flow" };

  assert.deepEqual(
    renderableLoopEdges([missingSource, valid, missingTarget, ordinaryFlow], nodes),
    [valid],
  );
});
