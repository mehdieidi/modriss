import assert from "node:assert/strict";
import test from "node:test";

globalThis.window = {
  location: { origin: "http://localhost" },
};
globalThis.document = {
  getElementById: () => null,
};

const { buildModelPatch } = await import("../../apps/frontend/js/model-patch.js");

test("uses add for optional graph layout properties", () => {
  const previous = {
    graph: {
      elements: [{ id: "container", x: 120, y: 80 }],
    },
  };
  const next = {
    graph: {
      elements: [{ id: "container", x: 180, y: 80 }],
    },
  };

  assert.deepEqual(buildModelPatch(previous, next), [
    {
      op: "add",
      path: "/graph/elements/0/x",
      value: 180,
    },
  ]);
});

test("continues to replace non-layout graph properties", () => {
  const previous = {
    graph: {
      elements: [{ id: "container", name: "Before" }],
    },
  };
  const next = {
    graph: {
      elements: [{ id: "container", name: "After" }],
    },
  };

  assert.deepEqual(buildModelPatch(previous, next), [
    {
      op: "replace",
      path: "/graph/elements/0/name",
      value: "After",
    },
  ]);
});
