import assert from "node:assert/strict";
import test from "node:test";
import { validateCreateEdge, validateCreateNode } from "../dist/operation-validator.js";

test("validateCreateNode rejects unknown types", () => {
  const result = validateCreateNode({ elements: [{ type: "BusinessGoal" }] }, "Unknown");
  assert.equal(result.ok, false);
});

test("validateCreateEdge returns default kind for legal relationship", () => {
  const result = validateCreateEdge(
    {
      relationshipRules: [
        { sourceType: "BusinessGoal", targetType: "Capability", allowedKinds: ["REALIZED_BY"] },
      ],
    },
    "BusinessGoal",
    "Capability",
  );
  assert.equal(result.ok, true);
  assert.equal(result.edgeKind, "REALIZED_BY");
});

test("validateCreateEdge excludes configured trace kind from manual relationships", () => {
  const levelConfig = {
    relationshipSemantics: { traceKind: "TRACE" },
    relationshipRules: [
      {
        sourceType: "BusinessGoal",
        targetType: "Capability",
        allowedKinds: ["TRACE", "REALIZED_BY"],
      },
    ],
  };

  const defaultResult = validateCreateEdge(levelConfig, "BusinessGoal", "Capability");
  assert.equal(defaultResult.ok, true);
  assert.equal(defaultResult.edgeKind, "REALIZED_BY");
  assert.deepEqual(defaultResult.legalKinds, ["REALIZED_BY"]);

  const traceResult = validateCreateEdge(levelConfig, "BusinessGoal", "Capability", "TRACE");
  assert.equal(traceResult.ok, false);
});
