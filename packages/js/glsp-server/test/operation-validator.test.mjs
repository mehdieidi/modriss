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
