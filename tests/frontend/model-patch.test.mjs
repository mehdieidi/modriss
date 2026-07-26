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

test("keeps independently saved layouts in separate views", () => {
  const previous = {
    views: [
      { id: "business-process", autoLayoutApplied: false, nodes: [{ elementId: "n1", x: 20, y: 30 }] },
      { id: "capability", autoLayoutApplied: true, nodes: [{ elementId: "n1", x: 400, y: 50 }] },
    ],
  };
  const next = structuredClone(previous);
  next.views[0].autoLayoutApplied = true;
  next.views[0].nodes[0] = { elementId: "n1", x: 180, y: 90 };

  const operations = buildModelPatch(previous, next);

  assert.deepEqual(operations, [
    { op: "replace", path: "/views/0/autoLayoutApplied", value: true },
    { op: "replace", path: "/views/0/nodes", value: [{ elementId: "n1", x: 180, y: 90 }] },
  ]);
});
